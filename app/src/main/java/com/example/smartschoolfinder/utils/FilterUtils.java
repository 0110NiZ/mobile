package com.example.smartschoolfinder.utils;

import com.example.smartschoolfinder.model.School;

import java.util.ArrayList;
import java.util.List;

public class FilterUtils {
    public static List<School> filter(List<School> source, String keyword, String district, String type) {
        List<School> result = new ArrayList<>();
        String keywordRaw = keyword == null ? "" : keyword.trim();
        String k = keywordRaw.toLowerCase();
        String[] keywordTokens = keywordRaw.isEmpty() ? new String[0] : keywordRaw.split("\\s+");
        String districtValue = district == null ? "All" : district;
        String typeValue = type == null ? "All" : type;
        String districtNorm = normalizeDistrict(districtValue);
        String typeNorm = normalizeType(typeValue);

        for (School s : source) {
            String nameEn = s.getName() == null ? "" : s.getName();
            String nameZh = s.getChineseName() == null ? "" : s.getChineseName();
            String nameEnLower = nameEn.toLowerCase();
            String districtText = s.getDistrict() == null ? "" : s.getDistrict();
            String typeText = buildTypeHint(s);

            boolean keywordOk = k.isEmpty() || nameEnLower.contains(k) || nameZh.contains(keywordRaw);
            if (!keywordOk && keywordTokens.length > 1) {
                boolean allTokensMatch = true;
                for (String token : keywordTokens) {
                    String tokenTrimmed = token == null ? "" : token.trim();
                    if (tokenTrimmed.isEmpty()) continue;
                    String tokenLower = tokenTrimmed.toLowerCase();
                    boolean tokenMatch = nameEnLower.contains(tokenLower) || nameZh.contains(tokenTrimmed);
                    if (!tokenMatch) {
                        allTokensMatch = false;
                        break;
                    }
                }
                keywordOk = allTokensMatch;
            }
            boolean districtOk = "All".equalsIgnoreCase(districtValue) || normalizeDistrict(districtText).equals(districtNorm);
            boolean typeOk = "All".equalsIgnoreCase(typeValue) || normalizeType(typeText).equals(typeNorm);

            if (keywordOk && districtOk && typeOk) {
                result.add(s);
            }
        }
        return result;
    }

