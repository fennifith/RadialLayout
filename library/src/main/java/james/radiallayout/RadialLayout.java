package james.radiallayout;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.os.Handler;
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

    public static final float CLICK_DOWN_SCALE = 0.8f;
    public static final float CLICK_UP_SCALE = 1.07f;

    public static final float SHADOW_RADIUS = 4;
    public static final float SHADOW_OFFSET = 2;
    public static final int SHADOW_COLOR = 170;
    public static final float SHADOW_SIZE = SHADOW_RADIUS + SHADOW_OFFSET;

    /**
     * The radius of the circles in dp. Must be greater than 12, as the value
     * can vary by +/- 6 based on scale.
     */
    public static final int CIRCLE_RADIUS = 34 + ((int) SHADOW_SIZE);
    public static final int ITEM_SEPARATION = 8;

    private Paint paint;
    private Paint outlinePaint;
    private Paint shadowPaint;
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

    private Bitmap currentUser;
    private float currentUserScale;
    private List<Float> targetCurrentUserScales;
    private int currentUserRadius;

    /**
     * true once the view has been drawn - will invalidate continuously until then as an alternative to a ViewTreeObserver
     */
    private boolean isFirstDrawn;

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

        shadowPaint = new Paint();
        shadowPaint.setAntiAlias(false);
        shadowPaint.setFilterBitmap(false);
        shadowPaint.setDither(false);
        setLayerType(LAYER_TYPE_SOFTWARE, shadowPaint);
        shadowPaint.setShadowLayer(ConversionUtils.dpToPx(SHADOW_RADIUS), 0, ConversionUtils.dpToPx(SHADOW_OFFSET), Color.rgb(SHADOW_COLOR, SHADOW_COLOR, SHADOW_COLOR));

        setFocusable(true);
        setClickable(true);

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1) != 1) {
            try {
                ValueAnimator.class.getMethod("setDurationScale", float.class).invoke(null, 1f); //force animator duration (ignore developer options)
            } catch (Throwable t) {
            }
        }

        animator = ValueAnimator.ofFloat(0, 2 * (float) Math.PI);
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
        targetCurrentUserScales = new ArrayList<>();
    }

    public void setMeBitmap(Bitmap bitmap) {
        int size = ConversionUtils.dpToPx(CIRCLE_RADIUS * 2);
        int shadowSize = ConversionUtils.dpToPx(SHADOW_SIZE);
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, size - (shadowSize * 2), size - (shadowSize * 2));

        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
        drawable.setCornerRadius(size / 2);
        drawable.setAntiAlias(true);

        Bitmap roundedBitmap = ImageUtils.drawableToBitmap(drawable);
        currentUser = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(currentUser);
        canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, (size / 2) - shadowSize - 1, shadowPaint);
        canvas.drawBitmap(roundedBitmap, shadowSize, shadowSize, paint);

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
                for (RadialItem item : RadialLayout.this.items) {
                    //Log.d("Radial", "Item: " + RadialLayout.this.items.indexOf(item) + ", X: " + item.getX() + ", Y: " + item.getY());
                    item.scale = 0;
                    item.targetRadian = item.radian;
                    item.clickUp(RadialLayout.this);
                }

                isReady = true;
                invalidate();
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
                            newItem.targetRadian = newItem.radian;
                            newItem.clickUp(RadialLayout.this);
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

                int distance = (RadialUtils.getRadius(maxRow + 1) * 2) - Math.min(canvas.getWidth(), canvas.getHeight());
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

        if (currentUser != null) {
            if (targetCurrentUserScales.size() > 0) {
                if (targetCurrentUserScales.size() > 1 && Math.abs(currentUserScale - targetCurrentUserScales.get(0)) < 0.01)
                    targetCurrentUserScales.remove(0);

                currentUserScale = (targetCurrentUserScales.get(0) + (currentUserScale * 5)) / 6;
            }

            float nScale = 0;
            float distance = (float) Math.sqrt(Math.pow(offsetX + currentUserRadius, 2) + Math.pow(offsetY + currentUserRadius, 2));
            int totalRadius = (canvas.getWidth() + canvas.getHeight()) / 4;
            if (distance < totalRadius) {
                nScale = Math.min((float) (Math.sqrt(totalRadius - distance) / Math.sqrt(currentUserRadius * 2)) * currentUserScale, currentUserScale);
            }

            if (nScale > 0) {
                Matrix matrix = new Matrix();
                matrix.preScale(nScale, nScale, currentUser.getWidth() / 2, currentUser.getHeight() / 2);
                matrix.postTranslate(((canvas.getWidth() - currentUser.getWidth()) / 2) + offsetX, ((canvas.getHeight() - currentUser.getHeight()) / 2) + offsetY);
                canvas.drawBitmap(currentUser, matrix, paint);

                canvas.drawCircle((canvas.getWidth() / 2) + offsetX, (canvas.getHeight() / 2) + offsetY, (currentUser.getWidth() / 2) * nScale, outlinePaint);
            }
        }

        boolean needsFrame = false;

        if (isReady && canvas.getWidth() > 0 && canvas.getHeight() > 0 && getWidth() > 0 && getHeight() > 0) {
            isFirstDrawn = true;

            for (int i = 0; i < items.size(); i++) {
                RadialItem item = items.get(i);
                Matrix matrix = item.getMatrix(canvas.getWidth(), canvas.getHeight(), offsetX, offsetY);
                if (matrix != null)
                    canvas.drawBitmap(item.getCircleImage(this), matrix, paint);

                item.nextFrame(this);
                if (!needsFrame)
                    needsFrame = item.needsFrame();
            }
        }

        if ((currentUser != null && targetCurrentUserScales.size() > 0 && (targetCurrentUserScales.size() > 1 || Math.abs(targetCurrentUserScales.get(0) - currentUserScale) >= 0.01))
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

                if (Math.sqrt(Math.pow((getWidth() / 2) - downX + offsetX, 2) + Math.pow((getHeight() / 2) - downY + offsetY, 2)) < currentUserRadius) {
                    clickMeDown();

                    for (RadialItem item : items) {
                        item.clickUp(this);
                    }
                } else {
                    clickMeUp();

                    for (RadialItem item : items) {
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
                int distance = (RadialUtils.getRadius(maxRow + 1) * 2) - Math.min(width, height);
                if (distance > 0 && (ConversionUtils.pxToDp((int) Math.abs(event.getX() - downX)) * ConversionUtils.pxToDp((int) Math.abs(event.getY() - downY)) >= 64 || isDragged)) {
                    isDragged = true;
                    isScrolling = true;
                    fingerX = Math.max(-distance / 2, Math.min(distance / 2, event.getX() - downX + lastX));
                    fingerY = Math.max(-distance / 2, Math.min(distance / 2, event.getY() - downY + lastY));

                    clickMeUp();
                    for (RadialItem item : items) {
                        item.clickUp(this);
                    }
                    return true;
                } else return false;
            case MotionEvent.ACTION_CANCEL:
                isFingerDown = false;
                handler.removeCallbacks(upRunnable);
                handler.post(upRunnable);
                isDragged = false;

                clickMeUp();
                for (RadialItem item : items) {
                    item.clickUp(this);
                }
                break;
            case MotionEvent.ACTION_UP:
                isFingerDown = false;
                for (RadialItem item : items) {
                    item.clickUp(this);
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
                                item.clickBack(this);

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
        targetCurrentUserScales.clear();
        targetCurrentUserScales.add(CLICK_DOWN_SCALE);

        postInvalidate();
    }

    private void clickMeUp() {
        targetCurrentUserScales.clear();
        targetCurrentUserScales.add(1f);

        postInvalidate();
    }

    private void clickMeBack() {
        targetCurrentUserScales.clear();
        targetCurrentUserScales.add(CLICK_UP_SCALE);
        targetCurrentUserScales.add(1f);

        postInvalidate();
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
        private float scale;

        private float drawnRadius;
        private double drawnRadian;
        private float drawnScale;

        private float targetRadius;
        private double targetRadian;
        private List<Float> targetScales;

        private boolean isRemoving;

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

            targetScales = new ArrayList<>();
            targetScales.add(1f);
        }

        public RadialItem(RadialItem item) {
            this(item.id, item.image, item.size, item.distance);
            radius = item.radius;
            scaledImage = item.scaledImage;
            circleImage = item.circleImage;

            targetRadius = radius;
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
            int shadowSize = ConversionUtils.dpToPx(SHADOW_SIZE) * 2;
            if (radius > shadowSize && (scaledImage == null || scaledImage.getWidth() != (radius * 2) - shadowSize || scaledImage.getHeight() != (radius * 2) - shadowSize)) {
                //Log.d("RadialLayout", "new " + (scaledImage == null ? "scaled bitmap" : "radius"));
                scaledImage = ThumbnailUtils.extractThumbnail(image, (int) ((radius * 2) - shadowSize), (int) ((radius * 2) - shadowSize));
            }
            this.radius = radius;
            targetRadius = radius;
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
         * @param layout the current radial layout
         * @return a circular image bitmap
         */
        private Bitmap getCircleImage(final RadialLayout layout) {
            if (circleImage == null || circleImage.getWidth() != radius * 2 || circleImage.getHeight() != radius * 2) {
                Log.d("DrawingImage", "drawing");
                if (scaledImage == null)
                    setRadius(radius);

                int shadowSize = ConversionUtils.dpToPx(SHADOW_SIZE);

                RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(layout.getResources(), scaledImage);
                roundedBitmapDrawable.setCornerRadius(radius);
                roundedBitmapDrawable.setAntiAlias(true);

                Bitmap roundedBitmap = ImageUtils.drawableToBitmap(roundedBitmapDrawable);
                circleImage = Bitmap.createBitmap(roundedBitmap.getWidth() + (shadowSize * 2), roundedBitmap.getHeight() + (shadowSize * 2), Bitmap.Config.ARGB_4444);
                Canvas canvas = new Canvas(circleImage);
                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, (canvas.getWidth() / 2) - shadowSize - 1, layout.shadowPaint);
                canvas.drawBitmap(roundedBitmap, shadowSize, shadowSize, layout.paint);

                //Log.d("RadialLayout", "new circle");
            }

            return circleImage;
        }

        /**
         * Creates a Matrix to scale the image to the correct dimensions on a Canvas.
         */
        private Matrix getMatrix(int canvasWidth, int canvasHeight, float offsetX, float offsetY) {
            drawnRadius = radius;
            drawnRadian = radian;
            drawnScale = scale;

            float nScale = 0;
            float distance = (float) Math.sqrt(Math.pow(offsetX + getX() + radius, 2) + Math.pow(offsetY + getY() + radius, 2));
            int totalRadius = (canvasWidth + canvasHeight) / 4;
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

        public boolean needsFrame() {
            return Math.abs(targetRadius - drawnRadius) > 0.01
                    || Math.abs(targetRadian - drawnRadian) > 0.001
                    || (targetScales.size() > 0 && (targetScales.size() > 1 || Math.abs(targetScales.get(0) - drawnScale) > 0.01))
                    || isRemoving;
        }

        public void nextFrame(final RadialLayout layout) {
            radius = (targetRadius + (radius * 5)) / 6;
            radian = (targetRadian + (radian * 5)) / 6;
            if (targetScales.size() > 0) {
                if (targetScales.size() > 1 && Math.abs(scale - targetScales.get(0)) < 0.01)
                    targetScales.remove(0);

                scale = (targetScales.get(0) + (scale * 5)) / 6;
                if (scale < 0.02 && isRemoving)
                    layout.items.remove(this);

            } else if (isRemoving)
                layout.items.remove(this);
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

            float tempRadius = radius;
            setRadius(item.radius);
            getCircleImage(layout);
            radius = tempRadius;

            targetRadius = item.radius;
            targetRadian = item.radian;

            layout.postInvalidate();
        }

        private void clickDown(final RadialLayout layout) {
            targetScales.clear();
            targetScales.add(CLICK_DOWN_SCALE);

            layout.postInvalidate();
        }

        private void clickUp(final RadialLayout layout) {
            targetScales.clear();
            targetScales.add(1f);

            layout.postInvalidate();
        }

        /**
         * Creates a bouncy scale animation intended for touch feedback.
         */
        private void clickBack(final RadialLayout layout) {
            targetScales.clear();
            targetScales.add(CLICK_UP_SCALE);
            targetScales.add(1f);

            layout.postInvalidate();
        }

        /**
         * Removes the item from the layout.
         *
         * @param layout the layout to be removed from
         */
        private void removeFrom(final RadialLayout layout) {
            targetScales.clear();
            targetScales.add(0f);
            isRemoving = true;

            layout.postInvalidate();
        }

    }

}