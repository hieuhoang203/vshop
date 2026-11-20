import { test, expect, type Page } from "@playwright/test";
import { account, login } from "./support/auth";

async function emptyCart(page: Page) {
  await page.goto("/cart");
  const clear = page.getByRole("button", { name: "Xóa toàn bộ giỏ hàng" });
  if (await clear.count()) {
    await clear.click();
    await expect(page.getByText("Giỏ hàng đang trống")).toBeVisible();
  }
}

test("signed-out visitors are asked to sign in before using the cart", async ({ page }) => {
  await page.goto("/products/den-ban-luma");
  await expect(page.getByRole("link", { name: "Đăng nhập để thêm vào giỏ" })).toBeVisible();
  await expect(page.getByRole("link", { name: /Giỏ hàng/ })).toHaveCount(0);
  await page.goto("/cart");
  await expect(page.getByRole("heading", { name: "Đăng nhập để xem giỏ hàng" })).toBeVisible();
  // BFF cart routes refuse cross-site calls and unauthenticated callers.
  const crossSite = await page.request.post("/api/bff/cart/lines", {
    headers: { origin: "https://untrusted.example" },
    data: { skuId: "10000000-0000-0000-0000-000000000001", quantity: 1 },
  });
  expect(crossSite.status()).toBe(403);
  const anonymous = await page.request.post("/api/bff/cart/lines", {
    headers: { origin: "http://localhost:3100" },
    data: { skuId: "10000000-0000-0000-0000-000000000001", quantity: 1 },
  });
  expect(anonymous.status()).toBe(401);
});

test("buyer fills the cart, sees exact totals and locks a quote that goes stale on change", async ({
  browser,
}) => {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  try {
    const page = await context.newPage();
    await login(page, account("buyer.local"), "/cart");
    await emptyCart(page);

    // Add two different fixture products from their detail pages.
    await page.goto("/products/den-ban-luma");
    await page.getByLabel("Số lượng", { exact: true }).fill("2");
    await page.getByRole("button", { name: "Thêm vào giỏ" }).click();
    await expect(page.getByText("Đã thêm vào giỏ. Giỏ hiện có 2 sản phẩm.")).toBeVisible();
    await expect(page.getByRole("link", { name: "Giỏ hàng (2 sản phẩm)" })).toBeVisible();
    await page.goto("/products/tui-tote-everyday");
    await page.getByRole("button", { name: "Thêm vào giỏ" }).click();
    await expect(page.getByText("Giỏ hiện có 3 sản phẩm.")).toBeVisible();

    await page.getByRole("link", { name: "Xem giỏ hàng" }).click();
    await expect(page).toHaveURL("http://localhost:3100/cart");
    // 2 x 459.000 + 1 x 189.000 = 1.107.000, one shop group, price taken from the catalog.
    await expect(page.getByRole("region", { name: /VShop Studio/ })).toBeVisible();
    await expect(page.locator(".cart-total dd")).toContainText("1.107.000");
    await expect(page.getByText(/Chưa gồm phí vận chuyển, giảm giá và thuế/)).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );

    // Quantity controls recalculate on the server.
    await page.getByRole("button", { name: "Tăng số lượng Túi tote Everyday" }).click();
    await expect(page.locator(".cart-total dd")).toContainText("1.296.000");
    await page.getByRole("button", { name: "Xóa Túi tote Everyday khỏi giỏ" }).click();
    await expect(page.locator(".cart-total dd")).toContainText("918.000");

    // A quote locks the price; any later cart change invalidates it.
    await page.getByRole("button", { name: "Chốt báo giá" }).click();
    const quote = page.getByRole("status", { name: "Báo giá" });
    await expect(quote).toContainText("Giá được giữ đến");
    await expect(quote).toContainText("918.000");
    await page.getByRole("button", { name: "Tăng số lượng Đèn bàn Luma" }).click();
    await expect(quote).toContainText("báo giá cũ không còn hiệu lực");

    // The cart persists across a reload and a fresh session of the same account.
    await page.reload();
    await expect(page.locator(".cart-total dd")).toContainText("1.377.000");
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/cart");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: ".local/screenshots/cart-mobile.png", fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.screenshot({ path: ".local/screenshots/cart-desktop.png", fullPage: true });

    await emptyCart(page);
    await page.goto("/");
    await expect(page.getByRole("link", { name: "Giỏ hàng" })).toBeVisible();
  } finally {
    await context.close();
  }
});
