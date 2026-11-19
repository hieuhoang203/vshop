import { test, expect } from "@playwright/test";
import { createHash, randomBytes } from "node:crypto";
import { readFileSync } from "node:fs";

test("mobile public client uses exact callback PKCE API audience refresh and logout", async ({
  browser,
  request,
}) => {
  const account = JSON.parse(
    readFileSync(".local/accounts.json", "utf8").replace(/^\uFEFF/, ""),
  ).find((item: { username: string }) => item.username === "buyer.local");
  const verifier = randomBytes(32).toString("base64url");
  const state = randomBytes(24).toString("hex");
  const issuer = "http://localhost:8180/realms/vshop";
  const redirectUri = "vshop://auth/callback";
  const base = `${issuer}/protocol/openid-connect`;
  const params = new URLSearchParams({
    client_id: "vshop-mobile",
    redirect_uri: redirectUri,
    response_type: "code",
    scope: "openid profile",
    state,
    code_challenge: createHash("sha256").update(verifier).digest("base64url"),
    code_challenge_method: "S256",
  });
  const context = await browser.newContext();
  const page = await context.newPage();
  try {
    await page.goto(`${base}/auth?${params}`);
    await page.locator("#username").fill(account.username);
    await page.locator("#password").fill(account.password);
    const callback = page.waitForResponse(
      (response) =>
        response.status() === 302 && (response.headers().location ?? "").startsWith(redirectUri),
    );
    await page.locator("#kc-login").click();
    const location = new URL((await callback).headers().location!);
    expect(location.searchParams.get("state")).toBe(state);
    const exchange = await request.post(`${base}/token`, {
      form: {
        grant_type: "authorization_code",
        client_id: "vshop-mobile",
        redirect_uri: redirectUri,
        code: location.searchParams.get("code")!,
        code_verifier: verifier,
      },
    });
    expect(exchange.ok()).toBeTruthy();
    const tokens = await exchange.json();
    const profile = await request.get("http://localhost:8081/api/v1/me", {
      headers: { Authorization: `Bearer ${tokens.access_token}` },
    });
    expect(profile.ok()).toBeTruthy();
    expect((await profile.json()).username).toBe("buyer.local");
    const forbidden = await request.get(
      "http://localhost:8081/api/v1/admin/analytics/shop-activity",
      { headers: { Authorization: `Bearer ${tokens.access_token}` } },
    );
    expect(forbidden.status()).toBe(403);
    const refresh = await request.post(`${base}/token`, {
      form: {
        grant_type: "refresh_token",
        client_id: "vshop-mobile",
        refresh_token: tokens.refresh_token,
      },
    });
    expect(refresh.ok()).toBeTruthy();
    const refreshed = await refresh.json();
    const logout = await request.post(`${base}/logout`, {
      form: { client_id: "vshop-mobile", refresh_token: refreshed.refresh_token },
    });
    expect(logout.ok()).toBeTruthy();
    const afterLogout = await request.post(`${base}/token`, {
      form: {
        grant_type: "refresh_token",
        client_id: "vshop-mobile",
        refresh_token: refreshed.refresh_token,
      },
    });
    expect(afterLogout.status()).toBe(400);
    const invalidRedirect = await request.get(
      `${base}/auth?${new URLSearchParams({ ...Object.fromEntries(params), redirect_uri: "vshop://untrusted" })}`,
    );
    expect(invalidRedirect.status()).toBe(400);
    params.delete("code_challenge");
    params.delete("code_challenge_method");
    const missingPkce = await request.get(`${base}/auth?${params}`, { maxRedirects: 0 });
    expect(missingPkce.status()).toBe(302);
    expect(new URL(missingPkce.headers().location!).searchParams.get("error")).toBe(
      "invalid_request",
    );
  } finally {
    await context.close();
  }
});
