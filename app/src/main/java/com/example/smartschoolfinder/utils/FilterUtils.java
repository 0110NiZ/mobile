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

    private static String normalizeDistrict(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.isEmpty() || "all".equals(s)) return "all";
        if (s.contains("hong kong") || s.contains("hk island") || s.contains("island")) return "hong kong island";
        if (s.contains("kowloon")) return "kowloon";
        if (s.contains("new territories") || s.contains("new territory") || s.contains("nt")) return "new territories";
        return s;
    }

    private static String normalizeType(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.isEmpty() || "all".equals(s)) return "all";
        if (s.contains("primary")) return "primary";
        if (s.contains("secondary")) return "secondary";
        if (s.contains("international")) return "international";
        return s;
    }
}
