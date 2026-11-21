import { test, expect } from "@playwright/test";
import { deleteUserByEmail, realmRolesOf } from "./support/keycloak";

const PASSWORD = "matkhau2026";
/** The API limits sign-ups per client address; each test pretends to be a different visitor. */
const visitor = () => ({
  "x-forwarded-for": `10.${Math.floor(Math.random() * 250)}.${Math.floor(Math.random() * 250)}.${Date.now() % 250}`,
});

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

test("a new buyer signs up in the site, signs in with the same email and is only a BUYER", async ({
  page,
  request,
}) => {
  const email = `e2e.signup.${Date.now()}@example.com`;
  try {
    const headers = visitor();
    await page.setExtraHTTPHeaders(headers);
    await page.goto("/register");
    await page.getByLabel("Họ và tên").fill("Nguyễn Thị Hoa");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Mật khẩu", { exact: true }).fill(PASSWORD);
    await page.getByLabel("Nhập lại mật khẩu").fill(PASSWORD);
    await page.getByLabel(/Tôi đã đọc và đồng ý/).check();
    await page.getByRole("button", { name: "Tạo tài khoản" }).click();
    await expect(page.getByTestId("register-success")).toContainText("Tạo tài khoản thành công");

    // Nothing but the BUYER role is granted, whatever the form said.
    expect(await realmRolesOf(request, email)).toEqual(expect.arrayContaining(["BUYER"]));
    expect(await realmRolesOf(request, email)).not.toContain("SELLER");
    expect(await realmRolesOf(request, email)).not.toContain("ADMIN");

    // Same email again is refused next to the field, with a way to sign in.
    const again = await page.request.post("/api/auth/register", {
      headers: { ...headers, origin: "http://localhost:3100" },
      data: { fullName: "Nguyễn Thị Hoa", email, password: PASSWORD, acceptTerms: true },
    });
    expect(again.status()).toBe(409);
    expect((await again.json()).code).toBe("EMAIL_ALREADY_REGISTERED");

    // Sign in: the email is pre-filled, the page is Vietnamese, the real name is shown.
    await page.getByRole("link", { name: "Đăng nhập ngay" }).click();
    await expect(page.getByRole("heading", { name: "Đăng nhập vào tài khoản" })).toBeVisible();
    await expect(page.getByLabel("Email hoặc tên đăng nhập")).toHaveValue(email);
    await page.getByLabel("Mật khẩu", { exact: true }).fill(PASSWORD);
    await page.getByRole("button", { name: "Đăng nhập" }).click();
    await expect(page.getByRole("button", { name: "Tài khoản Nguyễn Thị Hoa" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Giỏ hàng" })).toBeVisible();
  } finally {
    await deleteUserByEmail(request, email);
  }
});

test("the sign-up endpoint is not reachable from other sites and rejects bad input itself", async ({
  request,
}) => {
  const body = { fullName: "Nguyễn Thị Hoa", email: "x@example.com", password: PASSWORD };
  const crossSite = await request.post("/api/auth/register", {
    headers: { origin: "https://untrusted.example" },
    data: { ...body, acceptTerms: true },
  });
  expect(crossSite.status()).toBe(403);
  const headers = visitor();
  const weak = await request.post("/api/auth/register", {
    headers: { ...headers, origin: "http://localhost:3100" },
    data: { ...body, email: "weak.pw@example.com", password: "abcdefgh", acceptTerms: true },
  });
  expect(weak.status()).toBe(400);
  const noTerms = await request.post("/api/auth/register", {
    headers: { ...headers, origin: "http://localhost:3100" },
    data: { ...body, email: "no.terms@example.com", acceptTerms: false },
  });
  expect(noTerms.status()).toBe(400);
});
