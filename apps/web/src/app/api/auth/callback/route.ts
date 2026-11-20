import { NextRequest, NextResponse } from "next/server";
import {
  FLOW_COOKIE,
  SESSION_COOKIE,
  SESSION_MAX_SECONDS,
  cookieOptions,
  createSession,
  issuer,
  safeReturnTo,
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
    if (!tokens.refresh_token) throw new Error("Missing refresh token");
    const sessionId = await createSession(tokens, verified);
    const response = NextResponse.redirect(new URL(safeReturnTo(String(flow.returnTo)), webBase()));
    // The cookie is only an opaque id; tokens live in the server-side session store.
    response.cookies.set(SESSION_COOKIE, sessionId, {
      ...cookieOptions(),
      maxAge: SESSION_MAX_SECONDS,
    });
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
