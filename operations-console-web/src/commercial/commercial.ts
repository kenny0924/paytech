import { ApiError, session } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import { configurationCommand } from '../configuration/configurationModel.ts';
import type { ConfigurationCommand } from '../configuration/configurationModel.ts';
import { enumLabel, translator } from '../i18n/index.ts';
import { decodeBenchmark, decodeCapabilityCondition, decodeVersionReview } from './termReview.ts';
import type { CapabilityCondition, ComponentBenchmark, VersionReview } from './termReview.ts';

/** 非组件代码在调用时通过 translator 取文案。 */
const t = translator('commercial');

// ---------- 路径与权限 ----------

/** Operations BFF 商务路径（/commercial-admin/v1）。 */
export const commercialPaths = {
  agreements: '/commercial-admin/v1/agreements',
  agreement: (agreementId: string) => `/commercial-admin/v1/agreements/${agreementId}`,
  submitForSignature: (agreementId: string) => `/commercial-admin/v1/agreements/${agreementId}/submit-for-signature`,
  acceptance: (agreementId: string, partyId: string) => `/commercial-admin/v1/agreements/${agreementId}/parties/${partyId}/acceptance`,
  activateAgreement: (agreementId: string) => `/commercial-admin/v1/agreements/${agreementId}/activate`,
  terminateAgreement: (agreementId: string) => `/commercial-admin/v1/agreements/${agreementId}/terminate`,
  products: (agreementId: string) => `/commercial-admin/v1/agreements/${agreementId}/products`,
  capabilities: (agreementId: string) => `/commercial-admin/v1/agreements/${agreementId}/capabilities`,
  capability: (agreementId: string, capabilityId: string) => `/commercial-admin/v1/agreements/${agreementId}/capabilities/${capabilityId}`,
  capabilitySuspension: (agreementId: string, capabilityId: string) => `/commercial-admin/v1/agreements/${agreementId}/capabilities/${capabilityId}/suspension`,
  capabilityResumption: (agreementId: string, capabilityId: string) => `/commercial-admin/v1/agreements/${agreementId}/capabilities/${capabilityId}/resumption`,
  prerequisiteChecks: (agreementId: string) => `/commercial-admin/v1/agreements/${agreementId}/prerequisite-checks`,
  profiles: '/commercial-admin/v1/pricing-profiles',
  profile: (profileId: string) => `/commercial-admin/v1/pricing-profiles/${profileId}`,
  profileAction: (profileId: string, action: 'activate' | 'retire') => `/commercial-admin/v1/pricing-profiles/${profileId}/${action}`,
  plans: '/commercial-admin/v1/commercial-terms/plans',
  versions: (planId: string) => `/commercial-admin/v1/commercial-terms/plans/${planId}/versions`,
  version: (versionId: string) => `/commercial-admin/v1/commercial-terms/versions/${versionId}`,
  prices: (versionId: string) => `/commercial-admin/v1/commercial-terms/versions/${versionId}/prices`,
  versionAction: (versionId: string, action: VersionAction) => `/commercial-admin/v1/commercial-terms/versions/${versionId}/${action}`,
  dashboard: '/commercial-admin/v1/dashboard',
  rateCards: '/commercial-admin/v1/rate-cards',
  simulate: '/commercial-admin/v1/commercial-prices/simulate',
  fillFromTemplate: (versionId: string) => `/commercial-admin/v1/commercial-terms/versions/${versionId}/fill-from-template`,
  versionAcceptance: (versionId: string) => `/commercial-admin/v1/commercial-terms/versions/${versionId}/acceptance`,
} as const;

/** 页面路由（与 Console Resource frontend_route 一致）。 */
export const commercialRoutes = {
  workbench: '/commercial/workbench',
  agreements: '/commercial/agreements',
  agreement: (agreementId: string) => `/commercial/agreements/${agreementId}`,
  profiles: '/commercial/pricing-profiles',
  terms: '/commercial/terms',
  version: (versionId: string) => `/commercial/terms/versions/${versionId}`,
  rateCards: '/commercial/rate-cards',
  priceSimulator: '/commercial/price-simulator',
  catalog: '/commercial/catalog',
  history: '/commercial/history',
  templates: '/commercial/templates',
} as const;

/**
 * 按钮资源：合同维护、画像维护、价目维护（创建 / 价格 / 提交 / 排期 / 激活）与价目审批（批准 / 驳回）；
 * 一期新增目录维护、标准价目维护 / 复核与价格协议签署登记。
 */
export const commercialButtons = {
  agreementManage: 'OPERATIONS_COMMERCIAL_AGREEMENT_MANAGE',
  profileManage: 'OPERATIONS_COMMERCIAL_PROFILE_MANAGE',
  termManage: 'OPERATIONS_COMMERCIAL_TERM_MANAGE',
  termApprove: 'OPERATIONS_COMMERCIAL_TERM_APPROVE',
  catalogManage: 'OPERATIONS_COMMERCIAL_CATALOG_MANAGE',
  templateManage: 'OPERATIONS_COMMERCIAL_TEMPLATE_MANAGE',
  templateApprove: 'OPERATIONS_COMMERCIAL_TEMPLATE_APPROVE',
  termSign: 'OPERATIONS_COMMERCIAL_TERM_SIGN',
} as const;

export function hasButton(resources: readonly ConsoleResource[], code: string): boolean {
  return resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);
}

// ---------- 枚举与文案 ----------

export const agreementTypes = ['CLIENT', 'PARTNER'] as const;
export const businessModes = ['MOR', 'PSP'] as const;
export const partyRoles = ['CLIENT', 'LOCAL_PARTNER', 'PLATFORM_SIGNATORY', 'OPERATING_ENTITY'] as const;
export const acceptanceMethods = ['E_SIGNATURE', 'CLICK_ACCEPTANCE', 'MANUAL_SIGNATURE'] as const;
/** 开通项合同成员资格：纳入 / 关闭；能否执行看 capabilityExecutionStatuses。 */
export const capabilityStatuses = ['ACTIVE', 'DISABLED'] as const;
/** 开通项执行状态（成员资格 DISABLED 时为空）：等前置、已开通、前置失效暂停、手动暂停。 */
export const capabilityExecutionStatuses = ['PENDING_PREREQUISITES', 'ENABLED', 'SUSPENDED_BY_PREREQUISITE', 'SUSPENDED_MANUALLY'] as const;
/** 前置项与结论（commercial 闭合枚举）；原因码为开放字符串，未知码显示原值。 */
export const prerequisiteCodes = ['QUALIFICATION', 'AGREEMENT_TERM', 'CHANNEL_IDENTITY'] as const;
export const prerequisiteResults = ['SATISFIED', 'UNSATISFIED', 'UNKNOWN', 'SKIPPED'] as const;
export const profileKinds = ['PAYMENT_METHOD', 'PAYOUT_RAIL', 'FX_PAIR', 'DISPUTE', 'PERIODIC'] as const;
export const profileStatuses = ['DRAFT', 'ACTIVE', 'RETIRED'] as const;
export const dimensionCodes = ['CARD_BRAND', 'FUNDING_TYPE', 'CARD_LEVEL', 'CARD_PRODUCT', 'ISSUER_REGION', 'ISSUER_COUNTRY', 'THREE_DS_RESULT',
  'CURRENCY', 'CONSUMER_COUNTRY', 'TRANSACTION_TYPE', 'PAYOUT_RAIL', 'DESTINATION_COUNTRY', 'CURRENCY_PAIR', 'DISPUTE_STAGE'] as const;
