import { ApiError } from '../api/session.ts';
import type { ConsoleResource, PagingResponseBody } from '../api/types.ts';

/**
 * 风控 · 承保（REQ-03，technique 第 8、10.1、11 节）：ops-bff 路径、响应解码与页面模型
 * <li>ops-bff 聚合 compliance / client-master / Platform 与 risk-decision 记录；页面命令只发结论字段，输入由 ops-bff 重新取得后附带。</li>
 * <li>解码只要求页面用到的字段存在且类型正确，ops-bff 追加的展示字段（如操作人显示名）可缺省，多出的字段忽略。</li>
 * <li>ID、金额、时间均为字符串；金额只做字符串分组显示，不转浮点。</li>
 */
export const underwritingApi = '/risk-underwriting/v1';
export const underwritingRoute = '/risk/underwriting';
export const underwritingMenu = 'OPERATIONS_RISK_UNDERWRITING';
export const underwritingButtons = { manage: 'OPERATIONS_RISK_UNDERWRITING_MANAGE', approve: 'OPERATIONS_RISK_UNDERWRITING_APPROVE' } as const;
const enc = encodeURIComponent;
export const underwritingPaths = {
  list: `${underwritingApi}/underwritings`,
  detail: (id: string) => `${underwritingApi}/underwritings/${enc(id)}`,
  workspace: (clientId: string) => `${underwritingApi}/clients/${enc(clientId)}/workspace`,
  history: (clientId: string) => `${underwritingApi}/clients/${enc(clientId)}/underwritings`,
  create: (clientId: string) => `${underwritingApi}/clients/${enc(clientId)}/underwritings`,
  save: (id: string) => `${underwritingApi}/underwritings/${enc(id)}`,
  submission: (id: string) => `${underwritingApi}/underwritings/${enc(id)}/submission`,
  review: (id: string) => `${underwritingApi}/underwritings/${enc(id)}/review`,
  auto: (clientId: string) => `${underwritingApi}/clients/${enc(clientId)}/auto-underwritings`,
  revision: (id: string) => `${underwritingApi}/underwritings/${enc(id)}/revision`,
  previews: `${underwritingApi}/previews`,
  statistics: `${underwritingApi}/underwriting-statistics`,
} as const;
export const underwritingRoutes = {
  list: underwritingRoute,
  detail: (id: string) => `${underwritingRoute}/${id}`,
  create: (clientId: string) => `${underwritingRoute}/new?clientId=${enc(clientId)}`,
};

export const statuses = ['DRAFT', 'SUBMITTED', 'APPROVED', 'DECLINED', 'SUPERSEDED'] as const;
export type UnderwritingStatus = typeof statuses[number];
export const crrs = ['LOW', 'MEDIUM', 'HIGH'] as const;
export type Crr = typeof crrs[number];
export const decisionSources = ['MANUAL', 'AUTO_RULE'] as const;
export type DecisionSource = typeof decisionSources[number];
export const fulfillmentTypes = ['INSTANT', 'PRE_ORDER', 'SUBSCRIPTION', 'SERVICE'] as const;
export const inputStatuses = ['READY', 'DD_NOT_APPROVED', 'CLASSIFICATION_NOT_CONFIRMED', 'SOURCE_UNAVAILABLE'] as const;
export type InputStatus = typeof inputStatuses[number];
/** 自动承保判定项（technique 8.2）：前三组是原型 02 的三个条件，另两项是前提，只在不通过时显示。 */
export const checkCodes = ['RULE_CONFIGURED', 'CRR_LOW', 'VOLUME_PROVIDED', 'CURRENCY_CONFIGURED', 'VOLUME_WITHIN_THRESHOLD',
  'MCC_NOT_NETWORK_HIGH_RISK', 'MCC_NOT_EXCLUDED', 'CLASSIFICATION_CONFIRMED'] as const;
export const checkGroups = [
  { group: 'crr', codes: ['CRR_LOW'] },
  { group: 'volume', codes: ['VOLUME_PROVIDED', 'CURRENCY_CONFIGURED', 'VOLUME_WITHIN_THRESHOLD'] },
  { group: 'mcc', codes: ['MCC_NOT_NETWORK_HIGH_RISK', 'MCC_NOT_EXCLUDED'] },
] as const;
export const prerequisiteChecks = ['RULE_CONFIGURED', 'CLASSIFICATION_CONFIRMED'] as const;
export const exposureUnavailableReasons = ['VOLUME_MISSING', 'FULFILLMENT_TYPE_MISSING', 'FULFILLMENT_DAYS_MISSING'] as const;

