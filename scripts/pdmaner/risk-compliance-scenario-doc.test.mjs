import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { APPROVED_TABLES } from "./risk-compliance-test-oracle/schema.oracle.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const scenarioPath = path.join(workspaceRoot, "agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md");
const logicalDatabasePath = path.join(workspaceRoot, "agent-docs/database/RISK_COMPLIANCE_DATABASE.md");
const modelPath = path.join(workspaceRoot, "risk-compliance-service/docs/database/risk-compliance.pdma.json");
const physicalDecisionsPath = path.join(
  workspaceRoot,
  "risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md",
);
const settlementDesignPath = path.join(
  workspaceRoot,
  "agent-docs/plans/2026-08-28-risk-settlement-eligibility-balanced-simplification-design.md",
);
const payoutCheckpointAlignmentPath = path.join(
  workspaceRoot,
  "agent-docs/plans/2026-08-29-risk-manual-payout-eligibility-checkpoint-alignment-implementation-plan.md",
);
const priorLimitPlanPath = path.join(
  workspaceRoot,
  "agent-docs/plans/2026-08-28-risk-limit-balanced-simplification-implementation-plan.md",
);
const priorDecisionPlanPath = path.join(
  workspaceRoot,
  "agent-docs/plans/2026-08-27-risk-decision-engine-balanced-simplification-implementation-plan.md",
);

const REMOVED_DECISION_TABLES = Object.freeze([
  "t_limit_definition",
  "t_limit_plan",
  "t_limit_plan_version",
  "t_limit_plan_item",
  "t_feature_definition",
  "t_action_definition",
  "t_rule_operand_value",
  "t_release_static_check",
  "t_release_approval",
  "t_release_state_change",
  "t_release_shadow_summary",
  "t_release_shadow_comparison",
  "t_evaluation_rule_result",
  "t_evaluation_predicate_result",
  "t_action_execution",
  "t_settlement_restriction",
  "t_settlement_restriction_scope",
  "t_reserve_requirement",
  "t_settle_restrict_state_change",
]);

const DISPUTE_TABLES = Object.freeze([
  "t_dispute_case",
  "t_dispute_state_change",
  "t_dispute_evidence_package",
  "t_dispute_evidence_ref",
  "t_dispute_submission_request",
  "t_dispute_outcome",
  "t_dispute_liability_decision",
]);

const REMOVED_DISPUTE_FIELDS = Object.freeze([
  "submission_deadline",
  "document_version",
  "request_hash",
  "liability_id",
  "release_id",
]);

const DISPUTE_DECISION_SOURCE_TYPES = Object.freeze([
  "PROVIDER_ACTION",
  "CLIENT_DISPOSITION",
  "OPERATIONS_DISPOSITION",
  "OPERATIONS_CORRECTION",
]);

const HISTORICAL_RISK_TOTALS = Object.freeze({
  tables: 71,
  fields: 865,
  uniqueIndexes: 105,
  accessIndexes: 75,
  relations: 111,
  oneToOneRelations: 9,
  diagramGroups: 8,
});

test("Risk current documents freeze the 15-column compensation reconstruction and token boundaries", () => {
  const scenario = readFileSync(scenarioPath, "utf8");
  const logicalDatabase = readFileSync(logicalDatabasePath, "utf8");
  const physicalDecisions = readFileSync(physicalDecisionsPath, "utf8");
  const currentContract = `${scenario}\n${logicalDatabase}\n${physicalDecisions}`;

  assert.match(currentContract, /event_key[^。]*event_type[^。]*sub_event_type/u);
  for (const defaultContract of [
    "execute_status DEFAULT 'INIT'",
    "execute_count DEFAULT 0",
    "next_execute_at DEFAULT CURRENT_TIMESTAMP(6)",
    "create_time DEFAULT CURRENT_TIMESTAMP(6)",
    "update_time DEFAULT CURRENT_TIMESTAMP(6)",
  ]) {
    assert.ok(currentContract.includes(defaultContract), `补偿事件文档缺少默认值：${defaultContract}`);
  }
  assert.ok(
    currentContract.includes("idx_comp_event_ready(execute_status,next_execute_at,id)"),
    "补偿事件文档缺少带 id 稳定排序的 Ready 索引",
  );
  assert.ok(
    currentContract.includes("idx_comp_event_lease(execute_status,lock_until,id)"),
    "补偿事件文档缺少带 id 稳定排序的 Lease 索引",
  );
  assert.match(currentContract, /Risk 补偿事件重建清单/u);
  for (const legacyField of ["execution_type", "source_type", "source_id", "source_version", "schema_version", "payload_hash"]) {
    const reliability = section(logicalDatabase, "### 4.8 本地可靠执行", "## 5. 核心关系图");
    assert.doesNotMatch(reliability, new RegExp(`\\b${legacyField}\\b`, "u"));
  }
  assert.match(currentContract, /三个 Risk-owned Recoverable Token/u);
  assert.match(currentContract, /四个 Commerce Stable Correlation Token/u);
  assert.match(currentContract, /dd_party_snapshot\.snapshot_vault_token[^。]*NOT NULL/u);
  assert.match(currentContract, /dd_party_snapshot\.snapshot_vault_token[^。]*Reveal/u);
  assert.match(currentContract, /规则配置[^。]*不调用 Vault/u);
  assert.match(currentContract, /Risk 物理 DDL[^。]*零 CHECK[^。]*零 FOREIGN KEY/u);
  assert.match(currentContract, /全局 DB-01[^。]*未完成/u);
});

