import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { CLOSED_ENUM_VALUES } from "./client-contract-field-metadata.mjs";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const modelPath = path.join(
  workspaceRoot,
  "client-contract-service/docs/database/client-contract.pdma.json",
);
const scenarioPath = path.join(
  workspaceRoot,
  "agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md",
);

const expectedSchema = "CODE-MOR:CCT:REAL_WORLD_SCENARIOS:1";
const expectedStoryId = "NOVA_GULF_UAE_2026";
const requiredFlowSteps = [
  "input",
  "write_transaction",
  "current_query",
  "change_or_failure",
  "history_or_wire",
];

function readModel() {
  return JSON.parse(readFileSync(modelPath, "utf8"));
}

function readScenarioDocument() {
  assert.ok(
    existsSync(scenarioPath),
    "Client & Contract 现实场景文档尚未创建",
  );
  return readFileSync(scenarioPath, "utf8");
}

function readScenarioManifest(document) {
  const match = document.match(
    /<!-- CCT_SCENARIO_MANIFEST_START -->\s*```json\s*([\s\S]*?)\s*```\s*<!-- CCT_SCENARIO_MANIFEST_END -->/u,
  );
  assert.ok(match, "现实场景文档缺少可机器校验的场景 Manifest");
  return JSON.parse(match[1]);
}

function scenarioBody(document) {
  const markerIndex = document.indexOf("<!-- CCT_SCENARIO_MANIFEST_START -->");
  assert.notEqual(markerIndex, -1, "现实场景文档缺少 Manifest 起始标记");
  return document.slice(0, markerIndex);
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

function physicalEntityMap(model) {
  return new Map(model.entities.map((entity) => [entity.defKey, entity]));
}

function normalizedEnums(enums) {
  return Object.fromEntries(
    Object.entries(enums)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([fieldPath, values]) => [fieldPath, [...values]]),
  );
}

test("现实场景 Manifest 覆盖当前全部物理表并只引用存在的字段", () => {
  const document = readScenarioDocument();
  const body = scenarioBody(document);
  const manifest = readScenarioManifest(document);
  const model = readModel();
  const entities = physicalEntityMap(model);

  assert.equal(manifest.schema, expectedSchema);
  assert.equal(manifest.storyId, expectedStoryId);
  const approvedTotals = {
    tables: 36,
    fields: 404,
    uniqueIndexes: 54,
    accessIndexes: 43,
    relations: 61,
    diagramGroups: 6,
  };
  assert.deepEqual(modelTotals(model), approvedTotals);
  assert.deepEqual(manifest.modelTotals, approvedTotals);

  const referencedTables = new Set();
  const referencedFields = new Map();
  for (const scenario of manifest.scenarios) {
    assert.equal(typeof scenario.sectionHeading, "string", `${scenario.id} 缺少正文章节定位`);
    const sectionStart = body.indexOf(scenario.sectionHeading);
    assert.notEqual(sectionStart, -1, `${scenario.id} 的正文章节不存在`);
    const nextScenario = manifest.scenarios[manifest.scenarios.indexOf(scenario) + 1];
    const nextSectionStart = !nextScenario || nextScenario.sectionHeading === scenario.sectionHeading
      ? -1
      : body.indexOf(nextScenario.sectionHeading, sectionStart + scenario.sectionHeading.length);
    const section = body.slice(sectionStart, nextSectionStart === -1 ? undefined : nextSectionStart);
    assert.deepEqual(
      scenario.flowSteps,
      requiredFlowSteps,
      `${scenario.id} 缺少完整的写入、查询、变更和历史流程`,
    );
    for (const tableKey of scenario.tables) {
      assert.ok(entities.has(tableKey), `${scenario.id} 引用了不存在的表 ${tableKey}`);
      assert.ok(
        section.includes("`" + tableKey + "`") || section.includes("`" + tableKey + "."),
        `${scenario.id} 正文未解释 ${tableKey}`,
      );
      referencedTables.add(tableKey);
    }
    for (const fieldPath of scenario.fieldRefs) {
      const match = fieldPath.match(/^(t_[a-z0-9_]+)\.([a-z0-9_]+)$/u);
      assert.ok(match, `${scenario.id} 的字段引用格式非法：${fieldPath}`);
      const [, tableKey, fieldKey] = match;
      const entity = entities.get(tableKey);
      assert.ok(entity, `${scenario.id} 引用了不存在的表 ${tableKey}`);
      assert.ok(
        entity.fields.some((field) => field.defKey === fieldKey),
        `${scenario.id} 引用了不存在的字段 ${fieldPath}`,
      );
      assert.ok(
        section.includes("`" + fieldPath + "`") || section.includes("`" + fieldPath + "="),
        `${scenario.id} 正文缺少字段示例 ${fieldPath}`,
      );
      referencedFields.set(tableKey, (referencedFields.get(tableKey) ?? 0) + 1);
    }
  }

  assert.deepEqual(
    [...referencedTables].sort(),
    [...entities.keys()].sort(),
    "现实场景必须覆盖当前全部 36 张物理表",
  );
  for (const tableKey of entities.keys()) {
    assert.ok(referencedFields.has(tableKey), `${tableKey} 没有任何关键字段示例`);
    assert.ok(
      body.includes("`" + tableKey + "`") || body.includes("`" + tableKey + "."),
      `${tableKey} 未在正文中出现`,
    );
  }

  const qualifiedReferences = body.matchAll(/`(t_[a-z0-9_]+)\.([a-z0-9_]+)`/gu);
  for (const [, tableKey, fieldKey] of qualifiedReferences) {
    const entity = entities.get(tableKey);
    assert.ok(entity, `正文引用了不存在的表 ${tableKey}`);
    assert.ok(
      entity.fields.some((field) => field.defKey === fieldKey),
      `正文引用了不存在的字段 ${tableKey}.${fieldKey}`,
    );
  }
});

