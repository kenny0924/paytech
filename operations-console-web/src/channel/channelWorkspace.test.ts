import assert from 'node:assert/strict';
import test from 'node:test';
import { decodeCallGrant, decodeChannel, decodeChannelSummary } from './channel.ts';
import type { CallGrant } from './channel.ts';
import {
  activeGrantsByCaller, callerOptions, channelByCodeRoute, channelListFilters, channelTypeFilter, detailTab, detailTabs, endpointDraftIssues,
  endpointReferences, endpointRowErrors, endpointsBody, grantMatrix, grantView, isCallerService, legacyIntegrationTarget, parseChannelPath,
  parseLegacyIntegrationPath, protocolSection, typeCountsSequentially, typeSegmentSearch, workspaceRoutes,
} from './channelWorkspace.ts';

const time = '2026-10-22T01:00:00Z';
const grant = (callGrantId: string, callerService: string, standardApiCode: string, grantStatus: 'ACTIVE' | 'DISABLED' = 'ACTIVE'): CallGrant => ({
  callGrantId, channelId: '880000000207', callerService, standardApiCode, standardApiName: standardApiCode.toLowerCase(), grantStatus, version: 1,
  createdBy: '1001', updatedBy: null, createTime: time, updateTime: time });

test('类型分段：?type= 只认 PAYMENT / SERVICE，切换时保留其他筛选并回到第 1 页', () => {
  assert.equal(channelTypeFilter('?type=SERVICE'), 'SERVICE');
  assert.equal(channelTypeFilter('?type=PAYMENT&keyword=NI'), 'PAYMENT');
  assert.equal(channelTypeFilter('?type=service'), null);
  assert.equal(channelTypeFilter(''), null);
  assert.equal(typeSegmentSearch('?keyword=NI&page=3&size=20', 'SERVICE'), '?keyword=NI&size=20&type=SERVICE');
  assert.equal(typeSegmentSearch('?type=SERVICE&page=2', null), '');
  assert.deepEqual(channelListFilters('?type=SERVICE&keyword=%20GOO%20&status=ACTIVE&page=2'), { keyword: 'GOO', status: 'ACTIVE', channelType: 'SERVICE' });
  assert.deepEqual(channelListFilters('?type=X&status=BAD'), {});
});

test('详情页签按类型不同；非法或不适用的 ?tab= 回到概览', () => {
  assert.deepEqual(detailTabs.PAYMENT, ['overview', 'accounts', 'mids', 'protocol', 'verify', 'changes']);
  assert.deepEqual(detailTabs.SERVICE, ['overview', 'credential', 'protocol', 'verify', 'grants', 'changes']);
  assert.equal(detailTab('SERVICE', 'grants'), 'grants');
  assert.equal(detailTab('PAYMENT', 'grants'), 'overview');
  assert.equal(detailTab('SERVICE', 'mids'), 'overview');
  assert.equal(detailTab('PAYMENT', null), 'overview');
  assert.equal(protocolSection('apis'), 'apis');
  assert.equal(protocolSection('x'), 'implementations');
  assert.equal(grantView('list'), 'list');
  assert.equal(grantView(null), 'matrix');
});

test('工作区路由：页签、协议子页与沙箱子页', () => {
  const routes = workspaceRoutes('880000000101');
  assert.equal(routes.tab('overview'), '/channel/channels/880000000101');
  assert.equal(routes.tab('grants', { view: 'list' }), '/channel/channels/880000000101?tab=grants&view=list');
  assert.equal(routes.protocol('implementations'), '/channel/channels/880000000101?tab=protocol');
  assert.equal(routes.protocol('scripts'), '/channel/channels/880000000101?tab=protocol&section=scripts');
  assert.equal(routes.changes('CHANNEL_CALL_GRANT', '880000000931'), '/channel/channels/880000000101?tab=changes&targetType=CHANNEL_CALL_GRANT&targetId=880000000931');
  assert.equal(routes.implementation('PAY', 'create'), '/channel/channels/880000000101/protocol/implementations/PAY?mode=create');
  assert.equal(routes.api('CREATE_ORDER', 'edit'), '/channel/channels/880000000101/protocol/apis/CREATE_ORDER?mode=edit');
  assert.equal(routes.verifyRun('42'), '/channel/channels/880000000101/verify/runs/42');
  assert.equal(channelByCodeRoute('NI', { tab: 'protocol' }), '/channel/channels/code/NI?tab=protocol');
  assert.equal(channelByCodeRoute('NI', {}, '/protocol/implementations/PAY'), '/channel/channels/code/NI/protocol/implementations/PAY');
});

