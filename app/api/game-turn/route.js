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

export async function POST(request) {
  try {
    const sessionId = await getSessionId();
    const body = await request.json().catch(() => null);
    const action = typeof body?.action === "string" ? body.action.trim() : "";
    if (!action) return json({ error: "Hành động không được để trống.", saved: false }, 400);
    if (action.length > 12000) return json({ error: "Hành động quá dài.", saved: false }, 400);

    const current = await loadState(sessionId);
    const generated = await generateTurn(current, action);

    const nextState = {
      ...current,
      title: typeof generated.title === "string" ? generated.title : current.title,
      turn: current.turn + 1,
      mode: "ai",
      canonLoaded: current.canonLoaded === true,
      location: typeof generated.location === "string" ? generated.location : current.location,
      player: objectOr(generated.player, current.player),
      party: Array.isArray(generated.party) ? generated.party : current.party,
      inventory: Array.isArray(generated.inventory) ? generated.inventory : current.inventory,
      flags: objectOr(generated.flags, current.flags),
      snapshotUrl:
        typeof generated.snapshotUrl === "string" || generated.snapshotUrl === null
          ? generated.snapshotUrl
          : current.snapshotUrl,
      log: [
        ...(Array.isArray(current.log) ? current.log : []),
        { role: "player", text: action },
        { role: "gm", text: generated.reply.trim() },
      ],
    };

    const state = await saveState(sessionId, nextState, current.revision);
    return json({ state, storage: storageName(), saved: true });
  } catch (error) {
    if (error instanceof StateConflictError) {
      return json({ error: error.message, saved: false, storage: storageName() }, 409);
    }
    console.error("game-turn failed", error instanceof Error ? error.message : "unknown error");
    return json({ error: "Lượt chơi không được lưu.", saved: false, storage: storageName() }, 500);
  }
}
