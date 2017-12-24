package james.radiallayout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;

import com.afollestad.async.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import james.radiallayout.utils.ConversionUtils;
import james.radiallayout.utils.ImageUtils;
import james.radiallayout.utils.RadialUtils;

/**
 * A layout that arranges its items in circles.
 */
public class RadialLayout extends View {

    /**
     * The radius of the circles in dp. Must be greater than 12, as the value
     * can vary by +/- 6 based on scale.
     */
    public static final int CIRCLE_RADIUS = 36;
    public static final int ITEM_SEPARATION = 8;

    public static final float CLICK_DOWN_SCALE = 0.8f;
    public static final float CLICK_UP_SCALE = 1.2f;

    private Paint paint;
    private Paint outlinePaint;
    private List<RadialItem> items;
    private boolean isReady;

    //private ValueAnimator animator;
    private boolean isScrolling;
    private float offsetX, offsetY;
    private float velocityX, velocityY;
    private float fingerX, fingerY;
    private float lastX, lastY;
    private int maxRow;

    private float downX, downY;
    private boolean isDown, isIgnorant, isDragged;
    private Handler handler = new Handler();
    private Runnable upRunnable = new Runnable() {
        @Override
        public void run() {
            isDown = false;
            isIgnorant = false;
            lastX = 0;
            lastY = 0;
            velocityX = 0;
            velocityY = 0;

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (!isDown) {
                        if (offsetX > 0.001 || offsetY > 0.001) {
                            offsetX /= 1.2;
                            offsetY /= 1.2;
                            handler.postDelayed(this, 10);
                        } else {
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

    private Bitmap currentUser;
    private float currentUserScale;
    private int currentUserRadius;
    private ValueAnimator currentUserAnimator;

    private MeClickListener meListener;
    private ClickListener listener;

    public RadialLayout(@NonNull Context context) {
        this(context, null);
    }

    public RadialLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadialLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        items = new ArrayList<>();
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);

        outlinePaint = new Paint();
        outlinePaint.setAntiAlias(true);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(ConversionUtils.dpToPx(3));
        outlinePaint.setColor(ContextCompat.getColor(context, R.color.colorAccent));

        setFocusable(true);
        setClickable(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1) != 1) {
            try {
                ValueAnimator.class.getMethod("setDurationScale", float.class).invoke(null, 1f); //force animator duration (ignore developer options)
            } catch (Throwable t) {
            }
        }

        /*animator = ValueAnimator.ofFloat(0, 2 * (float) Math.PI);
        animator.setDuration(500000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                if (isReady) {
                    for (RadialItem item : items) {
                        item.radianOffset = (float) valueAnimator.getAnimatedValue();
                    }
                }
            }
        });
        animator.start();*/

        currentUserRadius = ConversionUtils.dpToPx(CIRCLE_RADIUS + 6);
    }

    public void setMeBitmap(Bitmap bitmap) {
        int size = ConversionUtils.dpToPx(CIRCLE_RADIUS * 2);
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, size, size);

        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
        drawable.setCornerRadius(size / 2);
        drawable.setAntiAlias(true);

        currentUser = ImageUtils.drawableToBitmap(drawable);
        currentUserScale = 0;
        clickMeUp();

        invalidate();
    }

    public void setMeListener(@Nullable MeClickListener listener) {
        meListener = listener;
    }

    public void setClickListener(@Nullable ClickListener listener) {
        this.listener = listener;
    }

