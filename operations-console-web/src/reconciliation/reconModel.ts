import { ApiError } from '../api/session.ts';
import type { ConsoleResource, PagingResponseBody } from '../api/types.ts';

/** Operations BFF 对账路径（浏览器路径 = /finance + reconciliation 路径，块 123）。 */
const base = '/finance/reconciliation/v1';
export const reconPaths = {
  clearingAccounts: `${base}/clearing-accounts`,
  unassignedMids: `${base}/clearing-accounts/unassigned-mids`,
  clearingAccount: (id: string) => `${base}/clearing-accounts/${id}`,
  clearingSettings: (id: string) => `${base}/clearing-accounts/${id}/settings`,
  clearingMids: (id: string) => `${base}/clearing-accounts/${id}/mids`,
  clearingMid: (id: string, midId: string) => `${base}/clearing-accounts/${id}/mids/${midId}`,
  clearingStatus: (id: string) => `${base}/clearing-accounts/${id}/status`,
  cashAccounts: `${base}/cash-accounts`,
  cashAccount: (id: string) => `${base}/cash-accounts/${id}`,
  cashClosure: (id: string) => `${base}/cash-accounts/${id}/closure`,
  cashApproval: (id: string) => `${base}/cash-accounts/${id}/approval`,
  channelStatements: `${base}/channel-statements`,
  channelStatement: (id: string) => `${base}/channel-statements/${id}`,
  channelStatementLines: (id: string) => `${base}/channel-statements/${id}/lines`,
  channelStatementCompletion: (id: string) => `${base}/channel-statements/${id}/completion`,
  channelStatementVoid: (id: string) => `${base}/channel-statements/${id}/void`,
  channelLines: `${base}/channel-statement-lines`,
  platformTxns: `${base}/platform-txns`,
  bankStatements: `${base}/bank-statements`,
  bankStatement: (id: string) => `${base}/bank-statements/${id}`,
  bankStatementLines: (id: string) => `${base}/bank-statements/${id}/lines`,
  bankStatementCompletion: (id: string) => `${base}/bank-statements/${id}/completion`,
  bankLines: `${base}/bank-statement-lines`,
  bankLine: (id: string) => `${base}/bank-statement-lines/${id}`,
  expectedMovements: `${base}/expected-bank-movements`,
  bankMatches: `${base}/bank-matches`,
  bankMatchConfirmation: (id: string) => `${base}/bank-matches/${id}/confirmation`,
  bankMatchRejection: (id: string) => `${base}/bank-matches/${id}/rejection`,
  exceptions: `${base}/recon-exceptions`,
  exception: (id: string) => `${base}/recon-exceptions/${id}`,
  resolutions: (exceptionId: string) => `${base}/recon-exceptions/${exceptionId}/resolutions`,
  resolutionApproval: (id: string) => `${base}/recon-resolutions/${id}/approval`,
  summary: `${base}/summary`,
} as const;

/** 两个按钮资源：对账操作、对账审批（AC-V1）；Platform 未授权时按钮不出现。 */
export const reconActions = { operate: 'OPERATIONS_FINANCE_RECON_OPERATE', approve: 'OPERATIONS_FINANCE_RECON_APPROVE' } as const;

export function hasReconAction(resources: readonly ConsoleResource[], resourceCode: string): boolean {
  return resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === resourceCode);
}

// ---------- 合同模型（与 reconciliation-api 字段一致；金额、主键为字符串） ----------

