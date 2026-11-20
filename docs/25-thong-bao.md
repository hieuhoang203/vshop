# Thông báo trong ứng dụng

Ngày: **20/11/2026**. Phạm vi: thông báo trong ứng dụng cho người mua và chủ gian hàng khi đơn hàng, hoàn tiền hoặc yêu cầu trả hàng đổi trạng thái. **Chưa có email, SMS hay thông báo đẩy**: chưa có nhà cung cấp gửi tin nào được chọn (xem mục 4).

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

## 4. Giới hạn

1. **Chỉ trong ứng dụng.** Người dùng không mở web sẽ không biết. Email/SMS/đẩy cần chọn nhà cung cấp, mẫu nội dung và xử lý đồng ý nhận thông báo; handler hiện có có thể được tái dùng.
2. Cập nhật bằng thăm dò 30 giây, chưa có đẩy thời gian thực.
3. Chưa có tuỳ chọn tắt từng loại thông báo và chưa dọn các thông báo cũ.
4. Thông báo cho quản trị viên chưa có (dùng hàng đợi).
5. Chủ gian hàng là người duy nhất nhận (chưa có thành viên khác của gian hàng).
6. Khi tiến trình nền không chạy, thông báo trễ cho tới khi nó chạy lại; sự kiện không mất vì đã nằm trong outbox.
