# Kế hoạch hoàn tiền và trả hàng

Ngày: **20/11/2026**. Trạng thái: **bản kế hoạch để chủ sản phẩm duyệt, chưa có mã**. Mục đích: xác định các tình huống phải hoàn tiền, cách các sàn lớn xử lý, và lộ trình áp dụng cho VShop.

## 1. Nguồn tham khảo và mức độ kiểm chứng

| Nguồn                                                                                                                                                                                                       | Kết quả                                                                                                                                                                                                                           |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Shopee Việt Nam** (Trung tâm trợ giúp, các bài "Những quy định chung về Trả hàng/Hoàn tiền", "Thời gian nhận tiền hoàn và cách kiểm tra tiền hoàn", "Hướng dẫn Người mua trả lời đề xuất Hoàn Tiền Ngay") | **Đã đọc trực tiếp** ngày 20/11/2026. Mục 2 trích từ đây.                                                                                                                                                                         |
| **Lazada, Tiki**                                                                                                                                                                                            | **Chưa kiểm chứng.** Trang trợ giúp chỉ hiện khung rỗng với công cụ đọc web; chỉ thấy tiêu đề "đổi ý miễn phí trong 30 ngày" ở trang chủ Tiki, chưa đọc được nội dung chính sách.                                                 |
| **Luật Bảo vệ quyền lợi người tiêu dùng 2023, Nghị định 52/2013 (sửa bởi 85/2021)**                                                                                                                         | **Chưa đọc được** (các trang văn bản pháp luật chặn hoặc không tải). Không viết con số pháp lý nào ở đây. **Cần người có chuyên môn pháp lý đối chiếu trước khi chạy thật** (thời hạn, quyền đơn phương trả hàng, hạn hoàn tiền). |
| **payOS** (tài liệu API)                                                                                                                                                                                    | Không có API hoàn tiền cho khoản đã nhận; có API chi tiền ra tài khoản ngân hàng (`/v1/payouts`) với chữ ký riêng, giới hạn và điều kiện kích hoạt chưa công bố.                                                                  |
| **VNPay sandbox**                                                                                                                                                                                           | Có API truy vấn và hoàn tiền (toàn phần `02`, một phần `03`); chữ ký của hai API này chưa được tài liệu mô tả đủ.                                                                                                                 |

## 2. Shopee làm gì (đã kiểm chứng)

- **Không hỗ trợ đổi hàng**, chỉ trả hàng/hoàn tiền. Hàng có vấn đề thì người mua từ chối khi đồng kiểm (nếu có) hoặc gửi yêu cầu sau khi nhận.
- **Hạn gửi yêu cầu:** 15 ngày kể từ khi đơn "Giao hàng thành công"; đơn người bán tự vận chuyển: 15 ngày kể từ khi bấm "Đã nhận được hàng" hoặc 20 ngày kể từ khi "Lấy hàng thành công" nếu không bấm; thực phẩm tươi sống/đông lạnh: **24 giờ** (trừ "chưa nhận được hàng").
- **Phản hồi yêu cầu:** trong 3–5 ngày làm việc. Người bán có thể **đề xuất hoàn tiền ngay** (một phần hoặc toàn bộ, không cần trả hàng); người mua đồng ý để nhận tiền ngay, hoặc chọn "tôi muốn trả hàng" để nhận toàn bộ số tiền theo yêu cầu gốc. Người bán khiếu nại thì **sàn xem xét và quyết định trong 3–5 ngày làm việc**.
- **Lý do được chấp nhận:** chưa nhận được hàng; thiếu hàng; gửi sai hàng; bể vỡ (vụn, trầy xước, rò rỉ, thùng hỏng, khác); hàng lỗi không hoạt động; khác mô tả; đã qua sử dụng; hàng giả/nhái; **đổi ý** (chỉ khi còn nguyên tem, nhãn, bao bì, và loại trừ nhóm sản phẩm hạn chế trả hàng).
- **Tiền hoàn về đâu và bao lâu** (sau khi sàn chấp nhận): COD, QR hoặc chuyển khoản ngân hàng thì vào ví ShopeePay (24 giờ) hoặc tài khoản ngân hàng liên kết mặc định (2 ngày làm việc); thanh toán qua ứng dụng ngân hàng về tài khoản ban đầu (7 ngày làm việc); thẻ nội địa 2–5 ngày làm việc; thẻ tín dụng/ghi nợ 7–14 ngày làm việc. Có màn hình theo dõi số tiền, kênh nhận và trạng thái.
- **Voucher và xu:** voucher của sàn hoàn theo tỷ lệ phần hàng được hoàn (toàn đơn thì hoàn cả mã); **voucher của shop và mã miễn phí vận chuyển không hoàn**; xu hoàn theo tỷ lệ giá trị hoàn.
- **Phí trả hàng** khi người mua tự sắp xếp trả hàng được hoàn theo chính sách hỗ trợ phí trả hàng.

