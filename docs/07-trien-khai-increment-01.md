# Increment 01 — Nền tảng và luồng xét duyệt gian hàng

Ngày hoàn tất increment: **19/11/2026**. Phạm vi: chạy local, chưa triển khai production.

## 1. Kết quả có thể chạy và kiểm chứng

- Monorepo npm workspaces: Next.js 16.4.6, React 19.3.1, TypeScript 5.9.3; Java 21, Spring Boot 4.1.2, Maven wrapper.
- API thực với PostgreSQL 18, Flyway migration; profile local có fixture tách khỏi migration production.
- Keycloak 26.4.7: OIDC Authorization Code + PKCE, state và nonce; xác minh JWT issuer/audience/signature ở BFF và backend.
- Web giữ access/id token trong cookie JWE mã hóa, HttpOnly, SameSite=Lax; không lưu token trong localStorage. Secure bật khi WEB_BASE_URL là HTTPS; local đang HTTP.
- Buyer xem/tìm/lọc danh mục và chi tiết sản phẩm từ PostgreSQL, SSR hoạt động cả khi tắt JavaScript. Không có checkout hoặc nút đặt hàng giả.
- Buyer/seller đăng ký một shop trong phạm vi pilot; admin yêu cầu bổ sung hoặc chấp thuận; owner sửa và gửi lại hồ sơ bị từ chối.
- Backend chặn truy cập chéo shop và buyer gọi admin API, kể cả bỏ qua giao diện. Unique constraint bảo vệ trường hợp hai submit cùng lúc.
- Thay đổi shop + audit + outbox cùng transaction. Worker riêng ghi durable receipt có dedupe, khóa SKIP LOCKED và transaction; đây mới là consumer nội bộ, chưa có gửi email/broker/DLQ.
- Docker Compose PostgreSQL/Keycloak/Redis đã chạy. Profile Kong/MinIO và Dockerfiles đã viết nhưng chưa build/chạy nghiệm thu.
- Có CI workflow backend tests/PostgreSQL + web typecheck/build; chưa chạy trên GitHub, chưa có remote.

## 2. Luồng đã chạy trên trình duyệt thật

```text
Tài khoản mới được cấp vai trò BUYER qua Keycloak
  → đăng nhập trên mobile
  → gửi hồ sơ shop
  → reload vẫn thấy SUBMITTED
  → admin đăng nhập tài khoản riêng
  → REJECTED kèm nội dung cần bổ sung
  → seller cập nhật mô tả và gửi lại
  → admin duyệt APPROVED trên mobile
  → seller reload thấy Đã duyệt
```

Tài khoản E2E cuối: `e2e.seller.1795059564149`, Keycloak subject `e317a1d8-cf4f-4362-ae7b-b253bbc27a0d`, shop `E2E Shop 1795059565436`. Định danh và thời điểm được lưu trong `.local/e2e-last-run.json`; mật khẩu test user mới chỉ giữ trong phiên test và không xuất vào report.

Sau các lượt nghiệm thu local: **5 shop = 1 shop fixture + 4 shop E2E**, đều APPROVED; **3 danh mục, 6 sản phẩm fixture**, **16 audit events/outbox events**, **16 processed events/receipts**. Không có đơn mua, payment hoặc giao dịch tiền thật. Các shop E2E được giữ để kiểm chứng persistence, không phải seller thực.

## 3. Kiểm tra đã đạt

| Kiểm tra | Kết quả/bằng chứng |
|---|---|
| Backend integration | **11 passed, 0 failed**, PostgreSQL riêng `vshop_test`; báo cáo `backend/target/surefire-reports` |
| Phân quyền | Anonymous 401; buyer review 403; đọc/sửa shop khác 404; danh sách chỉ shop của subject |
| Cạnh tranh | Hai submit cùng subject: đúng 1 shop + 1 audit event, request còn lại conflict |
| State transitions | Duyệt một lần; duyệt lại conflict; rejected resubmit; invalid whitespace bị từ chối |
| Catalog guard | Product thuộc shop chưa duyệt không public; query bind parameter, wildcard escaped; giới hạn page/size |
| Outbox | Replay cùng event chỉ có một receipt và trạng thái processed |
| Browser E2E | **5 passed**, Chromium thực, OIDC qua Keycloak đang chạy; API/DB không mock |
| Responsive | Trang chủ, chi tiết, cửa vào seller/admin: 360/390/768/1440px không tràn ngang; seller form/status và admin review thực ở 390px |
| SSR/assets | Tắt JavaScript vẫn đọc/tìm catalog; SVG sản phẩm được browser tải thành công |
| Authentication | HttpOnly/SameSite; cookie không đọc bằng JS; không token trong localStorage; Origin sai 403; callback và bearer giả bị từ chối |
| Build/types | Next production build và `next typegen && tsc --noEmit` đạt |
| npm audit | Dependencies production: 0 vulnerabilities được npm báo tại lần kiểm tra; không coi đây là kiểm toán bảo mật đầy đủ |
| Compose | Config core + các profile tùy chọn parse thành công; core chạy thật; API health 200/UP, có correlation header |
| Secrets | Các file secrets được ignore; tìm 5 secrets Docker đã sinh trong các file có thể track không thấy rò rỉ |

