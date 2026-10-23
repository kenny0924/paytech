import { ApiError, session } from '../api/session.ts';
import { pageSizeOptions } from '../api/paging.ts';
import type { PagingRequestBody } from '../api/paging.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { configurationCommand } from '../configuration/configurationModel.ts';
import type { ConfigurationCommand } from '../configuration/configurationModel.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('finance');

/** Operations BFF 财务路径（浏览器路径 = /finance + Owner 路径）。 */
export const financePaths = {
  financeEntities: '/finance/finance-entities/v1',
  fiscalCalendars: '/finance/fiscal-calendars/v1',
  periods: (fiscalCalendarId: string) => `/finance/fiscal-calendars/v1/${fiscalCalendarId}/periods`,
  openPeriod: (accountingPeriodId: string) => `/finance/accounting-periods/v1/${accountingPeriodId}/open`,
  ledgerBooks: '/finance/ledger-books/v1',
  accounts: (ledgerBookId: string) => `/finance/ledger-books/v1/${ledgerBookId}/accounts`,
  journalLines: (ledgerBookId: string, accountId: string) => `/finance/ledger-books/v1/${ledgerBookId}/accounts/${accountId}/journal-lines`,
  journalEntry: (ledgerBookId: string, journalEntryId: string) => `/finance/ledger-books/v1/${ledgerBookId}/journal-entries/${journalEntryId}`,
  charts: '/finance/chart-of-accounts/v1',
  chartAccounts: (chartCode: string) => `/finance/chart-of-accounts/v1/${chartCode}/accounts`,
  chartAccount: (chartCode: string, chartAccountId: string, action: 'update' | 'disable' | 'enable') =>
    `/finance/chart-of-accounts/v1/${chartCode}/accounts/${chartAccountId}/${action}`,
  chartChanges: (chartCode: string) => `/finance/chart-of-accounts/v1/${chartCode}/changes`,
  accountBalances: (ledgerBookId: string, periodId: string) => `/finance/ledger-books/v1/${ledgerBookId}/periods/${periodId}/account-balances`,
  trialBalances: (ledgerBookId: string, periodId: string) => `/finance/ledger-books/v1/${ledgerBookId}/periods/${periodId}/trial-balances`,
  trialBalance: (ledgerBookId: string, periodId: string, runNo: string) =>
    `/finance/ledger-books/v1/${ledgerBookId}/periods/${periodId}/trial-balances/${runNo}`,
  currencies: '/finance/currencies/v1',
  clientBalance: (clientId: string) => `/finance/balances/v1/clients/${clientId}`,
  partnerBalance: (partnerId: string) => `/finance/balances/v1/partners/${partnerId}`,
  ownerAccounts: '/finance/owner-accounts/v1',
  ownerAccountEntries: (accountId: string) => `/finance/owner-accounts/v1/${accountId}/entries`,
  accountPosting: (postingId: string) => `/finance/account-postings/v1/${postingId}`,
} as const;

/** 五类创建、期间开放与科目维护的按钮资源；只读账号看不到任何新建 / 开放 / 维护按钮。 */
export const manageFinanceSetup = 'OPERATIONS_FINANCE_SETUP_MANAGE';

export const entityNoPattern = /^[A-Z]{2}[0-9]{3}$/;
export const decimalIdPattern = /^[1-9][0-9]{0,18}$/;
const chartCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
/** 科目编号 4-2-2-2：一级 4 位，每下一级加 2 位，最多 4 级（ledger ChartAccountNoRule）。 */
export const chartAccountNoPattern = /^[1-9][0-9]{3}([0-9]{2}){0,3}$/;
/** ledger 账户号最长 48 位（LedgerBookQueryApi）；account 账户号为大写字母、数字与连字符（OwnerAccountQueryApi）。 */
const ledgerAccountNoMax = 48;
export const ownerAccountNoPattern = /^[A-Z0-9][A-Z0-9-]{0,47}$/;
const currencyPattern = /^[A-Z]{3}$/;
const decimalPattern = /^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/;
const isoDatePattern = /^\d{4}-\d{2}-\d{2}$/;

export const ledgerEntityTypes = ['OPERATING_ENTITY', 'FINANCE_ENTITY'] as const;
/** ledger 账户核算对象（LedgerOwnerType）：只有平台类对象，Client / Partner 余额在明细账。 */
export const ownerTypes = ['OPERATING_ENTITY', 'FINANCE_ENTITY', 'CHANNEL_ACCOUNT', 'CASH_ACCOUNT', 'TAX_AUTHORITY'] as const;
export const balanceBuckets = ['PENDING', 'AVAILABLE', 'LOCKED', 'NONE'] as const;
/** 明细账 Owner 类型（account OwnerType）。 */
export const subledgerOwnerTypes = ['CLIENT', 'LOCAL_PARTNER', 'CASH_ACCOUNT'] as const;
/** 明细账科目目录（account OwnerAccountType 六条），显示名为 finance.enum.subledgerAccount.*，供科目筛选与列表副标题。 */
export const subledgerAccountCodes = [
  'CLIENT_PAYABLE', 'CLIENT_SECURITY_DEPOSIT', 'CLIENT_RECOVERY_RECEIVABLE', 'PARTNER_PAYABLE', 'PARTNER_RECOVERY_RECEIVABLE', 'BANK_CASH',
] as const;
/** 明细账科目显示名；不在目录内的科目代码返回 null（列表不显示副标题）。 */
export function subledgerAccountName(accountCode: string): string | null {
  return (subledgerAccountCodes as readonly string[]).includes(accountCode) ? enumLabel(t, 'enum.subledgerAccount', accountCode) : null;
}

export type LedgerEntityType = typeof ledgerEntityTypes[number];
export type OwnerType = typeof ownerTypes[number];
export type BalanceBucket = typeof balanceBuckets[number];
export type SubledgerOwnerType = typeof subledgerOwnerTypes[number];
export type FinanceEntityStatus = 'ACTIVE' | 'CLOSED';
export type PeriodStatus = 'FUTURE' | 'OPEN' | 'CLOSING' | 'CLOSED' | 'REOPENING';
export type ReferenceDataStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE';
export type BookStatus = 'OPEN' | 'SUSPENDED' | 'CLOSED';
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'CLOSED';
export type Side = 'DEBIT' | 'CREDIT';
export type JournalEntryType = 'STANDARD' | 'REVERSAL' | 'ADJUSTMENT' | 'CLOSING' | 'REOPENING';
export type OriginType = 'AUTO' | 'MANUAL' | 'ACCOUNT_POSTING';
export type PostingSourceType = 'ECONOMIC_FACT' | 'BALANCE_MOVEMENT';
export type OwnerAccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type SetupTab = 'entities' | 'calendars' | 'books' | 'charts';
export type AccountClass = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';
export type ChartAccountStatus = 'ACTIVE' | 'INACTIVE';
export type ChartAccountChangeType = 'CREATE' | 'UPDATE' | 'DISABLE' | 'ENABLE';
export type TrialBalanceStatus = 'CREATED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type BalanceOwnerType = 'CLIENT' | 'PARTNER';

