import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const modelPath = path.join(
  workspaceRoot,
  "commerce-service/docs/database/commerce.pdma.json",
);
const decisionsPath = path.join(
  workspaceRoot,
  "commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md",
);
const scenarioPath = path.join(
  workspaceRoot,
  "agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md",
);
const artifactsReady = existsSync(modelPath) && existsSync(decisionsPath);

const expectedLogicalTables = Object.freeze([
  "commerce_order",
  "order_context",
  "order_legal",
  "order_item",
  "order_payment_projection",
  "order_refund_position",
  "checkout_session",
  "consumer_context",
  "consumer_contract",
  "checkout_network_evidence",
  "consumer_acceptance",
  "refund_request",
  "command_idempotency",
  "compensation_event",
]);

const removedTables = Object.freeze([
  "order_status_history",
  "checkout_status_history",
  "fulfillment_status_history",
  "refund_status_history",
  "acceptance_state_change",
  "consumer_acceptance_summary",
  "fulfill_callback_snapshot",
  "order_amount_snapshot",
  "order_seller_snapshot",
  "order_contract_snapshot",
  "order_tax_line",
  "order_commercial_term",
  "order_term_component",
  "order_snapshot",
  "fulfillment",
  "fulfillment_confirmation",
  "fulfillment_evidence",
  "refund_reservation",
  "refund_reserve_change",
  "refund_payment_projection",
  "consumer_document",
  "business_change_record",
  "business_change_version",
]);

function readScenario() {
  assert.ok(existsSync(scenarioPath), "Commerce 现实场景文档不存在");
  return readFileSync(scenarioPath, "utf8");
}

function scenarioTableRows(source) {
  const start = source.indexOf("## 4. 14 表逐表映射");
  const end = source.indexOf("### 4.1 两笔 Refund Request 完整行", start);
  assert.ok(start >= 0 && end > start, "现实场景缺少 14 表逐表映射章节");
  return source
    .slice(start, end)
    .split(/\r?\n/u)
    .map((line) => line.match(/^\| `([a-z][a-z0-9_]*)` \|(.+)\|$/u))
    .filter(Boolean)
    .map((match) => ({
      table: match[1],
      cells: match[2].split("|").map((cell) => cell.trim()),
    }));
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

function artifactTest(name, fn) {
  test(name, { skip: !artifactsReady }, fn);
}

test("Commerce real-world scenario has a generated model and physical decisions", () => {
  const missing = [modelPath, decisionsPath]
    .filter((artifactPath) => !existsSync(artifactPath))
    .map((artifactPath) => path.relative(workspaceRoot, artifactPath));
  assert.deepEqual(missing, [], `Commerce 场景契约缺少产物：${missing.join(", ")}`);
});

test("NOVA scenario provides one concrete mapping row for every approved table", () => {
  const source = readScenario();
  assert.match(source, /场景 ID：`NOVA_COMMERCE_UAE_2026`/u);
  const rows = scenarioTableRows(source);
  assert.deepEqual(rows.map((row) => row.table), expectedLogicalTables);
  assert.equal(new Set(rows.map((row) => row.table)).size, 14);
  for (const row of rows) {
    assert.equal(row.cells.length, 2, `${row.table} 必须同时说明示例和写入/更新/冻结语义`);
    assert.ok(row.cells.every((cell) => cell.length > 3), `${row.table} 的场景说明过于空泛`);
  }
});

artifactTest("scenario table coverage matches the generated 223-field model", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const scenarioTables = scenarioTableRows(readScenario()).map((row) => `t_${row.table}`);
  assert.deepEqual(scenarioTables, model.entities.map((entity) => entity.defKey));
  assert.deepEqual(modelTotals(model), {
    tables: 14,
    fields: 223,
    uniqueIndexes: 20,
    accessIndexes: 11,
    relations: 14,
    diagramGroups: 4,
  });
});

artifactTest("scenario fills every current Refund Request field for Client and Operations", () => {
  const source = readScenario();
  const start = source.indexOf("### 4.1 两笔 Refund Request 完整行");
  const end = source.indexOf("## 5.", start);
  assert.ok(start >= 0 && end > start, "现实场景缺少两类 Refund Request 完整行");
  const documentedFields = [...source.slice(start, end).matchAll(/^\| `([a-z][a-z0-9_]*)` \|/gmu)]
    .map((match) => match[1]);
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const refundFields = model.entities
    .find((entity) => entity.defKey === "t_refund_request")
    .fields
    .map((field) => field.defKey);
  assert.deepEqual(documentedFields, refundFields);
  assert.match(source.slice(start, end), /`request_origin` \| `CLIENT` \| `OPERATIONS`/u);
  assert.match(source.slice(start, end), /`client_refund_id` \| `NOVA-REFUND-20-001` \| `NULL`/u);
});