**Ý rút ra:** (1) hạn theo mốc "đã giao" do hệ thống ghi nhận; (2) danh sách lý do cố định, mỗi lý do kèm bằng chứng và điều kiện; (3) hoàn tiền không cần trả hàng cho khoản nhỏ hoặc do người bán đề xuất; (4) có trọng tài của sàn khi bất đồng; (5) COD vẫn hoàn được vì có kênh thứ hai (ví hoặc tài khoản ngân hàng); (6) mọi khoản hoàn có trạng thái và hạn xử lý rõ ràng cho người mua.

## 3. Các tình huống hoàn tiền của VShop

"Tiền đã thu" nghĩa là đơn đã thanh toán online (payOS/VNPay) hoặc COD đã được shipper thu. Cột "Hôm nay" là hiện trạng mã.

| #   | Tình huống                                                                                           | Tiền đã thu?              | Việc phải làm                                                                           | Hôm nay                                             |
| --- | ---------------------------------------------------------------------------------------------------- | ------------------------- | --------------------------------------------------------------------------------------- | --------------------------------------------------- |
| 1   | Người mua hủy khi shop chưa xác nhận                                                                 | Online: có. COD: chưa     | Trả tồn; **online: hoàn 100% (cả phí ship)**; COD: không có tiền                        | Trả tồn đúng. **Online: chưa hoàn tiền, tiền kẹt.** |
| 2   | Shop từ chối đơn                                                                                     | Online: có                | Như trên                                                                                | Như trên, **chưa hoàn tiền**                        |
| 3   | Shop không xác nhận quá 24 giờ (hàng đợi quá hạn)                                                    | Online: có                | Quản trị viên hủy thay, trả tồn, hoàn 100%                                              | Mới có hàng đợi xem, chưa có hành động              |
| 4   | Thanh toán đến sau khi đơn hết hạn                                                                   | Có                        | Hoàn 100%                                                                               | Có `review_case`, **chưa có hoàn tiền**             |
| 5   | Thu trùng hai lần, hoặc thu thừa/thiếu                                                               | Có                        | Hoàn phần dư, hoặc hoàn rồi đặt lại                                                     | Chỉ ghi nhận một phần (dư vào tra soát), chưa hoàn  |
| 6   | Vận chuyển thất bại: GHTK hủy, mất hàng, giao thất bại rồi hoàn về shop                              | Online: có. COD: chưa thu | Online: hoàn; hàng hoàn về thì kiểm và nhập kho lại                                     | Webhook ghi sự kiện `ShipmentProblem`, chưa xử lý   |
| 7   | Yêu cầu trả hàng sau khi giao: chưa nhận được, thiếu, sai hàng, hư hỏng, lỗi, khác mô tả, giả, đổi ý | Có (COD: đã thu hộ)       | Mở hồ sơ trả hàng, bằng chứng, shop phản hồi, trọng tài, trả hàng, kiểm hàng, hoàn      | Chưa có                                             |
| 8   | Hoàn một phần (một số dòng/số lượng, hoặc shop đề xuất hoàn ngay một phần)                           | Có                        | Hoàn theo dòng, phân bổ phí ship và giảm giá theo thuật toán xác định (docs/20 mục 7.2) | Chưa có                                             |
| 9   | Tranh chấp ngân hàng/chargeback (thẻ)                                                                | Có                        | Giữ tiền chờ kết quả, đối chiếu                                                         | Chưa có; ngoài phạm vi pilot                        |

