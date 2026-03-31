const mongoose = require("mongoose");

const ReviewSchema = new mongoose.Schema(
  {
    schoolId: { type: String, required: true, index: true },
    reviewerName: { type: String, default: "Guest User" },
    rating: { type: Number, min: 1, max: 5, required: true },
    comment: { type: String, required: true },
    isSeeded: { type: Boolean, default: false },
    likes: { type: Number, default: 0 },
    dislikes: { type: Number, default: 0 }
  },
  { timestamps: true, collection: "reviews" }
);

module.exports = mongoose.model("Review", ReviewSchema);

