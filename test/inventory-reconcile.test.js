import test from "node:test";
import assert from "node:assert/strict";
import {
  applyTurnWithPickupReconcile,
  confirmedPickupNarrativeIssues,
  extractConfirmedPickupCandidate,
  extractOmnivaultRestoreTarget,
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
const restoredBottleAction = "Thấy một vỏ chai rỗng dưới chân, hắn nhặt lên, hoàn nguyên lại thành chai nước, bỏ vào kho.";
const restoredBottleReply = "Kai nhấc chiếc vỏ chai rỗng lên. Luồng sáng từ Nhẫn Vạn Tàng quét qua, hoàn nguyên nó thành một chai nước đầy rồi thu chai nước vào kho không gian.";

test("screenshot regression: confirmed empty bottle pickup is reconciled into inventory", () => {
  const generated = { reply: bottleReply, ops: [] };
  const result = applyTurnWithPickupReconcile(baseState(), generated, bottleAction, { loot: { success: false } });
  assert.equal(result.rejected.length, 0);
  assert.equal(result.accepted.some((op) => op.type === "inventory_upsert" && op.basis === "gm_confirmed_pickup"), true);
  assert.equal(result.state.inventory.some((item) => /vỏ chai rỗng/i.test(item.name)), true);
});

test("Kai Omnivault regression: empty bottle restored into water is stored as water without loot roll", () => {
  const generated = { reply: restoredBottleReply, ops: [] };
  const result = applyTurnWithPickupReconcile(baseState(), generated, restoredBottleAction, { loot: { success: false }, almondWater: { success: false } });
  assert.equal(extractOmnivaultRestoreTarget(restoredBottleAction), "chai nước");
  assert.equal(result.rejected.length, 0);
  assert.equal(result.accepted.some((op) => op.type === "inventory_upsert" && op.basis === "omnivault_restore"), true);
  assert.equal(result.state.inventory.some((item) => /^chai nước$/i.test(item.name)), true);
  assert.equal(result.state.inventory.some((item) => /^vỏ chai rỗng$/i.test(item.name)), false);
});

test("Omnivault restore removes a writer-proposed pre-restore source item", () => {
  const generated = {
    reply: restoredBottleReply,
    ops: [{ type: "inventory_upsert", item: { name: "vỏ chai rỗng", quantity: 1, state: "STORED" }, basis: "semantic_inference" }],
  };
  const ops = reconcileConfirmedPickupOps(baseState(), generated, restoredBottleAction);
  assert.equal(ops.length, 1);
  assert.equal(ops[0].basis, "omnivault_restore");
  assert.equal(ops[0].item.name, "chai nước");
});

test("valid explicit Omnivault restore forces narrative repair if writer denies or ignores it", () => {
  const issues = confirmedPickupNarrativeIssues(
    restoredBottleAction,
    "Kai cầm chiếc vỏ chai lên nhưng không thể hoàn nguyên nó vì cần đi tìm vật tư trước.",
  );
  assert.equal(issues.length, 1);
  assert.equal(issues[0].rule, "omnivault_action_lock");
});

test("pickup candidate ignores motion-only nhặt lên and falls back to the introduced object", () => {
  assert.equal(extractConfirmedPickupCandidate(bottleAction, bottleReply), "vỏ chai rỗng");
});

test("an explicit named pickup still wins over an unrelated earlier visible object", () => {
  const action = "Thấy một cánh cửa ở xa, Kai nhặt vỏ chai rỗng rồi bỏ vào kho.";
  const reply = "Kai nhặt vỏ chai rỗng rồi cất nó vào Nhẫn Vạn Tàng; cánh cửa vẫn ở phía xa.";
  assert.equal(extractConfirmedPickupCandidate(action, reply), "vỏ chai rỗng");
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
