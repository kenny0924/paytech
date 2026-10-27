import { test } from "node:test";
import assert from "node:assert/strict";
import { dateRules, documentDateIssues, today } from "./documentDates.ts";

const on = "2026-10-27";

test("dates are optional unless the slot has an age limit or requires expiry", () => {
  assert.deepEqual(dateRules({}), { issueRequired: false, expiryRequired: false });
  assert.deepEqual(dateRules({ maxAgeDays: 180 }), { issueRequired: true, expiryRequired: false });
  assert.deepEqual(dateRules({ expiryRequired: true }), { issueRequired: false, expiryRequired: true });
  assert.deepEqual(documentDateIssues({}, "", "", on), {});
  assert.deepEqual(documentDateIssues({ maxAgeDays: 180, expiryRequired: true }, "", "", on),
    { issueDate: { key: "error.field_missing" }, expiryDate: { key: "error.field_missing" } });
});

test("issue date cannot be in the future and must fall within the age limit", () => {
  assert.deepEqual(documentDateIssues({}, "2026-10-28", "", on).issueDate, { key: "error.issue_future" });
  assert.deepEqual(documentDateIssues({ maxAgeDays: 180 }, "2026-03-02", "", on).issueDate,
    { key: "error.document_too_old", params: { months: 6 } });
  assert.equal(documentDateIssues({ maxAgeDays: 180 }, "2026-05-01", "", on).issueDate, undefined);
  assert.deepEqual(documentDateIssues({}, "2026-02-30", "", on).issueDate, { key: "error.format" });
});

test("expiry must be in the future and not before the issue date", () => {
  assert.deepEqual(documentDateIssues({}, "", "2026-10-27", on).expiryDate, { key: "error.expired" });
  assert.equal(documentDateIssues({}, "", "2026-10-28", on).expiryDate, undefined);
  assert.deepEqual(documentDateIssues({}, "2027-01-01", "2026-12-01", "2027-02-01").expiryDate, { key: "error.expired" });
  assert.deepEqual(documentDateIssues({}, "2026-10-01", "2026-09-30", "2026-09-01").issueDate, { key: "error.issue_future" });
  assert.match(today(new Date(2026, 0, 5)), /^2026-01-05$/);
});
