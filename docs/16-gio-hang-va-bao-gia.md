# Giỏ hàng và báo giá

Ngày: **20/11/2026**. Phạm vi: backend module `checkout` (giỏ và báo giá) cùng UI web cho buyer (backlog D01 và D02, **InProgress**). **Chưa có đặt hàng, thanh toán, vận chuyển hay voucher.** Giỏ và báo giá không giữ tồn, không tạo đơn.

## 1. API (buyer)

Cần đăng nhập với vai trò BUYER hoặc SELLER; tài khoản lấy từ token, không bao giờ từ request. ADMIN nhận 403.

| Method | Đường dẫn                                    | Mục đích                                                                                |
| ------ | -------------------------------------------- | --------------------------------------------------------------------------------------- |
| GET    | `/api/v1/cart`                               | Giỏ hiện tại, tính giá và tình trạng hàng ngay lúc này                                  |
| POST   | `/api/v1/cart/lines`                         | `{skuId, quantity}` cộng thêm số lượng (tối đa 99/dòng, không idempotent theo thiết kế) |
| PUT    | `/api/v1/cart/lines/{skuId}`                 | Đặt số lượng tuyệt đối (idempotent; đặt lại đúng số lượng cũ không làm đổi `version`)   |
| DELETE | `/api/v1/cart/lines/{skuId}`, `/api/v1/cart` | Xóa một dòng hoặc cả giỏ                                                                |
| POST   | `/api/v1/checkout/quotes`                    | Chốt báo giá từ giỏ hiện tại                                                            |
| GET    | `/api/v1/checkout/quotes/{id}`               | Xem báo giá (chỉ của chính mình; của người khác trả 404)                                |
| GET    | `/api/v1/catalog/products/{slug}/skus`       | Công khai: các SKU đang bán của sản phẩm hiển thị (nhãn phân loại, giá, còn bao nhiêu)  |

## 2. Quy tắc đã triển khai

- **Giỏ chỉ là ý định mua (BR03).** Lưu SKU và số lượng; không lưu giá, không giữ tồn. Một giỏ mỗi tài khoản; tối đa 50 dòng khác nhau, 99 mỗi dòng (vượt thì cộng dồn bị chặn ở 99). Thêm vào giỏ chỉ yêu cầu SKU đang bán (SKU `ACTIVE`, sản phẩm `ACTIVE`, shop `APPROVED`); hết hàng vẫn thêm được nhưng bị gắn cảnh báo.
- **Giá luôn do server tính (BR04).** API không có trường giá để client gửi; một `unitPrice` gửi kèm bị bỏ qua (có test). Đơn giá lấy từ catalog hiện tại, số lượng sellable từ tồn kho, rồi gom theo shop. Tiền là số nguyên VND, nhân và cộng có kiểm tràn số (`Math.multiplyExact/addExact`); đã kiểm biên 99 × 1.000.000.000.
- **Cảnh báo theo dòng:** `UNAVAILABLE` (không còn bán), `OUT_OF_STOCK`, `INSUFFICIENT_STOCK` kèm `availableQuantity`. `subtotal` và `itemsTotal` chỉ cộng các dòng không có cảnh báo; `canCheckout` chỉ đúng khi giỏ có hàng và mọi dòng đều ổn.
- **Chưa tính phí vận chuyển, giảm giá, thuế.** Mọi phản hồi mang `notCalculated: ["SHIPPING","DISCOUNT","TAX"]` và UI ghi rõ "chưa gồm", để `itemsTotal` không bị hiểu là số phải trả.
- **Báo giá bất biến và có hạn.** Lưu snapshot JSONB của giỏ đã tính cùng `cart_version` và hạn. Mỗi lần giỏ thay đổi, `version` của giỏ tăng, nên báo giá cũ có `current = false`; hết hạn cũng vậy. Thay đổi giá catalog sau đó không làm đổi báo giá đã chốt (có test). Checkout sau này phải yêu cầu `current = true` và tính lại ở phía server.
- **Hạn báo giá** mặc định 10 phút (`vshop.checkout.quote-ttl`, ISO-8601). Đây là **đề xuất chưa được chốt**; tính theo giờ DB.
- **Đồng thời:** mọi lệnh ghi khóa dòng giỏ `FOR UPDATE` và tăng `version` trong cùng transaction; tạo giỏ lần đầu dùng `ON CONFLICT`. Test 8 luồng cùng thêm một SKU: số lượng đúng 8, `version` đúng 8, chỉ một giỏ.
- Thứ tự shop và dòng ổn định theo thời điểm thêm (dùng `clock_timestamp()` vì các dòng thêm trong một transaction sẽ trùng `now()`).

