import { test, expect, type Page } from "@playwright/test";
import { account } from "./support/auth";
import { deleteUserByEmail, realmRolesOf } from "./support/keycloak";
import { actionLink, waitForMail } from "./support/mailpit";

const PASSWORD = "matkhau2026";
/** The web limits attempts per client address; each test pretends to be a different visitor. */
const visitor = () => ({
  "x-forwarded-for": `10.${Math.floor(Math.random() * 250)}.${Math.floor(Math.random() * 250)}.${Date.now() % 250}`,
});

async function signUp(page: Page, email: string, password = PASSWORD) {
  await page.goto("/register");
  await page.getByLabel("Họ và tên").fill("Nguyễn Thị Hoa");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByLabel("Nhập lại mật khẩu").fill(password);
  await page.getByLabel(/Tôi đã đọc và đồng ý/).check();
  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page.getByTestId("register-success")).toContainText("Tạo tài khoản thành công");
  await expect(page.getByTestId("register-success")).toContainText("Bạn đã được đăng nhập");
  await page.getByRole("link", { name: "Bắt đầu mua sắm" }).click();
  await expect(page.getByRole("button", { name: /^Tài khoản / })).toBeVisible();
}

async function logOut(page: Page) {
  await page.getByRole("button", { name: /^Tài khoản / }).click();
  await page.getByRole("menuitem", { name: "Đăng xuất" }).click();
  await expect(page.getByRole("link", { name: "Đăng ký", exact: true })).toBeVisible();
}

async function signInWith(page: Page, email: string, password: string) {
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
}

test("sign-up page validates every field in the browser before sending anything", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Đăng ký", exact: true }).click();
  await expect(page).toHaveURL(/\/register$/);
  let sent = 0;
  await page.route("**/api/auth/register", (route) => {
    sent += 1;
    return route.abort();
  });

  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page.getByText("Họ tên cần 2–60 ký tự chữ, không chứa số.")).toBeVisible();
  await expect(page.getByText("Địa chỉ email không hợp lệ.")).toBeVisible();
  await expect(page.getByText("Bạn cần đồng ý với điều khoản để tạo tài khoản.")).toBeVisible();

  await page.getByLabel("Họ và tên").fill("Hoa 123");
  await page.getByLabel("Email").fill("khong-hop-le");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("abcdefgh");
  await expect(page.getByRole("list", { name: "Yêu cầu mật khẩu" })).toContainText(
    "Có chữ số (chưa đạt)",
  );
  await page.getByLabel("Mật khẩu", { exact: true }).fill("abcdefg1");
  await expect(page.getByRole("list", { name: "Yêu cầu mật khẩu" })).toContainText(
    "Có chữ số (đạt)",
  );
  await page.getByLabel("Nhập lại mật khẩu").fill("khac12345");
  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page.getByText("Mật khẩu nhập lại chưa khớp.")).toBeVisible();
  expect(sent).toBe(0);
});

