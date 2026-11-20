# Giao diện seller/admin cho sản phẩm, tồn kho và duyệt hàng

Ngày: **20/11/2026**. Phạm vi: web Next.js (`apps/web`) dùng các API ở [docs/12](12-catalog-seller-api.md), [docs/13](13-ton-kho-va-giu-hang.md) và [docs/14](14-kiem-duyet-san-pham.md). **Chưa có UI trên app native**; seller và admin dùng web responsive như kế hoạch.

## 1. Màn hình

| Route                   | Vai trò                | Nội dung                                                                                                                           |
| ----------------------- | ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `/seller/products`      | Seller (shop đã duyệt) | Danh sách sản phẩm: trạng thái, khoảng giá SKU đang bán, tổng có thể bán, lý do bị từ chối                                         |
| `/seller/products/new`  | Seller                 | Tên, mô tả, danh mục, ảnh (thư viện có sẵn), tối đa 3 phân loại; bảng SKU tự sinh theo tổ hợp, mỗi dòng có mã SKU và giá           |
| `/seller/products/[id]` | Seller                 | Gửi duyệt/ẩn/hiện lại, bảng SKU (đổi giá, bật tắt bán, thêm SKU), hộp thoại tồn kho (nhập hàng, điều chỉnh, lịch sử), sửa nội dung |
| `/admin/products`       | Admin                  | Hàng đợi duyệt: nội dung, shop, SKU và giá; chấp thuận hoặc yêu cầu chỉnh sửa kèm lý do                                            |

Trang `/seller` và `/admin` có thanh điều hướng giữa hồ sơ gian hàng và sản phẩm. Seller chưa có gian hàng hoặc gian hàng chưa được duyệt được hướng về `/seller` thay vì thấy công cụ không dùng được.

## 2. Hành vi đáng chú ý

- **Chống nhập kho trùng khi mất kết nối.** Mỗi lần lưu tồn dùng một `Idempotency-Key`; khóa được giữ nguyên cho tới khi thao tác thành công hoặc người dùng đổi nội dung, nên bấm lại sau lỗi mạng không cộng hai lần.
- **Phiên bản.** Mọi lệnh sửa gửi `expectedVersion` của dữ liệu đang xem. Admin duyệt đúng phiên bản đã xem; nếu seller sửa trong lúc đó, quyết định bị từ chối 409 và hàng đợi tự tải lại.
- Thông báo "Đã lưu" của form nội dung chỉ hiện sau khi trang đã tải lại dữ liệu mới, để thao tác kế tiếp (ví dụ Gửi duyệt) không dùng phiên bản cũ.
- Giá nhập bằng số nguyên VND (kiểm ở trình duyệt, backend vẫn là nơi quyết định); `150000.5` bị từ chối.
- Seller được cảnh báo rõ rằng sửa tên/mô tả/danh mục/ảnh của sản phẩm đang bán sẽ đưa nó về chờ duyệt.
- Bảng SKU chuyển thành thẻ xếp dọc ở màn hình hẹp; hộp thoại tồn kho dùng Radix Dialog (focus, Escape, tên truy cập).

## 3. BFF

`/api/bff/[...path]` giờ dùng danh sách route cho phép theo từng phương thức, kèm giới hạn kích thước riêng (tạo sản phẩm tối đa 128 KiB, các lệnh khác 1–16 KiB). Mới có thêm: các route catalog, inventory, duyệt sản phẩm, và `GET` cho số dư/lịch sử tồn.

- Lệnh ghi vẫn bắt buộc `Origin` đúng; `GET` chấp nhận `Origin` đúng hoặc `Sec-Fetch-Site: same-origin`, còn lại 403.
- Chỉ header `Idempotency-Key` hợp lệ (8–100 ký tự an toàn) được chuyển tiếp; header khác của client không được.
- Route không nằm trong danh sách trả 404 mà không chạm API. Java vẫn kiểm quyền ở mọi endpoint.

Kiểu TypeScript cho các endpoint nằm trong `packages/contracts` (viết tay như phần còn lại; OpenAPI vẫn là backlog).

## 4. Backend bổ sung

`SellerProductResponse` có thêm `shopName` (join đọc từ `merchant.shop`) để admin biết sản phẩm của gian hàng nào.

## 5. Giới hạn

1. Ảnh chỉ chọn từ 6 ảnh có sẵn; chưa có tải ảnh, scan hay thư viện media.
2. Không sửa được phân loại/giá trị sau khi tạo; không xóa SKU (chỉ ngừng bán).
3. Danh sách seller chỉ hiện 20 sản phẩm mới nhất, chưa có phân trang, tìm kiếm hay lọc theo trạng thái. Hàng đợi admin tương tự.
4. Chưa có thông báo cho seller khi sản phẩm được duyệt hoặc bị từ chối; seller phải mở lại trang. Chưa có lịch sử duyệt trên giao diện (dữ liệu có ở `catalog.product_review`).
5. Seller chưa xem được tồn theo nhiều SKU trong một bảng, nhập hàng loạt hay CSV.
6. Chưa kiểm bằng screen reader hay thiết bị thật; accessible name, nhãn form và thông báo lỗi (`role="alert"`) đã được dùng nhưng chỉ kiểm bằng trình duyệt tự động.

## 6. Kiểm chứng

- `npm run typecheck`, `npm run build`, `npm run format:check` đạt.
- Backend 61 test đạt sau khi thêm `shopName`.
- Playwright: **14 test đạt** trên production build, API và Keycloak thật (12 test cũ và 2 test mới `seller-catalog.spec.ts`):
  - seller tạo sản phẩm hai phân loại (lỗi mã SKU trống và giá lẻ bị chặn trước khi gửi), nhập kho 12, thử giảm 50 bị từ chối, gửi duyệt;
  - admin từ chối kèm lý do, seller thấy lý do, sửa và gửi lại, admin chấp thuận;
  - khách chưa đăng nhập thấy sản phẩm trên trang chủ với giá thấp nhất; đổi giá không cần duyệt lại; ẩn thì biến mất khỏi trang chủ;
  - màn hình 390 px không tràn ngang ở danh sách, thêm mới, chi tiết và hàng đợi admin;
  - chưa đăng nhập thấy lời mời đăng nhập, buyer bị chặn ở admin, gọi BFF khác nguồn hoặc route không cho phép bị từ chối.
- Ảnh đã xem trực tiếp: `.local/screenshots/seller-product-detail.png`, `seller-stock-dialog.png`, `admin-product-review.png`, `seller-mobile-{list,new,detail}.png`.
- Migration V1003 đã được áp dụng lên DB local `vshop` đang có dữ liệu (nâng cấp từ V1002) khi khởi động API.

Lưu ý khi chạy E2E: test tạo người dùng Keycloak, gian hàng và sản phẩm có tiền tố `E2E`/`Cốc E2E` trong DB local và giữ lại để kiểm persistence. Test ẩn sản phẩm khi kết thúc để trang chủ vẫn chỉ có 6 sản phẩm mẫu; nếu một lần chạy hỏng giữa chừng, sản phẩm `ACTIVE` còn lại phải được ẩn thủ công, nếu không các test catalog cũ đếm sai số sản phẩm. Không chạy các test này vào môi trường thật.
