/** Sign-up rules, mirrored by the API (which is the authority). Used for instant form feedback. */
export const NAME_PATTERN = /^\p{L}[\p{L}\p{M} .'-]{1,59}$/u;
export const EMAIL_PATTERN = /^[^\s@<>",;]{1,64}@[^\s@<>",;]{1,190}\.[A-Za-z]{2,}$/;

export type PasswordCheck = { id: string; label: string; ok: boolean };

export function passwordChecks(password: string): PasswordCheck[] {
  return [
    { id: "length", label: "Từ 8 đến 64 ký tự", ok: password.length >= 8 && password.length <= 64 },
    { id: "letter", label: "Có chữ cái", ok: /\p{L}/u.test(password) },
    { id: "digit", label: "Có chữ số", ok: /\d/.test(password) },
  ];
}

export type RegisterFields = {
  fullName: string;
  email: string;
  password: string;
  confirm: string;
  acceptTerms: boolean;
};
export type RegisterErrors = Partial<Record<keyof RegisterFields, string>>;

export function validateRegister(f: RegisterFields): RegisterErrors {
  const errors: RegisterErrors = {};
  const name = f.fullName.normalize("NFC").trim().replace(/\s+/g, " ");
  if (!NAME_PATTERN.test(name)) errors.fullName = "Họ tên cần 2–60 ký tự chữ, không chứa số.";
  const email = f.email.trim();
  if (email.length > 254 || !EMAIL_PATTERN.test(email))
    errors.email = "Địa chỉ email không hợp lệ.";
  const failed = passwordChecks(f.password).find((c) => !c.ok);
  if (failed) errors.password = "Mật khẩu chưa đáp ứng các yêu cầu bên dưới.";
  else if (f.password !== f.password.trim())
    errors.password = "Mật khẩu không được bắt đầu hoặc kết thúc bằng khoảng trắng.";
  else if (f.password.toLowerCase() === email.toLowerCase())
    errors.password = "Mật khẩu không được trùng với email.";
  if (!errors.password && f.confirm !== f.password) errors.confirm = "Mật khẩu nhập lại chưa khớp.";
  if (!f.acceptTerms) errors.acceptTerms = "Bạn cần đồng ý với điều khoản để tạo tài khoản.";
  return errors;
}
