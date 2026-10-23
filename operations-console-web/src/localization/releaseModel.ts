import { BASE_LOCALE } from './localizationApi.ts';
import type { ReleaseBundle, ReleaseChangeSummary, ReleaseDiff, ReleaseStatus, ReleaseSummary, ReleaseTextChange } from './localizationApi.ts';

/** 发布记录的展示状态（原型 05）：检查中、线上、已下线（READY 但不是当前）、检查失败。 */
export type ReleaseDisplay = 'checking' | 'live' | 'offline' | 'failed';
export function releaseDisplay(release: Pick<ReleaseSummary, 'status' | 'current'>): ReleaseDisplay {
  if (release.status === 'PENDING_CHECK') return 'checking';
  if (release.status === 'FAILED') return 'failed';
  return release.current ? 'live' : 'offline';
}

/** 轮询间隔：每 2 秒（原型 05"每 2 秒自动刷新"）。 */
export const POLL_INTERVAL_MS = 2000;
/** 轮询上限：检查通常几秒完成，超过 5 分钟停止并提示稍后刷新。 */
export const POLL_LIMIT_MS = 5 * 60 * 1000;
/** 轮询决定：检查中继续；READY / FAILED 终止；超过上限停止。 */
export function pollDecision(status: ReleaseStatus | null | undefined, elapsedMs: number): 'continue' | 'done' | 'timeout' {
  if (status !== 'PENDING_CHECK') return 'done';
  return elapsedMs >= POLL_LIMIT_MS ? 'timeout' : 'continue';
}
/** 列表中是否还有检查中的发布（需要继续刷新列表）。 */
export const hasPending = (releases: Pick<ReleaseSummary, 'status'>[]) => releases.some(release => release.status === 'PENDING_CHECK');

/** 上线进度四步（原型 05）：生成语言包、写入公开桶、检查文件、自动上线。 */
export type StepState = 'done' | 'active' | 'failed' | 'waiting';
export interface ProgressStep { key: 'generate' | 'store' | 'check' | 'activate'; state: StepState }
export function progressSteps(release: Pick<ReleaseSummary, 'status'>): ProgressStep[] {
  const check: StepState = release.status === 'PENDING_CHECK' ? 'active' : release.status === 'FAILED' ? 'failed' : 'done';
  return [
    { key: 'generate', state: 'done' },
    { key: 'store', state: 'done' },
    { key: 'check', state: check },
    // READY 即检查通过：若期间有人手动切到更新的发布，本发布只标 READY、不上线（technique 3.3），由调用方按 current 说明。
    { key: 'activate', state: release.status === 'READY' ? 'done' : 'waiting' },
  ];
}

/** 语言包摘要：语言列表、命名空间数、总字节。 */
export function bundleSummary(bundles: ReleaseBundle[]): { locales: string[]; namespaces: number; bytes: number; files: number } {
  const locales = [...new Set(bundles.map(bundle => bundle.locale))].sort((left, right) => left === BASE_LOCALE ? -1 : right === BASE_LOCALE ? 1 : left.localeCompare(right));
  return { locales, namespaces: new Set(bundles.map(bundle => bundle.namespaceCode)).size,
    bytes: bundles.reduce((sum, bundle) => sum + bundle.byteSize, 0), files: bundles.length };
}

/**
 * 两次发布的变更摘要（原型 05 右侧）：英文的新增 / 改动 / 删除即 key 的新增、英文改动与废弃；其他语言的新增与改动按语言合计为"译文修改"。
 * 其他语言的删除只在 key 被废弃时出现，已计入废弃，不重复计数。
 */
export interface ChangeRow { kind: 'added' | 'translation' | 'source' | 'removed'; locale: string; items: ReleaseTextChange[] }
export function changeRows(diff: ReleaseDiff): ChangeRow[] {
  const en = (items: ReleaseTextChange[]) => items.filter(item => item.locale === BASE_LOCALE);
  const removedKeys = new Set(en(diff.removed).map(item => item.keyCode));
  const rows: ChangeRow[] = [{ kind: 'added', locale: BASE_LOCALE, items: en(diff.added) }];
  const others = [...new Set([...diff.added, ...diff.changed, ...diff.removed].map(item => item.locale).filter(locale => locale !== BASE_LOCALE))].sort();
  for (const locale of others) {
    const items = [...diff.added, ...diff.changed, ...diff.removed]
      .filter(item => item.locale === locale && !removedKeys.has(item.keyCode));
    rows.push({ kind: 'translation', locale, items });
  }
  rows.push({ kind: 'source', locale: BASE_LOCALE, items: en(diff.changed) }, { kind: 'removed', locale: BASE_LOCALE, items: en(diff.removed) });
  return rows.filter(row => row.items.length > 0);
}
/** 变更总数（"63 处"）。 */
export const changeTotal = (rows: ChangeRow[]) => rows.reduce((sum, row) => sum + row.items.length, 0);

/**
 * 切换线上版本的影响（原型 06）：diff 以目标发布为对比方、当前线上为基准。
 * 当前有而目标没有的 key 显示内置英文；其他语言改动回到目标版本的译文。
 */
export interface SwitchImpact { missingKeys: number; extraKeys: number; translations: { locale: string; count: number; example: ReleaseTextChange | null }[] }
export function switchImpact(diff: ReleaseDiff): SwitchImpact {
  const en = (items: ReleaseTextChange[]) => items.filter(item => item.locale === BASE_LOCALE);
  const removedKeys = new Set(en(diff.removed).map(item => item.keyCode));
  const byLocale = new Map<string, ReleaseTextChange[]>();
  for (const item of [...diff.changed, ...diff.added, ...diff.removed]) {
    if (item.locale === BASE_LOCALE || removedKeys.has(item.keyCode)) continue;
    byLocale.set(item.locale, [...(byLocale.get(item.locale) ?? []), item]);
  }
  return { missingKeys: en(diff.removed).length, extraKeys: en(diff.added).length,
    translations: [...byLocale].sort(([left], [right]) => left.localeCompare(right)).map(([locale, items]) => ({ locale, count: items.length,
      example: items.find(item => item.before && item.after) ?? null })) };
}

/** 切换按钮文案：比当前线上旧为"切回"，更新为"上线"。 */
export const switchDirection = (target: number, current: number | null): 'back' | 'forward' => current !== null && target < current ? 'back' : 'forward';

/** 字节数展示：132 KB。 */
export function sizeText(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/**
 * 变更摘要行（原型 04、05）：新增 key、各语言译文修改、英文改动、废弃；数量为 0 的行不显示，样例 key 最多 20 条。
 */
export interface SummaryRow { kind: ChangeRow['kind']; locale: string; count: number; samples: string[] }
export function summaryRows(summary: ReleaseChangeSummary): SummaryRow[] {
  return [
    { kind: 'added' as const, locale: BASE_LOCALE, count: summary.addedKeyCount, samples: summary.addedKeys },
    ...summary.translationChanges.filter(item => item.locale !== BASE_LOCALE)
      .map(item => ({ kind: 'translation' as const, locale: item.locale, count: item.changedCount, samples: item.sampleKeys })),
    { kind: 'source' as const, locale: BASE_LOCALE, count: summary.sourceChangedKeyCount, samples: summary.sourceChangedKeys },
    { kind: 'removed' as const, locale: BASE_LOCALE, count: summary.deprecatedKeyCount, samples: summary.deprecatedKeys },
  ].filter(row => row.count > 0);
}
export const summaryTotal = (summary: ReleaseChangeSummary) => summaryRows(summary).reduce((sum, row) => sum + row.count, 0);
