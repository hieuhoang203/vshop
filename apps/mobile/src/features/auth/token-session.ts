export type Tokens = { accessToken: string; refreshToken: string; expiresAt: number };
export interface CredentialStore {
  read(): Promise<string | null>;
  write(value: string | null): Promise<void>;
}
export interface OAuthAdapter {
  refresh(token: string): Promise<Tokens>;
  revoke(token: string): Promise<void>;
}
export class SessionExpired extends Error {}

/** One refresh at a time; generation + ordered storage writes protect logout races. */
export class TokenSession {
  private tokens: Tokens | null = null;
  private generation = 0;
  private refreshing: Promise<string> | null = null;
  private writes: Promise<void> = Promise.resolve();
  constructor(
    private store: CredentialStore,
    private oauth: OAuthAdapter,
    private now = Date.now,
  ) {}
  private persist(value: string | null) {
    const write = this.writes.catch(() => {}).then(() => this.store.write(value));
    this.writes = write;
    return write;
  }
  async accept(tokens: Tokens) {
    const version = ++this.generation;
    this.tokens = null;
    await this.persist(tokens.refreshToken);
    if (version !== this.generation) throw new SessionExpired("Phiên đã đóng.");
    this.tokens = tokens;
  }
  async restore() {
    const version = this.generation;
    const refreshToken = await this.store.read();
    if (!refreshToken || version !== this.generation) return false;
    this.tokens = { accessToken: "", refreshToken, expiresAt: 0 };
    await this.accessToken();
    return true;
  }
  async accessToken(): Promise<string> {
    if (!this.tokens) throw new SessionExpired("Vui lòng đăng nhập để tiếp tục.");
    if (this.tokens.expiresAt > this.now() + 30000) return this.tokens.accessToken;
    if (this.refreshing) return this.refreshing;
    const version = this.generation;
    const token = this.tokens.refreshToken;
    const refresh = (async () => {
      try {
        const next = await this.oauth.refresh(token);
        if (version !== this.generation) throw new SessionExpired("Phiên đã đóng.");
        await this.persist(next.refreshToken);
        if (version !== this.generation) throw new SessionExpired("Phiên đã đóng.");
        this.tokens = next;
        return next.accessToken;
      } catch (error) {
        if (error instanceof SessionExpired && version === this.generation) {
          this.tokens = null;
          ++this.generation;
          await this.persist(null);
        }
        throw error;
      }
    })();
    this.refreshing = refresh;
    try {
      return await refresh;
    } finally {
      if (this.refreshing === refresh) this.refreshing = null;
    }
  }
  async signOut() {
    const token = this.tokens?.refreshToken;
    ++this.generation;
    this.tokens = null;
    this.refreshing = null;
    // A failed secure-store delete is surfaced to the user rather than claiming success.
    await this.persist(null);
    if (token) await this.oauth.revoke(token).catch(() => {});
  }
}
