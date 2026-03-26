package com.example.smartschoolfinder.network;

import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.model.School;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApiClient {

    public void fetchSchools(ApiCallback<List<School>> callback) {
        new Thread(() -> {
            try {
                List<School> schools = fetchAndMergeAllSources();
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(schools));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Failed to load schools: " + e.getMessage()));
            }
        }).start();
    }

    private List<School> fetchAndMergeAllSources() throws Exception {
        List<School> allSchools = new ArrayList<>();
        Set<String> dedupeKeys = new HashSet<>();

        for (String sourceUrl : AppConstants.SCHOOL_API_URLS) {
            String body = executeGet(sourceUrl);
            List<School> sourceSchools = parseSchoolsFromResponse(body);

            // Merge all sources with dedupe; never overwrite previously loaded schools.
            for (School school : sourceSchools) {
                String dedupeKey = buildDedupeKey(school);
                if (dedupeKeys.add(dedupeKey)) {
                    allSchools.add(school);
                }
            }
        }

        return allSchools;
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
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private List<School> parseSchoolsFromResponse(String responseBody) {
        List<School> result = new ArrayList<>();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return result;
        }

        try {
            // Support both direct-array and wrapped-object responses.
            if (responseBody.trim().startsWith("[")) {
                parseSchoolArray(new JSONArray(responseBody), result);
            } else {
                JSONObject root = new JSONObject(responseBody);
                JSONArray array = pickSchoolArray(root);
                parseSchoolArray(array, result);
            }
        } catch (Exception ignored) {
            // Keep student project robust: return parsed data so far instead of crashing.
        }
        return result;
    }

    private JSONArray pickSchoolArray(JSONObject root) {
        if (root.has("schools")) {
            return root.optJSONArray("schools");
        }
        if (root.has("data")) {
            return root.optJSONArray("data");
        }
        if (root.has("items")) {
            return root.optJSONArray("items");
        }
        if (root.has("results")) {
            return root.optJSONArray("results");
        }
        if (root.has("Result")) {
            return root.optJSONArray("Result");
        }
        if (root.has("Records")) {
            return root.optJSONArray("Records");
        }
        if (root.has("records")) {
            return root.optJSONArray("records");
        }
        return new JSONArray();
    }

    private void parseSchoolArray(JSONArray array, List<School> out) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            out.add(parseSchool(item)); // Traverse full array; no partial parsing.
        }
    }

    private School parseSchool(JSONObject item) {
        // Support EDB official keys and common API keys.
        String id = firstNonEmpty(item, "SCHOOL NO.", "school_no", "school_id", "id", "code");
        String name = firstNonEmpty(item, "ENGLISH NAME", "SCHOOL NAME", "school_name", "name", "中文名稱");
        String district = firstNonEmpty(item, "DISTRICT", "分區", "district", "region");
        String type = firstNonEmpty(item, "SCHOOL LEVEL", "ENGLISH CATEGORY", "school_type", "type", "學校類型");
        String address = firstNonEmpty(item, "ENGLISH ADDRESS", "ADDRESS", "school_address", "address", "中文地址");
        String phone = firstNonEmpty(item, "TELEPHONE", "聯絡電話", "tel", "phone", "telephone");
        String tuition = firstNonEmpty(item, "TUITION", "fee", "tuition");
        String bus = firstNonEmpty(item, "BUS", "transport_bus", "bus");
        String minibus = firstNonEmpty(item, "MINIBUS", "transport_minibus", "minibus");
        String mtr = firstNonEmpty(item, "MTR", "transport_mtr", "mtr");
        String convenience = firstNonEmpty(item, "TRANSPORT CONVENIENCE", "transport_convenience", "transport_score");
        double latitude = optDouble(item, "LATITUDE", "latitude", "lat");
        double longitude = optDouble(item, "LONGITUDE", "longitude", "lng", "lon");

        // Fallback values to keep UI stable if API has missing fields.
        if (id == null || id.trim().isEmpty()) {
            id = name;
        }
        if (name == null) name = "";
        if (district == null) district = "";
        if (type == null) type = "";
        if (address == null) address = "";
        if (phone == null) phone = "";
        if (tuition == null) tuition = "N/A";
        if (bus == null) bus = "N/A";
        if (minibus == null) minibus = "N/A";
        if (mtr == null) mtr = "N/A";
        if (convenience == null) convenience = "N/A";

        return new School(id, name, district, type, address, phone, tuition, bus, minibus, mtr, convenience, latitude, longitude);
    }

    private String buildDedupeKey(School school) {
        String id = safe(school.getId());
        if (!id.isEmpty()) {
            return "ID:" + id;
        }
        // Requirement: fallback dedupe by name + address.
        return "NA:" + safe(school.getName()) + "|" + safe(school.getAddress());
    }

    private String firstNonEmpty(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private double optDouble(JSONObject item, String... keys) {
        for (String key : keys) {
            if (item.has(key)) {
                return item.optDouble(key, 0d);
            }
        }
        return 0d;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