test('渠道路径解析：列表、详情、按编码、协议与沙箱子页；其余为 404', () => {
  assert.deepEqual(parseChannelPath('/channel/channels'), { kind: 'list' });
  assert.deepEqual(parseChannelPath('/channel/channels/'), { kind: 'list' });
  assert.deepEqual(parseChannelPath('/channel/channels/880000000101'), { kind: 'detail', channelId: '880000000101' });
  assert.deepEqual(parseChannelPath('/channel/channels/code/NI'), { kind: 'byCode', channelCode: 'NI', suffix: '' });
  assert.deepEqual(parseChannelPath('/channel/channels/code/NI/protocol/implementations/PAY'),
    { kind: 'byCode', channelCode: 'NI', suffix: '/protocol/implementations/PAY' });
  assert.equal(parseChannelPath('/channel/channels/code/NI/mids'), null);
  assert.deepEqual(parseChannelPath('/channel/channels/1/protocol/apis/new'), { kind: 'protocol', channelId: '1', section: 'apis', code: null });
  assert.deepEqual(parseChannelPath('/channel/channels/1/protocol/implementations/PAY'), { kind: 'protocol', channelId: '1', section: 'implementations', code: 'PAY' });
  assert.equal(parseChannelPath('/channel/channels/1/protocol/implementations/new'), null);
  assert.deepEqual(parseChannelPath('/channel/channels/1/verify/cases/UAT_PAY'), { kind: 'verify', channelId: '1', section: 'cases', code: 'UAT_PAY' });
  assert.deepEqual(parseChannelPath('/channel/channels/1/verify/runs/77'), { kind: 'verify', channelId: '1', section: 'runs', code: '77' });
  assert.equal(parseChannelPath('/channel/channels/1/verify/runs/new'), null);
  assert.equal(parseChannelPath('/channel/channels/abc'), null);
  assert.equal(parseChannelPath('/channel/channels/1/mids'), null);
  assert.equal(parseChannelPath('/channel/channels/code/ni'), null);
});

test('旧接入方地址映射到渠道详情的同名页签', () => {
  assert.deepEqual(parseLegacyIntegrationPath('/external-api/integrations'), { code: null, rest: [] });
  assert.deepEqual(parseLegacyIntegrationPath('/external-api/integrations/new'), { code: null, rest: [] });
  assert.deepEqual(parseLegacyIntegrationPath('/external-api/integrations/NI/apis/CREATE_ORDER'), { code: 'NI', rest: ['apis', 'CREATE_ORDER'] });
  assert.equal(parseLegacyIntegrationPath('/external-api/integrations/ni'), null);
  assert.equal(parseLegacyIntegrationPath('/external-api/standard-apis'), null);
  const id = '880000000101';
  assert.equal(legacyIntegrationTarget(id, [], ''), '/channel/channels/880000000101');
  assert.equal(legacyIntegrationTarget(id, [], '?tab=basic&mode=edit'), '/channel/channels/880000000101');
  assert.equal(legacyIntegrationTarget(id, [], '?tab=apis&page=2'), '/channel/channels/880000000101?tab=protocol&section=apis');
  assert.equal(legacyIntegrationTarget(id, [], '?tab=implementations'), '/channel/channels/880000000101?tab=protocol');
  assert.equal(legacyIntegrationTarget(id, [], '?tab=verify'), '/channel/channels/880000000101?tab=verify');
  assert.equal(legacyIntegrationTarget(id, [], '?tab=unknown'), '/channel/channels/880000000101');
  assert.equal(legacyIntegrationTarget(id, ['apis', 'CREATE_ORDER'], '?mode=edit'), '/channel/channels/880000000101/protocol/apis/CREATE_ORDER?mode=edit');
  assert.equal(legacyIntegrationTarget(id, ['scripts', 'new'], ''), '/channel/channels/880000000101/protocol/scripts/new');
  assert.equal(legacyIntegrationTarget(id, ['implementations', 'PAY'], '?mode=create'), '/channel/channels/880000000101/protocol/implementations/PAY?mode=create');
  assert.equal(legacyIntegrationTarget(id, ['verify-accounts', 'UAT'], ''), '/channel/channels/880000000101/verify/accounts/UAT');
  assert.equal(legacyIntegrationTarget(id, ['verify-cases', 'PAY_OK'], '?mode=edit'), '/channel/channels/880000000101/verify/cases/PAY_OK?mode=edit');
  assert.equal(legacyIntegrationTarget(id, ['verify-runs', '9001'], ''), '/channel/channels/880000000101/verify/runs/9001');
  assert.equal(legacyIntegrationTarget(id, ['verify-runs', 'x'], ''), null);
  assert.equal(legacyIntegrationTarget(id, ['implementations', 'new'], ''), null);
  assert.equal(legacyIntegrationTarget(id, ['apis'], ''), null);
});

