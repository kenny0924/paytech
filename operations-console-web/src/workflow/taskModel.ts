import type {
  ActionRun, FileUploadConfig, FormDefinition, FrozenTemplate, InstanceSummary, NodeRun, Task, TaskType, WorkflowFile, WorkflowGraph,
} from './workflowTypes.ts';
import { valueProblems } from './formModel.ts';
import { fileAddressesOf, outputFileAddresses, scanFailed, scanPending, uploadRequired } from './fileModel.ts';
import { nodeOf, outgoing } from './graphModel.ts';
import { actionState, isCompleted, latestRuns } from './runtimeModel.ts';
import type { BatchStatus } from './runtimeModel.ts';
import { translator } from '../i18n/index.ts';

const t = translator('workflow');

/**
 * 我的待办 / 我发起的（工作流体验优化 S1，technique 3.1、3.2、3.5）的纯逻辑：超时标记、相对时间、横向进度条、
 * 动作完成资格与必选计数、只在失焦或尝试完成后显示的必填提示、422 字段错误映射、批量模式进出、提交内容摘要与等待人文字。
 */

// ---------- 时间 ----------
/** 超时阈值：固定 24 小时（Q3，前端常量）。 */
export const OVERDUE_HOURS = 24;
const HOUR = 3_600_000;

/** 已等待毫秒数；时间无效时为 null。 */
function elapsedMillis(since: string | null | undefined, now: number): number | null {
  if (!since) return null;
  const start = new Date(since).getTime();
  return Number.isFinite(start) ? Math.max(0, now - start) : null;
}

/** 达到超时阈值时返回“超时 N 天 / N 小时”（按已等待时长，满 1 天按天），未达阈值返回 null。 */
export function overdueText(since: string | null | undefined, now = Date.now(), thresholdHours = OVERDUE_HOURS): string | null {
  const elapsed = elapsedMillis(since, now);
  if (elapsed === null || elapsed < thresholdHours * HOUR) return null;
  const hours = Math.floor(elapsed / HOUR);
  return hours >= 24 ? t('overdue.days', { count: Math.floor(hours / 24) }) : t('overdue.hours', { count: hours });
}

const pad = (value: number) => String(value).padStart(2, '0');

