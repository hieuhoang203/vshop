"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type { AdminRefund, ReasonInput, RefundPaidInput } from "@vshop/contracts";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

/** Pays out one refund by hand: send the transfer in the bank app, then record its reference. */
export function RefundActions({ refund }: { refund: AdminRefund }) {
  const router = useRouter();
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function act(path: string, body: unknown) {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff("POST", `admin/refunds/${refund.id}/${path}`, body);
    setBusy(false);
    if (!result.ok) return setError(result.message);
    router.refresh();
  }

  if (refund.status === "SUBMITTED") {
    return (
      <div className="order-actions">
        <p className="form-note">
          Lệnh chi đã gửi tới đơn vị thanh toán, đang chờ kết quả. Không chuyển khoản tay lúc này.
        </p>
        <Button disabled={busy} onClick={() => act("payout/check", {})}>
          Kiểm tra trạng thái chi
        </Button>
        {error && (
          <p role="alert" className="error-message">
            {error}
          </p>
        )}
      </div>
    );
  }
  if (refund.status !== "APPROVED") return null;
  return (
    <div className="order-actions">
      {refund.canPayAutomatically && (
        <>
          <Button disabled={busy} onClick={() => act("payout", {})}>
            Chi tự động qua payOS
          </Button>
          {refund.payoutFailure && (
            <p role="alert" className="error-message">
              Lần chi trước không thành công: {refund.payoutFailure}
            </p>
          )}
          <p className="form-note">Hoặc chuyển khoản tay rồi ghi nhận mã giao dịch:</p>
        </>
      )}
      <label htmlFor={`ref-${refund.id}`}>Mã giao dịch ngân hàng sau khi chuyển khoản</label>
      <TextField.Root
        id={`ref-${refund.id}`}
        value={reference}
        onChange={(e) => setReference(e.target.value)}
      />
      <Button
        disabled={busy}
        onClick={() => act("paid", { payoutReference: reference } satisfies RefundPaidInput)}
      >
        Đã chuyển khoản
      </Button>
      <label htmlFor={`bad-${refund.id}`}>Lý do tài khoản không dùng được</label>
      <TextField.Root
        id={`bad-${refund.id}`}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
      <Button
        color="red"
        variant="soft"
        disabled={busy}
        onClick={() => act("bad-account", { reason } satisfies ReasonInput)}
      >
        Sai tài khoản, hỏi lại người mua
      </Button>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </div>
  );
}
