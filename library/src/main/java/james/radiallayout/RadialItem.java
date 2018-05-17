package james.radiallayout;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import james.radiallayout.utils.ConversionUtils;
import james.radiallayout.utils.ImageUtils;
import james.radiallayout.utils.RadialUtils;

/**
 * A view container specifying info such as the size to scale the image
 * relative to the rest of the items and the distance that the view should be
 * from the center of the layout.
 */
public class RadialItem {

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

    public int getSize() {
        return size;
    }

    public int getDistance() {
        return distance;
    }

    /**
     * Sets the radius of this RadialItem and creates a new scaled bitmap if the current one does not match
     * the required dimensions.
     *
     * @param radius the required radius of the circle
     */
    void setRadius(float radius, float shadowSizeDp) {
        int shadowSize = ConversionUtils.dpToPx(shadowSizeDp) * 2;
        if (radius > shadowSize && (scaledImage == null || scaledImage.getWidth() != (radius * 2) - shadowSize || scaledImage.getHeight() != (radius * 2) - shadowSize)) {
            //Log.d("RadialLayout", "new " + (scaledImage == null ? "scaled bitmap" : "radius"));
            scaledImage = ThumbnailUtils.extractThumbnail(image, (int) ((radius * 2) - shadowSize), (int) ((radius * 2) - shadowSize));
        }
        this.radius = radius;
        targetRadius = radius;
    }

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
    Bitmap getCircleImage(final RadialLayoutView layout, float shadowSizeDp) {
        if (circleImage == null || circleImage.getWidth() != radius * 2 || circleImage.getHeight() != radius * 2) {
            Log.d("DrawingImage", "drawing");
            if (scaledImage == null)
                setRadius(radius, shadowSizeDp);

            int shadowSize = ConversionUtils.dpToPx(shadowSizeDp);

            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(layout.getResources(), scaledImage);
            roundedBitmapDrawable.setCornerRadius(radius);
            roundedBitmapDrawable.setAntiAlias(true);

            Bitmap roundedBitmap = ImageUtils.drawableToBitmap(roundedBitmapDrawable);
            if (shadowSizeDp > 0) {
                circleImage = Bitmap.createBitmap(roundedBitmap.getWidth() + (shadowSize * 2), roundedBitmap.getHeight() + (shadowSize * 2), Bitmap.Config.ARGB_4444);
                Canvas canvas = new Canvas(circleImage);
                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, (canvas.getWidth() / 2) - shadowSize - 1, layout.getShadowPaint());
                canvas.drawBitmap(roundedBitmap, shadowSize, shadowSize, layout.getPaint());
            } else circleImage = roundedBitmap;

            //Log.d("RadialLayout", "new circle");
        }

        return circleImage;
    }

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
     * Animates this RadialItem to the dimensions and position of the parameter.
     *
     * @param item   the item to animate to the position of
     * @param layout the layout to be animated in
     */
    void animateTo(RadialItem item, final RadialLayoutView layout, float shadowSizeDp) {
        image = item.image;
        scaledImage = item.scaledImage;
        circleImage = item.circleImage;
        row = item.row;
        size = item.size;
        distance = item.distance;

        float tempRadius = radius;
        setRadius(item.radius, shadowSizeDp);
        getCircleImage(layout, shadowSizeDp);
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
