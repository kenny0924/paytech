import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { renderSchema } from "./generate-oe-client-schemas.mjs";

const source = "paytech/payment/docs/database/payment.pdma.json";
const model = JSON.parse(readFileSync(new URL(`../../${source}`, import.meta.url), "utf8"));
const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
const fields = (table) => new Set(entities.get(table).fields.map((field) => field.defKey));

test("Execution removes obsolete roots and duplicated upstream/Channel facts", () => {
  for (const table of ["t_payment_intent_binding", "t_payment_pricing_fact", "t_payment_state_transition", "t_payment_customer_action", "t_payment_instrument"]) {
    assert.ok(!entities.has(table), `Obsolete table: ${table}`);
  }
  const removed = {
    t_payment_intent: ["source_order_context_id", "context_format"],
    t_payment_attempt: ["failure_stage", "status_reason_code", "consumer_contract_id", "consumer_terms_acceptance_id", "refund_policy_acceptance_id", "privacy_notice_acceptance_id", "acceptance_completed_at", "order_version", "transaction_assessment_id", "risk_result", "limit_reservation_group_id", "payment_goal", "authentication_requirement", "card_reference"],
    t_payment_action: ["channel_id", "channel_account_id", "mid_id", "target_payment_action_id", "authentication_action_id", "channel_operation_ref", "authentication_outcome", "challenge_performed", "authentication_valid_until", "status_reason_code"],
  };
  for (const [table, names] of Object.entries(removed)) {
    for (const name of names) assert.ok(!fields(table).has(name), `${table}.${name} must be removed`);
  }
});

test("Execution preserves frozen routing, original money target, risk and interaction facts", () => {
  for (const name of ["channel_id", "channel_account_id", "mid_id", "capture_mode", "payment_instrument_token_id", "active_action_id"]) {
    assert.ok(fields("t_payment_attempt").has(name), `Attempt must contain ${name}`);
  }
  for (const name of ["target_channel_txn_ref_id", "channel_idempotency_key", "transaction_assessment_id", "risk_result", "authentication_requirement", "interaction_type", "interaction_expires_at", "channel_response_code", "channel_response_msg"]) {
    assert.ok(fields("t_payment_action").has(name), `Action must contain ${name}`);
  }
  const action = entities.get("t_payment_action");
  const key = action.indexes.find((index) => index.defKey === "uk_payment_action_idempotency");
  assert.equal(key.unique, true);
  assert.deepEqual(key.fields.map((part) => action.fields.find((field) => field.id === part.fieldDefKey).defKey), ["channel_idempotency_key"]);
});

test("Payment canonical schema exactly matches the maintained model", () => {
  const sql = readFileSync(new URL("../../paytech/payment/docs/database/sql/payment-schema.sql", import.meta.url), "utf8");
  assert.equal(sql, renderSchema(model, source));
  assert.doesNotMatch(sql, /\b(?:FOREIGN KEY|CHECK\s*\()/u);
});
