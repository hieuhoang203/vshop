import { NextRequest, NextResponse } from "next/server";
import {
  currentSessionId,
  destroySession,
  getSession,
  issuer,
  SESSION_COOKIE,
  webBase,
} from "@/lib/auth";
export async function POST(request: NextRequest) {
  if (request.headers.get("origin") !== new URL(webBase()).origin)
    return new NextResponse("Forbidden", { status: 403 });
  const session = await getSession();
  const sessionId = await currentSessionId();
  // Remove the server-side record first, so the session is dead even if Keycloak is unreachable.
  if (sessionId) await destroySession(sessionId).catch(() => {});
  const url = new URL(`${issuer()}/protocol/openid-connect/logout`);
  url.searchParams.set("client_id", "vshop-web");
  url.searchParams.set("post_logout_redirect_uri", `${webBase()}/`);
  if (session) url.searchParams.set("id_token_hint", session.idToken);
  const response = NextResponse.redirect(session ? url : new URL(webBase()), 303);
  response.cookies.delete(SESSION_COOKIE);
  response.headers.set("Cache-Control", "no-store");
  return response;
}
