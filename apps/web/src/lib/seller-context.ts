import "server-only";
import type { Shop } from "@vshop/contracts";
import { getSession } from "./auth";
import { getMyShops } from "./api";

export type SellerContext =
  | { kind: "login" }
  | { kind: "forbidden" }
  | { kind: "no-shop" }
  | { kind: "not-approved"; shop: Shop }
  | { kind: "ok"; shop: Shop };

/** Resolves who is calling and whether their (single, pilot) shop may manage products. */
export async function loadSellerContext(): Promise<SellerContext> {
  const session = await getSession();
  if (!session) return { kind: "login" };
  if (!session.roles.some((r) => r === "BUYER" || r === "SELLER")) return { kind: "forbidden" };
  const shop = (await getMyShops())[0];
  if (!shop) return { kind: "no-shop" };
  return shop.status === "APPROVED" ? { kind: "ok", shop } : { kind: "not-approved", shop };
}