export interface ClearingMid { midId: string; createTime: string; externalMidCode?: string; midStatus?: string }
export interface ClearingAccount {
  clearingAccountId: string; clearingAccountCode: string; channelCode: string; entityNo: string; currency: string;
  settlementCashAccountId: string | null; unmatchedWaitDays: number; costToleranceAmount: string; cashGraceDays: number; status: string;
  version: string; createTime: string; updateTime: string; mids: ClearingMid[];
}
export interface ClearingAccountSummary {
  clearingAccountId: string; clearingAccountCode: string; channelCode: string; entityNo: string; currency: string;
  settlementCashAccountId: string | null; status: string; midCount: number; updateTime: string;
}
export interface UnassignedMid { midId: string; externalMidCode: string; channelCode: string; entityNo: string; currency: string }
export interface CashAccount {
  cashAccountId: string; entityType: string; entityId: string; accountPurpose: string; bankCode: string; accountName: string; currency: string;
  accountLast4: string; status: string; pendingAction: string | null; contentHash: string; makerId: string; checkerId: string | null;
  version: string; createTime: string; updateTime: string;
}
export interface ChannelStatement {
  statementId: string; clearingAccountId: string; clearingAccountCode: string; channelBatchRef: string; revision: number; settlementDate: string;
  currency: string; grossAmount: string; feeAmount: string; netAmount: string; cashDirection: string; declaredLineCount: number;
  importedLineCount: number; fileName: string; fileContentHash: string; rejectReason: string | null; status: string;
  cashConfirmedAmount: string; reconciledTime: string | null; makerId: string; version: string; createTime: string; updateTime: string;
}
export interface ReconcileGate {
  transactionLinesMatched: boolean; adjustmentLinesClassified: boolean; noBlockingException: boolean; totalsConsistent: boolean;
  settlementCashAccountConfigured: boolean;
}
export interface ChannelStatementDetail {
  statement: ChannelStatement; unmatchedLineCount: number; matchedLineCount: number; exceptionLineCount: number; openExceptionCount: number;
  expectedMovementId: string | null; gate: ReconcileGate | null;
}
export interface ChannelStatementLine {
  lineId: string; statementId: string; lineNo: number; lineType: string; direction: string; amount: string; feeAmount: string;
  referenceType: string | null; externalTransactionId: string | null; transactionDate: string | null; rawAttributes: Record<string, string> | null;
  platformTxnId: string | null; matchMethod: string | null; matchedTime: string | null; actualFee: string | null; resolutionId: string | null; matchStatus: string;
}
export interface LineRejection { lineNo: number | null; reason: string }
export interface ImportCompletion {
  status: string; importedLineCount: number; rejectReason: string | null; rejections: LineRejection[];
  statementId?: string; bankStatementId?: string; duplicateLineCount?: number; gapExceptionId?: string | null;
}
export interface PlatformTxn {
  platformTxnId: string; clearingAccountId: string; direction: string; txnSource: string; billingFactId: string; entityNo: string;
  sourceRefType: string; sourceRefId: string; channelTransactionRefId: string; referenceType: string; externalTransactionId: string; currency: string;
  expectedAmount: string; expectedFee: string; matchedAmount: string; actualFee: string; settledAmount: string; status: string; businessTime: string;
  version: string; createTime: string; updateTime: string;
}
export interface BankStatement {
  bankStatementId: string; cashAccountId: string; statementRef: string; periodStart: string; periodEnd: string; currency: string;
  openingBalance: string; closingBalance: string; declaredLineCount: number; importedLineCount: number; duplicateLineCount: number;
  fileName: string; fileContentHash: string; rejectReason: string | null; status: string; makerId: string; createTime: string; updateTime: string;
}
export interface BankStatementLine {
  bankStatementLineId: string; bankStatementId: string; cashAccountId: string; lineNo: number; bankTransactionRef: string; valueDate: string;
  direction: string; amount: string; currency: string; counterpartyName: string | null; referenceText: string | null; matchedAmount: string;
  matchStatus: string; version: string;
}
export interface ExpectedMovement {
  expectedMovementId: string; movementType: string; sourceId: string; cashAccountId: string; entityNo: string; direction: string; currency: string;
  expectedAmount: string; expectedDate: string; bankPaymentRef: string | null; confirmedAmount: string; classifiedAmount: string; status: string;
  version: string; createTime: string; updateTime: string;
}
export interface BankMatch {
  bankMatchId: string; bankStatementLineId: string; expectedMovementId: string; matchedAmount: string; matchMethod: string;
  payoutComponentType: string | null; status: string; resolutionId: string | null; operatorId: string | null; decisionTime: string | null; createTime: string;
}
export interface ReconException {
  exceptionId: string; exceptionType: string; reconLayer: string; blocking: boolean; sourceType: string; sourceId: string; statementId: string | null;
  clearingAccountId: string | null; cashAccountId: string | null; entityType: string; entityId: string; currency: string; differenceAmount: string;
  detail: string | null; status: string; openedBy: string | null; openedTime: string; ageDays: number; closedTime: string | null; version: string;
}
export interface ReconResolution {
  resolutionId: string; exceptionId: string; resolutionNo: number; resolutionType: string; classificationCode: string | null; amount: string | null;
  rematchTargetType: string | null; rematchTargetId: string | null; rematchAmount: string | null; note: string; contentHash: string; makerId: string;
  checkerId: string | null; checkerDecision: string | null; decisionTime: string | null; status: string; createTime: string;
}
export interface SourceSummary {
  sourceType: string; sourceId: string; reference: string | null; direction: string | null; amount: string | null; status: string | null; businessDate: string | null;
}
export interface AccountLabel { clearingAccountCode: string | null; channelCode: string | null; cashAccountName: string | null; accountLast4: string | null }
export interface ReconExceptionDetail {
  exception: ReconException; source: SourceSummary; availableResolutionTypes: string[]; availableClassificationCodes: string[];
  resolutions: ReconResolution[]; account: AccountLabel | null;
}
/** 渠道流水（跨账单的账单行）：行字段 + 所属账单与清算账户。 */
export interface ChannelLineView extends ChannelStatementLine {
  clearingAccountId: string; clearingAccountCode: string; channelBatchRef: string; revision: number; statementStatus: string;
  settlementDate: string; currency: string | null;
}
/** 银行流水详情（reconciliation BankStatementLineDetailResponseBody）：流水、所属对账单、全部匹配（含预期银行流水）、未结异常。 */
export interface BankLineMatch { match: BankMatch; expectedMovement: ExpectedMovement | null }
export interface BankLineDetail { line: BankStatementLine; statement: BankStatement | null; matches: BankLineMatch[]; openExceptions: ReconException[] }
export interface ReconSummary {
  entityNo: string | null; currency: string;
  inTransitStatements: { matchingCount: number; matchingNetAmount: string; reconciledCount: number; reconciledUnconfirmedAmount: string };
  openExceptionsByAge: { bucket: string; count: number; blockingCount: number }[];
  unmatchedBankLines: { count: number; creditAmount: string; debitAmount: string };
}