/** 画像种类允许的维度（与 commercial ProfileConditionEntry 说明一致）。 */
export const kindDimensions: Readonly<Record<ProfileKind, readonly Dimension[]>> = {
  PAYMENT_METHOD: ['CARD_BRAND', 'FUNDING_TYPE', 'CARD_LEVEL', 'CARD_PRODUCT', 'ISSUER_REGION', 'ISSUER_COUNTRY', 'THREE_DS_RESULT', 'CURRENCY',
    'CONSUMER_COUNTRY', 'TRANSACTION_TYPE'],
  PAYOUT_RAIL: ['PAYOUT_RAIL', 'DESTINATION_COUNTRY', 'CURRENCY'],
  FX_PAIR: ['CURRENCY_PAIR'],
  DISPUTE: ['DISPUTE_STAGE', 'CARD_BRAND', 'CURRENCY'],
  PERIODIC: [],
};
export const termTypes = ['CLIENT_PRICING', 'PARTNER_SHARE', 'PLATFORM_SERVICE_CHARGE', 'PARTNER_BUY_RATE'] as const;
/** Partner 条款类型：Owner 为 Local Partner，价格无结算四字段。 */
export const partnerTermTypes: readonly string[] = ['PARTNER_SHARE', 'PLATFORM_SERVICE_CHARGE', 'PARTNER_BUY_RATE'];
export const componentTypes = ['PERCENTAGE', 'FIXED', 'TIERED'] as const;
export const calculationBases = ['GROSS_AMOUNT', 'NET_AMOUNT', 'ACTUAL_CLIENT_FEE', 'FIXED_PER_EVENT', 'BUY_RATE_DIFFERENCE'] as const;
export const refundBehaviors = ['RETURN_ALL', 'RETURN_PRORATA', 'KEEP'] as const;
export const bandBases = ['TRANSACTION_AMOUNT', 'PERIOD_VOLUME'] as const;
export const bandModes = ['WHOLE_BASE', 'PROGRESSIVE', 'RETROACTIVE'] as const;
export const pricingModels = ['BLENDED', 'INTERCHANGE_PLUS_PLUS'] as const;
export const feeTaxTreatments = ['INCLUSIVE', 'EXCLUSIVE'] as const;
export const versionStatuses = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'CLIENT_SIGNED', 'SCHEDULED', 'ACTIVE', 'SUPERSEDED', 'EXPIRED'] as const;
export const agreementStatuses = ['DRAFT', 'PENDING_SIGNATURE', 'SIGNED', 'ACTIVE', 'EXPIRED', 'TERMINATED', 'CANCELLED'] as const;
/** 本期结算日历固定码（technique §2.10）。 */
export const defaultBusinessCalendar = 'AE_BUSINESS_DAYS';

export type AgreementType = typeof agreementTypes[number];
export type PartyRole = typeof partyRoles[number];
export type AcceptanceMethod = typeof acceptanceMethods[number];
export type CapabilityStatus = typeof capabilityStatuses[number];
export type CapabilityExecutionStatus = typeof capabilityExecutionStatuses[number];
export type PrerequisiteCode = typeof prerequisiteCodes[number];
export type PrerequisiteResult = typeof prerequisiteResults[number];
export type ProfileKind = typeof profileKinds[number];
export type ProfileStatus = typeof profileStatuses[number];
export type Dimension = typeof dimensionCodes[number];
export type ComponentType = typeof componentTypes[number];
export type VersionStatus = typeof versionStatuses[number];
export type AgreementStatus = typeof agreementStatuses[number];
export type VersionAction = 'submit' | 'approve' | 'reject' | 'schedule' | 'activate';

/**
 * 枚举显示名在 commercial 命名空间，用 enumLabel 取（未知代码原样显示）：enum.agreementStatus、enum.versionStatus、
 * enum.profileStatus、enum.AgreementCapabilityStatus、enum.AgreementCapabilityExecutionStatus、enum.CapabilityPrerequisiteCode、
 * enum.CapabilityPrerequisiteResult、enum.CapabilityPrerequisiteReason、enum.agreementType、enum.partyRole、enum.acceptanceMethod、enum.profileKind、
 * enum.pricingModel、enum.feeTaxTreatment、enum.componentType、enum.termType。
 */
/** 按当前语言连接若干段文本：kind=list 为并列项（、/ ,），kind=clauses 为分句（；/ ;）。 */
export function joinList(items: readonly string[], kind: 'list' | 'clauses' = 'list'): string {
  return items.reduce((head, tail) => t(kind === 'list' ? 'join.list' : 'join.clauses', { head, tail }));
}
/** 版本口径展示：定价模型 · 含税口径，未知代码原样显示。 */
export const versionBasisLabel = (pricingModel: string, feeTaxTreatment: string) =>
  `${enumLabel(t, 'enum.pricingModel', pricingModel)} · ${enumLabel(t, 'enum.feeTaxTreatment', feeTaxTreatment)}`;
/** 状态标签色：生效 / 开通为绿，待办为橙或蓝，结束为灰，暂停为金（开通项两种暂停按原型 14—16 为红）。 */
export const commercialStatusColors: Readonly<Record<string, string>> = {
  ACTIVE: 'green', ENABLED: 'green', SIGNED: 'cyan', APPROVED: 'cyan', CLIENT_SIGNED: 'cyan', SCHEDULED: 'blue', PENDING_SIGNATURE: 'orange', PENDING_APPROVAL: 'orange',
  PENDING_PREREQUISITES: 'orange', SUSPENDED_BY_PREREQUISITE: 'red', SUSPENDED_MANUALLY: 'red',
  DRAFT: 'default', SUSPENDED: 'gold', DISABLED: 'default', RETIRED: 'default', SUPERSEDED: 'default', EXPIRED: 'default', TERMINATED: 'red', CANCELLED: 'default',
};

// ---------- 读模型 ----------

