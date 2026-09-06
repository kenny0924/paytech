import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const service = new URL("../../risk-compliance-service/", import.meta.url);
const dmlUrl = new URL("docs/database/sql/risk-compliance-initial-data.sql", service);
const vectorsUrl = new URL("risk-test/src/test/resources/fixtures/risk-decision-v1-golden-vectors.json", service);
const vectors = JSON.parse(readFileSync(vectorsUrl, "utf8"));

const EXPECTED_VECTORS = Object.freeze([
  Object.freeze({
    actionVersionId: "240001",
    actionCode: "ALLOW_CHECKOUT",
    actionName: "Allow Checkout",
    handlerCode: "DECISION_ALLOW_V1",
    decisionCode: "ALLOW",
    canonicalJson: "{\"actionCategory\":\"DECISION\",\"decisionCode\":\"ALLOW\",\"handlerCode\":\"DECISION_ALLOW_V1\",\"implementationVersion\":\"1.0.0\",\"mergePolicy\":\"MOST_RESTRICTIVE\",\"parameters\":[],\"schema\":\"RISK_ACTION_HANDLER_DESCRIPTOR_V1\",\"shadowSupportedFlag\":true,\"terminalFlag\":true}",
    sha256: "185de7185670a27961afb0eade21b87fd99cc9677542ef5eaf6bb5c0806ceaa1",
  }),
  Object.freeze({
    actionVersionId: "240002",
    actionCode: "REVIEW_CHECKOUT",
    actionName: "Review Checkout",
    handlerCode: "DECISION_REVIEW_V1",
    decisionCode: "REVIEW",
    canonicalJson: "{\"actionCategory\":\"DECISION\",\"decisionCode\":\"REVIEW\",\"handlerCode\":\"DECISION_REVIEW_V1\",\"implementationVersion\":\"1.0.0\",\"mergePolicy\":\"MOST_RESTRICTIVE\",\"parameters\":[],\"schema\":\"RISK_ACTION_HANDLER_DESCRIPTOR_V1\",\"shadowSupportedFlag\":true,\"terminalFlag\":true}",
    sha256: "a4d4709e280ee2d8c2a4dcc5537e91318a16b2cd27ba41a10d70bdae25e85716",
  }),
  Object.freeze({
    actionVersionId: "240003",
    actionCode: "DENY_CHECKOUT",
    actionName: "Deny Checkout",
    handlerCode: "DECISION_DENY_V1",
    decisionCode: "DENY",
    canonicalJson: "{\"actionCategory\":\"DECISION\",\"decisionCode\":\"DENY\",\"handlerCode\":\"DECISION_DENY_V1\",\"implementationVersion\":\"1.0.0\",\"mergePolicy\":\"MOST_RESTRICTIVE\",\"parameters\":[],\"schema\":\"RISK_ACTION_HANDLER_DESCRIPTOR_V1\",\"shadowSupportedFlag\":true,\"terminalFlag\":true}",
    sha256: "8dc8b20590fa0d0c98732d332871901c568207ba9e346277668c9daab3a1e9b7",
  }),
]);

const PROJECTION_IDS = Object.freeze([
  "rule-set-version-primary",
  "decision-point-version-primary",
  "decision-release-primary",
  "create-rule-set-primary",
  "freeze-rule-set-primary",
  "create-test-release-primary",
  "validate-test-release-primary",
]);

const PROJECTION_SCHEMAS = Object.freeze([
  "RISK_RULE_SET_VERSION_V1",
  "RISK_DECISION_POINT_VERSION_V1",
  "RISK_DECISION_RELEASE_V1",
  "RISK_CREATE_RULE_SET_VERSION_COMMAND_V1",
  "RISK_FREEZE_RULE_SET_VERSION_COMMAND_V1",
  "RISK_CREATE_TEST_RELEASE_COMMAND_V1",
  "RISK_VALIDATE_TEST_RELEASE_COMMAND_V1",
]);

