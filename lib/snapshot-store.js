import { getCache } from "@vercel/functions";

const SNAPSHOT_NAMESPACE = "backroom-snapshot-v1";
const SNAPSHOT_TTL_SECONDS = 60 * 60 * 24 * 365;
const MAX_BASE64_CHARS = 1_500_000;
const cache = getCache(undefined, SNAPSHOT_NAMESPACE);

export async function saveSnapshot(sessionId, snapshot) {
  if (!snapshot || typeof snapshot.data !== "string" || !snapshot.data) {
    throw new Error("SNAPSHOT_INVALID");
  }
  if (snapshot.data.length > MAX_BASE64_CHARS) {
    throw new Error("SNAPSHOT_TOO_LARGE");
  }

  const stored = {
    data: snapshot.data,
    mimeType: snapshot.mimeType === "image/png" ? "image/png" : "image/jpeg",
    turn: Number.isInteger(snapshot.turn) ? snapshot.turn : null,
    createdAt: new Date().toISOString(),
  };

  await cache.set(sessionId, stored, {
    ttl: SNAPSHOT_TTL_SECONDS,
    tags: [`backroom-snapshot-${sessionId}`],
    name: "Backroom scene snapshot",
  });

  return stored;
}

export async function loadSnapshot(sessionId) {
  const value = await cache.get(sessionId);
  if (!value) return null;
  if (typeof value === "string") {
    try {
      return JSON.parse(value);
    } catch {
      return null;
    }
  }
  return value;
}

export async function deleteSnapshot(sessionId) {
  await cache.delete(sessionId);
}
