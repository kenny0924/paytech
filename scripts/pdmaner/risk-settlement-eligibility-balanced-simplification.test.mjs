import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { TABLES as DECISION_ENGINE_TABLES } from "./risk/manifests/02-decision-engine.mjs";
import { TABLES as EVALUATION_TABLES } from "./risk/manifests/03-evaluation.mjs";
import { TABLES as SETTLEMENT_TABLES } from "./risk/manifests/06-settlement.mjs";
import { TABLES, TABLE_GROUPS } from "./risk/manifest.mjs";
import * as MODEL_POLICY from "./risk/model-policy.mjs";
import { SETTLEMENT_ELIGIBILITY_TARGET } from "./risk-compliance-test-oracle/schema.oracle.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const modelPath = path.join(workspaceRoot, "risk-compliance-service/docs/database/risk-compliance.pdma.json");

const SCOPE_HASH_210001_USD_AE = "835f9cfa331ac2307cfe2fdc1122c98081af03d32aaf4ad4dc9cd3fd85b630f0";
const SCOPE_HASH_210002_USD_ALL_COUNTRIES = "afe580f03a670414c151ae43b1b42fa1323bcf057bb00c12ebe474ac03ed3ef0";
const CONTROL_CONTENT_HASH = "e70fa02c3b8c74c3723561da68555190fbd086fe7da9b35ad11fec0b87ddf70f";
const SCOPE_SET_HASH = "eb061c5b5272c13af4674b12a6dd6f79ff5456bbf348b61224930c9127101e7e";
const SCOPE_SET_HASH_CONTROL_9_V1_SCOPE_2 = "d48be01484bb38b1200661ae7d96279f97a422e57b4e5798a4d169f75dab0677";
const SCOPE_SET_HASH_CONTROL_9_V1_TWO_SCOPES = "457e6e8951f1e14653b8de66ddf48d57041d6c2d6069ba3204c0b56f05393cd2";
const SCOPE_SET_HASH_CONTROL_10_V2_SCOPE_10 = "4b68eaf8b2d5c18f048eace2d99cc8df23b419667a6c82717b10b90c28d14b64";
const ELIGIBILITY_INPUT_HASH = "3a19652d8d19f3a24bbe7256f407481b426c27b89af148bb0b061085e362376f";
const CONTROL_SET_HASH = "c2397e6f735bb63b6c125f6a61c746148f94976da7e96392daf00c219efa20dc";

async function loadSettlementCanonicalizers() {
  const [control, eligibility] = await Promise.all([
    import("./risk/settlement-control-canonicalizer.mjs"),
    import("./risk/settlement-eligibility-canonicalizer.mjs"),
  ]);
  return { ...control, ...eligibility };
}

function deepFreeze(value) {
  if (value === null || typeof value !== "object" || Object.isFrozen(value)) return value;
  Object.freeze(value);
  for (const child of Object.values(value)) deepFreeze(child);
  return value;
}

function literalScopeTupleParameters() {
  return [
    {
      ruleActionId: "250001", parameterDefinitionId: "241101", valueSequence: 1,
      sourceType: "LITERAL", featureVersionId: null, inputFieldId: null,
      stringValue: "210001", numberValue: null, booleanValue: null, datetimeValue: null, durationValue: null,
    },
    {
      ruleActionId: "250001", parameterDefinitionId: "241102", valueSequence: 1,
      sourceType: "LITERAL", featureVersionId: null, inputFieldId: null,
      stringValue: "USD", numberValue: null, booleanValue: null, datetimeValue: null, durationValue: null,
    },
    {
      ruleActionId: "250001", parameterDefinitionId: "241103", valueSequence: 1,
      sourceType: "LITERAL", featureVersionId: null, inputFieldId: null,
      stringValue: "AE", numberValue: null, booleanValue: null, datetimeValue: null, durationValue: null,
    },
    {
      ruleActionId: "250001", parameterDefinitionId: "241101", valueSequence: 2,
      sourceType: "LITERAL", featureVersionId: null, inputFieldId: null,
      stringValue: "210002", numberValue: null, booleanValue: null, datetimeValue: null, durationValue: null,
    },
    {
      ruleActionId: "250001", parameterDefinitionId: "241102", valueSequence: 2,
      sourceType: "LITERAL", featureVersionId: null, inputFieldId: null,
      stringValue: "USD", numberValue: null, booleanValue: null, datetimeValue: null, durationValue: null,
    },
    {
      ruleActionId: "250001", parameterDefinitionId: "241103", valueSequence: 2,
      sourceType: "LITERAL", featureVersionId: null, inputFieldId: null,
      stringValue: "*", numberValue: null, booleanValue: null, datetimeValue: null, durationValue: null,
    },
  ];
}

function tableByKey(tables, key) {
  return tables.find((table) => table.defKey === key);
}

function fieldNames(table) {
  return table.fields.map((field) => field.defKey);
}

function assertFields(table, expected) {
  assert.ok(table, `缺少目标表 ${expected[0]}`);
  const actual = new Set(fieldNames(table));
  for (const field of expected) assert.ok(actual.has(field), `${table.defKey} 缺少字段 ${field}`);
}

function assertAbsentFields(table, forbidden) {
  assert.ok(table, "缺少 Settlement Control 表");
  const actual = new Set(fieldNames(table));
  for (const field of forbidden) assert.ok(!actual.has(field), `${table.defKey} 仍保留字段 ${field}`);
}

function assertUniqueIndex(table, expected) {
  assert.ok(table, `缺少索引所属表 ${expected.join(",")}`);
  assert.ok(
    table.indexes.some((index) => index.unique && JSON.stringify(index.fields) === JSON.stringify(expected)),
    `${table.defKey} 缺少唯一键 (${expected.join(",")})`,
  );
}

function assertIndex(table, name, unique, fields) {
  assert.ok(table, `缺少索引所属表 ${name}`);
  assert.deepEqual(
    table.indexes.find((index) => index.defKey === name),
    { defKey: name, unique, fields },
    `${table.defKey} 的 ${name} 定义不准确`,
  );
}

function assertNoUniqueIndex(table, forbidden) {
  assert.ok(table, `缺少索引所属表 ${forbidden.join(",")}`);
  assert.equal(
    table.indexes.some((index) => index.unique && JSON.stringify(index.fields) === JSON.stringify(forbidden)),
    false,
    `${table.defKey} 仍保留唯一键 (${forbidden.join(",")})`,
  );
}

