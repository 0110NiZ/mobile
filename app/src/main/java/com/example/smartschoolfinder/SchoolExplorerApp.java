package com.example.smartschoolfinder;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.utils.LocaleUtils;

public class SchoolExplorerApp extends Application {
    private static boolean hasShownLocationNoticeThisProcess = false;

    public static synchronized boolean shouldShowLocationNoticeOncePerProcess() {
        if (hasShownLocationNoticeThisProcess) {
            return false;
        }
        hasShownLocationNoticeThisProcess = true;
        return true;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleUtils.wrapWithSavedAppLanguage(base));
    }

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
