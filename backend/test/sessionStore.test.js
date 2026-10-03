const test = require("node:test");
const assert = require("node:assert");
const { getHistory, appendTurn } = require("../src/session/sessionStore");

test("new session starts with empty history", () => {
  assert.deepStrictEqual(getHistory("never-seen-before"), []);
});

test("appendTurn records user+assistant turns in order", () => {
  const sessionId = `test-${Date.now()}`;
  appendTurn(sessionId, "hello", "hi there");
  const history = getHistory(sessionId);

  assert.strictEqual(history.length, 2);
  assert.deepStrictEqual(history[0], { role: "user", content: "hello" });
  assert.deepStrictEqual(history[1], { role: "assistant", content: "hi there" });
});

test("history is capped and keeps the most recent turns", () => {
  const sessionId = `test-cap-${Date.now()}`;
  for (let i = 0; i < 20; i++) {
    appendTurn(sessionId, `msg ${i}`, `reply ${i}`);
  }
  const history = getHistory(sessionId);
  assert.ok(history.length <= 24);
  assert.strictEqual(history[history.length - 2].content, "msg 19");
});
