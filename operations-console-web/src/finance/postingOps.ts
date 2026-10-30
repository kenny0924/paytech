import { ApiError } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import { decimalIdPattern, ledgerEntityTypes, replaceQuery } from './finance.ts';
import type { LedgerEntityType, PostingSourceType } from './finance.ts';

/** 明细账运维（technique 7.3）：B1 进度、B2 批次浏览、B3 追平、B4 重建；浏览器路径 = /api/operations + 下列路径。 */
export const postingOpsPaths = {
  progress: (ledgerBookId: string) => `/finance/posting-progress/v1/ledger-books/${ledgerBookId}`,
  streamPostings: (streamId: string, fromSequence: string, limit: number) =>
    `/finance/posting-streams/v1/${streamId}/postings?${new URLSearchParams({ fromSequence, limit: String(limit) })}`,
  catchUp: (streamId: string) => `/finance/posting-streams/v1/${streamId}/catch-up`,
  rebuild: '/finance/posting-rebuilds/v1',
} as const;

/** 页面路由：进度总览与某条流的批次浏览子页（menu.ts 登记子路由）。 */
export const postingOpsRoute = '/finance/posting-ops';
export const postingStreamRoute = (streamId: string) => `${postingOpsRoute}/streams/${streamId}`;

/** 两个执行类按钮资源（Platform console-resource-capabilities.json）；只读账号看不到追平与重建。 */
export const postingOpsActions = {
  catchUp: 'OPERATIONS_FINANCE_POSTING_OPS_CATCH_UP',
  rebuild: 'OPERATIONS_FINANCE_POSTING_OPS_REBUILD',
} as const;

export function hasPostingOpsAction(resources: readonly ConsoleResource[], resourceCode: string): boolean {
  return resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === resourceCode);
}

/** 批次浏览每页条数（B2 limit 取 20 / 50 / 100 / 200，页面固定 50）。 */
export const browsePageSize = 50;
/** account 过账流标识 (OE|FE).主体.币种.分片（AccountPostedPayload.parseAggregateId）。 */
export const streamIdPattern = /^(OE|FE)\.[A-Z0-9]{1,19}\.[A-Z]{3}\.(0|[1-9][0-9]{0,8})$/;

export type SegmentStatus = 'CONSISTENT' | 'BEHIND' | 'UNAVAILABLE';
export type StreamStatus = 'CONSISTENT' | 'BEHIND';

/** 批次摘要：B1 卡住的批次与 B2 列表项同形；桶移动批次没有事实号。 */
export interface PostingSummary {
  postingId: string; postingSequence: string; sourceType: PostingSourceType; factId: string | null; factType: string | null;
  factApplyStatus: 'APPLIED' | 'SKIPPED' | null; postedTime: string; publishedTime: string | null; entryCount: string;
}
/** billing → account：UNAVAILABLE 时数字为空。 */
export interface FactSegment {
  status: SegmentStatus; billingPendingCount: string | null; billingMaxPendingFactId: string | null; billingPublishedCount: string | null;
  billingMaxPublishedFactId: string | null; accountAppliedCount: string | null; accountMaxAppliedFactId: string | null;
}
export interface PostingStream {
  streamId: string; entityType: LedgerEntityType; entityId: string; currency: string; shardNo: string; accountMaxSequence: string;
  accountLastPostedTime: string | null; ledgerConsumedSequence: string; lag: string; status: StreamStatus;
  stuckPosting: PostingSummary | null;
}
/** account → ledger：流清单来自 account，account 不可用时为 UNAVAILABLE 且没有流。 */
export interface StreamSegment { status: SegmentStatus; streams: PostingStream[] }
export interface PostingProgress {
  ledgerBookId: string; entityType: LedgerEntityType; entityId: string; currency: string; factSegment: FactSegment; streamSegment: StreamSegment;
}
/**
 * 段 ① 不可用的原因：ledger 需要 billing 与 account 两方进度才能判定这一段，响应只给 UNAVAILABLE、不区分是哪一方。
 * 流清单只依赖 account，段 ② 也不可用时只能确定 account 不可用（billing 是否可用未知）；段 ② 正常时说明 account 可用，原因是 billing。
 */
