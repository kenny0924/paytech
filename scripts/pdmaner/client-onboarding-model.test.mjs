import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const root = new URL("../../", import.meta.url);
const model = (file) => JSON.parse(readFileSync(new URL(file, root), "utf8"));
// CCT onboarding tables live in client-master; Agreement Party lives in commercial.
const clientMaster = model("paytech/client-master/docs/database/client-master.pdma.json");
const commercial = model("paytech/commercial/docs/database/commercial.pdma.json");
const cct = clientMaster;
const risk = model("risk-compliance-service/docs/database/risk-compliance.pdma.json");
const table = (m, name) => m.entities.find((e) => e.defKey === name);
const field = (t, name) => t.fields.find((f) => f.defKey === name);
const unique = (t, names) => t.indexes.some((i) => i.unique &&
  JSON.stringify(i.fields.map((f) => t.fields.find((x) => x.id === f.fieldDefKey).defKey)) === JSON.stringify(names));

test("Client partial drafts and one active application match the approved contract", () => {
  const app = table(cct, "t_client_application");
  assert.ok(unique(app, ["requested_by_account_id", "active_slot"]));
  assert.equal(field(app, "requested_by_account_id").notNull, true);
  assert.match(field(app, "status").comment, /RETURNED/u);
  assert.equal(field(app, "current_submission_id").type, "VARCHAR");
  assert.equal(field(app, "target_party_profile_id"), undefined);
  const profile = table(cct, "t_party_profile");
  assert.ok(unique(profile, ["party_id"]));
  assert.equal(field(profile, "version_no"), undefined);
  assert.ok(field(profile, "approved_time"));
  assert.equal(field(table(cct, "t_individual_profile"), "given_name").notNull, false);
  assert.equal(field(table(cct, "t_party_profile_identifier"), "identity_key_id").notNull, false);
  assert.equal(field(table(cct, "t_client_business_profile"), "business_description").len, 2000);
});

test("Risk distinguishes resubmissions and keeps shared legacy evidence references", () => {
  const cases = table(risk, "t_due_diligence_case");
  assert.ok(unique(cases, ["source_namespace", "source_application_id", "source_submission_id", "role_type", "role_id", "qualification_type"]));
  assert.match(field(cases, "status").comment, /RETURNED/u);
  const subjects = table(risk, "t_dd_case_subject");
  assert.equal(field(subjects, "source_profile_version_no").notNull, false);
  assert.ok(field(subjects, "source_relation_id"));
  assert.ok(field(subjects, "source_relation_version_id"));
  const classification = table(risk, "t_dd_case_classification");
  assert.ok(classification);
  assert.ok(unique(classification, ["case_id", "primary_slot"]));
  for (const name of ["t_partner_application", "t_operating_entity_application"]) {
    assert.ok(table(cct, name).correlations.some((r) => r.refEntity === table(cct, "t_party_profile").id));
  }
  // Agreement Party moved to commercial: the shared Party Profile reference crosses
  // databases, so it keeps the indexed field but no PDManer correlation.
  const agreementParty = table(commercial, "t_agreement_party");
  const profileRef = field(agreementParty, "party_profile_id");
  assert.ok(table(clientMaster, "t_party_profile") && !table(commercial, "t_party_profile"));
  assert.ok(agreementParty.indexes.some((i) => i.fields[0]?.fieldDefKey === profileRef.id));
  assert.ok(!agreementParty.correlations.some((r) => r.myField === profileRef.id));
});

test("all approved onboarding column shapes and unique keys reach the current Owner models", () => {
  const review = model("requirements/20260914/client-console-注册与客户入驻/db/target-field-projection.json");
  for (const [owner, current] of [["CCT", cct], ["Risk", risk]]) {
    for (const expected of review.systems[owner].tables) {
      const actual = table(current, expected.name);
      assert.deepEqual(actual.fields.map(f => f.defKey).sort(), expected.columns.map(f => f.name).sort(), expected.name);
      for (const column of expected.columns) {
        const f = field(actual, column.name);
        assert.deepEqual([f.type, String(f.len), String(f.scale), !f.notNull, String(f.defaultValue)],
          [column.type, String(column.length), String(column.scale), column.nullable, String(column.default)],
          `${expected.name}.${column.name}`);
      }
      for (const index of expected.indexes.filter(i => i.unique)) {
        assert.ok(unique(actual, index.columns), `${owner}.${expected.name}.${index.name}`);
      }
    }
  }
});
