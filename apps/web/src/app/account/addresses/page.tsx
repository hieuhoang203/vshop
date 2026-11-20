import Link from "next/link";
import { MapPin } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getAddresses } from "@/lib/api";
import { AddressBook } from "@/components/account/address-book";

export const metadata = { title: "Sổ địa chỉ" };

export default async function AddressesPage() {
  const session = await getSession();
  if (!session)
    return (
      <div className="container portal-intro page-space">
        <div className="portal-symbol">
          <MapPin size={34} />
        </div>
        <span className="eyebrow">TÀI KHOẢN</span>
        <h1>Đăng nhập để xem sổ địa chỉ</h1>
        <p>Địa chỉ giao hàng được lưu theo tài khoản của bạn.</p>
        <Link href="/api/auth/login?returnTo=/account/addresses" className="button button-dark">
          Đăng nhập để tiếp tục
        </Link>
      </div>
    );
  if (!session.roles.some((r) => r === "BUYER" || r === "SELLER"))
    return (
      <div className="container empty-state page-space">
        <MapPin size={40} />
        <h1>Tài khoản này không có sổ địa chỉ</h1>
        <p>Sổ địa chỉ dành cho tài khoản người mua hoặc người bán.</p>
      </div>
    );
  const addresses = await getAddresses();
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">TÀI KHOẢN</span>
        <h1>Sổ địa chỉ</h1>
        <p>Địa chỉ nhận hàng của bạn. Chỉ bạn xem và chỉnh sửa được.</p>
      </div>
      <AddressBook addresses={addresses} />
    </div>
  );
}
