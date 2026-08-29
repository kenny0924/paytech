/**
 * Design-time executable specification for Risk Decision Engine releases.
 *
 * This module validates normalized model/test fixtures only. It is not the
 * production Java evaluator and must never execute handlers, SQL, scripts,
 * HTTP calls, database operations, or business side effects.
 */

import { createHash } from "node:crypto";

import {
  CROSS_ROW_CONTRACTS,
  SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS,
  SETTLEMENT_ELIGIBILITY_OBLIGATION_CONTRACT,
} from "./model-policy.mjs";
import { TABLES as RISK_TABLES } from "./manifest.mjs";
import { computeLimitVersionContentHash } from "./limit-version-canonicalizer.mjs";
import { canonicalizeSettlementActionParameterIdentity } from "./settlement-control-canonicalizer.mjs";

export const DECISION_RELEASE_CHECK_CODES = Object.freeze([
  "REFERENCE_INTEGRITY",
  "AST_SHAPE",
  "AST_TYPES",
  "FEATURE_DEPENDENCY",
  "HANDLER_HASH",
  "ACTION_PARAMETERS",
  "TERMINAL_ACTION",
  "SYSTEM_OBLIGATION",
]);

export const RELEASE_TRANSITION_CHECK_CODES = Object.freeze([
  "SHADOW_GATE",
  "APPROVAL_GATE",
  "STATE_TRANSITION",
  "ACTIVATION_PAIR",
]);

const CHECK_ORDER = new Map(
  [...DECISION_RELEASE_CHECK_CODES, ...RELEASE_TRANSITION_CHECK_CODES]
    .map((checkCode, index) => [checkCode, index]),
);
const VALUE_FIELDS = Object.freeze({
  STRING: "stringValue",
  NUMBER: "numberValue",
  BOOLEAN: "booleanValue",
  DATETIME: "datetimeValue",
  DURATION: "durationValue",
});
const VALUE_FIELD_NAMES = Object.freeze(Object.values(VALUE_FIELDS));
const ORDERING_TYPES = new Set(["NUMBER", "DATETIME", "DURATION"]);
const BINARY_OPERATORS = new Set(["EQ", "NE", "GT", "GTE", "LT", "LTE"]);
const SET_OPERATORS = new Set(["IN", "NOT_IN"]);
const NULL_OPERATORS = new Set(["IS_NULL", "IS_NOT_NULL"]);
const TERMINAL_DECISIONS = new Set(["ALLOW", "REVIEW", "DENY"]);
const RETIRED_ACTION_CODES = new Set([
  "CREATE_RESERVE_REQUIREMENT",
  "CREATE_SETTLEMENT_RESTRICTION",
]);
const REQUIRED_STATIC_CHECKS = Object.freeze([...DECISION_RELEASE_CHECK_CODES]);
const RELEASE_GRAPH = Object.freeze({
  DRAFT: Object.freeze(["VALIDATING", "REJECTED"]),
  VALIDATING: Object.freeze(["SHADOWING", "REJECTED"]),
  SHADOWING: Object.freeze(["REVIEW_PENDING", "REJECTED"]),
  REVIEW_PENDING: Object.freeze(["APPROVED", "REJECTED"]),
  APPROVED: Object.freeze(["ACTIVE"]),
  ACTIVE: Object.freeze(["SUPERSEDED"]),
  REJECTED: Object.freeze([]),
  SUPERSEDED: Object.freeze([]),
});
const SHA256_PATTERN = /^[0-9a-f]{64}$/;
const VISIBLE_ASCII_ID_PATTERN = /^[\x21-\x7e]+$/;
const CROSS_ROW_FIELD_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;
const CROSS_ROW_CODE_PATTERN = /^[A-Z][A-Z0-9_]*$/;
const POSITIVE_DECIMAL_ID_PATTERN = /^[1-9][0-9]*$/;
const NONNEGATIVE_DECIMAL_PATTERN = /^(?:0|[1-9][0-9]*)$/;
const DECIMAL_24_8_PATTERN = /^-?(?:0|[1-9][0-9]{0,15})\.[0-9]{8}$/;
const SIGNED_BIGINT_MAX = 9223372036854775807n;
const SIGNED_INT_MIN = -2147483648;
const SIGNED_INT_MAX = 2147483647;
const UTC_MICROSECOND_TIMESTAMP_PATTERN = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})\.(\d{6})Z$/;
const VISIBLE_IDENTIFIER_FIELDS = new Set(["approvalRequestId", "triggerId"]);
const OPAQUE_IDENTIFIER_MAX_LENGTH = 128;
const STRING_LITERAL_MAX_LENGTH = 512;
const LIMIT_SUBJECT_TYPES = new Set(["CONSUMER", "EMAIL_TOKEN", "PHONE_TOKEN", "IP_ADDRESS", "CLIENT", "OPERATING_ENTITY"]);
const LIMIT_METRIC_TYPES = new Set(["AMOUNT", "COUNT", "AMOUNT_AND_COUNT"]);
const LIMIT_WINDOW_UNITS = new Set(["MINUTE", "HOUR", "DAY", "MONTH"]);
const LIMIT_CURRENCY_MODES = new Set(["FIXED", "PER_CURRENCY", "NO_CURRENCY"]);
const LIMIT_STATUSES = new Set(["DRAFT", "FROZEN", "RETIRED"]);
const ISO_CURRENCY_PATTERN = /^[A-Z]{3}$/;
const ISO_COUNTRY_PATTERN = /^[A-Z]{2}$/;
const SETTLEMENT_POINT_CODES = new Set(
  SETTLEMENT_ELIGIBILITY_OBLIGATION_CONTRACT.root.predicates
    .find((predicate) => predicate.field === "point_code")?.values ?? [],
);
const SETTLEMENT_SCOPE_PARAMETER_CODES = Object.freeze([
  "SCOPE_OPERATING_ENTITY_KEY",
  "SCOPE_CURRENCY_KEY",
  "SCOPE_BANK_COUNTRY_KEY",
]);
const SETTLEMENT_CONTROL_PARAMETER_CODES = new Set([
  "ROLE_TYPE", "CONTROL_TYPE", "REASON_CODE", ...SETTLEMENT_SCOPE_PARAMETER_CODES,
]);
const DECISION_COLLECTION_IDS = Object.freeze({
  inputFields: "inputFieldId",
  pointFeatures: "bindingId",
  featureVersions: "featureVersionId",
  featureDependencies: "dependencyId",
  pointRuleSets: "bindingId",
  ruleSetVersions: "ruleSetVersionId",
  rules: "ruleId",
  nodes: "nodeId",
  operands: "operandId",
  actionVersions: "actionVersionId",
  parameterDefinitions: "parameterDefinitionId",
  ruleActions: "ruleActionId",
  actionParameters: "parameterId",
  obligations: "obligationId",
  limitVersions: "limitVersionId",
  limitBindings: "bindingId",
});
const RELEASE_CONTENT_COLLECTION_FIELDS = Object.freeze({
  inputFields: Object.freeze([
    "inputFieldId", "pointVersionId", "fieldCode", "valueType",
    "requiredFlag", "dataClassification", "ruleReferenceFlag", "actionParameterFlag",
  ]),
  pointFeatures: Object.freeze([
    "bindingId", "pointVersionId", "featureVersionId", "sequenceNo",
  ]),
  featureVersions: Object.freeze([
    "featureVersionId", "featureCode", "businessCategory", "versionNo", "resultValueType",
    "handlerCode", "implementationVersion", "implementationHash", "timeoutMs",
  ]),
  featureDependencies: Object.freeze([
    "dependencyId", "featureVersionId", "dependencyFeatureVersionId", "sequenceNo", "requiredFlag",
  ]),
  pointRuleSets: Object.freeze([
    "bindingId", "pointVersionId", "ruleSetVersionId", "sequenceNo",
  ]),
  ruleSetVersions: Object.freeze([
    "ruleSetVersionId", "decisionPointId", "ruleSetCode", "versionNo", "contentHash",
  ]),
  rules: Object.freeze([
    "ruleId", "ruleSetVersionId", "ruleCode", "priorityNo", "enabledFlag", "reasonCode",
  ]),
  nodes: Object.freeze([
    "nodeId", "ruleId", "parentNodeId", "nodeType", "groupOperator",
    "comparisonOperator", "sequenceNo",
  ]),
  operands: Object.freeze([
    "operandId", "nodeId", "operandRole", "valueSequence", "sourceType",
    "featureVersionId", "inputFieldId", "valueType", "stringValue", "numberValue",
    "booleanValue", "datetimeValue", "durationValue",
  ]),
  actionVersions: Object.freeze([
    "actionVersionId", "actionCode", "actionCategory", "targetType", "versionNo", "handlerCode",
    "implementationVersion", "implementationHash", "terminalFlag", "decisionCode",
    "mergePolicy", "shadowSupportedFlag",
  ]),
  parameterDefinitions: Object.freeze([
    "parameterDefinitionId", "actionVersionId", "parameterCode", "valueType",
    "requiredFlag", "allowedSourceType", "targetParameterFlag",
  ]),
  ruleActions: Object.freeze([
    "ruleActionId", "ruleId", "actionVersionId", "sequenceNo",
  ]),
  obligations: Object.freeze([
    "obligationId", "pointVersionId", "triggerDecision", "obligationType",
    "actionVersionId", "sequenceNo", "failurePolicy", "enabledFlag",
  ]),
  limitVersions: Object.freeze([
    "limitVersionId", "limitCode", "versionNo", "subjectType", "metricType",
    "windowUnit", "windowSize", "amountLimit", "countLimit", "currencyMode",
    "fixedCurrency", "contentHash",
  ]),
  limitBindings: Object.freeze([
    "bindingId", "obligationId", "limitVersionId", "sequenceNo", "subjectSourceType",
    "subjectFeatureVersionId", "subjectInputFieldId", "amountSourceType",
    "amountFeatureVersionId", "amountInputFieldId",
  ]),
});
const DECISION_COLLECTIONS = Object.freeze([
  ...Object.keys(DECISION_COLLECTION_IDS),
  "handlerDeployments",
]);
const RISK_TABLE_SCHEMAS = new Map(RISK_TABLES.map((table) => [
  table.defKey,
  Object.freeze({
    fields: new Map(table.fields.map((field) => [field.defKey, field])),
    primaryField: table.fields.find((field) => field.primaryKey)?.defKey,
  }),
]));
const TRANSITION_COLLECTION_IDS = Object.freeze({
  relatedReleasesBefore: "releaseId",
  relatedReleasesAfter: "releaseId",
  auditEvents: "auditEventId",
  shadowRuns: "shadowRunId",
});

function isPresent(value) {
  return value !== null && value !== undefined;
}

function isCanonicalBusinessId(value, maxLength = OPAQUE_IDENTIFIER_MAX_LENGTH) {
  return typeof value === "string" && value.length <= maxLength && VISIBLE_ASCII_ID_PATTERN.test(value);
}

function isCanonicalBigIntId(value) {
  return typeof value === "string" && POSITIVE_DECIMAL_ID_PATTERN.test(value)
    && value.length <= 19 && BigInt(value) <= SIGNED_BIGINT_MAX;
}

function isCanonicalSha256(value) {
  return typeof value === "string" && SHA256_PATTERN.test(value);
}

function isCanonicalNonnegativeBigInt(value) {
  return typeof value === "string" && NONNEGATIVE_DECIMAL_PATTERN.test(value)
    && value.length <= 19 && BigInt(value) <= SIGNED_BIGINT_MAX;
}

function isCanonicalDecimal24Scale8(value) {
  return typeof value === "string" && DECIMAL_24_8_PATTERN.test(value)
    && value !== "-0.00000000";
}

function isCanonicalUtcMicrosecondTimestamp(value) {
  if (typeof value !== "string") return false;
  const match = UTC_MICROSECOND_TIMESTAMP_PATTERN.exec(value);
  if (!match) return false;
  const year = Number(match[1].slice(0, 4));
  if (year < 1000 || year > 9999) return false;
  const millisecondTimestamp = `${match[1]}.${match[2].slice(0, 3)}Z`;
  const instant = Date.parse(millisecondTimestamp);
  return Number.isFinite(instant) && new Date(instant).toISOString() === millisecondTimestamp;
}

function isCanonicalTypedLiteral(valueType, value) {
  if (valueType === "STRING") {
    return typeof value === "string" && Array.from(value).length <= STRING_LITERAL_MAX_LENGTH;
  }
  if (valueType === "NUMBER") return isCanonicalDecimal24Scale8(value);
  if (valueType === "BOOLEAN") return typeof value === "boolean";
  if (valueType === "DATETIME") return isCanonicalUtcMicrosecondTimestamp(value);
  if (valueType === "DURATION") return isCanonicalNonnegativeBigInt(value);
  return false;
}

function isCanonicalJsonValue(value, ancestors = new Set()) {
  if (value === null || typeof value === "string" || typeof value === "boolean") return true;
  if (typeof value === "number") return Number.isFinite(value) && !Object.is(value, -0);
  if (typeof value !== "object" || ancestors.has(value)) return false;
  const prototype = Object.getPrototypeOf(value);
  if (Array.isArray(value)) {
    if (prototype !== Array.prototype || Object.getOwnPropertySymbols(value).length > 0) return false;
    const names = Object.getOwnPropertyNames(value);
    const descriptors = Object.getOwnPropertyDescriptors(value);
    if (names.length !== value.length + 1 || !names.includes("length")) return false;
    const indexDescriptors = Array.from({ length: value.length }, (_, index) => descriptors[String(index)]);
    if (indexDescriptors.some((descriptor) => (
      !descriptor || !descriptor.enumerable || !("value" in descriptor)
    ))) return false;
    ancestors.add(value);
    const valid = indexDescriptors.every((descriptor) => isCanonicalJsonValue(descriptor.value, ancestors));
    ancestors.delete(value);
    return valid;
  }
  if (prototype !== Object.prototype && prototype !== null) return false;
  if (Object.getOwnPropertySymbols(value).length > 0) return false;
  const names = Object.getOwnPropertyNames(value);
  const descriptors = Object.getOwnPropertyDescriptors(value);
  if (names.some((name) => !descriptors[name].enumerable || !("value" in descriptors[name]))) return false;
  ancestors.add(value);
  const valid = names.every((name) => isCanonicalJsonValue(descriptors[name].value, ancestors));
  ancestors.delete(value);
  return valid;
}

function rowsOf(fixture, key) {
  return Array.isArray(fixture?.[key]) ? fixture[key] : [];
}

function uniqueRowsById(rows, idField) {
  const counts = new Map();
  for (const row of rows) {
    const id = row?.[idField];
    if (isCanonicalBigIntId(id)) counts.set(id, (counts.get(id) ?? 0) + 1);
  }
  return rows.filter((row) => isCanonicalBigIntId(row?.[idField]) && counts.get(row[idField]) === 1);
}

function transitionRows(fixture, key) {
  return uniqueRowsById(rowsOf(fixture, key), TRANSITION_COLLECTION_IDS[key]);
}

function createCollector(releaseId) {
  const errors = [];
  const add = (checkCode, errorCode, location = {}) => {
    const error = {
      check_code: checkCode,
      severity: "ERROR",
      error_code: errorCode,
      release_id: String(releaseId ?? "UNKNOWN"),
    };
    for (const key of ["rule_code", "node_id", "action_code", "path"]) {
      if (isPresent(location[key])) error[key] = String(location[key]);
    }
    errors.push(error);
  };
  return { add, finish: () => stableErrors(errors) };
}

function stableErrors(errors) {
  const keys = new Set();
  const unique = [];
  for (const error of errors) {
    const key = JSON.stringify(error);
    if (keys.has(key)) continue;
    keys.add(key);
    unique.push(error);
  }
  return unique.sort((left, right) => {
    const leftRank = CHECK_ORDER.get(left.check_code) ?? Number.MAX_SAFE_INTEGER;
    const rightRank = CHECK_ORDER.get(right.check_code) ?? Number.MAX_SAFE_INTEGER;
    if (leftRank !== rightRank) return leftRank - rightRank;
    for (const field of ["release_id", "rule_code", "node_id", "action_code", "error_code", "path"]) {
      const compared = String(left[field] ?? "").localeCompare(String(right[field] ?? ""));
      if (compared !== 0) return compared;
    }
    return 0;
  });
}

function indexRows(rows, idField, add, path) {
  const index = new Map();
  const duplicateIds = new Set();
  for (const row of rows) {
    const id = row?.[idField];
    if (!isPresent(id)) {
      add("REFERENCE_INTEGRITY", "REFERENCE_MISSING_ID", { path });
      continue;
    }
    if (typeof id !== "string") {
      add("REFERENCE_INTEGRITY", "REFERENCE_ID_NOT_STRING", { path: `${path}.${idField}` });
      continue;
    }
    if (!isCanonicalBigIntId(id)) {
      add("REFERENCE_INTEGRITY", "REFERENCE_ID_INVALID", { path: `${path}.${idField}` });
      continue;
    }
    if (duplicateIds.has(id) || index.has(id)) {
      add("REFERENCE_INTEGRITY", "REFERENCE_DUPLICATE_ID", { path: `${path}.${id}` });
      index.delete(id);
      duplicateIds.add(id);
      continue;
    }
    index.set(id, row);
  }
  return index;
}

function validateRequiredCollections(fixture, add) {
  for (const key of DECISION_COLLECTIONS) {
    if (!Array.isArray(fixture?.[key])) add("REFERENCE_INTEGRITY", "REFERENCE_COLLECTION_MISSING", { path: key });
  }
}

function identifierKind(fieldName) {
  if (VISIBLE_IDENTIFIER_FIELDS.has(fieldName)) return "VISIBLE";
  if (fieldName === "id" || fieldName.endsWith("Id") || fieldName.endsWith("_id")) return "BIGINT";
  return null;
}

function validateBusinessIdTypes(
  value,
  path,
  add,
  checkCode = "REFERENCE_INTEGRITY",
  errorCode = "REFERENCE_ID_NOT_STRING",
  invalidErrorCode = "REFERENCE_ID_INVALID",
) {
  if (Array.isArray(value)) {
    value.forEach((entry) => validateBusinessIdTypes(
      entry, path, add, checkCode, errorCode, invalidErrorCode,
    ));
    return;
  }
  if (!value || typeof value !== "object") return;
  for (const [key, fieldValue] of Object.entries(value)) {
    // Cross-row rows are physical-schema fixtures. Their identifiers, nullability,
    // lengths and hashes are validated from the Risk Manifest below; applying the
    // camel/snake-case business-fixture heuristic here would misclassify legitimate
    // opaque VARCHAR fields such as decision_evaluation.evaluation_request_id.
    if (path === "" && key === "crossRowRows") continue;
    const fieldPath = path ? `${path}.${key}` : key;
    const kind = identifierKind(key);
    const canonical = kind === "VISIBLE" ? isCanonicalBusinessId : isCanonicalBigIntId;
    if (kind && isPresent(fieldValue) && typeof fieldValue !== "string") {
      add(checkCode, errorCode, { path: fieldPath });
    } else if (kind && isPresent(fieldValue) && !canonical(fieldValue)) {
      add(checkCode, invalidErrorCode, { path: fieldPath });
    }
    validateBusinessIdTypes(fieldValue, fieldPath, add, checkCode, errorCode, invalidErrorCode);
  }
}

function validateTransitionClosure(fixture, add) {
  for (const [key, idField] of Object.entries(TRANSITION_COLLECTION_IDS)) {
    const rows = fixture?.[key];
    if (!Array.isArray(rows)) {
      add("STATE_TRANSITION", "STATE_COLLECTION_MISSING", { path: key });
      continue;
    }
    const seen = new Set();
    for (const row of rows) {
      const id = row?.[idField];
      if (!isPresent(id)) add("STATE_TRANSITION", "STATE_ID_MISSING", { path: `${key}.${idField}` });
      else if (typeof id !== "string") add("STATE_TRANSITION", "STATE_ID_NOT_STRING", { path: `${key}.${idField}` });
      else if (!isCanonicalBigIntId(id)) add("STATE_TRANSITION", "STATE_ID_INVALID", { path: `${key}.${idField}` });
      else if (seen.has(id)) add("STATE_TRANSITION", "STATE_ID_DUPLICATE", { path: `${key}.${id}` });
      seen.add(id);
    }
  }
  for (const event of rowsOf(fixture, "auditEvents")) {
    if (typeof event?.auditEventId === "string" && !isCanonicalBigIntId(event.auditEventId)) {
      add("STATE_TRANSITION", "STATE_AUDIT_EVENT_ID_INVALID", { path: `auditEvents.${event.auditEventId}` });
    }
    if (!isCanonicalUtcMicrosecondTimestamp(event?.eventTime)) {
      add("STATE_TRANSITION", "STATE_EVENT_TIME_INVALID", { path: `auditEvents.${event?.auditEventId ?? "missing"}.eventTime` });
    }
  }
  validateBusinessIdTypes(fixture, "", add, "STATE_TRANSITION", "STATE_ID_NOT_STRING", "STATE_ID_INVALID");
  for (const [path, release, required] of [
    ["candidateBefore", fixture?.candidateBefore, false],
    ["candidateAfter", fixture?.candidateAfter, true],
  ]) {
    if (!release) {
      if (required) add("STATE_TRANSITION", "STATE_RELEASE_ROOT_MISSING", { path });
      continue;
    }
    for (const field of ["releaseId", "decisionPointId", "pointVersionId"]) {
      if (!isCanonicalBigIntId(release[field])) add("STATE_TRANSITION", "STATE_ID_INVALID", { path: `${path}.${field}` });
    }
    if (!isCanonicalSha256(release.contentHash)) {
      add("STATE_TRANSITION", "STATE_CONTENT_HASH_INVALID", { path: `${path}.contentHash` });
    }
  }
  for (const key of ["relatedReleasesBefore", "relatedReleasesAfter"]) {
    for (const release of rowsOf(fixture, key)) {
      if (!releaseIdentityIsTyped(release)) {
        add("STATE_TRANSITION", "STATE_RELATED_RELEASE_IDENTITY_INVALID", {
          path: `${key}.${release?.releaseId ?? "missing"}`,
        });
      }
      if (!isCanonicalSha256(release?.contentHash)) {
        add("STATE_TRANSITION", "STATE_CONTENT_HASH_INVALID", {
          path: `${key}.${release?.releaseId ?? "missing"}.contentHash`,
        });
      }
      if (!releaseSlotMatchesStatus(release)) {
        add("STATE_TRANSITION", "STATE_RELATED_RELEASE_SLOT_MISMATCH", {
          path: `${key}.${release?.releaseId ?? "missing"}.activeSlot`,
        });
      }
    }
  }
}

