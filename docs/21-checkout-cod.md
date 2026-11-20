# Đặt đơn COD và cổng chính sách (S0 + S1)

Ngày: **20/11/2026**. Phạm vi: triển khai lát cắt S0 và S1 của [docs/20](20-thiet-ke-checkout.md). Chưa có thanh toán trực tuyến, vận chuyển, voucher hay xử lý đơn của seller (S2–S4).

## 1. Đã làm

**S0: nền**

- `V1006`: bảng `platform.policy_version` có trạng thái `DRAFT/APPROVED/RETIRED`, người tạo, người duyệt, thời điểm duyệt; chỉ một bản `APPROVED` cho mỗi loại (chỉ mục duy nhất một phần). Thêm `platform.outbox_event.aggregate_type`.
- Bốn loại chính sách, nội dung là số nguyên: `SHIPPING_FLAT {amount}`, `RETURN {windowDays}`, `COD_LIMITS {maxOrderAmount, maxOpenOrders}`, `PAYMENT_WINDOW {paymentMinutes, reservationBufferMinutes}`. Có khoảng giá trị hợp lệ cho từng trường.
- API quản trị `GET/POST /api/v1/admin/policies`, `POST /{id}/approve` (ADMIN + OTP, ghi audit). Duyệt bản mới làm bản cũ thành `RETIRED`. **Bản nháp không bao giờ được dùng.** Không có số nào được nạp sẵn: chủ sản phẩm phải tạo và duyệt các con số D4, D5, D7.
- `OutboxService` tổng quát; `ReservationExpiryWorker` gọi `expireDue` (bật cùng `vshop.worker.enabled`).
- Phí ship vào bộ tính giá: giỏ và báo giá có `shippingFee`, `payable` theo shop, `shippingTotal`, `payableTotal`; `notCalculated` chứa `SHIPPING` khi chưa có chính sách đã duyệt. Báo giá và đơn dùng cùng một bộ tính.

**S1: đặt đơn COD**

- `V1007`: `sales.checkout_group`, `seller_order`, `order_line`, `order_line_reservation`, `order_status_history` (snapshot tên shop và sản phẩm, vùng giao, `return_policy_id`, CHECK `payable = items − discount + shipping`).
- `POST /api/v1/checkout/orders` (header `Idempotency-Key`, thân `{quoteId, paymentMethod}`), `GET /checkout/groups/{id}`, `GET /orders`, `GET /orders/{id}`. Thông tin người nhận mã hóa bằng `PiiCipher`, gắn với id đơn.
- Một transaction: khóa giỏ → idempotency → cổng chính sách → kiểm báo giá và tính lại giá → cấm mua hàng của shop mình → hạn mức COD → ghi đơn → giữ hàng theo thứ tự `sku_id` và `commit` ngay → outbox `OrderPlaced` (chỉ id và số tiền) → xóa giỏ.
- Web: nút "Đặt hàng (COD)" sau khi chốt báo giá (khóa idempotency giữ nguyên khi mạng lỗi), `/checkout/result/{groupId}`, `/orders`, `/orders/{id}`, `/admin/policies`.

## 2. Khác với thiết kế docs/20

1. **Idempotency kiểm sau khi khóa giỏ, không phải insert-trước.** Khóa giỏ đã tuần tự hóa mọi checkout của một người mua nên kiểm tra khóa và báo giá dưới khóa là đủ; ràng buộc UNIQUE vẫn là chốt chặn cuối.
2. **Mã lỗi:** báo giá hết hạn, giỏ/địa chỉ đổi hoặc giá đổi đều là `QUOTE_STALE` (409) kèm thông điệp; không có `QUOTE_CHANGED` với bảng chênh lệch có cấu trúc. Thiếu chính sách là `CHECKOUT_POLICY_MISSING`.
3. **PSP chưa hỗ trợ:** `paymentMethod=PSP` trả 400.
4. Hạn mức COD áp dụng cho từng đơn shop (`maxOrderAmount`) và số đơn COD đang mở của người mua (`maxOpenOrders`).

## 3. Bài học khi làm

- Các hàm đọc đơn không được đặt `readOnly`: `AccountService.requireActiveAccount` có thể tạo tài khoản (INSERT) và PostgreSQL từ chối trong transaction chỉ đọc. Test MockMvc không bắt được vì test chạy trong transaction ghi; E2E thật bắt được.
- Test đồng thời commit dữ liệu thật nên dọn theo tiền tố `order-conc-` kể cả dữ liệu của lần chạy hỏng trước.

## 4. Kiểm chứng

- Backend: 10 test tích hợp (cổng chính sách, đặt đơn hai shop, retry cùng khóa, báo giá cũ/giá đổi/hết hạn, thiếu địa chỉ, hết hàng sau báo giá, tự mua, hạn mức COD, validate) và 3 test đồng thời (8 request song song một khóa chỉ tạo một đơn, đơn vị cuối cùng chỉ thuộc một người, hai checkout mua cùng cặp SKU ngược thứ tự không deadlock).
- Playwright `order.spec.ts` với API, Keycloak (TOTP thật) và Redis thật: admin duyệt bốn chính sách, người mua đặt đơn COD, trang kết quả, danh sách và chi tiết đơn.
- `verify-db-design.ps1` đạt với V1006, V1007 và DDL tham chiếu đã bỏ các bảng đã triển khai.

## 5. Giới hạn

1. Các con số chính sách trong test và E2E chỉ là giá trị thử; số thật do chủ sản phẩm quyết định (D4, D5, D7).
2. Một người vừa tạo vừa duyệt chính sách vẫn được (chưa có kiểm soát hai người).
3. Tồn kho trừ ngay khi đặt COD; chưa có luồng hủy hoàn tồn (S2).
4. Phí ship cố định theo shop; chưa phân bổ hay báo giá từ carrier.
5. Dữ liệu E2E để lại chính sách đã duyệt và đơn COD trong DB local.
