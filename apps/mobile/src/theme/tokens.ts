export const colors = {
  ink: "#20202A",
  muted: "#716A7E",
  purple: "#713AF4",
  purpleLight: "#F0EAFE",
  lime: "#D6F77B",
  pink: "#FFC5DE",
  line: "#EAE5F1",
  surface: "#F8F6FC",
  white: "#FFFFFF",
  danger: "#B22248",
  green: "#356A47",
};
export const fonts = { regular: "Manrope", bold: "ManropeBold" };
export const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);
