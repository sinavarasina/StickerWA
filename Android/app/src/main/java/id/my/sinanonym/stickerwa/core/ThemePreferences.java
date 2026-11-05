package id.my.sinanonym.stickerwa.core;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import id.my.sinanonym.stickerwa.R;

public final class ThemePreferences {
    private static final String PREF_NAME = "stickerwa_theme_prefs";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String KEY_PALETTE = "palette";

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    public static final String PALETTE_CATPPUCCIN = "catppuccin";
    public static final String PALETTE_GRUVBOX = "gruvbox";
    public static final String PALETTE_MONET = "monet";

    private ThemePreferences() {}

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void initNightMode(Context context) {
        applyNightMode(getThemeMode(context));
    }

    public static void applyTheme(Context context) {
        initNightMode(context);
        context.setTheme(getThemeResId(context));
    }

    public static void applyNightMode(String mode) {
        if (MODE_LIGHT.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (MODE_DARK.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static String getThemeMode(Context context) {
        return getPrefs(context).getString(KEY_THEME_MODE, MODE_SYSTEM);
    }

    public static void setThemeMode(Context context, String mode) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode).apply();
        applyNightMode(mode);
    }

    public static String getPalette(Context context) {
        return getPrefs(context).getString(KEY_PALETTE, PALETTE_CATPPUCCIN);
    }

    public static void setPalette(Context context, String palette) {
        getPrefs(context).edit().putString(KEY_PALETTE, palette).apply();
    }

    public static int getThemeResId(Context context) {
        String palette = getPalette(context);
        if (PALETTE_GRUVBOX.equals(palette)) {
            return R.style.AppTheme_Gruvbox;
        } else if (PALETTE_MONET.equals(palette)) {
            return R.style.AppTheme_Monet;
        }
        return R.style.AppTheme_Catppuccin;
    }

    public static void showThemeDialog(Activity activity) {
        String currentMode = getThemeMode(activity);
        String currentPalette = getPalette(activity);

        String[] options = new String[] {
            "Catppuccin (System Default)",
            "Catppuccin Light (Latte)",
            "Catppuccin Dark (Mocha)",
            "Gruvbox (System Default)",
            "Gruvbox Light",
            "Gruvbox Dark"
        };

        int selectedIndex = 0;
        if (PALETTE_CATPPUCCIN.equals(currentPalette)) {
            if (MODE_LIGHT.equals(currentMode)) selectedIndex = 1;
            else if (MODE_DARK.equals(currentMode)) selectedIndex = 2;
            else selectedIndex = 0;
        } else {
            if (MODE_LIGHT.equals(currentMode)) selectedIndex = 4;
            else if (MODE_DARK.equals(currentMode)) selectedIndex = 5;
            else selectedIndex = 3;
        }

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.theme_dialog_title)
            .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                String newPalette = PALETTE_CATPPUCCIN;
                String newMode = MODE_SYSTEM;
                switch (which) {
                    case 0:
                        newPalette = PALETTE_CATPPUCCIN;
                        newMode = MODE_SYSTEM;
                        break;
                    case 1:
                        newPalette = PALETTE_CATPPUCCIN;
                        newMode = MODE_LIGHT;
                        break;
                    case 2:
                        newPalette = PALETTE_CATPPUCCIN;
                        newMode = MODE_DARK;
                        break;
                    case 3:
                        newPalette = PALETTE_GRUVBOX;
                        newMode = MODE_SYSTEM;
                        break;
                    case 4:
                        newPalette = PALETTE_GRUVBOX;
                        newMode = MODE_LIGHT;
                        break;
                    case 5:
                        newPalette = PALETTE_GRUVBOX;
                        newMode = MODE_DARK;
                        break;
                }
                setPalette(activity, newPalette);
                setThemeMode(activity, newMode);
                dialog.dismiss();
                activity.recreate();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
