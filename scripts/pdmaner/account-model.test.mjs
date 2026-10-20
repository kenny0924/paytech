import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// account 模型脱离 Finance 生成器后直接维护 JSON；本测试只读 JSON，保留生成器原有的图组与关系结构门禁。
const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const modelPath = path.join(workspaceRoot, "paytech/account/docs/database/account.pdma.json");
const schemaPath = path.join(workspaceRoot, "paytech/account/docs/database/sql/account-schema.sql");
const model = JSON.parse(readFileSync(modelPath, "utf8"));
const schema = readFileSync(schemaPath, "utf8");
const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
const fieldOwner = new Map(model.entities.flatMap((entity) => entity.fields.map((field) => [field.id, entity])));

function relationKey(entity, relation) {
  const child = entity.fields.find((field) => field.id === relation.myField);
  const parent = entitiesById.get(relation.refEntity);
  const parentField = parent.fields.find((field) => field.id === relation.refField);
  return `${entity.defKey}.${child.defKey} -> ${parent.defKey}.${parentField.defKey}`;
}

// 已处理事实只从批次侧经 uk_posting_fact (source_type, fact_id, fact_version) 进入，没有按 posting_id 反查事实的访问路径，不建索引。
const UNINDEXED_RELATIONS = new Set(["t_balance_applied_fact.posting_id -> t_account_posting.id"]);

test("account tables match the subledger schema", () => {
  assert.deepEqual(model.entities.map((entity) => entity.defKey), [
    "t_owner_account",
    "t_owner_account_no_sequence",
    "t_account_posting_stream",
    "t_account_posting",
    "t_account_entry",
    "t_balance_applied_fact",
    "t_balance_movement",
    "t_consumed_event",
    "t_consumed_aggregate_version",
    "t_compensation_event",
  ]);
  assert.deepEqual(model.diagrams.map((diagram) => diagram.defName), ["明细账：账户、批次与流水", "可靠执行与消费幂等"]);
});

// Schema 手工维护：逐表核对列与索引名称、顺序和模型一致。
test("account schema file matches the model columns and indexes", () => {
  const tables = [...schema.matchAll(/^CREATE TABLE `(\w+)` \(\n([\s\S]*?)\n\) ENGINE/gmu)];
  assert.deepEqual(tables.map((match) => match[1]), model.entities.map((entity) => entity.defKey));
  for (const [, table, body] of tables) {
    const entity = model.entities.find((item) => item.defKey === table);
    const columns = [...body.matchAll(/^  `(\w+)` /gmu)].map((match) => match[1]);
    const indexes = [...body.matchAll(/^  (?:UNIQUE )?KEY `(\w+)`/gmu)].map((match) => match[1]);
    assert.deepEqual(columns, entity.fields.map((field) => field.defKey), `${table} 列与模型不一致`);
    assert.deepEqual(indexes, entity.indexes.map((index) => index.defKey), `${table} 索引与模型不一致`);
  }
});

// 可读账户号：表内唯一，序列按主体 + 币种 + 科目代码唯一（2026-10-19 科目分级与可读账户号决定第 7 条）。
test("owner account number is unique and allocated per entity, currency and account code", () => {
  const indexFields = (table, indexKey) => {
    const entity = model.entities.find((item) => item.defKey === table);
    const index = entity.indexes.find((item) => item.defKey === indexKey);
    assert.ok(index?.unique, `${table}.${indexKey} 必须是唯一索引`);
    return index.fields.map((field) => entity.fields.find((item) => item.id === field.fieldDefKey).defKey);
  };
  assert.deepEqual(indexFields("t_owner_account", "uk_owner_account_no"), ["account_no"]);
  assert.deepEqual(indexFields("t_owner_account_no_sequence", "uk_owner_account_no_seq"),
    ["entity_type", "entity_id", "currency", "account_code"]);
});

test("account relations are internal, point at primary keys and are indexed", () => {
  const relations = model.entities.flatMap((entity) => entity.correlations.map((relation) => relationKey(entity, relation)));
  assert.deepEqual(relations, [
    "t_account_entry.posting_id -> t_account_posting.id",
    "t_account_entry.account_id -> t_owner_account.id",
    "t_account_entry.reverses_entry_id -> t_account_entry.id",
    "t_balance_applied_fact.posting_id -> t_account_posting.id",
    "t_balance_movement.posting_id -> t_account_posting.id",
  ]);
  for (const entity of model.entities) {
    for (const relation of entity.correlations) {
      const parent = entitiesById.get(relation.refEntity);
      assert.ok(parent.fields.find((field) => field.id === relation.refField).primaryKey, `${relationKey(entity, relation)} 必须指向主键`);
      if (UNINDEXED_RELATIONS.has(relationKey(entity, relation))) {
        continue;
      }
      const indexed = entity.indexes.some((index) => index.fields[0]?.fieldDefKey === relation.myField);
      assert.ok(indexed, `关系子字段缺少左前缀索引：${relationKey(entity, relation)}`);
    }
  }
});

test("account diagrams cover each table and each relation exactly once", () => {
  const primaryTables = model.diagrams.flatMap((diagram) => diagram.properties.primaryTables);
  assert.deepEqual([...primaryTables].sort(), model.entities.map((entity) => entity.defKey).sort());
  const relationIds = model.entities.flatMap((entity) => entity.correlations.map((relation) => relation.id));
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells).filter((cell) => cell.shape === "erdRelation");
  assert.deepEqual(relationCells.map((cell) => cell.originKey).sort(), [...relationIds].sort());

  for (const diagram of model.diagrams) {
    const tableCells = new Map(diagram.canvasData.cells.filter((cell) => cell.shape === "table").map((cell) => [cell.id, cell]));
    assert.deepEqual(
      [...tableCells.values()].map((cell) => entitiesById.get(cell.originKey).defKey).sort(),
      [...diagram.properties.primaryTables, ...diagram.properties.referenceTables].sort(),
      `${diagram.defKey} 表格单元与图组表清单不一致`,
    );
    for (const cell of diagram.canvasData.cells.filter((item) => item.shape === "erdRelation")) {
      for (const end of [cell.source, cell.target]) {
        const [fieldId] = end.port.split("%");
        assert.equal(fieldOwner.get(fieldId)?.id, tableCells.get(end.cell)?.originKey, `${diagram.defKey} 关系端点与表格单元不一致`);
      }
    }
  }
});

test("account model identifiers are unique", () => {
  const ids = [
    ...model.entities.flatMap((entity) => [entity.id, ...entity.correlations.map((relation) => relation.id)]),
    ...model.diagrams.flatMap((diagram) => [diagram.id, ...diagram.canvasData.cells.map((cell) => cell.id)]),
  ];
  assert.equal(new Set(ids).size, ids.length);
});
