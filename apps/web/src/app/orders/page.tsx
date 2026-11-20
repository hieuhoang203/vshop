import { redirect } from "next/navigation";
import Link from "next/link";
import { getSession } from "@/lib/auth";
import { getOrders } from "@/lib/api";
import { STATUS_TEXT, dateTime } from "@/components/orders/order-card";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Đơn hàng của tôi" };

export default async function Orders() {
  if (!(await getSession())) redirect("/api/auth/login?returnTo=/orders");
  const page = await getOrders();
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">ĐƠN HÀNG</span>
        <h1>Đơn hàng của tôi</h1>
      </div>
      {page.items.length ? (
        <ul className="cart-lines">
          {page.items.map((o) => (
            <li key={o.id} className="panel cart-line">
              <div className="cart-line-info">
                <Link href={`/orders/${o.id}`}>{o.orderNumber}</Link>
                <span className="row-sub">
                  {o.shopName} · {o.firstProductName}
                  {o.itemCount > 1 ? ` và ${o.itemCount - 1} sản phẩm khác` : ""}
                </span>
                <span className="row-sub">{dateTime.format(new Date(o.createdAt))}</span>
              </div>
              <div className="cart-line-total">
                <strong>{formatMoney(o.payableAmount)}</strong>
                <span className="status status-submitted">{STATUS_TEXT[o.status] ?? o.status}</span>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <div className="panel empty-state">
          <h2>Bạn chưa có đơn hàng</h2>
          <Link href="/" className="button button-dark">
            Khám phá sản phẩm
          </Link>
        </div>
      )}
    </div>
  );
}