export type FactUnavailableCause = 'ACCOUNT' | 'BILLING';
export function factUnavailableCause(progress: Pick<PostingProgress, 'factSegment' | 'streamSegment'>): FactUnavailableCause | null {
  if (progress.factSegment.status !== 'UNAVAILABLE') return null;
  return progress.streamSegment.status === 'UNAVAILABLE' ? 'ACCOUNT' : 'BILLING';
}
/**
 * 账簿与财务主体列表的失败提示（这两个列表在本页不弹通知）：整页报错已说明时不再提示（它们与进度同样依赖 ledger）；
 * 否则给出第一条列表错误，在工具栏下显示可重试的提示。
 */
export function listFailureNotice(pageError: unknown, ...listErrors: string[]): string | null {
  if (pageError) return null;
  return listErrors.find(error => !!error) ?? null;
}
export interface PostingPage { items: PostingSummary[]; nextFromSequence: string | null }
export interface CatchUpResult { processedCount: string; lastSequence: string; caughtUp: boolean }
export interface RebuildResult { accountCount: string; changedAccountCount: string | null; entryCount: string; maxPostingSequence: string }

const segmentStatuses: readonly string[] = ['CONSISTENT', 'BEHIND', 'UNAVAILABLE'];
const streamStatuses: readonly string[] = ['CONSISTENT', 'BEHIND'];
const postingSources: readonly string[] = ['ECONOMIC_FACT', 'BALANCE_MOVEMENT'];

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && decimalIdPattern.test(v);
const digits = (v: unknown) => typeof v === 'string' && /^(0|[1-9][0-9]{0,18})$/.test(v);
const text = (v: unknown, max = 64) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const currency = (v: unknown) => typeof v === 'string' && /^[A-Z]{3}$/.test(v);
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 批次摘要：事实批次必须有事实号与类型，桶移动批次没有事实号。 */
export function decodePostingSummary(value: unknown): PostingSummary {
  const v = value as PostingSummary;
  require(object(v) && id(v.postingId) && digits(v.postingSequence) && oneOf(postingSources)(v.sourceType)
    && (v.sourceType === 'ECONOMIC_FACT' ? id(v.factId) && text(v.factType) : v.factId == null)
    && optional(text)(v.factType) && optional(oneOf(['APPLIED', 'SKIPPED']))(v.factApplyStatus)
    && instant(v.postedTime) && optional(instant)(v.publishedTime) && digits(v.entryCount));
  return { ...v, factId: orNull(v.factId), factType: orNull(v.factType), factApplyStatus: orNull(v.factApplyStatus), publishedTime: orNull(v.publishedTime) };
}

const factCountKeys = ['billingPendingCount', 'billingPublishedCount', 'accountAppliedCount'] as const;
const factIdKeys = ['billingMaxPendingFactId', 'billingMaxPublishedFactId', 'accountMaxAppliedFactId'] as const;

/** 计费事实段：可判定时三项计数必填、最大事实号可空（计数为 0）；UNAVAILABLE 时全部为空。 */
function decodeFactSegment(value: unknown): FactSegment {
  const v = value as FactSegment;
  require(object(v) && oneOf(segmentStatuses)(v.status));
  if (v.status === 'UNAVAILABLE') {
    require([...factCountKeys, ...factIdKeys].every(key => v[key] == null));
  } else {
    require(factCountKeys.every(key => digits(v[key])) && factIdKeys.every(key => optional(id)(v[key])));
  }
  return { status: v.status, ...Object.fromEntries([...factCountKeys, ...factIdKeys].map(key => [key, orNull(v[key])])) } as FactSegment;
}

/** 单条流：标识与四段一致；卡住的批次只出现在落后的流上。 */
function decodeStream(value: unknown): PostingStream {
  const v = value as PostingStream;
  require(object(v) && typeof v.streamId === 'string' && streamIdPattern.test(v.streamId) && oneOf(ledgerEntityTypes)(v.entityType)
    && typeof v.entityId === 'string' && /^[A-Z0-9]{1,19}$/.test(v.entityId) && currency(v.currency) && digits(v.shardNo)
    && v.streamId === streamIdOf(v.entityType, v.entityId, v.currency, v.shardNo)
    && digits(v.accountMaxSequence) && optional(instant)(v.accountLastPostedTime) && digits(v.ledgerConsumedSequence)
    && digits(v.lag) && oneOf(streamStatuses)(v.status)
    && (v.status === 'BEHIND' || v.stuckPosting == null));
  return { streamId: v.streamId, entityType: v.entityType, entityId: v.entityId, currency: v.currency, shardNo: v.shardNo,
    accountMaxSequence: v.accountMaxSequence, accountLastPostedTime: orNull(v.accountLastPostedTime), ledgerConsumedSequence: v.ledgerConsumedSequence,
    lag: v.lag, status: v.status, stuckPosting: v.stuckPosting == null ? null : decodePostingSummary(v.stuckPosting) };
}

