# 01. Khảo sát Shopee và hướng áp dụng cho VShop

## 1. Phương pháp và mức tin cậy

Phạm vi: Shopee Việt Nam; tài liệu công khai người mua/người bán; đối chiếu cho mô hình marketplace. Ngày truy cập: 19/11/2026. Các trang có thể đổi nội dung theo thời điểm, ngành hàng, tài khoản và chương trình. Khi thực thi BA cần lưu bản nội dung/ngày hiệu lực hợp lệ, tránh phụ thuộc duy nhất vào URL sống.

Ký hiệu: **X** = có nguồn chính thức hỗ trợ nhóm chức năng; **ĐX** = đề xuất cho VShop; **CX** = chi tiết chưa kiểm chứng qua tài khoản/thử nghiệm. X chỉ xác nhận nội dung nguồn, không có nghĩa đã kiểm thử sản phẩm.

## 2. Danh sách nguồn

| ID | Nguồn chính thức | Sử dụng |
|---|---|---|
| S01 | [Điều khoản dịch vụ](https://help.shopee.vn/portal/4/article/77243) | Mô hình sàn, tài khoản phụ, thanh toán và phí |
| S02 | [Đăng ký bán hàng](https://shopee.vn/blog/?p=189017) | Hồ sơ và thông tin thuế người bán |
| S03 | [Đăng sản phẩm](https://shopee.vn/blog/?p=190299) | Nội dung, thuộc tính, phân loại hàng, giá và kho |
| S04 | [Quy định đăng bán](https://help.shopee.vn/portal/4/article/77246) | Kiểm soát danh mục, giá và thông tin hàng hóa |
| S05 | [Hướng dẫn đặt hàng](https://shopee.vn/blog/?p=189519) | Luồng đặt hàng người mua |
| S06 | [Voucher vận chuyển](https://help.shopee.vn/portal/4/article/79243) | Kết hợp ưu đãi shop, sàn, vận chuyển |
| S07 | [Hủy đơn](https://help.shopee.vn/portal/4/article/79182) | Điều kiện hủy theo trạng thái/đơn vị vận chuyển |
| S08 | [Shopee Đảm Bảo](https://help.shopee.vn/portal/4/article/79314) | Bảo vệ người mua, quyền yêu cầu sau khi xác nhận nhận hàng |
| S09 | [Chính sách trả hàng/hoàn tiền](https://help.shopee.vn/portal/4/article/77251) | Phạm vi, điều kiện, xử lý tranh chấp |
| S10 | [Hoàn voucher và tiền khi hủy](https://help.shopee.vn/portal/4/article/79296) | Tiền và voucher là hai quy trình xử lý khác nhau |
| S11 | [Giao không thành công — người mua](https://help.shopee.vn/portal/4/article/206961) | Không nhận được hàng, hoàn về và hoàn tiền |
| S12 | [Giao không thành công — người bán](https://help.shopee.vn/portal/1/article/102523) | Theo dõi hàng hoàn, kiểm nhận và nhập kho |
| S13 | [Số dư tài khoản](https://help.shopee.vn/portal/4/article/79447) | Số dư và yêu cầu thanh toán về ngân hàng |
| S14 | [Quản lý và phân quyền nhân viên](https://help.shopee.vn/portal/1/article/98758-Thi%E1%BA%BFt%20l%E1%BA%ADp%20Qu%E1%BA%A3n%20L%C3%BD%20v%C3%A0%20ph%C3%A2n%20quy%E1%BB%81n%20nh%C3%A2n%20vi%C3%AAn) | Nhân viên được phân quyền; tài liệu thuộc công cụ quản lý, không suy rộng mọi loại shop |
| S15 | [Quản lý đơn trên nhiều nền tảng](https://help.shopee.vn/portal/1/article/98754) | Xử lý đóng gói và nhiều shop; nhóm công cụ mở rộng |
| S16 | [Đánh giá sản phẩm](https://shopee.vn/blog/?p=59675) | Đánh giá sau mua, nội dung đa phương tiện |
| S17 | [FAQ Livestream](https://help.shopee.vn/portal/10/article/164061) | Live/Video và báo cáo chuyển đổi |
| S18 | [Dịch vụ hiển thị trên ứng dụng](https://ads.shopee.vn/learn/faq/398/2239) | Công cụ quảng cáo/hiển thị, ngân sách và quản lý số dư |

S12/S14/S15 có thể thuộc sản phẩm hỗ trợ quản lý của Shopee; cần kiểm tra điều kiện sử dụng trước khi coi là tính năng tiêu chuẩn của Seller Centre. Không dùng PDF đào tạo cũ làm chuẩn cho mức phí/SLA hiện hành.

## 3. Ma trận chức năng khảo sát → VShop

| Nhóm | Kết quả khảo sát | Hướng áp dụng VShop | Giai đoạn |
|---|---|---|---|
| Tài khoản mua/bán | X: sàn kết nối người mua và người bán; có hồ sơ đăng ký bán [S01,S02] | Một danh tính có thể mua hàng và tham gia shop; trạng thái seller riêng | Ra mắt |
| Khám phá/tìm kiếm | X: tài liệu đặt hàng đề cập tìm và chọn hàng [S05]; CX: ranking, cá nhân hóa | Tìm tiếng Việt, danh mục, lọc giá/thuộc tính, sắp xếp có công bố | Ra mắt; ML sau |
| Trang sản phẩm | X: mô tả, ảnh/video, thuộc tính, phân loại, giá và kho [S03,S04] | SKU độc lập, chính sách và người bán rõ ràng; ảnh bắt buộc, video sau | Ra mắt |
| Gian hàng | X: đăng ký và bán qua shop [S02,S03] | Hồ sơ shop, chính sách, danh sách sản phẩm và điểm đánh giá | Ra mắt |
| Giỏ và checkout | X: luồng đặt hàng, voucher [S05,S06]; CX: transaction nội bộ | Giỏ nhiều shop; một checkout tạo đơn con theo shop; snapshot giá | Ra mắt |
| Ưu đãi | X: voucher shop/sàn/vận chuyển có thể kết hợp theo điều kiện [S06] | Rule engine giới hạn; giải thích mã không hợp lệ; ghi người tài trợ | Ra mắt bản đơn giản |
| Thanh toán | X: nhiều phương thức được đề cập [S01]; CX: hợp đồng/API | COD + một PSP có sandbox và khả năng hoàn tiền; không tự xây ví | Ra mắt |
| Hủy | X: khác nhau theo trạng thái và carrier; có ngoại lệ theo nhóm người dùng [S07] | Hủy ngay trước xử lý; yêu cầu hủy khi đã chuẩn bị; sau bàn giao theo năng lực carrier | Ra mắt |
| Chuẩn bị/giao nhận | X: quản lý đóng gói, hàng giao lỗi, hoàn về [S11,S12,S15] | Một carrier, tạo nhãn, tracking, xử lý nhận sai và hàng hoàn | Ra mắt |
| Bảo vệ người mua | X: còn quyền yêu cầu trong hạn kể cả đã nhấn nhận hàng [S08] | Không khóa quyền trả hàng chỉ vì đơn hiển thị hoàn thành | Ra mắt |
| Trả hàng/hoàn tiền | X: yêu cầu có thể cần xử lý bởi sàn [S09] | Case theo từng dòng hàng; bằng chứng; chấp thuận/từ chối; kháng nghị | Ra mắt |
| Số dư/chi trả seller | X: có số dư, yêu cầu thanh toán và điều chỉnh [S01,S13] | Sổ cái phải trả seller; giữ/khả dụng/đang chi; đối soát trước chi | Ra mắt |
| Phí sàn | X: điều khoản nêu các loại phí và cơ sở tính [S01] | Biểu phí VShop có phiên bản/ngày hiệu lực; không sao chép tỷ lệ Shopee | Ra mắt |
| Nhân viên shop | X: tài khoản phụ/phân quyền [S01,S14] | Owner, xử lý đơn, catalog, CSKH; quyền tài chính riêng | Sau pilot; owner đủ ở pilot |
| Chat/CSKH | X: kênh liên hệ/hỗ trợ trong tài liệu [S07,S09]; CX: tính năng inbox đầy đủ | Ticket liên kết đơn trước; chat trực tiếp sau, có báo cáo vi phạm | Ticket ra mắt |
| Đánh giá | X: nội dung sau mua, ảnh/video [S16] | Verified purchase; chống spam; seller phản hồi, không tự xóa đánh giá xấu | Ra mắt bản cơ bản |
| Ads/Live/Video | X: tồn tại nhóm chức năng [S17,S18] | Hoãn; chỉ làm khi có traffic, ngân sách moderation và attribution | Sau năm đầu |
| Admin nội bộ | Không có quyền truy cập; **ĐX** | Quản trị shop, nội dung, giao dịch, case, đối soát, cấu hình, audit | Ra mắt |
| Phát hiện gian lận | CX: thuật toán/quy tắc nội bộ | **ĐX**: rule đơn giản, hàng đợi kiểm tra, quyền kháng nghị | Ra mắt tối thiểu |

## 4. Luồng nhìn từ từng vai trò

Người mua: khám phá → xem SKU/chính sách → thêm giỏ → chọn địa chỉ/phương thức → nhận báo giá → đặt hàng → thanh toán nếu trả trước → theo dõi từng shop/kiện → nhận hàng → đánh giá hoặc mở case. Hủy, giao lỗi và hoàn tiền là các nhánh có trạng thái riêng [S03,S05,S07–S11,S16].

Người bán: đăng ký/hồ sơ → thiết lập shop → đăng và cập nhật hàng → xử lý đơn → đóng gói/bàn giao → theo dõi giao và hàng hoàn → phản hồi case → theo dõi các khoản thu/phí/điều chỉnh → nhận tiền [S02,S03,S09,S12,S13,S15].

Quản lý toàn sàn (**ĐX**): duyệt người bán và nội dung → thiết lập chính sách/biểu phí → giám sát đơn/SLA → xử lý khiếu nại và gian lận → đối soát PSP/carrier/ngân hàng → duyệt chi trả → theo dõi chỉ số và audit. Quyền xem, quyết định và thực hiện tiền phải phân biệt.

## 5. Cải tiến ưu tiên cho VShop

| Cải tiến đề xuất | Quy tắc triển khai | Chỉ số kiểm chứng sau pilot |
|---|---|---|
| Giá cuối rõ trước đặt hàng | Tách tiền hàng, voucher, ship, khoản người mua thực trả; không có phí xuất hiện sau xác nhận | Tỷ lệ thoát checkout, ticket lệch giá |
| Giải thích mã giảm giá | Trả reason code cụ thể: thiếu giá trị tối thiểu, sai shop, hết lượt, sai phương thức | Ticket voucher; tỷ lệ áp thành công |
| Một timeline xuyên suốt | Timeline gom sự kiện đơn, tiền, vận chuyển, case; giữ mã tham chiếu từng bên | Thời gian tra soát một vụ việc |
| Minh bạch tiền seller | Mỗi khoản khấu trừ liên kết đơn và phiên bản phí; bảng kê tải được | Số chênh lệch chưa xử lý, ticket doanh thu |
| Đổi trả cân bằng | Chính sách theo ngành/lý do; bằng chứng hai phía; quy trình kháng nghị | Thời gian giải quyết; tỷ lệ đảo quyết định |
| Ưu tiên chất lượng tìm kiếm | Thuộc tính chuẩn; lọc thực dùng; sản phẩm bị khóa không thể mua dù index chưa cập nhật | Search zero-result; tỷ lệ click/mua |
| Vận hành ít nhân sự | Dashboard việc cần làm theo hạn; retry an toàn; báo động tích hợp lỗi | Case quá hạn; backlog webhook/worker |

Đây là giả thuyết cải tiến cần đo lường, không khẳng định Shopee đang thiếu các khả năng này. Không đặt chỉ tiêu tăng trưởng theo phần trăm trước khi có baseline.

## 6. Các khảo sát tiếp theo trước khi khóa SRS

1. Quan sát có tài khoản và ghi bằng chứng: web/mobile buyer, shop seller được cho phép; test đổi SKU, phí ship, voucher, hủy và case; không phát sinh giao dịch ngoài phạm vi cho phép.
2. Phỏng vấn khoảng 5 người mua, 3 người bán và 1–2 người có kinh nghiệm vận hành; mẫu thăm dò, không đại diện thống kê toàn thị trường.
3. Chọn ngành hàng đầu tiên; xác nhận chứng từ, hàng cấm/hạn chế, bao gói và điều kiện hoàn.
4. Lấy tài liệu/hợp đồng PSP và carrier: người đứng tên giao dịch, thu hộ, hoàn COD, phân bổ nhiều shop, phí, webhook, settlement file, SLA và nghĩa vụ xử lý tiền.
5. Chạy workshop 10 tình huống khó: trả một phần, seller hết hàng, payment muộn, giao nhầm, mất kiện, hủy khi đang giao, seller đã rút tiền, voucher nhiều shop, giả mạo webhook, carrier không trả tiền COD.
6. Lưu evidence register: URL/tài liệu, ngày hiệu lực, vai trò, bước tái hiện, kết quả, ảnh nếu được phép, hạn chế và quyết định áp dụng.
