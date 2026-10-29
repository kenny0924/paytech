import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { translator } from '../i18n/index.ts';
import type { Translate } from '../i18n/index.ts';
import { decodeIndustryCategory, decodeMcc } from '../referenceData/referenceData.ts';
import type { IndustryCategory, RefIndustry, RefMcc } from '../referenceData/referenceData.ts';

const t = translator('client-review');

/**
 * Client 入驻审核工作台的 BFF 路径与页面模型；compliance 案件事实原样展示，批准资格只看 compliance 门禁（CP-6）。
 * <li>CP-5 新增字段（检查、筛查、CRR、推荐、时间线等）缺省按空处理，旧案件仍可展示。</li>
 */
export const reviewApi = '/client-onboarding-admin/v1';
export const reviewRoute = '/client-onboarding/reviews';
export const reviewButtons = {
  classify: 'CLIENT_ONBOARDING_CLASSIFY',
  evidence: 'CLIENT_ONBOARDING_REVIEW_EVIDENCE',
  screening: 'CLIENT_ONBOARDING_SCREENING',
  /** CRR 与行业 / MCC 同属分类确认权限（块 138 决定）。 */
  riskRate: 'CLIENT_ONBOARDING_CLASSIFY',
  recommend: 'CLIENT_ONBOARDING_RECOMMEND',
  waive: 'CLIENT_ONBOARDING_WAIVE',
  return: 'CLIENT_ONBOARDING_RETURN',
  decide: 'CLIENT_ONBOARDING_DECIDE',
} as const;

/** 证据；fileId 由文件服务地址 /files/v1/{fileId} 解析，用于 O10 下载。 */
export interface Evidence {
  evidenceId: string; fileAddress: string; fileId: string; status: string; documentType: string | null; issueDate: string | null; expiryDate: string | null;
  rejectReasonCode: string | null; reviewerId: string | null; reviewedTime: string | null; reusedFromCaseId: string | null;
}
export interface Requirement {
  requirementId: string; requirementCode: string; documentTypes: string[]; required: boolean; status: string; evidence: Evidence[];
  waivable: boolean; maxAgeDays: number | null; waivedBy: string | null; waivedReasonCode: string | null; reusedFromCaseId: string | null;
}
export interface Subject {
  caseId: string; caseStatus: string; partyId: string; profileId: string; roleType: string; qualificationType: string; dependencyTypes: string[];
  requirements: Requirement[]; uboBasis: string | null; jobTitle: string | null;
}
export interface Classification { industryCode: string; mccCode: string; primary: boolean }
/** 检查；compliance 按主体返回，页面展平并带上主体。 */
export interface Check {
  checkId: string; caseId: string; subjectPartyId: string; checkCode: string; checkType: string | null; checkMode: string; blocking: boolean; required: boolean;
  status: string; outcome: string | null; reasonCode: string | null; note: string | null; reviewerId: string | null; reviewedTime: string | null;
  validUntil: string | null; reusedFromCaseId: string | null;
}
export interface HitResolution { resolution: string; reasonCode: string | null; note: string | null; resolvedBy: string | null; resolvedTime: string; onBehalfOfPartnerId: string | null }
export interface ScreeningHit {
  hitId: string; externalHitRef: string | null; entityType: string | null; matchType: string | null; matchScore: string | null; riskLevel: string | null;
  listTypes: string[]; status: string; resolutions: HitResolution[];
}
export interface ScreeningRun {
  runId: string; caseId: string; subjectPartyId: string; screeningType: string; providerCode: string | null; status: string; triggerSource: string | null;
  startedTime: string | null; completedTime: string | null; hits: ScreeningHit[];
}
export interface RiskFactor { factorCode: string; factorValue: string; source: string | null; recordedBy: string | null; recordedTime: string | null }
export interface TimelineEntry {
  changeId: string; caseId: string | null; actionType: string; fromStatus: string | null; toStatus: string | null; actorId: string | null; operatedBy: string | null;
  onBehalfOfPartnerId: string | null; reasonCode: string | null; occurredAt: string;
}
export interface ReturnError { requirementKey: string; subjectPartyId: string | null; code: string; reason: string | null; remediation: string; dueTime: string | null }
export interface ReviewCase {
  caseId: string; caseNo: string; applicationId: string; clientId: string; sourceSubmissionId: string; qualificationType: string;
  status: string; version: string; submittedAt: string; decidedAt: string | null; publicNote: string | null; returnSection: string | null;
  subjects: Subject[]; classifications: Classification[]; approvalBlockers: string[];
  jurisdictionCode: string | null; tier: string | null; riskRating: string | null; responsiblePartnerId: string | null;
  operatedBy: string | null; onBehalfOfPartnerId: string | null; roundNo: number | null; previousCaseId: string | null;
  reviewDueTime: string | null; fourEyesRequired: boolean; recommendation: string | null; recommendedBy: string | null; recommendedTime: string | null;
  recommendationNote: string | null; caseType: string | null; programVersionId: string | null; riskRatingNote: string | null; riskRatedBy: string | null;
  riskRatedAt: string | null; checks: Check[]; screenings: ScreeningRun[]; riskFactors: RiskFactor[]; timeline: TimelineEntry[]; returnErrors: ReturnError[];
}
export interface Person { partyId: string; values: Record<string, Record<string, string | null>> }
export interface Relation { relationId: string; relatedPartyId: string; relationType: string; ownershipPercentage: string | null; status: string }
export interface DocumentRef { documentId: string; subjectPartyId: string; requirementCode: string; documentType: string; fileAddress: string; fileId: string; subjectInScope: boolean }
/**
 * client-master 声明（每类最新一条）
 * <li>draftRevision 为声明针对的申请资料版本；current 表示与当前资料版本及条款版本一致，false 即声明后资料又被修改。</li>
 * <li>draftRevision、current、declaredByAccountId 为 2026-10-27 新增字段，旧响应缺省按 null 显示为未知。</li>
 */
export interface Declaration {
  declarationId: string; declarationType: string; declaredValue: 'TRUE' | 'FALSE'; termsVersion: string | null; declaredAt: string;
  declaredByAccountId: string | null; draftRevision: string | null; current: boolean | null;
}
export interface Application {
  applicationId: string; applicationNo: string; clientId: string; clientType: string; subjectPartyId: string; status: string;
  values: Record<string, Record<string, string | null>>; people: Person[]; relations: Relation[]; documents: DocumentRef[]; declarations: Declaration[];
}
export interface ClassificationSet { caseId: string; applicationId: string; confirmed: boolean; confirmedBy: string | null; confirmedAt: string | null; items: Classification[] }
export type Availability = 'AVAILABLE' | 'UNAVAILABLE';
/**
 * 审核详情：industries 为 Platform ACTIVE 行业树（大类 → 细类，细类带建议 MCC）；mccs 为当前分类与建议 MCC 涉及的编码（任意状态）。
 * 名称显示见 referenceData.ts（refdata 文案 → 后端英文 name → 编码）。
 */
