import { ApiError } from '../api/session.ts';

// ---------- 开通与定价一期：版本读模型的新增字段（破标、来源、签署、相关性提示、接受条件） ----------
// 只放类型与解码，不依赖 commercial.ts，供 commercial.ts 的 decodePrice / decodeVersion / decodeCapability 引用。

/** 开通项接受条件：同维度多值 OR，异维度 AND。 */
export interface CapabilityCondition { dimensionCode: string; values: string[] }

export const benchmarkStatuses = ['BELOW_STANDARD', 'AT_OR_ABOVE', 'NO_BENCHMARK'] as const;
export type BenchmarkStatus = typeof benchmarkStatuses[number];
/** 组件与来源模板同组件的比较结果；standard* 为模板值（无标准可比时为空）。 */
export interface ComponentBenchmark {
  status: BenchmarkStatus; standardRate: string | null; standardFixedAmount: string | null; standardMinAmount: string | null; standardMaxAmount: string | null;
}

export const relevanceReasonCodes = ['PAYMENT_METHOD_NOT_ENABLED', 'CURRENCY_NOT_ENABLED', 'CONDITION_OUT_OF_SCOPE', 'FEATURE_NOT_ENABLED',
  'NO_CAPABILITY_ON_ENTITY'] as const;
/** 价格挂不上已开通开通项的提示（保存提示、激活拦截）。reasonCode 未知时原样保留。 */
export interface RelevanceWarning { entityNo: string; currency: string; pricingProfileCode: string; reasonCode: string; message: string | null }

/** 来源模板方案已有更新版本生效。 */
export interface TemplateUpdate { latestTemplateVersionId: string; latestVersionNo: string }

/** 价格协议签署记录（一个版本一次）。 */
export interface VersionAcceptance {
  versionAcceptanceId: string | null; partyId: string; acceptanceMethod: string; acceptedAt: string; acceptanceReference: string; fileId: string;
  contentHash: string; recordedBy: string | null;
}

/** 版本读模型的新增字段；旧回执（写命令回执）缺省时取空值。 */
export interface VersionReview {
  belowStandardCount: number | null; sourceTemplateVersionId: string | null; templateUpdate: TemplateUpdate | null; signatureExempt: boolean;
  acceptance: VersionAcceptance | null; relevanceWarnings: RelevanceWarning[];
}

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const array = (v: unknown): v is unknown[] => Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v);
const text = (v: unknown, max = 256) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const decimal = (v: unknown) => typeof v === 'string' && /^(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(v);
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 接受条件：维度代码 + 至少一个取值。 */
export function decodeCapabilityCondition(value: unknown): CapabilityCondition {
  require(object(value) && text(value.dimensionCode, 64) && array(value.values) && value.values.length > 0 && value.values.every(item => text(item, 64)));
  const v = value as unknown as CapabilityCondition;
  return { dimensionCode: v.dimensionCode, values: [...v.values] };
}

/** 组件破标比较；缺省为 null（模板版本、旧回执）。 */
export function decodeBenchmark(value: unknown): ComponentBenchmark | null {
  if (value === null || value === undefined) return null;
  const v = value as ComponentBenchmark;
  require(object(v) && (benchmarkStatuses as readonly string[]).includes(v.status) && optional(decimal)(v.standardRate)
    && optional(decimal)(v.standardFixedAmount) && optional(decimal)(v.standardMinAmount) && optional(decimal)(v.standardMaxAmount));
  return { status: v.status, standardRate: orNull(v.standardRate), standardFixedAmount: orNull(v.standardFixedAmount),
    standardMinAmount: orNull(v.standardMinAmount), standardMaxAmount: orNull(v.standardMaxAmount) };
}

export function decodeRelevanceWarning(value: unknown): RelevanceWarning {
  const v = value as RelevanceWarning;
  require(object(v) && text(v.entityNo, 5) && text(v.currency, 3) && text(v.pricingProfileCode, 64) && text(v.reasonCode, 64)
    && optional(text)(v.message));
  return { entityNo: v.entityNo, currency: v.currency, pricingProfileCode: v.pricingProfileCode, reasonCode: v.reasonCode, message: orNull(v.message) };
}

function decodeTemplateUpdate(value: unknown): TemplateUpdate | null {
  if (value === null || value === undefined) return null;
  const v = value as Record<string, unknown>;
  require(object(v) && id(v.latestTemplateVersionId) && (text(v.latestVersionNo, 19) || Number.isSafeInteger(v.latestVersionNo)));
  return { latestTemplateVersionId: v.latestTemplateVersionId as string, latestVersionNo: String(v.latestVersionNo) };
}

function decodeAcceptance(value: unknown): VersionAcceptance | null {
  if (value === null || value === undefined) return null;
  const v = value as VersionAcceptance;
  require(object(v) && optional(id)(v.versionAcceptanceId) && id(v.partyId) && text(v.acceptanceMethod, 32) && instant(v.acceptedAt)
    && text(v.acceptanceReference, 128) && id(v.fileId) && typeof v.contentHash === 'string' && /^[0-9a-f]{64}$/.test(v.contentHash)
    && optional(text)(v.recordedBy));
  return { versionAcceptanceId: orNull(v.versionAcceptanceId), partyId: v.partyId, acceptanceMethod: v.acceptanceMethod, acceptedAt: v.acceptedAt,
    acceptanceReference: v.acceptanceReference, fileId: v.fileId, contentHash: v.contentHash, recordedBy: orNull(v.recordedBy) };
}

/** 版本读模型新增字段：全部可缺省（写命令回执、旧 BFF），存在时严格校验。 */
export function decodeVersionReview(value: Record<string, unknown>): VersionReview {
  const count = value.belowStandardCount;
  require((count === undefined || count === null || (Number.isSafeInteger(count) && Number(count) >= 0))
    && optional(id)(value.sourceTemplateVersionId) && optional((v: unknown) => typeof v === 'boolean')(value.signatureExempt)
    && optional(array)(value.relevanceWarnings));
  return {
    belowStandardCount: orNull(count as number | null | undefined), sourceTemplateVersionId: orNull(value.sourceTemplateVersionId as string | undefined),
    templateUpdate: decodeTemplateUpdate(value.templateUpdate), signatureExempt: value.signatureExempt === true,
    acceptance: decodeAcceptance(value.acceptance), relevanceWarnings: ((value.relevanceWarnings ?? []) as unknown[]).map(decodeRelevanceWarning),
  };
}
