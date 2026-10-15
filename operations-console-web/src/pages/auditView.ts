import { ApiError } from '../api/session.ts';
import type { AuditDetailResponseBody } from '../api/types.ts';
import { resolveTimeRange } from '../components/timeRange.ts';
import type { TimeRangeValue } from '../components/timeRange.ts';

export interface AuditFilterValues {
  timeRange?: TimeRangeValue; actorId?: string;
  actionCode?: string; result?: string; requestId?: string; resourceType?: string; resourceId?: string;
}
export const auditActions: Record<string, string> = {
  CHANGE_TENANT_SECURITY_STATUS: '变更租户安全状态',
  OPERATIONS_NOTIFICATION_TEMPLATE_CREATE: '新建通知模板', OPERATIONS_NOTIFICATION_TEMPLATE_STATUS_CHANGE: '启停通知模板',
  OPERATIONS_NOTIFICATION_CONTENT_SAVE: '保存通知内容', OPERATIONS_NOTIFICATION_ROUTE_SAVE: '保存通知路由',
  OPERATIONS_NOTIFICATION_ROUTE_STATUS_CHANGE: '启停通知路由', OPERATIONS_NOTIFICATION_PROVIDER_STATUS_CHANGE: '启停发送账号',
  OPERATIONS_NOTIFICATION_PROVIDER_CREATE: '新建发送账号', OPERATIONS_NOTIFICATION_PROVIDER_UPDATE: '修改发送账号',
  OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL_REPLACE: '更换发送账号密钥', OPERATIONS_NOTIFICATION_PROVIDER_TEST_SEND: '发送账号测试发送',
  OPERATIONS_API_CREDENTIAL_REVOKE: '紧急撤销凭证', OPERATIONS_API_SCOPE_CREATE: '新增 API Scope', OPERATIONS_API_SCOPE_UPDATE: '编辑 API Scope',
  OPERATIONS_ACCOUNT_CREATE: '创建人员', OPERATIONS_ACCOUNT_DELETE: '删除人员', OPERATIONS_ACCOUNT_DISABLE: '停用人员',
  OPERATIONS_ACCOUNT_ENABLE: '启用人员', OPERATIONS_ACCOUNT_ROLES: '分配角色',
  OPERATIONS_ROLE_CREATE: '创建角色', OPERATIONS_ROLE_UPDATE: '更新角色', OPERATIONS_ROLE_DELETE: '删除角色',
  OPERATIONS_PASSWORD_CHANGE: '修改密码', OPERATIONS_LOGIN: '账号登录', OPERATIONS_LOGOUT: '退出登录',
  OPERATIONS_REFRESH: '刷新会话', OPERATIONS_AUTHORIZATION: '访问授权', OPERATIONS_BOOTSTRAP_COMPLETE: '创建首位管理员',
  OPERATIONS_ACCOUNT_LIST: '查询人员', OPERATIONS_AUDIT_LIST: '查询审计日志', OPERATIONS_AUDIT_DETAIL: '查看审计详情',
  OPERATIONS_NAVIGATION: '查询导航', OPERATIONS_ROLE_LIST: '查询角色', OPERATIONS_ROLE_DETAIL: '查看角色详情',
  OPERATIONS_ROLE_EMPLOYEE_COUNTS: '查询角色关联人数', OPERATIONS_ROLE_PERMISSIONS: '查询角色权限', OPERATIONS_ROLE_RESOURCES: '查询角色资源',
};
export const auditResults: Record<string, string> = { SUCCEEDED: '成功', DENIED: '拒绝', FAILED: '失败' };
export const auditResourceTypes: Record<string, string> = { ACCESS_TENANT: '访问租户', API_CREDENTIAL: 'API 凭证', API_SCOPE: 'API Scope', GATEWAY_ROUTE: 'API 路由', USER_ACCOUNT: '人员账号', AUTH_SESSION: '登录会话', NOTIFICATION_TEMPLATE: '通知模板', NOTIFICATION_CONTENT: '通知内容', NOTIFICATION_ROUTE: '通知路由', NOTIFICATION_PROVIDER: '发送账号', ROUTE: '接口权限', ROLE: '角色', AUDIT_EVENT: '审计记录' };

/** 时间范围在提交时按浏览器本地时区解析，传输时转换 UTC。 */
export function auditFilters(values: AuditFilterValues, now = new Date()): Record<string, string> {
  const filters: Record<string, string> = {};
  const range = resolveTimeRange(values.timeRange, now);
  if (values.timeRange?.preset === 'CUSTOM' && (!range.start || !range.end)) {
    throw new Error('请选择完整的开始和结束时间');
  }
  for (const [key, value] of [['occurredFrom', range.start], ['occurredTo', range.end]] as const) {
    if (!value) continue;
    const instant = new Date(value);
    if (!Number.isFinite(instant.getTime())) throw new Error('请输入有效时间');
    filters[key] = instant.toISOString();
  }
  if (filters.occurredFrom && filters.occurredTo && filters.occurredFrom >= filters.occurredTo) {
    throw new Error('结束时间必须晚于开始时间');
  }
  for (const key of ['actorId', 'actionCode', 'result', 'requestId', 'resourceType', 'resourceId'] as const) {
    const value = values[key]?.trim();
    if (value) filters[key] = value;
  }
  if (filters.resourceId && !filters.resourceType) throw new Error('请同时指定对象类型');
  return filters;
}

export function auditValue(value: string | null): string {
  return value === null ? '已隐藏' : value === '' ? '（空字符串）' : value;
}

/** 不推导或补全历史属性，仅检查详情身份和可安全展示的集合形状。 */
export function validateAuditDetail(detail: AuditDetailResponseBody, eventId: string): void {
  const values = (items: unknown) => Array.isArray(items) && items.every(value => value === null || typeof value === 'string');
  if (!detail || detail.event?.auditEventId !== eventId || !Array.isArray(detail.changes) || !Array.isArray(detail.attributes)
    || detail.changes.some(change => !change || typeof change.key !== 'string' || typeof change.label !== 'string'
      || !values(change.before) || !values(change.after))
    || detail.attributes.some(attribute => !attribute || typeof attribute.key !== 'string' || typeof attribute.label !== 'string'
      || !values(attribute.values))) throw new ApiError(502, 'INVALID_RESPONSE');
}
