import { ApiError } from '../api/session.ts';
import type { GatewayRouteSummaryResponseBody, PagingResponseBody } from '../api/types.ts';
export interface Credential {
  credentialId: string; tenantId: string; clientId: string; clientName: string; keyPrefix: string;
  storedStatus: string; status: 'ACTIVE' | 'EXPIRED' | 'REVOKED'; tenantOwnerStatus: string; tenantSecurityStatus: string;
  expiresTime: string | null; version: number; createTime: string; updateTime: string;
}
export interface CredentialDetail extends Credential { rotatedFromId: string | null }
export interface CredentialGrant {
  scopeId: string; scopeCode: string; displayName: string | null; resourcePattern: string; actionCode: string;
  storedStatus: string; status: 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'PENDING'; effectiveFrom: string; effectiveTo: string | null;
  version: number; createTime: string; updateTime: string;
}
export interface Scope { apiScopeId: string; scopeCode: string; displayName: string; description: string | null; version: number }
export interface ScopeDetail extends Scope { createTime: string; updateTime: string }
export interface ScopeMutation { apiScopeId: string; scopeCode: string; version: number }
export interface RevokeMutation { credentialId: string; status: 'REVOKED'; version: number }
export type ReferenceKind = 'NONE' | 'ROUTE' | 'CREDENTIAL';
export interface ScopePage { scope: ScopeDetail; references: null | { kind: 'ROUTE'; page: PagingResponseBody<GatewayRouteSummaryResponseBody> } | { kind: 'CREDENTIAL'; page: PagingResponseBody<Credential> } }
export interface CredentialSearch { clientMode?: string; client?: string; credentialMode?: string; credential?: string; status?: string; scopeCode?: string }
export const scopeCodePattern = /^[a-z][a-z0-9]*(?:[.:-][a-z0-9]+)*$/;
/** Credential / grant statuses; labels are `gateway.enum.credentialStatus.<status>`. */
export const credentialStatuses = ['ACTIVE', 'EXPIRED', 'REVOKED'] as const;
export function credentialFilters(values: CredentialSearch): Record<string, string> {
  const result: Record<string, string> = {};
  if (values.client?.trim()) result[values.clientMode === 'clientId' ? 'clientId' : 'clientName'] = values.client.trim();
  if (values.credential?.trim()) result[values.credentialMode === 'credentialId' ? 'credentialId' : 'keyPrefix'] = values.credential.trim();
  for (const key of ['status', 'scopeCode'] as const) if (values[key]?.trim()) result[key] = values[key]!.trim();
  return result;
}
export function scopeReferenceQuery(kind: ReferenceKind, page: number, size: number) {
  return new URLSearchParams({ referenceKind: kind, ...(kind === 'NONE' ? {} : { page: String(page), size: String(size) }) });
}
const id = (v: unknown) => typeof v === 'string' && /^[1-9][0-9]{0,18}$/.test(v) && BigInt(v) <= 9223372036854775807n;
const version = (v: unknown) => Number.isInteger(v) && Number(v) > 0 && Number(v) <= 2147483647;
export function scopeMutation(value: unknown, expectedCode: string): ScopeMutation {
  const v = value as ScopeMutation;
  if (!v || !id(v.apiScopeId) || v.scopeCode !== expectedCode || !version(v.version)) throw new ApiError(502, 'INVALID_RESPONSE');
  return { apiScopeId: v.apiScopeId, scopeCode: v.scopeCode, version: v.version };
}
export function revokeMutation(value: unknown, expectedId: string, expectedVersion: number): RevokeMutation {
  const v = value as RevokeMutation;
  if (!v || v.credentialId !== expectedId || v.status !== 'REVOKED' || !version(v.version)
    || ![expectedVersion, expectedVersion + 1].includes(v.version)) throw new ApiError(502, 'INVALID_RESPONSE');
  return { credentialId: v.credentialId, status: v.status, version: v.version };
}
export function validateCredential(value: CredentialDetail, expectedId: string): CredentialDetail {
  if (!value || value.credentialId !== expectedId || !id(value.tenantId) || !id(value.clientId)
    || typeof value.keyPrefix !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9-]{7,31}$/.test(value.keyPrefix)
    || !['ACTIVE', 'EXPIRED', 'REVOKED'].includes(value.status) || !version(value.version)
    || value.rotatedFromId !== null && !id(value.rotatedFromId)) throw new ApiError(502, 'INVALID_RESPONSE');
  return value;
}
export function validateScopePage(value: ScopePage, code: string, kind: ReferenceKind = 'NONE'): ScopePage {
  if (!value?.scope || value.scope.scopeCode !== code || !id(value.scope.apiScopeId) || !version(value.scope.version)
    || typeof value.scope.displayName !== 'string' || (kind === 'NONE' ? value.references !== null : value.references?.kind !== kind))
    throw new ApiError(502, 'INVALID_RESPONSE');
  return value;
}
