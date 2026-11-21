# Vận chuyển qua GHTK (S4)

Ngày: **20/11/2026**. Phạm vi: docs/20 mục S4 với **Giao Hàng Tiết Kiệm (GHTK)**. Số liệu kỹ thuật lấy từ tài liệu `api.ghtk.vn` (tổng quan, tạo đơn, tính phí, trạng thái, webhook, hủy đơn, xử lý lỗi). **Chưa chạy với GHTK thật** vì chưa có token và mã đối tác (xem mục 6). Các trang in nhãn và lấy địa chỉ lấy hàng của GHTK chưa đọc được nên chưa dùng.

## 1. Đã làm

- **V1011:** `fulfillment.pickup_address` (điện thoại và địa chỉ mã hóa bằng `PiiCipher`), `fulfillment.shipment` (một kiện cho một đơn; chỉ mục duy nhất cho kiện đang sống) và `fulfillment.shipment_event` (mỗi báo cáo của GHTK lưu một lần). Các bảng này đã được bỏ khỏi `target-commerce.sql`; `shipment_line` và `cod_receivable` vẫn là thiết kế tham chiếu.
- **Cổng `ShippingCarrier`** và **`GhtkCarrier`**: tạo đơn (`POST /services/shipment/order`), tra theo mã đơn của ta (`GET /services/shipment/v2/{id}`), hủy (`POST /services/shipment/cancel/{label}`), đọc webhook. Header `Token` và `X-Client-Source`; mã đơn của ta (`order.id`) là khóa chống trùng vì GHTK không nhận hai lần cùng `order.id`. Tiền là số nguyên VND, khối lượng sản phẩm đổi sang kg.
- **Tạo vận đơn không giữ khóa DB khi gọi mạng:** giao dịch 1 ghi kiện ở trạng thái `CREATING`; gọi GHTK ngoài giao dịch; giao dịch 2 ghi kết quả và chuyển đơn sang `IN_FULFILLMENT`. GHTK từ chối thì kiện `FAILED` (đơn vẫn `READY_TO_SHIP`, thử lại dùng lại cùng mã đối tác). Không nhận được phản hồi thì kiện ở lại `CREATING` và lần thử lại gửi cùng mã; GHTK trả "đã tồn tại" kèm nhãn (hoặc ta tra theo mã đơn) nên **không bao giờ tạo hai kiện**.
- **COD:** `pick_money` = tổng phải thu của đơn (tiền hàng + phí ship) khi đơn là COD, 0 khi đã thanh toán online; `is_freeship=1` vì người mua đã trả phí ship cho sàn. Giá trị khai báo (`value`) là tiền hàng.
- **Webhook** `POST /api/v1/shipping/ghtk/webhook` (JSON hoặc form; công khai, xác thực bằng bí mật `hash` trên URL, so sánh thời gian hằng; GHTK gọi thẳng API, không qua BFF). Sai bí mật thì trả 200 `success=false` và ghi log, không xử lý. Báo cáo lặp không tác dụng hai lần. Ánh xạ: 3 lấy hàng, 4 đang giao, **5/6/45 đã giao** (đơn sang `DELIVERED`, tính hạn đổi trả; khác với chế độ thủ công vì có xác nhận của đơn vị vận chuyển), 9/10 giao thất bại, 20/21/11 hoàn, **-1 hủy** (đơn quay về `READY_TO_SHIP`). Trạng thái lạ chỉ được ghi lại.
- **Hủy vận đơn:** `POST .../orders/{id}/shipment/cancel`; GHTK chỉ cho hủy khi chưa lấy hàng; thành công thì kiện `CANCELED`, đơn về `READY_TO_SHIP`, tạo lại dùng mã đối tác mới (`...-2`).
- **API shop:** `GET/PUT /api/v1/seller/shops/{id}/pickup-address`, `POST .../orders/{id}/shipment {weightGrams}`, `POST .../shipment/cancel`. Web: form địa chỉ lấy hàng, nút "Tạo vận đơn GHTK" kèm ô khối lượng và nút "Hủy vận đơn GHTK" ở `/seller/orders`; giao thủ công vẫn dùng được.

## 2. Cấu hình

| Biến môi trường      | Ý nghĩa                                                                                                          |
| -------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `GHTK_TOKEN`         | Token API tạo ở trang khách hàng của GHTK                                                                        |
| `GHTK_PARTNER_CODE`  | Mã đối tác (header `X-Client-Source`)                                                                            |
| `GHTK_WEBHOOK_TOKEN` | Bí mật tự chọn, đặt vào URL webhook (`?hash=...`)                                                                |
| `GHTK_BASE_URL`      | Mặc định là staging `https://services-staging.ghtklab.com`; production là `https://services.giaohangtietkiem.vn` |

Chưa cấu hình thì tạo vận đơn bị từ chối (409 `CARRIER_NOT_CONFIGURED`) và shop dùng giao thủ công. Không để bí mật trong repo.

## 3. Kiểm chứng

6 test tích hợp với GHTK được giả lập (`ShipmentIntegrationTests`): cần cấu hình và địa chỉ lấy hàng và đơn đã sẵn sàng; dữ liệu gửi sang GHTK (mã đơn, số tiền thu hộ, giá trị khai báo, điện thoại, tổng khối lượng); lặp lại không tạo hai kiện; giao hàng chỉ đến từ webhook đúng bí mật, lặp lại không tác dụng; bị từ chối rồi không có phản hồi rồi "đã tồn tại" cho đúng một kiện cùng mã; hủy và tạo lại; chỉ chủ gian hàng dùng được API.

## 4. Khác thiết kế docs/20

1. **Phí ship vẫn là phí cố định theo chính sách.** Chưa dùng API tính phí của GHTK tại bước báo giá; phí GHTK thực tế (`carrier_fee`) được lưu để đối chiếu nhưng không đổi số tiền người mua trả.
2. **Chưa có `cod_receivable`** (đối soát tiền thu hộ) và chưa dùng trạng thái 5/6 để đối soát với shop.

## 5. Giới hạn

1. **Chưa có khối lượng sản phẩm trong danh mục:** shop nhập tổng khối lượng kiện khi tạo vận đơn (50g–50kg); khối lượng được chia đều cho các dòng. Cần thêm khối lượng và kích thước vào SKU để tính chính xác.
2. **Địa chỉ người nhận chỉ có tỉnh và phường/xã**, chưa có quận/huyện; ta gửi `hamlet="Khác"` và địa chỉ cụ thể. Có thể bị GHTK từ chối với địa chỉ chưa chuẩn; cần danh mục hành chính chuẩn.
3. **Xác thực webhook chỉ là bí mật trên URL** (GHTK không mô tả chữ ký). Dùng HTTPS và giá trị ngẫu nhiên dài; không ghi URL vào log.
4. **Tài liệu GHTK có chỗ mâu thuẫn** (lệnh hủy ghi POST nhưng mẫu mã dùng GET; mức phí trong ví dụ webhook), cần xác nhận bằng một lần gọi staging.
5. **Chưa đọc** trang in nhãn, danh sách địa chỉ lấy hàng và các API B2C của GHTK; chưa có in nhãn.
6. Trạng thái trả hàng/hoàn của GHTK (20/21/11) mới chỉ được ghi và phát sự kiện `ShipmentProblem`; hàng hoàn về shop nay được xử lý ở docs/24 mục 12 (shop xác nhận, hủy đơn, nhập kho, hoàn tiền).
7. Chưa có tác vụ đối chiếu định kỳ trạng thái với GHTK khi webhook bị mất.
