# Thông báo trong ứng dụng

Ngày: **20/11/2026**. Phạm vi: thông báo trong ứng dụng cho người mua và chủ gian hàng khi đơn hàng, hoàn tiền hoặc yêu cầu trả hàng đổi trạng thái. Có thêm **bản sao qua email** (người dùng tự bật, mục 3b). **Chưa có SMS hay thông báo đẩy**.

## 1. Cách hoạt động

```text
Dịch vụ nghiệp vụ ──(cùng giao dịch)──► outbox_event ──(OutboxWorker, 3 giây/lần)──► OutboxDispatcher
                                                                                         │ mỗi handler trong savepoint riêng
                                                                                         ▼
                                              OrderNotificationHandler ──► platform.notification (dedupe theo người nhận)
```

- Sự kiện đã được ghi vào outbox cùng giao dịch với thay đổi nghiệp vụ (không thể mất hay thừa so với thay đổi), nên thông báo không bao giờ báo về điều chưa xảy ra.
- `OutboxProcessingService` ghi biên nhận, gọi các `OutboxEventHandler`, rồi đánh dấu đã xử lý trong **một giao dịch**. Mỗi handler chạy trong **savepoint** (`NESTED`): handler lỗi bị ghi log và hoàn tác riêng, không chặn sự kiện sau nó.
- Giao theo kiểu "ít nhất một lần": khóa chống trùng `(người nhận, sự kiện)` bảo đảm phát lại cùng sự kiện không tạo thêm thông báo.
- `OrderNotificationHandler` (module `order`) quyết định ai nhận gì; module `platform` chỉ lưu và phục vụ thông báo, nên không phụ thuộc ngược vào đơn hàng.
- Ở môi trường cục bộ (`profile local`) một tiến trình API cũng chạy các job nền (outbox, nhả tồn, hủy đơn chưa thanh toán). Ở môi trường thật chạy tiến trình riêng với profile `worker`; các job an toàn khi chạy song song (khóa `SKIP LOCKED`).

## 2. Ai nhận gì

| Sự kiện                                             | Người mua                       | Chủ gian hàng        |
| --------------------------------------------------- | ------------------------------- | -------------------- |
| Đơn COD mới / đơn đã thanh toán trực tuyến          | Đã nhận thanh toán (đơn online) | Đơn mới              |
| Shop xác nhận / từ chối                             | Có                              |                      |
| Người mua hủy đơn                                   |                                 | Có                   |
| Đơn hết hạn thanh toán (hệ thống hủy)               | Có                              |                      |
| Giao cho vận chuyển / đã giao                       | Có                              |                      |
| Vận đơn có vấn đề (giao thất bại, hoàn về)          | Có                              | Có                   |
| Cần nhập tài khoản nhận hoàn tiền / đã hoàn tiền    | Có                              |                      |
| Yêu cầu trả hàng mới                                |                                 | Có                   |
| Người bán đề xuất, từ chối, đồng ý nhận lại hàng    | Có                              |                      |
| Người mua gửi trả hàng, đồng ý đề xuất, hủy yêu cầu |                                 | Có                   |
| Chuyển quản trị viên, duyệt hoàn tiền, bị từ chối   | Có (hoặc cả hai bên)            | Có (hoặc cả hai bên) |

Quản trị viên **không** nhận thông báo; họ làm việc từ các hàng đợi (`/admin/refunds`, `/admin/returns`, `/admin/orders`). Nội dung chỉ có số đơn, số tiền và đường dẫn tới đúng màn hình; **không có số điện thoại, địa chỉ hay tên người nhận** (có test kiểm tra).

## 3. API và giao diện

- `GET /api/v1/me/notifications?unread=&page=&size=`, `GET .../unread-count`, `POST .../{id}/read`, `POST .../read-all`. Mọi truy vấn gắn với tài khoản trong token; đánh dấu thông báo của người khác trả 404.
- Web: chuông có số chưa đọc ở đầu trang (cập nhật 30 giây một lần khi tab đang hiện, và ngay sau khi làm mới trang), trang `/notifications`, mở một thông báo thì đánh dấu đã đọc và chuyển tới nội dung liên quan.

## 3b. Email (người dùng tự bật)

- **Đồng ý nhận:** `GET/PUT /api/v1/me/email-notifications` (`{"enabled":true|false}`). Địa chỉ lấy từ claim `email` của token và **chỉ khi `email_verified=true`**; không nhận địa chỉ từ người dùng nhập. Thiếu hoặc chưa xác minh trả 409 `EMAIL_NOT_VERIFIED`. Địa chỉ lưu mã hoá (`identity.notification_email`), API chỉ trả dạng che (`h***@gmail.com`).
- **Hàng đợi:** mỗi thông báo mới của người đã bật sinh một dòng `platform.email_delivery` trong cùng transaction. `EmailWorker` (5 giây) nhận việc bằng `FOR UPDATE SKIP LOCKED`, gửi **ngoài transaction**, và **đọc lại sự đồng ý lúc gửi**: tắt email thì thư đang chờ chuyển `SKIPPED`.
- **Thử lại:** lỗi gửi chờ 1, 5, 30, 120 phút; sau lần thứ 5 là `FAILED`. Tiến trình chết giữa chừng thì sau 2 phút thư được nhận lại. Thông báo trong ứng dụng không bị ảnh hưởng.
- **Nội dung:** tiêu đề và nội dung thông báo (đã không có dữ liệu cá nhân), liên kết tới đúng màn hình, liên kết tắt email; HTML được escape, tiêu đề một dòng.
- **Cấu hình (chỉ qua môi trường):** `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_STARTTLS`, `EMAIL_FROM`, `WEB_BASE_URL`. Máy local dùng Mailpit (`docker compose up -d mailpit`, SMTP 1025, xem thư tại http://localhost:8025).
- **Giao diện:** `/notifications/settings`.
- **Đã kiểm chứng:** test tích hợp (đồng ý, xếp hàng, gửi, thử lại/bỏ cuộc, bỏ qua khi tắt, không cấu hình) và một lần gửi thật qua SMTP tới Mailpit (tiếng Việt, escape HTML). **Chưa thử với nhà cung cấp thật.**

## 4. Giới hạn

1. **Email cần máy chủ SMTP thật.** Khi chưa đặt `SMTP_HOST` và `EMAIL_FROM` thì không có email nào được gửi (giao diện báo rõ). SMS/đẩy chưa có; cần chọn nhà cung cấp.
2. Cập nhật bằng thăm dò 30 giây, chưa có đẩy thời gian thực.
3. Chưa có tuỳ chọn tắt từng loại thông báo và chưa dọn các thông báo cũ.
4. Thông báo cho quản trị viên chưa có (dùng hàng đợi).
5. Chủ gian hàng là người duy nhất nhận (chưa có thành viên khác của gian hàng).
6. Khi tiến trình nền không chạy, thông báo trễ cho tới khi nó chạy lại; sự kiện không mất vì đã nằm trong outbox.
