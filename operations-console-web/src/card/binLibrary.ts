import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { translator } from '../i18n/index.ts';

const t = translator('card');

// ---------- 路径、路由与按钮（块 132） ----------

const admin = '/card-admin/v1/bin-library';

/** Operations BFF 卡 BIN 库路径（经 Gateway /api/operations 前缀）。 */
export const binLibraryPaths = {
  versions: `${admin}/versions`,
  version: (versionNo: number) => `${admin}/versions/${versionNo}`,
  errors: (versionNo: number) => `${admin}/versions/${versionNo}/errors`,
  activation: (versionNo: number) => `${admin}/versions/${versionNo}/activation`,
  activations: `${admin}/activations`,
  ranges: (versionNo: number) => `${admin}/versions/${versionNo}/ranges`,
  lookup: (bin: string, versionNo: number | null) => `${admin}/bins/${bin}${versionNo === null ? '' : `?versionNo=${versionNo}`}`,
  uploads: `${admin}/uploads`,
  uploadCompletion: (uploadId: string) => `${admin}/uploads/${uploadId}/completion`,
} as const;

/** 维护按钮：导入、激活与回切。没有该按钮时页面只读。 */
export const binLibraryManageButton = 'OPERATIONS_CARD_BIN_LIBRARY_MANAGE';

// ---------- 模型 ----------

export const versionStatuses = ['VALIDATING', 'VALID', 'INVALID', 'ACTIVE', 'RETIRED'] as const;
export type VersionStatus = typeof versionStatuses[number];
/** Status labels are `card.enum.versionStatus.<status>`; activation types `card.enum.activationType.<type>`. */
export const versionStatusColors: Readonly<Record<VersionStatus, string>> = {
  VALIDATING: 'processing', VALID: 'blue', INVALID: 'red', ACTIVE: 'green', RETIRED: 'default',
};
/** 校验通过、有号段可浏览的状态；VALIDATING / INVALID 不能浏览号段。 */
export const browsableStatuses: readonly VersionStatus[] = ['VALID', 'ACTIVE', 'RETIRED'];
export const isBrowsable = (version: Pick<BinLibraryVersion, 'status'>) => browsableStatuses.includes(version.status);

/** Card 允许的卡属性取值；显示名为 `card.enum.cardBrand.<值>`、`card.enum.fundingType.<值>`、`card.enum.cardProduct.<值>`。 */
export const cardBrands = ['VISA', 'MASTERCARD', 'AMEX', 'UNIONPAY', 'MADA', 'DISCOVER', 'JCB', 'DINERS'] as const;
export const fundingTypes = ['CREDIT', 'DEBIT', 'PREPAID', 'CHARGE'] as const;
/** 卡组织标识：品牌缩写（品牌名称，不翻译）与底色；未知卡组织用灰底原始编码。 */
export const brandMarks: Readonly<Record<string, { mark: string; color: string }>> = {
  VISA: { mark: 'VISA', color: '#1a1f71' }, MASTERCARD: { mark: 'MC', color: '#252525' }, AMEX: { mark: 'AMEX', color: '#2e77bc' },
  UNIONPAY: { mark: 'UP', color: '#d6262d' }, MADA: { mark: 'mada', color: '#14805e' }, DISCOVER: { mark: 'DISC', color: '#e8762c' },
  JCB: { mark: 'JCB', color: '#0b7a3e' }, DINERS: { mark: 'DC', color: '#004a97' },
};
/** 发卡国筛选的常用国家；有参考数据权限时改用 Platform 国家清单。 */
export const commonIssuerCountries: readonly string[] = ['AE', 'SA', 'EG', 'KW', 'QA', 'BH', 'OM', 'JO', 'US', 'GB', 'CN'];

export interface BinLibraryVersion {
  versionNo: number; status: VersionStatus; active: boolean; fileAddress: string | null; fileName: string | null;
  fileContentHash: string | null; totalRows: number | null; processedRows: number; errorRows: number; failureReason: string | null;
  createdBy: string | null; createdByLabel: string | null; createTime: string; updateTime: string;
}

export interface BinImportError { lineNo: number; columnName: string | null; errorCode: string; errorMessage: string; valueExcerpt: string | null }

export interface BinLibraryActivation {
  activationType: 'ACTIVATE' | 'ROLLBACK'; versionNo: number; previousVersionNo: number | null; operatorAccountId: string | null;
  operatorLabel: string | null; activatedAt: string;
}

