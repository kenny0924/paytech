import assert from 'node:assert/strict';
import test from 'node:test';
import {
  OVERDUE_HOURS, actionProgress, anyScanPending, batchCandidates, batchIdle, completionMode, completionProblems, defaultExpandedAction, durationText,
  enterBatch, exitBatch, groupCounts, groupTasks, handledResult, neighborGroup, nextGroupAfterDone, orderNodeActions, outputFieldErrors, overdueText,
  progressText, relativeTime, submissionSummary, taskGroupKey, taskSteps, toggleAll, toggleChecked, visibleProblems, waitingForMe, waitingOn,
} from './taskModel.ts';
import { actionState } from './runtimeModel.ts';
import type { BatchStatus } from './runtimeModel.ts';
import { actionRunStatusLabels } from './workflowLabels.ts';
import type { ActionRun, InstanceSummary, NodeRun, Task, WorkflowFile, WorkflowGraph } from './workflowTypes.ts';

const HOUR = 3_600_000;
const now = new Date('2026-10-22T12:00:00').getTime();
const ago = (hours: number) => new Date(now - hours * HOUR).toISOString();

test('超时：达到 24 小时才标红，满 1 天按天；未到阈值返回 null', () => {
  assert.equal(OVERDUE_HOURS, 24);
  assert.equal(overdueText(ago(0), now), null);
  assert.equal(overdueText(ago(23.9), now), null);
  assert.equal(overdueText(ago(24), now), '超时 1 天');
  assert.equal(overdueText(ago(50), now), '超时 2 天');
  assert.equal(overdueText(ago(5), now, 4), '超时 5 小时');
  assert.equal(overdueText('not a time', now), null);
  assert.equal(relativeTime(ago(0.1), now), '6 分钟前');
  assert.equal(relativeTime(ago(3), now), '3 小时前');
  assert.equal(relativeTime(new Date('2026-10-21T16:40:00').toISOString(), now), '昨天 16:40');
  assert.equal(relativeTime(new Date('2026-10-19T08:00:00').toISOString(), now), '10-19');
  assert.equal(durationText(ago(98), null, now), '4 天');
  assert.equal(durationText(ago(10), ago(8), now), '2 小时');
});

const node = (key: string, type: WorkflowGraph['nodes'][number]['type'], name = key) => ({ key, type, name, position: { x: 0, y: 0 } });
const run = (nodeKey: string, status: NodeRun['status'], extra: Partial<NodeRun> = {}): NodeRun => ({
  nodeRunId: `${nodeKey}-1`, nodeKey, nodeType: 'TASK', roundNo: 1, branchKey: null, previousNodeRunId: null, status, selectedEdgeKey: null,
  activatedTime: ago(1), completedTime: null, rejectKind: null, rejectTargets: null, ...extra });

const branching: WorkflowGraph = {
  nodes: [node('s', 'START', '开始'), node('fill', 'TASK', '填写资料'), node('gw', 'EXCLUSIVE', '是否跨境'), node('local', 'TASK', '境内审核'),
    node('cross', 'TASK', '跨境审核'), node('final', 'TASK', '终审'), node('e', 'END', '结束')],
  edges: [{ key: 'e1', from: 's', to: 'fill' }, { key: 'e2', from: 'fill', to: 'gw' }, { key: 'e3', from: 'gw', to: 'local' }, { key: 'e4', from: 'gw', to: 'cross' },
    { key: 'e5', from: 'local', to: 'final' }, { key: 'e6', from: 'cross', to: 'final' }, { key: 'e7', from: 'final', to: 'e' }],
};