**Phát hiện quan trọng:** các tình huống **1, 2, 4** đang xảy ra được **ngay bây giờ** với đơn thanh toán online (hủy hoặc từ chối đơn đã trả tiền chỉ trả tồn kho, không có bước hoàn tiền). Vì vậy R0 dưới đây nên làm trước mọi thứ khác.

## 4. Cách hoàn tiền cho từng phương thức

- **Online qua payOS:** payOS không có API hoàn trên khoản đã nhận. Hoàn tiền thực chất là **chuyển khoản ra tài khoản ngân hàng của người mua** (API chi tiền hoặc chuyển tay). Cần: số tài khoản và ngân hàng người nhận. Webhook thanh toán có `counterAccountNumber`, `counterAccountBankId`, `counterAccountName` nhưng tài liệu ghi chỉ MB Bank, ACB, KienlongBank hỗ trợ; các ngân hàng khác phải **hỏi người mua** và mã hóa bằng `PiiCipher`.
- **Online qua VNPay:** có API hoàn toàn phần hoặc một phần về đúng phương thức đã trả; cần xác nhận chữ ký truy vấn/hoàn bằng một lần gọi sandbox.
- **COD:** nếu hàng chưa giao thì không có tiền để hoàn. Nếu đã giao và shipper đã thu, tiền nằm ở GHTK rồi chuyển về shop (đối soát), nên hoàn tiền cho người mua là **một khoản phải trả mới** từ sàn hoặc shop, cần tài khoản ngân hàng người mua và dòng tiền hoàn lại từ shop (S4: `cod_receivable`).
- **Không bao giờ hoàn quá số đã thu:** tổng hoàn của một đơn ≤ số đã phân bổ cho đơn đó trừ đi số đã hoàn.

## 5. Thiết kế đề xuất

### 5.1 Bảng và bất biến (bám DDL tham chiếu)

- `payment.refund` (đã có trong DDL tham chiếu) + `payment.refund_allocation`: mỗi khoản hoàn có **khóa idempotency**, số tiền, trạng thái `APPROVED → SUBMITTED → PENDING → SUCCEEDED | FAILED | UNKNOWN`, tham chiếu của nhà cung cấp/ngân hàng, và phân bổ về từng `allocation`.
- Bảng **yêu cầu hoàn tiền** (`refund_due` hoặc dùng `refund.APPROVED`) có lý do (`ORDER_CANCELED`, `SELLER_REJECTED`, `PAID_AFTER_EXPIRY`, `DUPLICATE_CHARGE`, `RETURN_APPROVED`, `SHIPMENT_FAILED`, `ADMIN_GOODWILL`) và nguồn (đơn, hồ sơ trả hàng, `review_case`).
- Bất biến kiểm tra bằng test: Σ hoàn của một `allocation` ≤ số tiền `allocation`; không số âm; chạy lại cùng khóa không hoàn hai lần; phân bổ hoàn xuống dòng dùng thuật toán phần dư lớn nhất đã có (không phụ thuộc thứ tự).
- Tài khoản nhận tiền hoàn (ngân hàng, số tài khoản, tên) lưu mã hóa, gắn với người mua; nhật ký và outbox không chứa số tài khoản.

### 5.2 Luồng tự động cho tình huống 1–5