function assertDeepFrozen(value, label) {
  if (value === null || typeof value !== "object") return;
  assert.equal(Object.isFrozen(value), true, `${label} 必须逐层冻结`);
  for (const [key, child] of Object.entries(value)) assertDeepFrozen(child, `${label}.${key}`);
}

test("Action parameter identity catches dropping valueSequence or relying on physical row order", async () => {
  const { canonicalizeSettlementActionParameterIdentity } = await loadSettlementCanonicalizers();
  const parameters = literalScopeTupleParameters();
  const original = structuredClone(parameters);
  deepFreeze(parameters);
  const expected = "{\"actionParameters\":[{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241101\",\"ruleActionId\":\"250001\",\"sourceType\":\"LITERAL\",\"stringValue\":\"210001\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241101\",\"ruleActionId\":\"250001\",\"sourceType\":\"LITERAL\",\"stringValue\":\"210002\",\"valueSequence\":2},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241102\",\"ruleActionId\":\"250001\",\"sourceType\":\"LITERAL\",\"stringValue\":\"USD\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241102\",\"ruleActionId\":\"250001\",\"sourceType\":\"LITERAL\",\"stringValue\":\"USD\",\"valueSequence\":2},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241103\",\"ruleActionId\":\"250001\",\"sourceType\":\"LITERAL\",\"stringValue\":\"AE\",\"valueSequence\":1},{\"booleanValue\":null,\"datetimeValue\":null,\"durationValue\":null,\"featureVersionId\":null,\"inputFieldId\":null,\"numberValue\":null,\"parameterDefinitionId\":\"241103\",\"ruleActionId\":\"250001\",\"sourceType\":\"LITERAL\",\"stringValue\":\"*\",\"valueSequence\":2}]}";

  assert.equal(canonicalizeSettlementActionParameterIdentity(parameters), expected);
  assert.equal(canonicalizeSettlementActionParameterIdentity(parameters.toReversed()), expected);
  assert.deepEqual(parameters, original, "canonicalization must not mutate Action Parameter rows");

  const swappedTuples = literalScopeTupleParameters().map((parameter) => ({
    ...parameter,
    valueSequence: parameter.valueSequence === 1 ? 2 : 1,
  }));
  assert.notEqual(
    canonicalizeSettlementActionParameterIdentity(swappedTuples),
    expected,
    "swapping the two sequence-aligned literal Scope tuples must change Release parameter identity",
  );
});

test("Scope canonicalization catches omitted nulls and non-JCS field order", async () => {
  const {
    buildSettlementControlInstanceKey,
    canonicalizeSettlementControlScope,
    hashSettlementControlScope,
  } = await loadSettlementCanonicalizers();

  assert.equal(
    buildSettlementControlInstanceKey({ ruleActionId: "250001", businessInstanceKey: "DISPUTE_CASE:610101" }),
    "RULE_ACTION:250001:DISPUTE_CASE:610101",
  );
  assert.equal(
    canonicalizeSettlementControlScope({ operatingEntityId: "210001", currency: "USD", bankCountry: "AE" }),
    "{\"schema\":\"CODE-MOR:RISK:SETTLEMENT_CONTROL_SCOPE:1\",\"scope\":{\"bankCountry\":\"AE\",\"currency\":\"USD\",\"operatingEntityId\":\"210001\"}}",
  );
  assert.equal(
    hashSettlementControlScope({ operatingEntityId: "210001", currency: "USD", bankCountry: "AE" }),
    SCOPE_HASH_210001_USD_AE,
  );
  assert.equal(
    canonicalizeSettlementControlScope({ bankCountry: null, operatingEntityId: null, currency: null }),
    "{\"schema\":\"CODE-MOR:RISK:SETTLEMENT_CONTROL_SCOPE:1\",\"scope\":{\"bankCountry\":null,\"currency\":null,\"operatingEntityId\":null}}",
  );
});

test("Control content catches unsorted scope hashes and mutation of caller-owned scopes", async () => {
  const { canonicalizeSettlementControlContent, hashSettlementControlContent } = await loadSettlementCanonicalizers();
  const control = {
    controlType: "MIN_SECURITY_DEPOSIT",
    effectiveFrom: "2026-08-28T10:15:30.123456Z",
    effectiveTo: null,
    reasonCode: "DISPUTE_EXPOSURE",
    releaseConditionCode: "MANUAL_REVIEW",
    requiredAmount: "20000",
    requiredCurrency: "USD",
    actionVersionId: "240001",
    controlInstanceKey: "RULE_ACTION:250001:DISPUTE_CASE:610101",
    ruleActionId: "250001",
    roleId: "200001",
    roleType: "CLIENT",
  };
  const scopes = [
    { operatingEntityId: "210002", currency: "USD", bankCountry: null },
    { operatingEntityId: "210001", currency: "USD", bankCountry: "AE" },
  ];
  const original = structuredClone(scopes);
  deepFreeze(scopes);
  const expected = "{\"control\":{\"controlType\":\"MIN_SECURITY_DEPOSIT\",\"effectiveFrom\":\"2026-08-28T10:15:30.123456Z\",\"effectiveTo\":null,\"reasonCode\":\"DISPUTE_EXPOSURE\",\"releaseConditionCode\":\"MANUAL_REVIEW\",\"requiredAmount\":\"20000\",\"requiredCurrency\":\"USD\"},\"origin\":{\"actionVersionId\":\"240001\",\"controlInstanceKey\":\"RULE_ACTION:250001:DISPUTE_CASE:610101\",\"ruleActionId\":\"250001\"},\"owner\":{\"roleId\":\"200001\",\"roleType\":\"CLIENT\"},\"schema\":\"CODE-MOR:RISK:SETTLEMENT_CONTROL_CONTENT:1\",\"scopeHashes\":[\"835f9cfa331ac2307cfe2fdc1122c98081af03d32aaf4ad4dc9cd3fd85b630f0\",\"afe580f03a670414c151ae43b1b42fa1323bcf057bb00c12ebe474ac03ed3ef0\"]}";

  assert.equal(canonicalizeSettlementControlContent(control, scopes), expected);
  assert.equal(canonicalizeSettlementControlContent({ ...control }, scopes.toReversed()), expected);
  assert.equal(hashSettlementControlContent(control, scopes), CONTROL_CONTENT_HASH);
  assert.equal(hashSettlementControlContent(control, scopes), hashSettlementControlContent({ ...control }, scopes.toReversed()));
  assert.match(hashSettlementControlContent(control, scopes), /^[0-9a-f]{64}$/u);
  assert.deepEqual(scopes, original, "canonicalization must sort a copy of Scope rows");
});

