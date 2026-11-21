# Đăng ký tài khoản trong site và giao diện mới

Ngày: **21/11/2026**. Phạm vi: người mua tự đăng ký bằng form ngay trên VShop (không chuyển sang trang Keycloak), trang đăng nhập Keycloak được thiết kế lại cho cùng phong cách, và giao diện cửa hàng được làm lại.

## 1. Luồng đăng ký

```text
/register (form) → BFF /api/auth/register → API POST /api/v1/auth/register
                                              → Keycloak Admin API (tạo user + vai trò BUYER)
→ "Tạo tài khoản thành công" → "Đăng nhập ngay" (email điền sẵn) → đăng nhập → về trang chủ
```

- **Form** (`/register`): họ tên, email, mật khẩu, nhập lại mật khẩu, đồng ý điều khoản. Kiểm tra từng ô khi rời ô và khi gửi, danh sách yêu cầu mật khẩu cập nhật trực tiếp, nút hiện/ẩn mật khẩu, lỗi gắn với đúng ô (`aria-invalid`, `role="alert"`).
- **API là nơi quyết định.** Mọi quy tắc được kiểm lại ở backend; form chỉ giúp phản hồi nhanh.

| Trường | Quy tắc |
|---|---|
| Họ tên | 2–60 ký tự, bắt đầu bằng chữ; chỉ chữ (có dấu), khoảng trắng, `.`, `'`, `-`; chuẩn hóa NFC (gõ dấu tổ hợp vẫn đúng) |
| Email | định dạng hợp lệ, tối đa 254 ký tự, đưa về chữ thường; là tên đăng nhập |
| Mật khẩu | 8–64 ký tự, có chữ cái và chữ số, không bắt đầu/kết thúc bằng khoảng trắng, không trùng email hoặc phần trước `@` |
| Điều khoản | bắt buộc tick |

- **Kết quả:** `201` tạo xong; `400 INVALID_REGISTRATION_INPUT` sai dữ liệu (kèm thông báo tiếng Việt); `409 EMAIL_ALREADY_REGISTERED` email đã có; `429` thử quá nhiều; `503 REGISTRATION_UNAVAILABLE` chưa cấu hình hoặc Keycloak không phản hồi.
- **Vai trò:** chỉ `BUYER`. Người bán và quản trị viên không bao giờ được tạo bằng đường này (có test kiểm tra vai trò thật trong Keycloak). Người bán vẫn mở gian hàng từ tài khoản mua như trước.
- **Mật khẩu** đi qua BFF → API → Keycloak đúng một lần; không ghi log (`RegisterRequest.toString()` bỏ mật khẩu), không lưu trong database của VShop. Hàng `identity.account` vẫn chỉ được tạo ở lần gọi API đầu tiên sau khi đăng nhập.
- **Chống lạm dụng:** BFF kiểm Origin, giới hạn 10 lần/giờ/địa chỉ và 5 lần/giờ/email (Redis; Redis lỗi thì từ chối, không bỏ qua giới hạn). Địa chỉ lấy từ `X-Forwarded-For`, nên khi triển khai phải đặt sau proxy tin cậy ghi đè header này.
- **Keycloak:** client `vshop-provisioner` (confidential, chỉ service account, không đăng nhập được) với quyền tối thiểu `manage-users`, `view-users`, `view-realm`. Secret `KEYCLOAK_PROVISIONER_SECRET` chỉ nằm trong `.env`. User tạo xong mà không gán được BUYER sẽ bị xóa lại, không để lại tài khoản dở dang. Chính sách mật khẩu của realm: `length(8) and digits and lowerCase and notUsername`.

## 2. Đăng nhập

Vẫn dùng Authorization Code + PKCE như cũ (an toàn nhất cho cookie phiên và MFA của admin), nhưng trang đăng nhập của Keycloak nay là **theme `vshop`**: nền sáng, cùng màu và phông Manrope, tiếng Việt (Keycloak không kèm bản tiếng Việt nên `messages_vi.properties` do dự án viết), có liên kết "Đăng ký ngay" về `/register`. Sau khi đăng ký, email được điền sẵn (`login_hint`). Tên hiển thị trên site là họ tên thật nếu có.

Chưa làm: tự đăng nhập ngay sau khi đăng ký (người dùng nhập lại mật khẩu một lần), form đăng nhập nằm hẳn trong site (cần luồng mật khẩu trực tiếp, bỏ qua trang MFA của admin; chưa chọn làm).

## 3. Giao diện

- Header nền tím gradient, ô tìm kiếm trắng nổi bật, nút Đăng ký/Đăng nhập, mục danh mục dạng viên thuốc; footer tím đậm 4 cột.
- Thẻ sản phẩm bo tròn lớn, giá màu thương hiệu, tên gian hàng có biểu tượng; thanh bộ lọc nằm trong một thẻ; chip danh mục đang chọn nổi bật.
- Thanh "cam kết dịch vụ" đặt **sau** danh sách sản phẩm; chỉ nêu các việc hệ thống thật sự làm (COD/thanh toán online, theo dõi đơn, hoàn tiền/trả hàng, người bán được duyệt). Không có số liệu bán hàng, đánh giá hay "đã bán" giả.
- Giữ quy ước dự án: sản phẩm và tìm kiếm/danh mục nằm ở màn hình đầu, banner nằm sau.

## 4. Kiểm chứng

- Backend: 6 test tích hợp đăng ký (hợp lệ, tên NFC, các dạng sai, email trùng, mật khẩu bị Keycloak từ chối, 503). Đăng ký thật với Keycloak local bằng form, đăng nhập lại, có vai trò BUYER.
- E2E (`tests/e2e/register.spec.ts`): kiểm tra trên trình duyệt, tạo → đăng nhập → vai trò → xóa user; chặn gọi chéo site.
- Đã xem giao diện desktop và mobile (375 px).

## 5. Giới hạn

1. Email **chưa được xác minh** (`emailVerified=false`), nên người dùng chưa bật được thông báo email (cần email đã xác minh). Cần máy chủ SMTP cho Keycloak để gửi mail xác minh và quên mật khẩu.
2. "Quên mật khẩu" chưa bật (cũng cần SMTP).
3. Chưa có CAPTCHA; chống lạm dụng mới là giới hạn tốc độ.
4. Chưa có trang điều khoản và chính sách bảo mật thật: ô đồng ý hiện chưa có liên kết đến văn bản.
5. Người dùng bên ngoài thấy thông báo "email đã đăng ký", cho phép dò email có tài khoản hay chưa; giới hạn tốc độ giảm nhưng không xóa rủi ro này.
6. Cần chạy `scripts/init-registration.ps1` trên môi trường Keycloak đã có sẵn (môi trường mới được `init-local.ps1` cấu hình sẵn), rồi khởi động lại API để nạp `KEYCLOAK_PROVISIONER_SECRET`.
