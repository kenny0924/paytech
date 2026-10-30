import { ApiError } from '../api/session.ts';
import type { ConsoleResource, PagingResponseBody } from '../api/types.ts';

/**
 * 风控 · 限额档位（REQ-04，technique 第 8、11 节）：ops-bff 路径、响应解码与页面模型
 * <li>risk-decision 是档位权威；ops-bff 工作台聚合档位详情（L2）、变更历史（L3）、有效承保与复核清单系统项。</li>
 * <li>申请只发目标档位、理由、上线说明、观察目标与四个人工确认项；系统核对项由 ops-bff 在提交时重新读取。</li>
 * <li>解码只要求页面用到的字段存在且类型正确；ops-bff 追加的显示名可缺省，多出的字段忽略。ID、金额、时间均为字符串。</li>
 */
export const limitTierApi = '/risk-limit-tiers/v1';
export const limitTierRoute = '/risk/limit-tiers';
export const limitTierMenu = 'OPERATIONS_RISK_LIMIT_TIERS';
export const limitTierButtons = {
  request: 'OPERATIONS_RISK_LIMIT_TIER_REQUEST', approve: 'OPERATIONS_RISK_LIMIT_TIER_APPROVE', approveElevated: 'OPERATIONS_RISK_LIMIT_TIER_APPROVE_ELEVATED',
} as const;
const enc = encodeURIComponent;
export const limitTierPaths = {
  list: `${limitTierApi}/client-tiers`,
  statistics: `${limitTierApi}/tier-statistics`,
  quotas: `${limitTierApi}/tier-quotas`,
  workspace: (clientId: string) => `${limitTierApi}/clients/${enc(clientId)}/workspace`,
  history: (clientId: string) => `${limitTierApi}/clients/${enc(clientId)}/tier-changes`,
  request: (clientId: string) => `${limitTierApi}/clients/${enc(clientId)}/tier-changes`,
  withdrawal: (changeId: string) => `${limitTierApi}/tier-changes/${enc(changeId)}/withdrawal`,
  approval: (changeId: string) => `${limitTierApi}/tier-changes/${enc(changeId)}/approval`,
  elevatedApproval: (changeId: string) => `${limitTierApi}/tier-changes/${enc(changeId)}/elevated-approval`,
  rejection: (changeId: string) => `${limitTierApi}/tier-changes/${enc(changeId)}/rejection`,
  elevatedRejection: (changeId: string) => `${limitTierApi}/tier-changes/${enc(changeId)}/elevated-rejection`,
} as const;
export const limitTierRoutes = { list: limitTierRoute, detail: (clientId: string) => `${limitTierRoute}/${enc(clientId)}` };

export const tiers = ['VERIFICATION', 'OBSERVATION', 'STANDARD', 'RESTRICTED'] as const;
export type Tier = typeof tiers[number];
export const crrs = ['LOW', 'MEDIUM', 'HIGH'] as const;
export type Crr = typeof crrs[number];
export const sourceTypes = ['UNDERWRITING_INIT', 'MIGRATION', 'REQUEST'] as const;
export type SourceType = typeof sourceTypes[number];
export const changeStatuses = ['PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'] as const;
export type ChangeStatus = typeof changeStatuses[number];
export const approvalLevels = ['STANDARD', 'ELEVATED'] as const;
export type ApprovalLevel = typeof approvalLevels[number];
export const checkStatuses = ['OK', 'NOT_OK', 'UNAVAILABLE'] as const;
export type CheckStatus = typeof checkStatuses[number];
/** 复核清单（B5）：四个系统核对项（ops-bff 读取，只读）与四个人工确认项（页面勾选）。 */
export const systemChecks = ['QUALIFICATION', 'UNDERWRITING', 'AGREEMENT', 'CAPABILITIES'] as const;
export type SystemCheck = typeof systemChecks[number];
export const manualChecks = ['CHANNEL', 'INTEGRATION', 'PRODUCTION', 'OPEN_ISSUES'] as const;
export type ManualCheck = typeof manualChecks[number];
/** 人工项 → 请求体字段（ChecklistBody）。 */
export const manualCheckField: Record<ManualCheck, string> = {
  CHANNEL: 'channelRegistrationConfirmed', INTEGRATION: 'integrationConfirmed', PRODUCTION: 'productionVerified', OPEN_ISSUES: 'openIssuesCleared',
};
/** 系统项的数据来源（页面标注 compliance / risk / commercial）。 */
export const systemCheckSource: Record<SystemCheck, string> = { QUALIFICATION: 'compliance', UNDERWRITING: 'risk', AGREEMENT: 'commercial', CAPABILITIES: 'commercial' };

