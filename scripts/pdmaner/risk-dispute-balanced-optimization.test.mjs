import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { TABLES as DISPUTE_TABLES } from "./risk/manifests/07-dispute.mjs";
import * as MODEL_POLICY from "./risk/model-policy.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const modelPath = path.join(workspaceRoot, "risk-compliance-service/docs/database/risk-compliance.pdma.json");

function table(key) {
  const result = DISPUTE_TABLES.find((item) => item.defKey === key);
  assert.ok(result, `缺少 Dispute 表 ${key}`);
  return result;
}

function fieldNames(target) {
  return target.fields.map((field) => field.defKey);
}

function field(target, key) {
  const result = target.fields.find((item) => item.defKey === key);
  assert.ok(result, `${target.defKey} 缺少字段 ${key}`);
  return result;
}

function index(target, key) {
  const result = target.indexes.find((item) => item.defKey === key);
  assert.ok(result, `${target.defKey} 缺少索引 ${key}`);
  return result;
}

test("Dispute keeps seven lifecycle tables while removing five duplicate fields", () => {
  assert.deepEqual(DISPUTE_TABLES.map((item) => item.defKey), [
    "dispute_case",
    "dispute_state_change",
    "dispute_evidence_package",
    "dispute_evidence_ref",
    "dispute_submission_request",
    "dispute_outcome",
    "dispute_liability_decision",
  ]);

  assert.deepEqual(fieldNames(table("dispute_evidence_package")), [
    "package_id", "dispute_case_id", "version_no", "status", "content_hash", "create_time", "update_time",
  ]);
  assert.deepEqual(fieldNames(table("dispute_evidence_ref")), [
    "evidence_id", "package_id", "evidence_type", "file_id", "content_hash", "create_time",
  ]);
  assert.deepEqual(fieldNames(table("dispute_submission_request")), [
    "request_id", "dispute_case_id", "package_id", "request_no", "status", "active_slot",
    "channel_dispute_action_id", "action_source_version", "requested_time", "resolved_time", "version",
    "create_time", "update_time",
  ]);

  const liability = table("dispute_liability_decision");
  assert.deepEqual(fieldNames(liability), [
    "outcome_id", "decision_evaluation_id", "action_effect_id", "recommended_principal_bearer",
    "principal_bearer", "channel_fee_bearer", "platform_error_flag", "channel_recovery_required",
    "security_deposit_use_allowed", "override_reason_code", "reason_code", "decided_by", "decided_time",
    "create_time",
  ]);
  assert.equal(field(liability, "outcome_id").primaryKey, true);
  assert.equal(liability.indexes.some((item) => item.defKey === "uk_dispute_liability"), false);
  assert.equal(liability.indexes.some((item) => item.defKey === "idx_liability_release"), false);
  assert.deepEqual(
    liability.relations.find((relation) => relation.childField === "outcome_id"),
    {
      childField: "outcome_id",
      parentTable: "dispute_outcome",
      parentField: "outcome_id",
      cardinality: "1:1",
      childTable: "dispute_liability_decision",
    },
  );
  assert.equal(liability.relations.some((relation) => relation.childField === "release_id"), false);
  assert.equal(DISPUTE_TABLES.flatMap((item) => item.fields).length, 84);
});

test("Dispute freezes the approved Case states, Outcome sources and exact keys", () => {
  const disputeCase = table("dispute_case");
  const caseStatus = field(disputeCase, "status");
  assert.deepEqual(caseStatus.closedValues, [
    "EVIDENCE_REQUIRED", "UNDER_REVIEW", "SUBMITTED", "WON", "LOST", "CLOSED",
  ]);
  assert.deepEqual(index(disputeCase, "uk_dispute_outcome_ptr").fields, ["current_outcome_id", "dispute_case_id"]);

  const evidencePackage = table("dispute_evidence_package");
  assert.deepEqual(index(evidencePackage, "uk_dispute_package_identity").fields, ["package_id", "dispute_case_id"]);

  const evidence = table("dispute_evidence_ref");
  assert.deepEqual(index(evidence, "uk_dispute_evidence").fields, ["package_id", "evidence_type", "file_id"]);

  const request = table("dispute_submission_request");
  assert.deepEqual(index(request, "idx_dispute_req_package").fields, ["package_id", "dispute_case_id"]);

  const outcome = table("dispute_outcome");
  assert.equal(outcome.comment, "保存 Channel 或已授权处置形成的 WON/LOST 结果及 Operations Correction 不可变版本");
  assert.equal(field(outcome, "reason_code").openSource, "Risk 统一争议结果原因目录");
  assert.deepEqual(field(outcome, "decision_source_type").closedValues, [
    "CHANNEL_ACTION", "CLIENT_DISPOSITION", "OPERATIONS_DISPOSITION", "OPERATIONS_CORRECTION",
  ]);
  assert.deepEqual(index(outcome, "uk_dispute_out_source"), {
    defKey: "uk_dispute_out_source",
    unique: true,
    fields: ["decision_source_type", "decision_source_id", "source_version"],
  });
  assert.deepEqual(index(outcome, "uk_dispute_outcome_identity").fields, ["outcome_id", "dispute_case_id"]);
  assert.deepEqual(index(outcome, "uk_dispute_out_prev"), {
    defKey: "uk_dispute_out_prev",
    unique: true,
    fields: ["supersedes_outcome_id", "dispute_case_id"],
  });
  assert.deepEqual(index(outcome, "idx_dispute_out_action").fields, ["channel_dispute_action_id"]);

  const liability = table("dispute_liability_decision");
  assert.equal(
    liability.comment,
    "保存争议 Outcome 的责任分类、规则 Evaluation/Action Effect 或人工授权证据",
  );
});

