import { ApiError } from '../api/session.ts';
import { translator } from '../i18n/index.ts';

const t = translator('client-review');

/**
 * 入驻码（client-master CM-12）：只能为 OE_PROVIDER 角色的 Local Partner 签发；明文码只在签发响应中出现一次，列表只有掩码
 * <li>显示名为 `client-review.codes.enum.status.<code>`（ACTIVE / REVOKED / EXPIRED）。</li>
 */
export const codesApi = '/onboarding-code-admin/v1';
export const codesRoute = '/client-onboarding/codes';
export const codesButton = 'OPERATIONS_ONBOARDING_CODES_MANAGE';
export const codeStatuses = ['ACTIVE', 'REVOKED', 'EXPIRED'] as const;
/** 明文入驻码格式 XXXX-XXXX（大写字母与数字）。 */
export const plainCodePattern = /^[A-Z0-9]{4}-[A-Z0-9]{4}$/;
export const maxUsesLimit = 100000;
export const noteLimit = 255;

export interface PartnerOption { localPartnerId: string; partnerNo: string; legalName: string | null; status: string }
export interface OnboardingCode {
  codeId: string; localPartnerId: string; codeMasked: string; status: string; effectiveFrom: string; effectiveTo: string | null;
  maxUses: number | null; useCount: number; note: string | null; issuedBy: string | null; issuedTime: string; revokedBy: string | null; revokedTime: string | null;
}
export interface IssuedCode extends OnboardingCode { code: string }

const text = (v: unknown, max = 255): v is string => typeof v === 'string' && v.length <= max;
const nullableText = (v: unknown, max = 255) => v === null || text(v, max);
const code = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const isId = (v: unknown): v is string => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v);
const nullableId = (v: unknown) => v === null || isId(v);
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/.test(v) && Number.isFinite(Date.parse(v));
const nullableInstant = (v: unknown) => v === null || instant(v);
const count = (v: unknown): v is number => Number.isSafeInteger(v) && (v as number) >= 0;
const list = (v: unknown, max = 1000): v is unknown[] => Array.isArray(v) && v.length <= max;
const opt = <T>(v: T | undefined, fallback: T): T => v === undefined ? fallback : v;
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }

export function decodePartnerOptions(value: unknown): PartnerOption[] {
  const v = value as { items: PartnerOption[] }; require(v && list(v.items, 500));
  const items = v.items.map(p => { require(p && isId(p.localPartnerId) && text(p.partnerNo, 64) && nullableText(p.legalName) && code(opt(p.status, 'ACTIVE')));
    return { localPartnerId: p.localPartnerId, partnerNo: p.partnerNo, legalName: p.legalName, status: opt(p.status, 'ACTIVE') }; });
  require(new Set(items.map(p => p.localPartnerId)).size === items.length);
  return items;
}
/** 一条入驻码；必须属于请求的 Partner，掩码不得是明文格式。 */
export function decodeCode(value: unknown, localPartnerId: string): OnboardingCode {
  const v = value as OnboardingCode;
  require(v && isId(v.codeId) && v.localPartnerId === localPartnerId && text(v.codeMasked, 16) && !plainCodePattern.test(v.codeMasked) && code(v.status)
    && instant(v.effectiveFrom) && nullableInstant(opt(v.effectiveTo, null)) && (opt(v.maxUses, null) === null || count(v.maxUses)) && count(v.useCount)
    && nullableText(opt(v.note, null), 500) && nullableId(opt(v.issuedBy, null)) && instant(v.issuedTime) && nullableId(opt(v.revokedBy, null))
    && nullableInstant(opt(v.revokedTime, null)));
  return { codeId: v.codeId, localPartnerId: v.localPartnerId, codeMasked: v.codeMasked, status: v.status, effectiveFrom: v.effectiveFrom,
    effectiveTo: opt(v.effectiveTo, null), maxUses: opt(v.maxUses, null), useCount: v.useCount, note: opt(v.note, null), issuedBy: opt(v.issuedBy, null),
    issuedTime: v.issuedTime, revokedBy: opt(v.revokedBy, null), revokedTime: opt(v.revokedTime, null) };
}
export function decodeCodes(value: unknown, localPartnerId: string): OnboardingCode[] {
  const v = value as { items: unknown[] }; require(v && list(v.items));
  const items = v.items.map(item => decodeCode(item, localPartnerId)); require(new Set(items.map(c => c.codeId)).size === items.length);
  return items.sort((a, b) => b.issuedTime.localeCompare(a.issuedTime));
}
/** 签发结果 {issued, code}：唯一一次带明文码。 */
export function decodeIssuedCode(value: unknown, localPartnerId: string): IssuedCode {
  const v = value as { issued: unknown; code: unknown };
  require(v && typeof v.code === 'string' && plainCodePattern.test(v.code));
  return { ...decodeCode(v.issued, localPartnerId), code: v.code };
}
/** 签发请求：有效期截止可空（不过期），次数上限可空（不限）。 */
export function issuePayload(values: { effectiveTo?: string | null; maxUses?: number | null; note?: string }) {
  return { effectiveFrom: null, effectiveTo: values.effectiveTo || null, maxUses: values.maxUses ?? null, note: values.note?.trim() || null };
}
/** 签发表单校验：截止时间必须晚于现在，次数 1—100000（client-master 上限）。 */
export function issueIssues(values: { effectiveTo?: string | null; maxUses?: number | null }, now = Date.now()): string[] {
  const issues: string[] = [];
  if (values.effectiveTo && Date.parse(values.effectiveTo) <= now) issues.push(t('codes.issue.pastExpiry'));
  if (values.maxUses !== undefined && values.maxUses !== null && (!Number.isInteger(values.maxUses) || values.maxUses < 1 || values.maxUses > maxUsesLimit)) issues.push(t('codes.issue.maxUses'));
  return issues;
}
/**
 * 页面显示的状态：client-master 保存的 ACTIVE 在截止时间已过或次数用尽时按 EXPIRED 显示（兑换端同样拒绝），其他原样
 * <li>只影响显示与过滤，不改服务端状态。</li>
 */
export function effectiveStatus(c: Pick<OnboardingCode, 'status' | 'effectiveTo' | 'maxUses' | 'useCount'>, now = Date.now()): string {
  if (c.status !== 'ACTIVE') return c.status;
  if (c.effectiveTo !== null && Date.parse(c.effectiveTo) < now) return 'EXPIRED';
  if (c.maxUses !== null && c.useCount >= c.maxUses) return 'EXPIRED';
  return c.status;
}
export const usageText = (c: Pick<OnboardingCode, 'useCount' | 'maxUses'>) => c.maxUses === null ? t('codes.usageUnlimited', { used: c.useCount }) : t('codes.usage', { used: c.useCount, max: c.maxUses });