export interface PendingChange {
  changeId: string; fromTier: Tier | null; toTier: Tier; approvalLevel: ApprovalLevel; requestedBy: string | null; requestedByLabel: string | null; requestedTime: string;
}
export interface TierSummary {
  clientId: string; clientDisplayName: string; clientNo: string; jurisdictionCode: string; clientType: string; crr: Crr | null; tier: Tier;
  effectiveTime: string; sourceType: SourceType; sourceReason: string | null; observationStartedTime: string | null; observationDaysElapsed: number | null;
  observationTargetDays: number | null; observationAllowedTransactions: number | null; observationTargetTransactions: number | null; observationDue: boolean;
  pendingChange: PendingChange | null; updatedAt: string;
}
export interface Observation { startedTime: string; daysElapsed: number; targetDays: number; allowedTransactions: number; targetTransactions: number; due: boolean }
/**
 * 本档位一个币种的额度；单笔 / 当日 / 近 30 天常量缺项时为空（风控配置不完整）
 * <li>当日 = 当前 UTC 自然日；近 30 天 = 含当日的最近 30 个 UTC 自然日（按日汇总，非滑动窗口）。</li>
 */
export interface Quota { currency: string; single: string | null; day: string | null; last30Days: string | null; usedToday: string; usedLast30Days: string }
export type UnconfiguredPolicy = 'DENY' | 'ALLOW';
export interface TierDetail extends TierSummary {
  version: number; previousTier: Tier | null; observation: Observation | null; quotas: Quota[]; unconfiguredCurrencies: string[]; unconfiguredCurrencyPolicy: UnconfiguredPolicy;
}
export interface Checklist {
  qualificationStatus: string | null; qualificationValidTo: string | null; underwritingId: string | null; underwritingVersionNo: number | null; agreementId: string | null;
  agreementStatus: string | null; enabledPaymentCapabilities: string[]; pendingPaymentCapabilities: string[];
  channelRegistrationConfirmed: boolean | null; integrationConfirmed: boolean | null; productionVerified: boolean | null; openIssuesCleared: boolean | null;
}
export interface TierChange {
  changeId: string; clientId: string; changeType: SourceType; fromTier: Tier | null; toTier: Tier; status: ChangeStatus; approvalLevel: ApprovalLevel | null;
  crrSnapshot: Crr | null; reason: string; goLiveNote: string | null; checklist: Checklist | null; observationTargetDays: number | null; observationTargetTransactions: number | null;
  observationDaysAtRequest: number | null; observationAllowedAtRequest: number | null; requestedBy: string | null; requestedByLabel: string | null; requestedTime: string;
  decidedBy: string | null; decidedByLabel: string | null; decidedTime: string | null; decisionComment: string | null; version: number;
}
export interface Statistics { verification: number; observation: number; standard: number; restricted: number; pendingApproval: number; observationDue: number }
export interface WorkspaceUnderwriting {
  underwritingId: string; versionNo: number; crr: Crr | null; observationDays: number | null; observationTransactions: number | null; suggestedSettlementDays: number | null;
}
export interface SystemCheckFacts {
  qualificationStatus: string | null; qualificationValidTo: string | null; underwritingVersionNo: number | null; crr: Crr | null; agreementId: string | null;
  agreementStatus: string | null; enabledPaymentCapabilities: string[]; pendingPaymentCapabilities: string[];
}
export interface WorkspaceClient { clientId: string; clientNo: string | null; displayName: string | null; clientType: string | null; jurisdictionCode: string | null }
export interface Workspace {
  client: WorkspaceClient; tier: TierDetail | null; history: TierChange[]; underwriting: WorkspaceUnderwriting | null;
  checks: { code: SystemCheck; status: CheckStatus }[]; facts: SystemCheckFacts;
}

/* ---------- 解码 ---------- */

