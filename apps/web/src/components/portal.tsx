import Link from "next/link";
import { LockKeyhole, ShieldCheck, Store } from "lucide-react";
export function LoginPrompt({ target }: { target: "seller" | "admin" }) {
  const admin = target === "admin";
  return (
    <div className="container portal-intro page-space">
      <div className="portal-symbol">{admin ? <ShieldCheck size={34} /> : <Store size={34} />}</div>
      <span className="eyebrow">{admin ? "QUẢN TRỊ VSHOP" : "DÀNH CHO NGƯỜI BÁN"}</span>
      <h1>{admin ? "Không gian quản lý của bạn" : "Bắt đầu hành trình bán hàng"}</h1>
      <p>
        {admin
          ? "Đăng nhập bằng tài khoản được phân quyền để xử lý hồ sơ gian hàng."
          : "Tạo hồ sơ gian hàng, giới thiệu sản phẩm của bạn và cùng chuẩn bị cho ngày mở bán."}
      </p>
      <Link href={`/api/auth/login?returnTo=/${target}`} className="button button-dark">
        <LockKeyhole size={17} />
        Đăng nhập để tiếp tục
      </Link>
      <p className="subtle">
        {admin
          ? "Quyền truy cập được kiểm tra trên từng thao tác."
          : "Bạn có thể sử dụng tài khoản người mua để đăng ký gian hàng."}
      </p>
    </div>
  );
}
