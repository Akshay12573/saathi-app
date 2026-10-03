require("dotenv").config();
const express = require("express");
const cors = require("cors");

const { requireAppKey } = require("./middleware/auth");
const chatRoute = require("./routes/chat");
const researchRoute = require("./routes/research");
const agentStepRoute = require("./routes/agentStep");

const app = express();
app.use(cors());
app.use(express.json({ limit: "2mb" }));

app.get("/healthz", (req, res) => {
  res.json({ status: "ok" });
});

app.use("/api", requireAppKey, chatRoute);
app.use("/api", requireAppKey, researchRoute);
app.use("/api", requireAppKey, agentStepRoute);

app.use((req, res) => {
  res.status(404).json({ error: "Not found" });
});

// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error("Unhandled error:", err);
  res.status(500).json({ error: "Internal server error" });
});

const port = process.env.PORT || 8080;
if (require.main === module) {
  app.listen(port, () => {
    console.log(`Saathi backend listening on port ${port}`);
  });
}

module.exports = app;
