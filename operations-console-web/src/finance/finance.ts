import { ApiError, session } from '../api/session.ts';
import { pageSizeOptions } from '../api/paging.ts';
import type { PagingRequestBody } from '../api/paging.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { configurationCommand } from '../configuration/configurationModel.ts';
import type { ConfigurationCommand } from '../configuration/configurationModel.ts';

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
  currencies: '/finance/currencies/v1',
  clientBalance: (clientId: string) => `/finance/balances/v1/clients/${clientId}`,
  partnerBalance: (partnerId: string) => `/finance/balances/v1/partners/${partnerId}`,
} as const;

/** 五类创建与期间开放的按钮资源；只读账号看不到任何新建 / 开放按钮。 */
export const manageFinanceSetup = 'OPERATIONS_FINANCE_SETUP_MANAGE';

export const entityNoPattern = /^[A-Z]{2}[0-9]{3}$/;
export const decimalIdPattern = /^[1-9][0-9]{0,18}$/;
const chartCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
const currencyPattern = /^[A-Z]{3}$/;
const decimalPattern = /^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/;
const isoDatePattern = /^\d{4}-\d{2}-\d{2}$/;

export const ledgerEntityTypes = ['OPERATING_ENTITY', 'FINANCE_ENTITY'] as const;
export const ownerTypes = ['OPERATING_ENTITY', 'FINANCE_ENTITY', 'CLIENT', 'LOCAL_PARTNER', 'CHANNEL_ACCOUNT', 'CASH_ACCOUNT', 'TAX_AUTHORITY'] as const;
export const balanceBuckets = ['PENDING', 'AVAILABLE', 'LOCKED', 'NONE'] as const;
/** 使用 PENDING / AVAILABLE / LOCKED 余额桶的科目；其余科目固定 NONE。 */
export const bucketedAccountCodes: readonly string[] = ['CLIENT_PAYABLE', 'PARTNER_PAYABLE'];

export type LedgerEntityType = typeof ledgerEntityTypes[number];
export type OwnerType = typeof ownerTypes[number];
export type BalanceBucket = typeof balanceBuckets[number];
export type FinanceEntityStatus = 'ACTIVE' | 'CLOSED';
export type PeriodStatus = 'FUTURE' | 'OPEN' | 'CLOSING' | 'CLOSED' | 'REOPENING';
export type ReferenceDataStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE';
export type BookStatus = 'OPEN' | 'SUSPENDED' | 'CLOSED';
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'CLOSED';
export type Side = 'DEBIT' | 'CREDIT';
export type JournalEntryType = 'STANDARD' | 'REVERSAL' | 'ADJUSTMENT' | 'CLOSING' | 'REOPENING';
export type OriginType = 'AUTO' | 'MANUAL';
export type SetupTab = 'entities' | 'calendars' | 'books' | 'charts';
export type BalanceOwnerType = 'CLIENT' | 'PARTNER';

