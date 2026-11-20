import { createHmac } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { expect, type Page } from "@playwright/test";

export type Account = { username: string; password: string; roles?: string[] };
export const accounts: Account[] = JSON.parse(
  readFileSync(".local/accounts.json", "utf8").replace(/^﻿/, ""),
);
export const account = (username: string) => accounts.find((a) => a.username === username)!;

/**
 * Local-only TOTP state: the secret an ADMIN account enrolled with, and the time steps already
 * spent. Keycloak refuses to accept the same OTP code twice, so each login uses an unused step
 * (the current one or a neighbour inside the look-ahead window) or waits for the next one.
 * The file lives under .local (ignored by Git) next to accounts.json.
 */
const STATE_FILE = ".local/totp.json";
type TotpState = Record<string, { secret: string; used: number[] }>;
const STEP_SECONDS = 30;

const readState = (): TotpState =>
  existsSync(STATE_FILE) ? JSON.parse(readFileSync(STATE_FILE, "utf8")) : {};
function writeState(state: TotpState) {
  mkdirSync(".local", { recursive: true });
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

function base32(secret: string): Buffer {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const char of secret.replace(/[\s=]/g, "").toUpperCase())
    bits += alphabet.indexOf(char).toString(2).padStart(5, "0");
  const bytes: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(bytes);
}
function codeForStep(secret: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hmac = createHmac("sha1", base32(secret)).update(counter).digest();
  const offset = hmac[hmac.length - 1] & 15;
  const value =
    ((hmac[offset] & 0x7f) << 24) |
    (hmac[offset + 1] << 16) |
    (hmac[offset + 2] << 8) |
    hmac[offset + 3];
  return String(value % 1_000_000).padStart(6, "0");
}

/** Returns an OTP code for the user that has not been used yet, waiting if necessary. */
export async function nextCode(username: string, secret?: string): Promise<string> {
  for (;;) {
    const state = readState();
    const entry = (state[username] ??= { secret: secret ?? "", used: [] });
    if (secret) entry.secret = secret;
    const current = Math.floor(Date.now() / 1000 / STEP_SECONDS);
    entry.used = entry.used.filter((step) => step >= current - 3);
    for (const step of [current, current + 1, current - 1]) {
      if (!entry.used.includes(step)) {
        entry.used.push(step);
        writeState(state);
        return codeForStep(entry.secret, step);
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 1500)); // all three slots spent: wait
  }
}

async function logoutViaApi(page: Page) {
  await page.request.post("/api/auth/logout", { headers: { origin: "http://localhost:3100" } });
}

/**
 * Signs in through Keycloak. ADMIN accounts are forced through TOTP: the first login enrols an
 * authenticator (secret saved to .local/totp.json); the session created right after enrolment has
 * no amr=otp, so we sign out and in again to obtain a session the API accepts.
 */
export async function login(page: Page, who: Account, target = "/"): Promise<void> {
  await page.goto(`/api/auth/login?returnTo=${encodeURIComponent(target)}`);
  // A live Keycloak SSO session signs in without showing the form.
  const idle = new Promise<never>(() => {});
  const first = await Promise.race([
    page.waitForURL(`http://localhost:3100${target}`, { timeout: 20_000 }).then(
      () => "sso",
      () => idle,
    ),
    page
      .locator("#username")
      .waitFor({ timeout: 20_000 })
      .then(
        () => "form",
        () => idle,
      ),
  ]);
  if (first === "sso") return;
  await page.locator("#username").fill(who.username);
  await page.locator("#password").fill(who.password);
  await page.locator("#kc-login").click();
  const never = new Promise<never>(() => {});
  const outcome = await Promise.race([
    page.waitForURL(`http://localhost:3100${target}`, { timeout: 20_000 }).then(
      () => "done",
      () => never,
    ),
    page
      .locator("#totp")
      .waitFor({ timeout: 20_000 })
      .then(
        () => "setup",
        () => never,
      ),
    page
      .locator("#otp")
      .waitFor({ timeout: 20_000 })
      .then(
        () => "challenge",
        () => never,
      ),
  ]);
  if (outcome === "setup") {
    await page.getByRole("link", { name: /unable to scan/i }).click();
    const secret = (await page.locator("#kc-totp-secret-key").innerText()).replace(/\s/g, "");
    await page.locator("#totp").fill(await nextCode(who.username, secret));
    const label = page.locator("#userLabel");
    if (await label.count()) await label.fill("e2e");
    await page.locator("#saveTOTPBtn").click();
    await expect(page).toHaveURL(`http://localhost:3100${target}`);
    // Enrolment does not count as an OTP login: start over so the session carries amr=otp.
    await logoutViaApi(page);
    return login(page, who, target);
  }
  if (outcome === "challenge") {
    if (!readState()[who.username]?.secret)
      throw new Error(
        `${who.username} has an authenticator but its secret is not in ${STATE_FILE}. ` +
          `Run scripts/reset-otp.ps1 -Username ${who.username} and run the tests again to enrol a known secret.`,
      );
    await page.locator("#otp").fill(await nextCode(who.username));
    await page.locator("#kc-login").click();
    await expect(page).toHaveURL(`http://localhost:3100${target}`);
  }
}
