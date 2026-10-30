import assert from 'node:assert/strict';
import test from 'node:test';
import {
  ancestry, buildEdges, buildLanes, collectIssues, entryClients, filterByClient, lanePalette, laneColors, locateValue, mergeByObject, nodeBadge, skippedViews, sourcePath,
} from './jointLayout.ts';
import type { RunLink, RunNode } from './jointRun.ts';

function node(id: string, depth: number, groupName: string, laneRank: number, subjectSortNo: number, patch: Partial<RunNode> = {}): RunNode {
  return { id, nodeId: id, depth, templateCode: id.toUpperCase(), templateName: id, versionNo: 1, dataSourceCode: 'DS', subjectKeyType: `${id}_ID`, groupName, laneRank,
    subjectSortNo, triggerKeyType: 'X', keyParameter: 'ids', keyParameterList: null, triggerValues: [], status: 'SUCCEEDED', errorCode: null, errorMessage: null,
    pagingMode: 'OFFSET', truncated: false, elapsedMillis: 1, columns: [], rows: [], warnings: [], nextCursor: null, ...patch };
}
const link = (sourceId: string, depth: number, keyType: string, value: string, rowIndex = 0): RunLink =>
  ({ sourceId, nodeId: sourceId, depth, rowIndex, column: keyType.toLowerCase(), keyType, value });

test('泳道按 laneRank 排序，泳道内按 subjectSortNo、跳数；颜色按 J1 分组顺序取色板，与入口无关', () => {
  const nodes = [
    node('attempt', 0, 'Payment', 40, 50), node('intent', 1, 'Payment', 40, 40), node('order', 2, 'Acquiring', 10, 20),
    node('checkout', 3, 'Acquiring', 10, 10), node('fact', 2, 'Billing', 100, 100), node('refund', 3, 'Acquiring', 10, 30), node('order-2', 1, 'Acquiring', 10, 20),
  ];
  const colors = laneColors([{ groupName: 'Billing', rank: 100 }, { groupName: 'Acquiring', rank: 10 }, { groupName: 'Payment', rank: 40 }], nodes);
  assert.deepEqual([...colors.entries()], [['Acquiring', lanePalette[0]], ['Payment', lanePalette[1]], ['Billing', lanePalette[2]]]);
  const lanes = buildLanes(nodes, colors);
  assert.deepEqual(lanes.map(lane => [lane.groupName, lane.color, lane.nodes.map(item => item.id)]), [
    ['Acquiring', lanePalette[0], ['checkout', 'order-2', 'order', 'refund']],
    ['Payment', lanePalette[1], ['intent', 'attempt']],
    ['Billing', lanePalette[2], ['fact']],
  ]);
  // J1 未列出的分组排在已知分组之后，按 laneRank
  const extra = laneColors([{ groupName: 'Acquiring', rank: 10 }], [node('z', 0, 'Zeta', 900, 1), node('p', 0, 'Payout', 130, 1)]);
  assert.deepEqual([...extra.keys()], ['Acquiring', 'Payout', 'Zeta']);
});

test('连线：链路值 → 下一层 triggerValues 含该值的节点；来源路径与路径文字', () => {
  const nodes = [
    node('refund', 0, 'A', 10, 30), node('order', 1, 'A', 10, 20, { triggerKeyType: 'ORDER_ID', triggerValues: ['100'] }),
    node('intent', 1, 'P', 40, 40, { triggerKeyType: 'ORDER_ID', triggerValues: ['100', '101'] }),
    node('fact', 2, 'B', 100, 100, { triggerKeyType: 'INTENT_ID', triggerValues: ['300'] }),
    node('late', 3, 'A', 10, 20, { triggerKeyType: 'ORDER_ID', triggerValues: ['100'] }),
  ];
  const links = [link('refund', 0, 'ORDER_ID', '100'), link('refund', 0, 'ORDER_ID', '101', 1), link('intent', 1, 'INTENT_ID', '300')];
  const edges = buildEdges(nodes, links);
  assert.deepEqual(edges.map(edge => [edge.id, edge.values]), [['refund->order', ['100']], ['refund->intent', ['100', '101']], ['intent->fact', ['300']]]);
  const path = ancestry('fact', edges);
  assert.deepEqual([...path.nodes].sort(), ['fact', 'intent', 'refund']);
  assert.deepEqual([...path.edges].sort(), ['intent->fact', 'refund->intent']);
  assert.deepEqual(ancestry(null, edges).nodes.size, 0);
  const lanes = buildLanes(nodes, laneColors([], nodes));
  assert.deepEqual(sourcePath('fact', nodes, edges, lanes).map(item => item.id), ['refund', 'intent', 'fact']);
});

