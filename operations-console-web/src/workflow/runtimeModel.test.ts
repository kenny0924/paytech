import assert from 'node:assert/strict';
import test from 'node:test';
import {
  batchEligibility, batchStatus, batchSubmission, batchSummary, currentHandlers, decisionError, decisionOptions, evaluateClause, latestRuns, neighborTask, nextAfterDone, nodeApproverIds,
  pageTaskLink, previewPath, previousRoundAction, progressOf, resolveShortcut, revisionOf, runBatch, runtimeVariables, traversedEdges,
  visualState, waitingText,
} from './runtimeModel.ts';
import { changeSummary, diffOutputs, duplicateField, fieldProblems, moveField, newField, outputOf, valueProblems } from './formModel.ts';
import { configDiff, configProblems, defaultConfig, frozenConfig, normalizeConfig, referenceSummary } from './templateModel.ts';
import type { ActionRun, Approval, InstanceDetail, NodeRun, Task, WorkflowGraph } from './workflowTypes.ts';

const run = (nodeKey: string, roundNo: number, status: NodeRun['status'], extra: Partial<NodeRun> = {}): NodeRun => ({
  nodeRunId: `${nodeKey}-${roundNo}`, nodeKey, nodeType: 'TASK', roundNo, branchKey: null, previousNodeRunId: null, status,
  selectedEdgeKey: null, activatedTime: `2026-10-2${roundNo}T09:00:00Z`, completedTime: null, rejectKind: null, rejectTargets: null, ...extra });
const action = (nodeRunId: string, actionKey: string, status: ActionRun['status'], output: Record<string, unknown> | null, assigneeId = '7'): ActionRun => ({
  actionRunId: `${nodeRunId}-${actionKey}`, nodeRunId, actionKey, actionType: 'PAGE_TASK', required: true, assigneeId, status, output,
  submittedBy: output ? assigneeId : null, submittedTime: null });
const approval = (nodeRunId: string, status: Approval['status'], extra: Partial<Approval> = {}): Approval => ({
  approvalId: `${nodeRunId}-a`, nodeRunId, approverId: '8', status, comment: null, rejectTargetNodeKey: null, decidedTime: null, ...extra });

const graph: WorkflowGraph = {
  nodes: [
    { key: 'start', type: 'START', name: '开始', position: { x: 0, y: 0 } },
    { key: 'approve', type: 'TASK', name: '立项审批', position: { x: 0, y: 0 }, approval: { mode: 'ANY', approverIds: ['8'] }, actions: [] },
    { key: 'gate', type: 'EXCLUSIVE', name: '含 Apple Pay？', position: { x: 0, y: 0 }, defaultEdgeKey: 'e4',
      conditions: [{ edgeKey: 'e3', all: [{ variable: 'start.paymentMethod', operator: 'EQ', value: 'APPLE_PAY' }] }] },
    { key: 'cert', type: 'TASK', name: '上传 Apple Pay 证书', position: { x: 0, y: 0 }, actions: [{ key: 'certFiles', templateId: '3', required: true }] },
    { key: 'risk', type: 'TASK', name: '风控：配置规则', position: { x: 0, y: 0 }, actions: [{ key: 'riskRule', templateId: '2', required: true,
      inputs: { channelCode: { source: 'VARIABLE', value: 'start.channelCode' }, country: { source: 'CONSTANT', value: 'SA' } } }] },
    { key: 'end', type: 'END', name: '结束', position: { x: 0, y: 0 } },
  ],
  edges: [{ key: 'e1', from: 'start', to: 'approve' }, { key: 'e2', from: 'approve', to: 'gate' }, { key: 'e3', from: 'gate', to: 'cert' },
    { key: 'e4', from: 'gate', to: 'risk' }, { key: 'e5', from: 'cert', to: 'risk' }, { key: 'e6', from: 'risk', to: 'end' }],
};

function detail(overrides: Partial<InstanceDetail> = {}): InstanceDetail {
  return { instance: { instanceId: '1', definitionId: '2', title: '上线 Geidea', businessKey: null, status: 'RUNNING', startedBy: '9', startedByName: 'ops.lily',
    startTime: '', endTime: null }, definitionCode: 'NEW', definitionName: '新渠道上线', versionNo: 2, version: 3, terminatedBy: null, terminationReason: null,
  graph, startForm: { fields: [] }, startValues: { channelCode: 'GEIDEA', paymentMethod: 'CARD' }, actionSnapshot: null,
  nodeRuns: [], actionRuns: [], verifications: [], approvals: [], transfers: [], nodeApprovers: {}, people: { 7: 'ops.wang', 8: 'ops.wu' }, files: [], ...overrides };
}

