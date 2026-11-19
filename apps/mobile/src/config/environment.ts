function endpoint(value: string, name: string) {
  const url = new URL(value);
  if (
    !["http:", "https:"].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash
  ) {
    throw new Error(`${name} phải là URL HTTP(S) không chứa thông tin đăng nhập hoặc query.`);
  }
  if (!__DEV__ && process.env.EXPO_PUBLIC_ENV !== "development" && url.protocol !== "https:") {
    throw new Error(`${name} phải dùng HTTPS trong bản phát hành.`);
  }
  return value.replace(/\/$/, "");
}
export const environment = {
  apiUrl: endpoint(process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:8081", "API URL"),
  issuer: endpoint(
    process.env.EXPO_PUBLIC_OIDC_ISSUER ?? "http://localhost:8180/realms/vshop",
    "OIDC issuer",
  ),
  clientId: "vshop-mobile",
  redirectUri: "vshop://auth/callback",
};
