import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { translator } from '../i18n/index.ts';

const t = translator('client-review');

/** Client 入驻审核的 BFF 路径与页面模型；Owner 事实原样展示，通过资格只看 Risk 门禁。 */
export const reviewApi = '/client-onboarding-admin/v1';
export const reviewRoute = '/client-onboarding/reviews';

export interface Evidence { evidenceId: string; fileId: string; status: string }
export interface Requirement { requirementId: string; requirementCode: string; documentType: string; required: boolean; status: string; evidence: Evidence[] }
export interface Subject { caseId: string; caseStatus: string; partyId: string; profileId: string; roleType: string; qualificationType: string; dependencyTypes: string[]; requirements: Requirement[] }
export interface Classification { industryCode: string; mccCode: string; primary: boolean }
export interface ReviewCase {
  caseId: string; caseNo: string; applicationId: string; clientId: string; sourceSubmissionId: string; qualificationType: string;
  status: string; version: string; submittedAt: string; decidedAt: string | null; publicNote: string | null; returnSection: string | null;
  subjects: Subject[]; classifications: Classification[]; approvalBlockers: string[];
}
export interface Person { partyId: string; values: Record<string, Record<string, string | null>> }
export interface Relation { relationId: string; relatedPartyId: string; relationType: string; ownershipPercentage: string | null; status: string }
export interface DocumentRef { documentId: string; subjectPartyId: string; requirementCode: string; documentType: string; fileId: string; subjectInScope: boolean }
export interface Application {
  applicationId: string; applicationNo: string; clientId: string; clientType: string; subjectPartyId: string; status: string;
  values: Record<string, Record<string, string | null>>; people: Person[]; relations: Relation[]; documents: DocumentRef[];
}
export interface ClassificationSet { caseId: string; applicationId: string; confirmed: boolean; confirmedBy: string | null; confirmedAt: string | null; items: Classification[] }
export interface Option { code: string; label: string }
export type Availability = 'AVAILABLE' | 'UNAVAILABLE';
export interface ReviewDetail {
  review: ReviewCase; applicationStatus: Availability; application: Application | null; classificationStatus: Availability;
  classification: ClassificationSet | null; industriesStatus: Availability; industries: Option[];
}
export interface Command { caseId: string; status: string }

/**
 * Display names are `client-review.enum.<type>.<code>` (caseStatus, qualification, role, evidence, blocker, section,
 * relation) and `client-review.application.group.<group>` / `.field.<field>`, read with enumLabel; unknown codes show as is.
 */
export const caseStatuses = ['SUBMITTED', 'IN_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED', 'WITHDRAWN'] as const;
export const activeStatuses = ['SUBMITTED', 'IN_REVIEW'];
/** Client types offered in the list filter (related-person KYC is never a top-level case). */
export const clientQualificationTypes = ['INDIVIDUAL_KYC', 'ORGANIZATION_KYB'] as const;
export const returnSections = ['BASIC', 'BUSINESS', 'PEOPLE', 'MATERIALS'] as const;

export const isId = (v: unknown): v is string => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v) && BigInt(v) <= 9223372036854775807n;
const text = (v: unknown, max = 255): v is string => typeof v === 'string' && v.length <= max;
const code = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const nullable = (v: unknown, max = 1000) => v === null || text(v, max);
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/.test(v) && Number.isFinite(Date.parse(v));
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
const list = (v: unknown, max = 1000): v is unknown[] => Array.isArray(v) && v.length <= max;

