# 04. Lộ trình một năm và điều kiện ra mắt

## 1. Phạm vi cam kết để lập kế hoạch

**Đích:** closed beta có giao dịch thật ở quy mô nhỏ, sau đó quyết định mở dần; không đặt mục tiêu ngang quy mô/chức năng toàn Shopee trong một năm với một người.

Ra mắt: hàng vật lý ngành chọn trước, seller được mời, buyer web và app cơ bản, seller/admin responsive web, SKU/tồn, giỏ nhiều shop, COD + một PSP, một carrier, voucher đơn giản, đơn/cancel, return/refund có admin hỗ trợ, ticket, review, sổ cái và đối soát/chi thủ công có kiểm soát, audit/backup/quan sát.

Giới hạn triển khai có chủ đích: một kho xuất/shop; một kiện/seller order; không đổi hàng trực tiếp — xử lý return/refund rồi mua mới nếu phù hợp; không giao từng phần; không seller tự giao ở pilot; không hỗ trợ hàng đặt trước/backorder. Người dùng phải thấy rõ hạn chế trước mua.

Hoãn: app seller/admin riêng, chat realtime đầy đủ, livestream/video commerce, affiliate, auction ads, coins/game, ví nạp/rút riêng, BNPL tự vận hành, cross-border, multi-currency, fulfillment warehouse, multi-warehouse, recommendation ML, flash-sale lớn, Kubernetes/service mesh và tách nhiều service. Backlog có các task mở rộng để tránh mất hướng dài hạn.

## 2. Năng lực và ước lượng

Giả định **làm gần toàn thời gian**, khoảng 5 ngày/tuần. Sau nghỉ/phần việc khác, dành khoảng 46 tuần × 4,5 ngày = **207 ngày công khả dụng**. Khoảng 170–180 ngày là kế hoạch cơ sở, còn khoảng 27–37 ngày cho rework/đối tác/store review/sự cố. Đây là ước lượng ban đầu độ tin cậy thấp đến trung bình; một người vừa học vừa làm hoặc bán thời gian sẽ cần giảm scope hoặc kéo dài lịch.

`EstimateDays` trong backlog là effort của một người cho đầu ra được mô tả, không bao gồm mọi thời gian chờ bên ngoài. Vai trò trong cột Owner là “chiếc mũ” người sáng lập đang đảm nhiệm, không phải headcount. Cần rà lại sau discovery và sau một vertical slice thanh toán/giao vận. Không cộng đồng thời các task như có cả đội.

Backlog hiện có **130 task**: **118 P0 = 174,5 ngày công**, **6 P1 = 36 ngày**, **6 P2 = 32 ngày**. P1/P2 ở phase X không thuộc cơ sở một năm. So với 207 ngày khả dụng giả định, P0 để lại **32,5 ngày dự phòng**, khoảng 16% năng lực. Nếu thực tế phát sinh 30% rework thì riêng P0 thành khoảng 227 ngày và cần giảm scope hoặc lùi lịch.

| Phase | Task P0 | Ngày công cơ sở |
|---|---:|---:|
| A | 13 | 17,5 |
| B | 15 | 19 |
| C | 14 | 17,5 |
| D | 20 | 29 |
| E | 21 | 28 |
| F | 10 | 13 |
| G | 11 | 23,5 |
| H | 9 | 17 |
| I | 5 | 10 |
| **Tổng** | **118** | **174,5** |

Các task discovery như pháp lý/đối tác là effort chuẩn bị và xử lý của người sáng lập; không thay thế thời gian chờ xác minh hoặc dịch vụ chuyên môn bên ngoài. Pilot phải dành thêm thời gian vận hành theo số đơn thực tế, phần này sử dụng buffer và có thể kéo dài lịch.