export interface ReviewDetail {
  review: ReviewCase; applicationStatus: Availability; application: Application | null; classificationStatus: Availability;
  classification: ClassificationSet | null; industriesStatus: Availability; industries: IndustryCategory[]; mccsStatus: Availability; mccs: RefMcc[];
}
export interface Command { caseId: string; status: string }
/** 可被设为责任方 / 代操作对象的 PayFac（OE_PROVIDER 角色的 ACTIVE Local Partner）。 */
export interface ResponsiblePartner { localPartnerId: string; partnerNo: string; legalName: string | null }

/**
 * Display names are `client-review.enum.<type>.<code>` (caseStatus, qualification, role, evidence, blocker, section, relation,
 * rejectReason, checkStatus, checkOutcome, screeningStatus, hitStatus, hitResolution, riskRating, riskFactor, tier,
 * recommendation, errorCode, remediation, action, operatedBy, subjectRole) read with enumLabel; unknown codes show as is.
 */
/** 本切片 compliance 实际写入的案件状态（WAITING_EVIDENCE / PENDING_DEPENDENCY 已定义未启用，只保留显示名）。 */
export const caseStatuses = ['SUBMITTED', 'IN_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED'] as const;
export const activeStatuses = ['SUBMITTED', 'IN_REVIEW', 'WAITING_EVIDENCE', 'PENDING_DEPENDENCY'];
/** 列表过滤“平台自营”：compliance 以 responsiblePartnerId=0 表示无责任 PayFac。 */
export const platformOwnedFilter = '0';
/** Client types offered in the list filter (related-person KYC is never a top-level case). */
export const clientQualificationTypes = ['INDIVIDUAL_KYC', 'ORGANIZATION_KYB'] as const;
/** Legacy whole-section return, still shown on cases returned before requirement-level errors. */
export const returnSections = ['BASIC', 'BUSINESS', 'PEOPLE', 'MATERIALS'] as const;
export const evidenceRejectReasons = ['DOCUMENT_EXPIRED', 'DOCUMENT_TOO_OLD', 'DOCUMENT_UNREADABLE', 'DOCUMENT_MISMATCH_NAME',
  'DOCUMENT_MISMATCH_ADDRESS', 'DOCUMENT_TYPE_INVALID', 'DOCUMENT_FRAUDULENT', 'OTHER'] as const;
export const checkOutcomes = ['PASS', 'FAIL'] as const;
/** POTENTIAL_MATCH / INSUFFICIENT_EVIDENCE keep the hit open (SM-I). */
export const hitResolutions = ['NO_MATCH', 'FALSE_POSITIVE', 'POTENTIAL_MATCH', 'TRUE_POSITIVE_ACCEPT', 'TRUE_POSITIVE_REJECT', 'INSUFFICIENT_EVIDENCE'] as const;
export const closingResolutions: readonly string[] = ['NO_MATCH', 'FALSE_POSITIVE', 'TRUE_POSITIVE_ACCEPT', 'TRUE_POSITIVE_REJECT'];
export const riskRatings = ['LOW', 'MEDIUM', 'HIGH'] as const;
export const riskFactorCodes = ['COUNTRY_RISK', 'NATIONALITY_RISK', 'PEP', 'INDUSTRY_RISK', 'VOLUME', 'ENTITY_STRUCTURE', 'ADVERSE_MEDIA',
  'PRIOR_REJECTION', 'MANUAL'] as const;
export const recommendations = ['APPROVE', 'REJECT'] as const;
/** Requirement-level return error codes (design 8.3) the reviewer can pick; the Client sees the translated code. */
export const returnErrorCodes = ['FIELD_MISSING', 'FIELD_FORMAT_INVALID', 'IDENTIFIER_FORMAT_INVALID', 'IDENTIFIER_EXPIRED', 'UBO_MISSING',
  'SIGNATORY_MISSING', 'DIRECTOR_MISSING', 'RELATED_PERSON_INCOMPLETE', 'DOCUMENT_MISSING', 'DOCUMENT_EXPIRED', 'DOCUMENT_TOO_OLD',
  'DOCUMENT_UNREADABLE', 'DOCUMENT_MISMATCH_NAME', 'DOCUMENT_MISMATCH_ADDRESS', 'DOCUMENT_TYPE_INVALID', 'DECLARATION_MISSING',
  'VERIFICATION_FAILED_IDENTITY', 'VERIFICATION_FAILED_REGISTRY', 'VERIFICATION_FAILED_TAX_ID'] as const;
export const remediationCodes = ['PROVIDE_FIELD', 'UPLOAD_DOCUMENT', 'ADD_PERSON', 'ADD_RELATION', 'SIGN_DECLARATION', 'CONTACT_SUPPORT', 'NONE'] as const;
export const returnDueDays = { min: 1, max: 90, initial: 14 };

export const isId = (v: unknown): v is string => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v) && BigInt(v) <= 9223372036854775807n;
const text = (v: unknown, max = 255): v is string => typeof v === 'string' && v.length <= max;
const code = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const nullable = (v: unknown, max = 1000) => v === null || text(v, max);
const nullableCode = (v: unknown) => v === null || code(v);
const nullableId = (v: unknown) => v === null || isId(v);
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/.test(v) && Number.isFinite(Date.parse(v));
const nullableInstant = (v: unknown) => v === null || instant(v);
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
const list = (v: unknown, max = 1000): v is unknown[] => Array.isArray(v) && v.length <= max;
/** CP-5 新字段：缺省视为 null / 空列表 / false。 */
const opt = <T>(v: T | undefined, fallback: T): T => v === undefined ? fallback : v;
/** 与 compliance 一致的需求键格式。 */
export const requirementKeyPattern = /^[A-Za-z0-9_.-]{1,128}$/;

