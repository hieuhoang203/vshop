import { SessionExpired, TokenSession, type Tokens } from "../src/features/auth/token-session";
function setup() {
  let stored: string | null = null;
  const store = {
    read: jest.fn(async () => stored),
    write: jest.fn(async (value: string | null) => {
      stored = value;
    }),
  };
  const oauth = {
    refresh: jest.fn(async (): Promise<Tokens> => ({
      accessToken: "new-access",
      refreshToken: "rotated",
      expiresAt: 90000,
    })),
    revoke: jest.fn(async () => {}),
  };
  const session = new TokenSession(store, oauth, () => 1000);
  return { store, oauth, session };
}
test("refresh happens once for parallel requests and stores only rotated refresh credentials", async () => {
  const { session, oauth, store } = setup();
  await session.accept({ accessToken: "expired", refreshToken: "original", expiresAt: 0 });
  expect(await Promise.all([session.accessToken(), session.accessToken()])).toEqual([
    "new-access",
    "new-access",
  ]);
  expect(oauth.refresh).toHaveBeenCalledTimes(1);
  expect(store.write.mock.calls.map((call) => call[0])).toEqual(["original", "rotated"]);
});
test("logout during refresh cannot resurrect credentials", async () => {
  const { session, oauth, store } = setup();
  let release!: (value: Tokens) => void;
  oauth.refresh.mockImplementation(
    () =>
      new Promise((resolve) => {
        release = resolve;
      }),
  );
  await session.accept({ accessToken: "expired", refreshToken: "original", expiresAt: 0 });
  const pending = session.accessToken();
  await session.signOut();
  release({ accessToken: "late-access", refreshToken: "late-refresh", expiresAt: 90000 });
  await expect(pending).rejects.toBeInstanceOf(SessionExpired);
  expect(await store.read()).toBeNull();
  await expect(session.accessToken()).rejects.toBeInstanceOf(SessionExpired);
});
test("invalid refresh clears the persisted session", async () => {
  const { session, oauth, store } = setup();
  await session.accept({ accessToken: "expired", refreshToken: "original", expiresAt: 0 });
  oauth.refresh.mockRejectedValue(new SessionExpired("expired"));
  await expect(session.accessToken()).rejects.toBeInstanceOf(SessionExpired);
  expect(await store.read()).toBeNull();
});
test("network failure preserves refresh credentials for retry", async () => {
  const { session, oauth, store } = setup();
  await session.accept({ accessToken: "expired", refreshToken: "original", expiresAt: 0 });
  oauth.refresh.mockRejectedValueOnce(new Error("offline"));
  await expect(session.accessToken()).rejects.toThrow("offline");
  expect(await store.read()).toBe("original");
  expect(await session.accessToken()).toBe("new-access");
});
test("secure storage failure does not publish an authenticated token", async () => {
  const { session, store } = setup();
  store.write.mockRejectedValueOnce(new Error("storage unavailable"));
  await expect(
    session.accept({ accessToken: "access", refreshToken: "refresh", expiresAt: 90000 }),
  ).rejects.toThrow("storage unavailable");
  await expect(session.accessToken()).rejects.toBeInstanceOf(SessionExpired);
});
test("restore refreshes stored credentials without saving access tokens", async () => {
  const { session, store } = setup();
  await store.write("restored-refresh");
  expect(await session.restore()).toBe(true);
  expect(await session.accessToken()).toBe("new-access");
});
