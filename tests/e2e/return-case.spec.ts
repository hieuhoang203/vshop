import { test, expect, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { account, login } from "./support/auth";
import { createUser, deleteUser } from "./support/keycloak";
import { addAddress, clearAddresses } from "./support/addresses";

// Return case through the three role UIs. Like the other order specs it leaves approved policies
// (test values) in the local database, so re-apply the real ones afterwards.
const origin = { origin: "http://localhost:3100" };
async function approvePolicy(page: Page, policyType: string, content: Record<string, number>) {
  const draft = await page.request.post("/api/bff/admin/policies", {
    headers: origin,
    data: { policyType, content },
  });
  expect(draft.status()).toBe(201);
  const { id } = await draft.json();
  expect(
    (
      await page.request.post(`/api/bff/admin/policies/${id}/approve`, { headers: origin })
    ).status(),
  ).toBe(200);
}
function sql(statement: string) {
  execFileSync(
    "docker",
    [
      "compose",
      "exec",
      "-T",
      "postgres",
      "psql",
      "-U",
      "vshop",
      "-d",
      "vshop",
      "-v",
      "ON_ERROR_STOP=1",
      "-c",
      statement,
    ],
    { stdio: "pipe" },
  );
}

test("return case: buyer opens, shop offers, buyer declines, admin decides, refund appears", async ({
  browser,
  request,
}) => {
  const seller = await createUser(request, "e2e.return", "BUYER");
  const adminContext = await browser.newContext();
  const sellerContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const buyerContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  try {
    const shopName = `E2E Return ${Date.now()}`;
    const slug = `e2e-return-${Date.now()}`;
    const sellerPage = await sellerContext.newPage();
    const admin = await adminContext.newPage();
    await login(sellerPage, seller, "/seller");
    await sellerPage.getByLabel("Tên gian hàng", { exact: true }).fill(shopName);
    await sellerPage
      .getByLabel("Giới thiệu gian hàng", { exact: true })
      .fill("Gian hàng kiểm thử trả hàng tự động E2E VShop. Không phục vụ mua bán thật.");
    await sellerPage.getByRole("button", { name: "Gửi hồ sơ gian hàng", exact: true }).click();
    await expect(sellerPage.getByText("Đang xét duyệt", { exact: true })).toBeVisible();
    await login(admin, account("admin.local"), "/admin");
    const card = admin.locator(".review-card").filter({
      has: admin.getByRole("heading", { name: shopName, exact: true }),
    });
    await card
      .getByLabel("Lý do xử lý", { exact: true })
      .fill("Đã kiểm tra hồ sơ kiểm thử trả hàng.");
    await card.getByRole("button", { name: "Lưu quyết định" }).click();
    await expect(card).toHaveCount(0);

    await admin.goto("/admin/policies");
    await approvePolicy(admin, "SHIPPING_FLAT", { amount: 25000 });
    await approvePolicy(admin, "RETURN", { windowDays: 7 });
    await approvePolicy(admin, "COD_LIMITS", { maxOrderAmount: 3000000, maxOpenOrders: 50 });
    await approvePolicy(admin, "PAYMENT_WINDOW", {
      paymentMinutes: 15,
      reservationBufferMinutes: 5,
    });
    await approvePolicy(admin, "SELLER_CONFIRMATION", { confirmMinutes: 1440 });
    await approvePolicy(admin, "RETURN_HANDLING", { sellerResponseHours: 48 });

    sql(`
      WITH s AS (SELECT id FROM merchant.shop WHERE name = '${shopName}'),
           c AS (SELECT id FROM catalog.category ORDER BY slug LIMIT 1),
           p AS (INSERT INTO catalog.product(id, shop_id, category_id, slug, name, description, image_key, status)
                 SELECT gen_random_uuid(), s.id, c.id, '${slug}', 'Sản phẩm E2E trả hàng',
                        'Sản phẩm kiểm thử trả hàng dành cho bài kiểm thử tự động.', 'lamp', 'ACTIVE'
                 FROM s, c RETURNING id, shop_id),
           k AS (INSERT INTO catalog.product_sku(id, product_id, shop_id, seller_sku, price)
                 SELECT id, id, shop_id, 'E2E-' || '${slug}', 120000 FROM p RETURNING id, shop_id),
           w AS (INSERT INTO inventory.warehouse(id, shop_id, code, name)
                 SELECT gen_random_uuid(), shop_id, 'W1', 'Kho' FROM k RETURNING id, shop_id)
      INSERT INTO inventory.stock_balance(sku_id, warehouse_id, shop_id, on_hand)
      SELECT k.id, w.id, k.shop_id, 5 FROM k, w`);

    const buyer = await buyerContext.newPage();
    await login(buyer, account("buyer.local"), "/cart");
    await buyer.goto("/account/addresses");
    await clearAddresses(buyer);
    await addAddress(buyer, {
      name: "Nguyễn Văn A",
      phone: "0912345678",
      street: "Số 12 ngõ 34 Nguyễn Trãi",
      ward: "Phường Láng",
      province: "Hà Nội",
    });
    await expect(buyer.getByText("Phường Láng")).toBeVisible();
    await buyer.goto("/cart");
    const clear = buyer.getByRole("button", { name: "Xóa toàn bộ giỏ hàng" });
    if (await clear.count()) await clear.click();
    await buyer.goto(`/products/${slug}`);
    await buyer.getByRole("button", { name: "Thêm vào giỏ" }).click();
    await expect(buyer.getByText("Đã thêm vào giỏ.")).toBeVisible();
    await buyer.goto("/cart");
    await buyer.getByRole("button", { name: "Chốt báo giá" }).click();
    await buyer.getByRole("button", { name: "Đặt hàng (COD)" }).click();
    await expect(buyer).toHaveURL(/\/checkout\/result\//);
    const orderNumber = (
      await buyer
        .getByRole("link", { name: /^VS\d{6}-/ })
        .first()
        .innerText()
    ).trim();

    // The shop is told about the new order (the worker turns the event into a notification).
    await expect
      .poll(
        async () => {
          await sellerPage.goto("/notifications");
          return sellerPage.getByText(`Đơn mới ${orderNumber}`).count();
        },
        { timeout: 30_000 },
      )
      .toBeGreaterThan(0);
    await expect(sellerPage.getByRole("link", { name: /Thông báo, \d+ chưa đọc/ })).toBeVisible();
    await sellerPage.getByRole("button", { name: "Đánh dấu đã đọc tất cả" }).click();
    await expect(sellerPage.getByRole("link", { name: "Thông báo", exact: true })).toBeVisible();

    // The shop delivers it by hand.
    await sellerPage.goto("/seller/orders");
    const order = sellerPage.getByTestId(`seller-order-${orderNumber}`);
    await order.getByRole("button", { name: "Xác nhận đơn" }).click();
    await order.getByRole("button", { name: "Bắt đầu đóng gói" }).click();
    await order.getByRole("button", { name: "Sẵn sàng giao" }).click();
    await order.getByLabel("Mã vận đơn (nhập tay)").fill("VN-RET-0001");
    await order.getByRole("button", { name: "Đã giao cho đơn vị vận chuyển" }).click();
    await order.getByRole("button", { name: "Xác nhận đã giao" }).click();
    await expect(order).toContainText("Đã giao");

    // The buyer is told it was delivered.
    await expect
      .poll(
        async () => {
          await buyer.goto("/notifications");
          return buyer.getByText(`Đơn ${orderNumber} đã giao`).count();
        },
        { timeout: 30_000 },
      )
      .toBeGreaterThan(0);
    await buyer.screenshot({ path: ".local/screenshots/notifications-buyer.png", fullPage: true });

    // Buyer asks for a refund (damaged goods, keeps them).
    await buyer.goto("/orders");
    await buyer.getByRole("link", { name: orderNumber }).click();
    const form = buyer.getByRole("form", { name: "Yêu cầu trả hàng" });
    await form.getByLabel("Lý do").selectOption("DAMAGED");
    await form.getByLabel("Mô tả vấn đề").fill("Sản phẩm bị nứt khi nhận hàng");
    await form.getByRole("button", { name: "Gửi yêu cầu" }).click();
    const panel = buyer.getByTestId("return-panel");
    await expect(panel).toContainText("Chờ người bán phản hồi");
    await expect(panel).toContainText("145.000"); // 120.000 + 25.000 shipping

    // The shop offers a partial refund; the buyer finds it too low and asks the administrator.
    await sellerPage.goto("/seller/returns");
    const sc = sellerPage.getByTestId(`return-${orderNumber}`);
    await sc.getByLabel("Số tiền hoàn đề xuất (VND)").fill("50000");
    await sc.getByRole("button", { name: "Đề xuất hoàn tiền, không cần trả hàng" }).click();
    await expect(sc).toContainText("Người bán đã đề xuất hoàn tiền");
    await buyer.reload();
    await buyer.getByRole("button", { name: "Không đồng ý, nhờ quản trị viên xem xét" }).click();
    await expect(buyer.getByTestId("return-panel")).toContainText(
      "Đang chờ quản trị viên quyết định",
    );

    // The administrator settles it at 100.000.
    await admin.goto("/admin/returns");
    const ac = admin.getByTestId(`return-${orderNumber}`);
    await ac.getByLabel("Số tiền hoàn (VND)").fill("100000");
    await ac.getByLabel("Lý do quyết định").fill("Hoàn một phần theo ảnh hư hỏng");
    await ac.getByRole("button", { name: "Ghi nhận quyết định" }).click();
    await expect(admin.getByTestId(`return-${orderNumber}`)).toHaveCount(0);

    // A refund of 100.000 now waits for the buyer's account (cash order: no online charge).
    await buyer.reload();
    await expect(buyer.getByTestId("return-panel")).toContainText("Đã duyệt hoàn tiền");
    await expect(buyer.getByTestId("refund-panel")).toContainText("Hoàn tiền 100.000");
    await buyer.screenshot({ path: ".local/screenshots/return-buyer.png", fullPage: true });

    await buyer.goto("/account/addresses");
    await clearAddresses(buyer);
  } finally {
    await adminContext.close();
    await sellerContext.close();
    await buyerContext.close();
    await deleteUser(request, seller.id);
  }
});
