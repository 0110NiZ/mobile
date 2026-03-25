package com.example.smartschoolfinder.network;

import android.os.Handler;
import android.os.Looper;

import com.example.smartschoolfinder.model.School;

import java.util.ArrayList;
import java.util.List;

public class ApiClient {

    public void fetchSchools(ApiCallback<List<School>> callback) {
        new Thread(() -> {
            try {
                Thread.sleep(1200);
                List<School> schools = mockSchools();
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(schools));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Failed to load schools"));
            }
        }).start();
    }

    private List<School> mockSchools() {
        List<School> list = new ArrayList<>();
        list.add(new School("1", "St. Mary School", "Hong Kong Island", "Secondary", "1 Queen's Road Central", "25250001", "$32000",
                "10, 12A", "1M", "Central", "High", 22.2819, 114.1589));
        list.add(new School("2", "Kowloon Science College", "Kowloon", "Secondary", "88 Nathan Road", "25250002", "$28000",
                "2, 6", "6A", "Jordan", "Medium", 22.3045, 114.1717));
        list.add(new School("3", "New Territories Primary", "New Territories", "Primary", "20 Sha Tin Centre Street", "25250003", "$18000",
                "40X", "65K", "Sha Tin", "High", 22.3835, 114.1887));
        list.add(new School("4", "Island Kids Academy", "Hong Kong Island", "Primary", "15 Causeway Bay Road", "25250004", "$26000",
                "8X", "5", "Causeway Bay", "Low", 22.2802, 114.1849));
        list.add(new School("5", "Harbour International School", "Kowloon", "International", "5 Austin Road West", "25250005", "$76000",
                "215X", "26", "Kowloon", "Medium", 22.3027, 114.1611));
        return list;
    }
}
