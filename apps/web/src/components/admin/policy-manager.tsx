"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type { CreatePolicyInput, Policy } from "@vshop/contracts";
import { Button, Select, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

type PolicyType = CreatePolicyInput["policyType"];
const FIELDS: Record<PolicyType, { key: string; label: string }[]> = {
  SHIPPING_FLAT: [{ key: "amount", label: "Phí vận chuyển mỗi đơn (VND)" }],
  RETURN: [{ key: "windowDays", label: "Số ngày được đổi trả" }],
  COD_LIMITS: [
    { key: "maxOrderAmount", label: "Tối đa mỗi đơn COD (VND)" },
    { key: "maxOpenOrders", label: "Số đơn COD đang mở tối đa" },
  ],
  SELLER_CONFIRMATION: [{ key: "confirmMinutes", label: "Hạn người bán xác nhận đơn (phút)" }],
  PAYMENT_WINDOW: [
    { key: "paymentMinutes", label: "Thời gian thanh toán (phút)" },
    { key: "reservationBufferMinutes", label: "Đệm giữ hàng (phút)" },
  ],
};
const STATUS: Record<string, string> = {
  DRAFT: "Nháp",
  APPROVED: "Đang áp dụng",
  RETIRED: "Đã thay thế",
};

export function PolicyManager({ policies }: { policies: Policy[] }) {
  const router = useRouter();
  const [type, setType] = useState<PolicyType>("SHIPPING_FLAT");
  const [values, setValues] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function call(path: string, body?: unknown) {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff("POST", path, body);
    setBusy(false);
    if (!result.ok) return setError(result.message);
    setValues({});
    router.refresh();
  }
  function draft(event: React.FormEvent) {
    event.preventDefault();
    const content: Record<string, number> = {};
    for (const f of FIELDS[type]) content[f.key] = Number(values[f.key]);
    void call("admin/policies", { policyType: type, content } satisfies CreatePolicyInput);
  }

  return (
    <>
      <p className="form-note">
        Các con số này quyết định số tiền người mua phải trả. Bản nháp không được dùng; chỉ bản đã
        duyệt mới áp dụng, và mọi lần duyệt được ghi nhật ký. Cần đủ bốn loại đã duyệt thì mới đặt
        hàng được.
      </p>
      <form className="panel" onSubmit={draft} noValidate>
        <h2>Tạo bản nháp</h2>
        <label htmlFor="policy-type">Loại chính sách</label>
        <Select.Root
          value={type}
          onValueChange={(v) => {
            setType(v as PolicyType);
            setValues({});
          }}
        >
          <Select.Trigger id="policy-type" aria-label="Loại chính sách" />
          <Select.Content>
            {Object.keys(FIELDS).map((t) => (
              <Select.Item key={t} value={t}>
                {t}
              </Select.Item>
            ))}
          </Select.Content>
        </Select.Root>
        {FIELDS[type].map((f) => (
          <div key={f.key}>
            <label htmlFor={`f-${f.key}`}>{f.label}</label>
            <TextField.Root
              id={`f-${f.key}`}
              type="number"
              inputMode="numeric"
              value={values[f.key] ?? ""}
              onChange={(e) => setValues({ ...values, [f.key]: e.target.value })}
            />
          </div>
        ))}
        <Button type="submit" loading={busy} disabled={busy}>
          Lưu bản nháp
        </Button>
        {error && (
          <p role="alert" className="error-message">
            {error}
          </p>
        )}
      </form>
      <ul className="cart-lines">
        {policies.map((p) => (
          <li key={p.id} className="panel cart-line" data-testid={`policy-${p.policyType}`}>
            <div className="cart-line-info">
              <strong>
                {p.policyType} · v{p.version}
              </strong>
              <span className="row-sub">{JSON.stringify(p.content)}</span>
            </div>
            <div className="cart-line-total">
              <span className="status status-submitted">{STATUS[p.status]}</span>
              {p.status === "DRAFT" && (
                <Button
                  type="button"
                  disabled={busy}
                  aria-label={`Duyệt ${p.policyType} v${p.version}`}
                  onClick={() => call(`admin/policies/${p.id}/approve`)}
                >
                  Duyệt
                </Button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
