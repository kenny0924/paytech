import { ApiError } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import { decodeAccountingPeriod, entityNoPattern, decimalIdPattern, sumDecimals } from './finance.ts';
import type { AccountingPeriod, BalanceBucket, ChartAccount, FinanceErrorRule, LedgerAccount, Side } from './finance.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('finance');

/**
 * 财务控制台三页（调整、期间关账、记账规则）与试算发起的路径、解码、权限显示规则和纯函数
 * <li>BFF 路径见 C-report 第 4 节；无请求体的命令（计费事实批准 / 作废、发起关账、发起试算、规则版本提交 / 审批 / 驳回 / 作废）不带 Body。</li>
 * <li>编制人与复核人由 BFF 从会话填入，页面不传操作人；本人不能复核的规则在页面隐藏按钮，服务端仍以错误码兜底。</li>
 */
export const workflowPaths = {
  manualAdjustments: '/finance/manual-adjustments/v1',
  manualAdjustment: (manualAdjustmentId: string) => `/finance/manual-adjustments/v1/${manualAdjustmentId}`,
  reviewManualAdjustment: (manualAdjustmentId: string, action: 'approve' | 'reject') => `/finance/manual-adjustments/v1/${manualAdjustmentId}/${action}`,
  billingFacts: '/finance/billing-facts/v1',
  billingFact: (factId: string) => `/finance/billing-facts/v1/${factId}`,
  billingFactAction: (factId: string, action: 'approve' | 'void') => `/finance/billing-facts/v1/${factId}/${action}`,
  periodCloseBoard: '/finance/period-close/v1/board',
  periodCloses: (accountingPeriodId: string) => `/finance/accounting-periods/v1/${accountingPeriodId}/closes`,
  periodClose: (accountingPeriodId: string, closeNo: string) => `/finance/accounting-periods/v1/${accountingPeriodId}/closes/${closeNo}`,
  reviewPeriodClose: (accountingPeriodId: string, closeNo: string, action: 'approve' | 'reject') =>
    `/finance/accounting-periods/v1/${accountingPeriodId}/closes/${closeNo}/${action}`,
  periodReopens: (accountingPeriodId: string) => `/finance/accounting-periods/v1/${accountingPeriodId}/reopens`,
  runTrialBalance: (ledgerBookId: string, periodId: string) => `/finance/ledger-books/v1/${ledgerBookId}/periods/${periodId}/trial-balances`,
  ruleBundles: '/finance/accounting-rule-bundles/v1',
  ruleBundle: (bundleCode: string) => `/finance/accounting-rule-bundles/v1/${bundleCode}`,
  ruleVersions: (bundleCode: string) => `/finance/accounting-rule-bundles/v1/${bundleCode}/versions`,
  ruleVersion: (bundleCode: string, bundleVer: string) => `/finance/accounting-rule-bundles/v1/${bundleCode}/versions/${bundleVer}`,
  ruleVersionAction: (bundleCode: string, bundleVer: string, action: 'submit' | 'approve' | 'reject' | 'void') =>
    `/finance/accounting-rule-bundles/v1/${bundleCode}/versions/${bundleVer}/${action}`,
} as const;

/** 按钮资源（C-report 第 3 节）。 */
export const financeActions = {
  ledgerManage: 'OPERATIONS_FINANCE_LEDGER_MANAGE',
  adjustmentsManage: 'OPERATIONS_FINANCE_ADJUSTMENTS_MANAGE',
  adjustmentsApprove: 'OPERATIONS_FINANCE_ADJUSTMENTS_APPROVE',
  periodCloseManage: 'OPERATIONS_FINANCE_PERIOD_CLOSE_MANAGE',
  periodCloseApprove: 'OPERATIONS_FINANCE_PERIOD_CLOSE_APPROVE',
  rulesManage: 'OPERATIONS_FINANCE_ACCOUNTING_RULES_MANAGE',
  rulesApprove: 'OPERATIONS_FINANCE_ACCOUNTING_RULES_APPROVE',
} as const;

export function hasAction(resources: readonly ConsoleResource[], resourceCode: string): boolean {
  return resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === resourceCode);
}

// ---------- 类型 ----------

export type AdjustmentStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'POSTED' | 'CANCELLED';
export type BillingFactStatus = 'DRAFT' | 'APPROVED' | 'SUPERSEDED' | 'VOIDED';
export type PeriodCloseStatus = 'CREATED' | 'RUNNING' | 'PENDING_CHECK' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'FAILED';
export type PeriodCloseType = 'CLOSE' | 'REOPEN';
export type RuleVersionStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'ACTIVE' | 'REJECTED' | 'RETIRED' | 'VOIDED';
export type ApprovalRole = 'MAKER' | 'CHECKER';
export type ApprovalDecision = 'APPROVED' | 'REJECTED';

export interface Approval { approvalRole: ApprovalRole; actorId: string; decision: ApprovalDecision; approvedHash: string | null; decisionTime: string }
export interface ManualAdjustmentSummary {
  manualAdjustmentId: string; adjustmentNo: string; ledgerBookId: string; accountingPeriodId: string; currency: string; reasonCode: string;
  status: AdjustmentStatus; totalDebit: string; totalCredit: string; makerId: string; submittedTime: string; journalEntryId: string | null;
}
export interface ManualAdjustmentLine { lineNo: number; accountId: string; entrySide: Side; amount: string; lineDescription: string }
export interface ManualAdjustmentDetail extends ManualAdjustmentSummary {
  fileId: string | null; fileContentHash: string | null; contentHash: string; lines: ManualAdjustmentLine[]; approvals: Approval[];
  journalSequence: string | null; postingDate: string | null;
}
export interface SubmittedAdjustment { manualAdjustmentId: string; adjustmentNo: string; contentHash: string; status: AdjustmentStatus }
export interface AdjustmentReview { manualAdjustmentId: string; status: AdjustmentStatus; journalEntryId: string | null; journalSequence: string | null; postingDate: string | null }

export interface BillingFactSummary {
  factId: string; factType: string; sourceReference: string; clientId: string | null; status: BillingFactStatus; grossAmount: string;
  makerId: string | null; checkerId: string | null; createTime: string; approvedTime: string | null;
}
export interface BillingFactLine {
  lineNo: string; componentType: string; payerRole: string; payerId: string; payeeRole: string; payeeId: string; direction: string;
  postedAmount: string; targetBucket: string | null;
}
export interface BillingFactRef { refType: string; refId: string }
export interface BillingFact {
  factId: string; factVersion: string; supersedesFactId: string | null; parentFactId: string | null; factType: string; originType: string;
  status: BillingFactStatus; sourceSystem: string; sourceEventKey: string; businessTime: string; recordedTime: string | null; clientId: string | null;
  entityNo: string; localPartnerId: string | null; financeEntityId: string | null; currency: string; grossAmount: string; makerId: string | null;
  checkerId: string | null; approvedTime: string | null; publishedAt: string | null; refs: BillingFactRef[]; lines: BillingFactLine[];
}

