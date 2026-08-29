import { createHash } from "node:crypto";

const MAX_SIGNED_BIGINT = 9223372036854775807n;
const POSITIVE_DECIMAL = /^[1-9][0-9]*$/u;
const NONNEGATIVE_DECIMAL = /^(?:0|[1-9][0-9]*)$/u;
const CANONICAL_MONEY = /^(?:0|[1-9][0-9]{0,15})(?:\.[0-9]{0,7}[1-9])?$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const CURRENCY = /^[A-Z]{3}$/u;
const COUNTRY = /^[A-Z]{2}$/u;
const CODE = /^[A-Z][A-Z0-9_]*$/u;
const BUSINESS_INSTANCE_KEY = /^[A-Z0-9][A-Z0-9._:-]{0,95}$/u;
const UTC_MICROSECOND = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})\.(\d{6})Z$/u;

const ACTION_PARAMETER_FIELDS = Object.freeze([
  "booleanValue", "datetimeValue", "durationValue", "featureVersionId", "inputFieldId",
  "numberValue", "parameterDefinitionId", "ruleActionId", "sourceType", "stringValue", "valueSequence",
]);
const SCOPE_FIELDS = Object.freeze(["bankCountry", "currency", "operatingEntityId"]);
const CONTENT_FIELDS = Object.freeze([
  "actionVersionId", "controlInstanceKey", "controlType", "effectiveFrom", "effectiveTo",
  "reasonCode", "releaseConditionCode", "requiredAmount", "requiredCurrency", "roleId", "roleType", "ruleActionId",
]);
const SCOPE_SET_FIELDS = Object.freeze(["controlId", "controlVersion", "scopes"]);
const SCOPE_REF_FIELDS = Object.freeze(["scopeHash", "scopeId"]);
const CONTROL_SET_FIELDS = Object.freeze(["controls", "decision", "requiredAmount", "requiredCurrency"]);
const MATCHED_CONTROL_FIELDS = Object.freeze([
  "controlId", "controlType", "controlVersion", "effectiveFrom", "effectiveTo", "reasonCode",
  "requiredAmount", "requiredCurrency", "scopeCount", "scopeSetHash", "scopes",
]);

const SETTLEMENT_CONTROL_SCOPE_SCHEMA = "CODE-MOR:RISK:SETTLEMENT_CONTROL_SCOPE:1";
const SETTLEMENT_CONTROL_CONTENT_SCHEMA = "CODE-MOR:RISK:SETTLEMENT_CONTROL_CONTENT:1";
const SETTLEMENT_CONTROL_SCOPE_SET_SCHEMA = "CODE-MOR:RISK:SETTLEMENT_CONTROL_SCOPE_SET:1";
const SETTLEMENT_CONTROL_SET_SCHEMA = "CODE-MOR:RISK:SETTLEMENT_CONTROL_SET:1";

function requireClosedRecord(value, fields, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`${path} must be an object`);
  }
  const keys = Reflect.ownKeys(value);
  if (keys.some((key) => typeof key !== "string")
    || keys.length !== fields.length
    || keys.some((key) => !fields.includes(key))) {
    throw new TypeError(`${path} must contain exactly: ${fields.join(", ")}`);
  }
  return value;
}

function requireDenseArray(value, path, nonempty = false) {
  if (!Array.isArray(value) || (nonempty && value.length === 0)) {
    throw new TypeError(`${path} must be a ${nonempty ? "non-empty " : ""}dense array`);
  }
  for (let index = 0; index < value.length; index += 1) {
    if (!Object.hasOwn(value, index)) throw new TypeError(`${path} must be a dense array`);
  }
  return value;
}

function requireUnicodeScalarString(value, path, allowEmpty = false) {
  if (typeof value !== "string" || (!allowEmpty && value.length === 0)) {
    throw new TypeError(`${path} must be a ${allowEmpty ? "" : "non-empty "}string`);
  }
  for (let index = 0; index < value.length; index += 1) {
    const codeUnit = value.charCodeAt(index);
    if (codeUnit >= 0xd800 && codeUnit <= 0xdbff) {
      const next = value.charCodeAt(index + 1);
      if (!(next >= 0xdc00 && next <= 0xdfff)) {
        throw new TypeError(`${path} must contain Unicode scalar values only`);
      }
      index += 1;
    } else if (codeUnit >= 0xdc00 && codeUnit <= 0xdfff) {
      throw new TypeError(`${path} must contain Unicode scalar values only`);
    }
  }
  return value;
}

