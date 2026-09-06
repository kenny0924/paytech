import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { TABLES as DECISION_ENGINE_TABLES } from "./risk/manifests/02-decision-engine.mjs";
import { TABLES as EVALUATION_TABLES } from "./risk/manifests/03-evaluation.mjs";
import { TABLES as RISK_TABLES } from "./risk/manifest.mjs";
import { CHECK_BINDINGS, CHECK_CONTRACTS, CROSS_ROW_CONTRACTS } from "./risk/model-policy.mjs";
import * as MODEL_POLICY from "./risk/model-policy.mjs";
import { canonicalizeSettlementActionParameterIdentity } from "./risk/settlement-control-canonicalizer.mjs";
import {
  canonicalizeRiskCreateRuleSetVersionCommand,
  canonicalizeRiskCreateTestReleaseCommand,
  canonicalizeRiskDecisionPointVersion,
  canonicalizeRiskDecisionRelease,
  canonicalizeRiskFreezeRuleSetVersionCommand,
  canonicalizeRiskRuleSetVersion,
  canonicalizeRiskValidateTestReleaseCommand,
  executeCrossRowContractRegistry,
  materializeDecisionStaticChecks,
  summarizeDecisionStaticViolations,
  validateDecisionRelease,
  validateReleaseTransition,
  validateSettlementAssessmentDomainContractMetadata,
} from "./risk/decision-engine-static-validator.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const modelPath = path.join(
  workspaceRoot,
  "risk-compliance-service/docs/database/risk-compliance.pdma.json",
);
const decisionGoldenVectorPath = path.join(
  workspaceRoot,
  "risk-compliance-service/risk-test/src/test/resources/fixtures/risk-decision-v1-golden-vectors.json",
);

function readDecisionGoldenVectors() {
  return JSON.parse(readFileSync(decisionGoldenVectorPath, "utf8"));
}

function readModel() {
  return JSON.parse(readFileSync(modelPath, "utf8"));
}

function entitiesByKey(model) {
  return new Map(model.entities.map((entity) => [entity.defKey, entity]));
}

function fieldNames(entity) {
  return new Set(entity.fields.map((field) => field.defKey));
}

function indexFields(entity, index) {
  const fieldsByReference = new Map(entity.fields.flatMap((field) => [
    [field.id, field.defKey],
    [field.defKey, field.defKey],
  ]));
  return index.fields.map((entry) => fieldsByReference.get(entry.fieldDefKey));
}

function assertFields(entity, expectedFields) {
  const actual = fieldNames(entity);
  for (const field of expectedFields) {
    assert.ok(actual.has(field), `${entity.defKey} 缺少目标字段：${field}`);
  }
}

function assertAbsentFields(entity, forbiddenFields) {
  const actual = fieldNames(entity);
  for (const field of forbiddenFields) {
    assert.ok(!actual.has(field), `${entity.defKey} 保留了已删除字段：${field}`);
  }
}

function assertUniqueIndex(entity, expectedFields) {
  assert.ok(
    entity.indexes.some((index) => index.unique
      && JSON.stringify(indexFields(entity, index)) === JSON.stringify(expectedFields)),
    `${entity.defKey} 缺少唯一键：(${expectedFields.join(", ")})`,
  );
}

function assertAccessIndex(entity, expectedFields) {
  assert.ok(
    entity.indexes.some((index) => !index.unique
      && JSON.stringify(indexFields(entity, index)) === JSON.stringify(expectedFields)),
    `${entity.defKey} 缺少访问索引：(${expectedFields.join(", ")})`,
  );
}

function assertRelation(entities, childTable, childField, parentTable, parentField) {
  const child = entities.get(childTable);
  const parent = entities.get(parentTable);
  const childFieldsById = new Map(child.fields.map((field) => [field.id, field.defKey]));
  const parentFieldsById = new Map(parent.fields.map((field) => [field.id, field.defKey]));
  assert.ok(
    child.correlations.some((relation) => (
      childFieldsById.get(relation.myField) === childField
      && relation.refEntity === parent.id
      && parentFieldsById.get(relation.refField) === parentField
    )),
    `缺少目标关系：${childTable}.${childField} -> ${parentTable}.${parentField}`,
  );
}

function manifestTablesByKey() {
  return new Map(DECISION_ENGINE_TABLES.map((table) => [table.defKey, table]));
}

function manifestFieldNames(table) {
  return new Set(table.fields.map((field) => field.defKey));
}

function assertManifestUniqueIndex(table, expectedFields) {
  assert.ok(
    table.indexes.some((index) => index.unique
      && JSON.stringify(index.fields) === JSON.stringify(expectedFields)),
    `${table.defKey} Manifest 缺少唯一键：(${expectedFields.join(", ")})`,
  );
}

function assertManifestAccessIndex(table, defKey, expectedFields) {
  assert.ok(
    table.indexes.some((index) => index.defKey === defKey
      && !index.unique
      && JSON.stringify(index.fields) === JSON.stringify(expectedFields)),
    `${table.defKey} Manifest 缺少访问索引 ${defKey}：(${expectedFields.join(", ")})`,
  );
}

const CONFIG_CHECK_CODES = Object.freeze([
  "REFERENCE_INTEGRITY",
  "AST_SHAPE",
  "AST_TYPES",
  "FEATURE_DEPENDENCY",
  "HANDLER_HASH",
  "ACTION_PARAMETERS",
  "TERMINAL_ACTION",
  "SYSTEM_OBLIGATION",
]);

const STATE_CHECK_CODES = Object.freeze([
  "SHADOW_GATE",
  "APPROVAL_GATE",
  "STATE_TRANSITION",
  "ACTIVATION_PAIR",
]);

const hashOf = (character) => character.repeat(64);

function disputeLiabilityDecisionRow(overrides = {}) {
  return {
    outcome_id: "350101",
    decision_evaluation_id: null,
    action_effect_id: null,
    recommended_principal_bearer: null,
    principal_bearer: "CLIENT",
    provider_fee_bearer: "CLIENT",
    platform_error_flag: false,
    provider_recovery_required: false,
    security_deposit_use_allowed: false,
    override_reason_code: null,
    reason_code: "CLIENT_RESPONSIBILITY",
    decided_by: "900001",
    decided_time: "2026-08-28T10:00:00.000000Z",
    create_time: "2026-08-28T10:00:00.000000Z",
    ...overrides,
  };
}

const SETTLEMENT_ACTION_PARAMETER_IDENTITY = "{\"actionParameters\":[{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241101\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"CLIENT\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241102\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"MIN_SECURITY_DEPOSIT\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241103\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"DISPUTE_EXPOSURE\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241111\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"210001\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241111\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"210002\",\"valueSequence\":2},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241112\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"USD\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241112\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"USD\",\"valueSequence\":2},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241113\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"AE\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241113\",\"ruleActionId\":\"242101\",\"sourceType\":\"LITERAL\",\"stringValue\":\"*\",\"valueSequence\":2}]}";
const SETTLEMENT_RELEASE_CONTENT_HASH = "ab49fb5416d46a2764e16f28f4854cbb45a8a28c5d80c188785f9a0170f1389d";
const SETTLEMENT_SINGLE_SCOPE_CONTENT_HASH = "bba2a3c6822d366a50494717189cc32e7da400058064e6dd825956b48f60c984";
const SETTLEMENT_CONTEXT_SCOPE_CONTENT_HASH = "77db850128895a63e958ed375aa552573eea32fe22a9ce437371c3dd95f6edea";
const SETTLEMENT_DUPLICATE_RULE_CONTENT_HASH = "0260722165236f355f617f444bb3f8aea0a72144f45f35a8b980aa9300e6e1fa";
const SETTLEMENT_LIMIT_DISPLAY_CONTENT_HASH = "b91a0aa157ab39fca21e4dea2a48476e02862588239c18c8f50be32140cf3b3b";
const REQUIRED_RUNTIME_COLLECTIONS = Object.freeze([
  "action_effect", "action_version", "checkout_input_snapshot", "compensation_event",
  "decision_evaluation", "decision_point", "decision_point_limit_binding",
  "decision_point_obligation", "decision_point_rule_set", "decision_point_version",
  "dispute_liability_decision", "evaluation_action_intent", "limit_movement",
  "limit_reservation", "limit_reservation_group", "release_shadow_run", "risk_list_entry",
  "risk_restriction", "risk_rule", "rule_action", "settlement_control",
  "settlement_control_scope", "settlement_control_state_change", "settlement_risk_assessment",
  "transaction_risk_assessment", "velocity_event",
]);

function emptyCrossRowRows() {
  return {
    decision_point: [
      { decision_point_id: "210001", business_domain: "PAYMENT" },
      { decision_point_id: "210101", point_code: "PAYOUT_CREATION", business_domain: "SETTLEMENT" },
      { decision_point_id: "210102", point_code: "PAYOUT_FINAL_APPROVAL", business_domain: "SETTLEMENT" },
      { decision_point_id: "210103", point_code: "PAYOUT_EXECUTION_CREATION", business_domain: "SETTLEMENT" },
      { decision_point_id: "210104", point_code: "PAYOUT_EXTERNAL_SUBMISSION", business_domain: "SETTLEMENT" },
    ],
    decision_point_version: [
      { point_version_id: "210011", default_decision: "DENY" },
      { point_version_id: "210111", decision_point_id: "210101", default_decision: "DENY" },
      { point_version_id: "210112", decision_point_id: "210102", default_decision: "DENY" },
      { point_version_id: "210113", decision_point_id: "210103", default_decision: "DENY" },
      { point_version_id: "210114", decision_point_id: "210104", default_decision: "DENY" },
    ],
    decision_evaluation: [
      { evaluation_id: "310001", evaluation_mode: "PRIMARY", technical_status: "SUCCEEDED", decision_point_id: "210001", point_version_id: "210011", release_id: "260001", evaluation_time: "2026-08-27T10:00:00.000000Z", input_snapshot_hash: hashOf("1"), proposed_decision: "ALLOW", default_decision_used_flag: false },
      { evaluation_id: "310002", evaluation_mode: "SHADOW", technical_status: "SUCCEEDED", decision_point_id: "210001", point_version_id: "210011", release_id: "260002", evaluation_time: "2026-08-27T10:00:00.000000Z", input_snapshot_hash: hashOf("1"), proposed_decision: "ALLOW", default_decision_used_flag: false, shadow_run_id: "270001", baseline_evaluation_id: "310001" },
      { evaluation_id: "310003", evaluation_mode: "REPLAY", technical_status: "SUCCEEDED", decision_point_id: "210001", point_version_id: "210011", release_id: "260002", evaluation_time: "2026-08-27T10:00:00.000000Z", input_snapshot_hash: hashOf("1"), proposed_decision: "ALLOW", default_decision_used_flag: false },
    ],
    release_shadow_run: [
      { shadow_run_id: "270001", baseline_release_id: "260001", candidate_release_id: "260002" },
    ],
    action_version: [
      { action_version_id: "240011", merge_policy: "MAX" },
    ],
    decision_point_rule_set: [
      { binding_id: "211001", point_version_id: "210011", rule_set_version_id: "230011" },
    ],
    risk_rule: [
      { rule_id: "231001", rule_set_version_id: "230011" },
    ],
    rule_action: [
      { rule_action_id: "242001", rule_id: "231001", action_version_id: "240011" },
    ],
    decision_point_obligation: [
      { obligation_id: "259101", point_version_id: "210111", trigger_decision: "ALLOW", obligation_type: "CHECK_SETTLEMENT_ELIGIBILITY", action_version_id: null, sequence_no: 1, failure_policy: "FAIL_CLOSED", enabled_flag: true },
      { obligation_id: "259102", point_version_id: "210112", trigger_decision: "ALLOW", obligation_type: "CHECK_SETTLEMENT_ELIGIBILITY", action_version_id: null, sequence_no: 1, failure_policy: "FAIL_CLOSED", enabled_flag: true },
      { obligation_id: "259103", point_version_id: "210113", trigger_decision: "ALLOW", obligation_type: "CHECK_SETTLEMENT_ELIGIBILITY", action_version_id: null, sequence_no: 1, failure_policy: "FAIL_CLOSED", enabled_flag: true },
      { obligation_id: "259104", point_version_id: "210114", trigger_decision: "ALLOW", obligation_type: "CHECK_SETTLEMENT_ELIGIBILITY", action_version_id: null, sequence_no: 1, failure_policy: "FAIL_CLOSED", enabled_flag: true },
    ],
    decision_point_limit_binding: [],
    evaluation_action_intent: [
      { intent_id: "314001", evaluation_id: "310001", source_type: "RULE", rule_action_id: "242001", action_version_id: "240011", resolved_target_type: "CONSUMER", resolved_target_key: "consumer-C-9001", intent_sequence: 1, merged_effect_id: "315001", status: "MERGED" },
      { intent_id: "314002", evaluation_id: "310002", source_type: "RULE", rule_action_id: "242001", action_version_id: "240011", resolved_target_type: "CONSUMER", resolved_target_key: "consumer-C-9002", intent_sequence: 1, merged_effect_id: "315002", status: "MERGED" },
      { intent_id: "314003", evaluation_id: "310003", source_type: "RULE", rule_action_id: "242001", action_version_id: "240011", resolved_target_type: "CONSUMER", resolved_target_key: "consumer-C-9003", intent_sequence: 1, merged_effect_id: "315003", status: "MERGED" },
    ],
    action_effect: [
      { effect_id: "315001", evaluation_id: "310001", action_version_id: "240011", effect_mode: "REAL", target_type: "CONSUMER", target_key: "consumer-C-9001", merge_policy: "MAX", idempotency_key: "risk-action-effect:v1:315001" },
      { effect_id: "315002", evaluation_id: "310002", action_version_id: "240011", effect_mode: "SIMULATED", target_type: "CONSUMER", target_key: "consumer-C-9002", merge_policy: "MAX", idempotency_key: "risk-action-effect:v1:315002" },
      { effect_id: "315003", evaluation_id: "310003", action_version_id: "240011", effect_mode: "SIMULATED", target_type: "CONSUMER", target_key: "consumer-C-9003", merge_policy: "MAX", idempotency_key: "risk-action-effect:v1:315003" },
    ],
    checkout_input_snapshot: [],
    transaction_risk_assessment: [
      { assessment_id: "321001", evaluation_id: "310001" },
      { assessment_id: "321002", evaluation_id: "310002" },
      { assessment_id: "321003", evaluation_id: "310003" },
    ],
    settlement_risk_assessment: [],
    risk_restriction: [],
    risk_list_entry: [],
    limit_reservation_group: [],
    limit_reservation: [],
    limit_movement: [],
    settlement_control: [],
    settlement_control_scope: [],
    settlement_control_state_change: [],
    dispute_liability_decision: [],
    compensation_event: [],
    velocity_event: [],
  };
}

