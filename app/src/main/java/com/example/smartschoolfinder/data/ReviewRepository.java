package com.example.smartschoolfinder.data;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.R;
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
    private static final String TAG = "REVIEW_REPO";
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context appContext;

    public ReviewRepository(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    public void getReviews(String schoolId, String sort, String deviceUserId, ApiCallback<ReviewListResponse> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/" + encodePath(schoolId) + buildQuery(sort, deviceUserId);
                Log.d(TAG, "GET reviews url=" + url);
                String body = executeGet(url);
                ReviewListResponse response = gson.fromJson(body, ReviewListResponse.class);
                mainHandler.post(() -> callback.onSuccess(response));
            } catch (Exception e) {
                Log.e(TAG, "GET reviews failed", e);
                mainHandler.post(() -> callback.onError(localizedReviewServiceError()));
            }
        }).start();
    }

    public void addReview(String schoolId, String deviceUserId, String reviewerName, int rating, String comment, ApiCallback<Review> callback) {
        addReview(schoolId, deviceUserId, reviewerName, rating, comment, null, callback);
    }

    public void addReview(String schoolId, String deviceUserId, String reviewerName, int rating, String comment, String parentId, ApiCallback<Review> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews";
                Log.d(TAG, "POST review url=" + url);
                JSONObject payload = new JSONObject();
                payload.put("schoolId", safeTrim(schoolId));
                payload.put("deviceUserId", safeTrim(deviceUserId));
                payload.put("reviewerName", safeTrim(reviewerName));
                payload.put("rating", rating);
                payload.put("comment", safeTrim(comment));
                if (parentId != null && !safeTrim(parentId).isEmpty()) {
                    payload.put("parentId", safeTrim(parentId));
                }

                String body = executePostJson(url, payload.toString());
                JSONObject root = new JSONObject(body);
                Review review = gson.fromJson(root.getJSONObject("review").toString(), Review.class);
                mainHandler.post(() -> callback.onSuccess(review));
            } catch (Exception e) {
                Log.e(TAG, "POST review failed", e);
                mainHandler.post(() -> callback.onError(localizedReviewServiceError()));
            }
        }).start();
    }

    public void reactToReview(String reviewId, String deviceUserId, String action, ApiCallback<ReactionResult> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/" + encodePath(reviewId) + "/react";
                Log.d(TAG, "POST react url=" + url);
                JSONObject payload = new JSONObject();
                payload.put("deviceUserId", safeTrim(deviceUserId));
                payload.put("action", safeTrim(action));
                String body = executePostJson(url, payload.toString());
                ReactionResult result = gson.fromJson(body, ReactionResult.class);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "POST react failed", e);
                mainHandler.post(() -> callback.onError(localizedReviewServiceError()));
            }
        }).start();
    }

    public void updateReview(String reviewId, String deviceUserId, int rating, String comment, ApiCallback<Review> callback) {
        updateReview(reviewId, deviceUserId, null, rating, comment, callback);
    }

    public void updateReview(String reviewId, String deviceUserId, String reviewerName, int rating, String comment, ApiCallback<Review> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/" + encodePath(reviewId);
                Log.d(TAG, "PUT review url=" + url);
                JSONObject payload = new JSONObject();
                payload.put("deviceUserId", safeTrim(deviceUserId));
                if (reviewerName != null && !safeTrim(reviewerName).isEmpty()) {
                    payload.put("reviewerName", safeTrim(reviewerName));
                }
                payload.put("rating", rating);
                payload.put("comment", safeTrim(comment));
                String body = executeJsonWithMethod(url, "PUT", payload.toString());
                JSONObject root = new JSONObject(body);
                Review review = gson.fromJson(root.getJSONObject("review").toString(), Review.class);
                mainHandler.post(() -> callback.onSuccess(review));
            } catch (Exception e) {
                Log.e(TAG, "PUT review failed", e);
                mainHandler.post(() -> callback.onError(localizedReviewServiceError()));
            }
        }).start();
    }

    public void deleteReview(String reviewId, String deviceUserId, ApiCallback<Boolean> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/" + encodePath(reviewId) + "?deviceUserId=" + encodePath(deviceUserId);
                Log.d(TAG, "DELETE review url=" + url);
                executeDelete(url);
                mainHandler.post(() -> callback.onSuccess(true));
            } catch (Exception e) {
                Log.e(TAG, "DELETE review failed", e);
                mainHandler.post(() -> callback.onError(localizedReviewServiceError()));
            }
        }).start();
    }

    public void seedReviews(List<SeedSchool> schools, ApiCallback<SeedResult> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.REVIEW_API_BASE_URL + "api/reviews/seed";
                Log.d(TAG, "POST seed reviews url=" + url);
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
                Log.e(TAG, "POST seed reviews failed", e);
                mainHandler.post(() -> callback.onError(localizedReviewServiceError()));
            }
        }).start();
    }

    private String localizedReviewServiceError() {
        if (appContext == null) {
            return "Unable to connect to review service.";
        }
        return appContext.getString(R.string.review_service_unavailable);
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
