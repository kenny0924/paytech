import { enumCodes, enumLabel } from "./enum-label.ts";
import { formatDate, translator } from "./i18n/index.ts";

const td = translator("developer");

/** 工作区首页返回的权限能力（开发者与团队）；未授予的项不出现。 */
export type Capability = "API_CREDENTIAL_READ" | "API_CREDENTIAL_MANAGE" | "WEBHOOK_READ" | "WEBHOOK_MANAGE" | "TEAM_READ" | "TEAM_MANAGE";
export const capabilityCodes: readonly Capability[] = [
  "API_CREDENTIAL_READ", "API_CREDENTIAL_MANAGE", "WEBHOOK_READ", "WEBHOOK_MANAGE", "TEAM_READ", "TEAM_MANAGE",
];

/** 只保留已知能力码；未知值不参与菜单判断。 */
export function readCapabilities(value: unknown): Capability[] {
  if (!Array.isArray(value)) return [];
  return capabilityCodes.filter((code) => value.includes(code));
}

export type DeveloperMenuItem = { key: "api-keys" | "webhooks"; path: string };
/** 开发者分组：有读或写权限即显示对应菜单；都没有时整组不显示。 */
export function developerMenu(capabilities: readonly Capability[]): DeveloperMenuItem[] {
  const items: DeveloperMenuItem[] = [];
  if (capabilities.includes("API_CREDENTIAL_READ") || capabilities.includes("API_CREDENTIAL_MANAGE")) {
    items.push({ key: "api-keys", path: "/workspace/developer/api-keys" });
  }
  if (capabilities.includes("WEBHOOK_READ") || capabilities.includes("WEBHOOK_MANAGE")) {
    items.push({ key: "webhooks", path: "/workspace/developer/webhooks" });
  }
  return items;
}

/** API 凭证摘要；列表与详情都不含密钥或校验值。 */
export type Credential = {
  credentialId: string;
  name: string | null;
  keyPrefix: string;
  status: string;
  scopeCodes: string[];
  createdAt: string;
  expiresAt: string;
  rotatedFromId: string | null;
  version: number;
};
export type Scope = { scopeCode: string; displayName: string | null; description: string | null };

export const validityOptions = [30, 90, 180, 365] as const;
export const defaultValidityDays = 365;
/** 旧密钥宽限期；展示名按当前语言读取。 */
export const graceOptions: readonly { minutes: number; readonly label: string }[] = [0, 60, 1440, 10080].map((minutes) => ({
  minutes,
  get label() { return td("grace.m" + minutes); },
}));
export const defaultGraceMinutes = 1440;

/** 凭证名称：去首尾空白后 1—64 字符，不允许控制字符。 */
export function credentialNameIssue(name: string): string | null {
  const value = name.trim();
  if (!value) return td("credential.name_required");
  if (Array.from(value).length > 64) return td("credential.name_too_long", { max: 64 });
  if (/[\u0000-\u001f\u007f]/.test(value)) return td("credential.name_control_chars");
  return null;
}

/** 创建凭证表单校验；返回字段到提示的映射，空对象表示可以提交。 */
export function credentialFormIssues(form: { name: string; scopeCodes: string[]; validityDays: number }, catalog: readonly string[]) {
  const issues: Record<string, string> = {};
  const name = credentialNameIssue(form.name);
  if (name) issues.name = name;
  if (form.scopeCodes.length === 0) issues.scopeCodes = td("credential.scope_required");
  else if (form.scopeCodes.length > 64) issues.scopeCodes = td("credential.scope_too_many");
  else if (new Set(form.scopeCodes).size !== form.scopeCodes.length || form.scopeCodes.some((code) => !catalog.includes(code))) {
    issues.scopeCodes = td("credential.scope_invalid");
  }
  if (!validityOptions.includes(form.validityDays as (typeof validityOptions)[number])) issues.validityDays = td("credential.validity_required");
  return issues;
}

/** 列表只读校验：任一项字段异常时整体视为读取失败。 */
export function readCredentials(value: unknown): Credential[] | null {
  const items = (value as { items?: unknown } | null)?.items;
  if (!Array.isArray(items)) return null;
  const result: Credential[] = [];
  for (const raw of items) {
    const c = raw as Partial<Credential> | null;
    if (!c || typeof c.credentialId !== "string" || typeof c.keyPrefix !== "string" || typeof c.status !== "string"
      || !Array.isArray(c.scopeCodes) || typeof c.expiresAt !== "string" || typeof c.version !== "number") return null;
    result.push({
      credentialId: c.credentialId, name: typeof c.name === "string" ? c.name : null, keyPrefix: c.keyPrefix, status: c.status,
      scopeCodes: c.scopeCodes.filter((code): code is string => typeof code === "string"),
      createdAt: typeof c.createdAt === "string" ? c.createdAt : "", expiresAt: c.expiresAt,
      rotatedFromId: typeof c.rotatedFromId === "string" ? c.rotatedFromId : null, version: c.version,
    });
  }
  return result;
}

/**
 * 一次性密钥弹窗的关闭规则
 * <li>必须勾选“我已安全保存”才能关闭；Esc、点遮罩同样受此限制。</li>
 */
export function canCloseSecret(acknowledged: boolean): boolean {
  return acknowledged;
}

