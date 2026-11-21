"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import type { Refund, RefundAccountInput } from "@vshop/contracts";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";

const REASON: Record<string, string> = {
  ORDER_CANCELED: "Bạn đã hủy đơn",
  SELLER_REJECTED: "Người bán không thể giao đơn này",
  PAID_AFTER_EXPIRY: "Thanh toán đến sau khi đơn đã hết hạn",
  ADMIN_GOODWILL: "Hoàn tiền theo quyết định của VShop",
  RETURN_APPROVED: "Yêu cầu trả hàng của bạn đã được chấp nhận",
  SHIPMENT_FAILED: "Đơn không giao được",
};

/** Shows where a refund stands and, while we need it, collects the bank account to send it to. */
export function RefundPanel({ orderId, refund }: { orderId: string; refund: Refund }) {
  const router = useRouter();
  const [values, setValues] = useState<RefundAccountInput>({
    bankName: "",
    accountNumber: "",
    accountName: "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [banks, setBanks] = useState<{ code: string; name: string }[]>([]);
  const [bankCode, setBankCode] = useState("");

  useEffect(() => {
    void bff<{ code: string; name: string }[]>("GET", "orders/banks").then((r) => {
      if (r.ok) setBanks(r.data);
    });
  }, []);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff("PUT", `orders/${orderId}/refund-account`, {
      ...values,
      ...(bankCode ? { bankCode } : {}),
    });
    setBusy(false);
    if (!result.ok) return setError(result.message);
    router.refresh();
  }

  return (
    <section className="panel" aria-label="Hoàn tiền" data-testid="refund-panel">
      <h2>Hoàn tiền {formatMoney(refund.amount)}</h2>
      <p className="form-note">{REASON[refund.reason] ?? refund.reason}</p>
      {refund.status === "AWAITING_ACCOUNT" && (
        <form onSubmit={save} noValidate>
          <p role="status">
            Để hoàn tiền, vui lòng cho biết tài khoản ngân hàng nhận tiền. Tiền được chuyển khoản
            cho đúng tài khoản này.
          </p>
          {banks.length > 0 && (
            <>
              <label htmlFor="refund-bank-list">Chọn ngân hàng trong danh sách</label>
              <select
                id="refund-bank-list"
                className="auth-select"
                value={bankCode}
                onChange={(e) => setBankCode(e.target.value)}
              >
                <option value="">Ngân hàng khác (nhập bên dưới)</option>
                {banks.map((b) => (
                  <option key={b.code} value={b.code}>
                    {b.name}
                  </option>
                ))}
              </select>
            </>
          )}
          <label htmlFor="refund-bank">Ngân hàng</label>
          <TextField.Root
            id="refund-bank"
            value={values.bankName}
            onChange={(e) => setValues({ ...values, bankName: e.target.value })}
          />
          <label htmlFor="refund-number">Số tài khoản</label>
          <TextField.Root
            id="refund-number"
            inputMode="numeric"
            value={values.accountNumber}
            onChange={(e) => setValues({ ...values, accountNumber: e.target.value })}
          />
          <label htmlFor="refund-name">Tên chủ tài khoản</label>
          <TextField.Root
            id="refund-name"
            value={values.accountName}
            onChange={(e) => setValues({ ...values, accountName: e.target.value })}
          />
          <Button type="submit" loading={busy} disabled={busy}>
            Gửi thông tin nhận tiền
          </Button>
          {error && (
            <p role="alert" className="error-message">
              {error}
            </p>
          )}
        </form>
      )}
      {refund.status === "APPROVED" && (
        <p role="status">
          Đã nhận thông tin tài khoản {refund.bankName} ****{refund.accountLast4}. VShop sẽ chuyển
          khoản cho bạn; bạn sẽ thấy mã giao dịch ngay tại đây khi hoàn tất.
        </p>
      )}
      {refund.status === "SUCCEEDED" && (
        <p role="status">
          Đã chuyển khoản {formatMoney(refund.amount)} tới {refund.bankName} ****
          {refund.accountLast4}. Mã giao dịch: <strong>{refund.payoutReference}</strong>.
        </p>
      )}
    </section>
  );
}
