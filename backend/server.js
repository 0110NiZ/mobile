const express = require("express");
const cors = require("cors");
const dotenv = require("dotenv");
const mongoose = require("mongoose");
const path = require("path");
const { initTransportData } = require("./src/services/TransportService");

// Load env from both backend/.env and project-root/.env (root is where Android project lives).
dotenv.config({ path: path.join(__dirname, ".env") });
dotenv.config({ path: path.join(__dirname, "..", ".env") });

const app = express();
app.use(cors());
// Seed request can include many schools; allow larger JSON bodies for student demos.
app.use(express.json({ limit: "2mb" }));

// Minimal request log for debugging demo runs.
app.use((req, res, next) => {
  const start = Date.now();
  res.on("finish", () => {
    const ms = Date.now() - start;
    console.log(`${req.method} ${req.originalUrl} -> ${res.statusCode} (${ms}ms)`);
  });
  next();
});

app.get("/health", (req, res) => {
  res.json({ ok: true });
});

app.use("/api/reviews", require("./src/routes/reviews"));
app.use("/api/transport", require("./src/routes/transport"));
app.use("/api/schools", require("./src/routes/schools"));

const PORT = process.env.PORT || 3000;

async function start() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    throw new Error("Missing MONGODB_URI in environment");
  }

  await mongoose.connect(uri, { dbName: "smartschoolfinder" });
  console.log("Connected to MongoDB");

  initTransportData();
  console.log("Transport data initialized in memory");

  app.listen(PORT, () => {
    console.log(`API server running on port ${PORT}`);
  });
}

start().catch((err) => {
  console.error(err);
  process.exit(1);
});

