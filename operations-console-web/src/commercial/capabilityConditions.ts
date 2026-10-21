import { ApiError } from '../api/session.ts';
import { offsetInstant, optionalInstant } from './commercial.ts';
import type { AgreementCapability, CapabilityStatus } from './commercial.ts';
import type { CapabilityDimension, CatalogCapability } from './commercialConsole.ts';
import { percentInput, percentIssue, rateFromPercent } from './componentTable.ts';
import type { CapabilityCondition } from './termReview.ts';

// ---------- 维度清单（commercial GET /products/dimensions，代码登记、只读） ----------

/** 维度元数据：事实来源、判定时点、可用于开通（接受条件）/ 定价。 */
export interface DimensionDefinition {
  dimensionCode: string; displayName: string; factSource: string; evaluationPoint: string; acceptanceAllowed: boolean; pricingAllowed: boolean;
}

/** 维度中文名兜底（维度清单读取失败或未返回 displayName 时使用）。 */
export const dimensionLabels: Readonly<Record<string, string>> = {
  CURRENCY: '币种', CONSUMER_COUNTRY: '消费者国家', CARD_BRAND: '卡组织', FUNDING_TYPE: '借贷记类型', CARD_LEVEL: '卡等级', CARD_PRODUCT: '卡产品',
  ISSUER_COUNTRY: '发卡国', ISSUER_REGION: '发卡地区', THREE_DS_RESULT: '3DS 结果', TRANSACTION_TYPE: '交易类型', PAYOUT_RAIL: '出款通道',
  DESTINATION_COUNTRY: '目的国', CURRENCY_PAIR: '币对', DISPUTE_STAGE: '争议阶段',
};

/** 常见取值建议（只作下拉建议，仍可输入其他代码）。 */
export const dimensionSuggestions: Readonly<Record<string, readonly string[]>> = {
  CURRENCY: ['AED', 'USD', 'SAR', 'EUR', 'GBP'], CONSUMER_COUNTRY: ['AE', 'SA', 'US', 'GB'], ISSUER_COUNTRY: ['AE', 'SA', 'US', 'GB'],
  DESTINATION_COUNTRY: ['AE', 'SA', 'US', 'GB'], CARD_BRAND: ['VISA', 'MASTERCARD', 'AMEX', 'UNIONPAY', 'MADA', 'DISCOVER', 'JCB'],
  FUNDING_TYPE: ['CREDIT', 'DEBIT', 'PREPAID'], CARD_LEVEL: ['CLASSIC', 'GOLD', 'PLATINUM', 'SIGNATURE', 'INFINITE', 'WORLD', 'WORLD_ELITE'],
  CARD_PRODUCT: ['CONSUMER', 'COMMERCIAL'], ISSUER_REGION: ['DOMESTIC', 'GCC', 'INTERNATIONAL'], CURRENCY_PAIR: ['USD/AED', 'EUR/AED'],
};

// TODO by CLAUDE: 币种改为字典维护后替换此常量
/** 币种下拉的候选（暂为写死的常用币种，后续改为字典维护）：代码 → 中文名。 */
export const currencyOptions: Readonly<Record<string, string>> = {
  AED: '阿联酋迪拉姆', USD: '美元', SAR: '沙特里亚尔', EUR: '欧元', GBP: '英镑', KWD: '科威特第纳尔', QAR: '卡塔尔里亚尔', BHD: '巴林第纳尔',
  OMR: '阿曼里亚尔', EGP: '埃及镑', JOD: '约旦第纳尔', INR: '印度卢比', PKR: '巴基斯坦卢比', CNY: '人民币', HKD: '港币', JPY: '日元', SGD: '新加坡元',
  CHF: '瑞士法郎', CAD: '加拿大元', AUD: '澳大利亚元',
};

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
  const codes = [...Object.keys(currencyOptions).filter(code => !isQuickCurrency(code)),
    ...splitCurrencies(values).more.filter(code => !currencyOptions[code])];
  return codes.map(code => ({ value: code, label: currencyOptions[code] ? `${code} ${currencyOptions[code]}` : code }));
}

/** 国家代码中文名（国家类维度的选项文字；未列出的合法代码原样显示）。 */
export const countryLabels: Readonly<Record<string, string>> = {
  AE: '阿联酋', SA: '沙特阿拉伯', KW: '科威特', QA: '卡塔尔', BH: '巴林', OM: '阿曼', EG: '埃及', JO: '约旦', US: '美国', GB: '英国', DE: '德国',
  FR: '法国', IN: '印度', CN: '中国', SG: '新加坡',
};

/** 枚举型取值中文名（汇总与标签用；未列出的取值原样显示）。 */
const valueLabels: Readonly<Record<string, Readonly<Record<string, string>>>> = {
  FUNDING_TYPE: { CREDIT: '贷记', DEBIT: '借记', PREPAID: '预付' },
  CARD_PRODUCT: { CONSUMER: '个人卡', COMMERCIAL: '商务卡' },
  ISSUER_REGION: { DOMESTIC: '境内', GCC: '海湾国家', INTERNATIONAL: '境外' },
};

