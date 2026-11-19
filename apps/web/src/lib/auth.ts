import "server-only";
import { cookies } from "next/headers";
import { createRemoteJWKSet, EncryptJWT, jwtDecrypt, jwtVerify } from "jose";

export const SESSION_COOKIE = "vshop_session";
export const FLOW_COOKIE = "vshop_oidc_flow";
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
export type Session = {
  accessToken: string;
  idToken: string;
  name: string;
  roles: string[];
  subject: string;
};
export async function getSession(): Promise<Session | null> {
  const value = (await cookies()).get(SESSION_COOKIE)?.value;
  if (!value) return null;
  try {
    const payload = await unseal(value);
    return payload as unknown as Session;
  } catch {
    return null;
  }
}
export function safeReturnTo(value: string | null) {
  return value === "/seller" || value === "/admin" ? value : "/";
}
let jwks: ReturnType<typeof createRemoteJWKSet> | undefined;
export async function verifyTokens(access: string, id: string, nonce: string) {
  jwks ??= createRemoteJWKSet(new URL(`${issuer()}/protocol/openid-connect/certs`));
  const token = (
    await jwtVerify(access, jwks, {
      issuer: issuer(),
      audience: "vshop-api",
      algorithms: ["RS256"],
    })
  ).payload;
  const identity = (
    await jwtVerify(id, jwks, {
      issuer: issuer(),
      audience: "vshop-web",
      algorithms: ["RS256"],
    })
  ).payload;
  if (identity.nonce !== nonce || identity.sub !== token.sub)
    throw new Error("Invalid OIDC identity");
  const roles = (token.realm_access as { roles?: string[] } | undefined)?.roles ?? [];
  return {
    token,
    identity,
    roles: roles.filter((r) => ["BUYER", "SELLER", "ADMIN"].includes(r)),
  };
}