export interface FinanceEntity { financeEntityId: string; entityCode: string; legalPartyId: string; displayName: string; status: FinanceEntityStatus; createTime: string }
export interface FiscalCalendar { fiscalCalendarId: string; entityType: LedgerEntityType; entityId: string; calendarCode: string; timezone: string; yearStartMonth: number; createTime: string }
export interface AccountingPeriod {
  accountingPeriodId: string; fiscalCalendarId: string; fiscalYear: number; periodNo: number; startDate: string; endDate: string;
  status: PeriodStatus; reopenCount: number;
}
export interface ChartOfAccounts { chartId: string; chartCode: string; chartName: string; status: ReferenceDataStatus }
/** 科目：postingAllowed 为 true 是末级科目，false 是汇总科目；一级科目没有上级。 */
export interface ChartAccount {
  chartAccountId: string; chartId: string; accountNo: string; accountCode: string; accountName: string; accountClass: AccountClass; normalSide: Side;
  postingAllowed: boolean; parentChartAccountId: string | null; level: number; accountStatus: ChartAccountStatus;
}
/** 科目变更记录：before* 为变更前值（CREATE 为空），after* 为变更后值。 */
export interface ChartAccountChange {
  changeId: string; chartAccountId: string; accountNo: string; changeType: ChartAccountChangeType;
  beforeAccountNo: string | null; afterAccountNo: string; beforeParentAccountNo: string | null; afterParentAccountNo: string | null;
  beforeAccountName: string | null; afterAccountName: string; beforeAccountStatus: ChartAccountStatus | null; afterAccountStatus: ChartAccountStatus;
  operatorId: string; changeTime: string;
}
export interface Currency { currency: string; minorUnit: number; roundingMode: string; status: ReferenceDataStatus }
export interface LedgerBook { ledgerBookId: string; entityType: LedgerEntityType; entityId: string; currency: string; chartCode: string; bookStatus: BookStatus }
/** 从 URL 直接进入时只知道账簿主键；从列表下钻时带完整账簿。 */
export type BookRef = Pick<LedgerBook, 'ledgerBookId'> & Partial<Omit<LedgerBook, 'ledgerBookId'>>;
export type AccountRef = Pick<LedgerAccount, 'accountId'> & Partial<Omit<LedgerAccount, 'accountId'>>;
export interface LedgerAccount {
  accountId: string; accountNo: string; accountCode: string; accountName: string; ownerType: OwnerType; ownerId: string; balanceBucket: BalanceBucket;
  normalSide: Side; accountStatus: AccountStatus; debitTotal: string; creditTotal: string; netBalance: string; lastJournalSequence: string | null;
}
export interface JournalLine {
  journalEntryId: string; journalSequence: string; postingDate: string; entryType: JournalEntryType; lineNo: number; entrySide: Side;
  amount: string; balanceAfter: string; instructionType: string; originType: OriginType; sourceSystem: string | null;
  sourceEventKey: string | null; adjustmentNo: string | null; accountPostingId: string | null; accountPostingSequence: string | null;
}
export interface JournalEntryLine {
  lineNo: number; accountId: string; accountNo: string; accountCode: string; accountName: string; ownerType: OwnerType; ownerId: string;
  balanceBucket: BalanceBucket; entrySide: Side; amount: string; accountEntryId: string | null;
}
export interface JournalEntryDetail {
  journalEntryId: string; ledgerBookId: string; accountingPeriodId: string; journalSequence: string; postingDate: string; entryType: JournalEntryType;
  reversalOfEntryId: string | null; instructionId: string; instructionType: string; originType: OriginType; sourceSystem: string | null;
  sourceEventKey: string | null; manualAdjustmentId: string | null; adjustmentNo: string | null; accountPostingId: string | null;
  accountPostingSequence: string | null; lines: JournalEntryLine[];
}
/** 科目余额行（L6 与试算详情共用）：期初、期末按科目自身余额方向表达，负数为反向余额。 */
export interface AccountBalanceLine {
  chartAccountId: string; accountNo: string; accountCode: string; accountName: string; accountClass: AccountClass; normalSide: Side;
  parentChartAccountId: string | null; level: number; postingAllowed: boolean; openingBalance: string; periodDebit: string; periodCredit: string;
  closingBalance: string;
}
export interface AccountBalanceList { ledgerBookId: string; periodId: string; currency: string; lines: AccountBalanceLine[] }
export interface TrialBalanceRun {
  trialBalanceRunId: string; ledgerBookId: string; periodId: string; runNo: string; asOfSequence: string; status: TrialBalanceStatus;
  totalDebit: string; totalCredit: string; contentHash: string; lineCount: number; exceptionCount: number; createTime: string;
}
export interface TrialBalanceFinding { exceptionType: string; referenceType: string; referenceId: string; differenceAmount: string; status: string }
/** 试算详情：lines 为末级试算行，summaryLines 为查询时汇总的级次汇总行。 */
export interface TrialBalanceDetail extends TrialBalanceRun {
  lines: AccountBalanceLine[]; summaryLines: AccountBalanceLine[]; exceptions: TrialBalanceFinding[];
}
export interface Money { currency: string; amount: string }
export interface ClientBalance {
  clientId: string; entityNo: string; currency: string; pendingAmount: Money; availableAmount: Money; lockedAmount: Money;
  securityDepositAmount: Money; recoveryReceivableAmount: Money;
}
export interface PartnerBalance {
  partnerId: string; entityNo: string; currency: string; pendingAmount: Money; availableAmount: Money; lockedAmount: Money; recoveryReceivableAmount: Money;
}
/** 明细账 Owner 账户（account）；金额为 Money。 */
export interface OwnerAccount {
  accountId: string; accountNo: string; accountCode: string; ownerType: SubledgerOwnerType; ownerId: string; balanceBucket: BalanceBucket; normalSide: Side;
  accountStatus: OwnerAccountStatus; debitTotal: Money; creditTotal: Money; netBalance: Money; balanceVersion: string;
}
export type OwnerAccountRef = Pick<OwnerAccount, 'accountId'> & Partial<Omit<OwnerAccount, 'accountId'>>;
/** Owner 账户的一条明细流水：事实批次带 factId / factVersion，桶移动批次带 movementSourceType。 */
export interface AccountEntry {
  entryId: string; postingId: string; postingSequence: string; sourceType: PostingSourceType; factId: string | null; factVersion: string | null;
  factType: string | null; movementSourceType: string | null; lineNo: string; factLineNo: string | null; entrySide: Side; amount: Money; reversesEntryId: string | null; postedTime: string;
}
export interface AccountPostingEntry {
  entryId: string; lineNo: string; accountId: string; accountNo: string; accountCode: string; ownerType: SubledgerOwnerType; ownerId: string;
  balanceBucket: BalanceBucket; entrySide: Side; amount: Money; factLineNo: string | null; reversesEntryId: string | null;
}
/** 过账批次：批次头、来源事实或桶移动，以及全部流水。 */
export interface AccountPosting {
  postingId: string; entityType: LedgerEntityType; entityId: string; currency: string; shardNo: string; postingSequence: string;
  sourceType: PostingSourceType; postedTime: string; publishedTime: string | null; factId: string | null; factVersion: string | null; factType: string | null;
  supersedesFactId: string | null; factApplyStatus: 'APPLIED' | 'SKIPPED' | null; financeEntityId: string | null; movementId: string | null;
  movementSourceType: string | null; movementSourceRef: string | null; entries: AccountPostingEntry[];
}

/**
 * 枚举显示名在 finance.enum.<类型>.<值>（enumLabel 取值，未知值显示原代码）：ledgerEntityType、financeEntityStatus、
 * referenceStatus、accountStatus、side、accountClass、chartAccountStatus、chartChangeType、trialStatus、entryType、
 * ownerAccountStatus、postingSource。
 */
export const financeEntityStatuses: readonly FinanceEntityStatus[] = ['ACTIVE', 'CLOSED'];
export const postingSourceColors: Record<PostingSourceType, string> = { ECONOMIC_FACT: 'default', BALANCE_MOVEMENT: 'orange' };
/** 统一标签色：启用/开放为绿，未来/草稿为蓝/橙，停用/关闭为灰，异常为红。 */
export const statusColors: Record<string, string> = {
  ACTIVE: 'green', OPEN: 'green', FUTURE: 'blue', DRAFT: 'orange', CLOSING: 'gold', REOPENING: 'gold',
  SUSPENDED: 'orange', INACTIVE: 'default', CLOSED: 'default', FROZEN: 'orange', SUCCEEDED: 'green', RUNNING: 'gold', CREATED: 'blue', FAILED: 'red',
};
export const sideColors: Record<Side, string> = { DEBIT: 'blue', CREDIT: 'orange' };
export const entryTypeColors: Record<JournalEntryType, string> = { STANDARD: 'default', REVERSAL: 'red', ADJUSTMENT: 'orange', CLOSING: 'purple', REOPENING: 'purple' };

const periodStatuses: readonly string[] = ['FUTURE', 'OPEN', 'CLOSING', 'CLOSED', 'REOPENING'];
const referenceStatuses: readonly string[] = ['DRAFT', 'ACTIVE', 'INACTIVE'];
const bookStatuses: readonly string[] = ['OPEN', 'SUSPENDED', 'CLOSED'];
const accountStatuses: readonly string[] = ['ACTIVE', 'INACTIVE', 'CLOSED'];
export const accountClasses = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'] as const;
const chartAccountStatuses: readonly string[] = ['ACTIVE', 'INACTIVE'];
const changeTypes: readonly string[] = ['CREATE', 'UPDATE', 'DISABLE', 'ENABLE'];
const trialStatuses: readonly string[] = ['CREATED', 'RUNNING', 'SUCCEEDED', 'FAILED'];
const sides: readonly string[] = ['DEBIT', 'CREDIT'];
const entryTypes: readonly string[] = ['STANDARD', 'REVERSAL', 'ADJUSTMENT', 'CLOSING', 'REOPENING'];
const originTypes: readonly string[] = ['AUTO', 'MANUAL', 'ACCOUNT_POSTING'];
const ownerAccountStatuses: readonly string[] = ['ACTIVE', 'FROZEN', 'CLOSED'];
const postingSources: readonly string[] = ['ECONOMIC_FACT', 'BALANCE_MOVEMENT'];
/** account 主体标识与科目代码格式（OwnerAccountQueryApi）。 */
const subledgerEntityIdPattern = /^[A-Z0-9]{1,19}$/;

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && decimalIdPattern.test(v);
const text = (v: unknown, max = 256) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const decimal = (v: unknown) => typeof v === 'string' && decimalPattern.test(v);
const digits = (v: unknown) => typeof v === 'string' && /^[0-9]{1,19}$/.test(v);
const isoDate = (v: unknown) => typeof v === 'string' && isoDatePattern.test(v) && !Number.isNaN(Date.parse(`${v}T00:00:00Z`))
  && new Date(`${v}T00:00:00Z`).toISOString().slice(0, 10) === v;
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const integer = (v: unknown, min: number, max: number) => Number.isInteger(v) && Number(v) >= min && Number(v) <= max;
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
/** 可空字段：缺失与 null 都视为 null，避免下游序列化策略差异导致误判。 */
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

export function decodeFinanceEntity(value: unknown): FinanceEntity {
  const v = value as FinanceEntity;
  require(object(v) && id(v.financeEntityId) && text(v.entityCode, 64) && id(v.legalPartyId) && text(v.displayName)
    && oneOf(['ACTIVE', 'CLOSED'])(v.status) && instant(v.createTime));
  return v;
}

export function decodeFiscalCalendar(value: unknown): FiscalCalendar {
  const v = value as FiscalCalendar;
  require(object(v) && id(v.fiscalCalendarId) && oneOf(ledgerEntityTypes)(v.entityType) && text(v.entityId, 64) && text(v.calendarCode, 64)
    && text(v.timezone, 64) && integer(v.yearStartMonth, 1, 12) && instant(v.createTime));
  return v;
}

/** 期间必须属于所查日历。 */
export function decodeAccountingPeriod(value: unknown, fiscalCalendarId?: string): AccountingPeriod {
  const v = value as AccountingPeriod;
  require(object(v) && id(v.accountingPeriodId) && id(v.fiscalCalendarId) && (!fiscalCalendarId || v.fiscalCalendarId === fiscalCalendarId)
    && integer(v.fiscalYear, 1900, 9999) && integer(v.periodNo, 1, 999) && isoDate(v.startDate) && isoDate(v.endDate)
    && v.startDate <= v.endDate && oneOf(periodStatuses)(v.status) && integer(v.reopenCount, 0, 2147483647));
  return v;
}

