package com.example.smartschoolfinder.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.smartschoolfinder.constants.AppConstants;

import java.util.Locale;

public class LocaleUtils {
    public static String getSavedLanguageTag(Context context) {
        if (context == null) {
            return "";
        }
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(AppConstants.KEY_APP_LANGUAGE, "");
        return saved == null ? "" : saved.trim();
    }

    public static Context wrapWithSavedAppLanguage(Context context) {
        String tag = getSavedLanguageTag(context);
        if (tag.isEmpty()) {
            return context;
        }
        return wrap(context, tag);
    }

    public static Context wrap(Context context, String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return context;
        }
        Locale locale = Locale.forLanguageTag(languageCode.trim());
        if (locale == null || locale.getLanguage() == null || locale.getLanguage().trim().isEmpty()) {
            locale = new Locale(languageCode.trim());
        }
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return new ContextWrapper(context.createConfigurationContext(config));
    }

    public static void setLocale(Context context, String languageCode) {
        wrap(context, languageCode);
    }

    private static boolean localeListHasChinese(LocaleList list) {
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            Locale loc = list.get(i);
            if (loc != null && "zh".equalsIgnoreCase(loc.getLanguage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean localeListCompatHasChinese(LocaleListCompat list) {
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            Locale loc = list.get(i);
            if (loc != null && "zh".equalsIgnoreCase(loc.getLanguage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean configurationMayUseChinese(Configuration cfg) {
        return cfg != null && localeListHasChinese(cfg.getLocales());
    }

    /**
     * Whether APIs / parsing should prefer Chinese fields (EDB 中文*, transport name_zh, etc.).
     * Uses explicit app language pref first; otherwise AppCompat application locales, then
     * both the given context and application context (Activity vs Application can differ).
     */
    public static boolean prefersChineseSchoolData(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(AppConstants.KEY_APP_LANGUAGE, "");
        if (saved != null && !saved.trim().isEmpty()) {
            return saved.toLowerCase(Locale.ROOT).startsWith("zh");
        }
        if (localeListCompatHasChinese(AppCompatDelegate.getApplicationLocales())) {
            return true;
        }
        if (configurationMayUseChinese(context.getResources().getConfiguration())) {
            return true;
        }
        Context app = context.getApplicationContext();
        if (app != null && app != context) {
            if (configurationMayUseChinese(app.getResources().getConfiguration())) {
                return true;
            }
        }
        return false;
    }
}