Thuê hỗ trợ ngắn hạn nên ưu tiên rà soát pháp lý/thuế, kiểm bảo mật và kiểm tra tài chính độc lập. Ngân sách dịch vụ, thiết bị, tài khoản store, phí OTP, PSP/carrier, backup và vốn hoàn tiền là khoản riêng với effort code. Không chốt chi phí tiền trước khi chọn provider và lấy báo giá.

## 3. Roadmap 52 tuần kể từ ngày bắt đầu

Các phase trong CSV ánh xạ bảng dưới. Tuần là cửa sổ dự kiến; dependencies và cổng chất lượng được ưu tiên hơn ngày lịch. P0 là tối thiểu để mở live, P1 là hoàn thiện nếu còn sức, P2 là sau năm đầu. App buyer cơ bản thuộc P0 theo mục tiêu có cả ứng dụng và web.

| Phase | Tuần | Kết quả và cổng nghiệm thu |
|---|---|---|
| A — Discovery | 1–4 | Chọn ngành, mô hình tiền, khảo sát có bằng chứng, policy v1, user story/wireframe, PSP/carrier feasibility. **G0:** chưa có đường tiền hợp lệ thì chỉ tiếp tục sandbox |
| B — Foundation | 5–8 | Repo, CI, môi trường, schema, Keycloak, quyền shop, observability, media, backup nền. **G1:** đăng nhập/ownership và restore smoke đạt |
| C — Merchant/catalog | 9–14 | Onboarding, duyệt shop, danh mục/SKU, tồn/reservation, shop/PDP/search. **G2:** vertical slice seller đăng hàng → buyer xem; test tồn đồng thời |
| D — Commerce | 15–23 | Quote, nhiều shop, voucher, idempotency, payment/COD, cancel, outbox, shipment. **G3:** sandbox đặt-trả-giao-hủy và late payment không mất/nhân tiền |
| E — Aftersales/finance | 24–31 | Return/refund, stock hoàn, journal, COD/PSP reconcile, payable/payout. **G4:** đơn mẫu khớp từng đồng và đầy đủ ngoại lệ |
| F — Portals | 32–35 | Hoàn seller/admin/buyer flows, ticket/review/notification/export, chính sách/help. **G5:** UAT xuyên vai trò, không IDOR |
| G — Buyer app | 36–41 | App Android/iOS tối thiểu, hosted payment, deep link, push, case, build/store submission. **G6:** giao dịch sandbox trên thiết bị thật và không lộ token |
| H — Hardening | 42–46 | UAT hồi quy, tải/concurrency, security, recovery, data policy, onboarding guide/runbooks. **G7:** release candidate đủ điều kiện pilot |
| I — Closed pilot | 47–52 | Người bán mời, đơn thật hạn mức nhỏ, kiểm hoàn/đối soát/chi thực, sửa lỗi và đánh giá mở rộng. **G8:** số liệu đối soát và vận hành đủ tin cậy |
| X — Growth | Sau pilot | Các P1/P2 theo số liệu thực; không ngầm coi nằm trong lịch 52 tuần |

PSP/carrier đăng ký và hồ sơ pháp lý bắt đầu từ A, thử sandbox ở A/B, không đợi tới D mới xin API. Store account và yêu cầu xác minh bắt đầu sớm; build app đầy đủ ở G. Mỗi phase có thể sử dụng phần buffer, nhưng không mượn thời gian bằng cách bỏ invariant tiền/tồn.

## 4. Thứ tự triển khai và đường găng

```text
Chốt mô hình sàn/ngành/tiền
  → Xác thực đối tác + policy + dữ liệu/quyền
  → Seller được duyệt + SKU/tồn
  → Quote + order/reservation/voucher
  → Thu tiền/COD + cancel + giao vận
  → Return/refund + journal + đối soát + payout
  → Portals đủ vận hành + app buyer
  → Kiểm tải/bảo mật/khôi phục
  → Pilot thật có hạn mức → mở rộng
```

