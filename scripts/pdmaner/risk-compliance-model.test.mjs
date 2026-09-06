import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import {
  APPROVED_DIAGRAMS,
  APPROVED_TABLE_GROUPS,
  APPROVED_TABLES,
  COMPENSATION_EVENT_TARGET,
  COMPENSATION_EVENT_RECONSTRUCTION_TARGET,
  EXPECTED_GROUP_SIZES,
  EXPECTED_CHECK_COUNT,
  EXPECTED_CHECK_POLICY_DIGEST,
  EXPECTED_MODEL_DIGEST,
  EXPECTED_STRUCTURAL_DIGEST,
  EXPECTED_TEMPLATE_DIGEST,
  EXPECTED_TOTALS,
  FILE_REFERENCE_TUPLES,
  FORBIDDEN_FIELD_PATTERN,
  FORBIDDEN_TABLES,
  REMOVED_DD_FIELDS,
  REQUIRED_COMPOSITE_FK_CONTRACTS,
  REQUIRED_COMMERCE_STABLE_CORRELATION_TOKENS,
  REQUIRED_DD_CHECK_CONTRACTS,
  REQUIRED_DD_COMMENT_FRAGMENTS,
  REQUIRED_DD_ENUMS,
  REQUIRED_DD_FIELD_SHAPES,
  REQUIRED_DD_INDEXES,
  REQUIRED_DD_POSITIVE_VERSION_TABLES,
  REQUIRED_DD_RELATIONS,
  REQUIRED_DD_VAULT_TOKEN_REFERENCES,
  REQUIRED_FIELDS,
  REQUIRED_RISK_OWNED_RECOVERABLE_TOKENS,
  RULE_SET_VERSION_AUDIT_TARGET,
} from "./risk-compliance-test-oracle/schema.oracle.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const paths = Object.freeze({
  template: path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json"),
  manifest: path.join(workspaceRoot, "scripts/pdmaner/risk/manifest.mjs"),
  policy: path.join(workspaceRoot, "scripts/pdmaner/risk/model-policy.mjs"),
  generator: path.join(workspaceRoot, "scripts/pdmaner/risk/generate-risk-compliance-model.mjs"),
  model: path.join(workspaceRoot, "risk-compliance-service/docs/database/risk-compliance.pdma.json"),
  decisions: path.join(workspaceRoot, "risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md"),
});

const idPattern = /^[A-F0-9]{8}-[A-F0-9]{4}-5[A-F0-9]{3}-[89AB][A-F0-9]{3}-[A-F0-9]{12}$/u;

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function assertArtifactsExist() {
  for (const [name, artifactPath] of Object.entries(paths)) {
    assert.ok(existsSync(artifactPath), `Risk ${name} 尚未生成：${artifactPath}`);
  }
}

function readModel() {
  assertArtifactsExist();
  return JSON.parse(readFileSync(paths.model, "utf8"));
}

function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  const relations = model.entities.flatMap((entity) => entity.correlations);
  return {
    tables: model.entities.length,
    fields: model.entities.flatMap((entity) => entity.fields).length,
    uniqueIndexes: indexes.filter((index) => index.unique).length,
    accessIndexes: indexes.filter((index) => !index.unique).length,
    relations: relations.length,
    oneToOneRelations: relations.filter((relation) => relation.myRows === "1").length,
    diagramGroups: model.diagrams.length,
  };
}

// Test-owned projection: deliberately does not import the production generator or manifests.
function structuralProjection(model) {
  const mysqlMappingId = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
  const javaMappingId = "797A1496-D649-4261-89B4-544132EC3F36";
  const mybatisMappingId = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return {
    mappings: model.dataTypeMapping.mappings.map((mapping) => ({
      id: mapping.id,
      defKey: mapping.defKey,
      mysql: mapping[mysqlMappingId] ?? null,
      java: mapping[javaMappingId] ?? null,
      mybatis: mapping[mybatisMappingId] ?? null,
    })),
    domains: model.domains.map(({ id, defKey, applyFor, len, scale }) => ({ id, defKey, applyFor, len, scale })),
    namingRules: model.namingRules.map(({ id, defName, programCode }) => ({ id, defName, programCode })),
    entities: model.entities.map((entity) => {
      const fieldsById = new Map(entity.fields.map((field) => [field.id, field]));
      return {
        id: entity.id,
        defKey: entity.defKey,
        defName: entity.defName,
        comment: entity.comment,
        compositeForeignKeys: entity.properties?.compositeForeignKeys ?? [],
        fields: entity.fields.map(({
          id, defKey, defName, comment, type, len, scale, primaryKey,
          notNull, autoIncrement, defaultValue, baseType, domain,
        }) => ({
          id, defKey, defName, comment, type, len, scale, primaryKey,
          notNull, autoIncrement, defaultValue, baseType, domain,
        })),
        indexes: entity.indexes.map((index) => ({
          id: index.id,
          defKey: index.defKey,
          unique: index.unique,
          fields: index.fields.map((entry) => fieldsById.get(entry.fieldDefKey)?.defKey),
        })),
        correlations: entity.correlations.map((relation) => {
          const parent = entitiesById.get(relation.refEntity);
          return {
            id: relation.id,
            childField: fieldsById.get(relation.myField)?.defKey,
            parentTable: parent?.defKey,
            parentField: parent?.fields.find((field) => field.id === relation.refField)?.defKey,
            myRows: relation.myRows,
            refRows: relation.refRows,
          };
        }),
      };
    }),
    diagrams: model.diagrams.map((diagram) => ({
      id: diagram.id,
      defKey: diagram.defKey,
      defName: diagram.defName,
      primaryTables: diagram.properties?.primaryTables ?? [],
      referenceTables: diagram.properties?.referenceTables ?? [],
      relations: diagram.canvasData.cells
        .filter((cell) => cell.shape === "erdRelation")
        .map(({ id, originKey, relation }) => ({ id, originKey, relation })),
    })),
  };
}

