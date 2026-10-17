import { readCapabilities } from "./developer.ts";
import type { Capability } from "./developer.ts";
/** 开始页“我的工作区”中的一项；clientId 由服务端从成员关系得出，浏览器只回传 memberId。 */
export type WorkspaceItem = {
  memberId: string;
  clientId: string;
  /** CCT 摘要缺失时为空，只降级该项显示。 */
  clientNo: string | null;
  displayName: string | null;
  clientType: string | null;
  state: "ACCESSIBLE" | "RESTRICTED";
  reasonText: string | null;
  current: boolean;
};
export type Workspaces = { status: "AVAILABLE" | "UNAVAILABLE"; items: WorkspaceItem[] };
/** 工作区首页；只含已批准资料摘要，不含收款、余额或邀请信息。 */
export type WorkspaceHome = {
  /** CCT 未返回已批准资料时只有 clientId，其余为空。 */
  workspace: { clientId: string; clientNo: string | null; displayName: string | null; clientType: string | null; clientStatus: string | null };
  profile: { country: string | null; businessModelCode: string | null; approvedAt: string | null };
  notice: string | null;
  /** 开发者与团队权限能力；旧版 BFF 不返回时为空。 */
  capabilities: Capability[];
};

const id = /^[1-9][0-9]{0,18}$/;
const unavailable: Workspaces = { status: "UNAVAILABLE", items: [] };

/**
 * 校验 C01 返回的工作区区域
 * <li>成员或 Client ID、状态格式不符时整体按“暂时无法读取”处理，不展示部分可疑数据。</li>
 * <li>单项缺少客户编号、名称或类型（CCT 摘要缺失）时只降级该项，不影响其他工作区。</li>
 * <li>缺少该字段（旧版 BFF）同样视为不可用，不影响入驻申请列表。</li>
 */
export function readWorkspaces(value: unknown): Workspaces {
  if (!value || typeof value !== "object") return unavailable;
  const v = value as { status?: unknown; items?: unknown };
  if (v.status === "UNAVAILABLE") return unavailable;
  if (v.status !== "AVAILABLE" || !Array.isArray(v.items)) return unavailable;
  const items: WorkspaceItem[] = [];
  for (const raw of v.items) {
    const i = raw as Partial<WorkspaceItem> | null;
    if (!i || typeof i !== "object" || !id.test(String(i.memberId)) || !id.test(String(i.clientId))
      || (i.state !== "ACCESSIBLE" && i.state !== "RESTRICTED")
      || !(i.reasonText === null || i.reasonText === undefined || typeof i.reasonText === "string")) {
      return unavailable;
    }
    items.push({ memberId: i.memberId!, clientId: i.clientId!, clientNo: optionalText(i.clientNo), displayName: optionalText(i.displayName),
      clientType: optionalText(i.clientType), state: i.state, reasonText: i.reasonText ?? null, current: i.current === true });
  }
  return { status: "AVAILABLE", items };
}

function optionalText(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

/** 工作区显示名称：CCT 已批准名称，读取不到时用客户编号，都没有时显示固定提示。 */
export function workspaceName(item: { displayName: string | null; clientNo: string | null }): string {
  return item.displayName?.trim() || item.clientNo || "名称暂不可用";
}

/** 名称下方的说明行：类型 · 编号，缺失的部分省略。 */
export function workspaceSubtitle(item: { clientType: string | null; clientNo: string | null }): string {
  return [item.clientType ? clientTypeLabels[item.clientType] ?? item.clientType : null, item.clientNo].filter(Boolean).join(" · ");
}

export const clientTypeLabels: Record<string, string> = { INDIVIDUAL: "个人", ORGANIZATION: "组织" };
export const clientStatusLabels: Record<string, string> = { ACTIVE: "已启用", PENDING: "开通中", SUSPENDED: "已暂停", TERMINATED: "已终止" };
export const businessModelLabels: Record<string, string> = {
  PHYSICAL_GOODS: "实物商品", DIGITAL_GOODS: "数字商品", SERVICES: "服务", SUBSCRIPTION: "订阅", MARKETPLACE: "平台 / 市场", OTHER: "其他",
};
export const homeNotices: Record<string, string> = { PAYMENTS_NOT_YET_ENABLED: "进入工作区不代表已开通收款。" };

const fallbackTexts: Record<string, string> = {
  TENANT_TERMINATED: "工作区已终止",
  TENANT_SUSPENDED: "工作区已暂停",
  TENANT_SECURITY_SUSPENDED: "工作区暂时不可访问",
  MEMBER_INACTIVE: "您的成员资格已失效",
};
/** 刷新降级原因的公开文案；未知原因码不透传。 */
export function fallbackText(reason: string | null): string | null {
  if (!reason) return null;
  return "已退出工作区：" + (fallbackTexts[reason] ?? "工作区暂时不可访问") + "。";
}

/** 审核通过的申请对应的可进入工作区；没有可访问项时返回空，页面继续显示“正在开通”。 */
export function enterableWorkspace(clientId: string | undefined, workspaces: Workspaces | null): WorkspaceItem | null {
  if (!clientId || !workspaces || workspaces.status !== "AVAILABLE") return null;
  return workspaces.items.find((item) => item.clientId === clientId && item.state === "ACCESSIBLE") ?? null;
}

/** 首页响应的最小校验；只要求 clientId，资料缺失时按项降级显示；不合格时按读取失败处理。 */
export function readHome(value: unknown): WorkspaceHome | null {
  const v = value as WorkspaceHome | null;
  if (!v || typeof v !== "object" || !v.workspace || !v.profile) return null;
  const w = v.workspace;
  if (!id.test(String(w.clientId))) return null;
  return {
    workspace: { clientId: w.clientId, clientNo: optionalText(w.clientNo), displayName: optionalText(w.displayName),
      clientType: optionalText(w.clientType), clientStatus: optionalText(w.clientStatus) },
    profile: {
      country: typeof v.profile.country === "string" ? v.profile.country : null,
      businessModelCode: typeof v.profile.businessModelCode === "string" ? v.profile.businessModelCode : null,
      approvedAt: typeof v.profile.approvedAt === "string" ? v.profile.approvedAt : null,
    },
    notice: typeof v.notice === "string" ? v.notice : null,
    capabilities: readCapabilities((value as { capabilities?: unknown }).capabilities),
  };
}