Mỗi 2 tuần demo một luồng chạy được, ghi quyết định và cập nhật estimate. Mỗi tháng đối chiếu remaining days với capacity. Nếu cuối tuần 23 chưa qua G3, dừng tính năng mở rộng và lên phương án lùi mốc hoặc cắt phần tiện ích; không cắt refund, reconcile, audit, ownership hoặc recovery.

Nếu không đủ thời gian app iOS/Android cả hai, ưu tiên ra buyer web + Android closed testing và công bố lại phạm vi/mốc iOS; đó là thay đổi phạm vi cần người sở hữu sản phẩm chốt, không gọi mặc định là đã hoàn thành mục tiêu app đa nền tảng.

## 5. Definition of Ready / Done

Ready: có actor, precondition, happy path, alternative/error path, permission, policy version, mẫu dữ liệu, acceptance và dependencies. Tiền/tồn phải có expected arithmetic/state transitions; UI có loading/empty/error/pending và responsive.

Done: code review tự kiểm có checklist theo risk; migration/recovery tương thích; test có ý nghĩa cho invariant; kiểm API và UI tương ứng; logs/metrics không lộ dữ liệu; tài liệu/runbook cập nhật; có evidence cho acceptance. “Đã viết API” chưa đủ cho task “buyer hoàn tiền được”.

## 6. Điều kiện bắt buộc trước live

1. Ba vai trò thực hiện được end-to-end trên staging; scope quyền kể cả export/file/API được kiểm. Không lỗi chặn bán/mua hoặc lỗ hổng nghiêm trọng chưa xử lý.
2. Stress test SKU cuối/voucher cuối; webhook duplicate/out-of-order; cancel race; payment timeout và late success; partial refund; payout unknown và retry; không có tác động tiền/tồn trùng.
3. Đối soát mẫu chứng minh: buyer đã trả/hoàn, PSP/carrier đã thu/chuyển, các phí/subsidy/tax, seller payable và payout; không còn chênh lệch không giải thích. Có luồng xử lý chargeback và refund sau payout.
4. Có hợp đồng/phương án với PSP/carrier đúng mô hình marketplace; đường tiền và trách nhiệm thu hộ/chi/hoàn được xác nhận. Không tự nhận mình có quyền vận hành ví hoặc giữ tiền kiểu escrow chỉ vì code có balance.
5. Quy chế, điều khoản, chính sách đổi trả, dữ liệu, hàng hạn chế, seller KYC và hồ sơ/thủ tục phù hợp đã được kiểm tra theo quy định có hiệu lực tại ngày live. Yêu cầu website và app được xét riêng.
6. Có nguồn tiền và quy trình hoàn buyer trong COD chưa về, seller âm hoặc chargeback; có hạn mức đơn/shop/chi và emergency stop có kiểm soát.
7. Restore drill chứng minh RPO/RTO thực; secret rotation và rollback drill; cảnh báo test tới đúng người; runbook PSP/carrier/DB lỗi.
8. App kiểm thiết bị thật, deep link, mạng yếu, submit lặp, token expiry, push không lộ dữ liệu; hồ sơ store/privacy/account deletion phù hợp được duyệt khi phát hành.
9. Người vận hành có thời gian phục vụ ticket/hoàn tiền/đối soát trong giờ hỗ trợ đã công bố; có đường hỗ trợ khi người sáng lập vắng mặt. Một người làm code không tự tạo ra năng lực CSKH 24/7.

## 7. Ma trận rủi ro và phản ứng

