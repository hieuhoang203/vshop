import { test, expect, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { account, login } from "./support/auth";
import { createUser, deleteUser } from "./support/keycloak";
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

test("shop confirms, packs, ships and delivers a COD order that the buyer can follow", async ({
  browser,
  request,
}) => {
  const seller = await createUser(request, "e2e.fulfil", "BUYER");
  const adminContext = await browser.newContext();
  const sellerContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const buyerContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  try {
    const shopName = `E2E Fulfil ${Date.now()}`;
    const slug = `e2e-fulfil-${Date.now()}`;
    const sellerPage = await sellerContext.newPage();
    const admin = await adminContext.newPage();

    // Shop onboarding and approval through the real flows.
    await login(sellerPage, seller, "/seller");
    await sellerPage.getByLabel("Tên gian hàng", { exact: true }).fill(shopName);
    await sellerPage
      .getByLabel("Giới thiệu gian hàng", { exact: true })
      .fill("Gian hàng kiểm thử xử lý đơn tự động E2E VShop. Không phục vụ mua bán thật.");
    await sellerPage.getByRole("button", { name: "Gửi hồ sơ gian hàng", exact: true }).click();
    await expect(sellerPage.getByText("Đang xét duyệt", { exact: true })).toBeVisible();
    await login(admin, account("admin.local"), "/admin");
    const card = admin.locator(".review-card").filter({
      has: admin.getByRole("heading", { name: shopName, exact: true }),
    });
    await card.getByLabel("Lý do xử lý", { exact: true }).fill("Đã kiểm tra hồ sơ kiểm thử đơn.");
    await card.getByRole("button", { name: "Lưu quyết định" }).click();
    await expect(card).toHaveCount(0);

    // Policies (placeholders for local testing) so checkout is open.
    await admin.goto("/admin/policies");
    await approvePolicy(admin, "SHIPPING_FLAT", { amount: 30000 });
    await approvePolicy(admin, "RETURN", { windowDays: 7 });
    await approvePolicy(admin, "COD_LIMITS", { maxOrderAmount: 5000000, maxOpenOrders: 50 });
    await approvePolicy(admin, "PAYMENT_WINDOW", {
      paymentMinutes: 15,
      reservationBufferMinutes: 5,
    });

    // One sellable product with stock. Catalog and moderation flows have their own tests, so the
    // row is seeded directly; ownership of the shop comes from the real onboarding above.
    sql(`
      WITH s AS (SELECT id FROM merchant.shop WHERE name = '${shopName}'),
           c AS (SELECT id FROM catalog.category ORDER BY slug LIMIT 1),
           p AS (INSERT INTO catalog.product(id, shop_id, category_id, slug, name, description, image_key, status)
                 SELECT gen_random_uuid(), s.id, c.id, '${slug}', 'Sản phẩm E2E xử lý đơn',
                        'Sản phẩm kiểm thử xử lý đơn dành cho bài kiểm thử tự động.', 'lamp', 'ACTIVE'
                 FROM s, c RETURNING id, shop_id),
           k AS (INSERT INTO catalog.product_sku(id, product_id, shop_id, seller_sku, price)
                 SELECT id, id, shop_id, 'E2E-' || '${slug}', 120000 FROM p RETURNING id, shop_id),
           w AS (INSERT INTO inventory.warehouse(id, shop_id, code, name)
                 SELECT gen_random_uuid(), shop_id, 'W1', 'Kho' FROM k RETURNING id, shop_id)
      INSERT INTO inventory.stock_balance(sku_id, warehouse_id, shop_id, on_hand)
      SELECT k.id, w.id, k.shop_id, 5 FROM k, w`);

    // Buyer orders it.
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

    // The shop sees the order without the buyer's contact details until it confirms.
    await sellerPage.goto("/seller/orders");
    const order = sellerPage.getByTestId(`seller-order-${orderNumber}`);
    await expect(order).toContainText("Chờ người bán xác nhận");
    await expect(order).toContainText("chi tiết hiện sau khi xác nhận");
    await expect(order).not.toContainText("0912345678");
    await order.getByRole("button", { name: "Xác nhận đơn" }).click();
    await expect(order).toContainText("0912345678");
    await order.getByRole("button", { name: "Bắt đầu đóng gói" }).click();
    await order.getByRole("button", { name: "Sẵn sàng giao" }).click();
    await order.getByLabel("Mã vận đơn (nhập tay)").fill("VN-E2E-0001");
    await order.getByRole("button", { name: "Đã giao cho đơn vị vận chuyển" }).click();
    await expect(order).toContainText("Mã vận đơn (nhập tay): VN-E2E-0001");
    await expect(order).toContainText("chưa có xác nhận từ đơn vị vận chuyển");
    await order.getByRole("button", { name: "Xác nhận đã giao" }).click();
    await expect(order).toContainText("Đã giao");

    // The buyer sees the result and can no longer cancel.
    await buyer.goto("/orders");
    await buyer.getByRole("link", { name: orderNumber }).click();
    await expect(buyer.getByText("Đã giao").first()).toBeVisible();
    await expect(buyer.getByRole("button", { name: "Hủy đơn hàng" })).toHaveCount(0);
    await buyer.goto("/account/addresses");
    await clearAddresses(buyer);
  } finally {
    await adminContext.close();
    await sellerContext.close();
    await buyerContext.close();
    await deleteUser(request, seller.id);
  }
});
