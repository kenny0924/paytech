import { ApiError } from '../api/session.ts';
import { capabilityKindLabels, catalogLabel } from './catalogTree.ts';
import type { CatalogEventRow } from './catalogTree.ts';
import { decodeCatalogCapability, decodeFeeEvent } from './commercialConsole.ts';
import type { CapabilityDimension, CatalogCapability, CatalogFeeEvent } from './commercialConsole.ts';
import type { DimensionDefinition } from './capabilityConditions.ts';

// ---------- 产品目录维护（开通项新增 / 编辑 / 停用 / 启用、计费事件挂接） ----------

export const catalogPaths = {
  capabilities: '/commercial-admin/v1/products/capabilities',
  capability: (productCapabilityId: string) => `/commercial-admin/v1/products/capabilities/${productCapabilityId}`,
  capabilityAction: (productCapabilityId: string, action: CapabilityStatusAction) =>
    `/commercial-admin/v1/products/capabilities/${productCapabilityId}/${action}`,
  feeEventCapability: (productFeeEventId: string) => `/commercial-admin/v1/products/fee-events/${productFeeEventId}/capability`,
} as const;

export type CapabilityStatusAction = 'retire' | 'activate';

/** 开通项种类（新增时选择，之后不可改）。 */
export const catalogCapabilityKinds = ['PAYMENT_METHOD', 'FEATURE', 'PAYOUT_RAIL', 'FX', 'DISPUTE', 'PERIODIC_SERVICE'] as const;
export type CatalogCapabilityKind = typeof catalogCapabilityKinds[number];

/** 支付方式代码建议（一期无 Payment 目录接口，按格式校验 + 已知代码提示）。 */
export const paymentMethodSuggestions = ['CARD', 'APPLE_PAY', 'GOOGLE_PAY', 'STC_PAY', 'MADA', 'BNPL_TABBY'] as const;

/** 开通项代码与支付方式代码：大写字母开头，2–64 位大写字母、数字与下划线。 */
export const capabilityCodePattern = /^[A-Z][A-Z0-9_]{1,63}$/;

export interface CatalogCapabilityFormValues {
  productCode: string; capabilityCode: string; capabilityName: string; capabilityKind: CatalogCapabilityKind; paymentMethodCode?: string;
  dimensionCodes?: string[]; requiredDimensions?: string[];
}

/** 新增表单默认值：支付方式默认可配币种且必填。 */
export function newCatalogCapabilityForm(productCode: string): CatalogCapabilityFormValues {
  return { productCode, capabilityCode: '', capabilityName: '', capabilityKind: 'PAYMENT_METHOD', dimensionCodes: ['CURRENCY'], requiredDimensions: ['CURRENCY'] };
}

/** 读模型 → 编辑表单。 */
export function catalogCapabilityForm(capability: CatalogCapability): CatalogCapabilityFormValues {
  return { productCode: capability.productCode, capabilityCode: capability.capabilityCode, capabilityName: capability.capabilityName,
    capabilityKind: capability.capabilityKind as CatalogCapabilityKind, paymentMethodCode: capability.paymentMethodCode ?? undefined,
    dimensionCodes: capability.dimensions.map(item => item.dimensionCode),
    requiredDimensions: capability.dimensions.filter(item => item.required).map(item => item.dimensionCode) };
}

/** 可配维度：按选择顺序去重；必填只在已选维度内有效。 */
export function capabilityDimensionsBody(values: Pick<CatalogCapabilityFormValues, 'dimensionCodes' | 'requiredDimensions'>): CapabilityDimension[] {
  const required = new Set(values.requiredDimensions ?? []);
  return [...new Set(values.dimensionCodes ?? [])].map(dimensionCode => ({ dimensionCode, required: required.has(dimensionCode) }));
}

/** 新增请求：支付方式种类带支付方式代码（转大写），其余种类为 null。 */
export function createCatalogCapabilityBody(values: CatalogCapabilityFormValues) {
  const paymentMethodCode = values.capabilityKind === 'PAYMENT_METHOD' ? (values.paymentMethodCode ?? '').trim().toUpperCase() || null : null;
  return { productCode: values.productCode, capabilityCode: values.capabilityCode.trim().toUpperCase(), capabilityName: values.capabilityName.trim(),
    capabilityKind: values.capabilityKind, paymentMethodCode, dimensions: capabilityDimensionsBody(values) };
}

/** 修改请求：只改名称、归属产品与可配维度；代码与种类不可改。 */
export function updateCatalogCapabilityBody(expectedChangeVersion: number | string, values: CatalogCapabilityFormValues) {
  return { expectedChangeVersion, capabilityName: values.capabilityName.trim(), productCode: values.productCode, dimensions: capabilityDimensionsBody(values) };
}

