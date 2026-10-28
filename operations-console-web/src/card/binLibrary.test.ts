import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pagingQuery } from '../api/paging.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  activeFilterCount, binLibraryManageButton, binLibraryPaths, binPrefixIssue, csvFileIssue, csvHeader, csvTemplate, decodeActivationPage,
  decodeImportErrorPage, decodeLookup, decodeRange, decodeRangePage, decodeUploadCompletion, decodeUploadStart, decodeVersion, decodeVersionPage,
  emptyRangeFilters, isBrowsable, isQueryableBin, maxCsvBytes, operatorDisplay, previewCsv, rangeQueryFilters, rowDelta, splitCsvLine,
  versionAction, versionProgress,
} from './binLibrary.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('card');

const version = {
  versionNo: 4, status: 'VALID', active: false, fileAddress: '/files/v1/7409912387612', fileName: 'bin-2026-10-21.csv',
  fileContentHash: 'a'.repeat(64), totalRows: 315902, processedRows: 315902, errorRows: 0, failureReason: null,
  createdBy: '10001', createTime: '2026-10-21T08:40:00.000000Z', updateTime: '2026-10-21T08:46:12.000000Z',
};

test('注册：卡 BIN 库页面与维护按钮登记在 pageCapabilities', () => {
  const page = pageCapabilities.find(p => p.componentKey === 'OPERATIONS_CARD_BIN_LIBRARY');
  assert.deepEqual(page, { componentKey: 'OPERATIONS_CARD_BIN_LIBRARY', displayName: '卡 BIN 库', frontendRoute: '/card-bin-library',
    actions: [{ resourceCode: binLibraryManageButton, displayName: '维护' }] });
});

test('路径：查询版本可选，未指定时查当前生效版本', () => {
  assert.equal(binLibraryPaths.lookup('41111111', null), '/card-admin/v1/bin-library/bins/41111111');
  assert.equal(binLibraryPaths.lookup('411111', 2), '/card-admin/v1/bin-library/bins/411111?versionNo=2');
  assert.equal(binLibraryPaths.activation(4), '/card-admin/v1/bin-library/versions/4/activation');
  assert.equal(binLibraryPaths.ranges(6), '/card-admin/v1/bin-library/versions/6/ranges');
  assert.equal(binLibraryPaths.uploadCompletion('7409912387001'), '/card-admin/v1/bin-library/uploads/7409912387001/completion');
});

test('版本解码：可空字段统一为 null，未知状态或缺字段按无效响应拒绝', () => {
  const seed = decodeVersion({ ...version, versionNo: 1, status: 'RETIRED', fileAddress: undefined, fileName: null, fileContentHash: null, createdBy: null });
  assert.equal(seed.fileAddress, null);
  assert.equal(seed.createdBy, null);
  assert.equal(seed.createdByLabel, null);
  assert.equal(decodeVersion({ ...version, createdByLabel: 'kenny' }).createdByLabel, 'kenny');
  assert.throws(() => decodeVersion({ ...version, createdByLabel: 10001 }));
  for (const broken of [{ ...version, status: 'DELETED' }, { ...version, versionNo: 0 }, { ...version, processedRows: '1' }, { ...version, active: 'false' }]) {
    assert.throws(() => decodeVersion(broken), (error: unknown) => error instanceof ApiError && error.status === 502);
  }
  const page = decodeVersionPage({ items: [version], page: 1, size: 10, total: 1 });
  assert.equal(page.items[0].status, 'VALID');
  assert.throws(() => decodeVersionPage({ items: [version], page: 0, size: 10, total: 1 }));
});

test('错误明细与激活历史解码', () => {
  const errors = decodeImportErrorPage({ items: [{ lineNo: 12002, columnName: 'bin_prefix', errorCode: 'INVALID_FORMAT', errorMessage: '不是 6～8 位数字',
    valueExcerpt: null }, { lineNo: 30, errorCode: 'COLUMN_COUNT_MISMATCH', errorMessage: '列数不符' }], page: 1, size: 10, total: 2 });
  assert.equal(errors.items[1].columnName, null);
  const history = decodeActivationPage({ items: [{ activationType: 'ROLLBACK', versionNo: 2, previousVersionNo: 3, operatorAccountId: '10001',
    activatedAt: '2026-10-22T09:00:00.000000Z' }, { activationType: 'ACTIVATE', versionNo: 1, previousVersionNo: null, operatorAccountId: null,
    activatedAt: '2026-10-01T00:00:00.000000Z' }], page: 1, size: 10, total: 2 });
  assert.equal(history.items[1].previousVersionNo, null);
  assert.equal(history.items[0].operatorLabel, null);
  assert.equal(decodeActivationPage({ items: [{ activationType: 'ACTIVATE', versionNo: 1, previousVersionNo: null, operatorAccountId: '221546852372267008',
    operatorLabel: 'ke***@gmail.com', activatedAt: '2026-10-22T09:00:00Z' }], page: 1, size: 10, total: 1 }).items[0].operatorLabel, 'ke***@gmail.com');
  assert.throws(() => decodeActivationPage({ items: [{ activationType: 'SWITCH', versionNo: 2, activatedAt: 'x' }], page: 1, size: 10, total: 1 }));
});