const FILE_ADDRESS = /^\/files\/v1\/([1-9][0-9]{0,18})$/;
/** 文件服务地址中的文件 ID；地址不符合 /files/v1/{fileId} 视为上游响应无效。 */
export function fileIdOf(fileAddress: unknown): string {
  const match = typeof fileAddress === 'string' ? FILE_ADDRESS.exec(fileAddress) : null; require(match); return match[1];
}
const nullableDay = (v: unknown) => v === null || (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v));
function decodeEvidence(value: unknown): Evidence {
  const v = value as Record<string, unknown>;
  require(v && isId(v.evidenceId) && code(v.status));
  const evidence: Evidence = { evidenceId: v.evidenceId as string, fileAddress: v.fileAddress as string, fileId: fileIdOf(v.fileAddress), status: v.status as string,
    documentType: opt(v.documentType as string | null, null), issueDate: opt(v.issueDate as string | null, null), expiryDate: opt(v.expiryDate as string | null, null),
    rejectReasonCode: opt(v.rejectReasonCode as string | null, null), reviewerId: opt(v.reviewerId as string | null, null), reviewedTime: opt(v.reviewedAt as string | null, null),
    reusedFromCaseId: opt(v.reusedFromCaseId as string | null, null) };
  require(nullableCode(evidence.documentType) && nullableDay(evidence.issueDate) && nullableDay(evidence.expiryDate) && nullableCode(evidence.rejectReasonCode)
    && nullableId(evidence.reviewerId) && nullableInstant(evidence.reviewedTime) && nullableId(evidence.reusedFromCaseId));
  return evidence;
}
function decodeRequirement(value: unknown): Requirement {
  const v = value as Record<string, unknown>; const types = opt(v?.documentTypes as unknown[], []);
  require(v && isId(v.requirementId) && code(v.requirementCode) && list(types, 32) && types.every(code) && typeof v.required === 'boolean' && code(v.status) && list(v.evidence));
  const requirement: Requirement = { requirementId: v.requirementId as string, requirementCode: v.requirementCode as string, documentTypes: [...types] as string[],
    required: v.required, status: v.status as string, evidence: (v.evidence as unknown[]).map(decodeEvidence), waivable: opt(v.waivable as boolean, false),
    maxAgeDays: opt(v.maxAgeDays as number | null, null), waivedBy: opt(v.waivedBy as string | null, null), waivedReasonCode: opt(v.waivedReasonCode as string | null, null),
    reusedFromCaseId: opt(v.reusedFromCaseId as string | null, null) };
  require(typeof requirement.waivable === 'boolean' && (requirement.maxAgeDays === null || (Number.isSafeInteger(requirement.maxAgeDays) && requirement.maxAgeDays >= 0))
    && nullableId(requirement.waivedBy) && nullableCode(requirement.waivedReasonCode) && nullableId(requirement.reusedFromCaseId));
  return requirement;
}
function decodeSubject(value: unknown): Subject {
  const v = value as Subject;
  require(v && isId(v.caseId) && code(v.caseStatus) && isId(v.partyId) && isId(v.profileId) && code(v.roleType) && code(v.qualificationType)
    && list(v.dependencyTypes ?? [], 16) && (v.dependencyTypes ?? []).every(code) && list(v.requirements) && nullable(opt(v.uboBasis, null), 64) && nullable(opt(v.jobTitle, null), 128));
  return { caseId: v.caseId, caseStatus: v.caseStatus, partyId: v.partyId, profileId: v.profileId, roleType: v.roleType, qualificationType: v.qualificationType,
    dependencyTypes: [...(v.dependencyTypes ?? [])], requirements: v.requirements.map(decodeRequirement), uboBasis: opt(v.uboBasis, null), jobTitle: opt(v.jobTitle, null) };
}
function decodeClassification(value: unknown): Classification {
  const v = value as Classification; require(v && code(v.industryCode) && typeof v.mccCode === 'string' && /^[0-9]{4}$/.test(v.mccCode) && typeof v.primary === 'boolean');
  return { industryCode: v.industryCode, mccCode: v.mccCode, primary: v.primary };
}
type Wire = Record<string, unknown>;
const field = <T>(v: Wire, key: string) => opt(v[key] as T | null | undefined, null) as T | null;
function decodeCheck(value: unknown, subject: Subject): Check {
  const v = value as Wire;
  require(v && isId(v.checkId) && code(v.checkCode) && code(v.checkMode) && typeof v.blocking === 'boolean' && code(v.status));
  const check: Check = { checkId: v.checkId as string, caseId: subject.caseId, subjectPartyId: subject.partyId, checkCode: v.checkCode as string,
    checkType: field<string>(v, 'checkType'), checkMode: v.checkMode as string, blocking: v.blocking, required: opt(v.required as boolean, true), status: v.status as string,
    outcome: field<string>(v, 'outcome'), reasonCode: field<string>(v, 'reasonCode'), note: field<string>(v, 'note'), reviewerId: field<string>(v, 'reviewerId'),
    reviewedTime: field<string>(v, 'reviewedAt'), validUntil: field<string>(v, 'validUntil'), reusedFromCaseId: field<string>(v, 'reusedFromCaseId') };
  require(nullableCode(check.checkType) && typeof check.required === 'boolean' && nullableCode(check.outcome) && nullableCode(check.reasonCode) && nullable(check.note)
    && nullableId(check.reviewerId) && nullableInstant(check.reviewedTime) && nullableInstant(check.validUntil) && nullableId(check.reusedFromCaseId));
  return check;
}
function decodeResolution(value: unknown): HitResolution {
  const v = value as Wire;
  const resolution: HitResolution = { resolution: v?.resolution as string, reasonCode: field<string>(v ?? {}, 'reasonCode'), note: field<string>(v ?? {}, 'note'),
    resolvedBy: field<string>(v ?? {}, 'reviewerId'), resolvedTime: v?.resolvedAt as string, onBehalfOfPartnerId: field<string>(v ?? {}, 'onBehalfOfPartnerId') };
  require(v && code(resolution.resolution) && nullableCode(resolution.reasonCode) && nullable(resolution.note) && nullableId(resolution.resolvedBy)
    && instant(resolution.resolvedTime) && nullableId(resolution.onBehalfOfPartnerId));
  return resolution;
}
function decodeHit(value: unknown): ScreeningHit {
  const v = value as Wire; const lists = opt(v?.listTypes as unknown[], []); const resolutions = opt(v?.resolutions as unknown[], []);
  require(v && isId(v.hitId) && code(v.status) && list(lists, 16) && lists.every(code) && list(resolutions, 64));
  const hit: ScreeningHit = { hitId: v.hitId as string, externalHitRef: field<string>(v, 'externalHitRef'), entityType: field<string>(v, 'entityType'),
    matchType: field<string>(v, 'matchType'), matchScore: field<string>(v, 'matchScore'), riskLevel: field<string>(v, 'riskLevel'), listTypes: [...lists] as string[],
    status: v.status as string, resolutions: resolutions.map(decodeResolution) };
  require(nullable(hit.externalHitRef, 128) && nullableCode(hit.entityType) && nullableCode(hit.matchType) && nullable(hit.matchScore, 32) && nullableCode(hit.riskLevel));
  return hit;
}
function decodeRun(value: unknown, subject: Subject): ScreeningRun {
  const v = value as Wire; const hits = opt(v?.hits as unknown[], []);
  require(v && isId(v.runId) && code(v.screeningType) && code(v.status) && list(hits, 200));
  const run: ScreeningRun = { runId: v.runId as string, caseId: subject.caseId, subjectPartyId: subject.partyId, screeningType: v.screeningType as string,
    providerCode: field<string>(v, 'providerCode'), status: v.status as string, triggerSource: field<string>(v, 'triggerSource'),
    startedTime: field<string>(v, 'startedAt'), completedTime: field<string>(v, 'completedAt'), hits: hits.map(decodeHit) };
  require(nullableCode(run.providerCode) && nullableCode(run.triggerSource) && nullableInstant(run.startedTime) && nullableInstant(run.completedTime));
  return run;
}
function decodeFactor(value: unknown): RiskFactor {
  const v = value as Wire;
  require(v && code(v.factorCode) && text(v.factorValue, 128));
  const factor: RiskFactor = { factorCode: v.factorCode as string, factorValue: v.factorValue as string, source: field<string>(v, 'source'),
    recordedBy: field<string>(v, 'recordedBy'), recordedTime: field<string>(v, 'recordedAt') };
  require(nullableCode(factor.source) && nullableId(factor.recordedBy) && nullableInstant(factor.recordedTime));
  return factor;
}
function decodeTimeline(value: unknown, index: number): TimelineEntry {
  const v = value as Wire;
  require(v && code(v.actionType) && instant(v.occurredAt));
  const entry: TimelineEntry = { changeId: `${index}`, caseId: field<string>(v, 'caseId'), actionType: v.actionType as string, fromStatus: field<string>(v, 'fromStatus'),
    toStatus: field<string>(v, 'toStatus'), actorId: field<string>(v, 'actorId'), operatedBy: field<string>(v, 'operatedBy'),
    onBehalfOfPartnerId: field<string>(v, 'onBehalfOfPartnerId'), reasonCode: field<string>(v, 'reasonCode'), occurredAt: v.occurredAt as string };
  require(nullableId(entry.caseId) && nullableCode(entry.fromStatus) && nullableCode(entry.toStatus) && nullableId(entry.actorId) && nullableCode(entry.operatedBy)
    && nullableId(entry.onBehalfOfPartnerId) && nullableCode(entry.reasonCode));
  return entry;
}
function decodeReturnError(value: unknown): ReturnError {
  const v = value as ReturnError;
  require(v && typeof v.requirementKey === 'string' && requirementKeyPattern.test(v.requirementKey) && nullableId(opt(v.subjectPartyId, null))
    && code(v.code) && nullable(opt(v.reason, null)) && code(v.remediation) && nullableInstant(opt(v.dueTime, null)));
  return { requirementKey: v.requirementKey, subjectPartyId: opt(v.subjectPartyId, null), code: v.code, reason: opt(v.reason, null), remediation: v.remediation,
    dueTime: opt(v.dueTime, null) };
}
/** 新增的案件级字段（辖区、档次、CRR、责任方、轮次、推荐、四眼）。 */
function decodeCaseFacts(v: Partial<ReviewCase>) {
  const facts = {
    jurisdictionCode: opt(v.jurisdictionCode, null), tier: opt(v.tier, null), riskRating: opt(v.riskRating, null),
    responsiblePartnerId: opt(v.responsiblePartnerId, null), operatedBy: opt(v.operatedBy, null), onBehalfOfPartnerId: opt(v.onBehalfOfPartnerId, null),
    roundNo: opt(v.roundNo, null), previousCaseId: opt(v.previousCaseId, null), reviewDueTime: opt(v.reviewDueTime, null),
    fourEyesRequired: opt(v.fourEyesRequired, false) ?? false, recommendation: opt(v.recommendation, null), recommendedBy: opt(v.recommendedBy, null),
    recommendedTime: opt((v as Wire).recommendedAt as string | null, null), recommendationNote: opt(v.recommendationNote, null), caseType: opt(v.caseType, null),
    programVersionId: opt(v.programVersionId, null), riskRatingNote: opt(v.riskRatingNote, null), riskRatedBy: opt(v.riskRatedBy, null),
    riskRatedAt: opt(v.riskRatedAt, null),
  };
  require((facts.jurisdictionCode === null || /^[A-Z][A-Z0-9-]{1,15}$/.test(facts.jurisdictionCode)) && nullableCode(facts.tier) && nullableCode(facts.riskRating)
    && nullableId(facts.responsiblePartnerId) && nullableCode(facts.operatedBy) && nullableId(facts.onBehalfOfPartnerId)
    && (facts.roundNo === null || (Number.isSafeInteger(facts.roundNo) && facts.roundNo >= 1)) && nullableId(facts.previousCaseId)
    && nullableInstant(facts.reviewDueTime) && typeof facts.fourEyesRequired === 'boolean' && nullableCode(facts.recommendation)
    && nullableId(facts.recommendedBy) && nullableInstant(facts.recommendedTime) && nullable(facts.recommendationNote, 2000) && nullableCode(facts.caseType)
    && nullableId(facts.programVersionId) && nullable(facts.riskRatingNote) && nullableId(facts.riskRatedBy) && nullableInstant(facts.riskRatedAt));
  return facts;
}
/** compliance 案件摘要或详情；expectedId 时必须是请求的案件。 */
export function decodeCase(value: unknown, expectedId?: string): ReviewCase {
  const v = value as ReviewCase;
  require(v && isId(v.caseId) && (!expectedId || v.caseId === expectedId) && text(v.caseNo, 64) && isId(v.applicationId) && isId(v.clientId)
    && text(v.sourceSubmissionId, 64) && code(v.qualificationType) && code(v.status) && /^[1-9][0-9]{0,9}$/.test(v.version)
    && instant(v.submittedAt) && (v.decidedAt === null || instant(v.decidedAt)) && nullable(v.publicNote)
    && (v.returnSection === null || v.returnSection === undefined || (returnSections as readonly string[]).includes(v.returnSection))
    && list(v.subjects ?? [], 64) && list(v.classifications ?? [], 20) && list(v.approvalBlockers ?? [], 32)
    && list(v.riskFactors ?? [], 64) && list(v.timeline ?? [], 500) && list(v.returnErrors ?? [], 200));
  const subjects = (v.subjects ?? []).map(decodeSubject);
  const wireSubjects = (v.subjects ?? []) as unknown as Wire[];
  const perSubject = <T>(key: string, decode: (item: unknown, subject: Subject) => T) => subjects.flatMap((subject, index) => {
    const items = opt(wireSubjects[index][key] as unknown[], []); require(list(items, 200)); return items.map(item => decode(item, subject)); });
  return { caseId: v.caseId, caseNo: v.caseNo, applicationId: v.applicationId, clientId: v.clientId, sourceSubmissionId: v.sourceSubmissionId,
    qualificationType: v.qualificationType, status: v.status, version: v.version, submittedAt: v.submittedAt, decidedAt: v.decidedAt,
    publicNote: v.publicNote, returnSection: v.returnSection ?? null, subjects,
    classifications: (v.classifications ?? []).map(decodeClassification), approvalBlockers: (v.approvalBlockers ?? []).map(b => { require(code(b)); return b; }),
    ...decodeCaseFacts(v), checks: perSubject('checks', decodeCheck), screenings: perSubject('screenings', decodeRun),
    riskFactors: (v.riskFactors ?? []).map(decodeFactor), timeline: (v.timeline ?? []).map(decodeTimeline), returnErrors: (v.returnErrors ?? []).map(decodeReturnError) };
}
export function decodeCasePage(value: unknown): PagingResponseBody<ReviewCase> {
  const v = value as PagingResponseBody<unknown>;
  require(v && list(v.items, 100) && Number.isSafeInteger(v.total) && v.total >= 0 && Number.isInteger(v.page) && v.page >= 1 && Number.isInteger(v.size) && v.size >= 1 && v.size <= 100);
  const items = v.items.map(item => decodeCase(item)); require(new Set(items.map(i => i.caseId)).size === items.length);
  return { items, page: v.page, size: v.size, total: v.total };
}
/** 资料值长度上限：client-master 最长的资料字段为退款政策链接（2048）。 */
const MAX_VALUE_LENGTH = 2048;
function decodeValues(value: unknown): Record<string, Record<string, string | null>> {
  require(value && typeof value === 'object' && !Array.isArray(value));
  const result: Record<string, Record<string, string | null>> = {};
  for (const [group, fields] of Object.entries(value as Record<string, unknown>)) {
    require(fields && typeof fields === 'object' && !Array.isArray(fields));
    result[group] = {};
    for (const [field, raw] of Object.entries(fields as Record<string, unknown>)) {
      require(raw === null || typeof raw === 'string' || typeof raw === 'boolean' || typeof raw === 'number');
      require(raw === null || String(raw).length <= MAX_VALUE_LENGTH);
      result[group][field] = raw === null ? null : String(raw);
    }
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
    documents: v.documents.map(d => { require(d && isId(d.documentId) && isId(d.subjectPartyId) && code(d.requirementCode) && code(d.documentType) && typeof d.subjectInScope === 'boolean');
      // 材料只存文件服务地址 /files/v1/{fileId}；文件 ID 由地址解析，供下载使用。
      return { documentId: d.documentId, subjectPartyId: d.subjectPartyId, requirementCode: d.requirementCode, documentType: d.documentType,
        fileAddress: d.fileAddress, fileId: fileIdOf(d.fileAddress), subjectInScope: d.subjectInScope }; }),
    declarations: decodeDeclarations(v.declarations) };
}
/** 资料版本为非负整数；client-master 以数字或数字字符串返回，页面统一为字符串。 */
const revision = (v: unknown): string | null => {
  if (v === undefined || v === null) return null;
  require((typeof v === 'number' && Number.isSafeInteger(v) && v >= 0) || (typeof v === 'string' && /^(0|[1-9][0-9]{0,18})$/.test(v)));
  return String(v);
};
function decodeDeclarations(value: unknown): Declaration[] {
  const rows = opt(value as unknown[] | undefined, []);
  require(list(rows, 64));
  return rows.map(raw => {
    const d = raw as Declaration;
    require(d && isId(d.declarationId) && code(d.declarationType) && (d.declaredValue === 'TRUE' || d.declaredValue === 'FALSE')
      && nullable(opt(d.termsVersion, null), 64) && instant(d.declaredAt) && nullableId(opt(d.declaredByAccountId, null))
      && (d.current === undefined || d.current === null || typeof d.current === 'boolean'));
    return { declarationId: d.declarationId, declarationType: d.declarationType, declaredValue: d.declaredValue, termsVersion: opt(d.termsVersion, null),
      declaredAt: d.declaredAt, declaredByAccountId: opt(d.declaredByAccountId, null), draftRevision: revision(d.draftRevision), current: opt(d.current, null) };
  });
}
/** 声明是否针对当前资料：CURRENT 一致、STALE 声明后资料或条款已变、UNKNOWN 旧响应未给出。 */
export type DeclarationStatus = 'CURRENT' | 'STALE' | 'UNKNOWN';
export interface DeclarationRow {
  key: string; declarationType: string; answer: string; declaredAt: string; declaredBy: string | null; termsVersion: string; status: DeclarationStatus; statusText: string;
}
/**
 * 声明区块的行：声明名称由页面按 onboarding-schema 取文案；取值显示为是 / 否文字，从不显示 TRUE / FALSE
 * <li>条款版本为空时显示“—”；状态按 current 翻译。</li>
 */
