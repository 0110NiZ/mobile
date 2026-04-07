package com.example.smartschoolfinder;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.smartschoolfinder.constants.AppConstants;

public class SchoolExplorerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);
        int mode = prefs.getInt(
                AppConstants.KEY_THEME_MODE,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);

        String appLanguage = prefs.getString(AppConstants.KEY_APP_LANGUAGE, "");
        if (appLanguage != null && !appLanguage.trim().isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appLanguage));
        }
    }
}
