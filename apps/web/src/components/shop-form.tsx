"use client";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { Shop } from "@vshop/contracts";
import { Button, TextField, TextArea } from "@radix-ui/themes";
export function ShopForm({ shop }: { shop?: Shop }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (pending) return;
    setPending(true);
    setError("");
    const data = new FormData(e.currentTarget);
    try {
      const result = await fetch(
        shop ? `/api/bff/seller/shops/${shop.id}` : "/api/bff/seller/shops",
        {
          method: shop ? "PUT" : "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            name: data.get("name"),
            description: data.get("description"),
          }),
        },
      );
      if (!result.ok) {
        const body = await result.json();
        throw new Error(body.message ?? "Không thể gửi hồ sơ.");
      }
      router.refresh();
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Kết nối bị gián đoạn. Vui lòng kiểm tra hồ sơ trước khi gửi lại.",
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <form className="panel shop-form" onSubmit={submit}>
      <h2>{shop ? "Bổ sung hồ sơ gian hàng" : "Thông tin gian hàng"}</h2>
      <p>Giới thiệu rõ gian hàng và nhóm sản phẩm bạn dự định kinh doanh.</p>
      <label htmlFor="shop-name">Tên gian hàng</label>
      <TextField.Root
        id="shop-name"
        name="name"
        required
        minLength={3}
        maxLength={100}
        defaultValue={shop?.name}
        placeholder="Ví dụ: Mộc Studio"
      />
      <label htmlFor="shop-description">Giới thiệu gian hàng</label>
      <TextArea
        id="shop-description"
        name="description"
        required
        minLength={20}
        maxLength={2000}
        rows={5}
        defaultValue={shop?.description}
        placeholder="Bạn bán sản phẩm gì? Điều gì làm nên nét riêng của gian hàng?"
      />
      <p className="form-note">
        Tối thiểu 20 ký tự. Hồ sơ cần được duyệt trước khi gian hàng có thể hoạt động.
      </p>
      {error && (
        <div role="alert" className="error-message">
          {error}
        </div>
      )}
      <Button size="3" loading={pending} disabled={pending} type="submit">
        {pending ? "Đang gửi hồ sơ…" : shop ? "Gửi lại để xét duyệt" : "Gửi hồ sơ gian hàng"}
      </Button>
    </form>
  );
}
