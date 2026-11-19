import AsyncStorage from "@react-native-async-storage/async-storage";
import { createContext, useContext, useEffect, useRef, useState } from "react";
import { Alert } from "react-native";
import * as Haptics from "expo-haptics";
import { parseSaved, savedKey, toggleProduct, type SavedProduct } from "./saved-model";

const Context = createContext<{
  items: SavedProduct[];
  ready: boolean;
  toggle(product: SavedProduct): void;
} | null>(null);
export function SavedProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<SavedProduct[]>([]);
  const [ready, setReady] = useState(false);
  const current = useRef(items);
  const pending = useRef(Promise.resolve());
  useEffect(() => {
    AsyncStorage.getItem(savedKey)
      .then((raw) => {
        current.current = parseSaved(raw);
        setItems(current.current);
        setReady(true);
      })
      .catch(() =>
        Alert.alert("Chưa thể đọc món đã lưu", "Hãy đóng và mở lại ứng dụng để thử lại."),
      );
  }, []);
  function toggle(product: SavedProduct) {
    if (!ready) return;
    pending.current = pending.current
      .then(async () => {
        const next = toggleProduct(current.current, product);
        await AsyncStorage.setItem(savedKey, JSON.stringify(next));
        current.current = next;
        setItems(next);
        void Haptics.selectionAsync().catch(() => {});
      })
      .catch(() =>
        Alert.alert("Chưa lưu được món", "Bộ nhớ thiết bị chưa sẵn sàng. Vui lòng thử lại."),
      );
  }
  return <Context.Provider value={{ items, ready, toggle }}>{children}</Context.Provider>;
}
export function useSaved() {
  const value = useContext(Context);
  if (!value) throw new Error("SavedProvider missing");
  return value;
}
