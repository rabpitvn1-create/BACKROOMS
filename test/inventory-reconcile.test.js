import test from "node:test";
import assert from "node:assert/strict";
import {
  applyTurnWithPickupReconcile,
  extractConfirmedPickupCandidate,
  reconcileConfirmedPickupOps,
} from "../lib/inventory-reconcile.js";

function baseState() {
  return {
    level: { number: "0", name: "The Lobby" },
    location: "Level 0 / The Lobby",
    player: { name: "Kai Akechi", codename: "Twilight" },
    party: [],
    inventory: [
      { name: "White Wraith Magnum", quantity: 1, state: "CARRIED" },
      { name: "Blackblood Armor & linked modules", quantity: 1, state: "INTACT" },
      { name: "Omnivault Ring / Nhẫn Vạn Tàng", quantity: 1, state: "INTACT" },
    ],
    flags: {
      currentLevel: { number: "0", name: "The Lobby" },
      exploration: { visibleItems: [], exitProgress: "NO CONFIRMED CLUE" },
      omnivault: {},
      madGod: { spawned: false },
    },
    log: [],
  };
}

const bottleAction = "Thấy một vỏ chai rỗng dưới chân, hắn nhặt lên, bỏ vào kho";
const bottleReply = "Kai nhìn xuống chiếc vỏ chai nhựa rỗng nằm bên chân. Hắn nhấc nó lên kiểm tra. Nhẫn Vạn Tàng thu gọn chiếc vỏ chai rỗng vào không gian lưu trữ để dự phòng.";

test("screenshot regression: confirmed empty bottle pickup is reconciled into inventory", () => {
  const generated = { reply: bottleReply, ops: [] };
  const result = applyTurnWithPickupReconcile(baseState(), generated, bottleAction, { loot: { success: false } });
  assert.equal(result.rejected.length, 0);
  assert.equal(result.accepted.some((op) => op.type === "inventory_upsert" && op.basis === "gm_confirmed_pickup"), true);
  assert.equal(result.state.inventory.some((item) => /vỏ chai rỗng/i.test(item.name)), true);
});

test("pickup candidate survives descriptive words inserted by GM reply", () => {
  assert.equal(extractConfirmedPickupCandidate(bottleAction, bottleReply), "vỏ chai rỗng");
});

test("existing model inventory op is marked as GM-confirmed instead of duplicated", () => {
  const generated = {
    reply: bottleReply,
    ops: [{ type: "inventory_upsert", item: { name: "Vỏ chai nhựa rỗng", quantity: 1, state: "STORED" }, basis: "semantic_inference" }],
  };
  const ops = reconcileConfirmedPickupOps(baseState(), generated, bottleAction);
  assert.equal(ops.length, 1);
  assert.equal(ops[0].basis, "gm_confirmed_pickup");
});

test("GM denial never creates an inventory item", () => {
  const generated = { reply: "Kai cúi xuống nhưng không thể nhặt vỏ chai rỗng; nó kẹt dưới lớp sàn.", ops: [] };
  const result = applyTurnWithPickupReconcile(baseState(), generated, bottleAction, { loot: { success: false } });
  assert.equal(result.state.inventory.some((item) => /vỏ chai rỗng/i.test(item.name)), false);
  assert.equal(result.accepted.length, 0);
});

test("restricted weapon cannot use mundane pickup reconciliation to bypass loot gates", () => {
  const action = "Thấy một khẩu súng trường dưới chân, Kai nhặt lên rồi bỏ vào kho.";
  const generated = { reply: "Kai nhặt khẩu súng trường dưới chân và cất nó vào Nhẫn Vạn Tàng.", ops: [] };
  const result = applyTurnWithPickupReconcile(baseState(), generated, action, { loot: { success: false } });
  assert.equal(result.state.inventory.some((item) => /súng trường/i.test(item.name)), false);
  assert.equal(result.accepted.length, 0);
});
