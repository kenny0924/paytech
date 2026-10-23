import assert from 'node:assert/strict';
import test from 'node:test';
import {
  cardMenuItems, definitionCardStatus, editorStatusText, filterDefinitions, instructionLine, loadThumbnail, monthDay, primaryCardAction, problemTarget,
  runningInstanceNote, thumbnailRequestId, thumbnailSource, versionChanges,
} from './definitionModel.ts';
import { addNode, connect, newGraph, setEdgeClauses } from './graphModel.ts';
import { DETAIL_PRIORITY, DetailQueue } from './prefetchModel.ts';
import type { DefinitionDetail, DefinitionSummary, VersionBody, WorkflowGraph } from './workflowTypes.ts';

function summary(patch: Partial<DefinitionSummary> = {}): DefinitionSummary {
  return { definitionId: '1', definitionCode: 'MERCHANT_ONBOARD', definitionName: '商户入驻审核', description: null, status: 'ACTIVE',
    currentVersionNo: 3, draftVersionNo: null, version: 7, updateTime: '2026-10-21T08:00:00Z', ...patch };
}

test('卡片状态四选一：已停用 > 有未发布修改 > 仅草稿 > 已发布', () => {
  assert.deepEqual(definitionCardStatus(summary({ status: 'INACTIVE', draftVersionNo: 4 })), { kind: 'INACTIVE', label: '已停用', tone: 'muted' });
  assert.deepEqual(definitionCardStatus(summary({ currentVersionNo: 2, draftVersionNo: 3 })),
    { kind: 'PUBLISHED_WITH_DRAFT', label: '已发布 v2 · 有未发布修改', tone: 'warn' });
  assert.deepEqual(definitionCardStatus(summary({ currentVersionNo: null, draftVersionNo: 1 })), { kind: 'DRAFT_ONLY', label: '草稿 · 未发布', tone: 'draft' });
  assert.deepEqual(definitionCardStatus(summary()), { kind: 'PUBLISHED', label: '已发布 v3', tone: 'ok' });
});

test('主按钮与「⋯」菜单：已发布发起、仅草稿继续编辑、已停用启用；停用在最后且只对启用中的流程', () => {
  const all = { canStart: true, canManage: true };
  assert.equal(primaryCardAction(summary(), all), 'LAUNCH');
  assert.equal(primaryCardAction(summary({ draftVersionNo: 4 }), all), 'LAUNCH');
  assert.equal(primaryCardAction(summary({ currentVersionNo: null, draftVersionNo: 1 }), all), 'CONTINUE');
  assert.equal(primaryCardAction(summary({ status: 'INACTIVE' }), all), 'ENABLE');
  assert.equal(primaryCardAction(summary(), { canStart: false, canManage: true }), 'EDIT');
  assert.equal(primaryCardAction(summary({ status: 'INACTIVE' }), { canStart: true, canManage: false }), 'VIEW');
  assert.deepEqual(cardMenuItems(summary(), all), ['EDIT', 'VERSIONS', 'DISABLE']);
  assert.deepEqual(cardMenuItems(summary({ currentVersionNo: null, draftVersionNo: 1 }), all), ['VERSIONS', 'DISABLE']);
  assert.deepEqual(cardMenuItems(summary({ status: 'INACTIVE' }), all), ['EDIT', 'VERSIONS']);
  assert.deepEqual(cardMenuItems(summary(), { canStart: true, canManage: false }), ['EDIT', 'VERSIONS']);
});

