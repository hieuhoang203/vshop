export const CASE_STATUS: Record<string, string> = {
  REQUESTED: "Chờ người bán phản hồi",
  SELLER_OFFERED: "Người bán đã đề xuất hoàn tiền",
  SELLER_REJECTED: "Người bán từ chối",
  AWAITING_RETURN: "Chờ người mua gửi trả hàng",
  IN_RETURN: "Hàng đang được trả về",
  REVIEWING: "Đang chờ quản trị viên quyết định",
  REFUND_APPROVED: "Đã duyệt hoàn tiền",
  REJECTED: "Bị từ chối",
  CANCELED: "Đã hủy",
};

export const REASONS: Record<string, string> = {
  NOT_RECEIVED: "Chưa nhận được hàng",
  MISSING_ITEMS: "Thiếu hàng",
  WRONG_ITEM: "Người bán gửi sai hàng",
  DAMAGED: "Hàng bị hư hỏng, bể vỡ",
  DEFECTIVE: "Hàng lỗi, không hoạt động",
  NOT_AS_DESCRIBED: "Khác với mô tả",
  FAKE: "Hàng giả, nhái",
  CHANGED_MIND: "Đổi ý (hàng còn nguyên)",
};

export const ACTOR: Record<string, string> = {
  BUYER: "Người mua",
  SELLER: "Người bán",
  ADMIN: "Quản trị viên",
  SYSTEM: "Hệ thống",
};
