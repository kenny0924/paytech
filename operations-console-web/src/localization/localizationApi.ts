import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';

/**
 * 多语言管理的 BFF 路径与响应解码（technique 3.8；字段以 Platform api 合同 localization 包为准）。
 * BFF 前缀 /localization-admin/v1 = Platform /internal/localization/v1 去掉前缀后同路径转发；session 统一加 /api/operations。
 * 导入文件上传两步端点 /uploads 与 /uploads/{uploadId}/completion 见卡 B 报告（用途 KEY_IMPORT / TRANSLATION_IMPORT）。
 */
export const adminBase = '/localization-admin/v1';
const app = (code: string) => `${adminBase}/applications/${encodeURIComponent(code)}`;
export const localizationPaths = {
  locales: `${adminBase}/locales`,
  locale: (code: string) => `${adminBase}/locales/${encodeURIComponent(code)}`,
  applications: `${adminBase}/applications`,
  application: app,
  namespaces: (code: string) => `${app(code)}/namespaces`,
  namespace: (code: string, ns: string) => `${app(code)}/namespaces/${encodeURIComponent(ns)}`,
  keyImportPreview: (code: string) => `${app(code)}/key-imports/previews`,
  keyImports: (code: string) => `${app(code)}/key-imports`,
  translations: (code: string) => `${app(code)}/translations`,
  translationBatch: (code: string) => `${app(code)}/translations/batch`,
  translationExport: (code: string, locale: string) => `${app(code)}/translations/export?locale=${encodeURIComponent(locale)}`,
  translationImports: (code: string) => `${app(code)}/translations/imports`,
  precheck: (code: string) => `${app(code)}/release-precheck`,
  releases: (code: string) => `${app(code)}/releases`,
  release: (code: string, releaseNo: number) => `${app(code)}/releases/${releaseNo}`,
  releaseDiff: (code: string, releaseNo: number) => `${app(code)}/releases/${releaseNo}/diff`,
  currentRelease: (code: string) => `${app(code)}/current-release`,
  uploads: `${adminBase}/uploads`,
  uploadCompletion: (uploadId: string) => `${adminBase}/uploads/${encodeURIComponent(uploadId)}/completion`,
} as const;

/** 前端路由（与 pageCapabilities、Platform 注册块一致）。 */
export const localizationRoutes = { translations: '/i18n/translations', releases: '/i18n/releases', settings: '/i18n/settings' } as const;

/**
 * 按钮资源码（pageCapabilities 中登记；每页独立，绑定权限：发布与切换为 I18N_PUBLISH，其余为 I18N_EDIT）。
 * 注册块以卡 B 为准，未定稿前按此命名。
 */
export const localizationButtons = {
  translationEdit: 'OPERATIONS_I18N_TRANSLATION_EDIT', keyImport: 'OPERATIONS_I18N_KEY_IMPORT', publish: 'OPERATIONS_I18N_RELEASE_PUBLISH',
  activate: 'OPERATIONS_I18N_RELEASE_ACTIVATE',
  applicationCreate: 'OPERATIONS_I18N_APPLICATION_CREATE', settingsEdit: 'OPERATIONS_I18N_SETTINGS_EDIT', namespaceManage: 'OPERATIONS_I18N_NAMESPACE_MANAGE',
} as const;

/** 基准语言，全系统固定。 */
export const BASE_LOCALE = 'en';

