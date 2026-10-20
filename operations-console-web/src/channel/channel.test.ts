import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pageRoutePaths } from '../navigation/menu.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  canCreateVersion, capabilityConditionIssues, capabilityDetailIssues, capabilityDetailsBody, capabilitySummary, channelButtons, channelErrorMessage,
  channelPaths, channelRoutes, componentSummary, configEntryIssues, configurationBody, costVersionActions, costVersionBody, costVersionIssues,
  decodeAccount, decodeChannelPage, decodeCostVersion, decodeMid, decodePlan, decodeVersionDiff, defaultCompareBase, diffRows, diffSummary, editable,
  fieldPath, lifecycleActions, percent, primaryLifecycleAction, scopeDrafts, tokenSuffixLabel,
} from './channel.ts';
import type { CapabilityDefinition, CostPlan, ScopeDraft } from './channel.ts';

const time = '2026-10-20T01:00:00Z';
const hash = 'a'.repeat(64);
const channelSummary = { channelId: '7300000000000000001', channelCode: 'PAYERMAX', channelName: 'PayerMax', status: 'ACTIVE', accountCount: 2, midCount: 3, updateTime: time };
const account = { channelAccountId: '7312001', channelId: '7300000000000000001', entityNo: 'AE001', accountName: '主账号', status: 'ACTIVE', remark: null,
  configured: true, vaultTokenSuffix: '7Q2x', boundMidCount: 3, createTime: time, updateTime: time };
const mid = { midId: '7401001', channelId: '7300000000000000001', channelAccountId: '7312001', externalMidCode: 'PM-AE001-CARD', entityNo: 'AE001',
  status: 'ACTIVE', activatedAt: time, closedAt: null, createTime: time, updateTime: time, capabilities: [{ midCapabilityId: '7501', midId: '7401001',
    paymentMethodCode: 'CARD', schemeCode: 'VISA', countryCode: 'AE', currency: 'AED', mccCode: '5816', status: 'ENABLED', createTime: time, updateTime: time,
    details: [{ capabilityCode: 'ACTION.PAY', valueType: 'BOOLEAN', booleanValue: true, decimalValue: null, codeValue: null },
      { capabilityCode: 'ACTION.REFUND', valueType: 'BOOLEAN', booleanValue: false, decimalValue: null, codeValue: null },
      { capabilityCode: 'AMOUNT.MIN', valueType: 'DECIMAL', booleanValue: null, decimalValue: '10', codeValue: null },
      { capabilityCode: 'AMOUNT.MAX', valueType: 'DECIMAL', booleanValue: null, decimalValue: '50000', codeValue: null },
      { capabilityCode: 'SECURITY.3DS_MODE', valueType: 'CODE', booleanValue: null, decimalValue: null, codeValue: 'REQUIRED' }] }] };
const component = (code: string, rate: string | null, fixed: string | null = null) => ({ componentId: '1', componentCode: code, componentType: 'PROCESSING_FEE',
  calculationBase: 'TRANSACTION_AMOUNT', rateValue: rate, fixedAmount: fixed, currency: fixed ? 'AED' : null, minAmount: null, maxAmount: null, roundingMode: 'HALF_UP' });
const scope = (scopeNo: number, actionType: string, components: unknown[]) => ({ scopeNo, midId: '7401001', entityNo: 'AE001', channelAgreementRef: 'PM-2026',
  countryCode: 'AE', currency: 'AED', paymentMethodCode: 'CARD', actionType, components });
const version = (costVer: string, status: string, effectiveFrom = time) => ({ costVerId: `740100${costVer}`, planCode: 'PAYERMAX_STD', costVer, status, effectiveFrom, effectiveTo: null,
  contentHash: hash, makerId: '10001', checkerId: null, scopes: [scope(1, 'PAY', [component('PROC_FEE', '0.0290', '1.00')])] });
const definitions: CapabilityDefinition[] = [
  { capabilityCode: 'ACTION.PAY', valueType: 'BOOLEAN', allowedCodes: [] },
  { capabilityCode: 'AMOUNT.MIN', valueType: 'DECIMAL', allowedCodes: [] },
  { capabilityCode: 'SECURITY.3DS_MODE', valueType: 'CODE', allowedCodes: ['REQUIRED', 'SUPPORTED', 'UNSUPPORTED'] },
];

