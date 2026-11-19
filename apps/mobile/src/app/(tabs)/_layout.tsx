import { Tabs } from "expo-router";
import { Compass, Search, Heart, UserRound } from "../../components/icons";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { colors, fonts } from "../../theme/tokens";
import { useSaved } from "../../features/saved/saved-provider";
export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  const saved = useSaved();
  return (
    <SafeAreaView
      style={{ flex: 1, backgroundColor: colors.white }}
      edges={["top", "left", "right"]}
    >
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarActiveTintColor: colors.purple,
          tabBarInactiveTintColor: colors.muted,
          tabBarLabelStyle: { fontFamily: fonts.bold, fontSize: 11, lineHeight: 18, marginTop: 2 },
          tabBarStyle: {
            borderTopColor: colors.line,
            height: 64 + insets.bottom,
            paddingBottom: insets.bottom,
            paddingTop: 4,
          },
          tabBarItemStyle: { paddingTop: 3, paddingBottom: 3 },
        }}
      >
        <Tabs.Screen
          name="index"
          options={{
            title: "Khám phá",
            tabBarIcon: ({ color, size }) => <Compass color={color} size={size} />,
          }}
        />
        <Tabs.Screen
          name="search"
          options={{
            title: "Tìm kiếm",
            tabBarIcon: ({ color, size }) => <Search color={color} size={size} />,
          }}
        />
        <Tabs.Screen
          name="saved"
          options={{
            title: "Đã lưu",
            tabBarBadge: saved.items.length || undefined,
            tabBarBadgeStyle: { backgroundColor: colors.lime, color: colors.ink },
            tabBarIcon: ({ color, size }) => <Heart color={color} size={size} />,
          }}
        />
        <Tabs.Screen
          name="account"
          options={{
            title: "Tài khoản",
            tabBarIcon: ({ color, size }) => <UserRound color={color} size={size} />,
          }}
        />
      </Tabs>
    </SafeAreaView>
  );
}
