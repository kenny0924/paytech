import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import test from "node:test";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const paths = Object.freeze({
  manifest: path.join(workspaceRoot, "scripts/pdmaner/vault/manifest.mjs"),
  policy: path.join(workspaceRoot, "scripts/pdmaner/vault/model-policy.mjs"),
  generator: path.join(workspaceRoot, "scripts/pdmaner/vault/generate-vault-model.mjs"),
  model: path.join(workspaceRoot, "vault-service/docs/database/vault.pdma.json"),
  decisions: path.join(
    workspaceRoot,
    "vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md",
  ),
  template: path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json"),
});

const task2Artifacts = Object.freeze([
  paths.manifest,
  paths.policy,
  paths.generator,
  paths.model,
]);
const requiredArtifacts = Object.freeze([
  ...task2Artifacts,
  paths.decisions,
]);
const task2ArtifactsReady = task2Artifacts.every(existsSync);
const physicalDecisionsReady = existsSync(paths.decisions);
const expectedTemplateDigest =
  "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
const idPattern =
  /^[A-F0-9]{8}-[A-F0-9]{4}-5[A-F0-9]{3}-[89AB][A-F0-9]{3}-[A-F0-9]{12}$/u;

// Test-owned oracle. It must never import a production Vault manifest or policy.
const APPROVED_FIELD_MANIFEST = String.raw`
[t_vault_record]
record_id BIGINT NN
token VARCHAR(64) NN
data_type VARCHAR(64) NN
token_mode VARCHAR(16) NN
value_fingerprint BINARY(32) N
ciphertext BLOB NN
encryption_nonce BINARY(12) NN
wrapped_dek BLOB NN
kms_key_ref VARCHAR(512) NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_vault_protect_request]
protect_request_id BIGINT NN
caller_service VARCHAR(128) NN
idempotency_key VARCHAR(128) NN
request_hash BINARY(32) NN
record_id BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_vault_access_event]
access_event_id BIGINT NN
request_id VARCHAR(64) NN
trace_id VARCHAR(64) N
caller_service VARCHAR(128) NN
actor_id VARCHAR(64) N
action VARCHAR(16) NN
data_type VARCHAR(64) N
purpose VARCHAR(64) N
record_id BIGINT N
outcome VARCHAR(16) NN
result_code VARCHAR(64) NN
policy_version VARCHAR(64) N
reason_code VARCHAR(64) N
ticket_ref VARCHAR(64) N
create_time DATETIME(6) NN

[t_vault_data_type]
data_type_id BIGINT NN
data_type_code VARCHAR(64) NN
token_mode VARCHAR(16) NN
data_class VARCHAR(16) NN
description VARCHAR(256) NN
manual_protect_enabled TINYINT(1) NN
manual_reveal_enabled TINYINT(1) NN
status VARCHAR(16) NN
version INT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_vault_access_rule]
access_rule_id BIGINT NN
caller_service VARCHAR(128) NN
action VARCHAR(16) NN
data_type_code VARCHAR(64) NN
purpose_code VARCHAR(64) NN
status VARCHAR(16) NN
version INT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_vault_policy_change]
policy_change_id BIGINT NN
policy_version_no INT NN
change_type VARCHAR(32) NN
target_type VARCHAR(16) NN
target_key VARCHAR(256) NN
before_snapshot JSON N
after_snapshot JSON NN
reason VARCHAR(500) NN
actor_id VARCHAR(64) NN
request_id VARCHAR(64) NN
create_time DATETIME(6) NN
`;

const APPROVED_INDEX_MANIFEST = String.raw`
t_vault_record
UK uk_vault_record_token(token)
UK uk_vault_record_stable(data_type,value_fingerprint)
t_vault_protect_request
UK uk_vault_protect_request(caller_service,idempotency_key)
IDX idx_vault_protect_record(record_id)
t_vault_access_event
IDX idx_vault_access_record(record_id,create_time)
IDX idx_vault_access_caller(caller_service,create_time)
IDX idx_vault_access_request(request_id)
IDX idx_vault_access_actor(actor_id,create_time)
t_vault_data_type
UK uk_vault_data_type_code(data_type_code)
t_vault_access_rule
UK uk_vault_access_rule(caller_service,action,data_type_code,purpose_code)
IDX idx_vault_access_rule_type(data_type_code)
t_vault_policy_change
UK uk_vault_policy_version(policy_version_no)
IDX idx_vault_policy_change_target(target_type,target_key,create_time)
`;

