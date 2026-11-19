# Kiến trúc mã nguồn VShop

Trạng thái: quy ước đang được áp dụng và kiểm tra tự động. Ngày cập nhật: 19/11/2026.

## 1. Tổ chức theo module và theo tầng

```text
backend/src/main/java/vn/vshop/
├── VShopApplication.java
├── config/                       # Security và cấu hình chạy scheduler
├── common/
│   ├── exception/                # Mã lỗi nghiệp vụ, không phụ thuộc HTTP
│   ├── model/                    # Kiểu kết quả dùng chung ở application layer
│   └── web/
│       └── dto/                  # Error/pagination contract, filter và advice
└── modules/
    ├── identity/
    ├── merchant/
    ├── catalog/
    ├── inventory/                # Entity hiện tại; chưa có API ghi tồn
    └── platform/
        └── worker/               # Scheduler gọi service, không chứa SQL
```

Cấu trúc của module có API:

```text
modules/merchant/
├── controller/
│   ├── SellerShopController.java
│   └── AdminShopController.java
├── dto/
│   ├── request/                  # SubmitShopRequest, ReviewShopRequest
│   └── response/                 # ShopResponse
├── entity/                       # Shop, ShopReview, các enum nghiệp vụ
├── mapper/                       # Chuyển entity sang response
├── repository/
│   ├── ShopRepository.java        # Hợp đồng truy cập dữ liệu
│   └── jdbc/
│       └── JdbcShopRepository.java
└── service/
    └── ShopService.java           # Nghiệp vụ và transaction
```

Không tạo `controller/` toàn cục gom mọi domain. Cũng không tạo hàng loạt package/service rỗng cho các tính năng chưa viết. Entity của bảng chưa có use case ghi có thể chưa có repository/controller tương ứng.

## 2. Trách nhiệm và giới hạn từng tầng

| Tầng | Được làm | Không được làm |
|---|---|---|
| Controller | Đọc HTTP, xác thực dữ liệu request, lấy danh tính, gọi service, trả response | SQL, JdbcClient, transaction điều phối, tự duyệt quyền shop dựa vào client |
| DTO request | Kiểu dữ liệu đầu vào, giới hạn độ dài/số lượng, chuẩn hóa khoảng trắng | Entity database, thực hiện query hoặc chứa secret |
| DTO response | Hợp đồng JSON được công bố | Đưa toàn entity ra ngoài, lộ owner account, version nội bộ hoặc token |
| Mapper | Chuyển dữ liệu giữa request/query model/entity/projection/response | Gọi DB hoặc thực hiện quyết định nghiệp vụ |
| Service | Guard nghiệp vụ, ownership, state transition, orchestration, transaction | SQL, HttpStatus, servlet/JWT hoặc phụ thuộc implementation repository |
| Repository interface | Mô tả thao tác lưu/đọc theo nhu cầu use case | Quy tắc HTTP, DTO giao diện, gửi notification |
| JDBC repository | SQL có bind parameter, row mapping, row lock, persistence | Quyết định actor được duyệt shop hay không; trả DTO HTTP |
| Entity | Dữ liệu và kiểu nghiệp vụ tương ứng persistence identity | Phụ thuộc Spring MVC, DTO, JdbcClient hoặc annotation serialize dành riêng API |
| Query model/projection | Dữ liệu đọc tổng hợp có tên/kiểu rõ | Giả làm một entity dùng để ghi nguyên aggregate |
| Worker | Lịch chạy, gọi application service | Truy cập JdbcClient hoặc giữ transaction khi gọi dịch vụ ngoài |

Luồng phụ thuộc:

```text
HTTP → Controller → Service → Repository interface → JDBC implementation → PostgreSQL
          ↕             ↕                    ↕
      DTO + Mapper   Entity/model        Entity/projection

Scheduler → ProcessingService → EventRepository → PostgreSQL
```

Ví dụ thực tế: `SellerShopController.submit()` nhận `SubmitShopRequest`; `ShopService.submit()` tạo/kiểm account, tạo shop và membership, ghi audit/outbox trong cùng transaction; `JdbcShopRepository` là nơi duy nhất biết câu INSERT/SELECT của shop; `ShopMapper` trả `ShopResponse`.

## 3. Entity và ORM

Entity hiện tại là Java record bất biến, có identity, enum trạng thái, timestamp và version khi phù hợp. Các record này được repository ánh xạ bằng Spring JDBC. **Không gắn `@Entity` của JPA vào lớp không dùng JPA** và không gọi DTO là entity.

