import { ApiError } from '../api/session.ts';
import type { GatewayConfigurationKind, GatewayConfigurationStatus,
  GatewayRouteResponseBody, GatewayTargetResponseBody } from '../api/types.ts';
import { translator } from '../i18n/index.ts';

const t = translator('gateway');

export interface GatewayStatusEntity {
  kind: GatewayConfigurationKind;
  code: string;
  status: GatewayConfigurationStatus;
  version: number;
}

export interface GatewayStatusCommand {
  path: string;
  method: 'PATCH';
  payload: string;
  key: string;
}

/** Success notice after a status change; read at call time so it follows the current language. */
export const gatewayStatusSavedMessage = () => t('statusModal.saved');

export type GatewayStatusFailureAction = 'STALE' | 'CONFLICT' | 'AUTHORIZATION_CHANGED' | 'RETRY';

export function gatewayRouteBreakGlassWarning(entity: GatewayStatusEntity): string | null {
  if (entity.kind !== 'route' || entity.status !== 'ACTIVE' || !entity.code.startsWith('operations-gateway-')) {
    return null;
  }
  if (entity.code === 'operations-gateway-route-status') {
    return t('statusModal.routeStatusRouteWarning');
  }
  return t('statusModal.managementRouteWarning');
}

export function gatewayStatusFailureAction(error: unknown): GatewayStatusFailureAction {
  if (!(error instanceof ApiError)) return 'RETRY';
  if (error.status === 404) return 'STALE';
  if (error.status === 409) return 'CONFLICT';
  if (error.status === 403) return 'AUTHORIZATION_CHANGED';
  return 'RETRY';
}

export function isExpectedGatewayStatusResponse(entity: GatewayStatusEntity, responseCode: unknown,
  status: unknown, version: unknown): boolean {
  const expectedStatus = entity.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  return responseCode === entity.code && status === expectedStatus
    && Number.isInteger(version) && version === entity.version + 1;
}

/** A manual retry reuses its key only while the resource, version, desired status and reason stay identical. */
export function gatewayStatusCommand(entity: GatewayStatusEntity, reason: string,
  previous: GatewayStatusCommand | null): GatewayStatusCommand {
  const collection = entity.kind === 'target' ? 'target-services' : 'routes';
  const path = `/gateway-admin/v1/${collection}/${encodeURIComponent(entity.code)}/status`;
  const payload = JSON.stringify({
    status: entity.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
    expectedVersion: entity.version,
    reason: reason.trim(),
  });
  const key = previous?.path === path && previous.method === 'PATCH' && previous.payload === payload
    ? previous.key : crypto.randomUUID();
  return { path, method: 'PATCH', payload, key };
}

export function gatewayFilters(values: Record<string, unknown>): Readonly<Record<string, string>> {
  const result: Record<string, string> = {};
  for (const [key, value] of Object.entries(values)) {
    if (typeof value === 'string' && value.trim()) result[key] = value.trim();
  }
  return result;
}

function requiredText(value: unknown) { return typeof value === 'string' && value.trim().length > 0; }
function validStatus(value: unknown): value is GatewayConfigurationStatus { return value === 'ACTIVE' || value === 'DISABLED'; }

export function validateGatewayTarget(value: GatewayTargetResponseBody, code: string) {
  if (!value || value.targetServiceCode !== code || !requiredText(value.discoveryServiceName)
    || !requiredText(value.channel) || !validStatus(value.status)
    || !Number.isInteger(value.version) || value.version <= 0 || !requiredText(value.createdBy)
    || !requiredText(value.updatedBy) || !requiredText(value.createTime) || !requiredText(value.updateTime)) {
    throw new ApiError(502, 'INVALID_RESPONSE');
  }
}

export function validateGatewayRoute(value: GatewayRouteResponseBody, code: string) {
  if (!value || value.routeCode !== code || !requiredText(value.channel) || !requiredText(value.httpMethod)
    || !requiredText(value.externalPathTemplate) || !requiredText(value.downstreamPathTemplate)
    || !requiredText(value.targetServiceCode) || !validStatus(value.targetStatus)
    || !requiredText(value.securityProfileCode) || !requiredText(value.authorizationMode)
    || !validStatus(value.status) || !Number.isInteger(value.version) || value.version <= 0
    || !Array.isArray(value.headers) || !Array.isArray(value.queryParameterNames) || !Array.isArray(value.guardRules)
    || !requiredText(value.createdBy) || !requiredText(value.updatedBy)
    || !requiredText(value.createTime) || !requiredText(value.updateTime)) {
    throw new ApiError(502, 'INVALID_RESPONSE');
  }
}
