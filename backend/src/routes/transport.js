const express = require("express");
const https = require("https");

const router = express.Router();

const DEFAULT_RADIUS_METERS = 500;

function toNumber(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function haversineMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

function formatDistance(meters) {
  if (!Number.isFinite(meters)) return "N/A";
  if (meters < 1000) return `${meters}m`;
  return `${(meters / 1000).toFixed(1)}km`;
}

function isMtr(place) {
  const name = String(place.name || "").toLowerCase();
  return name.includes("mtr") || name.includes("station");
}

function isMinibus(place) {
  const text = `${place.name || ""} ${place.vicinity || ""}`.toLowerCase();
  return (
    text.includes("minibus") ||
    text.includes("public light bus") ||
    text.includes("green minibus") ||
    text.includes("plb")
  );
}

function isBus(place) {
  const text = `${place.name || ""} ${place.vicinity || ""}`.toLowerCase();
  return (
    text.includes("bus") ||
    text.includes("bus stop") ||
    text.includes("bus terminus")
  );
}

function toStationItem(place, meters) {
  return {
    name: place.name || "Unknown",
    distanceMeters: meters,
    distanceText: formatDistance(meters),
    vicinity: place.vicinity || ""
  };
}

function scoreToStars(mtr, busCount, minibusCount) {
  let score = 1;
  if (mtr) score += 2;
  if (busCount >= 2) score += 1;
  if (minibusCount >= 1) score += 1;
  const stars = Math.max(1, Math.min(5, score));
  return "⭐".repeat(stars);
}

function httpsGetJson(url) {
  return new Promise((resolve, reject) => {
    https
      .get(url, (res) => {
        let raw = "";
        res.on("data", (chunk) => {
          raw += chunk;
        });
        res.on("end", () => {
          try {
            const data = JSON.parse(raw || "{}");
            resolve({ statusCode: res.statusCode || 500, data });
          } catch (e) {
            reject(new Error(`Invalid JSON from upstream: ${e.message}`));
          }
        });
      })
      .on("error", (err) => reject(err));
  });
}

router.get("/nearby", async (req, res) => {
  try {
    const lat = toNumber(req.query.latitude ?? req.query.lat);
    const lng = toNumber(req.query.longitude ?? req.query.lng);
    const radius = Math.max(100, Math.min(2000, toNumber(req.query.radius) || DEFAULT_RADIUS_METERS));
    if (lat === null || lng === null) {
      return res.status(400).json({ error: "latitude and longitude are required" });
    }

    const apiKey = process.env.GOOGLE_PLACES_API_KEY;
    if (!apiKey) {
      return res.status(500).json({ error: "Missing GOOGLE_PLACES_API_KEY on server" });
    }

    const url =
      `https://maps.googleapis.com/maps/api/place/nearbysearch/json` +
      `?location=${encodeURIComponent(`${lat},${lng}`)}` +
      `&radius=${encodeURIComponent(String(radius))}` +
      `&type=transit_station` +
      `&key=${encodeURIComponent(apiKey)}`;

    const upstream = await httpsGetJson(url);
    const payload = upstream.data || {};
    if (upstream.statusCode < 200 || upstream.statusCode >= 300
      || payload.status === "REQUEST_DENIED" || payload.status === "INVALID_REQUEST") {
      return res.status(502).json({
        error: "Google Places request failed",
        details: payload.error_message || payload.status || "unknown error"
      });
    }

    const places = Array.isArray(payload.results) ? payload.results : [];
    const enriched = places
      .map((p) => {
        const pLat = toNumber(p?.geometry?.location?.lat);
        const pLng = toNumber(p?.geometry?.location?.lng);
        if (pLat === null || pLng === null) return null;
        const meters = haversineMeters(lat, lng, pLat, pLng);
        return { place: p, meters };
      })
      .filter(Boolean)
      .sort((a, b) => a.meters - b.meters);

    const mtrCandidates = enriched.filter((x) => isMtr(x.place));
    const busCandidates = enriched.filter((x) => isBus(x.place) && !isMinibus(x.place));
    const minibusCandidates = enriched.filter((x) => isMinibus(x.place));

    const mtr = mtrCandidates.length > 0 ? toStationItem(mtrCandidates[0].place, mtrCandidates[0].meters) : null;
    const bus = busCandidates.slice(0, 3).map((x) => toStationItem(x.place, x.meters));
    const minibus = minibusCandidates.slice(0, 3).map((x) => toStationItem(x.place, x.meters));

    const convenienceScore = scoreToStars(mtr, bus.length, minibus.length);

    return res.json({
      center: { latitude: lat, longitude: lng, radiusMeters: radius },
      mtr,
      bus,
      minibus,
      convenienceScore
    });
  } catch (err) {
    return res.status(500).json({ error: "Failed to load nearby transport", details: String(err.message || err) });
  }
});

module.exports = router;

