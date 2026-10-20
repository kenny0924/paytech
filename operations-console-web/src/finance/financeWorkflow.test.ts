import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import { financeCommand, financeErrorTarget } from './finance.ts';
import type { AccountingPeriod, ChartAccount, LedgerAccount } from './finance.ts';
import {
  adjustableAccountOptions, amountError, billingErrorTargets, billingFactActions, billingFactBody, billingFilters, canReview, closablePeriodId, decodeAdjustmentReview,
  decodeBillingFact, decodeBillingFactResult, decodeBillingFactSummary, decodeManualAdjustmentDetail, decodeManualAdjustmentSummary, decodePeriodBoard,
  decodePeriodClose, decodePeriodCloseDetail, decodePeriodCloseResult, decodeRuleBundle, decodeRuleBundleList, decodeRuleVersion, decodeSubmittedAdjustment,
  draftTotals, factLineError, failedStep, financeActions, formFieldPath, groupRuleLines, hasAction, hasVersionInProgress, journalEntryLink, ledgerWorkflowErrorTargets,
  manualAdjustmentBody, openPeriods, reopenablePeriodId, reopenBody, resolvePartyId, ruleAccountOptions, ruleBundleName, ruleLinesError, ruleVersionActions,
  ruleVersionBody, sortedBoard, templateRuleLines, trialRunLink, workflowPaths,
} from './financeWorkflow.ts';
import type { RuleVersion } from './financeWorkflow.ts';

