package james.radiallayout.utils;

import james.radiallayout.RadialLayout;

public class RadialUtils {

    public static int getRadius(int row) {
        return ConversionUtils.dpToPx(((row + 1) * (RadialLayout.CIRCLE_RADIUS * 2 + RadialLayout.ITEM_SEPARATION)) + 12);
    }

    public static int getCircumference(int row) {
        return (int) (2 * Math.PI * getRadius(row));
    }

}
