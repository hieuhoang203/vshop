import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { SearchScreen } from "../src/features/catalog/search-screen";
import { ProductScreen } from "../src/features/catalog/product-screen";
import { SavedProvider } from "../src/features/saved/saved-provider";
import { getCategories, getProduct, getProducts } from "../src/features/catalog/catalog-api";
import { ApiFailure } from "../src/services/http-client";
import AsyncStorage from "@react-native-async-storage/async-storage";

jest.mock("expo-router", () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: () => ({ slug: "den-ban-luma" }),
}));
jest.mock("expo-image", () => ({ Image: require("react-native").Image }));
jest.mock("../src/features/catalog/catalog-api", () => ({
  ...jest.requireActual("../src/features/catalog/catalog-api"),
  getProducts: jest.fn(),
  getCategories: jest.fn(),
  getProduct: jest.fn(),
}));
const item = {
  id: "product-1",
  slug: "den-ban-luma",
  name: "Đèn bàn Luma",
  description: "Đèn bàn dành cho góc làm việc.",
  categorySlug: "goc-lam-viec",
  categoryName: "Góc làm việc",
  shopId: "shop-1",
  shopName: "Shop thử",
  price: 459000,
  currency: "VND" as const,
  stock: 10,
  imageKey: "lamp",
};
const clients: QueryClient[] = [];
afterEach(() => clients.splice(0).forEach((client) => client.clear()));
async function mount(component: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  clients.push(client);
  await render(
    <SafeAreaProvider
      initialMetrics={{
        frame: { x: 0, y: 0, width: 390, height: 844 },
        insets: { top: 30, left: 0, right: 0, bottom: 20 },
      }}
    >
      <QueryClientProvider client={client}>
        <SavedProvider>{component}</SavedProvider>
      </QueryClientProvider>
    </SafeAreaProvider>,
  );
  return client;
}
beforeEach(async () => {
  jest.clearAllMocks();
  await AsyncStorage.clear();
  jest
    .mocked(getCategories)
    .mockResolvedValue([{ id: "c1", slug: "goc-lam-viec", name: "Góc làm việc", count: 1 }]);
  jest.mocked(getProducts).mockResolvedValue({ items: [item], total: 1, page: 0, size: 12 });
  jest.mocked(getProduct).mockResolvedValue(item);
});
test("native search applies price and sort before requesting server results", async () => {
  await mount(<SearchScreen />);
  await screen.findByText("Đèn bàn Luma");
  await fireEvent.press(screen.getByLabelText("Lọc và sắp xếp"));
  await fireEvent.press(screen.getByText("Đến 200k"));
  await fireEvent.press(screen.getByText("Giá thấp đến cao"));
  await fireEvent.press(screen.getByText("Áp dụng"));
  await waitFor(() =>
    expect(getProducts).toHaveBeenLastCalledWith(
      expect.objectContaining({ minPrice: 0, maxPrice: 200000, sort: "price-asc" }),
      0,
      expect.anything(),
    ),
  );
  await fireEvent.changeText(screen.getByLabelText("Tìm sản phẩm"), "Mori");
  await waitFor(() =>
    expect(getProducts).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "Mori", maxPrice: 200000 }),
      0,
      expect.anything(),
    ),
  );
});
test("native product favorite persists a snapshot and updates button state", async () => {
  await mount(<ProductScreen />);
  await screen.findByText("Đèn bàn Luma");
  await fireEvent.press(screen.getByText("Lưu món này"));
  await screen.findByText("Bỏ lưu món này");
  const stored = JSON.parse((await AsyncStorage.getItem("vshop.mobile.saved.v1"))!);
  expect(stored[0]).toMatchObject({ id: item.id, price: item.price });
  expect(stored[0].accessToken).toBeUndefined();
  await fireEvent.press(screen.getByText("Bỏ lưu món này"));
  await screen.findByText("Lưu món này");
});
test("removed product has a real not-found state rather than stale snapshot details", async () => {
  jest.mocked(getProduct).mockRejectedValue(new ApiFailure(404, "Sản phẩm không tồn tại."));
  await mount(<ProductScreen />);
  await screen.findByText("Món này hiện không còn trong danh mục");
  expect(screen.queryByText("Lưu món này")).toBeNull();
});