test("Risk test-owned Oracle freezes exactly 71 approved tables in eight groups", () => {
  assert.equal(Object.keys(APPROVED_TABLE_GROUPS).length, 8);
  assert.deepEqual(
    Object.fromEntries(Object.entries(APPROVED_TABLE_GROUPS).map(([key, tables]) => [key, tables.length])),
    EXPECTED_GROUP_SIZES,
  );
  assert.equal(APPROVED_TABLES.length, 71);
  assert.equal(new Set(APPROVED_TABLES).size, 71);
  assert.ok(APPROVED_TABLES.every((table) => /^t_[a-z][a-z0-9_]*$/u.test(table)));
  assert.ok(APPROVED_TABLES.every((table) => table.length <= 32));
  assert.ok(FORBIDDEN_TABLES.every((table) => !APPROVED_TABLES.includes(table)));
});

test("Risk Manifest DSL rejects incomplete and physically invalid tables", async () => {
  const { defineTable, field, primaryKey, uniqueIndex } = await import("./risk/schema-dsl.mjs");
  assert.throws(() => defineTable({
    key: "missing_metadata",
    name: "Missing",
    comment: "Missing",
    fields: [primaryKey("id", "主键", "测试主键")],
  }), /中文/u);
  assert.throws(() => defineTable({
    key: "table_name_that_is_longer_than_the_physical_database_limit",
    name: "超长表",
    comment: "验证物理表名长度限制",
    fields: [primaryKey("id", "主键", "测试主键")],
  }), /32/u);
  assert.throws(() => field(
    "field_name_that_is_longer_than_the_physical_database_limit",
    "VARCHAR(64)",
    "NN",
    "超长字段",
    "验证物理字段名长度限制",
  ), /32/u);
  assert.throws(() => uniqueIndex("idx_wrong_prefix", "id"), /索引/u);
});

test("Risk PDManer production artifacts are ready for validation", () => {
  assertArtifactsExist();
});

