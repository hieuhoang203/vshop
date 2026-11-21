import Link from "next/link";
import { Sparkles, ShoppingBag } from "lucide-react";
export function Footer() {
  return (
    <footer className="footer">
      <div className="container footer-grid">
        <div>
          <Link href="/" className="brand">
            <span className="brand-icon">
              <ShoppingBag size={23} />
            </span>
            vshop<span className="brand-star">✳</span>
          </Link>
          <p>
            Chọn món hợp mình.
            <br />
            Sống theo cách mình thích.
          </p>
          <span className="footer-tag">
            <Sparkles size={13} /> A little more you.
          </span>
        </div>
        <div>
          <strong>Đi tìm một điều mới</strong>
          <Link href="/#san-pham">Khám phá sản phẩm</Link>
          <Link href="/?category=goc-lam-viec#san-pham">Góc làm việc</Link>
          <Link href="/?category=nha-cua#san-pham">Nhà cửa & đời sống</Link>
        </div>
        <div>
          <strong>Tài khoản của bạn</strong>
          <Link href="/orders">Đơn hàng của tôi</Link>
          <Link href="/account/addresses">Sổ địa chỉ</Link>
          <Link href="/notifications">Thông báo</Link>
          <Link href="/register">Tạo tài khoản mới</Link>
        </div>
        <div>
          <strong>Cùng tạo nên VShop</strong>
          <Link href="/seller">Trở thành người bán</Link>
          <Link href="/admin">Quản trị hệ thống</Link>
          <p className="footer-pilot">VShop đang trong giai đoạn thử nghiệm.</p>
        </div>
      </div>
      <div className="container footer-bottom">
        <span>© 2026 VShop. Được xây dựng tại Việt Nam.</span>
        <span>
          Made for your kind of everyday <span aria-hidden="true">✳</span>
        </span>
      </div>
    </footer>
  );
}
