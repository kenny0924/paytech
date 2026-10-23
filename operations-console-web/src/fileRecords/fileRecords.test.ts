import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import { auditActions, auditResourceTypes } from '../pages/auditView.ts';
import {
  activeQuickFilter, applyQuickFilter, canDownloadFileRecord, currentVersion, decodeDownloadGrant, decodeFileRecordDetail, decodeFileRecordPage,
  downloadFileRecord, fileRecordDetailPath, fileRecordDownloadPath, fileRecordFilters, fileRecordPath, filtersFromQuery, formValuesFromFilters,
  previewableImage, quickFilters, versionDownloadable,
} from './fileRecords.ts';
import type { FileRecordDetail, FileRecordSummary, FileVersionRecord } from './fileRecords.ts';

const time = '2026-10-22T12:31:00.123456Z';
const hash = 'a'.repeat(64);
const summary: FileRecordSummary = { fileId: '239061120044503040', fileAddress: '/files/v1/239061120044503040', displayName: 'merchant-logo.png',
  sourceSystem: 'client-console-bff', reasonCode: 'MERCHANT_LOGO', accessLevel: 'PUBLIC', currentVersionNo: 3, pendingVersionNo: 4,
  scanStatus: 'AVAILABLE', objectStatus: 'ACTIVE', mimeType: 'image/png', sizeBytes: '86016', createTime: time, updateTime: time };
const version = (versionNo: number, versionStatus: FileVersionRecord['versionStatus'], extra: Partial<FileVersionRecord> = {}): FileVersionRecord => ({
  versionNo, versionStatus, scanStatus: 'AVAILABLE', displayName: 'merchant-logo.png', mimeType: 'image/png', sizeBytes: '86016', contentHash: hash,
  uploadId: String(239061120044503040n + BigInt(versionNo)), uploaderAccountId: '238877120983621632', downloadable: versionStatus !== 'CANDIDATE',
  availableTime: versionStatus === 'CANDIDATE' ? null : time, createTime: time, ...extra });
const detail: FileRecordDetail = { ...summary, contentHash: hash, uploaderAccountId: '238877120983621632', instanceCode: 'S3_TEST',
  bucketName: 'mor-test-public-assets', objectRef: '7d1e9c42-5b1a-4f0e-9a3b-2c8e61f0d5a7',
  publicUrl: 'https://cdn-test.example.com/7d1e9c42-5b1a-4f0e-9a3b-2c8e61f0d5a7',
  versionedUrl: 'https://cdn-test.example.com/7d1e9c42-5b1a-4f0e-9a3b-2c8e61f0d5a7?v=3',
  versions: [version(4, 'CANDIDATE', { scanStatus: 'SCANNING' }), version(3, 'CURRENT'), version(2, 'SUPERSEDED'), version(1, 'SUPERSEDED')],
  scanResults: [{ versionNo: 3, scanPolicyCode: 'MERCHANT_LOGO_PUBLIC', revisionNo: 1, scannerCode: 'PLATFORM_FORMAT', scanType: 'FORMAT',
    resultCode: 'FORMAT_VALID', attemptNo: 1, scannedTime: time }],
  uploads: [{ uploadId: '239061120044503044', kind: 'REPLACEMENT', replacementNo: 3, status: 'COMPLETED', displayName: 'merchant-logo.png',
    expectedMime: 'image/png', expectedSize: '90112', uploaderAccountId: null, resultVersionNo: 4, expiresTime: time, createTime: time, updateTime: time },
  { uploadId: '239061120044503041', kind: 'ORIGINAL', replacementNo: 0, status: 'COMPLETED', displayName: 'merchant-logo.png',
    expectedMime: 'image/png', expectedSize: '73728', uploaderAccountId: '238877120983621632', resultVersionNo: 1, expiresTime: time, createTime: time, updateTime: time }] };

test('页面注册：文件记录挂在 /file-records，下载按钮与 Platform 能力清单一致；接口路径对应 BFF 合同', () => {
  const page = pageCapabilities.find(p => p.componentKey === 'OPERATIONS_FILE_RECORDS');
  assert.equal(page?.frontendRoute, '/file-records');
  assert.deepEqual(page?.actions.map(action => action.resourceCode), [downloadFileRecord]);
  assert.equal(fileRecordPath, '/storage-admin/v1/files');
  assert.equal(fileRecordDetailPath('239061120044503040'), '/storage-admin/v1/files/239061120044503040');
  assert.equal(fileRecordDownloadPath('1'), '/storage-admin/v1/files/1/download');
  assert.equal(fileRecordDownloadPath('1', 2), '/storage-admin/v1/files/1/download?versionNo=2');
});