function buildValidDecisionFixture() {
  return {
    release: {
      releaseId: "260002",
      decisionPointId: "210001",
      pointVersionId: "210011",
      environmentCode: "PRODUCTION",
      contentHash: hashOf("6"),
      status: "DRAFT",
    },
    decisionPoint: {
      decisionPointId: "210001",
      pointCode: "PAYMENT_ATTEMPT_RISK",
      status: "ACTIVE",
    },
    pointVersion: {
      pointVersionId: "210011",
      decisionPointId: "210001",
      defaultDecision: "DENY",
      failureStrategy: "FAIL_CLOSED",
    },
    inputFields: [
      {
        inputFieldId: "210021",
        pointVersionId: "210011",
        fieldCode: "client_ip_address",
        valueType: "STRING",
        ruleReferenceFlag: true,
        actionParameterFlag: true,
      },
      {
        inputFieldId: "210022",
        pointVersionId: "210011",
        fieldCode: "consumer_reference",
        valueType: "STRING",
        ruleReferenceFlag: true,
        actionParameterFlag: true,
      },
    ],
    pointFeatures: [
      { bindingId: "220101", pointVersionId: "210011", featureVersionId: "220011", sequenceNo: 1 },
      { bindingId: "220102", pointVersionId: "210011", featureVersionId: "220012", sequenceNo: 2 },
    ],
    featureVersions: [
      {
        featureVersionId: "220011",
        featureCode: "IP_RISK_LEVEL",
        resultValueType: "STRING",
        handlerCode: "IpRiskLevelFeature",
        implementationVersion: "1.0.0",
        implementationHash: hashOf("a"),
        status: "ACTIVE",
      },
      {
        featureVersionId: "220012",
        featureCode: "VELOCITY_SCORE",
        resultValueType: "NUMBER",
        handlerCode: "VelocityScoreFeature",
        implementationVersion: "1.0.0",
        implementationHash: hashOf("b"),
        status: "ACTIVE",
      },
    ],
    featureDependencies: [
      {
        dependencyId: "220021",
        featureVersionId: "220012",
        dependencyFeatureVersionId: "220011",
        sequenceNo: 1,
        requiredFlag: true,
      },
    ],
    pointRuleSets: [
      { bindingId: "230101", pointVersionId: "210011", ruleSetVersionId: "230011", sequenceNo: 1 },
    ],
    ruleSetVersions: [
      {
        ruleSetVersionId: "230011",
        decisionPointId: "210001",
        ruleSetCode: "PAYMENT_NETWORK",
        status: "FROZEN",
      },
    ],
    rules: [
      { ruleId: "230021", ruleSetVersionId: "230011", ruleCode: "HIGH_RISK_NETWORK", priorityNo: 1, enabledFlag: true },
    ],
    nodes: [
      { nodeId: "230031", ruleId: "230021", parentNodeId: null, nodeType: "GROUP", groupOperator: "AND", comparisonOperator: null, sequenceNo: 1 },
      { nodeId: "230032", ruleId: "230021", parentNodeId: "230031", nodeType: "PREDICATE", groupOperator: null, comparisonOperator: "EQ", sequenceNo: 1 },
      { nodeId: "230033", ruleId: "230021", parentNodeId: "230031", nodeType: "GROUP", groupOperator: "OR", comparisonOperator: null, sequenceNo: 2 },
      { nodeId: "230034", ruleId: "230021", parentNodeId: "230033", nodeType: "PREDICATE", groupOperator: null, comparisonOperator: "IN", sequenceNo: 1 },
      { nodeId: "230035", ruleId: "230021", parentNodeId: "230033", nodeType: "GROUP", groupOperator: "NOT", comparisonOperator: null, sequenceNo: 2 },
      { nodeId: "230036", ruleId: "230021", parentNodeId: "230035", nodeType: "PREDICATE", groupOperator: null, comparisonOperator: "IS_NULL", sequenceNo: 1 },
      { nodeId: "230037", ruleId: "230021", parentNodeId: "230031", nodeType: "PREDICATE", groupOperator: null, comparisonOperator: "BETWEEN", sequenceNo: 3 },
    ],
    operands: [
      { operandId: "231001", nodeId: "230032", operandRole: "LEFT", valueSequence: 1, sourceType: "FEATURE", featureVersionId: "220011", inputFieldId: null, valueType: "STRING" },
      { operandId: "231002", nodeId: "230032", operandRole: "RIGHT", valueSequence: 1, sourceType: "LITERAL", featureVersionId: null, inputFieldId: null, valueType: "STRING", stringValue: "HIGH" },
      { operandId: "231003", nodeId: "230034", operandRole: "LEFT", valueSequence: 1, sourceType: "CONTEXT", featureVersionId: null, inputFieldId: "210021", valueType: "STRING" },
      { operandId: "231004", nodeId: "230034", operandRole: "RIGHT", valueSequence: 1, sourceType: "LITERAL", featureVersionId: null, inputFieldId: null, valueType: "STRING", stringValue: "203.0.113.9" },
      { operandId: "231005", nodeId: "230034", operandRole: "RIGHT", valueSequence: 2, sourceType: "LITERAL", featureVersionId: null, inputFieldId: null, valueType: "STRING", stringValue: "198.51.100.7" },
      { operandId: "231006", nodeId: "230036", operandRole: "LEFT", valueSequence: 1, sourceType: "CONTEXT", featureVersionId: null, inputFieldId: "210022", valueType: "STRING" },
      { operandId: "231007", nodeId: "230037", operandRole: "LEFT", valueSequence: 1, sourceType: "FEATURE", featureVersionId: "220012", inputFieldId: null, valueType: "NUMBER" },
      { operandId: "231008", nodeId: "230037", operandRole: "RIGHT", valueSequence: 1, sourceType: "LITERAL", featureVersionId: null, inputFieldId: null, valueType: "NUMBER", numberValue: "10.00000000" },
      { operandId: "231009", nodeId: "230037", operandRole: "RIGHT", valueSequence: 2, sourceType: "LITERAL", featureVersionId: null, inputFieldId: null, valueType: "NUMBER", numberValue: "100.00000000" },
    ],
    actionVersions: [
      {
        actionVersionId: "240011",
        actionCode: "COOLDOWN_SUBJECT",
        handlerCode: "CooldownSubjectAction",
        implementationVersion: "1.0.0",
        implementationHash: hashOf("c"),
        terminalFlag: false,
        decisionCode: null,
        shadowSupportedFlag: true,
        status: "ACTIVE",
      },
      {
        actionVersionId: "240012",
        actionCode: "ADD_LIST_ENTRY",
        handlerCode: "AddListEntryAction",
        implementationVersion: "1.0.0",
        implementationHash: hashOf("d"),
        terminalFlag: false,
        decisionCode: null,
        shadowSupportedFlag: true,
        status: "ACTIVE",
      },
      {
        actionVersionId: "240013",
        actionCode: "ALLOW_PAYMENT",
        handlerCode: "AllowPaymentAction",
        implementationVersion: "1.0.0",
        implementationHash: hashOf("e"),
        terminalFlag: true,
        decisionCode: "ALLOW",
        shadowSupportedFlag: true,
        status: "ACTIVE",
      },
    ],
    parameterDefinitions: [
      { parameterDefinitionId: "241001", actionVersionId: "240011", parameterCode: "duration_ms", valueType: "DURATION", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
      { parameterDefinitionId: "241002", actionVersionId: "240011", parameterCode: "subject_ref", valueType: "STRING", requiredFlag: true, allowedSourceType: "CONTEXT", targetParameterFlag: true },
      { parameterDefinitionId: "241003", actionVersionId: "240012", parameterCode: "list_value", valueType: "STRING", requiredFlag: true, allowedSourceType: "CONTEXT", targetParameterFlag: true },
      { parameterDefinitionId: "241004", actionVersionId: "240012", parameterCode: "list_type", valueType: "STRING", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    ],
    ruleActions: [
      { ruleActionId: "242001", ruleId: "230021", actionVersionId: "240011", sequenceNo: 1 },
      { ruleActionId: "242002", ruleId: "230021", actionVersionId: "240012", sequenceNo: 2 },
      { ruleActionId: "242003", ruleId: "230021", actionVersionId: "240013", sequenceNo: 3 },
    ],
    actionParameters: [
      { parameterId: "243001", ruleActionId: "242001", parameterDefinitionId: "241001", valueSequence: 1, sourceType: "LITERAL", durationValue: "1800000" },
      { parameterId: "243002", ruleActionId: "242001", parameterDefinitionId: "241002", valueSequence: 1, sourceType: "CONTEXT", inputFieldId: "210022" },
      { parameterId: "243003", ruleActionId: "242002", parameterDefinitionId: "241003", valueSequence: 1, sourceType: "CONTEXT", inputFieldId: "210021" },
      { parameterId: "243004", ruleActionId: "242002", parameterDefinitionId: "241004", valueSequence: 1, sourceType: "LITERAL", stringValue: "IP" },
    ],
    obligations: [
      { obligationId: "250001", pointVersionId: "210011", triggerDecision: "ALLOW", obligationType: "RESERVE_LIMIT_GROUP", actionVersionId: null, sequenceNo: 1, failurePolicy: "FAIL_CLOSED", enabledFlag: true },
    ],
    limitVersions: [
      {
        limitVersionId: "250011",
        limitCode: "CONSUMER_DAILY_AMOUNT",
        limitName: "Consumer Daily Amount",
        versionNo: 1,
        subjectType: "CONSUMER",
        metricType: "AMOUNT",
        windowUnit: "DAY",
        windowSize: 1,
        amountLimit: "1000.00000000",
        countLimit: null,
        currencyMode: "PER_CURRENCY",
        fixedCurrency: null,
        contentHash: "178a023a92ab991090c6e7928337ff3b859bff675c3a925d8cc3763626576995",
        status: "FROZEN",
        frozenBy: "900001",
        frozenTime: "2026-08-27T09:00:00.000000Z",
      },
    ],
    limitBindings: [
      {
        bindingId: "250021", obligationId: "250001", limitVersionId: "250011", sequenceNo: 1,
        subjectSourceType: "CONTEXT", subjectFeatureVersionId: null, subjectInputFieldId: "210022",
        amountSourceType: "FEATURE", amountFeatureVersionId: "220012", amountInputFieldId: null,
      },
    ],
    handlerDeployments: [
      { handlerType: "FEATURE", handlerCode: "IpRiskLevelFeature", implementationVersion: "1.0.0", implementationHash: hashOf("a"), deploymentStatus: "DEPLOYED" },
      { handlerType: "FEATURE", handlerCode: "VelocityScoreFeature", implementationVersion: "1.0.0", implementationHash: hashOf("b"), deploymentStatus: "DEPLOYED" },
      { handlerType: "ACTION", handlerCode: "CooldownSubjectAction", implementationVersion: "1.0.0", implementationHash: hashOf("c"), deploymentStatus: "DEPLOYED" },
      { handlerType: "ACTION", handlerCode: "AddListEntryAction", implementationVersion: "1.0.0", implementationHash: hashOf("d"), deploymentStatus: "DEPLOYED" },
      { handlerType: "ACTION", handlerCode: "AllowPaymentAction", implementationVersion: "1.0.0", implementationHash: hashOf("e"), deploymentStatus: "DEPLOYED" },
    ],
    crossRowRows: emptyCrossRowRows(),
  };
}

function addValidSettlementRuntimeFacts(rows) {
  rows.action_version.push({
    action_version_id: "240101", action_code: "CREATE_SETTLEMENT_CONTROL",
    action_category: "SETTLEMENT", terminal_flag: false, status: "ACTIVE",
    shadow_supported_flag: true, merge_policy: "FIRST",
  });
  rows.decision_point_rule_set.push({
    binding_id: "211101", point_version_id: "210111", rule_set_version_id: "230011",
  });
  rows.risk_rule.push({ rule_id: "230021", rule_set_version_id: "230011" });
  rows.rule_action.push({ rule_action_id: "242101", rule_id: "230021", action_version_id: "240101" });
  rows.decision_evaluation.push({
    evaluation_id: "310101", evaluation_mode: "PRIMARY", technical_status: "SUCCEEDED",
    decision_point_id: "210101", point_version_id: "210111", release_id: "260002",
    evaluation_time: "2026-08-28T10:00:00.000000Z", input_snapshot_hash: hashOf("2"),
    proposed_decision: "ALLOW", business_decision: "DENY", default_decision_used_flag: false,
    shadow_run_id: null, baseline_evaluation_id: null,
  });
  const targetKey = "CLIENT:200001:RULE_ACTION:242101:PAYOUT:560001";
  rows.evaluation_action_intent.push({
    intent_id: "314101", evaluation_id: "310101", source_type: "RULE",
    rule_action_id: "242101", obligation_id: null, action_version_id: "240101",
    resolved_target_type: "SETTLEMENT_CONTROL", resolved_target_key: targetKey,
    intent_sequence: 1, merged_effect_id: "315101", status: "MERGED",
  });
  rows.action_effect.push({
    effect_id: "315101", evaluation_id: "310101", action_version_id: "240101",
    effect_mode: "REAL", target_type: "SETTLEMENT_CONTROL", target_key: targetKey,
    merge_policy: "FIRST", effect_status: "APPLIED", execution_type: "LOCAL_SYNC",
    execution_status: "SUCCEEDED", idempotency_key: "risk-action-effect:v1:315101",
  });
  rows.settlement_risk_assessment.push({
    assessment_id: "321101", evaluation_id: "310101",
    checkpoint_type: "PAYOUT_CREATION", input_hash: hashOf("2"),
    eligibility_decision: "RESTRICT",
  });
  rows.settlement_control.push({
    control_id: "360101", role_type: "CLIENT", role_id: "200001",
    control_type: "MIN_SECURITY_DEPOSIT", source_action_effect_id: "315101",
    control_instance_key: "RULE_ACTION:242101:PAYOUT:560001",
    required_currency: "USD", status: "RELEASED", active_slot: null, version: 2,
  });
  rows.settlement_control_scope.push({
    scope_id: "361101", control_id: "360101", operating_entity_id: "210001",
    currency: "USD", bank_country: "AE", scope_hash: hashOf("3"),
  });
  rows.settlement_control_state_change.push(
    {
      change_id: "362101", control_id: "360101", aggregate_version: 1,
      from_status: null, to_status: "ACTIVE", trigger_type: "ACTION_EFFECT",
      trigger_id: "315101",
    },
    {
      change_id: "362102", control_id: "360101", aggregate_version: 2,
      from_status: "ACTIVE", to_status: "RELEASED", trigger_type: "OPERATION_COMMAND",
      trigger_id: "risk-command:900101",
    },
  );
  rows.compensation_event.push(
    { id: "370101", event_key: "risk:settlement-control-change:362101", event_type: "PUBLISH_SETTLEMENT_CONTROL_EVENT", sub_event_type: "SettlementControlChanged" },
    { id: "370102", event_key: "risk:settlement-control-change:362102", event_type: "PUBLISH_SETTLEMENT_CONTROL_EVENT", sub_event_type: "SettlementControlChanged" },
  );
  return rows;
}

function buildValidSettlementDecisionFixture() {
  const fixture = buildValidDecisionFixture();
  fixture.decisionPoint = {
    decisionPointId: "210101", pointCode: "PAYOUT_CREATION",
    businessDomain: "SETTLEMENT", status: "ACTIVE",
  };
  fixture.pointVersion = {
    pointVersionId: "210111", decisionPointId: "210101",
    defaultDecision: "DENY", failureStrategy: "FAIL_CLOSED",
  };
  fixture.release.decisionPointId = "210101";
  fixture.release.pointVersionId = "210111";
  fixture.release.contentHash = SETTLEMENT_RELEASE_CONTENT_HASH;
  fixture.inputFields.forEach((row) => { row.pointVersionId = "210111"; });
  fixture.pointFeatures.forEach((row) => { row.pointVersionId = "210111"; });
  fixture.pointRuleSets = [{
    bindingId: "230101", pointVersionId: "210111", ruleSetVersionId: "230011", sequenceNo: 1,
  }];
  fixture.ruleSetVersions[0].decisionPointId = "210101";
  fixture.rules[0].reasonCode = "SETTLEMENT_POLICY";
  fixture.actionVersions = [
    {
      actionVersionId: "240101", actionCode: "CREATE_SETTLEMENT_CONTROL",
      actionCategory: "SETTLEMENT", targetType: "SETTLEMENT_CONTROL",
      handlerCode: "CreateSettlementControlAction", implementationVersion: "1.0.0",
      implementationHash: hashOf("c"), terminalFlag: false, decisionCode: null,
      mergePolicy: "FIRST", shadowSupportedFlag: true, status: "ACTIVE",
    },
    {
      actionVersionId: "240102", actionCode: "DENY_SETTLEMENT",
      actionCategory: "DECISION", targetType: null,
      handlerCode: "DenySettlementAction", implementationVersion: "1.0.0",
      implementationHash: hashOf("d"), terminalFlag: true, decisionCode: "DENY",
      mergePolicy: "FIRST", shadowSupportedFlag: true, status: "ACTIVE",
    },
    {
      actionVersionId: "240103", actionCode: "RECORD_SETTLEMENT_SCORE",
      actionCategory: "ASSESSMENT", targetType: "CLIENT",
      handlerCode: "RecordSettlementScoreAction", implementationVersion: "1.0.0",
      implementationHash: hashOf("e"), terminalFlag: false, decisionCode: null,
      mergePolicy: "FIRST", shadowSupportedFlag: true, status: "ACTIVE",
    },
  ];
  fixture.parameterDefinitions = [
    { parameterDefinitionId: "241101", actionVersionId: "240101", parameterCode: "ROLE_TYPE", valueType: "STRING", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: true },
    { parameterDefinitionId: "241102", actionVersionId: "240101", parameterCode: "CONTROL_TYPE", valueType: "STRING", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241103", actionVersionId: "240101", parameterCode: "REASON_CODE", valueType: "STRING", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241111", actionVersionId: "240101", parameterCode: "SCOPE_OPERATING_ENTITY_KEY", valueType: "STRING", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241112", actionVersionId: "240101", parameterCode: "SCOPE_CURRENCY_KEY", valueType: "STRING", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241113", actionVersionId: "240101", parameterCode: "SCOPE_BANK_COUNTRY_KEY", valueType: "STRING", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241120", actionVersionId: "240103", parameterCode: "GENERIC_NUMBER", valueType: "NUMBER", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241121", actionVersionId: "240103", parameterCode: "GENERIC_STRING", valueType: "STRING", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241122", actionVersionId: "240103", parameterCode: "GENERIC_BOOLEAN", valueType: "BOOLEAN", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241123", actionVersionId: "240103", parameterCode: "GENERIC_DATETIME", valueType: "DATETIME", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
    { parameterDefinitionId: "241124", actionVersionId: "240103", parameterCode: "GENERIC_DURATION", valueType: "DURATION", requiredFlag: true, allowedSourceType: "LITERAL", targetParameterFlag: false },
  ];
  fixture.ruleActions = [
    { ruleActionId: "242101", ruleId: "230021", actionVersionId: "240101", sequenceNo: 1 },
    { ruleActionId: "242103", ruleId: "230021", actionVersionId: "240103", sequenceNo: 2 },
    { ruleActionId: "242102", ruleId: "230021", actionVersionId: "240102", sequenceNo: 3 },
  ];
  const literal = (parameterId, parameterDefinitionId, valueSequence, stringValue) => ({
    parameterId, ruleActionId: "242101", parameterDefinitionId, valueSequence,
    sourceType: "LITERAL", stringValue,
  });
  fixture.actionParameters = [
    literal("243101", "241101", 1, "CLIENT"),
    literal("243102", "241102", 1, "MIN_SECURITY_DEPOSIT"),
    literal("243103", "241103", 1, "DISPUTE_EXPOSURE"),
    literal("243111", "241111", 1, "210001"),
    literal("243112", "241112", 1, "USD"),
    literal("243113", "241113", 1, "AE"),
    literal("243121", "241111", 2, "210002"),
    literal("243122", "241112", 2, "USD"),
    literal("243123", "241113", 2, "*"),
    {
      parameterId: "243130", ruleActionId: "242103", parameterDefinitionId: "241120",
      valueSequence: 1, sourceType: "LITERAL", numberValue: "10.00000000",
    },
    {
      parameterId: "243131", ruleActionId: "242103", parameterDefinitionId: "241121",
      valueSequence: 1, sourceType: "LITERAL", stringValue: "SETTLEMENT_SCORE",
    },
    {
      parameterId: "243132", ruleActionId: "242103", parameterDefinitionId: "241122",
      valueSequence: 1, sourceType: "LITERAL", booleanValue: true,
    },
    {
      parameterId: "243133", ruleActionId: "242103", parameterDefinitionId: "241123",
      valueSequence: 1, sourceType: "LITERAL", datetimeValue: "2026-08-28T10:00:00.000000Z",
    },
    {
      parameterId: "243134", ruleActionId: "242103", parameterDefinitionId: "241124",
      valueSequence: 1, sourceType: "LITERAL", durationValue: "1800000",
    },
  ];
  fixture.obligations = [{
    obligationId: "250101", pointVersionId: "210111", triggerDecision: "ALLOW",
    obligationType: "CHECK_SETTLEMENT_ELIGIBILITY", actionVersionId: null,
    sequenceNo: 1, failurePolicy: "FAIL_CLOSED", enabledFlag: true,
  }];
  fixture.limitVersions = [];
  fixture.limitBindings = [];
  fixture.handlerDeployments = [
    ...fixture.handlerDeployments.filter((row) => row.handlerType === "FEATURE"),
    { handlerType: "ACTION", handlerCode: "CreateSettlementControlAction", implementationVersion: "1.0.0", implementationHash: hashOf("c"), deploymentStatus: "DEPLOYED" },
    { handlerType: "ACTION", handlerCode: "DenySettlementAction", implementationVersion: "1.0.0", implementationHash: hashOf("d"), deploymentStatus: "DEPLOYED" },
    { handlerType: "ACTION", handlerCode: "RecordSettlementScoreAction", implementationVersion: "1.0.0", implementationHash: hashOf("e"), deploymentStatus: "DEPLOYED" },
  ];
  addValidSettlementRuntimeFacts(fixture.crossRowRows);
  return fixture;
}

const SETTLEMENT_SCOPE_DEFINITION_IDS = new Set(["241111", "241112", "241113"]);

function settlementParameterIdentityRows(fixture) {
  return fixture.actionParameters
    .filter((parameter) => parameter.ruleActionId === "242101")
    .map((parameter) => ({
    ruleActionId: parameter.ruleActionId,
    parameterDefinitionId: parameter.parameterDefinitionId,
    valueSequence: parameter.valueSequence,
    sourceType: parameter.sourceType,
    featureVersionId: parameter.featureVersionId ?? null,
    inputFieldId: parameter.inputFieldId ?? null,
    stringValue: parameter.stringValue ?? null,
    numberValue: parameter.numberValue ?? null,
    booleanValue: parameter.booleanValue ?? null,
    datetimeValue: parameter.datetimeValue ?? null,
    durationValue: parameter.durationValue ?? null,
    }));
}

function buildSingleScopeSettlementDecisionFixture() {
  const fixture = buildValidSettlementDecisionFixture();
  fixture.actionParameters = fixture.actionParameters.filter((parameter) => (
    !SETTLEMENT_SCOPE_DEFINITION_IDS.has(parameter.parameterDefinitionId)
      || parameter.valueSequence === 1
  ));
  fixture.release.contentHash = SETTLEMENT_SINGLE_SCOPE_CONTENT_HASH;
  return fixture;
}

function buildContextScopeSettlementDecisionFixture() {
  const fixture = buildSingleScopeSettlementDecisionFixture();
  for (const definition of fixture.parameterDefinitions) {
    if (SETTLEMENT_SCOPE_DEFINITION_IDS.has(definition.parameterDefinitionId)) {
      definition.allowedSourceType = "CONTEXT";
    }
  }
  for (const parameter of fixture.actionParameters) {
    if (!SETTLEMENT_SCOPE_DEFINITION_IDS.has(parameter.parameterDefinitionId)) continue;
    parameter.sourceType = "CONTEXT";
    parameter.inputFieldId = "210021";
    parameter.stringValue = null;
  }
  fixture.release.contentHash = SETTLEMENT_CONTEXT_SCOPE_CONTENT_HASH;
  return fixture;
}

function buildValidTransitionFixture() {
  const candidateBefore = {
    releaseId: "260002", decisionPointId: "210001", pointVersionId: "210011",
    environmentCode: "PRODUCTION", contentHash: hashOf("7"), status: "APPROVED", activeSlot: null, version: 5,
  };
  const candidateAfter = { ...candidateBefore, status: "ACTIVE", activeSlot: 1, version: 6 };
  const previousBefore = {
    releaseId: "260001", decisionPointId: "210001", pointVersionId: "210010",
    environmentCode: "PRODUCTION", contentHash: hashOf("8"), status: "ACTIVE", activeSlot: 1, version: 7,
  };
  const previousAfter = { ...previousBefore, status: "SUPERSEDED", activeSlot: null, version: 8 };
  const candidateStates = [
    [1, null, "DRAFT"],
    [2, "DRAFT", "VALIDATING"],
    [3, "VALIDATING", "SHADOWING"],
    [4, "SHADOWING", "REVIEW_PENDING"],
    [5, "REVIEW_PENDING", "APPROVED"],
    [6, "APPROVED", "ACTIVE"],
  ].map(([aggregateVersion, fromStatus, toStatus]) => ({
    auditEventId: `2801${String(aggregateVersion).padStart(2, "0")}`,
    eventType: "STATE_CHANGE",
    eventTime: aggregateVersion >= 5
      ? "2026-08-27T10:01:00.000000Z"
      : `2026-08-27T09:00:0${aggregateVersion}.000000Z`,
    releaseId: "260002",
    aggregateVersion,
    fromStatus,
    toStatus,
    triggerType: aggregateVersion >= 5 ? "APPROVAL" : "COMMAND",
    triggerId: aggregateVersion >= 5 ? "approval-2" : `state-${aggregateVersion}`,
  }));
  const staticChecks = CONFIG_CHECK_CODES.map((checkCode, index) => ({
    auditEventId: `2802${String(index + 1).padStart(2, "0")}`,
    eventType: "STATIC_CHECK",
    eventTime: `2026-08-27T09:00:02.${String(index + 1).padStart(6, "0")}Z`,
    releaseId: "260002",
    checkCode,
    checkStatus: "PASSED",
    severity: "ERROR",
  }));
  const approvals = [
    { auditEventId: "280301", eventType: "APPROVAL", eventTime: "2026-08-27T10:00:00.000000Z", releaseId: "260002", shadowRunId: "270001", approvalRequestId: "approval-1", approvalStatus: "APPROVED", reviewerId: "900001" },
    { auditEventId: "280302", eventType: "APPROVAL", eventTime: "2026-08-27T10:01:00.000000Z", releaseId: "260002", shadowRunId: "270001", approvalRequestId: "approval-2", approvalStatus: "APPROVED", reviewerId: "900002" },
  ];
  const candidateStateEvent = candidateStates.at(-1);
  const previousStateEvent = {
    auditEventId: "280401",
    eventType: "STATE_CHANGE", eventTime: "2026-08-27T10:01:00.000000Z",
    releaseId: "260001", aggregateVersion: 8,
    fromStatus: "ACTIVE", toStatus: "SUPERSEDED", triggerType: "APPROVAL", triggerId: "approval-2",
  };
  return {
    candidateBefore,
    candidateAfter,
    relatedReleasesBefore: [previousBefore],
    relatedReleasesAfter: [candidateAfter, previousAfter],
    auditEvents: [...staticChecks, ...approvals, ...candidateStates, previousStateEvent],
    shadowRuns: [{
      shadowRunId: "270001",
      baselineReleaseId: "260001",
      candidateReleaseId: "260002",
      decisionPointId: "210001",
      environmentCode: "PRODUCTION",
      status: "COMPLETED",
      completedTime: "2026-08-27T09:00:03.500000Z",
      checkStatus: "PASSED",
    }],
    selectedShadowRunId: "270001",
    activation: {
      candidateBefore,
      candidateAfter,
      previousBefore,
      previousAfter,
      candidateStateEvent,
      previousStateEvent,
    },
  };
}

function buildInitialTransitionFixture() {
  const candidateAfter = {
    releaseId: "260010", decisionPointId: "210001", pointVersionId: "210011",
    environmentCode: "PRODUCTION", contentHash: hashOf("9"), status: "DRAFT", activeSlot: null, version: 1,
  };
  return {
    candidateBefore: null,
    candidateAfter,
    relatedReleasesBefore: [],
    relatedReleasesAfter: [candidateAfter],
    auditEvents: [{
      auditEventId: "280501", eventType: "STATE_CHANGE",
      eventTime: "2026-08-27T08:00:00.000000Z", releaseId: "260010",
      aggregateVersion: 1, fromStatus: null, toStatus: "DRAFT",
      triggerType: "COMMAND", triggerId: "create-260010",
    }],
    shadowRuns: [],
    selectedShadowRunId: null,
    activation: null,
  };
}

function errorsForCode(errors, checkCode) {
  return errors.filter((error) => error.check_code === checkCode);
}

function assertRejectedBy(errors, checkCode, errorCode) {
  assert.ok(errorsForCode(errors, checkCode).length > 0, `缺少 ${checkCode} 错误：${JSON.stringify(errors)}`);
  if (errorCode) {
    assert.ok(errors.some((error) => error.check_code === checkCode && error.error_code === errorCode), `缺少 ${checkCode}/${errorCode}：${JSON.stringify(errors)}`);
  }
}

function assertNotRejectedBy(errors, checkCode, errorCode) {
  assert.ok(
    !errors.some((error) => error.check_code === checkCode && error.error_code === errorCode),
    `不应出现 ${checkCode}/${errorCode}：${JSON.stringify(errors)}`,
  );
}

test("Risk contract resolves a named PDManer index field reference", () => {
  assert.deepEqual(
    indexFields(
      { fields: [{ id: "FIELD-100", defKey: "feature_code" }] },
      { fields: [{ fieldDefKey: "feature_code" }] },
    ),
    ["feature_code"],
  );
});

test("Risk Decision Engine Manifest collapses catalog roots and typed Literal rows", () => {
  const tables = manifestTablesByKey();
  const contracts = [
    {
      table: "feature_version",
      fields: ["feature_version_id", "feature_code", "feature_name", "business_category", "version_no"],
      absent: ["feature_id"],
      unique: ["feature_code", "version_no"],
    },
    {
      table: "action_version",
      fields: ["action_version_id", "action_code", "action_name", "action_category", "version_no", "implementation_hash"],
      absent: ["action_id"],
      unique: ["action_code", "version_no"],
    },
    {
      table: "rule_set_version",
      fields: ["rule_set_version_id", "decision_point_id", "rule_set_code", "rule_set_name", "version_no"],
      absent: ["rule_set_id"],
      unique: ["decision_point_id", "rule_set_code", "version_no"],
    },
    {
      table: "rule_operand",
      fields: [
        "operand_id", "node_id", "operand_role", "value_sequence", "source_type",
        "feature_version_id", "input_field_id", "value_type", "string_value", "number_value",
        "boolean_value", "datetime_value", "duration_value", "create_time",
      ],
      absent: ["operand_value_id"],
      unique: ["node_id", "operand_role", "value_sequence"],
    },
  ];

  for (const contract of contracts) {
    const table = tables.get(contract.table);
    assert.ok(table, `缺少目标 Manifest 表：${contract.table}`);
    const fields = manifestFieldNames(table);
    for (const field of contract.fields) {
      assert.ok(fields.has(field), `${contract.table} Manifest 缺少目标字段：${field}`);
    }
    for (const field of contract.absent) {
      assert.ok(!fields.has(field), `${contract.table} Manifest 保留了已删除字段：${field}`);
    }
    assertManifestUniqueIndex(table, contract.unique);
  }

  for (const removedTable of ["feature_definition", "action_definition", "rule_set", "rule_operand_value"]) {
    assert.ok(!tables.has(removedTable), `Manifest 保留了已删除表：${removedTable}`);
  }
  assert.deepEqual(
    tables.get("rule_set_version")?.relations,
    [{ childField: "decision_point_id", parentTable: "decision_point", parentField: "decision_point_id", cardinality: "n:1", childTable: "rule_set_version" }],
  );

  const removedFields = new Set(["feature_id", "action_id", "rule_set_id", "operand_value_id"]);
  const removedTables = new Set(["feature_definition", "action_definition", "rule_set", "rule_operand_value"]);
  for (const table of DECISION_ENGINE_TABLES) {
    for (const relation of table.relations) {
      assert.ok(!removedFields.has(relation.childField), `Rule AST 保留了已删除关系字段：${table.defKey}.${relation.childField}`);
      assert.ok(!removedTables.has(relation.parentTable), `Rule AST 保留了已删除父表关系：${table.defKey} -> ${relation.parentTable}`);
    }
  }
});

test("Risk Decision Engine Policy constrains typed Operand source and sequence rows", () => {
  assert.equal(
    CHECK_CONTRACTS.ck_rule_operand_source,
    "(source_type='FEATURE' AND value_sequence=1 AND feature_version_id IS NOT NULL AND input_field_id IS NULL AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (source_type='CONTEXT' AND value_sequence=1 AND feature_version_id IS NULL AND input_field_id IS NOT NULL AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (source_type='LITERAL' AND feature_version_id IS NULL AND input_field_id IS NULL)",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_rule_operand_value_type,
    "(source_type IN ('FEATURE','CONTEXT') AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (source_type='LITERAL' AND ((value_type='STRING' AND string_value IS NOT NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (value_type='NUMBER' AND string_value IS NULL AND number_value IS NOT NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (value_type='BOOLEAN' AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NOT NULL AND datetime_value IS NULL AND duration_value IS NULL) OR (value_type='DATETIME' AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NOT NULL AND duration_value IS NULL) OR (value_type='DURATION' AND string_value IS NULL AND number_value IS NULL AND boolean_value IS NULL AND datetime_value IS NULL AND duration_value IS NOT NULL)))",
  );
  assert.equal(CHECK_CONTRACTS.ck_rule_operand_sequence, "value_sequence>0");
  assert.deepEqual(CHECK_BINDINGS.ck_rule_operand_source, ["rule_operand"]);
  assert.deepEqual(CHECK_BINDINGS.ck_rule_operand_value_type, ["rule_operand"]);
  assert.deepEqual(CHECK_BINDINGS.ck_rule_operand_sequence, ["rule_operand"]);
  assert.equal(CHECK_CONTRACTS.ck_typed_literal, undefined);
});

test("Risk staged Manifest and Policy consolidate Release audit and Shadow evidence", () => {
  const decisionTables = manifestTablesByKey();
  const evaluationTables = new Map(EVALUATION_TABLES.map((table) => [table.defKey, table]));
  const audit = decisionTables.get("release_audit_event");
  const shadowRun = decisionTables.get("release_shadow_run");
  const evaluation = evaluationTables.get("decision_evaluation");

  assert.equal(RISK_TABLES.length, 71);
  assert.equal(DECISION_ENGINE_TABLES.length, 19);
  assert.equal(EVALUATION_TABLES.length, 8);

  assert.ok(audit, "缺少目标 Manifest 表：release_audit_event");
  for (const field of [
    "audit_event_id", "release_id", "event_type", "event_time", "check_code", "check_status",
    "severity", "error_code", "error_summary", "shadow_run_id", "approval_request_id",
    "approval_status", "reviewer_id", "review_comment", "from_status", "to_status",
    "reason_code", "trigger_type", "trigger_id", "actor_id", "aggregate_version",
  ]) {
    assert.ok(manifestFieldNames(audit).has(field), `release_audit_event Manifest 缺少目标字段：${field}`);
  }
  assert.equal(audit.audit, "immutable");
  assertManifestUniqueIndex(audit, ["release_id", "event_type", "check_code"]);
  assertManifestUniqueIndex(audit, ["release_id", "event_type", "aggregate_version"]);
  assertManifestUniqueIndex(audit, ["release_id", "event_type", "approval_request_id"]);
  assert.ok(audit.indexes.some((index) => !index.unique && JSON.stringify(index.fields) === JSON.stringify(["release_id", "event_time"])));
  assert.ok(audit.indexes.some((index) => !index.unique && JSON.stringify(index.fields) === JSON.stringify(["shadow_run_id", "event_time"])));
  assert.deepEqual(audit.relations.map(({ childField, parentTable, parentField }) => ({ childField, parentTable, parentField })), [
    { childField: "release_id", parentTable: "decision_release", parentField: "release_id" },
    { childField: "shadow_run_id", parentTable: "release_shadow_run", parentField: "shadow_run_id" },
  ]);

  assert.ok(shadowRun, "缺少目标 Manifest 表：release_shadow_run");
  for (const field of [
    "allow_to_review_count", "allow_to_deny_count", "deny_to_allow_count", "action_change_count",
    "feature_missing_count", "failed_count", "timed_out_count", "p95_millis", "p99_millis",
    "check_status", "version",
  ]) {
    assert.ok(manifestFieldNames(shadowRun).has(field), `release_shadow_run Manifest 缺少目标字段：${field}`);
  }
  assert.equal(shadowRun.fields.find((field) => field.defKey === "version")?.type, "BIGINT");

  assert.ok(evaluation, "缺少目标 Manifest 表：decision_evaluation");
  for (const field of [
    "shadow_run_id", "baseline_evaluation_id", "decision_changed_flag", "action_changed_flag",
    "comparison_severity", "compared_time",
  ]) {
    assert.ok(manifestFieldNames(evaluation).has(field), `decision_evaluation Manifest 缺少目标字段：${field}`);
  }
  assertManifestUniqueIndex(evaluation, ["shadow_run_id", "baseline_evaluation_id"]);
  assert.deepEqual(evaluation.relations.slice(-2).map(({ childField, parentTable, parentField }) => ({ childField, parentTable, parentField })), [
    { childField: "shadow_run_id", parentTable: "release_shadow_run", parentField: "shadow_run_id" },
    { childField: "baseline_evaluation_id", parentTable: "decision_evaluation", parentField: "evaluation_id" },
  ]);

  for (const removedTable of [
    "release_static_check", "release_approval", "release_state_change", "release_shadow_summary",
    "release_shadow_comparison",
  ]) {
    assert.ok(!decisionTables.has(removedTable), `Manifest 保留了已删除表：${removedTable}`);
  }
  assert.equal(
    CHECK_CONTRACTS.ck_release_audit_event,
    "(event_type='STATIC_CHECK' AND check_code IS NOT NULL AND check_status IS NOT NULL AND severity IS NOT NULL AND shadow_run_id IS NULL AND approval_request_id IS NULL AND approval_status IS NULL AND reviewer_id IS NULL AND review_comment IS NULL AND from_status IS NULL AND to_status IS NULL AND reason_code IS NULL AND trigger_type IS NULL AND trigger_id IS NULL AND actor_id IS NULL AND aggregate_version IS NULL) OR (event_type='APPROVAL' AND check_code IS NULL AND check_status IS NULL AND severity IS NULL AND error_code IS NULL AND error_summary IS NULL AND shadow_run_id IS NOT NULL AND approval_request_id IS NOT NULL AND approval_status IS NOT NULL AND reviewer_id IS NOT NULL AND from_status IS NULL AND to_status IS NULL AND reason_code IS NULL AND trigger_type IS NULL AND trigger_id IS NULL AND actor_id IS NULL AND aggregate_version IS NULL) OR (event_type='STATE_CHANGE' AND check_code IS NULL AND check_status IS NULL AND severity IS NULL AND error_code IS NULL AND error_summary IS NULL AND shadow_run_id IS NULL AND approval_request_id IS NULL AND approval_status IS NULL AND reviewer_id IS NULL AND review_comment IS NULL AND to_status IS NOT NULL AND trigger_type IS NOT NULL AND trigger_id IS NOT NULL AND aggregate_version IS NOT NULL AND ((aggregate_version=1 AND from_status IS NULL) OR (aggregate_version>1 AND from_status IS NOT NULL)))",
  );
  assert.ok(CHECK_CONTRACTS.ck_release_audit_event.includes("error_code IS NULL AND error_summary IS NULL"));
  assert.ok(CHECK_CONTRACTS.ck_release_audit_event.includes("aggregate_version IS NOT NULL"));
  assert.equal(
    CHECK_CONTRACTS.ck_shadow_run_completion,
    "(status='COMPLETED' AND completed_time IS NOT NULL AND allow_to_review_count IS NOT NULL AND allow_to_deny_count IS NOT NULL AND deny_to_allow_count IS NOT NULL AND action_change_count IS NOT NULL AND feature_missing_count IS NOT NULL AND failed_count IS NOT NULL AND timed_out_count IS NOT NULL AND p95_millis IS NOT NULL AND p99_millis IS NOT NULL AND check_status IS NOT NULL) OR (status IN ('CREATED','RUNNING') AND completed_time IS NULL AND allow_to_review_count IS NULL AND allow_to_deny_count IS NULL AND deny_to_allow_count IS NULL AND action_change_count IS NULL AND feature_missing_count IS NULL AND failed_count IS NULL AND timed_out_count IS NULL AND p95_millis IS NULL AND p99_millis IS NULL AND check_status IS NULL) OR (status IN ('FAILED','CANCELLED') AND completed_time IS NOT NULL AND allow_to_review_count IS NULL AND allow_to_deny_count IS NULL AND deny_to_allow_count IS NULL AND action_change_count IS NULL AND feature_missing_count IS NULL AND failed_count IS NULL AND timed_out_count IS NULL AND p95_millis IS NULL AND p99_millis IS NULL AND check_status IS NULL)",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_evaluation_shadow_comparison,
    "(evaluation_mode='SHADOW' AND shadow_run_id IS NOT NULL AND baseline_evaluation_id IS NOT NULL AND decision_changed_flag IS NOT NULL AND action_changed_flag IS NOT NULL AND comparison_severity IS NOT NULL AND compared_time IS NOT NULL) OR (evaluation_mode IN ('PRIMARY','REPLAY') AND shadow_run_id IS NULL AND baseline_evaluation_id IS NULL AND decision_changed_flag IS NULL AND action_changed_flag IS NULL AND comparison_severity IS NULL AND compared_time IS NULL)",
  );
  assert.deepEqual(CHECK_BINDINGS.ck_release_audit_event, ["release_audit_event"]);
  assert.deepEqual(CHECK_BINDINGS.ck_shadow_run_completion, ["release_shadow_run"]);
  assert.deepEqual(CHECK_BINDINGS.ck_evaluation_shadow_comparison, ["decision_evaluation"]);
});

test("Risk staged Manifest and Policy consolidate evaluation traces and Action execution", () => {
  const evaluationTables = new Map(EVALUATION_TABLES.map((table) => [table.defKey, table]));
  const trace = evaluationTables.get("evaluation_rule_trace");
  const effect = evaluationTables.get("action_effect");

  assert.equal(RISK_TABLES.length, 71);
  assert.equal(DECISION_ENGINE_TABLES.length, 19);
  assert.equal(EVALUATION_TABLES.length, 8);

  assert.ok(trace, "缺少目标 Manifest 表：evaluation_rule_trace");
  assert.equal(trace.audit, "immutable");
  assert.equal(trace.fields.find((field) => field.defKey === "trace_id")?.type, "BIGINT");
  for (const field of [
    "trace_id", "evaluation_id", "trace_type", "rule_set_version_id", "rule_id", "node_id",
    "execution_sequence", "result_value", "terminal_flag", "elapsed_millis", "failure_code",
    "rule_result_slot", "predicate_result_slot", "create_time",
  ]) {
    assert.ok(manifestFieldNames(trace).has(field), `evaluation_rule_trace Manifest 缺少目标字段：${field}`);
  }
  assertManifestUniqueIndex(trace, ["evaluation_id", "rule_id", "rule_result_slot"]);
  assertManifestUniqueIndex(trace, ["evaluation_id", "node_id", "predicate_result_slot"]);
  assertManifestUniqueIndex(trace, ["evaluation_id", "trace_type", "execution_sequence"]);
  assert.deepEqual(trace.relations.map(({ childField, parentTable, parentField }) => ({ childField, parentTable, parentField })), [
    { childField: "evaluation_id", parentTable: "decision_evaluation", parentField: "evaluation_id" },
    { childField: "rule_set_version_id", parentTable: "rule_set_version", parentField: "rule_set_version_id" },
    { childField: "rule_id", parentTable: "risk_rule", parentField: "rule_id" },
    { childField: "node_id", parentTable: "rule_node", parentField: "node_id" },
  ]);

  assert.ok(effect, "缺少目标 Manifest 表：action_effect");
  assert.equal(effect.fields.find((field) => field.defKey === "effect_id")?.type, "BIGINT");
  for (const field of [
    "effect_status", "idempotency_key", "execution_type", "execution_status", "attempt_count",
    "started_time", "completed_time", "last_error_code", "last_error_summary",
  ]) {
    assert.ok(manifestFieldNames(effect).has(field), `action_effect Manifest 缺少目标字段：${field}`);
  }
  assert.ok(!manifestFieldNames(effect).has("status"), "action_effect Manifest 保留了混合生命周期 status");
  assertManifestUniqueIndex(effect, ["idempotency_key"]);

  for (const removedTable of ["evaluation_rule_result", "evaluation_predicate_result", "action_execution"]) {
    assert.ok(!evaluationTables.has(removedTable), `Manifest 保留了已删除表：${removedTable}`);
  }

  assert.equal(
    CHECK_CONTRACTS.ck_evaluation_rule_trace,
    "(trace_type='RULE' AND rule_set_version_id IS NOT NULL AND rule_id IS NOT NULL AND node_id IS NULL AND result_value IS NOT NULL AND terminal_flag IS NOT NULL AND elapsed_millis IS NOT NULL AND failure_code IS NULL AND rule_result_slot=1 AND predicate_result_slot IS NULL) OR (trace_type='PREDICATE' AND rule_set_version_id IS NULL AND rule_id IS NOT NULL AND node_id IS NOT NULL AND result_value IS NOT NULL AND terminal_flag IS NULL AND elapsed_millis IS NULL AND failure_code IS NULL AND rule_result_slot IS NULL AND predicate_result_slot=1) OR (trace_type='PREDICATE' AND rule_set_version_id IS NULL AND rule_id IS NOT NULL AND node_id IS NOT NULL AND result_value IS NULL AND terminal_flag IS NULL AND elapsed_millis IS NULL AND failure_code IS NOT NULL AND rule_result_slot IS NULL AND predicate_result_slot=1)",
  );
  const effectExecutionContract = CHECK_CONTRACTS.ck_action_effect_execution;
  assert.ok(effectExecutionContract.startsWith(
    "(effect_mode='SIMULATED' AND effect_status='SIMULATED' AND idempotency_key IS NULL",
  ));
  assert.equal(
    effectExecutionContract.match(/idempotency_key=CONCAT\('risk-action-effect:v1:',effect_hash\)/g)?.length,
    5,
    "每个 REAL Effect 状态都必须使用 effect_hash 派生的幂等键",
  );
  for (const executionStatus of ["INIT", "PROCESSING", "ERROR", "SUCCEEDED", "FAILED"]) {
    assert.ok(effectExecutionContract.includes(`execution_status='${executionStatus}'`));
  }
  assert.deepEqual(CHECK_BINDINGS.ck_evaluation_rule_trace, ["evaluation_rule_trace"]);
  assert.deepEqual(CHECK_BINDINGS.ck_action_effect_execution, ["action_effect"]);

  for (const table of RISK_TABLES) {
    for (const relation of table.relations.filter((relation) => /effect_id$/.test(relation.childField))) {
      assert.equal(relation.parentTable, "action_effect", `${table.defKey}.${relation.childField} 必须指向 action_effect`);
      assert.equal(relation.parentField, "effect_id", `${table.defKey}.${relation.childField} 必须指向 action_effect.effect_id`);
    }
  }
});

test("Risk Evaluation binds Action Intent to a typed source and freezes the decision matrix", () => {
  const evaluationTables = new Map(EVALUATION_TABLES.map((table) => [table.defKey, table]));
  const evaluation = evaluationTables.get("decision_evaluation");
  const intent = evaluationTables.get("evaluation_action_intent");

  assert.ok(evaluation, "缺少目标 Manifest 表：decision_evaluation");
  assert.ok(intent, "缺少目标 Manifest 表：evaluation_action_intent");
  assert.ok(!manifestFieldNames(intent).has("source_id"), "Action Intent 仍保留无外键的通用 source_id");
  assert.ok(manifestFieldNames(intent).has("obligation_id"), "Action Intent 缺少系统义务外键");
  assert.equal(intent.fields.find((field) => field.defKey === "obligation_id")?.notNull, false);
  assertManifestUniqueIndex(intent, ["evaluation_id", "rule_action_id", "intent_sequence"]);
  assertManifestUniqueIndex(intent, ["evaluation_id", "obligation_id", "intent_sequence"]);
  assertManifestAccessIndex(intent, "idx_intent_obligation", ["obligation_id"]);
  assert.deepEqual(
    intent.relations
      .filter((relation) => relation.childField === "obligation_id")
      .map(({ childField, parentTable, parentField }) => ({ childField, parentTable, parentField })),
    [{ childField: "obligation_id", parentTable: "decision_point_obligation", parentField: "obligation_id" }],
  );

  assert.equal(
    CHECK_CONTRACTS.ck_evaluation_state,
    "(technical_status='SUCCEEDED' AND proposed_decision IS NOT NULL AND business_decision IS NOT NULL AND failure_code IS NULL AND ((proposed_decision=business_decision AND decision_adjustment_reason_code IS NULL) OR (proposed_decision<>business_decision AND decision_adjustment_reason_code IS NOT NULL))) OR (technical_status IN ('FAILED','TIMED_OUT') AND proposed_decision IS NULL AND business_decision IS NULL AND decision_adjustment_reason_code IS NULL AND default_decision_used_flag=0 AND failure_code IS NOT NULL)",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_evaluation_action_intent_source,
    "(source_type='RULE' AND rule_action_id IS NOT NULL AND obligation_id IS NULL) OR (source_type='OBLIGATION' AND rule_action_id IS NULL AND obligation_id IS NOT NULL)",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_evaluation_action_intent_effect,
    "(status='MERGED' AND merged_effect_id IS NOT NULL) OR (status IN ('COLLECTED','DISCARDED','FAILED') AND merged_effect_id IS NULL)",
  );
  assert.deepEqual(CHECK_BINDINGS.ck_evaluation_state, ["decision_evaluation"]);
  assert.deepEqual(CHECK_BINDINGS.ck_evaluation_action_intent_source, ["evaluation_action_intent"]);
  assert.deepEqual(CHECK_BINDINGS.ck_evaluation_action_intent_effect, ["evaluation_action_intent"]);

  assert.deepEqual(MODEL_POLICY.ACTION_EFFECT_CANONICALIZATION, {
    canonicalizerCode: "RISK_ACTION_EFFECT_V1",
    canonicalFormat: "RFC_8785_JCS",
    textEncoding: "UTF-8",
    digestAlgorithm: "SHA-256",
    digestEncoding: "LOWERCASE_HEX",
    bigIntEncoding: "CANONICAL_DECIMAL_STRING",
    effectFields: [
      "action_version_id", "effect_mode", "evaluation_id", "merge_policy", "target_key", "target_type",
    ],
    contributorTable: "evaluation_action_intent",
    contributorFilter: { field: "status", value: "MERGED" },
    contributorLink: { field: "merged_effect_id", effectField: "effect_id" },
    contributorOrder: ["intent_sequence", "intent_id"],
    contributorFields: [
      "intent_id", "intent_sequence", "obligation_id", "resolved_target_key",
      "resolved_target_type", "rule_action_id", "source_type",
    ],
    excludedEffectFields: [
      "effect_id", "effect_status", "idempotency_key", "execution_type", "execution_status",
      "attempt_count", "started_time", "completed_time", "last_error_code", "last_error_summary",
    ],
    realIdempotencyKeyPrefix: "risk-action-effect:v1:",
  });
});

test("Risk staged Policy leaves Task 5 cross-row contracts for simulated Effects", () => {
  assert.deepEqual(Object.keys(CROSS_ROW_CONTRACTS).sort(), [
    "CR_EFFECT_HAS_MERGED_INTENT",
    "CR_EVALUATION_DEFAULT_DECISION",
    "CR_EVALUATION_EFFECT_MODE",
    "CR_EVALUATION_SNAPSHOT_DOMAIN",
    "CR_INTENT_OBLIGATION_SOURCE",
    "CR_INTENT_RULE_SOURCE",
    "CR_MERGED_INTENT_EFFECT",
    "CR_SHADOW_COMPARISON",
    "CR_SIMULATED_EFFECT_DOWNSTREAM",
  ]);
  assert.deepEqual(CROSS_ROW_CONTRACTS.CR_EVALUATION_EFFECT_MODE, {
    code: "CR_EVALUATION_EFFECT_MODE",
    contractType: "ALLOWED_MAPPING",
    selectors: [
      { mappingKey: "evaluationMode", table: "decision_evaluation", field: "evaluation_mode" },
      { mappingKey: "technicalStatus", table: "decision_evaluation", field: "technical_status" },
      { mappingKey: "effectMode", table: "action_effect", field: "effect_mode" },
    ],
    joins: [
      {
        from: { table: "decision_evaluation", field: "evaluation_id" },
        to: { table: "action_effect", field: "evaluation_id" },
      },
    ],
    requiredCoverage: [{ table: "action_effect", field: "effect_id" }],
    allowedMappings: [
      { evaluationMode: "PRIMARY", technicalStatus: "SUCCEEDED", effectMode: "REAL" },
      { evaluationMode: "SHADOW", technicalStatus: "SUCCEEDED", effectMode: "SIMULATED" },
      { evaluationMode: "REPLAY", technicalStatus: "SUCCEEDED", effectMode: "SIMULATED" },
    ],
  });
  assert.deepEqual(CROSS_ROW_CONTRACTS.CR_SHADOW_COMPARISON, {
    code: "CR_SHADOW_COMPARISON",
    contractType: "JOIN_ASSERTIONS",
    root: {
      alias: "candidate",
      table: "decision_evaluation",
      predicates: [{ field: "evaluation_mode", operator: "EQUALS", value: "SHADOW" }],
    },
    joins: [
      {
        from: { alias: "candidate", field: "baseline_evaluation_id" },
        to: { alias: "baseline", table: "decision_evaluation", field: "evaluation_id" },
        required: true,
      },
      {
        from: { alias: "candidate", field: "shadow_run_id" },
        to: { alias: "run", table: "release_shadow_run", field: "shadow_run_id" },
        required: true,
      },
    ],
    rowPredicates: [
      { endpoint: { alias: "baseline", field: "evaluation_mode" }, operator: "EQUALS", value: "PRIMARY" },
    ],
    fieldAssertions: [
      { left: { alias: "candidate", field: "evaluation_id" }, operator: "NOT_EQUALS", right: { alias: "baseline", field: "evaluation_id" } },
      { left: { alias: "candidate", field: "decision_point_id" }, operator: "EQUALS", right: { alias: "baseline", field: "decision_point_id" } },
      { left: { alias: "candidate", field: "evaluation_time" }, operator: "EQUALS", right: { alias: "baseline", field: "evaluation_time" } },
      { left: { alias: "candidate", field: "input_snapshot_hash" }, operator: "EQUALS", right: { alias: "baseline", field: "input_snapshot_hash" } },
      { left: { alias: "candidate", field: "release_id" }, operator: "EQUALS", right: { alias: "run", field: "candidate_release_id" } },
      { left: { alias: "baseline", field: "release_id" }, operator: "EQUALS", right: { alias: "run", field: "baseline_release_id" } },
      { left: { alias: "candidate", field: "shadow_run_id" }, operator: "EQUALS", right: { alias: "run", field: "shadow_run_id" } },
    ],
  });
  assert.equal(
    CROSS_ROW_CONTRACTS.CR_SIMULATED_EFFECT_DOWNSTREAM.forbiddenPaths.some((path) => (
      path.code === "CR_SIMULATED_EFFECT_SETTLEMENT_RESTRICTION"
      || path.joins.some((join) => [join.from.table, join.to.table].some((table) => (
        table.startsWith("settlement_restriction")
      )))
    )),
    false,
    "current simulated-effect policy must contain no legacy Settlement Restriction path",
  );
  assert.deepEqual(CROSS_ROW_CONTRACTS.CR_SIMULATED_EFFECT_DOWNSTREAM, {
    code: "CR_SIMULATED_EFFECT_DOWNSTREAM",
    contractType: "FORBIDDEN_PATHS",
    trigger: { table: "decision_evaluation", field: "evaluation_mode", operator: "IN", values: ["SHADOW", "REPLAY"] },
    forbiddenPaths: [
      {
        code: "CR_SIMULATED_EFFECT_RISK_RESTRICTION",
        joins: [
          { from: { table: "decision_evaluation", field: "evaluation_id" }, to: { table: "action_effect", field: "evaluation_id" } },
          { from: { table: "action_effect", field: "effect_id" }, to: { table: "risk_restriction", field: "source_effect_id" } },
        ],
        targetPredicates: [],
      },
      {
        code: "CR_SIMULATED_EFFECT_RISK_LIST_ENTRY",
        joins: [
          { from: { table: "decision_evaluation", field: "evaluation_id" }, to: { table: "action_effect", field: "evaluation_id" } },
          { from: { table: "action_effect", field: "effect_id" }, to: { table: "risk_list_entry", field: "source_effect_id" } },
        ],
        targetPredicates: [],
      },
      {
        code: "CR_SIMULATED_EFFECT_LIMIT_RESERVATION_GROUP",
        joins: [
          { from: { table: "decision_evaluation", field: "evaluation_id" }, to: { table: "transaction_risk_assessment", field: "evaluation_id" } },
          { from: { table: "transaction_risk_assessment", field: "assessment_id" }, to: { table: "limit_reservation_group", field: "assessment_id" } },
        ],
        targetPredicates: [],
      },
      {
        code: "CR_SIMULATED_EFFECT_LIMIT_RESERVATION",
        joins: [
          { from: { table: "decision_evaluation", field: "evaluation_id" }, to: { table: "transaction_risk_assessment", field: "evaluation_id" } },
          { from: { table: "transaction_risk_assessment", field: "assessment_id" }, to: { table: "limit_reservation_group", field: "assessment_id" } },
          { from: { table: "limit_reservation_group", field: "reservation_group_id" }, to: { table: "limit_reservation", field: "reservation_group_id" } },
        ],
        targetPredicates: [],
      },
      {
        code: "CR_SIMULATED_EFFECT_LIMIT_MOVEMENT",
        joins: [
          { from: { table: "decision_evaluation", field: "evaluation_id" }, to: { table: "transaction_risk_assessment", field: "evaluation_id" } },
          { from: { table: "transaction_risk_assessment", field: "assessment_id" }, to: { table: "limit_reservation_group", field: "assessment_id" } },
          { from: { table: "limit_reservation_group", field: "reservation_group_id" }, to: { table: "limit_reservation", field: "reservation_group_id" } },
          { from: { table: "limit_reservation", field: "reservation_id" }, to: { table: "limit_movement", field: "reservation_id" } },
        ],
        targetPredicates: [],
      },
      {
        code: "CR_SIMULATED_EFFECT_DISPUTE_LIABILITY",
        joins: [
          { from: { table: "decision_evaluation", field: "evaluation_id" }, to: { table: "action_effect", field: "evaluation_id" } },
          { from: { table: "action_effect", field: "effect_id" }, to: { table: "dispute_liability_decision", field: "action_effect_id" } },
        ],
        targetPredicates: [],
      },
      {
        code: "CR_SIMULATED_EFFECT_COMPENSATION_EVENT",
        joins: [
          { from: { table: "decision_evaluation", field: "evaluation_id" }, to: { table: "action_effect", field: "evaluation_id" } },
          { from: { table: "action_effect", field: "idempotency_key" }, to: { table: "compensation_event", field: "event_key" } },
        ],
        targetPredicates: [
          { field: "event_type", operator: "EQUALS", value: "EXECUTE_RISK_ACTION_EFFECT" },
          { field: "sub_event_type", operator: "EQUALS", value: "APPLY_ASYNC_ACTION_EFFECT" },
        ],
      },
      {
        code: "CR_SIMULATED_EFFECT_VELOCITY_EVENT",
        joins: [{ from: { table: "decision_evaluation", field: "evaluation_id" }, to: { table: "velocity_event", field: "source_id" } }],
        targetPredicates: [{ field: "source_type", operator: "IN", values: ["CHECKOUT_EVALUATION"] }],
      },
    ],
  });

  const tables = new Map(RISK_TABLES.map((table) => [table.defKey, table]));
  const modeContract = CROSS_ROW_CONTRACTS.CR_EVALUATION_EFFECT_MODE;
  for (const selector of modeContract.selectors) {
    const table = tables.get(selector.table);
    assert.ok(table, `模式契约选择器表不存在：${selector.table}`);
    assert.ok(manifestFieldNames(table).has(selector.field), `模式契约选择器字段不存在：${selector.table}.${selector.field}`);
  }
  for (const join of modeContract.joins) {
    for (const endpoint of [join.from, join.to]) {
      const table = tables.get(endpoint.table);
      assert.ok(table, `模式契约连接表不存在：${endpoint.table}`);
      assert.ok(manifestFieldNames(table).has(endpoint.field), `模式契约连接字段不存在：${endpoint.table}.${endpoint.field}`);
    }
  }

  const downstreamContract = CROSS_ROW_CONTRACTS.CR_SIMULATED_EFFECT_DOWNSTREAM;
  const triggerTable = tables.get(downstreamContract.trigger.table);
  assert.ok(triggerTable, `模拟 Effect 触发表不存在：${downstreamContract.trigger.table}`);
  assert.ok(manifestFieldNames(triggerTable).has(downstreamContract.trigger.field), `模拟 Effect 触发字段不存在：${downstreamContract.trigger.table}.${downstreamContract.trigger.field}`);
  const paths = downstreamContract.forbiddenPaths;
  for (const path of paths) {
    assert.equal(path.joins[0].from.table, "decision_evaluation", `${path.code} 必须从 decision_evaluation 开始`);
    const target = path.joins.at(-1).to.table;
    assert.ok(tables.has(target), `${path.code} 目标表不存在：${target}`);
    for (const join of path.joins) {
      for (const endpoint of [join.from, join.to]) {
        const table = tables.get(endpoint.table);
        assert.ok(table, `${path.code} 连接表不存在：${endpoint.table}`);
        assert.ok(manifestFieldNames(table).has(endpoint.field), `${path.code} 连接字段不存在：${endpoint.table}.${endpoint.field}`);
      }
    }
    for (let index = 1; index < path.joins.length; index += 1) {
      assert.equal(path.joins[index - 1].to.table, path.joins[index].from.table, `${path.code} 连接路径不连续`);
    }
    for (const predicate of path.targetPredicates) {
      assert.ok(manifestFieldNames(tables.get(target)).has(predicate.field), `${path.code} 目标谓词字段不存在：${target}.${predicate.field}`);
    }
  }
});

test("Risk Evaluation cross-row policy freezes Intent, Effect, default decision, and Snapshot consistency", () => {
  assert.deepEqual(CROSS_ROW_CONTRACTS.CR_EVALUATION_DEFAULT_DECISION, {
    code: "CR_EVALUATION_DEFAULT_DECISION",
    contractType: "JOIN_ASSERTIONS",
    root: {
      alias: "evaluation",
      table: "decision_evaluation",
      predicates: [{ field: "default_decision_used_flag", operator: "EQUALS", value: true }],
    },
    joins: [{
      from: { alias: "evaluation", field: "point_version_id" },
      to: { alias: "pointVersion", table: "decision_point_version", field: "point_version_id" },
      required: true,
    }],
    rowPredicates: [],
    fieldAssertions: [{
      left: { alias: "evaluation", field: "proposed_decision" },
      operator: "EQUALS",
      right: { alias: "pointVersion", field: "default_decision" },
    }],
  });

  for (const [code, sourceType, targetTable, targetField] of [
    ["CR_INTENT_RULE_SOURCE", "RULE", "rule_action", "rule_action_id"],
    ["CR_INTENT_OBLIGATION_SOURCE", "OBLIGATION", "decision_point_obligation", "obligation_id"],
  ]) {
    const contract = CROSS_ROW_CONTRACTS[code];
    assert.equal(contract.contractType, "JOIN_ASSERTIONS");
    assert.deepEqual(contract.root.predicates, [{ field: "source_type", operator: "EQUALS", value: sourceType }]);
    assert.deepEqual(contract.joins[0].to, { alias: "source", table: targetTable, field: targetField });
    assert.ok(contract.fieldAssertions.some((assertion) => (
      assertion.left.alias === "intent" && assertion.left.field === "action_version_id"
      && assertion.right.alias === "source" && assertion.right.field === "action_version_id"
    )));
  }
  assert.equal(CROSS_ROW_CONTRACTS.CR_INTENT_RULE_SOURCE.contextQuantifier, "ANY");
  assert.ok(CROSS_ROW_CONTRACTS.CR_INTENT_RULE_SOURCE.joins.some((join) => (
    join.to.table === "decision_point_rule_set" && join.required === false
  )));
  assert.ok(CROSS_ROW_CONTRACTS.CR_INTENT_RULE_SOURCE.fieldAssertions.some((assertion) => (
    assertion.left.alias === "binding" && assertion.left.field === "point_version_id"
    && assertion.right.alias === "evaluation" && assertion.right.field === "point_version_id"
  )));
  assert.ok(CROSS_ROW_CONTRACTS.CR_INTENT_OBLIGATION_SOURCE.fieldAssertions.some((assertion) => (
    assertion.left.alias === "source" && assertion.left.field === "point_version_id"
    && assertion.right.alias === "evaluation" && assertion.right.field === "point_version_id"
  )));

  assert.equal(CROSS_ROW_CONTRACTS.CR_MERGED_INTENT_EFFECT.contractType, "JOIN_ASSERTIONS");
  assert.equal(CROSS_ROW_CONTRACTS.CR_EFFECT_HAS_MERGED_INTENT.contractType, "JOIN_ASSERTIONS");
  assert.deepEqual(CROSS_ROW_CONTRACTS.CR_EVALUATION_SNAPSHOT_DOMAIN, {
    code: "CR_EVALUATION_SNAPSHOT_DOMAIN",
    contractType: "CONDITIONAL_CARDINALITY",
    root: { alias: "evaluation", table: "decision_evaluation", key: "evaluation_id" },
    joins: [{
      from: { alias: "evaluation", field: "decision_point_id" },
      to: { alias: "decisionPoint", table: "decision_point", field: "decision_point_id" },
      required: true,
    }],
    discriminator: { alias: "decisionPoint", field: "business_domain" },
    children: [
      { key: "checkoutSnapshot", table: "checkout_input_snapshot", foreignKey: "evaluation_id" },
      { key: "paymentSnapshot", table: "transaction_risk_assessment", foreignKey: "evaluation_id" },
      { key: "settlementSnapshot", table: "settlement_risk_assessment", foreignKey: "evaluation_id" },
    ],
    cases: [
      { values: ["CHECKOUT"], exactly: { checkoutSnapshot: 1, paymentSnapshot: 0, settlementSnapshot: 0 } },
      { values: ["PAYMENT"], exactly: { checkoutSnapshot: 0, paymentSnapshot: 1, settlementSnapshot: 0 } },
      { values: ["SETTLEMENT"], exactly: { checkoutSnapshot: 0, paymentSnapshot: 0, settlementSnapshot: 1 } },
      { values: ["DUE_DILIGENCE", "DISPUTE"], exactly: { checkoutSnapshot: 0, paymentSnapshot: 0, settlementSnapshot: 0 } },
    ],
  });
});

test("Risk Decision Engine and Evaluation freeze the 71-table target inventories", () => {
  const model = readModel();
  const groups = new Map(model.diagrams.map((diagram) => [diagram.defKey, diagram.properties.primaryTables]));
  const decisionEngine = [
    "t_decision_point", "t_decision_point_version", "t_decision_point_input_field",
    "t_decision_point_feature", "t_decision_point_rule_set", "t_decision_point_obligation",
    "t_feature_version", "t_feature_dependency", "t_rule_set_version", "t_risk_rule",
    "t_rule_node", "t_rule_operand", "t_action_version", "t_action_parameter_definition",
    "t_rule_action", "t_rule_action_parameter", "t_decision_release", "t_release_audit_event",
    "t_release_shadow_run",
  ];
  const evaluation = [
    "t_decision_evaluation", "t_evaluation_feature_result", "t_evaluation_rule_trace",
    "t_evaluation_action_intent", "t_action_effect", "t_checkout_input_snapshot",
    "t_transaction_risk_assessment", "t_settlement_risk_assessment",
  ];

  assert.equal(model.entities.length, 71);
  assert.deepEqual(groups.get("decision_engine")?.sort(), decisionEngine.sort());
  assert.deepEqual(groups.get("evaluation")?.sort(), evaluation.sort());
});

test("Task 6 keeps every new Evaluation relation child field on a left-prefix access index", () => {
  const evaluationTables = new Map(EVALUATION_TABLES.map((table) => [table.defKey, table]));
  const evaluationManifest = evaluationTables.get("decision_evaluation");
  const traceManifest = evaluationTables.get("evaluation_rule_trace");

  assert.ok(evaluationManifest, "缺少目标 Manifest 表：decision_evaluation");
  assert.ok(traceManifest, "缺少目标 Manifest 表：evaluation_rule_trace");
  assertManifestAccessIndex(evaluationManifest, "idx_eval_baseline", ["baseline_evaluation_id"]);
  assertManifestAccessIndex(traceManifest, "idx_eval_trace_rule", ["rule_id"]);

  const entities = entitiesByKey(readModel());
  const evaluation = entities.get("t_decision_evaluation");
  const trace = entities.get("t_evaluation_rule_trace");

  assert.ok(evaluation, "缺少目标表：t_decision_evaluation");
  assert.ok(trace, "缺少目标表：t_evaluation_rule_trace");
  assertAccessIndex(evaluation, ["baseline_evaluation_id"]);
  assertAccessIndex(trace, ["rule_id"]);
});

test("Risk Decision Engine absorbs Feature Action and Rule Set catalog identities into Version rows", () => {
  const entities = entitiesByKey(readModel());
  const contracts = [
    {
      table: "t_feature_version",
      fields: ["feature_version_id", "feature_code", "feature_name", "business_category", "version_no"],
      absent: ["feature_id"],
      unique: ["feature_code", "version_no"],
    },
    {
      table: "t_action_version",
      fields: ["action_version_id", "action_code", "action_name", "action_category", "version_no"],
      absent: ["action_id"],
      unique: ["action_code", "version_no"],
    },
    {
      table: "t_rule_set_version",
      fields: ["rule_set_version_id", "decision_point_id", "rule_set_code", "rule_set_name", "version_no"],
      absent: ["rule_set_id"],
      unique: ["decision_point_id", "rule_set_code", "version_no"],
    },
  ];

  for (const contract of contracts) {
    const entity = entities.get(contract.table);
    assert.ok(entity, `缺少目标表：${contract.table}`);
    assertFields(entity, contract.fields);
    assertAbsentFields(entity, contract.absent);
    assertUniqueIndex(entity, contract.unique);
  }
  assertRelation(entities, "t_rule_set_version", "decision_point_id", "t_decision_point", "decision_point_id");
});

test("Risk Rule Operand stores typed multi-row Literal values without a value child table", () => {
  const entities = entitiesByKey(readModel());
  const operand = entities.get("t_rule_operand");

  assert.ok(operand, "缺少目标表：t_rule_operand");
  assertFields(operand, [
    "operand_id", "node_id", "operand_role", "value_sequence", "source_type",
    "feature_version_id", "input_field_id", "value_type", "string_value", "number_value",
    "boolean_value", "datetime_value", "duration_value", "create_time",
  ]);
  assertUniqueIndex(operand, ["node_id", "operand_role", "value_sequence"]);
  assert.ok(!entities.has("t_rule_operand_value"), "已删除的 t_rule_operand_value 仍存在");
});

test("Risk Release uses typed audit events and Shadow comparisons are held on Evaluations", () => {
  const entities = entitiesByKey(readModel());
  const audit = entities.get("t_release_audit_event");
  const shadowRun = entities.get("t_release_shadow_run");
  const evaluation = entities.get("t_decision_evaluation");

  assert.ok(audit, "缺少目标表：t_release_audit_event");
  assertFields(audit, [
    "audit_event_id", "release_id", "event_type", "event_time", "check_code", "check_status",
    "severity", "error_code", "error_summary", "shadow_run_id", "approval_request_id",
    "approval_status", "reviewer_id", "review_comment", "from_status", "to_status",
    "reason_code", "trigger_type", "trigger_id", "actor_id", "aggregate_version", "create_time",
  ]);
  assertUniqueIndex(audit, ["release_id", "event_type", "check_code"]);
  assertUniqueIndex(audit, ["release_id", "event_type", "aggregate_version"]);
  assertUniqueIndex(audit, ["release_id", "event_type", "approval_request_id"]);
  assertAccessIndex(audit, ["release_id", "event_time"]);
  assertAccessIndex(audit, ["shadow_run_id", "event_time"]);
  assertRelation(entities, "t_release_audit_event", "release_id", "t_decision_release", "release_id");
  assertRelation(entities, "t_release_audit_event", "shadow_run_id", "t_release_shadow_run", "shadow_run_id");

  assert.ok(shadowRun, "缺少目标表：t_release_shadow_run");
  assertFields(shadowRun, [
    "allow_to_review_count", "allow_to_deny_count", "deny_to_allow_count", "action_change_count",
    "feature_missing_count", "failed_count", "timed_out_count", "p95_millis", "p99_millis",
    "check_status", "version",
  ]);

  assert.ok(evaluation, "缺少目标表：t_decision_evaluation");
  assertFields(evaluation, [
    "shadow_run_id", "baseline_evaluation_id", "decision_changed_flag", "action_changed_flag",
    "comparison_severity", "compared_time",
  ]);
  assertUniqueIndex(evaluation, ["shadow_run_id", "baseline_evaluation_id"]);
  assertRelation(entities, "t_decision_evaluation", "shadow_run_id", "t_release_shadow_run", "shadow_run_id");
  assertRelation(entities, "t_decision_evaluation", "baseline_evaluation_id", "t_decision_evaluation", "evaluation_id");
  assert.ok(!entities.has("t_release_shadow_comparison"), "已删除的 t_release_shadow_comparison 仍存在");
});

test("Risk Evaluation combines Rule and Predicate traces and embeds one-to-one Effect execution", () => {
  const entities = entitiesByKey(readModel());
  const trace = entities.get("t_evaluation_rule_trace");
  const effect = entities.get("t_action_effect");

  assert.ok(trace, "缺少目标表：t_evaluation_rule_trace");
  assertFields(trace, [
    "trace_id", "evaluation_id", "trace_type", "rule_set_version_id", "rule_id", "node_id",
    "execution_sequence", "result_value", "terminal_flag", "elapsed_millis", "failure_code",
    "rule_result_slot", "predicate_result_slot", "create_time",
  ]);
  assertUniqueIndex(trace, ["evaluation_id", "rule_id", "rule_result_slot"]);
  assertUniqueIndex(trace, ["evaluation_id", "node_id", "predicate_result_slot"]);
  assertUniqueIndex(trace, ["evaluation_id", "trace_type", "execution_sequence"]);
  assertRelation(entities, "t_evaluation_rule_trace", "evaluation_id", "t_decision_evaluation", "evaluation_id");
  assertRelation(entities, "t_evaluation_rule_trace", "rule_set_version_id", "t_rule_set_version", "rule_set_version_id");
  assertRelation(entities, "t_evaluation_rule_trace", "rule_id", "t_risk_rule", "rule_id");
  assertRelation(entities, "t_evaluation_rule_trace", "node_id", "t_rule_node", "node_id");

  assert.ok(effect, "缺少目标表：t_action_effect");
  assertFields(effect, [
    "effect_status", "idempotency_key", "execution_type", "execution_status", "attempt_count",
    "started_time", "completed_time", "last_error_code", "last_error_summary",
  ]);
  assertAbsentFields(effect, ["status"]);
  assertUniqueIndex(effect, ["idempotency_key"]);
  assert.ok(!entities.has("t_action_execution"), "已删除的 t_action_execution 仍存在");
});

test("Task 5 accepts a deep typed AST with Cooldown and IP-list actions deterministically", () => {
  const fixture = buildValidDecisionFixture();
  const before = structuredClone(fixture);

  assert.deepEqual(validateDecisionRelease(fixture), []);
  assert.deepEqual(validateDecisionRelease(fixture), []);
  assert.deepEqual(fixture, before, "静态校验器不得修改输入 Fixture");

  const shuffled = structuredClone(fixture);
  for (const [key, value] of Object.entries(shuffled)) {
    if (Array.isArray(value)) value.reverse();
  }
  for (const rows of Object.values(shuffled.crossRowRows)) rows.reverse();
  assert.deepEqual(validateDecisionRelease(shuffled), []);
});

test("Settlement Release hashes mixed generic typed parameters and complete sequenced Scope tuples", () => {
  const fixture = buildValidSettlementDecisionFixture();
  const before = structuredClone(fixture);

  assert.equal(
    fixture.actionParameters.find((parameter) => parameter.parameterDefinitionId === "241120").numberValue,
    "10.00000000",
    "通用 NUMBER 必须保留 Decision Engine 固定八位小数的规范编码",
  );

  assert.equal(
    canonicalizeSettlementActionParameterIdentity(settlementParameterIdentityRows(fixture)),
    SETTLEMENT_ACTION_PARAMETER_IDENTITY,
    "Settlement Release 参数身份必须匹配独立冻结 bytes",
  );
  assert.deepEqual(validateDecisionRelease(fixture), []);
  assert.deepEqual(fixture, before, "Settlement 静态与运行事实校验不得修改输入");

  fixture.actionParameters.reverse();
  for (const rows of Object.values(fixture.crossRowRows)) rows.reverse();
  assert.equal(
    canonicalizeSettlementActionParameterIdentity(settlementParameterIdentityRows(fixture)),
    SETTLEMENT_ACTION_PARAMETER_IDENTITY,
    "物理行倒序不得改变冻结参数身份",
  );
  assert.deepEqual(
    validateDecisionRelease(fixture),
    [],
    "Release 参数身份与运行事实闭包不得依赖 Fixture/SQL 返回顺序",
  );

  const swappedTuples = buildValidSettlementDecisionFixture();
  for (const parameter of swappedTuples.actionParameters) {
    if (!SETTLEMENT_SCOPE_DEFINITION_IDS.has(parameter.parameterDefinitionId)) continue;
    parameter.valueSequence = parameter.valueSequence === 1 ? 2 : 1;
  }
  assert.notEqual(
    canonicalizeSettlementActionParameterIdentity(settlementParameterIdentityRows(swappedTuples)),
    SETTLEMENT_ACTION_PARAMETER_IDENTITY,
    "交换两个完整 Scope Tuple 必须改变规范参数身份",
  );
  assertRejectedBy(
    validateDecisionRelease(swappedTuples),
    "REFERENCE_INTEGRITY",
    "RELEASE_CONTENT_HASH_MISMATCH",
  );

  const malformedProjection = buildValidSettlementDecisionFixture();
  malformedProjection.actionParameters.find((parameter) => (
    parameter.parameterDefinitionId === "241120"
  )).numberValue = 10n;
  assertRejectedBy(
    validateDecisionRelease(malformedProjection),
    "REFERENCE_INTEGRITY",
    "RELEASE_CONTENT_PROJECTION_INVALID",
  );
});

test("Settlement Release hash excludes lifecycle metadata but binds immutable executable content", () => {
  const lifecycleOnly = buildValidSettlementDecisionFixture();
  Object.assign(lifecycleOnly.release, {
    status: "APPROVED", activeSlot: 1, version: 9,
    approvedBy: "900001", approvedTime: "2026-08-28T11:00:00.000000Z",
  });
  Object.assign(lifecycleOnly.decisionPoint, {
    status: "DISABLED", updateTime: "2026-08-28T11:01:00.000000Z",
  });
  Object.assign(lifecycleOnly.pointVersion, {
    status: "RETIRED", frozenBy: "900002", frozenTime: "2026-08-28T11:02:00.000000Z",
  });
  Object.assign(lifecycleOnly.ruleSetVersions[0], {
    frozenBy: "900003", frozenTime: "2026-08-28T11:03:00.000000Z",
    approvedBy: "900004", approvedTime: "2026-08-28T11:04:00.000000Z",
  });
  Object.assign(lifecycleOnly.actionVersions[0], {
    createTime: "2026-08-28T11:05:00.000000Z",
    updateTime: "2026-08-28T11:06:00.000000Z",
  });
  const lifecycleErrors = validateDecisionRelease(lifecycleOnly);
  assertNotRejectedBy(
    lifecycleErrors,
    "REFERENCE_INTEGRITY",
    "RELEASE_CONTENT_HASH_MISMATCH",
  );

  const executableMutation = buildValidSettlementDecisionFixture();
  executableMutation.rules[0].priorityNo = 2;
  assertRejectedBy(
    validateDecisionRelease(executableMutation),
    "REFERENCE_INTEGRITY",
    "RELEASE_CONTENT_HASH_MISMATCH",
  );
});

test("Settlement Release collection sorting is total for duplicate stable IDs", () => {
  const firstOrder = buildValidSettlementDecisionFixture();
  firstOrder.rules.push({ ...firstOrder.rules[0], priorityNo: 2 });
  firstOrder.release.contentHash = SETTLEMENT_DUPLICATE_RULE_CONTENT_HASH;
  const reversedOrder = structuredClone(firstOrder);
  reversedOrder.rules.reverse();

  const firstErrors = validateDecisionRelease(firstOrder);
  const reversedErrors = validateDecisionRelease(reversedOrder);
  assertRejectedBy(firstErrors, "REFERENCE_INTEGRITY", "REFERENCE_DUPLICATE_ID");
  assertNotRejectedBy(firstErrors, "REFERENCE_INTEGRITY", "RELEASE_CONTENT_HASH_MISMATCH");
  assert.deepEqual(
    reversedErrors,
    firstErrors,
    "相同 ID 的不同行倒序后必须产生完全相同的 Hash 结果与稳定错误",
  );
});

test("Settlement Release neutralizes duplicate Parameter Definition lookup independent of row order", () => {
  const fixtureWithDuplicateDefinition = () => {
    const fixture = buildValidSettlementDecisionFixture();
    fixture.parameterDefinitions.push({
      ...fixture.parameterDefinitions.find((definition) => (
        definition.parameterDefinitionId === "241120"
      )),
      valueType: "STRING",
    });
    return fixture;
  };
  const firstOrder = fixtureWithDuplicateDefinition();
  const reversedOrder = fixtureWithDuplicateDefinition();
  reversedOrder.parameterDefinitions.reverse();

  const firstErrors = validateDecisionRelease(firstOrder);
  const reversedErrors = validateDecisionRelease(reversedOrder);
  assertRejectedBy(firstErrors, "REFERENCE_INTEGRITY", "REFERENCE_DUPLICATE_ID");
  assertRejectedBy(firstErrors, "REFERENCE_INTEGRITY", "RELEASE_CONTENT_PROJECTION_INVALID");
  assert.deepEqual(
    reversedErrors,
    firstErrors,
    "冲突的 Parameter Definition 重复行不得按物理顺序选择 first/last valueType",
  );
});

test("Settlement Release hash excludes display-only Limit name", () => {
  const fixtureWithLimitName = (limitName) => {
    const fixture = buildValidSettlementDecisionFixture();
    fixture.limitVersions = structuredClone(buildValidDecisionFixture().limitVersions);
    fixture.limitVersions[0].limitName = limitName;
    fixture.release.contentHash = SETTLEMENT_LIMIT_DISPLAY_CONTENT_HASH;
    return fixture;
  };

  assert.deepEqual(validateDecisionRelease(fixtureWithLimitName("Consumer Daily Amount")), []);
  assert.deepEqual(validateDecisionRelease(fixtureWithLimitName("Renamed Display Label")), []);
});

test("Settlement Release rejects missing ambiguous or rewritten mandatory Eligibility obligations", () => {
  const cases = [
    ["REQUIRED_SETTLEMENT_OBLIGATION_MISSING", (rows) => {
      rows.decision_point_obligation = rows.decision_point_obligation.filter((row) => row.point_version_id !== "210112");
    }],
    ["REQUIRED_SETTLEMENT_OBLIGATION_AMBIGUOUS", (rows) => rows.decision_point_obligation.push({
      ...rows.decision_point_obligation.find((row) => row.point_version_id === "210112"),
      obligation_id: "259112", sequence_no: 2,
    })],
    ["SETTLEMENT_OBLIGATION_CONTRACT_INVALID", (rows) => {
      rows.decision_point_obligation.find((row) => row.point_version_id === "210112").trigger_decision = "DENY";
    }],
    ["SETTLEMENT_OBLIGATION_CONTRACT_INVALID", (rows) => {
      rows.decision_point_obligation.find((row) => row.point_version_id === "210112").failure_policy = "RETRY_ASYNC";
    }],
    ["SETTLEMENT_OBLIGATION_CONTRACT_INVALID", (rows) => {
      rows.decision_point_obligation.find((row) => row.point_version_id === "210112").enabled_flag = false;
    }],
    ["SETTLEMENT_OBLIGATION_CONTRACT_INVALID", (rows) => {
      rows.decision_point_obligation.find((row) => row.point_version_id === "210112").action_version_id = "240101";
    }],
    ["SETTLEMENT_OBLIGATION_CONTRACT_INVALID", (rows) => rows.decision_point_limit_binding.push({
      binding_id: "251101", obligation_id: "259102", limit_version_id: "250011",
    })],
    ["SETTLEMENT_OBLIGATION_CONTRACT_INVALID", (rows) => rows.evaluation_action_intent.push({
      ...rows.evaluation_action_intent[0], intent_id: "314112", source_type: "OBLIGATION",
      rule_action_id: null, obligation_id: "259102",
    })],
  ];

  for (const [errorCode, mutate] of cases) {
    const fixture = buildValidSettlementDecisionFixture();
    mutate(fixture.crossRowRows);
    assertRejectedBy(validateDecisionRelease(fixture), "SYSTEM_OBLIGATION", errorCode);
  }
});

test("Settlement Release freezes Rule reasons, exact Action metadata, and triple Action Parameter identity", () => {
  const missingReason = buildValidSettlementDecisionFixture();
  missingReason.rules[0].reasonCode = null;
  assertRejectedBy(validateDecisionRelease(missingReason), "SYSTEM_OBLIGATION", "SETTLEMENT_RULE_REASON_MISSING");

  const wrongCode = buildSingleScopeSettlementDecisionFixture();
  wrongCode.actionVersions[0].actionCode = "CREATE_SETTLEMENT_RESTRICTION";
  assertRejectedBy(
    validateDecisionRelease(wrongCode),
    "ACTION_PARAMETERS",
    "SETTLEMENT_ACTION_CONTRACT_INVALID",
  );

  for (const mutate of [
    (action) => { action.actionCategory = "RESTRICTION"; },
    (action) => { action.targetType = "CLIENT"; },
    (action) => { action.terminalFlag = true; action.decisionCode = "DENY"; },
    (action) => { action.status = "RETIRED"; },
    (action) => { action.shadowSupportedFlag = false; },
  ]) {
    const fixture = buildValidSettlementDecisionFixture();
    mutate(fixture.actionVersions[0]);
    assertRejectedBy(validateDecisionRelease(fixture), "ACTION_PARAMETERS", "SETTLEMENT_ACTION_CONTRACT_INVALID");
  }

  const handlerMismatch = buildValidSettlementDecisionFixture();
  handlerMismatch.handlerDeployments.find((row) => row.handlerCode === "CreateSettlementControlAction").implementationHash = hashOf("e");
  assertRejectedBy(validateDecisionRelease(handlerMismatch), "HANDLER_HASH", "HANDLER_HASH_MISMATCH");

  const duplicateTriple = buildValidSettlementDecisionFixture();
  duplicateTriple.actionParameters.push({ ...duplicateTriple.actionParameters.at(-1), parameterId: "243199" });
  assertRejectedBy(validateDecisionRelease(duplicateTriple), "ACTION_PARAMETERS", "ACTION_PARAMETER_DUPLICATE");

  const nonSettlementSequence = buildValidDecisionFixture();
  nonSettlementSequence.actionParameters[0].valueSequence = 2;
  assertRejectedBy(validateDecisionRelease(nonSettlementSequence), "ACTION_PARAMETERS", "ACTION_PARAMETER_SEQUENCE_INVALID");

  const rootSequence = buildValidSettlementDecisionFixture();
  rootSequence.actionParameters.find((row) => row.parameterDefinitionId === "241101").valueSequence = 2;
  assertRejectedBy(validateDecisionRelease(rootSequence), "ACTION_PARAMETERS", "SETTLEMENT_SCOPE_SEQUENCE_INVALID");
});

test("Settlement Scope tuples require complete continuous ID Currency Country identities", () => {
  const cases = [
    ["SETTLEMENT_SCOPE_SEQUENCE_INVALID", (fixture) => {
      fixture.actionParameters.filter((row) => row.valueSequence === 2).forEach((row) => { row.valueSequence = 3; });
    }],
    ["SETTLEMENT_SCOPE_SEQUENCE_INVALID", (fixture) => {
      fixture.actionParameters.find((row) => row.parameterDefinitionId === "241111" && row.valueSequence === 2).valueSequence = 1;
    }],
    ["SETTLEMENT_SCOPE_TUPLE_INVALID", (fixture) => {
      fixture.actionParameters = fixture.actionParameters.filter((row) => !(row.parameterDefinitionId === "241113" && row.valueSequence === 2));
    }],
    ["SETTLEMENT_SCOPE_TUPLE_INVALID", (fixture) => {
      fixture.actionParameters.find((row) => row.parameterDefinitionId === "241111").stringValue = "0210001";
    }],
    ["SETTLEMENT_SCOPE_TUPLE_INVALID", (fixture) => {
      fixture.actionParameters.find((row) => row.parameterDefinitionId === "241112").stringValue = "usd";
    }],
    ["SETTLEMENT_SCOPE_TUPLE_INVALID", (fixture) => {
      fixture.actionParameters.find((row) => row.parameterDefinitionId === "241113").stringValue = "ARE";
    }],
  ];
  for (const [errorCode, mutate] of cases) {
    const fixture = buildValidSettlementDecisionFixture();
    mutate(fixture);
    assertRejectedBy(validateDecisionRelease(fixture), "ACTION_PARAMETERS", errorCode);
  }

  const forbiddenDeposit = buildValidSettlementDecisionFixture();
  forbiddenDeposit.actionParameters.find((row) => row.parameterDefinitionId === "241101").stringValue = "LOCAL_PARTNER";
  assertRejectedBy(
    validateDecisionRelease(forbiddenDeposit),
    "SYSTEM_OBLIGATION",
    "LOCAL_PARTNER_SECURITY_DEPOSIT_FORBIDDEN",
  );
});

test("Settlement Scope source matrix permits one Context tuple but rejects Feature mixed and multi-Context tuples", () => {
  assert.deepEqual(validateDecisionRelease(buildSingleScopeSettlementDecisionFixture()), []);
  assert.deepEqual(validateDecisionRelease(buildContextScopeSettlementDecisionFixture()), []);

  const featureScope = buildSingleScopeSettlementDecisionFixture();
  for (const definition of featureScope.parameterDefinitions) {
    if (SETTLEMENT_SCOPE_DEFINITION_IDS.has(definition.parameterDefinitionId)) {
      definition.allowedSourceType = "FEATURE";
    }
  }
  for (const parameter of featureScope.actionParameters) {
    if (!SETTLEMENT_SCOPE_DEFINITION_IDS.has(parameter.parameterDefinitionId)) continue;
    parameter.sourceType = "FEATURE";
    parameter.featureVersionId = "220011";
    parameter.stringValue = null;
  }
  assertRejectedBy(
    validateDecisionRelease(featureScope),
    "ACTION_PARAMETERS",
    "SETTLEMENT_SCOPE_TUPLE_INVALID",
  );

  const mixedScope = buildSingleScopeSettlementDecisionFixture();
  mixedScope.parameterDefinitions.find((row) => row.parameterDefinitionId === "241111").allowedSourceType = "CONTEXT";
  Object.assign(
    mixedScope.actionParameters.find((row) => row.parameterDefinitionId === "241111"),
    { sourceType: "CONTEXT", inputFieldId: "210021", stringValue: null },
  );
  assertRejectedBy(
    validateDecisionRelease(mixedScope),
    "ACTION_PARAMETERS",
    "SETTLEMENT_SCOPE_TUPLE_INVALID",
  );

  const multiContext = buildContextScopeSettlementDecisionFixture();
  for (const parameter of multiContext.actionParameters.filter((row) => (
    SETTLEMENT_SCOPE_DEFINITION_IDS.has(row.parameterDefinitionId)
  ))) {
    multiContext.actionParameters.push({
      ...parameter,
      parameterId: String(Number(parameter.parameterId) + 10),
      valueSequence: 2,
    });
  }
  assertRejectedBy(
    validateDecisionRelease(multiContext),
    "ACTION_PARAMETERS",
    "SETTLEMENT_SCOPE_TUPLE_INVALID",
  );

  const unprovableDepositRole = buildSingleScopeSettlementDecisionFixture();
  unprovableDepositRole.parameterDefinitions.find((row) => row.parameterDefinitionId === "241101").allowedSourceType = "CONTEXT";
  Object.assign(
    unprovableDepositRole.actionParameters.find((row) => row.parameterDefinitionId === "241101"),
    { sourceType: "CONTEXT", inputFieldId: "210021", stringValue: null },
  );
  assertRejectedBy(
    validateDecisionRelease(unprovableDepositRole),
    "SYSTEM_OBLIGATION",
    "LOCAL_PARTNER_SECURITY_DEPOSIT_FORBIDDEN",
  );
});

test("Runtime cross-row snapshots require every collection as an explicit array", () => {
  const invalidCollectionValues = Object.freeze([
    ["deleted", Symbol("delete")],
    ["null", null],
    ["object", Object.freeze({})],
  ]);
  for (const table of REQUIRED_RUNTIME_COLLECTIONS) {
    for (const [label, invalidValue] of invalidCollectionValues) {
      const fixture = buildValidSettlementDecisionFixture();
      if (typeof invalidValue === "symbol") delete fixture.crossRowRows[table];
      else fixture.crossRowRows[table] = invalidValue;
      const errors = validateDecisionRelease(fixture);
      assert.ok(
        errors.some((error) => error.check_code === "REFERENCE_INTEGRITY"
          && error.error_code === "CR_POLICY_MISSING_ROWS"
          && error.path === `crossRowRows.${table}`),
        `${table}=${label} 必须 fail closed：${JSON.stringify(errors)}`,
      );
    }
  }
});

test("Settlement runtime origin predicates fail independently and bind the checked static Release", () => {
  const cases = [
    ["evaluation mode", "CR_SIMULATED_EFFECT_SETTLEMENT_CONTROL", (rows) => {
      rows.decision_evaluation.find((row) => row.evaluation_id === "310101").evaluation_mode = "SHADOW";
    }],
    ["evaluation technical status", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.decision_evaluation.find((row) => row.evaluation_id === "310101").technical_status = "FAILED";
    }],
    ["Effect mode", "CR_SIMULATED_EFFECT_SETTLEMENT_CONTROL", (rows) => {
      rows.action_effect.find((row) => row.effect_id === "315101").effect_mode = "SIMULATED";
    }],
    ["Effect status", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_effect.find((row) => row.effect_id === "315101").effect_status = "CREATED";
    }],
    ["execution type", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_effect.find((row) => row.effect_id === "315101").execution_type = "LOCAL_HANDLER";
    }],
    ["execution status", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_effect.find((row) => row.effect_id === "315101").execution_status = "ERROR";
    }],
    ["Intent source", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.evaluation_action_intent.find((row) => row.intent_id === "314101").source_type = "OBLIGATION";
    }],
    ["Intent status", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.evaluation_action_intent.find((row) => row.intent_id === "314101").status = "COLLECTED";
    }],
    ["missing Intent", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.evaluation_action_intent = rows.evaluation_action_intent.filter((row) => row.intent_id !== "314101");
    }],
    ["duplicate Intent", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => rows.evaluation_action_intent.push({
      ...rows.evaluation_action_intent.find((row) => row.intent_id === "314101"),
      intent_id: "314102", intent_sequence: 2,
    })],
    ["missing Effect", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_effect = rows.action_effect.filter((row) => row.effect_id !== "315101");
    }],
    ["duplicate Effect", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => rows.action_effect.push({
      ...rows.action_effect.find((row) => row.effect_id === "315101"),
    })],
    ["missing Evaluation", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.decision_evaluation = rows.decision_evaluation.filter((row) => row.evaluation_id !== "310101");
    }],
    ["duplicate Evaluation", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => rows.decision_evaluation.push({
      ...rows.decision_evaluation.find((row) => row.evaluation_id === "310101"),
    })],
    ["missing runtime Rule Action", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.rule_action = rows.rule_action.filter((row) => row.rule_action_id !== "242101");
    }],
    ["duplicate runtime Rule Action", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => rows.rule_action.push({
      ...rows.rule_action.find((row) => row.rule_action_id === "242101"),
    })],
    ["missing runtime Rule", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.risk_rule = rows.risk_rule.filter((row) => row.rule_id !== "230021");
    }],
    ["duplicate runtime Rule", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => rows.risk_rule.push({
      ...rows.risk_rule.find((row) => row.rule_id === "230021"),
    })],
    ["missing runtime Point RuleSet", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.decision_point_rule_set = rows.decision_point_rule_set.filter((row) => row.binding_id !== "211101");
    }],
    ["duplicate runtime Point RuleSet", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => rows.decision_point_rule_set.push({
      ...rows.decision_point_rule_set.find((row) => row.binding_id === "211101"),
    })],
    ["missing runtime Action Version", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_version = rows.action_version.filter((row) => row.action_version_id !== "240101");
    }],
    ["duplicate runtime Action Version", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => rows.action_version.push({
      ...rows.action_version.find((row) => row.action_version_id === "240101"),
    })],
    ["Intent Evaluation provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.evaluation_action_intent.find((row) => row.intent_id === "314101").evaluation_id = "310001";
    }],
    ["Effect Action provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_effect.find((row) => row.effect_id === "315101").action_version_id = "240011";
    }],
    ["Intent Action provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.evaluation_action_intent.find((row) => row.intent_id === "314101").action_version_id = "240011";
    }],
    ["runtime Rule Action version provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.rule_action.find((row) => row.rule_action_id === "242101").action_version_id = "240011";
    }],
    ["runtime Rule Action Rule provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.rule_action.find((row) => row.rule_action_id === "242101").rule_id = "231001";
    }],
    ["runtime Release provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.decision_evaluation.find((row) => row.evaluation_id === "310101").release_id = "260001";
    }],
    ["runtime Point provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.decision_evaluation.find((row) => row.evaluation_id === "310101").decision_point_id = "210102";
    }],
    ["runtime Point Version provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.decision_evaluation.find((row) => row.evaluation_id === "310101").point_version_id = "210112";
    }],
    ["runtime Action code provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_version.find((row) => row.action_version_id === "240101").action_code = "CREATE_OTHER_CONTROL";
    }],
    ["runtime Action category provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_version.find((row) => row.action_version_id === "240101").action_category = "RESTRICTION";
    }],
    ["runtime Action terminal provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_version.find((row) => row.action_version_id === "240101").terminal_flag = true;
    }],
    ["runtime Action status provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_version.find((row) => row.action_version_id === "240101").status = "RETIRED";
    }],
    ["runtime Action shadow provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_version.find((row) => row.action_version_id === "240101").shadow_supported_flag = false;
    }],
    ["runtime Action merge provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_version.find((row) => row.action_version_id === "240101").merge_policy = "MAX";
    }],
    ["Effect target type", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_effect.find((row) => row.effect_id === "315101").target_type = "CLIENT";
    }],
    ["Intent target type", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.evaluation_action_intent.find((row) => row.intent_id === "314101").resolved_target_type = "CLIENT";
    }],
    ["Effect target key", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.action_effect.find((row) => row.effect_id === "315101").target_key = "CLIENT:200001:WRONG";
    }],
    ["Intent target key", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.evaluation_action_intent.find((row) => row.intent_id === "314101").resolved_target_key = "CLIENT:200001:WRONG";
    }],
    ["Control target role", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.settlement_control[0].role_type = "LOCAL_PARTNER";
    }],
    ["Control target id", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.settlement_control[0].role_id = "200002";
    }],
    ["Control instance provenance", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", (rows) => {
      rows.settlement_control[0].control_instance_key = "RULE_ACTION:242102:PAYOUT:560001";
    }],
  ];
  for (const [label, errorCode, mutate] of cases) {
    const fixture = buildValidSettlementDecisionFixture();
    mutate(fixture.crossRowRows);
    const errors = validateDecisionRelease(fixture);
    assertRejectedBy(errors, "REFERENCE_INTEGRITY", errorCode);
    assert.ok(errors.length > 0, `${label} 必须独立失败`);
  }

  for (const malformed of [null, 7]) {
    const fixture = buildValidSettlementDecisionFixture();
    fixture.crossRowRows.settlement_control[0].control_instance_key = malformed;
    let errors;
    assert.doesNotThrow(() => { errors = validateDecisionRelease(fixture); });
    assertRejectedBy(errors, "REFERENCE_INTEGRITY", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID");
  }
});