test('节点角标与未查全汇总：失败、部分（单键截断）、未查（单步跳过与页面护栏）', () => {
  const failed = node('a', 1, 'G', 1, 1, { status: 'FAILED', errorCode: 'DATA_QUERY_TIMEOUT' });
  const skipped1 = node('b', 2, 'G', 1, 1, { status: 'SKIPPED', errorCode: 'STEP_EXECUTION_LIMIT' });
  const skipped2 = node('c', 2, 'G', 1, 1, { status: 'SKIPPED', errorCode: 'STEP_EXECUTION_LIMIT' });
  assert.deepEqual(nodeBadge(failed), { kind: 'failed' });
  assert.deepEqual(nodeBadge(skipped1), { kind: 'skipped' });
  assert.deepEqual(nodeBadge(node('e', 0, 'G', 1, 1), 2), { kind: 'entry', count: 2 });
  assert.deepEqual(nodeBadge(node('t', 1, 'G', 1, 1, { truncated: true }), 100), { kind: 'truncated', count: 100 });
  const issues = collectIssues({ nodes: [failed, skipped1, skipped2, node('ok', 0, 'G', 1, 1)],
    truncations: [{ rule: 'MAX_VALUES_PER_KEY', keyType: 'FACT_ID', dropped: 14, depth: 1 }, { rule: 'STEP_BUDGET', keyType: null, dropped: null, depth: 2 }],
    stop: { rule: 'MAX_TEMPLATE_EXECUTIONS', depth: 3, pending: [{ keyType: 'X', values: ['1'] }] } });
  assert.deepEqual(issues.map(issue => [issue.kind, issue.nodeIds, issue.rule]), [
    ['failed', ['a'], 'DATA_QUERY_TIMEOUT'], ['partial', [], 'MAX_VALUES_PER_KEY'], ['skipped', ['b', 'c'], 'STEP_EXECUTION_LIMIT'],
    ['skipped', [], 'MAX_TEMPLATE_EXECUTIONS'],
  ]);
  assert.deepEqual(collectIssues({ nodes: [], truncations: [], stop: { rule: 'USER_STOPPED', depth: 1, pending: [] } }), []);
});

test('命中多个客户：第 0 层客户维度取值；按客户筛选只保留该客户的行及其下游', () => {
  const clientColumns = [{ name: 'order_id', label: null, keyType: 'ORDER_ID', keyRole: 'LINK' as const }, { name: 'client_id', label: null, keyType: 'CLIENT_ID', keyRole: 'DIMENSION' as const }];
  const orders = node('orders', 0, 'A', 10, 20, { columns: clientColumns, rows: [['100', '1904221'], ['101', '1907718']] });
  const refunds100 = node('refunds', 1, 'A', 10, 30, { triggerKeyType: 'ORDER_ID', triggerValues: ['100'] });
  const intents = node('intents', 1, 'P', 40, 40, { triggerKeyType: 'ORDER_ID', triggerValues: ['100', '101'], columns: clientColumns, rows: [['100', '1904221'], ['101', '1907718']] });
  const nodes = [orders, refunds100, intents];
  const links = [link('orders', 0, 'ORDER_ID', '100', 0), link('orders', 0, 'ORDER_ID', '101', 1)];
  assert.deepEqual(entryClients(nodes), ['1904221', '1907718']);
  assert.deepEqual(entryClients([refunds100]), []);
  assert.equal(filterByClient(nodes, links, null), null);
  assert.deepEqual([...filterByClient(nodes, links, '1907718')!.entries()], [['orders', [1]], ['intents', [1]]]);
  assert.deepEqual([...filterByClient(nodes, links, '1904221')!.entries()], [['orders', [0]], ['refunds', []], ['intents', [0]]]);
});

test('链路值定位：优先以该键为行主体且含该值的节点，其次被该值触发的节点', () => {
  const columns = [{ name: 'posting_id', label: null, keyType: 'POSTING_ID', keyRole: 'LINK' as const }];
  const posting = node('posting', 2, 'Acc', 110, 110, { subjectKeyType: 'POSTING_ID', columns, rows: [['55']] });
  const journal = node('journal', 3, 'GL', 120, 120, { triggerKeyType: 'POSTING_ID', triggerValues: ['55'], columns, rows: [['55']] });
  assert.equal(locateValue([journal, posting], 'POSTING_ID', '55', 'journal')?.id, 'posting');
  assert.equal(locateValue([journal, posting], 'POSTING_ID', '55', 'posting')?.id, 'journal');
  assert.equal(locateValue([journal, posting], 'POSTING_ID', '56', 'journal'), null);
});

