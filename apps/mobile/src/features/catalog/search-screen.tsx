import { useEffect, useState } from "react";
import { ScrollView, TextInput, View } from "react-native";
import { useLocalSearchParams } from "expo-router";
import { Search, SlidersHorizontal } from "../../components/icons";
import { Button, Chip, IconAction, Sheet, Txt, shared } from "../../components/ui";
import { colors } from "../../theme/tokens";
import { emptyFilter, type CatalogFilter } from "./catalog-api";
import { useCatalog } from "./use-catalog";
import { CatalogGrid } from "./catalog-grid";

export function SearchScreen() {
  const params = useLocalSearchParams<{ category?: string }>();
  const [text, setText] = useState("");
  const [filter, setFilter] = useState<CatalogFilter>({
    ...emptyFilter,
    category: params.category ?? "",
  });
  const [draft, setDraft] = useState(filter);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    setFilter((prev) => ({
      ...prev,
      category: typeof params.category === "string" ? params.category : "",
    }));
  }, [params.category]);
  useEffect(() => {
    const timer = setTimeout(() => setFilter((prev) => ({ ...prev, q: text.trim() })), 350);
    return () => clearTimeout(timer);
  }, [text]);
  const { products, categories, items } = useCatalog(filter);
  const priceRanges = [
    { name: "Mọi mức giá", min: undefined, max: undefined },
    { name: "Đến 200k", min: 0, max: 200000 },
    { name: "200–500k", min: 200000, max: 500000 },
    { name: "Từ 500k", min: 500000, max: undefined },
  ];
  const sortOptions = [
    { name: "Mới nhất", value: "newest" },
    { name: "Giá thấp đến cao", value: "price-asc" },
    { name: "Giá cao đến thấp", value: "price-desc" },
  ] as const;
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
        header={
          <View style={{ gap: 18, paddingBottom: 7 }}>
            <Txt accessibilityRole="header" style={shared.title}>
              Tìm đúng món. Đúng gu.
            </Txt>
            <View style={[shared.row, { gap: 8 }]}>
              <View
                style={[
                  shared.input,
                  { flex: 1, flexDirection: "row", alignItems: "center", gap: 9 },
                ]}
              >
                <Search size={18} color={colors.muted} />
                <TextInput
                  accessibilityLabel="Tìm sản phẩm"
                  placeholder="Đèn bàn, Mori, tai nghe…"
                  placeholderTextColor={colors.muted}
                  value={text}
                  onChangeText={setText}
                  maxLength={100}
                  returnKeyType="search"
                  onSubmitEditing={() => setFilter((prev) => ({ ...prev, q: text.trim() }))}
                  style={{ flex: 1, minHeight: 48, color: colors.ink }}
                />
              </View>
              <IconAction
                label="Lọc và sắp xếp"
                onPress={() => {
                  setDraft(filter);
                  setOpen(true);
                }}
              >
                <SlidersHorizontal color={colors.purple} size={24} />
              </IconAction>
            </View>
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={{ gap: 8 }}
            >
              <Chip
                label="Tất cả"
                selected={!filter.category}
                onPress={() => setFilter((prev) => ({ ...prev, category: "" }))}
              />
              {categories.data?.map((c) => (
                <Chip
                  key={c.id}
                  label={c.name}
                  selected={filter.category === c.slug}
                  onPress={() => setFilter((prev) => ({ ...prev, category: c.slug }))}
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
              <Txt style={{ color: colors.muted, fontSize: 12 }}>
                {products.data
                  ? `${products.data.pages[0]?.total ?? 0} sản phẩm phù hợp`
                  : "Đang tìm món hợp gu…"}
              </Txt>
              <Txt style={{ color: colors.purple, fontSize: 12 }}>
                {sortOptions.find((option) => option.value === filter.sort)?.name}
              </Txt>
            </View>
            {(filter.minPrice !== undefined || filter.maxPrice !== undefined) && (
              <Button
                secondary
                title="Bỏ lọc giá"
                onPress={() =>
                  setFilter((prev) => ({ ...prev, minPrice: undefined, maxPrice: undefined }))
                }
              />
            )}
          </View>
        }
      />
      <Sheet open={open} onClose={() => setOpen(false)} title="Khám phá theo cách bạn muốn">
        <View style={{ gap: 12 }}>
          <Txt style={shared.subheading}>Ngân sách hôm nay?</Txt>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
            {priceRanges.map((range) => (
              <Chip
                key={range.name}
                label={range.name}
                selected={draft.minPrice === range.min && draft.maxPrice === range.max}
                onPress={() =>
                  setDraft((prev) => ({ ...prev, minPrice: range.min, maxPrice: range.max }))
                }
              />
            ))}
          </View>
        </View>
        <View style={{ gap: 12 }}>
          <Txt style={shared.subheading}>Sắp xếp sản phẩm</Txt>
          {sortOptions.map((option) => (
            <Chip
              key={option.value}
              label={option.name}
              selected={draft.sort === option.value}
              onPress={() => setDraft((prev) => ({ ...prev, sort: option.value }))}
            />
          ))}
        </View>
        <Button
          title="Áp dụng"
          onPress={() => {
            setFilter(draft);
            setOpen(false);
          }}
        />
        <Button
          title="Đặt lại bộ lọc"
          secondary
          onPress={() => {
            setFilter({ ...emptyFilter, q: text.trim() });
            setOpen(false);
          }}
        />
      </Sheet>
    </View>
  );
}
