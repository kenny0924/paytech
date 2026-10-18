import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { CLOSED_ENUM_VALUES } from "./client-contract-field-metadata.mjs";

const workspaceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
// The Client & Contract catalog is split into the client-master and commercial
// databases; scenarios are checked against the combined catalog view.
const modelPaths = [
  "paytech/client-master/docs/database/client-master.pdma.json",
  "paytech/commercial/docs/database/commercial.pdma.json",
].map((file) => path.join(workspaceRoot, file));
// Old CCT database artifacts split into client-master and commercial; the
// scenario document (agent-docs) still links the old paths until its text update.
const retiredScenarioLinkTargets = Object.freeze({
  "client-contract-service/docs/database/client-contract.pdma.json": [
    "paytech/client-master/docs/database/client-master.pdma.json",
    "paytech/commercial/docs/database/commercial.pdma.json",
  ],
  "client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md": [
    "paytech/client-master/docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md",
    "paytech/commercial/docs/database/COMMERCIAL_PHYSICAL_DECISIONS.md",
  ],
});
// Moved from Commerce; not part of the Client & Contract catalog or its scenarios.
// Step 08: paytech-messaging consumer tables are not CCT catalog tables either.
const nonCatalogTables = new Set(["t_client_stored_card_setting", "t_consumed_event", "t_consumed_aggregate_version"]);
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
const partnerApplicationLifecycle = Object.freeze({
  scenarioId: "roles-and-onboarding",
  table: "t_partner_application",
  qualificationType: "PARTNER_DUE_DILIGENCE",
  statuses: ["DRAFT", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN"],
  requestedByAccountReference: "PLATFORM_ACCOUNT_EXTERNAL_NO_FK",
  riskCoordinatesReference: "RISK_EXTERNAL_NO_FK",
  applicationDocumentParent: "partner_application_id",
  startOrResumeTransaction: {
    atomicWrites: [
      "PENDING_LOCAL_PARTNER",
      "DRAFT_PARTNER_APPLICATION",
      "BCR_ROOT_AND_VERSION",
      "COMPENSATION_EVENT:ProvisionAccessTenant",
    ],
    compensationEventExecutionType: "LOCAL_HANDLER",
    compensationEventDispatch: "AFTER_COMMIT",
  },
  submitTransaction: {
    atomicWrites: [
      "SUBMITTED_PARTNER_APPLICATION",
      "APPLICATION_DOCUMENT_FILE_IDS",
      "SUBMISSION_SNAPSHOT_AND_HASH",
      "BCR_ROOT_AND_VERSION",
      "COMPENSATION_EVENT:RiskDueDiligence",
    ],
    compensationEventExecutionType: "LOCAL_HANDLER",
    compensationEventDispatch: "AFTER_COMMIT",
  },
  idempotency: {
    sameKeySameHash: "RETURN_EXISTING_RESULT",
    sameKeyDifferentHash: "CONFLICT",
  },
  statusUpdateBeforeProvision: {
    outcome: "RETRYABLE",
    tenantCreation: "FORBIDDEN_WITHOUT_REQUESTED_BY_ACCOUNT_ID",
  },
  eventOrder: [
    "PLATFORM_ACCOUNT_VERIFIED",
    "CCT_DRAFT_PARTNER_APPLICATION_AND_BCR_COMMITTED",
    "PROVISION_ACCESS_TENANT_AFTER_COMMIT",
    "ONBOARDING_FILES_UPLOADED_TO_RESOLVED_PENDING_TENANT",
    "CCT_SUBMITTED_SNAPSHOT_AND_BCR_COMMITTED",
    "RISK_DUE_DILIGENCE_SUBMISSION_AFTER_COMMIT",
    "RISK_QUALIFICATION_DECISION",
    "CCT_APPROVED_STATUS_PROJECTION",
  ],
  rejectedResubmission: "REUSE_PENDING_LOCAL_PARTNER",
});

function readModel() {
  const models = modelPaths.map((file) => JSON.parse(readFileSync(file, "utf8")));
  const entities = new Map();
  for (const model of models) {
    for (const entity of model.entities) {
      if (nonCatalogTables.has(entity.defKey)) continue;
      // BCR and compensation tables exist in both databases with identical definitions.
      if (entities.has(entity.defKey)) {
        assert.deepEqual(entity, entities.get(entity.defKey), `shared table differs: ${entity.defKey}`);
        continue;
      }
      entities.set(entity.defKey, entity);
    }
  }
  return { entities: [...entities.values()], diagrams: models.flatMap((model) => model.diagrams) };
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

function readPartnerProvisionCommand(document) {
  const match = document.match(
    /after-commit 向 Platform 发送 PENDING Tenant\/OWNER Member\/Onboarding Role 的命令：\s*```json\s*([\s\S]*?)\s*```/u,
  );
  assert.ok(match, "Gulf 场景缺少 after-commit Platform Provision JSON 命令");
  return JSON.parse(match[1]);
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
    tables: 38,
    fields: 428,
    uniqueIndexes: 61,
    accessIndexes: 43,
    relations: 64,
    diagramGroups: 6,
  };
  assert.deepEqual(modelTotals(model), approvedTotals);
  assert.deepEqual(manifest.modelTotals, approvedTotals);

  const referencedTables = new Set();
  const referencedTableCounts = new Map();
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
      referencedTableCounts.set(tableKey, (referencedTableCounts.get(tableKey) ?? 0) + 1);
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
    "现实场景必须覆盖当前全部 40 张物理表",
  );
  for (const tableKey of entities.keys()) {
    assert.equal(
      referencedTableCounts.get(tableKey),
      1,
      `${tableKey} 必须且只能映射到一个现实场景`,
    );
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

test("现实场景 Manifest 固定 Partner Application 的前租户生命周期与本地事件边界", () => {
  const manifest = readScenarioManifest(readScenarioDocument());
  const scenario = manifest.scenarios.find(({ id }) => id === partnerApplicationLifecycle.scenarioId);
  assert.ok(scenario, "Partner Application 必须映射到入驻场景");
  assert.equal(scenario.tables.includes(partnerApplicationLifecycle.table), true);
  for (const fieldPath of [
    "t_client_application.requested_by_account_id",
    "t_partner_application.application_id",
    "t_partner_application.local_partner_id",
    "t_partner_application.requested_by_account_id",
    "t_partner_application.qualification_type",
    "t_partner_application.submission_content_hash",
    "t_application_document.partner_application_id",
  ]) assert.equal(scenario.fieldRefs.includes(fieldPath), true, `Partner Application 场景字段缺失：${fieldPath}`);

  const { platformProvisionCommand, ...lifecycle } = manifest.partnerApplicationLifecycle;
  assert.deepEqual(lifecycle, partnerApplicationLifecycle);
  assert.ok(platformProvisionCommand, "Partner Application 生命周期必须声明 Platform Provision 命令");
});

test("现实场景独立校验 Gulf Platform Provision 命令、幂等和乱序边界", () => {
  const document = readScenarioDocument();
  const manifest = readScenarioManifest(document);
  const payload = readPartnerProvisionCommand(document);

  assert.deepEqual(
    Object.keys(payload).sort(),
    [
      "operation_type",
      "tenant_type",
      "owner_ref_id",
      "requested_by_account_id",
      "source_version",
      "payload_hash",
    ].sort(),
  );
  assert.equal(payload.operation_type, "PROVISION_ACCESS_TENANT");
  assert.equal(payload.tenant_type, "PARTNER");
  assert.equal(payload.owner_ref_id, "730300000000000001");
  assert.equal(payload.requested_by_account_id, "220301");
  assert.equal(payload.source_version, 1);
  assert.equal(typeof payload.owner_ref_id, "string");
  assert.equal(typeof payload.requested_by_account_id, "string");
  assert.equal(typeof payload.source_version, "number");
  assert.match(payload.payload_hash, /^[a-f0-9]{64}$/u);
  assert.deepEqual(manifest.partnerApplicationLifecycle.platformProvisionCommand, payload);

  const identity = `${payload.tenant_type}:${payload.owner_ref_id}:${payload.requested_by_account_id}:${payload.source_version}`;
  assert.equal(identity, "PARTNER:730300000000000001:220301:1");
  assert.equal(document.includes(`命令身份为 \`${identity}\``), true);
  assert.match(document, /同版本同 Hash 返回原 Tenant\/Member；同版本异 Hash 为 `CONFLICT`；旧版本拒绝/u);
  assert.match(document, /同一 `tenant_type` \+ `owner_ref_id` Owner tuple 的状态投影只接受严格单调递增 `source_version`/u);
  assert.match(document, /Status 投影若先于 Provision 到达只能重试，不能创建缺少 `requested_by_account_id` 的 Tenant/u);
  assert.match(document, /START_OR_RESUME.*DRAFT.*ProvisionAccessTenant/u);
  assert.match(document, /PLT-I04.*Account Context.*PENDING.*TENANT_ONBOARDING_OWNER/u);
  assert.match(document, /SUBMIT.*File ID.*RiskDueDiligence/u);
  assert.match(document, /after-commit 向 Platform 发送 PENDING Tenant\/OWNER Member\/Onboarding Role 的命令/u);
  assert.deepEqual(manifest.partnerApplicationLifecycle.startOrResumeTransaction, partnerApplicationLifecycle.startOrResumeTransaction);
  assert.deepEqual(manifest.partnerApplicationLifecycle.submitTransaction, partnerApplicationLifecycle.submitTransaction);
});

test("现实场景文档从 Agent、数据库和 CCT 当前导航均可到达", () => {
  readScenarioDocument();
  const navigationContracts = [
    ["agent-docs/README.md", "./database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md"],
    ["agent-docs/database/README.md", "./scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md"],
    ["agent-docs/database/CLIENT_CONTRACT_DATABASE.md", "./scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md"],
    [
      "paytech/client-master/docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md",
      "../../../../agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md",
    ],
    [
      "paytech/commercial/docs/database/COMMERCIAL_PHYSICAL_DECISIONS.md",
      "../../../../agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md",
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
    const linkPath = target.split("#", 1)[0].replace(/^<|>$/gu, "");
    const targetPath = path.resolve(path.dirname(scenarioPath), linkPath);
    const retired = retiredScenarioLinkTargets[path.relative(workspaceRoot, targetPath)];
    if (retired) {
      // agent-docs text is updated in a later step; the split replacements must exist.
      assert.equal(existsSync(targetPath), false, `已拆分的旧目标仍存在：${target}`);
      for (const replacement of retired) {
        assert.ok(existsSync(path.join(workspaceRoot, replacement)), `拆分后的替代目标不存在：${replacement}`);
      }
      continue;
    }
    assert.ok(existsSync(targetPath), `现实场景文档链接目标不存在：${target}`);
  }
});

test("现实场景隔离保留 Legacy CCT-I14 Scope 闭包与失败边界", () => {
  const document = readScenarioDocument();

  assert.match(document, /场景七（legacy \/ pending migration）/u);
  assert.match(document, /`t_commercial_term_scope`[\s\S]*不是第 8 节的新写模型/u);
  assert.match(document, /不得把 `term_scope_id` 重命名为 Price ID/u);
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

test("现实场景固定可复用 Profile、准确 OE Price、组合与阶梯定价", () => {
  const document = readScenarioDocument();

  assert.match(document, /Payment Method `CARD` 是 Payment 拥有的稳定代码\/应用常量；CCT 不建立 Payment Method 表/u);
  assert.match(document, /`CARD_NETWORK` 是 OR[\s\S]*`FUNDING_TYPE`、`AUTHENTICATION_CLASS` 之间是 AND/u);
  assert.match(document, /缺少某维度才表示不限制，不能写 `\*`、CSV、Operator 或 Range/u);
  assert.match(document, /Profile 的外部身份始终是全局唯一且不可复用的 `payment_method_profile_code`/u);
  assert.match(document, /每条 `t_commercial_term_price` 都绑定准确非空 OE/u);
  assert.match(document, /具体 Profile 有 3 个不同 Dimension[\s\S]*只有具体度相同才比较更小的 `priority_no`/u);
  assert.match(document, /`PERCENTAGE`[\s\S]*`FIXED`[\s\S]*`TIERED`/u);
  assert.match(document, /多 Component 按 `component_no` 稳定计算并相加/u);
  assert.match(document, /连续半开 Band[\s\S]*`WHOLE_BASE`[\s\S]*`PROGRESSIVE`/u);
  assert.match(document, /不创建 Profile Version/u);
  assert.match(document, /新 Wire 尚未定义/u);
});
