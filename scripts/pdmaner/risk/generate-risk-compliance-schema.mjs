import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const workspaceUrl = new URL("../../../", import.meta.url);
const modelUrl = new URL("risk-compliance-service/docs/database/risk-compliance.pdma.json", workspaceUrl);
const schemaUrl = new URL("risk-compliance-service/docs/database/sql/risk-compliance-schema.sql", workspaceUrl);

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function quote(value) {
  invariant(typeof value === "string" && !value.includes("\0"), "SQL COMMENT 必须是无 NUL 的字符串");
  return `'${value.replaceAll("\\", "\\\\").replaceAll("'", "''")}'`;
}

function sqlType(field) {
  invariant(/^[A-Z]+$/u.test(field.type), `非法字段类型：${field.defKey}.${field.type}`);
  if (!field.len) {
    invariant(!field.scale, `无长度字段不得定义精度：${field.defKey}`);
    return field.type;
  }
  invariant(Number.isInteger(field.len) && field.len > 0, `非法字段长度：${field.defKey}`);
  if (field.scale) invariant(Number.isInteger(field.scale) && field.scale > 0, `非法字段精度：${field.defKey}`);
  return `${field.type}(${field.len}${field.scale ? `,${field.scale}` : ""})`;
}

function exactStringSql(field) {
  const characterSet = field.extProps?.characterSet;
  const collation = field.extProps?.collation;
  invariant(Boolean(characterSet) === Boolean(collation), `字符集与排序规则必须成对声明：${field.defKey}`);
  if (!characterSet) return "";
  invariant(field.type === "VARCHAR", `仅 VARCHAR 字段可声明精确字符串规则：${field.defKey}`);
  invariant(characterSet === "ascii" && collation === "ascii_bin", `Risk 精确字符串只允许 ascii/ascii_bin：${field.defKey}`);
  return ` CHARACTER SET ${characterSet} COLLATE ${collation}`;
}

function validateModel(model) {
  invariant(Array.isArray(model.entities) && model.entities.length === 71, "Risk Schema 必须直接覆盖最终 71 表 PDManer");
  const tableNames = new Set();
  for (const entity of model.entities) {
    invariant(/^t_[a-z][a-z0-9_]*$/u.test(entity.defKey), `非法 Risk 物理表名：${entity.defKey}`);
    invariant(!tableNames.has(entity.defKey), `重复 Risk 物理表名：${entity.defKey}`);
    tableNames.add(entity.defKey);
    invariant(/[\u3400-\u9fff]/u.test(entity.comment), `表缺少中文注释：${entity.defKey}`);
    invariant(Array.isArray(entity.fields) && entity.fields.length > 0, `表没有字段：${entity.defKey}`);
    const fieldNames = new Set();
    const fieldIds = new Set();
    let primaryKeyCount = 0;
    for (const field of entity.fields) {
      invariant(/^[a-z][a-z0-9_]*$/u.test(field.defKey), `非法字段名：${entity.defKey}.${field.defKey}`);
      invariant(!fieldNames.has(field.defKey), `重复字段名：${entity.defKey}.${field.defKey}`);
      invariant(!fieldIds.has(field.id), `重复字段 ID：${entity.defKey}.${field.id}`);
      fieldNames.add(field.defKey);
      fieldIds.add(field.id);
      invariant(field.autoIncrement === false, `Risk Snowflake 主键禁止 AUTO_INCREMENT：${entity.defKey}.${field.defKey}`);
      invariant(typeof field.notNull === "boolean", `字段 Nullability 缺失：${entity.defKey}.${field.defKey}`);
      invariant(typeof field.defaultValue === "string", `字段 Default 缺失：${entity.defKey}.${field.defKey}`);
      invariant(/[\u3400-\u9fff]/u.test(field.comment), `字段缺少中文注释：${entity.defKey}.${field.defKey}`);
      if (field.primaryKey) primaryKeyCount += 1;
      sqlType(field);
      exactStringSql(field);
    }
    invariant(primaryKeyCount > 0, `表必须有主键：${entity.defKey}`);

    const indexNames = new Set();
    for (const index of entity.indexes ?? []) {
      invariant(index.unique ? index.defKey.startsWith("uk_") : index.defKey.startsWith("idx_"), `索引前缀错误：${entity.defKey}.${index.defKey}`);
      invariant(!indexNames.has(index.defKey), `重复索引名：${entity.defKey}.${index.defKey}`);
      indexNames.add(index.defKey);
      invariant(/[\u3400-\u9fff]/u.test(index.comment), `索引缺少中文注释：${entity.defKey}.${index.defKey}`);
      invariant(Array.isArray(index.fields) && index.fields.length > 0, `索引没有字段：${entity.defKey}.${index.defKey}`);
      for (const { fieldDefKey } of index.fields) {
        invariant(fieldIds.has(fieldDefKey), `索引引用未知字段：${entity.defKey}.${index.defKey}.${fieldDefKey}`);
      }
    }
  }
  invariant(tableNames.has("t_settle_control_state_change"), "Risk Schema 缺少特殊物理表 t_settle_control_state_change");
}

