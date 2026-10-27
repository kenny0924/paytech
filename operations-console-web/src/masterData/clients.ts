import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { decodeQualifications, isAvailability } from '../qualification/qualification.ts';
import type { Availability, Qualification } from '../qualification/qualification.ts';

/**
 * Client 列表 / 详情（client-master CM-11 + compliance 资格）的 BFF 路径与页面模型
 * <li>证件号只有掩码；详情资格部分不可用时列表为空，页面不得推断为“未登记”。</li>
 * <li>显示名为 `master-data.client.enum.<type>.<code>`（status、clientType、applicationStatus、relation）；资料值的组 / 字段名沿用 `client-review.application.*`。</li>
 */
export const clientAdminApi = '/client-admin/v1';
export const clientRoutes = { list: '/master-data/clients', detail: (clientId: string) => `/master-data/clients/${clientId}` };
export const clientStatuses = ['PENDING', 'ACTIVE', 'SUSPENDED', 'TERMINATED'] as const;
export const applicationStatuses = ['DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED', 'WITHDRAWN'] as const;

export interface ClientListItem {
  clientId: string; clientNo: string; clientType: string; legalName: string | null; jurisdictionCode: string | null; status: string;
  responsiblePartnerId: string | null; latestApplicationId: string | null; latestApplicationStatus: string | null; createdTime: string;
}
/** 申请资料值：分组 → 字段 → 值；证件只有掩码（identifierMasked）。 */
export type Values = Record<string, Record<string, string | null>>;
export interface ClientPerson { partyId: string; values: Values }
export interface ClientRelation {
  relationId: string; relatedPartyId: string; relationType: string; ownershipPercentage: string | null; uboBasis: string | null; jobTitle: string | null; status: string;
}
export interface ClientApplication {
  applicationId: string; applicationNo: string | null; status: string; submissionRound: number | null; jurisdictionCode: string | null;
  organizationTypeCode: string | null; responsiblePartnerId: string | null; dueDiligenceCaseId: string | null; submittedTime: string | null; updatedTime: string | null;
}
export interface ClientDetail {
  summary: ClientListItem; values: Values; people: ClientPerson[]; relations: ClientRelation[]; applications: ClientApplication[];
}
export interface ClientPage { client: ClientDetail; qualificationsStatus: Availability; qualifications: Qualification[] }

const text = (v: unknown, max = 255): v is string => typeof v === 'string' && v.length <= max;
const nullableText = (v: unknown, max = 255) => v === null || text(v, max);
const code = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const nullableCode = (v: unknown) => v === null || code(v);
const isId = (v: unknown): v is string => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v);
const nullableId = (v: unknown) => v === null || isId(v);
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(v) && Number.isFinite(Date.parse(v));
const nullableInstant = (v: unknown) => v === null || instant(v);
const nullableJurisdiction = (v: unknown) => v === null || (typeof v === 'string' && /^[A-Z][A-Z0-9-]{1,15}$/.test(v));
const list = (v: unknown, max = 1000): v is unknown[] => Array.isArray(v) && v.length <= max;
const opt = <T>(v: T | undefined, fallback: T): T => v === undefined ? fallback : v;
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }

