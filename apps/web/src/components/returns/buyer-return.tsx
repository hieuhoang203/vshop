"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type { OpenReturnInput, ReturnCase, ShipOrderInput } from "@vshop/contracts";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";
import { REASONS } from "@/lib/return-labels";
import { CaseSummary } from "./case-timeline";

function useAction() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function run(method: "POST", path: string, body: unknown) {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff(method, path, body);
    setBusy(false);
    if (!result.ok) return setError(result.message);
    router.refresh();
  }
  return { busy, error, run };
}

/** Opens a return case for a delivered order. The server checks the window and the reason. */
export function ReturnRequestForm({ orderId }: { orderId: string }) {
  const { busy, error, run } = useAction();
  const [reason, setReason] = useState("DAMAGED");
  const [resolution, setResolution] = useState<"REFUND_ONLY" | "RETURN_AND_REFUND">("REFUND_ONLY");
  const [description, setDescription] = useState("");
  const mustReturn = reason === "CHANGED_MIND";

  return (
    <form
      className="panel"
      aria-label="Yêu cầu trả hàng"
      onSubmit={(e) => {
        e.preventDefault();
        void run("POST", `orders/${orderId}/return-case`, {
          reasonCode: reason,
          preferredResolution: mustReturn ? "RETURN_AND_REFUND" : resolution,
          description,
        } satisfies OpenReturnInput);
      }}
      noValidate
    >
      <h2>Yêu cầu trả hàng / hoàn tiền</h2>
      <p className="form-note">
        Bạn có thể gửi yêu cầu trong thời hạn đổi trả kể từ khi đơn được giao. Đổi ý chỉ áp dụng khi
        hàng còn nguyên và không hoàn phí vận chuyển.
      </p>
      <label htmlFor="return-reason">Lý do</label>
      <select id="return-reason" value={reason} onChange={(e) => setReason(e.target.value)}>
        {Object.entries(REASONS).map(([code, label]) => (
          <option key={code} value={code}>
            {label}
          </option>
        ))}
      </select>
      <label htmlFor="return-resolution">Bạn muốn</label>
      <select
        id="return-resolution"
        value={mustReturn ? "RETURN_AND_REFUND" : resolution}
        disabled={mustReturn}
        onChange={(e) => setResolution(e.target.value as "REFUND_ONLY" | "RETURN_AND_REFUND")}
      >
        <option value="REFUND_ONLY">Hoàn tiền, giữ lại hàng</option>
        <option value="RETURN_AND_REFUND">Trả hàng và hoàn tiền</option>
      </select>
      <label htmlFor="return-description">Mô tả vấn đề</label>
      <TextField.Root
        id="return-description"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <Button type="submit" loading={busy} disabled={busy}>
        Gửi yêu cầu
      </Button>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </form>
  );
}

/** The case so far and the next step open to the buyer. */
export function BuyerReturnPanel({ c }: { c: ReturnCase }) {
  const { busy, error, run } = useAction();
  const [tracking, setTracking] = useState("");
  const [district, setDistrict] = useState("");
  return (
    <section className="panel" aria-label="Yêu cầu trả hàng" data-testid="return-panel">
      <h2>Yêu cầu trả hàng</h2>
      <CaseSummary c={c} />
      <div className="order-actions">
        {c.status === "SELLER_OFFERED" && (
          <>
            <Button
              disabled={busy}
              onClick={() => run("POST", `return-cases/${c.id}/accept-offer`, {})}
            >
              Đồng ý nhận hoàn tiền
            </Button>
            <Button
              variant="soft"
              disabled={busy}
              onClick={() => run("POST", `return-cases/${c.id}/decline-offer`, {})}
            >
              Không đồng ý, nhờ quản trị viên xem xét
            </Button>
          </>
        )}
        {c.status === "SELLER_REJECTED" && (
          <Button disabled={busy} onClick={() => run("POST", `return-cases/${c.id}/escalate`, {})}>
            Nhờ quản trị viên xem xét lại
          </Button>
        )}
        {c.status === "AWAITING_RETURN" && (
          <>
            <label htmlFor={`ship-${c.id}`}>Mã vận đơn khi gửi trả hàng</label>
            <TextField.Root
              id={`ship-${c.id}`}
              value={tracking}
              onChange={(e) => setTracking(e.target.value)}
            />
            <p className="form-note">Hoặc để GHTK đến lấy hàng tại địa chỉ giao của đơn:</p>
            <label htmlFor={`district-${c.id}`}>Quận/huyện nơi lấy hàng</label>
            <TextField.Root
              id={`district-${c.id}`}
              value={district}
              maxLength={100}
              onChange={(e) => setDistrict(e.target.value)}
            />
            <Button
              variant="soft"
              disabled={busy || !district.trim()}
              onClick={() =>
                run("POST", `return-cases/${c.id}/return-shipment`, { district: district.trim() })
              }
            >
              Tạo vận đơn trả hàng qua GHTK
            </Button>
            <Button
              disabled={busy}
              onClick={() =>
                run("POST", `return-cases/${c.id}/ship`, {
                  trackingCode: tracking,
                } satisfies ShipOrderInput)
              }
            >
              Tôi đã gửi trả hàng
            </Button>
          </>
        )}
        {["REQUESTED", "SELLER_OFFERED", "AWAITING_RETURN"].includes(c.status) && (
          <Button
            color="red"
            variant="soft"
            disabled={busy}
            onClick={() => run("POST", `return-cases/${c.id}/cancel`, {})}
          >
            Hủy yêu cầu
          </Button>
        )}
      </div>
      {c.status === "REFUND_APPROVED" && (
        <p role="status">
          Khoản hoàn tiền của bạn nằm ở mục Hoàn tiền ngay trên trang đơn hàng này.
        </p>
      )}
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </section>
  );
}