const time = '2026-10-20T03:00:00Z';
const hash = 'a'.repeat(64);
const maker = '7300000000000000001'; const checker = '7300000000000000002';
const bookId = '7318800000000000101'; const periodId = '7312000000000000011'; const calendarId = '7312000000000000001';
const invalid = (run: () => unknown) => assert.throws(run, (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE');
const repo = (path: string) => readFileSync(new URL(`../../../${path}`, import.meta.url), 'utf8');
const source = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');

/** 读取 Java CodeMessageEnum 的 Code 与默认消息（多行常量合并后解析）。 */
function javaCodes(path: string): Map<string, string> {
  const text = repo(path).replace(/\s*\n\s*/g, ' ');
  return new Map([...text.matchAll(/([A-Z_]+)\("([A-Z_]+)", "([^"]+)"\)/g)].map(match => [match[2], match[3]]));
}

test('路径：BFF 路径与 C-report 第 4 节一致', () => {
  assert.equal(workflowPaths.manualAdjustments, '/finance/manual-adjustments/v1');
  assert.equal(workflowPaths.reviewManualAdjustment('1', 'approve'), '/finance/manual-adjustments/v1/1/approve');
  assert.equal(workflowPaths.billingFacts, '/finance/billing-facts/v1');
  assert.equal(workflowPaths.billingFactAction('9', 'void'), '/finance/billing-facts/v1/9/void');
  assert.equal(workflowPaths.periodCloseBoard, '/finance/period-close/v1/board');
  assert.equal(workflowPaths.periodClose('3', '2'), '/finance/accounting-periods/v1/3/closes/2');
  assert.equal(workflowPaths.reviewPeriodClose('3', '2', 'reject'), '/finance/accounting-periods/v1/3/closes/2/reject');
  assert.equal(workflowPaths.periodReopens('3'), '/finance/accounting-periods/v1/3/reopens');
  assert.equal(workflowPaths.runTrialBalance(bookId, periodId), `/finance/ledger-books/v1/${bookId}/periods/${periodId}/trial-balances`);
  assert.equal(workflowPaths.ruleBundles, '/finance/accounting-rule-bundles/v1');
  assert.equal(workflowPaths.ruleVersions('STANDARD_TRANSACTION'), '/finance/accounting-rule-bundles/v1/STANDARD_TRANSACTION/versions');
  assert.equal(workflowPaths.ruleVersionAction('STANDARD_TRANSACTION', '3', 'void'), '/finance/accounting-rule-bundles/v1/STANDARD_TRANSACTION/versions/3/void');
});

test('无请求体命令：body 为 undefined 时不带正文（Gateway Body 上限 0），同一目标重试复用 Idempotency-Key', () => {
  const first = financeCommand(workflowPaths.billingFactAction('9', 'approve'), undefined);
  assert.equal(first.body, '');
  assert.equal(financeCommand(first.path, undefined, first).key, first.key);
  for (const file of ['./BillingFactDrawers.tsx', './RuleVersionDrawers.tsx', '../pages/FinancePeriodClose.tsx', './TrialBalanceTab.tsx']) {
    assert.doesNotMatch(source(file), /run\([^,]+, \{\}/, `${file} 不得为无请求体命令发送 {}`);
  }
  assert.match(source('./BillingFactDrawers.tsx'), /billingFactAction\(factId, action\), undefined,/);
  assert.match(source('./RuleVersionDrawers.tsx'), /ruleVersionAction\(bundleCode, bundleVer, action\), undefined,/);
  assert.match(source('../pages/FinancePeriodClose.tsx'), /periodCloses\(item\.period\.accountingPeriodId\), undefined,/);
});

test('能力清单：Web 财务页面与动作与 Platform console-resource-capabilities.json 逐项一致', () => {
  const platform = JSON.parse(repo('paytech/platform-support/paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json')) as typeof pageCapabilities;
  const finance = (items: readonly (typeof pageCapabilities)[number][]) => items.filter(item => item.componentKey.startsWith('OPERATIONS_FINANCE_'));
  assert.deepEqual(finance(pageCapabilities), finance(platform));
  const codes = finance(pageCapabilities).flatMap(page => page.actions.map(action => action.resourceCode));
  for (const action of Object.values(financeActions)) assert.ok(codes.includes(action), action);
});

test('错误码：LedgerErrorCode 与 BillingFactErrorCode 全部登记，默认消息与 Owner 枚举逐字一致', () => {
  const ledger = javaCodes('paytech/ledger/paytech-ledger-api/src/main/java/com/paytech/ledger/api/enums/LedgerErrorCode.java');
  const billing = javaCodes('paytech/billing/paytech-billing-api/src/main/java/com/paytech/billing/api/enums/BillingFactErrorCode.java');
  assert.equal(ledger.size, 15); assert.equal(billing.size, 4);
  for (const [code, message] of ledger) assert.equal(ledgerWorkflowErrorTargets[code]?.message, message, code);
  for (const [code, message] of billing) assert.equal(billingErrorTargets[code]?.message, message, code);
});

test('错误落点：表单内字段就地显示，其余在顶部；未登记 Code 交给通用通知', () => {
  const error = (code: string) => new ApiError(400, code, '');
  assert.deepEqual(financeErrorTarget(error('ADJUSTMENT_PERIOD_NOT_OPEN'), ['accountingPeriodId', 'lines'], ledgerWorkflowErrorTargets),
    { field: 'accountingPeriodId', message: '会计期间未开放（ADJUSTMENT_PERIOD_NOT_OPEN）' });
  assert.deepEqual(financeErrorTarget(error('SELF_REVIEW_FORBIDDEN'), [], billingErrorTargets),
    { field: null, message: '制单人不能复核自己提交的计费事实（SELF_REVIEW_FORBIDDEN）' });
  assert.equal(financeErrorTarget(error('RULE_LINE_INVALID'), [], ledgerWorkflowErrorTargets)?.field, null);
  assert.equal(financeErrorTarget(error('BUSINESS_CHECK_FAILED'), [], ledgerWorkflowErrorTargets), null);
  assert.deepEqual(formFieldPath('lines[2].amount'), ['lines', 2, 'amount']);
  assert.deepEqual(formFieldPath('effectiveFrom'), ['effectiveFrom']);
});

const summary = { manualAdjustmentId: '7400000000000000001', adjustmentNo: 'MA-20261020-0001', ledgerBookId: bookId, accountingPeriodId: periodId, currency: 'USD',
  reasonCode: 'RECLASSIFICATION', status: 'SUBMITTED', totalDebit: '100.00', totalCredit: '100.00', makerId: maker, submittedTime: time, journalEntryId: null };
const adjustmentDetail = { ...summary, fileId: null, fileContentHash: null, contentHash: hash, journalSequence: null, postingDate: null,
  lines: [{ lineNo: 1, accountId: '7318800000000000201', entrySide: 'DEBIT', amount: '100.00', lineDescription: '重分类 OA-1' },
    { lineNo: 2, accountId: '7318800000000000202', entrySide: 'CREDIT', amount: '100.00', lineDescription: '重分类 OA-1' }],
  approvals: [{ approvalRole: 'MAKER', actorId: maker, decision: 'APPROVED', approvedContentHash: hash, decisionTime: time }] };

test('解码：人工调整列表、详情、提交与复核回执', () => {
  assert.equal(decodeManualAdjustmentSummary(bookId)(summary).journalEntryId, null);
  invalid(() => decodeManualAdjustmentSummary('7318800000000000999')(summary));
  invalid(() => decodeManualAdjustmentSummary(bookId)({ ...summary, status: 'UNKNOWN' }));
  const detail = decodeManualAdjustmentDetail(summary.manualAdjustmentId)(adjustmentDetail);
  assert.equal(detail.approvals[0].approvedHash, hash);
  assert.equal(detail.lines.length, 2);
  invalid(() => decodeManualAdjustmentDetail(summary.manualAdjustmentId)({ ...adjustmentDetail, contentHash: 'short' }));
  invalid(() => decodeManualAdjustmentDetail(summary.manualAdjustmentId)({ ...adjustmentDetail, journalEntryId: '7400000000000000009' }));
  const posted = { ...adjustmentDetail, status: 'POSTED', journalEntryId: '7400000000000000009', journalSequence: '1025', postingDate: '2026-10-20' };
  assert.equal(decodeManualAdjustmentDetail(summary.manualAdjustmentId)(posted).journalSequence, '1025');
  assert.equal(decodeSubmittedAdjustment({ manualAdjustmentId: summary.manualAdjustmentId, adjustmentNo: summary.adjustmentNo, contentHash: hash, status: 'SUBMITTED' }).contentHash, hash);
  const approved = { manualAdjustmentId: summary.manualAdjustmentId, status: 'POSTED', journalEntryId: '7400000000000000009', journalSequence: '1025', postingDate: '2026-10-20' };
  assert.equal(decodeAdjustmentReview(summary.manualAdjustmentId, 'approve')(approved).status, 'POSTED');
  invalid(() => decodeAdjustmentReview(summary.manualAdjustmentId, 'reject')(approved));
  assert.equal(decodeAdjustmentReview(summary.manualAdjustmentId, 'reject')({ manualAdjustmentId: summary.manualAdjustmentId, status: 'REJECTED', journalEntryId: null }).journalEntryId, null);
});

const factLine = { lineNo: '1', componentType: 'CLIENT_PRINCIPAL', payerRole: 'OPERATING_ENTITY', payerId: 'AE001', payeeRole: 'CLIENT', payeeId: '1001',
  direction: 'ADJUSTMENT', postedAmount: '25.00', targetBucket: 'AVAILABLE' };
const fact = { factId: '7500000000000000001', factVersion: '0', supersedesFactId: null, parentFactId: null, factType: 'MANUAL_ADJUSTMENT', originType: 'MANUAL',
  status: 'DRAFT', sourceSystem: 'OPERATIONS', sourceEventKey: 'OPS-3f1c', businessTime: time, recordedTime: null, clientId: '1001', entityNo: 'AE001',
  localPartnerId: null, financeEntityId: null, currency: 'USD', grossAmount: '25.00', makerId: maker, checkerId: null, approvedTime: null, publishedAt: null,
  refs: [{ refType: 'TICKET', refId: 'OPS-77' }], lines: [factLine] };

test('解码：计费事实列表、详情与命令回执（草稿无复核人；命令回执校验期望状态）', () => {
  const row = decodeBillingFactSummary({ factId: fact.factId, factType: 'MANUAL_ADJUSTMENT', sourceReference: 'OPS-3f1c', clientId: '1001', status: 'VOIDED',
    grossAmount: '25.00', makerId: maker, checkerId: null, createTime: time, approvedTime: null });
  assert.equal(row.status, 'VOIDED');
  assert.equal(decodeBillingFact(fact.factId)(fact).lines[0].targetBucket, 'AVAILABLE');
  invalid(() => decodeBillingFact('7500000000000000002')(fact));
  invalid(() => decodeBillingFact()({ ...fact, checkerId: checker }));
  invalid(() => decodeBillingFact()({ ...fact, lines: [{ ...factLine, targetBucket: 'LOCKED' }] }));
  assert.equal(decodeBillingFactResult('DRAFT')(fact).factId, fact.factId);
  invalid(() => decodeBillingFactResult('VOIDED', fact.factId)(fact));
  assert.equal(decodeBillingFactResult('APPROVED', fact.factId)({ ...fact, status: 'APPROVED', checkerId: checker, approvedTime: time }).checkerId, checker);
});

const period = (periodNo: number, status: string): AccountingPeriod => ({ accountingPeriodId: `7312000000000000${100 + periodNo}`, fiscalCalendarId: calendarId,
  fiscalYear: 2026, periodNo, startDate: `2026-${String(periodNo).padStart(2, '0')}-01`, endDate: `2026-${String(periodNo).padStart(2, '0')}-28`,
  status: status as AccountingPeriod['status'], reopenCount: 0 });
const close = { periodCloseId: '7600000000000000001', periodId, closeNo: '1', closeType: 'CLOSE', status: 'PENDING_CHECK', makerId: maker, checkerId: null,
  closeHash: hash, failedStepType: null, reopensCloseNo: null, reopenReasonCode: null, reopenRequestRef: null, createTime: time };

test('解码：关账流程、详情（步骤升序）、回执状态与看板（期间属于日历、最近记录属于期间）', () => {
  assert.equal(decodePeriodClose(periodId)(close).closeHash, hash);
  invalid(() => decodePeriodClose(periodId)({ ...close, reopenReasonCode: 'AUDIT_ADJUSTMENT' }));
  const reopen = { ...close, closeNo: '2', closeType: 'REOPEN', reopensCloseNo: '1', reopenReasonCode: 'AUDIT_ADJUSTMENT', reopenRequestRef: 'REQ-1' };
  assert.equal(decodePeriodClose(periodId)(reopen).reopenRequestRef, 'REQ-1');
  invalid(() => decodePeriodClose(periodId)({ ...reopen, reopenRequestRef: null }));
  const steps = [{ stepNo: 1, stepType: 'LOCK_SEQUENCE', status: 'SUCCEEDED', resultHash: hash, completedTime: time },
    { stepNo: 5, stepType: 'RESOLVE_EXCEPTIONS', status: 'FAILED', resultHash: null, completedTime: null }];
  const detail = { ...close, status: 'FAILED', closeHash: null, failedStepType: 'RESOLVE_EXCEPTIONS', steps,
    books: [{ ledgerBookId: bookId, asOfSequence: '1024', finalTrialRunId: null, postingInstructionId: null, status: 'LOCKED' }],
    approvals: [{ approvalRole: 'MAKER', actorId: maker, decision: 'APPROVED', approvedCloseHash: null, decisionTime: time }] };
  const decoded = decodePeriodCloseDetail(periodId, '1')(detail);
  assert.equal(failedStep(decoded)?.stepNo, 5);
  assert.equal(failedStep({ ...decoded, failedStepType: null })?.stepType, 'RESOLVE_EXCEPTIONS');
  invalid(() => decodePeriodCloseDetail(periodId, '1')({ ...detail, steps: [...steps].reverse() }));
  invalid(() => decodePeriodCloseDetail(periodId, '9')(detail));
  assert.equal(decodePeriodCloseResult(periodId, ['COMPLETED'])({ ...close, status: 'COMPLETED', checkerId: checker }).status, 'COMPLETED');
  invalid(() => decodePeriodCloseResult(periodId, ['REJECTED'])(close));
  const p1 = period(1, 'CLOSED'); const p2 = { ...period(2, 'OPEN'), accountingPeriodId: periodId };
  const board = decodePeriodBoard(calendarId)({ fiscalCalendarId: calendarId, periods: [{ period: p2, latestClose: close }, { period: p1, latestClose: null }] });
  assert.deepEqual(sortedBoard(board).map(item => item.period.periodNo), [1, 2]);
  invalid(() => decodePeriodBoard(calendarId)({ fiscalCalendarId: '7312000000000000099', periods: [] }));
  invalid(() => decodePeriodBoard(calendarId)({ fiscalCalendarId: calendarId, periods: [{ period: p1, latestClose: close }] }));
});

const ruleLine = (instructionType: string, lineNo: number, entrySide: string, accountCode = 'CASH_CLEARING') => ({ ruleLineId: `770000000000000${lineNo}${instructionType.length}`,
  instructionType, lineNo, lineCode: `L${lineNo}`, amountSourceCode: 'GROSS_AMOUNT', ownerRole: 'BOOK_ENTITY', accountCode, balanceBucket: 'NONE', entrySide, requiredFlag: true });
const version = { versionId: '7700000000000000001', bundleCode: 'STANDARD_TRANSACTION', bundleVer: '3', effectiveFrom: time, effectiveTo: null, status: 'ACTIVE',
  makerId: maker, checkerId: checker, approvedTime: time,
  lines: [ruleLine('PAYMENT_SUCCEEDED', 2, 'CREDIT'), ruleLine('ECONOMIC_ADJUSTMENT', 1, 'DEBIT'), ruleLine('PAYMENT_SUCCEEDED', 1, 'DEBIT'), ruleLine('ECONOMIC_ADJUSTMENT', 2, 'CREDIT')] };

test('解码：规则包列表（当前版本与生效时间同有同无）、规则包（版本降序）、规则版本（代码 / 版本 / 期望状态）', () => {
  const bundles = decodeRuleBundleList({ items: [{ bundleCode: 'STANDARD_TRANSACTION', status: 'ACTIVE', currentBundleVer: '3', currentEffectiveFrom: time },
    { bundleCode: 'STANDARD_ADJUSTMENT', status: 'ACTIVE', currentBundleVer: null, currentEffectiveFrom: null }] });
  assert.equal(bundles[1].currentBundleVer, null);
  invalid(() => decodeRuleBundleList({ items: [{ bundleCode: 'X', status: 'ACTIVE', currentBundleVer: '3', currentEffectiveFrom: null }] }));
  invalid(() => decodeRuleBundleList([]));
  const summaryOf = (bundleVer: string, status: string) => ({ versionId: `770000000000000000${bundleVer}`, bundleVer, status, effectiveFrom: time, effectiveTo: null });
  const bundle = { bundleId: '7700000000000000100', bundleCode: 'STANDARD_TRANSACTION', status: 'ACTIVE', currentBundleVer: '3',
    versions: [summaryOf('4', 'VOIDED'), summaryOf('3', 'ACTIVE')] };
  assert.equal(decodeRuleBundle('STANDARD_TRANSACTION')(bundle).versions.length, 2);
  invalid(() => decodeRuleBundle('STANDARD_TRANSACTION')({ ...bundle, versions: [...bundle.versions].reverse() }));
  invalid(() => decodeRuleBundle('STANDARD_ADJUSTMENT')(bundle));
  assert.equal(decodeRuleVersion('STANDARD_TRANSACTION', '3', ['ACTIVE', 'APPROVED'])(version).lines.length, 4);
  invalid(() => decodeRuleVersion('STANDARD_TRANSACTION', '3', ['PENDING_APPROVAL'])(version));
  invalid(() => decodeRuleVersion('STANDARD_TRANSACTION', '3')({ ...version, lines: [{ ...version.lines[0], requiredFlag: 'yes' }] }));
  assert.equal(ruleBundleName('STANDARD_TRANSACTION'), '交易记账规则');
  assert.equal(ruleBundleName('STANDARD_ADJUSTMENT'), '调整记账规则');
  assert.equal(ruleBundleName('CUSTOM_X'), 'CUSTOM_X');
});

test('权限显示：BUTTON 资源控制；本人不能复核；规则提交 / 作废只给制单人本人；计费事实作废需制单权限', () => {
  const resources = [{ resourceType: 'BUTTON', resourceCode: financeActions.adjustmentsApprove }, { resourceType: 'MENU', resourceCode: financeActions.adjustmentsManage }] as ConsoleResource[];
  assert.equal(hasAction(resources, financeActions.adjustmentsApprove), true);
  assert.equal(hasAction(resources, financeActions.adjustmentsManage), false);
  assert.equal(canReview(true, 'SUBMITTED', 'SUBMITTED', maker, checker), true);
  assert.equal(canReview(true, 'SUBMITTED', 'SUBMITTED', maker, maker), false);
  assert.equal(canReview(false, 'SUBMITTED', 'SUBMITTED', maker, checker), false);
  assert.equal(canReview(true, 'POSTED', 'SUBMITTED', maker, checker), false);
  assert.deepEqual(ruleVersionActions({ status: 'DRAFT', makerId: maker }, maker, true, true), { submit: true, void: true, approve: false, reject: false });
  assert.deepEqual(ruleVersionActions({ status: 'DRAFT', makerId: maker }, checker, true, true), { submit: false, void: false, approve: false, reject: false });
  assert.deepEqual(ruleVersionActions({ status: 'PENDING_APPROVAL', makerId: maker }, checker, false, true), { submit: false, void: false, approve: true, reject: true });
  assert.deepEqual(ruleVersionActions({ status: 'PENDING_APPROVAL', makerId: maker }, maker, true, true), { submit: false, void: false, approve: false, reject: false });
  assert.deepEqual(billingFactActions({ status: 'DRAFT', makerId: maker }, checker, false, true), { approve: true, void: false });
  assert.deepEqual(billingFactActions({ status: 'DRAFT', makerId: maker }, maker, true, true), { approve: false, void: true });
  assert.deepEqual(billingFactActions({ status: 'APPROVED', makerId: maker }, checker, true, true), { approve: false, void: false });
  assert.equal(hasVersionInProgress([{ status: 'ACTIVE' }, { status: 'VOIDED' }]), false);
  assert.equal(hasVersionInProgress([{ status: 'ACTIVE' }, { status: 'PENDING_APPROVAL' }]), true);
});

test('可操作期间：发起关账只给最早开放期间（之前全部已关账）；重开只给最近已关账期间（无关账中 / 重开中）', () => {
  assert.equal(closablePeriodId([period(3, 'OPEN'), period(1, 'CLOSED'), period(2, 'OPEN')]), period(2, 'OPEN').accountingPeriodId);
  assert.equal(closablePeriodId([period(1, 'CLOSING'), period(2, 'OPEN')]), null);
  assert.equal(closablePeriodId([period(1, 'FUTURE')]), null);
  assert.equal(reopenablePeriodId([period(1, 'CLOSED'), period(2, 'CLOSED'), period(3, 'OPEN')]), period(2, 'CLOSED').accountingPeriodId);
  assert.equal(reopenablePeriodId([period(1, 'CLOSED'), period(2, 'CLOSING')]), null);
  assert.equal(reopenablePeriodId([period(1, 'OPEN')]), null);
  assert.deepEqual(openPeriods([period(2, 'OPEN'), period(1, 'CLOSED'), period(3, 'OPEN')]).map(item => item.periodNo), [2, 3]);
  assert.deepEqual(reopenBody({ reasonCode: 'AUDIT_ADJUSTMENT', requestRef: ' REQ-1 ' }), { reasonCode: 'AUDIT_ADJUSTMENT', requestRef: 'REQ-1' });
});

test('人工调整制单：金额校验、借贷合计实时计算、不平衡 / 不完整禁止提交、请求体不含操作人', () => {
  assert.equal(amountError('', 2), '请填写金额');
  assert.equal(amountError('0.00', 2), '金额必须大于零');
  assert.equal(amountError('1.234', 2), '小数位不能超过 2 位');
  assert.equal(amountError('-1', 2), '金额为正的十进制数，如 100.00');
  assert.equal(amountError('10.5', 2), null);
  const line = (entrySide: 'DEBIT' | 'CREDIT', amount: string) => ({ accountId: '7318800000000000201', entrySide, amount, lineDescription: '说明' });
  const balanced = draftTotals([line('DEBIT', '100.10'), line('CREDIT', '60.05'), line('CREDIT', '40.05')], 2);
  assert.deepEqual([balanced.debit, balanced.credit, balanced.balanced], ['100.10', '100.10', true]);
  const off = draftTotals([line('DEBIT', '100'), line('CREDIT', '90')], 2);
  assert.equal(off.balanced, false); assert.equal(off.complete, true); assert.equal(off.difference, '10');
  assert.equal(draftTotals([line('DEBIT', '100')], 2).complete, false);
  assert.equal(draftTotals([line('DEBIT', '100'), { ...line('CREDIT', '100'), lineDescription: ' ' }], 2).balanced, false);
  assert.equal(draftTotals([line('DEBIT', '0'), line('CREDIT', '0')], 2).balanced, false);
  const body = manualAdjustmentBody({ accountingPeriodId: periodId, reasonCode: 'ACCRUAL', lines: [line('DEBIT', ' 5 '), line('CREDIT', '5')] }, bookId, 'USD');
  assert.equal(body.lines[0].amount, '5');
  assert.equal('makerId' in body, false);
});

test('人工调整可选账户：排除控制科目、停用账户、停用 / 不可记账科目；标签为「账户号 · 科目编号 名称」', () => {
  const chart = (accountCode: string, accountNo: string, extra: Partial<ChartAccount> = {}): ChartAccount => ({ chartAccountId: `71${accountNo}`, chartId: '1',
    accountNo, accountCode, accountName: `${accountCode} 名称`, accountClass: 'ASSET', normalSide: 'DEBIT', postingAllowed: true, parentChartAccountId: null, level: 2,
    accountStatus: 'ACTIVE', ...extra } as ChartAccount);
  const account = (accountId: string, accountCode: string, extra: Partial<LedgerAccount> = {}) => ({ accountId, accountNo: `AE001-USD-${accountId}`, accountCode,
    accountName: '', ownerType: 'OPERATING_ENTITY', ownerId: 'AE001', balanceBucket: 'NONE', normalSide: 'DEBIT', accountStatus: 'ACTIVE', ...extra } as unknown as LedgerAccount);
  const charts = [chart('CASH_CLEARING', '112201'), chart('CLIENT_PAYABLE', '220101'), chart('ACCRUED_EXPENSE', '224101', { accountStatus: 'DISABLED' as ChartAccount['accountStatus'] }),
    chart('OPERATING_EXPENSE', '6601', { postingAllowed: false })];
  const options = adjustableAccountOptions([account('1', 'CASH_CLEARING'), account('2', 'CLIENT_PAYABLE'), account('3', 'ACCRUED_EXPENSE'),
    account('4', 'OPERATING_EXPENSE'), account('5', 'CASH_CLEARING', { accountStatus: 'INACTIVE' as LedgerAccount['accountStatus'] }), account('6', 'UNKNOWN')], charts);
  assert.deepEqual(options.map(option => [option.value, option.label]), [['1', 'AE001-USD-1 · 112201 CASH_CLEARING 名称']]);
  assert.deepEqual(ruleAccountOptions(charts).map(option => option.value), ['CASH_CLEARING']);
});

test('计费事实：查询条件（主体与币种必填、时间区间）、明细行预检、请求体（头部绑定角色取头部值，不含操作人与来源）', () => {
  assert.throws(() => billingFilters({ currency: 'USD' }), /经营主体/);
  assert.throws(() => billingFilters({ entityNo: 'AE001', currency: 'USD', clientId: 'abc' }), /Client ID/);
  assert.throws(() => billingFilters({ entityNo: 'AE001', currency: 'USD', createdFrom: '2026-10-02T00:00:00Z', createdTo: '2026-10-01T00:00:00Z' }), /结束时间/);
  assert.deepEqual(billingFilters({ entityNo: 'ae001', currency: 'USD', status: 'DRAFT', createdFrom: '2026-10-01T00:00:00Z' }),
    { entityNo: 'AE001', currency: 'USD', status: 'DRAFT', createdFrom: '2026-10-01T00:00:00.000Z' });
  const header = { clientId: '1001', entityNo: 'AE001' };
  const valid = { componentType: 'CLIENT_PRINCIPAL', payerRole: 'OPERATING_ENTITY', payeeRole: 'CLIENT', direction: 'ADJUSTMENT', amount: '25.00', targetBucket: 'AVAILABLE' };
  assert.equal(resolvePartyId('CLIENT', '9999', header), '1001');
  assert.equal(resolvePartyId('CHANNEL', ' 42 ', header), '42');
  assert.equal(factLineError(valid, header, 2), null);
  assert.match(factLineError({ ...valid, payeeRole: 'CHANNEL', payeeId: '42' }, header, 2) ?? '', /目标余额桶/);
  assert.match(factLineError({ ...valid, componentType: 'PARTNER_SHARE' }, header, 2) ?? '', /Local Partner/);
  assert.match(factLineError({ ...valid, payerRole: 'CLIENT' }, header, 2) ?? '', /付方与收方不能相同/);
  assert.match(factLineError({ ...valid, payeeRole: 'LOCAL_PARTNER' }, header, 2) ?? '', /标识缺失/);
  assert.equal(factLineError({ ...valid, amount: '1.001' }, header, 2), '小数位不能超过 2 位');
  const body = billingFactBody({ ...header, currency: 'USD', ticketNo: ' T-1 ', lines: [valid] });
  assert.deepEqual(body, { factType: 'MANUAL_ADJUSTMENT', clientId: '1001', entityNo: 'AE001', currency: 'USD', refs: [{ refType: 'TICKET', refId: 'T-1' }],
    lines: [{ componentType: 'CLIENT_PRINCIPAL', payerRole: 'OPERATING_ENTITY', payerId: 'AE001', payeeRole: 'CLIENT', payeeId: '1001', amount: '25.00',
      direction: 'ADJUSTMENT', targetBucket: 'AVAILABLE' }] });
  for (const key of ['makerId', 'sourceSystem', 'sourceReference']) assert.equal(key in body, false, key);
  assert.equal(billingFactBody({ ...header, currency: 'USD', supersedesFactId: '75', lines: [valid] }).supersedesFactId, '75');
});

test('规则版本：模板复制（去掉主键与行号、按类型与行号排序）、分组、行集合预检与请求体行号重排', () => {
  const template = templateRuleLines(version as RuleVersion);
  assert.deepEqual(template.map(line => [line.instructionType, line.lineCode]),
    [['ECONOMIC_ADJUSTMENT', 'L1'], ['ECONOMIC_ADJUSTMENT', 'L2'], ['PAYMENT_SUCCEEDED', 'L1'], ['PAYMENT_SUCCEEDED', 'L2']]);
  assert.equal('ruleLineId' in template[0] || 'lineNo' in template[0], false);
  assert.deepEqual(templateRuleLines(null), []);
  assert.deepEqual(groupRuleLines(version.lines).map(group => [group.instructionType, group.lines.map(line => line.lineNo)]),
    [['PAYMENT_SUCCEEDED', [1, 2]], ['ECONOMIC_ADJUSTMENT', [1, 2]]]);
  assert.equal(ruleLinesError(template), null);
  assert.equal(ruleLinesError([]), '至少需要一行规则');
  assert.equal(ruleLinesError([{ ...template[0], accountCode: '' }, template[1]]), '第 1 行未填写完整');
  assert.match(ruleLinesError([template[0], { ...template[1], lineCode: 'L1' }]) ?? '', /行代码 L1 重复/);
  assert.match(ruleLinesError([template[0]]) ?? '', /人工调整计费事实 缺少借方或贷方行/);
  const body = ruleVersionBody('2026-10-21T08:00:00Z', [template[2], template[0], template[3], template[1]]);
  assert.equal(body.effectiveFrom, '2026-10-21T08:00:00.000Z');
  assert.deepEqual(body.lines.map(line => [line.instructionType, line.lineNo]),
    [['PAYMENT_SUCCEEDED', 1], ['ECONOMIC_ADJUSTMENT', 1], ['PAYMENT_SUCCEEDED', 2], ['ECONOMIC_ADJUSTMENT', 2]]);
  assert.equal('makerId' in body, false);
  assert.throws(() => ruleVersionBody('not a time', template), /生效时间/);
});

test('跳转：试算记录链接到账本查询试算平衡页签，分录链接到账簿下钻', () => {
  assert.equal(trialRunLink(bookId, periodId, '3'), `/finance/ledger?tab=trial&tbBook=${bookId}&tbPeriod=${periodId}&tbView=runs&tbRun=3`);
  assert.equal(trialRunLink(bookId, periodId), `/finance/ledger?tab=trial&tbBook=${bookId}&tbPeriod=${periodId}&tbView=runs`);
  assert.equal(journalEntryLink(bookId, '7318800000000000201', '7400000000000000009'), `/finance/ledger?book=${bookId}&account=7318800000000000201&entry=7400000000000000009`);
});

test('页面接线：三个新页按 BUTTON 资源控制写操作，空状态与无权限提示齐全', () => {
  const adjustments = source('../pages/FinanceAdjustments.tsx');
  assert.match(adjustments, /hasAction\(resources, financeActions\.adjustmentsManage\)/);
  assert.match(adjustments, /hasAction\(resources, financeActions\.adjustmentsApprove\)/);
  assert.match(source('./ManualAdjustmentsTab.tsx'), /canManage && book && <Form\.Item><Button type="primary"/);
  assert.match(source('./ManualAdjustmentsTab.tsx'), /当前账号没有复核权限/);
  assert.match(source('./ManualAdjustmentDrawers.tsx'), /disabled=\{!totals\.balanced \|\| command\.conflict\}/, '不平衡禁止提交');
  assert.match(source('./BillingFactsTab.tsx'), /canManage && <Button type="primary" onClick=\{\(\) => setCreating\(true\)\}>登记计费事实/);
  const close = source('../pages/FinancePeriodClose.tsx');
  assert.match(close, /canManage && row\.period\.accountingPeriodId === closable/);
  assert.match(close, /canManage && row\.period\.accountingPeriodId === reopenable/);
  assert.match(close, /command\.saving && <Alert type="info"/, '关账执行中提示');
  assert.match(close, /选择财务日历后查看期间关账看板/);
  const rules = source('../pages/FinanceAccountingRules.tsx');
  assert.match(rules, /canManage && !!current && !hasVersionInProgress\(current\.versions\)/);
  assert.match(rules, /选择规则包后查看版本/);
  assert.match(source('./RuleVersionDrawers.tsx'), /ruleVersionActions\(version, actorId, canManage, canApprove\)/);
  assert.match(source('./PeriodCloseDrawers.tsx'), /canReview\(canApprove, item\.status, 'PENDING_CHECK', item\.makerId, actorId\)/);
  assert.match(source('./PeriodCloseDrawers.tsx'), /\{ approvedCloseHash: item\.closeHash \}/, '复核回传关账摘要');
  assert.match(source('./ManualAdjustmentDrawers.tsx'), /\{ approvedContentHash: item\.contentHash \}/, '复核回传内容摘要');
});

test('同 Code 不同来源：SELF_REVIEW_FORBIDDEN 显示 Problem detail（各 Owner 默认消息），detail 缺失时退回本表文案', () => {
  const ledgerMessage = ledgerWorkflowErrorTargets.SELF_REVIEW_FORBIDDEN.message;
  const billingMessage = billingErrorTargets.SELF_REVIEW_FORBIDDEN.message;
  assert.notEqual(ledgerMessage, billingMessage);
  const fromBilling = new ApiError(400, 'SELF_REVIEW_FORBIDDEN', 'req-1', billingMessage);
  assert.equal(fromBilling.detail, billingMessage);
  assert.equal(financeErrorTarget(fromBilling, [], ledgerWorkflowErrorTargets)?.message, `${billingMessage}（SELF_REVIEW_FORBIDDEN）`);
  const fromLedger = new ApiError(400, 'SELF_REVIEW_FORBIDDEN', 'req-2', ledgerMessage);
  assert.equal(financeErrorTarget(fromLedger, [], billingErrorTargets)?.message, `${ledgerMessage}（SELF_REVIEW_FORBIDDEN）`);
  assert.equal(financeErrorTarget(new ApiError(400, 'SELF_REVIEW_FORBIDDEN', 'req-3'), [], billingErrorTargets)?.message, `${billingMessage}（SELF_REVIEW_FORBIDDEN）`);
});

test('计费事实登记防重复提交：提交中按钮禁用、抽屉不能关闭，成功后关闭抽屉', () => {
  const drawer = source('./BillingFactDrawers.tsx');
  assert.match(drawer, /loading=\{command\.saving\} disabled=\{command\.saving \|\| command\.conflict\} onClick=\{\(\) => void submit\(\)\}>提交复核/);
  assert.match(drawer, /onClose=\{\(\) => \{ if \(!command\.saving\) onClose\(\); \}\}/);
  assert.match(drawer, /name="finance-create-billing-fact" layout="vertical" disabled=\{command\.saving \|\| command\.conflict\}/);
  assert.match(source('./BillingFactsTab.tsx'), /onCreated=\{factId => \{ setCreating\(false\);/);
  assert.match(source('./useFinanceCommand.ts'), /if \(busy\.current\) return null;/);
});