test("Risk model is current, deterministic and preserves the shared template", () => {
  assertArtifactsExist();
  assert.equal(sha256(readFileSync(paths.template)), EXPECTED_TEMPLATE_DIGEST);
  const result = spawnSync(process.execPath, [paths.generator, "--check"], {
    cwd: workspaceRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /Risk & Compliance model is up to date/u);
  assert.equal(sha256(readFileSync(paths.model)), EXPECTED_MODEL_DIGEST);
});

test("Risk model matches the independent totals and structural digest", () => {
  const model = readModel();
  assert.deepEqual(modelTotals(model), EXPECTED_TOTALS);
  assert.equal(sha256(JSON.stringify(structuralProjection(model))), EXPECTED_STRUCTURAL_DIGEST);
});

test("Risk model has the exact approved table set and eight diagram groups", () => {
  const model = readModel();
  assert.deepEqual(model.entities.map((entity) => entity.defKey).sort(), [...APPROVED_TABLES].sort());
  assert.equal(modelTotals(model).tables, 71);
  assert.equal(modelTotals(model).diagramGroups, 8);
  assert.deepEqual(
    Object.fromEntries(model.diagrams.map((diagram) => [diagram.defKey, diagram.defName])),
    APPROVED_DIAGRAMS,
  );
  assert.ok(FORBIDDEN_TABLES.every((table) => !model.entities.some((entity) => entity.defKey === table)));
});

test("Risk identifiers, metadata, primary keys and indexes follow physical conventions", () => {
  const model = readModel();
  const generatedIds = [];
  for (const entity of model.entities) {
    assert.match(entity.defKey, /^t_[a-z][a-z0-9_]*$/u);
    assert.ok(entity.defKey.length <= 32, `${entity.defKey} 超过 32 字符`);
    assert.match(entity.defName, /[\u3400-\u9fff]/u);
    assert.match(entity.comment, /[\u3400-\u9fff]/u);
    generatedIds.push(entity.id);

    const primaryKeys = entity.fields.filter((field) => field.primaryKey);
    assert.equal(primaryKeys.length, 1, `${entity.defKey} 必须恰有一个主键`);
    assert.equal(primaryKeys[0].type, "BIGINT");
    assert.equal(primaryKeys[0].autoIncrement, false);
    assert.ok(entity.fields.some((field) => field.defKey === "create_time"), `${entity.defKey} 缺少 create_time`);
    assert.equal(new Set(entity.fields.map((field) => field.defKey)).size, entity.fields.length);

    for (const field of entity.fields) {
      generatedIds.push(field.id);
      assert.match(field.defKey, /^[a-z][a-z0-9_]*$/u);
      assert.ok(field.defKey.length <= 32, `${entity.defKey}.${field.defKey} 超过 32 字符`);
      assert.match(field.defName, /[\u3400-\u9fff]/u);
      assert.match(field.comment, /[\u3400-\u9fff]/u);
      assert.doesNotMatch(field.defKey, FORBIDDEN_FIELD_PATTERN);
    }

    for (const index of entity.indexes) {
      generatedIds.push(index.id, ...index.fields.map((field) => field.id));
      assert.match(index.defKey, index.unique ? /^uk_[a-z0-9_]+$/u : /^idx_[a-z0-9_]+$/u);
      assert.ok(index.defKey.length <= 32, `${entity.defKey}.${index.defKey} 超过 32 字符`);
      assert.match(index.defName, /[\u3400-\u9fff]/u);
    }
  }
  assert.ok(generatedIds.every((id) => idPattern.test(id)));
  assert.equal(new Set(generatedIds).size, generatedIds.length);
});

test("Risk model freezes the critical due diligence, rule, action, limit and dispute fields", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  for (const [table, fields] of Object.entries(REQUIRED_FIELDS)) {
    const entity = entities.get(table);
    assert.ok(entity, `缺少关键表：${table}`);
    const actual = new Set(entity.fields.map((field) => field.defKey));
    for (const field of fields) assert.ok(actual.has(field), `缺少关键字段：${table}.${field}`);
  }
  for (const tuple of FILE_REFERENCE_TUPLES) {
    const fields = new Set(entities.get(tuple.table).fields.map((field) => field.defKey));
    assert.ok(fields.has(tuple.id) && fields.has(tuple.hash), `文件引用必须成对：${tuple.table}`);
  }
});

test("Risk compensation event uses the exact Foundation 15-column identity and lease contract", async () => {
  const model = readModel();
  const entity = model.entities.find(({ defKey }) => defKey === "t_compensation_event");
  const fields = new Map(entity.fields.map((field) => [field.defKey, field]));
  const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
  const indexes = Object.fromEntries(entity.indexes.map((index) => [
    index.defKey,
    index.fields.map((entry) => fieldsById.get(entry.fieldDefKey)),
  ]));

  assert.deepEqual(entity.fields.map((field) => field.defKey), COMPENSATION_EVENT_TARGET.fields);
  for (const fieldName of COMPENSATION_EVENT_TARGET.legacyFields) {
    assert.ok(!fields.has(fieldName), `旧 Compensation Event 字段仍存在：${fieldName}`);
  }
  for (const [fieldName, shape] of Object.entries(COMPENSATION_EVENT_TARGET.fieldShapes)) {
    const actual = fields.get(fieldName);
    assert.ok(actual, `Compensation Event 缺少字段：${fieldName}`);
    assert.deepEqual(
      Object.fromEntries(Object.keys(shape).map((key) => [key, actual[key]])),
      shape,
      `Compensation Event 字段形状漂移：${fieldName}`,
    );
  }
  assert.deepEqual(indexes, COMPENSATION_EVENT_TARGET.indexes);

  const {
    CHECK_BINDINGS,
    CHECK_CONTRACTS,
    COMPENSATION_EVENT_RECONSTRUCTION_INVENTORY,
  } = await import("./risk/model-policy.mjs");
  assert.ok(!Object.hasOwn(CHECK_CONTRACTS, "ck_compensation_counters"));
  assert.ok(!Object.hasOwn(CHECK_BINDINGS, "ck_compensation_counters"));
  assert.deepEqual(
    Object.fromEntries(COMPENSATION_EVENT_RECONSTRUCTION_INVENTORY.map((fact) => [
      fact.eventType,
      fact.subEventTypes,
    ])),
    COMPENSATION_EVENT_RECONSTRUCTION_TARGET,
  );
  for (const fact of COMPENSATION_EVENT_RECONSTRUCTION_INVENTORY) {
    assert.ok(fact.eventKeyFormat && fact.eventKeySource);
    assert.ok(fact.authorityTables.length > 0);
    assert.ok(Array.isArray(fact.envelopeSources));
    assert.ok(fact.permanentIdempotency);
    assert.equal(fact.payloadPolicy, "NULL_OR_MINIMAL_NON_SENSITIVE_JSON_OBJECT");
  }
});

