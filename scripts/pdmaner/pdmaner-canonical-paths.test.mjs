import test from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

// 第 13 步收口：17 个 Java 持库服务（dispute、payout 以模块库登记）+ Vault，与 design/09 §5.2 一致。
const modelContracts = [
  ["paytech/client-master", "client-master.pdma.json", "scripts/pdmaner/generate-client-master-model.mjs"],
  ["paytech/commercial", "commercial.pdma.json", "scripts/pdmaner/generate-commercial-model.mjs"],
  ["paytech/acquiring", "acquiring.pdma.json", "scripts/pdmaner/generate-acquiring-model.mjs"],
  ["paytech/counterparty", "counterparty.pdma.json", "scripts/pdmaner/generate-counterparty-model.mjs"],
  ["paytech/payment", "payment.pdma.json", "scripts/pdmaner/generate-payment-model.mjs"],
  ["paytech/channel-connector", "channel-connector.pdma.json", "scripts/pdmaner/generate-channel-connector-model.mjs"],
  // 第 12 步：卡域 4 表迁出 payment，归 card-service（mor_card）。
  ["paytech/card", "card.pdma.json", "scripts/pdmaner/generate-card-model.mjs"],
  // 第 06 步：Risk & Compliance 模型按服务拆为 risk-decision、compliance、dispute（dispute 仅 Schema 归属）。
  ["paytech/risk-decision", "risk-decision.pdma.json", "scripts/pdmaner/risk/generate-risk-decision-model.mjs"],
  ["paytech/compliance", "compliance.pdma.json", "scripts/pdmaner/risk/generate-compliance-model.mjs"],
  ["paytech/dispute", "dispute.pdma.json", "scripts/pdmaner/risk/generate-dispute-model.mjs"],
  // ledger 模型脱离 Finance 生成器（2026-10-19 控制科目重构），直接维护 JSON。
  ["paytech/ledger", "ledger.pdma.json", null],
  // account 模型脱离 Finance 生成器，直接维护 JSON。
  ["paytech/account", "account.pdma.json", null],
  // billing 模型脱离 Finance 生成器（2026-10-19 计费事实重构），直接维护 JSON，由 billing-model.test.mjs 校验。
  ["paytech/billing", "billing.pdma.json", null],
  // reconciliation 模型脱离 Finance 生成器（2026-10-20 对账切片 1），直接维护 JSON，由 reconciliation-model.test.mjs 校验。
  ["paytech/reconciliation", "reconciliation.pdma.json", null],
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
    if (!generator) continue;
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

test("the contract list equals the set of PDManer models under paytech/<service>/docs/database", () => {
  const paytechRoot = path.join(workspaceRoot, "paytech");
  const found = readdirSync(paytechRoot)
    .filter((service) => statSync(path.join(paytechRoot, service)).isDirectory())
    .flatMap((service) => {
      const databaseDirectory = path.join(paytechRoot, service, "docs/database");
      try {
        return readdirSync(databaseDirectory)
          .filter((name) => name.endsWith(".pdma.json") && !name.startsWith(".back_"))
          .map((name) => `paytech/${service}/${name}`);
      } catch {
        return [];
      }
    })
    .sort();
  const expected = modelContracts.map(([service, filename]) => `${service}/${filename}`).sort();
  assert.equal(modelContracts.length, 18);
  assert.deepEqual(found, expected);
});