const APPROVED_RELATION_MANIFEST = String.raw`
n:1 vault_protect_request.record_id -> vault_record.record_id
n:1 vault_access_event.record_id -> vault_record.record_id
`;

const expectedTableOrder = Object.freeze([
  "t_vault_record",
  "t_vault_protect_request",
  "t_vault_access_event",
  "t_vault_data_type",
  "t_vault_access_rule",
  "t_vault_policy_change",
]);
const forbiddenFieldPattern = /environment|tenant|binding|resource|plaintext|raw_value|masked|blind_index|key_version|algorithm|expiry|shard|bucket|compensation|payload_json/u;
const approvedSensitiveFields = Object.freeze({
  value_fingerprint: "t_vault_record",
  request_hash: "t_vault_protect_request",
  ciphertext: "t_vault_record",
  encryption_nonce: "t_vault_record",
  wrapped_dek: "t_vault_record",
  kms_key_ref: "t_vault_record",
});
const EXPECTED_CHECK_CONTRACTS = Object.freeze({
  ck_vault_record_mode: "token_mode IN ('REFERENCE','STABLE')",
  ck_vault_record_fingerprint: "(token_mode='STABLE' AND value_fingerprint IS NOT NULL) OR (token_mode='REFERENCE' AND value_fingerprint IS NULL)",
  ck_vault_access_action: "action IN ('PROTECT','REVEAL')",
  ck_vault_access_outcome: "outcome IN ('SUCCESS','DENIED','FAILED')",
});

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function logicalTableKey(physicalTableKey) {
  return physicalTableKey.replace(/^t_/u, "");
}

function parseType(typeToken) {
  const match = typeToken.match(/^([A-Z]+)(?:\((\d+)(?:,(\d+))?\))?$/u);
  assert.ok(match, `无法解析物理类型：${typeToken}`);
  return {
    type: match[1],
    len: match[2] === undefined ? "" : Number(match[2]),
    scale: match[3] === undefined ? "" : Number(match[3]),
  };
}

function parseApprovedFieldManifest() {
  const headings = [...APPROVED_FIELD_MANIFEST.matchAll(/^\[(t_[a-z0-9_]+)\]$/gmu)];
  return headings.map((heading, index) => {
    const end = headings[index + 1]?.index ?? APPROVED_FIELD_MANIFEST.length;
    const tableSection = APPROVED_FIELD_MANIFEST.slice(heading.index, end);
    const fields = tableSection.slice(heading[0].length)
      .split(/\r?\n/u)
      .map((line) => line.trim())
      .filter((line) => /^[a-z][a-z0-9_]*\s+/u.test(line))
      .map((line, position) => {
        const match = line.match(
          /^([a-z][a-z0-9_]*)\s+([A-Z]+(?:\(\d+(?:,\d+)?\))?)\s+(NN|N)$/u,
        );
        assert.ok(match, `无法解析字段 Manifest：${heading[1]}.${line}`);
        return {
          defKey: match[1],
          ...parseType(match[2]),
          primaryKey: position === 0,
          notNull: match[3] === "NN",
        };
      });
    return { defKey: heading[1], fields };
  });
}

function parseApprovedIndexes() {
  const indexes = new Map();
  let tableKey;
  for (const rawLine of APPROVED_INDEX_MANIFEST.split(/\r?\n/u)) {
    const line = rawLine.trim();
    if (/^t_[a-z0-9_]+$/u.test(line)) {
      tableKey = line;
      indexes.set(tableKey, []);
      continue;
    }
    const match = line.match(/^(UK|IDX)\s+(\w+)\(([^)]+)\)$/u);
    if (!match) continue;
    assert.ok(tableKey, `索引 ${match[2]} 没有所属表`);
    indexes.get(tableKey).push({
      defKey: match[2],
      unique: match[1] === "UK",
      fields: match[3].split(",").map((field) => field.trim()),
    });
  }
  return indexes;
}

