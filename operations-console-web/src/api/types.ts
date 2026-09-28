export interface TokenGrantResponseBody {
  accessToken: string; tokenType: 'Bearer'; expiresIn: number;
  refreshToken: string; refreshExpiresIn: number;
  passwordChangeRequired: boolean; bootstrapAdministratorRequired: boolean;
}
export interface SessionContextResponseBody {
  accountId: string; sessionId: string; accountType: string; emailMasked: string | null;
  accountStatus: string; tenantId: string | null; memberId: string | null;
  passwordChangeRequired: boolean; bootstrapAdministratorRequired: boolean;
  integrationMode: string;
}
export interface ConsoleResource {
  resourceId: string; resourceCode: string; resourceType: 'MENU' | 'BUTTON';
  parentResourceId: string | null; displayName: string; frontendRoute: string | null;
  componentKey: string | null; sortOrder: number;
}
export interface NavigationResponseBody { resources: ConsoleResource[] }
export interface Role { roleId: string; roleCode: string; roleName: string; systemRole: boolean }
export interface RoleListItem extends Role { version: number; employeeCount: number }
export interface PermissionNode {
  nodeId: string; displayName: string; permissionId: string | null; children: PermissionNode[];
}
export interface RolePermissionOptionsResponseBody { nodes: PermissionNode[] }
export interface RoleResponseBody extends Role { version: number; permissionIds: string[] }
export interface RoleDetailResponseBody extends RoleResponseBody { nodes: PermissionNode[] }
export interface CreateRoleRequestBody { roleCode: string; roleName: string; permissionIds: string[]; reason: string }
export interface UpdateRoleRequestBody { roleName: string; permissionIds: string[]; expectedVersion: number; reason: string }
export interface DeleteRoleRequestBody { expectedVersion: number; reason: string }
export interface DeleteAccountRequestBody { expectedVersion: number; expectedMemberVersion: number; reason: string }
export interface Account {
  accountId: string; emailMasked: string; status: string; passwordChangeRequired: boolean;
  lockedUntil: string | null; version: number; memberId: string; memberVersion: number; roles: Role[];
}
export interface PagingResponseBody<T> { items: T[]; page: number; size: number; total: number }
export interface AuditEvent {
  auditEventId: string; actorId: string; actionCode: string; resourceType: string;
  resourceId: string; result: string; requestId: string; occurredTime: string; reason: string;
}
export interface AuditChange { key: string; label: string; before: (string | null)[]; after: (string | null)[] }
export interface AuditAttribute { key: string; label: string; values: (string | null)[] }
export interface AuditDetailResponseBody { event: AuditEvent; changes: AuditChange[]; attributes: AuditAttribute[] }

export type GatewayConfigurationStatus = 'ACTIVE' | 'DISABLED';
export type GatewayConfigurationKind = 'target' | 'route';
export interface GatewayTargetSummaryResponseBody {
  targetServiceCode: string; discoveryServiceName: string; channel: string;
  status: GatewayConfigurationStatus; version: number;
}
export interface GatewayTargetResponseBody extends GatewayTargetSummaryResponseBody {
  createdBy: string; updatedBy: string; createTime: string; updateTime: string;
}
export interface GatewayRouteSummaryResponseBody {
  routeCode: string; channel: string; httpMethod: string; externalPathTemplate: string;
  targetServiceCode: string; targetStatus: GatewayConfigurationStatus;
  securityProfileCode: string; authorizationMode: string;
  status: GatewayConfigurationStatus; version: number;
}
export interface GatewayRouteHeaderResponseBody { headerName: string; headerUse: string }
export interface GatewayGuardRuleResponseBody {
  ruleType: string; dimension: string; capacity: number | null; refillTokens: number | null;
  refillPeriodMillis: number | null; concurrencyLimit: number | null;
}
export interface GatewayRouteResponseBody extends GatewayRouteSummaryResponseBody {
  downstreamPathTemplate: string; permissionId: string | null; permissionCode: string | null;
  requiredScopeCode: string | null; requestCookieMode: string; responseCookieMode: string;
  bodyLimitBytes: number; bodyReceiveTimeoutMillis: number; responseTimeoutMillis: number;
  headers: GatewayRouteHeaderResponseBody[]; queryParameterNames: string[]; guardRules: GatewayGuardRuleResponseBody[];
  createdBy: string; updatedBy: string; createTime: string; updateTime: string;
}
export interface GatewayTargetStatusResponseBody {
  targetServiceCode: string; status: GatewayConfigurationStatus; version: number;
}
export interface GatewayRouteStatusResponseBody {
  routeCode: string; status: GatewayConfigurationStatus; version: number;
}