Mọi nơi hiện đang `markCanceled`/`reject`/phát hiện thanh toán muộn sẽ, **trong cùng giao dịch**, ghi một yêu cầu hoàn tiền `APPROVED` nếu đơn đã thu tiền online. Quản trị viên (hoặc job sau này) xử lý hàng đợi.

### 5.3 Hồ sơ trả hàng sau giao hàng (tình huống 7–8)

Rút gọn từ `aftersales.return_case` trong DDL tham chiếu: `REQUESTED → (SELLER_OFFERED | REVIEWING) → RETURN_REQUIRED → IN_RETURN → INSPECTING → REFUND_APPROVED → CLOSED`, cộng `REJECTED`, `DISPUTED`, `CANCELED`.

- Mở được trong **hạn đổi trả** của chính sách đã duyệt (hiện 7 ngày, tính từ `delivered_at`; từ nay ưu tiên mốc do GHTK xác nhận).
- Lý do theo danh sách cố định của mục 2 và bằng chứng (ảnh) bắt buộc với nhóm lỗi hàng.
- Shop phản hồi trong hạn; có thể **đề xuất hoàn ngay** một phần/toàn phần không cần trả hàng. Shop im lặng quá hạn hoặc khiếu nại thì **quản trị viên làm trọng tài**.
- Trả hàng qua GHTK (vận đơn hướng `RETURN`), kiểm hàng khi về, nhập kho lại theo kết quả kiểm.

## 6. Các quyết định cần chủ sản phẩm chốt (D12–D22)

| Mã  | Câu hỏi                                                                     | Gợi ý                                                                                       |
| --- | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| D12 | Hạn trả hàng sau khi giao                                                   | Giữ 7 ngày (đã duyệt), **cần đối chiếu pháp lý**; Shopee dùng 15 ngày                       |
| D13 | Lý do nào miễn phí trả hàng, lý do nào người mua chịu (đổi ý)               | Lỗi do shop/vận chuyển: shop hoặc sàn chịu; đổi ý: người mua chịu phí ship, hàng còn nguyên |
| D14 | Ai chịu phí vận chuyển ban đầu khi hoàn toàn bộ                             | Hoàn 100% khi shop có lỗi; đổi ý thì không hoàn phí ship                                    |
| D15 | Hạn shop phản hồi yêu cầu trả hàng                                          | 48 giờ; quá hạn thì chuyển trọng tài, không tự duyệt                                        |
| D16 | Hoàn không cần trả hàng với khoản nhỏ                                       | Ví dụ dưới một ngưỡng tiền (cần số), hoặc khi shop đề xuất và người mua đồng ý              |
| D17 | Thời gian cam kết hoàn tiền cho người mua                                   | Ví dụ trong vòng 3 ngày làm việc kể từ khi duyệt (cần số; phụ thuộc kênh chi)               |
| D18 | Ai trả tiền hoàn: sàn ứng trước rồi trừ vào shop, hay trừ từ doanh thu shop | Sàn ứng trước từ tài khoản payOS rồi ghi nợ shop                                            |
| D19 | Nhóm sản phẩm hạn chế trả hàng (thực phẩm tươi, đồ lót...)                  | Cần danh mục và cờ trên sản phẩm                                                            |
| D20 | Giới hạn chống lạm dụng (số lần trả hàng, ngưỡng tiền mỗi người mua)        | Đặt giới hạn, vượt thì chuyển người duyệt                                                   |
| D21 | Kênh chi tiền: chi tay trước hay bật API chi tiền payOS                     | Pha 1 chi tay có ghi chứng từ, pha 2 tự động khi payOS xác nhận kích hoạt và hạn mức        |
| D22 | Ai là trọng tài trong pilot                                                 | Quản trị viên có OTP, mọi quyết định ghi audit                                              |

## 7. Lộ trình

