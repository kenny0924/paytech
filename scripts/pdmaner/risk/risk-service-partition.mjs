// Splits the Risk & Compliance PDManer model into one canonical model per owning service
// (requirements/20261017/architecture-V2骨架与模块搬迁/design/03 §2.2, design/07 §1、§7, task card 06 §2).
//
// - compliance owns due diligence, screening, qualification and the three settlement-control tables (C3, D38);
// - dispute owns the seven dispute tables (Schema ownership only, D42; mor_dispute is not created in step 06);
// - risk-decision owns everything else (rule configuration, evaluation, lists, limits, settlement risk assessment);
// - t_compensation_event is a per-database table and appears in all three models;
// - t_command_idempotency has no code and is deleted (S10, design/03 §2.2), so it appears in none.
// Entities, field / index / relation / diagram IDs are copied unchanged. Relations whose parent lives in another service are
// dropped (cross-database references keep the ID column only, design/07 §3); diagrams keep only the cells of their own service.

export const COMPLIANCE_TABLES = Object.freeze([
  "t_dd_program",
  "t_dd_program_version",
  "t_dd_program_requirement",
  "t_dd_program_check",
  "t_dd_program_screening",
  "t_due_diligence_case",
  "t_dd_case_subject",
  "t_dd_case_classification",
  "t_dd_case_dependency",
  "t_dd_requirement",
  "t_dd_evidence_ref",
  "t_dd_case_state_change",
  "t_dd_party_snapshot",
  "t_dd_check",
  "t_screening_run",
  "t_screening_hit",
  "t_screening_resolution",
  "t_qualification_decision",
  "t_role_qualification",
  "t_qualification_state_change",
  "t_settlement_control",
  "t_settlement_control_scope",
  "t_settle_control_state_change",
]);

export const DISPUTE_TABLES = Object.freeze([
  "t_dispute_case",
  "t_dispute_state_change",
  "t_dispute_evidence_package",
  "t_dispute_evidence_ref",
  "t_dispute_submission_request",
  "t_dispute_outcome",
  "t_dispute_liability_decision",
]);

export const PER_DATABASE_TABLES = Object.freeze(["t_compensation_event"]);

export const DELETED_TABLES = Object.freeze(["t_command_idempotency"]);

export const RISK_SERVICES = Object.freeze(["risk-decision", "compliance", "dispute"]);

/** Diagram groups of the committed model owned by each service; reference cells of foreign tables in other groups are not kept. */
export const RISK_SERVICE_DIAGRAMS = Object.freeze({
  "risk-decision": Object.freeze(["decision_engine", "evaluation", "risk_data", "limits", "reliability"]),
  compliance: Object.freeze(["due_diligence", "settlement", "reliability"]),
  dispute: Object.freeze(["dispute", "reliability"]),
});

export const RISK_SERVICE_MODELS = Object.freeze({
  "risk-decision": Object.freeze({
    name: "Code-MoR Risk Decision Database V1",
    describe: "Risk decision part of the Risk & Compliance V1 PDManer model (mor_risk_decision)",
    modelPath: "paytech/risk-decision/docs/database/risk-decision.pdma.json",
    schemaPath: "paytech/risk-decision/docs/database/sql/risk-decision-schema.sql",
  }),
  compliance: Object.freeze({
    name: "Code-MoR Compliance Database V1",
    describe: "Compliance part of the Risk & Compliance V1 PDManer model (mor_compliance; settlement control C3)",
    modelPath: "paytech/compliance/docs/database/compliance.pdma.json",
    schemaPath: "paytech/compliance/docs/database/sql/compliance-schema.sql",
  }),
  dispute: Object.freeze({
    name: "Code-MoR Dispute Database V1",
    describe: "Dispute part of the Risk & Compliance V1 PDManer model (mor_dispute; Schema ownership only)",
    modelPath: "paytech/dispute/docs/database/dispute.pdma.json",
    schemaPath: "paytech/dispute/docs/database/sql/dispute-schema.sql",
  }),
});

/** Owning service of a physical table; null for deleted tables. */
export function serviceOfTable(table) {
  if (DELETED_TABLES.includes(table)) return null;
  if (COMPLIANCE_TABLES.includes(table)) return "compliance";
  if (DISPUTE_TABLES.includes(table)) return "dispute";
  return "risk-decision";
}

/** Whether a table belongs to the given service's database. */
export function tableInService(table, service) {
  if (DELETED_TABLES.includes(table)) return false;
  return PER_DATABASE_TABLES.includes(table) || serviceOfTable(table) === service;
}

