import { test, expect, type Page } from "@playwright/test";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { randomBytes } from "node:crypto";
type Account = { username: string; password: string; roles: string[] };
const accounts: Account[] = JSON.parse(
  readFileSync(".local/accounts.json", "utf8").replace(/^\uFEFF/, ""),
);
async function login(page: Page, account: Account, target = "/") {
  await page.goto(`/api/auth/login?returnTo=${encodeURIComponent(target)}`);
  await page.locator("#username").fill(account.username);
  await page.locator("#password").fill(account.password);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(`http://localhost:3100${target}`);
  await expect(page.getByText(account.username, { exact: true })).toBeVisible();
}
test("catalog search categories and detail use persisted data", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Khám phá sản phẩm");
  await expect(page.locator(".product-card")).toHaveCount(6);
  await page
    .getByRole("searchbox")
    .count()
    .then(async (count) => {
      if (count) await page.getByRole("searchbox").fill("Luma");
      else await page.getByLabel("Tìm sản phẩm").fill("Luma");
    });
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(page.locator(".product-card")).toHaveCount(1);
  await page.getByRole("link", { name: "Đèn bàn Luma", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Đèn bàn Luma");
  await expect(page.getByText("Chưa mở bán trực tuyến", { exact: true })).toBeVisible();
  await page.goto("/?category=nha-cua#san-pham");
  await expect(page.locator(".product-card")).toHaveCount(2);
  await page.goto("/?q=khong-co-san-pham-xyz");
  await expect(page.getByText("Chưa tìm thấy sản phẩm phù hợp", { exact: true })).toBeVisible();
});
test("buyer cannot access admin and session tokens stay server side", async ({ page, context }) => {
  await login(
    page,
    accounts.find((a) => a.username === "buyer.local")!,
    "/admin",
  );
  await expect(page.getByRole("heading", { name: "Bạn không có quyền truy cập" })).toBeVisible();
  const response = await page.request.post(
    "/api/bff/admin/shops/10000000-0000-0000-0000-000000000001/review",
    {
      headers: { origin: "http://localhost:3100" },
      data: { decision: "APPROVED", reason: "Unauthorized attempt" },
    },
  );
  expect(response.status()).toBe(403);
  expect((await page.request.get("/api/bff/admin/analytics?days=30")).status()).toBe(403);
  const session = (await context.cookies()).find((c) => c.name === "vshop_session");
  expect(session?.httpOnly).toBe(true);
  expect(session?.sameSite).toBe("Lax");
  expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([]);
  expect(await page.evaluate(() => document.cookie)).not.toContain("vshop_session");
  const csrf = await page.request.post("/api/bff/seller/shops", {
    headers: { origin: "https://untrusted.example" },
    data: {
      name: "CSRF test",
      description: "Must never reach the API endpoint",
    },
  });
  expect(csrf.status()).toBe(403);
  await page.getByRole("button", { name: /buyer\.local/ }).click();
  await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
  await expect(page.getByRole("link", { name: "Đăng nhập", exact: true })).toBeVisible();
  expect((await page.request.get("/api/bff/admin/analytics?days=30")).status()).toBe(401);
});
test("seller application admin rejection resubmission and approval persist", async ({
  browser,
  request,
}) => {
  const env = Object.fromEntries(
    readFileSync(".env", "utf8")
      .replace(/^\uFEFF/, "")
      .split(/\r?\n/)
      .filter(Boolean)
      .map((l) => {
        const i = l.indexOf("=");
        return [l.slice(0, i), l.slice(i + 1)];
      }),
  );
  const tokenResponse = await request.post(
    "http://localhost:8180/realms/master/protocol/openid-connect/token",
    {
      form: {
        grant_type: "password",
        client_id: "admin-cli",
        username: "vshop-local-admin",
        password: env.KEYCLOAK_ADMIN_PASSWORD,
      },
    },
  );
  expect(tokenResponse.ok()).toBeTruthy();
  const token = (await tokenResponse.json()).access_token;
  const headers = { Authorization: `Bearer ${token}` };
  const username = `e2e.seller.${Date.now()}`;
  const password = randomBytes(24).toString("hex");
  const created = await request.post("http://localhost:8180/admin/realms/vshop/users", {
    headers,
    data: {
      username,
      enabled: true,
      emailVerified: true,
      email: `${username}@vshop.local`,
      firstName: "E2E",
      lastName: "VShop",
      credentials: [{ type: "password", value: password, temporary: false }],
    },
  });
  expect(created.status()).toBe(201);
  const userId = created.headers().location.split("/").at(-1)!;
  const role = await request.get("http://localhost:8180/admin/realms/vshop/roles/BUYER", {
    headers,
  });
  const assigned = await request.post(
    `http://localhost:8180/admin/realms/vshop/users/${userId}/role-mappings/realm`,
    { headers, data: [await role.json()] },
  );
  expect(assigned.ok()).toBeTruthy();
  const sellerContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
  });
  const adminContext = await browser.newContext();
  try {
    const seller = await sellerContext.newPage(),
      admin = await adminContext.newPage();
    await login(seller, { username, password, roles: ["BUYER"] }, "/seller");
    const shopName = `E2E Shop ${Date.now()}`;
    await seller.getByLabel("Tên gian hàng", { exact: true }).fill(shopName);
    await seller
      .getByLabel("Giới thiệu gian hàng", { exact: true })
      .fill("Gian hàng kiểm thử tự động E2E VShop. Không phục vụ mua bán thật.");
    await seller.getByRole("button", { name: "Gửi hồ sơ gian hàng", exact: true }).click();
    await expect(seller.getByText("Đang xét duyệt", { exact: true })).toBeVisible();
    await seller.reload();
    await expect(seller.getByRole("heading", { name: shopName, exact: true })).toBeVisible();
    await login(
      admin,
      accounts.find((a) => a.username === "admin.local")!,
      "/admin",
    );
    let card = admin.locator(".review-card").filter({
      has: admin.getByRole("heading", { name: shopName, exact: true }),
    });
    await card.getByRole("combobox", { name: "Quyết định", exact: true }).click();
    await admin.getByRole("option", { name: "Yêu cầu bổ sung", exact: true }).click();
    await card
      .getByLabel("Lý do xử lý", { exact: true })
      .fill("Bổ sung danh mục sản phẩm dự kiến kinh doanh.");
    await card.getByRole("button", { name: "Lưu quyết định" }).click();
    await expect(card).toHaveCount(0);
    await seller.reload();
    await expect(seller.getByText("Cần bổ sung", { exact: true })).toBeVisible();
    await seller
      .getByLabel("Giới thiệu gian hàng", { exact: true })
      .fill(
        "Gian hàng kiểm thử tự động E2E VShop chuyên phụ kiện văn phòng. Không phục vụ mua bán thật.",
      );
    await seller.getByRole("button", { name: "Gửi lại để xét duyệt" }).click();
    await expect(seller.getByText("Đang xét duyệt", { exact: true })).toBeVisible();
    await admin.setViewportSize({ width: 390, height: 844 });
    await admin.reload();
    card = admin.locator(".review-card").filter({
      has: admin.getByRole("heading", { name: shopName, exact: true }),
    });
    expect(await admin.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await card
      .getByLabel("Lý do xử lý", { exact: true })
      .fill("Đã kiểm tra hồ sơ kiểm thử và danh mục dự kiến.");
    await card.getByRole("button", { name: "Lưu quyết định" }).click();
    await expect(card).toHaveCount(0);
    await seller.reload();
    await expect(seller.getByText("Đã duyệt", { exact: true })).toBeVisible();
    expect(await seller.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    mkdirSync(".local", { recursive: true });
    writeFileSync(
      ".local/e2e-last-run.json",
      JSON.stringify(
        {
          username,
          userId,
          shopName,
          expectedStatus: "APPROVED",
          at: new Date().toISOString(),
        },
        null,
        2,
      ),
    );
  } finally {
    await sellerContext.close();
    await adminContext.close();
  }
});
test("desktop and mobile layouts contain no horizontal overflow", async ({ page }) => {
  for (const width of [1440, 768, 390, 360]) {
    await page.setViewportSize({ width, height: 950 });
    for (const url of ["/", "/products/den-ban-luma", "/seller", "/admin"]) {
      await page.goto(url);
      await expect(page.locator("main")).toBeVisible();
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
        `${url} at ${width}px`,
      ).toBe(true);
    }
  }
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto("/");
  await page.screenshot({
    path: ".local/screenshots/storefront-desktop.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.screenshot({
    path: ".local/screenshots/storefront-mobile.png",
    fullPage: true,
  });
});
test("server rendered catalog works without JavaScript and invalid auth is rejected", async ({
  browser,
  request,
}) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  try {
    const page = await context.newPage();
    await page.goto("/");
    await expect(page.locator(".product-card")).toHaveCount(6);
    const art = page.locator(".product-art img");
    for (let i = 0; i < (await art.count()); i++)
      expect(
        await art.nth(i).evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0),
      ).toBe(true);
    await page.getByLabel("Tìm sản phẩm").fill("Mori");
    await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
    await expect(page.locator(".product-card")).toHaveCount(1);
    await page.goto("/api/auth/callback?code=forged&state=forged");
    await expect(page).toHaveURL("http://localhost:3100/auth-error");
    const invalid = await request.get("http://localhost:8081/api/v1/seller/shops", {
      headers: { Authorization: "Bearer forged.token.value" },
    });
    expect(invalid.status()).toBe(401);
  } finally {
    await context.close();
  }
});
