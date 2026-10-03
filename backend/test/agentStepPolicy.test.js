const test = require("node:test");
const assert = require("node:assert");
const { isSensitiveText, sanitizeStepResponse } = require("../src/llm/agentStepPolicy");

test("isSensitiveText matches send/post/pay/delete style words", () => {
  assert.strictEqual(isSensitiveText("Send"), true);
  assert.strictEqual(isSensitiveText("Post"), true);
  assert.strictEqual(isSensitiveText("Confirm order"), true);
  assert.strictEqual(isSensitiveText("Home"), false);
  assert.strictEqual(isSensitiveText(""), false);
});

test("sanitizeStepResponse falls back to FAILED for an unknown action", () => {
  const result = sanitizeStepResponse({ action: "DELETE_EVERYTHING" }, []);
  assert.strictEqual(result.action, "FAILED");
});

test("sanitizeStepResponse clears message on non-terminal actions even if the model set one", () => {
  const result = sanitizeStepResponse(
    { action: "TAP", target_index: 1, message: "Message bhej diya" },
    [{ index: 1, text: "Send" }]
  );
  assert.strictEqual(result.message, "", "message must be empty until DONE/FAILED — the tap hasn't happened yet");
});

test("sanitizeStepResponse keeps message on DONE", () => {
  const result = sanitizeStepResponse({ action: "DONE", message: "Ho gaya" }, []);
  assert.strictEqual(result.message, "Ho gaya");
});

test("sanitizeStepResponse forces is_sensitive true via heuristic even if model said false", () => {
  const result = sanitizeStepResponse(
    { action: "TAP", target_index: 0, is_sensitive: false },
    [{ index: 0, text: "Send", content_desc: "" }]
  );
  assert.strictEqual(result.is_sensitive, true);
});

test("sanitizeStepResponse respects model is_sensitive=true even without a heuristic match", () => {
  const result = sanitizeStepResponse(
    { action: "TAP", target_index: 0, is_sensitive: true },
    [{ index: 0, text: "Next", content_desc: "" }]
  );
  assert.strictEqual(result.is_sensitive, true);
});

test("sanitizeStepResponse ignores a target_index not present in the dump", () => {
  const result = sanitizeStepResponse({ action: "TAP", target_index: 99 }, [{ index: 0, text: "Home" }]);
  assert.strictEqual(result.target_index, 99);
  assert.strictEqual(result.is_sensitive, false);
});
