import type { Capability } from "./developer.ts";
import { enumLabel } from "./enum-label.ts";
import { formatDate, translator } from "./i18n/index.ts";

const tt = translator("team");

/** 工作区角色；OWNER 固定为 TENANT_OWNER，可邀请或改授的只有后三个。 */
export type RoleCode = "TENANT_OWNER" | "TENANT_ADMIN" | "TENANT_DEVELOPER" | "TENANT_VIEWER";
export type AssignableRole = { code: Exclude<RoleCode, "TENANT_OWNER">; label: string; description: string };

/** 展示名与说明按当前语言读取。 */
function assignable(code: AssignableRole["code"]): AssignableRole {
  return {
    code,
    get label() { return roleLabel(code); },
    get description() { return tt("role_description." + code.toLowerCase()); },
  };
}
export const assignableRoles: readonly AssignableRole[] = [assignable("TENANT_ADMIN"), assignable("TENANT_DEVELOPER"), assignable("TENANT_VIEWER")];
export const defaultInviteRole: AssignableRole["code"] = "TENANT_DEVELOPER";
export const memberStatusLabel = (code: string): string | null => enumLabel("member_status", code);
export const invitationStatusLabel = (code: string): string | null => enumLabel("invitation_status", code);
export const invitationStatuses = ["PENDING", "EXPIRED", "ACCEPTED", "REVOKED"] as const;
export type InvitationStatus = (typeof invitationStatuses)[number];

/** 未知角色码不透传原值，避免展示内部代码。 */
export function roleLabel(code: string | null | undefined): string {
  return enumLabel("role", code) ?? "—";
}

export function isAssignableRole(code: string): code is AssignableRole["code"] {
  return assignableRoles.some((role) => role.code === code);
}

/**
 * 团队页的可见性
 * <li>TEAM_READ：显示“团队”菜单与成员列表；TEAM_MANAGE：邀请区与全部操作。</li>
 * <li>只有 TEAM_MANAGE 时同样可见成员列表（管理权限包含查看）。</li>
 */
export function teamAccess(capabilities: readonly Capability[]): { visible: boolean; canManage: boolean } {
  const canManage = capabilities.includes("TEAM_MANAGE");
  return { visible: canManage || capabilities.includes("TEAM_READ"), canManage };
}

export type TeamMember = {
  memberId: string; emailMasked: string; memberType: string; roleCode: string; status: string; joinedAt: string | null; self: boolean;
};
export type TeamInvitation = {
  invitationId: string; emailMasked: string; roleCode: string; status: string; expiresAt: string | null;
  invitedByEmailMasked: string | null; createdAt: string | null; emailDelivery: "SENT" | "FAILED" | null;
};
export type TeamPage<T> = { items: T[]; page: number; size: number; total: number };

const id = /^[1-9][0-9]{0,18}$/;
const text = (value: unknown): string | null => (typeof value === "string" ? value : null);

/** 成员项校验；ID、脱敏邮箱、类型或角色缺失时视为异常。 */
export function readMember(value: unknown): TeamMember | null {
  const m = value as Record<string, unknown> | null;
  if (!m || typeof m !== "object" || !id.test(String(m.memberId)) || typeof m.emailMasked !== "string"
    || typeof m.memberType !== "string" || typeof m.roleCode !== "string" || typeof m.status !== "string") return null;
  return {
    memberId: String(m.memberId), emailMasked: m.emailMasked, memberType: m.memberType, roleCode: m.roleCode, status: m.status,
    joinedAt: text(m.joinedAt), self: m.self === true,
  };
}

/** 邀请项校验；emailDelivery 只在发起与重发响应中出现。 */
export function readInvitation(value: unknown): TeamInvitation | null {
  const i = value as Record<string, unknown> | null;
  if (!i || typeof i !== "object" || !id.test(String(i.invitationId)) || typeof i.emailMasked !== "string"
    || typeof i.roleCode !== "string" || typeof i.status !== "string") return null;
  return {
    invitationId: String(i.invitationId), emailMasked: i.emailMasked, roleCode: i.roleCode, status: i.status,
    expiresAt: text(i.expiresAt), invitedByEmailMasked: text(i.invitedByEmailMasked), createdAt: text(i.createdAt),
    emailDelivery: i.emailDelivery === "SENT" || i.emailDelivery === "FAILED" ? i.emailDelivery : null,
  };
}

