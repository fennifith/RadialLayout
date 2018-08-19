RadialLayout is a scrollable-ish view that arranges images in circles extending from the center of the screen that was originally made for [Lemonade](https://lemonade.social). For demonstration and experimentation, an apk of the sample project can be downloaded [here](../../releases).

## Screenshots

|With Shadows|Without Shadows|GIF (pronounced "jeff")|
|-----|-----|-----|
|![img](./images/shadows.png?raw=true)|![img](./images/noshadows.png?raw=true)|![img](./images/jeff.gif?raw=true)|

## Usage

### Installation

The Gradle dependency is available through jCenter, which is used by default in Android Studio. To add the dependency to your project, copy this line into the dependencies section of your app's build.gradle file.

```gradle
implementation 'me.jfenn:radiallayout:0.0.1'
```

### Layout

You can add the view into any layout like this:

```xml
<me.jfenn.radiallayout.RadialLayoutView
    android:id="@+id/radialLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

There are not currently any XML attributes that can be used configure the view because of the way that this project is designed. Feel free to create an issue if there are any that you want. I can't think of any.

### Items

There are three methods to modify the items of the view: `setCenterItem`, `setItems`, and `updateItems`. 

#### Setting the Center Item

This method accepts a `CenteredRadialItem`. This class does not properly implement of the functionality of the `BaseRadialItem` subclass, and as such should not be passed to `setItems` or `updateItems`. It can be constructed and applied to the view as follows:

```java
CenterItem item = new CenteredRadialItem(
    bitmap, // the image to display - will be resized to fit inside the item appropriately
    72      // the diameter of the center item, in dp
);

// (optional), draws an outline around the item
item.setOutline(
    3,          // the thickness (dp) of the outline
    4,          // the distance (dp) between the edge of the image and the outline
    Color.BLACK // the color of the outline
);

radialLayout.setCenterItem(item);
```

This method can be called at any time, and will not affect any of the functionality of the rest of the class.

#### Setting / Updating Radial Items

The `setItems` and `updateItems` methods set and update the items in the view, respectively. While `setItems` replaces the entire list of items with a new one, `updateItems` will attempt to "morph" the current list into a new one, and will try to preserve the radius and reuse bitmaps from items that are present in both lists. `updateItems` should only be called after `setItems`.

Both `setItems` and `updateItems` accept a list of `BaseRadialItem`s and is intended to change the entire set of items to a new list. This list can contain classes other than `RadialItem` (for example, your own custom subclasses that draw their items differently), but it should not contain any `CenteredRadialItem` classes as stated above. RadialItems can be constructed as follows:

```java
RadialItem item = new RadialItem(
    "item-5",   // The id of the item, used for comparisons when updateItems is called
    image,      // the image to display - will be resized to fit inside the item appropriately
    1,          // the size of the item, relative to other items in the view
    5           // the distance of the item from the center, relative to other items in the view
);
```

Both of the methods return a `RadialLayoutView.Builder` that allows you to specify certain parameters as follows:

```java
radialLayout.setItems(items)
  .withItemRadius(36)           // the average radius of the views, in dp, +/- the variation (next line)
  .withItemRadiusVariation(6)   // how much (in dp) the radius of the views should vary according to the 'size' attribute of the items
  .withItemSeparation(8)        // the minimum distance between items in the view, in dp
  .withShadowRadius(4)          // the radius of the shadow to draw behind the view (dp)
  .withShadowOffset(2)          // the vertical offset of the shadow (dp)
  .apply();
```

The items in the view will not be applied instantly, as their positions are calculated in a background thread. To be notified when they are applied, you can pass a `RadialLayout.Builder.OnAppliedListener` to the `apply()` method, or call `applySynchronous()` in a background thread of your own.

Keep in mind that when a shadow radius and offset are specified, performance of the view will drop significantly for no apparent reason. I have not found a solution to this. See issue [#1](../../issues/1).

### Configuration

#### Listening for Click Events

This is pretty straightforward.

```java
radialLayout.setOnItemClickListener(new RadialLayoutView.OnItemClickListener() {
    @Override
    public void onItemClick(
            RadialLayoutView layout,    // the layout that the click event is from
            BaseRadialItem item,        // the item that was clicked
            int index                   // the index of the item that was clicked in the array
    ) {
        // do something
        
        // perhaps the easiest way to detect which item corresponds to what is by using the id argument
        // in the constructor for something other than making sure the lists are merged properly?
        
        // hint: item.getId();
    }
});

// and, for the center item...

radialLayout.setOnCenterClickListener(new RadialLayoutView.OnCenterClickListener() {
    @Override
    public void onCenterClick(RadialLayoutView layout /* the layout that the click event is from */) {
        // do something
    }
});
```

#### Paint

If for some reason you feel like it, there are also two methods in RadialLayoutView that return the paint used for shadows and images that you can use to modify... things.

```java
Paint imagePaint = radialLayout.getPaint();
Paint shadowPaint = radialLayout.getShadowPaint();
```