export interface PeriodClose {
  periodCloseId: string; periodId: string; closeNo: string; closeType: PeriodCloseType; status: PeriodCloseStatus; makerId: string;
  checkerId: string | null; closeHash: string | null; failedStepType: string | null; reopensCloseNo: string | null; reopenReasonCode: string | null;
  reopenRequestRef: string | null; createTime: string;
}
export interface PeriodCloseStep { stepNo: number; stepType: string; status: string; resultHash: string | null; completedTime: string | null }
export interface PeriodCloseBook { ledgerBookId: string; asOfSequence: string; finalTrialRunId: string | null; postingInstructionId: string | null; status: string }
export interface PeriodCloseDetail extends PeriodClose { steps: PeriodCloseStep[]; books: PeriodCloseBook[]; approvals: Approval[] }
export interface PeriodBoardItem { period: AccountingPeriod; latestClose: PeriodClose | null }
export interface PeriodBoard { fiscalCalendarId: string; periods: PeriodBoardItem[] }

export interface RuleBundleSummary { bundleCode: string; status: string; currentBundleVer: string | null; currentEffectiveFrom: string | null }
export interface RuleVersionSummary { versionId: string; bundleVer: string; status: RuleVersionStatus; effectiveFrom: string; effectiveTo: string | null }
export interface RuleBundle { bundleId: string; bundleCode: string; status: string; currentBundleVer: string | null; versions: RuleVersionSummary[] }
export interface RuleLine {
  instructionType: string; lineNo: number; lineCode: string; amountSourceCode: string; ownerRole: string; accountCode: string;
  balanceBucket: BalanceBucket; entrySide: Side; requiredFlag: boolean;
}
export interface RuleVersion {
  versionId: string; bundleCode: string; bundleVer: string; effectiveFrom: string; effectiveTo: string | null; status: RuleVersionStatus;
  makerId: string; checkerId: string | null; approvedTime: string | null; lines: (RuleLine & { ruleLineId: string })[];
}

// ---------- 目录与显示名 ----------

/**
 * 显示名在 finance.enum.<类型>.<值>（enumLabel 取值，未知值显示原代码）：adjustmentStatus、billingFactStatus、periodCloseStatus、
 * periodCloseType、periodStatus、ruleVersionStatus、approvalRole、approvalDecision、adjustmentReason、reopenReason、closeStep、
 * closeStepStatus、factComponent、factPartyRole、factDirection、factType、ruleBundle、instructionType、amountSource、ruleOwnerRole。
 * 这里只保留代码顺序（下拉选项与解码白名单）。
 */
export const adjustmentStatuses: readonly AdjustmentStatus[] = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED', 'CANCELLED'];
export const billingFactStatuses: readonly BillingFactStatus[] = ['DRAFT', 'APPROVED', 'SUPERSEDED', 'VOIDED'];
export const periodCloseStatuses: readonly PeriodCloseStatus[] = ['CREATED', 'RUNNING', 'PENDING_CHECK', 'APPROVED', 'REJECTED', 'COMPLETED', 'FAILED'];
export const ruleVersionStatuses: readonly RuleVersionStatus[] = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'REJECTED', 'RETIRED', 'VOIDED'];
/** 工作流状态标签色：待处理为金色，完成为绿色，驳回 / 失败为红色，作废 / 退役为灰色。 */
export const workflowStatusColors: Record<string, string> = {
  SUBMITTED: 'gold', PENDING_CHECK: 'gold', PENDING_APPROVAL: 'gold', RUNNING: 'gold', CREATED: 'blue', DRAFT: 'orange', APPROVED: 'blue',
  POSTED: 'green', COMPLETED: 'green', ACTIVE: 'green', REJECTED: 'red', FAILED: 'red', SUPERSEDED: 'default', VOIDED: 'default',
  RETIRED: 'default', CANCELLED: 'default', OPEN: 'green', FUTURE: 'blue', CLOSING: 'gold', REOPENING: 'gold', CLOSED: 'default',
  SUCCEEDED: 'green', PENDING: 'default', SKIPPED: 'default', LOCKED: 'gold', FINALIZED: 'green',
};

/**
 * 人工调整原因代码（ledger 目录非固定枚举，本期页面固定下拉；需求「不做」原因目录维护）
 * <li>外部单据号写在分录行说明中（需求 Q4：本期不做附件上传）。</li>
 */
export const adjustmentReasonCodes = ['ACCOUNTING_CORRECTION', 'RECLASSIFICATION', 'ACCRUAL', 'AUDIT_ADJUSTMENT', 'OTHER'] as const;
export const reopenReasonCodes = ['ACCOUNTING_CORRECTION', 'LATE_TRANSACTION', 'AUDIT_ADJUSTMENT'] as const;

/** 计费事实登记目录（billing FactComponentType / FactPartyRole / FactLineDirection，MANUAL_ADJUSTMENT 允许的子集）。 */
export const factComponentCodes = ['CLIENT_PRINCIPAL', 'CLIENT_FEE', 'PARTNER_SHARE', 'PLATFORM_SERVICE_CHARGE', 'SELLER_TAX', 'CHANNEL_COST'] as const;
export const factPartyRoles = [
  'CLIENT', 'LOCAL_PARTNER', 'CHANNEL', 'OPERATING_ENTITY', 'FINANCE_ENTITY', 'TAX_AUTHORITY', 'CASH_ACCOUNT', 'FX_ACCOUNT',
] as const;
export const factDirections = ['CHARGE', 'REVERSAL', 'ADJUSTMENT'] as const;
export const factTargetBuckets = ['AVAILABLE', 'PENDING'] as const;
export const factTypes = [
  'PAYMENT', 'REFUND', 'DUPLICATE_CAPTURE', 'DUPLICATE_COMPENSATION', 'DISPUTE', 'COST_ADJUSTMENT',
  'CHANNEL_SETTLEMENT', 'PAYOUT_SETTLED', 'TAX_PAID', 'MANUAL_ADJUSTMENT', 'FX_SELL', 'FX_BUY',
] as const;

/** 规则包显示名由代码映射（finance.enum.ruleBundle.*），未知代码显示代码本身。 */
export function ruleBundleName(bundleCode: string): string {
  return enumLabel(t, 'enum.ruleBundle', bundleCode);
}
/** ledger AutoInstructionType / AmountSource / RuleOwnerRole 目录。 */
export const instructionTypes = [
  'PAYMENT_SUCCEEDED', 'PLATFORM_SERVICE_CHARGE_PARTNER', 'PLATFORM_SERVICE_CHARGE_PLATFORM', 'ECONOMIC_ADJUSTMENT', 'CHANNEL_COST_ADJUSTMENT',
] as const;
export const amountSourceCodes = [
  'GROSS_AMOUNT', 'CLIENT_PRINCIPAL', 'CLIENT_FEE', 'SELLER_TAX', 'CHANNEL_COST', 'PARTNER_SHARE', 'PLATFORM_SERVICE_CHARGE',
] as const;
export const ruleOwnerRoles = ['BOOK_ENTITY', 'CHANNEL_ACCOUNT', 'PLATFORM_FINANCE_ENTITY', 'TRANSACTION_OE', 'TAX_AUTHORITY'] as const;