test('列表前端筛选：已发布含有未发布修改，搜索名称或编码，按最近修改倒序，数量随搜索变化', () => {
  const items = [
    summary({ definitionId: 'a', definitionName: '商户入驻审核', updateTime: '2026-10-21T00:00:00Z' }),
    summary({ definitionId: 'b', definitionName: '渠道上线', definitionCode: 'CHANNEL_ONLINE', currentVersionNo: 2, draftVersionNo: 3, updateTime: '2026-10-22T00:00:00Z' }),
    summary({ definitionId: 'c', definitionName: '商户信息变更', currentVersionNo: null, draftVersionNo: 1, updateTime: '2026-10-20T00:00:00Z' }),
    summary({ definitionId: 'd', definitionName: '保证金退还', status: 'INACTIVE', updateTime: '2026-09-28T00:00:00Z' }),
  ];
  const all = filterDefinitions(items, 'ALL', '');
  assert.deepEqual(all.counts, { ALL: 4, PUBLISHED: 2, DRAFT: 1, INACTIVE: 1 });
  assert.deepEqual(all.visible.map(item => item.definitionId), ['b', 'a', 'c', 'd']);
  assert.deepEqual(filterDefinitions(items, 'PUBLISHED', '').visible.map(item => item.definitionId), ['b', 'a']);
  const searched = filterDefinitions(items, 'ALL', 'channel');
  assert.deepEqual(searched.visible.map(item => item.definitionId), ['b']);
  assert.deepEqual(searched.counts, { ALL: 1, PUBLISHED: 1, DRAFT: 0, INACTIVE: 0 });
  assert.deepEqual(filterDefinitions(items, 'DRAFT', '商户').visible.map(item => item.definitionId), ['c']);
});

test('更新于 MM-DD：本年省略年份', () => {
  const now = new Date(2026, 9, 23);
  assert.equal(monthDay(new Date(2026, 9, 21, 12).toISOString(), now), '10-21');
  assert.equal(monthDay(new Date(2025, 0, 2, 12).toISOString(), now), '2025-01-02');
  assert.equal(monthDay(null, now), '—');
});

function version(versionNo: number, graph: WorkflowGraph, status: VersionBody['status'] = 'PUBLISHED'): VersionBody {
  return { versionNo, status, graph, startForm: { fields: [] }, actionSnapshot: null, publishedBy: null, publishedTime: null, updateTime: '' };
}
function detailOf(currentVersionNo: number | null, draft: VersionBody | null, versions: VersionBody[] = []): DefinitionDetail {
  return { definition: summary({ currentVersionNo, draftVersionNo: draft?.versionNo ?? null }), draft, problems: [], versions, people: {} };
}

test('缩略图：有发布版本读当前发布版本，只有草稿读草稿图；每张卡一次请求', async () => {
  const published = addNode(newGraph(), 'TASK', { x: 200, y: 0 }).graph;
  const draft = newGraph();
  const calls: string[] = [];
  const fetchers = {
    detail: async (id: string) => { calls.push(`detail:${id}`); return detailOf(null, version(1, draft, 'DRAFT')); },
    version: async (id: string, versionNo: number) => { calls.push(`version:${id}:${versionNo}`); return version(versionNo, published); },
  };
  assert.deepEqual(thumbnailSource(summary()), { kind: 'VERSION', versionNo: 3 });
  assert.equal(await loadThumbnail(summary(), fetchers), published);
  assert.equal(await loadThumbnail(summary({ definitionId: '2', currentVersionNo: null, draftVersionNo: 1 }), fetchers), draft);
  assert.deepEqual(calls, ['version:1:3', 'detail:2']);
  // 版本号变化后重新读取
  assert.notEqual(thumbnailRequestId(summary()), thumbnailRequestId(summary({ version: 8 })));
});

