import type {
  ActionRun, Approval, ApprovalDecision, ConditionClause, FrozenTemplate, GraphNode, InstanceDetail, NodeAction, NodeRun, PageTaskConfig,
  Task, WorkflowGraph,
} from './workflowTypes.ts';
import { gatewayExits, nodeOf, outgoing, topologicalOrder } from './graphModel.ts';

// ---------- 节点状态 ----------
export type VisualState = 'done' | 'active' | 'pending' | 'rejected' | 'cancelled';

/** 每个节点最新一轮的运行。 */
export function latestRuns(nodeRuns: NodeRun[]): Map<string, NodeRun> {
  const result = new Map<string, NodeRun>();
  for (const run of nodeRuns) {
    const current = result.get(run.nodeKey);
    if (!current || run.roundNo > current.roundNo) result.set(run.nodeKey, run);
  }
  return result;
}

/** 绿 已完成 / 蓝 进行中 / 灰 未到达 / 红 被驳回（technique 第 8 节）。 */
export function visualState(run: NodeRun | undefined): VisualState {
  if (!run) return 'pending';
  switch (run.status) {
    case 'PASSED': return 'done';
    case 'ACTIVE': case 'APPROVING': case 'WAITING': return 'active';
    case 'REJECTED': case 'RETURNED': return 'rejected';
    default: return 'cancelled';
  }
}

/** 节点是否有过退回或驳回（新一轮激活后仍需提示）。 */
export const hadRevision = (nodeRuns: NodeRun[], nodeKey: string) =>
  nodeRuns.some(run => run.nodeKey === nodeKey && (run.status === 'RETURNED' || run.status === 'REJECTED'));

/** 已走过的连线：两端都有运行；排他网关只算选中的出口。 */
export function traversedEdges(graph: WorkflowGraph, nodeRuns: NodeRun[]): Set<string> {
  const runs = latestRuns(nodeRuns);
  const result = new Set<string>();
  for (const edge of graph.edges) {
    const from = runs.get(edge.from); const to = runs.get(edge.to);
    if (!from || !to) continue;
    const gateway = nodeOf(graph, edge.from);
    if (gateway?.type === 'EXCLUSIVE' && !nodeRuns.some(run => run.nodeKey === edge.from && run.selectedEdgeKey === edge.key)) continue;
    result.add(edge.key);
  }
  return result;
}

export interface Progress { completed: number; active: number; pending: number; total: number }

/** 进度按任务节点计：已通过 / 进行中 / 未到达。 */
export function progressOf(detail: Pick<InstanceDetail, 'graph' | 'nodeRuns'>): Progress {
  const runs = latestRuns(detail.nodeRuns);
  const tasks = detail.graph.nodes.filter(node => node.type === 'TASK');
  const states = tasks.map(node => visualState(runs.get(node.key)));
  return { completed: states.filter(state => state === 'done').length, active: states.filter(state => state === 'active' || state === 'rejected').length,
    pending: states.filter(state => state === 'pending').length, total: tasks.length };
}

export interface Handler { nodeKey: string; nodeName: string; stage: 'ACTION' | 'APPROVAL'; people: string[]; waitingSince: string; unverified: boolean }

/** 当前进行中的任务节点与处理人：待提交看动作执行人，待审批看未决审批人。 */
export function currentHandlers(detail: InstanceDetail): Handler[] {
  if (detail.instance.status !== 'RUNNING') return [];
  const runs = latestRuns(detail.nodeRuns);
  return detail.graph.nodes.filter(node => node.type === 'TASK').flatMap(node => {
    const run = runs.get(node.key);
    if (!run || (run.status !== 'ACTIVE' && run.status !== 'APPROVING')) return [];
    const actions = detail.actionRuns.filter(item => item.nodeRunId === run.nodeRunId);
    const people = run.status === 'ACTIVE'
      ? [...new Set(actions.filter(item => item.status === 'PENDING' || item.status === 'UNVERIFIED').map(item => item.assigneeId))]
      : detail.approvals.filter(item => item.nodeRunId === run.nodeRunId && item.status === 'PENDING').map(item => item.approverId);
    return [{ nodeKey: node.key, nodeName: node.name || node.key, stage: run.status === 'ACTIVE' ? 'ACTION' : 'APPROVAL',
      people: people.map(id => detail.people[id] ?? id), waitingSince: run.activatedTime,
      unverified: actions.some(item => item.status === 'UNVERIFIED') }];
  });
}