function validateUniqueTuple(rows, fields, add, path) {
  const seen = new Set();
  for (const row of rows) {
    const key = JSON.stringify(fields.map((field) => row[field]));
    if (seen.has(key)) add("REFERENCE_INTEGRITY", "REFERENCE_DUPLICATE_BINDING", { path });
    seen.add(key);
  }
}

function sequenceIsContinuous(rows, field = "sequenceNo") {
  const values = rows.map((row) => row[field]).sort((left, right) => left - right);
  return values.every((value, index) => Number.isInteger(value) && value === index + 1);
}

function ruleLocation(rule, node, path) {
  return {
    rule_code: rule?.ruleCode,
    node_id: node?.nodeId,
    path,
  };
}

function buildDecisionIndexes(fixture, add) {
  return {
    inputs: indexRows(rowsOf(fixture, "inputFields"), "inputFieldId", add, "inputFields"),
    features: indexRows(rowsOf(fixture, "featureVersions"), "featureVersionId", add, "featureVersions"),
    ruleSets: indexRows(rowsOf(fixture, "ruleSetVersions"), "ruleSetVersionId", add, "ruleSetVersions"),
    rules: indexRows(rowsOf(fixture, "rules"), "ruleId", add, "rules"),
    nodes: indexRows(rowsOf(fixture, "nodes"), "nodeId", add, "nodes"),
    actions: indexRows(rowsOf(fixture, "actionVersions"), "actionVersionId", add, "actionVersions"),
    definitions: indexRows(rowsOf(fixture, "parameterDefinitions"), "parameterDefinitionId", add, "parameterDefinitions"),
    ruleActions: indexRows(rowsOf(fixture, "ruleActions"), "ruleActionId", add, "ruleActions"),
    obligations: indexRows(rowsOf(fixture, "obligations"), "obligationId", add, "obligations"),
    limitVersions: indexRows(rowsOf(fixture, "limitVersions"), "limitVersionId", add, "limitVersions"),
    limitBindings: indexRows(rowsOf(fixture, "limitBindings"), "bindingId", add, "limitBindings"),
    pointFeatures: indexRows(rowsOf(fixture, "pointFeatures"), "bindingId", add, "pointFeatures"),
    featureDependencies: indexRows(rowsOf(fixture, "featureDependencies"), "dependencyId", add, "featureDependencies"),
    pointRuleSets: indexRows(rowsOf(fixture, "pointRuleSets"), "bindingId", add, "pointRuleSets"),
    operands: indexRows(rowsOf(fixture, "operands"), "operandId", add, "operands"),
    actionParameters: indexRows(rowsOf(fixture, "actionParameters"), "parameterId", add, "actionParameters"),
  };
}

function validateReleaseRoots(fixture, indexes, add) {
  const { release, decisionPoint, pointVersion } = fixture;
  if (!release || !decisionPoint || !pointVersion) {
    add("REFERENCE_INTEGRITY", "REFERENCE_RELEASE_ROOT_MISSING", { path: "release" });
    return;
  }
  if (!isCanonicalBigIntId(release.releaseId)) {
    add("REFERENCE_INTEGRITY", "REFERENCE_ID_INVALID", { path: "release.releaseId" });
  }
  if (!isCanonicalSha256(release.contentHash)) {
    add("REFERENCE_INTEGRITY", "RELEASE_CONTENT_HASH_INVALID", { path: "release.contentHash" });
  }
  if (release.decisionPointId !== decisionPoint.decisionPointId
    || pointVersion.decisionPointId !== decisionPoint.decisionPointId
    || release.pointVersionId !== pointVersion.pointVersionId) {
    add("REFERENCE_INTEGRITY", "REFERENCE_POINT_MISMATCH", { path: "release" });
  }

  const allowedRuleSetIds = new Set();
  for (const binding of rowsOf(fixture, "pointRuleSets")) {
    if (binding.pointVersionId !== pointVersion.pointVersionId) {
      add("REFERENCE_INTEGRITY", "REFERENCE_POINT_VERSION_MISMATCH", { path: `pointRuleSets.${binding.bindingId}` });
    }
    const ruleSet = indexes.ruleSets.get(binding.ruleSetVersionId);
    if (!ruleSet) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `pointRuleSets.${binding.bindingId}.ruleSetVersionId` });
    else if (ruleSet.decisionPointId !== decisionPoint.decisionPointId) {
      add("REFERENCE_INTEGRITY", "REFERENCE_POINT_MISMATCH", { path: `ruleSetVersions.${ruleSet.ruleSetVersionId}` });
    }
    if (ruleSet && ruleSet.status !== "FROZEN") {
      add("REFERENCE_INTEGRITY", "RULE_SET_VERSION_NOT_FROZEN", { path: `ruleSetVersions.${ruleSet.ruleSetVersionId}` });
    }
    allowedRuleSetIds.add(binding.ruleSetVersionId);
  }
  for (const rule of rowsOf(fixture, "rules")) {
    if (!indexes.ruleSets.has(rule.ruleSetVersionId)) {
      add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { rule_code: rule.ruleCode, path: `rules.${rule.ruleId}.ruleSetVersionId` });
    } else if (!allowedRuleSetIds.has(rule.ruleSetVersionId)) {
      add("REFERENCE_INTEGRITY", "RULE_SET_NOT_ALLOWLISTED", { rule_code: rule.ruleCode, path: `rules.${rule.ruleId}` });
    }
  }
}

function decisionAllowLists(fixture) {
  return {
    featureIds: new Set(rowsOf(fixture, "pointFeatures").map((row) => row.featureVersionId)),
    inputIds: new Set(rowsOf(fixture, "inputFields").map((row) => row.inputFieldId)),
  };
}

function validateDecisionReferences(fixture, indexes, add) {
  validateReleaseRoots(fixture, indexes, add);
  const { featureIds } = decisionAllowLists(fixture);
  const pointVersionId = fixture.pointVersion?.pointVersionId;
  validateUniqueTuple(rowsOf(fixture, "pointFeatures"), ["pointVersionId", "featureVersionId"], add, "pointFeatures");
  validateUniqueTuple(rowsOf(fixture, "pointRuleSets"), ["pointVersionId", "ruleSetVersionId"], add, "pointRuleSets");
  validateUniqueTuple(rowsOf(fixture, "limitBindings"), ["obligationId", "limitVersionId"], add, "limitBindings");
  validateUniqueTuple(rowsOf(fixture, "limitBindings"), ["obligationId", "sequenceNo"], add, "limitBindings");

  for (const binding of rowsOf(fixture, "pointFeatures")) {
    if (binding.pointVersionId !== pointVersionId) {
      add("REFERENCE_INTEGRITY", "REFERENCE_POINT_VERSION_MISMATCH", { path: `pointFeatures.${binding.bindingId}` });
    }
    if (!indexes.features.has(binding.featureVersionId)) {
      add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `pointFeatures.${binding.bindingId}.featureVersionId` });
    }
  }
  for (const input of rowsOf(fixture, "inputFields")) {
    if (input.pointVersionId !== pointVersionId) {
      add("REFERENCE_INTEGRITY", "REFERENCE_POINT_VERSION_MISMATCH", { path: `inputFields.${input.inputFieldId}` });
    }
  }
  for (const node of rowsOf(fixture, "nodes")) {
    const rule = indexes.rules.get(node.ruleId);
    if (!rule) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { node_id: node.nodeId, path: `nodes.${node.nodeId}.ruleId` });
    if (!isPresent(node.parentNodeId)) continue;
    const parent = indexes.nodes.get(node.parentNodeId);
    if (!parent) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", ruleLocation(rule, node, `nodes.${node.nodeId}.parentNodeId`));
    else if (parent.ruleId !== node.ruleId) add("REFERENCE_INTEGRITY", "REFERENCE_PARENT_MISMATCH", ruleLocation(rule, node, `nodes.${node.nodeId}`));
  }
  for (const operand of rowsOf(fixture, "operands")) {
    const node = indexes.nodes.get(operand.nodeId);
    const rule = indexes.rules.get(node?.ruleId);
    if (!node) {
      add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `operands.${operand.operandId}.nodeId` });
      continue;
    }
    if (operand.sourceType === "FEATURE") {
      if (!indexes.features.has(operand.featureVersionId)) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", ruleLocation(rule, node, `operands.${operand.operandId}.featureVersionId`));
      else if (!featureIds.has(operand.featureVersionId)) add("REFERENCE_INTEGRITY", "FEATURE_NOT_ALLOWLISTED", ruleLocation(rule, node, `operands.${operand.operandId}`));
    }
    if (operand.sourceType === "CONTEXT") {
      const input = indexes.inputs.get(operand.inputFieldId);
      if (!input) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", ruleLocation(rule, node, `operands.${operand.operandId}.inputFieldId`));
      else if (input.pointVersionId !== pointVersionId || input.ruleReferenceFlag !== true) {
        add("REFERENCE_INTEGRITY", "CONTEXT_RULE_REFERENCE_FORBIDDEN", ruleLocation(rule, node, `operands.${operand.operandId}`));
      }
    }
  }
  for (const ruleAction of rowsOf(fixture, "ruleActions")) {
    const rule = indexes.rules.get(ruleAction.ruleId);
    const action = indexes.actions.get(ruleAction.actionVersionId);
    if (!rule) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `ruleActions.${ruleAction.ruleActionId}.ruleId` });
    if (!action) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { rule_code: rule?.ruleCode, path: `ruleActions.${ruleAction.ruleActionId}.actionVersionId` });
  }
  for (const dependency of rowsOf(fixture, "featureDependencies")) {
    if (!indexes.features.has(dependency.featureVersionId)) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `featureDependencies.${dependency.dependencyId}.featureVersionId` });
    if (!indexes.features.has(dependency.dependencyFeatureVersionId)) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `featureDependencies.${dependency.dependencyId}.dependencyFeatureVersionId` });
  }
  for (const definition of rowsOf(fixture, "parameterDefinitions")) {
    if (!indexes.actions.has(definition.actionVersionId)) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `parameterDefinitions.${definition.parameterDefinitionId}.actionVersionId` });
  }
  for (const parameter of rowsOf(fixture, "actionParameters")) {
    if (!indexes.ruleActions.has(parameter.ruleActionId)) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `actionParameters.${parameter.parameterId}.ruleActionId` });
    if (!indexes.definitions.has(parameter.parameterDefinitionId)) add("REFERENCE_INTEGRITY", "REFERENCE_MISSING", { path: `actionParameters.${parameter.parameterId}.parameterDefinitionId` });
  }
  for (const binding of rowsOf(fixture, "limitBindings")) {
    const location = { path: `limitBindings.${binding.bindingId}` };
    if (!indexes.obligations.has(binding.obligationId)
      || !indexes.limitVersions.has(binding.limitVersionId)) {
      add("REFERENCE_INTEGRITY", "LIMIT_BINDING_REFERENCE_INVALID", location);
    }
    for (const [sourceType, featureId, inputId] of [
      [binding.subjectSourceType, binding.subjectFeatureVersionId, binding.subjectInputFieldId],
      [binding.amountSourceType, binding.amountFeatureVersionId, binding.amountInputFieldId],
    ]) {
      if (sourceType === "FEATURE" && !indexes.features.has(featureId)) {
        add("REFERENCE_INTEGRITY", "LIMIT_BINDING_REFERENCE_INVALID", location);
      }
      if (sourceType === "CONTEXT" && !indexes.inputs.has(inputId)) {
        add("REFERENCE_INTEGRITY", "LIMIT_BINDING_REFERENCE_INVALID", location);
      }
    }
  }
}

function childrenByParent(nodes) {
  const children = new Map();
  for (const node of nodes) {
    if (!isPresent(node.parentNodeId)) continue;
    if (!children.has(node.parentNodeId)) children.set(node.parentNodeId, []);
    children.get(node.parentNodeId).push(node);
  }
  for (const childRows of children.values()) {
    childRows.sort((left, right) => String(left.nodeId).localeCompare(String(right.nodeId)));
  }
  return children;
}

function validateRuleGraph(rule, nodes, children, add) {
  const roots = nodes.filter((node) => !isPresent(node.parentNodeId));
  const firstRoot = [...roots].sort((left, right) => String(left.nodeId).localeCompare(String(right.nodeId)))[0];
  if (roots.length !== 1) add("AST_SHAPE", "AST_ROOT_COUNT", ruleLocation(rule, firstRoot, `rules.${rule.ruleId}.nodes`));

  const state = new Map();
  const visit = (node) => {
    const current = state.get(node.nodeId) ?? 0;
    if (current === 1) {
      add("AST_SHAPE", "AST_CYCLE", ruleLocation(rule, node, `nodes.${node.nodeId}`));
      return;
    }
    if (current === 2) return;
    state.set(node.nodeId, 1);
    for (const child of children.get(node.nodeId) ?? []) {
      if (child.ruleId === rule.ruleId) visit(child);
    }
    state.set(node.nodeId, 2);
  };
  for (const node of [...nodes].sort((left, right) => String(left.nodeId).localeCompare(String(right.nodeId)))) visit(node);

  if (roots.length === 1) {
    const reached = new Set();
    const reach = (node) => {
      if (reached.has(node.nodeId)) return;
      reached.add(node.nodeId);
      for (const child of children.get(node.nodeId) ?? []) if (child.ruleId === rule.ruleId) reach(child);
    };
    reach(roots[0]);
    for (const node of nodes) {
      if (!reached.has(node.nodeId)) add("AST_SHAPE", "AST_NODE_UNREACHABLE", ruleLocation(rule, node, `nodes.${node.nodeId}`));
    }
  }
}

function validateNodeShape(rule, node, childRows, add) {
  const location = ruleLocation(rule, node, `nodes.${node.nodeId}`);
  if (childRows.length > 0 && !sequenceIsContinuous(childRows)) add("AST_SHAPE", "AST_CHILD_SEQUENCE", location);
  if (node.nodeType === "GROUP") {
    if (!new Set(["AND", "OR", "NOT"]).has(node.groupOperator) || isPresent(node.comparisonOperator)) {
      add("AST_SHAPE", "AST_NODE_METADATA_INVALID", location);
    }
    const validArity = node.groupOperator === "NOT" ? childRows.length === 1 : childRows.length >= 2;
    if (!validArity) add("AST_SHAPE", "AST_GROUP_ARITY", location);
    return;
  }
  if (node.nodeType !== "PREDICATE" || isPresent(node.groupOperator) || !isPresent(node.comparisonOperator)) {
    add("AST_SHAPE", "AST_NODE_METADATA_INVALID", location);
  }
  if (childRows.length > 0) add("AST_SHAPE", "AST_PREDICATE_HAS_CHILDREN", location);
}

function validateOperandArity(rule, node, operands, add) {
  const location = ruleLocation(rule, node, `nodes.${node.nodeId}.operands`);
  const rolesValid = operands.every((operand) => operand.operandRole === "LEFT" || operand.operandRole === "RIGHT");
  if (!rolesValid) add("AST_SHAPE", "AST_OPERAND_ROLE_INVALID", location);
  for (const role of ["LEFT", "RIGHT"]) {
    const roleRows = operands.filter((operand) => operand.operandRole === role);
    if (roleRows.length > 0 && !sequenceIsContinuous(roleRows, "valueSequence")) add("AST_SHAPE", "AST_OPERAND_SEQUENCE", location);
  }
  const scalar = operands.filter((operand) => operand.sourceType !== "LITERAL");
  const literals = operands.filter((operand) => operand.sourceType === "LITERAL");
  const left = operands.filter((operand) => operand.operandRole === "LEFT");
  const right = operands.filter((operand) => operand.operandRole === "RIGHT");
  const binary = operands.length === 2 && rolesValid && left.length === 1 && right.length === 1
    && left[0].valueSequence === 1 && right[0].valueSequence === 1;

  if (BINARY_OPERATORS.has(node.comparisonOperator) && !binary) add("AST_SHAPE", "AST_OPERAND_ARITY", location);
  if (SET_OPERATORS.has(node.comparisonOperator)) {
    const oppositeRoles = rolesValid && scalar.length === 1 && literals.length >= 1
      && literals.every((literal) => literal.operandRole !== scalar[0].operandRole);
    if (!oppositeRoles) add("AST_SHAPE", "AST_OPERAND_ARITY", location);
  }
  if (node.comparisonOperator === "BETWEEN") {
    const exact = rolesValid && scalar.length === 1 && literals.length === 2
      && literals.every((literal) => literal.operandRole !== scalar[0].operandRole)
      && sequenceIsContinuous(literals, "valueSequence");
    if (!exact) add("AST_SHAPE", "AST_OPERAND_ARITY", location);
  }
  if (NULL_OPERATORS.has(node.comparisonOperator) && !(rolesValid && operands.length === 1 && scalar.length === 1)) {
    add("AST_SHAPE", "AST_OPERAND_ARITY", location);
  }
  if (![...BINARY_OPERATORS, ...SET_OPERATORS, ...NULL_OPERATORS, "BETWEEN"].includes(node.comparisonOperator)) {
    add("AST_SHAPE", "AST_OPERATOR_UNSUPPORTED", location);
  }
}

function validateAstShape(fixture, indexes, add) {
  const nodes = rowsOf(fixture, "nodes");
  const children = childrenByParent(nodes);
  const operandsByNode = new Map();
  for (const operand of rowsOf(fixture, "operands")) {
    if (!operandsByNode.has(operand.nodeId)) operandsByNode.set(operand.nodeId, []);
    operandsByNode.get(operand.nodeId).push(operand);
  }
  for (const rule of rowsOf(fixture, "rules")) {
    const ruleNodes = nodes.filter((node) => node.ruleId === rule.ruleId);
    validateRuleGraph(rule, ruleNodes, children, add);
    for (const node of ruleNodes) {
      const childRows = (children.get(node.nodeId) ?? []).filter((child) => child.ruleId === rule.ruleId);
      validateNodeShape(rule, node, childRows, add);
      if (node.nodeType === "GROUP" && (operandsByNode.get(node.nodeId) ?? []).length > 0) {
        add("AST_SHAPE", "AST_GROUP_HAS_OPERANDS", ruleLocation(rule, node, `nodes.${node.nodeId}.operands`));
      }
      if (node.nodeType === "PREDICATE") validateOperandArity(rule, node, operandsByNode.get(node.nodeId) ?? [], add);
    }
  }
}

function typedValueCount(row) {
  return VALUE_FIELD_NAMES.filter((field) => isPresent(row[field])).length;
}

function resolveOperandType(operand, indexes, rule, node, add) {
  const location = ruleLocation(rule, node, `operands.${operand.operandId}`);
  if (operand.sourceType === "LITERAL") {
    if (typedValueCount(operand) !== 1 || !VALUE_FIELDS[operand.valueType]
      || !isPresent(operand[VALUE_FIELDS[operand.valueType]])) {
      add("AST_TYPES", "AST_LITERAL_VALUE_SHAPE", location);
    } else if (!isCanonicalTypedLiteral(operand.valueType, operand[VALUE_FIELDS[operand.valueType]])) {
      add("AST_TYPES", "AST_LITERAL_VALUE_INVALID", location);
    }
    if (isPresent(operand.featureVersionId) || isPresent(operand.inputFieldId)) {
      add("AST_TYPES", "AST_LITERAL_VALUE_SHAPE", location);
    }
    return operand.valueType;
  }
  if (typedValueCount(operand) !== 0) add("AST_TYPES", "AST_LITERAL_VALUE_SHAPE", location);
  if (operand.sourceType === "FEATURE") {
    const feature = indexes.features.get(operand.featureVersionId);
    if (feature && operand.valueType !== feature.resultValueType) add("AST_TYPES", "AST_DECLARED_TYPE_MISMATCH", location);
    if (isPresent(operand.inputFieldId)) add("AST_TYPES", "AST_DECLARED_TYPE_MISMATCH", location);
    return feature?.resultValueType ?? operand.valueType;
  }
  if (operand.sourceType === "CONTEXT") {
    const input = indexes.inputs.get(operand.inputFieldId);
    if (input && operand.valueType !== input.valueType) add("AST_TYPES", "AST_DECLARED_TYPE_MISMATCH", location);
    if (isPresent(operand.featureVersionId)) add("AST_TYPES", "AST_DECLARED_TYPE_MISMATCH", location);
    return input?.valueType ?? operand.valueType;
  }
  add("AST_TYPES", "AST_OPERAND_SOURCE_UNSUPPORTED", location);
  return operand.valueType;
}