test('节点着色取最新一轮；排他网关只点亮选中的出口；进度与处理人', () => {
  const runs = [run('start', 1, 'PASSED', { nodeType: 'START' }), run('approve', 1, 'PASSED'), run('gate', 1, 'PASSED', { nodeType: 'EXCLUSIVE', selectedEdgeKey: 'e4' }),
    run('risk', 1, 'RETURNED'), run('risk', 2, 'ACTIVE')];
  assert.equal(latestRuns(runs).get('risk')!.roundNo, 2);
  assert.equal(visualState(latestRuns(runs).get('risk')), 'active');
  assert.equal(visualState(undefined), 'pending');
  assert.equal(visualState(run('x', 1, 'REJECTED')), 'rejected');
  assert.deepEqual([...traversedEdges(graph, runs)].sort(), ['e1', 'e2', 'e4']);
  const value = detail({ nodeRuns: runs, actionRuns: [action('risk-2', 'riskRule', 'PENDING', null)] });
  assert.deepEqual(progressOf(value), { completed: 1, active: 1, pending: 1, total: 3 });
  assert.deepEqual(currentHandlers(value).map(item => [item.nodeKey, item.stage, item.people]), [['risk', 'ACTION', ['ops.wang']]]);
  // 我发起的时间线：本人显示为“你”
  assert.deepEqual(currentHandlers(value, '7')[0].people, ['你']);
  assert.deepEqual(currentHandlers(value, '99')[0].people, ['ops.wang']);
});

test('运行时变量取启动值与最新一轮已完成输出；页面任务拼接站内路由与参数', () => {
  const value = detail({ nodeRuns: [run('risk', 1, 'RETURNED'), run('risk', 2, 'APPROVING')],
    actionRuns: [action('risk-1', 'riskRule', 'VERIFIED', { note: 'v1' }), action('risk-2', 'riskRule', 'COMPLETED', { note: 'v2' })] });
  const variables = runtimeVariables(value);
  assert.equal(variables['start.channelCode'], 'GEIDEA');
  assert.equal(variables['riskRule.note'], 'v2');
  const config = { pageRoute: '/payment/routes', parameters: [{ key: 'channelCode', label: '渠道' }, { key: 'country', label: '国家' }, { key: 'missing', label: '缺' }] };
  assert.equal(pageTaskLink(config, graph.nodes[4].actions![0].inputs, variables), '/payment/routes?channelCode=GEIDEA&country=SA');
  assert.equal(pageTaskLink({ pageRoute: 'https://evil.test/' }, undefined, {}), null);
  assert.equal(pageTaskLink({ pageRoute: '//evil.test' }, undefined, {}), null);
});

test('退回修改后的新一轮能找到上一轮输出与退回意见，并标出差异', () => {
  const value = detail({ nodeRuns: [run('risk', 1, 'RETURNED'), run('risk', 2, 'ACTIVE')],
    actionRuns: [action('risk-1', 'riskRule', 'VERIFIED', { note: '规则集已发布' }), action('risk-2', 'riskRule', 'PENDING', null)],
    approvals: [approval('risk-1', 'RETURNED', { comment: '补充阈值说明', decidedTime: '2026-10-21T17:40:00Z' })] });
  const current = value.actionRuns[1];
  assert.deepEqual(previousRoundAction(value, current)?.output, { note: '规则集已发布' });
  const revision = revisionOf(value, value.nodeRuns[1])!;
  assert.equal(revision.kind, 'RETURNED');
  assert.equal(revision.approval.comment, '补充阈值说明');
  const changes = diffOutputs({ note: '规则集已发布', files: ['1'] }, { note: '规则集已发布，新增阈值', files: ['2'] });
  assert.deepEqual(changes.map(item => [item.key, item.kind]), [['note', 'changed'], ['files', 'changed']]);
  assert.equal(changeSummary(changes, { note: '确认说明', files: '文件' }), '确认说明已修改 · 文件 +1 −1');
  assert.equal(changeSummary([]), '与上一轮相同');
  assert.equal(revisionOf(value, value.nodeRuns[0]), null);
});

