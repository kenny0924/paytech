import { ApiError } from '../api/session.ts';

/**
 * 财务 → 税务查询（AC-13、AC-14）的页面模型、解码与纯计算
 * <li>BFF：GET /tax/v1/orders/{orderNo} 返回页面模型；POST /tax/v1/orders/{orderNo}/documents/{documentId}/download 签发 60 秒地址。</li>
 * <li>状态与分类为稳定 Code，界面文案全部取 tax.* 语言包键；解码严格校验，未知 Code 与缺失字段按上游响应无效（502）失败关闭。</li>
 * <li>"最近查询"只保存在本浏览器 localStorage；不可访问时静默降级为空。</li>
 */
export const taxPaths = {
  order: (orderNo: string) => `/tax/v1/orders/${encodeURIComponent(orderNo)}`,
  download: (orderNo: string, documentId: string) =>
    `/tax/v1/orders/${encodeURIComponent(orderNo)}/documents/${encodeURIComponent(documentId)}/download`,
};

/** 与 tax 合同一致的公开订单号格式。 */
export const orderNoPattern = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;
export const exampleOrderNo = 'ORD-20261029-000184';

export const pageStatuses = ['NOT_MANAGED', 'BLOCKED', 'ESTIMATED', 'QUOTED', 'REGISTERED', 'ISSUED'] as const;
export const stepKeys = ['QUOTE', 'RECALCULATION', 'PAYMENT', 'REGISTRATION', 'REPORTING'] as const;
export const stepStates = ['DONE', 'BLOCKED', 'ATTENTION', 'PENDING', 'NOT_APPLICABLE', 'UNTRACKED'] as const;
export const calloutKinds = ['CLIENT_RESPONSIBLE', 'PAYMENT_BLOCKED', 'REPORTING_FX_MISSING', 'DOCUMENT_PENDING', 'REGISTERED',
  'ESTIMATE_ONLY', 'AWAITING_PAYMENT'] as const;
export const calloutTones = ['INFO', 'SUCCESS', 'WARNING', 'DANGER', 'NEUTRAL'] as const;
export const calculationStages = ['PROVISIONAL', 'FINAL'] as const;
export const calculationStatuses = ['READY', 'BLOCKED', 'NOT_MANAGED'] as const;

export type PageStatus = typeof pageStatuses[number];
export type StepKey = typeof stepKeys[number];
export type StepState = typeof stepStates[number];
export type CalloutKind = typeof calloutKinds[number];

export interface TaxStep { key: StepKey; state: StepState; amount: string | null; countryCode: string | null; documentNumber: string | null;
  reportingCurrency: string | null; occurredAt: string | null }
export interface TaxCallout { kind: CalloutKind; tone: typeof calloutTones[number]; taxAmount: string | null; reportingCurrency: string | null;
  expiresAt: string | null }
export interface TaxAmounts { calculationId: string; revision: number; stage: string; status: string; taxAmountSource: string; netAmount: string | null;
  taxAmount: string | null; grossAmount: string | null; expiresAt: string | null }
export interface TaxExplanation { mixed: boolean; jurisdictionCode: string | null; taxType: string | null; treatment: string | null; rate: string | null;
  evidenceType: string | null; countryCode: string | null; schemeCode: string | null; packageCode: string | null; policyRevision: number | null }
export interface TaxLine { lineNo: number; lineRef: string; categoryCode: string; description: string | null; quantity: string; netAmount: string | null; taxAmount: string | null;
  rate: string | null; treatment: string | null; ruleCode: string | null; componentCount: number }
export interface TaxQuote { calculationId: string; revision: number; stage: string; status: string; current: boolean; taxAmount: string | null;
  evidenceType: string | null; countryCode: string | null; previousCountryCode: string | null; taxDelta: string | null; calculatedAt: string }
export interface TaxBlockReason { code: string; path: string | null; lineNo: number | null; categoryCode: string | null; countryCode: string | null }
export interface TaxMatching { evidenceType: string | null; countryCode: string | null; marketMatched: boolean; policyMatched: boolean }
export interface TaxReceipt { documentId: string; documentKind: string; documentNumber: string | null; issueStatus: string; issuedAt: string | null;
  downloadable: boolean }
