import { ApiError } from '../api/session.ts';
import {
  currencyPattern, decodeComponent, decodeCondition, decodePrice, decodeSettlement, entityNoPattern, idPattern, ownerReason,
} from './commercial.ts';
import type { Dimension, Price, PriceComponent, PriceSettlement, ProfileCondition } from './commercial.ts';
import { decodeTermVersionListItem } from './commercialConsole.ts';
import { decodeCapabilityCondition } from './termReview.ts';
import type { CapabilityCondition } from './termReview.ts';
import type { TermVersionListItem } from './commercialConsole.ts';

// ---------- 工作台 ----------

/** 工作台指标卡片，声明顺序即卡片顺序（与 BFF CommercialDashboardMetric 一致）。 */
export const dashboardMetrics = ['PENDING_APPROVAL_VERSIONS', 'SCHEDULED_VERSIONS', 'DRAFT_AGREEMENTS', 'PENDING_SIGNATURE_AGREEMENTS', 'ACTIVE_AGREEMENTS'] as const;
export type DashboardMetricCode = typeof dashboardMetrics[number];
export const dashboardMetricLabels: Record<DashboardMetricCode, string> = {
  PENDING_APPROVAL_VERSIONS: '待审批版本', SCHEDULED_VERSIONS: '已排期待生效', DRAFT_AGREEMENTS: '草稿合同', PENDING_SIGNATURE_AGREEMENTS: '待签署合同',
  ACTIVE_AGREEMENTS: '生效合同',
};
export interface DashboardMetric { metricCode: DashboardMetricCode; count: number }
export interface Dashboard { asOf: string; metrics: DashboardMetric[]; approvalQueue: TermVersionListItem[] }

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const array = (v: unknown): v is unknown[] => Array.isArray(v);
const text = (v: unknown, max = 256) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const id = (v: unknown) => typeof v === 'string' && idPattern.test(v);
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const decimal = (v: unknown) => typeof v === 'string' && /^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(v);
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 工作台：五张卡片按约定顺序、数量为非负整数；审批队列只含待审批版本。 */
export function decodeDashboard(value: unknown): Dashboard {
  const v = value as Dashboard;
  require(object(v) && instant(v.asOf) && array(v.metrics) && array(v.approvalQueue));
  const metrics = v.metrics.map(metric => {
    require(object(metric) && (dashboardMetrics as readonly string[]).includes(metric.metricCode) && Number.isSafeInteger(metric.count)
      && Number(metric.count) >= 0);
    return { metricCode: metric.metricCode, count: metric.count };
  });
  const approvalQueue = v.approvalQueue.map(decodeTermVersionListItem);
  require(approvalQueue.every(item => item.status === 'PENDING_APPROVAL'));
  return { asOf: v.asOf, metrics, approvalQueue };
}

// ---------- 费率卡 ----------

export interface RateCardLimit { limitCode: string; limitValue: string }
export interface RateCardCapability {
  agreementCapabilityId: string; capabilityCode: string; productCode: string | null; capabilityKind: string | null; paymentMethodCode: string | null;
  dimensionCode: string | null; dimensionValue: string | null; entityNo: string; status: string; effectiveFrom: string; effectiveTo: string | null;
  rollingReserveRate: string | null; rollingReserveDays: number | null; limits: RateCardLimit[];
  /** 一期接受条件与 3DS 开关；旧 BFF 只有 limits。 */
  conditions: CapabilityCondition[]; require3ds: boolean;
}
export interface FeeEventComponents { feeEventCode: string; components: PriceComponent[] }
export interface ProfilePrice {
  priceId: string; pricingProfileCode: string; profileName: string | null; specificity: number; priorityNo: number;
  conditions: ProfileCondition[]; settlement: PriceSettlement | null; feeEvents: FeeEventComponents[];
}
export interface CurrencyPrices { currency: string; profiles: ProfilePrice[] }
export interface CapabilityPrices { agreementCapabilityId: string; capabilityCode: string; currencies: CurrencyPrices[] }
export interface TermVersionRateCard {
  planId: string; termType: string; versionId: string; versionNo: string; status: string; timing: 'CURRENT' | 'SCHEDULED'; effectiveFrom: string;
  effectiveTo: string | null; pricingModel: string; feeTaxTreatment: string; capabilities: CapabilityPrices[];
}
export interface AgreementRateCard {
  agreementId: string; agreementNo: string; entityNo: string; businessMode: string | null; fundMode: string | null; pricingAuthority: string | null;
  effectiveFrom: string; effectiveTo: string | null; products: string[]; capabilities: RateCardCapability[]; termVersions: TermVersionRateCard[];
}
export interface RateCard { clientId: string; asOf: string; currency: string | null; agreements: AgreementRateCard[] }

