import { createHash } from "node:crypto";

export const DISPUTE_EVIDENCE_PACKAGE_SCHEMA = "CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1";

const MAX_SIGNED_BIGINT = 9223372036854775807n;
const MAX_SIGNED_INTEGER = 2147483647;
const POSITIVE_DECIMAL = /^[1-9][0-9]*$/u;
const EVIDENCE_TYPE = /^[A-Z][A-Z0-9_]{0,63}$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const PACKAGE_FIELDS = Object.freeze(["disputeCaseId", "evidence", "versionNo"]);
const EVIDENCE_FIELDS = Object.freeze(["contentHash", "evidenceType", "fileId"]);

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

function requireDenseEvidence(value) {
  if (!Array.isArray(value) || value.length === 0) {
    throw new TypeError("package.evidence must be a non-empty dense array");
  }
  for (let index = 0; index < value.length; index += 1) {
    if (!Object.hasOwn(value, index)) {
      throw new TypeError("package.evidence must be a non-empty dense array");
    }
  }
  return value;
}

function requireCanonicalId(value, path) {
  if (typeof value !== "string" || !POSITIVE_DECIMAL.test(value)) {
    throw new TypeError(`${path} must be a canonical positive BIGINT decimal string`);
  }
  if (BigInt(value) > MAX_SIGNED_BIGINT) throw new TypeError(`${path} exceeds signed BIGINT`);
  return value;
}

function requirePositiveInteger(value) {
  if (!Number.isInteger(value) || value <= 0 || value > MAX_SIGNED_INTEGER) {
    throw new TypeError("package.versionNo must be a positive signed INTEGER");
  }
  return value;
}

function canonicalEvidence(row, index) {
  const path = `package.evidence[${index}]`;
  requireClosedRecord(row, EVIDENCE_FIELDS, path);
  if (typeof row.contentHash !== "string" || !SHA256.test(row.contentHash)) {
    throw new TypeError(`${path}.contentHash must be a lowercase SHA-256 hex string`);
  }
  if (typeof row.evidenceType !== "string" || !EVIDENCE_TYPE.test(row.evidenceType)) {
    throw new TypeError(`${path}.evidenceType must be a canonical uppercase code`);
  }
  return {
    contentHash: row.contentHash,
    evidenceType: row.evidenceType,
    fileId: requireCanonicalId(row.fileId, `${path}.fileId`),
  };
}

function compareEvidence(left, right) {
  if (left.evidenceType < right.evidenceType) return -1;
  if (left.evidenceType > right.evidenceType) return 1;
  const leftId = BigInt(left.fileId);
  const rightId = BigInt(right.fileId);
  return leftId < rightId ? -1 : leftId > rightId ? 1 : 0;
}

export function canonicalizeDisputeEvidencePackage(input) {
  requireClosedRecord(input, PACKAGE_FIELDS, "package");
  const evidence = requireDenseEvidence(input.evidence)
    .map(canonicalEvidence)
    .sort(compareEvidence);
  const identities = evidence.map((row) => `${row.evidenceType}:${row.fileId}`);
  if (new Set(identities).size !== identities.length) {
    throw new TypeError("package.evidence contains a duplicate evidenceType + fileId identity");
  }
  return JSON.stringify({
    disputeCaseId: requireCanonicalId(input.disputeCaseId, "package.disputeCaseId"),
    evidence,
    schema: DISPUTE_EVIDENCE_PACKAGE_SCHEMA,
    versionNo: requirePositiveInteger(input.versionNo),
  });
}

export function hashDisputeEvidencePackage(input) {
  return createHash("sha256")
    .update(canonicalizeDisputeEvidencePackage(input), "utf8")
    .digest("hex");
}