export interface AgreementParty {
  agreementPartyId: string; partyId: string; partyProfileId: string; partyRole: PartyRole; acceptanceMethod: AcceptanceMethod | null;
  acceptedByActorId: string | null; acceptedAt: string | null; acceptanceReference: string | null; evidenceDocumentId: string | null;
}
export interface AgreementDocument { agreementDocumentId: string; documentType: string; fileId: string; contentHash: string }
export interface AgreementCapability {
  agreementCapabilityId: string; entityNo: string; capabilityCode: string; status: CapabilityStatus;
  /** 执行状态；成员资格 DISABLED 时为 null。 */
  executionStatus: CapabilityExecutionStatus | null; effectiveFrom: string; effectiveTo: string | null;
  rollingReserveRate: string | null; rollingReserveDays: number | null; conditions: CapabilityCondition[]; require3ds: boolean;
}
export interface Agreement {
  agreementId: string; agreementNo: string; agreementName: string; agreementType: AgreementType; businessMode: string | null; fundMode: string | null;
  pricingAuthority: string | null; referralPartnerId: string | null; clientId: string | null; localPartnerId: string | null; entityNo: string | null;
  operatingEntityNos: string[]; status: AgreementStatus; effectiveFrom: string; effectiveTo: string | null; signedTime: string | null;
  activatedTime: string | null; changeVersion: number; parties: AgreementParty[]; documents: AgreementDocument[]; productCodes: string[];
  capabilities: AgreementCapability[];
}
export interface AgreementTermVersion {
  planId: string; termType: string; versionId: string; versionNo: string; status: VersionStatus; timing: 'CURRENT' | 'SCHEDULED';
  effectiveFrom: string; effectiveTo: string | null; pricingModel: string; feeTaxTreatment: string;
}
export type SectionStatus = 'AVAILABLE' | 'UNAVAILABLE' | 'NOT_APPLICABLE';
export interface AgreementOverview { agreement: Agreement; termVersionsStatus: SectionStatus; termVersions: AgreementTermVersion[] }
export interface AgreementWriteResult { agreementId: string; agreementNo: string; status: AgreementStatus; changeVersion: number }
export interface CapabilityWriteResult {
  agreementId: string; agreementCapabilityId: string; status: CapabilityStatus; executionStatus: CapabilityExecutionStatus | null; changeVersion: number;
}
/** 一项前置的结论；reasonCode 在 SATISFIED 时为 null，params 为文案参数。 */
export interface PrerequisiteCheck { code: PrerequisiteCode; result: PrerequisiteResult; reasonCode: string | null; params: Record<string, string> }
/** 一个开通项的前置核对结果（DISABLED 的 checks 为空）。 */
export interface CapabilityPrerequisites {
  agreementCapabilityId: string; entityNo: string; capabilityCode: string; capabilityKind: string; status: CapabilityStatus;
  executionStatus: CapabilityExecutionStatus | null; checks: PrerequisiteCheck[];
}
/** 合同前置核对：核对后的合同 changeVersion（有状态迁移时已推进）与全部开通项结果。 */
export interface PrerequisiteCheckResult { agreementId: string; changeVersion: number; capabilities: CapabilityPrerequisites[] }

export interface ProfileCondition { dimensionCode: Dimension; conditionValue: string }
export interface PricingProfile {
  pricingProfileId: string; pricingProfileCode: string; profileName: string; profileKind: ProfileKind; paymentMethodCode: string | null;
  status: ProfileStatus; changeVersion: number; conditions: ProfileCondition[];
}
export interface TermPlan {
  planId: string; planCode: string; termType: string; agreementId: string | null; entityNo: string | null; clientId: string | null;
  localPartnerId: string | null; status: string; changeVersion: string; ownerType: string | null; templateName: string | null;
}
/**
 * 条款版本；模板版本没有合同（agreementId 为 null，ownerType=PLATFORM_TEMPLATE）。
 * VersionReview 为一期新增的破标 / 来源 / 签署 / 相关性字段；ownerType 旧回执缺省为 null。
 */
export interface TermVersion extends VersionReview {
  versionId: string; planId: string; termType: string; agreementId: string | null; versionNo: string; status: VersionStatus; effectiveFrom: string;
  effectiveTo: string | null; roundingMode: string; pricingModel: string; feeTaxTreatment: string; changeVersion: string; ownerType: OwnerType | null;
}
/** 方案 Owner 类型：Client、Local Partner 或平台标准价目。 */
export const ownerTypes = ['CLIENT', 'LOCAL_PARTNER', 'PLATFORM_TEMPLATE'] as const;
export type OwnerType = typeof ownerTypes[number];
export interface PriceBand { bandId: string; bandNo: number; lowerBound: string; upperBound: string | null; rate: string | null; fixedAmount: string | null; currency: string | null }
export interface PriceComponent {
  componentId: string; componentNo: number; componentCode: string; componentType: ComponentType; triggerCode: string; calculationBase: string;
  refundBehavior: string; rate: string | null; fixedAmount: string | null; currency: string | null; minAmount: string | null; maxAmount: string | null;
  bandBasis: string | null; bandMode: string | null; bands: PriceBand[]; benchmark: ComponentBenchmark | null;
}
export interface PriceSettlement { settlementBusinessDays: number; businessCalendarCode: string; settlementTimezone: string; cutoffSecondOfDay: number }
export interface Price {
  priceId: string; entityNo: string; currency: string; pricingProfileCode: string; paymentMethodCode: string | null; priorityNo: number;
  conditions: ProfileCondition[]; components: PriceComponent[]; settlement: PriceSettlement | null; sourceTemplatePriceId: string | null;
}
export interface TermScope {
  agreementId: string; agreementNo: string; agreementName: string; agreementType: AgreementType; status: AgreementStatus; clientId: string | null;
  localPartnerId: string | null; entityNos: string[]; capabilities: AgreementCapability[];
}
export interface TermWorkspace { version: TermVersion; agreement: TermScope; prices: Price[] }

// ---------- 解码 ----------

export const idPattern = /^[1-9][0-9]{0,18}$/;
export const entityNoPattern = /^[A-Z]{2}[0-9]{3}$/;
export const currencyPattern = /^[A-Z]{3}$/;
export const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
const decimalPattern = /^(0|[1-9][0-9]*)(\.[0-9]+)?$/;

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && idPattern.test(v);
const text = (v: unknown, max = 256) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const decimal = (v: unknown) => typeof v === 'string' && decimalPattern.test(v);
const positiveInteger = (v: unknown) => Number.isInteger(v) && Number(v) > 0;
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
const array = (v: unknown): v is unknown[] => Array.isArray(v);
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

export function decodeCapability(value: unknown): AgreementCapability {
  const v = value as AgreementCapability;
  require(object(v) && id(v.agreementCapabilityId) && typeof v.entityNo === 'string' && entityNoPattern.test(v.entityNo) && text(v.capabilityCode, 64)
    && oneOf(capabilityStatuses)(v.status) && optional(oneOf(capabilityExecutionStatuses))(v.executionStatus) && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo) && optional(decimal)(v.rollingReserveRate)
    && optional(positiveInteger)(v.rollingReserveDays) && array(v.conditions) && optional((x: unknown) => typeof x === 'boolean')(v.require3ds));
  return { ...v, executionStatus: orNull(v.executionStatus), effectiveTo: orNull(v.effectiveTo), rollingReserveRate: orNull(v.rollingReserveRate), rollingReserveDays: orNull(v.rollingReserveDays),
    conditions: v.conditions.map(decodeCapabilityCondition), require3ds: v.require3ds === true };
}

