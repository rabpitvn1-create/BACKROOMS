import test from "node:test";
import assert from "node:assert/strict";
import { canonDependenciesFor, canonPacketFor } from "../lib/canon-router.js";

function state(overrides = {}) {
  return {
    level: { number: "1", name: "Parking Zone" },
    party: [],
    inventory: [
      { name: "White Wraith Magnum", quantity: 1, state: "CARRIED" },
      { name: "Omnivault Ring / Nhẫn Vạn Tàng", quantity: 1, state: "INTACT" },
    ],
    flags: {
      iris: { exists: true, continuity: "SEPARATED FROM KAI" },
      syvial: { exists: true, continuity: "SEPARATED FROM KAI" },
      entityRegistry: [],
      entitiesConfirmedLocal: 0,
      ...overrides,
    },
  };
}

test("quiet movement keeps item/entity deep canon unloaded", () => {
  const packet = canonPacketFor(state(), "Kai đi tiếp dọc hành lang.", {});
  assert.equal(packet.dependencies.level, "1");
  assert.equal(packet.dependencies.entity, false);
  assert.equal(packet.dependencies.item, false);
  assert.match(packet.worldCanon, /LEVEL 1 — PARKING ZONE/);
  assert.doesNotMatch(packet.worldCanon, /ENTITY HARD LOCK/);
  assert.doesNotMatch(packet.worldCanon, /ITEM \/ RESOURCE HARD LOCK/);
  assert.match(packet.gameMasterCanon, /Tên Kai Akechi/);
  assert.doesNotMatch(packet.gameMasterCanon, /Guilty Crown Override/);
});

test("combat closes over Kai combat dependencies and entity canon", () => {
  const packet = canonPacketFor(
    state({ entitiesConfirmedLocal: 1, entityRegistry: [{ name: "Hound" }] }),
    "Kai rút White Wraith Magnum và bắn Hound.",
    {},
  );
  assert.equal(packet.dependencies.combat, true);
  assert.equal(packet.dependencies.entity, true);
  assert.match(packet.worldCanon, /ENTITY HARD LOCK/);
  assert.match(packet.gameMasterCanon, /White Wraith Magnum/);
  assert.match(packet.gameMasterCanon, /Thiện xạ UR\+/);
});

test("Omnivault action loads item rules and Omnivault hard canon", () => {
  const packet = canonPacketFor(state(), "Kai Scan chai nước bằng Omnivault rồi Copy một bản.", {});
  assert.equal(packet.dependencies.omnivault, true);
  assert.equal(packet.dependencies.item, true);
  assert.match(packet.worldCanon, /ITEM \/ RESOURCE HARD LOCK/);
  assert.match(packet.gameMasterCanon, /Omnivault Scan\/Copy/);
});

test("present or reunion character still uses authoritative lazy character canon", () => {
  const current = state({ iris: { exists: true, continuity: "REUNITED / WITH KAI" } });
  current.party = [{ name: "Iris" }];
  const packet = canonPacketFor(current, "Kai hỏi Iris cô vừa thấy gì.", {});
  assert.equal(packet.dependencies.character, true);
  assert.match(packet.characterCanon, /IRIS \/ ARGUS — CHARACTER CANON R05/);
  assert.match(packet.writingCanon, /HỘI THOẠI/);
});

test("successful loot roll loads item rules even when action wording is generic", () => {
  const deps = canonDependenciesFor(state(), "Kai lục khu vực kỹ hơn.", {
    loot: { success: true },
  });
  const packet = canonPacketFor(state(), "Kai lục khu vực kỹ hơn.", {
    loot: { success: true },
  });
  assert.equal(deps.item, true);
  assert.match(packet.worldCanon, /ITEM \/ RESOURCE HARD LOCK/);
});
