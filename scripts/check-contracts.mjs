// Fails when packages/contracts/src/generated/api.ts is not exactly what openapi-typescript
// produces from docs/api/openapi.json. Together with the backend OpenApiSpecTests (spec == code)
// this closes the chain code -> OpenAPI -> TypeScript, so a contract change cannot be half-applied.
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const committed = path.join(root, "packages/contracts/src/generated/api.ts");
const dir = mkdtempSync(path.join(tmpdir(), "vshop-contracts-"));
const generated = path.join(dir, "api.ts");
try {
  const bin = path.join(root, "node_modules/openapi-typescript/bin/cli.js");
  execFileSync(process.execPath, [bin, path.join(root, "docs/api/openapi.json"), "-o", generated], {
    stdio: "ignore",
  });
  const normalise = (file) => readFileSync(file, "utf8").replace(/\r\n/g, "\n");
  if (normalise(committed) !== normalise(generated)) {
    console.error(
      "packages/contracts/src/generated/api.ts is out of date with docs/api/openapi.json.\n" +
        "Run `npm run contracts:generate` and commit the result.",
    );
    process.exit(1);
  }
  console.log("TypeScript contracts match docs/api/openapi.json.");
} finally {
  rmSync(dir, { recursive: true, force: true });
}
