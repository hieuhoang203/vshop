import { useEffect, useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  RefreshControl,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import type { Shop } from "@vshop/contracts";
import { useAuth } from "../auth/auth-provider";
import { Button, EmptyState, Loading, Txt, shared } from "../../components/ui";
import { colors } from "../../theme/tokens";
export function MerchantScreen() {
  const insets = useSafeAreaInsets();
  const auth = useAuth();
  const client = useQueryClient();
  const key = ["private", auth.profile?.subject, "shops"];
  const query = useQuery({
    queryKey: key,
    queryFn: () => auth.authorized<Shop[]>("/seller/shops"),
    enabled: !!auth.profile,
    staleTime: 0,
  });
  const shop = query.data?.[0];
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [validation, setValidation] = useState("");
  useEffect(() => {
    setName(shop?.name ?? "");
    setDescription(shop?.description ?? "");
  }, [shop?.id, shop?.description, shop?.name]);
  const mutation = useMutation({
    mutationFn: () =>
      auth.authorized<Shop>(shop ? `/seller/shops/${shop.id}` : "/seller/shops", {
        method: shop ? "PUT" : "POST",
        body: JSON.stringify({ name: name.trim(), description: description.trim() }),
      }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: key });
    },
    retry: false,
  });
  function submit() {
    if (name.trim().length < 3 || description.trim().length < 20) {
      setValidation("Tên shop cần từ 3–100 ký tự; giới thiệu cần từ 20–2000 ký tự.");
      return;
    }
    setValidation("");
    mutation.mutate();
  }
  if (!auth.profile)
    return (
      <View style={shared.page}>
        <EmptyState
          title="Bắt đầu một gian hàng của riêng bạn"
          message="Đăng nhập để gửi hồ sơ và theo dõi kết quả xét duyệt."
        />
        <View style={{ padding: 20 }}>
          <Button title="Đăng nhập VShop" busy={auth.busy} onPress={() => void auth.signIn()} />
          {auth.error && <Txt style={shared.error}>{auth.error}</Txt>}
        </View>
      </View>
    );
  if (query.isPending)
    return (
      <View style={shared.page}>
        <Loading />
      </View>
    );
  if (query.isError)
    return (
      <View style={shared.page}>
        <EmptyState
          title="Chưa tải được hồ sơ"
          message={query.error.message}
          onRetry={() => void query.refetch()}
        />
      </View>
    );
  const editable = !shop || shop.status === "REJECTED";
  return (
    <KeyboardAvoidingView
      style={shared.page}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
      keyboardVerticalOffset={95}
    >
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={[shared.content, { paddingBottom: insets.bottom + 32 }]}
        refreshControl={
          <RefreshControl
            refreshing={query.isRefetching}
            onRefresh={() => void query.refetch()}
            tintColor={colors.purple}
          />
        }
      >
        <Txt accessibilityRole="header" style={shared.title}>
          {shop ? shop.name : "Mang gu của bạn đến VShop."}
        </Txt>
        {shop && (
          <View style={shared.panel}>
            <Txt style={shared.subheading}>
              {shop.status === "SUBMITTED"
                ? "Đang xét duyệt"
                : shop.status === "APPROVED"
                  ? "Gian hàng đã được duyệt"
                  : "Cần bổ sung hồ sơ"}
            </Txt>
            <Txt style={shared.muted}>
              {shop.status === "SUBMITTED"
                ? "Hồ sơ đã được lưu. VShop sẽ xem xét trước khi gian hàng hoạt động."
                : shop.status === "APPROVED"
                  ? "Bạn có thể theo dõi hồ sơ tại đây. Quản lý sản phẩm và đơn hàng chưa được triển khai."
                  : shop.rejectionReason}
            </Txt>
          </View>
        )}
        {editable && (
          <View style={{ gap: 14 }}>
            <Txt style={shared.subheading}>{shop ? "Bổ sung thông tin" : "Hồ sơ gian hàng"}</Txt>
            <Txt>Tên gian hàng</Txt>
            <TextInput
              accessibilityLabel="Tên gian hàng"
              value={name}
              onChangeText={setName}
              maxLength={100}
              style={shared.input}
              placeholder="Ví dụ: Mộc Studio"
              placeholderTextColor={colors.muted}
              editable={!mutation.isPending}
            />
            <Txt>Giới thiệu gian hàng</Txt>
            <TextInput
              accessibilityLabel="Giới thiệu gian hàng"
              value={description}
              onChangeText={setDescription}
              maxLength={2000}
              multiline
              textAlignVertical="top"
              style={[shared.input, { minHeight: 150, paddingTop: 15 }]}
              placeholder="Giới thiệu sản phẩm và câu chuyện của bạn (ít nhất 20 ký tự)"
              placeholderTextColor={colors.muted}
              editable={!mutation.isPending}
            />
            {(validation || mutation.error) && (
              <Txt accessibilityLiveRegion="polite" style={shared.error}>
                {validation || mutation.error?.message}
              </Txt>
            )}
            {mutation.isError && (
              <Button
                secondary
                title="Kiểm tra trạng thái hồ sơ"
                onPress={() => void query.refetch()}
              />
            )}
            <Button
              title={shop ? "Gửi lại để xét duyệt" : "Gửi hồ sơ gian hàng"}
              onPress={submit}
              busy={mutation.isPending}
              disabled={query.isRefetching}
            />
            <Txt style={{ color: colors.muted, fontSize: 12 }}>
              Nếu kết nối gián đoạn sau khi gửi, kiểm tra trạng thái trước khi gửi lại.
            </Txt>
          </View>
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
