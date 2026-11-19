import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type TextProps,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { X, Sparkles } from "./icons";
import { colors, fonts } from "../theme/tokens";

export function Txt({ style, ...props }: TextProps) {
  return (
    <Text
      {...props}
      style={[
        s.text,
        style,
        StyleSheet.flatten(style)?.fontFamily === fonts.bold && { fontWeight: "700" },
      ]}
    />
  );
}
export function Button({
  title,
  onPress,
  secondary,
  busy,
  disabled,
  accessibilityLabel,
}: {
  title: string;
  onPress(): void;
  secondary?: boolean;
  busy?: boolean;
  disabled?: boolean;
  accessibilityLabel?: string;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityState={{ disabled: disabled || busy, busy }}
      onPress={onPress}
      disabled={disabled || busy}
      style={({ pressed }) => [
        s.button,
        secondary && s.secondary,
        (disabled || busy) && { opacity: 0.5 },
        pressed && { opacity: 0.8 },
      ]}
    >
      {busy && <ActivityIndicator color={secondary ? colors.purple : colors.white} />}
      <Txt style={[s.buttonText, secondary && { color: colors.purple }]}>{title}</Txt>
    </Pressable>
  );
}
export function IconAction({
  label,
  onPress,
  children,
  selected,
  disabled,
}: {
  label: string;
  onPress(): void;
  children: React.ReactNode;
  selected?: boolean;
  disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityLabel={label}
      accessibilityRole="button"
      accessibilityState={{ selected, disabled }}
      disabled={disabled}
      hitSlop={4}
      style={s.iconAction}
    >
      {children}
    </Pressable>
  );
}
export function Chip({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress(): void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[s.chip, selected && { backgroundColor: colors.ink, borderColor: colors.ink }]}
    >
      <Txt style={[s.chipText, selected && { color: colors.white }]}>{label}</Txt>
    </Pressable>
  );
}
export function EmptyState({
  title,
  message,
  onRetry,
}: {
  title: string;
  message?: string;
  onRetry?: () => void;
}) {
  return (
    <View style={s.empty}>
      <Sparkles size={32} color={colors.purple} />
      <Txt style={s.title}>{title}</Txt>
      {message && <Txt style={s.muted}>{message}</Txt>}
      {onRetry && <Button title="Thử lại" onPress={onRetry} secondary />}
    </View>
  );
}
export function Loading() {
  return (
    <View accessibilityLabel="Đang tải" style={s.empty}>
      <ActivityIndicator color={colors.purple} />
    </View>
  );
}
export function Sheet({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean;
  onClose(): void;
  title: string;
  children: React.ReactNode;
}) {
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={open} transparent animationType="slide" onRequestClose={onClose}>
      <View style={s.backdrop}>
        <Pressable
          style={StyleSheet.absoluteFill}
          onPress={onClose}
          accessibilityLabel="Đóng bộ lọc"
          accessibilityRole="button"
        />
        <View
          accessibilityViewIsModal
          style={[s.sheet, { paddingBottom: Math.max(insets.bottom, 20), maxHeight: "85%" }]}
        >
          <View style={s.row}>
            <Txt accessibilityRole="header" style={s.title}>
              {title}
            </Txt>
            <IconAction label="Đóng" onPress={onClose}>
              <X color={colors.ink} size={23} />
            </IconAction>
          </View>
          <ScrollView
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={{ gap: 20, paddingTop: 12 }}
          >
            {children}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}
export const shared = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.white },
  content: { padding: 20, paddingBottom: 32, gap: 20 },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 12 },
  title: { fontFamily: fonts.bold, fontSize: 25, letterSpacing: -0.8, color: colors.ink },
  subheading: { fontFamily: fonts.bold, fontSize: 19, letterSpacing: -0.5 },
  muted: { color: colors.muted, lineHeight: 23 },
  panel: { padding: 20, borderRadius: 22, backgroundColor: colors.surface, gap: 14 },
  input: {
    minHeight: 50,
    backgroundColor: colors.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.line,
    paddingHorizontal: 16,
    color: colors.ink,
    fontFamily: fonts.regular,
    fontSize: 15,
  },
  error: { color: colors.danger, lineHeight: 23 },
});
const s = StyleSheet.create({
  text: { fontFamily: fonts.regular, fontSize: 14, color: colors.ink },
  button: {
    minHeight: 50,
    borderRadius: 16,
    backgroundColor: colors.purple,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 20,
    paddingVertical: 13,
    gap: 9,
  },
  secondary: { backgroundColor: colors.purpleLight },
  buttonText: { color: colors.white, fontFamily: fonts.bold, textAlign: "center" },
  iconAction: {
    minWidth: 44,
    minHeight: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
  },
  chip: {
    minHeight: 44,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.line,
    paddingHorizontal: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  chipText: { fontSize: 13 },
  empty: { alignItems: "center", justifyContent: "center", padding: 30, gap: 16 },
  title: { fontFamily: fonts.bold, fontSize: 20, textAlign: "center" },
  muted: { color: colors.muted, textAlign: "center", lineHeight: 23 },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 10 },
  backdrop: { flex: 1, backgroundColor: "#20122D80", justifyContent: "flex-end" },
  sheet: {
    backgroundColor: colors.white,
    paddingHorizontal: 22,
    paddingTop: 15,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
  },
});
