"use client";

import { useRef, useState } from "react";
import { Button, SegmentedControl } from "@radix-ui/themes";
import { Shuffle } from "lucide-react";
import Link from "next/link";
import type { Product } from "@vshop/contracts";
import { QuickViewDialog } from "./quick-view";

const moods = {
  focus: {
    label: "Tập trung",
    title: "Bật nhịp riêng.",
    image: "headphones",
    category: "goc-lam-viec",
  },
  chill: { label: "Sống chill", title: "Chậm lại một chút.", image: "mug", category: "nha-cua" },
  move: { label: "Đi muôn nơi", title: "Mang gu đi cùng.", image: "tote", category: "phu-kien" },
};
export function DiscoveryTools({ products }: { products: Product[] }) {
  const [mood, setMood] = useState<keyof typeof moods>("focus");
  const [surprise, setSurprise] = useState<Product | null>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const item = moods[mood];
  function pick() {
    const available = products.filter((p) => p.stock > 0);
    const pool = available.length ? available : products;
    const alternatives = pool.filter((p) => p.id !== surprise?.id);
    const choices = alternatives.length ? alternatives : pool;
    setSurprise(choices[Math.floor(Math.random() * choices.length)] ?? null);
  }
  return (
    <section className="discovery-tools" aria-label="Khám phá thêm">
      <div className="discovery-tool-art">
        <img src={`/products/${item.image}.svg`} alt={item.title} width={88} height={88} />
      </div>
      <div className="discovery-tool-copy">
        <h2>{item.title}</h2>
        <SegmentedControl.Root
          size="1"
          value={mood}
          onValueChange={(value) => setMood(value as keyof typeof moods)}
          aria-label="Chọn cảm hứng"
        >
          {Object.entries(moods).map(([value, option]) => (
            <SegmentedControl.Item key={value} value={value}>
              {option.label}
            </SegmentedControl.Item>
          ))}
        </SegmentedControl.Root>
        <Link href={`/?category=${item.category}#san-pham`}>
          Khám phá {item.label.toLowerCase()}
        </Link>
      </div>
      <Button ref={trigger} variant="soft" size="3" onClick={pick} disabled={!products.length}>
        <Shuffle size={17} />
        Chọn giùm mình
      </Button>
      <QuickViewDialog
        product={surprise}
        open={!!surprise}
        onOpenChange={(open) => {
          if (!open) setSurprise(null);
        }}
        surprise
        onShuffle={pick}
        triggerRef={trigger}
      />
    </section>
  );
}