/** 输入快照（ops-bff 组装；提交时由 risk-decision 冻结）。 */
export interface UnderwritingInput {
  clientDisplayName: string; clientNo: string; clientType: string; jurisdictionCode: string;
  crr: Crr | null; crrMissing: boolean; ddTier: string | null; mccCode: string | null; industryCode: string | null; networkHighRiskMcc: boolean;
  expectedMonthlyVolume: string | null; volumeCurrency: string | null; expectedTicket: string | null;
  fulfillmentType: string | null; fulfillmentDays: number | null; websiteUrl: string | null; refundPolicyUrl: string | null; expectedPaymentMethods: string | null;
  sourceCaseId: string | null; sourceCaseVersion: string | null; sourceApplicationId: string | null; sourceDraftRevision: string | null;
  sourceClassificationConfirmedAt: string | null;
}
/** 承保结论；草稿时各项可空。 */
export interface UnderwritingDecision {
  observationDays: number | null; observationTransactions: number | null; requireDeposit: boolean; depositAmount: string | null; depositCurrency: string | null;
  depositBlocksCollection: boolean; suggestedSettlementDays: number | null; highRiskMcc: boolean; highRiskMccReason: string | null;
  limitNote: string | null; underwritingNote: string | null; handoverNote: string | null;
}
export interface Exposure { amount: string; currency: string; riskDays: number; settlementDaysAssumed: boolean }
export interface Underwriting {
  underwritingId: string; clientId: string; versionNo: number; status: UnderwritingStatus; decisionSource: DecisionSource | null; ruleVersionNo: number | null;
  reviewLevel: Crr; input: UnderwritingInput | null; decision: UnderwritingDecision | null;
  estimatedExposure: Exposure | null; exposureUnavailableReason: string | null;
  declineReason: string | null; reviewComment: string | null; revisionReason: string | null; supersedesUnderwritingId: string | null;
  createdBy: string; createdByLabel: string | null; submittedBy: string | null; submittedByLabel: string | null; submittedAt: string | null;
  decidedBy: string | null; decidedByLabel: string | null; decidedAt: string | null; supersededAt: string | null; version: number;
}
export interface UnderwritingSummary {
  underwritingId: string; clientId: string; clientDisplayName: string; clientNo: string; jurisdictionCode: string; clientType: string;
  crr: Crr | null; mccCode: string | null; networkHighRiskMcc: boolean; versionNo: number; status: UnderwritingStatus; reviewLevel: Crr;
  decisionSource: DecisionSource | null; ruleVersionNo: number | null; estimatedExposure: Exposure | null; exposureUnavailableReason: string | null;
  fulfillmentType: string | null; suggestedSettlementDays: number | null; requireDeposit: boolean;
  submittedBy: string | null; submittedByLabel: string | null; decidedBy: string | null; decidedByLabel: string | null; updatedAt: string;
}
export interface Statistics { effective: number; draft: number; submitted: number; approvedThisMonth: number; declinedThisMonth: number }
export interface Check { code: string; passed: boolean }
/** 预览；thresholdAmount / thresholdCurrency 为 ACTIVE 规则中流水币种的月流水阈值，没有规则或该币种无阈值时为空。 */
export interface Preview { ruleVersionNo: number | null; autoEligible: boolean; checks: Check[]; estimatedExposure: Exposure | null; exposureUnavailableReason: string | null; reviewLevel: Crr;
  thresholdAmount: string | null; thresholdCurrency: string | null }
export interface Current { effective: Underwriting | null; open: Underwriting | null; latest: Underwriting | null }
export interface WorkspaceClient { clientId: string; clientNo: string; displayName: string; clientType: string; jurisdictionCode: string | null }
export interface Workspace {
  client: WorkspaceClient; inputStatus: InputStatus; input: UnderwritingInput | null; preview: Preview | null; current: Current; canSubmit: boolean;
}

/* ---------- 解码 ---------- */

function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
type Row = Record<string, unknown>;
const record = (v: unknown): Row => { require(v !== null && typeof v === 'object' && !Array.isArray(v)); return v as Row; };
const idPattern = /^[1-9][0-9]{0,18}$/;
const isId = (v: unknown): v is string => typeof v === 'string' && idPattern.test(v);
const decimal = /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/;
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(v) && Number.isFinite(Date.parse(v));
const textOf = (v: unknown, max: number): v is string => typeof v === 'string' && [...v].length <= max;
const codeOf = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const int = (v: unknown, min: number, max: number): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= min && v <= max;
function nullable<T>(v: unknown, valid: (v: unknown) => boolean): T | null { if (v === null || v === undefined) return null; require(valid(v)); return v as T; }
/** ops-bff 追加的展示字段：缺省或 null 视为没有。 */
const label = (v: unknown) => nullable<string>(v, x => textOf(x, 255));
const oneOf = <T extends string>(values: readonly T[]) => (v: unknown): v is T => typeof v === 'string' && (values as readonly string[]).includes(v);
const bool = (v: unknown): v is boolean => typeof v === 'boolean';
/** 只校验数字字符串格式（十进制 ID / 版本）。 */
const digits = (v: unknown) => typeof v === 'string' && /^[0-9]{1,19}$/.test(v);

