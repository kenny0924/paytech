import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  addEdge, dominators, flowTarget, idPrefix, layout, newFlow, nextId, outgoing, readableSteps, removeVertex, renameVertex,
  reorderOutgoing, requestCoverage, statusCondition, toFlow, toFlowJson, variableOptions,
} from './flowModel.ts';
import type { ApiContract, Flow, FlowEditorContext } from './flowModel.ts';
import type { ApiField } from './externalApiModel.ts';

const node = (id: string, integrationApi = 'API') => ({ id, integrationApi, request: {} });
const field = (fieldLocation: ApiField['fieldLocation'], fieldPath: string, required = true): ApiField & { fixed: boolean } =>
  ({ fieldLocation, fieldPath, fieldName: fieldPath, dataType: 'STRING', required, description: null, fixed: false });
/** a → b（条件）/ c（兜底）→ d → done/other */
function diamond(): Flow {
  return { timeoutMs: 1000, initializers: [], entry: 'a', nodes: [node('a'), node('b'), node('c'), node('d')],
    returns: [{ id: 'done', executeStatus: 'SUCCESS', outputs: {} }, { id: 'other', executeStatus: 'ERROR' }],
    edges: [{ from: 'a', to: 'b', condition: statusCondition('a', 'SUCCESS'), priority: 1 }, { from: 'a', to: 'c', default: true },
      { from: 'b', to: 'd', default: true }, { from: 'c', to: 'd', default: true },
      { from: 'd', to: 'done', condition: 'x', priority: 1 }, { from: 'd', to: 'other', default: true }] };
}

test('菱形汇合：汇合后只有起点与汇合节点必经，分支节点不是', () => {
  const flow = diamond();
  assert.deepEqual([...dominators(flow).get('done')!].sort(), ['a', 'd', 'done']);
  assert.deepEqual(readableSteps(flow, 'done', false), ['a', 'd']);
  assert.deepEqual(readableSteps(flow, 'd', false), ['a']);
  assert.deepEqual(readableSteps(flow, 'd', true), ['a', 'd']);
});

test('连线：只能从节点连出，拒绝环与重复；首条为兜底，之后为条件且优先级递增', () => {
  let flow: Flow = { ...newFlow(), entry: 'a', nodes: [node('a'), node('b')] };
  const first = addEdge(flow, 'a', 'b');
  assert.ok('flow' in first); assert.deepEqual(first.edge, { from: 'a', to: 'b', default: true });
  flow = first.flow;
  assert.deepEqual(addEdge(flow, 'b', 'a'), { error: '流程暂不支持环' });
  assert.deepEqual(addEdge(flow, 'a', 'a'), { error: '流程暂不支持环' });
  assert.deepEqual(addEdge(flow, 'a', 'b'), { error: '已存在相同的连线' });
  assert.deepEqual(addEdge(flow, 'unknown', 'b'), { error: '只能从原子 API 节点连出' });
  const second = addEdge(flow, 'a', 'unknown');
  assert.ok('flow' in second); assert.deepEqual(second.edge, { from: 'a', to: 'unknown', condition: '', priority: 1 });
});

test('出口顺序：条件按优先级、兜底最后；重排后优先级从 1 连续', () => {
  const flow = diamond();
  const extra = { ...flow, edges: [...flow.edges, { from: 'a', to: 'd', condition: 'y', priority: 5 }] };
  assert.deepEqual(outgoing(extra, 'a').map(edge => edge.to), ['b', 'd', 'c']);
  const [first, second] = outgoing(extra, 'a');
  const reordered = reorderOutgoing(extra, 'a', [second, first]);
  assert.deepEqual(outgoing(reordered, 'a').map(edge => `${edge.to}:${edge.priority ?? 'default'}`), ['d:1', 'b:2', 'c:default']);
});

test('删除与重命名同步入口和连线', () => {
  const removed = removeVertex(diamond(), 'a');
  assert.equal(removed.entry, '');
  assert.equal(removed.edges.some(edge => edge.from === 'a' || edge.to === 'a'), false);
  const renamed = renameVertex(diamond(), 'a', 'start1');
  assert.equal(renamed.entry, 'start1');
  assert.equal(renamed.edges.filter(edge => edge.from === 'start1').length, 2);
});

test('标识生成避开初始化项、节点与出口', () => {
  const flow = { ...diamond(), initializers: [{ id: 'createOrder', function: 'X', args: {}, required: true }] };
  assert.equal(nextId(flow, 'createOrder'), 'createOrder2');
  assert.equal(nextId(flow, 'queryOrder'), 'queryOrder');
  assert.equal(idPrefix('CREATE_ORDER'), 'createOrder');
  assert.equal(idPrefix('V2_QUERY'), 'v2Query');
});

