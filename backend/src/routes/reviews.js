const express = require("express");
const Review = require("../models/Review");
const { buildSeedReviewsForSchool } = require("../utils/seedReviews");

const router = express.Router();
const COMMENT_REPLY_DEBUG = "COMMENT_REPLY_DEBUG";

function getUserReaction(doc, deviceUserId) {
  if (!deviceUserId) return "none";
  const likedBy = Array.isArray(doc.likedBy) ? doc.likedBy : [];
  const dislikedBy = Array.isArray(doc.dislikedBy) ? doc.dislikedBy : [];
  if (likedBy.includes(deviceUserId)) return "like";
  if (dislikedBy.includes(deviceUserId)) return "dislike";
  return "none";
}

function countsFromDoc(doc) {
  const likedBy = Array.isArray(doc.likedBy) ? doc.likedBy : null;
  const dislikedBy = Array.isArray(doc.dislikedBy) ? doc.dislikedBy : null;
  const likes = likedBy ? likedBy.length : (typeof doc.likes === "number" ? doc.likes : 0);
  const dislikes = dislikedBy ? dislikedBy.length : (typeof doc.dislikes === "number" ? doc.dislikes : 0);
  return { likes, dislikes };
}

function toTimestamp(doc, deviceUserId) {
  const createdAt = doc.createdAt ? new Date(doc.createdAt).getTime() : Date.now();
  const { likes, dislikes } = countsFromDoc(doc);
  const authorDeviceId = typeof doc.authorDeviceId === "string" ? doc.authorDeviceId : "";
  const isOwner = !!deviceUserId && !!authorDeviceId && authorDeviceId === deviceUserId;
  const isUserComment = typeof doc.isUserComment === "boolean" ? doc.isUserComment : (!doc.isSeeded && !!authorDeviceId);
  return {
    ...doc.toObject({ versionKey: false }),
    likes,
    dislikes,
    userReaction: getUserReaction(doc, deviceUserId),
    isOwner,
    isUserComment,
    timestamp: createdAt
  };
}

router.get("/:schoolId", async (req, res) => {
  try {
    const schoolId = String(req.params.schoolId || "").trim();
    const deviceUserId = String(req.query.deviceUserId || "").trim();
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
    const topLevelDocs = docs.filter((d) => !d.parentId);
    const totalReviews = topLevelDocs.length;
    const averageRating =
      totalReviews === 0 ? 0 : topLevelDocs.reduce((sum, r) => sum + (r.rating || 0), 0) / totalReviews;

    const replyCountMap = {};
    for (const d of docs) {
      if (!d.parentId) continue;
      const pid = String(d.parentId);
      replyCountMap[pid] = (replyCountMap[pid] || 0) + 1;
    }
    for (const key of Object.keys(replyCountMap)) {
      console.log(`${COMMENT_REPLY_DEBUG}: parentId=${key}, replyCount=${replyCountMap[key]}`);
    }

    return res.json({
      schoolId,
      averageRating,
      totalReviews,
      reviews: docs.map((d) => toTimestamp(d, deviceUserId))
    });
  } catch (err) {
    return res.status(500).json({ error: "Failed to load reviews" });
  }
});

router.post("/", async (req, res) => {
  try {
    const schoolId = String(req.body.schoolId || "").trim();
    const deviceUserId = String(req.body.deviceUserId || req.body.authorDeviceId || "").trim();
    const reviewerNameRaw = String(req.body.reviewerName || "").trim();
    const reviewerName = reviewerNameRaw ? reviewerNameRaw : "Guest User";
    const parentIdRaw = req.body.parentId;
    const parentId = parentIdRaw == null ? null : String(parentIdRaw).trim();
    const rating = Number(req.body.rating);
    const comment = String(req.body.comment || "").trim();

    if (!schoolId) {
      return res.status(400).json({ error: "schoolId is required" });
    }
    const isReply = !!parentId;
    if (isReply) {
      const parent = await Review.findById(parentId).lean();
      if (!parent) {
        return res.status(400).json({ error: "parent review not found" });
      }
    }
    if (!isReply && (!Number.isFinite(rating) || rating < 1 || rating > 5)) {
      return res.status(400).json({ error: "rating must be between 1 and 5" });
    }
    if (!comment) {
      return res.status(400).json({ error: "comment is required" });
    }

    const doc = await Review.create({
      schoolId,
      parentId: isReply ? parentId : null,
      reviewerName,
      rating: isReply ? 0 : Math.round(rating),
      comment,
      isSeeded: false,
      isUserComment: true,
      authorDeviceId: deviceUserId,
      likedBy: [],
      dislikedBy: [],
      likes: 0,
      dislikes: 0
    });

    if (isReply) {
      console.log(`${COMMENT_REPLY_DEBUG}: parentId=${parentId}, commentId=${doc._id}`);
    }

    return res.status(201).json({ review: toTimestamp(doc, deviceUserId) });
  } catch (err) {
    return res.status(500).json({ error: "Failed to create review" });
  }
});

