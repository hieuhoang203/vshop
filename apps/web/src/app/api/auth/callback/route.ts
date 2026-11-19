import { NextRequest, NextResponse } from "next/server";
import {
  FLOW_COOKIE,
  SESSION_COOKIE,
  cookieOptions,
  issuer,
  safeReturnTo,
  seal,
  unseal,
  verifyTokens,
  webBase,
} from "@/lib/auth";
export async function GET(request: NextRequest) {
  try {
    const flowCookie = request.cookies.get(FLOW_COOKIE)?.value;
    if (!flowCookie) throw new Error("Missing flow");
    const flow = await unseal(flowCookie);
    const code = request.nextUrl.searchParams.get("code");
    if (
      !code ||
      request.nextUrl.searchParams.get("state") !== flow.state ||
      typeof flow.verifier !== "string"
    )
      throw new Error("Invalid state");
    const result = await fetch(`${issuer()}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      cache: "no-store",
      signal: AbortSignal.timeout(8000),
      body: new URLSearchParams({
        grant_type: "authorization_code",
        client_id: "vshop-web",
        code,
        redirect_uri: `${webBase()}/api/auth/callback`,
        code_verifier: flow.verifier,
      }),
    });
    if (!result.ok) throw new Error("Token exchange failed");
    const tokens = await result.json();
    const verified = await verifyTokens(tokens.access_token, tokens.id_token, String(flow.nonce));
    const remaining = Math.min(
      Number(tokens.expires_in),
      Number(verified.token.exp) - Math.floor(Date.now() / 1000),
    );
    if (!Number.isFinite(remaining) || remaining < 1) throw new Error("Expired token");
    const response = NextResponse.redirect(new URL(safeReturnTo(String(flow.returnTo)), webBase()));
    response.cookies.set(
      SESSION_COOKIE,
      await seal(
        {
          accessToken: tokens.access_token,
          idToken: tokens.id_token,
          subject: verified.token.sub,
          name: verified.identity.preferred_username ?? "Tài khoản",
          roles: verified.roles,
        },
        remaining,
      ),
      { ...cookieOptions(), maxAge: remaining },
    );
    response.cookies.delete(FLOW_COOKIE);
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch {
    const response = NextResponse.redirect(new URL("/auth-error", webBase()));
    response.cookies.delete(FLOW_COOKIE);
    response.cookies.delete(SESSION_COOKIE);
    return response;
  }
}