export interface FinanceEntity { financeEntityId: string; entityCode: string; legalPartyId: string; displayName: string; status: FinanceEntityStatus; createTime: string }
export interface FiscalCalendar { fiscalCalendarId: string; entityType: LedgerEntityType; entityId: string; calendarCode: string; timezone: string; yearStartMonth: number; createTime: string }
export interface AccountingPeriod {
  accountingPeriodId: string; fiscalCalendarId: string; fiscalYear: number; periodNo: number; startDate: string; endDate: string;
  status: PeriodStatus; reopenCount: number;
}
export interface ChartOfAccounts { chartId: string; chartCode: string; chartName: string; status: ReferenceDataStatus }
export interface ChartAccount {
  chartAccountId: string; chartId: string; accountCode: string; accountName: string; accountClass: string; normalSide: Side; postingAllowed: boolean;
}
export interface Currency { currency: string; minorUnit: number; roundingMode: string; status: ReferenceDataStatus }
export interface LedgerBook { ledgerBookId: string; entityType: LedgerEntityType; entityId: string; currency: string; chartCode: string; bookStatus: BookStatus }
/** 从 URL 直接进入时只知道账簿主键；从列表下钻时带完整账簿。 */
export type BookRef = Pick<LedgerBook, 'ledgerBookId'> & Partial<Omit<LedgerBook, 'ledgerBookId'>>;
export type AccountRef = Pick<LedgerAccount, 'accountId'> & Partial<Omit<LedgerAccount, 'accountId'>>;
export interface LedgerAccount {
  accountId: string; accountCode: string; ownerType: OwnerType; ownerId: string; balanceBucket: BalanceBucket; normalSide: Side;
  accountStatus: AccountStatus; debitTotal: string; creditTotal: string; netBalance: string; lastJournalSequence: string | null;
}
export interface JournalLine {
  journalEntryId: string; journalSequence: string; postingDate: string; entryType: JournalEntryType; lineNo: number; entrySide: Side;
  amount: string; balanceAfter: string; instructionType: string; originType: OriginType; sourceSystem: string | null;
  sourceEventKey: string | null; adjustmentNo: string | null;
}
export interface JournalEntryLine {
  lineNo: number; accountId: string; accountCode: string; ownerType: OwnerType; ownerId: string; balanceBucket: BalanceBucket; entrySide: Side; amount: string;
}
export interface JournalEntryDetail {
  journalEntryId: string; ledgerBookId: string; accountingPeriodId: string; journalSequence: string; postingDate: string; entryType: JournalEntryType;
  reversalOfEntryId: string | null; instructionId: string; instructionType: string; originType: OriginType; sourceSystem: string | null;
  sourceEventKey: string | null; manualAdjustmentId: string | null; adjustmentNo: string | null; lines: JournalEntryLine[];
}
export interface Money { currency: string; amount: string }
export interface ClientBalance {
  clientId: string; entityNo: string; currency: string; pendingAmount: Money; availableAmount: Money; lockedAmount: Money;
  securityDepositAmount: Money; recoveryReceivableAmount: Money;
}
export interface PartnerBalance {
  partnerId: string; entityNo: string; currency: string; pendingAmount: Money; availableAmount: Money; lockedAmount: Money; recoveryReceivableAmount: Money;
}

export const entityTypeLabels: Record<LedgerEntityType, string> = { OPERATING_ENTITY: '经营主体', FINANCE_ENTITY: '财务主体' };
export const financeEntityStatusLabels: Record<FinanceEntityStatus, string> = { ACTIVE: '启用', CLOSED: '已关闭' };
export const referenceStatusLabels: Record<ReferenceDataStatus, string> = { DRAFT: '草稿', ACTIVE: '启用', INACTIVE: '停用' };
export const accountStatusLabels: Record<AccountStatus, string> = { ACTIVE: '启用', INACTIVE: '停用', CLOSED: '已关闭' };
export const sideLabels: Record<Side, string> = { DEBIT: '借', CREDIT: '贷' };
export const entryTypeLabels: Record<JournalEntryType, string> = { STANDARD: '标准', REVERSAL: '冲回', ADJUSTMENT: '调整', CLOSING: '结账', REOPENING: '重开' };
/** 统一标签色：启用/开放为绿，未来/草稿为蓝/橙，停用/关闭为灰，异常为红。 */
export const statusColors: Record<string, string> = {
  ACTIVE: 'green', OPEN: 'green', FUTURE: 'blue', DRAFT: 'orange', CLOSING: 'gold', REOPENING: 'gold',
  SUSPENDED: 'orange', INACTIVE: 'default', CLOSED: 'default',
};
export const sideColors: Record<Side, string> = { DEBIT: 'blue', CREDIT: 'orange' };
export const entryTypeColors: Record<JournalEntryType, string> = { STANDARD: 'default', REVERSAL: 'red', ADJUSTMENT: 'orange', CLOSING: 'purple', REOPENING: 'purple' };

