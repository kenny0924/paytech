import type { GatewayGuardRuleResponseBody } from '../api/types.ts';
import type { GatewayProfileCapability, ManagedRoute } from './types.ts';

export function initialGuards(profile: GatewayProfileCapability): GatewayGuardRuleResponseBody[] {
  return profile.requiredGuardDimensions.flatMap(dimension => [
    { ruleType: 'RATE_LIMIT', dimension, capacity: 10, refillTokens: 10, refillPeriodMillis: 1000, concurrencyLimit: null },
    { ruleType: 'CONCURRENCY', dimension, capacity: null, refillTokens: null, refillPeriodMillis: null, concurrencyLimit: 5 },
  ]);
}
/** 编辑只发送可写合同；详情中的状态、审计字段和派生权限编码不能回传。 */
export function routeWrite(values: Record<string, unknown>, detail: ManagedRoute | null): Record<string, unknown> {
  const fields = ['httpMethod', 'externalPathTemplate', 'downstreamPathTemplate', 'targetServiceCode', 'securityProfileCode',
    'authorizationMode', 'requestCookieMode', 'responseCookieMode', 'bodyLimitBytes', 'bodyReceiveTimeoutMillis', 'responseTimeoutMillis',
    'headers', 'queryParameterNames', 'guardRules', 'reason'];
  const body = Object.fromEntries(fields.map(key => [key, values[key]]));
  const mode = values.authorizationMode;
  return { ...body, permissionId: mode === 'HUMAN_PERMISSION' ? values.permissionId || null : null,
    requiredScopeCode: mode === 'API_SCOPE' ? values.requiredScopeCode || null : null,
    ...(detail ? { expectedVersion: detail.version } : { routeCode: values.routeCode, channel: values.channel }) };
}