const PROJECTION_SHA256 = Object.freeze({
  "rule-set-version-primary": "c0c264fc814e152e58504c65ebb3b310e3821ad9c199fedf97a6870eba87871f",
  "decision-point-version-primary": "764b0f16fa0d238897476baa49fe4a66bbcb911e13cd3318bcc8ed237d35f691",
  "decision-release-primary": "edfecb39902e0d0a01f111bbc7fbefb626d4b7ee0756c77146c4c39fe5e71ba1",
  "create-rule-set-primary": "3f03a296cd7515c59713b6e86f44b3144154a5801174c58b499e9516ae24d9a8",
  "freeze-rule-set-primary": "eaab2898c2553b40850d79b10f38e421735191feaad094b6c10ae05de31f6183",
  "create-test-release-primary": "7131d6f878fe9920ac8a4f44b7477252b2d84cc3279f699468ebc7b9076c14fe",
  "validate-test-release-primary": "ea405a2ef710738b4fe0007f62e85cba741216409d6590b16d9690ed56f48173",
});

const DISCRIMINATOR_IDS = Object.freeze([
  "nfc-equivalence",
  "decimal-normalization",
  "utc-duration",
  "explicit-null-shape",
  "jcs-utf16-key-order",
  "unpaired-surrogate-rejected",
  "numeric-id-sort-2-before-10",
  "semantic-array-permutation",
  "violation-order-null-dot-underscore-id",
  "eight-static-check-order",
]);

const TEMPLATE_FIELD_CODES = Object.freeze([
  "country_code", "locale", "industry_code", "mcc_code", "currency", "merchandise_amount",
  "discount_amount", "net_amount", "entity_source_type",
]);

const STATIC_CHECK_RESULTS = Object.freeze([
  Object.freeze({ checkCode: "REFERENCE_INTEGRITY", checkStatus: "PASSED", severity: "INFO", errorCode: null, errorSummary: null }),
  Object.freeze({ checkCode: "AST_SHAPE", checkStatus: "FAILED", severity: "ERROR", errorCode: "AST_PATH", errorSummary: "first=AST_PATH@node.2; violation_count=2" }),
  Object.freeze({ checkCode: "AST_TYPES", checkStatus: "PASSED", severity: "INFO", errorCode: null, errorSummary: null }),
  Object.freeze({ checkCode: "FEATURE_DEPENDENCY", checkStatus: "PASSED", severity: "INFO", errorCode: null, errorSummary: null }),
  Object.freeze({ checkCode: "HANDLER_HASH", checkStatus: "FAILED", severity: "ERROR", errorCode: "HANDLER_HASH", errorSummary: "first=HANDLER_HASH@-; violation_count=1" }),
  Object.freeze({ checkCode: "ACTION_PARAMETERS", checkStatus: "PASSED", severity: "INFO", errorCode: null, errorSummary: null }),
  Object.freeze({ checkCode: "TERMINAL_ACTION", checkStatus: "PASSED", severity: "INFO", errorCode: null, errorSummary: null }),
  Object.freeze({ checkCode: "SYSTEM_OBLIGATION", checkStatus: "FAILED", severity: "ERROR", errorCode: "SYSTEM_OBLIGATION", errorSummary: "first=SYSTEM_OBLIGATION@-; violation_count=1" }),
]);

const DESCRIPTOR_KEYS = Object.freeze([
  "actionCategory",
  "decisionCode",
  "handlerCode",
  "implementationVersion",
  "mergePolicy",
  "parameters",
  "schema",
  "shadowSupportedFlag",
  "terminalFlag",
]);

function canonicalize(value) {
  if (value === null || typeof value === "boolean" || typeof value === "number") return JSON.stringify(value);
  if (typeof value === "string") return JSON.stringify(value.normalize("NFC"));
  if (Array.isArray(value)) return `[${value.map(canonicalize).join(",")}]`;
  assert.equal(Object.getPrototypeOf(value), Object.prototype, "JCS fixture 只接受 JSON Object");
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`).join(",")}}`;
}

function readDml() {
  assert.ok(existsSync(dmlUrl), "Risk 唯一初始化 DML 尚未创建");
  return readFileSync(dmlUrl, "utf8");
}

