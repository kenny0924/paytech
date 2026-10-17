// Writes the risk-decision part of the Risk & Compliance PDManer model
// (paytech/risk-decision/docs/database/risk-decision.pdma.json); see risk-service-partition.mjs.
//
// Usage: node scripts/pdmaner/risk/generate-risk-decision-model.mjs [--check]
import { runRiskServiceModelCli } from "./risk-service-partition.mjs";

await runRiskServiceModelCli("risk-decision");