test('缩略图经 DetailQueue 串行读取：同时最多 1 个在途，版本历史（选中优先级）插队到缩略图之前，429 退避重试一次', async () => {
  let inFlight = 0; let maxInFlight = 0; const order: string[] = []; let failedOnce = false;
  const releases: (() => void)[] = [];
  const queue = new DetailQueue<string>(async id => {
    inFlight++; maxInFlight = Math.max(maxInFlight, inFlight); order.push(id);
    await new Promise<void>(resolve => releases.push(resolve));
    inFlight--;
    if (id === 'c' && !failedOnce) { failedOnce = true; throw Object.assign(new Error('429'), { status: 429 }); }
    return id;
  }, { retryDelay: error => (error as { status?: number }).status === 429 ? 0 : null, sleep: async () => undefined });
  const results = ['a', 'b', 'c'].map(id => queue.request(id, DETAIL_PRIORITY.PREFETCH, `thumb:${id}`));
  const versions = queue.request('detail:x', DETAIL_PRIORITY.SELECTED, 'versions');
  const drain = async () => { while (releases.length || queue.running() || queue.pending().length) { await new Promise(resolve => setTimeout(resolve, 0)); releases.shift()?.(); } };
  await drain();
  assert.deepEqual(await Promise.all(results), ['a', 'b', 'c']);
  assert.equal(await versions, 'detail:x');
  assert.equal(maxInFlight, 1);
  assert.deepEqual(order, ['a', 'detail:x', 'b', 'c', 'c']);
});

test('编辑器状态药丸：新建 / 草稿基于 vN / 与 vN 相同 / 只读版本', () => {
  assert.equal(editorStatusText({ currentVersionNo: null, hasDraft: true, dirty: false }), '新建 · 未发布');
  assert.equal(editorStatusText({ currentVersionNo: 2, hasDraft: true, dirty: false }), '草稿 · 基于 v2，未发布');
  assert.equal(editorStatusText({ currentVersionNo: 2, hasDraft: false, dirty: true }), '草稿 · 基于 v2，未发布');
  assert.equal(editorStatusText({ currentVersionNo: 2, hasDraft: false, dirty: false }), '与 v2 相同');
  assert.equal(editorStatusText({ currentVersionNo: 2, hasDraft: true, dirty: false, viewingVersion: 1 }), 'v1 · 只读');
});

function edge(result: ReturnType<typeof connect>) { if ('error' in result) throw new Error(result.error); return result; }

/** v2：start → fill → gateway ─默认→ final → end；gateway ─条件→ end。 */
function baseGraph(): WorkflowGraph {
  let graph = newGraph();
  graph = { ...graph, nodes: [...graph.nodes,
    { key: 'fill', type: 'TASK', name: '填写商户资料', position: { x: 200, y: 0 }, actions: [{ key: 'profile', templateId: 't1', required: true }], notifications: [] },
    { key: 'gateway', type: 'EXCLUSIVE', name: '是否跨境', position: { x: 400, y: 0 }, conditions: [] },
    { key: 'final', type: 'TASK', name: '上线终审', position: { x: 600, y: 0 }, approval: { mode: 'ANY', approverIds: ['1'] }, notifications: [] }] };
  for (const [from, to] of [['start', 'fill'], ['fill', 'gateway'], ['gateway', 'final'], ['gateway', 'end'], ['final', 'end']]) graph = edge(connect(graph, from, to)).graph;
  return setEdgeClauses(graph, 'gateway', 'e4', [{ variable: 'start.crossBorder', operator: 'EQ', value: 'false' }]);
}

