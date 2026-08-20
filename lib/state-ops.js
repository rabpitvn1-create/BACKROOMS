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

const PARTY_FIELDS = new Set([
  "name", "id", "nationality", "role", "condition", "status", "emotion", "attitude",
  "knowledge", "inventory", "continuity",
]);
const NEED_FIELDS = new Set(["thirst", "hunger", "fatigue", "sleepDeprivation"]);
const ACQUIRE_SIGNAL = /(?<![\p{L}\p{N}_])(nhặt|lấy|nhận|thu hồi|tịch thu|cất|bỏ vào|đưa vào|store|pickup|take|receive|copy|sao chép)(?![\p{L}\p{N}_])/iu;
const COPY_SIGNAL = /(?<![\p{L}\p{N}_])(copy|sao chép|nhân bản|omnivault)(?![\p{L}\p{N}_])/iu;
const DISPOSE_SIGNAL = /(?<![\p{L}\p{N}_])(ăn|uống|dùng hết|tiêu thụ|vứt|bỏ đi|đánh mất|mất|trao|đưa cho|cho|chuyển quyền|consume|discard|lose|give|transfer)(?![\p{L}\p{N}_])/iu;
const PARTY_REMOVE_SIGNAL = /(?<![\p{L}\p{N}_])(rời|tách|chia tay|bỏ lại|ở lại|leave|separate|depart)(?![\p{L}\p{N}_])/iu;
const GEAR_SIGNAL = /(?<![\p{L}\p{N}_])(rút|cất|trang bị|mặc|cởi|tháo|đeo|holster|draw|equip|unequip|wear|remove)(?![\p{L}\p{N}_])/iu;
const NEED_SIGNAL = /(?<![\p{L}\p{N}_])(ăn|uống|nghỉ|ngủ|băng bó|chữa|hồi phục|eat|drink|rest|sleep|heal|treat)(?![\p{L}\p{N}_])/iu;
const COMBAT_SIGNAL = /(?<![\p{L}\p{N}_])(bắn|đánh|đấm|đá|tấn công|phản công|chiến đấu|né|shoot|attack|fight|combat)(?![\p{L}\p{N}_])/iu;
const OMNIVAULT_SIGNAL = /(?<![\p{L}\p{N}_])(omnivault|nhẫn vạn tàng|scan|copy|restore|upgrade|quét|sao chép|hoàn nguyên|nâng cấp)(?![\p{L}\p{N}_])/iu;
const COMMUNICATION_SIGNAL = /(?<![\p{L}\p{N}_])(gọi|liên lạc|radio|kênh|tín hiệu|call|contact|signal|channel)(?![\p{L}\p{N}_])/iu;
const READY_PROGRESS = /(READY|GUARANTEED|CONDITION MET|TRANSITION AVAILABLE)/i;
const PRESENT_CONTINUITY = /(REUNITED|WITH KAI|TOGETHER|PRESENT)/i;
const CONFIRMED_PATH = /(CONFIRMED|DIRECT|ARRIVED|CONTACT ESTABLISHED)/i;

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
  return typeof name === "string" ? name.trim().toLocaleLowerCase("vi") : "";
}

function itemName(value) {
  const name = typeof value === "string" ? value : value?.name;
  return typeof name === "string" ? name.trim() : "";
}

function indexByName(list, name) {
  const needle = normalizedName(name);
  return Array.isArray(list) ? list.findIndex((item) => normalizedName(item) === needle) : -1;
}

function boundedString(value, max = 300) {
  return typeof value === "string" && value.trim() && value.length <= max;
}

function continuityPathConfirmed(state, key) {
  const local = String(state?.flags?.[key]?.reunionPath || "").toUpperCase();
  const shared = String(state?.flags?.reunionPath?.[key] || "").toUpperCase();
  return CONFIRMED_PATH.test(`${local} ${shared}`);
}

function mayAddCharacter(state, name, rolls) {
  const lowered = String(name || "").trim().toLocaleLowerCase("vi");
  if (!lowered) return false;
  if (lowered.includes("iris")) return rolls?.irisReunion?.success === true || continuityPathConfirmed(state, "iris");
  if (lowered.includes("syvial")) return rolls?.syvialReunion?.success === true || continuityPathConfirmed(state, "syvial");
  return rolls?.survivor?.success === true;
}