    public List<RadialItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * This method begins the necessary calculations to display the items
     * in a background thread and adds them to the view when it has finished.
     *
     * @param items the items to add
     */
    public void setItems(List<RadialItem> items) {
        this.items = items;
        if (items.size() < 1) {
            isReady = true;
            return;
        }

        isReady = false;

        new Action() {
            @NonNull
            @Override
            public String id() {
                return "radialItems";
            }

            @Nullable
            @Override
            protected Object run() throws InterruptedException {
                List<RadialItem> items = new ArrayList<>(RadialLayout.this.items);

                Collections.sort(items, new Comparator<RadialItem>() {
                    @Override
                    public int compare(RadialItem o1, RadialItem o2) {
                        return o1.size - o2.size; //sort small -> big
                    }
                });

                for (int i = 0; i < items.size(); i++) {
                    int radius = ConversionUtils.dpToPx((CIRCLE_RADIUS - 12) + (12 * ((float) i / items.size())));
                    //Log.d("Radial", "Item: " + i + ", Size: " + items.get(i).size + ", Radius: " + radius);
                    items.get(i).setRadius(radius);
                }

                Collections.sort(items, new Comparator<RadialItem>() {
                    @Override
                    public int compare(RadialItem o1, RadialItem o2) {
                        return o1.distance - o2.distance; //sort small -> big
                    }
                });

                int size = 0, circumference = RadialUtils.getCircumference(0), usedCircumference = 0;
                for (int i = 0; i < items.size(); i++) {
                    RadialItem item = items.get(i);
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
                    RadialItem item = items.get(i), previousItem = items.get(i - 1);
                    float difference = previousItem.radius + item.radius;
                    double cosine = ((2 * Math.pow(RadialUtils.getRadius(0), 2)) - Math.pow(difference, 2)) / (2 * Math.pow(RadialUtils.getRadius(0), 2));
                    item.radian = previousItem.radian + Math.acos(cosine) + (((double) padding / circumference) * 2 * Math.PI);
                    //Log.d("Radial", "Row: 0, Item: " + i + ", Difference: " + difference + ", Cosine: " + cosine + ", Degrees: " + (item.radian * 180 / Math.PI));
                }

                for (int i = 1; true; i++) {
                    int rowStart = size;

                    if (rowStart == 0 || rowStart >= items.size())
                        break;
                    else maxRow = i;

                    int rowSize = 0, rowCircumference = RadialUtils.getCircumference(i), rowUsedCircumference = 0;
                    for (int i2 = rowStart; i2 < items.size(); i2++) {
                        RadialItem item = items.get(i2);
                        if (rowUsedCircumference + (item.radius * 2) + (ConversionUtils.dpToPx(ITEM_SEPARATION) * i) < rowCircumference) {
                            rowUsedCircumference += (item.radius * 2) + ConversionUtils.dpToPx(ITEM_SEPARATION);
                            item.row = i;
                            rowSize++;

                            //Log.d("Radial", "Row: " + item.row + ", Item: " + i2);
                        } else break;
                    }

                    int rowPadding = ((rowCircumference - rowUsedCircumference) / (rowSize + 1)) + ConversionUtils.dpToPx(8);
                    items.get(rowStart).radian = (items.get(rowStart - 1).radian + items.get(rowStart - 2).radian) / 2;

                    for (int i2 = rowStart; i2 < rowStart + rowSize; i2++) {
                        RadialItem item, previousItem = items.get(i2 - 1);
                        try {
                            item = items.get(i2);
                        } catch (IndexOutOfBoundsException e) {
                            break;
                        }

                        float difference = previousItem.radius + item.radius;
                        double cosine = ((2 * Math.pow(RadialUtils.getRadius(i), 2)) - Math.pow(difference, 2)) / (2 * Math.pow(RadialUtils.getRadius(i), 2));
                        item.radian = previousItem.radian + Math.acos(cosine) + (((double) rowPadding / rowCircumference) * 2 * Math.PI);
                        //Log.d("Radial", "Row: " + item.row + ", Item: " + i2 + ", Difference: " + difference + ", Cosine: " + cosine + ", Degrees: " + (item.radian * 180 / Math.PI));
                    }

                    size += rowSize;
                }

                return null;
            }

            @Override
            protected void done(@Nullable Object result) {
                int width = getWidth(), height = getHeight();
                if (width > 0 && height > 0) {
                    for (RadialItem item : RadialLayout.this.items) {
                        //Log.d("Radial", "Item: " + RadialLayout.this.items.indexOf(item) + ", X: " + item.getX() + ", Y: " + item.getY());
                        item.scale = 0;
                        item.clickUp();
                    }

                    isReady = true;
                    invalidate();
                } else {
                    getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            for (RadialItem item : RadialLayout.this.items) {
                                //Log.d("Radial", "Item: " + RadialLayout.this.items.indexOf(item) + ", X: " + item.getX() + ", Y: " + item.getY());
                                item.scale = 0;
                                item.clickUp();
                            }

                            isReady = true;
                            invalidate();

                            getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
                }
            }
        }.execute();
    }

