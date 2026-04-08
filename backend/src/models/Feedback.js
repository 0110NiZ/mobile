const mongoose = require("mongoose");

const FeedbackSchema = new mongoose.Schema(
  {
    rating: { type: Number, min: 1, max: 5, required: true },
    comment: { type: String, required: true, trim: true },
    source: { type: String, default: "app_feedback", required: true }
  },
  { timestamps: true, collection: "feedback" }
);

module.exports = mongoose.model("Feedback", FeedbackSchema);

