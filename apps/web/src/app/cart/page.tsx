import { ShoppingCart } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getCart } from "@/lib/api";
import { CartView } from "@/components/cart/cart-view";
import Link from "next/link";

export const metadata = { title: "Giỏ hàng" };

export default async function CartPage() {
  const session = await getSession();
  if (!session)
    return (
      <div className="container portal-intro page-space">
        <div className="portal-symbol">
          <ShoppingCart size={34} />
        </div>
        <span className="eyebrow">GIỎ HÀNG</span>
        <h1>Đăng nhập để xem giỏ hàng</h1>
        <p>Giỏ hàng được lưu theo tài khoản của bạn.</p>
        <Link href="/api/auth/login?returnTo=/cart" className="button button-dark">
          Đăng nhập để tiếp tục
        </Link>
      </div>
    );
  if (!session.roles.some((r) => r === "BUYER" || r === "SELLER"))
    return (
      <div className="container empty-state page-space">
        <ShoppingCart size={40} />
        <h1>Tài khoản này không có giỏ hàng</h1>
        <p>Giỏ hàng dành cho tài khoản người mua hoặc người bán.</p>
      </div>
    );
  const cart = await getCart();
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">GIỎ HÀNG</span>
        <h1>Giỏ hàng của bạn</h1>
        <p>Giá và tình trạng hàng luôn được tính lại từ dữ liệu hiện tại của gian hàng.</p>
      </div>
      <CartView initial={cart} />
    </div>
  );
}
