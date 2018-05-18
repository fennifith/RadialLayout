package james.radiallayout;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import java.util.ArrayList;
import java.util.List;

import james.radiallayout.utils.RadialUtils;

/**
 * A view container specifying info such as the size to scale the image
 * relative to the rest of the items and the distance that the view should be
 * from the center of the layout.
 */
public abstract class BaseRadialItem {

    String id;
    Bitmap image;
    Bitmap scaledImage;
    Bitmap circleImage;
    int size;
    int distance;
    float radius;
    int row;
    double radian;
    double radianOffset;
    float scale;

    float drawnRadius;
    double drawnRadian;
    float drawnScale;

    float targetRadius;
    double targetRadian;
    List<Float> targetScales;

    boolean isRemoving;

    float itemRadius;
    float itemSeparation;

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
    public BaseRadialItem(String id, Bitmap image, int size, int distance) {
        this.id = id;
        this.image = image;
        this.size = size;
        this.distance = distance;
        scale = 1;

        targetScales = new ArrayList<>();
        targetScales.add(1f);
    }

    public BaseRadialItem(BaseRadialItem item) {
        this(item.id, item.image, item.size, item.distance);
        radius = item.radius;
        scaledImage = item.scaledImage;
        circleImage = item.circleImage;

        targetRadius = radius;
    }

    /**
     * Creates a copy of the BaseRadialItem by calling the constructor with itself as an argument.
     *
     * @return a new instance of the BaseRadialItem with the same construction parameters
     */
    abstract BaseRadialItem copy();

    public String getId() {
        return id;
    }

    public int getSize() {
        return size;
    }

    public int getDistance() {
        return distance;
    }

    /**
     * Sets the radius of this BaseRadialItem and creates a new scaled bitmap if the current one does not match
     * the required dimensions.
     *
     * @param radius the required radius of the circle
     */
    abstract void setRadius(float radius, float shadowSizeDp);

    float getX() {
        return (float) (RadialUtils.getRadius(row, itemRadius, itemSeparation) * Math.sin((Math.PI / 2) - (radian + radianOffset))) - radius;
    }

    float getY() {
        return (float) (RadialUtils.getRadius(row, itemRadius, itemSeparation) * Math.sin(radian + radianOffset)) - radius;
    }

    /**
     * Creates a new circular bitmap if the current one does not match the required dimensions, and returns it.
     *
     * @param layout the current radial layout
     * @return a circular image bitmap
     */
    abstract Bitmap getCircleImage(final RadialLayoutView layout, float shadowRadiusDp);

    /**
     * Creates a Matrix to scale the image to the correct dimensions on a Canvas.
     */
    Matrix getMatrix(int canvasWidth, int canvasHeight, float offsetX, float offsetY) {
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

    boolean needsFrame() {
        return Math.abs(targetRadius - drawnRadius) > 0.01
                || Math.abs(targetRadian - drawnRadian) > 0.001
                || (targetScales.size() > 0 && (targetScales.size() > 1 || Math.abs(targetScales.get(0) - drawnScale) > 0.01))
                || isRemoving;
    }

    void nextFrame(final RadialLayoutView layout) {
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
     * Animates this BaseRadialItem to the dimensions and position of the parameter.
     *
     * @param item         the item to animate to the position of
     * @param layout       the layout to be animated in
     * @param shadowRadius the radius (in dp) of the shadow to be drawn
     */
    void animateTo(BaseRadialItem item, final RadialLayoutView layout, float shadowRadius) {
        image = item.image;
        scaledImage = item.scaledImage;
        circleImage = item.circleImage;
        row = item.row;
        size = item.size;
        distance = item.distance;

        float tempRadius = radius;
        setRadius(item.radius, shadowRadius);
        getCircleImage(layout, shadowRadius);
        radius = tempRadius;

        targetRadius = item.radius;
        targetRadian = item.radian;

        layout.postInvalidate();
    }

    void clickDown(final RadialLayoutView layout) {
        targetScales.clear();
        targetScales.add(RadialLayoutView.CLICK_DOWN_SCALE);

        layout.postInvalidate();
    }

    void clickUp(final RadialLayoutView layout) {
        targetScales.clear();
        targetScales.add(1f);

        layout.postInvalidate();
    }

    /**
     * Creates a bouncy scale animation intended for touch feedback.
     */
    void clickBack(final RadialLayoutView layout) {
        targetScales.clear();
        targetScales.add(RadialLayoutView.CLICK_UP_SCALE);
        targetScales.add(1f);

        layout.postInvalidate();
    }

    /**
     * Removes the item from the layout.
     *
     * @param layout the layout to be removed from
     */
    void removeFrom(final RadialLayoutView layout) {
        targetScales.clear();
        targetScales.add(0f);
        isRemoving = true;

        layout.postInvalidate();
    }

}
