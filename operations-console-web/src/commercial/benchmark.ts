import { percent } from './commercial.ts';
import type { AgreementCapability, MatrixRow, Price, PriceComponent } from './commercial.ts';
import type { CatalogCapability } from './commercialConsole.ts';
import type { ComponentBenchmark, RelevanceWarning } from './termReview.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('commercial');

// ---------- 破标标记 ----------

export interface BenchmarkMark { label: string; color: string; below: boolean }

/** 组件标记：低于来源模板为红色「破标」，无来源或模板无对应组件为「无标准可比」，不低于模板不标。 */
export function benchmarkMark(benchmark: ComponentBenchmark | null): BenchmarkMark | null {
  if (!benchmark || benchmark.status === 'AT_OR_ABOVE') return null;
  return benchmark.status === 'BELOW_STANDARD' ? { label: t('benchmark.below'), color: 'red', below: true } : { label: t('benchmark.none'), color: 'default', below: false };
}

/** 悬停显示的模板值：费率按百分比，金额原样；模板无值的字段不列。 */
export function benchmarkDetail(benchmark: ComponentBenchmark | null): string | null {
  if (!benchmark) return null;
  if (benchmark.status === 'NO_BENCHMARK') return t('benchmark.noComparable');
  const parts = [benchmark.standardRate !== null && t('benchmark.rate', { value: percent(benchmark.standardRate) }),
    benchmark.standardFixedAmount !== null && t('benchmark.fixed', { value: benchmark.standardFixedAmount }),
    benchmark.standardMinAmount !== null && t('benchmark.min', { value: benchmark.standardMinAmount }),
    benchmark.standardMaxAmount !== null && t('benchmark.max', { value: benchmark.standardMaxAmount })].filter(Boolean);
  return t('benchmark.templateValues', { values: parts.length ? parts.join(' · ') : '—' });
}

/** 一组价格里的破标组件数（版本未返回 belowStandardCount 时的兜底统计）。 */
export function belowStandardComponents(prices: readonly Pick<Price, 'components'>[]): number {
  return prices.reduce((sum, price) => sum + price.components.filter(component => component.benchmark?.status === 'BELOW_STANDARD').length, 0);
}

/** 一格（同一计费事件的多个组件）是否含破标组件。 */
export const cellBelowStandard = (components: readonly Pick<PriceComponent, 'benchmark'>[]) =>
  components.some(component => component.benchmark?.status === 'BELOW_STANDARD');

// ---------- 来源 ----------

/** 价格行来源标签：来自模板的行显示「模板名 vN」（版本级来源已知时），否则「标准价目」；手工行为 null。 */
export function sourceLabel(price: Pick<Price, 'sourceTemplatePriceId'>, templateLabel: string | null): string | null {
  return price.sourceTemplatePriceId ? templateLabel ?? t('source.standardTemplate') : null;
}

// ---------- 相关性提示 ----------

/** 原因显示名为 commercial 命名空间的 enum.relevanceReason.<编码>，未知编码原样显示。 */

/** 价格的定位键：OE × 币种 × 画像（与矩阵行键一致）。 */
export const priceKey = (item: { entityNo: string; currency: string; pricingProfileCode: string }) =>
  `${item.entityNo}|${item.currency}|${item.pricingProfileCode}`;

/** 一条提示：AE001 / AED / CARD_AMEX：支付方式未开通（commercial 说明）。 */
export function relevanceText(warning: RelevanceWarning): string {
  const params = { price: `${warning.entityNo} / ${warning.currency} / ${warning.pricingProfileCode}`, reason: enumLabel(t, 'enum.relevanceReason', warning.reasonCode) };
  return warning.message ? t('relevance.textWithMessage', { ...params, message: warning.message }) : t('relevance.text', params);
}

// ---------- 价格按开通项分组 ----------

export interface PriceGroup {
  key: string; title: string; capability: AgreementCapability | null; unmatched: boolean; rows: MatrixRow[]; warnings: RelevanceWarning[];
}