const periodStatuses: readonly string[] = ['FUTURE', 'OPEN', 'CLOSING', 'CLOSED', 'REOPENING'];
const referenceStatuses: readonly string[] = ['DRAFT', 'ACTIVE', 'INACTIVE'];
const bookStatuses: readonly string[] = ['OPEN', 'SUSPENDED', 'CLOSED'];
const accountStatuses: readonly string[] = ['ACTIVE', 'INACTIVE', 'CLOSED'];
const accountClasses: readonly string[] = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'];
const sides: readonly string[] = ['DEBIT', 'CREDIT'];
const entryTypes: readonly string[] = ['STANDARD', 'REVERSAL', 'ADJUSTMENT', 'CLOSING', 'REOPENING'];
const originTypes: readonly string[] = ['AUTO', 'MANUAL'];

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

export function decodeChartAccount(value: unknown): ChartAccount {
  const v = value as ChartAccount;
  require(object(v) && id(v.chartAccountId) && id(v.chartId) && text(v.accountCode, 64) && text(v.accountName)
    && oneOf(accountClasses)(v.accountClass) && oneOf(sides)(v.normalSide) && typeof v.postingAllowed === 'boolean');
  return v;
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
  require(object(v) && id(v.accountId) && text(v.accountCode, 64) && oneOf(ownerTypes)(v.ownerType) && text(v.ownerId, 64)
    && oneOf(balanceBuckets)(v.balanceBucket) && oneOf(sides)(v.normalSide) && oneOf(accountStatuses)(v.accountStatus)
    && decimal(v.debitTotal) && decimal(v.creditTotal) && decimal(v.netBalance) && optional(digits)(v.lastJournalSequence));
  return { ...v, lastJournalSequence: orNull(v.lastJournalSequence) };
}

export function decodeJournalLine(value: unknown): JournalLine {
  const v = value as JournalLine;
  require(object(v) && id(v.journalEntryId) && digits(v.journalSequence) && isoDate(v.postingDate) && oneOf(entryTypes)(v.entryType)
    && integer(v.lineNo, 1, 2147483647) && oneOf(sides)(v.entrySide) && decimal(v.amount) && decimal(v.balanceAfter)
    && text(v.instructionType, 64) && oneOf(originTypes)(v.originType) && optional(text)(v.sourceSystem)
    && optional(text)(v.sourceEventKey) && optional(text)(v.adjustmentNo));
  return { ...v, sourceSystem: orNull(v.sourceSystem), sourceEventKey: orNull(v.sourceEventKey), adjustmentNo: orNull(v.adjustmentNo) };
}

function decodeEntryLine(value: unknown): JournalEntryLine {
  const v = value as JournalEntryLine;
  require(object(v) && integer(v.lineNo, 1, 2147483647) && id(v.accountId) && text(v.accountCode, 64) && oneOf(ownerTypes)(v.ownerType)
    && text(v.ownerId, 64) && oneOf(balanceBuckets)(v.balanceBucket) && oneOf(sides)(v.entrySide) && decimal(v.amount));
  return v;
}

