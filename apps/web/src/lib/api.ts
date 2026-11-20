import "server-only";
import type {
  Address,
  CartContent,
  CatalogPage,
  CheckoutGroup,
  Order,
  OrderPage,
  OverdueOrders,
  SellerOrder,
  SellerOrderPage,
  Policy,
  PublicSku,
  Category,
  Product,
  SellerProduct,
  SellerProductPage,
  Shop,
  ShopActivityReport,
} from "@vshop/contracts";
import { getSession } from "./auth";
export const apiBase = () => process.env.API_BASE_URL ?? "http://localhost:8081";
export async function api<T>(path: string, authenticated = false): Promise<T> {
  const session = authenticated ? await getSession() : null;
  const response = await fetch(`${apiBase()}/api/v1${path}`, {
    headers: session ? { Authorization: `Bearer ${session.accessToken}` } : {},
    cache: "no-store",
    signal: AbortSignal.timeout(6000),
  });
  if (!response.ok) throw new Error(`API_${response.status}`);
  return response.json() as Promise<T>;
}
export const getCatalog = (q = "", category = "", options: Record<string, string> = {}) =>
  api<CatalogPage>(`/catalog/products?${new URLSearchParams({ q, category, ...options })}`);
export const getCategories = () => api<Category[]>("/catalog/categories");
export const getProduct = (slug: string) =>
  api<Product>(`/catalog/products/${encodeURIComponent(slug)}`);
export const getMyShops = () => api<Shop[]>("/seller/shops", true);
export const getShopQueue = () => api<Shop[]>("/admin/shops", true);
export const getShopActivity = (days = 30) =>
  api<ShopActivityReport>(`/admin/analytics/shop-activity?days=${days}`, true);
export const money = (amount: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(amount);

export const getSellerProducts = (shopId: string, page = 0, size = 20) =>
  api<SellerProductPage>(`/seller/shops/${shopId}/products?page=${page}&size=${size}`, true);
export const getSellerProduct = (shopId: string, productId: string) =>
  api<SellerProduct>(`/seller/shops/${shopId}/products/${productId}`, true);
export const getReviewQueue = (page = 0, size = 20) =>
  api<SellerProductPage>(`/admin/products/review-queue?page=${page}&size=${size}`, true);

export const getCart = () => api<CartContent>("/cart", true);
export const getProductSkus = (slug: string) =>
  api<PublicSku[]>(`/catalog/products/${encodeURIComponent(slug)}/skus`);

export const getCheckoutGroup = (id: string) =>
  api<CheckoutGroup>(`/checkout/groups/${encodeURIComponent(id)}`, true);
export const getOrders = (page = 0) => api<OrderPage>(`/orders?page=${page}&size=20`, true);
export const getOrder = (id: string) => api<Order>(`/orders/${encodeURIComponent(id)}`, true);
export const getPolicies = () => api<Policy[]>("/admin/policies", true);

export const getAddresses = () => api<Address[]>("/me/addresses", true);

export const getSellerOrders = (shopId: string, status = "", page = 0) =>
  api<SellerOrderPage>(
    `/seller/shops/${shopId}/orders?page=${page}&size=20${status ? `&status=${encodeURIComponent(status)}` : ""}`,
    true,
  );
export const getSellerOrder = (shopId: string, orderId: string) =>
  api<SellerOrder>(`/seller/shops/${shopId}/orders/${encodeURIComponent(orderId)}`, true);

export const getOverdueOrders = () => api<OverdueOrders>("/admin/orders/overdue", true);
