# Tồn kho và giữ hàng

Ngày: **20/11/2026**. Phạm vi: backend module `inventory` (backlog C09 và C10, **InProgress**). Chưa có UI quản lý tồn, chưa có checkout gọi giữ hàng, chưa có worker tự động hết hạn.

## 1. Seller API

Dưới `/api/v1/seller/shops/{shopId}/inventory`, chỉ owner của shop `APPROVED`.

| Method | Đường dẫn                        | Mục đích                                                                                                                                         |
| ------ | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| POST   | `/adjustments`                   | Nhập kho (`RECEIPT`, delta > 0) hoặc điều chỉnh (`ADJUSTMENT`, delta ≠ 0). Bắt buộc header `Idempotency-Key` (8–100 ký tự) và `reason` ≥ 5 ký tự |
| GET    | `/skus/{skuId}`                  | Số dư theo kho: `onHand`, `reserved`, `blocked`, `available`                                                                                     |
| GET    | `/skus/{skuId}/movements?limit=` | Lịch sử movement gần nhất (tối đa 200)                                                                                                           |

- Pilot một kho mỗi shop: kho `DEFAULT` được tạo ở lần nhập đầu tiên. Đây chưa phải địa chỉ xuất hàng đã xác minh.
- `|delta| ≤ 1.000.000`. Không thể giảm `on_hand` thấp hơn `reserved + blocked`; vi phạm trả 409 `INSUFFICIENT_STOCK` và không ghi gì.
- **Idempotency:** khóa được gắn với shop. Gửi lại cùng khóa và cùng nội dung chỉ áp dụng một lần và trả số dư hiện tại; cùng khóa khác nội dung trả 409 `IDEMPOTENCY_CONFLICT`. Lần lặp không ghi thêm audit.
- Mỗi lần áp dụng ghi `StockMovement` và audit `StockAdjusted` cùng transaction. Audit dùng mã shop làm `resource_id` (cùng lưu ý với catalog).
- SKU phải thuộc shop trong đường dẫn, nếu không trả 404 và không ghi gì. Seller catalog API hiển thị `available` từ cùng số dư này.

## 2. ReservationService (nội bộ, không có HTTP)

Dành cho module checkout sau này; cố ý **không** có endpoint công khai.

| Lệnh                                     | Hiệu ứng                                                                                                                 | Lặp lại                                                                         |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| `reserve(sku, qty, reference, key, ttl)` | `reserved += qty` trên kho còn nhiều hàng nhất đủ qty; tạo reservation `HELD` có hạn                                     | Cùng khóa + cùng payload trả lượt giữ cũ; khác payload → `IDEMPOTENCY_CONFLICT` |
| `commit(id)`                             | `on_hand -= qty` và `reserved -= qty` cùng lúc (BR32), `COMMITTED`                                                       | Lặp lại là no-op                                                                |
| `release(id)`                            | `reserved -= qty`, `RELEASED`                                                                                            | Lặp lại (kể cả sau `EXPIRED`) là no-op                                          |
| `expireDue(limit)`                       | Chuyển `HELD` quá hạn thành `EXPIRED`, trả `reserved`; `FOR UPDATE SKIP LOCKED` nên nhiều worker chạy song song vẫn đúng | Chạy lại không trả hai lần                                                      |

Trạng thái cuối không chuyển sang trạng thái khác: `COMMITTED` không thể `release`, `RELEASED/EXPIRED` không thể `commit` (409 `INVALID_RESERVATION_STATE`). Mọi thay đổi số lượng đi qua một câu `UPDATE` có điều kiện (không âm, `reserved ≤ on_hand`, giữ phần `blocked`), cộng thêm `CHECK` của DB làm lớp cuối. Mỗi lệnh ghi một movement với khóa duy nhất (`reserve:`, `commit:`, `release:`, `expire:` + id). Thứ tự khóa: reservation trước, balance sau (balance theo `warehouse_id`).

Quyết định cần lưu ý:

- **`commit` không kiểm tra hạn.** Trạng thái lưu là nguồn sự thật, để đơn đã thanh toán không bị mất ở khoảng giữa hạn giữ và lần quét hết hạn. Checkout vẫn có thể từ chối commit theo chính sách thanh toán muộn (BR10/BR13).
- `reserve` **không kiểm tra** SKU hay sản phẩm đang bán, vì đó là việc của quote/checkout.
- TTL cho phép từ 1 giây đến 24 giờ; số lượng mỗi lần từ 1 đến 10.000. Hạn tính bằng giờ DB (`now()`), không dùng đồng hồ client.

## 3. Giới hạn còn lại

1. Chưa có scheduler gọi `expireDue`; sẽ nối cùng lúc với checkout để có dữ liệu thật để kiểm. Tạm thời hạn không bị cưỡng chế cho tới khi có worker.
2. Chưa dùng `blocked` (hàng hỏng/kiểm nhận) và movement `RETURN`; thuộc luồng hậu mãi.
3. Chưa có cảnh báo hết hàng, nhập nhiều SKU một lần, import CSV, hoặc nhiều kho.
4. Chưa hạn chế quyền `UPDATE/DELETE` của runtime role trên `stock_movement` (việc hardening production).
5. Staff (`FULFILLMENT`) chưa được dùng; chỉ owner thao tác.
6. `packages/contracts`, web và mobile chưa có kiểu/UI cho các endpoint này.

## 4. Kiểm chứng

`scripts/test-backend.ps1`: **56 test đạt**, chạy lặp 3 lần không flaky.

- `SellerInventoryIntegrationTests` (5): nhập/điều chỉnh có movement và audit, đầu vào sai không đổi dữ liệu, cùng khóa chỉ áp dụng một lần, không giảm dưới lượng đang giữ, cô lập giữa seller/shop và shop chưa duyệt.
- `ReservationConcurrencyTests` (7, giao dịch thật có commit, tự dọn dữ liệu): **8 luồng tranh 1 đơn vị cuối → đúng 1 thắng, 7 thua**; 12 người mua × 3 đơn vị trên 10 tồn → đúng 3 thắng, `reserved = 9`; 8 lần retry cùng khóa → một reservation và một movement; 6 lệnh commit song song → trừ tồn một lần; vòng đời và trạng thái cuối; hold quá hạn được trả đúng một lần dưới 4 lần quét song song.
- ArchUnit 8/8 và 11 DB constraint test vẫn đạt.

Test chạy trên PostgreSQL thật trong `vshop_test`; thao tác có commit chỉ đụng shop do chính test tạo và xóa sau khi chạy. Chưa có E2E trình duyệt vì chưa có UI, và chưa chạy tải đo p95 nên chưa có số liệu hiệu năng.
