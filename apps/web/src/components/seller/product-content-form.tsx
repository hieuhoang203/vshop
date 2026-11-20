"use client";
import type { UpdateProductInput } from "@vshop/contracts";
import { useState, useTransition, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { Category, SellerProduct } from "@vshop/contracts";
import { Button, Select, TextArea, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";
import { IMAGE_CHOICES } from "@/lib/catalog-labels";

export function ProductContentForm({
  product,
  categories,
}: {
  product: SellerProduct;
  categories: Category[];
}) {
  const router = useRouter();
  const [categoryId, setCategoryId] = useState(product.categoryId);
  const [imageKey, setImageKey] = useState(product.imageKey);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [refreshing, startRefresh] = useTransition();
  const live = product.status === "ACTIVE" || product.status === "HIDDEN";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const name = String(form.get("name") ?? "").trim();
    const description = String(form.get("description") ?? "").trim();
    setError("");
    setSaved(false);
    if (name.length < 3) return setError("Tên sản phẩm cần ít nhất 3 ký tự.");
    if (description.length < 10) return setError("Mô tả cần ít nhất 10 ký tự.");
    setPending(true);
    const result = await bff("PUT", `seller/shops/${product.shopId}/products/${product.id}`, {
      name,
      description,
      categoryId,
      imageKey,
      expectedVersion: product.version,
    } satisfies UpdateProductInput);
    setPending(false);
    if (!result.ok) return setError(result.message);
    setSaved(true);
    startRefresh(() => router.refresh());
  }

  return (
    <form className="panel product-form" onSubmit={submit} noValidate>
      <h2>Thông tin sản phẩm</h2>
      {live && (
        <div className="notice">
          <p>
            Sửa tên, mô tả, danh mục hoặc ảnh sẽ đưa sản phẩm về trạng thái chờ duyệt và tạm ngưng
            bán cho tới khi được duyệt lại. Đổi giá và trạng thái SKU không cần duyệt lại.
          </p>
        </div>
      )}
      <label htmlFor="edit-name">Tên sản phẩm</label>
      <TextField.Root id="edit-name" name="name" defaultValue={product.name} maxLength={200} />
      <label htmlFor="edit-description">Mô tả</label>
      <TextArea
        id="edit-description"
        name="description"
        defaultValue={product.description}
        maxLength={5000}
        rows={5}
      />
      <div className="form-row">
        <div>
          <label htmlFor="edit-category">Danh mục</label>
          <Select.Root value={categoryId} onValueChange={setCategoryId}>
            <Select.Trigger id="edit-category" aria-label="Danh mục" />
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
          <label htmlFor="edit-image">Ảnh sản phẩm</label>
          <Select.Root value={imageKey} onValueChange={setImageKey}>
            <Select.Trigger id="edit-image" aria-label="Ảnh sản phẩm" />
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
      {error && (
        <div role="alert" className="error-message">
          {error}
        </div>
      )}
      {saved && !refreshing && (
        <p role="status" className="form-note">
          Đã lưu thay đổi.
        </p>
      )}
      <Button size="3" type="submit" loading={pending} disabled={pending || refreshing}>
        {pending ? "Đang lưu…" : "Lưu thông tin"}
      </Button>
    </form>
  );
}