/** 规则行科目所在的科目表（ledger AccountingCodes.STANDARD_CHART_CODE）。 */
export const standardChartCode = 'STANDARD';

/** Client / Local Partner 控制科目：人工调整与规则行都不能直接使用，Owner 影响走计费事实。 */
export const controlAccountCodes: readonly string[] = [
  'CLIENT_PAYABLE', 'CLIENT_SECURITY_DEPOSIT', 'CLIENT_RECOVERY_RECEIVABLE', 'PARTNER_PAYABLE', 'PARTNER_RECOVERY_RECEIVABLE',
];

// ---------- 错误码（technique D4） ----------

/** ledger LedgerErrorCode 的页面落点：字段名对应制单表单，其余在表单 / 抽屉顶部。 */
export const ledgerWorkflowErrorTargets: Readonly<Record<string, FinanceErrorRule>> = {
  SELF_REVIEW_FORBIDDEN: { field: null, messageKey: 'error.ledgerWorkflow.SELF_REVIEW_FORBIDDEN' },
  REVIEW_CONTENT_CHANGED: { field: null, messageKey: 'error.ledgerWorkflow.REVIEW_CONTENT_CHANGED' },
  REVIEW_STATUS_INVALID: { field: null, messageKey: 'error.ledgerWorkflow.REVIEW_STATUS_INVALID' },
  ADJUSTMENT_UNBALANCED: { field: 'lines', messageKey: 'error.ledgerWorkflow.ADJUSTMENT_UNBALANCED' },
  ADJUSTMENT_PERIOD_NOT_OPEN: { field: 'accountingPeriodId', messageKey: 'error.ledgerWorkflow.ADJUSTMENT_PERIOD_NOT_OPEN' },
  ADJUSTMENT_ACCOUNT_INVALID: { field: 'lines', messageKey: 'error.ledgerWorkflow.ADJUSTMENT_ACCOUNT_INVALID' },
  OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN: { field: 'lines', messageKey: 'error.ledgerWorkflow.OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN' },
  PERIOD_CLOSE_ORDER_INVALID: { field: null, messageKey: 'error.ledgerWorkflow.PERIOD_CLOSE_ORDER_INVALID' },
  PERIOD_NOT_OPEN: { field: null, messageKey: 'error.ledgerWorkflow.PERIOD_NOT_OPEN' },
  PERIOD_REOPEN_NOT_ALLOWED: { field: null, messageKey: 'error.ledgerWorkflow.PERIOD_REOPEN_NOT_ALLOWED' },
  RULE_VERSION_IN_PROGRESS: { field: null, messageKey: 'error.ledgerWorkflow.RULE_VERSION_IN_PROGRESS' },
  RULE_VERSION_STATUS_INVALID: { field: null, messageKey: 'error.ledgerWorkflow.RULE_VERSION_STATUS_INVALID' },
  RULE_SUBMITTER_NOT_MAKER: { field: null, messageKey: 'error.ledgerWorkflow.RULE_SUBMITTER_NOT_MAKER' },
  RULE_LINE_INVALID: { field: 'lines', messageKey: 'error.ledgerWorkflow.RULE_LINE_INVALID' },
  RULE_EFFECTIVE_FROM_INVALID: { field: 'effectiveFrom', messageKey: 'error.ledgerWorkflow.RULE_EFFECTIVE_FROM_INVALID' },
  LEDGER_BOOK_NOT_FOUND: { field: null, messageKey: 'error.ledgerWorkflow.LEDGER_BOOK_NOT_FOUND' },
  ACCOUNTING_PERIOD_NOT_FOUND: { field: null, messageKey: 'error.ledgerWorkflow.ACCOUNTING_PERIOD_NOT_FOUND' },
};

/** billing BillingFactErrorCode 的页面落点。 */
export const billingErrorTargets: Readonly<Record<string, FinanceErrorRule>> = {
  SELF_REVIEW_FORBIDDEN: { field: null, messageKey: 'error.billing.SELF_REVIEW_FORBIDDEN' },
  FACT_NOT_DRAFT: { field: null, messageKey: 'error.billing.FACT_NOT_DRAFT' },
  FACT_LINE_INVALID: { field: 'lines', messageKey: 'error.billing.FACT_LINE_INVALID' },
  FACT_SUPERSEDE_INVALID: { field: 'supersedesFactId', messageKey: 'error.billing.FACT_SUPERSEDE_INVALID' },
};

/** 服务端字段路径（如 lines[0].amount）转为 antd 表单名路径。 */
export function formFieldPath(field: string): (string | number)[] {
  return field.split(/[.[\]]/).filter(Boolean).map(part => /^\d+$/.test(part) ? Number(part) : part);
}

// ---------- 解码 ----------

const hashPattern = /^[0-9a-f]{64}$/;
const currencyPattern = /^[A-Z]{3}$/;
const decimalPattern = /^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/;
const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && decimalIdPattern.test(v);
const text = (v: unknown, max = 256) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const code = (v: unknown) => typeof v === 'string' && codePattern.test(v);
const decimal = (v: unknown) => typeof v === 'string' && decimalPattern.test(v);
const digits = (v: unknown) => typeof v === 'string' && /^[0-9]{1,19}$/.test(v);
const hash = (v: unknown) => typeof v === 'string' && hashPattern.test(v);
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const isoDate = (v: unknown) => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v);
const integer = (v: unknown, min: number, max: number) => Number.isInteger(v) && Number(v) >= min && Number(v) <= max;
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
const array = (v: unknown): v is unknown[] => Array.isArray(v);
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

const billingStatuses = billingFactStatuses;
const closeStatuses = periodCloseStatuses;
const ruleStatuses = ruleVersionStatuses;
const sides = ['DEBIT', 'CREDIT'];
const buckets = ['PENDING', 'AVAILABLE', 'LOCKED', 'NONE'];

function decodeApproval(hashKey: 'approvedContentHash' | 'approvedCloseHash') {
  return (value: unknown): Approval => {
    const v = value as Record<string, unknown>;
    require(object(v) && oneOf(['MAKER', 'CHECKER'])(v.approvalRole) && id(v.actorId) && oneOf(['APPROVED', 'REJECTED'])(v.decision)
      && optional(hash)(v[hashKey]) && instant(v.decisionTime));
    return { approvalRole: v.approvalRole as ApprovalRole, actorId: v.actorId as string, decision: v.decision as ApprovalDecision,
      approvedHash: orNull(v[hashKey] as string | null), decisionTime: v.decisionTime as string };
  };
}

