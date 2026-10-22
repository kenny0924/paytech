import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';

// ---------- 路径、路由与按钮（块 132） ----------

const admin = '/card-admin/v1/bin-library';

/** Operations BFF 卡 BIN 库路径（经 Gateway /api/operations 前缀）。 */
export const binLibraryPaths = {
  versions: `${admin}/versions`,
  version: (versionNo: number) => `${admin}/versions/${versionNo}`,
  errors: (versionNo: number) => `${admin}/versions/${versionNo}/errors`,
  activation: (versionNo: number) => `${admin}/versions/${versionNo}/activation`,
  activations: `${admin}/activations`,
  lookup: (bin: string, versionNo: number | null) => `${admin}/bins/${bin}${versionNo === null ? '' : `?versionNo=${versionNo}`}`,
  uploads: `${admin}/uploads`,
  uploadCompletion: (uploadId: string) => `${admin}/uploads/${uploadId}/completion`,
} as const;

/** 维护按钮：导入、激活与回切。没有该按钮时页面只读。 */
export const binLibraryManageButton = 'OPERATIONS_CARD_BIN_LIBRARY_MANAGE';

// ---------- 模型 ----------

export const versionStatuses = ['VALIDATING', 'VALID', 'INVALID', 'ACTIVE', 'RETIRED'] as const;
export type VersionStatus = typeof versionStatuses[number];
export const versionStatusLabels: Readonly<Record<VersionStatus, string>> = {
  VALIDATING: '校验中', VALID: '可激活', INVALID: '校验失败', ACTIVE: '生效中', RETIRED: '已停用',
};
export const versionStatusColors: Readonly<Record<VersionStatus, string>> = {
  VALIDATING: 'processing', VALID: 'blue', INVALID: 'red', ACTIVE: 'green', RETIRED: 'default',
};

export interface BinLibraryVersion {
  versionNo: number; status: VersionStatus; active: boolean; fileAddress: string | null; fileName: string | null;
  fileContentHash: string | null; totalRows: number | null; processedRows: number; errorRows: number; failureReason: string | null;
  createdBy: string | null; createTime: string; updateTime: string;
}

export interface BinImportError { lineNo: number; columnName: string | null; errorCode: string; errorMessage: string; valueExcerpt: string | null }

export const activationTypeLabels: Readonly<Record<string, string>> = { ACTIVATE: '激活', ROLLBACK: '回切' };
export interface BinLibraryActivation {
  activationType: 'ACTIVATE' | 'ROLLBACK'; versionNo: number; previousVersionNo: number | null; operatorAccountId: string | null; activatedAt: string;
}

export interface BinLookup {
  recognized: boolean; binLibraryVersionNo: number | null; matchedPrefixLength: number | null; binPrefix: string | null;
  cardBrand: string | null; fundingType: string | null; cardLevel: string | null; cardProduct: string | null;
  issuerCountry: string | null; issuerRegion: string | null; issuerName: string | null;
}

export interface UploadAuthorization { method: 'PUT'; url: string; headers: Readonly<Record<string, string>>; expiresAt: string }
export interface UploadStart { uploadId: string; authorization: UploadAuthorization; expiresAt: string }
export interface UploadCompletion { uploadId: string; fileId: string; fileAddress: string; scanStatus: string; completedAt: string }

// ---------- 解码（失败只给通用错误，不回显响应内容） ----------

const isRecord = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
function require(valid: boolean): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
const isText = (v: unknown): v is string => typeof v === 'string';
const isNullableText = (v: unknown) => v === null || v === undefined || typeof v === 'string';
const isInteger = (v: unknown, min = 0) => Number.isInteger(v) && Number(v) >= min;
const isNullableInteger = (v: unknown, min = 0) => v === null || v === undefined || isInteger(v, min);
const idPattern = /^[1-9][0-9]{0,18}$/;
const orNull = <T>(v: T | undefined | null): T | null => v ?? null;

export function decodeVersion(value: unknown): BinLibraryVersion {
  require(isRecord(value) && isInteger(value.versionNo, 1) && (versionStatuses as readonly unknown[]).includes(value.status)
    && typeof value.active === 'boolean' && isNullableText(value.fileAddress) && isNullableText(value.fileName)
    && isNullableText(value.fileContentHash) && isNullableInteger(value.totalRows) && isInteger(value.processedRows)
    && isInteger(value.errorRows) && isNullableText(value.failureReason) && isNullableText(value.createdBy)
    && isText(value.createTime) && isText(value.updateTime));
  const v = value as unknown as BinLibraryVersion;
  return { versionNo: v.versionNo, status: v.status, active: v.active, fileAddress: orNull(v.fileAddress), fileName: orNull(v.fileName),
    fileContentHash: orNull(v.fileContentHash), totalRows: orNull(v.totalRows), processedRows: v.processedRows, errorRows: v.errorRows,
    failureReason: orNull(v.failureReason), createdBy: orNull(v.createdBy), createTime: v.createTime, updateTime: v.updateTime };
}

