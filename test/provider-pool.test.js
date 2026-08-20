import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { configuredGeminiCount, providerHealthSnapshot, PROVIDER_LIMITS } from "../lib/ai-provider-pool.js";

const keyNames = [1, 2, 3, 4, 5].map((slot) => `GEMINI_API_KEY_${slot}`);

test("provider pool recognizes all five Gemini key slots", () => {
  const previous = Object.fromEntries(keyNames.map((name) => [name, process.env[name]]));
  try {
    for (let slot = 1; slot <= 5; slot += 1) process.env[`GEMINI_API_KEY_${slot}`] = `test-${slot}`;
    assert.equal(configuredGeminiCount(), 5);
    const health = providerHealthSnapshot();
    assert.equal(health.length, 5);
    assert.deepEqual(health.map((entry) => entry.configured), [true, true, true, true, true]);
  } finally {
    for (const name of keyNames) {
      if (previous[name] == null) delete process.env[name];
      else process.env[name] = previous[name];
    }
  }
});

test("provider pool tolerates partially configured Gemini slots", () => {
  const previous = Object.fromEntries(keyNames.map((name) => [name, process.env[name]]));
  try {
    for (const name of keyNames) delete process.env[name];
    process.env.GEMINI_API_KEY_2 = "test-2";
    process.env.GEMINI_API_KEY_5 = "test-5";
    assert.equal(configuredGeminiCount(), 2);
  } finally {
    for (const name of keyNames) {
      if (previous[name] == null) delete process.env[name];
      else process.env[name] = previous[name];
    }
  }
});

test("provider deadlines fit inside the 60 second game-turn runtime", () => {
  assert.equal(PROVIDER_LIMITS.geminiTimeoutMs, 8000);
  assert.equal(PROVIDER_LIMITS.lunaTimeoutMs, 12000);
  assert.ok(PROVIDER_LIMITS.totalDeadlineMs <= 55000);
  assert.ok(PROVIDER_LIMITS.totalDeadlineMs < 60000);
});

test("Gemini 3.6 requests use low thinking and no deprecated temperature sampling", () => {
  const source = readFileSync(new URL("../lib/ai-provider-pool.js", import.meta.url), "utf8");
  assert.match(source, /thinkingConfig:\s*\{\s*thinkingLevel:\s*"low"\s*\}/);
  const geminiStart = source.indexOf("async function requestGemini");
  const lunaStart = source.indexOf("async function requestLuna", geminiStart);
  const geminiBody = source.slice(geminiStart, lunaStart);
  assert.doesNotMatch(geminiBody, /temperature:/);
});
