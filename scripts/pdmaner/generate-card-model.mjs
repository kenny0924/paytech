// Writes the card part of the Payment Orchestration PDManer model (paytech/card/docs/database/card.pdma.json):
// the card partition of the generated model (per-database t_compensation_event, see payment-service-partition.mjs)
// plus the card tables and step 12 additions from card-model-additions.mjs.
//
// Usage: node scripts/pdmaner/generate-card-model.mjs [--check]
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { applyCardModelAdditions } from "./card-model-additions.mjs";
import { generatePaymentModel, serializePaymentModel } from "./generate-payment-model.mjs";
import { partitionModel } from "./payment-service-partition.mjs";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const outputPath = path.join(workspaceRoot, "paytech/card/docs/database/card.pdma.json");

export function generateCardModel() {
  return applyCardModelAdditions(partitionModel(generatePaymentModel(), "card"));
}

function runCli() {
  const unknown = process.argv.slice(2).filter((argument) => argument !== "--check");
  if (unknown.length > 0) throw new Error(`Unknown argument(s): ${unknown.join(", ")}`);
  const expected = serializePaymentModel(generateCardModel());
  const tables = JSON.parse(expected).entities.length;
  if (process.argv.includes("--check")) {
    if (!existsSync(outputPath)) throw new Error(`Card model does not exist: ${outputPath}`);
    if (readFileSync(outputPath, "utf8") !== expected) throw new Error("Card model is stale; regenerate it");
    process.stdout.write(`Card model is up to date (${tables} tables)\n`);
    return;
  }
  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, expected, "utf8");
  process.stdout.write(`Wrote card model: ${outputPath} (${tables} tables)\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runCli();
}
