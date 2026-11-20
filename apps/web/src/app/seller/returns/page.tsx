import { getSellerReturnCases } from "@/lib/api";
import { loadSellerContext } from "@/lib/seller-context";
import { PortalNav } from "@/components/portal-nav";
import { SellerBlocked } from "@/components/seller/seller-blocked";
import { CaseSummary } from "@/components/returns/case-timeline";
import { SellerReturnActions } from "@/components/returns/seller-return-actions";

export const metadata = { title: "Yêu cầu trả hàng" };

export default async function SellerReturns() {
  const context = await loadSellerContext();
  if (context.kind !== "ok") return <SellerBlocked context={context} />;
  const page = await getSellerReturnCases(context.shop.id);
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">KÊNH NGƯỜI BÁN</span>
        <h1>Yêu cầu trả hàng</h1>
        <p>
          Phản hồi đúng hạn. Quá hạn mà chưa phản hồi, yêu cầu chuyển cho quản trị viên quyết định.
        </p>
      </div>
      <PortalNav portal="seller" current="/seller/returns" />
      <p>{page.total} yêu cầu</p>
      {page.items.map((c) => (
        <section
          className="panel"
          key={c.id}
          aria-label={`Yêu cầu ${c.orderNumber}`}
          data-testid={`return-${c.orderNumber}`}
        >
          <h2>{c.orderNumber}</h2>
          <CaseSummary c={c} />
          <SellerReturnActions shopId={context.shop.id} c={c} />
        </section>
      ))}
      {!page.items.length && <div className="panel empty-state">Chưa có yêu cầu trả hàng.</div>}
    </div>
  );
}