test('BIN 查询解码：未识别时属性为 null，版本号保留', () => {
  const unknown = decodeLookup({ recognized: false, binLibraryVersionNo: 3, matchedPrefixLength: null, binPrefix: null, cardBrand: null });
  assert.equal(unknown.recognized, false);
  assert.equal(unknown.binLibraryVersionNo, 3);
  assert.equal(unknown.issuerRegion, null);
  assert.throws(() => decodeLookup({ recognized: 'false' }));
  assert.throws(() => decodeLookup({ recognized: true, matchedPrefixLength: 5 }));
});

test('上传解码：PUT 授权地址必须是 http(s)，完成回执的文件地址必须与文件 ID 一致', () => {
  const start = decodeUploadStart({ uploadId: '7409912387001', expiresAt: '2026-10-22T08:15:00Z',
    authorization: { method: 'PUT', url: 'https://storage.local.test/bin', headers: { 'Content-Type': 'text/csv' }, expiresAt: '2026-10-22T08:15:00Z' } });
  assert.equal(start.authorization.headers['Content-Type'], 'text/csv');
  assert.throws(() => decodeUploadStart({ uploadId: '7409912387001', expiresAt: 'x', authorization: { method: 'POST', url: 'https://x' } }));
  assert.throws(() => decodeUploadStart({ uploadId: '7409912387001', expiresAt: 'x', authorization: { method: 'PUT', url: 'javascript:alert(1)' } }));
  const done = decodeUploadCompletion({ uploadId: '7409912387001', fileId: '7409912387650', fileAddress: '/files/v1/7409912387650',
    scanStatus: 'SCANNING', completedAt: '2026-10-22T08:01:00Z' });
  assert.equal(done.fileAddress, '/files/v1/7409912387650');
  assert.throws(() => decodeUploadCompletion({ ...done, fileAddress: '/files/v1/1' }));
});

test('版本动作：VALID 激活、RETIRED 回切，其余没有动作', () => {
  assert.deepEqual(versionAction({ status: 'VALID' }), { kind: 'ACTIVATE', verb: 'Activate' });
  assert.deepEqual(versionAction({ status: 'RETIRED' }), { kind: 'ROLLBACK', verb: 'Roll Back' });
  for (const status of ['VALIDATING', 'INVALID', 'ACTIVE'] as const) assert.equal(versionAction({ status }), null);
});

test('进度：总行数未知为不定进度，已知时向下取整且不超过 100', () => {
  assert.deepEqual(versionProgress({ totalRows: null, processedRows: 0 }), { percent: null, label: t('progress.waiting') });
  assert.deepEqual(versionProgress({ totalRows: 315902, processedRows: 128000 }), { percent: 40, label: 'Written 128,000 / 315,902 rows' });
  assert.equal(versionProgress({ totalRows: 10, processedRows: 12 }).percent, 100);
  assert.equal(rowDelta(312480, 315902), '+3,422');
  assert.equal(rowDelta(312480, 309115), '-3,365');
  assert.equal(rowDelta(1, 1), '±0');
  assert.equal(rowDelta(null, 1), '');
});

test('号段解码：真实响应形状，四个可空字段接受 null 与缺省，必填字段与前缀长度严格校验', () => {
  const page = decodeRangePage({ items: [
    { binPrefix: '41111111', prefixLength: 8, cardBrand: 'VISA', fundingType: 'CREDIT', cardLevel: 'CLASSIC', cardProduct: 'CONSUMER',
      issuerCountry: 'US', issuerRegion: 'NORTH_AMERICA', issuerName: 'TEST BANK' },
    { binPrefix: '621483', prefixLength: 6, cardBrand: 'UNIONPAY', fundingType: null, cardLevel: null, cardProduct: null,
      issuerCountry: 'CN', issuerRegion: 'ASIA_PACIFIC', issuerName: null },
  ], page: 1, size: 50, total: 17 });
  assert.equal(page.total, 17);
  assert.deepEqual(page.items[1], { binPrefix: '621483', prefixLength: 6, cardBrand: 'UNIONPAY', fundingType: null, cardLevel: null, cardProduct: null,
    issuerCountry: 'CN', issuerRegion: 'ASIA_PACIFIC', issuerName: null });
  const range = { binPrefix: '411111', prefixLength: 6, cardBrand: 'VISA', issuerCountry: 'US', issuerRegion: 'NORTH_AMERICA' };
  assert.equal(decodeRange(range).cardLevel, null);
  for (const broken of [{ ...range, prefixLength: 8 }, { ...range, binPrefix: '41111' }, { ...range, binPrefix: '411111111', prefixLength: 9 },
    { ...range, cardBrand: null }, { ...range, issuerCountry: 'usa' }, { ...range, issuerRegion: null }, { ...range, fundingType: 1 }, { ...range, issuerName: false }]) {
    assert.throws(() => decodeRange(broken), (error: unknown) => error instanceof ApiError && error.status === 502);
  }
  assert.throws(() => decodeRangePage({ items: [range], page: 1, size: 50 }));
});

