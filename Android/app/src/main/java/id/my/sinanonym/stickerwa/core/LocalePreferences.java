package id.my.sinanonym.stickerwa.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * Handles in-app language preferences supporting Follow System, English, and Bahasa Indonesia.
 */
public final class LocalePreferences {

    private static final String PREF_NAME = "stickerwa_locale_prefs";
    public static final String KEY_LOCALE = "selected_locale";

    public static final String LOCALE_SYSTEM = "system";
    public static final String LOCALE_EN = "en";
    public static final String LOCALE_IN = "in";

    private LocalePreferences() {}

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getLocale(Context context) {
        return getPrefs(context).getString(KEY_LOCALE, LOCALE_SYSTEM);
    }

    public static void setLocale(Context context, String localeCode) {
        getPrefs(context).edit().putString(KEY_LOCALE, localeCode).apply();
    }

    public static Context wrapContext(Context context) {
        String lang = getLocale(context);
        if (LOCALE_SYSTEM.equals(lang)) {
            return context;
        }

        Locale targetLocale = new Locale(lang);
        Locale.setDefault(targetLocale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(targetLocale);
            return context.createConfigurationContext(config);
        } else {
            config.locale = targetLocale;
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            return context;
        }
    }
}
