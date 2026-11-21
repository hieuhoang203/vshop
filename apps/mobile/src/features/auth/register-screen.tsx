import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { router } from "expo-router";
import { ApiFailure, request } from "../../services/http-client";
import { Button, Txt, shared } from "../../components/ui";
import { colors } from "../../theme/tokens";
import { useAuth } from "./auth-provider";
import {
  passwordChecks,
  validateRegister,
  type RegisterErrors,
  type RegisterFields,
} from "./register-model";

const empty: RegisterFields = {
  fullName: "",
  email: "",
  password: "",
  confirm: "",
  acceptTerms: false,
};

/** Buyer sign-up on the phone. The API repeats every rule; this only gives quick feedback. */
export function RegisterScreen() {
  const auth = useAuth();
  const [fields, setFields] = useState<RegisterFields>(empty);
  const [showErrors, setShowErrors] = useState(false);
  const [serverError, setServerError] = useState("");
  const [emailTaken, setEmailTaken] = useState(false);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  const errors: RegisterErrors = {
    ...validateRegister(fields),
    ...(emailTaken
      ? { email: "Email này đã được đăng ký. Hãy đăng nhập hoặc dùng email khác." }
      : {}),
  };
  const set = <K extends keyof RegisterFields>(key: K, value: RegisterFields[K]) => {
    setFields((f) => ({ ...f, [key]: value }));
    if (key === "email") setEmailTaken(false);
  };

  async function submit() {
    setShowErrors(true);
    setServerError("");
    if (Object.keys(validateRegister(fields)).length) return;
    setBusy(true);
    try {
      await request<void>("/auth/register", {
        method: "POST",
        body: JSON.stringify({
          fullName: fields.fullName,
          email: fields.email.trim(),
          password: fields.password,
          acceptTerms: fields.acceptTerms,
        }),
      });
      setFields(empty);
      setDone(true);
    } catch (error) {
      if (error instanceof ApiFailure && error.status === 409) setEmailTaken(true);
      else setServerError(error instanceof Error ? error.message : "Không thể tạo tài khoản.");
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <View style={[shared.page, shared.content]}>
        <View style={[shared.panel, { backgroundColor: colors.purpleLight }]}>
          <Txt accessibilityRole="header" style={shared.title}>
            Tạo tài khoản thành công!
          </Txt>
          <Txt style={shared.muted}>Đăng nhập để bắt đầu mua sắm trên VShop.</Txt>
          <Button
            title="Đăng nhập ngay"
            busy={auth.busy}
            onPress={() => {
              void auth.signIn();
              router.back();
            }}
          />
        </View>
      </View>
    );
  }

  const field = (
    key: "fullName" | "email" | "password" | "confirm",
    label: string,
    props: React.ComponentProps<typeof TextInput>,
  ) => (
    <View style={{ gap: 6 }}>
      <Txt style={{ fontWeight: "700" }}>{label}</Txt>
      <TextInput
        {...props}
        value={fields[key]}
        onChangeText={(value) => set(key, value)}
        accessibilityLabel={label}
        style={[shared.input, showErrors && errors[key] ? { borderColor: colors.danger } : null]}
        placeholderTextColor={colors.muted}
      />
      {showErrors && errors[key] && (
        <Txt accessibilityLiveRegion="polite" style={shared.error}>
          {errors[key]}
        </Txt>
      )}
    </View>
  );

  return (
    <KeyboardAvoidingView
      style={{ flex: 1 }}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <ScrollView
        style={shared.page}
        contentContainerStyle={shared.content}
        keyboardShouldPersistTaps="handled"
      >
        <Txt accessibilityRole="header" style={shared.title}>
          Đăng ký tài khoản
        </Txt>
        {field("fullName", "Họ và tên", { autoComplete: "name", maxLength: 60 })}
        {field("email", "Email", {
          autoComplete: "email",
          keyboardType: "email-address",
          autoCapitalize: "none",
          maxLength: 254,
        })}
        {field("password", "Mật khẩu", {
          secureTextEntry: true,
          autoComplete: "new-password",
          maxLength: 64,
        })}
        <View style={{ gap: 2 }}>
          {passwordChecks(fields.password).map((c) => (
            <Txt key={c.id} style={{ color: c.ok ? colors.green : colors.muted, fontSize: 13 }}>
              {c.ok ? "✓" : "○"} {c.label}
            </Txt>
          ))}
        </View>
        {field("confirm", "Nhập lại mật khẩu", {
          secureTextEntry: true,
          autoComplete: "new-password",
          maxLength: 64,
        })}
        <Pressable
          accessibilityRole="checkbox"
          accessibilityState={{ checked: fields.acceptTerms }}
          accessibilityLabel="Tôi đồng ý với điều khoản sử dụng và chính sách bảo mật"
          onPress={() => set("acceptTerms", !fields.acceptTerms)}
          style={{ flexDirection: "row", gap: 10, alignItems: "flex-start" }}
        >
          <Txt style={{ fontSize: 20, color: colors.purple }}>{fields.acceptTerms ? "☑" : "☐"}</Txt>
          <Txt style={{ flex: 1 }}>
            Tôi đã đọc và đồng ý với điều khoản sử dụng và chính sách bảo mật của VShop.
          </Txt>
        </Pressable>
        {showErrors && errors.acceptTerms && (
          <Txt accessibilityLiveRegion="polite" style={shared.error}>
            {errors.acceptTerms}
          </Txt>
        )}
        {serverError !== "" && (
          <Txt accessibilityLiveRegion="polite" style={shared.error}>
            {serverError}
          </Txt>
        )}
        <Button title="Tạo tài khoản" busy={busy} onPress={() => void submit()} />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
