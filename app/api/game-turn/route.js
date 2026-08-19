import { NextResponse } from "next/server";
import { randomInt, randomUUID } from "node:crypto";
import { callGeminiTurn } from "../../../lib/gemini.js";
import {
  STORAGE_NAME,
  loadOrCreateCanonicalState,
  loadState,
  persistState
} from "../../../lib/state-store.js";

export const dynamic = "force-dynamic";

function sessionFrom(request) {
  const value = request.cookies.get("backroom_session")?.value;
  if (typeof value === "string" && /^[a-zA-Z0-9-]{16,80}$/.test(value)) return value;
  return randomUUID();
}

function withSession(response, sessionId) {
  response.cookies.set("backroom_session", sessionId, {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 365
  });
  return response;
}

function roll(max) {
  return randomInt(1, max + 1);
}

function makeRolls(action) {
  const survivorRaw = roll(10000);
  const irisRaw = roll(1000000);
  const syvialRaw = roll(1000000);
  const hazardRaw = roll(10000);
  const lootRaw = roll(10000);
  const exitRaw = roll(10000);
  const searchesWater = /(nước|water|almond|uống|khát)/i.test(action);
  const physicalRisk = /(đi|bước|chạy|leo|mở|đóng|chạm|tìm|kiểm tra|quét|scan|bắn|phá|đẩy|kéo|cúi|nhìn vào|tiến|lùi)/i.test(action);
  const probesExit = /(exit|lối thoát|thoát|cửa trắng|cánh cửa|ngưỡng|hành lang phía sau)/i.test(action);

  return {
    survivor: { dice: "d10000", chance: "2.00%", raw: survivorRaw, threshold: 200, success: survivorRaw <= 200, eligible: true },
    irisReunion: { dice: "d1000000", chance: "0.0025%", raw: irisRaw, threshold: 25, success: irisRaw <= 25, eligible: true },
    syvialReunion: { dice: "d1000000", chance: "0.0025%", raw: syvialRaw, threshold: 25, success: syvialRaw <= 25, eligible: true },
    hazard: { dice: "d10000", chance: "4.00%", raw: hazardRaw, threshold: 400, success: physicalRisk && hazardRaw <= 400, eligible: physicalRisk },
    almondWater: { dice: "d10000", chance: "0.20%", raw: lootRaw, threshold: 20, success: searchesWater && lootRaw <= 20, eligible: searchesWater },
    exitProbe: { dice: "d10000", chance: "1.00% discovery clue only", raw: exitRaw, threshold: 100, success: probesExit && exitRaw <= 100, eligible: probesExit, note: "Không tự động tạo transition; chỉ cho phép clue/discovery nếu hợp continuity." }
  };
}

function isPlainObject(value) {
  return value && typeof value === "object" && !Array.isArray(value);
}

function mergeObject(base, patch, depth = 0) {
  if (!isPlainObject(patch) || depth > 5) return base;
  const out = { ...(isPlainObject(base) ? base : {}) };
  for (const [key, value] of Object.entries(patch)) {
    if (["sessionId", "revision", "version", "turn", "canonLoaded", "canonVersion", "updatedAt"].includes(key)) continue;
    if (isPlainObject(value)) out[key] = mergeObject(out[key], value, depth + 1);
    else if (Array.isArray(value)) out[key] = value.slice(0, 500);
    else if (["string", "number", "boolean"].includes(typeof value) || value === null) out[key] = value;
  }
  return out;
}

function applyStateChanges(state, changes) {
  if (!isPlainObject(changes)) return state;
  const next = { ...state };
  if (typeof changes.location === "string" && changes.location.trim()) next.location = changes.location.slice(0, 1000);
  if (isPlainObject(changes.player)) next.player = mergeObject(state.player, changes.player);
  if (Array.isArray(changes.party)) next.party = changes.party.slice(0, 100);
  if (Array.isArray(changes.inventory)) next.inventory = changes.inventory.slice(0, 500);
  if (isPlainObject(changes.flags)) next.flags = mergeObject(state.flags, changes.flags);
  if (changes.snapshotUrl === null || typeof changes.snapshotUrl === "string") next.snapshotUrl = changes.snapshotUrl?.slice(0, 4000) ?? null;
  return next;
}

export async function POST(request) {
  const sessionId = sessionFrom(request);
  let body;
  try {
    body = await request.json();
  } catch {
    return withSession(NextResponse.json({ error: "JSON không hợp lệ.", saved: false }, { status: 400 }), sessionId);
  }

  const action = typeof body?.action === "string" ? body.action.trim().slice(0, 4000) : "";
  if (!action) {
    return withSession(NextResponse.json({ error: "Thiếu hành động.", saved: false }, { status: 400 }), sessionId);
  }

  try {
    const state = await loadOrCreateCanonicalState(sessionId);
    const baseRevision = state.revision;
    const rolls = makeRolls(action);
    const result = await callGeminiTurn({ action, state, rolls });

    if (!result.ok) {
      const status = result.status >= 400 && result.status < 600 ? result.status : 502;
      return withSession(NextResponse.json({
        error: status >= 500 ? "Không thể hoàn tất và lưu lượt này." : "Gemini từ chối request hiện tại.",
        saved: false,
        state,
        storage: STORAGE_NAME
      }, { status }), sessionId);
    }

    // Re-read before commit. If another request changed this session while Gemini was working,
    // reject this stale result instead of moving the save backwards.
    const latest = await loadState(sessionId);
    if (!latest || latest.revision !== baseRevision) {
      return withSession(NextResponse.json({
        error: "State đã thay đổi trong lúc xử lý lượt. Kết quả cũ không được ghi đè.",
        saved: false,
        state: latest || state,
        storage: STORAGE_NAME
      }, { status: 409 }), sessionId);
    }

    let next = applyStateChanges(state, result.stateChanges);
    next = {
      ...next,
      version: 2,
      sessionId,
      turn: state.turn + 1,
      mode: "ai",
      canonLoaded: true,
      canonVersion: state.canonVersion,
      flags: {
        ...next.flags,
        lastRolls: { turn: state.turn + 1, ...rolls }
      },
      log: [
        ...(Array.isArray(state.log) ? state.log : []),
        { role: "player", text: action },
        { role: "gm", text: result.narrative }
      ].slice(-500),
      updatedAt: new Date().toISOString(),
      revision: randomUUID()
    };

    await persistState(sessionId, next);
    const verified = await loadState(sessionId);
    if (!verified || verified.revision !== next.revision || verified.turn !== next.turn) {
      throw new Error("SAVE_VERIFY_FAILED");
    }

    return withSession(NextResponse.json({
      state: verified,
      storage: STORAGE_NAME,
      saved: true,
      rolls
    }, { status: 200 }), sessionId);
  } catch {
    return withSession(NextResponse.json({
      error: "Lượt chơi không được lưu vì backend gặp lỗi.",
      storage: STORAGE_NAME,
      saved: false
    }, { status: 500 }), sessionId);
  }
}
