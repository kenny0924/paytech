import { test } from "node:test";
import assert from "node:assert/strict";
import { applicationFixture, schemaFixture } from "./fixture.test-data.ts";
import { daysLeft, fieldRequirementIssues, locate, phaseOf, taskSummaries } from "./progress.ts";
import { errorText } from "./draft.ts";

const schema = schemaFixture();
const requirement = (requirementKey: string, extra: Record<string, unknown> = {}) => ({
  requirementKind: "FIELD", requirementKey, subjectPartyId: "401", status: "CURRENTLY_DUE", awaitingActionFrom: "CLIENT",
  errorCode: "FIELD_MISSING", remediationCode: "PROVIDE_FIELD", dueTime: null, ...extra,
});

test("requirement keys locate tasks, fields, people, document slots and declarations", () => {
  const app = applicationFixture({ people: [{ partyId: "402", editable: true, values: {} }] });
  assert.deepEqual(locate("organization.legalName", "401", "FIELD", app, schema), { task: "profile", fieldKey: "organization.legalName" });
  assert.deepEqual(locate("address.postalCode", "401", "FIELD", app, schema), { task: "address", fieldKey: "address" });
  assert.equal(locate("contact.email", "401", "FIELD", app, schema).task, "address");
  assert.deepEqual(locate("document.BANK", "401", "DOCUMENT", app, schema), { task: "materials", requirementCode: "BANK" });
  assert.deepEqual(locate("document.IDENTITY", "402", "DOCUMENT", app, schema), { task: "people", personId: "402", requirementCode: "IDENTITY" });
  assert.equal(locate("relation.ubo.min_one", null, "RELATION", app, schema).task, "people");
  assert.deepEqual(locate("declaration.INFORMATION_TRUE", null, "DECLARATION", app, schema), { task: "declarations", declarationType: "INFORMATION_TRUE" });
});

test("task state: not started, in progress, complete, needs attention; review locked until all complete", () => {
  const empty = taskSummaries(applicationFixture({ requirements: [requirement("organization.legalName")] }), schema);
  assert.equal(empty.find((item) => item.key === "profile")?.state, "todo");
  assert.equal(empty.find((item) => item.key === "review")?.state, "locked");
  const partial = taskSummaries(applicationFixture({ values: { organization: { legalName: "Noor" } },
    requirements: [requirement("identifier.SA_CR_NUMBER", { requirementKind: "IDENTIFIER" })] }), schema);
  assert.equal(partial.find((item) => item.key === "profile")?.state, "active");
  const invalid = taskSummaries(applicationFixture({ values: { organization: { legalName: "Noor" } },
    requirements: [requirement("identifier.SA_CR_NUMBER", { requirementKind: "IDENTIFIER", errorCode: "IDENTIFIER_FORMAT_INVALID" })] }), schema);
  assert.equal(invalid.find((item) => item.key === "profile")?.state, "attention");
  const done = taskSummaries(applicationFixture({ values: { organization: { legalName: "Noor" } } }), schema);
  assert.equal(done.find((item) => item.key === "profile")?.state, "done");
});

test("field requirement issues: only open, non-missing codes of the primary subject on the given paths", () => {
  const app = applicationFixture({ requirements: [
    requirement("business.currency", { errorCode: "FIELD_VALUE_NOT_IN_CATALOG" }),
    requirement("address.stateOrProvince", { errorCode: "FIELD_VALUE_NOT_IN_CATALOG" }),
    requirement("organization.legalName"),
    requirement("organization.tradeName", { errorCode: "FIELD_FORMAT_INVALID", status: "SATISFIED" }),
    requirement("individual.nationality", { errorCode: "FIELD_VALUE_NOT_IN_CATALOG", subjectPartyId: "402" }),
  ] });
  const paths = new Set(["business.currency", "organization.legalName", "organization.tradeName", "individual.nationality"]);
  assert.deepEqual(fieldRequirementIssues(app, paths), { "business.currency": "FIELD_VALUE_NOT_IN_CATALOG" });
  assert.deepEqual(fieldRequirementIssues(applicationFixture({ requirementsStatus: "UNAVAILABLE",
    requirements: [requirement("business.currency", { errorCode: "FIELD_VALUE_NOT_IN_CATALOG" })] }), paths), {});
});

test("returned errors mark their task as needing attention even without open requirements", () => {
  const app = applicationFixture({ status: "RETURNED", values: { organization: { legalName: "Noor" } } });
  const summaries = taskSummaries(app, schema, [{ requirementKey: "document.BANK", subjectPartyId: "401", code: "DOCUMENT_TOO_OLD", remediation: "UPLOAD_DOCUMENT" }]);
  assert.equal(summaries.find((item) => item.key === "materials")?.state, "attention");
});