test("Settlement Control aggregate freezes every history event currency and Assessment assertion", () => {
  const cases = [
    ["source Effect uniqueness", "CR_SETTLEMENT_CONTROL_EFFECT_UNIQUE", (rows) => rows.settlement_control.push({
      ...rows.settlement_control[0], control_id: "360102", control_no: "SC-360102",
    })],
    ["missing Scope", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => { rows.settlement_control_scope = []; }],
    ["missing initial State Change", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control_state_change = rows.settlement_control_state_change.filter((row) => row.aggregate_version !== 1);
    }],
    ["duplicate initial State Change", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => rows.settlement_control_state_change.push({
      ...rows.settlement_control_state_change[0], change_id: "362103",
    })],
    ["initial aggregate version", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control_state_change[0].aggregate_version = 3;
    }],
    ["initial from status", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control_state_change[0].from_status = "ACTIVE";
    }],
    ["initial to status", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control_state_change[0].to_status = "RELEASED";
    }],
    ["initial trigger type", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control_state_change[0].trigger_type = "OPERATION_COMMAND";
    }],
    ["initial trigger id", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control_state_change[0].trigger_id = "315199";
    }],
    ["incomplete history", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control_state_change = rows.settlement_control_state_change.filter((row) => row.aggregate_version !== 2);
    }],
    ["history linkage", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control_state_change[1].from_status = "RELEASED";
    }],
    ["root status", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control[0].status = "EXPIRED";
    }],
    ["root active slot", "CR_SETTLEMENT_CONTROL_AGGREGATE", (rows) => {
      rows.settlement_control[0].active_slot = 1;
    }],
    ["root version", "CR_SETTLEMENT_CONTROL_EVENT", (rows) => {
      rows.settlement_control[0].version = 3;
    }],
    ["missing per-version event", "CR_SETTLEMENT_CONTROL_EVENT", (rows) => {
      rows.compensation_event = rows.compensation_event.filter((row) => row.event_key !== "risk:settlement-control-change:362102");
    }],
    ["duplicate per-version event", "CR_SETTLEMENT_CONTROL_EVENT", (rows) => rows.compensation_event.push({
      ...rows.compensation_event[1], id: "370103",
    })],
    ["event key", "CR_SETTLEMENT_CONTROL_EVENT", (rows) => {
      rows.compensation_event[0].event_key = "risk:settlement-control-change:999999";
    }],
    ["event type", "CR_SETTLEMENT_CONTROL_EVENT", (rows) => {
      rows.compensation_event[0].event_type = "WrongEvent";
    }],
    ["event sub type", "CR_SETTLEMENT_CONTROL_EVENT", (rows) => {
      rows.compensation_event[0].sub_event_type = "WrongEvent";
    }],
    ["null Scope currency", "CR_SETTLEMENT_CONTROL_CURRENCY", (rows) => {
      rows.settlement_control_scope[0].currency = null;
    }],
    ["mismatched Scope currency", "CR_SETTLEMENT_CONTROL_CURRENCY", (rows) => {
      rows.settlement_control_scope[0].currency = "EUR";
    }],
    ["missing Assessment", "CR_SETTLEMENT_ASSESSMENT_DOMAIN", (rows) => {
      rows.settlement_risk_assessment = rows.settlement_risk_assessment.filter((row) => row.evaluation_id !== "310101");
    }],
    ["duplicate Assessment", "CR_SETTLEMENT_ASSESSMENT_DOMAIN", (rows) => rows.settlement_risk_assessment.push({
      ...rows.settlement_risk_assessment[0], assessment_id: "321102",
    })],
    ["checkpoint does not match Point", "CR_SETTLEMENT_ASSESSMENT_DOMAIN", (rows) => {
      rows.settlement_risk_assessment[0].checkpoint_type = "PAYOUT_FINAL_APPROVAL";
    }],
    ["Assessment input hash does not match Evaluation", "CR_SETTLEMENT_ASSESSMENT_DOMAIN", (rows) => {
      rows.settlement_risk_assessment[0].input_hash = hashOf("9");
    }],
    ["ALLOW Assessment contradicts DENY business decision", "CR_SETTLEMENT_ASSESSMENT_DOMAIN", (rows) => {
      rows.settlement_risk_assessment[0].eligibility_decision = "ALLOW";
    }],
    ["RESTRICT Assessment contradicts ALLOW business decision", "CR_SETTLEMENT_ASSESSMENT_DOMAIN", (rows) => {
      rows.decision_evaluation.find((row) => row.evaluation_id === "310101").business_decision = "ALLOW";
    }],
    ["missing Eligibility result after successful ALLOW proposal", "CR_SETTLEMENT_ASSESSMENT_DOMAIN", (rows) => {
      rows.settlement_risk_assessment[0].eligibility_decision = null;
    }],
    ["technical failure cannot expose an Eligibility result", "CR_SETTLEMENT_ASSESSMENT_DOMAIN", (rows) => {
      const evaluation = rows.decision_evaluation.find((row) => row.evaluation_id === "310101");
      evaluation.technical_status = "FAILED";
      evaluation.proposed_decision = null;
      evaluation.business_decision = null;
    }],
  ];
  for (const [label, errorCode, mutate] of cases) {
    const fixture = buildValidSettlementDecisionFixture();
    mutate(fixture.crossRowRows);
    const errors = validateDecisionRelease(fixture);
    assertRejectedBy(errors, "REFERENCE_INTEGRITY", errorCode);
    assert.ok(errors.length > 0, `${label} 必须独立失败`);
  }
});