function decodeEvidence(value: unknown): Evidence {
  const v = value as Evidence; require(v && isId(v.evidenceId) && isId(v.fileId) && code(v.status));
  return { evidenceId: v.evidenceId, fileId: v.fileId, status: v.status };
}
function decodeRequirement(value: unknown): Requirement {
  const v = value as Requirement;
  require(v && isId(v.requirementId) && code(v.requirementCode) && code(v.documentType) && typeof v.required === 'boolean' && code(v.status) && list(v.evidence));
  return { requirementId: v.requirementId, requirementCode: v.requirementCode, documentType: v.documentType, required: v.required, status: v.status, evidence: v.evidence.map(decodeEvidence) };
}
function decodeSubject(value: unknown): Subject {
  const v = value as Subject;
  require(v && isId(v.caseId) && code(v.caseStatus) && isId(v.partyId) && isId(v.profileId) && code(v.roleType) && code(v.qualificationType)
    && list(v.dependencyTypes, 16) && v.dependencyTypes.every(code) && list(v.requirements));
  return { caseId: v.caseId, caseStatus: v.caseStatus, partyId: v.partyId, profileId: v.profileId, roleType: v.roleType,
    qualificationType: v.qualificationType, dependencyTypes: [...v.dependencyTypes], requirements: v.requirements.map(decodeRequirement) };
}
function decodeClassification(value: unknown): Classification {
  const v = value as Classification; require(v && code(v.industryCode) && typeof v.mccCode === 'string' && /^[0-9]{4}$/.test(v.mccCode) && typeof v.primary === 'boolean');
  return { industryCode: v.industryCode, mccCode: v.mccCode, primary: v.primary };
}
/** Risk 任务摘要或详情；expectedId 时必须是请求的任务。 */
export function decodeCase(value: unknown, expectedId?: string): ReviewCase {
  const v = value as ReviewCase;
  require(v && isId(v.caseId) && (!expectedId || v.caseId === expectedId) && text(v.caseNo, 64) && isId(v.applicationId) && isId(v.clientId)
    && text(v.sourceSubmissionId, 64) && code(v.qualificationType) && code(v.status) && /^[1-9][0-9]{0,9}$/.test(v.version)
    && instant(v.submittedAt) && (v.decidedAt === null || instant(v.decidedAt)) && nullable(v.publicNote)
    && (v.returnSection === null || (returnSections as readonly string[]).includes(v.returnSection))
    && list(v.subjects ?? [], 64) && list(v.classifications ?? [], 20) && list(v.approvalBlockers ?? [], 16));
  return { caseId: v.caseId, caseNo: v.caseNo, applicationId: v.applicationId, clientId: v.clientId, sourceSubmissionId: v.sourceSubmissionId,
    qualificationType: v.qualificationType, status: v.status, version: v.version, submittedAt: v.submittedAt, decidedAt: v.decidedAt,
    publicNote: v.publicNote, returnSection: v.returnSection, subjects: (v.subjects ?? []).map(decodeSubject),
    classifications: (v.classifications ?? []).map(decodeClassification), approvalBlockers: (v.approvalBlockers ?? []).map(b => { require(code(b)); return b; }) };
}
export function decodeCasePage(value: unknown): PagingResponseBody<ReviewCase> {
  const v = value as PagingResponseBody<unknown>;
  require(v && list(v.items, 100) && Number.isSafeInteger(v.total) && v.total >= 0 && Number.isInteger(v.page) && v.page >= 1 && Number.isInteger(v.size) && v.size >= 1 && v.size <= 100);
  const items = v.items.map(item => decodeCase(item)); require(new Set(items.map(i => i.caseId)).size === items.length);
  return { items, page: v.page, size: v.size, total: v.total };
}
function decodeValues(value: unknown): Record<string, Record<string, string | null>> {
  require(value && typeof value === 'object' && !Array.isArray(value));
  const result: Record<string, Record<string, string | null>> = {};
  for (const [group, fields] of Object.entries(value as Record<string, unknown>)) {
    require(fields && typeof fields === 'object' && !Array.isArray(fields));
    result[group] = {};
    for (const [field, raw] of Object.entries(fields as Record<string, unknown>)) { require(nullable(raw, 2000)); result[group][field] = raw as string | null; }
  }
  return result;
}
export function decodeApplication(value: unknown, applicationId: string): Application {
  const v = value as Application;
  require(v && v.applicationId === applicationId && text(v.applicationNo, 64) && isId(v.clientId) && code(v.clientType) && isId(v.subjectPartyId)
    && code(v.status) && list(v.people, 64) && list(v.relations, 128) && list(v.documents, 256));
  return { applicationId: v.applicationId, applicationNo: v.applicationNo, clientId: v.clientId, clientType: v.clientType, subjectPartyId: v.subjectPartyId,
    status: v.status, values: decodeValues(v.values),
    people: v.people.map(p => { require(p && isId(p.partyId)); return { partyId: p.partyId, values: decodeValues(p.values) }; }),
    relations: v.relations.map(r => { require(r && isId(r.relationId) && isId(r.relatedPartyId) && code(r.relationType) && nullable(r.ownershipPercentage, 16) && code(r.status));
      return { relationId: r.relationId, relatedPartyId: r.relatedPartyId, relationType: r.relationType, ownershipPercentage: r.ownershipPercentage, status: r.status }; }),
    documents: v.documents.map(d => { require(d && isId(d.documentId) && isId(d.subjectPartyId) && code(d.requirementCode) && code(d.documentType) && isId(d.fileId) && typeof d.subjectInScope === 'boolean');
      return { documentId: d.documentId, subjectPartyId: d.subjectPartyId, requirementCode: d.requirementCode, documentType: d.documentType, fileId: d.fileId, subjectInScope: d.subjectInScope }; }) };
}
export function decodeClassificationSet(value: unknown, caseId: string): ClassificationSet {
  const v = value as ClassificationSet;
  require(v && v.caseId === caseId && isId(v.applicationId) && typeof v.confirmed === 'boolean' && list(v.items, 20)
    && (v.confirmed ? isId(v.confirmedBy) && instant(v.confirmedAt) : v.confirmedBy === null && v.confirmedAt === null));
  return { caseId: v.caseId, applicationId: v.applicationId, confirmed: v.confirmed, confirmedBy: v.confirmedBy, confirmedAt: v.confirmedAt, items: v.items.map(decodeClassification) };
}
const availability = (v: unknown): v is Availability => v === 'AVAILABLE' || v === 'UNAVAILABLE';
/** 详情：CCT 部分标为不可用时对应字段必须为空，不能当作“无数据”。 */
export function decodeDetail(value: unknown, caseId: string): ReviewDetail {
  const v = value as ReviewDetail;
  require(v && availability(v.applicationStatus) && availability(v.classificationStatus) && availability(v.industriesStatus) && list(v.industries, 200)
    && (v.applicationStatus === 'AVAILABLE') === (v.application !== null) && (v.classificationStatus === 'AVAILABLE') === (v.classification !== null));
  const review = decodeCase(v.review, caseId);
  return { review, applicationStatus: v.applicationStatus, application: v.application === null ? null : decodeApplication(v.application, review.applicationId),
    classificationStatus: v.classificationStatus, classification: v.classification === null ? null : decodeClassificationSet(v.classification, caseId),
    industriesStatus: v.industriesStatus, industries: v.industries.map(o => { require(o && code(o.code) && text(o.label, 128)); return { code: o.code, label: o.label }; }) };
}
export function decodeCommand(value: unknown, caseId: string): Command {
  const v = value as Command; require(v && v.caseId === caseId && code(v.status));
  return { caseId: v.caseId, status: v.status };
}
export interface FileDownload { fileId: string; displayName: string; mimeType: string; url: string; expiresAt: string }
/** 下载授权必须对应所点文件且为 HTTPS 地址；地址只用于本次跳转，不保存。 */
export function decodeFileDownload(value: unknown, fileId: string): FileDownload {
  const v = value as FileDownload;
  require(v && v.fileId === fileId && text(v.displayName) && text(v.mimeType, 128) && instant(v.expiresAt)
    && typeof v.url === 'string' && v.url.length <= 8192 && v.url.startsWith('https://'));
  return { fileId: v.fileId, displayName: v.displayName, mimeType: v.mimeType, url: v.url, expiresAt: v.expiresAt };
}
/** 通过按钮只看 Risk 门禁：任务在进行且没有未满足项。 */
export function canApprove(review: ReviewCase): boolean {
  return activeStatuses.includes(review.status) && review.approvalBlockers.length === 0;
}
/** 分类草稿的前端校验；最终以 CCT 为准。 */
export function classificationIssues(items: Classification[]): string[] {
  const issues: string[] = [];
  if (items.length === 0) issues.push(t('classification.issue.empty'));
  if (items.length > 20) issues.push(t('classification.issue.tooMany'));
  if (items.filter(i => i.primary).length !== 1) issues.push(t('classification.issue.onePrimary'));
  if (items.some(i => !/^[0-9]{4}$/.test(i.mccCode))) issues.push(t('classification.issue.mccFormat'));
  if (items.some(i => !i.industryCode)) issues.push(t('classification.issue.industryRequired'));
  if (new Set(items.map(i => `${i.industryCode}:${i.mccCode}`)).size !== items.length) issues.push(t('classification.issue.duplicate'));
  return issues;
}
export function filters(values: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).filter(([, v]) => v?.trim()).map(([k, v]) => [k, v!.trim()]));
}
