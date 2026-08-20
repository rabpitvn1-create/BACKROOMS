import { applyTurnOperations } from "./state-ops.js";

const ACQUIRE_SIGNAL = /(?<![\p{L}\p{N}_])(nhặt|nhấc|lấy|cầm|thu hồi|tịch thu|nhận|cất|bỏ vào|đưa vào|thu vào|lưu trữ|store|pickup|take|receive)(?![\p{L}\p{N}_])/iu;
const STORE_SIGNAL = /(?<![\p{L}\p{N}_])(cất|bỏ vào|đưa vào|thu vào|lưu trữ|kho|omnivault|nhẫn vạn tàng|store|stored)(?![\p{L}\p{N}_])/iu;
const RESTORE_SIGNAL = /(?<![\p{L}\p{N}_])(hoàn nguyên|restore)(?![\p{L}\p{N}_])/iu;
const DENIAL_SIGNAL = /(?<![\p{L}\p{N}_])(không thể nhặt|không nhặt được|không thể lấy|không lấy được|không thể cất|không cất được|không thể hoàn nguyên|hoàn nguyên thất bại|không tồn tại|không có vật|cannot pick|cannot take|cannot store|cannot restore|restore failed)(?![\p{L}\p{N}_])/iu;
const GENERIC_REFERENCE = /^(nó|vật đó|thứ đó|đồ đó|cái đó|chiếc đó|it|that)$/iu;
const RESTRICTED_PICKUP = /(?<![\p{L}\p{N}_])(almond water|madgod|liquid pain|greek fire|royal ration|firesalt|memory juice|super almond|súng|gun|pistol|rifle|shotgun|magnum|revolver|đạn|ammo|lựu đạn|grenade|bom|bomb|dao|knife|kiếm|sword|blade|giáp|armor|helmet|mũ bảo hộ|module|core|nhẫn|ring|chìa|key|thẻ|card|artifact|cổ vật|thuốc|medkit|ration|khẩu phần)(?![\p{L}\p{N}_])/iu;

const DIRECT_PATTERNS = [
  /(?<![\p{L}\p{N}_])(?:nhặt|lấy|cầm|thu hồi|tịch thu|nhận)\s+(?:(?:một|cái|chiếc|con|quyển)\s+)?(.{2,120}?)(?=\s+(?:lên|ra|rồi|và|để|bỏ|cất|đưa|vào|cho|hoàn nguyên)\b|[,.;!?]|$)/iu,
  /(?<![\p{L}\p{N}_])(?:bỏ|cất|đưa|thu)\s+(?:(?:một|cái|chiếc|con|quyển)\s+)?(.{2,120}?)\s+(?:vào|trong)\s+(?:kho|omnivault|nhẫn vạn tàng|không gian lưu trữ)(?=[,.;!?]|$)/iu,
];

const INTRO_PATTERNS = [
  /(?<![\p{L}\p{N}_])(?:thấy|nhìn thấy|phát hiện)\s+(?:(?:một|cái|chiếc|con|quyển)\s+)?(.{2,120}?)(?=\s+(?:dưới|trên|bên|cạnh|ở|nằm|trong|ngay)\b|[,.;!?]|$)/iu,
  /(?<![\p{L}\p{N}_])(?:có)\s+(?:(?:một|cái|chiếc|con|quyển)\s+)?(.{2,120}?)(?=\s+(?:dưới|trên|bên|cạnh|ở|nằm|trong|ngay)\b|[,.;!?]|$)/iu,
];

const RESTORE_PATTERNS = [
  /(?<![\p{L}\p{N}_])(?:hoàn nguyên|restore)(?:\s+(?:lại|nó|vật đó|thứ đó|cái đó|chiếc đó))*\s+(?:thành|về)\s+(?:(?:một|cái|chiếc)\s+)?(.{2,120}?)(?=\s+(?:rồi|và|để|bỏ|cất|đưa|vào|trong)\b|[,.;!?]|$)/iu,
];

