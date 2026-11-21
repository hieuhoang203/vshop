import "server-only";

/**
 * Cloudflare Turnstile (a privacy-friendly CAPTCHA). It is switched on only when both keys are in
 * the environment; without them the sign-up and reset forms work with rate limits alone.
 */
export const captchaSiteKey = () => process.env.TURNSTILE_SITE_KEY ?? "";
export const captchaEnabled = () => captchaSiteKey() !== "" && !!process.env.TURNSTILE_SECRET_KEY;

export async function verifyCaptcha(token: unknown, ip: string): Promise<boolean> {
  if (typeof token !== "string" || token.length === 0 || token.length > 4096) return false;
  try {
    const response = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        secret: process.env.TURNSTILE_SECRET_KEY ?? "",
        response: token,
        ...(ip !== "local" ? { remoteip: ip } : {}),
      }),
      cache: "no-store",
      signal: AbortSignal.timeout(5000),
    });
    return response.ok && ((await response.json()) as { success?: boolean }).success === true;
  } catch {
    return false;
  }
}
