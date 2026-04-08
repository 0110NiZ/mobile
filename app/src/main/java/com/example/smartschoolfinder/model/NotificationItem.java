package com.example.smartschoolfinder.model;

import com.google.gson.annotations.SerializedName;

public class NotificationItem {
    @SerializedName("_id")
    private String id;
    private String recipientDeviceId;
    private String actorDeviceId;
    private String type;
    private String schoolId;
    private String commentId;
    private String parentCommentId;
    private String message;
    private boolean isRead;
    private String createdAt;

    public String getId() { return id; }
    public String getRecipientDeviceId() { return recipientDeviceId; }
    public String getActorDeviceId() { return actorDeviceId; }
    public String getType() { return type; }
    public String getSchoolId() { return schoolId; }
    public String getCommentId() { return commentId; }
    public String getParentCommentId() { return parentCommentId; }
    public String getMessage() { return message; }
    public boolean isRead() { return isRead; }
    public String getCreatedAt() { return createdAt; }
}

