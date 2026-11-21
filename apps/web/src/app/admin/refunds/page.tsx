import { ShieldCheck } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getAdminRefunds } from "@/lib/api";
import { LoginPrompt } from "@/components/portal";
import { MfaRequired } from "@/components/admin/mfa-required";
import { PortalNav } from "@/components/portal-nav";
import { RefundActions } from "@/components/admin/refund-actions";
import { dateTime } from "@/components/orders/order-card";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Hoàn tiền" };

const STATUS: Record<string, string> = {
  AWAITING_ACCOUNT: "Chờ người mua nhập tài khoản",
  SUBMITTED: "Đã gửi lệnh chi, chờ kết quả",
  APPROVED: "Sẵn sàng chuyển khoản",
  SUCCEEDED: "Đã chuyển khoản",
  FAILED: "Thất bại",
};
const REASON: Record<string, string> = {
  ORDER_CANCELED: "Người mua hủy đơn",
  SELLER_REJECTED: "Người bán từ chối",
  PAID_AFTER_EXPIRY: "Thanh toán đến muộn",
  ADMIN_GOODWILL: "Hoàn theo quyết định quản trị",
};

export default async function Refunds() {
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
  const refunds = await getAdminRefunds();
  const ready = refunds.filter((r) => r.status === "APPROVED").length;
  return (
    <div className="container page-space admin-page">
      <div className="portal-heading">
        <span className="eyebrow">QUẢN TRỊ VSHOP</span>
        <h1>Hoàn tiền</h1>
        <p>
          Hoàn tiền là chuyển tiền thật. Chuyển khoản trong ứng dụng ngân hàng cho đúng tài khoản và
          số tiền, rồi ghi lại mã giao dịch ngân hàng. Mỗi khoản chỉ ghi nhận một lần.
        </p>
      </div>
      <PortalNav portal="admin" current="/admin/refunds" />
      <p>
        {refunds.length} khoản · {ready} sẵn sàng chuyển khoản
      </p>
      {refunds.map((r) => (
        <section
          className="panel"
          key={r.id}
          aria-label={`Hoàn tiền ${r.orderNumber}`}
          data-testid={`refund-${r.orderNumber}`}
        >
          <h2>
            {r.orderNumber} · {formatMoney(r.amount)}
          </h2>
          <p>
            <span className="status status-submitted">{STATUS[r.status] ?? r.status}</span> ·{" "}
            {REASON[r.reason] ?? r.reason} · {dateTime.format(new Date(r.createdAt))}
          </p>
          {r.accountNumber && (
            <p>
              Nhận tại: {r.bankName} · <strong>{r.accountNumber}</strong> · {r.accountName}
            </p>
          )}
          {r.note && <p className="form-note">Ghi chú: {r.note}</p>}
          {r.payoutReference && <p>Mã giao dịch: {r.payoutReference}</p>}
          <RefundActions refund={r} />
        </section>
      ))}
      {!refunds.length && <div className="panel empty-state">Chưa có khoản hoàn tiền nào.</div>}
    </div>
  );
}
