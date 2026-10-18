// Writes the compliance part of the Risk & Compliance PDManer model
// (paytech/compliance/docs/database/compliance.pdma.json); see risk-service-partition.mjs.
//
// Usage: node scripts/pdmaner/risk/generate-compliance-model.mjs [--check]
import { runRiskServiceModelCli } from "./risk-service-partition.mjs";

await runRiskServiceModelCli("compliance");
