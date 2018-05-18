package james.radiallayoutsample;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.List;

import james.radiallayout.BaseRadialItem;
import james.radiallayout.CenteredRadialItem;
import james.radiallayout.RadialItem;
import james.radiallayout.RadialLayoutView;

public class MainActivity extends AppCompatActivity {

    private RadialLayoutView layout;
    private Bitmap resource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layout = findViewById(R.id.radialLayout);

        Glide.with(this).asBitmap().load("https://TheAndroidMaster.github.io/images/headers/highway.jpg").into(new SimpleTarget<Bitmap>() {
            @Override
            public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                MainActivity.this.resource = resource;
                List<BaseRadialItem> items = new ArrayList<>();

                for (int i = 0; i < 5; i++)
                    items.add(new RadialItem("i", resource, (int) (Math.random() * 4), (int) (Math.random() * 4)));

                layout.setItems(items).apply();

                CenteredRadialItem centerItem = new CenteredRadialItem(resource, 64);
                centerItem.setOutline(2, 4, ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
                layout.setCenterItem(centerItem);
            }
        });

        layout.setClickListener(new RadialLayoutView.ClickListener() {
            @Override
            public void onClick(RadialLayoutView layout, BaseRadialItem item, int index) {
                List<BaseRadialItem> items = layout.getItems();
                items.add(new RadialItem("h", resource, (int) (Math.random() * 5) + 1, items.size() + 8));
                layout.updateItems(items).apply();
            }
        });

        layout.setCenterListener(new RadialLayoutView.CenterClickListener() {
            @Override
            public void onCenterClick(RadialLayoutView layout) {
                List<BaseRadialItem> items = layout.getItems();
                if (items.size() > 0) {
                    items.remove(items.size() - 1);
                    layout.updateItems(items).apply();
                } else {
                    items.add(new RadialItem("h", resource, (int) (Math.random() * 5) + 1, items.size() + 8));
                    layout.updateItems(items).apply();
                }
            }
        });

        ((CheckBox) findViewById(R.id.shadows)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                layout.setItems(layout.getItems())
                        .withShadowRadius(b ? 4 : 0)
                        .withShadowOffset(b ? 2 : 0)
                        .apply();
            }
        });
    }
}