export function declarationRows(tReview: Translate, declarations: Declaration[]): DeclarationRow[] {
  return declarations.map(d => {
    const status: DeclarationStatus = d.current === true ? 'CURRENT' : d.current === false ? 'STALE' : 'UNKNOWN';
    return { key: d.declarationId, declarationType: d.declarationType, answer: tReview(`declarations.answer.${d.declaredValue}`), declaredAt: d.declaredAt,
      declaredBy: d.declaredByAccountId, termsVersion: d.termsVersion ?? '—', status, statusText: tReview(`declarations.status.${status}`) };
  });
}
export function decodeClassificationSet(value: unknown, caseId: string): ClassificationSet {
  const v = value as ClassificationSet;
  require(v && v.caseId === caseId && isId(v.applicationId) && typeof v.confirmed === 'boolean' && list(v.items, 20)
    && (v.confirmed ? isId(v.confirmedBy) && instant(v.confirmedAt) : v.confirmedBy === null && v.confirmedAt === null));
  return { caseId: v.caseId, applicationId: v.applicationId, confirmed: v.confirmed, confirmedBy: v.confirmedBy, confirmedAt: v.confirmedAt, items: v.items.map(decodeClassification) };
}
const availability = (v: unknown): v is Availability => v === 'AVAILABLE' || v === 'UNAVAILABLE';
/** 详情：client-master 部分标为不可用时对应字段必须为空，不能当作“无数据”。 */
export function decodeDetail(value: unknown, caseId: string): ReviewDetail {
  const v = value as ReviewDetail;
  require(v && availability(v.applicationStatus) && availability(v.classificationStatus) && availability(v.industriesStatus) && list(v.industries, 200)
    && availability(v.mccsStatus) && list(v.mccs, 2000)
    && (v.applicationStatus === 'AVAILABLE') === (v.application !== null) && (v.classificationStatus === 'AVAILABLE') === (v.classification !== null));
  const review = decodeCase(v.review, caseId);
  return { review, applicationStatus: v.applicationStatus, application: v.application === null ? null : decodeApplication(v.application, review.applicationId),
    classificationStatus: v.classificationStatus, classification: v.classification === null ? null : decodeClassificationSet(v.classification, caseId),
    industriesStatus: v.industriesStatus, industries: v.industries.map(decodeIndustryCategory), mccsStatus: v.mccsStatus, mccs: v.mccs.map(decodeMcc) };
}
export function decodeCommand(value: unknown, caseId: string): Command {
  const v = value as Command; require(v && v.caseId === caseId && code(v.status));
  return { caseId: v.caseId, status: v.status };
}
/**
 * 同一申请的当前案件：案件列表按提交时间倒序，首条即最新一轮
 * <li>返回 null 表示读不到；与当前查看的案件不同即为历史轮次。</li>
 */