/** Returns a deep copy of the model that contains only the given service's tables, relations and diagram cells. */
export function partitionModel(model, service) {
  const target = RISK_SERVICE_MODELS[service];
  if (!target) throw new Error(`Unknown Risk partition: ${service}`);
  const copy = structuredClone(model);
  copy.name = target.name;
  copy.describe = target.describe;
  copy.entities = copy.entities.filter((entity) => tableInService(entity.defKey, service));
  const entityIds = new Set(copy.entities.map((entity) => entity.id));
  for (const entity of copy.entities) {
    entity.correlations = (entity.correlations ?? []).filter((relation) => entityIds.has(relation.refEntity));
  }
  const relationIds = new Set(copy.entities.flatMap((entity) => entity.correlations.map((relation) => relation.id)));
  copy.diagrams = copy.diagrams.map((diagram) => {
    const cells = diagram.canvasData.cells;
    const tableCells = cells.filter((cell) => cell.shape === "table" && entityIds.has(cell.originKey));
    const cellIds = new Set(tableCells.map((cell) => cell.id));
    const kept = cells.filter((cell) => {
      if (cell.shape === "table") return entityIds.has(cell.originKey);
      if (cell.shape === "erdRelation") {
        return relationIds.has(cell.originKey) && cellIds.has(cell.source?.cell) && cellIds.has(cell.target?.cell);
      }
      return true;
    });
    const tables = new Set(copy.entities.map((entity) => entity.defKey));
    const properties = Object.fromEntries(Object.entries(diagram.properties ?? {}).map(([key, value]) => (
      [key, Array.isArray(value) ? value.filter((table) => tables.has(table)) : value])));
    return { ...diagram, canvasData: { ...diagram.canvasData, cells: kept }, properties };
  }).filter((diagram) => RISK_SERVICE_DIAGRAMS[service].includes(diagram.defKey)
    && diagram.canvasData.cells.some((cell) => cell.shape === "table"));
  return copy;
}

/** Serialized form identical to the committed models (2-space JSON, no trailing newline). */
export function serializeRiskServiceModel(model, service) {
  return JSON.stringify(partitionModel(model, service), null, 2);
}

/**
 * Re-assembles the three committed partitions into one union view for whole-Risk assertions, in the pre-split order:
 * due diligence, decision, settlement control, dispute, then t_compensation_event once; diagrams in the pre-split group order with
 * the per-database reliability diagram merged. Cross-database relations and t_command_idempotency are absent by design.
 */
export function unionRiskModels(models) {
  const union = structuredClone(models["risk-decision"]);
  union.name = "Code-MoR Risk & Compliance union view (risk-decision + compliance + dispute)";
  const compliance = models.compliance.entities;
  const ordered = [
    ...compliance.filter((entity) => !CONTROL_TABLES.includes(entity.defKey) && !PER_DATABASE_TABLES.includes(entity.defKey)),
    ...models["risk-decision"].entities.filter((entity) => !PER_DATABASE_TABLES.includes(entity.defKey)),
    ...compliance.filter((entity) => CONTROL_TABLES.includes(entity.defKey)),
    ...models.dispute.entities.filter((entity) => !PER_DATABASE_TABLES.includes(entity.defKey)),
    ...models["risk-decision"].entities.filter((entity) => PER_DATABASE_TABLES.includes(entity.defKey)),
  ];
  union.entities = structuredClone(ordered);
  const diagrams = new Map();
  for (const key of DIAGRAM_ORDER) {
    for (const service of RISK_SERVICES) {
      const diagram = models[service].diagrams.find((candidate) => candidate.defKey === key);
      if (!diagram) continue;
      const existing = diagrams.get(key);
      if (!existing) {
        diagrams.set(key, structuredClone(diagram));
        continue;
      }
      const cellIds = new Set(existing.canvasData.cells.map((cell) => cell.id));
      existing.canvasData.cells.push(...structuredClone(diagram.canvasData.cells.filter((cell) => !cellIds.has(cell.id))));
      for (const [name, value] of Object.entries(diagram.properties ?? {})) {
        if (Array.isArray(value)) existing.properties[name] = [...new Set([...(existing.properties[name] ?? []), ...value])];
      }
    }
  }
  union.diagrams = [...diagrams.values()];
  return union;
}

const CONTROL_TABLES = Object.freeze(["t_settlement_control", "t_settlement_control_scope", "t_settle_control_state_change"]);

const DIAGRAM_ORDER = Object.freeze([
  "due_diligence", "decision_engine", "evaluation", "risk_data", "limits", "settlement", "dispute", "reliability",
]);

/**
 * CLI shared by generate-risk-decision-model.mjs, generate-compliance-model.mjs and generate-dispute-model.mjs:
 * partitions the Manifest-generated model and writes (or with --check verifies) paytech/<service>/docs/database/<service>.pdma.json.
 */
export async function runRiskServiceModelCli(service, argv = process.argv.slice(2)) {
  const { existsSync, mkdirSync, readFileSync, writeFileSync } = await import("node:fs");
  const path = await import("node:path");
  const { fileURLToPath } = await import("node:url");
  const { generateRiskComplianceModel } = await import("./generate-risk-compliance-model.mjs");
  const unknown = argv.filter((argument) => argument !== "--check");
  if (unknown.length > 0) throw new Error(`Unknown argument(s): ${unknown.join(", ")}`);
  const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
  const outputPath = path.join(workspaceRoot, RISK_SERVICE_MODELS[service].modelPath);
  const expected = serializeRiskServiceModel(generateRiskComplianceModel(), service);
  const tables = JSON.parse(expected).entities.length;
  if (argv.includes("--check")) {
    if (!existsSync(outputPath)) throw new Error(`${service} model does not exist: ${outputPath}`);
    if (readFileSync(outputPath, "utf8") !== expected) throw new Error(`${service} model is stale; regenerate it`);
    process.stdout.write(`${service} model is up to date (${tables} tables)\n`);
    return;
  }
  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, expected, "utf8");
  process.stdout.write(`Wrote ${service} model: ${outputPath} (${tables} tables)\n`);
}
