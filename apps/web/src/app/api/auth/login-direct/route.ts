import { createHash } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import {
  SESSION_COOKIE,
  SESSION_MAX_SECONDS,
  cookieOptions,
  directLogin,
  safeReturnTo,
  webBase,
} from "@/lib/auth";
import { allowAttempt } from "@/lib/session-store";

const noStore = { "Cache-Control": "no-store" };
const fail = (status: number, code: string, message: string, extra: object = {}) =>
  NextResponse.json({ code, message, ...extra }, { status, headers: noStore });

/**
 * Sign-in from the in-site form. Origin is checked, attempts are limited per address and per email
 * (Keycloak also locks accounts that are guessed at), and the same message is used for an unknown
 * email and a wrong password.
 */
export async function POST(request: NextRequest) {
  if (request.headers.get("origin") !== new URL(webBase()).origin)
    return fail(403, "FORBIDDEN", "Yêu cầu không hợp lệ.");
  const text = await request.text();
  if (Buffer.byteLength(text, "utf8") > 1_024)
    return fail(413, "TOO_LARGE", "Thông tin vượt giới hạn.");
  let body: { email?: unknown; password?: unknown; returnTo?: unknown };
  try {
    body = JSON.parse(text);
  } catch {
    return fail(400, "BAD_REQUEST", "Thông tin không hợp lệ.");
  }
  if (
    typeof body.email !== "string" ||
    typeof body.password !== "string" ||
    body.email.length === 0 ||
    body.email.length > 254 ||
    body.password.length === 0 ||
    body.password.length > 200
  )
    return fail(400, "BAD_REQUEST", "Vui lòng nhập email và mật khẩu.");

  const email = body.email.trim().toLowerCase();
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "local";
  const emailKey = createHash("sha256").update(email).digest("hex");
  try {
    const allowed =
      (await allowAttempt(`login:ip:${ip}`, 40, 600)) &&
      (await allowAttempt(`login:email:${emailKey}`, 10, 600));
    if (!allowed)
      return fail(429, "RATE_LIMITED", "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau ít phút.");
  } catch {
    return fail(503, "UNAVAILABLE", "Đăng nhập tạm thời chưa khả dụng. Vui lòng thử lại sau.");
  }

  const result = await directLogin(email, body.password);
  if (!result.ok) {
    if (result.reason === "invalid")
      return fail(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.");
    if (result.reason === "browser")
      return fail(
        409,
        "USE_BROWSER_LOGIN",
        "Tài khoản này cần đăng nhập qua trang xác thực (tài khoản quản trị hoặc cần hoàn tất bước bảo mật).",
        { ssoUrl: "/api/auth/login?mode=sso" },
      );
    return fail(503, "UNAVAILABLE", "Đăng nhập tạm thời chưa khả dụng. Vui lòng thử lại sau.");
  }
  const response = NextResponse.json(
    { ok: true, returnTo: safeReturnTo(typeof body.returnTo === "string" ? body.returnTo : null) },
    { headers: noStore },
  );
  // The cookie is only an opaque id; tokens live in the server-side session store.
  response.cookies.set(SESSION_COOKIE, result.sessionId, {
    ...cookieOptions(),
    maxAge: SESSION_MAX_SECONDS,
  });
  return response;
}
