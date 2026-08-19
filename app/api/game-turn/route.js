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
    const snapshotRequested = generated.snapshotEvent?.shouldGenerate === true;

    const nextState = {
      ...current,
      title: typeof generated.title === "string" ? generated.title : current.title,
      turn: current.turn + 1,
      mode: "ai",
      canonLoaded: true,
      canonVersion: current.canonVersion,
      location: typeof generated.location === "string" ? generated.location : current.location,
      player: objectOr(generated.player, current.player),
      party: Array.isArray(generated.party) ? generated.party : current.party,
      inventory: Array.isArray(generated.inventory) ? generated.inventory : current.inventory,
      flags: {
        ...current.flags,
        ...generatedFlags,
        lastRolls: { turn: current.turn + 1, ...rolls },
      },
      // A normal turn must never erase or replace the last meaningful image.
      // Only /api/snapshot is allowed to update this URL after an approved event.
      snapshotUrl: current.snapshotUrl,
      log: [
        ...(Array.isArray(current.log) ? current.log : []),
        { role: "player", text: action },
        { role: "gm", text: generated.reply.trim() },
      ],
    };

    const state = await saveState(sessionId, nextState, current.revision);
    return json({
      state,
      storage: storageName(),
      saved: true,
      rolls,
      snapshotRequested,
      snapshotReason: snapshotRequested && typeof generated.snapshotEvent?.reason === "string"
        ? generated.snapshotEvent.reason.slice(0, 300)
        : null,
    });
  } catch (error) {
    if (error instanceof StateConflictError) {
      return json({ error: error.message, saved: false, storage: storageName() }, 409);
    }
    console.error("game-turn failed", error instanceof Error ? error.message : "unknown error");
    return json({ error: "Lượt chơi không được lưu.", saved: false, storage: storageName() }, 500);
  }
}