function cleanCandidate(value) {
  let candidate = String(value || "").trim();
  candidate = candidate.replace(/^[\s"'“”‘’]+|[\s"'“”‘’]+$/gu, "");
  candidate = candidate.replace(/^(?:một|cái|chiếc|con|quyển)\s+/iu, "").trim();
  candidate = candidate.replace(/\s+/gu, " ");
  if (!candidate || candidate.length < 2 || candidate.length > 120 || GENERIC_REFERENCE.test(candidate)) return "";
  return candidate;
}

function candidateFromPatterns(text, patterns) {
  for (const pattern of patterns) {
    const match = pattern.exec(text);
    const candidate = cleanCandidate(match?.[1]);
    if (candidate) return candidate;
  }
  return "";
}

function candidateFromAction(action) {
  const text = String(action || "");
  return candidateFromPatterns(text, DIRECT_PATTERNS) || candidateFromPatterns(text, INTRO_PATTERNS);
}

export function extractOmnivaultRestoreTarget(action) {
  const text = String(action || "");
  if (!RESTORE_SIGNAL.test(text)) return "";
  return candidateFromPatterns(text, RESTORE_PATTERNS);
}

function meaningfulTokens(value) {
  return String(value || "")
    .toLocaleLowerCase("vi")
    .split(/[^\p{L}\p{N}_]+/u)
    .map((token) => token.trim())
    .filter((token) => token.length >= 2 && !["một", "cái", "chiếc", "con", "quyển", "the", "an"].includes(token));
}

function tokenOverlap(left, right) {
  const leftTokens = meaningfulTokens(left);
  if (!leftTokens.length) return false;
  const rightSet = new Set(meaningfulTokens(right));
  const matches = leftTokens.filter((token) => rightSet.has(token)).length;
  const required = leftTokens.length === 1 ? 1 : Math.min(2, leftTokens.length);
  return matches >= required;
}

function findInventoryMatch(state, name) {
  const inventory = Array.isArray(state?.inventory) ? state.inventory : [];
  return inventory.find((item) => tokenOverlap(item?.name || item, name) && tokenOverlap(name, item?.name || item)) || null;
}

function inventoryHasName(state, name) {
  return Boolean(findInventoryMatch(state, name));
}

function replyConfirmsPickup(candidate, reply) {
  const text = String(reply || "");
  if (!candidate || !text || DENIAL_SIGNAL.test(text)) return false;
  if (!ACQUIRE_SIGNAL.test(text) && !STORE_SIGNAL.test(text)) return false;
  return tokenOverlap(candidate, text);
}

function replyConfirmsRestore(target, reply) {
  const text = String(reply || "");
  if (!target || !text || DENIAL_SIGNAL.test(text)) return false;
  if (!RESTORE_SIGNAL.test(text) && !STORE_SIGNAL.test(text)) return false;
  return tokenOverlap(target, text);
}

export function isMundaneConfirmedPickupName(name) {
  const value = cleanCandidate(name);
  return Boolean(value) && !RESTRICTED_PICKUP.test(value);
}

export function extractConfirmedPickupCandidate(action, reply) {
  if (!ACQUIRE_SIGNAL.test(String(action || ""))) return "";
  const source = candidateFromAction(action);
  if (!source || !replyConfirmsPickup(source, reply)) return "";
  return source;
}

export function confirmedPickupNarrativeIssues(action, reply) {
  const target = extractOmnivaultRestoreTarget(action);
  if (!target || !isMundaneConfirmedPickupName(target) || replyConfirmsRestore(target, reply)) return [];
  return [{
    rule: "omnivault_action_lock",
    severity: "hard",
    claim: `Omnivault restore -> ${target}`,
    reason: `Kai explicitly used Omnivault Restore on an ordinary inanimate object. Do not turn this into a survival/resource failure. Confirm the successful Restore into ${target}, then store it if the player said to store it.`,
  }];
}

function desiredInventoryOutcome(current, generated, action) {
  const reply = String(generated?.reply || "");
  const restoreTarget = extractOmnivaultRestoreTarget(action);
  if (restoreTarget && isMundaneConfirmedPickupName(restoreTarget) && replyConfirmsRestore(restoreTarget, reply)) {
    return { name: restoreTarget, basis: "omnivault_restore" };
  }
  const pickup = extractConfirmedPickupCandidate(action, reply);
  if (pickup && isMundaneConfirmedPickupName(pickup)) return { name: pickup, basis: "gm_confirmed_pickup" };
  return null;
}

export function reconcileConfirmedPickupOps(current, generated, action) {
  const outcome = desiredInventoryOutcome(current, generated, action);
  const existingOps = Array.isArray(generated?.ops) ? generated.ops.map((op) => ({ ...op })) : [];
  if (!outcome) return existingOps;

  const existingItem = findInventoryMatch(current, outcome.name);
  const previousQuantity = Number(existingItem?.quantity ?? 0);
  const finalQuantity = Number.isFinite(previousQuantity) && previousQuantity > 0 ? previousQuantity + 1 : 1;
  let matched = false;
  const ops = existingOps.map((op) => {
    if (op?.type !== "inventory_upsert" || !op?.item?.name || !tokenOverlap(op.item.name, outcome.name)) return op;
    matched = true;
    return {
      ...op,
      item: { ...op.item, name: outcome.name, quantity: Math.max(Number(op.item.quantity || 1), finalQuantity), state: op.item.state || "STORED" },
      basis: outcome.basis,
    };
  });

  if (!matched) {
    ops.push({
      type: "inventory_upsert",
      item: { name: outcome.name, quantity: finalQuantity, state: "STORED" },
      basis: outcome.basis,
    });
  }
  return ops;
}

export function applyTurnWithPickupReconcile(current, generated, action, rolls) {
  const outcome = desiredInventoryOutcome(current, generated, action);
  const reconciledOps = reconcileConfirmedPickupOps(current, generated, action);
  const result = applyTurnOperations(current, reconciledOps, { action, rolls });
  if (!outcome) return { ...result, ops: reconciledOps };

  const state = structuredClone(result.state);
  const accepted = [...result.accepted];
  const rejected = [];
  let promoted = false;

  for (const entry of result.rejected) {
    const op = entry?.op;
    const pickupName = op?.type === "inventory_upsert" ? op?.item?.name : "";
    const trustedBasis = op?.basis === "gm_confirmed_pickup" || op?.basis === "omnivault_restore";
    const eligible = !promoted
      && entry?.reason === "inventory_acquisition_not_established"
      && trustedBasis
      && isMundaneConfirmedPickupName(pickupName)
      && tokenOverlap(pickupName, outcome.name);
    if (!eligible) {
      rejected.push(entry);
      continue;
    }

    const inventory = Array.isArray(state.inventory) ? [...state.inventory] : [];
    const index = inventory.findIndex((item) => tokenOverlap(item?.name || item, pickupName) && tokenOverlap(pickupName, item?.name || item));
    if (index >= 0) {
      const previous = inventory[index];
      inventory[index] = { ...previous, ...op.item, quantity: Number(op.item?.quantity || Number(previous?.quantity || 1) + 1), state: op.item?.state || previous?.state || "STORED" };
    } else {
      inventory.push({ ...op.item, name: String(pickupName).trim(), quantity: Number(op.item?.quantity || 1), state: op.item?.state || "STORED" });
    }
    state.inventory = inventory;
    accepted.push(op);
    promoted = true;
  }

  return { state, accepted, rejected, ops: reconciledOps };
}
