import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  accountBody, balanceCards, balanceErrorTargets, balanceFieldName, balanceRequestPath, bookBody, bookFilters, bucketOptions, calendarBody,
  decodeAccountingPeriod, decodeChartAccount, decodeChartOfAccounts, decodeClientBalance, decodeCreated, decodeCurrency, decodeFinanceEntity,
  decodeFiscalCalendar, decodeJournalEntryDetail, decodeJournalLine, decodeLedgerAccount, decodeLedgerBook, decodeOpenedPeriod,
  decodePartnerBalance, entityBody, entityLabel, financeCommand, financeErrorTarget, financeErrorTargets, financePaths, formatAmount,
  journalTotals, ledgerLevelSearch, ledgerQuerySearch, lineFilters, listOf, manageFinanceSetup, ownerIdError, pageOf, periodBody,
  periodRangeError, readLedgerQuery, readSetupTab, sumDecimals, timezoneOptions,
} from './finance.ts';

const time = '2026-10-18T05:12:00Z';
const entity = { financeEntityId: '7312345678901234567', entityCode: 'PLT-AE', legalPartyId: '880000000000012001', displayName: '平台阿联酋财务主体', status: 'ACTIVE', createTime: time };
const calendar = { fiscalCalendarId: '7312000000000000001', entityType: 'OPERATING_ENTITY', entityId: 'AE001', calendarCode: 'AE-STD', timezone: 'Asia/Dubai', yearStartMonth: 1, createTime: time };
const period = { accountingPeriodId: '7312000000000000011', fiscalCalendarId: calendar.fiscalCalendarId, fiscalYear: 2026, periodNo: 11,
  startDate: '2026-11-01', endDate: '2026-11-30', status: 'FUTURE', reopenCount: 0 };