test("Risk token policy separates owned Reveal references from Commerce correlation-only tokens", async () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey.slice(2), entity]));
  const {
    COMMERCE_STABLE_CORRELATION_TOKEN_REFERENCES,
    RISK_OWNED_RECOVERABLE_TOKEN_REFERENCES,
    VAULT_TOKEN_REFERENCES,
  } = await import("./risk/model-policy.mjs");

  assert.deepEqual(RISK_OWNED_RECOVERABLE_TOKEN_REFERENCES, REQUIRED_RISK_OWNED_RECOVERABLE_TOKENS);
  assert.deepEqual(VAULT_TOKEN_REFERENCES, REQUIRED_RISK_OWNED_RECOVERABLE_TOKENS);
  assert.deepEqual(COMMERCE_STABLE_CORRELATION_TOKEN_REFERENCES, REQUIRED_COMMERCE_STABLE_CORRELATION_TOKENS);
  for (const reference of [...REQUIRED_RISK_OWNED_RECOVERABLE_TOKENS, ...REQUIRED_COMMERCE_STABLE_CORRELATION_TOKENS]) {
    const field = entities.get(reference.table)?.fields.find(({ defKey }) => defKey === reference.token);
    assert.ok(field, `Token 字段缺失：${reference.table}.${reference.token}`);
    assert.deepEqual({ type: field.type, len: field.len }, { type: "VARCHAR", len: 128 });
  }
  const owned = new Set(REQUIRED_RISK_OWNED_RECOVERABLE_TOKENS.map(({ table, token }) => `${table}.${token}`));
  for (const { table, token } of REQUIRED_COMMERCE_STABLE_CORRELATION_TOKENS) {
    assert.ok(!owned.has(`${table}.${token}`), "Commerce Stable Correlation Token 不得登记为 Risk Reveal 授权");
  }
  assert.equal(entities.get("dd_party_snapshot").fields.find(({ defKey }) => defKey === "snapshot_vault_token").notNull, true);
});

test("Risk Rule Set Version freezes creator and safe change reason in exact order", () => {
  const model = readModel();
  const entity = model.entities.find(({ defKey }) => defKey === "t_rule_set_version");
  const fields = new Map(entity.fields.map((field) => [field.defKey, field]));
  assert.deepEqual(entity.fields.map((field) => field.defKey), RULE_SET_VERSION_AUDIT_TARGET.orderedFields);
  for (const [fieldName, shape] of Object.entries(RULE_SET_VERSION_AUDIT_TARGET.fieldShapes)) {
    const actual = fields.get(fieldName);
    assert.ok(actual, `Rule Set Version 缺少审计字段：${fieldName}`);
    assert.deepEqual(
      Object.fromEntries(Object.keys(shape).map((key) => [key, actual[key]])),
      shape,
      `Rule Set Version 审计字段形状漂移：${fieldName}`,
    );
  }
});

test("Risk PDManer keeps logical relation sidecars but defines no physical CHECK or FOREIGN KEY", () => {
  const model = readModel();
  const logicalRelations = model.entities.flatMap((entity) => entity.correlations);
  const compositeSidecars = model.entities.flatMap((entity) => entity.properties?.compositeForeignKeys ?? []);
  assert.ok(logicalRelations.length > 0);
  assert.deepEqual(
    compositeSidecars.toSorted((left, right) => left.name.localeCompare(right.name)),
    REQUIRED_COMPOSITE_FK_CONTRACTS.toSorted((left, right) => left.name.localeCompare(right.name)),
  );
  for (const entity of model.entities) {
    for (const forbiddenKey of ["checks", "checkConstraints", "foreignKeys", "foreignKeyConstraints"]) {
      assert.ok(!Object.hasOwn(entity, forbiddenKey), `Risk 物理模型不得定义 ${forbiddenKey}：${entity.defKey}`);
      assert.ok(!Object.hasOwn(entity.properties ?? {}, forbiddenKey), `Risk 物理模型 properties 不得定义 ${forbiddenKey}：${entity.defKey}`);
    }
  }
});