test("result phases distinguish expired from withdrawn", () => {
  assert.equal(phaseOf({ status: "UNDER_REVIEW", review: null }), "in_review");
  assert.equal(phaseOf({ status: "WITHDRAWN", review: null }), "withdrawn");
  assert.equal(phaseOf({ status: "WITHDRAWN", review: { caseStatus: "EXPIRED", outcome: "EXPIRED", publicNote: null, decidedAt: null, dueTime: null, errors: [] } }), "expired");
  assert.equal(phaseOf({ status: "RETURNED", review: null }), "returned");
});

test("days left rounds up and is negative after the deadline", () => {
  const now = Date.parse("2026-10-27T00:00:00Z");
  assert.equal(daysLeft("2026-11-10T00:00:00Z", now), 14);
  assert.equal(daysLeft("2026-10-27T05:00:00Z", now), 1);
  assert.equal(daysLeft("2026-10-26T00:00:00Z", now), -1);
  assert.equal(daysLeft(null, now), null);
});

test("error codes use the country-pack text and never fall back to server wording", () => {
  assert.equal(errorText("FIELD_MISSING"), "This field is required.");
  assert.equal(errorText("SOMETHING_NEW"), "This item needs attention.");
});

test("declarations read back from the application; latest record per type wins", async () => {
  const { declared } = await import("./progress.ts");
  const app = applicationFixture({ declarations: [
    { declarationType: "INFORMATION_TRUE", declaredValue: "TRUE", declaredAt: "2026-10-27T00:00:00Z" },
    { declarationType: "INFORMATION_TRUE", declaredValue: "FALSE", declaredAt: "2026-10-27T01:00:00Z" },
    { declarationType: "PLATFORM_TERMS", declaredValue: "TRUE", declaredAt: "2026-10-27T00:00:00Z" }] });
  assert.equal(declared(app, "INFORMATION_TRUE"), false);
  assert.equal(declared(app, "PLATFORM_TERMS"), true);
  assert.equal(declared(applicationFixture({ requirements: [] }), "PLATFORM_TERMS"), true);
});

test("request failures show localized text by code, never the server detail", async () => {
  const { requestErrorText } = await import("./draft.ts");
  const { RequestFailure } = await import("../auth.ts");
  assert.equal(requestErrorText(new RequestFailure(422, "入驻字段不符合约定", "r", { "address.city": "x" }, "INVALID_DRAFT")),
    "Some information couldn't be saved. Check the highlighted fields and try again.");
  assert.equal(requestErrorText(new RequestFailure(422, "入驻字段不符合约定", "r", {}, "INVALID_DRAFT")),
    "This information couldn't be saved. Try again; if it keeps happening, contact support and quote the request ID.");
  assert.equal(requestErrorText(new RequestFailure(429, "限流", "r", {}, "SOMETHING")), "Too many changes at once. Wait a moment and try again.");
  assert.equal(requestErrorText(new RequestFailure(503, "内部", "r", {}, "X")), "The service is temporarily unavailable. Please try again later.");
  assert.equal(requestErrorText(new RequestFailure(409, "冲突", "r", {}, "UNKNOWN_CODE")), "The action didn't complete");
});

test("autosave queue runs saves one at a time and a failure does not block the next save", async () => {
  const { serialQueue } = await import("./draft.ts");
  const enqueue = serialQueue();
  let running = 0, peak = 0;
  const order: string[] = [];
  const job = (name: string, fail = false) => async () => {
    running++; peak = Math.max(peak, running);
    await new Promise((resolve) => setTimeout(resolve, 5));
    order.push(name); running--;
    if (fail) throw new Error(name);
    return name;
  };
  const first = enqueue(job("a", true)), second = enqueue(job("b")), third = enqueue(job("c"));
  await assert.rejects(first);
  assert.equal(await second, "b");
  assert.equal(await third, "c");
  assert.deepEqual(order, ["a", "b", "c"]);
  assert.equal(peak, 1);
});

test("drawer summary labels identifier sub-paths, not raw paths", async () => {
  const { fieldPaths } = await import("./draft.ts");
  const schema = schemaFixture();
  const passport = schema.sections[0].groups[1].fields[2];
  assert.deepEqual(fieldPaths(passport, schema, { "identifier.identifierType": "PASSPORT" }).map((item) => item.path),
    ["identifier.identifierValue", "identifier.issuingCountry", "identifier.expiryDate"]);
});