/** 画像条件与开通项接受条件相容：每个画像维度的取值集合与开通项同维度取值有交集；开通项未写的维度视为全集。 */
export function conditionsCompatible(price: Pick<Price, 'conditions'>, capability: Pick<AgreementCapability, 'conditions'>): boolean {
  const byDimension = new Map<string, Set<string>>();
  for (const condition of price.conditions) {
    const values = byDimension.get(condition.dimensionCode) ?? new Set<string>();
    values.add(condition.conditionValue);
    byDimension.set(condition.dimensionCode, values);
  }
  for (const [dimension, values] of byDimension) {
    const accepted = capability.conditions.find(item => item.dimensionCode === dimension);
    if (accepted && !accepted.values.some(value => values.has(value))) return false;
  }
  return true;
}

/** 价格是否对应开通项（与 commercial 相关性规则一致的近似）：同 OE、支付方式相等、币种在 CURRENCY 条件内、画像条件相容。 */
export function priceMatchesCapability(price: Pick<Price, 'entityNo' | 'currency' | 'paymentMethodCode' | 'conditions'>, capability: AgreementCapability,
  catalog: readonly Pick<CatalogCapability, 'capabilityCode' | 'paymentMethodCode'>[]): boolean {
  if (capability.entityNo !== price.entityNo || !price.paymentMethodCode) return false;
  const paymentMethodCode = catalog.find(item => item.capabilityCode === capability.capabilityCode)?.paymentMethodCode ?? capability.capabilityCode;
  if (paymentMethodCode !== price.paymentMethodCode) return false;
  const currencies = capability.conditions.find(item => item.dimensionCode === 'CURRENCY')?.values;
  if (currencies && !currencies.includes(price.currency)) return false;
  return conditionsCompatible(price, capability);
}

const unmatchedKey = 'UNMATCHED';
const otherKey = 'OTHER';

/**
 * 价格按开通项分组（编辑器展示用，最终以 commercial 相关性校验为准）：
 * <li>commercial 返回相关性提示的行、以及带支付方式却找不到对应开通项的行，单列「未对应开通项」并标红。</li>
 * <li>其余带支付方式的行归到第一个对应的开通项（ENABLED 优先）；无支付方式的价格（出款、外汇等）归「其他价格」。</li>
 * <li>分组顺序：合同开通项顺序 → 其他价格 → 未对应开通项；空分组不显示。</li>
 */
export function groupPricesByCapability(rows: readonly MatrixRow[], capabilities: readonly AgreementCapability[],
  catalog: readonly Pick<CatalogCapability, 'capabilityCode' | 'paymentMethodCode'>[], warnings: readonly RelevanceWarning[]): PriceGroup[] {
  const ordered = [...capabilities].sort((a, b) => Number(b.status === 'ENABLED') - Number(a.status === 'ENABLED'));
  const groups = new Map<string, PriceGroup>();
  const group = (key: string, make: () => Omit<PriceGroup, 'rows' | 'warnings'>) => {
    const existing = groups.get(key) ?? { ...make(), rows: [], warnings: [] };
    groups.set(key, existing);
    return existing;
  };
  for (const row of rows) {
    const rowWarnings = warnings.filter(warning => priceKey(warning) === row.key);
    const capability = rowWarnings.length ? undefined : ordered.find(item => priceMatchesCapability(row.price, item, catalog));
    if (capability) {
      group(capability.agreementCapabilityId, () => ({ key: capability.agreementCapabilityId, title: `${capability.entityNo} · ${capability.capabilityCode}`,
        capability, unmatched: false })).rows.push(row);
    } else if (!rowWarnings.length && !row.price.paymentMethodCode) {
      group(otherKey, () => ({ key: otherKey, title: t('priceGroup.other'), capability: null, unmatched: false })).rows.push(row);
    } else {
      const target = group(unmatchedKey, () => ({ key: unmatchedKey, title: t('priceGroup.unmatched'), capability: null, unmatched: true }));
      target.rows.push(row); target.warnings.push(...rowWarnings);
    }
  }
  const order = [...capabilities.map(item => item.agreementCapabilityId), otherKey, unmatchedKey];
  return order.map(key => groups.get(key)).filter((item): item is PriceGroup => !!item);
}