/** 按拓扑顺序列出任务节点及状态（待办详情顶部的路线条）。 */
export function routeChips(graph: WorkflowGraph, nodeRuns: NodeRun[]): { key: string; name: string; state: VisualState }[] {
  const runs = latestRuns(nodeRuns);
  return topologicalOrder(graph).map(key => nodeOf(graph, key)!).filter(node => node.type !== 'EXCLUSIVE' && node.type !== 'PARALLEL_SPLIT')
    .map(node => ({ key: node.key, name: node.name || node.key, state: node.type === 'START' ? 'done' : visualState(runs.get(node.key)) }));
}

// ---------- 变量与动作 ----------
/** 运行时变量：start.* 取启动值，<actionKey>.* 取最新一轮已验证动作的输出（3.3）。 */
export function runtimeVariables(detail: Pick<InstanceDetail, 'startValues' | 'actionRuns' | 'nodeRuns'>): Record<string, unknown> {
  const variables: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(detail.startValues ?? {})) variables[`start.${key}`] = value;
  const roundOf = new Map(detail.nodeRuns.map(run => [run.nodeRunId, run.roundNo]));
  const latest = new Map<string, ActionRun>();
  for (const run of detail.actionRuns) {
    if (run.status !== 'VERIFIED') continue;
    const current = latest.get(run.actionKey);
    if (!current || (roundOf.get(run.nodeRunId) ?? 0) > (roundOf.get(current.nodeRunId) ?? 0)) latest.set(run.actionKey, run);
  }
  for (const [key, run] of latest) for (const [field, value] of Object.entries(run.output ?? {})) variables[`${key}.${field}`] = value;
  return variables;
}

/** 动作显示名：运行详情的模板名 → 冻结快照的模板名 → 动作 key（早期快照没有名称）。 */
export function actionLabel(detail: Pick<InstanceDetail, 'graph' | 'actionSnapshot'>, run: Pick<ActionRun, 'actionKey' | 'templateName'>): string {
  return run.templateName || frozenTemplate(detail, run.actionKey)?.templateName || run.actionKey;
}

/** 图上的节点动作配置（取 templateId、输入映射）。 */
export function nodeActionOf(graph: WorkflowGraph, actionKey: string): { node: GraphNode; action: NodeAction } | null {
  for (const node of graph.nodes) {
    const action = node.actions?.find(item => item.key === actionKey);
    if (action) return { node, action };
  }
  return null;
}

export function frozenTemplate(detail: Pick<InstanceDetail, 'graph' | 'actionSnapshot'>, actionKey: string): FrozenTemplate | undefined {
  const located = nodeActionOf(detail.graph, actionKey);
  return located ? detail.actionSnapshot?.templates.find(item => item.templateId === located.action.templateId) : undefined;
}

/** 页面任务的跳转地址：pageRoute 只允许站内路径，参数按输入映射取变量或常量后拼成查询串（3.3）。 */
export function pageTaskLink(config: PageTaskConfig, inputs: NodeAction['inputs'], variables: Record<string, unknown>): string | null {
  if (!isInternalRoute(config.pageRoute)) return null;
  const query = new URLSearchParams();
  for (const parameter of config.parameters ?? []) {
    const mapping = inputs?.[parameter.key];
    if (!mapping) continue;
    const value = mapping.source === 'CONSTANT' ? mapping.value : variables[mapping.value];
    if (value !== undefined && value !== null && value !== '') query.set(parameter.key, Array.isArray(value) ? value.join(',') : String(value));
  }
  const text = query.toString();
  return text ? `${config.pageRoute}${config.pageRoute.includes('?') ? '&' : '?'}${text}` : config.pageRoute;
}

