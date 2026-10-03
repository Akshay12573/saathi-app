const express = require("express");
const { buildMessages, buildSystemPrompt } = require("../llm/promptSchema");
const { completeJson } = require("../llm/anthropicClient");
const { getHistory, appendTurn } = require("../session/sessionStore");
const { logActivity } = require("../util/activityLog");

const router = express.Router();

router.post("/chat", async (req, res) => {
  const { session_id, text } = req.body || {};

  if (!session_id || typeof session_id !== "string") {
    return res.status(400).json({ error: "session_id is required" });
  }
  if (!text || typeof text !== "string" || !text.trim()) {
    return res.status(400).json({ error: "text is required" });
  }

  try {
    const history = getHistory(session_id);
    const messages = buildMessages(history, text);
    const systemPrompt = buildSystemPrompt(new Date().toISOString());
    const parsed = await completeJson(systemPrompt, messages);

    const replyText = typeof parsed.reply_text === "string" ? parsed.reply_text : "";
    const actions = Array.isArray(parsed.actions) ? parsed.actions : [];

    appendTurn(session_id, text, replyText);

    logActivity("chat", {
      session_id,
      user_text: text,
      reply_text: replyText,
      action_types: actions.map((a) => a.type)
    });

    res.json({
      reply_text: replyText,
      actions,
      needs_more_info: Boolean(parsed.needs_more_info),
      follow_up_question: parsed.follow_up_question ?? null
    });
  } catch (err) {
    console.error("[/api/chat] failed:", err.message);
    logActivity("chat_error", { session_id, user_text: text, error: err.message });
    res.status(502).json({ error: `AI backend error: ${err.message}` });
  }
});

module.exports = router;
