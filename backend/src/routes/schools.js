const express = require("express");
const mongoose = require("mongoose");
const {
  cachedMTRStops,
  cachedBusStops,
  cachedMinibusStops,
  calculateHaversineDistance
} = require("../services/TransportService");
const { ensureEdbCache, findEdbBySchoolId } = require("../services/edbSchoolsCache");

const router = express.Router();

function toNumber(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function pickNearest(items) {
  if (!Array.isArray(items) || items.length === 0) return null;
  return items.sort((a, b) => a.distance - b.distance)[0];
}

async function findSchoolById(id) {
  const schools = mongoose.connection.db.collection("schools");
  const candidates = [
    { id },
    { schoolId: id },
    { schoolCode: id },
    { _id: id },
    { "SCHOOL NO.": id },
    { "SCHOOL CODE": id }
  ];
  for (const q of candidates) {
    const doc = await schools.findOne(q);
    if (doc) return doc;
  }
  return null;
}

function extractCoordinates(doc) {
  if (!doc) return { lat: null, lng: null };
  const lat = toNumber(doc.latitude ?? doc.lat ?? doc.LATITUDE);
  const lng = toNumber(doc.longitude ?? doc.lng ?? doc.long ?? doc.lon ?? doc.LONGITUDE);
  return { lat, lng };
}

router.get("/:id/transport", async (req, res) => {
  try {
    const id = String(req.params.id || "").trim();
    if (!id) return res.status(400).json({ error: "school id is required" });

    const school = await findSchoolById(id);
    let { lat, lng } = extractCoordinates(school);
    if (lat === null || lng === null) {
      const edbRows = await ensureEdbCache(false);
      const coords = findEdbBySchoolId(edbRows, id);
      lat = coords ? coords.lat : null;
      lng = coords ? coords.lng : null;
    }
    if (lat === null || lng === null) {
      return res.status(404).json({ error: "school coordinates missing", schoolId: id });
    }

    const nearestMtr = pickNearest(
      cachedMTRStops.map((s) => ({
        name: s.name,
        distance: calculateHaversineDistance(lat, lng, s.latitude, s.longitude)
      }))
    );
    const nearestBus = pickNearest(
      cachedBusStops.map((s) => ({
        name: s.name,
        distance: calculateHaversineDistance(lat, lng, s.latitude, s.longitude)
      }))
    );
    const nearestMinibus = pickNearest(
      cachedMinibusStops.map((s) => ({
        name: s.name,
        distance: calculateHaversineDistance(lat, lng, s.latitude, s.longitude)
      }))
    );

    const mtr = nearestMtr && nearestMtr.distance <= 1000 ? nearestMtr : null;
    const bus = nearestBus && nearestBus.distance <= 1000 ? nearestBus : null;
    const minibus = nearestMinibus && nearestMinibus.distance <= 1000 ? nearestMinibus : null;

    return res.json({ mtr, bus, minibus });
  } catch (err) {
    return res.status(500).json({
      error: "Failed to load school transport",
      details: String(err.message || err)
    });
  }
});

module.exports = router;