test("Risk due diligence keeps relation snapshots and review scheduling without auxiliary tables or pointers", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));

  for (const [table, fields] of Object.entries(REMOVED_DD_FIELDS)) {
    const actual = new Set(entities.get(table).fields.map((field) => field.defKey));
    for (const field of fields) assert.ok(!actual.has(field), `冗余 DD 字段仍存在：${table}.${field}`);
  }

  for (const [table, expectedIndexes] of Object.entries(REQUIRED_DD_INDEXES)) {
    const entity = entities.get(table);
    const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
    const indexes = new Map(entity.indexes.map((index) => [
      index.defKey,
      index.fields.map((entry) => fieldsById.get(entry.fieldDefKey)),
    ]));
    for (const [indexName, expectedFields] of Object.entries(expectedIndexes)) {
      assert.deepEqual(indexes.get(indexName), expectedFields, `DD 索引不满足防重/扫描契约：${table}.${indexName}`);
    }
  }
});

test("Risk due diligence simplification removes duplicate fields and keeps exact physical shapes", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));

  for (const [table, expectedFields] of Object.entries(REQUIRED_DD_FIELD_SHAPES)) {
    const fields = new Map(entities.get(table).fields.map((field) => [field.defKey, field]));
    for (const [fieldName, expected] of Object.entries(expectedFields)) {
      const actual = fields.get(fieldName);
      assert.ok(actual, `DD 精简后字段缺失：${table}.${fieldName}`);
      assert.deepEqual(
        { type: actual.type, notNull: actual.notNull },
        expected,
        `DD 字段类型或空值语义错误：${table}.${fieldName}`,
      );
    }
  }

  for (const relation of REQUIRED_DD_RELATIONS) {
    const child = entities.get(relation.childTable);
    const parent = entities.get(relation.parentTable);
    const childFields = new Map(child.fields.map((field) => [field.id, field.defKey]));
    const parentFields = new Map(parent.fields.map((field) => [field.id, field.defKey]));
    const actual = child.correlations.some((candidate) => (
      childFields.get(candidate.myField) === relation.childField
      && candidate.refEntity === parent.id
      && parentFields.get(candidate.refField) === relation.parentField
    ));
    assert.ok(actual, `DD 本库关系缺失：${relation.childTable}.${relation.childField}`);
  }

  for (const [table, fields] of Object.entries(REQUIRED_DD_ENUMS)) {
    const entityFields = new Map(entities.get(table).fields.map((field) => [field.defKey, field]));
    for (const [fieldName, values] of Object.entries(fields)) {
      assert.match(
        entityFields.get(fieldName).comment,
        new RegExp(`允许值：${values.join("、")}。`, "u"),
        `DD 闭合枚举漂移：${table}.${fieldName}`,
      );
    }
  }

  for (const [table, fields] of Object.entries(REQUIRED_DD_COMMENT_FRAGMENTS)) {
    const entityFields = new Map(entities.get(table).fields.map((field) => [field.defKey, field]));
    for (const [fieldName, fragments] of Object.entries(fields)) {
      const comment = entityFields.get(fieldName)?.comment ?? "";
      for (const fragment of fragments) {
        assert.ok(comment.includes(fragment), `DD 字段缺少消费者/口径说明：${table}.${fieldName}.${fragment}`);
      }
    }
  }
});

test("Risk due diligence Application identity scopes idempotency by typed source, role and qualification", () => {
  const model = readModel();
  const entity = model.entities.find(({ defKey }) => defKey === "t_due_diligence_case");
  const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
  const sourceIndex = entity.indexes.find(({ defKey }) => defKey === "uk_dd_case_source");
  const sourceFields = sourceIndex.fields.map((entry) => fieldsById.get(entry.fieldDefKey));

  assert.deepEqual(sourceFields, [
    "source_namespace",
    "source_application_id",
    "role_type",
    "role_id",
    "qualification_type",
  ]);
  const sourceKey = ({ sourceNamespace, sourceApplicationId, roleType, roleId, qualificationType }) =>
    `${sourceNamespace}:${sourceApplicationId}:${roleType}:${roleId}:${qualificationType}`;
  const first = {
    sourceNamespace: "CCT_CLIENT_APPLICATION",
    sourceApplicationId: 810001,
    roleType: "CLIENT",
    roleId: 200001,
    qualificationType: "ORGANIZATION_KYB",
    hash: "sha256:first",
  };
  const divergent = { ...first, hash: "sha256:divergent" };
  const relatedPerson = {
    ...first,
    roleType: "RELATED_PERSON",
    roleId: 100002,
    qualificationType: "RELATED_PERSON_KYC",
  };
  const operatingEntity = {
    ...first,
    sourceNamespace: "CCT_OPERATING_ENTITY_APPLICATION",
    roleType: "OPERATING_ENTITY",
    roleId: 300001,
    qualificationType: "ENTITY_UNDERWRITING",
  };
  const operatingEntityRelatedPerson = {
    ...relatedPerson,
    sourceNamespace: "CCT_OPERATING_ENTITY_APPLICATION",
  };

  assert.equal(sourceKey(first), sourceKey(divergent));
  assert.notEqual(first.hash, divergent.hash);
  assert.notEqual(sourceKey(first), sourceKey(relatedPerson));
  assert.notEqual(sourceKey(first), sourceKey(operatingEntity));
  assert.notEqual(sourceKey(relatedPerson), sourceKey(operatingEntityRelatedPerson));
});

