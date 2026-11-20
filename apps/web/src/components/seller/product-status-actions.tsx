"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type { ProductStatus, SellerProduct } from "@vshop/contracts";
import { Button } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

const ACTIONS: Partial<
  Record<ProductStatus, { target: ProductStatus; label: string; hint: string }>
> = {
  DRAFT: {
    target: "PENDING_REVIEW",
    label: "Gửi duyệt",
    hint: "Quản trị viên sẽ kiểm tra nội dung trước khi sản phẩm được bán.",
  },
  REJECTED: {
    target: "PENDING_REVIEW",
    label: "Gửi duyệt lại",
    hint: "Hãy chỉnh sửa theo lý do bên trên rồi gửi lại.",
  },
  ACTIVE: {
    target: "HIDDEN",
    label: "Ẩn sản phẩm",
    hint: "Sản phẩm sẽ không hiển thị cho người mua. Bạn có thể hiện lại bất cứ lúc nào.",
  },
  HIDDEN: {
    target: "ACTIVE",
    label: "Hiện lại sản phẩm",
    hint: "Nội dung hiện tại đã được duyệt nên có thể hiển thị ngay.",
  },
};

export function ProductStatusActions({ product }: { product: SellerProduct }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const action = ACTIONS[product.status];
  const hasActiveSku = product.skus.some((sku) => sku.status === "ACTIVE");

  async function run() {
    if (!action || pending) return;
    setPending(true);
    setError("");
    const result = await bff(
      "PUT",
      `seller/shops/${product.shopId}/products/${product.id}/status`,
      {
        status: action.target,
        expectedVersion: product.version,
      },
    );
    setPending(false);
    if (!result.ok) return setError(result.message);
    router.refresh();
  }

  if (!action)
    return (
      <div className="notice">
        <p>Sản phẩm đang chờ quản trị viên duyệt. Bạn vẫn có thể chỉnh giá và nhập kho.</p>
      </div>
    );
  const blocked = action.target !== "HIDDEN" && !hasActiveSku;
  return (
    <div className="status-actions">
      <p className="form-note">{action.hint}</p>
      {blocked && <p className="form-note">Cần ít nhất một SKU đang bán.</p>}
      <Button size="3" onClick={run} loading={pending} disabled={pending || blocked}>
        {action.label}
      </Button>
      {error && (
        <div role="alert" className="error-message">
          {error}
        </div>
      )}
    </div>
  );
}
