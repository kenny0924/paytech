import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  chunkLines, classificationOptions, classificationsBySource, compareDecimal, decodeClearingAccount, decodeExceptionDetail, decodeUtf8,
  expectedRemaining, manualMatchTotals, parseBankStatementCsv, parseChannelStatementCsv, parseCsv, positiveAmountError, reconActions,
  reconPaths, rematchTargetType, sha256Hex, signedSum,
} from './reconModel.ts';

const repo = (path: string) => readFileSync(new URL(`../../../${path}`, import.meta.url), 'utf8');
const javaEnum = (path: string) => [...repo(path).matchAll(/^\s{4}([A-Z][A-Z_]+)[,;]/gm)].map(match => match[1]);

/** 设计稿第 3 节 S1：PAYMENT 100（费 0.45）、REFUND 30、ADJUSTMENT 月费 2.00；净额 67.55 CREDIT。 */
const s1 = [
  '#HEADER,clearing_account_code,channel_batch_ref,settlement_date,currency,gross_amount,fee_amount,net_amount,cash_direction',
  '#HEADER,NI-AE001-USD,NI-20261019-001,2026-10-19,USD,68.00,0.45,67.55,CREDIT',
  'line_no,line_type,direction,amount,fee_amount,reference_type,external_transaction_id,transaction_date,terminal_id,remark',
  '1,PAYMENT,CREDIT,100.00,0.45,NI_ORDER_REF,NI-ORD-0001,2026-10-18,T01,',
  '2,REFUND,DEBIT,30.00,0,NI_REFUND_REF,NI-RFD-0001,2026-10-18,T01,"partial, customer"',
  '3,ADJUSTMENT,DEBIT,2.00,0,,,2026-10-19,,monthly fee',
].join('\r\n');

const bank = [
  '#HEADER,bank_account_last4,statement_ref,period_start,period_end,currency,opening_balance,closing_balance',
  '#HEADER,4321,ENBD-202610-19,2026-10-19,2026-10-19,USD,-10.00,57.40',
  'line_no,bank_transaction_ref,value_date,direction,amount,counterparty_name,reference_text',
  '1,ENBD-TXN-0001,2026-10-19,CREDIT,67.40,NETWORK INTERNATIONAL,SETTLEMENT NI-20261019-001',
].join('\n');

test('路径：BFF 对账路径为 /finance + reconciliation 路径', () => {
  assert.equal(reconPaths.clearingAccounts, '/finance/reconciliation/v1/clearing-accounts');
  assert.equal(reconPaths.clearingMid('1', '2'), '/finance/reconciliation/v1/clearing-accounts/1/mids/2');
  assert.equal(reconPaths.channelStatementCompletion('9'), '/finance/reconciliation/v1/channel-statements/9/completion');
  assert.equal(reconPaths.bankLines, '/finance/reconciliation/v1/bank-statement-lines');
  assert.equal(reconPaths.resolutionApproval('7'), '/finance/reconciliation/v1/recon-resolutions/7/approval');
  assert.equal(reconPaths.summary, '/finance/reconciliation/v1/summary');
});

test('能力清单：七个对账页面与两个按钮登记在 pageCapabilities，按钮挂在对账异常页', () => {
  const pages = pageCapabilities.filter(page => page.componentKey.startsWith('OPERATIONS_FINANCE_RECON_'));
  assert.deepEqual(pages.map(page => page.componentKey), ['OPERATIONS_FINANCE_RECON_CLEARING', 'OPERATIONS_FINANCE_RECON_CASH',
    'OPERATIONS_FINANCE_RECON_STATEMENTS', 'OPERATIONS_FINANCE_RECON_PLATFORM_TXNS', 'OPERATIONS_FINANCE_RECON_BANK',
    'OPERATIONS_FINANCE_RECON_EXCEPTIONS', 'OPERATIONS_FINANCE_RECON_SUMMARY']);
  assert.ok(pages.every(page => page.frontendRoute.startsWith('/finance/reconciliation/')));
  const actions = pages.flatMap(page => page.actions.map(action => action.resourceCode));
  assert.deepEqual(actions, [reconActions.operate, reconActions.approve]);
});

test('CSV：引号、逗号、引号转义、CRLF 与空行', () => {
  assert.deepEqual(parseCsv('a,"b,c","d ""e"""\r\n\r\nf,,g\n'), [['a', 'b,c', 'd "e"'], ['f', '', 'g']]);
  assert.throws(() => parseCsv('a,"b'), /引号未闭合/);
});