function parseApprovedRelations() {
  return APPROVED_RELATION_MANIFEST
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => line.startsWith("n:1 "))
    .map((line) => {
      const match = line.match(
        /^n:1\s+([a-z0-9_]+)\.([a-z0-9_]+) -> ([a-z0-9_]+)\.([a-z0-9_]+)$/u,
      );
      assert.ok(match, `无法解析本库关系：${line}`);
      return {
        childTable: match[1],
        childField: match[2],
        parentTable: match[3],
        parentField: match[4],
      };
    });
}

function readModel() {
  return JSON.parse(readFileSync(paths.model, "utf8"));
}

function entityMap(model) {
  return new Map(model.entities.map((entity) => [entity.defKey, entity]));
}

function fieldById(entity) {
  return new Map(entity.fields.map((field) => [field.id, field]));
}

function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  return {
    tables: model.entities.length,
    fields: model.entities.flatMap((entity) => entity.fields).length,
    uniqueIndexes: indexes.filter((index) => index.unique).length,
    accessIndexes: indexes.filter((index) => !index.unique).length,
    relations: model.entities.flatMap((entity) => entity.correlations).length,
    diagramGroups: model.diagrams.length,
  };
}

function relationSignatures(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return model.entities.flatMap((child) => {
    const childFields = fieldById(child);
    return child.correlations.map((relation) => {
      const parent = entitiesById.get(relation.refEntity);
      const parentFields = fieldById(parent);
      return [
        `${relation.myRows}:1`,
        `${logicalTableKey(child.defKey)}.${childFields.get(relation.myField).defKey}`,
        `${logicalTableKey(parent.defKey)}.${parentFields.get(relation.refField).defKey}`,
      ].join("|");
    });
  });
}

function expectedRelationSignatures() {
  return parseApprovedRelations().map((relation) => [
    "n:1",
    `${relation.childTable}.${relation.childField}`,
    `${relation.parentTable}.${relation.parentField}`,
  ].join("|"));
}

function generatedIds(model) {
  return [
    ...model.entities.flatMap((entity) => [
      entity.id,
      ...entity.fields.map((field) => field.id),
      ...entity.indexes.flatMap((index) => [
        index.id,
        ...index.fields.map((field) => field.id),
      ]),
    ]),
    ...model.diagrams.flatMap((diagram) => [
      diagram.id,
      ...diagram.canvasData.cells.map((cell) => cell.id),
    ]),
  ];
}

function task2ArtifactTest(name, fn) {
  test(name, { skip: !task2ArtifactsReady }, fn);
}

function physicalDecisionsTest(name, fn) {
  test(name, { skip: !physicalDecisionsReady }, fn);
}

test("approved Vault field manifest independently freezes the exact six-table candidate", () => {
  const approvedTables = parseApprovedFieldManifest();
  assert.deepEqual(approvedTables.map((table) => table.defKey), expectedTableOrder);
  assert.equal(
    approvedTables.flatMap((table) => table.fields).length,
    64,
    "批准候选字段清单实算必须为 64",
  );
  assert.equal(parseApprovedIndexes().size, 6);
  assert.equal(parseApprovedRelations().length, 2);
});

test("shared PDManer template retains the approved byte-for-byte digest", () => {
  assert.equal(sha256(readFileSync(paths.template)), expectedTemplateDigest);
});

test("Vault PDManer contract has every planned design artifact", () => {
  const missingArtifacts = requiredArtifacts
    .filter((artifactPath) => !existsSync(artifactPath))
    .map((artifactPath) => path.relative(workspaceRoot, artifactPath));
  assert.deepEqual(
    missingArtifacts,
    [],
    `Vault PDManer artifacts are missing: ${missingArtifacts.join(", ")}`,
  );
});

