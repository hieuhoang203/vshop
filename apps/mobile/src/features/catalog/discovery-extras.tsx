import { useState } from "react";
import { View } from "react-native";
import { router } from "expo-router";
import { ProductArt } from "../../components/product-art";
import { Button, Chip, Txt, shared } from "../../components/ui";
const moods = [
  { label: "Tập trung", title: "Bật nhịp riêng.", image: "headphones", category: "goc-lam-viec" },
  { label: "Sống chill", title: "Chậm lại một chút.", image: "mug", category: "nha-cua" },
  { label: "Đi muôn nơi", title: "Mang gu đi cùng.", image: "tote", category: "phu-kien" },
] as const;
export function DiscoveryExtras() {
  const [mood, setMood] = useState(0);
  const selected = moods[mood]!;
  return (
    <View style={[shared.panel, { marginTop: 14, padding: 16 }]}>
      <View style={shared.row}>
        <View style={{ flex: 1, gap: 6 }}>
          <Txt style={shared.subheading}>Khám phá thêm</Txt>
          <Txt style={shared.muted}>{selected.title}</Txt>
        </View>
        <ProductArt imageKey={selected.image} style={{ width: 64, height: 64, borderRadius: 14 }} />
      </View>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
        {moods.map((item, index) => (
          <Chip
            key={item.label}
            label={item.label}
            selected={index === mood}
            onPress={() => setMood(index)}
          />
        ))}
      </View>
      <Button
        title={`Khám phá ${selected.label.toLowerCase()}`}
        secondary
        onPress={() =>
          router.push({ pathname: "/search", params: { category: selected.category } })
        }
      />
    </View>
  );
}
