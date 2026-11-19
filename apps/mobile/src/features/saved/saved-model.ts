import type { Product } from "@vshop/contracts";
export type SavedProduct = Pick<Product, "id" | "slug" | "name" | "price" | "imageKey">;
export const savedKey = "vshop.mobile.saved.v1";
export function parseSaved(raw: string | null): SavedProduct[] {
  try {
    const list: unknown = JSON.parse(raw ?? "[]");
    if (!Array.isArray(list)) return [];
    const unique = new Map<string, SavedProduct>();
    for (const p of list) {
      if (
        p &&
        typeof p.id === "string" &&
        typeof p.name === "string" &&
        p.name.length <= 200 &&
        typeof p.slug === "string" &&
        /^[a-z0-9-]{1,160}$/.test(p.slug) &&
        Number.isSafeInteger(p.price) &&
        p.price > 0 &&
        typeof p.imageKey === "string"
      ) {
        unique.set(p.id, {
          id: p.id,
          slug: p.slug,
          name: p.name,
          price: p.price,
          imageKey: p.imageKey,
        });
      }
    }
    return [...unique.values()].slice(-100);
  } catch {
    return [];
  }
}
export function toggleProduct(items: SavedProduct[], product: SavedProduct) {
  if (items.some((item) => item.id === product.id))
    return items.filter((item) => item.id !== product.id);
  return [
    ...items,
    {
      id: product.id,
      slug: product.slug,
      name: product.name,
      price: product.price,
      imageKey: product.imageKey,
    },
  ].slice(-100);
}
