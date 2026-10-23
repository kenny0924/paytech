import { readEmailLink } from "./auth.ts";
import type { EmailLink } from "./auth.ts";
import { translator } from "./i18n/index.ts";

const tc = translator("common"), ti = translator("invitation");

/** 接受邀请页的固定站内路径；登录后的 return-to 只允许这一条。 */
export const invitationAcceptPath = "/invitations/accept";
export const defaultStartPath = "/onboarding";
const storageKey = "paytech-client-pending-invitation";

type SessionStore = Pick<Storage, "getItem" | "setItem" | "removeItem">;

/**
 * 启动时读取邀请链接（必须在清理 URL 之前调用）
 * <li>只有接受页路径上的合法片段才保存；保存到 sessionStorage，供登录 / 注册往返后继续。</li>
 * <li>浏览器存储不可用时返回链接本身，本页内存中仍可继续。</li>
 */
export function captureInvitationLink(pathname: string, fragment: string, store: SessionStore | null): EmailLink | null {
  if (pathname !== invitationAcceptPath) return null;
  const link = readEmailLink(fragment);
  if (!link) return null;
  try {
    store?.setItem(storageKey, JSON.stringify(link));
  } catch {
    // 存储被禁用时只保留内存中的链接。
  }
  return link;
}

/** 读取待处理邀请；内容被篡改或格式不符时视为没有。 */
export function readStoredInvitation(store: SessionStore | null): EmailLink | null {
  let raw: string | null = null;
  try {
    raw = store?.getItem(storageKey) ?? null;
  } catch {
    return null;
  }
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<EmailLink> | null;
    if (!value || typeof value.challengeNo !== "string" || typeof value.secret !== "string") return null;
    return readEmailLink("#challengeNo=" + encodeURIComponent(value.challengeNo) + "&secret=" + encodeURIComponent(value.secret));
  } catch {
    return null;
  }
}

/** 邀请已接受、已失效或无效时清除，避免之后登录被带回接受页。 */
export function clearStoredInvitation(store: SessionStore | null): void {
  try {
    store?.removeItem(storageKey);
  } catch {
    // 忽略存储异常。
  }
}

/** 登录成功后的去向：有待处理邀请时回到接受页，否则回开始页。 */
export function afterLoginPath(store: SessionStore | null): string {
  return readStoredInvitation(store) ? invitationAcceptPath : defaultStartPath;
}

export type InvitationPreview = { emailMasked: string; roleCode: string; status: string; expiresAt: string | null };
export type InvitationDetails = InvitationPreview & {
  emailMatches: boolean; clientId: string | null; workspaceName: string | null; clientNo: string | null; clientType: string | null;
};
export type InvitationAcceptance = { outcome: "JOINED" | "ALREADY_MEMBER"; tenantId: string; memberId: string; clientId: string };

const text = (value: unknown): string | null => (typeof value === "string" && value.trim() ? value : null);

/** 预览（公开）响应校验。 */
export function readPreview(value: unknown): InvitationPreview | null {
  const v = value as Record<string, unknown> | null;
  if (!v || typeof v !== "object" || typeof v.emailMasked !== "string" || typeof v.roleCode !== "string" || typeof v.status !== "string") return null;
  return { emailMasked: v.emailMasked, roleCode: v.roleCode, status: v.status, expiresAt: text(v.expiresAt) };
}

/** 详情（账号上下文）响应校验；工作区名称与编号可能为空。 */
export function readDetails(value: unknown): InvitationDetails | null {
  const preview = readPreview(value);
  const v = value as Record<string, unknown> | null;
  if (!preview || typeof v?.emailMatches !== "boolean") return null;
  return {
    ...preview, emailMatches: v.emailMatches, clientId: text(v.clientId), workspaceName: text(v.workspaceName),
    clientNo: text(v.clientNo), clientType: text(v.clientType),
  };
}

export function readAcceptance(value: unknown): InvitationAcceptance | null {
  const v = value as Record<string, unknown> | null;
  if (!v || (v.outcome !== "JOINED" && v.outcome !== "ALREADY_MEMBER")) return null;
  return { outcome: v.outcome, tenantId: String(v.tenantId ?? ""), memberId: String(v.memberId ?? ""), clientId: String(v.clientId ?? "") };
}

/** 详情页显示的工作区名称：名称 → 客户编号 → 固定提示。 */
export function invitationWorkspaceName(details: Pick<InvitationDetails, "workspaceName" | "clientNo">): string {
  return details.workspaceName ?? details.clientNo ?? tc("name_unavailable");
}

/** 只有待接受与已接受（同一账号再次打开，幂等返回已加入）可以继续接受。 */
export function invitationOpen(status: string): boolean {
  return status === "PENDING" || status === "ACCEPTED";
}

export type InvitationProblem = "invalid" | "closed" | "mismatch" | "unavailable" | "account-context" | null;
/** 错误码到页面状态；其他错误按通用失败处理（可重试）。 */
export function invitationProblem(code: string): InvitationProblem {
  switch (code) {
    case "CLIENT_INVITATION_INVALID": return "invalid";
    case "CLIENT_INVITATION_CLOSED": return "closed";
    case "CLIENT_INVITATION_EMAIL_MISMATCH": return "mismatch";
    case "CLIENT_WORKSPACE_UNAVAILABLE": return "unavailable";
    case "CLIENT_ACCOUNT_CONTEXT_REQUIRED": return "account-context";
    default: return null;
  }
}

/** 页面状态对应的说明文案。 */
export function invitationProblemText(problem: "invalid" | "closed" | "unavailable" | "missing"): string {
  return ti("problem." + problem);
}
