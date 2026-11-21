"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type {
  CreateShipmentInput,
  ReasonInput,
  SellerOrder,
  ShipOrderInput,
} from "@vshop/contracts";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

/** Buttons for the next legal step of an order. The API is the authority on what is allowed. */
export function OrderActions({ shopId, order }: { shopId: string; order: SellerOrder }) {
  const router = useRouter();
  const [text, setText] = useState("");
  const [weight, setWeight] = useState("500");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const base = `seller/shops/${shopId}/orders/${order.id}`;

  async function act(step: string, body?: unknown) {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await bff("POST", `${base}/${step}`, body ?? {});
    setBusy(false);
    if (!result.ok) return setError(result.message);
    setText("");
    router.refresh();
  }

  const status = order.status;
  return (
    <div className="order-actions">
      {status === "AWAITING_SELLER" && (
        <>
          <Button disabled={busy} onClick={() => act("confirm")}>
            Xác nhận đơn
          </Button>
          <label htmlFor={`reason-${order.id}`}>Lý do từ chối</label>
          <TextField.Root
            id={`reason-${order.id}`}
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <Button
            color="red"
            variant="soft"
            disabled={busy}
            onClick={() => act("reject", { reason: text } satisfies ReasonInput)}
          >
            Từ chối đơn
          </Button>
        </>
      )}
      {status === "CONFIRMED" && (
        <Button disabled={busy} onClick={() => act("pack")}>
          Bắt đầu đóng gói
        </Button>
      )}
      {status === "PACKING" && (
        <Button disabled={busy} onClick={() => act("ready")}>
          Sẵn sàng giao
        </Button>
      )}
      {status === "READY_TO_SHIP" && (
        <>
          <label htmlFor={`weight-${order.id}`}>Khối lượng kiện hàng (gram)</label>
          <TextField.Root
            id={`weight-${order.id}`}
            type="number"
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
          />
          <Button
            disabled={busy}
            onClick={() =>
              act("shipment", { weightGrams: Number(weight) } satisfies CreateShipmentInput)
            }
          >
            Tạo vận đơn GHTK
          </Button>
          <p className="form-note">Hoặc giao thủ công bằng mã vận đơn tự nhập:</p>
          <label htmlFor={`track-${order.id}`}>Mã vận đơn (nhập tay)</label>
          <TextField.Root
            id={`track-${order.id}`}
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <Button
            disabled={busy}
            onClick={() => act("ship", { trackingCode: text } satisfies ShipOrderInput)}
          >
            Đã giao cho đơn vị vận chuyển
          </Button>
        </>
      )}
      {status === "IN_FULFILLMENT" && (
        <>
          <p className="form-note">
            Chế độ giao thủ công: trạng thái đã giao do bạn tự xác nhận, chưa có xác nhận từ đơn vị
            vận chuyển.
          </p>
          <Button disabled={busy} onClick={() => act("delivered")}>
            Xác nhận đã giao
          </Button>
          <Button color="red" variant="soft" disabled={busy} onClick={() => act("shipment/cancel")}>
            Hủy vận đơn GHTK
          </Button>
          <p className="form-note">
            Giao không thành công và hàng đã về tay bạn? Đơn sẽ bị hủy, tiền hàng đã thu online được
            hoàn lại cho người mua.
          </p>
          <Button
            color="orange"
            variant="soft"
            disabled={busy}
            onClick={() => act("parcel-returned", { restock: true })}
          >
            Hàng giao thất bại đã hoàn về shop (nhập kho lại)
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
