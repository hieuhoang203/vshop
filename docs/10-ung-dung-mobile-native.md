# VShop mobile — ứng dụng riêng cho iOS và Android

Bố cục màn khám phá đã cập nhật để sản phẩm xuất hiện trước banner; xem [báo cáo 11](11-san-pham-la-trung-tam.md) cho bằng chứng viewport và APK hiện hành.

Ngày triển khai: 19/11/2026. Increment: nền tảng native + catalog + lưu món + tài khoản + hồ sơ seller.

## 1. Nền tảng

React Native **0.88.1**, Expo SDK **58**, Expo Router, TypeScript; React **19.3.1** thống nhất với web và nằm trong peer range React Native. Expo dependency check loại trừ riêng React để giữ bản vá 19.3.1 thay vì baseline 19.3.0 của SDK. Expo Doctor và bundle hai nền tảng đã kiểm.

App dùng native views, không dùng WebView tải Next.js. Web giữ Radix/shadcn; app có component native và cùng nhận diện màu/font. Không chuyển Radix DOM/Recharts web vào app. Báo cáo admin hiện vẫn ở web; chưa cần chart native trong increment này.

Nguồn chính thức: [React Native components](https://reactnative.dev/docs/intro-react-native-components), [Expo Router](https://docs.expo.dev/router/installation/), [monorepos](https://docs.expo.dev/guides/monorepos/), [OAuth/OpenID](https://docs.expo.dev/guides/authentication/), [development builds](https://docs.expo.dev/develop/development-builds/introduction/).

## 2. Màn hình và luồng

| Màn hình | Luồng thực hiện |
|---|---|
| Khám phá | Catalog API → thẻ; đổi mood → danh mục; chọn ngẫu nhiên → sản phẩm từ dữ liệu đã tải |
| Tìm kiếm | Debounce 350 ms; từ khóa/danh mục/giá/sort → query mới; backend xử lý trước phân trang |
| Bộ lọc | Native modal bottom sheet: draft → áp dụng/reset; Back Android đóng modal |
| Chi tiết | API theo slug, giá/trạng thái hiện tại; lưu/bỏ lưu, native share; 404 không dùng snapshot làm chi tiết giả |
| Đã lưu | AsyncStorage, snapshot tối đa 100 món; giá ghi rõ là giá khi lưu |
| Tài khoản | System browser → Keycloak → callback app → PKCE exchange → profile từ Java API |
| Gian hàng | Đăng nhập → đọc; chưa có → gửi; REJECTED → bổ sung/gửi lại; SUBMITTED/APPROVED → theo dõi, không sửa trái trạng thái |

Bottom tabs: Khám phá / Tìm kiếm / Đã lưu / Tài khoản. Có safe area, phân trang, kéo tải lại, loading/error/empty/retry và haptic sau khi lưu thành công. Form seller giữ giới hạn 3–100 ký tự tên, 20–2000 giới thiệu; backend quyết định ownership/trạng thái.

Hình native là PNG raster hóa từ SVG fixture hiện có, không phụ thuộc web server. Catalog/giá/shop lấy API. Chưa mở checkout/payment/orders/chat/push/upload media thật; không dựng số liệu hoặc nút mua giả.

## 3. Tổ chức code

| Tầng | Trách nhiệm |
|---|---|
| `src/app` | Routes/tabs/stack, ghép provider; thin route re-export screen |
| Feature screen | Native view và tương tác người dùng |
| Catalog hooks | TanStack Query cache/abort/pagination/refetch |
| API adapter | Query/path dùng `@vshop/contracts` |
| `services/http-client` | Base URL, bearer, timeout 10s, lỗi HTTP/network; không UI |
| Auth adapter | Expo AuthSession/WebBrowser và SecureStore |
| `TokenSession` | Single-flight refresh, generation guard và ordered writes chống logout race |
| Saved model/provider | Kiểm snapshot, giới hạn, hàng đợi ghi; cập nhật UI sau persistence thành công |

Không thêm Zustand chỉ để có tên thư viện: local state/Context và TanStack Query đủ cho phạm vi hiện tại. Không sửa schema/migration hoặc backend nghiệp vụ trong increment này.

## 4. Xác thực và lưu trữ

Client `vshop-mobile`: public, không secret/password grant, Authorization Code, **PKCE S256 bắt buộc**, callback chính xác `vshop://auth/callback`, audience `vshop-api`. `scripts/init-mobile-auth.ps1` provision vào realm local đã tồn tại, giữ users/web client. Realm template hỗ trợ khởi tạo mới.

Access token chỉ ở memory; refresh token ở SecureStore. Không lưu token vào AsyncStorage/navigation/logs. Khôi phục bằng refresh rồi `/me`, không lấy decode JWT làm bằng chứng quyền. App gọi Java bằng bearer, không dùng cookie/BFF web. Java xác minh token/audience/role.

Mất mạng lúc refresh giữ credential để retry; invalid grant/token xóa phiên. Logout xóa local trước và gọi Keycloak logout; lỗi mạng phía server không khôi phục local. Refresh đang chạy không được ghi lại credential sau logout. SecureStore lỗi ghi/xóa phải hiển thị lỗi.

Món đã lưu thuộc thiết bị, giữ sau logout, chưa đồng bộ account. Custom scheme phù hợp local/pilot; trước production cần domain, Universal Links/App Links và kiểm deep-link collision trên thiết bị thật. PKCE vẫn bắt buộc, đổi callback cần cập nhật cả app/Keycloak.

## 5. Local và phân phối

API 8081, Keycloak 8180, Metro 8082. Android USB/emulator dùng `adb reverse`, giữ issuer thống nhất. Docker vẫn bind localhost, không tự mở DB/Keycloak ra LAN.

iPhone thật cần HTTPS/staging truy cập được, issuer trùng backend/token. Native iOS local build cần Mac/Xcode; cloud signing/distribution cần cấu hình tài khoản chủ dự án. Chưa tạo tài khoản hoặc gửi build lên store.

`eas.json` có development/preview/production. Production từ chối thiếu HTTPS hoặc bật development HTTP. Script Android local bật HTTP có chủ đích và tạo APK debug-signed với bundle nhúng; không phải bản ký store. Generated native projects, SDK và artifacts nằm trong ignore; tái tạo từ app config qua prebuild.

## 6. Kiểm chứng

- **13 tests** Jest/React Native Testing Library: session concurrency/logout race, storage failure, invalid grant/network retry, snapshot, filter/sort native, favorite persistence, 404 và hai luồng tạo/gửi lại hồ sơ seller.
- **Expo Doctor 20/20**, TypeScript mobile pass.
- Expo export tạo bundle Hermes Android/iOS; export iOS không phải IPA hoặc bằng chứng đã chạy iPhone.
- OAuth integration dùng Keycloak/API thật: code+PKCE → `/me` → chặn admin với buyer → refresh → logout → refresh bị từ chối. Callback sai và thiếu PKCE bị chặn.
- **APK Android build/install thành công**, ARM64 + x86_64, app ID `vn.vshop.mobile`, phiên bản 0.1.0. Đã kiểm chữ ký APK v2, signer Android Debug; đây là bản local, không phải bản phát hành store.
- **6 kiểm tra runtime trên emulator Android API 36 x86_64 đạt**: cold launch, tìm kiếm từ API thật, bộ lọc giá/Back/reset, snapshot qua force-stop/relaunch, detail từ API hiện tại và deep-link 404. Không dùng browser/WebView cho các màn này. Log không có lỗi JavaScript/Android crash của app trong lần smoke cuối.
- Script tái kiểm: `scripts/test-mobile-android.ps1`; báo cáo `.local/native-smoke-report.json`; ảnh `.local/screenshots/mobile-native-{home,search,saved,detail,detail-info,not-found}.png`. Đã xem ảnh thực và sửa nhãn tab bị cắt/safe-area cuối màn hình.
- **11 browser/OAuth E2E pass** trên Next production server. Lần chạy với Next dev server có một timeout khi mở popover; production đã chạy lại đầy đủ và đạt. Component tests dùng mock API/storage; native smoke và OAuth dùng API local thật.
- Chưa nghiệm thu iPhone thật, iOS signing, store review hoặc production deployment.

## 7. Chạy và bàn giao

Xem [hướng dẫn mobile](../apps/mobile/README.md). Root có `mobile`, `mobile:android`, `mobile:ios`, `mobile:typecheck`, `mobile:test`, `mobile:export`. CI thêm typecheck/tests/export; native signing/store build chưa vào CI.

Các file cần đọc khi phát triển tiếp: `app.config.ts`, `eas.json`, `src/config/environment.ts`, `features/auth/token-session.ts`, `features/auth/oauth.ts`, `services/http-client.ts`. Format theo root: `npm run format:check`.

Windows dùng CMake **3.31.6**/Ninja **1.12.1** thay mặc định CMake 3.22.1/Ninja 1.10.2 để xử lý đường dẫn C++ codegen dài; cấu hình chỉ áp dụng Windows. `LongPathsEnabled` trên máy đã là 1, không thay registry. Tham chiếu [hướng dẫn Expo](https://docs.expo.dev/troubleshooting/windows-long-paths/). Build source-only tiếp theo có thể dùng `-SkipPrebuild`; thay dependency/native config thì chạy prebuild bình thường.

Runtime native login/system-browser callback và SecureStore Keychain/Keystore chưa được kiểm đầu-cuối trên thiết bị thật. Protocol OAuth đã kiểm bằng Keycloak thật, session/storage concurrency bằng unit test. Không suy diễn rằng bundle iOS hoặc OAuth browser test là nghiệm thu đăng nhập trên iPhone/Android thật.

Backlog G01/G02/G03 chuyển sang **InProgress**, chưa Done vì còn gate thiết bị thật, iOS và nghiệp vụ SKU mở rộng. Các hạng mục checkout/payment/order/push/store submission vẫn Planned. Dependency audit hiện còn 22 cảnh báo moderate trong cây công cụ/dependency; cần đánh giá và xử lý trước bản phát hành production.
