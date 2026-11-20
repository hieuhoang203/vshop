# VShop — Hồ sơ khảo sát và kế hoạch phát triển

Ngày lập: **19/11/2026**. Phiên bản: **0.1 — để chủ sản phẩm xem xét**.

Đầu vào đã xác nhận: **Việt Nam; sàn nhiều người bán; một người phát triển; dự kiến ra mắt sau khoảng một năm**. Mốc mục tiêu tham chiếu: khoảng tháng 11/2027, tùy thời điểm bắt đầu và mức độ toàn thời gian.

## Đề xuất chính

Xây sàn giao dịch hàng hóa vật lý có kiểm soát, ưu tiên một nhóm ngành hàng và một tập người bán được mời. Hoàn thiện chu trình mua → thanh toán/COD → giao nhận → đổi trả/hoàn tiền → đối soát → chi trả, đồng thời cung cấp công cụ xử lý ngoại lệ cho người vận hành.

Web người mua, cổng người bán, cổng admin dùng Next.js/TypeScript. App người mua dùng React Native/Expo. Backend Java/Spring Boot được chia module nghiệp vụ, triển khai ban đầu dưới dạng **modular monolith + worker**. PostgreSQL là cơ sở dữ liệu giao dịch; Redis hỗ trợ cache; object storage tương thích S3 lưu file; Keycloak xác thực. Elasticsearch, MongoDB và Kubernetes chỉ bổ sung khi vượt tiêu chí đã đặt ra.

Đây là phương án kỹ thuật cho VShop, không phải kết luận về kiến trúc nội bộ Shopee.

## Tài liệu để duyệt

| Tài liệu                                                                       | Nội dung                                                                                         |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| [01-khao-sat-shopee.md](01-khao-sat-shopee.md)                                 | Khảo sát chức năng công khai, nguồn tham chiếu, khoảng trống và cải tiến                         |
| [02-nghiep-vu-va-luong.md](02-nghiep-vu-va-luong.md)                           | Vai trò, phạm vi, quy tắc, trạng thái, luồng đúng/sai, mô hình dữ liệu                           |
| [03-kien-truc-cong-nghe.md](03-kien-truc-cong-nghe.md)                         | Đánh giá stack, ranh giới module, bảo mật, lưu trữ, vận hành                                     |
| [04-lo-trinh-va-nghiem-thu.md](04-lo-trinh-va-nghiem-thu.md)                   | Lộ trình 52 tuần, phạm vi ra mắt, nguồn lực, rủi ro, điều kiện nghiệm thu                        |
| [05-backlog.csv](05-backlog.csv)                                               | Công việc có ID, ưu tiên, phụ thuộc, đầu ra, điều kiện nghiệm thu và ước lượng                   |
| [06-uat.csv](06-uat.csv)                                                       | Kịch bản nghiệm thu xuyên vai trò và các tình huống tranh chấp/race condition                    |
| [07-trien-khai-increment-01.md](07-trien-khai-increment-01.md)                 | Nền tảng đã triển khai, bằng chứng kiểm thử và giới hạn hiện tại                                 |
| [08-chuan-hoa-db-va-kien-truc.md](08-chuan-hoa-db-va-kien-truc.md)             | Kết quả tái cấu trúc, migration bảo toàn dữ liệu và kiểm thử                                     |
| [09-giao-dien-radix-va-bieu-do.md](09-giao-dien-radix-va-bieu-do.md)           | Giao diện mới, tương tác Radix, định nghĩa biểu đồ, API và kiểm thử                              |
| [10-ung-dung-mobile-native.md](10-ung-dung-mobile-native.md)                   | App React Native/Expo riêng, luồng mobile, Keycloak PKCE và kiểm thử                             |
| [11-san-pham-la-trung-tam.md](11-san-pham-la-trung-tam.md)                     | Bố cục hiện hành: sản phẩm trước banner, kiểm viewport web/native và APK cập nhật                |
| [12-catalog-seller-api.md](12-catalog-seller-api.md)                           | API seller ghi sản phẩm, biến thể, SKU, giá; quy tắc, giới hạn và kiểm chứng                     |
| [13-ton-kho-va-giu-hang.md](13-ton-kho-va-giu-hang.md)                         | API nhập/điều chỉnh tồn, vòng đời reservation, bất biến, kiểm thử cạnh tranh                     |
| [14-kiem-duyet-san-pham.md](14-kiem-duyet-san-pham.md)                         | Kiểm duyệt sản phẩm: máy trạng thái, API admin, ràng buộc phiên bản, giới hạn                    |
| [15-giao-dien-seller-admin-san-pham.md](15-giao-dien-seller-admin-san-pham.md) | UI web seller/admin: sản phẩm, SKU, tồn kho, duyệt hàng; BFF, giới hạn, kiểm chứng               |
| [16-gio-hang-va-bao-gia.md](16-gio-hang-va-bao-gia.md)                         | Giỏ hàng và báo giá: API, quy tắc giá, UI web, giới hạn, kiểm chứng                              |
| [17-phien-dang-nhap-va-mfa.md](17-phien-dang-nhap-va-mfa.md)                   | Phiên lưu ở Redis, refresh token, MFA bắt buộc cho admin, vận hành local                         |
| [18-so-dia-chi-buyer.md](18-so-dia-chi-buyer.md)                               | Sổ địa chỉ buyer: mã hóa dữ liệu cá nhân, báo giá gắn địa chỉ, UI, giới hạn                      |
| [19-openapi-contracts.md](19-openapi-contracts.md)                             | OpenAPI sinh từ Java, kiểu TypeScript sinh từ OpenAPI, kiểm tra lệch trong CI, quy trình đổi API |
| [architecture/README.md](architecture/README.md)                               | Quy ước từng tầng, entity/DTO/repository, format và ArchUnit                                     |
| [database/README.md](database/README.md)                                       | ERD, bảng/quan hệ, từ điển cột, DDL và invariant giao dịch                                       |