    public void updateItems(final List<RadialItem> items) {
        if (!isReady) {
            Log.e("RadialLayout", "Cannot update items before they are set");
            return;
        }

        if (items.size() < 1) {
            RadialLayout.this.items = items;
            return;
        }

        new Action<List<RadialItem>>() {
            @NonNull
            @Override
            public String id() {
                return "radialItems";
            }

            @Nullable
            @Override
            protected List<RadialItem> run() throws InterruptedException {
                List<RadialItem> newItems = new ArrayList<>();
                for (RadialItem item : items) {
                    newItems.add(new RadialItem(item));
                }

                Collections.sort(newItems, new Comparator<RadialItem>() {
                    @Override
                    public int compare(RadialItem o1, RadialItem o2) {
                        return o1.size - o2.size; //sort small -> big
                    }
                });

                for (int i = 0; i < newItems.size(); i++) {
                    int radius = ConversionUtils.dpToPx((CIRCLE_RADIUS - 12) + (12 * ((float) i / newItems.size())));
                    //Log.d("Radial", "Item: " + i + ", Size: " + items.get(i).size + ", Radius: " + radius);
                    RadialItem item = newItems.get(i);
                    if (item.radius <= 0)
                        newItems.get(i).radius = radius; //only change radius for new items to reduce memory usage
                }

                Collections.sort(newItems, new Comparator<RadialItem>() {
                    @Override
                    public int compare(RadialItem o1, RadialItem o2) {
                        return o1.distance - o2.distance; //sort small -> big
                    }
                });

                int size = 0, circumference = RadialUtils.getCircumference(0), usedCircumference = 0;
                for (int i = 0; i < newItems.size(); i++) {
                    RadialItem item = newItems.get(i);
                    if (usedCircumference + (item.radius * 2) + (ConversionUtils.dpToPx(ITEM_SEPARATION) * i) < circumference) {
                        usedCircumference += (item.radius * 2) + ConversionUtils.dpToPx(ITEM_SEPARATION);
                        item.row = 0;
                        size++;

                        //Log.d("Radial", "Item: " + items.indexOf(item) + ", Row: 0");
                    } else break;
                }

                int padding = ((circumference - usedCircumference) / (size + 1)) + ConversionUtils.dpToPx(8);
                newItems.get(0).radian = -Math.PI / 2;
                for (int i = 1; i < size; i++) {
                    RadialItem item = newItems.get(i), previousItem = newItems.get(i - 1);
                    float difference = previousItem.radius + item.radius;
                    double cosine = ((2 * Math.pow(RadialUtils.getRadius(0), 2)) - Math.pow(difference, 2)) / (2 * Math.pow(RadialUtils.getRadius(0), 2));
                    item.radian = previousItem.radian + Math.acos(cosine) + (((double) padding / circumference) * 2 * Math.PI);
                    //Log.d("Radial", "Row: 0, Item: " + i + ", Difference: " + difference + ", Cosine: " + cosine + ", Degrees: " + (item.radian * 180 / Math.PI));
                }

                for (int i = 1; true; i++) {
                    int rowStart = size;

                    if (rowStart == 0 || rowStart >= newItems.size())
                        break;
                    else maxRow = i;

                    int rowSize = 0, rowCircumference = RadialUtils.getCircumference(i), rowUsedCircumference = 0;
                    for (int i2 = rowStart; i2 < newItems.size(); i2++) {
                        RadialItem item = newItems.get(i2);
                        if (rowUsedCircumference + (item.radius * 2) + (ConversionUtils.dpToPx(8) * i) < rowCircumference) {
                            rowUsedCircumference += (item.radius * 2) + ConversionUtils.dpToPx(8);
                            item.row = i;
                            rowSize++;

                            //Log.d("Radial", "Row: " + item.row + ", Item: " + i2);
                        } else break;
                    }

                    int rowPadding = ((rowCircumference - rowUsedCircumference) / (rowSize + 1)) + ConversionUtils.dpToPx(8);
                    newItems.get(rowStart).radian = (newItems.get(rowStart - 1).radian + newItems.get(rowStart - 2).radian) / 2;

                    for (int i2 = rowStart; i2 < rowStart + rowSize; i2++) {
                        RadialItem item, previousItem = newItems.get(i2 - 1);
                        try {
                            item = newItems.get(i2);
                        } catch (IndexOutOfBoundsException e) {
                            break;
                        }

                        float difference = previousItem.radius + item.radius;
                        double cosine = ((2 * Math.pow(RadialUtils.getRadius(i), 2)) - Math.pow(difference, 2)) / (2 * Math.pow(RadialUtils.getRadius(i), 2));
                        item.radian = previousItem.radian + Math.acos(cosine) + (((double) rowPadding / rowCircumference) * 2 * Math.PI);
                        //Log.d("Radial", "Row: " + item.row + ", Item: " + i2 + ", Difference: " + difference + ", Cosine: " + cosine + ", Degrees: " + (item.radian * 180 / Math.PI));
                    }

                    size += rowSize;
                }

                return newItems;
            }

            @Override
            protected void done(@Nullable List<RadialItem> result) {
                if (isReady && result != null) {
                    for (int i = 0; i < result.size(); i++) {
                        if (i < RadialLayout.this.items.size())
                            RadialLayout.this.items.get(i).animateTo(result.get(i), RadialLayout.this);
                        else {
                            RadialItem newItem = result.get(i);
                            RadialLayout.this.items.add(newItem);
                            newItem.scale = 0;
                            newItem.clickUp();
                        }
                    }

                    for (int i = result.size(); i < RadialLayout.this.items.size(); i++) {
                        RadialLayout.this.items.get(i).removeFrom(RadialLayout.this);
                    }
                }
            }
        }.execute();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isScrolling) {
            float newVelocityX = (fingerX - offsetX + (velocityX * 4)) / 6;
            float newVelocityY = (fingerY - offsetY + (velocityY * 4)) / 6;
            if ((int) newVelocityX != (int) velocityX || (int) newVelocityY != (int) velocityY) {
                velocityX = newVelocityX;
                velocityY = newVelocityY;
                offsetX += velocityX;
                offsetY += velocityY;
                fingerX = offsetX + velocityX / 2;
                fingerY = offsetY + velocityY / 2;
            } else {
                isScrolling = false;
                fingerX = offsetX;
                fingerY = offsetY;
            }
        }

