// Writes the channel-connector part of the Payment Orchestration PDManer model
// (paytech/channel-connector/docs/database/channel-connector.pdma.json); see payment-service-partition.mjs.
//
// Usage: node scripts/pdmaner/generate-channel-connector-model.mjs [--check]
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { serializePaymentServiceModel } from "./generate-payment-model.mjs";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const outputPath = path.join(workspaceRoot, "paytech/channel-connector/docs/database/channel-connector.pdma.json");

export function generateChannelConnectorModel() {
  return JSON.parse(serializePaymentServiceModel("channel-connector"));
}

function runCli() {
  const unknown = process.argv.slice(2).filter((argument) => argument !== "--check");
  if (unknown.length > 0) throw new Error(`Unknown argument(s): ${unknown.join(", ")}`);
  const expected = serializePaymentServiceModel("channel-connector");
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