test('页面注册：三页、按钮码与详情路由和块 92 一致', () => {
  const pages = Object.fromEntries(pageCapabilities.map(page => [page.componentKey, page]));
  assert.equal(pages.OPERATIONS_CHANNEL_CHANNELS.frontendRoute, channelRoutes.channels);
  assert.equal(pages.OPERATIONS_CHANNEL_MIDS.frontendRoute, channelRoutes.mids);
  assert.equal(pages.OPERATIONS_CHANNEL_COST_PLANS.frontendRoute, channelRoutes.costPlans);
  assert.deepEqual(pageCapabilities.filter(p => p.componentKey.startsWith('OPERATIONS_CHANNEL_')).flatMap(p => p.actions.map(a => a.resourceCode)).sort(),
    Object.values(channelButtons).sort());
  const menu = (componentKey: string, frontendRoute: string) => ({ resourceId: '1', resourceCode: componentKey, resourceType: 'MENU' as const,
    parentResourceId: null, displayName: '', frontendRoute, componentKey, sortOrder: 1 });
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_CHANNEL_CHANNELS', '/channel/channels')), ['/channel/channels', '/channel/channels/:channelId']);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_CHANNEL_MIDS', '/channel/mids')), ['/channel/mids', '/channel/mids/:midId']);
  assert.deepEqual(pageRoutePaths(menu('OPERATIONS_CHANNEL_COST_PLANS', '/channel/cost-plans')),
    ['/channel/cost-plans', '/channel/cost-plans/:planCode', '/channel/cost-plans/:planCode/versions/:costVer']);
  assert.equal(channelRoutes.costCompare('P', '3', '4'), '/channel/cost-plans/P/versions/4?compareWith=3');
});

test('BFF 路径与 30 条路由及对比路由对应', () => {
  assert.equal(channelPaths.accountConfiguration('7'), '/channel-admin/v1/channel-accounts/7/configuration');
  assert.equal(channelPaths.midAccount('8'), '/channel-admin/v1/mids/8/account');
  assert.equal(channelPaths.capabilityDetails('9'), '/channel-admin/v1/mid-capabilities/9/details');
  assert.equal(channelPaths.costVersionAction('P', '2', 'cancel'), '/channel-admin/v1/channel-cost-plans/P/versions/2/cancel');
  assert.equal(channelPaths.costVersionDiff('P', '3', '4'), '/channel-admin/v1/channel-cost-plans/P/versions/3/diff/4');
});

test('解码：合法响应通过，身份不一致、缺字段或非法状态报 502', () => {
  assert.equal(decodeChannelPage({ items: [channelSummary], page: 1, size: 10, total: 1 }).items[0].channelCode, 'PAYERMAX');
  assert.equal(decodeAccount(account, '7312001').vaultTokenSuffix, '7Q2x');
  assert.equal(decodeMid(mid, '7401001').capabilities[0].details.length, 5);
  assert.equal(decodeCostVersion('PAYERMAX_STD', '3')(version('3', 'ACTIVE')).scopes[0].components[0].rateValue, '0.0290');
  for (const invalid of [() => decodeAccount(account, '1'), () => decodeMid({ ...mid, status: 'UNKNOWN' }), () => decodeChannelPage({ items: [{ ...channelSummary, midCount: -1 }] }),
    () => decodeCostVersion('OTHER')(version('3', 'ACTIVE')), () => decodeAccount({ ...account, configured: 'yes' })]) {
    assert.throws(invalid, (error: unknown) => error instanceof ApiError && error.status === 502);
  }
  assert.equal(decodeAccount({ ...account, vaultTokenSuffix: undefined, configured: false }).vaultTokenSuffix, null);
});

test('状态按钮：只显示当前状态允许的下一步，CLOSED 与无权限无操作', () => {
  assert.deepEqual(lifecycleActions('DRAFT', true).map(a => a.targetStatus), ['ACTIVE', 'CLOSED']);
  assert.deepEqual(lifecycleActions('ACTIVE', true).map(a => a.label), ['暂停', '关闭']);
  assert.deepEqual(lifecycleActions('SUSPENDED', true).map(a => a.label), ['恢复', '关闭']);
  assert.deepEqual(lifecycleActions('CLOSED', true), []);
  assert.deepEqual(lifecycleActions('ACTIVE', false), []);
  assert.equal(primaryLifecycleAction('DRAFT', true)?.label, '激活');
  assert.equal(primaryLifecycleAction('CLOSED', true), null);
  assert.equal(editable('CLOSED', true), false);
  assert.equal(editable('SUSPENDED', true), true);
});

