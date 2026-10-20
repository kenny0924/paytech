import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import path from "node:path";
import { CHECK_CONTRACTS as MODEL_CHECK_CONTRACTS } from "./finance/model-policy.mjs";
import { renderSchema, schemaTargets } from "./generate-oe-client-schemas.mjs";
import { generateClientMasterModel } from "./generate-client-master-model.mjs";
import { generateCommercialModel } from "./generate-commercial-model.mjs";
import { generateAcquiringModel } from "./generate-acquiring-model.mjs";
import { generateCounterpartyModel } from "./generate-counterparty-model.mjs";
import { generatePaymentModel } from "./generate-payment-model.mjs";
import { generateFinanceModel } from "./finance/generate-finance-model.mjs";

const table = (model, name) => {
  const result = model.entities.find((entry) => entry.defKey === name);
  assert.ok(result, `Missing table ${name}`);
  return result;
};
const field = (entity, name) => {
  const result = entity.fields.find((entry) => entry.defKey === name);
  assert.ok(result, `Missing ${entity.defKey}.${name}`);
  return result;
};
const unique = (entity, names) => entity.indexes.some((index) => index.unique &&
  JSON.stringify(index.fields.map((part) => entity.fields.find((f) => f.id === part.fieldDefKey)?.defKey)) === JSON.stringify(names));

test("OE has one immutable five-character primary identity and no numeric surrogate", () => {
  const oe = table(generateClientMasterModel(), "t_operating_entity");
  assert.equal(field(oe, "entity_no").type, "CHAR");
  assert.equal(field(oe, "entity_no").len, 5);
  assert.equal(field(oe, "entity_no").primaryKey, true);
  assert.ok(!oe.fields.some((f) => f.defKey === "operating_entity_id"));
});

test("Polymorphic identities can carry OE codes without converting numeric Party and Book keys", () => {
  const payment = generatePaymentModel();
  const finance = generateFinanceModel();
  for (const [model, name, key] of [
    [payment, "t_routing_policy", "scope_id"],
    [finance, "t_account", "owner_id"],
    [finance, "t_economic_fact_line", "party_id"],
  ]) {
    const actual = field(table(model, name), key);
    assert.deepEqual([actual.type, actual.len, actual.extProps.collation], ["VARCHAR", 64, "ascii_bin"]);
  }
  const sourcePolicy = MODEL_CHECK_CONTRACTS.ck_txn_econ_component_source;
  assert.match(sourcePolicy, /CCT_PRICE_COMPONENT.*source_price_id IS NOT NULL.*source_rule_scope_id IS NULL/u);
});

test("Four manual schemas exactly preserve model metadata, types and physical constraints", () => {
  const root = path.resolve(import.meta.dirname, "../..");
  assert.deepEqual(schemaTargets.map(([repository]) => repository), [
    "paytech/client-master", "paytech/commercial", "paytech/acquiring", "paytech/counterparty", "paytech/payment",
    "paytech/channel-connector",
    "paytech/ledger", "paytech/account", "paytech/billing", "paytech/reconciliation", "paytech/tax", "paytech/payout",
  ]);
  for (const [repository, name] of schemaTargets) {
    const source = `${repository}/docs/database/${name}.pdma.json`;
    const model = JSON.parse(readFileSync(path.join(root, source), "utf8"));
    const sql = readFileSync(path.join(root, repository, "docs/database/sql", `${name}-schema.sql`), "utf8");
    assert.equal(sql, renderSchema(model, source));
    const executable = sql.replace(/'(?:''|\\.|[^'])*'/gu, "''").replace(/^--.*$/gmu, "");
    assert.doesNotMatch(executable, /\b(?:CHECK|FOREIGN\s+KEY|DROP|ALTER|INSERT|DELETE|UPDATE)\b/iu);
    assert.equal((executable.match(/CREATE TABLE /gu) ?? []).length, model.entities.length);
    for (const entity of model.entities) {
      assert.ok(entity.comment.length <= 2048, `${entity.defKey} table comment exceeds MySQL limit`);
      for (const item of entity.fields) assert.ok(item.comment.length <= 1024);
    }
  }
});

test("Scope approval preserves exact tuples independently from Client-wide affiliation priority", () => {
  const model = generateClientMasterModel();
  const application = table(model, "t_operating_entity_application");
  assert.match(application.comment, /SCOPE_CHANGE.*沿用既有主体和唯一来源关系，不回退主体状态/u);
  const scope = table(model, "t_operating_entity_scope");
  assert.ok(unique(scope, ["application_id", "consumer_country_code", "currency", "industry_code", "mcc_code"]));
  assert.ok(unique(scope, ["entity_no", "consumer_country_code", "currency", "industry_code", "mcc_code", "active_slot"]));
  const affiliation = table(model, "t_client_entity_affiliation");
  assert.ok(unique(affiliation, ["client_id", "entity_no", "active_slot"]));
  assert.ok(unique(affiliation, ["client_id", "priority_no", "active_slot"]));
  for (const name of ["scope_country", "scope_currency", "industry_code", "mcc_code"]) {
    assert.ok(!affiliation.fields.some((f) => f.defKey === name));
  }
  for (const name of ["t_entity_assignment_rule", "t_client_entity_assignment", "t_agreement_acceptance"]) {
    for (const current of [model, generateCommercialModel()]) {
      assert.ok(!current.entities.some((e) => e.defKey === name), `${name} must leave the new-write model`);
    }
  }
});

