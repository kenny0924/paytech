import { test } from "node:test";
import assert from "node:assert/strict";
import { enterableWorkspace, fallbackText, readHome, readWorkspaces, workspaceName, workspaceSubtitle } from "./workspace.ts";

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

test("an item missing CCT summary degrades alone instead of hiding the whole list", () => {
  const list = readWorkspaces({ status: "AVAILABLE", items: [item,
    { ...item, memberId: "13", clientId: "23", clientNo: null, displayName: null, clientType: null },
    { ...item, memberId: "14", clientId: "24", clientNo: "CL-24", displayName: null, clientType: undefined }] });
  assert.equal(list.status, "AVAILABLE");
  assert.equal(list.items.length, 3);
  assert.equal(workspaceName(list.items[1]), "Name unavailable");
  assert.equal(workspaceSubtitle(list.items[1]), "");
  assert.equal(workspaceName(list.items[2]), "CL-24");
  assert.equal(workspaceSubtitle(list.items[2]), "CL-24");
  assert.equal(workspaceSubtitle(list.items[0]), "Organization · CL-21");
});

test("fallback reason maps to public text and unknown codes stay generic", () => {
  assert.equal(fallbackText(null), null);
  assert.equal(fallbackText("TENANT_SUSPENDED"), "You've left the workspace: it has been suspended.");
  assert.equal(fallbackText("MEMBER_INACTIVE"), "You've left the workspace: your membership is no longer active.");
  assert.equal(fallbackText("SOMETHING_INTERNAL"), "You've left the workspace: it is temporarily unavailable.");
});

test("approved result offers entry only for an accessible workspace of the same client", () => {
  const ready = readWorkspaces({ status: "AVAILABLE", items: [item] });
  assert.equal(enterableWorkspace("21", ready)?.memberId, "11");
  for (const workspaces of [null, readWorkspaces({ status: "AVAILABLE", items: [{ ...item, state: "RESTRICTED" }] }),
    readWorkspaces({ status: "AVAILABLE", items: [{ ...item, clientId: "99" }] }), readWorkspaces(undefined)]) {
    assert.equal(enterableWorkspace("21", workspaces), null);
  }
  assert.equal(enterableWorkspace(undefined, ready), null);
});

test("home response is validated before rendering", () => {
  const home = readHome({ workspace: { clientId: "21", clientNo: "CL-21", displayName: null, clientType: "INDIVIDUAL", clientStatus: "ACTIVE" },
    profile: { country: "AE", businessModelCode: "SERVICES", approvedAt: "2026-10-16T08:00:00.000000Z" }, notice: "PAYMENTS_NOT_YET_ENABLED" });
  assert.equal(home?.workspace.displayName, null);
  assert.equal(home?.profile.country, "AE");
  assert.equal(readHome({ workspace: { clientId: "bad" }, profile: {} }), null);
  const partial = readHome({ workspace: { clientId: "21", clientNo: null, displayName: null, clientType: null, clientStatus: null }, profile: {} });
  assert.equal(partial?.workspace.clientNo, null);
  assert.equal(partial && workspaceName(partial.workspace), "Name unavailable");
  assert.equal(readHome(null), null);
});
