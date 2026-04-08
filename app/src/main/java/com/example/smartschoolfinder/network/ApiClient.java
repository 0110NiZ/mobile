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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
    private static final String UNIVERSITY_DEBUG_TAG = "UNIVERSITY_DEBUG";
    private static final String PREFS_NAME = "smart_school_prefs";
    private static final String KEY_NAME_TRANSLATION_CACHE = "name_translation_cache_v1";
    private static final String SCHOOL_NAME_MAP_ASSET = "school_name_zh_map.csv";
    private static final String SCHOOL_RELIGION_MAP_ASSET = "SCH_LOC_EDB.csv";
    private static final String UNIVERSITY_CSV_ASSET = "higher_education_institutions.csv";
    private static final String SCHOOLS_CACHE_FILE = "schools_master_cache_v3.json";
    private static final String SCHOOLS_COMPILED_API_URL = AppConstants.REVIEW_API_BASE_URL + "api/schools/compiled";
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

                List<School> schools = loadSchoolsFromDiskCache(context);
                if (schools == null || schools.isEmpty()) {
                    schools = fetchSchoolsFromBackendCompiled(preferChineseContent);
                }
                if (schools == null || schools.isEmpty()) {
                    schools = fetchAndMergeAllSources(preferChineseContent);
                }
                schools = mergeUniversitySchoolsFromAsset(context, schools);
                applyReligionFromAssetMap(context, schools);
                applyUniversityWebsiteOverrides(schools);
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
                saveSchoolsToDiskCache(context, schools);
                final List<School> resultSchools = schools;
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(resultSchools));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Failed to load schools: " + e.getMessage()));
            }
        }).start();
    }

    private List<School> fetchSchoolsFromBackendCompiled(boolean preferChineseContent) {
        try {
            String body = executeGet(SCHOOLS_COMPILED_API_URL);
            List<School> schools = parseSchoolsFromResponse(body, preferChineseContent);
            if (schools != null && !schools.isEmpty()) {
                Log.d(COUNT_DEBUG_TAG, "compiled backend schools = " + schools.size());
                return dedupeMergedSchools(schools);
            }
        } catch (Exception e) {
            Log.w(COUNT_DEBUG_TAG, "compiled backend unavailable: " + e.getMessage());
        }
        return new ArrayList<>();
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

    private List<School> mergeUniversitySchoolsFromAsset(Context context, List<School> schools) {
        List<School> base = schools == null ? new ArrayList<>() : new ArrayList<>(schools);
        List<School> universities = loadUniversitySchoolsFromAsset(context);
        if (universities.isEmpty()) {
            Log.d(UNIVERSITY_DEBUG_TAG, "merged total = " + base.size());
            Log.d(UNIVERSITY_DEBUG_TAG, "deduped total = " + dedupeMergedSchools(base).size());
            return dedupeMergedSchools(base);
        }
        base.addAll(universities);
        Log.d(UNIVERSITY_DEBUG_TAG, "merged total = " + base.size());
        List<School> deduped = dedupeMergedSchools(base);
        deduped = overlayUniversityFields(deduped, universities);
        Log.d(UNIVERSITY_DEBUG_TAG, "deduped total = " + deduped.size());
        return deduped;
    }

    private List<School> overlayUniversityFields(List<School> deduped, List<School> universities) {
        if (deduped == null || deduped.isEmpty() || universities == null || universities.isEmpty()) {
            return deduped == null ? new ArrayList<>() : deduped;
        }
        Map<String, School> uniByEntityKey = new LinkedHashMap<>();
        for (School uni : universities) {
            if (uni == null) continue;
            uniByEntityKey.put(buildEntityKey(uni), uni);
        }
        int patched = 0;
        List<School> out = new ArrayList<>(deduped.size());
        for (School s : deduped) {
            if (s == null) continue;
            School uni = uniByEntityKey.get(buildEntityKey(s));
            if (uni == null) {
                out.add(s);
                continue;
            }
            String district = safeName(s.getDistrict());
            String districtFromUni = safeName(uni.getDistrict());
            String mergedDistrict = isUnknownDistrict(district) ? districtFromUni : district;
            if (mergedDistrict.isEmpty()) mergedDistrict = "Unknown";

            // Keep university records consistently labeled as Higher Education.
            String mergedType = safeName(uni.getType()).isEmpty() ? s.getType() : uni.getType();
            String mergedZhName = safeName(s.getChineseName()).isEmpty() ? uni.getChineseName() : s.getChineseName();
            String mergedZhAddress = safeName(s.getChineseAddress()).isEmpty() ? uni.getChineseAddress() : s.getChineseAddress();
            String mergedPhone = safeName(s.getPhone()).isEmpty() ? "N/A" : s.getPhone();
            String mergedWebsite = safeName(s.getWebsite()).isEmpty() ? safeName(uni.getWebsite()) : s.getWebsite();

            School merged = new School(
                    s.getSchoolCode(),
                    s.getId(),
                    s.getName(),
                    mergedZhName,
                    mergedDistrict,
                    mergedType,
                    s.getGender(),
                    s.getAddress(),
                    mergedZhAddress,
                    mergedPhone,
                    s.getTuition(),
                    s.getTransportBus(),
                    s.getTransportMinibus(),
                    s.getTransportMtr(),
                    s.getTransportConvenience(),
                    s.getLatitude(),
                    s.getLongitude()
            );
            merged.setWebsite(mergedWebsite);
            merged.setReligion(s.getReligion());
            merged.setChineseReligion(s.getChineseReligion());
            merged.setDistance(s.getDistance());
            out.add(merged);
            patched++;
        }
        Log.d(UNIVERSITY_DEBUG_TAG, "overlay patched universities = " + patched);
        return out;
    }

    private List<School> loadUniversitySchoolsFromAsset(Context context) {
        List<School> out = new ArrayList<>();
        if (context == null) {
            Log.w(UNIVERSITY_DEBUG_TAG, "csv loaded = false, reason = context is null");
            return out;
        }
        int rawRows = 0;
        int mappedRows = 0;
        try (InputStream is = context.getAssets().open(UNIVERSITY_CSV_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            Log.d(UNIVERSITY_DEBUG_TAG, "csv loaded = true");
            String header = reader.readLine();
            if (header == null || header.trim().isEmpty()) {
                Log.w(UNIVERSITY_DEBUG_TAG, "csv loaded = false, reason = empty header");
                return out;
            }
            List<String> headers = parseCsvColumns(header);
            int idxNameEn = findHeaderIndex(headers, "NAME_EN");
            int idxNameTc = findHeaderIndex(headers, "NAME_TC");
            int idxAddressEn = findHeaderIndex(headers, "ADDRESS_EN");
            int idxAddressTc = findHeaderIndex(headers, "ADDRESS_TC");
            int idxLat = findHeaderIndex(headers, "LATITUDE");
            int idxLon = findHeaderIndex(headers, "LONGITUDE");
            int idxId = findHeaderIndex(headers, "ID", "INSTITUTION_ID", "UID", "KEY");
            if (idxNameEn < 0 || idxAddressEn < 0 || idxLat < 0 || idxLon < 0) {
                Log.w(UNIVERSITY_DEBUG_TAG, "csv loaded = false, reason = required headers missing");
                return out;
            }
            String line;
            int sampleLogged = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                rawRows++;
                List<String> cols = parseCsvColumns(line);
                String nameEn = getColumnValue(cols, idxNameEn);
                String nameTc = idxNameTc >= 0 ? getColumnValue(cols, idxNameTc) : "";
                String addressEn = getColumnValue(cols, idxAddressEn);
                String addressTc = idxAddressTc >= 0 ? getColumnValue(cols, idxAddressTc) : "";
                if (addressEn == null) addressEn = "";
                if (addressTc == null) addressTc = "";
                addressEn = addressEn.trim();
                addressTc = addressTc.trim();
                String inferredDistrict = inferDistrictFromAddress(addressEn, addressTc);
                double lat = parseDoubleSafe(getColumnValue(cols, idxLat));
                double lon = parseDoubleSafe(getColumnValue(cols, idxLon));
                if (nameEn.isEmpty() || addressEn.isEmpty() || Double.isNaN(lat) || Double.isNaN(lon)) {
                    continue;
                }
                String stableId = idxId >= 0 ? getColumnValue(cols, idxId) : "";
                if (stableId.isEmpty()) {
                    stableId = "UNI|" + normalizeSchoolNameKey(nameEn) + "|" + normalizeSchoolNameKey(addressEn);
                }
                School school = new School(
                        "",
                        stableId,
                        nameEn,
                        nameTc,
                        inferredDistrict,
                        "Higher Education",
                        "",
                        addressEn,
                        addressTc,
                        "N/A",
                        "N/A",
                        "N/A",
                        "N/A",
                        "N/A",
                        "N/A",
                        lat,
                        lon
                );
                out.add(school);
                mappedRows++;
                if (sampleLogged < 5) {
                    Log.d(UNIVERSITY_DEBUG_TAG, "sample = "
                            + nameEn + " / " + (nameTc.isEmpty() ? "(empty)" : nameTc)
                            + " | addr_en=" + addressEn
                            + " | addr_tc=" + (addressTc.isEmpty() ? "(empty)" : addressTc)
                            + " | district=" + inferredDistrict
                            + " | phone=N/A");
                    sampleLogged++;
                }
            }
            Log.d(UNIVERSITY_DEBUG_TAG, "university raw rows = " + rawRows);
            Log.d(UNIVERSITY_DEBUG_TAG, "mapped rows = " + mappedRows);
        } catch (Exception e) {
            Log.w(UNIVERSITY_DEBUG_TAG, "csv loaded = false, reason = " + e.getMessage());
        }
        return out;
    }

    private int findHeaderIndex(List<String> headers, String... names) {
        if (headers == null || names == null) return -1;
        for (int i = 0; i < headers.size(); i++) {
            String h = stripBom(headers.get(i)).trim().toUpperCase(Locale.ROOT);
            for (String name : names) {
                if (name != null && h.equals(name.toUpperCase(Locale.ROOT))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private double parseDoubleSafe(String raw) {
        if (raw == null) return Double.NaN;
        String v = raw.trim();
        if (v.isEmpty()) return Double.NaN;
        try {
            return Double.parseDouble(v);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private String inferDistrictFromAddress(String addressEn, String addressTc) {
        String en = addressEn == null ? "" : addressEn.trim().toLowerCase(Locale.ROOT);
        String tc = addressTc == null ? "" : addressTc.trim();

        if (containsAny(en, "hong kong", "aberdeen", "pokfulam", "north point", "wan chai", "wanchai",
                "causeway bay", "admiralty", "central", "sheung wan", "chai wan", "shau kei wan")
                || containsAny(tc, "香港", "香港島", "港島", "香港仔", "薄扶林", "北角", "灣仔", "铜锣湾", "銅鑼灣",
                "金鐘", "中環", "上環", "柴灣", "筲箕灣")) {
            return "Hong Kong Island";
        }

        if (containsAny(en, "kowloon", "ho man tin", "hung hom", "tsim sha tsui", "kowloon bay",
                "cheung sha wan", "mong kok", "yaumati", "yau ma tei", "kwun tong", "wong tai sin", "sham shui po")
                || containsAny(tc, "九龍", "何文田", "紅磡", "尖沙咀", "九龍灣", "長沙灣", "旺角", "油麻地", "觀塘", "黄大仙", "黃大仙", "深水埗")) {
            return "Kowloon";
        }

        if (containsAny(en, "new territories", "n.t.", "nt.", "shatin", "sha tin", "tai po", "tuen mun",
                "fanling", "tsuen wan", "ma on shan", "tseung kwan o", "sai kung", "yuen long", "sheung shui", "kwai chung", "tsing yi")
                || containsAny(tc, "新界", "沙田", "大埔", "屯門", "粉嶺", "荃灣", "馬鞍山", "將軍澳", "西貢", "元朗", "上水", "葵涌", "青衣")) {
            return "New Territories";
        }

        return "Unknown";
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isEmpty() || keywords == null) return false;
        for (String keyword : keywords) {
            if (keyword == null || keyword.isEmpty()) continue;
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private boolean isUnknownDistrict(String district) {
        if (district == null) return true;
        String d = district.trim().toLowerCase(Locale.ROOT);
        return d.isEmpty() || "unknown".equals(d) || "未知".equals(d);
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
        String website = firstNonEmpty(item, "WEBSITE", "SCHOOL WEBSITE", "SCHOOL WEB SITE", "網址", "學校網址", "website", "url");
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
        if (website == null) website = "";
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
        school.setWebsite(website);
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
                String existingZh = safeName(school.getChineseName());
                if (existingZh.isEmpty() || existingZh.equalsIgnoreCase(en)) {
                    school.setChineseName(zh.trim());
                    applied++;
                    if (sample < MAP_DEBUG_SAMPLE_LIMIT) {
                        Log.d(TRANSLATE_DEBUG_TAG, "english=" + en + ", chinese=" + zh.trim());
                        sample++;
                    }
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
        int matchedById = 0;
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
                if ((school.getWebsite() == null || school.getWebsite().trim().isEmpty()) && hit.length > 2) {
                    school.setWebsite(hit[2]);
                }
                matchedByCode++;
                continue;
            }

            // Many rows use SCHOOL NO. as stable id; match by School.id as fallback.
            String id = safe(school.getId());
            if (!id.isEmpty()) {
                hit = byCode.get(id);
            }
            if (hit != null) {
                school.setReligion(hit[0]);
                school.setChineseReligion(hit[1]);
                if ((school.getWebsite() == null || school.getWebsite().trim().isEmpty()) && hit.length > 2) {
                    school.setWebsite(hit[2]);
                }
                matchedById++;
                continue;
            }

            String englishName = safeName(school.getName());
            if (englishName.isEmpty()) continue;
            hit = byEnglishName.get(cacheKey(englishName));
            if (hit != null) {
                school.setReligion(hit[0]);
                school.setChineseReligion(hit[1]);
                if ((school.getWebsite() == null || school.getWebsite().trim().isEmpty()) && hit.length > 2) {
                    school.setWebsite(hit[2]);
                }
                matchedByName++;
            }
        }

        Log.d(TRANSLATE_DEBUG_TAG, "religion matched by code = " + matchedByCode);
        Log.d(TRANSLATE_DEBUG_TAG, "religion matched by id = " + matchedById);
        Log.d(TRANSLATE_DEBUG_TAG, "religion matched by name = " + matchedByName);
    }

    private void applyUniversityWebsiteOverrides(List<School> schools) {
        if (schools == null || schools.isEmpty()) return;
        int applied = 0;
        for (School school : schools) {
            if (school == null) continue;
            String website = pickUniversityWebsiteOverride(school);
            if (website.isEmpty()) continue;
            school.setWebsite(website);
            applied++;
        }
        Log.d(UNIVERSITY_DEBUG_TAG, "website overrides applied = " + applied);
    }

    private String pickUniversityWebsiteOverride(School school) {
        String en = normalizeSchoolNameKey(safeName(school.getName()));
        String zh = normalizeSchoolNameKey(safeName(school.getChineseName()));
        if (en.isEmpty() && zh.isEmpty()) return "";

        if (containsAnyNorm(en, zh, "CITYUNIVERSITYOFHONGKONG", "香港城市大學", "香港城市大学")) {
            return "https://www.cityu.edu.hk/zh-hk";
        }
        if (containsAnyNorm(en, zh, "GRATIACHRISTIANCOLLEGE", "宏恩基督教學院", "宏恩基督教学院")) {
            return "https://www.gcc.edu.hk/zh/%e4%b8%bb%e9%a0%81/";
        }
        if (containsAnyNorm(en, zh, "HKCTINSTITUTEOFHIGHEREDUCATION", "HONGKONGCOLLEGEOFTECHNOLOGY", "港專學院", "港专学院", "香港專業進修學校", "香港专业进修学校")) {
            return "https://www.hkct.edu.hk/sc";
        }
        if (containsAnyNorm(en, zh, "HONGKONGACADEMYFORPERFORMINGARTS", "香港演藝學院", "香港演艺学院")) {
            return "https://www.hkapa.edu/tch";
        }
        if (containsAnyNorm(en, zh, "HONGKONGBAPTISTUNIVERSITY", "香港浸會大學", "香港浸会大学")) {
            return "https://www.hkbu.edu.hk/zh_cn.html";
        }
        if (containsAnyNorm(en, zh, "HONGKONGCHUHAICOLLEGE", "香港珠海學院", "香港珠海学院")) {
            return "https://www.chuhai.edu.hk/";
        }
        if (containsAnyNorm(en, zh, "HONGKONGMETROPOLITANUNIVERSITY", "香港都會大學", "香港都会大学")) {
            return "https://www.hkmu.edu.hk/sc/";
        }
        if (containsAnyNorm(en, zh, "HONGKONGNANGYANCOLLEGEOFHIGHEREDUCATION", "香港能仁專上學院", "香港能仁专上学院")) {
            return "https://www.ny.edu.hk/web/home.html";
        }
        if (containsAnyNorm(en, zh, "HONGKONGSHUEYANUNIVERSITY", "香港樹仁大學", "香港树仁大学")) {
            return "https://www.hksyu.edu/sc/home";
        }
        if (containsAnyNorm(en, zh, "LINGNANUNIVERSITY", "嶺南大學", "岭南大学")) {
            return "https://www.ln.edu.hk/";
        }
        if (containsAnyNorm(en, zh, "SAINTFRANCISUNIVERSITY", "聖方濟各大學", "圣方济各大学")) {
            return "https://www.sfu.edu.hk/sc/home/index.html";
        }
        if (containsAnyNorm(en, zh, "VOCATIONALTRAININGCOUNCIL", "職業訓練局", "职业训练局", "THEI", "HONGKONGINSTITUTEOFVOCATIONALEDUCATION", "HONGKONGINSTITUTEOFINFORMATIONTECHNOLOGY")) {
            return "https://thei.edu.hk/";
        }
        if (containsAnyNorm(en, zh, "THECHINESEUNIVERSITYOFHONGKONG", "香港中文大學", "香港中文大学")) {
            return "https://translate.itsc.cuhk.edu.hk/uniTS/www.cuhk.edu.hk/chinese/index.html";
        }
        if (containsAnyNorm(en, zh, "THEEDUCATIONUNIVERSITYOFHONGKONG", "香港教育大學", "香港教育大学")) {
            return "https://www.eduhk.hk/zhs";
        }
        if (containsAnyNorm(en, zh, "THEHANGSENGUNIVERSITYOFHONGKONG", "香港恒生大學", "香港恒生大学")) {
            return "https://www.hsu.edu.hk/hk/";
        }
        if (containsAnyNorm(en, zh, "THEHONGKONGPOLYTECHNICUNIVERSITY", "香港理工大學", "香港理工大学")) {
            return "https://www.polyu.edu.hk/sc/";
        }
        if (containsAnyNorm(en, zh, "THEHONGKONGUNIVERSITYOFSCIENCEANDTECHNOLOGY", "香港科技大學", "香港科技大学")) {
            return "https://www.polyu.edu.hk/sc/";
        }
        if (containsAnyNorm(en, zh, "THEUNIVERSITYOFHONGKONG", "香港大學", "香港大学", "HKUSPACE")) {
            return "https://www.hku.hk/c_index.html";
        }
        if (containsAnyNorm(en, zh, "TUNGWAHCOLLEGE", "東華學院", "东华学院")) {
            return "https://www.twc.edu.hk/sc/index.php";
        }
        if (containsAnyNorm(en, zh, "UOWCOLLEGEHONGKONG", "香港伍倫貢學院", "香港伍伦贡学院")) {
            return "https://www.uowchk.edu.hk/";
        }
        if (containsAnyNorm(en, zh, "YEWCHUNGCOLLEGEOFEARLYCHILDHOODEDUCATION", "耀中幼教學院", "耀中幼教学院")) {
            return "https://www.yccece.edu.hk/tc/";
        }
        return "";
    }

    private boolean containsAnyNorm(String enNorm, String zhNorm, String... needles) {
        if (needles == null) return false;
        for (String needle : needles) {
            if (needle == null || needle.trim().isEmpty()) continue;
            String n = normalizeSchoolNameKey(needle);
            if (!n.isEmpty() && ((enNorm != null && enNorm.contains(n)) || (zhNorm != null && zhNorm.contains(n)))) {
                return true;
            }
        }
        return false;
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
            int websiteEnIndex = -1;
            int websiteZhIndex = -1;
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
                        else if ("WEBSITE".equals(key) || "SCHOOL WEBSITE".equals(key)) websiteEnIndex = i;
                        else if ("學校網址".equals(columns.get(i).trim()) || "網址".equals(columns.get(i).trim())) websiteZhIndex = i;
                    }
                    continue;
                }

                if (religionIndex < 0) continue;
                String religionEn = getColumnValue(columns, religionIndex);
                String religionZh = religionZhIndex >= 0 ? getColumnValue(columns, religionZhIndex) : "";
                String websiteEn = websiteEnIndex >= 0 ? getColumnValue(columns, websiteEnIndex) : "";
                String websiteZh = websiteZhIndex >= 0 ? getColumnValue(columns, websiteZhIndex) : "";
                String website = !websiteEn.isEmpty() ? websiteEn : websiteZh;
                if (religionEn.isEmpty() && religionZh.isEmpty()) continue;

                String schoolNo = schoolNoIndex >= 0 ? getColumnValue(columns, schoolNoIndex) : "";
                if (!schoolNo.isEmpty()) {
                    byCode.put(schoolNo, new String[]{religionEn, religionZh, website});
                }
                String englishName = englishNameIndex >= 0 ? getColumnValue(columns, englishNameIndex) : "";
                if (!englishName.isEmpty()) {
                    byEnglishName.put(cacheKey(englishName), new String[]{religionEn, religionZh, website});
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

    private List<String> parseCsvColumns(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
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
                out.add(stripOptionalQuotes(cur.toString()));
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(stripOptionalQuotes(cur.toString()));
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

    private List<School> loadSchoolsFromDiskCache(Context context) {
        if (context == null) return new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), SCHOOLS_CACHE_FILE);
            if (!file.exists() || file.length() <= 0) return new ArrayList<>();
            StringBuilder builder = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            String raw = builder.toString().trim();
            if (raw.isEmpty()) return new ArrayList<>();
            List<School> schools = parseSchoolsFromResponse(raw, false);
            if (schools == null || schools.isEmpty()) return new ArrayList<>();
            Log.d(COUNT_DEBUG_TAG, "disk cached schools = " + schools.size());
            return dedupeMergedSchools(schools);
        } catch (Exception e) {
            Log.w(COUNT_DEBUG_TAG, "load disk cache failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveSchoolsToDiskCache(Context context, List<School> schools) {
        if (context == null || schools == null || schools.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            for (School s : schools) {
                if (s == null) continue;
                JSONObject obj = new JSONObject();
                obj.put("SCHOOL CODE", s.getSchoolCode());
                obj.put("SCHOOL NO.", s.getId());
                obj.put("ENGLISH NAME", s.getName());
                obj.put("中文名稱", s.getChineseName());
                obj.put("DISTRICT", s.getDistrict());
                obj.put("SCHOOL LEVEL", s.getType());
                obj.put("STUDENTS GENDER", s.getGender());
                obj.put("ENGLISH ADDRESS", s.getAddress());
                obj.put("中文地址", s.getChineseAddress());
                obj.put("TELEPHONE", s.getPhone());
                obj.put("TUITION", s.getTuition());
                obj.put("WEBSITE", s.getWebsite());
                obj.put("RELIGION", s.getReligion());
                obj.put("宗教", s.getChineseReligion());
                obj.put("BUS", s.getTransportBus());
                obj.put("MINIBUS", s.getTransportMinibus());
                obj.put("MTR", s.getTransportMtr());
                obj.put("TRANSPORT CONVENIENCE", s.getTransportConvenience());
                obj.put("LATITUDE", s.getLatitude());
                obj.put("LONGITUDE", s.getLongitude());
                arr.put(obj);
            }
            JSONObject root = new JSONObject();
            root.put("schools", arr);
            File file = new File(context.getFilesDir(), SCHOOLS_CACHE_FILE);
            try (FileOutputStream fos = new FileOutputStream(file, false);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
                writer.write(root.toString());
            }
        } catch (Exception e) {
            Log.w(COUNT_DEBUG_TAG, "save disk cache failed: " + e.getMessage());
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
            c.setWebsite(s.getWebsite());
            c.setChineseReligion(s.getChineseReligion());
            c.setDistance(s.getDistance());
            c.clearCachedSortMeta();
            copy.add(c);
        }
        return copy;
    }
}