/** 版本内一个号段；fundingType、cardLevel、cardProduct、issuerName 可空。 */
export interface BinRange {
  binPrefix: string; prefixLength: number; cardBrand: string; fundingType: string | null; cardLevel: string | null; cardProduct: string | null;
  issuerCountry: string; issuerRegion: string; issuerName: string | null;
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
    && isNullableText(value.createdByLabel) && isText(value.createTime) && isText(value.updateTime));
  const v = value as unknown as BinLibraryVersion;
  return { versionNo: v.versionNo, status: v.status, active: v.active, fileAddress: orNull(v.fileAddress), fileName: orNull(v.fileName),
    fileContentHash: orNull(v.fileContentHash), totalRows: orNull(v.totalRows), processedRows: v.processedRows, errorRows: v.errorRows,
    failureReason: orNull(v.failureReason), createdBy: orNull(v.createdBy), createdByLabel: orNull(v.createdByLabel),
    createTime: v.createTime, updateTime: v.updateTime };
}

export function decodeImportError(value: unknown): BinImportError {
  require(isRecord(value) && isInteger(value.lineNo, 1) && isNullableText(value.columnName) && isText(value.errorCode)
    && isText(value.errorMessage) && isNullableText(value.valueExcerpt));
  const v = value as unknown as BinImportError;
  return { lineNo: v.lineNo, columnName: orNull(v.columnName), errorCode: v.errorCode, errorMessage: v.errorMessage, valueExcerpt: orNull(v.valueExcerpt) };
}

export function decodeActivation(value: unknown): BinLibraryActivation {
  require(isRecord(value) && (value.activationType === 'ACTIVATE' || value.activationType === 'ROLLBACK') && isInteger(value.versionNo, 1)
    && isNullableInteger(value.previousVersionNo, 1) && isNullableText(value.operatorAccountId) && isNullableText(value.operatorLabel)
    && isText(value.activatedAt));
  const v = value as unknown as BinLibraryActivation;
  return { activationType: v.activationType, versionNo: v.versionNo, previousVersionNo: orNull(v.previousVersionNo),
    operatorAccountId: orNull(v.operatorAccountId), operatorLabel: orNull(v.operatorLabel), activatedAt: v.activatedAt };
}

