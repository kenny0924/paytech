import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const scenarioPath = path.join(workspaceRoot, "agent-docs/database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md");
const modelPath = path.join(workspaceRoot, "payment-orchestration-service/docs/database/payment-orchestration.pdma.json");
const expectedTableNames = Object.freeze([
  "t_payment_channel", "t_channel_account", "t_channel_mid", "t_mid_capability", "t_mid_health_state",
  "t_routing_policy", "t_routing_policy_version", "t_routing_policy_mid", "t_routing_decision", "t_route_candidate_eval",
  "t_payment_intent", "t_payment_attempt", "t_payment_pricing_fact", "t_payment_instrument_token", "t_payment_action", "t_payment_intent_binding", "t_channel_transaction_ref", "t_channel_interaction", "t_duplicate_payment_case", "t_payment_state_transition",
  "t_payment_refund_position", "t_channel_refund", "t_channel_callback", "t_channel_callback_item", "t_channel_event_exception", "t_channel_dispute_notice", "t_channel_dispute_event", "t_channel_dispute_action", "t_command_idempotency", "t_compensation_event",
]);
const retiredNames = Object.freeze(["channel_connection", "channel_connection_version", "mid_limit", "mid_credential_ref", "mid_health", "routing_policy_binding", "routing_rule_condition", "payment_execution_gate", "payment_execution_gate_result", "three_ds_context", "payment_recovery_task", "channel_refund_attempt", "refund_transaction_ref", "active_slot"]);

function readScenario() {
  assert.ok(existsSync(scenarioPath), "Payment 现实场景文档不存在");
  return readFileSync(scenarioPath, "utf8");
}
function scenarioTableRows(source) {
  const start = source.indexOf("## 7. 30 表覆盖核对");
  assert.ok(start >= 0, "Payment 场景缺少 30 表覆盖章节");
  return source.slice(start).split(/\r?\n/u)
    .filter((line) => /^\|.*\|$/u.test(line))
    .flatMap((line) => [...line.matchAll(/t_[a-z0-9_]+/gu)].map((match) => match[0]));
}
function actualTableDataRows(source) {
  return source.split(/\r?\n/u).flatMap((line) => {
    const match = line.match(/^\|\s*(t_[a-z0-9_]+)\s*\|\s*(.*?)\s*\|\s*$/u);
    return match ? [{ tableName: match[1], values: match[2] }] : [];
  });
}
function assignedFields(values) {
  return [...values.matchAll(/(?:^|[，；：\s])([a-z][a-z0-9_]*)=([^，；\s|]+)/gu)]
    .map((match) => ({ fieldName: match[1], value: match[2] }));
}
function closedEnumValues(comment) {
  const match = comment.match(/允许值：([^。]+)。/u);
  return match ? match[1].split("、") : null;
}
function requireModel() {
  assert.ok(existsSync(modelPath), `Payment scenario model readiness failure: missing ${path.relative(workspaceRoot, modelPath)}`);
  return JSON.parse(readFileSync(modelPath, "utf8"));
}

const scenarioModelReadyAtRegistration = existsSync(modelPath);

test("Payment real-world scenario covers the approved 30 tables exactly once in its coverage catalog", () => {
  const source = readScenario();
  assert.match(source, /NI.*Channel/u);
  assert.match(source, /3DS.*UNKNOWN.*退款.*重复成功.*Callback.*争议/u);
  const coverageStart = source.indexOf("## 7. 30 表覆盖核对");
  const coverage = scenarioTableRows(source);
  assert.deepEqual(coverage, expectedTableNames);
  assert.equal(new Set(coverage).size, 30);
  for (const tableName of expectedTableNames) {
    assert.ok(source.slice(0, coverageStart).includes(`| ${tableName} |`), `场景未列出 ${tableName} 的实际行`);
  }
});

test("Payment scenario preserves pre-submit checks, asynchronous recovery and retired-model boundaries", () => {
  const source = readScenario();
  for (const expected of [
    "首次 Channel 提交前", "Fail-Closed", "Client/Operating Entity", "Compliance", "channel_account_id=81101", "account_code=NI-API-AE", "integration_config_key=payment.ni.ae.primary", "selected_channel_account_id=81101", "capability_code=ACTION.SALE", "boolean_value=1", "circuit_status=CLOSED", "health_observed_at=", "consumer_terms_acceptance_id=71011", "refund_policy_acceptance_id=71012", "privacy_notice_acceptance_id=71013", "UNKNOWN", "LOCAL_HANDLER", "LATE_DUPLICATE_SALE", "SUBMIT_EVIDENCE",
  ]) assert.ok(source.includes(expected), `场景缺少关键 Payment 边界：${expected}`);
  for (const retiredName of retiredNames) assert.match(source, new RegExp(`不采用[^\\n]*${retiredName}|${retiredName}.*不采用`, "u"));
});

