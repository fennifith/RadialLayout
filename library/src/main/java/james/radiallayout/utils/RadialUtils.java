package james.radiallayout.utils;

import james.radiallayout.views.RadialLayoutView;

public class RadialUtils {

    public static int getRadius(int row) {
        return ConversionUtils.dpToPx(((row + 1) * (RadialLayoutView.CIRCLE_RADIUS * 2 + RadialLayoutView.ITEM_SEPARATION)) + 12);
    }

    public static int getCircumference(int row) {
        return (int) (2 * Math.PI * getRadius(row));
    }

}