test('变量树：input、非凭据资源、必经节点输出；凭据单列，非必经节点灰显', () => {
  const flow = { ...diamond(), initializers: [{ id: 'account', function: 'CHANNEL_ACCOUNT_CONFIG', args: {}, required: true }] };
  const context: FlowEditorContext = { standardApi: { requestFields: [field('BODY', 'order.id'), field('BODY', 'items[].sku')], responseFields: [] },
    apis: [{ apiCode: 'API', apiName: 'api', requestFields: [], params: [], outputs: { SUCCESS: [{ name: 'ref', dataType: 'STRING', required: true }], FAIL: [] } }] };
  const functions = [{ functionCode: 'CHANNEL_ACCOUNT_CONFIG', kind: 'INITIALIZER', params: [], returns: 'OBJECT',
    returnRoots: { credentials: 'PROTECTED_HANDLE', parameters: 'OBJECT' }, stages: [] }];
  const options = variableOptions(flow, context, functions, 'done');
  assert.deepEqual(options.paths, ['input.order.id', 'resources.account.parameters', 'steps.a.executeStatus', 'steps.a.outputs.ref',
    'steps.d.executeStatus', 'steps.d.outputs.ref']);
  assert.deepEqual(options.handlePaths, ['resources.account.credentials.']);
  assert.deepEqual(options.unavailableSteps, ['b', 'c']);
});

test('请求覆盖：固定值与祖先赋值计入，数组元素与可选字段不计', () => {
  const api: ApiContract = { apiCode: 'API', apiName: 'api', params: [], outputs: {}, requestFields: [
    { ...field('HEADER', 'Content-Type'), fixed: true }, field('PATH', 'outletRef'), field('BODY', 'amount'), field('BODY', 'amount.value'),
    field('BODY', 'items[].sku'), field('BODY', 'memo', false)] };
  const coverage = requestCoverage({ id: 'n', integrationApi: 'API', request: { 'BODY:amount': { kind: 'PATH', path: 'input.a' } } }, api);
  assert.deepEqual(coverage, { covered: 3, required: 4, missing: ['PATH:outletRef'] });
});

test('校验问题位置映射到编辑对象', () => {
  assert.deepEqual(flowTarget('flowDefinition.nodes[1].request.BODY:amount.path'), { kind: 'node', index: 1 });
  assert.deepEqual(flowTarget('flowDefinition.edges[3].priority'), { kind: 'edge', index: 3 });
  assert.deepEqual(flowTarget('flowDefinition.initializers[0].function'), { kind: 'initializer', index: 0 });
  assert.deepEqual(flowTarget('flowDefinition.returns[2].outputs'), { kind: 'return', index: 2 });
  assert.deepEqual(flowTarget('flowDefinition.edges'), { kind: 'edges' });
  assert.deepEqual(flowTarget('flowDefinition.timeoutMs'), { kind: 'timeout' });
  assert.deepEqual(flowTarget('standardApiCode'), { kind: 'flow' });
});

test('布局按最长路径分层，不可达顶点放最后一层', () => {
  const flow = { ...diamond(), returns: [...diamond().returns, { id: 'orphan', executeStatus: 'ERROR' as const }] };
  const positions = layout(flow, '__start');
  assert.deepEqual(positions.get('__start'), { x: 0, y: 0 });
  assert.equal(positions.get('a')!.y, 170);
  assert.equal(positions.get('b')!.y, 340);
  assert.equal(positions.get('d')!.y, 510);
  assert.equal(positions.get('done')!.y, 680);
  assert.equal(positions.get('orphan')!.y, 850);
});

test('序列化：ERROR 出口不带 outputs，兜底连线只带 from/to/default，空调用参数省略；解析后再序列化不变', () => {
  const flow = diamond();
  flow.nodes[0] = { ...flow.nodes[0], params: {} };
  const json = toFlowJson(flow);
  assert.deepEqual(Object.keys(json), ['timeoutMs', 'initializers', 'entry', 'nodes', 'returns', 'edges']);
  assert.equal('params' in (json.nodes as object[])[0], false);
  assert.deepEqual((json.returns as object[])[1], { id: 'other', executeStatus: 'ERROR' });
  assert.deepEqual((json.edges as object[])[1], { from: 'a', to: 'c', default: true });
  assert.deepEqual(toFlowJson(toFlow(json)), json);
});
