"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import type { PickupAddress, PickupAddressInput } from "@vshop/contracts";
import { Button, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

const FIELDS: { key: keyof PickupAddressInput; label: string }[] = [
  { key: "contactName", label: "Người liên hệ lấy hàng" },
  { key: "phone", label: "Số điện thoại lấy hàng" },
  { key: "street", label: "Địa chỉ lấy hàng (số nhà, đường)" },
  { key: "ward", label: "Phường/Xã" },
  { key: "district", label: "Quận/Huyện" },
  { key: "province", label: "Tỉnh/Thành phố" },
];

/** The address the carrier collects parcels from. Needed before a carrier order can be created. */
export function PickupForm({ shopId, current }: { shopId: string; current: PickupAddress | null }) {
  const router = useRouter();
  const [values, setValues] = useState<PickupAddressInput>({
    contactName: current?.contactName ?? "",
    phone: current?.phone ?? "",
    street: current?.street ?? "",
    ward: current?.ward ?? "",
    district: current?.district ?? "",
    province: current?.province ?? "",
  });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    setMessage("");
    const result = await bff("PUT", `seller/shops/${shopId}/pickup-address`, values);
    setBusy(false);
    if (!result.ok) return setError(result.message);
    setMessage("Đã lưu địa chỉ lấy hàng.");
    router.refresh();
  }

  return (
    <form className="panel" onSubmit={save} noValidate>
      <h2>Địa chỉ lấy hàng</h2>
      <p className="form-note">
        {current
          ? "Đơn vị vận chuyển sẽ lấy hàng tại địa chỉ này."
          : "Chưa có địa chỉ lấy hàng nên chưa tạo được vận đơn với đơn vị vận chuyển."}
      </p>
      {FIELDS.map((f) => (
        <div key={f.key}>
          <label htmlFor={`pickup-${f.key}`}>{f.label}</label>
          <TextField.Root
            id={`pickup-${f.key}`}
            value={values[f.key]}
            onChange={(e) => setValues({ ...values, [f.key]: e.target.value })}
          />
        </div>
      ))}
      <Button type="submit" loading={busy} disabled={busy}>
        Lưu địa chỉ lấy hàng
      </Button>
      {message && (
        <p role="status" className="form-note">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </form>
  );
}
