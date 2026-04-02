package com.example.smartschoolfinder.model;

import com.example.smartschoolfinder.utils.DistanceUtils;

public class School {
    /** EDB "School Code" (unique if present) */
    private String schoolCode;
    private String id;
    private String name;
    private String district;
    private String type;
    private String address;
    private String phone;
    private String tuition;
    private String transportBus;
    private String transportMinibus;
    private String transportMtr;
    private String transportConvenience;
    private double latitude;
    private double longitude;
    /** 与用户位置的距离（公里）；负数表示无法计算 */
    private double distance = -1;

    public School() {
    }

    public School(String schoolCode, String id, String name, String district, String type, String address, String phone,
                  String tuition, String transportBus, String transportMinibus, String transportMtr,
                  String transportConvenience, double latitude, double longitude) {
        this.schoolCode = schoolCode;
        this.id = id;
        this.name = name;
        this.district = district;
        this.type = type;
        this.address = address;
        this.phone = phone;
        this.tuition = tuition;
        this.transportBus = transportBus;
        this.transportMinibus = transportMinibus;
        this.transportMtr = transportMtr;
        this.transportConvenience = transportConvenience;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public School(String id, String name, String district, String type, String address, String phone,
                  String tuition, String transportBus, String transportMinibus, String transportMtr,
                  String transportConvenience, double latitude, double longitude) {
        this(null, id, name, district, type, address, phone, tuition, transportBus, transportMinibus, transportMtr, transportConvenience, latitude, longitude);
    }

    public String getSchoolCode() { return schoolCode; }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDistrict() { return district; }
    public String getType() { return type; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getTuition() { return tuition; }
    public String getTransportBus() { return transportBus; }
    public String getTransportMinibus() { return transportMinibus; }
    public String getTransportMtr() { return transportMtr; }
    public String getTransportConvenience() { return transportConvenience; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

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

    /** 根据用户经纬度写入 distance；坐标无效时置为 -1 */
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
        distance = DistanceUtils.calculateDistance(userLat, userLon, latitude, longitude);
    }

    public void clearDistance() {
        distance = -1;
    }
}
