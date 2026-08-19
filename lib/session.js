import crypto from "node:crypto";
import { cookies } from "next/headers";

const COOKIE_NAME = "backroom_session";
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export async function getSessionId() {
  const jar = await cookies();
  const existing = jar.get(COOKIE_NAME)?.value;
  if (existing && UUID_RE.test(existing)) return existing;

  const sessionId = crypto.randomUUID();
  jar.set(COOKIE_NAME, sessionId, {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 365,
  });
  return sessionId;
}
