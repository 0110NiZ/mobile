package com.example.smartschoolfinder.data;

import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.model.NotificationListResponse;
import com.example.smartschoolfinder.network.ApiCallback;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class NotificationRepository {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public void getNotifications(String recipientDeviceId, ApiCallback<NotificationListResponse> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/notifications?recipientDeviceId=" + encode(recipientDeviceId);
                String body = executeGet(url);
                NotificationListResponse response = gson.fromJson(body, NotificationListResponse.class);
                mainHandler.post(() -> callback.onSuccess(response));
            } catch (Exception e) {
                String msg = e == null ? "" : String.valueOf(e.getMessage());
                if (msg.contains("HTTP 404")) {
                    // Backend may still be running old code; keep UI graceful.
                    NotificationListResponse empty = new NotificationListResponse();
                    mainHandler.post(() -> callback.onSuccess(empty));
                } else {
                    mainHandler.post(() -> callback.onError("Failed to load notifications: " + msg));
                }
            }
        }).start();
    }

    public void markRead(String recipientDeviceId, ApiCallback<Boolean> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/notifications/mark-read";
                JSONObject payload = new JSONObject();
                payload.put("recipientDeviceId", recipientDeviceId == null ? "" : recipientDeviceId.trim());
                executePost(url, payload.toString());
                mainHandler.post(() -> callback.onSuccess(true));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to mark read: " + e.getMessage()));
            }
        }).start();
    }

    private String executeGet(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readFully(stream);
            if (status < 200 || status >= 300) throw new RuntimeException("HTTP " + status + ": " + body);
            return body;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void executePost(String urlString, String json) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = readFully(stream);
            if (status < 200 || status >= 300) throw new RuntimeException("HTTP " + status + ": " + body);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readFully(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        return builder.toString();
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }
}