test('进度条沿主路径：只显示走过的分支；网关未走到时以“…”结束', () => {
  const before = taskSteps(branching, [run('s', 'PASSED', { nodeType: 'START' }), run('fill', 'ACTIVE')]);
  assert.deepEqual(before.map(step => [step.name, step.state]), [['开始', 'done'], ['填写资料', 'current'], ['…', 'pending']]);
  const after = taskSteps(branching, [run('s', 'PASSED', { nodeType: 'START' }), run('fill', 'PASSED'),
    run('gw', 'PASSED', { nodeType: 'EXCLUSIVE', selectedEdgeKey: 'e4' }), run('cross', 'APPROVING')]);
  assert.deepEqual(after.map(step => [step.name, step.state]),
    [['开始', 'done'], ['填写资料', 'done'], ['跨境审核', 'current'], ['终审', 'pending'], ['结束', 'pending']]);
  const parallel: WorkflowGraph = {
    nodes: [node('s', 'START', '开始'), node('split', 'PARALLEL_SPLIT'), node('a', 'TASK', '风控'), node('b', 'TASK', '财务'), node('join', 'PARALLEL_JOIN'),
      node('z', 'TASK', '上线'), node('e', 'END', '结束')],
    edges: [{ key: '1', from: 's', to: 'split' }, { key: '2', from: 'split', to: 'a' }, { key: '3', from: 'split', to: 'b' }, { key: '4', from: 'a', to: 'join' },
      { key: '5', from: 'b', to: 'join' }, { key: '6', from: 'join', to: 'z' }, { key: '7', from: 'z', to: 'e' }],
  };
  assert.deepEqual(taskSteps(parallel, [run('s', 'PASSED'), run('a', 'PASSED'), run('b', 'ACTIVE')]).map(step => [step.name, step.state]),
    [['开始', 'done'], ['风控', 'done'], ['财务', 'current'], ['上线', 'pending'], ['结束', 'pending']]);
});

const action = (extra: Partial<ActionRun>): ActionRun => ({ actionRunId: '1', nodeRunId: 'n', actionKey: 'a', actionType: 'FORM', required: true, assigneeId: '7',
  status: 'PENDING', output: null, submittedBy: null, submittedTime: null, ...extra });

test('状态归一：COMPLETED 与旧 VERIFIED 为已完成，旧 UNVERIFIED 为待处理；标签为待处理 / 已完成 / 已取消', () => {
  assert.equal(actionState('COMPLETED'), 'COMPLETED');
  assert.equal(actionState('VERIFIED'), 'COMPLETED');
  assert.equal(actionState('UNVERIFIED'), 'PENDING');
  assert.equal(actionState('CANCELLED'), 'CANCELLED');
  assert.equal(actionState('SOMETHING_NEW'), 'PENDING');
  assert.deepEqual([actionRunStatusLabels.PENDING, actionRunStatusLabels.COMPLETED, actionRunStatusLabels.CANCELLED], ['待处理', '已完成', '已取消']);
  assert.equal(actionRunStatusLabels.VERIFIED, '已完成');
  assert.equal(actionRunStatusLabels.UNVERIFIED, '待处理');
  assert.deepEqual([handledResult('COMPLETED').label, handledResult('VERIFIED').label, handledResult('APPROVED').label, handledResult('RETURNED').label,
    handledResult('REJECTED').label], ['已完成', '已完成', '已同意', '已退回', '已驳回']);
});

test('完成资格：必选动作进入审批后锁定，可选动作到放行前可完成；已完成可修改', () => {
  const active = { status: 'ACTIVE' as const }; const approving = { status: 'APPROVING' as const };
  assert.equal(completionMode(action({}), active, true, '7'), 'COMPLETE');
  assert.equal(completionMode(action({ status: 'COMPLETED' }), active, true, '7'), 'EDIT');
  assert.equal(completionMode(action({ status: 'VERIFIED' }), active, true, '7'), 'EDIT');
  assert.equal(completionMode(action({ status: 'COMPLETED' }), approving, true, '7'), 'LOCKED');
  assert.equal(completionMode(action({ required: false }), approving, true, '7'), 'COMPLETE');
  assert.equal(completionMode(action({ required: false, status: 'COMPLETED' }), approving, true, '7'), 'EDIT');
  assert.equal(completionMode(action({ required: false }), { status: 'PASSED' }, true, '7'), 'LOCKED');
  assert.equal(completionMode(action({}), active, true, '8'), 'LOCKED');
  assert.equal(completionMode(action({}), active, false, '7'), 'LOCKED');
  assert.equal(completionMode(action({ status: 'CANCELLED' }), active, true, '7'), 'LOCKED');
});

