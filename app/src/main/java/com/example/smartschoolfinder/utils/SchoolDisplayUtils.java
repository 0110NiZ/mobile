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
        if ("kindergarten_childcare".equals(norm)) return "幼稚園暨幼兒中心";
        if ("kindergarten".equals(norm)) return "幼稚園";
        if ("primary".equals(norm)) return "小學";
        if ("secondary".equals(norm)) return "中學";
        if ("university".equals(norm)) return "專上教育";
        if ("special".equals(norm)) return "特殊學校";
        if ("international".equals(norm)) return "國際學校";
        if ("unknown".equals(norm) || raw.isEmpty()) return "未知";
        return raw;
    }

    public static String religionFilterKey(School school) {
        String en = school == null ? "" : safe(school.getReligion());
        String zh = school == null ? "" : safe(school.getChineseReligion());
        return normalizeReligionKey(en, zh);
    }

    public static String displayReligion(Context context, School school) {
        boolean zh = LocaleUtils.prefersChineseSchoolData(context);
        String en = school == null ? "" : safe(school.getReligion());
        String zhValue = school == null ? "" : safe(school.getChineseReligion());
        String key = normalizeReligionKey(en, zhValue);
        if ("na".equals(key)) return "N/A";
        if ("taoism".equals(key)) return zh ? "道教" : "Taoism";
        if ("buddhism".equals(key)) return zh ? "佛教" : "Buddhism";
        if ("christianity".equals(key)) return zh ? "基督教" : "Christianity";
        if ("catholicism".equals(key)) return zh ? "天主教" : "Catholicism";
        if ("confucianism".equals(key)) return zh ? "孔教" : "Confucianism";
        if ("other".equals(key)) return zh ? "其他" : "Other";
        if ("three-religions".equals(key)) return zh ? "儒釋道三教" : "Confucianism-Buddhism-Taoism";
        if ("none".equals(key)) return zh ? "無" : "None";
        if ("sikhism".equals(key)) return zh ? "錫克教" : "Sikhism";
        if ("islam".equals(key)) return zh ? "伊斯蘭教" : "Islam";
        return zh ? (zhValue.isEmpty() ? en : zhValue) : (en.isEmpty() ? zhValue : en);
    }

    private static String normalizeReligionKey(String enRaw, String zhRaw) {
        String en = safe(enRaw).toLowerCase(Locale.ROOT);
        String zh = safe(zhRaw);
        String zhNoSpace = zh.replace(" ", "");
        if (en.isEmpty() && zh.isEmpty()) return "na";

        if ("n/a".equals(en) || "na".equals(en) || "n.a.".equals(en)
                || "not applicable".equals(en) || "-".equals(en)
                || "不適用".equals(zh) || "不适用".equals(zh)) {
            return "na";
        }
        if (en.contains("tao") || zhNoSpace.contains("道教")) return "taoism";
        if (en.contains("buddh") || zhNoSpace.contains("佛教")) return "buddhism";
        if (en.contains("protestant") || en.contains("christian") || zhNoSpace.contains("基督教")) return "christianity";
        if (en.contains("catholic") || zhNoSpace.contains("天主教")) return "catholicism";
        if (en.contains("confucian") || zhNoSpace.contains("孔教")) return "confucianism";
        if (en.contains("sikh") || zhNoSpace.contains("錫克教") || zhNoSpace.contains("锡克教")) return "sikhism";
        if (en.contains("islam") || en.contains("muslim") || zhNoSpace.contains("伊斯蘭教") || zhNoSpace.contains("伊斯兰教")) return "islam";
        if (en.contains("confucianism-buddhism-taoism")
                || zhNoSpace.contains("儒釋道三教") || zhNoSpace.contains("儒释道三教")) {
            return "three-religions";
        }
        if ("none".equals(en) || "no religion".equals(en) || "nil".equals(en)
                || "無".equals(zh) || "无".equals(zh)) {
            return "none";
        }
        if (en.contains("other") || zhNoSpace.contains("其他")) return "other";
        return "other";
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
