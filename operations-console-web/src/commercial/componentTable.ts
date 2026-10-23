import { priceDraft } from './commercial.ts';
import type { BandDraft, ComponentDraft, ComponentType, Price, PriceDraft } from './commercial.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('commercial');

// ---------- 百分比 ↔ 小数 ----------

const unsignedDecimal = /^[0-9]+(\.[0-9]+)?$/;
const percentPattern = /^(0|[1-9][0-9]{0,2})(\.[0-9]{1,8})?$/;

/** 去掉整数部分前导零、小数部分末尾零，拼回十进制字符串。 */
function joinDecimal(integer: string, fraction: string): string {
  const whole = integer.replace(/^0+(?=\d)/, '') || '0';
  const rest = fraction.replace(/0+$/, '');
  return rest ? `${whole}.${rest}` : whole;
}

/** 小数费率 → 百分比输入：0.025 → 2.5；按字符串移动小数点，不经浮点。空值原样返回，非数字原样返回交给校验。 */
export function percentInput(rate: string | null | undefined): string | null {
  if (rate === null || rate === undefined) return null;
  const value = rate.trim();
  if (!unsignedDecimal.test(value)) return rate;
  const [integer, fraction = ''] = value.split('.');
  const padded = fraction.padEnd(2, '0');
  return joinDecimal(`${integer}${padded.slice(0, 2)}`, padded.slice(2));
}

/** 百分比输入 → 小数费率：2.2 → 0.022；按字符串移动小数点，不经浮点。空值原样返回，非数字原样返回交给校验。 */
export function rateFromPercent(value: string | null | undefined): string | null {
  if (value === null || value === undefined) return null;
  const trimmed = value.trim();
  if (!unsignedDecimal.test(trimmed)) return value;
  const [integer, fraction = ''] = trimmed.split('.');
  const padded = integer.padStart(2, '0');
  return joinDecimal(padded.slice(0, -2), `${padded.slice(-2)}${fraction}`);
}

/** 百分比输入校验：0–100，最多 8 位小数（换算后接口费率最多 10 位）；空值不在此处判断。返回问题或 null。 */
export function percentIssue(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  if (!trimmed) return null;
  if (!percentPattern.test(trimmed)) return t('percent.issue');
  const [integer, fraction = ''] = trimmed.split('.');
  const overHundred = Number(integer) > 100 || (integer === '100' && /[1-9]/.test(fraction));
  return overHundred ? t('percent.issue') : null;
}

/** 主单位十进制金额：最多 16 位整数、8 位小数。 */
export const amountPattern = /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/;

// ---------- 币种推导与草稿换算 ----------

const filled = (value: string | null | undefined) => Boolean(value?.trim());

/** 组件金额币种：固定额、阶梯、填了上下限的百分比取价格币种，其余为 null（commercial 要求与价格币种一致）。 */
export function componentCurrency(component: Pick<ComponentDraft, 'componentType' | 'minAmount' | 'maxAmount'>, priceCurrency: string): string | null {
  if (component.componentType === 'FIXED' || component.componentType === 'TIERED') return priceCurrency;
  return filled(component.minAmount) || filled(component.maxAmount) ? priceCurrency : null;
}

const mapBands = (bands: BandDraft[] | null, convert: (rate: string | null) => string | null) =>
  bands ? bands.map(band => ({ ...band, rate: convert(band.rate) })) : null;

/** 表单草稿 → 提交草稿：组件与档位费率由百分比换回小数，并推导组件币种；结果交给 pricesBody。 */
export function toSubmitDrafts(drafts: readonly PriceDraft[]): PriceDraft[] {
  return drafts.map(draft => ({
    ...draft,
    components: (draft.components ?? []).map(component => ({
      ...component, rate: rateFromPercent(component.rate), currency: componentCurrency(component, draft.currency),
      bands: mapBands(component.bands, rateFromPercent),
    })),
  }));
}

/** 读模型 → 表单草稿：priceDraft 之后把组件与档位费率换成百分比。 */
export function toFormDrafts(prices: readonly Price[]): PriceDraft[] {
  return prices.map(priceDraft).map(draft => ({
    ...draft,
    components: draft.components.map(component => ({ ...component, rate: percentInput(component.rate), bands: mapBands(component.bands, percentInput) })),
  }));
}