function splitSqlList(text) {
  const values = [];
  let start = 0;
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    if (text[index] === "'") {
      if (quoted && text[index + 1] === "'") {
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (text[index] === "," && !quoted) {
      values.push(text.slice(start, index).trim());
      start = index + 1;
    }
  }
  values.push(text.slice(start).trim());
  return values;
}

function parseRows(text) {
  const rows = [];
  let quoted = false;
  let depth = 0;
  let start = -1;
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (character === "'") {
      if (quoted && text[index + 1] === "'") index += 1;
      else quoted = !quoted;
    } else if (!quoted && character === "(") {
      if (depth === 0) start = index + 1;
      depth += 1;
    } else if (!quoted && character === ")") {
      depth -= 1;
      if (depth === 0) rows.push(splitSqlList(text.slice(start, index)));
    }
  }
  assert.equal(depth, 0, "DML VALUES 括号不闭合");
  assert.equal(quoted, false, "DML VALUES 字符串不闭合");
  return rows;
}

function parseInserts(sql) {
  return [...sql.matchAll(/INSERT INTO `([^`]+)` \(([^)]+)\) VALUES\s*([\s\S]*?);/gu)].map((match) => ({
    table: match[1],
    columns: [...match[2].matchAll(/`([^`]+)`/gu)].map((column) => column[1]),
    rows: parseRows(match[3]),
  }));
}

test("shared golden vectors freeze three complete NFC JCS Handler Descriptor byte sequences and hashes", () => {
  assert.equal(vectors.schema, "RISK_DECISION_V1_GOLDEN_VECTORS");
  assert.equal(vectors.fixtureVersion, 2);
  assert.equal(vectors.handlerDescriptors.length, 3);
  for (const [index, expected] of EXPECTED_VECTORS.entries()) {
    const vector = vectors.handlerDescriptors[index];
    assert.deepEqual(
      {
        actionVersionId: vector.actionVersionId,
        actionCode: vector.actionCode,
        actionName: vector.actionName,
        handlerCode: vector.descriptor.handlerCode,
        decisionCode: vector.descriptor.decisionCode,
        canonicalJson: vector.canonicalJson,
        sha256: vector.sha256,
      },
      expected,
    );
    assert.equal(vector.versionNo, 1);
    assert.deepEqual(Object.keys(vector.descriptor).toSorted(), DESCRIPTOR_KEYS);
    assert.equal(vector.descriptor.schema, "RISK_ACTION_HANDLER_DESCRIPTOR_V1");
    assert.equal(vector.descriptor.implementationVersion, "1.0.0");
    assert.equal(vector.descriptor.actionCategory, "DECISION");
    assert.equal(vector.descriptor.terminalFlag, true);
    assert.equal(vector.descriptor.mergePolicy, "MOST_RESTRICTIVE");
    assert.equal(vector.descriptor.shadowSupportedFlag, true);
    assert.deepEqual(vector.descriptor.parameters, []);
    assert.equal(vector.canonicalJson, canonicalize(vector.descriptor));
    assert.equal(vector.canonicalUtf8Hex, Buffer.from(expected.canonicalJson, "utf8").toString("hex"));
    assert.equal(createHash("sha256").update(expected.canonicalJson, "utf8").digest("hex"), expected.sha256);
    assert.match(vector.sha256, /^[0-9a-f]{64}$/u);
  }

  const normalizedDescriptors = vectors.handlerDescriptors.map(({ descriptor }) => ({
    ...descriptor,
    handlerCode: "<HANDLER>",
    decisionCode: "<DECISION>",
  }));
  assert.deepEqual(normalizedDescriptors[1], normalizedDescriptors[0]);
  assert.deepEqual(normalizedDescriptors[2], normalizedDescriptors[0]);
});

