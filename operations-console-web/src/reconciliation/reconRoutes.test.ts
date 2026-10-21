import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import { pageRoutePaths } from '../navigation/menu.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import { decodeBankLineDetail, decodeChannelLine, reconPaths } from './reconModel.ts';
import {
  backTarget, channelLineFilterKeys, channelLinePageSizes, channelLineQuery, channelLinesOfTxn, compactFilters, exceptionSourceLink, highlightLineNo,
  isNotFoundFailure, isReconId, linePageOf, movementSourceLink, platformTxnsOfLine, readListQuery, reconRoutes, withQuery, writeListQuery,
} from './reconRoutes.ts';

const menu = (componentKey: string, frontendRoute: string): ConsoleResource => ({
  resourceId: '1', parentResourceId: null, resourceType: 'MENU', resourceCode: componentKey, displayName: componentKey, componentKey, frontendRoute,
  sortOrder: 1,
} as unknown as ConsoleResource);

test('子路由：对账详情页挂在同一菜单页下，路径与 reconRoutes 一致', () => {
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_FINANCE_RECON_CLEARING', reconRoutes.clearingAccounts)),
    [reconRoutes.clearingAccounts, `${reconRoutes.clearingAccounts}/:clearingAccountId`]);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_FINANCE_RECON_CASH', reconRoutes.cashAccounts)),
    [reconRoutes.cashAccounts, `${reconRoutes.cashAccounts}/:cashAccountId`]);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_FINANCE_RECON_STATEMENTS', reconRoutes.statements)),
    [reconRoutes.statements, `${reconRoutes.statements}/:statementId`]);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_FINANCE_RECON_BANK', reconRoutes.bank)),
    [reconRoutes.bank, `${reconRoutes.bank}/statements/:bankStatementId`, `${reconRoutes.bank}/lines/:bankStatementLineId`]);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_FINANCE_RECON_EXCEPTIONS', reconRoutes.exceptions)),
    [reconRoutes.exceptions, `${reconRoutes.exceptions}/:exceptionId`]);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_FINANCE_RECON_CHANNEL_LINES', reconRoutes.channelLines)), [reconRoutes.channelLines]);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_FINANCE_RECON_PLATFORM_TXNS', reconRoutes.platformTxns)), [reconRoutes.platformTxns]);
  assert.equal(reconRoutes.bankLine('5'), '/finance/reconciliation/bank/lines/5');
  assert.equal(reconRoutes.bankStatement('6'), '/finance/reconciliation/bank/statements/6');
  assert.equal(reconRoutes.statement('7', 12), '/finance/reconciliation/statements/7?line=12');
  assert.equal(reconRoutes.statement('7', null), '/finance/reconciliation/statements/7');
});

test('渠道流水页：注册在平台流水之后，只读、无按钮资源', () => {
  const keys = pageCapabilities.map(page => page.componentKey as string);
  const index = keys.indexOf('OPERATIONS_FINANCE_RECON_CHANNEL_LINES');
  assert.equal(keys[index - 1], 'OPERATIONS_FINANCE_RECON_PLATFORM_TXNS');
  assert.deepEqual(pageCapabilities[index], { componentKey: 'OPERATIONS_FINANCE_RECON_CHANNEL_LINES', displayName: '渠道流水',
    frontendRoute: '/finance/reconciliation/channel-lines', actions: [] });
  assert.equal(reconPaths.channelLines, '/finance/reconciliation/v1/channel-statement-lines');
  assert.equal(reconPaths.bankStatement('3'), '/finance/reconciliation/v1/bank-statements/3');
  assert.equal(reconPaths.bankLine('4'), '/finance/reconciliation/v1/bank-statement-lines/4');
});