test("Risk Limit bindings belong to typed obligations and allow an empty atomic group", () => {
  const noConfiguredLimits = buildValidDecisionFixture();
  noConfiguredLimits.limitBindings = [];
  assert.deepEqual(
    validateDecisionRelease(noConfiguredLimits),
    [],
    "RESERVE_LIMIT_GROUP 可在零条 Binding 时创建空 Group，不应伪造 Limit Version",
  );

  const actionWithBinding = buildValidDecisionFixture();
  actionWithBinding.obligations[0].obligationType = "ACTION";
  actionWithBinding.obligations[0].actionVersionId = "240011";
  assertRejectedBy(
    validateDecisionRelease(actionWithBinding),
    "SYSTEM_OBLIGATION",
    "LIMIT_BINDING_ACTION_FORBIDDEN",
  );

  const foreignBinding = buildValidDecisionFixture();
  foreignBinding.limitBindings[0].obligationId = "259999";
  assertRejectedBy(
    validateDecisionRelease(foreignBinding),
    "REFERENCE_INTEGRITY",
    "LIMIT_BINDING_REFERENCE_INVALID",
  );

  const duplicateGroupOwner = buildValidDecisionFixture();
  duplicateGroupOwner.decisionPoint.pointCode = "CHECKOUT_PREPARE";
  duplicateGroupOwner.obligations.push({
    obligationId: "250002", pointVersionId: "210011", triggerDecision: "ALLOW",
    obligationType: "RESERVE_LIMIT_GROUP", actionVersionId: null, sequenceNo: 2,
    failurePolicy: "FAIL_CLOSED", enabledFlag: true,
  });
  assertRejectedBy(
    validateDecisionRelease(duplicateGroupOwner),
    "SYSTEM_OBLIGATION",
    "LIMIT_OBLIGATION_AMBIGUOUS",
  );
});

