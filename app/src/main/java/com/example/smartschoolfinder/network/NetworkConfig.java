package com.example.smartschoolfinder.network;

import android.os.Build;
import android.util.Log;

import com.example.smartschoolfinder.BuildConfig;

import java.util.Locale;

public final class NetworkConfig {
    private static final String TAG = "NETWORK_CONFIG";
    private static final String EMULATOR_HOST = "10.0.2.2:3000";

    private NetworkConfig() {
    }

    public static String getBackendBaseUrl() {
        String host = isEmulator() ? EMULATOR_HOST : normalizeHost(BuildConfig.BACKEND_HOST);
        String base = "http://" + host + "/";
        Log.d(TAG, "backend base url = " + base + ", emulator=" + isEmulator());
        return base;
    }

    private static String normalizeHost(String raw) {
        String host = raw == null ? "" : raw.trim();
        if (host.isEmpty()) {
            host = "192.168.1.100:3000";
        }
        host = host.replace("http://", "").replace("https://", "");
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    private static boolean isEmulator() {
        String fingerprint = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT.toLowerCase(Locale.ROOT);
        String model = Build.MODEL == null ? "" : Build.MODEL.toLowerCase(Locale.ROOT);
        String product = Build.PRODUCT == null ? "" : Build.PRODUCT.toLowerCase(Locale.ROOT);
        return fingerprint.contains("generic")
                || fingerprint.contains("emulator")
                || model.contains("emulator")
                || model.contains("sdk")
                || product.contains("sdk")
                || product.contains("emulator")
                || "google_sdk".equals(product);
    }
}
