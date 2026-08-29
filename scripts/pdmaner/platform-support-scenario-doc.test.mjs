import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { APPROVED_TABLES, FORBIDDEN_TABLES } from "./platform-support-test-oracle/schema.oracle.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const scenarioPath = path.join(workspaceRoot, "agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md");
const modelPath = path.join(workspaceRoot, "platform-support-service/docs/database/platform-support.pdma.json");

function scenarioRows(content) {
  return content.split(/\n/u).flatMap((line) => {
    const match = line.match(/^\| `(t_[a-z0-9_]+)` \| (.*) \|$/u);
    return match ? [{ table: match[1], data: match[2] }] : [];
  });
}

function fieldAssignments(data) {
  return [...data.matchAll(/(?:^|[，； /])([a-z][a-z0-9_]*)=([^，； |/`]+)/gu)]
    .map((match) => ({ field: match[1], value: match[2] }));
}

function allowedValues(field) {
  return field.comment.match(/允许值：([^。]+)。/u)?.[1].split("、") ?? null;
}

function assertValueMatchesType(tableKey, field, value) {
  if (value === "NULL") return;
  const context = `${tableKey}.${field.defKey}=${value}`;
  if (["BIGINT", "INT", "INTEGER", "TINYINT", "SMALLINT", "BOOLEAN"].includes(field.type)) {
    assert.match(value, /^-?\d+$/u, `${context} 不是整数`);
  } else if (field.type === "DECIMAL") {
    assert.match(value, /^-?\d+(?:\.\d+)?$/u, `${context} 不是十进制数`);
  } else if (field.type === "DATE") {
    assert.match(value, /^\d{4}-\d{2}-\d{2}$/u, `${context} 不是 DATE`);
  } else if (field.type === "DATETIME") {
    assert.ok(!Number.isNaN(Date.parse(value)), `${context} 不是带时区的 ISO 8601 时刻`);
  }
}

test("Platform Support reality scenarios cover every approved table and no removed table", () => {
  assert.ok(existsSync(scenarioPath), `Platform Support 现实场景尚未生成：${scenarioPath}`);
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  assert.deepEqual([...new Set(rows.map((row) => row.table))].sort(), [...APPROVED_TABLES].sort());
  const mentionedTables = new Set(content.match(/\bt_[a-z][a-z0-9_]*\b/gu) ?? []);
  for (const table of mentionedTables) assert.ok(APPROVED_TABLES.includes(table), `现实场景引用未知物理表：${table}`);
  for (const table of FORBIDDEN_TABLES) assert.ok(!mentionedTables.has(table), `现实场景仍使用已删除表：${table}`);
});

test("Platform Support scenario assignments use real fields, primitive types and closed enums", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const entities = new Map(model.entities.map((entity) => [
    entity.defKey,
    new Map(entity.fields.map((field) => [field.defKey, field])),
  ]));
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  let assignmentCount = 0;
  for (const row of rows) {
    const fields = entities.get(row.table);
    assert.ok(fields, `场景使用未知表 ${row.table}`);
    for (const assignment of fieldAssignments(row.data)) {
      assignmentCount += 1;
      const field = fields.get(assignment.field);
      assert.ok(field, `${row.table} 使用不存在字段 ${assignment.field}`);
      assertValueMatchesType(row.table, field, assignment.value);
      const allowed = allowedValues(field);
      if (allowed && assignment.value !== "NULL") {
        assert.ok(allowed.includes(assignment.value),
          `${row.table}.${assignment.field}=${assignment.value} 不在允许值 ${allowed.join("、")} 中`);
      }
    }
  }
  assert.ok(assignmentCount >= 350, `场景字段赋值不足，当前仅 ${assignmentCount} 个`);
});

test("Platform Support scenarios demonstrate approved failure and recovery paths", () => {
  const content = readFileSync(scenarioPath, "utf8");
  for (const marker of [
    "SCN-PLT-01", "SCN-PLT-02", "SCN-PLT-03", "SCN-PLT-04",
    "SCN-PLT-05", "SCN-PLT-06", "SCN-PLT-07", "SCN-PLT-08",
    "AVAILABLE → QUARANTINED", "Credit Note", "supersedes_version_id",
    "晚到 COMPLAINT", "Replay", "Last-Known-Good", "ROLLBACK",
    "INLINE", "VAULT_TOKEN", "SECRET_REF", "lock_version",
  ]) {
    assert.match(content, new RegExp(marker, "u"), `现实场景缺少关键链路：${marker}`);
  }
  assert.doesNotMatch(content, /登录 MFA[^。\n]*OTP|跨库外键|通用 Job 平台/u);
});

test("Platform Support scenarios retain the minimum-data security boundary", () => {
  const content = readFileSync(scenarioPath, "utf8");
  assert.match(content, /Vault Token/u);
  assert.match(content, /File ID.*Content Hash/su);
  assert.match(content, /不保存密码/u);
  assert.match(content, /不保存[^。\n]*(?:PAN|CVV)/u);
  assert.doesNotMatch(content, /vault_record_version=|vault_metadata_hash=|password=|private_key=|\bpan=|\bcvv=/iu);
});