export interface Locale {
  localeCode: string; nativeName: string; englishName: string; textDirection: 'LTR' | 'RTL'; status: 'ENABLED' | 'DISABLED';
  sortOrder: number; version: number; updatedBy: string | null; updateTime: string | null;
}
export interface Application {
  applicationCode: string; applicationName: string; defaultLocale: string; requiredLocales: string[]; coverageThresholdPercent: number;
  status: string; currentReleaseNo: number | null; keySetRevision: number; contentRevision: number; version: number;
  updatedBy: string | null; updateTime: string | null;
  /** 相对线上发布有变化的 key 数（去重）；旧响应没有时为 null。 */
  pendingChangeCount: number | null;
}
export interface LocaleCoverage {
  locale: string; required: boolean; totalKeys: number; translatedKeys: number; sourceChangedKeys: number; coveragePercent: number; satisfied: boolean;
}
export type ReleaseStatus = 'PENDING_CHECK' | 'READY' | 'FAILED';
/** 相对线上（或上一）发布的变更摘要；样例 key 每类最多 20 条。 */
export interface ReleaseChangeSummary {
  baseReleaseNo: number | null;
  addedKeyCount: number; addedKeys: string[];
  deprecatedKeyCount: number; deprecatedKeys: string[];
  sourceChangedKeyCount: number; sourceChangedKeys: string[];
  translationChanges: { locale: string; changedCount: number; sampleKeys: string[] }[];
}
export interface BundleCheckProgress { total: number; available: number; pending: number; rejected: number }
export interface ReleaseSummary {
  releaseNo: number; status: ReleaseStatus; current: boolean; keyCount: number; releaseNote: string | null; failureReason: string | null;
  createdBy: string | null; createTime: string | null; readyTime: string | null; changeSummary: ReleaseChangeSummary | null;
}
export interface ApplicationDetail {
  application: Application; currentRelease: ReleaseSummary | null; activeKeyCount: number; deprecatedKeyCount: number;
  namespaceCount: number; coverage: LocaleCoverage[];
}
export interface Namespace {
  namespaceCode: string; namespaceName: string; description: string | null; sortOrder: number; activeKeyCount: number;
  deprecatedKeyCount: number; version: number; updatedBy: string | null; updateTime: string | null;
}
export interface Problem { field: string; message: string }
export interface KeyImportItem { namespaceCode: string; keyCode: string; sourceText: string; previousSourceText: string | null; description: string | null }
export interface KeyImportPreview {
  importable: boolean; previewDigest: string; keySetRevision: number; added: KeyImportItem[]; sourceChanged: KeyImportItem[];
  deprecated: KeyImportItem[]; restored: KeyImportItem[]; unchangedCount: number; missingNamespaces: string[]; problems: Problem[];
}
export interface KeyImportResult {
  addedCount: number; sourceChangedCount: number; deprecatedCount: number; restoredCount: number; descriptionChangedCount: number;
  keySetRevision: number; contentRevision: number;
}
export type TranslationState = 'UNTRANSLATED' | 'SOURCE_CHANGED' | 'TRANSLATED';
export interface TranslationEntry {
  namespaceCode: string; keyCode: string; sourceText: string; description: string | null; parameters: string[]; text: string | null;
  version: number; sourceChanged: boolean; updatedBy: string | null; updateTime: string | null;
}
export interface SaveTranslationsResult { translations: { keyCode: string; version: number }[]; contentRevision: number }
export interface ImportTranslationsResult { updatedCount: number; unchangedCount: number; emptyCount: number; contentRevision: number }
export interface LocaleKey { locale: string; keyCode: string }
export interface ReleasePrecheck {
  publishable: boolean; contentRevision: number; nextReleaseNo: number; activeKeyCount: number; coverage: LocaleCoverage[];
  missing: LocaleKey[]; missingTotal: number; sourceChanged: LocaleKey[]; sourceChangedTotal: number; problems: Problem[];
  /** 相对当前线上发布；读取端口未接入时为 null。 */
  changeSummary: ReleaseChangeSummary | null;
}
export interface ReleaseBundle { locale: string; namespaceCode: string; fileAddress: string; contentHash: string; byteSize: number }
export interface ReleaseDetail {
  release: ReleaseSummary; basedOnContentRevision: number; coverage: LocaleCoverage[];
  report: { missing: LocaleKey[]; sourceChanged: LocaleKey[] }; bundles: ReleaseBundle[]; checkProgress: BundleCheckProgress | null;
}
export interface ReleaseTextChange { locale: string; keyCode: string; before: string | null; after: string | null }
export interface ReleaseDiff { releaseNo: number; baseReleaseNo: number | null; added: ReleaseTextChange[]; changed: ReleaseTextChange[]; removed: ReleaseTextChange[] }
export interface TranslationFile { format: string; application: string; locale: string; namespaces: Record<string, Record<string, string>> }

