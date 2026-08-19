import { NextResponse } from "next/server";
import { getSessionId } from "../../../lib/session.js";
import {
  StateConflictError,
  loadState,
  resetState,
  saveState,
  storageName,
} from "../../../lib/state-store.js";

export const dynamic = "force-dynamic";

function json(body, status = 200) {
  return NextResponse.json(body, {
    status,
    headers: {
      "Cache-Control": "no-store",
      "X-Backroom-Build": process.env.VERCEL_GIT_COMMIT_SHA || "unknown",
    },
  });
}

export async function GET() {
  try {
    const sessionId = await getSessionId();
    const state = await loadState(sessionId);
    return json({ state, storage: storageName() });
  } catch (error) {
    console.error("game-state GET failed", error instanceof Error ? error.message : "unknown error");
    return json({ error: "Không thể tải state từ server." }, 500);
  }
}

export async function POST(request) {
  try {
    const sessionId = await getSessionId();
    const body = await request.json().catch(() => null);
    if (!body || typeof body !== "object") return json({ error: "JSON không hợp lệ." }, 400);

    if (body.reset === true) {
      const state = await resetState(sessionId);
      return json({ state, storage: storageName(), saved: true });
    }

    if (!body.state || typeof body.state !== "object" || Array.isArray(body.state)) {
      return json({ error: "Thiếu state hợp lệ." }, 400);
    }

    const expectedRevision = typeof body.expectedRevision === "string" ? body.expectedRevision : undefined;
    const state = await saveState(sessionId, body.state, expectedRevision);
    return json({ state, storage: storageName(), saved: true });
  } catch (error) {
    if (error instanceof StateConflictError) {
      return json({ error: error.message }, 409);
    }
    console.error("game-state POST failed", error instanceof Error ? error.message : "unknown error");
    return json({ error: "Không thể lưu state lên server." }, 500);
  }
}
