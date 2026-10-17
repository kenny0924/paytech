import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

const modelContracts = [
  ["client-contract-service", "client-contract.pdma.json", "scripts/pdmaner/generate-client-contract-model.mjs"],
  ["commerce-service", "commerce.pdma.json", "scripts/pdmaner/generate-commerce-model.mjs"],
  ["payment-orchestration-service", "payment-orchestration.pdma.json", "scripts/pdmaner/generate-payment-model.mjs"],
  ["risk-compliance-service", "risk-compliance.pdma.json", "scripts/pdmaner/risk/generate-risk-compliance-model.mjs"],
  ["finance-service", "finance.pdma.json", "scripts/pdmaner/finance/generate-finance-model.mjs"],
  ["platform-support-service", "platform-support.pdma.json", "scripts/pdmaner/platform-support/generate-platform-support-model.mjs"],
  ["paytech/vault", "vault.pdma.json", "scripts/pdmaner/vault/generate-vault-model.mjs"],
];

test("each database owner keeps exactly one canonical short-name PDManer model", () => {
  for (const [service, filename] of modelContracts) {
    const databaseDirectory = path.join(workspaceRoot, service, "docs/database");
    const models = readdirSync(databaseDirectory, { withFileTypes: true })
      .filter((entry) => entry.isFile() && entry.name.endsWith(".pdma.json") && !entry.name.startsWith(".back_"))
      .map((entry) => entry.name)
      .sort();

    assert.deepEqual(models, [filename], `${service} must keep only ${filename}`);
    assert.match(filename, /^[a-z0-9]+(?:-[a-z0-9]+)*\.pdma\.json$/u);
  }
});

test("every PDManer generator writes to its canonical short path", () => {
  for (const [service, filename, generator] of modelContracts) {
    const source = readFileSync(path.join(workspaceRoot, generator), "utf8");
    assert.ok(
      source.includes(`${service}/docs/database/${filename}`),
      `${generator} must target ${service}/docs/database/${filename}`,
    );
    assert.doesNotMatch(source, /Code-MoR .* Database V1\.pdma\.json/u);
  }
});
