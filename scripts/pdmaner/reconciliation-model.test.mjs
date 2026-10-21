import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { renderSchema } from "./generate-oe-client-schemas.mjs";

// reconciliation 模型自 2026-10-20（需求 reconciliation-渠道资金对账切片1）脱离 Finance 生成器后直接维护 JSON；本测试只读 JSON 与建库
// SQL，核对 15 张表（12 张业务表 + 补偿事件 + paytech-messaging 消费幂等两表）、关键列、索引、库内关系、图组、标识唯一，以及
// reconciliation-schema.sql 与模型渲染逐字一致、测试 DDL 与需求待执行 DDL 的 CREATE 语句与 Schema 逐表一致。
const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const modelSource = "paytech/reconciliation/docs/database/reconciliation.pdma.json";
const model = JSON.parse(readFileSync(path.join(workspaceRoot, modelSource), "utf8"));
const schema = readFileSync(
  path.join(workspaceRoot, "paytech/reconciliation/docs/database/sql/reconciliation-schema.sql"), "utf8");
const testDdl = readFileSync(
  path.join(workspaceRoot, "paytech/reconciliation/paytech-reconciliation-test/src/test/resources/init/ddl.sql"), "utf8");
const requirementDdl = readFileSync(
  path.join(workspaceRoot, "requirements/20261020/reconciliation-渠道资金对账切片1/db/reconciliation/ddl.sql"), "utf8");
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

test("reconciliation keeps 15 tables in schema order and no database connection", () => {
  assert.deepEqual(model.entities.map((entity) => entity.defKey), [
    "t_clearing_account",
    "t_clearing_account_mid",
    "t_platform_txn",
    "t_channel_statement",
    "t_channel_statement_line",
    "t_cash_account",
    "t_bank_statement",
    "t_bank_statement_line",
    "t_expected_bank_movement",
    "t_bank_match",
    "t_recon_exception",
    "t_recon_resolution",
    "t_consumed_event",
    "t_consumed_aggregate_version",
    "t_compensation_event",
  ]);
  assert.equal(model.name, "reconciliation");
  assert.equal(model.dbConn.length, 0, "模型不得保存数据库连接");
  assert.equal(model.dbConns.length, 0, "模型不得保存数据库连接");
});

test("reconciliation-schema.sql is the exact rendering of the model; test and requirement DDL repeat every table", () => {
  assert.equal(schema, renderSchema(model, modelSource));
  assert.deepEqual(createStatements(testDdl), createStatements(schema));
  assert.deepEqual(createStatements(requirementDdl), createStatements(schema));
  assert.match(testDdl, /GRANT SELECT, INSERT, UPDATE, DELETE ON mor_reconciliation\.\* TO 'reconciliation_app'@'%';/u);
});

test("reconciliation columns follow the approved field-level design", () => {
  assert.deepEqual(model.entities.map((entity) => [entity.defKey, entity.fields.length]), [
    ["t_clearing_account", 13],
    ["t_clearing_account_mid", 5],
    ["t_platform_txn", 22],
    ["t_channel_statement", 22],
    ["t_channel_statement_line", 21],
    ["t_cash_account", 17],
    ["t_bank_statement", 19],
    ["t_bank_statement_line", 15],
    ["t_expected_bank_movement", 16],
    ["t_bank_match", 13],
    ["t_recon_exception", 22],
    ["t_recon_resolution", 20],
    ["t_consumed_event", 6],
    ["t_consumed_aggregate_version", 4],
    ["t_compensation_event", 15],
  ]);
  for (const [table, key, type, notNull] of [
    ["t_clearing_account", "settlement_cash_account_id", "BIGINT", false],
    ["t_clearing_account", "cost_tolerance_amount", "DECIMAL", true],
    ["t_platform_txn", "billing_fact_id", "BIGINT", true],
    ["t_platform_txn", "external_transaction_id", "VARCHAR", true],
    ["t_channel_statement", "settlement_date", "DATE", true],
    ["t_channel_statement", "reconciled_time", "DATETIME", false],
    ["t_channel_statement_line", "raw_attributes", "JSON", false],
    ["t_channel_statement_line", "platform_txn_id", "BIGINT", false],
    ["t_channel_statement_line", "actual_fee", "DECIMAL", false],
    ["t_cash_account", "account_vault_token", "VARCHAR", true],
    ["t_cash_account", "pending_action", "VARCHAR", false],
    ["t_bank_statement", "opening_balance", "DECIMAL", true],
    ["t_bank_statement_line", "matched_amount", "DECIMAL", true],
    ["t_expected_bank_movement", "classified_amount", "DECIMAL", true],
    ["t_bank_match", "resolution_id", "BIGINT", false],
    ["t_recon_exception", "blocking", "TINYINT", true],
    ["t_recon_exception", "active_slot", "TINYINT", false],
    ["t_recon_resolution", "amount", "DECIMAL", false],
  ]) {
    const field = fieldOf(table, key);
    assert.deepEqual([field.type, field.notNull], [type, notNull], `${table}.${key}`);
  }
  for (const entity of model.entities) {
    for (const field of entity.fields) {
      assert.ok(field.defName && field.comment, `${entity.defKey}.${field.defKey} 缺少显示名称或说明`);
      if (field.type === "DECIMAL") {
        assert.deepEqual([field.len, field.scale], [24, 8], `${entity.defKey}.${field.defKey} 金额精度`);
      }
    }
    assert.ok(!entity.fields.some((field) => /blind_index|key_version|masked|metadata_hash|record_version/u.test(field.defKey)),
      `${entity.defKey} 不得保存 Vault V1 不提供的列`);
  }
  assert.match(fieldOf("t_channel_statement", "status").comment,
    /允许值：RECEIVING、REJECTED、MATCHING、VOIDED、RECONCILED、CASH_CONFIRMED。$/u);
  assert.match(fieldOf("t_recon_resolution", "resolution_type").comment, /允许值：RE_MATCH、ACCEPT_TIMING_DIFFERENCE、CLASSIFY。$/u);
  assert.match(fieldOf("t_recon_exception", "source_type").comment,
    /允许值：CHANNEL_STATEMENT、CHANNEL_STATEMENT_LINE、PLATFORM_TXN、BANK_STATEMENT、BANK_STATEMENT_LINE、EXPECTED_BANK_MOVEMENT。$/u);
  assert.equal(fieldOf("t_clearing_account", "unmatched_wait_days").defaultValue, "3");
  assert.ok(fieldOf("t_consumed_aggregate_version", "aggregate_type").primaryKey);
  assert.ok(fieldOf("t_consumed_aggregate_version", "aggregate_id").primaryKey);
});