function decodeListItem(value: unknown): ClientListItem {
  const v = value as Record<string, unknown>;
  const item: ClientListItem = { clientId: v?.clientId as string, clientNo: v?.clientNo as string, clientType: v?.clientType as string,
    legalName: opt(v?.legalName as string | null | undefined, null), jurisdictionCode: opt(v?.jurisdictionCode as string | null | undefined, null),
    status: v?.status as string, responsiblePartnerId: opt(v?.responsiblePartnerId as string | null | undefined, null),
    latestApplicationId: opt(v?.latestApplicationId as string | null | undefined, null),
    latestApplicationStatus: opt(v?.latestApplicationStatus as string | null | undefined, null), createdTime: v?.createdAt as string };
  require(v && isId(item.clientId) && text(item.clientNo, 64) && code(item.clientType) && nullableText(item.legalName) && nullableJurisdiction(item.jurisdictionCode)
    && code(item.status) && nullableId(item.responsiblePartnerId) && nullableId(item.latestApplicationId) && nullableCode(item.latestApplicationStatus)
    && instant(item.createdTime));
  return item;
}
export function decodeClientPage(value: unknown): PagingResponseBody<ClientListItem> {
  const v = value as PagingResponseBody<unknown>;
  require(v && list(v.items, 100) && Number.isSafeInteger(v.total) && v.total >= 0 && Number.isInteger(v.page) && v.page >= 1 && Number.isInteger(v.size) && v.size >= 1 && v.size <= 100);
  const items = v.items.map(decodeListItem); require(new Set(items.map(i => i.clientId)).size === items.length);
  return { items, page: v.page, size: v.size, total: v.total };
}
/** 原值证件号键：出现即视为上游违反“只给掩码”。 */
const RAW_IDENTIFIER_KEYS = new Set(['identifierValue', 'normalizedIdentifierValue']);
function decodeValues(value: unknown): Values {
  if (value === null || value === undefined) return {};
  require(typeof value === 'object' && !Array.isArray(value));
  const result: Values = {};
  for (const [group, fields] of Object.entries(value as Record<string, unknown>)) {
    if (fields === null) continue;
    require(typeof fields === 'object' && !Array.isArray(fields));
    result[group] = {};
    for (const [field, raw] of Object.entries(fields as Record<string, unknown>)) {
      require(!RAW_IDENTIFIER_KEYS.has(field) && (raw === null || ['string', 'number', 'boolean'].includes(typeof raw)) && String(raw).length <= 2000);
      result[group][field] = raw === null ? null : String(raw);
    }
  }
  return result;
}
function decodeRelation(value: unknown): ClientRelation {
  const v = value as ClientRelation;
  require(v && isId(v.relationId) && isId(v.relatedPartyId) && code(v.relationType) && nullableText(opt(v.ownershipPercentage, null), 16)
    && nullableCode(opt(v.uboBasis, null)) && nullableText(opt(v.jobTitle, null), 128) && code(v.status));
  return { relationId: v.relationId, relatedPartyId: v.relatedPartyId, relationType: v.relationType, ownershipPercentage: opt(v.ownershipPercentage, null),
    uboBasis: opt(v.uboBasis, null), jobTitle: opt(v.jobTitle, null), status: v.status };
}
function decodeApplication(value: unknown): ClientApplication {
  const v = value as Record<string, unknown>;
  const item: ClientApplication = { applicationId: v?.applicationId as string, applicationNo: opt(v?.applicationNo as string | null | undefined, null),
    status: v?.status as string, submissionRound: opt(v?.submissionRound as number | null | undefined, null),
    jurisdictionCode: opt(v?.jurisdictionCode as string | null | undefined, null), organizationTypeCode: opt(v?.organizationTypeCode as string | null | undefined, null),
    responsiblePartnerId: opt(v?.responsiblePartnerId as string | null | undefined, null), dueDiligenceCaseId: opt(v?.dueDiligenceCaseId as string | null | undefined, null),
    submittedTime: opt(v?.submittedAt as string | null | undefined, null), updatedTime: opt(v?.updatedAt as string | null | undefined, null) };
  require(v && isId(item.applicationId) && nullableText(item.applicationNo, 64) && code(item.status)
    && (item.submissionRound === null || (Number.isSafeInteger(item.submissionRound) && item.submissionRound >= 0)) && nullableJurisdiction(item.jurisdictionCode)
    && nullableCode(item.organizationTypeCode) && nullableId(item.responsiblePartnerId) && nullableId(item.dueDiligenceCaseId)
    && nullableInstant(item.submittedTime) && nullableInstant(item.updatedTime));
  return item;
}
/** Client 详情；必须是请求的 Client。 */
export function decodeClient(value: unknown, clientId: string): ClientDetail {
  const v = value as Record<string, unknown>;
  require(v && list(opt(v.people as unknown[] | undefined, []), 200) && list(opt(v.relations as unknown[] | undefined, []), 400) && list(v.applications, 200));
  const summary = decodeListItem(v.client); require(summary.clientId === clientId);
  const people = opt(v.people as unknown[] | undefined, []).map(raw => { const p = raw as ClientPerson; require(p && isId(p.partyId));
    return { partyId: p.partyId, values: decodeValues(p.values) }; });
  return { summary, values: decodeValues(v.values), people, relations: opt(v.relations as unknown[] | undefined, []).map(decodeRelation),
    applications: (v.applications as unknown[]).map(decodeApplication).sort((a, b) => (b.submittedTime ?? b.updatedTime ?? '').localeCompare(a.submittedTime ?? a.updatedTime ?? '')) };
}
export function decodeClientDetail(value: unknown, clientId: string): ClientPage {
  const v = value as ClientPage; require(v && isAvailability(v.qualificationsStatus));
  const qualifications = decodeQualifications(v.qualifications, { roleType: 'CLIENT', roleId: clientId });
  require(v.qualificationsStatus === 'AVAILABLE' || qualifications.length === 0);
  return { client: decodeClient(v.client, clientId), qualificationsStatus: v.qualificationsStatus, qualifications };
}
export function clientFilters(values: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).filter(([, v]) => v?.trim()).map(([k, v]) => [k, v!.trim()]));
}
