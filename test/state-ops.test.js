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
      hp: null,
      condition: "Ổn định",
      needs: { thirst: "NONE CONFIRMED" },
      weapon: "White Wraith Magnum — CARRIED",
      armor: "Blackblood Armor & linked modules — INTACT",
    },
    party: [],
    inventory: [
      { name: "White Wraith Magnum", quantity: 1, state: "CARRIED" },
      { name: "Blackblood Armor & linked modules", quantity: 1, state: "INTACT" },
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
      exploration: { exitProgress: "NO CONFIRMED CLUE", visibleItems: [] },
      omnivault: {},
    },
    log: [{ role: "gm", text: "Kai nhìn thấy Magic Rifle cạnh tường." }],
  };
}

test("identity and unestablished player fields cannot be overwritten", () => {
  const result = applyTurnOperations(baseState(), [{
    type: "patch_player",
    patch: { name: "Wrong", codename: "Wrong", hp: 999999 },
  }], { action: "Kai đi tiếp.", rolls: {} });
  assert.equal(result.state.player.name, "Kai Akechi");
  assert.equal(result.state.player.codename, "Twilight");
  assert.equal(result.state.player.hp, null);
  assert.ok(result.rejected.length > 0);
});

test("freeform log cannot establish a hallucinated inventory item", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "inventory_upsert", item: { name: "Magic Rifle", quantity: 1 } },
  ], { action: "Kai nhặt Magic Rifle.", rolls: { loot: { success: false } } });
  assert.equal(result.rejected[0]?.reason, "inventory_acquisition_not_established");
  assert.equal(result.state.inventory.some((item) => item.name === "Magic Rifle"), false);
});

test("structured visible scene item can be acquired explicitly", () => {
  const current = baseState();
  current.flags.exploration.visibleItems = [{ name: "chai kim loại màu xám" }];
  const result = applyTurnOperations(current, [
    { type: "inventory_upsert", item: { name: "chai kim loại màu xám", quantity: 1, state: "STORED" } },
  ], { action: "Kai nhặt chai kim loại màu xám rồi cất vào Omnivault.", rolls: { loot: { success: false } } });
  assert.equal(result.rejected.length, 0);
  assert.equal(result.state.inventory.some((item) => /chai kim loại/i.test(item.name)), true);
});

test("inventory cannot silently disappear", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "inventory_remove", name: "White Wraith Magnum" },
  ], { action: "Kai nhìn quanh căn phòng." });
  assert.equal(result.rejected[0]?.reason, "inventory_remove_without_basis");
  assert.equal(result.state.inventory.some((item) => item.name === "White Wraith Magnum"), true);
});

test("unowned weapon cannot be equipped", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "patch_player", patch: { weapon: "Magic Rifle" } },
  ], { action: "Kai trang bị Magic Rifle.", rolls: {} });
  assert.equal(result.state.player.weapon, "White Wraith Magnum — CARRIED");
  assert.equal(result.rejected[0]?.reason, "weapon_change_not_authorized");
});

test("exploration cannot manufacture READY exit state without successful probe", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "flag_patch", root: "exploration", value: { exitProgress: "READY", exitCandidate: "Door X" } },
  ], { action: "Kai đi tiếp.", rolls: { exitProbe: { success: false } } });
  assert.equal(result.state.flags.exploration.exitProgress, "NO CONFIRMED CLUE");
  assert.equal(result.rejected[0]?.reason, "exit_candidate_without_successful_probe");
});

test("exit progress cannot jump directly from none to ready even on one successful probe", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "flag_patch", root: "exploration", value: { exitProgress: "READY" } },
  ], { action: "Kai khảo sát lối thoát.", rolls: { exitProbe: { success: true } } });
  assert.equal(result.state.flags.exploration.exitProgress, "NO CONFIRMED CLUE");
  assert.equal(result.rejected[0]?.reason, "exit_progress_invalid_jump");
});

test("reunion operation is rejected without locked roll or continuity path", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "flag_patch", root: "iris", value: { continuity: "REUNITED / WITH KAI" } },
    { type: "party_upsert", member: { name: "Iris" } },
  ], { action: "Kai đi tiếp.", rolls: { irisReunion: { success: false } } });
  assert.equal(result.state.flags.iris.continuity, "SEPARATED FROM KAI");
  assert.equal(result.state.party.length, 0);
  assert.equal(result.rejected.length, 2);
});

test("reunion operation is accepted with successful locked roll", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "flag_patch", root: "iris", value: { continuity: "REUNITED / WITH KAI" } },
    { type: "party_upsert", member: { name: "Iris" } },
  ], { action: "Kai tiến vào khu vực mới.", rolls: { irisReunion: { success: true } } });
  assert.equal(result.state.flags.iris.continuity, "REUNITED / WITH KAI");
  assert.equal(result.state.party[0].name, "Iris");
});

test("reunion path cannot self-authorize without reunion roll", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "flag_patch", root: "reunionPath", value: { iris: "CONFIRMED DIRECT" } },
  ], { action: "Kai đi tiếp.", rolls: { irisReunion: { success: false } } });
  assert.equal(result.rejected[0]?.reason, "iris_reunion_path_not_permitted");
});

test("level changes remain server-gated", () => {
  const result = applyTurnOperations(baseState(), [
    { type: "set_level", level: { number: "2", name: "Pipe Dreams" } },
  ], { action: "Kai tuyên bố mình đã sang Level 2.", rolls: { exitProbe: { success: false } } });
  assert.equal(result.state.level.number, "1");
  assert.equal(result.rejected[0]?.reason, "level_transition_not_permitted");
});
