import { NextResponse } from "next/server";
import { randomUUID } from "node:crypto";
import {
  STORAGE_NAME,
  loadOrCreateCanonicalState,
  loadState,
  persistState,
  sanitizeClientState
} from "../../../lib/state-store.js";
import { createCanonicalState } from "../../../lib/canon.js";

export const dynamic = "force-dynamic";

function sessionFrom(request) {
  const value = request.cookies.get("backroom_session")?.value;
  if (typeof value === "string" && /^[a-zA-Z0-9-]{16,80}$/.test(value)) {
    return { sessionId: value, created: false };
  }
  return { sessionId: randomUUID(), created: true };
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

export async function GET(request) {
  const { sessionId } = sessionFrom(request);
  try {
    const state = await loadOrCreateCanonicalState(sessionId);
    return withSession(NextResponse.json({ state, storage: STORAGE_NAME }, {
      status: 200,
      headers: { "Cache-Control": "no-store" }
    }), sessionId);
  } catch {
    return withSession(NextResponse.json({
      error: "Không thể tải trạng thái từ storage.",
      storage: STORAGE_NAME
    }, { status: 500 }), sessionId);
  }
}

export async function POST(request) {
  const { sessionId } = sessionFrom(request);
  let body;
  try {
    body = await request.json();
  } catch {
    return withSession(NextResponse.json({ error: "JSON không hợp lệ." }, { status: 400 }), sessionId);
  }

  try {
    if (body?.reset === true) {
      const resetState = createCanonicalState(sessionId);
      await persistState(sessionId, resetState);
      return withSession(NextResponse.json({
        state: resetState,
        storage: STORAGE_NAME,
        saved: true,
        reset: true
      }, { status: 200 }), sessionId);
    }

    const current = await loadOrCreateCanonicalState(sessionId);
    if (body?.expectedRevision && body.expectedRevision !== current.revision) {
      return withSession(NextResponse.json({
        error: "State trên server đã thay đổi. Hãy Tải lại trước khi Lưu.",
        state: current,
        storage: STORAGE_NAME,
        saved: false
      }, { status: 409 }), sessionId);
    }

    const next = sanitizeClientState(body?.state, sessionId, current);
    await persistState(sessionId, next);

    const verified = await loadState(sessionId);
    if (!verified || verified.revision !== next.revision) {
      throw new Error("SAVE_VERIFY_FAILED");
    }

    return withSession(NextResponse.json({
      state: verified,
      storage: STORAGE_NAME,
      saved: true
    }, { status: 200 }), sessionId);
  } catch (error) {
    const code = error?.message;
    if (code === "STATE_INVALID" || code === "TURN_INVALID") {
      return withSession(NextResponse.json({ error: "State không hợp lệ.", saved: false }, { status: 400 }), sessionId);
    }
    return withSession(NextResponse.json({
      error: "Không thể ghi trạng thái vào storage.",
      storage: STORAGE_NAME,
      saved: false
    }, { status: 500 }), sessionId);
  }
}
