# Thanh toán trực tuyến qua VNPay (S3)

Ngày: **20/11/2026**. Phạm vi: docs/20 mục S3 với **VNPay** làm nhà cung cấp đầu tiên. Số liệu kỹ thuật lấy từ tài liệu sandbox của VNPay (thanh toán PAY phiên bản 2.1.0, IPN, bảng mã lỗi). Chưa chạy được với VNPay thật vì chưa có tài khoản sandbox (xem mục 5).

## 1. Vì sao VNPay mà không phải payOS

|                   | VNPay                               | payOS                                                     |
| ----------------- | ----------------------------------- | --------------------------------------------------------- |
| Môi trường thử    | Có sandbox và thẻ thử               | Không có sandbox, thử trên tài khoản thật với số tiền nhỏ |
| Hoàn tiền qua API | Có (querydr và refund)              | Tài liệu chưa mô tả                                       |
| Chữ ký            | HMAC-SHA512, thuật toán và ví dụ rõ | HMAC-SHA256, thiếu ví dụ cho request tạo link             |
| SDK Java          | Có mẫu Java                         | Không có SDK Java                                         |

payOS vẫn dùng được sau này vì lõi thanh toán nằm sau cổng `PaymentGateway`.

## 2. Đã làm

- **V1010:** `payment.intent`, `attempt`, `charge`, `allocation` (theo DDL tham chiếu), `provider_event` (ghi mọi callback, chống trùng) và `review_case` (hàng đợi tra soát). Các bảng này đã được bỏ khỏi `target-commerce.sql`.
- **Cổng `PaymentGateway`** (`modules/order/gateway`) và **`VnpayGateway`**: dựng URL thanh toán (tham số `vnp_*`, số tiền nhân 100, giờ GMT+7, ký HMAC-SHA512 trên các tham số đã sắp xếp và mã hóa URL) và xác minh callback. Hai việc này là tính toán thuần, không gọi mạng nên an toàn trong transaction.
- **Đặt đơn `PSP`:** đơn vào `PENDING_PAYMENT` với `payment_deadline` theo chính sách (mặc định 15 phút), hàng được **giữ** (`HELD`, TTL = hạn + 5 phút đệm) chứ chưa trừ, tạo `intent` và `attempt`, trả `paymentUrl`. Gửi lại cùng khóa idempotency nhận lại đúng URL cũ.
- **Callback IPN** `GET /api/v1/payments/vnpay/ipn` (công khai, không token, chỉ xác thực bằng chữ ký; VNPay gọi thẳng API, không qua BFF). Xử lý: chữ ký sai trả `97`; không thấy đơn `01`; sai số tiền `04`; đã xác nhận `02`; thành công `00`. Thành công khớp thì **một transaction** ghi charge, phân bổ về từng đơn, chuyển đơn sang `AWAITING_SELLER`, `commit` tồn kho, outbox `OrderPaid`. Thanh toán thất bại thì attempt `FAILED`, đơn vẫn chờ tới hạn.
- **Hết hạn:** `OrderExpiryWorker` (cần `vshop.worker.enabled`) hủy đơn chưa thanh toán quá hạn, nhả tồn, intent `CANCELED`. Chạy lại là no-op.
- **Thanh toán muộn:** nếu callback thành công đến sau khi đơn đã hủy, charge được ghi, **không** hồi sinh đơn, intent `UNKNOWN`, mở `review_case` `PAID_AFTER_EXPIRY` để người xử lý hoàn tiền.
- **Web:** chọn COD hoặc VNPay ở giỏ; với VNPay chuyển tới `paymentUrl`; trang kết quả tự làm mới và chỉ tin trạng thái từ DB ("đang chờ thanh toán" cho tới khi callback đến).

## 3. Cấu hình

| Biến môi trường     | Ý nghĩa                                                       |
| ------------------- | ------------------------------------------------------------- |
| `VNPAY_TMN_CODE`    | Mã website (TmnCode) từ VNPay                                 |
| `VNPAY_HASH_SECRET` | Chuỗi bí mật tạo chữ ký                                       |
| `VNPAY_PAY_URL`     | Mặc định là URL sandbox; URL thật do VNPay cấp khi onboarding |
| `WEB_BASE_URL`      | Địa chỉ web nơi khách được đưa về                             |

Chưa cấu hình thì thanh toán trực tuyến bị từ chối (400, nêu rõ) và vẫn đặt COD bình thường. Không để bí mật trong repo.

## 4. Kiểm chứng

5 test tích hợp (`PaymentIntegrationTests`): URL ký đúng thuật toán (tính độc lập trong test), gửi lại cùng khóa nhận cùng URL, callback đúng chữ ký xác nhận đơn một lần và trừ tồn, callback lặp trả `02`, chữ ký giả hoặc bị sửa `97`, sai số tiền `04`, thanh toán thất bại không xác nhận đơn, hết hạn nhả tồn, thanh toán muộn vào hàng đợi tra soát mà không hồi sinh đơn, callback công khai chỉ mở bằng chữ ký.

## 5. Giới hạn và việc còn mở

1. **Chưa thử với VNPay thật.** Cần TmnCode và HashSecret sandbox (đăng ký tại trang sandbox của VNPay); khi có, chạy thử bằng thẻ thử trong tài liệu. IPN cần địa chỉ HTTPS công khai (có thể dùng một đường hầm khi thử).
2. **Chưa gọi `querydr` trước khi hủy đơn quá hạn** (docs/20 yêu cầu hỏi nhà cung cấp trước). Rủi ro được che bằng nhánh thanh toán muộn vào hàng đợi tra soát. Tài liệu VNPay còn thiếu định nghĩa `hashWithSecureType` cho querydr/refund, cần xác nhận bằng một lần gọi sandbox.
3. **Chưa có hoàn tiền** và chưa có màn hình cho `review_case` (chỉ lưu).
4. **Chưa có thử lại thanh toán** bằng attempt mới khi attempt đầu thất bại (hiện đơn chờ tới hạn rồi hủy).
5. **Mã khách (`vnp_IpAddr`)** lấy từ `X-Forwarded-For` hoặc địa chỉ kết nối; BFF hiện chưa chuyển tiếp IP khách nên sẽ là IP máy chủ web.
6. Người mua chưa thể hủy đơn đang chờ thanh toán (chỉ hết hạn tự hủy).
7. Thanh toán COD và trực tuyến cùng nhóm đơn: chưa hỗ trợ trộn.
