# Phiên đăng nhập, refresh token và MFA cho admin

Ngày: **20/11/2026**. Phạm vi: web (BFF), Keycloak và API Java (backlog B04, B05, B06 vẫn **InProgress**). App native đã có refresh riêng từ trước và không thay đổi.

## 1. Vì sao đổi cách lưu phiên

Cookie phiên cũ chứa trực tiếp access token và id token mã hóa (JWE) và đã dài khoảng **3,5 KB**, sát giới hạn 4 KB của cookie. Thêm refresh token là không vừa. Vì vậy phiên được chuyển sang **kho phía server (Redis)**:

- Trình duyệt chỉ giữ cookie `vshop_session` = 43 ký tự ngẫu nhiên (HttpOnly, SameSite=Lax). Token không bao giờ rời server, kể cả refresh token.
- Bản ghi trong Redis được mã hóa bằng `SESSION_SECRET` (AES-256-GCM) trước khi lưu; Redis chỉ chứa dữ liệu đã mã hóa.
- Hết hạn: **30 phút không hoạt động** (theo SSO idle của Keycloak, được gia hạn mỗi lần refresh) và tối đa **8 giờ** kể từ lúc đăng nhập, bất kể hoạt động.
- Đăng xuất xóa bản ghi trước rồi mới gọi Keycloak, nên một cookie bị sao chép sẽ vô dụng ngay cả khi Keycloak không truy cập được. Test E2E xác nhận điều này.
- **Redis mất hoặc không truy cập được** thì người dùng bị coi là chưa đăng nhập và phải đăng nhập lại. Không có dữ liệu đơn, tồn hay tiền nằm trong Redis, đúng với nguyên tắc ở tài liệu kiến trúc.

## 2. Refresh token

`getSession()` (dùng ở layout, trang, route handler) kiểm tra access token còn dưới 30 giây thì làm mới bằng refresh token ngay trong server. Vì cookie không đổi nên cách này dùng được cả trong Server Component (nơi không thể ghi cookie).

- **Một lần làm mới cho nhiều request cùng lúc:** request đầu lấy khóa Redis (`SET NX`, 10 giây), các request khác đợi kết quả; khóa chỉ bị xóa bởi chính chủ. Kết quả được memoize theo request để layout, trang và lời gọi API dùng chung một lần đọc.
- Keycloak trả `4xx` (ví dụ `invalid_grant`, phiên SSO đã kết thúc) → xóa phiên và coi là đăng xuất. Lỗi mạng hoặc `5xx` → giữ phiên và thử lại ở request sau; người dùng không bị đăng xuất vì Keycloak chập chờn.
- Sau mỗi lần làm mới: kiểm lại chữ ký, issuer, audience và `sub` phải trùng; **role và trạng thái MFA được tính lại từ token mới**, nên việc thu hồi role có hiệu lực trong vòng một access token.
- Realm đặt `revokeRefreshToken = false` (refresh token không dùng một lần) để các lần làm mới song song không làm Keycloak hủy phiên; khóa Redis giảm trùng lặp ngay cả khi vậy.

## 3. MFA bắt buộc cho admin

**Keycloak:** luồng đăng nhập trình duyệt `browser-admin-mfa` = cookie SSO **hoặc** (tên đăng nhập/mật khẩu, rồi OTP khi người dùng có role ADMIN). Admin chưa có ứng dụng xác thực bị buộc thiết lập ở lần đăng nhập đầu và không có cách bỏ qua. Token mang claim `amr` (`pwd`, `otp`) nhờ mapper `oidc-amr-mapper` trên client `vshop-web` và `vshop-mobile`. Cấu hình có hai đường, đã kiểm cả hai trên Keycloak 26.4.7:

1. `infra/keycloak/realm-template.json` cho realm mới (đã import vào một Keycloak thử và đăng nhập thật);
2. `scripts/init-admin-mfa.ps1` cho realm đã tồn tại (idempotent, tự dọn nếu dở dang, tự gia hạn token quản trị 60 giây; đã chạy trên một realm cũ thử, rồi trên realm local). Script cũng đặt SSO idle 30 phút, tối đa 8 giờ và **đăng xuất mọi phiên đang có**, vì cookie SSO cũ sẽ bỏ qua bước OTP.

**API Java:** `/api/v1/admin/**` yêu cầu role ADMIN **và** `amr` chứa `otp`. Token admin chỉ có mật khẩu bị từ chối `403` với mã `MFA_REQUIRED` (cấu hình `vshop.security.admin-mfa-required`, mặc định bật; chỉ tắt trong một số test tích hợp không liên quan). Người mua có OTP vẫn không phải admin và không nhận gợi ý MFA.

**Web:** admin chưa có OTP trong phiên thấy màn "Cần xác thực hai bước" có nút đăng xuất để đăng nhập lại, thay vì lỗi trống.