test('成本版本按钮：Maker / Checker 权限分离，作废只给未到期 APPROVED', () => {
  const now = Date.parse('2026-10-20T00:00:00Z');
  const manage = { canManage: true, canApprove: false }; const approve = { canManage: false, canApprove: true };
  assert.deepEqual(costVersionActions({ status: 'DRAFT', effectiveFrom: time }, manage, now), { submit: true, approve: false, reject: false, cancel: false });
  assert.deepEqual(costVersionActions({ status: 'PENDING_APPROVAL', effectiveFrom: time }, manage, now), { submit: false, approve: false, reject: false, cancel: false });
  assert.deepEqual(costVersionActions({ status: 'PENDING_APPROVAL', effectiveFrom: time }, approve, now), { submit: false, approve: true, reject: true, cancel: false });
  assert.equal(costVersionActions({ status: 'APPROVED', effectiveFrom: '2026-10-25T00:00:00Z' }, manage, now).cancel, true);
  assert.equal(costVersionActions({ status: 'APPROVED', effectiveFrom: '2026-10-19T00:00:00Z' }, manage, now).cancel, false);
  assert.equal(costVersionActions({ status: 'ACTIVE', effectiveFrom: '2026-10-25T00:00:00Z' }, manage, now).cancel, false);
  const plan = decodePlan('PAYERMAX_STD')({ planCode: 'PAYERMAX_STD', channelId: '1', status: 'ACTIVE', currentCostVer: '3',
    versions: [version('4', 'APPROVED'), version('3', 'ACTIVE'), version('2', 'INACTIVE')] }) as CostPlan;
  assert.equal(canCreateVersion(plan, true), true);
  assert.equal(plan.versions[0].costVerId, '7401004');
  assert.equal(plan.versions[0].scopeCount, null);
  const counted = decodePlan('PAYERMAX_STD')({ planCode: 'PAYERMAX_STD', channelId: '1', status: 'ACTIVE', currentCostVer: '3',
    versions: [{ ...version('3', 'ACTIVE'), scopes: undefined, scopeCount: 2, componentCount: 5 }] }) as CostPlan;
  assert.deepEqual([counted.versions[0].scopeCount, counted.versions[0].componentCount], [2, 5]);
  assert.throws(() => decodePlan('PAYERMAX_STD')({ planCode: 'PAYERMAX_STD', channelId: '1', status: 'ACTIVE', currentCostVer: null,
    versions: [{ ...version('3', 'ACTIVE'), costVerId: undefined }] }));
  assert.equal(canCreateVersion({ ...plan, versions: [...plan.versions, { ...plan.versions[0], costVer: '5', status: 'PENDING_APPROVAL' }] }, true), false);
  assert.equal(defaultCompareBase(plan, '4'), '3');
  assert.equal(defaultCompareBase(plan, '3'), '2');
  assert.equal(defaultCompareBase({ ...plan, currentCostVer: null }, '2'), null);
});

test('账号配置：键格式、重复、值长度与 64 项上限；凭据不回显只显示 Token 末 4 位', () => {
  assert.equal(configEntryIssues([{ key: 'merchantId', value: 'PM-1' }, { key: 'outlet.ref', value: 'x' }], '参数'), null);
  assert.match(configEntryIssues([{ key: '1bad', value: 'x' }], '参数') ?? '', /名称须以字母开头/);
  assert.match(configEntryIssues([{ key: 'apiKey', value: 'a' }, { key: 'apiKey', value: 'b' }], '凭据') ?? '', /重复/);
  assert.match(configEntryIssues([{ key: 'apiKey', value: '' }], '凭据') ?? '', /1–8192/);
  assert.match(configEntryIssues([{ key: 'apiKey', value: 'x'.repeat(8193) }], '凭据') ?? '', /1–8192/);
  assert.match(configEntryIssues(Array.from({ length: 65 }, (_, i) => ({ key: `k${i}`, value: 'v' })), '参数') ?? '', /64/);
  assert.deepEqual(configurationBody([{ key: 'merchantId', value: 'PM-1' }], [{ key: 'apiKey', value: 'secret' }]),
    { parameters: { merchantId: 'PM-1' }, credentials: { apiKey: 'secret' } });
  assert.equal(tokenSuffixLabel({ configured: true, vaultTokenSuffix: '7Q2x' }), 'Token …7Q2x');
  assert.equal(tokenSuffixLabel({ configured: false, vaultTokenSuffix: null }), '未配置');
});

