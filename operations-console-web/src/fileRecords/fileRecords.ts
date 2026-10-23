import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { resolveTimeRange } from '../components/timeRange.ts';
import type { TimeRangeValue } from '../components/timeRange.ts';
import { accessLevels, instanceCodePattern, scanPolicyCodePattern, sourceSystemPattern, storageAdminPath } from '../fileStorage/fileStorage.ts';
import type { AccessLevel } from '../fileStorage/fileStorage.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('file');

/** 文件记录接口（Platform 存储管理合同 /storage-admin/v1/files，经 Operations BFF 同路径转发）。 */
export const fileRecordPath = `${storageAdminPath}/files`;
export const fileRecordDetailPath = (fileId: string) => `${fileRecordPath}/${encodeURIComponent(fileId)}`;
/** 运维下载授权；缺省版本号表示当前版本。 */
export const fileRecordDownloadPath = (fileId: string, versionNo?: number) =>
  `${fileRecordDetailPath(fileId)}/download${versionNo === undefined ? '' : `?versionNo=${versionNo}`}`;
/** 下载按钮资源；没有时版本历史不显示下载，快速预览只显示图标。 */
export const downloadFileRecord = 'OPERATIONS_FILE_RECORD_DOWNLOAD';
export const canDownloadFileRecord = (resources: readonly { resourceType: string; resourceCode: string }[]) =>
  resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === downloadFileRecord);
/** 创建时间范围必填，最长 31 天。 */
export const maxRangeDays = 31;

export const scanStatuses = ['PENDING_SCAN', 'SCANNING', 'AVAILABLE', 'QUARANTINED', 'REJECTED'] as const;
export const objectStatuses = ['ACTIVE', 'UNAVAILABLE', 'RETIRED'] as const;
export const versionStatuses = ['CANDIDATE', 'CURRENT', 'SUPERSEDED', 'REJECTED'] as const;
export const uploadKinds = ['ORIGINAL', 'REPLACEMENT'] as const;
export type ScanStatus = typeof scanStatuses[number];
export type ObjectStatus = typeof objectStatuses[number];
export type VersionStatus = typeof versionStatuses[number];
export type UploadKind = typeof uploadKinds[number];

/**
 * 显示名在 `file` 命名空间（`enum.scanStatus`、`enum.objectStatus`、`enum.versionStatus`、`enum.uploadKind`、`enum.uploadStatus`），
 * 由下面的函数在调用时取；未知取值原样展示。
 */
export const scanStatusLabel = (value: string) => enumLabel(t, 'enum.scanStatus', value);
export const objectStatusLabel = (value: string) => enumLabel(t, 'enum.objectStatus', value);
export const versionStatusLabel = (value: string) => enumLabel(t, 'enum.versionStatus', value);
export const uploadKindLabel = (value: string) => enumLabel(t, 'enum.uploadKind', value);
export const uploadStatusLabel = (value: string) => enumLabel(t, 'enum.uploadStatus', value);
export const scanStatusColors: Readonly<Record<ScanStatus, string>> = {
  PENDING_SCAN: 'gold', SCANNING: 'gold', AVAILABLE: 'green', QUARANTINED: 'red', REJECTED: 'red',
};
export const versionStatusColors: Readonly<Record<VersionStatus, string>> = {
  CANDIDATE: 'gold', CURRENT: 'green', SUPERSEDED: 'default', REJECTED: 'red',
};

export interface FileRecordSummary {
  fileId: string; fileAddress: string; displayName: string; sourceSystem: string; reasonCode: string; accessLevel: AccessLevel;
  currentVersionNo: number; pendingVersionNo: number | null; scanStatus: ScanStatus; objectStatus: ObjectStatus; mimeType: string;
  sizeBytes: string; createTime: string; updateTime: string;
}
export interface FileVersionRecord {
  versionNo: number; versionStatus: VersionStatus; scanStatus: ScanStatus; displayName: string; mimeType: string; sizeBytes: string;
  contentHash: string; uploadId: string | null; uploaderAccountId: string | null; downloadable: boolean; availableTime: string | null; createTime: string;
}
export interface FileScanRecord {
  versionNo: number; scanPolicyCode: string; revisionNo: number; scannerCode: string; scanType: string; resultCode: string; attemptNo: number;
  scannedTime: string | null;
}
export interface FileUploadRecord {
  uploadId: string; kind: UploadKind; replacementNo: number; status: string; displayName: string; expectedMime: string; expectedSize: string;
  uploaderAccountId: string | null; resultVersionNo: number | null; expiresTime: string; createTime: string; updateTime: string;
}
export interface FileRecordDetail extends FileRecordSummary {
  contentHash: string; uploaderAccountId: string | null; instanceCode: string; bucketName: string; objectRef: string; publicUrl: string | null;
  versionedUrl: string | null; versions: FileVersionRecord[]; scanResults: FileScanRecord[]; uploads: FileUploadRecord[];
}
export interface FileDownloadGrant { url: string; expiresAt: string; disposition: string }

