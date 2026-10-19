import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import {
  APPROVED_ACTIVE_PATHS,
  APPROVED_DIAGRAMS,
  APPROVED_HASH_PATHS,
  APPROVED_TABLES,
  APPROVED_VERSION_PATHS,
  EXPECTED_CHECK_BINDINGS,
  EXPECTED_CHECK_CONTRACTS,
  EXPECTED_FILE_REFERENCE_TUPLES,
  EXPECTED_IMMUTABLE_TABLES,
  EXPECTED_MODEL_DIGEST,
  EXPECTED_STRUCTURAL_DIGEST,
  EXPECTED_TEMPLATE_DIGEST,
  EXPECTED_TOTALS,
  FORBIDDEN_FIELDS,
  FORBIDDEN_TABLES,
  FORBIDDEN_TABLE_FIELDS,
  REQUIRED_FIELDS,
} from "./finance-test-oracle/schema.oracle.mjs";
import {
  generateFinanceModel,
  generateFinanceServiceModels,
  serializeFinanceModel,
  serviceModelPath,
} from "./finance/generate-finance-model.mjs";
import { EXPECTED_SERVICE_TABLE_COUNTS, FINANCE_SERVICES } from "./finance/service-split.mjs";
import {
  CHECK_BINDINGS as MODEL_CHECK_BINDINGS,
  CHECK_CONTRACTS as MODEL_CHECK_CONTRACTS,
  FILE_REFERENCE_TUPLES as MODEL_FILE_REFERENCE_TUPLES,
} from "./finance/model-policy.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const paths = Object.freeze({
  generator: path.join(workspaceRoot, "scripts/pdmaner/finance/generate-finance-model.mjs"),
  manifest: path.join(workspaceRoot, "scripts/pdmaner/finance/manifest.mjs"),
  manifestDirectory: path.join(workspaceRoot, "scripts/pdmaner/finance/manifests"),
  policy: path.join(workspaceRoot, "scripts/pdmaner/finance/model-policy.mjs"),
  oracle: path.join(workspaceRoot, "scripts/pdmaner/finance-test-oracle/schema.oracle.mjs"),
  decisions: path.join(workspaceRoot, "paytech/ledger/docs/database/FINANCE_PHYSICAL_DECISIONS.md"),
  template: path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json"),
});

const idPattern = /^[A-F0-9]{8}-[A-F0-9]{4}-5[A-F0-9]{3}-[89AB][A-F0-9]{3}-[A-F0-9]{12}$/u;
const mysqlMappingId = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
const javaMappingId = "797A1496-D649-4261-89B4-544132EC3F36";
const mybatisMappingId = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function assertArtifactsExist() {
  for (const [name, artifactPath] of Object.entries(paths)) {
    assert.ok(existsSync(artifactPath), `Finance ${name} 尚未生成：${artifactPath}`);
  }
}

// 第 07 步起 Finance 不再提交单一 84 表模型：逻辑模型由生成器在内存构建，生成器维护的服务库模型是它的物理投影
// （account、ledger 已脱离生成器直接维护 JSON）。下面的结构断言针对逻辑模型；"service artifacts" 用例逐字核对已提交服务模型等于投影。
let logicalModelBytes;

function readModelBytes() {
  assertArtifactsExist();
  for (const service of FINANCE_SERVICES) {
    assert.ok(existsSync(serviceModelPath(service)), `Finance ${service} 模型尚未生成：${serviceModelPath(service)}`);
  }
  logicalModelBytes ??= Buffer.from(serializeFinanceModel(generateFinanceModel()), "utf8");
  return logicalModelBytes;
}

function readModel() {
  return JSON.parse(readModelBytes().toString("utf8"));
}

function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  const relations = model.entities.flatMap((entity) => entity.correlations);
  return {
    tables: model.entities.length,
    fields: model.entities.flatMap((entity) => entity.fields).length,
    uniqueIndexes: indexes.filter((index) => index.unique).length,
    accessIndexes: indexes.filter((index) => !index.unique).length,
    relations: relations.length,
    oneToOneRelations: relations.filter((relation) => relation.myRows === "1").length,
    diagramGroups: model.diagrams.length,
  };
}

// Test-owned projection: deliberately does not import the production generator or manifests.
function structuralProjection(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return {
    mappings: model.dataTypeMapping.mappings.map((mapping) => ({
      id: mapping.id,
      defKey: mapping.defKey,
      mysql: mapping[mysqlMappingId] ?? null,
      java: mapping[javaMappingId] ?? null,
      mybatis: mapping[mybatisMappingId] ?? null,
    })),
    domains: model.domains.map((domain) => ({
      id: domain.id,
      defKey: domain.defKey,
      applyFor: domain.applyFor,
      len: domain.len,
      scale: domain.scale,
    })),
    namingRules: model.namingRules.map((rule) => ({
      id: rule.id,
      defName: rule.defName,
      programCode: rule.programCode,
    })),
    entities: model.entities.map((entity) => {
      const fieldsById = new Map(entity.fields.map((field) => [field.id, field]));
      return {
        id: entity.id,
        defKey: entity.defKey,
        defName: entity.defName,
        comment: entity.comment,
        fields: entity.fields.map((field) => ({
          id: field.id,
          defKey: field.defKey,
          defName: field.defName,
          comment: field.comment,
          type: field.type,
          len: field.len,
          scale: field.scale,
          primaryKey: field.primaryKey,
          notNull: field.notNull,
          autoIncrement: field.autoIncrement,
          defaultValue: field.defaultValue,
          baseType: field.baseType,
          domain: field.domain,
        })),
        indexes: entity.indexes.map((index) => ({
          id: index.id,
          defKey: index.defKey,
          unique: index.unique,
          fields: index.fields.map((entry) => fieldsById.get(entry.fieldDefKey)?.defKey),
        })),
        correlations: entity.correlations.map((relation) => {
          const parent = entitiesById.get(relation.refEntity);
          const parentField = parent?.fields.find((field) => field.id === relation.refField);
          return {
            id: relation.id,
            childField: fieldsById.get(relation.myField)?.defKey,
            parentTable: parent?.defKey,
            parentField: parentField?.defKey,
            myRows: relation.myRows,
            refRows: relation.refRows,
          };
        }),
      };
    }),
    diagrams: model.diagrams.map((diagram) => ({
      id: diagram.id,
      defKey: diagram.defKey,
      defName: diagram.defName,
      primaryTables: diagram.properties?.primaryTables ?? [],
      referenceTables: diagram.properties?.referenceTables ?? [],
      relations: diagram.canvasData.cells
        .filter((cell) => cell.shape === "erdRelation")
        .map((cell) => ({ id: cell.id, originKey: cell.originKey, relation: cell.relation })),
    })),
  };
}

function fieldPaths(model, predicate) {
  return model.entities.flatMap((entity) => entity.fields
    .filter(predicate)
    .map((field) => `${entity.defKey}.${field.defKey}`));
}

test("Finance Oracle freezes exactly 84 approved tables and seven Manual Payout tables", () => {
  assert.equal(APPROVED_TABLES.length, 84);
  assert.equal(new Set(APPROVED_TABLES).size, 84);
  assert.ok(APPROVED_TABLES.every((table) => /^t_[a-z][a-z0-9_]*$/u.test(table)));
  assert.deepEqual(APPROVED_TABLES.filter((table) => [
    "t_beneficiary_account",
    "t_finance_restriction_proj",
    "t_payout",
    "t_payout_approval",
    "t_payout_execution",
    "t_payout_evidence_ref",
    "t_payout_event",
  ].includes(table)), [
    "t_beneficiary_account",
    "t_finance_restriction_proj",
    "t_payout",
    "t_payout_approval",
    "t_payout_execution",
    "t_payout_evidence_ref",
    "t_payout_event",
  ]);
});

test("Finance artifact is deterministic, current and byte-approved", () => {
  assertArtifactsExist();
  assert.equal(sha256(readFileSync(paths.template)), EXPECTED_TEMPLATE_DIGEST);
  const result = spawnSync(process.execPath, [paths.generator, "--check"], {
    cwd: workspaceRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  for (const service of FINANCE_SERVICES) {
    assert.match(result.stdout, new RegExp(`${service} model is up to date`, "u"));
  }
  assert.equal(sha256(readModelBytes()), EXPECTED_MODEL_DIGEST);
});

test("Finance service artifacts are exact per-database projections of the logical model", () => {
  const logical = generateFinanceModel();
  const serviceModels = generateFinanceServiceModels(logical);
  const owners = new Map();
  for (const service of FINANCE_SERVICES) {
    const artifact = readFileSync(serviceModelPath(service), "utf8");
    const model = serviceModels.get(service);
    assert.equal(artifact, serializeFinanceModel(model), `${service} 模型与逻辑模型投影不一致`);
    assert.equal(model.entities.length, EXPECTED_SERVICE_TABLE_COUNTS[service], `${service} 表数`);
    assert.equal(model.dbConn.length, 0, `${service} 模型不得保存数据库连接`);
    const entityIds = new Set(model.entities.map((entity) => entity.id));
    for (const entity of model.entities) {
      for (const relation of entity.correlations) {
        assert.ok(entityIds.has(relation.refEntity), `${service}.${entity.defKey} 不得保留跨库关系`);
      }
      owners.set(entity.defKey, [...(owners.get(entity.defKey) ?? []), service]);
    }
  }
  assert.deepEqual(owners.get("t_compensation_event"), [...FINANCE_SERVICES], "补偿事件每库一份");
  assert.deepEqual(owners.get("t_currency_spec"), ["billing"], "币种规则在生成器维护的服务库中只有 billing 持只读副本");
  assert.ok(!owners.has("t_command_idempotency"), "t_command_idempotency 已删除");
  // account（第 11b 步）与 ledger（2026-10-19 控制科目重构）服务库模型已脱离生成器直接维护，不在此投影；逻辑模型仍为 84 张表。
  // ledger 逻辑切分的 29 张表中，27 张只属于 ledger（另两张为每库一份的补偿事件与 currency_spec 只读副本）。
  for (const table of ["t_account", "t_ledger_book", "t_accounting_instruction", "t_period_close"]) {
    assert.ok(!owners.has(table), `${table} 属于 ledger，不再投影`);
  }
  assert.equal(owners.size, 84 - 1 - 27, "其余每张逻辑表恰属一个生成器维护的服务库");
  for (const [table, services] of owners) {
    if (!["t_compensation_event", "t_currency_spec"].includes(table)) {
      assert.equal(services.length, 1, `${table} 只能属于一个服务库`);
    }
  }
});

test("Finance model matches the independent structural digest and totals", () => {
  const model = readModel();
  assert.deepEqual(modelTotals(model), EXPECTED_TOTALS);
  assert.equal(sha256(JSON.stringify(structuralProjection(model))), EXPECTED_STRUCTURAL_DIGEST);
  assert.deepEqual(model.entities.map((entity) => entity.defKey).sort(), [...APPROVED_TABLES].sort());
});

test("Finance removes generic Funding while refund posting remains direct", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));

  assert.ok(!entities.has("t_refund_funding_reservation"));
  for (const tableKey of [
    "t_funding_position",
    "t_funding_commitment",
    "t_funding_allocation",
    "t_funding_move",
  ]) {
    assert.ok(!entities.has(tableKey), `恢复了通用 Funding 表 ${tableKey}`);
  }
});

