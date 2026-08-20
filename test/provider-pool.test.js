import test from "node:test";
import assert from "node:assert/strict";
import {
  configuredGeminiCount,
  generateProviderText,
  modelHealthSnapshot,
  providerHealthSnapshot,
  PROVIDER_LIMITS,
  resetProviderHealthForTests,
} from "../lib/ai-provider-pool.js";

const keyNames = [1, 2, 3, 4, 5].map((slot) => `GEMINI_API_KEY_${slot}`);
const providerEnvNames = [...keyNames, "LUNA_API_KEY", "LUNA_MODEL", "LUNA_BASE_URL"];

function captureEnvironment() {
  return Object.fromEntries(providerEnvNames.map((name) => [name, process.env[name]]));
}

function restoreEnvironment(previous) {
  for (const name of providerEnvNames) {
    if (previous[name] == null) delete process.env[name];
    else process.env[name] = previous[name];
  }
}

function clearProviderEnvironment() {
  for (const name of providerEnvNames) delete process.env[name];
}

function configureKeys(count) {
  for (let slot = 1; slot <= count; slot += 1) process.env[`GEMINI_API_KEY_${slot}`] = `test-key-${slot}`;
}

function geminiSuccess(text = '{"reply":"ok","ops":[],"snapshotEvent":{"shouldGenerate":false,"type":"none","reason":""}}') {
  return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text }] } }] }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function geminiError(status, code = "ERROR") {
  return new Response(JSON.stringify({ error: { status: code, message: `${code} test` } }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function modelFromUrl(url) {
  const match = String(url).match(/models\/([^:]+):generateContent/);
  return match ? decodeURIComponent(match[1]) : "";
}

function keyFromOptions(options) {
  return options?.headers?.["x-goog-api-key"] || "";
}

function setupProviderTest(t, keyCount = 1) {
  const previous = captureEnvironment();
  const originalFetch = globalThis.fetch;
  clearProviderEnvironment();
  configureKeys(keyCount);
  resetProviderHealthForTests();
  t.after(() => {
    globalThis.fetch = originalFetch;
    restoreEnvironment(previous);
    resetProviderHealthForTests();
  });
}

test("provider pool recognizes all five Gemini account slots", () => {
  const previous = captureEnvironment();
  try {
    clearProviderEnvironment();
    configureKeys(5);
    resetProviderHealthForTests();
    assert.equal(configuredGeminiCount(), 5);
    const health = providerHealthSnapshot();
    assert.equal(health.length, 5);
    assert.deepEqual(health.map((entry) => entry.configured), [true, true, true, true, true]);
    for (const entry of health) {
      assert.ok(entry.perModel["gemini-3.6-flash"]);
      assert.ok(entry.perModel["gemini-3.5-flash"]);
      assert.ok(entry.perModel["gemini-3.5-flash-lite"]);
    }
  } finally {
    restoreEnvironment(previous);
    resetProviderHealthForTests();
  }
});

test("provider pool tolerates partially configured Gemini slots", () => {
  const previous = captureEnvironment();
  try {
    clearProviderEnvironment();
    process.env.GEMINI_API_KEY_2 = "test-2";
    process.env.GEMINI_API_KEY_5 = "test-5";
    resetProviderHealthForTests();
    assert.equal(configuredGeminiCount(), 2);
  } finally {
    restoreEnvironment(previous);
    resetProviderHealthForTests();
  }
});

test("writer and auditor model orders match BACKROOMS policy", () => {
  assert.deepEqual(PROVIDER_LIMITS.writerModels.map((entry) => entry.id), [
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
  ]);
  assert.deepEqual(PROVIDER_LIMITS.auditorModels.map((entry) => entry.id), [
    "gemini-3.5-flash-lite",
    "gemini-3.5-flash",
  ]);
  assert.equal(PROVIDER_LIMITS.writerModels[0].thinkingLevel, "low");
  assert.equal(PROVIDER_LIMITS.writerModels[2].thinkingLevel, "minimal");
  assert.equal(PROVIDER_LIMITS.lunaTimeoutMs, 12000);
  assert.equal(PROVIDER_LIMITS.lunaReserveMs, 12500);
  assert.ok(PROVIDER_LIMITS.totalDeadlineMs < 60000);
});

test("429 rotates to another account on the same strongest model", async (t) => {
  setupProviderTest(t, 2);
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ model: modelFromUrl(url), key: keyFromOptions(options) });
    if (calls.length === 1) return geminiError(429, "RESOURCE_EXHAUSTED");
    return geminiSuccess();
  };

  const result = await generateProviderText("test", { allowLuna: false, totalDeadlineMs: 5000 });
  assert.equal(result.model, "gemini-3.6-flash");
  assert.equal(result.workerSlot, 2);
  assert.deepEqual(calls.map((call) => call.model), ["gemini-3.6-flash", "gemini-3.6-flash"]);
  assert.deepEqual(calls.map((call) => call.key), ["test-key-1", "test-key-2"]);
});