function validateAstTypes(fixture, indexes, add) {
  const operandsByNode = new Map();
  for (const operand of rowsOf(fixture, "operands")) {
    if (!operandsByNode.has(operand.nodeId)) operandsByNode.set(operand.nodeId, []);
    operandsByNode.get(operand.nodeId).push(operand);
  }
  for (const node of rowsOf(fixture, "nodes").filter((row) => row.nodeType === "PREDICATE")) {
    const rule = indexes.rules.get(node.ruleId);
    const types = (operandsByNode.get(node.nodeId) ?? [])
      .map((operand) => resolveOperandType(operand, indexes, rule, node, add))
      .filter(isPresent);
    if (new Set(types).size > 1) add("AST_TYPES", "AST_OPERAND_TYPE_MISMATCH", ruleLocation(rule, node, `nodes.${node.nodeId}`));
    if (new Set(["GT", "GTE", "LT", "LTE", "BETWEEN"]).has(node.comparisonOperator)
      && types.some((valueType) => !ORDERING_TYPES.has(valueType))) {
      add("AST_TYPES", "AST_OPERATOR_TYPE_UNSUPPORTED", ruleLocation(rule, node, `nodes.${node.nodeId}`));
    }
  }
}

function validateFeatureDependencies(fixture, indexes, add) {
  const allowed = decisionAllowLists(fixture).featureIds;
  const dependencies = rowsOf(fixture, "featureDependencies");
  for (const dependency of dependencies) {
    if (!allowed.has(dependency.featureVersionId) || !allowed.has(dependency.dependencyFeatureVersionId)) {
      add("FEATURE_DEPENDENCY", "FEATURE_DEPENDENCY_NOT_ALLOWLISTED", { path: `featureDependencies.${dependency.dependencyId}` });
    }
  }
  const edges = new Map([...allowed].map((featureId) => [featureId, []]));
  for (const featureId of [...allowed].sort()) {
    const rows = dependencies.filter((row) => row.featureVersionId === featureId)
      .sort((left, right) => String(left.dependencyId).localeCompare(String(right.dependencyId)));
    if (rows.length > 0 && !sequenceIsContinuous(rows)) {
      add("FEATURE_DEPENDENCY", "FEATURE_DEPENDENCY_SEQUENCE", { path: `featureDependencies.${featureId}` });
    }
    for (const dependency of rows) {
      if (dependency.featureVersionId === dependency.dependencyFeatureVersionId) {
        add("FEATURE_DEPENDENCY", "FEATURE_DEPENDENCY_SELF", { path: `featureDependencies.${dependency.dependencyId}` });
      }
      if (!indexes.features.has(dependency.dependencyFeatureVersionId) || !allowed.has(dependency.dependencyFeatureVersionId)) {
        add("FEATURE_DEPENDENCY", "FEATURE_DEPENDENCY_NOT_ALLOWLISTED", { path: `featureDependencies.${dependency.dependencyId}` });
      }
      edges.get(featureId).push(dependency.dependencyFeatureVersionId);
    }
  }
  const state = new Map();
  const visit = (featureId) => {
    if (state.get(featureId) === 1) {
      add("FEATURE_DEPENDENCY", "FEATURE_DEPENDENCY_CYCLE", { path: `featureDependencies.${featureId}` });
      return;
    }
    if (state.get(featureId) === 2) return;
    state.set(featureId, 1);
    for (const dependencyId of edges.get(featureId) ?? []) if (edges.has(dependencyId)) visit(dependencyId);
    state.set(featureId, 2);
  };
  for (const featureId of [...allowed].sort()) visit(featureId);
}

function validateHandlerVersion(version, handlerType, deployments, add, location) {
  if (version.status !== "ACTIVE") add("HANDLER_HASH", "HANDLER_VERSION_INACTIVE", location);
  if (!isPresent(version.implementationHash)) add("HANDLER_HASH", "HANDLER_EXPECTED_HASH_MISSING", location);
  if (!isCanonicalSha256(version.implementationHash)) add("HANDLER_HASH", "HANDLER_HASH_FORMAT_INVALID", location);
  const matches = deployments.filter((deployment) => deployment.handlerType === handlerType
    && deployment.handlerCode === version.handlerCode
    && deployment.implementationVersion === version.implementationVersion);
  if (matches.length === 0) {
    add("HANDLER_HASH", "HANDLER_REGISTRATION_MISSING", location);
    return;
  }
  if (matches.length > 1) add("HANDLER_HASH", "HANDLER_REGISTRATION_AMBIGUOUS", location);
  for (const deployment of matches) {
    if (deployment.deploymentStatus !== "DEPLOYED") add("HANDLER_HASH", "HANDLER_NOT_DEPLOYED", location);
    if (!isCanonicalSha256(deployment.implementationHash)) add("HANDLER_HASH", "HANDLER_HASH_FORMAT_INVALID", location);
    if (deployment.implementationHash !== version.implementationHash) add("HANDLER_HASH", "HANDLER_HASH_MISMATCH", location);
  }
}

function usedActionIds(fixture) {
  return new Set([
    ...rowsOf(fixture, "ruleActions").map((row) => row.actionVersionId),
    ...rowsOf(fixture, "obligations").map((row) => row.actionVersionId).filter(isPresent),
  ]);
}

function validateHandlers(fixture, indexes, add) {
  const deployments = rowsOf(fixture, "handlerDeployments");
  const featureIds = decisionAllowLists(fixture).featureIds;
  for (const featureId of featureIds) {
    const feature = indexes.features.get(featureId);
    if (feature) validateHandlerVersion(feature, "FEATURE", deployments, add, { path: `featureVersions.${featureId}` });
  }
  for (const actionId of usedActionIds(fixture)) {
    const action = indexes.actions.get(actionId);
    if (action) validateHandlerVersion(action, "ACTION", deployments, add, { action_code: action.actionCode, path: `actionVersions.${actionId}` });
  }
}

function validateParameterValue(parameter, definition, fixture, indexes, rule, action, add) {
  const location = { rule_code: rule?.ruleCode, action_code: action?.actionCode, path: `actionParameters.${parameter.parameterId}` };
  if (parameter.sourceType !== definition.allowedSourceType) add("ACTION_PARAMETERS", "ACTION_PARAMETER_SOURCE_MISMATCH", location);
  if (parameter.sourceType === "LITERAL") {
    if (typedValueCount(parameter) !== 1 || !VALUE_FIELDS[definition.valueType]
      || !isPresent(parameter[VALUE_FIELDS[definition.valueType]])) {
      add("ACTION_PARAMETERS", "ACTION_PARAMETER_VALUE_SHAPE", location);
    } else if (!isCanonicalTypedLiteral(definition.valueType, parameter[VALUE_FIELDS[definition.valueType]])) {
      add("ACTION_PARAMETERS", "ACTION_PARAMETER_LITERAL_INVALID", location);
    }
    if (isPresent(parameter.featureVersionId) || isPresent(parameter.inputFieldId)) add("ACTION_PARAMETERS", "ACTION_PARAMETER_VALUE_SHAPE", location);
    return;
  }
  if (typedValueCount(parameter) !== 0) add("ACTION_PARAMETERS", "ACTION_PARAMETER_VALUE_SHAPE", location);
  if (parameter.sourceType === "FEATURE") {
    if (isPresent(parameter.inputFieldId)) add("ACTION_PARAMETERS", "ACTION_PARAMETER_VALUE_SHAPE", location);
    const feature = indexes.features.get(parameter.featureVersionId);
    if (!feature || !decisionAllowLists(fixture).featureIds.has(parameter.featureVersionId)) {
      add("ACTION_PARAMETERS", "FEATURE_NOT_ALLOWLISTED", location);
    } else if (feature.resultValueType !== definition.valueType) add("ACTION_PARAMETERS", "ACTION_PARAMETER_TYPE_MISMATCH", location);
    return;
  }
  if (parameter.sourceType === "CONTEXT") {
    if (isPresent(parameter.featureVersionId)) add("ACTION_PARAMETERS", "ACTION_PARAMETER_VALUE_SHAPE", location);
    const input = indexes.inputs.get(parameter.inputFieldId);
    if (!input || input.pointVersionId !== fixture.pointVersion?.pointVersionId || input.actionParameterFlag !== true) {
      add("ACTION_PARAMETERS", "CONTEXT_ACTION_PARAMETER_FORBIDDEN", location);
    } else if (input.valueType !== definition.valueType) add("ACTION_PARAMETERS", "ACTION_PARAMETER_TYPE_MISMATCH", location);
    return;
  }
  add("ACTION_PARAMETERS", "ACTION_PARAMETER_SOURCE_MISMATCH", location);
}

function actionParameterProjection(parameter) {
  return {
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
  };
}

function immutableContentProjection(row, fields, path) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    throw new TypeError(`${path} must be an object`);
  }
  const projection = {};
  for (const field of fields) {
    const value = row[field] ?? null;
    if (value !== null && typeof value !== "string" && typeof value !== "boolean"
      && !(typeof value === "number" && Number.isFinite(value) && !Object.is(value, -0))) {
      throw new TypeError(`${path}.${field} must be a canonical scalar or null`);
    }
    projection[field] = value;
  }
  return projection;
}

function compareContentIdentity(left, right) {
  const leftText = String(left ?? "");
  const rightText = String(right ?? "");
  if (isCanonicalBigIntId(leftText) && isCanonicalBigIntId(rightText)) {
    const leftId = BigInt(leftText);
    const rightId = BigInt(rightText);
    return leftId < rightId ? -1 : leftId > rightId ? 1 : 0;
  }
  return leftText.localeCompare(rightText);
}

function projectedRowBytes(row) {
  if (!isCanonicalJsonValue(row)) throw new TypeError("Release content projection must be canonical JSON");
  return JSON.stringify(row);
}

function canonicalCollectionProjection(fixture, collection, idField) {
  const fields = RELEASE_CONTENT_COLLECTION_FIELDS[collection];
  if (!fields) throw new TypeError(`Missing Release projection for ${collection}`);
  return rowsOf(fixture, collection)
    .map((row, index) => {
      const projection = immutableContentProjection(row, fields, `${collection}[${index}]`);
      return { projection, bytes: projectedRowBytes(projection) };
    })
    .sort((left, right) => (
      compareContentIdentity(left.projection[idField], right.projection[idField])
      || left.bytes.localeCompare(right.bytes)
    ))
    .map(({ projection }) => projection);
}

function genericActionParameterProjection(parameter, definition, index) {
  const path = `actionParameters[${index}]`;
  const projection = actionParameterProjection(parameter);
  if (!isCanonicalBigIntId(projection.ruleActionId)
    || !isCanonicalBigIntId(projection.parameterDefinitionId)
    || !Number.isSafeInteger(projection.valueSequence) || projection.valueSequence <= 0
    || !new Set(["FEATURE", "CONTEXT", "LITERAL"]).has(projection.sourceType)
    || !definition || definition.parameterDefinitionId !== projection.parameterDefinitionId) {
    throw new TypeError(`${path} has an invalid generic identity`);
  }
  const presentValueFields = VALUE_FIELD_NAMES.filter((field) => projection[field] !== null);
  if (projection.sourceType === "FEATURE") {
    if (!isCanonicalBigIntId(projection.featureVersionId)
      || projection.inputFieldId !== null || presentValueFields.length !== 0) {
      throw new TypeError(`${path} has an invalid FEATURE source shape`);
    }
  } else if (projection.sourceType === "CONTEXT") {
    if (projection.featureVersionId !== null
      || !isCanonicalBigIntId(projection.inputFieldId) || presentValueFields.length !== 0) {
      throw new TypeError(`${path} has an invalid CONTEXT source shape`);
    }
  } else {
    const valueField = VALUE_FIELDS[definition.valueType];
    if (projection.featureVersionId !== null || projection.inputFieldId !== null
      || presentValueFields.length !== 1 || presentValueFields[0] !== valueField
      || !isCanonicalTypedLiteral(definition.valueType, projection[valueField])) {
      throw new TypeError(`${path} has an invalid generic Literal`);
    }
  }
  projectedRowBytes(projection);
  return projection;
}

function compareActionParameterIdentity(left, right) {
  return compareContentIdentity(left.parameterDefinitionId, right.parameterDefinitionId)
    || left.valueSequence - right.valueSequence
    || compareContentIdentity(left.ruleActionId, right.ruleActionId)
    || projectedRowBytes(left).localeCompare(projectedRowBytes(right));
}

function canonicalActionParameterComponents(fixture) {
  const definitions = rowsOf(fixture, "parameterDefinitions");
  const definitionsById = new Map(
    uniqueRowsById(definitions, "parameterDefinitionId").map((definition) => [
      definition.parameterDefinitionId, definition,
    ]),
  );
  const settlementActionIds = new Set(rowsOf(fixture, "actionVersions")
    .filter((action) => settlementControlActionCandidate(
      action,
      definitions.filter((definition) => definition.actionVersionId === action.actionVersionId),
    ))
    .map((action) => action.actionVersionId));
  const settlementRuleActionIds = new Set(rowsOf(fixture, "ruleActions")
    .filter((ruleAction) => settlementActionIds.has(ruleAction.actionVersionId))
    .map((ruleAction) => ruleAction.ruleActionId));
  const settlementParameters = [];
  const genericParameters = [];
  rowsOf(fixture, "actionParameters").forEach((parameter, index) => {
    if (settlementRuleActionIds.has(parameter.ruleActionId)) {
      settlementParameters.push(actionParameterProjection(parameter));
      return;
    }
    genericParameters.push(genericActionParameterProjection(
      parameter,
      definitionsById.get(parameter.parameterDefinitionId),
      index,
    ));
  });
  const settlement = JSON.parse(
    canonicalizeSettlementActionParameterIdentity(settlementParameters),
  ).actionParameters;
  return {
    genericActionParameters: genericParameters.sort(compareActionParameterIdentity),
    settlementActionParameters: settlement,
  };
}

function canonicalDecisionReleaseContent(fixture) {
  const collections = {};
  for (const [collection, idField] of Object.entries(DECISION_COLLECTION_IDS)) {
    if (collection === "actionParameters") {
      collections[collection] = canonicalActionParameterComponents(fixture);
      continue;
    }
    collections[collection] = canonicalCollectionProjection(fixture, collection, idField);
  }
  const projection = {
    release: immutableContentProjection(fixture.release, [
      "releaseId", "decisionPointId", "pointVersionId", "environmentCode",
    ], "release"),
    decisionPoint: immutableContentProjection(fixture.decisionPoint, [
      "decisionPointId", "pointCode", "businessDomain",
    ], "decisionPoint"),
    pointVersion: immutableContentProjection(fixture.pointVersion, [
      "pointVersionId", "decisionPointId", "versionNo", "inputContractCode",
      "inputContractVersion", "outputContractCode", "defaultDecision", "evaluationStrategy",
      "failureStrategy", "timeoutMs", "contentHash",
    ], "pointVersion"),
    collections,
  };
  if (!isCanonicalJsonValue(projection)) {
    throw new TypeError("Release content projection must be canonical JSON");
  }
  return JSON.stringify(projection);
}

function decisionReleaseContentHash(fixture) {
  return createHash("sha256").update(canonicalDecisionReleaseContent(fixture), "utf8").digest("hex");
}

function settlementControlActionCandidate(action, definitions) {
  const parameterCodes = new Set(definitions.map((definition) => definition.parameterCode));
  return action.actionCode === "CREATE_SETTLEMENT_CONTROL"
    || action.targetType === "SETTLEMENT_CONTROL"
    || [...SETTLEMENT_CONTROL_PARAMETER_CODES].every((code) => parameterCodes.has(code));
}

function settlementActionContractIsValid(action) {
  return action.actionCode === "CREATE_SETTLEMENT_CONTROL"
    && action.actionCategory === "SETTLEMENT"
    && action.targetType === "SETTLEMENT_CONTROL"
    && action.terminalFlag === false
    && action.decisionCode === null
    && action.status === "ACTIVE"
    && action.shadowSupportedFlag === true;
}

function settlementScopeLiteralIsValid(parameter, parameterCode) {
  const value = parameter.stringValue;
  if (parameterCode === "SCOPE_OPERATING_ENTITY_KEY") {
    return value === "*" || isCanonicalBigIntId(value);
  }
  if (parameterCode === "SCOPE_CURRENCY_KEY") {
    return value === "*" || (typeof value === "string" && ISO_CURRENCY_PATTERN.test(value));
  }
  return value === "*" || (typeof value === "string" && ISO_COUNTRY_PATTERN.test(value));
}

function validateSettlementScopeParameters(parameters, definitions, location, add) {
  const definitionsByCode = new Map(definitions.map((definition) => [definition.parameterCode, definition]));
  const scopeDefinitions = SETTLEMENT_SCOPE_PARAMETER_CODES.map((code) => definitionsByCode.get(code));
  if (scopeDefinitions.some((definition) => !definition)) {
    add("ACTION_PARAMETERS", "SETTLEMENT_SCOPE_TUPLE_INVALID", location);
    return;
  }
  const scopeIds = new Set(scopeDefinitions.map((definition) => definition.parameterDefinitionId));
  const scopeParameters = parameters.filter((parameter) => scopeIds.has(parameter.parameterDefinitionId));
  const sequences = [...new Set(scopeParameters.map((parameter) => parameter.valueSequence))]
    .sort((left, right) => left - right);
  if (sequences.length === 0
    || sequences.some((sequence, index) => !Number.isInteger(sequence) || sequence !== index + 1)) {
    add("ACTION_PARAMETERS", "SETTLEMENT_SCOPE_SEQUENCE_INVALID", location);
  }
  if (scopeDefinitions.some((definition) => {
    const definitionSequences = scopeParameters
      .filter((parameter) => parameter.parameterDefinitionId === definition.parameterDefinitionId)
      .map((parameter) => parameter.valueSequence)
      .sort((left, right) => left - right);
    return definitionSequences.length !== sequences.length
      || definitionSequences.some((sequence, index) => sequence !== index + 1);
  })) add("ACTION_PARAMETERS", "SETTLEMENT_SCOPE_SEQUENCE_INVALID", location);
  for (const sequence of sequences) {
    const tuple = scopeDefinitions.map((definition) => scopeParameters.filter((parameter) => (
      parameter.parameterDefinitionId === definition.parameterDefinitionId
      && parameter.valueSequence === sequence
    )));
    if (tuple.some((dimension) => dimension.length !== 1)) {
      add("ACTION_PARAMETERS", "SETTLEMENT_SCOPE_TUPLE_INVALID", location);
      continue;
    }
    const sourceTypes = new Set(tuple.map((dimension) => dimension[0].sourceType));
    const sourceType = sourceTypes.size === 1 ? tuple[0][0].sourceType : null;
    const sourceValid = sourceType === "LITERAL"
      ? tuple.every((dimension, index) => settlementScopeLiteralIsValid(
        dimension[0], SETTLEMENT_SCOPE_PARAMETER_CODES[index],
      ))
      : sourceType === "CONTEXT" && sequences.length === 1 && sequence === 1;
    if (!sourceValid) add("ACTION_PARAMETERS", "SETTLEMENT_SCOPE_TUPLE_INVALID", location);
  }
  const rootParameters = parameters.filter((parameter) => !scopeIds.has(parameter.parameterDefinitionId));
  if (rootParameters.some((parameter) => parameter.valueSequence !== 1)) {
    add("ACTION_PARAMETERS", "SETTLEMENT_SCOPE_SEQUENCE_INVALID", location);
  }
}

function validateSettlementReleaseContent(fixture, add) {
  const hasSettlementAction = rowsOf(fixture, "actionVersions").some((action) => {
    const definitions = rowsOf(fixture, "parameterDefinitions")
      .filter((definition) => definition.actionVersionId === action.actionVersionId);
    return settlementControlActionCandidate(action, definitions);
  });
  if (!hasSettlementAction || !isCanonicalSha256(fixture.release?.contentHash)) return;
  try {
    if (decisionReleaseContentHash(fixture) !== fixture.release.contentHash) {
      add("REFERENCE_INTEGRITY", "RELEASE_CONTENT_HASH_MISMATCH", { path: "release.contentHash" });
    }
  } catch {
    add("REFERENCE_INTEGRITY", "RELEASE_CONTENT_PROJECTION_INVALID", {
      path: "release.contentHash",
    });
  }
}

function validateSettlementParameterIdentity(parameters, location, add) {
  try {
    canonicalizeSettlementActionParameterIdentity(parameters.map(actionParameterProjection));
  } catch (error) {
    const errorCode = /duplicate Action Parameter identity/u.test(String(error?.message))
      ? "ACTION_PARAMETER_DUPLICATE"
      : "SETTLEMENT_SCOPE_TUPLE_INVALID";
    add("ACTION_PARAMETERS", errorCode, location);
  }
}

function validateRetiredActionCodes(fixture, indexes, add) {
  const usages = [
    ...rowsOf(fixture, "ruleActions").map((ruleAction) => ({
      actionVersionId: ruleAction.actionVersionId,
      path: `ruleActions.${ruleAction.ruleActionId}`,
      ruleCode: indexes.rules.get(ruleAction.ruleId)?.ruleCode,
    })),
    ...rowsOf(fixture, "obligations")
      .filter((obligation) => isPresent(obligation.actionVersionId))
      .map((obligation) => ({
        actionVersionId: obligation.actionVersionId,
        path: `obligations.${obligation.obligationId}`,
      })),
  ];
  for (const usage of usages) {
    const action = indexes.actions.get(usage.actionVersionId);
    if (!action || !RETIRED_ACTION_CODES.has(action.actionCode)) continue;
    add("ACTION_PARAMETERS", "RETIRED_ACTION_CODE", {
      action_code: action.actionCode,
      path: usage.path,
      rule_code: usage.ruleCode,
    });
  }
}