test("Scope-set and eligibility input catch lexicographic ID sorting or scalar normalization", async () => {
  const {
    canonicalizeSettlementControlScopeSet,
    canonicalizeSettlementEligibilityInput,
    hashSettlementControlScopeSet,
    hashSettlementEligibilityInput,
  } = await loadSettlementCanonicalizers();
  const scopeSet = {
    controlId: "400001",
    controlVersion: 3,
    scopes: [
      { scopeId: "10", scopeHash: SCOPE_HASH_210002_USD_ALL_COUNTRIES },
      { scopeId: "2", scopeHash: SCOPE_HASH_210001_USD_AE },
    ],
  };
  const expectedScopeSet = "{\"controlId\":\"400001\",\"controlVersion\":3,\"schema\":\"CODE-MOR:RISK:SETTLEMENT_CONTROL_SCOPE_SET:1\",\"scopes\":[{\"scopeHash\":\"835f9cfa331ac2307cfe2fdc1122c98081af03d32aaf4ad4dc9cd3fd85b630f0\",\"scopeId\":\"2\"},{\"scopeHash\":\"afe580f03a670414c151ae43b1b42fa1323bcf057bb00c12ebe474ac03ed3ef0\",\"scopeId\":\"10\"}]}";
  assert.equal(canonicalizeSettlementControlScopeSet(scopeSet), expectedScopeSet);
  assert.equal(hashSettlementControlScopeSet(scopeSet), SCOPE_SET_HASH);
  assert.match(hashSettlementControlScopeSet(scopeSet), /^[0-9a-f]{64}$/u);
  assert.equal(hashSettlementControlScopeSet(scopeSet), hashSettlementControlScopeSet({ ...scopeSet, scopes: scopeSet.scopes.toReversed() }));

  const eligibility = {
    checkpointType: "PAYOUT_CREATION",
    roleType: "CLIENT",
    roleId: "200001",
    operatingEntityId: "210001",
    bankCountry: "AE",
    grossAmount: "82000",
    currency: "USD",
    sourceFactType: "PAYOUT",
    sourceFactId: "510001",
    sourceFactVersion: 1,
  };
  const expectedEligibility = "{\"bankCountry\":\"AE\",\"checkpointType\":\"PAYOUT_CREATION\",\"currency\":\"USD\",\"grossAmount\":\"82000\",\"operatingEntityId\":\"210001\",\"roleId\":\"200001\",\"roleType\":\"CLIENT\",\"schema\":\"CODE-MOR:RISK:SETTLEMENT_ELIGIBILITY_INPUT:1\",\"sourceFactId\":\"510001\",\"sourceFactType\":\"PAYOUT\",\"sourceFactVersion\":1}";
  assert.equal(canonicalizeSettlementEligibilityInput(eligibility), expectedEligibility);
  assert.equal(hashSettlementEligibilityInput(eligibility), ELIGIBILITY_INPUT_HASH);
  assert.match(hashSettlementEligibilityInput(eligibility), /^[0-9a-f]{64}$/u);
  assert.throws(
    () => canonicalizeSettlementEligibilityInput({ ...eligibility, grossAmount: "0" }),
    /grossAmount must be greater than zero/u,
  );
  assert.throws(
    () => canonicalizeSettlementEligibilityInput({ ...eligibility, sourceFactVersion: 2 }),
    /creation checkpoint must use sourceFactVersion 1/u,
  );
  assert.throws(
    () => canonicalizeSettlementEligibilityInput({
      ...eligibility,
      checkpointType: "PAYOUT_EXECUTION_CREATION",
      sourceFactType: "PAYOUT_EXECUTION",
      sourceFactVersion: 2,
    }),
    /creation checkpoint must use sourceFactVersion 1/u,
  );
  assert.doesNotThrow(() => canonicalizeSettlementEligibilityInput({
    ...eligibility,
    checkpointType: "PAYOUT_FINAL_APPROVAL",
    sourceFactVersion: 2147483647,
  }));
  assert.throws(
    () => canonicalizeSettlementEligibilityInput({
      ...eligibility,
      checkpointType: "PAYOUT_FINAL_APPROVAL",
      sourceFactVersion: 2147483648,
    }),
    /MySQL signed INT in 1\.\.2147483647/u,
  );
  assert.doesNotThrow(() => canonicalizeSettlementEligibilityInput({
    ...eligibility,
    checkpointType: "PAYOUT_EXTERNAL_SUBMISSION",
    sourceFactType: "PAYOUT_EXECUTION",
    sourceFactVersion: 7,
  }));
});

