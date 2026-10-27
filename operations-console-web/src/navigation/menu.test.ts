import assert from 'node:assert/strict';
import test from 'node:test';
import type { ConsoleResource } from '../api/types.ts';
import { testT } from '../i18n/testing.ts';
import { pageRoutePaths, buildMenuTree, legacyRedirects, menuPages, menuAncestors, mergeMenus, resourceLabel } from './menu.ts';

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
    ['/external-api/integrations']);
});

test('工作流页面登记子路由：实例详情、流程编辑器与版本只读、动作详情；我的待办只有根路径', () => {
  const menu = (frontendRoute: string, componentKey: string) => ({ frontendRoute, componentKey }) as ConsoleResource;
  assert.deepEqual(pageRoutePaths(menu('/workflow/my-tasks', 'OPERATIONS_WORKFLOW_MY_TASKS')), ['/workflow/my-tasks']);
  assert.deepEqual(pageRoutePaths(menu('/workflow/instances', 'OPERATIONS_WORKFLOW_INSTANCES')), ['/workflow/instances', '/workflow/instances/:instanceId']);
  assert.deepEqual(pageRoutePaths(menu('/workflow/definitions', 'OPERATIONS_WORKFLOW_DEFINITIONS')),
    ['/workflow/definitions', '/workflow/definitions/:definitionId', '/workflow/definitions/:definitionId/versions/:versionNo']);
  assert.deepEqual(pageRoutePaths(menu('/workflow/action-templates', 'OPERATIONS_WORKFLOW_ACTION_TEMPLATES')), ['/workflow/action-templates', '/workflow/action-templates/:templateId']);
  const group = resource('workflow', { displayName: '工作流' });
  const tasks = resource('tasks', { parentResourceId: 'workflow', displayName: '我的待办', frontendRoute: '/workflow/my-tasks', componentKey: 'OPERATIONS_WORKFLOW_MY_TASKS', sortOrder: 10 });
  const definitions = resource('definitions', { parentResourceId: 'workflow', displayName: '流程定义', frontendRoute: '/workflow/definitions', componentKey: 'OPERATIONS_WORKFLOW_DEFINITIONS', sortOrder: 30 });
  assert.deepEqual(menuPages(buildMenuTree([group, definitions, tasks])).map(item => item.displayName), ['我的待办', '流程定义']);
});

test('我发起的：工作流菜单下独立页面 /workflow/started，只有根路径，排在我的待办之后', () => {
  assert.deepEqual(pageRoutePaths({ frontendRoute: '/workflow/started', componentKey: 'OPERATIONS_WORKFLOW_STARTED' } as ConsoleResource), ['/workflow/started']);
  const group = resource('workflow', { displayName: '工作流' });
  const tasks = resource('tasks', { parentResourceId: 'workflow', displayName: '我的待办', frontendRoute: '/workflow/my-tasks', componentKey: 'OPERATIONS_WORKFLOW_MY_TASKS', sortOrder: 10 });
  const started = resource('started', { parentResourceId: 'workflow', displayName: '我发起的', frontendRoute: '/workflow/started', componentKey: 'OPERATIONS_WORKFLOW_STARTED', sortOrder: 15 });
  const instances = resource('instances', { parentResourceId: 'workflow', displayName: '流程实例', frontendRoute: '/workflow/instances', componentKey: 'OPERATIONS_WORKFLOW_INSTANCES', sortOrder: 20 });
  assert.deepEqual(menuPages(buildMenuTree([group, instances, started, tasks])).map(item => item.frontendRoute), ['/workflow/my-tasks', '/workflow/started', '/workflow/instances']);
});

test('数据查询：模板页登记编辑器子路由（:templateId 含 new），数据源页只有根路径', () => {
  const menu = { frontendRoute: '/data-query/templates', componentKey: 'OPERATIONS_QUERY_TEMPLATES' } as ConsoleResource;
  assert.deepEqual(pageRoutePaths(menu), ['/data-query/templates', '/data-query/templates/:templateId']);
  assert.deepEqual(pageRoutePaths({ ...menu, frontendRoute: '/data-query/data-sources', componentKey: 'OPERATIONS_DATA_SOURCES' }), ['/data-query/data-sources']);
});

