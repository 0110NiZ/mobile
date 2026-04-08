package com.example.smartschoolfinder.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 运行时定位权限与最后已知位置读取；失败时由调用方使用香港默认坐标。
 */
public final class LocationHelper {
    private static final String TAG = "LOCATION_HELPER";

    /** 香港大致中心，作为无权限或无法定位时的兜底 */
    public static final double HK_DEFAULT_LATITUDE = 22.3193;
    public static final double HK_DEFAULT_LONGITUDE = 114.1694;

    public static final int REQUEST_CODE_LOCATION = 5001;
    private static final long MAX_FRESH_LOCATION_AGE_MS = 2 * 60 * 1000L;
    private static final long MAX_FALLBACK_LOCATION_AGE_MS = 10 * 60 * 1000L;
    private static final float MAX_FINE_ACCURACY_METERS = 200f;
    private static final float MAX_COARSE_ACCURACY_METERS = 2000f;
    private static final long CURRENT_LOCATION_TIMEOUT_MS = 4500L;
    private static volatile Location latestAcceptedLocation;

    public interface LocationResultCallback {
        void onLocationResult(Location location);
    }

    private LocationHelper() {
    }

    public static boolean hasFineLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasCoarseLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasLocationPermission(Context context) {
        return hasFineLocationPermission(context) || hasCoarseLocationPermission(context);
    }