        if (currentUser != null) {
            float nScale = 0;
            float distance = (float) Math.sqrt(Math.pow(offsetX + currentUserRadius, 2) + Math.pow(offsetY + currentUserRadius, 2));
            int totalRadius = Math.min(canvas.getWidth(), canvas.getHeight()) / 2;
            if (distance < totalRadius) {
                nScale = Math.min((float) (Math.sqrt(totalRadius - distance) / Math.sqrt(currentUserRadius * 2)) * currentUserScale, currentUserScale);
            }

            if (nScale > 0) {
                Matrix matrix = new Matrix();
                matrix.preScale(nScale, nScale, currentUser.getWidth() / 2, currentUser.getHeight() / 2);
                matrix.postTranslate(((canvas.getWidth() - currentUser.getWidth()) / 2) + offsetX, ((canvas.getHeight() - currentUser.getHeight()) / 2) + offsetY);
                canvas.drawBitmap(currentUser, matrix, paint);

                canvas.drawCircle((canvas.getWidth() / 2) + offsetX, (canvas.getHeight() / 2) + offsetY, currentUserRadius * nScale, outlinePaint);
            }
        }

        if (isReady) {
            for (RadialItem item : items) {
                Matrix matrix = item.getMatrix(canvas.getWidth(), canvas.getHeight(), offsetX, offsetY);
                if (matrix != null)
                    canvas.drawBitmap(item.getCircleImage(getResources()), matrix, paint);
            }

            postInvalidate();
        } else if (currentUser != null && currentUserScale < 1)
            postInvalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                if (!isDown) {
                    isDown = true;
                    handler.removeCallbacks(upRunnable);
                } else isIgnorant = true;
                isDragged = false;