test('URL 筛选：读写往返，保留其他参数；缺省筛选与「清空缺省」可区分；非法页码回默认', () => {
  const keys = ['matchStatus', 'direction'];
  const defaults = { matchStatus: 'UNMATCHED' };
  assert.deepEqual(readListQuery('?account=9&tab=lines', keys, defaults), { filters: { matchStatus: 'UNMATCHED' }, paging: { page: 1, size: 10 } });
  const written = writeListQuery('?account=9&tab=lines&page=4', keys, { filters: { direction: 'CREDIT' }, paging: { page: 3, size: 20 } }, defaults);
  assert.equal(written, '?account=9&tab=lines&matchStatus=&direction=CREDIT&page=3&size=20');
  assert.deepEqual(readListQuery(written, keys, defaults), { filters: { direction: 'CREDIT' }, paging: { page: 3, size: 20 } });
  assert.equal(writeListQuery('?account=9', keys, { filters: { matchStatus: 'UNMATCHED' }, paging: { page: 1, size: 10 } }, defaults), '?account=9');
  assert.equal(writeListQuery('', keys, { filters: {}, paging: { page: 1, size: 10 } }), '');
  assert.deepEqual(readListQuery('?page=0&size=7&other=x', keys).paging, { page: 1, size: 10 });
  assert.deepEqual(readListQuery('?page=2&size=200', keys, {}, channelLinePageSizes).paging, { page: 2, size: 10 });
  assert.deepEqual(readListQuery('?page=2&size=100', keys, {}, channelLinePageSizes).paging, { page: 2, size: 100 });
  assert.deepEqual(readListQuery('?direction=%20DEBIT%20&unknown=1', keys).filters, { direction: 'DEBIT' });
  assert.deepEqual(compactFilters({ a: ' x ', b: '', c: undefined, d: null }), { a: 'x' });
});

test('渠道流水查询：只取已知键并去空白；日期格式与先后校验；交易号可单独查询', () => {
  assert.deepEqual(channelLineFilterKeys, ['clearingAccountId', 'statementStatus', 'matchStatus', 'lineType', 'referenceType', 'externalTransactionId',
    'transactionDateFrom', 'transactionDateTo']);
  assert.deepEqual(channelLineQuery({ clearingAccountId: '12', externalTransactionId: ' NI-ORD-0001 ', statementStatus: '', foo: 'bar' }),
    { filters: { clearingAccountId: '12', externalTransactionId: 'NI-ORD-0001' }, error: null });
  assert.equal(channelLineQuery({ referenceType: 'NI_ORDER_REF' }).error, null);
  assert.equal(channelLineQuery({ transactionDateFrom: '2026/10/01' }).error, '交易日期格式为 YYYY-MM-DD');
  assert.equal(channelLineQuery({ transactionDateFrom: '2026-10-21', transactionDateTo: '2026-10-01' }).error, '交易日期起不能晚于止');
  assert.equal(channelLineQuery({ transactionDateFrom: '2026-10-01', transactionDateTo: '2026-10-01' }).error, null);
});

test('高亮行：?line= 取正整数行号；无筛选时所在页 = ceil(行号 / 每页条数)', () => {
  assert.equal(highlightLineNo('?line=21'), 21);
  assert.equal(highlightLineNo('?line=0'), null);
  assert.equal(highlightLineNo('?line=abc'), null);
  assert.equal(highlightLineNo(''), null);
  assert.equal(linePageOf(1, 20), 1); assert.equal(linePageOf(20, 20), 1); assert.equal(linePageOf(21, 20), 2); assert.equal(linePageOf(0, 20), 1);
});

test('链接：平台流水 ↔ 账单行、预期银行流水 → 渠道账单、带查询串的列表', () => {
  assert.equal(channelLinesOfTxn({ clearingAccountId: '8', referenceType: 'NI_ORDER_REF', externalTransactionId: 'NI-ORD-0001' }),
    '/finance/reconciliation/channel-lines?clearingAccountId=8&referenceType=NI_ORDER_REF&externalTransactionId=NI-ORD-0001');
  assert.equal(platformTxnsOfLine({ clearingAccountId: '8', externalTransactionId: 'NI-ORD-0001' }),
    '/finance/reconciliation/platform-txns?clearingAccountId=8&externalTransactionId=NI-ORD-0001');
  assert.equal(platformTxnsOfLine({ clearingAccountId: '8', externalTransactionId: null }), '/finance/reconciliation/platform-txns?clearingAccountId=8');
  assert.equal(movementSourceLink({ movementType: 'CHANNEL_SETTLEMENT', sourceId: '44' }), '/finance/reconciliation/statements/44');
  assert.equal(movementSourceLink({ movementType: 'PAYOUT', sourceId: '44' }), null);
  assert.equal(withQuery('/x', { a: '1', b: null, c: '' }), '/x?a=1');
  assert.equal(withQuery('/x', {}), '/x');
});

