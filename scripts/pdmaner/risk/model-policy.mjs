import { TABLE_GROUPS } from "./manifest.mjs";

const primaryKeys = (groupKey) => Object.freeze(TABLE_GROUPS[groupKey].map((table) => table.defKey));

const deepFreeze = (value) => {
  if (value === null || typeof value !== "object" || Object.isFrozen(value)) return value;
  for (const child of Object.values(value)) deepFreeze(child);
  return Object.freeze(value);
};

export const DIAGRAM_GROUPS = Object.freeze([
  Object.freeze({ defKey: "due_diligence", defName: "尽调与资格", primary: primaryKeys("due_diligence") }),
  Object.freeze({ defKey: "decision_engine", defName: "决策点与规则发布", primary: primaryKeys("decision_engine") }),
  Object.freeze({ defKey: "evaluation", defName: "评估与 Action", primary: primaryKeys("evaluation") }),
  Object.freeze({ defKey: "risk_data", defName: "冷却、名单与风险数据", primary: primaryKeys("risk_data") }),
  Object.freeze({ defKey: "limits", defName: "限额与占用", primary: primaryKeys("limits") }),
  Object.freeze({ defKey: "settlement", defName: "结算资格控制", primary: primaryKeys("settlement") }),
  Object.freeze({ defKey: "dispute", defName: "争议与举证", primary: primaryKeys("dispute") }),
  Object.freeze({ defKey: "reliability", defName: "可靠执行", primary: primaryKeys("reliability") }),
]);

export const FILE_REFERENCE_TUPLES = Object.freeze([
  Object.freeze({ table: "dd_evidence_ref", id: "file_id", hash: "content_hash" }),
  Object.freeze({ table: "screening_resolution", id: "evidence_file_id", hash: "evidence_content_hash" }),
  Object.freeze({ table: "dispute_evidence_ref", id: "file_id", hash: "content_hash" }),
]);

export const RISK_OWNED_RECOVERABLE_TOKEN_REFERENCES = Object.freeze([
  Object.freeze({ table: "dd_party_snapshot", token: "snapshot_vault_token" }),
  Object.freeze({ table: "dd_check", token: "result_vault_token" }),
  Object.freeze({ table: "screening_hit", token: "name_vault_token" }),
]);

// Risk authorizes Reveal only for the three owned recoverable values above. These
// Commerce-origin tokens are stable equality/frequency correlation inputs and
// must never be promoted into the Risk Reveal authorization registry.
export const COMMERCE_STABLE_CORRELATION_TOKEN_REFERENCES = Object.freeze([
  Object.freeze({ table: "checkout_input_snapshot", token: "consumer_email_vault_token" }),
  Object.freeze({ table: "checkout_input_snapshot", token: "consumer_phone_vault_token" }),
  Object.freeze({ table: "transaction_risk_assessment", token: "consumer_email_vault_token" }),
  Object.freeze({ table: "transaction_risk_assessment", token: "consumer_phone_vault_token" }),
]);

// Backward-compatible policy name: this registry deliberately contains only
// Risk-owned recoverable values and is the sole Reveal-authorized set.
export const VAULT_TOKEN_REFERENCES = RISK_OWNED_RECOVERABLE_TOKEN_REFERENCES;

const compensationFact = ({
  eventType,
  subEventTypes,
  eventKeyFormat,
  eventKeySource,
  authorityTables,
  envelopeSources,
  permanentIdempotency,
}) => deepFreeze({
  eventType,
  subEventTypes,
  eventKeyFormat,
  eventKeySource,
  authorityTables,
  envelopeSources,
  permanentIdempotency,
  payloadPolicy: "NULL_OR_MINIMAL_NON_SENSITIVE_JSON_OBJECT",
});

// Risk 补偿事件重建清单：成功任务删除后，业务历史/事实仍永久保存事件身份与
// 重建坐标；payload_json 不复制可从这些权威表恢复的上下文。
export const COMPENSATION_EVENT_RECONSTRUCTION_INVENTORY = Object.freeze([
  compensationFact({
    eventType: "PUBLISH_ROLE_QUALIFICATION_EVENT",
    subEventTypes: [
      "RoleQualificationApproved", "RoleQualificationRejected",
      "RoleQualificationExpired", "RoleQualificationRevoked",
    ],
    eventKeyFormat: "risk:qualification-change:{changeId}",
    eventKeySource: "qualification_state_change.change_id",
    authorityTables: ["role_qualification", "qualification_state_change", "qualification_decision"],
    envelopeSources: ["role_qualification", "qualification_state_change", "qualification_decision"],
    permanentIdempotency: "qualification_state_change.change_id",
  }),
  compensationFact({
    eventType: "PUBLISH_COMPLIANCE_RESTRICTION_EVENT",
    subEventTypes: ["ComplianceRestricted"],
    eventKeyFormat: "risk:restriction:{restrictionId}",
    eventKeySource: "risk_restriction.restriction_id",
    authorityTables: ["risk_restriction"],
    envelopeSources: ["risk_restriction", "compensation_event.payload_json:MINIMAL_EVENT_TIME_FIELDS_IF_REQUIRED"],
    permanentIdempotency: "risk_restriction.restriction_id",
  }),
  compensationFact({
    eventType: "PUBLISH_CHECKOUT_RISK_ASSESSMENT_EVENT",
    subEventTypes: ["CheckoutRiskAssessed"],
    eventKeyFormat: "risk:checkout-assessment:{snapshotId}",
    eventKeySource: "checkout_input_snapshot.snapshot_id",
    authorityTables: ["checkout_input_snapshot", "decision_evaluation"],
    envelopeSources: ["checkout_input_snapshot", "decision_evaluation"],
    permanentIdempotency: "checkout_input_snapshot.snapshot_id",
  }),
  compensationFact({
    eventType: "PUBLISH_PAYMENT_RISK_ASSESSMENT_EVENT",
    subEventTypes: ["PaymentRiskAssessed"],
    eventKeyFormat: "risk:payment-assessment:{assessmentId}",
    eventKeySource: "transaction_risk_assessment.assessment_id",
    authorityTables: ["transaction_risk_assessment", "decision_evaluation"],
    envelopeSources: ["transaction_risk_assessment", "decision_evaluation"],
    permanentIdempotency: "transaction_risk_assessment.assessment_id",
  }),
  compensationFact({
    eventType: "PUBLISH_SETTLEMENT_CONTROL_EVENT",
    subEventTypes: ["SettlementControlChanged"],
    eventKeyFormat: "risk:settlement-control-change:{changeId}",
    eventKeySource: "settlement_control_state_change.change_id",
    authorityTables: ["settlement_control", "settlement_control_scope", "settlement_control_state_change"],
    envelopeSources: ["settlement_control", "settlement_control_scope", "settlement_control_state_change"],
    permanentIdempotency: "settlement_control_state_change.change_id",
  }),
  compensationFact({
    eventType: "PUBLISH_DISPUTE_EVENT",
    subEventTypes: [
      "DisputeOpened", "DisputeEvidenceRequested", "DisputeWon",
      "DisputeLost", "DisputeOutcomeCorrected",
    ],
    eventKeyFormat: "risk:dispute-change:{changeId}",
    eventKeySource: "dispute_state_change.change_id",
    authorityTables: [
      "dispute_case", "dispute_state_change", "dispute_submission_request",
      "dispute_outcome", "dispute_liability_decision",
    ],
    envelopeSources: [
      "dispute_case", "dispute_state_change", "dispute_submission_request",
      "dispute_outcome", "dispute_liability_decision",
    ],
    permanentIdempotency: "dispute_state_change.change_id + sub_event_type",
  }),
  compensationFact({
    eventType: "EXECUTE_RISK_ACTION_EFFECT",
    subEventTypes: ["APPLY_ASYNC_ACTION_EFFECT"],
    eventKeyFormat: "{actionEffectIdempotencyKey}",
    eventKeySource: "action_effect.idempotency_key",
    authorityTables: ["action_effect", "decision_evaluation", "action_version"],
    envelopeSources: [],
    permanentIdempotency: "action_effect.uk_action_effect_key(idempotency_key)",
  }),
]);

function compositeFk(name, childTable, childFields, parentTable, parentFields) {
  return deepFreeze({ name, childTable, childFields, parentTable, parentFields });
}

// PDManer 只绘制既有单字段逻辑边；这些精确列组随生成实体持久化，
// 仅作为 Owner 写入口、真实本地事务、CAS/唯一索引和测试共同执行的应用不变量。
export const COMPOSITE_FK_CONTRACTS = Object.freeze([
  compositeFk(
    "fk_dispute_req_package_case",
    "t_dispute_submission_request",
    ["package_id", "dispute_case_id"],
    "t_dispute_evidence_package",
    ["package_id", "dispute_case_id"],
  ),
  compositeFk(
    "fk_dispute_case_current_out",
    "t_dispute_case",
    ["current_outcome_id", "dispute_case_id"],
    "t_dispute_outcome",
    ["outcome_id", "dispute_case_id"],
  ),
  compositeFk(
    "fk_dispute_out_prev_case",
    "t_dispute_outcome",
    ["supersedes_outcome_id", "dispute_case_id"],
    "t_dispute_outcome",
    ["outcome_id", "dispute_case_id"],
  ),
]);

