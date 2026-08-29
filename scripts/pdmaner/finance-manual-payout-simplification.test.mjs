import assert from "node:assert/strict";
import test from "node:test";

import { TABLES } from "./finance/manifest.mjs";
import { CHECK_BINDINGS, CHECK_CONTRACTS, DIAGRAM_GROUPS } from "./finance/model-policy.mjs";

const byKey = (tables) => new Map(tables.map((table) => [table.defKey, table]));
const fieldKeys = (table) => table?.fields?.map((field) => field.defKey) ?? [];

const expectedPayoutTables = [
  "beneficiary_account", "finance_restriction_proj", "payout", "payout_approval",
  "payout_execution", "payout_evidence_ref", "payout_event",
];

test("Finance manual payout simplification has exactly 84 tables and seven payout tables", () => {
  assert.equal(TABLES.length, 84);
  assert.deepEqual(
    TABLES.filter((table) => expectedPayoutTables.includes(table.defKey)).map((table) => table.defKey),
    expectedPayoutTables,
  );
  const tables = byKey(TABLES);
  for (const removed of [
    "beneficiary_account_ver", "settlement_profile", "settlement_profile_ver", "profile_review",
    "payout_request", "settlement_run", "settlement_candidate", "candidate_requirement_snapshot",
    "settlement", "settlement_req_snapshot", "settlement_approval", "settlement_cancel",
    "settlement_cancel_approval", "settlement_state_change", "settlement_payment",
    "settlement_payment_attempt", "settlement_payment_event", "settlement_evidence_ref",
  ]) assert.ok(!tables.has(removed), `restored removed table ${removed}`);
});

test("Finance payout freezes approved owner, fee, Risk and accounting coordinates", () => {
  const tables = byKey(TABLES);
  for (const key of [
    "owner_type", "owner_id", "operating_entity_id", "currency", "beneficiary_account_id",
    "beneficiary_content_hash", "available_account_id", "locked_account_id", "requested_amount",
    "fee_mode", "fee_amount", "owner_debit_amount", "beneficiary_amount", "cash_required_amount",
    "assessment_id", "evaluation_id", "eligibility_decision", "eligibility_reason_codes",
    "eligibility_matched_controls", "risk_required_amount", "risk_required_currency",
    "control_set_hash", "eligibility_hash", "request_hash", "approval_hash", "journal_entry_id",
    "reconciled_time", "status", "version",
  ]) assert.ok(fieldKeys(tables.get("payout")).includes(key), `missing payout.${key}`);

  assert.ok(fieldKeys(tables.get("payout_execution")).includes("active_slot"));
  assert.ok(fieldKeys(tables.get("payout_event")).includes("payout_version_after"));
  assert.ok(fieldKeys(tables.get("payout_event")).includes("execution_version_after"));
  assert.match(CHECK_CONTRACTS.ck_payout_amounts, /ADDED_ON_TOP.*DEDUCTED_FROM_PAYOUT/u);
  assert.match(CHECK_CONTRACTS.ck_payout_event_scope, /event_scope='PAYOUT'.*event_scope='EXECUTION'/u);
  assert.deepEqual(CHECK_BINDINGS.ck_payout_amounts, ["payout"]);
  assert.deepEqual(
    DIAGRAM_GROUPS.find((group) => group.defKey === "manual_payout")?.primary,
    expectedPayoutTables,
  );
  assert.equal(DIAGRAM_GROUPS.length, 6);
});

test("Finance shares maturity and reconciles payout execution to bank debits", () => {
  const tables = byKey(TABLES);
  const economicsFields = fieldKeys(tables.get("transaction_economics"));
  assert.ok(economicsFields.includes("balance_available_time"));
  assert.ok(!economicsFields.includes("client_available_time"));
  assert.match(CHECK_CONTRACTS.ck_account_owner_bucket, /LOCAL_PARTNER.*PENDING.*AVAILABLE.*LOCKED/u);

  const bankMatchFields = fieldKeys(tables.get("bank_recon_match"));
  assert.ok(bankMatchFields.includes("payout_execution_id"));
  assert.ok(bankMatchFields.includes("payout_component_type"));
  assert.ok(!bankMatchFields.includes("settlement_payment_attempt_id"));
  assert.match(CHECK_CONTRACTS.ck_bank_recon_match_target, /PAYOUT_DEBIT.*payout_execution_id/u);
  assert.match(CHECK_CONTRACTS.ck_bank_recon_payout_component, /BENEFICIARY_TRANSFER.*BANK_FEE.*COMBINED/u);
});
