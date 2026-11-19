import type { CatalogPage, Category, Product } from "@vshop/contracts";
import { request } from "../../services/http-client";

export type CatalogFilter = {
  q: string;
  category: string;
  sort: "newest" | "price-asc" | "price-desc";
  minPrice?: number;
  maxPrice?: number;
};
export const emptyFilter: CatalogFilter = { q: "", category: "", sort: "newest" };
export function catalogKey(filter: CatalogFilter) {
  return ["catalog", filter] as const;
}
export function getProducts(filter: CatalogFilter, page: number, signal?: AbortSignal) {
  const params = new URLSearchParams({
    q: filter.q.trim(),
    category: filter.category,
    sort: filter.sort,
    page: String(page),
    size: "12",
  });
  if (filter.minPrice !== undefined) params.set("minPrice", String(filter.minPrice));
  if (filter.maxPrice !== undefined) params.set("maxPrice", String(filter.maxPrice));
  return request<CatalogPage>(`/catalog/products?${params}`, { signal });
}
export const getCategories = (signal?: AbortSignal) =>
  request<Category[]>("/catalog/categories", { signal });
export const getProduct = (slug: string, signal?: AbortSignal) =>
  request<Product>(`/catalog/products/${encodeURIComponent(slug)}`, { signal });
