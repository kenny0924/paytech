import { ApiError } from '../api/session.ts';
import { enumLabel, translator } from '../i18n/index.ts';
import type { ConfigurationMutation } from './types.ts';

/** Non-component code takes text through `translator` at call time (never at module load). */
const t = translator('currency');

/** Operations BFF 币种维护路径；Platform 为币种唯一 Owner。 */
export const currencySpecPath = '/currency-admin/v1/currency-specs';
/** 新建、编辑草稿与启停的按钮资源；没有该按钮时页面只读。 */
export const manageCurrencySpecs = 'OPERATIONS_CURRENCY_SPEC_MANAGE';

export const currencySpecStatuses = ['DRAFT', 'ACTIVE', 'INACTIVE'] as const;
export const roundingModes = ['HALF_UP'] as const;
export type CurrencySpecStatus = typeof currencySpecStatuses[number];
export type RoundingMode = typeof roundingModes[number];

/** 一个币种；数字代码、名称、符号与图标可空（历史数据未补齐时显示占位）；name 是 ISO 英文名，页面名称用 Intl 本地化。 */
export interface CurrencySpec {
  currency: string; numericCode: string | null; name: string | null; symbol: string | null; minorUnit: number; roundingMode: RoundingMode;
  logoFileAddress: string | null; logoPublicUrl: string | null; status: CurrencySpecStatus; version: number;
}

/** 状态与舍入方式的显示名是 `currency.enum.status.<值>`、`currency.enum.roundingMode.<值>`，用 enumLabel 取。 */
export const currencyStatusColors: Readonly<Record<CurrencySpecStatus, string>> = { DRAFT: 'gold', ACTIVE: 'green', INACTIVE: 'default' };
export const currencyCodePattern = /^[A-Z]{3}$/;
export const maxMinorUnit = 8;

const decimalIdPattern = /^[1-9][0-9]{0,18}$/;
const specFields = ['currency', 'numericCode', 'name', 'symbol', 'minorUnit', 'roundingMode', 'logoFileAddress', 'logoPublicUrl', 'status', 'version'];
const mutationFields = ['objectType', 'objectId', 'objectCode', 'status', 'version'];
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const exactFields = (v: Record<string, unknown>, fields: readonly string[]) =>
  Object.keys(v).length === fields.length && fields.every(field => field in v);
const integer = (v: unknown, min: number, max: number) => Number.isInteger(v) && Number(v) >= min && Number(v) <= max;
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const nullableText = (v: unknown, max: number) => v === null || (typeof v === 'string' && v.length <= max);
const nullableMatch = (v: unknown, pattern: RegExp) => v === null || (typeof v === 'string' && pattern.test(v));
const nullableUrl = (v: unknown) => {
  if (v === null) return true;
  if (typeof v !== 'string') return false;
  try { const url = new URL(v); return url.protocol === 'https:' || url.protocol === 'http:'; } catch { return false; }
};
/** 图标只保存文件服务地址（文件地址原则），由 Platform 换算公开地址。 */
export const fileAddressPattern = /^\/files\/v1\/[1-9][0-9]{0,18}$/;
export const numericCodePattern = /^[0-9]{3}$/;
export const maxNameLength = 128;
export const maxSymbolLength = 8;
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 币种列表严格解码：字段集合、代码格式、小数位、枚举、版本、数字代码与图标地址逐项校验，币种代码不得重复。 */
export function decodeCurrencySpecList(value: unknown): CurrencySpec[] {
  require(object(value) && exactFields(value, ['items']) && Array.isArray(value.items));
  const items = (value as { items: unknown[] }).items.map(decodeCurrencySpec);
  require(new Set(items.map(item => item.currency)).size === items.length);
  return items;
}

function decodeCurrencySpec(value: unknown): CurrencySpec {
  const v = value as CurrencySpec;
  require(object(v) && exactFields(v as unknown as Record<string, unknown>, specFields) && typeof v.currency === 'string'
    && currencyCodePattern.test(v.currency) && integer(v.minorUnit, 0, maxMinorUnit) && oneOf(roundingModes)(v.roundingMode)
    && oneOf(currencySpecStatuses)(v.status) && integer(v.version, 1, Number.MAX_SAFE_INTEGER)
    && nullableMatch(v.numericCode, numericCodePattern) && nullableText(v.name, maxNameLength) && nullableText(v.symbol, maxSymbolLength)
    && nullableMatch(v.logoFileAddress, fileAddressPattern) && nullableUrl(v.logoPublicUrl));
  return v;
}

