import { expect, type Page } from "@playwright/test";

/** Removes every address of the signed-in buyer through the UI, so tests start and end clean. */
export async function clearAddresses(page: Page) {
  await page.goto("/account/addresses");
  for (;;) {
    const buttons = page.getByRole("button", { name: /^Xóa địa chỉ / });
    const before = await buttons.count();
    if (!before) break;
    await buttons.first().click();
    await page.getByRole("button", { name: "Xóa địa chỉ", exact: true }).click();
    // Wait for the refreshed list before looking again, or the same card would be deleted twice.
    await expect(buttons).toHaveCount(before - 1);
  }
  await expect(page.getByText("Chưa có địa chỉ giao hàng")).toBeVisible();
}

export async function addAddress(
  page: Page,
  fields: { name: string; phone: string; street: string; ward: string; province: string },
  makeDefault = false,
) {
  await page.getByRole("button", { name: "Thêm địa chỉ" }).click();
  await page.getByLabel("Tên người nhận").fill(fields.name);
  await page.getByLabel("Số điện thoại").fill(fields.phone);
  await page.getByLabel("Số nhà, tên đường").fill(fields.street);
  await page.getByLabel("Phường/Xã").fill(fields.ward);
  await page.getByLabel("Tỉnh/Thành phố").fill(fields.province);
  if (makeDefault) await page.getByLabel("Đặt làm địa chỉ mặc định").check();
  await page.getByRole("button", { name: "Lưu địa chỉ" }).click();
}