function decodeStreamSegment(value: unknown): StreamSegment {
  const v = value as StreamSegment;
  require(object(v) && oneOf(segmentStatuses)(v.status) && Array.isArray(v.streams) && (v.status !== 'UNAVAILABLE' || v.streams.length === 0));
  return { status: v.status, streams: v.streams.map(decodeStream) };
}

/** B1 账簿入账进度：账簿必须与请求一致。 */
export function decodePostingProgress(value: unknown, ledgerBookId: string): PostingProgress {
  const v = value as PostingProgress;
  require(object(v) && v.ledgerBookId === ledgerBookId && oneOf(ledgerEntityTypes)(v.entityType) && text(v.entityId) && currency(v.currency));
  return { ledgerBookId: v.ledgerBookId, entityType: v.entityType, entityId: v.entityId, currency: v.currency,
    factSegment: decodeFactSegment(v.factSegment), streamSegment: decodeStreamSegment(v.streamSegment) };
}

/** B2 批次页：序号严格升序、不早于起始序号；下一页起点在本页之后。 */
export function decodePostingPage(value: unknown, fromSequence: string): PostingPage {
  const v = value as PostingPage;
  require(object(v) && Array.isArray(v.items) && optional(digits)(v.nextFromSequence));
  const items = v.items.map(decodePostingSummary);
  const sequences = items.map(item => BigInt(item.postingSequence));
  require(sequences.every((sequence, index) => index === 0 ? sequence >= BigInt(fromSequence) : sequence > sequences[index - 1]));
  const next = orNull(v.nextFromSequence);
  require(next === null || !sequences.length || BigInt(next) > sequences[sequences.length - 1]);
  return { items, nextFromSequence: next };
}

/** B3 追平结果。 */
export function decodeCatchUpResult(value: unknown): CatchUpResult {
  const v = value as CatchUpResult;
  require(object(v) && digits(v.processedCount) && digits(v.lastSequence) && typeof v.caughtUp === 'boolean');
  return { processedCount: v.processedCount, lastSequence: v.lastSequence, caughtUp: v.caughtUp };
}

/** B4 重建结果：changedAccountCount 为 account 新增字段（7.2），缺失时页面只显示重算数量。 */
export function decodeRebuildResult(value: unknown): RebuildResult {
  const v = value as RebuildResult;
  require(object(v) && digits(v.accountCount) && optional(digits)(v.changedAccountCount) && digits(v.entryCount) && digits(v.maxPostingSequence));
  return { accountCount: v.accountCount, changedAccountCount: orNull(v.changedAccountCount), entryCount: v.entryCount, maxPostingSequence: v.maxPostingSequence };
}

export function streamIdOf(entityType: LedgerEntityType, entityId: string, currencyCode: string, shardNo: string): string {
  return `${entityType === 'OPERATING_ENTITY' ? 'OE' : 'FE'}.${entityId}.${currencyCode}.${shardNo}`;
}

// ---------- 计算与状态映射 ----------

/** 十进制字符串相减，不足为 0（序号、计数均为非负整数字符串）。 */
export function gapOf(larger: string | null, smaller: string | null): bigint {
  if (larger == null || smaller == null) return 0n;
  const gap = BigInt(larger) - BigInt(smaller);
  return gap > 0n ? gap : 0n;
}

/** 追平后剩余落后数 = 刷新后的 account 最大序号 − 本次处理后序号；流已不在进度中时未知。 */
export function remainingAfterCatchUp(progress: PostingProgress | null, streamId: string, lastSequence: string): bigint | null {
  const stream = progress?.streamSegment.streams.find(item => item.streamId === streamId);
  return stream ? gapOf(stream.accountMaxSequence, lastSequence) : null;
}

