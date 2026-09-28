import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { gatewayFilters, gatewayRouteBreakGlassWarning, gatewayStatusCommand, gatewayStatusFailureAction,
  gatewayStatusSavedMessage, isExpectedGatewayStatusResponse } from './gatewayConfigurationModel.ts';

test('status command uses PATCH, the current version and a trimmed reason', () => {
  const command = gatewayStatusCommand({ kind: 'route', code: 'checkout route/one', status: 'ACTIVE', version: 7 },
    '  emergency maintenance  ', null);
  assert.equal(command.path, '/gateway-admin/v1/routes/checkout%20route%2Fone/status');
  assert.equal(command.method, 'PATCH');
  assert.deepEqual(JSON.parse(command.payload), {
    status: 'DISABLED', expectedVersion: 7, reason: 'emergency maintenance',
  });
});

test('manual retry keeps a key only for the exact same status command', () => {
  const entity = { kind: 'target' as const, code: 'operations-console-bff', status: 'DISABLED' as const, version: 3 };
  const first = gatewayStatusCommand(entity, 'restore service', null);
  assert.equal(gatewayStatusCommand(entity, '  restore service ', first).key, first.key);
  assert.notEqual(gatewayStatusCommand({ ...entity, version: 4 }, 'restore service', first).key, first.key);
  assert.notEqual(gatewayStatusCommand({ ...entity, status: 'ACTIVE' }, 'restore service', first).key, first.key);
  assert.notEqual(gatewayStatusCommand({ ...entity, code: 'other-service' }, 'restore service', first).key, first.key);
  assert.notEqual(gatewayStatusCommand(entity, 'different reason', first).key, first.key);
});

test('filters trim text and omit empty controls', () => {
  assert.deepEqual(gatewayFilters({ routeCode: '  route-one ', channel: '', status: undefined,
    targetServiceCode: ' target-one ' }), { routeCode: 'route-one', targetServiceCode: 'target-one' });
});

test('status outcome keeps success wording bounded and refreshes stale resources', () => {
  assert.equal(gatewayStatusSavedMessage, 'Platform 当前配置已保存；此页面不确认 Gateway 实例刷新状态。');
  assert.equal(gatewayStatusFailureAction(new ApiError(404, 'GATEWAY_CONFIGURATION_NOT_FOUND')), 'STALE');
  assert.equal(gatewayStatusFailureAction(new ApiError(409, 'CONCURRENT_REQUEST')), 'CONFLICT');
  assert.equal(gatewayStatusFailureAction(new ApiError(403, 'OPERATIONS_PERMISSION_DENIED')), 'AUTHORIZATION_CHANGED');
  assert.equal(gatewayStatusFailureAction(new Error('network')), 'RETRY');
});

test('status response must identify the resource and advance exactly one version', () => {
  const entity = { kind: 'target' as const, code: 'operations-console-bff', status: 'ACTIVE' as const, version: 3 };
  assert.equal(isExpectedGatewayStatusResponse(entity, 'operations-console-bff', 'DISABLED', 4), true);
  assert.equal(isExpectedGatewayStatusResponse(entity, 'operations-console-bff', 'DISABLED', 5), false);
  assert.equal(isExpectedGatewayStatusResponse(entity, 'another-service', 'DISABLED', 4), false);
  assert.equal(isExpectedGatewayStatusResponse(entity, 'operations-console-bff', 'ACTIVE', 4), false);
  assert.equal(isExpectedGatewayStatusResponse(entity, 'operations-console-bff', 'DISABLED', 4.5), false);
});

test('disabling Operations Gateway management routes warns about self lockout', () => {
  assert.match(gatewayRouteBreakGlassWarning({ kind: 'route', code: 'operations-gateway-route-status',
    status: 'ACTIVE', version: 2 }) ?? '', /无法再执行任何 Route 状态变更/);
  assert.match(gatewayRouteBreakGlassWarning({ kind: 'route', code: 'operations-gateway-routes',
    status: 'ACTIVE', version: 3 }) ?? '', /削弱或切断本页面/);
  assert.equal(gatewayRouteBreakGlassWarning({ kind: 'route', code: 'client-orders', status: 'ACTIVE', version: 1 }), null);
  assert.equal(gatewayRouteBreakGlassWarning({ kind: 'route', code: 'operations-gateway-route-status',
    status: 'DISABLED', version: 2 }), null);
});