function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
type Row = Record<string, unknown>;
const record = (v: unknown): Row => { require(v !== null && typeof v === 'object' && !Array.isArray(v)); return v as Row; };
const idPattern = /^[1-9][0-9]{0,18}$/;
const isId = (v: unknown): v is string => typeof v === 'string' && idPattern.test(v);
const decimal = /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/;
const amount = (v: unknown): v is string => typeof v === 'string' && decimal.test(v);
const currency = (v: unknown): v is string => typeof v === 'string' && /^[A-Z]{3}$/.test(v);
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(v) && Number.isFinite(Date.parse(v));
const textOf = (v: unknown, max: number): v is string => typeof v === 'string' && [...v].length <= max;
const codeOf = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const int = (v: unknown, min: number, max: number): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= min && v <= max;
const count = (v: unknown): v is number => int(v, 0, Number.MAX_SAFE_INTEGER);
const bool = (v: unknown): v is boolean => typeof v === 'boolean';
function nullable<T>(v: unknown, valid: (v: unknown) => boolean): T | null { if (v === null || v === undefined) return null; require(valid(v)); return v as T; }
const oneOf = <T extends string>(values: readonly T[]) => (v: unknown): v is T => typeof v === 'string' && (values as readonly string[]).includes(v);
const label = (v: unknown) => nullable<string>(v, x => textOf(x, 255));
/** 支付方式代码：数组，或兼容逗号串；去重保序。 */
function codes(v: unknown): string[] {
  if (v === null || v === undefined || v === '') return [];
  const list = typeof v === 'string' ? v.split(',').map(code => code.trim()).filter(Boolean) : (require(Array.isArray(v)), v as unknown[]);
  require(list.length <= 64 && list.every(codeOf));
  return [...new Set(list as string[])];
}

function decodePendingChange(value: unknown): PendingChange | null {
  if (value === null || value === undefined) return null;
  const v = record(value);
  require(isId(v.changeId) && oneOf(tiers)(v.toTier) && oneOf(approvalLevels)(v.approvalLevel) && instant(v.requestedTime));
  return { changeId: v.changeId, fromTier: nullable(v.fromTier, oneOf(tiers)), toTier: v.toTier, approvalLevel: v.approvalLevel,
    requestedBy: nullable(v.requestedBy, isId), requestedByLabel: label(v.requestedByLabel), requestedTime: v.requestedTime };
}

export function decodeSummary(value: unknown): TierSummary {
  const v = record(value);
  require(isId(v.clientId) && textOf(v.clientDisplayName, 255) && textOf(v.clientNo, 64) && textOf(v.jurisdictionCode, 16) && codeOf(v.clientType)
    && oneOf(tiers)(v.tier) && instant(v.effectiveTime) && oneOf(sourceTypes)(v.sourceType) && instant(v.updatedAt));
  const days = (x: unknown) => nullable<number>(x, y => int(y, 0, 100000));
  const summary: TierSummary = {
    clientId: v.clientId, clientDisplayName: v.clientDisplayName, clientNo: v.clientNo, jurisdictionCode: v.jurisdictionCode, clientType: v.clientType,
    crr: nullable(v.crr, oneOf(crrs)), tier: v.tier, effectiveTime: v.effectiveTime, sourceType: v.sourceType, sourceReason: nullable(v.sourceReason, x => textOf(x, 1000)),
    observationStartedTime: nullable(v.observationStartedTime, instant), observationDaysElapsed: days(v.observationDaysElapsed),
    observationTargetDays: nullable(v.observationTargetDays, x => int(x, 1, 365)), observationAllowedTransactions: nullable(v.observationAllowedTransactions, count),
    observationTargetTransactions: nullable(v.observationTargetTransactions, x => int(x, 1, 100000)),
    observationDue: v.observationDue === undefined || v.observationDue === null ? false : (require(bool(v.observationDue)), v.observationDue as boolean),
    pendingChange: decodePendingChange(v.pendingChange), updatedAt: v.updatedAt,
  };
  // 只有观察档有观察期，且到期标记只出现在观察档。
  require(summary.tier === 'OBSERVATION' || (!summary.observationDue && summary.observationStartedTime === null));
  require(!summary.pendingChange || summary.pendingChange.fromTier === null || summary.pendingChange.fromTier === summary.tier);
  return summary;
}

export function decodeSummaryPage(value: unknown): PagingResponseBody<TierSummary> {
  const v = record(value);
  require(Array.isArray(v.items) && v.items.length <= 200 && int(v.page, 1, 100000) && int(v.size, 1, 200) && count(v.total));
  return { items: v.items.map(decodeSummary), page: v.page, size: v.size, total: v.total };
}

export function decodeStatistics(value: unknown): Statistics {
  const v = record(value);
  const keys = ['verification', 'observation', 'standard', 'restricted', 'pendingApproval', 'observationDue'] as const;
  require(keys.every(key => count(v[key])));
  return Object.fromEntries(keys.map(key => [key, v[key]])) as unknown as Statistics;
}

