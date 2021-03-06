package me.jfenn.radiallayout;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;

import me.jfenn.radiallayout.utils.ConversionUtils;
import me.jfenn.radiallayout.utils.ImageUtils;

public class CenteredRadialItem extends BaseRadialItem {

    private Paint outlinePaint;
    private int outlineWeight;
    private int outlineRadius;

    public CenteredRadialItem(Bitmap image, int sizeDp) {
        super("", image, sizeDp, 0);

        outlinePaint = new Paint();
        outlinePaint.setAntiAlias(true);
        outlinePaint.setDither(true);
        outlinePaint.setStyle(Paint.Style.STROKE);
        setOutline(2, 4, Color.BLACK);
    }

    private CenteredRadialItem(CenteredRadialItem item) {
        super(item);
        setOutline(item.outlineWeight, item.outlineRadius, item.outlinePaint.getColor());
    }

    /**
     * Set the weight, radius, and color of the outline.
     *
     * @param weight the thickness (dp) of the outline
     * @param radius the distance (dp) between the edge of the image and the outline
     * @param color  the color of the outline
     */
    public void setOutline(int weight, int radius, int color) {
        outlineWeight = weight;
        outlineRadius = radius;

        outlinePaint.setStrokeWidth(ConversionUtils.dpToPx(weight));
        outlinePaint.setColor(color);
        scaledImage = null;
        circleImage = null;
    }

    @Override
    CenteredRadialItem copy() {
        return new CenteredRadialItem(this);
    }

    @Override
    void setRadius(float radius, float shadowSizeDp) {
        int shadowSize = ConversionUtils.dpToPx(shadowSizeDp);
        float imageRadius = radius - Math.max(shadowSize, ConversionUtils.dpToPx(outlineRadius + outlineWeight));
        if (radius > imageRadius && (scaledImage == null || scaledImage.getWidth() != imageRadius * 2 || scaledImage.getHeight() != imageRadius * 2))
            scaledImage = ThumbnailUtils.extractThumbnail(image, (int) (imageRadius * 2), (int) (imageRadius * 2));
        this.radius = radius;
        targetRadius = radius;
    }

    @Override
    Bitmap getCircleImage(RadialLayoutView layout, float shadowRadiusDp) {
        if (circleImage == null || circleImage.getWidth() != radius * 2 || circleImage.getHeight() != radius * 2) {
            if (scaledImage == null)
                setRadius(radius, shadowRadiusDp);

            int outlineWeight = ConversionUtils.dpToPx(this.outlineWeight);
            int imageOffset = Math.max(ConversionUtils.dpToPx(this.outlineRadius) + outlineWeight, ConversionUtils.dpToPx(shadowRadiusDp));

            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(layout.getResources(), scaledImage);
            roundedBitmapDrawable.setCornerRadius(radius);
            roundedBitmapDrawable.setAntiAlias(true);

            Bitmap roundedBitmap = ImageUtils.drawableToBitmap(roundedBitmapDrawable);
            if (imageOffset > 0) {
                circleImage = Bitmap.createBitmap(roundedBitmap.getWidth() + (imageOffset * 2), roundedBitmap.getHeight() + (imageOffset * 2), Bitmap.Config.ARGB_4444);
                Canvas canvas = new Canvas(circleImage);
                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, (canvas.getWidth() / 2) - outlineWeight - 1, outlinePaint);
                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, (canvas.getWidth() / 2) - imageOffset - 1, layout.getShadowPaint());
                canvas.drawBitmap(roundedBitmap, imageOffset, imageOffset, layout.getPaint());
            } else circleImage = roundedBitmap;
        }

        return circleImage;
    }

    @Override
    float getX() {
        return 0;
    }

    @Override
    float getY() {
        return 0;
    }

    @Override
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
            matrix.postTranslate((canvasWidth / 2) - radius + offsetX, (canvasHeight / 2) - radius + offsetY);
            return matrix;
        } else return null;
    }
}