function validateActionParameters(fixture, indexes, add) {
  validateRetiredActionCodes(fixture, indexes, add);
  for (const actionId of usedActionIds(fixture)) {
    const action = indexes.actions.get(actionId);
    if (action && action.shadowSupportedFlag !== true) {
      add("ACTION_PARAMETERS", "ACTION_SHADOW_UNSUPPORTED", {
        action_code: action.actionCode,
        path: `actionVersions.${actionId}`,
      });
    }
  }
  for (const rule of rowsOf(fixture, "rules")) {
    const actions = rowsOf(fixture, "ruleActions").filter((row) => row.ruleId === rule.ruleId);
    if (actions.length > 0 && !sequenceIsContinuous(actions)) {
      add("ACTION_PARAMETERS", "ACTION_SEQUENCE", { rule_code: rule.ruleCode, path: `rules.${rule.ruleId}.actions` });
    }
  }
  for (const ruleAction of rowsOf(fixture, "ruleActions")) {
    const rule = indexes.rules.get(ruleAction.ruleId);
    const action = indexes.actions.get(ruleAction.actionVersionId);
    if (!action) continue;
    const location = { rule_code: rule?.ruleCode, action_code: action.actionCode, path: `ruleActions.${ruleAction.ruleActionId}` };
    const definitions = rowsOf(fixture, "parameterDefinitions").filter((row) => row.actionVersionId === action.actionVersionId);
    const parameters = rowsOf(fixture, "actionParameters").filter((row) => row.ruleActionId === ruleAction.ruleActionId);
    const counts = new Map();
    const identities = new Map();
    for (const parameter of parameters) {
      counts.set(parameter.parameterDefinitionId, (counts.get(parameter.parameterDefinitionId) ?? 0) + 1);
      const identity = `${parameter.ruleActionId}\u0000${parameter.parameterDefinitionId}\u0000${parameter.valueSequence}`;
      identities.set(identity, (identities.get(identity) ?? 0) + 1);
    }
    for (const definition of definitions.filter((row) => row.requiredFlag === true)) {
      if ((counts.get(definition.parameterDefinitionId) ?? 0) === 0) add("ACTION_PARAMETERS", "ACTION_PARAMETER_MISSING", location);
    }
    if ([...identities.values()].some((count) => count > 1)) {
      add("ACTION_PARAMETERS", "ACTION_PARAMETER_DUPLICATE", location);
    }
    if (settlementControlActionCandidate(action, definitions)) {
      if (!settlementActionContractIsValid(action)) {
        add("ACTION_PARAMETERS", "SETTLEMENT_ACTION_CONTRACT_INVALID", location);
      }
      validateSettlementScopeParameters(parameters, definitions, location, add);
      validateSettlementParameterIdentity(parameters, location, add);
    } else if (parameters.some((parameter) => parameter.valueSequence !== 1)) {
      add("ACTION_PARAMETERS", "ACTION_PARAMETER_SEQUENCE_INVALID", location);
    }
    for (const parameter of parameters) {
      const definition = indexes.definitions.get(parameter.parameterDefinitionId);
      if (!definition || definition.actionVersionId !== action.actionVersionId) {
        add("ACTION_PARAMETERS", "ACTION_PARAMETER_UNKNOWN", { ...location, path: `actionParameters.${parameter.parameterId}` });
        continue;
      }
      validateParameterValue(parameter, definition, fixture, indexes, rule, action, add);
    }
  }
  validateSettlementReleaseContent(fixture, add);
}

function validateTerminalActions(fixture, indexes, add) {
  for (const action of rowsOf(fixture, "actionVersions")) {
    const metadataValid = action.terminalFlag === true
      ? TERMINAL_DECISIONS.has(action.decisionCode)
      : action.terminalFlag === false && !isPresent(action.decisionCode);
    if (!metadataValid) add("TERMINAL_ACTION", "TERMINAL_ACTION_METADATA_INVALID", { action_code: action.actionCode, path: `actionVersions.${action.actionVersionId}` });
  }
  for (const rule of rowsOf(fixture, "rules")) {
    const terminal = rowsOf(fixture, "ruleActions").filter((row) => row.ruleId === rule.ruleId
      && indexes.actions.get(row.actionVersionId)?.terminalFlag === true);
    if (terminal.length > 1) add("TERMINAL_ACTION", "TERMINAL_ACTION_MULTIPLE", { rule_code: rule.ruleCode, path: `rules.${rule.ruleId}.actions` });
  }
}

function limitBindingSourceShape(binding, prefix) {
  const sourceType = binding[`${prefix}SourceType`];
  const featureId = binding[`${prefix}FeatureVersionId`];
  const inputId = binding[`${prefix}InputFieldId`];
  if (!isPresent(sourceType) && !isPresent(featureId) && !isPresent(inputId)) return "NONE";
  if (sourceType === "FEATURE" && isPresent(featureId) && !isPresent(inputId)) return "FEATURE";
  if (sourceType === "CONTEXT" && !isPresent(featureId) && isPresent(inputId)) return "CONTEXT";
  return "INVALID";
}

function validateLimitBindingSource(binding, prefix, expectedType, fixture, indexes, add, errorCode) {
  const shape = limitBindingSourceShape(binding, prefix);
  const location = { path: `limitBindings.${binding.bindingId}.${prefix}` };
  if (shape === "INVALID" || shape === "NONE") {
    add("SYSTEM_OBLIGATION", errorCode, location);
    return;
  }
  if (shape === "FEATURE") {
    const featureId = binding[`${prefix}FeatureVersionId`];
    const feature = indexes.features.get(featureId);
    if (!feature || !decisionAllowLists(fixture).featureIds.has(featureId)) {
      add("SYSTEM_OBLIGATION", "LIMIT_BINDING_POINT_MISMATCH", location);
    } else if (feature.resultValueType !== expectedType) {
      add("SYSTEM_OBLIGATION", errorCode, location);
    }
    return;
  }
  const input = indexes.inputs.get(binding[`${prefix}InputFieldId`]);
  if (!input || input.pointVersionId !== fixture.pointVersion?.pointVersionId) {
    add("SYSTEM_OBLIGATION", "LIMIT_BINDING_POINT_MISMATCH", location);
  } else if (input.valueType !== expectedType) {
    add("SYSTEM_OBLIGATION", errorCode, location);
  }
}

function isCanonicalPositiveAmount(value) {
  return isCanonicalDecimal24Scale8(value)
    && !value.startsWith("-")
    && value !== "0.00000000";
}

function isCanonicalPositiveCount(value) {
  return isCanonicalNonnegativeBigInt(value) && BigInt(value) > 0n;
}

function limitVersionShapeIsValid(limitVersion) {
  const amountValid = isCanonicalPositiveAmount(limitVersion.amountLimit);
  const countValid = isCanonicalPositiveCount(limitVersion.countLimit);
  const thresholdValid = (limitVersion.metricType === "AMOUNT"
      && amountValid && !isPresent(limitVersion.countLimit))
    || (limitVersion.metricType === "COUNT"
      && !isPresent(limitVersion.amountLimit) && countValid)
    || (limitVersion.metricType === "AMOUNT_AND_COUNT" && amountValid && countValid);
  const currencyValid = (limitVersion.currencyMode === "FIXED"
      && typeof limitVersion.fixedCurrency === "string"
      && ISO_CURRENCY_PATTERN.test(limitVersion.fixedCurrency))
    || (new Set(["PER_CURRENCY", "NO_CURRENCY"]).has(limitVersion.currencyMode)
      && !isPresent(limitVersion.fixedCurrency));
  const freezeValid = (limitVersion.status === "DRAFT"
      && !isPresent(limitVersion.frozenBy)
      && !isPresent(limitVersion.frozenTime))
    || (new Set(["FROZEN", "RETIRED"]).has(limitVersion.status)
      && isCanonicalBigIntId(limitVersion.frozenBy)
      && isCanonicalUtcMicrosecondTimestamp(limitVersion.frozenTime));

  return isCanonicalBusinessId(limitVersion.limitCode, 64)
    && typeof limitVersion.limitName === "string"
    && limitVersion.limitName.length > 0
    && limitVersion.limitName.length <= 128
    && Number.isInteger(limitVersion.versionNo)
    && limitVersion.versionNo > 0
    && LIMIT_SUBJECT_TYPES.has(limitVersion.subjectType)
    && LIMIT_METRIC_TYPES.has(limitVersion.metricType)
    && LIMIT_WINDOW_UNITS.has(limitVersion.windowUnit)
    && Number.isInteger(limitVersion.windowSize)
    && limitVersion.windowSize > 0
    && LIMIT_CURRENCY_MODES.has(limitVersion.currencyMode)
    && thresholdValid
    && currencyValid
    && !(new Set(["AMOUNT", "AMOUNT_AND_COUNT"]).has(limitVersion.metricType)
      && limitVersion.currencyMode === "NO_CURRENCY")
    && LIMIT_STATUSES.has(limitVersion.status)
    && freezeValid;
}

function validateLimitVersions(fixture, add) {
  const semanticsByCode = new Map();
  const codeVersions = new Set();
  for (const limitVersion of rowsOf(fixture, "limitVersions")) {
    const location = { path: `limitVersions.${limitVersion.limitVersionId}` };
    const shapeValid = limitVersionShapeIsValid(limitVersion);
    if (!shapeValid) add("SYSTEM_OBLIGATION", "LIMIT_VERSION_SHAPE_INVALID", location);

    if (!isCanonicalSha256(limitVersion.contentHash)) {
      add("SYSTEM_OBLIGATION", "LIMIT_VERSION_CONTENT_HASH_INVALID", location);
    } else if (shapeValid && computeLimitVersionContentHash(limitVersion) !== limitVersion.contentHash) {
      add("SYSTEM_OBLIGATION", "LIMIT_VERSION_CONTENT_HASH_MISMATCH", location);
    }

    const codeVersion = `${limitVersion.limitCode}\u0000${limitVersion.versionNo}`;
    if (codeVersions.has(codeVersion)) {
      add("SYSTEM_OBLIGATION", "LIMIT_VERSION_DUPLICATE_CODE_VERSION", location);
    }
    codeVersions.add(codeVersion);

    const semantics = `${limitVersion.subjectType}\u0000${limitVersion.metricType}`;
    const expectedSemantics = semanticsByCode.get(limitVersion.limitCode);
    if (expectedSemantics && expectedSemantics !== semantics) {
      add("SYSTEM_OBLIGATION", "LIMIT_VERSION_CODE_SEMANTICS_CHANGED", location);
    } else if (!expectedSemantics) {
      semanticsByCode.set(limitVersion.limitCode, semantics);
    }
  }
}

function validateLimitBindings(fixture, indexes, add) {
  const bindings = rowsOf(fixture, "limitBindings");
  const grouped = new Map();
  for (const binding of bindings) {
    if (!grouped.has(binding.obligationId)) grouped.set(binding.obligationId, []);
    grouped.get(binding.obligationId).push(binding);
  }
  for (const [obligationId, rows] of grouped) {
    if (!sequenceIsContinuous(rows)) {
      add("SYSTEM_OBLIGATION", "LIMIT_BINDING_SEQUENCE", { path: `limitBindings.${obligationId}` });
    }
  }

  for (const binding of bindings) {
    const location = { path: `limitBindings.${binding.bindingId}` };
    const obligation = indexes.obligations.get(binding.obligationId);
    const limitVersion = indexes.limitVersions.get(binding.limitVersionId);
    if (!obligation || !limitVersion) continue;
    if (obligation.obligationType !== "RESERVE_LIMIT_GROUP") {
      add("SYSTEM_OBLIGATION", "LIMIT_BINDING_ACTION_FORBIDDEN", location);
      continue;
    }
    if (obligation.pointVersionId !== fixture.pointVersion?.pointVersionId) {
      add("SYSTEM_OBLIGATION", "LIMIT_BINDING_POINT_MISMATCH", location);
    }
    if (limitVersion.status !== "FROZEN") {
      add("SYSTEM_OBLIGATION", "LIMIT_BINDING_VERSION_INVALID", location);
    }
    validateLimitBindingSource(binding, "subject", "STRING", fixture, indexes, add, "LIMIT_BINDING_SOURCE_INVALID");

    const amountShape = limitBindingSourceShape(binding, "amount");
    if (limitVersion.metricType === "COUNT") {
      if (amountShape !== "NONE") add("SYSTEM_OBLIGATION", "LIMIT_BINDING_AMOUNT_INVALID", location);
      continue;
    }
    if (!new Set(["AMOUNT", "AMOUNT_AND_COUNT"]).has(limitVersion.metricType)) {
      add("SYSTEM_OBLIGATION", "LIMIT_BINDING_AMOUNT_INVALID", location);
      continue;
    }
    validateLimitBindingSource(binding, "amount", "NUMBER", fixture, indexes, add, "LIMIT_BINDING_AMOUNT_INVALID");
  }
}

function validateSettlementEligibilityRows(fixture, add) {
  const rows = fixture.crossRowRows;
  if (!rows || typeof rows !== "object") return;
  const points = Array.isArray(rows.decision_point) ? rows.decision_point : [];
  const versions = Array.isArray(rows.decision_point_version) ? rows.decision_point_version : [];
  const obligations = Array.isArray(rows.decision_point_obligation) ? rows.decision_point_obligation : [];
  const bindings = Array.isArray(rows.decision_point_limit_binding) ? rows.decision_point_limit_binding : [];
  const intents = Array.isArray(rows.evaluation_action_intent) ? rows.evaluation_action_intent : [];
  const settlementPoints = points.filter((point) => (
    point.business_domain === "SETTLEMENT" && SETTLEMENT_POINT_CODES.has(point.point_code)
  ));
  for (const point of settlementPoints) {
    const pointVersions = versions.filter((version) => version.decision_point_id === point.decision_point_id);
    for (const version of pointVersions) {
      const required = obligations.filter((obligation) => (
        obligation.point_version_id === version.point_version_id
        && obligation.obligation_type === "CHECK_SETTLEMENT_ELIGIBILITY"
      ));
      const location = { path: `crossRowRows.decision_point_version.${version.point_version_id}` };
      if (required.length === 0) add("SYSTEM_OBLIGATION", "REQUIRED_SETTLEMENT_OBLIGATION_MISSING", location);
      if (required.length > 1) add("SYSTEM_OBLIGATION", "REQUIRED_SETTLEMENT_OBLIGATION_AMBIGUOUS", location);
      for (const obligation of required) {
        const contractValid = obligation.trigger_decision === "ALLOW"
          && obligation.action_version_id === null
          && obligation.failure_policy === "FAIL_CLOSED"
          && obligation.enabled_flag === true
          && bindings.every((binding) => binding.obligation_id !== obligation.obligation_id)
          && intents.every((intent) => intent.obligation_id !== obligation.obligation_id);
        if (!contractValid) add("SYSTEM_OBLIGATION", "SETTLEMENT_OBLIGATION_CONTRACT_INVALID", location);
      }
    }
  }
}

function validateSettlementRuleReasons(fixture, indexes, add) {
  if (!SETTLEMENT_POINT_CODES.has(fixture.decisionPoint?.pointCode)) return;
  for (const rule of rowsOf(fixture, "rules").filter((candidate) => candidate.enabledFlag === true)) {
    const restrictsSettlement = rowsOf(fixture, "ruleActions").some((ruleAction) => {
      const action = indexes.actions.get(ruleAction.actionVersionId);
      return ruleAction.ruleId === rule.ruleId
        && action?.terminalFlag === true
        && new Set(["REVIEW", "DENY"]).has(action.decisionCode);
    });
    if (restrictsSettlement && !isCanonicalBusinessId(rule.reasonCode)) {
      add("SYSTEM_OBLIGATION", "SETTLEMENT_RULE_REASON_MISSING", {
        rule_code: rule.ruleCode,
        path: `rules.${rule.ruleId}.reasonCode`,
      });
    }
  }
}

function validateLocalPartnerSecurityDeposit(fixture, indexes, add) {
  for (const ruleAction of rowsOf(fixture, "ruleActions")) {
    const action = indexes.actions.get(ruleAction.actionVersionId);
    if (action?.actionCode !== "CREATE_SETTLEMENT_CONTROL") continue;
    const definitions = rowsOf(fixture, "parameterDefinitions")
      .filter((definition) => definition.actionVersionId === action.actionVersionId);
    const definitionByCode = new Map(definitions.map((definition) => [definition.parameterCode, definition]));
    const parameters = rowsOf(fixture, "actionParameters")
      .filter((parameter) => parameter.ruleActionId === ruleAction.ruleActionId);
    const parameter = (code) => {
      const definition = definitionByCode.get(code);
      return parameters.find((candidate) => (
        candidate.parameterDefinitionId === definition?.parameterDefinitionId
        && candidate.valueSequence === 1
      ));
    };
    const role = parameter("ROLE_TYPE");
    const controlType = parameter("CONTROL_TYPE");
    const minimumDeposit = controlType?.sourceType === "LITERAL"
      && controlType.stringValue === "MIN_SECURITY_DEPOSIT";
    const provableClient = role?.sourceType === "LITERAL" && role.stringValue === "CLIENT";
    if (minimumDeposit && !provableClient) {
      add("SYSTEM_OBLIGATION", "LOCAL_PARTNER_SECURITY_DEPOSIT_FORBIDDEN", {
        action_code: action.actionCode,
        path: `ruleActions.${ruleAction.ruleActionId}`,
      });
    }
  }
}

function validateSystemObligations(fixture, indexes, add) {
  const obligations = rowsOf(fixture, "obligations");
  validateLimitVersions(fixture, add);
  const sequenceGroups = new Map();
  for (const obligation of obligations) {
    const key = `${obligation.pointVersionId}\u0000${obligation.triggerDecision}`;
    if (!sequenceGroups.has(key)) sequenceGroups.set(key, []);
    sequenceGroups.get(key).push(obligation);
  }
  for (const [key, grouped] of sequenceGroups) {
    if (!sequenceIsContinuous(grouped)) add("SYSTEM_OBLIGATION", "OBLIGATION_SEQUENCE", { path: `obligations.${key}` });
    if (grouped.filter((obligation) => obligation.obligationType === "RESERVE_LIMIT_GROUP").length > 1) {
      add("SYSTEM_OBLIGATION", "LIMIT_OBLIGATION_AMBIGUOUS", { path: `obligations.${key}` });
    }
  }
  for (const obligation of obligations) {
    const targetShapeValid = (obligation.obligationType === "ACTION" && isPresent(obligation.actionVersionId))
      || (new Set(["RESERVE_LIMIT_GROUP", "CHECK_SETTLEMENT_ELIGIBILITY"])
        .has(obligation.obligationType) && !isPresent(obligation.actionVersionId));
    if (!targetShapeValid) add("SYSTEM_OBLIGATION", "OBLIGATION_TARGET_XOR", { path: `obligations.${obligation.obligationId}` });
    if (obligation.pointVersionId !== fixture.pointVersion?.pointVersionId) add("SYSTEM_OBLIGATION", "OBLIGATION_POINT_MISMATCH", { path: `obligations.${obligation.obligationId}` });
    if (obligation.enabledFlag !== true) add("SYSTEM_OBLIGATION", "OBLIGATION_DISABLED", { path: `obligations.${obligation.obligationId}` });
    if (obligation.obligationType === "ACTION" && isPresent(obligation.actionVersionId)) {
      const action = indexes.actions.get(obligation.actionVersionId);
      const location = { action_code: action?.actionCode, path: `obligations.${obligation.obligationId}` };
      if (!action) add("SYSTEM_OBLIGATION", "OBLIGATION_TARGET_MISSING", location);
      else {
        const hasRequiredParameters = rowsOf(fixture, "parameterDefinitions")
          .some((definition) => definition.actionVersionId === action.actionVersionId && definition.requiredFlag === true);
        if (hasRequiredParameters) add("SYSTEM_OBLIGATION", "OBLIGATION_ACTION_PARAMETERS_UNBINDABLE", location);
        if (action.terminalFlag !== false || action.decisionCode !== null) {
          add("SYSTEM_OBLIGATION", "OBLIGATION_ACTION_TERMINAL", location);
        }
      }
    }
  }
  validateLimitBindings(fixture, indexes, add);
  if (fixture.decisionPoint?.pointCode === "PAYMENT_ATTEMPT_RISK") {
    const required = obligations.filter((obligation) => obligation.triggerDecision === "ALLOW"
      && obligation.obligationType === "RESERVE_LIMIT_GROUP");
    if (required.length === 0) add("SYSTEM_OBLIGATION", "REQUIRED_OBLIGATION_MISSING", { path: "obligations" });
    if (required.length > 1) add("SYSTEM_OBLIGATION", "REQUIRED_OBLIGATION_AMBIGUOUS", { path: "obligations" });
    for (const obligation of required) {
      if (obligation.pointVersionId !== fixture.pointVersion?.pointVersionId
        || isPresent(obligation.actionVersionId)) {
        add("SYSTEM_OBLIGATION", "REQUIRED_OBLIGATION_TARGET_MISMATCH", { path: `obligations.${obligation.obligationId}` });
      }
      if (obligation.failurePolicy !== "FAIL_CLOSED") add("SYSTEM_OBLIGATION", "REQUIRED_OBLIGATION_FAILURE_POLICY", { path: `obligations.${obligation.obligationId}` });
      if (obligation.enabledFlag !== true) add("SYSTEM_OBLIGATION", "REQUIRED_OBLIGATION_MISSING", { path: `obligations.${obligation.obligationId}` });
    }
  }
  if (SETTLEMENT_POINT_CODES.has(fixture.decisionPoint?.pointCode)) {
    const required = obligations.filter((obligation) => (
      obligation.obligationType === "CHECK_SETTLEMENT_ELIGIBILITY"
    ));
    if (required.length === 0) add("SYSTEM_OBLIGATION", "REQUIRED_SETTLEMENT_OBLIGATION_MISSING", { path: "obligations" });
    if (required.length > 1) add("SYSTEM_OBLIGATION", "REQUIRED_SETTLEMENT_OBLIGATION_AMBIGUOUS", { path: "obligations" });
    for (const obligation of required) {
      const valid = obligation.pointVersionId === fixture.pointVersion?.pointVersionId
        && obligation.triggerDecision === "ALLOW"
        && obligation.actionVersionId === null
        && obligation.failurePolicy === "FAIL_CLOSED"
        && obligation.enabledFlag === true
        && rowsOf(fixture, "limitBindings").every((binding) => binding.obligationId !== obligation.obligationId);
      if (!valid) add("SYSTEM_OBLIGATION", "SETTLEMENT_OBLIGATION_CONTRACT_INVALID", { path: `obligations.${obligation.obligationId}` });
    }
  }
  validateSettlementEligibilityRows(fixture, add);
  validateSettlementRuleReasons(fixture, indexes, add);
  validateLocalPartnerSecurityDeposit(fixture, indexes, add);
}