function adjustmentHead(v: Record<string, unknown>) {
  return id(v.manualAdjustmentId) && text(v.adjustmentNo, 64) && id(v.ledgerBookId) && id(v.accountingPeriodId) && typeof v.currency === 'string'
    && currencyPattern.test(v.currency) && text(v.reasonCode, 64) && oneOf(adjustmentStatuses)(v.status) && decimal(v.totalDebit) && decimal(v.totalCredit)
    && id(v.makerId) && instant(v.submittedTime) && optional(id)(v.journalEntryId);
}

/** 人工调整列表元素：必须属于所查账簿。 */
export function decodeManualAdjustmentSummary(ledgerBookId: string) {
  return (value: unknown): ManualAdjustmentSummary => {
    const v = value as ManualAdjustmentSummary;
    require(object(v) && adjustmentHead(v as unknown as Record<string, unknown>) && v.ledgerBookId === ledgerBookId);
    return { ...v, journalEntryId: orNull(v.journalEntryId) };
  };
}

function decodeAdjustmentLine(value: unknown): ManualAdjustmentLine {
  const v = value as ManualAdjustmentLine;
  require(object(v) && integer(v.lineNo, 1, 2147483647) && id(v.accountId) && oneOf(sides)(v.entrySide) && decimal(v.amount) && text(v.lineDescription, 512));
  return v;
}

/** 人工调整详情：主键与请求一致；已过账时分录三项齐全，未过账时都为空。 */
export function decodeManualAdjustmentDetail(manualAdjustmentId: string) {
  return (value: unknown): ManualAdjustmentDetail => {
    const v = value as ManualAdjustmentDetail;
    const raw = value as Record<string, unknown>;
    require(object(v) && adjustmentHead(raw) && v.manualAdjustmentId === manualAdjustmentId && hash(v.contentHash) && optional(id)(v.fileId)
      && optional(hash)(v.fileContentHash) && array(v.lines) && array(v.approvals)
      && (v.journalEntryId ? digits(v.journalSequence) && isoDate(v.postingDate) : v.journalSequence == null && v.postingDate == null));
    return { ...v, fileId: orNull(v.fileId), fileContentHash: orNull(v.fileContentHash), journalEntryId: orNull(v.journalEntryId),
      journalSequence: orNull(v.journalSequence), postingDate: orNull(v.postingDate), lines: v.lines.map(decodeAdjustmentLine),
      approvals: (raw.approvals as unknown[]).map(decodeApproval('approvedContentHash')) };
  };
}

export function decodeSubmittedAdjustment(value: unknown): SubmittedAdjustment {
  const v = value as SubmittedAdjustment;
  require(object(v) && id(v.manualAdjustmentId) && text(v.adjustmentNo, 64) && hash(v.contentHash) && v.status === 'SUBMITTED');
  return v;
}

/** 复核结果：同一调整；批准为 POSTED 且带分录，驳回为 REJECTED 且无分录。 */
export function decodeAdjustmentReview(manualAdjustmentId: string, action: 'approve' | 'reject') {
  return (value: unknown): AdjustmentReview => {
    const v = value as AdjustmentReview;
    require(object(v) && v.manualAdjustmentId === manualAdjustmentId && (action === 'approve'
      ? v.status === 'POSTED' && id(v.journalEntryId) && digits(v.journalSequence) && isoDate(v.postingDate)
      : v.status === 'REJECTED' && v.journalEntryId == null));
    return { ...v, journalEntryId: orNull(v.journalEntryId), journalSequence: orNull(v.journalSequence), postingDate: orNull(v.postingDate) };
  };
}

export function decodeBillingFactSummary(value: unknown): BillingFactSummary {
  const v = value as BillingFactSummary;
  require(object(v) && id(v.factId) && code(v.factType) && text(v.sourceReference, 128) && optional(id)(v.clientId) && oneOf(billingStatuses)(v.status)
    && decimal(v.grossAmount) && optional(id)(v.makerId) && optional(id)(v.checkerId) && instant(v.createTime) && optional(instant)(v.approvedTime));
  return { ...v, clientId: orNull(v.clientId), makerId: orNull(v.makerId), checkerId: orNull(v.checkerId), approvedTime: orNull(v.approvedTime) };
}

const partyId = (v: unknown) => typeof v === 'string' && /^[A-Z0-9]{1,19}$/.test(v);

function decodeFactLine(value: unknown): BillingFactLine {
  const v = value as BillingFactLine;
  require(object(v) && digits(v.lineNo) && code(v.componentType) && code(v.payerRole) && partyId(v.payerId) && code(v.payeeRole) && partyId(v.payeeId)
    && oneOf(['CHARGE', 'REVERSAL', 'ADJUSTMENT'])(v.direction) && decimal(v.postedAmount) && optional(oneOf(factTargetBuckets))(v.targetBucket));
  return { lineNo: v.lineNo, componentType: v.componentType, payerRole: v.payerRole, payerId: v.payerId, payeeRole: v.payeeRole, payeeId: v.payeeId,
    direction: v.direction, postedAmount: v.postedAmount, targetBucket: orNull(v.targetBucket) };
}

function decodeFactRef(value: unknown): BillingFactRef {
  const v = value as BillingFactRef;
  require(object(v) && code(v.refType) && text(v.refId, 64));
  return { refType: v.refType, refId: v.refId };
}

/** 计费事实详情：指定主键时必须一致；草稿没有复核人与批准时间。 */
export function decodeBillingFact(factId?: string) {
  return (value: unknown): BillingFact => {
    const v = value as BillingFact;
    require(object(v) && id(v.factId) && (!factId || v.factId === factId) && digits(v.factVersion) && optional(id)(v.supersedesFactId)
      && optional(id)(v.parentFactId) && code(v.factType) && code(v.originType) && oneOf(billingStatuses)(v.status) && text(v.sourceSystem, 64)
      && text(v.sourceEventKey, 128) && instant(v.businessTime) && optional(instant)(v.recordedTime) && optional(id)(v.clientId)
      && typeof v.entityNo === 'string' && entityNoPattern.test(v.entityNo) && optional(id)(v.localPartnerId) && optional(id)(v.financeEntityId)
      && typeof v.currency === 'string' && currencyPattern.test(v.currency) && decimal(v.grossAmount) && optional(id)(v.makerId) && optional(id)(v.checkerId)
      && optional(instant)(v.approvedTime) && optional(instant)(v.publishedAt) && optional(array)(v.refs) && array(v.lines)
      && (v.status !== 'DRAFT' || (v.checkerId == null && v.approvedTime == null)));
    return { ...v, supersedesFactId: orNull(v.supersedesFactId), parentFactId: orNull(v.parentFactId), recordedTime: orNull(v.recordedTime),
      clientId: orNull(v.clientId), localPartnerId: orNull(v.localPartnerId), financeEntityId: orNull(v.financeEntityId), makerId: orNull(v.makerId),
      checkerId: orNull(v.checkerId), approvedTime: orNull(v.approvedTime), publishedAt: orNull(v.publishedAt),
      refs: (v.refs ?? []).map(decodeFactRef), lines: v.lines.map(decodeFactLine) };
  };
}

