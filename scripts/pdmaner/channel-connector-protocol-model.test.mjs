// channel-connector 渠道表与渠道协议表的模型、完整 Schema、测试 DDL 与迁移 SQL 一致性。
// 2026-10-20 第一段自 Platform 迁入 10 张协议表；2026-10-22 第二段（requirements/20261020/channel-connector-统一对外渠道出口/technique.md
// 第 5 节）把 t_payment_channel 与 t_integration 合并为 t_channel、7 张 t_integration_* 表改名为 t_channel_*、新增 t_channel_call_grant。
// 第一段增量与 Platform 删表脚本是已执行的历史文件，只断言其自身内容；不再与当前 Schema 逐字比较。
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { CHANNEL_TABLE } from "./channel-connector/channel-model.mjs";
import { PROTOCOL_TABLES } from "./channel-connector/protocol-model.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const read = (relative) => readFileSync(path.join(workspaceRoot, relative), "utf8");
const service = "paytech/channel-connector";
const requirement = "requirements/20261020/channel-connector-统一对外渠道出口";
const model = JSON.parse(read(`${service}/docs/database/channel-connector.pdma.json`));
const schema = read(`${service}/docs/database/sql/channel-connector-schema.sql`);
const testDdl = read(`${service}/paytech-channel-connector-test/src/test/resources/init/ddl.sql`);
const migrate = read(`${requirement}/db/channel-connector/phase2-20-migrate.sql`);
const tablesOf = (sql) => [...sql.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
const schemaTables = tablesOf(schema);
const schemaBlock = (name) => schemaTables.find((table) => table[1] === name)?.[0];
const uncommented = (sql) => sql.replace(/^--.*$/gm, "");
const entity = (name) => model.entities.find((item) => item.defKey === name);
const fieldsOf = (name) => entity(name).fields.map((field) => field.defKey);

/** 第一段迁入时的 10 张协议表名（历史文件使用）。 */
const PHASE1_TABLES = Object.freeze(["t_standard_api", "t_api_field", "t_integration", "t_integration_api", "t_integration_script",
  "t_integration_standard_api", "t_integration_verify_account", "t_integration_verify_case", "t_integration_verify_run",
  "t_integration_verify_run_step"]);

test("channel-connector model is current: 28 tables, protocol relations stay inside protocol tables and t_channel", () => {
  const result = spawnSync(process.execPath, ["scripts/pdmaner/generate-channel-connector-model.mjs", "--check"],
    { cwd: workspaceRoot, encoding: "utf8" });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.equal(model.entities.length, 28);
  assert.deepEqual(PROTOCOL_TABLES, ["t_standard_api", "t_api_field", "t_channel_api", "t_channel_script",
    "t_channel_standard_api", "t_channel_verify_account", "t_channel_verify_case", "t_channel_verify_run",
    "t_channel_verify_run_step", "t_channel_call_grant"]);
  for (const legacy of ["t_payment_channel", "t_integration", ...PHASE1_TABLES.filter((name) => name.startsWith("t_integration_"))]) {
    assert.ok(!entity(legacy), `模型仍含 ${legacy}`);
  }
  const owned = new Set([CHANNEL_TABLE, ...PROTOCOL_TABLES].map((table) => entity(table).id));
  for (const table of PROTOCOL_TABLES) {
    for (const relation of entity(table).correlations) assert.ok(owned.has(relation.refEntity), `${table} 关系越出 protocol 模块`);
  }
  const channelId = entity(CHANNEL_TABLE).id;
  for (const table of ["t_channel_api", "t_channel_script", "t_channel_standard_api", "t_channel_verify_account",
    "t_channel_verify_case", "t_channel_verify_run", "t_channel_call_grant", "t_channel_account", "t_channel_mid", "t_channel_cost_plan"]) {
    assert.ok(entity(table).correlations.some((relation) => relation.refEntity === channelId), `${table} 未关联 t_channel`);
  }
  const diagram = model.diagrams.find((item) => item.defKey === "channel_protocol");
  assert.ok(diagram, "缺少渠道协议图组");
  const cells = diagram.canvasData.cells.filter((cell) => cell.shape === "table").map((cell) => cell.originKey);
  assert.deepEqual(cells.sort(), [...owned].sort());
});

test("full schema keeps exactly 28 tables and the test DDL copies every block verbatim", () => {
  assert.deepEqual(schemaTables.map((table) => table[1]).sort(), model.entities.map((item) => item.defKey).sort());
  assert.deepEqual(tablesOf(testDdl).map((table) => table[1]).sort(), schemaTables.map((table) => table[1]).sort());
  for (const [block, name] of schemaTables) assert.ok(testDdl.includes(block), `测试 DDL 与完整 Schema 不一致：${name}`);
  assert.doesNotMatch(uncommented(schema), /\bCHECK\s*\(|\bFOREIGN\s+KEY\b/i);
  assert.doesNotMatch(schema, /Platform Support 数据库|接入方标识|t_payment_channel|t_integration/u);
});

test("t_channel merges payment channel identity with outbound endpoints and an immutable type", () => {
  assert.deepEqual(fieldsOf(CHANNEL_TABLE), ["channel_id", "channel_code", "channel_name", "channel_type", "channel_status",
    "description", "endpoints", "callback_definition_json", "version", "created_by", "updated_by", "create_time", "update_time"]);
  const field = (key) => entity(CHANNEL_TABLE).fields.find((item) => item.defKey === key);
  assert.deepEqual(field("channel_code").extProps, { characterSet: "ascii", collation: "ascii_bin" });
  assert.match(field("channel_type").comment, /允许值：PAYMENT、SERVICE。$/u);
  assert.equal(field("channel_type").len, 16);
  assert.equal(field("endpoints").type, "JSON");
  assert.equal(field("endpoints").notNull, false);
  assert.match(field("endpoints").comment, /trusted-networks/u);
  assert.equal(field("version").notNull, true);
  assert.equal(field("created_by").notNull, false);
  assert.equal(field("updated_by").notNull, false);
  assert.match(schemaBlock(CHANNEL_TABLE), /UNIQUE KEY `uk_channel_code` \(`channel_code`\)/u);
  assert.match(schemaBlock(CHANNEL_TABLE), /KEY `idx_channel_type_status` \(`channel_type`, `channel_status`\)/u);
  const accountEntity = entity("t_channel_account").fields.find((item) => item.defKey === "entity_no");
  assert.equal(accountEntity.notNull, false);
  assert.match(accountEntity.comment, /服务渠道账号必须为空/u);
});

test("renamed protocol tables use channel_id and t_channel_call_grant has the approved keys", () => {
  const ids = { t_channel_api: "channel_api_id", t_channel_script: "channel_script_id",
    t_channel_standard_api: "channel_standard_api_id" };
  for (const [table, primary] of Object.entries(ids)) assert.equal(fieldsOf(table)[0], primary);
  for (const table of ["t_channel_api", "t_channel_script", "t_channel_standard_api", "t_channel_verify_account",
    "t_channel_verify_case", "t_channel_verify_run"]) {
    assert.ok(fieldsOf(table).includes("channel_id"), `${table} 缺少 channel_id`);
    assert.ok(!fieldsOf(table).some((key) => key.startsWith("integration")), `${table} 仍含 integration 列`);
  }
  for (const index of ["uk_channel_api_code", "uk_channel_script_code", "uk_channel_standard_api", "idx_channel_std_api_standard",
    "idx_verify_run_channel"]) {
    assert.match(schema, new RegExp(`KEY \`${index}\``, "u"));
  }
  assert.deepEqual(fieldsOf("t_channel_call_grant"), ["call_grant_id", "caller_service", "channel_id", "standard_api_id",
    "grant_status", "version", "created_by", "updated_by", "create_time", "update_time"]);
  const grant = schemaBlock("t_channel_call_grant");
  assert.match(grant, /`caller_service` VARCHAR\(64\) CHARACTER SET ascii COLLATE ascii_bin NOT NULL/u);
  assert.match(grant, /UNIQUE KEY `uk_channel_call_grant` \(`caller_service`, `channel_id`, `standard_api_id`\)/u);
  assert.match(grant, /KEY `idx_channel_call_grant_channel` \(`channel_id`, `grant_status`\)/u);
  assert.equal([...grant.matchAll(/KEY `/gu)].length, 2, "t_channel_call_grant 只有两个索引");
  assert.match(entity("t_standard_api").comment, /t_channel_call_grant/u);
  assert.match(entity("t_api_field").fields.find((item) => item.defKey === "api_id").comment, /t_channel_api\.channel_api_id/u);
});

test("t_channel_config_change lists the phase-2 target types", () => {
  const change = entity("t_channel_config_change");
  assert.match(change.fields.find((item) => item.defKey === "target_type").comment,
    /允许值：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION、STANDARD_API、CHANNEL_API、CHANNEL_SCRIPT、CHANNEL_IMPLEMENTATION、VERIFY_ACCOUNT、VERIFY_CASE、CHANNEL_CALL_GRANT。$/u);
  assert.match(change.fields.find((item) => item.defKey === "change_type").comment, /CANCEL、DELETE。$/u);
});

test("phase-2 migration renames, alters every column to the schema definition and drops t_integration last", () => {
  const statements = uncommented(migrate);
  for (const [from, to] of [["t_payment_channel", "t_channel"], ["t_integration_api", "t_channel_api"],
    ["t_integration_script", "t_channel_script"], ["t_integration_standard_api", "t_channel_standard_api"],
    ["t_integration_verify_account", "t_channel_verify_account"], ["t_integration_verify_case", "t_channel_verify_case"],
    ["t_integration_verify_run", "t_channel_verify_run"], ["t_integration_verify_run_step", "t_channel_verify_run_step"]]) {
    assert.ok(statements.includes(`\`${from}\` TO \`${to}\``), `缺少改名 ${from}`);
  }
  assert.ok(statements.includes(schemaBlock("t_channel_call_grant")), "t_channel_call_grant 与完整 Schema 不一致");
  // ADD / CHANGE / MODIFY 的列定义必须逐字等于完整 Schema 中的列（ADD 的回填默认值在收尾时去掉）。
  const columns = new Set(schema.split("\n").filter((line) => line.startsWith("  `")).map((line) => line.trim().replace(/,$/u, "")));
  const altered = [...statements.matchAll(/^ {2}(?:ADD COLUMN|MODIFY COLUMN|CHANGE COLUMN `\w+`) (`.*?)(?: AFTER `\w+`)?,?;?$/gmu)]
    .map((match) => match[1].replace(/ DEFAULT (?:'PAYMENT'|1) COMMENT/u, " COMMENT"));
  assert.ok(altered.length > 30, "未解析到 ALTER 列定义");
  for (const line of altered) assert.ok(columns.has(line), `ALTER 列定义与 Schema 不一致：${line.slice(0, 80)}`);
  assert.match(statements, /ALTER COLUMN `channel_type` DROP DEFAULT,\n {2}ALTER COLUMN `version` DROP DEFAULT;/u);
  assert.ok(statements.trimEnd().endsWith("DROP TABLE `t_integration`;"), "t_integration 应在最后删除");
  assert.doesNotMatch(statements, /\bCHECK\s*\(|\bFOREIGN\s+KEY\b/i);
  assert.match(statements, /SET c\.target_type = 'CHANNEL', c\.target_id = m\.channel_id/u);
});

test("phase-2 precheck and verify scripts are read-only", () => {
  for (const file of ["phase2-10-precheck.sql", "phase2-30-verify.sql"]) {
    const statements = uncommented(read(`${requirement}/db/channel-connector/${file}`));
    assert.doesNotMatch(statements, /\b(?:INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|RENAME|TRUNCATE|REPLACE)\b/u, `${file} 含写入语句`);
  }
  assert.match(read(`${requirement}/db/channel-connector/phase2-rollback.md`), /--set-gtid-purged=OFF/u);
});

test("phase-1 history files still describe the ten migrated tables under their old names", () => {
  const increment = read(`${service}/docs/database/sql/changes/2026-10-20-protocol-tables.sql`);
  assert.deepEqual(tablesOf(increment).map((table) => table[1]), PHASE1_TABLES);
  assert.match(increment, /ALTER TABLE `t_channel_config_change`\n {2}ADD COLUMN `change_detail` JSON NULL/u);
  const drop = read(`${requirement}/db/platform-support-service/drop-integration.sql`);
  const statements = uncommented(drop).split(";").map((item) => item.trim()).filter(Boolean);
  assert.deepEqual(statements.map((item) => item.match(/^DROP TABLE `(\w+)`$/u)?.[1]).sort(), [...PHASE1_TABLES].sort());
  assert.match(drop, /mor_platform/u);
});

test("Platform no longer defines the protocol tables", () => {
  const platformSchema = read("paytech/platform-support/docs/database/sql/platform-support-schema.sql");
  const platformModel = JSON.parse(read("paytech/platform-support/docs/database/platform-support.pdma.json"));
  for (const table of PHASE1_TABLES) {
    assert.ok(!tablesOf(platformSchema).some((item) => item[1] === table), `Platform Schema 仍含 ${table}`);
    assert.ok(!platformModel.entities.some((item) => item.defKey === table), `Platform 模型仍含 ${table}`);
  }
});
