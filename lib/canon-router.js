import { GAME_MASTER_CANON } from "./canon.js";
import { characterCanonFor } from "./character-canon.js";
import { levelFromState } from "./gameplay.js";
import { worldCanonFor } from "./world-canon.js";
import { WRITING_CANON } from "./writing-canon.js";

const ENTITY_SIGNAL = /\b(entity|hound|clump|duller|deathmoth|faceling|smiler|skin[- ]?stealer|window|beast|wretch|cable mimic|jeff|quái|thực thể|sinh vật|kẻ săn)\b/i;
const ITEM_SIGNAL = /\b(item|loot|almond|water|nước|chai|đồ|vật phẩm|crate|liquid pain|greek fire|madgod|omnivault|nhẫn|scan|copy|restore|upgrade|nhặt|lấy|cất|inventory)\b/i;
const COMBAT_SIGNAL = /\b(bắn|đánh|đấm|đá|chiến đấu|tấn công|phản công|né|devil trigger|guilty crown|white wraith|magnum|talon|phantom|combat|shoot|attack|fight)\b/i;
const OMNIVAULT_SIGNAL = /\b(omnivault|nhẫn vạn tàng|scan|copy|restore|upgrade|hoàn nguyên|nâng cấp|sao chép|quét)\b/i;
const DIALOGUE_SIGNAL = /[“”"']|\b(hỏi|nói|trả lời|gọi|bảo|thuyết phục|xin|cảm ơn|xin lỗi|talk|ask|say|tell)\b/i;

function asText(value) {
  return typeof value === "string" ? value : "";
}

function section(text, start, end = null) {
  const source = asText(text);
  const startIndex = source.indexOf(start);
  if (startIndex < 0) return "";
  const endIndex = end ? source.indexOf(end, startIndex + start.length) : -1;
  return source.slice(startIndex, endIndex >= 0 ? endIndex : source.length).trim();
}

function linesContaining(text, patterns, { includeHeaders = false } = {}) {
  return asText(text)
    .split("\n")
    .filter((line) => {
      const trimmed = line.trim();
      if (!trimmed) return false;
      if (includeHeaders && !trimmed.startsWith("-")) return true;
      return patterns.some((pattern) => pattern.test(trimmed));
    })
    .join("\n")
    .trim();
}

function currentRuntimeSignals(state, rolls) {
  const flags = state?.flags || {};
  const activeEntity = Number(flags.entitiesConfirmedLocal || 0) > 0
    || (Array.isArray(flags.entityRegistry) && flags.entityRegistry.length > 0)
    || rolls?.entityEncounter?.success === true;
  const itemEvent = rolls?.loot?.success === true
    || rolls?.almondWater?.success === true
    || rolls?.madGodSet?.success === true;
  return { activeEntity, itemEvent };
}

function compactGameMasterCanon(action) {
  const kernel = section(GAME_MASTER_CANON, "MAIN CAMPAIGN / NEW GAME HARD LOCK", "LEVEL 0 / INITIAL AREA");
  const fairness = section(GAME_MASTER_CANON, "ENCOUNTER FAIRNESS", "VĂN PHONG");
  const style = section(GAME_MASTER_CANON, "VĂN PHONG");
  const kai = section(GAME_MASTER_CANON, "KAI AKECHI / TWILIGHT — R05 HARD CANON", "ENCOUNTER FAIRNESS");

  const alwaysKaiPatterns = [
    /^- Tên Kai Akechi/i,
    /^- Tính cách:/i,
    /^- Độ khó hợp lệ/i,
  ];
  const combatPatterns = [
    /Thiện xạ UR\+/i,
    /Thể chất bán quỷ/i,
    /Sparda Core/i,
    /Devil Trigger/i,
    /Guilty Crown Override/i,
    /White Wraith Magnum/i,
    /Blackblood Armor/i,
  ];
  const omnivaultPatterns = [/Omnivault/i];

  const patterns = [...alwaysKaiPatterns];
  if (COMBAT_SIGNAL.test(action)) patterns.push(...combatPatterns);
  if (OMNIVAULT_SIGNAL.test(action) || ITEM_SIGNAL.test(action)) patterns.push(...omnivaultPatterns);

  const kaiSlice = linesContaining(kai, patterns, { includeHeaders: true });
  return [kernel, kaiSlice, fairness, style].filter(Boolean).join("\n\n");
}

function compactWorldCanon(levelNumber, action, state, rolls) {
  const full = worldCanonFor(levelNumber);
  const entityIndex = full.indexOf("ENTITY HARD LOCK");
  const itemIndex = full.indexOf("ITEM / RESOURCE HARD LOCK");
  const baseEnd = [entityIndex, itemIndex].filter((value) => value >= 0).sort((a, b) => a - b)[0] ?? full.length;
  const base = full.slice(0, baseEnd).trim();

  const { activeEntity, itemEvent } = currentRuntimeSignals(state, rolls);
  const needsEntity = activeEntity || ENTITY_SIGNAL.test(action);
  const needsItem = itemEvent || ITEM_SIGNAL.test(action);

  const entitySection = needsEntity && entityIndex >= 0
    ? full.slice(entityIndex, itemIndex >= 0 ? itemIndex : full.length).trim()
    : "";
  const itemSection = needsItem && itemIndex >= 0
    ? full.slice(itemIndex).trim()
    : "";

  return [base, entitySection, itemSection].filter(Boolean).join("\n\n");
}

function compactWritingCanon(action) {
  const pov = section(WRITING_CANON, "ĐIỂM NHÌN VÀ TRI THỨC", "VĂN XUÔI");
  const prose = section(WRITING_CANON, "VĂN XUÔI", "HỘI THOẠI");
  const dialogue = DIALOGUE_SIGNAL.test(action)
    ? section(WRITING_CANON, "HỘI THOẠI", "KINH DỊ SIÊU NHIÊN")
    : "";
  const horror = section(WRITING_CANON, "KINH DỊ SIÊU NHIÊN", "CỔNG CUỐI TRƯỚC KHI TRẢ REPLY");
  const gate = section(WRITING_CANON, "CỔNG CUỐI TRƯỚC KHI TRẢ REPLY");
  return [pov, prose, dialogue, horror, gate].filter(Boolean).join("\n\n");
}

export function canonDependenciesFor(state, action, rolls = null) {
  const text = asText(action);
  const level = levelFromState(state);
  const { activeEntity, itemEvent } = currentRuntimeSignals(state, rolls);
  return Object.freeze({
    level: String(level.number),
    world: true,
    entity: activeEntity || ENTITY_SIGNAL.test(text),
    item: itemEvent || ITEM_SIGNAL.test(text),
    character: Boolean(
      DIALOGUE_SIGNAL.test(text)
      || rolls?.irisReunion?.success === true
      || rolls?.syvialReunion?.success === true
      || (Array.isArray(state?.party) && state.party.length > 0)
    ),
    combat: COMBAT_SIGNAL.test(text),
    omnivault: OMNIVAULT_SIGNAL.test(text),
  });
}

export function canonPacketFor(state, action, rolls = null) {
  const level = levelFromState(state);
  const dependencies = canonDependenciesFor(state, action, rolls);
  return {
    dependencies,
    gameMasterCanon: compactGameMasterCanon(action),
    worldCanon: compactWorldCanon(level.number, action, state, rolls),
    characterCanon: characterCanonFor(state, rolls),
    writingCanon: compactWritingCanon(action),
  };
}
