package com.example.smartschoolfinder.utils;

/**
 * 使用 Haversine 公式计算地球表面两点间的大圆距离（适合短距离估算）。
 */
public final class DistanceUtils {

    /** 地球平均半径（公里），用于 Haversine 公式 */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private DistanceUtils() {
    }

    /**
     * 计算两点经纬度之间的距离。
     *
     * @param lat1 起点纬度（度）
     * @param lon1 起点经度（度）
     * @param lat2 终点纬度（度）
     * @param lon2 终点经度（度）
     * @return 距离（公里），保留一位小数
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        // Haversine: a = sin²(Δφ/2) + cos φ1 ⋅ cos φ2 ⋅ sin²(Δλ/2)
        double sinHalfLat = Math.sin(deltaLat / 2);
        double sinHalfLon = Math.sin(deltaLon / 2);
        double a = sinHalfLat * sinHalfLat
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinHalfLon * sinHalfLon;
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double km = EARTH_RADIUS_KM * c;
        // 保留一位小数，便于列表展示
        return Math.round(km * 10.0) / 10.0;
    }
}