function readPage<T>(value: unknown, readItem: (raw: unknown) => T | null): TeamPage<T> | null {
  const v = value as { items?: unknown; page?: unknown; size?: unknown; total?: unknown } | null;
  if (!v || typeof v !== "object" || !Array.isArray(v.items)) return null;
  const items: T[] = [];
  for (const raw of v.items) {
    const item = readItem(raw);
    if (!item) return null;
    items.push(item);
  }
  const number = (n: unknown, fallback: number) => (typeof n === "number" && Number.isInteger(n) && n >= 0 ? n : fallback);
  return { items, page: Math.max(1, number(v.page, 1)), size: Math.max(1, number(v.size, items.length || 20)), total: number(v.total, items.length) };
}

/** 列表校验：任一项异常时整页视为读取失败，不展示部分可疑数据。 */
export const readMemberPage = (value: unknown) => readPage(value, readMember);
export const readInvitationPage = (value: unknown) => readPage(value, readInvitation);

/** 是否还有下一页。 */
export function hasNextPage(page: TeamPage<unknown>): boolean {
  return page.page * page.size < page.total;
}

export const teamPageSize = 20;
/** 列表查询参数；邀请列表可带状态筛选。 */
export function teamQuery(page: number, status?: InvitationStatus): string {
  const params = new URLSearchParams();
  if (status) params.set("status", status);
  params.set("page", String(Math.max(1, Math.trunc(page))));
  params.set("size", String(teamPageSize));
  return params.toString();
}

/** 成员行是否显示“更改角色 / 移除”：需管理权限，OWNER 与自己所在行不显示。 */
export function canOperateMember(member: TeamMember, canManage: boolean): boolean {
  return canManage && member.memberType !== "OWNER" && member.roleCode !== "TENANT_OWNER" && !member.self && member.status === "ACTIVE";
}

/** 邀请行可用操作：待接受可重发、撤销；已过期只能重发（重新计算 7 天）。 */
export function invitationActions(invitation: TeamInvitation, canManage: boolean): { resend: boolean; revoke: boolean } {
  if (!canManage) return { resend: false, revoke: false };
  return { resend: invitation.status === "PENDING" || invitation.status === "EXPIRED", revoke: invitation.status === "PENDING" };
}

/** 邮箱前端预检：只做格式校验，不做企业域名限制。 */
export function inviteEmailIssue(email: string): string | null {
  const value = email.trim();
  if (!value) return tt("invite.email_required");
  if (value.length > 320) return tt("invite.email_too_long");
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return tt("invite.email_invalid");
  return null;
}

export function inviteFormIssues(form: { email: string; roleCode: string }): Record<string, string> {
  const issues: Record<string, string> = {};
  const email = inviteEmailIssue(form.email);
  if (email) issues.email = email;
  if (!isAssignableRole(form.roleCode)) issues.roleCode = tt("invite.role_required");
  return issues;
}

export const emailFailedText = (): string => tt("invite.email_failed");

/** 团队操作业务错误的就地提示；未登记的错误码使用服务端说明。 */
export const teamErrorCodes = [
  "CLIENT_INVITATION_DUPLICATE", "CLIENT_INVITATION_LIMIT_REACHED", "CLIENT_MEMBER_EXISTS", "CLIENT_MEMBER_PROTECTED",
  "CLIENT_WORKSPACE_UNAVAILABLE", "CLIENT_PERMISSION_DENIED",
] as const;
export function teamErrorText(code: string, fallback: string): string {
  return (teamErrorCodes as readonly string[]).includes(code) ? tt("error." + code.toLowerCase()) : fallback;
}

/** 到期提示：未来按剩余天数，已过期显示“已过期”。 */
export function expiryText(expiresAt: string | null, now = Date.now()): string {
  const time = expiresAt ? Date.parse(expiresAt) : NaN;
  if (!Number.isFinite(time)) return "—";
  const remaining = time - now;
  if (remaining <= 0) return tt("expiry.expired");
  const days = Math.floor(remaining / 86_400_000);
  if (days >= 1) return tt("expiry.days", { days });
  const hours = Math.max(1, Math.floor(remaining / 3_600_000));
  return tt("expiry.hours", { hours });
}

/** 按当前语言显示的本地日期。 */
export function displayDate(value: string | null | undefined): string {
  return formatDate(value);
}