test("Risk Limit versions freeze executable shape, canonical content and code semantics", () => {
  const invalidShapes = [
    (row) => { row.versionNo = 0; },
    (row) => { row.windowSize = 0; },
    (row) => { row.amountLimit = null; },
    (row) => { row.countLimit = "1"; },
    (row) => { row.currencyMode = "NO_CURRENCY"; },
    (row) => { row.fixedCurrency = "USD"; },
    (row) => { row.frozenBy = null; },
    (row) => { row.frozenTime = "2026-08-27T09:00:00Z"; },
  ];
  for (const mutate of invalidShapes) {
    const invalidShape = buildValidDecisionFixture();
    mutate(invalidShape.limitVersions[0]);
    assertRejectedBy(
      validateDecisionRelease(invalidShape),
      "SYSTEM_OBLIGATION",
      "LIMIT_VERSION_SHAPE_INVALID",
    );
  }

  const rewrittenFrozenVersion = buildValidDecisionFixture();
  rewrittenFrozenVersion.limitVersions[0].amountLimit = "1200.00000000";
  assertRejectedBy(
    validateDecisionRelease(rewrittenFrozenVersion),
    "SYSTEM_OBLIGATION",
    "LIMIT_VERSION_CONTENT_HASH_MISMATCH",
  );

  const malformedHash = buildValidDecisionFixture();
  malformedHash.limitVersions[0].contentHash = hashOf("A");
  assertRejectedBy(
    validateDecisionRelease(malformedHash),
    "SYSTEM_OBLIGATION",
    "LIMIT_VERSION_CONTENT_HASH_INVALID",
  );

  const duplicateCodeVersion = buildValidDecisionFixture();
  duplicateCodeVersion.limitVersions.push({
    ...duplicateCodeVersion.limitVersions[0],
    limitVersionId: "250012",
  });
  assertRejectedBy(
    validateDecisionRelease(duplicateCodeVersion),
    "SYSTEM_OBLIGATION",
    "LIMIT_VERSION_DUPLICATE_CODE_VERSION",
  );

  const changedCodeSemantics = buildValidDecisionFixture();
  changedCodeSemantics.limitVersions.push({
    ...changedCodeSemantics.limitVersions[0],
    limitVersionId: "250012",
    versionNo: 2,
    subjectType: "CLIENT",
    contentHash: "99d70270ae6c96d4a59d060a8a309d73d9a3a19f80dda6bcd6af8cc460906033",
  });
  assertRejectedBy(
    validateDecisionRelease(changedCodeSemantics),
    "SYSTEM_OBLIGATION",
    "LIMIT_VERSION_CODE_SEMANTICS_CHANGED",
  );
});

test("Task 5 rejects every frozen configuration check code", () => {
  const cases = [
    {
      checkCode: "REFERENCE_INTEGRITY",
      errorCode: "FEATURE_NOT_ALLOWLISTED",
      mutate: (fixture) => fixture.pointFeatures.splice(0, 1),
    },
    {
      checkCode: "AST_SHAPE",
      errorCode: "AST_ROOT_COUNT",
      mutate: (fixture) => fixture.nodes.push({ nodeId: "230099", ruleId: "230021", parentNodeId: null, nodeType: "PREDICATE", groupOperator: null, comparisonOperator: "IS_NULL", sequenceNo: 2 }),
    },
    {
      checkCode: "AST_TYPES",
      errorCode: "AST_OPERAND_TYPE_MISMATCH",
      mutate: (fixture) => Object.assign(fixture.operands.find((row) => row.operandId === "231002"), { valueType: "NUMBER", stringValue: undefined, numberValue: "1" }),
    },
    {
      checkCode: "FEATURE_DEPENDENCY",
      errorCode: "FEATURE_DEPENDENCY_CYCLE",
      mutate: (fixture) => fixture.featureDependencies.push({ dependencyId: "220022", featureVersionId: "220011", dependencyFeatureVersionId: "220012", sequenceNo: 1, requiredFlag: true }),
    },
    {
      checkCode: "HANDLER_HASH",
      errorCode: "HANDLER_HASH_MISMATCH",
      mutate: (fixture) => { fixture.handlerDeployments.find((row) => row.handlerCode === "AddListEntryAction").implementationHash = hashOf("f"); },
    },
    {
      checkCode: "ACTION_PARAMETERS",
      errorCode: "ACTION_PARAMETER_MISSING",
      mutate: (fixture) => { fixture.actionParameters = fixture.actionParameters.filter((row) => row.parameterDefinitionId !== "241001"); },
    },
    {
      checkCode: "TERMINAL_ACTION",
      errorCode: "TERMINAL_ACTION_MULTIPLE",
      mutate: (fixture) => Object.assign(fixture.actionVersions.find((row) => row.actionVersionId === "240012"), { terminalFlag: true, decisionCode: "DENY" }),
    },
    {
      checkCode: "SYSTEM_OBLIGATION",
      errorCode: "REQUIRED_OBLIGATION_MISSING",
      mutate: (fixture) => { fixture.obligations = []; },
    },
  ];

  for (const invalidCase of cases) {
    const fixture = buildValidDecisionFixture();
    invalidCase.mutate(fixture);
    assertRejectedBy(validateDecisionRelease(fixture), invalidCase.checkCode, invalidCase.errorCode);
  }
  assert.deepEqual(CONFIG_CHECK_CODES, cases.map(({ checkCode }) => checkCode));
});

test("Task 5 rejects AST cycles, sequence gaps, arity errors, and unauthorized references", () => {
  const cases = [
    {
      checkCode: "AST_SHAPE",
      errorCode: "AST_CYCLE",
      mutate: (fixture) => fixture.nodes.push(
        { nodeId: "230090", ruleId: "230021", parentNodeId: "230091", nodeType: "GROUP", groupOperator: "NOT", comparisonOperator: null, sequenceNo: 1 },
        { nodeId: "230091", ruleId: "230021", parentNodeId: "230090", nodeType: "GROUP", groupOperator: "NOT", comparisonOperator: null, sequenceNo: 1 },
      ),
    },
    {
      checkCode: "AST_SHAPE",
      errorCode: "AST_CHILD_SEQUENCE",
      mutate: (fixture) => { fixture.nodes.find((row) => row.nodeId === "230037").sequenceNo = 4; },
    },
    {
      checkCode: "AST_SHAPE",
      errorCode: "AST_GROUP_ARITY",
      mutate: (fixture) => { fixture.nodes.find((row) => row.nodeId === "230035").groupOperator = "AND"; },
    },
    {
      checkCode: "AST_SHAPE",
      errorCode: "AST_OPERAND_ARITY",
      mutate: (fixture) => { fixture.operands = fixture.operands.filter((row) => row.operandId !== "231009"); },
    },
    {
      checkCode: "REFERENCE_INTEGRITY",
      errorCode: "CONTEXT_RULE_REFERENCE_FORBIDDEN",
      mutate: (fixture) => { fixture.inputFields.find((row) => row.inputFieldId === "210021").ruleReferenceFlag = false; },
    },
    {
      checkCode: "ACTION_PARAMETERS",
      errorCode: "CONTEXT_ACTION_PARAMETER_FORBIDDEN",
      mutate: (fixture) => { fixture.inputFields.find((row) => row.inputFieldId === "210022").actionParameterFlag = false; },
    },
  ];

  for (const invalidCase of cases) {
    const fixture = buildValidDecisionFixture();
    invalidCase.mutate(fixture);
    assertRejectedBy(validateDecisionRelease(fixture), invalidCase.checkCode, invalidCase.errorCode);
  }
});

test("Task 5 fails closed for malformed Payment Limit obligations", () => {
  const xor = buildValidDecisionFixture();
  xor.obligations[0].actionVersionId = "240011";
  assertRejectedBy(validateDecisionRelease(xor), "SYSTEM_OBLIGATION", "OBLIGATION_TARGET_XOR");

  const failurePolicy = buildValidDecisionFixture();
  failurePolicy.obligations[0].failurePolicy = "RETRY_ASYNC";
  assertRejectedBy(validateDecisionRelease(failurePolicy), "SYSTEM_OBLIGATION", "REQUIRED_OBLIGATION_FAILURE_POLICY");

});

test("Task 5 executes both cross-row contract types from registry metadata", () => {
  const valid = buildValidDecisionFixture();
  assert.deepEqual(validateDecisionRelease(valid), []);

  const invalidMapping = buildValidDecisionFixture();
  invalidMapping.crossRowRows.action_effect[0].effect_mode = "SIMULATED";
  assertRejectedBy(validateDecisionRelease(invalidMapping), "REFERENCE_INTEGRITY", "CR_EVALUATION_EFFECT_MODE");

  const pathCases = [
    ["CR_SIMULATED_EFFECT_RISK_RESTRICTION", (rows) => rows.risk_restriction.push({ restriction_id: "1", source_effect_id: "315002" })],
    ["CR_SIMULATED_EFFECT_RISK_LIST_ENTRY", (rows) => rows.risk_list_entry.push({ entry_id: "1", source_effect_id: "315002" })],
    ["CR_SIMULATED_EFFECT_LIMIT_RESERVATION_GROUP", (rows) => {
      rows.transaction_risk_assessment.push({ assessment_id: "320001", evaluation_id: "310002" });
      rows.limit_reservation_group.push({ reservation_group_id: "330001", assessment_id: "320001" });
    }],
    ["CR_SIMULATED_EFFECT_LIMIT_RESERVATION", (rows) => {
      rows.transaction_risk_assessment.push({ assessment_id: "320001", evaluation_id: "310002" });
      rows.limit_reservation_group.push({ reservation_group_id: "330001", assessment_id: "320001" });
      rows.limit_reservation.push({ reservation_id: "340001", reservation_group_id: "330001" });
    }],
    ["CR_SIMULATED_EFFECT_LIMIT_MOVEMENT", (rows) => {
      rows.transaction_risk_assessment.push({ assessment_id: "320001", evaluation_id: "310002" });
      rows.limit_reservation_group.push({ reservation_group_id: "330001", assessment_id: "320001" });
      rows.limit_reservation.push({ reservation_id: "340001", reservation_group_id: "330001" });
      rows.limit_movement.push({ movement_id: "350001", reservation_id: "340001" });
    }],
    ["CR_SIMULATED_EFFECT_DISPUTE_LIABILITY", (rows) => rows.dispute_liability_decision.push(
      disputeLiabilityDecisionRow({
        outcome_id: "350102",
        decision_evaluation_id: "310002",
        action_effect_id: "315002",
        decided_by: null,
      }),
    )],
    ["CR_SIMULATED_EFFECT_COMPENSATION_EVENT", (rows) => rows.compensation_event.push({
      id: "1",
      event_key: rows.action_effect.find(({ effect_id }) => effect_id === "315002").idempotency_key,
      event_type: "EXECUTE_RISK_ACTION_EFFECT",
      sub_event_type: "APPLY_ASYNC_ACTION_EFFECT",
    })],
    ["CR_SIMULATED_EFFECT_VELOCITY_EVENT", (rows) => rows.velocity_event.push({ velocity_event_id: "1", source_type: "CHECKOUT_EVALUATION", source_id: "310002" })],
  ];
  for (const [errorCode, mutate] of pathCases) {
    const fixture = buildValidDecisionFixture();
    mutate(fixture.crossRowRows);
    assertRejectedBy(validateDecisionRelease(fixture), "REFERENCE_INTEGRITY", errorCode);
  }

  const discriminatorMismatch = buildValidDecisionFixture();
  discriminatorMismatch.crossRowRows.compensation_event.push({
    id: "1",
    event_key: discriminatorMismatch.crossRowRows.action_effect.find(({ effect_id }) => effect_id === "315002").idempotency_key,
    event_type: "PUBLISH_SETTLEMENT_CONTROL_EVENT",
    sub_event_type: "SettlementControlChanged",
  });
  discriminatorMismatch.crossRowRows.velocity_event.push({ velocity_event_id: "1", source_type: "COMMAND", source_id: "310002" });
  assert.deepEqual(validateDecisionRelease(discriminatorMismatch), []);

  const missingRows = buildValidDecisionFixture();
  delete missingRows.crossRowRows.velocity_event;
  assertRejectedBy(validateDecisionRelease(missingRows), "REFERENCE_INTEGRITY", "CR_POLICY_MISSING_ROWS");
});

test("Risk Evaluation never creates an Effect after technical failure", () => {
  for (const [evaluationId, technicalStatus] of [
    ["310001", "FAILED"],
    ["310002", "TIMED_OUT"],
    ["310003", "FAILED"],
  ]) {
    const fixture = buildValidDecisionFixture();
    fixture.crossRowRows.decision_evaluation
      .find((evaluation) => evaluation.evaluation_id === evaluationId)
      .technical_status = technicalStatus;
    assertRejectedBy(
      validateDecisionRelease(fixture),
      "REFERENCE_INTEGRITY",
      "CR_EVALUATION_EFFECT_MODE",
    );
  }
});

test("Risk Evaluation binds every Intent source to its exact Point Version", () => {
  const foreignObligation = buildValidDecisionFixture();
  const obligationIntent = foreignObligation.crossRowRows.evaluation_action_intent[0];
  obligationIntent.source_type = "OBLIGATION";
  delete obligationIntent.rule_action_id;
  obligationIntent.obligation_id = "250101";
  foreignObligation.crossRowRows.decision_point_obligation.push({
    obligation_id: "250101",
    point_version_id: "210099",
    obligation_type: "ACTION",
    action_version_id: "240011",
  });
  assertRejectedBy(
    validateDecisionRelease(foreignObligation),
    "REFERENCE_INTEGRITY",
    "CR_INTENT_OBLIGATION_SOURCE",
  );

  const foreignRuleSet = buildValidDecisionFixture();
  foreignRuleSet.crossRowRows.decision_point_rule_set[0].point_version_id = "210099";
  assertRejectedBy(
    validateDecisionRelease(foreignRuleSet),
    "REFERENCE_INTEGRITY",
    "CR_INTENT_RULE_SOURCE",
  );

  const reusedRuleSet = buildValidDecisionFixture();
  reusedRuleSet.crossRowRows.decision_point_rule_set.push({
    binding_id: "211002",
    point_version_id: "210099",
    rule_set_version_id: "230011",
  });
  assert.deepEqual(
    validateDecisionRelease(reusedRuleSet),
    [],
    "同一冻结 Rule Set 可复用于多个 Point Version，但本次版本必须有准确 Binding",
  );
});

test("Risk Evaluation rejects inconsistent Intent sources, merged Effects, and default decisions", () => {
  const validObligation = buildValidDecisionFixture();
  const obligationIntent = validObligation.crossRowRows.evaluation_action_intent[0];
  obligationIntent.source_type = "OBLIGATION";
  delete obligationIntent.rule_action_id;
  obligationIntent.obligation_id = "250101";
  validObligation.crossRowRows.decision_point_obligation.push({
    obligation_id: "250101", point_version_id: "210011", obligation_type: "ACTION", action_version_id: "240011",
  });
  assert.deepEqual(validateDecisionRelease(validObligation), []);

  const pureLimitObligation = buildValidDecisionFixture();
  pureLimitObligation.crossRowRows.decision_point_obligation.push({
    obligation_id: "250102", point_version_id: "210011", obligation_type: "RESERVE_LIMIT_GROUP", action_version_id: null,
  });
  assert.deepEqual(
    validateDecisionRelease(pureLimitObligation),
    [],
    "RESERVE_LIMIT_GROUP 系统义务不得被强制伪造成 Action Intent",
  );

  const invalidCases = [
    ["CR_INTENT_RULE_SOURCE", (rows) => {
      rows.action_version.push({ action_version_id: "240012", merge_policy: "MAX" });
      rows.evaluation_action_intent[0].action_version_id = "240012";
    }],
    ["CR_INTENT_OBLIGATION_SOURCE", (rows) => {
      const intent = rows.evaluation_action_intent[0];
      intent.source_type = "OBLIGATION";
      delete intent.rule_action_id;
      intent.obligation_id = "250101";
      rows.decision_point_obligation.push({ obligation_id: "250101", point_version_id: "210011", obligation_type: "ACTION", action_version_id: "240012" });
    }],
    ["CR_MERGED_INTENT_EFFECT", (rows) => {
      rows.action_effect[0].target_key = "consumer-C-other";
    }],
    ["CR_MERGED_INTENT_EFFECT", (rows) => {
      rows.action_effect[0].merge_policy = "FIRST";
    }],
    ["CR_EFFECT_HAS_MERGED_INTENT", (rows) => {
      rows.evaluation_action_intent = rows.evaluation_action_intent
        .filter((intent) => intent.merged_effect_id !== "315001");
    }],
    ["CR_EVALUATION_DEFAULT_DECISION", (rows) => {
      rows.decision_evaluation[0].default_decision_used_flag = true;
    }],
  ];
  for (const [errorCode, mutate] of invalidCases) {
    const fixture = buildValidDecisionFixture();
    mutate(fixture.crossRowRows);
    assertRejectedBy(validateDecisionRelease(fixture), "REFERENCE_INTEGRITY", errorCode);
  }

  const validDefault = buildValidDecisionFixture();
  validDefault.crossRowRows.decision_evaluation[0].default_decision_used_flag = true;
  validDefault.crossRowRows.decision_evaluation[0].proposed_decision = "DENY";
  assert.deepEqual(validateDecisionRelease(validDefault), []);
});

test("Risk Evaluation enforces exactly one input Snapshot family by Decision Point domain", () => {
  const validDomains = [
    ["CHECKOUT", "checkout_input_snapshot", "snapshot_id"],
    ["PAYMENT", "transaction_risk_assessment", "assessment_id"],
    ["SETTLEMENT", "settlement_risk_assessment", "assessment_id"],
  ];
  for (const [domain, table, idField] of validDomains) {
    const fixture = buildValidDecisionFixture();
    fixture.crossRowRows.decision_point[0].business_domain = domain;
    if (domain === "SETTLEMENT") {
      fixture.crossRowRows.decision_point[0].point_code = "PAYOUT_CREATION";
      for (const evaluation of fixture.crossRowRows.decision_evaluation) {
        evaluation.business_decision = "ALLOW";
      }
    }
    fixture.crossRowRows.checkout_input_snapshot = [];
    fixture.crossRowRows.transaction_risk_assessment = [];
    fixture.crossRowRows.settlement_risk_assessment = [];
    fixture.crossRowRows[table] = fixture.crossRowRows.decision_evaluation.map((evaluation, index) => (
      domain === "SETTLEMENT"
        ? {
          [idField]: String(321101 + index),
          evaluation_id: evaluation.evaluation_id,
          checkpoint_type: "PAYOUT_CREATION",
          input_hash: evaluation.input_snapshot_hash,
          eligibility_decision: "ALLOW",
        }
        : {
          [idField]: String(321101 + index),
          evaluation_id: evaluation.evaluation_id,
        }
    ));
    assert.deepEqual(validateDecisionRelease(fixture), [], `${domain} 应接受准确的一类输入快照`);
  }

  for (const domain of ["DUE_DILIGENCE", "DISPUTE"]) {
    const fixture = buildValidDecisionFixture();
    fixture.crossRowRows.decision_point[0].business_domain = domain;
    fixture.crossRowRows.transaction_risk_assessment = [];
    assert.deepEqual(validateDecisionRelease(fixture), [], `${domain} 不应要求交易输入快照`);
  }

  const invalidCases = [
    (rows) => { rows.transaction_risk_assessment = rows.transaction_risk_assessment.slice(1); },
    (rows) => rows.transaction_risk_assessment.push({ assessment_id: "321099", evaluation_id: "310001" }),
    (rows) => {
      rows.transaction_risk_assessment = rows.transaction_risk_assessment.filter((row) => row.evaluation_id !== "310001");
      rows.checkout_input_snapshot.push({ snapshot_id: "321099", evaluation_id: "310001" });
    },
    (rows) => rows.checkout_input_snapshot.push({ snapshot_id: "321099", evaluation_id: "310001" }),
    (rows) => rows.settlement_risk_assessment.push({ assessment_id: "321099", evaluation_id: "319999" }),
    (rows) => {
      rows.decision_point[0].business_domain = "DISPUTE";
    },
  ];
  for (const mutate of invalidCases) {
    const fixture = buildValidDecisionFixture();
    mutate(fixture.crossRowRows);
    assertRejectedBy(
      validateDecisionRelease(fixture),
      "REFERENCE_INTEGRITY",
      "CR_EVALUATION_SNAPSHOT_DOMAIN",
    );
  }
});