export function decodeChartOfAccounts(value: unknown): ChartOfAccounts {
  const v = value as ChartOfAccounts;
  require(object(v) && id(v.chartId) && typeof v.chartCode === 'string' && chartCodePattern.test(v.chartCode) && text(v.chartName)
    && oneOf(referenceStatuses)(v.status));
  return v;
}

const chartAccountNo = (v: unknown) => typeof v === 'string' && chartAccountNoPattern.test(v);
const level = (v: unknown) => integer(v, 1, 4);

/** 科目：级次与编号长度一致，一级科目没有上级、下级必须有上级。 */
export function decodeChartAccount(value: unknown): ChartAccount {
  const v = value as ChartAccount;
  require(object(v) && id(v.chartAccountId) && id(v.chartId) && chartAccountNo(v.accountNo) && text(v.accountCode, 64) && text(v.accountName, 255)
    && oneOf(accountClasses)(v.accountClass) && oneOf(sides)(v.normalSide) && typeof v.postingAllowed === 'boolean'
    && level(v.level) && v.level === chartAccountLevel(v.accountNo) && (v.level === 1 ? v.parentChartAccountId == null : id(v.parentChartAccountId))
    && oneOf(chartAccountStatuses)(v.accountStatus));
  return { ...v, parentChartAccountId: orNull(v.parentChartAccountId) };
}

/** 科目变更记录：指定科目时必须属于该科目。 */
export function decodeChartAccountChange(chartAccountId?: string) {
  return (value: unknown): ChartAccountChange => {
    const v = value as ChartAccountChange;
    require(object(v) && id(v.changeId) && id(v.chartAccountId) && (!chartAccountId || v.chartAccountId === chartAccountId)
      && chartAccountNo(v.accountNo) && oneOf(changeTypes)(v.changeType) && optional(chartAccountNo)(v.beforeAccountNo)
      && chartAccountNo(v.afterAccountNo) && optional(chartAccountNo)(v.beforeParentAccountNo) && optional(chartAccountNo)(v.afterParentAccountNo)
      && optional(text)(v.beforeAccountName) && text(v.afterAccountName, 255) && optional(oneOf(chartAccountStatuses))(v.beforeAccountStatus)
      && oneOf(chartAccountStatuses)(v.afterAccountStatus) && text(v.operatorId, 64) && instant(v.changeTime));
    return { ...v, beforeAccountNo: orNull(v.beforeAccountNo), beforeParentAccountNo: orNull(v.beforeParentAccountNo),
      afterParentAccountNo: orNull(v.afterParentAccountNo), beforeAccountName: orNull(v.beforeAccountName), beforeAccountStatus: orNull(v.beforeAccountStatus) };
  };
}

/** 科目维护回执：必须是所操作的科目。 */
export function decodeMaintainedChartAccount(chartAccountId: string) {
  return (value: unknown): ChartAccount => {
    const account = decodeChartAccount(value);
    require(account.chartAccountId === chartAccountId);
    return account;
  };
}

export function decodeCurrency(value: unknown): Currency {
  const v = value as Currency;
  require(object(v) && typeof v.currency === 'string' && currencyPattern.test(v.currency) && integer(v.minorUnit, 0, 18)
    && text(v.roundingMode, 32) && oneOf(referenceStatuses)(v.status));
  return v;
}

export function decodeLedgerBook(value: unknown): LedgerBook {
  const v = value as LedgerBook;
  require(object(v) && id(v.ledgerBookId) && oneOf(ledgerEntityTypes)(v.entityType) && text(v.entityId, 64)
    && typeof v.currency === 'string' && currencyPattern.test(v.currency) && text(v.chartCode, 64) && oneOf(bookStatuses)(v.bookStatus));
  return v;
}

export function decodeLedgerAccount(value: unknown): LedgerAccount {
  const v = value as LedgerAccount;
  require(object(v) && id(v.accountId) && text(v.accountNo, ledgerAccountNoMax) && text(v.accountCode, 64) && text(v.accountName, 255)
    && oneOf(ownerTypes)(v.ownerType) && text(v.ownerId, 64)
    && oneOf(balanceBuckets)(v.balanceBucket) && oneOf(sides)(v.normalSide) && oneOf(accountStatuses)(v.accountStatus)
    && decimal(v.debitTotal) && decimal(v.creditTotal) && decimal(v.netBalance) && optional(digits)(v.lastJournalSequence));
  return { ...v, lastJournalSequence: orNull(v.lastJournalSequence) };
}

export function decodeJournalLine(value: unknown): JournalLine {
  const v = value as JournalLine;
  require(object(v) && id(v.journalEntryId) && digits(v.journalSequence) && isoDate(v.postingDate) && oneOf(entryTypes)(v.entryType)
    && integer(v.lineNo, 1, 2147483647) && oneOf(sides)(v.entrySide) && decimal(v.amount) && decimal(v.balanceAfter)
    && text(v.instructionType, 64) && oneOf(originTypes)(v.originType) && optional(text)(v.sourceSystem)
    && optional(text)(v.sourceEventKey) && optional(text)(v.adjustmentNo) && accountPostingRef(v));
  return { ...v, sourceSystem: orNull(v.sourceSystem), sourceEventKey: orNull(v.sourceEventKey), adjustmentNo: orNull(v.adjustmentNo),
    accountPostingId: orNull(v.accountPostingId), accountPostingSequence: orNull(v.accountPostingSequence) };
}

/** ACCOUNT_POSTING 来源必须带 account 批次主键与序号，其他来源两者为空。 */
function accountPostingRef(v: { originType: unknown; accountPostingId?: unknown; accountPostingSequence?: unknown }) {
  return v.originType === 'ACCOUNT_POSTING' ? id(v.accountPostingId) && digits(v.accountPostingSequence)
    : v.accountPostingId == null && v.accountPostingSequence == null;
}

function decodeEntryLine(value: unknown): JournalEntryLine {
  const v = value as JournalEntryLine;
  require(object(v) && integer(v.lineNo, 1, 2147483647) && id(v.accountId) && text(v.accountNo, ledgerAccountNoMax) && text(v.accountCode, 64)
    && text(v.accountName, 255) && oneOf(ownerTypes)(v.ownerType)
    && text(v.ownerId, 64) && oneOf(balanceBuckets)(v.balanceBucket) && oneOf(sides)(v.entrySide) && decimal(v.amount)
    && optional(id)(v.accountEntryId));
  return { ...v, accountEntryId: orNull(v.accountEntryId) };
}

/** 分录详情：分录与账簿必须与请求一致，至少一行。 */
export function decodeJournalEntryDetail(value: unknown, ledgerBookId: string, journalEntryId: string): JournalEntryDetail {
  const v = value as JournalEntryDetail;
  require(object(v) && v.journalEntryId === journalEntryId && v.ledgerBookId === ledgerBookId && id(v.accountingPeriodId)
    && digits(v.journalSequence) && isoDate(v.postingDate) && oneOf(entryTypes)(v.entryType) && optional(id)(v.reversalOfEntryId)
    && id(v.instructionId) && text(v.instructionType, 64) && oneOf(originTypes)(v.originType) && optional(text)(v.sourceSystem)
    && optional(text)(v.sourceEventKey) && optional(id)(v.manualAdjustmentId) && optional(text)(v.adjustmentNo) && accountPostingRef(v)
    && Array.isArray(v.lines) && v.lines.length > 0);
  return { ...v, reversalOfEntryId: orNull(v.reversalOfEntryId), sourceSystem: orNull(v.sourceSystem), sourceEventKey: orNull(v.sourceEventKey),
    manualAdjustmentId: orNull(v.manualAdjustmentId), adjustmentNo: orNull(v.adjustmentNo), accountPostingId: orNull(v.accountPostingId),
    accountPostingSequence: orNull(v.accountPostingSequence), lines: v.lines.map(decodeEntryLine) };
}

function decodeBalanceLine(value: unknown): AccountBalanceLine {
  const v = value as AccountBalanceLine;
  require(object(v) && id(v.chartAccountId) && chartAccountNo(v.accountNo) && text(v.accountCode, 64) && text(v.accountName, 255)
    && oneOf(accountClasses)(v.accountClass) && oneOf(sides)(v.normalSide) && level(v.level) && v.level === chartAccountLevel(v.accountNo)
    && (v.level === 1 ? v.parentChartAccountId == null : id(v.parentChartAccountId)) && typeof v.postingAllowed === 'boolean'
    && [v.openingBalance, v.periodDebit, v.periodCredit, v.closingBalance].every(decimal));
  return { ...v, parentChartAccountId: orNull(v.parentChartAccountId) };
}

/** 实时科目余额（L6）：账簿与期间必须与请求一致。 */
export function decodeAccountBalanceList(value: unknown, ledgerBookId: string, periodId: string): AccountBalanceList {
  const v = value as AccountBalanceList;
  require(object(v) && v.ledgerBookId === ledgerBookId && v.periodId === periodId && typeof v.currency === 'string' && currencyPattern.test(v.currency)
    && Array.isArray(v.lines));
  return { ...v, lines: v.lines.map(decodeBalanceLine) };
}

/** 试算摘要：属于所查账簿与期间。 */
export function decodeTrialBalanceRun(ledgerBookId: string, periodId: string) {
  return (value: unknown): TrialBalanceRun => {
    const v = value as TrialBalanceRun;
    require(object(v) && id(v.trialBalanceRunId) && v.ledgerBookId === ledgerBookId && v.periodId === periodId && digits(v.runNo)
      && digits(v.asOfSequence) && oneOf(trialStatuses)(v.status) && decimal(v.totalDebit) && decimal(v.totalCredit) && text(v.contentHash, 128)
      && integer(v.lineCount, 0, 2147483647) && integer(v.exceptionCount, 0, 2147483647) && instant(v.createTime));
    return v;
  };
}

function decodeFinding(value: unknown): TrialBalanceFinding {
  const v = value as TrialBalanceFinding;
  require(object(v) && text(v.exceptionType, 64) && text(v.referenceType, 64) && text(v.referenceId, 64) && decimal(v.differenceAmount)
    && text(v.status, 32));
  return v;
}