Ảnh đã xem trực tiếp: `.local/screenshots/storefront-desktop.png`, `.local/screenshots/storefront-mobile.png`. Có sửa typography mobile sau khi xem và chạy lại browser test trên đúng production build. Đã loại tiến trình VShop cũ gây HTML/CSS khác phiên bản trước lượt kiểm tra cuối.

## 4. Trạng thái backlog

Backlog gốc vẫn **130 task, 174,5 ngày công P0**. Snapshot sau increment: **1 Done, 16 InProgress, 113 Planned**. Không dùng số task có code để báo hoàn thành nguyên phase.

| Task | Trạng thái | Giới hạn còn lại |
|---|---|---|
| B03 | Done | Baseline migration chạy từ DB trắng, constraints DB và Flyway được dùng thực |
| B01 | InProgress | Web/API/worker có code, chưa có app Expo và enforcement ranh giới module |
| B02 | InProgress | Local có thể chạy; chưa staging, chưa secret manager |
| B04/B06 | InProgress | OIDC/session đã chạy; chưa refresh, MFA bắt buộc admin, key rotation drill và revoke toàn phiên |
| B05 | InProgress | Ownership shop và quyền admin đã kiểm; các domain đơn/tiền chưa tồn tại |
| B07 | InProgress | Có DTO TypeScript và error model; chưa OpenAPI generation và contracts đầy đủ |
| B08 | InProgress | Audit shop/correlation đã có; chưa audit export, tamper controls và cấu hình log production đầy đủ |
| B10 | InProgress | Worker receipt dedupe đã kiểm; chưa broker, retry policy/DLQ và consumer thông báo |
| B11/B12 | InProgress | Có config gateway/CI/Dockerfiles; chưa chạy gateway, image build, CI remote, deploy/rollback staging |
| B14/B15 | InProgress | Shell và test nền có; chưa toàn màn hình, chưa Testcontainers và coverage toàn domain |
| C02/C03 | InProgress | Hồ sơ cơ bản và review đã chạy; chưa KYC, thuế, tài khoản ngân hàng hoặc xác minh pháp lý |
| C11/C12 | InProgress | Catalog SSR và search/lọc category; chưa SKU selector, facet, tìm không dấu, ranking và pagination UI |

A01–A13 vẫn cần discovery/đối tác/xác nhận sản phẩm. Việc bắt đầu foundation không đồng nghĩa đã có phỏng vấn, hợp đồng PSP/carrier hoặc được phép vận hành live. Bộ 42 UAT trong `06-uat.csv` vẫn là test plan toàn sản phẩm; không đánh dấu toàn bộ đạt từ 5 browser test increment.

## 5. Việc tiếp theo theo thứ tự

1. Hoàn nền tảng auth: refresh token an toàn, MFA admin, session revoke; thêm OpenAPI và enforce module boundaries.
2. Chốt ngành pilot cùng requirements hồ sơ seller; bổ sung thông tin/luồng xác minh tương ứng trước khi tuyên bố hoàn onboarding.
3. Xây catalog write flow: thuộc tính ngành, variants/SKU, giá, moderation; migration từ read model hiện tại có kiểm soát.
4. Xây inventory balance/movement/reservation; test cạnh tranh SKU cuối, commit/release và điều chỉnh dưới reserved.
5. Sau đó mới dựng quote/cart/checkout; cần PSP/carrier feasibility và policy được chốt trước khi tiến đến giao dịch thật.

Đợt này không có deployment bên ngoài, không gửi email/tin nhắn tới người khác, không phát sinh mua bán hoặc chi phí giao dịch PSP/carrier.

Sau kiểm tra đã dừng 3 tiến trình web/API/worker thuộc checkout bằng `scripts/stop-local.ps1`, tránh giữ process cũ khi build lại. PostgreSQL/Keycloak/Redis local cùng dữ liệu vẫn được giữ. Khởi động lại theo README để xem giao diện.
