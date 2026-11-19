import type { ConfigContext, ExpoConfig } from "expo/config";

export default ({ config }: ConfigContext): ExpoConfig => {
  if (process.env.VSHOP_BUILD_ENV === "production") {
    for (const name of ["EXPO_PUBLIC_API_URL", "EXPO_PUBLIC_OIDC_ISSUER"]) {
      if (!process.env[name]?.startsWith("https://"))
        throw new Error(`${name} must use HTTPS for production.`);
    }
    if (process.env.VSHOP_ALLOW_HTTP === "true" || process.env.EXPO_PUBLIC_ENV === "development")
      throw new Error("Production cannot use development HTTP settings.");
  }
  return {
    ...config,
    name: "VShop",
    slug: "vshop-mobile",
    version: "0.1.0",
    scheme: "vshop",
    orientation: "portrait",
    userInterfaceStyle: "light",
    icon: "./assets/icon.png",
    ios: {
      bundleIdentifier: "vn.vshop.mobile",
      supportsTablet: true,
      infoPlist:
        process.env.VSHOP_ALLOW_HTTP === "true"
          ? { NSAppTransportSecurity: { NSAllowsLocalNetworking: true } }
          : {},
    },
    android: {
      package: "vn.vshop.mobile",
      blockedPermissions: [
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
      ],
      adaptiveIcon: { foregroundImage: "./assets/adaptive-icon.png", backgroundColor: "#713AF4" },
      predictiveBackGestureEnabled: true,
    },
    plugins: [
      "expo-router",
      "expo-secure-store",
      "expo-web-browser",
      [
        "expo-splash-screen",
        { backgroundColor: "#713AF4", image: "./assets/icon.png", imageWidth: 150 },
      ],
      [
        "expo-build-properties",
        {
          android: {
            usesCleartextTraffic: process.env.VSHOP_ALLOW_HTTP === "true",
            ...(process.platform === "win32" ? { cmakeVersion: "3.31.6" } : {}),
          },
        },
      ],
    ],
  };
};
