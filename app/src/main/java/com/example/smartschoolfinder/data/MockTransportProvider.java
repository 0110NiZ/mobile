package com.example.smartschoolfinder.data;

import com.example.smartschoolfinder.model.School;
import com.example.smartschoolfinder.model.TransportInfo;

public class MockTransportProvider {
    public static TransportInfo buildTransportInfo(School school) {
        if (school == null) {
            return fallback();
        }

        String district = school.getDistrict() == null ? "" : school.getDistrict().trim().toLowerCase();

        // Hong Kong style mock data by district (including Green Minibus routes).
        if (district.contains("tai po")) {
            return new TransportInfo(
                    "Tai Po Market Station", "800m",
                    "Tai Yuen Estate Stop", "150m",
                    "Green Minibus 20A, 20B", "100m, 1 min walk",
                    "⭐⭐⭐⭐"
            );
        }
        if (district.contains("sha tin")) {
            return new TransportInfo(
                    "Sha Tin Station", "900m",
                    "Sha Tin Central Bus Terminus", "220m",
                    "Green Minibus 65A, 65K", "180m, 2 min walk",
                    "⭐⭐⭐⭐"
            );
        }
        if (district.contains("yuen long")) {
            return new TransportInfo(
                    "Long Ping Station", "1.1km",
                    "Yuen Long (West) Bus Stop", "260m",
                    "Green Minibus 33, 39M", "130m, 2 min walk",
                    "⭐⭐⭐"
            );
        }
        if (district.contains("tuen mun")) {
            return new TransportInfo(
                    "Tuen Mun Station", "1.2km",
                    "Town Centre Bus Stop", "300m",
                    "Green Minibus 43, 44", "170m, 2 min walk",
                    "⭐⭐⭐"
            );
        }
        if (district.contains("kwun tong")) {
            return new TransportInfo(
                    "Kwun Tong Station", "650m",
                    "Kwun Tong Road Stop", "120m",
                    "Green Minibus 22M, 47M", "110m, 1 min walk",
                    "⭐⭐⭐⭐"
            );
        }

        return fallback();
    }

    private static TransportInfo fallback() {
        return new TransportInfo(
                "N/A", "N/A",
                "N/A", "N/A",
                "N/A", "N/A",
                "N/A"
        );
    }
}
