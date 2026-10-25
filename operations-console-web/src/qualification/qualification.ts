import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';

/**
 * 主体资格（compliance 角色资格）的 BFF 路径与页面模型：主体查找 B7、列表 B8、历史 B9、手工登记 B10、撤销 B11。
 * <li>roleId：OPERATING_ENTITY 为 entityNo，CLIENT / LOCAL_PARTNER 为十进制 ID；主体查找的 subjectRef 与之相同。</li>
 * <li>显示名为 `qualification.enum.<type>.<code>`（roleType、qualificationType、status、effectiveStatus、decisionSource、
 * reviewStatus、triggerType、outcome、subjectStatus），用 enumLabel 读取，未知代码原样显示。</li>
 */
export const qualificationApi = '/qualification-admin/v1';
export const qualificationRoute = '/compliance/qualifications';
export const qualificationManageButton = 'OPERATIONS_QUALIFICATIONS_MANAGE';

export const roleTypes = ['CLIENT', 'OPERATING_ENTITY', 'LOCAL_PARTNER'] as const;
export type RoleType = typeof roleTypes[number];
export const qualificationTypes = ['INDIVIDUAL_KYC', 'ORGANIZATION_KYB', 'PARTNER_DUE_DILIGENCE', 'ENTITY_UNDERWRITING'] as const;
/** 存储状态（列表筛选 status）。 */
export const qualificationStatuses = ['APPROVED', 'REJECTED', 'REVOKED', 'EXPIRED'] as const;
export const effectiveStatuses = ['ELIGIBLE', 'NOT_REGISTERED', 'REJECTED', 'REVOKED', 'EXPIRED', 'NOT_YET_VALID', 'SETTLEMENT_BLOCKED'] as const;
export const decisionSources = ['AUTOMATED', 'REVIEWER', 'OVERRIDE', 'MANUAL'] as const;
export const triggerTypes = ['OPERATIONS_REVIEW', 'OPERATIONS_MANUAL', 'BACKFILL'] as const;
export const pageSizes = [10, 20, 50, 100, 200];

/** 每个角色允许手工登记的资格类型（compliance 校验同一组合）。 */
const allowedTypes: Record<RoleType, readonly string[]> = {
  CLIENT: ['INDIVIDUAL_KYC', 'ORGANIZATION_KYB'],
  OPERATING_ENTITY: ['ENTITY_UNDERWRITING'],
  LOCAL_PARTNER: ['PARTNER_DUE_DILIGENCE'],
};
export function allowedQualificationTypes(roleType: string): readonly string[] {
  return isRoleType(roleType) ? allowedTypes[roleType] : [];
}

export interface Qualification {
  qualificationId: string; roleType: RoleType; roleId: string; qualificationType: string; status: string; effectiveStatus: string;
  validFrom: string; validTo: string | null; currentDecisionId: string; decisionSource: string; reviewStatus: string; version: string; updatedAt: string;
}
export interface QualificationRow { qualification: Qualification; subjectName: string | null }
export interface Subject { roleType: RoleType; roleId: string; displayName: string | null; status: string }
export interface HistoryItem {
  changeId: string; aggregateVersion: string; fromStatus: string | null; toStatus: string; triggerType: string; reasonCode: string | null;
  actorId: string | null; occurredAt: string; decisionId: string; decisionSource: string; outcome: string; decidedBy: string | null;
  basisNote: string | null; validFrom: string; validTo: string | null;
}
export interface History { qualificationId: string; items: HistoryItem[] }
export type Availability = 'AVAILABLE' | 'UNAVAILABLE';

export const isId = (v: unknown): v is string => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v) && BigInt(v) <= 9223372036854775807n;
export const entityNoPattern = /^[A-Z]{2}[0-9]{3}$/;
export const isRoleType = (v: unknown): v is RoleType => typeof v === 'string' && (roleTypes as readonly string[]).includes(v);
/** 角色标识格式：OE 为 entityNo，其他为十进制 ID。 */
export function isRoleId(roleType: string, roleId: unknown): roleId is string {
  if (roleType === 'OPERATING_ENTITY') return typeof roleId === 'string' && entityNoPattern.test(roleId);
  return isRoleType(roleType) && isId(roleId);
}
const text = (v: unknown, max = 255): v is string => typeof v === 'string' && v.length <= max;
const nullableText = (v: unknown, max = 1000) => v === null || text(v, max);
const code = (v: unknown): v is string => typeof v === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v);
const nullableCode = (v: unknown) => v === null || code(v);
const decimal = (v: unknown): v is string => typeof v === 'string' && /^[0-9]{1,19}$/.test(v);
export const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(v) && Number.isFinite(Date.parse(v));
const nullableInstant = (v: unknown) => v === null || instant(v);
const list = (v: unknown, max = 1000): v is unknown[] => Array.isArray(v) && v.length <= max;
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
export const isAvailability = (v: unknown): v is Availability => v === 'AVAILABLE' || v === 'UNAVAILABLE';