test('审批决定可用性：同一人限制、纯审批不能退回修改、首节点与分支首节点不能驳回、汇合后驳回需选分支', () => {
  const pending = approval('risk-2', 'PENDING');
  const base = { approval: pending, hasActions: true, submittedByMe: false, instanceRunning: true };
  const enabled = (options: ReturnType<typeof decisionOptions>) => options.options.map(item => item.enabled);
  assert.deepEqual(enabled(decisionOptions({ ...base, nodeRun: run('risk', 2, 'APPROVING', { rejectKind: 'PREVIOUS_TASK' }) })), [true, true, true]);
  const branchFirst = decisionOptions({ ...base, nodeRun: run('risk', 2, 'APPROVING', { rejectKind: 'BRANCH_FIRST_NODE' }) });
  assert.deepEqual(enabled(branchFirst), [true, true, false]);
  assert.match(branchFirst.options[2].reason, /并行分支的第一个节点/);
  const pureApproval = decisionOptions({ ...base, hasActions: false, nodeRun: run('approve', 1, 'APPROVING', { rejectKind: 'FIRST_TASK' }) });
  assert.deepEqual(enabled(pureApproval), [true, false, false]);
  assert.match(pureApproval.options[1].reason, /纯审批节点/);
  const self = decisionOptions({ ...base, submittedByMe: true, nodeRun: run('risk', 2, 'APPROVING') });
  assert.deepEqual(enabled(self), [false, false, false]);
  const join = decisionOptions({ ...base, nodeRun: run('review', 1, 'APPROVING', { rejectKind: 'CHOOSE_BRANCH', rejectTargets: ['risk', 'channel'] }) });
  assert.deepEqual(join.rejectTargets, ['risk', 'channel']);
  assert.equal(decisionError(join, 'REJECT', '有问题'), '请选择驳回到哪一条分支');
  assert.equal(decisionError(join, 'REJECT', '有问题', 'risk'), null);
  assert.equal(decisionError(join, 'RETURN', ' '), '退回修改和驳回必须填写意见');
  assert.equal(decisionError(join, 'APPROVE', ''), null);
  assert.equal(decisionError(pureApproval, 'RETURN', '意见'), '本节点没有动作，纯审批节点不能退回修改');
});

test('批量同意只收无动作的待审批，逐项调用并逐项返回结果', async () => {
  const task = (taskId: string, nodeKey: string): Task => ({ taskType: 'APPROVAL', taskId, instanceId: '1', instanceTitle: 't', nodeKey, roundNo: 1, actionKey: null, waitingSince: '' });
  const value = detail({ approvals: [{ ...approval('approve-1', 'PENDING'), approvalId: 'a1' }, { ...approval('risk-1', 'PENDING'), approvalId: 'a2' }] });
  assert.deepEqual(batchEligibility(task('a1', 'approve'), value, '8'), { eligible: true, reason: '' });
  assert.match(batchEligibility(task('a2', 'risk'), value, '8').reason, /含 1 个动作/);
  assert.equal(batchEligibility(task('a1', 'approve'), undefined, '8').eligible, false);
  assert.equal(batchEligibility({ ...task('a1', 'approve'), taskType: 'ACTION' }, value, '8').eligible, false);
  assert.deepEqual(batchStatus(task('a1', 'approve'), undefined, '8', false), { state: 'CHECKING', reason: '检查中' });
  assert.deepEqual(batchStatus(task('a1', 'approve'), undefined, '8', true), { state: 'UNCHECKABLE', reason: '无法检查，请单独处理' });
  assert.deepEqual(batchStatus(task('a1', 'approve'), value, '8', false), { state: 'ELIGIBLE', reason: '' });
  assert.equal(batchStatus(task('a1', 'approve'), value, '8', true).state, 'ELIGIBLE');
  assert.equal(batchStatus(task('a2', 'risk'), value, '8', false).state, 'INELIGIBLE');
  assert.equal(batchStatus({ ...task('a1', 'approve'), taskType: 'ACTION' }, value, '8', false).state, 'INELIGIBLE');
  const statuses = new Map([['t1', { state: 'ELIGIBLE' as const, reason: '' }], ['t2', { state: 'CHECKING' as const, reason: '检查中' }],
    ['t3', { state: 'UNCHECKABLE' as const, reason: '无法检查，请单独处理' }], ['t4', { state: 'INELIGIBLE' as const, reason: '已被处理' }]]);
  assert.deepEqual(batchSubmission(['t1', 't2', 't3', 't4'], statuses), { ready: false, checking: ['t2'], submit: ['t1'], excluded: ['t3', 't4'] });
  assert.equal(batchSubmission(['t1', 't3'], statuses).ready, true);
  assert.equal(batchSubmission(['t3', 't4'], statuses).ready, false);
  assert.deepEqual(batchSubmission(['t9'], statuses).checking, ['t9']);
  // 汇总按冻结列表与结果计数：列表刷新后已同意项消失也不会出现负数。
  const frozenResults = new Map([['t1', { taskId: 't1', ok: true, message: '已同意' }], ['t2', { taskId: 't2', ok: false, message: '已被他人处理' }]]);
  assert.deepEqual(batchSummary(['t1', 't2', 't3', 't4'], frozenResults), { approved: 1, failed: 1, skipped: 2 });
  assert.deepEqual(batchSummary(['t1'], frozenResults), { approved: 1, failed: 0, skipped: 0 });
  assert.deepEqual(batchSummary([], frozenResults), { approved: 0, failed: 0, skipped: 0 });
  const calls: string[] = []; const seen: string[] = [];
  const results = await runBatch(['a1', 'a3', 'a4'], async id => { calls.push(id); if (id === 'a3') throw new Error('已被他人处理'); },
    error => (error as Error).message, result => seen.push(result.taskId));
  assert.deepEqual(calls, ['a1', 'a3', 'a4']);
  assert.deepEqual(seen, ['a1', 'a3', 'a4']);
  assert.deepEqual(results.map(item => [item.taskId, item.ok, item.message]), [['a1', true, '已同意'], ['a3', false, '已被他人处理'], ['a4', true, '已同意']]);
});