/** 只接受以单个 / 开头的控制台内部路由。 */
export const isInternalRoute = (route: string) => /^\/(?![/\\])[^\s:]*$/.test(route);

/** 同一节点上一轮同一动作的运行。 */
export function previousRoundAction(detail: Pick<InstanceDetail, 'nodeRuns' | 'actionRuns'>, run: ActionRun): ActionRun | null {
  const nodeRun = detail.nodeRuns.find(item => item.nodeRunId === run.nodeRunId);
  if (!nodeRun || nodeRun.roundNo <= 1) return null;
  const previous = detail.nodeRuns.filter(item => item.nodeKey === nodeRun.nodeKey && item.roundNo < nodeRun.roundNo)
    .sort((left, right) => right.roundNo - left.roundNo)[0];
  return previous ? detail.actionRuns.find(item => item.nodeRunId === previous.nodeRunId && item.actionKey === run.actionKey) ?? null : null;
}

export interface Revision { approval: Approval; kind: 'RETURNED' | 'REJECTED'; roundNo: number }

/** 本轮为什么重开：上一轮被退回修改，或下游节点驳回回到本节点；取激活前最近的一条决定。 */
export function revisionOf(detail: Pick<InstanceDetail, 'nodeRuns' | 'approvals'>, nodeRun: NodeRun): Revision | null {
  if (nodeRun.roundNo <= 1) return null;
  const previous = detail.nodeRuns.find(item => item.nodeKey === nodeRun.nodeKey && item.roundNo === nodeRun.roundNo - 1);
  const candidates = detail.approvals.filter(item => (item.status === 'RETURNED' && item.nodeRunId === previous?.nodeRunId)
    || (item.status === 'REJECTED' && item.decidedTime !== null && item.decidedTime <= nodeRun.activatedTime))
    .sort((left, right) => (right.decidedTime ?? '').localeCompare(left.decidedTime ?? ''));
  const approval = candidates[0];
  return approval ? { approval, kind: approval.status as 'RETURNED' | 'REJECTED', roundNo: previous?.roundNo ?? nodeRun.roundNo - 1 } : null;
}

// ---------- 审批决定 ----------
export interface DecisionOption { decision: ApprovalDecision; enabled: boolean; reason: string; needsComment: boolean }
export interface DecisionOptions { options: DecisionOption[]; blocked: string | null; rejectTargets: string[] }

/**
 * 三个审批决定的可用性：
 * - 本人提交过本节点动作时整体不可审批（同一人限制，B3）；
 * - 退回修改仅节点配有动作时可用（B5a）；
 * - 驳回按后端给出的 rejectKind：流程第一个任务节点、并行分支第一个节点不可驳回；汇合后需选目标分支（B5）。
 */
export function decisionOptions(input: { approval: Approval; nodeRun: NodeRun; hasActions: boolean; submittedByMe: boolean; instanceRunning: boolean }): DecisionOptions {
  const { approval, nodeRun, hasActions, submittedByMe, instanceRunning } = input;
  const blocked = !instanceRunning ? '实例已结束，只读'
    : approval.status !== 'PENDING' ? '这条审批已处理'
    : nodeRun.status !== 'APPROVING' ? '节点不在待审批状态'
    : submittedByMe ? '你提交过本节点的动作，不能审批本节点'
    : null;
  const rejectReason = nodeRun.rejectKind === 'FIRST_TASK' ? '本节点是流程第一个任务节点，没有可驳回的上一节点；发起人可终止实例'
    : nodeRun.rejectKind === 'BRANCH_FIRST_NODE' ? '本节点是并行分支的第一个节点，驳回会牵连其他分支'
    : nodeRun.rejectKind === 'CHOOSE_BRANCH' && !(nodeRun.rejectTargets?.length) ? '没有可退回的分支'
    : '';
  const options: DecisionOption[] = [
    { decision: 'APPROVE', enabled: !blocked, reason: blocked ?? '', needsComment: false },
    { decision: 'RETURN', enabled: !blocked && hasActions, reason: blocked ?? (hasActions ? '' : '本节点没有动作，纯审批节点不能退回修改'), needsComment: true },
    { decision: 'REJECT', enabled: !blocked && !rejectReason, reason: blocked ?? rejectReason, needsComment: true },
  ];
  return { options, blocked, rejectTargets: nodeRun.rejectKind === 'CHOOSE_BRANCH' ? nodeRun.rejectTargets ?? [] : [] };
}

