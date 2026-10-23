import assert from 'node:assert/strict';
import test from 'node:test';
import {
  addNode, applyPositions, autoLayout, changeOperator, changeVariable, connect, gatewayExits, localProblems, newGraph, normalizeGraph,
  record, redo, removeEdge, removeNode, renameNode, setDefaultExit, setEdgeClauses, setExitPriority, toCanvas, undo, variableCatalog,
  emptyHistory, insertOnEdge, insertSpacing, publishedBaseVersionNo,
} from './graphModel.ts';
import { parseGraph } from './workflowApi.ts';
import type { ActionTemplate, DefinitionDetail, VersionBody, WorkflowGraph } from './workflowTypes.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('workflow');

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
  assert.match((connect(graph, 'task2', 'task1') as { error: string }).error, /can have only one outgoing connection/);
  assert.match((connect(graph, 'gateway1', 'task1') as { error: string }).error, /can't contain a cycle/);
  assert.match((connect(graph, 'task1', 'start') as { error: string }).error, /start node can't have incoming connections/);
  assert.match((connect(graph, 'end', 'task1') as { error: string }).error, /end node can't have outgoing connections/);
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
  const canvas = toCanvas(graph, () => [{ name: 'start.paymentMethod', label: '支付方式', type: 'TEXT', source: t('graph.startForm') }], 'task1');
  assert.deepEqual(canvas.nodes.map(node => [node.id, node.type]), [['start', 'circle'], ['end', 'circle'], ['task1', 'task'], ['gateway1', 'gateway'], ['task2', 'task']]);
  assert.equal(canvas.nodes.find(node => node.id === 'task1')!.selected, true);
  assert.equal(canvas.edges.find(edge => edge.id === 'e3')!.label, 'Default');
  assert.equal(canvas.edges.find(edge => edge.id === 'e4')!.label, '支付方式 contains APPLE_PAY');
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
  assert.match((renameNode(graph, 'approve', 'e1') as { error: string }).error, /already in use/);
  assert.match((renameNode(graph, 'approve', '1bad') as { error: string }).error, /must start with a letter/);
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

test('本地检查与服务端同码同文：缺审批人且无动作、默认出口、孤立节点、到不了结束、数据查询引用', () => {
  const graph = sampleGraph();
  const withQuery = { ...graph, nodes: graph.nodes.map(node => node.key === 'task2' ? { ...node, actions: [{ key: 'ready', templateId: '9', required: true, executorId: '1' }] } : node) };
  const templates = new Map<string, ActionTemplate>([['9', { templateId: '9', templateCode: 'Q', templateName: 'Q', actionType: 'DATA_QUERY',
    description: null, status: 'ACTIVE', version: 1, updatedBy: null, updateTime: '', config: null }]]);
  const problems = localProblems(withQuery, templates);
  const find = (code: string) => problems.find(problem => problem.code === code);
  assert.deepEqual(find('TASK_EMPTY'), { code: 'TASK_EMPTY', message: t('graph.problem.TASK_EMPTY'), nodeKey: 'task1', edgeKey: null, itemKey: null });
  assert.equal(find('CONDITION_EMPTY')?.message, 'The condition group of connection e4 has no clauses.');
  assert.equal(find('CONDITION_EMPTY')?.edgeKey, 'e4');
  assert.equal(find('TEMPLATE_RESERVED_TYPE')?.itemKey, 'ready');
  const broken = addNode(graph, 'TASK', { x: 0, y: 300 }).graph;
  const brokenCodes = localProblems(broken, new Map()).map(problem => `${problem.code}:${problem.nodeKey}`);
  assert.ok(brokenCodes.includes('NODE_UNREACHABLE:task3'));
  assert.ok(brokenCodes.includes('NODE_CANNOT_REACH_END:task3'));
  assert.ok(brokenCodes.includes('TASK_OUTGOING:task3'));
  assert.deepEqual(localProblems({ nodes: [], edges: [] }, new Map()).map(problem => [problem.code, problem.message]),
    [['START_COUNT', 'There must be exactly one start node; there are 0.'], ['END_MISSING', 'At least one end node is required.']]);
  // 排他网关缺默认出口 / 不带条件的出边
  const noDefault = { ...graph, nodes: graph.nodes.map(node => node.key === 'gateway1' ? { ...node, defaultEdgeKey: undefined } : node) };
  const gatewayProblems = localProblems(noDefault, new Map()).filter(problem => problem.nodeKey === 'gateway1');
  assert.deepEqual(gatewayProblems.map(problem => problem.code).sort(), ['CONDITION_EMPTY', 'EXCLUSIVE_DEFAULT_MISSING', 'EXCLUSIVE_EDGE_UNUSED']);
  assert.equal(gatewayProblems.find(problem => problem.code === 'EXCLUSIVE_DEFAULT_MISSING')!.message, t('graph.problem.EXCLUSIVE_DEFAULT_MISSING'));
});

test('本地检查：动作缺执行人、节点缺名称、已停用的通知触发', () => {
  let graph = sampleGraph();
  graph = { ...graph, nodes: graph.nodes.map(node => node.key === 'task1' ? { ...node, name: ' ', approval: { mode: 'ANY' as const, approverIds: ['7'] },
    actions: [{ key: 'profile', templateId: '1', required: true }], notifications: [{ trigger: 'VERIFICATION_FAILED' as const, type: 'NOTIFY' as const, recipients: ['STARTER' as const] }] } : node) };
  const problems = localProblems(graph, new Map());
  assert.ok(problems.some(problem => problem.code === 'EXECUTOR_MISSING' && problem.itemKey === 'profile' && problem.message === t('graph.problem.EXECUTOR_MISSING')));
  assert.ok(problems.some(problem => problem.code === 'NODE_NAME_MISSING' && problem.nodeKey === 'task1'));
  assert.ok(problems.some(problem => problem.code === 'NOTIFICATION_TRIGGER_INVALID' && problem.itemKey === 'notifications[0]'));
  assert.equal(problems.some(problem => problem.code === 'TASK_EMPTY' && problem.nodeKey === 'task1'), false);
});

/** start → fork ⇉ (left, right) ⇉ join → end。 */
function parallelGraph(): WorkflowGraph {
  let graph = newGraph();
  const fork = addNode(graph, 'PARALLEL_SPLIT', { x: 100, y: 0 }); graph = fork.graph;
  const left = addNode(graph, 'TASK', { x: 200, y: -60 }); graph = left.graph;
  const right = addNode(graph, 'TASK', { x: 200, y: 60 }); graph = right.graph;
  const join = addNode(graph, 'PARALLEL_JOIN', { x: 300, y: 0 }); graph = join.graph;
  for (const [from, to] of [['start', fork.key], [fork.key, left.key], [fork.key, right.key], [left.key, join.key], [right.key, join.key], [join.key, 'end']]) {
    graph = edgeOrFail(connect(graph, from, to)).graph;
  }
  return { ...graph, nodes: graph.nodes.map(node => node.type === 'TASK' ? { ...node, approval: { mode: 'ANY' as const, approverIds: ['1'] } } : node) };
}

test('本地检查：并行分叉与汇合配对、分支内结束节点', () => {
  const paired = parallelGraph();
  assert.deepEqual(localProblems(paired, new Map(), { fields: [] }), []);
  // 右分支改连到结束：分叉不成对、结束节点在分支内、汇合只剩一条入边
  const edge = paired.edges.find(item => item.from === 'task2' && item.to === 'join1')!;
  const broken = { ...paired, edges: paired.edges.map(item => item.key === edge.key ? { ...item, to: 'end' } : item) };
  const codes = localProblems(broken, new Map()).map(problem => `${problem.code}:${problem.nodeKey}`);
  assert.ok(codes.includes('PARALLEL_END_INSIDE:end'));
  assert.ok(codes.includes('PARALLEL_JOIN_INCOMING:join1'));
  assert.ok(codes.includes('PARALLEL_UNPAIRED:join1'));
  // 两个分支汇入不同的汇合
  let split = paired;
  const other = addNode(split, 'PARALLEL_JOIN', { x: 300, y: 120 }); split = other.graph;
  split = { ...split, edges: split.edges.map(item => item.key === edge.key ? { ...item, to: other.key } : item) };
  split = edgeOrFail(connect(split, other.key, 'end')).graph;
  const message = localProblems(split, new Map()).find(problem => problem.code === 'PARALLEL_UNPAIRED' && problem.nodeKey === 'fork1')?.message;
  assert.equal(message, 'All branches of a parallel split must lead into the same parallel join; they currently lead into [join1, join2].');
});

test('本地检查：条件与输入映射引用不可用的变量；模板详情未读到时跳过', () => {
  let graph = setEdgeClauses(sampleGraph(), 'gateway1', 'e4', [{ variable: 'start.country', operator: 'EQ', value: 'SA' }]);
  const form = { fields: [{ key: 'channelCode', label: '渠道', type: 'TEXT' as const, required: true }] };
  const unavailable = localProblems(graph, new Map(), form).filter(problem => problem.code === 'VARIABLE_UNAVAILABLE');
  assert.equal(unavailable.length, 1);
  assert.equal(unavailable[0].edgeKey, 'e4');
  assert.equal(unavailable[0].message, t('graph.problem.VARIABLE_UNAVAILABLE', { name: 'start.country', node: 'gateway1' }));
  // 上游动作的模板还没读到详情：profile.* 跳过
  graph = setEdgeClauses(graph, 'gateway1', 'e4', [{ variable: 'profile.currency', operator: 'EQ', value: 'USD' }]);
  graph = { ...graph, nodes: graph.nodes.map(node => node.key === 'task1' ? { ...node, actions: [{ key: 'profile', templateId: '1', required: true, executorId: '1' }] } : node) };
  assert.equal(localProblems(graph, new Map(), form).some(problem => problem.code === 'VARIABLE_UNAVAILABLE'), false);
  const template: ActionTemplate = { templateId: '1', templateCode: 'P', templateName: '资料', actionType: 'FORM', description: null, status: 'ACTIVE', version: 1,
    updatedBy: null, updateTime: '', config: { fields: [{ key: 'amount', label: '金额', type: 'NUMBER', required: true }] } };
  assert.equal(localProblems(graph, new Map([['1', template]]), form).filter(problem => problem.code === 'VARIABLE_UNAVAILABLE').length, 1);
  // 输入映射同样检查
  graph = { ...graph, nodes: graph.nodes.map(node => node.key === 'task2' ? { ...node, actions: [{ key: 'page', templateId: '1', required: true, executorId: '1',
    inputs: { merchantName: { source: 'VARIABLE' as const, value: 'start.unknown' } } }] } : node) };
  assert.ok(localProblems(graph, new Map([['1', template]]), form).some(problem => problem.code === 'VARIABLE_UNAVAILABLE' && problem.itemKey === 'page'));
});

test('连线上插入节点：A→B 拆成 A→新→B，原条件留在前一段，后一段不带条件', () => {
  let graph = setEdgeClauses(sampleGraph(), 'gateway1', 'e4', [{ variable: 'start.country', operator: 'EQ', value: 'SA' }]);
  const result = insertOnEdge(graph, 'e4', 'TASK');
  if ('error' in result) throw new Error(result.error);
  graph = result.graph;
  assert.equal(result.key, 'task3');
  assert.deepEqual(graph.edges.filter(edge => edge.key === 'e4' || edge.from === 'task3'), [{ key: 'e4', from: 'gateway1', to: 'task3' }, { key: 'e6', from: 'task3', to: 'task2' }]);
  const gateway = graph.nodes.find(node => node.key === 'gateway1')!;
  assert.deepEqual(gateway.conditions, [{ edgeKey: 'e4', all: [{ variable: 'start.country', operator: 'EQ', value: 'SA' }] }]);
  assert.equal(gateway.defaultEdgeKey, 'e3');
  // 插在默认出口上：默认留在前一段
  const onDefault = insertOnEdge(graph, 'e3', 'TASK');
  if ('error' in onDefault) throw new Error(onDefault.error);
  assert.equal(onDefault.graph.nodes.find(node => node.key === 'gateway1')!.defaultEdgeKey, 'e3');
  assert.equal(onDefault.graph.edges.find(edge => edge.key === 'e3')!.to, onDefault.key);
});

test('连线上插入节点：排他网关的新出边成为默认出口；两端太近时右移终点；不能插入开始或结束', () => {
  const graph = sampleGraph();
  const result = insertOnEdge(graph, 'e1', 'EXCLUSIVE');
  if ('error' in result) throw new Error(result.error);
  const inserted = result.graph.nodes.find(node => node.key === result.key)!;
  const exit = result.graph.edges.find(edge => edge.from === result.key)!;
  assert.equal(exit.to, 'task1');
  assert.equal(inserted.defaultEdgeKey, exit.key);
  assert.deepEqual(inserted.conditions, []);
  // start(0) → task1(200)：两段各需 insertSpacing（280），task1 及其右侧右移 360 到 560，新节点在 280；连接点与开始节点对齐（17 − 25）
  const x = (key: string) => result.graph.nodes.find(node => node.key === key)!.position.x;
  assert.equal(insertSpacing, 280);
  assert.equal(x('start'), 0);
  assert.equal(inserted.position.x, 280);
  assert.equal(x('task1'), 560);
  assert.equal(x('gateway1'), 760);
  assert.equal(inserted.position.y, -8);
  assert.match((insertOnEdge(graph, 'e1', 'END') as { error: string }).error, /Only task nodes or gateways can be inserted/);
});

test('连线上插入节点：间距足够时放中点且不移动；不同行时取中点；终点不在右侧时不移动其他节点', () => {
  let graph = newGraph();
  let added = addNode(graph, 'TASK', { x: 700, y: -9 }); graph = added.graph; const far = added.key;
  added = addNode(graph, 'TASK', { x: 1100, y: 300 }); graph = added.graph; const below = added.key;
  graph = edgeOrFail(connect(graph, 'start', far)).graph;
  graph = edgeOrFail(connect(graph, far, below)).graph;
  graph = edgeOrFail(connect(graph, below, 'end')).graph;
  const position = (value: WorkflowGraph, key: string) => value.nodes.find(node => node.key === key)!.position;
  // start(0) → far(700)：间距 ≥ 560，中点 350，不移动；连接点同一行（17 与 -9 + 26），新任务与开始节点对齐
  const wide = insertOnEdge(graph, 'e1', 'TASK');
  if ('error' in wide) throw new Error(wide.error);
  assert.deepEqual(position(wide.graph, wide.key), { x: 350, y: -9 });
  assert.deepEqual(position(wide.graph, far), { x: 700, y: -9 });
  // far(700) → below(1100, 300)：间距 400 不足，below 及右侧右移 160；不同行，取两端中间
  const down = insertOnEdge(graph, 'e2', 'TASK');
  if ('error' in down) throw new Error(down.error);
  assert.deepEqual(position(down.graph, down.key), { x: 980, y: 146 });
  assert.equal(position(down.graph, below).x, 1260);
  assert.ok(position(down.graph, below).x - position(down.graph, down.key).x >= insertSpacing);
  // below(1100) → end(440)：终点在左侧（回绕），只取中点，不移动
  const back = insertOnEdge(graph, 'e3', 'TASK');
  if ('error' in back) throw new Error(back.error);
  assert.equal(position(back.graph, back.key).x, 770);
  assert.deepEqual(back.graph.nodes.filter(node => node.key !== back.key).map(node => node.position), graph.nodes.map(node => node.position));
  assert.match((insertOnEdge(graph, 'nope', 'TASK') as { error: string }).error, /doesn't exist/);
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
  assert.ok(codes.includes('GRAPH_TOO_LARGE'));
  assert.ok(codes.includes('START_FORM_TOO_LARGE'));
  assert.deepEqual(localProblems(newGraph(), new Map(), { fields: [] }).filter(problem => problem.code.endsWith('_TOO_LARGE')), []);
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