test('渠道账单：S1 解析为账单头与三行明细，特有列进 rawAttributes（空值不带）', () => {
  const { file, issues } = parseChannelStatementCsv(s1);
  assert.deepEqual(issues, []);
  assert.deepEqual(file?.header, { clearingAccountCode: 'NI-AE001-USD', channelBatchRef: 'NI-20261019-001', settlementDate: '2026-10-19',
    currency: 'USD', grossAmount: '68.00', feeAmount: '0.45', netAmount: '67.55', cashDirection: 'CREDIT' });
  assert.equal(file?.lines.length, 3);
  assert.deepEqual(file?.lines[0].rawAttributes, { terminal_id: 'T01' });
  assert.deepEqual(file?.lines[1].rawAttributes, { terminal_id: 'T01', remark: 'partial, customer' });
  assert.deepEqual(file?.lines[2], { lineNo: 3, lineType: 'ADJUSTMENT', direction: 'DEBIT', amount: '2.00', feeAmount: '0', referenceType: null,
    externalTransactionId: null, transactionDate: '2026-10-19', rawAttributes: { remark: 'monthly fee' } });
});

test('渠道账单：结构错误整单拒绝并给出行号与原因', () => {
  assert.deepEqual(parseChannelStatementCsv('line_no\n1').issues, [{ lineNo: null, reason: '前两行必须是以 #HEADER 开头的账单头列名行与取值行' }]);
  const missing = s1.replace(',cash_direction', '').replace(',CREDIT\r\n', '\r\n');
  assert.ok(parseChannelStatementCsv(missing).issues.some(issue => issue.reason === '账单头缺少列 cash_direction'));
  const bad = s1.replace('2,REFUND,DEBIT,30.00', '4,REFUND,SIDEWAYS,-30').replace('3,ADJUSTMENT,DEBIT', '3,PAYMENT,DEBIT');
  const { file, issues } = parseChannelStatementCsv(bad);
  assert.equal(file, null);
  assert.deepEqual(issues, [
    { lineNo: 2, reason: 'line_no 应为 2，实际为「4」' },
    { lineNo: 2, reason: 'direction 只能为 CREDIT 或 DEBIT' },
    { lineNo: 2, reason: 'amount 必须是非负十进制金额' },
    { lineNo: 3, reason: 'PAYMENT 行必须带外部交易号' },
  ]);
  assert.ok(parseChannelStatementCsv(s1.replace('2026-10-19,USD', '2026-02-30,USD')).issues
    .some(issue => issue.reason === 'settlement_date 必须是 YYYY-MM-DD'));
});

test('银行对账单：期初可为负；末四位须与所选银行账户一致；流水金额须为正', () => {
  const { file, issues } = parseBankStatementCsv(bank, '4321');
  assert.deepEqual(issues, []);
  assert.equal(file?.header.openingBalance, '-10.00');
  assert.deepEqual(file?.lines[0], { lineNo: 1, bankTransactionRef: 'ENBD-TXN-0001', valueDate: '2026-10-19', direction: 'CREDIT', amount: '67.40',
    counterpartyName: 'NETWORK INTERNATIONAL', referenceText: 'SETTLEMENT NI-20261019-001' });
  assert.deepEqual(parseBankStatementCsv(bank, '9999').issues, [{ lineNo: null, reason: '文件末四位 4321 与所选银行账户 9999 不一致' }]);
  assert.deepEqual(parseBankStatementCsv(bank.replace('CREDIT,67.40', 'CREDIT,0'), '4321').issues,
    [{ lineNo: 1, reason: 'amount 必须是正的十进制金额' }]);
  const empty = bank.split('\n').slice(0, 3).join('\n');
  assert.deepEqual(parseBankStatementCsv(empty, '4321').file?.lines, []);
});

