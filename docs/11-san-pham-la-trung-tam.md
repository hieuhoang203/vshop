# VShop — sản phẩm là trung tâm

Cập nhật 19/11/2026 theo yêu cầu: người dùng mở web/app phải thấy sản phẩm, không bị banner chiếm màn hình đầu. Tài liệu này thay thế bố cục hero trong báo cáo 09/10; nghiệp vụ và API không thay đổi.

## Bố cục hiện hành

| Khu vực | Thay đổi |
|---|---|
| Web | Sau tìm kiếm là tiêu đề ngắn, danh mục/bộ lọc và lưới sản phẩm; bỏ hero/slogan lớn, category tiles trùng lặp và marquee |
| Lưới web | Desktop 5 cột, chế độ nhiều hơn 6 cột; thu về 4/3/2 cột theo viewport |
| Thẻ sản phẩm | Ảnh vuông, tên và giá nổi bật; giảm khoảng trống, bỏ nhãn category đè trên ảnh ở storefront |
| Web điện thoại | Ẩn topbar slogan và hàng menu danh mục trùng với bộ lọc; giữ tìm kiếm, tài khoản và danh sách |
| App | Thanh tìm kiếm + nút gợi ý nhỏ → danh mục → sản phẩm; bỏ hàng logo/CTA lớn và hero khỏi đầu danh sách |
| Khám phá phụ | Mood sau danh sách sản phẩm, vẫn đổi danh mục và gợi ý từ sản phẩm thật |
| Seller | Lời mời mở shop ở dưới danh sách; không cạnh tranh với sản phẩm ở màn đầu |

Giữ màu tím, font Manrope, lưu món, xem nhanh, tìm kiếm/lọc/sắp xếp và navigation theo quyền. Không bổ sung số đã bán, rating, giảm giá hoặc huy hiệu không có dữ liệu nghiệp vụ. Danh mục local hiện có 6 sản phẩm fixture, không nhân bản để giả lập nhiều hàng.

## Tổ chức code

- `apps/web/src/app/page.tsx`: catalog trước, discovery tools sau; heading chính là “Khám phá sản phẩm”.
- `components/discovery/discovery-tools.tsx`: tiện ích khám phá nhỏ dùng Radix; đã bỏ component hero cũ.
- `styles/commerce.css`: bố cục storefront ưu tiên sản phẩm, tách khỏi style dùng chung; dọn CSS hero/category cũ.
- `apps/mobile/src/features/catalog/discover-screen.tsx`: header ngắn và dữ liệu catalog.
- `discovery-extras.tsx`: state mood riêng ở footer, không đẩy sản phẩm xuống dưới.
- `catalog-grid.tsx`: hỗ trợ footer tùy chọn; giữ phân trang/refetch/error handling.

Quy tắc sản phẩm trước banner đã được ghi trong `AGENTS.md` để các lần phát triển tiếp giữ nhất quán.

## Nghiệm thu

- Web: không cuộn trang, phải thấy **ảnh, tên và giá của hai sản phẩm đầu**; kiểm bằng bounding box ở 1440×900, 1366×768, 390×844 và 360×740. Không tràn document ngang; discovery tools nằm sau catalog.
- Probe thêm 768×1024. Ở web 390 px, hàng ảnh bắt đầu khoảng y=304, giá kết thúc khoảng y=604; ở 360 px, giá kết thúc khoảng y=590. Có ảnh viewport thực, không chỉ full-page screenshot.
- Android: APK được build/cài thật; smoke kiểm hai giá đầu ở trên thanh tab trước khi cuộn, đồng thời giữ tìm kiếm, lọc, Back, persistence, detail và 404. Kiểm thêm cấu hình màn nhỏ 960×1680 px ở density 420 (~366×640 dp).
- 12 Playwright E2E, 13 mobile unit/component tests; build web, TypeScript, Prettier, bundle Android/iOS. Native runtime kiểm trên Android emulator; chưa nghiệm thu iPhone thật.

Ảnh: `.local/screenshots/products-first-web-1440.png`, `products-first-web-390.png`, `products-first-web-360.png`, `mobile-native-home.png`, `products-first-native-small.png`. Kết quả đo web: `.local/products-first-web.json`; native: `.local/native-smoke-report.json`.

APK hiện hành: `.local/artifacts/vshop-android-local.apk`, có checksum cạnh file. Đây là bản local dùng API/Keycloak local, không phải store release; hướng dẫn tại `apps/mobile/README.md`.
