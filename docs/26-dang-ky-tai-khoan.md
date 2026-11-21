# Đăng ký, đăng nhập, khôi phục tài khoản và giao diện mới

Ngày: **21/11/2026**. Phạm vi: người mua tự đăng ký và đăng nhập bằng form ngay trên VShop (web và mobile), tự đăng nhập sau khi đăng ký, quên mật khẩu và xác minh email qua SMTP, CAPTCHA tùy chọn, trang điều khoản/chính sách, trang Keycloak có theme VShop, và giao diện cửa hàng được làm lại.

## 1. Luồng người dùng

```text
/register (form) → BFF /api/auth/register → API POST /api/v1/auth/register → Keycloak Admin API
   → tạo user + vai trò BUYER → BFF đăng nhập luôn bằng mật khẩu vừa chọn → "Bạn đã được đăng nhập" → trang chủ
/login (form) → BFF /api/auth/login-direct → Keycloak (password grant, client riêng) → phiên phía máy chủ
/forgot-password → BFF → API POST /api/v1/auth/forgot-password → Keycloak gửi email → đặt mật khẩu mới → /login
/notifications/settings → "Gửi email xác minh" → API POST /api/v1/me/email-verification → email → xác minh
```

- Mọi đường dẫn `/api/auth/login?...` cũ vẫn dùng được: chúng chuyển tới `/login`. `?mode=sso` dẫn tới trang Keycloak.
- **Quản trị viên** không đăng nhập bằng form: tài khoản có OTP không qua được password grant, và BFF từ chối cả khi qua được (xóa phiên Keycloak vừa tạo). Họ dùng liên kết "Đăng nhập có xác thực hai bước" (trang Keycloak, bắt buộc OTP). API vẫn chặn gọi quản trị nếu token không có `amr=otp`, nên đây không phải lớp bảo vệ duy nhất.
- Tên hiển thị trên site là họ tên thật nếu có.

## 2. Quy tắc đăng ký

Form kiểm tra từng ô khi rời ô và khi gửi; **API là nơi quyết định** và kiểm lại tất cả.

| Trường | Quy tắc |
|---|---|
| Họ tên | 2–60 ký tự, bắt đầu bằng chữ; chữ (có dấu), khoảng trắng, `.`, `'`, `-`; chuẩn hóa NFC |
| Email | hợp lệ, tối đa 254 ký tự, đưa về chữ thường; là tên đăng nhập |
| Mật khẩu | 8–64 ký tự, có chữ cái và chữ số, không bắt đầu/kết thúc bằng khoảng trắng, không trùng email |
| Điều khoản | bắt buộc tick (có liên kết tới `/terms` và `/privacy`) |

Kết quả API: `201`; `400 INVALID_REGISTRATION_INPUT`; `409 EMAIL_ALREADY_REGISTERED`; `429 TOO_MANY_REQUESTS`; `503 REGISTRATION_UNAVAILABLE`. Chỉ cấp vai trò `BUYER` (có test); người bán mở gian hàng từ tài khoản mua như trước.

## 3. Bảo mật

- **Mật khẩu** đi qua BFF → API/Keycloak một lần, không ghi log, không lưu ở database VShop.
- **Client Keycloak chỉ dùng ở máy chủ:**
  - `vshop-provisioner` (service account, quyền `manage-users`, `view-users`, `view-realm`): tạo user và gửi email hành động.
  - `vshop-web-direct` (confidential, password grant): form đăng nhập. `vshop-web` công khai vẫn **không** bật password grant.
  - Secret chỉ ở `.env` (`KEYCLOAK_PROVISIONER_SECRET`, `KEYCLOAK_WEB_DIRECT_SECRET`, và `apps/web/.env.local` cho web).
- **Giới hạn tốc độ:** BFF (Redis, dùng chung): đăng ký 10/giờ/IP và 5/giờ/email, đăng nhập 40/10 phút/IP và 10/10 phút/email, quên mật khẩu 10/giờ/IP. API (bộ nhớ, cho cả mobile): đăng ký 5/giờ/email, quên mật khẩu 3/giờ/email, xác minh 3/giờ/tài khoản. Redis lỗi thì BFF từ chối, không bỏ giới hạn. IP lấy từ `X-Forwarded-For`: khi triển khai phải đặt sau proxy tin cậy ghi đè header này.
- **Quên mật khẩu** luôn trả "đã gửi" với email đúng định dạng, dù có tài khoản hay không. Liên kết trong email do Keycloak tạo (dùng một lần, 30 phút); mật khẩu mới được nhập trên trang Keycloak, không qua VShop.
- **CAPTCHA (tùy chọn):** Cloudflare Turnstile cho đăng ký và quên mật khẩu. Bật khi có đủ `TURNSTILE_SITE_KEY` và `TURNSTILE_SECRET_KEY` (khóa thật tạo trong Cloudflare); không có thì chỉ dùng giới hạn tốc độ. Đã kiểm với khóa thử nghiệm của Cloudflare: thiếu token bị từ chối, widget điền token và đăng ký thành công.

