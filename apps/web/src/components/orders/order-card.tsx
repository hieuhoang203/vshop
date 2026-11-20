import Link from "next/link";
import type { Order } from "@vshop/contracts";
import { formatMoney } from "@/lib/format";

export const STATUS_TEXT: Record<string, string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  AWAITING_SELLER: "Chờ người bán xác nhận",
  CONFIRMED: "Người bán đã xác nhận",
  PACKING: "Đang đóng gói",
  READY_TO_SHIP: "Sẵn sàng giao",
  IN_FULFILLMENT: "Đang giao",
  DELIVERED: "Đã giao",
  COMPLETED: "Hoàn tất",
  CANCEL_REQUESTED: "Đang yêu cầu hủy",
  CANCELED: "Đã hủy",
  REJECTED: "Người bán từ chối",
};
export const dateTime = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh",
});

export function OrderCard({ order, link }: { order: Order; link?: boolean }) {
  return (
    <section className="panel cart-group" aria-label={`Đơn ${order.orderNumber}`}>
      <h2>
        {link ? <Link href={`/orders/${order.id}`}>{order.orderNumber}</Link> : order.orderNumber} ·{" "}
        {order.shopName}
      </h2>
      <p>
        <span className="status status-submitted">{STATUS_TEXT[order.status] ?? order.status}</span>
      </p>
      <ul className="cart-lines">
        {order.lines.map((line) => (
          <li key={line.skuId} className="cart-line">
            <div className="cart-line-info">
              <Link href={`/products/${line.productSlug}`}>{line.productName}</Link>
              {line.variantLabel && <span className="row-sub">{line.variantLabel}</span>}
              <span className="row-sub">
                {line.quantity} × {formatMoney(line.unitPrice)}
              </span>
            </div>
            <div className="cart-line-total">
              <strong>{formatMoney(line.netAmount)}</strong>
            </div>
          </li>
        ))}
      </ul>
      <dl>
        <div>
          <dt>Tiền hàng</dt>
          <dd>{formatMoney(order.itemAmount)}</dd>
        </div>
        <div>
          <dt>Phí vận chuyển</dt>
          <dd>{formatMoney(order.shippingAmount)}</dd>
        </div>
        <div className="cart-total">
          <dt>{order.status === "PENDING_PAYMENT" ? "Cần thanh toán" : "Tổng thanh toán"}</dt>
          <dd>{formatMoney(order.payableAmount)}</dd>
        </div>
      </dl>
      <p className="form-note">
        Giao đến {order.recipient.name}, {order.recipient.phone} · {order.recipient.street},{" "}
        {order.recipient.ward}, {order.recipient.province}
      </p>
    </section>
  );
}