export function decodeExposure(value: unknown): Exposure | null {
  if (value === null || value === undefined) return null;
  const v = record(value);
  require(typeof v.amount === 'string' && decimal.test(v.amount) && typeof v.currency === 'string' && /^[A-Z]{3}$/.test(v.currency)
    && int(v.riskDays, 0, 1000) && bool(v.settlementDaysAssumed));
  return { amount: v.amount, currency: v.currency, riskDays: v.riskDays, settlementDaysAssumed: v.settlementDaysAssumed };
}

export function decodeInput(value: unknown): UnderwritingInput | null {
  if (value === null || value === undefined) return null;
  const v = record(value);
  require(textOf(v.clientDisplayName, 255) && textOf(v.clientNo, 64) && codeOf(v.clientType) && textOf(v.jurisdictionCode, 16) && bool(v.networkHighRiskMcc));
  const crr = nullable<Crr>(v.crr, oneOf(crrs));
  return {
    clientDisplayName: v.clientDisplayName, clientNo: v.clientNo, clientType: v.clientType, jurisdictionCode: v.jurisdictionCode,
    crr, crrMissing: v.crrMissing === undefined ? crr === null : (require(bool(v.crrMissing)), v.crrMissing as boolean),
    ddTier: nullable(v.ddTier, codeOf), mccCode: nullable(v.mccCode, x => typeof x === 'string' && /^[0-9]{4}$/.test(x)),
    industryCode: nullable(v.industryCode, x => textOf(x, 64)), networkHighRiskMcc: v.networkHighRiskMcc,
    expectedMonthlyVolume: nullable(v.expectedMonthlyVolume, x => typeof x === 'string' && decimal.test(x)),
    volumeCurrency: nullable(v.volumeCurrency, x => typeof x === 'string' && /^[A-Z]{3}$/.test(x)),
    expectedTicket: nullable(v.expectedTicket, x => typeof x === 'string' && decimal.test(x)),
    fulfillmentType: nullable(v.fulfillmentType, codeOf), fulfillmentDays: nullable(v.fulfillmentDays, x => int(x, 1, 365)),
    websiteUrl: nullable(v.websiteUrl, x => textOf(x, 2048)), refundPolicyUrl: nullable(v.refundPolicyUrl, x => textOf(x, 2048)),
    expectedPaymentMethods: nullable(v.expectedPaymentMethods, x => textOf(x, 512)),
    sourceCaseId: nullable(v.sourceCaseId, isId), sourceCaseVersion: nullable(v.sourceCaseVersion, digits),
    sourceApplicationId: nullable(v.sourceApplicationId, isId), sourceDraftRevision: nullable(v.sourceDraftRevision, digits),
    sourceClassificationConfirmedAt: nullable(v.sourceClassificationConfirmedAt, instant),
  };
}

export function decodeDecision(value: unknown): UnderwritingDecision | null {
  if (value === null || value === undefined) return null;
  const v = record(value);
  require(bool(v.requireDeposit) && bool(v.depositBlocksCollection) && bool(v.highRiskMcc));
  const note = (x: unknown, max: number) => nullable<string>(x, y => textOf(y, max));
  return {
    observationDays: nullable(v.observationDays, x => int(x, 1, 365)), observationTransactions: nullable(v.observationTransactions, x => int(x, 1, 100000)),
    requireDeposit: v.requireDeposit, depositAmount: nullable(v.depositAmount, x => typeof x === 'string' && decimal.test(x)),
    depositCurrency: nullable(v.depositCurrency, x => typeof x === 'string' && /^[A-Z]{3}$/.test(x)), depositBlocksCollection: v.depositBlocksCollection,
    suggestedSettlementDays: nullable(v.suggestedSettlementDays, x => int(x, 0, 90)), highRiskMcc: v.highRiskMcc,
    highRiskMccReason: note(v.highRiskMccReason, 500), limitNote: note(v.limitNote, 500), underwritingNote: note(v.underwritingNote, 2000), handoverNote: note(v.handoverNote, 500),
  };
}