test("Task 5 emits sorted deduplicated errors independent of input order", () => {
  const fixture = buildValidDecisionFixture();
  fixture.nodes.push({ nodeId: "230099", ruleId: "230021", parentNodeId: null, nodeType: "PREDICATE", groupOperator: null, comparisonOperator: "IS_NULL", sequenceNo: 2 });
  fixture.handlerDeployments.find((row) => row.handlerCode === "CooldownSubjectAction").implementationHash = hashOf("f");
  const before = structuredClone(fixture);
  const first = validateDecisionRelease(fixture);
  const second = validateDecisionRelease(fixture);
  const shuffled = structuredClone(fixture);
  for (const value of Object.values(shuffled)) if (Array.isArray(value)) value.reverse();
  for (const rows of Object.values(shuffled.crossRowRows)) rows.reverse();

  assert.deepEqual(first, second);
  assert.deepEqual(first, validateDecisionRelease(shuffled));
  assert.deepEqual(fixture, before);
  assert.ok(first.length >= 2);
  for (const error of first) {
    assert.equal(error.severity, "ERROR");
    assert.equal(error.release_id, "260002");
    assert.ok(CONFIG_CHECK_CODES.includes(error.check_code));
    assert.deepEqual(Object.keys(error).filter((key) => error[key] === undefined), []);
  }
});

test("Task 5 accepts the complete reviewed release and atomic activation", () => {
  const fixture = buildValidTransitionFixture();
  const before = structuredClone(fixture);
  assert.deepEqual(validateReleaseTransition(fixture), []);
  assert.deepEqual(validateReleaseTransition(fixture), []);
  assert.deepEqual(fixture, before);

  const shuffled = structuredClone(fixture);
  shuffled.auditEvents.reverse();
  shuffled.relatedReleasesAfter.reverse();
  assert.deepEqual(validateReleaseTransition(shuffled), []);
});

test("Task 5 rejects each frozen release gate", () => {
  const cases = [
    {
      checkCode: "SHADOW_GATE",
      errorCode: "STATIC_CHECK_MISSING",
      mutate: (fixture) => { fixture.auditEvents = fixture.auditEvents.filter((event) => event.checkCode !== "AST_TYPES"); },
    },
    {
      checkCode: "APPROVAL_GATE",
      errorCode: "APPROVAL_DISTINCT_REVIEWERS_INSUFFICIENT",
      mutate: (fixture) => { fixture.auditEvents.find((event) => event.approvalRequestId === "approval-2").reviewerId = "900001"; },
    },
    {
      checkCode: "STATE_TRANSITION",
      errorCode: "STATE_EDGE_FORBIDDEN",
      mutate: (fixture) => {
        const event = fixture.auditEvents.find((row) => row.eventType === "STATE_CHANGE" && row.aggregateVersion === 2);
        event.toStatus = "ACTIVE";
      },
    },
    {
      checkCode: "ACTIVATION_PAIR",
      errorCode: "ACTIVATION_TRIGGER_MISMATCH",
      mutate: (fixture) => { fixture.activation.previousStateEvent.triggerId = "different-trigger"; },
    },
  ];
  for (const invalidCase of cases) {
    const fixture = buildValidTransitionFixture();
    invalidCase.mutate(fixture);
    assertRejectedBy(validateReleaseTransition(fixture), invalidCase.checkCode, invalidCase.errorCode);
  }
  assert.deepEqual(STATE_CHECK_CODES, cases.map(({ checkCode }) => checkCode));
});

test("Task 5 rejects approval request reuse, state version gaps, and activation bootstrap", () => {
  const requestReuse = buildValidTransitionFixture();
  requestReuse.auditEvents.find((event) => event.approvalRequestId === "approval-2").approvalRequestId = "approval-1";
  assertRejectedBy(validateReleaseTransition(requestReuse), "APPROVAL_GATE", "APPROVAL_REQUEST_CONFLICT");

  const versionGap = buildValidTransitionFixture();
  versionGap.auditEvents.find((row) => row.eventType === "STATE_CHANGE" && row.aggregateVersion === 4).aggregateVersion = 9;
  assertRejectedBy(validateReleaseTransition(versionGap), "STATE_TRANSITION", "STATE_VERSION_GAP");

  const bootstrap = buildValidTransitionFixture();
  bootstrap.activation.previousBefore = null;
  bootstrap.activation.previousAfter = null;
  bootstrap.activation.previousStateEvent = null;
  assertRejectedBy(validateReleaseTransition(bootstrap), "ACTIVATION_PAIR", "ACTIVATION_PAIR_MISSING");
});

function buildReviewPendingTransitionFixture() {
  const fixture = buildValidTransitionFixture();
  fixture.candidateBefore = { ...fixture.candidateBefore, status: "SHADOWING", activeSlot: null, version: 3 };
  fixture.candidateAfter = { ...fixture.candidateBefore, status: "REVIEW_PENDING", activeSlot: null, version: 4 };
  fixture.auditEvents = fixture.auditEvents.filter((event) => (
    event.eventType === "STATIC_CHECK"
    || (event.eventType === "STATE_CHANGE" && event.releaseId === "260002" && event.aggregateVersion <= 4)
  ));
  fixture.relatedReleasesAfter = [fixture.candidateAfter, fixture.relatedReleasesBefore[0]];
  fixture.activation = null;
  return fixture;
}

test("Task 5 fix preserves gates after a release reaches a terminal state", () => {
  const rejected = buildValidTransitionFixture();
  rejected.candidateBefore = { ...rejected.candidateBefore, status: "SHADOWING", activeSlot: null, version: 3 };
  rejected.candidateAfter = { ...rejected.candidateBefore, status: "REJECTED", activeSlot: null, version: 4 };
  rejected.auditEvents = [
    ...rejected.auditEvents.filter((event) => event.eventType === "STATE_CHANGE" && event.releaseId === "260002" && event.aggregateVersion <= 3),
    { auditEventId: "280104", eventType: "STATE_CHANGE", eventTime: "2026-08-27T09:00:04.000000Z", releaseId: "260002", aggregateVersion: 4, fromStatus: "SHADOWING", toStatus: "REJECTED", triggerType: "COMMAND", triggerId: "reject-1" },
  ];
  rejected.relatedReleasesAfter = [rejected.candidateAfter, rejected.relatedReleasesBefore[0]];
  rejected.activation = null;
  assertRejectedBy(validateReleaseTransition(rejected), "SHADOW_GATE", "STATIC_CHECK_MISSING");

  const superseded = buildValidTransitionFixture();
  superseded.candidateBefore = { ...superseded.candidateAfter };
  superseded.candidateAfter = { ...superseded.candidateAfter, status: "SUPERSEDED", activeSlot: null, version: 7 };
  superseded.auditEvents = [
    ...superseded.auditEvents.filter((event) => event.eventType !== "APPROVAL" && event.releaseId === "260002"),
    { auditEventId: "280107", eventType: "STATE_CHANGE", eventTime: "2026-08-27T11:00:00.000000Z", releaseId: "260002", aggregateVersion: 7, fromStatus: "ACTIVE", toStatus: "SUPERSEDED", triggerType: "COMMAND", triggerId: "supersede-1" },
  ];
  superseded.relatedReleasesAfter = [superseded.candidateAfter];
  superseded.activation = null;
  assertRejectedBy(validateReleaseTransition(superseded), "APPROVAL_GATE", "APPROVAL_DISTINCT_REVIEWERS_INSUFFICIENT");
});

test("Task 5 fix validates every Shadow comparison against its PRIMARY baseline and run", () => {
  const cases = [
    (rows) => { rows.decision_evaluation.find((row) => row.evaluation_id === "310002").baseline_evaluation_id = "399999"; },
    (rows) => { rows.decision_evaluation.find((row) => row.evaluation_id === "310001").decision_point_id = "219999"; },
    (rows) => { rows.decision_evaluation.find((row) => row.evaluation_id === "310001").evaluation_time = "2026-08-27T10:00:01.000000Z"; },
    (rows) => { rows.decision_evaluation.find((row) => row.evaluation_id === "310001").input_snapshot_hash = hashOf("2"); },
    (rows) => { rows.release_shadow_run[0].candidate_release_id = "269999"; },
    (rows) => { rows.release_shadow_run[0].baseline_release_id = "269998"; },
    (rows) => { rows.decision_evaluation.find((row) => row.evaluation_id === "310002").shadow_run_id = "279999"; },
    (rows) => {
      rows.decision_evaluation.push({ evaluation_id: "310004", evaluation_mode: "REPLAY", decision_point_id: "210001", point_version_id: "210011", release_id: "260001", evaluation_time: "2026-08-27T10:00:00.000000Z", input_snapshot_hash: hashOf("1"), proposed_decision: "ALLOW", default_decision_used_flag: false });
      rows.transaction_risk_assessment.push({ assessment_id: "321004", evaluation_id: "310004" });
      rows.decision_evaluation.find((row) => row.evaluation_id === "310002").baseline_evaluation_id = "310004";
    },
  ];
  for (const mutate of cases) {
    const fixture = buildValidDecisionFixture();
    mutate(fixture.crossRowRows);
    assertRejectedBy(validateDecisionRelease(fixture), "REFERENCE_INTEGRITY", "CR_SHADOW_COMPARISON");
  }
});

test("Task 5 fix roots no-side-effect checks at Shadow and Replay Evaluations", () => {
  const zeroEffectLimit = buildValidDecisionFixture();
  zeroEffectLimit.crossRowRows.action_effect = zeroEffectLimit.crossRowRows.action_effect.filter((row) => row.effect_id !== "315002");
  zeroEffectLimit.crossRowRows.transaction_risk_assessment.push({ assessment_id: "320001", evaluation_id: "310002" });
  zeroEffectLimit.crossRowRows.limit_reservation_group.push({ reservation_group_id: "330001", assessment_id: "320001" });
  assertRejectedBy(validateDecisionRelease(zeroEffectLimit), "REFERENCE_INTEGRITY", "CR_SIMULATED_EFFECT_LIMIT_RESERVATION_GROUP");

  const zeroEffectVelocity = buildValidDecisionFixture();
  zeroEffectVelocity.crossRowRows.action_effect = zeroEffectVelocity.crossRowRows.action_effect.filter((row) => row.effect_id !== "315003");
  zeroEffectVelocity.crossRowRows.velocity_event.push({ velocity_event_id: "1", source_type: "CHECKOUT_EVALUATION", source_id: "310003" });
  assertRejectedBy(validateDecisionRelease(zeroEffectVelocity), "REFERENCE_INTEGRITY", "CR_SIMULATED_EFFECT_VELOCITY_EVENT");

  const orphanEffect = buildValidDecisionFixture();
  orphanEffect.crossRowRows.action_effect.push({ effect_id: "315099", evaluation_id: "319999", effect_mode: "REAL" });
  assertRejectedBy(validateDecisionRelease(orphanEffect), "REFERENCE_INTEGRITY", "CR_EVALUATION_EFFECT_MODE");

  const closedVelocityType = buildValidDecisionFixture();
  closedVelocityType.crossRowRows.velocity_event.push({ velocity_event_id: "1", source_type: "PAYMENT_EVALUATION", source_id: "310002" });
  assert.deepEqual(validateDecisionRelease(closedVelocityType), []);
});

test("Task 5 fix requires both activation events and immutable release identities", () => {
  const missingPreviousEvent = buildValidTransitionFixture();
  missingPreviousEvent.auditEvents = missingPreviousEvent.auditEvents.filter((event) => event !== missingPreviousEvent.activation.previousStateEvent);
  assertRejectedBy(validateReleaseTransition(missingPreviousEvent), "ACTIVATION_PAIR", "ACTIVATION_EVENT_MISMATCH");

  const switchedCandidate = buildValidTransitionFixture();
  switchedCandidate.candidateAfter.pointVersionId = "210099";
  switchedCandidate.activation.candidateAfter.pointVersionId = "210099";
  switchedCandidate.relatedReleasesAfter.find((release) => release.releaseId === "260002").pointVersionId = "210099";
  assertRejectedBy(validateReleaseTransition(switchedCandidate), "ACTIVATION_PAIR", "ACTIVATION_IDENTITY_MISMATCH");

  const switchedPrevious = buildValidTransitionFixture();
  switchedPrevious.activation.previousAfter.releaseId = "260099";
  switchedPrevious.activation.previousStateEvent.releaseId = "260099";
  switchedPrevious.relatedReleasesAfter.find((release) => release.status === "SUPERSEDED").releaseId = "260099";
  assertRejectedBy(validateReleaseTransition(switchedPrevious), "ACTIVATION_PAIR", "ACTIVATION_IDENTITY_MISMATCH");
});

test("Task 5 fix requires frozen Rule Sets and a complete normalized closure", () => {
  const draftRuleSet = buildValidDecisionFixture();
  draftRuleSet.ruleSetVersions[0].status = "DRAFT";
  assertRejectedBy(validateDecisionRelease(draftRuleSet), "REFERENCE_INTEGRITY", "RULE_SET_VERSION_NOT_FROZEN");

  const closureCases = [
    ["REFERENCE_INTEGRITY", "REFERENCE_COLLECTION_MISSING", (fixture) => { delete fixture.featureDependencies; }],
    ["REFERENCE_INTEGRITY", "REFERENCE_MISSING", (fixture) => fixture.featureDependencies.push({ dependencyId: "220099", featureVersionId: "229999", dependencyFeatureVersionId: "220011", sequenceNo: 1, requiredFlag: true })],
    ["REFERENCE_INTEGRITY", "REFERENCE_MISSING", (fixture) => fixture.parameterDefinitions.push({ parameterDefinitionId: "241099", actionVersionId: "249999", parameterCode: "orphan", valueType: "STRING", requiredFlag: false, allowedSourceType: "LITERAL", targetParameterFlag: false })],
    ["REFERENCE_INTEGRITY", "REFERENCE_MISSING", (fixture) => fixture.actionParameters.push({ parameterId: "243099", ruleActionId: "249999", parameterDefinitionId: "241001", valueSequence: 1, sourceType: "LITERAL", durationValue: "1" })],
    ["REFERENCE_INTEGRITY", "LIMIT_BINDING_REFERENCE_INVALID", (fixture) => fixture.limitBindings.push({ bindingId: "250099", obligationId: "259999", limitVersionId: "250011", sequenceNo: 1, subjectSourceType: "CONTEXT", subjectInputFieldId: "210022", amountSourceType: "FEATURE", amountFeatureVersionId: "220012" })],
    ["REFERENCE_INTEGRITY", "REFERENCE_DUPLICATE_BINDING", (fixture) => fixture.pointFeatures.push({ bindingId: "220199", pointVersionId: "210011", featureVersionId: "220011", sequenceNo: 3 })],
    ["REFERENCE_INTEGRITY", "REFERENCE_MISSING_ID", (fixture) => { delete fixture.pointFeatures[0].bindingId; }],
    ["AST_SHAPE", "AST_GROUP_HAS_OPERANDS", (fixture) => fixture.operands.push({ operandId: "231099", nodeId: "230031", operandRole: "LEFT", valueSequence: 1, sourceType: "LITERAL", valueType: "STRING", stringValue: "illegal" })],
  ];
  for (const [checkCode, errorCode, mutate] of closureCases) {
    const fixture = buildValidDecisionFixture();
    mutate(fixture);
    assertRejectedBy(validateDecisionRelease(fixture), checkCode, errorCode);
  }
});

test("Task 5 fix enforces Action Parameter source XOR", () => {
  const contextWithFeature = buildValidDecisionFixture();
  contextWithFeature.actionParameters.find((row) => row.parameterId === "243002").featureVersionId = "220011";
  assertRejectedBy(validateDecisionRelease(contextWithFeature), "ACTION_PARAMETERS", "ACTION_PARAMETER_VALUE_SHAPE");

  const featureWithContext = buildValidDecisionFixture();
  const definition = featureWithContext.parameterDefinitions.find((row) => row.parameterDefinitionId === "241002");
  definition.allowedSourceType = "FEATURE";
  const parameter = featureWithContext.actionParameters.find((row) => row.parameterId === "243002");
  parameter.sourceType = "FEATURE";
  parameter.featureVersionId = "220011";
  assertRejectedBy(validateDecisionRelease(featureWithContext), "ACTION_PARAMETERS", "ACTION_PARAMETER_VALUE_SHAPE");
});

test("Task 5 fix groups Obligation sequence and validates every Limit target", () => {
  const grouped = buildValidDecisionFixture();
  grouped.actionVersions.push({
    actionVersionId: "240014", actionCode: "WRITE_AUDIT_NOTE",
    handlerCode: "WriteAuditNoteAction", implementationVersion: "1.0.0",
    implementationHash: hashOf("f"), terminalFlag: false, decisionCode: null,
    shadowSupportedFlag: true, status: "ACTIVE",
  });
  grouped.handlerDeployments.push({
    handlerType: "ACTION", handlerCode: "WriteAuditNoteAction",
    implementationVersion: "1.0.0", implementationHash: hashOf("f"), deploymentStatus: "DEPLOYED",
  });
  grouped.obligations.push({ obligationId: "250002", pointVersionId: "210011", triggerDecision: "DENY", obligationType: "ACTION", actionVersionId: "240014", sequenceNo: 1, failurePolicy: "FAIL_CLOSED", enabledFlag: true });
  assert.deepEqual(validateDecisionRelease(grouped), []);

  const crossPoint = buildValidDecisionFixture();
  crossPoint.limitBindings[0].subjectInputFieldId = "210099";
  crossPoint.inputFields.push({ inputFieldId: "210099", pointVersionId: "219999", fieldCode: "foreign_consumer", valueType: "STRING", ruleReferenceFlag: true, actionParameterFlag: true });
  assertRejectedBy(validateDecisionRelease(crossPoint), "SYSTEM_OBLIGATION", "LIMIT_BINDING_POINT_MISMATCH");
});

test("Task 5 fix rejects non-string business and approval identities", () => {
  const numericRelease = buildValidDecisionFixture();
  numericRelease.release.releaseId = 260002;
  assertRejectedBy(validateDecisionRelease(numericRelease), "REFERENCE_INTEGRITY", "REFERENCE_ID_NOT_STRING");

  const numericReviewer = buildValidTransitionFixture();
  numericReviewer.auditEvents.find((event) => event.approvalRequestId === "approval-2").reviewerId = 900001;
  assertRejectedBy(validateReleaseTransition(numericReviewer), "APPROVAL_GATE", "APPROVAL_REVIEWER_INVALID");
  assertRejectedBy(validateReleaseTransition(numericReviewer), "APPROVAL_GATE", "APPROVAL_DISTINCT_REVIEWERS_INSUFFICIENT");

  const numericRequest = buildValidTransitionFixture();
  numericRequest.auditEvents.find((event) => event.approvalRequestId === "approval-1").approvalRequestId = 1;
  assertRejectedBy(validateReleaseTransition(numericRequest), "APPROVAL_GATE", "APPROVAL_REQUEST_INVALID");

  const numericShadowRun = buildValidTransitionFixture();
  numericShadowRun.selectedShadowRunId = 270001;
  numericShadowRun.shadowRuns[0].shadowRunId = 270001;
  for (const event of numericShadowRun.auditEvents.filter((row) => row.eventType === "APPROVAL")) event.shadowRunId = 270001;
  assertRejectedBy(validateReleaseTransition(numericShadowRun), "STATE_TRANSITION", "STATE_ID_NOT_STRING");

  const missingAuditIdentity = buildValidTransitionFixture();
  delete missingAuditIdentity.auditEvents[0].auditEventId;
  assertRejectedBy(validateReleaseTransition(missingAuditIdentity), "STATE_TRANSITION", "STATE_ID_MISSING");

  const duplicateShadowRun = buildValidTransitionFixture();
  duplicateShadowRun.shadowRuns.push(structuredClone(duplicateShadowRun.shadowRuns[0]));
  assertRejectedBy(validateReleaseTransition(duplicateShadowRun), "STATE_TRANSITION", "STATE_ID_DUPLICATE");

  const missingCollection = buildValidTransitionFixture();
  delete missingCollection.relatedReleasesAfter;
  assertRejectedBy(validateReleaseTransition(missingCollection), "STATE_TRANSITION", "STATE_COLLECTION_MISSING");
});

test("Task 5 fix validates ordinary transition mutation and root state", () => {
  assert.deepEqual(validateReleaseTransition(buildReviewPendingTransitionFixture()), []);
  const cases = [
    ["STATE_IDENTITY_MISMATCH", (fixture) => { fixture.candidateBefore.pointVersionId = "210099"; }],
    ["STATE_CAS_MISMATCH", (fixture) => { fixture.candidateBefore.version = 2; }],
    ["STATE_TRIGGER_INVALID", (fixture) => { fixture.auditEvents.find((event) => event.eventType === "STATE_CHANGE" && event.aggregateVersion === 4).triggerId = null; }],
    ["STATE_ACTIVE_SLOT_MISMATCH", (fixture) => { fixture.candidateAfter.activeSlot = 1; fixture.relatedReleasesAfter[0].activeSlot = 1; }],
  ];
  for (const [errorCode, mutate] of cases) {
    const fixture = buildReviewPendingTransitionFixture();
    mutate(fixture);
    assertRejectedBy(validateReleaseTransition(fixture), "STATE_TRANSITION", errorCode);
  }
});

test("Task 5 fix requires canonical SHA-256 Handler hashes", () => {
  const featureHash = buildValidDecisionFixture();
  featureHash.featureVersions[0].implementationHash = hashOf("g");
  featureHash.handlerDeployments.find((row) => row.handlerCode === "IpRiskLevelFeature").implementationHash = hashOf("g");
  assertRejectedBy(validateDecisionRelease(featureHash), "HANDLER_HASH", "HANDLER_HASH_FORMAT_INVALID");

  const actionHash = buildValidDecisionFixture();
  actionHash.actionVersions[0].implementationHash = "ABC";
  actionHash.handlerDeployments.find((row) => row.handlerCode === "CooldownSubjectAction").implementationHash = "ABC";
  assertRejectedBy(validateDecisionRelease(actionHash), "HANDLER_HASH", "HANDLER_HASH_FORMAT_INVALID");
});

test("Task 5 fix rejects unknown cross-row contract and assertion operators", () => {
  assert.throws(
    () => executeCrossRowContractRegistry({}, { BAD: { code: "BAD", contractType: "UNKNOWN" } }, "260002"),
    /Unsupported cross-row contract type/,
  );
  assert.throws(
    () => executeCrossRowContractRegistry(
      { source: [], target: [] },
      {
        BAD_ASSERTION: {
          code: "BAD_ASSERTION",
          contractType: "JOIN_ASSERTIONS",
          root: { alias: "source", table: "source", predicates: [] },
          joins: [{ from: { alias: "source", field: "id" }, to: { alias: "target", table: "target", field: "id" }, required: true }],
          rowPredicates: [],
          fieldAssertions: [{ left: { alias: "source", field: "id" }, operator: "MAGIC", right: { alias: "target", field: "id" } }],
        },
      },
      "260002",
    ),
    /Unsupported cross-row assertion operator/,
  );
  assert.throws(
    () => executeCrossRowContractRegistry(
      { source: [] },
      {
        BAD_PREDICATE: {
          code: "BAD_PREDICATE",
          contractType: "JOIN_ASSERTIONS",
          root: { alias: "source", table: "source", predicates: [{ field: "id", operator: "MAGIC", value: "1" }] },
          joins: [],
          rowPredicates: [],
          fieldAssertions: [],
        },
      },
      "260002",
    ),
    /Unsupported cross-row predicate operator/,
  );
});

test("Task 5 fix round 2 accepts the legal initial NULL-to-DRAFT transition", () => {
  assert.deepEqual(validateReleaseTransition(buildInitialTransitionFixture()), []);
});

test("Task 5 fix round 2 rejects extra and unknown binary Operand roles", () => {
  const extra = buildValidDecisionFixture();
  extra.operands.push({
    operandId: "231099", nodeId: "230032", operandRole: "MIDDLE", valueSequence: 1,
    sourceType: "LITERAL", featureVersionId: null, inputFieldId: null,
    valueType: "STRING", stringValue: "HIGH",
  });
  assertRejectedBy(validateDecisionRelease(extra), "AST_SHAPE", "AST_OPERAND_ARITY");

  const unknown = buildValidDecisionFixture();
  unknown.operands.find((row) => row.operandId === "231002").operandRole = "MIDDLE";
  assertRejectedBy(validateDecisionRelease(unknown), "AST_SHAPE", "AST_OPERAND_ROLE_INVALID");
});

test("Task 5 fix round 2 rejects unknown cross-row aliases and unordered topology", () => {
  assert.throws(
    () => executeCrossRowContractRegistry(
      { source: [{ id: "1" }] },
      {
        BAD_ALIAS: {
          code: "BAD_ALIAS",
          contractType: "JOIN_ASSERTIONS",
          root: { alias: "source", table: "source", predicates: [] },
          joins: [],
          rowPredicates: [],
          fieldAssertions: [{ left: { alias: "missing_left", field: "id" }, operator: "EQUALS", right: { alias: "missing_right", field: "id" } }],
        },
      },
      "260002",
    ),
    /Unknown cross-row alias/,
  );
  assert.throws(
    () => executeCrossRowContractRegistry(
      { source: [{ id: "1" }], target: [{ id: "1" }] },
      {
        BAD_TOPOLOGY: {
          code: "BAD_TOPOLOGY",
          contractType: "JOIN_ASSERTIONS",
          root: { alias: "source", table: "source", predicates: [] },
          joins: [{ from: { alias: "not_yet_joined", field: "id" }, to: { alias: "target", table: "target", field: "id" }, required: true }],
          rowPredicates: [],
          fieldAssertions: [],
        },
      },
      "260002",
    ),
    /Invalid cross-row join topology/,
  );
});

test("Task 5 fix round 2 binds activation to the approval completing dual review", () => {
  const command = buildValidTransitionFixture();
  command.activation.candidateStateEvent.triggerType = "COMMAND";
  command.activation.previousStateEvent.triggerType = "COMMAND";
  assertRejectedBy(validateReleaseTransition(command), "ACTIVATION_PAIR", "ACTIVATION_TRIGGER_MISMATCH");

  const firstApproval = buildValidTransitionFixture();
  firstApproval.activation.candidateStateEvent.triggerId = "approval-1";
  firstApproval.activation.previousStateEvent.triggerId = "approval-1";
  assertRejectedBy(validateReleaseTransition(firstApproval), "ACTIVATION_PAIR", "ACTIVATION_TRIGGER_MISMATCH");
});

test("Task 5 fix round 2 requires both Feature Dependency endpoints on the Point", () => {
  const fixture = buildValidDecisionFixture();
  fixture.featureVersions.push({
    featureVersionId: "220099", featureCode: "UNBOUND_FEATURE", resultValueType: "STRING",
    handlerCode: "UnboundFeature", implementationVersion: "1.0.0",
    implementationHash: hashOf("f"), status: "ACTIVE",
  });
  fixture.featureDependencies.push({
    dependencyId: "220099", featureVersionId: "220099",
    dependencyFeatureVersionId: "220011", sequenceNo: 1, requiredFlag: true,
  });
  assertRejectedBy(validateDecisionRelease(fixture), "FEATURE_DEPENDENCY", "FEATURE_DEPENDENCY_NOT_ALLOWLISTED");
});

test("Task 5 fix round 2 rejects empty IDs and noncanonical Release hashes", () => {
  const emptyReleaseId = buildValidDecisionFixture();
  emptyReleaseId.release.releaseId = "";
  assertRejectedBy(validateDecisionRelease(emptyReleaseId), "REFERENCE_INTEGRITY", "REFERENCE_ID_INVALID");

  const whitespaceId = buildValidDecisionFixture();
  whitespaceId.pointFeatures[0].bindingId = " 220101 ";
  assertRejectedBy(validateDecisionRelease(whitespaceId), "REFERENCE_INTEGRITY", "REFERENCE_ID_INVALID");

  const missingHash = buildValidDecisionFixture();
  delete missingHash.release.contentHash;
  assertRejectedBy(validateDecisionRelease(missingHash), "REFERENCE_INTEGRITY", "RELEASE_CONTENT_HASH_INVALID");

  const uppercaseHash = buildValidDecisionFixture();
  uppercaseHash.release.contentHash = hashOf("a").toUpperCase();
  assertRejectedBy(validateDecisionRelease(uppercaseHash), "REFERENCE_INTEGRITY", "RELEASE_CONTENT_HASH_INVALID");
});

