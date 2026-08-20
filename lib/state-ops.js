import {
  canTransitionLevel,
  levelFromState,
  normalizeLevel,
  sameLevel,
} from "./gameplay.js";

const FLAG_ROOTS = new Set([
  "communication",
  "iris",
  "syvial",
  "jeff",
  "madGod",
  "exploration",
  "omnivault",
  "survivorRegistry",
  "entityRegistry",
  "survivorsConfirmed",
  "entitiesConfirmedLocal",
  "visualAreaKey",
  "visualEventKey",
  "entityEncounterKey",
  "reunionPath",
]);

const ACQUIRE_SIGNAL = /\b(nhặt|lấy|nhận|thu hồi|tịch thu|cất|bỏ vào|đưa vào|store|pickup|take|receive|copy|sao chép)\b/i;
const COPY_SIGNAL = /\b(copy|sao chép|nhân bản|omnivault)\b/i;
const DISPOSE_SIGNAL = /\b(ăn|uống|dùng hết|tiêu thụ|vứt|bỏ đi|đánh mất|mất|trao|đưa cho|cho |chuyển quyền|consume|discard|lose|give|transfer)\b/i;

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value));
}

function objectOr(value, fallback = {}) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : fallback;
}

function mergeObject(base, patch) {
  return { ...objectOr(base), ...objectOr(patch) };
}

function normalizedName(value) {
  const name = typeof value === "string" ? value : value?.name;
  return typeof name === "string" ? name.trim().toLowerCase() : "";
}

function itemName(value) {
  const name = typeof value === "string" ? value : value?.name;
  return typeof name === "string" ? name.trim() : "";
}

function indexByName(list, name) {
  const needle = normalizedName(name);
  return Array.isArray(list) ? list.findIndex((item) => normalizedName(item) === needle) : -1;
}

function continuityPathConfirmed(state, key) {
  const local = String(state?.flags?.[key]?.reunionPath || "").toUpperCase();
  const shared = String(state?.flags?.reunionPath?.[key] || "").toUpperCase();
  return /(CONFIRMED|DIRECT|ARRIVED|CONTACT ESTABLISHED)/.test(`${local} ${shared}`);
}

function mayAddCharacter(state, name, rolls) {
  const lowered = String(name || "").trim().toLowerCase();
  if (!lowered) return false;
  if (lowered.includes("iris")) return rolls?.irisReunion?.success === true || continuityPathConfirmed(state, "iris");
  if (lowered.includes("syvial")) return rolls?.syvialReunion?.success === true || continuityPathConfirmed(state, "syvial");
  return rolls?.survivor?.success === true;
}

function establishedInState(state, name) {
  const needle = String(name || "").trim().toLowerCase();
  if (!needle) return false;
  return JSON.stringify(state || {}).toLowerCase().includes(needle);
}

function allowedNewInventoryItem(state, item, action, rolls) {
  const name = itemName(item);
  if (!name || !ACQUIRE_SIGNAL.test(action)) return false;
  const lowered = name.toLowerCase();
  if (establishedInState(state, name)) return true;
  if (/madgod/.test(lowered)) {
    return state?.flags?.madGod?.spawned === true && establishedInState(state?.flags?.madGod, name);
  }
  if (/almond water/.test(lowered)) return rolls?.almondWater?.success === true;
  return rolls?.loot?.success === true;
}