/** 计费事实命令回执：同一事实且状态符合命令（登记为草稿、批准为已批准、作废为已作废）。 */
export function decodeBillingFactResult(expected: BillingFactStatus, factId?: string) {
  return (value: unknown): BillingFact => {
    const fact = decodeBillingFact(factId)(value);
    require(fact.status === expected);
    return fact;
  };
}

const closeTypes = ['CLOSE', 'REOPEN'];

/** 关账流程摘要：指定期间时必须属于该期间；重开流程带原因与申请号，关账流程不带。 */
export function decodePeriodClose(periodId?: string) {
  return (value: unknown): PeriodClose => {
    const v = value as PeriodClose;
    require(object(v) && id(v.periodCloseId) && id(v.periodId) && (!periodId || v.periodId === periodId) && /^[1-9][0-9]{0,8}$/.test(String(v.closeNo))
      && typeof v.closeNo === 'string' && oneOf(closeTypes)(v.closeType) && oneOf(closeStatuses)(v.status) && id(v.makerId) && optional(id)(v.checkerId)
      && optional(hash)(v.closeHash) && optional(code)(v.failedStepType) && instant(v.createTime)
      && (v.closeType === 'REOPEN' ? text(v.reopenReasonCode, 64) && text(v.reopenRequestRef, 128) : v.reopenReasonCode == null && v.reopenRequestRef == null)
      && optional(digits)(v.reopensCloseNo));
    return { periodCloseId: v.periodCloseId, periodId: v.periodId, closeNo: v.closeNo, closeType: v.closeType, status: v.status, makerId: v.makerId,
      checkerId: orNull(v.checkerId), closeHash: orNull(v.closeHash), failedStepType: orNull(v.failedStepType), reopensCloseNo: orNull(v.reopensCloseNo),
      reopenReasonCode: orNull(v.reopenReasonCode), reopenRequestRef: orNull(v.reopenRequestRef), createTime: v.createTime };
  };
}

function decodeStep(value: unknown): PeriodCloseStep {
  const v = value as PeriodCloseStep;
  require(object(v) && integer(v.stepNo, 1, 99) && code(v.stepType) && code(v.status) && optional(hash)(v.resultHash) && optional(instant)(v.completedTime));
  return { stepNo: v.stepNo, stepType: v.stepType, status: v.status, resultHash: orNull(v.resultHash), completedTime: orNull(v.completedTime) };
}

function decodeCloseBook(value: unknown): PeriodCloseBook {
  const v = value as PeriodCloseBook;
  require(object(v) && id(v.ledgerBookId) && digits(v.asOfSequence) && optional(id)(v.finalTrialRunId) && optional(id)(v.postingInstructionId) && code(v.status));
  return { ledgerBookId: v.ledgerBookId, asOfSequence: v.asOfSequence, finalTrialRunId: orNull(v.finalTrialRunId),
    postingInstructionId: orNull(v.postingInstructionId), status: v.status };
}

/** 关账详情：期间与序号同请求，步骤按步骤号升序。 */
export function decodePeriodCloseDetail(periodId: string, closeNo: string) {
  return (value: unknown): PeriodCloseDetail => {
    const head = decodePeriodClose(periodId)(value);
    const v = value as Record<string, unknown>;
    require(head.closeNo === closeNo && array(v.steps) && array(v.books) && array(v.approvals));
    const steps = (v.steps as unknown[]).map(decodeStep);
    require(steps.every((step, index) => index === 0 || steps[index - 1].stepNo < step.stepNo));
    return { ...head, steps, books: (v.books as unknown[]).map(decodeCloseBook), approvals: (v.approvals as unknown[]).map(decodeApproval('approvedCloseHash')) };
  };
}

/** 关账回执：期间一致且状态在允许集合内。 */
export function decodePeriodCloseResult(periodId: string, statuses: readonly PeriodCloseStatus[]) {
  return (value: unknown): PeriodClose => {
    const close = decodePeriodClose(periodId)(value);
    require(statuses.includes(close.status));
    return close;
  };
}

/** 关账看板：日历一致；每个期间属于该日历，最近记录属于该期间。 */
export function decodePeriodBoard(fiscalCalendarId: string) {
  return (value: unknown): PeriodBoard => {
    const v = value as PeriodBoard;
    require(object(v) && v.fiscalCalendarId === fiscalCalendarId && array(v.periods));
    return { fiscalCalendarId, periods: (v.periods as unknown[]).map(item => {
      require(object(item));
      const period = decodeAccountingPeriod((item as Record<string, unknown>).period, fiscalCalendarId);
      const latest = (item as Record<string, unknown>).latestClose;
      return { period, latestClose: latest == null ? null : decodePeriodClose(period.accountingPeriodId)(latest) };
    }) };
  };
}

export function decodeRuleBundleList(value: unknown): RuleBundleSummary[] {
  const list = value as Record<string, unknown>;
  require(object(list) && array(list.items));
  return (list.items as unknown[]).map(item => {
    const v = item as RuleBundleSummary;
    require(object(v) && code(v.bundleCode) && code(v.status) && optional(id)(v.currentBundleVer) && optional(instant)(v.currentEffectiveFrom)
      && (v.currentBundleVer == null) === (v.currentEffectiveFrom == null));
    return { bundleCode: v.bundleCode, status: v.status, currentBundleVer: orNull(v.currentBundleVer), currentEffectiveFrom: orNull(v.currentEffectiveFrom) };
  });
}

/** 规则包详情：代码一致，版本号降序。 */
export function decodeRuleBundle(bundleCode: string) {
  return (value: unknown): RuleBundle => {
    const v = value as RuleBundle;
    require(object(v) && id(v.bundleId) && v.bundleCode === bundleCode && code(v.status) && optional(id)(v.currentBundleVer) && array(v.versions));
    const versions = v.versions.map(item => {
      const version = item as RuleVersionSummary;
      require(object(version) && id(version.versionId) && id(version.bundleVer) && oneOf(ruleStatuses)(version.status) && instant(version.effectiveFrom)
        && optional(instant)(version.effectiveTo));
      return { ...version, effectiveTo: orNull(version.effectiveTo) };
    });
    require(versions.every((version, index) => index === 0 || BigInt(versions[index - 1].bundleVer) > BigInt(version.bundleVer)));
    return { bundleId: v.bundleId, bundleCode, status: v.status, currentBundleVer: orNull(v.currentBundleVer), versions };
  };
}

