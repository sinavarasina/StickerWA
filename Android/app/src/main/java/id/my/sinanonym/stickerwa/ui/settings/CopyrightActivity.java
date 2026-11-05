package id.my.sinanonym.stickerwa.ui.settings;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.ui.base.BaseActivity;

/**
 * Dedicated Copyright Notice and Legal Disclaimer screen detailing character art
 * attribution, non-commercial fair use status, community submissions, and WhatsApp platform notices.
 */
public class CopyrightActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_copyright);

        View appBar = findViewById(R.id.app_bar);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(R.string.copyright_screen_title);
            }
        }

        if (appBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBar, (v, insets) -> {
                Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int leftPadding = Math.max(cutoutInsets.left, sysBars.left);
                int rightPadding = Math.max(cutoutInsets.right, sysBars.right);
                v.setPadding(leftPadding, sysBars.top, rightPadding, 0);
                return insets;
            });
        }

        View scrollView = findViewById(R.id.copyright_scroll);
        if (scrollView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
                Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int leftPadding = Math.max(cutoutInsets.left, sysBars.left);
                int rightPadding = Math.max(cutoutInsets.right, sysBars.right);
                float density = getResources().getDisplayMetrics().density;
                boolean isLand = getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                int sidePad = (int) ((isLand ? 24 : 16) * density);
                int topPad = (int) (14 * density);
                int botPad = (int) (32 * density) + sysBars.bottom;
                v.setPadding(leftPadding + sidePad, topPad, rightPadding + sidePad, botPad);
                return insets;
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