test("Finance separates Channel transaction matching from bank reconciliation without a derived Cash Item", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexFields = (tableKey, indexKey) => {
    const entity = entities.get(tableKey);
    const index = entity?.indexes.find((candidate) => candidate.defKey === indexKey);
    return index?.fields.map((entry) => entity.fields
      .find((field) => field.id === entry.fieldDefKey)?.defKey) ?? [];
  };
  const relationTarget = (tableKey, fieldKey) => {
    const entity = entities.get(tableKey);
    const field = entity?.fields.find((candidate) => candidate.defKey === fieldKey);
    const relation = entity?.correlations.find((candidate) => candidate.myField === field?.id);
    return model.entities.find((candidate) => candidate.id === relation?.refEntity)?.defKey;
  };

  for (const removedTable of [
    "t_channel_settle_alloc",
    "t_channel_cash_item",
    "t_recon_match",
  ]) {
    assert.ok(!entities.has(removedTable), `仍保留旧对账表 ${removedTable}`);
  }

  assert.ok(entities.has("t_channel_transaction_match"));
  assert.ok(entities.has("t_bank_recon_match"));
  assert.deepEqual(fieldKeys("t_channel_transaction_match"), [
    "id",
    "settle_item_id",
    "target_type",
    "target_key",
    "open_item_id",
    "debit_item_id",
    "allocation_no",
    "gross_amount",
    "fee_amount",
    "net_amount",
    "create_time",
  ]);
  assert.match(
    entities.get("t_channel_transaction_match").comment,
    /本地冻结.*无需在线查询 Payment/u,
  );
  assert.match(
    entities.get("t_channel_transaction_match").comment,
    /PAYMENT.*DUPLICATE_CAPTURE.*REFUND.*DISPUTE.*FEE.*ADJUSTMENT.*Cost Confirm.*Recon Exception/u,
  );
  assert.equal(
    relationTarget("t_channel_transaction_match", "settle_item_id"),
    "t_channel_settle_item",
  );
  assert.equal(relationTarget("t_channel_transaction_match", "open_item_id"), "t_channel_open_item");
  assert.equal(relationTarget("t_channel_transaction_match", "debit_item_id"), "t_channel_debit_item");

  assert.ok(fieldKeys("t_bank_recon_match").includes("channel_batch_id"));
  assert.ok(!fieldKeys("t_bank_recon_match").includes("channel_cash_item_id"));
  assert.equal(relationTarget("t_bank_recon_match", "channel_batch_id"), "t_channel_settle_batch");
  assert.deepEqual(
    indexFields("t_bank_recon_match", "idx_bank_recon_channel"),
    ["channel_batch_id", "status"],
  );
  assert.deepEqual(
    indexFields("t_channel_settle_batch", "idx_channel_batch_recon"),
    ["channel_account_id", "status", "cash_direction", "settlement_date"],
  );

  const accountPurpose = entities.get("t_cash_account_binding").fields
    .find((field) => field.defKey === "account_purpose");
  assert.match(accountPurpose.comment, /允许值：CHANNEL_SETTLEMENT、PAYOUT、TAX。$/u);
  assert.doesNotMatch(accountPurpose.comment, /REFUND/u);

  const diagram = model.diagrams.find((candidate) => candidate.defKey === "channel_cash_recon");
  assert.equal(diagram?.properties?.primaryTables.length, 15);
  assert.ok(diagram?.properties?.primaryTables.includes("t_channel_transaction_match"));
  assert.ok(diagram?.properties?.primaryTables.includes("t_bank_recon_match"));
  assert.ok(!diagram?.properties?.primaryTables.includes("t_channel_cash_item"));
});

test("Finance RE_MATCH resolution identifies exactly one reconciliation layer", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const resolution = entities.get("t_recon_resolution");
  const fields = new Map(resolution.fields.map((field) => [field.defKey, field]));
  const relationFor = (fieldKey) => resolution.correlations
    .find((relation) => relation.myField === fields.get(fieldKey)?.id);
  const targetFor = (fieldKey) => model.entities
    .find((entity) => entity.id === relationFor(fieldKey)?.refEntity)?.defKey;

  assert.ok(!fields.has("recon_match_id"));
  assert.ok(fields.has("channel_transaction_match_id"));
  assert.ok(fields.has("bank_recon_match_id"));
  assert.equal(relationFor("channel_transaction_match_id")?.myRows, "1");
  assert.equal(relationFor("bank_recon_match_id")?.myRows, "1");
  assert.equal(targetFor("channel_transaction_match_id"), "t_channel_transaction_match");
  assert.equal(targetFor("bank_recon_match_id"), "t_bank_recon_match");
  assert.match(
    MODEL_CHECK_CONTRACTS.ck_recon_resolution_execution,
    /channel_transaction_match_id IS NOT NULL AND bank_recon_match_id IS NULL.*channel_transaction_match_id IS NULL AND bank_recon_match_id IS NOT NULL/u,
  );
});

test("Finance models security deposit and recovery through Account plus Journal only", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const deletedTables = new Set([
    "t_reserve_case",
    "t_restricted_fund",
    "t_restricted_fund_move",
    "t_recovery_case",
    "t_recovery_application",
  ]);

  for (const tableKey of deletedTables) {
    assert.ok(!entities.has(tableKey), `仍保留已删除的 ${tableKey}`);
  }
  assert.ok(!model.diagrams.some((diagram) => diagram.defKey === "balance_reserve_recovery"));
  const ledgerAndClose = model.diagrams.find((diagram) => diagram.defKey === "ledger_and_close");
  assert.ok(ledgerAndClose?.properties?.primaryTables.includes("t_ledger_period_balance"));

  for (const removedSnapshot of ["t_candidate_requirement_snapshot", "t_settlement_req_snapshot"]) {
    assert.ok(!entities.has(removedSnapshot), `仍保留已删除的 ${removedSnapshot}`);
  }
  const serializedModel = JSON.stringify(model);
  for (const accountCode of [
    "CLIENT_SECURITY_DEPOSIT",
    "CLIENT_RECOVERY_RECEIVABLE",
    "PARTNER_RECOVERY_RECEIVABLE",
  ]) {
    assert.match(serializedModel, new RegExp(accountCode, "u"), `缺少账户语义 ${accountCode}`);
  }
  assert.match(
    MODEL_CHECK_CONTRACTS.ck_account_owner_bucket,
    /owner_type='CLIENT'.*NONE.*owner_type='LOCAL_PARTNER'.*NONE.*PENDING.*AVAILABLE.*LOCKED/u,
  );
  const account = entities.get("t_account");
  const balanceBucket = account.fields.find((field) => field.defKey === "balance_bucket");
  assert.match(
    account.comment,
    /PARTNER_PAYABLE.*PENDING、AVAILABLE、LOCKED.*PARTNER_RECOVERY_RECEIVABLE\/NONE/u,
  );
  assert.match(
    balanceBucket.comment,
    /PARTNER_PAYABLE.*PENDING、AVAILABLE、LOCKED.*PARTNER_RECOVERY_RECEIVABLE.*NONE/u,
  );
  assert.doesNotMatch(
    MODEL_CHECK_CONTRACTS.ck_account_owner_bucket,
    /PARTNER_PAYABLE|PARTNER_RECOVERY_RECEIVABLE/u,
    "单行 owner/bucket CHECK 不得冒充跨行 Chart Account code 与 bucket 的一致性门禁",
  );

  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity.defKey]));
  for (const entity of model.entities) {
    for (const relation of entity.correlations) {
      assert.ok(!deletedTables.has(entitiesById.get(relation.refEntity)));
    }
  }
});

test("Finance identifiers, metadata, keys and indexes satisfy physical conventions", () => {
  const model = readModel();
  const generatedIds = [];
  for (const entity of model.entities) {
    assert.match(entity.defKey, /^t_[a-z][a-z0-9_]*$/u);
    assert.ok(entity.defKey.length <= 32, `${entity.defKey} 超过 32 字符`);
    assert.match(entity.defName, /[\u3400-\u9fff]/u);
    assert.match(entity.comment, /[\u3400-\u9fff]/u);
    assert.doesNotMatch(entity.comment, /\bfor\s+t_[a-z0-9_]+\b/iu);
    generatedIds.push(entity.id);

    const primaryKeys = entity.fields.filter((field) => field.primaryKey);
    assert.equal(primaryKeys.length, 1, `${entity.defKey} 必须恰有一个主键`);
    assert.equal(primaryKeys[0].type, "BIGINT");
    assert.equal(primaryKeys[0].autoIncrement, false);
    assert.ok(entity.fields.some((field) => field.defKey === "create_time"));
    assert.equal(new Set(entity.fields.map((field) => field.defKey)).size, entity.fields.length);

    for (const field of entity.fields) {
      generatedIds.push(field.id);
      assert.match(field.defKey, /^[a-z][a-z0-9_]*$/u);
      assert.ok(field.defKey.length <= 32, `${entity.defKey}.${field.defKey} 超过 32 字符`);
      assert.match(field.defName, /[\u3400-\u9fff]/u, `${entity.defKey}.${field.defKey} 缺少中文名`);
      assert.match(field.comment, /[\u3400-\u9fff]/u, `${entity.defKey}.${field.defKey} 缺少中文说明`);
      assert.equal(field.autoIncrement, false);
      if (/(?:^|_)(?:code|type|status|currency|country|direction|decision|result|bucket|side|mode|role|method|validation|bearer)$/u.test(field.defKey)) {
        assert.match(field.comment, /允许值：|取值来源：/u, `${entity.defKey}.${field.defKey} 缺少取值契约`);
      }
      if (["active_slot", "active_lock_flag"].includes(field.defKey)) {
        assert.match(field.comment, /1/u);
        assert.match(field.comment, /NULL/u);
      }
    }

    const fieldIds = new Set(entity.fields.map((field) => field.id));
    const signatures = new Set();
    for (const index of entity.indexes) {
      generatedIds.push(index.id, ...index.fields.map((entry) => entry.id));
      assert.match(index.defKey, index.unique ? /^uk_/u : /^idx_/u);
      assert.ok(index.defKey.length <= 32);
      const signature = `${index.unique}:${index.fields.map((entry) => entry.fieldDefKey).join(",")}`;
      assert.ok(!signatures.has(signature), `${entity.defKey} 重复索引签名 ${signature}`);
      signatures.add(signature);
      for (const entry of index.fields) assert.ok(fieldIds.has(entry.fieldDefKey));
    }
    generatedIds.push(...entity.correlations.map((relation) => relation.id));
  }
  generatedIds.push(...model.diagrams.flatMap((diagram) => [
    diagram.id,
    ...diagram.canvasData.cells.map((cell) => cell.id),
  ]));
  assert.equal(new Set(generatedIds).size, generatedIds.length, "生成对象 ID 必须全局唯一");
  for (const id of generatedIds) assert.match(id, idPattern);
});

