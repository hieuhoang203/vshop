import { redirect } from "next/navigation";
import { MailCheck, ShieldCheck } from "lucide-react";
import { getSession } from "@/lib/auth";
import { captchaEnabled, captchaSiteKey } from "@/lib/captcha";
import { ForgotPasswordForm } from "@/components/auth/forgot-password-form";

export const metadata = { title: "Quên mật khẩu" };

export default async function ForgotPassword() {
  if (await getSession()) redirect("/");
  return (
    <div className="container page-space auth-page">
      <aside className="auth-aside" aria-label="Về việc đặt lại mật khẩu">
        <span className="eyebrow">KHÔI PHỤC TÀI KHOẢN</span>
        <h1>Lấy lại quyền truy cập trong vài phút.</h1>
        <ul>
          <li>
            <span>
              <MailCheck size={20} />
            </span>
            Liên kết đặt lại được gửi tới email đăng ký
          </li>
          <li>
            <span>
              <ShieldCheck size={20} />
            </span>
            Liên kết chỉ dùng một lần và hết hạn sau 30 phút
          </li>
        </ul>
      </aside>
      <ForgotPasswordForm captchaSiteKey={captchaEnabled() ? captchaSiteKey() : ""} />
    </div>
  );
}