function applyInventoryUpsert(state, op, action, rolls, rejected) {
  const item = objectOr(op?.item, null);
  const name = itemName(item);
  if (!item || !name) {
    rejected.push({ op, reason: "invalid_inventory_item" });
    return;
  }

  const inventory = Array.isArray(state.inventory) ? state.inventory : [];
  const index = indexByName(inventory, name);
  if (index < 0) {
    if (!allowedNewInventoryItem(state, item, action, rolls)) {
      rejected.push({ op, reason: "inventory_acquisition_not_established" });
      return;
    }
    const quantity = Number(item.quantity ?? 1);
    inventory.push({ ...item, quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : 1 });
    state.inventory = inventory;
    return;
  }

  const previous = objectOr(inventory[index], { name });
  const previousQuantity = Number(previous.quantity ?? 1);
  const requestedQuantity = Number(item.quantity ?? previousQuantity);
  if (Number.isFinite(requestedQuantity)) {
    if (requestedQuantity > previousQuantity && !ACQUIRE_SIGNAL.test(action) && !COPY_SIGNAL.test(action)) {
      rejected.push({ op, reason: "inventory_quantity_increase_without_basis" });
      return;
    }
    if (requestedQuantity < previousQuantity && !DISPOSE_SIGNAL.test(action)) {
      rejected.push({ op, reason: "inventory_quantity_decrease_without_basis" });
      return;
    }
  }

  inventory[index] = { ...previous, ...item, name: previous.name || name };
  state.inventory = inventory;
}

function applyInventoryRemove(state, op, action, rejected) {
  const name = String(op?.name || "").trim();
  const inventory = Array.isArray(state.inventory) ? state.inventory : [];
  const index = indexByName(inventory, name);
  if (!name || index < 0) {
    rejected.push({ op, reason: "inventory_remove_missing_item" });
    return;
  }
  if (!DISPOSE_SIGNAL.test(action)) {
    rejected.push({ op, reason: "inventory_remove_without_basis" });
    return;
  }
  inventory.splice(index, 1);
  state.inventory = inventory;
}

function applyPlayerPatch(state, op, rejected) {
  const patch = objectOr(op?.patch, null);
  if (!patch) {
    rejected.push({ op, reason: "invalid_player_patch" });
    return;
  }
  const allowed = {};
  for (const key of ["hp", "condition", "needs", "weapon", "armor"]) {
    if (Object.hasOwn(patch, key)) allowed[key] = clone(patch[key]);
  }
  if (Object.hasOwn(allowed, "needs")) {
    allowed.needs = mergeObject(state?.player?.needs, allowed.needs);
  }
  state.player = {
    ...objectOr(state.player),
    ...allowed,
    name: state?.player?.name || "Kai Akechi",
    codename: state?.player?.codename || "Twilight",
  };
}

function applyPartyUpsert(state, op, rolls, rejected) {
  const member = typeof op?.member === "string" ? { name: op.member } : objectOr(op?.member, null);
  const name = member?.name;
  if (typeof name !== "string" || !name.trim()) {
    rejected.push({ op, reason: "invalid_party_member" });
    return;
  }
  const party = Array.isArray(state.party) ? state.party : [];
  const index = indexByName(party, name);
  if (index >= 0) {
    party[index] = { ...objectOr(party[index], { name }), ...member };
    state.party = party;
    return;
  }
  if (!mayAddCharacter(state, name, rolls)) {
    rejected.push({ op, reason: "party_add_not_permitted" });
    return;
  }
  party.push(member);
  state.party = party;
}

function applyPartyRemove(state, op, action, rejected) {
  const name = String(op?.name || "").trim();
  const party = Array.isArray(state.party) ? state.party : [];
  const index = indexByName(party, name);
  if (!name || index < 0) {
    rejected.push({ op, reason: "party_remove_missing_member" });
    return;
  }
  if (!/\b(rời|tách|chia tay|bỏ lại|ở lại|leave|separate|depart)\b/i.test(action)) {
    rejected.push({ op, reason: "party_remove_without_basis" });
    return;
  }
  party.splice(index, 1);
  state.party = party;
}

