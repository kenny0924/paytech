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
  EXPECTED_CHECK_COUNT,
  EXPECTED_CHECK_POLICY_DIGEST,
  EXPECTED_GROUP_SIZES,
  EXPECTED_MODEL_DIGEST,
  EXPECTED_STRUCTURAL_DIGEST,
  EXPECTED_TEMPLATE_DIGEST,
  EXPECTED_TOTALS,
  FILE_REFERENCE_TUPLES,
  FORBIDDEN_FIELD_PATTERN,
  FORBIDDEN_FIELDS_BY_TABLE,
  FORBIDDEN_TABLES,
  REQUIRED_FIELDS,
  REQUIRED_COMPOSITE_FKS,
  REQUIRED_CHECK_CONTRACTS,
} from "./platform-support-test-oracle/schema.oracle.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const paths = Object.freeze({
  template: path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json"),
  manifest: path.join(workspaceRoot, "scripts/pdmaner/platform-support/manifest.mjs"),
  policy: path.join(workspaceRoot, "scripts/pdmaner/platform-support/model-policy.mjs"),
  generator: path.join(workspaceRoot, "scripts/pdmaner/platform-support/generate-platform-support-model.mjs"),
  model: path.join(workspaceRoot, "platform-support-service/docs/database/platform-support.pdma.json"),
  decisions: path.join(workspaceRoot, "platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md"),
});

