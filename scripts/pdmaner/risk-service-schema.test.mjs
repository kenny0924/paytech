import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";

import { RISK_SCHEMA_PATHS, RISK_SERVICES, readRiskServiceModel, readRiskUnionModel } from "./risk-service-models.mjs";

// Step 06: one Schema slice per service model (paytech/<service>/docs/database/sql/<service>-schema.sql).
const workspace = new URL("../../", import.meta.url);
const generatorUrl = new URL("./risk/generate-risk-compliance-schema.mjs", import.meta.url);
const EXPECTED_TABLE_COUNTS = Object.freeze({ "risk-decision": 54, compliance: 24, dispute: 8 });
const model = readRiskUnionModel();

function readSchema(service) {
  assert.ok(existsSync(RISK_SCHEMA_PATHS[service]), `${service} Schema SQL 尚未生成`);
  return readFileSync(RISK_SCHEMA_PATHS[service], "utf8");
}

function quote(value) {
  return `'${value.replaceAll("'", "''")}'`;
}

function sqlType(field) {
  if (!field.len) return field.type;
  return `${field.type}(${field.len}${field.scale ? `,${field.scale}` : ""})`;
}

function exactStringSql(field) {
  if (!field.extProps?.characterSet) return "";
  return ` CHARACTER SET ${field.extProps.characterSet} COLLATE ${field.extProps.collation}`;
}

function expectedDefinitions(entity) {
  const definitions = entity.fields.map((field) => (
    `  \`${field.defKey}\` ${sqlType(field)}${exactStringSql(field)} ${field.notNull ? "NOT NULL" : "NULL"}`
    + (field.defaultValue === "" ? "" : ` DEFAULT ${field.defaultValue}`)
    + ` COMMENT ${quote(field.comment)}`
  ));
  const primaryKeys = entity.fields.filter((field) => field.primaryKey).map((field) => `\`${field.defKey}\``);
  assert.ok(primaryKeys.length > 0, `${entity.defKey} 必须有主键`);
  definitions.push(`  PRIMARY KEY (${primaryKeys.join(", ")})`);

  const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
  for (const index of entity.indexes) {
    const indexFields = index.fields.map(({ fieldDefKey }) => {
      const fieldName = fieldsById.get(fieldDefKey);
      assert.ok(fieldName, `${entity.defKey}.${index.defKey} 引用了未知字段`);
      return `\`${fieldName}\``;
    });
    definitions.push(
      `  ${index.unique ? "UNIQUE KEY" : "KEY"} \`${index.defKey}\` (${indexFields.join(", ")}) COMMENT ${quote(index.comment)}`,
    );
  }
  return definitions.map((definition, index) => `${definition}${index < definitions.length - 1 ? "," : ""}`);
}

function parseTables(sql) {
  return [...sql.matchAll(
    /CREATE TABLE `([^`]+)` \(\n([\s\S]*?)\n\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='((?:''|[^'])*)';/gu,
  )].map((match) => ({
    name: match[1],
    definitions: match[2].split("\n"),
    comment: match[3].replaceAll("''", "'"),
  }));
}

function executableSql(sql) {
  return sql
    .replace(/^--.*$/gmu, "")
    .replace(/'(?:''|[^'])*'/gu, "''");
}

test("Risk Schemas create exactly each service model's tables in deterministic order", () => {
  for (const service of RISK_SERVICES) {
    const serviceModel = readRiskServiceModel(service);
    const tables = parseTables(readSchema(service));
    assert.equal(serviceModel.entities.length, EXPECTED_TABLE_COUNTS[service], service);
    assert.deepEqual(tables.map(({ name }) => name), serviceModel.entities.map(({ defKey }) => defKey), service);
  }
  assert.ok(parseTables(readSchema("compliance")).some(({ name }) => name === "t_settle_control_state_change"));
});

test("every Risk SQL column, default, Chinese comment, primary key and index matches PDManer", () => {
  for (const service of RISK_SERVICES) {
    const tables = new Map(parseTables(readSchema(service)).map((table) => [table.name, table]));
    for (const entity of readRiskServiceModel(service).entities) {
      const table = tables.get(entity.defKey);
      assert.ok(table, `${service} Schema 缺少表：${entity.defKey}`);
      assert.equal(table.comment, entity.comment, `${entity.defKey} 表注释漂移`);
      assert.deepEqual(table.definitions, expectedDefinitions(entity), `${entity.defKey} 定义漂移`);
    }
  }
});

test("rule configuration stores its contract and field codes while preserving legacy composition tables", () => {
  const tables = new Map(model.entities.map(entity => [entity.defKey, entity]));
  const fields = name => new Map(tables.get(name).fields.map(field => [field.defKey, field]));
  const version = fields("t_rule_set_version");
  for (const [name, type, len] of [["input_contract_code", "VARCHAR", 64], ["input_contract_version", "INT", null]]) {
    const field = version.get(name);
    assert.equal(field.type, type);
    assert.equal(Number(field.len) || null, len);
    assert.equal(field.notNull, true);
    assert.equal(field.defaultValue, "");
  }
  for (const [name, removedIndex] of [["t_rule_operand", "idx_rule_op_input"], ["t_rule_action_parameter", "idx_rule_param_input"]]) {
    const columns = fields(name);
    assert.equal(columns.has("input_field_id"), false);
    assert.equal(columns.get("field_code").type, "VARCHAR");
    assert.equal(Number(columns.get("field_code").len), 64);
    assert.equal(columns.get("field_code").notNull, false);
    assert.equal(tables.get(name).indexes.some(index => index.defKey === removedIndex), false);
  }
  for (const name of ["t_decision_point_version", "t_decision_point_input_field", "t_decision_point_rule_set"]) {
    assert.ok(tables.has(name), `${name} 仍供历史闭包读取`);
  }
});

test("Risk command idempotency table is deleted from every split model and Schema (S10)", () => {
  // Step 06: t_command_idempotency has no code and is deleted (design/03 §2.2); its former ascii_bin key no longer exists.
  for (const service of RISK_SERVICES) {
    assert.ok(!readRiskServiceModel(service).entities.some(({ defKey }) => defKey === "t_command_idempotency"), service);
    assert.doesNotMatch(readSchema(service), /t_command_idempotency|CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT [^\n]*幂等/u, service);
  }
});

test("Risk empty-database Schema contains no migration, seed or physical business constraints", () => {
  for (const service of RISK_SERVICES) {
    const sql = readSchema(service);
    assert.match(sql, /只适用于人工审核后的空 mor_[a-z_]+ 数据库/u);
    const executable = executableSql(sql);
    assert.doesNotMatch(executable, /\b(?:DROP|TRUNCATE|INSERT|AUTO_INCREMENT)\b/iu);
    assert.doesNotMatch(executable, /\bCHECK\s*\(/iu);
    assert.doesNotMatch(executable, /\bFOREIGN\s+KEY\b/iu);
  }
});

test("Risk Schema generator --check proves the committed SQL is current", () => {
  assert.ok(existsSync(generatorUrl), "Risk Schema 生成器尚未实现");
  const result = spawnSync(process.execPath, [fileURLToPath(generatorUrl), "--check"], {
    cwd: fileURLToPath(workspace),
    encoding: "utf8",
  });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  for (const service of RISK_SERVICES) {
    assert.match(result.stdout, new RegExp(`${service} Schema is up to date \\(${EXPECTED_TABLE_COUNTS[service]} tables\\)`, "u"));
  }
});
