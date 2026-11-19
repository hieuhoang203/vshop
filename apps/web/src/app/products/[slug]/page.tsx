import Link from "next/link";
import { notFound } from "next/navigation";
import { Badge, Button } from "@radix-ui/themes";
import { Store, Package, Heart, Sparkles } from "lucide-react";
import { getProduct, getCatalog } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import { DetailActions } from "@/components/discovery/detail-actions";
import { ProductCard } from "@/components/product-card";
export default async function ProductDetail({ params }: { params: Promise<{ slug: string }> }) {
  let product;
  try {
    product = await getProduct((await params).slug);
  } catch (error) {
    if (error instanceof Error && error.message === "API_404") notFound();
    throw error;
  }
  const related = await getCatalog("", product.categorySlug)
    .then((result) => result.items.filter((item) => item.id !== product.id).slice(0, 3))
    .catch(() => []);
  return (
    <div className="container page-space">
      <div className="breadcrumb">
        <Link href="/">Khám phá</Link>
        <span>/</span>
        <Link href={`/?category=${product.categorySlug}#san-pham`}>{product.categoryName}</Link>
        <span>/</span>
        <span>{product.name}</span>
      </div>
      <div className="detail-grid">
        <div className={`detail-art art-${product.imageKey}`}>
          <span className="detail-art-label">IT'S A GOOD FIND.</span>
          <img
            src={`/products/${product.imageKey}.svg`}
            alt={product.name}
            width={520}
            height={480}
          />
          <span className="detail-art-star" aria-hidden="true">
            ✳
          </span>
          <Badge className="detail-art-badge" color="gray" size="2">
            <Heart size={14} /> Một chút chất riêng
          </Badge>
        </div>
        <section className="detail-copy">
          <Badge color="iris" variant="soft" size="2">
            {product.categoryName}
          </Badge>
          <h1>{product.name}</h1>
          <p className="detail-shop">
            <Store size={16} />
            {product.shopName}
          </p>
          <div className="detail-price">{formatMoney(product.price)}</div>
          <p className="detail-description">{product.description}</p>
          <DetailActions product={product} />
          <div className="notice">
            <Package size={21} />
            <div>
              <strong>Chưa mở bán trực tuyến</strong>
              <p>
                VShop đang thử nghiệm. Bạn có thể lưu món yêu thích và quay lại khi gian hàng mở
                bán.
              </p>
            </div>
          </div>
          <div className="detail-note">
            <Sparkles size={17} />
            Thêm một món hợp gu. Thêm một chút cảm hứng.
          </div>
        </section>
      </div>
      {related.length > 0 && (
        <section className="related-products">
          <div className="section-heading">
            <div>
              <span className="eyebrow">CÓ THỂ BẠN CŨNG THÍCH</span>
              <h2>Cùng một vibe.</h2>
            </div>
            <Button asChild variant="outline" color="gray">
              <Link href={`/?category=${product.categorySlug}#san-pham`}>Xem danh mục</Link>
            </Button>
          </div>
          <div className="product-grid">
            {related.map((item, index) => (
              <ProductCard key={item.id} product={item} index={index} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
