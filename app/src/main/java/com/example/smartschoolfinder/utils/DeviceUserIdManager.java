package com.example.smartschoolfinder.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.smartschoolfinder.constants.AppConstants;

import java.util.UUID;

public final class DeviceUserIdManager {
    private static final String KEY_DEVICE_USER_ID = "device_user_id_v1";

    private DeviceUserIdManager() {
    }

    public static String getOrCreate(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String existing = prefs.getString(KEY_DEVICE_USER_ID, null);
        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }
        String id = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_DEVICE_USER_ID, id).apply();
        return id;
    }
}