/** 决定提交前的检查：退回修改与驳回必填意见；汇合后驳回必须选目标分支。 */
export function decisionError(options: DecisionOptions, decision: ApprovalDecision, comment: string, target?: string): string | null {
  const option = options.options.find(item => item.decision === decision);
  if (!option?.enabled) return option?.reason || '当前不可选';
  if (option.needsComment && !comment.trim()) return '退回修改和驳回必须填写意见';
  if (comment.length > 512) return '意见不能超过 512 个字符';
  if (decision === 'REJECT' && options.rejectTargets.length && !target) return '请选择驳回到哪一条分支';
  return null;
}

// ---------- 批量同意 ----------
/** 只有没有动作的审批可批量同意（technique 第 8 节）；已决、本人提交过的不行。 */
export function batchEligibility(task: Task, detail: InstanceDetail | undefined, actorId: string): { eligible: boolean; reason: string } {
  if (task.taskType !== 'APPROVAL') return { eligible: false, reason: '不是审批' };
  if (!detail) return { eligible: false, reason: '加载中' };
  const node = nodeOf(detail.graph, task.nodeKey);
  if ((node?.actions?.length ?? 0) > 0) return { eligible: false, reason: `含 ${node!.actions!.length} 个动作，需逐条查看` };
  const approval = detail.approvals.find(item => item.approvalId === task.taskId);
  if (!approval || approval.status !== 'PENDING') return { eligible: false, reason: '已被处理' };
  if (approval.approverId !== actorId) return { eligible: false, reason: '不是你的审批' };
  return { eligible: true, reason: '' };
}

/** 批量审批资格：检查中（详情未加载）、可批量、不可批量（原因）、无法检查（详情加载失败）。 */
export type BatchState = 'CHECKING' | 'ELIGIBLE' | 'INELIGIBLE' | 'UNCHECKABLE';
export interface BatchStatus { state: BatchState; reason: string }

/** 单项资格：详情加载失败标为无法检查；没有详情为检查中；有详情按原规则判断。 */
export function batchStatus(task: Task, detail: InstanceDetail | undefined, actorId: string, failed: boolean): BatchStatus {
  if (task.taskType !== 'APPROVAL') return { state: 'INELIGIBLE', reason: '不是审批' };
  if (!detail) return failed ? { state: 'UNCHECKABLE', reason: '无法检查，请单独处理' } : { state: 'CHECKING', reason: '检查中' };
  const result = batchEligibility(task, detail, actorId);
  return result.eligible ? { state: 'ELIGIBLE', reason: '' } : { state: 'INELIGIBLE', reason: result.reason };
}

/**
 * 批量提交计划（整体等待）：任一勾选项仍在检查中时整体不可提交；检查完成后只提交可批量的项，
 * 不可批量与无法检查的项列为排除，不提交。
 */
export function batchSubmission(taskIds: readonly string[], statuses: ReadonlyMap<string, BatchStatus>) {
  const of = (state: BatchState) => taskIds.filter(id => (statuses.get(id)?.state ?? 'CHECKING') === state);
  const checking = of('CHECKING'); const submit = of('ELIGIBLE');
  const excluded = taskIds.filter(id => !checking.includes(id) && !submit.includes(id));
  return { ready: checking.length === 0 && submit.length > 0, checking, submit, excluded };
}

export interface BatchResult { taskId: string; ok: boolean; message: string }

/** 批量完成汇总：按冻结的勾选项与结果计数；没有结果的项为未提交（检查后排除），不靠减法。 */
export function batchSummary(taskIds: readonly string[], results: ReadonlyMap<string, BatchResult>) {
  let approved = 0; let failed = 0; let skipped = 0;
  for (const id of taskIds) {
    const result = results.get(id);
    if (!result) skipped++; else if (result.ok) approved++; else failed++;
  }
  return { approved, failed, skipped };
}

