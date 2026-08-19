import { NextResponse } from "next/server";
import { generateSnapshotImage } from "../../../lib/gemini-image.js";
import { getSessionId } from "../../../lib/session.js";
import { saveSnapshot } from "../../../lib/snapshot-store.js";
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

function tailText(value, max = 3500) {
  if (typeof value !== "string") return "";
  return value.length > max ? value.slice(-max) : value;
}

function snapshotPrompt(state) {
  const recent = Array.isArray(state.log)
    ? state.log.slice(-4).map((entry) => `${entry.role === "player" ? "PLAYER" : "GM"}: ${tailText(entry.text, 1800)}`).join("\n\n")
    : "";

  const partyNames = Array.isArray(state.party)
    ? state.party.map((member) => typeof member === "string" ? member : member?.name).filter(Boolean)
    : [];

  return `Create one cinematic 16:9 visual snapshot of the CURRENT END STATE of a Backrooms text game.

Hard visual rules:
- Show the present scene only, not earlier events and not a montage.
- Main character: Kai Akechi / Twilight. He is physically present at the current location.
- Keep his known equipment/state from the supplied game state. Do not invent missing weapons, injuries, powers in use, or costume damage.
- If party is empty, Kai is alone. Do NOT show Iris, Syvial, survivors, NPCs, monsters, shadows shaped like people, or Entities unless the current state explicitly confirms they are physically present.
- Do not turn hallucinations, memories, unknown phenomena or off-screen characters into literal visible beings.
- Do not add exits, loot, doors, water, blood, corpses, signs, text, HUD, captions, logos or UI unless the current state explicitly contains them.
- Backrooms Level 0 should use stale yellow wallpaper, damp-looking carpet, fluorescent ceiling panels, oppressive empty office-like geometry and liminal fluorescent lighting.
- Photorealistic cinematic game concept art, grounded anatomy and materials, subtle atmospheric grain, no written text in the image.
- Camera should communicate the playable situation clearly, not obscure the environment with a close-up portrait.

CURRENT STATE:
Turn: ${state.turn}
Location: ${state.location}
Player: ${JSON.stringify(state.player)}
Party physically with Kai: ${JSON.stringify(partyNames)}
Inventory/equipment: ${JSON.stringify(state.inventory)}
Relevant flags: ${JSON.stringify(state.flags)}

RECENT CONTEXT, with the final lines taking priority:
${recent}`;
}

export async function POST() {
  try {
    const sessionId = await getSessionId();
    const current = await loadState(sessionId);
    const baseRevision = current.revision;
    const image = await generateSnapshotImage(snapshotPrompt(current));

    const latest = await loadState(sessionId);
    if (latest.revision !== baseRevision) {
      throw new StateConflictError("State đã đổi trong lúc tạo snapshot. Ảnh cũ không được gắn vào lượt mới.");
    }

    await saveSnapshot(sessionId, {
      data: image.data,
      mimeType: image.mimeType,
      turn: current.turn,
    });

    const snapshotUrl = `/api/snapshot/image?turn=${current.turn}&v=${Date.now()}`;
    const state = await saveState(
      sessionId,
      { ...current, snapshotUrl },
      current.revision,
    );

    return json({
      state,
      snapshotUrl: state.snapshotUrl,
      saved: true,
      storage: storageName(),
      imageModel: image.model,
    });
  } catch (error) {
    if (error instanceof StateConflictError) {
      return json({ error: error.message, saved: false }, 409);
    }
    console.error("snapshot generation failed", error instanceof Error ? error.message : "unknown error");
    return json({ error: "Không thể tạo snapshot bằng Gemini.", saved: false }, 500);
  }
}
