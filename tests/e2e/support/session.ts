import { readFileSync } from "node:fs";
import Redis from "ioredis";
import { EncryptJWT, jwtDecrypt } from "jose";

/**
 * Test-only access to the BFF session store, used to age or corrupt a session and observe the
 * server-side refresh. Mirrors apps/web/src/lib/auth.ts (same key, issuer and audience).
 */
export type StoredSession = {
  accessToken: string;
  idToken: string;
  refreshToken: string;
  accessExp: number;
  createdAt: number;
  subject: string;
  name: string;
  roles: string[];
  mfa: boolean;
};

function webEnv(): Record<string, string> {
  return Object.fromEntries(
    readFileSync("apps/web/.env.local", "utf8")
      .replace(/^﻿/, "")
      .split(/\r?\n/)
      .filter((line) => line.includes("="))
      .map((line) => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]),
  );
}
const key = () => Buffer.from(webEnv().SESSION_SECRET, "base64");
const redisKey = (sessionId: string) => `vshop:session:${sessionId}`;

export async function withRedis<T>(run: (redis: Redis) => Promise<T>): Promise<T> {
  const redis = new Redis(webEnv().REDIS_URL, { maxRetriesPerRequest: 1 });
  try {
    return await run(redis);
  } finally {
    redis.disconnect();
  }
}

export async function readRecord(sessionId: string): Promise<StoredSession | null> {
  return withRedis(async (redis) => {
    const sealed = await redis.get(redisKey(sessionId));
    if (!sealed) return null;
    const { payload } = await jwtDecrypt(sealed, key(), {
      issuer: "vshop-web",
      audience: "vshop-web",
    });
    return payload as unknown as StoredSession;
  });
}

/** Rewrites the record keeping its remaining TTL. */
export async function updateRecord(sessionId: string, change: Partial<StoredSession>) {
  const current = await readRecord(sessionId);
  if (!current) throw new Error("No session record to update");
  await withRedis(async (redis) => {
    const ttl = await redis.ttl(redisKey(sessionId));
    const sealed = await new EncryptJWT({ ...current, ...change })
      .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
      .setIssuedAt()
      .setExpirationTime(Math.floor(Date.now() / 1000) + Math.max(ttl, 60))
      .setIssuer("vshop-web")
      .setAudience("vshop-web")
      .encrypt(key());
    await redis.set(redisKey(sessionId), sealed, "EX", Math.max(ttl, 60));
  });
}

export async function recordExists(sessionId: string): Promise<boolean> {
  return withRedis(async (redis) => (await redis.exists(redisKey(sessionId))) === 1);
}
