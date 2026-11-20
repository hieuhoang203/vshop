import { ShieldCheck } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getOverdueOrders } from "@/lib/api";
import { LoginPrompt } from "@/components/portal";
import { MfaRequired } from "@/components/admin/mfa-required";
import { PortalNav } from "@/components/portal-nav";
import { dateTime } from "@/components/orders/order-card";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Đơn quá hạn" };

export default async function OverdueOrdersPage() {
  const session = await getSession();
  if (!session) return <LoginPrompt target="admin" />;
  if (!session.roles.includes("ADMIN"))
    return (
      <div className="container empty-state page-space">
        <ShieldCheck size={40} />
        <h1>Bạn không có quyền truy cập</h1>
      </div>
    );
  if (!session.mfa) return <MfaRequired />;
  const queue = await getOverdueOrders();
  return (
    <div className="container page-space admin-page">
      <div className="portal-heading">
        <span className="eyebrow">QUẢN TRỊ VSHOP</span>
        <h1>Đơn quá hạn xác nhận</h1>
        <p>
          Đơn mà người bán chưa xác nhận hoặc từ chối trong hạn đã duyệt. Hệ thống không tự hủy; bộ
          phận hỗ trợ liên hệ người bán và quyết định.
        </p>
      </div>
      <PortalNav portal="admin" current="/admin/orders" />
      {queue.confirmMinutes === null ? (
        <p className="form-note" role="status">
          Chưa có chính sách hạn xác nhận đã được duyệt nên chưa xác định được đơn quá hạn. Hãy tạo
          và duyệt chính sách SELLER_CONFIRMATION ở mục Chính sách.
        </p>
      ) : (
        <p>
          Hạn xác nhận: {queue.confirmMinutes / 60} giờ · {queue.items.length} đơn quá hạn
        </p>
      )}
      {queue.items.map((order) => (
        <section className="panel" key={order.id} aria-label={`Đơn ${order.orderNumber}`}>
          <h2>{order.orderNumber}</h2>
          <p>
            {order.shopName} · {formatMoney(order.payableAmount)} · đặt lúc{" "}
            {dateTime.format(new Date(order.createdAt))} · quá hạn{" "}
            {Math.floor(order.minutesOverdue / 60)} giờ {order.minutesOverdue % 60} phút
          </p>
        </section>
      ))}
    </div>
  );
}