function assertCrossRowField(field, label) {
  if (typeof field !== "string" || !CROSS_ROW_FIELD_PATTERN.test(field)) {
    throw new Error(`Invalid cross-row field: ${label}`);
  }
}

function assertCrossRowPolicyField(table, field, label) {
  assertCrossRowField(field, label);
  const schema = RISK_TABLE_SCHEMAS.get(table);
  if (schema && !schema.fields.has(field)) {
    throw new Error(`Unknown Risk Manifest field: ${table}.${field}`);
  }
}

function readCrossRowField(row, field, label) {
  if (!row || !Object.prototype.hasOwnProperty.call(row, field)) {
    throw new Error(`Missing cross-row field: ${label}.${field}`);
  }
  const value = row[field];
  if (value === undefined || (typeof value === "string" && value.trim().length === 0)) {
    throw new Error(`Missing cross-row field: ${label}.${field}`);
  }
  return value;
}

function crossRowJoinMatches(left, right) {
  return left !== null && right !== null && left === right;
}

function evaluatePredicate(row, predicate, label) {
  const actual = readCrossRowField(row, predicate.field, label);
  if (predicate.operator === "EQUALS") return actual === predicate.value;
  if (predicate.operator === "IN") {
    if (!Array.isArray(predicate.values)) throw new Error("Cross-row IN predicate requires values");
    return predicate.values.includes(actual);
  }
  throw new Error(`Unsupported cross-row predicate operator: ${predicate.operator}`);
}

function assertTableJoinTopology(joins, rootTable, label) {
  if (!isCanonicalBusinessId(rootTable)) throw new Error(`Invalid cross-row join topology: ${label}`);
  const available = new Set([rootTable]);
  for (const join of joins ?? []) {
    if (!available.has(join?.from?.table) || !isCanonicalBusinessId(join?.to?.table)
      || available.has(join.to.table)) {
      throw new Error(`Invalid cross-row join topology: ${label}`);
    }
    available.add(join.to.table);
  }
  return available;
}

function assertJoinAliasTopology(contract) {
  if (!isCanonicalBusinessId(contract.root?.alias) || !isCanonicalBusinessId(contract.root?.table)) {
    throw new Error(`Invalid cross-row join topology: ${contract.code}`);
  }
  const aliases = new Map([[contract.root.alias, contract.root.table]]);
  for (const join of contract.joins ?? []) {
    if (!aliases.has(join?.from?.alias) || !isCanonicalBusinessId(join?.to?.alias)
      || !isCanonicalBusinessId(join?.to?.table) || aliases.has(join.to.alias)) {
      throw new Error(`Invalid cross-row join topology: ${contract.code}`);
    }
    aliases.set(join.to.alias, join.to.table);
  }
  for (const predicate of contract.rowPredicates ?? []) {
    if (!aliases.has(predicate?.endpoint?.alias)) {
      throw new Error(`Unknown cross-row alias: ${predicate?.endpoint?.alias}`);
    }
  }
  for (const assertion of contract.fieldAssertions ?? []) {
    for (const endpoint of [assertion?.left, assertion?.right]) {
      if (!aliases.has(endpoint?.alias)) throw new Error(`Unknown cross-row alias: ${endpoint?.alias}`);
    }
  }
  return aliases;
}

function assertPredicateMetadata(predicate, table, label) {
  assertCrossRowPolicyField(table, predicate?.field, label);
  if (!new Set(["EQUALS", "IN"]).has(predicate?.operator)) {
    throw new Error(`Unsupported cross-row predicate operator: ${predicate?.operator}`);
  }
  if (predicate.operator === "EQUALS"
    && (!Object.prototype.hasOwnProperty.call(predicate, "value") || predicate.value === undefined
      || predicate.value === null
      || (typeof predicate.value === "string" && predicate.value.trim().length === 0))) {
    throw new Error("Cross-row EQUALS predicate requires value");
  }
  if (predicate.operator === "IN") {
    const values = predicate.values;
    if (!Array.isArray(values) || values.length === 0) {
      throw new Error("Cross-row IN predicate requires non-empty values");
    }
    const dense = Object.keys(values).length === values.length
      && values.every((value, index) => Object.prototype.hasOwnProperty.call(values, index)
        && value !== undefined && value !== null
        && !(typeof value === "string" && value.trim().length === 0));
    if (!dense) throw new Error("Cross-row IN predicate requires dense nonblank values");
  }
  const values = predicate.operator === "EQUALS" ? [predicate.value] : predicate.values;
  for (const value of values) assertManifestPolicyValue(table, predicate.field, value, label);
}

function manifestField(table, field) {
  return RISK_TABLE_SCHEMAS.get(table)?.fields.get(field) ?? null;
}

function manifestValueError(field, value) {
  if (field.defKey.endsWith("_hash") && field.type === "CHAR" && field.len === 64) {
    return isCanonicalSha256(value) ? null : "REFERENCE_HASH_INVALID";
  }
  if (isManifestIdentityField(field)) {
    if (typeof value !== "string") return "REFERENCE_ID_NOT_STRING";
    return isCanonicalBigIntId(value) ? null : "REFERENCE_ID_INVALID";
  }
  if (isManifestOpaqueIdentifierField(field)) {
    if (typeof value !== "string") return "REFERENCE_ID_NOT_STRING";
    if (field.len && Array.from(value).length > field.len) return "REFERENCE_FIELD_LENGTH_INVALID";
    return isCanonicalBusinessId(value, field.len || OPAQUE_IDENTIFIER_MAX_LENGTH)
      ? null
      : "REFERENCE_ID_INVALID";
  }
  return validateManifestScalar(field, value);
}

function assertManifestPolicyValue(table, fieldName, value, label) {
  const field = manifestField(table, fieldName);
  if (field && manifestValueError(field, value)) {
    throw new Error(`Invalid cross-row predicate value: ${label}.${fieldName}`);
  }
}

function assertManifestFieldCompatibility(leftTable, leftField, rightTable, rightField, label) {
  const left = manifestField(leftTable, leftField);
  const right = manifestField(rightTable, rightField);
  if (!left || !right) return;
  if (left.type !== right.type || left.len !== right.len || left.scale !== right.scale) {
    throw new Error(`Incompatible cross-row fields: ${label}`);
  }
}

function assertAllowedMappingMetadata(contract) {
  if (!Array.isArray(contract.joins) || contract.joins.length === 0
    || !Array.isArray(contract.selectors) || contract.selectors.length === 0
    || !Array.isArray(contract.requiredCoverage)
    || !Array.isArray(contract.allowedMappings) || contract.allowedMappings.length === 0) {
    throw new Error(`Invalid cross-row ALLOWED_MAPPING metadata: ${contract.code}`);
  }
  const rootTable = contract.joins[0]?.from?.table;
  if (!isCanonicalBusinessId(rootTable)) throw new Error(`Invalid cross-row join topology: ${contract.code}`);
  const available = assertTableJoinTopology(contract.joins, rootTable, contract.code);
  const mappingKeys = new Set();
  for (const selector of contract.selectors) {
    if (!available.has(selector.table)) throw new Error(`Invalid cross-row join topology: ${contract.code}`);
    assertCrossRowPolicyField(selector.table, selector.field, `${contract.code}.selectors`);
    assertCrossRowField(selector.mappingKey, `${contract.code}.selectors.mappingKey`);
    if (mappingKeys.has(selector.mappingKey)) throw new Error(`Invalid cross-row selector mapping key: ${contract.code}`);
    mappingKeys.add(selector.mappingKey);
  }
  for (const mapping of contract.allowedMappings) {
    if (!mapping || typeof mapping !== "object"
      || Object.keys(mapping).length !== mappingKeys.size
      || [...mappingKeys].some((key) => !Object.prototype.hasOwnProperty.call(mapping, key))) {
      throw new Error(`Invalid cross-row allowed mapping: ${contract.code}`);
    }
    for (const selector of contract.selectors) {
      assertManifestPolicyValue(
        selector.table,
        selector.field,
        mapping[selector.mappingKey],
        `${contract.code}.allowedMappings.${selector.mappingKey}`,
      );
    }
  }
  for (const coverage of contract.requiredCoverage ?? []) {
    if (!available.has(coverage.table)) throw new Error(`Invalid cross-row join topology: ${contract.code}`);
    assertCrossRowPolicyField(coverage.table, coverage.field, `${contract.code}.requiredCoverage`);
  }
  for (const join of contract.joins) {
    assertCrossRowPolicyField(join.from.table, join.from.field, `${contract.code}.joins.from`);
    assertCrossRowPolicyField(join.to.table, join.to.field, `${contract.code}.joins.to`);
    assertManifestFieldCompatibility(
      join.from.table, join.from.field, join.to.table, join.to.field, `${contract.code}.joins`,
    );
  }
}

function assertForbiddenPathsMetadata(contract) {
  if (!Array.isArray(contract.forbiddenPaths) || contract.forbiddenPaths.length === 0) {
    throw new Error(`Invalid cross-row FORBIDDEN_PATHS metadata: ${contract.code}`);
  }
  if (!isCanonicalBusinessId(contract.trigger?.table)) {
    throw new Error(`Invalid cross-row join topology: ${contract.code}`);
  }
  assertPredicateMetadata(contract.trigger, contract.trigger.table, `${contract.code}.trigger`);
  for (const path of contract.forbiddenPaths) {
    if (typeof path.code !== "string" || !CROSS_ROW_CODE_PATTERN.test(path.code)) {
      throw new Error(`Invalid cross-row path code: ${path.code}`);
    }
    if (!Array.isArray(path.joins) || path.joins.length === 0) {
      throw new Error(`Invalid cross-row join topology: ${path.code}`);
    }
    if (!Array.isArray(path.targetPredicates)) {
      throw new Error(`Invalid cross-row FORBIDDEN_PATHS metadata: ${path.code}`);
    }
    assertTableJoinTopology(path.joins, contract.trigger.table, path.code);
    for (const join of path.joins) {
      assertCrossRowPolicyField(join.from.table, join.from.field, `${path.code}.joins.from`);
      assertCrossRowPolicyField(join.to.table, join.to.field, `${path.code}.joins.to`);
      assertManifestFieldCompatibility(
        join.from.table, join.from.field, join.to.table, join.to.field, `${path.code}.joins`,
      );
    }
    const targetTable = path.joins.at(-1).to.table;
    for (const predicate of path.targetPredicates ?? []) {
      assertPredicateMetadata(predicate, targetTable, `${path.code}.targetPredicates`);
    }
  }
}

function assertJoinAssertionsMetadata(contract) {
  if (!Array.isArray(contract.joins)
    || !Array.isArray(contract.root?.predicates)
    || !Array.isArray(contract.rowPredicates)
    || !Array.isArray(contract.fieldAssertions)) {
    throw new Error(`Invalid cross-row JOIN_ASSERTIONS metadata: ${contract.code}`);
  }
  const contextQuantifier = contract.contextQuantifier ?? "ALL";
  if (!new Set(["ALL", "ANY"]).has(contextQuantifier)) {
    throw new Error(`Invalid cross-row context quantifier: ${contract.code}`);
  }
  const aliases = assertJoinAliasTopology(contract);
  for (const predicate of contract.root.predicates ?? []) {
    assertPredicateMetadata(predicate, contract.root.table, `${contract.code}.root.predicates`);
  }
  for (const join of contract.joins) {
    if (typeof join.required !== "boolean") {
      throw new Error(`Invalid cross-row join required flag: ${contract.code}`);
    }
    assertCrossRowPolicyField(aliases.get(join.from.alias), join.from.field, `${contract.code}.joins.from`);
    assertCrossRowPolicyField(join.to.table, join.to.field, `${contract.code}.joins.to`);
    assertManifestFieldCompatibility(
      aliases.get(join.from.alias), join.from.field, join.to.table, join.to.field, `${contract.code}.joins`,
    );
  }
  for (const predicate of contract.rowPredicates ?? []) {
    assertPredicateMetadata(
      { ...predicate, field: predicate.endpoint?.field },
      aliases.get(predicate.endpoint?.alias),
      `${contract.code}.rowPredicates`,
    );
  }
  for (const assertion of contract.fieldAssertions ?? []) {
    assertCrossRowPolicyField(aliases.get(assertion.left?.alias), assertion.left?.field, `${contract.code}.fieldAssertions.left`);
    assertCrossRowPolicyField(aliases.get(assertion.right?.alias), assertion.right?.field, `${contract.code}.fieldAssertions.right`);
    assertManifestFieldCompatibility(
      aliases.get(assertion.left?.alias), assertion.left?.field,
      aliases.get(assertion.right?.alias), assertion.right?.field,
      `${contract.code}.fieldAssertions`,
    );
    if (!new Set(["EQUALS", "NOT_EQUALS"]).has(assertion?.operator)) {
      throw new Error(`Unsupported cross-row assertion operator: ${assertion?.operator}`);
    }
  }
  if (contract.joins.length === 0
    || (contract.rowPredicates.length + contract.fieldAssertions.length === 0)) {
    throw new Error(`Invalid cross-row JOIN_ASSERTIONS metadata: ${contract.code}`);
  }
}

function assertConditionalCardinalityMetadata(contract) {
  if (!Array.isArray(contract.joins) || contract.joins.length === 0
    || !Array.isArray(contract.children) || contract.children.length === 0
    || !Array.isArray(contract.cases) || contract.cases.length === 0) {
    throw new Error(`Invalid cross-row CONDITIONAL_CARDINALITY metadata: ${contract.code}`);
  }
  const aliases = assertJoinAliasTopology(contract);
  assertCrossRowPolicyField(contract.root.table, contract.root.key, `${contract.code}.root.key`);
  const rootSchema = RISK_TABLE_SCHEMAS.get(contract.root.table);
  if (rootSchema?.primaryField && rootSchema.primaryField !== contract.root.key) {
    throw new Error(`Invalid cross-row cardinality root key: ${contract.code}`);
  }
  for (const join of contract.joins) {
    if (join.required !== true) {
      throw new Error(`Invalid cross-row cardinality join required flag: ${contract.code}`);
    }
    assertCrossRowPolicyField(aliases.get(join.from.alias), join.from.field, `${contract.code}.joins.from`);
    assertCrossRowPolicyField(join.to.table, join.to.field, `${contract.code}.joins.to`);
    assertManifestFieldCompatibility(
      aliases.get(join.from.alias), join.from.field, join.to.table, join.to.field,
      `${contract.code}.joins`,
    );
  }
  if (!aliases.has(contract.discriminator?.alias)) {
    throw new Error(`Unknown cross-row alias: ${contract.discriminator?.alias}`);
  }
  const discriminatorTable = aliases.get(contract.discriminator.alias);
  assertCrossRowPolicyField(
    discriminatorTable, contract.discriminator.field, `${contract.code}.discriminator`,
  );

  const childKeys = new Set();
  const childTables = new Set();
  for (const child of contract.children) {
    assertCrossRowField(child?.key, `${contract.code}.children.key`);
    if (!isCanonicalBusinessId(child?.table) || childKeys.has(child.key) || childTables.has(child.table)) {
      throw new Error(`Invalid cross-row cardinality child: ${contract.code}`);
    }
    childKeys.add(child.key);
    childTables.add(child.table);
    assertCrossRowPolicyField(child.table, child.foreignKey, `${contract.code}.children.foreignKey`);
    assertManifestFieldCompatibility(
      contract.root.table, contract.root.key, child.table, child.foreignKey,
      `${contract.code}.children`,
    );
  }

  const coveredValues = new Set();
  for (const branch of contract.cases) {
    const values = branch?.values;
    if (!Array.isArray(values) || values.length === 0
      || Object.keys(values).length !== values.length
      || values.some((value, index) => !Object.prototype.hasOwnProperty.call(values, index)
        || value === undefined || value === null
        || (typeof value === "string" && value.trim().length === 0))) {
      throw new Error(`Invalid cross-row cardinality case values: ${contract.code}`);
    }
    for (const value of values) {
      assertManifestPolicyValue(
        discriminatorTable, contract.discriminator.field, value,
        `${contract.code}.cases.values`,
      );
      if (coveredValues.has(value)) {
        throw new Error(`Duplicate cross-row cardinality case value: ${contract.code}`);
      }
      coveredValues.add(value);
    }
    if (!branch.exactly || typeof branch.exactly !== "object"
      || Array.isArray(branch.exactly)
      || Object.keys(branch.exactly).length !== childKeys.size
      || [...childKeys].some((key) => !Object.prototype.hasOwnProperty.call(branch.exactly, key))) {
      throw new Error(`Invalid cross-row cardinality exact counts: ${contract.code}`);
    }
    for (const [key, count] of Object.entries(branch.exactly)) {
      if (!childKeys.has(key) || !Number.isSafeInteger(count) || count < 0) {
        throw new Error(`Invalid cross-row cardinality exact counts: ${contract.code}`);
      }
    }
  }
  const closedValues = manifestField(discriminatorTable, contract.discriminator.field)?.closedValues;
  if (Array.isArray(closedValues)
    && (coveredValues.size !== closedValues.length
      || closedValues.some((value) => !coveredValues.has(value)))) {
    throw new Error(`Incomplete cross-row cardinality cases: ${contract.code}`);
  }
}

const settlementAssertionSignature = (assertion) => JSON.stringify([
  assertion.left?.alias,
  assertion.left?.field,
  assertion.operator,
  assertion.right?.alias,
  assertion.right?.field,
]);

const settlementSelectorSignature = (selector) => JSON.stringify([
  selector.mappingKey,
  selector.alias,
  selector.field,
]);

const SETTLEMENT_ASSESSMENT_RESULT_KEYS = Object.freeze([
  "eligibilityDecision",
  "technicalStatus",
  "proposedDecision",
  "businessDecision",
]);

const REQUIRED_SETTLEMENT_ASSESSMENT_ASSERTIONS = Object.freeze([
  JSON.stringify(["assessment", "checkpoint_type", "EQUALS", "decisionPoint", "point_code"]),
  JSON.stringify(["assessment", "input_hash", "EQUALS", "evaluation", "input_snapshot_hash"]),
]);

const REQUIRED_SETTLEMENT_ASSESSMENT_SELECTORS = Object.freeze([
  JSON.stringify(["eligibilityDecision", "assessment", "eligibility_decision"]),
  JSON.stringify(["technicalStatus", "evaluation", "technical_status"]),
  JSON.stringify(["proposedDecision", "evaluation", "proposed_decision"]),
  JSON.stringify(["businessDecision", "evaluation", "business_decision"]),
]);

const REQUIRED_SETTLEMENT_ASSESSMENT_MAPPINGS = Object.freeze([
  ["ALLOW", "SUCCEEDED", "ALLOW", "ALLOW"],
  ["RESTRICT", "SUCCEEDED", "ALLOW", "DENY"],
  [null, "SUCCEEDED", "REVIEW", "REVIEW"],
  [null, "SUCCEEDED", "DENY", "DENY"],
  [null, "FAILED", null, null],
  [null, "TIMED_OUT", null, null],
].map((mapping) => JSON.stringify(mapping)));

function assertExactSettlementMetadata(actualSignatures, requiredSignatures, incompleteMessage, invalidMessage) {
  const actual = new Set(actualSignatures);
  const required = new Set(requiredSignatures);
  const hasMissing = [...required].some((signature) => !actual.has(signature));
  if (hasMissing && actualSignatures.length < requiredSignatures.length) {
    throw new Error(incompleteMessage);
  }
  if (hasMissing || actualSignatures.length !== requiredSignatures.length
    || actual.size !== required.size) {
    throw new Error(invalidMessage);
  }
}

