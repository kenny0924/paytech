import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { renderSchema } from "./generate-oe-client-schemas.mjs";

// billing 模型自 2026-10-19（需求 billing-计费事实模型重构与渠道成本迁出）脱离 Finance 生成器后直接维护 JSON；本测试只读 JSON 与建库
// SQL，核对 11 张表、关键列、索引、库内关系、图组、标识唯一，以及 billing-schema.sql 与模型渲染逐字一致、测试 DDL 与 Schema 逐表一致。
const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const modelSource = "paytech/billing/docs/database/billing.pdma.json";
const model = JSON.parse(readFileSync(path.join(workspaceRoot, modelSource), "utf8"));
const schema = readFileSync(path.join(workspaceRoot, "paytech/billing/docs/database/sql/billing-schema.sql"), "utf8");
const testDdl = readFileSync(
  path.join(workspaceRoot, "paytech/billing/paytech-billing-test/src/test/resources/init/ddl.sql"), "utf8");
const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
const entitiesByKey = new Map(model.entities.map((entity) => [entity.defKey, entity]));
const fieldOwner = new Map(model.entities.flatMap((entity) => entity.fields.map((field) => [field.id, entity])));

function fieldOf(table, key) {
  const field = entitiesByKey.get(table).fields.find((item) => item.defKey === key);
  assert.ok(field, `${table}.${key}`);
  return field;
}

function indexOf(table, key) {
  const entity = entitiesByKey.get(table);
  const index = entity.indexes.find((item) => item.defKey === key);
  assert.ok(index, `${table}.${key}`);
  return {
    unique: index.unique,
    fields: index.fields.map((part) => entity.fields.find((field) => field.id === part.fieldDefKey).defKey),
  };
}

function relationKey(entity, relation) {
  const child = entity.fields.find((field) => field.id === relation.myField);
  const parent = entitiesById.get(relation.refEntity);
  const parentField = parent.fields.find((field) => field.id === relation.refField);
  return `${entity.defKey}.${child.defKey} -> ${parent.defKey}.${parentField.defKey} (${relation.myRows}:${relation.refRows})`;
}

function createStatements(sql) {
  return [...sql.matchAll(/^CREATE TABLE `(\w+)` \([\s\S]*?\) ENGINE=[^\n]*;$/gmu)].map((match) => [match[1], match[0]]);
}

test("billing keeps 11 tables in schema order and no database connection", () => {
  assert.deepEqual(model.entities.map((entity) => entity.defKey), [
    "t_currency_spec",
    "t_billing_fact",
    "t_billing_fact_ref",
    "t_billing_fact_pricing",
    "t_billing_fact_line",
    "t_billing_fact_line_link",
    "t_business_calendar",
    "t_business_calendar_day",
    "t_consumed_event",
    "t_consumed_aggregate_version",
    "t_compensation_event",
  ]);
  assert.equal(model.dbConn.length, 0, "模型不得保存数据库连接");
  assert.equal(model.dbConns.length, 0, "模型不得保存数据库连接");
});

test("billing-schema.sql is the exact rendering of the model and the test DDL repeats every table", () => {
  assert.equal(schema, renderSchema(model, modelSource));
  assert.deepEqual(createStatements(testDdl), createStatements(schema));
  assert.match(testDdl, /INSERT INTO `t_business_calendar`[\s\S]*'DEFAULT'/u);
});

test("billing fact columns follow the approved design", () => {
  assert.equal(entitiesByKey.get("t_billing_fact").fields.length, 25);
  assert.equal(entitiesByKey.get("t_billing_fact_ref").fields.length, 7);
  assert.equal(entitiesByKey.get("t_billing_fact_line").fields.length, 20);
  for (const [table, key, type, notNull] of [
    ["t_billing_fact", "origin_type", "VARCHAR", true],
    ["t_billing_fact", "business_time", "DATETIME", true],
    ["t_billing_fact", "client_id", "BIGINT", false],
    ["t_billing_fact", "parent_fact_id", "BIGINT", false],
    ["t_billing_fact_ref", "ref_id", "VARCHAR", true],
    ["t_billing_fact_ref", "external_reference_type", "VARCHAR", false],
    ["t_billing_fact_ref", "external_transaction_id", "VARCHAR", false],
    ["t_billing_fact_pricing", "commercial_price_id", "BIGINT", true],
    ["t_billing_fact_line", "target_bucket", "VARCHAR", false],
    ["t_billing_fact_line", "source_evidence", "JSON", false],
    ["t_billing_fact_line_link", "target_line_id", "BIGINT", true],
    ["t_business_calendar_day", "calendar_date", "DATE", true],
  ]) {
    const field = fieldOf(table, key);
    assert.deepEqual([field.type, field.notNull], [type, notNull], `${table}.${key}`);
  }
  assert.equal(fieldOf("t_billing_fact", "fact_version").defaultValue, "0");
  assert.match(fieldOf("t_billing_fact_ref", "ref_type").comment, /TICKET、FX_DEAL、FX_QUOTE、CHANNEL_TRANSACTION_REF、RECON_RESOLUTION。$/u);
  assert.deepEqual(entitiesByKey.get("t_billing_fact_ref").fields.map((field) => field.defKey),
    ["id", "fact_id", "ref_type", "ref_id", "external_reference_type", "external_transaction_id", "create_time"]);
  assert.equal(fieldOf("t_billing_fact_ref", "external_transaction_id").len, 128);
  assert.match(fieldOf("t_billing_fact_line", "component_type").comment, /CHANNEL_COST \+ ADJUSTMENT/u);
  assert.ok(fieldOf("t_business_calendar", "calendar_code").primaryKey);
  assert.ok(fieldOf("t_billing_fact_pricing", "fact_id").primaryKey);
  for (const entity of model.entities) {
    assert.ok(!entity.fields.some((field) => /economic_fact|economics/u.test(field.defKey)), `${entity.defKey} 残留旧列`);
  }
});

