import test from "node:test";
import assert from "node:assert/strict";
import { configuredGeminiCount, providerHealthSnapshot } from "../lib/ai-provider-pool.js";

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