test("Control-set canonicalization catches lexicographic Control and Scope ID ordering", async () => {
  const { canonicalizeSettlementControlSet, hashSettlementControlSet } = await loadSettlementCanonicalizers();
  const controls = [
    {
      controlId: "10", controlVersion: 2, controlType: "MIN_SECURITY_DEPOSIT", reasonCode: "DISPUTE_EXPOSURE",
      effectiveFrom: "2026-08-28T10:15:30.123456Z", effectiveTo: null,
      requiredAmount: "20000", requiredCurrency: "USD", scopeCount: 1,
      scopeSetHash: SCOPE_SET_HASH_CONTROL_10_V2_SCOPE_10,
      scopes: [{ scopeId: "10", scopeHash: SCOPE_HASH_210002_USD_ALL_COUNTRIES }],
    },
    {
      controlId: "9", controlVersion: 1, controlType: "SETTLEMENT_BLOCK", reasonCode: "KYB_EXPIRED",
      effectiveFrom: "2026-08-28T09:00:00.000001Z", effectiveTo: "2026-08-29T09:00:00.000001Z",
      requiredAmount: null, requiredCurrency: null, scopeCount: 2,
      scopeSetHash: SCOPE_SET_HASH_CONTROL_9_V1_TWO_SCOPES,
      scopes: [
        { scopeId: "10", scopeHash: SCOPE_HASH_210002_USD_ALL_COUNTRIES },
        { scopeId: "2", scopeHash: SCOPE_HASH_210001_USD_AE },
      ],
    },
  ];
  const result = { decision: "RESTRICT", requiredAmount: "20000", requiredCurrency: "USD", controls };
  const original = structuredClone(result);
  deepFreeze(result);
  const expected = "{\"controls\":[{\"controlId\":\"9\",\"controlType\":\"SETTLEMENT_BLOCK\",\"controlVersion\":1,\"effectiveFrom\":\"2026-08-28T09:00:00.000001Z\",\"effectiveTo\":\"2026-08-29T09:00:00.000001Z\",\"reasonCode\":\"KYB_EXPIRED\",\"requiredAmount\":null,\"requiredCurrency\":null,\"scopeCount\":2,\"scopeSetHash\":\"457e6e8951f1e14653b8de66ddf48d57041d6c2d6069ba3204c0b56f05393cd2\",\"scopes\":[{\"scopeHash\":\"835f9cfa331ac2307cfe2fdc1122c98081af03d32aaf4ad4dc9cd3fd85b630f0\",\"scopeId\":\"2\"},{\"scopeHash\":\"afe580f03a670414c151ae43b1b42fa1323bcf057bb00c12ebe474ac03ed3ef0\",\"scopeId\":\"10\"}]},{\"controlId\":\"10\",\"controlType\":\"MIN_SECURITY_DEPOSIT\",\"controlVersion\":2,\"effectiveFrom\":\"2026-08-28T10:15:30.123456Z\",\"effectiveTo\":null,\"reasonCode\":\"DISPUTE_EXPOSURE\",\"requiredAmount\":\"20000\",\"requiredCurrency\":\"USD\",\"scopeCount\":1,\"scopeSetHash\":\"4b68eaf8b2d5c18f048eace2d99cc8df23b419667a6c82717b10b90c28d14b64\",\"scopes\":[{\"scopeHash\":\"afe580f03a670414c151ae43b1b42fa1323bcf057bb00c12ebe474ac03ed3ef0\",\"scopeId\":\"10\"}]}],\"decision\":\"RESTRICT\",\"requiredAmount\":\"20000\",\"requiredCurrency\":\"USD\",\"schema\":\"CODE-MOR:RISK:SETTLEMENT_CONTROL_SET:1\"}";

  assert.equal(canonicalizeSettlementControlSet(result), expected);
  assert.equal(hashSettlementControlSet(result), CONTROL_SET_HASH);
  assert.match(hashSettlementControlSet(result), /^[0-9a-f]{64}$/u);
  assert.equal(hashSettlementControlSet(result), hashSettlementControlSet({ ...result, controls: controls.toReversed() }));
  assert.deepEqual(result, original, "canonicalization must not reorder nested Control or Scope arrays");

  const mismatchedScopeSetHash = structuredClone(result);
  mismatchedScopeSetHash.controls[0].scopeSetHash = "0".repeat(64);
  assert.throws(
    () => canonicalizeSettlementControlSet(mismatchedScopeSetHash),
    /scopeSetHash must match canonical Scope Set/u,
  );
});

test("Closed Settlement schemas catch unknown fields and noncanonical scalar encodings", async () => {
  const canonicalizers = await loadSettlementCanonicalizers();
  const validScope = { operatingEntityId: "210001", currency: "USD", bankCountry: "AE" };
  const validEligibility = {
    checkpointType: "PAYOUT_CREATION", roleType: "CLIENT", roleId: "200001",
    operatingEntityId: "210001", bankCountry: "AE", grossAmount: "82000", currency: "USD",
    sourceFactType: "PAYOUT", sourceFactId: "510001", sourceFactVersion: 1,
  };
  const invalidCases = [
    ["unknown scope field", () => canonicalizers.canonicalizeSettlementControlScope({ ...validScope, country: "AE" })],
    ["wrong scalar type", () => canonicalizers.canonicalizeSettlementControlScope({ ...validScope, operatingEntityId: 210001 })],
    ["zero-padded BIGINT", () => canonicalizers.canonicalizeSettlementControlScope({ ...validScope, operatingEntityId: "0210001" })],
    ["noncanonical money exponent", () => canonicalizers.canonicalizeSettlementEligibilityInput({ ...validEligibility, grossAmount: "8.2e4" })],
    ["noncanonical money trailing zero", () => canonicalizers.canonicalizeSettlementEligibilityInput({ ...validEligibility, grossAmount: "82000.00" })],
    ["timestamp without six microseconds", () => canonicalizers.canonicalizeSettlementControlSet({
      decision: "ALLOW", requiredAmount: "0", requiredCurrency: "USD",
      controls: [{
        controlId: "9", controlVersion: 1, controlType: "SETTLEMENT_BLOCK", reasonCode: "KYB_EXPIRED",
        effectiveFrom: "2026-08-28T09:00:00Z", effectiveTo: null,
        requiredAmount: null, requiredCurrency: null, scopeCount: 1,
        scopeSetHash: SCOPE_SET_HASH_CONTROL_9_V1_SCOPE_2,
        scopes: [{ scopeId: "2", scopeHash: SCOPE_HASH_210001_USD_AE }],
      }],
    })],
    ["uppercase SHA-256", () => canonicalizers.canonicalizeSettlementControlScopeSet({
      controlId: "400001", controlVersion: 1,
      scopes: [{ scopeId: "2", scopeHash: SCOPE_HASH_210001_USD_AE.toUpperCase() }],
    })],
    ["lone Unicode surrogate", () => canonicalizers.buildSettlementControlInstanceKey({
      ruleActionId: "250001", businessInstanceKey: `DISPUTE_${String.fromCharCode(0xd800)}`,
    })],
  ];

  for (const [mutation, invoke] of invalidCases) {
    assert.throws(invoke, undefined, `must reject mutation: ${mutation}`);
  }
});

test("Closed Settlement schemas catch missing fields and nonpositive ID spellings", async () => {
  const canonicalizers = await loadSettlementCanonicalizers();
  const parameters = literalScopeTupleParameters();
  const missingParameterField = { ...parameters[0] };
  delete missingParameterField.booleanValue;
  const missingEligibilityField = {
    checkpointType: "PAYOUT_CREATION", roleType: "CLIENT", roleId: "200001",
    operatingEntityId: "210001", bankCountry: "AE", grossAmount: "82000", currency: "USD",
    sourceFactType: "PAYOUT", sourceFactId: "510001",
  };

  assert.throws(
    () => canonicalizers.canonicalizeSettlementActionParameterIdentity([missingParameterField]),
    /must contain exactly/u,
  );
  assert.throws(
    () => canonicalizers.canonicalizeSettlementControlScope({ operatingEntityId: "210001", currency: "USD" }),
    /must contain exactly/u,
  );
  assert.throws(
    () => canonicalizers.canonicalizeSettlementEligibilityInput(missingEligibilityField),
    /must contain exactly/u,
  );
  for (const invalidId of ["0", "-0", "-1"]) {
    assert.throws(
      () => canonicalizers.buildSettlementControlInstanceKey({ ruleActionId: invalidId, businessInstanceKey: "DEFAULT" }),
      /canonical positive BIGINT/u,
      `must reject nonpositive ID ${invalidId}`,
    );
  }
});

