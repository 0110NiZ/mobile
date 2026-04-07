package com.example.smartschoolfinder.data;

import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.constants.AppConstants;
import com.example.smartschoolfinder.model.TransportInfo;
import com.example.smartschoolfinder.network.ApiCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TransportRepository {
    /** Matches backend TransportService mock names when API omits name_zh. */
    private static final Map<String, String> KNOWN_STOP_EN_TO_ZH;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("Tai Po Market Station", "大埔墟站");
        m.put("Tai Wo Station", "太和站");
        m.put("Kowloon Tong Station", "九龍塘站");
        m.put("Sha Tin Station", "沙田站");
        m.put("University Station", "大學站");
        m.put("Tai Yuen Estate Stop (KMB 71K)", "大元邨站（九巴71K）");
        m.put("Kwong Fuk Road Stop (KMB 72A)", "廣福道站（九巴72A）");
        m.put("Kowloon Tong Station Bus Terminus", "九龍塘鐵路站巴士總站");
        m.put("La Salle Road Stop", "喇沙利道站");
        m.put("Sha Tin Central Bus Terminus", "沙田市中心巴士總站");
        m.put("Green Minibus 20K Stop", "專線小巴20K站");
        m.put("Green Minibus 20A Stop", "專線小巴20A站");
        m.put("Green Minibus 28K Stop", "專線小巴28K站");
        m.put("Green Minibus 25M (Kowloon Tong) Stop", "專線小巴25M站（九龍塘）");
        m.put("Green Minibus 65A (Sha Tin) Stop", "專線小巴65A站（沙田）");
        KNOWN_STOP_EN_TO_ZH = Collections.unmodifiableMap(m);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void getSchoolTransport(String schoolId, boolean preferChinese, ApiCallback<TransportInfo> callback) {
        new Thread(() -> {
            try {
                String url = AppConstants.TRANSPORT_API_BASE_URL
                        + "api/schools/" + encodePath(schoolId) + "/transport";
                String body = executeGet(url, preferChinese);
                JSONObject root = new JSONObject(body);
                TransportInfo info = parseToTransportInfo(root, preferChinese);
                mainHandler.post(() -> callback.onSuccess(info));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Failed to load transport: " + e.getMessage()));
            }
        }).start();
    }

    private static String localizedStopName(JSONObject node, boolean preferChinese) {
        if (node == null) {
            return "N/A";
        }
        if (preferChinese) {
            String zh = node.optString("name_zh", "").trim();
            if (!zh.isEmpty()) {
                return zh;
            }
            String camel = node.optString("nameZh", "").trim();
            if (!camel.isEmpty()) {
                return camel;
            }
            String tc = node.optString("name_tc", "").trim();
            if (!tc.isEmpty()) {
                return tc;
            }
        }
        String en = node.optString("name", "").trim();
        if (en.isEmpty()) {
            return "N/A";
        }
        if (preferChinese) {
            String mapped = KNOWN_STOP_EN_TO_ZH.get(en);
            if (mapped != null) {
                return mapped;
            }
        }
        return en;
    }

    private TransportInfo parseToTransportInfo(JSONObject root, boolean preferChinese) {
        JSONObject mtr = root.optJSONObject("mtr");
        JSONObject bus = root.optJSONObject("bus");
        JSONObject minibus = root.optJSONObject("minibus");

        String mtrName = mtr == null ? "N/A" : localizedStopName(mtr, preferChinese);
        String mtrDistance = mtr == null ? "N/A" : distanceToText(mtr.optInt("distance", -1));

        String busName = bus == null ? "N/A" : localizedStopName(bus, preferChinese);
        String busDistance = bus == null ? "N/A" : distanceToText(bus.optInt("distance", -1));
        String minibusName = minibus == null ? "N/A" : localizedStopName(minibus, preferChinese);
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

    private String executeGet(String urlString, boolean preferChinese) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            if (preferChinese) {
                connection.setRequestProperty("Accept-Language", "zh-Hant,zh-TW;q=0.9,zh;q=0.8,en;q=0.7");
            } else {
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
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

    private String encodePath(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }
}

