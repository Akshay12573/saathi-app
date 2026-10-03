const express = require("express");
const { search } = require("../search/searchProvider");
const { summarize } = require("../llm/openaiClient");

const router = express.Router();

router.post("/research", async (req, res) => {
  const { query } = req.body || {};
  if (!query || typeof query !== "string" || !query.trim()) {
    return res.status(400).json({ error: "query is required" });
  }

  try {
    const { configured, results } = await search(query);

    if (!configured) {
      return res.json({
        summary: "Web research abhi configure nahi hai (TAVILY_API_KEY set karo backend .env mein).",
        sources: []
      });
    }

    if (results.length === 0) {
      return res.json({ summary: `'${query}' ke liye kuch nahi mila.`, sources: [] });
    }

    const prompt = `Query: ${query}\n\nResults:\n` +
      results.map((r, i) => `${i + 1}. ${r.title}\n${r.content}\n(${r.url})`).join("\n\n");

    const summary = await summarize(prompt);

    res.json({
      summary: summary || "Results mil gaye par summarize nahi ho paya.",
      sources: results.map((r) => r.url)
    });
  } catch (err) {
    console.error("[/api/research] failed:", err.message);
    res.status(502).json({ error: `Research failed: ${err.message}` });
  }
});

module.exports = router;
