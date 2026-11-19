"use client";

import { Theme } from "@radix-ui/themes";
import * as Toast from "@radix-ui/react-toast";
import { Check, X } from "lucide-react";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { Product } from "@vshop/contracts";

export type SavedProduct = Pick<Product, "id" | "slug" | "name" | "price" | "imageKey">;
const STORAGE_KEY = "vshop.saved-products.v1";
type DiscoveryContext = {
  saved: SavedProduct[];
  toggleSaved: (product: SavedProduct) => void;
  notify: (message: string) => void;
};
const Context = createContext<DiscoveryContext | null>(null);

function readSaved(): SavedProduct[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "[]");
    if (!Array.isArray(value)) return [];
    return value
      .filter(
        (p): p is SavedProduct =>
          p &&
          typeof p.id === "string" &&
          typeof p.slug === "string" &&
          /^[a-z0-9-]{1,160}$/.test(p.slug) &&
          typeof p.name === "string" &&
          p.name.length <= 200 &&
          typeof p.price === "number" &&
          Number.isSafeInteger(p.price) &&
          p.price > 0 &&
          ["lamp", "tote", "mug", "headphones", "bottle", "notebook"].includes(p.imageKey),
      )
      .slice(0, 100);
  } catch {
    return [];
  }
}

export function VshopProviders({ children }: { children: React.ReactNode }) {
  const [saved, setSaved] = useState<SavedProduct[]>([]);
  const savedRef = useRef<SavedProduct[]>([]);
  const [toast, setToast] = useState({ id: 0, message: "", open: false });
  useEffect(() => {
    const sync = () => {
      const next = readSaved();
      savedRef.current = next;
      setSaved(next);
    };
    sync();
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY) sync();
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);
  const notify = useCallback(
    (message: string) => setToast({ id: Date.now(), message, open: true }),
    [],
  );
  const toggleSaved = useCallback(
    (product: SavedProduct) => {
      const exists = savedRef.current.some((p) => p.id === product.id);
      const next = exists
        ? savedRef.current.filter((p) => p.id !== product.id)
        : [
            ...savedRef.current,
            {
              id: product.id,
              slug: product.slug,
              name: product.name,
              price: product.price,
              imageKey: product.imageKey,
            },
          ].slice(-100);
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        savedRef.current = next;
        setSaved(next);
        notify(
          exists
            ? "Đã bỏ món khỏi danh sách lưu."
            : "Đã lưu món vào góc của bạn trên thiết bị này.",
        );
      } catch {
        notify("Chưa thể lưu trên thiết bị này. Kiểm tra quyền lưu trữ của trình duyệt.");
      }
    },
    [notify],
  );
  const value = useMemo(() => ({ saved, toggleSaved, notify }), [saved, toggleSaved, notify]);
  return (
    <Theme
      accentColor="iris"
      grayColor="slate"
      radius="large"
      scaling="100%"
      className="vshop-theme"
    >
      <Toast.Provider duration={3500} swipeDirection="right">
        <Context.Provider value={value}>{children}</Context.Provider>
        <Toast.Root
          key={toast.id}
          className="vshop-toast"
          open={toast.open}
          onOpenChange={(open) => setToast((current) => ({ ...current, open }))}
        >
          <span className="toast-icon">
            <Check size={18} />
          </span>
          <Toast.Title>{toast.message}</Toast.Title>
          <Toast.Close className="icon-plain" aria-label="Đóng thông báo">
            <X size={17} />
          </Toast.Close>
        </Toast.Root>
        <Toast.Viewport className="toast-viewport" />
      </Toast.Provider>
    </Theme>
  );
}

export function useDiscovery() {
  const context = useContext(Context);
  if (!context) throw new Error("Discovery components must be inside VshopProviders");
  return context;
}