test("Finance relations are internal, indexed and enforce one-to-one cardinality", () => {
  const model = readModel();
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  for (const child of model.entities) {
    const childFields = new Map(child.fields.map((field) => [field.id, field]));
    const primaryKey = child.fields.find((field) => field.primaryKey);
    for (const relation of child.correlations) {
      const childField = childFields.get(relation.myField);
      const parent = entitiesById.get(relation.refEntity);
      assert.ok(childField && parent, `${child.defKey} 存在跨模型或未知关系`);
      assert.ok(parent.fields.some((field) => field.id === relation.refField));
      const leftPrefix = childField.id === primaryKey.id
        || child.indexes.some((index) => index.fields[0]?.fieldDefKey === childField.id);
      assert.ok(leftPrefix, `${child.defKey}.${childField.defKey} 缺少关系左前缀索引`);
      if (relation.myRows === "1") {
        const exactUnique = child.indexes.filter((index) => index.unique
          && index.fields.length === 1
          && index.fields[0].fieldDefKey === childField.id);
        assert.ok(childField.id === primaryKey.id || exactUnique.length === 1,
          `${child.defKey}.${childField.defKey} 的 1:1 关系缺少单字段 UK`);
      }
    }
  }
});

test("Finance diagrams cover each primary table and each relation exactly once", () => {
  const model = readModel();
  assert.deepEqual(
    Object.fromEntries(model.diagrams.map((diagram) => [diagram.defKey, diagram.defName])),
    APPROVED_DIAGRAMS,
  );
  const primaryTables = model.diagrams.flatMap((diagram) => diagram.properties?.primaryTables ?? []);
  assert.equal(new Set(primaryTables).size, APPROVED_TABLES.length);
  assert.deepEqual(primaryTables.sort(), [...APPROVED_TABLES].sort());
  const correlations = model.entities.flatMap((entity) => entity.correlations).map((relation) => relation.id).sort();
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells)
    .filter((cell) => cell.shape === "erdRelation")
    .map((cell) => cell.originKey)
    .sort();
  assert.deepEqual(relationCells, correlations);
});

test("Finance Money, ratio, LocalDate and Instant mappings remain explicit", () => {
  const model = readModel();
  const mappings = new Map(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping]));
  const domains = new Map(model.domains.map((domain) => [domain.defKey, domain]));
  assert.deepEqual([domains.get("Money").len, domains.get("Money").scale], [24, 8]);
  assert.deepEqual([domains.get("Decimal").len, domains.get("Decimal").scale], [18, 10]);
  assert.equal(domains.get("DateTime").len, 6);
  assert.deepEqual([domains.get("currency").applyFor, domains.get("currency").len], [mappings.get("char").id, 3]);
  assert.deepEqual([mappings.get("date")[javaMappingId], mappings.get("date")[mybatisMappingId]], ["Instant", "Instant"]);
  assert.deepEqual(
    [mappings.get("localDate")[mysqlMappingId], mappings.get("localDate")[javaMappingId], mappings.get("localDate")[mybatisMappingId]],
    ["DATE", "LocalDate", "LocalDate"],
  );
  const dateFields = model.entities.flatMap((entity) => entity.fields.filter((field) => field.type === "DATE"));
  assert.equal(dateFields.length, 12);
  for (const field of dateFields) assert.equal(field.baseType, mappings.get("localDate").id);
  for (const field of model.entities.flatMap((entity) => entity.fields.filter((item) => item.type === "DATETIME"))) {
    assert.equal(field.baseType, mappings.get("date").id);
    assert.equal(field.domain, domains.get("DateTime").id);
    assert.equal(field.len, 6);
  }
});

test("Finance mechanical fields and reviewed cross-system tuples stay frozen", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  for (const removedTable of [
    "t_account_tree_path",
    "t_accounting_rule_artifact",
    "t_intercompany_transfer",
  ]) {
    assert.ok(!entities.has(removedTable), `恢复了已删除表 ${removedTable}`);
  }
  for (const removedTable of FORBIDDEN_TABLES) {
    assert.ok(!entities.has(removedTable), `恢复了已删除 Settlement 表 ${removedTable}`);
  }
  const serializedModel = JSON.stringify(model);
  for (const removedAccountCode of ["INTERCOMPANY_RECEIVABLE", "INTERCOMPANY_PAYABLE"]) {
    assert.ok(!serializedModel.includes(removedAccountCode), `恢复了未获批跨主体科目 ${removedAccountCode}`);
  }
  assert.deepEqual(
    fieldPaths(model, (field) => ["version", "balance_version"].includes(field.defKey)),
    APPROVED_VERSION_PATHS,
  );
  assert.deepEqual(fieldPaths(model, (field) => field.defKey.endsWith("_hash")), APPROVED_HASH_PATHS);
  assert.deepEqual(
    fieldPaths(model, (field) => ["active_slot", "active_lock_flag"].includes(field.defKey)),
    APPROVED_ACTIVE_PATHS,
  );
  for (const [tableKey, fieldKeys] of Object.entries(REQUIRED_FIELDS)) {
    const actual = new Set(entities.get(tableKey)?.fields.map((field) => field.defKey));
    for (const fieldKey of fieldKeys) assert.ok(actual.has(fieldKey), `缺少 ${tableKey}.${fieldKey}`);
  }
  assert.deepEqual(MODEL_FILE_REFERENCE_TUPLES, EXPECTED_FILE_REFERENCE_TUPLES);
  assert.deepEqual(
    EXPECTED_FILE_REFERENCE_TUPLES.map(({ table, id, hash }) => {
      const fields = new Map(entities.get(`t_${table}`)?.fields.map((field) => [field.defKey, field]));
      return {
        table,
        id,
        hash,
        idNotNull: fields.get(id)?.notNull,
        hashNotNull: fields.get(hash)?.notNull,
      };
    }),
    [
      { table: "tax_return", id: "file_id", hash: "file_content_hash", idNotNull: false, hashNotNull: false },
      { table: "manual_adjustment", id: "file_id", hash: "file_content_hash", idNotNull: false, hashNotNull: false },
      { table: "channel_settle_batch", id: "file_id", hash: "file_content_hash", idNotNull: true, hashNotNull: true },
      { table: "bank_statement", id: "file_id", hash: "file_content_hash", idNotNull: true, hashNotNull: true },
      { table: "payout_evidence_ref", id: "file_id", hash: "file_content_hash", idNotNull: true, hashNotNull: true },
    ],
  );
  for (const entity of model.entities) {
    for (const field of entity.fields) {
      assert.ok(!FORBIDDEN_FIELDS.includes(field.defKey), `恢复了禁止字段 ${entity.defKey}.${field.defKey}`);
    }
  }
  for (const [tableKey, fieldKeys] of Object.entries(FORBIDDEN_TABLE_FIELDS)) {
    const actual = new Set(entities.get(tableKey)?.fields.map((field) => field.defKey));
    for (const fieldKey of fieldKeys) {
      assert.ok(!actual.has(fieldKey), `恢复了已移除字段 ${tableKey}.${fieldKey}`);
    }
  }
  assert.ok(!entities.get("t_finance_restriction_proj").fields.some((field) => field.defKey === "active_slot"));
  assert.ok(!entities.get("t_command_idempotency").fields.some((field) => field.defKey === "version"));
  assert.ok(!entities.get("t_compensation_event").fields.some((field) => field.defKey === "version"));
  assert.ok(entities.get("t_compensation_event").fields.some((field) => field.defKey === "lock_version"));
  const channelCostConfirmFields = new Set(
    entities.get("t_channel_cost_confirm").fields.map((field) => field.defKey),
  );
  for (const removedField of ["channel_id", "settle_batch_ref", "channel_item_ref", "currency"]) {
    assert.ok(!channelCostConfirmFields.has(removedField), `渠道实际成本恢复了冗余字段 ${removedField}`);
  }
});

