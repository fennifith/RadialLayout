package james.radiallayoutsample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewTreeObserver;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import james.radiallayout.CircleImageView;
import james.radiallayout.utils.ConversionUtils;
import james.radiallayout.RadialLayout;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RadialLayout layout = findViewById(R.id.radialLayout);

        List<RadialLayout.RadialItem> items = new ArrayList<>();

        CircleImageView profileImageView = new CircleImageView(this);
        profileImageView.setElevation(ConversionUtils.dpToPx(4));
        items.add(new RadialLayout.RadialItem(profileImageView, 3, -1));
        profileImageView.getViewTreeObserver().addOnGlobalLayoutListener(new GlideTreeObserver(profileImageView));

        for (int i = 1; i < 100; i++) {
            final CircleImageView imageView = new CircleImageView(this);
            imageView.setElevation(ConversionUtils.dpToPx(2));
            items.add(new RadialLayout.RadialItem(imageView, (int) (Math.random() * 4), (int) (Math.random() * 4)));
            imageView.getViewTreeObserver().addOnGlobalLayoutListener(new GlideTreeObserver(imageView));
        }

        layout.setItems(items);
    }

    public static class GlideTreeObserver implements ViewTreeObserver.OnGlobalLayoutListener {

        private CircleImageView imageView;

        public GlideTreeObserver(CircleImageView imageView) {
            this.imageView = imageView;
        }

        @Override
        public void onGlobalLayout() {
            String url;
            switch ((int) (Math.random() * 4)) {
                case 0:
                    url = "https://TheAndroidMaster.github.io/images/headers/highway.jpg";
                    break;
                case 1:
                    url = "https://TheAndroidMaster.github.io/images/headers/onamountain.jpeg";
                    break;
                case 2:
                    url = "https://TheAndroidMaster.github.io/images/headers/rocks.jpg";
                    break;
                case 3:
                    url = "https://TheAndroidMaster.github.io/images/headers/vukheader.jpg";
                    break;
                case 4:
                    url = "https://TheAndroidMaster.github.io/images/headers/cabbage.jpg";
                    break;
                default:
                    return;
            }

            Glide.with(imageView.getContext()).load(url).into(imageView);
            imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }
}
