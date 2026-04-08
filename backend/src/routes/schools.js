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
let compiledSchoolsCache = null;

function safe(v) {
  return String(v || "").trim().toLowerCase();
}

function firstNonEmpty(obj, keys) {
  for (const k of keys) {
    const v = obj && obj[k] != null ? String(obj[k]).trim() : "";
    if (v) return v;
  }
  return "";
}

function buildDedupeKey(item) {
  const code = safe(firstNonEmpty(item, ["SCHOOL CODE", "schoolCode", "school_code"]));
  if (code) return `CODE:${code}`;
  const id = safe(firstNonEmpty(item, ["SCHOOL NO.", "id", "school_id"]));
  if (id) return `ID:${id}`;
  const name = safe(firstNonEmpty(item, ["ENGLISH NAME", "name", "school_name"]));
  const addr = safe(firstNonEmpty(item, ["ENGLISH ADDRESS", "address", "school_address"]));
  if (name || addr) return `NA:${name}|${addr}`;
  return "EMPTY";
}

function buildEntityKey(item) {
  const name = safe(firstNonEmpty(item, ["ENGLISH NAME", "name", "school_name"]));
  const addr = safe(firstNonEmpty(item, ["ENGLISH ADDRESS", "address", "school_address"]));
  if (name || addr) return `NA:${name}|${addr}`;
  return buildDedupeKey(item);
}

async function fetchCompiledSchools() {
  if (compiledSchoolsCache && Array.isArray(compiledSchoolsCache) && compiledSchoolsCache.length > 0) {
    return compiledSchoolsCache;
  }

  const src =
    "https://www.edb.gov.hk/attachment/en/student-parents/sch-info/sch-search/sch-location-info/SCH_LOC_EDB.json";
  const response = await fetch(src);
  if (!response.ok) {
    throw new Error(`failed to fetch schools: ${response.status}`);
  }
  const rows = await response.json();
  const list = Array.isArray(rows) ? rows : [];

  const stage1Seen = new Set();
  const stage1 = [];
  for (const item of list) {
    const key = buildDedupeKey(item);
    if (!stage1Seen.has(key)) {
      stage1Seen.add(key);
      stage1.push(item);
    }
  }

  const stage2Seen = new Set();
  const deduped = [];
  for (const item of stage1) {
    const key = buildEntityKey(item);
    if (!stage2Seen.has(key)) {
      stage2Seen.add(key);
      deduped.push(item);
    }
  }

  compiledSchoolsCache = deduped;
  return deduped;
}

router.get("/compiled", async (req, res) => {
  try {
    const schools = await fetchCompiledSchools();
    return res.json({
      source: "edb-compiled",
      total: schools.length,
      schools
    });
  } catch (err) {
    return res.status(500).json({
      error: "Failed to load compiled schools",
      details: String(err.message || err)
    });
  }
});

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
        name_zh: s.name_zh || s.name,
        distance: calculateHaversineDistance(lat, lng, s.latitude, s.longitude)
      }))
    );
    const nearestBus = pickNearest(
      cachedBusStops.map((s) => ({
        name: s.name,
        name_zh: s.name_zh || s.name,
        distance: calculateHaversineDistance(lat, lng, s.latitude, s.longitude)
      }))
    );
    const nearestMinibus = pickNearest(
      cachedMinibusStops.map((s) => ({
        name: s.name,
        name_zh: s.name_zh || s.name,
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

