import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { RegisterScreen } from "../src/features/auth/register-screen";
import { useAuth } from "../src/features/auth/auth-provider";
import { validateRegister } from "../src/features/auth/register-model";

jest.mock("../src/features/auth/auth-provider", () => ({ useAuth: jest.fn() }));
jest.mock("expo-router", () => ({ router: { back: jest.fn(), push: jest.fn() } }));

const valid = {
  fullName: "Nguyễn Thị Hoa",
  email: "hoa@example.com",
  password: "matkhau123",
  confirm: "matkhau123",
  acceptTerms: true,
};

describe("sign-up rules", () => {
  it("accepts a valid form and explains each problem", () => {
    expect(validateRegister(valid)).toEqual({});
    expect(validateRegister({ ...valid, fullName: "Hoa 123" }).fullName).toBeDefined();
    expect(validateRegister({ ...valid, email: "khong-hop-le" }).email).toBeDefined();
    expect(
      validateRegister({ ...valid, password: "abcdefgh", confirm: "abcdefgh" }).password,
    ).toBeDefined();
    expect(validateRegister({ ...valid, confirm: "khac12345" }).confirm).toBeDefined();
    expect(validateRegister({ ...valid, acceptTerms: false }).acceptTerms).toBeDefined();
    expect(
      validateRegister({ ...valid, password: "hoa@example.com", confirm: "hoa@example.com" })
        .password,
    ).toBeDefined();
  });
});

function mount() {
  jest.mocked(useAuth).mockReturnValue({
    profile: null,
    busy: false,
    error: "",
    authorized: jest.fn(),
    signIn: jest.fn(),
    signOut: jest.fn(),
    restore: jest.fn(),
  });
  return render(
    <SafeAreaProvider
      initialMetrics={{
        frame: { x: 0, y: 0, width: 390, height: 844 },
        insets: { top: 30, left: 0, right: 0, bottom: 20 },
      }}
    >
      <RegisterScreen />
    </SafeAreaProvider>,
  );
}
async function fill() {
  await fireEvent.changeText(screen.getByLabelText("Họ và tên"), valid.fullName);
  await fireEvent.changeText(screen.getByLabelText("Email"), valid.email);
  await fireEvent.changeText(screen.getByLabelText("Mật khẩu"), valid.password);
  await fireEvent.changeText(screen.getByLabelText("Nhập lại mật khẩu"), valid.confirm);
  await fireEvent.press(screen.getByRole("checkbox"));
}

describe("RegisterScreen", () => {
  afterEach(() => jest.restoreAllMocks());

  it("shows the problems and sends nothing when the form is invalid", async () => {
    const fetchMock = jest.spyOn(global, "fetch");
    await mount();
    await fireEvent.press(screen.getByRole("button", { name: "Tạo tài khoản" }));
    expect(await screen.findByText("Địa chỉ email không hợp lệ.")).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("creates the account and offers to sign in", async () => {
    const fetchMock = jest
      .spyOn(global, "fetch")
      .mockResolvedValue(new Response(null, { status: 201 }));
    await mount();
    await fill();
    await fireEvent.press(screen.getByRole("button", { name: "Tạo tài khoản" }));
    expect(await screen.findByText("Tạo tài khoản thành công!")).toBeTruthy();
    const call = fetchMock.mock.calls[0];
    const url = call?.[0];
    const init = call?.[1];
    expect(String(url)).toContain("/api/v1/auth/register");
    expect(JSON.parse(String((init as RequestInit).body))).toMatchObject({
      email: "hoa@example.com",
      acceptTerms: true,
    });
  });

  it("puts a taken email next to the field", async () => {
    jest.spyOn(global, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({ code: "EMAIL_ALREADY_REGISTERED", message: "Email này đã được đăng ký." }),
        {
          status: 409,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );
    await mount();
    await fill();
    await fireEvent.press(screen.getByRole("button", { name: "Tạo tài khoản" }));
    await waitFor(() => expect(screen.getByText(/Email này đã được đăng ký/)).toBeTruthy());
  });
});