export function decodeUnderwriting(value: unknown, expectedId?: string): Underwriting {
  const v = record(value);
  require(isId(v.underwritingId) && (!expectedId || v.underwritingId === expectedId) && isId(v.clientId) && int(v.versionNo, 1, 1000000)
    && oneOf(statuses)(v.status) && oneOf(crrs)(v.reviewLevel) && isId(v.createdBy) && int(v.version, 0, 2147483647));
  const reason = (x: unknown) => nullable<string>(x, y => textOf(y, 500));
  const result: Underwriting = {
    underwritingId: v.underwritingId, clientId: v.clientId, versionNo: v.versionNo, status: v.status, decisionSource: nullable(v.decisionSource, oneOf(decisionSources)),
    ruleVersionNo: nullable(v.ruleVersionNo, x => int(x, 1, 1000000)), reviewLevel: v.reviewLevel,
    input: decodeInput(v.input), decision: decodeDecision(v.decision), estimatedExposure: decodeExposure(v.estimatedExposure),
    exposureUnavailableReason: nullable(v.exposureUnavailableReason, codeOf),
    declineReason: reason(v.declineReason), reviewComment: reason(v.reviewComment), revisionReason: reason(v.revisionReason),
    supersedesUnderwritingId: nullable(v.supersedesUnderwritingId, isId),
    createdBy: v.createdBy, createdByLabel: label(v.createdByLabel), submittedBy: nullable(v.submittedBy, isId), submittedByLabel: label(v.submittedByLabel),
    submittedAt: nullable(v.submittedAt, instant), decidedBy: nullable(v.decidedBy, isId), decidedByLabel: label(v.decidedByLabel),
    decidedAt: nullable(v.decidedAt, instant), supersededAt: nullable(v.supersededAt, instant), version: v.version,
  };
  require(result.status !== 'DECLINED' || result.declineReason !== null);
  require(result.decisionSource !== 'AUTO_RULE' || result.ruleVersionNo !== null);
  return result;
}

export function decodeSummary(value: unknown): UnderwritingSummary {
  const v = record(value);
  require(isId(v.underwritingId) && isId(v.clientId) && textOf(v.clientDisplayName, 255) && textOf(v.clientNo, 64) && textOf(v.jurisdictionCode, 16)
    && codeOf(v.clientType) && bool(v.networkHighRiskMcc) && int(v.versionNo, 1, 1000000) && oneOf(statuses)(v.status) && oneOf(crrs)(v.reviewLevel)
    && bool(v.requireDeposit) && instant(v.updatedAt));
  return {
    underwritingId: v.underwritingId, clientId: v.clientId, clientDisplayName: v.clientDisplayName, clientNo: v.clientNo, jurisdictionCode: v.jurisdictionCode,
    clientType: v.clientType, crr: nullable(v.crr, oneOf(crrs)), mccCode: nullable(v.mccCode, x => typeof x === 'string' && /^[0-9]{4}$/.test(x)),
    networkHighRiskMcc: v.networkHighRiskMcc, versionNo: v.versionNo, status: v.status, reviewLevel: v.reviewLevel,
    decisionSource: nullable(v.decisionSource, oneOf(decisionSources)), ruleVersionNo: nullable(v.ruleVersionNo, x => int(x, 1, 1000000)),
    estimatedExposure: decodeExposure(v.estimatedExposure), exposureUnavailableReason: nullable(v.exposureUnavailableReason, codeOf),
    fulfillmentType: nullable(v.fulfillmentType, codeOf), suggestedSettlementDays: nullable(v.suggestedSettlementDays, x => int(x, 0, 90)),
    requireDeposit: v.requireDeposit, submittedBy: nullable(v.submittedBy, isId), submittedByLabel: label(v.submittedByLabel),
    decidedBy: nullable(v.decidedBy, isId), decidedByLabel: label(v.decidedByLabel), updatedAt: v.updatedAt,
  };
}

export function decodeSummaryPage(value: unknown): PagingResponseBody<UnderwritingSummary> {
  const v = record(value);
  require(Array.isArray(v.items) && v.items.length <= 200 && int(v.page, 1, 100000) && int(v.size, 1, 200) && int(v.total, 0, Number.MAX_SAFE_INTEGER));
  return { items: v.items.map(decodeSummary), page: v.page, size: v.size, total: v.total };
}

/** 版本历史（U4）：`version_no` 倒序，同一 Client。 */
export function decodeHistory(value: unknown, clientId: string): Underwriting[] {
  const v = record(value);
  require(Array.isArray(v.items) && v.items.length <= 100);
  const items = v.items.map(item => decodeUnderwriting(item));
  require(items.every((item, index) => item.clientId === clientId && (index === 0 || items[index - 1].versionNo > item.versionNo)));
  return items;
}

