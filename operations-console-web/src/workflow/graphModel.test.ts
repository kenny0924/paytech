import assert from 'node:assert/strict';
import test from 'node:test';
import {
  addNode, applyPositions, autoLayout, changeOperator, changeVariable, connect, gatewayExits, localProblems, newGraph, normalizeGraph,
  record, redo, removeEdge, removeNode, renameNode, setDefaultExit, setEdgeClauses, setExitPriority, toCanvas, undo, variableCatalog,
  emptyHistory, publishedBaseVersionNo,
} from './graphModel.ts';
import { parseGraph } from './workflowApi.ts';
import type { ActionTemplate, DefinitionDetail, VersionBody, WorkflowGraph } from './workflowTypes.ts';

function edgeOrFail(result: ReturnType<typeof connect>) {
  if ('error' in result) throw new Error(result.error);
  return result;
}

/** start → approve → gateway ─(e?)→ applePay → end；默认 → end。 */
function sampleGraph(): WorkflowGraph {
  let graph = newGraph();
  let added = addNode(graph, 'TASK', { x: 200, y: 0 }); graph = added.graph; const task = added.key;
  added = addNode(graph, 'EXCLUSIVE', { x: 400, y: 0 }); graph = added.graph; const gateway = added.key;
  added = addNode(graph, 'TASK', { x: 600, y: -100 }); graph = added.graph; const apple = added.key;
  graph = edgeOrFail(connect(graph, 'start', task)).graph;
  graph = edgeOrFail(connect(graph, task, gateway)).graph;
  graph = edgeOrFail(connect(graph, gateway, 'end')).graph;
  graph = edgeOrFail(connect(graph, gateway, apple)).graph;
  graph = edgeOrFail(connect(graph, apple, 'end')).graph;
  return graph;
}

test('新建节点 key 不与节点、连线、动作重复，连线规则拒绝自连、回边、多出边和开始入边', () => {
  const graph = sampleGraph();
  assert.deepEqual(graph.nodes.map(node => node.key), ['start', 'end', 'task1', 'gateway1', 'task2']);
  assert.deepEqual(graph.edges.map(edge => edge.key), ['e1', 'e2', 'e3', 'e4', 'e5']);
  assert.equal('error' in connect(graph, 'task1', 'task1'), true);
  assert.match((connect(graph, 'task2', 'task1') as { error: string }).error, /只能有一条出边/);
  assert.match((connect(graph, 'gateway1', 'task1') as { error: string }).error, /不能有环/);
  assert.match((connect(graph, 'task1', 'start') as { error: string }).error, /开始节点不能有入边/);
  assert.match((connect(graph, 'end', 'task1') as { error: string }).error, /结束节点不能有出边/);
});

test('排他网关第一条出边为默认出口，后续出边按顺序成为条件出口；删除连线同步清理条件', () => {
  let graph = sampleGraph();
  const gateway = graph.nodes.find(node => node.key === 'gateway1')!;
  assert.equal(gateway.defaultEdgeKey, 'e3');
  assert.deepEqual(gateway.conditions, [{ edgeKey: 'e4', all: [] }]);
  graph = setEdgeClauses(graph, 'gateway1', 'e4', [{ variable: 'start.paymentMethod', operator: 'EQ', value: 'APPLE_PAY' }]);
  assert.deepEqual(gatewayExits(graph, 'gateway1').map(exit => [exit.edge.key, exit.priority, exit.isDefault]), [['e4', 1, false], ['e3', null, true]]);
  graph = setDefaultExit(graph, 'gateway1', 'e4');
  assert.deepEqual(gatewayExits(graph, 'gateway1').map(exit => [exit.edge.key, exit.isDefault]), [['e3', false], ['e4', true]]);
  graph = removeEdge(graph, 'e4');
  const after = graph.nodes.find(node => node.key === 'gateway1')!;
  assert.equal(after.defaultEdgeKey, undefined);
  assert.deepEqual(after.conditions, [{ edgeKey: 'e3', all: [] }]);
});

test('调整条件出口优先级并删除节点时去掉相连连线', () => {
  let graph = sampleGraph();
  const third = addNode(graph, 'TASK', { x: 600, y: 100 }); graph = third.graph;
  graph = edgeOrFail(connect(graph, 'gateway1', third.key)).graph;
  graph = setExitPriority(graph, 'gateway1', 'e6', 1);
  assert.deepEqual(gatewayExits(graph, 'gateway1').filter(exit => !exit.isDefault).map(exit => exit.edge.key), ['e6', 'e4']);
  graph = removeNode(graph, 'task2');
  assert.equal(graph.edges.some(edge => edge.from === 'task2' || edge.to === 'task2'), false);
  assert.deepEqual(graph.nodes.find(node => node.key === 'gateway1')!.conditions!.map(item => item.edgeKey), ['e6']);
  assert.equal(removeNode(graph, 'start'), graph);
});

