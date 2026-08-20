import crypto from "node:crypto";
import { neon } from "@neondatabase/serverless";
import {
  CANON_VERSION,
  PREVIOUS_TURN9_CANON_VERSION,
  createNewGameState,
} from "./canon.js";

const memoryStates = new Map();

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
  return hasDatabase() ? "neon-postgres" : "memory";
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

function objectOr(value, fallback = {}) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : fallback;
}

function mergeKnownState(base, source) {
  const input = objectOr(source);
  const merged = { ...base, ...input };
  for (const key of ["communication", "iris", "syvial", "jeff", "madGod", "exploration", "omnivault"]) {
    if (objectOr(base?.[key], null)) {
      merged[key] = { ...base[key], ...objectOr(input[key]) };
    }
  }
  return merged;
}

export function defaultState(sessionId) {
  return createNewGameState(sessionId);
}

export function normalizeState(input, sessionId, { keepRevision = false } = {}) {
  const source = input && typeof input === "object" ? input : {};
  const base = defaultState(sessionId);
  const turn = Number.isInteger(source.turn) && source.turn >= 1 ? source.turn : base.turn;
  const log = cleanLog(source.log);

  return {
    ...source,
    version: Math.max(Number.isInteger(source.version) && source.version > 0 ? source.version : 0, base.version),
    sessionId,
    title: cleanString(source.title, base.title, 200),
    level: objectOr(source.level, base.level),
    turn,
    mode: cleanString(source.mode, base.mode, 64),
    canonLoaded: true,
    canonVersion: CANON_VERSION,
    location: cleanString(source.location, base.location, 1000),
    player: {
      ...base.player,
      ...objectOr(source.player),
      needs: { ...objectOr(base.player?.needs), ...objectOr(source.player?.needs) },
    },
    party: Array.isArray(source.party) ? source.party : base.party,
    inventory: Array.isArray(source.inventory) ? source.inventory : base.inventory,
    flags: mergeKnownState(base.flags, source.flags),
    snapshotUrl: typeof source.snapshotUrl === "string" ? source.snapshotUrl.slice(0, 8000) : null,
    log: log.length ? log : base.log,
    updatedAt: new Date().toISOString(),
    revision: keepRevision && typeof source.revision === "string" ? source.revision : crypto.randomUUID(),
  };
}

async function cacheGet(sessionId) {
  const value = memoryStates.get(sessionId);
  if (!value) return null;
  return JSON.parse(JSON.stringify(value));
}

async function cacheSet(sessionId, state) {
  memoryStates.set(sessionId, JSON.parse(JSON.stringify(state)));
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

async function writeWithoutConflict(sessionId, state) {
  if (hasDatabase()) return databaseSet(sessionId, state);
  await cacheSet(sessionId, state);
  return state;
}

function needsNewGameMigration(state) {
  if (!state) return true;
  if (state.canonLoaded !== true) return true;
  // The previous deployment incorrectly treated one specific Turn 9 save as the
  // default start for every player. Replace those seeded sessions with a real NEW GAME.
  if (state.canonVersion === PREVIOUS_TURN9_CANON_VERSION) return true;
  return false;
}

export async function loadState(sessionId) {
  let state = hasDatabase() ? await databaseGet(sessionId) : await cacheGet(sessionId);
  if (needsNewGameMigration(state)) {
    state = defaultState(sessionId);
    await writeWithoutConflict(sessionId, state);
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
  return writeWithoutConflict(sessionId, state);
}
