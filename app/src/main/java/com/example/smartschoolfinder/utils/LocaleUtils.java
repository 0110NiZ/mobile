package com.example.smartschoolfinder.utils;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;

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
}
