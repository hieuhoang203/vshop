import { FlatList, View, useWindowDimensions } from "react-native";
import { router } from "expo-router";
import { useSaved } from "./saved-provider";
import { ProductCard } from "../../components/product-card";
import { Button, EmptyState, Loading, Txt, shared } from "../../components/ui";
export function SavedScreen() {
  const { items, ready } = useSaved();
  const columns = useWindowDimensions().width >= 700 ? 3 : 2;
  return (
    <View style={shared.page}>
      <FlatList
        key={columns}
        data={items}
        numColumns={columns}
        keyExtractor={(p) => p.id}
        contentContainerStyle={{ padding: 16, paddingBottom: 32, gap: 14 }}
        columnWrapperStyle={{ gap: 12 }}
        ListHeaderComponent={
          <View style={{ gap: 12, paddingBottom: 10 }}>
            <Txt accessibilityRole="header" style={shared.title}>
              Món hợp gu, giữ ở đây.
            </Txt>
            <Txt style={shared.muted}>
              {items.length} món trên thiết bị này. Giá hiển thị là giá khi lưu; mở chi tiết để xem
              giá và trạng thái hiện tại.
            </Txt>
          </View>
        }
        renderItem={({ item }) => (
          <View style={{ flex: 1 / columns }}>
            <ProductCard product={item} snapshot />
          </View>
        )}
        ListEmptyComponent={
          ready ? (
            <View>
              <EmptyState
                title="Gu của bạn đang chờ được khám phá"
                message="Chạm trái tim ở những món bạn thích để lưu lại."
              />
              <Button title="Đi tìm món hợp gu" onPress={() => router.push("/")} />
            </View>
          ) : (
            <Loading />
          )
        }
      />
    </View>
  );
}
