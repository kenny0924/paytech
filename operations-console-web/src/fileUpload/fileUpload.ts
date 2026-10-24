import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { scanStatuses } from '../fileRecords/fileRecords.ts';
import type { ScanStatus } from '../fileRecords/fileRecords.ts';
import { accessLevels, instanceCodePattern, scanPolicyCodePattern, sourceSystemPattern } from '../fileStorage/fileStorage.ts';
import type { AccessLevel } from '../fileStorage/fileStorage.ts';
import { byteSizeLabel } from '../fileStorage/scanPolicy.ts';
import { translator } from '../i18n/index.ts';

const t = translator('file');

// ---------- 路径、页面与按钮（块 136） ----------

const base = '/file-upload/v1';

/** Operations BFF 文件上传路径（经 Gateway /api/operations 前缀）。 */
export const fileUploadPaths = {
  constraints: `${base}/constraints`,
  files: `${base}/files`,
  file: (fileId: string) => `${base}/files/${encodeURIComponent(fileId)}`,
  uploads: `${base}/uploads`,
  uploadCompletion: (uploadId: string) => `${base}/uploads/${encodeURIComponent(uploadId)}/completion`,
} as const;

/** 上传按钮：没有时页面只读（只有限制提示与最近上传）。 */
export const uploadFileButton = 'OPERATIONS_FILE_UPLOAD_CREATE';
type Resource = { resourceType: string; resourceCode: string; componentKey?: string | null };
export const canUploadFile = (resources: readonly Resource[]) =>
  resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === uploadFileButton);
/** 有文件记录页面时，最近上传的每行可跳到 /file-records?file=&lt;id&gt;。 */
export const canOpenFileRecords = (resources: readonly Resource[]) =>
  resources.some(resource => resource.resourceType === 'MENU' && resource.componentKey === 'OPERATIONS_FILE_RECORDS');
/** 有存储管理页面时，未配置提示可跳到 /file-storage；没有时只提示联系管理员。 */
export const canOpenFileStorage = (resources: readonly Resource[]) =>
  resources.some(resource => resource.resourceType === 'MENU' && resource.componentKey === 'OPERATIONS_FILE_STORAGE');
export const fileRecordHref = (fileId: string) => `/file-records?${new URLSearchParams({ file: fileId })}`;

// ---------- 模型 ----------

export interface UploadConstraints {
  configured: boolean; accessLevel: AccessLevel; routeSourceSystem: string | null; instanceCode: string | null; bucketName: string | null;
  publicBaseUrl: string | null; scanPolicyCode: string | null; scanPolicyRevisionNo: number | null; allowedMimeTypes: string[];
  maxSizeBytes: string | null; maxUploadSeconds: number | null;
}

export interface UploadedFile {
  fileId: string; fileAddress: string; displayName: string; mimeType: string; sizeBytes: string; scanStatus: ScanStatus; versionNo: number;
  uploaderAccountId: string | null; uploadedByMe: boolean; createTime: string; publicUrl: string | null; versionedUrl: string | null;
}

// ---------- 解码（失败只给通用错误，不回显响应内容） ----------

const constraintFields = ['configured', 'accessLevel', 'routeSourceSystem', 'instanceCode', 'bucketName', 'publicBaseUrl', 'scanPolicyCode',
  'scanPolicyRevisionNo', 'allowedMimeTypes', 'maxSizeBytes', 'maxUploadSeconds'] as const;
const fileFields = ['fileId', 'fileAddress', 'displayName', 'mimeType', 'sizeBytes', 'scanStatus', 'versionNo', 'uploaderAccountId',
  'uploadedByMe', 'createTime', 'publicUrl', 'versionedUrl'] as const;
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const exactFields = (v: Record<string, unknown>, fields: readonly string[]) =>
  Object.keys(v).length === fields.length && fields.every(field => field in v);
