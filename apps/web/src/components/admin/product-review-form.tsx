"use client";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button, Select, TextField } from "@radix-ui/themes";
import { bff } from "@/lib/client-api";

export function ProductReviewForm({
  productId,
  version,
}: {
  productId: string;
  /** The exact content version shown to the admin; a newer seller edit makes the decision fail. */
  version: number;
}) {
  const router = useRouter();
  const [decision, setDecision] = useState("APPROVED");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const reason = String(new FormData(event.currentTarget).get("reason") ?? "").trim();
    setError("");
    if (reason.length < 5) return setError("Cần nêu lý do ít nhất 5 ký tự.");
    setPending(true);
    const result = await bff("POST", `admin/products/${productId}/review`, {
      decision,
      reason,
      expectedVersion: version,
    });
    setPending(false);
    // On a version/state conflict the queue is stale, so reload it and show the message.
    if (!result.ok) {
      setError(result.message);
      if (result.status === 409) router.refresh();
      return;
    }
    router.refresh();
  }

  return (
    <form className="review-form" onSubmit={submit} noValidate>
      <div className="review-fields">
        <div>
          <label htmlFor={`product-decision-${productId}`}>Quyết định</label>
          <Select.Root value={decision} onValueChange={setDecision}>
            <Select.Trigger id={`product-decision-${productId}`} aria-label="Quyết định" />
            <Select.Content>
              <Select.Item value="APPROVED">Chấp thuận</Select.Item>
              <Select.Item value="REJECTED">Yêu cầu chỉnh sửa</Select.Item>
            </Select.Content>
          </Select.Root>
        </div>
        <div>
          <label htmlFor={`product-reason-${productId}`}>Lý do xử lý</label>
          <TextField.Root
            id={`product-reason-${productId}`}
            name="reason"
            maxLength={500}
            placeholder="Ghi rõ kết quả kiểm tra nội dung"
          />
        </div>
      </div>
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
      <Button size="3" type="submit" loading={pending} disabled={pending}>
        {pending ? "Đang xử lý…" : "Lưu quyết định"}
      </Button>
    </form>
  );
}