test("Payment scenario freezes payment method, transaction instrument Token and Action authentication semantics", () => {
  const source = readScenario();

  assert.match(source, /t_payment_attempt.*payment_method_code=CARD/su);
  assert.match(source, /t_payment_attempt.*payment_instrument_token_id=101101/su);
  assert.match(source, /t_payment_instrument_token.*payment_instrument_token_id=101101.*source_submission_id=112001.*instrument_type=CARD.*instrument_ref=.*expires_at=/su);
  assert.match(source, /一个 Token 最多关联一个 Attempt/u);
  assert.match(source, /UNKNOWN 只查询原动作/u);
  assert.doesNotMatch(source, /\|\s*t_payment_instrument\s*\||card_reference=/u);
  assert.match(source, /t_payment_action.*action_type=AUTHENTICATE.*action_status=REQUIRES_ACTION.*interaction_type=THREE_DS_CHALLENGE/su);
  assert.match(source, /REDIRECT.*THREE_DS_CHALLENGE.*SDK.*APP_SWITCH.*QR_CODE/su);
  assert.match(source, /不保存.*Redirect URL.*Client Secret.*SDK Token.*QR Payload/su);
  assert.doesNotMatch(source, /\|\s*t_three_ds_context\s*\|/u);
  assert.doesNotMatch(source, /channel_token_ref=/u);
});

test("Payment scenario preserves the exact refund reference chain and strict duplicate-success definition", () => {
  const source = readScenario();

  assert.match(source, /t_payment_refund_position.*original_channel_txn_ref_id=/su);
  assert.match(source, /t_channel_refund.*payment_refund_position_id=.*refund_amount=.*refund_status=.*version=/su);
  assert.doesNotMatch(source, /\|\s*t_channel_refund\s*\|[^\n]*(?:payment_intent_id|successful_attempt_id|funding_binding_hash|refund_funding_reservation_id|funding_reserve_version|funding_bound_version)=/u);
  assert.match(source, /Commerce 本地预占后直接调用 PAY-I04/u);
  assert.match(source, /UNKNOWN.*只保留在 Payment 本地.*不通知 Finance/su);
  assert.match(source, /SUCCEEDED.*同时通知 Commerce 与 Finance/su);
  assert.match(source, /FAILED\/CANCELLED.*只通知 Commerce/u);
  assert.match(source, /t_payment_action.*action_type=REFUND.*target_channel_txn_ref_id=/su);
  assert.match(source, /t_channel_transaction_ref.*reference_type=REFUND.*original_channel_txn_ref_id=/su);
  assert.match(source, /原.*Transaction Ref.*payment_action_id.*Channel Account.*MID/su);
  assert.match(source, /同一 Intent.*第二.*不同.*(?:SALE|CAPTURE).*成功事实/su);
  assert.match(source, /重复 Callback.*同一外部交易.*QUERY.*AUTHORIZE.*CAPTURE.*不(?:属于|判定为).*重复支付/su);
  assert.match(source, /successful_attempt_id.*不(?:替换|改写).*CONFLICTED.*补偿退款/su);
  assert.match(source, /未 Capture.*Authorization.*VOID.*不.*重复支付退款/su);
});

test("Payment scenario limits generic state transitions to the five money-flow aggregates", () => {
  const source = readScenario();

  assert.match(source, /PAYMENT_INTENT.*PAYMENT_ATTEMPT.*PAYMENT_ACTION.*CHANNEL_REFUND.*DUPLICATE_PAYMENT_CASE/su);
  assert.match(source, /Channel Callback.*Callback\/Item\/Exception.*Channel Dispute Notice.*Event.*不.*t_payment_state_transition/su);
  const typedRows = actualTableDataRows(source)
    .filter(({ tableName }) => tableName === "t_payment_state_transition")
    .map((row) => assignedFields(row.values))
    .filter((fields) => fields.some(({ fieldName }) => fieldName === "aggregate_type"));
  assert.ok(typedRows.length > 0, "场景至少需要一条显式状态流转类型行");
  for (const fields of typedRows) {
    const aggregateType = fields.find(({ fieldName }) => fieldName === "aggregate_type")?.value;
    const rootType = fields.find(({ fieldName }) => fieldName === "root_type")?.value;
    const allowed = new Set(["PAYMENT_INTENT", "PAYMENT_ATTEMPT", "PAYMENT_ACTION", "CHANNEL_REFUND", "DUPLICATE_PAYMENT_CASE"]);
    assert.ok(allowed.has(aggregateType), `场景状态轨迹 aggregate_type 越界：${aggregateType}`);
    assert.ok(allowed.has(rootType), `场景状态轨迹 root_type 越界：${rootType}`);
  }
});

test("Payment scenario model artifact is ready for field-value validation", () => { requireModel(); });

if (scenarioModelReadyAtRegistration) {
test("Payment scenario real table field=value rows match model fields and closed enums", () => {
  const entities = new Map(requireModel().entities.map((entity) => [entity.defKey, entity]));
  const rows = actualTableDataRows(readScenario());
  assert.ok(rows.length >= 29, "场景缺少实际表数据行");
  const coveredTables = new Set();
  for (const { tableName, values } of rows) {
    const entity = entities.get(tableName);
    assert.ok(entity, `场景引用不存在的表 ${tableName}`);
    const fields = new Map(entity.fields.map((field) => [field.defKey, field]));
    const assignments = assignedFields(values);
    assert.ok(assignments.length > 0, `场景实际表行未解析出 field=value：${tableName}`);
    coveredTables.add(tableName);
    for (const { fieldName, value } of assignments) {
      const field = fields.get(fieldName);
      assert.ok(field, `场景引用不存在的字段 ${tableName}.${fieldName}`);
      const allowed = closedEnumValues(field.comment ?? "");
      if (allowed && value !== "NULL") assert.ok(allowed.includes(value), `场景枚举值不合法 ${tableName}.${fieldName}=${value}`);
    }
  }
  assert.deepEqual([...coveredTables].sort(), [...expectedTableNames].sort(), "场景实际表数据行未覆盖全部 30 表");
});
}