test("Task 5 fix round 2 validates obligation-only Action Shadow and Handler contracts", () => {
  const fixture = buildValidDecisionFixture();
  fixture.actionVersions.push({
    actionVersionId: "240014", actionCode: "OBLIGATION_NOTIFICATION",
    handlerCode: "ObligationNotificationAction", implementationVersion: "1.0.0",
    implementationHash: hashOf("f"), terminalFlag: false, decisionCode: null,
    shadowSupportedFlag: false, status: "ACTIVE",
  });
  fixture.handlerDeployments.push({
    handlerType: "ACTION", handlerCode: "ObligationNotificationAction",
    implementationVersion: "1.0.0", implementationHash: hashOf("f"), deploymentStatus: "DEPLOYED",
  });
  fixture.obligations.push({
    obligationId: "250002", pointVersionId: "210011", triggerDecision: "DENY",
    obligationType: "ACTION", actionVersionId: "240014", sequenceNo: 1,
    failurePolicy: "FAIL_CLOSED", enabledFlag: true,
  });
  assertRejectedBy(validateDecisionRelease(fixture), "ACTION_PARAMETERS", "ACTION_SHADOW_UNSUPPORTED");

  fixture.actionVersions.at(-1).shadowSupportedFlag = true;
  fixture.handlerDeployments.at(-1).implementationHash = hashOf("a");
  assertRejectedBy(validateDecisionRelease(fixture), "HANDLER_HASH", "HANDLER_HASH_MISMATCH");
});

test("Task 5 fix round 2 rejects standalone ACTIVE-to-SUPERSEDED transitions", () => {
  const fixture = buildValidTransitionFixture();
  fixture.candidateBefore = { ...fixture.candidateAfter };
  fixture.candidateAfter = { ...fixture.candidateAfter, status: "SUPERSEDED", activeSlot: null, version: 7 };
  fixture.auditEvents.push({
    auditEventId: "280107", eventType: "STATE_CHANGE",
    eventTime: "2026-08-27T11:00:00.000000Z", releaseId: "260002",
    aggregateVersion: 7, fromStatus: "ACTIVE", toStatus: "SUPERSEDED",
    triggerType: "COMMAND", triggerId: "standalone-supersede",
  });
  fixture.relatedReleasesAfter = [fixture.candidateAfter];
  fixture.activation = null;
  assertRejectedBy(validateReleaseTransition(fixture), "ACTIVATION_PAIR", "ACTIVATION_PAIR_MISSING");
});

test("Task 5 fix round 3 requires canonical audit time and direct activation command time", () => {
  for (const invalidTime of [
    "2026-08-27T14:01:00.000000+04:00",
    "2026-08-27T10:01:00.1Z",
    "2026-02-30T10:01:00.000000Z",
    "not-a-time",
  ]) {
    const fixture = buildValidTransitionFixture();
    fixture.auditEvents.find((event) => event.approvalRequestId === "approval-2").eventTime = invalidTime;
    assertRejectedBy(validateReleaseTransition(fixture), "STATE_TRANSITION", "STATE_EVENT_TIME_INVALID");
  }

  const nonNumericTieBreak = buildValidTransitionFixture();
  nonNumericTieBreak.auditEvents.find((event) => event.approvalRequestId === "approval-2").auditEventId = "approval-event-2";
  assertRejectedBy(validateReleaseTransition(nonNumericTieBreak), "STATE_TRANSITION", "STATE_AUDIT_EVENT_ID_INVALID");

  const detachedCommandTime = buildValidTransitionFixture();
  detachedCommandTime.activation.candidateStateEvent.eventTime = "2026-08-27T10:01:00.000001Z";
  detachedCommandTime.activation.previousStateEvent.eventTime = "2026-08-27T10:01:00.000001Z";
  assertRejectedBy(validateReleaseTransition(detachedCommandTime), "ACTIVATION_PAIR", "ACTIVATION_COMMAND_TIME_MISMATCH");
});

test("Task 5 fix round 3 fails fast for absent or blank cross-row fields", () => {
  const rows = {
    source: [{ id: "source-1", kind: "SOURCE", target_id: "target-1", shared: "same" }],
    target: [{ id: "target-1", kind: "TARGET", shared: "same" }],
  };
  const joinContract = {
    code: "FIELD_CONTRACT",
    contractType: "JOIN_ASSERTIONS",
    root: { alias: "source", table: "source", predicates: [{ field: "kind", operator: "EQUALS", value: "SOURCE" }] },
    joins: [{ from: { alias: "source", field: "target_id" }, to: { alias: "target", table: "target", field: "id" }, required: true }],
    rowPredicates: [{ endpoint: { alias: "target", field: "kind" }, operator: "EQUALS", value: "TARGET" }],
    fieldAssertions: [{ left: { alias: "source", field: "shared" }, operator: "EQUALS", right: { alias: "target", field: "shared" } }],
  };
  assert.deepEqual(executeCrossRowContractRegistry(rows, { FIELD_CONTRACT: joinContract }, "260002"), []);
  assert.deepEqual(executeCrossRowContractRegistry({ source: [], target: [] }, { FIELD_CONTRACT: joinContract }, "260002"), []);

  const blankJoin = structuredClone(joinContract);
  blankJoin.joins[0].from.field = "";
  assert.throws(() => executeCrossRowContractRegistry(rows, { FIELD_CONTRACT: blankJoin }, "260002"), /Invalid cross-row field/);

  const blankPredicate = structuredClone(joinContract);
  blankPredicate.root.predicates[0].field = "";
  assert.throws(() => executeCrossRowContractRegistry(rows, { FIELD_CONTRACT: blankPredicate }, "260002"), /Invalid cross-row field/);

  const blankAssertion = structuredClone(joinContract);
  blankAssertion.fieldAssertions[0].left.field = "";
  assert.throws(() => executeCrossRowContractRegistry(rows, { FIELD_CONTRACT: blankAssertion }, "260002"), /Invalid cross-row field/);

  const absentJoinValue = structuredClone(rows);
  delete absentJoinValue.source[0].target_id;
  assert.throws(() => executeCrossRowContractRegistry(absentJoinValue, { FIELD_CONTRACT: joinContract }, "260002"), /Missing cross-row field/);

  const absentAssertionValues = structuredClone(rows);
  delete absentAssertionValues.source[0].shared;
  delete absentAssertionValues.target[0].shared;
  assert.throws(() => executeCrossRowContractRegistry(absentAssertionValues, { FIELD_CONTRACT: joinContract }, "260002"), /Missing cross-row field/);

  const mappingContract = {
    code: "MAPPING_CONTRACT",
    contractType: "ALLOWED_MAPPING",
    selectors: [
      { mappingKey: "sourceKind", table: "source", field: "kind" },
      { mappingKey: "targetKind", table: "target", field: "kind" },
    ],
    joins: [{ from: { table: "source", field: "target_id" }, to: { table: "target", field: "id" } }],
    requiredCoverage: [],
    allowedMappings: [{ sourceKind: "SOURCE", targetKind: "TARGET" }],
  };
  const blankSelector = structuredClone(mappingContract);
  blankSelector.selectors[0].field = "";
  assert.throws(() => executeCrossRowContractRegistry(rows, { MAPPING_CONTRACT: blankSelector }, "260002"), /Invalid cross-row field/);
  const absentSelector = structuredClone(rows);
  delete absentSelector.source[0].kind;
  assert.throws(() => executeCrossRowContractRegistry(absentSelector, { MAPPING_CONTRACT: mappingContract }, "260002"), /Missing cross-row field/);
});

test("Task 5 fix round 3 rejects initial creation when the Release already exists", () => {
  const fixture = buildInitialTransitionFixture();
  fixture.relatedReleasesBefore.push(structuredClone(fixture.candidateAfter));
  assertRejectedBy(validateReleaseTransition(fixture), "STATE_TRANSITION", "STATE_INITIAL_RELEASE_ALREADY_EXISTS");
});

test("Task 5 fix round 3 binds activation descriptors to persisted audit identity", () => {
  const cases = [
    (event) => { event.auditEventId = "999999"; },
    (event) => { event.eventType = "STATIC_CHECK"; },
    (event) => { event.eventTime = "2026-08-27T10:01:00.000001Z"; },
  ];
  for (const mutate of cases) {
    const fixture = buildValidTransitionFixture();
    fixture.activation.candidateStateEvent = { ...fixture.activation.candidateStateEvent };
    mutate(fixture.activation.candidateStateEvent);
    assertRejectedBy(validateReleaseTransition(fixture), "ACTIVATION_PAIR", "ACTIVATION_EVENT_IDENTITY_MISMATCH");
  }
});

test("Task 5 fix round 3 rejects the impossible SUPERSEDED-root activation orientation", () => {
  const fixture = buildValidTransitionFixture();
  fixture.candidateBefore = structuredClone(fixture.activation.previousBefore);
  fixture.candidateAfter = structuredClone(fixture.activation.previousAfter);
  assertRejectedBy(validateReleaseTransition(fixture), "ACTIVATION_PAIR", "ACTIVATION_ROOT_MUST_BECOME_ACTIVE");
});

test("Task 5 fix round 3 requires the ordinary after snapshot to be persisted", () => {
  const fixture = buildReviewPendingTransitionFixture();
  fixture.relatedReleasesAfter = [fixture.relatedReleasesBefore[0]];
  assertRejectedBy(validateReleaseTransition(fixture), "STATE_TRANSITION", "STATE_AFTER_SNAPSHOT_MISSING");
});

test("Task 5 fix round 3 neutralizes duplicate IDs independent of row order", () => {
  const forward = buildValidDecisionFixture();
  forward.featureVersions.push({
    ...forward.featureVersions[0],
    featureCode: "DUPLICATE_FEATURE",
    resultValueType: "BOOLEAN",
    handlerCode: "DuplicateFeature",
    implementationHash: hashOf("f"),
  });
  const reverse = structuredClone(forward);
  reverse.featureVersions.reverse();
  assert.deepEqual(validateDecisionRelease(forward), validateDecisionRelease(reverse));
});

test("Task 5 fix round 3 rejects invisible Unicode in business IDs", () => {
  const fixture = buildValidDecisionFixture();
  fixture.release.releaseId = "260\u200B002";
  assertRejectedBy(validateDecisionRelease(fixture), "REFERENCE_INTEGRITY", "REFERENCE_ID_INVALID");
});

test("Task 5 fix round 4 treats present NULL join keys as non-matches", () => {
  const nullableDispute = buildValidDecisionFixture();
  nullableDispute.crossRowRows.dispute_liability_decision.push(disputeLiabilityDecisionRow({
    outcome_id: "350103",
    action_effect_id: null,
  }));
  assert.deepEqual(validateDecisionRelease(nullableDispute), []);

  const nullJoinContract = {
    code: "CR_NULL_JOIN",
    contractType: "FORBIDDEN_PATHS",
    trigger: { table: "source", field: "kind", operator: "EQUALS", value: "SOURCE" },
    forbiddenPaths: [{
      code: "CR_NULL_JOIN",
      joins: [{ from: { table: "source", field: "target_id" }, to: { table: "target", field: "id" } }],
      targetPredicates: [],
    }],
  };
  const nullableRows = {
    source: [{ kind: "SOURCE", target_id: null }],
    target: [{ id: null }],
  };
  assert.deepEqual(executeCrossRowContractRegistry(nullableRows, { CR_NULL_JOIN: nullJoinContract }, "260002"), []);

  const undefinedJoin = structuredClone(nullableRows);
  undefinedJoin.source[0].target_id = undefined;
  assert.throws(
    () => executeCrossRowContractRegistry(undefinedJoin, { CR_NULL_JOIN: nullJoinContract }, "260002"),
    /Missing cross-row field/,
  );
});

test("Task 5 fix round 4 validates every related Release slot and counts every ACTIVE row", () => {
  const invalidBefore = buildValidTransitionFixture();
  invalidBefore.relatedReleasesBefore.push({
    releaseId: "260099", decisionPointId: "210001", pointVersionId: "210099",
    environmentCode: "PRODUCTION", contentHash: hashOf("a"),
    status: "DRAFT", activeSlot: 1, version: 1,
  });
  assertRejectedBy(validateReleaseTransition(invalidBefore), "STATE_TRANSITION", "STATE_RELATED_RELEASE_SLOT_MISMATCH");

  const invalidAfter = buildValidTransitionFixture();
  invalidAfter.relatedReleasesAfter.push({
    releaseId: "260099", decisionPointId: "210001", pointVersionId: "210099",
    environmentCode: "PRODUCTION", contentHash: hashOf("a"),
    status: "ACTIVE", activeSlot: null, version: 1,
  });
  const errors = validateReleaseTransition(invalidAfter);
  assertRejectedBy(errors, "STATE_TRANSITION", "STATE_RELATED_RELEASE_SLOT_MISMATCH");
  assertRejectedBy(errors, "ACTIVATION_PAIR", "ACTIVATION_MULTIPLE_ACTIVE");
});

test("Task 5 fix round 4 requires monotonic State history before final Approval", () => {
  const backwardState = buildValidTransitionFixture();
  backwardState.auditEvents.find((event) => event.eventType === "STATE_CHANGE" && event.aggregateVersion === 4)
    .eventTime = "2026-08-27T08:59:59.000000Z";
  assertRejectedBy(validateReleaseTransition(backwardState), "STATE_TRANSITION", "STATE_EVENT_TIME_ORDER_INVALID");

  const approvalBeforeHistory = buildValidTransitionFixture();
  approvalBeforeHistory.auditEvents.find((event) => event.eventType === "STATE_CHANGE" && event.aggregateVersion === 5)
    .eventTime = "2026-08-27T10:02:00.000000Z";
  const errors = validateReleaseTransition(approvalBeforeHistory);
  assertRejectedBy(errors, "STATE_TRANSITION", "STATE_EVENT_TIME_ORDER_INVALID");
  assertRejectedBy(errors, "ACTIVATION_PAIR", "ACTIVATION_APPROVAL_PRECEDES_HISTORY");
});

test("Task 5 fix round 4 requires string SHA-256 values everywhere", () => {
  const releaseHash = buildValidDecisionFixture();
  releaseHash.release.contentHash = [hashOf("7")];
  assertRejectedBy(validateDecisionRelease(releaseHash), "REFERENCE_INTEGRITY", "RELEASE_CONTENT_HASH_INVALID");

  const transitionHash = buildValidTransitionFixture();
  const sharedContentHash = [hashOf("7")];
  transitionHash.candidateBefore.contentHash = sharedContentHash;
  transitionHash.candidateAfter.contentHash = sharedContentHash;
  assertRejectedBy(validateReleaseTransition(transitionHash), "STATE_TRANSITION", "STATE_CONTENT_HASH_INVALID");

  const relatedHash = buildValidTransitionFixture();
  relatedHash.relatedReleasesAfter.push({
    releaseId: "260099", decisionPointId: "210001", pointVersionId: "210099",
    environmentCode: "PRODUCTION", contentHash: [hashOf("a")],
    status: "DRAFT", activeSlot: null, version: 1,
  });
  assertRejectedBy(validateReleaseTransition(relatedHash), "STATE_TRANSITION", "STATE_CONTENT_HASH_INVALID");

  const featureHash = buildValidDecisionFixture();
  const sharedFeatureHash = [hashOf("a")];
  featureHash.featureVersions[0].implementationHash = sharedFeatureHash;
  featureHash.handlerDeployments.find((row) => row.handlerCode === "IpRiskLevelFeature").implementationHash = sharedFeatureHash;
  assertRejectedBy(validateDecisionRelease(featureHash), "HANDLER_HASH", "HANDLER_HASH_FORMAT_INVALID");

  const actionHash = buildValidDecisionFixture();
  const sharedActionHash = [hashOf("c")];
  actionHash.actionVersions[0].implementationHash = sharedActionHash;
  actionHash.handlerDeployments.find((row) => row.handlerCode === "CooldownSubjectAction").implementationHash = sharedActionHash;
  assertRejectedBy(validateDecisionRelease(actionHash), "HANDLER_HASH", "HANDLER_HASH_FORMAT_INVALID");
});

test("Task 5 fix round 4 rejects malformed predicate metadata before execution", () => {
  const missingEqualsValue = {
    code: "MISSING_EQUALS_VALUE",
    contractType: "JOIN_ASSERTIONS",
    root: { alias: "source", table: "source", predicates: [{ field: "kind", operator: "EQUALS" }] },
    joins: [], rowPredicates: [], fieldAssertions: [],
  };
  assert.throws(
    () => executeCrossRowContractRegistry({ source: [] }, { MISSING_EQUALS_VALUE: missingEqualsValue }, "260002"),
    /EQUALS predicate requires value/,
  );

  const emptyInValues = {
    ...missingEqualsValue,
    code: "EMPTY_IN_VALUES",
    root: { alias: "source", table: "source", predicates: [{ field: "kind", operator: "IN", values: [] }] },
  };
  assert.throws(
    () => executeCrossRowContractRegistry({ source: [] }, { EMPTY_IN_VALUES: emptyInValues }, "260002"),
    /IN predicate requires non-empty values/,
  );
});

test("Task 5 fix round 4 rejects required parameters on Obligation Actions", () => {
  const sharedAction = buildValidDecisionFixture();
  sharedAction.obligations.push({
    obligationId: "250002", pointVersionId: "210011", triggerDecision: "DENY",
    obligationType: "ACTION", actionVersionId: "240011", sequenceNo: 1,
    failurePolicy: "FAIL_CLOSED", enabledFlag: true,
  });
  assertRejectedBy(validateDecisionRelease(sharedAction), "SYSTEM_OBLIGATION", "OBLIGATION_ACTION_PARAMETERS_UNBINDABLE");

  const obligationOnly = buildValidDecisionFixture();
  obligationOnly.actionVersions.push({
    actionVersionId: "240014", actionCode: "OBLIGATION_NOTIFICATION",
    handlerCode: "ObligationNotificationAction", implementationVersion: "1.0.0",
    implementationHash: hashOf("f"), terminalFlag: false, decisionCode: null,
    shadowSupportedFlag: true, status: "ACTIVE",
  });
  obligationOnly.handlerDeployments.push({
    handlerType: "ACTION", handlerCode: "ObligationNotificationAction",
    implementationVersion: "1.0.0", implementationHash: hashOf("f"), deploymentStatus: "DEPLOYED",
  });
  obligationOnly.parameterDefinitions.push({
    parameterDefinitionId: "241099", actionVersionId: "240014",
    parameterCode: "recipient", valueType: "STRING", requiredFlag: true,
    allowedSourceType: "CONTEXT", targetParameterFlag: false,
  });
  obligationOnly.obligations.push({
    obligationId: "250002", pointVersionId: "210011", triggerDecision: "DENY",
    obligationType: "ACTION", actionVersionId: "240014", sequenceNo: 1,
    failurePolicy: "FAIL_CLOSED", enabledFlag: true,
  });
  assertRejectedBy(validateDecisionRelease(obligationOnly), "SYSTEM_OBLIGATION", "OBLIGATION_ACTION_PARAMETERS_UNBINDABLE");
});

test("Task 5 fix round 4 constrains audit times to MySQL DATETIME(6)", () => {
  for (const invalidYear of ["0000", "0999"]) {
    const fixture = buildInitialTransitionFixture();
    fixture.auditEvents[0].eventTime = `${invalidYear}-08-27T08:00:00.000000Z`;
    assertRejectedBy(validateReleaseTransition(fixture), "STATE_TRANSITION", "STATE_EVENT_TIME_INVALID");
  }
  for (const validYear of ["1000", "9999"]) {
    const fixture = buildInitialTransitionFixture();
    fixture.auditEvents[0].eventTime = `${validYear}-08-27T08:00:00.000000Z`;
    assert.deepEqual(validateReleaseTransition(fixture), []);
  }
});

test("Task 5 fix round 4 validates cross-row event-key types through both APIs", () => {
  const directRows = emptyCrossRowRows();
  directRows.compensation_event.push({
    id: "360001", event_key: 315002, event_type: "EXECUTE_RISK_ACTION_EFFECT", sub_event_type: "APPLY_ASYNC_ACTION_EFFECT",
  });
  assertRejectedBy(
    executeCrossRowContractRegistry(directRows, CROSS_ROW_CONTRACTS, "260002"),
    "REFERENCE_INTEGRITY",
    "REFERENCE_FIELD_TYPE_INVALID",
  );

  const wrapped = buildValidDecisionFixture();
  wrapped.crossRowRows.compensation_event.push({
    id: "360001", event_key: 315002, event_type: "EXECUTE_RISK_ACTION_EFFECT", sub_event_type: "APPLY_ASYNC_ACTION_EFFECT",
  });
  assertRejectedBy(validateDecisionRelease(wrapped), "REFERENCE_INTEGRITY", "REFERENCE_FIELD_TYPE_INVALID");
});

test("Task 5 fix round 4 rejects terminal Actions used as Obligations", () => {
  const fixture = buildValidDecisionFixture();
  fixture.obligations.push({
    obligationId: "250002", pointVersionId: "210011", triggerDecision: "DENY",
    obligationType: "ACTION", actionVersionId: "240013", sequenceNo: 1,
    failurePolicy: "FAIL_CLOSED", enabledFlag: true,
  });
  assertRejectedBy(validateDecisionRelease(fixture), "SYSTEM_OBLIGATION", "OBLIGATION_ACTION_TERMINAL");
});

test("Task 5 fix round 4 enforces signed BIGINT identity grammar by field", () => {
  for (const invalidId of ["abc", "0", "0001", "9223372036854775808"]) {
    const fixture = buildValidDecisionFixture();
    fixture.release.releaseId = invalidId;
    assertRejectedBy(validateDecisionRelease(fixture), "REFERENCE_INTEGRITY", "REFERENCE_ID_INVALID");
  }

  const overflowAudit = buildInitialTransitionFixture();
  overflowAudit.auditEvents[0].auditEventId = "9223372036854775808";
  assertRejectedBy(validateReleaseTransition(overflowAudit), "STATE_TRANSITION", "STATE_AUDIT_EVENT_ID_INVALID");

  const alphaReviewer = buildValidTransitionFixture();
  alphaReviewer.auditEvents.find((event) => event.approvalRequestId === "approval-2").reviewerId = "reviewer-two";
  assertRejectedBy(validateReleaseTransition(alphaReviewer), "APPROVAL_GATE", "APPROVAL_REVIEWER_INVALID");

  const alphaActor = buildInitialTransitionFixture();
  alphaActor.auditEvents[0].actorId = "operations-user";
  assertRejectedBy(validateReleaseTransition(alphaActor), "STATE_TRANSITION", "STATE_ID_INVALID");

  const opaqueCommandIds = buildValidTransitionFixture();
  const completingApproval = opaqueCommandIds.auditEvents.find((event) => event.approvalRequestId === "approval-2");
  completingApproval.approvalRequestId = "approval-request-A";
  for (const event of opaqueCommandIds.auditEvents.filter((event) => event.triggerId === "approval-2")) {
    event.triggerId = "approval-request-A";
  }
  assert.deepEqual(validateReleaseTransition(opaqueCommandIds), []);

  const directRows = emptyCrossRowRows();
  directRows.compensation_event.push({
    id: "not-a-bigint", source_id: "315002", source_type: "ACTION_EFFECT",
  });
  assertRejectedBy(
    executeCrossRowContractRegistry(directRows, CROSS_ROW_CONTRACTS, "260002"),
    "REFERENCE_INTEGRITY",
    "REFERENCE_ID_INVALID",
  );
});

test("Task 5 fix round 5 derives cross-row ID type and nullability from the Risk Manifest", () => {
  const validRows = {
    decision_evaluation: [{
      evaluation_id: "310101",
      evaluation_request_id: "evaluation-request-A",
      release_id: "260002",
      shadow_run_id: null,
      baseline_evaluation_id: null,
    }],
    limit_movement: [{ movement_id: "340101", trigger_id: "limit-command-A" }],
    dispute_liability_decision: [disputeLiabilityDecisionRow()],
  };
  assert.deepEqual(executeCrossRowContractRegistry(validRows, {}, "260002"), []);

  const wrappedValidRows = buildValidDecisionFixture();
  wrappedValidRows.crossRowRows.decision_evaluation[0].evaluation_request_id = "evaluation-request-A";
  assert.deepEqual(
    validateDecisionRelease(wrappedValidRows),
    [],
    "validateDecisionRelease must preserve Manifest-declared opaque VARCHAR identifiers",
  );

  const nullRequiredId = structuredClone(validRows);
  nullRequiredId.decision_evaluation[0].release_id = null;
  assertRejectedBy(
    executeCrossRowContractRegistry(nullRequiredId, {}, "260002"),
    "REFERENCE_INTEGRITY",
    "REFERENCE_FIELD_NULL_NOT_ALLOWED",
  );

  const unknownField = structuredClone(validRows);
  unknownField.decision_evaluation[0].unknown_reference_id = "310999";
  assertRejectedBy(
    executeCrossRowContractRegistry(unknownField, {}, "260002"),
    "REFERENCE_INTEGRITY",
    "REFERENCE_FIELD_UNKNOWN",
  );
});

test("Task 5 fix round 5 validates every supplied Manifest hash without coercion", () => {
  for (const [table, row, field] of [
    ["decision_evaluation", { evaluation_id: "310101", input_snapshot_hash: [hashOf("1")] }, "input_snapshot_hash"],
    ["action_effect", { effect_id: "315101", effect_hash: { value: hashOf("2") } }, "effect_hash"],
  ]) {
    const errors = executeCrossRowContractRegistry({ [table]: [row] }, {}, "260002");
    assertRejectedBy(errors, "REFERENCE_INTEGRITY", "REFERENCE_HASH_INVALID");
    assert.ok(errors.some((error) => error.path === `crossRowRows.${table}.${row[Object.keys(row)[0]]}.${field}`));
  }
});

test("Task 5 final review enforces Manifest closed values and signed INT range", () => {
  assertRejectedBy(
    executeCrossRowContractRegistry({
      decision_evaluation: [{ evaluation_id: "310101", evaluation_mode: "NOT_A_MODE" }],
    }, {}, "260002"),
    "REFERENCE_INTEGRITY",
    "REFERENCE_FIELD_ENUM_INVALID",
  );
  assertRejectedBy(
    executeCrossRowContractRegistry({
      release_audit_event: [{ audit_event_id: "280101", approval_status: "NOT_A_STATUS" }],
    }, {}, "260002"),
    "REFERENCE_INTEGRITY",
    "REFERENCE_FIELD_ENUM_INVALID",
  );
  assertRejectedBy(
    executeCrossRowContractRegistry({
      decision_evaluation: [{ evaluation_id: "310101", elapsed_millis: Number.MAX_SAFE_INTEGER }],
    }, {}, "260002"),
    "REFERENCE_INTEGRITY",
    "REFERENCE_FIELD_RANGE_INVALID",
  );
  for (const payloadJson of [() => {}, Symbol("x"), 1n]) {
    assertRejectedBy(
      executeCrossRowContractRegistry({
        compensation_event: [{ id: "360101", payload_json: payloadJson }],
      }, {}, "260002"),
      "REFERENCE_INTEGRITY",
      "REFERENCE_FIELD_TYPE_INVALID",
    );
  }

  const symbolArray = [1];
  symbolArray[Symbol("hidden")] = "value";
  const hiddenArray = [1];
  Object.defineProperty(hiddenArray, "hidden", { value: () => {}, enumerable: false });
  const toJsonArray = [1];
  Object.defineProperty(toJsonArray, "toJSON", { value: () => [1], enumerable: false });
  class JsonArraySubclass extends Array {}
  const subclassArray = new JsonArraySubclass(1);
  subclassArray[0] = 1;
  let getterCalls = 0;
  const getterArray = [1];
  Object.defineProperty(getterArray, "0", {
    enumerable: true,
    configurable: true,
    get() {
      getterCalls += 1;
      return 1;
    },
  });
  for (const payloadJson of [symbolArray, hiddenArray, toJsonArray, subclassArray, getterArray]) {
    assertRejectedBy(
      executeCrossRowContractRegistry({
        compensation_event: [{ id: "360101", payload_json: payloadJson }],
      }, {}, "260002"),
      "REFERENCE_INTEGRITY",
      "REFERENCE_FIELD_TYPE_INVALID",
    );
  }
  assert.equal(getterCalls, 0, "JSON validation must not execute array getters");
});

test("Task 5 fix round 5 validates known policy fields from Manifest even for empty row arrays", () => {
  const mapping = {
    code: "KNOWN_FIELD_CONTRACT",
    contractType: "ALLOWED_MAPPING",
    selectors: [{ mappingKey: "mode", table: "decision_evaluation", field: "not_a_manifest_field" }],
    joins: [{
      from: { table: "decision_evaluation", field: "evaluation_id" },
      to: { table: "action_effect", field: "evaluation_id" },
    }],
    requiredCoverage: [],
    allowedMappings: [{ mode: "PRIMARY" }],
  };
  assert.throws(
    () => executeCrossRowContractRegistry(
      { decision_evaluation: [], action_effect: [] },
      { KNOWN_FIELD_CONTRACT: mapping },
      "260002",
    ),
    /Unknown Risk Manifest field/,
  );

  const forbidden = {
    code: "KNOWN_TRIGGER_FIELD",
    contractType: "FORBIDDEN_PATHS",
    trigger: { table: "decision_evaluation", field: "not_a_manifest_field", operator: "EQUALS", value: "SHADOW" },
    forbiddenPaths: [{
      code: "KNOWN_TRIGGER_FIELD_PATH",
      joins: [{
        from: { table: "decision_evaluation", field: "evaluation_id" },
        to: { table: "action_effect", field: "evaluation_id" },
      }],
      targetPredicates: [],
    }],
  };
  assert.throws(
    () => executeCrossRowContractRegistry(
      { decision_evaluation: [], action_effect: [] },
      { KNOWN_TRIGGER_FIELD: forbidden },
      "260002",
    ),
    /Unknown Risk Manifest field/,
  );
});