/** 卡住的批次序号 = ledger 已消费 + 1；流不落后时没有。 */
export function stuckSequenceOf(stream: Pick<PostingStream, 'status' | 'ledgerConsumedSequence'> | undefined): string | null {
  return stream?.status === 'BEHIND' ? String(BigInt(stream.ledgerConsumedSequence) + 1n) : null;
}

/** 浏览批次的默认起点：落后的流从卡住批次前 9 批开始，一致的流显示最后一页。 */
export function browseStartSequence(stream: Pick<PostingStream, 'status' | 'ledgerConsumedSequence' | 'accountMaxSequence'>): string {
  const start = stream.status === 'BEHIND' ? BigInt(stream.ledgerConsumedSequence) - 9n
    : BigInt(stream.accountMaxSequence) - BigInt(browsePageSize) + 1n;
  return String(start > 1n ? start : 1n);
}

/**
 * 追平结果条（画板 24）：done 已追平、partial 部分（继续追平 · 剩余 N）、conflict 409、rejected 规则 / 数据拒绝、timeout BFF 超时
 * <li>by 为执行人（当前登录人员的显示名），at 为发起时间；rejected 记下发起前 ledger 已消费序号，刷新后据此说明本次是否处理了批次。</li>
 */
export type CatchUpOutcome =
  | { kind: 'done'; processedCount: string; lastSequence: string; at: string; by: string }
  | { kind: 'partial'; processedCount: string; lastSequence: string; at: string; by: string }
  | { kind: 'conflict'; at: string; by: string }
  | { kind: 'timeout'; at: string; by: string }
  | { kind: 'rejected'; code: string; detail: string; consumedBefore: string | null; at: string; by: string };

/** 把 B3 的成功结果或错误映射为结果条；其他错误（503 等）返回 null，交给通用通知。 */
export function catchUpOutcome(result: CatchUpResult | unknown, at: string, by: string, consumedBefore: string | null = null): CatchUpOutcome | null {
  if (result instanceof ApiError) {
    if (result.status === 409) return { kind: 'conflict', at, by };
    if (result.status === 400) return { kind: 'rejected', code: result.code, detail: result.detail, consumedBefore, at, by };
    if (result.status === 504) return { kind: 'timeout', at, by };
    return null;
  }
  const value = result as CatchUpResult | null;
  if (!value || typeof value.caughtUp !== 'boolean') return null;
  return { kind: value.caughtUp ? 'done' : 'partial', processedCount: value.processedCount, lastSequence: value.lastSequence, at, by };
}

/**
 * 结果条实际显示的类型：ledger 处理满一页（maxBatches）时即使已到流末尾也返回 caughtUp=false；
 * 处理后序号已不小于 account 最大序号（B1 进度）时剩余为 0，按「已追平」显示，不出「继续追平」。
 */
export function catchUpDisplayKind(outcome: CatchUpOutcome, progress: PostingProgress | null, streamId: string): CatchUpOutcome['kind'] {
  if (outcome.kind !== 'partial') return outcome.kind;
  return remainingAfterCatchUp(progress, streamId, outcome.lastSequence) === 0n ? 'done' : 'partial';
}

/**
 * 规则拒绝后本次推进了多少批：ledger 的 400 不带处理数，只能用刷新后的已消费序号与发起前比较；
 * 进度尚未在拒绝之后刷新、发起前序号未知或流已不在进度中时返回 null（不下结论）。
 */
export function advancedBeforeRejection(outcome: Extract<CatchUpOutcome, { kind: 'rejected' }>, progress: PostingProgress | null,
  checkedAt: string | null, streamId: string): bigint | null {
  if (outcome.consumedBefore === null || !checkedAt || Date.parse(checkedAt) < Date.parse(outcome.at)) return null;
  const stream = progress?.streamSegment.streams.find(item => item.streamId === streamId);
  return stream ? gapOf(stream.ledgerConsumedSequence, outcome.consumedBefore) : null;
}

/** 重建结果条（画板 26）：corrected 有纠正、unchanged 无需纠正、recalculated 未返回变化数、noStream、conflict、timeout；by 为执行人。 */
export type RebuildOutcome =
  | { kind: 'corrected' | 'unchanged' | 'recalculated'; result: RebuildResult; at: string; by: string }
  | { kind: 'noStream' | 'conflict' | 'timeout'; at: string; by: string };