test("shared v2 fixture freezes seven closed projection triples and independent canonical discriminators", () => {
  assert.deepEqual(vectors.canonicalProjections.map(({ id }) => id), PROJECTION_IDS);
  assert.deepEqual(vectors.canonicalProjections.map(({ schema }) => schema), PROJECTION_SCHEMAS);
  assert.deepEqual(vectors.discriminators.map(({ id }) => id), DISCRIMINATOR_IDS);

  for (const projection of vectors.canonicalProjections) {
    assert.deepEqual(Object.keys(projection).toSorted(), ["expected", "id", "input", "schema"]);
    assert.equal(projection.input.schema, projection.schema);
    assert.deepEqual(Object.keys(projection.expected).toSorted(), ["canonicalJson", "canonicalUtf8Hex", "sha256"]);
    assert.equal(projection.expected.canonicalUtf8Hex, Buffer.from(projection.expected.canonicalJson, "utf8").toString("hex"));
    assert.equal(createHash("sha256").update(projection.expected.canonicalJson, "utf8").digest("hex"), projection.expected.sha256);
    assert.match(projection.expected.sha256, /^[0-9a-f]{64}$/u);
    assert.equal(projection.expected.sha256, PROJECTION_SHA256[projection.id]);
  }

  const projection = Object.fromEntries(vectors.canonicalProjections.map((entry) => [entry.id, entry]));
  assert.match(projection["decision-point-version-primary"].expected.canonicalJson,
    new RegExp(projection["rule-set-version-primary"].expected.sha256, "u"));
  assert.match(projection["decision-release-primary"].expected.canonicalJson,
    new RegExp(projection["decision-point-version-primary"].expected.sha256, "u"));
  assert.equal(projection["decision-point-version-primary"].input.inputContractCode, "ORDER_PREPARATION");
  assert.equal(projection["decision-point-version-primary"].input.inputContractVersion, 4);
  assert.deepEqual(projection["decision-point-version-primary"].input.inputFields.map(({ fieldCode }) => fieldCode).toSorted(),
    TEMPLATE_FIELD_CODES.toSorted());

  const ruleSetPrimary = projection["rule-set-version-primary"].input;
  const primaryPredicateOperands = [
    ruleSetPrimary.rules[0].condition.children[0].operands,
    ruleSetPrimary.rules[0].condition.children[1].operands,
    ruleSetPrimary.rules[1].condition.operands,
  ];
  assert.deepEqual(primaryPredicateOperands.map((operands) => operands.map(({ operandRole }) => operandRole)),
    [["RIGHT", "LEFT"], ["RIGHT", "LEFT"], ["RIGHT", "LEFT"]],
    "Rule Set primary must deliberately arrive in non-canonical operand order");
  assert.equal(primaryPredicateOperands.flat().every(({ valueSequence }) => valueSequence === 1), true,
    "arrival order must not alter each Node/Operand Role value sequence");

  const release = projection["decision-release-primary"].input;
  assert.equal(release.pointVersion.inputContractCode, "ORDER_PREPARATION");
  assert.equal(release.pointVersion.inputContractVersion, 4);
  assert.equal(release.inputFields.length, 9);
  assert.deepEqual(release.inputFields.map(({ fieldCode }) => fieldCode).toSorted(), TEMPLATE_FIELD_CODES.toSorted());
  assert.equal(release.ruleSetBindings.length, 1);
  assert.deepEqual(release.ruleSetBindings.map(({ ruleSetVersionId }) => ruleSetVersionId), ["230001"]);
  assert.deepEqual(release.ruleSetVersions.map(({ ruleSetVersionId }) => ruleSetVersionId), ["230001"]);
  assert.equal(release.ruleSetVersions[0].contentHash,
    projection["rule-set-version-primary"].expected.sha256);
  assert.equal(release.rules.length, 2);
  assert.equal(release.nodes.length, 4);
  assert.equal(release.operands.length, 6);
  assert.deepEqual(release.nodes.map(({ ruleId }) => ruleId).toSorted(), ["10", "10", "10", "2"]);
  assert.equal(release.operands.filter(({ sourceType }) => sourceType === "CONTEXT").length, 3);
  assert.equal(release.operands.filter(({ sourceType }) => sourceType === "LITERAL").length, 3);
  assert.equal(new Set(release.operands.filter(({ sourceType }) => sourceType === "CONTEXT")
    .map(({ inputFieldId }) => inputFieldId)).size, 3);
  assert.equal(release.operands.every(({ valueSequence }) => valueSequence === 1), true,
    "每个 Node/Operand Role 的首个值序号必须为 1");
  assert.match(projection["decision-release-primary"].expected.canonicalJson,
    /"numberValue":"1\.00000000"/u);
  assert.deepEqual(
    Object.keys(JSON.parse(projection["decision-release-primary"].expected.canonicalJson)).slice(-5),
    ["ruleActions", "ruleSetBindings", "ruleSetVersions", "rules", "schema"],
    "Release root keys must retain RFC 8785 UTF-16 ordering",
  );

  const create = projection["create-rule-set-primary"].input;
  assert.equal(create.schema, "RISK_CREATE_RULE_SET_VERSION_COMMAND_V1");
  assert.equal(Array.isArray(create.rules), true);
  assert.equal(Object.hasOwn(create, "arrivalPermutations"), false);
  assert.equal(projection["create-rule-set-primary"].expected.canonicalJson.includes("nodeKey"), false);
  assert.equal(create.rules.flatMap(({ conditionNodes }) => conditionNodes)
    .flatMap(({ operands }) => operands).every(({ valueSequence }) => valueSequence === 1), true);

  const discriminator = Object.fromEntries(vectors.discriminators.map((entry) => [entry.id, entry]));
  assert.equal(discriminator["nfc-equivalence"].inputs.length, 2);
  assert.equal(discriminator["nfc-equivalence"].expected.sha256,
    "f11fb9ac12d6e72d2c12389e51c1d61e420c6d82f58a9eb6b12ad7b67ffce98f");
  assert.equal(discriminator["jcs-utf16-key-order"].expected.sha256,
    "afbba5e7ece53d6eaf190380eb4ee93fa9dbcd7d1798bf1dfd5ae40e8f4dbe4b");
  assert.equal(discriminator["decimal-normalization"].rejected.some(({ kind }) => kind === "JSON_NUMBER"), true);
  assert.equal(discriminator["semantic-array-permutation"].equivalentInputs.length, 2);
  assert.equal(discriminator["semantic-array-permutation"].expectedSameHash,
    discriminator["semantic-array-permutation"].expectedSame.sha256);
  assert.equal(discriminator["semantic-array-permutation"].expectedDifferentHash,
    discriminator["semantic-array-permutation"].expectedChanged.sha256);
  assert.equal(discriminator["semantic-array-permutation"].changedInput.schema,
    "RISK_CREATE_RULE_SET_VERSION_COMMAND_V1");
  assert.equal(discriminator["semantic-array-permutation"].equivalentInputs[0].rules[0].conditionNodes[0].nodeKey,
    "node-10");
  assert.notEqual(discriminator["semantic-array-permutation"].equivalentInputs[0].rules[0].conditionNodes[0].nodeKey,
    discriminator["semantic-array-permutation"].equivalentInputs[1].rules[1].conditionNodes[0].nodeKey);
  assert.deepEqual(discriminator["eight-static-check-order"].inputChecks.map(({ checkCode }) => checkCode).toSorted(),
    STATIC_CHECK_RESULTS.map(({ checkCode }) => checkCode).toSorted());
  assert.deepEqual(discriminator["eight-static-check-order"].expected.results, STATIC_CHECK_RESULTS);
  assert.deepEqual(discriminator["violation-order-null-dot-underscore-id"].expected, {
    uniqueCount: 5,
    first: { errorCode: "AST_PATH", path: "node.2", ruleCode: "A_RULE", nodeId: "2", actionCode: "ALLOW_CHECKOUT" },
    summary: "first=AST_PATH@node.2; violation_count=5",
    sortedUnique: [
      { errorCode: "AST_PATH", path: "node.2", ruleCode: "A_RULE", nodeId: "2", actionCode: "ALLOW_CHECKOUT" },
      { errorCode: "AST_PATH", path: "node.2", ruleCode: "A_RULE", nodeId: "2", actionCode: null },
      { errorCode: "AST_PATH", path: "node_2", ruleCode: "A_RULE", nodeId: "10", actionCode: null },
      { errorCode: "AST_PATH", path: null, ruleCode: "A_RULE", nodeId: "2", actionCode: null },
      { errorCode: "AST_PATH", path: null, ruleCode: "A_RULE", nodeId: "10", actionCode: null },
    ],
  });
});

