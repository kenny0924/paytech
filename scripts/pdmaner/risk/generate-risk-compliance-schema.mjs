// Renders each Risk service's Schema SQL from its canonical PDManer model (design/07 §7, design/09 §4.3):
// paytech/{risk-decision,compliance,dispute}/docs/database/sql/<service>-schema.sql.
// Usage: node scripts/pdmaner/risk/generate-risk-compliance-schema.mjs [--check]
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

import { RISK_SERVICES, RISK_SERVICE_MODELS, tableInService } from "./risk-service-partition.mjs";

const workspaceUrl = new URL("../../../", import.meta.url);

/** Expected table count per service model (design/03 §4.2: 53 / 23 / 7 own tables plus t_compensation_event). */
export const RISK_SERVICE_TABLE_COUNTS = Object.freeze({ "risk-decision": 54, compliance: 24, dispute: 8 });

const SCHEMA_TITLES = Object.freeze({
  "risk-decision": ["Risk Decision", "mor_risk_decision"],
  compliance: ["Compliance", "mor_compliance"],
  dispute: ["Dispute", "mor_dispute"],
});

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
  invariant(["CHAR", "VARCHAR"].includes(field.type), `仅 CHAR/VARCHAR 字段可声明精确字符串规则：${field.defKey}`);
  invariant((characterSet === "ascii" && collation === "ascii_bin") || (characterSet === "utf8mb4" && collation === "utf8mb4_0900_bin"), `Risk 精确字符串仅允许已批准的二进制排序：${field.defKey}`);
  return ` CHARACTER SET ${characterSet} COLLATE ${collation}`;
}

function validateModel(model, service) {
  invariant(Array.isArray(model.entities) && model.entities.length === RISK_SERVICE_TABLE_COUNTS[service],
    `${service} Schema 必须直接覆盖 ${RISK_SERVICE_TABLE_COUNTS[service]} 表 PDManer`);
  for (const entity of model.entities) {
    invariant(tableInService(entity.defKey, service), `表不属于 ${service}：${entity.defKey}`);
  }
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
  if (service === "compliance") {
    invariant(tableNames.has("t_settle_control_state_change"), "Compliance Schema 缺少特殊物理表 t_settle_control_state_change");
  }
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

export function renderRiskSchema(model, service) {
  invariant(RISK_SERVICES.includes(service), `未知 Risk 服务：${service}`);
  validateModel(model, service);
  const [title, database] = SCHEMA_TITLES[service];
  return [
    `-- Code-MoR ${title} schema: verbatim slice of the approved Risk & Compliance schema by table ownership (design/03 §2.2).`,
    `-- 只适用于人工审核后的空 ${database} 数据库；不得作为应用启动迁移或已有数据库升级脚本。`,
    `-- Source: ${RISK_SERVICE_MODELS[service].modelPath}`,
    "",
    ...model.entities.flatMap((entity) => [renderEntity(entity), ""]),
  ].join("\n");
}

function main() {
  const args = process.argv.slice(2);
  invariant(args.length <= 1 && (args.length === 0 || args[0] === "--check"), "Usage: node generate-risk-compliance-schema.mjs [--check]");
  for (const service of RISK_SERVICES) {
    const modelUrl = new URL(RISK_SERVICE_MODELS[service].modelPath, workspaceUrl);
    const schemaUrl = new URL(RISK_SERVICE_MODELS[service].schemaPath, workspaceUrl);
    const model = JSON.parse(readFileSync(modelUrl, "utf8"));
    const expected = renderRiskSchema(model, service);
    if (args[0] === "--check") {
      if (!existsSync(schemaUrl) || readFileSync(schemaUrl, "utf8") !== expected) {
        process.stderr.write(`${service} Schema is stale; run generate-risk-compliance-schema.mjs\n`);
        process.exitCode = 1;
        continue;
      }
      process.stdout.write(`${service} Schema is up to date (${model.entities.length} tables)\n`);
      continue;
    }
    mkdirSync(dirname(fileURLToPath(schemaUrl)), { recursive: true });
    writeFileSync(schemaUrl, expected, "utf8");
    process.stdout.write(`Wrote ${service} Schema (${model.entities.length} tables): ${fileURLToPath(schemaUrl)}\n`);
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main();
