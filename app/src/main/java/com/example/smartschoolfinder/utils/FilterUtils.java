package com.example.smartschoolfinder.utils;

import com.example.smartschoolfinder.model.School;

import java.util.ArrayList;
import java.util.List;

public class FilterUtils {
    public static List<School> filter(List<School> source, String keyword, String district, String type) {
        List<School> result = new ArrayList<>();
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        String districtValue = district == null ? "All" : district;
        String typeValue = type == null ? "All" : type;
        String districtNorm = normalizeDistrict(districtValue);
        String typeNorm = normalizeType(typeValue);

        for (School s : source) {
            String name = s.getName() == null ? "" : s.getName();
            String districtText = s.getDistrict() == null ? "" : s.getDistrict();
            String typeText = s.getType() == null ? "" : s.getType();

            boolean keywordOk = k.isEmpty() || name.toLowerCase().contains(k);
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

    public static String normalizeType(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.isEmpty() || "all".equals(s)) return "all";
        if (s.contains("primary") || s.contains("pri") || s.contains("小學") || s.contains("小学")) return "primary";
        if (s.contains("secondary") || s.contains("sec") || s.contains("中學") || s.contains("中学")) return "secondary";
        if (s.contains("kindergarten") || s.contains("kg")
                || s.contains("幼稚園") || s.contains("幼稚园")
                || s.contains("幼兒") || s.contains("幼儿")) return "kindergarten";
        if (s.contains("special") || s.contains("特殊")) return "special";
        if (s.contains("international") || s.contains("intl")
                || s.contains("國際") || s.contains("国际")) return "international";
        return s;
    }
}
