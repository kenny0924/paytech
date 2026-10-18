// Shared contract for the two PDManer models split from commerce-service in step 03 (acquiring, consumer).
// Each service test supplies its own frozen oracle (ordered tables, fields, indexes), totals and digests; this module only
// checks them against the checked-in artifacts. It is not a test file and is not matched by *.test.mjs.
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { renderSchema } from "./generate-oe-client-schemas.mjs";

export const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const expectedTemplateDigest = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
const idPattern = /^[A-F0-9]{8}-[A-F0-9]{4}-5[A-F0-9]{3}-[89AB][A-F0-9]{3}-[A-F0-9]{12}$/u;

const sha256 = (value) => createHash("sha256").update(value).digest("hex");

/** Service artifact paths, relative to the workspace root. */
export function servicePaths(service) {
  const upper = service.toUpperCase();
  const root = `paytech/${service}`;
  return Object.freeze({
    model: `${root}/docs/database/${service}.pdma.json`,
    decisions: `${root}/docs/database/${upper}_PHYSICAL_DECISIONS.md`,
    schema: `${root}/docs/database/sql/${service}-schema.sql`,
    initialData: `${root}/docs/database/sql/${service}-initial-data.sql`,
    testDdl: `${root}/paytech-${service}-test/src/test/resources/init/ddl.sql`,
    generator: `scripts/pdmaner/generate-${service}-model.mjs`,
    core: "scripts/pdmaner/commerce-model-core.mjs",
    metadata: "scripts/pdmaner/commerce-field-metadata.mjs",
    template: "agent-docs/templates/pdmaner/template.pdma.json",
    manifestMarker: `${upper}_PHYSICAL_MANIFEST`,
  });
}

const read = (relative) => readFileSync(path.join(workspaceRoot, relative), "utf8");

/** Projects the model to the oracle text format: [table], `field TYPE(len) NN|N`, `UK|IDX name(fields)`. */
export function oracleProjection(model) {
  const lines = [];
  for (const entity of model.entities) {
    const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
    lines.push(`[${entity.defKey}]`);
    for (const field of entity.fields) {
      const precision = field.len === "" ? "" : `(${field.len}${field.scale === "" ? "" : `,${field.scale}`})`;
      lines.push(`${field.defKey} ${field.type}${precision} ${field.notNull ? "NN" : "N"}`);
    }
    for (const index of entity.indexes) {
      lines.push(`${index.unique ? "UK" : "IDX"} ${index.defKey}(${index.fields.map((part) => fieldsById.get(part.fieldDefKey)).join(",")})`);
    }
    lines.push("");
  }
  return lines.join("\n").trim();
}

/** CREATE TABLE blocks keyed by table name. */
export function createTableBlocks(sql) {
  const blocks = new Map();
  for (const match of sql.matchAll(/CREATE TABLE `(t_[a-z0-9_]+)` \(.*?;\n/gsu)) blocks.set(match[1], match[0]);
  return blocks;
}

function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  return {
    tables: model.entities.length,
    fields: model.entities.flatMap((entity) => entity.fields).length,
    uniqueIndexes: indexes.filter((index) => index.unique).length,
    accessIndexes: indexes.filter((index) => !index.unique).length,
    relations: model.entities.flatMap((entity) => entity.correlations).length,
    diagramGroups: model.diagrams.length,
  };
}

/**
 * Registers the model contract tests for one service.
 *
 * @param {object} contract service, label, database, account, oracle, totals, digests, diagrams, versionTables,
 *   forbiddenTables, forbiddenFields (table.field), relations (child.field -> parent.field)
 */
