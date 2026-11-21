import { randomBytes, createHash } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { EMAIL_PATTERN } from "@/lib/register-rules";
import { FLOW_COOKIE, cookieOptions, issuer, safeReturnTo, seal, webBase } from "@/lib/auth";
export async function GET(request: NextRequest) {
  const state = randomBytes(24).toString("base64url");
  const nonce = randomBytes(24).toString("base64url");
  const verifier = randomBytes(32).toString("base64url");
  const redirect = new URL(`${issuer()}/protocol/openid-connect/auth`);
  // Pre-fills the sign-in form after sign-up; only a plausible email address is passed on.
  const hint = request.nextUrl.searchParams.get("login_hint") ?? "";
  redirect.search = new URLSearchParams({
    ...(hint.length <= 254 && EMAIL_PATTERN.test(hint) ? { login_hint: hint } : {}),
    client_id: "vshop-web",
    response_type: "code",
    scope: "openid profile email",
    redirect_uri: `${webBase()}/api/auth/callback`,
    state,
    nonce,
    code_challenge: createHash("sha256").update(verifier).digest("base64url"),
    code_challenge_method: "S256",
  }).toString();
  const response = NextResponse.redirect(redirect);
  response.cookies.set(
    FLOW_COOKIE,
    await seal(
      {
        state,
        nonce,
        verifier,
        returnTo: safeReturnTo(request.nextUrl.searchParams.get("returnTo")),
      },
      600,
    ),
    { ...cookieOptions(), maxAge: 600 },
  );
  response.headers.set("Cache-Control", "no-store");
  return response;
}