// ---------- 响应解码：只校验页面依赖的身份与集合，合同漂移按 502 处理 ----------

export const decimalIdPattern = /^[1-9][0-9]{0,18}$/;
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && decimalIdPattern.test(v);
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 对象带指定主键字段（正十进制字符串）；expected 给出时必须相等。 */
export function decodeWithId<T>(key: string, expected?: string) {
  return (value: unknown): T => {
    require(object(value) && id(value[key]) && (expected === undefined || value[key] === expected));
    return value as T;
  };
}

/** 分页结果逐项解码。 */
export function pageOf<T>(decode: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    const v = value as PagingResponseBody<T>;
    require(object(v) && Array.isArray(v.items) && Number.isInteger(v.page) && Number.isInteger(v.size) && Number.isInteger(v.total));
    return { ...v, items: v.items.map(decode) };
  };
}

export function decodeClearingAccount(expected?: string) {
  return (value: unknown): ClearingAccount => {
    const v = decodeWithId<ClearingAccount>('clearingAccountId', expected)(value);
    require(Array.isArray(v.mids));
    return v;
  };
}

export function decodeUnassignedMids(value: unknown): UnassignedMid[] {
  require(object(value) && Array.isArray(value.items));
  return (value as { items: UnassignedMid[] }).items;
}

export function decodeStatementDetail(expected: string) {
  return (value: unknown): ChannelStatementDetail => {
    require(object(value));
    decodeWithId('statementId', expected)((value as { statement: unknown }).statement);
    return value as unknown as ChannelStatementDetail;
  };
}

export function decodeCompletion(key: 'statementId' | 'bankStatementId', expected: string) {
  return (value: unknown): ImportCompletion => {
    const v = decodeWithId<ImportCompletion>(key, expected)(value);
    require(Array.isArray(v.rejections) && typeof v.status === 'string');
    return v;
  };
}

export function decodeExceptionDetail(expected: string) {
  return (value: unknown): ReconExceptionDetail => {
    require(object(value) && Array.isArray(value.resolutions) && Array.isArray(value.availableResolutionTypes)
      && Array.isArray(value.availableClassificationCodes) && object(value.source));
    decodeWithId('exceptionId', expected)((value as { exception: unknown }).exception);
    return { account: null, ...(value as object) } as ReconExceptionDetail;
  };
}

export function decodeBankMatchList(value: unknown): BankMatch[] {
  require(object(value) && Array.isArray(value.items));
  return (value as { items: BankMatch[] }).items;
}

/** 渠道流水行：行主键与所属账单主键。 */
export function decodeChannelLine(value: unknown): ChannelLineView {
  const v = decodeWithId<ChannelLineView>('lineId')(value);
  require(id(v.statementId));
  return v;
}

export function decodeBankLineDetail(expected: string) {
  return (value: unknown): BankLineDetail => {
    require(object(value) && Array.isArray(value.matches));
    decodeWithId('bankStatementLineId', expected)((value as { line: unknown }).line);
    const detail = value as unknown as BankLineDetail;
    return { ...detail, openExceptions: Array.isArray(detail.openExceptions) ? detail.openExceptions : [] };
  };
}

export function decodeSummary(currency: string) {
  return (value: unknown): ReconSummary => {
    require(object(value) && value.currency === currency && object(value.inTransitStatements) && Array.isArray(value.openExceptionsByAge)
      && object(value.unmatchedBankLines));
    return value as unknown as ReconSummary;
  };
}

// ---------- 显示名 ----------