export function latestCaseId(page: PagingResponseBody<ReviewCase>, applicationId: string): string | null {
  const latest = page.items.find(item => item.applicationId === applicationId);
  return latest ? latest.caseId : null;
}
/** OE_PROVIDER Partner 选项；同一 Partner 不能重复。 */
export function decodeResponsiblePartners(value: unknown): ResponsiblePartner[] {
  const v = value as { items: ResponsiblePartner[] };
  require(v && list(v.items, 500));
  const items = v.items.map(p => { require(p && isId(p.localPartnerId) && text(p.partnerNo, 64) && nullable(p.legalName, 255));
    return { localPartnerId: p.localPartnerId, partnerNo: p.partnerNo, legalName: p.legalName }; });
  require(new Set(items.map(p => p.localPartnerId)).size === items.length);
  return items;
}
export function partnerName(partners: readonly ResponsiblePartner[], partnerId: string | null): string | null {
  if (partnerId === null) return null;
  const partner = partners.find(p => p.localPartnerId === partnerId);
  return partner ? partner.legalName ?? partner.partnerNo : partnerId;
}
export interface FileDownload { fileId: string; displayName: string; mimeType: string; url: string; expiresAt: string }
/** 下载授权必须对应所点文件且为 HTTPS 地址；地址只用于本次跳转，不保存。 */
export function decodeFileDownload(value: unknown, fileId: string): FileDownload {
  const v = value as FileDownload;
  require(v && v.fileId === fileId && text(v.displayName) && text(v.mimeType, 128) && instant(v.expiresAt)
    && typeof v.url === 'string' && v.url.length <= 8192 && v.url.startsWith('https://'));
  return { fileId: v.fileId, displayName: v.displayName, mimeType: v.mimeType, url: v.url, expiresAt: v.expiresAt };
}
/** 批准按钮只看 compliance 门禁：案件在进行、没有未满足项，且四眼时决定人不是推荐人。 */
export function canApprove(review: ReviewCase, actorId?: string): boolean {
  return activeStatuses.includes(review.status) && review.approvalBlockers.length === 0 && !fourEyesBlocked(review, actorId);
}
/**
 * 四眼下的最终拒绝：须已有 REJECT 推荐且推荐人不是当前操作人（compliance 否则 422 RECOMMENDATION_MISSING /
 * RECOMMENDATION_NOT_REJECT / FOUR_EYES_SAME_PERSON）；不要求四眼时总可提交。
 */