test("Dispute CHECK contracts are null-safe and bind every approved row matrix", () => {
  const expected = {
    ck_dispute_deadlines: "channel_dispute_source_version>0 AND disputed_amount>0 AND client_response_due_time<channel_response_due_time",
    ck_dispute_case_outcome: "(status IN ('EVIDENCE_REQUIRED','UNDER_REVIEW','SUBMITTED') AND current_outcome_id IS NULL) OR (status IN ('WON','LOST','CLOSED') AND current_outcome_id IS NOT NULL)",
    ck_dispute_state: "aggregate_version>0 AND ((aggregate_version=1 AND from_status IS NULL AND to_status='EVIDENCE_REQUIRED') OR (aggregate_version>1 AND from_status IS NOT NULL AND ((from_status='EVIDENCE_REQUIRED' AND to_status IN ('UNDER_REVIEW','LOST')) OR (from_status='UNDER_REVIEW' AND to_status IN ('SUBMITTED','LOST')) OR (from_status='SUBMITTED' AND to_status IN ('WON','LOST')) OR (from_status IN ('WON','LOST') AND to_status='CLOSED') OR (from_status='CLOSED' AND to_status='CLOSED'))))",
    ck_dispute_package_version: "version_no>0",
    ck_dispute_request_slot_time: "request_no>0 AND requested_time IS NOT NULL AND ((status IN ('CREATED','SUBMITTED','UNKNOWN') AND active_slot IS NOT NULL AND active_slot=1 AND resolved_time IS NULL) OR (status IN ('SUCCEEDED','FAILED','CANCELLED') AND active_slot IS NULL AND resolved_time IS NOT NULL AND requested_time<=resolved_time))",
    ck_dispute_request_action: "((channel_dispute_action_id IS NULL AND action_source_version IS NULL) OR (channel_dispute_action_id IS NOT NULL AND action_source_version IS NOT NULL AND action_source_version>0)) AND ((status='CREATED' AND channel_dispute_action_id IS NULL) OR (status IN ('SUBMITTED','UNKNOWN','SUCCEEDED') AND channel_dispute_action_id IS NOT NULL) OR status IN ('FAILED','CANCELLED'))",
    ck_dispute_outcome_version: "outcome_version>0 AND source_version>0 AND ((outcome_version=1 AND supersedes_outcome_id IS NULL) OR (outcome_version>1 AND supersedes_outcome_id IS NOT NULL AND supersedes_outcome_id<>outcome_id))",
    ck_dispute_outcome_source: "(decision_source_type='CHANNEL_ACTION' AND outcome_version=1 AND supersedes_outcome_id IS NULL AND channel_dispute_action_id IS NOT NULL AND decision_source_id=channel_dispute_action_id) OR (decision_source_type IN ('CLIENT_DISPOSITION','OPERATIONS_DISPOSITION') AND outcome_version=1 AND outcome='LOST' AND supersedes_outcome_id IS NULL AND channel_dispute_action_id IS NULL) OR (decision_source_type='OPERATIONS_CORRECTION' AND outcome_version>1 AND supersedes_outcome_id IS NOT NULL)",
    ck_liability_recommendation: "recommended_principal_bearer IS NULL OR recommended_principal_bearer=principal_bearer OR override_reason_code IS NOT NULL",
    ck_liability_security_deposit: "security_deposit_use_allowed IN (0,1) AND ((decision_evaluation_id IS NOT NULL AND action_effect_id IS NOT NULL AND decided_by IS NULL) OR (decision_evaluation_id IS NULL AND action_effect_id IS NULL AND decided_by IS NOT NULL)) AND (security_deposit_use_allowed=0 OR principal_bearer='CLIENT')",
  };
  for (const [key, expression] of Object.entries(expected)) {
    assert.equal(MODEL_POLICY.CHECK_CONTRACTS[key], expression, `${key} 定义不准确`);
    assert.ok(MODEL_POLICY.CHECK_BINDINGS[key]?.length > 0, `${key} 缺少表绑定`);
  }
  assert.deepEqual(MODEL_POLICY.CHECK_BINDINGS.ck_dispute_state, ["dispute_state_change"]);
  assert.deepEqual(MODEL_POLICY.CHECK_BINDINGS.ck_dispute_request_slot_time, ["dispute_submission_request"]);
  assert.deepEqual(MODEL_POLICY.CHECK_BINDINGS.ck_liability_security_deposit, ["dispute_liability_decision"]);
});

