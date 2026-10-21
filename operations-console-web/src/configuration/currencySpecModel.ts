import { ApiError } from '../api/session.ts';
import type { ConfigurationMutation } from './types.ts';

/** Operations BFF 币种维护路径；Platform 为币种唯一 Owner。 */
export const currencySpecPath = '/currency-admin/v1/currency-specs';
/** 新建、编辑草稿与启停的按钮资源；没有该按钮时页面只读。 */
export const manageCurrencySpecs = 'OPERATIONS_CURRENCY_SPEC_MANAGE';

export const currencySpecStatuses = ['DRAFT', 'ACTIVE', 'INACTIVE'] as const;
export const roundingModes = ['HALF_UP'] as const;
export type CurrencySpecStatus = typeof currencySpecStatuses[number];
export type RoundingMode = typeof roundingModes[number];

export interface CurrencySpec {
  currency: string; minorUnit: number; roundingMode: RoundingMode; status: CurrencySpecStatus; version: number; updateTime: string;
}

export const currencyStatusLabels: Readonly<Record<CurrencySpecStatus, string>> = { DRAFT: '草稿', ACTIVE: '启用', INACTIVE: '停用' };
export const currencyStatusColors: Readonly<Record<CurrencySpecStatus, string>> = { DRAFT: 'gold', ACTIVE: 'green', INACTIVE: 'default' };
export const roundingModeLabels: Readonly<Record<RoundingMode, string>> = { HALF_UP: '四舍五入（HALF_UP）' };
export const currencyCodePattern = /^[A-Z]{3}$/;
export const maxMinorUnit = 8;

const decimalIdPattern = /^[1-9][0-9]{0,18}$/;
const specFields = ['currency', 'minorUnit', 'roundingMode', 'status', 'version', 'updateTime'];
const mutationFields = ['objectType', 'objectId', 'objectCode', 'status', 'version'];
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const exactFields = (v: Record<string, unknown>, fields: readonly string[]) =>
  Object.keys(v).length === fields.length && fields.every(field => field in v);
const integer = (v: unknown, min: number, max: number) => Number.isInteger(v) && Number(v) >= min && Number(v) <= max;
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 币种列表严格解码：字段集合、代码格式、小数位、枚举与版本逐项校验，币种代码不得重复。 */
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
    && oneOf(currencySpecStatuses)(v.status) && integer(v.version, 1, Number.MAX_SAFE_INTEGER) && instant(v.updateTime));
  return v;
}

/** 写回执必须是本次币种、目标状态，且版本为正整数。 */
export function decodeCurrencySpecMutation(value: unknown, currency: string, status: CurrencySpecStatus): ConfigurationMutation {
  const v = value as ConfigurationMutation;
  require(object(v) && exactFields(v as unknown as Record<string, unknown>, mutationFields) && v.objectType === 'CURRENCY_SPEC'
    && typeof v.objectId === 'string' && decimalIdPattern.test(v.objectId) && v.objectCode === currency && v.status === status
    && integer(v.version, 1, Number.MAX_SAFE_INTEGER));
  return v;
}

export interface CurrencySpecFormValues { currency?: string; minorUnit?: number; roundingMode?: RoundingMode; reason?: string }

/** 新建请求体：只发送合同字段，代码与原因去除首尾空白。 */
export function createCurrencySpecBody(values: CurrencySpecFormValues) {
  return { currency: (values.currency ?? '').trim(), minorUnit: values.minorUnit, roundingMode: values.roundingMode, reason: (values.reason ?? '').trim() };
}

/** 修改草稿请求体：代码在路径上，不随 Body 发送。 */
export function updateCurrencySpecBody(values: CurrencySpecFormValues, spec: CurrencySpec) {
  return { minorUnit: values.minorUnit, roundingMode: values.roundingMode, expectedVersion: spec.version, reason: (values.reason ?? '').trim() };
}

/** 只有草稿可修改小数位与舍入方式；启用后冻结。 */
export const editableCurrencySpec = (spec: CurrencySpec) => spec.status === 'DRAFT';

export interface CurrencyStatusChange { target: CurrencySpecStatus; verb: string; notice: string }

/** 状态动作：草稿与停用可启用，启用可停用。 */
export function currencyStatusChange(spec: CurrencySpec): CurrencyStatusChange {
  if (spec.status === 'ACTIVE') {
    return { target: 'INACTIVE', verb: '停用', notice: '已有账簿与计费事实照常处理，只拦截新建账簿等新用途。' };
  }
  return { target: 'ACTIVE', verb: '启用', notice: `请再次核对：小数位 ${spec.minorUnit}，舍入方式 ${roundingModeLabels[spec.roundingMode]}。`
    + '启用后小数位与舍入方式不可修改，币种代码也无法换一个重建。' };
}
