"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { Heart, Eye, Plus, Store } from "lucide-react";
import { IconButton, Tooltip } from "@radix-ui/themes";
import type { Product } from "@vshop/contracts";
import { formatMoney } from "@/lib/format";
import { useDiscovery } from "@/components/providers";
import { QuickViewDialog } from "@/components/discovery/quick-view";

export function ProductCard({ product, index = 0 }: { product: Product; index?: number }) {
  const { saved, toggleSaved } = useDiscovery();
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const isSaved = saved.some((p) => p.id === product.id);
  return (
    <article
      className="product-card"
      style={{ "--card-delay": `${Math.min(index, 8) * 45}ms` } as React.CSSProperties}
    >
      <div className={`product-art art-${product.imageKey}`}>
        <Link
          href={`/products/${product.slug}`}
          className="product-image-link"
          aria-label={`Khám phá ${product.name}`}
        >
          <img
            src={`/products/${product.imageKey}.svg`}
            alt={product.name}
            width={300}
            height={270}
            loading="lazy"
          />
        </Link>
        <span className="product-category">{product.categoryName}</span>
        <Tooltip content={isSaved ? "Bỏ lưu món này" : "Lưu trên thiết bị này"}>
          <button
            className="favorite-button"
            onClick={() => toggleSaved(product)}
            aria-label={`${isSaved ? "Bỏ lưu" : "Lưu"} ${product.name}`}
            aria-pressed={isSaved}
          >
            <Heart size={19} fill={isSaved ? "currentColor" : "none"} />
          </button>
        </Tooltip>
        <button
          className="quick-view-button"
          onClick={() => setOpen(true)}
          ref={trigger}
          aria-label={`Xem nhanh ${product.name}`}
        >
          <Eye size={16} /> Xem nhanh
        </button>
      </div>
      <div className="product-body">
        <p className="shop-name">
          <Store size={12} aria-hidden="true" /> {product.shopName}
        </p>
        <h3>
          <Link href={`/products/${product.slug}`}>{product.name}</Link>
        </h3>
        <div className="product-bottom">
          <div>
            <span className="price-caption">Giá tham khảo</span>
            <div className="product-price">{formatMoney(product.price)}</div>
          </div>
          <Tooltip content={`Xem nhanh ${product.name}`}>
            <IconButton
              variant="soft"
              color="gray"
              radius="full"
              onClick={() => setOpen(true)}
              aria-label={`Mở thông tin ${product.name}`}
            >
              <Plus size={20} />
            </IconButton>
          </Tooltip>
        </div>
      </div>
      <QuickViewDialog
        product={open ? product : null}
        open={open}
        onOpenChange={setOpen}
        triggerRef={trigger}
      />
    </article>
  );
}