export function validateSettlementAssessmentDomainContractMetadata(contract) {
  const denseNonemptyArray = (value) => Array.isArray(value) && value.length > 0
    && Object.keys(value).length === value.length
    && value.every((entry, index) => Object.prototype.hasOwnProperty.call(value, index) && entry !== undefined);
  if (!denseNonemptyArray(contract?.fieldAssertions)
    || !denseNonemptyArray(contract?.resultSelectors)
    || !denseNonemptyArray(contract?.allowedResultMappings)) {
    throw new Error(`Invalid Settlement Assessment metadata: ${contract?.code}`);
  }

  const aliases = assertJoinAliasTopology(contract);
  const assertionSignatures = [];
  for (const assertion of contract.fieldAssertions) {
    const leftTable = aliases.get(assertion.left?.alias);
    const rightTable = aliases.get(assertion.right?.alias);
    assertCrossRowPolicyField(leftTable, assertion.left?.field, `${contract.code}.fieldAssertions.left`);
    assertCrossRowPolicyField(rightTable, assertion.right?.field, `${contract.code}.fieldAssertions.right`);
    assertManifestFieldCompatibility(
      leftTable, assertion.left?.field, rightTable, assertion.right?.field,
      `${contract.code}.fieldAssertions`,
    );
    if (!new Set(["EQUALS", "NOT_EQUALS"]).has(assertion.operator)) {
      throw new Error(`Unsupported cross-row assertion operator: ${assertion.operator}`);
    }
    assertionSignatures.push(settlementAssertionSignature(assertion));
  }
  assertExactSettlementMetadata(
    assertionSignatures,
    REQUIRED_SETTLEMENT_ASSESSMENT_ASSERTIONS,
    `Incomplete Settlement Assessment field assertions: ${contract.code}`,
    `Invalid Settlement Assessment field assertions: ${contract.code}`,
  );

  const mappingKeys = new Set();
  const selectorSignatures = [];
  for (const selector of contract.resultSelectors) {
    const table = aliases.get(selector.alias);
    if (!table) throw new Error(`Unknown cross-row alias: ${selector.alias}`);
    assertCrossRowPolicyField(table, selector.field, `${contract.code}.resultSelectors`);
    assertCrossRowField(selector.mappingKey, `${contract.code}.resultSelectors.mappingKey`);
    if (mappingKeys.has(selector.mappingKey)) {
      throw new Error(`Invalid Settlement Assessment selector mapping key: ${contract.code}`);
    }
    mappingKeys.add(selector.mappingKey);
    selectorSignatures.push(settlementSelectorSignature(selector));
  }
  assertExactSettlementMetadata(
    selectorSignatures,
    REQUIRED_SETTLEMENT_ASSESSMENT_SELECTORS,
    `Incomplete Settlement Assessment result selectors: ${contract.code}`,
    `Invalid Settlement Assessment result selectors: ${contract.code}`,
  );

  const mappingSignatures = new Set();
  for (const mapping of contract.allowedResultMappings) {
    if (!mapping || typeof mapping !== "object" || Array.isArray(mapping)
      || Object.keys(mapping).length !== mappingKeys.size
      || [...mappingKeys].some((key) => !Object.prototype.hasOwnProperty.call(mapping, key))) {
      throw new Error(`Invalid Settlement Assessment result mapping: ${contract.code}`);
    }
    for (const selector of contract.resultSelectors) {
      const table = aliases.get(selector.alias);
      const field = manifestField(table, selector.field);
      const value = mapping[selector.mappingKey];
      if (value === null) {
        if (!field || field.notNull) {
          throw new Error(`Invalid Settlement Assessment result mapping: ${contract.code}`);
        }
      } else {
        assertManifestPolicyValue(
          table, selector.field, value,
          `${contract.code}.allowedResultMappings.${selector.mappingKey}`,
        );
      }
    }
    const signature = JSON.stringify(SETTLEMENT_ASSESSMENT_RESULT_KEYS.map((key) => mapping[key]));
    if (mappingSignatures.has(signature)) {
      throw new Error(`Invalid Settlement Assessment result mapping: ${contract.code}`);
    }
    mappingSignatures.add(signature);
  }
  assertExactSettlementMetadata(
    [...mappingSignatures],
    REQUIRED_SETTLEMENT_ASSESSMENT_MAPPINGS,
    `Incomplete Settlement Assessment result mappings: ${contract.code}`,
    `Invalid Settlement Assessment result mapping: ${contract.code}`,
  );
}

function assertCrossRowContractMetadata(contract) {
  if (typeof contract?.code !== "string" || !CROSS_ROW_CODE_PATTERN.test(contract.code)) {
    throw new Error(`Invalid cross-row contract code: ${contract?.code}`);
  }
  if (!new Set([
    "ALLOWED_MAPPING", "FORBIDDEN_PATHS", "JOIN_ASSERTIONS", "CONDITIONAL_CARDINALITY",
  ]).has(contract.contractType)) {
    throw new Error(`Unsupported cross-row contract type: ${contract.contractType}`);
  }
  if (contract.contractType === "ALLOWED_MAPPING") assertAllowedMappingMetadata(contract);
  else if (contract.contractType === "FORBIDDEN_PATHS") assertForbiddenPathsMetadata(contract);
  else if (contract.contractType === "JOIN_ASSERTIONS") assertJoinAssertionsMetadata(contract);
  else assertConditionalCardinalityMetadata(contract);
}

function contractTables(contract) {
  const tables = new Set();
  for (const selector of contract.selectors ?? []) tables.add(selector.table);
  if (contract.trigger) tables.add(contract.trigger.table);
  if (contract.root) tables.add(contract.root.table);
  for (const coverage of contract.requiredCoverage ?? []) tables.add(coverage.table);
  if (contract.contractType === "CONDITIONAL_CARDINALITY") {
    for (const join of contract.joins ?? []) tables.add(join.to.table);
    for (const child of contract.children ?? []) tables.add(child.table);
    return tables;
  }
  if (contract.contractType === "JOIN_ASSERTIONS") {
    for (const join of contract.joins ?? []) tables.add(join.to.table);
    return tables;
  }
  const paths = contract.contractType === "FORBIDDEN_PATHS" ? contract.forbiddenPaths : [contract];
  for (const path of paths) {
    for (const join of path.joins ?? []) {
      tables.add(join.from.table);
      tables.add(join.to.table);
    }
  }
  return tables;
}

function followJoins(rowsByTable, joins, initialRows) {
  if (joins.length === 0) return [];
  let contexts = initialRows.map((row) => ({ [joins[0].from.table]: row }));
  for (const join of joins) {
    const targetRows = rowsByTable[join.to.table];
    const next = [];
    for (const context of contexts) {
      const source = context[join.from.table];
      if (!source) continue;
      const sourceValue = readCrossRowField(source, join.from.field, join.from.table);
      for (const target of targetRows) {
        const targetValue = readCrossRowField(target, join.to.field, join.to.table);
        if (crossRowJoinMatches(sourceValue, targetValue)) next.push({ ...context, [join.to.table]: target });
      }
    }
    contexts = next;
  }
  return contexts;
}

function executeAllowedMapping(contract, rowsByTable, add) {
  const firstTable = contract.joins[0]?.from.table;
  const contexts = followJoins(rowsByTable, contract.joins, rowsByTable[firstTable]);
  for (const context of contexts) {
    const actual = Object.fromEntries(contract.selectors.map((selector) => [
      selector.mappingKey,
      readCrossRowField(context[selector.table], selector.field, selector.table),
    ]));
    const allowed = contract.allowedMappings.some((mapping) => contract.selectors.every((selector) => mapping[selector.mappingKey] === actual[selector.mappingKey]));
    if (!allowed) add("REFERENCE_INTEGRITY", contract.code, { path: `crossRowRows.${firstTable}` });
  }
  for (const coverage of contract.requiredCoverage ?? []) {
    for (const row of rowsByTable[coverage.table]) readCrossRowField(row, coverage.field, coverage.table);
    const coveredRows = new Set(contexts.map((context) => context[coverage.table]).filter(Boolean));
    if (rowsByTable[coverage.table].some((row) => !coveredRows.has(row))) {
      add("REFERENCE_INTEGRITY", contract.code, { path: `crossRowRows.${coverage.table}.${coverage.field}` });
    }
  }
}

function executeForbiddenPaths(contract, rowsByTable, add) {
  const triggerRows = rowsByTable[contract.trigger.table]
    .filter((row) => evaluatePredicate(row, contract.trigger, contract.trigger.table));
  for (const path of contract.forbiddenPaths) {
    const contexts = followJoins(rowsByTable, path.joins, triggerRows);
    const targetTable = path.joins.at(-1)?.to.table;
    if (contexts.some((context) => path.targetPredicates.every((predicate) => evaluatePredicate(context[targetTable], predicate, targetTable)))) {
      add("REFERENCE_INTEGRITY", path.code, { path: `crossRowRows.${targetTable}` });
    }
  }
}

function assertionMatches(context, assertion) {
  const left = readCrossRowField(context[assertion.left.alias], assertion.left.field, assertion.left.alias);
  const right = readCrossRowField(context[assertion.right.alias], assertion.right.field, assertion.right.alias);
  if (assertion.operator === "EQUALS") return left === right;
  if (assertion.operator === "NOT_EQUALS") return left !== right;
  throw new Error(`Unsupported cross-row assertion operator: ${assertion.operator}`);
}

function executeJoinAssertions(contract, rowsByTable, add) {
  const rootRows = rowsByTable[contract.root.table]
    .filter((row) => contract.root.predicates.every((predicate) => evaluatePredicate(row, predicate, contract.root.table)));
  for (const rootRow of rootRows) {
    let contexts = [{ [contract.root.alias]: rootRow }];
    let missingRequired = false;
    for (const join of contract.joins) {
      const next = [];
      for (const context of contexts) {
        const source = context[join.from.alias];
        const sourceValue = readCrossRowField(source, join.from.field, join.from.alias);
        const matches = rowsByTable[join.to.table]
          .filter((target) => crossRowJoinMatches(
            sourceValue,
            readCrossRowField(target, join.to.field, join.to.alias),
          ));
        if (join.required === true && matches.length !== 1) missingRequired = true;
        for (const target of matches) next.push({ ...context, [join.to.alias]: target });
      }
      contexts = next;
    }
    const contextIsValid = (context) => (
      contract.rowPredicates.every((predicate) => evaluatePredicate(context[predicate.endpoint.alias], {
        field: predicate.endpoint.field,
        operator: predicate.operator,
        value: predicate.value,
        values: predicate.values,
      }, predicate.endpoint.alias))
      && contract.fieldAssertions.every((assertion) => assertionMatches(context, assertion))
    );
    const rowsValid = contexts.length > 0 && (
      (contract.contextQuantifier ?? "ALL") === "ANY"
        ? contexts.some(contextIsValid)
        : contexts.every(contextIsValid)
    );
    if (missingRequired || !rowsValid) add("REFERENCE_INTEGRITY", contract.code, { path: `crossRowRows.${contract.root.table}` });
  }
}

function executeConditionalCardinality(contract, rowsByTable, add) {
  const rootRows = rowsByTable[contract.root.table];
  const rootKeys = new Set();
  for (const rootRow of rootRows) {
    const rootKey = readCrossRowField(rootRow, contract.root.key, contract.root.alias);
    rootKeys.add(rootKey);
    let contexts = [{ [contract.root.alias]: rootRow }];
    let invalidJoin = false;
    for (const join of contract.joins) {
      const next = [];
      for (const context of contexts) {
        const source = context[join.from.alias];
        const sourceValue = readCrossRowField(source, join.from.field, join.from.alias);
        const matches = rowsByTable[join.to.table].filter((target) => crossRowJoinMatches(
          sourceValue,
          readCrossRowField(target, join.to.field, join.to.alias),
        ));
        if (matches.length !== 1) invalidJoin = true;
        for (const target of matches) next.push({ ...context, [join.to.alias]: target });
      }
      contexts = next;
    }
    if (invalidJoin || contexts.length !== 1) {
      add("REFERENCE_INTEGRITY", contract.code, {
        path: `crossRowRows.${contract.root.table}.${rootKey}`,
      });
      continue;
    }
    const context = contexts[0];
    const discriminatorValue = readCrossRowField(
      context[contract.discriminator.alias],
      contract.discriminator.field,
      contract.discriminator.alias,
    );
    const branch = contract.cases.find((candidate) => candidate.values.includes(discriminatorValue));
    const valid = branch && contract.children.every((child) => {
      const count = rowsByTable[child.table].filter((row) => crossRowJoinMatches(
        rootKey,
        readCrossRowField(row, child.foreignKey, child.table),
      )).length;
      return count === branch.exactly[child.key];
    });
    if (!valid) {
      add("REFERENCE_INTEGRITY", contract.code, {
        path: `crossRowRows.${contract.root.table}.${rootKey}`,
      });
    }
  }
  for (const child of contract.children) {
    for (const row of rowsByTable[child.table]) {
      const foreignKey = readCrossRowField(row, child.foreignKey, child.table);
      if (!rootKeys.has(foreignKey)) {
        add("REFERENCE_INTEGRITY", contract.code, {
          path: crossRowFieldPath(child.table, row, child.foreignKey),
        });
      }
    }
  }
}

function isManifestIdentityField(field) {
  return field.type === "BIGINT"
    && (field.primaryKey || field.defKey === "id" || field.defKey.endsWith("_id"));
}

function isManifestOpaqueIdentifierField(field) {
  return field.type === "VARCHAR" && field.defKey.endsWith("_id");
}

function crossRowFieldPath(table, row, field) {
  const primaryField = RISK_TABLE_SCHEMAS.get(table)?.primaryField;
  const primaryValue = primaryField ? row?.[primaryField] : null;
  const rowKey = isCanonicalBigIntId(primaryValue) ? primaryValue : field;
  return `crossRowRows.${table}.${rowKey}.${field}`;
}

function validateManifestScalar(field, value) {
  let errorCode = null;
  if (field.type === "VARCHAR" || field.type === "CHAR") {
    if (typeof value !== "string") errorCode = "REFERENCE_FIELD_TYPE_INVALID";
    else if (field.len && Array.from(value).length > field.len) {
      errorCode = "REFERENCE_FIELD_LENGTH_INVALID";
    }
  } else if (field.type === "DATETIME") {
    errorCode = isCanonicalUtcMicrosecondTimestamp(value) ? null : "REFERENCE_FIELD_TYPE_INVALID";
  } else if (field.type === "BOOLEAN") {
    errorCode = typeof value === "boolean" ? null : "REFERENCE_FIELD_TYPE_INVALID";
  } else if (field.type === "INT") {
    if (!Number.isInteger(value)) errorCode = "REFERENCE_FIELD_TYPE_INVALID";
    else if (value < SIGNED_INT_MIN || value > SIGNED_INT_MAX) errorCode = "REFERENCE_FIELD_RANGE_INVALID";
  } else if (field.type === "DECIMAL") {
    const pattern = new RegExp(`^-?(?:0|[1-9][0-9]{0,${field.len - field.scale - 1}})\\.[0-9]{${field.scale}}$`);
    errorCode = typeof value === "string" && pattern.test(value)
      ? null
      : "REFERENCE_FIELD_TYPE_INVALID";
  } else if (field.type === "BIGINT") {
    if (typeof value !== "string" || !/^-?(?:0|[1-9][0-9]*)$/.test(value)) {
      errorCode = "REFERENCE_FIELD_TYPE_INVALID";
    } else {
      const numeric = BigInt(value);
      if (numeric < -9223372036854775808n || numeric > SIGNED_BIGINT_MAX) {
        errorCode = "REFERENCE_FIELD_TYPE_INVALID";
      }
    }
  } else if (field.type === "JSON") {
    errorCode = isCanonicalJsonValue(value) ? null : "REFERENCE_FIELD_TYPE_INVALID";
  }
  if (!errorCode && Array.isArray(field.closedValues) && !field.closedValues.includes(value)) {
    errorCode = "REFERENCE_FIELD_ENUM_INVALID";
  }
  return errorCode;
}

function validateKnownCrossRowRows(rowsByTable, add) {
  for (const [table, rows] of Object.entries(rowsByTable)) {
    const schema = RISK_TABLE_SCHEMAS.get(table);
    if (!schema || !Array.isArray(rows)) continue;
    for (const row of rows) {
      if (!row || typeof row !== "object" || Array.isArray(row)) {
        add("REFERENCE_INTEGRITY", "REFERENCE_ROW_INVALID", { path: `crossRowRows.${table}` });
        continue;
      }
      for (const [fieldName, value] of Object.entries(row)) {
        const field = schema.fields.get(fieldName);
        const path = crossRowFieldPath(table, row, fieldName);
        if (!field) {
          add("REFERENCE_INTEGRITY", "REFERENCE_FIELD_UNKNOWN", { path });
          continue;
        }
        if (value === undefined || (typeof value === "string" && value.trim().length === 0)) {
          add("REFERENCE_INTEGRITY", "REFERENCE_FIELD_MISSING", { path });
          continue;
        }
        if (value === null) {
          if (field.notNull) add("REFERENCE_INTEGRITY", "REFERENCE_FIELD_NULL_NOT_ALLOWED", { path });
          continue;
        }
        if (fieldName.endsWith("_hash") && field.type === "CHAR" && field.len === 64) {
          if (!isCanonicalSha256(value)) add("REFERENCE_INTEGRITY", "REFERENCE_HASH_INVALID", { path });
          continue;
        }
        if (isManifestIdentityField(field)) {
          if (typeof value !== "string") add("REFERENCE_INTEGRITY", "REFERENCE_ID_NOT_STRING", { path });
          else if (!isCanonicalBigIntId(value)) add("REFERENCE_INTEGRITY", "REFERENCE_ID_INVALID", { path });
          continue;
        }
        if (isManifestOpaqueIdentifierField(field)) {
          if (typeof value !== "string") add("REFERENCE_INTEGRITY", "REFERENCE_ID_NOT_STRING", { path });
          else if (field.len && value.length > field.len) {
            add("REFERENCE_INTEGRITY", "REFERENCE_FIELD_LENGTH_INVALID", { path });
          } else if (!isCanonicalBusinessId(value, field.len || OPAQUE_IDENTIFIER_MAX_LENGTH)) {
            add("REFERENCE_INTEGRITY", "REFERENCE_ID_INVALID", { path });
          }
          continue;
        }
        const errorCode = validateManifestScalar(field, value);
        if (errorCode) add("REFERENCE_INTEGRITY", errorCode, { path });
      }
    }
  }
}

function executeCrossRowRegistry(rowsByTable, contracts, add) {
  const contractList = Object.values(contracts ?? {});
  const emittedCodes = new Set();
  for (const contract of contractList) {
    assertCrossRowContractMetadata(contract);
    const codes = contract.contractType === "FORBIDDEN_PATHS"
      ? contract.forbiddenPaths.map((path) => path.code)
      : [contract.code];
    for (const code of codes) {
      if (emittedCodes.has(code)) throw new Error(`Duplicate cross-row error code: ${code}`);
      emittedCodes.add(code);
    }
  }
  if (!rowsByTable || typeof rowsByTable !== "object") {
    add("REFERENCE_INTEGRITY", "CR_POLICY_MISSING_ROWS", { path: "crossRowRows" });
    return;
  }
  validateKnownCrossRowRows(rowsByTable, add);
  const executableRowsByTable = { ...rowsByTable };
  for (const [table, schema] of RISK_TABLE_SCHEMAS) {
    if (!Array.isArray(rowsByTable[table]) || !schema.primaryField) continue;
    const index = indexRows(rowsByTable[table], schema.primaryField, add, `crossRowRows.${table}`);
    executableRowsByTable[table] = [...index.values()];
  }
  for (const contract of contractList) {
    const missingTables = [...contractTables(contract)].filter((table) => !Array.isArray(executableRowsByTable[table]));
    for (const table of missingTables) add("REFERENCE_INTEGRITY", "CR_POLICY_MISSING_ROWS", { path: `crossRowRows.${table}` });
    if (missingTables.length > 0) continue;
    if (contract.contractType === "ALLOWED_MAPPING") executeAllowedMapping(contract, executableRowsByTable, add);
    else if (contract.contractType === "FORBIDDEN_PATHS") executeForbiddenPaths(contract, executableRowsByTable, add);
    else if (contract.contractType === "JOIN_ASSERTIONS") executeJoinAssertions(contract, executableRowsByTable, add);
    else if (contract.contractType === "CONDITIONAL_CARDINALITY") {
      executeConditionalCardinality(contract, executableRowsByTable, add);
    }
    else throw new Error(`Unsupported cross-row contract type: ${contract.contractType}`);
  }
}

export function executeCrossRowContractRegistry(rowsByTable, contracts, releaseId = "UNKNOWN") {
  const { add, finish } = createCollector(releaseId);
  executeCrossRowRegistry(rowsByTable, contracts, add);
  return finish();
}

function collectMetadataTables(value, tables = new Set()) {
  if (!value || typeof value !== "object") return tables;
  if (typeof value.table === "string") tables.add(value.table);
  for (const child of Object.values(value)) collectMetadataTables(child, tables);
  return tables;
}

function validateRequiredSettlementRuntimeRows(rowsByTable, add) {
  if (!rowsByTable || typeof rowsByTable !== "object") return;
  const tables = collectMetadataTables([
    SETTLEMENT_ELIGIBILITY_OBLIGATION_CONTRACT,
    SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS,
  ]);
  for (const table of [...tables].sort()) {
    if (!Array.isArray(rowsByTable[table])) {
      add("REFERENCE_INTEGRITY", "CR_POLICY_MISSING_ROWS", { path: `crossRowRows.${table}` });
    }
  }
}

function settlementRows(rowsByTable, table) {
  return Array.isArray(rowsByTable?.[table]) ? rowsByTable[table] : [];
}

