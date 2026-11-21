// Builds the Keycloak realm file for production from infra/keycloak/realm-template.json and
// deploy/.env: public URLs, the secrets of the two server-only clients and the mail server.
// The result (deploy/.generated/vshop-realm.json) contains secrets and is ignored by Git.
//   node scripts/prepare-prod-realm.mjs
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const envText = readFileSync(join(root, "deploy/.env"), "utf8").replace(/^﻿/, "");
const env = Object.fromEntries(
  envText
    .split(/\r?\n/)
    .filter((line) => /^[A-Z0-9_]+=/.test(line))
    .map((line) => [line.slice(0, line.indexOf("=")), line.slice(line.indexOf("=") + 1)]),
);
const need = (name) => {
  const value = env[name];
  if (!value || value.startsWith("CHANGE_ME")) throw new Error(`${name} is missing in deploy/.env`);
  return value;
};

const shop = `https://${need("SHOP_DOMAIN")}`;
const realm = JSON.parse(readFileSync(join(root, "infra/keycloak/realm-template.json"), "utf8"));
realm.sslRequired = "external";
realm.registrationAllowed = false;

const web = realm.clients.find((c) => c.clientId === "vshop-web");
web.baseUrl = shop;
web.rootUrl = shop;
web.redirectUris = [`${shop}/api/auth/callback`, `${shop}/login`];
web.webOrigins = [shop];
web.attributes["post.logout.redirect.uris"] = `${shop}/*`;

realm.clients.find((c) => c.clientId === "vshop-provisioner").secret = need(
  "KEYCLOAK_PROVISIONER_SECRET",
);
realm.clients.find((c) => c.clientId === "vshop-web-direct").secret = need(
  "KEYCLOAK_WEB_DIRECT_SECRET",
);

// Mail for verification and password-reset links. "Name <address>" is split for Keycloak.
if (env.SMTP_HOST) {
  const from = env.EMAIL_FROM ?? "";
  const match = from.match(/^(.*)<([^>]+)>$/);
  realm.smtpServer = {
    host: env.SMTP_HOST,
    port: env.SMTP_PORT || "587",
    from: (match ? match[2] : from).trim(),
    fromDisplayName: match ? match[1].trim() : "VShop",
    starttls: String(env.SMTP_STARTTLS !== "false"),
    ssl: "false",
    ...(env.SMTP_USERNAME
      ? { auth: "true", user: env.SMTP_USERNAME, password: env.SMTP_PASSWORD ?? "" }
      : { auth: "false" }),
  };
} else {
  delete realm.smtpServer;
  console.warn("SMTP_HOST is empty: Keycloak cannot send verification or reset mail.");
}

// No fixture accounts exist in the template; only the service account of the provisioner client.
mkdirSync(join(root, "deploy/.generated"), { recursive: true });
const out = join(root, "deploy/.generated/vshop-realm.json");
writeFileSync(out, JSON.stringify(realm, null, 2));
console.log(`Wrote ${out}`);