test("initial DML inserts exactly one CHECKOUT_PREPARE point and three ACTIVE Action Versions", () => {
  const sql = readDml();
  const inserts = parseInserts(sql);
  assert.deepEqual(inserts.map(({ table }) => table), ["t_decision_point", "t_action_version"]);
  assert.deepEqual(inserts[0].columns, [
    "decision_point_id", "point_code", "point_name", "business_domain", "status", "create_time", "update_time",
  ]);
  assert.deepEqual(inserts[0].rows, [[
    "210001", "'CHECKOUT_PREPARE'", "'Checkout Prepare'", "'CHECKOUT'", "'ACTIVE'",
    "'2026-09-05 00:00:00.000000'", "'2026-09-05 00:00:00.000000'",
  ]]);

  assert.deepEqual(inserts[1].columns, [
    "action_version_id", "action_code", "action_name", "action_category", "version_no", "handler_code",
    "implementation_version", "implementation_hash", "terminal_flag", "decision_code", "merge_policy",
    "shadow_supported_flag", "status", "create_time", "update_time",
  ]);
  assert.equal(inserts[1].rows.length, 3);
  assert.deepEqual(inserts[1].rows, EXPECTED_VECTORS.map((vector) => [
    vector.actionVersionId,
    `'${vector.actionCode}'`,
    `'${vector.actionName}'`,
    "'DECISION'",
    "1",
    `'${vector.handlerCode}'`,
    "'1.0.0'",
    `'${vector.sha256}'`,
    "1",
    `'${vector.decisionCode}'`,
    "'MOST_RESTRICTIVE'",
    "1",
    "'ACTIVE'",
    "'2026-09-05 00:00:00.000000'",
    "'2026-09-05 00:00:00.000000'",
  ]));
});

