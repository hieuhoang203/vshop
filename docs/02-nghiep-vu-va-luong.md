# 02. Nghiệp vụ, luồng và quy tắc VShop

Tất cả quy tắc dưới đây là **đề xuất VShop** cần chủ sản phẩm duyệt; không mô tả triển khai nội bộ Shopee. Phạm vi đã xác nhận: marketplace Việt Nam. Giả định lập kế hoạch: hàng hóa vật lý thông thường, VND, seller tự giữ và đóng gói hàng; một kho giao hàng/shop ở pilot; một PSP, một carrier; không hàng số, xuyên biên giới, thực phẩm tươi, thuốc hoặc hàng cần kiểm soát đặc biệt trong pilot.

## 1. Phạm vi và vai trò

| Vai trò | Khả năng | Giới hạn bắt buộc |
|---|---|---|
| Khách | Xem/tìm hàng, xem shop và chính sách | Phải đăng nhập trước đặt hàng ở pilot |
| Buyer | Địa chỉ, giỏ, đơn của mình, thanh toán, case, đánh giá | Không xem đơn/người nhận của người khác |
| Seller owner | Hồ sơ shop, hàng, tồn, đơn shop, case, doanh thu, tài khoản nhận tiền | Không xem dữ liệu shop khác hoặc toàn payment nhiều shop |
| Seller staff (sau pilot) | Catalog, fulfillment, CSKH theo scope shop | Không có quyền ngân hàng/chi trả mặc định |
| Support | Tra đơn được phân công, ticket, trao đổi, đề xuất xử lý | Không sửa sổ cái hay tự chi tiền |
| Moderator | Duyệt shop/hàng, báo cáo vi phạm, hạn chế nội dung | Khóa shop không xóa nghĩa vụ giao/hoàn tiền đang có |
| Finance | Đối soát, duyệt hoàn/chi theo hạn mức, điều chỉnh có chứng từ | Không chỉnh trực tiếp số dư; mọi bút toán có lý do |
| Platform admin | Tài khoản nội bộ, cấu hình, chính sách, vận hành | MFA, audit; quyền tài chính cấp riêng |
| Integration/worker | Nhận sự kiện và chạy job theo quyền máy | Không dùng tài khoản admin; API/webhook có xác thực |

Một người có thể mang nhiều vai trò. Quyền hiệu lực = danh tính hợp lệ + action + shop/resource scope + trạng thái tài khoản/chính sách. Không tin `shopId` client gửi. Pilot chỉ có một người vận hành thì không được tuyên bố có kiểm soát hai người thực chất; hạn mức thấp, duyệt thủ công và báo cáo audit là biện pháp tạm thời. Trước mở rộng cần người kiểm tra tiền độc lập.

## 2. Danh mục phân hệ và màn hình

| Bề mặt | Màn hình ra mắt |
|---|---|
| Buyer web | Trang chủ gọn, danh mục, tìm kiếm/lọc, chi tiết SKU, shop, giỏ theo shop, checkout, thanh toán, đơn/chi tiết/tracking, case/hoàn tiền, đánh giá, tài khoản/địa chỉ, thông báo, hỗ trợ/chính sách |
| Buyer app | Đăng nhập, danh sách/tìm hàng, chi tiết SKU, giỏ/checkout, thanh toán qua SDK/hosted flow, đơn/tracking, gửi case, địa chỉ, thông báo; không chỉ đóng web trong WebView |
| Seller web | Onboarding/trạng thái duyệt, hồ sơ/ngân hàng, catalog/SKU, tồn và lịch sử, đơn/nhãn bàn giao, hàng giao lỗi/hàng hoàn, case, bảng kê/số dư/yêu cầu chi, ticket |
| Admin web | Việc cần xử lý, người dùng/shop/duyệt, danh mục/thuộc tính, sản phẩm/báo cáo, tra đơn/payment/shipment, case/kháng nghị, refund, COD/PSP đối soát, chi trả, phí/chính sách/voucher, audit và tích hợp lỗi |