function decodeParty(value: unknown): AgreementParty {
  const v = value as AgreementParty;
  require(object(v) && id(v.agreementPartyId) && id(v.partyId) && id(v.partyProfileId) && oneOf(partyRoles)(v.partyRole)
    && optional(oneOf(acceptanceMethods))(v.acceptanceMethod) && optional(id)(v.acceptedByActorId) && optional(instant)(v.acceptedAt)
    && optional(text)(v.acceptanceReference) && optional(id)(v.evidenceDocumentId));
  return { ...v, acceptanceMethod: orNull(v.acceptanceMethod), acceptedByActorId: orNull(v.acceptedByActorId), acceptedAt: orNull(v.acceptedAt),
    acceptanceReference: orNull(v.acceptanceReference), evidenceDocumentId: orNull(v.evidenceDocumentId) };
}

function decodeDocument(value: unknown): AgreementDocument {
  require(object(value) && id(value.agreementDocumentId) && text(value.documentType, 64) && id(value.fileId) && typeof value.contentHash === 'string'
    && /^[0-9a-f]{64}$/.test(value.contentHash));
  return value as unknown as AgreementDocument;
}

/** 合同详情：合同 ID 必须与请求一致。 */
export function decodeAgreement(value: unknown, agreementId?: string): Agreement {
  const v = value as Agreement;
  require(object(v) && id(v.agreementId) && (!agreementId || v.agreementId === agreementId) && text(v.agreementNo, 64) && text(v.agreementName, 255)
    && oneOf(agreementTypes)(v.agreementType) && oneOf(agreementStatuses)(v.status) && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo)
    && optional(instant)(v.signedTime) && optional(instant)(v.activatedTime) && positiveInteger(v.changeVersion) && optional(id)(v.clientId)
    && optional(id)(v.localPartnerId) && optional(id)(v.referralPartnerId) && optional((e: unknown) => typeof e === 'string' && entityNoPattern.test(e))(v.entityNo)
    && optional(array)(v.operatingEntityNos) && array(v.parties) && array(v.documents) && array(v.productCodes) && array(v.capabilities)
    && v.productCodes.every(code => text(code, 64)));
  return {
    ...v, businessMode: orNull(v.businessMode), fundMode: orNull(v.fundMode), pricingAuthority: orNull(v.pricingAuthority),
    referralPartnerId: orNull(v.referralPartnerId), clientId: orNull(v.clientId), localPartnerId: orNull(v.localPartnerId), entityNo: orNull(v.entityNo),
    operatingEntityNos: v.operatingEntityNos ?? [], effectiveTo: orNull(v.effectiveTo), signedTime: orNull(v.signedTime), activatedTime: orNull(v.activatedTime),
    parties: v.parties.map(decodeParty), documents: v.documents.map(decodeDocument), capabilities: v.capabilities.map(decodeCapability),
  };
}

function decodeAgreementTermVersion(value: unknown): AgreementTermVersion {
  const v = value as AgreementTermVersion;
  require(object(v) && id(v.planId) && text(v.termType, 64) && id(v.versionId) && text(v.versionNo, 19) && oneOf(versionStatuses)(v.status)
    && oneOf(['CURRENT', 'SCHEDULED'])(v.timing) && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo));
  return { ...v, effectiveTo: orNull(v.effectiveTo) };
}

/** 合同详情聚合：版本部分状态与列表一致（非 AVAILABLE 时为空）。 */
export function decodeAgreementOverview(agreementId: string) {
  return (value: unknown): AgreementOverview => {
    const v = value as AgreementOverview;
    require(object(v) && oneOf(['AVAILABLE', 'UNAVAILABLE', 'NOT_APPLICABLE'])(v.termVersionsStatus) && array(v.termVersions)
      && (v.termVersionsStatus === 'AVAILABLE' || v.termVersions.length === 0));
    return { agreement: decodeAgreement(v.agreement, agreementId), termVersionsStatus: v.termVersionsStatus,
      termVersions: v.termVersions.map(decodeAgreementTermVersion) };
  };
}

export function decodeAgreementWrite(agreementId?: string) {
  return (value: unknown): AgreementWriteResult => {
    const v = value as AgreementWriteResult;
    require(object(v) && id(v.agreementId) && (!agreementId || v.agreementId === agreementId) && text(v.agreementNo, 64)
      && oneOf(agreementStatuses)(v.status) && positiveInteger(v.changeVersion));
    return v;
  };
}

export function decodeCapabilityWrite(agreementId: string) {
  return (value: unknown): CapabilityWriteResult => {
    const v = value as CapabilityWriteResult;
    require(object(v) && v.agreementId === agreementId && id(v.agreementCapabilityId) && oneOf(capabilityStatuses)(v.status)
      && optional(oneOf(capabilityExecutionStatuses))(v.executionStatus) && positiveInteger(v.changeVersion));
    return { ...v, executionStatus: orNull(v.executionStatus) };
  };
}

function decodePrerequisiteCheck(value: unknown): PrerequisiteCheck {
  const v = value as PrerequisiteCheck;
  require(object(v) && oneOf(prerequisiteCodes)(v.code) && oneOf(prerequisiteResults)(v.result) && optional(text)(v.reasonCode)
    && optional(object)(v.params) && Object.values(v.params ?? {}).every(param => typeof param === 'string'));
  return { code: v.code, result: v.result, reasonCode: orNull(v.reasonCode), params: v.params ?? {} };
}

function decodeCapabilityPrerequisites(value: unknown): CapabilityPrerequisites {
  const v = value as CapabilityPrerequisites;
  require(object(v) && id(v.agreementCapabilityId) && typeof v.entityNo === 'string' && entityNoPattern.test(v.entityNo) && text(v.capabilityCode, 64)
    && text(v.capabilityKind, 64) && oneOf(capabilityStatuses)(v.status) && optional(oneOf(capabilityExecutionStatuses))(v.executionStatus) && array(v.checks));
  return { ...v, executionStatus: orNull(v.executionStatus), checks: v.checks.map(decodePrerequisiteCheck) };
}

/** 前置核对结果：合同 ID 必须与请求一致。 */
export function decodePrerequisiteCheckResult(agreementId: string) {
  return (value: unknown): PrerequisiteCheckResult => {
    const v = value as PrerequisiteCheckResult;
    require(object(v) && v.agreementId === agreementId && positiveInteger(v.changeVersion) && array(v.capabilities));
    return { agreementId, changeVersion: v.changeVersion, capabilities: v.capabilities.map(decodeCapabilityPrerequisites) };
  };
}

/**
 * 立即核对合同开通项前置（POST，可能迁移执行状态；无 Body）。每次点击是一次新的核对，使用新的 Idempotency-Key。
 */
export function checkPrerequisites(agreementId: string): Promise<PrerequisiteCheckResult> {
  return session.request(commercialPaths.prerequisiteChecks(agreementId), { method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() } }, decodePrerequisiteCheckResult(agreementId));
}