test("reconciliation indexes support the documented access paths", () => {
  for (const [table, key, unique, fields] of [
    ["t_clearing_account", "uk_clearing_account_code", true, ["clearing_account_code"]],
    ["t_clearing_account_mid", "uk_clearing_account_mid", true, ["mid_id", "currency"]],
    ["t_platform_txn", "uk_platform_txn_fact", true, ["billing_fact_id"]],
    ["t_platform_txn", "uk_platform_txn_external", true,
      ["clearing_account_id", "reference_type", "external_transaction_id", "direction"]],
    ["t_channel_statement", "uk_channel_statement_rev", true, ["clearing_account_id", "channel_batch_ref", "revision"]],
    ["t_channel_statement_line", "uk_channel_line_no", true, ["statement_id", "line_no"]],
    ["t_channel_statement_line", "idx_channel_line_ref", false,
      ["clearing_account_id", "reference_type", "external_transaction_id"]],
    ["t_channel_statement_line", "idx_channel_line_status", false, ["clearing_account_id", "match_status", "create_time"]],
    ["t_cash_account", "idx_cash_account_scope", false,
      ["entity_type", "entity_id", "currency", "account_purpose", "status"]],
    ["t_bank_statement", "uk_bank_statement_ref", true, ["cash_account_id", "statement_ref"]],
    ["t_bank_statement_line", "uk_bank_line_ref", true, ["cash_account_id", "bank_transaction_ref"]],
    ["t_expected_bank_movement", "uk_expected_movement_source", true, ["movement_type", "source_id"]],
    ["t_recon_exception", "uk_recon_exception_active", true, ["source_type", "source_id", "exception_type", "active_slot"]],
    ["t_recon_resolution", "uk_recon_resolution_no", true, ["exception_id", "resolution_no"]],
    ["t_compensation_event", "uk_comp_event_identity", true, ["event_key", "event_type", "sub_event_type"]],
  ]) {
    assert.deepEqual(indexOf(table, key), { unique, fields }, `${table}.${key}`);
  }
  for (const entity of model.entities) {
    for (const index of entity.indexes) {
      assert.match(index.defKey, index.unique ? /^uk_/u : /^idx_/u, `${entity.defKey}.${index.defKey} 前缀`);
    }
  }
  const total = model.entities.reduce((sum, entity) => sum + entity.indexes.length, 0);
  assert.equal(total, 34);
});

test("reconciliation relations are internal, point at primary keys and are indexed", () => {
  const relations = model.entities.flatMap((entity) => entity.correlations.map((relation) => relationKey(entity, relation)));
  assert.deepEqual(relations, [
    "t_clearing_account_mid.clearing_account_id -> t_clearing_account.id (n:1)",
    "t_platform_txn.clearing_account_id -> t_clearing_account.id (n:1)",
    "t_channel_statement.clearing_account_id -> t_clearing_account.id (n:1)",
    "t_channel_statement_line.statement_id -> t_channel_statement.id (n:1)",
    "t_channel_statement_line.platform_txn_id -> t_platform_txn.id (n:1)",
    "t_bank_statement.cash_account_id -> t_cash_account.id (n:1)",
    "t_bank_statement_line.statement_id -> t_bank_statement.id (n:1)",
    "t_expected_bank_movement.cash_account_id -> t_cash_account.id (n:1)",
    "t_bank_match.bank_statement_line_id -> t_bank_statement_line.id (n:1)",
    "t_bank_match.expected_movement_id -> t_expected_bank_movement.id (n:1)",
    "t_recon_resolution.exception_id -> t_recon_exception.id (n:1)",
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

test("reconciliation diagrams cover each table and each relation exactly once", () => {
  assert.deepEqual(model.diagrams.map((diagram) => [diagram.defName, diagram.properties.primaryTables.length]),
    [["交易层对账", 5], ["银行层对账", 5], ["异常与处置", 2], ["可靠执行与消费幂等", 3]]);
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

test("reconciliation model identifiers are unique", () => {
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