export function rejectionBlocker(review: ReviewCase, actorId?: string): string | null {
  if (!review.fourEyesRequired) return null;
  if (review.recommendation === null) return 'RECOMMENDATION_MISSING';
  if (review.recommendation !== 'REJECT') return 'RECOMMENDATION_NOT_REJECT';
  return fourEyesBlocked(review, actorId) ? 'FOUR_EYES_SAME_PERSON' : null;
}
/** compliance 拒绝批准 / 最终拒绝的错误码：未满足门禁（含四眼）。 */
export const NOT_READY = 'CLIENT_REVIEW_NOT_READY';
export const isNotReady = (failure: { status: number; code: string } | undefined) => failure?.status === 422 && failure.code === NOT_READY;
/**
 * 结论被 422 NOT_READY 拒绝后要展示的未满足项：取刷新后的案件 approvalBlockers（compliance 按当前查看人计算），
 * 最终拒绝另加四眼条件；不解析错误文案（BFF 只透出错误码的默认说明）。
 */
export function unmetConditions(review: ReviewCase, decision: 'APPROVE' | 'REJECT', actorId?: string): string[] {
  if (decision === 'APPROVE') return [...review.approvalBlockers];
  const fourEyes = rejectionBlocker(review, actorId);
  return fourEyes ? [fourEyes] : review.approvalBlockers.filter(code => code.startsWith('RECOMMENDATION_') || code === 'FOUR_EYES_SAME_PERSON');
}
/** 四眼：程序要求四眼且当前操作人就是推荐人时不能做最终决定。 */
export function fourEyesBlocked(review: ReviewCase, actorId?: string): boolean {
  return review.fourEyesRequired && actorId !== undefined && review.recommendedBy !== null && review.recommendedBy === actorId;
}
/** 可人工录入结果的检查：MANUAL 且尚未完成。 */
export function checkAwaitingResult(check: Check): boolean {
  return check.checkMode === 'MANUAL' && (check.status === 'REQUESTED' || check.status === 'RUNNING' || (check.status === 'COMPLETED' && check.outcome === 'REVIEW'));
}
/** 可豁免：程序允许且要求仍未满足（PENDING / REJECTED）。 */
export function canWaive(requirement: Requirement): boolean {
  return requirement.waivable && (requirement.status === 'PENDING' || requirement.status === 'REJECTED');
}
export const openHits = (review: ReviewCase) => review.screenings.flatMap(run => run.hits).filter(hit => hit.status === 'OPEN');
/** 工作台分区计数：待处理的材料、检查、命中。 */
export function workbenchCounts(review: ReviewCase) {
  const evidence = review.subjects.flatMap(s => s.requirements).flatMap(r => r.evidence).filter(e => e.status === 'RECEIVED').length;
  const checks = review.checks.filter(checkAwaitingResult).length;
  return { evidence, checks, hits: openHits(review).length };
}
// ---------- 行业与 MCC 分类（O03 / O04） ----------

