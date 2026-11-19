import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { catalogKey, getCategories, getProducts, type CatalogFilter } from "./catalog-api";

export function useCatalog(filter: CatalogFilter) {
  const products = useInfiniteQuery({
    queryKey: catalogKey(filter),
    initialPageParam: 0,
    queryFn: ({ pageParam, signal }) => getProducts(filter, pageParam, signal),
    getNextPageParam: (last) =>
      (last.page + 1) * last.size < last.total ? last.page + 1 : undefined,
  });
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: ({ signal }) => getCategories(signal),
  });
  return { products, categories, items: products.data?.pages.flatMap((page) => page.items) ?? [] };
}
