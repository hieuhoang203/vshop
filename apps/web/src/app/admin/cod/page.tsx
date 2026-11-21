import { ShieldCheck } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getAdminCod } from "@/lib/api";
import { LoginPrompt } from "@/components/portal";
import { MfaRequired } from "@/components/admin/mfa-required";
import { PortalNav } from "@/components/portal-nav";
import { CodSettleForm } from "@/components/admin/cod-settle-form";
import { dateTime } from "@/components/orders/order-card";
import { formatMoney } from "@/lib/format";

export const metadata = { title: "Tiền thu hộ COD" };

const STATUS: Record<string, string> = {
  WITH_CARRIER: "Đơn vị vận chuyển đang giữ",
  SETTLED: "Đã đối soát",
};

export default async function Cod() {
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
  const items = await getAdminCod();
  const open = items.filter((c) => c.status === "WITH_CARRIER");
  const holding = open.reduce((sum, c) => sum + c.dueAmount, 0);
  return (
    <div className="container page-space admin-page">
      <div className="portal-heading">
        <span className="eyebrow">QUẢN TRỊ VSHOP</span>
        <h1>Tiền thu hộ (COD)</h1>
        <p>
          Tiền mặt shipper đã thu của người mua. Khi đơn vị vận chuyển chuyển tiền về, ghi lại mã
          đối soát; số phải thu đã trừ các khoản đã hoàn cho người mua.
        </p>
      </div>
      <PortalNav portal="admin" current="/admin/cod" />
      <p>
        {open.length} khoản chưa đối soát · {formatMoney(holding)} đang nằm ở đơn vị vận chuyển
      </p>
      {items.map((c) => (
        <section
          className="panel"
          key={c.id}
          aria-label={`Thu hộ ${c.orderNumber}`}
          data-testid={`cod-${c.orderNumber}`}
        >
          <h2>
            Đơn {c.orderNumber} · {formatMoney(c.amount)}
          </h2>
          <p>
            <span className="status status-submitted">{STATUS[c.status] ?? c.status}</span> ·{" "}
            {c.collectedBy === "CARRIER" ? "Shipper thu" : "Shop tự thu"} ·{" "}
            {dateTime.format(new Date(c.createdAt))}
          </p>
          {c.refundedAmount > 0 && (
            <p className="form-note">
              Đã hoàn người mua {formatMoney(c.refundedAmount)} · còn phải thu{" "}
              {formatMoney(c.dueAmount)}
            </p>
          )}
          {c.settledReference && <p>Mã đối soát: {c.settledReference}</p>}
          {c.status === "WITH_CARRIER" && <CodSettleForm id={c.id} />}
        </section>
      ))}
      {items.length === 0 && <div className="panel empty-state">Chưa có khoản thu hộ nào.</div>}
    </div>
  );
}