CSV dùng UTF-8 có BOM để mở tiếng Việt trong Excel. Import theo cột `ID`, `DependsOn`, `BusinessRules`, `Acceptance`; trạng thái được cập nhật theo increment. Hiện tại: 2 Done, 26 InProgress, 102 Planned (B03, B07 Done; C01, C06–C10, D01, D02 InProgress). Các ước lượng vẫn là kế hoạch ban đầu, không phải timesheet thực tế.

## Ranh giới bằng chứng

Khảo sát dựa trên tài liệu chính thức công khai được tìm kiếm/đọc ngày 19/11/2026; chưa đăng nhập tài khoản mua/bán, chưa đặt đơn thật và chưa phỏng vấn người dùng. Không có quyền truy cập admin Shopee. Chức năng admin, thuật toán, cấu trúc dữ liệu, cơ chế khóa và hạch toán nêu trong tài liệu là **thiết kế đề xuất**. Không tuyên bố đã khảo sát toàn bộ Shopee hoặc đã chứng minh luồng vận hành thực tế của họ.

Các mốc SLA, tỷ lệ phí, hạn mức, ngân sách, quy tắc thuế và danh mục hạn chế của VShop phải được duyệt riêng. Không sao chép tỷ lệ phí hoặc thời hạn Shopee vào code. Tài liệu này là cơ sở để viết SRS/user story và kiểm chứng tiếp, chưa thay thế xác nhận của đối tác thanh toán, vận chuyển hoặc chuyên môn pháp lý.

Đã phát triển và chuẩn hóa nền tảng local; xem [DB/kiến trúc hiện hành](08-chuan-hoa-db-va-kien-truc.md), [giao diện hiện hành](09-giao-dien-radix-va-bieu-do.md) và [hướng dẫn chạy](../README.md). Increment 01 là snapshot trước tái cấu trúc. Chưa triển khai production hoặc hoàn thành toàn bộ nghiệp vụ marketplace.