## 3. Giao diện web

- **Trang chi tiết sản phẩm:** chọn lựa chọn (nếu có nhiều SKU) và số lượng rồi "Thêm vào giỏ"; hết hàng thì khóa nút. Chưa đăng nhập thì hiện nút đăng nhập, quay lại đúng trang sản phẩm sau khi đăng nhập (đường quay lại chỉ cho phép `/seller`, `/admin`, `/cart` và `/products/<slug>`). Thông báo "Chưa mở bán trực tuyến" đã đổi thành "Chưa mở thanh toán".
- **Header:** biểu tượng giỏ kèm số lượng, chỉ hiện với tài khoản mua được. Lỗi khi lấy giỏ không làm hỏng trang.
- **`/cart`:** gom theo shop, tăng/giảm/xóa từng dòng, xóa cả giỏ, cảnh báo từng dòng, tóm tắt và nút "Chốt báo giá". Báo giá hiện "Giá được giữ đến HH:mm"; nếu giỏ đổi sau đó hoặc quá hạn thì hiện cảnh báo không còn hiệu lực. **Không có nút đặt hàng hay thanh toán**; trang nói rõ báo giá chưa tạo đơn và chưa giữ hàng.
- BFF thêm các route giỏ và báo giá vào danh sách cho phép, kèm phương thức `DELETE`.

## 4. Giới hạn và quyết định còn mở

1. **Không có địa chỉ, vận chuyển, voucher, thuế.** Backlog D02 yêu cầu tính lại khi đổi địa chỉ; chưa làm được vì chưa có địa chỉ (C01) hay carrier. Báo giá hiện chỉ là tổng tiền hàng.
2. **Hạn báo giá 10 phút chưa được duyệt.** Cần chốt cùng thời gian giữ hàng khi thanh toán (BR13).
3. Chưa có bảng `price_policy`/`policy_version` nên báo giá chưa ghi chính sách giá đã dùng (cột đã được chừa trong DDL tham chiếu).
4. Người bán vẫn mua được sản phẩm của chính mình; chưa có luật cấm.
5. Giỏ chưa gộp từ thiết bị chưa đăng nhập; chưa có app native cho giỏ hàng.
6. Chưa dọn quote hết hạn (bảng chỉ tăng); cần job dọn/lưu giữ khi có dữ liệu thật.
7. Giỏ không tự gỡ SKU đã ngừng bán; người mua thấy cảnh báo và tự xóa.

## 5. Kiểm chứng

- Backend: **72 test đạt** (thêm 10 test `CartQuoteIntegrationTests` và 1 test `CartConcurrencyTests`): gom theo shop và giá từ catalog, bỏ qua giá client, cảnh báo tồn mà không giữ tồn, SKU không bán/shop chưa duyệt, giới hạn số lượng và 50 dòng, biên tiền, cô lập giữa buyer và vai trò (401/403), `version` chỉ tăng khi có thay đổi, báo giá bất biến/hết hạn/stale và không đọc được của người khác, danh sách SKU công khai.
- Playwright: **16 test đạt** (14 cũ + 2 mới trong `cart.spec.ts`) trên bản build production với API và Keycloak thật: thêm hai sản phẩm từ trang chi tiết, tổng 1.107.000 → 1.296.000 → 918.000 sau khi tăng/xóa, chốt báo giá rồi thấy nó mất hiệu lực khi đổi giỏ, giỏ giữ nguyên sau khi tải lại, 390 px không tràn ngang, khách chưa đăng nhập bị yêu cầu đăng nhập, BFF từ chối gọi khác nguồn (403) và chưa đăng nhập (401). Ảnh đã xem: `.local/screenshots/cart-desktop.png`, `cart-mobile.png`.
- Migration **V1004** (schema `checkout` và 3 bảng, chỉ thêm) đã được áp dụng lên DB local đang có dữ liệu khi khởi động API; `verify-db-design.ps1` dựng mới từ trắng đạt và dictionary đã xuất lại (21 bảng đã triển khai, 35 bảng tham chiếu). `checkout.cart`, `cart_line`, `quote` đã được chuyển ra khỏi `target-commerce.sql`.
- E2E dùng tài khoản `buyer.local` và giỏ của họ; test tự xóa giỏ ở cuối.