Seller/admin dùng responsive web trong năm đầu. Chưa có app seller riêng. Về nghiệp vụ, trả hàng có thể được xử lý thủ công có audit; người dùng vẫn phải gửi, theo dõi và nhận kết quả trong hệ thống.

## 3. Mô hình giao dịch

```text
User -- ShopMembership --> Shop -- Product --> SKU
User --> Cart --> CheckoutQuote --> CheckoutGroup
CheckoutGroup --> SellerOrder [1..n shop]
SellerOrder --> OrderLine [snapshot SKU, giá, thuế/phí/chính sách]
OrderLine --> StockReservation --> InventoryMovement
CheckoutGroup --> PaymentIntent --> PaymentAttempt [0..n]
Payment/charge --> PaymentAllocation --> SellerOrder/OrderLine
SellerOrder --> Shipment --> ShipmentEvent
OrderLine --> ReturnCase --> ReturnLine --> ReturnShipment
ReturnCase/Cancel --> Refund --> RefundAllocation --> Payment
Order/Refund/Fee --> JournalEntry --> JournalLine
Shop --> SettlementBatch/Payout --> BankTransfer
```

Pilot một seller order tương ứng một kiện; schema cho phép nhiều kiện sau này nhưng UI/API phải từ chối chia kiện khi chưa hỗ trợ. COD được xác định số tiền thu cho từng kiện; trả trước dùng một payment tổng chỉ khi PSP chấp thuận mô hình nhiều shop. Nếu PSP không hỗ trợ, phải đổi UX sang thanh toán từng đơn shop trước beta, không giả lập khả năng gom thanh toán.

`CheckoutGroup` chỉ là nhóm mua hàng. Không dùng nó thay trạng thái riêng của từng shop. PaymentIntent không phải một lần thu tiền thành công: cần PaymentAttempt/charge để xử lý retry và phát hiện thu trùng. Dữ liệu tài chính chỉ được bổ sung/đảo bút toán, không xóa lịch sử.

## 4. Sổ quy tắc nghiệp vụ

