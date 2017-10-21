package james.radiallayout;

import android.content.Context;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.afollestad.async.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import james.radiallayout.utils.ConversionUtils;
import james.radiallayout.utils.RadialUtils;

/**
 * A layout that arranges its items in circles.
 */
public class RadialLayout extends FrameLayout {

    private List<RadialItem> items;

    public RadialLayout(@NonNull Context context) {
        this(context, null);
    }

    public RadialLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadialLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        items = new ArrayList<>();
    }

    /**
     * This method begins the necessary calculations to display the items
     * in a background thread and adds them to the view when it has finished.
     *
     * @param items the items to add
     */
    public void setItems(List<RadialItem> items) {
        this.items = items;

        new Action() {
            @NonNull
            @Override
            public String id() {
                return "radialItems";
            }

            @Nullable
            @Override
            protected Object run() throws InterruptedException {
                List<RadialItem> items = new ArrayList<>(RadialLayout.this.items);

                Collections.sort(items, new Comparator<RadialItem>() {
                    @Override
                    public int compare(RadialItem o1, RadialItem o2) {
                        return o1.size - o2.size; //sort small -> big
                    }
                });

                for (int i = 0; i < items.size(); i++) {
                    int radius = ConversionUtils.dpToPx(18 + (12 * ((float) i / items.size())));
                    //Log.d("Radial", "Item: " + i + ", Size: " + items.get(i).size + ", Radius: " + radius);
                    items.get(i).setRadius(radius);
                }

                Collections.sort(items, new Comparator<RadialItem>() {
                    @Override
                    public int compare(RadialItem o1, RadialItem o2) {
                        return o1.distance - o2.distance; //sort small -> big
                    }
                });

                int size = 0, circumference = RadialUtils.getCircumference(0), usedCircumference = 0;
                for (int i = 0; i < items.size(); i++) {
                    RadialItem item = items.get(i);
                    if (usedCircumference + (item.radius * 2) + (ConversionUtils.dpToPx(8) * i) < circumference) {
                        usedCircumference += (item.radius * 2) + ConversionUtils.dpToPx(8);
                        item.row = 0;
                        size++;

                        //Log.d("Radial", "Item: " + items.indexOf(item) + ", Row: 0");
                    } else break;
                }

                int padding = ((circumference - usedCircumference) / (size + 1)) + ConversionUtils.dpToPx(8);
                items.get(0).radian = -Math.PI / 2;
                for (int i = 1; i < size; i++) {
                    RadialItem item = items.get(i), previousItem = items.get(i - 1);
                    int difference = previousItem.radius + item.radius;
                    double cosine = ((2 * Math.pow(RadialUtils.getRadius(0), 2)) - Math.pow(difference, 2)) / (2 * Math.pow(RadialUtils.getRadius(0), 2));
                    item.radian = previousItem.radian + Math.acos(cosine) + (((double) padding / circumference) * 2 * Math.PI);
                    //Log.d("Radial", "Row: 0, Item: " + i + ", Difference: " + difference + ", Cosine: " + cosine + ", Degrees: " + (item.radian * 180 / Math.PI));
                }

                for (int i = 1; true; i++) {
                    int rowStart = size;

                    if (rowStart == 0 || rowStart >= items.size())
                        break;

                    int rowSize = 0, rowCircumference = RadialUtils.getCircumference(i), rowUsedCircumference = 0;
                    for (int i2 = rowStart; i2 < items.size(); i2++) {
                        RadialItem item = items.get(i2);
                        if (rowUsedCircumference + (item.radius * 2) + (ConversionUtils.dpToPx(8) * i) < rowCircumference) {
                            rowUsedCircumference += (item.radius * 2) + ConversionUtils.dpToPx(8);
                            item.row = i;
                            rowSize++;

                            //Log.d("Radial", "Row: " + item.row + ", Item: " + i2);
                        } else break;
                    }

                    int rowPadding = ((rowCircumference - rowUsedCircumference) / (rowSize + 1)) + ConversionUtils.dpToPx(8);
                    items.get(rowStart).radian = (items.get(rowStart - 1).radian + items.get(rowStart - 2).radian) / 2;

                    for (int i2 = rowStart; i2 < rowStart + rowSize; i2++) {
                        RadialItem item, previousItem = items.get(i2 - 1);
                        try {
                            item = items.get(i2);
                        } catch (IndexOutOfBoundsException e) {
                            break;
                        }

                        int difference = previousItem.radius + item.radius;
                        double cosine = ((2 * Math.pow(RadialUtils.getRadius(i), 2)) - Math.pow(difference, 2)) / (2 * Math.pow(RadialUtils.getRadius(i), 2));
                        item.radian = previousItem.radian + Math.acos(cosine) + (((double) rowPadding / rowCircumference) * 2 * Math.PI);
                        //Log.d("Radial", "Row: " + item.row + ", Item: " + i2 + ", Difference: " + difference + ", Cosine: " + cosine + ", Degrees: " + (item.radian * 180 / Math.PI));
                    }

                    size += rowSize;
                }

                return null;
            }

            @Override
            protected void done(@Nullable Object result) {
                int width = getWidth(), height = getHeight();
                if (width > 0 && height > 0) {
                    for (RadialItem item : RadialLayout.this.items) {
                        //Log.d("Radial", "Item: " + RadialLayout.this.items.indexOf(item) + ", X: " + item.getX() + ", Y: " + item.getY());
                        addView(item.view);
                        item.view.setX((width / 2) + item.getX());
                        item.view.setY((height / 2) + item.getY());
                    }
                } else {
                    getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            int width = getWidth(), height = getHeight();
                            for (RadialItem item : RadialLayout.this.items) {
                                //Log.d("Radial", "Item: " + RadialLayout.this.items.indexOf(item) + ", X: " + item.getX() + ", Y: " + item.getY());
                                addView(item.view);
                                item.view.setX((width / 2) + item.getX());
                                item.view.setY((height / 2) + item.getY());
                            }

                            getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
                }
            }
        }.execute();
    }

    /**
     * A view container specifying info such as the size to scale the view
     * relative to the rest of the items and the distance that the view should be
     * from the center of the layout.
     */
    public static class RadialItem {

        private View view;
        private int size, distance;
        private int radius, row;
        private double radian;

        /**
         * Creates a new container specifying info such as the size to scale the view
         * relative to the rest of the content and the distance the item should be
         * from the center.
         *
         * @param view the view to be displayed
         * @param size the size to scale the view relative to other items
         * @param distance the distance from the center to position the view at relative to other items
         */
        public RadialItem(View view, int size, int distance) {
            this.view = view;
            this.size = size;
            this.distance = distance;
        }

        private void setRadius(int radius) {
            this.radius = radius;
            FrameLayout.LayoutParams layoutParams = new LayoutParams(radius * 2, radius * 2);
            view.setLayoutParams(layoutParams);
        }

        private float getX() {
            return (float) (RadialUtils.getRadius(row) * Math.sin((Math.PI / 2) - radian)) - radius;
        }

        private float getY() {
            return (float) (RadialUtils.getRadius(row) * Math.sin(radian)) - radius;
        }

    }

}