test('未执行的视图：按行主体键类型与已加载值的交集归到节点；同一模板合并；排除节点自身模板；入口 0 行显示未查到', () => {
  const columns = [{ name: 'order_id', label: null, keyType: 'ORDER_ID', keyRole: 'LINK' as const }];
  const order = node('order', 0, 'A', 10, 20, { templateCode: 'ORDER_BY_NO', subjectKeyType: 'ORDER_ID', columns, rows: [['100'], ['101']] });
  const views = skippedViews(order, [
    { templateCode: 'ORDER_BY_ID', templateName: 'Order by ID', keyType: 'ORDER_ID', values: ['100', '555'], depth: 2 },
    { templateCode: 'ORDER_BY_ID', templateName: 'Order by ID', keyType: 'ORDER_ID', values: ['101'], depth: 1 },
    { templateCode: 'ORDER_BY_NO', templateName: 'Self', keyType: 'ORDER_ID', values: ['100'], depth: 1 },
    { templateCode: 'SESSION_BY_ID', templateName: 'Session', keyType: 'SESSION_ID', values: ['100'], depth: 1 },
    { templateCode: 'OTHER', templateName: 'Other', keyType: 'ORDER_ID', values: ['999'], depth: 1 },
  ]);
  assert.deepEqual(views, [{ templateCode: 'ORDER_BY_ID', templateName: 'Order by ID', keyType: 'ORDER_ID', values: ['100', '101'], depth: 1 }]);
  assert.deepEqual(skippedViews({ ...order, status: 'FAILED' }, [{ templateCode: 'ORDER_BY_ID', templateName: 'x', keyType: 'ORDER_ID', values: ['100'], depth: 1 }]), []);
  assert.deepEqual(nodeBadge({ ...order, rows: [] }), { kind: 'entry', count: 0 });
});

test('按对象合并显示：同一行主体值只在首次出现的节点显示；全部重复的节点不绘制并记为“也可经”；链接改指向首次显示的行', () => {
  const columns = (subject: string) => [{ name: 'id', label: null, keyType: subject, keyRole: 'LINK' as const }, { name: 'next', label: null, keyType: 'NEXT_ID', keyRole: 'LINK' as const }];
  const byOrder = node('fact-by-order', 1, 'B', 100, 100, { subjectKeyType: 'FACT_ID', columns: columns('FACT_ID'), rows: [['1', 'p1'], ['2', 'p2']] });
  const byAttempt = node('fact-by-attempt', 2, 'B', 100, 100, { subjectKeyType: 'FACT_ID', columns: columns('FACT_ID'), rows: [['2', 'p2']] });
  const partial = node('fact-partial', 2, 'B', 100, 100, { subjectKeyType: 'FACT_ID', columns: columns('FACT_ID'), rows: [['1', 'p1'], ['3', 'p3']] });
  const noSubject = node('no-subject', 2, 'B', 100, 100, { subjectKeyType: 'FACT_ID', columns: [{ name: 'x', label: null, keyType: null, keyRole: null }], rows: [['a']] });
  const failed = node('failed', 2, 'B', 100, 100, { subjectKeyType: 'FACT_ID', status: 'FAILED', columns: columns('FACT_ID'), rows: [] });
  const empty = node('empty', 2, 'B', 100, 100, { subjectKeyType: 'FACT_ID', columns: columns('FACT_ID'), rows: [] });
  const other = node('posting', 2, 'C', 110, 110, { subjectKeyType: 'POSTING_ID', columns: columns('POSTING_ID'), rows: [['2', null]] });
  const target = node('next', 3, 'C', 110, 120, { triggerKeyType: 'NEXT_ID', triggerValues: ['p2'] });
  const links = [link('fact-by-order', 1, 'NEXT_ID', 'p1', 0), link('fact-by-attempt', 2, 'NEXT_ID', 'p2', 0), link('fact-partial', 2, 'NEXT_ID', 'p3', 1)];
  const merged = mergeByObject([byOrder, byAttempt, partial, noSubject, failed, empty, other, target], links);
  assert.deepEqual(merged.nodes.map(item => [item.id, item.rows.length]),
    [['fact-by-order', 2], ['fact-partial', 1], ['no-subject', 1], ['failed', 0], ['empty', 0], ['posting', 1], ['next', 0]]);
  assert.deepEqual(merged.nodes[1].rows, [['3', 'p3']]);
  assert.deepEqual(merged.also.get('fact-by-order')?.map(item => item.nodeId), ['fact-by-attempt', 'fact-partial']);
  assert.equal(merged.also.has('posting'), false, 'same value under another subject key type is another object');
  // 被合并行的链接改指向首次显示它的节点与行号；保留行的链接改为新的行号
  assert.deepEqual(merged.links.map(item => [item.sourceId, item.rowIndex, item.value]), [['fact-by-order', 0, 'p1'], ['fact-by-order', 1, 'p2'], ['fact-partial', 0, 'p3']]);
  assert.deepEqual(buildEdges(merged.nodes, merged.links).map(edge => edge.id), ['fact-by-order->next']);
  // 计数与汇总按合并后的节点：被合并的节点不再计入
  assert.equal(merged.nodes.length, 7);
  assert.deepEqual(collectIssues({ nodes: merged.nodes, truncations: [], stop: null }).map(issue => issue.nodeIds), [['failed']]);
});