function decodeRateCardCapability(value: unknown): RateCardCapability {
  const v = value as RateCardCapability;
  require(object(v) && id(v.agreementCapabilityId) && text(v.capabilityCode, 64) && typeof v.entityNo === 'string' && entityNoPattern.test(v.entityNo)
    && text(v.status, 32) && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo) && optional(decimal)(v.rollingReserveRate) && optional(array)(v.limits)
    && (v.limits ?? []).every(limit => object(limit) && text(limit.limitCode, 64) && text(limit.limitValue, 128)) && optional(array)(v.conditions));
  return { ...v, productCode: orNull(v.productCode), capabilityKind: orNull(v.capabilityKind), paymentMethodCode: orNull(v.paymentMethodCode),
    dimensionCode: orNull(v.dimensionCode), dimensionValue: orNull(v.dimensionValue), effectiveTo: orNull(v.effectiveTo),
    rollingReserveRate: orNull(v.rollingReserveRate), rollingReserveDays: orNull(v.rollingReserveDays), limits: v.limits ?? [],
    conditions: (v.conditions ?? []).map(decodeCapabilityCondition), require3ds: v.require3ds === true };
}

function decodeProfilePrice(value: unknown): ProfilePrice {
  const v = value as ProfilePrice;
  require(object(v) && id(v.priceId) && text(v.pricingProfileCode, 64) && Number.isInteger(v.specificity) && Number.isInteger(v.priorityNo)
    && array(v.conditions) && array(v.feeEvents));
  return { ...v, profileName: orNull(v.profileName), conditions: v.conditions.map(decodeCondition), settlement: decodeSettlement(v.settlement),
    feeEvents: v.feeEvents.map(event => {
      require(object(event) && text(event.feeEventCode, 64) && array(event.components));
      return { feeEventCode: event.feeEventCode as string, components: (event.components as unknown[]).map(decodeComponent) };
    }) };
}

function decodeTermVersionRateCard(value: unknown): TermVersionRateCard {
  const v = value as TermVersionRateCard;
  require(object(v) && id(v.planId) && text(v.termType, 64) && id(v.versionId) && text(v.versionNo, 19) && text(v.status, 32)
    && (v.timing === 'CURRENT' || v.timing === 'SCHEDULED') && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo) && array(v.capabilities));
  return { ...v, effectiveTo: orNull(v.effectiveTo), capabilities: v.capabilities.map(capability => {
    require(object(capability) && id(capability.agreementCapabilityId) && text(capability.capabilityCode, 64) && array(capability.currencies));
    return { agreementCapabilityId: capability.agreementCapabilityId as string, capabilityCode: capability.capabilityCode as string,
      currencies: (capability.currencies as unknown[]).map(value => {
        const block = value as Record<string, unknown>;
        require(object(block) && typeof block.currency === 'string' && currencyPattern.test(block.currency) && array(block.profiles));
        return { currency: block.currency as string, profiles: (block.profiles as unknown[]).map(decodeProfilePrice) };
      }) };
  }) };
}

