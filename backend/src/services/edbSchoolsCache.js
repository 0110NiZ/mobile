const axios = require("axios");

// EDB school location dataset (English)
const EDB_SCHOOL_LOC_URL =
  "https://www.edb.gov.hk/attachment/en/student-parents/sch-info/sch-search/sch-location-info/SCH_LOC_EDB.json";

const CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

const cache = {
  lastLoadedAt: 0,
  loading: null,
  rows: []
};

function toNumber(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

async function loadEdbRows() {
  const resp = await axios.get(EDB_SCHOOL_LOC_URL, { timeout: 30000 });
  const data = resp?.data;
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.records)) return data.records;
  return [];
}

async function ensureEdbCache(force = false) {
  const fresh = Date.now() - cache.lastLoadedAt < CACHE_TTL_MS;
  if (!force && fresh && cache.rows.length > 0) {
    return cache.rows;
  }
  if (cache.loading) {
    await cache.loading;
    return cache.rows;
  }
  cache.loading = loadEdbRows();
  try {
    cache.rows = await cache.loading;
    cache.lastLoadedAt = Date.now();
  } finally {
    cache.loading = null;
  }
  return cache.rows;
}

function findEdbBySchoolId(rows, schoolId) {
  const id = String(schoolId || "").trim();
  if (!id) return null;

  // Common keys observed in EDB dataset and other sources
  const candidates = rows.filter((r) => {
    if (!r) return false;
    const a = String(r["SCHOOL NO."] ?? r.school_no ?? r.id ?? r.code ?? "").trim();
    const b = String(r["SCHOOL CODE"] ?? r.school_code ?? r.schoolCode ?? "").trim();
    return a === id || b === id;
  });

  const row = candidates.length > 0 ? candidates[0] : null;
  if (!row) return null;

  const lat = toNumber(row.LATITUDE ?? row.latitude ?? row.lat);
  const lng = toNumber(row.LONGITUDE ?? row.longitude ?? row.long ?? row.lon ?? row.lng);
  if (lat === null || lng === null) return null;

  return { lat, lng };
}

module.exports = {
  ensureEdbCache,
  findEdbBySchoolId
};

