import type { GatewayGuardRuleResponseBody } from '../api/types.ts';
import { enumLabel, translator } from '../i18n/index.ts';
import type { GatewayProfileCapability, ManagedRoute } from './types.ts';

const t = translator('gateway');

export function initialGuards(profile: GatewayProfileCapability): GatewayGuardRuleResponseBody[] {
  return profile.requiredGuardDimensions.flatMap(dimension => [
    { ruleType: 'RATE_LIMIT', dimension, capacity: 10, refillTokens: 10, refillPeriodMillis: 1000, concurrencyLimit: null },
    { ruleType: 'CONCURRENCY', dimension, capacity: null, refillTokens: null, refillPeriodMillis: null, concurrencyLimit: 5 },
  ]);
}
/** 请求幂等选项来自 Profile 能力；当前值不在能力内时仍保留，便于详情原样回显。显示名为 `gateway.enum.idempotencyMode.<值>`。 */
export function idempotencyOptions(modes: readonly string[] = [], current?: string | null) {
  const values = current && !modes.includes(current) ? [...modes, current] : [...modes];
  return values.map(value => ({ value, label: enumLabel(t, 'enum.idempotencyMode', value) }));
}
export function defaultIdempotencyMode(profile: GatewayProfileCapability): string {
  return profile.idempotencyModes.includes('NONE') ? 'NONE' : profile.idempotencyModes[0];
}
/** 编辑只发送可写合同；详情中的状态、审计字段和派生权限编码不能回传。 */
export function routeWrite(values: Record<string, unknown>, detail: ManagedRoute | null): Record<string, unknown> {
  const fields = ['httpMethod', 'externalPathTemplate', 'downstreamPathTemplate', 'targetServiceCode', 'securityProfileCode',
    'authorizationMode', 'requestCookieMode', 'responseCookieMode', 'idempotencyMode', 'bodyLimitBytes', 'bodyReceiveTimeoutMillis', 'responseTimeoutMillis',
    'headers', 'queryParameterNames', 'guardRules', 'reason'];
  const body = Object.fromEntries(fields.map(key => [key, values[key]]));
  const mode = values.authorizationMode;
  return { ...body, permissionId: mode === 'HUMAN_PERMISSION' ? values.permissionId || null : null,
    requiredScopeCode: mode === 'API_SCOPE' ? values.requiredScopeCode || null : null,
    ...(detail ? { expectedVersion: detail.version } : { routeCode: values.routeCode, channel: values.channel }) };
}