function tableExampleInventory(document) {
  const counts = new Map();
  for (const match of document.matchAll(/^\| `(t_[a-z][a-z0-9_]*)` \|/gmu)) {
    counts.set(match[1], (counts.get(match[1]) ?? 0) + 1);
  }
  return counts;
}

function section(document, start, end) {
  const startIndex = document.indexOf(start);
  assert.notEqual(startIndex, -1, `文档缺少章节：${start}`);
  const endIndex = document.indexOf(end, startIndex + start.length);
  assert.notEqual(endIndex, -1, `文档缺少章节边界：${end}`);
  return document.slice(startIndex, endIndex);
}

test("Risk real-world scenarios cover all 74 physical tables and no removed or unknown table", () => {
  assert.ok(existsSync(scenarioPath), `Risk 现实场景尚未生成：${scenarioPath}`);
  const scenario = readFileSync(scenarioPath, "utf8");
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const modelTables = new Set(model.entities.map((entity) => entity.defKey));
  const mentionedTables = new Set(scenario.match(/\bt_[a-z][a-z0-9_]*\b/gu) ?? []);
  const exampleInventory = tableExampleInventory(scenario);

  assert.deepEqual([...modelTables].sort(), [...APPROVED_TABLES].sort());
  assert.equal(APPROVED_TABLES.length, 74);
  assert.deepEqual([...exampleInventory.keys()].sort(), [...APPROVED_TABLES].sort());
  assert.equal(exampleInventory.size, 74);
  for (const [table, count] of exampleInventory) {
    assert.equal(count, 1, `现实场景必须恰有一条逐表示例：${table}`);
  }
  for (const table of APPROVED_TABLES) assert.ok(mentionedTables.has(table), `现实场景未覆盖：${table}`);
  for (const table of mentionedTables) assert.ok(modelTables.has(table), `现实场景引用未知物理表：${table}`);
  for (const table of REMOVED_DECISION_TABLES) {
    assert.ok(!mentionedTables.has(table), `现实场景仍把已删除表写成现行结构：${table}`);
  }
});

test("Risk documents route current statistics and preserve the historical Settlement snapshot", () => {
  const scenario = readFileSync(scenarioPath, "utf8");
  const logicalDatabase = readFileSync(logicalDatabasePath, "utf8");
  const physicalDecisions = readFileSync(physicalDecisionsPath, "utf8");
  const currentDocuments = [scenario, logicalDatabase, physicalDecisions];

  assert.match(scenario, /现行生成模型统计、验证结果和部署状态只见/u);
  assert.match(scenario, /规则集管理方案/u);
  assert.match(scenario, /任一历史版本被当前 ACTIVE Release 使用时禁止整体删除/u);
  assert.match(physicalDecisions, /已冻结物理基线（历史快照；现行统计由台账维护）/u);
  assert.match(scenario, /Settlement 3 表/u);
  assert.doesNotMatch(scenario, /Settlement 4 表/u);
  assert.match(logicalDatabase, /Settlement Eligibility Control（3 张）/u);
  assert.match(physicalDecisions, /\| 结算资格控制 \| 3 \|/u);

  for (const table of [
    "t_settlement_control",
    "t_settlement_control_scope",
    "t_settle_control_state_change",
  ]) {
    assert.ok(currentDocuments.some((document) => document.includes(table)), `当前文档缺少 Settlement 表：${table}`);
  }
  for (const table of [
    "t_settlement_restriction",
    "t_settlement_restriction_scope",
    "t_reserve_requirement",
    "t_settle_restrict_state_change",
  ]) {
    for (const document of currentDocuments) {
      assert.ok(!document.includes(table), `当前文档仍引用已删除 Settlement 表：${table}`);
    }
  }

  const manifestBlock = physicalDecisions.match(
    /<!-- RISK_PHYSICAL_MANIFEST_START -->\s*```json\s*([\s\S]*?)\s*```\s*<!-- RISK_PHYSICAL_MANIFEST_END -->/u,
  )?.[1];
  assert.ok(manifestBlock, "物理决策缺少 Risk manifest block");
  const physicalManifest = JSON.parse(manifestBlock);
  assert.deepEqual(physicalManifest.modelTotals, HISTORICAL_RISK_TOTALS);
  const ledger = readFileSync(path.join(workspaceRoot, "agent-docs/CURRENT_IMPLEMENTATION.md"), "utf8");
  assert.match(ledger, /\| Risk & Compliance \| 74 \| 898 \| 110 \| 80 \| 113 \| 9 \| 8 \|/u);
  assert.equal(physicalManifest.checkContracts, 82);
  assert.equal(
    physicalManifest.modelSha256,
    "68b232ffa2f12423b00497db6682ffd71274f5b8b2d1f4c61d78e9a2db4ad3cf",
  );
  assert.equal(
    physicalManifest.structuralProjectionSha256,
    "be58d091f5ffe87fce6c3fa67ad079845896475124ad7c866196e48a9d7992d9",
  );
  assert.equal(
    physicalManifest.checkPolicySha256,
    "a1cc6af3ecf7dab474ee6a85f1f5c95caf1f08612834df9c128c19cef746ab11",
  );
  assert.match(
    logicalDatabase,
    /现行模型统计见\[当前台账\]/u,
  );
});