test('必选计数与底部文字：已取消不计，可选不阻塞', () => {
  const runs = [action({ status: 'COMPLETED' }), action({ status: 'PENDING' }), action({ required: false }), action({ status: 'CANCELLED' })];
  const progress = actionProgress(runs);
  assert.deepEqual(progress, { required: 2, requiredDone: 1, optional: 1, optionalDone: 0 });
  assert.deepEqual(progressText(progress, '合规审核', false), { text: '还有 1 项必选动作未完成，完成后进入合规审核', warn: true });
  assert.deepEqual(progressText({ required: 2, requiredDone: 2, optional: 0, optionalDone: 0 }), { text: '必选 2 项 · 已完成 2 项', warn: false });
  assert.equal(progressText({ required: 0, requiredDone: 0, optional: 1, optionalDone: 0 }).text, '本节点只有可选动作，不阻塞放行');
});

test('必填检查只在失焦或尝试完成后显示；上传只查至少一个；扫描中不挡完成', () => {
  const template = { config: { fields: [{ key: 'name', label: '名称', type: 'TEXT' as const, required: true, rules: { maxLength: 3 } },
    { key: 'note', label: '备注', type: 'LONG_TEXT' as const, required: false }] } };
  const problems = completionProblems(action({}), template, { name: '' });
  assert.deepEqual(problems, { name: '必填' });
  assert.deepEqual(completionProblems(action({}), template, { name: '超过三个字也可以' }), {}, '旧配置 maxLength 不再检查');
  assert.deepEqual(visibleProblems(problems, new Set(), false), {}, '打开时不标红');
  assert.deepEqual(visibleProblems(problems, new Set(['name']), false), { name: '必填' });
  assert.deepEqual(visibleProblems(problems, new Set(), true), { name: '必填' });
  const upload = action({ actionType: 'FILE_UPLOAD' });
  assert.deepEqual(completionProblems(upload, { config: { required: true } }, {}), { files: '请至少上传一个文件' });
  assert.deepEqual(completionProblems(upload, { config: { minFiles: 1, maxFiles: 1 } }, { files: ['/files/v1/1', '/files/v1/2'] }), {});
  assert.deepEqual(completionProblems(upload, { config: { required: false } }, {}), {});
  assert.deepEqual(completionProblems(action({ actionType: 'MANUAL_CONFIRM' }), { config: { noteRequired: true } }, {}), {}, '说明一律选填');
  const known = new Map<string, WorkflowFile>([['/files/v1/1', { fileId: '1', fileAddress: '/files/v1/1', displayName: 'a.pdf', mimeType: null, sizeBytes: null, scanStatus: 'SCANNING', uploaderId: null }]]);
  assert.equal(anyScanPending(['/files/v1/1'], known), true);
  assert.deepEqual(outputFieldErrors([{ field: 'output.name', message: '必填' }, { field: 'output.files[1]', message: '文件不属于本实例' }, { field: 'expectedVersion', message: '缺失' }]),
    { name: '必填', files: '第 2 个文件：文件不属于本实例', _: 'expectedVersion 缺失' });
});

test('422 字段路径就地映射：output.<key>、output.files、output.files[i]、output.note', () => {
  assert.deepEqual(outputFieldErrors([{ field: 'output.amount', message: '必须是数字' }, { field: 'output.files', message: '至少上传一个文件' },
    { field: 'output.files[0]', message: '文件未通过安全检查，请删除后重新上传' }, { field: 'output.note', message: '超过 4000 个字符' },
    { field: 'output.contract[2]', message: '文件不可用' }]),
  { amount: '必须是数字', files: '至少上传一个文件；第 1 个文件：文件未通过安全检查，请删除后重新上传', note: '超过 4000 个字符',
    contract: '第 3 个文件：文件不可用' });
});