    public static String normalizeDistrict(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.isEmpty() || "all".equals(s)) return "all";
        if ("unknown".equals(s) || "未知".equals(s)) return "unknown";
        if (s.contains("hong kong") || s.contains("hk island") || s.contains("island")
                || s.contains("central and western") || s.contains("wan chai")
                || s.contains("eastern") || s.contains("southern")
                || s.contains("香港") || s.contains("港島") || s.contains("香港島")
                || s.contains("中西區") || s.contains("灣仔") || s.contains("东区")
                || s.contains("東區") || s.contains("南區")) {
            return "hong kong island";
        }
        if (s.contains("kowloon")
                || s.contains("yau tsim mong") || s.contains("sham shui po")
                || s.contains("kowloon city") || s.contains("wong tai sin")
                || s.contains("kwun tong")
                || s.contains("油尖旺") || s.contains("深水埗")
                || s.contains("九龍城") || s.contains("黄大仙")
                || s.contains("黃大仙") || s.contains("觀塘") || s.contains("观塘")) {
            return "kowloon";
        }
        if (s.contains("new territories") || s.contains("new territory") || s.contains("nt")
                || s.contains("tsuen wan") || s.contains("tuen mun")
                || s.contains("yuen long") || s.contains("north")
                || s.contains("tai po") || s.contains("sha tin")
                || s.contains("sai kung") || s.contains("islands")
                || s.contains("kwai tsing")
                || s.contains("新界") || s.contains("荃灣") || s.contains("荃湾")
                || s.contains("屯門") || s.contains("屯门")
                || s.contains("元朗") || s.contains("北區")
                || s.contains("北区") || s.contains("大埔")
                || s.contains("沙田") || s.contains("西貢")
                || s.contains("西贡") || s.contains("離島")
                || s.contains("离岛") || s.contains("葵青")) {
            return "new territories";
        }
        return "unknown";
    }

    public static String normalizeSubDistrict(String v) {
        if (v == null) return "unknown";
        String s = v.trim().toLowerCase();
        if (s.isEmpty() || "unknown".equals(s) || "未知".equals(s)) return "unknown";

        if (containsAny(s, "central and western", "central western", "中西區", "中西区")) return "central and western";
        if (containsAny(s, "wan chai", "wanchai", "灣仔", "湾仔")) return "wan chai";
        if (containsAny(s, "eastern", "東區", "东区")) return "eastern";
        if (containsAny(s, "southern", "南區", "南区")) return "southern";

        if (containsAny(s, "yau tsim mong", "油尖旺")) return "yau tsim mong";
        if (containsAny(s, "sham shui po", "深水埗")) return "sham shui po";
        if (containsAny(s, "kowloon city", "九龍城", "九龙城")) return "kowloon city";
        if (containsAny(s, "wong tai sin", "黃大仙", "黄大仙")) return "wong tai sin";
        if (containsAny(s, "kwun tong", "觀塘", "观塘")) return "kwun tong";

        if (containsAny(s, "islands", "離島", "离岛")) return "islands";
        if (containsAny(s, "kwai tsing", "葵青")) return "kwai tsing";
        if (containsAny(s, "tsuen wan", "荃灣", "荃湾")) return "tsuen wan";
        if (containsAny(s, "tuen mun", "屯門", "屯门")) return "tuen mun";
        if (containsAny(s, "yuen long", "元朗")) return "yuen long";
        if (containsAny(s, "north", "北區", "北区")) return "north";
        if (containsAny(s, "tai po", "大埔")) return "tai po";
        if (containsAny(s, "sha tin", "shatin", "沙田")) return "sha tin";
        if (containsAny(s, "sai kung", "西貢", "西贡")) return "sai kung";

        return "unknown";
    }

    public static String normalizeType(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.isEmpty() || "all".equals(s)) return "all";
        if (s.contains("kindergarten-cum-child care")
                || s.contains("kindergarten cum child care")
                || s.contains("kindergarten and child care")
                || s.contains("child care centre")
                || s.contains("child care center")
                || s.contains("幼稚園暨幼兒中心")
                || s.contains("幼稚园暨幼儿中心")) return "kindergarten_childcare";
        if (s.contains("primary") || s.contains("pri") || s.contains("小學") || s.contains("小学")) return "primary";
        if (s.contains("secondary") || s.contains("sec") || s.contains("中學") || s.contains("中学")) return "secondary";
        if (s.contains("kindergarten") || s.contains("kg")
                || s.contains("幼稚園") || s.contains("幼稚园")
                || s.contains("幼兒") || s.contains("幼儿")) return "kindergarten";
        if (s.contains("special") || s.contains("特殊")) return "special";
        if (s.contains("international") || s.contains("intl")
                || s.contains("國際") || s.contains("国际")) return "international";
        if (s.contains("university") || s.contains("universities")
                || s.contains("higher education")
                || s.contains("大學") || s.contains("大学")
                || s.contains("專上教育") || s.contains("专上教育")) return "university";
        return s;
    }

    public static String normalizeSession(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.isEmpty() || "all".equals(s)) return "all";
        if (containsAny(s, "a.m.", "am", "morning", "上午")) return "am";
        if (containsAny(s, "p.m.", "pm", "afternoon", "下午")) return "pm";
        if (containsAny(s, "evening", "night", "夜校", "晚間", "晚上")) return "evening";
        if (containsAny(s, "whole day", "wholeday", "full day", "全日")) return "whole_day";
        return s;
    }

    public static String normalizeFinanceType(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.isEmpty() || "all".equals(s)) return "all";
        if (containsAny(s, "aided", "資助")) return "aided";
        if (containsAny(s, "caput", "按位津貼", "按位津贴")) return "caput";
        if (containsAny(s, "direct subsidy scheme", "dss", "直接資助計劃", "直接资助计划")) return "dss";
        if (containsAny(s, "english schools foundation", "esf", "英基學校協會", "英基学校协会")) return "esf";
        if (containsAny(s, "government", "官立")) return "government";
        if (containsAny(s, "private independent sch scheme", "private independent school scheme", "piss", "私立獨立學校計劃", "私立独立学校计划")) return "piss";
        if (containsAny(s, "private", "私立")) return "private";
        return s;
    }

    private static String buildTypeHint(School s) {
        if (s == null) return "";
        String type = s.getType() == null ? "" : s.getType();
        String enName = s.getName() == null ? "" : s.getName();
        String zhName = s.getChineseName() == null ? "" : s.getChineseName();
        return (type + " " + enName + " " + zhName).trim();
    }

    private static boolean containsAny(String s, String... needles) {
        if (s == null || needles == null) return false;
        for (String needle : needles) {
            if (needle == null || needle.isEmpty()) continue;
            if (s.contains(needle.toLowerCase())) return true;
        }
        return false;
    }
}
