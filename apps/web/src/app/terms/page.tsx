import Link from "next/link";

export const metadata = { title: "Điều khoản sử dụng" };

/**
 * Draft terms for the pilot. They describe what the platform actually does today and must be
 * reviewed by a lawyer before public launch (see docs/26-dang-ky-tai-khoan.md).
 */
export default function Terms() {
  return (
    <div className="container page-space legal-page">
      <div className="portal-heading">
        <span className="eyebrow">PHÁP LÝ</span>
        <h1>Điều khoản sử dụng</h1>
        <p>Cập nhật lần cuối: 21/11/2026 · Bản thử nghiệm, đang chờ rà soát pháp lý.</p>
      </div>
      <article className="panel legal-body">
        <h2>1. VShop là gì</h2>
        <p>
          VShop là sàn thương mại điện tử kết nối người mua với các gian hàng độc lập. Người bán
          chịu trách nhiệm về sản phẩm, giá và việc giao hàng của gian hàng mình; VShop cung cấp nền
          tảng đặt hàng, thanh toán, theo dõi đơn, hoàn tiền và trả hàng theo các chính sách đã công
          bố.
        </p>
        <h2>2. Tài khoản</h2>
        <ul>
          <li>Bạn cung cấp thông tin đúng sự thật và giữ bí mật mật khẩu của mình.</li>
          <li>
            Mỗi email chỉ có một tài khoản. Bạn chịu trách nhiệm về hoạt động trong tài khoản.
          </li>
          <li>
            VShop có thể tạm khóa tài khoản có dấu hiệu gian lận hoặc vi phạm điều khoản, và sẽ nêu
            lý do khi có thể.
          </li>
        </ul>
        <h2>3. Đặt hàng và thanh toán</h2>
        <ul>
          <li>
            Giá và phí vận chuyển được hiển thị trước khi đặt. Đơn được tạo khi bạn xác nhận và hệ
            thống giữ hàng thành công.
          </li>
          <li>
            Thanh toán khi nhận hàng (COD) có giới hạn giá trị và số đơn đang mở; thanh toán trực
            tuyến được thực hiện qua cổng thanh toán đối tác. VShop không lưu số thẻ.
          </li>
          <li>Đơn chưa thanh toán đúng hạn sẽ tự hủy và hàng được trả về kho.</li>
        </ul>
        <h2>4. Hủy đơn, hoàn tiền và trả hàng</h2>
        <p>
          Bạn có thể hủy đơn khi gian hàng chưa giao cho đơn vị vận chuyển. Sau khi nhận hàng, bạn
          có thể gửi yêu cầu trả hàng trong thời hạn chính sách đang áp dụng; người bán phản hồi
          trong thời hạn quy định, và quản trị viên quyết định khi hai bên không thống nhất. Hoàn
          tiền được thực hiện về tài khoản bạn cung cấp.
        </p>
        <h2>5. Hành vi không được phép</h2>
        <ul>
          <li>Gian lận, lạm dụng chính sách hoàn tiền, tạo nhiều tài khoản để trục lợi.</li>
          <li>Truy cập trái phép, can thiệp hệ thống hoặc thu thập dữ liệu hàng loạt.</li>
          <li>Đăng bán hàng cấm, hàng giả hoặc nội dung vi phạm pháp luật (đối với người bán).</li>
        </ul>
        <h2>6. Trách nhiệm</h2>
        <p>
          VShop nỗ lực giữ dịch vụ ổn định nhưng không bảo đảm không gián đoạn. Trong phạm vi pháp
          luật cho phép, trách nhiệm của VShop đối với một đơn hàng không vượt quá giá trị đơn đó.
        </p>
        <h2>7. Thay đổi điều khoản</h2>
        <p>
          Khi điều khoản thay đổi, VShop thông báo trên website. Việc tiếp tục sử dụng sau thời điểm
          có hiệu lực nghĩa là bạn đồng ý với bản mới.
        </p>
        <p className="legal-note">
          Xem thêm <Link href="/privacy">Chính sách bảo mật</Link>.
        </p>
      </article>
    </div>
  );
}
