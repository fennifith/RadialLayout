package james.radiallayout;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;

import james.radiallayout.utils.ConversionUtils;
import james.radiallayout.utils.ImageUtils;

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

    public void setOutline(int weight, int radius, int color) {
        outlineWeight = weight;
        outlineRadius = radius;

        outlinePaint.setStrokeWidth(ConversionUtils.dpToPx(weight));
        outlinePaint.setColor(color);
    }

    @Override
    CenteredRadialItem copy() {
        return new CenteredRadialItem(this);
    }

    @Override
    void setRadius(float radius, float shadowSizeDp) {
        int shadowSize = ConversionUtils.dpToPx(shadowSizeDp);
        float imageRadius = radius - shadowSize - outlineRadius - outlineWeight;
        if (radius > shadowSize && (scaledImage == null || scaledImage.getWidth() != imageRadius * 2 || scaledImage.getHeight() != imageRadius * 2))
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
            int extraRadius = ConversionUtils.dpToPx(this.outlineRadius) + outlineWeight;

            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(layout.getResources(), scaledImage);
            roundedBitmapDrawable.setCornerRadius(radius);
            roundedBitmapDrawable.setAntiAlias(true);

            Bitmap roundedBitmap = ImageUtils.drawableToBitmap(roundedBitmapDrawable);
            if (extraRadius > 0) {
                circleImage = Bitmap.createBitmap(roundedBitmap.getWidth() + (extraRadius * 2), roundedBitmap.getHeight() + (extraRadius * 2), Bitmap.Config.ARGB_4444);
                Canvas canvas = new Canvas(circleImage);
                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, (canvas.getWidth() / 2) - outlineWeight - 1, outlinePaint);
                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, (canvas.getWidth() / 2) - extraRadius - 1, layout.getShadowPaint());
                canvas.drawBitmap(roundedBitmap, extraRadius, extraRadius, layout.getPaint());
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
