import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';

export const tenantApi = '/tenant-access-admin/v1/tenants';
export type SecurityStatus = 'ACTIVE' | 'SUSPENDED';
export interface Reason { code: string; message: string }
export interface Tenant {
  tenantId: string; tenantType: 'CLIENT' | 'PARTNER'; ownerRefId: string; tenantName: string;
  ownerStatus: 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED'; securityStatus: SecurityStatus;
  version: number; tenantAccessAllowed: boolean; restrictionReasons: Reason[]; createdAt: string; updatedAt: string;
}
export interface Member {
  tenantId: string; memberId: string; accountId: string; emailMasked: string | null;
  memberType: 'OWNER' | 'ADMIN' | 'MEMBER'; memberStatus: 'ACTIVE' | 'SUSPENDED' | 'REVOKED';
  accountStatus: 'PENDING_VERIFICATION' | 'ACTIVE' | 'DISABLED' | 'CLOSED'; createdAt: string; updatedAt: string;
}
export interface RoleGrant {
  grantId: string; roleId: string; roleCode: string; roleName: string; roleScope: 'PLATFORM' | 'TENANT';
  roleTenantId: string | null; roleStatus: 'ACTIVE' | 'INACTIVE'; grantStatus: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
  effectiveFrom: string; effectiveTo: string | null; effective: boolean; reasons: Reason[];
}
export interface MemberPermission {
  permissionId: string; permissionCode: string; resourceType: string; actionCode: string; permissionStatus: 'ACTIVE' | 'INACTIVE';
  effective: boolean; sourceCount: number; effectiveSourceCount: number; reasons: Reason[];
}
export interface PermissionSource extends RoleGrant { rolePermissionId: string; rolePermissionStatus: 'ACTIVE' | 'REVOKED' }
export interface EvaluatedPage<T> extends PagingResponseBody<T> { evaluatedAt: string }
export interface SecurityMutation { tenantId: string; securityStatus: SecurityStatus; version: number }
export const ownerLabels = { PENDING: '待激活', ACTIVE: '正常', SUSPENDED: '已停用', TERMINATED: '已终止' };
export const securityLabels = { ACTIVE: '正常', SUSPENDED: '已暂停' };
export const memberLabels = { OWNER: '所有者', ADMIN: '管理员', MEMBER: '成员' };
export const stateLabels: Record<string, string> = { ACTIVE: '有效', INACTIVE: '已停用', SUSPENDED: '已暂停', REVOKED: '已撤销', EXPIRED: '已过期', PENDING_VERIFICATION: '待验证', DISABLED: '已停用', CLOSED: '已关闭' };
export const reasonCodes = ['TENANT_OWNER_INACTIVE', 'TENANT_SECURITY_SUSPENDED', 'ACCOUNT_INACTIVE', 'MEMBER_INACTIVE', 'ROLE_INACTIVE', 'GRANT_REVOKED', 'GRANT_NOT_STARTED', 'GRANT_EXPIRED', 'ROLE_PERMISSION_REVOKED', 'PERMISSION_INACTIVE'];
export const isId = (v: unknown): v is string => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v) && BigInt(v) <= 9223372036854775807n;
const version = (v: number) => Number.isInteger(v) && v > 0 && v <= 2147483647;
const text = (v: unknown, max: number): v is string => typeof v === 'string' && v.length <= max;
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$/.test(v) && Number.isFinite(Date.parse(v));
/** 比较批准的 UTC 微秒时间，避免字符串小数位或 JS 毫秒精度改变有效区间。 */
function instantMicros(value: string): bigint {
  const seconds = BigInt(Date.parse(`${value.slice(0, 19)}Z`) / 1000);
  const fraction = value.includes('.') ? value.slice(20, -1).padEnd(6, '0') : '0';
  return seconds * 1000000n + BigInt(fraction);
}
const oneOf = (v: unknown, values: readonly string[]) => typeof v === 'string' && values.includes(v);
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
function reasons(value: unknown, effective: boolean): Reason[] {
  require(Array.isArray(value) && value.length <= reasonCodes.length && typeof effective === 'boolean' && effective === (value.length === 0));
  let previous = -1;
  return value.map(v => {
    require(v && text(v.code, 64) && text(v.message, 255));
    const index = reasonCodes.indexOf(v.code); require(index > previous); previous = index;
    return { code: v.code, message: v.message };
  });
}
export function decodeTenant(value: unknown, expectedId?: string): Tenant {
  const v = value as Tenant;
  require(v && isId(v.tenantId) && (!expectedId || expectedId === v.tenantId) && isId(v.ownerRefId) && text(v.tenantName, 255)
    && oneOf(v.tenantType, ['CLIENT', 'PARTNER']) && oneOf(v.ownerStatus, Object.keys(ownerLabels)) && oneOf(v.securityStatus, Object.keys(securityLabels))
    && version(v.version) && instant(v.createdAt) && instant(v.updatedAt)
    && v.tenantAccessAllowed === (v.ownerStatus === 'ACTIVE' && v.securityStatus === 'ACTIVE'));
  const restrictionReasons = reasons(v.restrictionReasons, v.tenantAccessAllowed); require(restrictionReasons.every(r => reasonCodes.indexOf(r.code) < 2));
  return { tenantId: v.tenantId, tenantType: v.tenantType, ownerRefId: v.ownerRefId, tenantName: v.tenantName, ownerStatus: v.ownerStatus,
    securityStatus: v.securityStatus, version: v.version, tenantAccessAllowed: v.tenantAccessAllowed, restrictionReasons, createdAt: v.createdAt, updatedAt: v.updatedAt };
}
export function decodeMember(value: unknown, tenantId: string, memberId?: string): Member {
  const v = value as Member;
  require(v && v.tenantId === tenantId && isId(v.memberId) && (!memberId || v.memberId === memberId) && isId(v.accountId)
    && (v.emailMasked === null || text(v.emailMasked, 255)) && oneOf(v.memberType, Object.keys(memberLabels))
    && oneOf(v.memberStatus, ['ACTIVE', 'SUSPENDED', 'REVOKED']) && oneOf(v.accountStatus, ['PENDING_VERIFICATION', 'ACTIVE', 'DISABLED', 'CLOSED'])
    && instant(v.createdAt) && instant(v.updatedAt));
  return { tenantId: v.tenantId, memberId: v.memberId, accountId: v.accountId, emailMasked: v.emailMasked, memberType: v.memberType,
    memberStatus: v.memberStatus, accountStatus: v.accountStatus, createdAt: v.createdAt, updatedAt: v.updatedAt };
}
export function decodeRole(value: unknown, tenantId: string): RoleGrant {
  const v = value as RoleGrant;
  require(v && isId(v.grantId) && isId(v.roleId) && text(v.roleCode, 64) && text(v.roleName, 255)
    && (v.roleScope === 'PLATFORM' ? v.roleTenantId === null : v.roleScope === 'TENANT' && v.roleTenantId === tenantId)
    && oneOf(v.roleStatus, ['ACTIVE', 'INACTIVE']) && oneOf(v.grantStatus, ['ACTIVE', 'REVOKED', 'EXPIRED']) && instant(v.effectiveFrom)
    && (v.effectiveTo === null || instant(v.effectiveTo) && instantMicros(v.effectiveTo) > instantMicros(v.effectiveFrom)));
  return { grantId: v.grantId, roleId: v.roleId, roleCode: v.roleCode, roleName: v.roleName, roleScope: v.roleScope, roleTenantId: v.roleTenantId,
    roleStatus: v.roleStatus, grantStatus: v.grantStatus, effectiveFrom: v.effectiveFrom, effectiveTo: v.effectiveTo, effective: v.effective, reasons: reasons(v.reasons, v.effective) };
}
export function decodePermission(value: unknown): MemberPermission {
  const v = value as MemberPermission;
  require(v && isId(v.permissionId) && text(v.permissionCode, 128) && text(v.resourceType, 64) && text(v.actionCode, 64)
    && oneOf(v.permissionStatus, ['ACTIVE', 'INACTIVE']) && Number.isSafeInteger(v.sourceCount) && v.sourceCount > 0
    && Number.isSafeInteger(v.effectiveSourceCount) && v.effectiveSourceCount >= 0 && v.effectiveSourceCount <= v.sourceCount
    && v.effective === (v.effectiveSourceCount > 0));
  return { permissionId: v.permissionId, permissionCode: v.permissionCode, resourceType: v.resourceType, actionCode: v.actionCode,
    permissionStatus: v.permissionStatus, sourceCount: v.sourceCount, effectiveSourceCount: v.effectiveSourceCount, effective: v.effective, reasons: reasons(v.reasons, v.effective) };
}
export function decodeSource(value: unknown, tenantId: string): PermissionSource {
  const v = value as PermissionSource; const role = decodeRole(v, tenantId);
  require(isId(v.rolePermissionId) && oneOf(v.rolePermissionStatus, ['ACTIVE', 'REVOKED']));
  return { ...role, rolePermissionId: v.rolePermissionId, rolePermissionStatus: v.rolePermissionStatus };
}
export function decodePage<T>(value: unknown, decode: (v: unknown) => T, identity: (v: T) => string): PagingResponseBody<T> {
  const v = value as PagingResponseBody<unknown>;
  require(v && Array.isArray(v.items) && Number.isSafeInteger(v.total) && v.total >= 0 && Number.isInteger(v.page) && v.page >= 1 && v.page <= 100000 && [10, 20, 50, 100].includes(v.size));
  const items = v.items.map(decode); require(new Set(items.map(identity)).size === items.length);
  return { items, page: v.page, size: v.size, total: v.total };
}
export function decodeEvaluated<T>(value: unknown, decode: (v: unknown) => T, identity: (v: T) => string): EvaluatedPage<T> {
  const v = value as EvaluatedPage<unknown>; const page = decodePage(value, decode, identity); require(instant(v.evaluatedAt));
  return { ...page, evaluatedAt: v.evaluatedAt };
}
export function decodeMutation(value: unknown, tenant: Tenant, securityStatus: SecurityStatus): SecurityMutation {
  const v = value as SecurityMutation;
  require(v && v.tenantId === tenant.tenantId && v.securityStatus === securityStatus && version(v.version) && [tenant.version, tenant.version + 1].includes(v.version));
  return { tenantId: v.tenantId, securityStatus: v.securityStatus, version: v.version };
}
export function filters(values: Record<string, string | undefined>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).filter(([, v]) => v?.trim()).map(([k, v]) => [k, v!.trim()]));
}
export function replaceQuery(search: string, updates: Record<string, string | null>): string {
  const next = new URLSearchParams(search);
  for (const [key, value] of Object.entries(updates)) if (value === null || value === '') next.delete(key); else next.set(key, value);
  return `?${next}`;
}
