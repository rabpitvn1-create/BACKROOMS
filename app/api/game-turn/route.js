import { randomInt } from "node:crypto";
import { NextResponse } from "next/server";
import { generateTurn } from "../../../lib/gemini.js";
import { getSessionId } from "../../../lib/session.js";
import {
  StateConflictError,
  loadState,
  saveState,
  storageName,
} from "../../../lib/state-store.js";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

const SNAPSHOT_TYPES = new Set([
  "level_transition",
  "special_area",
  "entity_encounter",
  "character_encounter",
  "major_event",
]);

function json(body, status = 200) {
  return NextResponse.json(body, {
    status,
    headers: { "Cache-Control": "no-store" },
  });
}

function objectOr(value, fallback) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : fallback;
}

function roll(max) {
  return randomInt(1, max + 1);
}

function makeRolls(action) {
  const survivor = roll(10000);
  const iris = roll(1000000);
  const syvial = roll(1000000);
  const hazard = roll(10000);
  const almondWater = roll(10000);
  const exit = roll(10000);

  const physicalRisk = /(đi|bước|chạy|leo|mở|đóng|chạm|tìm|kiểm tra|quét|scan|bắn|phá|đẩy|kéo|tiến|lùi|cúi|nhìn vào)/i.test(action);
  const searchesWater = /(nước|water|almond|uống|khát)/i.test(action);
  const probesExit = /(exit|lối thoát|thoát|cửa trắng|cánh cửa|ngưỡng|hành lang phía sau)/i.test(action);

  return {
    survivor: { dice: "d10000", chance: "2.00%", raw: survivor, threshold: 200, eligible: true, success: survivor <= 200 },
    irisReunion: { dice: "d1000000", chance: "0.0025%", raw: iris, threshold: 25, eligible: true, success: iris <= 25 },
    syvialReunion: { dice: "d1000000", chance: "0.0025%", raw: syvial, threshold: 25, eligible: true, success: syvial <= 25 },
    hazard: { dice: "d10000", chance: "4.00%", raw: hazard, threshold: 400, eligible: physicalRisk, success: physicalRisk && hazard <= 400 },
    almondWater: { dice: "d10000", chance: "0.20%", raw: almondWater, threshold: 20, eligible: searchesWater, success: searchesWater && almondWater <= 20 },
    exitProbe: { dice: "d10000", chance: "1.00% discovery clue", raw: exit, threshold: 100, eligible: probesExit, success: probesExit && exit <= 100 },
  };
}

function cleanKey(value) {
  return typeof value === "string" ? value.trim().slice(0, 160) : "";
}

function normalizeLevel(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const rawNumber = value.number;
  const number = typeof rawNumber === "number" || typeof rawNumber === "string"
    ? String(rawNumber).trim()
    : "";
  const name = typeof value.name === "string" ? value.name.trim() : "";
  if (!number || !name) return null;
  return { number, name: name.slice(0, 120) };
}

function parseLevelText(value) {
  if (typeof value !== "string") return null;
  const match = value.match(/\bLevel\s+([^\s/—–-]+)\s*(?:\/|—|–|-)\s*([^—–\n]+?)(?:\s+[—–]\s+|$)/i);
  if (!match) return null;
  return { number: match[1].trim(), name: match[2].trim().slice(0, 120) };
}

function levelFromState(state) {
  return normalizeLevel(state?.level)
    || normalizeLevel(state?.flags?.currentLevel)
    || parseLevelText(state?.location)
    || parseLevelText(state?.title);
}

function sameLevel(a, b) {
  if (!a || !b) return false;
  return String(a.number).toLowerCase() === String(b.number).toLowerCase()
    && a.name.toLowerCase() === b.name.toLowerCase();
}

function levelLabel(level) {
  return level ? `Level ${level.number} – ${level.name}` : null;
}

function numberFlag(state, key) {
  const value = Number(state?.flags?.[key]);
  return Number.isFinite(value) ? value : 0;
}

function partyNames(state) {
  if (!Array.isArray(state?.party)) return [];
  return state.party
    .map((member) => typeof member === "string" ? member : member?.name)
    .filter((name) => typeof name === "string" && name.trim())
    .map((name) => name.trim().toLowerCase());
}

function newPartyMember(current, nextState) {
  const before = new Set(partyNames(current));
  return partyNames(nextState).some((name) => !before.has(name));
}

function reunionBecamePresent(current, nextState, key) {
  const before = String(current?.flags?.[key]?.continuity || "").toUpperCase();
  const after = String(nextState?.flags?.[key]?.continuity || "").toUpperCase();
  if (!after || after === before) return false;
  return /(REUNITED|WITH KAI|TOGETHER|PRESENT)/.test(after);
}

