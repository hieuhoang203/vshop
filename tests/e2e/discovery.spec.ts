import { test, expect, type Page } from "@playwright/test";
import { account, login } from "./support/auth";
async function adminLogin(page: Page) {
  await login(page, account("admin.local"), "/admin");
}

test("saved products persist locally and Radix dialog supports removal", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Lưu Đèn bàn Luma", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Bỏ lưu Đèn bàn Luma", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Bỏ lưu Đèn bàn Luma", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Món đã lưu (1)", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText("Món hợp gu, giữ ở đây.");
  await expect(dialog.getByRole("link", { name: "Đèn bàn Luma", exact: true })).toBeVisible();
  await dialog.getByRole("button", { name: "Bỏ lưu Đèn bàn Luma", exact: true }).click();
  await expect(dialog).toContainText("Gu của bạn đang chờ được khám phá.");
  // Radix dismisses the newest layer (the removal toast) before the dialog.
  await page.keyboard.press("Escape");
  await expect(page.locator(".vshop-toast[data-state='open']")).toHaveCount(0);
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
});

test("initial storefront viewport prioritizes product images names and prices", async ({
  page,
}) => {
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 1366, height: 768 },
    { width: 390, height: 844 },
    { width: 360, height: 740 },
  ]) {
    await page.setViewportSize(viewport);
    await page.goto("/");
    await page.evaluate(() => document.fonts.ready);
    const visible = await page.locator(".product-card").evaluateAll((cards) =>
      cards.slice(0, 2).map((card) => {
        const image = card.querySelector(".product-art")!.getBoundingClientRect();
        const name = card.querySelector("h3")!.getBoundingClientRect();
        const price = card.querySelector(".product-price")!.getBoundingClientRect();
        return (
          image.top >= 0 &&
          image.bottom < innerHeight &&
          name.bottom < innerHeight &&
          price.bottom < innerHeight
        );
      }),
    );
    expect(
      visible,
      `Product image/name/price must be visible without scrolling at ${viewport.width}px`,
    ).toEqual([true, true]);
    expect(await page.evaluate(() => scrollY)).toBe(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    const catalog = await page.locator("#san-pham").boundingBox();
    const extras = await page.locator(".discovery-tools").boundingBox();
    expect(extras!.y).toBeGreaterThan(catalog!.y + catalog!.height - 1);
  }
});

test("quick view and surprise choose real products and keep navigation available", async ({
  page,
}) => {
  await page.goto("/");
  const trigger = page.getByRole("button", { name: "Xem nhanh Đèn bàn Luma", exact: true });
  await trigger.focus();
  await page.keyboard.press("Enter");
  let dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("heading", { name: "Đèn bàn Luma", exact: true })).toBeVisible();
  await expect(dialog.getByRole("link", { name: "Xem chi tiết", exact: true })).toHaveAttribute(
    "href",
    "/products/den-ban-luma",
  );
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await page.getByRole("button", { name: "Chọn giùm mình", exact: true }).click();
  dialog = page.getByRole("dialog");
  await expect(dialog).toContainText("Gợi ý ngẫu nhiên");
  await expect(dialog.getByRole("link", { name: "Xem chi tiết", exact: true })).toHaveAttribute(
    "href",
    /^\/products\//,
  );
  const first = await dialog
    .getByRole("link", { name: "Xem chi tiết", exact: true })
    .getAttribute("href");
  await dialog.getByRole("button", { name: "Thử món khác", exact: true }).click();
  await expect(dialog.getByRole("link", { name: "Xem chi tiết", exact: true })).not.toHaveAttribute(
    "href",
    first!,
  );
});