/** 分录详情：分录与账簿必须与请求一致，至少一行。 */
export function decodeJournalEntryDetail(value: unknown, ledgerBookId: string, journalEntryId: string): JournalEntryDetail {
  const v = value as JournalEntryDetail;
  require(object(v) && v.journalEntryId === journalEntryId && v.ledgerBookId === ledgerBookId && id(v.accountingPeriodId)
    && digits(v.journalSequence) && isoDate(v.postingDate) && oneOf(entryTypes)(v.entryType) && optional(id)(v.reversalOfEntryId)
    && id(v.instructionId) && text(v.instructionType, 64) && oneOf(originTypes)(v.originType) && optional(text)(v.sourceSystem)
    && optional(text)(v.sourceEventKey) && optional(id)(v.manualAdjustmentId) && optional(text)(v.adjustmentNo)
    && Array.isArray(v.lines) && v.lines.length > 0);
  return { ...v, reversalOfEntryId: orNull(v.reversalOfEntryId), sourceSystem: orNull(v.sourceSystem), sourceEventKey: orNull(v.sourceEventKey),
    manualAdjustmentId: orNull(v.manualAdjustmentId), adjustmentNo: orNull(v.adjustmentNo), lines: v.lines.map(decodeEntryLine) };
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

/** ledger LedgerSetupErrorCode 与 account BalanceQueryErrorCode 的页面落点（technique 2.4）。 */
export const financeErrorTargets: Readonly<Record<string, FinanceErrorTarget>> = {
  FINANCE_ENTITY_ALREADY_EXISTS: { field: null, message: '财务主体代码或法律 Party 已被使用' },
  FINANCE_ENTITY_UNAVAILABLE: { field: 'entityId', message: '财务主体不存在或已关闭' },
  FISCAL_CALENDAR_ALREADY_EXISTS: { field: 'entityId', message: '该主体已有财务日历' },
  FISCAL_CALENDAR_NOT_FOUND: { field: null, message: '财务日历不存在' },
  ACCOUNTING_PERIOD_OVERLAP: { field: 'startDate', message: '期间日期与同日历其他期间重叠' },
  ACCOUNTING_PERIOD_NO_DUPLICATE: { field: 'periodNo', message: '同一财年期号已存在' },
  ACCOUNTING_PERIOD_NOT_FOUND: { field: null, message: '会计期间不存在' },
  ACCOUNTING_PERIOD_NOT_FUTURE: { field: null, message: '只有 FUTURE 期间可以开放' },
  LEDGER_BOOK_ALREADY_EXISTS: { field: null, message: '同主体同币种已有账簿' },
  CURRENCY_NOT_ACTIVE: { field: 'currency', message: '币种未启用' },
  CHART_NOT_ACTIVE: { field: 'chartCode', message: '科目表不存在或未启用' },
  LEDGER_BOOK_NOT_FOUND: { field: null, message: '账簿不存在' },
  CHART_ACCOUNT_NOT_POSTABLE: { field: 'accountCode', message: '科目不存在或不可过账' },
  ACCOUNT_IDENTITY_INVALID: { field: 'ownerType', message: '核算对象或余额桶不符合账户身份矩阵' },
  // 设计画板 05：账户重复落在余额桶字段下（同一核算对象 + 科目只差余额桶）。
  ACCOUNT_ALREADY_EXISTS: { field: 'balanceBucket', message: '该核算对象与余额桶的账户已存在' },
};

/** 余额查询（account）对同名 Code 的落点。 */
export const balanceErrorTargets: Readonly<Record<string, FinanceErrorTarget>> = {
  LEDGER_BOOK_NOT_FOUND: { field: 'entityNo', message: '未找到该经营主体与币种的账簿' },
};

/**
 * 把 400 业务错误映射为表单落点；字段不在当前表单内时退回表单顶部。
 * 文案后附错误码，便于与 Finance 日志对照。未映射或非 400 返回 null，由调用方走通用通知。
 */
export function financeErrorTarget(error: unknown, formFields: readonly string[] = [],
  table: Readonly<Record<string, FinanceErrorTarget>> = financeErrorTargets): FinanceErrorTarget | null {
  if (!(error instanceof ApiError) || error.status !== 400 || !Object.hasOwn(table, error.code)) return null;
  const target = table[error.code];
  const message = `${target.message}（${error.code}）`;
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
  return entity ? `${entity.entityCode}（${abbreviateId(entityId)}）` : entityId;
}

// ---------- 表单与请求体 ----------

export interface EntityFormValues { entityCode: string; legalPartyId: string; displayName: string }
export interface CalendarFormValues { entityType: LedgerEntityType; entityId: string; calendarCode: string; timezone: string; yearStartMonth: number }
export interface PeriodFormValues { fiscalYear: number; periodNo: number; startDate: string; endDate: string }
export interface BookFormValues { entityType: LedgerEntityType; entityId: string; currency: string; chartCode: string }
export interface AccountFormValues { accountCode: string; ownerType: OwnerType; ownerId: string; balanceBucket: BalanceBucket }

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

/** 非应付科目强制 NONE，避免切换科目后残留旧余额桶。 */
export function accountBody(values: AccountFormValues) {
  return { accountCode: values.accountCode, ownerType: values.ownerType, ownerId: values.ownerId.trim(),
    balanceBucket: usesPayableBuckets(values.accountCode) ? values.balanceBucket : 'NONE' };
}

export function usesPayableBuckets(accountCode: string | undefined): boolean {
  return !!accountCode && bucketedAccountCodes.includes(accountCode);
}

/** 新建账户可选余额桶。 */
export function bucketOptions(accountCode: string | undefined): BalanceBucket[] {
  return usesPayableBuckets(accountCode) ? ['PENDING', 'AVAILABLE', 'LOCKED'] : ['NONE'];
}

/** 核算对象标识校验：经营主体为 entityNo，其余为正十进制主键；返回错误文案或 null。 */
export function ownerIdError(ownerType: string | undefined, ownerId: string | undefined): string | null {
  const value = ownerId?.trim() ?? '';
  if (!value) return '请填写核算对象标识';
  if (ownerType === 'OPERATING_ENTITY') return entityNoPattern.test(value) ? null : '必须是两位大写国家码加三位序号，如 AE001';
  return decimalIdPattern.test(value) ? null : '必须是正十进制整数字符串';
}

/** 期间起止：开始不晚于结束。 */
export function periodRangeError(startDate?: string, endDate?: string): string | null {
  if (!startDate || !endDate) return null;
  return startDate <= endDate ? null : '开始日期不能晚于结束日期';
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
export const accountFilterKeys = ['accountCode', 'ownerType', 'ownerId', 'balanceBucket'] as const;
export const lineFilterKeys = ['postingDateFrom', 'postingDateTo'] as const;

export function bookFilters(values: Readonly<Record<string, unknown>>) { return trimmed(values, bookFilterKeys); }
export function accountFilters(values: Readonly<Record<string, unknown>>) { return trimmed(values, accountFilterKeys); }

/** 过账日期范围：yyyy-MM-dd，开始不晚于结束；默认不限。 */
export function lineFilters(values: Readonly<Record<string, unknown>>) {
  const filters = trimmed(values, lineFilterKeys);
  for (const key of lineFilterKeys) if (filters[key] && !isoDate(filters[key])) throw new Error('请输入 yyyy-MM-dd 格式的日期');
  if (filters.postingDateFrom && filters.postingDateTo && filters.postingDateFrom > filters.postingDateTo) throw new Error('开始日期不能晚于结束日期');
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
      && (key !== 'balanceBucket' || (balanceBuckets as readonly string[]).includes(value))),
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
    { key: 'pending', label: 'PENDING 待成熟应付', note: 'balance_available_time 未到', amount: result.pendingAmount },
    { key: 'available', label: 'AVAILABLE 可用应付', note: '不等于可出款资格', amount: result.availableAmount },
    { key: 'locked', label: 'LOCKED 已锁定应付', note: 'Payout 锁定', amount: result.lockedAmount },
  ];
  if ('clientId' in result) {
    cards.push({ key: 'deposit', label: '保证金', code: 'CLIENT_SECURITY_DEPOSIT', note: '负债类 / NONE', amount: result.securityDepositAmount });
    cards.push({ key: 'recovery', label: '追偿应收', code: 'CLIENT_RECOVERY_RECEIVABLE', note: '资产类 / NONE', amount: result.recoveryReceivableAmount });
  } else {
    cards.push({ key: 'recovery', label: '追偿应收', code: 'PARTNER_RECOVERY_RECEIVABLE', note: '资产类 / NONE', amount: result.recoveryReceivableAmount });
  }
  return cards;
}

/** 余额查询的服务端字段错误落到表单字段：取最后一段名称，Owner 路径变量对应 ownerId；无法对应时返回 null。 */
export function balanceFieldName(field: string): string | null {
  const name = field.split('.').pop() ?? field;
  if (name === 'clientId' || name === 'partnerId') return 'ownerId';
  return name === 'entityNo' || name === 'currency' ? name : null;
}
