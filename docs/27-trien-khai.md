# Triển khai production và vận hành

Ngày: **21/11/2026**. Trạng thái: **cấu hình đã viết và kiểm tra cú pháp, chưa triển khai ở đâu cả**. Không có máy chủ, tên miền hay khóa thật nào được dùng; mọi giá trị trong `deploy/.env.production.example` là chỗ trống. Nơi chạy là quyết định của chủ sản phẩm; cấu hình này chạy trên **một máy Docker** (VPS hoặc máy riêng) và có thể tách dần từng dịch vụ sang dịch vụ quản lý.

## 1. Thành phần

| Dịch vụ | Việc | Ghi chú |
|---|---|---|
| `proxy` (Caddy) | HTTPS tự động (Let's Encrypt), 3 tên miền | Chỉ dịch vụ này mở cổng 80/443 |
| `web` | Next.js (giao diện, BFF) | Không giữ dữ liệu; phiên nằm ở Redis |
| `api` | Spring Boot, không chạy việc nền | Kiểm tra sức khỏe `/actuator/health` |
| `worker` | Cùng ảnh với API, profile `worker` | Outbox, hết hạn đơn, email, đối soát chi tiền; chạy nhiều bản cũng an toàn (`SKIP LOCKED`) |
| `postgres`, `keycloak-db` | Hai cơ sở dữ liệu riêng | Volume Docker, không mở cổng |
| `keycloak` | Đăng nhập, đăng ký, MFA admin | Realm nhập từ file tạo bởi script |
| `redis` | Phiên web, giới hạn tốc độ dùng chung | Mất Redis chỉ buộc đăng nhập lại; đăng ký/đăng nhập tạm từ chối (an toàn) |

Tên miền: `SHOP_DOMAIN` (web và các callback của payOS/VNPay/GHTK), `AUTH_DOMAIN` (Keycloak; trang quản trị Keycloak và realm `master` bị chặn ngoài internet), `API_DOMAIN` (API công khai cho app mobile).

## 2. Lần triển khai đầu

1. Trỏ DNS ba tên miền về máy chủ; mở cổng 80 và 443.
2. `cp deploy/.env.production.example deploy/.env`, điền mọi `CHANGE_ME`. Sinh bí mật: `openssl rand -hex 24`; `SESSION_SECRET` và `PII_KEY_LOCAL_1` là 32 byte ngẫu nhiên mã base64 (`openssl rand -base64 32`).
3. **Sao lưu `PII_KEY_LOCAL_1` ở nơi an toàn, tách khỏi máy chủ.** Mất khóa này thì địa chỉ giao hàng, số điện thoại và tài khoản nhận hoàn tiền đã mã hóa không đọc lại được.
4. `node scripts/prepare-prod-realm.mjs` (tạo `deploy/.generated/vshop-realm.json`: URL công khai, secret của hai client chỉ dùng ở máy chủ, SMTP cho email của Keycloak).
5. `docker compose --env-file deploy/.env -f deploy/compose.prod.yaml up -d --build`. Flyway tự chạy migration khi API khởi động.
6. Đăng nhập Keycloak bằng tài khoản bootstrap (trực tiếp trên máy chủ hoặc qua đường hầm SSH tới cổng 8080, vì `/admin` bị chặn trên tên miền công khai), vào realm `vshop`, tạo **tài khoản quản trị đầu tiên** với vai trò `ADMIN` và `BUYER`; lần đăng nhập đầu sẽ bắt thiết lập OTP. Đổi mật khẩu bootstrap của Keycloak.
7. Đăng nhập quản trị trên web, tạo và **duyệt các chính sách** (phí ship, đổi trả, COD, thanh toán, xử lý trả hàng): đơn chưa đặt được khi thiếu chính sách đã duyệt.
8. Đăng ký webhook ở nhà cung cấp (chỉ khi bật tính năng tương ứng):
   - payOS: `https://<SHOP_DOMAIN>/api/v1/payments/payos/webhook`
   - VNPay IPN: `https://<SHOP_DOMAIN>/api/v1/payments/vnpay/ipn`
   - GHTK: `https://<SHOP_DOMAIN>/api/v1/shipping/ghtk/webhook?hash=<GHTK_WEBHOOK_TOKEN>`
9. Chạy luồng thật bằng đơn nhỏ: đăng ký, đặt COD, thanh toán payOS số tiền nhỏ, tạo vận đơn GHTK, hủy, hoàn tiền. payOS và GHTK production không có sandbox: mỗi lần thử là thật.

## 3. Cập nhật và quay lui

- Cập nhật: `git pull`, rồi lệnh `up -d --build` ở trên. Migration chỉ thêm, không sửa migration đã áp dụng. Migration V1000 trở lên đã áp dụng là bất biến.
- Quay lui mã: dùng ảnh trước (đặt thẻ qua workflow **Build container images**, chạy tay trên GitHub, đẩy lên GHCR). Migration không tự quay lui: khôi phục từ bản sao lưu nếu cần.
- Workflow `images.yml` chỉ chạy khi bạn bấm; không có gì tự phát hành.

## 4. Sao lưu và giám sát (việc bạn cần làm)

- **Sao lưu hằng ngày** cả hai PostgreSQL (`pg_dump`) ra nơi khác máy chủ, giữ nhiều bản, và **thử khôi phục** định kỳ. Sao lưu cả volume `redis-data` là không cần thiết.
- Giám sát: gọi `https://<API_DOMAIN>/actuator/health` từ dịch vụ ngoài; cảnh báo khi `worker` dừng (đơn quá hạn, email và chi tiền sẽ không chạy).
- Nhật ký: `docker compose logs`. Chưa có tập trung nhật ký, đo lường hay cảnh báo (backlog).

## 5. Bảo mật khi chạy thật

- Đặt máy chủ sau tường lửa chỉ mở 80/443 (và SSH có khóa).
- `X-Forwarded-For` được Caddy đặt lại; giới hạn tốc độ theo IP của BFF dựa vào đó nên **không đặt thêm proxy tin cậy lỏng phía trước** mà không ghi đè header này.
- Đổi định kỳ: mật khẩu cơ sở dữ liệu, secret hai client Keycloak, `SESSION_SECRET` (đổi sẽ đăng xuất mọi người). Đổi `PII_KEY_LOCAL_1` cần quy trình xoay khóa của `PiiCipher` (thêm khóa mới, đặt `PII_ACTIVE_KEY`, giữ khóa cũ để đọc), không đổi trực tiếp giá trị.
- Bật CAPTCHA (Turnstile) và SMTP thật trước khi mở đăng ký rộng rãi.
- Chi hoàn tiền tự động giữ `PAYOS_PAYOUT_ENABLED=false` cho tới khi kênh chi hộ payOS được xác minh và một khoản nhỏ được kiểm bằng tay.

## 6. Chưa kiểm chứng

- Chưa chạy `compose up` trên một máy chủ thật, chưa xin chứng chỉ HTTPS, chưa thử webhook từ internet (cần tên miền công khai).
- Ảnh Docker của API (577 MB) và web **đã build thành công** trên máy này (21/11/2026); `docker compose config` hợp lệ; realm sinh ra được kiểm trên dữ liệu giả, chưa nhập vào Keycloak production.
- Chưa có CDN, kho tệp cho ảnh sản phẩm, đa vùng, tự động mở rộng.
