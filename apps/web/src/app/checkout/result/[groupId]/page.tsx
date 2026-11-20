import { redirect } from "next/navigation";
import Link from "next/link";
import { CheckCircle2 } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getCheckoutGroup } from "@/lib/api";
import { OrderCard } from "@/components/orders/order-card";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Kết quả đặt hàng" };

export default async function CheckoutResult({ params }: { params: Promise<{ groupId: string }> }) {
  const { groupId } = await params;
  if (!(await getSession())) redirect(`/api/auth/login?returnTo=/checkout/result/${groupId}`);
  // The page reads the stored result; it never trusts anything in the URL.
  const group = await getCheckoutGroup(groupId);
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">ĐẶT HÀNG THÀNH CÔNG</span>
        <h1>
          <CheckCircle2 size={28} aria-hidden="true" /> Đã tạo {group.orders.length} đơn hàng
        </h1>
        <p>
          Thanh toán khi nhận hàng. Tổng cần thanh toán:{" "}
          <strong>{formatMoney(group.totalAmount)}</strong>
        </p>
      </div>
      {group.orders.map((order) => (
        <OrderCard key={order.id} order={order} link />
      ))}
      <Link href="/orders" className="button button-dark">
        Xem tất cả đơn hàng
      </Link>
    </div>
  );
}