| ID | Quy tắc/điều kiện kiểm soát |
|---|---|
| BR01 | Chỉ shop đã duyệt, hàng đã được phép bán, SKU còn bán và địa chỉ phục vụ được mới vào checkout. Khóa shop không chặn buyer tra đơn/case cũ. |
| BR02 | SKU là đơn vị giá/tồn; kết hợp thuộc tính không trùng trong sản phẩm; lưu tiền bằng số nguyên VND, không float. Phần trăm tính bằng decimal và có quy tắc làm tròn. |
| BR03 | Thêm giỏ không giữ hàng. `available = on_hand - reserved - blocked`; cập nhật tồn/reservation nguyên tử, không âm; không tăng tồn chỉ do nhận yêu cầu trả. |
| BR04 | Server tính quote gồm giá, khuyến mãi, ship, khoản người mua trả; có version và hạn. Đổi SKU, địa chỉ, số lượng, phương thức phải tính lại; giá tăng cần buyer xác nhận. |
| BR05 | Một lần submit với cùng idempotency key và payload chỉ tạo một checkout; khác payload cùng key trả conflict. Checkout nhiều shop phải có cơ chế rollback/compensation khi một phần thất bại. |
| BR06 | Lưu snapshot tên/SKU/thuộc tính/ảnh tham chiếu/giá/chính sách/phí/địa chỉ/ship tại đặt hàng; seller sửa hàng sau đó không làm thay đổi hợp đồng đơn cũ. |
| BR07 | Voucher có thời gian, scope, điều kiện, lượt/ngân sách, người tài trợ và version. Giữ lượt lúc submit, tiêu thụ/nhả theo kết quả; không vượt ngân sách dù request đồng thời. |
| BR08 | Phân bổ giảm giá xuống dòng hàng bằng cách xác định trước, làm tròn và bù phần dư ổn định. Tổng phân bổ đúng tổng ưu đãi; dòng đã hủy/hoàn không nhận ưu đãi lần hai. |
| BR09 | Chỉ webhook đã kiểm chứng hoặc API truy vấn PSP đáng tin xác nhận đã thu. Redirect client không chứng minh thành công. Kiểm amount/currency/reference/merchant và chống replay. |
| BR10 | Timeout payment là trạng thái chưa chắc chắn. Truy vấn PSP trước retry/cancel cuối; callback muộn không tự phục hồi đơn đã hủy. Nếu đã thu thật, hoàn an toàn hoặc đưa hàng đợi tra soát. |
| BR11 | Tách trạng thái đơn, tiền, giao vận, case, settlement. `DELIVERED` không đồng nghĩa PSP/carrier đã chuyển tiền; `COMPLETED` không tự đóng quyền trả hàng còn hạn. |
| BR12 | Hủy theo từng seller order. Trước xử lý có thể hủy ngay; đã chuẩn bị cần seller xác nhận; sau bàn giao chỉ recall nếu carrier hỗ trợ, còn lại theo giao lỗi/return. Race bàn giao-hủy phải phân xử theo sự kiện có thẩm quyền. |
| BR13 | Mọi hết hạn chờ thanh toán/xử lý/bàn giao/case dùng mốc server và policy version; job chạy lại không giải phóng tồn/hoàn tiền hai lần. Gợi ý ban đầu payment hold 15 phút, cần PSP xác nhận trước bật. |
| BR14 | Shipment event có mã duy nhất, thời gian sự kiện/nhận, trạng thái raw và normalized. Không làm lùi trạng thái từ webhook đến muộn; sửa sai bằng sự kiện correction có lý do. |
| BR15 | Giao lỗi khác buyer trả hàng. Chỉ nhập hàng hoàn vào tồn bán sau khi nhận và kiểm nhận; hàng hỏng đi blocked/quarantine; mất hàng mở claim carrier. |
| BR16 | Case theo dòng và số lượng còn được trả; cùng quantity không có hai case hoạt động chồng lấn. Chính sách theo ngành/lý do, hạn và chứng cứ. Dùng snapshot policy tại đơn, điều chỉnh pháp lý có audit. |
| BR17 | Hỗ trợ refund-only, return-and-refund, hoàn một phần; không bắt buộc mọi yêu cầu đều phải gửi hàng. Case quyết định chấp thuận không đồng nghĩa PSP đã hoàn tiền. |
| BR18 | Tổng completed + pending refund bị giữ quyền trên một charge không vượt số tiền đã thu còn được hoàn. Serialize kiểm tra với tạo refund; cập nhật chống trùng theo provider reference. |
| BR19 | Hoàn COD dùng phương thức nhận tiền được xác minh; không tồn tại thao tác hoàn về COD. Phải dự phòng nguồn chi hoàn khi carrier chưa chuyển COD cho sàn. |
| BR20 | Mỗi lần hoàn có bảng tính line money, ưu đãi, shipping, người chịu phí, phí/tax đảo tương ứng. Không tính lại theo giá hiện tại; không tự trừ lại ưu đãi người mua đã hưởng trừ chính sách đã công bố. |
| BR21 | Voucher khi hủy/hoàn khôi phục theo chính sách riêng: toàn phần/một phần, hiệu lực, gian lận; không tự cấp voucher mới mỗi lần callback. |
| BR22 | Seller payable, pending, held, available, payout-in-flight tách biệt. Chỉ chi phần đủ điều kiện, đối soát được, đã trừ hold và chưa chi/đang chi. Không chi theo GMV. |
| BR23 | Sổ cái kép: mỗi journal cùng currency có tổng nợ = tổng có; idempotent theo business event; reversal thay xóa. Báo cáo số dư có thể dựng lại từ journal. |
| BR24 | Phí/thuế/trợ giá có bên chịu và ngày hiệu lực; snapshot lúc phát sinh; báo cáo GMV, net sales, doanh thu phí, payable và tiền mặt khác nhau. Thuế dùng rule đã được chuyên môn xác nhận. |
| BR25 | Refund sau payout tạo nghĩa vụ thu hồi/negative payable/hold khoản tương lai có căn cứ; không tự rút ngân hàng seller hoặc tự sửa payout đã thành công. |
| BR26 | Đổi ngân hàng cần re-auth/MFA và kiểm tra chủ tài khoản; thời gian hold cấu hình; lần chi đang pending giữ snapshot đích; đổi đích không chuyển hướng lệnh cũ. |
| BR27 | Quyền theo shop/resource kiểm tra ở backend; API export, file, lịch sử và tìm kiếm admin cũng áp dụng. Audit không chứa token/giấy tờ đầy đủ. |
| BR28 | Chỉ buyer sở hữu dòng đã giao mới đánh giá; một review/dòng đủ điều kiện; version khi sửa; seller không được tự xóa; ẩn vi phạm có lý do và kháng nghị. |
| BR29 | Admin không sửa trực tiếp status/số dư. Dùng command có actor, reason, evidence và audit. Thao tác tài chính nhạy cảm có hạn mức, tách quyền và re-auth. |
| BR30 | Outbox cùng transaction với thay đổi nghiệp vụ; consumer có inbox/dedupe, retry và dead-letter. Hệ thống chấp nhận giao sự kiện ít nhất một lần, bảo đảm tác động nghiệp vụ một lần bằng idempotency. |
| BR31 | Lưu bằng chứng/cá nhân theo mục đích, quyền và lịch giữ; xóa/ẩn danh có ngoại lệ lưu giữ nghĩa vụ kế toán/tranh chấp; không xóa dây chuyền đơn tiền đang xử lý. |
| BR32 | Pilot commit reservation khi nền tảng xác nhận payment hợp lệ hoặc nhận đơn COD hợp lệ, trước bước seller xác nhận: cùng lúc giảm on_hand và reserved theo quantity; seller xác nhận sau đó không trừ thêm. Cancel trước giao tạo movement hoàn lại một lần. Hàng đã xuất chỉ nhập sau kiểm nhận. |

