package com.example.smartschoolfinder.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.model.Review;
import com.example.smartschoolfinder.model.ReviewListResponse;
import com.example.smartschoolfinder.network.ApiCallback;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ReviewRepository {
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ReviewRepository(Context context) {
    }

    public void getReviews(String schoolId, String sort, String deviceUserId, ApiCallback<ReviewListResponse> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/" + encodePath(schoolId) + buildQuery(sort, deviceUserId);
                String body = executeGet(url);
                ReviewListResponse response = gson.fromJson(body, ReviewListResponse.class);
                mainHandler.post(() -> callback.onSuccess(response));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to load reviews: " + e.getMessage()));
            }
        }).start();
    }

    public void addReview(String schoolId, String deviceUserId, String reviewerName, int rating, String comment, ApiCallback<Review> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews";
                JSONObject payload = new JSONObject();
                payload.put("schoolId", safeTrim(schoolId));
                payload.put("deviceUserId", safeTrim(deviceUserId));
                payload.put("reviewerName", safeTrim(reviewerName));
                payload.put("rating", rating);
                payload.put("comment", safeTrim(comment));

                String body = executePostJson(url, payload.toString());
                JSONObject root = new JSONObject(body);
                Review review = gson.fromJson(root.getJSONObject("review").toString(), Review.class);
                mainHandler.post(() -> callback.onSuccess(review));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to submit review: " + e.getMessage()));
            }
        }).start();
    }

    public void reactToReview(String reviewId, String deviceUserId, String action, ApiCallback<ReactionResult> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/" + encodePath(reviewId) + "/react";
                JSONObject payload = new JSONObject();
                payload.put("deviceUserId", safeTrim(deviceUserId));
                payload.put("action", safeTrim(action));
                String body = executePostJson(url, payload.toString());
                ReactionResult result = gson.fromJson(body, ReactionResult.class);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to react: " + e.getMessage()));
            }
        }).start();
    }

    public void updateReview(String reviewId, String deviceUserId, int rating, String comment, ApiCallback<Review> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/" + encodePath(reviewId);
                JSONObject payload = new JSONObject();
                payload.put("deviceUserId", safeTrim(deviceUserId));
                payload.put("rating", rating);
                payload.put("comment", safeTrim(comment));
                String body = executeJsonWithMethod(url, "PUT", payload.toString());
                JSONObject root = new JSONObject(body);
                Review review = gson.fromJson(root.getJSONObject("review").toString(), Review.class);
                mainHandler.post(() -> callback.onSuccess(review));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to update: " + e.getMessage()));
            }
        }).start();
    }

    public void deleteReview(String reviewId, String deviceUserId, ApiCallback<Boolean> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/" + encodePath(reviewId) + "?deviceUserId=" + encodePath(deviceUserId);
                executeDelete(url);
                mainHandler.post(() -> callback.onSuccess(true));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to delete: " + e.getMessage()));
            }
        }).start();
    }

    public void seedReviews(List<SeedSchool> schools, ApiCallback<SeedResult> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/seed";
                JSONArray arr = new JSONArray();
                for (SeedSchool s : schools) {
                    if (s == null || s.id == null) continue;
                    JSONObject o = new JSONObject();
                    o.put("id", safeTrim(s.id));
                    o.put("name", safeTrim(s.name));
                    arr.put(o);
                }
                JSONObject payload = new JSONObject();
                payload.put("schools", arr);

                String body = executePostJson(url, payload.toString());
                SeedResult result = gson.fromJson(body, SeedResult.class);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to seed reviews: " + e.getMessage()));
            }
        }).start();
    }

    public static class SeedSchool {
        public final String id;
        public final String name;

        public SeedSchool(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class SeedResult {
        public boolean ok;
        public int seededSchools;
        public int toppedUpSchools;
        public int skippedSchools;
    }

    public static class ReactionResult {
        public String reviewId;
        public int likes;
        public int dislikes;
        public String userReaction;
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

    private String executeJsonWithMethod(String urlString, String method, String json) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
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

    private void executeDelete(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
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

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private String buildQuery(String sort, String deviceUserId) {
        String s = safeTrim(sort);
        String d = safeTrim(deviceUserId);
        StringBuilder q = new StringBuilder();
        if (!s.isEmpty()) {
            q.append(q.length() == 0 ? "?" : "&").append("sort=").append(encodePath(s));
        }
        if (!d.isEmpty()) {
            q.append(q.length() == 0 ? "?" : "&").append("deviceUserId=").append(encodePath(d));
        }
        return q.toString();
    }

    private String encodePath(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }
}
