"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type { SellerOption, SellerProduct, SellerSku } from "@vshop/contracts";
import { Button, Select, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";
import { formatMoney } from "@/lib/format";
import { StockDialog } from "./stock-dialog";

function describe(sku: SellerSku, options: SellerOption[]) {
  if (!options.length) return "Mặc định";
  return options
    .map((option) => {
      const code = sku.optionValues[option.code];
      return option.values.find((v) => v.code === code)?.label ?? code;
    })
    .join(" / ");
}

function SkuRow({ product, sku }: { product: SellerProduct; sku: SellerSku }) {
  const router = useRouter();
  const [price, setPrice] = useState(String(sku.price));
  const [active, setActive] = useState(sku.status === "ACTIVE");
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
  const dirty = Number(price) !== sku.price || active !== (sku.status === "ACTIVE");

  async function save() {
    const value = Number(price);
    setMessage(null);
    if (!Number.isInteger(value) || value < 1 || value > 1_000_000_000)
      return setMessage({ text: "Giá phải là số nguyên VND từ 1 đến 1.000.000.000.", error: true });
    setPending(true);
    const result = await bff(
      "PUT",
      `seller/shops/${product.shopId}/products/${product.id}/skus/${sku.id}`,
      { price: value, status: active ? "ACTIVE" : "INACTIVE", expectedVersion: sku.version },
    );
    setPending(false);
    if (!result.ok) return setMessage({ text: result.message, error: true });
    setMessage({ text: "Đã lưu.", error: false });
    router.refresh();
  }

  return (
    <tr>
      <th scope="row">
        {sku.sellerSku}
        <span className="row-sub">{describe(sku, product.options)}</span>
      </th>
      <td data-label="Giá (VND)">
        <TextField.Root
          aria-label={`Giá ${sku.sellerSku}`}
          type="number"
          inputMode="numeric"
          min={1}
          max={1000000000}
          step={1}
          value={price}
          onChange={(e) => setPrice(e.target.value)}
        />
        <span className="row-sub">{formatMoney(Number(price) || 0)}</span>
      </td>
      <td data-label="Trạng thái">
        <label className="inline-check">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Đang bán
        </label>
      </td>
      <td className="num" data-label="Có thể bán">
        {sku.available}
      </td>
      <td className="row-actions">
        <Button
          type="button"
          size="2"
          onClick={save}
          loading={pending}
          disabled={!dirty || pending}
        >
          Lưu
        </Button>
        <StockDialog shopId={product.shopId} skuId={sku.id} sellerSku={sku.sellerSku} />
        {message && (
          <span
            role={message.error ? "alert" : "status"}
            className={message.error ? "error-message" : "form-note"}
          >
            {message.text}
          </span>
        )}
      </td>
    </tr>
  );
}

function AddSkuForm({ product }: { product: SellerProduct }) {
  const router = useRouter();
  const [selection, setSelection] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const sellerSku = String(form.get("sellerSku") ?? "").trim();
    const price = Number(form.get("price"));
    setError("");
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(sellerSku))
      return setError("Mã SKU chỉ gồm chữ, số, dấu . _ - (tối đa 80 ký tự).");
    if (!Number.isInteger(price) || price < 1 || price > 1_000_000_000)
      return setError("Giá phải là số nguyên VND từ 1 đến 1.000.000.000.");
    if (product.options.some((o) => !selection[o.code]))
      return setError("Hãy chọn một giá trị cho mỗi phân loại.");
    setPending(true);
    const result = await bff("POST", `seller/shops/${product.shopId}/products/${product.id}/skus`, {
      sellerSku,
      price,
      optionValues: selection,
    });
    setPending(false);
    if (!result.ok) return setError(result.message);
    (event.target as HTMLFormElement).reset();
    setSelection({});
    router.refresh();
  }

  return (
    <form className="add-sku" onSubmit={submit} noValidate>
      <h3>Thêm SKU</h3>
      <div className="form-row">
        {product.options.map((option) => (
          <div key={option.code}>
            <label htmlFor={`add-${option.code}`}>{option.name}</label>
            <Select.Root
              value={selection[option.code] ?? ""}
              onValueChange={(value) => setSelection((s) => ({ ...s, [option.code]: value }))}
            >
              <Select.Trigger
                id={`add-${option.code}`}
                aria-label={option.name}
                placeholder="Chọn"
              />
              <Select.Content>
                {option.values.map((v) => (
                  <Select.Item key={v.code} value={v.code}>
                    {v.label}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </div>
        ))}
        <div>
          <label htmlFor="add-sku-code">Mã SKU</label>
          <TextField.Root id="add-sku-code" name="sellerSku" maxLength={80} />
        </div>
        <div>
          <label htmlFor="add-sku-price">Giá (VND)</label>
          <TextField.Root
            id="add-sku-price"
            name="price"
            type="number"
            inputMode="numeric"
            min={1}
            max={1000000000}
            step={1}
          />
        </div>
      </div>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
      <Button type="submit" variant="soft" loading={pending} disabled={pending}>
        Thêm SKU
      </Button>
    </form>
  );
}

export function SkuManager({ product }: { product: SellerProduct }) {
  return (
    <section className="panel table-panel" aria-labelledby="sku-heading">
      <h2 id="sku-heading">SKU, giá và tồn kho</h2>
      <p className="form-note">
        Đổi giá chỉ áp dụng cho lần mua tiếp theo. Chỉ có thể ngừng bán SKU cuối cùng sau khi ẩn sản
        phẩm.
      </p>
      <table className="data-table stack-mobile">
        <caption className="sr-only">SKU của sản phẩm {product.name}</caption>
        <thead>
          <tr>
            <th scope="col">SKU</th>
            <th scope="col">Giá (VND)</th>
            <th scope="col">Trạng thái</th>
            <th scope="col" className="num">
              Có thể bán
            </th>
            <th scope="col">
              <span className="sr-only">Thao tác</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {product.skus.map((sku) => (
            <SkuRow key={sku.id} product={product} sku={sku} />
          ))}
        </tbody>
      </table>
      {product.skus.length < 100 && <AddSkuForm product={product} />}
    </section>
  );
}