export function decodeStatistics(value: unknown): Statistics {
  const v = record(value);
  const keys = ['effective', 'draft', 'submitted', 'approvedThisMonth', 'declinedThisMonth'] as const;
  require(keys.every(key => int(v[key], 0, Number.MAX_SAFE_INTEGER)));
  return { effective: v.effective as number, draft: v.draft as number, submitted: v.submitted as number,
    approvedThisMonth: v.approvedThisMonth as number, declinedThisMonth: v.declinedThisMonth as number };
}

export function decodePreview(value: unknown): Preview {
  const v = record(value);
  require(bool(v.autoEligible) && Array.isArray(v.checks) && v.checks.length <= 32 && oneOf(crrs)(v.reviewLevel));
  const checks = v.checks.map(item => { const c = record(item); require(codeOf(c.code) && bool(c.passed)); return { code: c.code, passed: c.passed }; });
  require(new Set(checks.map(check => check.code)).size === checks.length);
  // 判定一致性：可自动承保当且仅当返回的判定项全部通过（空判定不能算可自动）。
  require(v.autoEligible === (checks.length > 0 && checks.every(check => check.passed)));
  const thresholdAmount = nullable<string>(v.thresholdAmount, x => typeof x === 'string' && decimal.test(x));
  const thresholdCurrency = nullable<string>(v.thresholdCurrency, x => typeof x === 'string' && /^[A-Z]{3}$/.test(x));
  require((thresholdAmount === null) === (thresholdCurrency === null));
  return { ruleVersionNo: nullable(v.ruleVersionNo, x => int(x, 1, 1000000)), autoEligible: v.autoEligible, checks,
    estimatedExposure: decodeExposure(v.estimatedExposure), exposureUnavailableReason: nullable(v.exposureUnavailableReason, codeOf), reviewLevel: v.reviewLevel,
    thresholdAmount, thresholdCurrency };
}

function decodeCurrent(value: unknown, clientId: string): Current {
  const v = record(value);
  const one = (x: unknown) => { const item = x === null || x === undefined ? null : decodeUnderwriting(x); require(!item || item.clientId === clientId); return item; };
  const current = { effective: one(v.effective), open: one(v.open), latest: one(v.latest) };
  require(!current.effective || current.effective.status === 'APPROVED');
  require(!current.open || current.open.status === 'DRAFT' || current.open.status === 'SUBMITTED');
  return current;
}

export function decodeWorkspace(value: unknown, clientId: string): Workspace {
  const v = record(value); const c = record(v.client);
  require(c.clientId === clientId && textOf(c.clientNo, 64) && textOf(c.displayName, 255) && codeOf(c.clientType)
    && oneOf(inputStatuses)(v.inputStatus) && bool(v.canSubmit));
  const workspace: Workspace = {
    client: { clientId, clientNo: c.clientNo, displayName: c.displayName, clientType: c.clientType, jurisdictionCode: nullable(c.jurisdictionCode, x => textOf(x, 16)) },
    inputStatus: v.inputStatus, input: decodeInput(v.input), preview: v.preview === null || v.preview === undefined ? null : decodePreview(v.preview),
    current: decodeCurrent(v.current, clientId), canSubmit: v.canSubmit,
  };
  require(!workspace.canSubmit || (workspace.inputStatus === 'READY' && workspace.input !== null));
  return workspace;
}

/* ---------- 页面模型 ---------- */

export const hasButton = (resources: readonly ConsoleResource[], code: string) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === code);
export const hasMenu = (resources: readonly ConsoleResource[], componentKey: string) => resources.some(r => r.resourceType === 'MENU' && r.componentKey === componentKey);
export interface Permissions { manage: boolean; approve: boolean }
export const permissionsOf = (resources: readonly ConsoleResource[]): Permissions =>
  ({ manage: hasButton(resources, underwritingButtons.manage), approve: hasButton(resources, underwritingButtons.approve) });