export const clearingStatusLabels: Record<string, string> = { ACTIVE: '启用', INACTIVE: '停用' };
export const cashStatusLabels: Record<string, string> = { PENDING_VERIFICATION: '待复核', ACTIVE: '有效', SUSPENDED: '暂停', CLOSED: '已关闭' };
export const accountPurposeLabels: Record<string, string> = { CHANNEL_SETTLEMENT: '渠道结算', PAYOUT: '出款', TAX: '缴税' };
export const entityTypeLabels: Record<string, string> = { OPERATING_ENTITY: '经营主体', FINANCE_ENTITY: '财务主体' };
export const statementStatusLabels: Record<string, string> = {
  RECEIVING: '接收中', REJECTED: '已拒绝', MATCHING: '匹配中', VOIDED: '已作废', RECONCILED: '已对平', CASH_CONFIRMED: '已到账',
};
export const bankStatementStatusLabels: Record<string, string> = { RECEIVING: '接收中', IMPORTED: '已导入', REJECTED: '已拒绝' };
export const lineMatchStatusLabels: Record<string, string> = {
  UNMATCHED: '未匹配', PARTIALLY_MATCHED: '部分匹配', MATCHED: '已匹配', EXCEPTION: '异常', SUSPENDED: '暂记',
};
/** 渠道账单行只有三种匹配状态（接口只接受这三种筛选）。 */
export const channelLineMatchStatusLabels: Record<string, string> = { UNMATCHED: '未匹配', MATCHED: '已匹配', EXCEPTION: '异常' };
export const lineTypeLabels: Record<string, string> = { PAYMENT: '收款', REFUND: '退款', CHARGEBACK: '拒付', FEE: '费用', ADJUSTMENT: '调整' };
export const directionLabels: Record<string, string> = { CREDIT: '贷（收）', DEBIT: '借（付）', RECEIVABLE: '应收', PAYABLE: '应付', NONE: '无' };
export const platformTxnStatusLabels: Record<string, string> = {
  OPEN: '未匹配', PARTIALLY_MATCHED: '部分匹配', MATCHED: '已匹配', SETTLED: '已结算', EXCEPTION: '异常',
};
export const movementStatusLabels: Record<string, string> = {
  OPEN: '待到账', PARTIALLY_CONFIRMED: '部分到账', CONFIRMED: '已到账', CANCELLED: '已取消', EXCEPTION: '异常',
};
export const bankMatchStatusLabels: Record<string, string> = { PROPOSED: '建议', CONFIRMED: '已确认', REJECTED: '已拒绝' };
export const exceptionStatusLabels: Record<string, string> = { OPEN: '待处置', UNDER_REVIEW: '待复核', RESOLVED: '已解决', AUTO_CLOSED: '自动关闭' };
export const exceptionTypeLabels: Record<string, string> = {
  CHANNEL_LINE_UNMATCHED: '账单行未命中', AMBIGUOUS_REFERENCE: '交易号多条命中', AMOUNT_MISMATCH: '金额超出', DUPLICATE_SETTLEMENT: '重复结算',
  UNEXPLAINED_ADJUSTMENT: '调整行待归类', COST_DEVIATION: '渠道费偏差', PLATFORM_ITEM_UNSETTLED: '平台流水未结算', BANK_LINE_UNMATCHED: '银行流水未匹配',
  BANK_AMOUNT_MISMATCH: '到账金额不符', BANK_MOVEMENT_OVERDUE: '应到未到', BANK_STATEMENT_GAP: '对账单不连续', LATE_DEBIT: '迟到扣款',
};
export const reconLayerLabels: Record<string, string> = { TRANSACTION: '交易层', BANK: '银行层' };
export const sourceTypeLabels: Record<string, string> = {
  CHANNEL_STATEMENT: '渠道账单', CHANNEL_STATEMENT_LINE: '渠道账单行', PLATFORM_TXN: '平台流水', BANK_STATEMENT: '银行对账单',
  BANK_STATEMENT_LINE: '银行流水', EXPECTED_BANK_MOVEMENT: '预期银行流水',
};
export const resolutionTypeLabels: Record<string, string> = { RE_MATCH: '重新匹配', ACCEPT_TIMING_DIFFERENCE: '接受时间差', CLASSIFY: '归类' };
export const resolutionStatusLabels: Record<string, string> = { PENDING_CHECK: '待复核', REJECTED: '已驳回', APPLIED: '已应用' };
export const classificationLabels: Record<string, string> = {
  CHANNEL_FEE_OTHER: '渠道其他费用', CHANNEL_RESERVE_HOLD: '渠道保证金扣留', CHANNEL_RESERVE_RELEASE: '渠道保证金释放', BANK_FEE: '银行手续费',
  BANK_INTEREST: '银行利息', INTERNAL_TRANSFER: '自有账户调拨', UNALLOCATED_RECEIPT: '暂不能认定的收款', PAYOUT_RETURN_PENDING: '出款退回待处理',
  RECON_DIFFERENCE: '对账差异',
};
export const ageBucketLabels: Record<string, string> = { D0_3: '0–3 天', D4_7: '4–7 天', D8_30: '8–30 天', D31_PLUS: '31 天以上' };
export const reconStatusColors: Record<string, string> = {
  ACTIVE: 'green', INACTIVE: 'default', PENDING_VERIFICATION: 'gold', SUSPENDED: 'orange', CLOSED: 'default', RECEIVING: 'blue', REJECTED: 'red',
  MATCHING: 'gold', VOIDED: 'default', RECONCILED: 'green', CASH_CONFIRMED: 'green', IMPORTED: 'green', UNMATCHED: 'gold', PARTIALLY_MATCHED: 'gold',
  MATCHED: 'green', EXCEPTION: 'red', OPEN: 'gold', SETTLED: 'green', PARTIALLY_CONFIRMED: 'gold', CONFIRMED: 'green', CANCELLED: 'default',
  PROPOSED: 'blue', UNDER_REVIEW: 'gold', RESOLVED: 'green', AUTO_CLOSED: 'default', PENDING_CHECK: 'gold', APPLIED: 'green',
};

/**
 * 归类码按异常来源对象可用（设计 F3 表）；与 reconciliation 返回的当前可选码取交集，顺序按 F3 表
 * <li>渠道账单行只走「随对平带出」，平台流水只能 RECON_DIFFERENCE，银行层两类各有子集；其他来源对象不允许归类。</li>
 */
