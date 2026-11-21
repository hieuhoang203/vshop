import { test, expect, type Page } from "@playwright/test";
import { account, login, nextCode } from "./support/auth";
import { createUser, deleteUser } from "./support/keycloak";
import { readRecord, recordExists, updateRecord } from "./support/session";

const nowSeconds = () => Math.floor(Date.now() / 1000);

async function sessionId(page: Page): Promise<string> {
  const cookie = (await page.context().cookies()).find((c) => c.name === "vshop_session");
  expect(cookie, "session cookie").toBeTruthy();
  return cookie!.value;
}

test("the cookie is an opaque id and tokens are refreshed server-side", async ({ browser }) => {
  const context = await browser.newContext();
  try {
    const page = await context.newPage();
    await login(page, account("buyer.local"), "/cart");
    const cookie = (await context.cookies()).find((c) => c.name === "vshop_session")!;
    // Not a token: 43 random url-safe characters, HttpOnly, and tiny compared with the old 3.5 KB.
    expect(cookie.value).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(cookie.httpOnly).toBe(true);
    expect(cookie.sameSite).toBe("Lax");
    expect(await page.evaluate(() => document.cookie)).not.toContain("vshop_session");
    const id = cookie.value;
    const first = (await readRecord(id))!;
    expect(first.refreshToken.length).toBeGreaterThan(100);
    expect(first.mfa).toBe(false); // a buyer has no second factor

    // An expired access token is refreshed transparently; the cookie does not change.
    await updateRecord(id, { accessExp: nowSeconds() - 10 });
    await page.goto("/cart");
    await expect(page.getByRole("heading", { name: "Giỏ hàng của bạn" })).toBeVisible();
    const second = (await readRecord(id))!;
    expect(second.accessToken).not.toBe(first.accessToken);
    expect(second.accessExp).toBeGreaterThan(nowSeconds() + 60);
    expect(second.createdAt).toBe(first.createdAt);
    expect(await sessionId(page)).toBe(id);

    // Many requests noticing the expiry together all succeed and share one refresh.
    await updateRecord(id, { accessExp: nowSeconds() - 10 });
    const responses = await Promise.all(Array.from({ length: 6 }, () => page.request.get("/cart")));
    for (const response of responses) {
      expect(response.status()).toBe(200);
      expect(await response.text()).toContain("Giỏ hàng của bạn");
    }
    const third = (await readRecord(id))!;
    expect(third.accessToken).not.toBe(second.accessToken);

    // A refresh token Keycloak rejects ends the session instead of leaving a zombie.
    await updateRecord(id, { accessExp: nowSeconds() - 10, refreshToken: "not-a-real-token" });
    await page.goto("/cart");
    await expect(page.getByRole("heading", { name: "Đăng nhập để xem giỏ hàng" })).toBeVisible();
    expect(await recordExists(id)).toBe(false);
  } finally {
    await context.close();
  }
});

test("sessions end at logout and after the absolute lifetime", async ({ browser }) => {
  const context = await browser.newContext();
  const stolen = await browser.newContext();
  try {
    const page = await context.newPage();
    await login(page, account("buyer.local"), "/cart");
    const id = await sessionId(page);
    // Past the 8 hour cap the session is dropped even though Keycloak would still refresh it.
    await updateRecord(id, { createdAt: nowSeconds() - 9 * 3600 });
    await page.goto("/cart");
    await expect(page.getByRole("heading", { name: "Đăng nhập để xem giỏ hàng" })).toBeVisible();
    expect(await recordExists(id)).toBe(false);

    await login(page, account("buyer.local"), "/cart");
    const live = await sessionId(page);
    expect(live).not.toBe(id);
    await stolen.addCookies([
      { name: "vshop_session", value: live, domain: "localhost", path: "/" },
    ]);
    const other = await stolen.newPage();
    await other.goto("/cart");
    await expect(other.getByRole("heading", { name: "Giỏ hàng của bạn" })).toBeVisible();

    await page.request.post("/api/auth/logout", { headers: { origin: "http://localhost:3100" } });
    expect(await recordExists(live)).toBe(false);
    // The copied cookie is now worthless: logout is enforced on the server, not just in the browser.
    await other.goto("/cart");
    await expect(other.getByRole("heading", { name: "Đăng nhập để xem giỏ hàng" })).toBeVisible();
  } finally {
    await context.close();
    await stolen.close();
  }
});

test("admins must enrol and use a second factor before the admin area opens", async ({
  browser,
  request,
}) => {
  const admin = await createUser(request, "e2e.admin", "ADMIN");
  const context = await browser.newContext();
  try {
    const page = await context.newPage();
    await page.goto("/api/auth/login?returnTo=%2Fadmin");
    await page.locator("#username").fill(admin.username);
    await page.locator("#password").fill(admin.password);
    await page.locator("#kc-login").click();

    // An ADMIN without an authenticator is forced to enrol one: no way past this page.
    await expect(page.locator("#totp")).toBeVisible();
    await page.getByRole("link", { name: /unable to scan|không quét được/i }).click();
    const secret = (await page.locator("#kc-totp-secret-key").innerText()).replace(/\s/g, "");
    await page.locator("#totp").fill(await nextCode(admin.username, secret));
    await page.locator("#saveTOTPBtn").click();
    await expect(page).toHaveURL("http://localhost:3100/admin");

    // This session has a password but no amr=otp: the UI says so and the API refuses it.
    await expect(page.getByRole("heading", { name: "Cần xác thực hai bước" })).toBeVisible();
    expect((await readRecord(await sessionId(page)))!.mfa).toBe(false);
    expect((await page.request.get("/api/bff/admin/analytics?days=7")).status()).toBe(403);

    await page.getByRole("button", { name: "Đăng xuất để đăng nhập lại" }).click();
    await expect(page.getByRole("link", { name: "Đăng nhập", exact: true })).toBeVisible();

    // A wrong code is rejected and creates no session.
    await page.goto("/api/auth/login?returnTo=%2Fadmin");
    await page.locator("#username").fill(admin.username);
    await page.locator("#password").fill(admin.password);
    await page.locator("#kc-login").click();
    await page.locator("#otp").fill("000000");
    await page.locator("#kc-login").click();
    await expect(page.locator("#otp")).toBeVisible();
    await expect(page).not.toHaveURL(/localhost:3100\/admin/);
    expect(
      (await context.cookies()).find((c) => c.name === "vshop_session"),
      "no session after a wrong code",
    ).toBeUndefined();

    // The right code opens the admin area, and the API accepts the session.
    await login(page, admin, "/admin");
    await expect(page.getByRole("heading", { name: "Xét duyệt gian hàng" })).toBeVisible();
    expect((await readRecord(await sessionId(page)))!.mfa).toBe(true);
    expect((await page.request.get("/api/bff/admin/analytics?days=7")).status()).toBe(200);

    // The second factor survives a token refresh.
    const id = await sessionId(page);
    await updateRecord(id, { accessExp: nowSeconds() - 10 });
    await page.goto("/admin/products");
    await expect(page.getByRole("heading", { name: "Duyệt sản phẩm" })).toBeVisible();
    expect((await readRecord(id))!.mfa).toBe(true);
  } finally {
    await context.close();
    await deleteUser(request, admin.id);
  }
});