test('能力组：六维条件格式与明细按目录类型只取一个值', () => {
  assert.deepEqual(capabilityConditionIssues({ paymentMethodCode: 'CARD', schemeCode: '*', countryCode: 'AE', currency: '*', mccCode: '5816' }), {});
  const issues = capabilityConditionIssues({ paymentMethodCode: 'card', schemeCode: '', countryCode: 'ARE', currency: 'AE', mccCode: '581' });
  assert.deepEqual(Object.keys(issues).sort(), ['countryCode', 'currency', 'mccCode', 'paymentMethodCode', 'schemeCode']);
  assert.equal(capabilityDetailIssues([{ capabilityCode: 'ACTION.PAY', booleanValue: true }, { capabilityCode: 'AMOUNT.MIN', decimalValue: '10.5' },
    { capabilityCode: 'SECURITY.3DS_MODE', codeValue: 'REQUIRED' }], definitions), null);
  assert.match(capabilityDetailIssues([{ capabilityCode: 'UNKNOWN' }], definitions) ?? '', /未在目录中注册/);
  assert.match(capabilityDetailIssues([{ capabilityCode: 'AMOUNT.MIN', decimalValue: '1.123456789' }], definitions) ?? '', /8 位小数/);
  assert.match(capabilityDetailIssues([{ capabilityCode: 'SECURITY.3DS_MODE', codeValue: 'MAYBE' }], definitions) ?? '', /只能取/);
  assert.match(capabilityDetailIssues([], definitions) ?? '', /至少一项/);
  assert.deepEqual(capabilityDetailsBody([{ capabilityCode: 'AMOUNT.MIN', decimalValue: ' 10 ', booleanValue: true }], definitions),
    [{ capabilityCode: 'AMOUNT.MIN', booleanValue: null, decimalValue: '10', codeValue: null }]);
  assert.equal(capabilitySummary(decodeMid(mid).capabilities[0].details), 'PAY ✓ REFUND ✗ · 10–50000 · SECURITY.3DS_MODE=REQUIRED');
});

test('版本编制：MCC 以外的范围格式、十进制费率与金额、组成唯一与币种要求', () => {
  const good: ScopeDraft = { midId: '7401001', entityNo: 'AE001', channelAgreementRef: 'PM-2026', countryCode: 'AE', currency: 'AED', paymentMethodCode: 'CARD', actionType: 'PAY',
    components: [{ componentCode: 'PROC_FEE', componentType: 'PROCESSING_FEE', calculationBase: 'TRANSACTION_AMOUNT', rateValue: '0.0290', fixedAmount: '1.00',
      currency: 'AED', roundingMode: 'HALF_UP' }] };
  assert.deepEqual(costVersionIssues(time, [good]), []);
  assert.ok(costVersionIssues(undefined, []).includes('生效时间必填'));
  const bad: ScopeDraft = { ...good, countryCode: 'ARE', currency: 'AE', components: [{ ...good.components[0], rateValue: '2.9%', fixedAmount: '1.123456789' },
    { ...good.components[0], currency: undefined }] };
  const issues = costVersionIssues(time, [bad, good, good]).join('\n');
  for (const expected of ['国家须为两位', '币种须为三位', '费率须为最多 10 位小数', '金额须为最多 8 位小数', '组成代码 PROC_FEE 重复', '填写金额时需要币种', '六维相同']) {
    assert.ok(issues.includes(expected), expected);
  }
  assert.ok(costVersionIssues(time, [{ ...good, channelAgreementRef: '  ' }]).includes('范围 1：合同引用必填'));
  const body = costVersionBody(time, [{ ...good, channelAgreementRef: ' PM-2026 ' }]);
  assert.equal(body.scopes[0].scopeNo, 1);
  assert.equal(body.scopes[0].channelAgreementRef, 'PM-2026');
  assert.equal(body.scopes[0].components[0].minAmount, null);
  assert.equal('makerId' in body, false);
  assert.equal(scopeDrafts(decodeCostVersion('PAYERMAX_STD')(version('3', 'ACTIVE')))[0].components[0].fixedAmount, '1.00');
});

