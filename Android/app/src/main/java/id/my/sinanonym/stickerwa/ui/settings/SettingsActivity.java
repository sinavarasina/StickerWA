package id.my.sinanonym.stickerwa.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

import id.my.sinanonym.stickerwa.BuildConfig;
import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.core.AppExecutors;
import id.my.sinanonym.stickerwa.core.LocalePreferences;
import id.my.sinanonym.stickerwa.core.ThemePreferences;
import id.my.sinanonym.stickerwa.data.StickerRepository;
import id.my.sinanonym.stickerwa.data.model.StickerPack;
import id.my.sinanonym.stickerwa.ui.base.BaseActivity;

/**
 * Settings screen providing separated Theme Mode & Color Palette pickers,
 * along with detailed APK Build Information.
 */
public class SettingsActivity extends BaseActivity {

    private View optionModeSystem;
    private View optionModeLight;
    private View optionModeDark;
    private View checkModeSystem;
    private View checkModeLight;
    private View checkModeDark;

    private View optionPaletteCatppuccin;
    private View optionPaletteGruvbox;
    private View optionPaletteMonet;
    private View checkPaletteCatppuccin;
    private View checkPaletteGruvbox;
    private View checkPaletteMonet;

    private View optionLangSystem;
    private View optionLangEn;
    private View optionLangIn;
    private View checkLangSystem;
    private View checkLangEn;
    private View checkLangIn;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View appBar = findViewById(R.id.app_bar);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(R.string.settings_title);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
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
        View scrollView = findViewById(R.id.settings_scroll_view);
        if (scrollView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
                Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int leftPadding = Math.max(cutoutInsets.left, sysBars.left);
                int rightPadding = Math.max(cutoutInsets.right, sysBars.right);
                float density = getResources().getDisplayMetrics().density;
                int sidePad = (int) (16 * density);
                int topPad = (int) (12 * density);
                int botPad = (int) (24 * density) + sysBars.bottom;
                v.setPadding(leftPadding + sidePad, topPad, rightPadding + sidePad, botPad);
                return insets;
            });
        }

        bindThemeModeControls();
        bindPaletteControls();
        bindLanguageControls();
        populateBuildInfo();
        bindDeveloperControls();
        bindCopyrightRow();
    }

    private void bindCopyrightRow() {
        View cardCopyright = findViewById(R.id.card_copyright_notice);
        if (cardCopyright != null) {
            cardCopyright.setOnClickListener(v -> startActivity(new Intent(this, CopyrightActivity.class)));
        }
    }

    private void bindThemeModeControls() {
        optionModeSystem = findViewById(R.id.option_mode_system);
        optionModeLight = findViewById(R.id.option_mode_light);
        optionModeDark = findViewById(R.id.option_mode_dark);
        checkModeSystem = findViewById(R.id.check_mode_system);
        checkModeLight = findViewById(R.id.check_mode_light);
        checkModeDark = findViewById(R.id.check_mode_dark);

        updateThemeModeSelectionUI(ThemePreferences.getThemeMode(this));

        optionModeSystem.setOnClickListener(v -> applyNewThemeMode(ThemePreferences.MODE_SYSTEM));
        optionModeLight.setOnClickListener(v -> applyNewThemeMode(ThemePreferences.MODE_LIGHT));
        optionModeDark.setOnClickListener(v -> applyNewThemeMode(ThemePreferences.MODE_DARK));
    }

    private void applyNewThemeMode(String newMode) {
        String current = ThemePreferences.getThemeMode(this);
        if (!current.equals(newMode)) {
            ThemePreferences.setThemeMode(this, newMode);
            recreate();
        }
    }

    private void updateThemeModeSelectionUI(String mode) {
        boolean isLight = ThemePreferences.MODE_LIGHT.equals(mode);
        boolean isDark = ThemePreferences.MODE_DARK.equals(mode);
        boolean isSystem = !isLight && !isDark;

        optionModeSystem.setBackgroundResource(isSystem ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);
        optionModeLight.setBackgroundResource(isLight ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);
        optionModeDark.setBackgroundResource(isDark ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);

        checkModeSystem.setVisibility(isSystem ? View.VISIBLE : View.GONE);
        checkModeLight.setVisibility(isLight ? View.VISIBLE : View.GONE);
        checkModeDark.setVisibility(isDark ? View.VISIBLE : View.GONE);
    }

    private void bindPaletteControls() {
        optionPaletteCatppuccin = findViewById(R.id.option_palette_catppuccin);
        optionPaletteGruvbox = findViewById(R.id.option_palette_gruvbox);
        optionPaletteMonet = findViewById(R.id.option_palette_monet);
        checkPaletteCatppuccin = findViewById(R.id.check_palette_catppuccin);
        checkPaletteGruvbox = findViewById(R.id.check_palette_gruvbox);
        checkPaletteMonet = findViewById(R.id.check_palette_monet);

        updatePaletteSelectionUI(ThemePreferences.getPalette(this));

        optionPaletteCatppuccin.setOnClickListener(v -> applyNewPalette(ThemePreferences.PALETTE_CATPPUCCIN));
        optionPaletteGruvbox.setOnClickListener(v -> applyNewPalette(ThemePreferences.PALETTE_GRUVBOX));
        optionPaletteMonet.setOnClickListener(v -> applyNewPalette(ThemePreferences.PALETTE_MONET));
    }

    private void applyNewPalette(String newPalette) {
        String current = ThemePreferences.getPalette(this);
        if (!current.equals(newPalette)) {
            ThemePreferences.setPalette(this, newPalette);
            recreate();
        }
    }

    private void updatePaletteSelectionUI(String palette) {
        boolean isCatppuccin = ThemePreferences.PALETTE_CATPPUCCIN.equals(palette);
        boolean isGruvbox = ThemePreferences.PALETTE_GRUVBOX.equals(palette);
        boolean isMonet = ThemePreferences.PALETTE_MONET.equals(palette);

        optionPaletteCatppuccin.setBackgroundResource(isCatppuccin ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);
        optionPaletteGruvbox.setBackgroundResource(isGruvbox ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);
        optionPaletteMonet.setBackgroundResource(isMonet ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);

        checkPaletteCatppuccin.setVisibility(isCatppuccin ? View.VISIBLE : View.GONE);
        checkPaletteGruvbox.setVisibility(isGruvbox ? View.VISIBLE : View.GONE);
        checkPaletteMonet.setVisibility(isMonet ? View.VISIBLE : View.GONE);
    }

    private void bindLanguageControls() {
        optionLangSystem = findViewById(R.id.option_lang_system);
        optionLangEn = findViewById(R.id.option_lang_en);
        optionLangIn = findViewById(R.id.option_lang_in);
        checkLangSystem = findViewById(R.id.check_lang_system);
        checkLangEn = findViewById(R.id.check_lang_en);
        checkLangIn = findViewById(R.id.check_lang_in);

        if (optionLangSystem != null) {
            updateLanguageSelectionUI(LocalePreferences.getLocale(this));

            optionLangSystem.setOnClickListener(v -> applyNewLocale(LocalePreferences.LOCALE_SYSTEM));
            optionLangEn.setOnClickListener(v -> applyNewLocale(LocalePreferences.LOCALE_EN));
            optionLangIn.setOnClickListener(v -> applyNewLocale(LocalePreferences.LOCALE_IN));
        }
    }

    private void applyNewLocale(String newLocale) {
        String current = LocalePreferences.getLocale(this);
        if (!current.equals(newLocale)) {
            LocalePreferences.setLocale(this, newLocale);
            recreate();
        }
    }

    private void updateLanguageSelectionUI(String localeCode) {
        boolean isSystem = LocalePreferences.LOCALE_SYSTEM.equals(localeCode);
        boolean isEn = LocalePreferences.LOCALE_EN.equals(localeCode);
        boolean isIn = LocalePreferences.LOCALE_IN.equals(localeCode);

        if (optionLangSystem != null) {
            optionLangSystem.setBackgroundResource(isSystem ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);
            optionLangEn.setBackgroundResource(isEn ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);
            optionLangIn.setBackgroundResource(isIn ? R.drawable.bg_thumb_selected : R.drawable.bg_thumb_unselected);

            checkLangSystem.setVisibility(isSystem ? View.VISIBLE : View.GONE);
            checkLangEn.setVisibility(isEn ? View.VISIBLE : View.GONE);
            checkLangIn.setVisibility(isIn ? View.VISIBLE : View.GONE);
        }
    }

    private void populateBuildInfo() {
        TextView versionView = findViewById(R.id.build_info_version);
        TextView packageView = findViewById(R.id.build_info_package);
        TextView typeView = findViewById(R.id.build_info_type);
        TextView timeView = findViewById(R.id.build_info_time);
        TextView commitView = findViewById(R.id.build_info_commit);
        TextView archView = findViewById(R.id.build_info_arch);
        TextView sdkView = findViewById(R.id.build_info_sdk);
        TextView packsView = findViewById(R.id.build_info_packs);

        versionView.setText(getString(R.string.build_info_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        packageView.setText(getString(R.string.build_info_package_format, BuildConfig.APPLICATION_ID));
        typeView.setText(getString(R.string.build_info_type_format, BuildConfig.BUILD_TYPE.toUpperCase()));
        timeView.setText(getString(R.string.build_info_time_format, BuildConfig.BUILD_TIME));
        if (commitView != null) {
            commitView.setText(getString(R.string.build_info_commit_format, BuildConfig.GIT_COMMIT, BuildConfig.GIT_BRANCH));
        }

        String primaryAbi = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.SUPPORTED_ABIS.length > 0)
                ? Build.SUPPORTED_ABIS[0]
                : Build.CPU_ABI;
        archView.setText(getString(R.string.build_info_arch_format, primaryAbi));
        sdkView.setText(getString(R.string.build_info_sdk_format, Build.VERSION.SDK_INT, Build.VERSION.RELEASE));

        AppExecutors.execute(
                () -> StickerRepository.fetchStickerPacks(getApplicationContext()),
                packs -> {
                    int totalPacks = packs.size();
                    int totalStickers = 0;
                    for (StickerPack pack : packs) {
                        List<?> s = pack.getStickers();
                        if (s != null) {
                            totalStickers += s.size();
                        }
                    }
                    packsView.setText(getString(R.string.build_info_packs_format, totalPacks, totalStickers));
                }
        );
    }

    private void bindDeveloperControls() {
        View btnGithub = findViewById(R.id.btn_dev_github);
        View btnCodeberg = findViewById(R.id.btn_dev_codeberg);
        View btnGitlab = findViewById(R.id.btn_dev_gitlab);
        View btnEmail = findViewById(R.id.btn_dev_email);
        View btnWebsite = findViewById(R.id.btn_dev_website);

        if (btnGithub != null) {
            btnGithub.setOnClickListener(v -> safeOpenUrl("https://github.com/sinavarasina"));
        }
        if (btnCodeberg != null) {
            btnCodeberg.setOnClickListener(v -> safeOpenUrl("https://codeberg.org/sinavarasina"));
        }
        if (btnGitlab != null) {
            btnGitlab.setOnClickListener(v -> safeOpenUrl("https://gitlab.com/sinanonym"));
        }
        if (btnEmail != null) {
            btnEmail.setOnClickListener(v -> safeSendEmail("sina@sinanonym.my.id"));
        }
        if (btnWebsite != null) {
            btnWebsite.setOnClickListener(v -> safeOpenUrl("https://sinanonym.my.id"));
        }
    }

    private void safeOpenUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void safeSendEmail(String email) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + email));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_no_email_app, Toast.LENGTH_SHORT).show();
        }
    }
}
