"use client";
import { useMemo, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { Category, NewProductInput, SellerProduct } from "@vshop/contracts";
import { Button, Select, TextArea, TextField } from "@radix-ui/themes";
import { Plus, Trash2 } from "lucide-react";
import { bff } from "@/lib/client-api";
import { IMAGE_CHOICES, toCode } from "@/lib/catalog-labels";

type OptionDraft = { name: string; values: string };
type SkuDraft = { sellerSku: string; price: string };
type Combination = { key: string; label: string; selection: Record<string, string> };

const MAX_OPTIONS = 3;
const splitValues = (text: string) =>
  text
    .split(",")
    .map((v) => v.trim())
    .filter(Boolean);

/** Cartesian product of option values; one empty combination when there are no options. */
function combinations(options: { code: string; values: { code: string; label: string }[] }[]) {
  let rows: Combination[] = [{ key: "DEFAULT", label: "Mặc định", selection: {} }];
  for (const option of options) {
    rows = rows.flatMap((row) =>
      option.values.map((value) => ({
        key:
          row.key === "DEFAULT"
            ? `${option.code}=${value.code}`
            : `${row.key}|${option.code}=${value.code}`,
        label: row.key === "DEFAULT" ? value.label : `${row.label} / ${value.label}`,
        selection: { ...row.selection, [option.code]: value.code },
      })),
    );
  }
  return rows;
}

export function ProductBuilder({ shopId, categories }: { shopId: string; categories: Category[] }) {
  const router = useRouter();
  const [options, setOptions] = useState<OptionDraft[]>([]);
  const [skuDrafts, setSkuDrafts] = useState<Record<string, SkuDraft>>({});
  const [categoryId, setCategoryId] = useState(categories[0]?.id ?? "");
  const [imageKey, setImageKey] = useState<string>(IMAGE_CHOICES[0].key);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  const parsed = useMemo(
    () =>
      options.map((option) => ({
        code: toCode(option.name),
        name: option.name.trim(),
        values: splitValues(option.values).map((label) => ({ code: toCode(label), label })),
      })),
    [options],
  );
  const optionProblem = useMemo(() => {
    const codes = new Set<string>();
    for (const option of parsed) {
      if (!option.name || !option.code) return "Mỗi phân loại cần có tên (chữ hoặc số).";
      if (codes.has(option.code)) return `Phân loại “${option.name}” bị trùng.`;
      codes.add(option.code);
      if (!option.values.length) return `Phân loại “${option.name}” cần ít nhất một giá trị.`;
      if (option.values.length > 30) return `Phân loại “${option.name}” tối đa 30 giá trị.`;
      const values = new Set(option.values.map((v) => v.code));
      if (option.values.some((v) => !v.code) || values.size !== option.values.length)
        return `Giá trị của “${option.name}” bị trống hoặc trùng.`;
    }
    return "";
  }, [parsed]);
  const rows = useMemo(() => (optionProblem ? [] : combinations(parsed)), [parsed, optionProblem]);

  const setDraft = (key: string, patch: Partial<SkuDraft>) =>
    setSkuDrafts((drafts) => ({
      ...drafts,
      [key]: { ...(drafts[key] ?? { sellerSku: "", price: "" }), ...patch },
    }));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    setError("");
    if (optionProblem) return setError(optionProblem);
    if (rows.length > 100) return setError("Một sản phẩm tối đa 100 SKU. Hãy giảm số giá trị.");
    const form = new FormData(event.currentTarget);
    const skus: NewProductInput["skus"] = [];
    for (const row of rows) {
      const draft = skuDrafts[row.key] ?? { sellerSku: "", price: "" };
      const price = Number(draft.price);
      if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/.test(draft.sellerSku.trim()))
        return setError(`Mã SKU của “${row.label}” chỉ gồm chữ, số, dấu . _ - (tối đa 80 ký tự).`);
      if (!Number.isInteger(price) || price < 1 || price > 1_000_000_000)
        return setError(`Giá của “${row.label}” phải là số nguyên VND từ 1 đến 1.000.000.000.`);
      skus.push({ sellerSku: draft.sellerSku.trim(), price, optionValues: row.selection });
    }
    const body: NewProductInput = {
      name: String(form.get("name") ?? "").trim(),
      description: String(form.get("description") ?? "").trim(),
      categoryId,
      imageKey,
      options: parsed,
      skus,
    };
    if (body.name.length < 3) return setError("Tên sản phẩm cần ít nhất 3 ký tự.");
    if (body.description.length < 10) return setError("Mô tả cần ít nhất 10 ký tự.");
    setPending(true);
    const result = await bff<SellerProduct>("POST", `seller/shops/${shopId}/products`, body);
    setPending(false);
    if (!result.ok) return setError(result.message);
    router.push(`/seller/products/${result.data.id}`);
    router.refresh();
  }

  return (
    <form className="panel product-form" onSubmit={submit} noValidate>
      <h2>Thông tin sản phẩm</h2>
      <label htmlFor="product-name">Tên sản phẩm</label>
      <TextField.Root id="product-name" name="name" required maxLength={200} />
      <label htmlFor="product-description">Mô tả</label>
      <TextArea id="product-description" name="description" required maxLength={5000} rows={5} />
      <div className="form-row">
        <div>
          <label htmlFor="product-category">Danh mục</label>
          <Select.Root value={categoryId} onValueChange={setCategoryId}>
            <Select.Trigger id="product-category" aria-label="Danh mục" />
            <Select.Content>
              {categories.map((c) => (
                <Select.Item key={c.id} value={c.id}>
                  {c.name}
                </Select.Item>
              ))}
            </Select.Content>
          </Select.Root>
        </div>
        <div>
          <label htmlFor="product-image">Ảnh sản phẩm</label>
          <Select.Root value={imageKey} onValueChange={setImageKey}>
            <Select.Trigger id="product-image" aria-label="Ảnh sản phẩm" />
            <Select.Content>
              {IMAGE_CHOICES.map((i) => (
                <Select.Item key={i.key} value={i.key}>
                  {i.label}
                </Select.Item>
              ))}
            </Select.Content>
          </Select.Root>
        </div>
      </div>
      <p className="form-note">Tải ảnh riêng sẽ có sau; hiện chỉ chọn từ thư viện ảnh có sẵn.</p>

      <h2>Phân loại (tùy chọn)</h2>
      <p className="form-note">
        Ví dụ phân loại “Màu” với các giá trị “Đỏ, Xanh”. Tối đa {MAX_OPTIONS} phân loại.
      </p>
      {options.map((option, index) => (
        <div className="option-row" key={index}>
          <div>
            <label htmlFor={`option-name-${index}`}>Tên phân loại</label>
            <TextField.Root
              id={`option-name-${index}`}
              value={option.name}
              maxLength={100}
              onChange={(e) =>
                setOptions((list) =>
                  list.map((o, i) => (i === index ? { ...o, name: e.target.value } : o)),
                )
              }
            />
          </div>
          <div>
            <label htmlFor={`option-values-${index}`}>Các giá trị, cách nhau bằng dấu phẩy</label>
            <TextField.Root
              id={`option-values-${index}`}
              value={option.values}
              onChange={(e) =>
                setOptions((list) =>
                  list.map((o, i) => (i === index ? { ...o, values: e.target.value } : o)),
                )
              }
            />
          </div>
          <Button
            type="button"
            variant="soft"
            color="gray"
            aria-label={`Xóa phân loại ${option.name || index + 1}`}
            onClick={() => setOptions((list) => list.filter((_, i) => i !== index))}
          >
            <Trash2 size={16} />
          </Button>
        </div>
      ))}
      {options.length < MAX_OPTIONS && (
        <Button
          type="button"
          variant="soft"
          onClick={() => setOptions((list) => [...list, { name: "", values: "" }])}
        >
          <Plus size={16} /> Thêm phân loại
        </Button>
      )}
      {optionProblem && (
        <p role="status" className="form-note">
          {optionProblem}
        </p>
      )}

      <h2>SKU và giá</h2>
      <p className="form-note">
        Mỗi SKU là một lựa chọn có thể mua. Giá tính bằng VND, số nguyên. Số lượng tồn nhập sau khi
        tạo.
      </p>
      <div className="sku-grid">
        {rows.map((row) => (
          <div className="sku-row" key={row.key}>
            <strong>{row.label}</strong>
            <div>
              <label htmlFor={`sku-code-${row.key}`}>Mã SKU</label>
              <TextField.Root
                id={`sku-code-${row.key}`}
                value={skuDrafts[row.key]?.sellerSku ?? ""}
                maxLength={80}
                onChange={(e) => setDraft(row.key, { sellerSku: e.target.value })}
              />
            </div>
            <div>
              <label htmlFor={`sku-price-${row.key}`}>Giá (VND)</label>
              <TextField.Root
                id={`sku-price-${row.key}`}
                type="number"
                inputMode="numeric"
                min={1}
                max={1000000000}
                step={1}
                value={skuDrafts[row.key]?.price ?? ""}
                onChange={(e) => setDraft(row.key, { price: e.target.value })}
              />
            </div>
          </div>
        ))}
      </div>
      {error && (
        <div role="alert" className="error-message">
          {error}
        </div>
      )}
      <Button size="3" type="submit" loading={pending} disabled={pending}>
        {pending ? "Đang lưu…" : "Lưu sản phẩm nháp"}
      </Button>
    </form>
  );
}