// 连续版本、链尾和恰好一条 Liability 无法由单行物理约束完整表达。
// 该注册表是 Risk 聚合 Processor 与仓库自有 local-environment 测试的准确写入契约。
export const DISPUTE_AGGREGATE_CONTRACT = deepFreeze({
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

export const DISPUTE_EVIDENCE_PACKAGE_CANONICALIZATION = deepFreeze({
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

export const SETTLEMENT_ELIGIBILITY_ASSESSMENT_SOURCE_FACT_MATRIX = Object.freeze({
  PAYOUT_CREATION: Object.freeze(["PAYOUT"]),
  PAYOUT_FINAL_APPROVAL: Object.freeze(["PAYOUT"]),
  PAYOUT_EXECUTION_CREATION: Object.freeze(["PAYOUT_EXECUTION"]),
  PAYOUT_EXTERNAL_SUBMISSION: Object.freeze(["PAYOUT_EXECUTION"]),
});

export const SETTLEMENT_ELIGIBILITY_OBLIGATION_CONTRACT = deepFreeze({
  code: "CR_SETTLEMENT_ELIGIBILITY_OBLIGATION",
  contractType: "CARDINALITY_ASSERTIONS",
  root: {
    alias: "point", table: "decision_point", key: "decision_point_id",
    predicates: [
      { field: "business_domain", operator: "EQUALS", value: "SETTLEMENT" },
      {
        field: "point_code", operator: "IN",
        values: [
          "PAYOUT_CREATION", "PAYOUT_FINAL_APPROVAL",
          "PAYOUT_EXECUTION_CREATION", "PAYOUT_EXTERNAL_SUBMISSION",
        ],
      },
    ],
  },
  joins: [
    {
      from: { alias: "point", field: "decision_point_id" },
      to: { alias: "pointVersion", table: "decision_point_version", field: "decision_point_id" },
      required: true,
    },
    {
      from: { alias: "pointVersion", field: "point_version_id" },
      to: { alias: "obligation", table: "decision_point_obligation", field: "point_version_id" },
      required: false,
    },
    {
      from: { alias: "obligation", field: "obligation_id" },
      to: { alias: "limitBinding", table: "decision_point_limit_binding", field: "obligation_id" },
      required: false,
    },
    {
      from: { alias: "obligation", field: "obligation_id" },
      to: { alias: "intent", table: "evaluation_action_intent", field: "obligation_id" },
      required: false,
    },
  ],
  cardinalityAssertions: [
    {
      context: { alias: "pointVersion", key: "point_version_id" },
      child: { alias: "obligation", foreignKey: "point_version_id" },
      predicates: [
        { endpoint: { alias: "obligation", field: "obligation_type" }, operator: "EQUALS", value: "CHECK_SETTLEMENT_ELIGIBILITY" },
      ],
      childAssertions: [
        { endpoint: { alias: "obligation", field: "trigger_decision" }, operator: "EQUALS", value: "ALLOW" },
        { endpoint: { alias: "obligation", field: "action_version_id" }, operator: "IS_NULL" },
        { endpoint: { alias: "obligation", field: "failure_policy" }, operator: "EQUALS", value: "FAIL_CLOSED" },
        { endpoint: { alias: "obligation", field: "enabled_flag" }, operator: "EQUALS", value: true },
      ],
      exactly: 1,
    },
    {
      context: { alias: "obligation", key: "obligation_id" },
      child: { alias: "limitBinding", foreignKey: "obligation_id" },
      contextPredicates: [
        { endpoint: { alias: "obligation", field: "obligation_type" }, operator: "EQUALS", value: "CHECK_SETTLEMENT_ELIGIBILITY" },
      ],
      predicates: [], exactly: 0,
    },
    {
      context: { alias: "obligation", key: "obligation_id" },
      child: { alias: "intent", foreignKey: "obligation_id" },
      contextPredicates: [
        { endpoint: { alias: "obligation", field: "obligation_type" }, operator: "EQUALS", value: "CHECK_SETTLEMENT_ELIGIBILITY" },
      ],
      predicates: [], exactly: 0,
    },
  ],
});

export const SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS = deepFreeze({
  CR_SETTLEMENT_CONTROL_EFFECT_UNIQUE: {
    code: "CR_SETTLEMENT_CONTROL_EFFECT_UNIQUE",
    parent: { table: "action_effect", key: "effect_id" },
    child: { table: "settlement_control", foreignKey: "source_action_effect_id" },
    maximum: 1,
  },
  CR_SETTLEMENT_CONTROL_ORIGIN: {
    code: "CR_SETTLEMENT_CONTROL_ORIGIN",
    contractType: "JOIN_ASSERTIONS",
    root: { alias: "control", table: "settlement_control", key: "control_id", predicates: [] },
    joins: [
      {
        from: { alias: "control", field: "source_action_effect_id" },
        to: { alias: "effect", table: "action_effect", field: "effect_id" },
        required: true,
      },
      {
        from: { alias: "effect", field: "evaluation_id" },
        to: { alias: "evaluation", table: "decision_evaluation", field: "evaluation_id" },
        required: true,
      },
      {
        from: { alias: "effect", field: "effect_id" },
        to: { alias: "intent", table: "evaluation_action_intent", field: "merged_effect_id" },
        required: false,
      },
      {
        from: { alias: "intent", field: "rule_action_id" },
        to: { alias: "ruleAction", table: "rule_action", field: "rule_action_id" },
        required: true,
      },
    ],
    rowPredicates: [
      { endpoint: { alias: "evaluation", field: "evaluation_mode" }, operator: "EQUALS", value: "PRIMARY" },
      { endpoint: { alias: "evaluation", field: "technical_status" }, operator: "EQUALS", value: "SUCCEEDED" },
      { endpoint: { alias: "effect", field: "effect_mode" }, operator: "EQUALS", value: "REAL" },
      { endpoint: { alias: "effect", field: "effect_status" }, operator: "EQUALS", value: "APPLIED" },
      { endpoint: { alias: "effect", field: "execution_type" }, operator: "EQUALS", value: "LOCAL_SYNC" },
      { endpoint: { alias: "effect", field: "execution_status" }, operator: "EQUALS", value: "SUCCEEDED" },
      { endpoint: { alias: "effect", field: "target_type" }, operator: "EQUALS", value: "SETTLEMENT_CONTROL" },
      { endpoint: { alias: "intent", field: "source_type" }, operator: "EQUALS", value: "RULE" },
      { endpoint: { alias: "intent", field: "status" }, operator: "EQUALS", value: "MERGED" },
    ],
    cardinalityAssertions: [
      {
        context: { alias: "control", key: "control_id" },
        child: { alias: "effect", key: "effect_id" },
        join: {
          local: { alias: "control", field: "source_action_effect_id" },
          foreign: { alias: "effect", field: "effect_id" },
        },
        predicates: [], exactly: 1,
      },
      {
        context: { alias: "effect", key: "effect_id" },
        child: { alias: "intent", key: "intent_id" },
        join: {
          local: { alias: "effect", field: "effect_id" },
          foreign: { alias: "intent", field: "merged_effect_id" },
        },
        predicates: [], exactly: 1,
      },
    ],
    fieldAssertions: [
      { left: { alias: "effect", field: "evaluation_id" }, operator: "EQUALS", right: { alias: "intent", field: "evaluation_id" } },
      { left: { alias: "evaluation", field: "evaluation_id" }, operator: "EQUALS", right: { alias: "intent", field: "evaluation_id" } },
      { left: { alias: "effect", field: "action_version_id" }, operator: "EQUALS", right: { alias: "intent", field: "action_version_id" } },
      { left: { alias: "intent", field: "action_version_id" }, operator: "EQUALS", right: { alias: "ruleAction", field: "action_version_id" } },
      { left: { alias: "effect", field: "action_version_id" }, operator: "EQUALS", right: { alias: "ruleAction", field: "action_version_id" } },
      { left: { alias: "effect", field: "target_type" }, operator: "EQUALS", right: { alias: "intent", field: "resolved_target_type" } },
      { left: { alias: "effect", field: "target_key" }, operator: "EQUALS", right: { alias: "intent", field: "resolved_target_key" } },
    ],
    formattedFieldAssertions: [
      {
        endpoint: { alias: "effect", field: "target_key" }, operator: "EQUALS_FORMAT",
        format: "{roleType}:{roleId}:{controlInstanceKey}",
        components: [
          { placeholder: "roleType", endpoint: { alias: "control", field: "role_type" } },
          { placeholder: "roleId", endpoint: { alias: "control", field: "role_id" }, encoding: "CANONICAL_DECIMAL_STRING" },
          { placeholder: "controlInstanceKey", endpoint: { alias: "control", field: "control_instance_key" } },
        ],
      },
      {
        endpoint: { alias: "control", field: "control_instance_key" }, operator: "STARTS_WITH_COMPONENTS",
        components: [
          { literal: "RULE_ACTION:" },
          { endpoint: { alias: "intent", field: "rule_action_id" }, encoding: "CANONICAL_DECIMAL_STRING" },
          { literal: ":" },
        ],
      },
    ],
  },
  CR_SETTLEMENT_CONTROL_AGGREGATE: {
    code: "CR_SETTLEMENT_CONTROL_AGGREGATE",
    root: { table: "settlement_control", key: "control_id" },
    scopes: { table: "settlement_control_scope", foreignKey: "control_id", minimum: 1 },
    initialStateChange: {
      table: "settlement_control_state_change", foreignKey: "control_id", exactly: 1,
      predicates: { aggregate_version: 1, from_status: null, to_status: "ACTIVE", trigger_type: "ACTION_EFFECT" },
      triggerIdEquals: "settlement_control.source_action_effect_id",
      triggerIdEncoding: "CANONICAL_DECIMAL_STRING",
    },
    history: {
      orderBy: "aggregate_version", firstVersion: 1, contiguous: true,
      fromStatusEqualsPreviousToStatus: true,
    },
    rootProjection: {
      statusEquals: "last_state_change.to_status",
      versionEquals: "last_state_change.aggregate_version",
      activeSlotByStatus: { ACTIVE: 1, RELEASED: null, EXPIRED: null },
    },
  },
  CR_SETTLEMENT_CONTROL_EVENT: {
    code: "CR_SETTLEMENT_CONTROL_EVENT",
    contractType: "VERSIONED_CHILD_CARDINALITY",
    root: { alias: "control", table: "settlement_control", key: "control_id", version: "version", predicates: [] },
    joins: [
      {
        from: { alias: "control", field: "control_id" },
        to: { alias: "stateChange", table: "settlement_control_state_change", field: "control_id" },
        required: true,
      },
    ],
    versionHistory: {
      alias: "stateChange", table: "settlement_control_state_change",
      ownerField: "control_id", versionField: "aggregate_version",
      firstVersion: 1, lastVersion: { alias: "control", field: "version" }, contiguous: true,
    },
    eventIdentity: {
      table: "compensation_event",
      eventType: "PUBLISH_SETTLEMENT_CONTROL_EVENT",
      subEventType: "SettlementControlChanged",
      eventKeyFormat: "risk:settlement-control-change:{changeId}",
      eventKeyComponent: { alias: "stateChange", field: "change_id", encoding: "CANONICAL_DECIMAL_STRING" },
    },
    cardinalityAssertions: [{
      context: { alias: "stateChange", key: "change_id" },
      child: { alias: "event", table: "compensation_event", identityFields: ["event_key", "event_type", "sub_event_type"] },
      formattedIdentity: "risk:settlement-control-change:{changeId}",
      predicates: [
        { endpoint: { alias: "event", field: "event_type" }, operator: "EQUALS", value: "PUBLISH_SETTLEMENT_CONTROL_EVENT" },
        { endpoint: { alias: "event", field: "sub_event_type" }, operator: "EQUALS", value: "SettlementControlChanged" },
      ],
      exactly: 1,
    }],
  },
  CR_SETTLEMENT_CONTROL_CURRENCY: {
    code: "CR_SETTLEMENT_CONTROL_CURRENCY",
    when: { table: "settlement_control", field: "control_type", equals: "MIN_SECURITY_DEPOSIT" },
    children: { table: "settlement_control_scope", foreignKey: "control_id", minimum: 1 },
    assertions: [
      { childField: "currency", operator: "NOT_NULL" },
      { childField: "currency", operator: "EQUALS_ROOT", rootField: "required_currency" },
    ],
  },
  CR_SETTLEMENT_CONTROL_SIMULATION_FORBIDDEN: {
    code: "CR_SETTLEMENT_CONTROL_SIMULATION_FORBIDDEN",
    trigger: { table: "decision_evaluation", field: "evaluation_mode", values: ["SHADOW", "REPLAY"] },
    forbiddenPath: [
      ["decision_evaluation", "evaluation_id", "action_effect", "evaluation_id"],
      ["action_effect", "effect_id", "settlement_control", "source_action_effect_id"],
    ],
  },
  CR_SETTLEMENT_ASSESSMENT_DOMAIN: {
    code: "CR_SETTLEMENT_ASSESSMENT_DOMAIN",
    contractType: "CONDITIONAL_CARDINALITY",
    root: { alias: "evaluation", table: "decision_evaluation", key: "evaluation_id" },
    joins: [
      {
        from: { alias: "evaluation", field: "decision_point_id" },
        to: { alias: "decisionPoint", table: "decision_point", field: "decision_point_id" },
        required: true,
      },
      {
        from: { alias: "evaluation", field: "evaluation_id" },
        to: { alias: "assessment", table: "settlement_risk_assessment", field: "evaluation_id" },
        required: false,
      },
    ],
    discriminator: { alias: "decisionPoint", field: "business_domain" },
    children: [{
      key: "settlementAssessment", alias: "assessment",
      table: "settlement_risk_assessment", foreignKey: "evaluation_id",
    }],
    cases: [
      { values: ["SETTLEMENT"], exactly: { settlementAssessment: 1 } },
      {
        values: ["DUE_DILIGENCE", "CHECKOUT", "PAYMENT", "DISPUTE"],
        exactly: { settlementAssessment: 0 },
      },
    ],
    fieldAssertions: [
      {
        left: { alias: "assessment", field: "checkpoint_type" },
        operator: "EQUALS",
        right: { alias: "decisionPoint", field: "point_code" },
      },
      {
        left: { alias: "assessment", field: "input_hash" },
        operator: "EQUALS",
        right: { alias: "evaluation", field: "input_snapshot_hash" },
      },
    ],
    resultSelectors: [
      { mappingKey: "eligibilityDecision", alias: "assessment", field: "eligibility_decision" },
      { mappingKey: "technicalStatus", alias: "evaluation", field: "technical_status" },
      { mappingKey: "proposedDecision", alias: "evaluation", field: "proposed_decision" },
      { mappingKey: "businessDecision", alias: "evaluation", field: "business_decision" },
    ],
    allowedResultMappings: [
      { eligibilityDecision: "ALLOW", technicalStatus: "SUCCEEDED", proposedDecision: "ALLOW", businessDecision: "ALLOW" },
      { eligibilityDecision: "RESTRICT", technicalStatus: "SUCCEEDED", proposedDecision: "ALLOW", businessDecision: "DENY" },
      { eligibilityDecision: null, technicalStatus: "SUCCEEDED", proposedDecision: "REVIEW", businessDecision: "REVIEW" },
      { eligibilityDecision: null, technicalStatus: "SUCCEEDED", proposedDecision: "DENY", businessDecision: "DENY" },
      { eligibilityDecision: null, technicalStatus: "FAILED", proposedDecision: null, businessDecision: null },
      { eligibilityDecision: null, technicalStatus: "TIMED_OUT", proposedDecision: null, businessDecision: null },
    ],
  },
});

// PDManer 4.9.3 does not expose one portable CHECK node for every target database.
// These expressions are the exact later Migration/Testcontainers contract.
export const CHECK_CONTRACTS = Object.freeze({
  ck_positive_version: "version>0",
  ck_program_active_slot: "(status='ACTIVE' AND active_slot IS NOT NULL AND active_slot=1) OR (status IN ('DRAFT','RETIRED') AND active_slot IS NULL)",
  ck_program_approval: "(status='DRAFT' AND approved_by IS NULL AND approved_time IS NULL) OR (status IN ('ACTIVE','RETIRED') AND approved_by IS NOT NULL AND approved_time IS NOT NULL)",
  ck_program_version_no: "version_no>0",
  ck_program_check_validity: "validity_days IS NULL OR validity_days>0",
  ck_dd_case_active_slot: "(status IN ('DRAFT','SUBMITTED','IN_REVIEW','WAITING_EVIDENCE') AND active_slot IS NOT NULL AND active_slot=1) OR (status IN ('RETURNED','APPROVED','REJECTED','WITHDRAWN','EXPIRED') AND active_slot IS NULL)",
  ck_dd_case_times: "(status='DRAFT' AND submitted_time IS NULL AND decision_time IS NULL) OR (status IN ('SUBMITTED','IN_REVIEW','WAITING_EVIDENCE') AND submitted_time IS NOT NULL AND decision_time IS NULL) OR (status IN ('RETURNED','APPROVED','REJECTED') AND submitted_time IS NOT NULL AND decision_time IS NOT NULL AND submitted_time<=decision_time) OR (status IN ('WITHDRAWN','EXPIRED') AND decision_time IS NULL)",
  ck_dd_case_tuple: "(role_type='CLIENT' AND qualification_type IN ('INDIVIDUAL_KYC','ORGANIZATION_KYB')) OR (role_type='LOCAL_PARTNER' AND qualification_type='PARTNER_DUE_DILIGENCE') OR (role_type='OPERATING_ENTITY' AND qualification_type='ENTITY_UNDERWRITING') OR (role_type='RELATED_PERSON' AND qualification_type='RELATED_PERSON_KYC')",
  ck_dd_case_origin: "(case_origin='APPLICATION' AND ((source_namespace='CCT_CLIENT_APPLICATION' AND role_type IN ('CLIENT','RELATED_PERSON')) OR (source_namespace='CCT_OPERATING_ENTITY_APPLICATION' AND role_type IN ('OPERATING_ENTITY','RELATED_PERSON')) OR (source_namespace='CCT_LOCAL_PARTNER_APPLICATION' AND role_type IN ('LOCAL_PARTNER','RELATED_PERSON'))) AND source_application_id IS NOT NULL AND source_submission_id IS NOT NULL AND ((source_namespace='CCT_CLIENT_APPLICATION' AND source_submission_version IS NULL AND source_submission_hash IS NULL) OR (source_namespace<>'CCT_CLIENT_APPLICATION' AND source_submission_version IS NOT NULL AND source_submission_version>0 AND source_submission_hash IS NOT NULL)) AND scheduled_review_time IS NULL) OR (case_origin='CONTINUOUS_REVIEW' AND source_namespace='RISK_COMPLIANCE' AND source_application_id IS NULL AND source_submission_id IS NULL AND source_submission_version IS NULL AND source_submission_hash IS NULL AND scheduled_review_time IS NOT NULL) OR (case_origin='MANUAL' AND source_namespace='RISK_COMPLIANCE' AND source_application_id IS NULL AND source_submission_id IS NULL AND source_submission_version IS NULL AND source_submission_hash IS NULL AND scheduled_review_time IS NULL)",
  ck_dd_dependency_self: "parent_case_id<>child_case_id",
  ck_dd_subject_relation: "(subject_role='PRIMARY' AND primary_slot IS NOT NULL AND primary_slot=1 AND source_relation_id IS NULL AND source_relation_version_id IS NULL AND source_relation_version_no IS NULL AND ownership_percentage IS NULL) OR (subject_role='UBO' AND primary_slot IS NULL AND ((source_relation_id IS NOT NULL AND source_relation_version_id IS NULL AND source_relation_version_no IS NULL) OR (source_relation_id IS NULL AND source_relation_version_id IS NOT NULL AND source_relation_version_no IS NOT NULL AND source_relation_version_no>0))) OR (subject_role IN ('DIRECTOR','SIGNATORY') AND primary_slot IS NULL AND ((source_relation_id IS NOT NULL AND source_relation_version_id IS NULL AND source_relation_version_no IS NULL) OR (source_relation_id IS NULL AND source_relation_version_id IS NOT NULL AND source_relation_version_no IS NOT NULL AND source_relation_version_no>0)) AND ownership_percentage IS NULL)",
  ck_dd_subject_ownership: "ownership_percentage IS NULL OR (ownership_percentage>0 AND ownership_percentage<=100)",
  ck_dd_subject_profile_version: "source_profile_version_no IS NULL OR source_profile_version_no>0",
  ck_snapshot_schema_version: "schema_version>0",
  ck_dd_file_pair: "file_id IS NOT NULL AND content_hash IS NOT NULL",
  ck_dd_check_validity: "valid_until IS NULL OR performed_time<valid_until",
  ck_screen_run_times: "(status='STARTED' AND completed_time IS NULL) OR (status IN ('SUCCEEDED','FAILED','TIMED_OUT') AND completed_time IS NOT NULL AND started_time<=completed_time)",
  ck_screen_score: "match_score>=0 AND match_score<=1",
  ck_screen_resolution_file: "(evidence_file_id IS NULL AND evidence_content_hash IS NULL) OR (evidence_file_id IS NOT NULL AND evidence_content_hash IS NOT NULL)",
  ck_screen_resolution_version: "resolution_version>0",
  ck_qualification_range: "valid_to IS NULL OR valid_from<valid_to",
  ck_qual_decision_version: "decision_version IS NULL OR decision_version>0",
  ck_aggregate_version: "aggregate_version>0",
  ck_state_change_initial: "(aggregate_version=1 AND from_status IS NULL) OR (aggregate_version>1 AND from_status IS NOT NULL)",
  ck_qual_decision_source: "(decision_source='AUTOMATED' AND decision_evaluation_id IS NOT NULL AND ((recommended_decision='APPROVE' AND outcome='APPROVED') OR (recommended_decision='REJECT' AND outcome='REJECTED')) AND override_reason_code IS NULL) OR (decision_source='REVIEWER' AND decided_by IS NOT NULL AND override_reason_code IS NULL AND (recommended_decision IS NULL OR recommended_decision='REVIEW' OR (recommended_decision='APPROVE' AND outcome='APPROVED') OR (recommended_decision='REJECT' AND outcome='REJECTED'))) OR (decision_source='OVERRIDE' AND decision_evaluation_id IS NOT NULL AND recommended_decision IS NOT NULL AND override_reason_code IS NOT NULL AND decided_by IS NOT NULL)",
  ck_role_qual_review: "(review_status='NOT_REQUIRED' AND status='APPROVED' AND review_frequency_code IS NULL AND next_review_time IS NULL) OR (review_status IN ('ACTIVE','PAUSED') AND status='APPROVED' AND review_frequency_code IS NOT NULL AND next_review_time IS NOT NULL) OR (review_status='COMPLETED' AND status IN ('REJECTED','REVOKED','EXPIRED') AND next_review_time IS NULL)",
  ck_rule_node_shape: "(node_type='GROUP' AND group_operator IS NOT NULL AND comparison_operator IS NULL) OR (node_type='PREDICATE' AND group_operator IS NULL AND comparison_operator IS NOT NULL)",
  ck_rule_node_self: "parent_node_id IS NULL OR parent_node_id<>node_id",
  ck_rule_operand_source: "(source_type='FEATURE' AND value_sequence=1 AND feature_version_id IS NOT NULL AND field_code IS NULL AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (source_type='CONTEXT' AND value_sequence=1 AND feature_version_id IS NULL AND field_code IS NOT NULL AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (source_type='LITERAL' AND feature_version_id IS NULL AND field_code IS NULL)",
  ck_rule_operand_value_type: "(source_type IN ('FEATURE','CONTEXT') AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (source_type='LITERAL' AND ((value_type='STRING' AND string_value IS NOT NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (value_type='NUMBER' AND string_value IS NULL AND number_value IS NOT NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (value_type='BOOLEAN' AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NOT NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (value_type='DATETIME' AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NOT NULL AND duration_value IS NULL) OR (value_type='DURATION' AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NOT NULL)))",
  ck_rule_operand_sequence: "value_sequence>0",
  ck_action_terminal: "((terminal_flag=1 AND decision_code IS NOT NULL) OR (terminal_flag=0 AND decision_code IS NULL)) AND ((status IN ('CONFIGURATION_ONLY','RETIRED') AND terminal_flag=0 AND shadow_supported_flag=0 AND handler_code IS NULL AND implementation_version IS NULL AND implementation_hash IS NULL) OR (status IN ('REGISTERED','ACTIVE','RETIRED') AND handler_code IS NOT NULL AND implementation_version IS NOT NULL AND implementation_hash IS NOT NULL))",
  ck_action_parameter_source: "(source_type='FEATURE' AND feature_version_id IS NOT NULL AND field_code IS NULL AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (source_type='CONTEXT' AND feature_version_id IS NULL AND field_code IS NOT NULL AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (source_type='LITERAL' AND feature_version_id IS NULL AND field_code IS NULL AND ((string_value IS NOT NULL)+(number_value IS NOT NULL)+(boolean_value IS NOT NULL)+(datetime_value IS NOT NULL)+(duration_value IS NOT NULL)=1))",
  ck_action_parameter_sequence: "value_sequence>0",
  ck_obligation_target: "(obligation_type='ACTION' AND action_version_id IS NOT NULL) OR (obligation_type IN ('RESERVE_LIMIT_GROUP','CHECK_SETTLEMENT_ELIGIBILITY') AND action_version_id IS NULL)",
  ck_release_active_slot: "active_slot IS NULL OR active_slot=1",
  ck_release_times: "(activated_time IS NULL OR superseded_time IS NULL OR activated_time<superseded_time)",
  ck_release_audit_event: "(event_type='STATIC_CHECK' AND check_code IS NOT NULL AND check_status IS NOT NULL AND severity IS NOT NULL AND shadow_run_id IS NULL AND approval_request_id IS NULL AND approval_status IS NULL AND reviewer_id IS NULL AND review_comment IS NULL AND from_status IS NULL AND to_status IS NULL AND reason_code IS NULL AND trigger_type IS NULL AND trigger_id IS NULL AND actor_id IS NULL AND aggregate_version IS NULL) OR (event_type='APPROVAL' AND check_code IS NULL AND check_status IS NULL AND severity IS NULL AND error_code IS NULL AND error_summary IS NULL AND shadow_run_id IS NOT NULL AND approval_request_id IS NOT NULL AND approval_status IS NOT NULL AND reviewer_id IS NOT NULL AND from_status IS NULL AND to_status IS NULL AND reason_code IS NULL AND trigger_type IS NULL AND trigger_id IS NULL AND actor_id IS NULL AND aggregate_version IS NULL) OR (event_type='STATE_CHANGE' AND check_code IS NULL AND check_status IS NULL AND severity IS NULL AND error_code IS NULL AND error_summary IS NULL AND shadow_run_id IS NULL AND approval_request_id IS NULL AND approval_status IS NULL AND reviewer_id IS NULL AND review_comment IS NULL AND to_status IS NOT NULL AND trigger_type IS NOT NULL AND trigger_id IS NOT NULL AND aggregate_version IS NOT NULL AND ((aggregate_version=1 AND from_status IS NULL) OR (aggregate_version>1 AND from_status IS NOT NULL)))",
  ck_shadow_release_pair: "baseline_release_id<>candidate_release_id",
  ck_shadow_counts: "sample_target>=0 AND sample_count>=0 AND sample_count<=sample_target",
  ck_shadow_summary_counts: "allow_to_review_count>=0 AND allow_to_deny_count>=0 AND deny_to_allow_count>=0 AND action_change_count>=0 AND feature_missing_count>=0 AND failed_count>=0 AND timed_out_count>=0 AND p95_millis>=0 AND p99_millis>=p95_millis",
  ck_shadow_run_completion: "(status='COMPLETED' AND completed_time IS NOT NULL AND allow_to_review_count IS NOT NULL AND allow_to_deny_count IS NOT NULL AND deny_to_allow_count IS NOT NULL AND action_change_count IS NOT NULL AND feature_missing_count IS NOT NULL AND failed_count IS NOT NULL AND timed_out_count IS NOT NULL AND p95_millis IS NOT NULL AND p99_millis IS NOT NULL AND check_status IS NOT NULL) OR (status IN ('CREATED','RUNNING') AND completed_time IS NULL AND allow_to_review_count IS NULL AND allow_to_deny_count IS NULL AND deny_to_allow_count IS NULL AND action_change_count IS NULL AND feature_missing_count IS NULL AND failed_count IS NULL AND timed_out_count IS NULL AND p95_millis IS NULL AND p99_millis IS NULL AND check_status IS NULL) OR (status IN ('FAILED','CANCELLED') AND completed_time IS NOT NULL AND allow_to_review_count IS NULL AND allow_to_deny_count IS NULL AND deny_to_allow_count IS NULL AND action_change_count IS NULL AND feature_missing_count IS NULL AND failed_count IS NULL AND timed_out_count IS NULL AND p95_millis IS NULL AND p99_millis IS NULL AND check_status IS NULL)",
  ck_evaluation_state: "(technical_status='SUCCEEDED' AND proposed_decision IS NOT NULL AND business_decision IS NOT NULL AND failure_code IS NULL AND ((proposed_decision=business_decision AND decision_adjustment_reason_code IS NULL) OR (proposed_decision<>business_decision AND decision_adjustment_reason_code IS NOT NULL))) OR (technical_status IN ('FAILED','TIMED_OUT') AND proposed_decision IS NULL AND business_decision IS NULL AND decision_adjustment_reason_code IS NULL AND default_decision_used_flag=0 AND failure_code IS NOT NULL)",
  ck_evaluation_times: "started_time<=completed_time AND elapsed_millis>=0",
  ck_evaluation_shadow_comparison: "(evaluation_mode='SHADOW' AND shadow_run_id IS NOT NULL AND baseline_evaluation_id IS NOT NULL AND decision_changed_flag IS NOT NULL AND action_changed_flag IS NOT NULL AND comparison_severity IS NOT NULL AND compared_time IS NOT NULL) OR (evaluation_mode IN ('PRIMARY','REPLAY') AND shadow_run_id IS NULL AND baseline_evaluation_id IS NULL AND decision_changed_flag IS NULL AND action_changed_flag IS NULL AND comparison_severity IS NULL AND compared_time IS NULL)",
  ck_feature_result_value: "(technical_status='SUCCEEDED' AND ((string_value IS NOT NULL)+(number_value IS NOT NULL)+(boolean_value IS NOT NULL)+(datetime_value IS NOT NULL)+(duration_value IS NOT NULL)=1) AND failure_code IS NULL) OR (technical_status IN ('FAILED','TIMED_OUT') AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL AND failure_code IS NOT NULL)",
  ck_evaluation_rule_trace: "(trace_type='RULE' AND rule_set_version_id IS NOT NULL AND rule_id IS NOT NULL AND node_id IS NULL AND result_value IS NOT NULL AND terminal_flag IS NOT NULL AND elapsed_millis IS NOT NULL AND failure_code IS NULL AND rule_result_slot=1 AND predicate_result_slot IS NULL) OR (trace_type='PREDICATE' AND rule_set_version_id IS NULL AND rule_id IS NOT NULL AND node_id IS NOT NULL AND result_value IS NOT NULL AND terminal_flag IS NULL AND elapsed_millis IS NULL AND failure_code IS NULL AND rule_result_slot IS NULL AND predicate_result_slot=1) OR (trace_type='PREDICATE' AND rule_set_version_id IS NULL AND rule_id IS NOT NULL AND node_id IS NOT NULL AND result_value IS NULL AND terminal_flag IS NULL AND elapsed_millis IS NULL AND failure_code IS NOT NULL AND rule_result_slot IS NULL AND predicate_result_slot=1)",
  ck_evaluation_action_intent_source: "(source_type='RULE' AND rule_action_id IS NOT NULL AND obligation_id IS NULL) OR (source_type='OBLIGATION' AND rule_action_id IS NULL AND obligation_id IS NOT NULL)",
  ck_evaluation_action_intent_effect: "(status='MERGED' AND merged_effect_id IS NOT NULL) OR (status IN ('COLLECTED','DISCARDED','FAILED') AND merged_effect_id IS NULL)",
  ck_action_effect_execution: "(effect_mode='SIMULATED' AND effect_status='SIMULATED' AND idempotency_key IS NULL AND execution_type IS NULL AND execution_status IS NULL AND attempt_count IS NULL AND started_time IS NULL AND completed_time IS NULL AND last_error_code IS NULL AND last_error_summary IS NULL) OR (effect_mode='REAL' AND idempotency_key=CONCAT('risk-action-effect:v1:',effect_hash) AND execution_type IS NOT NULL AND execution_status='INIT' AND effect_status='CREATED' AND attempt_count=0 AND started_time IS NULL AND completed_time IS NULL AND last_error_code IS NULL AND last_error_summary IS NULL) OR (effect_mode='REAL' AND idempotency_key=CONCAT('risk-action-effect:v1:',effect_hash) AND execution_type IS NOT NULL AND execution_status='PROCESSING' AND effect_status='PENDING_ASYNC' AND attempt_count>=1 AND started_time IS NOT NULL AND completed_time IS NULL AND last_error_code IS NULL AND last_error_summary IS NULL) OR (effect_mode='REAL' AND idempotency_key=CONCAT('risk-action-effect:v1:',effect_hash) AND execution_type IS NOT NULL AND execution_status='ERROR' AND effect_status='PENDING_ASYNC' AND attempt_count>=1 AND started_time IS NOT NULL AND completed_time IS NULL AND last_error_code IS NOT NULL AND last_error_summary IS NOT NULL) OR (effect_mode='REAL' AND idempotency_key=CONCAT('risk-action-effect:v1:',effect_hash) AND execution_type IS NOT NULL AND execution_status='SUCCEEDED' AND effect_status='APPLIED' AND attempt_count>=1 AND started_time IS NOT NULL AND completed_time IS NOT NULL AND started_time<=completed_time AND last_error_code IS NULL AND last_error_summary IS NULL) OR (effect_mode='REAL' AND idempotency_key=CONCAT('risk-action-effect:v1:',effect_hash) AND execution_type IS NOT NULL AND execution_status='FAILED' AND effect_status='FAILED' AND attempt_count>=1 AND started_time IS NOT NULL AND completed_time IS NOT NULL AND started_time<=completed_time AND last_error_code IS NOT NULL AND last_error_summary IS NOT NULL)",
  ck_restriction_range: "effective_to IS NULL OR effective_from<effective_to",
  ck_list_entry_active_slot: "active_slot IS NULL OR active_slot=1",
  ck_velocity_money: "(amount IS NULL AND currency IS NULL) OR (amount IS NOT NULL AND amount>=0 AND currency IS NOT NULL)",
  ck_limit_threshold: "version_no>0 AND window_size>0 AND ((metric_type='AMOUNT' AND amount_limit IS NOT NULL AND amount_limit>0 AND count_limit IS NULL) OR (metric_type='COUNT' AND amount_limit IS NULL AND count_limit IS NOT NULL AND count_limit>0) OR (metric_type='AMOUNT_AND_COUNT' AND amount_limit IS NOT NULL AND amount_limit>0 AND count_limit IS NOT NULL AND count_limit>0))",
  ck_limit_currency_mode: "((currency_mode='FIXED' AND fixed_currency IS NOT NULL) OR (currency_mode IN ('PER_CURRENCY','NO_CURRENCY') AND fixed_currency IS NULL)) AND NOT (metric_type IN ('AMOUNT','AMOUNT_AND_COUNT') AND currency_mode='NO_CURRENCY')",
  ck_limit_version_freeze: "(status='DRAFT' AND frozen_by IS NULL AND frozen_time IS NULL) OR (status IN ('FROZEN','RETIRED') AND frozen_by IS NOT NULL AND frozen_time IS NOT NULL)",
  ck_limit_binding_subject: "(subject_source_type='FEATURE' AND subject_feature_version_id IS NOT NULL AND subject_input_field_id IS NULL) OR (subject_source_type='CONTEXT' AND subject_feature_version_id IS NULL AND subject_input_field_id IS NOT NULL)",
  ck_limit_binding_amount: "(amount_source_type IS NULL AND amount_feature_version_id IS NULL AND amount_input_field_id IS NULL) OR (amount_source_type='FEATURE' AND amount_feature_version_id IS NOT NULL AND amount_input_field_id IS NULL) OR (amount_source_type='CONTEXT' AND amount_feature_version_id IS NULL AND amount_input_field_id IS NOT NULL)",
  ck_limit_position: "window_start<window_end AND reserved_amount>=0 AND committed_amount>=0 AND reserved_count>=0 AND committed_count>=0 AND version>0",
  ck_limit_group: "version>0 AND ((status='RESERVED' AND hold_reason IS NULL AND resolved_time IS NULL) OR (status='PAYMENT_UNKNOWN' AND hold_reason IS NOT NULL AND resolved_time IS NULL) OR (status IN ('COMMITTED','RELEASED') AND hold_reason IS NULL AND resolved_time IS NOT NULL))",
  ck_limit_reservation: "(amount IS NULL OR amount>0) AND count_value>=0 AND (amount IS NOT NULL OR count_value>0)",
  ck_limit_group_change: "aggregate_version>0 AND ((aggregate_version=1 AND from_status IS NULL AND to_status='RESERVED') OR (aggregate_version>1 AND ((from_status='RESERVED' AND to_status IN ('PAYMENT_UNKNOWN','COMMITTED','RELEASED')) OR (from_status='PAYMENT_UNKNOWN' AND to_status IN ('COMMITTED','RELEASED')))))",
  ck_limit_movement: "position_version>0 AND amount_delta>=0 AND count_delta>=0 AND (amount_delta>0 OR count_delta>0)",
  ck_settle_control_type: "(control_type='SETTLEMENT_BLOCK' AND role_type IN ('CLIENT','LOCAL_PARTNER') AND required_amount IS NULL AND required_currency IS NULL AND release_condition_code IS NULL) OR (control_type='MIN_SECURITY_DEPOSIT' AND role_type='CLIENT' AND required_amount IS NOT NULL AND required_amount>0 AND required_currency IS NOT NULL AND release_condition_code IS NOT NULL)",
  ck_settle_control_status_slot: "status IS NOT NULL AND ((status='ACTIVE' AND active_slot IS NOT NULL AND active_slot=1) OR (status IN ('RELEASED','EXPIRED') AND active_slot IS NULL))",
  ck_settle_control_range: "effective_to IS NULL OR effective_from<effective_to",
  ck_settle_control_instance_key: "control_instance_key REGEXP '^RULE_ACTION:[1-9][0-9]*:[A-Z0-9][A-Z0-9._:-]{0,95}$'",
  ck_settle_control_state: "aggregate_version IS NOT NULL AND to_status IS NOT NULL AND ((aggregate_version=1 AND from_status IS NULL AND to_status='ACTIVE') OR (aggregate_version>1 AND from_status IS NOT NULL AND from_status='ACTIVE' AND to_status IN ('RELEASED','EXPIRED')))",
  ck_settle_control_trigger: "trigger_type IS NOT NULL AND aggregate_version IS NOT NULL AND to_status IS NOT NULL AND reason_code IS NOT NULL AND trigger_id IS NOT NULL AND ((trigger_type='ACTION_EFFECT' AND aggregate_version=1 AND from_status IS NULL AND to_status='ACTIVE' AND actor_id IS NULL AND trigger_id REGEXP '^[1-9][0-9]*$') OR (trigger_type='OPERATION_COMMAND' AND aggregate_version>1 AND from_status IS NOT NULL AND from_status='ACTIVE' AND to_status='RELEASED' AND actor_id IS NOT NULL AND trigger_id REGEXP '^risk-command:[1-9][0-9]*$') OR (trigger_type='SYSTEM_TIMER' AND aggregate_version>1 AND from_status IS NOT NULL AND from_status='ACTIVE' AND to_status='EXPIRED' AND actor_id IS NULL AND reason_code='EFFECTIVE_WINDOW_ENDED' AND trigger_id REGEXP '^settlement-control-expiry:v1:[1-9][0-9]*:[1-9][0-9]*:[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z$'))",
  ck_settle_assessment_input: "gross_amount>0 AND ((checkpoint_type IN ('PAYOUT_CREATION','PAYOUT_EXECUTION_CREATION') AND source_fact_version=1) OR (checkpoint_type IN ('PAYOUT_FINAL_APPROVAL','PAYOUT_EXTERNAL_SUBMISSION') AND source_fact_version>0)) AND ((checkpoint_type IN ('PAYOUT_CREATION','PAYOUT_FINAL_APPROVAL') AND source_fact_type='PAYOUT') OR (checkpoint_type IN ('PAYOUT_EXECUTION_CREATION','PAYOUT_EXTERNAL_SUBMISSION') AND source_fact_type='PAYOUT_EXECUTION'))",
  ck_settle_assessment_result: "(eligibility_decision IS NULL AND required_amount IS NULL AND required_currency IS NULL AND control_set_hash IS NULL) OR (eligibility_decision IS NOT NULL AND eligibility_decision IN ('ALLOW','RESTRICT') AND control_set_hash IS NOT NULL AND role_type IS NOT NULL AND ((role_type='CLIENT' AND required_amount IS NOT NULL AND required_amount>=0 AND required_currency IS NOT NULL AND currency IS NOT NULL AND required_currency=currency) OR (role_type='LOCAL_PARTNER' AND required_amount IS NULL AND required_currency IS NULL)))",
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
  ck_compensation_lease: "(execute_status='PROCESSING' AND lock_owner IS NOT NULL AND lock_until IS NOT NULL) OR (execute_status IN ('INIT','ERROR','FAILED') AND lock_owner IS NULL AND lock_until IS NULL)",
});

export const CHECK_BINDINGS = Object.freeze({
  ck_positive_version: Object.freeze(["due_diligence_case", "dd_case_dependency", "dd_requirement", "dd_evidence_ref", "screening_run", "screening_hit", "role_qualification", "decision_release", "release_shadow_run", "risk_restriction", "risk_list", "risk_list_entry", "payment_attempt_fact", "limit_position", "limit_reservation_group", "settlement_control", "dispute_case", "dispute_submission_request"]),
  ck_program_active_slot: Object.freeze(["dd_program_version"]),
  ck_program_approval: Object.freeze(["dd_program_version"]),
  ck_program_version_no: Object.freeze(["dd_program_version"]),
  ck_program_check_validity: Object.freeze(["dd_program_check"]),
  ck_dd_case_active_slot: Object.freeze(["due_diligence_case"]),
  ck_dd_case_times: Object.freeze(["due_diligence_case"]),
  ck_dd_case_tuple: Object.freeze(["due_diligence_case"]),
  ck_dd_case_origin: Object.freeze(["due_diligence_case"]),
  ck_dd_dependency_self: Object.freeze(["dd_case_dependency"]),
  ck_dd_subject_relation: Object.freeze(["dd_case_subject"]),
  ck_dd_subject_ownership: Object.freeze(["dd_case_subject"]),
  ck_dd_subject_profile_version: Object.freeze(["dd_case_subject"]),
  ck_snapshot_schema_version: Object.freeze(["dd_party_snapshot"]),
  ck_dd_file_pair: Object.freeze(["dd_evidence_ref", "dispute_evidence_ref"]),
  ck_dd_check_validity: Object.freeze(["dd_check"]),
  ck_screen_run_times: Object.freeze(["screening_run"]),
  ck_screen_score: Object.freeze(["screening_hit"]),
  ck_screen_resolution_file: Object.freeze(["screening_resolution"]),
  ck_screen_resolution_version: Object.freeze(["screening_resolution"]),
  ck_qualification_range: Object.freeze(["qualification_decision", "role_qualification"]),
  ck_qual_decision_version: Object.freeze(["qualification_decision"]),
  ck_aggregate_version: Object.freeze(["dd_case_state_change", "qualification_state_change"]),
  ck_state_change_initial: Object.freeze(["dd_case_state_change", "qualification_state_change"]),
  ck_qual_decision_source: Object.freeze(["qualification_decision"]),
  ck_role_qual_review: Object.freeze(["role_qualification"]),
  ck_rule_node_shape: Object.freeze(["rule_node"]),
  ck_rule_node_self: Object.freeze(["rule_node"]),
  ck_rule_operand_source: Object.freeze(["rule_operand"]),
  ck_rule_operand_value_type: Object.freeze(["rule_operand"]),
  ck_rule_operand_sequence: Object.freeze(["rule_operand"]),
  ck_action_terminal: Object.freeze(["action_version"]),
  ck_action_parameter_source: Object.freeze(["rule_action_parameter"]),
  ck_action_parameter_sequence: Object.freeze(["rule_action_parameter"]),
  ck_obligation_target: Object.freeze(["decision_point_obligation"]),
  ck_release_active_slot: Object.freeze(["decision_release"]),
  ck_release_times: Object.freeze(["decision_release"]),
  ck_release_audit_event: Object.freeze(["release_audit_event"]),
  ck_shadow_release_pair: Object.freeze(["release_shadow_run"]),
  ck_shadow_counts: Object.freeze(["release_shadow_run"]),
  ck_shadow_summary_counts: Object.freeze(["release_shadow_run"]),
  ck_shadow_run_completion: Object.freeze(["release_shadow_run"]),
  ck_evaluation_state: Object.freeze(["decision_evaluation"]),
  ck_evaluation_times: Object.freeze(["decision_evaluation"]),
  ck_evaluation_shadow_comparison: Object.freeze(["decision_evaluation"]),
  ck_feature_result_value: Object.freeze(["evaluation_feature_result"]),
  ck_evaluation_rule_trace: Object.freeze(["evaluation_rule_trace"]),
  ck_evaluation_action_intent_source: Object.freeze(["evaluation_action_intent"]),
  ck_evaluation_action_intent_effect: Object.freeze(["evaluation_action_intent"]),
  ck_action_effect_execution: Object.freeze(["action_effect"]),
  ck_restriction_range: Object.freeze(["risk_restriction", "risk_list_entry"]),
  ck_list_entry_active_slot: Object.freeze(["risk_list_entry"]),
  ck_velocity_money: Object.freeze(["velocity_event"]),
  ck_limit_threshold: Object.freeze(["limit_version"]),
  ck_limit_currency_mode: Object.freeze(["limit_version"]),
  ck_limit_version_freeze: Object.freeze(["limit_version"]),
  ck_limit_binding_subject: Object.freeze(["decision_point_limit_binding"]),
  ck_limit_binding_amount: Object.freeze(["decision_point_limit_binding"]),
  ck_limit_position: Object.freeze(["limit_position"]),
  ck_limit_group: Object.freeze(["limit_reservation_group"]),
  ck_limit_reservation: Object.freeze(["limit_reservation"]),
  ck_limit_group_change: Object.freeze(["limit_group_state_change"]),
  ck_limit_movement: Object.freeze(["limit_movement"]),
  ck_settle_control_type: Object.freeze(["settlement_control"]),
  ck_settle_control_status_slot: Object.freeze(["settlement_control"]),
  ck_settle_control_range: Object.freeze(["settlement_control"]),
  ck_settle_control_instance_key: Object.freeze(["settlement_control"]),
  ck_settle_control_state: Object.freeze(["settlement_control_state_change"]),
  ck_settle_control_trigger: Object.freeze(["settlement_control_state_change"]),
  ck_settle_assessment_input: Object.freeze(["settlement_risk_assessment"]),
  ck_settle_assessment_result: Object.freeze(["settlement_risk_assessment"]),
  ck_dispute_deadlines: Object.freeze(["dispute_case"]),
  ck_dispute_case_outcome: Object.freeze(["dispute_case"]),
  ck_dispute_state: Object.freeze(["dispute_state_change"]),
  ck_dispute_package_version: Object.freeze(["dispute_evidence_package"]),
  ck_dispute_request_slot_time: Object.freeze(["dispute_submission_request"]),
  ck_dispute_request_action: Object.freeze(["dispute_submission_request"]),
  ck_dispute_outcome_version: Object.freeze(["dispute_outcome"]),
  ck_dispute_outcome_source: Object.freeze(["dispute_outcome"]),
  ck_liability_recommendation: Object.freeze(["dispute_liability_decision"]),
  ck_liability_security_deposit: Object.freeze(["dispute_liability_decision"]),
  ck_compensation_lease: Object.freeze(["compensation_event"]),
});

export const ACTION_EFFECT_CANONICALIZATION = Object.freeze({
  canonicalizerCode: "RISK_ACTION_EFFECT_V1",
  canonicalFormat: "RFC_8785_JCS",
  textEncoding: "UTF-8",
  digestAlgorithm: "SHA-256",
  digestEncoding: "LOWERCASE_HEX",
  bigIntEncoding: "CANONICAL_DECIMAL_STRING",
  effectFields: Object.freeze([
    "action_version_id", "effect_mode", "evaluation_id", "merge_policy", "target_key", "target_type",
  ]),
  contributorTable: "evaluation_action_intent",
  contributorFilter: Object.freeze({ field: "status", value: "MERGED" }),
  contributorLink: Object.freeze({ field: "merged_effect_id", effectField: "effect_id" }),
  contributorOrder: Object.freeze(["intent_sequence", "intent_id"]),
  contributorFields: Object.freeze([
    "intent_id", "intent_sequence", "obligation_id", "resolved_target_key",
    "resolved_target_type", "rule_action_id", "source_type",
  ]),
  excludedEffectFields: Object.freeze([
    "effect_id", "effect_status", "idempotency_key", "execution_type", "execution_status",
    "attempt_count", "started_time", "completed_time", "last_error_code", "last_error_summary",
  ]),
  realIdempotencyKeyPrefix: "risk-action-effect:v1:",
});

// These cross-row contracts cannot be represented truthfully by the current portable
// CHECK-only DSL. Task 5 must validate them against the joined business rows.
export const CROSS_ROW_CONTRACTS = Object.freeze({
  CR_EVALUATION_DEFAULT_DECISION: Object.freeze({
    code: "CR_EVALUATION_DEFAULT_DECISION",
    contractType: "JOIN_ASSERTIONS",
    root: Object.freeze({
      alias: "evaluation",
      table: "decision_evaluation",
      predicates: Object.freeze([
        Object.freeze({ field: "default_decision_used_flag", operator: "EQUALS", value: true }),
      ]),
    }),
    joins: Object.freeze([
      Object.freeze({
        from: Object.freeze({ alias: "evaluation", field: "point_version_id" }),
        to: Object.freeze({ alias: "pointVersion", table: "decision_point_version", field: "point_version_id" }),
        required: true,
      }),
    ]),
    rowPredicates: Object.freeze([]),
    fieldAssertions: Object.freeze([
      Object.freeze({
        left: Object.freeze({ alias: "evaluation", field: "proposed_decision" }),
        operator: "EQUALS",
        right: Object.freeze({ alias: "pointVersion", field: "default_decision" }),
      }),
    ]),
  }),
  CR_EVALUATION_EFFECT_MODE: Object.freeze({
    code: "CR_EVALUATION_EFFECT_MODE",
    contractType: "ALLOWED_MAPPING",
    selectors: Object.freeze([
      Object.freeze({ mappingKey: "evaluationMode", table: "decision_evaluation", field: "evaluation_mode" }),
      Object.freeze({ mappingKey: "technicalStatus", table: "decision_evaluation", field: "technical_status" }),
      Object.freeze({ mappingKey: "effectMode", table: "action_effect", field: "effect_mode" }),
    ]),
    joins: Object.freeze([
      Object.freeze({
        from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }),
        to: Object.freeze({ table: "action_effect", field: "evaluation_id" }),
      }),
    ]),
    requiredCoverage: Object.freeze([
      Object.freeze({ table: "action_effect", field: "effect_id" }),
    ]),
    allowedMappings: Object.freeze([
      Object.freeze({ evaluationMode: "PRIMARY", technicalStatus: "SUCCEEDED", effectMode: "REAL" }),
      Object.freeze({ evaluationMode: "SHADOW", technicalStatus: "SUCCEEDED", effectMode: "SIMULATED" }),
      Object.freeze({ evaluationMode: "REPLAY", technicalStatus: "SUCCEEDED", effectMode: "SIMULATED" }),
    ]),
  }),
  CR_INTENT_RULE_SOURCE: Object.freeze({
    code: "CR_INTENT_RULE_SOURCE",
    contractType: "JOIN_ASSERTIONS",
    contextQuantifier: "ANY",
    root: Object.freeze({
      alias: "intent",
      table: "evaluation_action_intent",
      predicates: Object.freeze([
        Object.freeze({ field: "source_type", operator: "EQUALS", value: "RULE" }),
      ]),
    }),
    joins: Object.freeze([
      Object.freeze({
        from: Object.freeze({ alias: "intent", field: "rule_action_id" }),
        to: Object.freeze({ alias: "source", table: "rule_action", field: "rule_action_id" }),
        required: true,
      }),
      Object.freeze({
        from: Object.freeze({ alias: "intent", field: "evaluation_id" }),
        to: Object.freeze({ alias: "evaluation", table: "decision_evaluation", field: "evaluation_id" }),
        required: true,
      }),
      Object.freeze({
        from: Object.freeze({ alias: "source", field: "rule_id" }),
        to: Object.freeze({ alias: "rule", table: "risk_rule", field: "rule_id" }),
        required: true,
      }),
      Object.freeze({
        from: Object.freeze({ alias: "rule", field: "rule_set_version_id" }),
        to: Object.freeze({ alias: "binding", table: "decision_point_rule_set", field: "rule_set_version_id" }),
        required: false,
      }),
    ]),
    rowPredicates: Object.freeze([]),
    fieldAssertions: Object.freeze([
      Object.freeze({
        left: Object.freeze({ alias: "intent", field: "action_version_id" }),
        operator: "EQUALS",
        right: Object.freeze({ alias: "source", field: "action_version_id" }),
      }),
      Object.freeze({
        left: Object.freeze({ alias: "binding", field: "point_version_id" }),
        operator: "EQUALS",
        right: Object.freeze({ alias: "evaluation", field: "point_version_id" }),
      }),
    ]),
  }),
  CR_INTENT_OBLIGATION_SOURCE: Object.freeze({
    code: "CR_INTENT_OBLIGATION_SOURCE",
    contractType: "JOIN_ASSERTIONS",
    root: Object.freeze({
      alias: "intent",
      table: "evaluation_action_intent",
      predicates: Object.freeze([
        Object.freeze({ field: "source_type", operator: "EQUALS", value: "OBLIGATION" }),
      ]),
    }),
    joins: Object.freeze([
      Object.freeze({
        from: Object.freeze({ alias: "intent", field: "obligation_id" }),
        to: Object.freeze({ alias: "source", table: "decision_point_obligation", field: "obligation_id" }),
        required: true,
      }),
      Object.freeze({
        from: Object.freeze({ alias: "intent", field: "evaluation_id" }),
        to: Object.freeze({ alias: "evaluation", table: "decision_evaluation", field: "evaluation_id" }),
        required: true,
      }),
    ]),
    rowPredicates: Object.freeze([
      Object.freeze({ endpoint: Object.freeze({ alias: "source", field: "obligation_type" }), operator: "EQUALS", value: "ACTION" }),
    ]),
    fieldAssertions: Object.freeze([
      Object.freeze({
        left: Object.freeze({ alias: "intent", field: "action_version_id" }),
        operator: "EQUALS",
        right: Object.freeze({ alias: "source", field: "action_version_id" }),
      }),
      Object.freeze({
        left: Object.freeze({ alias: "source", field: "point_version_id" }),
        operator: "EQUALS",
        right: Object.freeze({ alias: "evaluation", field: "point_version_id" }),
      }),
    ]),
  }),
  CR_MERGED_INTENT_EFFECT: Object.freeze({
    code: "CR_MERGED_INTENT_EFFECT",
    contractType: "JOIN_ASSERTIONS",
    root: Object.freeze({
      alias: "intent",
      table: "evaluation_action_intent",
      predicates: Object.freeze([
        Object.freeze({ field: "status", operator: "EQUALS", value: "MERGED" }),
      ]),
    }),
    joins: Object.freeze([
      Object.freeze({
        from: Object.freeze({ alias: "intent", field: "merged_effect_id" }),
        to: Object.freeze({ alias: "effect", table: "action_effect", field: "effect_id" }),
        required: true,
      }),
      Object.freeze({
        from: Object.freeze({ alias: "intent", field: "action_version_id" }),
        to: Object.freeze({ alias: "actionVersion", table: "action_version", field: "action_version_id" }),
        required: true,
      }),
    ]),
    rowPredicates: Object.freeze([]),
    fieldAssertions: Object.freeze([
      Object.freeze({ left: Object.freeze({ alias: "intent", field: "evaluation_id" }), operator: "EQUALS", right: Object.freeze({ alias: "effect", field: "evaluation_id" }) }),
      Object.freeze({ left: Object.freeze({ alias: "intent", field: "action_version_id" }), operator: "EQUALS", right: Object.freeze({ alias: "effect", field: "action_version_id" }) }),
      Object.freeze({ left: Object.freeze({ alias: "intent", field: "resolved_target_type" }), operator: "EQUALS", right: Object.freeze({ alias: "effect", field: "target_type" }) }),
      Object.freeze({ left: Object.freeze({ alias: "intent", field: "resolved_target_key" }), operator: "EQUALS", right: Object.freeze({ alias: "effect", field: "target_key" }) }),
      Object.freeze({ left: Object.freeze({ alias: "actionVersion", field: "merge_policy" }), operator: "EQUALS", right: Object.freeze({ alias: "effect", field: "merge_policy" }) }),
    ]),
  }),
  CR_EFFECT_HAS_MERGED_INTENT: Object.freeze({
    code: "CR_EFFECT_HAS_MERGED_INTENT",
    contractType: "JOIN_ASSERTIONS",
    root: Object.freeze({ alias: "effect", table: "action_effect", predicates: Object.freeze([]) }),
    joins: Object.freeze([
      Object.freeze({
        from: Object.freeze({ alias: "effect", field: "effect_id" }),
        to: Object.freeze({ alias: "intent", table: "evaluation_action_intent", field: "merged_effect_id" }),
        required: false,
      }),
    ]),
    rowPredicates: Object.freeze([
      Object.freeze({ endpoint: Object.freeze({ alias: "intent", field: "status" }), operator: "EQUALS", value: "MERGED" }),
    ]),
    fieldAssertions: Object.freeze([
      Object.freeze({ left: Object.freeze({ alias: "effect", field: "evaluation_id" }), operator: "EQUALS", right: Object.freeze({ alias: "intent", field: "evaluation_id" }) }),
      Object.freeze({ left: Object.freeze({ alias: "effect", field: "action_version_id" }), operator: "EQUALS", right: Object.freeze({ alias: "intent", field: "action_version_id" }) }),
      Object.freeze({ left: Object.freeze({ alias: "effect", field: "target_type" }), operator: "EQUALS", right: Object.freeze({ alias: "intent", field: "resolved_target_type" }) }),
      Object.freeze({ left: Object.freeze({ alias: "effect", field: "target_key" }), operator: "EQUALS", right: Object.freeze({ alias: "intent", field: "resolved_target_key" }) }),
    ]),
  }),
  CR_EVALUATION_SNAPSHOT_DOMAIN: Object.freeze({
    code: "CR_EVALUATION_SNAPSHOT_DOMAIN",
    contractType: "CONDITIONAL_CARDINALITY",
    root: Object.freeze({ alias: "evaluation", table: "decision_evaluation", key: "evaluation_id" }),
    joins: Object.freeze([
      Object.freeze({
        from: Object.freeze({ alias: "evaluation", field: "decision_point_id" }),
        to: Object.freeze({ alias: "decisionPoint", table: "decision_point", field: "decision_point_id" }),
        required: true,
      }),
    ]),
    discriminator: Object.freeze({ alias: "decisionPoint", field: "business_domain" }),
    children: Object.freeze([
      Object.freeze({ key: "checkoutSnapshot", table: "checkout_input_snapshot", foreignKey: "evaluation_id" }),
      Object.freeze({ key: "paymentSnapshot", table: "transaction_risk_assessment", foreignKey: "evaluation_id" }),
      Object.freeze({ key: "settlementSnapshot", table: "settlement_risk_assessment", foreignKey: "evaluation_id" }),
    ]),
    cases: Object.freeze([
      Object.freeze({ values: Object.freeze(["CHECKOUT"]), exactly: Object.freeze({ checkoutSnapshot: 1, paymentSnapshot: 0, settlementSnapshot: 0 }) }),
      Object.freeze({ values: Object.freeze(["PAYMENT"]), exactly: Object.freeze({ checkoutSnapshot: 0, paymentSnapshot: 1, settlementSnapshot: 0 }) }),
      Object.freeze({ values: Object.freeze(["SETTLEMENT"]), exactly: Object.freeze({ checkoutSnapshot: 0, paymentSnapshot: 0, settlementSnapshot: 1 }) }),
      Object.freeze({ values: Object.freeze(["DUE_DILIGENCE", "DISPUTE"]), exactly: Object.freeze({ checkoutSnapshot: 0, paymentSnapshot: 0, settlementSnapshot: 0 }) }),
    ]),
  }),
  CR_SHADOW_COMPARISON: Object.freeze({
    code: "CR_SHADOW_COMPARISON",
    contractType: "JOIN_ASSERTIONS",
    root: Object.freeze({
      alias: "candidate",
      table: "decision_evaluation",
      predicates: Object.freeze([
        Object.freeze({ field: "evaluation_mode", operator: "EQUALS", value: "SHADOW" }),
      ]),
    }),
    joins: Object.freeze([
      Object.freeze({
        from: Object.freeze({ alias: "candidate", field: "baseline_evaluation_id" }),
        to: Object.freeze({ alias: "baseline", table: "decision_evaluation", field: "evaluation_id" }),
        required: true,
      }),
      Object.freeze({
        from: Object.freeze({ alias: "candidate", field: "shadow_run_id" }),
        to: Object.freeze({ alias: "run", table: "release_shadow_run", field: "shadow_run_id" }),
        required: true,
      }),
    ]),
    rowPredicates: Object.freeze([
      Object.freeze({ endpoint: Object.freeze({ alias: "baseline", field: "evaluation_mode" }), operator: "EQUALS", value: "PRIMARY" }),
    ]),
    fieldAssertions: Object.freeze([
      Object.freeze({ left: Object.freeze({ alias: "candidate", field: "evaluation_id" }), operator: "NOT_EQUALS", right: Object.freeze({ alias: "baseline", field: "evaluation_id" }) }),
      Object.freeze({ left: Object.freeze({ alias: "candidate", field: "decision_point_id" }), operator: "EQUALS", right: Object.freeze({ alias: "baseline", field: "decision_point_id" }) }),
      Object.freeze({ left: Object.freeze({ alias: "candidate", field: "evaluation_time" }), operator: "EQUALS", right: Object.freeze({ alias: "baseline", field: "evaluation_time" }) }),
      Object.freeze({ left: Object.freeze({ alias: "candidate", field: "input_snapshot_hash" }), operator: "EQUALS", right: Object.freeze({ alias: "baseline", field: "input_snapshot_hash" }) }),
      Object.freeze({ left: Object.freeze({ alias: "candidate", field: "release_id" }), operator: "EQUALS", right: Object.freeze({ alias: "run", field: "candidate_release_id" }) }),
      Object.freeze({ left: Object.freeze({ alias: "baseline", field: "release_id" }), operator: "EQUALS", right: Object.freeze({ alias: "run", field: "baseline_release_id" }) }),
      Object.freeze({ left: Object.freeze({ alias: "candidate", field: "shadow_run_id" }), operator: "EQUALS", right: Object.freeze({ alias: "run", field: "shadow_run_id" }) }),
    ]),
  }),
  CR_SIMULATED_EFFECT_DOWNSTREAM: Object.freeze({
    code: "CR_SIMULATED_EFFECT_DOWNSTREAM",
    contractType: "FORBIDDEN_PATHS",
    trigger: Object.freeze({ table: "decision_evaluation", field: "evaluation_mode", operator: "IN", values: Object.freeze(["SHADOW", "REPLAY"]) }),
    forbiddenPaths: Object.freeze([
      Object.freeze({
        code: "CR_SIMULATED_EFFECT_RISK_RESTRICTION",
        joins: Object.freeze([
          Object.freeze({ from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }), to: Object.freeze({ table: "action_effect", field: "evaluation_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "action_effect", field: "effect_id" }), to: Object.freeze({ table: "risk_restriction", field: "source_effect_id" }) }),
        ]),
        targetPredicates: Object.freeze([]),
      }),
      Object.freeze({
        code: "CR_SIMULATED_EFFECT_RISK_LIST_ENTRY",
        joins: Object.freeze([
          Object.freeze({ from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }), to: Object.freeze({ table: "action_effect", field: "evaluation_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "action_effect", field: "effect_id" }), to: Object.freeze({ table: "risk_list_entry", field: "source_effect_id" }) }),
        ]),
        targetPredicates: Object.freeze([]),
      }),
      Object.freeze({
        code: "CR_SIMULATED_EFFECT_LIMIT_RESERVATION_GROUP",
        joins: Object.freeze([
          Object.freeze({ from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }), to: Object.freeze({ table: "transaction_risk_assessment", field: "evaluation_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "transaction_risk_assessment", field: "assessment_id" }), to: Object.freeze({ table: "limit_reservation_group", field: "assessment_id" }) }),
        ]),
        targetPredicates: Object.freeze([]),
      }),
      Object.freeze({
        code: "CR_SIMULATED_EFFECT_LIMIT_RESERVATION",
        joins: Object.freeze([
          Object.freeze({ from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }), to: Object.freeze({ table: "transaction_risk_assessment", field: "evaluation_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "transaction_risk_assessment", field: "assessment_id" }), to: Object.freeze({ table: "limit_reservation_group", field: "assessment_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "limit_reservation_group", field: "reservation_group_id" }), to: Object.freeze({ table: "limit_reservation", field: "reservation_group_id" }) }),
        ]),
        targetPredicates: Object.freeze([]),
      }),
      Object.freeze({
        code: "CR_SIMULATED_EFFECT_LIMIT_MOVEMENT",
        joins: Object.freeze([
          Object.freeze({ from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }), to: Object.freeze({ table: "transaction_risk_assessment", field: "evaluation_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "transaction_risk_assessment", field: "assessment_id" }), to: Object.freeze({ table: "limit_reservation_group", field: "assessment_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "limit_reservation_group", field: "reservation_group_id" }), to: Object.freeze({ table: "limit_reservation", field: "reservation_group_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "limit_reservation", field: "reservation_id" }), to: Object.freeze({ table: "limit_movement", field: "reservation_id" }) }),
        ]),
        targetPredicates: Object.freeze([]),
      }),
      Object.freeze({
        code: "CR_SIMULATED_EFFECT_DISPUTE_LIABILITY",
        joins: Object.freeze([
          Object.freeze({ from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }), to: Object.freeze({ table: "action_effect", field: "evaluation_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "action_effect", field: "effect_id" }), to: Object.freeze({ table: "dispute_liability_decision", field: "action_effect_id" }) }),
        ]),
        targetPredicates: Object.freeze([]),
      }),
      Object.freeze({
        code: "CR_SIMULATED_EFFECT_COMPENSATION_EVENT",
        joins: Object.freeze([
          Object.freeze({ from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }), to: Object.freeze({ table: "action_effect", field: "evaluation_id" }) }),
          Object.freeze({ from: Object.freeze({ table: "action_effect", field: "idempotency_key" }), to: Object.freeze({ table: "compensation_event", field: "event_key" }) }),
        ]),
        targetPredicates: Object.freeze([
          Object.freeze({ field: "event_type", operator: "EQUALS", value: "EXECUTE_RISK_ACTION_EFFECT" }),
          Object.freeze({ field: "sub_event_type", operator: "EQUALS", value: "APPLY_ASYNC_ACTION_EFFECT" }),
        ]),
      }),
      Object.freeze({
        code: "CR_SIMULATED_EFFECT_VELOCITY_EVENT",
        joins: Object.freeze([
          Object.freeze({ from: Object.freeze({ table: "decision_evaluation", field: "evaluation_id" }), to: Object.freeze({ table: "velocity_event", field: "source_id" }) }),
        ]),
        targetPredicates: Object.freeze([
          Object.freeze({ field: "source_type", operator: "IN", values: Object.freeze(["CHECKOUT_EVALUATION"]) }),
        ]),
      }),
    ]),
  }),
});

// RCO-17 没有反向扫描消费者：当前指针从 flow_id 读取；其余引用从版本内有界节点/连线读取。
// 保留逻辑关系，不为尚无查询的反向方向新增物理索引。
export const VERSION_SCOPED_RELATIONS = Object.freeze([
  "decision_flow.current_flow_version_id",
  "decision_flow_node.rule_set_version_id",
  "decision_flow_edge.source_node_id",
  "decision_flow_edge.target_node_id",
]);