// ---------- 解码 ----------
function invalid(): never { throw new ApiError(502, 'INVALID_RESPONSE'); }
const object = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : invalid();
const list = (value: unknown): unknown[] => Array.isArray(value) ? value : [];
const str = (value: unknown): string => typeof value === 'string' ? value : invalid();
const optStr = (value: unknown): string | null => typeof value === 'string' ? value : null;
const int = (value: unknown, fallback = 0): number => typeof value === 'number' && Number.isFinite(value) ? value : fallback;
const optInt = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;
const strings = (value: unknown): string[] => list(value).filter((item): item is string => typeof item === 'string');

export function decodeLocale(value: unknown): Locale {
  const item = object(value);
  return { localeCode: str(item.localeCode), nativeName: str(item.nativeName), englishName: optStr(item.englishName) ?? '',
    textDirection: item.textDirection === 'RTL' ? 'RTL' : 'LTR', status: item.status === 'DISABLED' ? 'DISABLED' : 'ENABLED',
    sortOrder: int(item.sortOrder), version: int(item.version, 1), updatedBy: optStr(item.updatedBy), updateTime: optStr(item.updateTime) };
}
export const decodeLocales = (value: unknown): Locale[] => list(object(value).items).map(decodeLocale);

export function decodeApplication(value: unknown): Application {
  const item = object(value);
  return { applicationCode: str(item.applicationCode), applicationName: str(item.applicationName), defaultLocale: str(item.defaultLocale),
    requiredLocales: strings(item.requiredLocales), coverageThresholdPercent: int(item.coverageThresholdPercent, 100),
    status: optStr(item.status) ?? 'ENABLED', currentReleaseNo: optInt(item.currentReleaseNo), keySetRevision: int(item.keySetRevision),
    contentRevision: int(item.contentRevision), version: int(item.version, 1), updatedBy: optStr(item.updatedBy), updateTime: optStr(item.updateTime),
    pendingChangeCount: optInt(item.pendingChangeCount) };
}
export const decodeApplications = (value: unknown): Application[] => list(object(value).items).map(decodeApplication);