/** 逐项调用审批接口，任一失败不影响其他项；按原顺序返回每项结果。 */
export async function runBatch(taskIds: string[], decide: (taskId: string) => Promise<void>,
  describe: (error: unknown) => string, onResult?: (result: BatchResult) => void): Promise<BatchResult[]> {
  const results: BatchResult[] = [];
  for (const taskId of taskIds) {
    let result: BatchResult;
    try { await decide(taskId); result = { taskId, ok: true, message: '已同意' }; }
    catch (error) { result = { taskId, ok: false, message: describe(error) }; }
    results.push(result); onResult?.(result);
  }
  return results;
}

// ---------- 待办切换与快捷键 ----------
/** J / K 切换：越界时不动（返回 null）。 */
export function neighborTask(tasks: Task[], currentId: string | null, delta: 1 | -1): Task | null {
  if (!tasks.length) return null;
  const index = tasks.findIndex(task => task.taskId === currentId);
  if (index < 0) return tasks[0];
  return tasks[index + delta] ?? null;
}

/** 处理完自动打开下一条：取原列表中当前项之后的一条，没有则取之前的一条。 */
export function nextAfterDone(tasks: Task[], doneId: string): Task | null {
  const index = tasks.findIndex(task => task.taskId === doneId);
  const rest = tasks.filter(task => task.taskId !== doneId);
  if (!rest.length) return null;
  return index < 0 ? rest[0] : rest[Math.min(index, rest.length - 1)];
}

export type ShortcutAction = 'NEXT' | 'PREVIOUS' | 'APPROVE' | 'RETURN' | 'TRANSFER' | 'SUBMIT';

/**
 * 快捷键 J / K 切换，A 同意，R 退回修改，T 转交，Ctrl（或 ⌘）+ Enter 提交。
 * 焦点在输入框时只响应 Ctrl + Enter；A / R 只在审批时有效；有修饰键的单字母不拦截（保留浏览器快捷键）。
 */
export function resolveShortcut(input: { key: string; ctrlKey: boolean; metaKey: boolean; altKey: boolean; editable: boolean; taskType: Task['taskType'] | null }): ShortcutAction | null {
  if (input.key === 'Enter' && (input.ctrlKey || input.metaKey)) return input.taskType ? 'SUBMIT' : null;
  if (input.editable || input.ctrlKey || input.metaKey || input.altKey) return null;
  switch (input.key.toLowerCase()) {
    case 'j': return 'NEXT';
    case 'k': return 'PREVIOUS';
    case 't': return input.taskType ? 'TRANSFER' : null;
    case 'a': return input.taskType === 'APPROVAL' ? 'APPROVE' : null;
    case 'r': return input.taskType === 'APPROVAL' ? 'RETURN' : null;
    default: return null;
  }
}

/** 停留时长：“2 天”“5 小时”“30 分钟”。 */
export function waitingText(since: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.floor((now - new Date(since).getTime()) / 60000));
  if (!Number.isFinite(minutes)) return '—';
  if (minutes >= 1440) return `${Math.floor(minutes / 1440)} 天`;
  if (minutes >= 60) return `${Math.floor(minutes / 60)} 小时`;
  return `${minutes} 分钟`;
}
export const overOneDay = (since: string, now = Date.now()) => now - new Date(since).getTime() > 86400000;

// ---------- 条件求值与发起预览 ----------
/** 与后端条件求值一致的前端近似：字符串比较，数字 / 日期按大小比较，判空看空串和空数组。 */
export function evaluateClause(clause: ConditionClause, value: unknown): boolean {
  const empty = value === undefined || value === null || value === '' || Array.isArray(value) && value.length === 0;
  if (clause.operator === 'IS_EMPTY') return empty;
  if (clause.operator === 'IS_NOT_EMPTY') return !empty;
  if (empty) return false;
  const actual = String(value); const expected = Array.isArray(clause.value) ? clause.value : [clause.value ?? ''];
  const compare = () => {
    const left = Number(actual); const right = Number(expected[0]);
    return Number.isFinite(left) && Number.isFinite(right) ? left - right : actual.localeCompare(expected[0]);
  };
  switch (clause.operator) {
    case 'EQ': return actual === expected[0];
    case 'NE': return actual !== expected[0];
    case 'IN': return expected.includes(actual);
    case 'CONTAINS': return actual.includes(expected[0]);
    case 'GT': return compare() > 0;
    case 'GTE': return compare() >= 0;
    case 'LT': return compare() < 0;
    case 'LTE': return compare() <= 0;
  }
}

