# Catalog seller API — sản phẩm, biến thể và SKU

Ngày: **20/11/2026**. Phạm vi: backend API ghi catalog cho seller (backlog C06, **InProgress**). Chưa có UI editor, chưa upload ảnh, kiểm duyệt xem [docs/14](14-kiem-duyet-san-pham.md), tồn kho xem [docs/13](13-ton-kho-va-giu-hang.md).

## 1. Endpoint

Tất cả nằm dưới `/api/v1/seller/shops/{shopId}/products`, cần đăng nhập (SELLER/BUYER) và phải là **owner của shop** (pilot: một owner/shop). Shop không thuộc về người gọi trả 404 như các API shop hiện có; shop chưa `APPROVED` trả 409 `INVALID_SHOP_STATE`.

| Method | Đường dẫn                   | Mục đích                                                                        |
| ------ | --------------------------- | ------------------------------------------------------------------------------- |
| GET    | `/`                         | Danh sách sản phẩm của shop (kèm option, SKU, tồn bán được), `page`/`size ≤ 50` |
| GET    | `/{productId}`              | Chi tiết                                                                        |
| POST   | `/`                         | Tạo sản phẩm **DRAFT** cùng chiều phân loại và SKU, một transaction             |
| PUT    | `/{productId}`              | Sửa tên, mô tả, danh mục, ảnh; cần `expectedVersion`                            |
| PUT    | `/{productId}/status`       | `DRAFT→ACTIVE`, `ACTIVE→HIDDEN`, `HIDDEN→ACTIVE`; cần `expectedVersion`         |
| POST   | `/{productId}/skus`         | Thêm SKU từ các option đã có                                                    |
| PUT    | `/{productId}/skus/{skuId}` | Đổi giá và trạng thái bán `ACTIVE/INACTIVE`; cần `expectedVersion`              |

Buyer chỉ thấy sản phẩm `ACTIVE` của shop `APPROVED` có ít nhất một SKU `ACTIVE`; giá hiển thị là giá nhỏ nhất của SKU đang bán (đã kiểm bằng test).

## 2. Quy tắc đã triển khai

- **Biến thể:** tối đa 3 chiều, 30 giá trị/chiều, 100 SKU/sản phẩm. Mỗi SKU chọn đúng một giá trị cho mọi chiều; không có chiều thì sản phẩm có đúng một SKU (`DEFAULT`). `variant_signature` do server dựng theo thứ tự mã chiều đã sắp xếp, nên cùng tổ hợp luôn cho cùng chữ ký. Trùng tổ hợp hoặc trùng mã SKU trong shop → 409 `DUPLICATE_SKU`, và toàn bộ transaction rollback (không ghi dở).
- **Tiền:** số nguyên VND, 1 đến 1.000.000.000 (an toàn cho JavaScript). Jackson được cấu hình `accept-float-as-int: false` nên `1000.5` bị từ chối thay vì bị cắt thành 1000.
- **Đồng thời:** server khóa dòng sản phẩm `FOR UPDATE` cho mọi lệnh ghi. Sửa dựa vào `expectedVersion` (optimistic); lệch version → 409 `VERSION_CONFLICT`. Không thể ngừng bán SKU cuối cùng của sản phẩm đang `ACTIVE`; muốn vậy phải ẩn sản phẩm trước. Không thể hiển thị sản phẩm khi chưa có SKU đang bán.
- **Slug:** sinh từ tên (bỏ dấu, thêm 8 ký tự UUID), không đổi khi sửa tên để giữ URL.
- **Audit/outbox:** `ProductCreated`, `ProductUpdated`, `ProductStatusChanged`, `SkuAdded`, `SkuUpdated` ghi cùng transaction. Hiện `resource_id` là **shop**, mã sản phẩm nằm trong `reason`, vì payload outbox hiện tại chỉ có `shopId`; nên mở rộng sự kiện có `aggregateType` trước khi có consumer thật.
- **Ranh giới module:** `SellerCatalogService` gọi `ShopService` (quyền sở hữu) và `AccountService`; SQL nằm ở `JdbcSellerCatalogRepository`; ArchUnit 8/8 đạt. Số tồn trong response là join đọc có chủ đích sang `inventory`, catalog **không ghi** tồn.

## 3. Giới hạn và quyết định còn mở

1. **Chưa có kiểm duyệt (C08).** Seller tự `DRAFT→ACTIVE`; luồng F02 trong tài liệu nghiệp vụ yêu cầu `PENDING_REVIEW`. Sửa nội dung sản phẩm đang bán cũng chưa quay lại hàng đợi duyệt. Cần chốt trước khi mở cho seller thật.
2. **Ảnh** chỉ chấp nhận 6 khóa ảnh fixture (`lamp`, `tote`, `mug`, `headphones`, `bottle`, `notebook`) vì chưa có upload/scan; thay bằng media asset khi có object storage.
3. **Option bất biến sau khi tạo:** thêm SKU chỉ dùng giá trị sẵn có; chưa thêm/xóa chiều hoặc giá trị mới.
4. **Chưa có thuộc tính theo ngành (C05)**, cân nặng/kích thước, hay đổi danh mục có kiểm tra thuộc tính.
5. **Tồn kho:** chưa có API tạo/điều chỉnh tồn (C09/C10); SKU mới có `available = 0` cho tới khi inventory command được xây. Đây là bước kế tiếp.
6. **Staff** (`CATALOG_MANAGER`) chưa được dùng; chỉ owner ghi được.
7. `packages/contracts`, web và mobile chưa có kiểu/UI cho các endpoint này.

## 4. Kiểm chứng

`scripts/test-backend.ps1`: **44 test đạt** (thêm 10 test `SellerCatalogIntegrationTests` trên PostgreSQL thật: tạo biến thể → publish → giá buyer thấy; cô lập giữa seller và giữa shop; shop chưa duyệt; biến thể sai/trùng không ghi dở; mã SKU duy nhất theo shop; thêm SKU; SKU cuối; version cũ và máy trạng thái; tồn hiển thị và giới hạn `size`). Test đầu tiên phát hiện `@Validated` ở class làm lỗi `size=51` thành 500; đã bỏ, validation tham số trả 400 thống nhất. Chưa chạy E2E trình duyệt vì chưa có UI.
