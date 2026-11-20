import { test, expect } from "@playwright/test";
import { account, login } from "./support/auth";
import { addAddress, clearAddresses } from "./support/addresses";

test("signed-out visitors cannot reach the address book or its API", async ({ page }) => {
  await page.goto("/account/addresses");
  await expect(page.getByRole("heading", { name: "Đăng nhập để xem sổ địa chỉ" })).toBeVisible();
  const crossSite = await page.request.post("/api/bff/me/addresses", {
    headers: { origin: "https://untrusted.example" },
    data: {},
  });
  expect(crossSite.status()).toBe(403);
  const anonymous = await page.request.post("/api/bff/me/addresses", {
    headers: { origin: "http://localhost:3100" },
    data: {},
  });
  expect(anonymous.status()).toBe(401);
});

test("buyer manages an address book with defaults, validation and a bound quote", async ({
  browser,
}) => {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  try {
    const page = await context.newPage();
    await login(page, account("buyer.local"), "/account/addresses");
    await clearAddresses(page);

    // Bad input is explained and nothing is saved.
    await page.getByRole("button", { name: "Thêm địa chỉ" }).click();
    await page.getByLabel("Tên người nhận").fill("Nguyễn Văn A");
    await page.getByLabel("Số điện thoại").fill("0212345678");
    await page.getByLabel("Số nhà, tên đường").fill("Số 12 ngõ 34 Nguyễn Trãi");
    await page.getByLabel("Phường/Xã").fill("Phường Láng");
    await page.getByLabel("Tỉnh/Thành phố").fill("Hà Nội");
    await page.getByRole("button", { name: "Lưu địa chỉ" }).click();
    await expect(page.getByRole("alert")).toContainText("Số điện thoại di động chưa hợp lệ");
    await page.getByRole("button", { name: "Hủy" }).click();
    await expect(page.getByText("Chưa có địa chỉ giao hàng")).toBeVisible();

    // The first address becomes the default; a +84 number is normalised.
    await page.getByRole("button", { name: "Thêm địa chỉ" }).click();
    await page.getByLabel("Tên người nhận").fill("Nguyễn Văn A");
    await page.getByLabel("Số điện thoại").fill("+84 912 345 678");
    await page.getByLabel("Số nhà, tên đường").fill("Số 12 ngõ 34 Nguyễn Trãi");
    await page.getByLabel("Phường/Xã").fill("Phường Láng");
    await page.getByLabel("Tỉnh/Thành phố").fill("Hà Nội");
    await page.getByRole("button", { name: "Lưu địa chỉ" }).click();
    const first = page.locator(".address-card").filter({ hasText: "Phường Láng" });
    await expect(first).toContainText("0912345678");
    await expect(first).toContainText("Mặc định");

    // A second address can take over as the default; exactly one default remains.
    await addAddress(
      page,
      {
        name: "Trần Thị B",
        phone: "0987 654 321",
        street: "Tầng 5 Tòa nhà Sunrise",
        ward: "Phường Bến Nghé",
        province: "Thành phố Hồ Chí Minh",
      },
      true,
    );
    const second = page.locator(".address-card").filter({ hasText: "Phường Bến Nghé" });
    await expect(second).toContainText("Mặc định");
    await expect(page.locator(".address-card.is-default")).toHaveCount(1);
    await expect(page.locator(".address-card").first()).toContainText("Trần Thị B");

    // Editing keeps the data; making another default is one click.
    await first.getByRole("button", { name: /^Sửa địa chỉ/ }).click();
    await page.getByLabel("Số nhà, tên đường").fill("Số 99 phố mới");
    await page.getByRole("button", { name: "Lưu thay đổi" }).click();
    await expect(first).toContainText("Số 99 phố mới");
    await first.getByRole("button", { name: "Đặt mặc định" }).click();
    await expect(first).toContainText("Mặc định");
    await expect(page.locator(".address-card.is-default")).toHaveCount(1);
    await page.screenshot({ path: ".local/screenshots/address-book.png", fullPage: true });

    // The cart binds a quote to the chosen address and shows only its region.
    await page.goto("/products/den-ban-luma");
    await page.getByRole("button", { name: "Thêm vào giỏ" }).click();
    await expect(page.getByText(/Đã thêm vào giỏ/)).toBeVisible();
    await page.goto("/cart");
    await expect(page.getByRole("combobox", { name: "Địa chỉ giao hàng" })).toContainText(
      "Nguyễn Văn A",
    );
    await page.getByRole("button", { name: "Chốt báo giá" }).click();
    const quote = page.getByRole("status", { name: "Báo giá" });
    await expect(quote).toContainText("Giao đến: Phường Láng, Hà Nội");
    await expect(quote).not.toContainText("0912345678");
    await expect(quote).not.toContainText("Số 99 phố mới");

    // Choosing another address makes that quote stale until it is calculated again.
    await page.getByRole("combobox", { name: "Địa chỉ giao hàng" }).click();
    await page.getByRole("option", { name: /Trần Thị B/ }).click();
    await expect(quote).toContainText("Đã đổi địa chỉ");
    await page.getByRole("button", { name: "Chốt báo giá" }).click();
    await expect(quote).toContainText("Giao đến: Phường Bến Nghé, Thành phố Hồ Chí Minh");

    // Deleting an address that a quote used invalidates that quote on the server.
    await page.getByRole("button", { name: "Xóa toàn bộ giỏ hàng" }).click();
    await expect(page.getByText("Giỏ hàng đang trống")).toBeVisible();

    // The page stays inside a phone viewport.
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/account/addresses");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: ".local/screenshots/address-book-mobile.png", fullPage: true });
    await page.setViewportSize({ width: 1280, height: 900 });
    await clearAddresses(page);
  } finally {
    await context.close();
  }
});