export type PreviewStep = { kind: 'task'; node: GraphNode; reason?: string } | { kind: 'parallel'; branches: GraphNode[][] }
  | { kind: 'undecided'; node: GraphNode };

/**
 * 发起前的流程预览：从开始沿图走到结束。排他网关只按启动变量求值（条件引用动作输出时标为“运行时决定”并停止向下推演）；
 * 并行分叉列出各分支的任务节点后从汇合继续。
 */
export function previewPath(graph: WorkflowGraph, startValues: Record<string, unknown>): PreviewStep[] {
  const steps: PreviewStep[] = [];
  const variables = Object.fromEntries(Object.entries(startValues).map(([key, value]) => [`start.${key}`, value]));
  let current = graph.nodes.find(node => node.type === 'START')?.key;
  const visited = new Set<string>();
  let reason: string | undefined;
  while (current && !visited.has(current)) {
    visited.add(current);
    const node = nodeOf(graph, current);
    if (!node || node.type === 'END') break;
    if (node.type === 'TASK') { steps.push({ kind: 'task', node, reason }); reason = undefined; }
    if (node.type === 'EXCLUSIVE') {
      const exits = gatewayExits(graph, node.key);
      const decidable = exits.every(exit => exit.clauses.every(clause => clause.variable.startsWith('start.')));
      if (!decidable) { steps.push({ kind: 'undecided', node }); break; }
      const chosen = exits.find(exit => !exit.isDefault && exit.clauses.length && exit.clauses.every(clause => evaluateClause(clause, variables[clause.variable])))
        ?? exits.find(exit => exit.isDefault);
      reason = chosen && !chosen.isDefault ? `因为 ${node.name || node.key} 的条件成立` : undefined;
      current = chosen?.edge.to; continue;
    }
    if (node.type === 'PARALLEL_SPLIT') {
      const branches: GraphNode[][] = []; let join: string | undefined;
      for (const edge of outgoing(graph, node.key)) {
        const branch: GraphNode[] = []; let cursor: string | undefined = edge.to; const seen = new Set<string>();
        while (cursor && !seen.has(cursor)) {
          seen.add(cursor);
          const item = nodeOf(graph, cursor);
          if (!item || item.type === 'PARALLEL_JOIN' || item.type === 'END') { if (item?.type === 'PARALLEL_JOIN') join = item.key; break; }
          if (item.type === 'TASK') branch.push(item);
          cursor = outgoing(graph, cursor)[0]?.to;
        }
        branches.push(branch);
      }
      steps.push({ kind: 'parallel', branches });
      current = join ? outgoing(graph, join)[0]?.to : undefined; continue;
    }
    current = outgoing(graph, node.key)[0]?.to;
  }
  return steps;
}

/**
 * 任务节点的审批人：有无审批按发布时冻结的节点配置判断（审批行在必选动作完成后才生成）。
 * 已开启审批取本轮审批行；尚未开启时取实例当前生效的分配（含发起修改与转交），缺省为节点配置的默认审批人。
 */
export function nodeApproverIds(detail: Pick<InstanceDetail, 'graph' | 'approvals' | 'nodeApprovers'>, nodeKey: string, nodeRunId?: string): string[] {
  const node = nodeOf(detail.graph, nodeKey);
  if (!node?.approval?.approverIds.length) return [];
  const rows = detail.approvals.filter(item => item.nodeRunId === nodeRunId).map(item => item.approverId);
  if (rows.length) return [...new Set(rows)];
  return detail.nodeApprovers[nodeKey]?.length ? detail.nodeApprovers[nodeKey] : node.approval.approverIds;
}
