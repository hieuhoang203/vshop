import { redirect } from "next/navigation";
import { Gift, PackageCheck, ShieldCheck, Truck } from "lucide-react";
import { getSession } from "@/lib/auth";
import { RegisterForm } from "@/components/auth/register-form";

export const metadata = { title: "Đăng ký tài khoản" };

const perks = [
  { icon: PackageCheck, text: "Theo dõi đơn hàng và nhận thông báo từng bước" },
  { icon: Truck, text: "Lưu sổ địa chỉ, đặt hàng nhanh hơn" },
  { icon: ShieldCheck, text: "Chính sách hoàn tiền và trả hàng rõ ràng" },
  { icon: Gift, text: "Lưu những món bạn thích để xem lại sau" },
];

export default async function Register() {
  if (await getSession()) redirect("/");
  return (
    <div className="container page-space auth-page">
      <aside className="auth-aside" aria-label="Lợi ích khi có tài khoản">
        <span className="eyebrow">CHÀO MỪNG ĐẾN VSHOP</span>
        <h1>Tạo tài khoản, mua sắm thoải mái.</h1>
        <ul>
          {perks.map(({ icon: Icon, text }) => (
            <li key={text}>
              <span>
                <Icon size={20} />
              </span>
              {text}
            </li>
          ))}
        </ul>
      </aside>
      <RegisterForm />
    </div>
  );
}