test('卡 BIN 库：平台配置下注册 /card-bin-library 单一路由，维护按钮不进入菜单', () => {
  const platform = resource('platform', { displayName: '平台配置' });
  const page = resource('card-bin', { parentResourceId: 'platform', resourceCode: 'OPERATIONS_CARD_BIN_LIBRARY', displayName: '卡 BIN 库',
    frontendRoute: '/card-bin-library', componentKey: 'OPERATIONS_CARD_BIN_LIBRARY', sortOrder: 90 });
  const manage = resource('card-bin-manage', { parentResourceId: 'card-bin', resourceType: 'BUTTON', resourceCode: 'OPERATIONS_CARD_BIN_LIBRARY_MANAGE' });
  const tree = buildMenuTree([platform, page, manage]);
  assert.deepEqual(menuPages(tree).map(item => item.frontendRoute), ['/card-bin-library']);
  assert.deepEqual(pageRoutePaths(page), ['/card-bin-library']);
  assert.deepEqual(buildMenuTree([platform, { ...page, frontendRoute: '/card-bin' }]), []);
});

test('menu, breadcrumb and title labels use nav.resource.<resourceCode>; a code without a key falls back to display_name', () => {
  const t = testT('nav');
  assert.equal(resourceLabel(t, { resourceCode: 'OPERATIONS_ACCOUNTS', displayName: '人员管理' }), 'Users');
  assert.equal(resourceLabel(t, { resourceCode: 'OPERATIONS_FINANCE_RECON', displayName: '对账' }), 'Reconciliation');
  assert.equal(resourceLabel(t, { resourceCode: 'OPERATIONS_NOT_YET_TRANSLATED', displayName: '新页面' }), '新页面');
});

test('币种页并入参考数据：两个菜单都有权限时只显示参考数据；只有币种权限时改写为参考数据（沿用位置）；旧地址跳到币种页签', () => {
  const platform = resource('platform', { resourceCode: 'OPERATIONS_PLATFORM_CONFIGURATION', displayName: '平台配置' });
  const currency = resource('currency', { parentResourceId: 'platform', resourceCode: 'OPERATIONS_CURRENCY_SPECS', displayName: '币种',
    frontendRoute: '/currency-specs', componentKey: 'OPERATIONS_CURRENCY_SPECS', sortOrder: 70 });
  const manage = resource('currency-manage', { parentResourceId: 'currency', resourceType: 'BUTTON', resourceCode: 'OPERATIONS_CURRENCY_SPEC_MANAGE' });
  const referenceData = resource('refdata', { parentResourceId: 'platform', resourceCode: 'OPERATIONS_REFERENCE_DATA', displayName: '参考数据',
    frontendRoute: '/reference-data', componentKey: 'OPERATIONS_REFERENCE_DATA', sortOrder: 95 });
  const both = menuPages(buildMenuTree([platform, currency, manage, referenceData]));
  assert.deepEqual(both.map(menu => [menu.resourceId, menu.frontendRoute]), [['refdata', '/reference-data']]);
  const currencyOnly = menuPages(buildMenuTree([platform, currency, manage]));
  assert.deepEqual(currencyOnly.map(menu => [menu.resourceId, menu.resourceCode, menu.componentKey, menu.frontendRoute, menu.sortOrder]),
    [['currency', 'OPERATIONS_REFERENCE_DATA', 'OPERATIONS_REFERENCE_DATA', '/reference-data', 70]]);
  assert.equal(resourceLabel(testT('nav'), currencyOnly[0]), 'Reference Data');
  assert.deepEqual(menuPages(buildMenuTree([platform, referenceData])).map(menu => menu.resourceId), ['refdata']);
  assert.deepEqual(mergeMenus([manage]), [manage], 'buttons are untouched');
  assert.deepEqual(legacyRedirects(currencyOnly), [{ path: '/currency-specs', to: '/reference-data?tab=currencies' }]);
  assert.deepEqual(legacyRedirects(menuPages(buildMenuTree([platform, audit]))), [], 'no redirect without the reference data page');
});
