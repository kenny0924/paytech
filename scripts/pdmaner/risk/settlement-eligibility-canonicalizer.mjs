import { createHash } from "node:crypto";

const MAX_SIGNED_BIGINT = 9223372036854775807n;
const MAX_SIGNED_INT = 2147483647;
const POSITIVE_DECIMAL = /^[1-9][0-9]*$/u;
const CANONICAL_MONEY = /^(?:0|[1-9][0-9]{0,15})(?:\.[0-9]{0,7}[1-9])?$/u;
const CURRENCY = /^[A-Z]{3}$/u;
const COUNTRY = /^[A-Z]{2}$/u;
const CODE = /^[A-Z][A-Z0-9_]*$/u;
const INPUT_FIELDS = Object.freeze([
  "bankCountry", "checkpointType", "currency", "grossAmount", "operatingEntityId",
  "roleId", "roleType", "sourceFactId", "sourceFactType", "sourceFactVersion",
]);

const SETTLEMENT_ELIGIBILITY_INPUT_SCHEMA = "CODE-MOR:RISK:SETTLEMENT_ELIGIBILITY_INPUT:1";

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

function requireUnicodeScalarString(value, path) {
  if (typeof value !== "string" || value.length === 0) throw new TypeError(`${path} must be a non-empty string`);
  for (let index = 0; index < value.length; index += 1) {
    const codeUnit = value.charCodeAt(index);
    if (codeUnit >= 0xd800 && codeUnit <= 0xdbff) {
      const next = value.charCodeAt(index + 1);
      if (!(next >= 0xdc00 && next <= 0xdfff)) throw new TypeError(`${path} must contain Unicode scalar values only`);
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

function requireCode(value, path, allowed) {
  const code = requirePattern(value, CODE, path, "a canonical uppercase code");
  if (!allowed.includes(code)) throw new TypeError(`${path} is not an allowed code`);
  return code;
}

function requirePositiveInteger(value, path) {
  if (!Number.isInteger(value) || value < 1 || value > MAX_SIGNED_INT) {
    throw new TypeError(`${path} must be a MySQL signed INT in 1..2147483647`);
  }
  return value;
}

function requirePositiveCanonicalMoney(value, path) {
  const money = requirePattern(value, CANONICAL_MONEY, path, "canonical nonnegative money without exponent or insignificant zeros");
  if (money === "0") throw new TypeError(`${path} must be greater than zero`);
  return money;
}

export function canonicalizeSettlementEligibilityInput(input) {
  requireClosedRecord(input, INPUT_FIELDS, "input");
  const checkpointType = requireCode(input.checkpointType, "input.checkpointType", [
    "PAYOUT_CREATION", "PAYOUT_FINAL_APPROVAL",
    "PAYOUT_EXECUTION_CREATION", "PAYOUT_EXTERNAL_SUBMISSION",
  ]);
  const sourceFactType = requireCode(input.sourceFactType, "input.sourceFactType", [
    "PAYOUT", "PAYOUT_EXECUTION",
  ]);
  const allowedSourceFacts = {
    PAYOUT_CREATION: ["PAYOUT"],
    PAYOUT_FINAL_APPROVAL: ["PAYOUT"],
    PAYOUT_EXECUTION_CREATION: ["PAYOUT_EXECUTION"],
    PAYOUT_EXTERNAL_SUBMISSION: ["PAYOUT_EXECUTION"],
  };
  if (!allowedSourceFacts[checkpointType].includes(sourceFactType)) {
    throw new TypeError("input sourceFactType does not match checkpointType");
  }
  const sourceFactVersion = requirePositiveInteger(input.sourceFactVersion, "input.sourceFactVersion");
  if (["PAYOUT_CREATION", "PAYOUT_EXECUTION_CREATION"].includes(checkpointType)
    && sourceFactVersion !== 1) {
    throw new TypeError("input creation checkpoint must use sourceFactVersion 1");
  }
  return JSON.stringify({
    bankCountry: requirePattern(input.bankCountry, COUNTRY, "input.bankCountry", "an ISO uppercase country code"),
    checkpointType,
    currency: requirePattern(input.currency, CURRENCY, "input.currency", "an ISO uppercase currency code"),
    grossAmount: requirePositiveCanonicalMoney(input.grossAmount, "input.grossAmount"),
    operatingEntityId: requireCanonicalId(input.operatingEntityId, "input.operatingEntityId"),
    roleId: requireCanonicalId(input.roleId, "input.roleId"),
    roleType: requireCode(input.roleType, "input.roleType", ["CLIENT", "LOCAL_PARTNER"]),
    schema: SETTLEMENT_ELIGIBILITY_INPUT_SCHEMA,
    sourceFactId: requireCanonicalId(input.sourceFactId, "input.sourceFactId"),
    sourceFactType,
    sourceFactVersion,
  });
}

export function hashSettlementEligibilityInput(input) {
  return createHash("sha256")
    .update(canonicalizeSettlementEligibilityInput(input), "utf8")
    .digest("hex");
}
