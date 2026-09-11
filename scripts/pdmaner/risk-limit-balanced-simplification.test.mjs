import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { TABLES as DECISION_ENGINE_TABLES } from "./risk/manifests/02-decision-engine.mjs";
import { TABLES as LIMIT_TABLES } from "./risk/manifests/05-limits.mjs";
import { TABLES as RISK_TABLES } from "./risk/manifest.mjs";
import { CHECK_BINDINGS, CHECK_CONTRACTS } from "./risk/model-policy.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const modelPath = path.join(workspaceRoot, "risk-compliance-service/docs/database/risk-compliance.pdma.json");
const EXPECTED_LIMIT_TABLES = Object.freeze([
  "limit_version",
  "decision_point_limit_binding",
  "limit_position",
  "limit_reservation_group",
  "limit_reservation",
  "limit_group_state_change",
  "limit_movement",
]);

function tablesByKey(tables) {
  return new Map(tables.map((table) => [table.defKey, table]));
}

function fieldNames(table) {
  return table.fields.map((field) => field.defKey);
}

function assertFields(table, expected) {
  const actual = new Set(fieldNames(table));
  for (const field of expected) assert.ok(actual.has(field), `${table.defKey} 缺少字段 ${field}`);
}

function assertAbsentFields(table, forbidden) {
  const actual = new Set(fieldNames(table));
  for (const field of forbidden) assert.ok(!actual.has(field), `${table.defKey} 仍保留字段 ${field}`);
}

function assertUniqueIndex(table, expected) {
  assert.ok(
    table.indexes.some((index) => index.unique && JSON.stringify(index.fields) === JSON.stringify(expected)),
    `${table.defKey} 缺少唯一键 (${expected.join(",")})`,
  );
}

function assertRelation(table, childField, parentTable, parentField) {
  assert.ok(
    table.relations.some((relation) => relation.childField === childField
      && relation.parentTable === parentTable
      && relation.parentField === parentField),
    `${table.defKey}.${childField} 缺少到 ${parentTable}.${parentField} 的关系`,
  );
}

test("Risk Limit balanced simplification freezes exactly seven tables", () => {
  assert.deepEqual(LIMIT_TABLES.map((table) => table.defKey), EXPECTED_LIMIT_TABLES);
  assert.equal(RISK_TABLES.length, 72);

  const keys = new Set(RISK_TABLES.map((table) => table.defKey));
  for (const removed of ["limit_definition", "limit_plan", "limit_plan_version", "limit_plan_item"]) {
    assert.equal(keys.has(removed), false, `仍保留旧 Limit 表 ${removed}`);
  }
});

test("Limit Version absorbs identity and Point Obligation owns typed bindings", () => {
  const limits = tablesByKey(LIMIT_TABLES);
  const decision = tablesByKey(DECISION_ENGINE_TABLES);
  const version = limits.get("limit_version");
  const binding = limits.get("decision_point_limit_binding");
  const obligation = decision.get("decision_point_obligation");

  assertFields(version, [
    "limit_version_id", "limit_code", "limit_name", "version_no", "subject_type", "metric_type",
    "window_unit", "window_size", "amount_limit", "count_limit", "currency_mode", "fixed_currency",
    "content_hash", "status", "frozen_by", "frozen_time",
  ]);
  assertAbsentFields(version, ["limit_id"]);
  assertUniqueIndex(version, ["limit_code", "version_no"]);
  assert.equal(version.relations.length, 0);

  assertFields(obligation, ["obligation_id", "point_version_id", "obligation_type", "action_version_id"]);
  assertAbsentFields(obligation, ["limit_plan_version_id"]);
  assert.equal(obligation.relations.some((relation) => relation.parentTable === "limit_plan_version"), false);

  assertFields(binding, [
    "binding_id", "obligation_id", "limit_version_id", "sequence_no",
    "subject_source_type", "subject_feature_version_id", "subject_input_field_id",
    "amount_source_type", "amount_feature_version_id", "amount_input_field_id",
  ]);
  assert.equal(binding.audit, "immutable");
  assertUniqueIndex(binding, ["obligation_id", "limit_version_id"]);
  assertUniqueIndex(binding, ["obligation_id", "sequence_no"]);
  assertRelation(binding, "obligation_id", "decision_point_obligation", "obligation_id");
  assertRelation(binding, "limit_version_id", "limit_version", "limit_version_id");
});