function decodeRuleLine(value: unknown): RuleLine & { ruleLineId: string } {
  const v = value as RuleLine & { ruleLineId: string };
  require(object(v) && id(v.ruleLineId) && code(v.instructionType) && integer(v.lineNo, 1, 2147483647) && text(v.lineCode, 64) && code(v.amountSourceCode)
    && code(v.ownerRole) && text(v.accountCode, 64) && oneOf(buckets)(v.balanceBucket) && oneOf(sides)(v.entrySide) && typeof v.requiredFlag === 'boolean');
  return v;
}

/** 规则版本：代码一致，指定版本号时一致；可选校验命令后的期望状态。 */
export function decodeRuleVersion(bundleCode: string, bundleVer?: string, statuses?: readonly RuleVersionStatus[]) {
  return (value: unknown): RuleVersion => {
    const v = value as RuleVersion;
    require(object(v) && id(v.versionId) && v.bundleCode === bundleCode && id(v.bundleVer) && (!bundleVer || v.bundleVer === bundleVer)
      && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo) && oneOf(ruleStatuses)(v.status) && (!statuses || statuses.includes(v.status))
      && id(v.makerId) && optional(id)(v.checkerId) && optional(instant)(v.approvedTime) && array(v.lines));
    return { ...v, effectiveTo: orNull(v.effectiveTo), checkerId: orNull(v.checkerId), approvedTime: orNull(v.approvedTime), lines: v.lines.map(decodeRuleLine) };
  };
}

// ---------- 权限显示规则 ----------

/** 复核按钮：有复核权限、单据处于待复核状态、且当前账号不是制单人。 */
export function canReview(canApprove: boolean, status: string, pendingStatus: string, makerId: string | null, actorId: string): boolean {
  return canApprove && status === pendingStatus && !!makerId && makerId !== actorId;
}

/** 规则版本：提交 / 作废只给制单人本人的草稿；审批 / 驳回给非制单人的待审批版本。 */
export function ruleVersionActions(version: Pick<RuleVersion, 'status' | 'makerId'>, actorId: string, canManage: boolean, canApprove: boolean) {
  const ownDraft = canManage && version.status === 'DRAFT' && version.makerId === actorId;
  const review = canReview(canApprove, version.status, 'PENDING_APPROVAL', version.makerId, actorId);
  return { submit: ownDraft, void: ownDraft, approve: review, reject: review };
}

/** 计费事实：非本人可批准草稿；有制单权限可作废草稿。 */
export function billingFactActions(fact: Pick<BillingFact, 'status' | 'makerId'>, actorId: string, canManage: boolean, canApprove: boolean) {
  return { approve: canReview(canApprove, fact.status, 'DRAFT', fact.makerId, actorId), void: canManage && fact.status === 'DRAFT' };
}

// ---------- 期间关账 ----------

const periodOrder = (left: AccountingPeriod, right: AccountingPeriod) => left.fiscalYear - right.fiscalYear || left.periodNo - right.periodNo;

/** 「发起关账」只对最早的开放期间显示；它之前还有未关账（关账中 / 重开中 / 开放）期间时不显示。 */
export function closablePeriodId(periods: readonly AccountingPeriod[]): string | null {
  const ordered = [...periods].sort(periodOrder);
  const index = ordered.findIndex(period => period.status === 'OPEN');
  if (index < 0) return null;
  return ordered.slice(0, index).every(period => period.status === 'CLOSED') ? ordered[index].accountingPeriodId : null;
}

/** 「重开」只对最近已关账期间显示；之后存在关账中或重开中期间时不显示。 */
export function reopenablePeriodId(periods: readonly AccountingPeriod[]): string | null {
  const ordered = [...periods].sort(periodOrder);
  let latest = -1;
  ordered.forEach((period, index) => { if (period.status === 'CLOSED') latest = index; });
  if (latest < 0) return null;
  return ordered.some(period => period.status === 'CLOSING' || period.status === 'REOPENING') ? null : ordered[latest].accountingPeriodId;
}

/** 期间显示：财年 + 期号。 */
export const periodLabel = (period: Pick<AccountingPeriod, 'fiscalYear' | 'periodNo'>) =>
  t('period.label', { fiscalYear: String(period.fiscalYear), periodNo: String(period.periodNo) });

export function sortedBoard(board: PeriodBoard): PeriodBoardItem[] {
  return [...board.periods].sort((left, right) => periodOrder(left.period, right.period));
}

/** 关账失败步骤：优先取摘要的 failedStepType，否则取第一个 FAILED 步骤。 */
export function failedStep(detail: Pick<PeriodCloseDetail, 'failedStepType' | 'steps'>): PeriodCloseStep | null {
  return detail.steps.find(step => step.stepType === detail.failedStepType) ?? detail.steps.find(step => step.status === 'FAILED') ?? null;
}

export interface ReopenFormValues { reasonCode: string; requestRef: string }
export function reopenBody(values: ReopenFormValues) {
  return { reasonCode: values.reasonCode, requestRef: values.requestRef.trim() };
}

/** 试算平衡页签打开某次试算的 URL（账本查询页）。 */
export function trialRunLink(ledgerBookId: string, periodId: string, runNo?: string): string {
  const query = new URLSearchParams({ tab: 'trial', tbBook: ledgerBookId, tbPeriod: periodId, tbView: 'runs', ...(runNo ? { tbRun: runNo } : {}) });
  return `/finance/ledger?${query}`;
}

/** 分录跳转到账本查询：账簿 › 账户 › 流水层级打开分录抽屉（账本 URL 的分录挂在账户下，取调整第一行的账户）。 */
export function journalEntryLink(ledgerBookId: string, accountId: string, journalEntryId: string): string {
  return `/finance/ledger?${new URLSearchParams({ book: ledgerBookId, account: accountId, entry: journalEntryId })}`;
}

// ---------- 人工调整制单 ----------

export interface AdjustmentLineValues { accountId?: string; entrySide?: Side; amount?: string; lineDescription?: string }
export interface AdjustmentFormValues { accountingPeriodId: string; reasonCode: string; lines: AdjustmentLineValues[] }

const amountPattern = /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/;

/** 金额校验：正数、主单位十进制、小数位不超过币种精度；返回错误文案或 null。 */
export function amountError(amount: string | undefined, minorUnit?: number): string | null {
  const value = amount?.trim() ?? '';
  if (!value) return t('validation.amountRequired');
  if (!amountPattern.test(value)) return t('validation.amountPattern');
  if (/^0(\.0+)?$/.test(value)) return t('validation.amountPositive');
  if (minorUnit !== undefined && (value.split('.')[1] ?? '').length > minorUnit) return t('validation.amountScale', { minorUnit });
  return null;
}

