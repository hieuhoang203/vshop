import { createContext, useContext, useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { request, ApiFailure } from "../../services/http-client";
import { login, session } from "./oauth";
import { SessionExpired } from "./token-session";

type Profile = { subject: string; username: string; realmAccess: { roles: string[] } };
type Auth = {
  profile: Profile | null;
  busy: boolean;
  error: string;
  signIn(): Promise<void>;
  signOut(): Promise<void>;
  restore(): Promise<void>;
  authorized<T>(path: string, options?: RequestInit): Promise<T>;
};
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  async function loadProfile() {
    const accessToken = await session.accessToken();
    const next = await request<Profile>("/me", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    setProfile(next);
  }
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (cause) {
      if (
        cause instanceof SessionExpired ||
        (cause instanceof ApiFailure && cause.status === 401)
      ) {
        setProfile(null);
        await session.signOut().catch(() => {});
        queryClient.removeQueries({ queryKey: ["private"] });
      }
      setError(cause instanceof Error ? cause.message : "Chưa thể đăng nhập.");
    } finally {
      setBusy(false);
    }
  }
  const restore = () =>
    run(async () => {
      if (await session.restore()) await loadProfile();
    });
  useEffect(() => {
    void restore();
  }, []);
  const signIn = () =>
    run(async () => {
      if (await login()) await loadProfile();
    });
  const signOut = () =>
    run(async () => {
      setProfile(null);
      queryClient.removeQueries({ queryKey: ["private"] });
      await session.signOut();
    });
  async function authorized<T>(path: string, options: RequestInit = {}): Promise<T> {
    try {
      const token = await session.accessToken();
      return await request<T>(path, {
        ...options,
        headers: { ...options.headers, Authorization: `Bearer ${token}` },
      });
    } catch (cause) {
      if (
        cause instanceof SessionExpired ||
        (cause instanceof ApiFailure && cause.status === 401)
      ) {
        setProfile(null);
        queryClient.removeQueries({ queryKey: ["private"] });
        await session.signOut().catch(() => {});
      }
      throw cause;
    }
  }
  return (
    <Context.Provider value={{ profile, busy, error, signIn, signOut, restore, authorized }}>
      {children}
    </Context.Provider>
  );
}
export function useAuth() {
  const value = useContext(Context);
  if (!value) throw new Error("AuthProvider missing");
  return value;
}