| Lát cắt                        | Nội dung                                                                                                                                                                                                                                                                                         | Phụ thuộc                 | Ước tính |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------- | -------- |
| **R0** Sổ hoàn tiền và chi tay | Bảng hoàn tiền và phân bổ (chuyển từ DDL tham chiếu), tự tạo yêu cầu hoàn tiền cho tình huống 1–5 trong cùng giao dịch, thu tài khoản nhận tiền (mã hóa), hàng đợi quản trị viên "đã chuyển khoản" kèm mã chứng từ, màn hình theo dõi cho người mua, audit và outbox, test bất biến và đồng thời | D21 pha 1                 | 5 ngày   |
| **R1** Hồ sơ trả hàng          | Mở hồ sơ, lý do, bằng chứng, phản hồi shop, đề xuất hoàn ngay, trọng tài, hạn xử lý                                                                                                                                                                                                              | D12–D16, D22              | 8 ngày   |
| **R2** Trả hàng vật lý         | Vận đơn hướng `RETURN` qua GHTK, kiểm hàng, nhập kho lại, xử lý `ShipmentProblem` (tình huống 6)                                                                                                                                                                                                 | R1, GHTK                  | 6 ngày   |
| **R3** Chi tiền tự động        | Gọi chi tiền của payOS (hoặc API hoàn VNPay), truy vấn kết quả, đối soát, trạng thái `UNKNOWN`                                                                                                                                                                                                   | D21 pha 2, payOS xác nhận | 6 ngày   |
| **R4** COD và sổ cái           | `cod_receivable`, hoàn tiền cho đơn COD đã thu hộ, ghi nợ shop, hoàn một phần theo dòng và giảm giá                                                                                                                                                                                              | D18, R1                   | 8 ngày   |

**Đề xuất:** làm **R0 ngay** vì nó vá lỗ hổng đang có (tiền kẹt khi hủy hoặc từ chối đơn đã trả online) mà chưa cần quyết định chính sách nào ngoài cách chi tiền.

## 8. Kiểm thử cần có

Hoàn không vượt số đã thu (kể cả nhiều lần song song); lặp lại cùng khóa không hoàn hai lần; hủy đơn online tạo đúng một yêu cầu hoàn; hủy đơn COD không tạo yêu cầu; hoàn một phần phân bổ đúng từng đồng và độc lập thứ tự dòng; người mua chỉ thấy khoản hoàn của mình; tài khoản nhận tiền không xuất hiện trong log hay sự kiện; thanh toán muộn vào hàng đợi rồi hoàn; chuyển trạng thái chi tiền sai thứ tự không lùi.

## 9. Rủi ro và giới hạn

1. Điều kiện kích hoạt, hạn mức và phí của chi tiền payOS chưa công bố; phải hỏi payOS.
2. Chưa kiểm chứng chính sách Lazada, Tiki và quy định pháp luật; không dùng con số nào ở đây làm cam kết với khách khi chưa đối chiếu.
3. Hoàn tiền là chuyển tiền thật; mọi bước chi cần quyền quản trị có OTP, hai bước (duyệt và xác nhận đã chi) và nhật ký.
4. Một người vừa duyệt vừa chi không phải kiểm soát hai người; cần tách quyền khi có người thứ hai.

## 10. R0 đã làm (20/11/2026)

