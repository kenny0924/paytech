import { ApiError } from '../api/session.ts';
import { joinList, offsetInstant, optionalInstant } from './commercial.ts';
import { enumLabel, i18n, tOr, translator } from '../i18n/index.ts';
import type { AgreementCapability, CapabilityStatus } from './commercial.ts';
import type { CapabilityDimension, CatalogCapability } from './commercialConsole.ts';
import { percentInput, percentIssue, rateFromPercent } from './componentTable.ts';
import type { CapabilityCondition } from './termReview.ts';

const t = translator('commercial');

// ---------- 维度清单（commercial GET /products/dimensions，代码登记、只读） ----------

/** 维度元数据：事实来源、判定时点、可用于开通（接受条件）/ 定价。 */
export interface DimensionDefinition {
  dimensionCode: string; displayName: string; factSource: string; evaluationPoint: string; acceptanceAllowed: boolean; pricingAllowed: boolean;
}

/** 已知维度的显示名为 commercial 命名空间的 enum.dimension.<代码>；未登记的维度用维度清单的 displayName。 */

/** 常见取值建议（只作下拉建议，仍可输入其他代码）。 */
export const dimensionSuggestions: Readonly<Record<string, readonly string[]>> = {
  CURRENCY: ['AED', 'USD', 'SAR', 'EUR', 'GBP'], CONSUMER_COUNTRY: ['AE', 'SA', 'US', 'GB'], ISSUER_COUNTRY: ['AE', 'SA', 'US', 'GB'],
  DESTINATION_COUNTRY: ['AE', 'SA', 'US', 'GB'], CARD_BRAND: ['VISA', 'MASTERCARD', 'AMEX', 'UNIONPAY', 'MADA', 'DISCOVER', 'JCB', 'DINERS'],
  FUNDING_TYPE: ['CREDIT', 'DEBIT', 'PREPAID', 'CHARGE'], CARD_LEVEL: ['CLASSIC', 'GOLD', 'PLATINUM', 'SIGNATURE', 'INFINITE', 'WORLD', 'WORLD_ELITE'],
  CARD_PRODUCT: ['CONSUMER', 'COMMERCIAL'], ISSUER_REGION: ['GCC', 'MEA', 'EUROPE', 'NORTH_AMERICA', 'LATAM', 'APAC'], CURRENCY_PAIR: ['USD/AED', 'EUR/AED'],
};

// TODO by CLAUDE: 币种改为字典维护后替换此常量
/** 币种下拉的候选（暂为写死的常用币种，后续改为字典维护）；名称为 commercial 命名空间的 enum.currencyName.<代码>。 */
export const currencyCodes: readonly string[] = ['AED', 'USD', 'SAR', 'EUR', 'GBP', 'KWD', 'QAR', 'BHD', 'OMR', 'EGP', 'JOD', 'INR', 'PKR', 'CNY', 'HKD',
  'JPY', 'SGD', 'CHF', 'CAD', 'AUD'];

/** 币种快捷标签之外的部分：快捷标签（AED、USD…）各自点选，其余币种由「更多币种」下拉选择。 */
const isQuickCurrency = (code: string) => (dimensionSuggestions.CURRENCY ?? []).includes(code);

/** 拆分币种取值：quick 为快捷标签币种、more 为下拉部分；各自保持原数组顺序，规范化去重，不丢值。 */
export function splitCurrencies(values: readonly string[] | null | undefined): { quick: string[]; more: string[] } {
  const all = normalizeValues(values);
  return { quick: all.filter(isQuickCurrency), more: all.filter(code => !isQuickCurrency(code)) };
}

/** 合并回一个数组：先快捷、后下拉选择的顺序，去重。 */
export function mergeCurrencies(quick: readonly string[], more: readonly string[]): string[] {
  return normalizeValues([...quick, ...more]);
}