function requirePattern(value, pattern, path, description) {
  requireUnicodeScalarString(value, path);
  if (!pattern.test(value)) throw new TypeError(`${path} must be ${description}`);
  return value;
}

function requireCanonicalId(value, path) {
  requirePattern(value, POSITIVE_DECIMAL, path, "a canonical positive BIGINT decimal string");
  if (BigInt(value) > MAX_SIGNED_BIGINT) throw new TypeError(`${path} exceeds signed BIGINT`);
  return value;
}

function requireCanonicalNonnegativeBigint(value, path) {
  requirePattern(value, NONNEGATIVE_DECIMAL, path, "a canonical nonnegative BIGINT decimal string");
  if (BigInt(value) > MAX_SIGNED_BIGINT) throw new TypeError(`${path} exceeds signed BIGINT`);
  return value;
}

function requirePositiveInteger(value, path) {
  if (!Number.isSafeInteger(value) || value <= 0) throw new TypeError(`${path} must be a positive safe integer`);
  return value;
}

function requireCanonicalMoney(value, path, positive = false) {
  requirePattern(value, CANONICAL_MONEY, path, "canonical nonnegative money without exponent or insignificant zeros");
  if (positive && value === "0") throw new TypeError(`${path} must be greater than zero`);
  return value;
}

function requireTimestamp(value, path) {
  const timestamp = requireUnicodeScalarString(value, path);
  const match = UTC_MICROSECOND.exec(timestamp);
  if (!match) throw new TypeError(`${path} must be UTC with six microsecond digits`);
  const millisecondTimestamp = `${match[1]}.${match[2].slice(0, 3)}Z`;
  const parsed = new Date(millisecondTimestamp);
  if (Number.isNaN(parsed.valueOf()) || parsed.toISOString() !== millisecondTimestamp) {
    throw new TypeError(`${path} must be a valid UTC timestamp`);
  }
  return timestamp;
}

function requireNullable(value, validator, path) {
  return value === null ? null : validator(value, path);
}

function requireCode(value, path, allowed) {
  const code = requirePattern(value, CODE, path, "a canonical uppercase code");
  if (allowed && !allowed.includes(code)) throw new TypeError(`${path} is not an allowed code`);
  return code;
}

function requireHash(value, path) {
  return requirePattern(value, SHA256, path, "a lowercase SHA-256 hex string");
}

function compareIds(left, right) {
  const leftId = BigInt(left);
  const rightId = BigInt(right);
  return leftId < rightId ? -1 : leftId > rightId ? 1 : 0;
}

function hashCanonical(canonical) {
  return createHash("sha256").update(canonical, "utf8").digest("hex");
}

function canonicalActionParameter(row, index) {
  const path = `parameters[${index}]`;
  requireClosedRecord(row, ACTION_PARAMETER_FIELDS, path);
  const sourceType = requireCode(row.sourceType, `${path}.sourceType`, ["FEATURE", "CONTEXT", "LITERAL"]);
  const values = [row.stringValue, row.numberValue, row.booleanValue, row.datetimeValue, row.durationValue];
  const presentValues = values.filter((value) => value !== null).length;
  if (sourceType === "FEATURE" && (row.featureVersionId === null || row.inputFieldId !== null || presentValues !== 0)) {
    throw new TypeError(`${path} has an invalid FEATURE source shape`);
  }
  if (sourceType === "CONTEXT" && (row.featureVersionId !== null || row.inputFieldId === null || presentValues !== 0)) {
    throw new TypeError(`${path} has an invalid CONTEXT source shape`);
  }
  if (sourceType === "LITERAL" && (row.featureVersionId !== null || row.inputFieldId !== null || presentValues !== 1)) {
    throw new TypeError(`${path} has an invalid LITERAL source shape`);
  }
  if (row.stringValue !== null) requireUnicodeScalarString(row.stringValue, `${path}.stringValue`, true);
  if (row.numberValue !== null) requireCanonicalMoney(row.numberValue, `${path}.numberValue`);
  if (row.booleanValue !== null && typeof row.booleanValue !== "boolean") throw new TypeError(`${path}.booleanValue must be boolean or null`);
  if (row.datetimeValue !== null) requireTimestamp(row.datetimeValue, `${path}.datetimeValue`);
  if (row.durationValue !== null) requireCanonicalNonnegativeBigint(row.durationValue, `${path}.durationValue`);
  return {
    booleanValue: row.booleanValue,
    datetimeValue: row.datetimeValue,
    durationValue: row.durationValue,
    featureVersionId: requireNullable(row.featureVersionId, requireCanonicalId, `${path}.featureVersionId`),
    inputFieldId: requireNullable(row.inputFieldId, requireCanonicalId, `${path}.inputFieldId`),
    numberValue: row.numberValue,
    parameterDefinitionId: requireCanonicalId(row.parameterDefinitionId, `${path}.parameterDefinitionId`),
    ruleActionId: requireCanonicalId(row.ruleActionId, `${path}.ruleActionId`),
    sourceType,
    stringValue: row.stringValue,
    valueSequence: requirePositiveInteger(row.valueSequence, `${path}.valueSequence`),
  };
}

