import { readFileSync } from "node:fs";
import { randomBytes } from "node:crypto";
import { expect, type APIRequestContext } from "@playwright/test";
import type { Account } from "./auth";

const KEYCLOAK = "http://localhost:8180";

function localEnv(): Record<string, string> {
  return Object.fromEntries(
    readFileSync(".env", "utf8")
      .replace(/^﻿/, "")
      .split(/\r?\n/)
      .filter(Boolean)
      .map((line) => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]),
  );
}

async function adminHeaders(request: APIRequestContext) {
  const response = await request.post(`${KEYCLOAK}/realms/master/protocol/openid-connect/token`, {
    form: {
      grant_type: "password",
      client_id: "admin-cli",
      username: "vshop-local-admin",
      password: localEnv().KEYCLOAK_ADMIN_PASSWORD,
    },
  });
  expect(response.ok()).toBeTruthy();
  return { Authorization: `Bearer ${(await response.json()).access_token}` };
}

/** Creates a throw-away local Keycloak user with one realm role and a random password. */
export async function createUser(
  request: APIRequestContext,
  prefix: string,
  role: "BUYER" | "SELLER" | "ADMIN",
): Promise<Account & { id: string }> {
  const headers = await adminHeaders(request);
  const username = `${prefix}.${Date.now()}`;
  const password = randomBytes(24).toString("hex");
  const created = await request.post(`${KEYCLOAK}/admin/realms/vshop/users`, {
    headers,
    data: {
      username,
      enabled: true,
      emailVerified: true,
      email: `${username}@vshop.local`,
      firstName: "E2E",
      lastName: "VShop",
      credentials: [{ type: "password", value: password, temporary: false }],
    },
  });
  expect(created.status()).toBe(201);
  const id = created.headers().location.split("/").at(-1)!;
  const realmRole = await request.get(`${KEYCLOAK}/admin/realms/vshop/roles/${role}`, { headers });
  const assigned = await request.post(
    `${KEYCLOAK}/admin/realms/vshop/users/${id}/role-mappings/realm`,
    { headers, data: [await realmRole.json()] },
  );
  expect(assigned.ok()).toBeTruthy();
  return { username, password, roles: [role], id };
}

export async function deleteUser(request: APIRequestContext, id: string) {
  const headers = await adminHeaders(request);
  await request.delete(`${KEYCLOAK}/admin/realms/vshop/users/${id}`, { headers });
}

/** Removes a user created through self-service sign-up (found by its exact email). */
export async function deleteUserByEmail(request: APIRequestContext, email: string) {
  const headers = await adminHeaders(request);
  const found = await request.get(
    `${KEYCLOAK}/admin/realms/vshop/users?email=${encodeURIComponent(email)}&exact=true`,
    { headers },
  );
  for (const user of (await found.json()) as { id: string }[]) await deleteUser(request, user.id);
}

/** The realm roles of a user (to prove sign-up never grants more than BUYER). */
export async function realmRolesOf(request: APIRequestContext, email: string) {
  const headers = await adminHeaders(request);
  const found = await request.get(
    `${KEYCLOAK}/admin/realms/vshop/users?email=${encodeURIComponent(email)}&exact=true`,
    { headers },
  );
  const [user] = (await found.json()) as { id: string }[];
  const roles = await request.get(
    `${KEYCLOAK}/admin/realms/vshop/users/${user.id}/role-mappings/realm`,
    { headers },
  );
  return ((await roles.json()) as { name: string }[]).map((r) => r.name);
}