test('权限按钮：只有下载按钮资源才可下载；没有时版本行与快速预览都不下载', () => {
  assert.equal(canDownloadFileRecord([{ resourceType: 'BUTTON', resourceCode: downloadFileRecord }]), true);
  assert.equal(canDownloadFileRecord([{ resourceType: 'MENU', resourceCode: 'OPERATIONS_FILE_RECORDS' }]), false);
  assert.equal(canDownloadFileRecord([{ resourceType: 'MENU', resourceCode: downloadFileRecord }]), false);
  const [candidate, current, superseded] = detail.versions;
  assert.equal(versionDownloadable(current, true), true); assert.equal(versionDownloadable(superseded, true), true);
  assert.equal(versionDownloadable(candidate, true), false, '候选版本检查中不能下载');
  assert.equal(versionDownloadable(current, false), false, '没有下载按钮');
  assert.equal(previewableImage('image/png'), true); assert.equal(previewableImage('application/pdf'), false);
});

test('列表解码：字段集合固定，ID 与字节数为十进制字符串，替换版本须大于当前版本', () => {
  const page = decodeFileRecordPage({ items: [summary, { ...summary, fileId: '239050710426140672', accessLevel: 'PRIVATE', pendingVersionNo: null }],
    page: 1, size: 20, total: 2 });
  assert.deepEqual(page.items.map(item => item.fileId), ['239061120044503040', '239050710426140672']);
  for (const invalid of [{ ...summary, fileId: 239061120044503040 }, { ...summary, sizeBytes: 86016 }, { ...summary, scanStatus: 'CLEAN' },
    { ...summary, accessLevel: 'INTERNAL' }, { ...summary, pendingVersionNo: 3 }, { ...summary, currentVersionNo: 0 }, { ...summary, extra: 1 },
    { ...summary, sourceSystem: 'Client' }, { ...summary, objectStatus: 'DELETED' }]) {
    assert.throws(() => decodeFileRecordPage({ items: [invalid], page: 1, size: 20, total: 1 }), ApiError, JSON.stringify(invalid));
  }
  assert.throws(() => decodeFileRecordPage({ items: [summary, summary], page: 1, size: 20, total: 2 }), ApiError);
});

test('详情解码：必须是请求的文件；版本倒序且当前版本唯一；私有文件不得带公开地址；上传类型与替换序号一致', () => {
  const decoded = decodeFileRecordDetail(detail, summary.fileId);
  assert.equal(currentVersion(decoded)?.versionNo, 3);
  assert.equal(decodeFileRecordDetail({ ...detail, accessLevel: 'PRIVATE', publicUrl: null, versionedUrl: null }, summary.fileId).publicUrl, null);
  for (const invalid of [
    { ...detail, accessLevel: 'PRIVATE' }, { ...detail, publicUrl: 'http://cdn.test/x' }, { ...detail, contentHash: 'short' },
    { ...detail, versions: [version(2, 'SUPERSEDED'), version(3, 'CURRENT')] }, { ...detail, versions: [version(3, 'CURRENT'), version(2, 'CURRENT')] },
    { ...detail, versions: [version(2, 'CURRENT')] }, { ...detail, versions: [version(4, 'CANDIDATE', { downloadable: true, scanStatus: 'SCANNING' })] },
    { ...detail, uploads: [{ ...detail.uploads[1], replacementNo: 1 }] }, { ...detail, uploads: [detail.uploads[0], detail.uploads[0]] },
    { ...detail, scanResults: [{ ...detail.scanResults[0], attemptNo: 0 }] }, { ...detail, credentialToken: 'x' },
  ]) {
    assert.throws(() => decodeFileRecordDetail(invalid, summary.fileId), ApiError, JSON.stringify(invalid).slice(0, 80));
  }
  assert.throws(() => decodeFileRecordDetail(detail, '1'), ApiError);
});

test('下载授权：只接受 HTTPS 地址与未来的到期时间', () => {
  const now = Date.parse('2026-10-22T12:00:00Z');
  const grant = { url: 'https://s3.test/b/o?X-Amz-Signature=1', expiresAt: '2026-10-22T12:01:00Z', disposition: 'ATTACHMENT' };
  assert.equal(decodeDownloadGrant(grant, now).disposition, 'ATTACHMENT');
  assert.throws(() => decodeDownloadGrant({ ...grant, url: 'http://s3.test/o' }, now), ApiError);
  assert.throws(() => decodeDownloadGrant({ ...grant, expiresAt: '2026-10-22T11:59:00Z' }, now), ApiError);
  assert.throws(() => decodeDownloadGrant({ ...grant, extra: 1 }, now), ApiError);
});

