import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import { router } from "expo-router";
import { Search, Shuffle } from "../../components/icons";
import { Txt, shared, Chip, Button, IconAction } from "../../components/ui";
import { colors } from "../../theme/tokens";
import { useCatalog } from "./use-catalog";
import { emptyFilter } from "./catalog-api";
import { CatalogGrid } from "./catalog-grid";
import { DiscoveryExtras } from "./discovery-extras";
export function DiscoverScreen() {
  const { products, categories, items } = useCatalog(emptyFilter);
  function surprise() {
    const available = items.filter((p) => p.stock > 0);
    const pool = available.length ? available : items;
    const product = pool[Math.floor(Math.random() * pool.length)];
    if (product) router.push("/products/" + product.slug);
  }
  return (
    <View style={shared.page}>
      <CatalogGrid
        items={items}
        loading={products.isPending}
        error={products.error}
        refreshing={products.isRefetching && !products.isFetchingNextPage}
        onRefresh={() => {
          void products.refetch();
          void categories.refetch();
        }}
        hasMore={products.hasNextPage}
        fetchingMore={products.isFetchingNextPage}
        moreError={products.isFetchNextPageError}
        loadMore={() => void products.fetchNextPage()}
        footer={<DiscoveryExtras />}
        header={
          <View style={{ gap: 10, paddingBottom: 4 }}>
            <View style={[shared.row, { gap: 8 }]}>
              <Pressable
                style={s.search}
                onPress={() => router.push("/search")}
                accessibilityRole="button"
                accessibilityLabel="Tìm sản phẩm"
              >
                <Search size={20} color={colors.muted} />
                <Txt numberOfLines={1} style={{ color: colors.muted, flexShrink: 1 }}>
                  Tìm sản phẩm trên VShop
                </Txt>
              </Pressable>
              <IconAction label="Chọn giùm mình" onPress={surprise} disabled={!items.length}>
                <Shuffle color={colors.purple} size={23} />
              </IconAction>
            </View>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={{ gap: 8 }}
            >
              <Chip label="Tất cả" selected onPress={() => router.push("/search")} />
              {categories.data?.map((category) => (
                <Chip
                  key={category.id}
                  label={category.name}
                  selected={false}
                  onPress={() =>
                    router.push({ pathname: "/search", params: { category: category.slug } })
                  }
                />
              ))}
            </ScrollView>
            {categories.isError && (
              <Button
                title="Thử tải danh mục"
                secondary
                onPress={() => void categories.refetch()}
              />
            )}
            <View style={shared.row}>
              <Txt accessibilityRole="header" style={shared.subheading}>
                Sản phẩm mới
              </Txt>
              <Txt style={{ color: colors.muted, fontSize: 12 }}>
                {products.data ? (products.data.pages[0]?.total ?? 0) + " sản phẩm" : "Đang tải…"}
              </Txt>
            </View>
          </View>
        }
      />
    </View>
  );
}
const s = StyleSheet.create({
  search: {
    flex: 1,
    minHeight: 46,
    borderRadius: 12,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.line,
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 14,
    gap: 10,
  },
});
