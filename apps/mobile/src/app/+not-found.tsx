import { router } from "expo-router";
import { View } from "react-native";
import { Button, EmptyState, shared } from "../components/ui";
export default function NotFound() {
  return (
    <View style={shared.page}>
      <EmptyState title="Chưa tìm thấy góc này" />
      <Button title="Về khám phá" onPress={() => router.replace("/")} />
    </View>
  );
}
