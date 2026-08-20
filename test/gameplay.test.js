import test from "node:test";
import assert from "node:assert/strict";
import {
  canTransitionLevel,
  isGameplayTurn,
  levelFromState,
  makeRolls,
} from "../lib/gameplay.js";

function baseState(overrides = {}) {
  return {
    level: { number: "0", name: "The Lobby" },
    party: [],
    flags: {
      iris: { exists: true, continuity: "SEPARATED FROM KAI" },
      syvial: { exists: true, continuity: "SEPARATED FROM KAI" },
      exploration: { exitProgress: "NO CONFIRMED CLUE" },
      ...overrides,
    },
  };
}

const alwaysOne = () => 1;

test("meta inspection does not count as a gameplay turn", () => {
  assert.equal(isGameplayTurn("/status"), false);
  assert.equal(isGameplayTurn("Cho tôi xem inventory hiện tại"), false);
  assert.equal(isGameplayTurn("Kai kiểm tra cánh cửa trắng"), true);
});

test("Level 0 rolls only contextually eligible systems", () => {
  const rolls = makeRolls(baseState(), "Kai rà các phòng để tìm một chai Almond Water", alwaysOne);
  assert.equal(rolls.survivor.eligible, true);
  assert.equal(rolls.irisReunion.eligible, true);
  assert.equal(rolls.syvialReunion.eligible, true);
  assert.equal(rolls.hazard.eligible, true);
  assert.equal(rolls.entityEncounter.threshold, 5);
  assert.match(rolls.entityEncounter.chance, /incursion\/roaming only/);
  assert.equal(rolls.loot.eligible, true);
  assert.equal(rolls.madGodSet.eligible, true);
  assert.equal(rolls.madGodSet.threshold, 1);
  assert.equal(rolls.madGodSet.success, true);
  assert.equal(rolls.almondWater.eligible, true);
  assert.equal(rolls.almondWater.success, true);
  assert.equal(rolls.exitProbe.eligible, false);
  assert.equal(rolls.exitProbe.raw, null);
});

test("MadGod Set stops rolling after the campaign's unique copy exists", () => {
  const state = baseState({
    madGod: { spawned: true, location: "Level 3", holder: "NONE" },
  });
  const rolls = makeRolls(state, "Kai lục khu kỹ thuật để tìm vật phẩm", alwaysOne);
  assert.equal(rolls.madGodSet.eligible, false);
  assert.equal(rolls.madGodSet.raw, null);
  assert.equal(rolls.madGodSet.success, false);
});

test("reunion rolls stop after a character is present", () => {
  const state = baseState({
    iris: { exists: true, continuity: "REUNITED / WITH KAI" },
  });
  state.party = [{ name: "Iris" }];
  const rolls = makeRolls(state, "Kai đi tiếp", alwaysOne);
  assert.equal(rolls.irisReunion.eligible, false);
  assert.equal(rolls.irisReunion.raw, null);
  assert.equal(rolls.syvialReunion.eligible, true);
});

test("a canon-ready exit is deterministic instead of rerolled", () => {
  const state = baseState({
    exploration: { exitProgress: "TRANSITION AVAILABLE / CONDITION MET" },
  });
  const rolls = makeRolls(state, "Kai bước qua lối thoát đã xác nhận", alwaysOne);
  assert.equal(rolls.exitProbe.success, true);
  assert.equal(rolls.exitProbe.guaranteedByState, true);
  assert.equal(rolls.exitProbe.raw, null);
  assert.equal(canTransitionLevel(state, rolls), true);
});

test("level parser keeps the explicit state level", () => {
  assert.deepEqual(levelFromState({ level: { number: "5", name: "Terror Hotel" } }), {
    number: "5",
    name: "Terror Hotel",
  });
});
