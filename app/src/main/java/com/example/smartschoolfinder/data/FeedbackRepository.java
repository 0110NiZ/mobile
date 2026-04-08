package com.example.smartschoolfinder.data;

import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.network.ApiCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FeedbackRepository {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void submitFeedback(int rating, String comment, ApiCallback<Boolean> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/feedback";
                JSONObject payload = new JSONObject();
                payload.put("rating", rating);
                payload.put("comment", comment == null ? "" : comment.trim());
                executePostJson(url, payload.toString());
                mainHandler.post(() -> callback.onSuccess(true));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to submit feedback: " + e.getMessage()));
            }
        }).start();
    }

    private String executePostJson(String urlString, String json) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
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
}

