package com.example.smartschoolfinder.network;

import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.model.School;

import android.util.Log;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApiClient {
    // TEMP DEBUG (requested): per-source statistics
    private static final boolean ENABLE_SOURCE_DEBUG = true;
    private String currentSourceUrl = null;
    private Map<String, Integer> currentTypeKeyHits = null;
    private Map<String, Integer> currentDistrictKeyHits = null;

    public void fetchSchools(ApiCallback<List<School>> callback, boolean preferChineseContent) {
        new Thread(() -> {
            try {
                List<School> schools = fetchAndMergeAllSources(preferChineseContent);
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(schools));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Failed to load schools: " + e.getMessage()));
            }
        }).start();
    }

    private List<School> fetchAndMergeAllSources(boolean preferChineseContent) throws Exception {
        List<School> allSchools = new ArrayList<>();
        Set<String> dedupeKeys = new HashSet<>();

        for (String sourceUrl : AppConstants.SCHOOL_API_URLS) {
            String body = executeGet(sourceUrl);
            int mergedBefore = allSchools.size();
            int dedupeBefore = dedupeKeys.size();

            // Prepare per-source debug aggregators (no business logic changes)
            currentSourceUrl = sourceUrl;
            currentTypeKeyHits = new LinkedHashMap<>();
            currentDistrictKeyHits = new LinkedHashMap<>();

            List<School> sourceSchools = parseSchoolsFromResponse(body, preferChineseContent);

            if (ENABLE_SOURCE_DEBUG) {
                logSourceStats(sourceUrl, sourceSchools, currentDistrictKeyHits, currentTypeKeyHits);
            }

            // Merge all sources with dedupe; never overwrite previously loaded schools.
            for (School school : sourceSchools) {
                String dedupeKey = buildDedupeKey(school);
                if (dedupeKeys.add(dedupeKey)) {
                    allSchools.add(school);
                }
            }

            if (ENABLE_SOURCE_DEBUG) {
                Log.d("SOURCE_DEBUG", "url=" + sourceUrl);
                Log.d("SOURCE_DEBUG", "merge: before=" + mergedBefore + " after=" + allSchools.size()
                        + " (added=" + (allSchools.size() - mergedBefore) + ")");
                Log.d("SOURCE_DEBUG", "dedupeKeys: before=" + dedupeBefore + " after=" + dedupeKeys.size()
                        + " (newKeys=" + (dedupeKeys.size() - dedupeBefore) + ")");
            }

            currentSourceUrl = null;
            currentTypeKeyHits = null;
            currentDistrictKeyHits = null;
        }

        // TEMP DEBUG: print unique type values and counts (no business logic changes)
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (School s : allSchools) {
            if (s == null) continue;
            String t = s.getType();
            if (t == null) t = "";
            t = t.trim();
            if (t.isEmpty()) t = "(empty)";
            typeCounts.put(t, (typeCounts.containsKey(t) ? typeCounts.get(t) : 0) + 1);
        }
        Log.d("TYPE_DEBUG", "unique types = " + typeCounts.keySet());
        for (Map.Entry<String, Integer> e : typeCounts.entrySet()) {
            Log.d("TYPE_DEBUG", e.getKey() + " = " + e.getValue());
        }

        return allSchools;
    }

    private void logSourceStats(String sourceUrl, List<School> sourceSchools,
                                Map<String, Integer> districtKeyHits, Map<String, Integer> typeKeyHits) {
        int count = sourceSchools == null ? 0 : sourceSchools.size();
        Log.d("SOURCE_DEBUG", "url=" + sourceUrl);
        Log.d("SOURCE_DEBUG", "parsed school count=" + count);

        Map<String, Integer> districtCounts = new LinkedHashMap<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        if (sourceSchools != null) {
            for (School s : sourceSchools) {
                if (s == null) continue;
                String d = s.getDistrict() == null ? "" : s.getDistrict().trim();
                if (d.isEmpty()) d = "(empty)";
                districtCounts.put(d, (districtCounts.containsKey(d) ? districtCounts.get(d) : 0) + 1);

                String t = s.getType() == null ? "" : s.getType().trim();
                if (t.isEmpty()) t = "(empty)";
                typeCounts.put(t, (typeCounts.containsKey(t) ? typeCounts.get(t) : 0) + 1);
            }
        }

        Log.d("SOURCE_DEBUG", "district values=" + districtCounts.keySet());
        Log.d("SOURCE_DEBUG", "type values=" + typeCounts.keySet());
        for (Map.Entry<String, Integer> e : districtCounts.entrySet()) {
            Log.d("SOURCE_DEBUG", "district " + e.getKey() + " = " + e.getValue());
        }
        for (Map.Entry<String, Integer> e : typeCounts.entrySet()) {
            Log.d("SOURCE_DEBUG", "type " + e.getKey() + " = " + e.getValue());
        }

        if (districtKeyHits != null) {
            Log.d("SOURCE_DEBUG", "district key hits=" + districtKeyHits);
        }
        if (typeKeyHits != null) {
            Log.d("SOURCE_DEBUG", "type key hits=" + typeKeyHits);
        }
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

    private List<School> parseSchoolsFromResponse(String responseBody, boolean preferChineseContent) {
        List<School> result = new ArrayList<>();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return result;
        }

        try {
            // Support both direct-array and wrapped-object responses.
            if (responseBody.trim().startsWith("[")) {
                parseSchoolArray(new JSONArray(responseBody), result, preferChineseContent);
            } else {
                JSONObject root = new JSONObject(responseBody);
                JSONArray array = pickSchoolArray(root);
                parseSchoolArray(array, result, preferChineseContent);
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

    private void parseSchoolArray(JSONArray array, List<School> out, boolean preferChineseContent) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            out.add(parseSchool(item, preferChineseContent)); // Traverse full array; no partial parsing.
        }
    }

    private School parseSchool(JSONObject item, boolean preferChineseContent) {
        // Support EDB official keys and common API keys. EDB JSON includes both ENGLISH_* and 中文*; order follows UI language.
        String schoolCode = firstNonEmpty(item, "SCHOOL CODE", "School Code", "SCH_CODE", "school_code", "schoolCode", "sch_code");
        String id = firstNonEmpty(item, "SCHOOL NO.", "school_no", "school_id", "id", "code");
        String name;
        String district;
        String type;
        String address;
        if (preferChineseContent) {
            name = firstNonEmpty(item, "中文名稱", "�������Q", "ENGLISH NAME", "SCHOOL NAME", "school_name", "name");
            district = firstNonEmpty(item, "分區", "�օ^", "DISTRICT", "district", "region");
            type = firstNonEmpty(item, "學校類型", "中文類別", "����e", "�WУ���", "SCHOOL LEVEL", "ENGLISH CATEGORY", "school_type", "type");
            address = firstNonEmpty(item, "中文地址", "���ĵ�ַ", "ENGLISH ADDRESS", "ADDRESS", "school_address", "address");
        } else {
            name = firstNonEmpty(item, "ENGLISH NAME", "SCHOOL NAME", "school_name", "name", "中文名稱", "�������Q");
            district = firstNonEmpty(item, "DISTRICT", "分區", "�օ^", "district", "region");
            type = firstNonEmpty(item, "SCHOOL LEVEL", "ENGLISH CATEGORY", "school_type", "type", "學校類型", "中文類別", "����e", "�WУ���");
            address = firstNonEmpty(item, "ENGLISH ADDRESS", "ADDRESS", "school_address", "address", "中文地址", "���ĵ�ַ");
        }

        if (ENABLE_SOURCE_DEBUG && currentSourceUrl != null) {
            if (preferChineseContent) {
                bumpKeyHit(currentDistrictKeyHits, item, "分區", "�օ^", "DISTRICT", "district", "region");
                bumpKeyHit(currentTypeKeyHits, item, "學校類型", "中文類別", "����e", "�WУ���", "SCHOOL LEVEL", "ENGLISH CATEGORY", "school_type", "type");
            } else {
                bumpKeyHit(currentDistrictKeyHits, item, "DISTRICT", "分區", "�օ^", "district", "region");
                bumpKeyHit(currentTypeKeyHits, item, "SCHOOL LEVEL", "ENGLISH CATEGORY", "school_type", "type", "學校類型", "中文類別", "����e", "�WУ���");
            }
        }
        String phone = firstNonEmpty(item, "TELEPHONE", "聯絡電話", "tel", "phone", "telephone");
        String tuition = firstNonEmpty(item, "TUITION", "fee", "tuition");
        String bus = firstNonEmpty(item, "BUS", "transport_bus", "bus");
        String minibus = firstNonEmpty(item, "MINIBUS", "transport_minibus", "minibus");
        String mtr = firstNonEmpty(item, "MTR", "transport_mtr", "mtr");
        String convenience = firstNonEmpty(item, "TRANSPORT CONVENIENCE", "transport_convenience", "transport_score");
        double latitude = optCoordinate(item, "LATITUDE", "latitude", "lat");
        double longitude = optCoordinate(item, "LONGITUDE", "longitude", "lng", "lon");

        // Fallback values to keep UI stable if API has missing fields.
        if (id == null || id.trim().isEmpty()) {
            id = name;
        }
        if (schoolCode == null) schoolCode = "";
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

        return new School(schoolCode, id, name, district, type, address, phone, tuition, bus, minibus, mtr, convenience, latitude, longitude);
    }

    private void bumpKeyHit(Map<String, Integer> hits, JSONObject item, String... keys) {
        if (hits == null || item == null || keys == null) return;
        for (String key : keys) {
            String value = item.optString(key, "").trim();
            if (!value.isEmpty()) {
                hits.put(key, (hits.containsKey(key) ? hits.get(key) : 0) + 1);
                return;
            }
        }
        hits.put("(none)", (hits.containsKey("(none)") ? hits.get("(none)") : 0) + 1);
    }

    private String buildDedupeKey(School school) {
        String code = safe(school.getSchoolCode());
        if (!code.isEmpty()) {
            return "CODE:" + code;
        }
        // Requirement: if school code doesn't exist, dedupe by name + address.
        String na = safe(school.getName()) + "|" + safe(school.getAddress());
        if (!na.equals("|")) {
            return "NA:" + na;
        }
        // Last resort only: id.
        String id = safe(school.getId());
        return "ID:" + id;
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

    private double optCoordinate(JSONObject item, String... keys) {
        for (String key : keys) {
            if (item.has(key)) {
                Object raw = item.opt(key);
                if (raw == null || raw == JSONObject.NULL) {
                    continue;
                }
                if (raw instanceof Number) {
                    double value = ((Number) raw).doubleValue();
                    if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                        return value;
                    }
                    continue;
                }

                String text = String.valueOf(raw).trim();
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    double value = Double.parseDouble(text);
                    if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                        return value;
                    }
                } catch (NumberFormatException ignored) {
                    // Try next candidate key.
                }
            }
        }
        return Double.NaN;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
