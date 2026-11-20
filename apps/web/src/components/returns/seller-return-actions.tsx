"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type {
  ConfirmReceivedInput,
  OfferRefundInput,
  ReasonInput,
  ReturnCase,
} from "@vshop/contracts";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

/** The shop answers a request, or inspects the goods that came back. */
export function SellerReturnActions({ shopId, c }: { shopId: string; c: ReturnCase }) {
  const router = useRouter();
  const [amount, setAmount] = useState(String(c.requestedAmount));
  const [note, setNote] = useState("");
  const [restock, setRestock] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const base = `seller/shops/${shopId}/return-cases/${c.id}`;

  async function act(step: string, body: unknown) {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff("POST", `${base}/${step}`, body);
    setBusy(false);
    if (!result.ok) return setError(result.message);
    router.refresh();
  }

  const canOffer = !(c.reasonCode === "CHANGED_MIND");
  return (
    <div className="order-actions">
      {c.status === "REQUESTED" && (
        <>
          <label htmlFor={`note-${c.id}`}>Ghi chú hoặc lý do</label>
          <TextField.Root
            id={`note-${c.id}`}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
          {canOffer && (
            <>
              <label htmlFor={`amount-${c.id}`}>Số tiền hoàn đề xuất (VND)</label>
              <TextField.Root
                id={`amount-${c.id}`}
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
              <Button
                disabled={busy}
                onClick={() =>
                  act("offer-refund", { amount: Number(amount), note } satisfies OfferRefundInput)
                }
              >
                Đề xuất hoàn tiền, không cần trả hàng
              </Button>
            </>
          )}
          <Button disabled={busy} onClick={() => act("approve-return", { note })}>
            Đồng ý nhận lại hàng
          </Button>
          <Button
            color="red"
            variant="soft"
            disabled={busy}
            onClick={() => act("reject", { reason: note } satisfies ReasonInput)}
          >
            Từ chối yêu cầu
          </Button>
        </>
      )}
      {c.status === "IN_RETURN" && (
        <>
          <label>
            <input
              type="checkbox"
              checked={restock}
              onChange={(e) => setRestock(e.target.checked)}
            />{" "}
            Nhập lại kho để bán tiếp
          </label>
          <Button
            disabled={busy}
            onClick={() =>
              act("confirm-received", { restock, note } satisfies ConfirmReceivedInput)
            }
          >
            Đã nhận hàng trả về, hoàn tiền
          </Button>
          <label htmlFor={`fail-${c.id}`}>Lý do hàng trả về không đạt</label>
          <TextField.Root
            id={`fail-${c.id}`}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
          <Button
            color="red"
            variant="soft"
            disabled={busy}
            onClick={() => act("inspection-failed", { reason: note } satisfies ReasonInput)}
          >
            Hàng không đạt, nhờ quản trị viên quyết định
          </Button>
        </>
      )}
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </div>
  );
}