export function decodeCondition(value: unknown): ProfileCondition {
  require(object(value) && oneOf(dimensionCodes)(value.dimensionCode) && text(value.conditionValue, 128));
  return value as unknown as ProfileCondition;
}

export function decodeProfile(value: unknown): PricingProfile {
  const v = value as PricingProfile;
  require(object(v) && id(v.pricingProfileId) && text(v.pricingProfileCode, 64) && text(v.profileName, 128) && oneOf(profileKinds)(v.profileKind)
    && optional(text)(v.paymentMethodCode) && oneOf(profileStatuses)(v.status) && positiveInteger(v.changeVersion) && array(v.conditions));
  return { ...v, paymentMethodCode: orNull(v.paymentMethodCode), conditions: v.conditions.map(decodeCondition) };
}

export function decodeProfileList(value: unknown): PricingProfile[] {
  const v = value as { profiles: unknown[] };
  require(object(v) && array(v.profiles));
  return v.profiles.map(decodeProfile);
}

export function decodePlan(value: unknown): TermPlan {
  const v = value as TermPlan;
  require(object(v) && id(v.planId) && text(v.planCode, 64) && text(v.termType, 64) && optional(id)(v.agreementId) && text(v.status, 32) && id(v.changeVersion));
  return { ...v, agreementId: orNull(v.agreementId), entityNo: orNull(v.entityNo), clientId: orNull(v.clientId), localPartnerId: orNull(v.localPartnerId),
    ownerType: orNull(v.ownerType), templateName: orNull(v.templateName) };
}

export function decodeVersion(versionId?: string) {
  return (value: unknown): TermVersion => {
    const v = value as TermVersion;
    require(object(v) && id(v.versionId) && (!versionId || v.versionId === versionId) && id(v.planId) && text(v.termType, 64) && optional(id)(v.agreementId)
      && text(v.versionNo, 19) && oneOf(versionStatuses)(v.status) && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo)
      && text(v.pricingModel, 32) && text(v.feeTaxTreatment, 32) && id(v.changeVersion) && optional(oneOf(ownerTypes))(v.ownerType));
    return { ...v, agreementId: orNull(v.agreementId), effectiveTo: orNull(v.effectiveTo), ownerType: orNull(v.ownerType),
      ...decodeVersionReview(v as unknown as Record<string, unknown>) };
  };
}

function decodeBand(value: unknown): PriceBand {
  const v = value as PriceBand;
  require(object(v) && positiveInteger(v.bandNo) && decimal(v.lowerBound) && optional(decimal)(v.upperBound) && optional(decimal)(v.rate)
    && optional(decimal)(v.fixedAmount));
  return { ...v, upperBound: orNull(v.upperBound), rate: orNull(v.rate), fixedAmount: orNull(v.fixedAmount), currency: orNull(v.currency) };
}

export function decodeComponent(value: unknown): PriceComponent {
  const v = value as PriceComponent;
  require(object(v) && positiveInteger(v.componentNo) && text(v.componentCode, 64) && oneOf(componentTypes)(v.componentType) && text(v.triggerCode, 64)
    && text(v.calculationBase, 64) && text(v.refundBehavior, 32) && optional(decimal)(v.rate) && optional(decimal)(v.fixedAmount)
    && optional(decimal)(v.minAmount) && optional(decimal)(v.maxAmount) && optional(array)(v.bands));
  return { ...v, rate: orNull(v.rate), fixedAmount: orNull(v.fixedAmount), currency: orNull(v.currency), minAmount: orNull(v.minAmount),
    maxAmount: orNull(v.maxAmount), bandBasis: orNull(v.bandBasis), bandMode: orNull(v.bandMode), bands: (v.bands ?? []).map(decodeBand),
    benchmark: decodeBenchmark(v.benchmark) };
}

export function decodeSettlement(value: unknown): PriceSettlement | null {
  if (value === null || value === undefined) return null;
  require(object(value) && Number.isInteger(value.settlementBusinessDays) && text(value.businessCalendarCode, 64) && text(value.settlementTimezone, 64)
    && Number.isInteger(value.cutoffSecondOfDay));
  return value as unknown as PriceSettlement;
}

export function decodePrice(value: unknown): Price {
  const v = value as Price;
  require(object(v) && typeof v.entityNo === 'string' && entityNoPattern.test(v.entityNo) && typeof v.currency === 'string' && currencyPattern.test(v.currency)
    && text(v.pricingProfileCode, 64) && positiveInteger(v.priorityNo) && array(v.conditions) && array(v.components) && v.components.length > 0
    && optional(id)(v.sourceTemplatePriceId));
  return { priceId: String(v.priceId ?? ''), entityNo: v.entityNo, currency: v.currency, pricingProfileCode: v.pricingProfileCode,
    paymentMethodCode: orNull(v.paymentMethodCode), priorityNo: v.priorityNo, conditions: v.conditions.map(decodeCondition),
    components: v.components.map(decodeComponent), settlement: decodeSettlement(v.settlement), sourceTemplatePriceId: orNull(v.sourceTemplatePriceId) };
}

/** 版本编辑页聚合：版本 ID 与请求一致，价格只属于合同范围内的 OE。 */
export function decodeWorkspace(versionId: string) {
  return (value: unknown): TermWorkspace => {
    const v = value as TermWorkspace;
    require(object(v) && object(v.agreement) && array(v.prices));
    const version = decodeVersion(versionId)(v.version);
    const scope = v.agreement;
    require(id(scope.agreementId) && scope.agreementId === version.agreementId && oneOf(agreementTypes)(scope.agreementType)
      && oneOf(agreementStatuses)(scope.status) && array(scope.entityNos) && scope.entityNos.every(e => typeof e === 'string' && entityNoPattern.test(e))
      && array(scope.capabilities));
    const prices = v.prices.map(decodePrice);
    require(prices.every(price => scope.entityNos.includes(price.entityNo)));
    return { version, agreement: { ...scope, clientId: orNull(scope.clientId), localPartnerId: orNull(scope.localPartnerId),
      capabilities: scope.capabilities.map(decodeCapability) }, prices };
  };
}

// ---------- 状态机：哪些动作可见 ----------

export interface VersionPermissions { canManage: boolean; canApprove: boolean }
/** 版本按钮：草稿可编辑价格 / 提交；待审批可批准 / 驳回（审批权限）；已批准可排期；已排期可手工激活；任何状态可另起草稿。 */
export function versionActions(status: VersionStatus, permissions: VersionPermissions) {
  const { canManage, canApprove } = permissions;
  return {
    editPrices: canManage && status === 'DRAFT',
    submit: canManage && status === 'DRAFT',
    approve: canApprove && status === 'PENDING_APPROVAL',
    reject: canApprove && status === 'PENDING_APPROVAL',
    schedule: canManage && status === 'APPROVED',
    activate: canManage && status === 'SCHEDULED',
    copy: canManage,
  };
}

