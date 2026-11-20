# Sổ địa chỉ của buyer

Ngày: **20/11/2026**. Phạm vi: backend (module `identity`, `common/security`, `checkout`) và UI web (backlog C01, **InProgress**). Chưa có consent, chưa kiểm vùng giao hàng, chưa có app native.

## 1. API (chủ tài khoản)

Cần đăng nhập với vai trò BUYER hoặc SELLER; tài khoản lấy từ token, không bao giờ từ request. ADMIN nhận 403.

| Method | Đường dẫn                           | Mục đích                                                        |
| ------ | ----------------------------------- | --------------------------------------------------------------- |
| GET    | `/api/v1/me/addresses`              | Danh sách (mặc định đứng đầu)                                   |
| POST   | `/api/v1/me/addresses`              | Thêm; địa chỉ đầu tiên tự thành mặc định                        |
| PUT    | `/api/v1/me/addresses/{id}`         | Sửa, cần `expectedVersion`                                      |
| PUT    | `/api/v1/me/addresses/{id}/default` | Đặt mặc định                                                    |
| DELETE | `/api/v1/me/addresses/{id}`         | Xóa; nếu là mặc định thì địa chỉ mới nhất còn lại được nâng lên |
| POST   | `/api/v1/checkout/quotes`           | Nhận thêm `{addressId}` tùy chọn để gắn báo giá với địa chỉ     |

## 2. Quy tắc đã triển khai

- **Chủ sở hữu.** Mọi truy vấn kèm `account_id`; địa chỉ của người khác và địa chỉ không tồn tại đều trả 404 giống hệt nhau nên không dò được id (có test cho GET/PUT/DELETE/đặt mặc định/báo giá).
- **Số điện thoại:** chấp nhận `0912345678`, `091 234 5678`, `091.234.5678`, `+84912345678`, `84912345678` và lưu dạng chuẩn `0…`. Chỉ nhận di động Việt Nam 10 số đầu 03/05/07/08/09; số bàn và số khác bị từ chối.
- **Trường khác:** tên người nhận 2–100, địa chỉ cụ thể 5–200, phường/xã và tỉnh/thành bắt buộc ≤ 100, nhãn ≤ 30; khoảng trắng được gộp, ký tự điều khiển bị từ chối.
- **Tối đa 10 địa chỉ/tài khoản**; thêm/sửa/xóa/đặt mặc định được tuần tự hóa bằng khóa dòng tài khoản, và **index duy nhất** `uq_address_default` bảo đảm đúng một mặc định. Test 14 luồng cùng tạo: đúng 10 thành công, 4 bị từ chối, đúng 1 mặc định.
- **Phiên bản:** `version` chỉ tăng khi nội dung đổi, không tăng khi chỉ đổi mặc định, để một báo giá không mất hiệu lực vì buyer chọn địa chỉ mặc định khác.

## 3. Mã hóa dữ liệu cá nhân

Số điện thoại và địa chỉ cụ thể được mã hóa ở tầng ứng dụng (`PiiCipher`) trước khi vào PostgreSQL: AES-256-GCM, IV ngẫu nhiên 96 bit cho từng giá trị, định dạng `v1.<keyId>.<base64url>`. Dữ liệu xác thực kèm theo gắn mỗi bản mã với **bảng, cột và id dòng**, nên chép bản mã sang dòng hoặc cột khác sẽ giải mã thất bại (có test, kết quả là lỗi chứ không rò dữ liệu). Phong bì giả, bị sửa, sai phiên bản hoặc thiếu khóa đều báo lỗi, không bao giờ rơi về văn bản thường.

- **Khóa** lấy từ cấu hình (`vshop.pii.keys.<id>`, base64 32 byte; `active-key` cho dữ liệu mới); id khóa cũ vẫn đọc được nên xoay khóa không mất dữ liệu (có test). Local: `init-local.ps1` sinh `PII_KEY_LOCAL_1` vào `.env` (đã tạo trên máy này). Thiếu khóa thì API vẫn khởi động nhưng ghi cảnh báo và các thao tác lưu địa chỉ báo lỗi.
- **Bảo vệ được gì:** bản sao lưu hoặc quyền đọc DB. **Không bảo vệ** khi kẻ tấn công kiểm soát ứng dụng đang chạy cùng cấu hình của nó. Đây không phải KMS: khóa nằm trong biến môi trường, chưa có quản lý vòng đời khóa, và **mất khóa là mất dữ liệu** (cần sao lưu khóa riêng khỏi sao lưu DB).
- **Tỉnh và phường để đọc được** vì vùng giao hàng và phí vận chuyển sẽ suy ra từ đó; tên người nhận cũng để đọc được theo DDL tham chiếu.
- Báo giá, snapshot và log **không** chứa phone hay địa chỉ cụ thể: module khác chỉ nhận `ShipTo` (id, version, tỉnh, phường). Test xác nhận phản hồi và snapshot JSON của báo giá không chứa số điện thoại hay tên đường.

## 4. Báo giá gắn với địa chỉ (BR04)

