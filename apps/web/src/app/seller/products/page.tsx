import Link from "next/link";
import { Plus, PackageOpen } from "lucide-react";
import { getSellerProducts } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import { loadSellerContext } from "@/lib/seller-context";
import { PortalNav } from "@/components/portal-nav";
import { ProductStatusBadge } from "@/components/status-badge";
import { SellerBlocked } from "@/components/seller/seller-blocked";

export const metadata = { title: "Sản phẩm của tôi" };

export default async function SellerProducts() {
  const context = await loadSellerContext();
  if (context.kind !== "ok") return <SellerBlocked context={context} />;
  const page = await getSellerProducts(context.shop.id);
  return (
    <div className="container page-space">
      <div className="portal-heading">
        <span className="eyebrow">KÊNH NGƯỜI BÁN</span>
        <h1>Sản phẩm của tôi</h1>
        <p>Sản phẩm cần được quản trị viên duyệt trước khi hiển thị cho người mua.</p>
      </div>
      <PortalNav portal="seller" current="/seller/products" />
      <div className="list-toolbar">
        <span>{page.total} sản phẩm</span>
        <Link href="/seller/products/new" className="button button-dark">
          <Plus size={17} /> Thêm sản phẩm
        </Link>
      </div>
      {page.items.length ? (
        <div className="panel table-panel">
          <table className="data-table">
            <caption className="sr-only">Danh sách sản phẩm của gian hàng</caption>
            <thead>
              <tr>
                <th scope="col">Sản phẩm</th>
                <th scope="col">Trạng thái</th>
                <th scope="col" className="num">
                  Giá
                </th>
                <th scope="col" className="num">
                  Có thể bán
                </th>
              </tr>
            </thead>
            <tbody>
              {page.items.map((product) => {
                const active = product.skus.filter((sku) => sku.status === "ACTIVE");
                const prices = active.map((sku) => sku.price);
                const low = Math.min(...prices);
                const high = Math.max(...prices);
                return (
                  <tr key={product.id}>
                    <th scope="row">
                      <Link href={`/seller/products/${product.id}`}>{product.name}</Link>
                      <span className="row-sub">
                        {product.skus.length} SKU
                        {product.status === "REJECTED" && product.rejectionReason
                          ? ` · ${product.rejectionReason}`
                          : ""}
                      </span>
                    </th>
                    <td>
                      <ProductStatusBadge status={product.status} />
                    </td>
                    <td className="num">
                      {prices.length
                        ? low === high
                          ? formatMoney(low)
                          : `${formatMoney(low)} – ${formatMoney(high)}`
                        : "—"}
                    </td>
                    <td className="num">{active.reduce((sum, sku) => sum + sku.available, 0)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="panel empty-state">
          <PackageOpen size={34} />
          <h2>Chưa có sản phẩm nào</h2>
          <p>Thêm sản phẩm đầu tiên, nhập kho rồi gửi duyệt để bắt đầu bán.</p>
        </div>
      )}
      {page.total > page.items.length && (
        <p className="form-note">Đang hiển thị {page.items.length} sản phẩm mới nhất.</p>
      )}
    </div>
  );
}