test('提交内容摘要：表单字段数、文件数与安全检查、说明文字', () => {
  const file = (id: string, scanStatus: WorkflowFile['scanStatus']): [string, WorkflowFile] => [`/files/v1/${id}`,
    { fileId: id, fileAddress: `/files/v1/${id}`, displayName: `${id}.pdf`, mimeType: 'application/pdf', sizeBytes: '1', scanStatus, uploaderId: '7' }];
  const known = new Map([file('1', 'AVAILABLE'), file('2', 'REJECTED'), file('3', 'SCANNING')]);
  assert.equal(submissionSummary(action({ status: 'COMPLETED', output: { a: 'x', b: '' } }), { config: { fields: [
    { key: 'a', label: 'A', type: 'TEXT', required: true }, { key: 'b', label: 'B', type: 'TEXT', required: false }] } }, known), '1 个字段');
  assert.equal(submissionSummary(action({ actionType: 'FILE_UPLOAD', status: 'COMPLETED', output: { files: ['/files/v1/1', '/files/v1/2', '/files/v1/3'] } }), undefined, known),
    '3 个文件 · 1 个未通过安全检查 · 1 个安全检查中');
  assert.equal(submissionSummary(action({ actionType: 'MANUAL_CONFIRM', status: 'VERIFIED', output: { note: '已于 10-19 线下签约' } }), undefined, known), '说明：已于 10-19 线下签约');
  assert.equal(submissionSummary(action({ actionType: 'MANUAL_CONFIRM', status: 'PENDING' }), undefined, known), '尚未完成');
});

test('批量模式：显式进入、只列可批量项、全选与退出恢复原选中项', () => {
  const task = (taskId: string, taskType: Task['taskType']): Task => ({ taskType, taskId, instanceId: `i${taskId}`, instanceTitle: taskId, nodeKey: 'n', roundNo: 1,
    actionKey: null, waitingSince: ago(1) });
  const tasks = [task('a', 'ACTION'), task('b', 'APPROVAL'), task('c', 'APPROVAL'), task('d', 'APPROVAL')];
  const statuses = new Map<string, BatchStatus>([['b', { state: 'ELIGIBLE', reason: '' }], ['c', { state: 'INELIGIBLE', reason: '含 2 个动作' }],
    ['d', { state: 'CHECKING', reason: '检查中' }]]);
  const mode = enterBatch('a');
  assert.deepEqual(mode, { active: true, checked: [], restoreTaskId: 'a' });
  const candidates = batchCandidates(tasks, statuses);
  assert.deepEqual(candidates.eligible.map(item => item.taskId), ['b']);
  assert.equal(candidates.checking, 1);
  assert.equal(candidates.hidden, 3);
  assert.deepEqual(toggleChecked([], 'c', ['b']), [], '不可批量的项不能勾');
  assert.deepEqual(toggleChecked([], 'b', ['b']), ['b']);
  assert.deepEqual(toggleChecked(['b'], 'b', ['b']), []);
  assert.deepEqual(toggleAll([], ['b', 'x']), ['b', 'x']);
  assert.deepEqual(toggleAll(['b', 'x'], ['b', 'x']), []);
  const exited = exitBatch({ ...mode, checked: ['b'] });
  assert.equal(exited.restore, 'a');
  assert.deepEqual(exited.mode, batchIdle);
});

test('我发起的：当前步骤 · 等待人，本人显示为“你”；已结束为 —', () => {
  const row = (extra: Partial<InstanceSummary>): Pick<InstanceSummary, 'status' | 'currentNodes'> => ({ status: 'RUNNING', currentNodes: [], ...extra });
  assert.equal(waitingOn(row({ currentNodes: [{ nodeKey: 'k', nodeName: '合规审核', status: 'APPROVING', handlers: [{ accountId: '8', displayName: '李航' }] }] }), '7'),
    '合规审核 · 等待 李航');
  const mine = row({ currentNodes: [{ nodeKey: 'k', nodeName: '补充凭证', status: 'ACTIVE', handlers: [{ accountId: '7', displayName: 'ops.me' }, { accountId: '9', displayName: '陈雪' }] },
    { nodeKey: 'j', nodeName: '财务复核', status: 'WAITING', handlers: [] }] });
  assert.equal(waitingOn(mine, '7'), '补充凭证 · 等待 你、陈雪；财务复核');
  assert.equal(waitingForMe(mine, '7'), true);
  assert.equal(waitingForMe(mine, '8'), false);
  assert.equal(waitingOn(row({ status: 'COMPLETED' }), '7'), '—');
});

