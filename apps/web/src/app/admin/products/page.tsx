import { Inbox, ShieldCheck } from "lucide-react";
import { getSession } from "@/lib/auth";
import { getReviewQueue } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import { IMAGE_CHOICES } from "@/lib/catalog-labels";
import { LoginPrompt } from "@/components/portal";
import { MfaRequired } from "@/components/admin/mfa-required";
import { PortalNav } from "@/components/portal-nav";
import { ProductReviewForm } from "@/components/admin/product-review-form";

export const metadata = { title: "Duyệt sản phẩm" };

const submitted = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh",
});

export default async function AdminProducts() {
  const session = await getSession();
  if (!session) return <LoginPrompt target="admin" />;
  if (!session.roles.includes("ADMIN"))
    return (
      <div className="container empty-state page-space">
        <ShieldCheck size={40} />
        <h1>Bạn không có quyền truy cập</h1>
        <p>Khu vực này dành cho tài khoản quản trị được phân quyền.</p>
      </div>
    );
  if (!session.mfa) return <MfaRequired />;
  const queue = await getReviewQueue();
  return (
    <div className="container page-space admin-page">
      <div className="portal-heading">
        <span className="eyebrow">QUẢN TRỊ VSHOP</span>
        <h1>Duyệt sản phẩm</h1>
        <p>Sản phẩm chỉ hiển thị cho người mua sau khi được chấp thuận.</p>
      </div>
      <PortalNav portal="admin" current="/admin/products" />
      <div className="queue-summary">
        <span className="portal-symbol small-symbol">
          <Inbox size={24} />
        </span>
        <div>
          <strong>{queue.total}</strong>
          <span>sản phẩm đang chờ duyệt</span>
        </div>
      </div>
      {queue.items.length ? (
        queue.items.map((product) => {
          const image = IMAGE_CHOICES.find((i) => i.key === product.imageKey);
          return (
            <article className="panel review-card" key={product.id}>
              <div className="review-card-header">
                <div>
                  <h2>{product.name}</h2>
                  <p>
                    {product.shopName} · cập nhật {submitted.format(new Date(product.updatedAt))} ·
                    phiên bản {product.version}
                  </p>
                </div>
                <span className="status status-submitted">Chờ duyệt</span>
              </div>
              <p className="shop-description">{product.description}</p>
              <p className="form-note">Ảnh: {image?.label ?? product.imageKey}</p>
              <table className="data-table compact">
                <caption className="sr-only">SKU của {product.name}</caption>
                <thead>
                  <tr>
                    <th scope="col">SKU</th>
                    <th scope="col">Phân loại</th>
                    <th scope="col" className="num">
                      Giá
                    </th>
                    <th scope="col">Bán</th>
                  </tr>
                </thead>
                <tbody>
                  {product.skus.map((sku) => (
                    <tr key={sku.id}>
                      <th scope="row">{sku.sellerSku}</th>
                      <td>
                        {product.options.length
                          ? product.options
                              .map(
                                (o) =>
                                  `${o.name}: ${o.values.find((v) => v.code === sku.optionValues[o.code])?.label ?? sku.optionValues[o.code]}`,
                              )
                              .join(", ")
                          : "Mặc định"}
                      </td>
                      <td className="num">{formatMoney(sku.price)}</td>
                      <td>{sku.status === "ACTIVE" ? "Có" : "Ngừng"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <ProductReviewForm
                key={product.version}
                productId={product.id}
                version={product.version}
              />
            </article>
          );
        })
      ) : (
        <div className="panel empty-state">
          <Inbox size={34} />
          <h2>Đã xử lý hết sản phẩm</h2>
          <p>Sản phẩm mới sẽ xuất hiện tại đây khi người bán gửi duyệt.</p>
        </div>
      )}
    </div>
  );
}
