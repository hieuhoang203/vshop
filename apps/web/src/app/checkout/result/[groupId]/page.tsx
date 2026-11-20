import { redirect } from "next/navigation";
import Link from "next/link";
import { CheckCircle2 } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getCheckoutGroup } from "@/lib/api";
import { OrderCard } from "@/components/orders/order-card";
import { AutoRefresh } from "@/components/orders/auto-refresh";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Kết quả đặt hàng" };

export default async function CheckoutResult({ params }: { params: Promise<{ groupId: string }> }) {
  const { groupId } = await params;
  if (!(await getSession())) redirect(`/api/auth/login?returnTo=/checkout/result/${groupId}`);
  // The page reads the stored result; it never trusts anything in the URL.
  const group = await getCheckoutGroup(groupId);
  const waiting = group.orders.some((o) => o.status === "PENDING_PAYMENT");
  const canceled = group.orders.every((o) => o.status === "CANCELED");
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">
          {waiting ? "ĐANG CHỜ THANH TOÁN" : canceled ? "ĐƠN ĐÃ HỦY" : "ĐẶT HÀNG THÀNH CÔNG"}
        </span>
        <h1>
          <CheckCircle2 size={28} aria-hidden="true" /> Đã tạo {group.orders.length} đơn hàng
        </h1>
        <p>
          {group.paymentMethod === "COD" ? "Thanh toán khi nhận hàng. " : ""}Tổng cần thanh toán:{" "}
          <strong>{formatMoney(group.totalAmount)}</strong>
        </p>
      </div>
      {waiting && (
        <div className="panel" role="status">
          <AutoRefresh />
          <p>
            Đang chờ thanh toán. Trang này tự cập nhật khi hệ thống nhận được xác nhận từ cổng thanh
            toán; việc quay lại từ cổng thanh toán chưa phải là xác nhận.
          </p>
          {group.paymentUrl && (
            <a className="button button-dark" href={group.paymentUrl}>
              Thanh toán ngay
            </a>
          )}
        </div>
      )}
      {group.orders.map((order) => (
        <OrderCard key={order.id} order={order} link />
      ))}
      <Link href="/orders" className="button button-dark">
        Xem tất cả đơn hàng
      </Link>
    </div>
  );
}
