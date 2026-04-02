package com.example.smartschoolfinder.model;

public class TransportInfo {
    private final String mtrStation;
    private final String mtrDistance;
    private final String busStation;
    private final String busDistance;
    private final String minibusStation;
    private final String minibusDistance;
    private final String convenienceScore;

    public TransportInfo(String mtrStation, String mtrDistance,
                         String busStation, String busDistance,
                         String minibusStation, String minibusDistance,
                         String convenienceScore) {
        this.mtrStation = safe(mtrStation);
        this.mtrDistance = safe(mtrDistance);
        this.busStation = safe(busStation);
        this.busDistance = safe(busDistance);
        this.minibusStation = safe(minibusStation);
        this.minibusDistance = safe(minibusDistance);
        this.convenienceScore = safe(convenienceScore);
    }

    public String getMtrStation() {
        return mtrStation;
    }

    public String getMtrDistance() {
        return mtrDistance;
    }

    public String getBusStation() {
        return busStation;
    }

    public String getBusDistance() {
        return busDistance;
    }

    public String getMinibusStation() {
        return minibusStation;
    }

    public String getMinibusDistance() {
        return minibusDistance;
    }

    public String getConvenienceScore() {
        return convenienceScore;
    }

    private String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "N/A";
        return value.trim();
    }
}