- **V1013:** `payment.refund` (tách khỏi DDL tham chiếu; `return_case_id` sẽ thêm cùng hồ sơ trả hàng ở R1) và `payment.refund_allocation`. Trạng thái: `AWAITING_ACCOUNT` → `APPROVED` → `SUCCEEDED` (cộng `FAILED`, `SUBMITTED`, `PENDING`, `UNKNOWN` dành cho chi tự động ở R3). CHECK ở DB: chỉ `APPROVED`/`SUCCEEDED` mới có đủ tài khoản nhận, `SUCCEEDED` bắt buộc có mã giao dịch, thời điểm và người chi.
- **Tự tạo yêu cầu hoàn tiền trong cùng giao dịch** với sự kiện gây ra: người mua hủy đơn (`ORDER_CANCELED`), shop từ chối (`SELLER_REJECTED`) và thanh toán đến sau khi đơn đã hết hạn (`PAID_AFTER_EXPIRY`; tiền được phân bổ về các đơn đã hủy rồi hoàn). Đơn COD không có tiền thu nên không tạo gì.
- **Chống hoàn quá hoặc hoàn trùng:** số hoàn tối đa của mỗi đơn là số đã phân bổ trừ đi số đã hoàn (hàng phân bổ bị khóa trong giao dịch); khóa idempotency gồm đơn, lý do và khoản phân bổ, nên báo lại cùng nguyên nhân hay yêu cầu hoàn tiền "thiện chí" cho tiền đã hoàn đều không tạo thêm.
- **Người mua:** `PUT /api/v1/orders/{id}/refund-account` nhập ngân hàng, số tài khoản (6–20 chữ số), tên chủ tài khoản (chuẩn hóa không dấu, chữ hoa). Lưu mã hóa; người mua chỉ thấy 4 số cuối. Đơn chi tiết hiện khoản hoàn và trạng thái.
- **Quản trị viên (OTP):** `GET /api/v1/admin/refunds`, `POST .../{id}/paid` ghi mã giao dịch ngân hàng, **chỉ một lần** (cập nhật có điều kiện), `POST .../{id}/bad-account` trả khoản hoàn về người mua để nhập lại. Mọi thao tác ghi nhật ký audit và sự kiện outbox (`RefundRequested`, `RefundSucceeded`). Trang `/admin/refunds`.
- **Kiểm chứng:** 3 test tích hợp mới (hủy đơn đã trả tiền tạo đúng một khoản hoàn, nhập tài khoản và chi một lần, tài khoản sai quay về người mua, shop từ chối đơn đã trả, đơn COD không tạo hoàn tiền, cô lập người mua, quyền quản trị) và test thanh toán muộn được cập nhật (tạo phân bổ và hoàn tiền). Đã chạy thật qua giao diện: tạo trang thanh toán payOS thật (không thanh toán, đã hủy link), thông báo thanh toán ký cục bộ, người mua hủy đơn, nhập tài khoản, quản trị viên (TOTP thật) ghi nhận đã chuyển khoản với mã giả `E2E-SIMULATED-NO-MONEY`. Không có tiền thật nào di chuyển.

**Giới hạn còn lại của R0:** chi tiền vẫn do người làm tay trong ứng dụng ngân hàng (D21 pha 1); một người vừa xem tài khoản vừa ghi nhận chi (chưa có tách quyền hai người); chưa thông báo cho người mua khi khoản hoàn được chuyển (chưa có kênh thông báo); chưa hoàn khi quản trị viên hủy đơn quá hạn thay shop (chưa có thao tác đó); thu trùng hai lần cùng một đơn chưa được ghi nhận thành khoản hoàn; chưa có thử nghiệm đồng thời cho hai quản trị viên cùng ghi nhận một khoản (được bảo vệ bằng cập nhật có điều kiện và kiểm chứng tuần tự).

## 11. R1 đã làm (20/11/2026): hồ sơ trả hàng

Chủ sản phẩm chưa trả lời D12–D16 và D22, nên R1 dùng **các giá trị gợi ý của mục 6**, trong đó con số duy nhất là chính sách mới `RETURN_HANDLING {sellerResponseHours}` (đã duyệt **48 giờ** trong DB dev qua luồng quản trị viên). Hạn đổi trả vẫn là chính sách `RETURN` đang áp dụng (7 ngày). Các giá trị này nên được rà lại trước khi chạy thật.

