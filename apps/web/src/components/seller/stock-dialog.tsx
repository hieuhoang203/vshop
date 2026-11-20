"use client";
import type { StockAdjustmentInput } from "@vshop/contracts";
import { useCallback, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { StockBalance, StockMovement } from "@vshop/contracts";
import { Button, Dialog, Select, TextField } from "@radix-ui/themes";
import { bff, newIdempotencyKey } from "@/lib/client-api";

type Kind = "RECEIPT" | "ADJUSTMENT";
const dateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh",
});
const MOVEMENT_LABEL: Record<string, string> = {
  MIGRATION: "Chuyển đổi dữ liệu",
  RECEIPT: "Nhập kho",
  RESERVE: "Giữ hàng",
  RELEASE: "Nhả hàng",
  COMMIT: "Xuất kho",
  RETURN: "Hàng hoàn",
  ADJUSTMENT: "Điều chỉnh",
};

export function StockDialog({
  shopId,
  skuId,
  sellerSku,
}: {
  shopId: string;
  skuId: string;
  sellerSku: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [balance, setBalance] = useState<StockBalance | null>(null);
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [kind, setKind] = useState<Kind>("RECEIPT");
  const [loadError, setLoadError] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState("");
  const [pending, setPending] = useState(false);
  // The same key is reused for retries of the same input, so a lost response cannot apply twice.
  const key = useRef(newIdempotencyKey());
  const base = `seller/shops/${shopId}/inventory/skus/${skuId}`;

  const load = useCallback(async () => {
    setLoadError("");
    const [bal, mov] = await Promise.all([
      bff<StockBalance[]>("GET", base),
      bff<StockMovement[]>("GET", `${base}/movements?limit=20`),
    ]);
    if (!bal.ok) return setLoadError(bal.message);
    setBalance(bal.data[0] ?? null);
    setMovements(mov.ok ? mov.data : []);
  }, [base]);

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setDone("");
      setError("");
      void load();
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const magnitude = Number(form.get("quantity"));
    const reason = String(form.get("reason") ?? "").trim();
    setError("");
    setDone("");
    if (!Number.isInteger(magnitude) || magnitude < 1 || magnitude > 1_000_000)
      return setError("Số lượng phải là số nguyên từ 1 đến 1.000.000.");
    if (reason.length < 5) return setError("Cần nêu lý do ít nhất 5 ký tự.");
    const direction = String(form.get("direction")) === "decrease" ? -1 : 1;
    const delta = kind === "RECEIPT" ? magnitude : direction * magnitude;
    setPending(true);
    const result = await bff<StockBalance>(
      "POST",
      `seller/shops/${shopId}/inventory/adjustments`,
      { skuId, type: kind, delta, reason } satisfies StockAdjustmentInput,
      { "Idempotency-Key": key.current },
    );
    setPending(false);
    if (!result.ok) return setError(result.message);
    key.current = newIdempotencyKey();
    setDone(`Đã cập nhật. Có thể bán: ${result.data.available}.`);
    (event.target as HTMLFormElement).reset?.();
    await load();
    router.refresh();
  }

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Trigger>
        <Button type="button" variant="soft" size="2" aria-label={`Tồn kho ${sellerSku}`}>
          Tồn kho
        </Button>
      </Dialog.Trigger>
      <Dialog.Content maxWidth="560px">
        <Dialog.Title>Tồn kho {sellerSku}</Dialog.Title>
        <Dialog.Description size="2" color="gray">
          Mọi thay đổi được ghi lại cùng lý do. Hàng đang được giữ cho đơn hàng không thể bị trừ.
        </Dialog.Description>
        {loadError && (
          <p role="alert" className="error-message">
            {loadError}
          </p>
        )}
        {balance ? (
          <dl className="stock-figures">
            <div>
              <dt>Trong kho</dt>
              <dd>{balance.onHand}</dd>
            </div>
            <div>
              <dt>Đang giữ</dt>
              <dd>{balance.reserved}</dd>
            </div>
            <div>
              <dt>Có thể bán</dt>
              <dd>{balance.available}</dd>
            </div>
          </dl>
        ) : (
          !loadError && <p className="form-note">Chưa có tồn kho cho SKU này.</p>
        )}
        <form className="stock-form" onSubmit={submit} noValidate>
          <div className="form-row">
            <div>
              <label htmlFor={`stock-kind-${skuId}`}>Thao tác</label>
              <Select.Root
                value={kind}
                onValueChange={(v) => {
                  setKind(v as Kind);
                  key.current = newIdempotencyKey();
                }}
              >
                <Select.Trigger id={`stock-kind-${skuId}`} aria-label="Thao tác" />
                <Select.Content>
                  <Select.Item value="RECEIPT">Nhập thêm hàng</Select.Item>
                  <Select.Item value="ADJUSTMENT">Điều chỉnh (kiểm kê)</Select.Item>
                </Select.Content>
              </Select.Root>
            </div>
            {kind === "ADJUSTMENT" && (
              <div>
                <label htmlFor={`stock-direction-${skuId}`}>Hướng</label>
                <Select.Root
                  name="direction"
                  defaultValue="decrease"
                  onValueChange={() => (key.current = newIdempotencyKey())}
                >
                  <Select.Trigger id={`stock-direction-${skuId}`} aria-label="Hướng điều chỉnh" />
                  <Select.Content>
                    <Select.Item value="decrease">Giảm</Select.Item>
                    <Select.Item value="increase">Tăng</Select.Item>
                  </Select.Content>
                </Select.Root>
              </div>
            )}
            <div>
              <label htmlFor={`stock-qty-${skuId}`}>Số lượng</label>
              <TextField.Root
                id={`stock-qty-${skuId}`}
                name="quantity"
                type="number"
                inputMode="numeric"
                min={1}
                max={1000000}
                step={1}
                onChange={() => (key.current = newIdempotencyKey())}
              />
            </div>
          </div>
          <label htmlFor={`stock-reason-${skuId}`}>Lý do</label>
          <TextField.Root
            id={`stock-reason-${skuId}`}
            name="reason"
            maxLength={500}
            placeholder="Ví dụ: Nhập lô hàng ngày 20/11"
            onChange={() => (key.current = newIdempotencyKey())}
          />
          {error && (
            <p role="alert" className="error-message">
              {error}
            </p>
          )}
          {done && (
            <p role="status" className="form-note">
              {done}
            </p>
          )}
          <Button type="submit" loading={pending} disabled={pending}>
            Lưu thay đổi
          </Button>
        </form>
        {movements.length > 0 && (
          <>
            <h3 className="stock-history-title">Lịch sử gần đây</h3>
            <ul className="stock-history">
              {movements.map((m) => (
                <li key={m.id}>
                  <span>{MOVEMENT_LABEL[m.type] ?? m.type}</span>
                  <strong>
                    {m.deltaOnHand !== 0
                      ? `${m.deltaOnHand > 0 ? "+" : ""}${m.deltaOnHand}`
                      : `giữ ${m.deltaReserved > 0 ? "+" : ""}${m.deltaReserved}`}
                  </strong>
                  <span className="row-sub">{dateTime.format(new Date(m.createdAt))}</span>
                </li>
              ))}
            </ul>
          </>
        )}
        <div className="dialog-actions">
          <Dialog.Close>
            <Button type="button" variant="soft" color="gray">
              Đóng
            </Button>
          </Dialog.Close>
        </div>
      </Dialog.Content>
    </Dialog.Root>
  );
}
