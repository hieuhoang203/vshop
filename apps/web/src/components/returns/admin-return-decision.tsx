"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type { DecideReturnInput, ReturnCase } from "@vshop/contracts";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

/** The administrator settles a case: refund only, return and refund, or reject. */
export function AdminReturnDecision({ c }: { c: ReturnCase }) {
  const router = useRouter();
  const [decision, setDecision] = useState<DecideReturnInput["decision"]>("REFUND_ONLY");
  const [amount, setAmount] = useState(String(c.requestedAmount));
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function decide() {
    if (busy) return;
    setBusy(true);
    setError("");
    const body: DecideReturnInput = {
      decision,
      note,
      ...(decision === "REFUND_ONLY" ? { amount: Number(amount) } : {}),
    };
    const result = await bff("POST", `admin/return-cases/${c.id}/decide`, body);
    setBusy(false);
    if (!result.ok) return setError(result.message);
    router.refresh();
  }

  return (
    <div className="order-actions">
      <fieldset>
        <legend>Quyết định</legend>
        {[
          ["REFUND_ONLY", "Hoàn tiền, người mua giữ hàng"],
          ["RETURN_AND_REFUND", "Yêu cầu trả hàng rồi hoàn tiền"],
          ["REJECT", "Từ chối"],
        ].map(([value, label]) => (
          <label key={value}>
            <input
              type="radio"
              name={`decision-${c.id}`}
              checked={decision === value}
              onChange={() => setDecision(value)}
            />{" "}
            {label}
          </label>
        ))}
      </fieldset>
      {decision === "REFUND_ONLY" && (
        <>
          <label htmlFor={`amt-${c.id}`}>Số tiền hoàn (VND)</label>
          <TextField.Root
            id={`amt-${c.id}`}
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </>
      )}
      <label htmlFor={`why-${c.id}`}>Lý do quyết định</label>
      <TextField.Root id={`why-${c.id}`} value={note} onChange={(e) => setNote(e.target.value)} />
      <Button disabled={busy} onClick={decide}>
        Ghi nhận quyết định
      </Button>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </div>
  );
}