test("Finance shares Client and Partner maturity and uses direct Payout and Tax controls", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexFields = (tableKey, indexKey) => {
    const entity = entities.get(tableKey);
    const index = entity?.indexes.find((candidate) => candidate.defKey === indexKey);
    return index?.fields.map((entry) => entity.fields
      .find((field) => field.id === entry.fieldDefKey)?.defKey) ?? [];
  };
  const relationTarget = (tableKey, fieldKey) => {
    const entity = entities.get(tableKey);
    const field = entity?.fields.find((candidate) => candidate.defKey === fieldKey);
    const relation = entity?.correlations.find((candidate) => candidate.myField === field?.id);
    return model.entities.find((candidate) => candidate.id === relation?.refEntity)?.defKey;
  };
  for (const removedTable of [
    "t_balance_lot",
    "t_balance_lot_bucket",
    "t_balance_lot_move",
    "t_availability_policy",
    "t_availability_policy_ver",
    "t_availability_policy_scope",
    "t_availability_condition",
    "t_availability_evaluation",
    "t_availability_eval_reason",
    "t_settlement_line",
    ...FORBIDDEN_TABLES,
  ]) {
    assert.ok(!entities.has(removedTable), `恢复了已删除余额/可用性表 ${removedTable}`);
  }

  const serializedModel = JSON.stringify(model);
  for (const staleToken of ["BALANCE_LOT", "BALANCE_LOT_BUCKET", "LOT_AVAILABILITY"]) {
    assert.ok(!serializedModel.includes(staleToken), `恢复了已删除余额类型 ${staleToken}`);
  }
  for (const staleField of ["balance_lot_id", "locked_bucket_id", "active_lock_flag"]) {
    assert.ok(
      !model.entities.some((entity) => entity.fields.some((field) => field.defKey === staleField)),
      `恢复了已删除余额字段 ${staleField}`,
    );
  }

  const economics = entities.get("t_economic_fact");
  for (const fieldKey of ["payment_succeeded_time", "balance_available_time"]) {
    const field = economics.fields.find((candidate) => candidate.defKey === fieldKey);
    assert.deepEqual(
      [field?.type, field?.len, field?.notNull],
      ["DATETIME", 6, false],
      `交易 Economics 必须冻结可空 UTC 微秒时间 ${fieldKey}`,
    );
  }
  assert.deepEqual(
    indexFields("t_economic_fact", "idx_econ_balance_available"),
    ["fact_type", "balance_available_time"],
  );
  assert.ok(!fieldKeys("t_economic_fact").includes("client_available_time"));
  assert.match(
    MODEL_CHECK_CONTRACTS.ck_txn_economics_type,
    /fact_type='PAYMENT'.*payment_succeeded_time IS NOT NULL.*balance_available_time IS NOT NULL/u,
  );
  assert.match(
    MODEL_CHECK_CONTRACTS.ck_txn_economics_maturity_time,
    /balance_available_time>=payment_succeeded_time/u,
  );
  assert.match(
    MODEL_CHECK_CONTRACTS.ck_txn_economics_type,
    /fact_type='REFUND'.*payment_succeeded_time IS NULL.*balance_available_time IS NULL/u,
  );

  assert.deepEqual(indexFields("t_payout", "idx_payout_available"), ["available_account_id", "status"]);
  assert.deepEqual(indexFields("t_payout", "idx_payout_locked"), ["locked_account_id", "status"]);
  assert.deepEqual(
    indexFields("t_payout_execution", "idx_payout_execution_cash"),
    ["cash_account_id", "status"],
  );

  assert.deepEqual(
    ["tax_payable_account_id", "active_slot"].filter((field) => !fieldKeys("t_tax_payment").includes(field)),
    [],
  );
  for (const field of ["funding_commitment_id", "tax_payable_allocation_id", "bank_cash_allocation_id"]) {
    assert.ok(!fieldKeys("t_tax_payment").includes(field));
  }
  assert.deepEqual(indexFields("t_tax_payment", "uk_tax_payment_active"), ["tax_return_id", "active_slot"]);
  assert.deepEqual(indexFields("t_tax_payment", "idx_tax_pay_payable_status"), ["tax_payable_account_id", "status"]);
  assert.deepEqual(indexFields("t_tax_payment", "idx_tax_pay_cash_status"), ["cash_account_id", "status"]);
  assert.equal(relationTarget("t_tax_payment", "tax_payable_account_id"), "t_account");
  assert.equal(
    MODEL_CHECK_CONTRACTS.ck_tax_payment_state,
    "(status IN ('CREATED','SUBMITTED','PROCESSING','BANK_ACCEPTED','UNKNOWN') AND active_slot=1) OR (status IN ('FAILED','RECONCILED') AND active_slot IS NULL)",
  );
  assert.equal(
    MODEL_CHECK_CONTRACTS.ck_tax_payment_event_evidence_pair,
    "(source_ref IS NULL AND evidence_hash IS NULL) OR (source_ref IS NOT NULL AND evidence_hash IS NOT NULL)",
  );
  assert.equal(
    MODEL_CHECK_CONTRACTS.ck_tax_payment_failed_event,
    "event_type<>'FAILED' OR (source_ref IS NOT NULL AND evidence_hash IS NOT NULL)",
  );
  assert.deepEqual(MODEL_CHECK_BINDINGS.ck_tax_payment_event_evidence_pair, ["tax_payment_event"]);
  assert.deepEqual(MODEL_CHECK_BINDINGS.ck_tax_payment_failed_event, ["tax_payment_event"]);
  assert.equal(
    MODEL_CHECK_CONTRACTS.ck_account_owner_bucket,
    "(owner_type='CLIENT' AND balance_bucket IN ('PENDING','AVAILABLE','LOCKED','NONE')) OR (owner_type='LOCAL_PARTNER' AND balance_bucket IN ('NONE','PENDING','AVAILABLE','LOCKED')) OR (owner_type IN ('OPERATING_ENTITY','FINANCE_ENTITY','CHANNEL_ACCOUNT','CASH_ACCOUNT','TAX_AUTHORITY') AND balance_bucket='NONE')",
  );
});

test("Finance Manual Payout roots freeze beneficiary, amount, approval and final Journal contracts", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexFields = (tableKey, indexKey) => {
    const entity = entities.get(tableKey);
    const index = entity?.indexes.find((candidate) => candidate.defKey === indexKey);
    return index?.fields.map((entry) => entity.fields
      .find((field) => field.id === entry.fieldDefKey)?.defKey) ?? [];
  };

  assert.deepEqual(fieldKeys("t_beneficiary_account"), [
    "id", "owner_type", "owner_id", "account_code", "beneficiary_vault_token",
    "beneficiary_name_masked", "bank_country", "currency", "bank_account_blind_index",
    "bank_account_blind_key_ver", "account_last4", "vault_record_version",
    "vault_metadata_hash", "bank_code", "content_hash", "maker_id", "checker_id",
    "checker_decision", "decision_time", "status", "version", "create_time", "update_time",
  ]);
  assert.ok(!fieldKeys("t_beneficiary_account").includes("current_ver_id"));
  assert.deepEqual(indexFields("t_beneficiary_account", "uk_beneficiary_acct"), [
    "owner_type", "owner_id", "account_code",
  ]);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_beneficiary_lifecycle, EXPECTED_CHECK_CONTRACTS.ck_beneficiary_lifecycle);

  for (const fieldKey of REQUIRED_FIELDS.t_payout) {
    assert.ok(fieldKeys("t_payout").includes(fieldKey), `缺少 t_payout.${fieldKey}`);
  }
  assert.deepEqual(indexFields("t_payout", "uk_payout_request"), ["owner_type", "owner_id", "request_key"]);
  assert.deepEqual(indexFields("t_payout", "uk_payout_journal"), ["journal_entry_id"]);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_payout_amounts, EXPECTED_CHECK_CONTRACTS.ck_payout_amounts);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_payout_risk_snapshot, EXPECTED_CHECK_CONTRACTS.ck_payout_risk_snapshot);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_payout_approval_snapshot, EXPECTED_CHECK_CONTRACTS.ck_payout_approval_snapshot);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_payout_reconciled, EXPECTED_CHECK_CONTRACTS.ck_payout_reconciled);

  const amountContractAccepts = ({ requested, fee, ownerDebit, beneficiary, cashRequired, mode }) => (
    requested > 0 && fee >= 0 && ownerDebit > 0 && beneficiary > 0
    && cashRequired === ownerDebit && beneficiary + fee === ownerDebit
    && ((mode === "ADDED_ON_TOP" && ownerDebit === requested + fee && beneficiary === requested)
      || (mode === "DEDUCTED_FROM_PAYOUT" && requested > fee
        && ownerDebit === requested && beneficiary === requested - fee))
  );
  assert.equal(amountContractAccepts({ requested: 100, fee: 2, ownerDebit: 102, beneficiary: 100, cashRequired: 102, mode: "ADDED_ON_TOP" }), true);
  assert.equal(amountContractAccepts({ requested: 100, fee: 2, ownerDebit: 100, beneficiary: 98, cashRequired: 100, mode: "DEDUCTED_FROM_PAYOUT" }), true);
  assert.equal(amountContractAccepts({ requested: 100, fee: 100, ownerDebit: 100, beneficiary: 0, cashRequired: 100, mode: "DEDUCTED_FROM_PAYOUT" }), false);

  const payoutRelations = [
    "t_beneficiary_account", "t_finance_restriction_proj", "t_payout", "t_payout_approval",
    "t_payout_execution", "t_payout_evidence_ref", "t_payout_event",
  ].flatMap((tableKey) => entities.get(tableKey).correlations.map((relation) => ({ tableKey, relation })));
  const oneToOne = payoutRelations.filter(({ relation }) => relation.myRows === "1");
  assert.equal(oneToOne.length, 1);
  const payout = entities.get("t_payout");
  const journalField = payout.fields.find((field) => field.defKey === "journal_entry_id");
  assert.equal(oneToOne[0].tableKey, "t_payout");
  assert.equal(oneToOne[0].relation.myField, journalField.id);
  assert.equal(model.entities.find((entity) => entity.id === oneToOne[0].relation.refEntity)?.defKey, "t_journal_entry");
});

