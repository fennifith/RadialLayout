package james.radiallayout;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.ThumbnailUtils;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;

import james.radiallayout.utils.ConversionUtils;
import james.radiallayout.utils.ImageUtils;

/**
 * A view container specifying info such as the size to scale the image
 * relative to the rest of the items and the distance that the view should be
 * from the center of the layout.
 */
public class RadialItem extends BaseRadialItem {

    public RadialItem(String id, Bitmap image, int size, int distance) {
        super(id, image, size, distance);
    }

    private RadialItem(RadialItem item) {
        super(item);
    }

    @Override
    RadialItem copy() {
        return new RadialItem(this);
    }

    @Override
    void setRadius(float radius, float shadowSizeDp) {
        int shadowSize = ConversionUtils.dpToPx(shadowSizeDp) * 2;
        int size = (int) (radius * 2);
        if (radius > shadowSize && size > 0 && (scaledImage == null || scaledImage.getWidth() != size || scaledImage.getHeight() != size)) {
            //Log.d("RadialLayout", "new " + (scaledImage == null ? "scaled bitmap" : "radius"));
            scaledImage = ThumbnailUtils.extractThumbnail(image, size, size);
        }
        this.radius = radius;
        targetRadius = radius;
    }

    @Override
    Bitmap getCircleImage(RadialLayoutView layout, float shadowRadiusDp) {
        if (circleImage == null || circleImage.getWidth() != (int) (radius * 2) || circleImage.getHeight() != (int) (radius * 2)) {
            if (scaledImage == null)
                setRadius(radius, shadowRadiusDp);

            int shadowRadius = ConversionUtils.dpToPx(shadowRadiusDp);

            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(layout.getResources(), scaledImage);
            roundedBitmapDrawable.setCornerRadius(radius);
            roundedBitmapDrawable.setAntiAlias(true);

            Bitmap roundedBitmap = ImageUtils.drawableToBitmap(roundedBitmapDrawable);
            if (shadowRadius > 0) {
                circleImage = Bitmap.createBitmap(roundedBitmap.getWidth() + (shadowRadius * 2), roundedBitmap.getHeight() + (shadowRadius * 2), Bitmap.Config.ARGB_4444);
                Canvas canvas = new Canvas(circleImage);
                canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, (canvas.getWidth() / 2) - shadowRadius - 1, layout.getShadowPaint());
                canvas.drawBitmap(roundedBitmap, shadowRadius, shadowRadius, layout.getPaint());
            } else circleImage = roundedBitmap;
        }

        return circleImage;
    }
}
