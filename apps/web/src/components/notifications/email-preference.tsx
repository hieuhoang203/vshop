"use client";
import { useState } from "react";
import type { EmailNotification } from "@vshop/contracts";
import { Button } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

/**
 * Opt-in to email copies of notifications. The address is the verified email of the sign-in
 * account (never typed here) and is shown masked.
 */
export function EmailPreference({ initial }: { initial: EmailNotification }) {
  const [state, setState] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [needsVerification, setNeedsVerification] = useState(false);
  const [verificationSent, setVerificationSent] = useState(false);

  async function toggle() {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff<EmailNotification>("PUT", "me/email-notifications", {
      enabled: !state.enabled,
    });
    setBusy(false);
    if (result.ok) setState(result.data);
    else {
      setError(result.message);
      // 409 on turning email on means the sign-in email is missing or not verified yet.
      setNeedsVerification(!state.enabled && result.status === 409);
    }
  }

  async function sendVerification() {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff("POST", "me/email-verification", {});
    setBusy(false);
    if (result.ok) setVerificationSent(true);
    else setError(result.message);
  }

  return (
    <section className="panel" aria-labelledby="email-pref-title" data-testid="email-preference">
      <h2 id="email-pref-title">Nhận thông báo qua email</h2>
      <p className="row-sub">
        {state.enabled
          ? `Đang gửi bản sao thông báo tới ${state.maskedEmail}.`
          : "Bật để nhận bản sao thông báo về đơn hàng, hoàn tiền và trả hàng. Chúng tôi dùng email đã xác minh của tài khoản đăng nhập."}
      </p>
      {!state.available && (
        <p className="row-sub" role="status">
          Hệ thống chưa cấu hình máy chủ gửi email nên chưa có email nào được gửi đi.
        </p>
      )}
      {error && (
        <p role="alert" className="form-error">
          {error}
        </p>
      )}
      {needsVerification && !verificationSent && (
        <p className="row-sub">
          <Button variant="soft" disabled={busy} onClick={sendVerification}>
            Gửi email xác minh
          </Button>
        </p>
      )}
      {verificationSent && (
        <p className="row-sub" role="status">
          Đã gửi email xác minh. Mở email, bấm liên kết, rồi quay lại đây bật thông báo. Nếu chưa
          được, hãy đăng xuất và đăng nhập lại.
        </p>
      )}
      <Button variant={state.enabled ? "soft" : "solid"} disabled={busy} onClick={toggle}>
        {state.enabled ? "Tắt thông báo email" : "Bật thông báo email"}
      </Button>
    </section>
  );
}