### Hành vi cần biết

- **Phiên ngay sau khi thiết lập authenticator chưa có `amr=otp`.** Keycloak chỉ ghi `otp` khi người dùng nhập mã ở bước xác thực, không ghi khi thiết lập. Admin phải đăng xuất và đăng nhập lại một lần; UI chỉ rõ điều này. Đã quan sát trực tiếp, không suy đoán.
- `amr` được giữ nguyên qua các lần refresh của cùng phiên (đã kiểm).
- Mã OTP không dùng lại được trong cùng khoảng 30 giây; Keycloak chấp nhận lệch một bước.
- MFA chỉ được kiểm khi đăng nhập, chưa yêu cầu nhập lại OTP cho thao tác nhạy cảm (re-auth khi đổi tài khoản ngân hàng, duyệt chi) — việc của các module tài chính sau.
- Mọi tài khoản ADMIN bị ép OTP, kể cả tài khoản `admin.local` dùng cho phát triển.

## 4. Vận hành local

- `init-local.ps1` thêm `REDIS_URL` vào `apps/web/.env.local`. Môi trường đã tạo từ trước cần thêm dòng này (đã làm trên máy này).
- Realm local cũ: chạy `pwsh -File scripts/init-admin-mfa.ps1` một lần. Sau đó `admin.local` được hỏi thiết lập TOTP ở lần đăng nhập kế tiếp.
- **E2E:** helper `tests/e2e/support/auth.ts` thiết lập TOTP lần đầu, lưu khóa và các bước thời gian đã dùng vào `.local/totp.json` (Git ignore, giống `accounts.json`), rồi tự đăng xuất/đăng nhập lại để có phiên `amr=otp`. Muốn dùng `admin.local` với ứng dụng xác thực thật, thêm khóa trong file này vào ứng dụng, hoặc xóa OTP bằng `scripts/reset-otp.ps1 -Username admin.local` và tự thiết lập (khi đó test sẽ báo thiếu khóa cho tới khi chạy lại bước thiết lập).
- Production cần: Redis có mật khẩu/TLS, không dùng chung với dữ liệu giao dịch; nhiều admin thì mỗi người tự thiết lập; kênh khôi phục khi mất thiết bị (hiện chỉ có `reset-otp.ps1` cho local).

## 5. Giới hạn còn lại

1. Chưa có đăng xuất mọi thiết bị theo yêu cầu người dùng, hay danh sách phiên đang hoạt động để thu hồi (có thể làm trên kho Redis: liệt kê theo `subject`).
2. Chưa diễn tập xoay khóa ký (B04) và chưa kiểm key rotation của JWKS đang được cache.
3. Chưa có mã khôi phục (recovery code) hay WebAuthn; chỉ TOTP.
4. Chưa rate-limit đăng nhập ngoài `bruteForceProtected` của Keycloak.
5. App native chưa kiểm với tài khoản ADMIN (luồng PKCE dùng chung realm nên cũng bị ép OTP).
6. Không có test tải; khóa làm mới 10 giây và thời gian chờ 3,75 giây là giá trị chọn ban đầu.

## 6. Kiểm chứng

- Backend **76 test đạt** (thêm 4 test `AdminMfaEnforcementTests` trên toàn bộ filter chain: admin không `amr`/chỉ `pwd` bị 403 `MFA_REQUIRED` trên mọi endpoint admin gồm lệnh ghi, có `otp` thì qua, buyer có OTP không thành admin, anonymous 401, endpoint khác không đòi OTP).
- Playwright **19 test đạt** trên bản build production với API, Keycloak và Redis thật. Ba test mới `session-mfa.spec.ts`:
  - cookie là mã mờ 43 ký tự, HttpOnly, không đọc được bằng JS; access token hết hạn được làm mới và cookie không đổi; 6 request đồng thời đều thành công; refresh token sai thì xóa phiên;
  - quá 8 giờ thì phiên bị bỏ; đăng xuất xóa bản ghi nên cookie sao chép vô hiệu;
  - admin mới tạo bị buộc thiết lập OTP, phiên đầu bị chặn ở cả UI và API, mã sai không tạo phiên, mã đúng mở khu quản trị, và OTP vẫn còn sau refresh. Người dùng thử được xóa cuối bài.
- Các test E2E cũ dùng admin nay đi qua OTP thật (không bỏ qua MFA trong test).
- Trong lúc kiểm: lần đầu sau khi khởi động, ioredis từ chối lệnh gửi trước khi kết nối xong (`enableOfflineQueue: false`) nên đăng nhập đầu tiên báo lỗi. Đã bật hàng đợi offline; test đã bắt được lỗi này.
- Dependency mới: `ioredis` 5.11.1 (cố định phiên bản). `npm audit` không tăng cảnh báo (20 cảnh báo moderate có sẵn thuộc cây công cụ jest).
