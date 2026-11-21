"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

/** Records that the carrier remitted this cash, with the statement reference. Once only. */
export function CodSettleForm({ id }: { id: string }) {
  const router = useRouter();
  const [reference, setReference] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function settle() {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff("POST", `admin/cod-receivables/${id}/settled`, { reference });
    setBusy(false);
    if (!result.ok) return setError(result.message);
    router.refresh();
  }

  return (
    <div className="order-actions">
      <label htmlFor={`cod-${id}`}>Mã đối soát khi đơn vị vận chuyển chuyển tiền</label>
      <TextField.Root
        id={`cod-${id}`}
        value={reference}
        maxLength={100}
        onChange={(e) => setReference(e.target.value)}
      />
      <Button disabled={busy || reference.trim().length < 3} onClick={settle}>
        Đã nhận tiền đối soát
      </Button>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </div>
  );
}