/** 编辑中的一行：categoryCode 只用于界面联动，提交的是 industryCode（细类；只选到大类时为空，提交大类编码由 client-master 拒绝）。 */
export interface ClassificationRow { categoryCode: string; industryCode: string; mccCode: string; primary: boolean }
/** 一个行业编码在 ACTIVE 行业树中的位置：大类本身（subcategory 为 null）或细类及其大类。 */
export interface IndustryPosition { category: IndustryCategory; subcategory: RefIndustry | null }
export type ClassificationField = 'industryCode' | 'mccCode';
/** 行内问题：显示在对应控件下；文案为 `client-review.classification.rowIssue.<code>`。 */
export interface RowIssue { field: ClassificationField; code: 'industryRequired' | 'categoryOnly' | 'industryUnavailable' | 'mccFormat' | 'mccInactive'
  | 'mccUnknown' | 'duplicate' | 'rejected'; params?: Record<string, string> }
export const maxClassifications = 20;

/** 大类与细类编码 → 在树中的位置。 */
export function industryIndex(categories: readonly IndustryCategory[]): Map<string, IndustryPosition> {
  const index = new Map<string, IndustryPosition>();
  for (const category of categories) {
    index.set(category.industryCode, { category, subcategory: null });
    for (const child of category.children) index.set(child.industryCode, { category, subcategory: child });
  }
  return index;
}

/** 已保存的分类转为编辑行：细类带出大类；存量只到大类的编码（AC-10）只选中大类、细类留空；树中没有的编码原样保留。 */
export function classificationRows(items: readonly Classification[], index: Map<string, IndustryPosition>): ClassificationRow[] {
  return items.map(item => {
    const position = index.get(item.industryCode);
    if (position && !position.subcategory) return { categoryCode: item.industryCode, industryCode: '', mccCode: item.mccCode, primary: item.primary };
    return { categoryCode: position?.category.industryCode ?? '', industryCode: item.industryCode, mccCode: item.mccCode, primary: item.primary };
  });
}

/** 提交内容：细类为空时提交大类编码（由 client-master 以"只选到大类"拒绝），保持与页面所见一致。 */
export function classificationItems(rows: readonly ClassificationRow[]): Classification[] {
  return rows.map(row => ({ industryCode: row.industryCode || row.categoryCode, mccCode: row.mccCode, primary: row.primary }));
}

/** 细类的建议 MCC；没有细类或细类没有建议时为 null。 */
export function suggestedMcc(row: ClassificationRow, index: Map<string, IndustryPosition>): string | null {
  return index.get(row.industryCode)?.subcategory?.defaultMccCode ?? null;
}

/** 选择细类：带出建议 MCC（运营可再改选）；选择大类：清空细类与 MCC。 */
export function chooseSubcategory(row: ClassificationRow, industryCode: string, index: Map<string, IndustryPosition>): ClassificationRow {
  const position = index.get(industryCode);
  return { ...row, categoryCode: position?.category.industryCode ?? row.categoryCode, industryCode,
    mccCode: position?.subcategory?.defaultMccCode ?? row.mccCode };
}
export function chooseCategory(row: ClassificationRow, categoryCode: string): ClassificationRow {
  return row.categoryCode === categoryCode ? row : { ...row, categoryCode, industryCode: '', mccCode: '' };
}

/** MCC 标记：等于建议为"建议"，不同为"已改 · 建议 xxxx"，没有建议时不标。 */
export function mccMark(row: ClassificationRow, index: Map<string, IndustryPosition>): { kind: 'suggested' } | { kind: 'changed'; suggested: string } | null {
  const suggested = suggestedMcc(row, index);
  if (!suggested || !row.mccCode) return null;
  return row.mccCode === suggested ? { kind: 'suggested' } : { kind: 'changed', suggested };
}

/** 整组问题（不属于某一行）：至少一行、至多 20 行、恰好一个主要分类。 */
export function classificationListIssues(rows: readonly ClassificationRow[]): string[] {
  const issues: string[] = [];
  if (rows.length === 0) issues.push(t('classification.issue.empty'));
  if (rows.length > maxClassifications) issues.push(t('classification.issue.tooMany'));
  if (rows.filter(row => row.primary).length !== 1) issues.push(t('classification.issue.onePrimary'));
  return issues;
}

/**
 * 行内问题（前端预检，最终以 client-master 为准）：行业必须选到 ACTIVE 细类；MCC 必须四位数字，已知停用的 MCC 直接标出；行业 + MCC 不得重复
 * <li>mccs 为详情带回的 MCC（任意状态）与本页搜索选中的 MCC；不在其中的编码不预判，交给服务端。</li>
 */
export function classificationRowIssues(rows: readonly ClassificationRow[], index: Map<string, IndustryPosition>,
  mccs: ReadonlyMap<string, RefMcc>): Map<number, RowIssue[]> {
  const result = new Map<number, RowIssue[]>();
  const add = (row: number, issue: RowIssue) => result.set(row, [...(result.get(row) ?? []), issue]);
  const seen = new Set<string>();
  rows.forEach((row, i) => {
    if (!row.industryCode && !row.categoryCode) add(i, { field: 'industryCode', code: 'industryRequired' });
    else if (!row.industryCode) add(i, { field: 'industryCode', code: 'categoryOnly', params: { code: row.categoryCode } });
    else if (!index.get(row.industryCode)?.subcategory) add(i, { field: 'industryCode', code: 'industryUnavailable', params: { code: row.industryCode } });
    const mcc = mccs.get(row.mccCode);
    if (!/^[0-9]{4}$/.test(row.mccCode)) add(i, { field: 'mccCode', code: 'mccFormat' });
    else if (mcc && mcc.status !== 'ACTIVE') add(i, { field: 'mccCode', code: 'mccInactive', params: { code: row.mccCode } });
    const key = `${row.industryCode || row.categoryCode}:${row.mccCode}`;
    if (seen.has(key)) add(i, { field: 'mccCode', code: 'duplicate' });
    seen.add(key);
  });
  return result;
}

