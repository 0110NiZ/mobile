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

        for (School s : source) {
            String name = s.getName() == null ? "" : s.getName();
            String districtText = s.getDistrict() == null ? "" : s.getDistrict();
            String typeText = s.getType() == null ? "" : s.getType();

            boolean keywordOk = k.isEmpty() || name.toLowerCase().contains(k);
            boolean districtOk = "All".equals(districtValue) || districtText.equals(districtValue);
            boolean typeOk = "All".equals(typeValue) || typeText.equals(typeValue);

            if (keywordOk && districtOk && typeOk) {
                result.add(s);
            }
        }
        return result;
    }
}