| Rủi ro | Mức | Biện pháp và trigger |
|---|---|---|
| Vượt scope một người | Cao | P0 rõ; không thêm P1 khi G3/G4 chưa qua; re-estimate mỗi phase |
| PSP không hỗ trợ charge nhiều shop/partial refund | Cao | Spike/hợp đồng từ A; chuyển checkout thanh toán từng shop nếu cần, cập nhật UX và allocation trước beta |
| Pháp lý/thuế thay đổi đến 2027 | Cao | Phiên bản policy; rà lại trước live; không hard-code rate/SLA |
| Chi trả trước khi nhận COD/tiền về | Cao | Reconcile + hold + vốn dự phòng + hạn mức; không dựa trạng thái delivered |
| Một người vừa code vừa xử lý tiền | Cao | Manual approval có evidence; kiểm tra độc lập bên ngoài trước mở rộng; nêu rõ chưa có four-eyes thực |
| Hàng giả/gian lận/return abuse | Cao | Seller mời, ngành ít rủi ro, kiểm nội dung, bằng chứng hai phía, manual review/appeal |
| Bảo trì quá nhiều hạ tầng | Cao | PostgreSQL, Keycloak, storage và monitoring có quản lý nếu phù hợp; hoãn polyglot DB/K8s |
| Store review hoặc carrier onboarding chậm | Vừa | Mở tài khoản/hồ sơ từ A; sandbox sớm; buffer cuối kỳ |
| Không có traffic/seller supply | Cao | Kiểm nhu cầu và người bán từ A; pilot mời; chưa chi ads/Live khi thiếu catalog chất lượng |

## 8. Pháp lý và nghiệp vụ liên quan — danh sách kiểm chứng

Theo nguồn chính thức được tra cứu ngày 19/11/2026: [Luật Thương mại điện tử 122/2025/QH15](https://congbao.chinhphu.vn/van-ban/luat-so-122-2025-qh15-468683/61714.htm) có hiệu lực 01/07/2026; [Nghị định 248/2026/NĐ-CP](https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-248-2026-nd-cp-469983.htm) quy định chi tiết một số điều của luật. Vì mục tiêu live khoảng 2027, không dùng riêng khung nghị định cũ làm kết luận tuân thủ.

[Luật Bảo vệ dữ liệu cá nhân](https://xaydungchinhsach.chinhphu.vn/quoc-hoi-da-thong-qua-luat-bao-ve-du-lieu-ca-nhan-119250626153701582.htm) có hiệu lực 01/01/2026. [Nghị định 117/2025/NĐ-CP](https://chinhphu.vn/?classid=1&docid=213883&pageid=27160&typegroupid=4) điều chỉnh quản lý thuế hoạt động trên nền tảng số của hộ/cá nhân. Đây là điểm bắt đầu để phân tích yêu cầu, chưa xác định đầy đủ nghĩa vụ riêng của VShop hoặc cập nhật mọi văn bản sửa đổi đến ngày ra mắt.

Task pháp lý phải xác minh: loại nền tảng và thủ tục tương ứng; tư cách seller và xác minh; nghĩa vụ thông tin/thuế/hóa đơn; quy trình hàng hạn chế/thu hồi; bảo vệ người tiêu dùng và khiếu nại; dữ liệu và chuyển ra ngoài lãnh thổ; retention/deletion; mô hình thu/giữ/chi tiền với tổ chức có thẩm quyền; hợp đồng carrier/PSP và chính sách store. Mức thuế/phí và kết luận đủ điều kiện hoạt động cần chuyên môn xác nhận theo mô hình thực tế.

## 9. Quyết định cần chủ sản phẩm chốt sau khi đọc

- Ngành hàng đầu tiên và tập seller mời; cơ chế thu hút người mua có khác biệt gì.
- Quỹ thời gian thực tế/tuần và ngân sách tháng cho hạ tầng, đối tác, rà soát ngoài và vốn hoàn.
- Chấp nhận modular monolith trước, PostgreSQL giao dịch chính; chỉ thêm Mongo/search cluster khi có bằng chứng cần.
- MVP dùng ticket trước realtime chat, chi seller thủ công có đối soát, một carrier/PSP, một kho/kiện mỗi shop.
- Xác nhận PSP đủ năng lực cho checkout nhiều shop và PSP/carrier có partial refund/COD/return phù hợp.

Các điểm chưa chốt được giữ là assumption hoặc decision pending, không ngăn việc review tài liệu này.
