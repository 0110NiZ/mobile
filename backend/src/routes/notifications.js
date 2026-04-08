const express = require("express");
const Notification = require("../models/Notification");

const router = express.Router();

router.get("/", async (req, res) => {
  try {
    const recipientDeviceId = String(req.query.recipientDeviceId || "").trim();
    if (!recipientDeviceId) {
      return res.status(400).json({ error: "recipientDeviceId is required" });
    }
    const docs = await Notification.find({ recipientDeviceId })
      .sort({ createdAt: -1 })
      .lean();
    return res.json({ notifications: docs });
  } catch (err) {
    return res.status(500).json({ error: "Failed to load notifications" });
  }
});

router.post("/mark-read", async (req, res) => {
  try {
    const recipientDeviceId = String(req.body.recipientDeviceId || "").trim();
    if (!recipientDeviceId) {
      return res.status(400).json({ error: "recipientDeviceId is required" });
    }
    await Notification.updateMany({ recipientDeviceId, isRead: false }, { $set: { isRead: true } });
    return res.json({ ok: true });
  } catch (err) {
    return res.status(500).json({ error: "Failed to mark notifications as read" });
  }
});

module.exports = router;

