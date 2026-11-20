"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type { ReasonInput } from "@vshop/contracts";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

/** Shown only while the shop has not started on the order. */
export function CancelOrder({ orderId }: { orderId: string }) {
  const router = useRouter();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function cancel() {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff("POST", `orders/${orderId}/cancel`, {
      reason,
    } satisfies ReasonInput);
    setBusy(false);
    if (!result.ok) return setError(result.message);
    router.refresh();
  }

  return (
    <section className="panel" aria-label="Hủy đơn">
      <h2>Hủy đơn</h2>
      <p className="form-note">Bạn có thể hủy khi người bán chưa xác nhận đơn.</p>
      <label htmlFor="cancel-reason">Lý do hủy</label>
      <TextField.Root
        id="cancel-reason"
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
      <Button color="red" variant="soft" loading={busy} disabled={busy} onClick={cancel}>
        Hủy đơn hàng
      </Button>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </section>
  );
}