export function decodeCoverage(value: unknown): LocaleCoverage {
  const item = object(value);
  return { locale: str(item.locale), required: item.required === true, totalKeys: int(item.totalKeys), translatedKeys: int(item.translatedKeys),
    sourceChangedKeys: int(item.sourceChangedKeys), coveragePercent: int(item.coveragePercent, 100), satisfied: item.satisfied !== false };
}
export function decodeChangeSummary(value: unknown): ReleaseChangeSummary | null {
  if (value === null || value === undefined) return null;
  const item = object(value);
  return { baseReleaseNo: optInt(item.baseReleaseNo), addedKeyCount: int(item.addedKeyCount), addedKeys: strings(item.addedKeys),
    deprecatedKeyCount: int(item.deprecatedKeyCount), deprecatedKeys: strings(item.deprecatedKeys),
    sourceChangedKeyCount: int(item.sourceChangedKeyCount), sourceChangedKeys: strings(item.sourceChangedKeys),
    translationChanges: list(item.translationChanges).map(object).map(row => ({ locale: str(row.locale), changedCount: int(row.changedCount), sampleKeys: strings(row.sampleKeys) })) };
}
const releaseStatuses = new Set<ReleaseStatus>(['PENDING_CHECK', 'READY', 'FAILED']);
export function decodeReleaseSummary(value: unknown): ReleaseSummary {
  const item = object(value);
  const status = str(item.status) as ReleaseStatus;
  if (!releaseStatuses.has(status)) invalid();
  return { releaseNo: int(item.releaseNo) || invalid(), status, current: item.current === true, keyCount: int(item.keyCount),
    releaseNote: optStr(item.releaseNote), failureReason: optStr(item.failureReason), createdBy: optStr(item.createdBy),
    createTime: optStr(item.createTime), readyTime: optStr(item.readyTime), changeSummary: decodeChangeSummary(item.changeSummary) };
}
export function decodeApplicationDetail(value: unknown): ApplicationDetail {
  const item = object(value);
  return { application: decodeApplication(item.application), currentRelease: item.currentRelease ? decodeReleaseSummary(item.currentRelease) : null,
    activeKeyCount: int(item.activeKeyCount), deprecatedKeyCount: int(item.deprecatedKeyCount), namespaceCount: int(item.namespaceCount),
    coverage: list(item.coverage).map(decodeCoverage) };
}
export function decodeNamespace(value: unknown): Namespace {
  const item = object(value);
  return { namespaceCode: str(item.namespaceCode), namespaceName: str(item.namespaceName), description: optStr(item.description),
    sortOrder: int(item.sortOrder), activeKeyCount: int(item.activeKeyCount), deprecatedKeyCount: int(item.deprecatedKeyCount),
    version: int(item.version, 1), updatedBy: optStr(item.updatedBy), updateTime: optStr(item.updateTime) };
}
export const decodeNamespaces = (value: unknown): Namespace[] => list(object(value).items).map(decodeNamespace);

