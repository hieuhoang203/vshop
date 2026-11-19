"use client";

import { Button, Dialog, IconButton, Badge } from "@radix-ui/themes";
import { Heart, X, Shuffle, Store, Package } from "lucide-react";
import Link from "next/link";
import type { Product } from "@vshop/contracts";
import { useDiscovery } from "@/components/providers";
import { formatMoney } from "@/lib/format";

export function QuickViewDialog({
  product,
  open,
  onOpenChange,
  surprise = false,
  onShuffle,
  triggerRef,
}: {
  product: Product | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  surprise?: boolean;
  onShuffle?: () => void;
  triggerRef?: React.RefObject<HTMLButtonElement | null>;
}) {
  const { saved, toggleSaved } = useDiscovery();
  if (!product) return null;
  const isSaved = saved.some((p) => p.id === product.id);
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content
        maxWidth="780px"
        className="discovery-dialog quick-view-dialog"
        onCloseAutoFocus={(event) => {
          if (triggerRef?.current) {
            event.preventDefault();
            triggerRef.current.focus();
          }
        }}
      >
        <div className="dialog-top">
          <Badge color={surprise ? "orange" : "iris"}>
            {surprise ? "MỘT CHÚT BẤT NGỜ" : "XEM NHANH"}
          </Badge>
          <Dialog.Close>
            <IconButton variant="ghost" color="gray" aria-label="Đóng xem nhanh">
              <X size={20} />
            </IconButton>
          </Dialog.Close>
        </div>
        <div className="quick-view-grid">
          <div className={`quick-art art-${product.imageKey}`}>
            <img
              src={`/products/${product.imageKey}.svg`}
              alt={product.name}
              width={350}
              height={330}
            />
            <span className="quick-art-spark" aria-hidden="true">
              ✳
            </span>
          </div>
          <div className="quick-copy">
            <span className="eyebrow">{product.categoryName}</span>
            <Dialog.Title size="7">{product.name}</Dialog.Title>
            <Dialog.Description>
              {surprise
                ? "Gợi ý ngẫu nhiên từ những sản phẩm đang xem. Thích thì lưu, chưa đúng gu thì thử món khác nhé."
                : product.description}
            </Dialog.Description>
            <p className="quick-shop">
              <Store size={14} />
              {product.shopName}
            </p>
            <p className="quick-price">{formatMoney(product.price)}</p>
            <div className="quick-stock">
              <Package size={15} />
              {product.stock > 0 ? "Có trong danh mục" : "Tạm hết hàng"}
              <span>· Chưa mở đặt hàng</span>
            </div>
            <div className="quick-actions">
              <Button
                onClick={() => toggleSaved(product)}
                variant={isSaved ? "soft" : "solid"}
                size="3"
              >
                <Heart size={17} fill={isSaved ? "currentColor" : "none"} />
                {isSaved ? "Đã lưu trên thiết bị" : "Lưu món này"}
              </Button>
              {surprise && (
                <Button onClick={onShuffle} variant="soft" color="gray" size="3">
                  <Shuffle size={17} />
                  Thử món khác
                </Button>
              )}
              <Dialog.Close>
                <Button asChild variant="outline" color="gray" size="3">
                  <Link href={`/products/${product.slug}`}>Xem chi tiết</Link>
                </Button>
              </Dialog.Close>
            </div>
          </div>
        </div>
      </Dialog.Content>
    </Dialog.Root>
  );
}
