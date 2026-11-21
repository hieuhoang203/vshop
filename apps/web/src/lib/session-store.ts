import "server-only";
import { randomBytes } from "node:crypto";
import Redis from "ioredis";

/**
 * Server-side session store. The browser holds only an opaque random session id; tokens
 * (including the refresh token) never leave the server. Records are encrypted at rest with
 * SESSION_SECRET by the caller. Redis is a cache-grade store here: if it is lost, users sign in
 * again, and nothing about money, stock or orders depends on it.
 */
const KEY_PREFIX = "vshop:session:";
const LOCK_PREFIX = "vshop:session-lock:";
export const SESSION_ID_PATTERN = /^[A-Za-z0-9_-]{43}$/;

const globalForRedis = globalThis as unknown as { vshopRedis?: Redis };

function client(): Redis {
  if (!globalForRedis.vshopRedis) {
    const url = process.env.REDIS_URL;
    if (!url) throw new Error("REDIS_URL is not configured. Run scripts/init-local.ps1.");
    globalForRedis.vshopRedis = new Redis(url, {
      maxRetriesPerRequest: 1,
      connectTimeout: 2000,
      commandTimeout: 2000,
    });
    // Commands issued while the first connection is still opening wait in the offline queue.
    // Without an error listener ioredis would crash the process on connection errors.
    globalForRedis.vshopRedis.on("error", () => {});
  }
  return globalForRedis.vshopRedis;
}

export const newSessionId = () => randomBytes(32).toString("base64url");

export async function storeSession(sessionId: string, sealed: string, ttlSeconds: number) {
  await client().set(KEY_PREFIX + sessionId, sealed, "EX", Math.max(1, Math.floor(ttlSeconds)));
}

export async function readSession(sessionId: string): Promise<string | null> {
  return client().get(KEY_PREFIX + sessionId);
}

export async function removeSession(sessionId: string) {
  await client().del(KEY_PREFIX + sessionId);
}

/** Short-lived lock so that concurrent requests refresh a session's tokens only once. */
export async function acquireRefreshLock(sessionId: string): Promise<string | null> {
  const token = randomBytes(12).toString("hex");
  const ok = await client().set(LOCK_PREFIX + sessionId, token, "PX", 10_000, "NX");
  return ok === "OK" ? token : null;
}

export async function releaseRefreshLock(sessionId: string, token: string) {
  // Delete only our own lock; an expired lock may already belong to someone else.
  await client().eval(
    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
    1,
    LOCK_PREFIX + sessionId,
    token,
  );
}

/**
 * Fixed-window counter for public endpoints. Returns false once {@code limit} calls were made in
 * the current window. Callers must treat a Redis failure as "not allowed" for abuse-prone routes.
 */
export async function allowAttempt(key: string, limit: number, windowSeconds: number) {
  const redisKey = `vshop:rate:${key}`;
  const count = await client().incr(redisKey);
  if (count === 1) await client().expire(redisKey, windowSeconds);
  return count <= limit;
}
