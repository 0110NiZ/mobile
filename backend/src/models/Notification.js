const mongoose = require("mongoose");

const NotificationSchema = new mongoose.Schema(
  {
    recipientDeviceId: { type: String, required: true, index: true },
    actorDeviceId: { type: String, default: "" },
    type: { type: String, enum: ["like", "dislike", "reply"], required: true, index: true },
    schoolId: { type: String, default: "" },
    commentId: { type: String, default: "" },
    parentCommentId: { type: String, default: "" },
    message: { type: String, default: "" },
    isRead: { type: Boolean, default: false, index: true }
  },
  { timestamps: true, collection: "notifications" }
);

module.exports = mongoose.model("Notification", NotificationSchema);