function decodeObservation(value: unknown): Observation | null {
  if (value === null || value === undefined) return null;
  const v = record(value);
  require(instant(v.startedTime) && int(v.daysElapsed, 0, 100000) && int(v.targetDays, 1, 365) && count(v.allowedTransactions)
    && int(v.targetTransactions, 1, 100000) && bool(v.due));
  return { startedTime: v.startedTime, daysElapsed: v.daysElapsed, targetDays: v.targetDays, allowedTransactions: v.allowedTransactions,
    targetTransactions: v.targetTransactions, due: v.due };
}

function decodeQuota(value: unknown): Quota {
  const v = record(value);
  require(currency(v.currency) && amount(v.usedToday) && amount(v.usedLast30Days));
  return { currency: v.currency, single: nullable(v.single, amount), day: nullable(v.day, amount), last30Days: nullable(v.last30Days, amount),
    usedToday: v.usedToday, usedLast30Days: v.usedLast30Days };
}

/** 档位详情（L2）；无档位（`tier` 为空）返回 null。 */
export function decodeTierDetail(value: unknown, clientId: string): TierDetail | null {
  if (value === null || value === undefined) return null;
  const v = record(value);
  if (v.tier === null || v.tier === undefined) return null;
  const summary = decodeSummary(v);
  require(summary.clientId === clientId && int(v.version, 0, 2147483647) && Array.isArray(v.quotas) && v.quotas.length <= 64
    && oneOf(['DENY', 'ALLOW'] as const)(v.unconfiguredCurrencyPolicy));
  const quotas = v.quotas.map(decodeQuota);
  const unconfigured = v.unconfiguredCurrencies === undefined || v.unconfiguredCurrencies === null ? [] : (require(Array.isArray(v.unconfiguredCurrencies)), v.unconfiguredCurrencies as unknown[]);
  require(unconfigured.length <= 64 && unconfigured.every(currency));
  const configured = new Set(quotas.map(quota => quota.currency));
  require(configured.size === quotas.length && !(unconfigured as string[]).some(code => configured.has(code)));
  const detail: TierDetail = { ...summary, version: v.version as number, previousTier: nullable(v.previousTier, oneOf(tiers)), observation: decodeObservation(v.observation),
    quotas, unconfiguredCurrencies: [...new Set(unconfigured as string[])], unconfiguredCurrencyPolicy: v.unconfiguredCurrencyPolicy as UnconfiguredPolicy };
  require(detail.tier === 'OBSERVATION' || detail.observation === null);
  require(detail.tier !== 'RESTRICTED' || detail.previousTier !== 'RESTRICTED');
  return detail;
}

function decodeChecklist(value: unknown): Checklist | null {
  if (value === null || value === undefined) return null;
  const v = record(value);
  const flag = (x: unknown) => nullable<boolean>(x, bool);
  return {
    qualificationStatus: nullable(v.qualificationStatus, codeOf), qualificationValidTo: nullable(v.qualificationValidTo, instant), underwritingId: nullable(v.underwritingId, isId),
    underwritingVersionNo: nullable(v.underwritingVersionNo, x => int(x, 1, 1000000)), agreementId: nullable(v.agreementId, isId), agreementStatus: nullable(v.agreementStatus, codeOf),
    enabledPaymentCapabilities: codes(v.enabledPaymentCapabilities), pendingPaymentCapabilities: codes(v.pendingPaymentCapabilities),
    channelRegistrationConfirmed: flag(v.channelRegistrationConfirmed), integrationConfirmed: flag(v.integrationConfirmed), productionVerified: flag(v.productionVerified),
    openIssuesCleared: flag(v.openIssuesCleared),
  };
}

