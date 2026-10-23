import type { LocaleCoverage, ReleasePrecheck, TranslationEntry, TranslationState } from './localizationApi.ts';
import { validateTranslation } from './icu.ts';
import type { IcuIssue } from './icu.ts';

/** 翻译页签：全部 / 未翻译 / 基准已变更（原型 01）。 */
export type TranslationTab = 'all' | 'untranslated' | 'source_changed';
export const translationTabs: TranslationTab[] = ['all', 'untranslated', 'source_changed'];
export const tabState: Record<TranslationTab, TranslationState | null> = { all: null, untranslated: 'UNTRANSLATED', source_changed: 'SOURCE_CHANGED' };

/** 页签计数取目标语言的覆盖率：全部 = 在用 key；未翻译 = 全部 - 已翻译；基准已变更 = 沿用旧译文的条数。 */
export function tabCounts(coverage: LocaleCoverage | undefined): Record<TranslationTab, number> | null {
  if (!coverage) return null;
  return { all: coverage.totalKeys, untranslated: Math.max(coverage.totalKeys - coverage.translatedKeys, 0), source_changed: coverage.sourceChangedKeys };
}

/** key 所属命名空间：第一段。 */
export const namespaceOf = (keyCode: string) => keyCode.split('.')[0];

/** 列表状态图标：已翻译、未翻译、基准已变更、编辑中（有未保存修改）、有错误。 */
export type EntryStatus = 'translated' | 'untranslated' | 'source_changed' | 'editing' | 'error';
export function entryStatus(entry: TranslationEntry, draft?: string): EntryStatus {
  const text = draft ?? entry.text ?? '';
  const dirty = draft !== undefined && draft !== (entry.text ?? '');
  if (dirty && text.trim() && validateTranslation(entry.sourceText, text).length) return 'error';
  if (!dirty && entry.text && validateTranslation(entry.sourceText, entry.text).length) return 'error';
  if (dirty) return 'editing';
  if (!entry.text) return 'untranslated';
  return entry.sourceChanged ? 'source_changed' : 'translated';
}

/** 编辑器状态：保存按钮是否可用、底栏提示。 */
export interface EditorState {
  issues: IcuIssue[];
  dirty: boolean;
  /** 能保存：有修改且无问题；或基准已变更时原样确认沿用。 */
  canSave: boolean;
  /** 原样确认沿用（只更新所依据的基准修订）。 */
  confirmOnly: boolean;
}
export function editorState(entry: TranslationEntry, draft: string): EditorState {
  const dirty = draft !== (entry.text ?? '');
  const issues = draft.trim() || dirty ? validateTranslation(entry.sourceText, draft) : [];
  const confirmOnly = !dirty && entry.sourceChanged && !!entry.text && issues.length === 0;
  return { issues, dirty, canSave: (dirty && issues.length === 0) || confirmOnly, confirmOnly };
}

/** 上一条 / 下一条；到头时停在原处，当前不在列表时取第一条。 */
export function siblingKey(entries: TranslationEntry[], current: string | null, direction: 1 | -1): string | null {
  if (!entries.length) return null;
  const index = entries.findIndex(entry => entry.keyCode === current);
  if (index < 0) return entries[0].keyCode;
  return entries[Math.min(Math.max(index + direction, 0), entries.length - 1)].keyCode;
}
/** 保存后跳到的下一条：同列表下一条；已是最后一条时为 null（停在原处）。 */
export function nextAfterSave(entries: TranslationEntry[], current: string): string | null {
  const index = entries.findIndex(entry => entry.keyCode === current);
  return index >= 0 && index < entries.length - 1 ? entries[index + 1].keyCode : null;
}

/** 预检问题字段 translations.{locale}.{keyCode} → 定位目标；其他字段返回 null。 */
export function problemTarget(field: string): { locale: string; keyCode: string } | null {
  const match = /^translations\.([^.]+)\.(.+)$/.exec(field);
  return match ? { locale: match[1], keyCode: match[2] } : null;
}