function approveSnapshot(current, nextState, generated) {
  const event = objectOr(generated?.snapshotEvent, {});
  const type = cleanKey(event.type).toLowerCase();
  if (event.shouldGenerate !== true || !SNAPSHOT_TYPES.has(type)) {
    return { requested: false, type: null, key: null, reason: null };
  }

  const beforeLevel = levelFromState(current);
  const afterLevel = levelFromState(nextState);
  let eligible = false;
  let triggerKey = "";

  if (type === "level_transition") {
    eligible = Boolean(beforeLevel && afterLevel && !sameLevel(beforeLevel, afterLevel));
    triggerKey = afterLevel ? `level:${afterLevel.number}:${afterLevel.name}` : "";
  } else if (type === "entity_encounter") {
    const beforeCount = numberFlag(current, "entitiesConfirmedLocal");
    const afterCount = numberFlag(nextState, "entitiesConfirmedLocal");
    const beforeEncounter = cleanKey(current?.flags?.entityEncounterKey);
    const afterEncounter = cleanKey(nextState?.flags?.entityEncounterKey);
    eligible = afterCount > beforeCount || Boolean(afterEncounter && afterEncounter !== beforeEncounter);
    triggerKey = eligible ? `entity:${afterEncounter || afterCount}:${levelLabel(afterLevel) || "unknown"}` : "";
  } else if (type === "character_encounter") {
    const survivorIncrease = numberFlag(nextState, "survivorsConfirmed") > numberFlag(current, "survivorsConfirmed");
    const reunion = reunionBecamePresent(current, nextState, "iris") || reunionBecamePresent(current, nextState, "syvial");
    eligible = survivorIncrease || newPartyMember(current, nextState) || reunion;
    triggerKey = eligible
      ? `character:${partyNames(nextState).sort().join(",")}:${numberFlag(nextState, "survivorsConfirmed")}:${levelLabel(afterLevel) || "unknown"}`
      : "";
  } else if (type === "special_area") {
    const beforeArea = cleanKey(current?.flags?.visualAreaKey);
    const afterArea = cleanKey(nextState?.flags?.visualAreaKey);
    eligible = Boolean(afterArea && afterArea !== beforeArea);
    triggerKey = eligible ? `area:${levelLabel(afterLevel) || "unknown"}:${afterArea}` : "";
  } else if (type === "major_event") {
    const beforeEvent = cleanKey(current?.flags?.visualEventKey);
    const afterEvent = cleanKey(nextState?.flags?.visualEventKey);
    eligible = Boolean(afterEvent && afterEvent !== beforeEvent);
    triggerKey = eligible ? `event:${afterEvent}` : "";
  }

  const lastTrigger = cleanKey(current?.flags?.lastSnapshotTriggerKey);
  if (!eligible || !triggerKey || triggerKey === lastTrigger) {
    return { requested: false, type: null, key: null, reason: null };
  }

  return {
    requested: true,
    type,
    key: triggerKey.slice(0, 300),
    reason: typeof event.reason === "string" ? event.reason.trim().slice(0, 300) : "",
  };
}

export async function POST(request) {
  try {
    const sessionId = await getSessionId();
    const body = await request.json().catch(() => null);
    const action = typeof body?.action === "string" ? body.action.trim() : "";
    if (!action) return json({ error: "Hành động không được để trống.", saved: false }, 400);
    if (action.length > 12000) return json({ error: "Hành động quá dài.", saved: false }, 400);

    const current = await loadState(sessionId);
    const rolls = makeRolls(action);
    const generated = await generateTurn(current, action, rolls);
    const generatedFlags = objectOr(generated.flags, {});
    const currentLevel = levelFromState(current);
    const nextLevel = normalizeLevel(generated.level)
      || parseLevelText(generated.location)
      || currentLevel;

    const nextFlags = {
      ...current.flags,
      ...generatedFlags,
      lastRolls: { turn: current.turn + 1, ...rolls },
      ...(nextLevel ? { currentLevel: nextLevel } : {}),
    };

    const nextState = {
      ...current,
      title: levelLabel(nextLevel) || current.title,
      level: nextLevel || current.level,
      turn: current.turn + 1,
      mode: "ai",
      canonLoaded: true,
      canonVersion: current.canonVersion,
      location: typeof generated.location === "string" ? generated.location : current.location,
      player: objectOr(generated.player, current.player),
      party: Array.isArray(generated.party) ? generated.party : current.party,
      inventory: Array.isArray(generated.inventory) ? generated.inventory : current.inventory,
      flags: nextFlags,
      // Normal turns never replace the last meaningful image.
      // Only /api/snapshot may update this URL after the server approves an event.
      snapshotUrl: current.snapshotUrl,
      log: [
        ...(Array.isArray(current.log) ? current.log : []),
        { role: "player", text: action },
        { role: "gm", text: generated.reply.trim() },
      ],
    };

    const snapshot = approveSnapshot(current, nextState, generated);
    if (snapshot.requested) {
      nextState.flags = {
        ...nextState.flags,
        lastSnapshotTriggerKey: snapshot.key,
      };
    }

    const state = await saveState(sessionId, nextState, current.revision);
    return json({
      state,
      storage: storageName(),
      saved: true,
      rolls,
      snapshotRequested: snapshot.requested,
      snapshotType: snapshot.type,
      snapshotReason: snapshot.reason,
    });
  } catch (error) {
    if (error instanceof StateConflictError) {
      return json({ error: error.message, saved: false, storage: storageName() }, 409);
    }
    console.error("game-turn failed", error instanceof Error ? error.message : "unknown error");
    return json({ error: "Lượt chơi không được lưu.", saved: false, storage: storageName() }, 500);
  }
}