const decodeProblem = (value: unknown): Problem => { const item = object(value); return { field: optStr(item.field) ?? '', message: optStr(item.message) ?? '' }; };
const decodeImportItem = (value: unknown): KeyImportItem => {
  const item = object(value);
  return { namespaceCode: str(item.namespaceCode), keyCode: str(item.keyCode), sourceText: optStr(item.sourceText) ?? '',
    previousSourceText: optStr(item.previousSourceText), description: optStr(item.description) };
};
export function decodeKeyImportPreview(value: unknown): KeyImportPreview {
  const item = object(value);
  return { importable: item.importable === true, previewDigest: str(item.previewDigest), keySetRevision: int(item.keySetRevision),
    added: list(item.added).map(decodeImportItem), sourceChanged: list(item.sourceChanged).map(decodeImportItem),
    deprecated: list(item.deprecated).map(decodeImportItem), restored: list(item.restored).map(decodeImportItem),
    unchangedCount: int(item.unchangedCount), missingNamespaces: strings(item.missingNamespaces), problems: list(item.problems).map(decodeProblem) };
}
export function decodeKeyImportResult(value: unknown): KeyImportResult {
  const item = object(value);
  return { addedCount: int(item.addedCount), sourceChangedCount: int(item.sourceChangedCount), deprecatedCount: int(item.deprecatedCount),
    restoredCount: int(item.restoredCount), descriptionChangedCount: int(item.descriptionChangedCount),
    keySetRevision: int(item.keySetRevision), contentRevision: int(item.contentRevision) };
}
export function decodeTranslationEntry(value: unknown): TranslationEntry {
  const item = object(value);
  return { namespaceCode: str(item.namespaceCode), keyCode: str(item.keyCode), sourceText: str(item.sourceText),
    description: optStr(item.description), parameters: strings(item.parameters), text: optStr(item.text), version: int(item.version),
    sourceChanged: item.sourceChanged === true, updatedBy: optStr(item.updatedBy), updateTime: optStr(item.updateTime) };
}
export function decodeTranslationPage(value: unknown): PagingResponseBody<TranslationEntry> {
  const item = object(value);
  return { items: list(item.items).map(decodeTranslationEntry), page: int(item.page), size: int(item.size), total: int(item.total) };
}
export function decodeSaveTranslations(value: unknown): SaveTranslationsResult {
  const item = object(value);
  return { translations: list(item.translations).map(object).map(row => ({ keyCode: str(row.keyCode), version: int(row.version) })),
    contentRevision: int(item.contentRevision) };
}
export function decodeImportTranslations(value: unknown): ImportTranslationsResult {
  const item = object(value);
  return { updatedCount: int(item.updatedCount), unchangedCount: int(item.unchangedCount), emptyCount: int(item.emptyCount), contentRevision: int(item.contentRevision) };
}
const decodeLocaleKey = (value: unknown): LocaleKey => { const item = object(value); return { locale: str(item.locale), keyCode: str(item.keyCode) }; };
export function decodePrecheck(value: unknown): ReleasePrecheck {
  const item = object(value);
  return { publishable: item.publishable === true, contentRevision: int(item.contentRevision), nextReleaseNo: int(item.nextReleaseNo, 1),
    activeKeyCount: int(item.activeKeyCount), coverage: list(item.coverage).map(decodeCoverage), missing: list(item.missing).map(decodeLocaleKey),
    missingTotal: int(item.missingTotal), sourceChanged: list(item.sourceChanged).map(decodeLocaleKey), sourceChangedTotal: int(item.sourceChangedTotal),
    problems: list(item.problems).map(decodeProblem), changeSummary: decodeChangeSummary(item.changeSummary) };
}
export function decodeReleasePage(value: unknown): PagingResponseBody<ReleaseSummary> {
  const item = object(value);
  return { items: list(item.items).map(decodeReleaseSummary), page: int(item.page), size: int(item.size), total: int(item.total) };
}
export function decodeReleaseDetail(value: unknown): ReleaseDetail {
  const item = object(value); const report = item.report ? object(item.report) : {};
  return { release: decodeReleaseSummary(item.release), basedOnContentRevision: int(item.basedOnContentRevision),
    coverage: list(item.coverage).map(decodeCoverage),
    report: { missing: list(report.missing).map(decodeLocaleKey), sourceChanged: list(report.sourceChanged).map(decodeLocaleKey) },
    bundles: list(item.bundles).map(object).map(row => ({ locale: str(row.locale), namespaceCode: str(row.namespaceCode),
      fileAddress: optStr(row.fileAddress) ?? '', contentHash: optStr(row.contentHash) ?? '', byteSize: int(row.byteSize) })),
    checkProgress: item.checkProgress ? (progress => ({ total: int(progress.total), available: int(progress.available), pending: int(progress.pending),
      rejected: int(progress.rejected) }))(object(item.checkProgress)) : null };
}
const decodeTextChange = (value: unknown): ReleaseTextChange => {
  const item = object(value);
  return { locale: str(item.locale), keyCode: str(item.keyCode), before: optStr(item.before), after: optStr(item.after) };
};
export function decodeReleaseDiff(value: unknown): ReleaseDiff {
  const item = object(value);
  return { releaseNo: int(item.releaseNo), baseReleaseNo: optInt(item.baseReleaseNo), added: list(item.added).map(decodeTextChange),
    changed: list(item.changed).map(decodeTextChange), removed: list(item.removed).map(decodeTextChange) };
}
export function decodeCurrentRelease(value: unknown): { applicationCode: string; releaseNo: number } {
  const item = object(value);
  return { applicationCode: str(item.applicationCode), releaseNo: int(item.releaseNo) };
}
export function decodeTranslationFile(value: unknown): TranslationFile {
  const item = object(value); const namespaces = object(item.namespaces);
  return { format: str(item.format), application: str(item.application), locale: str(item.locale),
    namespaces: Object.fromEntries(Object.entries(namespaces).map(([ns, entries]) => [ns,
      Object.fromEntries(Object.entries(object(entries)).filter(([, text]) => typeof text === 'string')) as Record<string, string>])) };
}
/** 无正文响应（删除命名空间）。 */
export const decodeNothing = (): null => null;
