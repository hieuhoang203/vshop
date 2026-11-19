import { FlatList, RefreshControl, View, useWindowDimensions } from "react-native";
import type { ReactElement } from "react";
import type { Product } from "@vshop/contracts";
import { Button, EmptyState, Loading, Txt } from "../../components/ui";
import { ProductCard } from "../../components/product-card";
import { colors } from "../../theme/tokens";
export function CatalogGrid({
  items,
  header,
  loading,
  error,
  refreshing,
  onRefresh,
  hasMore,
  loadMore,
  fetchingMore,
  moreError,
  footer,
}: {
  items: Product[];
  header: ReactElement;
  loading: boolean;
  error?: Error | null;
  refreshing: boolean;
  onRefresh(): void;
  hasMore?: boolean;
  loadMore(): void;
  fetchingMore: boolean;
  moreError?: boolean;
  footer?: ReactElement;
}) {
  const columns = useWindowDimensions().width >= 700 ? 3 : 2;
  return (
    <FlatList
      key={columns}
      data={items}
      numColumns={columns}
      keyExtractor={(p) => p.id}
      contentContainerStyle={{ padding: 16, paddingBottom: 30, gap: 14 }}
      columnWrapperStyle={{ gap: 12 }}
      ListHeaderComponent={header}
      keyboardShouldPersistTaps="handled"
      renderItem={({ item }) => (
        <View style={{ flex: 1 / columns }}>
          <ProductCard product={item} />
        </View>
      )}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={onRefresh}
          tintColor={colors.purple}
          colors={[colors.purple]}
        />
      }
      ListEmptyComponent={
        loading ? (
          <Loading />
        ) : error ? (
          <EmptyState title="Chưa kết nối được VShop" message={error.message} onRetry={onRefresh} />
        ) : (
          <EmptyState
            title="Chưa gặp món hợp gu"
            message="Thử từ khóa khác hoặc thay đổi bộ lọc nhé."
          />
        )
      }
      ListFooterComponent={
        <View style={{ paddingTop: 14, gap: 12 }}>
          {items.length > 0 && error && (
            <Txt style={{ color: colors.danger }}>
              Chưa cập nhật được dữ liệu. Kéo xuống để thử lại.
            </Txt>
          )}
          {hasMore && (
            <Button
              secondary
              title={moreError ? "Thử tải thêm" : "Khám phá thêm"}
              onPress={loadMore}
              busy={fetchingMore}
            />
          )}
          {!loading && items.length > 0 && footer}
        </View>
      }
    />
  );
}