test('文件：严格 UTF-8、去 BOM；SHA-256 为 64 位小写十六进制', async () => {
  const bytes = new TextEncoder().encode('﻿abc');
  assert.equal(decodeUtf8(bytes.buffer as ArrayBuffer), 'abc');
  assert.throws(() => decodeUtf8(new Uint8Array([0xff, 0xfe, 0xfd]).buffer));
  assert.equal(await sha256Hex(new TextEncoder().encode('abc').buffer as ArrayBuffer),
    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
});

test('分段：每段不超过 100 行且序列化不超过上限', () => {
  const lines = Array.from({ length: 250 }, (_, index) => ({ lineNo: index + 1, note: 'x'.repeat(10) }));
  assert.deepEqual(chunkLines(lines).map(chunk => chunk.length), [100, 100, 50]);
  const wide = Array.from({ length: 30 }, (_, index) => ({ lineNo: index + 1, note: 'x'.repeat(5000) }));
  const chunks = chunkLines(wide);
  assert.ok(chunks.every(chunk => JSON.stringify({ lines: chunk }).length <= 60000));
  assert.equal(chunks.flat().length, 30);
});

test('十进制：带符号合计与比较精确，不受浮点误差影响', () => {
  assert.equal(signedSum([['0.1', 1], ['0.2', 1]]), '0.3');
  assert.equal(signedSum([['67.55', 1], ['67.40', -1]]), '0.15');
  assert.equal(signedSum([['1', 1], ['2.005', -1]]), '-1.005');
  assert.equal(compareDecimal('10.00', '10'), 0);
  assert.equal(compareDecimal('-0.01', '0'), -1);
  assert.equal(expectedRemaining({ expectedAmount: '67.55', confirmedAmount: '67.40', classifiedAmount: '0.15' }), '0.00');
  assert.equal(positiveAmountError('0.001'), '小数位不能超过 2 位');
  assert.equal(positiveAmountError('0'), '金额必须大于零');
});

test('人工多选匹配：同向为正、反向为负，实时剩余；超出流水金额或预期剩余时不能提交（AC-V5）', () => {
  const line = { direction: 'CREDIT', amount: '100.00', matchedAmount: '10.00' };
  const credit = (amount: string, remaining = '80.00') => ({ expectedMovementId: '1', direction: 'CREDIT', amount, remaining });
  const debit = (amount: string) => ({ expectedMovementId: '2', direction: 'DEBIT', amount, remaining: '50.00' });
  const ok = manualMatchTotals(line, [credit('80.00'), debit('5.00')]);
  assert.deepEqual([ok.signed, ok.remaining, ok.exceeds, ok.submittable], ['75.00', '15.00', false, true]);
  const exact = manualMatchTotals(line, [credit('80.00'), credit('10.00', '10.00')]);
  assert.deepEqual([exact.remaining, exact.submittable], ['0.00', true]);
  const over = manualMatchTotals(line, [credit('80.00'), credit('10.01', '20.00')]);
  assert.deepEqual([over.remaining, over.exceeds, over.submittable], ['-0.01', true, false]);
  const beyondMovement = manualMatchTotals(line, [credit('80.01')]);
  assert.deepEqual([beyondMovement.errors[0], beyondMovement.submittable], ['超过预期流水剩余 80.00', false]);
  const negative = manualMatchTotals(line, [debit('5.00')]);
  assert.deepEqual([negative.signed, negative.submittable], ['-5.00', false]);
  assert.equal(manualMatchTotals(line, []).submittable, false);
  assert.equal(manualMatchTotals(line, [credit('abc')]).signed, '0');
});

test('归类码：按来源对象（设计 F3）与 reconciliation 当前可选取交集，顺序按 F3；与 ReconClassification 枚举一致', () => {
  assert.deepEqual(classificationOptions('EXPECTED_BANK_MOVEMENT', ['RECON_DIFFERENCE', 'BANK_FEE', 'BANK_INTEREST']), ['BANK_FEE', 'RECON_DIFFERENCE']);
  assert.deepEqual(classificationOptions('PLATFORM_TXN', ['BANK_FEE', 'RECON_DIFFERENCE']), ['RECON_DIFFERENCE']);
  assert.deepEqual(classificationOptions('CHANNEL_STATEMENT', ['RECON_DIFFERENCE']), []);
  assert.equal(rematchTargetType('BANK'), 'EXPECTED_BANK_MOVEMENT');
  assert.equal(rematchTargetType('TRANSACTION'), 'PLATFORM_TXN');
  const codes = javaEnum('paytech/reconciliation/paytech-reconciliation-api/src/main/java/com/paytech/reconciliation/api/enums/ReconClassification.java');
  assert.deepEqual([...new Set(Object.values(classificationsBySource).flat())].sort(), [...codes].sort());
});

test('解码：主键与请求一致，集合缺失按上游无效响应', () => {
  const invalid = (run: () => unknown) => assert.throws(run, (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE');
  invalid(() => decodeClearingAccount('1')({ clearingAccountId: '2', mids: [] }));
  invalid(() => decodeClearingAccount('1')({ clearingAccountId: '1' }));
  const detail = decodeExceptionDetail('5')({ exception: { exceptionId: '5' }, source: {}, availableResolutionTypes: [], availableClassificationCodes: [], resolutions: [] });
  assert.equal(detail.account, null);
  invalid(() => decodeExceptionDetail('5')({ exception: { exceptionId: '6' }, source: {}, availableResolutionTypes: [], availableClassificationCodes: [], resolutions: [] }));
});
