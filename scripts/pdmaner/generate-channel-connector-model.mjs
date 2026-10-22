// Writes the channel-connector part of the Payment Orchestration PDManer model
// (paytech/channel-connector/docs/database/channel-connector.pdma.json); see payment-service-partition.mjs.
// Since 2026-10-20 (统一对外渠道出口第一段) the ten channel protocol tables moved from platform-support are appended from
// channel-connector/protocol-tables.mjs, see channel-connector/protocol-model.mjs. Since 2026-10-22 (第二段) the partition's
// t_payment_channel is reshaped into t_channel first (channel-connector/channel-model.mjs) and the protocol tables relate to it.
//
// Usage: node scripts/pdmaner/generate-channel-connector-model.mjs [--check]
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { reshapeChannelTables } from "./channel-connector/channel-model.mjs";
import { appendProtocolTables } from "./channel-connector/protocol-model.mjs";
import { serializePaymentServiceModel } from "./generate-payment-model.mjs";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const outputPath = path.join(workspaceRoot, "paytech/channel-connector/docs/database/channel-connector.pdma.json");

export function generateChannelConnectorModel() {
  return appendProtocolTables(reshapeChannelTables(JSON.parse(serializePaymentServiceModel("channel-connector"))));
}

function serializeChannelConnectorModel() {
  return `${JSON.stringify(generateChannelConnectorModel(), null, 2)}\n`;
}

function runCli() {
  const unknown = process.argv.slice(2).filter((argument) => argument !== "--check");
  if (unknown.length > 0) throw new Error(`Unknown argument(s): ${unknown.join(", ")}`);
  const expected = serializeChannelConnectorModel();
  const tables = JSON.parse(expected).entities.length;
  if (process.argv.includes("--check")) {
    if (!existsSync(outputPath)) throw new Error(`Channel connector model does not exist: ${outputPath}`);
    if (readFileSync(outputPath, "utf8") !== expected) throw new Error("Channel connector model is stale; regenerate it");
    process.stdout.write(`Channel connector model is up to date (${tables} tables)\n`);
    return;
  }
  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, expected, "utf8");
  process.stdout.write(`Wrote channel connector model: ${outputPath} (${tables} tables)\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runCli();
}
