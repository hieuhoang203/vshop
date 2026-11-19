import * as AuthSession from "expo-auth-session";
import * as WebBrowser from "expo-web-browser";
import * as SecureStore from "expo-secure-store";
import { environment } from "../../config/environment";
import { SessionExpired, TokenSession, type Tokens } from "./token-session";

WebBrowser.maybeCompleteAuthSession();
const credentialKey = "vshop.mobile.refresh.v1";
const discovery = () => AuthSession.fetchDiscoveryAsync(environment.issuer);
function tokens(response: AuthSession.TokenResponse, oldRefresh?: string): Tokens {
  const refreshToken = response.refreshToken ?? oldRefresh;
  if (!refreshToken) throw new SessionExpired("Máy chủ chưa cấp phiên đăng nhập mobile.");
  return {
    accessToken: response.accessToken,
    refreshToken,
    expiresAt: Date.now() + (response.expiresIn ?? 300) * 1000,
  };
}
export const session = new TokenSession(
  {
    read: () => SecureStore.getItemAsync(credentialKey),
    write: (value) =>
      value === null
        ? SecureStore.deleteItemAsync(credentialKey)
        : SecureStore.setItemAsync(credentialKey, value, {
            keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
          }),
  },
  {
    async refresh(refreshToken) {
      try {
        const response = await AuthSession.refreshAsync(
          { clientId: environment.clientId, refreshToken },
          await discovery(),
        );
        return tokens(response, refreshToken);
      } catch (error) {
        if (
          error instanceof AuthSession.TokenError &&
          ["invalid_grant", "invalid_token"].includes(error.code)
        ) {
          throw new SessionExpired("Phiên đã hết hạn. Vui lòng đăng nhập lại.");
        }
        throw new Error("Chưa thể khôi phục phiên. Kiểm tra mạng rồi thử lại.");
      }
    },
    async revoke(refreshToken) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 5000);
      try {
        await fetch(`${environment.issuer}/protocol/openid-connect/logout`, {
          method: "POST",
          signal: controller.signal,
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({
            client_id: environment.clientId,
            refresh_token: refreshToken,
          }).toString(),
        });
      } finally {
        clearTimeout(timeout);
      }
    },
  },
);

export async function login() {
  const endpoints = await discovery();
  const request = new AuthSession.AuthRequest({
    clientId: environment.clientId,
    redirectUri: environment.redirectUri,
    scopes: ["openid", "profile"],
    responseType: AuthSession.ResponseType.Code,
    usePKCE: true,
    prompt: AuthSession.Prompt.Login,
  });
  const result = await request.promptAsync(endpoints);
  if (result.type === "cancel" || result.type === "dismiss") return false;
  if (result.type !== "success" || !result.params.code || !request.codeVerifier) {
    throw new Error("Đăng nhập chưa hoàn tất. Vui lòng thử lại.");
  }
  const response = await AuthSession.exchangeCodeAsync(
    {
      clientId: environment.clientId,
      code: result.params.code,
      redirectUri: environment.redirectUri,
      extraParams: { code_verifier: request.codeVerifier },
    },
    endpoints,
  );
  await session.accept(tokens(response));
  return true;
}