export function registerServiceModelContract(contract) {
  const paths = servicePaths(contract.service);
  const required = [paths.model, paths.decisions, paths.schema, paths.initialData, paths.testDdl, paths.generator,
    paths.core, paths.metadata];
  const model = () => JSON.parse(read(paths.model));

  test(`${contract.label} PDManer contract has every design artifact at the canonical service path`, () => {
    const missing = required.filter((relative) => !existsSync(path.join(workspaceRoot, relative)));
    assert.deepEqual(missing, []);
    assert.equal(existsSync(path.join(workspaceRoot, "commerce-service")), false, "旧根 commerce-service 不得恢复");
  });

  test(`${contract.label} model has the exact ordered table, field and index contract`, () => {
    assert.equal(oracleProjection(model()), contract.oracle.trim());
  });

  test(`${contract.label} model keeps removed tables and columns out and owns no cross-database relation`, () => {
    const current = model();
    const tables = new Set(current.entities.map((entity) => entity.defKey));
    for (const table of contract.forbiddenTables) assert.equal(tables.has(table), false, `${table} 不属于 ${contract.label}`);
    for (const pathKey of contract.forbiddenFields) {
      const [table, field] = pathKey.split(".");
      const entity = current.entities.find((candidate) => candidate.defKey === table);
      assert.ok(!entity || !entity.fields.some((candidate) => candidate.defKey === field), `${pathKey} 已删除`);
    }
    const byId = new Map(current.entities.map((entity) => [entity.id, entity]));
    const relations = current.entities.flatMap((entity) => entity.correlations.map((relation) => {
      const parent = byId.get(relation.refEntity);
      assert.ok(parent, `${entity.defKey} 的关系指向本模型之外的表`);
      const child = entity.fields.find((field) => field.id === relation.myField).defKey;
      const parentField = parent.fields.find((field) => field.id === relation.refField).defKey;
      return `${entity.defKey}.${child} -> ${parent.defKey}.${parentField}`;
    }));
    assert.deepEqual(relations, contract.relations);
  });

  test(`${contract.label} model versions, immutability, diagrams and compensation defaults are exact`, () => {
    const current = model();
    assert.deepEqual(current.entities.filter((entity) => entity.fields.some((field) => field.defKey === "version"))
      .map((entity) => entity.defKey), contract.versionTables);
    assert.deepEqual(current.diagrams.map((diagram) => diagram.defName), contract.diagrams);
    const compensation = current.entities.find((entity) => entity.defKey === "t_compensation_event");
    const defaults = Object.fromEntries(compensation.fields.filter((field) => field.defaultValue !== "")
      .map((field) => [field.defKey, field.defaultValue]));
    assert.deepEqual(defaults, {
      execute_status: "'INIT'",
      execute_count: "0",
      next_execute_at: "CURRENT_TIMESTAMP(6)",
      lock_version: "1",
      create_time: "CURRENT_TIMESTAMP(6)",
      update_time: "CURRENT_TIMESTAMP(6)",
    });
  });

  test(`${contract.label} model is a credential-free deterministic clone of the shared template`, async () => {
    assert.equal(sha256(readFileSync(path.join(workspaceRoot, paths.template))), expectedTemplateDigest);
    const current = model();
    assert.deepEqual(current.dbConn, []);
    assert.deepEqual(current.dbConns, []);
    assert.equal(current.profile.default.dbConn, "");
    const core = await import(`./commerce-model-core.mjs?contract=${contract.service}-${Date.now()}`);
    const first = core.serializeServiceModel(contract.service);
    assert.equal(first, core.serializeServiceModel(contract.service), "相同输入必须产生逐字节相同模型");
    assert.equal(first, read(paths.model), "已提交模型必须与生成器输出逐字节一致");
    const ids = [
      ...current.entities.flatMap((entity) => [entity.id, ...entity.fields.map((field) => field.id),
        ...entity.indexes.flatMap((index) => [index.id, ...index.fields.map((part) => part.id)])]),
      ...current.diagrams.flatMap((diagram) => [diagram.id, ...diagram.canvasData.cells.map((cell) => cell.id)]),
    ];
    assert.ok(ids.every((id) => idPattern.test(id)), "所有生成 ID 必须是稳定 SHA-256 UUID v5");
    assert.equal(new Set(ids).size, ids.length, "所有生成 ID 必须唯一");
  });

  test(`${contract.label} model freezes totals and both SHA-256 digests in the physical decisions`, async () => {
    const bytes = read(paths.model);
    const current = JSON.parse(bytes);
    const core = await import(`./commerce-model-core.mjs?digest=${contract.service}-${Date.now()}`);
    const actual = {
      modelTotals: modelTotals(current),
      templateSha256: expectedTemplateDigest,
      modelSha256: sha256(bytes),
      structuralProjectionSha256: core.structuralProjectionDigest(current),
    };
    assert.deepEqual(actual.modelTotals, contract.totals);
    assert.equal(actual.modelSha256, contract.modelSha256);
    assert.equal(actual.structuralProjectionSha256, contract.structuralSha256);
    const decisions = read(paths.decisions);
    const block = decisions.match(new RegExp(`<!-- ${paths.manifestMarker}_START -->\\s*\`\`\`json\\s*([\\s\\S]*?)\`\`\`\\s*<!-- ${paths.manifestMarker}_END -->`, "u"));
    assert.ok(block, "物理决策缺少 Manifest 区块");
    assert.deepEqual(JSON.parse(block[1]), actual);
  });

  test(`${contract.label} manual schema exactly projects the model without CHECK or FK`, () => {
    const schema = read(paths.schema);
    assert.equal(schema, renderSchema(model(), paths.model));
    assert.doesNotMatch(schema, /\bCHECK\b|FOREIGN KEY|REFERENCES/u);
    assert.match(read(paths.initialData), /no business.*seed rows/iu);
  });

  test(`${contract.label} test DDL is a verbatim slice of the schema for ${contract.database} only`, () => {
    const ddl = read(paths.testDdl);
    const schemaBlocks = createTableBlocks(read(paths.schema));
    const ddlBlocks = createTableBlocks(ddl);
    assert.deepEqual([...ddlBlocks.keys()].sort(), [...schemaBlocks.keys()].sort());
    for (const [table, block] of ddlBlocks) assert.equal(block, schemaBlocks.get(table), `${table} 必须逐字切片`);
    assert.match(ddl, new RegExp(`CREATE DATABASE IF NOT EXISTS \`${contract.database}\``, "u"));
    assert.match(ddl, new RegExp(`GRANT SELECT, INSERT, UPDATE, DELETE ON \`${contract.database}\`\\.\\* TO '${contract.account}'@'%'`, "u"));
    const statements = ddl.split("\n").filter((line) => !line.startsWith("--")).join("\n");
    assert.doesNotMatch(statements, /DROP DATABASE|GRANT ALL/u);
  });

  test(`${contract.label} generator --check proves the checked-in model is current`, () => {
    const result = spawnSync(process.execPath, [path.join(workspaceRoot, paths.generator), "--check"], {
      cwd: workspaceRoot,
      encoding: "utf8",
    });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /model is up to date/u);
  });
}
