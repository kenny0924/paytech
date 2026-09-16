// Render one owner-local migration candidate. Never connects to a database.
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { renderSchema } from "../../../../../scripts/pdmaner/generate-oe-client-schemas.mjs";

const root = new URL("../../../../../", import.meta.url);
const here = new URL("./", import.meta.url);
const sha = value => createHash("sha256").update(value).digest("hex");
const sourceBytes = readFileSync(new URL("cct-compensation-source.json", here));
const source = JSON.parse(sourceBytes);
const modelBytes = readFileSync(new URL("client-contract-service/docs/database/client-contract.pdma.json", root));
const model = JSON.parse(modelBytes);
const target = model.entities.find(e => e.defKey === "t_compensation_event");
assert.equal(source.defKey, target.defKey);
assert.equal(source.fields.length, 20);
assert.equal(target.fields.length, 15);
assert.equal(sha(readFileSync(new URL("client-contract-empty-closure.sql", here))), "dc091d30620d30374fa0e5a0b54f93614201bf4e5c46016d1e1958ebc76a0815", "Executed ten-table SQL must remain frozen");
const sourceSql = renderSchema({ entities: [source] }, "cct-compensation-source.json");
const targetSql = renderSchema({ entities: [target] }, "client-contract-service/docs/database/client-contract.pdma.json");
const columns = [...targetSql.matchAll(/^  `(\w+)` (.*?)(?:,)?$/gmu)];
const before = new Set(source.fields.map(f => f.defKey));
const after = new Set(target.fields.map(f => f.defKey));
const clauses = source.indexes.map(i => `DROP INDEX \`${i.defKey}\``);
clauses.push(...[...before].filter(k => !after.has(k)).map(k => `DROP COLUMN \`${k}\``));
for (const [position, [, name, definition]] of columns.entries()) {
  clauses.push(`${before.has(name) ? "MODIFY" : "ADD"} COLUMN \`${name}\` ${definition} ${position ? `AFTER \`${columns[position - 1][1]}\`` : "FIRST"}`);
}
clauses.push(...[...targetSql.matchAll(/^  ((?:UNIQUE KEY|KEY) `\w+` [^\n]*?)(?:,)?$/gmu)].map(m => `ADD ${m[1]}`));
clauses.push(`COMMENT = ${targetSql.match(/ COMMENT=('(?:[^']|'')*');\n$/u)[1]}`);
const ddl = "-- CCT compensation ONLY: exact environment approval required before execution.\n-- Require the existing 20-column table to match cct-compensation-source.json and be empty.\n-- Stop CCT writers and dispatchers; take a fresh schema backup. DDL auto-commits.\n-- No mysql --force. Do not run the full schema or Foundation test DDL.\nSET NAMES utf8mb4;\n\nALTER TABLE `t_compensation_event`\n  " + clauses.join(",\n  ") + ";\n";
let manifestData = { scope: "one CCT compensation ALTER; candidate only, no execution", table: target.defKey,
  sourceFieldCount: 20, targetFieldCount: 15, sourceSha256: sha(sourceBytes), targetModelSha256: sha(modelBytes),
  sourceSqlSha256: sha(sourceSql), targetTableSha256: sha(JSON.stringify(target)), ddlSha256: sha(ddl),
  removedFields: [...before].filter(k => !after.has(k)), addedFields: [...after].filter(k => !before.has(k)),
  preservedExecutedTenTableSqlSha256: "dc091d30620d30374fa0e5a0b54f93614201bf4e5c46016d1e1958ebc76a0815",
};
const receiptFile = new URL("../../assets/stage4/cct-compensation-execution-20260915.json", here);
if (existsSync(receiptFile)) {
  const receipt = JSON.parse(readFileSync(receiptFile, "utf8"));
  assert.equal(receipt.status, "APPLIED_AND_STRUCTURALLY_VERIFIED");
  assert.equal(sha(ddl), receipt.ddlSha256, "Applied compensation SQL changed; create a new migration");
  assert.equal(sha(readFileSync(new URL("cct-compensation-empty.sql", here))), receipt.ddlSha256, "Applied SQL file drift");
  for (const key of ["sourceSha256", "sourceSqlSha256", "targetTableSha256"]) {
    assert.equal(manifestData[key], receipt.manifest[key], `Applied compensation ${key} drift`);
  }
  // The full-model fingerprint records the model that was applied, even when an unrelated table changes later.
  manifestData = receipt.manifest;
}
const manifest = JSON.stringify(manifestData, null, 2) + "\n";
assert.ok(process.argv.slice(2).every(arg => arg === "--check"));
for (const [name, content] of [["cct-compensation-empty.sql", ddl], ["cct-compensation-source.sql", sourceSql], ["cct-compensation-manifest.json", manifest]]) {
  const file = new URL(name, here);
  if (process.argv.includes("--check")) assert.equal(readFileSync(file, "utf8"), content, name);
  else writeFileSync(file, content);
}
console.log("CCT compensation candidate: one ALTER; source, target and executed SQL fingerprints checked");
