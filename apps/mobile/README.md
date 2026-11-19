# VShop mobile — iOS & Android

Ứng dụng React Native/Expo riêng. Native views, Expo Router và Spring Boot API; không nhúng website trong WebView.

## Chạy phát triển

Tại root repository, sau khi đã khởi tạo Docker/API theo README chính:

```powershell
npm ci
pwsh -File scripts/init-mobile-auth.ps1
Copy-Item apps/mobile/.env.example apps/mobile/.env.local
npm run mobile
```

Metro chạy **8082**, dùng development build thay vì Expo Go. Cần Android SDK, Java 21 và thiết bị USB/emulator để tạo/cài bản native:

```powershell
# ANDROID_HOME trỏ đến Android SDK; hỗ trợ SDK local tại .local/android-sdk.
pwsh -File scripts/connect-mobile-android.ps1
npm run mobile:android
```

`adb reverse` nối localhost Android về PC cho API 8081, Keycloak 8180 và Metro 8082. Có nhiều thiết bị thì dùng `-Device SERIAL`. Không đổi issuer mobile sang `10.0.2.2` khi backend vẫn xác minh issuer localhost.

## APK local có bundle đi kèm

```powershell
pwsh -File scripts/build-mobile-android.ps1
```

Artifact: `.local/artifacts/vshop-android-local.apk`, mặc định ARM64 + x86_64. Đây là **APK local ký debug**, dùng task assembleRelease để nhúng JS bundle và chạy không cần Metro. HTTP chỉ dành cho development đã bật rõ; app vẫn cần API/Keycloak, không phải app offline.

Trên Windows, cài thêm CMake **3.31.6** trong SDK Tools; cấu hình app tự chọn phiên bản này theo hướng dẫn Expo về đường dẫn dài. Máy cần bật long paths. Đã kiểm `LongPathsEnabled=1` trên máy hiện tại, không tự sửa registry. Chỉ đổi source JS thì có thể thêm `-SkipPrebuild` để dùng lại native project đã đồng bộ.

Sau khi build và thiết bị sẵn sàng:

```powershell
# Nếu dùng SDK local đã chuẩn bị trong checkout này:
$env:ANDROID_HOME = (Resolve-Path .local/android-sdk).Path
& "$env:ANDROID_HOME/platform-tools/adb.exe" install -r .local/artifacts/vshop-android-local.apk
pwsh -File scripts/test-mobile-android.ps1 -Device emulator-5554
```

Native smoke dùng fixture local, không tạo hồ sơ/đơn hàng; kiểm tra tìm kiếm, lọc, persistence và deep link. Bản APK vẫn cần API local chạy và `adb reverse` khi dùng localhost. Có SHA-256 cạnh APK tại `.local/artifacts/`.

## iOS và phân phối

Trên Mac có Xcode: `npm run mobile:ios`. Simulator truy cập localhost của Mac; API/Keycloak phải chạy hoặc forward tới Mac đó. iPhone thật cần endpoint HTTPS truy cập được, issuer thống nhất với backend và tài khoản Apple để ký/phân phối. `expo export` tạo bundle JS/Hermes, **không tạo IPA**.

`eas.json` có profile development/preview/production. Chưa cấu hình Expo project ID hoặc tài khoản store. Trước cloud build, chạy `eas init` bằng tài khoản chủ dự án và cấu hình API/issuer HTTPS; production chặn development/HTTP. `vn.vshop.mobile` là định danh dự kiến, chưa xác nhận quyền sở hữu trên store.

## Cấu trúc

| Thư mục | Trách nhiệm |
|---|---|
| `src/app/` | Thin routes, stack và tabs |
| `src/features/catalog/` | Khám phá, tìm kiếm, lọc, chi tiết, query hooks |
| `src/features/saved/` | Snapshot đã lưu trên thiết bị |
| `src/features/auth/` | OAuth PKCE, session/refresh và tài khoản |
| `src/features/merchant/` | Hồ sơ shop và gửi lại theo trạng thái API |
| `src/services/` | HTTP transport/timeout/error |
| `src/components/`, `src/theme/` | Native UI, hình sản phẩm, màu/font/format |
| `tests/` | Session concurrency, snapshot và màn hình native |

## Kiểm tra

```powershell
npm run mobile:typecheck
npm run mobile:test
npm run mobile:export
npm run test:e2e -- mobile-auth.spec.ts
# Trong apps/mobile:
npx expo-doctor
```

OAuth test cần Keycloak/API local, client đã provision và `.local/accounts.json`. Không in token hoặc bật trace đăng nhập. Component tests dùng API fixtures; OAuth test dùng Keycloak/API thật. Xem [báo cáo triển khai](../../docs/10-ung-dung-mobile-native.md) cho ranh giới kiểm chứng native.