/** 制单借贷合计：只计金额合法的行；balanced 要求至少两行、每行完整、合计大于零且借 = 贷。 */
export function draftTotals(lines: readonly AdjustmentLineValues[], minorUnit?: number) {
  const valid = lines.filter(line => line.entrySide && !amountError(line.amount, minorUnit));
  const total = (side: Side) => sumDecimals(['0', ...valid.filter(line => line.entrySide === side).map(line => line.amount!.trim())]);
  const debit = total('DEBIT'); const credit = total('CREDIT');
  const complete = lines.length >= 2 && valid.length === lines.length && lines.every(line => line.accountId && line.lineDescription?.trim());
  const difference = sumDecimals([debit, credit.startsWith('-') ? credit.slice(1) : `-${credit}`]);
  const balanced = complete && difference.replace(/[-.0]/g, '') === '' && debit.replace(/[-.0]/g, '') !== '';
  return { debit, credit, difference, complete, balanced };
}

export function manualAdjustmentBody(values: AdjustmentFormValues, ledgerBookId: string, currency: string) {
  return { ledgerBookId, accountingPeriodId: values.accountingPeriodId, currency, reasonCode: values.reasonCode,
    lines: values.lines.map(line => ({ accountId: line.accountId!, entrySide: line.entrySide!, amount: line.amount!.trim(), lineDescription: line.lineDescription!.trim() })) };
}

/** 制单只列开放期间。 */
export function openPeriods(periods: readonly AccountingPeriod[]): AccountingPeriod[] {
  return periods.filter(period => period.status === 'OPEN').sort(periodOrder);
}

export interface AccountOption { value: string; label: string; account: LedgerAccount }

/**
 * 人工调整可选账户：启用、非控制科目，且科目在科目表中启用并可记账
 * <li>显示「账户号 · 科目编号 名称」；科目不在科目表（尚未加载）时不列出。</li>
 */
export function adjustableAccountOptions(accounts: readonly LedgerAccount[], chartAccounts: readonly ChartAccount[]): AccountOption[] {
  const byCode = new Map(chartAccounts.map(item => [item.accountCode, item]));
  return accounts.flatMap(account => {
    const chart = byCode.get(account.accountCode);
    if (account.accountStatus !== 'ACTIVE' || controlAccountCodes.includes(account.accountCode)) return [];
    if (!chart || chart.accountStatus !== 'ACTIVE' || !chart.postingAllowed) return [];
    return [{ value: account.accountId, label: `${account.accountNo} · ${chart.accountNo} ${chart.accountName}`, account }];
  });
}

// ---------- 计费事实 ----------

/** 计费事实查询：主体与币种必填；时间范围按浏览器本地时区解析后以 UTC 传输。 */
export interface BillingFilterValues {
  entityNo?: string; currency?: string; clientId?: string; status?: string; factType?: string; createdFrom?: string; createdTo?: string;
}
export function billingFilters(values: BillingFilterValues): Record<string, string> {
  const entityNo = values.entityNo?.trim().toUpperCase() ?? '';
  if (!entityNoPattern.test(entityNo) || !currencyPattern.test(values.currency ?? '')) throw new Error(t('validation.billingScope'));
  const filters: Record<string, string> = { entityNo, currency: values.currency! };
  const clientId = values.clientId?.trim();
  if (clientId) {
    if (!decimalIdPattern.test(clientId)) throw new Error(t('validation.clientId'));
    filters.clientId = clientId;
  }
  if (values.status) filters.status = values.status;
  if (values.factType) filters.factType = values.factType;
  for (const key of ['createdFrom', 'createdTo'] as const) {
    if (!values[key]) continue;
    const time = new Date(values[key]!);
    if (!Number.isFinite(time.getTime())) throw new Error(t('validation.time'));
    filters[key] = time.toISOString();
  }
  if (filters.createdFrom && filters.createdTo && filters.createdFrom >= filters.createdTo) throw new Error(t('validation.timeRange'));
  return filters;
}

export interface FactHeaderValues { clientId?: string; entityNo?: string; localPartnerId?: string; financeEntityId?: string }
export interface FactLineValues {
  componentType?: string; payerRole?: string; payerId?: string; payeeRole?: string; payeeId?: string; amount?: string; direction?: string; targetBucket?: string;
}
export interface BillingFactFormValues extends FactHeaderValues {
  currency: string; ticketNo?: string; supersedesFactId?: string; lines: FactLineValues[];
}

/** 由头部决定标识的角色：Client、Local Partner、财务主体与经营主体只能等于头部对应值。 */
export const headerBoundRoles: Readonly<Record<string, keyof FactHeaderValues>> = {
  CLIENT: 'clientId', LOCAL_PARTNER: 'localPartnerId', FINANCE_ENTITY: 'financeEntityId', OPERATING_ENTITY: 'entityNo',
};

/** 明细行收 / 付方标识：头部绑定角色取头部值，其余取输入值。 */
export function resolvePartyId(role: string | undefined, inputId: string | undefined, header: FactHeaderValues): string {
  const key = role ? headerBoundRoles[role] : undefined;
  const value = key ? header[key] : inputId;
  return (value ?? '').trim().toUpperCase();
}

/** 明细行登记规则（billing FACT_LINE_INVALID 的页面预检）：返回错误文案或 null；服务端仍以错误码兜底。 */
export function factLineError(line: FactLineValues, header: FactHeaderValues, minorUnit?: number): string | null {
  if (!line.componentType || !line.payerRole || !line.payeeRole || !line.direction) return t('validation.factLineIncomplete');
  const payerId = resolvePartyId(line.payerRole, line.payerId, header);
  const payeeId = resolvePartyId(line.payeeRole, line.payeeId, header);
  for (const [role, value] of [[line.payerRole, payerId], [line.payeeRole, payeeId]] as const) {
    const roleName = enumLabel(t, 'enum.factPartyRole', role);
    if (!value) return t(headerBoundRoles[role] ? 'validation.partyIdMissingInHeader' : 'validation.partyIdMissing', { role: roleName });
    if (role === 'OPERATING_ENTITY' ? !entityNoPattern.test(value) : !decimalIdPattern.test(value)) return t('validation.partyIdInvalid', { role: roleName });
  }
  if (line.payerRole === line.payeeRole && payerId === payeeId) return t('validation.payerEqualsPayee');
  if (line.targetBucket && line.payeeRole !== 'CLIENT' && line.payeeRole !== 'LOCAL_PARTNER') return t('validation.targetBucketPayee');
  if ((line.componentType === 'PARTNER_SHARE' || line.componentType === 'PLATFORM_SERVICE_CHARGE') && (!header.localPartnerId || !header.financeEntityId)) {
    return t('validation.partnerHeaderRequired');
  }
  if (line.componentType === 'PLATFORM_SERVICE_CHARGE' && line.payeeRole !== 'FINANCE_ENTITY') return t('validation.serviceChargePayee');
  return amountError(line.amount, minorUnit);
}

