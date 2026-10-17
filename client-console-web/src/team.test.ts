import { test } from "node:test";
import assert from "node:assert/strict";
import {
  assignableRoles, canOperateMember, emailFailedText, expiryText, hasNextPage, invitationActions, inviteFormIssues, readInvitation,
  readInvitationPage, readMemberPage, roleLabel, teamAccess, teamErrorText, teamQuery,
} from "./team.ts";
import type { TeamMember } from "./team.ts";
import { readCapabilities } from "./developer.ts";

const member: TeamMember = { memberId: "31", emailMasked: "b***@example.test", memberType: "ADMIN", roleCode: "TENANT_ADMIN",
  status: "ACTIVE", joinedAt: "2026-10-17T00:00:00Z", self: false };
const invitation = { invitationId: "41", emailMasked: "d***@example.test", roleCode: "TENANT_VIEWER", status: "PENDING",
  expiresAt: "2026-10-24T00:00:00Z", invitedByEmailMasked: "a***@example.test", createdAt: "2026-10-17T00:00:00Z", emailDelivery: null };

test("capabilities decide team menu, member list and management visibility", () => {
  assert.deepEqual(teamAccess(readCapabilities([])), { visible: false, canManage: false });
  assert.deepEqual(teamAccess(readCapabilities(["TEAM_READ", "API_CREDENTIAL_READ"])), { visible: true, canManage: false });
  assert.deepEqual(teamAccess(readCapabilities(["TEAM_READ", "TEAM_MANAGE"])), { visible: true, canManage: true });
  assert.deepEqual(teamAccess(readCapabilities(["TEAM_MANAGE"])), { visible: true, canManage: true });
});

test("role labels cover the four workspace roles and only three are assignable", () => {
  assert.deepEqual(["TENANT_OWNER", "TENANT_ADMIN", "TENANT_DEVELOPER", "TENANT_VIEWER"].map(roleLabel), ["所有者", "管理员", "开发者", "只读成员"]);
  assert.equal(roleLabel("TENANT_SECRET"), "—");
  assert.deepEqual(assignableRoles.map((role) => role.code), ["TENANT_ADMIN", "TENANT_DEVELOPER", "TENANT_VIEWER"]);
  assert.equal(assignableRoles[0].description, "管理员可管理团队、API 凭证与 Webhook");
});

test("owner and self rows never show actions and read-only members see none", () => {
  assert.equal(canOperateMember(member, true), true);
  assert.equal(canOperateMember(member, false), false);
  assert.equal(canOperateMember({ ...member, memberType: "OWNER", roleCode: "TENANT_OWNER" }, true), false);
  assert.equal(canOperateMember({ ...member, self: true }, true), false);
  const inv = readInvitation(invitation)!;
  assert.deepEqual(invitationActions(inv, true), { resend: true, revoke: true });
  assert.deepEqual(invitationActions({ ...inv, status: "EXPIRED" }, true), { resend: true, revoke: false });
  assert.deepEqual(invitationActions({ ...inv, status: "ACCEPTED" }, true), { resend: false, revoke: false });
  assert.deepEqual(invitationActions(inv, false), { resend: false, revoke: false });
});

test("member and invitation pages are validated as a whole", () => {
  const page = readMemberPage({ items: [member, { ...member, memberId: "32", self: "yes" }], page: 1, size: 20, total: 2 });
  assert.equal(page?.items.length, 2);
  assert.equal(page?.items[1].self, false);
  assert.equal(readMemberPage({ items: [{ ...member, memberId: "abc" }], page: 1, size: 20, total: 1 }), null);
  assert.equal(readMemberPage({ items: [{ ...member, emailMasked: undefined }] }), null);
  assert.equal(readMemberPage(null), null);
  const invitations = readInvitationPage({ items: [invitation], page: 2, size: 20, total: 41 });
  assert.equal(invitations?.items[0].emailDelivery, null);
  assert.equal(invitations && hasNextPage(invitations), true);
  assert.equal(hasNextPage({ items: [], page: 3, size: 20, total: 41 }), false);
  assert.equal(readInvitation({ ...invitation, emailDelivery: "FAILED" })?.emailDelivery, "FAILED");
  assert.equal(readInvitation({ ...invitation, emailDelivery: "BOUNCED" })?.emailDelivery, null);
  assert.equal(readInvitation({ ...invitation, roleCode: 7 }), null);
});

test("invite form checks email format and role, and queries carry status and paging", () => {
  assert.deepEqual(inviteFormIssues({ email: " d@example.test ", roleCode: "TENANT_ADMIN" }), {});
  assert.deepEqual(Object.keys(inviteFormIssues({ email: "", roleCode: "TENANT_OWNER" })).sort(), ["email", "roleCode"]);
  assert.equal(inviteFormIssues({ email: "not-an-email", roleCode: "TENANT_VIEWER" }).email, "邮箱格式不正确");
  assert.equal(teamQuery(2, "EXPIRED"), "status=EXPIRED&page=2&size=20");
  assert.equal(teamQuery(0), "page=1&size=20");
  assert.equal(emailFailedText, "邀请已创建，邮件未发出，可稍后重发");
  assert.match(teamErrorText("CLIENT_INVITATION_DUPLICATE", "x"), /重发/);
  assert.equal(teamErrorText("UNKNOWN", "服务端说明"), "服务端说明");
});

test("expiry text counts remaining days and marks past expiry", () => {
  const now = Date.parse("2026-10-17T00:00:00Z");
  assert.equal(expiryText("2026-10-24T00:00:00Z", now), "7 天后到期");
  assert.equal(expiryText("2026-10-17T05:30:00Z", now), "5 小时后到期");
  assert.equal(expiryText("2026-10-16T00:00:00Z", now), "已过期");
  assert.equal(expiryText(null, now), "—");
});