export function decodeImportError(value: unknown): BinImportError {
  require(isRecord(value) && isInteger(value.lineNo, 1) && isNullableText(value.columnName) && isText(value.errorCode)
    && isText(value.errorMessage) && isNullableText(value.valueExcerpt));
  const v = value as unknown as BinImportError;
  return { lineNo: v.lineNo, columnName: orNull(v.columnName), errorCode: v.errorCode, errorMessage: v.errorMessage, valueExcerpt: orNull(v.valueExcerpt) };
}

export function decodeActivation(value: unknown): BinLibraryActivation {
  require(isRecord(value) && (value.activationType === 'ACTIVATE' || value.activationType === 'ROLLBACK') && isInteger(value.versionNo, 1)
    && isNullableInteger(value.previousVersionNo, 1) && isNullableText(value.operatorAccountId) && isText(value.activatedAt));
  const v = value as unknown as BinLibraryActivation;
  return { activationType: v.activationType, versionNo: v.versionNo, previousVersionNo: orNull(v.previousVersionNo),
    operatorAccountId: orNull(v.operatorAccountId), activatedAt: v.activatedAt };
}

export function decodeLookup(value: unknown): BinLookup {
  require(isRecord(value) && typeof value.recognized === 'boolean' && isNullableInteger(value.binLibraryVersionNo, 1)
    && isNullableInteger(value.matchedPrefixLength, 6) && ['binPrefix', 'cardBrand', 'fundingType', 'cardLevel', 'cardProduct',
      'issuerCountry', 'issuerRegion', 'issuerName'].every(key => isNullableText(value[key])));
  const v = value as unknown as BinLookup;
  return { recognized: v.recognized, binLibraryVersionNo: orNull(v.binLibraryVersionNo), matchedPrefixLength: orNull(v.matchedPrefixLength),
    binPrefix: orNull(v.binPrefix), cardBrand: orNull(v.cardBrand), fundingType: orNull(v.fundingType), cardLevel: orNull(v.cardLevel),
    cardProduct: orNull(v.cardProduct), issuerCountry: orNull(v.issuerCountry), issuerRegion: orNull(v.issuerRegion), issuerName: orNull(v.issuerName) };
}

export function decodeUploadStart(value: unknown): UploadStart {
  const authorization = isRecord(value) ? value.authorization : undefined;
  require(isRecord(value) && isText(value.uploadId) && idPattern.test(value.uploadId) && isText(value.expiresAt) && isRecord(authorization)
    && authorization.method === 'PUT' && isText(authorization.url) && /^https?:\/\//.test(authorization.url)
    && (authorization.headers === undefined || authorization.headers === null
      || (isRecord(authorization.headers) && Object.values(authorization.headers).every(isText))));
  const headers = (authorization.headers ?? {}) as Record<string, string>;
  return { uploadId: value.uploadId, expiresAt: value.expiresAt,
    authorization: { method: 'PUT', url: authorization.url as string, headers, expiresAt: String(authorization.expiresAt ?? value.expiresAt) } };
}

export function decodeUploadCompletion(value: unknown): UploadCompletion {
  require(isRecord(value) && isText(value.uploadId) && isText(value.fileId) && idPattern.test(value.fileId)
    && value.fileAddress === `/files/v1/${value.fileId}` && isText(value.scanStatus) && isText(value.completedAt));
  return value as unknown as UploadCompletion;
}

/** 分页响应：items 逐项解码，page/size/total 交给公共分页加载器复核。 */
function pageDecoder<T>(decodeItem: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    require(isRecord(value) && Array.isArray(value.items) && isInteger(value.page, 1) && isInteger(value.size, 1) && isInteger(value.total));
    const page = value as unknown as PagingResponseBody<unknown>;
    return { items: page.items.map(decodeItem), page: page.page, size: page.size, total: page.total };
  };
}
export const decodeVersionPage = pageDecoder(decodeVersion);
export const decodeImportErrorPage = pageDecoder(decodeImportError);
export const decodeActivationPage = pageDecoder(decodeActivation);

// ---------- 版本动作与进度 ----------

export type VersionAction = { kind: 'ACTIVATE' | 'ROLLBACK'; verb: string };

/** VALID 可激活、RETIRED 可回切；其余状态没有动作。 */
export function versionAction(version: Pick<BinLibraryVersion, 'status'>): VersionAction | null {
  if (version.status === 'VALID') return { kind: 'ACTIVATE', verb: '激活' };
  if (version.status === 'RETIRED') return { kind: 'ROLLBACK', verb: '回切' };
  return null;
}