/** 登记请求体（BFF 固定 sourceSystem 并生成 sourceReference，页面不传）：工单号为可选 TICKET 引用。 */
export function billingFactBody(values: BillingFactFormValues) {
  const header: FactHeaderValues = { clientId: values.clientId?.trim(), entityNo: values.entityNo?.trim().toUpperCase(),
    localPartnerId: values.localPartnerId?.trim() || undefined, financeEntityId: values.financeEntityId?.trim() || undefined };
  const ticket = values.ticketNo?.trim();
  return {
    factType: 'MANUAL_ADJUSTMENT', clientId: header.clientId, entityNo: header.entityNo, currency: values.currency,
    ...(header.localPartnerId ? { localPartnerId: header.localPartnerId, financeEntityId: header.financeEntityId } : {}),
    ...(values.supersedesFactId?.trim() ? { supersedesFactId: values.supersedesFactId.trim() } : {}),
    refs: ticket ? [{ refType: 'TICKET', refId: ticket }] : [],
    lines: values.lines.map(line => ({ componentType: line.componentType!, payerRole: line.payerRole!, payerId: resolvePartyId(line.payerRole, line.payerId, header),
      payeeRole: line.payeeRole!, payeeId: resolvePartyId(line.payeeRole, line.payeeId, header), amount: line.amount!.trim(), direction: line.direction!,
      ...(line.targetBucket ? { targetBucket: line.targetBucket } : {}) })),
  };
}

// ---------- 规则版本 ----------

export type RuleLineValues = Partial<Omit<RuleLine, 'lineNo'>>;

/** 以当前生效版本为模板：按指令类型、行号排序复制规则行，去掉行主键与行号（行号提交时按组内顺序重排）。 */
export function templateRuleLines(version: Pick<RuleVersion, 'lines'> | null | undefined): RuleLineValues[] {
  if (!version) return [];
  return [...version.lines].sort((left, right) => left.instructionType.localeCompare(right.instructionType) || left.lineNo - right.lineNo)
    .map(({ instructionType, lineCode, amountSourceCode, ownerRole, accountCode, balanceBucket, entrySide, requiredFlag }) =>
      ({ instructionType, lineCode, amountSourceCode, ownerRole, accountCode, balanceBucket, entrySide, requiredFlag }));
}

/** 按指令类型分组（保持首次出现顺序），组内按行号。 */
export function groupRuleLines<T extends Pick<RuleLine, 'instructionType' | 'lineNo'>>(lines: readonly T[]): { instructionType: string; lines: T[] }[] {
  const groups = new Map<string, T[]>();
  for (const line of lines) (groups.get(line.instructionType) ?? groups.set(line.instructionType, []).get(line.instructionType)!).push(line);
  return [...groups].map(([instructionType, items]) => ({ instructionType, lines: [...items].sort((left, right) => left.lineNo - right.lineNo) }));
}

/** 规则行集合预检：非空、每行完整、同指令类型内行代码唯一、每个指令类型借贷齐全；返回错误文案或 null。 */
export function ruleLinesError(lines: readonly RuleLineValues[]): string | null {
  if (!lines.length) return t('validation.ruleLinesRequired');
  const fields = ['instructionType', 'lineCode', 'amountSourceCode', 'ownerRole', 'accountCode', 'balanceBucket', 'entrySide'] as const;
  const incomplete = lines.findIndex(line => fields.some(field => !String(line[field] ?? '').trim()));
  if (incomplete >= 0) return t('validation.ruleLineIncomplete', { line: incomplete + 1 });
  for (const group of groupRuleLines(lines.map((line, index) => ({ ...line, instructionType: line.instructionType!, lineNo: index + 1 })))) {
    const codes = group.lines.map(line => line.lineCode!.trim());
    const duplicate = codes.find((value, index) => codes.indexOf(value) !== index);
    if (duplicate) return t('validation.ruleLineCodeDuplicate', { instructionType: enumLabel(t, 'enum.instructionType', group.instructionType), lineCode: duplicate });
    if (!group.lines.some(line => line.entrySide === 'DEBIT') || !group.lines.some(line => line.entrySide === 'CREDIT')) {
      return t('validation.ruleLineSideMissing', { instructionType: enumLabel(t, 'enum.instructionType', group.instructionType) });
    }
  }
  return null;
}

/** 新建版本请求体：生效时间按本地时区解析后转 UTC；行号按指令类型内的顺序从 1 编号。 */
export function ruleVersionBody(effectiveFrom: string, lines: readonly RuleLineValues[]) {
  const time = new Date(effectiveFrom);
  if (!Number.isFinite(time.getTime())) throw new Error(t('validation.effectiveFrom'));
  const counters = new Map<string, number>();
  return { effectiveFrom: time.toISOString(), lines: lines.map(line => {
    const lineNo = (counters.get(line.instructionType!) ?? 0) + 1; counters.set(line.instructionType!, lineNo);
    return { instructionType: line.instructionType!, lineNo, lineCode: line.lineCode!.trim(), amountSourceCode: line.amountSourceCode!, ownerRole: line.ownerRole!,
      accountCode: line.accountCode!, balanceBucket: line.balanceBucket!, entrySide: line.entrySide!, requiredFlag: !!line.requiredFlag };
  }) };
}

/** 规则行可选科目：启用、可记账、非控制科目。 */
export function ruleAccountOptions(chartAccounts: readonly ChartAccount[]) {
  return chartAccounts.filter(item => item.accountStatus === 'ACTIVE' && item.postingAllowed && !controlAccountCodes.includes(item.accountCode))
    .map(item => ({ value: item.accountCode, label: `${item.accountNo} ${item.accountName}` }));
}

/** 科目展示：编号 + 名称；科目表中找不到时显示代码。 */
export function chartAccountLabel(accountCode: string, chartAccounts: ReadonlyMap<string, ChartAccount>): string {
  const item = chartAccounts.get(accountCode);
  return item ? `${item.accountNo} ${item.accountName}` : accountCode;
}

/** 新建版本的生效时间默认值：本地当前分钟。 */
export function defaultEffectiveFrom(now = new Date()): string {
  const two = (value: number) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${two(now.getMonth() + 1)}-${two(now.getDate())}T${two(now.getHours())}:${two(now.getMinutes())}:00`;
}

/** 新建入口：已有草稿或待审批版本时不显示（ledger 以 RULE_VERSION_IN_PROGRESS 兜底）。 */
export function hasVersionInProgress(versions: readonly Pick<RuleVersionSummary, 'status'>[]): boolean {
  return versions.some(version => version.status === 'DRAFT' || version.status === 'PENDING_APPROVAL');
}
