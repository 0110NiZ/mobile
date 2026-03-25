package com.example.smartschoolfinder.utils;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleUtils {
    public static void setLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Configuration config = context.getResources().getConfiguration();
        config.setLocale(locale);
        context.createConfigurationContext(config);
    }
}