function structuredContainsName(value, name, depth = 0) {
  if (depth > 5 || value == null) return false;
  const needle = String(name || "").trim().toLocaleLowerCase("vi");
  if (!needle) return false;
  if (typeof value === "string") return value.toLocaleLowerCase("vi").includes(needle);
  if (Array.isArray(value)) return value.some((entry) => structuredContainsName(entry, name, depth + 1));
  if (typeof value === "object") {
    return Object.entries(value).some(([key, entry]) => key !== "log" && structuredContainsName(entry, name, depth + 1));
  }
  return false;
}

function establishedStructuredItem(state, name) {
  if (indexByName(state?.inventory, name) >= 0) return true;
  const exploration = state?.flags?.exploration || {};
  const sources = [
    exploration.visibleItems,
    exploration.discoveredItems,
    exploration.nearbyItems,
    state?.flags?.omnivault,
    state?.flags?.madGod,
  ];
  return sources.some((source) => structuredContainsName(source, name));
}

function allowedNewInventoryItem(state, item, action, rolls) {
  const name = itemName(item);
  if (!name || !ACQUIRE_SIGNAL.test(action)) return false;
  const lowered = name.toLocaleLowerCase("vi");
  const established = establishedStructuredItem(state, name);
  if (/madgod/.test(lowered)) return state?.flags?.madGod?.spawned === true && established;
  if (/almond water/.test(lowered)) return established || rolls?.almondWater?.success === true;
  if (COPY_SIGNAL.test(action)) return established;
  return established || rolls?.loot?.success === true;
}