test("Risk Local Partner Application namespace accepts only the approved main and related-person tuples", async () => {
  const model = readModel();
  const entity = model.entities.find(({ defKey }) => defKey === "t_due_diligence_case");
  const namespaceField = entity.fields.find(({ defKey }) => defKey === "source_namespace");
  const namespaceValues = new Set(
    namespaceField.comment.match(/允许值：([^。]+)。/u)?.[1].split("、") ?? [],
  );
  const { CHECK_CONTRACTS } = await import("./risk/model-policy.mjs");
  const applicationRoles = new Map(
    [...CHECK_CONTRACTS.ck_dd_case_origin.matchAll(
      /\(source_namespace='([^']+)' AND role_type IN \(([^)]+)\)\)/gu,
    )].map((match) => [
      match[1],
      new Set([...match[2].matchAll(/'([^']+)'/gu)].map((role) => role[1])),
    ]),
  );
  const qualificationsByRole = new Map([
    ["CLIENT", new Set(["INDIVIDUAL_KYC", "ORGANIZATION_KYB"])],
    ["LOCAL_PARTNER", new Set(["PARTNER_DUE_DILIGENCE"])],
    ["OPERATING_ENTITY", new Set(["ENTITY_UNDERWRITING"])],
    ["RELATED_PERSON", new Set(["RELATED_PERSON_KYC"])],
  ]);
  const sourceKey = ({ sourceNamespace, sourceApplicationId, roleType, roleId, qualificationType }) =>
    `${sourceNamespace}:${sourceApplicationId}:${roleType}:${roleId}:${qualificationType}`;
  const applicationTupleAccepted = (tuple) => (
    namespaceValues.has(tuple.sourceNamespace)
    && tuple.sourceApplicationId > 0n
    && tuple.roleId > 0n
    && applicationRoles.get(tuple.sourceNamespace)?.has(tuple.roleType) === true
    && qualificationsByRole.get(tuple.roleType)?.has(tuple.qualificationType) === true
  );
  const assertApplicationTupleAccepted = (tuple) => {
    assert.equal(applicationTupleAccepted(tuple), true, `应接受 Application Case Tuple：${sourceKey(tuple)}`);
  };
  const assertApplicationTupleRejected = (tuple) => {
    assert.equal(applicationTupleAccepted(tuple), false, `应拒绝 Application Case Tuple：${sourceKey(tuple)}`);
  };

  const partnerMain = {
    sourceNamespace: "CCT_LOCAL_PARTNER_APPLICATION",
    sourceApplicationId: 730320000000000001n,
    roleType: "LOCAL_PARTNER",
    roleId: 730300000000000001n,
    qualificationType: "PARTNER_DUE_DILIGENCE",
  };
  const relatedPerson = {
    sourceNamespace: "CCT_LOCAL_PARTNER_APPLICATION",
    sourceApplicationId: 730320000000000001n,
    roleType: "RELATED_PERSON",
    roleId: 730100000000000009n,
    qualificationType: "RELATED_PERSON_KYC",
  };

  assertApplicationTupleAccepted(partnerMain);
  assertApplicationTupleAccepted(relatedPerson);
  assertApplicationTupleRejected({
    ...partnerMain,
    roleType: "CLIENT",
    roleId: 730200000000000001n,
    qualificationType: "ORGANIZATION_KYB",
  });
  assertApplicationTupleRejected({
    ...partnerMain,
    roleType: "OPERATING_ENTITY",
    roleId: 730310000000000001n,
    qualificationType: "ENTITY_UNDERWRITING",
  });

  const sameRelatedPersonAcrossNamespaces = [
    "CCT_CLIENT_APPLICATION",
    "CCT_OPERATING_ENTITY_APPLICATION",
    "CCT_LOCAL_PARTNER_APPLICATION",
  ].map((sourceNamespace) => ({ ...relatedPerson, sourceNamespace }));
  for (const tuple of sameRelatedPersonAcrossNamespaces) assertApplicationTupleAccepted(tuple);
  assert.equal(
    new Set(sameRelatedPersonAcrossNamespaces.map(sourceKey)).size,
    sameRelatedPersonAcrossNamespaces.length,
    "相同 Application、Role、Party 和 Qualification 必须由三个 CCT Typed Namespace 隔离",
  );
});