test("Closed Settlement schemas reject sparse arrays before projection", async () => {
  const canonicalizers = await loadSettlementCanonicalizers();
  const parameters = literalScopeTupleParameters();
  delete parameters[1];
  const scopes = [
    { operatingEntityId: "210001", currency: "USD", bankCountry: "AE" },
    { operatingEntityId: "210002", currency: "USD", bankCountry: null },
  ];
  const sparseScopes = [...scopes];
  delete sparseScopes[0];
  const scopeRefs = [
    { scopeId: "2", scopeHash: SCOPE_HASH_210001_USD_AE },
    { scopeId: "10", scopeHash: SCOPE_HASH_210002_USD_ALL_COUNTRIES },
  ];
  const sparseScopeRefs = [...scopeRefs];
  delete sparseScopeRefs[0];
  const control = {
    controlType: "MIN_SECURITY_DEPOSIT", effectiveFrom: "2026-08-28T10:15:30.123456Z", effectiveTo: null,
    reasonCode: "DISPUTE_EXPOSURE", releaseConditionCode: "MANUAL_REVIEW", requiredAmount: "20000",
    requiredCurrency: "USD", actionVersionId: "240001",
    controlInstanceKey: "RULE_ACTION:250001:DISPUTE_CASE:610101", ruleActionId: "250001",
    roleId: "200001", roleType: "CLIENT",
  };
  const matchedControl = {
    controlId: "9", controlVersion: 1, controlType: "SETTLEMENT_BLOCK", reasonCode: "KYB_EXPIRED",
    effectiveFrom: "2026-08-28T09:00:00.000001Z", effectiveTo: null,
    requiredAmount: null, requiredCurrency: null, scopeCount: 2,
    scopeSetHash: SCOPE_SET_HASH_CONTROL_9_V1_TWO_SCOPES,
    scopes: scopeRefs,
  };
  const sparseMatchedControl = { ...matchedControl, scopes: sparseScopeRefs };
  const sparseControls = [sparseMatchedControl, { ...matchedControl, controlId: "10" }];
  delete sparseControls[0];

  for (const [arrayPath, invoke] of [
    ["parameters", () => canonicalizers.canonicalizeSettlementActionParameterIdentity(parameters)],
    ["content scopes", () => canonicalizers.canonicalizeSettlementControlContent(control, sparseScopes)],
    ["scope set", () => canonicalizers.canonicalizeSettlementControlScopeSet({ controlId: "400001", controlVersion: 1, scopes: sparseScopeRefs })],
    ["matched scopes", () => canonicalizers.canonicalizeSettlementControlSet({ decision: "RESTRICT", requiredAmount: null, requiredCurrency: null, controls: [sparseMatchedControl] })],
    ["control set", () => canonicalizers.canonicalizeSettlementControlSet({ decision: "RESTRICT", requiredAmount: null, requiredCurrency: null, controls: sparseControls })],
  ]) {
    assert.throws(invoke, /dense array/u, `must reject sparse ${arrayPath} immediately`);
  }
});

test("Closed Settlement schemas reject duplicate canonical sort identities in either order", async () => {
  const canonicalizers = await loadSettlementCanonicalizers();
  const parameters = literalScopeTupleParameters();
  const duplicatedParameters = [...parameters, { ...parameters[0], stringValue: "210099" }];
  const duplicatedScopeRefs = [
    { scopeId: "2", scopeHash: SCOPE_HASH_210001_USD_AE },
    { scopeId: "2", scopeHash: SCOPE_HASH_210002_USD_ALL_COUNTRIES },
  ];
  const matchedControl = {
    controlId: "9", controlVersion: 1, controlType: "SETTLEMENT_BLOCK", reasonCode: "KYB_EXPIRED",
    effectiveFrom: "2026-08-28T09:00:00.000001Z", effectiveTo: null,
    requiredAmount: null, requiredCurrency: null, scopeCount: 1,
    scopeSetHash: SCOPE_SET_HASH_CONTROL_9_V1_SCOPE_2,
    scopes: [duplicatedScopeRefs[0]],
  };

  for (const orderedParameters of [duplicatedParameters, duplicatedParameters.toReversed()]) {
    assert.throws(
      () => canonicalizers.canonicalizeSettlementActionParameterIdentity(orderedParameters),
      /duplicate Action Parameter identity/u,
    );
  }
  for (const orderedScopes of [duplicatedScopeRefs, duplicatedScopeRefs.toReversed()]) {
    assert.throws(
      () => canonicalizers.canonicalizeSettlementControlSet({
        decision: "RESTRICT", requiredAmount: null, requiredCurrency: null,
        controls: [{ ...matchedControl, scopeCount: 2, scopes: orderedScopes }],
      }),
      /unique scopeId/u,
    );
  }

  assert.throws(
    () => canonicalizers.canonicalizeSettlementControlContent(
      {
        controlType: "MIN_SECURITY_DEPOSIT", effectiveFrom: "2026-08-28T10:15:30.123456Z", effectiveTo: null,
        reasonCode: "DISPUTE_EXPOSURE", releaseConditionCode: "MANUAL_REVIEW", requiredAmount: "20000",
        requiredCurrency: "USD", actionVersionId: "240001",
        controlInstanceKey: "RULE_ACTION:250001:DISPUTE_CASE:610101", ruleActionId: "250001",
        roleId: "200001", roleType: "CLIENT",
      },
      [
        { operatingEntityId: "210001", currency: "USD", bankCountry: "AE" },
        { operatingEntityId: "210001", currency: "USD", bankCountry: "AE" },
      ],
    ),
    /scopes must be unique/u,
  );
  assert.throws(
    () => canonicalizers.canonicalizeSettlementControlScopeSet({
      controlId: "400001", controlVersion: 1, scopes: duplicatedScopeRefs,
    }),
    /unique scopeId/u,
  );
  assert.throws(
    () => canonicalizers.canonicalizeSettlementControlSet({
      decision: "RESTRICT", requiredAmount: null, requiredCurrency: null,
      controls: [
        matchedControl,
        { ...matchedControl },
      ],
    }),
    /unique controlId/u,
  );
});