test("Risk documents freeze the Local Partner Application namespace, role isolation and child ownership", () => {
  const scenario = readFileSync(scenarioPath, "utf8");
  const logicalDatabase = readFileSync(logicalDatabasePath, "utf8");
  const physicalDecisions = readFileSync(physicalDecisionsPath, "utf8");

  for (const [documentName, document] of [
    ["逻辑数据库", logicalDatabase],
    ["物理决策", physicalDecisions],
  ]) {
    const namespaceRow = document.match(/^\| `?CCT_LOCAL_PARTNER_APPLICATION`? \|[^\n]+$/mu)?.[0] ?? "";
    assert.match(namespaceRow, /LOCAL_PARTNER[^\n]*PARTNER_DUE_DILIGENCE/u, `${documentName} 缺少 Local Partner 主 Tuple`);
    assert.match(namespaceRow, /RELATED_PERSON[^\n]*RELATED_PERSON_KYC/u, `${documentName} 缺少关联人子 Tuple`);
    assert.doesNotMatch(namespaceRow, /(?:^|[^A-Z_])CLIENT(?:[^A-Z_]|$)|OPERATING_ENTITY/u, `${documentName} 错误允许 Client/OE Role`);
  }

  assert.match(
    scenario,
    /source_namespace=CCT_LOCAL_PARTNER_APPLICATION, source_application_id=730320000000000001, role_type=LOCAL_PARTNER, role_id=730300000000000001, qualification_type=PARTNER_DUE_DILIGENCE/u,
  );
  assert.match(
    scenario,
    /source_namespace=CCT_LOCAL_PARTNER_APPLICATION, source_application_id=730320000000000001, role_type=RELATED_PERSON, role_id=730100000000000009, qualification_type=RELATED_PERSON_KYC/u,
  );
  for (const namespace of [
    "CCT_CLIENT_APPLICATION",
    "CCT_OPERATING_ENTITY_APPLICATION",
    "CCT_LOCAL_PARTNER_APPLICATION",
  ]) {
    assert.match(scenario, new RegExp(`730320000000000001[^。\\n]*${namespace}|${namespace}[^。\\n]*730320000000000001`, "u"));
  }

  const ownershipContract = `${logicalDatabase}\n${scenario}\n${physicalDecisions}`;
  assert.match(
    ownershipContract,
    /CCT 只(?:接收|回填)[^。]*Local Partner 主 Case\/Decision[^。]*Risk[^。]*(?:关联人 KYC 子 Case|子 Case)[^。]*(?:依赖|管理)/u,
  );
  assert.match(ownershipContract, /application_id[^。]*role_type[^。]*role_id[^。]*qualification_type[^。]*submission_content_hash/u);
});