    public static boolean isSystemLocationEnabled(Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return lm.isLocationEnabled();
            }
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
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
            Log.d(TAG, "getBestLastKnownLocation skip: no permission");
            return null;
        }
        if (!isSystemLocationEnabled(context)) {
            Log.d(TAG, "getBestLastKnownLocation skip: system location disabled");
            return null;
        }
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return null;
            }
            Location gps = null;
            Location network = null;
            Location passive = null;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                network = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                passive = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
            boolean hasFine = hasFineLocationPermission(context);
            Location first = pickBetterLocation(gps, network, hasFine, true);
            Location best = pickBetterLocation(first, passive, hasFine, true);
            logLocation("last_known_best", best, true);
            if (best != null) {
                latestAcceptedLocation = new Location(best);
            }
            return best;
        } catch (SecurityException e) {
            return null;
        }
    }

    @SuppressLint("MissingPermission")
    public static void requestCurrentLocation(Context context, LocationResultCallback callback) {
        if (callback == null) return;
        if (!hasLocationPermission(context)) {
            Log.d(TAG, "requestCurrentLocation skip: no permission");
            callback.onLocationResult(null);
            return;
        }
        if (!isSystemLocationEnabled(context)) {
            Log.d(TAG, "requestCurrentLocation skip: system location disabled");
            callback.onLocationResult(null);
            return;
        }
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                callback.onLocationResult(null);
                return;
            }
            final boolean hasFine = hasFineLocationPermission(context);
            final String provider = chooseProvider(lm, hasFine);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (provider == null) {
                    Log.d(TAG, "requestCurrentLocation no enabled provider, fallback to last known");
                    callback.onLocationResult(getBestLastKnownLocation(context));
                    return;
                }
                lm.getCurrentLocation(provider, (CancellationSignal) null, context.getMainExecutor(),
                        location -> {
                            logLocation("fresh_current_location", location, false);
                            if (isValidLocationForDistance(context, location, false)) {
                                latestAcceptedLocation = new Location(location);
                                callback.onLocationResult(location);
                                return;
                            }
                            Log.d(TAG, "fresh location rejected, fallback to last known");
                            callback.onLocationResult(getBestLastKnownLocation(context));
                        });
                return;
            }
            if (provider == null) {
                callback.onLocationResult(getBestLastKnownLocation(context));
                return;
            }
            Handler handler = new Handler(Looper.getMainLooper());
            final boolean[] delivered = new boolean[]{false};
            final LocationListener[] listenerRef = new LocationListener[1];
            listenerRef[0] = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (delivered[0]) return;
                    delivered[0] = true;
                    try {
                        lm.removeUpdates(listenerRef[0]);
                    } catch (Exception ignored) {
                    }
                    handler.removeCallbacksAndMessages(null);
                    logLocation("fresh_single_update", location, false);
                    if (isValidLocationForDistance(context, location, false)) {
                        latestAcceptedLocation = new Location(location);
                        callback.onLocationResult(location);
                    } else {
                        callback.onLocationResult(getBestLastKnownLocation(context));
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    // no-op
                }

                @Override
                public void onProviderEnabled(String provider) {
                    // no-op
                }

                @Override
                public void onProviderDisabled(String provider) {
                    // no-op
                }
            };
            lm.requestSingleUpdate(provider, listenerRef[0], Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (delivered[0]) return;
                delivered[0] = true;
                try {
                    lm.removeUpdates(listenerRef[0]);
                } catch (Exception ignored) {
                }
                Log.d(TAG, "fresh_single_update timeout, fallback to last known");
                callback.onLocationResult(getBestLastKnownLocation(context));
            }, CURRENT_LOCATION_TIMEOUT_MS);
        } catch (Exception e) {
            Log.d(TAG, "requestCurrentLocation exception, fallback to null: " + e.getMessage());
            callback.onLocationResult(null);
        }
    }

    /**
     * Some emulators/devices may return a placeholder (0,0) or out-of-range coordinates.
     * Treat those as invalid so the app can fall back to HK default.
     */
    public static boolean isValidLocation(Location loc) {
        return isLocationStructurallyValid(loc);
    }

    public static boolean isValidLocationForDistance(Context context, Location loc, boolean allowFallbackAge) {
        if (!isLocationStructurallyValid(loc)) {
            return false;
        }
        long now = System.currentTimeMillis();
        long ageMs = now - loc.getTime();
        long maxAge = allowFallbackAge ? MAX_FALLBACK_LOCATION_AGE_MS : MAX_FRESH_LOCATION_AGE_MS;
        if (ageMs < 0 || ageMs > maxAge) {
            return false;
        }
        if (!loc.hasAccuracy()) {
            return false;
        }
        float accuracyLimit = hasFineLocationPermission(context)
                ? MAX_FINE_ACCURACY_METERS
                : MAX_COARSE_ACCURACY_METERS;
        if (loc.getAccuracy() <= 0f || loc.getAccuracy() > accuracyLimit) {
            return false;
        }
        return true;
    }

    public static Location getLatestAcceptedLocation(Context context) {
        Location latest = latestAcceptedLocation;
        if (latest == null) return null;
        if (!isValidLocationForDistance(context, latest, true)) {
            return null;
        }
        return new Location(latest);
    }

    public static void clearLatestAcceptedLocation() {
        latestAcceptedLocation = null;
    }

    private static boolean isLocationStructurallyValid(Location loc) {
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
        if (Math.abs(lat) < 1e-6 && Math.abs(lon) < 1e-6) {
            return false;
        }
        return loc.getProvider() != null && !loc.getProvider().trim().isEmpty();
    }

    private static Location pickBetterLocation(Location a, Location b, boolean hasFinePermission, boolean allowFallbackAge) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        boolean aUsable = isValidLocationForDistanceByPermission(a, hasFinePermission, allowFallbackAge);
        boolean bUsable = isValidLocationForDistanceByPermission(b, hasFinePermission, allowFallbackAge);
        if (aUsable && !bUsable) return a;
        if (!aUsable && bUsable) return b;
        if (aUsable && bUsable && a.hasAccuracy() && b.hasAccuracy()) {
            if (Math.abs(a.getAccuracy() - b.getAccuracy()) > 1f) {
                return a.getAccuracy() <= b.getAccuracy() ? a : b;
            }
        }
        return a.getTime() >= b.getTime() ? a : b;
    }

    private static boolean isValidLocationForDistanceByPermission(Location loc, boolean hasFinePermission, boolean allowFallbackAge) {
        if (!isLocationStructurallyValid(loc)) return false;
        long now = System.currentTimeMillis();
        long ageMs = now - loc.getTime();
        long maxAge = allowFallbackAge ? MAX_FALLBACK_LOCATION_AGE_MS : MAX_FRESH_LOCATION_AGE_MS;
        if (ageMs < 0 || ageMs > maxAge) return false;
        if (!loc.hasAccuracy()) return false;
        float limit = hasFinePermission ? MAX_FINE_ACCURACY_METERS : MAX_COARSE_ACCURACY_METERS;
        return loc.getAccuracy() > 0f && loc.getAccuracy() <= limit;
    }

    private static String chooseProvider(LocationManager lm, boolean hasFine) {
        if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        if (lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            return LocationManager.PASSIVE_PROVIDER;
        }
        return null;
    }

    private static void logLocation(String source, Location location, boolean fromLastKnown) {
        if (location == null) {
            Log.d(TAG, source + " result=null, fromLastKnown=" + fromLastKnown);
            return;
        }
        Log.d(TAG, source
                + ", provider=" + location.getProvider()
                + ", lat=" + location.getLatitude()
                + ", lon=" + location.getLongitude()
                + ", accuracy=" + (location.hasAccuracy() ? location.getAccuracy() : -1f)
                + ", time=" + location.getTime()
                + ", fromLastKnown=" + fromLastKnown);
    }

    /**
     * School data is Hong Kong only; emulator/devices may report overseas defaults.
     */
    public static boolean isLikelyHongKong(double lat, double lon) {
        return lat >= 22.10 && lat <= 22.60 && lon >= 113.80 && lon <= 114.50;
    }
}