## 5. Các luồng trọng tâm

### F01 — Mở và duyệt shop

1. Buyer đăng nhập, gửi hồ sơ loại hình seller, thông tin liên hệ, định danh/thuế theo yêu cầu, địa chỉ và tài khoản nhận tiền.
2. Shop `DRAFT → SUBMITTED → UNDER_REVIEW`; admin có thể `NEEDS_CHANGES`, `APPROVED` hoặc `REJECTED`, có lý do và lịch sử.
3. Chỉ approved mới publish và nhận đơn. Thay thông tin nhạy cảm tạo yêu cầu xác minh lại và hold phù hợp.
4. Suspension ngừng phát sinh đơn mới nhưng vẫn có đường thực hiện đơn/case/hoàn tiền đang tồn; emergency access phải được audit.

### F02 — Đăng bán và quản lý tồn

1. Seller chọn ngành hàng, khai thuộc tính bắt buộc, mô tả, ảnh, cân nặng/kích thước, biến thể và giá/tồn mỗi SKU.
2. Kiểm tra dữ liệu, quét file, kiểm moderation. `DRAFT → PENDING_REVIEW → ACTIVE`; từ chối trả lý do, không xóa chứng cứ.
3. Tăng/giảm tồn tạo inventory movement với reason; không cho điều chỉnh thấp hơn phần đã reserved/allocated.
4. Thay ngành hàng/nội dung rủi ro đưa lại hàng đợi duyệt; giá mới áp dụng quote mới, không tác động snapshot đơn cũ.

### F03 — Giỏ nhiều shop → checkout → trả trước/COD

1. Cart chỉ lưu ý định mua; server nạp lại SKU, shop, địa chỉ, carrier, điều kiện COD và voucher khi lấy quote.
2. Quote hiển thị từng shop, phí ship mỗi kiện, giảm giá và tổng trả; chứa version/hạn, chưa bảo đảm còn tồn.
3. Submit kiểm lại quote và idempotency key. Trong transaction pilot: tạo group + seller orders + lines; giữ tồn theo thứ tự khóa SKU ổn định; giữ voucher; tạo payment intent và outbox. Một shop lỗi thì rollback toàn submit; buyer có thể bỏ shop lỗi và nhận quote mới.
4. Trả trước: tạo PSP session **ngoài transaction DB**, bằng merchant reference ổn định. Nếu timeout truy vấn PSP; không tạo charge mới mù quáng. Order chờ payment, stock hold có deadline và cơ chế xử lý payment pending.
5. PSP xác thực thành công: ghi charge/allocation, journal, chuyển order sang chờ seller và commit tồn một lần; phát notification. Cùng event đến lại chỉ trả kết quả cũ.
6. COD: không đánh dấu paid; xác nhận điều kiện thu hộ từng kiện, chuyển chờ seller và commit tồn. Tiền COD theo dõi receivable riêng.
7. Buyer thấy từng đơn shop; một shop hủy không làm các shop khác bị hủy. Tiền hoàn và voucher được phân bổ theo dòng/seller order.