test("Settlement Eligibility freezes the three-table Risk topology and removes legacy structures", () => {
  assert.deepEqual(
    TABLE_GROUPS.settlement.map((table) => table.defKey),
    SETTLEMENT_ELIGIBILITY_TARGET.tableKeys,
  );
  assert.equal(TABLES.some((table) => table.defKey === "reserve_requirement"), false);
  assert.equal(TABLES.length, SETTLEMENT_ELIGIBILITY_TARGET.totalTables);

  const tableKeys = new Set(TABLES.map((table) => table.defKey));
  for (const removed of [
    "settlement_restriction",
    "settlement_restriction_scope",
    "settlement_restriction_state_change",
    "reserve_requirement",
  ]) {
    assert.equal(tableKeys.has(removed), false, `仍保留旧 Settlement 结构 ${removed}`);
  }
  assert.equal(
    TABLES.some((table) => table.defKey.startsWith("settlement_restriction")),
    false,
    "仍保留 settlement_restriction* 结构",
  );
  const closedValues = TABLES.flatMap((table) => table.fields.flatMap((field) => field.closedValues ?? []));
  assert.equal(closedValues.includes("SETTLEMENT_HOLD"), false, "仍保留 SETTLEMENT_HOLD 枚举值");
});

test("Settlement Control owns the source effect while excluding deprecated restriction columns", () => {
  const control = tableByKey(SETTLEMENT_TABLES, "settlement_control");
  const scope = tableByKey(SETTLEMENT_TABLES, "settlement_control_scope");
  const stateChange = tableByKey(SETTLEMENT_TABLES, "settlement_control_state_change");

  assertFields(control, SETTLEMENT_ELIGIBILITY_TARGET.controlFields);
  assert.deepEqual(fieldNames(control), [
    "control_id", "control_no", "role_type", "role_id", "control_type", "reason_code",
    "source_action_effect_id", "status", "effective_from", "effective_to",
    "required_amount", "required_currency", "release_condition_code",
    "control_instance_key", "content_hash", "active_slot", "version", "create_time", "update_time",
  ]);
  assertAbsentFields(control, ["decision_evaluation_id", "release_id", "reserve_rate"]);
  assertIndex(control, "uk_settle_control_no", true, ["control_no"]);
  assertIndex(control, "uk_settle_control_effect", true, ["source_action_effect_id"]);
  assertIndex(control, "uk_settle_control_active", true, ["role_type", "role_id", "control_instance_key", "active_slot"]);
  assertIndex(control, "idx_settle_control_role", false, ["role_type", "role_id", "active_slot", "control_id"]);
  assert.deepEqual(control.relations, [{
    childField: "source_action_effect_id",
    parentTable: "action_effect",
    parentField: "effect_id",
    cardinality: "1:1",
    childTable: "settlement_control",
  }]);

  assertFields(scope, SETTLEMENT_ELIGIBILITY_TARGET.scopeFields);
  assert.deepEqual(fieldNames(scope), [
    "scope_id", "control_id", "operating_entity_id", "currency", "bank_country", "scope_hash", "create_time",
  ]);
  assertAbsentFields(scope, ["country_code", "restriction_id"]);
  assertIndex(scope, "uk_settle_control_scope", true, ["control_id", "scope_hash"]);
  assert.deepEqual(scope.relations, [{
    childField: "control_id",
    parentTable: "settlement_control",
    parentField: "control_id",
    cardinality: "n:1",
    childTable: "settlement_control_scope",
  }]);

  assert.ok(stateChange, "必须保留 Settlement Control State Change 审计表");
  assert.equal(stateChange.physicalKey, "settle_control_state_change");
  assert.deepEqual(fieldNames(stateChange), [
    "change_id", "control_id", "from_status", "to_status", "reason_code", "trigger_type",
    "trigger_id", "actor_id", "aggregate_version", "occurred_time", "create_time",
  ]);
  assertIndex(stateChange, "uk_settle_control_state", true, ["control_id", "aggregate_version"]);
  assert.deepEqual(
    stateChange.indexes.filter((index) => index.unique),
    [{ defKey: "uk_settle_control_state", unique: true, fields: ["control_id", "aggregate_version"] }],
    "Settlement Control State Change 只能按聚合版本唯一，不得冻结 trigger 唯一键",
  );
  assert.equal(
    stateChange.indexes.some((index) => index.unique && index.fields.includes("trigger_id")),
    false,
    "Settlement Control State Change 不得按 trigger_id 唯一",
  );
  assert.deepEqual(stateChange.relations, [{
    childField: "control_id",
    parentTable: "settlement_control",
    parentField: "control_id",
    cardinality: "n:1",
    childTable: "settlement_control_state_change",
  }]);
});

test("Settlement Assessment freezes checkpoint inputs, results and the exact source-fact matrix", () => {
  const assessment = tableByKey(EVALUATION_TABLES, "settlement_risk_assessment");
  assertFields(assessment, SETTLEMENT_ELIGIBILITY_TARGET.assessmentFields);
  assert.deepEqual(fieldNames(assessment), [
    "assessment_id", "evaluation_id", "checkpoint_type", "role_type", "role_id",
    "operating_entity_id", "gross_amount", "currency", "bank_country", "source_fact_type",
    "source_fact_id", "source_fact_version", "input_hash", "eligibility_decision",
    "required_amount", "required_currency", "control_set_hash", "create_time",
  ]);
  assertAbsentFields(assessment, ["country_code"]);

  const fields = new Map(assessment.fields.map((field) => [field.defKey, field]));
  assert.deepEqual(
    fields.get("checkpoint_type")?.closedValues,
    Object.keys(SETTLEMENT_ELIGIBILITY_TARGET.assessmentSourceFactMatrix),
  );
  assert.deepEqual(
    fields.get("source_fact_type")?.closedValues,
    SETTLEMENT_ELIGIBILITY_TARGET.assessmentSourceFactTypes,
  );
  assert.deepEqual(
    MODEL_POLICY.SETTLEMENT_ELIGIBILITY_ASSESSMENT_SOURCE_FACT_MATRIX,
    {
      PAYOUT_CREATION: ["PAYOUT"],
      PAYOUT_FINAL_APPROVAL: ["PAYOUT"],
      PAYOUT_EXECUTION_CREATION: ["PAYOUT_EXECUTION"],
      PAYOUT_EXTERNAL_SUBMISSION: ["PAYOUT_EXECUTION"],
    },
  );
  assert.match(fields.get("gross_amount")?.description ?? "", /owner_debit_amount/u);
  assert.match(fields.get("gross_amount")?.description ?? "", /cash_required_amount/u);
  assert.match(fields.get("gross_amount")?.description ?? "", /requested_amount/u);
  assert.match(fields.get("gross_amount")?.description ?? "", /beneficiary_amount/u);
  assert.match(fields.get("source_fact_version")?.description ?? "", /变更前/u);
  assert.match(fields.get("source_fact_version")?.description ?? "", /expected version/iu);
  assert.match(fields.get("source_fact_version")?.description ?? "", /未来/u);
  assertIndex(assessment, "uk_settle_risk_eval", true, ["evaluation_id"]);
  assert.deepEqual(assessment.relations, [{
    childField: "evaluation_id",
    parentTable: "decision_evaluation",
    parentField: "evaluation_id",
    cardinality: "1:1",
    childTable: "settlement_risk_assessment",
  }]);
});