Giữ JDBC là quyết định persistence của giai đoạn này, không phải một yêu cầu mọi dự án phải dùng JDBC. Nó cho phép thể hiện rõ conditional UPDATE, composite key, `FOR UPDATE` và `SKIP LOCKED`. Spring JDBC hỗ trợ truy vấn có bind parameter và row mapping; việc chia tầng độc lập với lựa chọn JDBC/JPA. [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)

Nếu chuyển một module sang JPA sau này, giữ nguyên controller/service contracts, bổ sung adapter repository và kiểm migration/concurrency. Không trộn hai nguồn cập nhật cùng aggregate khi chưa có quy ước transaction/version.

## 4. Ranh giới nghiệp vụ và transaction

- Merchant sở hữu shop, membership và review; Identity sở hữu mapping subject → account; Catalog sở hữu product/SKU/options; Inventory sở hữu số lượng; Platform sở hữu audit/outbox/receipt.
- Merchant chỉ gọi `AccountService` và `AuditEventService`, không viết bảng của hai module đó qua repository riêng. Các service tham gia transaction của use case chính.
- `AuditEventService.record()` yêu cầu `Propagation.MANDATORY`, tránh ghi audit/outbox tách khỏi transaction domain.
- Repository đọc catalog được phép join merchant/inventory để tạo projection công khai trong modular monolith. Đây là ngoại lệ đọc có chủ đích; không cho catalog ghi bảng merchant/inventory. Nếu tách service, thay bằng projection đồng bộ qua event.
- Không gọi HTTP tới PSP/carrier bên trong transaction giữ khóa. Các domain này chưa có code triển khai.
- Read service dùng `readOnly`; catalog search dùng một snapshot `REPEATABLE_READ` để count và page nhất quán. Command shop dùng transaction và database guard/row lock.

## 5. Kiểm tra kiến trúc tự động

`backend/src/test/java/vn/vshop/architecture/ArchitectureTests.java` có 8 rule:

1. Controller không phụ thuộc repository hoặc JDBC.
2. Chỉ repository được dùng JDBC/SQL APIs.
3. Service không phụ thuộc HTTP, DTO, controller hoặc JDBC adapter cụ thể.
4. Repository không phụ thuộc các tầng phía trên.
5. Entity độc lập framework và contract HTTP.
6. JDBC adapter phải có `@Repository`.
7. Controller phải có `@RestController`.
8. Các module nghiệp vụ không tạo vòng phụ thuộc.

Các rule kiểm tra bytecode khi `mvn test/verify`; không chỉ là quy ước viết trong tài liệu. Chúng không phân tích mọi câu SQL nên cấm cross-module write vẫn cần review repository. [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html)

## 6. Chuẩn format và đặt tên

| Nội dung | Quy ước đang áp dụng |
|---|---|
| Java | Spotless + google-java-format 1.28.0, AOSP, 4 spaces; kiểm ở Maven `validate` |
| TypeScript/TSX/CSS/JSON/YAML | Prettier, 2 spaces, print width 100; `npm run format:check` trong CI |
| File Java | Một public top-level type/file; tên file khớp tên lớp; không wildcard import trong production code |
| Class | PascalCase; hậu tố `Controller`, `Service`, `Repository`, `Request`, `Response`, `Mapper` theo vai trò |
| Method/field | camelCase; tên mô tả hành vi, không viết tắt tùy tiện |
| SQL | Schema theo domain, snake_case; SQL nhiều dòng dùng text block trong repository |
| Tiền/thời gian | Tiền VND integer ở DB/Java; timestamp có timezone/UTC; hiển thị múi giờ Việt Nam ở UI |
| Lỗi | ErrorCode nghiệp vụ → handler ánh xạ HTTP; response lỗi có correlation ID |

Không format lại các migration đã được áp dụng. Checksum của migration là một phần lịch sử DB.

Chạy tại repository:

```powershell
npm run format
npm run format:check
```

Chạy tại `backend/`:

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd verify
```

## 7. Cấu trúc frontend

`apps/web/src/app` giữ route/page theo App Router; `components` giữ UI tái sử dụng; `lib/api.ts` gọi backend phía server, `lib/auth.ts` xử lý OIDC/session; `packages/contracts` giữ kiểu contract TypeScript. Route BFF chỉ làm session/Origin/allowlist/proxy, không tính giá, quyết định trạng thái shop hoặc truy cập PostgreSQL.

Hiện `contracts` còn được khai báo thủ công; OpenAPI generation vẫn là backlog. Chưa tự chia frontend thành quá nhiều package khi chưa có domain interaction tương ứng.
