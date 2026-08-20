import { randomInt } from "node:crypto";
import { LEVEL_PROFILES, normalizeLevelNumber } from "./world-canon.js";

const PHYSICAL_ACTION = /(đi|bước|chạy|leo|mở|đóng|chạm|lục|tìm|kiểm tra|khảo sát|quét|scan|bắn|phá|đẩy|kéo|tiến|lùi|cúi|nhìn vào|bò|nhảy|đào|tháo|đập|vượt|đi qua)/i;
const SEARCH_ACTION = /(tìm|lục|khám phá|khảo sát|kiểm tra|quét|scan|mở|tháo|quan sát kỹ|rà)/i;
const WATER_ACTION = /(nước|water|almond|uống|khát|chai|vòi|hồ|fountain)/i;
const EXIT_ACTION = /(exit|lối thoát|thoát|cửa trắng|cánh cửa|ngưỡng|chuyển level|sang level|hành lang phía sau|đường ra)/i;
const META_ACTION = /^\s*\/(meta|status|state|inventory|party|rules?|help|save)\b|^(?:cho (?:tôi|mình) )?(?:xem|hiện|kiểm tra|nhắc lại)\s+(?:trạng thái|state|inventory|túi đồ|party|đội hình|save|luật|dice|roll|canon)\b|^(?:trạng thái|inventory|túi đồ|party|save|luật|dice|roll|canon)\s*(?:là gì|hiện tại|\?|$)/i;

export function normalizeLevel(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const number = typeof value.number === "number" || typeof value.number === "string"
    ? String(value.number).trim()
    : "";
  const name = typeof value.name === "string" ? value.name.trim() : "";
  if (!number || !name) return null;
  return { number, name: name.slice(0, 120) };
}

export function parseLevelText(value) {
  if (typeof value !== "string") return null;
  const match = value.match(/\bLevel\s+([^\s/—–-]+)\s*(?:\/|—|–|-)\s*([^—–\n]+?)(?:\s+[—–]\s+|$)/i);
  if (!match) return null;
  return { number: match[1].trim(), name: match[2].trim().slice(0, 120) };
}

export function levelFromState(state) {
  return normalizeLevel(state?.level)
    || normalizeLevel(state?.flags?.currentLevel)
    || parseLevelText(state?.location)
    || parseLevelText(state?.title)
    || { number: "0", name: LEVEL_PROFILES["0"].name };
}

export function sameLevel(a, b) {
  if (!a || !b) return false;
  return String(a.number).toLowerCase() === String(b.number).toLowerCase()
    && String(a.name).toLowerCase() === String(b.name).toLowerCase();
}

export function levelLabel(level) {
  return level ? `Level ${level.number} – ${level.name}` : null;
}

export function isGameplayTurn(action) {
  return typeof action === "string" && action.trim() && !META_ACTION.test(action.trim());
}

function partyHas(state, name) {
  return Array.isArray(state?.party) && state.party.some((member) => {
    const value = typeof member === "string" ? member : member?.name;
    return typeof value === "string" && value.toLowerCase().includes(name);
  });
}

function reunionEligible(state, key) {
  const record = state?.flags?.[key];
  if (record?.exists !== true) return false;
  const continuity = String(record?.continuity || "").toUpperCase();
  if (/(REUNITED|WITH KAI|TOGETHER|PRESENT)/.test(continuity)) return false;
  if (partyHas(state, key)) return false;
  if (record?.reunionEligible === false) return false;
  return /(SEPARATED|LOST|UNKNOWN)/.test(continuity) || !continuity;
}

function asThreshold(value, fallback) {
  const number = Number(value);
  return Number.isInteger(number) && number >= 0 && number <= 10000 ? number : fallback;
}

function exitThreshold(state) {
  const explicit = asThreshold(state?.flags?.exitChanceThreshold, -1);
  if (explicit >= 0) return explicit;

  const progress = String(
    state?.flags?.exitProgress
      || state?.flags?.exploration?.exitProgress
      || state?.flags?.exitCandidate?.progress
      || "",
  ).toUpperCase();
  if (/(READY|GUARANTEED|CONDITION MET|TRANSITION AVAILABLE)/.test(progress)) return 10000;
  if (/(NEAR|ALMOST|VERY STRONG)/.test(progress)) return 300;
  if (/(STRONG|CORRECT ROUTE)/.test(progress)) return 200;
  if (/(CLUE|CANDIDATE|OPENED|OBSERVED|TRACKED)/.test(progress)) return 100;
  return 20;
}

