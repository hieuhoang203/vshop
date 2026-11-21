import { useEffect, useState } from "react";
import { AppState, View } from "react-native";
import { Stack, type ErrorBoundaryProps } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { QueryClient, QueryClientProvider, focusManager } from "@tanstack/react-query";
import { useFonts } from "expo-font";
import * as SplashScreen from "expo-splash-screen";
import { AuthProvider } from "../features/auth/auth-provider";
import { SavedProvider } from "../features/saved/saved-provider";
import { Button, Txt } from "../components/ui";
import { colors, fonts } from "../theme/tokens";

void SplashScreen.preventAutoHideAsync().catch(() => {});
export function ErrorBoundary({ retry }: ErrorBoundaryProps) {
  return (
    <View
      style={{
        flex: 1,
        padding: 30,
        justifyContent: "center",
        gap: 20,
        backgroundColor: colors.white,
      }}
    >
      <Txt>VShop chưa thể mở màn hình này. Vui lòng thử lại.</Txt>
      <Button title="Thử lại" onPress={retry} />
    </View>
  );
}
export default function RootLayout() {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30000, retry: 1, gcTime: 5 * 60 * 1000 },
          mutations: { retry: false },
        },
      }),
  );
  const [loaded, fontError] = useFonts({
    Manrope: require("../../assets/manrope.ttf"),
    ManropeBold: require("../../assets/manrope.ttf"),
  });
  useEffect(() => {
    if (loaded || fontError) void SplashScreen.hideAsync();
  }, [loaded, fontError]);
  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state) =>
      focusManager.setFocused(state === "active"),
    );
    return () => subscription.remove();
  }, []);
  if (!loaded && !fontError) return null;
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <SavedProvider>
            <StatusBar style="dark" />
            <Stack
              screenOptions={{
                headerTintColor: colors.ink,
                headerShadowVisible: false,
                headerTitleStyle: { fontFamily: fonts.bold },
                contentStyle: { backgroundColor: colors.white },
              }}
            >
              <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
              <Stack.Screen
                name="products/[slug]"
                options={{ title: "Món hợp gu", headerBackButtonDisplayMode: "minimal" }}
              />
              <Stack.Screen
                name="seller"
                options={{ title: "Gian hàng của tôi", headerBackButtonDisplayMode: "minimal" }}
              />
              <Stack.Screen
                name="register"
                options={{ title: "Đăng ký", headerBackButtonDisplayMode: "minimal" }}
              />
              <Stack.Screen name="auth/callback" options={{ headerShown: false }} />
            </Stack>
          </SavedProvider>
        </AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}
