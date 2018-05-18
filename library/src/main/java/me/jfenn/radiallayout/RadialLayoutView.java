package me.jfenn.radiallayout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.afollestad.async.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.jfenn.radiallayout.utils.ConversionUtils;
import me.jfenn.radiallayout.utils.RadialUtils;

public class RadialLayoutView extends View {

    public static final float CLICK_DOWN_SCALE = 0.8f;
    public static final float CLICK_UP_SCALE = 1.07f;

    private Paint paint;
    private Paint shadowPaint;
    List<BaseRadialItem> items;
    private boolean isReady;

    private boolean isScrolling;
    private float offsetX, offsetY;
    private float velocityX, velocityY;
    private float fingerX, fingerY;
    private float lastX, lastY;
    private int maxRow;

    private float downX, downY;
    private boolean isDown, isFingerDown, isIgnorant, isDragged;
    private Handler handler = new Handler();
    private Runnable upRunnable = new Runnable() {
        @Override
        public void run() {
            isDown = false;
            isIgnorant = false;
            velocityX = 0;
            velocityY = 0;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isDown) {
                        if (Math.abs(offsetX) > 0.01 || Math.abs(offsetY) > 0.01) {
                            offsetX /= 1.1;
                            offsetY /= 1.1;
                            lastX /= 1.1;
                            lastY /= 1.1;
                            handler.postDelayed(this, 10);
                        } else {
                            lastX = 0;
                            lastY = 0;
                            offsetX = 0;
                            offsetY = 0;
                            fingerX = 0;
                            fingerY = 0;
                        }
                    }
                }
            });
        }
    };

    private CenteredRadialItem centerItem;
    private List<Float> targetCurrentUserScales;

    private float itemRadius = 36;
    private float itemSeparation = 8;
    private float shadowRadius = 0;
    private float shadowOffset = 0;
    private int shadowColor = Color.argb(100, 0, 0, 0);

    /**
     * true once the view has been drawn - will invalidate continuously until then as an alternative to a ViewTreeObserver
     */
    private boolean isFirstDrawn;

    private CenterClickListener centerListener;
    private ClickListener listener;

    public RadialLayoutView(@NonNull Context context) {
        this(context, null);
    }

    public RadialLayoutView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadialLayoutView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        items = new ArrayList<>();
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);

        shadowPaint = new Paint();
        shadowPaint.setAntiAlias(false);
        shadowPaint.setFilterBitmap(false);
        shadowPaint.setDither(false);
        setLayerType(LAYER_TYPE_SOFTWARE, shadowPaint);
        shadowPaint.setShadowLayer(ConversionUtils.dpToPx(shadowRadius), 0, ConversionUtils.dpToPx(shadowOffset), shadowColor);

        setFocusable(true);
        setClickable(true);

        targetCurrentUserScales = new ArrayList<>();
    }

    public Paint getPaint() {
        return paint;
    }

    public Paint getShadowPaint() {
        return shadowPaint;
    }

    public void setShadowColor(int shadowColor) {
        this.shadowColor = shadowColor;
        shadowPaint.setShadowLayer(ConversionUtils.dpToPx(shadowRadius), 0, ConversionUtils.dpToPx(shadowOffset), shadowColor);

        if (centerItem != null)
            centerItem.circleImage = null;

        for (BaseRadialItem item : items) {
            item.circleImage = null;
        }

        postInvalidate();
    }

    public void setCenterItem(CenteredRadialItem item) {
        centerItem = item;
        centerItem.scale = 0;
        centerItem.setRadius(ConversionUtils.dpToPx(centerItem.size) / 2, shadowRadius + shadowOffset);
        clickCenterUp();
        postInvalidate();
    }

    public void setCenterListener(@Nullable CenterClickListener listener) {
        centerListener = listener;
    }

    public void setClickListener(@Nullable ClickListener listener) {
        this.listener = listener;
    }

    public List<BaseRadialItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * This method returns a builder to help set up the necessary parameters
     * for calculating item positions.
     *
     * @param items the items to add
     */
    public Builder setItems(List<BaseRadialItem> items) {
        this.items = items;
        if (items.size() < 1) {
            isReady = true;
            throw new EmptyListException("The list of RadialItems must have at least one item in it.");
        }

        isReady = false;

        return new Builder(this, items, true);
    }

    public Builder updateItems(final List<BaseRadialItem> items) {
        if (!isReady)
            throw new EmptyListException("Cannot update items before they are set.");
        if (items.size() < 1)
            throw new EmptyListException("The list of RadialItems must have at least one item in it.");

        return new Builder(this, items, false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isScrolling) {
            float newVelocityX = ((fingerX - offsetX) + (velocityX * 18)) / 21;
            float newVelocityY = ((fingerY - offsetY) + (velocityY * 18)) / 21;
            if (((int) newVelocityX != (int) velocityX || (int) newVelocityY != (int) velocityY)) {
                if (isFingerDown) {
                    velocityX = fingerX - offsetX;
                    velocityY = fingerY - offsetY;
                } else {
                    velocityX = newVelocityX;
                    velocityY = newVelocityY;
                }

                int distance = (RadialUtils.getRadius(maxRow + 1, itemRadius, itemSeparation) * 2) - Math.min(canvas.getWidth(), canvas.getHeight());
                float boundedX = Math.max(-distance / 2, Math.min(distance / 2, offsetX));
                float boundedY = Math.max(-distance / 2, Math.min(distance / 2, offsetY));

                offsetX = (((offsetX + velocityX) * 3) + boundedX) / 4;
                offsetY = (((offsetY + velocityY) * 3) + boundedY) / 4;
                fingerX = offsetX + velocityX / 2;
                fingerY = offsetY + velocityY / 2;

                if (!isFingerDown) {
                    lastX = offsetX;
                    lastY = offsetY;
                }
            } else {
                isScrolling = false;
                fingerX = offsetX;
                fingerY = offsetY;
                velocityX = 0;
                velocityY = 0;
            }
        }

        if (centerItem != null) {
            if (targetCurrentUserScales.size() > 0) {
                if (targetCurrentUserScales.size() > 1 && Math.abs(centerItem.scale - targetCurrentUserScales.get(0)) < 0.01)
                    targetCurrentUserScales.remove(0);

                centerItem.scale = (targetCurrentUserScales.get(0) + (centerItem.scale * 5)) / 6;
            }

            float nScale = 0;
            float distance = (float) Math.sqrt(Math.pow(offsetX + centerItem.radius, 2) + Math.pow(offsetY + centerItem.radius, 2));
            int totalRadius = (canvas.getWidth() + canvas.getHeight()) / 4;
            if (distance < totalRadius) {
                nScale = Math.min((float) (Math.sqrt(totalRadius - distance) / Math.sqrt(centerItem.radius * 2)) * centerItem.scale, centerItem.scale);
            }

            if (nScale > 0) {
                Matrix matrix = centerItem.getMatrix(canvas.getWidth(), canvas.getHeight(), offsetX, offsetY);
                if (matrix != null)
                    canvas.drawBitmap(centerItem.getCircleImage(this, shadowRadius + shadowOffset), matrix, paint);
            }
        }

        boolean needsFrame = false;

        if (isReady && canvas.getWidth() > 0 && canvas.getHeight() > 0 && getWidth() > 0 && getHeight() > 0) {
            isFirstDrawn = true;

            for (int i = 0; i < items.size(); i++) {
                BaseRadialItem item = items.get(i);
                Matrix matrix = item.getMatrix(canvas.getWidth(), canvas.getHeight(), offsetX - (shadowOffset + shadowRadius), offsetY - (shadowOffset + shadowRadius));
                if (matrix != null)
                    canvas.drawBitmap(item.getCircleImage(this, shadowRadius + shadowOffset), matrix, paint);

                item.nextFrame(this);
                if (!needsFrame)
                    needsFrame = item.needsFrame();
            }
        }

        if ((centerItem != null && targetCurrentUserScales.size() > 0 && (targetCurrentUserScales.size() > 1 || Math.abs(targetCurrentUserScales.get(0) - centerItem.scale) >= 0.01))
                || offsetX != 0 || offsetY != 0 || needsFrame || !isFirstDrawn) {
            postInvalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isFingerDown = true;
                downX = event.getX();
                downY = event.getY();
                if (!isDown) {
                    isDown = true;
                    handler.removeCallbacks(upRunnable);
                } else isIgnorant = true;
                isDragged = false;

                if (Math.sqrt(Math.pow((getWidth() / 2) - downX + offsetX, 2) + Math.pow((getHeight() / 2) - downY + offsetY, 2)) < centerItem.radius) {
                    clickCenterDown();

                    for (BaseRadialItem item : items) {
                        item.clickUp(this);
                    }
                } else {
                    clickCenterUp();

                    for (BaseRadialItem item : items) {
                        float itemX = (getWidth() / 2) + item.getX() + offsetX;
                        float itemY = (getHeight() / 2) + item.getY() + offsetY;
                        if (downX > itemX && downX - itemX < item.radius * 2 && downY > itemY && downY - itemY < item.radius * 2) {
                            item.clickDown(this);
                        } else item.clickUp(this);
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                isFingerDown = true;
                handler.removeCallbacks(upRunnable);
                int width = getWidth(), height = getHeight();
                int distance = (RadialUtils.getRadius(maxRow + 1, itemRadius, itemSeparation) * 2) - Math.min(width, height);
                if (distance > 0 && (ConversionUtils.pxToDp((int) Math.abs(event.getX() - downX)) * ConversionUtils.pxToDp((int) Math.abs(event.getY() - downY)) >= 64 || isDragged)) {
                    isDragged = true;
                    isScrolling = true;
                    fingerX = Math.max(-distance / 2, Math.min(distance / 2, event.getX() - downX + lastX));
                    fingerY = Math.max(-distance / 2, Math.min(distance / 2, event.getY() - downY + lastY));

                    clickCenterUp();
                    for (BaseRadialItem item : items) {
                        item.clickUp(this);
                    }
                    return true;
                } else return false;
            case MotionEvent.ACTION_CANCEL:
                isFingerDown = false;
                handler.removeCallbacks(upRunnable);
                handler.post(upRunnable);
                isDragged = false;

                clickCenterUp();
                for (BaseRadialItem item : items) {
                    item.clickUp(this);
                }
                break;
            case MotionEvent.ACTION_UP:
                isFingerDown = false;
                for (BaseRadialItem item : items) {
                    item.clickUp(this);
                }

                if (ConversionUtils.pxToDp((int) Math.abs(event.getX() - downX)) * ConversionUtils.pxToDp((int) Math.abs(event.getY() - downY)) < 64 && !isDragged) {
                    if (!isIgnorant)
                        isDown = false;

                    float eventX = event.getX();
                    float eventY = event.getY();

                    if (Math.sqrt(Math.pow((getWidth() / 2) - eventX + offsetX, 2) + Math.pow((getHeight() / 2) - eventY + offsetY, 2)) < centerItem.radius) {
                        if (centerListener != null)
                            centerListener.onCenterClick(this);

                        clickCenterBack();
                    } else {
                        clickCenterUp();

                        for (int i = 0; i < items.size(); i++) {
                            BaseRadialItem item = items.get(i);
                            float itemX = (getWidth() / 2) + item.getX() + offsetX;
                            float itemY = (getHeight() / 2) + item.getY() + offsetY;
                            if (eventX > itemX && eventX - itemX < item.radius * 2 && eventY > itemY && eventY - itemY < item.radius * 2) {
                                item.clickBack(this);

                                if (listener != null)
                                    listener.onClick(this, item, i);

                                break;
                            }
                        }
                    }
                } else {
                    clickCenterUp();
                    handler.removeCallbacks(upRunnable);
                    lastX = offsetX;
                    lastY = offsetY;
                    handler.postDelayed(upRunnable, 2000);
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private void clickCenterDown() {
        targetCurrentUserScales.clear();
        targetCurrentUserScales.add(CLICK_DOWN_SCALE);

        postInvalidate();
    }

    private void clickCenterUp() {
        targetCurrentUserScales.clear();
        targetCurrentUserScales.add(1f);

        postInvalidate();
    }

    private void clickCenterBack() {
        targetCurrentUserScales.clear();
        targetCurrentUserScales.add(CLICK_UP_SCALE);
        targetCurrentUserScales.add(1f);

        postInvalidate();
    }

    public interface CenterClickListener {
        void onCenterClick(RadialLayoutView layout);
    }

    public interface ClickListener {
        void onClick(RadialLayoutView layout, BaseRadialItem item, int index);
    }

    public static class Builder {

        private RadialLayoutView view;
        private List<BaseRadialItem> items;

        private float itemRadius;
        private float itemRadiusVariation;
        private float itemSeparation;
        private float shadowRadius;
        private float shadowOffset;

        private OnAppliedListener listener;
        private boolean isFirstTime;

        private Builder(RadialLayoutView view, List<BaseRadialItem> items, boolean isFirstTime) {
            this.view = view;
            this.items = items;
            itemRadius = view.itemRadius;
            itemRadiusVariation = 6;
            itemSeparation = view.itemSeparation;
            shadowRadius = view.shadowRadius;
            shadowOffset = view.shadowOffset;
            this.isFirstTime = isFirstTime;
        }

        public Builder withItemRadius(float radius) {
            itemRadius = radius;
            return this;
        }

        public Builder withItemRadiusVariation(float variation) {
            itemRadiusVariation = variation;
            return this;
        }

        public Builder withItemSeparation(float separation) {
            itemSeparation = separation;
            return this;
        }

        public Builder withShadowRadius(float radius) {
            shadowRadius = radius;
            return this;
        }

        public Builder withShadowOffset(float offset) {
            shadowOffset = offset;
            return this;
        }

        public void apply() {
            apply(null);
        }

        public void apply(@Nullable OnAppliedListener listener) {
            this.listener = listener;

            new Action<List<BaseRadialItem>>() {
                @NonNull
                @Override
                public String id() {
                    return "radialItems";
                }

                @Nullable
                @Override
                protected List<BaseRadialItem> run() {
                    return applySynchronous(false);
                }

                @Override
                protected void done(@Nullable List<BaseRadialItem> result) {
                    onApplied(result);
                }
            }.execute();
        }

        public void applySynchronous() {
            applySynchronous(true);
        }

        private List<BaseRadialItem> applySynchronous(boolean isActuallySynchronous) {
            List<BaseRadialItem> items = new ArrayList<>();
            if (isFirstTime)
                items.addAll(Builder.this.items);
            else {
                for (BaseRadialItem item : Builder.this.items)
                    items.add(item.copy());
            }

            Collections.sort(items, new Comparator<BaseRadialItem>() {
                @Override
                public int compare(BaseRadialItem o1, BaseRadialItem o2) {
                    return o1.size - o2.size; //sort small -> big
                }
            });

            for (int i = 0; i < items.size(); i++) {
                int radius = ConversionUtils.dpToPx((itemRadius - (itemRadiusVariation * 2)) + (itemRadiusVariation * 2 * ((float) i / items.size())));
                //Log.d("Radial", "Item: " + i + ", Size: " + items.get(i).size + ", Radius: " + radius);
                BaseRadialItem item = items.get(i);
                if (isFirstTime)
                    item.setRadius(radius, shadowRadius + shadowOffset);
                else if (item.radius <= 0) //only update radius for new items, reduces memory usage & makes transition smoother
                    item.radius = radius;

                if (shadowRadius != view.shadowRadius || shadowOffset != view.shadowOffset) {
                    item.scaledImage = null;
                    item.circleImage = null;
                }
            }

            Collections.sort(items, new Comparator<BaseRadialItem>() {
                @Override
                public int compare(BaseRadialItem o1, BaseRadialItem o2) {
                    return o1.distance - o2.distance; //sort small -> big
                }
            });

            int size = 0, circumference = RadialUtils.getCircumference(0, itemRadius, itemSeparation), usedCircumference = 0;
            for (int i = 0; i < items.size(); i++) {
                BaseRadialItem item = items.get(i);
                if (usedCircumference + (item.radius * 2) + (ConversionUtils.dpToPx(8) * i) < circumference) {
                    usedCircumference += (item.radius * 2) + ConversionUtils.dpToPx(8);
                    item.row = 0;
                    size++;

                    //Log.d("Radial", "Item: " + items.indexOf(item) + ", Row: 0");
                } else break;
            }

            int padding = ((circumference - usedCircumference) / (size + 1)) + ConversionUtils.dpToPx(8);
            items.get(0).radian = -Math.PI / 2;
            for (int i = 1; i < size; i++) {
                BaseRadialItem item = items.get(i), previousItem = items.get(i - 1);
                float difference = previousItem.radius + item.radius;
                int radius = RadialUtils.getRadius(0, itemRadius, itemSeparation);
                double cosine = ((2 * Math.pow(radius, 2)) - Math.pow(difference, 2)) / (2 * Math.pow(radius, 2));
                item.radian = previousItem.radian + Math.acos(cosine) + (((double) padding / circumference) * 2 * Math.PI);
                //Log.d("Radial", "Row: 0, Item: " + i + ", Difference: " + difference + ", Cosine: " + cosine + ", Degrees: " + (item.radian * 180 / Math.PI));
            }

            for (int i = 1; true; i++) {
                int rowStart = size;

                if (rowStart == 0 || rowStart >= items.size())
                    break;
                else view.maxRow = i;

                int rowSize = 0, rowCircumference = RadialUtils.getCircumference(i, itemRadius, itemSeparation), rowUsedCircumference = 0;
                for (int i2 = rowStart; i2 < items.size(); i2++) {
                    BaseRadialItem item = items.get(i2);
                    if (rowUsedCircumference + (item.radius * 2) + (ConversionUtils.dpToPx(itemSeparation) * i) < rowCircumference) {
                        rowUsedCircumference += (item.radius * 2) + ConversionUtils.dpToPx(itemSeparation);
                        item.row = i;
                        rowSize++;

                        //Log.d("Radial", "Row: " + item.row + ", Item: " + i2);
                    } else break;
                }

                int rowPadding = ((rowCircumference - rowUsedCircumference) / (rowSize + 1)) + ConversionUtils.dpToPx(8);
                items.get(rowStart).radian = (items.get(rowStart - 1).radian + items.get(rowStart - 2).radian) / 2;

                for (int i2 = rowStart; i2 < rowStart + rowSize; i2++) {
                    BaseRadialItem item, previousItem = items.get(i2 - 1);
                    try {
                        item = items.get(i2);
                    } catch (IndexOutOfBoundsException e) {
                        break;
                    }

                    float difference = previousItem.radius + item.radius;
                    int radius = RadialUtils.getRadius(i, itemRadius, itemSeparation);
                    double cosine = ((2 * Math.pow(radius, 2)) - Math.pow(difference, 2)) / (2 * Math.pow(radius, 2));
                    item.radian = previousItem.radian + Math.acos(cosine) + (((double) rowPadding / rowCircumference) * 2 * Math.PI);
                    //Log.d("Radial", "Row: " + item.row + ", Item: " + i2 + ", Difference: " + difference + ", Cosine: " + cosine + ", Degrees: " + (item.radian * 180 / Math.PI));
                }

                size += rowSize;
            }

            if (isActuallySynchronous)
                onApplied(items);

            return items;
        }

        private void onApplied(List<BaseRadialItem> result) {
            if (view.isReady && result != null && !isFirstTime) {
                for (int i = 0; i < result.size(); i++) {
                    BaseRadialItem item = result.get(i);
                    item.itemRadius = itemRadius;
                    item.itemSeparation = itemSeparation;

                    if (i < view.items.size())
                        view.items.get(i).animateTo(item, view, shadowRadius + shadowOffset);
                    else {
                        view.items.add(item);
                        item.scale = 0;
                        item.targetRadian = item.radian;
                        item.clickUp(view);
                    }
                }

                for (int i = result.size(); i < view.items.size(); i++) {
                    view.items.get(i).removeFrom(view);
                }
            } else {
                for (BaseRadialItem item : view.items) {
                    item.itemRadius = itemRadius;
                    item.itemSeparation = itemSeparation;
                    //Log.d("Radial", "Item: " + RadialLayout.this.items.indexOf(item) + ", X: " + item.getX() + ", Y: " + item.getY());
                    item.scale = 0;
                    item.targetRadian = item.radian;
                    item.clickUp(view);
                }

                view.isReady = true;
            }

            if (view.centerItem != null && (view.shadowRadius != shadowRadius || view.shadowOffset != shadowOffset)) {
                view.centerItem.scaledImage = null;
                view.centerItem.circleImage = null;
            }

            view.itemRadius = itemRadius;
            view.itemSeparation = itemSeparation;
            view.shadowRadius = shadowRadius;
            view.shadowOffset = shadowOffset;
            view.shadowPaint.setShadowLayer(ConversionUtils.dpToPx(shadowRadius), 0, ConversionUtils.dpToPx(shadowOffset), view.shadowColor);
            view.postInvalidate();

            if (listener != null)
                listener.onApplied(view);
        }

        public interface OnAppliedListener {
            void onApplied(RadialLayoutView view);
        }
    }

    public static class EmptyListException extends RuntimeException {
        private EmptyListException(String s) {
            super(s);
        }
    }

}