test("Task 5 fix round 5 rejects incomplete contract metadata and non-dense IN values", () => {
  const rows = { source: [], target: [] };
  const allowed = {
    code: "COMPLETE_MAPPING",
    contractType: "ALLOWED_MAPPING",
    selectors: [{ mappingKey: "kind", table: "source", field: "kind" }],
    joins: [{ from: { table: "source", field: "target_id" }, to: { table: "target", field: "id" } }],
    requiredCoverage: [],
    allowedMappings: [{ kind: "SOURCE" }],
  };
  for (const malformed of [
    { ...allowed, selectors: [] },
    { ...allowed, allowedMappings: [] },
    { ...allowed, allowedMappings: [{}] },
  ]) {
    assert.throws(
      () => executeCrossRowContractRegistry(rows, { [malformed.code]: malformed }, "260002"),
      /Invalid cross-row/,
    );
  }

  const noForbiddenPaths = {
    code: "NO_PATHS",
    contractType: "FORBIDDEN_PATHS",
    trigger: { table: "source", field: "kind", operator: "EQUALS", value: "SOURCE" },
    forbiddenPaths: [],
  };
  assert.throws(
    () => executeCrossRowContractRegistry({ source: [] }, { NO_PATHS: noForbiddenPaths }, "260002"),
    /Invalid cross-row/,
  );

  const noAssertions = {
    code: "NO_ASSERTIONS",
    contractType: "JOIN_ASSERTIONS",
    root: { alias: "source", table: "source", predicates: [] },
    joins: [{
      from: { alias: "source", field: "target_id" },
      to: { alias: "target", table: "target", field: "id" },
      required: true,
    }],
    rowPredicates: [],
    fieldAssertions: [],
  };
  assert.throws(
    () => executeCrossRowContractRegistry(rows, { NO_ASSERTIONS: noAssertions }, "260002"),
    /Invalid cross-row/,
  );

  const nonBooleanRequired = {
    ...noAssertions,
    code: "NON_BOOLEAN_REQUIRED",
    joins: [{ ...noAssertions.joins[0], required: "true" }],
    rowPredicates: [{ endpoint: { alias: "target", field: "kind" }, operator: "EQUALS", value: "TARGET" }],
  };
  assert.throws(
    () => executeCrossRowContractRegistry(rows, { NON_BOOLEAN_REQUIRED: nonBooleanRequired }, "260002"),
    /join required flag/,
  );

  const missingTargetPredicates = structuredClone(CROSS_ROW_CONTRACTS.CR_SIMULATED_EFFECT_DOWNSTREAM);
  delete missingTargetPredicates.forbiddenPaths[0].targetPredicates;
  assert.throws(
    () => executeCrossRowContractRegistry({}, { MISSING_TARGET_PREDICATES: missingTargetPredicates }, "260002"),
    /Invalid cross-row FORBIDDEN_PATHS metadata/,
  );

  const missingFieldAssertions = structuredClone(CROSS_ROW_CONTRACTS.CR_SHADOW_COMPARISON);
  delete missingFieldAssertions.fieldAssertions;
  assert.throws(
    () => executeCrossRowContractRegistry({}, { MISSING_FIELD_ASSERTIONS: missingFieldAssertions }, "260002"),
    /Invalid cross-row JOIN_ASSERTIONS metadata/,
  );

  const invalidContextQuantifier = structuredClone(CROSS_ROW_CONTRACTS.CR_INTENT_RULE_SOURCE);
  invalidContextQuantifier.contextQuantifier = "SOME";
  assert.throws(
    () => executeCrossRowContractRegistry({}, { INVALID_CONTEXT_QUANTIFIER: invalidContextQuantifier }, "260002"),
    /Invalid cross-row context quantifier/,
  );

  const missingContractCode = structuredClone(CROSS_ROW_CONTRACTS.CR_SHADOW_COMPARISON);
  delete missingContractCode.code;
  assert.throws(
    () => executeCrossRowContractRegistry({}, { MISSING_CONTRACT_CODE: missingContractCode }, "260002"),
    /Invalid cross-row contract code/,
  );

  const missingPathCode = structuredClone(CROSS_ROW_CONTRACTS.CR_SIMULATED_EFFECT_DOWNSTREAM);
  delete missingPathCode.forbiddenPaths[0].code;
  assert.throws(
    () => executeCrossRowContractRegistry({}, { MISSING_PATH_CODE: missingPathCode }, "260002"),
    /Invalid cross-row path code/,
  );

  for (const invalidValue of [123, "NOT_A_MODE"]) {
    const invalidEquals = structuredClone(CROSS_ROW_CONTRACTS.CR_SHADOW_COMPARISON);
    invalidEquals.root.predicates[0].value = invalidValue;
    assert.throws(
      () => executeCrossRowContractRegistry({}, { INVALID_KNOWN_EQUALS: invalidEquals }, "260002"),
      /Invalid cross-row predicate value/,
    );
  }

  const invalidKnownIn = structuredClone(CROSS_ROW_CONTRACTS.CR_SIMULATED_EFFECT_DOWNSTREAM);
  invalidKnownIn.trigger.values = [1];
  assert.throws(
    () => executeCrossRowContractRegistry({}, { INVALID_KNOWN_IN: invalidKnownIn }, "260002"),
    /Invalid cross-row predicate value/,
  );

  for (const values of [Array(1), [undefined], [null], [""], ["   "]]) {
    const invalidIn = {
      ...noAssertions,
      code: "INVALID_IN",
      root: { alias: "source", table: "source", predicates: [{ field: "kind", operator: "IN", values }] },
      rowPredicates: [{ endpoint: { alias: "target", field: "kind" }, operator: "EQUALS", value: "TARGET" }],
    };
    assert.throws(
      () => executeCrossRowContractRegistry(rows, { INVALID_IN: invalidIn }, "260002"),
      /IN predicate requires dense nonblank values/,
    );
  }
});

test("Risk Evaluation snapshot policy fails closed when conditional cardinality metadata drifts", () => {
  const incompleteDomain = structuredClone(CROSS_ROW_CONTRACTS.CR_EVALUATION_SNAPSHOT_DOMAIN);
  incompleteDomain.cases.at(-1).values = ["DUE_DILIGENCE"];
  assert.throws(
    () => executeCrossRowContractRegistry({}, { INCOMPLETE_DOMAIN: incompleteDomain }, "260002"),
    /Incomplete cross-row cardinality cases/,
  );

  const unknownDomain = structuredClone(CROSS_ROW_CONTRACTS.CR_EVALUATION_SNAPSHOT_DOMAIN);
  unknownDomain.cases[0].values = ["NOT_A_DOMAIN"];
  assert.throws(
    () => executeCrossRowContractRegistry({}, { UNKNOWN_DOMAIN: unknownDomain }, "260002"),
    /Invalid cross-row predicate value/,
  );

  const incompatibleForeignKey = structuredClone(CROSS_ROW_CONTRACTS.CR_EVALUATION_SNAPSHOT_DOMAIN);
  incompatibleForeignKey.children[0].foreignKey = "input_hash";
  assert.throws(
    () => executeCrossRowContractRegistry({}, { INCOMPATIBLE_CHILD: incompatibleForeignKey }, "260002"),
    /Incompatible cross-row fields/,
  );

  const invalidCount = structuredClone(CROSS_ROW_CONTRACTS.CR_EVALUATION_SNAPSHOT_DOMAIN);
  invalidCount.cases[0].exactly.checkoutSnapshot = -1;
  assert.throws(
    () => executeCrossRowContractRegistry({}, { INVALID_COUNT: invalidCount }, "260002"),
    /Invalid cross-row cardinality exact counts/,
  );
});

test("Settlement Assessment result policy rejects vacuous or ambiguous metadata", () => {
  const contract = MODEL_POLICY.SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS.CR_SETTLEMENT_ASSESSMENT_DOMAIN;
  assert.doesNotThrow(() => validateSettlementAssessmentDomainContractMetadata(contract));

  const reordered = structuredClone(contract);
  reordered.fieldAssertions.reverse();
  reordered.resultSelectors.reverse();
  reordered.allowedResultMappings.reverse();
  assert.doesNotThrow(() => validateSettlementAssessmentDomainContractMetadata(reordered));

  const noAssertions = structuredClone(contract);
  noAssertions.fieldAssertions = [];
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(noAssertions),
    /Invalid Settlement Assessment metadata/,
  );

  const noSelectors = structuredClone(contract);
  noSelectors.resultSelectors = [];
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(noSelectors),
    /Invalid Settlement Assessment metadata/,
  );

  const noMappings = structuredClone(contract);
  noMappings.allowedResultMappings = [];
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(noMappings),
    /Invalid Settlement Assessment metadata/,
  );

  const duplicateMappingKey = structuredClone(contract);
  duplicateMappingKey.resultSelectors[0].mappingKey = "technicalStatus";
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(duplicateMappingKey),
    /Invalid Settlement Assessment selector mapping key/,
  );

  const incompleteMapping = structuredClone(contract);
  delete incompleteMapping.allowedResultMappings[0].eligibilityDecision;
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(incompleteMapping),
    /Invalid Settlement Assessment result mapping/,
  );

  const invalidField = structuredClone(contract);
  invalidField.fieldAssertions[0].left.field = "not_a_manifest_field";
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(invalidField),
    /Unknown Risk Manifest field/,
  );

  for (const retainedAssertionIndex of [0, 1]) {
    const incompleteAssertions = structuredClone(contract);
    incompleteAssertions.fieldAssertions = [incompleteAssertions.fieldAssertions[retainedAssertionIndex]];
    assert.throws(
      () => validateSettlementAssessmentDomainContractMetadata(incompleteAssertions),
      /Incomplete Settlement Assessment field assertions/,
    );
  }

  const missingEligibilitySelector = structuredClone(contract);
  missingEligibilitySelector.resultSelectors = missingEligibilitySelector.resultSelectors
    .filter((selector) => selector.mappingKey !== "eligibilityDecision");
  missingEligibilitySelector.allowedResultMappings = missingEligibilitySelector.allowedResultMappings
    .map(({ eligibilityDecision: _eligibilityDecision, ...mapping }) => mapping);
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(missingEligibilitySelector),
    /Incomplete Settlement Assessment result selectors/,
  );

  const missingAllowedMapping = structuredClone(contract);
  missingAllowedMapping.allowedResultMappings = [missingAllowedMapping.allowedResultMappings[0]];
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(missingAllowedMapping),
    /Incomplete Settlement Assessment result mappings/,
  );

  const contradictoryMapping = structuredClone(contract);
  contradictoryMapping.allowedResultMappings.push({
    eligibilityDecision: "ALLOW",
    technicalStatus: "SUCCEEDED",
    proposedDecision: "ALLOW",
    businessDecision: "DENY",
  });
  assert.throws(
    () => validateSettlementAssessmentDomainContractMetadata(contradictoryMapping),
    /Invalid Settlement Assessment result mapping/,
  );
});

test("Task 5 fix round 5 requires complete identity before related Release scope counting", () => {
  const fixture = buildValidTransitionFixture();
  fixture.relatedReleasesAfter.push({
    releaseId: "260099",
    pointVersionId: "210099",
    contentHash: hashOf("a"),
    status: "ACTIVE",
    activeSlot: 1,
    version: 1,
  });
  const errors = validateReleaseTransition(fixture);
  assertRejectedBy(errors, "STATE_TRANSITION", "STATE_RELATED_RELEASE_IDENTITY_INVALID");
  assertRejectedBy(errors, "ACTIVATION_PAIR", "ACTIVATION_MULTIPLE_ACTIVE");
});

test("Task 5 fix round 5 keeps recursive and cross-row errors independent of array order", () => {
  const forward = buildValidDecisionFixture();
  forward.operands[0].featureVersionId = 220011;
  forward.operands[1].inputFieldId = 210021;
  const reverse = structuredClone(forward);
  reverse.operands.reverse();
  assert.deepEqual(validateDecisionRelease(forward), validateDecisionRelease(reverse));

  const crossRowForward = {
    decision_evaluation: [
      { evaluation_id: "310101", release_id: 260001 },
      { evaluation_id: "310102", point_version_id: 210011 },
    ],
  };
  const crossRowReverse = structuredClone(crossRowForward);
  crossRowReverse.decision_evaluation.reverse();
  assert.deepEqual(
    executeCrossRowContractRegistry(crossRowForward, {}, "260002"),
    executeCrossRowContractRegistry(crossRowReverse, {}, "260002"),
  );
});

test("Task 5 fix round 5 freezes canonical Operand and Action Parameter Literal representations", () => {
  const valueField = {
    STRING: "stringValue",
    NUMBER: "numberValue",
    BOOLEAN: "booleanValue",
    DATETIME: "datetimeValue",
    DURATION: "durationValue",
  };
  const validValues = {
    STRING: "literal",
    NUMBER: "10.00000000",
    BOOLEAN: true,
    DATETIME: "2026-08-27T10:00:00.000000Z",
    DURATION: "0",
  };
  const invalidValues = {
    STRING: { text: "literal" },
    NUMBER: "01.00000000",
    BOOLEAN: "true",
    DATETIME: "2026-08-27T10:00:00.000000+00:00",
    DURATION: "-1",
  };
  const setTypedValue = (row, valueType, value) => {
    for (const field of Object.values(valueField)) delete row[field];
    row[valueField[valueType]] = value;
  };
  const operandFixture = (valueType, value) => {
    const fixture = buildValidDecisionFixture();
    fixture.featureVersions[0].resultValueType = valueType;
    fixture.operands.find((row) => row.operandId === "231001").valueType = valueType;
    const literal = fixture.operands.find((row) => row.operandId === "231002");
    literal.valueType = valueType;
    setTypedValue(literal, valueType, value);
    return fixture;
  };
  const parameterFixture = (valueType, value) => {
    const fixture = buildValidDecisionFixture();
    fixture.parameterDefinitions.find((row) => row.parameterDefinitionId === "241004").valueType = valueType;
    const literal = fixture.actionParameters.find((row) => row.parameterId === "243004");
    setTypedValue(literal, valueType, value);
    return fixture;
  };

  for (const valueType of Object.keys(valueField)) {
    assertRejectedBy(
      validateDecisionRelease(operandFixture(valueType, invalidValues[valueType])),
      "AST_TYPES",
      "AST_LITERAL_VALUE_INVALID",
    );
    assertRejectedBy(
      validateDecisionRelease(parameterFixture(valueType, invalidValues[valueType])),
      "ACTION_PARAMETERS",
      "ACTION_PARAMETER_LITERAL_INVALID",
    );
    assert.deepEqual(validateDecisionRelease(operandFixture(valueType, validValues[valueType])), []);
    assert.deepEqual(validateDecisionRelease(parameterFixture(valueType, validValues[valueType])), []);
  }

  assertRejectedBy(
    validateDecisionRelease(operandFixture("STRING", "x".repeat(513))),
    "AST_TYPES",
    "AST_LITERAL_VALUE_INVALID",
  );
  assertRejectedBy(
    validateDecisionRelease(parameterFixture("STRING", "x".repeat(513))),
    "ACTION_PARAMETERS",
    "ACTION_PARAMETER_LITERAL_INVALID",
  );
});

test("Task 5B consumes all seven schema-bound golden projections byte-for-byte", () => {
  const vectors = readDecisionGoldenVectors();
  const canonicalizers = new Map([
    ["RISK_RULE_SET_VERSION_V1", canonicalizeRiskRuleSetVersion],
    ["RISK_DECISION_POINT_VERSION_V1", canonicalizeRiskDecisionPointVersion],
    ["RISK_DECISION_RELEASE_V1", canonicalizeRiskDecisionRelease],
    ["RISK_CREATE_RULE_SET_VERSION_COMMAND_V1", canonicalizeRiskCreateRuleSetVersionCommand],
    ["RISK_FREEZE_RULE_SET_VERSION_COMMAND_V1", canonicalizeRiskFreezeRuleSetVersionCommand],
    ["RISK_CREATE_TEST_RELEASE_COMMAND_V1", canonicalizeRiskCreateTestReleaseCommand],
    ["RISK_VALIDATE_TEST_RELEASE_COMMAND_V1", canonicalizeRiskValidateTestReleaseCommand],
  ]);

  assert.equal(vectors.canonicalProjections.length, 7);
  for (const vector of vectors.canonicalProjections) {
    const canonicalize = canonicalizers.get(vector.schema);
    assert.ok(canonicalize, `missing JavaScript canonicalizer for ${vector.schema}`);
    assert.deepEqual(canonicalize(vector.input), vector.expected, vector.id);
  }
});

test("Task 5B canonicalizes semantic arrival permutations and per-role operand sequences", () => {
  const vectors = readDecisionGoldenVectors();
  const create = vectors.canonicalProjections.find((vector) => vector.id === "create-rule-set-primary");
  const release = vectors.canonicalProjections.find((vector) => vector.id === "decision-release-primary");
  const semantic = vectors.discriminators.find((vector) => vector.id === "semantic-array-permutation");

  assert.equal(canonicalizeRiskCreateRuleSetVersionCommand(create.input).sha256, semantic.expectedSameHash);
  for (const equivalent of semantic.equivalentInputs) {
    assert.deepEqual(canonicalizeRiskCreateRuleSetVersionCommand(equivalent), semantic.expectedSame);
  }
  assert.deepEqual(canonicalizeRiskCreateRuleSetVersionCommand(semantic.changedInput), semantic.expectedChanged);

  const reversedArrival = structuredClone(create.input);
  reversedArrival.rules.reverse();
  for (const rule of reversedArrival.rules) {
    rule.conditionNodes.reverse();
    for (const node of rule.conditionNodes) node.operands.reverse();
  }
  assert.deepEqual(canonicalizeRiskCreateRuleSetVersionCommand(reversedArrival), create.expected);

  const releaseResult = canonicalizeRiskDecisionRelease(release.input);
  const paired = release.input.operands.filter((operand) => operand.nodeId === "2");
  assert.deepEqual(paired.map((operand) => [operand.operandRole, operand.valueSequence]), [["LEFT", 1], ["RIGHT", 1]]);
  assert.deepEqual(releaseResult, release.expected);
});

test("Task 5B uses formal non-null/UTF-8/numeric violation ordering and fixed eight checks", () => {
  const vectors = readDecisionGoldenVectors();
  const order = vectors.discriminators.find((vector) => vector.id === "violation-order-null-dot-underscore-id");
  const checks = vectors.discriminators.find((vector) => vector.id === "eight-static-check-order");

  assert.deepEqual(summarizeDecisionStaticViolations(order.violations), order.expected);
  assert.deepEqual(materializeDecisionStaticChecks(checks.inputChecks), checks.expected.results);
});

test("Task 5B preserves negative non-zero DECIMAL values while rejecting every negative zero", () => {
  const vectors = readDecisionGoldenVectors();
  const create = structuredClone(
    vectors.canonicalProjections.find((vector) => vector.id === "create-rule-set-primary").input,
  );
  const literal = create.rules.flatMap((rule) => rule.conditionNodes)
    .flatMap((node) => node.operands)
    .find((operand) => operand.valueType === "NUMBER" && operand.sourceType === "LITERAL");

  literal.literalValue = "-0.1";
  assert.match(canonicalizeRiskCreateRuleSetVersionCommand(create).canonicalJson, /"-0\.10000000"/);
  for (const negativeZero of ["-0", "-0.0", "-0.00000000"]) {
    literal.literalValue = negativeZero;
    assert.throws(() => canonicalizeRiskCreateRuleSetVersionCommand(create), /DECIMAL\(24,8\)/);
  }
});

test("Task 5 fix round 5 enforces physical length on opaque VARCHAR identifiers", () => {
  const tooLong = "a".repeat(129);
  const approval = buildValidTransitionFixture();
  approval.auditEvents.find((event) => event.approvalRequestId === "approval-2").approvalRequestId = tooLong;
  for (const event of approval.auditEvents.filter((event) => event.triggerId === "approval-2")) event.triggerId = tooLong;
  assertRejectedBy(validateReleaseTransition(approval), "APPROVAL_GATE", "APPROVAL_REQUEST_INVALID");

  const trigger = buildInitialTransitionFixture();
  trigger.auditEvents[0].triggerId = tooLong;
  assertRejectedBy(validateReleaseTransition(trigger), "STATE_TRANSITION", "STATE_TRIGGER_INVALID");

  const crossRow = {
    decision_evaluation: [{ evaluation_id: "310101", evaluation_request_id: tooLong }],
  };
  assertRejectedBy(
    executeCrossRowContractRegistry(crossRow, {}, "260002"),
    "REFERENCE_INTEGRITY",
    "REFERENCE_FIELD_LENGTH_INVALID",
  );
  crossRow.decision_evaluation[0].evaluation_request_id = "a".repeat(128);
  assert.deepEqual(executeCrossRowContractRegistry(crossRow, {}, "260002"), []);
});

test("Task 5 fix round 5 binds the completing Approval to both final candidate state events", () => {
  const pendingBeforeReview = buildReviewPendingTransitionFixture();
  pendingBeforeReview.auditEvents.push({
    auditEventId: "280303",
    eventType: "APPROVAL",
    eventTime: "2026-08-27T08:00:00.000000Z",
    releaseId: "260002",
    shadowRunId: "270001",
    approvalRequestId: "premature-approval",
    approvalStatus: "APPROVED",
    reviewerId: "900001",
  });
  assertRejectedBy(
    validateReleaseTransition(pendingBeforeReview),
    "APPROVAL_GATE",
    "APPROVAL_BEFORE_REVIEW_PENDING",
  );

  const beforeReview = buildValidTransitionFixture();
  beforeReview.auditEvents.find((event) => event.approvalRequestId === "approval-1").eventTime = "2026-08-27T08:00:00.000000Z";
  assertRejectedBy(validateReleaseTransition(beforeReview), "APPROVAL_GATE", "APPROVAL_BEFORE_REVIEW_PENDING");

  const sameTimeBeforeReview = buildValidTransitionFixture();
  const reviewPendingEvent = sameTimeBeforeReview.auditEvents.find((event) => (
    event.eventType === "STATE_CHANGE" && event.toStatus === "REVIEW_PENDING"
  ));
  const firstApproval = sameTimeBeforeReview.auditEvents.find((event) => event.approvalRequestId === "approval-1");
  firstApproval.eventTime = reviewPendingEvent.eventTime;
  firstApproval.auditEventId = "280001";
  assertRejectedBy(
    validateReleaseTransition(sameTimeBeforeReview),
    "APPROVAL_GATE",
    "APPROVAL_BEFORE_REVIEW_PENDING",
  );

  const detachedApproved = buildValidTransitionFixture();
  const approvedEvent = detachedApproved.auditEvents.find((event) => (
    event.eventType === "STATE_CHANGE" && event.aggregateVersion === 5
  ));
  approvedEvent.eventTime = "2026-08-27T09:00:05.000000Z";
  approvedEvent.triggerType = "COMMAND";
  approvedEvent.triggerId = "state-5";
  assertRejectedBy(
    validateReleaseTransition(detachedApproved),
    "ACTIVATION_PAIR",
    "ACTIVATION_APPROVAL_STATE_BINDING_INVALID",
  );

  const afterActive = buildValidTransitionFixture();
  afterActive.auditEvents.push({
    auditEventId: "280303",
    eventType: "APPROVAL",
    eventTime: "2026-08-27T10:02:00.000000Z",
    releaseId: "260002",
    shadowRunId: "270001",
    approvalRequestId: "approval-3",
    approvalStatus: "APPROVED",
    reviewerId: "900003",
  });
  assertRejectedBy(validateReleaseTransition(afterActive), "APPROVAL_GATE", "APPROVAL_AFTER_COMPLETION");

  const afterRejected = buildReviewPendingTransitionFixture();
  afterRejected.candidateBefore = { ...afterRejected.candidateAfter };
  afterRejected.candidateAfter = {
    ...afterRejected.candidateBefore,
    status: "REJECTED",
    version: 5,
  };
  afterRejected.auditEvents.push(
    {
      auditEventId: "280105",
      eventType: "STATE_CHANGE",
      eventTime: "2026-08-27T10:00:00.000000Z",
      releaseId: "260002",
      aggregateVersion: 5,
      fromStatus: "REVIEW_PENDING",
      toStatus: "REJECTED",
      triggerType: "COMMAND",
      triggerId: "reject-1",
    },
    {
      auditEventId: "280303",
      eventType: "APPROVAL",
      eventTime: "2026-08-27T10:01:00.000000Z",
      releaseId: "260002",
      shadowRunId: "270001",
      approvalRequestId: "approval-after-rejection",
      approvalStatus: "APPROVED",
      reviewerId: "900003",
    },
  );
  afterRejected.relatedReleasesAfter = [afterRejected.candidateAfter, afterRejected.relatedReleasesBefore[0]];
  assertRejectedBy(validateReleaseTransition(afterRejected), "APPROVAL_GATE", "APPROVAL_AFTER_TERMINAL");

  const invalidApprovalStatus = buildReviewPendingTransitionFixture();
  invalidApprovalStatus.auditEvents.push({
    auditEventId: "280303",
    eventType: "APPROVAL",
    eventTime: "2026-08-27T09:30:00.000000Z",
    releaseId: "260002",
    shadowRunId: "270001",
    approvalRequestId: "approval-pending",
    approvalStatus: "PENDING",
    reviewerId: "900003",
  });
  assertRejectedBy(
    validateReleaseTransition(invalidApprovalStatus),
    "APPROVAL_GATE",
    "APPROVAL_STATUS_INVALID",
  );
});

test("Task 5 final review enforces Static and Shadow gate chronology", () => {
  const lateStaticChecks = buildValidTransitionFixture();
  for (const check of lateStaticChecks.auditEvents.filter((event) => event.eventType === "STATIC_CHECK")) {
    check.eventTime = "2026-08-27T11:00:00.000000Z";
  }
  assertRejectedBy(
    validateReleaseTransition(lateStaticChecks),
    "SHADOW_GATE",
    "STATIC_CHECK_AFTER_SHADOWING",
  );

  const missingCompletion = buildValidTransitionFixture();
  delete missingCompletion.shadowRuns[0].completedTime;
  assertRejectedBy(
    validateReleaseTransition(missingCompletion),
    "SHADOW_GATE",
    "SHADOW_RUN_COMPLETION_TIME_INVALID",
  );

  const lateCompletion = buildValidTransitionFixture();
  lateCompletion.shadowRuns[0].completedTime = "2026-08-27T11:00:00.000000Z";
  assertRejectedBy(
    validateReleaseTransition(lateCompletion),
    "SHADOW_GATE",
    "SHADOW_RUN_COMPLETION_AFTER_REVIEW_PENDING",
  );

  const earlyCompletion = buildValidTransitionFixture();
  earlyCompletion.shadowRuns[0].completedTime = "2026-08-27T08:00:00.000000Z";
  assertRejectedBy(
    validateReleaseTransition(earlyCompletion),
    "SHADOW_GATE",
    "SHADOW_RUN_COMPLETION_BEFORE_SHADOWING",
  );
});

test("Final whole-slice fix rejects retired Action codes at every executable use site", () => {
  const retiredRuleAction = buildValidDecisionFixture();
  retiredRuleAction.actionVersions.find((action) => (
    action.actionVersionId === "240012"
  )).actionCode = "CREATE_SETTLEMENT_RESTRICTION";
  assertRejectedBy(
    validateDecisionRelease(retiredRuleAction),
    "ACTION_PARAMETERS",
    "RETIRED_ACTION_CODE",
  );

  const retiredObligationAction = buildValidDecisionFixture();
  retiredObligationAction.actionVersions.push({
    actionVersionId: "240014", actionCode: "CREATE_RESERVE_REQUIREMENT",
    handlerCode: "RetiredReserveAction", implementationVersion: "1.0.0",
    implementationHash: hashOf("f"), terminalFlag: false, decisionCode: null,
    shadowSupportedFlag: true, status: "ACTIVE",
  });
  retiredObligationAction.handlerDeployments.push({
    handlerType: "ACTION", handlerCode: "RetiredReserveAction",
    implementationVersion: "1.0.0", implementationHash: hashOf("f"),
    deploymentStatus: "DEPLOYED",
  });
  retiredObligationAction.obligations.push({
    obligationId: "250002", pointVersionId: "210011", triggerDecision: "DENY",
    obligationType: "ACTION", actionVersionId: "240014", sequenceNo: 1,
    failurePolicy: "FAIL_CLOSED", enabledFlag: true,
  });
  assertRejectedBy(
    validateDecisionRelease(retiredObligationAction),
    "ACTION_PARAMETERS",
    "RETIRED_ACTION_CODE",
  );
});