function applyInventoryUpsert(state, op, action, rolls, rejected) {
  const item = objectOr(op?.item, null);
  const name = itemName(item);
  if (!item || !name || name.length > 240) {
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
    if (!Number.isFinite(quantity) || quantity <= 0 || quantity > 9999) {
      rejected.push({ op, reason: "invalid_inventory_quantity" });
      return;
    }
    inventory.push({ ...item, name, quantity });
    state.inventory = inventory;
    return;
  }

  const previous = objectOr(inventory[index], { name });
  const previousQuantity = Number(previous.quantity ?? 1);
  const requestedQuantity = Number(item.quantity ?? previousQuantity);
  if (!Number.isFinite(requestedQuantity) || requestedQuantity <= 0 || requestedQuantity > 9999) {
    rejected.push({ op, reason: "invalid_inventory_quantity" });
    return;
  }
  if (requestedQuantity > previousQuantity && !ACQUIRE_SIGNAL.test(action) && !COPY_SIGNAL.test(action)) {
    rejected.push({ op, reason: "inventory_quantity_increase_without_basis" });
    return;
  }
  if (requestedQuantity < previousQuantity && !DISPOSE_SIGNAL.test(action)) {
    rejected.push({ op, reason: "inventory_quantity_decrease_without_basis" });
    return;
  }

  inventory[index] = { ...previous, ...item, name: previous.name || name, quantity: requestedQuantity };
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

function gearMatchesInventory(value, inventory) {
  const text = String(value || "").trim().toLocaleLowerCase("vi");
  if (!text) return false;
  if (/^(none|unarmed|holstered|stored|không|đã cất)$/i.test(text)) return true;
  return (Array.isArray(inventory) ? inventory : []).some((item) => {
    const name = normalizedName(item);
    return name.length >= 4 && text.includes(name);
  });
}

function worldConsequenceBasis(state, action, rolls) {
  const activeEntity = Number(state?.flags?.entitiesConfirmedLocal || 0) > 0;
  return rolls?.hazard?.success === true
    || rolls?.entityEncounter?.success === true
    || (activeEntity && COMBAT_SIGNAL.test(action));
}

function applyPlayerPatch(state, op, action, rolls, rejected) {
  const patch = objectOr(op?.patch, null);
  if (!patch) {
    rejected.push({ op, reason: "invalid_player_patch" });
    return;
  }

  const current = objectOr(state.player);
  const candidate = clone(current);
  let changed = false;

  for (const key of Object.keys(patch)) {
    if (!["hp", "condition", "needs", "weapon", "armor"].includes(key)) {
      rejected.push({ op, reason: "player_field_not_allowed" });
      return;
    }
  }

  if (Object.hasOwn(patch, "hp")) {
    const beforeHp = current.hp;
    const afterHp = patch.hp;
    if (beforeHp == null) {
      if (afterHp != null) {
        rejected.push({ op, reason: "hp_system_not_established" });
        return;
      }
    } else {
      const beforeNumber = Number(beforeHp);
      const afterNumber = Number(afterHp);
      if (!Number.isFinite(beforeNumber) || !Number.isFinite(afterNumber) || afterNumber < 0) {
        rejected.push({ op, reason: "invalid_hp" });
        return;
      }
      const explicitMax = Number(current.maxHp);
      const maxHp = Number.isFinite(explicitMax) && explicitMax >= beforeNumber ? explicitMax : beforeNumber;
      if (afterNumber > maxHp && !NEED_SIGNAL.test(action)) {
        rejected.push({ op, reason: "hp_increase_without_recovery_basis" });
        return;
      }
      if (afterNumber < beforeNumber && !worldConsequenceBasis(state, action, rolls)) {
        rejected.push({ op, reason: "hp_decrease_without_world_basis" });
        return;
      }
      candidate.hp = Math.min(afterNumber, maxHp);
      changed ||= candidate.hp !== beforeHp;
    }
  }

  if (Object.hasOwn(patch, "condition")) {
    if (!boundedString(patch.condition, 240)) {
      rejected.push({ op, reason: "invalid_player_condition" });
      return;
    }
    if (patch.condition !== current.condition && !worldConsequenceBasis(state, action, rolls) && !NEED_SIGNAL.test(action)) {
      rejected.push({ op, reason: "condition_change_without_basis" });
      return;
    }
    candidate.condition = patch.condition.trim();
    changed ||= candidate.condition !== current.condition;
  }

  if (Object.hasOwn(patch, "needs")) {
    const needsPatch = objectOr(patch.needs, null);
    if (!needsPatch || !NEED_SIGNAL.test(action)) {
      rejected.push({ op, reason: "needs_change_without_basis" });
      return;
    }
    const nextNeeds = { ...objectOr(current.needs) };
    for (const [key, value] of Object.entries(needsPatch)) {
      if (!NEED_FIELDS.has(key) || !boundedString(value, 120)) {
        rejected.push({ op, reason: "invalid_player_needs" });
        return;
      }
      nextNeeds[key] = value.trim();
    }
    candidate.needs = nextNeeds;
    changed = true;
  }

  for (const key of ["weapon", "armor"]) {
    if (!Object.hasOwn(patch, key)) continue;
    if (!boundedString(patch[key], 300) || !GEAR_SIGNAL.test(action) || !gearMatchesInventory(patch[key], state.inventory)) {
      rejected.push({ op, reason: `${key}_change_not_authorized` });
      return;
    }
    candidate[key] = patch[key].trim();
    changed ||= candidate[key] !== current[key];
  }

  candidate.name = current.name || "Kai Akechi";
  candidate.codename = current.codename || "Twilight";
  if (changed) state.player = candidate;
}

function sanitizedPartyMember(member) {
  const clean = {};
  for (const [key, value] of Object.entries(member || {})) {
    if (!PARTY_FIELDS.has(key)) return null;
    clean[key] = clone(value);
  }
  return clean;
}

function applyPartyUpsert(state, op, rolls, rejected) {
  const raw = typeof op?.member === "string" ? { name: op.member } : objectOr(op?.member, null);
  const member = raw ? sanitizedPartyMember(raw) : null;
  const name = member?.name;
  if (!member || typeof name !== "string" || !name.trim() || name.length > 160) {
    rejected.push({ op, reason: "invalid_party_member" });
    return;
  }
  const party = Array.isArray(state.party) ? state.party : [];
  const index = indexByName(party, name);
  if (index >= 0) {
    party[index] = { ...objectOr(party[index], { name }), ...member, name: objectOr(party[index], {}).name || name.trim() };
    state.party = party;
    return;
  }
  if (!mayAddCharacter(state, name, rolls)) {
    rejected.push({ op, reason: "party_add_not_permitted" });
    return;
  }
  party.push({ ...member, name: name.trim() });
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
  if (!PARTY_REMOVE_SIGNAL.test(action)) {
    rejected.push({ op, reason: "party_remove_without_basis" });
    return;
  }
  party.splice(index, 1);
  state.party = party;
}

function registryAddsNewName(before, after) {
  if (!Array.isArray(after)) return true;
  const previous = new Set((Array.isArray(before) ? before : []).map(normalizedName).filter(Boolean));
  return after.some((entry) => {
    const name = normalizedName(entry);
    return name && !previous.has(name);
  });
}

function exitProgressRank(value) {
  const text = String(value || "").toUpperCase();
  if (READY_PROGRESS.test(text)) return 4;
  if (/(NEAR|ALMOST|VERY STRONG)/.test(text)) return 3;
  if (/(STRONG|CORRECT ROUTE)/.test(text)) return 2;
  if (/(CLUE|CANDIDATE|OPENED|OBSERVED|TRACKED)/.test(text)) return 1;
  return 0;
}

function validateExplorationPatch(current, value, rolls, rejected, op) {
  const patch = objectOr(value, null);
  if (!patch) {
    rejected.push({ op, reason: "invalid_exploration_patch" });
    return null;
  }
  const allowed = new Set([
    "currentArea", "exploredAreas", "clues", "eliminatedRoutes", "exitCandidate", "exitProgress",
    "visibleItems", "discoveredItems", "nearbyItems",
  ]);
  for (const key of Object.keys(patch)) {
    if (!allowed.has(key)) {
      rejected.push({ op, reason: "exploration_field_not_allowed" });
      return null;
    }
  }

  const before = objectOr(current?.flags?.exploration);
  const safe = clone(patch);
  if (Object.hasOwn(patch, "exitCandidate") && patch.exitCandidate !== before.exitCandidate) {
    if (rolls?.exitProbe?.success !== true || !boundedString(patch.exitCandidate, 300)) {
      rejected.push({ op, reason: "exit_candidate_without_successful_probe" });
      return null;
    }
  }
  if (Object.hasOwn(patch, "exitProgress") && patch.exitProgress !== before.exitProgress) {
    if (rolls?.exitProbe?.success !== true || !boundedString(patch.exitProgress, 160)) {
      rejected.push({ op, reason: "exit_progress_without_successful_probe" });
      return null;
    }
    const beforeRank = exitProgressRank(before.exitProgress);
    const afterRank = exitProgressRank(patch.exitProgress);
    if (afterRank <= beforeRank || afterRank > beforeRank + 1) {
      rejected.push({ op, reason: "exit_progress_invalid_jump" });
      return null;
    }
  }
  for (const key of ["visibleItems", "discoveredItems", "nearbyItems"]) {
    if (Object.hasOwn(patch, key)) {
      const discovery = rolls?.loot?.success === true || rolls?.almondWater?.success === true || rolls?.madGodSet?.success === true;
      if (!discovery || !Array.isArray(patch[key]) || patch[key].length > 32) {
        rejected.push({ op, reason: "item_discovery_without_roll" });
        return null;
      }
    }
  }
  return safe;
}

function validateReunionPathPatch(current, value, rolls, rejected, op) {
  const patch = objectOr(value, null);
  if (!patch) {
    rejected.push({ op, reason: "invalid_reunion_path" });
    return null;
  }
  for (const key of ["iris", "syvial"]) {
    if (!Object.hasOwn(patch, key)) continue;
    const after = String(patch[key] || "");
    const before = String(current?.flags?.reunionPath?.[key] || "");
    if (!CONFIRMED_PATH.test(before) && CONFIRMED_PATH.test(after) && rolls?.[`${key}Reunion`]?.success !== true) {
      rejected.push({ op, reason: `${key}_reunion_path_not_permitted` });
      return null;
    }
  }
  return clone(patch);
}

function validateCommunicationPatch(current, value, action, rejected, op) {
  const patch = objectOr(value, null);
  if (!patch || !COMMUNICATION_SIGNAL.test(action)) {
    rejected.push({ op, reason: "communication_change_without_basis" });
    return null;
  }
  for (const [key, status] of Object.entries(patch)) {
    if (!["blackBlood", "frontrooms", "iris", "syvial"].includes(key) || typeof status !== "string" || status.length > 120) {
      rejected.push({ op, reason: "invalid_communication_patch" });
      return null;
    }
    const before = String(current?.flags?.communication?.[key] || "").toUpperCase();
    const after = status.toUpperCase();
    if (!/ONLINE|CONNECTED|CONTACT ESTABLISHED/.test(before) && /ONLINE|CONNECTED|CONTACT ESTABLISHED/.test(after)) {
      rejected.push({ op, reason: "communication_online_not_established" });
      return null;
    }
  }
  return clone(patch);
}

function gatedFlagPatch(current, next, root, value, action, rolls, rejected, op) {
  if (!FLAG_ROOTS.has(root)) {
    rejected.push({ op, reason: "flag_root_not_allowed" });
    return;
  }

  let safeValue = clone(value);
  if (root === "exploration") {
    safeValue = validateExplorationPatch(current, value, rolls, rejected, op);
    if (!safeValue) return;
  }
  if (root === "reunionPath") {
    safeValue = validateReunionPathPatch(current, value, rolls, rejected, op);
    if (!safeValue) return;
  }
  if (root === "communication") {
    safeValue = validateCommunicationPatch(current, value, action, rejected, op);
    if (!safeValue) return;
  }
  if (root === "omnivault" && (!OMNIVAULT_SIGNAL.test(action) || !objectOr(value, null))) {
    rejected.push({ op, reason: "omnivault_change_without_basis" });
    return;
  }

  if (root === "survivorsConfirmed" || root === "entitiesConfirmedLocal") {
    const before = Number(current?.flags?.[root] || 0);
    const after = Number(value);
    if (!Number.isInteger(after) || after < before) {
      rejected.push({ op, reason: `${root}_invalid_count` });
      return;
    }
    const rollKey = root === "survivorsConfirmed" ? "survivor" : "entityEncounter";
    if (after > before && rolls?.[rollKey]?.success !== true) {
      rejected.push({ op, reason: `${root}_without_roll` });
      return;
    }
  }

  if (root === "survivorRegistry") {
    if (!Array.isArray(value) || value.length > 128) {
      rejected.push({ op, reason: "invalid_survivor_registry" });
      return;
    }
    if (registryAddsNewName(current?.flags?.survivorRegistry, value) && rolls?.survivor?.success !== true) {
      rejected.push({ op, reason: "survivor_registry_without_roll" });
      return;
    }
  }
  if (root === "entityRegistry") {
    if (!Array.isArray(value) || value.length > 128) {
      rejected.push({ op, reason: "invalid_entity_registry" });
      return;
    }
    if (registryAddsNewName(current?.flags?.entityRegistry, value) && rolls?.entityEncounter?.success !== true) {
      rejected.push({ op, reason: "entity_registry_without_roll" });
      return;
    }
  }

  if ((root === "iris" || root === "syvial") && objectOr(value, null)) {
    const patch = objectOr(value);
    const before = String(current?.flags?.[root]?.continuity || "").toUpperCase();
    const after = String(patch.continuity || before).toUpperCase();
    const becamePresent = !PRESENT_CONTINUITY.test(before) && PRESENT_CONTINUITY.test(after);
    const allowed = rolls?.[`${root}Reunion`]?.success === true || continuityPathConfirmed(current, root);
    if (becamePresent && !allowed) {
      rejected.push({ op, reason: `${root}_reunion_not_permitted` });
      return;
    }
    if (Object.hasOwn(patch, "reunionPath") && !CONFIRMED_PATH.test(String(current?.flags?.[root]?.reunionPath || ""))
      && CONFIRMED_PATH.test(String(patch.reunionPath || "")) && rolls?.[`${root}Reunion`]?.success !== true) {
      rejected.push({ op, reason: `${root}_reunion_path_not_permitted` });
      return;
    }
  }

  if (root === "madGod" && objectOr(value, null)?.spawned === true) {
    if (current?.flags?.madGod?.spawned !== true && rolls?.madGodSet?.success !== true) {
      rejected.push({ op, reason: "madgod_spawn_without_roll" });
      return;
    }
  }
  if (root === "jeff") {
    const registryHasJeff = structuredContainsName(current?.flags?.entityRegistry, "jeff");
    if (!registryHasJeff && rolls?.entityEncounter?.success !== true) {
      rejected.push({ op, reason: "jeff_change_without_encounter" });
      return;
    }
  }

  const flags = objectOr(next.flags);
  if (safeValue && typeof safeValue === "object" && !Array.isArray(safeValue)) {
    flags[root] = mergeObject(flags[root], safeValue);
  } else {
    flags[root] = clone(safeValue);
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
        applyPlayerPatch(next, op, action, rolls, rejected);
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
        gatedFlagPatch(current, next, String(op.root || ""), op.value, action, rolls, rejected, op);
        break;
      default:
        rejected.push({ op, reason: "operation_type_not_allowed" });
        break;
    }

    if (rejected.length === beforeRejected) accepted.push(op);
  }

  return { state: next, accepted, rejected };
}
