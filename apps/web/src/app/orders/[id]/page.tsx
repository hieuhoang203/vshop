import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { getOrder, getReturnCase } from "@/lib/api";
import { BuyerReturnPanel, ReturnRequestForm } from "@/components/returns/buyer-return";
import { CancelOrder } from "@/components/orders/cancel-order";
import { RefundPanel } from "@/components/orders/refund-panel";
import { OrderCard, STATUS_TEXT, dateTime } from "@/components/orders/order-card";

export const metadata = { title: "Chi tiết đơn hàng" };

export default async function OrderDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!(await getSession())) redirect(`/api/auth/login?returnTo=/orders/${id}`);
  const order = await getOrder(id);
  const returnCase = await getReturnCase(id);
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">ĐƠN HÀNG</span>
        <h1>{order.orderNumber}</h1>
      </div>
      <OrderCard order={order} />
      {order.status === "AWAITING_SELLER" && <CancelOrder orderId={order.id} />}
      {returnCase && <BuyerReturnPanel c={returnCase} />}
      {!returnCase && order.status === "DELIVERED" && <ReturnRequestForm orderId={order.id} />}
      {order.refund && <RefundPanel orderId={order.id} refund={order.refund} />}
      <section className="panel" aria-label="Lịch sử trạng thái">
        <h2>Lịch sử</h2>
        <ol>
          {order.history.map((h, i) => (
            <li key={i}>
              {dateTime.format(new Date(h.createdAt))} · {STATUS_TEXT[h.toStatus] ?? h.toStatus}
              {h.reason ? ` — ${h.reason}` : ""}
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
