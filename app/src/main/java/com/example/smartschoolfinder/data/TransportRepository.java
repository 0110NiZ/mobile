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

    public void getNearbyTransport(double latitude, double longitude, ApiCallback<TransportInfo> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.TRANSPORT_API_BASE_URL
                        + "api/transport/nearby?lat=" + encode(latitude) + "&lng=" + encode(longitude) + "&radius=500";
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
        JSONArray bus = root.optJSONArray("bus");
        JSONArray minibus = root.optJSONArray("minibus");
        String stars = root.optString("convenienceScore", "⭐⭐⭐");

        String mtrName = mtr == null ? "N/A" : mtr.optString("name", "N/A");
        String mtrDistance = mtr == null ? "N/A" : mtr.optString("distanceText", "N/A");

        String busName = joinTopNames(bus, 3);
        String busDistance = firstDistance(bus);
        String minibusName = joinTopNames(minibus, 3);
        String minibusDistance = firstDistance(minibus);

        return new TransportInfo(
                mtrName, mtrDistance,
                busName, busDistance,
                minibusName, minibusDistance,
                stars
        );
    }

    private String joinTopNames(JSONArray arr, int max) {
        if (arr == null || arr.length() == 0) return "N/A";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(max, arr.length());
        for (int i = 0; i < n; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "").trim();
            if (name.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(name);
        }
        return sb.length() == 0 ? "N/A" : sb.toString();
    }

    private String firstDistance(JSONArray arr) {
        if (arr == null || arr.length() == 0) return "N/A";
        JSONObject first = arr.optJSONObject(0);
        if (first == null) return "N/A";
        String d = first.optString("distanceText", "").trim();
        return d.isEmpty() ? "N/A" : d;
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

    private String encode(double value) {
        return String.valueOf(value);
    }
}