test("现实场景枚举表与当前闭合枚举目录完全一致", () => {
  const manifest = readScenarioManifest(readScenarioDocument());
  assert.deepEqual(
    normalizedEnums(manifest.enumAssertions),
    normalizedEnums(CLOSED_ENUM_VALUES),
  );
});

test("现实场景文档从 Agent、数据库和 CCT 当前导航均可到达", () => {
  readScenarioDocument();
  const navigationContracts = [
    ["agent-docs/README.md", "./database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md"],
    ["agent-docs/database/README.md", "./scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md"],
    ["agent-docs/database/CLIENT_CONTRACT_DATABASE.md", "./scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md"],
    [
      "client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md",
      "../../../agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md",
    ],
  ];

  for (const [relativePath, expectedLink] of navigationContracts) {
    const sourcePath = path.join(workspaceRoot, relativePath);
    const source = readFileSync(sourcePath, "utf8");
    assert.match(
      source,
      new RegExp(expectedLink.replace(/[.*+?^${}()|[\]\\]/gu, "\\$&"), "u"),
      `${relativePath} 未接入现实场景导航`,
    );
    const targetPath = path.resolve(path.dirname(sourcePath), expectedLink.split("#", 1)[0]);
    assert.ok(existsSync(targetPath), `${relativePath} 的场景链接目标不存在：${targetPath}`);
  }

  const document = readScenarioDocument();
  for (const [, target] of document.matchAll(/\[[^\]]+\]\(([^)]+)\)/gu)) {
    if (/^(?:https?:|#)/u.test(target)) continue;
    const targetPath = path.resolve(path.dirname(scenarioPath), target.split("#", 1)[0]);
    assert.ok(existsSync(targetPath), `现实场景文档链接目标不存在：${target}`);
  }
});

test("现实场景固定 CCT-I14 的精确 Scope 闭包与失败边界", () => {
  const document = readScenarioDocument();

  assert.match(
    document,
    /GetImmutableCommercialTermScope\([\s\S]*term_scope_id=730820000000000001[\s\S]*agreement_operating_entity_id=730640000000000002[\s\S]*context_as_of_time=2026-10-15T12:00:00\.000000Z/u,
  );
  assert.match(document, /operating_entity_id=NULL.*全部 OE.*顶层返回实际 `operating_entity_id=730400000000000002`/u);
  assert.match(document, /owner_type=CLIENT\/owner_id=730200000000000001/u);
  assert.match(document, /Partner Agreement Association `730640000000000003`.*永久 Fail Closed/u);
  assert.match(document, /另一条内部闭包仍合法、但并非 Order 冻结的 Scope.*Finance 必须.*逐值比对/u);
  assert.match(document, /不接收 `expected_commercial_context_hash`.*不验证或重算 V5/u);
  assert.match(document, /暂时网络错误时 Finance 不 ACK 原 `PaymentSucceeded`.*Broker.*重投/u);
  assert.match(document, /CCT-I13 已校验四项结算字段完整，但不重复返回字段值/u);
  assert.match(document, /Commerce 不复制四项结算字段；Finance.*调用 CCT-I14/u);
});

test("现实场景固定 Partner 平台服务费条款、唯一签约方与 V5 边界", () => {
  const document = readScenarioDocument();

  assert.match(document, /`PLATFORM_SERVICE_CHARGE`.*第三类 Commercial Term/u);
  assert.match(document, /`GROSS_PAYMENT_AMOUNT`[\s\S]*USD 100\.00[\s\S]*0\.40%[\s\S]*USD 0\.40/u);
  assert.match(document, /Partner Agreement.*恰好一个.*`PLATFORM_SIGNATORY`/u);
  assert.match(document, /`platformServicePartyId`.*`platformServiceCharge`/u);
  assert.match(document, /`CODE-MOR:CCT:COMMERCIAL_CONTEXT:5`/u);
  assert.match(document, /非 PARTNER.*`platformServicePartyId=null`.*`platformServiceCharge=null`/u);
  assert.match(document, /零个.*多个.*PLATFORM_SIGNATORY.*Fail Closed/u);
});
