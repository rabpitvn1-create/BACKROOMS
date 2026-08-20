import { NextResponse } from "next/server";
import { generateTurn } from "../../../lib/gemini.js";
import {
  canTransitionLevel,
  isGameplayTurn,
  levelFromState,
  levelLabel,
  makeRolls,
  normalizeLevel,
  parseLevelText,
  sameLevel,
} from "../../../lib/gameplay.js";
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

function cleanKey(value) {
  return typeof value === "string" ? value.trim().slice(0, 160) : "";
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

function mergeRuntimeFlags(current, generated) {
  const next = { ...objectOr(current, {}), ...objectOr(generated, {}) };
  for (const key of ["communication", "iris", "syvial", "jeff", "madGod", "exploration", "omnivault"]) {
    next[key] = { ...objectOr(current?.[key], {}), ...objectOr(generated?.[key], {}) };
  }
  return next;
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

function gatedParty(current, proposed, rolls) {
  if (!Array.isArray(proposed)) return current.party;
  const before = new Set(partyNames(current));
  return proposed.filter((member) => {
    const name = typeof member === "string" ? member : member?.name;
    if (typeof name !== "string" || !name.trim()) return false;
    return before.has(name.trim().toLowerCase()) || mayAddCharacter(current, name, rolls);
  });
}

function gatedInventory(current, proposed, rolls) {
  if (!Array.isArray(proposed)) return current.inventory;
  const existingWater = (Array.isArray(current.inventory) ? current.inventory : []).filter((item) => {
    const name = typeof item === "string" ? item : item?.name;
    return /almond water/i.test(String(name || ""));
  }).length;
  const existingMadGod = (Array.isArray(current.inventory) ? current.inventory : []).filter((item) => {
    const name = typeof item === "string" ? item : item?.name;
    return /madgod/i.test(String(name || ""));
  }).length;
  let retainedWater = 0;
  let retainedMadGod = 0;
  return proposed.filter((item) => {
    const name = typeof item === "string" ? item : item?.name;
    if (/almond water/i.test(String(name || ""))) {
      // An ineligible water roll means this turn was not discovering new water.
      // Existing/established water can still be picked up, stored, copied, given,
      // consumed, or otherwise moved by the turn without being deleted by the gate.
      if (rolls?.almondWater?.eligible !== true) return true;
      if (rolls?.almondWater?.success === true) return true;
      retainedWater += 1;
      return retainedWater <= existingWater;
    }
    if (/madgod/i.test(String(name || ""))) {
      retainedMadGod += 1;
      if (retainedMadGod > 1) return false;
      if (rolls?.madGodSet?.success === true || current.flags?.madGod?.spawned === true) return true;
      return retainedMadGod <= existingMadGod;
    }
    return true;
  });
}

function gateGeneratedFlags(current, generatedFlags, rolls) {
  const flags = mergeRuntimeFlags(current.flags, generatedFlags);
  if (rolls?.survivor?.success !== true && numberFlag({ flags }, "survivorsConfirmed") > numberFlag(current, "survivorsConfirmed")) {
    flags.survivorsConfirmed = numberFlag(current, "survivorsConfirmed");
    flags.survivorRegistry = current.flags?.survivorRegistry || [];
  }
  if (rolls?.entityEncounter?.success !== true && numberFlag({ flags }, "entitiesConfirmedLocal") > numberFlag(current, "entitiesConfirmedLocal")) {
    flags.entitiesConfirmedLocal = numberFlag(current, "entitiesConfirmedLocal");
    flags.entityRegistry = current.flags?.entityRegistry || [];
    flags.entityEncounterKey = current.flags?.entityEncounterKey;
  }
  if (current.flags?.madGod?.spawned === true) {
    flags.madGod = { ...flags.madGod, spawned: true };
  } else if (rolls?.madGodSet?.success !== true && flags.madGod?.spawned === true) {
    flags.madGod = { ...current.flags?.madGod };
  }
  for (const key of ["iris", "syvial"]) {
    const allowed = rolls?.[`${key}Reunion`]?.success === true || continuityPathConfirmed(current, key);
    const before = String(current.flags?.[key]?.continuity || "").toUpperCase();
    const after = String(flags?.[key]?.continuity || "").toUpperCase();
    if (!allowed && !/(REUNITED|WITH KAI|TOGETHER|PRESENT)/.test(before) && /(REUNITED|WITH KAI|TOGETHER|PRESENT)/.test(after)) {
      flags[key] = { ...current.flags?.[key] };
    }
  }
  return flags;
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
    const gameplay = isGameplayTurn(action);
    const rolls = gameplay ? makeRolls(current, action) : null;
    const generated = await generateTurn(current, action, rolls, { isGameplayTurn: gameplay });
    const currentLevel = levelFromState(current);
    const proposedLevel = normalizeLevel(generated.level)
      || parseLevelText(generated.location)
      || currentLevel;
    const nextLevel = !gameplay
      ? currentLevel
      : !sameLevel(currentLevel, proposedLevel) && !canTransitionLevel(current, rolls)
        ? currentLevel
        : proposedLevel;

    const nextFlags = gameplay
      ? {
          ...gateGeneratedFlags(current, objectOr(generated.flags, {}), rolls),
          lastRolls: { turn: current.turn, ...rolls },
          ...(nextLevel ? { currentLevel: nextLevel } : {}),
        }
      : current.flags;

    const generatedPlayer = objectOr(generated.player, {});
    const nextPlayer = gameplay
      ? {
          ...current.player,
          ...generatedPlayer,
          name: current.player?.name || "Kai Akechi",
          codename: current.player?.codename || "Twilight",
          needs: {
            ...objectOr(current.player?.needs, {}),
            ...objectOr(generatedPlayer.needs, {}),
          },
        }
      : current.player;

    const nextState = {
      ...current,
      title: levelLabel(nextLevel) || current.title,
      level: nextLevel || current.level,
      turn: current.turn + (gameplay ? 1 : 0),
      mode: gameplay ? "ai" : current.mode,
      canonLoaded: true,
      canonVersion: current.canonVersion,
      location: gameplay && typeof generated.location === "string" ? generated.location : current.location,
      player: nextPlayer,
      party: gameplay ? gatedParty(current, generated.party, rolls) : current.party,
      inventory: gameplay ? gatedInventory(current, generated.inventory, rolls) : current.inventory,
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

    const snapshot = gameplay
      ? approveSnapshot(current, nextState, generated)
      : { requested: false, type: null, key: null, reason: null };
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
      turnAdvanced: gameplay,
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
