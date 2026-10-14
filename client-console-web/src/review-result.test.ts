import { test } from "node:test";
import assert from "node:assert/strict";
import { resultBanner } from "./onboarding.ts";

test("returned shows section, public note and resubmission hint", () => {
  const banner = resultBanner({ status: "RETURNED", reviewStatus: "AVAILABLE",
    review: { outcome: "RETURNED", returnSection: "BUSINESS", publicNote: "请补充经营地址", decidedAt: "2026-10-13T00:00:00.000000Z" } });
  assert.equal(banner?.tone, "warning");
  assert.equal(banner?.anchor, "business");
  assert.deepEqual(banner?.lines, ["需要修改：经营资料", "请补充经营地址", "修改完成后，请在页面底部重新提交审核。"]);
});

test("approved never claims the workspace is ready", () => {
  const banner = resultBanner({ status: "APPROVED", reviewStatus: "AVAILABLE",
    review: { outcome: "APPROVED", returnSection: null, publicNote: null, decidedAt: null } });
  assert.equal(banner?.title, "审核已通过");
  assert.ok(banner?.lines.some(line => line.includes("正在开通")));
});

test("rejected without note uses a safe generic message", () => {
  const banner = resultBanner({ status: "REJECTED", reviewStatus: "AVAILABLE",
    review: { outcome: "REJECTED", returnSection: null, publicNote: "  ", decidedAt: null } });
  assert.equal(banner?.lines[0], "如有疑问，请联系客服。");
});

test("unavailable or mismatched result falls back without inventing a reason", () => {
  for (const app of [
    { status: "RETURNED", reviewStatus: "UNAVAILABLE" as const, review: null },
    { status: "RETURNED", reviewStatus: "AVAILABLE" as const, review: { outcome: "REJECTED" as const, returnSection: null, publicNote: "旧结果", decidedAt: null } },
  ]) {
    const banner = resultBanner(app);
    assert.equal(banner?.anchor, null);
    assert.ok(banner?.lines.includes("审核说明暂时无法读取，请稍后刷新页面。"));
    assert.ok(!banner?.lines.includes("旧结果"));
  }
});

test("in-progress states have no result banner", () => {
  for (const status of ["DRAFT", "SUBMITTED", "UNDER_REVIEW"]) {
    assert.equal(resultBanner({ status, reviewStatus: "NONE", review: null }), null);
  }
});