const onlyFields = (v: Record<string, unknown>, fields: readonly string[]) => Object.keys(v).every(key => fields.includes(key));
const text = (v: unknown): v is string => typeof v === 'string' && v.trim().length > 0;
const decimalId = (v: unknown) => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v);
const byteCount = (v: unknown) => typeof v === 'string' && /^(0|[1-9][0-9]{0,18})$/.test(v);
const integer = (v: unknown, min: number) => Number.isSafeInteger(v) && Number(v) >= min;
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const mimeType = (v: unknown) => typeof v === 'string' && /^[a-z0-9][a-z0-9!#$&^_.+-]*\/[a-z0-9][a-z0-9!#$&^_.+-]*$/i.test(v);
const httpsUrl = (v: unknown) => {
  if (typeof v !== 'string') return false;
  try { const url = new URL(v); return url.protocol === 'https:' && !url.username && !url.password && !url.hash; } catch { return false; }
};
const absent = (v: unknown) => v === null || v === undefined;
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/**
 * 限制提示严格解码
 * <li>configured=false 时只保证 configured 与 accessLevel，其余字段缺省或为空，统一为 null / 空列表。</li>
 * <li>configured=true 时目标存储、策略、允许类型（非空、不重复）、大小上限与上传期限都必须有值；公开地址可空。</li>
 */
export function decodeUploadConstraints(value: unknown): UploadConstraints {
  require(object(value) && onlyFields(value, constraintFields) && typeof value.configured === 'boolean'
    && (accessLevels as readonly unknown[]).includes(value.accessLevel));
  const v = value as Record<string, unknown>;
  if (!v.configured) {
    return { configured: false, accessLevel: v.accessLevel as AccessLevel, routeSourceSystem: null, instanceCode: null, bucketName: null,
      publicBaseUrl: null, scanPolicyCode: null, scanPolicyRevisionNo: null, allowedMimeTypes: [], maxSizeBytes: null, maxUploadSeconds: null };
  }
  const types = v.allowedMimeTypes;
  require(exactFields(v, constraintFields) && (v.routeSourceSystem === '*' || (typeof v.routeSourceSystem === 'string' && sourceSystemPattern.test(v.routeSourceSystem)))
    && typeof v.instanceCode === 'string' && instanceCodePattern.test(v.instanceCode) && text(v.bucketName)
    && (absent(v.publicBaseUrl) || httpsUrl(v.publicBaseUrl)) && typeof v.scanPolicyCode === 'string' && scanPolicyCodePattern.test(v.scanPolicyCode)
    && integer(v.scanPolicyRevisionNo, 1) && Array.isArray(types) && types.length > 0 && types.every(mimeType) && new Set(types).size === types.length
    && byteCount(v.maxSizeBytes) && v.maxSizeBytes !== '0' && integer(v.maxUploadSeconds, 1));
  return { configured: true, accessLevel: v.accessLevel as AccessLevel, routeSourceSystem: v.routeSourceSystem as string,
    instanceCode: v.instanceCode as string, bucketName: v.bucketName as string, publicBaseUrl: (v.publicBaseUrl as string | null) ?? null,
    scanPolicyCode: v.scanPolicyCode as string, scanPolicyRevisionNo: v.scanPolicyRevisionNo as number,
    allowedMimeTypes: [...types as string[]], maxSizeBytes: v.maxSizeBytes as string, maxUploadSeconds: v.maxUploadSeconds as number };
}

/**
 * 上传记录严格解码：字段集合固定，ID 与字节数为十进制字符串
 * <li>地址只在检查通过时出现，且为 HTTPS；带版本的地址不会脱离公开地址单独出现。</li>
 */
export function decodeUploadedFile(value: unknown): UploadedFile {
  require(object(value) && exactFields(value, fileFields));
  const v = value as unknown as UploadedFile;
  require(decimalId(v.fileId) && v.fileAddress === `/files/v1/${v.fileId}` && text(v.displayName) && mimeType(v.mimeType) && byteCount(v.sizeBytes)
    && (scanStatuses as readonly unknown[]).includes(v.scanStatus) && integer(v.versionNo, 1)
    && (v.uploaderAccountId === null || decimalId(v.uploaderAccountId)) && typeof v.uploadedByMe === 'boolean' && instant(v.createTime)
    && (v.publicUrl === null || httpsUrl(v.publicUrl)) && (v.versionedUrl === null || (httpsUrl(v.versionedUrl) && v.publicUrl !== null))
    && (v.scanStatus === 'AVAILABLE' || (v.publicUrl === null && v.versionedUrl === null)));
  return v;
}

/** 单个文件：必须是请求的文件。 */
export function decodeUploadedFileFor(fileId: string) {
  return (value: unknown): UploadedFile => {
    const file = decodeUploadedFile(value);
    require(file.fileId === fileId);
    return file;
  };
}

/** 分页响应（与文件记录列表同形状）：逐行严格解码，文件 ID 不重复。 */
export function decodeUploadedFilePage(value: unknown): PagingResponseBody<UploadedFile> {
  require(object(value) && exactFields(value, ['items', 'page', 'size', 'total']) && Array.isArray(value.items));
  const v = value as unknown as PagingResponseBody<unknown>;
  const items = v.items.map(decodeUploadedFile);
  require(new Set(items.map(item => item.fileId)).size === items.length);
  return { ...v, items };
}

export interface UploadCompletionReceipt {
  uploadId: string; fileId: string; fileAddress: string; completedAt: string; versionNo: number; accessLevel: 'PUBLIC';
}

/** 完成上传回执（Platform FileUploadCompletionResponseBody 原样转发）：6 个字段，公开文件，文件地址与文件 ID 对应；不含检查状态。 */
export function decodeUploadCompletionReceipt(value: unknown): UploadCompletionReceipt {
  require(object(value) && exactFields(value, ['uploadId', 'fileId', 'fileAddress', 'completedAt', 'versionNo', 'accessLevel']));
  const v = value as unknown as UploadCompletionReceipt;
  require(decimalId(v.uploadId) && decimalId(v.fileId) && v.fileAddress === `/files/v1/${v.fileId}` && instant(v.completedAt)
    && integer(v.versionNo, 1) && v.accessLevel === 'PUBLIC');
  return v;
}

// ---------- 开始上传：请求体与前端预校验 ----------

export interface PickedFile { name: string; size: number; type: string }

/** 开始上传请求体：只有文件名、类型与字节数（十进制字符串）；原因、级别与上传人由 BFF 补上。 */
export const startUploadBody = (file: PickedFile) => ({ filename: file.name, mimeType: file.type, sizeBytes: String(file.size) });

/**
 * 开始上传前按限制提示拦截；有问题返回原因，被拦截时不调用接口
 * <li>未配置时不检查（上传区已禁用）。</li>
 * <li>浏览器识别不出类型时按不允许处理。</li>
 */
export function uploadFileIssue(file: PickedFile, constraints: UploadConstraints): string | null {
  if (!constraints.configured || constraints.maxSizeBytes === null) return t('upload.issue.notConfigured');
  if (file.size <= 0) return t('upload.issue.empty');
  if (!file.type) return t('upload.issue.unknownType');
  if (!constraints.allowedMimeTypes.includes(file.type.toLowerCase())) {
    return t('upload.issue.typeNotAllowed', { type: file.type, allowed: constraints.allowedMimeTypes.join(', ') });
  }
  if (BigInt(file.size) > BigInt(constraints.maxSizeBytes)) {
    return t('upload.issue.tooLarge', { size: byteSizeLabel(file.size), max: byteSizeLabel(constraints.maxSizeBytes) });
  }
  return null;
}

/** 文件选择框的 accept：允许的类型。 */
export const acceptAttribute = (constraints: UploadConstraints) => constraints.allowedMimeTypes.join(',');

// ---------- 完成上传后的检查轮询 ----------

export const pollIntervalMillis = 1000;
export const pollTimeoutMillis = 30_000;

export type PollOutcome = { kind: 'AVAILABLE' | 'FAILED'; file: UploadedFile } | { kind: 'TIMEOUT'; file: UploadedFile | null };

/** 检查结论：通过、未通过，或仍在检查（null）。 */
export function scanOutcome(file: UploadedFile): 'AVAILABLE' | 'FAILED' | null {
  if (file.scanStatus === 'AVAILABLE') return 'AVAILABLE';
  if (file.scanStatus === 'QUARANTINED' || file.scanStatus === 'REJECTED') return 'FAILED';
  return null;
}

export interface PollOptions {
  signal: AbortSignal; intervalMillis?: number; timeoutMillis?: number; now?: () => number;
  sleep?: (millis: number, signal: AbortSignal) => Promise<void>;
}

const abortableSleep = (millis: number, signal: AbortSignal) => new Promise<void>((resolve, reject) => {
  if (signal.aborted) { reject(signal.reason); return; }
  const timer = setTimeout(resolve, millis);
  signal.addEventListener('abort', () => { clearTimeout(timer); reject(signal.reason); }, { once: true });
});

/**
 * 每秒查一次文件，直到检查通过或未通过，最多 30 秒；一次只有一个请求在途
 * <li>超时返回最后一次查到的文件，页面提示稍后在最近上传中查看。</li>
 * <li>查询失败直接抛出，由调用方提示；中止时抛出中止原因。</li>
 */
export async function pollUploadedFile(fetchFile: (signal: AbortSignal) => Promise<UploadedFile>, options: PollOptions): Promise<PollOutcome> {
  const { signal, intervalMillis = pollIntervalMillis, timeoutMillis = pollTimeoutMillis, now = Date.now, sleep = abortableSleep } = options;
  const deadline = now() + timeoutMillis;
  let last: UploadedFile | null = null;
  for (;;) {
    signal.throwIfAborted();
    last = await fetchFile(signal);
    const outcome = scanOutcome(last);
    if (outcome) return { kind: outcome, file: last };
    if (now() + intervalMillis > deadline) return { kind: 'TIMEOUT', file: last };
    await sleep(intervalMillis, signal);
  }
}

// ---------- 展示 ----------

export interface FileAddresses { publicUrl: string; versionedUrl: string | null; fileAddress: string }

/** 只有公开且检查通过的文件才显示地址。 */
export function visibleAddresses(file: UploadedFile): FileAddresses | null {
  if (file.scanStatus !== 'AVAILABLE' || !file.publicUrl) return null;
  return { publicUrl: file.publicUrl, versionedUrl: file.versionedUrl, fileAddress: file.fileAddress };
}

/** 上传人：本人显示「我」，否则账号 ID。 */
export function uploaderLabel(file: Pick<UploadedFile, 'uploaderAccountId' | 'uploadedByMe'>): string {
  if (file.uploadedByMe) return t('upload.recent.me');
  return file.uploaderAccountId ?? '—';
}

export interface PageLayout { showUpload: boolean; uploadEnabled: boolean; notConfigured: boolean }

/**
 * 页面布局：没有上传按钮时只读（不显示上传区与结果卡）；未配置时提示并禁用上传区；限制提示未取到时也禁用上传区。
 */
export function pageLayout(canUpload: boolean, constraints: UploadConstraints | null): PageLayout {
  const notConfigured = constraints !== null && !constraints.configured;
  return { showUpload: canUpload, uploadEnabled: canUpload && constraints?.configured === true, notConfigured };
}

// ---------- 最近上传 ----------

export const recentRangeDays = [1, 7, 31] as const;
export type RecentRangeDays = typeof recentRangeDays[number];
export const recentPageSizes = [10, 20, 50];

export function recentRangeLabel(days: RecentRangeDays): string {
  if (days === 1) return t('upload.recent.range.last24Hours');
  if (days === 7) return t('upload.recent.range.last7Days');
  return t('upload.recent.range.last31Days');
}

/** 上传期限：整分钟按分钟显示，否则按秒。 */
export function uploadValidityLabel(seconds: number): string {
  return seconds % 60 === 0 ? t('upload.constraints.validityMinutes', { minutes: seconds / 60 }) : t('upload.constraints.validitySeconds', { seconds });
}

/** 写入规则：命中调用方自己的精确规则，或默认规则 *。 */
export function routeRuleLabel(routeSourceSystem: string): string {
  return routeSourceSystem === '*' ? t('upload.constraints.ruleDefault') : t('upload.constraints.ruleExact', { source: routeSourceSystem });
}

/** 最近上传筛选：最近 N 天（结束不含，取当前时间），只看我的。 */
export function recentFilters(days: RecentRangeDays, mine: boolean, now = new Date()): Record<string, string> {
  const to = now.getTime();
  return { createdFrom: new Date(to - days * 86_400_000).toISOString(), createdTo: new Date(to).toISOString(), mine: String(mine) };
}

/** 上传失败原因：字段错误汇总；Platform / BFF 的原因原样显示；其余用通用文案。 */
export function uploadErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return error instanceof Error && error.message ? error.message : t('upload.failed.generic');
  if (error.fieldErrors.length) return error.fieldErrors.map(item => t('upload.failed.fieldError', { field: item.field, message: item.message })).join('; ');
  return error.detail || error.message;
}

/** 失败原因：完成上传时的 422 是内容检查未通过（例如与声明类型不符），不生成文件；其余原因同 uploadErrorMessage。 */
export function uploadRejectionReason(error: unknown, completing: boolean): string {
  if (completing && error instanceof ApiError && error.status === 422) return t('upload.issue.contentRejected', { detail: uploadErrorMessage(error) });
  return uploadErrorMessage(error);
}
