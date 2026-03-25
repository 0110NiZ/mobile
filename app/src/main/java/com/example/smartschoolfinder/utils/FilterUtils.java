package com.example.smartschoolfinder.utils;

import com.example.smartschoolfinder.model.School;

import java.util.ArrayList;
import java.util.List;

public class FilterUtils {
    public static List<School> filter(List<School> source, String keyword, String district, String type) {
        List<School> result = new ArrayList<>();
        String k = keyword == null ? "" : keyword.trim().toLowerCase();

        for (School s : source) {
            boolean keywordOk = k.isEmpty() || s.getName().toLowerCase().contains(k);
            boolean districtOk = "All".equals(district) || s.getDistrict().equals(district);
            boolean typeOk = "All".equals(type) || s.getType().equals(type);

            if (keywordOk && districtOk && typeOk) {
                result.add(s);
            }
        }
        return result;
    }
}