task2ArtifactTest("model has the exact ordered six-table, 64-field contract", () => {
  const model = readModel();
  const approvedTables = parseApprovedFieldManifest();
  assert.deepEqual(model.entities.map((entity) => entity.defKey), expectedTableOrder);
  for (const expectedTable of approvedTables) {
    const actual = entityMap(model).get(expectedTable.defKey);
    assert.ok(actual, `缺少 ${expectedTable.defKey}`);
    assert.deepEqual(
      actual.fields.map((field) => ({
        defKey: field.defKey,
        type: field.type,
        len: field.len,
        scale: field.scale,
        primaryKey: field.primaryKey,
        notNull: field.notNull,
      })),
      expectedTable.fields,
      `${expectedTable.defKey} 字段顺序、类型或 Nullability 偏离批准 Manifest`,
    );
  }
  assert.deepEqual(modelTotals(model), {
    tables: 6,
    fields: 64,
    uniqueIndexes: 6,
    accessIndexes: 7,
    relations: 2,
    diagramGroups: 1,
  });
});

task2ArtifactTest("model freezes the exact six UK and seven IDX field orders", () => {
  const expectedIndexes = parseApprovedIndexes();
  for (const entity of readModel().entities) {
    const fields = fieldById(entity);
    const actual = entity.indexes.map((index) => ({
      defKey: index.defKey,
      unique: index.unique,
      fields: index.fields.map((entry) => fields.get(entry.fieldDefKey).defKey),
    }));
    assert.deepEqual(actual, expectedIndexes.get(entity.defKey) ?? [], entity.defKey);
  }
});

task2ArtifactTest("model has exactly two local indexed Record relationships", () => {
  const model = readModel();
  assert.deepEqual(relationSignatures(model).sort(), expectedRelationSignatures().sort());
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  for (const child of model.entities) {
    const childFields = fieldById(child);
    const primary = child.fields.find((field) => field.primaryKey);
    for (const relation of child.correlations) {
      assert.ok(entitiesById.has(relation.refEntity), `${child.defKey} 出现跨模型关系`);
      assert.deepEqual([relation.myRows, relation.refRows, relation.innerType], ["n", "1", ""]);
      const childField = childFields.get(relation.myField);
      assert.ok(
        childField.id === primary.id || child.indexes.some(
          (index) => index.fields[0]?.fieldDefKey === childField.id,
        ),
        `${child.defKey}.${childField.defKey} 缺少左前缀索引`,
      );
    }
  }
});

task2ArtifactTest("model excludes unsafe fields and confines approved cryptographic fields", () => {
  for (const entity of readModel().entities) {
    for (const field of entity.fields) {
      assert.doesNotMatch(field.defKey, forbiddenFieldPattern, `${entity.defKey}.${field.defKey}`);
      assert.equal(field.autoIncrement, false, `${entity.defKey}.${field.defKey} 不得自增`);
      assert.equal(field.defaultValue, "", `${entity.defKey}.${field.defKey} 必须显式无默认值`);
      if (Object.hasOwn(approvedSensitiveFields, field.defKey)) {
        assert.equal(
          entity.defKey,
          approvedSensitiveFields[field.defKey],
          `${field.defKey} 仅允许出现在批准表中`,
        );
      }
    }
  }
  for (const [fieldKey, tableKey] of Object.entries(approvedSensitiveFields)) {
    assert.ok(
      entityMap(readModel()).get(tableKey).fields.some((field) => field.defKey === fieldKey),
      `${tableKey} 缺少批准字段 ${fieldKey}`,
    );
  }
});

