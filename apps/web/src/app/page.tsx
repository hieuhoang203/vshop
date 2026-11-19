import Link from "next/link";
import { Button, Badge } from "@radix-ui/themes";
import { Store, Sparkles, Fingerprint, Heart, MousePointer2 } from "lucide-react";
import { getCatalog, getCategories } from "@/lib/api";
import { DiscoveryTools } from "@/components/discovery/discovery-tools";
import { CatalogExplorer, type CatalogFilters } from "@/components/discovery/catalog-explorer";
import type { CatalogPage, Category } from "@vshop/contracts";

function integer(value: string | string[] | undefined, max = Number.MAX_SAFE_INTEGER) {
  if (typeof value !== "string" || value === "") return undefined;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 && parsed <= max ? parsed : undefined;
}
export default async function Home({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const params = await searchParams;
  const filters: CatalogFilters = {
    q: typeof params.q === "string" ? params.q.slice(0, 100) : "",
    category: typeof params.category === "string" ? params.category.slice(0, 100) : "",
    sort:
      typeof params.sort === "string" && ["newest", "price-asc", "price-desc"].includes(params.sort)
        ? params.sort
        : "newest",
    minPrice: integer(params.minPrice),
    maxPrice: integer(params.maxPrice),
    page: integer(params.page, 10000) ?? 0,
  };
  const options: Record<string, string> = {
    sort: filters.sort,
    page: String(filters.page),
    size: "12",
  };
  if (filters.minPrice !== undefined) options.minPrice = String(filters.minPrice);
  if (filters.maxPrice !== undefined) options.maxPrice = String(filters.maxPrice);
  let data: CatalogPage = { items: [], total: 0, page: 0, size: 12 };
  let categories: Category[] = [];
  let unavailable = false;
  try {
    [data, categories] = await Promise.all([
      getCatalog(filters.q, filters.category, options),
      getCategories(),
    ]);
  } catch {
    unavailable = true;
  }
  const isLanding =
    !filters.q &&
    !filters.category &&
    filters.minPrice === undefined &&
    filters.maxPrice === undefined &&
    filters.page === 0;
  return (
    <div className="container storefront">
      <CatalogExplorer
        data={data}
        categories={categories}
        filters={filters}
        unavailable={unavailable}
        headingLevel={1}
      />
      {isLanding && <DiscoveryTools products={data.items} />}
      <section className="why-vshop">
        <div className="why-heading">
          <Fingerprint size={32} />
          <h2>Gu riêng, trải nghiệm cũng riêng.</h2>
        </div>
        <div>
          <Heart size={21} />
          <h3>Thấy thích, cứ lưu</h3>
          <p>Giữ lại những món hợp gu ngay trên thiết bị của bạn.</p>
        </div>
        <div>
          <MousePointer2 size={21} />
          <h3>Khám phá theo cách bạn muốn</h3>
          <p>Lọc theo giá, đổi góc nhìn, tìm một chút bất ngờ.</p>
        </div>
      </section>
      <section className="seller-banner">
        <div>
          <Badge color="gray" variant="solid" radius="full">
            <Store size={13} /> FOR THE CREATORS
          </Badge>
          <h2>
            Gu của bạn có thể
            <br />
            là điều ai đó đang tìm.
          </h2>
          <p>
            Mang những sản phẩm của bạn đến VShop.
            <br />
            Bắt đầu bằng một gian hàng, kể tiếp câu chuyện riêng.
          </p>
          <Button size="4" asChild className="seller-cta">
            <Link href="/seller">
              Mở gian hàng của bạn <Sparkles size={18} />
            </Link>
          </Button>
        </div>
        <div className="seller-collage" aria-hidden="true">
          <span className="seller-big-star">✳</span>
          <span className="seller-sticker">
            MADE BY YOU.
            <br />
            FOUND ON VSHOP.
          </span>
          <img src="/products/tote.svg" alt="" width={310} height={270} />
          <span className="seller-mini-sticker">YOUR NEXT CHAPTER</span>
        </div>
      </section>
    </div>
  );
}