test("initial DML has no extra catalog rows, silent overwrite or dynamic values", () => {
  const sql = readDml();
  assert.doesNotMatch(sql, /\bINSERT\s+IGNORE\b|\bON\s+DUPLICATE\s+KEY\b|\bREPLACE\s+INTO\b/iu);
  assert.doesNotMatch(sql, /\b(?:CURRENT_TIMESTAMP|NOW)\s*\(/iu);
  assert.doesNotMatch(sql, /sha256:|placeholder/iu);
  assert.equal([...sql.matchAll(/\bINSERT\s+INTO\b/giu)].length, 2);
  assert.equal(parseInserts(sql).reduce((count, insert) => count + insert.rows.length, 0), 4);
});

test("initial DML parser rejects executable residue after the two approved INSERT statements", () => {
  const approved = readDml();
  assertDmlHasOnlyApprovedStatements(approved);
  for (const residue of [
    "UPDATE t_decision_point SET status='ACTIVE';",
    "DELETE FROM t_action_version;",
    "CREATE TABLE t_unapproved(id BIGINT);",
    "INSERT INTO `t_action_version` (`action_version_id`) VALUES (240004);",
  ]) {
    assert.throws(() => assertDmlHasOnlyApprovedStatements(`${approved}\n${residue}`), /(?:只允许两条批准 INSERT|不得有 residue)/u);
  }
});

function assertDmlHasOnlyApprovedStatements(sql) {
  const residue = sql.replace(/--[^\n]*(?:\n|$)/gu, "").replace(/INSERT INTO `[^`]+` \([^)]*\) VALUES\s*[\s\S]*?;/gu, "").trim();
  assert.equal(residue, "", "唯一初始化 DML 剥离注释和两条批准 INSERT 后不得有 residue");
  assert.equal(parseInserts(sql).length, 2, "唯一初始化 DML 只允许两条批准 INSERT");
}