task2ArtifactTest("model is credential-free, has the exact vault_core diagram, and uses deterministic UUID v5 IDs", () => {
  const model = readModel();
  assert.deepEqual(model.dbConn, []);
  assert.deepEqual(model.dbConns, []);
  assert.equal(model.profile.default.dbConn, "");
  assert.equal(model.diagrams.length, 1, "Vault 只能有一个 Diagram Group");

  const diagram = model.diagrams[0];
  assert.equal(diagram.defKey, "vault_core");
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  const tableCellsById = new Map(diagram.canvasData.cells
    .filter((cell) => cell.shape === "table")
    .map((cell) => [cell.id, cell]));
  assert.deepEqual(
    [...tableCellsById.values()].map((cell) => entitiesById.get(cell.originKey).defKey),
    expectedTableOrder,
  );
  const correlationIds = model.entities.flatMap((entity) => entity.correlations.map((relation) => relation.id));
  const relationCells = diagram.canvasData.cells.filter((cell) => cell.shape === "erdRelation");
  assert.deepEqual(relationCells.map((cell) => cell.originKey), correlationIds);
  assert.deepEqual(
    relationCells.map((cell) => {
      const child = tableCellsById.get(cell.source.cell);
      const parent = tableCellsById.get(cell.target.cell);
      return [
        cell.relation,
        logicalTableKey(entitiesById.get(child.originKey).defKey),
        logicalTableKey(entitiesById.get(parent.originKey).defKey),
      ].join("|");
    }),
    [
      "n:1|vault_protect_request|vault_record",
      "n:1|vault_access_event|vault_record",
    ],
  );

  const ids = generatedIds(model);
  assert.ok(ids.every((id) => idPattern.test(id)), "所有生成 ID 必须是稳定 SHA-256 UUID v5");
  assert.equal(new Set(ids).size, ids.length, "所有生成 ID 必须唯一");
});

task2ArtifactTest("generator produces the checked-in model byte-for-byte and --check detects no drift", async () => {
  const generator = await import(`${pathToFileURL(paths.generator).href}?contract=${Date.now()}`);
  const first = generator.serializeVaultModel(generator.generateVaultModel());
  const second = generator.serializeVaultModel(generator.generateVaultModel());
  assert.equal(first, second, "相同输入必须产生逐字节相同模型");
  assert.equal(first, readFileSync(paths.model, "utf8"), "已提交模型必须与生成器输出逐字节一致");

  const result = spawnSync(process.execPath, [paths.generator, "--check"], {
    cwd: workspaceRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /Vault model is up to date/u);
});

task2ArtifactTest("production policy freezes exact table, field, index, relation, and CHECK contracts", async () => {
  const policy = await import(`${pathToFileURL(paths.policy).href}?contract=${Date.now()}`);
  const approvedTables = parseApprovedFieldManifest();
  assert.deepEqual(
    policy.ALLOWED_TABLE_KEYS,
    approvedTables.map((table) => logicalTableKey(table.defKey)),
  );
  assert.deepEqual(
    policy.ALLOWED_FIELDS_BY_TABLE,
    Object.fromEntries(approvedTables.map((table) => [
      logicalTableKey(table.defKey),
      table.fields.map((field) => field.defKey),
    ])),
  );
  assert.deepEqual(
    policy.ALLOWED_INDEX_SIGNATURES,
    Object.fromEntries([...parseApprovedIndexes()].map(([tableKey, indexes]) => [
      logicalTableKey(tableKey),
      indexes.map((index) => `${index.unique ? "UK" : "IDX"}|${index.defKey}|${index.fields.join(",")}`),
    ])),
  );
  assert.deepEqual(policy.ALLOWED_RELATION_SIGNATURES, expectedRelationSignatures());
  assert.deepEqual(policy.CHECK_CONTRACTS, EXPECTED_CHECK_CONTRACTS);
});

physicalDecisionsTest("physical decisions freeze all four exact CHECK sidecar contracts", () => {
  const source = readFileSync(paths.decisions, "utf8");
  for (const [checkContract, expression] of Object.entries(EXPECTED_CHECK_CONTRACTS)) {
    assert.match(source, new RegExp(`\\b${checkContract}\\b`, "u"));
    assert.ok(source.includes(expression), `${checkContract} 缺少准确表达式`);
  }
});