export function decodeChange(value: unknown, clientId?: string): TierChange {
  const v = record(value);
  require(isId(v.changeId) && isId(v.clientId) && (!clientId || v.clientId === clientId) && oneOf(sourceTypes)(v.changeType) && oneOf(tiers)(v.toTier)
    && oneOf(changeStatuses)(v.status) && textOf(v.reason, 1000) && instant(v.requestedTime) && int(v.version, 0, 2147483647));
  const small = (x: unknown) => nullable<number>(x, y => int(y, 0, 100000));
  const change: TierChange = {
    changeId: v.changeId, clientId: v.clientId, changeType: v.changeType, fromTier: nullable(v.fromTier, oneOf(tiers)), toTier: v.toTier, status: v.status,
    approvalLevel: nullable(v.approvalLevel, oneOf(approvalLevels)), crrSnapshot: nullable(v.crrSnapshot, oneOf(crrs)), reason: v.reason,
    goLiveNote: nullable(v.goLiveNote, x => textOf(x, 2000)), checklist: decodeChecklist(v.checklist),
    observationTargetDays: nullable(v.observationTargetDays, x => int(x, 1, 365)), observationTargetTransactions: nullable(v.observationTargetTransactions, x => int(x, 1, 100000)),
    observationDaysAtRequest: small(v.observationDaysAtRequest), observationAllowedAtRequest: nullable(v.observationAllowedAtRequest, count),
    requestedBy: nullable(v.requestedBy, isId), requestedByLabel: label(v.requestedByLabel), requestedTime: v.requestedTime,
    decidedBy: nullable(v.decidedBy, isId), decidedByLabel: label(v.decidedByLabel), decidedTime: nullable(v.decidedTime, instant),
    decisionComment: nullable(v.decisionComment, x => textOf(x, 500)), version: v.version,
  };
  // 运营申请有审批级别；待审批没有决定；驳回必有意见。
  require(change.changeType !== 'REQUEST' || change.approvalLevel !== null);
  require(change.status !== 'PENDING' || (change.changeType === 'REQUEST' && change.decidedTime === null));
  require(change.status !== 'REJECTED' || change.decisionComment !== null);
  return change;
}

/** 变更历史（L3）：同一 Client，申请时间倒序，至多一个待审批。 */
export function decodeHistory(value: unknown, clientId: string): TierChange[] {
  const v = record(value);
  require(Array.isArray(v.items) && v.items.length <= 100);
  const items = v.items.map(item => decodeChange(item, clientId));
  require(items.every((item, index) => index === 0 || Date.parse(items[index - 1].requestedTime) >= Date.parse(item.requestedTime)));
  require(items.filter(item => item.status === 'PENDING').length <= 1);
  return items;
}

function decodeUnderwriting(value: unknown): WorkspaceUnderwriting | null {
  if (value === null || value === undefined) return null;
  const v = record(value);
  require(isId(v.underwritingId) && int(v.versionNo, 1, 1000000));
  return { underwritingId: v.underwritingId, versionNo: v.versionNo, crr: nullable(v.crr, oneOf(crrs)),
    observationDays: nullable(v.observationDays, x => int(x, 1, 365)), observationTransactions: nullable(v.observationTransactions, x => int(x, 1, 100000)),
    suggestedSettlementDays: nullable(v.suggestedSettlementDays, x => int(x, 0, 90)) };
}

function decodeFacts(value: unknown): SystemCheckFacts {
  const v = value === null || value === undefined ? {} : record(value);
  return { qualificationStatus: nullable(v.qualificationStatus, codeOf), qualificationValidTo: nullable(v.qualificationValidTo, instant),
    underwritingVersionNo: nullable(v.underwritingVersionNo, x => int(x, 1, 1000000)), crr: nullable(v.crr, oneOf(crrs)), agreementId: nullable(v.agreementId, isId),
    agreementStatus: nullable(v.agreementStatus, codeOf), enabledPaymentCapabilities: codes(v.enabledPaymentCapabilities), pendingPaymentCapabilities: codes(v.pendingPaymentCapabilities) };
}

/**
 * 工作台（ops-bff）：档位详情、历史、有效承保与复核清单系统项
 * <li>系统项必须恰好是四项且各出现一次；缺失时整体视为响应异常，避免把"未返回"当成"已满足"。</li>
 */
export function decodeWorkspace(value: unknown, clientId: string): Workspace {
  const v = record(value); const c = record(v.client);
  require(c.clientId === clientId);
  const history = decodeHistory(v.history, clientId);
  const checklist = record(v.checklist);
  require(Array.isArray(checklist.items) && checklist.items.length === systemChecks.length);
  const checks = checklist.items.map(item => { const i = record(item); require(oneOf(systemChecks)(i.code) && oneOf(checkStatuses)(i.status)); return { code: i.code, status: i.status }; });
  require(new Set(checks.map(check => check.code)).size === systemChecks.length);
  const tier = decodeTierDetail(v.tier, clientId);
  const pending = history.find(item => item.status === 'PENDING') ?? null;
  // 详情的待审批摘要与历史中的待审批记录必须一致。
  require(!tier || (tier.pendingChange?.changeId ?? null) === (pending?.changeId ?? null));
  return {
    client: { clientId, clientNo: nullable(c.clientNo, x => textOf(x, 64)), displayName: nullable(c.displayName, x => textOf(x, 255)),
      clientType: nullable(c.clientType, codeOf), jurisdictionCode: nullable(c.jurisdictionCode, x => textOf(x, 16)) },
    tier, history, underwriting: decodeUnderwriting(v.underwriting),
    checks: systemChecks.map(code => checks.find(check => check.code === code)!), facts: decodeFacts(checklist.facts),
  };
}