function gatedFlagPatch(current, next, root, value, rolls, rejected, op) {
  if (!FLAG_ROOTS.has(root)) {
    rejected.push({ op, reason: "flag_root_not_allowed" });
    return;
  }

  if (root === "survivorsConfirmed") {
    const before = Number(current?.flags?.survivorsConfirmed || 0);
    const after = Number(value);
    if (after > before && rolls?.survivor?.success !== true) {
      rejected.push({ op, reason: "survivor_count_without_roll" });
      return;
    }
  }
  if (root === "entitiesConfirmedLocal") {
    const before = Number(current?.flags?.entitiesConfirmedLocal || 0);
    const after = Number(value);
    if (after > before && rolls?.entityEncounter?.success !== true) {
      rejected.push({ op, reason: "entity_count_without_roll" });
      return;
    }
  }
  if (root === "survivorRegistry" && rolls?.survivor?.success !== true) {
    const before = Array.isArray(current?.flags?.survivorRegistry) ? current.flags.survivorRegistry.length : 0;
    const after = Array.isArray(value) ? value.length : before;
    if (after > before) {
      rejected.push({ op, reason: "survivor_registry_without_roll" });
      return;
    }
  }
  if (root === "entityRegistry" && rolls?.entityEncounter?.success !== true) {
    const before = Array.isArray(current?.flags?.entityRegistry) ? current.flags.entityRegistry.length : 0;
    const after = Array.isArray(value) ? value.length : before;
    if (after > before) {
      rejected.push({ op, reason: "entity_registry_without_roll" });
      return;
    }
  }
  if ((root === "iris" || root === "syvial") && objectOr(value)?.continuity) {
    const before = String(current?.flags?.[root]?.continuity || "").toUpperCase();
    const after = String(value.continuity || "").toUpperCase();
    const becamePresent = !/(REUNITED|WITH KAI|TOGETHER|PRESENT)/.test(before)
      && /(REUNITED|WITH KAI|TOGETHER|PRESENT)/.test(after);
    const allowed = rolls?.[`${root}Reunion`]?.success === true || continuityPathConfirmed(current, root);
    if (becamePresent && !allowed) {
      rejected.push({ op, reason: `${root}_reunion_not_permitted` });
      return;
    }
  }
  if (root === "madGod" && objectOr(value)?.spawned === true) {
    if (current?.flags?.madGod?.spawned !== true && rolls?.madGodSet?.success !== true) {
      rejected.push({ op, reason: "madgod_spawn_without_roll" });
      return;
    }
  }

  const flags = objectOr(next.flags);
  if (value && typeof value === "object" && !Array.isArray(value)) {
    flags[root] = mergeObject(flags[root], value);
  } else {
    flags[root] = clone(value);
  }
  next.flags = flags;
}

export function applyTurnOperations(current, operations, { action = "", rolls = null } = {}) {
  const next = clone(current);
  const rejected = [];
  const accepted = [];
  const ops = Array.isArray(operations) ? operations : [];

  for (const raw of ops.slice(0, 64)) {
    const op = objectOr(raw, null);
    if (!op || typeof op.type !== "string") {
      rejected.push({ op: raw, reason: "invalid_operation" });
      continue;
    }

    const beforeRejected = rejected.length;
    switch (op.type) {
      case "set_location": {
        const value = String(op.value || "").trim();
        if (!value || value.length > 500) rejected.push({ op, reason: "invalid_location" });
        else next.location = value;
        break;
      }
      case "set_level": {
        const level = normalizeLevel(op.level);
        const currentLevel = levelFromState(current);
        if (!level) rejected.push({ op, reason: "invalid_level" });
        else if (!sameLevel(currentLevel, level) && !canTransitionLevel(current, rolls)) {
          rejected.push({ op, reason: "level_transition_not_permitted" });
        } else {
          next.level = level;
          next.flags = { ...objectOr(next.flags), currentLevel: level };
        }
        break;
      }
      case "patch_player":
        applyPlayerPatch(next, op, rejected);
        break;
      case "inventory_upsert":
        applyInventoryUpsert(next, op, action, rolls, rejected);
        break;
      case "inventory_remove":
        applyInventoryRemove(next, op, action, rejected);
        break;
      case "party_upsert":
        applyPartyUpsert(next, op, rolls, rejected);
        break;
      case "party_remove":
        applyPartyRemove(next, op, action, rejected);
        break;
      case "flag_patch":
        gatedFlagPatch(current, next, String(op.root || ""), op.value, rolls, rejected, op);
        break;
      default:
        rejected.push({ op, reason: "operation_type_not_allowed" });
        break;
    }

    if (rejected.length === beforeRejected) accepted.push(op);
  }

  return { state: next, accepted, rejected };
}