function renderEntity(entity) {
  const definitions = entity.fields.map((field) => (
    `  \`${field.defKey}\` ${sqlType(field)}${exactStringSql(field)} ${field.notNull ? "NOT NULL" : "NULL"}`
    + (field.defaultValue === "" ? "" : ` DEFAULT ${field.defaultValue}`)
    + ` COMMENT ${quote(field.comment)}`
  ));
  const primaryKeys = entity.fields.filter((field) => field.primaryKey).map((field) => `\`${field.defKey}\``);
  definitions.push(`  PRIMARY KEY (${primaryKeys.join(", ")})`);

  const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
  for (const index of entity.indexes ?? []) {
    const fields = index.fields.map(({ fieldDefKey }) => `\`${fieldsById.get(fieldDefKey)}\``);
    definitions.push(
      `  ${index.unique ? "UNIQUE KEY" : "KEY"} \`${index.defKey}\` (${fields.join(", ")}) COMMENT ${quote(index.comment)}`,
    );
  }
  const body = definitions.map((definition, index) => `${definition}${index < definitions.length - 1 ? "," : ""}`).join("\n");
  return `CREATE TABLE \`${entity.defKey}\` (\n${body}\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=${quote(entity.comment)};`;
}

export function renderRiskSchema(model) {
  validateModel(model);
  return [
    "-- Code-MoR Risk & Compliance complete schema generated from the approved PDManer model.",
    "-- 只适用于人工审核后的空 Risk 数据库；不得作为应用启动迁移或已有数据库升级脚本。",
    "-- Source: risk-compliance-service/docs/database/risk-compliance.pdma.json",
    "",
    ...model.entities.flatMap((entity) => [renderEntity(entity), ""]),
  ].join("\n");
}

function main() {
  const args = process.argv.slice(2);
  invariant(args.length <= 1 && (args.length === 0 || args[0] === "--check"), "Usage: node generate-risk-compliance-schema.mjs [--check]");
  const model = JSON.parse(readFileSync(modelUrl, "utf8"));
  const expected = renderRiskSchema(model);
  if (args[0] === "--check") {
    if (!existsSync(schemaUrl) || readFileSync(schemaUrl, "utf8") !== expected) {
      process.stderr.write("Risk Schema is stale; run generate-risk-compliance-schema.mjs\n");
      process.exitCode = 1;
      return;
    }
    process.stdout.write(`Risk Schema is up to date (${model.entities.length} tables)\n`);
    return;
  }
  mkdirSync(dirname(fileURLToPath(schemaUrl)), { recursive: true });
  writeFileSync(schemaUrl, expected, "utf8");
  process.stdout.write(`Wrote Risk Schema (${model.entities.length} tables): ${fileURLToPath(schemaUrl)}\n`);
}

main();