/* ---------- 页面模型 ---------- */

export const hasButton = (resources: readonly ConsoleResource[], code: string) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === code);
export const hasMenu = (resources: readonly ConsoleResource[], componentKey: string) => resources.some(r => r.resourceType === 'MENU' && r.componentKey === componentKey);
export interface Permissions { request: boolean; approve: boolean; approveElevated: boolean }
export const permissionsOf = (resources: readonly ConsoleResource[]): Permissions => ({ request: hasButton(resources, limitTierButtons.request),
  approve: hasButton(resources, limitTierButtons.approve), approveElevated: hasButton(resources, limitTierButtons.approveElevated) });

/** 档位高低（B10）：VERIFICATION < OBSERVATION < STANDARD；RESTRICTED 不参与比较。 */
const rank: Record<Exclude<Tier, 'RESTRICTED'>, number> = { VERIFICATION: 1, OBSERVATION: 2, STANDARD: 3 };
/**
 * 允许申请的目标档位（B3）
 * <li>VERIFICATION → OBSERVATION（上线复核）；OBSERVATION → STANDARD 或延长 OBSERVATION；任意非受限档 → RESTRICTED。</li>
 * <li>RESTRICTED → 受限前档位或更低；受限前档位未知时只能回验证档。</li>
 */
export function requestableTargets(tier: Tier, previousTier: Tier | null): Tier[] {
  switch (tier) {
    case 'VERIFICATION': return ['OBSERVATION', 'RESTRICTED'];
    case 'OBSERVATION': return ['STANDARD', 'OBSERVATION', 'RESTRICTED'];
    case 'STANDARD': return ['RESTRICTED'];
    case 'RESTRICTED': {
      const ceiling = previousTier && previousTier !== 'RESTRICTED' ? rank[previousTier] : rank.VERIFICATION;
      return (['VERIFICATION', 'OBSERVATION', 'STANDARD'] as const).filter(target => rank[target] <= ceiling);
    }
  }
}
/** 受限前档位（恢复上限）；未知按验证档。 */
export const restoreCeiling = (previousTier: Tier | null): Exclude<Tier, 'RESTRICTED'> =>
  previousTier && previousTier !== 'RESTRICTED' ? previousTier : 'VERIFICATION';

export const pendingOf = (history: readonly TierChange[]) => history.find(item => item.status === 'PENDING') ?? null;
/** 当前档位由哪条记录产生：历史中最近一条已批准记录。 */
export const sourceChangeOf = (history: readonly TierChange[]) => history.find(item => item.status === 'APPROVED') ?? null;

/**
 * 审批区可用性（B4、原型 04）
 * <li>申请人本人：不能审批（四眼），有申请权限时可撤回。</li>
 * <li>ELEVATED 只能用高级审批接口；STANDARD 有普通审批权限时走普通接口，只有高级权限时走高级接口（任意级别）。</li>
 * <li>没有对应权限：按钮禁用并说明须高级审批人；没有任何审批权限：只显示等待审批。</li>
 */
export type ApprovalAccess = { kind: 'approve'; elevated: boolean } | { kind: 'self'; canWithdraw: boolean } | { kind: 'insufficient' } | { kind: 'none' };
export function approvalAccess(change: Pick<TierChange, 'status' | 'requestedBy' | 'approvalLevel'>, actorId: string, permissions: Permissions): ApprovalAccess {
  if (change.status !== 'PENDING') return { kind: 'none' };
  if (change.requestedBy !== null && change.requestedBy === actorId) return { kind: 'self', canWithdraw: permissions.request };
  if (change.approvalLevel === 'ELEVATED') return permissions.approveElevated ? { kind: 'approve', elevated: true } : permissions.approve ? { kind: 'insufficient' } : { kind: 'none' };
  if (permissions.approve) return { kind: 'approve', elevated: false };
  return permissions.approveElevated ? { kind: 'approve', elevated: true } : { kind: 'none' };
}
export function decisionPath(changeId: string, decision: 'APPROVE' | 'REJECT', elevated: boolean): string {
  if (decision === 'APPROVE') return elevated ? limitTierPaths.elevatedApproval(changeId) : limitTierPaths.approval(changeId);
  return elevated ? limitTierPaths.elevatedRejection(changeId) : limitTierPaths.rejection(changeId);
}
/** 审批意见：驳回必填，≤ 500。 */
export function commentIssue(decision: 'APPROVE' | 'REJECT', comment: string): 'required' | 'tooLong' | null {
  if ([...comment].length > 500) return 'tooLong';
  return decision === 'REJECT' && !comment.trim() ? 'required' : null;
}

