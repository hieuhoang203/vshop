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

  if (refund.status !== "APPROVED") return null;
  return (
    <div className="order-actions">
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
