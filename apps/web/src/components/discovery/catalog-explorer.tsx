"use client";

import { Button, IconButton, Popover, Select, Slider, Tooltip, Badge } from "@radix-ui/themes";
import * as ToggleGroup from "@radix-ui/react-toggle-group";
import {
  Grid2X2,
  LayoutGrid,
  SlidersHorizontal,
  RotateCcw,
  X,
  Layers3,
  LoaderCircle,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import type { CatalogPage, Category } from "@vshop/contracts";
import { ProductCard } from "@/components/product-card";
import { formatMoney } from "@/lib/format";

export type CatalogFilters = {
  q: string;
  category: string;
  sort: string;
  minPrice?: number;
  maxPrice?: number;
  page: number;
};
export function CatalogExplorer({
  data,
  categories,
  filters,
  unavailable,
  headingLevel = 2,
}: {
  data: CatalogPage;
  categories: Category[];
  filters: CatalogFilters;
  unavailable: boolean;
  headingLevel?: 1 | 2;
}) {
  const Heading = headingLevel === 1 ? "h1" : "h2";
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [layout, setLayout] = useState("comfortable");
  const [price, setPrice] = useState([
    Math.min(filters.minPrice ?? 0, 2000000),
    Math.min(filters.maxPrice ?? 2000000, 2000000),
  ]);
  const [priceOpen, setPriceOpen] = useState(false);
  const hasPrice = filters.minPrice !== undefined || filters.maxPrice !== undefined;
  function href(patch: Partial<CatalogFilters>) {
    const next = { ...filters, page: 0, ...patch };
    const params = new URLSearchParams();
    if (next.q) params.set("q", next.q);
    if (next.category) params.set("category", next.category);
    if (next.sort !== "newest") params.set("sort", next.sort);
    if (next.minPrice !== undefined) params.set("minPrice", String(next.minPrice));
    if (next.maxPrice !== undefined) params.set("maxPrice", String(next.maxPrice));
    if (next.page) params.set("page", String(next.page));
    return `/${params.size ? `?${params}` : ""}#san-pham`;
  }
  function navigate(patch: Partial<CatalogFilters>) {
    startTransition(() => router.push(href(patch), { scroll: false }));
  }
  return (
    <section
      className={`catalog-section${isPending ? " catalog-pending" : ""}`}
      id="san-pham"
      aria-busy={isPending}
    >
      <div className="section-heading">
        <div>
          <span className="eyebrow">
            <span className="label-line" /> SẢN PHẨM
          </span>
          <Heading>
            {filters.q
              ? `Tìm thấy gu “${filters.q}”`
              : filters.category
                ? (categories.find((c) => c.slug === filters.category)?.name ??
                  "Khám phá theo danh mục")
                : "Khám phá sản phẩm"}
          </Heading>
          <p>
            {unavailable
              ? "Danh mục sẽ sớm trở lại."
              : `${data.total} sản phẩm${filters.q || filters.category || hasPrice ? " phù hợp với lựa chọn của bạn" : " trong danh mục"}`}
          </p>
        </div>
      </div>
      <div className="catalog-toolbar">
        <div className="filter-row">
          <Link
            className={!filters.category ? "filter active" : "filter"}
            href={href({ category: "" })}
          >
            Tất cả <span>{categories.reduce((sum, c) => sum + c.count, 0)}</span>
          </Link>
          {categories.map((c) => (
            <Link
              className={filters.category === c.slug ? "filter active" : "filter"}
              key={c.id}
              href={href({ category: c.slug })}
            >
              {c.name}
            </Link>
          ))}
        </div>
        <div className="catalog-controls">
          <Popover.Root
            open={priceOpen}
            onOpenChange={(open) => {
              setPriceOpen(open);
              if (open)
                setPrice([
                  Math.min(filters.minPrice ?? 0, 2000000),
                  Math.min(filters.maxPrice ?? 2000000, 2000000),
                ]);
            }}
          >
            <Popover.Trigger>
              <Button
                variant={hasPrice ? "soft" : "outline"}
                color={hasPrice ? "iris" : "gray"}
                className="price-filter-trigger"
              >
                <SlidersHorizontal size={16} />
                <span>Lọc giá</span>
                {hasPrice && <span className="filter-active-count">1</span>}
              </Button>
            </Popover.Trigger>
            <Popover.Content width="310px" className="price-popover">
              <h3>Ngân sách hôm nay?</h3>
              <p>Chọn khoảng giá hợp với bạn.</p>
              <div className="price-range-labels">
                <strong>{formatMoney(price[0])}</strong>
                <span>đến</span>
                <strong>{formatMoney(price[1])}</strong>
              </div>
              <Slider
                value={price}
                onValueChange={setPrice}
                min={0}
                max={2000000}
                step={10000}
                aria-label="Khoảng giá sản phẩm"
              />
              <div className="price-presets">
                {[
                  [0, 200000],
                  [200000, 500000],
                  [500000, 2000000],
                ].map(([min, max]) => (
                  <Button
                    key={min}
                    size="1"
                    variant="soft"
                    color="gray"
                    onClick={() => setPrice([min, max])}
                  >
                    {min === 0 ? "Đến 200k" : min === 200000 ? "200–500k" : "500k–2 triệu"}
                  </Button>
                ))}
              </div>
              <div className="popover-actions">
                <Button
                  variant="ghost"
                  color="gray"
                  onClick={() => {
                    setPrice([0, 2000000]);
                    setPriceOpen(false);
                    navigate({ minPrice: undefined, maxPrice: undefined });
                  }}
                >
                  Đặt lại
                </Button>
                <Button
                  onClick={() => {
                    setPriceOpen(false);
                    navigate({ minPrice: price[0], maxPrice: price[1] });
                  }}
                >
                  Áp dụng
                </Button>
              </div>
            </Popover.Content>
          </Popover.Root>
          <Select.Root value={filters.sort} onValueChange={(sort) => navigate({ sort })}>
            <Select.Trigger aria-label="Sắp xếp sản phẩm" className="sort-select" />
            <Select.Content>
              <Select.Item value="newest">Mới nhất</Select.Item>
              <Select.Item value="price-asc">Giá thấp đến cao</Select.Item>
              <Select.Item value="price-desc">Giá cao đến thấp</Select.Item>
            </Select.Content>
          </Select.Root>
          <ToggleGroup.Root
            type="single"
            value={layout}
            onValueChange={(value) => {
              if (value) setLayout(value);
            }}
            className="view-toggle"
            aria-label="Mật độ hiển thị"
          >
            <Tooltip content="Xem thoáng">
              <ToggleGroup.Item value="comfortable" aria-label="Xem thoáng">
                <Grid2X2 size={17} />
              </ToggleGroup.Item>
            </Tooltip>
            <Tooltip content="Xem nhiều hơn">
              <ToggleGroup.Item value="compact" aria-label="Xem nhiều hơn">
                <LayoutGrid size={17} />
              </ToggleGroup.Item>
            </Tooltip>
          </ToggleGroup.Root>
        </div>
      </div>
      {(filters.q || hasPrice) && (
        <div className="active-filters">
          {filters.q && (
            <Badge color="iris">
              Từ khóa: {filters.q}
              <IconButton
                variant="ghost"
                size="1"
                aria-label="Xóa từ khóa"
                onClick={() => navigate({ q: "" })}
              >
                <X size={12} />
              </IconButton>
            </Badge>
          )}
          {hasPrice && (
            <Badge color="iris">
              {formatMoney(filters.minPrice ?? 0)} –{" "}
              {filters.maxPrice === undefined ? "không giới hạn" : formatMoney(filters.maxPrice)}
              <IconButton
                variant="ghost"
                size="1"
                aria-label="Xóa lọc giá"
                onClick={() => navigate({ minPrice: undefined, maxPrice: undefined })}
              >
                <X size={12} />
              </IconButton>
            </Badge>
          )}
        </div>
      )}
      {isPending && (
        <div className="catalog-loading" role="status">
          <LoaderCircle size={17} className="spin" />
          Đang tìm món hợp gu…
        </div>
      )}
      {unavailable ? (
        <div className="empty-state" role="status">
          <Layers3 size={35} />
          <h3>Chưa thể tải sản phẩm</h3>
          <p>Kết nối đang tạm gián đoạn. Vui lòng thử lại sau.</p>
          <Button variant="outline" onClick={() => startTransition(() => router.refresh())}>
            Thử lại
          </Button>
        </div>
      ) : data.items.length ? (
        <div className={`product-grid view-${layout}`}>
          {data.items.map((product, index) => (
            <ProductCard key={product.id} product={product} index={index} />
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <span className="empty-shape">✳</span>
          <h3>Chưa tìm thấy sản phẩm phù hợp</h3>
          <p>Đổi khoảng giá hoặc thử một từ khóa khác. Gu mới có thể đang ở ngay gần đây.</p>
          <Button asChild>
            <Link href="/#san-pham">
              <RotateCcw size={16} /> Xem tất cả sản phẩm
            </Link>
          </Button>
        </div>
      )}
      {data.total > data.size && (
        <div className="catalog-pagination">
          <Button
            variant="soft"
            color="gray"
            disabled={filters.page === 0 || isPending}
            onClick={() => navigate({ page: filters.page - 1 })}
          >
            <ChevronLeft size={16} />
            Trước
          </Button>
          <span>
            Trang {filters.page + 1} / {Math.ceil(data.total / data.size)}
          </span>
          <Button
            variant="soft"
            color="gray"
            disabled={(filters.page + 1) * data.size >= data.total || isPending}
            onClick={() => navigate({ page: filters.page + 1 })}
          >
            Tiếp
            <ChevronRight size={16} />
          </Button>
        </div>
      )}
    </section>
  );
}
