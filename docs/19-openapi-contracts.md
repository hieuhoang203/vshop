# OpenAPI và hợp đồng TypeScript

Ngày: **20/11/2026**. Phạm vi: backend, `packages/contracts`, web, mobile, CI (backlog B07). Từ nay hình dạng API có **một nguồn duy nhất là mã Java**; web và mobile không còn khai báo kiểu API bằng tay.

## 1. Chuỗi sinh mã

```text
Controller + DTO Java ──(springdoc)──► docs/api/openapi.json ──(openapi-typescript)──► packages/contracts/src/generated/api.ts
                                       (commit, kiểm bằng test)                          (commit, kiểm bằng script)
                                                                  packages/contracts/src/index.ts = alias ổn định cho web/mobile
```

- **`docs/api/openapi.json`** (OpenAPI 3.1, 36 thao tác, 28 đường dẫn, 39 schema) được sinh bởi test `OpenApiSpecTests` và được commit để mọi thay đổi hợp đồng hiện ra trong diff review.
- **`packages/contracts/src/generated/api.ts`** do `openapi-typescript` 7.13.0 sinh ra. Không sửa tay (file được Prettier bỏ qua).
- **`packages/contracts/src/index.ts`** chỉ gồm alias tên ổn định (`Shop`, `Cart`, `Quote`, `NewProductInput`…) trỏ vào kiểu sinh ra, cùng các kiểu _input_ cho mọi lệnh ghi.

Springdoc (`springdoc-openapi-starter-webmvc-api` 3.2.0, chỉ phần API, không có Swagger UI) **tắt khi chạy thật** (`springdoc.api-docs.enabled=false`) nên API không phơi tài liệu ra ngoài; test bật nó bằng thuộc tính.

## 2. Quy trình khi đổi API

```bash
npm run openapi:update
```

Lệnh này chạy test sinh tài liệu với `UPDATE_OPENAPI=true` (ghi lại `docs/api/openapi.json`) rồi sinh lại TypeScript. Xem diff, sửa web/mobile nếu kiểu đổi, commit cả ba. Các lệnh liên quan:

| Lệnh                         | Việc                                                               |
| ---------------------------- | ------------------------------------------------------------------ |
| `npm run openapi`            | Kiểm tài liệu đã commit còn khớp mã Java không (không ghi)         |
| `npm run contracts:generate` | Sinh lại `generated/api.ts` từ tài liệu                            |
| `npm run contracts:check`    | Báo lỗi nếu `generated/api.ts` không khớp tài liệu (chạy trong CI) |

CI chặn hai kiểu lệch: `mvn verify` chạy `OpenApiSpecTests` (tài liệu ≠ mã Java), bước `contracts:check` (TypeScript ≠ tài liệu). Nếu quên cập nhật ở một khâu thì build đỏ với thông báo hướng dẫn chạy `npm run openapi:update`.

## 3. Hợp đồng mô tả đúng hành vi, không chỉ khung

Springdoc mặc định để mọi thuộc tính của response là tùy chọn, làm client phải kiểm `undefined` khắp nơi. `OpenApiConfiguration` định hình lại:

- **Response record:** mọi thành phần là _bắt buộc_, trừ khi gắn `@Nullable` (jspecify), khi đó schema có thêm kiểu `null` (`string | null`, hoặc `oneOf [$ref, null]` cho đối tượng, `enum` kèm `null`). Ví dụ `Shop.rejectionReason`, `QuoteResponse.shipTo`, `LineResponse.issue`, `AddressResponse.label` là `| null`. Thuộc tính null vẫn luôn có mặt trong JSON nên vẫn bắt buộc.
- **Request:** giữ nguyên ràng buộc Bean Validation (`@NotNull`, `@Size`, `@Min`, `@Max`, `@Pattern` thành `required`, độ dài, khoảng giá trị, regex).
- **Enum thật:** `ShopStatus`, `ProductStatus`, `SkuStatus`, `CartLineIssue`, `MovementType` giờ là kiểu enum trong DTO (JSON không đổi) nên TypeScript có union chặt thay cho `string`.
- Tên thao tác ổn định `{controller}{Method}` (ví dụ `sellerInventoryAdjust`), không còn hậu tố đếm như `review_1`. Hai response record trùng tên schema sẽ làm ứng dụng báo lỗi, không im lặng ghi đè.
- Mọi thao tác có phản hồi `default` là `ApiErrorResponse`; body JSON ghi đúng `application/json`; `Idempotency-Key` và tham số truy vấn (đã trải phẳng từ `@ModelAttribute`) hiện đúng; principal `Jwt` không xuất hiện như tham số.
- **Quyền truy cập:** mỗi thao tác có `security` (bearer) hoặc rỗng (catalog công khai) và phần mở rộng `x-vshop-access` (`public`, `anyOfRoles`, `requiresSecondFactor`).