export interface TaxRegistration { transactionId: string; registeredAt: string; taxPointDate: string; legalTimezone: string | null;
  sourceFactType: string; sourceFactId: string; calculationRevision: number }
export interface TaxReporting { status: string; blockReason: string | null; filingFrequency: string | null; periodStart: string | null;
  periodEnd: string | null; filingDueDate: string | null; taxAmount: string | null; reportingCurrency: string | null; reportingTaxAmount: string | null }
export interface TaxBasis { packageCode: string | null; policyRevision: number | null; jurisdictionCode: string | null; schemeCode: string | null;
  marketEnabled: boolean; roundingMethod: string | null; roundingScale: number | null }
export interface TaxNotManaged { reason: string | null; calculationId: string; businessMode: string; taxSubjectPartyId: string | null; buyerClientId: string }
export interface TaxOrderPage {
  orderNo: string; pageStatus: PageStatus; businessMode: string; buyerClientId: string; entityNo: string; taxSubjectPartyId: string | null; currency: string;
  /** ManagedBy Code（SUBJECT_HOLDER、PLATFORM_OPERATIONS）；没有税务注册时为 null。 */
  managedBy: string | null;
  /** BFF 从 client-master 聚合的名称；读取失败为 null，页面显示 ID。 */
  buyerClientName: string | null; entityName: string | null; taxSubjectName: string | null;
  steps: TaxStep[]; callout: TaxCallout; amounts: TaxAmounts; explanation: TaxExplanation | null; lines: TaxLine[]; quotes: TaxQuote[];
  blockReasons: TaxBlockReason[]; matching: TaxMatching | null; receipt: TaxReceipt | null; registration: TaxRegistration | null;
  reporting: TaxReporting | null; basis: TaxBasis | null; notManaged: TaxNotManaged | null;
}
export interface TaxDownload { documentId: string; documentNumber: string | null; url: string; expiresAt: string }

function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
const object = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const text = (v: unknown, max = 256): v is string => typeof v === 'string' && v.length > 0 && v.length <= max;
const textOrNull = (v: unknown, max = 256) => v === null || v === undefined || text(v, max);
const decimal = (v: unknown) => typeof v === 'string' && /^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(v);
const decimalOrNull = (v: unknown) => v === null || v === undefined || decimal(v);
const id = (v: unknown) => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v);
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const instantOrNull = (v: unknown) => v === null || v === undefined || instant(v);
const intOrNull = (v: unknown) => v === null || v === undefined || Number.isInteger(v);
const oneOf = <T extends readonly string[]>(values: T, v: unknown): v is T[number] => typeof v === 'string' && values.includes(v);
const list = (v: unknown, max = 500): v is unknown[] => Array.isArray(v) && v.length <= max;