/** 费率卡：Client 必须与请求一致。 */
export function decodeRateCard(clientId: string) {
  return (value: unknown): RateCard => {
    const v = value as RateCard;
    require(object(v) && v.clientId === clientId && instant(v.asOf) && optional((c: unknown) => typeof c === 'string' && currencyPattern.test(c))(v.currency)
      && array(v.agreements));
    return { ...v, currency: orNull(v.currency), agreements: v.agreements.map(agreement => {
      require(object(agreement) && id(agreement.agreementId) && text(agreement.agreementNo, 64) && typeof agreement.entityNo === 'string'
        && entityNoPattern.test(agreement.entityNo) && instant(agreement.effectiveFrom) && optional(instant)(agreement.effectiveTo) && array(agreement.products)
        && array(agreement.capabilities) && array(agreement.termVersions));
      const a = agreement as unknown as AgreementRateCard;
      return { ...a, businessMode: orNull(a.businessMode), fundMode: orNull(a.fundMode), pricingAuthority: orNull(a.pricingAuthority),
        effectiveTo: orNull(a.effectiveTo), capabilities: a.capabilities.map(decodeRateCardCapability), termVersions: a.termVersions.map(decodeTermVersionRateCard) };
    }) };
  };
}

export interface RateCardQuery { clientId: string; asOf?: string | null; currency?: string | null }
/** 费率卡查询路径：空值不带参数。 */
export function rateCardPath(base: string, query: RateCardQuery): string {
  const params = new URLSearchParams({ clientId: query.clientId.trim() });
  if (query.asOf) params.set('asOf', query.asOf);
  if (query.currency?.trim()) params.set('currency', query.currency.trim().toUpperCase());
  return `${base}?${params.toString()}`;
}

/** 开通项的显示名：支付方式 + 细分维度值。 */
export function capabilityLabel(capability: Pick<RateCardCapability, 'capabilityCode' | 'paymentMethodCode' | 'dimensionCode' | 'dimensionValue'>): string {
  const detail = [capability.paymentMethodCode, capability.dimensionCode && capability.dimensionValue ? `${capability.dimensionCode}=${capability.dimensionValue}` : null]
    .filter(Boolean).join(' · ');
  return detail ? `${capability.capabilityCode}（${detail}）` : capability.capabilityCode;
}

/** 截止秒数 → HH:mm:ss。 */
export function cutoffTime(seconds: number): string {
  const two = (n: number) => String(n).padStart(2, '0');
  return `${two(Math.floor(seconds / 3600))}:${two(Math.floor((seconds % 3600) / 60))}:${two(seconds % 60)}`;
}

// ---------- 价格试算 ----------

/** 维度事实的一行：键存在且「不适用」表示确认该维度不适用（值为 null）；值为空的行不提交（事实未知）。 */
export interface DimensionFactDraft { dimensionCode: Dimension; value?: string; notApplicable?: boolean }
export interface SimulationFormValues {
  termVersionId: string; entityNo: string; agreementId: string; termType: string; currency: string; contextAsOfTime: string; ownerId: string;
  paymentMethodCode?: string; dimensions?: DimensionFactDraft[]; amount?: string;
}

/** 表单 → 试算请求：Client 条款写 clientId，Partner 条款写 localPartnerId；时点转 UTC；同一维度只保留首行。 */
export function simulationBody(values: SimulationFormValues, partnerTerm: boolean) {
  const dimensions: Record<string, string | null> = {};
  for (const fact of values.dimensions ?? []) {
    if (!fact?.dimensionCode || Object.hasOwn(dimensions, fact.dimensionCode)) continue;
    if (fact.notApplicable) dimensions[fact.dimensionCode] = null;
    else if (fact.value?.trim()) dimensions[fact.dimensionCode] = fact.value.trim().toUpperCase();
  }
  const amount = values.amount?.trim();
  return {
    termVersionId: values.termVersionId.trim(), entityNo: values.entityNo.trim().toUpperCase(), agreementId: values.agreementId.trim(),
    termType: values.termType, currency: values.currency.trim().toUpperCase(), contextAsOfTime: values.contextAsOfTime,
    clientId: partnerTerm ? null : values.ownerId.trim(), localPartnerId: partnerTerm ? values.ownerId.trim() : null,
    paymentFacts: { paymentMethodCode: values.paymentMethodCode?.trim() ? values.paymentMethodCode.trim().toUpperCase() : null, dimensions },
    ...(amount ? { amount } : {}),
  };
}