/** 试算详情：序号与请求一致；汇总行一律不可过账。 */
export function decodeTrialBalanceDetail(value: unknown, ledgerBookId: string, periodId: string, runNo: string): TrialBalanceDetail {
  const v = value as TrialBalanceDetail;
  const run = decodeTrialBalanceRun(ledgerBookId, periodId)(v);
  require(run.runNo === runNo && Array.isArray(v.lines) && Array.isArray(v.summaryLines) && Array.isArray(v.exceptions));
  const summaryLines = v.summaryLines.map(decodeBalanceLine);
  require(summaryLines.every(line => !line.postingAllowed));
  return { ...run, lines: v.lines.map(decodeBalanceLine), summaryLines, exceptions: v.exceptions.map(decodeFinding) };
}

function money(v: unknown, currency: string) {
  return object(v) && v.currency === currency && decimal(v.amount);
}

/** Client 余额：Owner、OE 与币种必须与请求一致，五项金额同币种。 */
export function decodeClientBalance(value: unknown, clientId: string, entityNo: string, currency: string): ClientBalance {
  const v = value as ClientBalance;
  require(object(v) && v.clientId === clientId && v.entityNo === entityNo && v.currency === currency
    && [v.pendingAmount, v.availableAmount, v.lockedAmount, v.securityDepositAmount, v.recoveryReceivableAmount].every(item => money(item, currency)));
  return v;
}

/** Partner 余额：不含保证金字段，出现即视为合同漂移。 */
export function decodePartnerBalance(value: unknown, partnerId: string, entityNo: string, currency: string): PartnerBalance {
  const v = value as PartnerBalance;
  require(object(v) && v.partnerId === partnerId && v.entityNo === entityNo && v.currency === currency && !Object.hasOwn(v, 'securityDepositAmount')
    && [v.pendingAmount, v.availableAmount, v.lockedAmount, v.recoveryReceivableAmount].every(item => money(item, currency)));
  return v;
}

// ---------- 明细账 ----------

/** 金额：currency 给定时必须同币种，否则只校验格式（直接粘贴 URL 进入流水时还不知道币种）。 */
const moneyIn = (currency?: string) => (v: unknown) => object(v) && typeof v.currency === 'string' && currencyPattern.test(v.currency)
  && (!currency || v.currency === currency) && decimal(v.amount);

const ownerAccountNo = (v: unknown) => typeof v === 'string' && ownerAccountNoPattern.test(v);

/** Owner 账户：三类 Owner、四个余额桶，金额同查询币种。 */
export function decodeOwnerAccount(currency: string) {
  return (value: unknown): OwnerAccount => {
    const v = value as OwnerAccount;
    require(object(v) && id(v.accountId) && ownerAccountNo(v.accountNo) && typeof v.accountCode === 'string' && chartCodePattern.test(v.accountCode)
      && oneOf(subledgerOwnerTypes)(v.ownerType) && id(v.ownerId) && oneOf(balanceBuckets)(v.balanceBucket) && oneOf(sides)(v.normalSide)
      && oneOf(ownerAccountStatuses)(v.accountStatus) && [v.debitTotal, v.creditTotal, v.netBalance].every(moneyIn(currency))
      && digits(v.balanceVersion));
    return v;
  };
}

/** 事实批次必须有事实主键、版本与类型，没有桶移动来源；桶移动批次相反。 */
function postingSource(v: { sourceType: unknown; factId?: unknown; factVersion?: unknown; factType?: unknown; movementSourceType?: unknown }) {
  if (v.sourceType === 'ECONOMIC_FACT') return id(v.factId) && digits(v.factVersion) && text(v.factType, 64) && v.movementSourceType == null;
  return v.sourceType === 'BALANCE_MOVEMENT' && v.factId == null && v.factVersion == null && v.factType == null && text(v.movementSourceType, 64);
}

/** 账户明细流水。 */
export function decodeAccountEntry(currency?: string) {
  return (value: unknown): AccountEntry => {
    const v = value as AccountEntry;
    require(object(v) && id(v.entryId) && id(v.postingId) && digits(v.postingSequence) && oneOf(postingSources)(v.sourceType) && postingSource(v)
      && digits(v.lineNo) && optional(digits)(v.factLineNo) && oneOf(sides)(v.entrySide) && moneyIn(currency)(v.amount)
      && optional(id)(v.reversesEntryId) && instant(v.postedTime));
    return { ...v, factId: orNull(v.factId), factVersion: orNull(v.factVersion), factType: orNull(v.factType),
      movementSourceType: orNull(v.movementSourceType), factLineNo: orNull(v.factLineNo), reversesEntryId: orNull(v.reversesEntryId) };
  };
}

function decodePostingEntry(currency: string) {
  return (value: unknown): AccountPostingEntry => {
    const v = value as AccountPostingEntry;
    require(object(v) && id(v.entryId) && digits(v.lineNo) && id(v.accountId) && ownerAccountNo(v.accountNo)
      && typeof v.accountCode === 'string' && chartCodePattern.test(v.accountCode)
      && oneOf(subledgerOwnerTypes)(v.ownerType) && id(v.ownerId) && oneOf(balanceBuckets)(v.balanceBucket) && oneOf(sides)(v.entrySide)
      && moneyIn(currency)(v.amount) && optional(digits)(v.factLineNo) && optional(id)(v.reversesEntryId));
    return { ...v, factLineNo: orNull(v.factLineNo), reversesEntryId: orNull(v.reversesEntryId) };
  };
}

/** 过账批次：主键与请求一致，来源字段与来源类型互斥，流水同批次币种。 */
export function decodeAccountPosting(value: unknown, postingId: string): AccountPosting {
  const v = value as AccountPosting;
  require(object(v) && v.postingId === postingId && oneOf(ledgerEntityTypes)(v.entityType) && typeof v.entityId === 'string'
    && subledgerEntityIdPattern.test(v.entityId) && typeof v.currency === 'string' && currencyPattern.test(v.currency) && digits(v.shardNo)
    && digits(v.postingSequence) && oneOf(postingSources)(v.sourceType) && postingSource(v) && instant(v.postedTime)
    && optional(instant)(v.publishedTime) && optional(id)(v.supersedesFactId) && optional(oneOf(['APPLIED', 'SKIPPED']))(v.factApplyStatus)
    && optional(id)(v.financeEntityId) && optional(id)(v.movementId) && optional(text)(v.movementSourceRef) && Array.isArray(v.entries));
  return { ...v, publishedTime: orNull(v.publishedTime), factId: orNull(v.factId), factVersion: orNull(v.factVersion), factType: orNull(v.factType), supersedesFactId: orNull(v.supersedesFactId),
    factApplyStatus: orNull(v.factApplyStatus), financeEntityId: orNull(v.financeEntityId), movementId: orNull(v.movementId),
    movementSourceType: orNull(v.movementSourceType), movementSourceRef: orNull(v.movementSourceRef), entries: v.entries.map(decodePostingEntry(v.currency)) };
}

/** 列表响应 {items}。 */
export function listOf<T>(decode: (value: unknown) => T) {
  return (value: unknown): T[] => {
    require(object(value) && Array.isArray(value.items));
    return (value as { items: unknown[] }).items.map(decode);
  };
}

/** 分页结果逐项解码；页码与总数一致性由公共分页加载器校验。 */
export function pageOf<T>(decode: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    const v = value as PagingResponseBody<T>;
    require(object(v) && Array.isArray(v.items) && Number.isInteger(v.page) && Number.isInteger(v.size) && Number.isInteger(v.total));
    return { ...v, items: v.items.map(decode) };
  };
}

/** 创建结果只含新主键。 */
export function decodeCreated<K extends string>(key: K) {
  return (value: unknown): Record<K, string> => {
    require(object(value) && id(value[key]));
    return value as Record<K, string>;
  };
}

/** 开放结果：同一期间且状态为 OPEN。 */
export function decodeOpenedPeriod(value: unknown, accountingPeriodId: string): { accountingPeriodId: string; status: 'OPEN' } {
  require(object(value) && value.accountingPeriodId === accountingPeriodId && value.status === 'OPEN');
  return value as { accountingPeriodId: string; status: 'OPEN' };
}

// ---------- 错误码 ----------

/** 错误落点：字段名，或 null 表示表单顶部 / 页面提示。 */
export interface FinanceErrorTarget { field: string | null; message: string }
/** 错误码落点表的一项：messageKey 为 finance 命名空间下的文案 key（Problem detail 缺失时使用）。 */
export interface FinanceErrorRule { field: string | null; messageKey: string }