/** 页面模型：订单号须与请求一致，状态、步骤、提示与报价 Code 都在已知集合内。 */
export function decodeTaxOrderPage(value: unknown, orderNo: string): TaxOrderPage {
  const v = value as TaxOrderPage;
  require(object(v) && v.orderNo === orderNo && oneOf(pageStatuses, v.pageStatus) && text(v.currency, 3)
    && textOrNull(v.businessMode, 32) && textOrNull(v.entityNo, 5) && textOrNull(v.buyerClientName, 512) && textOrNull(v.entityName, 512)
    && textOrNull(v.taxSubjectName, 512) && textOrNull(v.managedBy, 32));
  require(list(v.steps, 5) && v.steps.length >= 3 && v.steps.every(step => object(step) && oneOf(stepKeys, step.key)
    && oneOf(stepStates, step.state) && decimalOrNull(step.amount) && instantOrNull(step.occurredAt)));
  require(object(v.callout) && oneOf(calloutKinds, v.callout.kind) && oneOf(calloutTones, v.callout.tone)
    && decimalOrNull(v.callout.taxAmount) && instantOrNull(v.callout.expiresAt));
  require(object(v.amounts) && id(v.amounts.calculationId) && Number.isInteger(v.amounts.revision) && oneOf(calculationStages, v.amounts.stage)
    && oneOf(calculationStatuses, v.amounts.status) && decimalOrNull(v.amounts.netAmount) && decimalOrNull(v.amounts.taxAmount)
    && decimalOrNull(v.amounts.grossAmount));
  require(list(v.lines) && v.lines.every(line => object(line) && Number.isInteger(line.lineNo) && text(line.lineRef) && text(line.categoryCode, 64) && textOrNull(line.description, 255)
    && decimalOrNull(line.netAmount) && decimalOrNull(line.taxAmount) && decimalOrNull(line.rate)));
  require(list(v.quotes, 100) && v.quotes.length > 0 && v.quotes.every(quote => object(quote) && id(quote.calculationId)
    && Number.isInteger(quote.revision) && oneOf(calculationStages, quote.stage) && oneOf(calculationStatuses, quote.status)
    && typeof quote.current === 'boolean' && decimalOrNull(quote.taxAmount) && decimalOrNull(quote.taxDelta) && instant(quote.calculatedAt)));
  require(list(v.blockReasons, 50) && v.blockReasons.every(reason => object(reason) && text(reason.code, 64) && intOrNull(reason.lineNo)));
  require(v.matching === null || v.matching === undefined || object(v.matching));
  require(v.receipt === null || v.receipt === undefined || (object(v.receipt) && id(v.receipt.documentId) && text(v.receipt.issueStatus, 32)
    && typeof v.receipt.downloadable === 'boolean' && instantOrNull(v.receipt.issuedAt)));
  require(v.registration === null || v.registration === undefined || (object(v.registration) && instant(v.registration.registeredAt)
    && Number.isInteger(v.registration.calculationRevision)));
  require(v.reporting === null || v.reporting === undefined || (object(v.reporting) && text(v.reporting.status, 32)
    && decimalOrNull(v.reporting.reportingTaxAmount)));
  require(v.explanation === null || v.explanation === undefined || (object(v.explanation) && decimalOrNull(v.explanation.rate)));
  return { ...v, managedBy: v.managedBy ?? null, lines: v.lines.map(line => ({ ...line, description: line.description ?? null })),
    buyerClientName: v.buyerClientName ?? null, entityName: v.entityName ?? null, taxSubjectName: v.taxSubjectName ?? null,
    explanation: v.explanation ?? null, matching: v.matching ?? null, receipt: v.receipt ?? null, registration: v.registration ?? null,
    reporting: v.reporting ?? null, basis: v.basis ?? null, notManaged: v.notManaged ?? null };
}

/** 下载授权：单据须与请求一致，地址必须为 HTTPS。 */
export function decodeTaxDownload(value: unknown, documentId: string): TaxDownload {
  const v = value as TaxDownload;
  require(object(v) && v.documentId === documentId && instant(v.expiresAt) && typeof v.url === 'string' && v.url.length <= 8192
    && v.url.startsWith('https://'));
  return { documentId: v.documentId, documentNumber: v.documentNumber ?? null, url: v.url, expiresAt: v.expiresAt };
}

/** 去掉首尾空白并校验格式；不合法返回 null。 */
export function normalizeOrderNo(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim();
  return orderNoPattern.test(trimmed) ? trimmed : null;
}

/** 税率小数（0.15）转为百分比数值（15）；非法为 null。按 Intl percent 显示由调用方完成。 */
export function rateNumber(rate: string | null | undefined): number | null {
  if (!rate || !decimal(rate)) return null;
  return Number(rate);
}

/** 带符号的变化额：正数补 "+"。 */
export function signedAmount(delta: string): string {
  return delta.startsWith('-') || /^0(\.0+)?$/.test(delta) ? delta : '+' + delta;
}

/** 请求编号缩写（前 4 位…后 4 位），用于下载失败提示中的"复制请求编号"。 */
export function shortRequestId(requestId: string): string {
  return requestId.length <= 12 ? requestId : `${requestId.slice(0, 4)}…${requestId.slice(-4)}`;
}

/**
 * 步骤事实文案键 step.<KEY>.<STATE>；不由平台计税用 step.notManaged. 前缀
 * <li>已被后续报价取代的阻断报价（步骤 DONE，但该步骤依据的报价为 BLOCKED、没有税额）用 step.<KEY>.DONE_BLOCKED，显示"暂估 · 阻断 · 国家"，不显示"— USD"。</li>
 * <li>建单报价步骤对应修订号最小的报价；资料提交重算步骤对应修订号最大的 FINAL 报价（与 BFF 取值一致）。</li>
 */