/** 发布前问题（原型 02 的"N 个问题"浮层）。 */
export interface IssueGroups {
  /** 译文错误：按 key 逐条，可定位。 */
  errors: { locale: string; keyCode: string; message: string }[];
  /** 必须覆盖语言未达标：缺口数、要求、按命名空间分布（来自预检前 200 条）。 */
  coverageGaps: { locale: string; missing: number; threshold: number; percent: number; byNamespace: { namespace: string; count: number }[] }[];
  /** 基准已变更沿用旧译文：不阻止发布。 */
  sourceChangedTotal: number;
  /** 没有在用 key：不能发布。 */
  noKeys: boolean;
  /** 阻止发布的问题组数（页头"N 个问题"）。 */
  blocking: number;
}
export function issueGroups(precheck: ReleasePrecheck | null, threshold: number): IssueGroups | null {
  if (!precheck) return null;
  const errors = precheck.problems.flatMap(problem => {
    const target = problemTarget(problem.field);
    return target ? [{ ...target, message: problem.message }] : [];
  });
  const coverageGaps = precheck.coverage.filter(item => item.required && !item.satisfied).map(item => {
    const counts = new Map<string, number>();
    for (const missing of precheck.missing) {
      if (missing.locale !== item.locale) continue;
      const namespace = namespaceOf(missing.keyCode);
      counts.set(namespace, (counts.get(namespace) ?? 0) + 1);
    }
    return { locale: item.locale, missing: Math.max(item.totalKeys - item.translatedKeys, 0), threshold, percent: item.coveragePercent,
      byNamespace: [...counts].map(([namespace, count]) => ({ namespace, count })).sort((left, right) => right.count - left.count || left.namespace.localeCompare(right.namespace)) };
  });
  const noKeys = precheck.activeKeyCount === 0;
  const unexplained = !precheck.publishable && !errors.length && !coverageGaps.length && !noKeys && precheck.problems.length > 0;
  const blocking = (errors.length || unexplained ? 1 : 0) + coverageGaps.length + (noKeys ? 1 : 0);
  return { errors: unexplained ? precheck.problems.map(problem => ({ locale: '', keyCode: problem.field, message: problem.message })) : errors,
    coverageGaps, sourceChangedTotal: precheck.sourceChangedTotal, noKeys, blocking };
}

/**
 * 把正在编辑的译文错误并入问题浮层（原型 02）：预检只看已保存的文案，但运营正在改的这条有错误时同样需要在页头提示。
 * 同一条已在预检问题中时不重复。
 */
export function withDraftError(groups: IssueGroups | null, draft: { locale: string; keyCode: string; message: string } | null): IssueGroups | null {
  if (!groups || !draft || groups.errors.some(item => item.locale === draft.locale && item.keyCode === draft.keyCode)) return groups;
  const errors = [draft, ...groups.errors];
  return { ...groups, errors, blocking: 1 + groups.coverageGaps.length + (groups.noKeys ? 1 : 0) };
}

/**
 * 未发布的修改次数：文案修订号相对线上发布所依据的修订号之差（每次保存、导入各计一次）。
 * 没有线上发布时为当前修订号；修订号缺失时为 null。
 */
export function unpublishedRevisions(contentRevision: number, basedOn: number | null): number {
  return Math.max(contentRevision - (basedOn ?? 0), 0);
}

/** 保存失败 422 的字段错误只取本条（translations[0].text）的说明。 */
export function saveErrorMessages(errors: readonly { field: string; message: string }[]): string[] {
  return errors.filter(item => /^translations\[0]\.text$/.test(item.field) || item.field === 'translations').map(item => item.message);
}

/** 关键字过滤在服务端；本地只做去抖后的规范化。 */
export const normalizeKeyword = (value: string) => value.trim().slice(0, 100);

