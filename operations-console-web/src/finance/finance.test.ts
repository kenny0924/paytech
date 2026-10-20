import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  accountBody, balanceCards, balanceErrorTargets, balanceFieldName, balanceRequestPath, bookBody, bookFilters, calendarBody,
  decodeAccountEntry, decodeAccountingPeriod, decodeAccountPosting, decodeChartAccount, decodeChartOfAccounts, decodeClientBalance, decodeCreated,
  decodeCurrency, decodeFinanceEntity, decodeFiscalCalendar, decodeJournalEntryDetail, decodeJournalLine, decodeLedgerAccount, decodeLedgerBook,
  decodeOpenedPeriod, decodeOwnerAccount, decodeOwnerAccountLookup, decodePartnerBalance, entityBody, entityLabel, entrySourceLabel, financeCommand, financeErrorTarget,
  financeErrorTargets, financePaths, formatAmount, journalLineSourceMeta, journalTotals, ledgerLevelSearch, ledgerQuerySearch, lineFilters, listOf,
  manageFinanceSetup, ownerAccountLookupPath, ownerAccountNotFoundMessage, ownerIdError, ownerTypes, pageOf, periodBody, periodRangeError, postingTotals, readLedgerQuery,
  readSetupTab, readSubledgerQuery, subledgerEntityIdError, subledgerErrorTargets, subledgerFilters, subledgerLevelSearch, subledgerPostingLink,
  subledgerQuerySearch, sumDecimals, timezoneOptions,
  accountFilters, balanceTotals, buildAccountTree, chartAccountBody, chartAccountLevel, chartAccountNoError, chartAccountUpdateBody, chartChangeFields,
  decodeAccountBalanceList, decodeChartAccountChange, decodeMaintainedChartAccount, decodeTrialBalanceDetail, decodeTrialBalanceRun, defaultNormalSide,
  expandableKeys, filterAccountTree, leafDrilldownSearch, ledgerTabSearch, readLedgerTab, readTrialQuery, trialSearch,
} from './finance.ts';

const time = '2026-10-18T05:12:00Z';
const entity = { financeEntityId: '7312345678901234567', entityCode: 'PLT-AE', legalPartyId: '880000000000012001', displayName: '平台阿联酋财务主体', status: 'ACTIVE', createTime: time };
const calendar = { fiscalCalendarId: '7312000000000000001', entityType: 'OPERATING_ENTITY', entityId: 'AE001', calendarCode: 'AE-STD', timezone: 'Asia/Dubai', yearStartMonth: 1, createTime: time };
const period = { accountingPeriodId: '7312000000000000011', fiscalCalendarId: calendar.fiscalCalendarId, fiscalYear: 2026, periodNo: 11,
  startDate: '2026-11-01', endDate: '2026-11-30', status: 'FUTURE', reopenCount: 0 };
