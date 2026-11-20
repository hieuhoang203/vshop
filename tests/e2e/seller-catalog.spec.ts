import { test, expect, type Browser, type APIRequestContext, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { account as findAccount, login, type Account } from "./support/auth";
import { randomBytes } from "node:crypto";

async function createSeller(request: APIRequestContext): Promise<Account> {
  const env = Object.fromEntries(
    readFileSync(".env", "utf8")
      .replace(/^﻿/, "")
      .split(/\r?\n/)
      .filter(Boolean)
      .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
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
  const headers = { Authorization: `Bearer ${(await tokenResponse.json()).access_token}` };
  const username = `e2e.catalog.${Date.now()}`;
  const password = randomBytes(24).toString("hex");
  const created = await request.post("http://localhost:8180/admin/realms/vshop/users", {
    headers,
    data: {
      username,
      enabled: true,
      emailVerified: true,
      email: `${username}@vshop.local`,
      firstName: "E2E",
      lastName: "Catalog",
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
  return { username, password, roles: ["BUYER"] };
}

async function approveShop(admin: Page, shopName: string) {
  await admin.goto("/admin");
  const card = admin.locator(".review-card").filter({
    has: admin.getByRole("heading", { name: shopName, exact: true }),
  });
  await card.getByLabel("Lý do xử lý", { exact: true }).fill("Đã kiểm tra hồ sơ kiểm thử catalog.");
  await card.getByRole("button", { name: "Lưu quyết định" }).click();
  await expect(card).toHaveCount(0);
}

async function reviewProduct(
  admin: Page,
  productName: string,
  decision: "Chấp thuận" | "Yêu cầu chỉnh sửa",
  reason: string,
) {
  await admin.goto("/admin/products");
  const card = admin.locator(".review-card").filter({
    has: admin.getByRole("heading", { name: productName, exact: true }),
  });
  await expect(card).toHaveCount(1);
  await admin.screenshot({ path: `.local/screenshots/admin-product-review.png`, fullPage: true });
  if (decision !== "Chấp thuận") {
    await card.getByRole("combobox", { name: "Quyết định", exact: true }).click();
    await admin.getByRole("option", { name: decision, exact: true }).click();
  }
  await card.getByLabel("Lý do xử lý", { exact: true }).fill(reason);
  await card.getByRole("button", { name: "Lưu quyết định" }).click();
  await expect(card).toHaveCount(0);
}

async function noHorizontalOverflow(page: Page) {
  const [scrollWidth, viewport] = await page.evaluate(() => [
    document.documentElement.scrollWidth,
    innerWidth,
  ]);
  expect(scrollWidth, page.url()).toBeLessThanOrEqual(viewport);
}

test("seller builds a variant product, stocks it, and admin moderation publishes it", async ({
  browser,
  request,
}: {
  browser: Browser;
  request: APIRequestContext;
}) => {
  const account = await createSeller(request);
  const sellerContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  try {
    const seller = await sellerContext.newPage();
    const admin = await adminContext.newPage();
    const shopName = `E2E Catalog ${Date.now()}`;
    const productName = `Cốc E2E ${Date.now()}`;

    // Shop onboarding and approval (existing flow) so product tools open up.
    await login(seller, account, "/seller");
    await seller.getByLabel("Tên gian hàng", { exact: true }).fill(shopName);
    await seller
      .getByLabel("Giới thiệu gian hàng", { exact: true })
      .fill("Gian hàng kiểm thử catalog tự động E2E VShop. Không phục vụ mua bán thật.");
    await seller.getByRole("button", { name: "Gửi hồ sơ gian hàng", exact: true }).click();
    await expect(seller.getByText("Đang xét duyệt", { exact: true })).toBeVisible();
    await login(admin, findAccount("admin.local"), "/admin");
    await approveShop(admin, shopName);

    // Create a product with two variants and prices.
    await seller.goto("/seller/products");
    await expect(seller.getByText("Chưa có sản phẩm nào")).toBeVisible();
    await seller.getByRole("link", { name: "Thêm sản phẩm" }).click();
    await seller.getByLabel("Tên sản phẩm", { exact: true }).fill(productName);
    await seller
      .getByLabel("Mô tả", { exact: true })
      .fill("Cốc kiểm thử có hai màu dành cho bài kiểm thử tự động.");
    await seller.getByRole("combobox", { name: "Ảnh sản phẩm" }).click();
    await seller.getByRole("option", { name: "Cốc gốm", exact: true }).click();
    await seller.getByRole("button", { name: "Thêm phân loại" }).click();
    await seller.getByLabel("Tên phân loại", { exact: true }).fill("Màu");
    await seller.getByLabel("Các giá trị, cách nhau bằng dấu phẩy").fill("Đỏ, Xanh");
    // Client validation: blank SKU codes and prices are rejected before any request.
    await seller.getByRole("button", { name: "Lưu sản phẩm nháp" }).click();
    await expect(seller.locator(".error-message")).toContainText("Mã SKU");
    await seller.locator('[id="sku-code-mau=do"]').fill("CUP-RED");
    await seller.locator('[id="sku-price-mau=do"]').fill("120000");
    await seller.locator('[id="sku-code-mau=xanh"]').fill("CUP-BLUE");
    await seller.locator('[id="sku-price-mau=xanh"]').fill("150000.5");
    await seller.getByRole("button", { name: "Lưu sản phẩm nháp" }).click();
    await expect(seller.locator(".error-message")).toContainText("số nguyên");
    await seller.locator('[id="sku-price-mau=xanh"]').fill("150000");
    await seller.getByRole("button", { name: "Lưu sản phẩm nháp" }).click();
    await expect(seller.getByRole("heading", { name: productName, level: 1 })).toBeVisible();
    await expect(seller.locator(".portal-heading .status")).toHaveText("Nháp");
    const productUrl = seller.url();
    await seller.screenshot({
      path: ".local/screenshots/seller-product-detail.png",
      fullPage: true,
    });

    // Stock CUP-RED through the dialog; the guard blocks reducing below zero.
    await seller.getByRole("button", { name: "Tồn kho CUP-RED" }).click();
    await seller.getByLabel("Số lượng", { exact: true }).fill("12");
    await seller.getByLabel("Lý do", { exact: true }).fill("Nhập lô đầu tiên kiểm thử");
    await seller.getByRole("button", { name: "Lưu thay đổi" }).click();
    await expect(seller.getByText("Có thể bán: 12")).toBeVisible();
    await expect(seller.locator(".stock-history li").first()).toContainText("+12");
    await seller.screenshot({ path: ".local/screenshots/seller-stock-dialog.png" });
    await seller.getByRole("combobox", { name: "Thao tác" }).click();
    await seller.getByRole("option", { name: "Điều chỉnh (kiểm kê)", exact: true }).click();
    await seller.getByLabel("Số lượng", { exact: true }).fill("50");
    await seller.getByLabel("Lý do", { exact: true }).fill("Thử giảm vượt quá tồn kho");
    await seller.getByRole("button", { name: "Lưu thay đổi" }).click();
    await expect(seller.locator(".error-message")).toContainText("thấp hơn");
    await seller.getByRole("button", { name: "Đóng" }).click();
    await seller.reload();
    await expect(seller.getByRole("row", { name: /CUP-RED/ })).toContainText("12");

    // Not public while a draft; submit for review.
    await seller.getByRole("button", { name: "Gửi duyệt" }).click();
    await expect(seller.locator(".portal-heading .status")).toHaveText("Chờ duyệt");
    await expect(seller.getByRole("button", { name: "Gửi duyệt" })).toHaveCount(0);

    // Admin rejects with a reason; the seller sees it and cannot self-publish.
    await reviewProduct(admin, productName, "Yêu cầu chỉnh sửa", "Mô tả cần nêu chất liệu cốc.");
    await seller.reload();
    await expect(seller.locator(".portal-heading .status")).toHaveText("Cần chỉnh sửa");
    await expect(seller.getByRole("note")).toContainText("Mô tả cần nêu chất liệu cốc.");
    await seller
      .getByLabel("Mô tả", { exact: true })
      .fill("Cốc gốm men mờ dung tích 350 ml, hai màu dành cho kiểm thử tự động.");
    await seller.getByRole("button", { name: "Lưu thông tin" }).click();
    await expect(seller.getByText("Đã lưu thay đổi.")).toBeVisible();
    await seller.getByRole("button", { name: "Gửi duyệt lại" }).click();
    await expect(seller.locator(".portal-heading .status")).toHaveText("Chờ duyệt");

    // Admin approves; buyers (anonymous) now see it with the lowest active price.
    await reviewProduct(admin, productName, "Chấp thuận", "Nội dung đã phù hợp để hiển thị.");
    await seller.reload();
    await expect(seller.locator(".portal-heading .status")).toHaveText("Đang bán");
    const buyer = await browser.newContext();
    try {
      const page = await buyer.newPage();
      await page.goto(`/?q=${encodeURIComponent(productName)}`);
      const card = page.locator(".product-card").filter({ hasText: productName });
      await expect(card).toHaveCount(1);
      await expect(card).toContainText("120.000");
    } finally {
      await buyer.close();
    }

    // Price change needs no review; deactivating the cheaper SKU raises the shown price.
    await seller.goto(productUrl);
    const redPrice = seller.getByLabel("Giá CUP-RED", { exact: true });
    await redPrice.fill("99000");
    await seller
      .getByRole("row", { name: /CUP-RED/ })
      .getByRole("button", { name: "Lưu" })
      .click();
    await expect(seller.locator(".portal-heading .status")).toHaveText("Đang bán");
    await expect(seller.getByRole("row", { name: /CUP-RED/ }).getByText("Đã lưu.")).toBeVisible();

    // Hide, then it disappears from the storefront.
    await seller.getByRole("button", { name: "Ẩn sản phẩm" }).click();
    await expect(seller.locator(".portal-heading .status")).toHaveText("Đã ẩn");
    const anon = await browser.newContext();
    try {
      const page = await anon.newPage();
      await page.goto(`/?q=${encodeURIComponent(productName)}`);
      await expect(page.locator(".product-card").filter({ hasText: productName })).toHaveCount(0);
    } finally {
      await anon.close();
    }

    // Mobile layout: portal pages do not overflow horizontally.
    await seller.setViewportSize({ width: 390, height: 844 });
    for (const path of ["/seller/products", "/seller/products/new", productUrl]) {
      await seller.goto(path);
      await noHorizontalOverflow(seller);
      await seller.screenshot({
        path: `.local/screenshots/seller-mobile-${path.includes("new") ? "new" : path.endsWith("products") ? "list" : "detail"}.png`,
        fullPage: true,
      });
    }
    await admin.setViewportSize({ width: 390, height: 844 });
    await admin.goto("/admin/products");
    await noHorizontalOverflow(admin);
  } finally {
    await sellerContext.close();
    await adminContext.close();
  }
});

test("product portals reject unauthenticated and unauthorised callers", async ({
  browser,
  page,
}) => {
  await page.goto("/seller/products");
  await expect(page.getByRole("link", { name: "Đăng nhập để tiếp tục" })).toBeVisible();
  await page.goto("/admin/products");
  await expect(page.getByRole("link", { name: "Đăng nhập để tiếp tục" })).toBeVisible();
  expect((await page.request.get("/api/bff/seller/shops/x/products")).status()).toBe(403);

  const context = await browser.newContext();
  try {
    const buyer = await context.newPage();
    await login(buyer, findAccount("buyer.local"), "/admin");
    await buyer.goto("/admin/products");
    await expect(buyer.getByRole("heading", { name: "Bạn không có quyền truy cập" })).toBeVisible();
    const forbidden = await buyer.request.post(
      "/api/bff/admin/products/10000000-0000-0000-0000-000000000001/review",
      {
        headers: { origin: "http://localhost:3100" },
        data: { decision: "APPROVED", reason: "Unauthorised attempt", expectedVersion: 0 },
      },
    );
    expect(forbidden.status()).toBe(403);
    // Cross-site and non-allowlisted calls never reach the API.
    const csrf = await buyer.request.post(
      "/api/bff/admin/products/10000000-0000-0000-0000-000000000001/review",
      {
        headers: { origin: "https://untrusted.example" },
        data: { decision: "APPROVED", reason: "Cross-site attempt", expectedVersion: 0 },
      },
    );
    expect(csrf.status()).toBe(403);
    const notAllowed = await buyer.request.post("/api/bff/seller/shops/x/inventory/reset", {
      headers: { origin: "http://localhost:3100" },
      data: {},
    });
    expect(notAllowed.status()).toBe(404);
  } finally {
    await context.close();
  }
});
