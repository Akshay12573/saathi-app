const OpenAI = require("openai");

let client = null;

function getClient() {
  if (!process.env.OPENAI_API_KEY) {
    throw new Error("OPENAI_API_KEY not configured on server");
  }
  if (!client) {
    client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
  }
  return client;
}

async function completeJson(messages) {
  const openai = getClient();
  const model = process.env.OPENAI_MODEL || "gpt-4o-mini";

  const completion = await openai.chat.completions.create({
    model,
    messages,
    response_format: { type: "json_object" },
    temperature: 0.4
  });

  const raw = completion.choices[0]?.message?.content;
  if (!raw) throw new Error("Empty response from LLM");
  return JSON.parse(raw);
}

async function summarize(prompt) {
  const openai = getClient();
  const model = process.env.OPENAI_MODEL || "gpt-4o-mini";

  const completion = await openai.chat.completions.create({
    model,
    messages: [
      { role: "system", content: "Summarize search results into 3-5 Hinglish sentences a voice assistant can speak aloud. Be factual, cite nothing inline, keep it short." },
      { role: "user", content: prompt }
    ],
    temperature: 0.3
  });

  return completion.choices[0]?.message?.content?.trim() || "";
}

module.exports = { completeJson, summarize };
