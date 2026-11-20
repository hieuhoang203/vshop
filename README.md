# VShop

Marketplace Việt Nam — một người phát triển, mục tiêu pilot sau khoảng một năm.

Increment 01 có web người mua, đăng nhập Keycloak, hồ sơ shop và xét duyệt admin được lưu thật vào PostgreSQL. Chưa tiếp nhận đơn hàng/thanh toán. [Hồ sơ BA và roadmap](docs/README.md) · [Phạm vi và kết quả triển khai](docs/07-trien-khai-increment-01.md).

Nền tảng đã được tái cấu trúc theo tầng và chuẩn hóa DB: [Kiến trúc code](docs/architecture/README.md) · [Thiết kế DB/ERD/dictionary](docs/database/README.md) · [Báo cáo tái cấu trúc](docs/08-chuan-hoa-db-va-kien-truc.md). Đây là tài liệu hiện hành; increment 01 giữ vai trò snapshot lịch sử.

Giao diện dùng **Radix Themes/Primitives**, ưu tiên ảnh/tên/giá sản phẩm ngay màn đầu, không có hero banner phía trên catalog. Giữ màu tím–lime, lưu món, xem nhanh, tìm nhanh Ctrl/⌘ K và lọc/sắp xếp từ backend; mood nằm sau danh sách. Admin dùng **shadcn Area Chart/Recharts**. Xem [bố cục hiện hành và kiểm chứng viewport](docs/11-san-pham-la-trung-tam.md).

**App mobile riêng:** React Native/Expo iOS và Android tại `apps/mobile`; khám phá/tìm kiếm/chi tiết/lưu món, đăng nhập PKCE và hồ sơ seller. Xem [hướng dẫn chạy](apps/mobile/README.md) và [kiến trúc/phạm vi mobile](docs/10-ung-dung-mobile-native.md). Web responsive và app native là hai giao diện riêng dùng chung API.

## Yêu cầu

- Java **21**, Node.js **22**, npm, PowerShell **7** và Docker Desktop đang chạy Linux containers.
- Maven được tải bằng wrapper `backend/mvnw.cmd` hoặc `backend/mvnw`, không cần cài Maven toàn máy.
- Local ports: web **3100**, API **8081**, PostgreSQL **15433**, Keycloak **8180** và health **9180**, Redis **6380**. Dùng **localhost** cho web để khớp OIDC/cookie/Origin; không đổi sang 127.0.0.1 khi đăng nhập.

## Chạy local trên Windows

Tại thư mục repository:

```powershell
pwsh -File scripts/init-local.ps1
docker compose up -d postgres keycloak redis
npm ci
```

Keycloak có thể cần 30–60 giây ở lần đầu. Kiểm trạng thái tại `http://localhost:9180/health/ready`.

Terminal 1 — backend:

```powershell
pwsh -File scripts/start-backend.ps1
```

Terminal 2 — web:

```powershell
npm run dev
```

