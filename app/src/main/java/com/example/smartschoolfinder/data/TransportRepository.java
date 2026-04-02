package com.example.smartschoolfinder.data;

import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.model.TransportInfo;
import com.example.smartschoolfinder.network.ApiCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TransportRepository {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void getSchoolTransport(String schoolId, ApiCallback<TransportInfo> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.TRANSPORT_API_BASE_URL
                        + "api/schools/" + encodePath(schoolId) + "/transport";
                String body = executeGet(url);
                JSONObject root = new JSONObject(body);
                TransportInfo info = parseToTransportInfo(root);
                mainHandler.post(() -> callback.onSuccess(info));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to load transport: " + e.getMessage()));
            }
        }).start();
    }

    private TransportInfo parseToTransportInfo(JSONObject root) {
        JSONObject mtr = root.optJSONObject("mtr");
        JSONObject bus = root.optJSONObject("bus");
        JSONObject minibus = root.optJSONObject("minibus");

        String mtrName = mtr == null ? "N/A" : mtr.optString("name", "N/A");
        String mtrDistance = mtr == null ? "N/A" : distanceToText(mtr.optInt("distance", -1));

        String busName = bus == null ? "N/A" : bus.optString("name", "N/A");
        String busDistance = bus == null ? "N/A" : distanceToText(bus.optInt("distance", -1));
        String minibusName = minibus == null ? "N/A" : minibus.optString("name", "N/A");
        String minibusDistance = minibus == null ? "N/A" : distanceToText(minibus.optInt("distance", -1));

        String stars = scoreFromNearest(
                mtr == null ? -1 : mtr.optInt("distance", -1),
                bus == null ? -1 : bus.optInt("distance", -1),
                minibus == null ? -1 : minibus.optInt("distance", -1)
        );

        return new TransportInfo(
                mtrName, mtrDistance,
                busName, busDistance,
                minibusName, minibusDistance,
                stars
        );
    }

    private String distanceToText(int meters) {
        return meters >= 0 ? meters + "m" : "N/A";
    }

    private String scoreFromNearest(int... distances) {
        int nearest = Integer.MAX_VALUE;
        for (int d : distances) {
            if (d >= 0 && d < nearest) nearest = d;
        }
        if (nearest == Integer.MAX_VALUE) return "N/A";
        if (nearest <= 300) return "⭐⭐⭐⭐⭐";
        if (nearest <= 500) return "⭐⭐⭐⭐";
        if (nearest <= 800) return "⭐⭐⭐";
        return "⭐⭐";
    }

    private String executeGet(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readFully(stream);
            if (status < 200 || status >= 300) {
                throw new RuntimeException("HTTP " + status + ": " + body);
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readFully(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private String encodePath(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }
}