test('变更摘要：节点增删改名、连线条件与增删、启动表单字段增删、动作增删、审批人变化', () => {
  const base = { graph: baseGraph(), startForm: { fields: [{ key: 'merchantName', label: '商户名称', type: 'TEXT' as const, required: true }] } };
  let graph = baseGraph();
  graph = addNode(graph, 'TASK', { x: 500, y: -100 }).graph;
  graph = { ...graph, nodes: graph.nodes.map(node => node.key === 'task1' ? { ...node, name: '合规审核' }
    : node.key === 'final' ? { ...node, name: '上线复核', approval: { mode: 'ANY' as const, approverIds: ['1', '2'] } }
    : node.key === 'fill' ? { ...node, actions: [{ key: 'license', templateId: 't2', required: true }] } : node) };
  graph = setEdgeClauses(graph, 'gateway', 'e4', [{ variable: 'start.crossBorder', operator: 'EQ', value: 'true' }]);
  graph = { ...graph, edges: graph.edges.filter(item => item.key !== 'e5') };
  graph = edge(connect(graph, 'final', 'task1')).graph;
  const draft = { graph, startForm: { fields: [{ key: 'region', label: '经营地区', type: 'TEXT' as const, required: false }] } };
  const names: Record<string, string> = { t1: '商户资料表单', t2: '营业执照上传' };
  const changes = versionChanges(base, draft, id => names[id] ?? id);
  const rows = changes.map(change => `${change.kind} ${change.action} ${change.subject}`);
  assert.deepEqual(rows, [
    'CHANGED 节点改名 上线终审 → 上线复核',
    'ADDED 新增节点 合规审核',
    'CHANGED 修改连线条件 是否跨境 → 结束：start.crossBorder 等于 true',
    'REMOVED 删除连线 上线复核 → 结束',
    'ADDED 「填写商户资料」新增动作 营业执照上传',
    'REMOVED 「填写商户资料」移除动作 商户资料表单',
    'CHANGED 修改审批人 上线复核',
    'ADDED 启动表单新增字段 经营地区',
    'REMOVED 启动表单删除字段 商户名称',
  ]);
  assert.equal(changes.find(change => change.action === '新增节点')!.nodeKey, 'task1');
  assert.equal(changes.find(change => change.action === '修改连线条件')!.edgeKey, 'e4');
  assert.deepEqual(versionChanges(base, base, id => id), []);
});

test('变更摘要：在连线上插入节点只报新增节点，不重复报连线增删', () => {
  const base = { graph: baseGraph(), startForm: { fields: [] } };
  let graph = baseGraph();
  const added = addNode(graph, 'TASK', { x: 100, y: 0 }); graph = added.graph;
  graph = { ...graph, edges: graph.edges.map(item => item.key === 'e1' ? { ...item, to: added.key } : item) };
  graph = { ...graph, edges: [...graph.edges, { key: 'e9', from: added.key, to: 'fill' }] };
  const changes = versionChanges(base, { graph, startForm: { fields: [] } }, id => id);
  assert.deepEqual(changes.map(change => change.action), ['新增节点', '删除连线']);
});

test('发布确认的运行中实例提示：读取失败、没有发布版本或没有运行中实例时不显示', () => {
  assert.equal(runningInstanceNote(12, 1, 2), '已发起的 12 个运行中实例继续按 v1 运行，新发起的使用 v2');
  // 运行中实例可能来自 v1、v2 等不同版本，不能都说成按当前发布版本运行
  assert.equal(runningInstanceNote(12, 2, 3), '已发起的 12 个运行中实例继续按各自发起时的版本运行，新发起的使用 v3');
  assert.equal(runningInstanceNote(null, 2, 3), null);
  assert.equal(runningInstanceNote(0, 2, 3), null);
  assert.equal(runningInstanceNote(5, null, 1), null);
});

test('问题位置名与动作说明首行', () => {
  const graph = baseGraph();
  assert.equal(problemTarget({ code: 'TASK_EMPTY', nodeKey: 'final', edgeKey: null, itemKey: null, message: '' }, graph), '上线终审');
  assert.equal(problemTarget({ code: 'CONDITION_EMPTY', nodeKey: 'gateway', edgeKey: 'e4', itemKey: null, message: '' }, graph), '是否跨境 → 结束');
  assert.equal(problemTarget({ code: 'STRUCTURE_INVALID', nodeKey: null, edgeKey: null, itemKey: 'name', message: '' }, graph), '启动表单');
  assert.equal(problemTarget({ code: 'START_COUNT', nodeKey: null, edgeKey: null, itemKey: null, message: '' }, graph), '流程');
  assert.equal(instructionLine({ actionType: 'FILE_UPLOAD', config: { instruction: '请上传营业执照\n第二行' }, description: '描述' }), '请上传营业执照');
  assert.equal(instructionLine({ actionType: 'FORM', config: { fields: [] }, description: '请补全商户主体信息' }), '请补全商户主体信息');
  assert.equal(instructionLine({ actionType: 'MANUAL_CONFIRM', config: { instruction: '  ' }, description: null }), '');
});
