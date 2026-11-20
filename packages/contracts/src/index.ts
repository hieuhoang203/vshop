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

export type PublicSku = {
  id: string;
  /** null for single-SKU products */
  variantLabel: string | null;
  price: number;
  available: number;
};
export type CartLineIssue = "UNAVAILABLE" | "OUT_OF_STOCK" | "INSUFFICIENT_STOCK";
export type CartLine = {
  skuId: string;
  productId: string;
  productSlug: string;
  productName: string;
  imageKey: string;
  variantLabel: string | null;
  sellerSku: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  issue: CartLineIssue | null;
  availableQuantity: number;
};
export type CartGroup = {
  shopId: string;
  shopName: string;
  /** Sum of lines without an issue. */
  subtotal: number;
  lines: CartLine[];
};
/** Used for both the live cart and a stored quote's content. Amounts are integer VND. */
export type CartContent = {
  version: number;
  itemCount: number;
  currency: "VND";
  groups: CartGroup[];
  itemsTotal: number;
  canCheckout: boolean;
  /** Price components that are not calculated yet: shipping, discounts, tax. */
  notCalculated: string[];
};
export type Quote = {
  id: string;
  createdAt: string;
  expiresAt: string;
  expired: boolean;
  /** false once expired or once the cart changed after the quote was calculated */
  current: boolean;
  content: CartContent;
};