/** 可选维度：维度清单中可用于开通的；读取失败时为空（表单退回手输）。 */
export function acceptanceDimensionOptions(definitions: readonly DimensionDefinition[]) {
  return definitions.filter(item => item.acceptanceAllowed).map(item => ({ value: item.dimensionCode, label: `${item.displayName} ${item.dimensionCode}` }));
}

/** 目录表「可配维度」列：必填维度带 *。 */
export function dimensionBadges(dimensions: readonly CapabilityDimension[]): string[] {
  return dimensions.map(item => `${item.dimensionCode}${item.required ? ' *' : ''}`);
}

/** 行操作：ACTIVE 可停用，RETIRED / DRAFT 可启用。 */
export function capabilityStatusAction(status: string): CapabilityStatusAction | null {
  if (status === 'ACTIVE') return 'retire';
  return status === 'RETIRED' || status === 'DRAFT' ? 'activate' : null;
}

// ---------- 计费事件挂接 ----------

/** 挂接选择的编码：NONE（无需开通）、KIND:<种类>（任一该种类开通项）、FEATURE:<功能开通项代码>。 */
export type AttachChoice = string;
export const noCapability: AttachChoice = 'NONE';

export function attachChoice(event: Pick<CatalogFeeEvent, 'capabilityKind' | 'capabilityCode'>): AttachChoice {
  if (!event.capabilityKind) return noCapability;
  return event.capabilityKind === 'FEATURE' && event.capabilityCode ? `FEATURE:${event.capabilityCode}` : `KIND:${event.capabilityKind}`;
}

/** 挂接下拉：无需开通、各非功能种类、本产品下的功能开通项。 */
export function attachOptions(productCode: string, capabilities: readonly CatalogCapability[]) {
  const features = capabilities.filter(item => item.productCode === productCode && item.capabilityKind === 'FEATURE');
  return [
    { value: noCapability, label: '无需开通（基础产品事件）' },
    ...catalogCapabilityKinds.filter(kind => kind !== 'FEATURE').map(kind => ({ value: `KIND:${kind}`, label: `任一${catalogLabel(capabilityKindLabels, kind)}开通项` })),
    ...features.map(item => ({ value: `FEATURE:${item.capabilityCode}`, label: `功能 · ${item.capabilityName} ${item.capabilityCode}` })),
  ];
}

/** 挂接请求：选择 → capabilityKind / capabilityCode（只有功能开通项带代码）。 */
export function feeEventAttachBody(expectedChangeVersion: number | string, choice: AttachChoice) {
  if (choice === noCapability) return { expectedChangeVersion, capabilityKind: null, capabilityCode: null };
  const [prefix, value] = choice.split(':');
  return prefix === 'FEATURE' ? { expectedChangeVersion, capabilityKind: 'FEATURE', capabilityCode: value }
    : { expectedChangeVersion, capabilityKind: value, capabilityCode: null };
}

/** 挂接显示文字。 */
export function attachLabel(event: Pick<CatalogFeeEvent, 'capabilityKind' | 'capabilityCode'>, capabilities: readonly CatalogCapability[] = []): string {
  if (!event.capabilityKind) return '无需开通';
  if (event.capabilityKind === 'FEATURE' && event.capabilityCode) {
    const name = capabilities.find(item => item.capabilityCode === event.capabilityCode)?.capabilityName;
    return `功能 · ${name && name !== event.capabilityCode ? `${name} ` : ''}${event.capabilityCode}`;
  }
  return `任一${catalogLabel(capabilityKindLabels, event.capabilityKind)}`;
}

/** 合并行的挂接：各条登记的挂接去重；登记间不一致时全部列出。 */
export function rowAttachLabels(row: Pick<CatalogEventRow, 'feeEvents'>, capabilities: readonly CatalogCapability[] = []): string[] {
  return [...new Set(row.feeEvents.map(event => attachLabel(event, capabilities)))];
}

// ---------- 回执 ----------

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 开通项写回执：ProductCapabilitySummary；给出 ID 时必须一致。 */
export function decodeCatalogCapabilityWrite(productCapabilityId?: string) {
  return (value: unknown): CatalogCapability => {
    require(object(value));
    const capability = decodeCatalogCapability(value);
    require(!productCapabilityId || capability.productCapabilityId === productCapabilityId);
    return capability;
  };
}

/** 计费事件挂接回执：ID 必须一致。 */
export function decodeFeeEventWrite(productFeeEventId: string) {
  return (value: unknown): CatalogFeeEvent => {
    const event = decodeFeeEvent(value);
    require(event.productFeeEventId === productFeeEventId);
    return event;
  };
}