/** 校验中的进度：总行数未知时不定进度（等待扫描 / 校验中）；已知时为写入百分比（向下取整，至多 100）。 */
export function versionProgress(version: Pick<BinLibraryVersion, 'totalRows' | 'processedRows'>): { percent: number | null; label: string } {
  if (version.totalRows === null || version.totalRows <= 0) return { percent: null, label: '等待扫描 / 校验中' };
  const percent = Math.min(100, Math.floor(version.processedRows * 100 / version.totalRows));
  return { percent, label: `已写入 ${formatCount(version.processedRows)} / ${formatCount(version.totalRows)} 行` };
}

/** 号段数差异：+3,422 / -3,365 / ±0；任一未知时为空串。 */
export function rowDelta(current: number | null, target: number | null): string {
  if (current === null || target === null) return '';
  const delta = target - current;
  return delta > 0 ? `+${formatCount(delta)}` : delta < 0 ? `-${formatCount(-delta)}` : '±0';
}

export const formatCount = (value: number | null | undefined) => value === null || value === undefined ? '—' : value.toLocaleString('en-US');

/** 409：页面看到的生效版本已被他人切换。 */
export const activationConflictMessage = '生效版本已被他人修改，请刷新后重试';

// ---------- BIN 输入 ----------

/** 6～8 位数字才可查询；超过 8 位的输入可能是卡号，不发送。 */
export function binInputIssue(input: string): string | null {
  const value = input.trim();
  if (!value) return null;
  if (!/^[0-9]*$/.test(value)) return '只能输入数字';
  if (value.length > 8) return '最多 8 位；只按 BIN 查询，不接受完整卡号';
  if (value.length < 6) return '至少 6 位';
  return null;
}

export const isQueryableBin = (input: string) => /^[0-9]{6,8}$/.test(input.trim());

// ---------- CSV 本地预检（完整校验由 Card 在创建版本后进行） ----------

export const csvHeader = 'bin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,issuer_name';
export const csvColumns = csvHeader.split(',');
export const maxCsvBytes = 50 * 1024 * 1024;

/** 示例模板：表头加两行合成数据（测试卡号段）。 */
export const csvTemplate = `${csvHeader}\n41111111,VISA,CREDIT,CLASSIC,CONSUMER,US,TEST BANK\n510510,MASTERCARD,DEBIT,,CONSUMER,GB,"EXAMPLE BANK, LTD"\n`;

/** 文件选择检查：.csv 扩展名、非空、不超过 50 MiB。 */
export function csvFileIssue(file: { name: string; size: number }): string | null {
  if (!/\.csv$/i.test(file.name)) return '只接受 .csv 文件';
  if (file.size <= 0) return '文件为空';
  if (file.size > maxCsvBytes) return '文件超过 50 MB 上限';
  return null;
}

/** 按 RFC 4180 拆分一行（双引号转义）；引号未闭合返回 null。 */
export function splitCsvLine(line: string): string[] | null {
  const cells: string[] = []; let cell = ''; let quoted = false;
  for (let index = 0; index < line.length; index++) {
    const char = line[index];
    if (quoted) {
      if (char === '"' && line[index + 1] === '"') { cell += '"'; index++; }
      else if (char === '"') quoted = false;
      else cell += char;
    } else if (char === '"' && cell === '') quoted = true;
    else if (char === ',') { cells.push(cell); cell = ''; }
    else cell += char;
  }
  if (quoted) return null;
  cells.push(cell);
  return cells;
}

export interface CsvPreview { headerValid: boolean; issue: string | null; rows: string[][] }

/**
 * 读取文件开头（调用方只传前若干 KB）：去 BOM，按 LF / CRLF 分行，核对第 1 行逐字等于标准表头，返回前 3 行预览
 * <li>最后一行可能因截断不完整，不进入预览；预览只做格式提示，不判断取值。</li>
 */
export function previewCsv(head: string, complete: boolean, rows = 3): CsvPreview {
  const text = head.startsWith('﻿') ? head.slice(1) : head;
  if (text.includes('\0')) return { headerValid: false, issue: '文件含 NUL 字符，不是文本 CSV', rows: [] };
  const lines = text.split(/\r?\n/);
  if (!complete) lines.pop();
  if (lines[0] !== csvHeader) return { headerValid: false, issue: `第 1 行必须逐字为 ${csvHeader}`, rows: [] };
  const data = lines.slice(1).filter(line => line !== '').slice(0, rows);
  const parsed = data.map(splitCsvLine);
  const broken = parsed.findIndex(cells => cells === null || cells.length !== csvColumns.length);
  return { headerValid: true, issue: broken >= 0 ? `第 ${broken + 2} 行列数不是 ${csvColumns.length} 列` : null,
    rows: parsed.filter((cells): cells is string[] => cells !== null) };
}
