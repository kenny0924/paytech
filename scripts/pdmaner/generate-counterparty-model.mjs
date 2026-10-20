// Writes the counterparty part of the Commerce-family PDManer manifest
// (paytech/counterparty/docs/database/counterparty.pdma.json); see commerce-model-core.mjs.
//
// Usage: node scripts/pdmaner/generate-counterparty-model.mjs [--check]
import path from "node:path";
import { fileURLToPath } from "node:url";

import { generateServiceModel, runGeneratorCli } from "./commerce-model-core.mjs";

export { structuralProjection, structuralProjectionDigest } from "./commerce-model-core.mjs";

export function generateCounterpartyModel(options = {}) {
  return generateServiceModel("counterparty", options);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runGeneratorCli("counterparty");
}