Báo giá có thể gắn một địa chỉ của chính buyer; chỉ tỉnh và phường được sao chép vào báo giá. Báo giá **không còn `current`** khi hết hạn, khi giỏ đổi, khi địa chỉ được **sửa** (version đổi) hoặc bị **xóa**. Đổi mặc định không làm báo giá mất hiệu lực. Báo giá không địa chỉ vẫn hoạt động như trước. Nhưng vì chưa có phí vận chuyển, việc gắn địa chỉ chưa làm thay đổi con số nào; nó chuẩn bị đường cho checkout và carrier.

## 5. Giao diện web

- `/account/addresses`: thẻ địa chỉ, thêm/sửa trong hộp thoại, đặt mặc định, xóa có xác nhận; khung viền nhận diện địa chỉ mặc định. Menu tài khoản có mục "Sổ địa chỉ". Kiểm tra sơ bộ phía trình duyệt phản chiếu luật của server (server vẫn là nơi quyết định).
- `/cart`: chọn "Giao đến" (mặc định được chọn sẵn), nút "Chốt báo giá" bị khóa nếu chưa có địa chỉ và có liên kết thêm địa chỉ. Báo giá hiện "Giao đến: phường, tỉnh"; đổi địa chỉ đang chọn thì báo giá cũ được đánh dấu hết hiệu lực.
- Kiểu `Address` nằm trong `packages/contracts`.

## 6. Giới hạn và quyết định còn mở

1. **Chưa kiểm danh mục hành chính hay vùng giao hàng.** Tỉnh và phường là chữ tự do vì chưa có dữ liệu hành chính chính thức hoặc vùng phục vụ của carrier; BR01 ("địa chỉ phục vụ được mới vào checkout") chưa thể thực thi. Cần danh mục đơn vị hành chính hiện hành (sau các thay đổi sáp nhập) và dữ liệu carrier trước khi bật kiểm tra.
2. **Chưa có consent** (C01 nhắc tới) và chưa có chính sách lưu giữ/xóa dữ liệu cá nhân (BR31): xóa địa chỉ hiện là xóa cứng, chấp nhận được vì chưa có đơn; khi có đơn, snapshot địa chỉ tại lúc đặt sẽ cần giữ riêng.
3. Chưa có audit cho thay đổi địa chỉ (payload outbox hiện chỉ có `shopId`); chưa có giới hạn tốc độ riêng.
4. Chỉ nhận số di động; chưa có địa chỉ nước ngoài hay số bàn.
5. Chưa có app native cho sổ địa chỉ.
6. Chưa có công cụ mã hóa lại toàn bộ dữ liệu sang khóa mới (đọc nhiều khóa đã được hỗ trợ, ghi lại hàng loạt thì chưa).

## 7. Kiểm chứng

- Backend: **89 test đạt**, chạy lặp hai lần (thêm 5 test `PiiCipherTests`, 7 test `AddressIntegrationTests`, 1 test `AddressConcurrencyTests`): mã hóa khứ hồi và IV không lặp, ràng buộc dòng/cột, giá trị bị sửa/sai định dạng, xoay khóa, thiếu hoặc sai khóa; bản mã trong DB không chứa số hay tên đường; chép bản mã sang dòng khác làm đọc thất bại; chuẩn hóa số điện thoại và từ chối đầu vào xấu; cô lập giữa các buyer và vai trò; phiên bản và đặt mặc định; xóa mặc định, giới hạn 10; báo giá gắn địa chỉ (vùng thôi, stale khi sửa hoặc xóa, không stale khi đổi mặc định, địa chỉ của người khác bị 404).
- Playwright: **21 test đạt** trên bản build production với API, Keycloak, Redis thật (thêm 2 test `address.spec.ts`; `cart.spec.ts` nay tạo địa chỉ trước khi chốt báo giá và dọn sau). Ảnh đã xem: `.local/screenshots/address-book.png`, `address-book-mobile.png`; 390 px không tràn ngang.
- Lúc kiểm tra tìm ra: BFF trả 500 cho mọi phản hồi `204` (xóa thành công) vì không được có body; đã sửa. Thẻ địa chỉ giờ tự tải lại danh sách khi gặp 404/409.
- Migration **V1005** (bảng `identity.address`, cột nullable trên `checkout.quote`; chỉ thêm) đã áp dụng lên DB local đang có dữ liệu; `verify-db-design.ps1` dựng mới đạt và dictionary đã xuất lại (22 bảng đã triển khai, 34 bảng tham chiếu, 415 cột). Bảng `identity.address` được chuyển ra khỏi `target-commerce.sql` (cột mã hóa khác thiết kế tham chiếu cũ: tách `street_ciphertext` và tỉnh/phường đọc được thay cho `administrative_code`).
- Script khởi động nạp biến môi trường có chữ số trong tên (`PII_KEY_LOCAL_1`); trước đó regex `[A-Z_]+` bỏ qua nó và API chạy với cảnh báo "chưa cấu hình khóa".