/** 写回执必须是本次币种、目标状态（修改不改变状态，传 null 表示任一已知状态），且版本为正整数。 */
export function decodeCurrencySpecMutation(value: unknown, currency: string, status: CurrencySpecStatus | null): ConfigurationMutation {
  const v = value as ConfigurationMutation;
  require(object(v) && exactFields(v as unknown as Record<string, unknown>, mutationFields) && v.objectType === 'CURRENCY_SPEC'
    && typeof v.objectId === 'string' && decimalIdPattern.test(v.objectId) && v.objectCode === currency
    && (status === null ? oneOf(currencySpecStatuses)(v.status) : v.status === status)
    && integer(v.version, 1, Number.MAX_SAFE_INTEGER));
  return v;
}

export interface CurrencySpecFormValues {
  currency?: string; numericCode?: string; name?: string; symbol?: string; minorUnit?: number; roundingMode?: RoundingMode;
  logoFileAddress?: string | null; reason?: string;
}

const trimmed = (value: string | undefined) => (value ?? '').trim();

/** 新建请求体：只发送合同字段，文本去除首尾空白；没有图标时为 null。 */
export function createCurrencySpecBody(values: CurrencySpecFormValues) {
  return { currency: trimmed(values.currency), numericCode: trimmed(values.numericCode), name: trimmed(values.name), symbol: trimmed(values.symbol),
    logoFileAddress: values.logoFileAddress ?? null, minorUnit: values.minorUnit, roundingMode: values.roundingMode, reason: trimmed(values.reason) };
}

/** 修改请求体（草稿全部属性）：代码在路径上，不随 Body 发送。 */
export function updateCurrencySpecBody(values: CurrencySpecFormValues, spec: CurrencySpec) {
  return { numericCode: trimmed(values.numericCode), name: trimmed(values.name), symbol: trimmed(values.symbol),
    logoFileAddress: values.logoFileAddress ?? null, minorUnit: values.minorUnit, roundingMode: values.roundingMode,
    expectedVersion: spec.version, reason: trimmed(values.reason) };
}

/** 已启用或停用的币种只更换图标：标准属性按当前值原样提交（Platform 比对后只允许图标不同）。 */
export function changeLogoBody(spec: CurrencySpec, logoFileAddress: string, reason: string) {
  return { numericCode: spec.numericCode, name: spec.name, symbol: spec.symbol, logoFileAddress, minorUnit: spec.minorUnit,
    roundingMode: spec.roundingMode, expectedVersion: spec.version, reason: reason.trim() };
}

/** 只有草稿可修改标准属性（数字代码、名称、符号、小数位、舍入方式）；启用后冻结。 */
export const editableCurrencySpec = (spec: CurrencySpec) => spec.status === 'DRAFT';
/** 已启用或停用但标准属性未补齐时，只换图标的修改会被 Platform 的必填校验拒绝；页面据此提示改走评审过的 SQL。 */
export const logoChangeBlocked = (spec: CurrencySpec) => !spec.numericCode || !spec.name || !spec.symbol;

/** 列表筛选：全部或某一状态的条数。 */
export function currencyStatusCounts(specs: readonly CurrencySpec[]): Record<CurrencySpecStatus | 'ALL', number> {
  const counts = { ALL: specs.length, DRAFT: 0, ACTIVE: 0, INACTIVE: 0 };
  specs.forEach(spec => { counts[spec.status] += 1; });
  return counts;
}

export interface CurrencyStatusChange { target: CurrencySpecStatus; verb: string; notice: string }

/** 状态动作：草稿与停用可启用，启用可停用。文案在调用时按当前语言取得。 */
export function currencyStatusChange(spec: Pick<CurrencySpec, 'status' | 'minorUnit' | 'roundingMode'>): CurrencyStatusChange {
  if (spec.status === 'ACTIVE') {
    return { target: 'INACTIVE', verb: t('statusChange.disable'), notice: t('statusChange.disableNotice') };
  }
  return { target: 'ACTIVE', verb: t('statusChange.enable'), notice: t('statusChange.enableNotice', {
    minorUnit: spec.minorUnit, roundingMode: enumLabel(t, 'enum.roundingMode', spec.roundingMode) }) };
}
