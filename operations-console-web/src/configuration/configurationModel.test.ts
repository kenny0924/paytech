import assert from 'node:assert/strict';
import test from 'node:test';
import { configurationCommand, descendants, visibleResourceIds } from './configurationModel.ts';
import { defaultIdempotencyMode, idempotencyOptions, initialGuards, routeWrite } from './gatewayEditorModel.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import type { ManagedResource, ManagedRoute } from './types.ts';
const row = (id: string, parent: string | null, name: string, status = 'ACTIVE') => ({ resourceId: id, parentResourceId: parent, displayName: name, resourceCode: name, status }) as ManagedResource;

test('资源搜索保留父链，移动父级选项排除全部后代', () => {
  const tree = [row('1', null, '系统'), row('2', '1', '页面'), row('3', '2', '导出', 'DISABLED'), row('4', null, '其他')];
  assert.deepEqual([...visibleResourceIds(tree, '导出', 'DISABLED')].sort(), ['1', '2', '3']);
  assert.deepEqual([...descendants(tree, '1')].sort(), ['1', '2', '3']);
});
test('人工重试保留命令 key，草稿或资源改变后才换 key', () => {
  const first = configurationCommand('/permissions/P1', 'PUT', { expectedVersion: 1, riskLevel: 'HIGH' });
  assert.equal(configurationCommand(first.path, first.method, JSON.parse(first.body), first).key, first.key);
  assert.notEqual(configurationCommand('/permissions/P2', first.method, JSON.parse(first.body), first).key, first.key);
  assert.notEqual(configurationCommand(first.path, first.method, { expectedVersion: 2, riskLevel: 'HIGH' }, first).key, first.key);
});
test('Route 写入排除只读字段，授权变更清理无关绑定，子集合完整发送', () => {
  const value = routeWrite({ routeCode: 'new-route', channel: 'CLIENT_API', authorizationMode: 'API_SCOPE', permissionId: '123', requiredScopeCode: 'orders.read', headers: [], queryParameterNames: [], guardRules: [], status: 'ACTIVE', version: 99 }, null);
  assert.equal(value.permissionId, null); assert.equal(value.requiredScopeCode, 'orders.read');
  assert.equal('status' in value, false); assert.equal('version' in value, false); assert.deepEqual(value.headers, []);
  const updated = routeWrite({ routeCode: 'injected', channel: 'CLIENT', authorizationMode: 'HUMAN_PERMISSION', permissionId: '123', requiredScopeCode: 'old' }, { version: 4 } as ManagedRoute);
  assert.equal(updated.expectedVersion, 4); assert.equal('routeCode' in updated, false); assert.equal('channel' in updated, false); assert.equal(updated.requiredScopeCode, null);
});
test('Route 请求幂等随写入发送，选项与默认值来自 Profile 能力', () => {
  const created = routeWrite({ routeCode: 'new-route', channel: 'CLIENT_API', authorizationMode: 'API_SCOPE', idempotencyMode: 'REQUIRED' }, null);
  assert.equal(created.idempotencyMode, 'REQUIRED');
  const updated = routeWrite({ authorizationMode: 'API_SCOPE', idempotencyMode: 'NONE' }, { version: 2 } as ManagedRoute);
  assert.equal(updated.idempotencyMode, 'NONE');
  const profile = { securityProfileCode: 'CLIENT_API_CREDENTIAL', authorizationModes: [], requestCookieModes: [], responseCookieModes: [], idempotencyModes: ['NONE', 'REQUIRED'], allowedGuardDimensions: [], requiredGuardDimensions: [] };
  assert.equal(defaultIdempotencyMode(profile), 'NONE');
  assert.deepEqual(idempotencyOptions(profile.idempotencyModes), [{ value: 'NONE', label: '不启用' }, { value: 'REQUIRED', label: '必须' }]);
  assert.deepEqual(idempotencyOptions(['NONE'], 'REQUIRED'), [{ value: 'NONE', label: '不启用' }, { value: 'REQUIRED', label: '必须' }]);
  assert.deepEqual(idempotencyOptions(undefined, 'NONE'), [{ value: 'NONE', label: '不启用' }]);
});
test('Guard 默认项只来自服务端必需维度，数值形状保持 Rate/Concurrency 互斥', () => {
  const values = initialGuards({ securityProfileCode: 'OPERATIONS_JWT', authorizationModes: [], requestCookieModes: [], responseCookieModes: [], idempotencyModes: ['NONE'], allowedGuardDimensions: ['SHARED_ROUTE', 'CLIENT_IP'], requiredGuardDimensions: ['SHARED_ROUTE', 'CLIENT_IP'] });
  assert.equal(values.length, 4); assert.equal(values[0].concurrencyLimit, null); assert.equal(values[1].capacity, null); assert.equal(values[1].ruleType, 'CONCURRENCY');
});
test('已部署页面注册覆盖新菜单，组件及动作没有重复身份', () => {
  assert.equal(pageCapabilities.length, 65);
  assert.equal(new Set(pageCapabilities.map(p => p.componentKey)).size, 65);
  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_DATA_SOURCES')?.frontendRoute, '/data-query/data-sources');
  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_QUERY_TEMPLATES')?.frontendRoute, '/data-query/templates');
  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_EXTERNAL_STANDARD_APIS')?.frontendRoute, '/external-api/standard-apis');
  // 接入方页面已并入渠道详情（channel-connector 第二段），只保留旧地址跳转
  assert.equal(pageCapabilities.find(p => (p.componentKey as string) === 'OPERATIONS_EXTERNAL_INTEGRATIONS'), undefined);
  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_CONSOLE_RESOURCES')?.frontendRoute, '/console-resources');
  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_PERMISSIONS')?.frontendRoute, '/permissions');
  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_API_CREDENTIALS')?.frontendRoute, '/api-credentials');
  assert.equal(pageCapabilities.find(p => p.componentKey === 'OPERATIONS_API_SCOPES')?.frontendRoute, '/api-scopes');
  const actions = pageCapabilities.flatMap(p => p.actions.map(a => a.resourceCode)); assert.equal(new Set(actions).size, actions.length);
});