test("Limit runtime keeps authority and audit while removing derived fields", () => {
  const limits = tablesByKey(LIMIT_TABLES);
  const position = limits.get("limit_position");
  const group = limits.get("limit_reservation_group");
  const reservation = limits.get("limit_reservation");
  const stateChange = limits.get("limit_group_state_change");
  const movement = limits.get("limit_movement");

  assertAbsentFields(position, ["subject_type", "currency"]);
  assertUniqueIndex(position, ["limit_version_id", "subject_key", "currency_scope_key", "window_start"]);

  assertFields(group, ["reservation_group_id", "assessment_id", "obligation_id", "status", "hold_reason", "resolved_time", "version"]);
  assertAbsentFields(group, ["payment_attempt_id", "plan_version_id", "reservation_count", "content_hash"]);
  assertUniqueIndex(group, ["assessment_id"]);
  assertRelation(group, "obligation_id", "decision_point_obligation", "obligation_id");

  assert.equal(reservation.audit, "immutable");
  assert.deepEqual(fieldNames(reservation), [
    "reservation_id", "reservation_group_id", "position_id", "amount", "count_value", "create_time",
  ]);
  assertUniqueIndex(reservation, ["reservation_group_id", "position_id"]);

  assert.ok(stateChange, "必须保留 Group State Change 审计");
  assertAbsentFields(movement, ["position_id"]);
  assertRelation(movement, "reservation_id", "limit_reservation", "reservation_id");
  assert.equal(movement.relations.length, 1);
});

test("Limit CHECK policy rejects ambiguous targets and invalid counter facts", () => {
  assert.equal(
    CHECK_CONTRACTS.ck_obligation_target,
    "(obligation_type='ACTION' AND action_version_id IS NOT NULL) OR (obligation_type IN ('RESERVE_LIMIT_GROUP','CHECK_SETTLEMENT_ELIGIBILITY') AND action_version_id IS NULL)",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_limit_threshold,
    "version_no>0 AND window_size>0 AND ((metric_type='AMOUNT' AND amount_limit IS NOT NULL AND amount_limit>0 AND count_limit IS NULL) OR (metric_type='COUNT' AND amount_limit IS NULL AND count_limit IS NOT NULL AND count_limit>0) OR (metric_type='AMOUNT_AND_COUNT' AND amount_limit IS NOT NULL AND amount_limit>0 AND count_limit IS NOT NULL AND count_limit>0))",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_limit_currency_mode,
    "((currency_mode='FIXED' AND fixed_currency IS NOT NULL) OR (currency_mode IN ('PER_CURRENCY','NO_CURRENCY') AND fixed_currency IS NULL)) AND NOT (metric_type IN ('AMOUNT','AMOUNT_AND_COUNT') AND currency_mode='NO_CURRENCY')",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_limit_version_freeze,
    "(status='DRAFT' AND frozen_by IS NULL AND frozen_time IS NULL) OR (status IN ('FROZEN','RETIRED') AND frozen_by IS NOT NULL AND frozen_time IS NOT NULL)",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_limit_group,
    "version>0 AND ((status='RESERVED' AND hold_reason IS NULL AND resolved_time IS NULL) OR (status='PAYMENT_UNKNOWN' AND hold_reason IS NOT NULL AND resolved_time IS NULL) OR (status IN ('COMMITTED','RELEASED') AND hold_reason IS NULL AND resolved_time IS NOT NULL))",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_limit_reservation,
    "(amount IS NULL OR amount>0) AND count_value>=0 AND (amount IS NOT NULL OR count_value>0)",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_limit_group_change,
    "aggregate_version>0 AND ((aggregate_version=1 AND from_status IS NULL AND to_status='RESERVED') OR (aggregate_version>1 AND ((from_status='RESERVED' AND to_status IN ('PAYMENT_UNKNOWN','COMMITTED','RELEASED')) OR (from_status='PAYMENT_UNKNOWN' AND to_status IN ('COMMITTED','RELEASED')))))",
  );
  assert.equal(
    CHECK_CONTRACTS.ck_limit_movement,
    "position_version>0 AND amount_delta>=0 AND count_delta>=0 AND (amount_delta>0 OR count_delta>0)",
  );

  assert.deepEqual(CHECK_BINDINGS.ck_limit_binding_subject, ["decision_point_limit_binding"]);
  assert.deepEqual(CHECK_BINDINGS.ck_limit_binding_amount, ["decision_point_limit_binding"]);
  assert.deepEqual(CHECK_BINDINGS.ck_limit_version_freeze, ["limit_version"]);
  assert.deepEqual(CHECK_BINDINGS.ck_limit_group_change, ["limit_group_state_change"]);
  assert.equal(CHECK_BINDINGS.ck_positive_version.includes("limit_reservation"), false);
});

test("Generated Risk model exposes the seven-table Limit contract", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const limitDiagram = model.diagrams.find((diagram) => diagram.defKey === "limits");
  assert.equal(model.entities.length, 72);
  assert.deepEqual(
    limitDiagram.properties.primaryTables,
    EXPECTED_LIMIT_TABLES.map((table) => `t_${table}`),
  );
});