test("rate-limited saves retry the same request with backoff; other failures surface at once", async () => {
  const { withBackoff } = await import("./draft.ts");
  const { RequestFailure } = await import("../auth.ts");
  const waits: number[] = [];
  const wait = async (ms: number) => { waits.push(ms); };
  let calls = 0;
  assert.equal(await withBackoff(async () => { if (++calls < 3) throw new RequestFailure(429, "x", "", {}, "GATEWAY_CONCURRENCY_LIMITED"); return "ok"; }, [10, 20, 40], wait), "ok");
  assert.deepEqual(waits, [10, 20]);
  calls = 0;
  await assert.rejects(withBackoff(async () => { calls++; throw new RequestFailure(422, "x", "", {}, "INVALID_DRAFT"); }, [10], wait));
  assert.equal(calls, 1);
  await assert.rejects(withBackoff(async () => { throw new RequestFailure(429, "x"); }, [1, 1], wait));
});

test("a failed 422 save maps owner field errors onto the form fields", async () => {
  const { serverIssues } = await import("./draft.ts");
  const { RequestFailure } = await import("../auth.ts");
  const schema = schemaFixture();
  const failure = new RequestFailure(422, "入驻字段不符合约定", "r", { "address.postalCode": "x" }, "INVALID_DRAFT",
    { errors: [{ field: "address.postalCode", message: "x" }, { requirementKey: "organization.legalName", code: "FIELD_FORMAT_INVALID" }] });
  const mapped = serverIssues(failure, schema);
  assert.deepEqual(Object.keys(mapped).sort(), ["address.postalCode", "organization.legalName"]);
  assert.ok(!Object.values(mapped).some((text) => text.includes("入驻")));
});

test("a server-rejected field is isolated: named fields are refused, the rest still save", async () => {
  const { sendIsolating } = await import("./draft.ts");
  const { RequestFailure } = await import("../auth.ts");
  const sent: string[][] = [];
  const reject = (bad: string[], named: boolean) => async (fields: string[]) => {
    sent.push(fields);
    if (fields.some((key) => bad.includes(key)))
      throw new RequestFailure(422, "x", "", named ? Object.fromEntries(bad.filter((key) => fields.includes(key)).map((key) => [key, "bad"])) : {}, "INVALID_DRAFT");
  };
  const map = (failure: unknown) => (failure as { fields: Record<string, string> }).fields;
  assert.deepEqual(await sendIsolating(["address.city", "address.poBox"], reject(["address.poBox"], true), map, "fallback"), { "address.poBox": "bad" });
  assert.deepEqual(sent, [["address.city", "address.poBox"], ["address.city"]]);
  sent.length = 0;
  assert.deepEqual(await sendIsolating(["address.city", "attributes.SA_SHORT_ADDRESS"], reject(["attributes.SA_SHORT_ADDRESS"], false), map, "fallback"),
    { "attributes.SA_SHORT_ADDRESS": "fallback" });
  assert.deepEqual(sent, [["address.city", "attributes.SA_SHORT_ADDRESS"], ["address.city"], ["attributes.SA_SHORT_ADDRESS"]]);
  await assert.rejects(sendIsolating(["a.b"], async () => { throw new RequestFailure(503, "x"); }, map, "fallback"));
});

test("upload registry tracks active transfers and claims", async () => {
  const { createUploadRegistry } = await import("./upload.ts");
  const registry = createUploadRegistry();
  let notified = 0;
  registry.subscribe(() => notified++);
  registry.claim("k1"); registry.setActive("k1", true); registry.setActive("k1", true);
  assert.equal(registry.activeCount(), 1);
  assert.equal(registry.isClaimed("k1"), true);
  registry.release("k1");
  assert.equal(registry.activeCount(), 0);
  assert.equal(registry.isClaimed("k1"), false);
  assert.equal(notified, 2);
});

test("a 422-rejected field does not poison later saves: a valid field edited afterwards saves alone", async () => {
  const { sendIsolating, draftPatch } = await import("./draft.ts");
  const { RequestFailure } = await import("../auth.ts");
  const baseline: Record<string, string> = {}, rejected = new Set<string>(), sent: string[][] = [];
  const server = async (fields: string[]) => {
    sent.push(fields);
    if (fields.includes("address.poBox")) throw new RequestFailure(422, "x", "", { "address.poBox": "bad" }, "INVALID_DRAFT");
    for (const key of fields) baseline[key] = values[key];
  };
  const map = (failure: unknown) => (failure as { fields: Record<string, string> }).fields;
  const values: Record<string, string> = { "address.poBox": "!!" };
  let body = draftPatch(baseline, values, rejected);
  for (const key of Object.keys(await sendIsolating(body.dirtyFields, server, map, "fallback"))) rejected.add(key);
  assert.deepEqual([...rejected], ["address.poBox"]);
  values["address.city"] = "Dubai";
  body = draftPatch(baseline, values, rejected);
  assert.deepEqual(body.dirtyFields, ["address.city"]);
  assert.deepEqual(await sendIsolating(body.dirtyFields, server, map, "fallback"), {});
  assert.equal(baseline["address.city"], "Dubai");
  assert.deepEqual(sent, [["address.poBox"], ["address.city"]]);
});