export function canonicalizeSettlementActionParameterIdentity(parameters) {
  requireDenseArray(parameters, "parameters");
  const actionParameters = parameters.map(canonicalActionParameter).sort((left, right) => (
    compareIds(left.parameterDefinitionId, right.parameterDefinitionId)
    || left.valueSequence - right.valueSequence
    || compareIds(left.ruleActionId, right.ruleActionId)
  ));
  const identities = actionParameters.map((parameter) => (
    `${parameter.ruleActionId}:${parameter.parameterDefinitionId}:${parameter.valueSequence}`
  ));
  if (new Set(identities).size !== identities.length) throw new TypeError("duplicate Action Parameter identity");
  return JSON.stringify({ actionParameters });
}

export function buildSettlementControlInstanceKey(input) {
  requireClosedRecord(input, ["businessInstanceKey", "ruleActionId"], "input");
  const ruleActionId = requireCanonicalId(input.ruleActionId, "input.ruleActionId");
  const businessInstanceKey = requirePattern(
    input.businessInstanceKey,
    BUSINESS_INSTANCE_KEY,
    "input.businessInstanceKey",
    "a canonical uppercase business instance key",
  );
  return `RULE_ACTION:${ruleActionId}:${businessInstanceKey}`;
}

function canonicalScopeProjection(scope, path = "scope") {
  requireClosedRecord(scope, SCOPE_FIELDS, path);
  return {
    bankCountry: requireNullable(scope.bankCountry, (value, fieldPath) => requirePattern(value, COUNTRY, fieldPath, "an ISO uppercase country code"), `${path}.bankCountry`),
    currency: requireNullable(scope.currency, (value, fieldPath) => requirePattern(value, CURRENCY, fieldPath, "an ISO uppercase currency code"), `${path}.currency`),
    operatingEntityId: requireNullable(scope.operatingEntityId, requireCanonicalId, `${path}.operatingEntityId`),
  };
}

export function canonicalizeSettlementControlScope(scope) {
  return JSON.stringify({
    schema: SETTLEMENT_CONTROL_SCOPE_SCHEMA,
    scope: canonicalScopeProjection(scope),
  });
}

export function hashSettlementControlScope(scope) {
  return hashCanonical(canonicalizeSettlementControlScope(scope));
}

