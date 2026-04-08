package com.example.smartschoolfinder.model;

import android.location.Location;

import com.example.smartschoolfinder.utils.LocationHelper;
import com.example.smartschoolfinder.utils.PinyinUtils;

public class School {
    /** EDB "School Code" (unique if present) */
    private String schoolCode;
    private String id;
    private String name;
    private String chineseName;
    private String district;
    private String type;
    private String gender;
    private String address;
    private String chineseAddress;
    private String phone;
    private String tuition;
    private String website;
    private String religion;
    private String chineseReligion;
    private String transportBus;
    private String transportMinibus;
    private String transportMtr;
    private String transportConvenience;
    private String cachedSortKey;
    private String cachedSortInitial;
    private String cachedSortLocale;
    private double latitude;
    private double longitude;
    /** 与用户位置的距离（公里）；负数表示无法计算 */
    private double distance = -1;

    public School() {
    }

    public School(String schoolCode, String id, String name, String district, String type, String gender, String address, String phone,
                  String tuition, String transportBus, String transportMinibus, String transportMtr,
                  String transportConvenience, double latitude, double longitude) {
        this(schoolCode, id, name, null, district, type, gender, address, null, phone, tuition, transportBus, transportMinibus, transportMtr, transportConvenience, latitude, longitude);
    }

    public School(String schoolCode, String id, String name, String chineseName, String district, String type, String gender,
                  String address, String chineseAddress, String phone, String tuition, String transportBus, String transportMinibus,
                  String transportMtr, String transportConvenience, double latitude, double longitude) {
        this.schoolCode = schoolCode;
        this.id = id;
        this.name = name;
        this.chineseName = chineseName;
        this.district = district;
        this.type = type;
        this.gender = gender;
        this.address = address;
        this.chineseAddress = chineseAddress;
        this.phone = phone;
        this.tuition = tuition;
        this.transportBus = transportBus;
        this.transportMinibus = transportMinibus;
        this.transportMtr = transportMtr;
        this.transportConvenience = transportConvenience;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public School(String id, String name, String district, String type, String gender, String address, String phone,
                  String tuition, String transportBus, String transportMinibus, String transportMtr,
                  String transportConvenience, double latitude, double longitude) {
        this(null, id, name, null, district, type, gender, address, null, phone, tuition, transportBus, transportMinibus, transportMtr, transportConvenience, latitude, longitude);
    }

    public String getSchoolCode() { return schoolCode; }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getChineseName() { return chineseName; }
    public String getDistrict() { return district; }
    public String getType() { return type; }
    public String getGender() { return gender; }
    public String getAddress() { return address; }
    public String getChineseAddress() { return chineseAddress; }
    public String getPhone() { return phone; }
    public String getTuition() { return tuition; }
    public String getWebsite() { return website; }
    public String getReligion() { return religion; }
    public String getChineseReligion() { return chineseReligion; }
    public String getTransportBus() { return transportBus; }
    public String getTransportMinibus() { return transportMinibus; }
    public String getTransportMtr() { return transportMtr; }
    public String getTransportConvenience() { return transportConvenience; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public void setChineseName(String chineseName) { this.chineseName = chineseName; }
    public void setWebsite(String website) { this.website = website; }
    public void setReligion(String religion) { this.religion = religion; }
    public void setChineseReligion(String chineseReligion) { this.chineseReligion = chineseReligion; }
    public String getCachedSortKey() { return cachedSortKey; }
    public String getCachedSortInitial() { return cachedSortInitial; }
    public String getCachedSortLocale() { return cachedSortLocale; }
    public void setCachedSortMeta(String localeTag, String sortKey, String sortInitial) {
        this.cachedSortLocale = localeTag;
        this.cachedSortKey = sortKey;
        this.cachedSortInitial = sortInitial;
    }
    public void clearCachedSortMeta() {
        this.cachedSortLocale = null;
        this.cachedSortKey = null;
        this.cachedSortInitial = null;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    /** 是否有可用的距离值（用于列表展示与排序） */
    public boolean hasValidDistance() {
        return distance >= 0 && !Double.isNaN(distance) && !Double.isInfinite(distance);
    }

    /**
     * 学校经纬度是否可信（非 NaN、在合法范围内，且排除明显的“未填” 0,0）。
     */
    public boolean hasValidCoordinates() {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return false;
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return false;
        }
        if (Math.abs(latitude) < 1e-6 && Math.abs(longitude) < 1e-6) {
            return false;
        }
        return true;
    }

    /** 根据用户经纬度写入 distance(km)；坐标无效时置为 -1 */
    public void updateDistanceFrom(double userLat, double userLon) {
        if (!hasValidCoordinates()) {
            distance = -1;
            return;
        }
        if (Double.isNaN(userLat) || Double.isNaN(userLon)
                || userLat < -90 || userLat > 90
                || userLon < -180 || userLon > 180) {
            distance = -1;
            return;
        }
        // Hard stop for invalid location fallback values: never compute from (0,0).
        if (Math.abs(userLat) < 1e-6 && Math.abs(userLon) < 1e-6) {
            distance = -1;
            return;
        }
        float[] results = new float[1];
        Location.distanceBetween(userLat, userLon, latitude, longitude, results);
        float meters = results[0];
        if (Float.isNaN(meters) || Float.isInfinite(meters) || meters < 0f) {
            distance = -1;
            return;
        }
        double km = meters / 1000.0;
        // Extra safeguard: if reference coordinate is corrupted and yields cross-ocean distance,
        // recalculate from HK fallback center so UI never shows 10000+ km.
        if (km > 1000.0) {
            float[] fallbackResults = new float[1];
            Location.distanceBetween(
                    LocationHelper.HK_DEFAULT_LATITUDE,
                    LocationHelper.HK_DEFAULT_LONGITUDE,
                    latitude,
                    longitude,
                    fallbackResults
            );
            float fallbackMeters = fallbackResults[0];
            if (Float.isNaN(fallbackMeters) || Float.isInfinite(fallbackMeters) || fallbackMeters < 0f) {
                distance = -1;
                return;
            }
            km = fallbackMeters / 1000.0;
        }
        // Persist distance in km for direct UI binding.
        distance = km;
    }

    public void clearDistance() {
        distance = -1;
    }

    public String getFirstLetter() {
        return PinyinUtils.firstLetter(name);
    }
}