### F04 — Xử lý và giao hàng

1. Seller nhận order đủ điều kiện, xác nhận hoặc từ chối theo lý do; job quá hạn xử lý theo policy.
2. Packing → tạo shipment/nhãn → chờ lấy. Tạo vận đơn idempotent; timeout phải tra mã ngoài trước tạo lại.
3. Carrier pickup chuyển shipment thực xuất; từ thời điểm này không hoàn tồn bằng đường hủy trước giao.
4. Tracking cập nhật từng kiện. Giao thành công mở cửa sổ hậu mãi; giao lỗi có retry/return-to-sender/lost theo carrier.
5. COD delivered chỉ cho biết đã giao/thu theo bằng chứng; journal receivable và đối soát carrier vẫn độc lập tiền về ngân hàng.

### F05 — Hủy và các cạnh tranh trạng thái

1. Unpaid: ghi yêu cầu hủy; nếu chưa có charge/pending nghi vấn, hủy và nhả giữ. Nếu PSP chưa rõ, `CANCEL_PENDING_PAYMENT_CHECK` trước khi kết thúc.
2. Paid, chưa xử lý: cancel từng shop; tính refund allocation, nhả voucher/tồn theo trạng thái, tạo refund pending.
3. Packing: `CANCEL_REQUESTED`; seller hoặc chính sách chấp thuận/từ chối. Chỉ chấp thuận khi xác định hàng chưa bàn giao.
4. Carrier đã nhận: không báo hủy thành công chỉ vì buyer nhấn nút; recall theo API nếu có, hoặc mở nhánh giao lỗi/return.
5. Callback trả tiền thành công sau cancel: giữ đơn canceled, ghi charge thật và phát lệnh hoàn một lần hoặc manual review. Nếu hai attempts đều bị thu, ghi cả hai charge và hoàn phần dư.

### F06 — Trả hàng, tranh chấp và hoàn tiền

1. Buyer chọn dòng/số lượng, lý do và bằng chứng; kiểm quyền, thời hạn, quota quantity và các case trước.
2. Seller/support xem và phản hồi đúng hạn; admin có thể quyết định refund-only, return-and-refund hoặc từ chối có lý do; cho kháng nghị theo policy.
3. Nếu return: tạo hướng dẫn/nhãn return, theo dõi pickup và nhận; seller kiểm nhận đủ/thiếu/hỏng, có đường tranh chấp nếu không đồng ý.
4. Hết hạn buyer chưa gửi/seller chưa phản hồi phải dùng policy cấu hình và bằng chứng, không mặc định mọi nhánh đều auto-refund.
5. Chấp thuận tiền: tính allocation trên snapshot và khoản còn được hoàn; giữ quyền hoàn nguyên tử; tạo refund command ổn định.
6. PSP success hoặc giao dịch hoàn COD được xác minh mới `REFUNDED`. Lỗi/timeout: pending/manual review, retry an toàn và đối soát.
7. Journal đảo phí/thuế/trợ giá theo policy; giảm payable hoặc tạo debt nếu đã payout. Nhập tồn chỉ sau nhận hàng đạt kiểm nhận; refund-only không nhập kho.
8. Buyer xem được kết quả case, trạng thái tiền, phương thức, số tiền và mã tham chiếu; có cửa hỗ trợ nếu tiền chưa về.

### F07 — Đối soát và chi trả seller