router.post("/:reviewId/react", async (req, res) => {
  try {
    const reviewId = String(req.params.reviewId || "").trim();
    const action = String(req.body.action || "").trim();
    const deviceUserId = String(req.body.deviceUserId || "").trim();
    if (!reviewId) {
      return res.status(400).json({ error: "reviewId is required" });
    }
    if (!deviceUserId) {
      return res.status(400).json({ error: "deviceUserId is required" });
    }
    if (action !== "like" && action !== "dislike") {
      return res.status(400).json({ error: "action must be like or dislike" });
    }

    const doc = await Review.findById(reviewId);
    if (!doc) {
      return res.status(404).json({ error: "review not found" });
    }

    const likedBy = Array.isArray(doc.likedBy) ? doc.likedBy : [];
    const dislikedBy = Array.isArray(doc.dislikedBy) ? doc.dislikedBy : [];
    const hasLike = likedBy.includes(deviceUserId);
    const hasDislike = dislikedBy.includes(deviceUserId);

    // Mutual-exclusive toggle rules:
    // none -> like/dislike
    // like -> like cancels; dislike switches
    // dislike -> dislike cancels; like switches
    if (action === "like") {
      if (hasLike) {
        doc.likedBy = likedBy.filter((id) => id !== deviceUserId);
      } else {
        doc.likedBy = [...likedBy, deviceUserId];
        doc.dislikedBy = dislikedBy.filter((id) => id !== deviceUserId);
      }
    } else {
      if (hasDislike) {
        doc.dislikedBy = dislikedBy.filter((id) => id !== deviceUserId);
      } else {
        doc.dislikedBy = [...dislikedBy, deviceUserId];
        doc.likedBy = likedBy.filter((id) => id !== deviceUserId);
      }
    }

    doc.likes = Array.isArray(doc.likedBy) ? doc.likedBy.length : 0;
    doc.dislikes = Array.isArray(doc.dislikedBy) ? doc.dislikedBy.length : 0;
    await doc.save();

    const userReaction = getUserReaction(doc, deviceUserId);
    const { likes, dislikes } = countsFromDoc(doc);
    return res.json({
      reviewId,
      likes,
      dislikes,
      userReaction
    });
  } catch (err) {
    return res.status(500).json({ error: "Failed to react to review" });
  }
});

router.put("/:reviewId", async (req, res) => {
  try {
    const reviewId = String(req.params.reviewId || "").trim();
    const deviceUserId = String(req.body.deviceUserId || "").trim();
    const rating = Number(req.body.rating);
    const comment = String(req.body.comment || "").trim();

    if (!reviewId) return res.status(400).json({ error: "reviewId is required" });
    if (!deviceUserId) return res.status(400).json({ error: "deviceUserId is required" });
    if (!Number.isFinite(rating) || rating < 1 || rating > 5) {
      return res.status(400).json({ error: "rating must be between 1 and 5" });
    }
    if (!comment) return res.status(400).json({ error: "comment is required" });

    const doc = await Review.findById(reviewId);
    if (!doc) return res.status(404).json({ error: "review not found" });
    const authorDeviceId = typeof doc.authorDeviceId === "string" ? doc.authorDeviceId : "";
    if (!authorDeviceId || authorDeviceId !== deviceUserId) {
      return res.status(403).json({ error: "not allowed" });
    }

    doc.rating = Math.round(rating);
    doc.comment = comment;
    await doc.save();

    return res.json({ review: toTimestamp(doc, deviceUserId) });
  } catch (err) {
    return res.status(500).json({ error: "Failed to update review" });
  }
});

router.delete("/:reviewId", async (req, res) => {
  try {
    const reviewId = String(req.params.reviewId || "").trim();
    const deviceUserId = String(req.query.deviceUserId || req.body.deviceUserId || "").trim();
    if (!reviewId) return res.status(400).json({ error: "reviewId is required" });
    if (!deviceUserId) return res.status(400).json({ error: "deviceUserId is required" });

    const doc = await Review.findById(reviewId);
    if (!doc) return res.status(404).json({ error: "review not found" });
    const authorDeviceId = typeof doc.authorDeviceId === "string" ? doc.authorDeviceId : "";
    if (!authorDeviceId || authorDeviceId !== deviceUserId) {
      return res.status(403).json({ error: "not allowed" });
    }

    await Review.deleteOne({ _id: reviewId });
    return res.json({ ok: true, reviewId });
  } catch (err) {
    return res.status(500).json({ error: "Failed to delete review" });
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