const book = { ledgerBookId: '7318800000000000101', entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', chartCode: 'STANDARD', bookStatus: 'OPEN' };
const account = { accountId: '7318800000000000201', accountCode: 'CLIENT_PAYABLE', ownerType: 'CLIENT', ownerId: '880000000000079001', balanceBucket: 'AVAILABLE',
  normalSide: 'CREDIT', accountStatus: 'ACTIVE', debitTotal: '0.00', creditTotal: '1250.00', netBalance: '1250.00', lastJournalSequence: '1024' };
const line = { journalEntryId: '7318802201114001024', journalSequence: '1024', postingDate: '2026-10-18', entryType: 'STANDARD', lineNo: 2, entrySide: 'CREDIT',
  amount: '120.00', balanceAfter: '1250.00', instructionType: 'PAYMENT_SUCCEEDED', originType: 'AUTO', sourceSystem: 'payment',
  sourceEventKey: 'pay-evt-20261018-000931', adjustmentNo: null };
const entryLine = (lineNo: number, entrySide: string, amount: string) => ({ lineNo, accountId: '7318800000000000201', accountCode: 'CLIENT_PAYABLE',
  ownerType: 'CLIENT', ownerId: '880000000000079001', balanceBucket: 'AVAILABLE', entrySide, amount });
const entry = { journalEntryId: line.journalEntryId, ledgerBookId: book.ledgerBookId, accountingPeriodId: '7312000000000000010', journalSequence: '1024',
  postingDate: '2026-10-18', entryType: 'STANDARD', reversalOfEntryId: null, instructionId: '7318802201114000987', instructionType: 'PAYMENT_SUCCEEDED',
  originType: 'AUTO', sourceSystem: 'payment', sourceEventKey: 'pay-evt-20261018-000931', manualAdjustmentId: null, adjustmentNo: null,
  lines: [entryLine(1, 'DEBIT', '125.00'), entryLine(2, 'CREDIT', '120.00'), entryLine(3, 'CREDIT', '5.00')] };
const usd = (amount: string) => ({ currency: 'USD', amount });

test('页面注册：三页追加在末尾，只有账务基础资料有维护动作', () => {
  const finance = pageCapabilities.slice(-3);
  assert.deepEqual(finance.map(page => [page.componentKey, page.frontendRoute]), [
    ['OPERATIONS_FINANCE_SETUP', '/finance/setup'], ['OPERATIONS_FINANCE_LEDGER', '/finance/ledger'], ['OPERATIONS_FINANCE_BALANCES', '/finance/balances']]);
  assert.deepEqual(finance.map(page => page.actions.map(action => action.resourceCode)), [[manageFinanceSetup], [], []]);
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
  const chartAccount = { chartAccountId: '11', chartId: '1', accountCode: 'CLIENT_PAYABLE', accountName: 'Client 应付', accountClass: 'LIABILITY', normalSide: 'CREDIT', postingAllowed: true };
  assert.equal(decodeChartAccount(chartAccount).postingAllowed, true);
  assert.throws(() => decodeChartAccount({ ...chartAccount, postingAllowed: 'true' }), ApiError);
  assert.equal(decodeCurrency({ currency: 'USD', minorUnit: 2, roundingMode: 'HALF_UP', status: 'ACTIVE' }).minorUnit, 2);
  assert.throws(() => decodeCurrency({ currency: 'usd', minorUnit: 2, roundingMode: 'HALF_UP', status: 'ACTIVE' }), ApiError);
});

test('账簿、账户、流水与分录解码；可空字段缺失视为 null', () => {
  assert.equal(pageOf(decodeLedgerBook)({ items: [book], page: 1, size: 10, total: 1 }).items[0].chartCode, 'STANDARD');
  assert.throws(() => decodeLedgerBook({ ...book, bookStatus: 'ACTIVE' }), ApiError);
  assert.equal(decodeLedgerAccount(account).netBalance, '1250.00');
  assert.equal(decodeLedgerAccount({ ...account, lastJournalSequence: undefined }).lastJournalSequence, null);
  assert.throws(() => decodeLedgerAccount({ ...account, netBalance: 1250 }), ApiError);
  assert.throws(() => decodeLedgerAccount({ ...account, balanceBucket: 'FROZEN' }), ApiError);
  assert.equal(decodeLedgerAccount({ ...account, netBalance: '-20.00' }).netBalance, '-20.00');
  assert.equal(decodeJournalLine(line).sourceSystem, 'payment');
  const manual = { ...line, originType: 'MANUAL', sourceSystem: undefined, sourceEventKey: undefined, adjustmentNo: 'MA-20261017-0007' };
  assert.deepEqual([decodeJournalLine(manual).sourceSystem, decodeJournalLine(manual).adjustmentNo], [null, 'MA-20261017-0007']);
  assert.throws(() => decodeJournalLine({ ...line, postingDate: '2026/10/18' }), ApiError);
  assert.equal(decodeJournalEntryDetail(entry, book.ledgerBookId, entry.journalEntryId).lines.length, 3);
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

test('请求体：去空白、非应付科目强制 NONE、期间原样提交', () => {
  assert.deepEqual(entityBody({ entityCode: ' PLT-AE ', legalPartyId: '880000000000012001 ', displayName: ' 平台 ' }),
    { entityCode: 'PLT-AE', legalPartyId: '880000000000012001', displayName: '平台' });
  assert.deepEqual(calendarBody({ entityType: 'OPERATING_ENTITY', entityId: ' AE001', calendarCode: 'AE-STD ', timezone: 'Asia/Dubai', yearStartMonth: 1 }),
    { entityType: 'OPERATING_ENTITY', entityId: 'AE001', calendarCode: 'AE-STD', timezone: 'Asia/Dubai', yearStartMonth: 1 });
  assert.deepEqual(periodBody({ fiscalYear: 2026, periodNo: 11, startDate: '2026-11-01', endDate: '2026-11-30' }),
    { fiscalYear: 2026, periodNo: 11, startDate: '2026-11-01', endDate: '2026-11-30' });
  assert.deepEqual(bookBody({ entityType: 'FINANCE_ENTITY', entityId: '7312345678901234567', currency: 'USD', chartCode: 'STANDARD' }),
    { entityType: 'FINANCE_ENTITY', entityId: '7312345678901234567', currency: 'USD', chartCode: 'STANDARD' });
  assert.equal(accountBody({ accountCode: 'CLIENT_SECURITY_DEPOSIT', ownerType: 'CLIENT', ownerId: '1', balanceBucket: 'AVAILABLE' }).balanceBucket, 'NONE');
  assert.equal(accountBody({ accountCode: 'PARTNER_PAYABLE', ownerType: 'LOCAL_PARTNER', ownerId: '1', balanceBucket: 'LOCKED' }).balanceBucket, 'LOCKED');
  assert.deepEqual(bucketOptions('CLIENT_PAYABLE'), ['PENDING', 'AVAILABLE', 'LOCKED']);
  assert.deepEqual(bucketOptions('BANK_CASH'), ['NONE']);
  assert.deepEqual(bucketOptions(undefined), ['NONE']);
});

test('表单校验：核算对象标识按类型、期间起止、时区下拉', () => {
  assert.equal(ownerIdError('OPERATING_ENTITY', 'AE001'), null);
  assert.match(ownerIdError('OPERATING_ENTITY', 'ae001') ?? '', /AE001/);
  assert.equal(ownerIdError('CLIENT', '880000000000079001'), null);
  assert.equal(ownerIdError('CLIENT', '88000000000007900A'), '必须是正十进制整数字符串');
  assert.equal(ownerIdError('CHANNEL_ACCOUNT', '0'), '必须是正十进制整数字符串');
  assert.equal(ownerIdError('CLIENT', ' '), '请填写核算对象标识');
  assert.equal(periodRangeError('2026-11-01', '2026-11-30'), null);
  assert.equal(periodRangeError('2026-11-30', '2026-11-01'), '开始日期不能晚于结束日期');
  assert.deepEqual(timezoneOptions(() => ['Asia/Dubai']), ['UTC', 'Asia/Dubai']);
  assert.deepEqual(timezoneOptions(() => { throw new Error('unsupported'); }), ['UTC']);
  assert.ok(timezoneOptions().includes('Asia/Dubai'));
});

test('错误码映射：15 个 Code 都有落点，字段不在表单内时退回表单顶部；余额页 LEDGER_BOOK_NOT_FOUND 落在 entityNo', () => {
  assert.equal(Object.keys(financeErrorTargets).length + Object.keys(balanceErrorTargets).length, 16, 'ledger 15 个 Code + account 同名 Code');
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
  assert.deepEqual(financeErrorTarget(error('LEDGER_BOOK_NOT_FOUND'), ['entityNo'], balanceErrorTargets),
    { field: 'entityNo', message: '未找到该经营主体与币种的账簿（LEDGER_BOOK_NOT_FOUND）' });
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
  for (const readOnly of ['./SetupChartsTab.tsx', './LedgerDrilldown.tsx', '../pages/FinanceLedger.tsx', '../pages/FinanceBalances.tsx']) {
    assert.doesNotMatch(source(readOnly), /FinanceModals|>新建/, `${readOnly} 不得出现新建入口`);
  }
  assert.match(source('./LedgerDrilldown.tsx'), /行后余额按全量历史累计，筛选不改变起点/);
  const balances = source('../pages/FinanceBalances.tsx');
  assert.match(balances, /Available 不等于可出款资格/);
  assert.match(balances, /未找到账簿，请确认 entityNo 与币种后重新查询/);
  assert.match(balances, /选择 Owner 类型并填写 Owner ID、entityNo 与币种后查询/);
  assert.match(source('./SetupCalendarsTab.tsx'), /选择一个日历查看会计期间/);
  assert.match(source('./SetupBooksTab.tsx'), /选择一个账簿查看账户/);
  assert.match(source('./SetupEntitiesTab.tsx'), /还没有财务主体/);
});
