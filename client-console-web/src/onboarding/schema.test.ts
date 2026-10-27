import { test } from "node:test";
import assert from "node:assert/strict";
import { applicationFixture, schemaFixture } from "./fixture.test-data.ts";
import {
  availableTasks, checksums, draftPath, fieldIssue, findField, formatOnboardingCode, identifierAlternatives, isIdentifierLead, isVisible,
  onboardingCodeComplete, selectedIdentifier, taskBlocks,
} from "./schema.ts";
import { readCheck, readJurisdictions } from "./model.ts";
import { draftPatch, localIssues } from "./draft.ts";
import { RequestFailure } from "../auth.ts";
import { serverIssues } from "./draft.ts";

const schema = schemaFixture();
const field = (key: string) => findField(schema, key)!;

test("field keys are draft paths; identifiers write one value slot; address expands by components", () => {
  assert.equal(draftPath(field("organization.legalName")), "organization.legalName");
  assert.equal(draftPath(field("attributes.SA_VAT_NUMBER")), "attributes.SA_VAT_NUMBER");
  assert.equal(draftPath(field("identifier.SA_CR_NUMBER")), "identifier.identifierValue");
  assert.equal(draftPath(field("address")), null);
  assert.deepEqual(schema.addressFormat?.components.map((item) => item.component), ["buildingNumber", "city", "postalCode"]);
});

test("visibleWhen supports alternatives and hides fields until the condition holds", () => {
  const authority = field("organization.registrationAuthorityCode");
  assert.equal(isVisible(authority, {}, schema), false);
  assert.equal(isVisible(authority, { "organization.organizationType": "FZE" }, schema), true);
  assert.equal(isVisible(authority, { "organization.organizationType": "LLC" }, schema), false);
});

test("identifier alternatives collapse into one control and follow the chosen type", () => {
  const nid = field("identifier.SA_NATIONAL_ID"), passport = field("identifier.PASSPORT");
  assert.deepEqual(identifierAlternatives(nid, schema).map((item) => item.fieldKey), ["identifier.SA_NATIONAL_ID", "identifier.PASSPORT"]);
  assert.equal(isIdentifierLead(nid, schema), true);
  assert.equal(isIdentifierLead(passport, schema), false);
  assert.equal(selectedIdentifier(nid, schema, {})?.code, "SA_NATIONAL_ID");
  assert.equal(selectedIdentifier(nid, schema, { "identifier.identifierType": "PASSPORT" })?.code, "PASSPORT");
});

test("format issues: fixed digits, checksum registry, url / email and length", () => {
  const cr = field("identifier.SA_CR_NUMBER");
  assert.deepEqual(fieldIssue(cr, "701234567", false, "[0-9]{10}"), { key: "error.digits", params: { expected: 10, actual: 9 } });
  assert.equal(fieldIssue(cr, "7012-345-678", false, "[0-9]{10}"), null);
  assert.equal(checksums.LUHN("1000000008"), true);
  assert.equal(checksums.LUHN("1000000009"), false);
  assert.equal(checksums.EG_NID("29001011234567"), true);
  assert.equal(checksums.EG_NID("29013011234567"), false);
  assert.deepEqual(fieldIssue(field("identifier.SA_NATIONAL_ID"), "1000000009", false, "1[0-9]{9}", "LUHN"), { key: "error.checksum" });
  assert.deepEqual(fieldIssue(field("contact.email"), "ops@", false), { key: "error.email" });
  assert.deepEqual(fieldIssue(field("organization.legalName"), "", true), { key: "error.field_missing" });
  assert.equal(fieldIssue(field("organization.legalName"), "", false), null);
});

test("local check reports missing required fields only when asked, and accepts a saved masked identifier", () => {
  const blocks = taskBlocks(schema, "profile", "PRIMARY");
  assert.deepEqual(Object.keys(localIssues(blocks, {}, schema, false)), []);
  const missing = localIssues(blocks, {}, schema, true);
  assert.ok(missing["organization.legalName"] && missing["identifier.identifierValue"]);
  const saved = localIssues(blocks, { "organization.legalName": "Noor", "organization.organizationType": "LLC", "identifier.identifierMasked": "******5678" }, schema, true);
  assert.equal(saved["identifier.identifierValue"], undefined);
  const wrong = localIssues(blocks, { "attributes.SA_VAT_NUMBER": "123" }, schema, false);
  assert.deepEqual(wrong["attributes.SA_VAT_NUMBER"], { key: "error.format" });
});

test("draft patch skips local UI keys, masked read-backs and fields with format issues", () => {
  const body = draftPatch({ "organization.legalName": "A" },
    { "organization.legalName": "B", "~replace.identifier": "1", "identifier.identifierMasked": "***", "attributes.SA_VAT_NUMBER": "12" },
    new Set(["attributes.SA_VAT_NUMBER"]));
  assert.deepEqual(body, { dirtyFields: ["organization.legalName"], values: { organization: { legalName: "B" } } });
});

test("requirement-level server errors map back to draft paths", () => {
  const failure = new RequestFailure(422, "x", "", {}, "SUBMISSION_INCOMPLETE",
    { errors: [{ requirementKey: "organization.legalName", code: "FIELD_MISSING" }, { requirementKey: "address.postalCode", code: "FIELD_FORMAT_INVALID" }] });
  assert.deepEqual(Object.keys(serverIssues(failure, schema)).sort(), ["address.postalCode", "organization.legalName"]);
});