test("Finance Manual Payout execution, events and bank matches are idempotent and NULL-safe", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexFields = (tableKey, indexKey) => {
    const entity = entities.get(tableKey);
    const index = entity?.indexes.find((candidate) => candidate.defKey === indexKey);
    return index?.fields.map((entry) => entity.fields
      .find((field) => field.id === entry.fieldDefKey)?.defKey) ?? [];
  };

  for (const fieldKey of REQUIRED_FIELDS.t_payout_execution) {
    assert.ok(fieldKeys("t_payout_execution").includes(fieldKey), `缺少 t_payout_execution.${fieldKey}`);
  }
  assert.deepEqual(indexFields("t_payout_execution", "uk_payout_execution"), ["payout_id", "attempt_no"]);
  assert.deepEqual(indexFields("t_payout_execution", "uk_payout_execution_key"), ["execution_key"]);
  assert.deepEqual(indexFields("t_payout_execution", "uk_payout_bank_ref"), ["cash_account_id", "bank_payment_ref"]);
  assert.deepEqual(indexFields("t_payout_execution", "uk_payout_active_execution"), ["payout_id", "active_slot"]);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_payout_execution_active, EXPECTED_CHECK_CONTRACTS.ck_payout_execution_active);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_payout_execution_state, EXPECTED_CHECK_CONTRACTS.ck_payout_execution_state);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_payout_execution_risk_snapshot, EXPECTED_CHECK_CONTRACTS.ck_payout_execution_risk_snapshot);

  for (const fieldKey of REQUIRED_FIELDS.t_payout_event) {
    assert.ok(fieldKeys("t_payout_event").includes(fieldKey), `缺少 t_payout_event.${fieldKey}`);
  }
  assert.deepEqual(indexFields("t_payout_event", "uk_payout_event_no"), ["payout_id", "event_no"]);
  assert.deepEqual(indexFields("t_payout_event", "uk_payout_event_source"), ["source_type", "source_event_key"]);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_payout_event_scope, EXPECTED_CHECK_CONTRACTS.ck_payout_event_scope);

  assert.deepEqual(indexFields("t_bank_recon_match", "uk_bank_recon_payout"), [
    "payout_execution_id", "bank_item_id", "match_type",
  ]);
  assert.deepEqual(indexFields("t_bank_recon_match", "idx_bank_recon_payout"), [
    "payout_execution_id", "status",
  ]);
  assert.ok(!fieldKeys("t_bank_recon_match").includes("settlement_payment_attempt_id"));
  assert.equal(MODEL_CHECK_CONTRACTS.ck_bank_recon_match_target, EXPECTED_CHECK_CONTRACTS.ck_bank_recon_match_target);
  assert.equal(MODEL_CHECK_CONTRACTS.ck_bank_recon_payout_component, EXPECTED_CHECK_CONTRACTS.ck_bank_recon_payout_component);
  assert.match(MODEL_CHECK_CONTRACTS.ck_bank_recon_match_target, /\) IS TRUE$/u);
  assert.match(MODEL_CHECK_CONTRACTS.ck_bank_recon_payout_component, /\) IS TRUE$/u);
  assert.match(MODEL_CHECK_CONTRACTS.ck_bank_recon_match_target, /PAYOUT_DEBIT.*payout_execution_id IS NOT NULL/u);
  assert.match(MODEL_CHECK_CONTRACTS.ck_bank_recon_payout_component, /BENEFICIARY_TRANSFER.*BANK_FEE.*COMBINED/u);
});

test("Finance Tax and Fiscal keeps 23 primary tables with exact Rule and Return versions", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexFields = (tableKey, indexKey) => {
    const entity = entities.get(tableKey);
    const index = entity?.indexes.find((candidate) => candidate.defKey === indexKey);
    return index?.fields.map((entry) => entity.fields
      .find((field) => field.id === entry.fieldDefKey)?.defKey) ?? [];
  };
  const relationTarget = (tableKey, fieldKey) => {
    const entity = entities.get(tableKey);
    const field = entity?.fields.find((candidate) => candidate.defKey === fieldKey);
    const relation = entity?.correlations.find((candidate) => candidate.myField === field?.id);
    return model.entities.find((candidate) => candidate.id === relation?.refEntity)?.defKey;
  };

  for (const removedTable of [
    "t_tax_rule_version",
    "t_tax_rule_approval",
    "t_tax_return_ver",
    "t_tax_return_approval",
  ]) {
    assert.ok(!entities.has(removedTable), `Tax/Fiscal 仍保留已合并表 ${removedTable}`);
  }

  assert.deepEqual(fieldKeys("t_tax_rule"), [
    "id",
    "rule_code",
    "tax_type",
    "rule_ver",
    "seller_jurisdiction_id",
    "buyer_jurisdiction_id",
    "buyer_type",
    "category_id",
    "treatment_id",
    "effective_from",
    "effective_to",
    "priority",
    "content_hash",
    "status",
    "maker_id",
    "checker_id",
    "checker_decision",
    "decision_time",
    "version",
    "create_time",
    "update_time",
  ]);
  assert.deepEqual(indexFields("t_tax_rule", "uk_tax_rule"), ["rule_code", "rule_ver"]);
  assert.ok(!fieldKeys("t_tax_rule").includes("current_ver_id"));
  assert.equal(
    entities.get("t_tax_rule")?.fields.find((field) => field.defKey === "content_hash")?.notNull,
    false,
  );

  for (const childTable of [
    "t_place_of_supply_rule",
    "t_tax_rate",
    "t_tax_evidence_rule",
    "t_tax_line",
  ]) {
    assert.ok(fieldKeys(childTable).includes("tax_rule_id"), `${childTable} 缺少 tax_rule_id`);
    assert.ok(!fieldKeys(childTable).includes("tax_rule_ver_id"), `${childTable} 仍引用旧版本表`);
    assert.equal(relationTarget(childTable, "tax_rule_id"), "t_tax_rule");
  }

  assert.deepEqual(fieldKeys("t_tax_return"), [
    "id",
    "return_no",
    "return_ver",
    "amends_return_id",
    "tax_period_id",
    "tax_registration_id",
    "authority_id",
    "currency",
    "declared_taxable_amount",
    "declared_tax",
    "declared_reverse_charge_taxable",
    "declared_reverse_charge_tax",
    "content_hash",
    "file_id",
    "file_content_hash",
    "maker_id",
    "checker_id",
    "checker_decision",
    "decision_time",
    "authority_ref",
    "status",
    "filed_time",
    "version",
    "create_time",
    "update_time",
  ]);
  assert.deepEqual(indexFields("t_tax_return", "uk_tax_return_no"), ["return_no", "return_ver"]);
  assert.deepEqual(indexFields("t_tax_return", "uk_tax_return_period"), [
    "tax_period_id",
    "return_ver",
  ]);
  assert.ok(!fieldKeys("t_tax_return").includes("current_ver_id"));
  assert.equal(
    entities.get("t_tax_return")?.fields.find((field) => field.defKey === "content_hash")?.notNull,
    false,
  );

  for (const childTable of ["t_tax_return_line", "t_tax_payment"]) {
    assert.ok(fieldKeys(childTable).includes("tax_return_id"), `${childTable} 缺少 tax_return_id`);
    assert.ok(!fieldKeys(childTable).includes("tax_return_ver_id"), `${childTable} 仍引用旧版本表`);
    assert.equal(relationTarget(childTable, "tax_return_id"), "t_tax_return");
  }

  assert.ok(Object.hasOwn(MODEL_CHECK_CONTRACTS, "ck_tax_rule_lifecycle"));
  assert.ok(Object.hasOwn(MODEL_CHECK_CONTRACTS, "ck_tax_return_lifecycle"));
  assert.deepEqual(MODEL_CHECK_BINDINGS.ck_tax_rule_lifecycle, ["tax_rule"]);
  assert.deepEqual(MODEL_CHECK_BINDINGS.ck_tax_return_lifecycle, ["tax_return"]);
  assert.deepEqual(MODEL_CHECK_BINDINGS.ck_tax_return_amend, ["tax_return"]);
  assert.ok(!MODEL_CHECK_BINDINGS.ck_versioned_root_pointer.includes("t_tax_rule"));
  assert.ok(!MODEL_CHECK_BINDINGS.ck_versioned_root_pointer.includes("t_tax_return"));
});

test("Finance Tax Payment event evidence matrix requires proof only for FAILED", () => {
  const accepts = (eventType, sourceRef, evidenceHash) => {
    const evidencePairIsValid = (sourceRef === null && evidenceHash === null)
      || (sourceRef !== null && evidenceHash !== null);
    const failedHasProof = eventType !== "FAILED"
      || (sourceRef !== null && evidenceHash !== null);
    return evidencePairIsValid && failedHasProof;
  };

  assert.equal(accepts("FAILED", "BANK-NO-DEBIT-1", "hash-proof-1"), true);
  assert.equal(accepts("FAILED", null, null), false);
  assert.equal(accepts("UNKNOWN", null, null), true);
  assert.equal(accepts("UNKNOWN", "BANK-TIMEOUT-1", "hash-timeout-1"), true);
  assert.equal(accepts("UNKNOWN", "BANK-TIMEOUT-1", null), false);
});

test("Finance Entity and polymorphic book ownership are explicit and minimal", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexFields = (tableKey, indexKey) => {
    const entity = entities.get(tableKey);
    const index = entity?.indexes.find((candidate) => candidate.defKey === indexKey);
    return index?.fields.map((entry) => entity.fields
      .find((field) => field.id === entry.fieldDefKey)?.defKey) ?? [];
  };

  assert.deepEqual(fieldKeys("t_finance_entity"), [
    "id",
    "entity_code",
    "legal_party_id",
    "display_name",
    "status",
    "create_time",
    "update_time",
  ]);
  const financeEntity = entities.get("t_finance_entity");
  assert.match(financeEntity.comment, /人工维护.*内部财务主体.*法律 Party/u);
  assert.deepEqual(indexFields("t_finance_entity", "uk_fin_entity_code"), ["entity_code"]);
  assert.deepEqual(indexFields("t_finance_entity", "uk_fin_entity_party"), ["legal_party_id"]);
  assert.equal(financeEntity.correlations.length, 0, "Finance Entity 不建立 CCT 跨库关系");
  for (const forbidden of [
    "version",
    "content_hash",
    "current_ver_id",
    "active_slot",
    "maker_id",
    "checker_id",
  ]) {
    assert.ok(!fieldKeys("t_finance_entity").includes(forbidden));
  }

  assert.deepEqual(fieldKeys("t_ledger_book"), [
    "id",
    "entity_type",
    "entity_id",
    "currency",
    "chart_id",
    "book_status",
    "version",
    "create_time",
    "update_time",
  ]);
  assert.deepEqual(indexFields("t_ledger_book", "uk_ledger_book_entity"), [
    "entity_type",
    "entity_id",
    "currency",
  ]);
  assert.match(
    entities.get("t_ledger_book").fields.find((field) => field.defKey === "entity_type").comment,
    /允许值：OPERATING_ENTITY、FINANCE_ENTITY。$/u,
  );

  assert.deepEqual(fieldKeys("t_fiscal_calendar"), [
    "id",
    "entity_type",
    "entity_id",
    "calendar_code",
    "timezone",
    "year_start_month",
    "version",
    "create_time",
    "update_time",
  ]);
  assert.deepEqual(indexFields("t_fiscal_calendar", "uk_fiscal_cal"), [
    "entity_type",
    "entity_id",
  ]);
  assert.ok(
    !entities.get("t_ledger_book").correlations.some((relation) => {
      const childField = entities.get("t_ledger_book").fields
        .find((field) => field.id === relation.myField)?.defKey;
      return childField === "entity_id";
    }),
    "多态 Ledger Book entity_id 不建立伪外键",
  );

  const accountOwnerType = entities.get("t_account").fields
    .find((field) => field.defKey === "owner_type");
  assert.match(accountOwnerType.comment, /FINANCE_ENTITY/u);
  assert.equal(
    MODEL_CHECK_CONTRACTS.ck_account_owner_bucket,
    "(owner_type='CLIENT' AND balance_bucket IN ('PENDING','AVAILABLE','LOCKED','NONE')) OR (owner_type='LOCAL_PARTNER' AND balance_bucket IN ('NONE','PENDING','AVAILABLE','LOCKED')) OR (owner_type IN ('OPERATING_ENTITY','FINANCE_ENTITY','CHANNEL_ACCOUNT','CASH_ACCOUNT','TAX_AUTHORITY') AND balance_bucket='NONE')",
  );
  assert.ok(!entities.has("t_accounting_entity"));
});