/** 列表页签：全部 + 五个状态（已作废 = SUPERSEDED）。 */
export const listTabs = ['ALL', ...statuses] as const;
export type ListTab = typeof listTabs[number];
export interface ListQuery { tab: ListTab; keyword: string; crr: string; decisionSource: string; pendingReviewForMe: boolean }
export const emptyListQuery: ListQuery = { tab: 'ALL', keyword: '', crr: '', decisionSource: '', pendingReviewForMe: false };
/** 列表 Query 参数（U1）：空值不发送；"待我复核"由 ops-bff 换成当前账号，并只列 SUBMITTED。 */
export function listFilters(query: ListQuery): Record<string, string> {
  const filters: Record<string, string> = {};
  if (query.pendingReviewForMe) filters.pendingReviewForMe = 'true';
  else if (query.tab !== 'ALL') filters.status = query.tab;
  if (query.keyword.trim()) filters.keyword = query.keyword.trim();
  if (query.crr) filters.crr = query.crr;
  if (query.decisionSource) filters.decisionSource = query.decisionSource;
  return filters;
}
/** 地址栏 → 列表查询；未知值回落默认。 */
export function parseListQuery(search: URLSearchParams): ListQuery {
  const tab = search.get('status') ?? 'ALL';
  const crr = search.get('crr') ?? ''; const source = search.get('decisionSource') ?? '';
  return { tab: (listTabs as readonly string[]).includes(tab) ? tab as ListTab : 'ALL', keyword: (search.get('keyword') ?? '').slice(0, 100),
    crr: (crrs as readonly string[]).includes(crr) ? crr : '', decisionSource: (decisionSources as readonly string[]).includes(source) ? source : '',
    pendingReviewForMe: search.get('pendingReviewForMe') === 'true' };
}

/** 十进制字符串按千分位分组；fraction 指定时补齐或截断到该位数（截断只用于显示，不做舍入，金额由服务端算好）。 */
export function groupDecimal(value: string, fraction?: number): string {
  const [whole, part = ''] = value.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const decimals = fraction === undefined ? part.replace(/0+$/, '') : part.padEnd(fraction, '0').slice(0, fraction);
  return decimals ? `${grouped}.${decimals}` : grouped;
}
export const money = (amount: string, currency: string, fraction?: number) => `${currency} ${groupDecimal(amount, fraction)}`;

/**
 * 估算敞口的风险天数构成（B10）：用于公式说明；数值以服务端 riskDays 为准
 * <li>PRE_ORDER：交付天数 + T+N；SUBSCRIPTION：30 + T+N；INSTANT / SERVICE：T+N；T+N 未填按 0。</li>
 */
export function riskDayParts(fulfillmentType: string | null, fulfillmentDays: number | null, settlementDays: number | null): number[] | null {
  const n = settlementDays ?? 0;
  switch (fulfillmentType) {
    case 'PRE_ORDER': return fulfillmentDays === null ? null : [fulfillmentDays, n];
    case 'SUBSCRIPTION': return [30, n];
    case 'INSTANT': case 'SERVICE': return [n];
    default: return null;
  }
}
/** 公式中风险天数部分："(45 + 7)" 或 "7"。 */
export function riskDaysText(parts: readonly number[] | null, riskDays: number): string {
  if (!parts || parts.reduce((sum, part) => sum + part, 0) !== riskDays) return String(riskDays);
  return parts.length > 1 ? `(${parts.join(' + ')})` : String(parts[0]);
}

export type CheckLine = { key: string; passed: boolean; failedCode: string | null };
/**
 * 判定清单：三个条件按组显示（组内任一不通过即不通过，原因取第一个未通过项）；前提项只在不通过时显示
 * <li>服务端未返回某组的任何判定项时该组不显示（例如未配置规则时只显示前提）。</li>
 */
export function checkLines(checks: readonly Check[]): CheckLine[] {
  const byCode = new Map(checks.map(check => [check.code, check.passed]));
  const prerequisites = prerequisiteChecks.filter(code => byCode.get(code) === false).map(code => ({ key: code, passed: false, failedCode: code }));
  const groups = checkGroups.flatMap(({ group, codes }) => {
    const present = codes.filter(code => byCode.has(code));
    if (!present.length) return [];
    const failed = present.find(code => byCode.get(code) === false) ?? null;
    return [{ key: group, passed: failed === null, failedCode: failed }];
  });
  const known = new Set<string>(checkCodes);
  const unknown = checks.filter(check => !known.has(check.code) && !check.passed).map(check => ({ key: check.code, passed: false, failedCode: check.code }));
  return [...prerequisites, ...groups, ...unknown];
}

