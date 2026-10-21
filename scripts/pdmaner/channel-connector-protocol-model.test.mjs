// channel-connector 渠道协议 10 表（2026-10-20 统一对外渠道出口第一段自 Platform 迁入）与 t_channel_config_change 扩展的
// 模型、完整 Schema、测试 DDL、增量与 Platform 删表脚本一致性。
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { PROTOCOL_TABLES } from "./channel-connector/protocol-model.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const read = (relative) => readFileSync(path.join(workspaceRoot, relative), "utf8");
const service = "paytech/channel-connector";
const model = JSON.parse(read(`${service}/docs/database/channel-connector.pdma.json`));
const schema = read(`${service}/docs/database/sql/channel-connector-schema.sql`);
const testDdl = read(`${service}/paytech-channel-connector-test/src/test/resources/init/ddl.sql`);
const increment = read(`${service}/docs/database/sql/changes/2026-10-20-protocol-tables.sql`);
const quote = (value) => `'${value.replaceAll("'", "''")}'`;
const tablesOf = (sql) => [...sql.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
const schemaTables = tablesOf(schema);
const schemaBlock = (name) => schemaTables.find((table) => table[1] === name)?.[0];
const uncommented = (sql) => sql.replace(/^--.*$/gm, "");

test("channel-connector model is current and contains the ten protocol tables with local relations only", () => {
  const result = spawnSync(process.execPath, ["scripts/pdmaner/generate-channel-connector-model.mjs", "--check"],
    { cwd: workspaceRoot, encoding: "utf8" });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.equal(model.entities.length, 28);
  assert.equal(PROTOCOL_TABLES.length, 10);
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  for (const table of PROTOCOL_TABLES) assert.ok(entities.has(table), `缺少协议表 ${table}`);
  const protocolIds = new Set(PROTOCOL_TABLES.map((table) => entities.get(table).id));
  for (const table of PROTOCOL_TABLES) {
    for (const relation of entities.get(table).correlations) assert.ok(protocolIds.has(relation.refEntity), `${table} 关系越出协议表`);
  }
  const diagram = model.diagrams.find((item) => item.defKey === "channel_protocol");
  assert.ok(diagram, "缺少渠道协议图组");
  const cells = diagram.canvasData.cells.filter((cell) => cell.shape === "table").map((cell) => cell.originKey);
  assert.deepEqual(cells.sort(), [...protocolIds].sort());
});

test("full schema keeps exactly 28 tables and the test DDL copies every block verbatim", () => {
  assert.deepEqual(schemaTables.map((table) => table[1]).sort(), model.entities.map((entity) => entity.defKey).sort());
  assert.deepEqual(tablesOf(testDdl).map((table) => table[1]).sort(), schemaTables.map((table) => table[1]).sort());
  for (const [block, name] of schemaTables) assert.ok(testDdl.includes(block), `测试 DDL 与完整 Schema 不一致：${name}`);
  assert.doesNotMatch(uncommented(schema), /\bCHECK\s*\(|\bFOREIGN\s+KEY\b/i);
});

test("t_channel_config_change carries change_detail and the extended target/change types", () => {
  const entity = model.entities.find((item) => item.defKey === "t_channel_config_change");
  const fields = entity.fields.map((field) => field.defKey);
  assert.equal(fields[fields.indexOf("reason") + 1], "change_detail");
  const detail = entity.fields.find((field) => field.defKey === "change_detail");
  assert.equal(detail.type, "JSON");
  assert.equal(detail.notNull, false);
  assert.match(detail.comment, /不含定义原文、参数或凭据值/u);
  assert.match(entity.fields.find((field) => field.defKey === "target_type").comment,
    /CHANNEL_COST_VERSION、STANDARD_API、INTEGRATION、INTEGRATION_API、INTEGRATION_SCRIPT、INTEGRATION_IMPLEMENTATION、VERIFY_ACCOUNT、VERIFY_CASE。$/u);
  assert.match(entity.fields.find((field) => field.defKey === "change_type").comment, /CANCEL、DELETE。$/u);
  assert.equal(entity.comment, "渠道配置、成本版本与外部 API 配置的人工变更留痕；与业务变更同事务写入，只增不改。");
});

test("2026-10-20 protocol increment creates the ten schema tables and alters only t_channel_config_change", () => {
  const created = tablesOf(increment);
  assert.deepEqual(created.map((table) => table[1]), PROTOCOL_TABLES);
  for (const [block, name] of created) assert.equal(block, schemaBlock(name), `增量与完整 Schema 不一致：${name}`);
  const entity = model.entities.find((item) => item.defKey === "t_channel_config_change");
  const comment = (key) => entity.fields.find((field) => field.defKey === key).comment;
  const alter = "ALTER TABLE `t_channel_config_change`\n"
    + `  ADD COLUMN \`change_detail\` JSON NULL COMMENT ${quote(comment("change_detail"))} AFTER \`reason\`,\n`
    + `  MODIFY COLUMN \`target_type\` VARCHAR(64) NOT NULL COMMENT ${quote(comment("target_type"))},\n`
    + `  MODIFY COLUMN \`change_type\` VARCHAR(64) NOT NULL COMMENT ${quote(comment("change_type"))},\n`
    + `  COMMENT=${quote(entity.comment)};`;
  assert.ok(increment.includes(alter), "ALTER 语句与模型注释不一致");
  const statements = uncommented(increment).replace(alter, "");
  assert.doesNotMatch(statements, /\b(?:DROP|ALTER|DELETE|INSERT|UPDATE|CHECK|FOREIGN)\b/i);
});

test("historical Platform increments still equal the migrated channel-connector tables", () => {
  const configuration = tablesOf(read("requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/integration-create.sql"));
  assert.deepEqual(configuration.map((table) => table[1]), PROTOCOL_TABLES.slice(0, 6));
  // 2026-10-14 沙箱验证增量以 MODIFY COLUMN 更新了 t_integration.endpoints 的注释，其余内容仍须逐字一致。
  const withoutEndpointsComment = (text) => text.replace(/^  `endpoints` JSON NOT NULL COMMENT '(?:[^']|'')*',$/m, "");
  for (const [block, name] of configuration) {
    assert.equal(withoutEndpointsComment(block), withoutEndpointsComment(schemaBlock(name)), name);
  }
  const sandboxSql = read("requirements/20261014/platform-support-渠道沙箱验证/db/ddl.sql");
  const sandbox = tablesOf(sandboxSql);
  assert.deepEqual(sandbox.map((table) => table[1]), PROTOCOL_TABLES.slice(6));
  for (const [block, name] of sandbox) assert.equal(block, schemaBlock(name), name);
  const endpoints = model.entities.find((entity) => entity.defKey === "t_integration").fields.find((field) => field.defKey === "endpoints");
  assert.ok(sandboxSql.includes(`ALTER TABLE \`t_integration\` MODIFY COLUMN \`endpoints\` JSON NOT NULL COMMENT ${quote(endpoints.comment)};`));
});

test("Platform no longer defines the protocol tables and its drop script removes exactly those ten", () => {
  const platformSchema = read("paytech/platform-support/docs/database/sql/platform-support-schema.sql");
  const platformTestDdl = read("paytech/platform-support/paytech-platform-support-test/src/test/resources/init/ddl.sql");
  const platformModel = JSON.parse(read("paytech/platform-support/docs/database/platform-support.pdma.json"));
  for (const table of PROTOCOL_TABLES) {
    assert.ok(!tablesOf(platformSchema).some((item) => item[1] === table), `Platform Schema 仍含 ${table}`);
    assert.ok(!tablesOf(platformTestDdl).some((item) => item[1] === table), `Platform 测试 DDL 仍含 ${table}`);
    assert.ok(!platformModel.entities.some((entity) => entity.defKey === table), `Platform 模型仍含 ${table}`);
  }
  const drop = read("requirements/20261020/channel-connector-统一对外渠道出口/db/platform-support-service/drop-integration.sql");
  const statements = uncommented(drop).split(";").map((item) => item.trim()).filter(Boolean);
  assert.deepEqual(statements.map((item) => item.match(/^DROP TABLE `(\w+)`$/u)?.[1]).sort(), [...PROTOCOL_TABLES].sort());
  assert.match(drop, /mor_platform/u);
  assert.match(drop, /第一段 mor-test 验收后执行/u);
});
