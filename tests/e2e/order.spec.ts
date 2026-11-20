import { test, expect, type Page } from "@playwright/test";
import { account, login } from "./support/auth";
import { addAddress, clearAddresses } from "./support/addresses";

const origin = { origin: "http://localhost:3100" };

async function approvePolicy(page: Page, policyType: string, content: Record<string, number>) {
  const draft = await page.request.post("/api/bff/admin/policies", {
    headers: origin,
    data: { policyType, content },
  });
  expect(draft.status()).toBe(201);
  const { id } = await draft.json();
  const approved = await page.request.post(`/api/bff/admin/policies/${id}/approve`, {
    headers: origin,
  });
  expect(approved.status()).toBe(200);
}

test("admin approves policies with OTP, then a buyer places a COD order", async ({ browser }) => {
  const adminContext = await browser.newContext();
  const buyerContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  try {
    // 1. Admin (real TOTP) approves the four policies. Draft numbers are placeholders for the
    //    owner to replace; the page says drafts are not used until approved.
    const admin = await adminContext.newPage();
    await login(admin, account("admin.local"), "/admin");
    await admin.goto("/admin/policies");
    await expect(admin.getByRole("heading", { name: "Chính sách đặt hàng" })).toBeVisible();
    await approvePolicy(admin, "SHIPPING_FLAT", { amount: 30000 });
    await approvePolicy(admin, "RETURN", { windowDays: 7 });
    await approvePolicy(admin, "COD_LIMITS", { maxOrderAmount: 5000000, maxOpenOrders: 50 });
    await approvePolicy(admin, "PAYMENT_WINDOW", {
      paymentMinutes: 15,
      reservationBufferMinutes: 5,
    });
    await admin.goto("/admin/policies");
    await expect(admin.getByTestId("policy-SHIPPING_FLAT").first()).toContainText("Đang áp dụng");

    // 2. Buyer: cart, address, quote with shipping, order.
    const page = await buyerContext.newPage();
    await login(page, account("buyer.local"), "/cart");
    await page.goto("/account/addresses");
    await clearAddresses(page);
    await addAddress(page, {
      name: "Nguyễn Văn A",
      phone: "0912345678",
      street: "Số 12 ngõ 34 Nguyễn Trãi",
      ward: "Phường Láng",
      province: "Hà Nội",
    });
    await expect(page.getByText("Phường Láng")).toBeVisible();
    await page.goto("/cart");
    const clear = page.getByRole("button", { name: "Xóa toàn bộ giỏ hàng" });
    if (await clear.count()) await clear.click();
    await page.goto("/products/den-ban-luma");
    await page.getByRole("button", { name: "Thêm vào giỏ" }).click();
    await expect(page.getByText("Đã thêm vào giỏ.")).toBeVisible();
    await page.goto("/cart");
    await page.getByRole("button", { name: "Chốt báo giá" }).click();
    const quote = page.getByRole("status", { name: "Báo giá" });
    await expect(quote).toContainText("Phí vận chuyển: 30.000");
    await expect(quote).toContainText("Tổng phải trả: 489.000");
    await page.getByRole("button", { name: "Đặt hàng (COD)" }).click();

    await expect(page).toHaveURL(/\/checkout\/result\//);
    await expect(page.getByRole("heading", { name: /Đã tạo 1 đơn hàng/ })).toBeVisible();
    await expect(page.getByText("Chờ người bán xác nhận")).toBeVisible();
    await expect(page.getByText("0912345678")).toBeVisible();
    // The cart is empty afterwards and the order is listed and readable.
    await page.goto("/cart");
    await expect(page.getByText("Giỏ hàng đang trống")).toBeVisible();
    await page.goto("/orders");
    await expect(page.getByRole("link", { name: /^VS\d{6}-/ }).first()).toBeVisible();
    await page
      .getByRole("link", { name: /^VS\d{6}-/ })
      .first()
      .click();
    await expect(page.getByRole("heading", { name: "Lịch sử" })).toBeVisible();
    await page.goto("/account/addresses");
    await clearAddresses(page);
  } finally {
    await adminContext.close();
    await buyerContext.close();
  }
});
