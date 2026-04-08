package com.example.smartschoolfinder.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import com.example.smartschoolfinder.constants.AppConstants;

public final class LocationModeUtils {
    public static final int MODE_CURRENT = 0;
    public static final int MODE_CUSTOM = 1;
    public static final int MODE_NONE = 2;

    private LocationModeUtils() {
    }

    public static final class LatLng {
        public final double lat;
        public final double lon;

        public LatLng(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static int getLocationMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(AppConstants.KEY_LOCATION_MODE)) {
            return prefs.getInt(AppConstants.KEY_LOCATION_MODE, MODE_CURRENT);
        }
        boolean old = prefs.getBoolean(AppConstants.KEY_USE_LOCATION, true);
        return old ? MODE_CURRENT : MODE_NONE;
    }

    public static LatLng getEffectiveLocation(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        int mode = getLocationMode(context);
        if (mode == MODE_NONE) {
            return null;
        }
        if (mode == MODE_CUSTOM) {
            if (!prefs.contains(AppConstants.KEY_CUSTOM_LOCATION_LAT)
                    || !prefs.contains(AppConstants.KEY_CUSTOM_LOCATION_LON)) {
                return null;
            }
            float lat = prefs.getFloat(AppConstants.KEY_CUSTOM_LOCATION_LAT, Float.NaN);
            float lon = prefs.getFloat(AppConstants.KEY_CUSTOM_LOCATION_LON, Float.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                return null;
            }
            if (Math.abs(lat) < 1e-6f && Math.abs(lon) < 1e-6f) {
                return null;
            }
            return new LatLng(lat, lon);
        }

        // MODE_CURRENT
        if (!LocationHelper.hasLocationPermission(context)) {
            return null;
        }
        if (!LocationHelper.isSystemLocationEnabled(context)) {
            return null;
        }
        Location loc = LocationHelper.getLatestAcceptedLocation(context);
        if (!LocationHelper.isValidLocationForDistance(context, loc, true)) {
            loc = LocationHelper.getBestLastKnownLocation(context);
        }
        if (!LocationHelper.isValidLocationForDistance(context, loc, true)) {
            return null;
        }
        return new LatLng(loc.getLatitude(), loc.getLongitude());
    }
}