function canonicalControlContent(control, scopes) {
  requireClosedRecord(control, CONTENT_FIELDS, "control");
  requireDenseArray(scopes, "scopes", true);
  const ruleActionId = requireCanonicalId(control.ruleActionId, "control.ruleActionId");
  const instancePrefix = `RULE_ACTION:${ruleActionId}:`;
  const controlInstanceKey = requireUnicodeScalarString(control.controlInstanceKey, "control.controlInstanceKey");
  if (!controlInstanceKey.startsWith(instancePrefix)) throw new TypeError("control.controlInstanceKey must match ruleActionId");
  requirePattern(controlInstanceKey.slice(instancePrefix.length), BUSINESS_INSTANCE_KEY, "control.businessInstanceKey", "a canonical uppercase business instance key");
  const controlType = requireCode(control.controlType, "control.controlType", ["SETTLEMENT_BLOCK", "MIN_SECURITY_DEPOSIT"]);
  const roleType = requireCode(control.roleType, "control.roleType", ["CLIENT", "LOCAL_PARTNER"]);
  const effectiveFrom = requireTimestamp(control.effectiveFrom, "control.effectiveFrom");
  const effectiveTo = requireNullable(control.effectiveTo, requireTimestamp, "control.effectiveTo");
  if (effectiveTo !== null && effectiveFrom >= effectiveTo) throw new TypeError("control effective range must be increasing");
  const requiredAmount = requireNullable(control.requiredAmount, (value, path) => requireCanonicalMoney(value, path, true), "control.requiredAmount");
  const requiredCurrency = requireNullable(control.requiredCurrency, (value, path) => requirePattern(value, CURRENCY, path, "an ISO uppercase currency code"), "control.requiredCurrency");
  const releaseConditionCode = requireNullable(control.releaseConditionCode, requireCode, "control.releaseConditionCode");
  if (controlType === "SETTLEMENT_BLOCK" && [requiredAmount, requiredCurrency, releaseConditionCode].some((value) => value !== null)) {
    throw new TypeError("SETTLEMENT_BLOCK money and release condition must be null");
  }
  if (controlType === "MIN_SECURITY_DEPOSIT"
    && (roleType !== "CLIENT" || requiredAmount === null || requiredCurrency === null || releaseConditionCode === null)) {
    throw new TypeError("MIN_SECURITY_DEPOSIT requires a Client amount, currency, and release condition");
  }
  const canonicalScopes = scopes.map((scope, index) => canonicalScopeProjection(scope, `scopes[${index}]`));
  if (controlType === "MIN_SECURITY_DEPOSIT"
    && canonicalScopes.some((scope) => scope.currency === null || scope.currency !== requiredCurrency)) {
    throw new TypeError("MIN_SECURITY_DEPOSIT Scope currency must equal requiredCurrency");
  }
  const scopeHashes = canonicalScopes.map((scope) => hashSettlementControlScope(scope)).sort();
  if (new Set(scopeHashes).size !== scopeHashes.length) throw new TypeError("scopes must be unique");
  return {
    control: {
      controlType,
      effectiveFrom,
      effectiveTo,
      reasonCode: requireCode(control.reasonCode, "control.reasonCode"),
      releaseConditionCode,
      requiredAmount,
      requiredCurrency,
    },
    origin: {
      actionVersionId: requireCanonicalId(control.actionVersionId, "control.actionVersionId"),
      controlInstanceKey,
      ruleActionId,
    },
    owner: {
      roleId: requireCanonicalId(control.roleId, "control.roleId"),
      roleType,
    },
    schema: SETTLEMENT_CONTROL_CONTENT_SCHEMA,
    scopeHashes,
  };
}

export function canonicalizeSettlementControlContent(control, scopes) {
  return JSON.stringify(canonicalControlContent(control, scopes));
}

export function hashSettlementControlContent(control, scopes) {
  return hashCanonical(canonicalizeSettlementControlContent(control, scopes));
}

function canonicalScopeReference(scope, index, path = "scopes") {
  const rowPath = `${path}[${index}]`;
  requireClosedRecord(scope, SCOPE_REF_FIELDS, rowPath);
  return {
    scopeHash: requireHash(scope.scopeHash, `${rowPath}.scopeHash`),
    scopeId: requireCanonicalId(scope.scopeId, `${rowPath}.scopeId`),
  };
}

export function canonicalizeSettlementControlScopeSet(input) {
  requireClosedRecord(input, SCOPE_SET_FIELDS, "input");
  requireDenseArray(input.scopes, "input.scopes", true);
  const scopes = input.scopes.map((scope, index) => canonicalScopeReference(scope, index))
    .sort((left, right) => compareIds(left.scopeId, right.scopeId));
  if (new Set(scopes.map((scope) => scope.scopeId)).size !== scopes.length) throw new TypeError("input.scopes must have unique scopeId values");
  return JSON.stringify({
    controlId: requireCanonicalId(input.controlId, "input.controlId"),
    controlVersion: requirePositiveInteger(input.controlVersion, "input.controlVersion"),
    schema: SETTLEMENT_CONTROL_SCOPE_SET_SCHEMA,
    scopes,
  });
}

export function hashSettlementControlScopeSet(input) {
  return hashCanonical(canonicalizeSettlementControlScopeSet(input));
}