test('待办切换与快捷键', () => {
  const tasks = ['1', '2', '3'].map(taskId => ({ taskId }) as Task);
  assert.equal(neighborTask(tasks, '2', 1)!.taskId, '3');
  assert.equal(neighborTask(tasks, '3', 1), null);
  assert.equal(neighborTask(tasks, '1', -1), null);
  assert.equal(neighborTask(tasks, null, 1)!.taskId, '1');
  assert.equal(nextAfterDone(tasks, '2')!.taskId, '3');
  assert.equal(nextAfterDone(tasks, '3')!.taskId, '2');
  assert.equal(nextAfterDone([tasks[0]], '1'), null);
  const key = (value: string, extra: Partial<Parameters<typeof resolveShortcut>[0]> = {}) =>
    resolveShortcut({ key: value, ctrlKey: false, metaKey: false, altKey: false, editable: false, taskType: 'APPROVAL', ...extra });
  assert.equal(key('j'), 'NEXT');
  assert.equal(key('K'), 'PREVIOUS');
  assert.equal(key('a'), 'APPROVE');
  assert.equal(key('r'), 'RETURN');
  assert.equal(key('t'), 'TRANSFER');
  assert.equal(key('a', { taskType: 'ACTION' }), null);
  assert.equal(key('j', { editable: true }), null);
  assert.equal(key('Enter', { ctrlKey: true, editable: true }), 'SUBMIT');
  assert.equal(key('Enter', { metaKey: true }), 'SUBMIT');
  assert.equal(key('Enter'), null);
  assert.equal(key('r', { metaKey: true }), null);
  assert.equal(key('t', { taskType: null }), null);
  assert.equal(waitingText('2026-10-20T00:00:00Z', Date.parse('2026-10-22T01:00:00Z')), '2 天');
  assert.equal(waitingText('2026-10-22T00:00:00Z', Date.parse('2026-10-22T05:10:00Z')), '5 小时');
});