test("billing indexes support the documented access paths", () => {
  assert.deepEqual(indexOf("t_billing_fact", "uk_fact_source"), { unique: true, fields: ["source_system", "source_event_key"] });
  assert.deepEqual(indexOf("t_billing_fact", "idx_fact_book_oe"), { unique: false, fields: ["entity_no", "currency", "id"] });
  assert.deepEqual(indexOf("t_billing_fact", "idx_fact_book_fe"),
    { unique: false, fields: ["finance_entity_id", "currency", "id"] });
  assert.deepEqual(indexOf("t_billing_fact_ref", "uk_fact_ref"), { unique: true, fields: ["fact_id", "ref_type"] });
  assert.deepEqual(indexOf("t_billing_fact_ref", "idx_fact_ref_lookup"), { unique: false, fields: ["ref_type", "ref_id"] });
  assert.deepEqual(indexOf("t_billing_fact_line", "uk_fact_line"), { unique: true, fields: ["fact_id", "line_no"] });
  assert.deepEqual(indexOf("t_billing_fact_line_link", "idx_fact_line_link_target"),
    { unique: false, fields: ["target_line_id"] });
  assert.deepEqual(indexOf("t_business_calendar_day", "uk_calendar_day"),
    { unique: true, fields: ["calendar_code", "calendar_date"] });
  const total = model.entities.reduce((sum, entity) => sum + entity.indexes.length, 0);
  assert.equal(total, 21);
});

test("billing relations are internal, point at primary keys and are indexed", () => {
  const relations = model.entities.flatMap((entity) => entity.correlations.map((relation) => relationKey(entity, relation)));
  assert.deepEqual(relations, [
    "t_billing_fact_ref.fact_id -> t_billing_fact.id (n:1)",
    "t_billing_fact_pricing.fact_id -> t_billing_fact.id (1:1)",
    "t_billing_fact_line.fact_id -> t_billing_fact.id (n:1)",
    "t_billing_fact_line_link.source_line_id -> t_billing_fact_line.id (n:1)",
    "t_billing_fact_line_link.target_line_id -> t_billing_fact_line.id (n:1)",
    "t_business_calendar_day.calendar_code -> t_business_calendar.calendar_code (n:1)",
  ]);
  for (const entity of model.entities) {
    for (const relation of entity.correlations) {
      const parent = entitiesById.get(relation.refEntity);
      assert.ok(parent.fields.find((field) => field.id === relation.refField).primaryKey,
        `${relationKey(entity, relation)} 必须指向主键`);
      const indexed = entity.fields.find((field) => field.id === relation.myField).primaryKey
        || entity.indexes.some((index) => index.fields[0]?.fieldDefKey === relation.myField);
      assert.ok(indexed, `关系子字段缺少左前缀索引：${relationKey(entity, relation)}`);
    }
  }
});

test("billing diagrams cover each table and each relation exactly once", () => {
  assert.deepEqual(model.diagrams.map((diagram) => [diagram.defName, diagram.properties.primaryTables.length]),
    [["计费事实", 5], ["参考数据副本", 3], ["可靠执行与消费幂等", 3]]);
  const primaryTables = model.diagrams.flatMap((diagram) => diagram.properties.primaryTables);
  assert.deepEqual([...primaryTables].sort(), model.entities.map((entity) => entity.defKey).sort());
  const relationIds = model.entities.flatMap((entity) => entity.correlations.map((relation) => relation.id));
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells)
    .filter((cell) => cell.shape === "erdRelation");
  assert.deepEqual(relationCells.map((cell) => cell.originKey).sort(), [...relationIds].sort());

  for (const diagram of model.diagrams) {
    const tableCells = new Map(diagram.canvasData.cells.filter((cell) => cell.shape === "table")
      .map((cell) => [cell.id, cell]));
    assert.deepEqual(
      [...tableCells.values()].map((cell) => entitiesById.get(cell.originKey).defKey).sort(),
      [...diagram.properties.primaryTables, ...diagram.properties.referenceTables].sort(),
      `${diagram.defKey} 表格单元与图组表清单不一致`,
    );
    for (const cell of diagram.canvasData.cells.filter((item) => item.shape === "erdRelation")) {
      for (const end of [cell.source, cell.target]) {
        const [fieldId] = end.port.split("%");
        assert.equal(fieldOwner.get(fieldId)?.id, tableCells.get(end.cell)?.originKey,
          `${diagram.defKey} 关系端点与表格单元不一致`);
      }
    }
  }
});

test("billing model identifiers are unique", () => {
  const ids = [
    ...model.entities.flatMap((entity) => [
      entity.id,
      ...entity.fields.map((field) => field.id),
      ...entity.indexes.flatMap((index) => [index.id, ...index.fields.map((part) => part.id)]),
      ...entity.correlations.map((relation) => relation.id),
    ]),
    ...model.diagrams.flatMap((diagram) => [diagram.id, ...diagram.canvasData.cells.map((cell) => cell.id)]),
  ];
  assert.equal(new Set(ids).size, ids.length);
});
