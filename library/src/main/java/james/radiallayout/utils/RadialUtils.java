package james.radiallayout.utils;

public class RadialUtils {

    public static int getRadius(int row) {
        return ConversionUtils.dpToPx(((row + 1) * 64) + 4);
    }

    public static int getCircumference(int row) {
        return (int) (2 * Math.PI * getRadius(row));
    }

}