test('发起预览：按启动值选择排他分支，条件引用动作输出时标为运行时决定', () => {
  const names = (values: Record<string, unknown>) => previewPath(graph, values).map(step => step.kind === 'task' ? step.node.key : step.kind);
  assert.deepEqual(names({ paymentMethod: 'APPLE_PAY' }), ['approve', 'cert', 'risk']);
  assert.deepEqual(names({ paymentMethod: 'CARD' }), ['approve', 'risk']);
  const runtime: WorkflowGraph = { ...graph, nodes: graph.nodes.map(node => node.key === 'gate'
    ? { ...node, conditions: [{ edgeKey: 'e3', all: [{ variable: 'approve.note', operator: 'IS_NOT_EMPTY' }] }] } : node) };
  assert.deepEqual(previewPath(runtime, {}).map(step => step.kind), ['task', 'undecided']);
  assert.equal(evaluateClause({ variable: 'x', operator: 'IN', value: ['SA', 'AE'] }, 'AE'), true);
  assert.equal(evaluateClause({ variable: 'x', operator: 'GTE', value: '10' }, '9.5'), false);
  assert.equal(evaluateClause({ variable: 'x', operator: 'IS_EMPTY' }, []), true);
  assert.equal(evaluateClause({ variable: 'x', operator: 'CONTAINS', value: 'PAY' }, 'APPLE_PAY'), true);
  assert.equal(evaluateClause({ variable: 'x', operator: 'EQ', value: 'A' }, undefined), false);
});

test('表单字段设计与填写校验', () => {
  const form = { fields: [
    { key: 'name', label: '名称', type: 'TEXT' as const, required: true, rules: { maxLength: 5 } },
    { key: 'days', label: '结算周期', type: 'NUMBER' as const, required: true, rules: { min: '1', max: '90', scale: 0 } },
    { key: 'currency', label: '币种', type: 'ENUM' as const, required: false, rules: { options: [{ value: 'USD', label: '美元' }] } },
    { key: 'goLive', label: '上线日', type: 'DATE' as const, required: false },
    { key: 'refund', label: '退款', type: 'BOOLEAN' as const, required: false },
  ] };
  // 需求 E1：旧配置的长度、范围与小数位不再检查，只查必填与平台技术保护（类型、平台长度上限）。
  assert.deepEqual(valueProblems(form, { name: '超过五个字符了', days: '120', currency: 'EUR', goLive: '2026/11/15', refund: 'yes' }),
    { currency: '不是可选值', goLive: '必须是 YYYY-MM-DD 日期', refund: '必须是是或否' });
  assert.deepEqual(valueProblems(form, { name: '', days: 'abc' }), { name: '必填', days: '必须是数字' });
  assert.deepEqual(valueProblems(form, { name: 'x'.repeat(256), days: '1.5' }), { name: '不能超过 255 个字符' });
  assert.deepEqual(valueProblems(form, { name: 'ok', days: '30', refund: false }), {});
  assert.deepEqual(outputOf(form, { name: 'ok', days: '30', currency: '', refund: false }), { name: 'ok', days: '30', refund: false });
  const duplicated = duplicateField({ fields: [form.fields[0], { ...form.fields[0], key: 'name' }] }, 0);
  assert.deepEqual(duplicated.fields.map(field => field.key), ['name', 'name2', 'name']);
  assert.match(fieldProblems(duplicated).get(2)!, /重复/);
  assert.deepEqual(moveField(form, 0, 1).fields.map(field => field.key).slice(0, 2), ['days', 'name']);
  const files = { fields: [{ key: 'f', label: '合同', type: 'FILE' as const, required: true, rules: { minFiles: 2, maxFiles: 3 } }] };
  assert.deepEqual(valueProblems(files, { f: ['/files/v1/11'] }), {}, '旧配置 minFiles 不再检查');
  assert.deepEqual(valueProblems(files, { f: ['/files/v1/11', '12'] }), { f: '文件地址无效，请重新上传' });
  assert.deepEqual(valueProblems(files, { f: ['/files/v1/11', '/files/v1/11'] }), { f: '文件重复' });
  assert.deepEqual(valueProblems(files, { f: ['/files/v1/11', '/files/v1/12'] }), {});
  assert.deepEqual(valueProblems(files, {}), { f: '必填' });
});