/** ledger LedgerSetupErrorCode 的页面落点（technique 2.4）。 */
export const financeErrorTargets: Readonly<Record<string, FinanceErrorRule>> = {
  FINANCE_ENTITY_ALREADY_EXISTS: { field: null, messageKey: 'error.ledgerSetup.FINANCE_ENTITY_ALREADY_EXISTS' },
  FINANCE_ENTITY_UNAVAILABLE: { field: 'entityId', messageKey: 'error.ledgerSetup.FINANCE_ENTITY_UNAVAILABLE' },
  FISCAL_CALENDAR_ALREADY_EXISTS: { field: 'entityId', messageKey: 'error.ledgerSetup.FISCAL_CALENDAR_ALREADY_EXISTS' },
  FISCAL_CALENDAR_NOT_FOUND: { field: null, messageKey: 'error.ledgerSetup.FISCAL_CALENDAR_NOT_FOUND' },
  ACCOUNTING_PERIOD_OVERLAP: { field: 'startDate', messageKey: 'error.ledgerSetup.ACCOUNTING_PERIOD_OVERLAP' },
  ACCOUNTING_PERIOD_NO_DUPLICATE: { field: 'periodNo', messageKey: 'error.ledgerSetup.ACCOUNTING_PERIOD_NO_DUPLICATE' },
  ACCOUNTING_PERIOD_NOT_FOUND: { field: null, messageKey: 'error.ledgerSetup.ACCOUNTING_PERIOD_NOT_FOUND' },
  ACCOUNTING_PERIOD_NOT_FUTURE: { field: null, messageKey: 'error.ledgerSetup.ACCOUNTING_PERIOD_NOT_FUTURE' },
  LEDGER_BOOK_ALREADY_EXISTS: { field: null, messageKey: 'error.ledgerSetup.LEDGER_BOOK_ALREADY_EXISTS' },
  CURRENCY_NOT_ACTIVE: { field: 'currency', messageKey: 'error.ledgerSetup.CURRENCY_NOT_ACTIVE' },
  CHART_NOT_ACTIVE: { field: 'chartCode', messageKey: 'error.ledgerSetup.CHART_NOT_ACTIVE' },
  LEDGER_BOOK_NOT_FOUND: { field: null, messageKey: 'error.ledgerSetup.LEDGER_BOOK_NOT_FOUND' },
  CHART_ACCOUNT_NOT_POSTABLE: { field: 'accountCode', messageKey: 'error.ledgerSetup.CHART_ACCOUNT_NOT_POSTABLE' },
  ACCOUNT_IDENTITY_INVALID: { field: 'ownerType', messageKey: 'error.ledgerSetup.ACCOUNT_IDENTITY_INVALID' },
  // 设计画板 05：账户重复落在余额桶字段下（余额桶固定 NONE，同一核算对象 + 科目即重复）。
  ACCOUNT_ALREADY_EXISTS: { field: 'balanceBucket', messageKey: 'error.ledgerSetup.ACCOUNT_ALREADY_EXISTS' },
  // 科目维护（technique 2.3）：编号、代码、上级落到表单字段，其余为页面提示；停用科目在建账户表单的科目字段。
  CHART_ACCOUNT_NOT_FOUND: { field: null, messageKey: 'error.ledgerSetup.CHART_ACCOUNT_NOT_FOUND' },
  CHART_ACCOUNT_NO_INVALID: { field: 'accountNo', messageKey: 'error.ledgerSetup.CHART_ACCOUNT_NO_INVALID' },
  CHART_ACCOUNT_NO_DUPLICATE: { field: 'accountNo', messageKey: 'error.ledgerSetup.CHART_ACCOUNT_NO_DUPLICATE' },
  CHART_ACCOUNT_CODE_DUPLICATE: { field: 'accountCode', messageKey: 'error.ledgerSetup.CHART_ACCOUNT_CODE_DUPLICATE' },
  CHART_PARENT_INVALID: { field: 'parentAccountNo', messageKey: 'error.ledgerSetup.CHART_PARENT_INVALID' },
  CHART_STRUCTURE_FROZEN: { field: null, messageKey: 'error.ledgerSetup.CHART_STRUCTURE_FROZEN' },
  CHART_ACCOUNT_IN_USE: { field: null, messageKey: 'error.ledgerSetup.CHART_ACCOUNT_IN_USE' },
  CHART_ACCOUNT_INACTIVE: { field: 'accountCode', messageKey: 'error.ledgerSetup.CHART_ACCOUNT_INACTIVE' },
};

/** 余额查询（account BalanceQueryErrorCode）的落点：Owner 在该主体与币种下没有账户时挂在 Owner ID（画板 11）。 */
export const balanceErrorTargets: Readonly<Record<string, FinanceErrorRule>> = {
  OWNER_ACCOUNT_NOT_FOUND: { field: 'ownerId', messageKey: 'error.balance.OWNER_ACCOUNT_NOT_FOUND' },
};

/** 明细账查询（account AccountPostingErrorCode 与流水的 ACCOUNT_NOT_FOUND）的页面提示。 */
export const subledgerErrorTargets: Readonly<Record<string, FinanceErrorRule>> = {
  ACCOUNT_NOT_FOUND: { field: null, messageKey: 'error.subledger.ACCOUNT_NOT_FOUND' },
  POSTING_NOT_FOUND: { field: null, messageKey: 'error.subledger.POSTING_NOT_FOUND' },
};

/** 余额页无账户提示带上 Owner 类型与查询范围，例如「该 Local Partner 在 SG002 / USD 下没有账户」。 */
export function ownerAccountNotFoundMessage(values: Pick<BalanceFormValues, 'ownerType' | 'entityNo' | 'currency'>): string {
  return t('balance.ownerAccountNotFound', {
    owner: values.ownerType === 'CLIENT' ? 'Client' : 'Local Partner', entityNo: values.entityNo.trim(), currency: values.currency,
  });
}

/**
 * 把 400 业务错误映射为表单落点；字段不在当前表单内时退回表单顶部。
 * 文案优先取 Problem detail（已登记 Code 的 detail 即 Owner 默认消息），缺失时用本表文案；后附错误码，便于与 Finance 日志对照。
 * 未映射或非 400 返回 null，由调用方走通用通知。
 */
export function financeErrorTarget(error: unknown, formFields: readonly string[] = [],
  table: Readonly<Record<string, FinanceErrorRule>> = financeErrorTargets): FinanceErrorTarget | null {
  if (!(error instanceof ApiError) || error.status !== 400 || !Object.hasOwn(table, error.code)) return null;
  const target = table[error.code];
  // 优先显示 Problem detail：同一 Code（如 SELF_REVIEW_FORBIDDEN）在 ledger 与 billing 的默认消息不同，各自显示 Owner 原文
  const message = t('error.withCode', { message: error.detail || t(target.messageKey), code: error.code });
  return { field: target.field && formFields.includes(target.field) ? target.field : null, message };
}

// ---------- 金额与时间 ----------

/** 金额格式：按币种小数位补零、千分位、负数标识；多于小数位的尾零截掉，非零尾数保留不舍入。 */
export function formatAmount(amount: string, minorUnit?: number): { text: string; negative: boolean } {
  if (!decimalPattern.test(amount)) return { text: amount, negative: false };
  const negative = amount.startsWith('-');
  const [integerPart, rawFraction = ''] = amount.replace(/^-/, '').split('.');
  let fraction = rawFraction;
  if (minorUnit !== undefined) {
    if (fraction.length < minorUnit) fraction = fraction.padEnd(minorUnit, '0');
    else fraction = fraction.slice(0, minorUnit) + fraction.slice(minorUnit).replace(/0+$/, '');
  }
  const grouped = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const zero = /^0*$/.test(integerPart + fraction);
  const body = fraction ? `${grouped}.${fraction}` : grouped;
  return { text: negative && !zero ? `-${body}` : body, negative: negative && !zero };
}

