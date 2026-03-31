const express = require("express");
const Review = require("../models/Review");
const { buildSeedReviewsForSchool } = require("../utils/seedReviews");

const router = express.Router();

function toTimestamp(doc) {
  const createdAt = doc.createdAt ? new Date(doc.createdAt).getTime() : Date.now();
  return {
    ...doc.toObject({ versionKey: false }),
    likes: typeof doc.likes === "number" ? doc.likes : 0,
    dislikes: typeof doc.dislikes === "number" ? doc.dislikes : 0,
    timestamp: createdAt
  };
}

router.get("/:schoolId", async (req, res) => {
  try {
    const schoolId = String(req.params.schoolId || "").trim();
    if (!schoolId) {
      return res.status(400).json({ error: "schoolId is required" });
    }

    const sort = String(req.query.sort || "latest").trim();
    let sortSpec = { createdAt: -1 };
    if (sort === "high_rating") {
      sortSpec = { rating: -1, createdAt: -1 };
    } else if (sort === "low_rating") {
      sortSpec = { rating: 1, createdAt: -1 };
    } else {
      sortSpec = { createdAt: -1 };
    }

    const docs = await Review.find({ schoolId }).sort(sortSpec).lean(false);
    const totalReviews = docs.length;
    const averageRating =
      totalReviews === 0 ? 0 : docs.reduce((sum, r) => sum + (r.rating || 0), 0) / totalReviews;

    return res.json({
      schoolId,
      averageRating,
      totalReviews,
      reviews: docs.map(toTimestamp)
    });
  } catch (err) {
    return res.status(500).json({ error: "Failed to load reviews" });
  }
});

router.post("/", async (req, res) => {
  try {
    const schoolId = String(req.body.schoolId || "").trim();
    const reviewerNameRaw = String(req.body.reviewerName || "").trim();
    const reviewerName = reviewerNameRaw ? reviewerNameRaw : "Guest User";
    const rating = Number(req.body.rating);
    const comment = String(req.body.comment || "").trim();

    if (!schoolId) {
      return res.status(400).json({ error: "schoolId is required" });
    }
    if (!Number.isFinite(rating) || rating < 1 || rating > 5) {
      return res.status(400).json({ error: "rating must be between 1 and 5" });
    }
    if (!comment) {
      return res.status(400).json({ error: "comment is required" });
    }

    const doc = await Review.create({
      schoolId,
      reviewerName,
      rating: Math.round(rating),
      comment,
      isSeeded: false,
      likes: 0,
      dislikes: 0
    });

    return res.status(201).json({ review: toTimestamp(doc) });
  } catch (err) {
    return res.status(500).json({ error: "Failed to create review" });
  }
});

router.post("/:reviewId/react", async (req, res) => {
  try {
    const reviewId = String(req.params.reviewId || "").trim();
    const action = String(req.body.action || "").trim();
    if (!reviewId) {
      return res.status(400).json({ error: "reviewId is required" });
    }
    if (action !== "like" && action !== "dislike") {
      return res.status(400).json({ error: "action must be like or dislike" });
    }

    const update =
      action === "like"
        ? { $inc: { likes: 1 } }
        : { $inc: { dislikes: 1 } };

    const doc = await Review.findByIdAndUpdate(reviewId, update, { new: true });
    if (!doc) {
      return res.status(404).json({ error: "review not found" });
    }
    return res.json({
      reviewId,
      likes: typeof doc.likes === "number" ? doc.likes : 0,
      dislikes: typeof doc.dislikes === "number" ? doc.dislikes : 0
    });
  } catch (err) {
    return res.status(500).json({ error: "Failed to react to review" });
  }
});

router.post("/seed", async (req, res) => {
  try {
    const schools = Array.isArray(req.body.schools) ? req.body.schools : [];
    if (schools.length === 0) {
      return res.status(400).json({ error: "schools must be a non-empty array" });
    }

    let seeded = 0;
    let skipped = 0;
    let toppedUp = 0;

    for (const s of schools) {
      const schoolId = String(s.id || "").trim();
      const schoolName = String(s.name || "").trim();
      if (!schoolId) continue;

      const existingSeeded = await Review.find({ schoolId, isSeeded: true }).select("comment").lean();
      const seededCount = existingSeeded.length;
      if (seededCount >= 20) {
        skipped += 1;
        continue;
      }

      const existingComments = existingSeeded.map((r) => r.comment);
      const need = 20 - seededCount;
      const docs = buildSeedReviewsForSchool({ schoolId, schoolName, count: need, existingComments });
      if (docs.length > 0) {
        await Review.insertMany(docs);
      }

      if (seededCount === 0) {
        seeded += 1;
      } else {
        toppedUp += 1;
      }
    }

    return res.json({ ok: true, seededSchools: seeded, toppedUpSchools: toppedUp, skippedSchools: skipped });
  } catch (err) {
    return res.status(500).json({ error: "Failed to seed reviews" });
  }
});

module.exports = router;

