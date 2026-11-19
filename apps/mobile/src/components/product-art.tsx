import { Image } from "expo-image";
import { View, type StyleProp, type ViewStyle } from "react-native";
import { Package } from "./icons";
import { colors } from "../theme/tokens";
const art: Record<string, number> = {
  lamp: require("../../assets/products/lamp.png"),
  tote: require("../../assets/products/tote.png"),
  mug: require("../../assets/products/mug.png"),
  headphones: require("../../assets/products/headphones.png"),
  bottle: require("../../assets/products/bottle.png"),
  notebook: require("../../assets/products/notebook.png"),
};
const backgrounds: Record<string, string> = {
  lamp: "#EEE8FA",
  tote: "#F4EBDD",
  mug: "#E8F1DD",
  headphones: "#E5EBF4",
  bottle: "#E8E7FC",
  notebook: "#FBE4EA",
};
export function ProductArt({
  imageKey,
  style,
  label,
}: {
  imageKey: string;
  style?: StyleProp<ViewStyle>;
  label?: string;
}) {
  return (
    <View
      style={[
        {
          backgroundColor: backgrounds[imageKey] ?? colors.surface,
          alignItems: "center",
          justifyContent: "center",
          overflow: "hidden",
        },
        style,
      ]}
    >
      {art[imageKey] ? (
        <Image
          source={art[imageKey]}
          style={{ width: "100%", height: "100%" }}
          contentFit="contain"
          accessibilityLabel={label}
          accessible={!!label}
        />
      ) : (
        <Package color={colors.muted} size={50} />
      )}
    </View>
  );
}