/** 「更多币种」下拉选项：常用币种中除快捷标签以外的，加上已选但不在清单中的币种（编辑回填不丢）；文字「KWD 科威特第纳尔」。 */
export function currencySelectOptions(values: readonly string[] | null | undefined): { value: string; label: string }[] {
  const codes = [...currencyCodes.filter(code => !isQuickCurrency(code)),
    ...splitCurrencies(values).more.filter(code => !currencyCodes.includes(code))];
  return codes.map(code => ({ value: code, label: codeWithName(code, 'enum.currencyName.' + code) }));
}

/** 已列出名称的国家代码（国家类维度的选项；名称为 commercial 命名空间的 enum.country.<代码>，未列出的合法代码原样显示）。 */
export const countryCodes: readonly string[] = ['AE', 'SA', 'KW', 'QA', 'BH', 'OM', 'EG', 'JO', 'US', 'GB', 'DE', 'FR', 'IN', 'CN', 'SG'];

/** 「代码 名称」；没有名称时只有代码。 */
function codeWithName(code: string, key: string): string {
  return t.has(key) ? `${code} ${t(key)}` : code;
}

/** 国家选项文字「AE 阿联酋」；未列出的代码原样。 */
export function countryOptionLabel(code: string): string {
  return codeWithName(code, 'enum.country.' + code);
}

/** 标签上的取值文字：国家为「AE 阿联酋」（与抽屉选项一致），枚举为显示名，币种等其余为代码。 */
export function conditionTagValue(dimensionCode: string, value: string): string {
  if (dimensionCode.endsWith('_COUNTRY')) return countryOptionLabel(value);
  return conditionValueLabel(dimensionCode, value);
}

/** 3DS 要求的条件标签文字。 */
export const require3dsTag = () => t('condition.require3ds');

/** 取值显示名：枚举型取值（enum.conditionValue.<维度>.<值>）/ 国家名，其余原样。 */
export function conditionValueLabel(dimensionCode: string, value: string): string {
  if (dimensionCode.endsWith('_COUNTRY')) return enumLabel(t, 'enum.country', value);
  return enumLabel(t, 'enum.conditionValue.' + dimensionCode, value);
}

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const text = (v: unknown, max = 256) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

export function decodeDimensions(value: unknown): DimensionDefinition[] {
  const v = value as { dimensions: unknown[] };
  require(object(v) && Array.isArray(v.dimensions));
  return v.dimensions.map(item => {
    const d = item as DimensionDefinition;
    require(object(d) && text(d.dimensionCode, 64) && typeof d.acceptanceAllowed === 'boolean' && typeof d.pricingAllowed === 'boolean');
    return { dimensionCode: d.dimensionCode, displayName: text(d.displayName, 64) ? d.displayName : d.dimensionCode,
      factSource: typeof d.factSource === 'string' ? d.factSource : '', evaluationPoint: typeof d.evaluationPoint === 'string' ? d.evaluationPoint : '',
      acceptanceAllowed: d.acceptanceAllowed, pricingAllowed: d.pricingAllowed };
  });
}

/** 维度显示名：中文界面为维度清单 displayName > 界面文案 > 原代码；其他语言为界面文案（enum.dimension.<代码>）> displayName > 原代码。 */
export function dimensionLabel(code: string, definitions: readonly DimensionDefinition[] = []): string {
  const server = definitions.find(item => item.dimensionCode === code)?.displayName;
  // 维度清单的名称是中文：中文界面沿用迁移前的优先级（清单名称 > 本地名称），其他语言先用界面翻译。
  if (server && server !== code && i18n.locale() === 'zh-CN') return server;
  return tOr(t, 'enum.dimension.' + code, server ?? code);
}

// ---------- 接受条件取值 ----------

/**
 * 条件控件：币种为候选标签 + 其他（必填，无「不限」）；国家类为可搜索多选（空即不限）；
 * 有常见取值的枚举型（卡组织、借贷记类型、卡等级…）平铺可选标签，首个为「不限」；币对等其余维度为标签输入。
 */
export type ConditionControl = 'CURRENCY' | 'COUNTRY' | 'ENUM' | 'TAGS';
export function conditionControl(dimensionCode: string): ConditionControl {
  if (dimensionCode === 'CURRENCY') return 'CURRENCY';
  if (dimensionCode.endsWith('_COUNTRY')) return 'COUNTRY';
  return dimensionCode !== 'CURRENCY_PAIR' && dimensionSuggestions[dimensionCode]?.length ? 'ENUM' : 'TAGS';
}