function chanceLabel(threshold, max, suffix = "") {
  if (threshold >= max) return `CANON CONDITION SATISFIED${suffix}`;
  const percent = (threshold / max) * 100;
  const digits = percent < 0.01 ? 4 : percent < 1 ? 2 : 2;
  return `${percent.toFixed(digits)}%${suffix}`;
}

function makeRecord({ dice, max, threshold, eligible, rng, suffix = "" }) {
  if (!eligible || threshold <= 0) {
    return {
      dice,
      chance: threshold <= 0 ? `0.00%${suffix}` : chanceLabel(threshold, max, suffix),
      raw: null,
      threshold,
      eligible: false,
      success: false,
    };
  }
  if (threshold >= max) {
    return {
      dice: "none",
      chance: chanceLabel(threshold, max, suffix),
      raw: null,
      threshold,
      eligible: true,
      success: true,
      guaranteedByState: true,
    };
  }
  const raw = rng(1, max + 1);
  return { dice, chance: chanceLabel(threshold, max, suffix), raw, threshold, eligible: true, success: raw <= threshold };
}

export function makeRolls(state, action, rng = randomInt) {
  const level = levelFromState(state);
  const number = normalizeLevelNumber(level.number);
  const profile = LEVEL_PROFILES[number];
  const physicalRisk = PHYSICAL_ACTION.test(action);
  const searches = SEARCH_ACTION.test(action);
  const searchesWater = WATER_ACTION.test(action) && searches;
  const probesExit = EXIT_ACTION.test(action) && (physicalRisk || searches);
  const survivorEligible = state?.flags?.survivorEncountersAllowed !== false;
  const entityEligible = physicalRisk && state?.flags?.entityEncountersAllowed !== false;
  const madGodEligible = searches
    && state?.flags?.madGod?.spawned !== true
    && state?.flags?.madGodDiscoveryAllowed !== false;
  const exitChance = exitThreshold(state);

  return {
    survivor: makeRecord({ dice: "d10000", max: 10000, threshold: 200, eligible: survivorEligible, rng }),
    irisReunion: makeRecord({ dice: "d1000000", max: 1000000, threshold: 25, eligible: reunionEligible(state, "iris"), rng }),
    syvialReunion: makeRecord({ dice: "d1000000", max: 1000000, threshold: 25, eligible: reunionEligible(state, "syvial"), rng }),
    hazard: makeRecord({ dice: "d10000", max: 10000, threshold: profile.hazardThreshold, eligible: physicalRisk, rng }),
    entityEncounter: makeRecord({ dice: "d10000", max: 10000, threshold: profile.entityThreshold, eligible: entityEligible, rng, suffix: number === "0" || number === "4" || number === "6" ? " incursion/roaming only" : "" }),
    loot: makeRecord({ dice: "d10000", max: 10000, threshold: profile.lootThreshold, eligible: searches, rng }),
    madGodSet: makeRecord({ dice: "d10000", max: 10000, threshold: 1, eligible: madGodEligible, rng, suffix: " UR+ UNIQUE discovery" }),
    almondWater: makeRecord({ dice: "d10000", max: 10000, threshold: profile.waterThreshold, eligible: searchesWater, rng }),
    exitProbe: makeRecord({ dice: "d10000", max: 10000, threshold: exitChance, eligible: probesExit, rng, suffix: " discovery clue" }),
  };
}

export function canTransitionLevel(state, rolls) {
  if (rolls?.exitProbe?.success === true) return true;
  if (state?.flags?.transitionReady === true || state?.flags?.exitReady === true) return true;
  const progress = String(state?.flags?.exitProgress || state?.flags?.exploration?.exitProgress || "").toUpperCase();
  return /(READY|GUARANTEED|CONDITION MET|TRANSITION AVAILABLE)/.test(progress);
}
