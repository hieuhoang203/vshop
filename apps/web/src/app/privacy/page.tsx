import Link from "next/link";

export const metadata = { title: "Chính sách bảo mật" };

/** Draft privacy policy for the pilot; it lists only what the system really does today. */
export default function Privacy() {
  return (
    <div className="container page-space legal-page">
      <div className="portal-heading">
        <span className="eyebrow">PHÁP LÝ</span>
        <h1>Chính sách bảo mật</h1>
        <p>Cập nhật lần cuối: 21/11/2026 · Bản thử nghiệm, đang chờ rà soát pháp lý.</p>
      </div>
      <article className="panel legal-body">
        <h2>1. Dữ liệu chúng tôi thu thập</h2>
        <ul>
          <li>
            <strong>Tài khoản:</strong> họ tên, email, mật khẩu. Mật khẩu do hệ thống xác thực lưu ở
            dạng băm; VShop không đọc và không lưu mật khẩu của bạn.
          </li>
          <li>
            <strong>Giao hàng:</strong> tên người nhận, số điện thoại, địa chỉ. Các trường này được
            mã hóa khi lưu.
          </li>
          <li>
            <strong>Đơn hàng:</strong> sản phẩm, giá, trạng thái thanh toán và vận chuyển, yêu cầu
            hoàn tiền/trả hàng. Tài khoản nhận hoàn tiền được mã hóa khi lưu.
          </li>
          <li>
            <strong>Thông báo email</strong> (nếu bạn bật): địa chỉ email đã xác minh, lưu mã hóa.
          </li>
        </ul>
        <h2>2. Mục đích sử dụng</h2>
        <p>
          Xử lý đơn hàng, thanh toán, giao hàng, hoàn tiền; thông báo tình trạng đơn; ngăn chặn gian
          lận và bảo vệ tài khoản. VShop không bán dữ liệu cá nhân.
        </p>
        <h2>3. Chia sẻ dữ liệu</h2>
        <ul>
          <li>
            <strong>Người bán</strong> chỉ nhận thông tin cần để giao đơn của họ (tên, số điện
            thoại, địa chỉ giao).
          </li>
          <li>
            <strong>Đơn vị vận chuyển và cổng thanh toán</strong> nhận dữ liệu tối thiểu để thực
            hiện dịch vụ.
          </li>
          <li>Cơ quan nhà nước khi có yêu cầu hợp pháp.</li>
        </ul>
        <h2>4. Lưu trữ và bảo vệ</h2>
        <p>
          Dữ liệu được lưu trên hạ tầng do VShop quản lý, truyền qua kết nối an toàn khi triển khai
          thật. Phiên đăng nhập được giữ phía máy chủ; mã truy cập không lưu trong trình duyệt.
        </p>
        <h2>5. Quyền của bạn</h2>
        <p>
          Bạn có thể xem và sửa sổ địa chỉ, bật/tắt thông báo email trong tài khoản. Để yêu cầu truy
          cập, chỉnh sửa hoặc xóa dữ liệu cá nhân, vui lòng liên hệ bộ phận hỗ trợ của VShop; một số
          dữ liệu đơn hàng có thể phải giữ lại theo quy định pháp luật.
        </p>
        <h2>6. Cookie</h2>
        <p>
          VShop dùng cookie cần thiết để duy trì phiên đăng nhập và bảo mật. Danh sách sản phẩm đã
          lưu nằm trên thiết bị của bạn, không đồng bộ với tài khoản.
        </p>
        <p className="legal-note">
          Xem thêm <Link href="/terms">Điều khoản sử dụng</Link>.
        </p>
      </article>
    </div>
  );
}
