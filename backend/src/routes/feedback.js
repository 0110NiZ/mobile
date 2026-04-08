const express = require("express");
const Feedback = require("../models/Feedback");

const router = express.Router();

router.post("/", async (req, res) => {
  try {
    const rating = Number(req.body.rating);
    const comment = String(req.body.comment || "").trim();

    if (!Number.isFinite(rating) || rating < 1 || rating > 5) {
      return res.status(400).json({ error: "rating must be between 1 and 5" });
    }
    if (!comment) {
      return res.status(400).json({ error: "comment is required" });
    }

    const doc = await Feedback.create({
      rating: Math.round(rating),
      comment,
      source: "app_feedback"
    });

    return res.status(201).json({
      ok: true,
      feedback: {
        _id: doc._id,
        rating: doc.rating,
        comment: doc.comment,
        source: doc.source,
        createdAt: doc.createdAt,
        updatedAt: doc.updatedAt
      }
    });
  } catch (err) {
    return res.status(500).json({ error: "Failed to submit feedback" });
  }
});

router.get("/", async (req, res) => {
  try {
    const docs = await Feedback.find({ source: "app_feedback" })
      .sort({ createdAt: -1 })
      .lean();
    return res.json({ ok: true, total: docs.length, items: docs });
  } catch (err) {
    return res.status(500).json({ error: "Failed to load feedback" });
  }
});

module.exports = router;

