import { createHash } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { apiBase } from "@/lib/api";
import {
  SESSION_COOKIE,
  SESSION_MAX_SECONDS,
  cookieOptions,
  directLogin,
  webBase,
} from "@/lib/auth";
import { captchaEnabled, verifyCaptcha } from "@/lib/captcha";
import { allowAttempt } from "@/lib/session-store";

const noStore = { "Cache-Control": "no-store" };
const fail = (status: number, message: string, code?: string) =>
  NextResponse.json({ code, message }, { status, headers: noStore });

/**
 * Public sign-up. The browser never talks to the API or the identity provider directly: this route
 * checks Origin, rate-limits per address and per email, and forwards only the four expected fields.
 * The password is passed through once and never logged or stored here.
 */
export async function POST(request: NextRequest) {
  if (request.headers.get("origin") !== new URL(webBase()).origin)
    return fail(403, "Yêu cầu không hợp lệ.");
  const text = await request.text();
  if (Buffer.byteLength(text, "utf8") > 6_144) return fail(413, "Thông tin vượt giới hạn.");
  let body: {
    fullName?: unknown;
    email?: unknown;
    password?: unknown;
    acceptTerms?: unknown;
    captchaToken?: unknown;
  };
  try {
    body = JSON.parse(text);
  } catch {
    return fail(400, "Thông tin không hợp lệ.");
  }
  if (
    typeof body.fullName !== "string" ||
    typeof body.email !== "string" ||
    typeof body.password !== "string"
  )
    return fail(400, "Thông tin không hợp lệ.");

  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "local";
  const emailKey = createHash("sha256").update(body.email.trim().toLowerCase()).digest("hex");
  try {
    const allowed =
      (await allowAttempt(`register:ip:${ip}`, 10, 3600)) &&
      (await allowAttempt(`register:email:${emailKey}`, 5, 3600));
    if (!allowed)
      return fail(429, "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau ít phút.", "RATE_LIMITED");
  } catch {
    // Abuse protection must not silently turn off.
    return fail(
      503,
      "Đăng ký tạm thời chưa khả dụng. Vui lòng thử lại sau.",
      "REGISTRATION_UNAVAILABLE",
    );
  }

  if (captchaEnabled() && !(await verifyCaptcha(body.captchaToken, ip)))
    return fail(400, "Vui lòng xác nhận bạn không phải người máy.", "CAPTCHA_FAILED");

  try {
    const result = await fetch(`${apiBase()}/api/v1/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fullName: body.fullName,
        email: body.email,
        password: body.password,
        acceptTerms: body.acceptTerms === true,
      }),
      cache: "no-store",
      signal: AbortSignal.timeout(15_000),
    });
    if (result.status === 201) {
      // Sign the new buyer straight in with the password just chosen; if that does not work the
      // form falls back to the sign-in page.
      const login = await directLogin(body.email.trim().toLowerCase(), body.password);
      const response = NextResponse.json(
        { ok: true, signedIn: login.ok },
        { status: 201, headers: noStore },
      );
      if (login.ok)
        response.cookies.set(SESSION_COOKIE, login.sessionId, {
          ...cookieOptions(),
          maxAge: SESSION_MAX_SECONDS,
        });
      return response;
    }
    const data = (await result.json().catch(() => ({}))) as { code?: string; message?: string };
    return fail(
      result.status >= 500 ? 503 : result.status,
      data.message ?? "Không thể tạo tài khoản lúc này.",
      data.code,
    );
  } catch {
    return fail(
      503,
      "Không thể tạo tài khoản lúc này. Vui lòng thử lại sau.",
      "REGISTRATION_UNAVAILABLE",
    );
  }
}
