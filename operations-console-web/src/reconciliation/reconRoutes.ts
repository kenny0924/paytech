import { decimalIdPattern } from './reconModel.ts';
import type { ExpectedMovement, ReconException, SourceSummary } from './reconModel.ts';

/** 对账页面路由（与 Console Resource frontend_route 及 menu.ts pageRoutePaths 子路由一致）。 */
const root = '/finance/reconciliation';
export const reconRoutes = {
  clearingAccounts: `${root}/clearing-accounts`,
  clearingAccount: (id: string) => `${root}/clearing-accounts/${id}`,
  cashAccounts: `${root}/cash-accounts`,
  cashAccount: (id: string) => `${root}/cash-accounts/${id}`,
  statements: `${root}/statements`,
  /** 渠道账单详情；lineNo 给出时定位并高亮该行（?line=）。 */
  statement: (id: string, lineNo?: number | null) => `${root}/statements/${id}${lineNo ? `?line=${lineNo}` : ''}`,
  platformTxns: `${root}/platform-txns`,
  channelLines: `${root}/channel-lines`,
  bank: `${root}/bank`,
  bankStatement: (id: string) => `${root}/bank/statements/${id}`,
  bankLine: (id: string) => `${root}/bank/lines/${id}`,
  exceptions: `${root}/exceptions`,
  exception: (id: string) => `${root}/exceptions/${id}`,
  summary: `${root}/summary`,
} as const;

/** 列表路由加查询串：空值不带。 */
export function withQuery(path: string, query: Readonly<Record<string, string | null | undefined>>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) if (value) params.set(key, value);
  const text = params.toString();
  return text ? `${path}?${text}` : path;
}

/** 详情路由参数是否为合法主键；非法时页面直接显示「未找到」而不发请求。 */
export function isReconId(value: string | undefined): value is string {
  return !!value && decimalIdPattern.test(value);
}

// ---------- 列表筛选与页码存 URL（AC-01：后退回到列表保留筛选与页码） ----------

export interface ListPaging { page: number; size: number }
export interface ListQuery { filters: Record<string, string>; paging: ListPaging }

export const listPageSizes: readonly number[] = [10, 20, 50, 100, 200];
/** 渠道流水接口每页最多 100 条。 */
export const channelLinePageSizes: readonly number[] = [10, 20, 50, 100];
export const defaultListPaging: ListPaging = { page: 1, size: 10 };

/**
 * 从查询串读列表筛选与页码
 * <li>只读 keys 列出的筛选键；键不存在时取 defaults，键存在但为空表示用户清空了默认筛选（不带该筛选）。</li>
 * <li>page 为正整数、size 为 sizes 之一（渠道流水只到 100），否则回到默认。</li>
 */
export function readListQuery(search: string, keys: readonly string[], defaults: Readonly<Record<string, string>> = {},
  sizes: readonly number[] = listPageSizes): ListQuery {
  const params = new URLSearchParams(search);
  const filters: Record<string, string> = {};
  for (const key of keys) {
    const value = params.has(key) ? params.get(key)!.trim() : defaults[key] ?? '';
    if (value) filters[key] = value;
  }
  const page = Number(params.get('page')); const size = Number(params.get('size'));
  return { filters, paging: {
    page: Number.isSafeInteger(page) && page >= 1 ? page : defaultListPaging.page,
    size: sizes.includes(size) ? size : defaultListPaging.size,
  } };
}

/**
 * 把列表筛选与页码写回查询串，保留 keys 以外的参数（如银行对账页的 account、tab）
 * <li>与默认值相同的筛选和默认页码不写；清空了默认筛选的键写为空值，读回时仍为「不筛选」。</li>
 */
export function writeListQuery(search: string, keys: readonly string[], query: ListQuery, defaults: Readonly<Record<string, string>> = {}): string {
  const params = new URLSearchParams(search);
  for (const key of [...keys, 'page', 'size']) params.delete(key);
  for (const key of keys) {
    const value = query.filters[key]?.trim() ?? '';
    if (value === (defaults[key] ?? '')) continue;
    params.set(key, value);
  }
  if (query.paging.page !== defaultListPaging.page) params.set('page', String(query.paging.page));
  if (query.paging.size !== defaultListPaging.size) params.set('size', String(query.paging.size));
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** 表单值 → 筛选：去空白、去空值。 */
export function compactFilters(values: Readonly<Record<string, string | null | undefined>>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).flatMap(([key, value]) => {
    const text = typeof value === 'string' ? value.trim() : '';
    return text ? [[key, text]] : [];
  }));
}

// ---------- 渠道流水查询 ----------

/** 渠道流水页的筛选键（与 GET /channel-statement-lines 参数一致）。 */
export const channelLineFilterKeys = ['clearingAccountId', 'statementStatus', 'matchStatus', 'lineType', 'referenceType', 'externalTransactionId',
  'transactionDateFrom', 'transactionDateTo'] as const;

const datePattern = /^\d{4}-\d{2}-\d{2}$/;

/**
 * 渠道流水查询条件：只取已知键并去空白；外部交易号精确查询
 * <li>账单状态不选时不带，由 reconciliation 缺省排除已作废、已拒绝、接收中的账单。</li>
 * <li>日期必须是 YYYY-MM-DD 且起不晚于止；交易号类型与外部交易号都可单独使用。</li>
 */
