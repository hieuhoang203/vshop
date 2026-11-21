import "server-only";
import { cache } from "react";
import { cookies } from "next/headers";
import { createRemoteJWKSet, EncryptJWT, jwtDecrypt, jwtVerify, type JWTPayload } from "jose";
import {
  SESSION_ID_PATTERN,
  acquireRefreshLock,
  newSessionId,
  readSession,
  releaseRefreshLock,
  removeSession,
  storeSession,
} from "./session-store";

export const SESSION_COOKIE = "vshop_session";
export const FLOW_COOKIE = "vshop_oidc_flow";
/** Absolute session lifetime regardless of activity. */
export const SESSION_MAX_SECONDS = 8 * 60 * 60;
/** Refresh when the access token has less than this many seconds left. */
const REFRESH_MARGIN_SECONDS = 30;
const DEFAULT_IDLE_SECONDS = 1800;

export const issuer = () => process.env.KEYCLOAK_ISSUER ?? "http://localhost:8180/realms/vshop";
export const webBase = () => process.env.WEB_BASE_URL ?? "http://localhost:3100";
export const cookieOptions = () => ({
  httpOnly: true,
  secure: webBase().startsWith("https:"),
  sameSite: "lax" as const,
  path: "/",
});
function secret() {
  const key = Buffer.from(process.env.SESSION_SECRET ?? "", "base64");
  if (key.length !== 32)
    throw new Error("SESSION_SECRET must be a generated 32-byte secret. Run init-local.ps1.");
  return key;
}
export async function seal(payload: Record<string, unknown>, seconds: number) {
  return new EncryptJWT(payload)
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuedAt()
    .setExpirationTime(Math.floor(Date.now() / 1000) + seconds)
    .setIssuer("vshop-web")
    .setAudience("vshop-web")
    .encrypt(secret());
}
export async function unseal(value: string) {
  return (
    await jwtDecrypt(value, secret(), {
      issuer: "vshop-web",
      audience: "vshop-web",
    })
  ).payload;
}

/** What pages and route handlers see. Tokens are always fresh when a Session is returned. */
export type Session = {
  accessToken: string;
  idToken: string;
  name: string;
  roles: string[];
  subject: string;
  /** True only when the token proves a second factor (amr contains "otp"). */
  mfa: boolean;
};
type StoredSession = Session & {
  refreshToken: string;
  /** Access token expiry, epoch seconds. */
  accessExp: number;
  /** Session creation, epoch seconds; bounds the absolute lifetime. */
  createdAt: number;
};

const nowSeconds = () => Math.floor(Date.now() / 1000);
const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
const isFresh = (record: StoredSession) => record.accessExp - nowSeconds() > REFRESH_MARGIN_SECONDS;

async function readRecord(sessionId: string): Promise<StoredSession | null> {
  const sealed = await readSession(sessionId);
  if (!sealed) return null;
  try {
    return (await unseal(sealed)) as unknown as StoredSession;
  } catch {
    // Unreadable (for example the secret was rotated): treat as signed out.
    await removeSession(sessionId);
    return null;
  }
}

async function writeRecord(sessionId: string, record: StoredSession, idleSeconds: number) {
  const remaining = record.createdAt + SESSION_MAX_SECONDS - nowSeconds();
  const ttl = Math.min(idleSeconds, remaining);
  if (ttl < 1) return removeSession(sessionId);
  await storeSession(sessionId, await seal(record as unknown as Record<string, unknown>, ttl), ttl);
}

export const rolesOf = (token: JWTPayload) => {
  const roles = (token.realm_access as { roles?: string[] } | undefined)?.roles ?? [];
  return roles.filter((r) => ["BUYER", "SELLER", "ADMIN"].includes(r));
};
export const hasSecondFactor = (token: JWTPayload) =>
  Array.isArray(token.amr) && (token.amr as unknown[]).includes("otp");

/** Creates a server-side session from a verified code exchange; returns the cookie value. */
export async function createSession(
  tokens: {
    access_token: string;
    id_token: string;
    refresh_token: string;
    refresh_expires_in?: number;
  },
  verified: { token: JWTPayload; identity: JWTPayload; roles: string[] },
) {
  const sessionId = newSessionId();
  const record: StoredSession = {
    accessToken: tokens.access_token,
    idToken: tokens.id_token,
    refreshToken: tokens.refresh_token,
    accessExp: Number(verified.token.exp),
    createdAt: nowSeconds(),
    subject: String(verified.token.sub),
    // Real name when the account has one (sign-up collects it), else the username.
    name: String(verified.identity.name || verified.identity.preferred_username || "Tài khoản"),
    roles: verified.roles,
    mfa: hasSecondFactor(verified.token),
  };
  await writeRecord(sessionId, record, Number(tokens.refresh_expires_in) || DEFAULT_IDLE_SECONDS);
  return sessionId;
}

