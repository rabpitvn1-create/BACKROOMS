import { NextResponse } from "next/server";
import { auditTurn } from "../../../lib/ai-audit.js";
import { generateTurn } from "../../../lib/gemini.js";
import {
  isGameplayTurn,
  levelFromState,
  levelLabel,
  makeRolls,
  sameLevel,
} from "../../../lib/gameplay.js";
import { getSessionId } from "../../../lib/session.js";
import { applyTurnOperations } from "../../../lib/state-ops.js";
import { AUDIT_LEVEL, scoreTurnRisk } from "../../../lib/turn-risk.js";
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

function hardIssues(audits) {
  return audits.flatMap((audit) => Array.isArray(audit?.issues)
    ? audit.issues.filter((issue) => issue?.severity === "hard")
    : []);
}

async function auditsForRisk({ risk, current, action, rolls, generated, operationResult }) {
  if (risk.level === AUDIT_LEVEL.NONE) return [];

  const writerSlot = generated?._provider?.workerSlot;
  const common = {
    current,
    action,
    rolls,
    generated,
    acceptedOps: operationResult.accepted,
    rejectedOps: operationResult.rejected,
    excludeSlots: Number.isInteger(writerSlot) ? [writerSlot] : [],
  };

  if (risk.level === AUDIT_LEVEL.NARROW) {
    return [await auditTurn({ ...common, scope: "canon" })];
  }

  const results = await Promise.all([
    auditTurn({ ...common, scope: "canon" }),
    auditTurn({ ...common, scope: "character" }),
  ]);
  return results;
}

function applyServerFields(current, operationResult, generated, gameplay, rolls) {
  let nextState = operationResult.state;
  const nextLevel = levelFromState(nextState);
  nextState = {
    ...nextState,
    title: levelLabel(nextLevel) || current.title,
    level: nextLevel || current.level,
    turn: current.turn + (gameplay ? 1 : 0),
    mode: gameplay ? "ai" : current.mode,
    canonLoaded: true,
    canonVersion: current.canonVersion,
    flags: gameplay
      ? {
          ...objectOr(nextState.flags, current.flags),
          lastRolls: { turn: current.turn, ...rolls },
          ...(nextLevel ? { currentLevel: nextLevel } : {}),
        }
      : current.flags,
    snapshotUrl: current.snapshotUrl,
    log: [
      ...(Array.isArray(current.log) ? current.log : []),
      { role: "player", text: generated._action },
      { role: "gm", text: generated.reply.trim() },
    ],
  };
  return nextState;
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

    let generated = await generateTurn(current, action, rolls, { isGameplayTurn: gameplay });
    generated._action = action;
    let operationResult = gameplay
      ? applyTurnOperations(current, generated.ops, { action, rolls })
      : { state: structuredClone(current), accepted: [], rejected: [] };
    let risk = scoreTurnRisk({
      current,
      generated,
      acceptedOps: operationResult.accepted,
      rejectedOps: operationResult.rejected,
    });
    let audits = await auditsForRisk({ risk, current, action, rolls, generated, operationResult });
    let issues = hardIssues(audits);
    let repaired = false;

    if (issues.length) {
      generated = await generateTurn(current, action, rolls, {
        isGameplayTurn: gameplay,
        auditFeedback: issues,
        excludeSlots: audits.map((audit) => audit.workerSlot).filter(Number.isInteger),
      });
      generated._action = action;
      operationResult = gameplay
        ? applyTurnOperations(current, generated.ops, { action, rolls })
        : { state: structuredClone(current), accepted: [], rejected: [] };
      risk = scoreTurnRisk({
        current,
        generated,
        acceptedOps: operationResult.accepted,
        rejectedOps: operationResult.rejected,
      });
      audits = await auditsForRisk({ risk, current, action, rolls, generated, operationResult });
      issues = hardIssues(audits);
      repaired = true;
    }

    if (issues.length) {
      return json({
        error: "Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.",
        saved: false,
        storage: storageName(),
        auditLevel: risk.level,
        auditIssues: issues.map((issue) => ({ rule: issue.rule, reason: issue.reason })),
      }, 422);
    }

    let nextState = applyServerFields(current, operationResult, generated, gameplay, rolls);
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
      operationsAccepted: operationResult.accepted.length,
      operationsRejected: operationResult.rejected.map((entry) => entry.reason),
      auditLevel: risk.level,
      auditCount: audits.length,
      auditRepaired: repaired,
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