test("Risk due diligence reuses one related-person child Case across dependency roles", () => {
  const model = readModel();
  const entity = model.entities.find(({ defKey }) => defKey === "t_dd_case_dependency");
  const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
  const dependencyIndex = entity.indexes.find(({ defKey }) => defKey === "uk_dd_case_dep");
  const dependencyFields = dependencyIndex.fields.map((entry) => fieldsById.get(entry.fieldDefKey));

  assert.deepEqual(dependencyFields, ["parent_case_id", "child_case_id", "dependency_type"]);
  const dependencyKey = ({ parentCaseId, childCaseId, dependencyType }) =>
    `${parentCaseId}:${childCaseId}:${dependencyType}`;
  const ubo = { parentCaseId: 120001, childCaseId: 120002, dependencyType: "UBO_KYC" };
  const director = { ...ubo, dependencyType: "DIRECTOR_KYC" };
  const duplicateUbo = { ...ubo };

  assert.notEqual(dependencyKey(ubo), dependencyKey(director));
  assert.equal(dependencyKey(ubo), dependencyKey(duplicateUbo));
});

test("Risk due diligence CHECK policy freezes origin, subject and review invariants", async () => {
  const { CHECK_BINDINGS, CHECK_CONTRACTS, VAULT_TOKEN_REFERENCES } = await import("./risk/model-policy.mjs");
  assert.equal(Object.keys(CHECK_CONTRACTS).length, EXPECTED_CHECK_COUNT);
  assert.equal(Object.keys(CHECK_BINDINGS).length, EXPECTED_CHECK_COUNT);
  const checkPolicyProjection = Object.fromEntries(Object.keys(CHECK_CONTRACTS).sort().map((checkName) => [
    checkName,
    { expression: CHECK_CONTRACTS[checkName], tables: CHECK_BINDINGS[checkName] },
  ]));
  assert.equal(sha256(JSON.stringify(checkPolicyProjection)), EXPECTED_CHECK_POLICY_DIGEST);
  for (const [checkName, expected] of Object.entries(REQUIRED_DD_CHECK_CONTRACTS)) {
    assert.equal(CHECK_CONTRACTS[checkName], expected.expression, `DD CHECK 表达式漂移：${checkName}`);
    assert.deepEqual(CHECK_BINDINGS[checkName], expected.tables, `DD CHECK 绑定漂移：${checkName}`);
  }
  for (const table of REQUIRED_DD_POSITIVE_VERSION_TABLES) {
    assert.ok(CHECK_BINDINGS.ck_positive_version.includes(table), `DD 可变表缺少正版本约束：${table}`);
  }
  assert.deepEqual(VAULT_TOKEN_REFERENCES, REQUIRED_DD_VAULT_TOKEN_REFERENCES);
  assert.ok(!Object.hasOwn(CHECK_CONTRACTS, "ck_dd_relation_self"));
  assert.ok(!Object.hasOwn(CHECK_BINDINGS, "ck_dd_relation_self"));
});

test("Risk due diligence active slots are occupied exactly by active lifecycle states", () => {
  const allowed = (status, activeSlot, activeStatuses, terminalStatuses) => (
    (activeStatuses.includes(status) && activeSlot === 1)
    || (terminalStatuses.includes(status) && activeSlot === null)
  );

  const programActive = ["ACTIVE"];
  const programInactive = ["DRAFT", "RETIRED"];
  for (const status of [...programActive, ...programInactive]) {
    assert.equal(allowed(status, 1, programActive, programInactive), programActive.includes(status));
    assert.equal(allowed(status, null, programActive, programInactive), programInactive.includes(status));
  }

  const caseActive = ["DRAFT", "SUBMITTED", "IN_REVIEW", "WAITING_EVIDENCE"];
  const caseTerminal = ["APPROVED", "REJECTED", "WITHDRAWN", "EXPIRED"];
  for (const status of [...caseActive, ...caseTerminal]) {
    assert.equal(allowed(status, 1, caseActive, caseTerminal), caseActive.includes(status));
    assert.equal(allowed(status, null, caseActive, caseTerminal), caseTerminal.includes(status));
  }
  assert.equal(allowed("ACTIVE", 0, programActive, programInactive), false);
  assert.equal(allowed("SUBMITTED", 0, caseActive, caseTerminal), false);
});

