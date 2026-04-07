package com.example.smartschoolfinder.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.smartschoolfinder.constants.AppConstants;

import java.util.Locale;

public class LocaleUtils {
    public static Context wrap(Context context, String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return context;
        }
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return new ContextWrapper(context.createConfigurationContext(config));
    }

    public static void setLocale(Context context, String languageCode) {
        wrap(context, languageCode);
    }

    /**
     * Whether school list/detail should use Chinese fields from EDB JSON
     * (中文名稱 / 中文地址 / 分區 / 學校類型) when present.
     */
    public static boolean prefersChineseSchoolData(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(AppConstants.KEY_APP_LANGUAGE, "");
        if (saved != null && !saved.trim().isEmpty()) {
            return saved.toLowerCase(Locale.ROOT).startsWith("zh");
        }
        LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
        for (int i = 0; i < appLocales.size(); i++) {
            Locale loc = appLocales.get(i);
            if (loc != null && "zh".equalsIgnoreCase(loc.getLanguage())) {
                return true;
            }
        }
        Configuration cfg = context.getResources().getConfiguration();
        if (cfg.getLocales().size() > 0) {
            return "zh".equalsIgnoreCase(cfg.getLocales().get(0).getLanguage());
        }
        return false;
    }
}
