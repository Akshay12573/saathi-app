const express = require("express");
const { AGENT_SYSTEM_PROMPT, buildAgentMessages } = require("../llm/agentStepSchema");
const { completeAgentStep } = require("../llm/anthropicClient");
const { sanitizeStepResponse } = require("../llm/agentStepPolicy");

const router = express.Router();

router.post("/agent-step", async (req, res) => {
  const { goal, screen_dump, step_history } = req.body || {};

  if (!goal || typeof goal !== "string" || !goal.trim()) {
    return res.status(400).json({ error: "goal is required" });
  }
  if (!Array.isArray(screen_dump)) {
    return res.status(400).json({ error: "screen_dump must be an array" });
  }

  try {
    const messages = buildAgentMessages(goal, screen_dump, Array.isArray(step_history) ? step_history : []);
    const parsed = await completeAgentStep(AGENT_SYSTEM_PROMPT, messages);
    res.json(sanitizeStepResponse(parsed, screen_dump));
  } catch (err) {
    console.error("[/api/agent-step] failed:", err.message);
    res.status(502).json({ error: `Agent step failed: ${err.message}` });
  }
});

module.exports = router;
