"use client";
import { useState, type FormEvent } from "react";
import Link from "next/link";
import { Button, TextField } from "@radix-ui/themes";
import { Eye, EyeOff, Lock, Mail } from "lucide-react";

/**
 * Sign-in form. Sends the email and password to the site's own route (never to the browser's
 * address bar or storage) and goes to the page the person came from when it works.
 */
export function LoginForm({
  returnTo,
  initialEmail,
  notice,
}: {
  returnTo: string;
  initialEmail: string;
  notice?: string;
}) {
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [error, setError] = useState("");
  const [ssoUrl, setSsoUrl] = useState("");
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    if (!email.trim() || !password) {
      setError("Vui lòng nhập email và mật khẩu.");
      return;
    }
    setPending(true);
    setError("");
    setSsoUrl("");
    try {
      const response = await fetch("/api/auth/login-direct", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim(), password, returnTo }),
      });
      const body = (await response.json().catch(() => ({}))) as {
        ok?: boolean;
        returnTo?: string;
        message?: string;
        ssoUrl?: string;
      };
      if (response.ok && body.ok) {
        // A full navigation so every server component reads the new session.
        window.location.assign(body.returnTo ?? "/");
        return;
      }
      setError(body.message ?? "Không thể đăng nhập lúc này.");
      if (body.ssoUrl) setSsoUrl(body.ssoUrl);
      setPassword("");
    } catch {
      setError("Kết nối tạm gián đoạn. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="auth-card" onSubmit={submit} noValidate>
      <h2>Đăng nhập</h2>
      <p className="auth-sub">
        Chưa có tài khoản? <Link href="/register">Đăng ký ngay</Link>
      </p>
      {notice && (
        <div role="status" className="auth-notice">
          {notice}
        </div>
      )}
      <div className="auth-field">
        <label htmlFor="login-email">Email</label>
        <TextField.Root
          id="login-email"
          size="3"
          type="email"
          autoComplete="username"
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
      <div className="auth-field">
        <label htmlFor="login-password">Mật khẩu</label>
        <TextField.Root
          id="login-password"
          size="3"
          type={show ? "text" : "password"}
          autoComplete="current-password"
          maxLength={200}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        >
          <TextField.Slot>
            <Lock size={18} />
          </TextField.Slot>
          <TextField.Slot>
            <button
              type="button"
              className="auth-eye"
              aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
              aria-pressed={show}
              onClick={() => setShow((v) => !v)}
            >
              {show ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </TextField.Slot>
        </TextField.Root>
        <p className="auth-sub auth-forgot">
          <Link href="/forgot-password">Quên mật khẩu?</Link>
        </p>
      </div>
      {error && (
        <div role="alert" className="error-message">
          {error}
          {ssoUrl && (
            <>
              {" "}
              <a href={ssoUrl}>Đăng nhập qua trang xác thực</a>
            </>
          )}
        </div>
      )}
      <Button size="4" type="submit" loading={pending} disabled={pending} className="auth-submit">
        {pending ? "Đang đăng nhập…" : "Đăng nhập"}
      </Button>
      <p className="auth-sub auth-admin-link">
        Quản trị viên? <a href="/api/auth/login?mode=sso">Đăng nhập có xác thực hai bước</a>
      </p>
    </form>
  );
}