- **V1014:** `aftersales.return_case` và `return_case_event`; `payment.refund` có thêm `source` (`ONLINE`/`COD`) và `return_case_id`, `charge_id` cho phép rỗng để **đơn COD đã giao cũng hoàn tiền được**; lý do `RETURN_APPROVED`. CHECK ở DB: duyệt hoàn phải có số tiền và hình thức; đổi ý bắt buộc trả hàng; chỉ một hồ sơ còn hiệu lực cho mỗi đơn.
- **Điều kiện mở hồ sơ:** đơn `DELIVERED`, còn trong hạn đổi trả (`return_eligible_until`), có chính sách xử lý đã duyệt, mô tả 10–1000 ký tự, lý do trong danh sách cố định (chưa nhận, thiếu, sai hàng, hư hỏng, lỗi, khác mô tả, giả, đổi ý). **Số tiền yêu cầu:** lỗi của shop hoặc vận chuyển hoàn cả phí ship; đổi ý chỉ hoàn tiền hàng.
- **Luồng:**
  - `REQUESTED` → shop **đề xuất hoàn tiền** (toàn phần hoặc một phần, không cần trả hàng) → người mua **đồng ý** (hoàn tiền) hoặc **không đồng ý** (chuyển quản trị viên); hoặc shop **đồng ý nhận lại hàng** → `AWAITING_RETURN` → người mua nhập mã vận đơn → `IN_RETURN` → shop **xác nhận đã nhận** (hoàn đủ, tùy chọn nhập lại kho) hoặc báo **không đạt** (chuyển quản trị viên); hoặc shop **từ chối** → người mua có thể nhờ quản trị viên xem xét lại.
  - Quản trị viên (OTP) quyết định các hồ sơ `REVIEWING` và các hồ sơ shop **không phản hồi quá hạn**: hoàn tiền không cần trả hàng (số tiền có thể thấp hơn yêu cầu), yêu cầu trả hàng, hoặc từ chối; ghi audit. **Không tự duyệt khi shop im lặng.**
  - Người mua hủy được khi chưa có quyết định cuối.
- **Hoàn tiền:** duyệt xong tạo khoản hoàn trong sổ R0 (cùng giao dịch, một khoản cho mỗi hồ sơ; không vượt số còn có thể hoàn, tính theo phân bổ với đơn online, theo tổng đơn trừ các khoản đã hoàn với đơn COD). Người mua nhập tài khoản và quản trị viên chi tay như R0.
- **API và web:** người mua (`/api/v1/orders/{id}/return-case`, `/api/v1/return-cases/{id}/...`), shop (`/api/v1/seller/shops/{id}/return-cases/...`), quản trị viên (`/api/v1/admin/return-cases`); trang đơn hàng của người mua, `/seller/returns`, `/admin/returns`.
- **Kiểm chứng:** 4 test tích hợp (điều kiện mở hồ sơ, đề xuất một phần và trần hoàn tiền online, trả hàng COD có nhập kho đúng một lần, quản trị viên xử lý hồ sơ bị từ chối, bị từ chối đề xuất và quá hạn) và Playwright `return-case.spec.ts` với shop, người mua và quản trị viên (TOTP) thật.

**Giới hạn của R1:** chưa có ảnh bằng chứng (chưa có kho lưu tệp), chỉ mô tả bằng chữ; hoàn toàn bộ đơn, chưa hoàn theo từng dòng hay số lượng (R4); trả hàng do người mua tự gửi và nhập mã vận đơn, chưa có vận đơn trả qua GHTK, kiểm hàng chỉ là xác nhận của shop (R2); chưa có thông báo cho các bên khi hồ sơ đổi trạng thái; chưa hoàn voucher (chưa có voucher); chưa phân biệt nhóm hàng hạn chế trả hàng; chưa có thử nghiệm đồng thời cho hai người cùng xử lý một hồ sơ (bảo vệ bằng cập nhật có điều kiện và kiểm chứng tuần tự); đơn COD đã giao nhưng khách chưa trả tiền (giao thất bại rồi hoàn về) cần sổ thu hộ của R4 để đối chiếu.