/** 「不限」：未选任何取值。 */
export const unrestricted = (values: readonly string[] | null | undefined) => normalizeValues(values).length === 0;

/** 点选一个取值：已选则取消，未选则追加（规范化后去重）；点「不限」由调用方清空为 []。 */
export function toggleConditionValue(values: readonly string[] | null | undefined, value: string): string[] {
  const current = normalizeValues(values);
  const next = value.trim().toUpperCase();
  if (!next) return current;
  return current.includes(next) ? current.filter(item => item !== next) : [...current, next];
}

/** 「+ 其他」输入：追加一个取值（已选则不变）。 */
export function addConditionValue(values: readonly string[] | null | undefined, value: string): string[] {
  return normalizeValues([...(values ?? []), value]);
}

/** 平铺选项：建议值在前，已选的列表外取值随后（编辑回填不丢）。 */
export function conditionOptions(dimensionCode: string, values: readonly string[] | null | undefined): string[] {
  const suggestions = dimensionSuggestions[dimensionCode] ?? [];
  return [...suggestions, ...normalizeValues(values).filter(value => !suggestions.includes(value))];
}

/** 条件区的显示顺序：必填维度在前（目前即币种），其余保持目录顺序；不影响提交顺序。 */
export function orderedDimensions(dimensions: readonly CapabilityDimension[]): CapabilityDimension[] {
  return [...dimensions.filter(item => item.required), ...dimensions.filter(item => !item.required)];
}

/**
 * 条件区底部的一句话汇总：「接受：币种 AED、USD · 借贷记类型 借记 · 卡组织不限 · 要求 3DS」。
 * 顺序同条件区（必填在前，其余目录顺序）；空的必填维度写「未选」；没有可配维度时为「接受：不限」。币种显示代码。
 */
export function conditionSummary(dimensions: readonly CapabilityDimension[], form: ConditionForm | undefined, require3ds: boolean | undefined,
  definitions: readonly DimensionDefinition[] = []): string {
  const parts = orderedDimensions(dimensions).map(dimension => {
    const label = dimensionLabel(dimension.dimensionCode, definitions);
    const values = normalizeValues(form?.[dimension.dimensionCode]);
    if (values.length) return t('condition.values', { dimension: label, values: joinList(values.map(value => conditionValueLabel(dimension.dimensionCode, value))) });
    return dimension.required ? t('condition.notSelected', { dimension: label }) : t('condition.any', { dimension: label });
  });
  if (require3ds) parts.push(require3dsTag());
  return t('condition.summary', { conditions: parts.length ? parts.join(' · ') : t('condition.unrestricted') });
}

/** 取值格式（与 commercial 校验一致）：币种 3 位、国家 2 位大写字母，币对 USD/AED，其余 [A-Z0-9_]{1,64}。返回问题或 null。 */
export function conditionValueIssue(dimensionCode: string, value: string): string | null {
  if (dimensionCode === 'CURRENCY') return /^[A-Z]{3}$/.test(value) ? null : t('condition.issue.currency', { value });
  if (dimensionCode.endsWith('_COUNTRY')) return /^[A-Z]{2}$/.test(value) ? null : t('condition.issue.country', { value });
  // 币对与定价画像同写法 USD/AED；其余维度按代码格式。
  if (dimensionCode === 'CURRENCY_PAIR') return /^[A-Z]{3}\/[A-Z]{3}$/.test(value) ? null : t('condition.issue.currencyPair', { value });
  return /^[A-Z0-9_]{1,64}$/.test(value) ? null : t('condition.issue.code', { value });
}

/** 取值规范化：去空白、转大写、去重，保持输入顺序。 */
export function normalizeValues(values: readonly string[] | null | undefined): string[] {
  return [...new Set((values ?? []).map(value => value.trim().toUpperCase()).filter(Boolean))];
}

