"use client";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Button, TextField, Select } from "@radix-ui/themes";
export function ReviewForm({ shopId }: { shopId: string }) {
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
      const result = await fetch(`/api/bff/admin/shops/${shopId}/review`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          decision: data.get("decision"),
          reason: data.get("reason"),
        }),
      });
      if (!result.ok) {
        const body = await result.json();
        throw new Error(body.message ?? "Không thể xử lý hồ sơ.");
      }
      router.refresh();
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Không thể kết nối. Vui lòng tải lại trạng thái hồ sơ.",
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <form className="review-form" onSubmit={submit}>
      <div className="review-fields">
        <div>
          <label htmlFor={`decision-${shopId}`}>Quyết định</label>
          <Select.Root name="decision" defaultValue="APPROVED">
            <Select.Trigger id={`decision-${shopId}`} aria-label="Quyết định" />
            <Select.Content>
              <Select.Item value="APPROVED">Chấp thuận</Select.Item>
              <Select.Item value="REJECTED">Yêu cầu bổ sung</Select.Item>
            </Select.Content>
          </Select.Root>
        </div>
        <div>
          <label htmlFor={`reason-${shopId}`}>Lý do xử lý</label>
          <TextField.Root
            id={`reason-${shopId}`}
            name="reason"
            required
            minLength={5}
            maxLength={500}
            placeholder="Ghi rõ kết quả kiểm tra hồ sơ"
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