/** 号段：前缀 6～8 位数字且长度与 prefixLength 一致，卡组织与发卡国必填；四个可空字段只接受 null（或缺省）与字符串。 */
export function decodeRange(value: unknown): BinRange {
  require(isRecord(value) && isText(value.binPrefix) && /^[0-9]{6,8}$/.test(value.binPrefix) && value.prefixLength === value.binPrefix.length
    && isText(value.cardBrand) && value.cardBrand !== '' && isText(value.issuerCountry) && /^[A-Z]{2}$/.test(value.issuerCountry)
    && isText(value.issuerRegion) && ['fundingType', 'cardLevel', 'cardProduct', 'issuerName'].every(key => isNullableText(value[key])));
  const v = value as unknown as BinRange;
  return { binPrefix: v.binPrefix, prefixLength: v.prefixLength, cardBrand: v.cardBrand, fundingType: orNull(v.fundingType),
    cardLevel: orNull(v.cardLevel), cardProduct: orNull(v.cardProduct), issuerCountry: v.issuerCountry, issuerRegion: v.issuerRegion,
    issuerName: orNull(v.issuerName) };
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
export const decodeRangePage = pageDecoder(decodeRange);

// ---------- 版本动作与进度 ----------

export type VersionAction = { kind: 'ACTIVATE' | 'ROLLBACK'; verb: string };

/** VALID 可激活、RETIRED 可回切；其余状态没有动作。 */
export function versionAction(version: Pick<BinLibraryVersion, 'status'>): VersionAction | null {
  if (version.status === 'VALID') return { kind: 'ACTIVATE', verb: t('enum.activationType.ACTIVATE') };
  if (version.status === 'RETIRED') return { kind: 'ROLLBACK', verb: t('enum.activationType.ROLLBACK') };
  return null;
}

/** 校验中的进度：总行数未知时不定进度（等待扫描 / 校验中）；已知时为写入百分比（向下取整，至多 100）。 */
export function versionProgress(version: Pick<BinLibraryVersion, 'totalRows' | 'processedRows'>): { percent: number | null; label: string } {
  if (version.totalRows === null || version.totalRows <= 0) return { percent: null, label: t('progress.waiting') };
  const percent = Math.min(100, Math.floor(version.processedRows * 100 / version.totalRows));
  return { percent, label: t('progress.written', { processed: formatCount(version.processedRows), total: formatCount(version.totalRows) }) };
}

/** 号段数差异：+3,422 / -3,365 / ±0；任一未知时为空串。 */
export function rowDelta(current: number | null, target: number | null): string {
  if (current === null || target === null) return '';
  const delta = target - current;
  return delta > 0 ? `+${formatCount(delta)}` : delta < 0 ? `-${formatCount(-delta)}` : '±0';
}

export const formatCount = (value: number | null | undefined) => value === null || value === undefined ? '—' : value.toLocaleString('en-US');

/** 409：页面看到的生效版本已被他人切换。 */
export const activationConflictMessage = () => t('activation.conflict');

// ---------- BIN 前缀筛选与号段查询 ----------

/**
 * BIN 前缀输入：空或 1～8 位数字可筛选；非数字或超过 8 位（可能是卡号）即时提示且不发请求。
 * 两种提示都说明"只按 BIN 查询"，不回显输入内容。
 */
export function binPrefixIssue(input: string): string | null {
  const value = input.trim();
  if (!value) return null;
  if (!/^[0-9]*$/.test(value)) return t('binInput.digitsOnly');
  if (value.length > 8) return t('binInput.tooLong');
  return null;
}

/** 6～8 位数字时另外做最长前缀匹配（与单 BIN 查询接口一致）。 */
export const isQueryableBin = (input: string) => /^[0-9]{6,8}$/.test(input.trim());

export interface RangeFilters { binPrefix: string; cardBrand: string | null; fundingType: string | null; issuerCountry: string | null }
export const emptyRangeFilters: RangeFilters = { binPrefix: '', cardBrand: null, fundingType: null, issuerCountry: null };

/** 号段列表 Query：只带有值的条件；BIN 前缀不合法时返回 null（不发请求）。分页参数由公共分页加载器追加。 */
export function rangeQueryFilters(filters: RangeFilters): Record<string, string> | null {
  const binPrefix = filters.binPrefix.trim();
  if (binPrefixIssue(binPrefix)) return null;
  const query: Record<string, string> = {};
  if (binPrefix) query.binPrefix = binPrefix;
  if (filters.cardBrand) query.cardBrand = filters.cardBrand;
  if (filters.fundingType) query.fundingType = filters.fundingType;
  if (filters.issuerCountry) query.issuerCountry = filters.issuerCountry;
  return query;
}

/** 已选筛选条件个数（BIN 前缀为空不计）。 */
export function activeFilterCount(filters: RangeFilters): number {
  return [filters.binPrefix.trim(), filters.cardBrand, filters.fundingType, filters.issuerCountry].filter(Boolean).length;
}

// ---------- 操作人显示 ----------

/**
 * 操作人：BFF 补全的显示名优先；没有时非数字原值（如 system）原样显示，数字账号 ID 显示"…"加末 6 位，悬停看完整 ID。
 * 原值为空（初始化种子数据）时显示系统。
 */
export function operatorDisplay(label: string | null, raw: string | null): { text: string; full: string | null } {
  if (label) return { text: label, full: raw && raw !== label ? raw : null };
  if (!raw) return { text: t('operator.system'), full: null };
  if (!/^[0-9]+$/.test(raw) || raw.length <= 6) return { text: raw, full: null };
  return { text: `…${raw.slice(-6)}`, full: raw };
}

// ---------- CSV 本地预检（完整校验由 Card 在创建版本后进行） ----------

export const csvHeader = 'bin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,issuer_name';
export const csvColumns = csvHeader.split(',');
export const maxCsvBytes = 50 * 1024 * 1024;

/** 示例模板：表头加两行合成数据（测试卡号段）。 */
export const csvTemplate = `${csvHeader}\n41111111,VISA,CREDIT,CLASSIC,CONSUMER,US,TEST BANK\n510510,MASTERCARD,DEBIT,,CONSUMER,GB,"EXAMPLE BANK, LTD"\n`;
/** 模板下载：页头"CSV 模板"与导入弹窗共用。 */
export const csvTemplateHref = `data:text/csv;charset=utf-8,${encodeURIComponent(csvTemplate)}`;
export const csvTemplateFileName = 'card-bin-library-template.csv';

/** 文件选择检查：.csv 扩展名、非空、不超过 50 MiB。 */
export function csvFileIssue(file: { name: string; size: number }): string | null {
  if (!/\.csv$/i.test(file.name)) return t('csv.onlyCsv');
  if (file.size <= 0) return t('csv.empty');
  if (file.size > maxCsvBytes) return t('csv.tooLarge');
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
  if (text.includes('\0')) return { headerValid: false, issue: t('csv.nul'), rows: [] };
  const lines = text.split(/\r?\n/);
  if (!complete) lines.pop();
  if (lines[0] !== csvHeader) return { headerValid: false, issue: t('csv.headerMismatch', { header: csvHeader }), rows: [] };
  const data = lines.slice(1).filter(line => line !== '').slice(0, rows);
  const parsed = data.map(splitCsvLine);
  const broken = parsed.findIndex(cells => cells === null || cells.length !== csvColumns.length);
  return { headerValid: true, issue: broken >= 0 ? t('csv.columnCount', { line: broken + 2, columns: csvColumns.length }) : null,
    rows: parsed.filter((cells): cells is string[] => cells !== null) };
}
