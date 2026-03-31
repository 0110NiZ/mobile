const express = require("express");
const cors = require("cors");
const dotenv = require("dotenv");
const mongoose = require("mongoose");

dotenv.config();

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

const PORT = process.env.PORT || 3000;

async function start() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    throw new Error("Missing MONGODB_URI in environment");
  }

  await mongoose.connect(uri, { dbName: "smartschoolfinder" });
  console.log("Connected to MongoDB");

  app.listen(PORT, () => {
    console.log(`API server running on port ${PORT}`);
  });
}

start().catch((err) => {
  console.error(err);
  process.exit(1);
});

