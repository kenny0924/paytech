import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// ledger 模型自 2026-10-19（account 明细账、ledger 控制科目重构）脱离 Finance 生成器后直接维护 JSON；本测试只读 JSON 与建库 SQL，
// 保留生成器原有的表集合、库内关系、图组与标识门禁。建库 SQL 与模型逐字一致由 oe-client-model.test.mjs 核对。
const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const modelPath = path.join(workspaceRoot, "paytech/ledger/docs/database/ledger.pdma.json");
const schemaPath = path.join(workspaceRoot, "paytech/ledger/docs/database/sql/ledger-schema.sql");
const model = JSON.parse(readFileSync(modelPath, "utf8"));
const schema = readFileSync(schemaPath, "utf8");
const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
const entitiesByKey = new Map(model.entities.map((entity) => [entity.defKey, entity]));
const fieldOwner = new Map(model.entities.flatMap((entity) => entity.fields.map((field) => [field.id, entity])));

function relationKey(entity, relation) {
  const child = entity.fields.find((field) => field.id === relation.myField);
  const parent = entitiesById.get(relation.refEntity);
  const parentField = parent.fields.find((field) => field.id === relation.refField);
  return `${entity.defKey}.${child.defKey} -> ${parent.defKey}.${parentField.defKey}`;
}

test("ledger tables match the schema file and keep 32 tables", () => {
  const schemaTables = [...schema.matchAll(/^CREATE TABLE `(\w+)`/gmu)].map((match) => match[1]);
  assert.deepEqual(model.entities.map((entity) => entity.defKey), schemaTables);
  assert.equal(model.entities.length, 32);
  assert.equal(model.dbConn.length, 0, "模型不得保存数据库连接");
});

test("ledger control accounts: account_entry_id is a cross-database ID with an index but no relation", () => {
  const line = entitiesByKey.get("t_instruction_line");
  const entry = line.fields.find((field) => field.defKey === "account_entry_id");
  assert.ok(entry, "t_instruction_line.account_entry_id");
  assert.deepEqual([entry.type, entry.notNull, entry.primaryKey], ["BIGINT", false, false]);
  const keys = line.fields.map((field) => field.defKey);
  assert.equal(keys.indexOf("account_entry_id"), keys.indexOf("manual_adjustment_line_id") + 1);
  const index = line.indexes.find((item) => item.defKey === "idx_instr_line_entry");
  assert.ok(index && !index.unique, "idx_instr_line_entry");
  assert.deepEqual(index.fields.map((part) => part.fieldDefKey), [entry.id]);
  assert.ok(!line.correlations.some((relation) => relation.myField === entry.id), "跨库引用不画关系");

  const ownerType = entitiesByKey.get("t_ledger_account").fields.find((field) => field.defKey === "owner_type");
  assert.match(ownerType.comment, /允许值：OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY。$/u);
  const origin = entitiesByKey.get("t_accounting_instruction").fields.find((field) => field.defKey === "origin_type");
  assert.match(origin.comment, /允许值：AUTO、ACCOUNT_POSTING、MANUAL。$/u);
  const step = entitiesByKey.get("t_close_step").fields.find((field) => field.defKey === "step_type");
  assert.match(step.comment, /CHECK_ACCOUNT_POSTING_PROGRESS/u);
  assert.doesNotMatch(step.comment, /RECONCILE_ACCOUNT_BALANCES/u);
  const resolution = entitiesByKey.get("t_trial_balance_exception").fields.find((field) => field.defKey === "resolution_type");
  assert.doesNotMatch(resolution.comment, /PROJECTION_REBUILD/u);
});

test("ledger relations are internal, point at primary keys and are indexed", () => {
  for (const entity of model.entities) {
    for (const relation of entity.correlations) {
      const parent = entitiesById.get(relation.refEntity);
      assert.ok(parent, `${entity.defKey} 的关系必须指向本库表`);
      assert.ok(parent.fields.find((field) => field.id === relation.refField)?.primaryKey,
        `${relationKey(entity, relation)} 必须指向主键`);
      const indexed = entity.fields.find((field) => field.id === relation.myField).primaryKey
        || entity.indexes.some((index) => index.fields[0]?.fieldDefKey === relation.myField);
      assert.ok(indexed, `关系子字段缺少左前缀索引：${relationKey(entity, relation)}`);
    }
  }
});

test("ledger diagrams cover each table and each relation exactly once", () => {
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

test("ledger model identifiers are unique", () => {
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
