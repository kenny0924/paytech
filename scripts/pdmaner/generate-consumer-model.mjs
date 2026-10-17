// Writes the consumer part of the Commerce-family PDManer manifest
// (paytech/consumer/docs/database/consumer.pdma.json); see commerce-model-core.mjs.
//
// Usage: node scripts/pdmaner/generate-consumer-model.mjs [--check]
import path from "node:path";
import { fileURLToPath } from "node:url";

import { generateServiceModel, runGeneratorCli } from "./commerce-model-core.mjs";

export { structuralProjection, structuralProjectionDigest } from "./commerce-model-core.mjs";

export function generateConsumerModel(options = {}) {
  return generateServiceModel("consumer", options);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runGeneratorCli("consumer");
}