test("Finance generalizes only legal-subject roots and preserves transaction OE facts", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexFields = (tableKey, indexKey) => {
    const entity = entities.get(tableKey);
    const index = entity?.indexes.find((candidate) => candidate.defKey === indexKey);
    return index?.fields.map((entry) => entity.fields
      .find((field) => field.id === entry.fieldDefKey)?.defKey) ?? [];
  };

  const generalizedRoots = [
    "t_tax_registration",
    "t_cash_account",
    "t_cash_account_binding",
    "t_recon_exception",
  ];
  for (const tableKey of generalizedRoots) {
    const fields = fieldKeys(tableKey);
    assert.ok(fields.includes("entity_type"), `${tableKey} 缺少 entity_type`);
    assert.ok(fields.includes("entity_id"), `${tableKey} 缺少 entity_id`);
    assert.ok(!fields.includes("entity_no"), `${tableKey} 仍使用单一 OE 主体`);
    assert.match(
      entities.get(tableKey).fields.find((field) => field.defKey === "entity_type").comment,
      /允许值：OPERATING_ENTITY、FINANCE_ENTITY。$/u,
    );
    const fieldsById = new Map(entities.get(tableKey).fields.map((field) => [field.id, field.defKey]));
    assert.ok(
      !entities.get(tableKey).correlations.some(
        (relation) => fieldsById.get(relation.myField) === "entity_id",
      ),
      `${tableKey}.entity_id 不得建立多态外键`,
    );
  }

  assert.deepEqual(indexFields("t_tax_registration", "uk_tax_registration"), [
    "entity_type", "entity_id", "authority_id", "tax_type", "registration_kind", "effective_from",
  ]);
  assert.deepEqual(indexFields("t_cash_account", "uk_cash_blind_index"), [
    "entity_type", "entity_id", "account_blind_index_key_version", "account_blind_index",
  ]);
  assert.deepEqual(indexFields("t_cash_account_binding", "uk_cash_binding"), [
    "entity_type", "entity_id", "currency", "account_purpose",
  ]);
  assert.deepEqual(indexFields("t_recon_exception", "idx_recon_exception_entity"), [
    "entity_type", "entity_id", "currency", "status",
  ]);

  for (const tableKey of [
    "t_tax_snapshot",
    "t_fiscal_record",
    "t_channel_account",
    "t_payout",
    "t_finance_restriction_proj",
  ]) {
    const fields = fieldKeys(tableKey);
    assert.ok(fields.includes("entity_no"), `${tableKey} 必须保留交易 Operating Entity`);
    assert.ok(!fields.includes("entity_type"), `${tableKey} 不得机械泛化主体`);
    assert.ok(!fields.includes("entity_id"), `${tableKey} 不得机械泛化主体`);
  }

  for (const tableKey of ["t_channel_open_item", "t_channel_debit_item"]) {
    const fields = fieldKeys(tableKey);
    assert.ok(fields.includes("channel_account_id"), `${tableKey} 必须通过 Channel Account 确定 OE`);
    assert.ok(fields.includes("transaction_economics_id"), `${tableKey} 必须与交易 Economics 交叉校验 OE`);
    assert.ok(!fields.includes("entity_no"), `${tableKey} 不重复保存父链 OE`);
    assert.ok(!fields.includes("entity_type"), `${tableKey} 不得泛化为 Finance Entity`);
    assert.ok(!fields.includes("entity_id"), `${tableKey} 不得泛化为 Finance Entity`);
  }

  assert.ok(!entities.has("t_refund_funding_reservation"));

  for (const tableKey of [
    "t_accounting_period",
    "t_tax_period",
    "t_tax_return",
    "t_tax_payment",
    "t_cash_account_binding_ver",
    "t_bank_statement",
    "t_bank_statement_item",
    "t_trial_balance_run",
    "t_trial_balance_line",
    "t_trial_balance_exception",
    "t_period_close",
    "t_period_close_book",
  ]) {
    assert.ok(!fieldKeys(tableKey).includes("entity_type"), `${tableKey} 应由父链确定主体`);
    assert.ok(!fieldKeys(tableKey).includes("entity_id"), `${tableKey} 应由父链确定主体`);
  }
});

test("Finance uses one owner-aware posting account and chart-level close projections", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexByKey = (tableKey, indexKey) => entities.get(tableKey)?.indexes
    .find((index) => index.defKey === indexKey);

  assert.equal(model.entities.length, 84);
  assert.ok(entities.has("t_account"));
  assert.ok(!entities.has("t_account_balance"));
  assert.ok(!entities.has("t_ledger_account"));
  assert.ok(!entities.has("t_subledger_account"));
  assert.ok(!entities.has("t_subledger_balance"));
  assert.deepEqual(fieldKeys("t_account"), [
    "id",
    "ledger_book_id",
    "chart_account_id",
    "owner_type",
    "owner_id",
    "balance_bucket",
    "normal_side",
    "account_status",
    "debit_total",
    "credit_total",
    "net_balance",
    "last_journal_sequence",
    "balance_version",
    "create_time",
    "update_time",
  ]);
  const accountNormalSide = entities.get("t_account").fields
    .find((field) => field.defKey === "normal_side");
  assert.equal(accountNormalSide?.notNull, true);
  assert.match(accountNormalSide?.comment ?? "", /允许值：DEBIT、CREDIT。$/u);
  assert.doesNotMatch(accountNormalSide?.comment ?? "", /BOTH/u);
  assert.match(
    entities.get("t_account").fields.find((field) => field.defKey === "net_balance")?.comment ?? "",
    /负数表示反向余额/u,
  );

  assert.deepEqual(fieldKeys("t_book_sequence"), [
    "id",
    "ledger_book_id",
    "next_value",
    "create_time",
    "update_time",
  ]);
  assert.deepEqual(
    indexByKey("t_account", "uk_account_identity")?.fields
      .map((entry) => entities.get("t_account").fields
        .find((field) => field.id === entry.fieldDefKey)?.defKey),
    ["ledger_book_id", "chart_account_id", "owner_type", "owner_id", "balance_bucket"],
  );

  for (const tableKey of ["t_instruction_line", "t_journal_line", "t_manual_adjustment_line"]) {
    const actual = new Set(fieldKeys(tableKey));
    assert.ok(actual.has("account_id"), `${tableKey} 缺少统一账户引用`);
    for (const removedField of ["ledger_account_id", "subledger_account_id", "currency"]) {
      assert.ok(!actual.has(removedField), `${tableKey} 仍保留 ${removedField}`);
    }
  }
  assert.ok(!fieldKeys("t_instruction_line").includes("ledger_book_id"));
  assert.ok(!fieldKeys("t_accounting_rule_line").includes("subledger_owner_type"));
  assert.equal(
    entities.get("t_accounting_rule_line").fields
      .find((field) => field.defKey === "balance_bucket")?.notNull,
    true,
  );

  assert.ok(!fieldKeys("t_account").includes("version"));
  assert.ok(!fieldKeys("t_book_sequence").includes("version"));
  assert.ok(entities.has("t_ledger_period_balance"));
  assert.ok(!entities.has("t_account_period_balance"));
  for (const requiredField of ["period_id", "ledger_book_id", "chart_account_id", "trial_balance_run_id"]) {
    assert.ok(fieldKeys("t_ledger_period_balance").includes(requiredField));
  }
  assert.ok(fieldKeys("t_trial_balance_line").includes("chart_account_id"));
  assert.ok(!fieldKeys("t_trial_balance_line").includes("ledger_account_id"));
  assert.ok(fieldKeys("t_trial_balance_exception").includes("reference_type"));
  assert.ok(fieldKeys("t_trial_balance_exception").includes("reference_id"));
  assert.ok(!fieldKeys("t_trial_balance_exception").includes("account_id"));

  const closeStepType = entities.get("t_close_step").fields
    .find((field) => field.defKey === "step_type");
  assert.match(closeStepType.comment, /RECONCILE_ACCOUNT_BALANCES/u);
  assert.doesNotMatch(closeStepType.comment, /RECONCILE_SUBLEDGERS/u);
  assert.ok(Object.hasOwn(MODEL_CHECK_CONTRACTS, "ck_account_owner_bucket"));
  assert.ok(Object.hasOwn(MODEL_CHECK_CONTRACTS, "ck_account_balance_projection"));
  assert.ok(!Object.hasOwn(MODEL_CHECK_CONTRACTS, "ck_subledger_bucket"));
});