test('graph_json 与画布互转：节点类型映射、条件标签、拖动位置写回、序列化保持结构', () => {
  let graph = setEdgeClauses(sampleGraph(), 'gateway1', 'e4', [{ variable: 'start.paymentMethod', operator: 'CONTAINS', value: 'APPLE_PAY' }]);
  const canvas = toCanvas(graph, () => [{ name: 'start.paymentMethod', label: '支付方式', type: 'TEXT', source: '启动表单' }], 'task1');
  assert.deepEqual(canvas.nodes.map(node => [node.id, node.type]), [['start', 'circle'], ['end', 'circle'], ['task1', 'task'], ['gateway1', 'gateway'], ['task2', 'task']]);
  assert.equal(canvas.nodes.find(node => node.id === 'task1')!.selected, true);
  assert.equal(canvas.edges.find(edge => edge.id === 'e3')!.label, '默认');
  assert.equal(canvas.edges.find(edge => edge.id === 'e4')!.label, '支付方式 包含 APPLE_PAY');
  assert.equal(canvas.edges.find(edge => edge.id === 'e1')!.label, undefined);
  graph = applyPositions(graph, [{ id: 'task1', position: { x: 210.6, y: 40.2 } }, { id: 'end' }]);
  assert.deepEqual(graph.nodes.find(node => node.key === 'task1')!.position, { x: 211, y: 40 });
  const saved = normalizeGraph(graph);
  const reloaded = parseGraph(JSON.parse(JSON.stringify(saved)));
  assert.deepEqual(reloaded.nodes.map(node => node.key), graph.nodes.map(node => node.key));
  assert.deepEqual(reloaded.edges, graph.edges);
  assert.deepEqual(reloaded.nodes.find(node => node.key === 'gateway1')!.conditions, [{ edgeKey: 'e4', all: [{ variable: 'start.paymentMethod', operator: 'CONTAINS', value: 'APPLE_PAY' }] }]);
  assert.deepEqual(saved.settings, { notifications: [] });
  assert.equal('approval' in saved.nodes.find(node => node.key === 'task1')!, false);
});

test('判空条件保存时不带值，修改节点 key 同步连线', () => {
  let graph = setEdgeClauses(sampleGraph(), 'gateway1', 'e4', [{ variable: 'start.files', operator: 'IS_EMPTY', value: 'x' }]);
  assert.deepEqual(normalizeGraph(graph).nodes.find(node => node.key === 'gateway1')!.conditions![0].all[0], { variable: 'start.files', operator: 'IS_EMPTY' });
  const renamed = renameNode(graph, 'task1', 'approve');
  if ('error' in renamed) throw new Error(renamed.error);
  graph = renamed.graph;
  assert.deepEqual(graph.edges.filter(edge => edge.from === 'approve' || edge.to === 'approve').map(edge => edge.key), ['e1', 'e2']);
  assert.match((renameNode(graph, 'approve', 'e1') as { error: string }).error, /已被使用/);
  assert.match((renameNode(graph, 'approve', '1bad') as { error: string }).error, /字母开头/);
});

test('条件组编辑：换比较符转换值形态，换变量保留可用比较符，文件只能判空', () => {
  const clause = { variable: 'start.country', operator: 'EQ' as const, value: 'SA' };
  assert.deepEqual(changeOperator(clause, 'IN'), { variable: 'start.country', operator: 'IN', value: ['SA'] });
  assert.deepEqual(changeOperator({ ...clause, operator: 'IN', value: ['SA', 'AE'] }, 'EQ'), { variable: 'start.country', operator: 'EQ', value: 'SA' });
  assert.deepEqual(changeOperator(clause, 'IS_NOT_EMPTY'), { variable: 'start.country', operator: 'IS_NOT_EMPTY' });
  assert.deepEqual(changeVariable(clause, { name: 'start.files', label: '合同', type: 'FILE', source: '' }), { variable: 'start.files', operator: 'IS_EMPTY' });
  assert.deepEqual(changeVariable(clause, { name: 'start.method', label: '支付方式', type: 'ENUM', source: '' }), { variable: 'start.method', operator: 'EQ', value: 'SA' });
});

test('可引用变量：启动表单 + 上游动作输出；并行另一分支的输出不可见', () => {
  let graph = newGraph();
  const fork = addNode(graph, 'PARALLEL_SPLIT', { x: 100, y: 0 }); graph = fork.graph;
  const left = addNode(graph, 'TASK', { x: 200, y: -60 }); graph = left.graph;
  const right = addNode(graph, 'TASK', { x: 200, y: 60 }); graph = right.graph;
  const join = addNode(graph, 'PARALLEL_JOIN', { x: 300, y: 0 }); graph = join.graph;
  const after = addNode(graph, 'TASK', { x: 400, y: 0 }); graph = after.graph;
  for (const [from, to] of [['start', fork.key], [fork.key, left.key], [fork.key, right.key], [left.key, join.key], [right.key, join.key], [join.key, after.key], [after.key, 'end']]) {
    graph = edgeOrFail(connect(graph, from, to)).graph;
  }
  graph = { ...graph, nodes: graph.nodes.map(node => node.key === left.key ? { ...node, actions: [{ key: 'profile', templateId: '1', required: true }] } : node) };
  const templates = new Map<string, ActionTemplate>([['1', { templateId: '1', templateCode: 'PROFILE', templateName: '资料', actionType: 'FORM',
    description: null, status: 'ACTIVE', version: 1, updatedBy: null, updateTime: '', config: { fields: [{ key: 'currency', label: '币种', type: 'ENUM', required: true }] } }]]);
  const form = { fields: [{ key: 'channelCode', label: '渠道编码', type: 'TEXT' as const, required: true }] };
  assert.deepEqual(variableCatalog(graph, form, templates, right.key).map(item => item.name), ['start.channelCode']);
  assert.deepEqual(variableCatalog(graph, form, templates, after.key).map(item => item.name), ['start.channelCode', 'profile.currency']);
});

