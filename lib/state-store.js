import crypto from "node:crypto";
import { getCache } from "@vercel/functions";
import { neon } from "@neondatabase/serverless";

const CACHE_TTL_SECONDS = 60 * 60 * 24 * 365;
const CACHE_NAMESPACE = "backroom-game-state-v1";
const cache = getCache(undefined, CACHE_NAMESPACE);

let dbClient;
let dbReady;

function databaseUrl() {
  return process.env.DATABASE_URL || process.env.POSTGRES_URL || process.env.NEON_DATABASE_URL || "";
}

function hasDatabase() {
  return Boolean(databaseUrl());
}

function db() {
  if (!dbClient) dbClient = neon(databaseUrl());
  return dbClient;
}

async function ensureDatabase() {
  if (!hasDatabase()) return;
  if (!dbReady) {
    dbReady = db()`
      CREATE TABLE IF NOT EXISTS backroom_game_states (
        session_id text PRIMARY KEY,
        state jsonb NOT NULL,
        revision text NOT NULL,
        turn integer NOT NULL DEFAULT 0,
        updated_at timestamptz NOT NULL DEFAULT now()
      )
    `;
  }
  await dbReady;
}

export function storageName() {
  return hasDatabase() ? "neon-postgres" : "vercel-runtime-cache";
}

export class StateConflictError extends Error {
  constructor(message = "State đã thay đổi trên server.") {
    super(message);
    this.name = "StateConflictError";
  }
}

function cleanString(value, fallback = "", max = 4000) {
  return typeof value === "string" ? value.slice(0, max) : fallback;
}

function cleanLog(value) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item) => item && typeof item === "object" && typeof item.text === "string")
    .slice(-500)
    .map((item) => ({
      ...item,
      role: cleanString(item.role, "gm", 32),
      text: cleanString(item.text, "", 24000),
    }));
}

export function defaultState(sessionId) {
  return {
    version: 1,
    sessionId,
    title: "Backrooms Session",
    turn: 0,
    mode: "backend",
    canonLoaded: false,
    location: "Chưa nạp canon",
    player: { name: "Chưa xác định", hp: null, condition: "Bình thường" },
    party: [],
    inventory: [],
    flags: {},
    snapshotUrl: null,
    log: [{ role: "gm", text: "Giao diện đã sẵn sàng. Canon và trạng thái phiên chơi chưa được nạp." }],
    updatedAt: new Date().toISOString(),
    revision: crypto.randomUUID(),
  };
}

export function normalizeState(input, sessionId, { keepRevision = false } = {}) {
  const source = input && typeof input === "object" ? input : {};
  const base = defaultState(sessionId);
  const turn = Number.isInteger(source.turn) && source.turn >= 0 ? source.turn : base.turn;

  return {
    ...source,
    version: Number.isInteger(source.version) && source.version > 0 ? source.version : 1,
    sessionId,
    title: cleanString(source.title, base.title, 200),
    turn,
    mode: cleanString(source.mode, base.mode, 64),
    canonLoaded: source.canonLoaded === true,
    location: cleanString(source.location, base.location, 1000),
    player: source.player && typeof source.player === "object" && !Array.isArray(source.player) ? source.player : base.player,
    party: Array.isArray(source.party) ? source.party : [],
    inventory: Array.isArray(source.inventory) ? source.inventory : [],
    flags: source.flags && typeof source.flags === "object" && !Array.isArray(source.flags) ? source.flags : {},
    snapshotUrl: typeof source.snapshotUrl === "string" ? source.snapshotUrl.slice(0, 8000) : null,
    log: cleanLog(source.log),
    updatedAt: new Date().toISOString(),
    revision: keepRevision && typeof source.revision === "string" ? source.revision : crypto.randomUUID(),
  };
}

async function cacheGet(sessionId) {
  const value = await cache.get(sessionId);
  if (!value) return null;
  return typeof value === "string" ? JSON.parse(value) : value;
}

async function cacheSet(sessionId, state) {
  await cache.set(sessionId, state, {
    ttl: CACHE_TTL_SECONDS,
    tags: [`backroom-session-${sessionId}`],
    name: "Backroom game state",
  });
}

async function databaseGet(sessionId) {
  await ensureDatabase();
  const rows = await db()`SELECT state FROM backroom_game_states WHERE session_id = ${sessionId} LIMIT 1`;
  return rows[0]?.state || null;
}

async function databaseSet(sessionId, state, expectedRevision) {
  await ensureDatabase();
  const payload = JSON.stringify(state);

  if (typeof expectedRevision === "string" && expectedRevision) {
    const rows = await db()`
      UPDATE backroom_game_states
      SET state = ${payload}::jsonb,
          revision = ${state.revision},
          turn = ${state.turn},
          updated_at = now()
      WHERE session_id = ${sessionId}
        AND revision = ${expectedRevision}
      RETURNING state
    `;
    if (!rows.length) throw new StateConflictError();
    return rows[0].state;
  }

  const rows = await db()`
    INSERT INTO backroom_game_states (session_id, state, revision, turn, updated_at)
    VALUES (${sessionId}, ${payload}::jsonb, ${state.revision}, ${state.turn}, now())
    ON CONFLICT (session_id) DO UPDATE
    SET state = EXCLUDED.state,
        revision = EXCLUDED.revision,
        turn = EXCLUDED.turn,
        updated_at = now()
    RETURNING state
  `;
  return rows[0].state;
}

export async function loadState(sessionId) {
  let state = hasDatabase() ? await databaseGet(sessionId) : await cacheGet(sessionId);
  if (!state) {
    state = defaultState(sessionId);
    if (hasDatabase()) await databaseSet(sessionId, state);
    else await cacheSet(sessionId, state);
  }
  return normalizeState(state, sessionId, { keepRevision: true });
}

export async function saveState(sessionId, input, expectedRevision) {
  const current = await loadState(sessionId);
  if (expectedRevision && current.revision !== expectedRevision) throw new StateConflictError();

  const candidate = normalizeState(input, sessionId);
  if (candidate.turn < current.turn) throw new StateConflictError("Không thể ghi đè một turn mới bằng state cũ hơn.");

  if (hasDatabase()) {
    return databaseSet(sessionId, candidate, expectedRevision || current.revision);
  }

  // Runtime Cache không cung cấp compare-and-swap. Kiểm tra revision/turn trước khi ghi,
  // rồi xác minh lại sau khi ghi để tránh báo thành công khi backend không giữ bản vừa lưu.
  const latest = await cacheGet(sessionId);
  if (latest && expectedRevision && latest.revision !== expectedRevision) throw new StateConflictError();
  if (latest && Number.isInteger(latest.turn) && candidate.turn < latest.turn) throw new StateConflictError();
  await cacheSet(sessionId, candidate);
  const stored = await cacheGet(sessionId);
  if (!stored || stored.revision !== candidate.revision || stored.turn !== candidate.turn) {
    throw new StateConflictError("State không được backend xác nhận sau khi ghi.");
  }
  return normalizeState(stored, sessionId, { keepRevision: true });
}

export async function resetState(sessionId) {
  const state = defaultState(sessionId);
  if (hasDatabase()) return databaseSet(sessionId, state);
  await cacheSet(sessionId, state);
  return state;
}
