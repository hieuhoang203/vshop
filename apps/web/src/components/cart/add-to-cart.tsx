"use client";
import type { CartLineInput } from "@vshop/contracts";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { CartContent, PublicSku } from "@vshop/contracts";
import { Button, Select, TextField } from "@radix-ui/themes";
import { ShoppingCart } from "lucide-react";
import { bff } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";

export function AddToCart({
  slug,
  skus,
  canShop,
}: {
  slug: string;
  skus: PublicSku[];
  /** false when signed out or the account cannot shop */
  canShop: boolean;
}) {
  const router = useRouter();
  const [skuId, setSkuId] = useState(skus[0]?.id ?? "");
  const [quantity, setQuantity] = useState("1");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [added, setAdded] = useState("");
  const selected = skus.find((s) => s.id === skuId);

  if (!skus.length)
    return (
      <p className="form-note" role="status">
        Sản phẩm hiện chưa có lựa chọn nào để mua.
      </p>
    );
  if (!canShop)
    return (
      <Button asChild size="4">
        <Link href={`/api/auth/login?returnTo=/products/${slug}`}>
          <ShoppingCart size={19} /> Đăng nhập để thêm vào giỏ
        </Link>
      </Button>
    );

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending || !selected) return;
    const count = Number(quantity);
    setError("");
    setAdded("");
    if (!Number.isInteger(count) || count < 1 || count > 99)
      return setError("Số lượng phải từ 1 đến 99.");
    setPending(true);
    const result = await bff<CartContent>("POST", "cart/lines", {
      skuId,
      quantity: count,
    } satisfies CartLineInput);
    setPending(false);
    if (!result.ok) return setError(result.message);
    setAdded(`Đã thêm vào giỏ. Giỏ hiện có ${result.data.itemCount} sản phẩm.`);
    router.refresh();
  }

  return (
    <form className="add-to-cart" onSubmit={submit} noValidate>
      {skus.length > 1 && (
        <div>
          <label htmlFor="sku-choice">Lựa chọn</label>
          <Select.Root value={skuId} onValueChange={setSkuId}>
            <Select.Trigger id="sku-choice" aria-label="Lựa chọn sản phẩm" />
            <Select.Content>
              {skus.map((sku) => (
                <Select.Item key={sku.id} value={sku.id}>
                  {sku.variantLabel ?? "Mặc định"} · {formatMoney(sku.price)}
                  {sku.available <= 0 ? " · hết hàng" : ""}
                </Select.Item>
              ))}
            </Select.Content>
          </Select.Root>
        </div>
      )}
      <div className="add-to-cart-row">
        <div>
          <label htmlFor="cart-quantity">Số lượng</label>
          <TextField.Root
            id="cart-quantity"
            type="number"
            inputMode="numeric"
            min={1}
            max={99}
            step={1}
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </div>
        <Button
          size="4"
          type="submit"
          loading={pending}
          disabled={pending || (selected?.available ?? 0) <= 0}
        >
          <ShoppingCart size={19} />
          {(selected?.available ?? 0) <= 0 ? "Hết hàng" : "Thêm vào giỏ"}
        </Button>
      </div>
      {selected && selected.available > 0 && (
        <p className="form-note">
          {selected.available <= 5 ? `Chỉ còn ${selected.available} sản phẩm. ` : ""}
          Thêm vào giỏ không giữ hàng; hàng chỉ được giữ khi đặt mua.
        </p>
      )}
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
      {added && (
        <p role="status" className="form-note cart-added">
          {added} <Link href="/cart">Xem giỏ hàng</Link>
        </p>
      )}
    </form>
  );
}
