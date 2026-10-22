import assert from 'node:assert/strict';
import test from 'node:test';
import type { ConsoleResource } from '../api/types.ts';
import { pageRoutePaths, buildMenuTree, menuPages, menuAncestors } from './menu.ts';

function resource(resourceId: string, values: Partial<ConsoleResource> = {}): ConsoleResource {
  return { resourceId, resourceCode: resourceId, resourceType: 'MENU', parentResourceId: null,
    displayName: resourceId, frontendRoute: null, componentKey: null, sortOrder: 0, ...values };
}
const system = resource('system', { displayName: '系统管理' });
const accounts = resource('accounts', { parentResourceId: 'system', displayName: '人员管理',
  frontendRoute: '/accounts', componentKey: 'OPERATIONS_ACCOUNTS', sortOrder: 10 });
const roles = resource('roles', { parentResourceId: 'system', displayName: '角色管理',
  frontendRoute: '/roles', componentKey: 'OPERATIONS_ROLES', sortOrder: 20 });
const audit = resource('audit', { parentResourceId: 'system', displayName: '审计日志',
  frontendRoute: '/audit', componentKey: 'OPERATIONS_AUDIT', sortOrder: 30 });
const gatewayConfiguration = resource('gateway-configuration', { parentResourceId: 'system', displayName: 'API 路由管理',
  frontendRoute: '/gateway-configuration', componentKey: 'OPERATIONS_GATEWAY_CONFIGURATION', sortOrder: 40 });

test('系统管理按同级顺序展示已部署页面，按钮不进入菜单', () => {
  const tree = buildMenuTree([gatewayConfiguration, audit, roles, system, accounts,
    resource('create', { resourceType: 'BUTTON', parentResourceId: 'accounts' })]);
  assert.equal(tree.length, 1);
  assert.equal(tree[0].displayName, '系统管理');
  assert.deepEqual(tree[0].children.map(menu => menu.displayName), ['人员管理', '角色管理', '审计日志', 'API 路由管理']);
  assert.deepEqual(menuPages(tree).map(menu => menu.frontendRoute), ['/accounts', '/roles', '/audit', '/gateway-configuration']);
  assert.deepEqual(menuAncestors(tree, '/roles'), ['system']);
});

test('无权限的页面不补回，目录没有可访问子页面时隐藏', () => {
  assert.deepEqual(menuPages(buildMenuTree([system, audit])).map(menu => menu.frontendRoute), ['/audit']);
  assert.deepEqual(buildMenuTree([system]), []);
  assert.deepEqual(buildMenuTree([accounts]), []);
});

test('未知组件或不匹配的路径不会注册页面，也不提升其子菜单', () => {
  const unknown = resource('unknown', { parentResourceId: 'system', componentKey: 'REMOTE', frontendRoute: '/remote' });
  assert.deepEqual(buildMenuTree([system, unknown, { ...accounts, parentResourceId: 'unknown' }]), []);
  assert.deepEqual(buildMenuTree([system, { ...accounts, frontendRoute: 'https://example.test/' }]), []);
});

test('多级目录可查找当前页面的祖先，环形和缺失父节点不显示', () => {
  const subgroup = resource('subgroup', { parentResourceId: 'system' });
  const tree = buildMenuTree([system, subgroup, { ...roles, parentResourceId: 'subgroup' }]);
  assert.deepEqual(menuAncestors(tree, '/roles'), ['system', 'subgroup']);
  assert.deepEqual(menuAncestors(tree, '/missing'), []);
  assert.deepEqual(buildMenuTree([resource('a', { parentResourceId: 'b' }),
    resource('b', { parentResourceId: 'a' }), { ...accounts, parentResourceId: 'a' }]), []);
});

test('风控目录只注册准确的规则配置组件和路径，撤销 READ 后不保留页面', () => {
  const parent = resource('risk', { resourceCode: 'OPERATIONS_RISK_MANAGEMENT', displayName: '风控管理' });
  const page = resource('risk-page', { parentResourceId: 'risk', resourceCode: 'OPERATIONS_RISK_RULE_CONFIGURATION',
    displayName: '规则配置', componentKey: 'OPERATIONS_RISK_RULE_CONFIGURATION', frontendRoute: '/risk/rule-configuration' });
  const write = resource('write', { parentResourceId: 'risk-page', resourceType: 'BUTTON', resourceCode: 'OPERATIONS_RISK_RULE_SET_SAVE' });
  assert.equal(buildMenuTree([parent, page, write])[0]?.displayName, '风控管理');
  assert.deepEqual(menuPages(buildMenuTree([parent, page, write])).map(item => item.frontendRoute), ['/risk/rule-configuration']);
  assert.deepEqual(menuPages(buildMenuTree([parent, page])), menuPages(buildMenuTree([parent, page, write])));
  assert.deepEqual(menuPages(buildMenuTree([parent, write])), []);
  assert.deepEqual(buildMenuTree([parent, { ...page, componentKey: 'OPERATIONS_RISK_UNKNOWN' }]), []);
  assert.deepEqual(buildMenuTree([parent, { ...page, frontendRoute: '/risk/rule-configurations' }]), []);
});

// 租户详情需要独立路径；风控等既有菜单继续严格匹配原有根路径。
test('tenant page owns only its three approved routes', () => {
  const menu = { frontendRoute: '/tenant-access', componentKey: 'OPERATIONS_TENANT_ACCESS' } as ConsoleResource;
  assert.deepEqual(pageRoutePaths(menu), ['/tenant-access', '/tenant-access/:tenantId', '/tenant-access/:tenantId/members/:memberId']);
  assert.deepEqual(pageRoutePaths({ ...menu, frontendRoute: '/risk/rule-configuration', componentKey: 'OPERATIONS_RISK_RULE_CONFIGURATION' }), ['/risk/rule-configuration']);
  assert.deepEqual(pageRoutePaths({ ...menu, frontendRoute: '/external-api/standard-apis', componentKey: 'OPERATIONS_EXTERNAL_STANDARD_APIS' }),
    ['/external-api/standard-apis', '/external-api/standard-apis/:standardApiCode']);
  assert.deepEqual(pageRoutePaths({ ...menu, frontendRoute: '/external-api/integrations', componentKey: 'OPERATIONS_EXTERNAL_INTEGRATIONS' }),
    ['/external-api/integrations', '/external-api/integrations/:integrationCode', '/external-api/integrations/:integrationCode/:section/:childCode']);
});

test('数据查询：模板页登记编辑器子路由（:templateId 含 new），数据源页只有根路径', () => {
  const menu = { frontendRoute: '/data-query/templates', componentKey: 'OPERATIONS_QUERY_TEMPLATES' } as ConsoleResource;
  assert.deepEqual(pageRoutePaths(menu), ['/data-query/templates', '/data-query/templates/:templateId']);
  assert.deepEqual(pageRoutePaths({ ...menu, frontendRoute: '/data-query/data-sources', componentKey: 'OPERATIONS_DATA_SOURCES' }), ['/data-query/data-sources']);
});