test("Dispute persists three same-Case composite foreign-key contracts", () => {
  assert.deepEqual(MODEL_POLICY.COMPOSITE_FK_CONTRACTS, [
    {
      name: "fk_dispute_req_package_case",
      childTable: "t_dispute_submission_request",
      childFields: ["package_id", "dispute_case_id"],
      parentTable: "t_dispute_evidence_package",
      parentFields: ["package_id", "dispute_case_id"],
    },
    {
      name: "fk_dispute_case_current_out",
      childTable: "t_dispute_case",
      childFields: ["current_outcome_id", "dispute_case_id"],
      parentTable: "t_dispute_outcome",
      parentFields: ["outcome_id", "dispute_case_id"],
    },
    {
      name: "fk_dispute_out_prev_case",
      childTable: "t_dispute_outcome",
      childFields: ["supersedes_outcome_id", "dispute_case_id"],
      parentTable: "t_dispute_outcome",
      parentFields: ["outcome_id", "dispute_case_id"],
    },
  ]);

  assert.deepEqual(MODEL_POLICY.DISPUTE_AGGREGATE_CONTRACT, {
    rootTable: "dispute_case",
    rootKey: "dispute_case_id",
    currentOutcomePointer: "current_outcome_id",
    outcomeTable: "dispute_outcome",
    outcomeKey: "outcome_id",
    outcomeVersion: "outcome_version",
    predecessorKey: "supersedes_outcome_id",
    liabilityTable: "dispute_liability_decision",
    liabilitySharedKey: "outcome_id",
    actionEffectTable: "action_effect",
    actionEffectEvaluationKey: "evaluation_id",
    stateChangeTable: "dispute_state_change",
    packageTable: "dispute_evidence_package",
    requestTable: "dispute_submission_request",
    atomicWriteSet: [
      "dispute_outcome", "dispute_liability_decision", "dispute_case",
      "dispute_state_change", "compensation_event",
    ],
    invariants: [
      "REQUEST_PACKAGE_SAME_CASE",
      "CURRENT_OUTCOME_IS_SAME_CASE_CHAIN_TAIL",
      "OUTCOME_PREDECESSOR_IS_SAME_CASE_PREVIOUS_VERSION",
      "EVERY_OUTCOME_HAS_EXACTLY_ONE_LIABILITY",
      "CASE_RESULT_STATUS_MATCHES_CURRENT_OUTCOME",
      "INITIAL_DISPOSITION_ONLY_CREATES_LOST",
      "CORRECTION_SOURCE_REQUIRES_PREDECESSOR",
      "LOST_TRANSITION_REQUIRES_AUTHORIZED_SOURCE",
      "RESULT_TRANSITION_SOURCE_COORDINATE_MATCHES_OUTCOME",
      "CLOSED_SELF_TRANSITION_REQUIRES_TRUSTED_CORRECTION",
      "CORRECTION_TRIGGER_COORDINATE_MATCHES_OUTCOME",
      "STATE_CHANGE_VERSION_MATCHES_CASE_VERSION",
      "LIABILITY_REQUIRES_RULE_OR_ACTOR_PROVENANCE",
      "LIABILITY_RULE_EFFECT_BELONGS_TO_EVALUATION",
      "SECURITY_DEPOSIT_PERMISSION_REQUIRES_LOST_OUTCOME",
    ],
  });
});

test("Dispute freezes the Evidence Package canonical hash contract", () => {
  assert.deepEqual(MODEL_POLICY.DISPUTE_EVIDENCE_PACKAGE_CANONICALIZATION, {
    canonicalizerCode: "RISK_DISPUTE_EVIDENCE_PACKAGE_V1",
    schema: "CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1",
    canonicalFormat: "FIXED_JSON_FIELD_ORDER",
    textEncoding: "UTF-8",
    digestAlgorithm: "SHA-256",
    digestEncoding: "LOWERCASE_HEX",
    bigIntEncoding: "CANONICAL_POSITIVE_DECIMAL_STRING",
    packageFields: ["disputeCaseId", "evidence", "schema", "versionNo"],
    evidenceOrder: ["evidenceType", "fileId:NUMERIC_ASC"],
    evidenceFields: ["contentHash", "evidenceType", "fileId"],
    duplicateIdentity: ["evidenceType", "fileId"],
    excludedFields: ["packageId", "status", "createTime", "updateTime"],
  });
});

test("Generated Risk model exposes the optimized Dispute contract", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const liability = entities.get("t_dispute_liability_decision");
  assert.ok(liability);
  assert.deepEqual(liability.fields.filter((item) => item.primaryKey).map((item) => item.defKey), ["outcome_id"]);
  assert.ok(liability.fields.some((item) => item.defKey === "security_deposit_use_allowed"));
  assert.equal(liability.fields.some((item) => item.defKey === "liability_id" || item.defKey === "release_id"), false);

  for (const contract of MODEL_POLICY.COMPOSITE_FK_CONTRACTS) {
    const child = entities.get(contract.childTable);
    assert.deepEqual(
      child?.properties?.compositeForeignKeys?.find((item) => item.name === contract.name),
      contract,
    );
  }
});
