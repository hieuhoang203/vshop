import { ShieldCheck } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getAdminReturnCases } from "@/lib/api";
import { LoginPrompt } from "@/components/portal";
import { MfaRequired } from "@/components/admin/mfa-required";
import { PortalNav } from "@/components/portal-nav";
import { CaseSummary } from "@/components/returns/case-timeline";
import { AdminReturnDecision } from "@/components/returns/admin-return-decision";

export const metadata = { title: "Yêu cầu trả hàng" };

export default async function AdminReturns() {
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
  const cases = await getAdminReturnCases();
  return (
    <div className="container page-space admin-page">
      <div className="portal-heading">
        <span className="eyebrow">QUẢN TRỊ VSHOP</span>
        <h1>Yêu cầu trả hàng cần quyết định</h1>
        <p>
          Gồm các yêu cầu người mua hoặc người bán nhờ quản trị viên xem xét, và các yêu cầu người
          bán không phản hồi đúng hạn. Mọi quyết định được ghi nhật ký.
        </p>
      </div>
      <PortalNav portal="admin" current="/admin/returns" />
      {cases.map((c) => (
        <section
          className="panel"
          key={c.id}
          aria-label={`Yêu cầu ${c.orderNumber}`}
          data-testid={`return-${c.orderNumber}`}
        >
          <h2>{c.orderNumber}</h2>
          <CaseSummary c={c} />
          <AdminReturnDecision c={c} />
        </section>
      ))}
      {!cases.length && <div className="panel empty-state">Không có yêu cầu nào đang chờ.</div>}
    </div>
  );
}
