export type ShopStatus = "SUBMITTED" | "APPROVED" | "REJECTED";
export type Shop = {
  id: string;
  name: string;
  description: string;
  status: ShopStatus;
  rejectionReason: string | null;
  createdAt: string;
};
export type Category = {
  id: string;
  name: string;
  slug: string;
  count: number;
};
export type Product = {
  id: string;
  slug: string;
  name: string;
  description: string;
  categorySlug: string;
  categoryName: string;
  shopId: string;
  shopName: string;
  price: number;
  currency: "VND";
  stock: number;
  imageKey: string;
};
export type CatalogPage = {
  items: Product[];
  total: number;
  page: number;
  size: number;
};
export type ApiError = {
  code: string;
  message: string;
  correlationId?: string;
};

export type ShopActivityReport = {
  from: string;
  to: string;
  timezone: string;
  points: { date: string; submitted: number; approved: number; rejected: number }[];
};

export type ProductStatus = "DRAFT" | "PENDING_REVIEW" | "REJECTED" | "ACTIVE" | "HIDDEN";
export type SkuStatus = "ACTIVE" | "INACTIVE";
export type SellerOption = {
  code: string;
  name: string;
  values: { code: string; label: string }[];
};
export type SellerSku = {
  id: string;
  sellerSku: string;
  price: number;
  currency: "VND";
  status: SkuStatus;
  version: number;
  /** option code -> value code */
  optionValues: Record<string, string>;
  /** on_hand - reserved - blocked across active warehouses */
  available: number;
};
export type SellerProduct = {
  id: string;
  shopId: string;
  shopName: string;
  categoryId: string;
  slug: string;
  name: string;
  description: string;
  imageKey: string;
  status: ProductStatus;
  rejectionReason: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  options: SellerOption[];
  skus: SellerSku[];
};
export type SellerProductPage = {
  items: SellerProduct[];
  total: number;
  page: number;
  size: number;
};
export type NewProductInput = {
  name: string;
  description: string;
  categoryId: string;
  imageKey: string;
  options: { code: string; name: string; values: { code: string; label: string }[] }[];
  skus: { sellerSku: string; price: number; optionValues: Record<string, string> }[];
};
export type StockBalance = {
  skuId: string;
  warehouseId: string;
  onHand: number;
  reserved: number;
  blocked: number;
  available: number;
  updatedAt: string;
};
export type StockMovement = {
  id: string;
  warehouseId: string;
  type: string;
  deltaOnHand: number;
  deltaReserved: number;
  deltaBlocked: number;
  reason: string;
  createdAt: string;
};