1. Hằng ngày nhập file/API PSP, carrier COD và bank; so khớp số tiền, mã, ngày, phí; lưu source file/checksum và kết quả import idempotent.
2. Phân loại unmatched, thiếu tiền, sai phí, duplicate, chargeback; hàng đợi finance xử lý có bằng chứng.
3. Settlement tính seller payable theo order: tiền bán hợp lệ + trợ giá được hưởng − refund − phí − nghĩa vụ thuế xác nhận − adjustments; khoản shipping pass-through theo hợp đồng, không coi mặc định là doanh thu seller.
4. Pilot chọn lịch chi sau khi tiền đã được đối soát, hết thời gian hold/return theo policy và không có case mở. Đây là lựa chọn VShop bảo thủ, không phải khẳng định cách Shopee chi tiền.
5. Tạo payout giữ available → payout-in-flight. Người có quyền duyệt kiểm beneficiary snapshot, hạn mức, chênh lệch và nguồn tiền.
6. Bank/PSP success: settle payout một lần; timeout `UNKNOWN/PENDING_REVIEW`, truy vấn trước gửi lại. Failed xác minh mới trả khoản giữ về available.
7. Đã chi nhưng có refund/chargeback: mở recovery/debt và hold theo điều khoản đã công bố; sàn cần vốn dự phòng trả buyer.

### F08 — Quản trị vi phạm và hỗ trợ

Report/ticket → phân công → thu bằng chứng → quyết định/hạn chế có thời hạn → thông báo → kháng nghị → đóng có audit. Khóa tài khoản không xóa đơn, tiền, hồ sơ xử lý. Admin replay job chỉ qua command chống trùng, không sửa DB thủ công.

## 6. Máy trạng thái và chuyển trạng thái

| Đối tượng | Trạng thái chính |
|---|---|
| SellerOrder | PENDING_PAYMENT → AWAITING_SELLER → CONFIRMED → PACKING → READY_TO_SHIP → IN_FULFILLMENT → DELIVERED → COMPLETED; nhánh CANCEL_REQUESTED/CANCELED/REJECTED; hậu mãi riêng |
| PaymentAttempt | CREATED → PENDING → SUCCEEDED / FAILED / EXPIRED / UNKNOWN; attempt expired vẫn có thể có late-success cần xử lý |
| Payment aggregate | UNPAID / PARTIALLY_PAID / PAID / OVERPAID; refund totals riêng; COD có trạng thái khoản phải thu carrier |
| Reservation | HELD → COMMITTED hoặc RELEASED/EXPIRED; trạng thái cuối không được thực thi lần hai |
| Shipment | CREATED → READY → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED; nhánh DELIVERY_FAILED → RETRY / RETURNING → RETURNED; LOST có claim |
| ReturnCase | REQUESTED → REVIEWING → RETURN_REQUIRED / REFUND_APPROVED / REJECTED; return path: AWAITING_RETURN → IN_RETURN → INSPECTING → REFUND_APPROVED / DISPUTED; cuối CLOSED/CANCELED, APPEAL là nhánh có lịch sử |
| Refund | REQUESTED → APPROVED → SUBMITTED → PENDING → SUCCEEDED / FAILED / UNKNOWN; approved/submitted/pending giữ quota hoàn |
| Payout | REQUESTED → REVIEWING → APPROVED → SUBMITTED → SUCCEEDED / FAILED / UNKNOWN; request rejected/canceled có đường nhả hold |

State machine phải có bảng command/event, trạng thái nguồn/đích, actor, guard, side effect, timer và idempotency. Các chuỗi ở trên là khung, không được dùng làm một enum chung cho toàn hệ thống.

