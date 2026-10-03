const test = require("node:test");
const assert = require("node:assert");
const { requireAppKey } = require("../src/middleware/auth");

function mockRes() {
  const res = {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(payload) { this.body = payload; return this; }
  };
  return res;
}

test("rejects when SAATHI_BACKEND_API_KEY is not configured", () => {
  delete process.env.SAATHI_BACKEND_API_KEY;
  const req = { header: () => "anything" };
  const res = mockRes();
  let nextCalled = false;

  requireAppKey(req, res, () => { nextCalled = true; });

  assert.strictEqual(nextCalled, false);
  assert.strictEqual(res.statusCode, 500);
});

test("rejects a wrong key", () => {
  process.env.SAATHI_BACKEND_API_KEY = "correct-key";
  const req = { header: () => "wrong-key" };
  const res = mockRes();
  let nextCalled = false;

  requireAppKey(req, res, () => { nextCalled = true; });

  assert.strictEqual(nextCalled, false);
  assert.strictEqual(res.statusCode, 401);
});

test("accepts the correct key", () => {
  process.env.SAATHI_BACKEND_API_KEY = "correct-key";
  const req = { header: () => "correct-key" };
  const res = mockRes();
  let nextCalled = false;

  requireAppKey(req, res, () => { nextCalled = true; });

  assert.strictEqual(nextCalled, true);
});