test('动作模板配置校验、引用汇总与配置对比', () => {
  assert.deepEqual(configProblems('PAGE_TASK', { pageRoute: 'https://x.test', parameters: [] }), ['目标页面只允许以 / 开头的控制台内部路由，不接受外部地址']);
  assert.deepEqual(configProblems('FILE_UPLOAD', { minFiles: 3, maxFiles: 2, mimeTypes: ['pdf'] }), [], '数量与类型不再配置');
  assert.deepEqual(configProblems('MANUAL_CONFIRM', { instruction: '确认' }), []);
  assert.deepEqual(normalizeConfig('FILE_UPLOAD', { minFiles: 1, maxFiles: 3, mimeTypes: ['application/pdf'], instruction: '上传执照' }), { required: true, instruction: '上传执照' });
  assert.deepEqual(normalizeConfig('FILE_UPLOAD', { minFiles: 0 }), { required: false, instruction: '' });
  assert.deepEqual(normalizeConfig('MANUAL_CONFIRM', { instruction: '线下签约', noteRequired: true }), { instruction: '线下签约' });
  assert.deepEqual(normalizeConfig('FORM', { fields: [{ key: 'n', label: 'N', type: 'TEXT', required: true, rules: { maxLength: 5 } },
    { key: 'c', label: 'C', type: 'ENUM', required: false, rules: { options: [{ value: 'A', label: 'a' }], maxLength: 3 } }] }),
  { fields: [{ key: 'n', label: 'N', type: 'TEXT', required: true }, { key: 'c', label: 'C', type: 'ENUM', required: false, rules: { options: [{ value: 'A', label: 'a' }] } }] });
  assert.deepEqual(defaultConfig('FILE_UPLOAD'), { required: true, instruction: '' });
  assert.deepEqual(newField({ fields: [] }, 'TEXT').rules, undefined);
  assert.deepEqual(newField({ fields: [] }, 'FILE').rules, undefined);
  const current = { fields: [{ key: 'days', label: '周期', type: 'NUMBER' as const, required: true, rules: { min: '1', max: '90' } },
    { key: 'goLive', label: '上线日', type: 'DATE' as const, required: true }] };
  const frozen = { fields: [{ key: 'days', label: '周期', type: 'NUMBER' as const, required: true, rules: { min: '1', max: '60' } }] };
  const references = { templateId: '5', currentTemplateVersion: 4, items: [
    { definitionId: '1', definitionCode: 'NEW', definitionName: '新渠道上线', versionNo: 3, currentVersion: true, frozenTemplateVersion: 4, sameAsCurrent: true, runningInstanceCount: 12 },
    { definitionId: '1', definitionCode: 'NEW', definitionName: '新渠道上线', versionNo: 2, currentVersion: false, frozenTemplateVersion: 3, sameAsCurrent: false, runningInstanceCount: 2 },
    { definitionId: '2', definitionCode: 'CHG', definitionName: '渠道资料变更', versionNo: 1, currentVersion: true, frozenTemplateVersion: 4, sameAsCurrent: true, runningInstanceCount: 0 }] };
  assert.deepEqual(referenceSummary(references), { definitions: 2, versions: 3, outdated: 1, running: 14, runningOnOutdated: 2 });
  const version = { versionNo: 2, status: 'PUBLISHED' as const, startForm: { fields: [] }, graph: { nodes: [], edges: [] }, publishedBy: null, publishedTime: null, updateTime: '',
    actionSnapshot: { templates: [{ templateId: '5', templateVersion: 3, templateCode: null, templateName: null, actionType: 'FORM' as const, config: frozen }] } };
  assert.deepEqual(frozenConfig(version, '5'), frozen);
  assert.equal(frozenConfig(version, '9'), null);
  assert.deepEqual(configDiff('FORM', frozen, current).map(item => [item.kind, item.label]), [['added', '上线日'], ['changed', '周期']]);
  assert.deepEqual(configDiff('MANUAL_CONFIRM', { instruction: 'a', noteRequired: true }, { instruction: 'b', noteRequired: true }).map(item => item.label), ['instruction']);
});

test('审批人按冻结的节点配置判断：审批行生成前也显示生效审批人，无审批配置的节点为空', () => {
  const base = detail({ nodeRuns: [run('approve', 1, 'ACTIVE')] });
  assert.deepEqual(nodeApproverIds(base, 'approve', 'approve-1'), ['8']);
  assert.deepEqual(nodeApproverIds({ ...base, nodeApprovers: { approve: ['11', '12'] } }, 'approve', 'approve-1'), ['11', '12']);
  const opened = detail({ nodeApprovers: { approve: ['11'] }, approvals: [approval('approve-1', 'PENDING', { approverId: '13' })] });
  assert.deepEqual(nodeApproverIds(opened, 'approve', 'approve-1'), ['13']);
  assert.deepEqual(nodeApproverIds(base, 'cert', 'cert-1'), []);
});
