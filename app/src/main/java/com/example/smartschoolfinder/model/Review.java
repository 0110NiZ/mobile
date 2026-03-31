package com.example.smartschoolfinder.model;

import com.google.gson.annotations.SerializedName;

public class Review {
    @SerializedName("_id")
    private String id;
    private String schoolId;
    private String reviewerName;
    private int rating;
    private String comment;
    private boolean isSeeded;
    private boolean isUserComment;
    private String authorDeviceId;
    private String userReaction; // like / dislike / none
    private boolean isOwner;
    private int likes;
    private int dislikes;
    private long timestamp;

    public Review() {
    }

    public Review(String id, String schoolId, String reviewerName, int rating, String comment, boolean isSeeded, int likes, int dislikes, long timestamp) {
        this.id = id;
        this.schoolId = schoolId;
        this.reviewerName = reviewerName;
        this.rating = rating;
        this.comment = comment;
        this.isSeeded = isSeeded;
        this.likes = likes;
        this.dislikes = dislikes;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public String getSchoolId() { return schoolId; }
    public String getReviewerName() { return reviewerName; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public boolean isSeeded() { return isSeeded; }
    public boolean isUserComment() { return isUserComment; }
    public String getAuthorDeviceId() { return authorDeviceId; }
    public String getUserReaction() { return userReaction; }
    public boolean isOwner() { return isOwner; }
    public int getLikes() { return likes; }
    public int getDislikes() { return dislikes; }
    public long getTimestamp() { return timestamp; }

    public void setLikes(int likes) { this.likes = likes; }
    public void setDislikes(int dislikes) { this.dislikes = dislikes; }
    public void setUserReaction(String userReaction) { this.userReaction = userReaction; }
    public void setOwner(boolean owner) { isOwner = owner; }
}