| Trigger | Điều kiện/actor | Kết quả cần nguyên tử |
|---|---|---|
| Submit checkout | Buyer sở hữu quote còn hạn, đủ tồn/quota | Order + reservation + voucher hold + outbox cùng commit |
| Payment succeeded | PSP đã xác minh, charge chưa ghi nhận | Charge/allocation + order/tồn phù hợp + journal + outbox |
| Payment timeout job | Không có thu thành công; pending PSP đã được giải quyết hoặc có cơ chế reconcile late-success | Hủy có kiểm soát + release hold một lần |
| Cancel accepted | Chưa pickup hoặc carrier recall xác minh | Cancel + refund request/hold + stock movement đúng giai đoạn |
| Delivery confirmed | Carrier event hợp lệ hoặc correction được duyệt | Shipment delivered + mốc hậu mãi; không tạo payout ngay |
| Refund approved | Quantity/amount còn hợp lệ, có người duyệt | Reserve refund amount + command + audit |
| Refund success | Provider/bank xác minh, chưa posted | Refund success + journal + payable/debt adjustment |
| Payout approved | Số dư khả dụng đủ, đích xác minh, không hold mới | Reserve available + payout snapshot + outbox |
| Payout success | Bank/PSP xác minh, chưa posted | Payout success + journal; không thể payout lại cùng request |

## 7. Minh họa tiền và phân bổ

Ví dụ **VShop đề xuất**: shop A có hàng 200.000đ, giảm shop 20.000đ; shop B 100.000đ. Tổng sau giảm shop = 280.000đ. Voucher sàn 30.000đ phân bổ theo tỷ trọng sau giảm shop: A 19.286đ, B 10.714đ. Ship A/B mỗi bên 20.000đ; hỗ trợ ship tổng 10.000đ, giả sử chia 5.000đ mỗi shop. Buyer trả A 175.714đ, B 104.286đ; tổng **280.000đ**.

Hủy toàn B trước xuất: hoàn buyer 104.286đ nếu policy hoàn cả ship phần này. Reverse phần voucher sàn 10.714đ và ship subsidy 5.000đ ở phía tài trợ; đó không phải khoản thêm vào tiền hoàn buyer. Các dòng còn lại của A giữ allocation gốc, không tính lại làm A đắt hơn sau mua. Hoàn quota voucher có quy tắc riêng.

Nếu voucher sàn do sàn tài trợ hoàn toàn và hợp đồng cho seller hưởng trợ giá, gross entitlement hàng của B trước phí/thuế là 100.000đ khi bán hợp lệ; khi hủy thì đảo entitlement đó. Cash collected, subsidy expense và seller payable là các khoản khác nhau; không lấy số buyer trả gán thẳng cho seller.

Từng phí/tax cần cấu hình `basis`, `rate/fixed`, `cap`, `payer`, `effective_from/to`, `refund_behavior`, `rounding`. Shipping phải tách phí buyer, carrier quoted/actual, subsidy và người chịu chênh lệch.

## 8. Những chính sách còn cần chốt

| Quyết định | Đề xuất để lập kế hoạch | Phụ thuộc |
|---|---|---|
| Ngành và loại seller | Một ngành hàng vật lý rủi ro thấp; seller mời | Khảo sát thị trường và hồ sơ pháp lý |
| Thanh toán nhiều shop | COD từng kiện + một PSP nếu hợp đồng cho phép charge tổng | PSP, cấu trúc thu hộ/chi trả |
| Hạn trả, tự hoàn thành, SLA | Có version, lịch ngày nghỉ và exception; không chốt số trong code | Chính sách công bố, carrier, chuyên môn pháp lý |
| Hủy/return một phần | Cancel theo seller order; return theo line/quantity | PSP partial refund và allocation |
| Phí và thuế | Một biểu phí đơn giản, tax theo loại seller khi được xác nhận | Kế toán/thuế, hợp đồng |
| Tiền seller | Chi thủ công được kiểm chứng trong pilot, đủ sổ cái/đối soát | Đối tác và nhân sự kiểm tra độc lập |
| Vốn hoàn/chargeback | Quỹ dự phòng được xác định trước live | Số đơn/giá trị và hợp đồng |
| Giao vận | Một carrier; một địa chỉ xuất/shop | Dịch vụ COD/return và sandbox |
| Sau bán | Ticket trong hệ thống, phản hồi thủ công có SLA | Năng lực vận hành của người sáng lập |

Chưa có câu trả lời cho các mục này thì vẫn làm được prototype/sandbox; không coi điều kiện live đã được đáp ứng.