/** 复核清单人工项勾选状态。 */
export type ManualState = Record<ManualCheck, boolean>;
export const emptyManualState: ManualState = { CHANNEL: false, INTEGRATION: false, PRODUCTION: false, OPEN_ISSUES: false };
/** 提交上线复核的前提（B5）：四个系统项均为 OK、四个人工项全部勾选、上线说明非空且 ≤ 1000。 */
export function goLiveReady(checks: readonly { status: CheckStatus }[], manual: ManualState, note: string): boolean {
  return checks.length === systemChecks.length && checks.every(check => check.status === 'OK') && manualChecks.every(code => manual[code])
    && note.trim().length > 0 && [...note.trim()].length <= 1000;
}

/** 申请表单（L4 页面字段）；观察目标为字符串输入，空 = 沿用。 */
export interface RequestForm { toTier: Tier; reason: string; goLiveNote: string; targetDays: string; targetTransactions: string; manual: ManualState }
export type RequestIssue = 'reasonRequired' | 'reasonTooLong' | 'targetDays' | 'targetTransactions' | 'checklist';
const wholeNumber = (value: string, min: number, max: number) => /^[0-9]{1,6}$/.test(value.trim()) && Number(value) >= min && Number(value) <= max;
export function requestIssues(form: RequestForm, fromTier: Tier): RequestIssue[] {
  const issues: RequestIssue[] = [];
  const reason = goLive(fromTier, form.toTier) ? form.goLiveNote : form.reason;
  if (!reason.trim()) issues.push('reasonRequired');
  else if ([...reason.trim()].length > 1000) issues.push('reasonTooLong');
  if (form.toTier === 'OBSERVATION' && fromTier === 'OBSERVATION') {
    if (form.targetDays.trim() && !wholeNumber(form.targetDays, 1, 365)) issues.push('targetDays');
    if (form.targetTransactions.trim() && !wholeNumber(form.targetTransactions, 1, 100000)) issues.push('targetTransactions');
  }
  if (goLive(fromTier, form.toTier) && !manualChecks.every(code => form.manual[code])) issues.push('checklist');
  return issues;
}
/** 上线复核：VERIFICATION → OBSERVATION。 */
export const goLive = (fromTier: Tier, toTier: Tier) => fromTier === 'VERIFICATION' && toTier === 'OBSERVATION';
/**
 * 表单 → L4 请求体
 * <li>上线复核：上线说明同时作为理由（原型只有一个说明框），并带四个人工项；系统项由 ops-bff 重新读取后附带。</li>
 * <li>延长观察：目标天数 / 笔数填写才发送，空则沿用。</li>
 */
export function requestBody(form: RequestForm, fromTier: Tier, expectedTierVersion: number): Record<string, unknown> {
  const body: Record<string, unknown> = { expectedTierVersion, toTier: form.toTier };
  if (goLive(fromTier, form.toTier)) {
    const note = form.goLiveNote.trim();
    body.reason = note; body.goLiveNote = note;
    body.checklist = Object.fromEntries(manualChecks.map(code => [manualCheckField[code], form.manual[code]]));
  } else body.reason = form.reason.trim();
  if (fromTier === 'OBSERVATION' && form.toTier === 'OBSERVATION') {
    if (form.targetDays.trim()) body.observationTargetDays = Number(form.targetDays.trim());
    if (form.targetTransactions.trim()) body.observationTargetTransactions = Number(form.targetTransactions.trim());
  }
  return body;
}