test("price filters sorting display density and keyboard search work", async ({ page }) => {
  await page.goto("/");
  // The page is server-rendered first; a click before hydration is lost, so retry until it opens.
  await expect(async () => {
    await page.getByRole("button", { name: "Lọc giá", exact: true }).click();
    await expect(page.getByRole("button", { name: "Đến 200k", exact: true })).toBeVisible({
      timeout: 2000,
    });
  }).toPass({ timeout: 30_000 });
  await page.getByRole("button", { name: "Đến 200k", exact: true }).click();
  await page.getByRole("button", { name: "Áp dụng", exact: true }).click();
  await expect(page).toHaveURL(/maxPrice=200000/);
  await expect(page.locator(".product-card")).toHaveCount(3);
  await page.getByRole("combobox", { name: "Sắp xếp sản phẩm", exact: true }).click();
  await page.getByRole("option", { name: "Giá thấp đến cao", exact: true }).click();
  await expect(page).toHaveURL(/sort=price-asc/);
  await expect(page.locator(".product-card").first()).toContainText("Sổ tay Grid");
  await page.getByRole("radio", { name: "Xem nhiều hơn", exact: true }).click();
  await expect(page.locator(".product-grid")).toHaveClass(/view-compact/);
  await page.keyboard.press("Control+k");
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Từ khóa tìm nhanh").fill("Mori");
  await dialog.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(page).toHaveURL(/q=Mori/);
  await expect(page.locator(".product-card")).toHaveCount(1);
});

test("mood selection changes discovery scene and respects reduced motion", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  await page.getByRole("radio", { name: "Sống chill", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Chậm lại một chút.", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".discovery-tool-art img")).toHaveAttribute("src", "/products/mug.svg");
  await expect(page.getByRole("link", { name: /Khám phá sống chill/ })).toHaveAttribute(
    "href",
    "/?category=nha-cua#san-pham",
  );
  expect(
    await page
      .locator(".product-card")
      .first()
      .evaluate((node) => getComputedStyle(node).animationName),
  ).toBe("none");
});

test("admin area chart uses real protected data and range controls", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await adminLogin(page);
  const panel = page.getByRole("region", { name: "Biểu đồ hoạt động gian hàng", exact: true });
  await expect(panel.locator(".recharts-surface")).toBeVisible();
  const current = await page.request.get("/api/bff/admin/analytics?days=30");
  expect(current.ok()).toBeTruthy();
  const data = await current.json();
  expect(data.points).toHaveLength(30);
  expect(data.timezone).toBe("Asia/Ho_Chi_Minh");
  const submitted = data.points.reduce(
    (sum: number, point: { submitted: number }) => sum + point.submitted,
    0,
  );
  await expect(panel.locator(".activity-stats > div").first().locator("strong")).toHaveText(
    String(submitted),
  );
  const response = page.waitForResponse((value) =>
    value.url().includes("/api/bff/admin/analytics?days=7"),
  );
  await panel.getByRole("radio", { name: "7 ngày", exact: true }).click();
  expect((await response).ok()).toBeTruthy();
  await expect(panel).toHaveAttribute("aria-busy", "false");
  await panel.locator("summary").click();
  await expect(panel.locator("tbody tr")).toHaveCount(7);
  await panel.getByRole("radio", { name: "Duyệt", exact: true }).click();
  const failureUrl = "**/api/bff/admin/analytics?days=90";
  await page.route(failureUrl, (route) =>
    route.fulfill({ status: 503, contentType: "application/json", body: "{}" }),
  );
  await panel.getByRole("radio", { name: "90 ngày", exact: true }).click();
  await expect(panel.getByText("Chưa thể tải dữ liệu hoạt động.", { exact: true })).toBeVisible();
  await expect(panel.locator(".recharts-surface")).toHaveCount(0);
  await expect(panel.locator(".activity-stats > div").first().locator("strong")).toHaveText("—");
  await page.unroute(failureUrl);
  const retry = page.waitForResponse((value) =>
    value.url().includes("/api/bff/admin/analytics?days=90"),
  );
  await panel.getByRole("button", { name: "Thử lại", exact: true }).click();
  expect((await retry).ok()).toBeTruthy();
  await expect(panel.locator(".recharts-surface")).toBeVisible();
  expect(errors).toEqual([]);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.screenshot({ path: ".local/screenshots/admin-genz-desktop.png", fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: ".local/screenshots/admin-genz-mobile.png", fullPage: true });
});
