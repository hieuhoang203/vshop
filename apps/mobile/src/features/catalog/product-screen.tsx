import { Alert, RefreshControl, ScrollView, Share, View } from "react-native";
import { router, useLocalSearchParams } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Share2, Store, ShieldCheck } from "../../components/icons";
import { ProductArt } from "../../components/product-art";
import { Button, EmptyState, IconAction, Loading, Txt, shared } from "../../components/ui";
import { colors, fonts, money } from "../../theme/tokens";
import { useSaved } from "../saved/saved-provider";
import { getProduct } from "./catalog-api";
import { ApiFailure } from "../../services/http-client";
export function ProductScreen() {
  const insets = useSafeAreaInsets();
  const { slug } = useLocalSearchParams<{ slug: string }>();
  const query = useQuery({
    queryKey: ["product", slug],
    queryFn: ({ signal }) => getProduct(slug, signal),
    retry: (count, error) => !(error instanceof ApiFailure && error.status === 404) && count < 1,
  });
  const saved = useSaved();
  if (query.isPending)
    return (
      <View style={shared.page}>
        <Loading />
      </View>
    );
  if (!query.data)
    return (
      <View style={shared.page}>
        <EmptyState
          title={
            query.error instanceof ApiFailure && query.error.status === 404
              ? "Món này hiện không còn trong danh mục"
              : "Chưa tải được sản phẩm"
          }
          message={query.error?.message}
          onRetry={() => void query.refetch()}
        />
        <Button title="Về khám phá" secondary onPress={() => router.replace("/")} />
      </View>
    );
  const product = query.data;
  const selected = saved.items.some((p) => p.id === product.id);
  async function share() {
    try {
      await Share.share({
        message: `${product.name} · ${money(product.price)}\nKhám phá trên VShop: vshop://products/${product.slug}`,
      });
    } catch {
      Alert.alert("Chưa chia sẻ được", "Vui lòng thử lại.");
    }
  }
  return (
    <ScrollView
      style={shared.page}
      contentContainerStyle={[shared.content, { paddingBottom: insets.bottom + 32 }]}
      refreshControl={
        <RefreshControl
          refreshing={query.isRefetching}
          onRefresh={() => void query.refetch()}
          tintColor={colors.purple}
        />
      }
    >
      <ProductArt
        imageKey={product.imageKey}
        label={product.name}
        style={{ width: "100%", aspectRatio: 1, borderRadius: 28 }}
      />
      <View style={shared.row}>
        <Txt style={{ color: colors.purple, flex: 1 }}>{product.categoryName}</Txt>
        <IconAction label="Chia sẻ sản phẩm" onPress={share}>
          <Share2 size={22} color={colors.purple} />
        </IconAction>
      </View>
      <Txt accessibilityRole="header" style={shared.title}>
        {product.name}
      </Txt>
      <View>
        <Txt style={{ color: colors.muted, fontSize: 12 }}>Giá tham khảo</Txt>
        <Txt style={{ fontFamily: fonts.bold, fontSize: 31, color: colors.purple }}>
          {money(product.price)}
        </Txt>
      </View>
      <View style={[shared.panel, { flexDirection: "row", alignItems: "center" }]}>
        <Store size={24} color={colors.purple} />
        <View style={{ flex: 1, gap: 5 }}>
          <Txt style={{ fontFamily: fonts.bold }}>{product.shopName}</Txt>
          <Txt style={shared.muted}>Thông tin gian hàng từ hệ thống</Txt>
        </View>
      </View>
      <View style={{ gap: 12 }}>
        <Txt style={shared.subheading}>Một chút về món này</Txt>
        <Txt style={{ lineHeight: 26 }}>{product.description}</Txt>
      </View>
      <View style={shared.panel}>
        <ShieldCheck color={colors.green} size={26} />
        <Txt style={shared.subheading}>
          {product.stock > 0 ? "Có trong danh mục" : "Tạm hết hàng"}
        </Txt>
        <Txt style={shared.muted}>
          VShop đang thử nghiệm. Chưa tiếp nhận đặt hàng hoặc thanh toán trên ứng dụng.
        </Txt>
      </View>
      {query.isError && (
        <Txt style={shared.error}>Chưa cập nhật được dữ liệu. Kéo xuống để thử lại.</Txt>
      )}
      <Button
        title={selected ? "Bỏ lưu món này" : "Lưu món này"}
        disabled={!saved.ready}
        onPress={() => saved.toggle(product)}
        secondary={selected}
      />
      <Txt style={{ color: colors.muted, fontSize: 12, textAlign: "center" }}>
        Danh sách lưu thuộc thiết bị này.
      </Txt>
    </ScrollView>
  );
}