/**
 * client-master 422 的字段路径（items[i].industryCode / items[i].mccCode，BFF 已投影到本次请求体）转为行内问题
 * <li>远端文案不外显，原因按本地数据判断：大类 → 只选到大类；行业不在 ACTIVE 树 → 不存在或已停用；MCC 已知停用 → 已停用，否则不存在或已停用。</li>
 */
export function classificationServerIssues(fieldErrors: readonly { field: string }[], rows: readonly ClassificationRow[],
  mccs: ReadonlyMap<string, RefMcc>): Map<number, RowIssue[]> {
  const result = new Map<number, RowIssue[]>();
  for (const error of fieldErrors) {
    const match = /^items\[(\d+)\]\.(industryCode|mccCode)$/.exec(error.field);
    if (!match || Number(match[1]) >= rows.length) continue;
    const row = rows[Number(match[1])]; const field = match[2] as ClassificationField;
    const issue: RowIssue = field === 'industryCode'
      ? (!row.industryCode && row.categoryCode ? { field, code: 'categoryOnly', params: { code: row.categoryCode } }
        : { field, code: 'industryUnavailable', params: { code: row.industryCode || row.categoryCode } })
      : mccs.get(row.mccCode)?.status === 'INACTIVE' || mccs.get(row.mccCode)?.status === 'DRAFT'
        ? { field, code: 'mccInactive', params: { code: row.mccCode } } : { field, code: 'mccUnknown', params: { code: row.mccCode } };
    result.set(Number(match[1]), [...(result.get(Number(match[1])) ?? []), issue]);
  }
  return result;
}

/** 行内问题文案（调用时取，随语言）。 */
export function rowIssueText(issue: RowIssue): string {
  return t('classification.rowIssue.' + issue.code, issue.params);
}

/** 与已保存内容相比的修改处：按行比较（行数变化计入差值；新增的空白行不算修改）。 */
export function classificationChanges(rows: readonly ClassificationRow[], saved: readonly Classification[]): number {
  const items = classificationItems(rows);
  const changed = items.filter((item, i) => saved[i] ? saved[i].industryCode !== item.industryCode || saved[i].mccCode !== item.mccCode
    || saved[i].primary !== item.primary : !!(item.industryCode || item.mccCode)).length;
  return changed + Math.max(0, saved.length - items.length);
}

export function filters(values: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).filter(([, v]) => v?.trim()).map(([k, v]) => [k, v!.trim()]));
}
/** 写命令统一带代操作 PayFac；平台自营（无责任方）时为 null。 */
export function withOnBehalf<T extends object>(payload: T, onBehalfOfPartnerId: string | null): T & { onBehalfOfPartnerId: string | null } {
  return { ...payload, onBehalfOfPartnerId };
}

/** 退回草稿中的一行需求级错误。 */
export interface ReturnErrorDraft { requirementKey: string; subjectPartyId: string | null; code: string; remediation: string; reason: string }
/** 被拒材料与未满足的必需要求生成退回草稿：材料要求键为 document.<requirementCode>。 */
export function suggestedReturnErrors(review: ReviewCase): ReturnErrorDraft[] {
  const drafts: ReturnErrorDraft[] = [];
  for (const subject of review.subjects) {
    for (const requirement of subject.requirements) {
      const rejected = requirement.evidence.find(e => e.status === 'REJECTED');
      if (!rejected && !(requirement.required && requirement.status === 'PENDING')) continue;
      const reason = rejected?.rejectReasonCode;
      drafts.push({ requirementKey: `document.${requirement.requirementCode}`, subjectPartyId: subject.partyId,
        code: reason && reason !== 'OTHER' && (returnErrorCodes as readonly string[]).includes(reason) ? reason : rejected ? 'DOCUMENT_UNREADABLE' : 'DOCUMENT_MISSING',
        remediation: 'UPLOAD_DOCUMENT', reason: '' });
    }
  }
  return drafts;
}
/** 退回草稿的前端校验；重复的“要求 + 主体 + 错误码”只保留一行。 */
export function returnErrorIssues(drafts: readonly ReturnErrorDraft[]): string[] {
  const issues: string[] = [];
  if (drafts.length === 0) issues.push(t('return.issue.empty'));
  if (drafts.length > 100) issues.push(t('return.issue.tooMany'));
  if (drafts.some(d => !requirementKeyPattern.test(d.requirementKey))) issues.push(t('return.issue.keyFormat'));
  if (drafts.some(d => !d.code || !d.remediation)) issues.push(t('return.issue.codeRequired'));
  if (drafts.some(d => d.reason.length > 1000)) issues.push(t('return.issue.reasonTooLong'));
  if (new Set(drafts.map(d => `${d.requirementKey}|${d.subjectPartyId}|${d.code}`)).size !== drafts.length) issues.push(t('return.issue.duplicate'));
  return issues;
}
/** 退回请求体：去空白，空原因为 null。 */
export function returnPayload(drafts: readonly ReturnErrorDraft[], dueDays: number, publicNote: string, reasonCode: string) {
  return { reasonCode: reasonCode.trim(), errors: drafts.map(d => ({ requirementKey: d.requirementKey.trim(), subjectPartyId: d.subjectPartyId, code: d.code,
    reason: d.reason.trim() || null, remediation: d.remediation })), dueDays, publicNote: publicNote.trim() };
}
/** CRR 草稿校验：评级必选，因子至少一条且同一因子代码 + 值不重复。 */
export function riskRatingIssues(rating: string | undefined, factors: readonly { factorCode: string; factorValue: string }[], note = 'x'): string[] {
  const issues: string[] = [];
  if (!rating) issues.push(t('risk.issue.ratingRequired'));
  if (factors.length > 50) issues.push(t('risk.issue.tooMany'));
  if (!note.trim()) issues.push(t('risk.issue.noteRequired'));
  if (factors.length === 0) issues.push(t('risk.issue.factorRequired'));
  if (factors.some(f => !f.factorCode || !f.factorValue.trim())) issues.push(t('risk.issue.factorIncomplete'));
  if (factors.some(f => f.factorValue.trim().length > 128)) issues.push(t('risk.issue.valueTooLong'));
  if (new Set(factors.map(f => `${f.factorCode}|${f.factorValue.trim()}`)).size !== factors.length) issues.push(t('risk.issue.duplicate'));
  return issues;
}