                if (Math.sqrt(Math.pow((getWidth() / 2) - downX + offsetX, 2) + Math.pow((getHeight() / 2) - downY + offsetY, 2)) < currentUserRadius) {
                    clickMeDown();

                    for (RadialItem item : items) {
                        item.clickUp();
                    }
                } else {
                    clickMeUp();

                    for (RadialItem item : items) {
                        float itemX = (getWidth() / 2) + item.getX() + offsetX;
                        float itemY = (getHeight() / 2) + item.getY() + offsetY;
                        if (downX > itemX && downX - itemX < item.radius * 2 && downY > itemY && downY - itemY < item.radius * 2) {
                            item.clickDown();
                        } else item.clickUp();
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                handler.removeCallbacks(upRunnable);
                int width = getWidth(), height = getHeight();
                int distance = (RadialUtils.getRadius(maxRow + 1) * 2) - Math.min(width, height);
                if (distance > 0 && (ConversionUtils.pxToDp((int) Math.abs(event.getX() - downX)) * ConversionUtils.pxToDp((int) Math.abs(event.getY() - downY)) >= 64 || isDragged)) {
                    isDragged = true;
                    isScrolling = true;
                    fingerX = Math.max(-distance / 2, Math.min(distance / 2, event.getX() - downX + lastX));
                    fingerY = Math.max(-distance / 2, Math.min(distance / 2, event.getY() - downY + lastY));

                    clickMeUp();
                    for (RadialItem item : items) {
                        item.clickUp();
                    }
                    return true;
                } else return false;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(upRunnable);
                handler.post(upRunnable);
                isDragged = false;

                clickMeUp();
                for (RadialItem item : items) {
                    item.clickUp();
                }
                break;
            case MotionEvent.ACTION_UP:
                for (RadialItem item : items) {
                    item.clickUp();
                }

                if (ConversionUtils.pxToDp((int) Math.abs(event.getX() - downX)) * ConversionUtils.pxToDp((int) Math.abs(event.getY() - downY)) < 64 && !isDragged) {
                    if (!isIgnorant)
                        isDown = false;

                    float eventX = event.getX();
                    float eventY = event.getY();

                    if (Math.sqrt(Math.pow((getWidth() / 2) - eventX + offsetX, 2) + Math.pow((getHeight() / 2) - eventY + offsetY, 2)) < currentUserRadius) {
                        if (meListener != null)
                            meListener.onMeClick(this);

                        clickMeBack();
                    } else {
                        clickMeUp();

                        for (int i = 0; i < items.size(); i++) {
                            RadialItem item = items.get(i);
                            float itemX = (getWidth() / 2) + item.getX() + offsetX;
                            float itemY = (getHeight() / 2) + item.getY() + offsetY;
                            if (eventX > itemX && eventX - itemX < item.radius * 2 && eventY > itemY && eventY - itemY < item.radius * 2) {
                                item.clickBack();

                                if (listener != null)
                                    listener.onClick(this, item, i);

                                break;
                            }
                        }
                    }
                } else {
                    clickMeUp();
                    handler.removeCallbacks(upRunnable);
                    lastX = offsetX;
                    lastY = offsetY;
                    handler.postDelayed(upRunnable, 2000);
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private void clickMeDown() {
        if (currentUserAnimator != null && currentUserAnimator.isStarted())
            currentUserAnimator.cancel();

        currentUserAnimator = ValueAnimator.ofFloat(currentUserScale, CLICK_DOWN_SCALE);
        currentUserAnimator.setInterpolator(new DecelerateInterpolator());
        currentUserAnimator.setDuration(350);
        currentUserAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                currentUserScale = (float) valueAnimator.getAnimatedValue();
            }
        });
        currentUserAnimator.start();
    }

