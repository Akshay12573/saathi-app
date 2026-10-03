// In-memory conversation history keyed by the Android app's per-install
// session id. Good enough for a single-process deployment; swap for
// Redis/Postgres if you scale past one instance.
const MAX_TURNS = 12;
const sessions = new Map();

function getHistory(sessionId) {
  return sessions.get(sessionId) ?? [];
}

function appendTurn(sessionId, userMessage, assistantMessage) {
  const history = sessions.get(sessionId) ?? [];
  history.push({ role: "user", content: userMessage });
  history.push({ role: "assistant", content: assistantMessage });
  while (history.length > MAX_TURNS * 2) {
    history.shift();
  }
  sessions.set(sessionId, history);
}

module.exports = { getHistory, appendTurn };
