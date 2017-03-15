package Modules;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class LoadingScreen {
    LinearLayout activityContent;
    RelativeLayout loadingContent;
    TextView loadingText;
    ProgressBar spinner;
    LinearLayout loadingBg;
    boolean loading;

    public LoadingScreen(LinearLayout activityContent, RelativeLayout loadingContent, TextView loadingText, ProgressBar spinner,
                         LinearLayout loadingBg) {
        this.activityContent = activityContent;
        this.loadingContent = loadingContent;
        this.loadingText = loadingText;
        this.spinner = spinner;
        this.loadingBg = loadingBg;
        this.loading = false;
    }

    public void enableLoading() {
        loading = true;
//        enableDisableView(activityContent, false);
        loadingBg.setVisibility(View.VISIBLE);
        loadingContent.setVisibility(View.VISIBLE);
    }

    public void disableLoading() {
        loading = false;
//        enableDisableView(activityContent,true);
        loadingBg.setVisibility(View.GONE);
        loadingContent.setVisibility(View.GONE);
    }

    public void updateLoadingText(String text) {
        loadingText.setText(text);
    }

    public static void enableDisableView(View view, boolean enabled) {
        enabled = true;
        view.setEnabled(enabled);
        view.setClickable(enabled);

        if ( view instanceof ViewGroup ) {
            ViewGroup group = (ViewGroup)view;

            for ( int idx = 0 ; idx < group.getChildCount() ; idx++ ) {
                enableDisableView(group.getChildAt(idx), enabled);
            }
        }
    }

}