test("Risk relations are local, indexed and one-to-one cardinality is enforceable", () => {
  const model = readModel();
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  const expectedCompositeForeignKeys = new Map(
    REQUIRED_COMPOSITE_FK_CONTRACTS.map((contract) => [contract.name, contract]),
  );
  for (const child of model.entities) {
    const fieldsById = new Map(child.fields.map((field) => [field.id, field]));
    const primary = child.fields.find((field) => field.primaryKey);
    for (const relation of child.correlations) {
      assert.ok(entitiesById.has(relation.refEntity), `${child.defKey} 引用了库外实体`);
      const childField = fieldsById.get(relation.myField);
      assert.ok(childField, `${child.defKey} 关系字段不存在`);
      const leftPrefix = childField.id === primary.id
        || child.indexes.some((index) => index.fields[0]?.fieldDefKey === childField.id);
      assert.ok(leftPrefix, `${child.defKey}.${childField.defKey} 缺少左前缀索引`);
      if (relation.myRows === "1") {
        const exactUnique = child.indexes.filter((index) => index.unique
          && index.fields.length === 1
          && index.fields[0].fieldDefKey === childField.id);
        const parent = entitiesById.get(relation.refEntity);
        const parentField = parent.fields.find((field) => field.id === relation.refField);
        const compositeUnique = (child.properties?.compositeForeignKeys ?? []).some((contract) => {
          const expected = expectedCompositeForeignKeys.get(contract.name);
          if (!expected || JSON.stringify(contract) !== JSON.stringify(expected)) return false;
          if (contract.childTable !== child.defKey || contract.childFields[0] !== childField.defKey) return false;
          if (contract.parentTable !== parent.defKey || contract.parentFields[0] !== parentField.defKey) return false;
          return child.indexes.some((index) => index.unique
            && index.fields.length === contract.childFields.length
            && contract.childFields.every((fieldName, offset) => (
              index.fields[offset]?.fieldDefKey === child.fields.find((field) => field.defKey === fieldName)?.id
            )));
        });
        assert.ok(childField.id === primary.id || exactUnique.length === 1 || compositeUnique,
          `${child.defKey}.${childField.defKey} 的 1:1 关系缺少主键、单列唯一键或同案复合唯一键`);
      }
    }
  }
});

test("Risk model persists the exact same-case composite foreign-key contracts", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const persisted = model.entities.flatMap((entity) => entity.properties?.compositeForeignKeys ?? []);
  assert.deepEqual(
    persisted.toSorted((left, right) => left.name.localeCompare(right.name)),
    REQUIRED_COMPOSITE_FK_CONTRACTS.toSorted((left, right) => left.name.localeCompare(right.name)),
  );

  for (const contract of REQUIRED_COMPOSITE_FK_CONTRACTS) {
    const child = entities.get(contract.childTable);
    const parent = entities.get(contract.parentTable);
    const childFields = new Map(child.fields.map((field) => [field.defKey, field]));
    const parentFields = new Map(parent.fields.map((field) => [field.defKey, field]));
    assert.ok(contract.childFields.every((field) => childFields.has(field)), `${contract.name} 子字段缺失`);
    assert.ok(contract.parentFields.every((field) => parentFields.has(field)), `${contract.name} 父字段缺失`);
    assert.ok(contract.childFields.every((field, index) => (
      childFields.get(field).type === parentFields.get(contract.parentFields[index]).type
      && childFields.get(field).len === parentFields.get(contract.parentFields[index]).len
      && childFields.get(field).scale === parentFields.get(contract.parentFields[index]).scale
    )), `${contract.name} 子父字段类型不一致`);
    assert.ok(child.indexes.some((index) => contract.childFields.every((field, offset) => (
      index.fields[offset]?.fieldDefKey === childFields.get(field).id
    ))), `${contract.name} 子表缺少左前缀索引`);
    assert.ok(parent.indexes.some((index) => index.unique
      && index.fields.length === contract.parentFields.length
      && contract.parentFields.every((field, offset) => (
        index.fields[offset]?.fieldDefKey === parentFields.get(field).id
      ))), `${contract.name} 父表缺少精确唯一键`);
  }
});

test("Risk diagrams draw each local relation once and every table owns one primary group", () => {
  const model = readModel();
  const primaryTables = model.diagrams.flatMap((diagram) => diagram.properties.primaryTables);
  assert.equal(primaryTables.length, 71);
  assert.equal(new Set(primaryTables).size, 71);
  assert.deepEqual([...primaryTables].sort(), [...APPROVED_TABLES].sort());
  const relationCount = model.entities.flatMap((entity) => entity.correlations).length;
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells)
    .filter((cell) => cell.shape === "erdRelation");
  assert.equal(relationCells.length, relationCount);
  assert.equal(new Set(relationCells.map((cell) => cell.originKey)).size, relationCount);
});

test("Risk model stores no database connection or forbidden card and credential fields", () => {
  const model = readModel();
  assert.deepEqual(model.dbConn, []);
  assert.deepEqual(model.dbConns, []);
  assert.equal(model.profile.default.dbConn, "");
  const allFields = model.entities.flatMap((entity) => entity.fields.map((field) => `${entity.defKey}.${field.defKey}`));
  assert.ok(allFields.every((field) => !FORBIDDEN_FIELD_PATTERN.test(field)));
  assert.ok(!JSON.stringify(model).match(/jdbc:\/\/|BEGIN PRIVATE KEY|password\s*[=:]/iu));
});