/** 结论表单值（字符串输入原样保留，提交时规范化）。 */
export interface DecisionForm {
  observationDays: string; observationTransactions: string; requireDeposit: boolean; depositAmount: string; depositCurrency: string;
  depositBlocksCollection: boolean; suggestedSettlementDays: string; highRiskMcc: boolean; highRiskMccReason: string;
  limitNote: string; underwritingNote: string; handoverNote: string;
}
/** 新建默认值（B5）：观察期 30 天 / 100 笔，不要求保证金，卡组织登记按 MCC 预勾。 */
export function defaultDecisionForm(networkHighRiskMcc: boolean, currency: string | null): DecisionForm {
  return { observationDays: '30', observationTransactions: '100', requireDeposit: false, depositAmount: '', depositCurrency: currency ?? 'USD',
    depositBlocksCollection: false, suggestedSettlementDays: '', highRiskMcc: networkHighRiskMcc, highRiskMccReason: '', limitNote: '', underwritingNote: '', handoverNote: '' };
}
/** 草稿已有结论时以草稿为准，空项回落默认值。 */
export function decisionFormOf(decision: UnderwritingDecision | null, networkHighRiskMcc: boolean, currency: string | null): DecisionForm {
  const base = defaultDecisionForm(networkHighRiskMcc, currency);
  if (!decision) return base;
  const str = (value: number | string | null, fallback: string) => value === null ? fallback : String(value);
  return { observationDays: str(decision.observationDays, base.observationDays), observationTransactions: str(decision.observationTransactions, base.observationTransactions),
    requireDeposit: decision.requireDeposit, depositAmount: decision.depositAmount ?? '', depositCurrency: decision.depositCurrency ?? base.depositCurrency,
    depositBlocksCollection: decision.depositBlocksCollection, suggestedSettlementDays: str(decision.suggestedSettlementDays, ''),
    highRiskMcc: decision.highRiskMcc, highRiskMccReason: decision.highRiskMccReason ?? '', limitNote: decision.limitNote ?? '',
    underwritingNote: decision.underwritingNote ?? '', handoverNote: decision.handoverNote ?? '' };
}

export type DecisionField = keyof DecisionForm;
export type DecisionIssue = 'required' | 'observationDaysRange' | 'observationTransactionsRange' | 'depositAmount' | 'depositCurrency'
  | 'settlementDaysRange' | 'reasonRequired' | 'tooLong';
const wholeNumber = (value: string, min: number, max: number) => /^[0-9]{1,6}$/.test(value.trim()) && Number(value) >= min && Number(value) <= max;
const positiveAmount = (value: string) => decimal.test(value.trim()) && /[1-9]/.test(value);
/** 结论校验（B5）：返回各字段的问题；草稿保存只校验填了的值格式，提交校验必填。 */
export function decisionIssues(form: DecisionForm, networkHighRiskMcc: boolean, mode: 'save' | 'submit'): Partial<Record<DecisionField, DecisionIssue>> {
  const issues: Partial<Record<DecisionField, DecisionIssue>> = {};
  const check = (field: DecisionField, value: string, valid: boolean, issue: DecisionIssue) => {
    if (!value.trim()) { if (mode === 'submit') issues[field] = 'required'; } else if (!valid) issues[field] = issue;
  };
  check('observationDays', form.observationDays, wholeNumber(form.observationDays, 1, 365), 'observationDaysRange');
  check('observationTransactions', form.observationTransactions, wholeNumber(form.observationTransactions, 1, 100000), 'observationTransactionsRange');
  if (form.requireDeposit) {
    check('depositAmount', form.depositAmount, positiveAmount(form.depositAmount), 'depositAmount');
    check('depositCurrency', form.depositCurrency, /^[A-Z]{3}$/.test(form.depositCurrency.trim()), 'depositCurrency');
  }
  if (form.suggestedSettlementDays.trim() && !wholeNumber(form.suggestedSettlementDays, 0, 90)) issues.suggestedSettlementDays = 'settlementDaysRange';
  if (form.highRiskMcc !== networkHighRiskMcc && !form.highRiskMccReason.trim()) issues.highRiskMccReason = 'reasonRequired';
  const limits: [DecisionField, number][] = [['highRiskMccReason', 500], ['limitNote', 500], ['underwritingNote', 2000], ['handoverNote', 500]];
  for (const [field, max] of limits) if ([...(form[field] as string)].length > max) issues[field] = 'tooLong';
  return issues;
}

/** 表单 → 请求体（UnderwritingDecisionBody）：空串为 null；不要求保证金时金额、币种为空且不阻塞收款；与默认一致时不带理由。 */
export function decisionBody(form: DecisionForm, networkHighRiskMcc: boolean): UnderwritingDecision {
  const optional = (value: string) => value.trim() ? value.trim() : null;
  const number = (value: string) => value.trim() ? Number(value.trim()) : null;
  return {
    observationDays: number(form.observationDays), observationTransactions: number(form.observationTransactions),
    requireDeposit: form.requireDeposit, depositAmount: form.requireDeposit ? optional(form.depositAmount) : null,
    depositCurrency: form.requireDeposit ? optional(form.depositCurrency) : null, depositBlocksCollection: form.requireDeposit && form.depositBlocksCollection,
    suggestedSettlementDays: number(form.suggestedSettlementDays), highRiskMcc: form.highRiskMcc,
    highRiskMccReason: form.highRiskMcc !== networkHighRiskMcc ? optional(form.highRiskMccReason) : null,
    limitNote: optional(form.limitNote), underwritingNote: optional(form.underwritingNote), handoverNote: optional(form.handoverNote),
  };
}
/** T+N 输入 → 预览参数：只有 0—90 的整数才发送，其他为空（按 0 计并标注）。 */
export function settlementDaysOf(value: string): number | null {
  return wholeNumber(value, 0, 90) ? Number(value.trim()) : null;
}