/** 列表页签：全部 + 四个档位。 */
export const listTabs = ['ALL', ...tiers] as const;
export type ListTab = typeof listTabs[number];
export interface ListQuery { tab: ListTab; keyword: string; observationDue: boolean; pendingApproval: boolean }
export const emptyListQuery: ListQuery = { tab: 'ALL', keyword: '', observationDue: false, pendingApproval: false };
/** 列表 Query 参数（L1）：空值不发送。 */
export function listFilters(query: ListQuery): Record<string, string> {
  const filters: Record<string, string> = {};
  if (query.tab !== 'ALL') filters.tier = query.tab;
  if (query.keyword.trim()) filters.keyword = query.keyword.trim();
  if (query.observationDue) filters.observationDue = 'true';
  if (query.pendingApproval) filters.pendingApproval = 'true';
  return filters;
}
/** 地址栏 → 列表查询；未知值回落默认。 */
export function parseListQuery(search: URLSearchParams): ListQuery {
  const tab = search.get('tier') ?? 'ALL';
  return { tab: (listTabs as readonly string[]).includes(tab) ? tab as ListTab : 'ALL', keyword: (search.get('keyword') ?? '').slice(0, 100),
    observationDue: search.get('observationDue') === 'true', pendingApproval: search.get('pendingApproval') === 'true' };
}
/** 列表查询 + 分页 → 地址栏参数。 */
export function listSearch(query: ListQuery, paging: { page: number; size: number }): string {
  const params = new URLSearchParams(listFilters(query));
  params.set('page', String(paging.page)); params.set('size', String(paging.size));
  return params.toString();
}

/** 十进制字符串按千分位分组，去掉末尾零（只做显示，不转浮点）。 */
export function groupDecimal(value: string): string {
  const [whole, part = ''] = value.split('.');
  const decimals = part.replace(/0+$/, '');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return decimals ? `${grouped}.${decimals}` : grouped;
}
/** 进度条百分比：已用 / 上限，0—100；上限为空或为零时 null（不画条）。只用于条长，数值显示以字符串为准。 */
export function percent(used: string | number, limit: string | number | null): number | null {
  if (limit === null) return null;
  const total = Number(limit), value = Number(used);
  if (!Number.isFinite(total) || total <= 0 || !Number.isFinite(value)) return null;
  return Math.max(0, Math.min(100, (value / total) * 100));
}
/** 已用达到或超过上限（条变红）。 */
export const exhausted = (used: string, limit: string | null) => limit !== null && Number(limit) > 0 && Number(used) >= Number(limit);

/** 额度表行：已配置币种 + 本档位未配置的币种（显示"未配置"）。 */
export type QuotaRow = { kind: 'configured'; quota: Quota } | { kind: 'unconfigured'; currency: string };
export function quotaRows(detail: Pick<TierDetail, 'quotas' | 'unconfiguredCurrencies'>): QuotaRow[] {
  return [...detail.quotas.map(quota => ({ kind: 'configured' as const, quota })), ...detail.unconfiguredCurrencies.map(code => ({ kind: 'unconfigured' as const, currency: code }))];
}

/** 列表行操作（原型 01）：可审批的待审批 → 处理；验证档、可申请、无待审批 → 申请上线；其他 → 查看。 */
export function rowAction(row: Pick<TierSummary, 'tier' | 'pendingChange'>, actorId: string, permissions: Permissions): 'handle' | 'goLive' | 'view' {
  const pending = row.pendingChange;
  if (pending) {
    const access = approvalAccess({ status: 'PENDING', requestedBy: pending.requestedBy, approvalLevel: pending.approvalLevel }, actorId, permissions);
    return access.kind === 'approve' ? 'handle' : 'view';
  }
  return row.tier === 'VERIFICATION' && permissions.request ? 'goLive' : 'view';
}

/** 已知命令错误码（technique 8.1）→ 文案键；未知码由页面显示服务端 detail。 */
export const knownErrors = ['RISK_LIMIT_TIER_NOT_FOUND', 'RISK_LIMIT_TIER_CHANGE_NOT_FOUND', 'RISK_LIMIT_TIER_TRANSITION_INVALID', 'RISK_LIMIT_TIER_CHANGE_PENDING_EXISTS',
  'RISK_LIMIT_TIER_VERSION_CONFLICT', 'RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED', 'RISK_LIMIT_TIER_ELEVATION_REQUIRED', 'RISK_LIMIT_TIER_SELF_APPROVAL',
  'RISK_LIMIT_TIER_NOT_REQUESTER', 'RISK_LIMIT_TIER_CHANGE_STATE_INVALID', 'CONCURRENT_REQUEST'] as const;

export const isClientId = (value: string) => idPattern.test(value);
