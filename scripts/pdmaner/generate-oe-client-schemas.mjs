import assert from "node:assert/strict";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(import.meta.dirname, "../..");
export const schemaTargets = Object.freeze([
  ["paytech/client-master", "client-master"],
  ["paytech/commercial", "commercial"],
  // 第 03 步：commerce-service 拆为 acquiring 与 consumer（design/09 §4.3）。
  ["paytech/acquiring", "acquiring"],
  ["paytech/consumer", "consumer"],
  ["paytech/payment", "payment"],
  ["paytech/channel-connector", "channel-connector"],
  // 第 07 步：finance-service 按服务切分为六个服务目录（design/09 §4.3）。
  ["paytech/ledger", "ledger"],
  ["paytech/account", "account"],
  ["paytech/billing", "billing"],
  ["paytech/reconciliation", "reconciliation"],
  ["paytech/tax", "tax"],
  ["paytech/payout", "payout"],
]);
const quote = (value) => `'${String(value).replaceAll("\\", "\\\\").replaceAll("'", "''")}'`;
const identifier = (value) => {
  assert.match(value, /^[a-z][a-z0-9_]*$/u);
  return `\`${value}\``;
};

// Only physical PK/UK/index constraints are exported. Model relationships and
// business validation clauses remain metadata, never executable FK/CHECK DDL.
export function renderSchema(model, source) {
  const tables = model.entities.map((entity) => {
    const columns = entity.fields.map((field) => {
      const precision = field.len === "" ? "" : `(${field.len}${field.scale === "" ? "" : `,${field.scale}`})`;
      const charset = field.extProps.characterSet;
      const collation = field.extProps.collation;
      assert.ok(!charset || /^[a-z0-9_]+$/u.test(charset));
      assert.ok(!collation || /^[a-z0-9_]+$/u.test(collation));
      const encoding = charset ? ` CHARACTER SET ${charset} COLLATE ${collation}` : "";
      const defaultSql = field.defaultValue === "" ? "" : ` DEFAULT ${field.defaultValue}`;
      return `  ${identifier(field.defKey)} ${field.type}${precision}${encoding} ${field.notNull ? "NOT NULL" : "NULL"}${defaultSql} COMMENT ${quote(field.comment)}`;
    });
    columns.push(`  PRIMARY KEY (${entity.fields.filter((f) => f.primaryKey).map((f) => identifier(f.defKey)).join(", ")})`);
    for (const index of entity.indexes) {
      const fields = index.fields.map((part) => {
        const field = entity.fields.find((f) => f.id === part.fieldDefKey);
        assert.ok(field, `Dangling index: ${entity.defKey}.${index.defKey}`);
        return identifier(field.defKey);
      });
      columns.push(`  ${index.unique ? "UNIQUE KEY" : "KEY"} ${identifier(index.defKey)} (${fields.join(", ")}) COMMENT ${quote(index.comment)}`);
    }
    return `CREATE TABLE ${identifier(entity.defKey)} (\n${columns.join(",\n")}\n) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT=${quote(entity.comment)};`;
  });
  return `-- Code-MoR target schema generated from the approved PDManer model.\n-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.\n-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.\n-- Source: ${source}\nSET NAMES utf8mb4;\n\n${tables.join("\n\n")}\n`;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  assert.ok(process.argv.slice(2).every((arg) => arg === "--check"), "Only --check is supported");
  for (const [repository, name] of schemaTargets) {
    const source = `${repository}/docs/database/${name}.pdma.json`;
    const target = path.join(root, repository, "docs/database/sql", `${name}-schema.sql`);
    const expected = renderSchema(JSON.parse(readFileSync(path.join(root, source), "utf8")), source);
    if (process.argv.includes("--check")) assert.equal(readFileSync(target, "utf8"), expected, `${name} schema drift`);
    else {
      mkdirSync(path.dirname(target), { recursive: true });
      writeFileSync(target, expected);
    }
    console.log(`${process.argv.includes("--check") ? "Verified" : "Generated"} ${name} schema`);
  }
}
