import type { ReturnCase } from "@vshop/contracts";
import { ACTOR, CASE_STATUS, REASONS } from "@/lib/return-labels";
import { dateTime } from "@/components/orders/order-card";
import { formatMoney } from "@/lib/format";

/** What the case is about and what happened so far; shared by buyer, shop and administrator. */
export function CaseSummary({ c }: { c: ReturnCase }) {
  return (
    <>
      <p>
        <span className="status status-submitted">{CASE_STATUS[c.status] ?? c.status}</span> ·{" "}
        {REASONS[c.reasonCode] ?? c.reasonCode} · yêu cầu {formatMoney(c.requestedAmount)}
        {c.offeredAmount ? ` · đề xuất ${formatMoney(c.offeredAmount)}` : ""}
        {c.approvedAmount ? ` · duyệt hoàn ${formatMoney(c.approvedAmount)}` : ""}
      </p>
      <p className="form-note">{c.description}</p>
      {c.items.length > 0 && (
        <ul className="form-note" aria-label="Sản phẩm trả">
          {c.items.map((i) => (
            <li key={i.orderLineId}>
              {i.productName} × {i.quantity} · {formatMoney(i.amount)}
            </li>
          ))}
        </ul>
      )}
      {c.status === "REQUESTED" && (
        <p className="form-note">
          Hạn người bán phản hồi: {dateTime.format(new Date(c.sellerDeadlineAt))}
        </p>
      )}
      {c.returnTrackingCode && <p>Mã vận đơn trả hàng: {c.returnTrackingCode}</p>}
      {c.sellerNote && <p className="form-note">Người bán: {c.sellerNote}</p>}
      {c.adminNote && <p className="form-note">Quản trị viên: {c.adminNote}</p>}
      <ol>
        {c.events.map((e, i) => (
          <li key={i}>
            {dateTime.format(new Date(e.createdAt))} · {ACTOR[e.actorType] ?? e.actorType} ·{" "}
            {CASE_STATUS[e.toStatus] ?? e.toStatus}
            {e.note ? ` — ${e.note}` : ""}
          </li>
        ))}
      </ol>
    </>
  );
}