/** 标签上的取值文字：国家为「AE 阿联酋」（与抽屉选项一致），枚举为中文名，币种等其余为代码。 */
export function conditionTagValue(dimensionCode: string, value: string): string {
  if (dimensionCode.endsWith('_COUNTRY')) return countryLabels[value] ? `${value} ${countryLabels[value]}` : value;
  return conditionValueLabel(dimensionCode, value);
}

/** 3DS 要求的条件标签文字。 */
export const require3dsTag = '要求 3DS';

/** 取值显示名：枚举中文名 / 国家中文名，其余原样。 */
export function conditionValueLabel(dimensionCode: string, value: string): string {
  if (dimensionCode.endsWith('_COUNTRY')) return countryLabels[value] ?? value;
  return valueLabels[dimensionCode]?.[value] ?? value;
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
    return { dimensionCode: d.dimensionCode, displayName: text(d.displayName, 64) ? d.displayName : dimensionLabels[d.dimensionCode] ?? d.dimensionCode,
      factSource: typeof d.factSource === 'string' ? d.factSource : '', evaluationPoint: typeof d.evaluationPoint === 'string' ? d.evaluationPoint : '',
      acceptanceAllowed: d.acceptanceAllowed, pricingAllowed: d.pricingAllowed };
  });
}

/** 维度显示名：优先维度清单，其次兜底中文名，最后原代码。 */
export function dimensionLabel(code: string, definitions: readonly DimensionDefinition[] = []): string {
  return definitions.find(item => item.dimensionCode === code)?.displayName ?? dimensionLabels[code] ?? code;
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
    if (values.length) return `${label} ${values.map(value => conditionValueLabel(dimension.dimensionCode, value)).join('、')}`;
    return dimension.required ? `${label}未选` : `${label}不限`;
  });
  if (require3ds) parts.push('要求 3DS');
  return `接受：${parts.length ? parts.join(' · ') : '不限'}`;
}

/** 取值格式（与 commercial 校验一致）：币种 3 位、国家 2 位大写字母，币对 USD/AED，其余 [A-Z0-9_]{1,64}。返回问题或 null。 */
export function conditionValueIssue(dimensionCode: string, value: string): string | null {
  if (dimensionCode === 'CURRENCY') return /^[A-Z]{3}$/.test(value) ? null : `${value} 不是三位币种`;
  if (dimensionCode.endsWith('_COUNTRY')) return /^[A-Z]{2}$/.test(value) ? null : `${value} 不是两位国家码`;
  // 币对与定价画像同写法 USD/AED；其余维度按代码格式。
  if (dimensionCode === 'CURRENCY_PAIR') return /^[A-Z]{3}\/[A-Z]{3}$/.test(value) ? null : `${value} 不是币对（如 USD/AED）`;
  return /^[A-Z0-9_]{1,64}$/.test(value) ? null : `${value} 只能是大写字母、数字与下划线`;
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
    if (dimension.required && values.length === 0) issues.push(`${dimensionLabel(dimension.dimensionCode, definitions)}必填`);
    for (const value of values) {
      const issue = conditionValueIssue(dimension.dimensionCode, value);
      if (issue) issues.push(`${dimensionLabel(dimension.dimensionCode, definitions)}：${issue}`);
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
  return capability.require3ds ? [...tags, require3dsTag] : tags;
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
  return update ? { ...common, status: values.status ?? 'ENABLED' }
    : { ...common, entityNo: values.entityNo.trim(), capabilityCode: values.capabilityCode.trim() };
}

/** 读模型 → 编辑表单的保证金与条件部分。 */
export function capabilityFormPatch(capability: AgreementCapability): Pick<CapabilityFormValues, 'reservePercent' | 'rollingReserveDays' | 'conditions' | 'require3ds'> {
  return { reservePercent: reservePercent(capability.rollingReserveRate), rollingReserveDays: capability.rollingReserveDays,
    conditions: conditionForm(capability.conditions), require3ds: capability.require3ds };
}

/** 保证金显示：5% / 90 天；未设置为 —。 */
export function reserveText(capability: Pick<AgreementCapability, 'rollingReserveRate' | 'rollingReserveDays'>): string {
  return capability.rollingReserveRate ? `${percentInput(capability.rollingReserveRate)}% / ${capability.rollingReserveDays ?? '—'} 天` : '—';
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

/** 卡片上的已有标记：ENABLED 为「已开通」，其余为「已添加 · 暂停 / 关闭」；没有为 null。 */
export function existingMark(existing: Pick<AgreementCapability, 'status'> | null): string | null {
  if (!existing) return null;
  return existing.status === 'ENABLED' ? '已开通' : `已添加 · ${existing.status === 'SUSPENDED' ? '暂停' : '关闭'}`;
}

/** 卡片的可配维度说明：中文名以「、」连接，没有可配维度时写明。 */
export function dimensionNames(dimensions: readonly CapabilityDimension[], definitions: readonly DimensionDefinition[] = []): string {
  return dimensions.length ? `可配：${dimensions.map(item => dimensionLabel(item.dimensionCode, definitions)).join('、')}` : '无可配维度';
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