test("Dispute documentation freezes the balanced seven-table aggregate and correction contracts", () => {
  const scenario = readFileSync(scenarioPath, "utf8");
  const logicalDatabase = readFileSync(logicalDatabasePath, "utf8");
  const physicalDecisions = readFileSync(physicalDecisionsPath, "utf8");
  const logicalDispute = section(logicalDatabase, "### 4.7 Dispute（7 张）", "### 4.8 本地可靠执行");
  const scenarioDispute = section(scenario, "## 7. SCN-RISK-06", "## 8. 本地可靠执行");
  const physicalDispute = section(physicalDecisions, "### 10.3 Dispute", "## 11. CHECK");
  const disputeContract = `${logicalDispute}\n${scenarioDispute}\n${physicalDispute}`;

  const logicalTables = [...logicalDispute.matchAll(/^\| (dispute_[a-z0-9_]+) \|/gmu)]
    .map((match) => `t_${match[1]}`)
    .sort();
  const scenarioTables = [...scenarioDispute.matchAll(/^\| `(t_dispute_[a-z0-9_]+)` \|/gmu)]
    .map((match) => match[1])
    .sort();
  assert.deepEqual(logicalTables, [...DISPUTE_TABLES].sort());
  assert.deepEqual(scenarioTables, [...DISPUTE_TABLES].sort());
  assert.match(physicalDispute, /Dispute[^。\n]*(?:7 张|7 表)/u);

  for (const field of REMOVED_DISPUTE_FIELDS) {
    const fieldPattern = new RegExp(`(?<![a-z0-9_])${field}(?![a-z0-9_])`, "u");
    for (const [documentName, disputeSection] of [
      ["逻辑库文档", logicalDispute],
      ["现实场景", scenarioDispute],
      ["物理决策", physicalDispute],
    ]) {
      assert.doesNotMatch(disputeSection, fieldPattern, `${documentName} Dispute 章节仍引用已删除字段：${field}`);
    }
  }

  const liabilityCatalogRow = logicalDispute.match(/^\| dispute_liability_decision \|[^\n]+/mu)?.[0] ?? "";
  const liabilityExampleRow = scenarioDispute.match(/^\| `t_dispute_liability_decision` \|[^\n]+/mu)?.[0] ?? "";
  for (const row of [liabilityCatalogRow, liabilityExampleRow]) {
    assert.match(row, /\boutcome_id\b/u, "Liability 必须以 Outcome ID 作为稳定身份");
    assert.match(row, /\bsecurity_deposit_use_allowed\b/u, "Liability 必须冻结保证金使用许可");
  }
  assert.match(
    disputeContract,
    /(?:共享主键[^。\n]*`?outcome_id|`?outcome_id`?[^。\n]*共享主键)/u,
    "Dispute 文档必须明确 Liability 与 Outcome 共享 outcome_id 主键",
  );

  for (const sourceType of DISPUTE_DECISION_SOURCE_TYPES) {
    assert.match(disputeContract, new RegExp(`\\b${sourceType}\\b`, "u"), `Dispute 缺少决定来源：${sourceType}`);
  }

  const requestExampleRow = scenarioDispute.match(/^\| `t_dispute_submission_request` \|[^\n]+/mu)?.[0] ?? "";
  for (const marker of ["status=UNKNOWN", "active_slot=1", "resolved_time=NULL"]) {
    assert.match(requestExampleRow, new RegExp(marker, "u"), `UNKNOWN Request 示例缺少：${marker}`);
  }
  assert.match(
    disputeContract,
    /UNKNOWN[^。\n]*(?:占用|保留)[^。\n]*(?:active_slot|进行中|活动槽)/u,
    "UNKNOWN Request 必须继续占用唯一活动槽",
  );

  for (const compositeIdentity of [
    /\(package_id\s*,\s*dispute_case_id\)[^。\n]*\(package_id\s*,\s*dispute_case_id\)/u,
    /\(current_outcome_id\s*,\s*dispute_case_id\)[^。\n]*\(outcome_id\s*,\s*dispute_case_id\)/u,
    /\(supersedes_outcome_id\s*,\s*dispute_case_id\)[^。\n]*\(outcome_id\s*,\s*dispute_case_id\)/u,
  ]) {
    assert.match(disputeContract, compositeIdentity, "Dispute 缺少复合同案引用契约");
  }
  assert.match(disputeContract, /outcome_version[^。\n]*(?:连续|相邻)/u, "Outcome Version 必须连续");
  assert.match(disputeContract, /supersedes_outcome_id[^。\n]*(?:直接前序|上一版|前一版)/u, "更正必须引用直接前序 Outcome");
  assert.match(disputeContract, /current_outcome_id[^。\n]*(?:链尾|最新版本)/u, "Case 当前指针必须指向同案链尾");
  assert.match(disputeContract, /Provider[^。\n]*Client[^。\n]*Operations[^。\n]*(?:Version 1|V1)/u);
  assert.match(disputeContract, /Client\/Operations[^。\n]*(?:只允许|只能)[^。\n]*(?:LOST|Lost)/u);
  assert.match(disputeContract, /(?:Version 2\+|V2\+|Version N)[^。\n]*(?:只允许|只有)[^。\n]*OPERATIONS_CORRECTION/iu);
  for (const trigger of ["PROVIDER_OUTCOME", "CLIENT_DISPOSITION", "OPERATIONS_DISPOSITION"]) {
    assert.match(disputeContract, new RegExp(`\\b${trigger}\\b`, "u"), `直接 LOST 缺少可信 Trigger：${trigger}`);
  }
  assert.match(disputeContract, /CLOSED[^。\n]*(?:自环|→CLOSED)[^。\n]*OPERATIONS_CORRECTION/u);
  assert.match(disputeContract, /首次[^。\n]*(?:WON\/LOST|结果)[^。\n]*V1 Outcome[^。\n]*(?:相同|等于|一致)/iu);
  assert.match(disputeContract, /trigger_id[^。\n]*decision_source_id/iu);
  assert.match(disputeContract, /(?:交换|错配)[^。\n]*(?:Trigger|更正命令)[^。\n]*(?:拒绝|不得)/u);
  assert.match(disputeContract, /Aggregate Version[^。\n]*(?:Case Version|Case[^。\n]*Version)[^。\n]*(?:一致|等于)/iu);
  assert.match(
    scenarioDispute,
    /UNDER_REVIEW→SUBMITTED[^。\n]*trigger_type=SUBMISSION_REQUEST[^。\n]*trigger_id=720041/u,
  );
  assert.match(
    scenarioDispute,
    /LOST→CLOSED[^。\n]*trigger_type=CASE_CLOSE_COMMAND[^。\n]*trigger_id=risk-command:720001-close/u,
  );
  assert.match(disputeContract, /Evaluation\/Effect[^。\n]*(?:成对|同时保存)/u);
  assert.match(disputeContract, /Effect[^。\n]*(?:属于|同一)[^。\n]*Evaluation/u);
  assert.match(disputeContract, /REAL\/APPLIED\/LOCAL_SYNC\/SUCCEEDED/u);
  assert.match(disputeContract, /(?:人工处置|人工更正|人工路径)[^。\n]*decided_by/u);
  assert.match(disputeContract, /decision_evaluation_id=NULL[^。\n]*action_effect_id=NULL[^。\n]*decided_by=/u);
  assert.match(disputeContract, /(?:禁止双来源|禁止同时带规则与人工来源)/u);
  assert.match(disputeContract, /(?:Timer|Deadline|沉默)[^。\n]*(?:不得|拒绝|不能)[^。\n]*(?:LOST|Lost)/u);

  for (const marker of [
    "CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1",
    "disputeCaseId",
    "versionNo",
    "evidenceType",
    "fileId",
    "contentHash",
  ]) {
    assert.ok(disputeContract.includes(marker), `Evidence Package Canonical 契约缺少：${marker}`);
  }
  assert.match(disputeContract, /evidenceType[^。\n]*fileId[^。\n]*(?:排序|升序)/u);
  assert.match(disputeContract, /UTF-8[^。\n]*SHA-256/u);
  assert.match(scenarioDispute, /Client 沉默[^。\n]*(?:不得|不能)[^。\n]*(?:Lost|LOST)/u);
});

test("RCO-I09 uses retrievable Assessment and Evaluation coordinates without a Risk result aggregate", () => {
  const scenario = readFileSync(scenarioPath, "utf8");
  const logicalDatabase = readFileSync(logicalDatabasePath, "utf8");
  const physicalDecisions = readFileSync(physicalDecisionsPath, "utf8");
  const settlementDesign = readFileSync(settlementDesignPath, "utf8");
  const currentContract = `${logicalDatabase}\n${scenario}\n${physicalDecisions}\n${settlementDesign}`;

  assert.match(currentContract, /RCO-I09[^。\n]*assessment_id[^。\n]*evaluation_id/u);
  assert.match(currentContract, /assessment_id[^。]*evaluation_id[^。]*(?:不可变|immutable)[^。]*(?:可检索|retrievable)/iu);
  for (const responseField of [
    "decision",
    "reason_codes[]",
    "matched_controls[]",
    "required_amount",
    "required_currency",
    "control_set_hash",
  ]) {
    assert.ok(currentContract.includes(responseField), `RCO-I09 最小响应缺少：${responseField}`);
  }
  const assessmentCatalog = logicalDatabase.match(/\| settlement_risk_assessment \|[^\n]+/u)?.[0] ?? "";
  for (const proofField of [
    "checkpoint_type",
    "role_type",
    "role_id",
    "operating_entity_id",
    "gross_amount",
    "currency",
    "bank_country",
    "source_fact_type",
    "source_fact_id",
    "source_fact_version",
    "input_hash",
  ]) {
    assert.match(assessmentCatalog, new RegExp(`\\b${proofField}\\b`, "u"), `Settlement Assessment 缺少证明字段：${proofField}`);
  }
  assert.match(currentContract, /Finance[^。]*冻结完整[^。]*eligibility_hash/u);
  assert.match(currentContract, /同一[^。]*(?:幂等命令|命令幂等|idempotent command)[^。]*(?:复用|返回)[^。]*assessment_id[^。]*evaluation_id/iu);
  assert.match(currentContract, /Assessment\.checkpoint_type[^。]*Decision Point\.point_code/u);
  assert.match(currentContract, /Assessment\.input_hash[^。]*Evaluation\.input_snapshot_hash/u);
  assert.match(currentContract, /ALLOW, SUCCEEDED, ALLOW, ALLOW/u);
  assert.match(currentContract, /RESTRICT, SUCCEEDED, ALLOW, DENY/u);
  assert.match(currentContract, /NULL, TIMED_OUT, NULL, NULL/u);
  assert.doesNotMatch(currentContract, /eligibility_result_(?:id|version|hash)/u);
  assert.doesNotMatch(
    settlementDesign,
    /RCO-I09\s*当前只返回\s*`?ALLOW\/RESTRICT`?/u,
    "当前 RCO-I09 已返回完整可重放契约，旧二值响应只能标注为实施前历史",
  );
});

test("Manual Payout scenarios re-evaluate all four checkpoints while preserving exact release and Finance boundaries", () => {
  const scenario = readFileSync(scenarioPath, "utf8");

  assert.match(scenario, /SETTLEMENT_BLOCK[^。\n]*20,000 USD[^。\n]*MIN_SECURITY_DEPOSIT/u);
  assert.match(scenario, /Payout Create[^。\n]*RESTRICT[^。\n]*20,000/u);
  assert.match(scenario, /精确释放[^。\n]*SETTLEMENT_BLOCK[^。\n]*MIN_SECURITY_DEPOSIT[^。\n]*ACTIVE/u);

  const checkpointPairs = [...scenario.matchAll(
    /(Payout Create|Final Approval|Execution Create|External Submit)[^\n]*evaluation_id=(\d+)[^\n]*assessment_id=(\d+)/gu,
  )];
  assert.deepEqual(
    checkpointPairs.map((match) => match[1]),
    ["Payout Create", "Final Approval", "Execution Create", "External Submit"],
  );
  assert.equal(new Set(checkpointPairs.map((match) => match[2])).size, 4);
  assert.equal(new Set(checkpointPairs.map((match) => match[3])).size, 4);

  assert.match(scenario, /同一[^。]*(?:幂等命令|命令幂等)[^。]*(?:复用|返回)[^。]*原[^。]*Evaluation[^。]*Assessment/u);
  assert.match(scenario, /Final Approval[^。]*不能复用 Payout Create/u);
  assert.match(scenario, /Execution Create[^。]*External Submit[^。]*BANK_CASH/su);
  assert.match(scenario, /Payout Create[^。]*预分配[^。]*source_fact_version=1/u);
  assert.match(scenario, /Final Approval[^。]*变更前[^。]*expected[^。]*version/iu);
  assert.match(scenario, /Execution Create[^。]*预分配[^。]*source_fact_version=1/u);
  assert.match(scenario, /External Submit[^。]*CREATED[^。]*变更前[^。]*expected[^。]*version/iu);
  assert.match(scenario, /source_fact_version[^。]*1\.\.2147483647/u);
  assert.match(scenario, /gross_amount=owner_debit_amount[^。]*cash_required_amount/u);
  assert.match(scenario, /requested_amount[^。]*beneficiary_amount[^。]*(?:不得|不能)/u);
  assert.match(scenario, /UNKNOWN[^。]*阻断[^。]*Retry/u);
  assert.match(scenario, /Local Partner[^。]*不(?:查询|读取|伪造)[^。]*CLIENT_SECURITY_DEPOSIT\/NONE[^。]*CLIENT_RECOVERY_RECEIVABLE\/NONE/u);
  assert.match(scenario, /beneficiary_account\.bank_country/u);
  assert.match(scenario, /Payout[^。]*(?:清偿|出款)[^。]*Client-to-Beneficiary Disbursement[^。]*(?:独立|不得复用|不属于)/u);
});

test("Settlement authority freezes lineage, state, lock order, timer replay and the current Limit graph", () => {
  const scenario = readFileSync(scenarioPath, "utf8");
  const logicalDatabase = readFileSync(logicalDatabasePath, "utf8");
  const settlementRelations = section(logicalDatabase, "### 5.4 Settlement", "## 6. 状态机");
  const limitRelations = section(
    logicalDatabase,
    "### 5.3 Evaluation、Action、Assessment 与 Limit",
    "### 5.4 Settlement",
  );

  for (const relation of [
    /DECISION_RELEASE \|\|--o\{ DECISION_EVALUATION/u,
    /DECISION_EVALUATION \|\|--o\{ ACTION_EFFECT/u,
    /ACTION_EFFECT \|\|--o\| SETTLEMENT_CONTROL/u,
    /DECISION_EVALUATION \|\|--o\| SETTLEMENT_RISK_ASSESSMENT/u,
    /SETTLEMENT_CONTROL \|\|--\|\{ SETTLEMENT_CONTROL_SCOPE/u,
    /SETTLEMENT_CONTROL \|\|--\|\{ SETTLEMENT_CONTROL_STATE_CHANGE/u,
  ]) {
    assert.match(settlementRelations, relation);
  }

  assert.match(logicalDatabase, /NULL ?→ ?ACTIVE ?→ ?RELEASED\|EXPIRED/u);
  assert.doesNotMatch(section(logicalDatabase, "### 6.5 Settlement", "### 6.6 Dispute"), /SCHEDULED/u);
  assert.match(scenario, /Role Range[^。]*Next-Key\/Gap Lock/u);
  assert.match(scenario, /Role Range -> control_id 升序 Root -> Scope\/Event/u);
  assert.match(scenario, /取得[^。]*锁后[^。]*数据库 UTC/u);
  assert.match(scenario, /control_id[^。]*expected_version[^。]*幂等键/u);
  assert.match(scenario, /settlement-control-expiry:v1:<control_id>:<expected_version>:<effective_to>/u);
  assert.match(scenario, /RELEASED\/EXPIRED[^。]*(?:不复活|不得复活|永不复活)/u);

  for (const removedLimitTable of [
    "LIMIT_PLAN",
    "LIMIT_PLAN_VERSION",
    "LIMIT_PLAN_ITEM",
    "LIMIT_DEFINITION",
  ]) {
    assert.ok(!limitRelations.includes(removedLimitTable), `当前 Limit 关系图仍引用删除表：${removedLimitTable}`);
  }
  assert.match(limitRelations, /DECISION_POINT_OBLIGATION \|\|--o\{ DECISION_POINT_LIMIT_BINDING/u);
  assert.match(limitRelations, /LIMIT_VERSION \|\|--o\{ DECISION_POINT_LIMIT_BINDING/u);
});

test("Historical Limit totals are explicitly scoped before Settlement Eligibility implementation", () => {
  const priorLimitPlan = readFileSync(priorLimitPlanPath, "utf8");

  assert.match(
    priorLimitPlan,
    /72 tables \/ 866 fields[^。]*Settlement Eligibility[^。]*(?:实施前|历史阶段)/u,
  );
});

test("Historical Settlement checkpoint records defer to the current Manual Payout alignment", () => {
  const historicalDesign = readFileSync(settlementDesignPath, "utf8");
  const currentAlignment = readFileSync(payoutCheckpointAlignmentPath, "utf8");

  assert.match(historicalDesign, /历史记录[^。]*2026-08-29-risk-manual-payout-eligibility-checkpoint-alignment-implementation-plan\.md/su);
  for (const checkpoint of [
    "PAYOUT_CREATION",
    "PAYOUT_FINAL_APPROVAL",
    "PAYOUT_EXECUTION_CREATION",
    "PAYOUT_EXTERNAL_SUBMISSION",
  ]) assert.match(currentAlignment, new RegExp(`\\b${checkpoint}\\b`, "u"));
  assert.match(currentAlignment, /source_fact_version[^。]*变更前[^。]*expected version/iu);
  assert.match(currentAlignment, /gross_amount=owner_debit_amount=cash_required_amount/u);
  assert.match(currentAlignment, /71 tables \/ 865 fields \/ 106 unique indexes \/ 74 access indexes \/ 111 local relations \/ 9 one-to-one relations \/ 8 Diagram Groups \/ 83 CHECK constraints/u);
});

test("Risk historical Decision Engine plan explicitly defers current Limit semantics", () => {
  const priorPlan = readFileSync(priorDecisionPlanPath, "utf8");

  assert.match(priorPlan, /Limit 相关结构与全库统计.*2026-08-28-risk-limit-balanced-simplification-design\.md[^。]*取代/su);
  assert.match(priorPlan, /历史阶段基线为[^。]*75 tables \/ 897 fields/u);
  assert.doesNotMatch(priorPlan, /Action Version 与 Limit Plan Version 严格二选一/u);
  assert.doesNotMatch(priorPlan, /纯 Limit Plan/u);
});

test("Risk real-world scenarios demonstrate the approved cross-domain failure and recovery paths", () => {
  assert.ok(existsSync(scenarioPath), `Risk 现实场景尚未生成：${scenarioPath}`);
  const scenario = readFileSync(scenarioPath, "utf8");
  for (const marker of [
    "SCN-RISK-01",
    "SCN-RISK-02",
    "SCN-RISK-03",
    "SCN-RISK-04",
    "SCN-RISK-05",
    "SCN-RISK-06",
    "SHADOW",
    "SIMULATED",
    "PAYMENT_UNKNOWN",
    "LIMIT_EXCEEDED",
    "DisputeEvidenceRequested",
    "lock_version",
  ]) {
    assert.match(scenario, new RegExp(marker, "u"), `现实场景缺少关键链路：${marker}`);
  }
  assert.doesNotMatch(scenario, /t_velocity_bucket|真实流量灰度|跨库外键/u);
});

test("Risk decision examples preserve the balanced release, shadow and action contracts", () => {
  const scenario = readFileSync(scenarioPath, "utf8");

  for (const marker of [
    "决策点、规则和发布逐表映射",
    "Evaluation、Assessment 与 Action 8 表",
    "t_release_audit_event",
    "STATIC_CHECK",
    "APPROVAL",
    "STATE_CHANGE",
    "t_evaluation_rule_trace",
    "source_type=OBLIGATION",
    "source_effect_id",
    "FIRST_TERMINAL",
    "FAIL_CLOSED",
    "MOST_RESTRICTIVE",
    "COOLDOWN",
    "BLOCKED_IP",
  ]) {
    assert.match(scenario, new RegExp(marker, "u"), `现实场景缺少平衡精简语义：${marker}`);
  }

  assert.match(
    scenario,
    /DRAFT→VALIDATING→SHADOWING→REVIEW_PENDING→APPROVED→ACTIVE→SUPERSEDED/u,
  );
  assert.match(scenario, /8 项强制 STATIC_CHECK/u);
  assert.match(scenario, /两个不同 reviewer[^。]*同一 Shadow Run/u);
  assert.match(
    scenario,
    /STATIC_CHECK[^。]*\(event_time,audit_event_id\)[^。]*严格早于[^。]*SHADOWING/u,
  );
  assert.match(
    scenario,
    /completed_time[^。]*SHADOWING（含）[^。]*REVIEW_PENDING（含）/u,
  );
  assert.match(
    scenario,
    /APPROVAL[^。]*\(event_time,audit_event_id\)[^。]*严格晚于[^。]*REVIEW_PENDING/u,
  );
  assert.match(
    scenario,
    /第二次批准[^。]*同一 approval_request_id[^。]*同一 event_time[^。]*REVIEW_PENDING→APPROVED[^。]*APPROVED→ACTIVE[^。]*ACTIVE→SUPERSEDED/u,
  );
  const shadowClause = scenario.match(/Shadow\/Replay[^。\n]*SIMULATED[^。\n]*/u)?.[0] ?? "";
  for (const prohibitedEffect of [
    "Risk Restriction",
    "Risk List Entry",
    "Limit Reservation Group",
    "Limit Reservation",
    "Limit Movement",
    "Settlement Control",
    "Dispute Liability Decision",
    "Velocity Event",
    "compensation_event",
  ]) {
    const exactListItem = new RegExp(`(?:^|[、，；]|真实 |或 )${prohibitedEffect}(?=[、，；]| 或 |$)`, "u");
    assert.match(shadowClause, exactListItem, `旁路无副作用契约缺少：${prohibitedEffect}`);
  }
  assert.match(
    scenario,
    /idempotency_key\/execution_type\/execution_status\/attempt_count\/started_time\/completed_time\/last_error_code\/last_error_summary[^。]*全为空/u,
  );
  assert.match(scenario, /Feature Version[^。]*feature_code[^。]*handler_code/u);
  assert.match(scenario, /Action Version[^。]*action_code[^。]*handler_code/u);
  assert.match(scenario, /Rule Set Version[^。]*decision_point_id[^。]*rule_set_code/u);
  assert.match(scenario, /LITERAL[^。]*value_sequence[^。]*string_value/u);
  assert.match(scenario, /SHADOW[^。]*baseline_evaluation_id[^。]*decision_changed_flag/u);
  assert.match(scenario, /effect_status[^。]*execution_status/u);
  assert.doesNotMatch(scenario, /source_type=SYSTEM_OBLIGATION/u);

  const exampleRows = Object.fromEntries(
    ["t_release_audit_event", "t_release_shadow_run", "t_decision_evaluation"].map((table) => [
      table,
      scenario.match(new RegExp("\\| `" + table + "` \\|[^\\n]+", "u"))?.[0] ?? "",
    ]),
  );
  for (const [table, exampleRow] of Object.entries(exampleRows)) {
    const timestamps = exampleRow.match(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z/gu) ?? [];
    assert.ok(timestamps.length > 0, `现实场景缺少时间示例：${table}`);
    for (const timestamp of timestamps) {
      assert.match(timestamp, /\.\d{6}Z$/u, `${table} 时间必须使用 UTC 六位微秒：${timestamp}`);
    }
  }

  const timestamp = (row, pattern, label) => {
    const value = row.match(pattern)?.[1];
    assert.ok(value, `现实场景缺少命名时间：${label}`);
    assert.match(value, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$/u, `${label} 格式错误`);
    return value;
  };
  const lastStaticTime = timestamp(
    exampleRows.t_release_audit_event,
    /最后一条 STATIC_CHECK[^`]*`event_time=([^`]+)`/u,
    "last STATIC_CHECK event_time",
  );
  const approvalTime = timestamp(
    exampleRows.t_release_audit_event,
    /approval_request_id=APR-260002-02, event_time=([^`,]+)/u,
    "completing APPROVAL event_time",
  );
  const shadowingTime = timestamp(
    exampleRows.t_release_shadow_run,
    /SHADOWING State Change 为 `([^`]+)`/u,
    "SHADOWING event_time",
  );
  const completedTime = timestamp(
    exampleRows.t_release_shadow_run,
    /completed_time=([^`,]+)/u,
    "Shadow completed_time",
  );
  const reviewPendingTime = timestamp(
    exampleRows.t_release_shadow_run,
    /REVIEW_PENDING State Change 为 `([^`]+)`/u,
    "REVIEW_PENDING event_time",
  );
  assert.ok(lastStaticTime < shadowingTime, "STATIC_CHECK 必须严格早于 SHADOWING");
  assert.ok(shadowingTime <= completedTime, "Shadow completed_time 不得早于 SHADOWING");
  assert.ok(completedTime <= reviewPendingTime, "Shadow completed_time 不得晚于 REVIEW_PENDING");
  assert.ok(reviewPendingTime < approvalTime, "完成审批必须严格晚于 REVIEW_PENDING");
});

test("Risk logical decision documentation matches the generated physical contract", () => {
  const logicalDatabase = readFileSync(logicalDatabasePath, "utf8");

  assert.match(
    logicalDatabase,
    /merge_policy 仅允许闭集 FIRST\/LAST\/MOST_RESTRICTIVE\/UNION\/MIN\/MAX/u,
  );
  assert.match(logicalDatabase, /Action 参数[^。]*LITERAL\/FEATURE\/CONTEXT/u);
  assert.doesNotMatch(logicalDatabase, /Action 参数[^。\n]*\bConstant\b/u);
  assert.doesNotMatch(
    logicalDatabase,
    /MAX_EXPIRE_TIME|UPSERT_AND_EXTEND|SET_UNION|REJECT_CONFLICT/u,
  );
  assert.doesNotMatch(
    logicalDatabase,
    /直接 LOST[^。\n]*以[^。\n]*(?:Deadline Timer|Client 沉默|客户端沉默|超时)[^。\n]*为 Trigger/u,
    "Deadline 或 Client 沉默不得直接形成 DisputeLost",
  );
  assert.match(
    logicalDatabase,
    /直接 LOST[^。\n]*已验证 Provider LOST[^。\n]*已授权 Client[^。\n]*已授权 Operations/u,
  );

  const auditCatalogRow = logicalDatabase.match(/\| release_audit_event \|[^\n]+/u)?.[0] ?? "";
  for (const field of [
    "audit_event_id",
    "release_id",
    "event_type",
    "event_time",
    "check_code",
    "check_status",
    "severity",
    "error_code",
    "error_summary",
    "shadow_run_id",
    "approval_request_id",
    "approval_status",
    "reviewer_id",
    "review_comment",
    "from_status",
    "to_status",
    "reason_code",
    "trigger_type",
    "trigger_id",
    "actor_id",
    "aggregate_version",
  ]) {
    assert.match(auditCatalogRow, new RegExp(`\\b${field}\\b`, "u"), `发布审计目录缺少字段：${field}`);
  }

  assert.match(logicalDatabase, /DECISION_RELEASE \|\|--o\{ RELEASE_SHADOW_RUN : baseline/u);
  assert.match(logicalDatabase, /DECISION_RELEASE \|\|--o\{ RELEASE_SHADOW_RUN : candidate/u);
  assert.match(logicalDatabase, /EVALUATION_ACTION_INTENT \}o--o\| ACTION_EFFECT : merged_into/u);
  assert.doesNotMatch(logicalDatabase, /EVALUATION_ACTION_INTENT \}o--\|\| ACTION_EFFECT : merged_into/u);
});
