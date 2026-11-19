# VShop — giao diện Radix và biểu đồ hoạt động

Snapshot giao diện ban đầu. Bố cục storefront hiện hành ưu tiên sản phẩm, xem [báo cáo 11](11-san-pham-la-trung-tam.md); lựa chọn Radix/shadcn và luồng nghiệp vụ bên dưới vẫn được giữ.

Ngày kiểm chứng: **19/11/2026**. Phạm vi: web Next.js hiện có cho buyer, seller và admin; chưa bao gồm app native.

## 1. Quyết định thiết kế

Áp dụng yêu cầu của chủ sản phẩm: giao diện dùng **Radix UI**, biểu đồ theo **shadcn Area Chart**. Đã đọc tài liệu chính thức trước khi tích hợp:

| Nguồn | Áp dụng |
|---|---|
| [Radix UI](https://www.radix-ui.com/) và [Themes setup](https://www.radix-ui.com/themes/docs/overview/getting-started) | Theme, button, text field, textarea, tooltip, select, segmented control, slider và table |
| [Radix Dialog](https://www.radix-ui.com/themes/docs/components/dialog) | Tìm nhanh, danh sách lưu và xem nhanh; quản lý focus, Escape và tên truy cập |
| [shadcn Area Charts](https://ui.shadcn.com/charts/area) và [Chart](https://ui.shadcn.com/docs/components/chart) | AreaChart, gradient, tooltip, ChartConfig và ChartContainer |

Đây là thiết kế riêng cho VShop: tím iris, lime, hồng, bố cục bento, sticker và chữ Manrope có hỗ trợ tiếng Việt. Font được phục vụ từ file local. Animation nhẹ, tắt theo `prefers-reduced-motion`; chart không chạy animation để tránh chuyển động và đọc số sai trong lúc đổi khoảng thời gian.

Không cài cả bộ shadcn/Tailwind: adapter `components/ui/chart.tsx` điều chỉnh mẫu ChartContainer/config/tooltip sang CSS đang dùng và Radix Themes. Chart vẫn dùng Recharts theo mẫu shadcn Area; không tuyên bố sao chép nguyên trang mẫu. Attribution và giấy phép nằm trong `THIRD_PARTY_NOTICES.md`.

## 2. Thành phần đã triển khai

| Khu vực | Thay đổi | Hành vi thực tế |
|---|---|---|
| Header | Nhận diện VShop mới, tìm kiếm, góc đã lưu, menu tài khoản | Tìm bằng form GET; Ctrl/⌘ K mở tìm nhanh; menu giữ logout POST |
| Trang chủ | Hero bento, mood selector, danh mục, banner seller | Đổi mood thay hình/nội dung và liên kết danh mục tương ứng |
| Chọn giùm mình | Gợi ý ngẫu nhiên và thử món khác | Chọn từ sản phẩm API đã tải ở trang hiện tại; tránh lặp ngay món trước nếu có lựa chọn khác; không phải AI recommendation |
| Thẻ sản phẩm | Heart, xem nhanh, chi tiết, 2 mật độ lưới | Tên/hình dẫn đến chi tiết thật; các nút độc lập, không lồng button trong link |
| Bộ lọc | Slider giá, preset, select sắp xếp, chip và phân trang | URL giữ điều kiện; backend lọc và sort trước phân trang, không chỉ lọc trang đang nhìn |
| Chi tiết | Gallery, thông tin shop, lưu/chia sẻ, sản phẩm liên quan | Related lấy cùng danh mục từ API; nút copy báo trạng thái thật |
| Seller | Form và trạng thái theo ngôn ngữ hình ảnh mới | Giữ luồng gửi hồ sơ → cần bổ sung → gửi lại → được duyệt |
| Admin | Panel hoạt động và form xét duyệt Radix | 7/30/90 ngày, đổi chuỗi, tooltip, bảng số liệu, loading/error/retry |

Các nội dung và hình sản phẩm hiện là fixture local. Chưa triển khai giỏ hàng, checkout hoặc thanh toán; giao diện ghi rõ chưa tiếp nhận đặt hàng.

## 3. Quy tắc lưu món

1. Heart lưu snapshot `{ id, slug, name, price, imageKey }` trong `localStorage["vshop.saved-products.v1"]`.
2. Reload vẫn giữ danh sách; sự kiện `storage` cập nhật giữa các tab cùng origin. Tối đa 100 món.
3. Có kiểm tra kiểu, slug và hình cho dữ liệu đọc lại; storage bị chặn sẽ báo lỗi, không hiển thị đã lưu giả.
4. Danh sách ghi **giá khi lưu**; vào chi tiết để kiểm tra giá/trạng thái hiện tại.
5. Đây là dữ liệu trên thiết bị, chưa đồng bộ tài khoản hoặc thiết bị khác. Token đăng nhập vẫn chỉ nằm trong session cookie HttpOnly mã hóa; không ghi vào localStorage.
6. Toast là một lớp Radix riêng: khi toast vừa xuất hiện trên dialog, Escape đóng toast trước, Escape tiếp theo đóng dialog. Có nút đóng dialog trực tiếp.

## 4. Dữ liệu biểu đồ và API

Luồng đọc: admin page (SSR) → Java API; đổi khoảng ngày → `GET /api/bff/admin/analytics?days=7|30|90` → Java API. BFF đọc session phía server, trả 401 khi chưa đăng nhập, 403 khi thiếu ADMIN; Java tiếp tục xác minh JWT và quyền ADMIN.

Java endpoint: `GET /api/v1/admin/analytics/shop-activity?days=N`; N từ 1 đến 90. Service xác định ngày bằng Clock và `Asia/Ho_Chi_Minh`, gồm ngày hiện tại và N−1 ngày trước. Repository gap-fill ngày không hoạt động bằng 0, không tạo số liệu tăng trưởng mẫu.

| Chỉ số | Nguồn | Định nghĩa |
|---|---|---|
| Hồ sơ tạo mới (`submitted`) | `merchant.shop.created_at` | Số shop được tạo trong ngày; không đếm lượt gửi lại; có thể gồm fixture/test local |
| Lượt chấp thuận (`approved`) | `merchant.shop_review` | Số quyết định APPROVED được lưu trong ngày, không phải số shop đang ở trạng thái APPROVED |
| Cần bổ sung (`rejected`) | `merchant.shop_review` | Số quyết định REJECTED được lưu trong ngày; xuất hiện trong bảng đối chiếu |

Biểu đồ vẽ hai chuỗi tạo mới/chấp thuận, không xếp chồng; người dùng xem riêng từng chuỗi hoặc cả hai. Bảng có cả ba chỉ số. Không suy ra tỷ lệ duyệt từ hai chuỗi vì các quyết định có thể thuộc hồ sơ tạo từ ngày khác.

Ngày trước khi có lịch sử `shop_review` không được tự suy diễn quyết định từ trạng thái shop. Dữ liệu hiển thị là bản ghi thực trong **DB local**, bao gồm fixture và E2E; không phải thống kê kinh doanh production.

Khi request lỗi: hiển thị lỗi, số tổng là “—”, không vẽ dữ liệu mẫu thay thế; có retry. Khi đổi khoảng liên tiếp: hủy request cũ bằng AbortController. BFF không cache và timeout sau 6 giây.

Catalog endpoint bổ sung `minPrice`, `maxPrice` và `sort=newest|price-asc|price-desc`. Giá dùng giá nhỏ nhất của SKU active; từ chối khoảng ngược và sort không hợp lệ. Danh mục, tổng kết quả, sort và phân trang được xử lý từ DB. UI slider giới hạn 0–2 triệu VND và ghi đúng giới hạn ở preset; API không bị giới hạn bởi slider.

## 5. Cấu trúc code và thư viện

| Đường dẫn | Trách nhiệm |
|---|---|
| `apps/web/src/components/discovery/` | Hero/mood, tìm nhanh, lưu món, quick view, lọc/sort và hành động chi tiết |
| `apps/web/src/components/admin/` | Panel hoạt động và điều khiển biểu đồ |
| `apps/web/src/components/ui/chart.tsx` | Adapter ChartContainer/tooltip/config dùng chung |
| `apps/web/src/components/providers.tsx` | Radix Theme, toast và state lưu món local |
| `apps/web/src/styles/` | Foundation, storefront, detail, overlays, portals, analytics, responsive, motion và footer |
| `apps/web/src/app/globals.css` | Chỉ ghép các stylesheet theo thứ tự cascade |
| `apps/web/src/lib/format.ts` | Format tiền/ngày dùng chung, không phụ thuộc server auth |
| `backend/.../merchant/controller`, `dto`, `mapper`, `model`, `service`, `repository/jdbc` | API báo cáo theo đúng tầng; SQL chỉ ở repository |
| `backend/.../config/TimeConfiguration.java` | Clock có thể thay trong kiểm thử ranh giới ngày |

Phiên bản khóa trong package/lock: `@radix-ui/themes` 3.3.0, Radix Toast 1.3.1, ToggleGroup 1.2.1, Recharts 3.8.0. Không thay schema, migration hoặc dữ liệu cũ cho increment UI này.

## 6. Kiểm chứng

| Kiểm tra | Kết quả |
|---|---|
| Backend | 34 tests pass: 8 ArchUnit, 11 database constraints, 13 integration và 2 analytics time-window tests |
| Web | Production build, TypeScript và Prettier check pass |
| Browser | 10 Playwright tests pass trên Chromium với production Next server |
| Responsive | 360, 390, 768 và 1440 px không tràn document ngang |
| Luồng xuyên vai trò | Buyer bị chặn admin; seller gửi → admin yêu cầu bổ sung → seller gửi lại → admin duyệt, dữ liệu lưu thật |
| Tương tác | Heart/reload/remove, dialog/keyboard/focus, random đổi món, mood/reduced-motion, lọc/sort/density, Ctrl+K |
| Biểu đồ | Tổng đối chiếu API, đổi ngày, bảng số liệu; giả lập HTTP 503 để kiểm lỗi/retry, không giả lập số liệu thành công |
| Bảo mật route mới | Buyer nhận 403, chưa đăng nhập nhận 401; API backend có kiểm quyền riêng |

Ảnh chụp local tại `.local/screenshots/storefront-genz-1440.png`, `storefront-genz-390.png`, `admin-genz-desktop.png`, `admin-genz-mobile.png`. Đã xem ảnh browser để kiểm bố cục, không chỉ đọc CSS. Đây là kiểm thử local; chưa nghiệm thu production, thiết bị di động thật hoặc screen reader.

## 7. Hướng tiếp theo

Khi triển khai dữ liệu người bán/sản phẩm thật, bổ sung media từ object storage và workflow kiểm duyệt. Đồng bộ wishlist cần bảng/API theo account và quy tắc merge guest; không kéo localStorage thành nguồn sự thật nghiệp vụ. Chart doanh thu/đơn hàng chỉ bổ sung sau khi có order/payment/refund/settlement và định nghĩa metric đã thống nhất.