export const classificationsBySource: Readonly<Record<string, readonly string[]>> = {
  CHANNEL_STATEMENT_LINE: ['CHANNEL_FEE_OTHER', 'CHANNEL_RESERVE_HOLD', 'CHANNEL_RESERVE_RELEASE', 'RECON_DIFFERENCE'],
  PLATFORM_TXN: ['RECON_DIFFERENCE'],
  EXPECTED_BANK_MOVEMENT: ['BANK_FEE', 'RECON_DIFFERENCE'],
  BANK_STATEMENT_LINE: ['BANK_FEE', 'BANK_INTEREST', 'INTERNAL_TRANSFER', 'UNALLOCATED_RECEIPT', 'PAYOUT_RETURN_PENDING', 'RECON_DIFFERENCE'],
};

export function classificationOptions(sourceType: string, available: readonly string[]): string[] {
  return (classificationsBySource[sourceType] ?? []).filter(code => available.includes(code));
}

/** 重新匹配的目标类型由对账层决定：交易层指向平台流水，银行层指向预期银行流水（并须给分配金额）。 */
export function rematchTargetType(reconLayer: string): 'PLATFORM_TXN' | 'EXPECTED_BANK_MOVEMENT' {
  return reconLayer === 'BANK' ? 'EXPECTED_BANK_MOVEMENT' : 'PLATFORM_TXN';
}

/** 人工开异常只允许三类对象（D-02）。 */
export const manualExceptionSources = ['PLATFORM_TXN', 'EXPECTED_BANK_MOVEMENT', 'BANK_STATEMENT_LINE'] as const;

// ---------- 十进制精确运算 ----------

const signedDecimal = /^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/;
const positiveDecimal = /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/;

function scaleOf(values: readonly string[]) {
  return Math.max(0, ...values.map(value => (value.split('.')[1] ?? '').length));
}

function units(value: string, scale: number): bigint {
  if (!signedDecimal.test(value)) throw new Error(`invalid decimal ${value}`);
  const negative = value.startsWith('-');
  const [integerPart, fraction = ''] = value.replace(/^-/, '').split('.');
  const result = BigInt(integerPart + fraction.padEnd(scale, '0'));
  return negative ? -result : result;
}

function fromUnits(value: bigint, scale: number): string {
  const negative = value < 0n;
  const digits = (negative ? -value : value).toString().padStart(scale + 1, '0');
  const text = scale ? `${digits.slice(0, -scale)}.${digits.slice(-scale)}` : digits;
  return negative ? `-${text}` : text;
}

/** 带符号求和：terms 为 [金额, 符号]；结果小数位取各项最大值。 */
export function signedSum(terms: readonly (readonly [string, 1 | -1])[]): string {
  const scale = scaleOf(terms.map(([value]) => value));
  return fromUnits(terms.reduce((total, [value, sign]) => total + units(value, scale) * BigInt(sign), 0n), scale);
}

/** 比较两个十进制字符串：-1 / 0 / 1。 */
export function compareDecimal(left: string, right: string): number {
  const scale = scaleOf([left, right]);
  const difference = units(left, scale) - units(right, scale);
  return difference === 0n ? 0 : difference < 0n ? -1 : 1;
}

/** 正数金额输入：普通十进制、大于零、小数位不超过精度。 */
export function positiveAmountError(value: string | undefined, minorUnit = 2): string | null {
  const text = value?.trim() ?? '';
  if (!text) return '请填写金额';
  if (!positiveDecimal.test(text)) return '金额为正的十进制数，如 100.00';
  if (/^0(\.0+)?$/.test(text)) return '金额必须大于零';
  if ((text.split('.')[1] ?? '').length > minorUnit) return `小数位不能超过 ${minorUnit} 位`;
  return null;
}

/** 预期银行流水的剩余待确认金额 = 预期 − 已确认 − 已归类（归类为带符号差额）。 */
export function expectedRemaining(movement: Pick<ExpectedMovement, 'expectedAmount' | 'confirmedAmount' | 'classifiedAmount'>): string {
  return signedSum([[movement.expectedAmount, 1], [movement.confirmedAmount, -1], [movement.classifiedAmount, -1]]);
}

export interface Allocation { expectedMovementId: string; direction: string; amount: string; remaining: string }

/**
 * 人工多选匹配的实时合计（AC-V5）
 * <li>每条分配对流水的符号：预期流水方向与流水方向相同为正，相反为负；剩余 = 流水金额 − 已匹配 − 带符号合计。</li>
 * <li>可提交：至少一条、每条金额合法且不超过预期剩余、带符号合计大于零且剩余不小于零。</li>
 */
export function manualMatchTotals(line: Pick<BankStatementLine, 'direction' | 'amount' | 'matchedAmount'>, allocations: readonly Allocation[], minorUnit = 2) {
  const valid = allocations.filter(item => !positiveAmountError(item.amount, minorUnit));
  const signed = signedSum([['0', 1], ...valid.map(item => [item.amount.trim(), item.direction === line.direction ? 1 : -1] as const)]);
  const remaining = signedSum([[line.amount, 1], [line.matchedAmount, -1], [signed, -1]]);
  const errors = allocations.map(item => positiveAmountError(item.amount, minorUnit)
    ?? (compareDecimal(item.amount.trim(), item.remaining) > 0 ? `超过预期流水剩余 ${item.remaining}` : null));
  const exceeds = compareDecimal(remaining, '0') < 0;
  const submittable = allocations.length > 0 && errors.every(error => !error) && compareDecimal(signed, '0') > 0 && !exceeds;
  return { signed, remaining, exceeds, errors, submittable };
}