### Quyền công bố phải khớp quyền thực thi

`OpenApiAccessTests` đi qua **mọi thao tác trong tài liệu** và gọi vào filter chain thật: không token → 401; token có role không được liệt kê → 403; admin không OTP → 403; thao tác công khai không được trả 401/403. Test này thay cho việc tin vào chú thích. Đã thử đột biến: sửa `rolesFor` cho đường admin thành `BUYER` thì test báo lỗi ở các thao tác admin.

## 4. Kiểm tra lúc biên dịch ở web

Các lệnh ghi của web dùng `satisfies` với kiểu input sinh ra (`UpdateProductInput`, `AddressInput`, `SetQuantityInput`, `StockAdjustmentInput`…), nên đổi hoặc bỏ một trường ở backend làm `tsc` báo lỗi đúng vị trí. Đã thử: đổi `quantity` thành `qty` ở nút thêm vào giỏ cho lỗi `Object literal may only specify known properties`. Phản hồi (`Shop`, `Cart`…) dùng kiểu sinh ra trực tiếp.

## 5. Giới hạn

1. **`bff()` chưa gõ kiểu theo đường dẫn.** Phương thức, đường dẫn và kiểu phản hồi vẫn do người gọi khai báo; chỉ body được kiểm bằng `satisfies`. Có thể sinh client theo `paths` (ví dụ `openapi-fetch`) khi cần.
2. **Một số nhóm không nằm trong hợp đồng:** các route BFF của Next.js (`/api/bff/**`, `/api/auth/**`) và `actuator`. Danh sách cho phép của BFF vẫn viết tay trong `route.ts`; chưa có test đối chiếu với tài liệu.
3. **Số nguyên 64 bit thành `number`.** Tiền VND tối đa 1.000.000.000 mỗi SKU và tổng giỏ dưới 10^13, nằm trong miền an toàn của JavaScript (2^53 ≈ 9×10^15), nhưng nếu sau này có số tiền lớn hơn thì cần chuỗi decimal (đã ghi trong tài liệu DB).
4. **Nhất quán theo quy ước, chưa có kiểm tra máy:** phân trang dùng chung `PageResponse`, lỗi dùng `ApiErrorResponse`, thời gian là ISO-8601, tiền là số nguyên. Chưa có test từ chối một endpoint mới vi phạm quy ước đó.
5. Tên schema của record lồng nhau là tên ngắn (`SkuResponse`, `LineResponse`); đủ rõ hiện nay, sẽ phải đổi nếu hai module cùng đặt tên.
6. App native đang dùng kiểu sinh ra nhưng chưa có luồng tạo client theo thao tác; chưa nghiệm thu lại trên thiết bị (chỉ chạy typecheck và test).
7. Chưa có kiểm tra tương thích ngược giữa hai phiên bản tài liệu (ví dụ cảnh báo khi đổi tên trường); diff review là biện pháp hiện tại. Phiên bản `info.version` cố định `v1`.

## 6. Kiểm chứng

- Backend **91 test đạt** (thêm `OpenApiSpecTests` và `OpenApiAccessTests`).
- `npm run contracts:check`, `npm run typecheck`, `npm run build`, `npm run format:check`, `npm run mobile:typecheck` và `npm run mobile:test` (13 test) đều đạt với kiểu sinh ra; web và mobile không cần sửa mã ngoài việc thêm `satisfies`.
- Dependency mới: `springdoc-openapi-starter-webmvc-api` 3.2.0 (backend), `openapi-typescript` 7.13.0 (công cụ build, `npm audit --omit=dev` cho `@vshop/contracts`: 0 lỗ hổng).
- Khi làm việc này phát hiện bước **"Validate reference database design" của CI thiếu V1003, V1004, V1005** trong danh sách DDL nên sẽ đỏ khi chạy trên GitHub (workflow chưa từng chạy vì chưa có remote). Đã bổ sung ba migration vào danh sách.
- Playwright **21 test đạt** trên bản build production với API, Keycloak và Redis thật sau khi đổi DTO sang enum và kiểu sinh ra (JSON phản hồi không đổi, đã được xác nhận bằng chạy thật). Bản xuất mobile (`npm run mobile:export`) vẫn thành công.
- API đang chạy **không** phục vụ tài liệu: `GET /v3/api-docs` trả 401.
