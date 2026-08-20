const MAX_BASE64_CHARS = 1_500_000;
const snapshots = new Map();

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

  snapshots.set(sessionId, { ...stored });

  return stored;
}

export async function loadSnapshot(sessionId) {
  const value = snapshots.get(sessionId);
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
  snapshots.delete(sessionId);
}