/** 复核区：本人提交时禁用（四眼）；没有复核权限不显示；只对 SUBMITTED。 */
export function reviewAccess(record: Pick<Underwriting, 'status' | 'submittedBy'>, actorId: string, canApprove: boolean): 'review' | 'self' | 'none' {
  if (record.status !== 'SUBMITTED' || !canApprove) return 'none';
  return record.submittedBy === actorId ? 'self' : 'review';
}
/** 复核意见：拒绝时必填，≤ 500。 */
export function reviewCommentIssue(decision: 'APPROVE' | 'DECLINE', comment: string): 'required' | 'tooLong' | null {
  if ([...comment].length > 500) return 'tooLong';
  return decision === 'DECLINE' && !comment.trim() ? 'required' : null;
}
/** 修订：当前有效的 APPROVED、有维护权限、该 Client 没有进行中的版本。 */
export function canRevise(record: Pick<Underwriting, 'status'>, history: readonly Pick<Underwriting, 'status'>[], canManage: boolean): boolean {
  return canManage && record.status === 'APPROVED' && !history.some(item => item.status === 'DRAFT' || item.status === 'SUBMITTED');
}
export function openVersion<T extends Pick<Underwriting, 'status'>>(history: readonly T[]): T | null {
  return history.find(item => item.status === 'DRAFT' || item.status === 'SUBMITTED') ?? null;
}

/**
 * 新建页的去向：已有进行中版本 → 跳到该版本；已有有效结论 → 提示走修订；否则按预览决定自动 / 人工
 * <li>无维护权限时只读提示；输入未就绪（尽调未批准等）时仍可人工填写草稿，但不能提交。</li>
 */
export type NewPageMode = { kind: 'redirect'; underwritingId: string } | { kind: 'effective'; underwritingId: string; versionNo: number }
  | { kind: 'readonly' } | { kind: 'auto' } | { kind: 'manual' };
export function newPageMode(workspace: Workspace, canManage: boolean, preferManual: boolean): NewPageMode {
  const { open, effective } = workspace.current;
  if (open) return { kind: 'redirect', underwritingId: open.underwritingId };
  if (effective) return { kind: 'effective', underwritingId: effective.underwritingId, versionNo: effective.versionNo };
  if (!canManage) return { kind: 'readonly' };
  return workspace.preview?.autoEligible && workspace.canSubmit && !preferManual ? { kind: 'auto' } : { kind: 'manual' };
}

/** 人工态的按钮（原型 03）：HIGH 只能提交复核（不能拒绝）；LOW / MEDIUM 提交即决定，可拒绝。 */
export function submitActions(reviewLevel: Crr): { submitKey: 'submit' | 'submitForReview'; canDecline: boolean } {
  return reviewLevel === 'HIGH' ? { submitKey: 'submitForReview', canDecline: false } : { submitKey: 'submit', canDecline: true };
}

/** 已知命令错误码（technique 8.1）→ 文案键；未知码由页面显示服务端 detail。 */
export const knownErrors = ['RISK_UNDERWRITING_NOT_FOUND', 'RISK_UNDERWRITING_OPEN_EXISTS', 'RISK_UNDERWRITING_VERSION_CONFLICT', 'RISK_UNDERWRITING_STATE_INVALID',
  'RISK_UNDERWRITING_SELF_REVIEW', 'RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW', 'RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE', 'CONCURRENT_REQUEST'] as const;

/** 422 字段错误路径（`decision.depositAmount`）→ 表单字段名；不属于结论的字段返回 null。 */
export function decisionFieldOf(path: string): DecisionField | null {
  const field = path.replace(/^decision\./, '');
  return path.startsWith('decision.') && field in defaultDecisionForm(false, null) ? field as DecisionField : null;
}

export const isUnderwritingId = (value: string) => idPattern.test(value);
/** 期望支付方式：规范化逗号串 → 去重代码。 */
export const paymentMethodCodes = (value: string | null) => value ? [...new Set(value.split(',').map(code => code.trim()).filter(Boolean))] : [];