/** 合同按钮：草稿可提交签署；待签署可登记签署；已签署可激活；生效可终止；订阅与开通项在草稿至生效期间可维护。 */
export function agreementActions(status: AgreementStatus, canManage: boolean) {
  const editable = canManage && ['DRAFT', 'PENDING_SIGNATURE', 'SIGNED', 'ACTIVE'].includes(status);
  return {
    submit: canManage && status === 'DRAFT',
    accept: canManage && status === 'PENDING_SIGNATURE',
    activate: canManage && status === 'SIGNED',
    terminate: canManage && status === 'ACTIVE',
    products: editable,
    capabilities: editable,
  };
}

// ---------- 价格矩阵 ----------

/** 矩阵的一行：OE × 币种 × 画像；每个计费事件一格，格内是该事件下的全部组件。 */
export interface MatrixRow { key: string; entityNo: string; currency: string; pricingProfileCode: string; priorityNo: number; price: Price; events: Record<string, PriceComponent[]> }
export interface PriceMatrix { events: string[]; rows: MatrixRow[] }

/** 按 OE、币种、优先级排行，按计费事件分列；事件列按代码排序。 */
export function priceMatrix(prices: readonly Price[]): PriceMatrix {
  const events = new Set<string>();
  const rows = prices.map(price => {
    const byEvent: Record<string, PriceComponent[]> = {};
    for (const component of price.components) {
      events.add(component.triggerCode);
      (byEvent[component.triggerCode] ??= []).push(component);
    }
    return { key: `${price.entityNo}|${price.currency}|${price.pricingProfileCode}`, entityNo: price.entityNo, currency: price.currency,
      pricingProfileCode: price.pricingProfileCode, priorityNo: price.priorityNo, price, events: byEvent };
  }).sort((a, b) => a.entityNo.localeCompare(b.entityNo) || a.currency.localeCompare(b.currency) || a.priorityNo - b.priorityNo);
  return { events: [...events].sort(), rows };
}

/** 已排序矩阵里按分组键合并单元格：组内首行跨整组，其余行为 0。 */
export function groupRowSpan<T>(rows: readonly T[], index: number, groupKey: (row: T) => string): number {
  const key = groupKey(rows[index]);
  if (index > 0 && groupKey(rows[index - 1]) === key) return 0;
  let span = 1;
  while (index + span < rows.length && groupKey(rows[index + span]) === key) span++;
  return span;
}

/** 组件的一句话摘要：2.9% (min 0.50 USD) + …；阶梯显示档数与基数。 */
export function componentSummary(component: Pick<PriceComponent, 'componentType' | 'rate' | 'fixedAmount' | 'currency' | 'minAmount' | 'maxAmount' | 'bands' | 'bandMode'>): string {
  if (component.componentType === 'PERCENTAGE') {
    const bounds = [component.minAmount && `min ${component.minAmount}`, component.maxAmount && `max ${component.maxAmount}`].filter(Boolean).join(' / ');
    return `${percent(component.rate)}${bounds ? ` (${bounds}${component.currency ? ` ${component.currency}` : ''})` : ''}`;
  }
  if (component.componentType === 'FIXED') return `${component.fixedAmount ?? '—'} ${component.currency ?? ''}`.trim();
  return `${t('component.tieredSummary', { count: component.bands.length })}${component.bandMode ? ` · ${component.bandMode}` : ''}`;
}

/** 0.029 → 2.9%；保留原始精度，不做浮点舍入。 */
export function percent(rate: string | null): string {
  if (rate === null || !decimalPattern.test(rate)) return '—';
  const [integer, fraction = ''] = rate.split('.');
  const padded = fraction.padEnd(2, '0');
  const whole = `${integer}${padded.slice(0, 2)}`.replace(/^0+(?=\d)/, '');
  const rest = padded.slice(2).replace(/0+$/, '');
  return `${whole}${rest ? `.${rest}` : ''}%`;
}

/**
 * 覆盖提示（对应激活校验 3 的近似）：合同下每个成员资格 ACTIVE 的开通项（含等前置与已暂停）× 其 CURRENCY 接受条件的每个币种，在同 OE 同币种下至少应有一条价格。
 * 只按 OE + 币种粗查，不模拟画像条件命中；最终以 commercial 排期校验为准。
 */
export function coverageGaps(capabilities: readonly AgreementCapability[], prices: readonly Pick<Price, 'entityNo' | 'currency'>[]): string[] {
  const priced = new Set(prices.map(price => `${price.entityNo}/${price.currency}`));
  const gaps = new Set<string>();
  for (const capability of capabilities) {
    if (capability.status !== 'ACTIVE') continue;
    for (const currency of capability.conditions.find(condition => condition.dimensionCode === 'CURRENCY')?.values ?? []) {
      const key = `${capability.entityNo}/${currency}`;
      if (!priced.has(key)) gaps.add(t('coverage.gap', { key, capabilityCode: capability.capabilityCode }));
    }
  }
  return [...gaps].sort();
}

// ---------- 价格编辑模型 ----------

export interface BandDraft { lowerBound: string; upperBound: string | null; rate: string | null; fixedAmount: string | null }
export interface ComponentDraft {
  componentCode: string; componentType: ComponentType; triggerCode: string; calculationBase: string; refundBehavior: string; rate: string | null;
  fixedAmount: string | null; currency: string | null; minAmount: string | null; maxAmount: string | null; bandBasis: string | null; bandMode: string | null;
  bands: BandDraft[] | null;
}
export interface PriceDraft {
  entityNo: string; currency: string; pricingProfileCode: string; priorityNo: number; settlementBusinessDays: number | null;
  businessCalendarCode: string | null; settlementTimezone: string | null; cutoffSecondOfDay: number | null; components: ComponentDraft[];
  /** 来源模板价格（基于模板生成 / 补齐）；保存时原样回传以保持来源，手工新增为空。 */
  sourceTemplatePriceId?: string | null;
}

const blank = (value: string | null | undefined) => {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
};

/** 读模型 → 编辑草稿：去掉价格 / 组件 / 档位主键、序号与派生字段；非阶梯组件 bands 为 null。 */
export function priceDraft(price: Price): PriceDraft {
  return {
    entityNo: price.entityNo, currency: price.currency, pricingProfileCode: price.pricingProfileCode, priorityNo: price.priorityNo,
    settlementBusinessDays: price.settlement?.settlementBusinessDays ?? null, businessCalendarCode: price.settlement?.businessCalendarCode ?? null,
    settlementTimezone: price.settlement?.settlementTimezone ?? null, cutoffSecondOfDay: price.settlement?.cutoffSecondOfDay ?? null,
    sourceTemplatePriceId: price.sourceTemplatePriceId,
    components: price.components.map(component => ({
      componentCode: component.componentCode, componentType: component.componentType, triggerCode: component.triggerCode,
      calculationBase: component.calculationBase, refundBehavior: component.refundBehavior, rate: component.rate, fixedAmount: component.fixedAmount,
      currency: component.currency, minAmount: component.minAmount, maxAmount: component.maxAmount, bandBasis: component.bandBasis,
      bandMode: component.bandMode,
      bands: component.componentType === 'TIERED' ? component.bands.map(band => ({ lowerBound: band.lowerBound, upperBound: band.upperBound,
        rate: band.rate, fixedAmount: band.fixedAmount })) : null,
    })),
  };
}