test("Finance keeps transaction economics explicit and accounting rules minimal", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (tableKey) => entities.get(tableKey)?.fields.map((field) => field.defKey) ?? [];
  const indexFields = (tableKey, indexKey) => {
    const entity = entities.get(tableKey);
    const index = entity?.indexes.find((item) => item.defKey === indexKey);
    return index?.fields.map((entry) => entity.fields
      .find((field) => field.id === entry.fieldDefKey)?.defKey) ?? [];
  };

  for (const oldTable of [
    "t_economics_snapshot",
    "t_economics_component",
    "t_economics_component_link",
    "t_accounting_rule_mapping",
    "t_accounting_rule_condition",
    "t_accounting_rule_approval",
  ]) {
    assert.ok(!entities.has(oldTable), `仍保留旧模型表 ${oldTable}`);
  }

  assert.deepEqual(fieldKeys("t_economic_fact"), [
  "id",
  "fact_type",
  "source_system",
  "source_event_key",
  "source_version",
  "source_payload_hash",
  "parent_economics_id",
  "order_id",
  "order_context_id",
  "context_format",
  "payment_fact_id",
  "client_price_id",
  "settlement_business_days",
  "business_calendar_code",
  "settlement_timezone",
  "cutoff_second_of_day",
  "commercial_context_time",
  "tax_snapshot_id",
  "payment_intent_id",
  "payment_attempt_id",
  "payment_succeeded_time",
  "balance_available_time",
  "refund_request_id",
  "channel_refund_id",
  "duplicate_capture_item_id",
  "channel_dispute_notice_id",
  "dispute_case_id",
  "client_id",
  "entity_no",
  "local_partner_id",
  "consumer_country_code",
  "payment_method",
  "industry_code",
  "mcc_code",
  "client_agreement_id",
  "partner_agreement_oe_id",
  "finance_entity_id",
  "currency",
  "gross_amount",
  "supersedes_fact_id",
  "status",
  "fact_version",
  "channel_account_id",
  "maker_id",
  "checker_id",
  "approved_time",
  "published_at",
  "create_time",
  "update_time"
]);
  assert.deepEqual(
    indexFields("t_economic_fact", "uk_econ_source"),
    ["source_system", "source_event_key"],
  );

  assert.deepEqual(fieldKeys("t_accounting_rule_bundle_ver"), [
    "id",
    "bundle_id",
    "bundle_ver",
    "effective_from",
    "effective_to",
    "status",
    "maker_id",
    "checker_id",
    "approved_time",
    "create_time",
    "update_time",
  ]);
  assert.match(
    entities.get("t_accounting_rule_bundle_ver").comment,
    /先锁定 Bundle.*再锁定 Version/u,
  );
  assert.deepEqual(fieldKeys("t_accounting_rule_line"), [
    "id",
    "bundle_ver_id",
    "instruction_type",
    "line_no",
    "line_code",
    "amount_source_code",
    "owner_role",
    "account_code",
    "balance_bucket",
    "entry_side",
    "required_flag",
    "create_time",
  ]);
  assert.deepEqual(
    indexFields("t_accounting_rule_line", "uk_acct_rule_line"),
    ["bundle_ver_id", "instruction_type", "line_no"],
  );
  assert.deepEqual(
    indexFields("t_accounting_rule_line", "uk_acct_rule_line_code"),
    ["bundle_ver_id", "instruction_type", "line_code"],
  );
  assert.deepEqual(
    indexFields("t_accounting_rule_line", "idx_acct_rule_amount"),
    ["bundle_ver_id", "instruction_type", "amount_source_code"],
  );
  assert.deepEqual(
    indexFields("t_accounting_instruction", "uk_instr_source"),
    ["source_system", "source_event_key", "instruction_type"],
  );
  assert.deepEqual(fieldKeys("t_accounting_instruction"), [
    "id",
    "ledger_book_id",
    "instruction_type",
    "origin_type",
    "source_system",
    "source_event_key",
    "source_version",
    "source_payload_hash",
    "economic_fact_id",
    "manual_adjustment_id",
    "accounting_rule_bundle_ver_id",
    "input_hash",
    "status",
    "maker_id",
    "checker_id",
    "version",
    "create_time",
    "update_time",
  ]);
  const instruction = entities.get("t_accounting_instruction");
  const instructionLedgerField = instruction.fields.find((field) => field.defKey === "ledger_book_id");
  const instructionLedgerRelation = instruction.correlations
    .find((relation) => relation.myField === instructionLedgerField?.id);
  assert.ok(instructionLedgerRelation, "Instruction 必须引用唯一 Ledger Book");
  assert.equal(instructionLedgerRelation.myRows, "n");
  assert.equal(
    model.entities.find((entity) => entity.id === instructionLedgerRelation.refEntity)?.defKey,
    "t_ledger_book",
  );
  assert.deepEqual(indexFields("t_accounting_instruction", "idx_instr_book_status"), [
    "ledger_book_id",
    "status",
    "create_time",
  ]);

  assert.deepEqual(fieldKeys("t_economic_fact_line"), [
  "id",
  "transaction_economics_id",
  "component_no",
  "component_type",
  "party_type",
  "party_id",
  "payer_role",
  "payer_id",
  "payee_role",
  "payee_id",
  "tax_line_id",
  "basis_amount",
  "rate_value",
  "raw_amount",
  "posted_amount",
  "effect_direction",
  "source_rule_type",
  "source_rule_version_id",
  "source_price_id",
  "source_profile_code",
  "source_band_evidence",
  "source_rule_scope_id",
  "source_rule_component_id",
  "source_component_code",
  "source_trigger_code",
  "source_calculation_base",
  "source_rate",
  "source_fixed_amount",
  "source_currency",
  "source_min_amount",
  "source_max_amount",
  "source_rule_content_hash",
  "calculation_method",
  "rounding_mode",
  "create_time"
]);
  const sourceRuleType = entities.get("t_economic_fact_line").fields
    .find((field) => field.defKey === "source_rule_type");
  assert.match(
    sourceRuleType.comment,
    /允许值：CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、TAX_RULE、CHANNEL_COST/u,
  );
  const partyType = entities.get("t_economic_fact_line").fields
    .find((field) => field.defKey === "party_type");
  assert.match(partyType.comment, /FINANCE_ENTITY/u);
  assert.match(
    entities.get("t_economic_fact_line").comment,
    /PLATFORM_SERVICE_CHARGE.*party_id.*finance_entity_id.*父链/u,
  );
  const economics = entities.get("t_economic_fact");
  const financeEntityField = economics.fields.find((field) => field.defKey === "finance_entity_id");
  assert.equal(financeEntityField.notNull, false);
  assert.match(financeEntityField.comment, /PARTNER.*ACTIVE.*Finance Entity/u);
  assert.ok(
    !economics.correlations.some((relation) => relation.myField === financeEntityField.id),
    "Economics Finance Entity 不建立跨聚合重复关系",
  );
  assert.equal(
    MODEL_CHECK_CONTRACTS.ck_txn_economics_partner,
    "(local_partner_id IS NULL AND partner_agreement_oe_id IS NULL AND finance_entity_id IS NULL) OR (local_partner_id IS NOT NULL AND partner_agreement_oe_id IS NOT NULL AND finance_entity_id IS NOT NULL)",
  );
  assert.match(
    MODEL_CHECK_CONTRACTS.ck_txn_econ_component_source,
    /source_rule_type='PLATFORM_SERVICE_CHARGE'.*source_rule_component_id IS NOT NULL.*party_type='FINANCE_ENTITY'/u,
  );
  assert.ok(!entities.has("t_platform_service_charge"));
  const componentType = entities.get("t_economic_fact_line").fields
    .find((field) => field.defKey === "component_type");
  assert.match(componentType.comment, /CHANNEL_COST_INCREASE.*CHANNEL_COST_DECREASE/u);
  const componentLinkComment = entities.get("t_economic_fact_line_link").comment;
  assert.match(componentLinkComment, /完整关联集.*posted_amount/u);
  assert.match(componentLinkComment, /稳定顺序锁定.*原组件.*累计关联金额/u);
  assert.match(componentLinkComment, /REVERSAL.*effect_direction.*ADJUSTMENT/u);
  const requiredFlag = entities.get("t_accounting_rule_line").fields
    .find((field) => field.defKey === "required_flag");
  assert.match(requiredFlag.comment, /金额为零.*整体跳过/u);
  const amountSourceCode = entities.get("t_accounting_rule_line").fields
    .find((field) => field.defKey === "amount_source_code");
  assert.match(amountSourceCode.comment, /CHANNEL_COST_INCREASE.*CHANNEL_COST_DECREASE/u);
  assert.ok(entities.has("t_economic_fact_line_link"));

  const renamedReferences = Object.freeze({
    t_accounting_instruction: ["economic_fact_id", "accounting_rule_bundle_ver_id"],
    t_instruction_line: ["accounting_rule_line_id"],
    t_channel_open_item: ["transaction_economics_id"],
    t_channel_debit_item: ["transaction_economics_id"],
    t_channel_cost_alloc: ["transaction_economics_id"],
  });
  for (const [tableKey, expectedFields] of Object.entries(renamedReferences)) {
    const actual = new Set(fieldKeys(tableKey));
    for (const fieldKey of expectedFields) assert.ok(actual.has(fieldKey), `缺少 ${tableKey}.${fieldKey}`);
  }
});

test("Finance immutable facts remain append-only by exact table whitelist", () => {
  const model = readModel();
  const immutableTables = model.entities
    .filter((entity) => !entity.fields.some((field) => field.defKey === "update_time"))
    .map((entity) => entity.defKey);
  assert.deepEqual(immutableTables, EXPECTED_IMMUTABLE_TABLES);
  for (const entity of model.entities) {
    assert.ok(entity.fields.some((field) => field.defKey === "create_time"));
    if (EXPECTED_IMMUTABLE_TABLES.includes(entity.defKey)) continue;
    assert.ok(entity.fields.some((field) => field.defKey === "update_time"), `${entity.defKey} 缺少 update_time`);
  }
});

