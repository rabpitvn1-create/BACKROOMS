import test from "node:test";
import assert from "node:assert/strict";
import { applyTurnOperations } from "../lib/state-ops.js";

function baseState() {
  return {
    level: { number: "1", name: "Parking Zone" },
    location: "Level 1 / Maintenance Hall",
    player: {
      name: "Kai Akechi",
      codename: "Twilight",
      condition: "Ổn định",
      needs: {},
    },
    party: [],
    inventory: [
      { name: "White Wraith Magnum", quantity: 1, state: "CARRIED" },
      { name: "Omnivault Ring / Nhẫn Vạn Tàng", quantity: 1, state: "INTACT" },
    ],
    flags: {
      currentLevel: { number: "1", name: "Parking Zone" },
      survivorsConfirmed: 0,
      survivorRegistry: [],
      entitiesConfirmedLocal: 0,
      entityRegistry: [],
      iris: { exists: true, continuity: "SEPARATED FROM KAI" },
      syvial: { exists: true, continuity: "SEPARATED FROM KAI" },
      madGod: { spawned: false },
      exploration: {},
      omnivault: {},
    },
    log: [
      { role: "gm", text: "Trên bàn có một chai kim loại màu xám." },
    ],
  };
}

test("identity fields cannot be overwritten by player patch", () => {
  const current = baseState();
  const result = applyTurnOperations(current, [
    {
      type: "patch_player",
      patch: {
        name: "Wrong Name",
        codename: "Wrong",
        condition: "Bị thương nhẹ",
      },
    },
  ]);
  assert.equal(result.state.player.name, "Kai Akechi");
  assert.equal(result.state.player.codename, "Twilight");
  assert.equal(result.state.player.condition, "Bị thương nhẹ");
});

test("new inventory cannot appear without acquisition basis", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "inventory_upsert", item: { name: "Magic Rifle", quantity: 1 } },
  ], {
    action: "Kai đi tiếp.",
    rolls: { loot: { success: false } },
  });
  assert.equal(result.rejected[0]?.reason, "inventory_acquisition_not_established");
  assert.equal(result.state.inventory.some((item) => item.name === "Magic Rifle"), false);
});

test("established scene item can be acquired explicitly", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "inventory_upsert", item: { name: "chai kim loại màu xám", quantity: 1, state: "STORED" } },
  ], {
    action: "Kai nhặt chai kim loại màu xám rồi cất vào Omnivault.",
    rolls: { loot: { success: false } },
  });
  assert.equal(result.rejected.length, 0);
  assert.equal(result.state.inventory.some((item) => /chai kim loại/i.test(item.name)), true);
});

test("inventory cannot silently disappear", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "inventory_remove", name: "White Wraith Magnum" },
  ], {
    action: "Kai nhìn quanh căn phòng.",
  });
  assert.equal(result.rejected[0]?.reason, "inventory_remove_without_basis");
  assert.equal(result.state.inventory.some((item) => item.name === "White Wraith Magnum"), true);
});

test("reunion operation is rejected without the locked roll or continuity path", () => {
  const result = applyTurnOperations(baseState(), [
    {
      type: "flag_patch",
      root: "iris",
      value: { continuity: "REUNITED / WITH KAI" },
    },
    {
      type: "party_upsert",
      member: { name: "Iris" },
    },
  ], {
    action: "Kai đi tiếp.",
    rolls: { irisReunion: { success: false } },
  });
  assert.equal(result.state.flags.iris.continuity, "SEPARATED FROM KAI");
  assert.equal(result.state.party.length, 0);
  assert.equal(result.rejected.length, 2);
});

test("reunion operation is accepted with a successful locked roll", () => {
  const result = applyTurnOperations(baseState(), [
    {
      type: "flag_patch",
      root: "iris",
      value: { continuity: "REUNITED / WITH KAI" },
    },
    {
      type: "party_upsert",
      member: { name: "Iris" },
    },
  ], {
    action: "Kai tiến vào khu vực mới.",
    rolls: { irisReunion: { success: true } },
  });
  assert.equal(result.state.flags.iris.continuity, "REUNITED / WITH KAI");
  assert.equal(result.state.party[0].name, "Iris");
});

test("level changes remain server-gated", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "set_level", level: { number: "2", name: "Pipe Dreams" } },
  ], {
    action: "Kai tuyên bố mình đã sang Level 2.",
    rolls: { exitProbe: { success: false } },
  });
  assert.equal(result.state.level.number, "1");
  assert.equal(result.rejected[0]?.reason, "level_transition_not_permitted");
});
