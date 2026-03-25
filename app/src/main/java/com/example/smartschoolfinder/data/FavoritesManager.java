package com.example.smartschoolfinder.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.smartschoolfinder.constants.AppConstants;

import java.util.HashSet;
import java.util.Set;

public class FavoritesManager {
    private final SharedPreferences prefs;

    public FavoritesManager(Context context) {
        prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> getFavorites() {
        return new HashSet<>(prefs.getStringSet(AppConstants.KEY_FAVORITES, new HashSet<>()));
    }

    public boolean isFavorite(String schoolId) {
        return getFavorites().contains(schoolId);
    }

    public void toggleFavorite(String schoolId) {
        Set<String> set = getFavorites();
        if (set.contains(schoolId)) {
            set.remove(schoolId);
        } else {
            set.add(schoolId);
        }
        prefs.edit().putStringSet(AppConstants.KEY_FAVORITES, set).apply();
    }
}
