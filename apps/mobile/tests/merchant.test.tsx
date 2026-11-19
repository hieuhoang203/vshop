import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import type { Shop } from "@vshop/contracts";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { MerchantScreen } from "../src/features/merchant/merchant-screen";
import { useAuth } from "../src/features/auth/auth-provider";

jest.mock("../src/features/auth/auth-provider", () => ({ useAuth: jest.fn() }));
const rejected: Shop = {
  id: "shop-1",
  name: "Mộc Studio",
  description: "Gian hàng phụ kiện làm việc dành cho bạn.",
  status: "REJECTED",
  rejectionReason: "Bổ sung nhóm sản phẩm",
  createdAt: "2026-11-19T00:00:00Z",
};
function auth(authorized: jest.Mock) {
  jest.mocked(useAuth).mockReturnValue({
    profile: { subject: "buyer-1", username: "buyer.local", realmAccess: { roles: ["BUYER"] } },
    busy: false,
    error: "",
    authorized,
    signIn: jest.fn(),
    signOut: jest.fn(),
    restore: jest.fn(),
  });
}
async function mount() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { gcTime: 0 } },
  });
  await render(
    <SafeAreaProvider
      initialMetrics={{
        frame: { x: 0, y: 0, width: 390, height: 844 },
        insets: { top: 30, left: 0, right: 0, bottom: 20 },
      }}
    >
      <QueryClientProvider client={client}>
        <MerchantScreen />
      </QueryClientProvider>
    </SafeAreaProvider>,
  );
}
test("rejected seller can resubmit through PUT and submitted status removes the form", async () => {
  let current = rejected;
  const authorized = jest.fn(async (_path: string, options?: RequestInit) => {
    if (options?.method === "PUT") {
      current = {
        ...current,
        ...JSON.parse(String(options.body)),
        status: "SUBMITTED",
        rejectionReason: null,
      };
      return current;
    }
    return [current];
  });
  auth(authorized);
  await mount();
  await screen.findByText("Bổ sung nhóm sản phẩm");
  await fireEvent.changeText(
    screen.getByLabelText("Giới thiệu gian hàng"),
    "Gian hàng Mộc chuyên phụ kiện cho góc làm việc và đồ dùng cá nhân.",
  );
  await fireEvent.press(screen.getByText("Gửi lại để xét duyệt"));
  await screen.findByText("Đang xét duyệt");
  expect(screen.queryByLabelText("Tên gian hàng")).toBeNull();
  expect(authorized).toHaveBeenCalledWith(
    "/seller/shops/shop-1",
    expect.objectContaining({ method: "PUT" }),
  );
});
test("new seller validation blocks invalid commands and POST creates a submitted profile", async () => {
  let shops: Shop[] = [];
  const authorized = jest.fn(async (_path: string, options?: RequestInit) => {
    if (options?.method === "POST") {
      const shop = {
        ...rejected,
        ...JSON.parse(String(options.body)),
        status: "SUBMITTED" as const,
        rejectionReason: null,
      };
      shops = [shop];
      return shop;
    }
    return shops;
  });
  auth(authorized);
  await mount();
  await screen.findByText("Hồ sơ gian hàng");
  await fireEvent.press(screen.getByText("Gửi hồ sơ gian hàng"));
  await screen.findByText("Tên shop cần từ 3–100 ký tự; giới thiệu cần từ 20–2000 ký tự.");
  expect(authorized.mock.calls.filter((call) => call[1]?.method === "POST")).toHaveLength(0);
  await fireEvent.changeText(screen.getByLabelText("Tên gian hàng"), "  Mộc Studio  ");
  await fireEvent.changeText(screen.getByLabelText("Giới thiệu gian hàng"), rejected.description);
  await fireEvent.press(screen.getByText("Gửi hồ sơ gian hàng"));
  await screen.findByText("Đang xét duyệt");
  await waitFor(() =>
    expect(authorized).toHaveBeenCalledWith(
      "/seller/shops",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ name: "Mộc Studio", description: rejected.description }),
      }),
    ),
  );
});