test("Settlement Eligibility obligation freezes its closed value and non-Action binding shape", () => {
  const obligation = tableByKey(DECISION_ENGINE_TABLES, "decision_point_obligation");
  const obligationType = obligation.fields.find((field) => field.defKey === "obligation_type");

  assert.deepEqual(obligationType?.closedValues, [
    "ACTION", "RESERVE_LIMIT_GROUP", "CHECK_SETTLEMENT_ELIGIBILITY",
  ]);
  assert.equal(
    MODEL_POLICY.CHECK_CONTRACTS.ck_obligation_target,
    "(obligation_type='ACTION' AND action_version_id IS NOT NULL) OR (obligation_type IN ('RESERVE_LIMIT_GROUP','CHECK_SETTLEMENT_ELIGIBILITY') AND action_version_id IS NULL)",
  );
  assert.deepEqual(MODEL_POLICY.CHECK_BINDINGS.ck_obligation_target, ["decision_point_obligation"]);
  assert.deepEqual(MODEL_POLICY.SETTLEMENT_ELIGIBILITY_OBLIGATION_CONTRACT, {
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
  assertDeepFrozen(
    MODEL_POLICY.SETTLEMENT_ELIGIBILITY_OBLIGATION_CONTRACT,
    "SETTLEMENT_ELIGIBILITY_OBLIGATION_CONTRACT",
  );
});

test("Rule Action Scope parameters require an ordered non-null value sequence", () => {
  const parameter = tableByKey(DECISION_ENGINE_TABLES, "rule_action_parameter");
  assertFields(parameter, ["value_sequence"]);
  const sequence = parameter.fields.find((field) => field.defKey === "value_sequence");
  assert.equal(sequence.notNull, true, "rule_action_parameter.value_sequence 必须非空");
  assertUniqueIndex(parameter, SETTLEMENT_ELIGIBILITY_TARGET.actionParameterUniqueKey);
  assertNoUniqueIndex(parameter, ["rule_action_id", "parameter_definition_id"]);
  assert.equal(MODEL_POLICY.CHECK_CONTRACTS.ck_action_parameter_sequence, "value_sequence>0");
  assert.deepEqual(MODEL_POLICY.CHECK_BINDINGS.ck_action_parameter_sequence, ["rule_action_parameter"]);
});

test("Settlement single-row CHECK contracts freeze type, slot, range, state, trigger, and Assessment matrices", () => {
  const expectedChecks = {
    ck_action_parameter_sequence: "value_sequence>0",
    ck_settle_control_type: "(control_type='SETTLEMENT_BLOCK' AND role_type IN ('CLIENT','LOCAL_PARTNER') AND required_amount IS NULL AND required_currency IS NULL AND release_condition_code IS NULL) OR (control_type='MIN_SECURITY_DEPOSIT' AND role_type='CLIENT' AND required_amount IS NOT NULL AND required_amount>0 AND required_currency IS NOT NULL AND release_condition_code IS NOT NULL)",
    ck_settle_control_status_slot: "status IS NOT NULL AND ((status='ACTIVE' AND active_slot IS NOT NULL AND active_slot=1) OR (status IN ('RELEASED','EXPIRED') AND active_slot IS NULL))",
    ck_settle_control_range: "effective_to IS NULL OR effective_from<effective_to",
    ck_settle_control_instance_key: "control_instance_key REGEXP '^RULE_ACTION:[1-9][0-9]*:[A-Z0-9][A-Z0-9._:-]{0,95}$'",
    ck_settle_control_state: "aggregate_version IS NOT NULL AND to_status IS NOT NULL AND ((aggregate_version=1 AND from_status IS NULL AND to_status='ACTIVE') OR (aggregate_version>1 AND from_status IS NOT NULL AND from_status='ACTIVE' AND to_status IN ('RELEASED','EXPIRED')))",
    ck_settle_control_trigger: "trigger_type IS NOT NULL AND aggregate_version IS NOT NULL AND to_status IS NOT NULL AND reason_code IS NOT NULL AND trigger_id IS NOT NULL AND ((trigger_type='ACTION_EFFECT' AND aggregate_version=1 AND from_status IS NULL AND to_status='ACTIVE' AND actor_id IS NULL AND trigger_id REGEXP '^[1-9][0-9]*$') OR (trigger_type='OPERATION_COMMAND' AND aggregate_version>1 AND from_status IS NOT NULL AND from_status='ACTIVE' AND to_status='RELEASED' AND actor_id IS NOT NULL AND trigger_id REGEXP '^risk-command:[1-9][0-9]*$') OR (trigger_type='SYSTEM_TIMER' AND aggregate_version>1 AND from_status IS NOT NULL AND from_status='ACTIVE' AND to_status='EXPIRED' AND actor_id IS NULL AND reason_code='EFFECTIVE_WINDOW_ENDED' AND trigger_id REGEXP '^settlement-control-expiry:v1:[1-9][0-9]*:[1-9][0-9]*:[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z$'))",
    ck_settle_assessment_input: "gross_amount>0 AND ((checkpoint_type IN ('PAYOUT_CREATION','PAYOUT_EXECUTION_CREATION') AND source_fact_version=1) OR (checkpoint_type IN ('PAYOUT_FINAL_APPROVAL','PAYOUT_EXTERNAL_SUBMISSION') AND source_fact_version>0)) AND ((checkpoint_type IN ('PAYOUT_CREATION','PAYOUT_FINAL_APPROVAL') AND source_fact_type='PAYOUT') OR (checkpoint_type IN ('PAYOUT_EXECUTION_CREATION','PAYOUT_EXTERNAL_SUBMISSION') AND source_fact_type='PAYOUT_EXECUTION'))",
    ck_settle_assessment_result: "(eligibility_decision IS NULL AND required_amount IS NULL AND required_currency IS NULL AND control_set_hash IS NULL) OR (eligibility_decision IS NOT NULL AND eligibility_decision IN ('ALLOW','RESTRICT') AND control_set_hash IS NOT NULL AND role_type IS NOT NULL AND ((role_type='CLIENT' AND required_amount IS NOT NULL AND required_amount>=0 AND required_currency IS NOT NULL AND currency IS NOT NULL AND required_currency=currency) OR (role_type='LOCAL_PARTNER' AND required_amount IS NULL AND required_currency IS NULL)))",
  };
  const expectedBindings = {
    ck_action_parameter_sequence: ["rule_action_parameter"],
    ck_settle_control_type: ["settlement_control"],
    ck_settle_control_status_slot: ["settlement_control"],
    ck_settle_control_range: ["settlement_control"],
    ck_settle_control_instance_key: ["settlement_control"],
    ck_settle_control_state: ["settlement_control_state_change"],
    ck_settle_control_trigger: ["settlement_control_state_change"],
    ck_settle_assessment_input: ["settlement_risk_assessment"],
    ck_settle_assessment_result: ["settlement_risk_assessment"],
  };

  for (const [name, expression] of Object.entries(expectedChecks)) {
    assert.equal(MODEL_POLICY.CHECK_CONTRACTS[name], expression, `${name} 表达式不准确`);
    assert.deepEqual(MODEL_POLICY.CHECK_BINDINGS[name], expectedBindings[name], `${name} 绑定不准确`);
  }
  assert.equal("ck_settle_restriction_range" in MODEL_POLICY.CHECK_CONTRACTS, false);
  assert.equal("ck_reserve_requirement" in MODEL_POLICY.CHECK_CONTRACTS, false);
});

test("Settlement cross-row policy freezes Effect origin, aggregate history, events, currency, simulation, and Assessment ownership", () => {
  const contracts = MODEL_POLICY.SETTLEMENT_CONTROL_CROSS_ROW_CONTRACTS;
  assert.ok(contracts, "缺少 Settlement Control 跨行合同元数据");
  assert.deepEqual(Object.keys(contracts).sort(), [
    "CR_SETTLEMENT_ASSESSMENT_DOMAIN",
    "CR_SETTLEMENT_CONTROL_AGGREGATE",
    "CR_SETTLEMENT_CONTROL_CURRENCY",
    "CR_SETTLEMENT_CONTROL_EFFECT_UNIQUE",
    "CR_SETTLEMENT_CONTROL_EVENT",
    "CR_SETTLEMENT_CONTROL_ORIGIN",
    "CR_SETTLEMENT_CONTROL_SIMULATION_FORBIDDEN",
  ]);
  assert.deepEqual(contracts.CR_SETTLEMENT_CONTROL_EFFECT_UNIQUE, {
    code: "CR_SETTLEMENT_CONTROL_EFFECT_UNIQUE",
    parent: { table: "action_effect", key: "effect_id" },
    child: { table: "settlement_control", foreignKey: "source_action_effect_id" },
    maximum: 1,
  });
  assert.deepEqual(contracts.CR_SETTLEMENT_CONTROL_ORIGIN, {
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
  });
  assert.deepEqual(contracts.CR_SETTLEMENT_CONTROL_AGGREGATE, {
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
  });
  assert.deepEqual(contracts.CR_SETTLEMENT_CONTROL_EVENT, {
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
  });
  assert.deepEqual(contracts.CR_SETTLEMENT_CONTROL_CURRENCY, {
    code: "CR_SETTLEMENT_CONTROL_CURRENCY",
    when: { table: "settlement_control", field: "control_type", equals: "MIN_SECURITY_DEPOSIT" },
    children: { table: "settlement_control_scope", foreignKey: "control_id", minimum: 1 },
    assertions: [
      { childField: "currency", operator: "NOT_NULL" },
      { childField: "currency", operator: "EQUALS_ROOT", rootField: "required_currency" },
    ],
  });
  assert.deepEqual(contracts.CR_SETTLEMENT_CONTROL_SIMULATION_FORBIDDEN, {
    code: "CR_SETTLEMENT_CONTROL_SIMULATION_FORBIDDEN",
    trigger: { table: "decision_evaluation", field: "evaluation_mode", values: ["SHADOW", "REPLAY"] },
    forbiddenPath: [
      ["decision_evaluation", "evaluation_id", "action_effect", "evaluation_id"],
      ["action_effect", "effect_id", "settlement_control", "source_action_effect_id"],
    ],
  });
  assert.deepEqual(contracts.CR_SETTLEMENT_ASSESSMENT_DOMAIN, {
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
  });
  assertDeepFrozen(contracts.CR_SETTLEMENT_CONTROL_ORIGIN, "CR_SETTLEMENT_CONTROL_ORIGIN");
  assertDeepFrozen(contracts.CR_SETTLEMENT_CONTROL_EVENT, "CR_SETTLEMENT_CONTROL_EVENT");
  assertDeepFrozen(contracts.CR_SETTLEMENT_ASSESSMENT_DOMAIN, "CR_SETTLEMENT_ASSESSMENT_DOMAIN");
});

test("Generated Risk model independently exposes the 71-table Settlement target", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const settlementDiagram = model.diagrams.find((diagram) => diagram.defKey === "settlement");

  assert.equal(
    model.entities.some((entity) => entity.defKey.startsWith("t_settlement_restriction")),
    false,
    "生成模型仍保留 t_settlement_restriction* 实体",
  );
  assert.equal(model.entities.length, SETTLEMENT_ELIGIBILITY_TARGET.totalTables);
  assert.equal(model.diagrams.length, SETTLEMENT_ELIGIBILITY_TARGET.diagramGroups);
  const settlementPhysicalTables = new Map(
    SETTLEMENT_TABLES.map((table) => [table.defKey, `t_${table.physicalKey ?? table.defKey}`]),
  );
  assert.deepEqual(
    settlementDiagram?.properties.primaryTables,
    SETTLEMENT_ELIGIBILITY_TARGET.tableKeys.map((table) => settlementPhysicalTables.get(table)),
  );
});
