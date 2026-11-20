import type { ProductStatus } from "@vshop/contracts";

/** Only bundled fixture images exist until media upload is built (matches the backend allowlist). */
export const IMAGE_CHOICES = [
  { key: "lamp", label: "Đèn bàn" },
  { key: "tote", label: "Túi tote" },
  { key: "mug", label: "Cốc gốm" },
  { key: "headphones", label: "Tai nghe" },
  { key: "bottle", label: "Bình giữ nhiệt" },
  { key: "notebook", label: "Sổ tay" },
] as const;

export const PRODUCT_STATUS: Record<ProductStatus, { label: string; tone: string }> = {
  DRAFT: { label: "Nháp", tone: "draft" },
  PENDING_REVIEW: { label: "Chờ duyệt", tone: "submitted" },
  REJECTED: { label: "Cần chỉnh sửa", tone: "rejected" },
  ACTIVE: { label: "Đang bán", tone: "approved" },
  HIDDEN: { label: "Đã ẩn", tone: "hidden" },
};

/** Lowercase ASCII code usable as option/value code on the backend ([a-z0-9][a-z0-9-]{0,49}). */
export function toCode(label: string): string {
  return label
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 50);
}