test('授权矩阵：行为调用方、列为已实现标准 API，未实现的授权单列，计数分状态', () => {
  const grants = [grant('3', 'commerce-service', 'PLACE_AUTOCOMPLETE'), grant('1', 'counterparty-service', 'GEOCODE_ADDRESS'),
    grant('2', 'counterparty-service', 'VALIDATE_ADDRESS'), grant('4', 'commerce-service', 'GEOCODE_ADDRESS', 'DISABLED'),
    grant('5', 'risk-service', 'REMOVED_API', 'DISABLED')];
  const matrix = grantMatrix(grants, [{ standardApiCode: 'VALIDATE_ADDRESS', standardApiName: '地址校验' },
    { standardApiCode: 'GEOCODE_ADDRESS', standardApiName: '地址解析' }, { standardApiCode: 'PLACE_AUTOCOMPLETE', standardApiName: '地址补全' }]);
  assert.deepEqual(matrix.columns.map(column => column.standardApiCode), ['GEOCODE_ADDRESS', 'PLACE_AUTOCOMPLETE', 'VALIDATE_ADDRESS']);
  assert.deepEqual(matrix.rows.map(row => row.callerService), ['commerce-service', 'counterparty-service']);
  assert.equal(matrix.rows[0].cells.GEOCODE_ADDRESS?.grantStatus, 'DISABLED');
  assert.equal(matrix.rows[0].cells.PLACE_AUTOCOMPLETE?.callGrantId, '3');
  assert.equal(matrix.rows[0].cells.VALIDATE_ADDRESS, undefined);
  assert.deepEqual(matrix.orphans.map(item => item.callGrantId), ['5']);
  assert.equal(matrix.activeCount, 3);
  assert.equal(matrix.disabledCount, 2);
  assert.deepEqual(grantMatrix([], []), { columns: [], rows: [], orphans: [], activeCount: 0, disabledCount: 0 });
  assert.deepEqual(activeGrantsByCaller(grants), [{ callerService: 'commerce-service', standardApiCodes: ['PLACE_AUTOCOMPLETE'] },
    { callerService: 'counterparty-service', standardApiCodes: ['GEOCODE_ADDRESS', 'VALIDATE_ADDRESS'] }]);
});

test('调用方候选：已有调用方并入预置名单，带已有条数；服务名格式校验', () => {
  const options = callerOptions([grant('1', 'counterparty-service', 'A'), grant('2', 'counterparty-service', 'B'), grant('3', 'billing-service', 'A')]);
  assert.deepEqual(options.map(option => option.callerService), ['acquiring-service', 'billing-service', 'client-master-service', 'commerce-service',
    'counterparty-service', 'payment-service', 'risk-service']);
  assert.deepEqual(options.find(option => option.callerService === 'counterparty-service'), { callerService: 'counterparty-service', label: '交易对手', grantCount: 2 });
  assert.equal(options.find(option => option.callerService === 'billing-service')?.label, null);
  assert.ok(isCallerService('risk-service'));
  assert.ok(!isCallerService('Risk-service'));
  assert.ok(!isCallerService('r'));
  assert.ok(!isCallerService('1risk'));
  assert.ok(!isCallerService('risk_service'));
});

