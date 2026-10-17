import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

const modelContracts = [
  ["paytech/client-master", "client-master.pdma.json", "scripts/pdmaner/generate-client-master-model.mjs"],
  ["paytech/commercial", "commercial.pdma.json", "scripts/pdmaner/generate-commercial-model.mjs"],
  ["paytech/acquiring", "acquiring.pdma.json", "scripts/pdmaner/generate-acquiring-model.mjs"],
  ["paytech/consumer", "consumer.pdma.json", "scripts/pdmaner/generate-consumer-model.mjs"],
  ["paytech/payment", "payment.pdma.json", "scripts/pdmaner/generate-payment-model.mjs"],
  ["paytech/channel-connector", "channel-connector.pdma.json", "scripts/pdmaner/generate-channel-connector-model.mjs"],
  ["risk-compliance-service", "risk-compliance.pdma.json", "scripts/pdmaner/risk/generate-risk-compliance-model.mjs"],
  ["paytech/ledger", "ledger.pdma.json", "scripts/pdmaner/finance/generate-finance-model.mjs"],
  ["paytech/account", "account.pdma.json", "scripts/pdmaner/finance/generate-finance-model.mjs"],
  ["paytech/billing", "billing.pdma.json", "scripts/pdmaner/finance/generate-finance-model.mjs"],
  ["paytech/reconciliation", "reconciliation.pdma.json", "scripts/pdmaner/finance/generate-finance-model.mjs"],
  ["paytech/tax", "tax.pdma.json", "scripts/pdmaner/finance/generate-finance-model.mjs"],
  ["paytech/payout", "payout.pdma.json", "scripts/pdmaner/finance/generate-finance-model.mjs"],
  ["paytech/platform-support", "platform-support.pdma.json", "scripts/pdmaner/platform-support/generate-platform-support-model.mjs"],
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
    // 第 07 步：Finance 逻辑模型按服务切分输出，生成器以服务名模板写 paytech/<svc>/docs/database/<svc>.pdma.json。
    const serviceTemplate = "paytech/${service}/docs/database/${service}.pdma.json";
    const templated = source.includes(serviceTemplate) && filename === `${path.basename(service)}.pdma.json`;
    assert.ok(
      source.includes(`${service}/docs/database/${filename}`) || templated,
      `${generator} must target ${service}/docs/database/${filename}`,
    );
    assert.doesNotMatch(source, /Code-MoR .* Database V1\.pdma\.json/u);
  }
});
