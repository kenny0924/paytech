import { BASE_LOCALE } from './localizationApi.ts';
import type { Application, Locale, LocaleCoverage, Namespace } from './localizationApi.ts';

/** 发布要求可选值（原型 07）；已有应用的其他取值保留并一并展示。 */
export const thresholdOptions = [100, 95, 90];
export const thresholdChoices = (current: number) => [...new Set([...thresholdOptions, current])].sort((left, right) => right - left);

export interface SettingsDraft { defaultLocale: string; requiredLocales: string[]; coverageThresholdPercent: number }
export const draftOf = (application: Application): SettingsDraft => ({ defaultLocale: application.defaultLocale,
  requiredLocales: application.requiredLocales.filter(code => code !== BASE_LOCALE), coverageThresholdPercent: application.coverageThresholdPercent });
export function settingsDirty(application: Application, draft: SettingsDraft): boolean {
  const saved = draftOf(application);
  return saved.defaultLocale !== draft.defaultLocale || saved.coverageThresholdPercent !== draft.coverageThresholdPercent
    || [...saved.requiredLocales].sort().join(',') !== [...draft.requiredLocales].sort().join(',');
}

/** 设置面板可选语言：ENABLED，按排序值；en 恒为基准。 */
export const enabledLocales = (locales: Locale[]) => locales.filter(locale => locale.status === 'ENABLED')
  .toSorted((left, right) => left.sortOrder - right.sortOrder || left.localeCode.localeCompare(right.localeCode));

/**
 * 即时是否满足（原型 07"简体中文当前 100%，满足要求"）：按草稿的必须语言与阈值，用当前覆盖率计算；
 * 与 Platform 规则一致：已翻译 × 100 ≥ 阈值 × 总数。
 */
export interface CoverageCheck { locale: string; percent: number; satisfied: boolean; missing: number }
export function coverageChecks(coverage: LocaleCoverage[], draft: SettingsDraft): CoverageCheck[] {
  return draft.requiredLocales.map(locale => {
    const item = coverage.find(entry => entry.locale === locale);
    const total = item?.totalKeys ?? 0; const translated = item?.translatedKeys ?? 0;
    const percent = total === 0 ? 100 : Math.floor(translated * 100 / total);
    return { locale, percent, satisfied: translated * 100 >= draft.coverageThresholdPercent * total, missing: Math.max(total - translated, 0) };
  });
}

/** 应用列表"覆盖"列：必须语言中最低的覆盖率；没有必须语言或没有 key 时为 null。 */
export function lowestRequiredCoverage(coverage: LocaleCoverage[]): LocaleCoverage | null {
  const required = coverage.filter(item => item.required && item.locale !== BASE_LOCALE && item.totalKeys > 0);
  return required.toSorted((left, right) => left.coveragePercent - right.coveragePercent)[0] ?? null;
}

/** 新命名空间默认排在最后：现有最大排序值 + 10，上限 9999。 */
export const nextSortOrder = (namespaces: Pick<Namespace, 'sortOrder'>[]) =>
  Math.min(namespaces.reduce((max, item) => Math.max(max, item.sortOrder), 0) + 10, 9999);