const idPattern = /^[A-F0-9]{8}-[A-F0-9]{4}-5[A-F0-9]{3}-[89AB][A-F0-9]{3}-[A-F0-9]{12}$/u;

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function assertArtifactsExist() {
  for (const [name, artifactPath] of Object.entries(paths)) {
    assert.ok(existsSync(artifactPath), `Platform Support ${name} 尚未生成：${artifactPath}`);
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
        properties: {
          logicalTableKey: entity.properties?.logicalTableKey,
          compositeForeignKeys: entity.properties?.compositeForeignKeys ?? [],
        },
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

test("Platform Support Oracle freezes exactly 92 approved tables in eight groups", () => {
  assert.equal(Object.keys(APPROVED_TABLE_GROUPS).length, 8);
  assert.deepEqual(
    Object.fromEntries(Object.entries(APPROVED_TABLE_GROUPS).map(([key, tables]) => [key, tables.length])),
    EXPECTED_GROUP_SIZES,
  );
  assert.equal(APPROVED_TABLES.length, 92);
  assert.equal(new Set(APPROVED_TABLES).size, 92);
  assert.ok(APPROVED_TABLES.every((table) => /^t_[a-z][a-z0-9_]*$/u.test(table)));
  assert.ok(APPROVED_TABLES.every((table) => table.length <= 32));
  assert.ok(FORBIDDEN_TABLES.every((table) => !APPROVED_TABLES.includes(table)));
});

test("Platform Support Manifest DSL rejects incomplete and invalid tables", async () => {
  assert.ok(existsSync(paths.manifest), `Platform Support Manifest 尚未生成：${paths.manifest}`);
  const { defineTable, field, primaryKey, uniqueIndex } = await import("./platform-support/schema-dsl.mjs");
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

test("Platform Support PDManer production artifacts are ready for validation", () => {
  assertArtifactsExist();
});

test("Platform Support model is current and preserves the shared template", () => {
  assertArtifactsExist();
  assert.equal(sha256(readFileSync(paths.template)), EXPECTED_TEMPLATE_DIGEST);
  const result = spawnSync(process.execPath, [paths.generator, "--check"], {
    cwd: workspaceRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /Platform Support model is up to date/u);
  assert.ok(EXPECTED_MODEL_DIGEST, "模型摘要尚未冻结");
  assert.equal(sha256(readFileSync(paths.model)), EXPECTED_MODEL_DIGEST);
});

test("Platform Support model matches independent totals and structural digest", () => {
  const model = readModel();
  assert.ok(EXPECTED_TOTALS, "模型统计尚未冻结");
  assert.ok(EXPECTED_STRUCTURAL_DIGEST, "结构摘要尚未冻结");
  assert.deepEqual(modelTotals(model), EXPECTED_TOTALS);
  assert.equal(sha256(JSON.stringify(structuralProjection(model))), EXPECTED_STRUCTURAL_DIGEST);
});

test("Platform Support model has the exact approved table and diagram sets", () => {
  const model = readModel();
  assert.deepEqual(model.entities.map((entity) => entity.defKey).sort(), [...APPROVED_TABLES].sort());
  assert.deepEqual(
    Object.fromEntries(model.diagrams.map((diagram) => [diagram.defKey, diagram.defName])),
    APPROVED_DIAGRAMS,
  );
  assert.ok(FORBIDDEN_TABLES.every((table) => !model.entities.some((entity) => entity.defKey === table)));
});

test("Platform Support identifiers, metadata, primary keys and indexes follow conventions", () => {
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
      generatedIds.push(index.id, ...index.fields.map((entry) => entry.id));
      assert.match(index.defKey, index.unique ? /^uk_[a-z0-9_]+$/u : /^idx_[a-z0-9_]+$/u);
      assert.ok(index.defKey.length <= 32, `${entity.defKey}.${index.defKey} 超过 32 字符`);
      assert.match(index.defName, /[\u3400-\u9fff]/u);
    }
  }
  assert.ok(generatedIds.every((id) => idPattern.test(id)));
  assert.equal(new Set(generatedIds).size, generatedIds.length);
});

test("Platform Support separates mutable lifecycle metadata from immutable facts", () => {
  const model = readModel();
  const byKey = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  for (const tableKey of [
    "t_file_scan_policy_ver",
    "t_document_template_ver",
    "t_document_template_translation",
    "t_document_version",
    "t_notify_template_translation",
  ]) {
    const fields = new Set(byKey.get(tableKey)?.fields.map((field) => field.defKey));
    assert.ok(fields.has("status"), `${tableKey} 必须保存生命周期状态`);
    assert.ok(fields.has("update_time"), `${tableKey} 的生命周期更新必须可审计`);
  }
  for (const tableKey of [
    "t_file_scan_requirement",
    "t_file_scan_result",
    "t_notification_attempt",
    "t_webhook_attempt",
    "t_audit_event",
  ]) {
    const fields = new Set(byKey.get(tableKey)?.fields.map((field) => field.defKey));
    assert.ok(fields.has("create_time"), `${tableKey} 必须保存创建时间`);
    assert.ok(!fields.has("update_time"), `${tableKey} 是追加式事实，不得保存更新时间`);
  }
});

test("Platform Support distinguishes pre-freeze cancellation from post-freeze gateway rejection", async () => {
  const { TABLES } = await import("./platform-support/manifest.mjs");
  const release = TABLES.find((table) => table.defKey === "gateway_release");
  const releaseStatus = release.fields.find((field) => field.defKey === "status");
  assert.ok(releaseStatus.closedValues.includes("CANCELLED"));
  assert.ok(releaseStatus.closedValues.includes("REJECTED"));
  assert.ok(releaseStatus.closedValues.includes("ACTIVATION_FAILED"));
  assert.ok(!releaseStatus.closedValues.includes("FAILED"));
});

test("Platform Support freezes critical fields and minimal Vault shapes", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  for (const [table, fields] of Object.entries(REQUIRED_FIELDS)) {
    const entity = entities.get(table);
    assert.ok(entity, `缺少关键表：${table}`);
    const actual = new Set(entity.fields.map((field) => field.defKey));
    for (const field of fields) assert.ok(actual.has(field), `缺少关键字段：${table}.${field}`);
  }
  for (const [table, fields] of Object.entries(FORBIDDEN_FIELDS_BY_TABLE)) {
    const actual = new Set(entities.get(table).fields.map((field) => field.defKey));
    for (const field of fields) assert.ok(!actual.has(field), `不需要的字段仍存在：${table}.${field}`);
  }
  for (const tuple of FILE_REFERENCE_TUPLES) {
    const actual = new Set(entities.get(tuple.table).fields.map((field) => field.defKey));
    assert.ok(actual.has(tuple.id) && actual.has(tuple.hash), `文件引用必须成对：${tuple.table}`);
  }
});

test("Platform Support persists enforceable cross-owner composite foreign keys", async () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const { COMPOSITE_FK_CONTRACTS } = await import("./platform-support/model-policy.mjs");
  assert.deepEqual(COMPOSITE_FK_CONTRACTS, REQUIRED_COMPOSITE_FKS);

  for (const contract of REQUIRED_COMPOSITE_FKS) {
    const child = entities.get(contract.childTable);
    const parent = entities.get(contract.parentTable);
    assert.ok(child && parent, `${contract.name} 引用了未知表`);
    const childFields = child.fields.map((field) => field.defKey);
    const parentFields = parent.fields.map((field) => field.defKey);
    assert.ok(contract.childFields.every((field) => childFields.includes(field)), `${contract.name} 子字段不完整`);
    assert.ok(contract.parentFields.every((field) => parentFields.includes(field)), `${contract.name} 父字段不完整`);

    const childIndexable = child.indexes.some((index) => {
      const fields = index.fields.map((entry) => child.fields.find((field) => field.id === entry.fieldDefKey)?.defKey);
      return contract.childFields.every((field, offset) => fields[offset] === field);
    });
    assert.ok(childIndexable, `${contract.name} 缺少子表左前缀索引`);

    const parentUnique = parent.indexes.some((index) => {
      const fields = index.fields.map((entry) => parent.fields.find((field) => field.id === entry.fieldDefKey)?.defKey);
      return index.unique && fields.length === contract.parentFields.length
        && contract.parentFields.every((field, offset) => fields[offset] === field);
    });
    assert.ok(parentUnique, `${contract.name} 缺少父表精确唯一键`);

    const persisted = child.properties?.compositeForeignKeys?.find((item) => item.name === contract.name);
    assert.deepEqual(persisted, contract, `${contract.name} 未完整写入 PDManer 实体属性`);
  }
});

test("Platform Support CHECK policy freezes critical single-row invariants", async () => {
  const { CHECK_BINDINGS, CHECK_CONTRACTS } = await import("./platform-support/model-policy.mjs");
  assert.equal(Object.keys(CHECK_CONTRACTS).length, EXPECTED_CHECK_COUNT);
  assert.deepEqual(Object.keys(CHECK_BINDINGS).sort(), Object.keys(CHECK_CONTRACTS).sort());
  const fullPolicy = Object.keys(CHECK_CONTRACTS).sort().map((name) => ({
    name,
    expression: CHECK_CONTRACTS[name],
    tables: CHECK_BINDINGS[name],
  }));
  assert.equal(sha256(JSON.stringify(fullPolicy)), EXPECTED_CHECK_POLICY_DIGEST, "完整 CHECK Policy 摘要漂移");
  for (const [checkName, expected] of Object.entries(REQUIRED_CHECK_CONTRACTS)) {
    assert.equal(CHECK_CONTRACTS[checkName], expected.expression, `CHECK 表达式漂移：${checkName}`);
    assert.deepEqual(CHECK_BINDINGS[checkName], expected.tables, `CHECK 绑定漂移：${checkName}`);
  }
});

test("Platform Support relations are local, indexed and one-to-one cardinality is enforceable", () => {
  const model = readModel();
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  for (const child of model.entities) {
    const fieldsById = new Map(child.fields.map((field) => [field.id, field]));
    const primary = child.fields.find((field) => field.primaryKey);
    for (const relation of child.correlations) {
      assert.ok(entitiesById.has(relation.refEntity), `${child.defKey} 引用了库外实体`);
      const childField = fieldsById.get(relation.myField);
      const leftPrefix = childField.id === primary.id
        || child.indexes.some((index) => index.fields[0]?.fieldDefKey === childField.id);
      assert.ok(leftPrefix, `${child.defKey}.${childField.defKey} 缺少左前缀索引`);
      if (relation.myRows === "1") {
        const exactUnique = child.indexes.filter((index) => index.unique
          && index.fields.length === 1
          && index.fields[0].fieldDefKey === childField.id);
        assert.ok(childField.id === primary.id || exactUnique.length === 1,
          `${child.defKey}.${childField.defKey} 的 1:1 关系缺少单列唯一键`);
      }
    }
  }
});

test("Platform Support diagrams draw each relation once and each table owns one group", () => {
  const model = readModel();
  const primaryTables = model.diagrams.flatMap((diagram) => diagram.properties.primaryTables);
  assert.equal(primaryTables.length, 92);
  assert.equal(new Set(primaryTables).size, 92);
  assert.deepEqual([...primaryTables].sort(), [...APPROVED_TABLES].sort());
  const relationCount = model.entities.flatMap((entity) => entity.correlations).length;
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells)
    .filter((cell) => cell.shape === "erdRelation");
  assert.equal(relationCells.length, relationCount);
  assert.equal(new Set(relationCells.map((cell) => cell.originKey)).size, relationCount);
});

test("Platform Support model stores no connection or forbidden credential and card fields", () => {
  const model = readModel();
  assert.deepEqual(model.dbConn, []);
  assert.deepEqual(model.dbConns, []);
  assert.equal(model.profile.default.dbConn, "");
  const allFields = model.entities.flatMap((entity) => entity.fields.map((field) => `${entity.defKey}.${field.defKey}`));
  assert.ok(allFields.every((field) => !FORBIDDEN_FIELD_PATTERN.test(field)));
  assert.ok(!JSON.stringify(model).match(/jdbc:\/\/|BEGIN PRIVATE KEY|password\s*[=:]/iu));
});