test("tasks come from the schema: individuals without people sections get no people task", () => {
  assert.deepEqual(availableTasks(schema), ["profile", "address", "people", "materials", "declarations", "review"]);
  assert.deepEqual(schema.declarations, [{ declarationType: "INFORMATION_TRUE", labelKey: "onboardingSchema.declaration.INFORMATION_TRUE" }]);
  assert.equal(applicationFixture().jurisdictionCode, "SA");
});

test("onboarding code is grouped 4-4 and uppercase; closed jurisdictions are not offered", () => {
  assert.equal(formatOnboardingCode("gp7k2q9z"), "GP7K-2Q9Z");
  assert.equal(formatOnboardingCode(" gp7k - 2q9z extra"), "GP7K-2Q9Z");
  assert.equal(onboardingCodeComplete("GP7K-2Q9"), false);
  assert.deepEqual(readJurisdictions([{ jurisdictionCode: "AE", countryCode: "AE", labelKey: "k", acceptancePolicy: "OPEN" },
    { jurisdictionCode: "RU", countryCode: "RU", labelKey: "k", acceptancePolicy: "CLOSED" }]).map((item) => item.jurisdictionCode), ["AE"]);
});

test("submission check must be well-formed and consistent", () => {
  assert.deepEqual(readCheck({ ready: true, errors: [] }, "x"), { ready: true, errors: [] });
  assert.throws(() => readCheck({ ready: true, errors: [{ requirementKey: "a", code: "FIELD_MISSING" }] }, "x"));
  assert.throws(() => readCheck({ ready: false, issues: [] }, "x"));
});

test("owner schema shape: catalog items use code, rule numbers arrive as strings, fallback address format", async () => {
  const { readSchema } = await import("./model.ts");
  const value = readSchema({ schemaId: "1", versionNo: 1, sections: [], catalogs: { CITY: [{ code: "RIYADH", labelKey: "k" }] },
    relationRules: { uboThresholdPct: "25", minSignatories: 1 }, addressFormat: null,
    fallbackAddressFormat: { code: "GENERIC", components: [{ component: "city", required: true, labelKey: "k", order: 30 }] },
    relationTypes: [{ code: "OWNERSHIP", ruleRole: "OWNER", ownershipPctRequired: true, uboFlag: false, labelKey: "k" }] }, "x");
  assert.deepEqual(value.catalogs.CITY, [{ itemCode: "RIYADH", labelKey: "k" }]);
  assert.equal(value.relationRules.uboThresholdPct, 25);
  assert.equal(value.addressFormat?.code, "GENERIC");
  assert.equal(value.relationTypes[0].ruleRole, "OWNER");
});

test("formal-submit Problem errors {field, message} become requirement-level errors with person links", async () => {
  const { problemErrors } = await import("./model.ts");
  assert.deepEqual(problemErrors({ code: "SUBMISSION_INCOMPLETE", errors: [
    { field: "document.BANK", message: "DOCUMENT_MISSING/UPLOAD_DOCUMENT" },
    { field: "people[31].individual.birthDate", message: "FIELD_MISSING/PROVIDE_FIELD" },
    { field: "organization.legalName", message: "free text is ignored" },
  ] }), [
    { requirementKey: "document.BANK", subjectPartyId: null, code: "DOCUMENT_MISSING", remediation: "UPLOAD_DOCUMENT" },
    { requirementKey: "individual.birthDate", subjectPartyId: "31", code: "FIELD_MISSING", remediation: "PROVIDE_FIELD" },
  ]);
  const { locate } = await import("./progress.ts");
  const app = applicationFixture({ people: [{ partyId: "31", editable: true, values: {} }] });
  assert.deepEqual(locate("individual.birthDate", "31", null, app, schema).task, "people");
  assert.deepEqual(locate("document.BANK", null, null, app, schema), { task: "materials", requirementCode: "BANK" });
});

test("related-person slots with a generic role apply to every person; role-specific slots only to that role", async () => {
  const { personSlots } = await import("./people.ts");
  const base = schemaFixture();
  const value = { ...base, materials: [...base.materials,
    { requirementCode: "IDENTITY_ANY", subjectRole: "ANY_RELATED", documentTypes: ["PASSPORT"], required: true },
    { requirementCode: "SIGNATORY_POA", subjectRole: "SIGNATORY", documentTypes: ["POA"], required: true }] };
  assert.deepEqual(personSlots(value, ["UBO"]).map((slot) => slot.requirementCode), ["IDENTITY", "IDENTITY_ANY"]);
  assert.deepEqual(personSlots(value, ["SIGNATORY"]).map((slot) => slot.requirementCode), ["IDENTITY", "IDENTITY_ANY", "SIGNATORY_POA"]);
});

test("people drawer maps 422 relation and person field errors onto its controls", async () => {
  const { drawerErrors } = await import("./people.ts");
  const text = (message: string) => "T:" + message;
  const result = drawerErrors({ "relation.ownershipPercentage": "UBO_OWNERSHIP_EXCEEDS_100/PROVIDE_FIELD", "relation.effectiveFrom": "FIELD_MISSING",
    "relation.uboBasis": "FIELD_MISSING", "person.individual.givenName": "FIELD_MISSING", "people[31].identifier.identifierValue": "IDENTIFIER_FORMAT_INVALID",
    "dirtyFields": "x" }, text);
  assert.deepEqual(result.relation, { share: "T:UBO_OWNERSHIP_EXCEEDS_100/PROVIDE_FIELD", roles: "T:FIELD_MISSING", basis: "T:FIELD_MISSING" });
  assert.deepEqual(result.person, { "individual.givenName": "T:FIELD_MISSING", "identifier.identifierValue": "T:IDENTIFIER_FORMAT_INVALID" });
  assert.equal(result.mapped, true);
  assert.equal(drawerErrors({}, text).mapped, false);
});
