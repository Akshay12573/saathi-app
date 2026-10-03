const test = require("node:test");
const assert = require("node:assert");
const { buildAgentMessages } = require("../src/llm/agentStepSchema");

test("buildAgentMessages includes CURRENT APP line when appName given", () => {
  const messages = buildAgentMessages("Send hi to John", [], [], "Instagram");
  assert.match(messages[0].content, /CURRENT APP: Instagram/);
});

test("buildAgentMessages omits CURRENT APP line when appName missing", () => {
  const messages = buildAgentMessages("Send hi to John", [], [], undefined);
  assert.doesNotMatch(messages[0].content, /CURRENT APP/);
});

test("buildAgentMessages notes an empty dump as possibly still loading", () => {
  const messages = buildAgentMessages("goal", [], [], "Zepto");
  assert.match(messages[0].content, /still be loading/);
});
