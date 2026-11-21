"use client";
import { useState, type FormEvent } from "react";
import Link from "next/link";
import { Button, TextField } from "@radix-ui/themes";
import { CircleCheck, Mail } from "lucide-react";
import { Captcha } from "@/components/auth/captcha";
import { EMAIL_PATTERN } from "@/lib/register-rules";

/** Asks for a reset link. The confirmation is the same whether or not the address has an account. */
export function ForgotPasswordForm({ captchaSiteKey }: { captchaSiteKey: string }) {
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const [sentTo, setSentTo] = useState("");
  const [captcha, setCaptcha] = useState("");
  const [captchaReset, setCaptchaReset] = useState(0);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const address = email.trim();
    if (address.length > 254 || !EMAIL_PATTERN.test(address)) {
      setError("Địa chỉ email không hợp lệ.");
      return;
    }
    if (captchaSiteKey && !captcha) {
      setError("Vui lòng xác nhận bạn không phải người máy.");
      return;
    }
    setPending(true);
    setError("");
    try {
      const response = await fetch("/api/auth/forgot-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: address, captchaToken: captcha }),
      });
      if (response.status === 202) {
        setSentTo(address);
        return;
      }
      const body = (await response.json().catch(() => ({}))) as { message?: string };
      setError(body.message ?? "Không thể gửi email lúc này.");
      setCaptchaReset((n) => n + 1);
    } catch {
      setError("Kết nối tạm gián đoạn. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  }

  if (sentTo) {
    return (
      <div className="auth-card auth-success" role="status" data-testid="forgot-success">
        <span className="auth-success-icon">
          <CircleCheck size={40} />
        </span>
        <h2>Kiểm tra email của bạn</h2>
        <p>
          Nếu <strong>{sentTo}</strong> có tài khoản, chúng tôi đã gửi một liên kết đặt lại mật
          khẩu. Liên kết có hiệu lực trong 30 phút.
        </p>
        <Button size="4" asChild>
          <Link href="/login">Quay lại đăng nhập</Link>
        </Button>
      </div>
    );
  }

  return (
    <form className="auth-card" onSubmit={submit} noValidate>
      <h2>Quên mật khẩu</h2>
      <p className="auth-sub">Nhập email đăng ký, chúng tôi sẽ gửi liên kết đặt lại mật khẩu.</p>
      <div className="auth-field">
        <label htmlFor="forgot-email">Email</label>
        <TextField.Root
          id="forgot-email"
          size="3"
          type="email"
          autoComplete="email"
          placeholder="ten@example.com"
          maxLength={254}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        >
          <TextField.Slot>
            <Mail size={18} />
          </TextField.Slot>
        </TextField.Root>
      </div>
      <Captcha siteKey={captchaSiteKey} onToken={setCaptcha} resetKey={captchaReset} />
      {error && (
        <div role="alert" className="error-message">
          {error}
        </div>
      )}
      <Button size="4" type="submit" loading={pending} disabled={pending} className="auth-submit">
        {pending ? "Đang gửi…" : "Gửi liên kết"}
      </Button>
      <p className="auth-sub auth-admin-link">
        <Link href="/login">Quay lại đăng nhập</Link>
      </p>
    </form>
  );
}
