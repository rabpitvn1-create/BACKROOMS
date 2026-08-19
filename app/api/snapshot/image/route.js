import { getSessionId } from "../../../../lib/session.js";
import { loadSnapshot } from "../../../../lib/snapshot-store.js";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const sessionId = await getSessionId();
    const snapshot = await loadSnapshot(sessionId);
    if (!snapshot?.data) {
      return new Response("Snapshot not found", {
        status: 404,
        headers: { "Cache-Control": "no-store" },
      });
    }

    const bytes = Buffer.from(snapshot.data, "base64");
    return new Response(bytes, {
      status: 200,
      headers: {
        "Content-Type": snapshot.mimeType || "image/jpeg",
        "Content-Length": String(bytes.length),
        "Cache-Control": "private, no-store, max-age=0",
        "X-Backroom-Snapshot-Turn": String(snapshot.turn ?? ""),
      },
    });
  } catch (error) {
    console.error("snapshot image failed", error instanceof Error ? error.message : "unknown error");
    return new Response("Snapshot unavailable", { status: 500 });
  }
}