/** 表单中的接受条件：维度代码 → 取值列表。 */
export type ConditionForm = Record<string, string[] | undefined>;

/** 读模型 → 表单。 */
export function conditionForm(conditions: readonly CapabilityCondition[]): ConditionForm {
  return Object.fromEntries(conditions.map(condition => [condition.dimensionCode, [...condition.values]]));
}

/** 表单 → 请求：只取该开通项可配的维度（按目录顺序），去掉空维度；取值规范化。 */
export function conditionsBody(dimensions: readonly CapabilityDimension[], form: ConditionForm | undefined): CapabilityCondition[] {
  return dimensions.map(dimension => ({ dimensionCode: dimension.dimensionCode, values: normalizeValues(form?.[dimension.dimensionCode]) }))
    .filter(condition => condition.values.length > 0);
}

/** 前置检查：必填维度有值、每个取值格式正确；返回问题列表（最终以 commercial 为准）。 */
export function conditionIssues(dimensions: readonly CapabilityDimension[], form: ConditionForm | undefined,
  definitions: readonly DimensionDefinition[] = []): string[] {
  const issues: string[] = [];
  for (const dimension of dimensions) {
    const values = normalizeValues(form?.[dimension.dimensionCode]);
    if (dimension.required && values.length === 0) issues.push(t('condition.issue.required', { dimension: dimensionLabel(dimension.dimensionCode, definitions) }));
    for (const value of values) {
      const issue = conditionValueIssue(dimension.dimensionCode, value);
      if (issue) issues.push(t('condition.issue.withDimension', { dimension: dimensionLabel(dimension.dimensionCode, definitions), issue }));
    }
  }
  return issues;
}

/**
 * 列表「接受条件」列：每个维度一个标签「借贷记类型 借记」「消费者国家 AE 阿联酋 / SA 沙特阿拉伯」「币种 AED / USD」，
 * 取值文字同抽屉（conditionTagValue）；开启 3DS 时追加「要求 3DS」。
 */
export function conditionTags(capability: Pick<AgreementCapability, 'conditions' | 'require3ds'>,
  definitions: readonly DimensionDefinition[] = []): string[] {
  const tags = capability.conditions.map(condition => `${dimensionLabel(condition.dimensionCode, definitions)} ${condition.values
    .map(value => conditionTagValue(condition.dimensionCode, value)).join(' / ')}`);
  return capability.require3ds ? [...tags, require3dsTag()] : tags;
}

// ---------- 合同开通项表单 ----------

export interface CapabilityFormValues {
  entityNo: string; capabilityCode: string; status?: CapabilityStatus; effectiveFrom: string; effectiveTo?: string;
  /** 保证金百分比（5 即 5%），提交时换为 0.05。 */
  reservePercent?: string; rollingReserveDays?: number | null; conditions?: ConditionForm; require3ds?: boolean;
}

/** 保证金：小数比例 → 百分比输入（0.05 → 5）。 */
export const reservePercent = (rate: string | null | undefined) => percentInput(rate) ?? undefined;
/** 保证金百分比校验：0–100、最多 8 位小数；空值不判断。 */
export const reservePercentIssue = (value: string | null | undefined) => percentIssue(value);

/**
 * 开通项写入：接受条件按可配维度整理、3DS 开关、保证金百分比换为小数（两字段成组为空或非空由表单校验）。
 * 新增带 OE 与开通项代码；修改带状态。
 */
export function capabilityBody(expectedChangeVersion: number, values: CapabilityFormValues, dimensions: readonly CapabilityDimension[], update: boolean) {
  const percent = values.reservePercent?.trim();
  const common = { expectedChangeVersion, effectiveFrom: offsetInstant(values.effectiveFrom), effectiveTo: optionalInstant(values.effectiveTo),
    rollingReserveRate: percent ? rateFromPercent(percent) : null, rollingReserveDays: values.rollingReserveDays ?? null,
    conditions: conditionsBody(dimensions, values.conditions), require3ds: values.require3ds === true };
  return update ? { ...common, status: values.status ?? 'ACTIVE' }
    : { ...common, entityNo: values.entityNo.trim(), capabilityCode: values.capabilityCode.trim() };
}