export function stepFactKey(step: TaxStep, page: TaxOrderPage): string {
  if (page.pageStatus === 'NOT_MANAGED') return `step.notManaged.${step.key}.${step.state}`;
  if (step.state === 'DONE' && quoteOfStep(step, page)?.status === 'BLOCKED') return `step.${step.key}.DONE_BLOCKED`;
  return `step.${step.key}.${step.state}`;
}

/** 报价步骤依据的报价；非报价步骤或没有对应报价为 undefined。 */
function quoteOfStep(step: TaxStep, page: TaxOrderPage): TaxQuote | undefined {
  const byRevision = [...page.quotes].sort((a, b) => a.revision - b.revision);
  if (step.key === 'QUOTE') return byRevision[0];
  if (step.key === 'RECALCULATION') return byRevision.filter(quote => quote.stage === 'FINAL').at(-1);
  return undefined;
}

/** 进度条步骤序号与当前停留位置：第一个非 DONE 的步骤；全部完成为 -1。 */
export function activeStepIndex(steps: TaxStep[]): number {
  return steps.findIndex(step => step.state !== 'DONE');
}

/** 阻断原因的处理方：市场准入归平台运营（或 Partner），品类归 Client，证据归消费者，其余归税务运营。 */
export function reasonOwner(code: string): 'PLATFORM' | 'CLIENT' | 'CONSUMER' | 'TAX_OPERATIONS' {
  if (code === 'MARKET_NOT_ENABLED') return 'PLATFORM';
  if (code === 'UNKNOWN_TAX_CATEGORY') return 'CLIENT';
  if (code === 'MISSING_EVIDENCE') return 'CONSUMER';
  return 'TAX_OPERATIONS';
}

/** 最近查询的一条：只保存页面已显示的非敏感摘要。 */
export interface RecentSearch { orderNo: string; buyerClientId: string; buyerClientName?: string | null; countryCode: string | null; businessMode: string; pageStatus: PageStatus;
  taxAmount: string | null; taxAmountSource: string; currency: string }
export const recentSearchKey = 'paytech.operations.tax.recent.v1';
export const recentSearchLimit = 5;
type RecentStorage = Pick<Storage, 'getItem' | 'setItem'>;

/** 浏览器 localStorage；不可访问时为 undefined。 */
export function browserStorage(): RecentStorage | undefined {
  try { return globalThis.localStorage ?? undefined; } catch { return undefined; }
}

/** 从页面模型生成最近查询摘要；国家取当前报价作为依据的证据国家。 */
export function recentFrom(page: TaxOrderPage): RecentSearch {
  const current = page.quotes.find(quote => quote.current) ?? page.quotes[0];
  return { orderNo: page.orderNo, buyerClientId: page.buyerClientId, buyerClientName: page.buyerClientName, countryCode: current?.countryCode ?? null, businessMode: page.businessMode,
    pageStatus: page.pageStatus, taxAmount: page.amounts.taxAmount, taxAmountSource: page.amounts.taxAmountSource, currency: page.currency };
}

/** 读取最近查询；存储不可用、内容损坏或格式不符时返回空列表。 */
export function readRecentSearches(storage: RecentStorage | undefined = browserStorage()): RecentSearch[] {
  try {
    const raw = storage?.getItem(recentSearchKey);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((item): item is RecentSearch => object(item) && normalizeOrderNo(item.orderNo as string) === item.orderNo
      && oneOf(pageStatuses, item.pageStatus) && typeof item.currency === 'string').slice(0, recentSearchLimit);
  } catch {
    return [];
  }
}

/** 记一次成功查询：同一订单移到最前，最多保留 5 条；写入失败静默忽略。返回新列表。 */
export function rememberSearch(entry: RecentSearch, storage: RecentStorage | undefined = browserStorage()): RecentSearch[] {
  const next = [entry, ...readRecentSearches(storage).filter(item => item.orderNo !== entry.orderNo)].slice(0, recentSearchLimit);
  try { storage?.setItem(recentSearchKey, JSON.stringify(next)); } catch { /* 只是便利功能 */ }
  return next;
}