// ---------- 批量保存（批量粘贴） ----------
export interface TranslationInputItem { keyCode: string; text: string; expectedVersion: number }
/** Gateway 写 Body 上限 64 KiB；按字节留余量取 48 KB，并且每批不超过合同的 200 条。 */
export const BATCH_MAX_BYTES = 48 * 1024;
export const BATCH_MAX_ITEMS = 200;
const bodyBytes = (locale: string, items: TranslationInputItem[]) => new TextEncoder().encode(JSON.stringify({ locale, translations: items })).length;
/** 按顺序切分批次：每批请求体 ≤ maxBytes 且 ≤ maxItems；单条本身超限时独占一批（由服务端拒绝并报告）。 */
export function splitBatches(locale: string, items: TranslationInputItem[], maxBytes = BATCH_MAX_BYTES, maxItems = BATCH_MAX_ITEMS): TranslationInputItem[][] {
  const batches: TranslationInputItem[][] = []; let current: TranslationInputItem[] = [];
  for (const item of items) {
    const next = [...current, item];
    if (current.length && (next.length > maxItems || bodyBytes(locale, next) > maxBytes)) { batches.push(current); current = [item]; }
    else current = next;
  }
  if (current.length) batches.push(current);
  return batches;
}
/** 一批失败时的字段错误 translations[i].text → 对应 key（422 I18N_MESSAGE_INVALID / VALIDATION_ERROR）。 */
export function batchErrorTargets(errors: readonly { field: string; message: string }[], batch: TranslationInputItem[]): { keyCode: string | null; message: string }[] {
  return errors.map(error => {
    const match = /^translations\[(\d+)]/.exec(error.field);
    return { keyCode: match ? batch[Number(match[1])]?.keyCode ?? null : null, message: error.message };
  });
}

/**
 * 批量粘贴的内容：JSON（扁平 {key: 译文}，或导出格式 {namespaces: {ns: {key: 译文}}}），或每行"key<Tab>译文"。
 * 空译文跳过；同一 key 重复时以最后一次为准；无法识别的行计入 invalidLines（行号从 1 开始）。
 */
export function parsePaste(text: string): { entries: { keyCode: string; text: string }[]; invalidLines: number[] } {
  const trimmed = text.trim();
  const map = new Map<string, string>(); const invalidLines: number[] = [];
  const add = (key: string, value: unknown) => { if (typeof value === 'string' && value.trim()) map.set(key.trim(), value); };
  if (trimmed.startsWith('{')) {
    try {
      const root = JSON.parse(trimmed) as Record<string, unknown>;
      const namespaces = root.namespaces;
      if (namespaces && typeof namespaces === 'object' && !Array.isArray(namespaces)) {
        for (const entries of Object.values(namespaces as Record<string, unknown>)) {
          if (entries && typeof entries === 'object') for (const [key, value] of Object.entries(entries as Record<string, unknown>)) add(key, value);
        }
      } else for (const [key, value] of Object.entries(root)) add(key, value);
      return { entries: [...map].map(([keyCode, value]) => ({ keyCode, text: value })), invalidLines };
    } catch { /* 不是合法 JSON 时按行解析 */ }
  }
  text.split('\n').forEach((line, index) => {
    if (!line.trim()) return;
    const tab = line.indexOf('\t');
    const key = tab > 0 ? line.slice(0, tab).trim() : '';
    if (!/^[a-z][a-z0-9-]*(\.[A-Za-z0-9_-]+)+$/.test(key)) { invalidLines.push(index + 1); return; }
    add(key, line.slice(tab + 1).replace(/\r$/, ''));
  });
  return { entries: [...map].map(([keyCode, value]) => ({ keyCode, text: value })), invalidLines };
}

/** 翻译列表每次加载的条数：首屏一次请求，其余按需加载（列表路由每用户 30 次 / 分钟）。 */
export const TRANSLATION_PAGE_SIZE = 100;
/** 批量粘贴核对时最多发出的列表请求数（每页 200 条），超过时请用户按命名空间分批，避免触发限流。 */
export const PASTE_PAGE_BUDGET = 20;
const PASTE_PAGE_SIZE = 200;

/** 批量粘贴的核对计划：只读取粘贴内容涉及的命名空间，并按各命名空间的 key 数估算请求页数。 */
export function pastePlan(keyCodes: string[], namespaces: { namespaceCode: string; activeKeyCount: number }[]) {
  const counts = new Map(namespaces.map(item => [item.namespaceCode, item.activeKeyCount]));
  const involved = [...new Set(keyCodes.map(namespaceOf))].filter(code => counts.has(code)).sort();
  const pages = involved.reduce((sum, code) => sum + Math.max(1, Math.ceil((counts.get(code) ?? 0) / PASTE_PAGE_SIZE)), 0);
  return { namespaces: involved, pages, tooLarge: pages > PASTE_PAGE_BUDGET };
}
