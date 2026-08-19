import { getCache } from "@vercel/functions";
import { createCanonicalState } from "./canon.js";

export const STORAGE_NAME = "vercel-runtime-cache";
const TTL_SECONDS = 60 * 60 * 24 * 365;
const KEY_PREFIX = "backroom:game-state:v2:";

function keyFor(sessionId) {
  return `${KEY_PREFIX}${sessionId}`;
}

export async function loadState(sessionId) {
  const cache = getCache();
  const state = await cache.get(keyFor(sessionId));
  if (!state || typeof state !== "object") return null;
  return state;
}

export async function persistState(sessionId, state) {
  const cache = getCache();
  await cache.set(keyFor(sessionId), state, { ttl: TTL_SECONDS });
}

export async function loadOrCreateCanonicalState(sessionId) {
  const existing = await loadState(sessionId);
  if (existing?.canonLoaded === true && Number(existing.turn) >= 9) return existing;

  const seeded = createCanonicalState(sessionId);
  await persistState(sessionId, seeded);
  return seeded;
}

export function sanitizeClientState(raw, sessionId, current) {
  if (!raw || typeof raw !== "object") throw new Error("STATE_INVALID");

  const turn = Number(raw.turn);
  if (!Number.isInteger(turn) || turn < 9) throw new Error("TURN_INVALID");

  const arr = (value, fallback = []) => Array.isArray(value) ? value.slice(0, 500) : fallback;
  const obj = (value, fallback = {}) => value && typeof value === "object" && !Array.isArray(value) ? value : fallback;
  const text = (value, fallback = "", max = 12000) => typeof value === "string" ? value.slice(0, max) : fallback;

  return {
    version: 2,
    sessionId,
    title: text(raw.title, current.title, 200),
    turn,
    mode: text(raw.mode, current.mode, 40),
    canonLoaded: true,
    canonVersion: current.canonVersion,
    location: text(raw.location, current.location, 1000),
    player: obj(raw.player, current.player),
    party: arr(raw.party, current.party),
    inventory: arr(raw.inventory, current.inventory),
    flags: obj(raw.flags, current.flags),
    snapshotUrl: raw.snapshotUrl == null ? null : text(raw.snapshotUrl, null, 4000),
    log: arr(raw.log, current.log).map((entry) => ({
      role: entry?.role === "player" ? "player" : "gm",
      text: text(entry?.text, "", 12000)
    })).filter((entry) => entry.text),
    updatedAt: new Date().toISOString(),
    revision: crypto.randomUUID()
  };
}
