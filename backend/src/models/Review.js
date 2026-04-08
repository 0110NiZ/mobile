const mongoose = require("mongoose");

const ReviewSchema = new mongoose.Schema(
  {
    schoolId: { type: String, required: true, index: true },
    parentId: { type: String, default: null, index: true },
    reviewerName: { type: String, default: "Guest User" },
    rating: { type: Number, min: 0, max: 5, required: true },
    comment: { type: String, required: true },
    isSeeded: { type: Boolean, default: false },
    isUserComment: { type: Boolean, default: false },
    authorDeviceId: { type: String, default: "" },
    // For mutual-exclusive reactions per device user
    likedBy: { type: [String], default: [] },
    dislikedBy: { type: [String], default: [] },
    likes: { type: Number, default: 0 },
    dislikes: { type: Number, default: 0 }
  },
  { timestamps: true, collection: "reviews" }
);

module.exports = mongoose.model("Review", ReviewSchema);

