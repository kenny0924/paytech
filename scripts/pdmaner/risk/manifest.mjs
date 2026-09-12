import { TABLES as DUE_DILIGENCE_TABLES } from "./manifests/01-due-diligence.mjs";
import { TABLES as DECISION_ENGINE_TABLES } from "./manifests/02-decision-engine.mjs";
import { TABLES as EVALUATION_TABLES } from "./manifests/03-evaluation.mjs";
import { TABLES as RISK_DATA_TABLES } from "./manifests/04-risk-data.mjs";
import { TABLES as LIMIT_TABLES } from "./manifests/05-limits.mjs";
import { TABLES as SETTLEMENT_TABLES } from "./manifests/06-settlement.mjs";
import { TABLES as DISPUTE_TABLES } from "./manifests/07-dispute.mjs";
import { TABLES as RELIABILITY_TABLES } from "./manifests/08-reliability.mjs";

export const TABLE_GROUPS = Object.freeze({
  due_diligence: DUE_DILIGENCE_TABLES,
  decision_engine: DECISION_ENGINE_TABLES,
  evaluation: EVALUATION_TABLES,
  risk_data: RISK_DATA_TABLES,
  limits: LIMIT_TABLES,
  settlement: SETTLEMENT_TABLES,
  dispute: DISPUTE_TABLES,
  reliability: RELIABILITY_TABLES,
});

export const TABLES = Object.freeze(Object.values(TABLE_GROUPS).flat());
export const RELATIONS = Object.freeze(TABLES.flatMap((table) => table.relations));

if (TABLES.length !== 74) {
  throw new Error(`Risk & Compliance 生产模型必须有 74 张表，当前为 ${TABLES.length}`);
}

if (new Set(TABLES.map((table) => table.defKey)).size !== TABLES.length) {
  throw new Error("Risk & Compliance 逻辑表名重复");
}

if (new Set(TABLES.map((table) => table.physicalKey)).size !== TABLES.length) {
  throw new Error("Risk & Compliance 物理表名重复");
}