const summaryFields = ['fileId', 'fileAddress', 'displayName', 'sourceSystem', 'reasonCode', 'accessLevel', 'currentVersionNo', 'pendingVersionNo',
  'scanStatus', 'objectStatus', 'mimeType', 'sizeBytes', 'createTime', 'updateTime'] as const;
const detailFields = [...summaryFields, 'contentHash', 'uploaderAccountId', 'instanceCode', 'bucketName', 'objectRef', 'publicUrl', 'versionedUrl',
  'versions', 'scanResults', 'uploads'] as const;
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const exactFields = (v: Record<string, unknown>, fields: readonly string[]) =>
  Object.keys(v).length === fields.length && fields.every(field => field in v);
const text = (v: unknown): v is string => typeof v === 'string' && v.trim().length > 0;
const decimalId = (v: unknown) => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v);
const optionalId = (v: unknown) => v === null || decimalId(v);
const byteCount = (v: unknown) => typeof v === 'string' && /^(0|[1-9][0-9]{0,18})$/.test(v);
const integer = (v: unknown, min: number) => Number.isSafeInteger(v) && Number(v) >= min;
const optionalInteger = (v: unknown) => v === null || integer(v, 1);
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const optionalInstant = (v: unknown) => v === null || instant(v);
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const sha256 = (v: unknown) => typeof v === 'string' && /^[0-9a-f]{64}$/i.test(v);
const sourceSystem = (v: unknown) => typeof v === 'string' && sourceSystemPattern.test(v);
const httpsUrl = (v: unknown) => {
  if (typeof v !== 'string') return false;
  try { const url = new URL(v); return url.protocol === 'https:' && !url.username && !url.password && !url.hash; } catch { return false; }
};
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

function validSummary(v: Record<string, unknown>): boolean {
  return decimalId(v.fileId) && text(v.fileAddress) && text(v.displayName) && sourceSystem(v.sourceSystem) && text(v.reasonCode)
    && oneOf(accessLevels)(v.accessLevel) && integer(v.currentVersionNo, 1) && optionalInteger(v.pendingVersionNo)
    && (v.pendingVersionNo === null || Number(v.pendingVersionNo) > Number(v.currentVersionNo))
    && oneOf(scanStatuses)(v.scanStatus) && oneOf(objectStatuses)(v.objectStatus) && text(v.mimeType) && byteCount(v.sizeBytes)
    && instant(v.createTime) && instant(v.updateTime);
}

/** 列表行严格解码：字段集合固定，ID 与字节数为十进制字符串。 */
export function decodeFileRecordSummary(value: unknown): FileRecordSummary {
  require(object(value) && exactFields(value, summaryFields) && validSummary(value));
  return value as unknown as FileRecordSummary;
}

/** 分页响应：逐行严格解码，文件 ID 不重复。 */
export function decodeFileRecordPage(value: unknown): PagingResponseBody<FileRecordSummary> {
  require(object(value) && exactFields(value, ['items', 'page', 'size', 'total']) && Array.isArray(value.items));
  const v = value as unknown as PagingResponseBody<unknown>;
  const items = v.items.map(decodeFileRecordSummary);
  require(new Set(items.map(item => item.fileId)).size === items.length);
  return { ...v, items };
}

function decodeVersion(value: unknown): FileVersionRecord {
  require(object(value) && exactFields(value, ['versionNo', 'versionStatus', 'scanStatus', 'displayName', 'mimeType', 'sizeBytes', 'contentHash',
    'uploadId', 'uploaderAccountId', 'downloadable', 'availableTime', 'createTime']));
  const v = value as unknown as FileVersionRecord;
  require(integer(v.versionNo, 1) && oneOf(versionStatuses)(v.versionStatus) && oneOf(scanStatuses)(v.scanStatus) && text(v.displayName)
    && text(v.mimeType) && byteCount(v.sizeBytes) && sha256(v.contentHash) && optionalId(v.uploadId) && optionalId(v.uploaderAccountId)
    && typeof v.downloadable === 'boolean' && optionalInstant(v.availableTime) && instant(v.createTime)
    && (!v.downloadable || ((v.versionStatus === 'CURRENT' || v.versionStatus === 'SUPERSEDED') && v.scanStatus === 'AVAILABLE')));
  return v;
}

function decodeScan(value: unknown): FileScanRecord {
  require(object(value) && exactFields(value, ['versionNo', 'scanPolicyCode', 'revisionNo', 'scannerCode', 'scanType', 'resultCode', 'attemptNo',
    'scannedTime']));
  const v = value as unknown as FileScanRecord;
  require(integer(v.versionNo, 1) && scanPolicyCodePattern.test(String(v.scanPolicyCode)) && integer(v.revisionNo, 1) && text(v.scannerCode)
    && text(v.scanType) && text(v.resultCode) && integer(v.attemptNo, 1) && optionalInstant(v.scannedTime));
  return v;
}