/** 一条资格；expectedId 时必须是请求的资格（登记、撤销命令的结果）。 */
export function decodeQualification(value: unknown, expectedId?: string): Qualification {
  const v = value as Qualification;
  require(v && isId(v.qualificationId) && (!expectedId || v.qualificationId === expectedId) && isRoleType(v.roleType) && isRoleId(v.roleType, v.roleId)
    && code(v.qualificationType) && code(v.status) && code(v.effectiveStatus) && instant(v.validFrom) && nullableInstant(v.validTo)
    && isId(v.currentDecisionId) && code(v.decisionSource) && code(v.reviewStatus) && decimal(v.version) && instant(v.updatedAt));
  return { qualificationId: v.qualificationId, roleType: v.roleType, roleId: v.roleId, qualificationType: v.qualificationType, status: v.status,
    effectiveStatus: v.effectiveStatus, validFrom: v.validFrom, validTo: v.validTo, currentDecisionId: v.currentDecisionId,
    decisionSource: v.decisionSource, reviewStatus: v.reviewStatus, version: v.version, updatedAt: v.updatedAt };
}
/** 资格列表须属于同一个主体时传 subject（详情页资格卡、主体卡）。 */
export function decodeQualifications(value: unknown, subject?: { roleType: string; roleId: string }): Qualification[] {
  require(list(value, 200));
  const items = value.map(item => decodeQualification(item));
  require(new Set(items.map(i => i.qualificationId)).size === items.length);
  require(!subject || items.every(i => i.roleType === subject.roleType && i.roleId === subject.roleId));
  return items;
}
function decodePageShape(v: PagingResponseBody<unknown>, maxSize: number) {
  require(v && list(v.items, maxSize) && Number.isSafeInteger(v.total) && v.total >= 0 && Number.isInteger(v.page) && v.page >= 1
    && Number.isInteger(v.size) && v.size >= 1 && v.size <= maxSize);
}
export function decodeQualificationPage(value: unknown): PagingResponseBody<QualificationRow> {
  const v = value as PagingResponseBody<QualificationRow>; decodePageShape(v, 200);
  const items = v.items.map(row => { require(row && nullableText(row.subjectName, 512)); return { qualification: decodeQualification(row.qualification), subjectName: row.subjectName }; });
  require(new Set(items.map(i => i.qualification.qualificationId)).size === items.length);
  return { items, page: v.page, size: v.size, total: v.total };
}
/** 主体查找结果须是所查的角色与主体。 */
export function decodeSubject(value: unknown, roleType: string, subjectRef: string): Subject {
  const v = value as Subject;
  require(v && v.roleType === roleType && isRoleType(v.roleType) && v.roleId === subjectRef && nullableText(v.displayName, 512) && code(v.status));
  return { roleType: v.roleType, roleId: v.roleId, displayName: v.displayName, status: v.status };
}
export function decodeHistory(value: unknown, qualificationId: string): History {
  const v = value as History;
  require(v && v.qualificationId === qualificationId && list(v.items));
  return { qualificationId: v.qualificationId, items: v.items.map(i => {
    require(i && isId(i.changeId) && decimal(i.aggregateVersion) && nullableCode(i.fromStatus) && code(i.toStatus) && code(i.triggerType)
      && nullableCode(i.reasonCode) && nullableText(i.actorId, 64) && instant(i.occurredAt) && isId(i.decisionId) && code(i.decisionSource)
      && code(i.outcome) && nullableText(i.decidedBy, 64) && nullableText(i.basisNote) && instant(i.validFrom) && nullableInstant(i.validTo));
    return { changeId: i.changeId, aggregateVersion: i.aggregateVersion, fromStatus: i.fromStatus, toStatus: i.toStatus, triggerType: i.triggerType,
      reasonCode: i.reasonCode, actorId: i.actorId, occurredAt: i.occurredAt, decisionId: i.decisionId, decisionSource: i.decisionSource,
      outcome: i.outcome, decidedBy: i.decidedBy, basisNote: i.basisNote, validFrom: i.validFrom, validTo: i.validTo };
  }) };
}

/** 撤销只对存储状态 APPROVED 的资格开放（其余 compliance 返回 409 QUALIFICATION_NOT_ACTIVE）。 */
export const canRevoke = (q: Qualification) => q.status === 'APPROVED';

/** 本地时间 → compliance 要求的 UTC 六位微秒。 */
export function utcMicros(value: Date): string {
  return value.toISOString().replace(/\.(\d{3})Z$/, '.$1000Z');
}

