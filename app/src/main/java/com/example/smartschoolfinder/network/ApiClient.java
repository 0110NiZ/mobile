package com.example.smartschoolfinder.network;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.Locale;

public class ApiClient {
    // TEMP DEBUG (requested): per-source statistics
    private static final boolean ENABLE_SOURCE_DEBUG = true;
    private static final String COUNT_DEBUG_TAG = "COUNT_DEBUG";
    private static final String DEDUP_DEBUG_TAG = "DEDUP_DEBUG";
    private static final String NAME_DEBUG_TAG = "NAME_DEBUG";
    private static final String TRANSLATE_DEBUG_TAG = "TRANSLATE_DEBUG";
    private static final String PREFS_NAME = "smart_school_prefs";
    private static final String KEY_NAME_TRANSLATION_CACHE = "name_translation_cache_v1";
    private static final String SCHOOL_NAME_MAP_ASSET = "school_name_zh_map.csv";
    private static final String SCHOOL_RELIGION_MAP_ASSET = "SCH_LOC_EDB.csv";
    private static final int TRANSLATE_BATCH_SIZE = 40;
    private static final int MAP_DEBUG_SAMPLE_LIMIT = 40;
    private static final int NAME_DEBUG_SAMPLE_LIMIT = 30;
    // Performance safeguard: disable runtime online translation to avoid UI stalls/429.
    private static final boolean ENABLE_ONLINE_NAME_TRANSLATION = false;
    // In-memory cache to avoid re-fetching/re-parsing on language switches.
    private static final Object SCHOOL_CACHE_LOCK = new Object();
    private static List<School> cachedMasterSchools = null;
    private int nameDebugCount = 0;
    private String currentSourceUrl = null;
    private Map<String, Integer> currentTypeKeyHits = null;
    private Map<String, Integer> currentDistrictKeyHits = null;