/** 列表右侧的相对时间：刚刚 / N 分钟前 / N 小时前（当天）/ 昨天 HH:mm / MM-DD（跨年带年份）。 */
export function relativeTime(time: string | null | undefined, now = Date.now()): string {
  const elapsed = elapsedMillis(time, now);
  if (elapsed === null) return '—';
  const date = new Date(time!); const today = new Date(now);
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) return t('relative.justNow');
  if (minutes < 60) return t('relative.minutesAgo', { count: minutes });
  const sameDay = date.toDateString() === today.toDateString();
  if (sameDay) return t('relative.hoursAgo', { count: Math.floor(minutes / 60) });
  const yesterday = new Date(today); yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return t('relative.yesterday', { time: `${pad(date.getHours())}:${pad(date.getMinutes())}` });
  const day = `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return date.getFullYear() === today.getFullYear() ? day : `${date.getFullYear()}-${day}`;
}

/** 已用时长：“4 天”“2 小时”“15 分钟”；结束的实例算到结束时间。 */
export function durationText(start: string | null | undefined, end: string | null | undefined, now = Date.now()): string {
  const finish = end ? new Date(end).getTime() : now;
  const elapsed = elapsedMillis(start, Number.isFinite(finish) ? finish : now);
  if (elapsed === null) return '—';
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes >= 1440) return t('duration.days', { count: Math.floor(minutes / 1440) });
  if (minutes >= 60) return t('duration.hours', { count: Math.floor(minutes / 60) });
  return t('duration.minutes', { count: minutes });
}

// ---------- 横向进度条 ----------
export type StepState = 'done' | 'current' | 'pending';
export interface TaskStep { key: string; name: string; state: StepState; more?: boolean }

/** 节点在进度条上的状态：最新一轮已通过为完成，进行中为当前，其余（未到、被驳回后待重做、已取消）为未到。 */
function stepState(run: NodeRun | undefined): StepState {
  if (!run) return 'pending';
  if (run.status === 'PASSED') return 'done';
  if (run.status === 'ACTIVE' || run.status === 'APPROVING' || run.status === 'WAITING') return 'current';
  return 'pending';
}

/**
 * 横向进度条（technique 第 2 节）：从开始沿主路径走到结束，只列开始、任务与结束节点。
 * 排他网关只沿实际选中的出口继续；网关尚未走到时停止，并以“…”表示后续在运行时决定（完整图在「查看流程图」）。
 * 并行分叉的各分支任务按分支顺序依次列出，再从汇合继续。
 */
export function taskSteps(graph: WorkflowGraph, nodeRuns: NodeRun[]): TaskStep[] {
  const runs = latestRuns(nodeRuns);
  const steps: TaskStep[] = [];
  const visited = new Set<string>();
  const push = (key: string) => {
    const node = nodeOf(graph, key)!;
    const run = runs.get(key);
    const state: StepState = node.type === 'START' ? (nodeRuns.length ? 'done' : 'current')
      : node.type === 'END' ? (run ? 'done' : 'pending') : stepState(run);
    steps.push({ key, name: node.name || key, state });
  };
  /** 沿一条路径前进，直到结束、汇合（返回汇合 key）或无法确定。 */
  const walk = (from: string | undefined, stopAtJoin: boolean): string | undefined => {
    let current = from;
    while (current && !visited.has(current)) {
      const node = nodeOf(graph, current);
      if (!node) return undefined;
      if (node.type === 'PARALLEL_JOIN' && stopAtJoin) return current;
      visited.add(current);
      if (node.type === 'START' || node.type === 'TASK' || node.type === 'END') push(current);
      if (node.type === 'END') return undefined;
      if (node.type === 'EXCLUSIVE') {
        const selected = nodeRuns.filter(run => run.nodeKey === node.key && run.selectedEdgeKey)
          .sort((left, right) => right.roundNo - left.roundNo)[0]?.selectedEdgeKey;
        const edge = selected ? graph.edges.find(item => item.key === selected) : undefined;
        if (!edge) { steps.push({ key: `${node.key}:more`, name: '…', state: 'pending', more: true }); return undefined; }
        current = edge.to; continue;
      }
      if (node.type === 'PARALLEL_SPLIT') {
        let join: string | undefined;
        for (const edge of outgoing(graph, node.key)) join = walk(edge.to, true) ?? join;
        if (!join) return undefined;
        visited.add(join); current = outgoing(graph, join)[0]?.to; continue;
      }
      current = outgoing(graph, current)[0]?.to;
    }
    return undefined;
  };
  walk(graph.nodes.find(node => node.type === 'START')?.key, false);
  return steps;
}

// ---------- 待办分组 ----------
/**
 * 待办列表项（stage-5 修正）：同一实例、同一节点运行（节点 + 轮次）上指派给我的动作合为一项「填写」，审批仍一条一项。
 * key：审批为 `APPROVAL:<审批 ID>`，填写为 `ACTION:<实例>:<节点>:<轮次>`；waitingSince 取组内最早，tasks 保持服务端顺序。
 */
export interface TaskGroup {
  key: string; taskType: TaskType; instanceId: string; instanceTitle: string; nodeKey: string; roundNo: number;
  nodeName?: string | null; definitionName?: string | null; waitingSince: string; tasks: Task[];
}

/** 待办所属列表项的 key。 */
export const taskGroupKey = (task: Pick<Task, 'taskType' | 'taskId' | 'instanceId' | 'nodeKey' | 'roundNo'>): string =>
  task.taskType === 'APPROVAL' ? `APPROVAL:${task.taskId}` : `ACTION:${task.instanceId}:${task.nodeKey}:${task.roundNo}`;

/** 按 (实例, 节点运行, 待办类型) 合并；列表项按组内第一条在服务端列表中的位置排序。 */
export function groupTasks(tasks: Task[]): TaskGroup[] {
  const groups = new Map<string, TaskGroup>();
  for (const task of tasks) {
    const key = taskGroupKey(task);
    const group = groups.get(key);
    if (!group) {
      groups.set(key, { key, taskType: task.taskType, instanceId: task.instanceId, instanceTitle: task.instanceTitle, nodeKey: task.nodeKey,
        roundNo: task.roundNo, nodeName: task.nodeName, definitionName: task.definitionName, waitingSince: task.waitingSince, tasks: [task] });
      continue;
    }
    group.tasks.push(task);
    if (task.waitingSince && (!group.waitingSince || task.waitingSince < group.waitingSince)) group.waitingSince = task.waitingSince;
  }
  return [...groups.values()];
}

/** 页签计数按列表项（合并后）统计。 */
export function groupCounts(groups: Pick<TaskGroup, 'taskType'>[]): { ALL: number; ACTION: number; APPROVAL: number } {
  const action = groups.filter(group => group.taskType === 'ACTION').length;
  return { ALL: groups.length, ACTION: action, APPROVAL: groups.length - action };
}

/** J / K：在列表项之间移动；当前项不在列表中时取第一项，越界返回 null。 */
export function neighborGroup<T extends { key: string }>(groups: T[], currentKey: string | null, delta: 1 | -1): T | null {
  if (!groups.length) return null;
  const index = groups.findIndex(group => group.key === currentKey);
  if (index < 0) return groups[0];
  return groups[index + delta] ?? null;
}

/** 处理完自动打开下一条：原列表中当前项之后的一项，没有则之前的一项。 */
export function nextGroupAfterDone<T extends { key: string }>(groups: T[], doneKey: string): T | null {
  const index = groups.findIndex(group => group.key === doneKey);
  const rest = groups.filter(group => group.key !== doneKey);
  if (!rest.length) return null;
  return index < 0 ? rest[0] : rest[Math.min(index, rest.length - 1)];
}

/** 节点动作的显示顺序：必选在前、可选在后，各自保持原顺序。 */
export const orderNodeActions = <T extends Pick<ActionRun, 'required'>>(runs: T[]): T[] =>
  [...runs.filter(run => run.required), ...runs.filter(run => !run.required)];

/** 打开时默认展开：我的第一个待完成必选动作，没有则我的第一个可完成动作，都没有则不展开。 */
export function defaultExpandedAction(runs: Pick<ActionRun, 'actionRunId' | 'status' | 'required' | 'assigneeId'>[], nodeRun: Pick<NodeRun, 'status'> | undefined,
  instanceRunning: boolean, actorId: string): string | null {
  const open = orderNodeActions(runs).filter(run => completionMode(run, nodeRun, instanceRunning, actorId) === 'COMPLETE');
  return (open.find(run => run.required) ?? open[0])?.actionRunId ?? null;
}

// ---------- 动作完成 ----------
export type CompletionMode = 'COMPLETE' | 'EDIT' | 'LOCKED';

/**
 * 「完成」按钮（需求 E2）：只有被指派人在实例运行中可操作；已取消的不可操作。
 * 节点 ACTIVE：未完成显示「完成」，已完成可「修改」后再次完成；节点进入审批（APPROVING）后必选动作锁定，
 * 可选动作在节点放行前仍可完成或修改；其余节点状态锁定。
 */
export function completionMode(run: Pick<ActionRun, 'status' | 'required' | 'assigneeId'>, nodeRun: Pick<NodeRun, 'status'> | undefined,
  instanceRunning: boolean, actorId: string): CompletionMode {
  const state = actionState(run.status);
  if (!instanceRunning || !nodeRun || run.assigneeId !== actorId || state === 'CANCELLED') return 'LOCKED';
  const open = nodeRun.status === 'ACTIVE' || (nodeRun.status === 'APPROVING' && !run.required);
  if (!open) return 'LOCKED';
  return state === 'COMPLETED' ? 'EDIT' : 'COMPLETE';
}

export interface ActionProgress { required: number; requiredDone: number; optional: number; optionalDone: number }

/** 本节点必选 / 可选动作的完成计数（已取消的不计）。 */
export function actionProgress(runs: Pick<ActionRun, 'status' | 'required'>[]): ActionProgress {
  const live = runs.filter(run => actionState(run.status) !== 'CANCELLED');
  const required = live.filter(run => run.required); const optional = live.filter(run => !run.required);
  return { required: required.length, requiredDone: required.filter(isCompleted).length, optional: optional.length, optionalDone: optional.filter(isCompleted).length };
}

/** 底部操作栏左侧文字：未完成时提示还差几项，完成后说明进入哪里。 */
export function progressText(progress: ActionProgress, nextStep?: string | null, hasApprovers = true): { text: string; warn: boolean } {
  const open = progress.required - progress.requiredDone;
  if (!progress.required) return { text: progress.optional ? t('progress.optionalOnly') : t('progress.noActions'), warn: false };
  if (open > 0) return { text: hasApprovers ? t('progress.openToApproval', { open })
    : t('progress.openToStep', { open, step: nextStep || t('progress.nextStep') }), warn: true };
  return { text: t('progress.done', { required: progress.required, done: progress.requiredDone }), warn: false };
}

/** 完成前的必填检查（需求 E1）：表单查必填（及平台技术保护），上传查「至少上传一个」，其他类型不检查。 */
export function completionProblems(run: Pick<ActionRun, 'actionType'>, template: Pick<FrozenTemplate, 'config'> | undefined,
  values: Record<string, unknown>): Record<string, string> {
  if (run.actionType === 'FORM') return valueProblems((template?.config ?? { fields: [] }) as FormDefinition, values);
  if (run.actionType === 'FILE_UPLOAD' && uploadRequired(template?.config as FileUploadConfig | undefined) && !fileAddressesOf(values.files).length)
    return { files: t('completion.filesRequired') };
  return {};
}

/** 只显示用户已离开过的字段或已尝试完成后的问题；打开时不标红（需求 A4）。 */
export function visibleProblems(problems: Record<string, string>, touched: ReadonlySet<string>, attempted: boolean): Record<string, string> {
  return attempted ? problems : Object.fromEntries(Object.entries(problems).filter(([key]) => touched.has(key)));
}

/** 完成接口 422 的字段错误映射到字段：output.<key> → key，output.files[i] → files（标明第几个文件）；其余归入 _。 */
export function outputFieldErrors(errors: readonly { field: string; message: string }[]): Record<string, string> {
  const result: Record<string, string> = {};
  const add = (key: string, message: string) => { result[key] = result[key] ? `${result[key]}${t('list.semicolon')}${message}` : message; };
  for (const error of errors) {
    const item = /^output\.([A-Za-z][A-Za-z0-9_]*)(?:\[(\d+)\])?/.exec(error.field);
    if (!item) { add('_', `${error.field} ${error.message}`); continue; }
    add(item[1], item[2] !== undefined ? t('completion.fileError', { index: Number(item[2]) + 1, message: error.message }) : error.message);
  }
  return result;
}

/** 有文件还在安全检查：不挡完成，只提示在后台进行（需求 E3）。 */
export function anyScanPending(addresses: string[], known: ReadonlyMap<string, WorkflowFile>): boolean {
  return addresses.some(address => scanPending(known.get(address)));
}

// ---------- 审批前查看提交内容 ----------
/** 「动作与提交内容」的内容摘要：表单 = 字段数，上传 = 文件数与安全检查，说明 = 说明文字（需求 E3、原型 15）。 */
export function submissionSummary(run: Pick<ActionRun, 'actionType' | 'output' | 'status'>, template: Pick<FrozenTemplate, 'config'> | undefined,
  known: ReadonlyMap<string, WorkflowFile>): string {
  if (!isCompleted(run) || !run.output) return actionState(run.status) === 'CANCELLED' ? t('submission.cancelled') : t('submission.notCompleted');
  if (run.actionType === 'FORM') {
    const fields = ((template?.config as FormDefinition | undefined)?.fields ?? []).map(field => field.key);
    const keys = fields.length ? fields : Object.keys(run.output);
    const filled = keys.filter(key => { const value = run.output![key]; return value !== undefined && value !== null && value !== '' && !(Array.isArray(value) && !value.length); });
    return t('submission.fields', { count: filled.length });
  }
  if (run.actionType === 'FILE_UPLOAD') {
    const addresses = outputFileAddresses(run, template);
    const failed = addresses.filter(address => scanFailed(known.get(address))).length;
    const pending = addresses.filter(address => scanPending(known.get(address))).length;
    return [t('submission.files', { count: addresses.length }), failed ? t('submission.scanFailed', { count: failed }) : '',
      pending ? t('submission.scanPending', { count: pending }) : ''].filter(Boolean).join(' · ');
  }
  const note = typeof run.output.note === 'string' ? run.output.note.trim() : '';
  return note ? t('submission.note', { note }) : t('submission.noNote');
}

// ---------- 已办 ----------
/** 已办结果：动作完成（含旧编码 VERIFIED）为已完成，审批为已同意 / 已退回 / 已驳回。 */
export function handledResult(result: string): { label: string; color: string } {
  switch (result) {
    case 'COMPLETED': case 'VERIFIED': return { label: t('handled.completed'), color: 'blue' };
    case 'APPROVED': return { label: t('handled.approved'), color: 'success' };
    case 'RETURNED': return { label: t('handled.returned'), color: 'warning' };
    case 'REJECTED': return { label: t('handled.rejected'), color: 'error' };
    case 'CANCELLED': case 'WITHDRAWN': return { label: t('handled.cancelled'), color: 'default' };
    case 'PENDING': case 'UNVERIFIED': return { label: t('handled.pending'), color: 'processing' };
    default: return { label: result, color: 'default' };
  }
}

// ---------- 批量审批模式 ----------
export interface BatchMode { active: boolean; checked: string[]; restoreTaskId: string | null }
export const batchIdle: BatchMode = { active: false, checked: [], restoreTaskId: null };

/** 进入批量模式：记住当前选中的待办，退出时恢复（AC-03）。 */
export const enterBatch = (selectedTaskId: string | null): BatchMode => ({ active: true, checked: [], restoreTaskId: selectedTaskId });

/** 退出批量模式：返回要恢复的选中项。 */
export const exitBatch = (mode: BatchMode): { mode: BatchMode; restore: string | null } => ({ mode: batchIdle, restore: mode.restoreTaskId });

/** 批量模式只列可批量的审批（沿用现有资格判断）；检查中的数量单独返回以便提示。 */
export function batchCandidates(tasks: Task[], statuses: ReadonlyMap<string, BatchStatus>): { eligible: Task[]; checking: number; hidden: number } {
  const approvals = tasks.filter(task => task.taskType === 'APPROVAL');
  const eligible = approvals.filter(task => statuses.get(task.taskId)?.state === 'ELIGIBLE');
  const checking = approvals.filter(task => (statuses.get(task.taskId)?.state ?? 'CHECKING') === 'CHECKING').length;
  return { eligible, checking, hidden: tasks.length - eligible.length };
}

/** 勾选切换与全选：只能勾可批量的项；全部已选时全选变为全不选。 */
export function toggleChecked(checked: string[], taskId: string, eligible: readonly string[]): string[] {
  if (checked.includes(taskId)) return checked.filter(id => id !== taskId);
  return eligible.includes(taskId) ? [...checked, taskId] : checked;
}
export const toggleAll = (checked: string[], eligible: readonly string[]): string[] =>
  eligible.length && eligible.every(id => checked.includes(id)) ? [] : [...eligible];

// ---------- 我发起的 ----------
/** 当前步骤 · 等待谁：取 currentNodes 的节点名与处理人显示名，本人显示为“你”；没有处理人时只写节点名。 */
export function waitingOn(row: Pick<InstanceSummary, 'status' | 'currentNodes'>, actorId: string): string {
  if (row.status !== 'RUNNING') return '—';
  const nodes = row.currentNodes ?? [];
  if (!nodes.length) return '—';
  return nodes.map(node => {
    const names = node.handlers.map(person => person.accountId === actorId ? t('people.you') : person.displayName);
    return names.length ? t('started.waitingOn', { node: node.nodeName, people: names.join(t('list.comma')) }) : node.nodeName;
  }).join(t('list.semicolon'));
}

/** 是否轮到发起人自己处理（状态列显示“待你处理”）。 */
export const waitingForMe = (row: Pick<InstanceSummary, 'status' | 'currentNodes'>, actorId: string) =>
  row.status === 'RUNNING' && (row.currentNodes ?? []).some(node => node.handlers.some(person => person.accountId === actorId));