export function rebuildOutcome(result: RebuildResult | unknown, at: string, by: string): RebuildOutcome | null {
  if (result instanceof ApiError) {
    if (result.status === 400 && result.code === 'STREAM_NOT_FOUND') return { kind: 'noStream', at, by };
    if (result.status === 409) return { kind: 'conflict', at, by };
    if (result.status === 504) return { kind: 'timeout', at, by };
    return null;
  }
  const value = result as RebuildResult | null;
  if (!value || !digits(value.accountCount)) return null;
  const kind = value.changedAccountCount === null ? 'recalculated' : value.changedAccountCount === '0' ? 'unchanged' : 'corrected';
  return { kind, result: value, at, by };
}

/** 重建请求体：主体与币种取自所选账簿。 */
export function rebuildBody(progress: Pick<PostingProgress, 'entityType' | 'entityId' | 'currency'>) {
  return { entityType: progress.entityType, entityId: progress.entityId, currency: progress.currency };
}

// ---------- 账簿显示名 ----------

/**
 * 已选账簿的显示名（主体 · 币种）按浏览器标签页记住：账簿列表与进度都来自 ledger，ledger 不可用时下拉仍显示「ZQ344 · USD」而不是主键。
 * 只是显示上的便利：存储不可用或被清空时退回主键，最多记 50 本。
 */
export type BookLabelStorage = Pick<Storage, 'getItem' | 'setItem'>;
const bookLabelStorageKey = 'paytech.operations.postingOps.bookLabels';
const rememberedBookLimit = 50;

function readBookLabels(storage: BookLabelStorage | undefined): Record<string, string> {
  if (!storage) return {};
  try {
    const value: unknown = JSON.parse(storage.getItem(bookLabelStorageKey) ?? '{}');
    if (!object(value)) return {};
    return Object.fromEntries(Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === 'string'));
  } catch { return {}; }
}

export function recallBookLabel(storage: BookLabelStorage | undefined, ledgerBookId: string): string | null {
  return readBookLabels(storage)[ledgerBookId] ?? null;
}

export function rememberBookLabel(storage: BookLabelStorage | undefined, ledgerBookId: string, label: string): void {
  if (!storage || !label) return;
  const labels = readBookLabels(storage);
  if (labels[ledgerBookId] === label) return;
  delete labels[ledgerBookId];
  const kept = Object.entries(labels).slice(-(rememberedBookLimit - 1));
  try { storage.setItem(bookLabelStorageKey, JSON.stringify(Object.fromEntries([...kept, [ledgerBookId, label]]))); }
  catch { /* 当前浏览器存储不可访问。 */ }
}

// ---------- URL ----------

export interface PostingOpsQuery { ledgerBookId: string | null }
export interface StreamBrowseQuery { ledgerBookId: string | null; fromSequence: string; posting: string | null }

/** 进度页：?ledgerBookId=；非法值视为未选择（显示空状态）。 */
export function readPostingOpsQuery(params: URLSearchParams): PostingOpsQuery {
  const ledgerBookId = params.get('ledgerBookId');
  return { ledgerBookId: ledgerBookId && decimalIdPattern.test(ledgerBookId) ? ledgerBookId : null };
}

/** 批次浏览：账簿用于返回与流头指标；起始序号缺省为 1；posting 为打开的批次抽屉。 */
export function readStreamBrowseQuery(params: URLSearchParams): StreamBrowseQuery {
  const fromSequence = params.get('fromSequence');
  const posting = params.get('posting');
  return { ...readPostingOpsQuery(params),
    fromSequence: fromSequence && /^[1-9][0-9]{0,18}$/.test(fromSequence) ? fromSequence : '1',
    posting: posting && decimalIdPattern.test(posting) ? posting : null };
}

export function postingOpsSearch(ledgerBookId: string | null): string {
  return replaceQuery('', { ledgerBookId });
}

export function streamBrowseSearch(ledgerBookId: string | null, fromSequence: string, posting: string | null = null): string {
  return replaceQuery('', { ledgerBookId, fromSequence, posting });
}
