"use client";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { Address, AddressInput } from "@vshop/contracts";
import { Badge, Button, Dialog, TextField } from "@radix-ui/themes";
import { MapPin, Pencil, Plus, Star, Trash2 } from "lucide-react";
import { bff } from "@/lib/client-api";

const MAX_ADDRESSES = 10;
const MOBILE = /^0[35789]\d{8}$/;

/** Mirrors the server's phone normalisation; the server remains the authority. */
function normalisePhone(raw: string) {
  let digits = raw.replace(/[\s.\-()]/g, "");
  if (digits.startsWith("+84")) digits = "0" + digits.slice(3);
  else if (digits.startsWith("84") && digits.length === 11) digits = "0" + digits.slice(2);
  return digits;
}

function AddressForm({
  address,
  first,
  onDone,
}: {
  address?: Address;
  /** true when this is the buyer's first address, which becomes the default anyway */
  first: boolean;
  onDone: () => void;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? "").trim();
    const input: AddressInput = {
      label: text("label") || undefined,
      recipientName: text("recipientName"),
      phone: text("phone"),
      street: text("street"),
      ward: text("ward"),
      province: text("province"),
      makeDefault: form.get("makeDefault") === "on",
    };
    setError("");
    if (input.recipientName.length < 2) return setError("Hãy nhập tên người nhận.");
    if (!MOBILE.test(normalisePhone(input.phone)))
      return setError("Số điện thoại di động chưa hợp lệ (ví dụ 0912 345 678).");
    if (input.street.length < 5) return setError("Địa chỉ cụ thể cần ít nhất 5 ký tự.");
    if (!input.ward) return setError("Hãy nhập phường/xã.");
    if (!input.province) return setError("Hãy nhập tỉnh/thành phố.");
    setPending(true);
    const result = address
      ? await bff("PUT", `me/addresses/${address.id}`, {
          ...input,
          expectedVersion: address.version,
        })
      : await bff("POST", "me/addresses", input);
    setPending(false);
    if (!result.ok) {
      if (result.status === 409) router.refresh();
      return setError(result.message);
    }
    router.refresh();
    onDone();
  }

  return (
    <form className="address-form" onSubmit={submit} noValidate>
      <div className="form-row">
        <div>
          <label htmlFor="addr-name">Tên người nhận</label>
          <TextField.Root
            id="addr-name"
            name="recipientName"
            defaultValue={address?.recipientName}
            maxLength={100}
            autoComplete="name"
          />
        </div>
        <div>
          <label htmlFor="addr-phone">Số điện thoại</label>
          <TextField.Root
            id="addr-phone"
            name="phone"
            type="tel"
            inputMode="tel"
            defaultValue={address?.phone}
            maxLength={20}
            autoComplete="tel"
          />
        </div>
      </div>
      <label htmlFor="addr-street">Số nhà, tên đường</label>
      <TextField.Root
        id="addr-street"
        name="street"
        defaultValue={address?.street}
        maxLength={200}
        autoComplete="address-line1"
      />
      <div className="form-row">
        <div>
          <label htmlFor="addr-ward">Phường/Xã</label>
          <TextField.Root id="addr-ward" name="ward" defaultValue={address?.ward} maxLength={100} />
        </div>
        <div>
          <label htmlFor="addr-province">Tỉnh/Thành phố</label>
          <TextField.Root
            id="addr-province"
            name="province"
            defaultValue={address?.province}
            maxLength={100}
            autoComplete="address-level1"
          />
        </div>
      </div>
      <label htmlFor="addr-label">Nhãn (không bắt buộc)</label>
      <TextField.Root
        id="addr-label"
        name="label"
        defaultValue={address?.label ?? ""}
        maxLength={30}
        placeholder="Nhà riêng, Văn phòng…"
      />
      {!first && !address?.isDefault && (
        <label className="inline-check">
          <input type="checkbox" name="makeDefault" /> Đặt làm địa chỉ mặc định
        </label>
      )}
      <p className="form-note">
        Số điện thoại và địa chỉ cụ thể được mã hóa khi lưu. Chưa kiểm tra danh mục hành chính hay
        vùng giao hàng.
      </p>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
      <div className="dialog-actions">
        <Dialog.Close>
          <Button type="button" variant="soft" color="gray">
            Hủy
          </Button>
        </Dialog.Close>
        <Button type="submit" loading={pending} disabled={pending}>
          {address ? "Lưu thay đổi" : "Lưu địa chỉ"}
        </Button>
      </div>
    </form>
  );
}

