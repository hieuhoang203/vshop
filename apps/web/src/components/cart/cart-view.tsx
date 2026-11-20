"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import type { Address, CartContent, CartLine, Quote } from "@vshop/contracts";
import { Button, Select } from "@radix-ui/themes";
import { Minus, Plus, ShoppingCart, Trash2 } from "lucide-react";
import { bff } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";

const ISSUE_TEXT: Record<string, (line: CartLine) => string> = {
  UNAVAILABLE: () => "Sản phẩm này không còn được bán. Hãy xóa khỏi giỏ.",
  OUT_OF_STOCK: () => "Hiện đã hết hàng.",
  INSUFFICIENT_STOCK: (line) => `Chỉ còn ${line.availableQuantity} sản phẩm. Hãy giảm số lượng.`,
};
const timeOnly = new Intl.DateTimeFormat("vi-VN", {
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh",
});

export function CartView({ initial, addresses }: { initial: CartContent; addresses: Address[] }) {
  const router = useRouter();
  const [cart, setCart] = useState(initial);
  const [addressId, setAddressId] = useState(
    () => (addresses.find((a) => a.isDefault) ?? addresses[0])?.id ?? "",
  );
  const [quote, setQuote] = useState<Quote | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(timer);
  }, []);

  async function run(key: string, request: () => ReturnType<typeof bff<CartContent>>) {
    if (busy) return;
    setBusy(key);
    setError("");
    const result = await request();
    setBusy(null);
    if (!result.ok) return setError(result.message);
    setCart(result.data);
    router.refresh(); // keeps the header count in step
  }

  const setQuantity = (line: CartLine, quantity: number) =>
    run(line.skuId, () => bff<CartContent>("PUT", `cart/lines/${line.skuId}`, { quantity }));
  const remove = (line: CartLine) =>
    run(line.skuId, () => bff<CartContent>("DELETE", `cart/lines/${line.skuId}`));
  const clear = () => run("clear", () => bff<CartContent>("DELETE", "cart"));

  async function createQuote() {
    if (busy) return;
    setBusy("quote");
    setError("");
    const result = await bff<Quote>("POST", "checkout/quotes", { addressId });
    setBusy(null);
    if (!result.ok) return setError(result.message);
    setQuote(result.data);
  }

  if (!cart.groups.length)
    return (
      <div className="panel empty-state">
        <ShoppingCart size={34} />
        <h2>Giỏ hàng đang trống</h2>
        <p>Hãy chọn vài món hợp gu rồi quay lại đây.</p>
        <Link href="/" className="button button-dark">
          Khám phá sản phẩm
        </Link>
      </div>
    );

  // A quote is only trustworthy while it matches the cart and has not expired.
  const quoteStale =
    quote !== null &&
    (quote.content.version !== cart.version ||
      quote.shipTo?.addressId !== addressId ||
      new Date(quote.expiresAt).getTime() <= now);
  const selectedAddress = addresses.find((a) => a.id === addressId);

  return (
    <div className="cart-layout">
      <div className="cart-groups">
        {cart.groups.map((group) => (
          <section className="panel cart-group" key={group.shopId} aria-label={group.shopName}>
            <h2>{group.shopName}</h2>
            <ul className="cart-lines">
              {group.lines.map((line) => (
                <li key={line.skuId} className={line.issue ? "cart-line has-issue" : "cart-line"}>
                  <img
                    src={`/products/${line.imageKey}.svg`}
                    alt=""
                    width={72}
                    height={72}
                    className="cart-thumb"
                  />
                  <div className="cart-line-info">
                    <Link href={`/products/${line.productSlug}`}>{line.productName}</Link>
                    {line.variantLabel && <span className="row-sub">{line.variantLabel}</span>}
                    <span className="row-sub">{formatMoney(line.unitPrice)} / sản phẩm</span>
                    {line.issue && (
                      <p role="alert" className="error-message">
                        {ISSUE_TEXT[line.issue](line)}
                      </p>
                    )}
                  </div>
                  <div
                    className="cart-qty"
                    role="group"
                    aria-label={`Số lượng ${line.productName}`}
                  >
                    <Button
                      type="button"
                      variant="soft"
                      color="gray"
                      aria-label={`Giảm số lượng ${line.productName}`}
                      disabled={busy !== null || line.quantity <= 1}
                      onClick={() => setQuantity(line, line.quantity - 1)}
                    >
                      <Minus size={15} />
                    </Button>
                    <output aria-live="polite" aria-label={`Số lượng hiện tại ${line.quantity}`}>
                      {line.quantity}
                    </output>
                    <Button
                      type="button"
                      variant="soft"
                      color="gray"
                      aria-label={`Tăng số lượng ${line.productName}`}
                      disabled={busy !== null || line.quantity >= 99}
                      onClick={() => setQuantity(line, line.quantity + 1)}
                    >
                      <Plus size={15} />
                    </Button>
                  </div>
                  <div className="cart-line-total">
                    <strong>{formatMoney(line.lineTotal)}</strong>
                    <Button
                      type="button"
                      variant="ghost"
                      color="gray"
                      aria-label={`Xóa ${line.productName} khỏi giỏ`}
                      disabled={busy !== null}
                      onClick={() => remove(line)}
                    >
                      <Trash2 size={16} /> Xóa
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
            <p className="cart-subtotal">
              Tạm tính của gian hàng: <strong>{formatMoney(group.subtotal)}</strong>
            </p>
          </section>
        ))}
        <Button
          type="button"
          variant="outline"
          color="gray"
          onClick={clear}
          disabled={busy !== null}
        >
          Xóa toàn bộ giỏ hàng
        </Button>
      </div>

      <aside className="panel cart-summary" aria-label="Tóm tắt giỏ hàng">
        <h2>Tóm tắt</h2>
        <dl>
          <div>
            <dt>Số sản phẩm</dt>
            <dd>{cart.itemCount}</dd>
          </div>
          <div className="cart-total">
            <dt>Tạm tính</dt>
            <dd>{formatMoney(cart.itemsTotal)}</dd>
          </div>
        </dl>
        <p className="form-note">
          Chưa gồm phí vận chuyển, giảm giá và thuế. Các khoản này chưa được tính. Giá có thể thay
          đổi cho tới khi bạn chốt báo giá.
        </p>
        <div className="cart-address">
          <label htmlFor="cart-address">Giao đến</label>
          {addresses.length ? (
            <Select.Root value={addressId} onValueChange={setAddressId}>
              <Select.Trigger id="cart-address" aria-label="Địa chỉ giao hàng" />
              <Select.Content>
                {addresses.map((a) => (
                  <Select.Item key={a.id} value={a.id}>
                    {a.recipientName} · {a.ward}, {a.province}
                    {a.isDefault ? " (mặc định)" : ""}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          ) : (
            <p className="form-note">
              Bạn chưa có địa chỉ. <Link href="/account/addresses">Thêm địa chỉ giao hàng</Link> để
              chốt báo giá.
            </p>
          )}
          {selectedAddress && (
            <p className="form-note">
              {selectedAddress.street}. <Link href="/account/addresses">Quản lý địa chỉ</Link>
            </p>
          )}
        </div>
        {!cart.canCheckout && (
          <p className="form-note" role="status">
            Hãy xử lý các sản phẩm có cảnh báo trước khi chốt báo giá.
          </p>
        )}
        <Button
          size="3"
          onClick={createQuote}
          loading={busy === "quote"}
          disabled={busy !== null || !cart.canCheckout || !addressId}
        >
          Chốt báo giá
        </Button>
        {error && (
          <p role="alert" className="error-message">
            {error}
          </p>
        )}
        {quote && (
          <div
            className={quoteStale ? "quote-box stale" : "quote-box"}
            role="status"
            aria-label="Báo giá"
          >
            <strong>
              {quoteStale
                ? quote.content.version !== cart.version
                  ? "Giỏ hàng đã thay đổi — báo giá cũ không còn hiệu lực"
                  : quote.shipTo?.addressId !== addressId
                    ? "Đã đổi địa chỉ — báo giá cũ không còn hiệu lực"
                    : "Báo giá đã hết hạn"
                : `Giá được giữ đến ${timeOnly.format(new Date(quote.expiresAt))}`}
            </strong>
            <span>Tổng sản phẩm trong báo giá: {formatMoney(quote.content.itemsTotal)}</span>
            {quote.shipTo && (
              <span>
                Giao đến: {quote.shipTo.ward}, {quote.shipTo.province}
              </span>
            )}
            <p className="form-note">
              Thanh toán và đặt hàng chưa mở, nên báo giá này chưa tạo đơn và chưa giữ hàng.
            </p>
          </div>
        )}
      </aside>
    </div>
  );
}