const two = (value: number) => String(value).padStart(2, '0');
/** Instant 按浏览器本地时区显示到分钟。 */
export function localTime(value: string): string {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`;
}

/** 长主键缩略：保留前 4 后 4 位。 */
export function abbreviateId(value: string): string {
  return value.length > 10 ? `${value.slice(0, 4)}…${value.slice(-4)}` : value;
}

/** 账簿 / 日历主体展示：财务主体显示代码与缩略主键，经营主体直接显示 entityNo。 */
export function entityLabel(entityType: LedgerEntityType, entityId: string, entities?: ReadonlyMap<string, FinanceEntity>): string {
  if (entityType === 'OPERATING_ENTITY') return entityId;
  const entity = entities?.get(entityId);
  return entity ? t('common.labelWithId', { label: entity.entityCode, id: abbreviateId(entityId) }) : entityId;
}

// ---------- 表单与请求体 ----------

export interface EntityFormValues { entityCode: string; legalPartyId: string; displayName: string }
export interface CalendarFormValues { entityType: LedgerEntityType; entityId: string; calendarCode: string; timezone: string; yearStartMonth: number }
export interface PeriodFormValues { fiscalYear: number; periodNo: number; startDate: string; endDate: string }
export interface BookFormValues { entityType: LedgerEntityType; entityId: string; currency: string; chartCode: string }
export interface AccountFormValues { accountCode: string; ownerType: OwnerType; ownerId: string }

export function entityBody(values: EntityFormValues) {
  return { entityCode: values.entityCode.trim(), legalPartyId: values.legalPartyId.trim(), displayName: values.displayName.trim() };
}

export function calendarBody(values: CalendarFormValues) {
  return { entityType: values.entityType, entityId: values.entityId.trim(), calendarCode: values.calendarCode.trim(),
    timezone: values.timezone, yearStartMonth: values.yearStartMonth };
}

export function periodBody(values: PeriodFormValues) {
  return { fiscalYear: values.fiscalYear, periodNo: values.periodNo, startDate: values.startDate, endDate: values.endDate };
}

export function bookBody(values: BookFormValues) {
  return { entityType: values.entityType, entityId: values.entityId.trim(), currency: values.currency, chartCode: values.chartCode };
}

/** ledger 账户余额桶一律 NONE（余额桶只在明细账）。 */
export function accountBody(values: AccountFormValues) {
  return { accountCode: values.accountCode, ownerType: values.ownerType, ownerId: values.ownerId.trim(), balanceBucket: 'NONE' as const };
}

/** 核算对象标识校验：经营主体为 entityNo，其余（财务主体、渠道账户、银行账户、税务机关）为正十进制主键；返回错误文案或 null。 */
export function ownerIdError(ownerType: string | undefined, ownerId: string | undefined): string | null {
  const value = ownerId?.trim() ?? '';
  if (!value) return t('validation.ownerIdRequired');
  if (ownerType === 'OPERATING_ENTITY') return entityNoPattern.test(value) ? null : t('validation.entityNoPattern');
  return decimalIdPattern.test(value) ? null : t('validation.decimalId');
}

/** 账户流水「来源」第二行：人工调整单号、account 批次（缩略主键 · 序号）或自动来源事件键。 */
export function journalLineSourceMeta(line: Pick<JournalLine, 'originType' | 'adjustmentNo' | 'sourceEventKey' | 'accountPostingId' | 'accountPostingSequence'>): string {
  if (line.originType === 'MANUAL') return line.adjustmentNo ?? '—';
  if (line.originType === 'ACCOUNT_POSTING' && line.accountPostingId) {
    return t('ledger.lineSource.posting', { posting: line.accountPostingId.slice(-4), sequence: String(line.accountPostingSequence) });
  }
  return line.sourceEventKey ?? '—';
}

/** 期间起止：开始不晚于结束。 */
export function periodRangeError(startDate?: string, endDate?: string): string | null {
  if (!startDate || !endDate) return null;
  return startDate <= endDate ? null : t('validation.dateRange');
}

/** IANA 时区下拉；浏览器不支持枚举时只给 UTC。 */
export function timezoneOptions(supported: () => string[] = () => Intl.supportedValuesOf('timeZone')): string[] {
  let zones: string[];
  try { zones = supported(); } catch { zones = []; }
  return zones.includes('UTC') ? zones : ['UTC', ...zones];
}

export function activeOnly<T extends { status: string }>(items: readonly T[]): T[] {
  return items.filter(item => item.status === 'ACTIVE');
}

// ---------- 科目树与科目维护 ----------

/** 由编号长度推出的级次：4 位为 1 级，每加 2 位加 1 级。 */
export function chartAccountLevel(accountNo: string): number {
  return (accountNo.length - 4) / 2 + 1;
}

/** 一级编号首位与类别对应：1 资产、2 负债、3 权益、4 收入、5 费用。 */
const classDigits: Record<AccountClass, string> = { ASSET: '1', LIABILITY: '2', EQUITY: '3', REVENUE: '4', EXPENSE: '5' };

/** 汇总科目默认余额方向：资产、费用为借，负债、权益、收入为贷。 */
export function defaultNormalSide(accountClass: AccountClass): Side {
  return accountClass === 'ASSET' || accountClass === 'EXPENSE' ? 'DEBIT' : 'CREDIT';
}

/** 科目编号分级校验（与 ledger ChartAccountNoRule 一致）：返回错误文案或 null；ledger 仍以 CHART_ACCOUNT_NO_INVALID 兜底。 */
export function chartAccountNoError(accountNo: string | undefined, accountClass: AccountClass | undefined, parentAccountNo?: string | null): string | null {
  const value = accountNo?.trim() ?? '';
  if (!value) return t('validation.accountNoRequired');
  if (!chartAccountNoPattern.test(value)) return t('validation.accountNoPattern');
  if (accountClass && value[0] !== classDigits[accountClass]) {
    return t('validation.accountNoClassDigit', { accountClass: enumLabel(t, 'enum.accountClass', accountClass), digit: classDigits[accountClass] });
  }
  if (!parentAccountNo) return value.length === 4 ? null : t('validation.accountNoTopLevel');
  if (value.length !== parentAccountNo.length + 2 || !value.startsWith(parentAccountNo)) return t('validation.accountNoChild', { parent: parentAccountNo });
  return null;
}

export interface ChartAccountFormValues {
  parentAccountNo?: string | null; accountNo: string; accountCode: string; accountName: string; accountClass: AccountClass; normalSide: Side;
  postingAllowed: boolean;
}

/** 新增科目请求体：一级科目不带上级编号。 */
export function chartAccountBody(values: ChartAccountFormValues) {
  return { accountNo: values.accountNo.trim(), accountCode: values.accountCode.trim(), accountName: values.accountName.trim(),
    accountClass: values.accountClass, normalSide: values.normalSide, postingAllowed: values.postingAllowed,
    ...(values.parentAccountNo ? { parentAccountNo: values.parentAccountNo } : {}) };
}

/** 修改科目请求体：名称、编号与上级三项都给出完整新值，一级科目的上级显式为 null。 */
export function chartAccountUpdateBody(values: Pick<ChartAccountFormValues, 'accountName' | 'accountNo' | 'parentAccountNo'>) {
  return { accountName: values.accountName.trim(), accountNo: values.accountNo.trim(), parentAccountNo: values.parentAccountNo || null };
}

interface TreeItem { chartAccountId: string; parentChartAccountId: string | null; accountNo: string }
/** 树节点：只有存在下级时才带 children，末级行不显示展开图标。 */
export type TreeNode<T extends TreeItem> = T & { children?: TreeNode<T>[] };

/** 按上级主键组装科目树，同级按编号升序；上级不在列表内（被筛掉）的科目作为根。 */
export function buildAccountTree<T extends TreeItem>(items: readonly T[]): TreeNode<T>[] {
  const nodes = new Map<string, TreeNode<T>>(items.map(item => [item.chartAccountId, { ...item }]));
  const roots: TreeNode<T>[] = [];
  for (const node of [...nodes.values()].sort((a, b) => a.accountNo.localeCompare(b.accountNo))) {
    const parent = node.parentChartAccountId ? nodes.get(node.parentChartAccountId) : undefined;
    if (parent) (parent.children ??= []).push(node); else roots.push(node);
  }
  return roots;
}

/** 关键字匹配编号、名称或代码（不区分大小写）：命中节点保留整棵子树，未命中节点只在有命中的下级时保留。 */
export function filterAccountTree<T extends TreeItem & { accountName: string; accountCode: string }>(nodes: readonly TreeNode<T>[], keyword: string): TreeNode<T>[] {
  const needle = keyword.trim().toLowerCase();
  if (!needle) return [...nodes];
  const result: TreeNode<T>[] = [];
  for (const node of nodes) {
    if ([node.accountNo, node.accountName, node.accountCode].some(value => value.toLowerCase().includes(needle))) { result.push(node); continue; }
    const children = filterAccountTree(node.children ?? [], keyword);
    if (children.length) result.push({ ...node, children });
  }
  return result;
}

/** 有下级的节点主键，供「全部展开」。 */
export function expandableKeys<T extends TreeItem>(nodes: readonly TreeNode<T>[]): string[] {
  return nodes.flatMap(node => node.children?.length ? [node.chartAccountId, ...expandableKeys(node.children)] : []);
}

/** 变更记录只列有变化的字段：编号、上级、名称、状态。 */
export function chartChangeFields(change: ChartAccountChange): { label: string; before: string; after: string }[] {
  const fields: [string, string | null, string | null][] = [
    [t('charts.changeField.accountNo'), change.beforeAccountNo, change.afterAccountNo],
    [t('charts.changeField.parent'), change.beforeParentAccountNo, change.afterParentAccountNo],
    [t('charts.changeField.name'), change.beforeAccountName, change.afterAccountName],
    [t('charts.changeField.status'), change.beforeAccountStatus && enumLabel(t, 'enum.chartAccountStatus', change.beforeAccountStatus),
      enumLabel(t, 'enum.chartAccountStatus', change.afterAccountStatus)],
  ];
  return fields.filter(([, before, after]) => before !== after).map(([label, before, after]) => ({ label, before: before ?? '—', after: after ?? '—' }));
}

// ---------- 科目余额与试算 ----------

const negate = (value: string) => value.startsWith('-') ? value.slice(1) : `-${value}`;
const isZero = (value: string) => value.replace(/[-.0]/g, '') === '';

/**
 * 一级科目合计：本期借贷发生额与期末借贷余额；期末余额先按科目方向折成借方方向再分借、贷两侧
 * <li>balanced 表示本期借 = 贷且期末借 = 贷。</li>
 */
export function balanceTotals(lines: readonly Pick<AccountBalanceLine, 'level' | 'normalSide' | 'periodDebit' | 'periodCredit' | 'closingBalance'>[]) {
  const roots = lines.filter(line => line.level === 1);
  const periodDebit = sumDecimals(roots.map(line => line.periodDebit));
  const periodCredit = sumDecimals(roots.map(line => line.periodCredit));
  const debitward = roots.map(line => line.normalSide === 'DEBIT' ? line.closingBalance : negate(line.closingBalance));
  const closingDebit = sumDecimals(debitward.filter(value => !value.startsWith('-')));
  const closingCredit = sumDecimals(debitward.filter(value => value.startsWith('-')).map(negate));
  const balanced = isZero(sumDecimals([periodDebit, negate(periodCredit)])) && isZero(sumDecimals([closingDebit, negate(closingCredit)]));
  return { periodDebit, periodCredit, closingDebit, closingCredit, balanced };
}

/** 账本查询页签：drilldown 账簿下钻（默认），trial 试算平衡。 */
export type LedgerTab = 'drilldown' | 'trial';
export type TrialView = 'balances' | 'runs';
export interface TrialQuery { book: string | null; period: string | null; view: TrialView; run: string | null }
const trialKeys = ['tbBook', 'tbPeriod', 'tbView', 'tbRun'] as const;

export function readLedgerTab(params: URLSearchParams): LedgerTab {
  return params.get('tab') === 'trial' ? 'trial' : 'drilldown';
}

/** 试算平衡 URL：账簿、期间、视图与打开的试算序号；非法值视为未选择。 */
export function readTrialQuery(params: URLSearchParams): TrialQuery {
  const pick = (key: string) => { const value = params.get(key); return value && decimalIdPattern.test(value) ? value : null; };
  const book = pick('tbBook'); const period = book ? pick('tbPeriod') : null;
  const view: TrialView = params.get('tbView') === 'runs' ? 'runs' : 'balances';
  const run = period && view === 'runs' ? params.get('tbRun') : null;
  return { book, period, view, run: run && /^[1-9][0-9]{0,8}$/.test(run) ? run : null };
}

/** 切换页签：进入试算保留下钻条件，回到下钻清掉试算条件。 */
export function ledgerTabSearch(search: string, tab: LedgerTab): string {
  return tab === 'trial' ? replaceQuery(search, { tab: 'trial' }) : replaceQuery(search, { tab: null, ...clear(trialKeys) });
}

/** 试算条件写回 URL：换账簿清期间与试算，换期间或视图清试算。 */
export function trialSearch(search: string, next: Partial<TrialQuery>): string {
  const current = readTrialQuery(new URLSearchParams(search));
  const book = next.book !== undefined ? next.book : current.book;
  const period = next.book !== undefined && next.book !== current.book ? null : next.period !== undefined ? next.period : current.period;
  const view = next.view ?? current.view;
  const run = (next.book !== undefined && next.book !== current.book) || (next.period !== undefined && next.period !== current.period)
    || (next.view !== undefined && next.view !== current.view) ? null : next.run !== undefined ? next.run : current.run;
  return replaceQuery(search, { tab: 'trial', tbBook: book, tbPeriod: period, tbView: view === 'runs' ? 'runs' : null, tbRun: run });
}

/** 末级科目行进入账簿下钻：选中账簿并按科目代码筛选账户。 */
export function leafDrilldownSearch(ledgerBookId: string, accountCode: string): string {
  return `?${new URLSearchParams({ book: ledgerBookId, accountCode })}`;
}

// ---------- 查询条件 ----------

const trimmed = (values: Readonly<Record<string, unknown>>, keys: readonly string[]) => {
  const filters: Record<string, string> = {};
  for (const key of keys) {
    const value = values[key];
    const text = typeof value === 'string' ? value.trim() : typeof value === 'number' ? String(value) : '';
    if (text) filters[key] = text;
  }
  return filters;
};

export const bookFilterKeys = ['entityType', 'entityId', 'currency'] as const;
export const accountFilterKeys = ['accountNo', 'accountCode', 'ownerType', 'ownerId', 'balanceBucket'] as const;
export const lineFilterKeys = ['postingDateFrom', 'postingDateTo'] as const;

export function bookFilters(values: Readonly<Record<string, unknown>>) { return trimmed(values, bookFilterKeys); }
export function accountFilters(values: Readonly<Record<string, unknown>>) { return trimmed(values, accountFilterKeys); }

/** 过账日期范围：yyyy-MM-dd，开始不晚于结束；默认不限。 */
export function lineFilters(values: Readonly<Record<string, unknown>>) {
  const filters = trimmed(values, lineFilterKeys);
  for (const key of lineFilterKeys) if (filters[key] && !isoDate(filters[key])) throw new Error(t('validation.isoDate'));
  if (filters.postingDateFrom && filters.postingDateTo && filters.postingDateFrom > filters.postingDateTo) throw new Error(t('validation.dateRange'));
  return filters;
}

// ---------- URL 状态 ----------

export function readSetupTab(params: URLSearchParams): SetupTab {
  const tab = params.get('tab');
  return tab === 'calendars' || tab === 'books' || tab === 'charts' ? tab : 'entities';
}

export type LedgerLevel = 'books' | 'accounts' | 'lines';
export interface LedgerQuery {
  level: LedgerLevel; book: string | null; account: string | null; entry: string | null;
  bookFilters: Record<string, string>; bookPaging: PagingRequestBody;
  accountFilters: Record<string, string>; accountPaging: PagingRequestBody;
  lineFilters: Record<string, string>; linePaging: PagingRequestBody;
}

const pagingKeys = { books: ['page', 'size'], accounts: ['accountPage', 'accountSize'], lines: ['linePage', 'lineSize'] } as const;

function readPaging(params: URLSearchParams, [pageKey, sizeKey]: readonly [string, string], defaultSize: number): PagingRequestBody {
  const page = Number(params.get(pageKey)); const size = Number(params.get(sizeKey));
  return { page: Number.isSafeInteger(page) && page >= 1 ? page : 1, size: pageSizeOptions.includes(size) ? size : defaultSize };
}

function readFilters(params: URLSearchParams, keys: readonly string[], valid: (key: string, value: string) => boolean = () => true) {
  const filters: Record<string, string> = {};
  for (const key of keys) { const value = params.get(key)?.trim(); if (value && valid(key, value)) filters[key] = value; }
  return filters;
}

/** 账本查询 URL：账簿 › 账户 › 流水逐级下钻；非法 ID 视为未选择，各级筛选与分页都保存在 URL。 */
export function readLedgerQuery(params: URLSearchParams): LedgerQuery {
  const pick = (key: string) => { const value = params.get(key); return value && decimalIdPattern.test(value) ? value : null; };
  const book = pick('book'); const account = book ? pick('account') : null; const entry = account ? pick('entry') : null;
  return {
    level: account ? 'lines' : book ? 'accounts' : 'books', book, account, entry,
    bookFilters: readFilters(params, bookFilterKeys, (key, value) => key !== 'entityType' || (ledgerEntityTypes as readonly string[]).includes(value)),
    bookPaging: readPaging(params, pagingKeys.books, 10),
    accountFilters: readFilters(params, accountFilterKeys, (key, value) => (key !== 'ownerType' || (ownerTypes as readonly string[]).includes(value))
      && (key !== 'balanceBucket' || (balanceBuckets as readonly string[]).includes(value)) && (key !== 'accountNo' || value.length <= ledgerAccountNoMax)),
    accountPaging: readPaging(params, pagingKeys.accounts, 10),
    lineFilters: readFilters(params, lineFilterKeys, (_, value) => isoDate(value)),
    linePaging: readPaging(params, pagingKeys.lines, 20),
  };
}

/** 合并 URL 查询参数，null 或空字符串删除。 */
export function replaceQuery(search: string, updates: Readonly<Record<string, string | null>>): string {
  const next = new URLSearchParams(search);
  for (const [key, value] of Object.entries(updates)) if (value === null || value === '') next.delete(key); else next.set(key, value);
  const text = next.toString();
  return text ? `?${text}` : '';
}

const clear = (keys: readonly string[]) => Object.fromEntries(keys.map(key => [key, null])) as Record<string, null>;
const accountLevelKeys = [...accountFilterKeys, ...pagingKeys.accounts];
const lineLevelKeys = [...lineFilterKeys, ...pagingKeys.lines];

/** 进入某一级：清掉更深层级的选择与条件，保留本级及上级筛选（面包屑回退时条件仍在）。 */
export function ledgerLevelSearch(search: string, level: LedgerLevel, selection: { book?: string; account?: string } = {}): string {
  if (level === 'books') return replaceQuery(search, { book: null, account: null, entry: null, ...clear(accountLevelKeys), ...clear(lineLevelKeys) });
  if (level === 'accounts') {
    const book = selection.book ?? new URLSearchParams(search).get('book');
    const switching = selection.book !== undefined && selection.book !== new URLSearchParams(search).get('book');
    return replaceQuery(search, { book, account: null, entry: null, ...(switching ? clear(accountLevelKeys) : {}), ...clear(lineLevelKeys) });
  }
  const account = selection.account ?? new URLSearchParams(search).get('account');
  const switching = selection.account !== undefined && selection.account !== new URLSearchParams(search).get('account');
  return replaceQuery(search, { account, entry: null, ...(switching ? clear(lineLevelKeys) : {}) });
}

/** 某一级的筛选或翻页写回 URL。 */
export function ledgerQuerySearch(search: string, level: LedgerLevel, filters: Readonly<Record<string, string>>, paging: PagingRequestBody): string {
  const keys = level === 'books' ? bookFilterKeys : level === 'accounts' ? accountFilterKeys : lineFilterKeys;
  const [pageKey, sizeKey] = pagingKeys[level];
  return replaceQuery(search, { ...clear(keys), ...filters, [pageKey]: String(paging.page), [sizeKey]: String(paging.size) });
}

// ---------- 明细账查询条件与 URL 状态 ----------

export const subledgerScopeKeys = ['entityType', 'entityId', 'currency'] as const;
export const subledgerAccountFilterKeys = ['accountNo', 'accountCode', 'ownerType', 'ownerId', 'balanceBucket'] as const;
export interface SubledgerScope { entityType: LedgerEntityType; entityId: string; currency: string }
export type SubledgerLevel = 'idle' | 'accounts' | 'entries';
export interface SubledgerQuery {
  level: SubledgerLevel; scope: SubledgerScope | null; accountFilters: Record<string, string>; accountPaging: PagingRequestBody;
  account: string | null; entryPaging: PagingRequestBody; posting: string | null; entry: string | null;
}
const subledgerPagingKeys = { accounts: ['page', 'size'], entries: ['entryPage', 'entrySize'] } as const;

/** 主体标识：经营主体为 entityNo，财务主体为正十进制主键；返回错误文案或 null。 */
export function subledgerEntityIdError(entityType: string | undefined, entityId: string | undefined): string | null {
  const value = entityId?.trim() ?? '';
  if (!value) return t('validation.entityIdRequired');
  if (entityType === 'FINANCE_ENTITY') return decimalIdPattern.test(value) ? null : t('validation.financeEntityId');
  return entityNoPattern.test(value) ? null : t('validation.entityNoPattern');
}

const subledgerFilterValid = (key: string, value: string) => (key !== 'ownerType' || (subledgerOwnerTypes as readonly string[]).includes(value))
  && (key !== 'balanceBucket' || (balanceBuckets as readonly string[]).includes(value))
  && (key !== 'ownerId' || decimalIdPattern.test(value)) && (key !== 'accountCode' || chartCodePattern.test(value))
  && (key !== 'accountNo' || ownerAccountNoPattern.test(value));

/** 明细账查询：主体三项必填且合法才查询；可选筛选只保留合法值（BFF 白名单 R19）。 */
export function subledgerFilters(values: Readonly<Record<string, unknown>>): Record<string, string> {
  const filters = trimmed(values, [...subledgerScopeKeys, ...subledgerAccountFilterKeys]);
  if (!(ledgerEntityTypes as readonly string[]).includes(filters.entityType) || subledgerEntityIdError(filters.entityType, filters.entityId)
    || !currencyPattern.test(filters.currency ?? '')) throw new Error(t('validation.subledgerScope'));
  for (const key of subledgerAccountFilterKeys) if (filters[key] && !subledgerFilterValid(key, filters[key])) throw new Error(t('validation.filterInvalid'));
  return filters;
}

/** 明细账 URL：主体 › 账户 › 流水逐级下钻；批次抽屉（posting，可带高亮流水 entry）可在任一级打开，供总账分录行直接跳转。 */
export function readSubledgerQuery(params: URLSearchParams): SubledgerQuery {
  const pick = (key: string) => { const value = params.get(key); return value && decimalIdPattern.test(value) ? value : null; };
  const scopeValues = readFilters(params, subledgerScopeKeys);
  let scope: SubledgerScope | null = null;
  try { const filters = subledgerFilters(scopeValues); scope = { entityType: filters.entityType as LedgerEntityType, entityId: filters.entityId, currency: filters.currency }; } catch { scope = null; }
  const account = scope ? pick('account') : null;
  const posting = pick('posting');
  return {
    level: account ? 'entries' : scope ? 'accounts' : 'idle', scope,
    accountFilters: readFilters(params, subledgerAccountFilterKeys, subledgerFilterValid),
    accountPaging: readPaging(params, subledgerPagingKeys.accounts, 10), account,
    entryPaging: readPaging(params, subledgerPagingKeys.entries, 20), posting, entry: posting ? pick('entry') : null,
  };
}

/** 进入某一级：idle 清空全部；accounts 清掉账户选择与流水分页（保留主体与筛选）；entries 换账户时重置流水分页。批次抽屉随层级切换关闭。 */
export function subledgerLevelSearch(search: string, level: SubledgerLevel, selection: { account?: string; scope?: SubledgerScope } = {}): string {
  const entryKeys = { account: null, ...clear(subledgerPagingKeys.entries), posting: null, entry: null };
  if (level === 'idle') return replaceQuery(search, { ...clear([...subledgerScopeKeys, ...subledgerAccountFilterKeys, ...subledgerPagingKeys.accounts]), ...entryKeys });
  if (level === 'accounts') return replaceQuery(search, { ...entryKeys, ...(selection.scope ?? {}) });
  const current = new URLSearchParams(search).get('account');
  const switching = selection.account !== undefined && selection.account !== current;
  return replaceQuery(search, { ...(selection.scope ?? {}), account: selection.account ?? current, posting: null, entry: null,
    ...(switching ? clear(subledgerPagingKeys.entries) : {}) });
}

/** 账户列表的主体、筛选与分页，或流水分页写回 URL；改主体或筛选时清掉账户选择。 */
export function subledgerQuerySearch(search: string, level: 'accounts' | 'entries', filters: Readonly<Record<string, string>>, paging: PagingRequestBody): string {
  const [pageKey, sizeKey] = subledgerPagingKeys[level];
  if (level === 'entries') return replaceQuery(search, { [pageKey]: String(paging.page), [sizeKey]: String(paging.size) });
  return replaceQuery(search, { ...clear([...subledgerScopeKeys, ...subledgerAccountFilterKeys]), ...filters, account: null,
    ...clear(subledgerPagingKeys.entries), [pageKey]: String(paging.page), [sizeKey]: String(paging.size) });
}

/**
 * 从批次抽屉进入流水时只知道科目、Owner 与余额桶：用 R19 按这四项精确筛选取回完整账户（方向、净余额）
 * <li>已有净余额（从账户列表下钻）或信息不全（直接粘贴 URL）时返回 null，不请求。</li>
 */
export function ownerAccountLookupPath(scope: SubledgerScope | null, account: OwnerAccountRef | null): string | null {
  if (!scope || !account || account.netBalance || !account.accountCode || !account.ownerType || !account.ownerId || !account.balanceBucket) return null;
  const query = new URLSearchParams({ ...scope, ownerType: account.ownerType, ownerId: account.ownerId, accountCode: account.accountCode,
    balanceBucket: account.balanceBucket, page: '1', size: '10' });
  return `${financePaths.ownerAccounts}?${query}`;
}

/** R19 精确筛选必须恰好一行且是同一账户，否则视为无效响应。 */
export function decodeOwnerAccountLookup(value: unknown, accountId: string, currency: string): OwnerAccount {
  const page = pageOf(decodeOwnerAccount(currency))(value);
  require(page.total === 1 && page.items.length === 1 && page.items[0].accountId === accountId);
  return page.items[0];
}

/** 总账分录行跳转明细账：先经批次详情（R21）定位，抽屉内高亮对应流水。 */
export function subledgerPostingLink(accountPostingId: string, accountEntryId: string): string {
  return `/finance/subledger${replaceQuery('', { posting: accountPostingId, entry: accountEntryId })}`;
}

/** 明细流水「来源」列：事实批次为「事实 ID · v版本」，桶移动为来源类型。 */
export function entrySourceLabel(entry: Pick<AccountEntry, 'sourceType' | 'factId' | 'factVersion' | 'movementSourceType'>): string {
  return entry.sourceType === 'ECONOMIC_FACT'
    ? t('subledger.entrySource.fact', { factId: String(entry.factId), version: String(entry.factVersion) }) : entry.movementSourceType ?? '—';
}

/** 批次流水借贷合计（Money 金额）。 */
export function postingTotals(entries: readonly Pick<AccountPostingEntry, 'entrySide' | 'amount'>[]) {
  return journalTotals(entries.map(entry => ({ entrySide: entry.entrySide, amount: entry.amount.amount })));
}

// ---------- 写命令 ----------

/** 同一目标与正文的人工重试复用 Idempotency-Key；无请求体（期间开放）时不发送 Body。 */
export function financeCommand(path: string, body: unknown, previous?: ConfigurationCommand): ConfigurationCommand {
  if (body !== undefined) return configurationCommand(path, 'POST', body, previous);
  return { path, method: 'POST', body: '', key: previous?.path === path && previous.body === '' ? previous.key : crypto.randomUUID() };
}

export function sendFinanceCommand<T>(command: ConfigurationCommand, decode: (value: unknown) => T): Promise<T> {
  return session.request<T>(command.path, { method: command.method, ...(command.body ? { body: command.body } : {}),
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } }, decode);
}

// ---------- 分录合计 ----------

/** 十进制字符串精确求和（BigInt，按最大小数位对齐），避免浮点误差。 */
export function sumDecimals(values: readonly string[]): string {
  const scale = Math.max(0, ...values.map(value => (value.split('.')[1] ?? '').length));
  let total = 0n;
  for (const value of values) {
    if (!decimalPattern.test(value)) throw new Error(`invalid decimal ${value}`);
    const negative = value.startsWith('-');
    const [integerPart, fraction = ''] = value.replace(/^-/, '').split('.');
    const units = BigInt(integerPart + fraction.padEnd(scale, '0'));
    total += negative ? -units : units;
  }
  const negative = total < 0n;
  const digits = (negative ? -total : total).toString().padStart(scale + 1, '0');
  const body = scale ? `${digits.slice(0, -scale)}.${digits.slice(-scale)}` : digits;
  return negative ? `-${body}` : body;
}

/** 分录借贷合计与是否平衡。 */
export function journalTotals(lines: readonly Pick<JournalEntryLine, 'entrySide' | 'amount'>[]): { debit: string; credit: string; balanced: boolean } {
  const debit = sumDecimals(lines.filter(line => line.entrySide === 'DEBIT').map(line => line.amount));
  const credit = sumDecimals(lines.filter(line => line.entrySide === 'CREDIT').map(line => line.amount));
  return { debit, credit, balanced: sumDecimals([debit, `-${credit}`.replace(/^--/, '')]).replace(/[-.0]/g, '') === '' };
}

// ---------- 余额查询 ----------

export interface BalanceFormValues { ownerType: BalanceOwnerType; ownerId: string; entityNo: string; currency: string }
export interface BalanceCard { key: string; label: string; code?: string; note: string; amount: Money }

/** 余额查询请求路径：Owner 路径变量加 entityNo / currency 查询参数。 */
export function balanceRequestPath(values: BalanceFormValues): string {
  const query = new URLSearchParams({ entityNo: values.entityNo.trim(), currency: values.currency });
  const ownerId = values.ownerId.trim();
  return `${values.ownerType === 'CLIENT' ? financePaths.clientBalance(ownerId) : financePaths.partnerBalance(ownerId)}?${query}`;
}

/** 余额卡片：应付三桶；Client 另有保证金与追偿应收，Partner 只有追偿应收（不显示保证金）。 */
export function balanceCards(result: ClientBalance | PartnerBalance): BalanceCard[] {
  const cards: BalanceCard[] = [
    { key: 'pending', label: t('balance.card.pending'), note: t('balance.card.pendingNote'), amount: result.pendingAmount },
    { key: 'available', label: t('balance.card.available'), note: t('balance.card.availableNote'), amount: result.availableAmount },
    { key: 'locked', label: t('balance.card.locked'), note: t('balance.card.lockedNote'), amount: result.lockedAmount },
  ];
  if ('clientId' in result) {
    cards.push({ key: 'deposit', label: t('balance.card.deposit'), code: 'CLIENT_SECURITY_DEPOSIT', note: t('balance.card.liabilityNote'), amount: result.securityDepositAmount });
    cards.push({ key: 'recovery', label: t('balance.card.recovery'), code: 'CLIENT_RECOVERY_RECEIVABLE', note: t('balance.card.assetNote'), amount: result.recoveryReceivableAmount });
  } else {
    cards.push({ key: 'recovery', label: t('balance.card.recovery'), code: 'PARTNER_RECOVERY_RECEIVABLE', note: t('balance.card.assetNote'), amount: result.recoveryReceivableAmount });
  }
  return cards;
}

/** 余额查询的服务端字段错误落到表单字段：取最后一段名称，Owner 路径变量对应 ownerId；无法对应时返回 null。 */
export function balanceFieldName(field: string): string | null {
  const name = field.split('.').pop() ?? field;
  if (name === 'clientId' || name === 'partnerId') return 'ownerId';
  return name === 'entityNo' || name === 'currency' ? name : null;
}
