// Pure validation/sanitization of a raw agent-step LLM response — no network
// calls, so this is unit-testable without hitting the real API (see
// backend/test/agentStepPolicy.test.js).

const VALID_ACTIONS = new Set(["TAP", "TYPE", "SCROLL_DOWN", "SCROLL_UP", "BACK", "DONE", "FAILED"]);
const SENSITIVE_WORDS = /\b(send|post|submit|pay|delete|remove|confirm|buy|order|share|publish|bhejo|bhej|delete karo)\b/i;

function isSensitiveText(text) {
  return SENSITIVE_WORDS.test(text || "");
}

/**
 * @param {object} parsed - raw (already JSON-parsed) model output
 * @param {Array<{index:number,text?:string,content_desc?:string}>} screenDump
 */
function sanitizeStepResponse(parsed, screenDump) {
  const action = VALID_ACTIONS.has(parsed.action) ? parsed.action : "FAILED";
  const targetIndex = Number.isInteger(parsed.target_index) ? parsed.target_index : null;
  const targetNode = targetIndex != null ? screenDump.find((n) => n.index === targetIndex) : null;

  const textToCheck = `${targetNode?.text || ""} ${targetNode?.content_desc || ""}`;
  const heuristicSensitive = isSensitiveText(textToCheck);

  const isTerminal = action === "DONE" || action === "FAILED";
  const message = isTerminal && typeof parsed.message === "string" ? parsed.message : "";

  return {
    action,
    target_index: targetIndex,
    text_to_type: typeof parsed.text_to_type === "string" ? parsed.text_to_type : null,
    is_sensitive: Boolean(parsed.is_sensitive) || heuristicSensitive,
    message
  };
}

module.exports = { VALID_ACTIONS, isSensitiveText, sanitizeStepResponse };
