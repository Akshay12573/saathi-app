const fs = require("fs");
const path = require("path");

// Local-only activity log so we can actually debug/improve the assistant
// from real usage — never committed (see backend/.gitignore) since it can
// contain user conversation text and on-screen content during agent tasks.
const LOG_DIR = path.join(__dirname, "..", "..", "logs");
const LOG_FILE = path.join(LOG_DIR, "activity.log");

function ensureLogDir() {
  if (!fs.existsSync(LOG_DIR)) fs.mkdirSync(LOG_DIR, { recursive: true });
}

function logActivity(event, data) {
  try {
    ensureLogDir();
    const line = JSON.stringify({ ts: new Date().toISOString(), event, ...data });
    fs.appendFileSync(LOG_FILE, line + "\n");
  } catch (err) {
    // Logging must never break the actual request.
    console.error("[activityLog] failed to write:", err.message);
  }
}

module.exports = { logActivity, LOG_FILE };