test("Agreement Party allows shared signatures within one party while reserving references across parties", () => {
  const party = table(generateCommercialModel(), "t_agreement_party");
  assert.ok(unique(party, ["agreement_id", "party_id", "party_role"]));
  assert.ok(unique(party, ["agreement_id", "party_id", "acceptance_ref_slot"]));
  assert.ok(unique(party, ["acceptance_reference", "acceptance_ref_slot"]));
  assert.equal(field(party, "acceptance_ref_slot").notNull, false);
  for (const name of ["accepted_time", "acceptance_recorded_at", "evidence_document_id"]) {
    assert.equal(field(party, name).notNull, false);
  }
});

test("Client plans are per agreement while shared Partner associations and version agreement survive", () => {
  const model = generateCommercialModel();
  const plan = table(model, "t_commercial_term_plan");
  assert.ok(unique(plan, ["agreement_id", "term_type"]));
  assert.ok(!unique(plan, ["client_id"]));
  assert.ok(unique(plan, ["local_partner_id", "term_type"]));
  field(table(model, "t_commercial_term_version"), "agreement_id");
  field(table(model, "t_agreement_operating_entity"), "entity_no");
  const price = table(model, "t_commercial_term_price");
  for (const name of ["settlement_business_days", "business_calendar_code", "settlement_timezone", "cutoff_second_of_day"]) {
    assert.equal(field(price, name).notNull, false, "Client-only requirement must not break Partner prices");
  }
});

test("Basic Checkout can exist before seller and final tax; preparation-era columns are retired (C7)", () => {
  const model = generateAcquiringModel();
  const order = table(model, "t_commerce_order");
  for (const name of ["entity_no", "tax_amount", "rounding_amount", "total_amount", "termination_requested_at", "customer_id"]) {
    assert.equal(field(order, name).notNull, false);
  }
  assert.equal(field(order, "net_amount").notNull, true);
  for (const name of ["preparation_command_id", "context_format"]) {
    assert.ok(!order.fields.some((f) => f.defKey === name), `${name} is removed by C7`);
  }
  assert.ok(!generateCounterpartyModel().entities.some((entity) => entity.defKey === "t_commerce_order"));
  const context = table(model, "t_order_context");
  for (const name of ["affiliation_id", "entity_scope_id", "entity_source_relation_id", "client_pricing_version_id"]) field(context, name);
  assert.ok(!context.fields.some((f) => f.defKey === "assignment_id"));
});

test("Payment preserves OE and term versions while retiring its standalone pricing snapshot", () => {
  const model = generatePaymentModel();
  const intent = table(model, "t_payment_intent");
  assert.equal(field(intent, "entity_no").type, "CHAR");
  for (const name of ["client_pricing_version_id", "partner_share_version_id", "platform_charge_version_id"]) field(intent, name);
  for (const name of ["assignment_id", "source_order_context_id", "context_format"]) {
    assert.ok(!intent.fields.some((f) => f.defKey === name));
  }
  assert.ok(!model.entities.some((entity) => entity.defKey === "t_payment_pricing_fact"));
  // Finance's historical payment_fact_id remains below; no fabricated replacement identity.
});

test("Finance keeps typed book identity and separate legacy channel-cost and new price evidence", () => {
  const model = generateFinanceModel();
  const book = table(model, "t_ledger_book");
  assert.equal(field(book, "entity_id").type, "VARCHAR");
  assert.equal(field(book, "id").type, "BIGINT");
  const economics = table(model, "t_economic_fact");
  for (const name of ["entity_no", "payment_fact_id", "client_price_id", "cutoff_second_of_day"]) field(economics, name);
  const component = table(model, "t_economic_fact_line");
  for (const name of ["source_rule_scope_id", "source_price_id", "source_band_evidence"]) field(component, name);
});

test("Every changed model retains valid local relations, indexes and diagram endpoints", () => {
  for (const generate of [generateClientMasterModel, generateCommercialModel, generateAcquiringModel, generateCounterpartyModel,
    generatePaymentModel, generateFinanceModel]) {
    const model = generate();
    const entities = new Map(model.entities.map((e) => [e.id, e]));
    for (const e of model.entities) {
      const fields = new Map(e.fields.map((f) => [f.id, f]));
      for (const i of e.indexes) for (const part of i.fields) assert.ok(fields.has(part.fieldDefKey));
      for (const r of e.correlations) {
        assert.ok(fields.has(r.myField));
        assert.ok(entities.get(r.refEntity)?.fields.some((f) => f.id === r.refField));
      }
    }
  }
});
