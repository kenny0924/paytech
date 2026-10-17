import type { GatewayRouteResponseBody, GatewayTargetResponseBody, PagingResponseBody } from '../api/types.ts';

export interface ManagementProtection { editableFields: string[]; statusChangeAllowed: boolean; reason: string | null }
export interface ConfigurationMutation { objectType: string; objectId: string; objectCode: string; status: string; version: number }
export interface ManagedResource {
  resourceId: string; resourceCode: string; channel: string; resourceType: 'MENU' | 'BUTTON'; parentResourceId: string | null;
  displayName: string; frontendRoute: string | null; componentKey: string | null; permissionId: string; permissionCode: string;
  permissionStatus: 'ACTIVE' | 'INACTIVE'; sortOrder: number; status: 'ACTIVE' | 'DISABLED'; version: number;
  managementProtection: ManagementProtection; supported: boolean; createTime: string; updateTime: string;
}
export interface ResourceCapabilities {
  components: { componentKey: string; displayName: string; frontendRoute: string; actions: { resourceCode: string; displayName: string }[] }[];
  resourceTypes: string[]; maxDepth: number; maxResources: number;
}
export interface PermissionSummary {
  permissionId: string; permissionCode: string; resourceType: string; actionCode: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'; status: 'ACTIVE' | 'INACTIVE'; version: number;
}
export interface PermissionDetail extends PermissionSummary {
  createTime: string; updateTime: string; managementProtection: ManagementProtection;
  referenceCounts: { resource: number; route: number; role: number };
}
export type PermissionOption = Omit<PermissionSummary, 'status' | 'version'>;
export interface ResourceReference { resourceId: string; resourceCode: string; displayName: string; channel: string; resourceType: string; status: string }
export interface RouteReference { routeCode: string; channel: string; httpMethod: string; externalPathTemplate: string; targetServiceCode: string; status: string }
export interface RoleReference { roleId: string; roleCode: string; roleName: string; roleStatus: string; grantStatus: string; scopeKey: string }
export type PermissionReference = ResourceReference | RouteReference | RoleReference;
export interface GatewayProfileCapability {
  securityProfileCode: string; authorizationModes: string[]; requestCookieModes: string[]; responseCookieModes: string[]; idempotencyModes: string[];
  allowedGuardDimensions: string[]; requiredGuardDimensions: string[];
}
export interface GatewayCapabilities {
  channels: { channel: string; profiles: GatewayProfileCapability[] }[]; httpMethods: string[];
  forbiddenHeaderNames: string[]; headerUses: string[];
  limits: { maxBodyLimitBytes: number; maxBodyReceiveTimeoutMillis: number; maxResponseTimeoutMillis: number;
    maxRateCapacity: number; maxRateRefillTokens: number; maxRateRefillPeriodMillis: number; maxConcurrencyLimit: number;
    maxTargets: number; maxRoutes: number; maxCorsHeaders: number; maxForwardedHeaders: number; maxQueryParameters: number; maxGuardRules: number };
}
export type ManagedTarget = GatewayTargetResponseBody & { managementProtection: ManagementProtection };
export type ManagedRoute = GatewayRouteResponseBody & { managementProtection: ManagementProtection };
export type { PagingResponseBody };