## 4. Email

- Keycloak gửi email qua SMTP của realm. Local: Mailpit (`mailpit:1025`, xem tại http://localhost:8025). Nhà cung cấp thật: đặt `KEYCLOAK_SMTP_HOST`, `KEYCLOAK_SMTP_PORT`, `KEYCLOAK_SMTP_FROM`, `KEYCLOAK_SMTP_USER`, `KEYCLOAK_SMTP_PASSWORD`, `KEYCLOAK_SMTP_STARTTLS` trong `.env` rồi chạy lại `scripts/init-registration.ps1`.
- Email và trang Keycloak có theme `vshop` (tiếng Việt do dự án viết vì Keycloak không kèm bản tiếng Việt).
- Đăng ký **không** ép xác minh email để vào được ngay. Xác minh là tùy chọn và cần khi bật thông báo email: ở `/notifications/settings` có nút "Gửi email xác minh". Claim `email_verified` cập nhật ở lần làm mới token hoặc đăng nhập lại.

## 5. Mobile

Màn hình **Đăng ký** (`/register` trong Expo Router) gọi thẳng `POST /api/v1/auth/register`, cùng quy tắc và thông báo như web (có kiểm tra tức thời). Xong thì bấm "Đăng nhập ngay" để mở trang Keycloak (PKCE như cũ). Mobile chưa có CAPTCHA (khóa Turnstile cần WebView) nên dựa vào giới hạn của API.

## 6. Giao diện

- Header tím gradient, ô tìm kiếm trắng nổi bật, nút Đăng ký/Đăng nhập, menu danh mục dạng viên thuốc; footer tím đậm 4 cột có điều khoản và chính sách.
- Thẻ sản phẩm, thanh bộ lọc, chip danh mục được làm lại; thanh "cam kết dịch vụ" đặt sau danh sách sản phẩm và chỉ nêu việc hệ thống thật sự làm. Không có số liệu bán hàng hay đánh giá giả. Giữ quy ước sản phẩm trước banner.

## 7. Kiểm chứng

- Backend: 148 test (gồm 6 đăng ký, 4 khôi phục/xác minh/giới hạn).
- E2E `tests/e2e/register.spec.ts` (6 test): kiểm tra form, đăng ký → tự đăng nhập → vai trò → đăng nhập lại, quên mật khẩu qua email thật tới Mailpit rồi đăng nhập mật khẩu mới, xác minh email rồi bật thông báo, admin bị từ chối ở form, trang pháp lý, chặn gọi chéo site.
- Mobile: 17 test, typecheck, `expo install --check`, export Android + iOS (bundle, không phải APK/IPA).
- Các E2E seller-orders/return-case nay ẩn sản phẩm mồi ở cuối để catalog dùng chung không bị tích tụ.

## 8. Giới hạn còn lại

1. **Điều khoản và chính sách là bản nháp** mô tả đúng hệ thống hiện tại; cần luật sư rà soát trước khi mở công khai.
2. **Chưa thử với nhà cung cấp SMTP và khóa Turnstile thật** (cần tài khoản và khóa của bạn). Đã kiểm bằng Mailpit và khóa thử nghiệm.
3. Email chưa xác minh khi mới đăng ký; người dùng phải tự xác minh nếu muốn nhận thông báo email.
4. Người ngoài vẫn thấy "email đã đăng ký" khi đăng ký trùng, cho phép dò email; giới hạn tốc độ và CAPTCHA giảm nhưng không xóa rủi ro.
5. Giới hạn của API nằm trong bộ nhớ từng phiên bản; khi chạy nhiều bản cần chuyển sang kho dùng chung.
6. Môi trường Keycloak đã có sẵn cần chạy `scripts/init-registration.ps1`, rồi khởi động lại API và web để nhận secret.
