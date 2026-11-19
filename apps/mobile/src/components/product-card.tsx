import { Pressable, StyleSheet, View } from "react-native";
import { router } from "expo-router";
import { Heart, Plus } from "./icons";
import type { Product } from "@vshop/contracts";
import type { SavedProduct } from "../features/saved/saved-model";
import { useSaved } from "../features/saved/saved-provider";
import { colors, fonts, money } from "../theme/tokens";
import { ProductArt } from "./product-art";
import { IconAction, Txt } from "./ui";
export function ProductCard({
  product,
  snapshot = false,
}: {
  product: Product | SavedProduct;
  snapshot?: boolean;
}) {
  const saved = useSaved();
  const selected = saved.items.some((p) => p.id === product.id);
  const open = () => router.push(`/products/${product.slug}`);
  return (
    <View style={s.card}>
      <View>
        <Pressable
          onPress={open}
          accessibilityRole="button"
          accessibilityLabel={`Xem ${product.name}`}
        >
          <ProductArt imageKey={product.imageKey} style={s.art} />
        </Pressable>
        <View style={s.save}>
          <IconAction
            label={`${selected ? "Bỏ lưu" : "Lưu"} ${product.name}`}
            selected={selected}
            disabled={!saved.ready}
            onPress={() => saved.toggle(product)}
          >
            <Heart size={20} color={colors.purple} fill={selected ? colors.purple : "none"} />
          </IconAction>
        </View>
      </View>
      <View style={s.copy}>
        <Pressable onPress={open} accessibilityRole="button" accessibilityLabel={product.name}>
          {"categoryName" in product && (
            <Txt numberOfLines={1} style={s.category}>
              {product.categoryName}
            </Txt>
          )}
          <Txt numberOfLines={2} style={s.name}>
            {product.name}
          </Txt>
        </Pressable>
        <Txt style={s.note}>{snapshot ? "Giá khi lưu" : "Giá tham khảo"}</Txt>
        <View style={s.price}>
          <Txt style={s.amount}>{money(product.price)}</Txt>
          <IconAction label={`Mở thông tin ${product.name}`} onPress={open}>
            <Plus size={20} color={colors.purple} />
          </IconAction>
        </View>
      </View>
    </View>
  );
}
const s = StyleSheet.create({
  card: {
    flex: 1,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: colors.line,
    overflow: "hidden",
    backgroundColor: colors.white,
  },
  art: { aspectRatio: 1, width: "100%" },
  save: { position: "absolute", top: 7, right: 7, backgroundColor: "#FFFFFFE8", borderRadius: 24 },
  copy: { padding: 12, flex: 1 },
  category: { fontSize: 11, color: colors.muted, marginBottom: 7 },
  name: { fontFamily: fonts.bold, fontSize: 14, minHeight: 38 },
  note: { fontSize: 11, color: colors.muted, marginTop: 10 },
  price: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginRight: -8,
  },
  amount: { fontFamily: fonts.bold, fontSize: 16, color: colors.purple, flexShrink: 1 },
});