export interface ManualDecisionDraft {
  roleType: RoleType; roleId: string; qualificationType: string; validFrom: Date; validTo: Date | null; reasonCode: string; basisNote: string;
}
/**
 * B10 请求体：已有资格行时必须带它的 version 作 expectedVersion，首次登记为 null；
 * 类型须是该角色允许的类型、结束时间须晚于开始时间，否则返回 null 由表单提示。
 */
export function manualDecisionPayload(draft: ManualDecisionDraft, existing: Qualification | null) {
  if (!allowedQualificationTypes(draft.roleType).includes(draft.qualificationType)) return null;
  if (draft.validTo && draft.validTo.getTime() <= draft.validFrom.getTime()) return null;
  if (existing && (existing.roleType !== draft.roleType || existing.roleId !== draft.roleId || existing.qualificationType !== draft.qualificationType)) return null;
  return { roleType: draft.roleType, roleId: draft.roleId, qualificationType: draft.qualificationType, validFrom: utcMicros(draft.validFrom),
    validTo: draft.validTo ? utcMicros(draft.validTo) : null, reasonCode: draft.reasonCode.trim(), basisNote: draft.basisNote.trim(),
    expectedVersion: existing ? existing.version : null };
}
export function revocationPayload(q: Qualification, reasonCode: string, basisNote: string) {
  return { expectedVersion: q.version, reasonCode: reasonCode.trim(), basisNote: basisNote.trim() };
}

/** 主体卡：每个允许类型对应当前资格行（没有则 null，表示未登记）。 */
export function subjectQualificationSlots(roleType: string, items: Qualification[]): { qualificationType: string; qualification: Qualification | null }[] {
  return allowedQualificationTypes(roleType).map(type => ({ qualificationType: type, qualification: items.find(i => i.qualificationType === type) ?? null }));
}

/** 列表与主体的 URL 查询：只保留合法的非空值；roleId 必须与合法的 roleType 一起出现。 */
export const listQueryKeys = ['roleType', 'roleId', 'qualificationType', 'status'] as const;
export function readListQuery(search: string): Record<string, string> {
  const query = new URLSearchParams(search); const result: Record<string, string> = {};
  const roleType = query.get('roleType')?.trim() ?? '';
  if (isRoleType(roleType)) {
    result.roleType = roleType;
    const roleId = query.get('roleId')?.trim().toUpperCase() ?? '';
    if (isRoleId(roleType, roleId)) result.roleId = roleId;
  }
  const type = query.get('qualificationType')?.trim() ?? '';
  if ((qualificationTypes as readonly string[]).includes(type)) result.qualificationType = type;
  const status = query.get('status')?.trim() ?? '';
  if ((qualificationStatuses as readonly string[]).includes(status)) result.status = status;
  return result;
}
/** 详情页“查看资格”跳转：预填主体。 */
export function qualificationLink(roleType: RoleType, roleId: string): string {
  return `${qualificationRoute}?${new URLSearchParams({ roleType, roleId })}`;
}
/** 页面颜色：实时状态 ELIGIBLE 为绿，未登记为灰，其余为红或橙。 */
export function effectiveStatusColor(status: string): string {
  if (status === 'ELIGIBLE') return 'green';
  if (status === 'NOT_REGISTERED') return 'default';
  if (status === 'NOT_YET_VALID' || status === 'SETTLEMENT_BLOCKED') return 'orange';
  return 'red';
}

/** 日期选择器的本地值（YYYY-MM-DDTHH:mm:ss，不含时区）与 Date 互转；非法值返回 null。 */
export function localInputOf(date: Date): string {
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}T${two(date.getHours())}:${two(date.getMinutes())}:${two(date.getSeconds())}`;
}
export function fromLocalInput(value: string | null | undefined): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date : null;
}

/**
 * 写命令失败的提示 key（qualification 命名空间）：版本变化提示刷新，主体终态、组合非法、对象不存在各有说明；
 * 其他错误已由统一通知展示，返回 null。
 */
export function commandFailureKey(failure: ApiError | undefined): string | null {
  if (!failure) return null;
  if (failure.status === 409) return failure.code === 'CONCURRENT_REQUEST' ? 'command.busy' : 'command.conflict';
  if (failure.status === 422) return failure.code === 'QUALIFICATION_SUBJECT_TERMINATED' ? 'command.subjectTerminated' : 'command.invalid';
  if (failure.status === 404) return 'command.notFound';
  return null;
}

/** 写按钮只对持有对应按钮资源（MANAGE 权限）的用户显示。 */
export function hasButton(resources: readonly { resourceType: string; resourceCode: string }[], code: string): boolean {
  return resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);
}
/** 资格行或主体卡某一类型的可用操作：历史需要已有资格行；登记需要 MANAGE 且主体未终止；撤销还需要存储状态 APPROVED。 */
export function qualificationActions(q: Qualification | null, canManage: boolean, subjectTerminated = false) {
  return { history: q !== null, register: canManage && !subjectTerminated, revoke: canManage && q !== null && canRevoke(q) };
}
