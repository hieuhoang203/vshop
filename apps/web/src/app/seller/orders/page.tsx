import { getSellerOrders } from "@/lib/api";
import { loadSellerContext } from "@/lib/seller-context";
import { formatMoney } from "@/lib/format";
import { PortalNav } from "@/components/portal-nav";
import { SellerBlocked } from "@/components/seller/seller-blocked";
import { OrderActions } from "@/components/seller/order-actions";
import { STATUS_TEXT, dateTime } from "@/components/orders/order-card";

export const metadata = { title: "Đơn hàng của gian hàng" };

export default async function SellerOrders({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>;
}) {
  const context = await loadSellerContext();
  if (context.kind !== "ok") return <SellerBlocked context={context} />;
  const { status = "" } = await searchParams;
  const page = await getSellerOrders(context.shop.id, /^[A-Z_]+$/.test(status) ? status : "");
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">KÊNH NGƯỜI BÁN</span>
        <h1>Đơn hàng</h1>
        <p>
          Thông tin liên hệ và địa chỉ người nhận chỉ hiện sau khi bạn xác nhận đơn. Giao hàng đang
          ở chế độ thủ công.
        </p>
      </div>
      <PortalNav portal="seller" current="/seller/orders" />
      <p>{page.total} đơn</p>
      {page.items.map((order) => (
        <section
          className="panel cart-group"
          key={order.id}
          aria-label={`Đơn ${order.orderNumber}`}
          data-testid={`seller-order-${order.orderNumber}`}
        >
          <h2>{order.orderNumber}</h2>
          <p>
            <span className="status status-submitted">
              {STATUS_TEXT[order.status] ?? order.status}
            </span>{" "}
            · {dateTime.format(new Date(order.createdAt))}
          </p>
          <ul className="cart-lines">
            {order.lines.map((line) => (
              <li key={line.skuId} className="cart-line">
                <div className="cart-line-info">
                  {line.productName}
                  <span className="row-sub">
                    {line.quantity} × {formatMoney(line.unitPrice)}
                  </span>
                </div>
                <strong>{formatMoney(line.netAmount)}</strong>
              </li>
            ))}
          </ul>
          <p>
            Phí vận chuyển {formatMoney(order.shippingAmount)} · Thu khi giao{" "}
            <strong>{formatMoney(order.payableAmount)}</strong>
          </p>
          <p className="form-note">
            Giao đến:{" "}
            {order.recipient.name
              ? `${order.recipient.name}, ${order.recipient.phone}, ${order.recipient.street}, `
              : "(chi tiết hiện sau khi xác nhận) "}
            {order.recipient.ward}, {order.recipient.province}
          </p>
          {order.trackingCode && <p>Mã vận đơn (nhập tay): {order.trackingCode}</p>}
          {order.cancelReason && <p>Lý do: {order.cancelReason}</p>}
          <OrderActions shopId={context.shop.id} order={order} />
        </section>
      ))}
      {!page.items.length && <div className="panel empty-state">Chưa có đơn hàng.</div>}
    </div>
  );
}