// ---------- CSV 解析（D-01：浏览器本地解析，分段发送） ----------

/** RFC 4180 风格：逗号分隔、双引号包裹、引号内 "" 转义、CRLF / LF 换行；返回每行的单元格。 */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = []; let cell = ''; let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') { cell += '"'; i++; }
      else if (char === '"') quoted = false;
      else cell += char;
      continue;
    }
    if (char === '"' && cell === '') quoted = true;
    else if (char === ',') { row.push(cell); cell = ''; }
    else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[i + 1] === '\n') i++;
      row.push(cell); rows.push(row); row = []; cell = '';
    } else cell += char;
  }
  if (quoted) throw new CsvError('引号未闭合');
  if (cell !== '' || row.length) { row.push(cell); rows.push(row); }
  return rows.filter(cells => !(cells.length === 1 && cells[0].trim() === ''));
}

export class CsvError extends Error {}

export interface CsvIssue { lineNo: number | null; reason: string }

export const maxStatementLines = 20000;
const channelHeaderColumns = ['clearing_account_code', 'channel_batch_ref', 'settlement_date', 'currency', 'gross_amount', 'fee_amount', 'net_amount', 'cash_direction'] as const;
const channelLineColumns = ['line_no', 'line_type', 'direction', 'amount', 'fee_amount', 'reference_type', 'external_transaction_id', 'transaction_date'] as const;
const bankHeaderColumns = ['bank_account_last4', 'statement_ref', 'period_start', 'period_end', 'currency', 'opening_balance', 'closing_balance'] as const;
const bankLineColumns = ['line_no', 'bank_transaction_ref', 'value_date', 'direction', 'amount', 'counterparty_name', 'reference_text'] as const;

