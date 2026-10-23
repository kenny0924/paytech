import { ApiError } from '../api/session.ts';
import type { AuditDetailResponseBody } from '../api/types.ts';
import { resolveTimeRange } from '../components/timeRange.ts';
import type { TimeRangeValue } from '../components/timeRange.ts';
import { translator } from '../i18n/index.ts';

const t = translator('access');

export interface AuditFilterValues {
  timeRange?: TimeRangeValue; actorId?: string;
  actionCode?: string; result?: string; requestId?: string; resourceType?: string; resourceId?: string;
}
/** Known action codes in display order; labels are `access.enum.auditAction.<code>`, unknown codes show as is. */
export const auditActionCodes = [
  'CHANGE_TENANT_SECURITY_STATUS', 'OPERATIONS_NOTIFICATION_TEMPLATE_CREATE', 'OPERATIONS_NOTIFICATION_TEMPLATE_STATUS_CHANGE',
  'OPERATIONS_NOTIFICATION_CONTENT_SAVE', 'OPERATIONS_NOTIFICATION_ROUTE_SAVE', 'OPERATIONS_NOTIFICATION_ROUTE_STATUS_CHANGE',
  'OPERATIONS_NOTIFICATION_PROVIDER_STATUS_CHANGE', 'OPERATIONS_NOTIFICATION_PROVIDER_CREATE',
  'OPERATIONS_NOTIFICATION_PROVIDER_UPDATE', 'OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL_REPLACE',
  'OPERATIONS_NOTIFICATION_PROVIDER_TEST_SEND', 'OPERATIONS_WEBHOOK_CLIENTS', 'OPERATIONS_WEBHOOK_ENDPOINTS',
  'OPERATIONS_WEBHOOK_ENDPOINT_CREATE', 'OPERATIONS_WEBHOOK_ENDPOINT_UPDATE', 'OPERATIONS_WEBHOOK_ENDPOINT_STATUS_CHANGE',
  'OPERATIONS_WEBHOOK_SECRET_ROTATE', 'OPERATIONS_WEBHOOK_ENDPOINT_TEST', 'OPERATIONS_WEBHOOK_DELIVERIES',
  'OPERATIONS_WEBHOOK_DELIVERY', 'OPERATIONS_WEBHOOK_DELIVERY_REPLAY', 'OPERATIONS_API_CREDENTIAL_REVOKE',
  'OPERATIONS_API_SCOPE_CREATE', 'OPERATIONS_API_SCOPE_UPDATE', 'OPERATIONS_ACCOUNT_CREATE', 'OPERATIONS_ACCOUNT_DELETE',
  'OPERATIONS_ACCOUNT_DISABLE', 'OPERATIONS_ACCOUNT_ENABLE', 'OPERATIONS_ACCOUNT_ROLES', 'OPERATIONS_ROLE_CREATE',
  'OPERATIONS_ROLE_UPDATE', 'OPERATIONS_ROLE_DELETE', 'OPERATIONS_PASSWORD_CHANGE', 'OPERATIONS_LOGIN', 'OPERATIONS_LOGOUT',
  'OPERATIONS_REFRESH', 'OPERATIONS_AUTHORIZATION', 'OPERATIONS_BOOTSTRAP_COMPLETE', 'OPERATIONS_ACCOUNT_LIST',
  'OPERATIONS_AUDIT_LIST', 'OPERATIONS_AUDIT_DETAIL', 'OPERATIONS_NAVIGATION', 'OPERATIONS_ROLE_LIST', 'OPERATIONS_ROLE_DETAIL',
  'OPERATIONS_ROLE_EMPLOYEE_COUNTS', 'OPERATIONS_ROLE_PERMISSIONS', 'OPERATIONS_ROLE_RESOURCES',
  'OPERATIONS_FILE_STORAGE_INSTANCES', 'OPERATIONS_FILE_STORAGE_INSTANCE_CREATE', 'OPERATIONS_FILE_STORAGE_INSTANCE_UPDATE',
  'OPERATIONS_FILE_STORAGE_CREDENTIAL_REPLACE', 'OPERATIONS_FILE_STORAGE_INSTANCE_STATUS',
  'OPERATIONS_FILE_STORAGE_BUCKET_REGISTER', 'OPERATIONS_FILE_STORAGE_BUCKET_UPDATE', 'OPERATIONS_FILE_STORAGE_BUCKET_CHECK',
  'OPERATIONS_FILE_STORAGE_BUCKET_STATUS', 'OPERATIONS_FILE_STORAGE_ROUTES', 'OPERATIONS_FILE_STORAGE_ROUTE_CREATE',
  'OPERATIONS_FILE_STORAGE_ROUTE_UPDATE', 'OPERATIONS_FILE_STORAGE_ROUTE_DELETE', 'OPERATIONS_FILE_SCAN_POLICIES',
  'OPERATIONS_FILE_SCAN_POLICY_CREATE', 'OPERATIONS_FILE_SCAN_POLICY_REVISION_CREATE',
  'OPERATIONS_FILE_SCAN_POLICY_REVISION_UPDATE', 'OPERATIONS_FILE_SCAN_POLICY_REVISION_DISCARD',
  'OPERATIONS_FILE_SCAN_POLICY_ACTIVATE', 'OPERATIONS_FILE_SCAN_POLICY_RETIRE', 'OPERATIONS_FILE_RECORDS',
  'FILE_DOWNLOAD_AUTHORIZE',
] as const;
/** Labels: `access.enum.auditResult.<code>`. */
export const auditResultCodes = [
  'SUCCEEDED', 'DENIED', 'FAILED',
] as const;
/** Labels: `access.enum.auditResourceType.<code>`. */
export const auditResourceTypeCodes = [
  'ACCESS_TENANT', 'API_CREDENTIAL', 'API_SCOPE', 'GATEWAY_ROUTE', 'USER_ACCOUNT', 'AUTH_SESSION', 'NOTIFICATION_TEMPLATE',
  'NOTIFICATION_CONTENT', 'NOTIFICATION_ROUTE', 'NOTIFICATION_PROVIDER', 'WEBHOOK_CLIENT', 'WEBHOOK_ENDPOINT',
  'WEBHOOK_DELIVERY', 'ROUTE', 'ROLE', 'AUDIT_EVENT', 'FILE_STORAGE_INSTANCE', 'FILE_STORAGE_BUCKET', 'FILE_STORAGE_ROUTE',
  'FILE_STORAGE_CATALOG', 'FILE_SCAN_POLICY', 'FILE',
] as const;

