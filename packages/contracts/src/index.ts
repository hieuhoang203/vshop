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