function decodeUpload(value: unknown): FileUploadRecord {
  require(object(value) && exactFields(value, ['uploadId', 'kind', 'replacementNo', 'status', 'displayName', 'expectedMime', 'expectedSize',
    'uploaderAccountId', 'resultVersionNo', 'expiresTime', 'createTime', 'updateTime']));
  const v = value as unknown as FileUploadRecord;
  require(decimalId(v.uploadId) && oneOf(uploadKinds)(v.kind) && integer(v.replacementNo, 0) && (v.kind === 'ORIGINAL') === (v.replacementNo === 0)
    && text(v.status) && text(v.displayName) && text(v.expectedMime) && byteCount(v.expectedSize) && optionalId(v.uploaderAccountId)
    && optionalInteger(v.resultVersionNo) && instant(v.expiresTime) && instant(v.createTime) && instant(v.updateTime));
  return v;
}

/**
 * 文件详情严格解码
 * <li>必须是请求的文件；版本号不重复且倒序，恰有一个当前版本且等于 currentVersionNo。</li>
 * <li>公开地址只在公开文件上出现，且为 HTTPS；私有文件两者都为空。</li>
 */
export function decodeFileRecordDetail(value: unknown, fileId: string): FileRecordDetail {
  require(object(value) && exactFields(value, detailFields) && value.fileId === fileId && validSummary(value)
    && Array.isArray(value.versions) && Array.isArray(value.scanResults) && Array.isArray(value.uploads));
  const v = value as unknown as FileRecordDetail;
  require(sha256(v.contentHash) && optionalId(v.uploaderAccountId) && instanceCodePattern.test(String(v.instanceCode)) && text(v.bucketName)
    && text(v.objectRef) && (v.publicUrl === null || httpsUrl(v.publicUrl)) && (v.versionedUrl === null || httpsUrl(v.versionedUrl))
    && (v.accessLevel === 'PUBLIC' || (v.publicUrl === null && v.versionedUrl === null)));
  const versions = v.versions.map(decodeVersion);
  require(versions.every((version, index) => index === 0 || versions[index - 1].versionNo > version.versionNo)
    && versions.filter(version => version.versionStatus === 'CURRENT').length <= 1
    && versions.every(version => version.versionStatus !== 'CURRENT' || version.versionNo === v.currentVersionNo));
  const uploads = v.uploads.map(decodeUpload);
  require(new Set(uploads.map(upload => upload.uploadId)).size === uploads.length);
  return { ...v, versions, scanResults: v.scanResults.map(decodeScan), uploads };
}

/** 下载授权：只接受 HTTPS 地址与未来的到期时间；地址不保存、不展示。 */
export function decodeDownloadGrant(value: unknown, now = Date.now()): FileDownloadGrant {
  require(object(value) && exactFields(value, ['url', 'expiresAt', 'disposition']) && httpsUrl(value.url) && instant(value.expiresAt)
    && text(value.disposition) && String(value.url).length <= 8192);
  const v = value as unknown as FileDownloadGrant;
  if (!(Date.parse(v.expiresAt) > now)) throw new ApiError(502, 'INVALID_RESPONSE');
  return v;
}

/** 快捷筛选：每项只设置一个条件，其余快捷条件清空；不显示计数（没有统计接口）。按钮文字为 `records.quick.<key>`。 */
export const quickFilters = [
  { key: 'all', filters: {} },
  { key: 'scanning', filters: { scanStatus: 'SCANNING' } },
  { key: 'rejected', filters: { scanStatus: 'REJECTED' } },
  { key: 'replacing', filters: { hasPendingReplacement: 'true' } },
  { key: 'public', filters: { accessLevel: 'PUBLIC' } },
] as const;
const quickKeys = ['scanStatus', 'accessLevel', 'hasPendingReplacement'] as const;

/** 当前条件对应的快捷筛选；不是单一快捷条件时返回 null。 */
export function activeQuickFilter(filters: Readonly<Record<string, string>>): string | null {
  const set = quickKeys.filter(key => filters[key]);
  if (!set.length) return 'all';
  const match = quickFilters.find(quick => {
    const entries = Object.entries(quick.filters);
    return entries.length === set.length && entries.every(([key, value]) => filters[key] === value);
  });
  return match?.key ?? null;
}

/** 应用快捷筛选：保留时间范围与其他条件，替换快捷条件。 */
export function applyQuickFilter(filters: Readonly<Record<string, string>>, key: string): Record<string, string> {
  const quick = quickFilters.find(item => item.key === key) ?? quickFilters[0];
  const rest = Object.fromEntries(Object.entries(filters).filter(([name]) => !(quickKeys as readonly string[]).includes(name)));
  return { ...rest, ...quick.filters };
}

