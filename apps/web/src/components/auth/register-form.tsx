"use client";
import { useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { Button, TextField } from "@radix-ui/themes";
import { Check, CircleCheck, Eye, EyeOff, Lock, Mail, UserRound } from "lucide-react";
import {
  passwordChecks,
  validateRegister,
  type RegisterErrors,
  type RegisterFields,
} from "@/lib/register-rules";

const empty: RegisterFields = {
  fullName: "",
  email: "",
  password: "",
  confirm: "",
  acceptTerms: false,
};

/**
 * Sign-up form. Checks every field as the person leaves it and again on submit; the API repeats
 * all rules, so this only saves a round trip. After success the person signs in with the same
 * email (pre-filled), the way the rest of the site authenticates.
 */
export function RegisterForm() {
  const [fields, setFields] = useState<RegisterFields>(empty);
  const [touched, setTouched] = useState<Partial<Record<keyof RegisterFields, boolean>>>({});
  const [serverErrors, setServerErrors] = useState<RegisterErrors>({});
  const [formError, setFormError] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [pending, setPending] = useState(false);
  const [doneEmail, setDoneEmail] = useState("");
  const firstInvalid = useRef<HTMLFormElement>(null);

  const errors = { ...validateRegister(fields), ...serverErrors };
  const visible = (key: keyof RegisterFields) =>
    touched[key] || serverErrors[key] ? errors[key] : undefined;
  const set = <K extends keyof RegisterFields>(key: K, value: RegisterFields[K]) => {
    setFields((f) => ({ ...f, [key]: value }));
    if (serverErrors[key]) setServerErrors((e) => ({ ...e, [key]: undefined }));
  };
  const blur = (key: keyof RegisterFields) => setTouched((t) => ({ ...t, [key]: true }));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    setTouched({ fullName: true, email: true, password: true, confirm: true, acceptTerms: true });
    setFormError("");
    const local = validateRegister(fields);
    if (Object.keys(local).length) {
      firstInvalid.current?.querySelector<HTMLElement>("[aria-invalid='true']")?.focus();
      return;
    }
    setPending(true);
    try {
      const response = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          fullName: fields.fullName,
          email: fields.email.trim(),
          password: fields.password,
          acceptTerms: fields.acceptTerms,
        }),
      });
      if (response.status === 201) {
        setDoneEmail(fields.email.trim().toLowerCase());
        setFields(empty);
        return;
      }
      const body = (await response.json().catch(() => ({}))) as { code?: string; message?: string };
      const message = body.message ?? "Không thể tạo tài khoản lúc này.";
      if (body.code === "EMAIL_ALREADY_REGISTERED") setServerErrors({ email: message });
      else setFormError(message);
    } catch {
      setFormError("Kết nối tạm gián đoạn. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  }

  if (doneEmail) {
    return (
      <div className="auth-card auth-success" role="status" data-testid="register-success">
        <span className="auth-success-icon">
          <CircleCheck size={40} />
        </span>
        <h2>Tạo tài khoản thành công!</h2>
        <p>
          Tài khoản <strong>{doneEmail}</strong> đã sẵn sàng. Đăng nhập để bắt đầu mua sắm.
        </p>
        <Button size="4" asChild>
          <Link href={`/api/auth/login?login_hint=${encodeURIComponent(doneEmail)}`}>
            Đăng nhập ngay
          </Link>
        </Button>
      </div>
    );
  }

  const checks = passwordChecks(fields.password);
  return (
    <form className="auth-card" onSubmit={submit} noValidate ref={firstInvalid}>
      <h2>Đăng ký tài khoản</h2>
      <p className="auth-sub">
        Đã có tài khoản? <Link href="/api/auth/login">Đăng nhập</Link>
      </p>

      <div className="auth-field">
        <label htmlFor="reg-name">Họ và tên</label>
        <TextField.Root
          id="reg-name"
          size="3"
          autoComplete="name"
          placeholder="Nguyễn Thị Hoa"
          maxLength={60}
          value={fields.fullName}
          aria-invalid={visible("fullName") ? true : undefined}
          aria-describedby={visible("fullName") ? "reg-name-error" : undefined}
          onChange={(e) => set("fullName", e.target.value)}
          onBlur={() => blur("fullName")}
        >
          <TextField.Slot>
            <UserRound size={18} />
          </TextField.Slot>
        </TextField.Root>
        {visible("fullName") && (
          <p id="reg-name-error" className="auth-error" role="alert">
            {visible("fullName")}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label htmlFor="reg-email">Email</label>
        <TextField.Root
          id="reg-email"
          size="3"
          type="email"
          autoComplete="email"
          placeholder="ten@example.com"
          maxLength={254}
          value={fields.email}
          aria-invalid={visible("email") ? true : undefined}
          aria-describedby={visible("email") ? "reg-email-error" : undefined}
          onChange={(e) => set("email", e.target.value)}
          onBlur={() => blur("email")}
        >
          <TextField.Slot>
            <Mail size={18} />
          </TextField.Slot>
        </TextField.Root>
        {visible("email") && (
          <p id="reg-email-error" className="auth-error" role="alert">
            {visible("email")}
            {serverErrors.email && (
              <>
                {" "}
                <Link
                  href={`/api/auth/login?login_hint=${encodeURIComponent(fields.email.trim())}`}
                >
                  Đăng nhập
                </Link>
              </>
            )}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label htmlFor="reg-password">Mật khẩu</label>
        <TextField.Root
          id="reg-password"
          size="3"
          type={showPassword ? "text" : "password"}
          autoComplete="new-password"
          maxLength={64}
          value={fields.password}
          aria-invalid={visible("password") ? true : undefined}
          aria-describedby="reg-password-rules"
          onChange={(e) => set("password", e.target.value)}
          onBlur={() => blur("password")}
        >
          <TextField.Slot>
            <Lock size={18} />
          </TextField.Slot>
          <TextField.Slot>
            <button
              type="button"
              className="auth-eye"
              aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
              aria-pressed={showPassword}
              onClick={() => setShowPassword((v) => !v)}
            >
              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </TextField.Slot>
        </TextField.Root>
        <ul id="reg-password-rules" className="auth-rules" aria-label="Yêu cầu mật khẩu">
          {checks.map((c) => (
            <li key={c.id} className={c.ok ? "ok" : undefined}>
              <Check size={14} aria-hidden="true" />
              {c.label}
              <span className="sr-only">{c.ok ? " (đạt)" : " (chưa đạt)"}</span>
            </li>
          ))}
        </ul>
        {visible("password") && (
          <p className="auth-error" role="alert">
            {visible("password")}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label htmlFor="reg-confirm">Nhập lại mật khẩu</label>
        <TextField.Root
          id="reg-confirm"
          size="3"
          type={showPassword ? "text" : "password"}
          autoComplete="new-password"
          maxLength={64}
          value={fields.confirm}
          aria-invalid={visible("confirm") ? true : undefined}
          aria-describedby={visible("confirm") ? "reg-confirm-error" : undefined}
          onChange={(e) => set("confirm", e.target.value)}
          onBlur={() => blur("confirm")}
        >
          <TextField.Slot>
            <Lock size={18} />
          </TextField.Slot>
        </TextField.Root>
        {visible("confirm") && (
          <p id="reg-confirm-error" className="auth-error" role="alert">
            {visible("confirm")}
          </p>
        )}
      </div>

      <div className="auth-field">
        <label className="auth-check" htmlFor="reg-terms">
          <input
            id="reg-terms"
            type="checkbox"
            checked={fields.acceptTerms}
            aria-invalid={visible("acceptTerms") ? true : undefined}
            onChange={(e) => set("acceptTerms", e.target.checked)}
            onBlur={() => blur("acceptTerms")}
          />
          <span>Tôi đã đọc và đồng ý với điều khoản sử dụng và chính sách bảo mật của VShop.</span>
        </label>
        {visible("acceptTerms") && (
          <p className="auth-error" role="alert">
            {visible("acceptTerms")}
          </p>
        )}
      </div>

      {formError && (
        <div role="alert" className="error-message">
          {formError}
        </div>
      )}
      <Button size="4" type="submit" loading={pending} disabled={pending} className="auth-submit">
        {pending ? "Đang tạo tài khoản…" : "Tạo tài khoản"}
      </Button>
    </form>
  );
}