function AddressDialog({ address, first }: { address?: Address; first: boolean }) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger>
        {address ? (
          <Button
            type="button"
            variant="soft"
            size="2"
            aria-label={`Sửa địa chỉ ${address.recipientName}`}
          >
            <Pencil size={15} /> Sửa
          </Button>
        ) : (
          <Button type="button" size="3">
            <Plus size={17} /> Thêm địa chỉ
          </Button>
        )}
      </Dialog.Trigger>
      <Dialog.Content maxWidth="560px">
        <Dialog.Title>{address ? "Sửa địa chỉ" : "Thêm địa chỉ giao hàng"}</Dialog.Title>
        <Dialog.Description size="2" color="gray">
          Chỉ bạn xem được thông tin này.
        </Dialog.Description>
        <AddressForm address={address} first={first} onDone={() => setOpen(false)} />
      </Dialog.Content>
    </Dialog.Root>
  );
}

function AddressCard({ address }: { address: Address }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function run(request: () => ReturnType<typeof bff>) {
    if (busy) return;
    setBusy(true);
    setError("");
    const result = await request();
    setBusy(false);
    if (!result.ok) {
      // 404/409 mean this card is out of date (changed elsewhere): reload the list.
      if (result.status === 404 || result.status === 409) router.refresh();
      return setError(result.message);
    }
    router.refresh();
  }

  return (
    <li className={address.isDefault ? "panel address-card is-default" : "panel address-card"}>
      <div className="address-card-head">
        <strong>{address.recipientName}</strong>
        {address.label && <Badge color="gray">{address.label}</Badge>}
        {address.isDefault && (
          <Badge color="iris">
            <Star size={12} /> Mặc định
          </Badge>
        )}
      </div>
      <p className="address-lines">
        {address.phone}
        <br />
        {address.street}
        <br />
        {address.ward}, {address.province}
      </p>
      <div className="address-actions">
        <AddressDialog address={address} first={false} />
        {!address.isDefault && (
          <Button
            type="button"
            variant="soft"
            size="2"
            disabled={busy}
            onClick={() => run(() => bff("PUT", `me/addresses/${address.id}/default`))}
          >
            Đặt mặc định
          </Button>
        )}
        <Dialog.Root>
          <Dialog.Trigger>
            <Button
              type="button"
              variant="soft"
              color="red"
              size="2"
              disabled={busy}
              aria-label={`Xóa địa chỉ ${address.recipientName}`}
            >
              <Trash2 size={15} /> Xóa
            </Button>
          </Dialog.Trigger>
          <Dialog.Content maxWidth="420px">
            <Dialog.Title>Xóa địa chỉ này?</Dialog.Title>
            <Dialog.Description size="2" color="gray">
              {address.street}, {address.ward}, {address.province}. Báo giá đang dùng địa chỉ này sẽ
              không còn hiệu lực.
            </Dialog.Description>
            <div className="dialog-actions">
              <Dialog.Close>
                <Button type="button" variant="soft" color="gray">
                  Giữ lại
                </Button>
              </Dialog.Close>
              <Dialog.Close>
                <Button
                  type="button"
                  color="red"
                  onClick={() => run(() => bff("DELETE", `me/addresses/${address.id}`))}
                >
                  Xóa địa chỉ
                </Button>
              </Dialog.Close>
            </div>
          </Dialog.Content>
        </Dialog.Root>
      </div>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </li>
  );
}

export function AddressBook({ addresses }: { addresses: Address[] }) {
  return (
    <div>
      <div className="list-toolbar">
        <span>
          {addresses.length}/{MAX_ADDRESSES} địa chỉ
        </span>
        {addresses.length < MAX_ADDRESSES ? (
          <AddressDialog first={addresses.length === 0} />
        ) : (
          <span className="form-note">Đã đủ {MAX_ADDRESSES} địa chỉ. Hãy xóa bớt để thêm.</span>
        )}
      </div>
      {addresses.length ? (
        <ul className="address-list">
          {addresses.map((address) => (
            <AddressCard key={address.id} address={address} />
          ))}
        </ul>
      ) : (
        <div className="panel empty-state">
          <MapPin size={34} />
          <h2>Chưa có địa chỉ giao hàng</h2>
          <p>Thêm địa chỉ để chốt báo giá và, sau này, đặt hàng.</p>
        </div>
      )}
    </div>
  );
}
