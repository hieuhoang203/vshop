import { NextRequest, NextResponse } from "next/server";
import { apiBase } from "@/lib/api";
import { webBase } from "@/lib/auth";
import { captchaEnabled, verifyCaptcha } from "@/lib/captcha";
import { allowAttempt } from "@/lib/session-store";

const noStore = { "Cache-Control": "no-store" };
const fail = (status: number, code: string, message: string) =>
  NextResponse.json({ code, message }, { status, headers: noStore });

/** Asks the API to e-mail a reset link. The answer never reveals whether the address exists. */
export async function POST(request: NextRequest) {
  if (request.headers.get("origin") !== new URL(webBase()).origin)
    return fail(403, "FORBIDDEN", "Yêu cầu không hợp lệ.");
  const text = await request.text();
  if (Buffer.byteLength(text, "utf8") > 4_500)
    return fail(413, "TOO_LARGE", "Thông tin vượt giới hạn.");
  let body: { email?: unknown; captchaToken?: unknown };
  try {
    body = JSON.parse(text);
  } catch {
    return fail(400, "BAD_REQUEST", "Thông tin không hợp lệ.");
  }
  if (typeof body.email !== "string") return fail(400, "BAD_REQUEST", "Thông tin không hợp lệ.");
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "local";
  try {
    if (!(await allowAttempt(`forgot:ip:${ip}`, 10, 3600)))
      return fail(429, "RATE_LIMITED", "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau ít phút.");
  } catch {
    return fail(503, "UNAVAILABLE", "Tạm thời chưa khả dụng. Vui lòng thử lại sau.");
  }
  if (captchaEnabled() && !(await verifyCaptcha(body.captchaToken, ip)))
    return fail(400, "CAPTCHA_FAILED", "Vui lòng xác nhận bạn không phải người máy.");
  try {
    const result = await fetch(`${apiBase()}/api/v1/auth/forgot-password`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: body.email }),
      cache: "no-store",
      signal: AbortSignal.timeout(15_000),
    });
    if (result.status === 202)
      return NextResponse.json({ ok: true }, { status: 202, headers: noStore });
    const data = (await result.json().catch(() => ({}))) as { code?: string; message?: string };
    return fail(
      result.status >= 500 ? 503 : result.status,
      data.code ?? "ERROR",
      data.message ?? "Không thể gửi email lúc này.",
    );
  } catch {
    return fail(503, "UNAVAILABLE", "Không thể gửi email lúc này. Vui lòng thử lại sau.");
  }
}
