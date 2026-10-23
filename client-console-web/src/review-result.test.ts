import { test } from "node:test";
import assert from "node:assert/strict";
import { resultBanner } from "./onboarding.ts";

test("returned shows section, public note and resubmission hint", () => {
  const banner = resultBanner({ status: "RETURNED", reviewStatus: "AVAILABLE",
    review: { outcome: "RETURNED", returnSection: "BUSINESS", publicNote: "请补充经营地址", decidedAt: "2026-10-13T00:00:00.000000Z" } });
  assert.equal(banner?.tone, "warning");
  assert.equal(banner?.anchor, "business");
  assert.deepEqual(banner?.lines, ["Needs changes: Business details", "请补充经营地址", "After making the changes, resubmit for review at the bottom of the page."]);
});

test("approved never claims the workspace is ready", () => {
  const banner = resultBanner({ status: "APPROVED", reviewStatus: "AVAILABLE",
    review: { outcome: "APPROVED", returnSection: null, publicNote: null, decidedAt: null } });
  assert.equal(banner?.title, "Application approved");
  assert.ok(banner?.lines.some(line => line.includes("being set up")));
});

test("rejected without note uses a safe generic message", () => {
  const banner = resultBanner({ status: "REJECTED", reviewStatus: "AVAILABLE",
    review: { outcome: "REJECTED", returnSection: null, publicNote: "  ", decidedAt: null } });
  assert.equal(banner?.lines[0], "If you have any questions, please contact customer support.");
});

test("unavailable or mismatched result falls back without inventing a reason", () => {
  for (const app of [
    { status: "RETURNED", reviewStatus: "UNAVAILABLE" as const, review: null },
    { status: "RETURNED", reviewStatus: "AVAILABLE" as const, review: { outcome: "REJECTED" as const, returnSection: null, publicNote: "旧结果", decidedAt: null } },
  ]) {
    const banner = resultBanner(app);
    assert.equal(banner?.anchor, null);
    assert.ok(banner?.lines.includes("We can't load the review notes right now. Please refresh the page later."));
    assert.ok(!banner?.lines.includes("旧结果"));
  }
});

test("in-progress states have no result banner", () => {
  for (const status of ["DRAFT", "SUBMITTED", "UNDER_REVIEW"]) {
    assert.equal(resultBanner({ status, reviewStatus: "NONE", review: null }), null);
  }
});
