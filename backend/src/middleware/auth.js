// Authenticates the Android app to the backend. This is deliberately a
// separate secret from the LLM provider key — it never needs the same
// blast radius and can be rotated independently.
function requireAppKey(req, res, next) {
  const expected = process.env.SAATHI_BACKEND_API_KEY;
  if (!expected) {
    return res.status(500).json({ error: "SAATHI_BACKEND_API_KEY not configured on server" });
  }

  const provided = req.header("x-saathi-key");
  if (provided !== expected) {
    return res.status(401).json({ error: "Invalid or missing x-saathi-key" });
  }

  next();
}

module.exports = { requireAppKey };
