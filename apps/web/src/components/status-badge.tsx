import type { ProductStatus } from "@vshop/contracts";
import { PRODUCT_STATUS } from "@/lib/catalog-labels";

export function ProductStatusBadge({ status }: { status: ProductStatus }) {
  const { label, tone } = PRODUCT_STATUS[status];
  return <span className={`status status-${tone}`}>{label}</span>;
}