/** BFF 422 字段路径 → 表单字段；Owner 字段 clientId / localPartnerId 对应 ownerId，维度事实整体不定位到单行。 */
export function simulationFieldName(field: string): Exclude<keyof SimulationFormValues, 'dimensions'> | null {
  const direct = ['termVersionId', 'entityNo', 'agreementId', 'termType', 'currency', 'contextAsOfTime', 'amount'] as const;
  const match = direct.find(name => name === field);
  if (match) return match;
  if (field === 'clientId' || field === 'localPartnerId') return 'ownerId';
  return field === 'paymentFacts.paymentMethodCode' ? 'paymentMethodCode' : null;
}

export interface ComponentIllustration {
  componentNo: number; componentCode: string; componentType: string; calculationBase: string; computed: boolean; rawAmount: string | null;
  amount: string | null; note: string | null;
}
export interface FeeEventIllustration { feeEventCode: string; totalAmount: string | null; components: ComponentIllustration[] }
export interface FeeIllustration { amount: string; currency: string; fractionDigits: number; feeEvents: FeeEventIllustration[] }
export interface Simulation { price: Price; illustration: FeeIllustration | null }

function decodeIllustration(value: unknown): FeeIllustration | null {
  if (value === null || value === undefined) return null;
  const v = value as FeeIllustration;
  require(object(v) && decimal(v.amount) && typeof v.currency === 'string' && Number.isInteger(v.fractionDigits) && array(v.feeEvents));
  return { ...v, feeEvents: v.feeEvents.map(event => {
    require(object(event) && typeof event.feeEventCode === 'string' && optional(decimal)(event.totalAmount) && array(event.components));
    return { feeEventCode: event.feeEventCode, totalAmount: orNull(event.totalAmount), components: event.components.map(component => {
      const c = component as ComponentIllustration;
      require(object(c) && Number.isInteger(c.componentNo) && typeof c.computed === 'boolean' && (!c.computed || (decimal(c.amount) && decimal(c.rawAmount))));
      return { ...c, rawAmount: orNull(c.rawAmount), amount: orNull(c.amount), note: orNull(c.note) };
    }) };
  }) };
}

/** 试算结果：命中价格的版本、OE 与币种必须与请求一致。 */
export function decodeSimulation(request: { termVersionId: string; entityNo: string; currency: string }) {
  return (value: unknown): Simulation => {
    const v = value as Simulation;
    require(object(v) && object(v.price));
    const price = decodePrice(v.price);
    const raw = v.price as unknown as Record<string, unknown>;
    require(raw.termVersionId === request.termVersionId && price.entityNo === request.entityNo && price.currency === request.currency);
    return { price, illustration: decodeIllustration(v.illustration) };
  };
}

/** commercial 命中失败的可读说明：错误码 + Owner 原因；其他错误返回 null 交给通用通知。 */
export const priceErrorLabels: Readonly<Record<string, string>> = {
  PRICING_FACTS_INCOMPLETE: '定价事实不完整：候选画像用到的维度未提供（确认不适用请勾选「不适用」）',
  PRICE_NOT_FOUND: '没有命中的价格：该版本、OE、币种与支付方式下没有匹配的画像',
  INVALID_PRICING_FACT: '定价事实不合法：维度代码或取值不在目录内',
  PRICE_AMBIGUOUS: '价格命中存在歧义或父链冲突：同具体度同优先级有多条命中，或版本 / 合同 / Owner / OE 与请求不一致',
};
export function simulationErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  const label = priceErrorLabels[error.code];
  if (label) return error.detail && error.detail !== label ? `${label}。commercial：${error.detail}（${error.code}）` : `${label}（${error.code}）`;
  if (error.status === 404) return '条款版本不存在。';
  if (error.status === 400 && error.code === 'BUSINESS_CHECK_FAILED') return `业务校验未通过：${ownerReason(error) ?? '请检查输入'}`;
  if (error.status === 400 && error.code === 'INVALID_REQUEST' && error.detail) return error.detail;
  return null;
}
