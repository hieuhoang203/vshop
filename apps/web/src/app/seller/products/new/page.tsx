import Link from "next/link";
import { getCategories } from "@/lib/api";
import { loadSellerContext } from "@/lib/seller-context";
import { PortalNav } from "@/components/portal-nav";
import { SellerBlocked } from "@/components/seller/seller-blocked";
import { ProductBuilder } from "@/components/seller/product-builder";

export const metadata = { title: "Thêm sản phẩm" };

export default async function NewProduct() {
  const context = await loadSellerContext();
  if (context.kind !== "ok") return <SellerBlocked context={context} />;
  const categories = await getCategories();
  return (
    <div className="container page-space">
      <div className="breadcrumb">
        <Link href="/seller/products">Sản phẩm</Link>
        <span>/</span>
        <span>Thêm mới</span>
      </div>
      <div className="portal-heading">
        <span className="eyebrow">KÊNH NGƯỜI BÁN</span>
        <h1>Thêm sản phẩm</h1>
        <p>Sản phẩm được lưu ở dạng nháp. Bạn nhập kho và gửi duyệt ở bước sau.</p>
      </div>
      <PortalNav portal="seller" current="/seller/products" />
      <ProductBuilder shopId={context.shop.id} categories={categories} />
    </div>
  );
}
