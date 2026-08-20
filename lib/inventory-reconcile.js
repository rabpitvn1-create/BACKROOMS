const ACQUIRE_SIGNAL = /(?<![\p{L}\p{N}_])(nhặt|nhấc|lấy|cầm|thu hồi|tịch thu|nhận|cất|bỏ vào|đưa vào|thu vào|lưu trữ|store|pickup|take|receive)(?![\p{L}\p{N}_])/iu;
const STORE_SIGNAL = /(?<![\p{L}\p{N}_])(cất|bỏ vào|đưa vào|thu vào|lưu trữ|kho|omnivault|nhẫn vạn tàng|store|stored)(?![\p{L}\p{N}_])/iu;
const DENIAL_SIGNAL = /(?<![\p{L}\p{N}_])(không thể nhặt|không nhặt được|không thể lấy|không lấy được|không thể cất|không cất được|không tồn tại|không có vật|cannot pick|cannot take|cannot store)(?![\p{L}\p{N}_])/iu;
const GENERIC_REFERENCE = /^(nó|vật đó|thứ đó|đồ đó|cái đó|chiếc đó|it|that)$/iu;
const RESTRICTED_PICKUP = /(?<![\p{L}\p{N}_])(almond water|madgod|liquid pain|greek fire|royal ration|firesalt|memory juice|super almond|súng|gun|pistol|rifle|shotgun|magnum|revolver|đạn|ammo|lựu đạn|grenade|bom|bomb|dao|knife|kiếm|sword|blade|giáp|armor|helmet|mũ bảo hộ|module|core|nhẫn|ring|chìa|key|thẻ|card|artifact|cổ vật|thuốc|medkit|ration|khẩu phần)(?![\p{L}\p{N}_])/iu;

const DIRECT_PATTERNS = [
  /(?<![\p{L}\p{N}_])(?:nhặt|lấy|cầm|thu hồi|tịch thu|nhận)\s+(?:(?:một|cái|chiếc|con|quyển|chai|lọ|hộp)\s+)?(.{2,120}?)(?=\s+(?:lên|ra|rồi|và|để|bỏ|cất|đưa|vào|cho)\b|[,.;!?]|$)/iu,
  /(?<![\p{L}\p{N}_])(?:bỏ|cất|đưa|thu)\s+(?:(?:một|cái|chiếc|con|quyển)\s+)?(.{2,120}?)\s+(?:vào|trong)\s+(?:kho|omnivault|nhẫn vạn tàng|không gian lưu trữ)(?=[,.;!?]|$)/iu,
];

const INTRO_PATTERNS = [
  /(?<![\p{L}\p{N}_])(?:thấy|nhìn thấy|phát hiện)\s+(?:(?:một|cái|chiếc|con|quyển|chai|lọ|hộp)\s+)?(.{2,120}?)(?=\s+(?:dưới|trên|bên|cạnh|ở|nằm|trong|ngay)\b|[,.;!?]|$)/iu,
  /(?<![\p{L}\p{N}_])(?:có)\s+(?:(?:một|cái|chiếc|con|quyển|chai|lọ|hộp)\s+)?(.{2,120}?)(?=\s+(?:dưới|trên|bên|cạnh|ở|nằm|trong|ngay)\b|[,.;!?]|$)/iu,
];

function cleanCandidate(value) {
  let candidate = String(value || "").trim();
  candidate = candidate.replace(/^[\s"'“”‘’]+|[\s"'“”‘’]+$/gu, "");
  candidate = candidate.replace(/^(?:một|cái|chiếc|con|quyển|lọ|hộp)\s+/iu, "").trim();
  candidate = candidate.replace(/\s+/gu, " ");
  if (!candidate || candidate.length < 2 || candidate.length > 120 || GENERIC_REFERENCE.test(candidate)) return "";
  return candidate;
}

function candidateFromAction(action) {
  const text = String(action || "");
  for (const pattern of DIRECT_PATTERNS) {
    const match = pattern.exec(text);
    const candidate = cleanCandidate(match?.[1]);
    if (candidate) return candidate;
  }
  for (const pattern of INTRO_PATTERNS) {
    const match = pattern.exec(text);
    const candidate = cleanCandidate(match?.[1]);
    if (candidate) return candidate;
  }
  return "";
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

function inventoryHasName(state, name) {
  const inventory = Array.isArray(state?.inventory) ? state.inventory : [];
  return inventory.some((item) => tokenOverlap(item?.name || item, name) && tokenOverlap(name, item?.name || item));
}

function replyConfirmsPickup(candidate, reply) {
  const text = String(reply || "");
  if (!candidate || !text || DENIAL_SIGNAL.test(text)) return false;
  if (!ACQUIRE_SIGNAL.test(text) && !STORE_SIGNAL.test(text)) return false;
  return tokenOverlap(candidate, text);
}

export function isMundaneConfirmedPickupName(name) {
  const value = cleanCandidate(name);
  return Boolean(value) && !RESTRICTED_PICKUP.test(value);
}

export function extractConfirmedPickupCandidate(action, reply) {
  if (!ACQUIRE_SIGNAL.test(String(action || ""))) return "";
  const candidate = candidateFromAction(action);
  if (!candidate || !replyConfirmsPickup(candidate, reply)) return "";
  return candidate;
}

export function reconcileConfirmedPickupOps(current, generated, action) {
  const reply = String(generated?.reply || "");
  const candidate = extractConfirmedPickupCandidate(action, reply);
  const existingOps = Array.isArray(generated?.ops) ? generated.ops.map((op) => ({ ...op })) : [];
  if (!candidate || !isMundaneConfirmedPickupName(candidate) || inventoryHasName(current, candidate)) return existingOps;

  let matched = false;
  const ops = existingOps.map((op) => {
    if (op?.type !== "inventory_upsert" || !op?.item?.name || !tokenOverlap(op.item.name, candidate)) return op;
    matched = true;
    return { ...op, basis: "gm_confirmed_pickup" };
  });

  if (!matched) {
    ops.push({
      type: "inventory_upsert",
      item: { name: candidate, quantity: 1, state: "STORED" },
      basis: "gm_confirmed_pickup",
    });
  }
  return ops;
}
