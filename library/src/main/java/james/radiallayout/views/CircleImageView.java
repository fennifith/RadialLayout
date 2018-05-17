package james.radiallayout.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;

import james.radiallayout.utils.ConversionUtils;
import james.radiallayout.utils.ImageUtils;

/**
 * An ImageView that clips its image to the shape of a circle with a
 * shadow behind it.
 */
public class CircleImageView extends AppCompatImageView {

    private Paint paint;
    private int shadowRadius = ConversionUtils.dpToPx(3.5f);

    public CircleImageView(Context context) {
        this(context, null);
    }

    public CircleImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleImageView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        paint = new Paint();
        paint.setAntiAlias(true);
    }

    /**
     * Sets the size of the elevation (shadow) to show behind the view.
     *
     * @param elevation elevation, preferably between 1 and 8
     */
    public void setElevation(int elevation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ShapeDrawable circle = new ShapeDrawable(new OvalShape());
            ViewCompat.setElevation(this, ConversionUtils.dpToPx(elevation));
            circle.getPaint().setColor(Color.TRANSPARENT);
            setBackgroundDrawable(circle);
        } else {
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    ShapeDrawable circle = new ShapeDrawable(new OvalShadow(shadowRadius, Math.min(getWidth(), getHeight()) * ConversionUtils.dpToPx(2)));
                    ViewCompat.setLayerType(CircleImageView.this, ViewCompat.LAYER_TYPE_SOFTWARE, circle.getPaint());
                    circle.getPaint().setShadowLayer(shadowRadius, 0, ConversionUtils.dpToPx(1.75f), 0x1E000000);
                    setPadding(shadowRadius, shadowRadius, shadowRadius, shadowRadius);
                    circle.getPaint().setColor(Color.TRANSPARENT);
                    setBackgroundDrawable(circle);
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            });
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        Bitmap image = ImageUtils.drawableToBitmap(getDrawable());
        if (image != null) {
            int size = Math.min(getWidth(), getHeight());
            image = ThumbnailUtils.extractThumbnail(image, size, size);

            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), image);
            roundedBitmapDrawable.setCornerRadius(size / 2);
            roundedBitmapDrawable.setAntiAlias(true);

            canvas.drawBitmap(ImageUtils.drawableToBitmap(roundedBitmapDrawable), 0, 0, paint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int size = getMeasuredWidth();
        setMeasuredDimension(size, size);
    }

    private class OvalShadow extends OvalShape {

        private Paint shadowPaint;
        private int diameter;

        public OvalShadow(int shadowRadius, int circleDiameter) {
            super();
            shadowPaint = new Paint();
            CircleImageView.this.shadowRadius = shadowRadius;
            diameter = circleDiameter;
            shadowPaint.setShader(new RadialGradient(diameter / 2, diameter / 2, CircleImageView.this.shadowRadius, new int[]{0x3D000000, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas, Paint paint) {
            final int width = CircleImageView.this.getWidth();
            final int height = CircleImageView.this.getHeight();
            canvas.drawCircle(width / 2, height / 2, (diameter / 2 + shadowRadius), shadowPaint);
            canvas.drawCircle(width / 2, height / 2, (diameter / 2), paint);
        }
    }
}
