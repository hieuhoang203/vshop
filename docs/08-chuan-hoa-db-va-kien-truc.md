# Báo cáo chuẩn hóa database và kiến trúc code

Ngày: **19/11/2026**. Phạm vi: tái cấu trúc nền tảng hiện có và thiết kế DB cho các domain tiếp theo.

## 1. Các vấn đề đã sửa

| Trước | Sau |
|---|---|
| CatalogController chứa SQL | Controller chỉ nhận request/gọi service; SQL ở JdbcCatalogRepository |
| ShopService vừa xử lý nghiệp vụ vừa gọi JDBC | ShopService điều phối transaction và guard; ShopRepository/JdbcShopRepository thực hiện persistence |
| Request/response record lẫn trong controller/entity | DTO request/response, entity, query model và mapper có package riêng |
| Service phụ thuộc HttpStatus | ErrorCode/BusinessException độc lập HTTP; GlobalExceptionHandler ánh xạ status |
| Worker chứa câu SQL và transaction | Worker gọi OutboxProcessingService → EventRepository |
| Product giữ giá và stock | Product → ProductSku → StockBalance theo kho; biến thể có quan hệ riêng |
| Owner subject nằm trực tiếp trong shop | Identity account + shop owner FK + membership; Keycloak tiếp tục giữ credentials |
| Chỉ có audit text cho quyết định mới | Thêm ShopReview có reviewer FK, decision, reason; giữ audit cũ |
| Format Java không thống nhất | Spotless/google-java-format AOSP, kiểm ngay ở Maven validate |
| Chỉ có quy ước kiến trúc bằng lời | 8 ArchUnit rules kiểm phụ thuộc tầng và vòng module |

## 2. Mô hình dữ liệu đã triển khai

17 bảng trên các schema `identity`, `merchant`, `catalog`, `inventory`, `platform`. Mỗi bảng lõi có entity tương ứng; quan hệ ghép được biểu diễn bằng các khóa thành phần, không gán một ID giả cho bảng nối.

Các ràng buộc quan trọng đã được kiểm bằng PostgreSQL:

- Owner account phải tồn tại; một shop/owner là giới hạn pilot được ghi rõ.
- OWNER membership phải khớp owner của shop.
- SKU thuộc đúng product/shop; seller SKU và tổ hợp biến thể không trùng trong phạm vi tương ứng.
- SKU và warehouse của một stock balance thuộc cùng shop.
- On-hand/reserved/blocked không âm, không vượt tổng hàng có thể phân bổ.
- Option/value của SKU không thể lấy từ sản phẩm hoặc chiều phân loại khác.
- Reservation có quantity dương, TTL và idempotency key unique.
- Giá integer VND dương; entity/DTO và repository không dùng float để tính tiền.

Xem [thiết kế DB](database/README.md), [data dictionary](database/data-dictionary.csv), [constraints](database/constraints.csv), [indexes](database/indexes.csv).

## 3. Thiết kế tham chiếu các module tiếp theo

`target-commerce.sql` mở rộng mô hình lên **55 bảng/14 schema**. **38 bảng thêm là reference design**, chưa được đưa vào DB ứng dụng và chưa có API giao dịch.

Đã thực thi DDL trên DB mới riêng, xác minh FK/type/check/index, và chạy 5 kiểm tra ledger: entry cân được commit; entry không cân bị từ chối; không sửa line đã ghi; không thêm line vào entry ở transaction sau; không ghi business event key trùng. Các aggregate như quota refund, budget voucher, quantity return và available payout còn phải được command service thực hiện nguyên tử khi phát triển.

Dictionary có **389 cột**, 697 constraint entries do PostgreSQL xuất (bao gồm NOT NULL), 188 index entries; số lượng phục vụ kiểm kê, không phải thước đo chất lượng hoặc bằng chứng mọi nghiệp vụ đã xong.

## 4. Bảo toàn dữ liệu migration

- Backup trước thay đổi nằm trong `.local/backups/vshop-before-schema-refactor.sql`.
- Áp dụng V1001/V1002 lên DB local thật; không sửa checksum V1/V2/V1000.
- Ngay sau migration, trước E2E mới: **5 shop, 6 product, 16 audit, 16 outbox được giữ nguyên**.
- Checksum product ID + giá + tồn trước/sau khớp: `484220645b8ff9ac13004b145f1379a2`.
- SKU mới nhận ID từ product cũ để truy xuất đối chiếu; stock được chuyển sang kho DEFAULT với opening movement; quyền owner được backfill qua account/membership.
- Dữ liệu E2E tạo sau snapshot là dữ liệu kiểm thử mới, không phải migration nhân bản dữ liệu cũ.
- Sau lượt E2E của bản tái cấu trúc: 6 shop tổng cộng, 20 audit/outbox events. Worker mới đã chạy thực và ghi đủ 20 receipt, không có event pending; dữ liệu kiểm thử được giữ trong local.

Đã kiểm cả upgrade DB cũ và đường tạo DB trống. Chưa thực hiện restore drill production; không gọi việc có file dump là đã đạt RPO/RTO.

## 5. Kết quả kiểm chứng

| Kiểm tra | Kết quả |
|---|---|
| Kiến trúc ArchUnit | 8/8 đạt |
| Ràng buộc DB và aggregate catalog | 11/11 đạt trên PostgreSQL thật |
| API nghiệp vụ/phân quyền/concurrency/outbox | 11/11 đạt |
| Java tổng | **30/30 đạt** |
| Browser xuyên buyer/seller/admin | **5/5 đạt**, API và Keycloak thật sau migration |
| Web | Production build, typecheck và Prettier check đạt |
| Java format | Spotless apply/check đạt |
| DDL tham chiếu | Tạo schema từ trắng và ledger checks đạt; DB kiểm tra được xóa riêng sau chạy |

Các test API tiếp tục dùng contract JSON cũ, do đó web không cần đổi hình dạng dữ liệu khi chuyển từ product price/stock sang SKU/inventory. Listing hiện trả giá thấp nhất của active SKU và tổng available ở kho active; UI chọn SKU và kiểm giá/tồn tại checkout vẫn chưa được triển khai.

## 6. Tài liệu và quy ước để phát triển tiếp

- [Kiến trúc code](architecture/README.md): trách nhiệm từng tầng và ví dụ luồng cụ thể.
- [Thiết kế DB](database/README.md): quan hệ, PK/FK, invariant, dữ liệu nhạy cảm, migration và các giới hạn còn lại.
- `AGENTS.md`: quy ước bắt buộc trong checkout cho thay đổi tiếp theo.
- CI đã thêm format check, ArchUnit và kiểm schema tham chiếu. CI remote chưa được chạy vì chưa có remote repository.

Backlog gốc chưa được đánh dấu hoàn thành các domain chưa có use case. Công việc tiếp theo vẫn gồm refresh/MFA/OpenAPI, seller KYC/thuế/ngân hàng, API SKU, atomic inventory commands, rồi mới checkout/payment/aftersales/finance theo cổng nghiệm thu đã lập.
