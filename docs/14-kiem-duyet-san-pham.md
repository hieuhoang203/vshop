# Kiểm duyệt sản phẩm

Ngày: **20/11/2026**. Phạm vi: backend (backlog C08, **InProgress**). Thực hiện luồng F02: `DRAFT → PENDING_REVIEW → ACTIVE`, từ chối có lý do. Chưa có UI hàng đợi duyệt cho admin hay màn xem lý do cho seller, chưa có thông báo cho seller, chưa có SLA hay phân công người duyệt.

## 1. Máy trạng thái sản phẩm

| Từ              | Sang           | Ai             | Điều kiện                                                                            |
| --------------- | -------------- | -------------- | ------------------------------------------------------------------------------------ |
| DRAFT, REJECTED | PENDING_REVIEW | Seller (owner) | Có ít nhất một SKU đang bán; xóa lý do từ chối cũ                                    |
| PENDING_REVIEW  | ACTIVE         | **Admin**      | Đúng `expectedVersion`, còn SKU đang bán                                             |
| PENDING_REVIEW  | REJECTED       | **Admin**      | Lý do ≥ 5 ký tự, đúng `expectedVersion`                                              |
| ACTIVE          | HIDDEN         | Seller         |                                                                                      |
| HIDDEN          | ACTIVE         | Seller         | Nội dung hiện tại đã được duyệt (mọi sửa nội dung đã đưa sản phẩm về PENDING_REVIEW) |
| ACTIVE, HIDDEN  | PENDING_REVIEW | Hệ thống       | Seller sửa tên, mô tả, danh mục hoặc ảnh                                             |

Seller không thể tự đưa sản phẩm sang ACTIVE từ DRAFT, REJECTED hoặc PENDING_REVIEW (409 `INVALID_PRODUCT_STATE`). Đổi giá hoặc trạng thái bán của SKU **không** cần duyệt lại. Sản phẩm REJECTED vẫn sửa được nhưng giữ REJECTED cho tới khi gửi duyệt lại. Buyer chỉ thấy ACTIVE, nên mọi trạng thái còn lại đều ngoài catalog công khai.

## 2. API

| Method | Đường dẫn                                       | Mục đích                                                          |
| ------ | ----------------------------------------------- | ----------------------------------------------------------------- |
| GET    | `/api/v1/admin/products/review-queue?page&size` | Hàng đợi PENDING_REVIEW của mọi shop, cũ nhất trước (ADMIN)       |
| POST   | `/api/v1/admin/products/{productId}/review`     | `{decision: APPROVED\|REJECTED, reason, expectedVersion}` (ADMIN) |

Response dùng chung `SellerProductResponse`, có thêm `rejectionReason`; seller đọc được lý do qua endpoint chi tiết của mình.

## 3. Bảo đảm đã triển khai

- **Quyết định gắn với đúng phiên bản đã xem.** Nếu seller sửa trong lúc sản phẩm nằm trong hàng đợi thì `expectedVersion` của admin lệch và bị từ chối 409 `VERSION_CONFLICT`, tránh duyệt nhầm nội dung mới chưa ai xem. `catalog.product_review` lưu `product_version` đã duyệt.
- Khóa dòng sản phẩm `FOR UPDATE` khi duyệt nên hai admin duyệt cùng lúc chỉ một người thành công; người sau nhận 409 vì sản phẩm không còn chờ duyệt.
- Lịch sử `product_review` là bản ghi bất biến (APPROVED/REJECTED, lý do, người duyệt, thời điểm). Audit `ProductApproved/ProductRejected` được ghi cùng transaction; vẫn dùng mã shop làm `resource_id` và mã sản phẩm trong `reason`.
- DB có `CHECK` ràng buộc `REJECTED` luôn đi kèm lý do ≥ 5 ký tự và các trạng thái khác thì không có lý do.
- Migration **V1003** chỉ thêm cột, bảng và nới `CHECK` trạng thái; không di chuyển hay xóa cột, dữ liệu cũ (DRAFT/ACTIVE/HIDDEN) vẫn hợp lệ nên không cần backup. Đã áp dụng lên DB test có sẵn ở V1002 (đường nâng cấp) và dựng mới từ trắng qua `verify-db-design.ps1`; dictionary đã được xuất lại.

## 4. Quyết định và giới hạn cần biết

1. **Sửa nội dung sản phẩm đang bán đưa nó ngừng bán cho đến khi được duyệt lại**, kể cả sửa lỗi chính tả. An toàn nhưng gây gián đoạn bán; nếu muốn, có thể chỉ yêu cầu duyệt lại khi đổi danh mục, ảnh hoặc có phân loại rủi ro. Cần chủ sản phẩm chốt.
2. **Mọi ADMIN đều duyệt được**; chưa có vai trò moderator riêng, chưa có phân công, SLA hay hàng đợi theo ngành (theo mục 1 tài liệu nghiệp vụ, Moderator là vai trò riêng).
3. Hủy gửi duyệt (quay về DRAFT) và duyệt hàng loạt chưa có.
4. Chưa có kiểm tra tự động nội dung (ảnh, từ khóa cấm, hàng hạn chế) và chưa có kháng nghị.
5. Chưa có tạm ngưng sản phẩm bởi admin sau khi đã ACTIVE (flow F08 xử lý vi phạm).
6. `packages/contracts` chưa có kiểu cho các endpoint này.

## 5. Kiểm chứng

`scripts/test-backend.ps1`: **61 test đạt** (thêm 5 test `ProductModerationIntegrationTests`; các test catalog cũ đã sửa theo luồng mới, trong đó có kiểm tra seller không tự publish được):

- admin duyệt → hiển thị công khai, hàng đợi chỉ cho ADMIN (401/403), `product_version` và audit được ghi;
- từ chối cần lý do, sản phẩm ngoài catalog, seller thấy lý do, sửa → gửi lại → duyệt, hai bản ghi review;
- sửa khi đang chờ duyệt làm quyết định cũ bị 409, duyệt đúng version thì thành công;
- chỉ PENDING_REVIEW mới duyệt được, duyệt hai lần và từ chối sau duyệt đều 409, seller không tự duyệt;
- sửa nội dung đang bán quay về duyệt, đổi giá thì không, ẩn rồi hiện lại không cần duyệt.

Chưa có E2E trình duyệt vì chưa có UI.