test("Finance CHECK policy matches the independent Oracle and rejects stale design tokens", () => {
  assert.deepEqual(MODEL_CHECK_CONTRACTS, EXPECTED_CHECK_CONTRACTS);
  assert.deepEqual(MODEL_CHECK_BINDINGS, EXPECTED_CHECK_BINDINGS);
  assert.deepEqual(Object.keys(MODEL_CHECK_BINDINGS), Object.keys(MODEL_CHECK_CONTRACTS));
  for (const [checkName, tableKeys] of Object.entries(MODEL_CHECK_BINDINGS)) {
    assert.ok(Object.hasOwn(MODEL_CHECK_CONTRACTS, checkName));
    for (const tableKey of tableKeys) {
      assert.ok(APPROVED_TABLES.includes(`t_${tableKey}`), `${checkName} 绑定未知表 ${tableKey}`);
    }
  }

  const manifestSources = readdirSync(paths.manifestDirectory)
    .filter((fileName) => fileName.endsWith(".mjs"))
    .sort()
    .map((fileName) => path.join(paths.manifestDirectory, fileName));
  const productionSources = [paths.generator, paths.manifest, paths.policy, ...manifestSources]
    .map((filePath) => readFileSync(filePath, "utf8"))
    .join("\n");
  for (const forbiddenTable of FORBIDDEN_TABLES) {
    assert.ok(!productionSources.includes(`\"${forbiddenTable.slice(2)}\"`),
      `生产模型源恢复了已删除 Settlement 表令牌 ${forbiddenTable}`);
  }
  assert.doesNotMatch(
    productionSources,
    /match_type='CHANNEL_RECEIPT'|match_type='CHANNEL_CASH'|trigger_type='SCHEDULED'|ck_settlement_attempt_active|channel_refund_item|cash_in_transit_item|settlement_requirement_snapshot|settlement_mode|RETURN_TO_SOURCE|return_reference|return_file_id|return_file_hash|t_reserve_case|t_restricted_fund|t_restricted_fund_move|t_recovery_case|t_recovery_application|reserve_case_id|balance_reserve_recovery|CLIENT_RESTRICTED_FUNDS|\bt_channel_settle_alloc\b|\bt_channel_cash_item\b|\bt_recon_match\b|\brecon_match_id\b/u,
  );
  assert.doesNotMatch(productionSources, /finance-test-oracle/u);
  const oracle = readFileSync(paths.oracle, "utf8");
  assert.doesNotMatch(oracle, /from\s+["'][^"']*finance\/(?:manifest|model-policy|generate-finance-model)/u);
});

test("Finance Channel reconciliation freezes the three-stage account contract", () => {
  const model = readModel();
  const modelSource = JSON.stringify(model);
  for (const accountCode of [
    "CHANNEL_UNSETTLED_RECEIVABLE",
    "CHANNEL_SETTLEMENT_RECEIVABLE",
    "CHANNEL_UNSETTLED_PAYABLE",
    "CHANNEL_SETTLEMENT_PAYABLE",
    "CHANNEL_FEE_ACCRUAL",
  ]) {
    assert.match(modelSource, new RegExp(accountCode, "u"), `模型缺少 ${accountCode} 三阶段账户语义`);
  }
  assert.doesNotMatch(
    modelSource,
    /\bCHANNEL_RECEIVABLE\b|\bCASH_IN_TRANSIT\b|\bCASH_OUT_TRANSIT\b/u,
  );

  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const channelBatch = entities.get("t_channel_settle_batch");
  assert.match(channelBatch?.comment, /Settlement.*Remittance.*Preliminary Transaction Report/u);
  assert.match(channelBatch?.comment, /CHANNEL_FEE_ACCRUAL.*不得重复.*Channel Fee Expense/u);
  const batchStatus = channelBatch.fields.find((field) => field.defKey === "status");
  assert.match(batchStatus?.comment, /VALIDATED.*不能.*重分类/u);
  assert.match(batchStatus?.comment, /交易对账完整.*批次.*Journal/u);

  const transactionMatch = entities.get("t_channel_transaction_match");
  assert.match(transactionMatch?.comment, /逐笔.*交易对账/u);
  assert.match(transactionMatch?.comment, /不代表.*银行/u);

  const bankMatch = entities.get("t_bank_recon_match");
  assert.match(bankMatch?.comment, /Channel Batch.*Bank Statement Item/u);
  assert.match(bankMatch?.comment, /BANK_CASH/u);

  const cashAccount = entities.get("t_cash_account");
  assert.ok(!cashAccount.fields.some((field) => field.defKey === "account_id"));

  const trialException = entities.get("t_trial_balance_exception");
  const exceptionType = trialException.fields.find((field) => field.defKey === "exception_type");
  assert.match(exceptionType?.comment, /CHANNEL_SETTLEMENT_MISMATCH/u);
  assert.doesNotMatch(exceptionType?.comment, /CASH_IN_TRANSIT_MISMATCH/u);
});

test("Finance close and material reconciliation controls are structurally reviewable", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const fieldKeys = (table) => new Set(entities.get(table).fields.map((field) => field.defKey));

  const trialRun = entities.get("t_trial_balance_run");
  const trialRunField = (fieldKey) => trialRun.fields
    .find((field) => field.defKey === fieldKey);
  assert.equal(trialRunField("content_hash")?.notNull, false);
  assert.match(trialRunField("content_hash")?.comment, /不包含.*解决/u);
  assert.match(trialRunField("total_debit")?.comment, /期末余额.*实际借方/u);
  assert.match(trialRunField("total_credit")?.comment, /期末余额.*实际贷方/u);
  assert.equal(
    MODEL_CHECK_CONTRACTS.ck_trial_balance_hash,
    "(status IN ('CREATED','RUNNING') AND content_hash IS NULL) OR (status IN ('SUCCEEDED','FAILED') AND content_hash IS NOT NULL)",
  );

  const trialException = entities.get("t_trial_balance_exception");
  const resolutionType = trialException.fields
    .find((field) => field.defKey === "resolution_type");
  const exceptionType = trialException.fields
    .find((field) => field.defKey === "exception_type");
  assert.match(resolutionType?.comment, /PROJECTION_REBUILD/u);
  assert.match(exceptionType?.comment, /PLATFORM_SERVICE_PAYABLE.*PLATFORM_SERVICE_RECEIVABLE/u);
  assert.match(
    MODEL_CHECK_CONTRACTS.ck_trial_exception_resolution,
    /exception_type='GENERAL_LEDGER_MISMATCH'.*PROJECTION_REBUILD/u,
  );
  assert.match(
    MODEL_CHECK_CONTRACTS.ck_trial_exception_resolution,
    /status='WAIVED'.*exception_type<>'GENERAL_LEDGER_MISMATCH'.*CONTROLLED_WAIVER/u,
  );

  const periodBalance = entities.get("t_ledger_period_balance");
  const periodBalanceSequence = periodBalance.fields
    .find((field) => field.defKey === "as_of_sequence");
  assert.equal(periodBalanceSequence?.type, "BIGINT");
  assert.equal(periodBalanceSequence?.notNull, true);
  assert.match(periodBalanceSequence?.comment, /旧水位.*不得覆盖/u);
  assert.equal(
    MODEL_CHECK_CONTRACTS.ck_ledger_period_balance_sequence,
    "as_of_sequence>=0",
  );

  const closeBook = entities.get("t_period_close_book");
  assert.match(closeBook.comment, /同一事务/u);
  assert.match(closeBook.comment, /同一账簿、期间和水位/u);
  assert.match(closeBook.comment, /无开放异常/u);
  assert.match(closeBook.comment, /期间余额/u);
  assert.match(closeBook.comment, /七个关账步骤/u);

  const channelOpenItem = entities.get("t_channel_open_item");
  const transactionEconomicsField = channelOpenItem.fields
    .find((field) => field.defKey === "transaction_economics_id");
  const transactionEconomicsRelation = channelOpenItem.correlations
    .find((relation) => relation.myField === transactionEconomicsField.id);
  assert.equal(transactionEconomicsRelation?.myRows, "n", "同一支付交易经济结果必须允许多个 Capture 应收项");
  assert.equal(
    model.entities.find((entity) => entity.id === transactionEconomicsRelation?.refEntity)?.defKey,
    "t_economic_fact",
  );
  assert.deepEqual(
    channelOpenItem.indexes
      .filter((index) => index.fields[0]?.fieldDefKey === transactionEconomicsField.id)
      .map((index) => ({ defKey: index.defKey, unique: index.unique })),
    [{ defKey: "idx_channel_open_econ", unique: false }],
  );

  for (const field of [
    "channel_transaction_match_id",
    "bank_recon_match_id",
    "content_hash",
    "checker_decision",
    "approved_content_hash",
    "decision_time",
  ]) {
    assert.ok(fieldKeys("t_recon_resolution").has(field), `重大对账处置缺少 ${field}`);
  }
  const reconResolution = entities.get("t_recon_resolution");
  for (const [fieldKey, targetTable] of [
    ["channel_transaction_match_id", "t_channel_transaction_match"],
    ["bank_recon_match_id", "t_bank_recon_match"],
  ]) {
    const matchField = reconResolution.fields.find((field) => field.defKey === fieldKey);
    const matchRelation = reconResolution.correlations
      .find((relation) => relation.myField === matchField.id);
    assert.equal(matchRelation?.myRows, "1", `${fieldKey} 必须是 1:1 执行证据`);
    assert.equal(
      model.entities.find((entity) => entity.id === matchRelation?.refEntity)?.defKey,
      targetTable,
    );
  }
  for (const field of [
    "resolution_type",
    "resolution_ref_id",
    "resolution_content_hash",
    "resolution_maker_id",
    "resolution_checker_id",
    "resolution_time",
  ]) assert.ok(fieldKeys("t_trial_balance_exception").has(field), `试算异常缺少 ${field}`);
  assert.ok(!fieldKeys("t_trial_balance_exception").has("resolution_ref"));
  assert.ok(entities.has("t_period_close_book"));
  assert.ok(!fieldKeys("t_period_close").has("posting_instruction_id"));
  for (const field of ["ledger_book_id", "as_of_sequence", "final_trial_run_id", "posting_instruction_id"]) {
    assert.ok(fieldKeys("t_period_close_book").has(field), `关账账簿范围缺少 ${field}`);
  }

  assert.ok(fieldKeys("t_channel_debit_item").has("transaction_economics_id"));
  assert.ok(fieldKeys("t_channel_settle_batch").has("debit_amount"));
  assert.ok(fieldKeys("t_channel_settle_batch").has("cash_direction"));
  assert.ok(!fieldKeys("t_channel_account").has("settlement_mode"));
});

test("Finance model carries no connection or forbidden sensitive storage", () => {
  const model = readModel();
  assert.deepEqual(model.dbConn, []);
  assert.deepEqual(model.dbConns, []);
  assert.equal(model.profile.default.dbConn, "");
  const fieldKeys = model.entities.flatMap((entity) => entity.fields.map((field) => field.defKey));
  for (const forbidden of ["partner_id", "jdbc_url", "password", "pin_block", "track_data"]) {
    assert.ok(!fieldKeys.includes(forbidden), `禁止字段被恢复：${forbidden}`);
  }
});