export async function destroySession(sessionId: string) {
  await removeSession(sessionId);
}

export async function currentSessionId(): Promise<string | null> {
  const value = (await cookies()).get(SESSION_COOKIE)?.value;
  return value && SESSION_ID_PATTERN.test(value) ? value : null;
}

/**
 * Refreshes tokens once per session even when many requests notice the expiry together: one
 * request takes a Redis lock, the others wait for its result. The browser cookie never changes, so
 * this works from Server Components too.
 */
async function refresh(sessionId: string): Promise<StoredSession | null> {
  const lock = await acquireRefreshLock(sessionId);
  if (!lock) {
    for (let i = 0; i < 25; i++) {
      await sleep(150);
      const other = await readRecord(sessionId);
      if (!other) return null;
      if (isFresh(other)) return other;
    }
    return null;
  }
  try {
    const record = await readRecord(sessionId);
    if (!record) return null;
    if (isFresh(record)) return record; // another process refreshed just before us
    const response = await fetch(`${issuer()}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      cache: "no-store",
      signal: AbortSignal.timeout(5000),
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: "vshop-web",
        refresh_token: record.refreshToken,
      }),
    });
    if (response.status >= 400 && response.status < 500) {
      // invalid_grant: the Keycloak session ended or the refresh token expired.
      await removeSession(sessionId);
      return null;
    }
    if (!response.ok) return null; // Keycloak trouble: keep the session and try again later
    const tokens = await response.json();
    const token = await verifyAccessToken(tokens.access_token);
    if (token.sub !== record.subject) {
      await removeSession(sessionId);
      return null;
    }
    const updated: StoredSession = {
      ...record,
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token ?? record.refreshToken,
      idToken: tokens.id_token ?? record.idToken,
      accessExp: Number(token.exp),
      roles: rolesOf(token),
      mfa: hasSecondFactor(token),
    };
    await writeRecord(
      sessionId,
      updated,
      Number(tokens.refresh_expires_in) || DEFAULT_IDLE_SECONDS,
    );
    return updated;
  } catch {
    return null;
  } finally {
    await releaseRefreshLock(sessionId, lock).catch(() => {});
  }
}

/** Current session or null. Memoised per request so layout, page and API calls share one read. */
export const getSession = cache(async (): Promise<Session | null> => {
  const sessionId = await currentSessionId();
  if (!sessionId) return null;
  try {
    let record = await readRecord(sessionId);
    if (!record) return null;
    if (nowSeconds() - record.createdAt > SESSION_MAX_SECONDS) {
      await removeSession(sessionId);
      return null;
    }
    if (!isFresh(record)) record = await refresh(sessionId);
    if (!record) return null;
    const { accessToken, idToken, name, roles, subject, mfa } = record;
    return { accessToken, idToken, name, roles, subject, mfa };
  } catch {
    // Redis unreachable or secret misconfigured: behave as signed out rather than erroring pages.
    return null;
  }
});

export function safeReturnTo(value: string | null) {
  if (
    value === "/seller" ||
    value === "/admin" ||
    value === "/cart" ||
    value === "/account/addresses"
  )
    return value;
  // Product pages only, so the return path can never point off-site or at an API route.
  return value !== null && /^\/products\/[a-z0-9-]{1,160}$/.test(value) ? value : "/";
}
let jwks: ReturnType<typeof createRemoteJWKSet> | undefined;
const keys = () =>
  (jwks ??= createRemoteJWKSet(new URL(`${issuer()}/protocol/openid-connect/certs`)));
export async function verifyAccessToken(access: string) {
  return (
    await jwtVerify(access, keys(), {
      issuer: issuer(),
      audience: "vshop-api",
      algorithms: ["RS256"],
    })
  ).payload;
}
export async function verifyTokens(access: string, id: string, nonce: string) {
  const token = await verifyAccessToken(access);
  const identity = (
    await jwtVerify(id, keys(), {
      issuer: issuer(),
      audience: "vshop-web",
      algorithms: ["RS256"],
    })
  ).payload;
  if (identity.nonce !== nonce || identity.sub !== token.sub)
    throw new Error("Invalid OIDC identity");
  return { token, identity, roles: rolesOf(token) };
}