/** 读模型 → 编辑表单的保证金与条件部分。 */
export function capabilityFormPatch(capability: AgreementCapability): Pick<CapabilityFormValues, 'reservePercent' | 'rollingReserveDays' | 'conditions' | 'require3ds'> {
  return { reservePercent: reservePercent(capability.rollingReserveRate), rollingReserveDays: capability.rollingReserveDays,
    conditions: conditionForm(capability.conditions), require3ds: capability.require3ds };
}

/** 保证金显示：5% / 90 天；未设置为 —。 */
export function reserveText(capability: Pick<AgreementCapability, 'rollingReserveRate' | 'rollingReserveDays'>): string {
  return capability.rollingReserveRate ? t('reserve.text', { percent: percentInput(capability.rollingReserveRate) ?? '', days: capability.rollingReserveDays ?? '—' }) : '—';
}

/** 开通项卡片：目录项 + 该 OE 上已有的合同开通项（有则置灰、点击编辑它）。 */
export interface CapabilityChoice { item: CatalogCapability; existing: AgreementCapability | null }

/**
 * 新增开通项卡片：已订阅产品下 ACTIVE 的目录项全部列出，按目录顺序；该 OE 已有的项带上那条合同开通项。
 * 给出 productCode（从产品区块打开）时只列该产品的项。
 */
export function capabilityChoices(catalog: readonly CatalogCapability[], productCodes: readonly string[],
  existing: readonly AgreementCapability[], entityNo: string, productCode?: string): CapabilityChoice[] {
  return catalog.filter(item => item.status === 'ACTIVE' && productCodes.includes(item.productCode) && (!productCode || item.productCode === productCode))
    .map(item => ({ item, existing: existing.find(capability => capability.entityNo === entityNo && capability.capabilityCode === item.capabilityCode) ?? null }));
}

/**
 * 卡片上的已有标记：执行状态 ENABLED 为「已开通」；其余为「已添加 · 等前置 / 前置失效暂停 / 手动暂停」，关闭的为「已添加 · 已关闭」；没有为 null。
 */
export function existingMark(existing: Pick<AgreementCapability, 'status' | 'executionStatus'> | null): string | null {
  if (!existing) return null;
  if (existing.status === 'ACTIVE' && existing.executionStatus === 'ENABLED') return t('capability.mark.enabled');
  const status = existing.status === 'ACTIVE' && existing.executionStatus
    ? enumLabel(t, 'enum.AgreementCapabilityExecutionStatus', existing.executionStatus)
    : enumLabel(t, 'enum.AgreementCapabilityStatus', existing.status);
  return t('capability.mark.added', { status });
}

/** 卡片的可配维度说明：中文名以「、」连接，没有可配维度时写明。 */
export function dimensionNames(dimensions: readonly CapabilityDimension[], definitions: readonly DimensionDefinition[] = []): string {
  return dimensions.length ? t('capability.dimensions', { dimensions: joinList(dimensions.map(item => dimensionLabel(item.dimensionCode, definitions))) })
    : t('capability.noDimensions');
}

/**
 * 开通项的可配维度（来自目录）；目录缺失时按已有条件推出。已有条件用到、目录已不再可配的维度追加在后（非必填），保证编辑回填不丢条件，
 * 是否仍允许由 commercial 校验。
 */
export function capabilityDimensions(catalogItem: Pick<CatalogCapability, 'dimensions'> | undefined,
  conditions: readonly CapabilityCondition[] = []): CapabilityDimension[] {
  if (!catalogItem?.dimensions.length) {
    return conditions.map(condition => ({ dimensionCode: condition.dimensionCode, required: condition.dimensionCode === 'CURRENCY' }));
  }
  const extra = conditions.filter(condition => condition.values.length && !catalogItem.dimensions.some(item => item.dimensionCode === condition.dimensionCode))
    .map(condition => ({ dimensionCode: condition.dimensionCode, required: false }));
  return extra.length ? [...catalogItem.dimensions, ...extra] : catalogItem.dimensions;
}