/** 新价格的默认草稿：Client 条款带结算四字段默认值，Partner 条款为空；一个百分比组件。 */
export function newPriceDraft(termType: string, entityNo: string, currency: string, pricingProfileCode: string, priorityNo: number): PriceDraft {
  const partner = partnerTermTypes.includes(termType);
  return {
    entityNo, currency, pricingProfileCode, priorityNo,
    settlementBusinessDays: partner ? null : 2, businessCalendarCode: partner ? null : defaultBusinessCalendar,
    settlementTimezone: partner ? null : 'UTC', cutoffSecondOfDay: partner ? null : 0,
    components: [{ componentCode: 'MDR', componentType: 'PERCENTAGE', triggerCode: 'PAYMENT_CAPTURED', calculationBase: 'GROSS_AMOUNT',
      refundBehavior: 'RETURN_PRORATA', rate: null, fixedAmount: null, currency: null, minAmount: null, maxAmount: null, bandBasis: null,
      bandMode: null, bands: null }],
  };
}

/** 编辑草稿 → PUT 价格请求：去空白，按组件类型清理无关字段（阶梯头部无费率 / 金额，非阶梯无档位）。 */
export function pricesBody(expectedChangeVersion: string, drafts: readonly PriceDraft[]) {
  return {
    expectedChangeVersion,
    prices: drafts.map(draft => ({
      entityNo: draft.entityNo, currency: draft.currency, pricingProfileCode: draft.pricingProfileCode, priorityNo: draft.priorityNo,
      settlementBusinessDays: draft.settlementBusinessDays, businessCalendarCode: blank(draft.businessCalendarCode),
      settlementTimezone: blank(draft.settlementTimezone), cutoffSecondOfDay: draft.cutoffSecondOfDay,
      sourceTemplatePriceId: draft.sourceTemplatePriceId ?? null,
      components: draft.components.map(component => {
        const tiered = component.componentType === 'TIERED';
        const percentage = component.componentType === 'PERCENTAGE';
        return {
          componentCode: component.componentCode.trim(), componentType: component.componentType, triggerCode: component.triggerCode.trim(),
          calculationBase: component.calculationBase, refundBehavior: component.refundBehavior,
          rate: percentage ? blank(component.rate) : null,
          fixedAmount: component.componentType === 'FIXED' ? blank(component.fixedAmount) : null,
          currency: blank(component.currency),
          minAmount: percentage ? blank(component.minAmount) : null, maxAmount: percentage ? blank(component.maxAmount) : null,
          bandBasis: tiered ? blank(component.bandBasis) : null, bandMode: tiered ? blank(component.bandMode) : null,
          bands: tiered ? (component.bands ?? []).map(band => ({ lowerBound: band.lowerBound.trim(), upperBound: blank(band.upperBound),
            rate: blank(band.rate), fixedAmount: blank(band.fixedAmount) })) : null,
        };
      }),
    })),
  };
}

/** 档位前置检查（与 commercial 激活校验 4 一致）：首档下界 0、相邻连续、末档上界为空、每档费率与金额恰好一个。返回问题列表。 */
export function bandIssues(bands: readonly BandDraft[], bandMode: string | null): string[] {
  const issues: string[] = [];
  if (bands.length === 0) return [t('band.issue.atLeastOne')];
  if (Number(bands[0].lowerBound) !== 0) issues.push(t('band.issue.firstLowerZero'));
  bands.forEach((band, index) => {
    const last = index === bands.length - 1;
    const rate = blank(band.rate); const amount = blank(band.fixedAmount);
    const no = index + 1;
    if (last && blank(band.upperBound) !== null) issues.push(t('band.issue.lastUpperEmpty'));
    if (!last) {
      const upper = blank(band.upperBound);
      if (upper === null) issues.push(t('band.issue.upperMissing', { no }));
      else if (Number(upper) !== Number(bands[index + 1].lowerBound)) issues.push(t('band.issue.upperEqualsNext', { no, next: index + 2 }));
      else if (Number(upper) <= Number(band.lowerBound)) issues.push(t('band.issue.upperGreater', { no }));
    }
    if ((rate === null) === (amount === null)) issues.push(t('band.issue.rateOrAmount', { no }));
    if (bandMode === 'PROGRESSIVE' && amount !== null) issues.push(t('band.issue.progressiveRateOnly', { no }));
  });
  return issues;
}

// ---------- 合同表单 ----------

export interface PartyDraft { partyId: string; partyProfileId: string; partyRole: PartyRole }
export interface AgreementFormValues {
  agreementNo: string; agreementName: string; agreementType: AgreementType; businessMode?: string; referralPartnerId?: string; clientId?: string;
  localPartnerId?: string; entityNo?: string; operatingEntityNos?: string[]; effectiveFrom: string; effectiveTo?: string; parties: PartyDraft[];
  signingFileId?: string; signingContentHash?: string;
}

/** 创建合同请求：CLIENT 固定资金模式 SETTLE_THROUGH_PLATFORM 与定价权 PLATFORM（本期唯一取值）；PARTNER 清空 Client 专属字段。 */
export function agreementBody(values: AgreementFormValues) {
  const client = values.agreementType === 'CLIENT';
  const signing = blank(values.signingFileId);
  return {
    agreementNo: values.agreementNo.trim(), agreementName: values.agreementName.trim(), agreementType: values.agreementType,
    businessMode: client ? values.businessMode ?? null : null, fundMode: client ? 'SETTLE_THROUGH_PLATFORM' : null,
    pricingAuthority: client ? 'PLATFORM' : null, referralPartnerId: client ? blank(values.referralPartnerId) : null,
    clientId: client ? blank(values.clientId) : null, localPartnerId: client ? null : blank(values.localPartnerId),
    entityNo: client ? blank(values.entityNo) : null, operatingEntityNos: client ? null : (values.operatingEntityNos ?? []).map(e => e.trim()).filter(Boolean),
    effectiveFrom: offsetInstant(values.effectiveFrom), effectiveTo: optionalInstant(values.effectiveTo),
    parties: values.parties.map(party => ({ partyId: party.partyId.trim(), partyProfileId: party.partyProfileId.trim(), partyRole: party.partyRole })),
    documents: signing ? [{ documentType: 'SIGNING_DOCUMENT', fileId: signing, contentHash: (values.signingContentHash ?? '').trim().toLowerCase() }] : null,
  };
}

