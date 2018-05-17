package james.radiallayout.utils;

public class RadialUtils {

    public static int getRadius(int row, float itemRadius, float itemSeparation) {
        return ConversionUtils.dpToPx(((row + 1) * (itemRadius * 2 + itemSeparation)) + 12);
    }

    public static int getCircumference(int row, float itemRadius, float itemSeparation) {
        return (int) (2 * Math.PI * getRadius(row, itemRadius, itemSeparation));
    }

}
