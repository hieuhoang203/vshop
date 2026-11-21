import { ScrollView, View } from "react-native";
import { router } from "expo-router";
import { Heart, Store, UserRound, ShieldCheck } from "../../components/icons";
import { useAuth } from "./auth-provider";
import { Button, Txt, shared } from "../../components/ui";
import { colors } from "../../theme/tokens";
export function AccountScreen() {
  const auth = useAuth();
  return (
    <ScrollView style={shared.page} contentContainerStyle={shared.content}>
      <Txt accessibilityRole="header" style={shared.title}>
        Một góc rất bạn.
      </Txt>
      <View style={[shared.panel, { backgroundColor: colors.purpleLight }]}>
        <UserRound size={36} color={colors.purple} />
        <Txt style={shared.subheading}>{auth.profile?.username ?? "Chào bạn, người có gu."}</Txt>
        <Txt style={shared.muted}>
          {auth.profile
            ? "Tài khoản của bạn trên VShop."
            : "Khám phá và lưu món ngay. Đăng nhập khi bạn muốn tạo gian hàng."}
        </Txt>
        {!auth.profile && (
          <>
            <Button title="Đăng nhập VShop" busy={auth.busy} onPress={() => void auth.signIn()} />
            <Button title="Tạo tài khoản" secondary onPress={() => router.push("/register")} />
          </>
        )}
      </View>
      {auth.error && (
        <View accessibilityLiveRegion="polite" style={shared.panel}>
          <Txt style={shared.error}>{auth.error}</Txt>
          <Button
            title="Thử khôi phục phiên"
            secondary
            onPress={() => void auth.restore()}
            busy={auth.busy}
          />
        </View>
      )}
      <View style={shared.panel}>
        <Heart color={colors.purple} size={24} />
        <Txt style={shared.subheading}>Gu riêng của bạn</Txt>
        <Txt style={shared.muted}>Món đã lưu được giữ trên thiết bị này, kể cả khi đăng xuất.</Txt>
        <Button title="Xem món đã lưu" secondary onPress={() => router.push("/saved")} />
      </View>
      <View style={shared.panel}>
        <Store color={colors.purple} size={24} />
        <Txt style={shared.subheading}>Từ người có gu đến người bán</Txt>
        <Txt style={shared.muted}>
          Tạo hồ sơ, theo dõi xét duyệt và bổ sung thông tin gian hàng.
        </Txt>
        <Button title="Gian hàng của tôi" secondary onPress={() => router.push("/seller")} />
      </View>
      {auth.profile?.realmAccess.roles.includes("ADMIN") && (
        <View style={shared.panel}>
          <ShieldCheck color={colors.purple} size={24} />
          <Txt style={shared.subheading}>Quản trị hệ thống</Txt>
          <Txt style={shared.muted}>
            Dùng cổng quản trị trên máy tính để xét duyệt và xem báo cáo đầy đủ. Ứng dụng hiện tập
            trung vào mua sắm và hồ sơ người bán.
          </Txt>
        </View>
      )}
      {auth.profile && (
        <Button title="Đăng xuất" secondary busy={auth.busy} onPress={() => void auth.signOut()} />
      )}
      <Txt style={{ color: colors.muted, fontSize: 12, textAlign: "center", paddingVertical: 10 }}>
        VShop 0.1 · Đang thử nghiệm{"\n"}Chưa tiếp nhận đơn hàng.
      </Txt>
    </ScrollView>
  );
}