function validateSettlementControlOrigin(fixture, rowsByTable, add) {
  const contract = SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS.CR_SETTLEMENT_CONTROL_ORIGIN;
  const controls = settlementRows(rowsByTable, contract.root.table);
  const effects = settlementRows(rowsByTable, "action_effect");
  const evaluations = settlementRows(rowsByTable, "decision_evaluation");
  const intents = settlementRows(rowsByTable, "evaluation_action_intent");
  const ruleActions = settlementRows(rowsByTable, "rule_action");
  const runtimeRules = settlementRows(rowsByTable, "risk_rule");
  const runtimeRuleSets = settlementRows(rowsByTable, "decision_point_rule_set");
  const runtimeActions = settlementRows(rowsByTable, "action_version");
  const staticRuleActions = rowsOf(fixture, "ruleActions");
  const staticRules = rowsOf(fixture, "rules");
  const staticActions = rowsOf(fixture, "actionVersions");
  const selectedRuleSets = new Set(rowsOf(fixture, "pointRuleSets")
    .filter((binding) => binding.pointVersionId === fixture.pointVersion?.pointVersionId)
    .map((binding) => binding.ruleSetVersionId));
  for (const control of controls) {
    const matchingEffects = effects.filter((effect) => effect.effect_id === control.source_action_effect_id);
    const effect = matchingEffects.length === 1 ? matchingEffects[0] : null;
    const evaluation = effect
      ? evaluations.filter((row) => row.evaluation_id === effect.evaluation_id)
      : [];
    const mergedIntents = effect
      ? intents.filter((intent) => intent.merged_effect_id === effect.effect_id)
      : [];
    const intent = mergedIntents.length === 1 ? mergedIntents[0] : null;
    const ruleActionRows = intent
      ? ruleActions.filter((row) => row.rule_action_id === intent.rule_action_id)
      : [];
    const ruleAction = ruleActionRows.length === 1 ? ruleActionRows[0] : null;
    const runtimeRuleRows = ruleAction
      ? runtimeRules.filter((row) => row.rule_id === ruleAction.rule_id)
      : [];
    const runtimeRule = runtimeRuleRows.length === 1 ? runtimeRuleRows[0] : null;
    const runtimeRuleSetRows = runtimeRule && evaluation.length === 1
      ? runtimeRuleSets.filter((binding) => (
        binding.point_version_id === evaluation[0].point_version_id
        && binding.rule_set_version_id === runtimeRule.rule_set_version_id
      ))
      : [];
    const runtimeActionRows = ruleAction
      ? runtimeActions.filter((action) => action.action_version_id === ruleAction.action_version_id)
      : [];
    const runtimeAction = runtimeActionRows.length === 1 ? runtimeActionRows[0] : null;
    const staticRuleActionRows = intent
      ? staticRuleActions.filter((row) => row.ruleActionId === intent.rule_action_id)
      : [];
    const staticRuleAction = staticRuleActionRows.length === 1 ? staticRuleActionRows[0] : null;
    const staticRuleRows = staticRuleAction
      ? staticRules.filter((row) => row.ruleId === staticRuleAction.ruleId)
      : [];
    const staticRule = staticRuleRows.length === 1 ? staticRuleRows[0] : null;
    const staticActionRows = staticRuleAction
      ? staticActions.filter((row) => row.actionVersionId === staticRuleAction.actionVersionId)
      : [];
    const staticAction = staticActionRows.length === 1 ? staticActionRows[0] : null;
    const formattableTarget = typeof control.role_type === "string"
      && isCanonicalBigIntId(control.role_id)
      && typeof control.control_instance_key === "string";
    const targetKey = formattableTarget
      ? `${control.role_type}:${control.role_id}:${control.control_instance_key}`
      : null;
    const valid = effect && evaluation.length === 1 && intent && ruleAction
      && runtimeRule && runtimeRuleSetRows.length === 1 && runtimeAction
      && staticRuleAction && staticRule && staticAction
      && evaluation[0].evaluation_mode === "PRIMARY"
      && evaluation[0].technical_status === "SUCCEEDED"
      && evaluation[0].release_id === fixture.release?.releaseId
      && evaluation[0].decision_point_id === fixture.decisionPoint?.decisionPointId
      && evaluation[0].point_version_id === fixture.pointVersion?.pointVersionId
      && effect.effect_mode === "REAL" && effect.effect_status === "APPLIED"
      && effect.execution_type === "LOCAL_SYNC" && effect.execution_status === "SUCCEEDED"
      && effect.target_type === "SETTLEMENT_CONTROL"
      && intent.source_type === "RULE" && intent.status === "MERGED"
      && effect.evaluation_id === intent.evaluation_id
      && evaluation[0].evaluation_id === intent.evaluation_id
      && effect.action_version_id === intent.action_version_id
      && intent.action_version_id === ruleAction.action_version_id
      && ruleAction.rule_id === staticRuleAction.ruleId
      && ruleAction.action_version_id === staticRuleAction.actionVersionId
      && runtimeRule.rule_set_version_id === staticRule.ruleSetVersionId
      && selectedRuleSets.has(staticRule.ruleSetVersionId)
      && staticAction.actionVersionId === intent.action_version_id
      && runtimeAction.action_code === staticAction.actionCode
      && runtimeAction.action_category === staticAction.actionCategory
      && runtimeAction.terminal_flag === staticAction.terminalFlag
      && runtimeAction.status === staticAction.status
      && runtimeAction.shadow_supported_flag === staticAction.shadowSupportedFlag
      && runtimeAction.merge_policy === staticAction.mergePolicy
      && effect.target_type === intent.resolved_target_type
      && effect.target_key === intent.resolved_target_key
      && targetKey !== null && effect.target_key === targetKey
      && typeof control.control_instance_key === "string"
      && control.control_instance_key.startsWith(`RULE_ACTION:${intent.rule_action_id}:`);
    if (!valid) add("REFERENCE_INTEGRITY", "CR_SETTLEMENT_CONTROL_EFFECT_INVALID", {
      path: `crossRowRows.settlement_control.${control.control_id}`,
    });
    if (effect && (new Set(["SHADOW", "REPLAY"]).has(evaluation[0]?.evaluation_mode)
      || effect.effect_mode === "SIMULATED")) {
      add("REFERENCE_INTEGRITY", "CR_SIMULATED_EFFECT_SETTLEMENT_CONTROL", {
        path: `crossRowRows.settlement_control.${control.control_id}`,
      });
    }
  }
}

function validateSettlementControlEffectUniqueness(rowsByTable, add) {
  const contract = SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS.CR_SETTLEMENT_CONTROL_EFFECT_UNIQUE;
  const controls = settlementRows(rowsByTable, contract.child.table);
  const counts = new Map();
  for (const control of controls) {
    const effectId = control[contract.child.foreignKey];
    counts.set(effectId, (counts.get(effectId) ?? 0) + 1);
  }
  if ([...counts.values()].some((count) => count > contract.maximum)) {
    add("REFERENCE_INTEGRITY", contract.code, { path: `crossRowRows.${contract.child.table}` });
  }
}

function settlementHistoryIsValid(control, history) {
  const ordered = [...history].sort((left, right) => left.aggregate_version - right.aggregate_version);
  const initial = ordered.filter((row) => row.aggregate_version === 1
    && row.from_status === null && row.to_status === "ACTIVE"
    && row.trigger_type === "ACTION_EFFECT");
  if (initial.length !== 1 || initial[0].trigger_id !== String(control.source_action_effect_id)) return false;
  if (ordered.some((row, index) => !Number.isInteger(row.aggregate_version)
    || row.aggregate_version !== index + 1)) return false;
  for (let index = 1; index < ordered.length; index += 1) {
    if (ordered[index].from_status !== ordered[index - 1].to_status) return false;
  }
  const last = ordered.at(-1);
  const expectedSlot = control.status === "ACTIVE" ? 1 : null;
  return last?.to_status === control.status
    && last.aggregate_version === control.version
    && control.active_slot === expectedSlot;
}

function validateSettlementControlAggregate(rowsByTable, add) {
  const contract = SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS.CR_SETTLEMENT_CONTROL_AGGREGATE;
  const controls = settlementRows(rowsByTable, contract.root.table);
  const scopes = settlementRows(rowsByTable, contract.scopes.table);
  const changes = settlementRows(rowsByTable, contract.initialStateChange.table);
  for (const control of controls) {
    const ownedScopes = scopes.filter((scope) => scope[contract.scopes.foreignKey] === control.control_id);
    const history = changes.filter((change) => change[contract.initialStateChange.foreignKey] === control.control_id);
    if (ownedScopes.length < contract.scopes.minimum || !settlementHistoryIsValid(control, history)) {
      add("REFERENCE_INTEGRITY", contract.code, {
        path: `crossRowRows.${contract.root.table}.${control.control_id}`,
      });
    }
  }
}

function validateSettlementControlEvents(rowsByTable, add) {
  const contract = SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS.CR_SETTLEMENT_CONTROL_EVENT;
  const controls = settlementRows(rowsByTable, contract.root.table);
  const changes = settlementRows(rowsByTable, contract.versionHistory.table);
  const events = settlementRows(rowsByTable, contract.joins[1].to.table);
  for (const control of controls) {
    const history = changes.filter((change) => change[contract.versionHistory.ownerField] === control.control_id);
    const versions = history.map((change) => change[contract.versionHistory.versionField])
      .sort((left, right) => left - right);
    let valid = Number.isInteger(control[contract.root.version])
      && versions.length === control[contract.root.version]
      && versions.every((version, index) => version === index + contract.versionHistory.firstVersion);
    for (const version of versions) {
      const matches = events.filter((event) => event.source_id === control.control_id
        && event.source_version === version
        && event.source_type === "SETTLEMENT_CONTROL"
        && event.event_type === "SettlementControlChanged");
      if (matches.length !== 1) valid = false;
    }
    if (!valid) add("REFERENCE_INTEGRITY", contract.code, {
      path: `crossRowRows.${contract.root.table}.${control.control_id}`,
    });
  }
}

function validateSettlementControlCurrency(rowsByTable, add) {
  const contract = SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS.CR_SETTLEMENT_CONTROL_CURRENCY;
  const controls = settlementRows(rowsByTable, contract.when.table)
    .filter((control) => control[contract.when.field] === contract.when.equals);
  const scopes = settlementRows(rowsByTable, contract.children.table);
  for (const control of controls) {
    const owned = scopes.filter((scope) => scope[contract.children.foreignKey] === control.control_id);
    if (owned.length < contract.children.minimum
      || owned.some((scope) => scope.currency === null || scope.currency !== control.required_currency)) {
      add("REFERENCE_INTEGRITY", contract.code, {
        path: `crossRowRows.${contract.when.table}.${control.control_id}`,
      });
    }
  }
}

function validateSettlementAssessmentDomain(rowsByTable, add) {
  const contract = SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS.CR_SETTLEMENT_ASSESSMENT_DOMAIN;
  validateSettlementAssessmentDomainContractMetadata(contract);
  const evaluations = settlementRows(rowsByTable, contract.root.table);
  const points = settlementRows(rowsByTable, contract.joins[0].to.table);
  const assessments = settlementRows(rowsByTable, contract.children[0].table);
  for (const evaluation of evaluations) {
    const point = points.find((candidate) => candidate.decision_point_id === evaluation.decision_point_id);
    if (!point) continue;
    const expected = point.business_domain === "SETTLEMENT" ? 1 : 0;
    const owned = assessments.filter((assessment) => assessment.evaluation_id === evaluation.evaluation_id);
    let valid = owned.length === expected;
    if (valid && expected === 1) {
      const context = { evaluation, decisionPoint: point, assessment: owned[0] };
      const fieldsMatch = contract.fieldAssertions.every((assertion) => assertionMatches(context, assertion));
      const actualResult = Object.fromEntries(contract.resultSelectors.map((selector) => [
        selector.mappingKey,
        readCrossRowField(context[selector.alias], selector.field, selector.alias),
      ]));
      const resultMatches = contract.allowedResultMappings.some((mapping) => (
        contract.resultSelectors.every((selector) => (
          mapping[selector.mappingKey] === actualResult[selector.mappingKey]
        ))
      ));
      valid = fieldsMatch && resultMatches;
    }
    if (!valid) add("REFERENCE_INTEGRITY", contract.code, {
      path: `crossRowRows.${contract.root.table}.${evaluation.evaluation_id}`,
    });
  }
}

function validateSettlementCrossRowContracts(fixture, rowsByTable, add) {
  validateSettlementControlEffectUniqueness(rowsByTable, add);
  validateSettlementControlOrigin(fixture, rowsByTable, add);
  validateSettlementControlAggregate(rowsByTable, add);
  validateSettlementControlEvents(rowsByTable, add);
  validateSettlementControlCurrency(rowsByTable, add);
  validateSettlementAssessmentDomain(rowsByTable, add);
}

function validateCrossRowContracts(fixture, add) {
  executeCrossRowRegistry(fixture.crossRowRows, CROSS_ROW_CONTRACTS, add);
  validateRequiredSettlementRuntimeRows(fixture.crossRowRows, add);
  validateSettlementCrossRowContracts(fixture, fixture.crossRowRows, add);
}

export function validateDecisionRelease(fixture) {
  const { add, finish } = createCollector(fixture?.release?.releaseId);
  validateRequiredCollections(fixture ?? {}, add);
  validateBusinessIdTypes(fixture ?? {}, "", add);
  const indexes = buildDecisionIndexes(fixture ?? {}, add);
  validateDecisionReferences(fixture ?? {}, indexes, add);
  validateAstShape(fixture ?? {}, indexes, add);
  validateAstTypes(fixture ?? {}, indexes, add);
  validateFeatureDependencies(fixture ?? {}, indexes, add);
  validateHandlers(fixture ?? {}, indexes, add);
  validateActionParameters(fixture ?? {}, indexes, add);
  validateTerminalActions(fixture ?? {}, indexes, add);
  validateSystemObligations(fixture ?? {}, indexes, add);
  validateCrossRowContracts(fixture ?? {}, add);
  return finish();
}

function candidateStateEvents(fixture) {
  return transitionRows(fixture, "auditEvents").filter((event) => event.eventType === "STATE_CHANGE"
    && event.releaseId === fixture.candidateAfter?.releaseId);
}

function historyReached(fixture, statuses) {
  return candidateStateEvents(fixture).some((event) => statuses.has(event.toStatus));
}

function validateShadowGate(fixture, add) {
  const candidate = fixture.candidateAfter;
  const events = transitionRows(fixture, "auditEvents");
  const shadowingEvents = candidateStateEvents(fixture).filter((event) => event.toStatus === "SHADOWING");
  const shadowingEvent = shadowingEvents.length === 1 ? shadowingEvents[0] : null;
  if (historyReached(fixture, new Set(["SHADOWING", "REVIEW_PENDING", "APPROVED", "ACTIVE", "SUPERSEDED"]))) {
    for (const checkCode of REQUIRED_STATIC_CHECKS) {
      const checks = events.filter((event) => event.eventType === "STATIC_CHECK"
        && event.releaseId === candidate.releaseId && event.checkCode === checkCode);
      if (checks.length === 0) add("SHADOW_GATE", "STATIC_CHECK_MISSING", { path: `auditEvents.${checkCode}` });
      if (checks.length > 1) add("SHADOW_GATE", "STATIC_CHECK_DUPLICATE", { path: `auditEvents.${checkCode}` });
      if (checks.some((event) => event.checkStatus !== "PASSED")) add("SHADOW_GATE", "STATIC_CHECK_FAILED", { path: `auditEvents.${checkCode}` });
      if (shadowingEvent && isCanonicalUtcMicrosecondTimestamp(shadowingEvent.eventTime)
        && isCanonicalBigIntId(shadowingEvent.auditEventId)
        && checks.some((event) => isCanonicalUtcMicrosecondTimestamp(event.eventTime)
          && isCanonicalBigIntId(event.auditEventId)
          && compareCanonicalAuditEvents(event, shadowingEvent) >= 0)) {
        add("SHADOW_GATE", "STATIC_CHECK_AFTER_SHADOWING", { path: `auditEvents.${checkCode}` });
      }
    }
  }
  if (!historyReached(fixture, new Set(["REVIEW_PENDING", "APPROVED", "ACTIVE", "SUPERSEDED"]))) return;
  const run = transitionRows(fixture, "shadowRuns").find((row) => row.shadowRunId === fixture.selectedShadowRunId);
  if (!run) {
    add("SHADOW_GATE", "SHADOW_RUN_MISSING", { path: "selectedShadowRunId" });
    return;
  }
  if (run.candidateReleaseId !== candidate.releaseId) add("SHADOW_GATE", "SHADOW_RUN_RELEASE_MISMATCH", { path: `shadowRuns.${run.shadowRunId}` });
  if (run.baselineReleaseId === run.candidateReleaseId) add("SHADOW_GATE", "SHADOW_RUN_BASELINE_INVALID", { path: `shadowRuns.${run.shadowRunId}` });
  const baseline = transitionRows(fixture, "relatedReleasesBefore").find((release) => release.releaseId === run.baselineReleaseId);
  if (!baseline || baseline.status !== "ACTIVE" || baseline.activeSlot !== 1
    || baseline.decisionPointId !== candidate.decisionPointId
    || baseline.environmentCode !== candidate.environmentCode
    || (isPresent(run.decisionPointId) && run.decisionPointId !== candidate.decisionPointId)
    || (isPresent(run.environmentCode) && run.environmentCode !== candidate.environmentCode)) {
    add("SHADOW_GATE", "SHADOW_RUN_BASELINE_INVALID", { path: `shadowRuns.${run.shadowRunId}` });
  }
  if (run.status !== "COMPLETED") add("SHADOW_GATE", "SHADOW_RUN_NOT_COMPLETED", { path: `shadowRuns.${run.shadowRunId}` });
  if (run.checkStatus !== "PASSED") add("SHADOW_GATE", "SHADOW_RUN_QUALITY_FAILED", { path: `shadowRuns.${run.shadowRunId}` });
  if (!isCanonicalUtcMicrosecondTimestamp(run.completedTime)) {
    add("SHADOW_GATE", "SHADOW_RUN_COMPLETION_TIME_INVALID", { path: `shadowRuns.${run.shadowRunId}.completedTime` });
  }
  if (shadowingEvent && isCanonicalUtcMicrosecondTimestamp(shadowingEvent.eventTime)
    && isCanonicalUtcMicrosecondTimestamp(run.completedTime)
    && run.completedTime < shadowingEvent.eventTime) {
    add("SHADOW_GATE", "SHADOW_RUN_COMPLETION_BEFORE_SHADOWING", { path: `shadowRuns.${run.shadowRunId}.completedTime` });
  }
  const reviewPendingEvents = candidateStateEvents(fixture).filter((event) => event.toStatus === "REVIEW_PENDING");
  const reviewPendingEvent = reviewPendingEvents.length === 1 ? reviewPendingEvents[0] : null;
  if (reviewPendingEvent && isCanonicalUtcMicrosecondTimestamp(reviewPendingEvent.eventTime)
    && isCanonicalUtcMicrosecondTimestamp(run.completedTime)
    && run.completedTime > reviewPendingEvent.eventTime) {
    add("SHADOW_GATE", "SHADOW_RUN_COMPLETION_AFTER_REVIEW_PENDING", { path: `shadowRuns.${run.shadowRunId}.completedTime` });
  }
}

function validateApprovalGate(fixture, add) {
  const candidate = fixture.candidateAfter;
  const requiresDualReview = historyReached(fixture, new Set(["APPROVED", "ACTIVE", "SUPERSEDED"]));
  const approvals = transitionRows(fixture, "auditEvents").filter((event) => event.eventType === "APPROVAL" && event.releaseId === candidate.releaseId);
  const reviewPendingEvents = candidateStateEvents(fixture).filter((event) => event.toStatus === "REVIEW_PENDING");
  const reviewPendingEvent = reviewPendingEvents.length === 1 ? reviewPendingEvents[0] : null;
  const terminalEvents = candidateStateEvents(fixture).filter((event) => (
    new Set(["REJECTED", "SUPERSEDED"]).has(event.toStatus)
    && isCanonicalUtcMicrosecondTimestamp(event.eventTime)
    && isCanonicalBigIntId(event.auditEventId)
  ));
  const requestIds = new Map();
  for (const approval of approvals) {
    const validRequestId = isCanonicalBusinessId(approval.approvalRequestId);
    const validReviewerId = isCanonicalBigIntId(approval.reviewerId);
    if (!validRequestId) add("APPROVAL_GATE", "APPROVAL_REQUEST_INVALID", { path: "auditEvents.APPROVAL.approvalRequestId" });
    if (validRequestId && requestIds.has(approval.approvalRequestId)) {
      add("APPROVAL_GATE", "APPROVAL_REQUEST_CONFLICT", { path: `auditEvents.${approval.approvalRequestId ?? "missing"}` });
    }
    if (validRequestId) requestIds.set(approval.approvalRequestId, approval);
    if (approval.shadowRunId !== fixture.selectedShadowRunId) add("APPROVAL_GATE", "APPROVAL_SHADOW_RUN_INVALID", { path: `auditEvents.${approval.approvalRequestId}` });
    if (!validReviewerId) add("APPROVAL_GATE", "APPROVAL_REVIEWER_INVALID", { path: `auditEvents.${approval.approvalRequestId}` });
    if (!new Set(["APPROVED", "REJECTED"]).has(approval.approvalStatus)) {
      add("APPROVAL_GATE", "APPROVAL_STATUS_INVALID", { path: `auditEvents.${approval.approvalRequestId}` });
    } else if (approval.approvalStatus === "REJECTED") {
      add("APPROVAL_GATE", "APPROVAL_REJECTED", { path: `auditEvents.${approval.approvalRequestId}` });
    }
    const orderedAfterReviewPending = reviewPendingEvent
      && isCanonicalUtcMicrosecondTimestamp(approval.eventTime)
      && isCanonicalBigIntId(approval.auditEventId)
      && isCanonicalUtcMicrosecondTimestamp(reviewPendingEvent.eventTime)
      && isCanonicalBigIntId(reviewPendingEvent.auditEventId)
      && compareCanonicalAuditEvents(approval, reviewPendingEvent) > 0;
    if (!reviewPendingEvent || (!orderedAfterReviewPending
      && isCanonicalUtcMicrosecondTimestamp(approval.eventTime)
      && isCanonicalBigIntId(approval.auditEventId)
      && isCanonicalUtcMicrosecondTimestamp(reviewPendingEvent.eventTime)
      && isCanonicalBigIntId(reviewPendingEvent.auditEventId))) {
      add("APPROVAL_GATE", "APPROVAL_BEFORE_REVIEW_PENDING", { path: `auditEvents.${approval.auditEventId}` });
    }
    if (isCanonicalUtcMicrosecondTimestamp(approval.eventTime)
      && isCanonicalBigIntId(approval.auditEventId)
      && terminalEvents.some((event) => compareCanonicalAuditEvents(approval, event) > 0)) {
      add("APPROVAL_GATE", "APPROVAL_AFTER_TERMINAL", { path: `auditEvents.${approval.auditEventId}` });
    }
  }
  if (!requiresDualReview) return;
  const approvedReviewers = new Set(approvals.filter((event) => event.approvalStatus === "APPROVED"
    && event.shadowRunId === fixture.selectedShadowRunId
    && isCanonicalBigIntId(event.reviewerId)).map((event) => event.reviewerId));
  if (approvedReviewers.size < 2) add("APPROVAL_GATE", "APPROVAL_DISTINCT_REVIEWERS_INSUFFICIENT", { path: "auditEvents.APPROVAL" });
  const completingApproval = dualReviewCompletingApproval(fixture, candidate.releaseId);
  if (completingApproval) {
    const approvedStateEvents = candidateStateEvents(fixture).filter((event) => (
      event.fromStatus === "REVIEW_PENDING" && event.toStatus === "APPROVED"
    ));
    const approvedStateEvent = approvedStateEvents.length === 1 ? approvedStateEvents[0] : null;
    if (!approvedStateEvent || approvedStateEvent.triggerType !== "APPROVAL"
      || approvedStateEvent.triggerId !== completingApproval.approvalRequestId
      || approvedStateEvent.eventTime !== completingApproval.eventTime) {
      add("APPROVAL_GATE", "APPROVAL_COMPLETION_STATE_MISMATCH", { path: "auditEvents.STATE_CHANGE.REVIEW_PENDING.APPROVED" });
    }
    for (const approval of approvals) {
      if (approval === completingApproval || !isCanonicalUtcMicrosecondTimestamp(approval.eventTime)
        || !isCanonicalBigIntId(approval.auditEventId)) continue;
      if (compareCanonicalAuditEvents(approval, completingApproval) > 0) {
        add("APPROVAL_GATE", "APPROVAL_AFTER_COMPLETION", { path: `auditEvents.${approval.auditEventId}` });
      }
    }
  }
}

