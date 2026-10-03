const Anthropic = require("@anthropic-ai/sdk");

let client = null;

function getClient() {
  if (!process.env.ANTHROPIC_API_KEY) {
    throw new Error("ANTHROPIC_API_KEY not configured on server");
  }
  if (!client) {
    client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });
  }
  return client;
}

function extractText(message) {
  return message.content
    .filter((block) => block.type === "text")
    .map((block) => block.text)
    .join("")
    .trim();
}

/** Strips ```json fences etc. in case the model wraps its JSON despite instructions. */
function stripCodeFences(text) {
  const fenced = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  return fenced ? fenced[1].trim() : text.trim();
}

async function completeJson(systemPrompt, messages) {
  const anthropic = getClient();
  const model = process.env.ANTHROPIC_MODEL || "claude-haiku-4-5-20251001";

  const response = await anthropic.messages.create({
    model,
    max_tokens: 1024,
    system: systemPrompt,
    messages
  });

  const raw = extractText(response);
  if (!raw) throw new Error("Empty response from LLM");

  try {
    return JSON.parse(stripCodeFences(raw));
  } catch (parseErr) {
    // The model occasionally drifts into plain conversation instead of the
    // JSON contract (seen live on meta/capability questions like "aap
    // kya-kya kar sakte ho"). Treat its raw text as the reply instead of
    // failing the whole request with a 502 — no action gets executed, which
    // is the safe default when we can't parse a structured plan anyway.
    console.warn("[anthropicClient] non-JSON response, falling back to plain reply:", raw.slice(0, 200));
    return { reply_text: raw, actions: [], needs_more_info: false, follow_up_question: null };
  }
}

async function summarize(prompt) {
  const anthropic = getClient();
  const model = process.env.ANTHROPIC_MODEL || "claude-haiku-4-5-20251001";

  const response = await anthropic.messages.create({
    model,
    max_tokens: 512,
    system: "Summarize search results into 3-5 Hinglish sentences a voice assistant can speak aloud. Be factual, cite nothing inline, keep it short.",
    messages: [{ role: "user", content: prompt }]
  });

  return extractText(response);
}

module.exports = { completeJson, summarize };
