import { redirect } from "next/navigation";
import { KeyRound, PackageCheck, ShieldCheck, Truck } from "lucide-react";
import { directLoginEnabled, getSession, safeReturnTo } from "@/lib/auth";
import { EMAIL_PATTERN } from "@/lib/register-rules";
import { LoginForm } from "@/components/auth/login-form";

export const metadata = { title: "Đăng nhập" };

const perks = [
  { icon: PackageCheck, text: "Xem và theo dõi đơn hàng của bạn" },
  { icon: Truck, text: "Đặt hàng nhanh với sổ địa chỉ đã lưu" },
  { icon: ShieldCheck, text: "Tài khoản được bảo vệ, mật khẩu không bao giờ lưu ở VShop" },
];

export default async function Login({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const returnTo = safeReturnTo(typeof params.returnTo === "string" ? params.returnTo : null);
  if (await getSession()) redirect(returnTo);
  // Without the sign-in client configured, fall back to the identity provider's own page.
  if (!directLoginEnabled()) redirect("/api/auth/login?mode=sso");
  const email =
    typeof params.email === "string" &&
    params.email.length <= 254 &&
    EMAIL_PATTERN.test(params.email)
      ? params.email
      : "";
  return (
    <div className="container page-space auth-page">
      <aside className="auth-aside" aria-label="Lợi ích khi đăng nhập">
        <span className="eyebrow">
          <KeyRound size={14} /> CHÀO MỪNG TRỞ LẠI
        </span>
        <h1>Đăng nhập để tiếp tục mua sắm.</h1>
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
      <LoginForm returnTo={returnTo} initialEmail={email} />
    </div>
  );
}