function integerVersion(value) {
  if (typeof value === "bigint") return value;
  if ((typeof value === "number" && Number.isInteger(value)) || (typeof value === "string" && /^\d+$/.test(value))) return BigInt(value);
  return null;
}

function sameStableReleaseIdentity(left, right) {
  return left?.releaseId === right?.releaseId
    && left?.decisionPointId === right?.decisionPointId
    && left?.pointVersionId === right?.pointVersionId
    && left?.environmentCode === right?.environmentCode
    && left?.contentHash === right?.contentHash;
}

function releaseIdentityIsTyped(release) {
  return ["releaseId", "decisionPointId", "pointVersionId"]
    .every((field) => isCanonicalBigIntId(release?.[field]))
    && isCanonicalBusinessId(release?.environmentCode)
    && isCanonicalSha256(release?.contentHash);
}

function releaseSlotMatchesStatus(release) {
  return release?.status === "ACTIVE" ? release.activeSlot === 1 : release?.activeSlot === null;
}

function validateStateTransitions(fixture, add) {
  const candidate = fixture.candidateAfter;
  const events = transitionRows(fixture, "auditEvents")
    .filter((event) => event.eventType === "STATE_CHANGE" && event.releaseId === candidate?.releaseId)
    .map((event) => ({ event, version: integerVersion(event.aggregateVersion) }))
    .sort((left, right) => {
      if (left.version === null) return 1;
      if (right.version === null) return -1;
      if (left.version !== right.version) return left.version < right.version ? -1 : 1;
      return JSON.stringify(left.event).localeCompare(JSON.stringify(right.event));
    });
  if (events.length === 0 || events[0].version !== 1n || isPresent(events[0].event.fromStatus) || events[0].event.toStatus !== "DRAFT") {
    add("STATE_TRANSITION", "STATE_INITIAL_INVALID", { path: "auditEvents.STATE_CHANGE" });
  }
  for (let index = 0; index < events.length; index += 1) {
    const current = events[index];
    if (current.version === null) add("STATE_TRANSITION", "STATE_VERSION_GAP", { path: "auditEvents.STATE_CHANGE" });
    if (index === 0) continue;
    const previous = events[index - 1];
    if (current.version === null || previous.version === null || current.version !== previous.version + 1n) {
      add("STATE_TRANSITION", "STATE_VERSION_GAP", { path: `auditEvents.${current.event.aggregateVersion}` });
    }
    if (current.event.fromStatus !== previous.event.toStatus) add("STATE_TRANSITION", "STATE_FROM_MISMATCH", { path: `auditEvents.${current.event.aggregateVersion}` });
    if (isCanonicalUtcMicrosecondTimestamp(current.event.eventTime)
      && isCanonicalUtcMicrosecondTimestamp(previous.event.eventTime)
      && current.event.eventTime < previous.event.eventTime) {
      add("STATE_TRANSITION", "STATE_EVENT_TIME_ORDER_INVALID", { path: `auditEvents.${current.event.auditEventId}.eventTime` });
    }
  }
  const versionCounts = new Map();
  for (const { version } of events) versionCounts.set(version, (versionCounts.get(version) ?? 0) + 1);
  if ([...versionCounts.values()].some((count) => count > 1)) {
    add("STATE_TRANSITION", "STATE_VERSION_GAP", { path: "auditEvents.STATE_CHANGE" });
  }
  for (const { event } of events) {
    if (typeof event.triggerType !== "string" || event.triggerType.length === 0
      || !isCanonicalBusinessId(event.triggerId)) {
      add("STATE_TRANSITION", "STATE_TRIGGER_INVALID", { path: `auditEvents.${event.aggregateVersion}` });
    }
    if (isPresent(event.fromStatus) && !(RELEASE_GRAPH[event.fromStatus] ?? []).includes(event.toStatus)) {
      add("STATE_TRANSITION", "STATE_EDGE_FORBIDDEN", { path: `auditEvents.${event.aggregateVersion}` });
    }
  }
  const last = events.at(-1);
  if (last && last.event.toStatus !== candidate.status) add("STATE_TRANSITION", "STATE_ROOT_STATUS_MISMATCH", { path: "candidateAfter.status" });
  if (last && integerVersion(candidate.version) !== last.version) add("STATE_TRANSITION", "STATE_ROOT_VERSION_MISMATCH", { path: "candidateAfter.version" });
  const before = fixture.candidateBefore;
  const previous = events.at(-2);
  const initialMutation = !isPresent(before) && events.length === 1
    && last?.version === 1n && last.event.fromStatus === null && last.event.toStatus === "DRAFT"
    && candidate?.status === "DRAFT" && integerVersion(candidate?.version) === 1n;
  if (initialMutation) {
    if (!releaseIdentityIsTyped(candidate)) add("STATE_TRANSITION", "STATE_IDENTITY_MISMATCH", { path: "candidateAfter" });
    if (!releaseSlotMatchesStatus(candidate)) add("STATE_TRANSITION", "STATE_ACTIVE_SLOT_MISMATCH", { path: "candidateAfter.activeSlot" });
    if (rowsOf(fixture, "relatedReleasesBefore").some((release) => release?.releaseId === candidate?.releaseId)) {
      add("STATE_TRANSITION", "STATE_INITIAL_RELEASE_ALREADY_EXISTS", { path: "relatedReleasesBefore" });
    }
    const persisted = transitionRows(fixture, "relatedReleasesAfter").some((release) => sameReleaseSnapshot(release, candidate));
    if (!persisted) add("STATE_TRANSITION", "STATE_MUTATION_MISMATCH", { path: "relatedReleasesAfter" });
  } else {
    if (!before || !candidate || !releaseIdentityIsTyped(before) || !releaseIdentityIsTyped(candidate)
      || !sameStableReleaseIdentity(before, candidate)) {
      add("STATE_TRANSITION", "STATE_IDENTITY_MISMATCH", { path: "candidateBefore" });
    }
    if (!versionIncremented(before, candidate)) add("STATE_TRANSITION", "STATE_CAS_MISMATCH", { path: "candidateAfter.version" });
    if (!last || last.event.fromStatus !== before?.status || last.event.toStatus !== candidate?.status
      || last.version !== integerVersion(candidate?.version)
      || !previous || previous.event.toStatus !== before?.status || previous.version !== integerVersion(before?.version)) {
      add("STATE_TRANSITION", "STATE_MUTATION_MISMATCH", { path: "auditEvents.STATE_CHANGE" });
    }
    if (!releaseSlotMatchesStatus(before) || !releaseSlotMatchesStatus(candidate)) {
      add("STATE_TRANSITION", "STATE_ACTIVE_SLOT_MISMATCH", { path: "candidateAfter.activeSlot" });
    }
    if (!transitionRows(fixture, "relatedReleasesAfter").some((release) => sameReleaseSnapshot(release, candidate))) {
      add("STATE_TRANSITION", "STATE_AFTER_SNAPSHOT_MISSING", { path: "relatedReleasesAfter" });
    }
  }
  for (const event of transitionRows(fixture, "auditEvents").filter((row) => row.eventType === "STATE_CHANGE")) {
    if (typeof event.releaseId !== "string" || event.releaseId.length === 0
      || typeof event.triggerType !== "string" || event.triggerType.length === 0
      || !isCanonicalBusinessId(event.triggerId)) {
      add("STATE_TRANSITION", "STATE_TRIGGER_INVALID", { path: "auditEvents.STATE_CHANGE" });
    }
  }
}

function sameScope(left, right) {
  return left?.decisionPointId === right?.decisionPointId && left?.environmentCode === right?.environmentCode;
}

function versionIncremented(before, after) {
  const beforeVersion = integerVersion(before?.version);
  const afterVersion = integerVersion(after?.version);
  return beforeVersion !== null && afterVersion !== null && afterVersion === beforeVersion + 1n;
}

function sameReleaseSnapshot(left, right) {
  return sameStableReleaseIdentity(left, right)
    && left?.status === right?.status
    && left?.activeSlot === right?.activeSlot
    && integerVersion(left?.version) === integerVersion(right?.version);
}

function sameStateEvent(left, right) {
  return left?.auditEventId === right?.auditEventId
    && left?.eventType === right?.eventType
    && left?.eventTime === right?.eventTime
    && left?.releaseId === right?.releaseId
    && left?.fromStatus === right?.fromStatus
    && left?.toStatus === right?.toStatus
    && left?.triggerType === right?.triggerType
    && left?.triggerId === right?.triggerId
    && integerVersion(left?.aggregateVersion) === integerVersion(right?.aggregateVersion);
}

function compareCanonicalAuditEvents(left, right) {
  if (left.eventTime !== right.eventTime) return left.eventTime < right.eventTime ? -1 : 1;
  const leftId = BigInt(left.auditEventId);
  const rightId = BigInt(right.auditEventId);
  return leftId === rightId ? 0 : leftId < rightId ? -1 : 1;
}

function dualReviewCompletingApproval(fixture, candidateReleaseId) {
  const approvals = transitionRows(fixture, "auditEvents").filter((event) => event.eventType === "APPROVAL"
    && event.releaseId === candidateReleaseId
    && event.shadowRunId === fixture.selectedShadowRunId
    && event.approvalStatus === "APPROVED"
    && isCanonicalBusinessId(event.approvalRequestId)
    && isCanonicalBigIntId(event.reviewerId));
  if (approvals.some((event) => !isCanonicalUtcMicrosecondTimestamp(event.eventTime)
    || !isCanonicalBigIntId(event.auditEventId))) return null;
  approvals.sort(compareCanonicalAuditEvents);
  const reviewers = new Set();
  for (const approval of approvals) {
    reviewers.add(approval.reviewerId);
    if (reviewers.size === 2) return approval;
  }
  return null;
}

function validateActivationPair(fixture, add) {
  const rootStatus = fixture.candidateAfter?.status;
  if (rootStatus !== "ACTIVE" && rootStatus !== "SUPERSEDED") return;
  const activation = fixture.activation;
  if (rootStatus === "SUPERSEDED") {
    add("ACTIVATION_PAIR", "ACTIVATION_ROOT_MUST_BECOME_ACTIVE", { path: "candidateAfter.status" });
    if (!activation?.candidateBefore || !activation.candidateAfter || !activation.previousBefore
      || !activation.previousAfter || !activation.candidateStateEvent || !activation.previousStateEvent) {
      add("ACTIVATION_PAIR", "ACTIVATION_PAIR_MISSING", { path: "activation" });
    }
    return;
  }
  if (!activation?.candidateBefore || !activation.candidateAfter || !activation.previousBefore
    || !activation.previousAfter || !activation.candidateStateEvent || !activation.previousStateEvent) {
    add("ACTIVATION_PAIR", "ACTIVATION_PAIR_MISSING", { path: "activation" });
    return;
  }
  const { candidateBefore, candidateAfter, previousBefore, previousAfter, candidateStateEvent, previousStateEvent } = activation;
  const rootPairMatches = sameReleaseSnapshot(candidateBefore, fixture.candidateBefore)
    && sameReleaseSnapshot(candidateAfter, fixture.candidateAfter);
  if (!rootPairMatches) {
    add("ACTIVATION_PAIR", "ACTIVATION_IDENTITY_MISMATCH", { path: "activation.candidate" });
  }
  if (!releaseIdentityIsTyped(candidateBefore) || !releaseIdentityIsTyped(candidateAfter)
    || !releaseIdentityIsTyped(previousBefore) || !releaseIdentityIsTyped(previousAfter)
    || !sameStableReleaseIdentity(candidateBefore, candidateAfter)
    || !sameStableReleaseIdentity(previousBefore, previousAfter)) {
    add("ACTIVATION_PAIR", "ACTIVATION_IDENTITY_MISMATCH", { path: "activation" });
  }
  if (!sameScope(candidateBefore, candidateAfter) || !sameScope(candidateAfter, previousBefore)
    || !sameScope(previousBefore, previousAfter) || candidateAfter.releaseId === previousAfter.releaseId) {
    add("ACTIVATION_PAIR", "ACTIVATION_SCOPE_MISMATCH", { path: "activation" });
  }
  const candidateValid = candidateBefore.status === "APPROVED" && candidateBefore.activeSlot === null
    && candidateAfter.status === "ACTIVE" && candidateAfter.activeSlot === 1;
  const previousValid = previousBefore.status === "ACTIVE" && previousBefore.activeSlot === 1
    && previousAfter.status === "SUPERSEDED" && previousAfter.activeSlot === null;
  if (!candidateValid || !previousValid) add("ACTIVATION_PAIR", "ACTIVATION_SLOT_MISMATCH", { path: "activation" });
  if (!versionIncremented(candidateBefore, candidateAfter) || !versionIncremented(previousBefore, previousAfter)) {
    add("ACTIVATION_PAIR", "ACTIVATION_CAS_MISMATCH", { path: "activation" });
  }
  const candidateEventValid = candidateStateEvent.eventType === "STATE_CHANGE"
    && candidateStateEvent.releaseId === candidateAfter.releaseId
    && candidateStateEvent.fromStatus === "APPROVED" && candidateStateEvent.toStatus === "ACTIVE"
    && integerVersion(candidateStateEvent.aggregateVersion) === integerVersion(candidateAfter.version);
  const previousEventValid = previousStateEvent.eventType === "STATE_CHANGE"
    && previousStateEvent.releaseId === previousAfter.releaseId
    && previousStateEvent.fromStatus === "ACTIVE" && previousStateEvent.toStatus === "SUPERSEDED"
    && integerVersion(previousStateEvent.aggregateVersion) === integerVersion(previousAfter.version);
  if (!candidateEventValid || !previousEventValid) add("ACTIVATION_PAIR", "ACTIVATION_EVENT_MISMATCH", { path: "activation" });
  const candidateEventPersisted = transitionRows(fixture, "auditEvents").some((event) => event.eventType === "STATE_CHANGE"
    && sameStateEvent(event, candidateStateEvent));
  const previousEventPersisted = transitionRows(fixture, "auditEvents").some((event) => event.eventType === "STATE_CHANGE"
    && sameStateEvent(event, previousStateEvent));
  if (!candidateEventPersisted || !previousEventPersisted) {
    add("ACTIVATION_PAIR", "ACTIVATION_EVENT_MISMATCH", { path: "auditEvents.STATE_CHANGE" });
    add("ACTIVATION_PAIR", "ACTIVATION_EVENT_IDENTITY_MISMATCH", { path: "auditEvents.STATE_CHANGE" });
  }
  const completingApproval = dualReviewCompletingApproval(fixture, candidateAfter.releaseId);
  if (!completingApproval || candidateStateEvent.triggerType !== "APPROVAL"
    || previousStateEvent.triggerType !== "APPROVAL"
    || candidateStateEvent.triggerId !== completingApproval.approvalRequestId
    || previousStateEvent.triggerId !== completingApproval.approvalRequestId) {
    add("ACTIVATION_PAIR", "ACTIVATION_TRIGGER_MISMATCH", { path: "activation.candidateStateEvent.triggerId" });
  }
  if (completingApproval && (candidateStateEvent.eventTime !== completingApproval.eventTime
    || previousStateEvent.eventTime !== completingApproval.eventTime)) {
    add("ACTIVATION_PAIR", "ACTIVATION_COMMAND_TIME_MISMATCH", { path: "activation.candidateStateEvent.eventTime" });
  }
  const approvedStateEvents = transitionRows(fixture, "auditEvents").filter((event) => (
    event.eventType === "STATE_CHANGE"
    && event.releaseId === candidateAfter.releaseId
    && event.fromStatus === "REVIEW_PENDING"
    && event.toStatus === "APPROVED"
  ));
  const approvedStateEvent = approvedStateEvents.length === 1 ? approvedStateEvents[0] : null;
  if (!completingApproval || !approvedStateEvent
    || approvedStateEvent.triggerType !== "APPROVAL"
    || approvedStateEvent.triggerId !== completingApproval.approvalRequestId
    || approvedStateEvent.eventTime !== completingApproval.eventTime
    || candidateStateEvent.triggerType !== "APPROVAL"
    || candidateStateEvent.triggerId !== completingApproval.approvalRequestId
    || candidateStateEvent.eventTime !== completingApproval.eventTime) {
    add("ACTIVATION_PAIR", "ACTIVATION_APPROVAL_STATE_BINDING_INVALID", { path: "auditEvents.STATE_CHANGE.APPROVAL" });
  }
  const precedingStateEvents = transitionRows(fixture, "auditEvents").filter((event) => event.eventType === "STATE_CHANGE"
    && event.releaseId === candidateBefore.releaseId
    && integerVersion(event.aggregateVersion) === integerVersion(candidateBefore.version));
  const precedingStateEvent = precedingStateEvents.length === 1 ? precedingStateEvents[0] : null;
  if (completingApproval && precedingStateEvent
    && isCanonicalUtcMicrosecondTimestamp(precedingStateEvent.eventTime)
    && completingApproval.eventTime < precedingStateEvent.eventTime) {
    add("ACTIVATION_PAIR", "ACTIVATION_APPROVAL_PRECEDES_HISTORY", { path: "auditEvents.APPROVAL.eventTime" });
  }
  const beforeRows = transitionRows(fixture, "relatedReleasesBefore");
  const previousPersisted = beforeRows.some((release) => sameReleaseSnapshot(release, previousBefore));
  const afterRows = transitionRows(fixture, "relatedReleasesAfter");
  const pairPersisted = afterRows.some((release) => sameReleaseSnapshot(release, candidateAfter))
    && afterRows.some((release) => sameReleaseSnapshot(release, previousAfter));
  if (!previousPersisted || !pairPersisted) add("ACTIVATION_PAIR", "ACTIVATION_PAIR_MISSING", { path: "relatedReleases" });
  const activeBefore = beforeRows.filter((release) => sameScope(release, candidateBefore)
    && release.status === "ACTIVE");
  const malformedActiveBefore = beforeRows.some((release) => release.status === "ACTIVE" && !releaseIdentityIsTyped(release));
  if (malformedActiveBefore || activeBefore.length !== 1 || activeBefore[0].releaseId !== previousBefore.releaseId) {
    add("ACTIVATION_PAIR", "ACTIVATION_SLOT_MISMATCH", { path: "relatedReleasesBefore" });
  }
  const active = afterRows.filter((release) => sameScope(release, candidateAfter)
    && release.status === "ACTIVE");
  const malformedActiveAfter = afterRows.some((release) => release.status === "ACTIVE" && !releaseIdentityIsTyped(release));
  if (malformedActiveAfter || active.length !== 1 || active[0].releaseId !== candidateAfter.releaseId) {
    add("ACTIVATION_PAIR", "ACTIVATION_MULTIPLE_ACTIVE", { path: "relatedReleasesAfter" });
  }
}

export function validateReleaseTransition(fixture) {
  const { add, finish } = createCollector(fixture?.candidateAfter?.releaseId ?? fixture?.candidateBefore?.releaseId);
  validateTransitionClosure(fixture ?? {}, add);
  validateShadowGate(fixture ?? {}, add);
  validateApprovalGate(fixture ?? {}, add);
  validateStateTransitions(fixture ?? {}, add);
  validateActivationPair(fixture ?? {}, add);
  return finish();
}