Mở [web](http://localhost:3100), [kênh người bán](http://localhost:3100/seller), [admin](http://localhost:3100/admin). API health: `http://localhost:8081/actuator/health`.

Script init sinh mật khẩu ngẫu nhiên vào `.local/accounts.json`, thông số Docker trong `.env`, cấu hình BFF trong `apps/web/.env.local`. Các file này đều bị Git ignore. **Không dùng thông tin đăng nhập có sẵn trên Internet**, không commit/share các file local này. Tài khoản thử nghiệm: `buyer.local`, `seller.local`, `admin.local`; đọc mật khẩu trên máy của bạn trong `.local/accounts.json`.

Tài khoản Keycloak console là `vshop-local-admin`; mật khẩu bootstrap nằm trong `.env`. Realm template được copy/import lần đầu. Sửa realm JSON không tự cập nhật realm đã tồn tại; thay đổi qua console hoặc migration realm có kiểm soát, không xóa volumes để cập nhật.

Phiên web hiện có thời hạn tương ứng access token, mặc định 10 phút; hết hạn yêu cầu đăng nhập lại. Auto-refresh, MFA bắt buộc admin và đăng ký tài khoản công khai chưa hoàn thành.

Terminal 3 — worker nhận sự kiện nội bộ (tùy chọn):

```powershell
pwsh -File scripts/start-backend.ps1 -Worker
```

Worker ghi nhận receipt bền vững, chống xử lý trùng; **chưa gửi email/push hoặc giao tiếp broker**.

## Kiểm tra

```powershell
pwsh -File scripts/test-backend.ps1
pwsh -File scripts/verify-db-design.ps1 -ExportDictionary
npm run format:check
npm run typecheck
npm run build
npm run test:e2e
```

Backend test dùng DB riêng `vshop_test` được script tạo trong PostgreSQL local. Hầu hết test rollback; test cạnh tranh tạo subject `concurrency-test-*` riêng trong DB test. Không cấu hình `VSHOP_TEST_DATABASE_URL` trỏ vào DB vận hành.

E2E cần API/Keycloak đang chạy và `.local/accounts.json`. Playwright dùng Chromium đã cài nếu tìm thấy; có thể chỉ định `PLAYWRIGHT_CHROMIUM_EXECUTABLE`, hoặc chạy `npx playwright install chromium` nếu chưa có browser. E2E tạo user/shop có tiền tố `e2e` trong môi trường local, giữ lại hồ sơ và audit để kiểm chứng persistence. Không chạy vào production. Không bật trace chứa thao tác đăng nhập nếu chưa có cách xử lý dữ liệu nhạy cảm.

Ảnh browser: `.local/screenshots/`. Báo cáo test: `backend/target/surefire-reports/` và `playwright-report/`. File `.local/e2e-last-run.json` lưu định danh lần kiểm thử gần nhất, không chứa mật khẩu/token.

## Cấu trúc

| Thư mục              | Vai trò                                                                                                    |
| -------------------- | ---------------------------------------------------------------------------------------------------------- |
| `apps/web`           | Next.js buyer/seller/admin; BFF giữ token trong cookie mã hóa HttpOnly                                     |
| `apps/mobile`        | App React Native/Expo iOS/Android: native routes, screens, API, auth và storage                            |
| `packages/contracts` | DTO TypeScript hiện dùng; OpenAPI generation là bước tiếp theo                                             |
| `backend`            | `common`, `config`, `modules/{identity,merchant,catalog,inventory,platform}`; mỗi module có các tầng riêng |
| `infra`              | Realm template và Kong DB-less config                                                                      |
| `scripts`            | Khởi tạo local, chạy backend/worker, test DB riêng                                                         |
| `tests/e2e`          | Browser test OIDC và luồng xét duyệt xuyên vai trò                                                         |
| `docs`               | Khảo sát, quy tắc, backlog, UAT và bằng chứng triển khai                                                   |

`application-local.yml` nạp 1 shop mẫu, 3 danh mục, 6 sản phẩm mẫu. Các fixture nằm ngoài migration production và không được nạp nếu không bật profile `local`. Không chạy production bằng profile local. V1000 được giữ nguyên lịch sử; migration lõi tiếp tục V1001/V1002 để tách account/SKU/inventory và không còn cần out-of-order.

DB ứng dụng có 18 bảng nền tảng trên 5 schema. DDL tham chiếu mở rộng lên 56 bảng/14 schema đã được kiểm trong DB riêng; không được hiểu rằng các chức năng checkout/payment/finance đã có code hoạt động.

Format Java tại `backend/`: `./mvnw.cmd spotless:apply`; kiểm bằng `./mvnw.cmd verify`. Format web/config: `npm run format`. ArchUnit ngăn phụ thuộc sai tầng và Spotless chặn Java sai format trong Maven lifecycle.

## Hạ tầng tùy chọn

```powershell
docker compose --profile media up -d storage
docker compose --profile gateway up -d gateway
```

MinIO tại `localhost:9100` và console `localhost:9101`; chưa tạo bucket/upload workflow. Kong proxy tại `localhost:8000/api`; Java vẫn xác minh JWT và quyền domain. Hai profile này đã có cấu hình nhưng chưa thuộc luồng mặc định đã nghiệm thu.

Dockerfile backend dùng context `backend`; Dockerfile web dùng context repository. Khi chạy container phải cấp DB/issuer/URL phù hợp; Dockerfiles hiện chưa được build và chưa có stack production được nghiệm thu. Không để container kết nối DB bằng localhost của chính nó. Keycloak/gateway/storage ở đây phục vụ local, chưa phải hardening production.

CI GitHub đã có backend tests/ArchUnit/Spotless với PostgreSQL, Prettier check, web build và xác minh DDL tham chiếu; chưa được thực thi trên GitHub vì chưa có remote. Browser E2E chưa được nối vào CI.

## Dừng local

Nhấn `Ctrl+C` ở terminal web/backend/worker. Nếu Windows còn giữ child process, chạy `pwsh -File scripts/stop-local.ps1`; script chỉ dừng tiến trình Next.js/Java xác định thuộc checkout VShop. Dừng hạ tầng bằng `docker compose stop` (giữ dữ liệu). Tránh `docker compose down -v` vì sẽ xóa dữ liệu local.

## Phạm vi tiếp theo

Hoàn thiện nền tảng: refresh session, MFA admin và OpenAPI; tiếp đó hồ sơ seller/thuế/ngân hàng, UI duyệt/editor sản phẩm/tồn kho và checkout giữ tồn. Backend đã có API ghi sản phẩm/biến thể/SKU/giá ([docs/12](docs/12-catalog-seller-api.md)) và tồn kho/reservation ([docs/13](docs/13-ton-kho-va-giu-hang.md)); kiểm duyệt sản phẩm đã có ở backend ([docs/14](docs/14-kiem-duyet-san-pham.md)); UI và checkout chưa được triển khai. Tích hợp PSP/carrier chỉ khi có tài liệu sandbox và mô hình tiền được xác nhận. Chưa triển khai checkout, payment, shipping, refund, settlement hoặc production deployment. App native mới có phạm vi ghi trong báo cáo mobile.