    private void clickMeUp() {
        if (currentUserAnimator != null && currentUserAnimator.isStarted())
            currentUserAnimator.cancel();

        currentUserAnimator = ValueAnimator.ofFloat(currentUserScale, 1);
        currentUserAnimator.setInterpolator(new DecelerateInterpolator());
        currentUserAnimator.setDuration(350);
        currentUserAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                currentUserScale = (float) valueAnimator.getAnimatedValue();
            }
        });
        currentUserAnimator.start();
    }

    private void clickMeBack() {
        if (currentUserAnimator != null && currentUserAnimator.isStarted())
            currentUserAnimator.cancel();

        currentUserAnimator = ValueAnimator.ofFloat(currentUserScale, CLICK_UP_SCALE, 1);
        currentUserAnimator.setInterpolator(new DecelerateInterpolator());
        currentUserAnimator.setDuration(350);
        currentUserAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                currentUserScale = (float) valueAnimator.getAnimatedValue();
            }
        });
        currentUserAnimator.start();
    }

    public interface MeClickListener {
        void onMeClick(RadialLayout layout);
    }

    public interface ClickListener {
        void onClick(RadialLayout layout, RadialItem item, int index);
    }

    /**
     * A view container specifying info such as the size to scale the image
     * relative to the rest of the items and the distance that the view should be
     * from the center of the layout.
     */
    public static class RadialItem {

        private String id;
        private Bitmap image;
        private Bitmap scaledImage;
        private Bitmap circleImage;
        private int size;
        private int distance;
        private float radius;
        private int row;
        private double radian;
        private double radianOffset;
        private ValueAnimator animator;
        private float scale;

        /**
         * Creates a new container specifying info such as the size to scale the image
         * relative to the rest of the content and the distance the item should be
         * from the center.
         *
         * @param id       some arbitrary identifier
         * @param image    the bitmap to be displayed
         * @param size     the size to scale the image relative to other items
         * @param distance the distance from the center to position the image at relative to other items
         */
        public RadialItem(String id, Bitmap image, int size, int distance) {
            this.id = id;
            this.image = image;
            this.size = size;
            this.distance = distance;
            scale = 1;
        }

        public RadialItem(RadialItem item) {
            id = item.id;
            image = item.image;
            size = item.size;
            distance = item.distance;
            radius = item.radius;
            scaledImage = item.scaledImage;
            circleImage = item.circleImage;
        }

        public String getId() {
            return id;
        }

        /**
         * Sets the radius of this RadialItem and creates a new scaled bitmap if the current one does not match
         * the required dimensions.
         *
         * @param radius the required radius of the circle
         */
        private void setRadius(float radius) {
            if (scaledImage == null || scaledImage.getWidth() != radius * 2 || scaledImage.getHeight() != radius * 2) {
                //Log.d("RadialLayout", "new " + (scaledImage == null ? "scaled bitmap" : "radius"));
                scaledImage = ThumbnailUtils.extractThumbnail(image, (int) (radius * 2), (int) (radius * 2));
            }
            this.radius = radius;
        }

        private float getX() {
            return (float) (RadialUtils.getRadius(row) * Math.sin((Math.PI / 2) - (radian + radianOffset))) - radius;
        }

        private float getY() {
            return (float) (RadialUtils.getRadius(row) * Math.sin(radian + radianOffset)) - radius;
        }

        /**
         * Creates a new circular bitmap if the current one does not match the required dimensions, and returns it.
         *
         * @param resources the resources of the current context
         * @return a circular image bitmap
         */
        private Bitmap getCircleImage(Resources resources) {
            if (circleImage == null || circleImage.getWidth() != radius * 2 || circleImage.getHeight() != radius * 2) {
                if (scaledImage == null)
                    setRadius(radius);

                RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(resources, scaledImage);
                roundedBitmapDrawable.setCornerRadius(radius);
                roundedBitmapDrawable.setAntiAlias(true);

                circleImage = ImageUtils.drawableToBitmap(roundedBitmapDrawable);
                //Log.d("RadialLayout", "new circle");
            }

            return circleImage;
        }

        /**
         * Creates a Matrix to scale the image to the correct dimensions on a Canvas.
         */
        private Matrix getMatrix(int canvasWidth, int canvasHeight, float offsetX, float offsetY) {
            float nScale = 0;
            float distance = (float) Math.sqrt(Math.pow(offsetX + getX() + radius, 2) + Math.pow(offsetY + getY() + radius, 2));
            int totalRadius = Math.min(canvasWidth, canvasHeight) / 2;
            if (distance < totalRadius) {
                nScale = Math.min((float) (Math.sqrt(totalRadius - distance) / Math.sqrt(radius * 2)) * scale, scale);
            }

            if (nScale > 0) {
                Matrix matrix = new Matrix();
                matrix.preScale(nScale, nScale, radius, radius);
                matrix.postTranslate((canvasWidth / 2) + offsetX + getX(), (canvasHeight / 2) + offsetY + getY());
                return matrix;
            } else return null;
        }

        /**
         * Animates this RadialItem to the dimensions and position of the parameter.
         *
         * @param item   the item to animate to the position of
         * @param layout the layout to be animated in
         */
        private void animateTo(RadialItem item, final RadialLayout layout) {
            image = item.image;
            scaledImage = item.scaledImage;
            circleImage = item.circleImage;
            row = item.row;
            size = item.size;
            distance = item.distance;

            ValueAnimator radiusAnimator = ValueAnimator.ofFloat(radius, item.radius);
            radiusAnimator.setInterpolator(new DecelerateInterpolator());
            radiusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    radius = (float) valueAnimator.getAnimatedValue();
                }
            });
            radiusAnimator.start();

            ValueAnimator radianAnimator = ValueAnimator.ofFloat((float) radian, (float) item.radian);
            radianAnimator.setInterpolator(new DecelerateInterpolator());
            radianAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    radian = (float) valueAnimator.getAnimatedValue();
                }
            });
            radianAnimator.start();

            setRadius(item.radius);
            getCircleImage(layout.getResources());
        }

        private void clickDown() {
            if (animator != null && animator.isStarted())
                animator.cancel();

            animator = ValueAnimator.ofFloat(scale, CLICK_DOWN_SCALE);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.setDuration(350);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    scale = (float) valueAnimator.getAnimatedValue();
                }
            });
            animator.start();
        }

        private void clickUp() {
            if (animator != null && animator.isStarted())
                animator.cancel();

            animator = ValueAnimator.ofFloat(scale, 1);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.setDuration(350);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    scale = (float) valueAnimator.getAnimatedValue();
                }
            });
            animator.start();
        }

        /**
         * Creates a bouncy scale animation intended for touch feedback.
         */
        private void clickBack() {
            if (animator != null && animator.isStarted())
                animator.cancel();

            animator = ValueAnimator.ofFloat(scale, CLICK_UP_SCALE, 1);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.setDuration(350);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    scale = (float) valueAnimator.getAnimatedValue();
                }
            });
            animator.start();
        }

        /**
         * Removes the item from the layout.
         *
         * @param layout the layout to be removed from
         */
        private void removeFrom(final RadialLayout layout) {
            if (animator != null && animator.isStarted())
                animator.cancel();

            animator = ValueAnimator.ofFloat(scale, 0);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.setDuration(200);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    scale = (float) valueAnimator.getAnimatedValue();
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    layout.items.remove(RadialItem.this);
                }
            });
            animator.start();
        }

    }

}