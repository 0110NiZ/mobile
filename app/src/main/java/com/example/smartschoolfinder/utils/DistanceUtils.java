package com.example.smartschoolfinder.utils;

/**
 * 使用 Haversine 公式计算地球表面两点间的大圆距离（适合短距离估算）。
 */
public final class DistanceUtils {

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
        final int R = 6371; // Earth radius in km

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