const book = { ledgerBookId: '7318800000000000101', entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', chartCode: 'STANDARD', bookStatus: 'OPEN' };
const account = { accountId: '7318800000000000201', accountNo: 'AE001-USD-22010101-0001', accountCode: 'CLIENT_PAYABLE', accountName: 'Client 应付款',
  ownerType: 'OPERATING_ENTITY', ownerId: 'AE001', balanceBucket: 'NONE',
  normalSide: 'CREDIT', accountStatus: 'ACTIVE', debitTotal: '0.00', creditTotal: '1970.00', netBalance: '1970.00', lastJournalSequence: '1024' };
const postingId = '7318802201114002018';
const line = { journalEntryId: '7318802201114001024', journalSequence: '1024', postingDate: '2026-10-18', entryType: 'STANDARD', lineNo: 2, entrySide: 'CREDIT',
  amount: '120.00', balanceAfter: '1970.00', instructionType: 'PAYMENT_SUCCEEDED', originType: 'ACCOUNT_POSTING', sourceSystem: 'account',
  sourceEventKey: postingId, adjustmentNo: null, accountPostingId: postingId, accountPostingSequence: '18' };
const entryLine = (lineNo: number, entrySide: string, amount: string, accountEntryId: string | null = null) => ({ lineNo, accountId: '7318800000000000201',
  accountNo: 'AE001-USD-22010101-0001', accountCode: 'CLIENT_PAYABLE', accountName: 'Client 应付款', ownerType: 'OPERATING_ENTITY', ownerId: 'AE001', balanceBucket: 'NONE', entrySide, amount, accountEntryId });
const entry = { journalEntryId: line.journalEntryId, ledgerBookId: book.ledgerBookId, accountingPeriodId: '7312000000000000010', journalSequence: '1024',
  postingDate: '2026-10-18', entryType: 'STANDARD', reversalOfEntryId: null, instructionId: '7318802201114000987', instructionType: 'PAYMENT_SUCCEEDED',
  originType: 'ACCOUNT_POSTING', sourceSystem: 'account', sourceEventKey: postingId, manualAdjustmentId: null, adjustmentNo: null,
  accountPostingId: postingId, accountPostingSequence: '18',
  lines: [entryLine(1, 'DEBIT', '125.00'), entryLine(2, 'CREDIT', '120.00', '7400000000003001'), entryLine(3, 'CREDIT', '5.00')] };
const usd = (amount: string) => ({ currency: 'USD', amount });

test('页面注册：财务七页按序登记，动作与 Platform 能力清单一致（账本查询增加发起试算，三个新页各有制单 / 复核）', () => {
  const finance = pageCapabilities.filter(page => page.componentKey.startsWith('OPERATIONS_FINANCE_'));
  assert.deepEqual(finance.map(page => [page.componentKey, page.frontendRoute]), [
    ['OPERATIONS_FINANCE_SETUP', '/finance/setup'], ['OPERATIONS_FINANCE_LEDGER', '/finance/ledger'], ['OPERATIONS_FINANCE_BALANCES', '/finance/balances'],
    ['OPERATIONS_FINANCE_SUBLEDGER', '/finance/subledger'], ['OPERATIONS_FINANCE_ADJUSTMENTS', '/finance/adjustments'],
    ['OPERATIONS_FINANCE_PERIOD_CLOSE', '/finance/period-close'], ['OPERATIONS_FINANCE_ACCOUNTING_RULES', '/finance/accounting-rules']]);
  assert.deepEqual(finance.map(page => page.actions.map(action => action.resourceCode)), [[manageFinanceSetup], ['OPERATIONS_FINANCE_LEDGER_MANAGE'], [], [],
    ['OPERATIONS_FINANCE_ADJUSTMENTS_MANAGE', 'OPERATIONS_FINANCE_ADJUSTMENTS_APPROVE'],
    ['OPERATIONS_FINANCE_PERIOD_CLOSE_MANAGE', 'OPERATIONS_FINANCE_PERIOD_CLOSE_APPROVE'],
    ['OPERATIONS_FINANCE_ACCOUNTING_RULES_MANAGE', 'OPERATIONS_FINANCE_ACCOUNTING_RULES_APPROVE']]);
});

test('基础资料解码：主键、状态、日期与枚举严格校验，未知值失败关闭', () => {
  assert.equal(decodeFinanceEntity(entity).entityCode, 'PLT-AE');
  assert.throws(() => decodeFinanceEntity({ ...entity, status: 'DISABLED' }), ApiError);
  assert.throws(() => decodeFinanceEntity({ ...entity, legalPartyId: 880000000000012001 }), ApiError);
  assert.equal(decodeFiscalCalendar(calendar).timezone, 'Asia/Dubai');
  assert.throws(() => decodeFiscalCalendar({ ...calendar, yearStartMonth: 13 }), ApiError);
  assert.throws(() => decodeFiscalCalendar({ ...calendar, entityType: 'CLIENT' }), ApiError);
  assert.equal(decodeAccountingPeriod(period, calendar.fiscalCalendarId).status, 'FUTURE');
  assert.throws(() => decodeAccountingPeriod(period, '7312000000000000002'), ApiError, '期间必须属于所查日历');
  assert.throws(() => decodeAccountingPeriod({ ...period, startDate: '2026-12-01' }), ApiError, '起止倒置');
  assert.throws(() => decodeAccountingPeriod({ ...period, endDate: '2026-11-31' }), ApiError, '非法日期');
  assert.throws(() => decodeAccountingPeriod({ ...period, status: 'LOCKED' }), ApiError);
  assert.equal(listOf(decodeChartOfAccounts)({ items: [{ chartId: '1', chartCode: 'STANDARD', chartName: '标准科目表', status: 'ACTIVE' }] }).length, 1);
  assert.throws(() => listOf(decodeChartOfAccounts)([]), ApiError);
  const chartAccount = { chartAccountId: '11', chartId: '1', accountNo: '22010101', accountCode: 'CLIENT_PAYABLE', accountName: 'Client 应付',
    accountClass: 'LIABILITY', normalSide: 'CREDIT', postingAllowed: true, parentChartAccountId: '10', level: 3, accountStatus: 'ACTIVE' };
  assert.equal(decodeChartAccount(chartAccount).postingAllowed, true);
  assert.throws(() => decodeChartAccount({ ...chartAccount, postingAllowed: 'true' }), ApiError);
  assert.throws(() => decodeChartAccount({ ...chartAccount, level: 1 }), ApiError, '级次必须与编号长度一致');
  assert.throws(() => decodeChartAccount({ ...chartAccount, parentChartAccountId: null }), ApiError, '下级科目必须有上级');
  assert.equal(decodeChartAccount({ ...chartAccount, accountNo: '2201', level: 1, parentChartAccountId: undefined }).parentChartAccountId, null);
  assert.throws(() => decodeChartAccount({ ...chartAccount, accountNo: '0201', level: 1, parentChartAccountId: null }), ApiError);
  assert.throws(() => decodeChartAccount({ ...chartAccount, accountStatus: 'CLOSED' }), ApiError);
  assert.equal(decodeMaintainedChartAccount('11')(chartAccount).chartAccountId, '11');
  assert.throws(() => decodeMaintainedChartAccount('12')(chartAccount), ApiError, '维护回执必须是所操作的科目');
  assert.equal(decodeCurrency({ currency: 'USD', minorUnit: 2, roundingMode: 'HALF_UP', status: 'ACTIVE' }).minorUnit, 2);
  assert.throws(() => decodeCurrency({ currency: 'usd', minorUnit: 2, roundingMode: 'HALF_UP', status: 'ACTIVE' }), ApiError);
});

test('账簿、账户、流水与分录解码；可空字段缺失视为 null', () => {
  assert.equal(pageOf(decodeLedgerBook)({ items: [book], page: 1, size: 10, total: 1 }).items[0].chartCode, 'STANDARD');
  assert.throws(() => decodeLedgerBook({ ...book, bookStatus: 'ACTIVE' }), ApiError);
  assert.equal(decodeLedgerAccount(account).netBalance, '1970.00');
  assert.throws(() => decodeLedgerAccount({ ...account, accountNo: undefined }), ApiError, '账户号必填');
  assert.throws(() => decodeLedgerAccount({ ...account, accountName: '' }), ApiError);
  assert.deepEqual(ownerTypes, ['OPERATING_ENTITY', 'FINANCE_ENTITY', 'CHANNEL_ACCOUNT', 'CASH_ACCOUNT', 'TAX_AUTHORITY'], 'LedgerOwnerType 五值');
  assert.throws(() => decodeLedgerAccount({ ...account, ownerType: 'CLIENT', ownerId: '880000000000079001' }), ApiError, 'Client 不再是总账核算对象');
  assert.equal(decodeLedgerAccount({ ...account, lastJournalSequence: undefined }).lastJournalSequence, null);
  assert.throws(() => decodeLedgerAccount({ ...account, netBalance: 1250 }), ApiError);
  assert.throws(() => decodeLedgerAccount({ ...account, balanceBucket: 'FROZEN' }), ApiError);
  assert.equal(decodeLedgerAccount({ ...account, netBalance: '-20.00' }).netBalance, '-20.00');
  assert.deepEqual([decodeJournalLine(line).accountPostingId, decodeJournalLine(line).accountPostingSequence], [postingId, '18']);
  assert.throws(() => decodeJournalLine({ ...line, accountPostingId: null }), ApiError, 'ACCOUNT_POSTING 必须带批次');
  const auto = { ...line, originType: 'AUTO', sourceSystem: 'payment', sourceEventKey: 'pay-evt-1', accountPostingId: undefined, accountPostingSequence: undefined };
  assert.equal(decodeJournalLine(auto).accountPostingId, null);
  assert.throws(() => decodeJournalLine({ ...auto, accountPostingId: postingId }), ApiError, '非 ACCOUNT_POSTING 不得带批次');
  const manual = { ...auto, originType: 'MANUAL', sourceSystem: undefined, sourceEventKey: undefined, adjustmentNo: 'MA-20261017-0007' };
  assert.deepEqual([decodeJournalLine(manual).sourceSystem, decodeJournalLine(manual).adjustmentNo], [null, 'MA-20261017-0007']);
  assert.deepEqual([line, auto, manual].map(item => journalLineSourceMeta(decodeJournalLine(item))), ['批次 …2018 · 序号 18', 'pay-evt-1', 'MA-20261017-0007']);
  assert.throws(() => decodeJournalLine({ ...line, postingDate: '2026/10/18' }), ApiError);
  const detail = decodeJournalEntryDetail(entry, book.ledgerBookId, entry.journalEntryId);
  assert.deepEqual(detail.lines.map(item => item.accountEntryId), [null, '7400000000003001', null]);
  assert.deepEqual([detail.lines[0].accountNo, detail.lines[0].accountName], ['AE001-USD-22010101-0001', 'Client 应付款']);
  assert.throws(() => decodeJournalEntryDetail({ ...entry, lines: [{ ...entryLine(1, 'DEBIT', '1'), accountNo: null }] }, book.ledgerBookId, entry.journalEntryId), ApiError);
  assert.equal(subledgerPostingLink(detail.accountPostingId ?? '', '7400000000003001'), `/finance/subledger?posting=${postingId}&entry=7400000000003001`);
  assert.throws(() => decodeJournalEntryDetail({ ...entry, accountPostingSequence: null }, book.ledgerBookId, entry.journalEntryId), ApiError);
  assert.throws(() => decodeJournalEntryDetail(entry, '9', entry.journalEntryId), ApiError, '账簿必须与请求一致');
  assert.throws(() => decodeJournalEntryDetail({ ...entry, lines: [] }, book.ledgerBookId, entry.journalEntryId), ApiError);
  assert.throws(() => decodeJournalEntryDetail({ ...entry, lines: [{ ...entryLine(1, 'DEBIT', '1'), entrySide: 'D' }] }, book.ledgerBookId, entry.journalEntryId), ApiError);
});

test('余额解码：Client 五项、Partner 四项且不得含保证金；币种与请求一致', () => {
  const client = { clientId: '880000000000079001', entityNo: 'AE001', currency: 'USD', pendingAmount: usd('420.00'), availableAmount: usd('1250.00'),
    lockedAmount: usd('300.00'), securityDepositAmount: usd('500.00'), recoveryReceivableAmount: usd('0.00') };
  assert.equal(decodeClientBalance(client, client.clientId, 'AE001', 'USD').securityDepositAmount.amount, '500.00');
  assert.throws(() => decodeClientBalance(client, client.clientId, 'AE002', 'USD'), ApiError);
  assert.throws(() => decodeClientBalance({ ...client, lockedAmount: { currency: 'AED', amount: '1' } }, client.clientId, 'AE001', 'USD'), ApiError);
  const { securityDepositAmount: _deposit, clientId: _client, ...rest } = client;
  const partner = { ...rest, partnerId: '880000000000031002' };
  assert.equal(decodePartnerBalance(partner, partner.partnerId, 'AE001', 'USD').recoveryReceivableAmount.amount, '0.00');
  assert.throws(() => decodePartnerBalance({ ...partner, securityDepositAmount: usd('1') }, partner.partnerId, 'AE001', 'USD'), ApiError);
  assert.deepEqual(balanceCards(decodeClientBalance(client, client.clientId, 'AE001', 'USD')).map(card => card.key),
    ['pending', 'available', 'locked', 'deposit', 'recovery']);
  assert.deepEqual(balanceCards(decodePartnerBalance(partner, partner.partnerId, 'AE001', 'USD')).map(card => card.code ?? card.key),
    ['pending', 'available', 'locked', 'PARTNER_RECOVERY_RECEIVABLE']);
  assert.equal(balanceRequestPath({ ownerType: 'CLIENT', ownerId: ' 880000000000079001 ', entityNo: 'AE001', currency: 'USD' }),
    '/finance/balances/v1/clients/880000000000079001?entityNo=AE001&currency=USD');
  assert.equal(balanceRequestPath({ ownerType: 'PARTNER', ownerId: '880000000000031002', entityNo: 'SG002', currency: 'USD' }),
    '/finance/balances/v1/partners/880000000000031002?entityNo=SG002&currency=USD');
  assert.deepEqual(['queryClientBalance.clientId', 'entityNo', 'size'].map(balanceFieldName), ['ownerId', 'entityNo', null]);
});

test('创建与开放结果解码', () => {
  assert.equal(decodeCreated('financeEntityId')({ financeEntityId: '7312345678901234567' }).financeEntityId, '7312345678901234567');
  assert.throws(() => decodeCreated('ledgerBookId')({ financeEntityId: '1' }), ApiError);
  assert.equal(decodeOpenedPeriod({ accountingPeriodId: '7', status: 'OPEN' }, '7').status, 'OPEN');
  assert.throws(() => decodeOpenedPeriod({ accountingPeriodId: '7', status: 'FUTURE' }, '7'), ApiError);
});

test('请求体：去空白、总账账户余额桶一律 NONE、期间原样提交', () => {
  assert.deepEqual(entityBody({ entityCode: ' PLT-AE ', legalPartyId: '880000000000012001 ', displayName: ' 平台 ' }),
    { entityCode: 'PLT-AE', legalPartyId: '880000000000012001', displayName: '平台' });
  assert.deepEqual(calendarBody({ entityType: 'OPERATING_ENTITY', entityId: ' AE001', calendarCode: 'AE-STD ', timezone: 'Asia/Dubai', yearStartMonth: 1 }),
    { entityType: 'OPERATING_ENTITY', entityId: 'AE001', calendarCode: 'AE-STD', timezone: 'Asia/Dubai', yearStartMonth: 1 });
  assert.deepEqual(periodBody({ fiscalYear: 2026, periodNo: 11, startDate: '2026-11-01', endDate: '2026-11-30' }),
    { fiscalYear: 2026, periodNo: 11, startDate: '2026-11-01', endDate: '2026-11-30' });
  assert.deepEqual(bookBody({ entityType: 'FINANCE_ENTITY', entityId: '7312345678901234567', currency: 'USD', chartCode: 'STANDARD' }),
    { entityType: 'FINANCE_ENTITY', entityId: '7312345678901234567', currency: 'USD', chartCode: 'STANDARD' });
  assert.deepEqual(accountBody({ accountCode: 'CHANNEL_UNSETTLED_RECEIVABLE', ownerType: 'CHANNEL_ACCOUNT', ownerId: ' 9001 ' }),
    { accountCode: 'CHANNEL_UNSETTLED_RECEIVABLE', ownerType: 'CHANNEL_ACCOUNT', ownerId: '9001', balanceBucket: 'NONE' });
  assert.equal(accountBody({ accountCode: 'CLIENT_PAYABLE', ownerType: 'OPERATING_ENTITY', ownerId: 'AE001' }).balanceBucket, 'NONE', '控制科目也固定 NONE');
});

test('表单校验：核算对象标识按类型、期间起止、时区下拉', () => {
  assert.equal(ownerIdError('OPERATING_ENTITY', 'AE001'), null);
  assert.match(ownerIdError('OPERATING_ENTITY', 'ae001') ?? '', /AE001/);
  assert.equal(ownerIdError('TAX_AUTHORITY', '7001'), null);
  assert.equal(ownerIdError('CHANNEL_ACCOUNT', '900A'), '必须是正十进制整数字符串');
  assert.equal(ownerIdError('CHANNEL_ACCOUNT', '0'), '必须是正十进制整数字符串');
  assert.equal(ownerIdError('CASH_ACCOUNT', ' '), '请填写核算对象标识');
  assert.equal(periodRangeError('2026-11-01', '2026-11-30'), null);
  assert.equal(periodRangeError('2026-11-30', '2026-11-01'), '开始日期不能晚于结束日期');
  assert.deepEqual(timezoneOptions(() => ['Asia/Dubai']), ['UTC', 'Asia/Dubai']);
  assert.deepEqual(timezoneOptions(() => { throw new Error('unsupported'); }), ['UTC']);
  assert.ok(timezoneOptions().includes('Asia/Dubai'));
});

test('错误码映射：ledger 23 个 Code 都有落点，字段不在表单内时退回表单顶部；余额页 OWNER_ACCOUNT_NOT_FOUND 落在 Owner ID', () => {
  assert.equal(Object.keys(financeErrorTargets).length, 23, 'ledger LedgerSetupErrorCode');
  const chartForm = ['parentAccountNo', 'accountNo', 'accountCode', 'accountName', 'accountClass', 'normalSide', 'postingAllowed'];
  assert.deepEqual(['CHART_ACCOUNT_NO_INVALID', 'CHART_ACCOUNT_NO_DUPLICATE', 'CHART_ACCOUNT_CODE_DUPLICATE', 'CHART_PARENT_INVALID',
    'CHART_ACCOUNT_NOT_FOUND', 'CHART_STRUCTURE_FROZEN', 'CHART_ACCOUNT_IN_USE'].map(code => financeErrorTarget(new ApiError(400, code, 'r'), chartForm)?.field),
    ['accountNo', 'accountNo', 'accountCode', 'parentAccountNo', null, null, null], '科目维护按 technique 2.3 落点');
  assert.equal(financeErrorTarget(new ApiError(400, 'CHART_ACCOUNT_INACTIVE', 'r'), ['accountCode', 'ownerType', 'ownerId', 'balanceBucket'])?.field,
    'accountCode', '停用科目落在建账户表单的科目');
  assert.equal(financeErrorTarget(new ApiError(400, 'CHART_ACCOUNT_IN_USE', 'r'))?.message,
    '科目仍被规则、结转规则、账户类型目录或启用中的下级引用，不能停用（CHART_ACCOUNT_IN_USE）');
  assert.deepEqual(Object.keys(balanceErrorTargets), ['OWNER_ACCOUNT_NOT_FOUND'], 'account BalanceQueryErrorCode');
  assert.deepEqual(Object.keys(subledgerErrorTargets).sort(), ['ACCOUNT_NOT_FOUND', 'POSTING_NOT_FOUND'], '明细账查询会收到的 account Code');
  const error = (code: string, status = 400) => new ApiError(status, code, 'req-1');
  assert.deepEqual(financeErrorTarget(error('ACCOUNTING_PERIOD_OVERLAP'), ['startDate', 'endDate']),
    { field: 'startDate', message: '期间日期与同日历其他期间重叠（ACCOUNTING_PERIOD_OVERLAP）' });
  assert.equal(financeErrorTarget(error('ACCOUNTING_PERIOD_OVERLAP'), [])?.field, null);
  assert.equal(financeErrorTarget(error('FINANCE_ENTITY_ALREADY_EXISTS'), ['entityCode'])?.field, null);
  assert.equal(financeErrorTarget(error('ACCOUNT_ALREADY_EXISTS'), ['balanceBucket'])?.field, 'balanceBucket');
  assert.equal(financeErrorTarget(error('CHART_ACCOUNT_NOT_POSTABLE'), ['accountCode'])?.field, 'accountCode');
  assert.equal(financeErrorTarget(error('ACCOUNT_IDENTITY_INVALID'), ['ownerType'])?.field, 'ownerType');
  assert.equal(financeErrorTarget(error('FINANCE_ENTITY_UNAVAILABLE'), ['entityId'])?.field, 'entityId');
  assert.equal(financeErrorTarget(error('LEDGER_BOOK_NOT_FOUND'))?.message, '账簿不存在（LEDGER_BOOK_NOT_FOUND）');
  assert.equal(financeErrorTarget(error('LEDGER_BOOK_NOT_FOUND'), ['entityNo'], balanceErrorTargets), null, '余额页不再有 LEDGER_BOOK_NOT_FOUND');
  assert.equal(financeErrorTarget(error('OWNER_ACCOUNT_NOT_FOUND'), ['ownerId'], balanceErrorTargets)?.field, 'ownerId');
  assert.equal(ownerAccountNotFoundMessage({ ownerType: 'PARTNER', entityNo: ' SG002', currency: 'USD' }),
    '该 Local Partner 在 SG002 / USD 下没有账户（OWNER_ACCOUNT_NOT_FOUND）');
  assert.equal(financeErrorTarget(error('POSTING_NOT_FOUND'), [], subledgerErrorTargets)?.message, '过账批次不存在（POSTING_NOT_FOUND）');
  assert.equal(financeErrorTarget(error('BUSINESS_CHECK_FAILED')), null, '公共错误交给通用通知');
  assert.equal(financeErrorTarget(error('ACCOUNT_ALREADY_EXISTS', 409)), null, '只映射 400');
  assert.equal(financeErrorTarget(new Error('x')), null);
});

test('金额格式：按小数位补零、千分位、负数标识，不舍入非零尾数', () => {
  assert.deepEqual(formatAmount('1250', 2), { text: '1,250.00', negative: false });
  assert.deepEqual(formatAmount('1580.5', 2), { text: '1,580.50', negative: false });
  assert.deepEqual(formatAmount('-20.00', 2), { text: '-20.00', negative: true });
  assert.deepEqual(formatAmount('-0.00', 2), { text: '0.00', negative: false });
  assert.deepEqual(formatAmount('120.0000', 2), { text: '120.00', negative: false });
  assert.deepEqual(formatAmount('120.0050', 2), { text: '120.005', negative: false });
  assert.deepEqual(formatAmount('1234567', 0), { text: '1,234,567', negative: false });
  assert.deepEqual(formatAmount('1234.5'), { text: '1,234.5', negative: false });
  assert.deepEqual(formatAmount('abc', 2), { text: 'abc', negative: false });
});

test('分录合计：精确十进制求和与借贷平衡判断', () => {
  assert.equal(sumDecimals(['0.1', '0.2']), '0.3');
  assert.equal(sumDecimals(['1250.00', '-1300.5']), '-50.50');
  assert.equal(sumDecimals([]), '0');
  assert.deepEqual(journalTotals(entry.lines as { entrySide: 'DEBIT' | 'CREDIT'; amount: string }[]), { debit: '125.00', credit: '125.00', balanced: true });
  assert.equal(journalTotals([{ entrySide: 'DEBIT', amount: '10.00' }, { entrySide: 'CREDIT', amount: '9.99' }]).balanced, false);
});

test('主体展示与查询条件', () => {
  const entities = new Map([[entity.financeEntityId, decodeFinanceEntity(entity)]]);
  assert.equal(entityLabel('OPERATING_ENTITY', 'AE001', entities), 'AE001');
  assert.equal(entityLabel('FINANCE_ENTITY', entity.financeEntityId, entities), 'PLT-AE（7312…4567）');
  assert.equal(entityLabel('FINANCE_ENTITY', '99', entities), '99');
  assert.deepEqual(bookFilters({ entityType: 'OPERATING_ENTITY', entityId: ' AE001 ', currency: undefined }), { entityType: 'OPERATING_ENTITY', entityId: 'AE001' });
  assert.deepEqual(lineFilters({ postingDateFrom: '2026-10-01', postingDateTo: '2026-10-18' }), { postingDateFrom: '2026-10-01', postingDateTo: '2026-10-18' });
  assert.deepEqual(lineFilters({}), {});
  assert.throws(() => lineFilters({ postingDateFrom: '2026-10-18', postingDateTo: '2026-10-01' }), /开始日期不能晚于结束日期/);
  assert.throws(() => lineFilters({ postingDateFrom: '2026/10/18' }), /yyyy-MM-dd/);
});

test('URL 状态：标签页、逐级下钻、非法值忽略、回退保留本级筛选', () => {
  assert.equal(readSetupTab(new URLSearchParams('tab=charts')), 'charts');
  assert.equal(readSetupTab(new URLSearchParams('tab=unknown')), 'entities');
  const books = readLedgerQuery(new URLSearchParams('entityType=OPERATING_ENTITY&currency=USD&size=7&page=0'));
  assert.equal(books.level, 'books');
  assert.deepEqual(books.bookFilters, { entityType: 'OPERATING_ENTITY', currency: 'USD' });
  assert.deepEqual(books.bookPaging, { page: 1, size: 10 }, '分页大小只允许 10/20/50/100/200');
  assert.equal(readLedgerQuery(new URLSearchParams('entityType=CLIENT')).bookFilters.entityType, undefined);
  assert.equal(readLedgerQuery(new URLSearchParams('account=5')).level, 'books', '没有账簿时忽略账户');
  assert.equal(readLedgerQuery(new URLSearchParams('book=abc')).book, null);
  const lines = readLedgerQuery(new URLSearchParams('book=1&account=2&entry=3&postingDateFrom=2026-10-01&postingDateTo=bad&linePage=2&lineSize=50'));
  assert.deepEqual([lines.level, lines.book, lines.account, lines.entry], ['lines', '1', '2', '3']);
  assert.deepEqual(lines.lineFilters, { postingDateFrom: '2026-10-01' });
  assert.deepEqual(lines.linePaging, { page: 2, size: 50 });
  assert.deepEqual(readLedgerQuery(new URLSearchParams('book=1')).linePaging, { page: 1, size: 20 });

  const search = '?currency=USD&page=2&book=1&accountCode=CLIENT_PAYABLE&accountPage=3&account=2&postingDateFrom=2026-10-01&entry=3';
  assert.equal(ledgerLevelSearch(search, 'books'), '?currency=USD&page=2');
  assert.equal(ledgerLevelSearch(search, 'accounts'), '?currency=USD&page=2&book=1&accountCode=CLIENT_PAYABLE&accountPage=3');
  assert.equal(ledgerLevelSearch('?currency=USD&book=1&accountCode=X', 'accounts', { book: '9' }), '?currency=USD&book=9', '换账簿清空账户条件');
  assert.equal(ledgerLevelSearch('?book=1&accountCode=X', 'lines', { account: '2' }), '?book=1&accountCode=X&account=2');
  assert.equal(ledgerQuerySearch('?book=1&accountCode=X&ownerType=CLIENT', 'accounts', { ownerType: 'LOCAL_PARTNER' }, { page: 1, size: 20 }),
    '?book=1&ownerType=LOCAL_PARTNER&accountPage=1&accountSize=20');
});

test('写命令：同一目标与正文复用幂等键；期间开放不带请求体', () => {
  const first = financeCommand(financePaths.financeEntities, { entityCode: 'PLT-AE' });
  assert.equal(financeCommand(first.path, { entityCode: 'PLT-AE' }, first).key, first.key);
  assert.notEqual(financeCommand(first.path, { entityCode: 'PLT-SG' }, first).key, first.key);
  const open = financeCommand(financePaths.openPeriod('7'), undefined);
  assert.deepEqual([open.path, open.method, open.body], ['/finance/accounting-periods/v1/7/open', 'POST', '']);
  assert.equal(financeCommand(open.path, undefined, open).key, open.key);
  assert.equal(financePaths.journalLines('1', '2'), '/finance/ledger-books/v1/1/accounts/2/journal-lines');
  assert.equal(financePaths.chartAccounts('STANDARD'), '/finance/chart-of-accounts/v1/STANDARD/accounts');
});

const source = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('页面接线：维护按钮受 BUTTON 资源控制，只读页与只读标签页没有新建入口，空状态与固定说明齐全', () => {
  const setup = source('../pages/FinanceSetup.tsx');
  assert.match(setup, /resourceType === 'BUTTON' && r\.resourceCode === manageFinanceSetup/);
  assert.match(setup, /canManage && createLabel \?/);
  assert.match(source('./SetupCalendarsTab.tsx'), /row\.status === 'FUTURE'/);
  assert.match(source('./SetupCalendarsTab.tsx'), /\.\.\.\(canManage \?/);
  assert.match(source('./SetupBooksTab.tsx'), /extra=\{canManage &&/);
  for (const readOnly of ['./LedgerDrilldown.tsx', '../pages/FinanceLedger.tsx', '../pages/FinanceBalances.tsx', './TrialBalanceTab.tsx',
    '../pages/FinanceSubledger.tsx', './SubledgerAccounts.tsx', './SubledgerEntries.tsx', './SubledgerPostingDrawer.tsx']) {
    assert.doesNotMatch(source(readOnly), /FinanceModals|ChartAccountModals|>新建/, `${readOnly} 不得出现新建入口`);
  }
  const trial = source('./TrialBalanceTab.tsx');
  assert.match(trial, /\{canRun && ready && <Form\.Item><Button type="primary"/, '发起试算只对有 OPERATIONS_FINANCE_LEDGER_MANAGE 的账号显示');
  assert.match(trial, /workflowPaths\.runTrialBalance\(book\.ledgerBookId, period\.accountingPeriodId\), undefined,/, '发起试算不带请求体');
  assert.match(source('../pages/FinanceLedger.tsx'), /canRun=\{hasAction\(resources, financeActions\.ledgerManage\)\}/);
  const charts = source('./SetupChartsTab.tsx');
  assert.match(setup, /<SetupChartsTab canManage=\{canManage\}/);
  assert.match(charts, /canManage && chartCode && <Button type="primary"/, '新增科目按钮受维护权限控制');
  assert.match(charts, /\.\.\.\(canManage \? \[\{ key: 'edit'/, '编辑、新增下级、停用 / 启用受维护权限控制');
  assert.match(charts, /key: 'changes', label: '变更记录'/, '变更记录对只读账号也可见');
  assert.match(source('./ChartAccountModals.tsx'), /停用后不能新建账户、不能被新规则引用，已有账户不受影响/);
  assert.match(source('./LedgerDrilldown.tsx'), /行后余额按全量历史累计，筛选不改变起点/);
  const balances = source('../pages/FinanceBalances.tsx');
  assert.match(balances, /Available 不等于可出款资格/);
  assert.match(balances, /该 Owner 在此经营主体与币种下没有账户，请确认 Owner ID、entityNo 与币种后重新查询/);
  assert.match(balances, /选择 Owner 类型并填写 Owner ID、entityNo 与币种后查询/);
  assert.match(source('./SetupCalendarsTab.tsx'), /选择一个日历查看会计期间/);
  assert.match(source('./SetupBooksTab.tsx'), /选择一个账簿查看账户/);
  assert.match(source('./SetupEntitiesTab.tsx'), /还没有财务主体/);
});

const ownerAccount = { accountId: '7200000000000001', accountNo: 'AE001-USD-22010101-S000001', accountCode: 'CLIENT_PAYABLE', ownerType: 'CLIENT', ownerId: '1001', balanceBucket: 'PENDING',
  normalSide: 'CREDIT', accountStatus: 'ACTIVE', debitTotal: usd('500.00'), creditTotal: usd('920.00'), netBalance: usd('420.00'), balanceVersion: '5' };
const factEntry = { entryId: '7400000000000009', postingId: '7300000000000009', postingSequence: '9', sourceType: 'ECONOMIC_FACT', factId: '8790',
  factVersion: '2', factType: 'PAYMENT', movementSourceType: null, lineNo: '1', factLineNo: '1', entrySide: 'DEBIT', amount: usd('200.00'), reversesEntryId: '7400000000000003',
  postedTime: '2026-10-16T09:12:00.123456Z' };
const movementEntry = { ...factEntry, entryId: '7400000000000015', sourceType: 'BALANCE_MOVEMENT', factId: undefined, factVersion: null, factType: null,
  movementSourceType: 'FACT_MATURITY', factLineNo: null, reversesEntryId: null };
const postingEntry = (entryId: string, lineNo: string, accountCode: string, ownerType: string, ownerId: string, amount: string) => ({ entryId, lineNo,
  accountId: '7200000000000001', accountNo: 'AE001-USD-22010101-S000001', accountCode, ownerType, ownerId, balanceBucket: 'PENDING', entrySide: 'CREDIT', amount: usd(amount), factLineNo: lineNo,
  reversesEntryId: null });
const posting = { postingId, entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', shardNo: '0', postingSequence: '18',
  sourceType: 'ECONOMIC_FACT', postedTime: '2026-10-18T10:21:07.123456Z', publishedTime: '2026-10-18T10:21:08.500Z', factId: '9001', factVersion: '1', factType: 'PAYMENT', supersedesFactId: null,
  factApplyStatus: 'APPLIED', financeEntityId: '8001', movementId: null, movementSourceType: null, movementSourceRef: null,
  entries: [postingEntry('7400000000000018', '1', 'CLIENT_PAYABLE', 'CLIENT', '1001', '90.00'),
    postingEntry('7400000000000019', '2', 'PARTNER_PAYABLE', 'LOCAL_PARTNER', '2001', '8.00')] };

test('明细账解码：Owner 账户、流水与批次严格校验，来源字段与来源类型互斥，金额同币种', () => {
  assert.equal(financePaths.ownerAccounts, '/finance/owner-accounts/v1');
  assert.equal(financePaths.ownerAccountEntries('7'), '/finance/owner-accounts/v1/7/entries');
  assert.equal(financePaths.accountPosting('8'), '/finance/account-postings/v1/8');
  assert.equal(pageOf(decodeOwnerAccount('USD'))({ items: [ownerAccount], page: 1, size: 10, total: 1 }).items[0].netBalance.amount, '420.00');
  assert.throws(() => decodeOwnerAccount('AED')(ownerAccount), ApiError, '金额必须同查询币种');
  assert.throws(() => decodeOwnerAccount('USD')({ ...ownerAccount, ownerType: 'OPERATING_ENTITY' }), ApiError, '明细账没有平台类 Owner');
  assert.throws(() => decodeOwnerAccount('USD')({ ...ownerAccount, accountStatus: 'INACTIVE' }), ApiError);
  assert.throws(() => decodeOwnerAccount('USD')({ ...ownerAccount, accountNo: 'ae001-usd' }), ApiError, '账户号为大写字母、数字与连字符');
  assert.equal(decodeOwnerAccount('USD')({ ...ownerAccount, accountCode: 'BANK_CASH', ownerType: 'CASH_ACCOUNT', balanceBucket: 'AVAILABLE',
    netBalance: usd('-1.00') }).netBalance.amount, '-1.00');
  const fact = decodeAccountEntry('USD')(factEntry);
  assert.deepEqual([fact.reversesEntryId, fact.factType, entrySourceLabel(fact)], ['7400000000000003', 'PAYMENT', '事实 8790 · v2']);
  const movement = decodeAccountEntry()(movementEntry);
  assert.deepEqual([movement.factId, movement.factType, movement.factLineNo, entrySourceLabel(movement)], [null, null, null, 'FACT_MATURITY']);
  assert.equal(decodeAccountEntry()({ ...movementEntry, factType: undefined }).factType, null, '缺失视为 null');
  assert.throws(() => decodeAccountEntry()({ ...factEntry, factType: null }), ApiError, '事实流水必须带事实类型');
  assert.throws(() => decodeAccountEntry()({ ...movementEntry, factType: 'PAYMENT' }), ApiError, '桶移动流水不得带事实类型');
  assert.throws(() => decodeAccountEntry()({ ...factEntry, movementSourceType: 'PAYOUT_LOCK' }), ApiError, '事实批次不得带桶移动来源');
  assert.throws(() => decodeAccountEntry()({ ...movementEntry, factId: '1' }), ApiError, '桶移动批次不得带事实');
  assert.throws(() => decodeAccountEntry()({ ...factEntry, postedTime: 'yesterday' }), ApiError);
  const decoded = decodeAccountPosting(posting, postingId);
  assert.deepEqual([decoded.entries.length, decoded.supersedesFactId, decoded.publishedTime], [2, null, '2026-10-18T10:21:08.500Z']);
  assert.equal(decodeAccountPosting({ ...posting, publishedTime: undefined }, postingId).publishedTime, null, '尚未发布');
  assert.throws(() => decodeAccountPosting({ ...posting, publishedTime: 'soon' }, postingId), ApiError);
  assert.deepEqual(postingTotals(decoded.entries), { debit: '0', credit: '98.00', balanced: false });
  assert.throws(() => decodeAccountPosting(posting, '7318802201114009999'), ApiError, '批次主键必须与请求一致');
  assert.throws(() => decodeAccountPosting({ ...posting, entries: undefined }, postingId), ApiError);
  assert.throws(() => decodeAccountPosting({ ...posting, entries: [{ ...posting.entries[0], amount: { currency: 'AED', amount: '1' } }] }, postingId), ApiError);
  assert.equal(decodeAccountPosting({ ...posting, factApplyStatus: 'SKIPPED', entries: [] }, postingId).entries.length, 0);
  const moved = { ...posting, sourceType: 'BALANCE_MOVEMENT', factId: null, factVersion: null, factType: null, factApplyStatus: null, financeEntityId: null,
    movementId: '7500000000000001', movementSourceType: 'PAYOUT_LOCK', movementSourceRef: 'payout-1' };
  assert.equal(decodeAccountPosting(moved, postingId).movementSourceRef, 'payout-1');
});

test('明细账查询条件：主体三项必填且合法，可选筛选只保留合法值', () => {
  assert.equal(subledgerEntityIdError('OPERATING_ENTITY', 'AE001'), null);
  assert.match(subledgerEntityIdError('OPERATING_ENTITY', '8001') ?? '', /AE001/);
  assert.equal(subledgerEntityIdError('FINANCE_ENTITY', '8001'), null);
  assert.match(subledgerEntityIdError('FINANCE_ENTITY', 'AE001') ?? '', /财务主体主键/);
  assert.equal(subledgerEntityIdError('OPERATING_ENTITY', ''), '请填写主体标识');
  assert.deepEqual(subledgerFilters({ entityType: 'OPERATING_ENTITY', entityId: ' AE001 ', currency: 'USD', ownerType: 'CLIENT', ownerId: ' 1001',
    accountCode: undefined, balanceBucket: '' }), { entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', ownerType: 'CLIENT', ownerId: '1001' });
  assert.throws(() => subledgerFilters({ entityType: 'OPERATING_ENTITY', entityId: 'AE001' }), /主体类型、主体标识与币种/);
  assert.throws(() => subledgerFilters({ entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', ownerType: 'CHANNEL_ACCOUNT' }), /筛选/);
  assert.throws(() => subledgerFilters({ entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', ownerId: 'P-1' }), /筛选/);
  assert.deepEqual(subledgerFilters({ entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', accountNo: ' AE001-USD-22010101-S000001 ' }),
    { entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', accountNo: 'AE001-USD-22010101-S000001' }, '账户号精确筛选');
  assert.throws(() => subledgerFilters({ entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', accountNo: 'ae001 usd' }), /筛选/);
  assert.deepEqual(accountFilters({ accountNo: ' AE001-USD-22010101-0001 ', accountCode: '' }), { accountNo: 'AE001-USD-22010101-0001' }, '总账账户号精确筛选');
  assert.equal(readLedgerQuery(new URLSearchParams(`book=1&accountNo=${'A'.repeat(49)}`)).accountFilters.accountNo, undefined, '超长账户号忽略');
});

test('明细账 URL 状态：逐级下钻、非法值忽略、面包屑回退保留筛选、批次抽屉可独立打开', () => {
  assert.equal(readSubledgerQuery(new URLSearchParams('')).level, 'idle');
  assert.equal(readSubledgerQuery(new URLSearchParams('entityType=OPERATING_ENTITY&entityId=8001&currency=USD')).scope, null, 'OE 主体标识必须是 entityNo');
  const accounts = readSubledgerQuery(new URLSearchParams('entityType=FINANCE_ENTITY&entityId=8001&currency=USD&ownerType=TAX_AUTHORITY&balanceBucket=LOCKED&size=7'));
  assert.deepEqual([accounts.level, accounts.scope], ['accounts', { entityType: 'FINANCE_ENTITY', entityId: '8001', currency: 'USD' }]);
  assert.deepEqual(accounts.accountFilters, { balanceBucket: 'LOCKED' }, '明细账 Owner 类型不含 TAX_AUTHORITY');
  assert.deepEqual(accounts.accountPaging, { page: 1, size: 10 });
  assert.equal(readSubledgerQuery(new URLSearchParams('account=5')).level, 'idle', '没有主体时忽略账户');
  const entries = readSubledgerQuery(new URLSearchParams('entityType=OPERATING_ENTITY&entityId=AE001&currency=USD&account=5&entryPage=2&entrySize=50'));
  assert.deepEqual([entries.level, entries.account, entries.entryPaging], ['entries', '5', { page: 2, size: 50 }]);
  assert.deepEqual(readSubledgerQuery(new URLSearchParams('account=5&entityType=OPERATING_ENTITY&entityId=AE001&currency=USD')).entryPaging, { page: 1, size: 20 });
  const drawer = readSubledgerQuery(new URLSearchParams(`posting=${postingId}&entry=7400000000003001`));
  assert.deepEqual([drawer.level, drawer.posting, drawer.entry], ['idle', postingId, '7400000000003001'], '总账跳转只带批次与流水');
  assert.equal(readSubledgerQuery(new URLSearchParams('entry=1')).entry, null, '没有批次时忽略高亮流水');

  const search = '?entityType=OPERATING_ENTITY&entityId=AE001&currency=USD&ownerType=CLIENT&page=2&account=5&entryPage=3&posting=9&entry=8';
  assert.equal(subledgerLevelSearch(search, 'accounts'), '?entityType=OPERATING_ENTITY&entityId=AE001&currency=USD&ownerType=CLIENT&page=2');
  assert.equal(subledgerLevelSearch(search, 'idle'), '');
  assert.equal(subledgerLevelSearch('?entityType=OPERATING_ENTITY&entityId=AE001&currency=USD&account=5&entryPage=3', 'entries', { account: '6' }),
    '?entityType=OPERATING_ENTITY&entityId=AE001&currency=USD&account=6', '换账户重置流水分页');
  assert.equal(subledgerLevelSearch(`?posting=${postingId}&entry=1`, 'entries', { account: '6',
    scope: { entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD' } }), '?entityType=OPERATING_ENTITY&entityId=AE001&currency=USD&account=6',
    '从批次抽屉进入账户流水时带上批次的主体并关闭抽屉');
  assert.equal(subledgerQuerySearch(search, 'accounts', { entityType: 'OPERATING_ENTITY', entityId: 'SG002', currency: 'USD', balanceBucket: 'NONE' },
    { page: 1, size: 20 }), '?entityType=OPERATING_ENTITY&entityId=SG002&currency=USD&page=1&posting=9&entry=8&balanceBucket=NONE&size=20');
  assert.equal(subledgerQuerySearch('?account=5', 'entries', {}, { page: 2, size: 50 }), '?account=5&entryPage=2&entrySize=50');
});

test('明细账页面接线：必填主体、空状态、只读、总账分录行链接到批次', () => {
  const page = source('../pages/FinanceSubledger.tsx');
  assert.match(page, /选择主体类型并填写主体标识与币种后查询 Owner 账户/);
  assert.match(source('./SubledgerAccounts.tsx'), /该主体与币种下还没有 Owner 账户/);
  assert.match(source('./SubledgerEntries.tsx'), /该账户还没有流水/);
  assert.match(source('./SubledgerEntries.tsx'), /title: '事实类型'/);
  assert.match(source('./SubledgerPostingDrawer.tsx'), /label: '发布时间'/);
  assert.match(source('./JournalEntryDrawer.tsx'), /subledgerPostingLink\(detail\.accountPostingId, row\.accountEntryId\)/);
  assert.match(source('../navigation/ConsolePage.tsx'), /OPERATIONS_FINANCE_SUBLEDGER: lazy\(\(\) => import\('\.\.\/pages\/FinanceSubledger'\)/);
});

test('从批次抽屉进入流水：按 R19 精确筛选补齐账户摘要，列表下钻与信息不全时不请求', () => {
  const scope = { entityType: 'OPERATING_ENTITY' as const, entityId: 'AE001', currency: 'USD' };
  const partial = { accountId: '7200000000000001', accountCode: 'CLIENT_PAYABLE', ownerType: 'CLIENT' as const, ownerId: '1001', balanceBucket: 'PENDING' as const };
  assert.equal(ownerAccountLookupPath(scope, partial), '/finance/owner-accounts/v1?entityType=OPERATING_ENTITY&entityId=AE001&currency=USD'
    + '&ownerType=CLIENT&ownerId=1001&accountCode=CLIENT_PAYABLE&balanceBucket=PENDING&page=1&size=10');
  assert.equal(ownerAccountLookupPath(scope, decodeOwnerAccount('USD')(ownerAccount)), null, '列表下钻已有净余额');
  assert.equal(ownerAccountLookupPath(scope, { accountId: partial.accountId }), null, '直接粘贴 URL 只有主键');
  assert.equal(ownerAccountLookupPath(null, partial), null);
  const one = { items: [ownerAccount], page: 1, size: 10, total: 1 };
  assert.deepEqual(decodeOwnerAccountLookup(one, partial.accountId, 'USD').netBalance, usd('420.00'));
  assert.throws(() => decodeOwnerAccountLookup({ ...one, items: [], total: 0 }, partial.accountId, 'USD'), ApiError, '必须恰好一行');
  assert.throws(() => decodeOwnerAccountLookup({ ...one, items: [ownerAccount, ownerAccount], total: 2 }, partial.accountId, 'USD'), ApiError);
  assert.throws(() => decodeOwnerAccountLookup(one, '7200000000000009', 'USD'), ApiError, '必须是同一账户');
  assert.match(source('../pages/FinanceSubledger.tsx'), /const account = useAccountSummary\(query\.scope, drilled\)/);
});

const chart = (chartAccountId: string, accountNo: string, accountCode: string, parentChartAccountId: string | null, postingAllowed: boolean,
  accountStatus = 'ACTIVE') => decodeChartAccount({ chartAccountId, chartId: '1', accountNo, accountCode, accountName: `${accountCode} 名称`,
  accountClass: accountNo.startsWith('1') ? 'ASSET' : 'LIABILITY', normalSide: accountNo.startsWith('1') ? 'DEBIT' : 'CREDIT', postingAllowed,
  parentChartAccountId, level: chartAccountLevel(accountNo), accountStatus });

test('科目树：按上级组装、同级按编号排序，搜索保留命中路径，只有汇总节点可展开', () => {
  const accounts = [chart('3009', '22010101', 'CLIENT_PAYABLE', '3207', true), chart('3106', '2201', 'CUSTOMER_FUNDS_PAYABLE', null, false),
    chart('3207', '220101', 'CLIENT_FUNDS_PAYABLE', '3106', false), chart('3208', '220102', 'PARTNER_FUNDS_PAYABLE', '3106', false),
    chart('3003', '10010101', 'BANK_CASH', '3201', true), chart('3201', '100101', 'BANK_DEPOSITS', '3101', false),
    chart('3010', '22010201', 'PARTNER_PAYABLE', '3208', true, 'INACTIVE'), chart('3101', '1001', 'CASH_AND_BANK', null, false)];
  const tree = buildAccountTree(accounts);
  assert.deepEqual(tree.map(node => node.accountNo), ['1001', '2201']);
  assert.deepEqual(tree[1].children?.map(node => node.accountNo), ['220101', '220102']);
  assert.deepEqual(tree[1].children?.map(node => node.children?.map(child => child.accountNo)), [['22010101'], ['22010201']]);
  assert.equal(tree[0].children?.[0].children?.[0].children, undefined, '末级节点不带 children');
  assert.deepEqual(expandableKeys(tree), ['3101', '3201', '3106', '3207', '3208']);
  assert.deepEqual(filterAccountTree(tree, 'partner').map(node => [node.accountNo, node.children?.map(child => child.accountNo)]), [['2201', ['220102']]]);
  assert.equal(filterAccountTree(tree, '1001')[0].children?.length, 1, '命中汇总科目保留整棵子树');
  assert.equal(filterAccountTree(tree, ' ').length, 2);
  assert.deepEqual(buildAccountTree(accounts.filter(item => item.accountNo !== '2201')).map(node => node.accountNo), ['1001', '220101', '220102'],
    '上级被筛掉的科目作为根');
});

test('科目编号分级规则与维护请求体', () => {
  assert.equal(chartAccountNoError('2201', 'LIABILITY', null), null);
  assert.equal(chartAccountNoError('220104', 'LIABILITY', '2201'), null);
  assert.match(chartAccountNoError('22010', 'LIABILITY', '2201') ?? '', /4、6、8 或 10 位/);
  assert.match(chartAccountNoError('1201', 'LIABILITY', null) ?? '', /以 2 开头/);
  assert.match(chartAccountNoError('220201', 'LIABILITY', '2201') ?? '', /上级 2201 后加 2 位/);
  assert.match(chartAccountNoError('220101', 'LIABILITY', null) ?? '', /一级科目编号为 4 位/);
  assert.equal(chartAccountNoError('', 'ASSET'), '请填写科目编号');
  assert.deepEqual([defaultNormalSide('ASSET'), defaultNormalSide('EXPENSE'), defaultNormalSide('EQUITY')], ['DEBIT', 'DEBIT', 'CREDIT']);
  assert.deepEqual(chartAccountBody({ parentAccountNo: '2201', accountNo: ' 220104', accountCode: 'MERCHANT_RESERVE_PAYABLE ', accountName: ' Client 风险准备金 ',
    accountClass: 'LIABILITY', normalSide: 'CREDIT', postingAllowed: true }), { accountNo: '220104', accountCode: 'MERCHANT_RESERVE_PAYABLE',
    accountName: 'Client 风险准备金', accountClass: 'LIABILITY', normalSide: 'CREDIT', postingAllowed: true, parentAccountNo: '2201' });
  assert.equal('parentAccountNo' in chartAccountBody({ accountNo: '2301', accountCode: 'X', accountName: 'x', accountClass: 'LIABILITY',
    normalSide: 'CREDIT', postingAllowed: false, parentAccountNo: undefined }), false, '一级科目不带上级');
  assert.deepEqual(chartAccountUpdateBody({ accountName: ' 新名称 ', accountNo: '2301', parentAccountNo: undefined }),
    { accountName: '新名称', accountNo: '2301', parentAccountNo: null }, '修改时一级科目的上级显式为 null');
  assert.equal(financePaths.chartAccount('STANDARD', '7', 'disable'), '/finance/chart-of-accounts/v1/STANDARD/accounts/7/disable');
  assert.equal(financePaths.chartChanges('STANDARD'), '/finance/chart-of-accounts/v1/STANDARD/changes');
  const disable = financeCommand(financePaths.chartAccount('STANDARD', '7', 'disable'), undefined);
  assert.deepEqual([disable.method, disable.body], ['POST', ''], '停用 / 启用不带请求体');
});

test('科目变更记录：解码、只列有变化的字段', () => {
  const change = { changeId: '91', chartAccountId: '7', accountNo: '220105', changeType: 'UPDATE', beforeAccountNo: '220104', afterAccountNo: '220105',
    beforeParentAccountNo: '2201', afterParentAccountNo: '2201', beforeAccountName: '旧', afterAccountName: '新', beforeAccountStatus: 'ACTIVE',
    afterAccountStatus: 'ACTIVE', operatorId: 'account_10001', changeTime: time };
  assert.deepEqual(chartChangeFields(decodeChartAccountChange('7')(change)), [{ label: '编号', before: '220104', after: '220105' },
    { label: '名称', before: '旧', after: '新' }]);
  const created = decodeChartAccountChange()({ ...change, changeType: 'CREATE', beforeAccountNo: null, beforeParentAccountNo: undefined, beforeAccountName: null,
    beforeAccountStatus: null });
  assert.deepEqual(chartChangeFields(created).map(field => [field.label, field.before]), [['编号', '—'], ['上级', '—'], ['名称', '—'], ['状态', '—']]);
  assert.deepEqual(chartChangeFields({ ...decodeChartAccountChange()(change), changeType: 'DISABLE', beforeAccountNo: '220105', beforeAccountName: '新',
    afterAccountStatus: 'INACTIVE' }), [{ label: '状态', before: '启用', after: '停用' }]);
  assert.throws(() => decodeChartAccountChange('8')(change), ApiError, '必须属于所查科目');
  assert.throws(() => decodeChartAccountChange()({ ...change, changeType: 'DELETE' }), ApiError);
});

const balanceLine = (chartAccountId: string, accountNo: string, parentChartAccountId: string | null, postingAllowed: boolean, normalSide: string,
  periodDebit: string, periodCredit: string, closingBalance: string) => ({ chartAccountId, accountNo, accountCode: `C${accountNo}`, accountName: `科目 ${accountNo}`,
  accountClass: accountNo.startsWith('1') ? 'ASSET' : accountNo.startsWith('4') ? 'REVENUE' : 'LIABILITY', normalSide, parentChartAccountId,
  level: chartAccountLevel(accountNo), postingAllowed, openingBalance: '0.00', periodDebit, periodCredit, closingBalance });
const balanceLines = [balanceLine('3101', '1001', null, false, 'DEBIT', '357.60', '0.00', '357.60'),
  balanceLine('3003', '10010101', '3201', true, 'DEBIT', '357.60', '0.00', '357.60'),
  balanceLine('3106', '2201', null, false, 'CREDIT', '14.40', '357.60', '343.20'),
  balanceLine('3117', '4001', null, false, 'CREDIT', '0.00', '14.40', '14.40')];

test('科目余额与试算：解码、一级合计借 = 贷、详情汇总行不可过账', () => {
  const list = decodeAccountBalanceList({ ledgerBookId: book.ledgerBookId, periodId: '9', currency: 'USD', lines: balanceLines }, book.ledgerBookId, '9');
  assert.equal(list.lines.length, 4);
  assert.throws(() => decodeAccountBalanceList({ ledgerBookId: book.ledgerBookId, periodId: '8', currency: 'USD', lines: [] }, book.ledgerBookId, '9'), ApiError);
  assert.throws(() => decodeAccountBalanceList({ ledgerBookId: book.ledgerBookId, periodId: '9', currency: 'USD',
    lines: [{ ...balanceLines[1], closingBalance: 1 }] }, book.ledgerBookId, '9'), ApiError);
  assert.deepEqual(balanceTotals(list.lines), { periodDebit: '372.00', periodCredit: '372.00', closingDebit: '357.60', closingCredit: '357.60', balanced: true });
  assert.equal(balanceTotals([{ ...list.lines[0], closingBalance: '357.59' }, list.lines[2], list.lines[3]]).balanced, false);
  assert.deepEqual(balanceTotals([{ ...list.lines[2], closingBalance: '-5.00', periodDebit: '5.00', periodCredit: '0.00' },
    { ...list.lines[0], closingBalance: '-5.00', periodDebit: '0.00', periodCredit: '5.00' }]).balanced, true, '反向余额折算后仍平衡');
  const run = { trialBalanceRunId: '77', ledgerBookId: book.ledgerBookId, periodId: '9', runNo: '2', asOfSequence: '1024', status: 'SUCCEEDED',
    totalDebit: '357.60', totalCredit: '357.60', contentHash: 'ab12', lineCount: 1, exceptionCount: 0, createTime: time };
  assert.equal(pageOf(decodeTrialBalanceRun(book.ledgerBookId, '9'))({ items: [run], page: 1, size: 10, total: 1 }).items[0].runNo, '2');
  assert.throws(() => decodeTrialBalanceRun(book.ledgerBookId, '9')({ ...run, status: 'BALANCED' }), ApiError);
  const detail = { ...run, lines: [balanceLines[1]], summaryLines: [balanceLines[0]], exceptions: [{ exceptionType: 'GENERAL_LEDGER_MISMATCH',
    referenceType: 'ACCOUNT', referenceId: '1', differenceAmount: '0.01', status: 'OPEN' }] };
  assert.equal(decodeTrialBalanceDetail(detail, book.ledgerBookId, '9', '2').exceptions.length, 1);
  assert.throws(() => decodeTrialBalanceDetail(detail, book.ledgerBookId, '9', '3'), ApiError, '序号必须与请求一致');
  assert.throws(() => decodeTrialBalanceDetail({ ...detail, summaryLines: [balanceLines[1]] }, book.ledgerBookId, '9', '2'), ApiError, '汇总行不可过账');
  assert.throws(() => decodeTrialBalanceDetail({ ...detail, summaryLines: undefined }, book.ledgerBookId, '9', '2'), ApiError);
  assert.equal(financePaths.accountBalances('1', '9'), '/finance/ledger-books/v1/1/periods/9/account-balances');
  assert.equal(financePaths.trialBalance('1', '9', '2'), '/finance/ledger-books/v1/1/periods/9/trial-balances/2');
});

test('试算平衡 URL：页签、账簿 › 期间 › 视图 › 试算，换上级清下级；末级行进入账簿下钻', () => {
  assert.equal(readLedgerTab(new URLSearchParams('tab=trial')), 'trial');
  assert.equal(readLedgerTab(new URLSearchParams('tab=x')), 'drilldown');
  assert.deepEqual(readTrialQuery(new URLSearchParams('tbBook=1&tbPeriod=9&tbView=runs&tbRun=2')), { book: '1', period: '9', view: 'runs', run: '2' });
  assert.deepEqual(readTrialQuery(new URLSearchParams('tbPeriod=9&tbRun=2')), { book: null, period: null, view: 'balances', run: null });
  assert.equal(readTrialQuery(new URLSearchParams('tbBook=1&tbPeriod=9&tbRun=2')).run, null, '实时余额视图没有试算');
  assert.equal(readTrialQuery(new URLSearchParams('tbBook=1&tbPeriod=9&tbView=runs&tbRun=0')).run, null);
  const search = '?tab=trial&tbBook=1&tbPeriod=9&tbView=runs&tbRun=2';
  assert.equal(trialSearch(search, { book: '5' }), '?tab=trial&tbBook=5&tbView=runs');
  assert.equal(trialSearch(search, { period: '8' }), '?tab=trial&tbBook=1&tbPeriod=8&tbView=runs');
  assert.equal(trialSearch(search, { view: 'balances' }), '?tab=trial&tbBook=1&tbPeriod=9');
  assert.equal(trialSearch(search, { run: null }), '?tab=trial&tbBook=1&tbPeriod=9&tbView=runs');
  assert.equal(ledgerTabSearch('?book=3&tab=trial&tbBook=1', 'drilldown'), '?book=3');
  assert.equal(ledgerTabSearch('?book=3', 'trial'), '?book=3&tab=trial');
  assert.equal(leafDrilldownSearch('1', 'CLIENT_PAYABLE'), '?book=1&accountCode=CLIENT_PAYABLE');
  assert.equal(readLedgerQuery(new URLSearchParams(leafDrilldownSearch('1', 'CLIENT_PAYABLE'))).accountFilters.accountCode, 'CLIENT_PAYABLE');
});