const datePattern = /^\d{4}-\d{2}-\d{2}$/;
const visibleAscii = /^[\x21-\x7E]{1,128}$/;
const validDate = (value: string) => datePattern.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`))
  && new Date(`${value}T00:00:00Z`).toISOString().startsWith(value);

/** 拆出两行 #HEADER（列名、取值）与其后的明细列名、明细；结构不符时整单报错。 */
function splitSections(rows: string[][], headerColumns: readonly string[], lineColumns: readonly string[]) {
  const issues: CsvIssue[] = [];
  if (rows.length < 3 || rows[0][0] !== '#HEADER' || rows[1][0] !== '#HEADER') {
    return { issues: [{ lineNo: null, reason: '前两行必须是以 #HEADER 开头的账单头列名行与取值行' }], header: {}, columns: [], details: [] as string[][] };
  }
  const headerNames = rows[0].slice(1).map(name => name.trim());
  const headerValues = rows[1].slice(1);
  for (const column of headerColumns) if (!headerNames.includes(column)) issues.push({ lineNo: null, reason: `账单头缺少列 ${column}` });
  if (headerValues.length !== headerNames.length) issues.push({ lineNo: null, reason: '账单头取值个数与列名不一致' });
  const header = Object.fromEntries(headerNames.map((name, index) => [name, (headerValues[index] ?? '').trim()]));
  const columns = rows[2].map(name => name.trim());
  for (const column of lineColumns) if (!columns.includes(column)) issues.push({ lineNo: null, reason: `明细缺少列 ${column}` });
  if (new Set(columns).size !== columns.length) issues.push({ lineNo: null, reason: '明细列名重复' });
  return { issues, header, columns, details: rows.slice(3) };
}

function cellsOf(columns: readonly string[], cells: readonly string[]) {
  return Object.fromEntries(columns.map((name, index) => [name, (cells[index] ?? '').trim()]));
}

function checkHeaderAmount(issues: CsvIssue[], header: Record<string, string>, name: string, signed: boolean) {
  const value = header[name] ?? '';
  if (!(signed ? /^-?(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/ : positiveDecimal).test(value)) {
    issues.push({ lineNo: null, reason: `账单头 ${name} 必须是${signed ? '带符号' : '非负'}十进制金额` });
  }
}

/** 行号必须从 1 连续递增。 */
function checkLineNo(issues: CsvIssue[], index: number, value: string) {
  if (value !== String(index + 1)) issues.push({ lineNo: index + 1, reason: `line_no 应为 ${index + 1}，实际为「${value}」` });
}

export interface ChannelStatementFile {
  header: { clearingAccountCode: string; channelBatchRef: string; settlementDate: string; currency: string; grossAmount: string; feeAmount: string;
    netAmount: string; cashDirection: string };
  lines: { lineNo: number; lineType: string; direction: string; amount: string; feeAmount: string; referenceType: string | null;
    externalTransactionId: string | null; transactionDate: string | null; rawAttributes: Record<string, string> | null }[];
}

/**
 * 解析标准渠道账单 CSV（technique 2.5）：第 1、2 行为 #HEADER 账单头列名与取值，第 3 行明细列名，其后明细
 * <li>只做结构与格式校验（列、行号、枚举、金额、日期、长度）；恒等式与匹配由 reconciliation 完成导入时校验。</li>
 * <li>标准列之外的列原样进 rawAttributes（空值不带，≤ 64 列、值 ≤ 1024 字符）。</li>
 */
export function parseChannelStatementCsv(text: string): { file: ChannelStatementFile | null; issues: CsvIssue[] } {
  let rows: string[][];
  try { rows = parseCsv(text); } catch (error) { return { file: null, issues: [{ lineNo: null, reason: (error as Error).message }] }; }
  const { issues, header, columns, details } = splitSections(rows, channelHeaderColumns, channelLineColumns);
  if (issues.length) return { file: null, issues };
  if (!/^[A-Z][A-Z0-9_-]{1,63}$/.test(header.clearing_account_code)) issues.push({ lineNo: null, reason: 'clearing_account_code 格式不正确' });
  if (!visibleAscii.test(header.channel_batch_ref)) issues.push({ lineNo: null, reason: 'channel_batch_ref 必须是 1–128 位可见 ASCII' });
  if (!validDate(header.settlement_date)) issues.push({ lineNo: null, reason: 'settlement_date 必须是 YYYY-MM-DD' });
  if (!/^[A-Z]{3}$/.test(header.currency)) issues.push({ lineNo: null, reason: 'currency 必须是三位大写币种' });
  checkHeaderAmount(issues, header, 'gross_amount', true);
  checkHeaderAmount(issues, header, 'fee_amount', false);
  checkHeaderAmount(issues, header, 'net_amount', false);
  if (!['CREDIT', 'DEBIT', 'NONE'].includes(header.cash_direction)) issues.push({ lineNo: null, reason: 'cash_direction 只能为 CREDIT、DEBIT 或 NONE' });
  if (!details.length) issues.push({ lineNo: null, reason: '没有明细行' });
  if (details.length > maxStatementLines) issues.push({ lineNo: null, reason: `明细超过 ${maxStatementLines} 行` });
  const extra = columns.filter(name => !(channelLineColumns as readonly string[]).includes(name));
  if (extra.length > 64) issues.push({ lineNo: null, reason: '渠道特有列超过 64 列' });
  const lines = details.map((cells, index) => {
    const v = cellsOf(columns, cells);
    const lineNo = index + 1;
    checkLineNo(issues, index, v.line_no);
    if (cells.length !== columns.length) issues.push({ lineNo, reason: `列数应为 ${columns.length}，实际为 ${cells.length}` });
    if (!['PAYMENT', 'REFUND', 'CHARGEBACK', 'FEE', 'ADJUSTMENT'].includes(v.line_type)) issues.push({ lineNo, reason: 'line_type 不在 PAYMENT / REFUND / CHARGEBACK / FEE / ADJUSTMENT 中' });
    if (!['CREDIT', 'DEBIT'].includes(v.direction)) issues.push({ lineNo, reason: 'direction 只能为 CREDIT 或 DEBIT' });
    if (!positiveDecimal.test(v.amount)) issues.push({ lineNo, reason: 'amount 必须是非负十进制金额' });
    if (!positiveDecimal.test(v.fee_amount)) issues.push({ lineNo, reason: 'fee_amount 必须是非负十进制金额（没有填 0）' });
    if (!!v.reference_type !== !!v.external_transaction_id) issues.push({ lineNo, reason: 'reference_type 与 external_transaction_id 必须同时填写或同时为空' });
    if (v.reference_type && !/^[A-Z][A-Z0-9_]{0,31}$/.test(v.reference_type)) issues.push({ lineNo, reason: 'reference_type 格式不正确' });
    if (v.external_transaction_id && !visibleAscii.test(v.external_transaction_id)) issues.push({ lineNo, reason: 'external_transaction_id 必须是 1–128 位可见 ASCII' });
    if (!v.external_transaction_id && !['ADJUSTMENT', 'FEE'].includes(v.line_type)) issues.push({ lineNo, reason: `${v.line_type} 行必须带外部交易号` });
    if (v.transaction_date && !validDate(v.transaction_date)) issues.push({ lineNo, reason: 'transaction_date 必须是 YYYY-MM-DD' });
    const raw = Object.fromEntries(extra.filter(name => v[name]).map(name => [name.slice(0, 64), v[name]]));
    if (Object.values(raw).some(value => value.length > 1024)) issues.push({ lineNo, reason: '渠道特有列的值超过 1024 字符' });
    return { lineNo, lineType: v.line_type, direction: v.direction, amount: v.amount, feeAmount: v.fee_amount, referenceType: v.reference_type || null,
      externalTransactionId: v.external_transaction_id || null, transactionDate: v.transaction_date || null,
      rawAttributes: Object.keys(raw).length ? raw : null };
  });
  if (issues.length) return { file: null, issues };
  return { file: { header: { clearingAccountCode: header.clearing_account_code, channelBatchRef: header.channel_batch_ref,
    settlementDate: header.settlement_date, currency: header.currency, grossAmount: header.gross_amount, feeAmount: header.fee_amount,
    netAmount: header.net_amount, cashDirection: header.cash_direction }, lines }, issues };
}

export interface BankStatementFile {
  header: { bankAccountLast4: string; statementRef: string; periodStart: string; periodEnd: string; currency: string; openingBalance: string; closingBalance: string };
  lines: { lineNo: number; bankTransactionRef: string; valueDate: string; direction: string; amount: string; counterpartyName: string | null; referenceText: string | null }[];
}

/**
 * 解析标准银行对账单 CSV（technique 2.5）：结构同渠道账单（两行 #HEADER + 明细列名 + 明细）
 * <li>只做结构与格式校验；期初 + 流水 = 期末、与上一张的连续性、重复流水由 reconciliation 完成导入时处理。</li>
 * <li>expectedLast4 为页面所选银行账户的末四位，不一致直接拒绝（technique 2.5）。</li>
 */
export function parseBankStatementCsv(text: string, expectedLast4?: string): { file: BankStatementFile | null; issues: CsvIssue[] } {
  let rows: string[][];
  try { rows = parseCsv(text); } catch (error) { return { file: null, issues: [{ lineNo: null, reason: (error as Error).message }] }; }
  const { issues, header, columns, details } = splitSections(rows, bankHeaderColumns, bankLineColumns);
  if (issues.length) return { file: null, issues };
  if (!/^[A-Za-z0-9]{4}$/.test(header.bank_account_last4)) issues.push({ lineNo: null, reason: 'bank_account_last4 必须是 4 位字母或数字' });
  else if (expectedLast4 && header.bank_account_last4 !== expectedLast4) {
    issues.push({ lineNo: null, reason: `文件末四位 ${header.bank_account_last4} 与所选银行账户 ${expectedLast4} 不一致` });
  }
  if (!visibleAscii.test(header.statement_ref)) issues.push({ lineNo: null, reason: 'statement_ref 必须是 1–128 位可见 ASCII' });
  if (!validDate(header.period_start) || !validDate(header.period_end)) issues.push({ lineNo: null, reason: 'period_start / period_end 必须是 YYYY-MM-DD' });
  else if (header.period_end < header.period_start) issues.push({ lineNo: null, reason: 'period_end 不能早于 period_start' });
  if (!/^[A-Z]{3}$/.test(header.currency)) issues.push({ lineNo: null, reason: 'currency 必须是三位大写币种' });
  checkHeaderAmount(issues, header, 'opening_balance', true);
  checkHeaderAmount(issues, header, 'closing_balance', true);
  if (details.length > maxStatementLines) issues.push({ lineNo: null, reason: `流水超过 ${maxStatementLines} 行` });
  const lines = details.map((cells, index) => {
    const v = cellsOf(columns, cells);
    const lineNo = index + 1;
    checkLineNo(issues, index, v.line_no);
    if (cells.length !== columns.length) issues.push({ lineNo, reason: `列数应为 ${columns.length}，实际为 ${cells.length}` });
    if (!visibleAscii.test(v.bank_transaction_ref)) issues.push({ lineNo, reason: 'bank_transaction_ref 必须是 1–128 位可见 ASCII' });
    if (!validDate(v.value_date)) issues.push({ lineNo, reason: 'value_date 必须是 YYYY-MM-DD' });
    if (!['CREDIT', 'DEBIT'].includes(v.direction)) issues.push({ lineNo, reason: 'direction 只能为 CREDIT 或 DEBIT' });
    if (!positiveDecimal.test(v.amount) || /^0(\.0+)?$/.test(v.amount)) issues.push({ lineNo, reason: 'amount 必须是正的十进制金额' });
    if (v.counterparty_name.length > 255) issues.push({ lineNo, reason: 'counterparty_name 超过 255 字符' });
    if (v.reference_text.length > 500) issues.push({ lineNo, reason: 'reference_text 超过 500 字符' });
    return { lineNo, bankTransactionRef: v.bank_transaction_ref, valueDate: v.value_date, direction: v.direction, amount: v.amount,
      counterpartyName: v.counterparty_name || null, referenceText: v.reference_text || null };
  });
  if (issues.length) return { file: null, issues };
  return { file: { header: { bankAccountLast4: header.bank_account_last4, statementRef: header.statement_ref, periodStart: header.period_start,
    periodEnd: header.period_end, currency: header.currency, openingBalance: header.opening_balance, closingBalance: header.closing_balance }, lines }, issues };
}

/** 读取文件文本：严格 UTF-8（非法字节报错），去掉 BOM。 */
export function decodeUtf8(bytes: ArrayBuffer): string {
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  return text.startsWith('﻿') ? text.slice(1) : text;
}

/** 整个原文件的 SHA-256，64 位小写十六进制（Web Crypto）。 */
export async function sha256Hex(bytes: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

/** 每段最多 100 行，且序列化后不超过 maxBytes（Gateway 路由上限 64 KiB，留出余量）。 */
export function chunkLines<T>(lines: readonly T[], maxLines = 100, maxBytes = 60000): T[][] {
  const encoder = new TextEncoder();
  const chunks: T[][] = []; let current: T[] = []; let size = 12;
  for (const line of lines) {
    const bytes = encoder.encode(JSON.stringify(line)).length + 1;
    if (current.length && (current.length >= maxLines || size + bytes > maxBytes)) { chunks.push(current); current = []; size = 12; }
    current.push(line); size += bytes;
  }
  if (current.length) chunks.push(current);
  return chunks;
}
