import test from "node:test";
import assert from "node:assert/strict";
import { characterCanonFor } from "../lib/character-canon.js";
import { worldCanonFor } from "../lib/world-canon.js";

const separatedState = {
  party: [],
  flags: {
    iris: { exists: true, continuity: "SEPARATED FROM KAI" },
    syvial: { exists: true, continuity: "SEPARATED FROM KAI" },
  },
};

test("character codices stay lazy until an encounter needs them", () => {
  const unloaded = characterCanonFor(separatedState, {});
  assert.match(unloaded, /Iris full codex remains unloaded/);
  assert.match(unloaded, /Syvial full codex remains unloaded/);

  const loaded = characterCanonFor(separatedState, {
    irisReunion: { success: true },
    syvialReunion: { success: false },
  });
  assert.match(loaded, /IRIS \/ ARGUS — CHARACTER CANON R05/);
  assert.match(loaded, /Syvial full codex remains unloaded/);
});

test("world canon routes only the active Level plus global rules", () => {
  const levelFive = worldCanonFor("5");
  assert.match(levelFive, /LEVEL 5 — TERROR HOTEL/);
  assert.doesNotMatch(levelFive, /LEVEL 4 — THE ABANDONED OFFICE/);
  assert.match(levelFive, /ENTITY HARD LOCK/);
  assert.match(levelFive, /ITEM \/ RESOURCE HARD LOCK/);
});