test("scenario demonstrates immutable evidence, Risk RFI and Cancel/Void/Reversal behavior", () => {
  const source = readScenario();
  for (const expected of [
    "Consumer 修正账单地址，Consumer Context Version 2 生效",
    "Privacy Notice Acceptance 与 LEGAL_ACCEPTANCE 网络证据同事务插入",
    "Client 命名空间内的非 PII、不透明业务引用",
    "不得复用 Email、Phone、证件号等个人原值",
    "consumer_profile_vault_token 只保护姓名和完整账单地址",
    "不重复包含 Email/Phone",
    "同一规范化 Email/Phone 在平台内部同字段作用域返回稳定 Token",
    "client_ip_address",
    "trusted_proxy_chain",
    "不采集 `device_identifier`",
    "不维护 Acceptance Summary",
    "Payment Refund=UNKNOWN，Commerce Request 保持 PROCESSING，本地退款额度继续预占",
    "Payment Refund=SUCCEEDED，Commerce 同事务消费本地退款额度并将 Request 置为 SUCCEEDED",
    "Commerce 本地预占成功后直接调用 PAY-I04",
    "Payment Refund=FAILED/CANCELLED，Commerce 同事务释放本地退款额度并进入对应终态",
    "Payment Refund=SUCCEEDED 同时通知 Commerce 与 Finance",
    "CLIENT 发起时 client_refund_id 必填；OPERATIONS 发起时为空",
    "Receipt 与 Credit Note 的状态、文件和更正关系由 Platform Support 保存",
    "GetImmutableCommercialTermScope",
    "GetImmutableTaxSnapshot",
    "finance_tax_snapshot_hash",
    "client_order_id",
    "commercial_context_time",
    "platform_service_charge_scope_id",
    "platform_service_party_id",
    "PARTNER 来源四项成组非空",
    "非 PARTNER 来源四项成组为空",
    "PLATFORM_SERVICE_CHARGE",
    "PLATFORM_SIGNATORY",
    "非 Partner 成功场景",
    "缺一字段拒绝创建 Order",
    "Commerce 不保存 CCT Term Component 或 Finance Entity 映射",
    "同一 Operating Entity",
    "新的 client_order_id",
    "不自动创建替换订单",
    "Chargeback 后由 Risk 发起 RFI/举证收集",
    "Commerce 不创建履约表或履约事件",
    "Cancel 不会静默升级成 Reversal",
    "授权成功但未 Capture",
    "AUTHORIZATION_VOIDED",
    "Captured Reversal",
    "Order 仍保持 `PAID`",
    "PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED",
  ]) {
    assert.ok(source.includes(expected), `场景缺少关键行为：${expected}`);
  }
  assert.match(
    source,
    /GetImmutableCommercialTermScope\(720000000000000403,720000000000000302,2026-08-23T08:00:00\.000000Z\)/u,
  );
  assert.match(source, /H_CCT_V5.*审计关联摘要.*不是单 Scope 校验参数/u);
  assert.match(source, /暂时网络失败不 ACK.*PaymentSucceeded.*重投/u);
  assert.match(source, /永久关系错误.*DLQ/u);
  assert.match(source, /Commerce V1 不保存通用 BCR/u);
  assert.doesNotMatch(source, /refund_funding_reservation_id|Finance Funding Reservation|Finance Ack/u);
  assert.doesNotMatch(source, /FulfillmentRequested|Fulfillment Confirmation|新 Confirmation|`fulfillment`|`fulfillment_confirmation`|`fulfillment_evidence`/u);
});

artifactTest("removed fulfillment and obsolete Vault/Blind fields stay out of the model", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const tableKeys = new Set(model.entities.map((entity) => entity.defKey));
  const fieldKeys = new Set(model.entities.flatMap((entity) => entity.fields.map((field) => field.defKey)));
  for (const table of removedTables) {
    assert.ok(!tableKeys.has(`t_${table}`), `恢复了已删除场景表 ${table}`);
  }
  for (const field of [
    "endpoint_id",
    "endpoint_version_id",
    "subscription_id",
    "consumer_blind_index",
    "consumer_blind_index_key_version",
    "email_blind_index",
    "email_blind_index_key_version",
    "phone_blind_index",
    "phone_blind_index_key_version",
    "consumer_composite_vault_token",
    "network_vault_token",
    "ip_blind_index",
    "ip_blind_index_key_version",
    "ip_range_blind_index",
    "ip_range_blind_index_key_version",
    "device_blind_index",
    "device_blind_index_key_version",
    "device_identifier",
    "external_order_id",
    "creation_request_hash",
    "order_snapshot_id",
    "snapshot_schema_version",
    "snapshot_hash",
    "cct_context_as_of_time",
  ]) {
    assert.ok(!fieldKeys.has(field), `恢复了已删除场景字段 ${field}`);
  }

  const source = readScenario();
  assert.match(source, /Commerce 14 表不保存 Webhook 配置 ID、URL 或 Secret/u);
  assert.match(source, /直接保存完整客户端 IP、完整 User-Agent 与可信代理链/u);
  assert.match(source, /不保存 Vault Token、Blind Index 或 Key Version/u);
});

artifactTest("physical decisions link back to the current Commerce authorities", () => {
  const source = readFileSync(decisionsPath, "utf8");
  for (const target of [
    "../../../agent-docs/database/COMMERCE_DATABASE.md",
    "../../../agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md",
    "../../../agent-docs/plans/2026-08-24-commerce-document-audit-cancel-reversal-design.md",
  ]) {
    assert.ok(source.includes(target), `物理决策缺少导航：${target}`);
    assert.ok(existsSync(path.resolve(path.dirname(decisionsPath), target)));
  }
});
