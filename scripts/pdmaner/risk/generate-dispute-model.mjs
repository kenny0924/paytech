// Writes the dispute part of the Risk & Compliance PDManer model
// (paytech/dispute/docs/database/dispute.pdma.json); see risk-service-partition.mjs.
//
// Usage: node scripts/pdmaner/risk/generate-dispute-model.mjs [--check]
import { runRiskServiceModelCli } from "./risk-service-partition.mjs";

await runRiskServiceModelCli("dispute");