test('本地检查覆盖开始结束、可达、任务出边与内容、默认出口、数据查询引用', () => {
  const graph = sampleGraph();
  const withQuery = { ...graph, nodes: graph.nodes.map(node => node.key === 'task2' ? { ...node, actions: [{ key: 'ready', templateId: '9', required: true, executorId: '1' }] } : node) };
  const templates = new Map<string, ActionTemplate>([['9', { templateId: '9', templateCode: 'Q', templateName: 'Q', actionType: 'DATA_QUERY',
    description: null, status: 'ACTIVE', version: 1, updatedBy: null, updateTime: '', config: null }]]);
  const codes = localProblems(withQuery, templates).map(problem => problem.code);
  assert.ok(codes.includes('LOCAL_TASK_EMPTY'));
  assert.ok(codes.includes('LOCAL_CONDITION_EMPTY'));
  assert.ok(codes.includes('LOCAL_DATA_QUERY'));
  const broken = addNode(graph, 'TASK', { x: 0, y: 300 }).graph;
  const brokenCodes = localProblems(broken, new Map()).map(problem => `${problem.code}:${problem.nodeKey}`);
  assert.ok(brokenCodes.includes('LOCAL_UNREACHABLE:task3'));
  assert.ok(brokenCodes.includes('LOCAL_DEAD_END:task3'));
  assert.ok(brokenCodes.includes('LOCAL_TASK_EXIT:task3'));
  assert.deepEqual(localProblems({ nodes: [], edges: [] }, new Map()).map(problem => problem.code), ['LOCAL_START_COUNT', 'LOCAL_END_COUNT']);
});

test('自动布局按层从左到右，撤销与重做恢复图', () => {
  const graph = autoLayout(sampleGraph());
  const x = (key: string) => graph.nodes.find(node => node.key === key)!.position.x;
  assert.ok(x('start') < x('task1') && x('task1') < x('gateway1') && x('gateway1') < x('task2') && x('task2') < x('end'));
  const changed = removeNode(graph, 'task2');
  const history = record(emptyHistory, graph);
  const undone = undo(history, changed)!;
  assert.equal(undone.graph, graph);
  assert.equal(redo(undone.history, undone.graph)!.graph, changed);
  assert.equal(undo(emptyHistory, graph), null);
});

test('本地检查同步大小上限：graph_json 48 KiB、start_form_json 12 KiB', () => {
  let graph = newGraph();
  graph = { ...graph, nodes: graph.nodes.map(node => node.key === 'end' ? { ...node, name: 'x'.repeat(50 * 1024) } : node) };
  const longForm = { fields: [{ key: 'note', label: '说明'.repeat(7000), type: 'TEXT' as const, required: false }] };
  const codes = localProblems(graph, new Map(), longForm).map(problem => problem.code);
  assert.ok(codes.includes('LOCAL_GRAPH_SIZE'));
  assert.ok(codes.includes('LOCAL_FORM_SIZE'));
  assert.deepEqual(localProblems(newGraph(), new Map(), { fields: [] }).filter(problem => problem.code.endsWith('_SIZE')), []);
});

test('没有草稿时以当前发布版本为底稿，缺省取最新发布版本；有草稿时不读取版本', () => {
  const version = (versionNo: number, status: VersionBody['status']): VersionBody => ({ versionNo, status, graph: { nodes: [], edges: [] },
    startForm: { fields: [] }, actionSnapshot: null, publishedBy: null, publishedTime: null, updateTime: '2026-10-22T00:00:00Z' });
  const detail = (currentVersionNo: number | null, draft: VersionBody | null, versions: VersionBody[]): DefinitionDetail => ({
    definition: { definitionId: '1', definitionCode: 'D', definitionName: 'D', description: null, status: 'ACTIVE', currentVersionNo,
      draftVersionNo: draft?.versionNo ?? null, version: 1, updateTime: '2026-10-22T00:00:00Z' }, draft, problems: [], versions, people: {} });
  assert.equal(publishedBaseVersionNo(detail(2, null, [version(1, 'PUBLISHED'), version(2, 'PUBLISHED'), version(3, 'PUBLISHED')])), 2);
  assert.equal(publishedBaseVersionNo(detail(null, null, [version(1, 'PUBLISHED'), version(3, 'PUBLISHED')])), 3);
  assert.equal(publishedBaseVersionNo(detail(2, version(3, 'DRAFT'), [version(2, 'PUBLISHED'), version(3, 'DRAFT')])), undefined);
  assert.equal(publishedBaseVersionNo(detail(null, null, [])), undefined);
});
