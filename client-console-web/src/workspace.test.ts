import { test } from "node:test";
import assert from "node:assert/strict";
import { enterableWorkspace, fallbackText, readHome, readWorkspaces, workspaceName } from "./workspace.ts";
import { resultBanner } from "./onboarding.ts";

const item = { memberId: "11", clientId: "21", clientNo: "CL-21", displayName: "Example Trading LLC", clientType: "ORGANIZATION",
  state: "ACCESSIBLE", reasonText: null, current: false };

test("workspace list keeps accessible and restricted items and falls back to client number", () => {
  const list = readWorkspaces({ status: "AVAILABLE", items: [item,
    { ...item, memberId: "12", clientId: "22", clientNo: "CL-22", displayName: null, state: "RESTRICTED", reasonText: "工作区已暂停" }] });
  assert.equal(list.status, "AVAILABLE");
  assert.equal(list.items.length, 2);
  assert.equal(workspaceName(list.items[0]), "Example Trading LLC");
  assert.equal(workspaceName(list.items[1]), "CL-22");
  assert.equal(list.items[1].reasonText, "工作区已暂停");
});

test("empty, unavailable, missing or malformed workspaces never break the application list", () => {
  assert.deepEqual(readWorkspaces({ status: "AVAILABLE", items: [] }), { status: "AVAILABLE", items: [] });
  for (const value of [undefined, null, { status: "UNAVAILABLE", items: [item] }, { status: "AVAILABLE" },
    { status: "AVAILABLE", items: [{ ...item, memberId: "x" }] }, { status: "AVAILABLE", items: [{ ...item, state: "OPEN" }] }]) {
    assert.deepEqual(readWorkspaces(value), { status: "UNAVAILABLE", items: [] });
  }
});

test("fallback reason maps to public text and unknown codes stay generic", () => {
  assert.equal(fallbackText(null), null);
  assert.equal(fallbackText("TENANT_SUSPENDED"), "已退出工作区：工作区已暂停。");
  assert.equal(fallbackText("MEMBER_INACTIVE"), "已退出工作区：您的成员资格已失效。");
  assert.equal(fallbackText("SOMETHING_INTERNAL"), "已退出工作区：工作区暂时不可访问。");
});

test("approved banner offers entry only for an accessible workspace of the same client", () => {
  const approved = { status: "APPROVED", reviewStatus: "NONE" as const, review: null, clientId: "21" };
  const ready = readWorkspaces({ status: "AVAILABLE", items: [item] });
  const enter = resultBanner(approved, ready);
  assert.equal(enter?.enterMemberId, "11");
  assert.ok(enter?.lines.includes("客户工作区已开通，可以进入。"));
  for (const workspaces of [null, readWorkspaces({ status: "AVAILABLE", items: [{ ...item, state: "RESTRICTED" }] }),
    readWorkspaces({ status: "AVAILABLE", items: [{ ...item, clientId: "99" }] }), readWorkspaces(undefined)]) {
    const banner = resultBanner(approved, workspaces);
    assert.equal(banner?.enterMemberId, null);
    assert.ok(banner?.lines.some((line) => line.includes("正在开通")));
  }
  assert.equal(enterableWorkspace(undefined, ready), null);
});

test("home response is validated before rendering", () => {
  const home = readHome({ workspace: { clientId: "21", clientNo: "CL-21", displayName: null, clientType: "INDIVIDUAL", clientStatus: "ACTIVE" },
    profile: { country: "AE", businessModelCode: "SERVICES", approvedAt: "2026-10-16T08:00:00.000000Z" }, notice: "PAYMENTS_NOT_YET_ENABLED" });
  assert.equal(home?.workspace.displayName, null);
  assert.equal(home?.profile.country, "AE");
  assert.equal(readHome({ workspace: { clientId: "bad" }, profile: {} }), null);
  assert.equal(readHome(null), null);
});