export interface AcceptanceFormValues {
  acceptanceReference: string; acceptanceMethod: AcceptanceMethod; acceptedByActorId?: string; acceptedAt: string; fileId?: string; contentHash?: string;
}
/** 签署登记：点击接受不带执行文件、必须带外部 Actor；电子签 / 线下签必须带执行文件。 */
export function acceptanceBody(expectedChangeVersion: number, values: AcceptanceFormValues) {
  const click = values.acceptanceMethod === 'CLICK_ACCEPTANCE';
  return {
    expectedChangeVersion, acceptanceReference: values.acceptanceReference.trim(), acceptanceMethod: values.acceptanceMethod,
    acceptedByActorId: click ? blank(values.acceptedByActorId) : null, acceptedAt: offsetInstant(values.acceptedAt),
    executedDocument: click ? null : { fileId: (values.fileId ?? '').trim(), contentHash: (values.contentHash ?? '').trim().toLowerCase() },
  };
}

/** 保证金两字段必须成组；返回错误文案或 null。 */
export function reserveError(rate: string | undefined | null, days: number | undefined | null): string | null {
  const hasRate = !!blank(rate); const hasDays = days !== undefined && days !== null;
  return hasRate === hasDays ? null : t('reserve.bothOrNeither');
}

export interface ProfileFormValues { pricingProfileCode?: string; profileKind: ProfileKind; profileName: string; paymentMethodCode?: string; conditions?: ProfileCondition[] }
/** 画像写入：PAYMENT_METHOD 才带支付方式；条件值转大写并去重。 */
export function profileBody(values: ProfileFormValues, expectedChangeVersion?: number) {
  const seen = new Set<string>();
  const conditions = (values.conditions ?? []).map(c => ({ dimensionCode: c.dimensionCode, conditionValue: c.conditionValue.trim().toUpperCase() }))
    .filter(c => c.conditionValue && !seen.has(`${c.dimensionCode}|${c.conditionValue}`) && seen.add(`${c.dimensionCode}|${c.conditionValue}`));
  const paymentMethodCode = values.profileKind === 'PAYMENT_METHOD' ? blank(values.paymentMethodCode)?.toUpperCase() ?? null : null;
  if (expectedChangeVersion !== undefined) return { expectedChangeVersion, profileName: values.profileName.trim(), paymentMethodCode, conditions };
  return { pricingProfileCode: (values.pricingProfileCode ?? '').trim().toUpperCase(), profileKind: values.profileKind,
    profileName: values.profileName.trim(), paymentMethodCode, conditions };
}

export interface PlanFormValues { planCode: string; termType: string; agreementId: string; ownerId: string }
/** 方案创建：Client 条款 Owner 写 clientId，Partner 条款写 localPartnerId（二选一）。 */
export function planBody(values: PlanFormValues) {
  const partner = partnerTermTypes.includes(values.termType);
  return { planCode: values.planCode.trim(), termType: values.termType, agreementId: values.agreementId.trim(),
    clientId: partner ? null : values.ownerId.trim(), localPartnerId: partner ? values.ownerId.trim() : null };
}

/** 本地 datetime（YYYY-MM-DDTHH:mm）→ UTC RFC 3339（排期要求 Z 结尾）。 */
export function utcInstant(local: string): string {
  const date = new Date(local);
  return Number.isFinite(date.getTime()) ? date.toISOString().replace(/\.000Z$/, 'Z') : local;
}

/** 本地 datetime → 带本地偏移的 ISO 8601（合同 / 开通项接受任意偏移）。 */
export function offsetInstant(local: string): string {
  const date = new Date(local);
  if (!Number.isFinite(date.getTime())) return local;
  const offset = -date.getTimezoneOffset(); const sign = offset >= 0 ? '+' : '-'; const abs = Math.abs(offset);
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}T${two(date.getHours())}:${two(date.getMinutes())}:${two(date.getSeconds())}${sign}${two(Math.floor(abs / 60))}:${two(abs % 60)}`;
}

/** ISO 时刻 → 日期选择器的本地值 YYYY-MM-DDTHH:mm:ss；空值返回 undefined。 */
export function localInput(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return undefined;
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}T${two(date.getHours())}:${two(date.getMinutes())}:${two(date.getSeconds())}`;
}

/** 可空的本地时间 → 带偏移 ISO；空白为 null。 */
export function optionalInstant(local: string | null | undefined): string | null {
  const value = blank(local);
  return value === null ? null : offsetInstant(value);
}

/** 时间显示：按浏览器本地时区到分钟。 */
export function localTime(value: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`;
}

// ---------- 错误提示 ----------

/** 错误提示场景：合同、画像、条款版本（含标准价目）与产品目录。 */
export type CommercialContext = 'agreement' | 'profile' | 'version' | 'catalog';

/** commercial 原因未透传时 BFF 给出的公共说明（Foundation BUSINESS_CHECK_FAILED 默认消息）。 */
// 服务端返回的原文（Foundation CommonErrorCode 的默认消息），是比对用的数据而非界面文案，故以 \u 转义保存
const genericBusinessDetail = '\u4e1a\u52a1\u6821\u9a8c\u672a\u901a\u8fc7';

/**
 * 商务写命令的错误说明：BFF 把 commercial 的业务拒绝原因作为 Problem detail 透传（F9），页面直接显示该原因，
 * 例如「激活校验：开通项 AE001/APPLE_PAY/AED 无价格」；原因缺失时按场景给出可操作的提示。409 为并发版本冲突。
 * 未识别返回 null，由调用方走通用通知。
 */
export function commercialErrorMessage(error: unknown, context: CommercialContext): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.status === 409) return t('error.conflict');
  if (error.status === 404) return t('error.notFound');
  if (error.status !== 400 || error.code !== 'BUSINESS_CHECK_FAILED') return null;
  const owner = ownerReason(error);
  if (owner) return t('error.businessCheckReason', { reason: owner });
  return t('error.businessCheckHint', { hint: t('error.hint.' + context) });
}

/** commercial 透传的具体原因；只有公共默认说明时返回 null。 */
export function ownerReason(error: ApiError): string | null {
  return error.detail && error.detail !== genericBusinessDetail ? error.detail : null;
}

// ---------- 命令 ----------

export type CommandMethod = 'POST' | 'PUT';
export function commercialCommand(path: string, method: CommandMethod, body: unknown, previous?: ConfigurationCommand): ConfigurationCommand {
  return configurationCommand(path, method, body, previous);
}

export function sendCommercialCommand<T>(command: ConfigurationCommand, decode: (value: unknown) => T): Promise<T> {
  return session.request<T>(command.path, { method: command.method, body: command.body,
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } }, decode);
}

/** prices[0].components[1].rate → ['prices', 0, 'components', 1, 'rate']，供 antd Form 定位嵌套字段。 */
export function fieldPath(field: string): (string | number)[] {
  return field.split('.').flatMap(part => {
    const match = /^([A-Za-z][A-Za-z0-9]*)\[(\d+)]$/.exec(part);
    return match ? [match[1], Number(match[2])] : [part];
  });
}

/** 路由参数中的十进制 ID。 */
export function isId(value: string | undefined): value is string {
  return typeof value === 'string' && idPattern.test(value);
}
