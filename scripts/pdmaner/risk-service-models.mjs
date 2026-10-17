// Test helper for the step-06 Risk split (design/07 §7): reads the three canonical service models, their Schema slices and
// physical-decision documents, plus a union view for assertions that cover the whole former Risk & Compliance model.
import { readFileSync } from "node:fs";
import path from "node:path";

import { RISK_SERVICES, RISK_SERVICE_MODELS, unionRiskModels } from "./risk/risk-service-partition.mjs";

export const workspaceRoot = path.resolve(import.meta.dirname, "../..");

export const RISK_MODEL_PATHS = Object.freeze(Object.fromEntries(RISK_SERVICES.map((service) => [
  service, path.join(workspaceRoot, RISK_SERVICE_MODELS[service].modelPath)])));

export const RISK_SCHEMA_PATHS = Object.freeze(Object.fromEntries(RISK_SERVICES.map((service) => [
  service, path.join(workspaceRoot, RISK_SERVICE_MODELS[service].schemaPath)])));

export const RISK_PHYSICAL_DECISION_PATHS = Object.freeze({
  "risk-decision": path.join(workspaceRoot, "paytech/risk-decision/docs/database/RISK_DECISION_PHYSICAL_DECISIONS.md"),
  compliance: path.join(workspaceRoot, "paytech/compliance/docs/database/COMPLIANCE_PHYSICAL_DECISIONS.md"),
  dispute: path.join(workspaceRoot, "paytech/dispute/docs/database/DISPUTE_PHYSICAL_DECISIONS.md"),
});

export const RISK_DECISION_SQL_DIR = path.join(workspaceRoot, "paytech/risk-decision/docs/database/sql");

/** The four relations of the pre-split model that crossed service databases and are no longer modelled (design/07 §3). */
export const CROSS_DATABASE_RELATIONS = Object.freeze([
  "t_qualification_decision.decision_evaluation_id->t_decision_evaluation",
  "t_settlement_control.source_action_effect_id->t_action_effect",
  "t_dispute_liability_decision.decision_evaluation_id->t_decision_evaluation",
  "t_dispute_liability_decision.action_effect_id->t_action_effect",
]);

export function readRiskServiceModel(service) {
  return JSON.parse(readFileSync(RISK_MODEL_PATHS[service], "utf8"));
}

export function readRiskServiceModels() {
  return Object.fromEntries(RISK_SERVICES.map((service) => [service, readRiskServiceModel(service)]));
}

/** Union of the three service models in pre-split order (one t_compensation_event). */
export function readRiskUnionModel() {
  return unionRiskModels(readRiskServiceModels());
}

/** Concatenated physical-decision documents (risk-decision, compliance, dispute). */
export function readRiskPhysicalDecisions() {
  return RISK_SERVICES.map((service) => readFileSync(RISK_PHYSICAL_DECISION_PATHS[service], "utf8")).join("\n");
}

export { RISK_SERVICES };
