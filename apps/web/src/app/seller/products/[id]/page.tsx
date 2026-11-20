import Link from "next/link";
import { notFound } from "next/navigation";
import { FileCheck2 } from "lucide-react";
import { getCategories, getSellerProduct } from "@/lib/api";
import { loadSellerContext } from "@/lib/seller-context";
import { PortalNav } from "@/components/portal-nav";
import { ProductStatusBadge } from "@/components/status-badge";
import { SellerBlocked } from "@/components/seller/seller-blocked";
import { ProductContentForm } from "@/components/seller/product-content-form";
import { ProductStatusActions } from "@/components/seller/product-status-actions";
import { SkuManager } from "@/components/seller/sku-manager";

export const metadata = { title: "Chi tiết sản phẩm" };

export default async function SellerProductDetail({ params }: { params: Promise<{ id: string }> }) {
  const context = await loadSellerContext();
  if (context.kind !== "ok") return <SellerBlocked context={context} />;
  const { id } = await params;
  let product;
  try {
    product = await getSellerProduct(context.shop.id, id);
  } catch (error) {
    // 404 for a missing product, a malformed id (400) or a product of another shop.
    if (error instanceof Error && /^API_(400|404)$/.test(error.message)) notFound();
    throw error;
  }
  const categories = await getCategories();
  return (
    <div className="container page-space">
      <div className="breadcrumb">
        <Link href="/seller/products">Sản phẩm</Link>
        <span>/</span>
        <span>{product.name}</span>
      </div>
      <div className="portal-heading">
        <span className="eyebrow">KÊNH NGƯỜI BÁN</span>
        <h1>{product.name}</h1>
        <p>
          <ProductStatusBadge status={product.status} />
        </p>
      </div>
      <PortalNav portal="seller" current="/seller/products" />
      {product.status === "REJECTED" && product.rejectionReason && (
        <div className="notice" role="note">
          <FileCheck2 size={22} />
          <div>
            <strong>Sản phẩm cần chỉnh sửa</strong>
            <p>{product.rejectionReason}</p>
          </div>
        </div>
      )}
      <section className="panel" aria-labelledby="status-heading">
        <h2 id="status-heading">Hiển thị cho người mua</h2>
        <ProductStatusActions key={product.version} product={product} />
      </section>
      <SkuManager product={product} />
      <ProductContentForm product={product} categories={categories} />
    </div>
  );
}
