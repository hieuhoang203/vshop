import { ShieldCheck } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getPolicies } from "@/lib/api";
import { LoginPrompt } from "@/components/portal";
import { MfaRequired } from "@/components/admin/mfa-required";
import { PortalNav } from "@/components/portal-nav";
import { PolicyManager } from "@/components/admin/policy-manager";

export const metadata = { title: "Chính sách" };

export default async function Policies() {
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
  const policies = await getPolicies();
  return (
    <div className="container page-space admin-page">
      <div className="portal-heading">
        <span className="eyebrow">QUẢN TRỊ VSHOP</span>
        <h1>Chính sách đặt hàng</h1>
      </div>
      <PortalNav portal="admin" current="/admin/policies" />
      <PolicyManager policies={policies} />
    </div>
  );
}