export interface FileRecordFilterValues {
  timeRange?: TimeRangeValue; fileId?: string; sourceSystem?: string; reasonCode?: string; accessLevel?: AccessLevel; scanStatus?: ScanStatus;
  hasPendingReplacement?: boolean;
}
export const defaultRange = (): TimeRangeValue => ({ preset: 'LAST_7_DAYS' });

/**
 * 列表筛选 → Query：时间范围必填（默认最近 7 天），转 UTC，结束不含，跨度不超过 31 天
 * <li>文件 ID 为十进制数字；来源系统按内部服务名、原因代码按大写代码校验；空的可选条件不发送。</li>
 */
export function fileRecordFilters(values: FileRecordFilterValues, now = new Date()): Record<string, string> {
  const range = resolveTimeRange(values.timeRange ?? defaultRange(), now);
  if (!range.start || !range.end) throw new Error(t('filters.incompleteRange'));
  const from = new Date(range.start); const to = new Date(range.end);
  if (!Number.isFinite(from.getTime()) || !Number.isFinite(to.getTime())) throw new Error(t('filters.invalidTime'));
  if (from >= to) throw new Error(t('filters.endBeforeStart'));
  if (to.getTime() - from.getTime() > maxRangeDays * 86_400_000) throw new Error(t('filters.rangeTooLong', { days: maxRangeDays }));
  const filters: Record<string, string> = { createdFrom: from.toISOString(), createdTo: to.toISOString() };
  const fileId = values.fileId?.trim(); const source = values.sourceSystem?.trim(); const reasonCode = values.reasonCode?.trim();
  if (fileId) { if (!decimalId(fileId)) throw new Error(t('filters.fileIdInvalid')); filters.fileId = fileId; }
  if (source) { if (!sourceSystem(source)) throw new Error(t('filters.sourceSystemInvalid')); filters.sourceSystem = source; }
  if (reasonCode) { if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(reasonCode)) throw new Error(t('filters.reasonCodeInvalid')); filters.reasonCode = reasonCode; }
  if (values.accessLevel) filters.accessLevel = values.accessLevel;
  if (values.scanStatus) filters.scanStatus = values.scanStatus;
  if (values.hasPendingReplacement) filters.hasPendingReplacement = 'true';
  return filters;
}

const filterKeys = ['createdFrom', 'createdTo', 'fileId', 'sourceSystem', 'reasonCode', 'accessLevel', 'scanStatus', 'hasPendingReplacement'] as const;

/** 从 URL 读回筛选：缺少或不合法的时间范围回到默认最近 7 天，其余只保留已知参数。 */
export function filtersFromQuery(query: URLSearchParams, now = new Date()): Record<string, string> {
  const known = Object.fromEntries(filterKeys.flatMap(key => query.get(key) ? [[key, query.get(key)!]] : []));
  const from = Date.parse(known.createdFrom ?? ''); const to = Date.parse(known.createdTo ?? '');
  if (Number.isFinite(from) && Number.isFinite(to) && from < to && to - from <= maxRangeDays * 86_400_000) return known;
  return { ...known, ...fileRecordFilters({}, now) };
}

/** 回填表单：URL 中的 UTC 时间按本地时间显示为自定义范围。 */
export function formValuesFromFilters(filters: Readonly<Record<string, string>>): FileRecordFilterValues {
  const local = (iso: string) => {
    const d = new Date(iso); const two = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())}T${two(d.getHours())}:${two(d.getMinutes())}:${two(d.getSeconds())}`;
  };
  return {
    timeRange: { preset: 'CUSTOM', start: local(filters.createdFrom), end: local(filters.createdTo) },
    fileId: filters.fileId, sourceSystem: filters.sourceSystem, reasonCode: filters.reasonCode,
    accessLevel: filters.accessLevel as AccessLevel | undefined, scanStatus: filters.scanStatus as ScanStatus | undefined,
    hasPendingReplacement: filters.hasPendingReplacement === 'true',
  };
}

/** 版本行可下载：版本可下载且有下载按钮权限。 */
export const versionDownloadable = (version: FileVersionRecord, canDownload: boolean) => canDownload && version.downloadable;

/** 当前版本行。 */
export const currentVersion = (detail: FileRecordDetail) => detail.versions.find(version => version.versionNo === detail.currentVersionNo) ?? null;

/** 截短显示 ID 或摘要：保留首尾。 */
export const shortText = (value: string, head = 4, tail = 4) => value.length > head + tail + 1 ? `${value.slice(0, head)}…${value.slice(-tail)}` : value;

/** 快速预览：只对浏览器可直接显示的图片加载预览。 */
export const previewableImage = (mimeType: string) => ['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(mimeType);