function canonicalMatchedControl(control, index) {
  const path = `result.controls[${index}]`;
  requireClosedRecord(control, MATCHED_CONTROL_FIELDS, path);
  requireDenseArray(control.scopes, `${path}.scopes`, true);
  const scopes = control.scopes.map((scope, scopeIndex) => canonicalScopeReference(scope, scopeIndex, `${path}.scopes`))
    .sort((left, right) => compareIds(left.scopeId, right.scopeId));
  if (new Set(scopes.map((scope) => scope.scopeId)).size !== scopes.length) {
    throw new TypeError(`${path}.scopes must have unique scopeId values`);
  }
  const scopeCount = requirePositiveInteger(control.scopeCount, `${path}.scopeCount`);
  if (scopeCount !== scopes.length) throw new TypeError(`${path}.scopeCount must equal scopes.length`);
  const controlType = requireCode(control.controlType, `${path}.controlType`, ["SETTLEMENT_BLOCK", "MIN_SECURITY_DEPOSIT"]);
  const requiredAmount = requireNullable(control.requiredAmount, (value, fieldPath) => requireCanonicalMoney(value, fieldPath, true), `${path}.requiredAmount`);
  const requiredCurrency = requireNullable(control.requiredCurrency, (value, fieldPath) => requirePattern(value, CURRENCY, fieldPath, "an ISO uppercase currency code"), `${path}.requiredCurrency`);
  if (controlType === "SETTLEMENT_BLOCK" && (requiredAmount !== null || requiredCurrency !== null)) {
    throw new TypeError(`${path} SETTLEMENT_BLOCK amount and currency must be null`);
  }
  if (controlType === "MIN_SECURITY_DEPOSIT" && (requiredAmount === null || requiredCurrency === null)) {
    throw new TypeError(`${path} MIN_SECURITY_DEPOSIT requires amount and currency`);
  }
  const effectiveFrom = requireTimestamp(control.effectiveFrom, `${path}.effectiveFrom`);
  const effectiveTo = requireNullable(control.effectiveTo, requireTimestamp, `${path}.effectiveTo`);
  if (effectiveTo !== null && effectiveFrom >= effectiveTo) throw new TypeError(`${path} effective range must be increasing`);
  const controlId = requireCanonicalId(control.controlId, `${path}.controlId`);
  const controlVersion = requirePositiveInteger(control.controlVersion, `${path}.controlVersion`);
  const scopeSetHash = requireHash(control.scopeSetHash, `${path}.scopeSetHash`);
  const expectedScopeSetHash = hashSettlementControlScopeSet({ controlId, controlVersion, scopes });
  if (scopeSetHash !== expectedScopeSetHash) {
    throw new TypeError(`${path}.scopeSetHash must match canonical Scope Set`);
  }
  return {
    controlId,
    controlType,
    controlVersion,
    effectiveFrom,
    effectiveTo,
    reasonCode: requireCode(control.reasonCode, `${path}.reasonCode`),
    requiredAmount,
    requiredCurrency,
    scopeCount,
    scopeSetHash,
    scopes,
  };
}

export function canonicalizeSettlementControlSet(result) {
  requireClosedRecord(result, CONTROL_SET_FIELDS, "result");
  requireDenseArray(result.controls, "result.controls");
  const controls = result.controls.map(canonicalMatchedControl)
    .sort((left, right) => compareIds(left.controlId, right.controlId));
  if (new Set(controls.map((control) => control.controlId)).size !== controls.length) {
    throw new TypeError("result.controls must have unique controlId values");
  }
  const requiredAmount = requireNullable(result.requiredAmount, requireCanonicalMoney, "result.requiredAmount");
  const requiredCurrency = requireNullable(result.requiredCurrency, (value, path) => requirePattern(value, CURRENCY, path, "an ISO uppercase currency code"), "result.requiredCurrency");
  if ((requiredAmount === null) !== (requiredCurrency === null)) throw new TypeError("result amount and currency must be both null or both present");
  return JSON.stringify({
    controls,
    decision: requireCode(result.decision, "result.decision", ["ALLOW", "RESTRICT"]),
    requiredAmount,
    requiredCurrency,
    schema: SETTLEMENT_CONTROL_SET_SCHEMA,
  });
}

export function hashSettlementControlSet(result) {
  return hashCanonical(canonicalizeSettlementControlSet(result));
}