test('对比模型：范围 × 组成展开、修改字段与头部统计', () => {
  const baseScope = scope(1, 'PAY', [component('PROC_FEE', '0.0290', '1.00'), component('XB_FEE', '0.0100')]);
  const targetScope = scope(1, 'PAY', [component('PROC_FEE', '0.0290', '1.00'), component('XB_FEE', '0.0120'), component('NEW_FEE', '0.0010')]);
  const diff = decodeVersionDiff('3', '4')({
    base: { costVerId: '7401003', scopeCount: 2, componentCount: 3, costVer: '3', status: 'ACTIVE', effectiveFrom: time, effectiveTo: null, contentHash: hash, makerId: '10001', checkerId: '10002' },
    target: { costVerId: '7401004', scopeCount: 2, componentCount: 4, costVer: '4', status: 'APPROVED', effectiveFrom: time, effectiveTo: null, contentHash: hash, makerId: '10001', checkerId: '10002' },
    added: [scope(2, 'REFUND', [component('REFUND_FEE', null, '2.00')])], removed: [], unchanged: [scope(3, 'CAPTURE', [component('CAP_FEE', '0.001')])],
    changed: [{ base: baseScope, target: targetScope, differences: [], addedComponents: [component('NEW_FEE', '0.0010')], removedComponents: [],
      changedComponents: [{ componentCode: 'XB_FEE', base: component('XB_FEE', '0.0100'), target: component('XB_FEE', '0.0120'),
        differences: [{ field: 'rateValue', baseValue: '0.0100', targetValue: '0.0120' }] }] }],
  });
  const rows = diffRows(diff);
  assert.deepEqual(rows.map(row => `${row.scope.actionType}/${row.componentCode}/${row.change}`).sort(), [
    'CAPTURE/CAP_FEE/UNCHANGED', 'PAY/NEW_FEE/ADDED', 'PAY/PROC_FEE/UNCHANGED', 'PAY/XB_FEE/CHANGED', 'REFUND/REFUND_FEE/ADDED']);
  const changed = rows.find(row => row.change === 'CHANGED')!;
  assert.equal(changed.baseSummary, '1.00% · HALF_UP');
  assert.equal(changed.targetSummary, '1.20% · HALF_UP');
  assert.equal(changed.differences[0].field, 'rateValue');
  assert.equal(rows.find(row => row.componentCode === 'REFUND_FEE')!.baseSummary, null);
  assert.deepEqual(diffSummary(diff), { baseScopes: 2, targetScopes: 3, addedScopes: 1, removedScopes: 0, baseComponents: 3, targetComponents: 5,
    addedComponents: 2, removedComponents: 0, changedComponents: 1 });
  assert.throws(() => decodeVersionDiff('3', '5')({ ...diff, base: diff.base, target: diff.target }), (error: unknown) => error instanceof ApiError);
});

test('展示与错误：百分比字符串移位、成本摘要、Owner 原因直出、字段路径', () => {
  assert.equal(percent('0.0290'), '2.90%');
  assert.equal(percent('0.012'), '1.2%');
  assert.equal(percent('1'), '100%');
  assert.equal(componentSummary({ rateValue: '0.0290', fixedAmount: '1.00', currency: 'AED', minAmount: null, maxAmount: '50.00', roundingMode: 'HALF_UP' }),
    '2.90% + 1.00 AED · 上限 50.00 AED · HALF_UP');
  assert.equal(componentSummary({ rateValue: null, fixedAmount: '2.00', currency: 'AED', minAmount: null, maxAmount: null, roundingMode: 'HALF_UP' }), '固定 2.00 AED · HALF_UP');
  assert.equal(channelErrorMessage(new ApiError(400, 'BUSINESS_CHECK_FAILED', 'r', 'Platform 不存在同编码接入方 NI')), '操作被拒绝：Platform 不存在同编码接入方 NI');
  assert.match(channelErrorMessage(new ApiError(400, 'BUSINESS_CHECK_FAILED', 'r', '业务校验未通过')) ?? '', /BUSINESS_CHECK_FAILED/);
  assert.equal(channelErrorMessage(new ApiError(409, 'CONCURRENT_REQUEST')), '数据已被其他操作修改，请重新加载后再试。');
  assert.equal(channelErrorMessage(new ApiError(404, 'NOT_FOUND', 'r', '渠道不存在: 1')), '渠道不存在: 1');
  assert.equal(channelErrorMessage(new ApiError(503, 'DEPENDENCY_UNAVAILABLE')), null);
  assert.deepEqual(fieldPath('scopes[0].components[1].rateValue'), ['scopes', 0, 'components', 1, 'rateValue']);
});