/** 从创建 / 轮换响应中取出一次性密钥；格式不符时视为响应异常。 */
export function readSecret(value: unknown, field: "secret" | "signingSecret"): string | null {
  const secret = (value as Record<string, unknown> | null)?.[field];
  return typeof secret === "string" && secret.length >= 16 && secret.length <= 256 && !/\s/.test(secret) ? secret : null;
}

export type WebhookEventType = { code: string; readonly label: string };
/** 可订阅的业务事件；展示名在 `enum.webhook_event`。 */
export const webhookEventTypes: readonly WebhookEventType[] = enumCodes.webhook_event.map((code) => ({
  code,
  get label() { return enumLabel("webhook_event", code) ?? code; },
}));
/** 测试事件不可订阅，只出现在投递记录中。 */
export const testEventType = "webhook.ping";
/** 事件展示名：已知事件、测试事件、其他原样显示。 */
export function eventTypeLabel(code: string): string {
  return code === testEventType ? td("webhooks.test_event") : enumLabel("webhook_event", code) ?? code;
}
export const maxEndpoints = 5;
/** Platform 只允许重发已结束的投递（成功、放弃、已取消）；进行中或等待重试的不能重发。 */
export const replayableDeliveryStatuses: readonly string[] = ["SUCCEEDED", "DEAD", "CANCELLED"];
export function canReplay(status: string | null | undefined): boolean {
  return !!status && replayableDeliveryStatuses.includes(status);
}
/** 重发被业务拒绝时的说明：上游业务原因不会经 BFF 透传，按 Platform 规则给出可能原因。 */
export const replayRejectedText = (): string => td("webhooks.replay_rejected");

/** Webhook 地址的前端预检：只做明显错误提示，SSRF 等规则以服务端为准。 */
export function endpointUrlIssue(url: string): string | null {
  const value = url.trim();
  if (!value) return td("endpoint.url_required");
  if (value.length > 2048) return td("endpoint.url_too_long");
  let parsed: URL;
  try { parsed = new URL(value); } catch { return td("endpoint.url_invalid"); }
  if (parsed.protocol !== "https:") return td("endpoint.url_https");
  if (parsed.username || parsed.password) return td("endpoint.url_credentials");
  if (parsed.hash) return td("endpoint.url_fragment");
  return null;
}

/** 端点表单校验：地址与至少一个已知事件。 */
export function endpointFormIssues(form: { url: string; eventTypes: string[] }) {
  const issues: Record<string, string> = {};
  const url = endpointUrlIssue(form.url);
  if (url) issues.url = url;
  const known = webhookEventTypes.map((event) => event.code);
  if (form.eventTypes.length === 0) issues.eventTypes = td("endpoint.events_required");
  else if (new Set(form.eventTypes).size !== form.eventTypes.length || form.eventTypes.some((code) => !known.includes(code))) {
    issues.eventTypes = td("endpoint.events_invalid");
  }
  return issues;
}

export type Endpoint = {
  endpointId: string; url: string; eventTypes: string[]; status: string; version: number;
  createTime: string | null; rotationNo: number | null; previousValidUntil: string | null;
};
/** 端点列表校验。 */
export function readEndpoints(value: unknown): Endpoint[] | null {
  const items = (value as { items?: unknown } | null)?.items;
  if (!Array.isArray(items)) return null;
  const result: Endpoint[] = [];
  for (const raw of items) {
    const e = raw as Record<string, unknown> | null;
    if (!e || typeof e.endpointId !== "string" || typeof e.url !== "string" || !Array.isArray(e.eventTypes)
      || typeof e.status !== "string" || typeof e.version !== "number") return null;
    const secret = e.signingSecret as Record<string, unknown> | null | undefined;
    result.push({
      endpointId: e.endpointId, url: e.url, eventTypes: e.eventTypes.filter((t): t is string => typeof t === "string"),
      status: e.status, version: e.version, createTime: typeof e.createTime === "string" ? e.createTime : null,
      rotationNo: typeof secret?.rotationNo === "number" ? secret.rotationNo : null,
      previousValidUntil: typeof secret?.previousValidUntil === "string" ? secret.previousValidUntil : null,
    });
  }
  return result;
}

export type DeliveryFilter = { endpointId: string; eventType: string; status: string; from: string; to: string; page: number; size: number };
export const emptyDeliveryFilter: DeliveryFilter = { endpointId: "", eventType: "", status: "", from: "", to: "", page: 1, size: 20 };
/** 投递查询参数：只带有值的条件，时间转为 UTC ISO，size 上限 50。 */
export function deliveryQuery(filter: DeliveryFilter): string {
  const params = new URLSearchParams();
  if (filter.endpointId) params.set("endpointId", filter.endpointId);
  if (filter.eventType) params.set("eventType", filter.eventType);
  if (filter.status) params.set("status", filter.status);
  for (const key of ["from", "to"] as const) {
    const time = Date.parse(filter[key]);
    if (filter[key] && Number.isFinite(time)) params.set(key, new Date(time).toISOString());
  }
  params.set("page", String(Math.max(1, Math.trunc(filter.page))));
  params.set("size", String(Math.min(50, Math.max(1, Math.trunc(filter.size)))));
  return params.toString();
}

/** 按当前语言显示的本地时间；无效值显示破折号。 */
export function displayTime(value: string | null | undefined): string {
  return formatDate(value, { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false });
}