test('BIN 前缀：1～8 位数字可筛选；非数字与超过 8 位（疑似卡号）即时提示且不生成查询', () => {
  assert.equal(binPrefixIssue(''), null);
  assert.equal(binPrefixIssue('4'), null);
  assert.equal(binPrefixIssue(' 41111111 '), null);
  assert.equal(binPrefixIssue('4111a'), t('binInput.digitsOnly'));
  assert.equal(binPrefixIssue('4111 1111'), t('binInput.digitsOnly'));
  assert.match(binPrefixIssue('411111111') ?? '', /never a full card number/);
  assert.match(binPrefixIssue('4111111111111111') ?? '', /never a full card number/);
  assert.equal(rangeQueryFilters({ ...emptyRangeFilters, binPrefix: '4111111111111111' }), null);
  assert.equal(rangeQueryFilters({ ...emptyRangeFilters, binPrefix: '41x' }), null);
  assert.equal(isQueryableBin(' 41111111 '), true);
  assert.equal(isQueryableBin('41111'), false);
  assert.equal(isQueryableBin('411111111'), false);
});

test('号段查询参数：只带有值的条件，分页参数在后', () => {
  assert.equal(pagingQuery(rangeQueryFilters(emptyRangeFilters) ?? {}, { page: 1, size: 50 }).toString(), 'page=1&size=50');
  const filters = { binPrefix: ' 4111 ', cardBrand: 'VISA', fundingType: null, issuerCountry: 'US' };
  assert.deepEqual(rangeQueryFilters(filters), { binPrefix: '4111', cardBrand: 'VISA', issuerCountry: 'US' });
  assert.equal(pagingQuery(rangeQueryFilters(filters) ?? {}, { page: 2, size: 50 }).toString(), 'binPrefix=4111&cardBrand=VISA&issuerCountry=US&page=2&size=50');
  assert.equal(activeFilterCount(filters), 3);
  assert.equal(activeFilterCount({ ...emptyRangeFilters, binPrefix: '  ' }), 0);
});

test('可浏览版本：VALID / ACTIVE / RETIRED', () => {
  assert.deepEqual((['VALIDATING', 'VALID', 'INVALID', 'ACTIVE', 'RETIRED'] as const).filter(status => isBrowsable({ status })), ['VALID', 'ACTIVE', 'RETIRED']);
});

test('操作人：显示名优先；非数字原值原样；数字 ID 截断为末 6 位并保留完整 ID；空为 system', () => {
  assert.deepEqual(operatorDisplay('kenny', '221546852372267008'), { text: 'kenny', full: '221546852372267008' });
  assert.deepEqual(operatorDisplay(null, '221546852372267008'), { text: '…267008', full: '221546852372267008' });
  assert.deepEqual(operatorDisplay(null, 'system'), { text: 'system', full: null });
  assert.deepEqual(operatorDisplay(null, '10001'), { text: '10001', full: null });
  assert.deepEqual(operatorDisplay(null, null), { text: t('operator.system'), full: null });
});

test('CSV 文件检查：扩展名、空文件与 50 MB 上限', () => {
  assert.equal(csvFileIssue({ name: 'bin.CSV', size: 10 }), null);
  assert.equal(csvFileIssue({ name: 'bin.xlsx', size: 10 }), t('csv.onlyCsv'));
  assert.equal(csvFileIssue({ name: 'bin.csv', size: 0 }), t('csv.empty'));
  assert.equal(csvFileIssue({ name: 'bin.csv', size: maxCsvBytes + 1 }), 'The file exceeds the 50 MB limit');
});

test('CSV 预检：去 BOM、兼容 CRLF、表头逐字匹配、引号转义与截断行', () => {
  const preview = previewCsv(`﻿${csvTemplate.replaceAll('\n', '\r\n')}`, true);
  assert.equal(preview.headerValid, true);
  assert.equal(preview.issue, null);
  assert.deepEqual(preview.rows[1], ['510510', 'MASTERCARD', 'DEBIT', '', 'CONSUMER', 'GB', 'EXAMPLE BANK, LTD']);
  assert.equal(previewCsv(`${csvHeader}\n411111,VISA`, false).rows.length, 0);
  assert.equal(previewCsv(`${csvHeader}\n411111,VISA\n`, true).issue, "Line 2 doesn't have 7 columns");
  const wrong = previewCsv('BIN,brand\n411111,VISA\n', true);
  assert.equal(wrong.headerValid, false);
  assert.equal(wrong.issue, t('csv.headerMismatch', { header: csvHeader }));
  assert.equal(previewCsv('a\0b', true).headerValid, false);
  assert.equal(previewCsv('a\0b', true).issue, t('csv.nul'));
  assert.deepEqual(splitCsvLine('"a ""b""",c'), ['a "b"', 'c']);
  assert.equal(splitCsvLine('"open,c'), null);
});
