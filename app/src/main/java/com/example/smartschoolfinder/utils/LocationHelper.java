package com.example.smartschoolfinder.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 运行时定位权限与最后已知位置读取；失败时由调用方使用香港默认坐标。
 */
public final class LocationHelper {

    /** 香港大致中心，作为无权限或无法定位时的兜底 */
    public static final double HK_DEFAULT_LATITUDE = 22.3193;
    public static final double HK_DEFAULT_LONGITUDE = 114.1694;

    public static final int REQUEST_CODE_LOCATION = 5001;

    private LocationHelper() {
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQUEST_CODE_LOCATION
        );
    }

    /**
     * 在已授权前提下返回较新的 last known location；无权限或异常时返回 null（勿在未授权时调用以免崩溃）。
     */
    @SuppressLint("MissingPermission")
    public static Location getBestLastKnownLocation(Context context) {
        if (!hasLocationPermission(context)) {
            return null;
        }
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return null;
            }
            Location gps = null;
            Location network = null;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                network = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            return pickBetterLocation(gps, network);
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * Some emulators/devices may return a placeholder (0,0) or out-of-range coordinates.
     * Treat those as invalid so the app can fall back to HK default.
     */
    public static boolean isValidLocation(Location loc) {
        if (loc == null) {
            return false;
        }
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return false;
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return false;
        }
        // Exclude "not set" coordinate used by some emulators/providers.
        return !(Math.abs(lat) < 1e-6 && Math.abs(lon) < 1e-6);
    }

    private static Location pickBetterLocation(Location a, Location b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.getTime() >= b.getTime() ? a : b;
    }

    /**
     * School data is Hong Kong only; emulator/devices may report overseas defaults.
     */
    public static boolean isLikelyHongKong(double lat, double lon) {
        return lat >= 22.10 && lat <= 22.60 && lon >= 113.80 && lon <= 114.50;
    }
}
