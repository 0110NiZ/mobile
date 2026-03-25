package com.example.smartschoolfinder.model;

public class School {
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

    public School() {
    }

    public School(String id, String name, String district, String type, String address, String phone,
                  String tuition, String transportBus, String transportMinibus, String transportMtr,
                  String transportConvenience, double latitude, double longitude) {
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
}
