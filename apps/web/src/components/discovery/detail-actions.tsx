"use client";

import { Button, Tooltip } from "@radix-ui/themes";
import { Heart, Link2 } from "lucide-react";
import type { Product } from "@vshop/contracts";
import { useDiscovery } from "@/components/providers";

export function DetailActions({ product }: { product: Product }) {
  const { saved, toggleSaved, notify } = useDiscovery();
  const isSaved = saved.some((p) => p.id === product.id);
  async function copyLink() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      notify("Đã sao chép liên kết sản phẩm.");
    } catch {
      notify("Chưa thể sao chép. Bạn có thể lấy liên kết từ thanh địa chỉ.");
    }
  }
  return (
    <div className="detail-actions">
      <Button
        size="4"
        onClick={() => toggleSaved(product)}
        variant={isSaved ? "soft" : "solid"}
        aria-pressed={isSaved}
      >
        <Heart size={19} fill={isSaved ? "currentColor" : "none"} />
        {isSaved ? "Đã lưu món này" : "Lưu vào gu của bạn"}
      </Button>
      <Tooltip content="Sao chép liên kết sản phẩm">
        <Button size="4" variant="outline" color="gray" onClick={copyLink}>
          <Link2 size={19} /> Chia sẻ
        </Button>
      </Tooltip>
    </div>
  );
}
