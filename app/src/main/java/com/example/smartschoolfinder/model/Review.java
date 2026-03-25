package com.example.smartschoolfinder.model;

public class Review {
    private String id;
    private String schoolId;
    private int rating;
    private String comment;
    private long timestamp;

    public Review() {
    }

    public Review(String id, String schoolId, int rating, String comment, long timestamp) {
        this.id = id;
        this.schoolId = schoolId;
        this.rating = rating;
        this.comment = comment;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getSchoolId() { return schoolId; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public long getTimestamp() { return timestamp; }
}
