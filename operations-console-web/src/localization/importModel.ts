import type { KeyImportItem, KeyImportPreview } from './localizationApi.ts';

/** key 导入文件（technique 3.5）：code-mor-i18n-source/1，单文件上限 2 MB，只允许 application/json。 */
export const SOURCE_FORMAT = 'code-mor-i18n-source/1';
export const TRANSLATION_FORMAT = 'code-mor-i18n-translation/1';
export const MAX_IMPORT_BYTES = 2 * 1024 * 1024;

export type LocalFileProblem = 'empty' | 'too_large' | 'not_json' | 'bad_format' | 'application_mismatch' | 'locale_mismatch';
export interface SourceFileSummary {
  problem: LocalFileProblem | null;
  /** 文件声明的应用（不一致时提示）。 */
  application: string | null;
  namespaces: { code: string; keys: number }[];
  keys: number;
}

/**
 * 上传前在浏览器读一遍文件：给出命名空间与 key 数（原型 03 文件行），并拦下明显不对的文件，避免白白上传。
 * 详细校验仍以 Platform 预览为准。
 */
export function summarizeImportFile(text: string, size: number, expected: { application: string; format: string; locale?: string }): SourceFileSummary {
  const empty: SourceFileSummary = { problem: null, application: null, namespaces: [], keys: 0 };
  if (size <= 0) return { ...empty, problem: 'empty' };
  if (size > MAX_IMPORT_BYTES) return { ...empty, problem: 'too_large' };
  let value: unknown;
  try { value = JSON.parse(text); } catch { return { ...empty, problem: 'not_json' }; }
  const root = value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null;
  const namespaces = root?.namespaces;
  if (!root || root.format !== expected.format || namespaces === null || typeof namespaces !== 'object' || Array.isArray(namespaces)) {
    return { ...empty, problem: 'bad_format' };
  }
  const counts = Object.entries(namespaces as Record<string, unknown>).map(([code, entries]) => ({ code,
    keys: entries !== null && typeof entries === 'object' && !Array.isArray(entries) ? Object.keys(entries).length : 0 }));
  const application = typeof root.application === 'string' ? root.application : null;
  const summary = { application, namespaces: counts, keys: counts.reduce((sum, item) => sum + item.keys, 0) };
  if (application !== expected.application) return { ...summary, problem: 'application_mismatch' };
  if (expected.locale !== undefined && root.locale !== expected.locale) return { ...summary, problem: 'locale_mismatch' };
  return { ...summary, problem: null };
}

/** 预览四类差异（原型 03 的变更摘要行），按新增、英文改动、代码已删除、重新出现排列，空类不显示。 */
export type ImportRowKind = 'added' | 'source_changed' | 'deprecated' | 'restored';
export function importRows(preview: KeyImportPreview): { kind: ImportRowKind; items: KeyImportItem[] }[] {
  return ([['added', preview.added], ['source_changed', preview.sourceChanged], ['deprecated', preview.deprecated], ['restored', preview.restored]] as const)
    .filter(([, items]) => items.length > 0).map(([kind, items]) => ({ kind, items: [...items] }));
}
export const importChangeTotal = (preview: KeyImportPreview) =>
  preview.added.length + preview.sourceChanged.length + preview.deprecated.length + preview.restored.length;

/**
 * 能否确认导入：Platform 判定可导入、没有未创建的命名空间与问题、预览未过期且确有变化。
 * 返回阻止原因，便于在底栏说明；null 表示可以确认。
 */
export type ImportBlocker = 'missing_namespaces' | 'problems' | 'stale' | 'no_changes' | 'not_importable';
export function importBlocker(preview: KeyImportPreview | null, stale: boolean): ImportBlocker | null {
  if (!preview) return 'not_importable';
  if (stale) return 'stale';
  if (preview.missingNamespaces.length) return 'missing_namespaces';
  if (preview.problems.length) return 'problems';
  if (!preview.importable) return 'not_importable';
  if (importChangeTotal(preview) === 0) return 'no_changes';
  return null;
}

/** 预览项展示：前 50 条，其余计数。 */
export const PREVIEW_LIST_LIMIT = 50;

/** 编码校验（新建应用 / 命名空间弹窗的即时提示）。 */
export type CodeProblem = 'required' | 'pattern' | 'duplicate';
export const applicationCodePattern = /^[a-z][a-z0-9-]{1,63}$/;
export const namespaceCodePattern = /^[a-z][a-z0-9-]{0,63}$/;
export function codeProblem(code: string, pattern: RegExp, existing: Iterable<string>): CodeProblem | null {
  if (!code) return 'required';
  if (!pattern.test(code)) return 'pattern';
  for (const item of existing) if (item === code) return 'duplicate';
  return null;
}