    public void fetchSchools(Context context, ApiCallback<List<School>> callback, boolean preferChineseContent) {
        new Thread(() -> {
            try {
                List<School> snapshot = getCachedSchoolsSnapshot();
                if (snapshot != null && !snapshot.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(snapshot));
                    return;
                }

                List<School> schools = fetchAndMergeAllSources(preferChineseContent);
                applyReligionFromAssetMap(context, schools);
                // Always apply name map once so later language switch can use cached Chinese names.
                applyChineseNameFromAssetMap(context, schools);
                if (preferChineseContent) {
                    if (ENABLE_ONLINE_NAME_TRANSLATION) {
                        fillMissingChineseNamesByTranslation(context, schools);
                    } else {
                        Log.d(TRANSLATE_DEBUG_TAG, "online name translation disabled for performance");
                    }
                }
                setCachedMasterSchools(schools);
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(schools));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Failed to load schools: " + e.getMessage()));
            }
        }).start();
    }

    private List<School> fetchAndMergeAllSources(boolean preferChineseContent) throws Exception {
        List<School> mergedRawSchools = new ArrayList<>();
        int totalParsedBeforeDedupe = 0;
        int sourceIndex = 0;

        for (String sourceUrl : AppConstants.SCHOOL_API_URLS) {
            sourceIndex++;
            String body = executeGet(sourceUrl);

            // Prepare per-source debug aggregators (no business logic changes)
            currentSourceUrl = sourceUrl;
            currentTypeKeyHits = new LinkedHashMap<>();
            currentDistrictKeyHits = new LinkedHashMap<>();

            List<School> sourceSchools = parseSchoolsFromResponse(body, preferChineseContent);
            totalParsedBeforeDedupe += sourceSchools.size();
            mergedRawSchools.addAll(sourceSchools);
            Log.d(DEDUP_DEBUG_TAG, "source" + sourceIndex + " size = " + sourceSchools.size() + ", url = " + sourceUrl);

            if (ENABLE_SOURCE_DEBUG) {
                logSourceStats(sourceUrl, sourceSchools, currentDistrictKeyHits, currentTypeKeyHits);
            }

            currentSourceUrl = null;
            currentTypeKeyHits = null;
            currentDistrictKeyHits = null;
        }

        Log.d(DEDUP_DEBUG_TAG, "merged raw size = " + mergedRawSchools.size());
        List<School> allSchools = dedupeMergedSchools(mergedRawSchools);
        Log.d(DEDUP_DEBUG_TAG, "deduped size = " + allSchools.size());

        Log.d(COUNT_DEBUG_TAG, "fetch complete = " + totalParsedBeforeDedupe);
        Log.d(COUNT_DEBUG_TAG, "before dedupe = " + totalParsedBeforeDedupe);
        Log.d(COUNT_DEBUG_TAG, "deduped = " + allSchools.size());
        Log.d(COUNT_DEBUG_TAG, "english master list = " + allSchools.size());

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

    private List<School> dedupeMergedSchools(List<School> mergedRawSchools) {
        List<School> safeSource = mergedRawSchools == null ? new ArrayList<>() : mergedRawSchools;
        // Stage 1: stable-key dedupe (code/id/name+address fallback).
        Set<String> stableKeys = new HashSet<>();
        List<School> stableUnique = new ArrayList<>();
        for (School school : safeSource) {
            if (school == null) continue;
            String key = buildDedupeKey(school);
            if (stableKeys.add(key)) {
                stableUnique.add(school);
            }
        }

        // Stage 2: collapse same real school (same normalized name+address),
        // because official dataset may contain multiple session/category rows.
        Set<String> entityKeys = new HashSet<>();
        List<School> deduped = new ArrayList<>();
        for (School school : stableUnique) {
            if (school == null) continue;
            String entityKey = buildEntityKey(school);
            if (entityKeys.add(entityKey)) {
                deduped.add(school);
            }
        }

        logDuplicateDiagnostics(safeSource);
        return deduped;
    }

    private void logDuplicateDiagnostics(List<School> source) {
        Map<String, Integer> keyCounts = new LinkedHashMap<>();
        Map<String, Set<String>> keyToPrimaryKeys = new LinkedHashMap<>();
        Map<String, String> keyToDisplayName = new LinkedHashMap<>();
        for (School school : source) {
            if (school == null) continue;
            String entityKey = buildEntityKey(school);
            String primaryKey = buildDedupeKey(school);
            keyCounts.put(entityKey, keyCounts.containsKey(entityKey) ? keyCounts.get(entityKey) + 1 : 1);
            Set<String> keys = keyToPrimaryKeys.get(entityKey);
            if (keys == null) {
                keys = new HashSet<>();
                keyToPrimaryKeys.put(entityKey, keys);
            }
            keys.add(primaryKey);
            if (!keyToDisplayName.containsKey(entityKey)) {
                String name = school.getName() == null ? "" : school.getName().trim();
                keyToDisplayName.put(entityKey, name.isEmpty() ? "(empty)" : name);
            }
        }
        int duplicateEntityCount = 0;
        for (Map.Entry<String, Integer> e : keyCounts.entrySet()) {
            if (e.getValue() <= 1) continue;
            duplicateEntityCount++;
            String entityKey = e.getKey();
            String displayName = keyToDisplayName.containsKey(entityKey) ? keyToDisplayName.get(entityKey) : "(empty)";
            Log.d(DEDUP_DEBUG_TAG, "duplicate school = " + displayName + ", count = " + e.getValue());
            Set<String> keys = keyToPrimaryKeys.get(entityKey);
            if (keys != null) {
                for (String key : keys) {
                    Log.d(DEDUP_DEBUG_TAG, "key = " + key);
                }
            }
        }
        Log.d(DEDUP_DEBUG_TAG, "duplicate entity groups = " + duplicateEntityCount);
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
        String rawStableId = firstNonEmpty(item, "SCHOOL NO.", "school_no", "school_id", "id", "code");
        FieldPick chineseNamePick = firstNonEmptyWithKey(item, "中文名稱", "CHINESE NAME", "SCHOOL NAME CHI", "chineseName", "chiName", "school_name_chi", "�������Q");
        if (isEmpty(chineseNamePick.value)) {
            FieldPick fallback = findChineseNameHeuristic(item);
            if (!isEmpty(fallback.value)) {
                chineseNamePick = fallback;
            }
        }
        String chineseName = chineseNamePick.value;
        String name = firstNonEmpty(item, "ENGLISH NAME", "SCHOOL NAME", "school_name", "name", "中文名稱", "�������Q");
        String district = firstNonEmpty(item, "DISTRICT", "district", "region", "分區", "�օ^");
        String type = firstNonEmpty(item, "SCHOOL LEVEL", "ENGLISH CATEGORY", "school_type", "type", "學校類型", "中文類別", "����e", "�WУ���");
        String gender = firstNonEmpty(item, "STUDENTS GENDER", "gender", "GENDER", "sex", "SEX");
        String chineseAddress = firstNonEmpty(item, "中文地址", "CHINESE ADDRESS", "chineseAddress", "chiAddress", "���ĵ�ַ");
        String address = firstNonEmpty(item, "ENGLISH ADDRESS", "ADDRESS", "school_address", "address", "中文地址", "���ĵ�ַ");

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
        String religion = firstNonEmpty(item, "RELIGION", "religion");
        String chineseReligion = firstNonEmpty(item, "宗教");
        String bus = firstNonEmpty(item, "BUS", "transport_bus", "bus");
        String minibus = firstNonEmpty(item, "MINIBUS", "transport_minibus", "minibus");
        String mtr = firstNonEmpty(item, "MTR", "transport_mtr", "mtr");
        String convenience = firstNonEmpty(item, "TRANSPORT CONVENIENCE", "transport_convenience", "transport_score");
        double latitude = optCoordinate(item, "LATITUDE", "latitude", "lat");
        double longitude = optCoordinate(item, "LONGITUDE", "longitude", "lng", "lon");

        // Fallback values to keep UI stable if API has missing fields.
        String id = rawStableId;
        if (id == null || id.trim().isEmpty()) {
            id = name;
        }
        if (schoolCode == null) schoolCode = "";
        if (name == null) name = "";
        if (chineseName == null) chineseName = "";
        if (district == null || district.trim().isEmpty()) district = "Unknown";
        if (type == null) type = "";
        if (gender == null) gender = "";
        if (chineseAddress == null) chineseAddress = "";
        if (address == null) address = "";
        if (phone == null) phone = "";
        if (tuition == null) tuition = "N/A";
        if (religion == null) religion = "";
        if (chineseReligion == null) chineseReligion = "";
        if (bus == null) bus = "N/A";
        if (minibus == null) minibus = "N/A";
        if (mtr == null) mtr = "N/A";
        if (convenience == null) convenience = "N/A";

        if (nameDebugCount < NAME_DEBUG_SAMPLE_LIMIT) {
            String locale = preferChineseContent ? "zh" : "en";
            Log.d(NAME_DEBUG_TAG, "field=" + (chineseNamePick.key == null ? "(none)" : chineseNamePick.key));
            Log.d(NAME_DEBUG_TAG, "locale=" + locale + ", english=" + name + ", chinese=" + (chineseName.isEmpty() ? "(empty)" : chineseName));
            nameDebugCount++;
        }

        School school = new School(schoolCode, id, name, chineseName, district, type, gender, address, chineseAddress, phone, tuition, bus, minibus, mtr, convenience, latitude, longitude);
        school.setReligion(religion);
        school.setChineseReligion(chineseReligion);
        return school;
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
        // Prefer stable numeric/school identifiers before language-dependent fields.
        String id = safe(school.getId());
        if (!id.isEmpty()) {
            return "ID:" + id;
        }
        // Last resort only: language-dependent fields.
        String na = safe(school.getName()) + "|" + safe(school.getAddress());
        if (!na.equals("|")) {
            return "NA:" + na;
        }
        return "EMPTY";
    }

    private String buildEntityKey(School school) {
        String name = safe(school == null ? null : school.getName());
        String address = safe(school == null ? null : school.getAddress());
        if (!name.isEmpty() || !address.isEmpty()) {
            return "NA:" + name + "|" + address;
        }
        return buildDedupeKey(school);
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

    private FieldPick firstNonEmptyWithKey(JSONObject item, String... keys) {
        for (String key : keys) {
            String value = item.optString(key, "").trim();
            if (!value.isEmpty()) {
                return new FieldPick(key, value);
            }
        }
        return new FieldPick(null, null);
    }

    private static final class FieldPick {
        final String key;
        final String value;
        FieldPick(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private FieldPick findChineseNameHeuristic(JSONObject item) {
        if (item == null) return new FieldPick(null, null);
        // Prefer the immediate sibling field right after ENGLISH NAME
        // (EDB payload usually stores Chinese name there).
        JSONArray names = item.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i, "");
                if ("ENGLISH NAME".equalsIgnoreCase(key) && i + 1 < names.length()) {
                    String siblingKey = names.optString(i + 1, "");
                    String siblingValue = item.optString(siblingKey, "").trim();
                    if (looksLikeSchoolNameValue(siblingValue)) {
                        return new FieldPick(siblingKey, siblingValue);
                    }
                }
            }
        }
        // Fallback: scan all fields and pick the most plausible Chinese school name.
        Iterator<String> keys = item.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key == null) continue;
            String value = item.optString(key, "").trim();
            if (looksLikeSchoolNameValue(value)) {
                return new FieldPick(key, value);
            }
        }
        return new FieldPick(null, null);
    }

    private boolean looksLikeSchoolNameValue(String value) {
        if (value == null) return false;
        String s = value.trim();
        if (s.isEmpty()) return false;
        String lower = s.toLowerCase();
        if (lower.startsWith("http")) return false;
        if (s.length() < 2 || s.length() > 80) return false;
        if (s.matches(".*\\d{4,}.*")) return false;
        if (s.contains("號") || s.contains("街") || s.contains("路") || s.contains("道")) return false; // likely address
        if (s.contains("區") && !s.contains("學")) return false; // likely district
        if (lower.contains("street") || lower.contains("road") || lower.contains("avenue") || lower.contains("floor")) return false;
        int cjkCount = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                cjkCount++;
            }
        }
        if (cjkCount >= 2) return true;
        return lower.contains("school") || lower.contains("kindergarten") || lower.contains("college");
    }

    private boolean isEmpty(String v) {
        return v == null || v.trim().isEmpty();
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

    private void fillMissingChineseNamesByTranslation(Context context, List<School> schools) {
        if (context == null || schools == null || schools.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, String> cache = loadNameTranslationCache(prefs);

        List<School> needsTranslationSchools = new ArrayList<>();
        List<String> namesToTranslate = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (School school : schools) {
            if (school == null) continue;
            String en = safeName(school.getName());
            if (en.isEmpty()) continue;
            String zh = safeName(school.getChineseName());
            if (!zh.isEmpty() && !zh.equalsIgnoreCase(en)) {
                continue;
            }
            String cacheKey = cacheKey(en);
            String cached = cache.get(cacheKey);
            if (cached != null && !cached.trim().isEmpty()) {
                school.setChineseName(cached);
                continue;
            }
            if (seen.add(cacheKey)) {
                namesToTranslate.add(en);
            }
            needsTranslationSchools.add(school);
        }

        if (namesToTranslate.isEmpty()) return;

        Log.d(TRANSLATE_DEBUG_TAG, "translate missing chinese names count = " + namesToTranslate.size());
        for (int start = 0; start < namesToTranslate.size(); start += TRANSLATE_BATCH_SIZE) {
            int end = Math.min(start + TRANSLATE_BATCH_SIZE, namesToTranslate.size());
            List<String> batch = namesToTranslate.subList(start, end);
            Map<String, String> translated = translateBatchEnToZh(batch);
            cache.putAll(translated);
        }
        saveNameTranslationCache(prefs, cache);

        for (School school : needsTranslationSchools) {
            if (school == null) continue;
            String en = safeName(school.getName());
            if (en.isEmpty()) continue;
            String translated = cache.get(cacheKey(en));
            if (translated != null && !translated.trim().isEmpty()) {
                school.setChineseName(translated);
            }
        }
    }

    private void applyChineseNameFromAssetMap(Context context, List<School> schools) {
        if (context == null || schools == null || schools.isEmpty()) return;
        Map<String, String> map = loadSchoolNameMapFromAsset(context);
        if (map.isEmpty()) {
            Log.w(TRANSLATE_DEBUG_TAG, "name map is empty: " + SCHOOL_NAME_MAP_ASSET);
            return;
        }
        int applied = 0;
        int unmatched = 0;
        int sample = 0;
        for (School school : schools) {
            if (school == null) continue;
            String en = safeName(school.getName());
            if (en.isEmpty()) continue;
            String zh = map.get(cacheKey(en));
            if (zh != null && !zh.trim().isEmpty()) {
                school.setChineseName(zh.trim());
                applied++;
                if (sample < MAP_DEBUG_SAMPLE_LIMIT) {
                    Log.d(TRANSLATE_DEBUG_TAG, "english=" + en + ", chinese=" + zh.trim());
                    sample++;
                }
            } else {
                unmatched++;
            }
        }
        Log.d(TRANSLATE_DEBUG_TAG, "map loaded size = " + map.size());
        Log.d(TRANSLATE_DEBUG_TAG, "matched = " + applied);
        Log.d(TRANSLATE_DEBUG_TAG, "unmatched = " + unmatched);
    }

    private void applyReligionFromAssetMap(Context context, List<School> schools) {
        if (context == null || schools == null || schools.isEmpty()) return;
        Map<String, String[]> byCode = new LinkedHashMap<>();
        Map<String, String[]> byEnglishName = new LinkedHashMap<>();
        loadReligionMapFromAsset(context, byCode, byEnglishName);
        if (byCode.isEmpty() && byEnglishName.isEmpty()) {
            Log.w(TRANSLATE_DEBUG_TAG, "religion map is empty: " + SCHOOL_RELIGION_MAP_ASSET);
            return;
        }

        int matchedByCode = 0;
        int matchedByName = 0;
        for (School school : schools) {
            if (school == null) continue;
            String[] hit = null;
            String code = safe(school.getSchoolCode());
            if (!code.isEmpty()) {
                hit = byCode.get(code);
            }
            if (hit != null) {
                school.setReligion(hit[0]);
                school.setChineseReligion(hit[1]);
                matchedByCode++;
                continue;
            }

            String englishName = safeName(school.getName());
            if (englishName.isEmpty()) continue;
            hit = byEnglishName.get(cacheKey(englishName));
            if (hit != null) {
                school.setReligion(hit[0]);
                school.setChineseReligion(hit[1]);
                matchedByName++;
            }
        }

        Log.d(TRANSLATE_DEBUG_TAG, "religion matched by code = " + matchedByCode);
        Log.d(TRANSLATE_DEBUG_TAG, "religion matched by name = " + matchedByName);
    }

    private void loadReligionMapFromAsset(Context context, Map<String, String[]> byCode, Map<String, String[]> byEnglishName) {
        if (context == null || byCode == null || byEnglishName == null) return;
        try (InputStream is = context.getAssets().open(SCHOOL_RELIGION_MAP_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean headerRead = false;
            int schoolNoIndex = -1;
            int englishNameIndex = -1;
            int religionIndex = -1;
            int religionZhIndex = -1;
            while ((line = reader.readLine()) != null) {
                String clean = line == null ? "" : line.trim();
                if (clean.isEmpty()) continue;
                List<String> columns = parseCsvLine(clean);
                if (columns.isEmpty()) continue;

                if (!headerRead) {
                    headerRead = true;
                    for (int i = 0; i < columns.size(); i++) {
                        String key = stripBom(columns.get(i)).trim().toUpperCase(Locale.ROOT);
                        if ("SCHOOL NO.".equals(key)) schoolNoIndex = i;
                        else if ("ENGLISH NAME".equals(key)) englishNameIndex = i;
                        else if ("RELIGION".equals(key)) religionIndex = i;
                        else if ("宗教".equals(columns.get(i).trim())) religionZhIndex = i;
                    }
                    continue;
                }

                if (religionIndex < 0) continue;
                String religionEn = getColumnValue(columns, religionIndex);
                String religionZh = religionZhIndex >= 0 ? getColumnValue(columns, religionZhIndex) : "";
                if (religionEn.isEmpty() && religionZh.isEmpty()) continue;

                String schoolNo = schoolNoIndex >= 0 ? getColumnValue(columns, schoolNoIndex) : "";
                if (!schoolNo.isEmpty()) {
                    byCode.put(schoolNo, new String[]{religionEn, religionZh});
                }
                String englishName = englishNameIndex >= 0 ? getColumnValue(columns, englishNameIndex) : "";
                if (!englishName.isEmpty()) {
                    byEnglishName.put(cacheKey(englishName), new String[]{religionEn, religionZh});
                }
            }
            Log.d(TRANSLATE_DEBUG_TAG, "religion map by code = " + byCode.size());
            Log.d(TRANSLATE_DEBUG_TAG, "religion map by english name = " + byEnglishName.size());
        } catch (Exception e) {
            Log.w(TRANSLATE_DEBUG_TAG, "load religion map failed: " + e.getMessage());
        }
    }

    private String getColumnValue(List<String> columns, int index) {
        if (columns == null || index < 0 || index >= columns.size()) return "";
        String value = columns.get(index);
        return value == null ? "" : stripBom(value).trim();
    }

    private Map<String, String> loadSchoolNameMapFromAsset(Context context) {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream is = context.getAssets().open(SCHOOL_NAME_MAP_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            int totalRows = 0;
            int validRows = 0;
            while ((line = reader.readLine()) != null) {
                totalRows++;
                String clean = line == null ? "" : line.trim();
                if (clean.isEmpty()) continue;
                if (!headerSkipped) {
                    headerSkipped = true;
                    String lc = clean.replace("\"", "").toLowerCase(Locale.ROOT);
                    if (lc.contains("english_name") && lc.contains("chinese_name")) {
                        continue;
                    }
                }
                List<String> columns = parseCsvLine(clean);
                if (columns.size() < 2) continue;
                String en = stripBom(columns.get(0)).trim();
                String zh = columns.get(1).trim();
                if (en.isEmpty() || zh.isEmpty()) continue;
                map.put(cacheKey(en), zh);
                validRows++;
            }
            Log.d(TRANSLATE_DEBUG_TAG, "map rows total = " + totalRows + ", valid = " + validRows);
        } catch (Exception e) {
            Log.w(TRANSLATE_DEBUG_TAG, "load map failed: " + e.getMessage());
        }
        return map;
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;

        // Compatibility: some editors accidentally save as TSV.
        if (line.indexOf('\t') >= 0 && line.indexOf(',') < 0) {
            String[] tsv = line.split("\\t", 2);
            if (tsv.length == 2) {
                out.add(stripOptionalQuotes(tsv[0]));
                out.add(stripOptionalQuotes(tsv[1]));
                return out;
            }
        }

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());

        // Keep "english_name" as first column, merge remaining as chinese_name.
        if (out.size() > 2) {
            StringBuilder merged = new StringBuilder();
            for (int i = 1; i < out.size(); i++) {
                if (i > 1) merged.append(',');
                merged.append(out.get(i));
            }
            List<String> normalized = new ArrayList<>(2);
            normalized.add(stripOptionalQuotes(out.get(0)));
            normalized.add(stripOptionalQuotes(merged.toString()));
            return normalized;
        }
        if (out.size() == 2) {
            out.set(0, stripOptionalQuotes(out.get(0)));
            out.set(1, stripOptionalQuotes(out.get(1)));
        }
        return out;
    }

    private String stripOptionalQuotes(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.replace("\"\"", "\"");
    }

    private String stripBom(String value) {
        if (value == null || value.isEmpty()) return value;
        if (value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private Map<String, String> translateBatchEnToZh(List<String> names) {
        Map<String, String> result = new LinkedHashMap<>();
        if (names == null || names.isEmpty()) return result;
        try {
            String joined = String.join("\n", names);
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-TW&dt=t&q="
                    + URLEncoder.encode(joined, StandardCharsets.UTF_8.name());
            String body = executeGet(url);
            String translatedJoined = parseGoogleTranslateText(body);
            if (translatedJoined == null || translatedJoined.trim().isEmpty()) {
                return result;
            }
            String[] lines = translatedJoined.split("\\n");
            for (int i = 0; i < names.size(); i++) {
                String en = names.get(i);
                String zh = i < lines.length ? lines[i].trim() : "";
                if (!zh.isEmpty()) {
                    result.put(cacheKey(en), zh);
                }
            }
        } catch (Exception e) {
            Log.w(TRANSLATE_DEBUG_TAG, "batch translate failed: " + e.getMessage());
        }
        return result;
    }

    private String parseGoogleTranslateText(String body) {
        try {
            JSONArray root = new JSONArray(body);
            JSONArray sentences = root.optJSONArray(0);
            if (sentences == null) return "";
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < sentences.length(); i++) {
                JSONArray piece = sentences.optJSONArray(i);
                if (piece == null || piece.length() == 0) continue;
                String seg = piece.optString(0, "");
                builder.append(seg);
            }
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, String> loadNameTranslationCache(SharedPreferences prefs) {
        Map<String, String> map = new LinkedHashMap<>();
        if (prefs == null) return map;
        String raw = prefs.getString(KEY_NAME_TRANSLATION_CACHE, "");
        if (raw == null || raw.trim().isEmpty()) return map;
        try {
            JSONObject obj = new JSONObject(raw);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = obj.optString(k, "");
                if (!k.trim().isEmpty() && !v.trim().isEmpty()) {
                    map.put(k, v);
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private void saveNameTranslationCache(SharedPreferences prefs, Map<String, String> cache) {
        if (prefs == null || cache == null) return;
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> e : cache.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                obj.put(e.getKey(), e.getValue());
            }
            prefs.edit().putString(KEY_NAME_TRANSLATION_CACHE, obj.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private String safeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String cacheKey(String enName) {
        return normalizeSchoolNameKey(enName);
    }

    private String normalizeSchoolNameKey(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.isEmpty()) return "";
        v = v.replace('\u3000', ' ');
        v = v.replace('（', '(').replace('）', ')');
        v = v.replace('，', ',').replace('：', ':');
        // Improve map hit rate when punctuation/spacing styles differ.
        v = v.replace("&", " AND ");
        v = v.replaceAll("\\s+", " ");
        v = v.toUpperCase(Locale.ROOT);
        v = v.replaceAll("[^A-Z0-9]", "");
        return v;
    }

    private List<School> getCachedSchoolsSnapshot() {
        synchronized (SCHOOL_CACHE_LOCK) {
            if (cachedMasterSchools == null || cachedMasterSchools.isEmpty()) return null;
            return deepCopySchools(cachedMasterSchools);
        }
    }

    private void setCachedMasterSchools(List<School> schools) {
        synchronized (SCHOOL_CACHE_LOCK) {
            cachedMasterSchools = deepCopySchools(schools);
        }
    }

    private List<School> deepCopySchools(List<School> source) {
        List<School> copy = new ArrayList<>();
        if (source == null) return copy;
        for (School s : source) {
            if (s == null) continue;
            School c = new School(
                    s.getSchoolCode(),
                    s.getId(),
                    s.getName(),
                    s.getChineseName(),
                    s.getDistrict(),
                    s.getType(),
                    s.getGender(),
                    s.getAddress(),
                    s.getChineseAddress(),
                    s.getPhone(),
                    s.getTuition(),
                    s.getTransportBus(),
                    s.getTransportMinibus(),
                    s.getTransportMtr(),
                    s.getTransportConvenience(),
                    s.getLatitude(),
                    s.getLongitude()
            );
            c.setReligion(s.getReligion());
            c.setChineseReligion(s.getChineseReligion());
            c.setDistance(s.getDistance());
            c.clearCachedSortMeta();
            copy.add(c);
        }
        return copy;
    }
}
