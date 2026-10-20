import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

// 第 03 步：counterparty 模型（t_customer 与本库补偿表）的逐表场景映射记录在 COUNTERPARTY_PHYSICAL_DECISIONS.md 第 3 节；
// 拆分前的 NOVA 现实场景（agent-docs）不含 Customer，第 13 步统一改写。
const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const modelPath = path.join(workspaceRoot, "paytech/counterparty/docs/database/counterparty.pdma.json");
const decisionsPath = path.join(workspaceRoot, "paytech/counterparty/docs/database/COUNTERPARTY_PHYSICAL_DECISIONS.md");

const expectedTables = Object.freeze(["t_customer", "t_compensation_event"]);

function mappingRows(source) {
  const start = source.indexOf("## 3. 逐表场景映射");
  const end = source.indexOf("## 4.", start);
  assert.ok(start >= 0 && end > start, "counterparty 物理决策缺少逐表场景映射章节");
  return source.slice(start, end).split(/\r?\n/u)
    .map((line) => line.match(/^\| `(t_[a-z0-9_]+)` \|(.+)\|$/u))
    .filter(Boolean)
    .map((match) => ({ table: match[1], cells: match[2].split("|").map((cell) => cell.trim()) }));
}

test("counterparty scenario has a generated model and physical decisions", () => {
  assert.ok(existsSync(modelPath), "counterparty 模型不存在");
  assert.ok(existsSync(decisionsPath), "counterparty 物理决策不存在");
});

test("counterparty scenario provides one concrete mapping row for every model table", () => {
  const rows = mappingRows(readFileSync(decisionsPath, "utf8"));
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  assert.deepEqual(rows.map((row) => row.table), expectedTables);
  assert.deepEqual(model.entities.map((entity) => entity.defKey), expectedTables);
  for (const row of rows) {
    assert.equal(row.cells.length, 2, `${row.table} 必须同时说明示例和写入/更新/冻结语义`);
    assert.ok(row.cells.every((cell) => cell.length > 3), `${row.table} 的场景说明过于空泛`);
  }
});

test("counterparty scenario keeps the D42 / S6 boundaries and no protected-value fields", () => {
  const source = readFileSync(decisionsPath, "utf8");
  for (const expected of ["D42", "S6", "uk(client_id, active_reference)", "STORED_CARD_INVALIDATION",
    "t_client_stored_card_setting` 归 client-master"]) {
    assert.ok(source.includes(expected), `counterparty 物理决策缺少关键边界：${expected}`);
  }
  const fieldKeys = new Set(JSON.parse(readFileSync(modelPath, "utf8")).entities
    .flatMap((entity) => entity.fields.map((field) => field.defKey)));
  for (const field of ["email_vault_token", "phone_vault_token", "consumer_profile_vault_token", "consumer_blind_index",
    "email_blind_index", "phone_blind_index", "stored_card_enabled"]) {
    assert.ok(!fieldKeys.has(field), `counterparty 模型不得包含 ${field}`);
  }
});

test("counterparty physical decisions link to the acquiring model decisions and the task card", () => {
  const source = readFileSync(decisionsPath, "utf8");
  for (const target of [
    "../../../acquiring/docs/database/ACQUIRING_PHYSICAL_DECISIONS.md",
    "../../../../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/03-acquiring-consumer.md",
  ]) {
    assert.ok(source.includes(target), `counterparty 物理决策缺少导航：${target}`);
    assert.ok(existsSync(path.resolve(path.dirname(decisionsPath), target)), `链接目标不存在：${target}`);
  }
});