const inboxTask = (taskType: Task['taskType'], taskId: string, instanceId: string, nodeKey: string, roundNo = 1, waitingSince = ago(1)): Task => ({
  taskType, taskId, instanceId, instanceTitle: `实例 ${instanceId}`, nodeKey, roundNo, actionKey: taskType === 'ACTION' ? taskId : null, waitingSince,
  definitionName: '商户入驻', nodeName: nodeKey });

test('待办分组：同一实例同一节点运行的我的动作合为一项填写，审批一条一项；计数按列表项', () => {
  const tasks = [inboxTask('ACTION', 'a1', 'i1', 'fill', 1, ago(2)), inboxTask('APPROVAL', 'p1', 'i2', 'review'), inboxTask('ACTION', 'a2', 'i1', 'fill', 1, ago(5)),
    inboxTask('ACTION', 'a3', 'i1', 'fill', 2), inboxTask('ACTION', 'a4', 'i1', 'other'), inboxTask('ACTION', 'a5', 'i1', 'fill', 1, ago(3)),
    inboxTask('APPROVAL', 'p2', 'i2', 'review')];
  const groups = groupTasks(tasks);
  assert.deepEqual(groups.map(group => [group.key, group.tasks.map(task => task.taskId)]), [
    ['ACTION:i1:fill:1', ['a1', 'a2', 'a5']], ['APPROVAL:p1', ['p1']], ['ACTION:i1:fill:2', ['a3']], ['ACTION:i1:other:1', ['a4']], ['APPROVAL:p2', ['p2']]]);
  // 组内最早等待时间用于超时与相对时间
  assert.equal(groups[0].waitingSince, ago(5));
  assert.equal(taskGroupKey(tasks[0]), 'ACTION:i1:fill:1');
  assert.deepEqual(groupCounts(groups), { ALL: 5, ACTION: 3, APPROVAL: 2 });
  assert.deepEqual(groupCounts([]), { ALL: 0, ACTION: 0, APPROVAL: 0 });
});

test('J / K 与处理完自动打开下一条在列表项之间移动', () => {
  const groups = [{ key: 'g1' }, { key: 'g2' }, { key: 'g3' }];
  assert.equal(neighborGroup(groups, 'g2', 1)!.key, 'g3');
  assert.equal(neighborGroup(groups, 'g2', -1)!.key, 'g1');
  assert.equal(neighborGroup(groups, 'g3', 1), null);
  assert.equal(neighborGroup(groups, 'gone', 1)!.key, 'g1');
  assert.equal(neighborGroup([], null, 1), null);
  assert.equal(nextGroupAfterDone(groups, 'g2')!.key, 'g3');
  assert.equal(nextGroupAfterDone(groups, 'g3')!.key, 'g2');
  assert.equal(nextGroupAfterDone([{ key: 'g1' }], 'g1'), null);
});

test('填写详情：必选在前，默认展开我的第一个待完成必选动作', () => {
  const nodeRun = { status: 'ACTIVE' } as Pick<NodeRun, 'status'>;
  const run = (actionRunId: string, required: boolean, status: ActionRun['status'], assigneeId = '7') => ({ actionRunId, required, status, assigneeId });
  const runs = [run('opt', false, 'PENDING'), run('req-done', true, 'COMPLETED'), run('req-other', true, 'PENDING', '8'), run('req-open', true, 'PENDING')];
  assert.deepEqual(orderNodeActions(runs).map(item => item.actionRunId), ['req-done', 'req-other', 'req-open', 'opt']);
  assert.equal(defaultExpandedAction(runs, nodeRun, true, '7'), 'req-open');
  // 必选都已完成：展开可选的
  assert.equal(defaultExpandedAction(runs.filter(item => item.actionRunId !== 'req-open'), nodeRun, true, '7'), 'opt');
  // 实例已结束：不展开
  assert.equal(defaultExpandedAction(runs, nodeRun, false, '7'), null);
});