test("a new buyer signs up, is signed in at once, is only a BUYER and can sign in again", async ({
  page,
  request,
}) => {
  const email = `e2e.signup.${Date.now()}@example.com`;
  const headers = visitor();
  await page.setExtraHTTPHeaders(headers);
  try {
    await signUp(page, email);
    await expect(page.getByRole("button", { name: "Tài khoản Nguyễn Thị Hoa" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Giỏ hàng" })).toBeVisible();

    // Nothing but the BUYER role is granted, whatever the form said.
    const roles = await realmRolesOf(request, email);
    expect(roles).toContain("BUYER");
    expect(roles).not.toContain("SELLER");
    expect(roles).not.toContain("ADMIN");

    // The same email again is refused next to the field.
    const again = await page.request.post("/api/auth/register", {
      headers: { ...headers, origin: "http://localhost:3100" },
      data: { fullName: "Nguyễn Thị Hoa", email, password: PASSWORD, acceptTerms: true },
    });
    expect(again.status()).toBe(409);
    expect((await again.json()).code).toBe("EMAIL_ALREADY_REGISTERED");

    // Sign out ends the session; the in-site form signs the same person in again.
    await logOut(page);
    await page.getByRole("link", { name: "Đăng nhập", exact: true }).first().click();
    await expect(page).toHaveURL(/\/login$/);
    await signInWith(page, email, "sai-mat-khau-1");
    await expect(page.locator("main").getByRole("alert")).toContainText(
      "Email hoặc mật khẩu không đúng.",
    );
    await signInWith(page, email, PASSWORD);
    await expect(page.getByRole("button", { name: "Tài khoản Nguyễn Thị Hoa" })).toBeVisible();
  } finally {
    await deleteUserByEmail(request, email);
  }
});

test("a forgotten password is reset from an e-mailed link and the new one signs in", async ({
  page,
  request,
}) => {
  const email = `e2e.reset.${Date.now()}@example.com`;
  await page.setExtraHTTPHeaders(visitor());
  try {
    await signUp(page, email);
    await logOut(page);

    await page.goto("/login");
    await page.getByRole("link", { name: "Quên mật khẩu?" }).click();
    await expect(page).toHaveURL(/forgot-password$/);
    await page.getByLabel("Email").fill(email);
    await page.getByRole("button", { name: "Gửi liên kết" }).click();
    await expect(page.getByTestId("forgot-success")).toContainText("Kiểm tra email của bạn");

    const mail = await waitForMail(request, email, /Hoàn tất yêu cầu/);
    await page.goto(actionLink(mail.Text));
    await page.getByRole("link", { name: /Bấm vào đây|Click here/ }).click();
    await page.locator("#password-new").fill("matkhaumoi2026");
    await page.locator("#password-confirm").fill("matkhaumoi2026");
    await page.getByRole("button", { name: /Xác nhận|Submit/ }).click();

    await page.goto("/login");
    await signInWith(page, email, PASSWORD);
    await expect(page.locator("main").getByRole("alert")).toContainText(
      "Email hoặc mật khẩu không đúng.",
    );
    await signInWith(page, email, "matkhaumoi2026");
    await expect(page.getByRole("button", { name: "Tài khoản Nguyễn Thị Hoa" })).toBeVisible();
  } finally {
    await deleteUserByEmail(request, email);
  }
});

test("a buyer verifies the sign-in email before turning on e-mail notifications", async ({
  page,
  request,
}) => {
  const email = `e2e.verify.${Date.now()}@example.com`;
  await page.setExtraHTTPHeaders(visitor());
  try {
    await signUp(page, email);
    await page.goto("/notifications/settings");
    await page.getByRole("button", { name: "Bật thông báo email" }).click();
    await expect(page.locator("main").getByRole("alert")).toContainText("chưa được xác minh");
    await page.getByRole("button", { name: "Gửi email xác minh" }).click();
    await expect(page.getByText("Đã gửi email xác minh.")).toBeVisible();
    const mail = await waitForMail(request, email, /Hoàn tất yêu cầu/);
    expect(mail.Text).toContain("xác minh email");
    await page.goto(actionLink(mail.Text));
    await page.getByRole("link", { name: /Bấm vào đây|Tiếp tục|Click here/i }).click();

    // A fresh sign-in carries the verified claim.
    await page.goto("/");
    await logOut(page);
    await page.goto("/login");
    await signInWith(page, email, PASSWORD);
    await expect(page.getByRole("button", { name: /^Tài khoản / })).toBeVisible();
    await page.goto("/notifications/settings");
    await page.getByRole("button", { name: "Bật thông báo email" }).click();
    await expect(page.getByText(/Đang gửi bản sao thông báo tới/)).toBeVisible();
  } finally {
    await deleteUserByEmail(request, email);
  }
});

test("administrators are sent to the verified sign-in page; legal pages exist", async ({
  page,
}) => {
  const admin = account("admin.local");
  await page.setExtraHTTPHeaders(visitor());
  const refused = await page.request.post("/api/auth/login-direct", {
    headers: { origin: "http://localhost:3100" },
    data: { email: "admin@vshop.local", password: admin.password },
  });
  // An enrolled administrator cannot pass the password-only grant, and no session is created.
  expect([401, 409]).toContain(refused.status());
  expect(refused.headers()["set-cookie"]).toBeUndefined();

  await page.goto("/terms");
  await expect(page.getByRole("heading", { name: "Điều khoản sử dụng" })).toBeVisible();
  await page.goto("/privacy");
  await expect(page.getByRole("heading", { name: "Chính sách bảo mật" })).toBeVisible();
});

test("the sign-in and sign-up endpoints reject other sites and bad input themselves", async ({
  request,
}) => {
  const crossSite = await request.post("/api/auth/register", {
    headers: { origin: "https://untrusted.example" },
    data: { fullName: "Nguyễn Thị Hoa", email: "x@example.com", password: PASSWORD },
  });
  expect(crossSite.status()).toBe(403);
  const crossLogin = await request.post("/api/auth/login-direct", {
    headers: { origin: "https://untrusted.example" },
    data: { email: "x@example.com", password: PASSWORD },
  });
  expect(crossLogin.status()).toBe(403);
  const headers = visitor();
  const weak = await request.post("/api/auth/register", {
    headers: { ...headers, origin: "http://localhost:3100" },
    data: {
      fullName: "Nguyễn Thị Hoa",
      email: `weak.pw.${Date.now()}@example.com`,
      password: "abcdefgh",
      acceptTerms: true,
    },
  });
  expect(weak.status()).toBe(400);
  const noTerms = await request.post("/api/auth/register", {
    headers: { ...headers, origin: "http://localhost:3100" },
    data: {
      fullName: "Nguyễn Thị Hoa",
      email: `no.terms.${Date.now()}@example.com`,
      password: PASSWORD,
      acceptTerms: false,
    },
  });
  expect(noTerms.status()).toBe(400);
});