// ---------- 行操作与摘要 ----------

const copySuffix = '_COPY';

/** 复制行的组件代码：追加 _COPY，超出 64 位时截断原代码。 */
export function copiedComponentCode(code: string): string {
  return `${(code ?? '').slice(0, 64 - copySuffix.length)}${copySuffix}`;
}

/** 复制一个组件草稿（档位深拷贝），组件代码追加 _COPY 待改。 */
export function copyComponentDraft(component: ComponentDraft): ComponentDraft {
  return { ...component, componentCode: copiedComponentCode(component.componentCode), bands: component.bands ? component.bands.map(band => ({ ...band })) : null };
}

/** 新增组件的默认草稿：固定额、每笔固定、不退。 */
export function newComponentDraft(): ComponentDraft {
  return { componentCode: '', componentType: 'FIXED', triggerCode: 'CAPTURE', calculationBase: 'FIXED_PER_EVENT', refundBehavior: 'KEEP', rate: null,
    fixedAmount: null, currency: null, minAmount: null, maxAmount: null, bandBasis: null, bandMode: null, bands: null };
}

/** 切换为阶梯时补齐默认值：至少一档（下界 0）、交易金额基数、整笔适用；其他类型不改字段（无关字段由 pricesBody 置空）。 */
export function componentTypePatch(type: ComponentType, current: Pick<ComponentDraft, 'bands' | 'bandBasis' | 'bandMode'>): Partial<ComponentDraft> {
  if (type !== 'TIERED') return {};
  return {
    bands: current.bands?.length ? current.bands : [{ lowerBound: '0', upperBound: null, rate: null, fixedAmount: null }],
    bandBasis: current.bandBasis ?? 'TRANSACTION_AMOUNT', bandMode: current.bandMode ?? 'WHOLE_BASE',
  };
}

/** 价格卡片摘要：价格 N · OE · 币种 · 画像 · 优先级 P · K 个组件；未填项显示「—」。 */
export function priceSummary(draft: Partial<PriceDraft> | undefined, index: number): string {
  const text = (value: string | number | null | undefined) => value === null || value === undefined || value === '' ? '—' : String(value);
  return t('price.summary', { no: index + 1, entityNo: text(draft?.entityNo), currency: text(draft?.currency), profile: text(draft?.pricingProfileCode),
    priority: text(draft?.priorityNo), count: draft?.components?.length ?? 0 });
}

/** 阶梯单元格文案：N 档 · 模式显示名。 */
export function tierSummary(bands: readonly unknown[] | null | undefined, bandMode: string | null | undefined): string {
  return t('tier.summary', { count: bands?.length ?? 0, mode: bandMode ? enumLabel(t, 'enum.bandMode', bandMode) : t('tier.noMode') });
}

// ---------- 枚举标签与可编辑列 ----------

/** 组件字段的枚举：显示名为 commercial 命名空间的 enum.<种类>.<值>。 */
export type ComponentEnum = 'calculationBase' | 'refundBehavior' | 'bandBasis' | 'bandMode';

export interface LabeledOption { value: string; label: string }

/** 下拉选项：label 为显示名（缺失时用代码），代码由页面作为副文字显示。调用时取文案。 */
export function labeledOptions(values: readonly string[], kind: ComponentEnum): LabeledOption[] {
  return values.map(value => ({ value, label: enumLabel(t, 'enum.' + kind, value) }));
}

export type ComponentColumn = 'rate' | 'fixedAmount' | 'minAmount' | 'maxAmount' | 'bands';

/** 组件类型 → 可编辑的金额列；其余金额列显示「—」不可编辑，保存时由 pricesBody 置空。 */
export const editableColumns: Readonly<Record<ComponentType, readonly ComponentColumn[]>> = {
  PERCENTAGE: ['rate', 'minAmount', 'maxAmount'], FIXED: ['fixedAmount'], TIERED: ['bands'],
};

export function isEditable(type: ComponentType | undefined, column: ComponentColumn): boolean {
  return type ? editableColumns[type].includes(column) : false;
}