test("429 cooldown is model-lane scoped so the same account may serve the next model", async (t) => {
  setupProviderTest(t, 1);
  const calls = [];
  globalThis.fetch = async (url, options) => {
    const model = modelFromUrl(url);
    calls.push({ model, key: keyFromOptions(options) });
    if (model === "gemini-3.6-flash") return geminiError(429, "RESOURCE_EXHAUSTED");
    return geminiSuccess();
  };

  const result = await generateProviderText("test", { allowLuna: false, totalDeadlineMs: 5000 });
  assert.equal(result.model, "gemini-3.5-flash");
  assert.equal(result.workerSlot, 1);
  assert.deepEqual(calls.map((call) => call.model), ["gemini-3.6-flash", "gemini-3.5-flash"]);
  assert.deepEqual(calls.map((call) => call.key), ["test-key-1", "test-key-1"]);
});

test("400 or 404 opens the model circuit immediately instead of wasting the remaining accounts", async (t) => {
  setupProviderTest(t, 3);
  const calls = [];
  globalThis.fetch = async (url, options) => {
    const model = modelFromUrl(url);
    calls.push({ model, key: keyFromOptions(options) });
    if (model === "gemini-3.6-flash") return geminiError(404, "NOT_FOUND");
    return geminiSuccess();
  };

  const result = await generateProviderText("test", { allowLuna: false, totalDeadlineMs: 5000 });
  assert.equal(result.model, "gemini-3.5-flash");
  assert.equal(calls.filter((call) => call.model === "gemini-3.6-flash").length, 1);
  const modelHealth = modelHealthSnapshot().find((entry) => entry.model === "gemini-3.6-flash");
  assert.equal(modelHealth.circuitOpen, true);
  assert.match(modelHealth.circuitReason, /^model:404:/);
});

test("three matching transient failures from distinct accounts circuit-break one model", async (t) => {
  setupProviderTest(t, 4);
  const calls = [];
  globalThis.fetch = async (url, options) => {
    const model = modelFromUrl(url);
    calls.push({ model, key: keyFromOptions(options) });
    if (model === "gemini-3.6-flash") return geminiError(503, "UNAVAILABLE");
    return geminiSuccess();
  };

  const result = await generateProviderText("test", { allowLuna: false, totalDeadlineMs: 5000 });
  assert.equal(result.model, "gemini-3.5-flash");
  assert.equal(calls.filter((call) => call.model === "gemini-3.6-flash").length, 3);
  assert.equal(modelHealthSnapshot().find((entry) => entry.model === "gemini-3.6-flash").circuitOpen, true);
});

test("401 or 403 disables one credential across the entire model matrix", async (t) => {
  setupProviderTest(t, 1);
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ model: modelFromUrl(url), key: keyFromOptions(options) });
    return geminiError(401, "UNAUTHENTICATED");
  };

  await assert.rejects(
    generateProviderText("test", { allowLuna: false, totalDeadlineMs: 5000 }),
    /Gemini HTTP 401/,
  );
  assert.equal(calls.length, 1);
  assert.equal(providerHealthSnapshot()[0].disabled, true);
});

test("auditor starts with Flash-Lite and does not need Luna", async (t) => {
  setupProviderTest(t, 1);
  const calls = [];
  globalThis.fetch = async (url) => {
    calls.push(modelFromUrl(url));
    return geminiSuccess('{"pass":true,"issues":[]}');
  };

  const result = await generateProviderText("audit", {
    policy: "audit",
    allowLuna: false,
    totalDeadlineMs: 5000,
    maxOutputTokens: 650,
  });
  assert.equal(result.model, "gemini-3.5-flash-lite");
  assert.deepEqual(calls, ["gemini-3.5-flash-lite"]);
});