/** 时间范围在提交时按浏览器本地时区解析，传输时转换 UTC。 */
export function auditFilters(values: AuditFilterValues, now = new Date()): Record<string, string> {
  const filters: Record<string, string> = {};
  const range = resolveTimeRange(values.timeRange, now);
  if (values.timeRange?.preset === 'CUSTOM' && (!range.start || !range.end)) {
    throw new Error(t('audit.error.incompleteRange'));
  }
  for (const [key, value] of [['occurredFrom', range.start], ['occurredTo', range.end]] as const) {
    if (!value) continue;
    const instant = new Date(value);
    if (!Number.isFinite(instant.getTime())) throw new Error(t('audit.error.invalidTime'));
    filters[key] = instant.toISOString();
  }
  if (filters.occurredFrom && filters.occurredTo && filters.occurredFrom >= filters.occurredTo) {
    throw new Error(t('audit.error.endBeforeStart'));
  }
  for (const key of ['actorId', 'actionCode', 'result', 'requestId', 'resourceType', 'resourceId'] as const) {
    const value = values[key]?.trim();
    if (value) filters[key] = value;
  }
  if (filters.resourceId && !filters.resourceType) throw new Error(t('audit.error.resourceTypeRequired'));
  return filters;
}

export function auditValue(value: string | null): string {
  return value === null ? t('audit.value.hidden') : value === '' ? t('audit.value.emptyString') : value;
}

/** 不推导或补全历史属性，仅检查详情身份和可安全展示的集合形状。 */
export function validateAuditDetail(detail: AuditDetailResponseBody, eventId: string): void {
  const values = (items: unknown) => Array.isArray(items) && items.every(value => value === null || typeof value === 'string');
  if (!detail || detail.event?.auditEventId !== eventId || !Array.isArray(detail.changes) || !Array.isArray(detail.attributes)
    || detail.changes.some(change => !change || typeof change.key !== 'string'
      || !values(change.before) || !values(change.after))
    || detail.attributes.some(attribute => !attribute || typeof attribute.key !== 'string'
      || !values(attribute.values))) throw new ApiError(502, 'INVALID_RESPONSE');
}