test('异常来源：六类来源对象各自指向详情页或列表', () => {
  const base = { sourceId: '30', statementId: '20', clearingAccountId: '10', cashAccountId: '11' };
  assert.equal(exceptionSourceLink({ ...base, sourceType: 'CHANNEL_STATEMENT' }), '/finance/reconciliation/statements/30');
  assert.equal(exceptionSourceLink({ ...base, sourceType: 'CHANNEL_STATEMENT_LINE' }, { reference: 'NI-20261019-001 第 3 行' }),
    '/finance/reconciliation/statements/20?line=3');
  assert.equal(exceptionSourceLink({ ...base, sourceType: 'CHANNEL_STATEMENT_LINE' }, { reference: null }), '/finance/reconciliation/statements/20');
  assert.equal(exceptionSourceLink({ ...base, sourceType: 'PLATFORM_TXN' }, { reference: 'NI-ORD-0001' }),
    '/finance/reconciliation/platform-txns?clearingAccountId=10&externalTransactionId=NI-ORD-0001');
  assert.equal(exceptionSourceLink({ ...base, sourceType: 'BANK_STATEMENT' }), '/finance/reconciliation/bank/statements/30');
  assert.equal(exceptionSourceLink({ ...base, sourceType: 'BANK_STATEMENT_LINE' }), '/finance/reconciliation/bank/lines/30');
  assert.equal(exceptionSourceLink({ ...base, sourceType: 'EXPECTED_BANK_MOVEMENT' }), '/finance/reconciliation/bank?account=11&tab=movements');
  assert.equal(exceptionSourceLink({ ...base, sourceType: 'UNKNOWN' }), null);
  assert.equal(exceptionSourceLink({ ...base, statementId: null, sourceType: 'CHANNEL_STATEMENT_LINE' }), null);
});

test('返回列表：来源是同一列表时带回筛选与页码，否则回到列表首页', () => {
  assert.equal(backTarget(reconRoutes.statements, '/finance/reconciliation/statements?status=MATCHING&page=3'),
    '/finance/reconciliation/statements?status=MATCHING&page=3');
  assert.equal(backTarget(reconRoutes.statements, '/finance/reconciliation/channel-lines?externalTransactionId=X'), reconRoutes.statements);
  assert.equal(backTarget(reconRoutes.statements, undefined), reconRoutes.statements);
  assert.equal(backTarget(reconRoutes.statements, 42), reconRoutes.statements);
});

test('未找到：404，或既有接口 400 业务拒绝「…不存在: 当前主键」；其他拒绝不算', () => {
  assert.equal(isNotFoundFailure({ status: 404, code: 'BANK_STATEMENT_NOT_FOUND' }, '1'), true);
  assert.equal(isNotFoundFailure({ status: 400, code: 'BUSINESS_CHECK_FAILED', detail: '渠道账单不存在: 123' }, '123'), true);
  assert.equal(isNotFoundFailure({ status: 400, code: 'BUSINESS_CHECK_FAILED', detail: '渠道账单不存在: 1234' }, '123'), false);
  assert.equal(isNotFoundFailure({ status: 400, code: 'BUSINESS_CHECK_FAILED', detail: '账单状态不允许' }, '123'), false);
  assert.equal(isNotFoundFailure({ status: 400, code: 'INVALID_REQUEST', detail: '渠道账单不存在: 123' }, '123'), false);
  assert.equal(isNotFoundFailure({ status: 503, code: 'SERVICE_UNAVAILABLE' }, '123'), false);
  assert.equal(isReconId('123'), true); assert.equal(isReconId('0'), false); assert.equal(isReconId('abc'), false); assert.equal(isReconId(undefined), false);
});

test('解码：渠道流水行带账单主键；银行流水详情核对流水主键，缺未结异常时补空列表', () => {
  const row = { lineId: '1', statementId: '2', lineNo: 1 };
  assert.equal(decodeChannelLine(row), row);
  assert.throws(() => decodeChannelLine({ lineId: '1' }), ApiError);
  const detail = { line: { bankStatementLineId: '5' }, statement: null, matches: [] };
  assert.deepEqual(decodeBankLineDetail('5')(detail).openExceptions, []);
  assert.throws(() => decodeBankLineDetail('6')(detail), ApiError);
  assert.throws(() => decodeBankLineDetail('5')({ line: { bankStatementLineId: '5' } }), ApiError);
});