test('出站地址：引用汇总、输入校验、Owner 错误定位到行、请求体', () => {
  const references = endpointReferences([{ apiCode: 'B', endpointCode: 'NI_API' }, { apiCode: 'A', endpointCode: 'NI_API' }, { apiCode: 'C', endpointCode: 'NI_SANDBOX' }]);
  assert.deepEqual(references.get('NI_API'), ['A', 'B']);
  assert.deepEqual(references.get('NI_SANDBOX'), ['C']);
  const rows = [{ endpointCode: 'NI', baseUrl: 'https://a.example.com' }, { endpointCode: 'NI_API', baseUrl: 'https://api.example.com' },
    { endpointCode: 'NI_INTERNAL', baseUrl: 'https://10.20.4.17:8443' }];
  assert.deepEqual([...endpointDraftIssues([{ endpointCode: '', baseUrl: 'x' }, { endpointCode: 'A', baseUrl: '' }, { endpointCode: 'B', baseUrl: 'u' },
    { endpointCode: 'B', baseUrl: 'u' }]).entries()], [[0, '请填写地址编码'], [1, '请填写 Base URL'], [3, '地址编码 B 重复']]);
  const byField = endpointRowErrors(rows, [{ field: 'endpoints[2].baseUrl', message: '地址落在私网' }, { field: 'endpoints', message: '最多 16 个' }]);
  assert.deepEqual([...byField.rows.entries()], [[2, ['地址落在私网']]]);
  assert.deepEqual(byField.general, ['最多 16 个']);
  const byHost = endpointRowErrors(rows, [], '出站地址不允许指向私网或保留地址: 10.20.4.17');
  assert.deepEqual([...byHost.rows.entries()], [[2, ['出站地址不允许指向私网或保留地址: 10.20.4.17']]]);
  const longest = endpointRowErrors(rows, [], '出站地址 NI_API 不允许 localhost');
  assert.deepEqual([...longest.rows.keys()], [1]);
  const general = endpointRowErrors(rows, [], '出站地址 NI_OLD 仍被原子 API 引用: A、B');
  assert.deepEqual(general, { rows: new Map(), general: ['出站地址 NI_OLD 仍被原子 API 引用: A、B'] });
  assert.deepEqual(endpointsBody(7, [{ endpointCode: ' NI_API ', baseUrl: ' https://api.example.com ' }, { endpointCode: 'S', baseUrl: 'https://s', sandbox: true }]),
    { expectedVersion: 7, endpoints: [{ endpointCode: 'NI_API', baseUrl: 'https://api.example.com', sandbox: false },
      { endpointCode: 'S', baseUrl: 'https://s', sandbox: true }] });
});

test('第二段 BFF 合同解码：渠道摘要、详情与授权', () => {
  const summary = decodeChannelSummary({ channelId: '880000000207', channelCode: 'GOOGLE_MAPS', channelName: 'Google 地图服务', channelType: 'SERVICE',
    status: 'ACTIVE', accountCount: 1, midCount: 0, endpointCount: 1, activeGrantCount: 3, updateTime: time });
  assert.equal(summary.activeGrantCount, 3);
  assert.equal(decodeChannelSummary({ ...summary, channelType: 'PAYMENT', activeGrantCount: null }).activeGrantCount, null);
  assert.throws(() => decodeChannelSummary({ ...summary, channelType: 'OTHER' }));
  assert.throws(() => decodeChannelSummary({ ...summary, endpointCount: undefined }));
  const detail = decodeChannel({ channelId: '880000000207', channelCode: 'GOOGLE_MAPS', channelName: 'Google 地图服务', channelType: 'SERVICE',
    description: null, endpoints: [{ endpointCode: 'MAPS_API', baseUrl: 'https://maps.googleapis.com' }], version: 4, status: 'ACTIVE',
    createTime: time, updateTime: time }, '880000000207');
  assert.deepEqual(detail.endpoints, [{ endpointCode: 'MAPS_API', baseUrl: 'https://maps.googleapis.com', sandbox: false }]);
  assert.deepEqual(decodeChannel({ ...detail, endpoints: null }).endpoints, []);
  assert.throws(() => decodeChannel({ ...detail, version: undefined }));
  const decoded = decodeCallGrant(grant('880000000931', 'commerce-service', 'PLACE_AUTOCOMPLETE'), '880000000931');
  assert.equal(decoded.updatedBy, null);
  assert.throws(() => decodeCallGrant({ ...decoded, callerService: 'Commerce' }));
  assert.throws(() => decodeCallGrant({ ...decoded, grantStatus: 'DELETED' }));
});

test('类型分段计数：全部、支付、服务依次查询，同一时刻只有一个请求；失败的分段不给计数', async () => {
  let inFlight = 0; let maxInFlight = 0; const asked: (string | null)[] = [];
  const counts = await typeCountsSequentially(async channelType => {
    inFlight += 1; maxInFlight = Math.max(maxInFlight, inFlight); asked.push(channelType);
    await new Promise(resolve => setTimeout(resolve, 1));
    inFlight -= 1;
    return channelType === 'SERVICE' ? undefined : channelType === 'PAYMENT' ? 3 : 5;
  });
  assert.deepEqual(asked, [null, 'PAYMENT', 'SERVICE']);
  assert.equal(maxInFlight, 1);
  assert.deepEqual(counts, { ALL: 5, PAYMENT: 3 });
});
