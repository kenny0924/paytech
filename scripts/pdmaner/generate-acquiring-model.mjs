// Writes the acquiring part of the Commerce-family PDManer manifest
// (paytech/acquiring/docs/database/acquiring.pdma.json); see commerce-model-core.mjs.
//
// Usage: node scripts/pdmaner/generate-acquiring-model.mjs [--check]
import path from "node:path";
import { fileURLToPath } from "node:url";

import { generateServiceModel, runGeneratorCli } from "./commerce-model-core.mjs";

export { structuralProjection, structuralProjectionDigest } from "./commerce-model-core.mjs";

export function generateAcquiringModel(options = {}) {
  return generateServiceModel("acquiring", options);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runGeneratorCli("acquiring");
}