test('筛选参数：时间范围必填、默认近 7 天、最长 31 天、转 UTC；空条件不发送；文件 ID 与来源格式预检', () => {
  const now = new Date(2026, 9, 22, 21, 0, 0);
  const defaults = fileRecordFilters({}, now);
  assert.deepEqual(Object.keys(defaults), ['createdFrom', 'createdTo']);
  assert.equal(Date.parse(defaults.createdTo) - Date.parse(defaults.createdFrom), 7 * 86_400_000);
  const filters = fileRecordFilters({ timeRange: { preset: 'CUSTOM', start: '2026-10-01T00:00:00', end: '2026-10-22T00:00:00' },
    fileId: ' 239061120044503040 ', sourceSystem: ' client-console-bff ', reasonCode: ' MERCHANT_LOGO ', accessLevel: 'PUBLIC', scanStatus: 'AVAILABLE',
    hasPendingReplacement: true }, now);
  assert.deepEqual({ ...filters, createdFrom: undefined, createdTo: undefined }, { createdFrom: undefined, createdTo: undefined,
    fileId: '239061120044503040', sourceSystem: 'client-console-bff', reasonCode: 'MERCHANT_LOGO', accessLevel: 'PUBLIC', scanStatus: 'AVAILABLE',
    hasPendingReplacement: 'true' });
  assert.equal(filters.createdFrom, new Date('2026-10-01T00:00:00').toISOString());
  assert.throws(() => fileRecordFilters({ timeRange: { preset: 'CUSTOM', start: '2026-09-01T00:00:00', end: '2026-10-22T00:00:00' } }, now), /31 天/);
  assert.throws(() => fileRecordFilters({ timeRange: { preset: 'CUSTOM', start: '2026-10-22T00:00:00', end: '2026-10-01T00:00:00' } }, now), /晚于/);
  assert.throws(() => fileRecordFilters({ timeRange: { preset: 'CUSTOM' } }, now), /完整/);
  assert.throws(() => fileRecordFilters({ fileId: 'abc' }, now), /数字/);
  assert.throws(() => fileRecordFilters({ sourceSystem: 'Client Console' }, now), /服务名/);
  assert.throws(() => fileRecordFilters({ reasonCode: 'merchant logo' }, now), /大写/);
  assert.equal('hasPendingReplacement' in fileRecordFilters({ hasPendingReplacement: false }, now), false);
});

test('URL 筛选：合法范围原样保留，缺失或超过 31 天回到默认；回填表单为本地自定义范围', () => {
  const now = new Date(2026, 9, 22, 21, 0, 0);
  const kept = filtersFromQuery(new URLSearchParams({ createdFrom: '2026-10-01T00:00:00.000Z', createdTo: '2026-10-08T00:00:00.000Z',
    scanStatus: 'REJECTED', page: '2', unknown: 'x' }), now);
  assert.deepEqual(kept, { createdFrom: '2026-10-01T00:00:00.000Z', createdTo: '2026-10-08T00:00:00.000Z', scanStatus: 'REJECTED' });
  const reset = filtersFromQuery(new URLSearchParams({ createdFrom: '2026-01-01T00:00:00Z', createdTo: '2026-10-08T00:00:00Z', accessLevel: 'PUBLIC' }), now);
  assert.equal(reset.accessLevel, 'PUBLIC'); assert.equal(Date.parse(reset.createdTo) - Date.parse(reset.createdFrom), 7 * 86_400_000);
  const values = formValuesFromFilters(kept);
  assert.equal(values.timeRange?.preset, 'CUSTOM'); assert.equal(new Date(values.timeRange!.start!).toISOString(), kept.createdFrom);
  assert.equal(values.scanStatus, 'REJECTED'); assert.equal(values.hasPendingReplacement, false);
});

test('快捷筛选：每项只设一个条件并保留时间范围与其他条件；不显示计数', () => {
  const base = { createdFrom: 'a', createdTo: 'b', sourceSystem: 'card-service' };
  assert.deepEqual(quickFilters.map(item => item.label), ['全部', '检查中', '检查未通过', '有替换进行中', '公开文件']);
  assert.equal(activeQuickFilter(base), 'all');
  const rejected = applyQuickFilter({ ...base, accessLevel: 'PUBLIC' }, 'rejected');
  assert.deepEqual(rejected, { ...base, scanStatus: 'REJECTED' });
  assert.equal(activeQuickFilter(rejected), 'rejected');
  assert.equal(activeQuickFilter(applyQuickFilter(base, 'replacing')), 'replacing');
  assert.equal(activeQuickFilter({ ...base, scanStatus: 'AVAILABLE' }), null, '非快捷条件不高亮');
  assert.equal(activeQuickFilter({ ...base, scanStatus: 'REJECTED', accessLevel: 'PUBLIC' }), null);
  assert.deepEqual(applyQuickFilter(rejected, 'all'), base);
});

test('审计中文名：补齐存储管理、检查策略、文件记录与下载授权的动作和对象类型', () => {
  for (const action of ['OPERATIONS_FILE_STORAGE_BUCKET_UPDATE', 'OPERATIONS_FILE_SCAN_POLICIES', 'OPERATIONS_FILE_SCAN_POLICY_CREATE',
    'OPERATIONS_FILE_SCAN_POLICY_REVISION_CREATE', 'OPERATIONS_FILE_SCAN_POLICY_REVISION_UPDATE', 'OPERATIONS_FILE_SCAN_POLICY_REVISION_DISCARD',
    'OPERATIONS_FILE_SCAN_POLICY_ACTIVATE', 'OPERATIONS_FILE_SCAN_POLICY_RETIRE', 'OPERATIONS_FILE_RECORDS', 'FILE_DOWNLOAD_AUTHORIZE']) {
    assert.ok(auditActions[action], action);
  }
  assert.equal(auditResourceTypes.FILE_SCAN_POLICY, '检查策略'); assert.equal(auditResourceTypes.FILE, '文件');
});