export function channelLineQuery(values: Readonly<Record<string, string | null | undefined>>): { filters: Record<string, string>; error: string | null } {
  const filters = compactFilters(Object.fromEntries(channelLineFilterKeys.map(key => [key, values[key]])));
  for (const key of ['transactionDateFrom', 'transactionDateTo'] as const) {
    if (filters[key] && !datePattern.test(filters[key])) return { filters, error: '交易日期格式为 YYYY-MM-DD' };
  }
  if (filters.transactionDateFrom && filters.transactionDateTo && filters.transactionDateFrom > filters.transactionDateTo) {
    return { filters, error: '交易日期起不能晚于止' };
  }
  return { filters, error: null };
}

/** 账单行在不筛选时所在的页（行号从 1 连续，账单行按行号升序分页）。 */
export function linePageOf(lineNo: number, size: number): number {
  return Number.isSafeInteger(lineNo) && lineNo >= 1 && size >= 1 ? Math.ceil(lineNo / size) : 1;
}

/** 查询串 ?line= 的行号；非正整数时为 null。 */
export function highlightLineNo(search: string): number | null {
  const value = new URLSearchParams(search).get('line');
  return value && /^[1-9][0-9]{0,8}$/.test(value) ? Number(value) : null;
}

// ---------- 详情页之间的链接 ----------

/** 平台流水 → 其渠道账单行（渠道流水页按清算账户 + 交易号精确查询）。 */
export function channelLinesOfTxn(txn: { clearingAccountId: string; referenceType: string | null; externalTransactionId: string | null }): string {
  return withQuery(reconRoutes.channelLines, { clearingAccountId: txn.clearingAccountId, referenceType: txn.referenceType,
    externalTransactionId: txn.externalTransactionId });
}

/** 账单行 → 匹配到的平台流水（平台流水页按清算账户 + 外部交易号查询）。 */
export function platformTxnsOfLine(line: { clearingAccountId: string | null; externalTransactionId: string | null }): string {
  return withQuery(reconRoutes.platformTxns, { clearingAccountId: line.clearingAccountId, externalTransactionId: line.externalTransactionId });
}

/** 预期银行流水 → 来源：渠道结算指向渠道账单详情，其他来源（出款、缴税）本期无详情页。 */
export function movementSourceLink(movement: Pick<ExpectedMovement, 'movementType' | 'sourceId'>): string | null {
  return movement.movementType === 'CHANNEL_SETTLEMENT' && isReconId(movement.sourceId) ? reconRoutes.statement(movement.sourceId) : null;
}

/**
 * 对账异常 → 来源对象详情（AC-04）
 * <li>渠道账单行用异常所属账单，行号取自来源摘要「批次号 第 N 行」；平台流水按清算账户 + 外部交易号定位。</li>
 * <li>预期银行流水没有独立详情，指向银行对账页该账户的「预期银行流水」页签。</li>
 */
export function exceptionSourceLink(exception: Pick<ReconException, 'sourceType' | 'sourceId' | 'statementId' | 'clearingAccountId' | 'cashAccountId'>,
  source?: Pick<SourceSummary, 'reference'> | null): string | null {
  switch (exception.sourceType) {
    case 'CHANNEL_STATEMENT':
      return isReconId(exception.sourceId) ? reconRoutes.statement(exception.sourceId) : null;
    case 'CHANNEL_STATEMENT_LINE': {
      if (!isReconId(exception.statementId ?? undefined)) return null;
      const lineNo = source?.reference?.match(/第 ([1-9][0-9]*) 行$/)?.[1];
      return reconRoutes.statement(exception.statementId!, lineNo ? Number(lineNo) : null);
    }
    case 'PLATFORM_TXN':
      return withQuery(reconRoutes.platformTxns, { clearingAccountId: exception.clearingAccountId, externalTransactionId: source?.reference ?? null });
    case 'BANK_STATEMENT':
      return isReconId(exception.sourceId) ? reconRoutes.bankStatement(exception.sourceId) : null;
    case 'BANK_STATEMENT_LINE':
      return isReconId(exception.sourceId) ? reconRoutes.bankLine(exception.sourceId) : null;
    case 'EXPECTED_BANK_MOVEMENT':
      return exception.cashAccountId ? withQuery(reconRoutes.bank, { account: exception.cashAccountId, tab: 'movements' }) : null;
    default:
      return null;
  }
}

/**
 * 详情读取失败是否视为「未找到」（AC-01）
 * <li>新接口（银行对账单、银行流水）返回 404 *_NOT_FOUND；既有按主键读取接口不存在时仍为 400 BUSINESS_CHECK_FAILED，detail 为「…不存在: 主键」。</li>
 * <li>只在 detail 以「不存在: 当前主键」结尾时认定，避免把其他业务拒绝当成未找到。</li>
 */
export function isNotFoundFailure(failure: { status: number; code?: string; detail?: string | null }, id: string): boolean {
  if (failure.status === 404) return true;
  return failure.status === 400 && failure.code === 'BUSINESS_CHECK_FAILED' && !!failure.detail && failure.detail.trim().endsWith(`不存在: ${id}`);
}

/** 列表 → 详情时带上来源地址；详情页「返回」只在来源是同一列表时用它，否则回到不带筛选的列表。 */
export function backTarget(listRoute: string, from: unknown): string {
  if (typeof from !== 'string') return listRoute;
  const path = from.split('?')[0];
  return path === listRoute ? from : listRoute;
}
