package com.example.smartschoolfinder.utils;

import android.content.Context;
import android.util.Log;

import com.example.smartschoolfinder.model.School;

import java.util.Locale;

public final class SchoolDisplayUtils {
    private static final String NAME_DEBUG_TAG = "NAME_DEBUG";
    private static final String TRANSLATE_DEBUG_TAG = "TRANSLATE_DEBUG";
    private static final int NAME_DEBUG_SAMPLE_LIMIT = 40;
    private static int nameDisplayDebugCount = 0;
    private SchoolDisplayUtils() {}

    public static String displayName(Context context, School school) {
        if (school == null) return "";
        boolean zh = LocaleUtils.prefersChineseSchoolData(context);
        String zhName = safe(school.getChineseName());
        String enName = safe(school.getName());
        String display = (zh && !zhName.isEmpty()) ? zhName : enName;
        if (nameDisplayDebugCount < NAME_DEBUG_SAMPLE_LIMIT) {
            Log.d(NAME_DEBUG_TAG, "locale=" + (zh ? "zh" : "en")
                    + ", english=" + enName
                    + ", chinese=" + (zhName.isEmpty() ? "(empty)" : zhName)
                    + ", displayName=" + display);
            Log.d(TRANSLATE_DEBUG_TAG, "locale=" + (zh ? "zh" : "en") + ", displayName=" + display);
            nameDisplayDebugCount++;
        }
        return display;
    }

    public static String displayAddress(Context context, School school) {
        if (school == null) return "";
        boolean zh = LocaleUtils.prefersChineseSchoolData(context);
        String zhAddress = safe(school.getChineseAddress());
        String enAddress = safe(school.getAddress());
        if (zh && !zhAddress.isEmpty()) return zhAddress;
        return enAddress;
    }

    public static String displayDistrict(Context context, School school) {
        String raw = school == null ? "" : safe(school.getDistrict());
        if (!LocaleUtils.prefersChineseSchoolData(context)) {
            return raw;
        }
        String norm = FilterUtils.normalizeDistrict(raw);
        if ("hong kong island".equals(norm)) return "香港島";
        if ("kowloon".equals(norm)) return "九龍";
        if ("new territories".equals(norm)) return "新界";
        return "未知";
    }

    public static String displayType(Context context, School school) {
        String raw = school == null ? "" : safe(school.getType());
        if (!LocaleUtils.prefersChineseSchoolData(context)) {
            return raw;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("kindergarten") && (lower.contains("child care") || lower.contains("childcare"))) {
            return "幼稚園暨幼兒中心";
        }
        String norm = FilterUtils.normalizeType(raw);
        if ("kindergarten".equals(norm)) return "幼稚園";
        if ("primary".equals(norm)) return "小學";
        if ("secondary".equals(norm)) return "中學";
        if ("university".equals(norm)) return "大學";
        if ("special".equals(norm)) return "特殊學校";
        if ("international".equals(norm)) return "國際學校";
        if ("unknown".equals(norm) || raw.isEmpty()) return "未知";
        return raw;
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
