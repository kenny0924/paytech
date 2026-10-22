import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Checkbox, Empty, Input, Pagination, Segmented, Switch, Table, Tag } from 'antd';
import { Link, useSearchParams } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { nodeOf } from '../workflow/graphModel';
import { batchStatus, batchSubmission, neighborTask, nextAfterDone, overOneDay, resolveShortcut, runBatch, waitingText } from '../workflow/runtimeModel';
import type { BatchResult, BatchStatus } from '../workflow/runtimeModel';
import { ActionTaskPanel, ApprovalPanel, BatchPanel, TaskHeader } from '../workflow/TaskPanels';
import type { PanelHandles } from '../workflow/TaskPanels';
import { failureText, sendWorkflowCommand, useWorkflowCommand, workflowCommand } from '../workflow/useWorkflowCommand';
import { decodeHandledTask, decodeInstanceDetail, decodeInstanceSummary, decodeMutation, decodePage, decodeTask, workflowPaths } from '../workflow/workflowApi';
import { actionRunStatusLabels, approvalStatusLabels, instanceStatusColors, instanceStatusLabels } from '../workflow/workflowLabels';
import type { HandledTask, InstanceDetail, InstanceSummary, Task } from '../workflow/workflowTypes';
import { LoadFailed, Spinner, shortTime, workflowRoutes } from '../workflow/WorkflowParts';
import { DETAIL_PRIORITY, DetailQueue, DetailRequestCancelled, planBatchFill, planPrefetch, retryAfterMillis } from '../workflow/prefetchModel';
import '../workflow/workflow.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type Filter = 'ALL' | 'ACTION' | 'APPROVAL' | 'OVERDUE';
/** 实例详情 429：会话层不透出 Retry-After，按默认短退避重试一次。 */
function detailRetryDelay(error: unknown): number | null {
  return error instanceof ApiError && error.status === 429 ? retryAfterMillis(null) : null;
}

const decodeTasks = decodePage(decodeTask);
const decodeInstances = decodePage(decodeInstanceSummary);
const autoNextKey = 'workflow.my-tasks.auto-next';
function readAutoNext() { try { return window.localStorage.getItem(autoNextKey) !== '0'; } catch { return true; } }
function saveAutoNext(value: boolean) { try { window.localStorage.setItem(autoNextKey, value ? '1' : '0'); } catch { /* 偏好可丢失 */ } }

/** 我的待办：左侧列表、右侧直接处理（原型 01、02、19—22）。 */
export function WorkflowMyTasks({ actorId, onAuthorizationChanged }: Props) {
  const [tab, setTab] = useState<'TODO' | 'HANDLED' | 'STARTED'>('TODO');
  return <div className="wf-page">
    <div className="wf-page-head"><h1>我的待办</h1>
      <Segmented value={tab} onChange={value => setTab(value as typeof tab)} options={[{ value: 'TODO', label: '待处理' }, { value: 'HANDLED', label: '已处理' }, { value: 'STARTED', label: '我发起的' }]} /></div>
    {tab === 'TODO' ? <TodoWorkspace actorId={actorId} onAuthorizationChanged={onAuthorizationChanged} />
      : tab === 'HANDLED' ? <HandledTasks /> : <StartedByMe actorId={actorId} />}
  </div>;
}

function TodoWorkspace({ actorId, onAuthorizationChanged }: { actorId: string; onAuthorizationChanged: () => void }) {
  const { message } = App.useApp();
  const [params, setParams] = useSearchParams();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [keyword, setKeyword] = useState('');
  const paging = usePaging<Task>(workflowPaths.myTasks, decodeTasks);
  /** 类型与标题关键字走服务端；“超过 1 天”在当前页内筛选。空值参数不发送。 */
  function query(nextFilter: Filter, nextKeyword: string) {
    paging.applyFilters({ ...(nextFilter === 'ACTION' || nextFilter === 'APPROVAL' ? { taskType: nextFilter } : {}),
      ...(nextKeyword.trim() ? { keyword: nextKeyword.trim() } : {}) });
  }
  function changeFilter(value: Filter) { setFilter(value); setChecked([]); query(value, keyword); }
  const tasks = useMemo(() => paging.data.items.filter(task => filter !== 'OVERDUE' || overOneDay(task.waitingSince)), [paging.data.items, filter]);
  const selectedId = params.get('task');
  const selected = tasks.find(task => task.taskId === selectedId) ?? null;
  const select = useCallback((task: Task | null) => {
    setParams(current => { const next = new URLSearchParams(current); if (task) next.set('task', task.taskId); else next.delete('task'); return next; }, { replace: true });
  }, [setParams]);
  useEffect(() => { if (!paging.loading && !selected && tasks.length) select(tasks[0]); }, [paging.loading, selected, tasks, select]);
  // 实例详情缓存：处理区与批量资格判断共用。
  const [details, setDetails] = useState<Map<string, InstanceDetail>>(new Map());
  const [checked, setChecked] = useState<string[]>([]);
  const batchMode = checked.length > 0;
  const [detailFailure, setDetailFailure] = useState<unknown>();
  const cache = useRef(details); cache.current = details;
  // 所有实例详情请求走同一串行队列：选中项 > 批量勾选 > 普通预取，同一实例合并，最多 1 个在途，429 退避重试一次。
  const queue = useMemo(() => new DetailQueue<InstanceDetail>(async instanceId => {
    const value = await session.request(workflowPaths.instance(instanceId), {}, decodeInstanceDetail);
    setDetails(current => new Map(current).set(instanceId, value));
    return value;
  }, { retryDelay: detailRetryDelay }), []);
  useEffect(() => () => { for (const tag of ['selected', 'batch', 'prefetch']) queue.cancel(tag); }, [queue]);
  const loadDetail = useCallback(async (instanceId: string, force = false) => {
    if (!force && cache.current.has(instanceId)) return cache.current.get(instanceId)!;
    return queue.request(instanceId, DETAIL_PRIORITY.SELECTED, 'refresh');
  }, [queue]);
  useEffect(() => {
    if (!selected) return;
    setDetailFailure(undefined);
    if (cache.current.has(selected.instanceId)) return;
    queue.request(selected.instanceId, DETAIL_PRIORITY.SELECTED, 'selected')
      .catch(error => { if (!(error instanceof DetailRequestCancelled)) setDetailFailure(error); });
    return () => queue.cancel('selected');
  }, [selected?.instanceId, queue]); // 只随所选实例变化，避免缓存更新时重复请求
  // 审批待办预取少量详情，用于判断能否批量同意；排在选中项之后，切换选中或卸载时取消剩余预取。
  const approvalIds = tasks.filter(task => task.taskType === 'APPROVAL').map(task => task.instanceId).join(',');
  useEffect(() => {
    if (batchMode) return; // 批量模式由按需补齐接管
    const ids = planPrefetch(approvalIds ? approvalIds.split(',') : [], selected?.instanceId ?? null, new Set(cache.current.keys()));
    ids.forEach(id => { queue.request(id, DETAIL_PRIORITY.PREFETCH, 'prefetch').catch(() => undefined); });
    return () => queue.cancel('prefetch');
  }, [approvalIds, selected?.instanceId, batchMode, queue]);
  // 批量模式：按需补齐勾选项（优先）与其他审批待办的详情；重试后仍失败的实例标为无法检查。
  const [failed, setFailed] = useState<ReadonlySet<string>>(new Set());
  const failedRef = useRef(failed); failedRef.current = failed;
  const checkedInstanceIds = paging.data.items.filter(task => checked.includes(task.taskId)).map(task => task.instanceId).join(',');
  useEffect(() => {
    if (!batchMode) { if (failedRef.current.size) setFailed(new Set()); return; }
    const ids = planBatchFill(checkedInstanceIds ? checkedInstanceIds.split(',') : [], approvalIds ? approvalIds.split(',') : [],
      new Set(cache.current.keys()), failedRef.current);
    ids.forEach(id => {
      queue.request(id, DETAIL_PRIORITY.BATCH, 'batch').catch(error => {
        if (!(error instanceof DetailRequestCancelled)) setFailed(current => new Set(current).add(id));
      });
    });
    return () => queue.cancel('batch');
  }, [batchMode, checkedInstanceIds, approvalIds, queue]);
  const statuses = useMemo(() => new Map<string, BatchStatus>(paging.data.items.map(task => [task.taskId,
    batchStatus(task, details.get(task.instanceId), actorId, failed.has(task.instanceId))])), [paging.data.items, details, actorId, failed]);
  const [autoNext, setAutoNext] = useState(readAutoNext);
  const [batchResults, setBatchResults] = useState<Map<string, BatchResult>>(new Map());
  const [batchRunning, setBatchRunning] = useState(false); const [batchComment, setBatchComment] = useState('');
  const command = useWorkflowCommand(onAuthorizationChanged);
  const handles = useRef<PanelHandles>({});
  const detail = selected ? details.get(selected.instanceId) : undefined;
  const batchTasks = paging.data.items.filter(task => checked.includes(task.taskId));
  // 开始提交时冻结勾选项与资格状态：提交后列表刷新会移除已同意的项，汇总与结果表格仍按冻结列表展示，直到关闭或退出批量模式。
  const [frozenBatch, setFrozenBatch] = useState<{ tasks: Task[]; statuses: ReadonlyMap<string, BatchStatus> } | null>(null);
  useEffect(() => { if (!batchMode) { setFrozenBatch(null); setBatchResults(new Map()); } }, [batchMode]);

  function changed(finished: boolean) {
    if (!selected) return;
    const done = selected;
    void loadDetail(done.instanceId, true).catch(() => undefined);
    paging.refresh();
    if (finished && autoNext) select(nextAfterDone(tasks, done.taskId));
  }
  async function runBatchApprove() {
    const plan = batchSubmission(batchTasks.map(task => task.taskId), statuses);
    if (!plan.ready) return;
    setFrozenBatch({ tasks: batchTasks, statuses: new Map(statuses) });
    setBatchRunning(true); setBatchResults(new Map());
    await runBatch(plan.submit, async taskId => {
      await sendWorkflowCommand(workflowCommand(workflowPaths.decision(taskId), 'POST',
        { decision: 'APPROVE', ...(batchComment.trim() ? { comment: batchComment.trim() } : {}) }), decodeMutation);
    }, failureText, result => setBatchResults(current => new Map(current).set(result.taskId, result)));
    setBatchRunning(false); paging.refresh();
    for (const id of new Set(batchTasks.map(task => task.instanceId))) void loadDetail(id, true).catch(() => undefined);
  }
  // 快捷键：J/K 切换，A 同意，R 退回修改，T 转交，Ctrl+Enter 提交。
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const editable = !!target?.closest('input, textarea, select, [contenteditable="true"]');
      const action = resolveShortcut({ key: event.key, ctrlKey: event.ctrlKey, metaKey: event.metaKey, altKey: event.altKey, editable,
        taskType: checked.length ? null : selected?.taskType ?? null });
      if (!action) return;
      event.preventDefault();
      if (action === 'NEXT' || action === 'PREVIOUS') {
        const next = neighborTask(tasks, selected?.taskId ?? null, action === 'NEXT' ? 1 : -1);
        if (next) select(next); else void message.info(action === 'NEXT' ? '已经是最后一条' : '已经是第一条');
      }
      if (action === 'SUBMIT') handles.current.submit?.();
      if (action === 'APPROVE') handles.current.approve?.();
      if (action === 'RETURN') handles.current.giveBack?.();
      if (action === 'TRANSFER') handles.current.transfer?.();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [tasks, selected, select, checked.length, message]);

  let panel;
  if (checked.length) panel = <BatchPanel tasks={frozenBatch?.tasks ?? batchTasks} details={details} statuses={frozenBatch?.statuses ?? statuses}
    results={batchResults} running={batchRunning} comment={batchComment}
    onComment={setBatchComment} onRun={() => void runBatchApprove()} onClear={() => { setChecked([]); setBatchResults(new Map()); setFrozenBatch(null); }}
    onFinish={() => { setChecked([]); setBatchResults(new Map()); setBatchComment(''); setFrozenBatch(null); }} />;
  else if (!selected) panel = <section className="wf-card"><Empty description={paging.loading ? '加载中' : '没有待处理的事项'} /></section>;
  else if (detailFailure) panel = <LoadFailed failure={detailFailure} retry={() => { setDetailFailure(undefined); loadDetail(selected.instanceId, true).catch(setDetailFailure); }} />;
  else if (!detail) panel = <Spinner />;
  else {
    const props = { detail, task: selected, actorId, command, handles, onChanged: changed };
    panel = <div key={selected.taskId}><TaskHeader {...props} />
      {selected.taskType === 'ACTION' ? <ActionTaskPanel {...props} /> : <ApprovalPanel {...props} />}</div>;
  }
  return <div className="wf-split">
    <aside className="wf-task-column">
      <div className="wf-chips">
        {(['ALL', 'ACTION', 'APPROVAL', 'OVERDUE'] as Filter[]).map(value => <button type="button" key={value}
          className={`wf-chip${filter === value ? ' is-active' : ''}`} onClick={() => changeFilter(value)}>
          {{ ALL: '全部', ACTION: '待我执行', APPROVAL: '待我审批', OVERDUE: '超过 1 天' }[value]}</button>)}
      </div>
      <Input.Search allowClear placeholder="搜索实例标题" value={keyword} onChange={event => setKeyword(event.target.value)} onSearch={value => query(filter, value)} />
      {paging.error && <Alert type="warning" showIcon title="待办加载失败" action={<Button size="small" onClick={paging.refresh}>重试</Button>} />}
      {checked.length > 0 && <div className="wf-batch-bar"><span>已选 {checked.length} 项（只能批量同意没有动作的审批）</span>
        <Button type="link" size="small" onClick={() => setChecked([])}>清除</Button></div>}
      <div className="wf-task-list">
        {tasks.map(task => {
          const taskDetail = details.get(task.instanceId);
          const eligibility = task.taskType === 'APPROVAL' ? statuses.get(task.taskId) ?? null : null;
          const isChecked = checked.includes(task.taskId);
          const node = taskDetail ? nodeOf(taskDetail.graph, task.nodeKey) : undefined;
          const overdue = overOneDay(task.waitingSince);
          return <div key={task.taskId} role="button" tabIndex={0} className={`wf-task-item${task.taskId === selected?.taskId && !checked.length ? ' is-selected' : ''}`}
            onClick={() => { setChecked([]); select(task); }} onKeyDown={event => { if (event.key === 'Enter') select(task); }}>
            {eligibility && <Checkbox checked={isChecked} disabled={!isChecked && (eligibility.state === 'INELIGIBLE' || eligibility.state === 'UNCHECKABLE')}
              title={eligibility.reason || undefined}
              onClick={event => event.stopPropagation()}
              onChange={event => setChecked(current => event.target.checked ? [...current, task.taskId] : current.filter(id => id !== task.taskId))} />}
            <span className={`wf-task-icon is-${task.taskType.toLowerCase()}`}>{task.taskType === 'ACTION' ? '✎' : '✓'}</span>
            <div className="wf-task-body">
              <div className="wf-task-row"><strong>{task.nodeName || node?.name || task.nodeKey}</strong>
                <Tag color={overdue ? 'red' : 'orange'}>{task.roundNo > 1 ? `第 ${task.roundNo} 轮 · ` : ''}停留 {waitingText(task.waitingSince)}</Tag></div>
              <div className="muted">{task.instanceTitle}{task.definitionName ? ` · ${task.definitionName}` : taskDetail ? ` · ${taskDetail.definitionName}` : ''}</div>
              <div className="wf-task-kind">{task.taskType === 'ACTION' ? `执行 · ${task.actionKey ?? ''}` : `审批 · ${node?.approval?.mode ?? ''}`}
                {eligibility && (eligibility.state === 'UNCHECKABLE' || filter === 'APPROVAL' && eligibility.state === 'INELIGIBLE')
                  && <span className="wf-warn-text"> · {eligibility.reason}</span>}
                {eligibility?.state === 'CHECKING' && isChecked && <span className="muted"> · 检查中</span>}</div>
            </div>
          </div>;
        })}
        {!paging.loading && !tasks.length && <Empty description="没有符合条件的待办" />}
      </div>
      {paging.data.total > paging.data.size && <Pagination size="small" {...paging.pagination} />}
      <div className="wf-shortcuts">
        <label><Switch size="small" checked={autoNext} onChange={value => { setAutoNext(value); saveAutoNext(value); }} /> 处理完自动打开下一条</label>
        <span className="muted">共 {paging.data.total} 条</span>
        <div><kbd>J</kbd><kbd>K</kbd> 上下切换 <kbd>A</kbd> 同意 <kbd>R</kbd> 退回修改 <kbd>T</kbd> 转交 <kbd>Ctrl</kbd><kbd>Enter</kbd> 提交</div>
      </div>
    </aside>
    <main className="wf-task-main">{panel}</main>
  </div>;
}

const decodeHandled = decodePage(decodeHandledTask);
const handledResultLabels: Record<string, string> = { ...actionRunStatusLabels, ...approvalStatusLabels };

/** 已处理：我提交过的动作与做过决定的审批（接口 26）。 */
function HandledTasks() {
  const [taskType, setTaskType] = useState<'ALL' | 'ACTION' | 'APPROVAL'>('ALL');
  const paging = usePaging<HandledTask>(workflowPaths.handledTasks, decodeHandled);
  const [keyword, setKeyword] = useState('');
  function query(nextType: typeof taskType, nextKeyword: string) {
    paging.applyFilters({ ...(nextType === 'ALL' ? {} : { taskType: nextType }), ...(nextKeyword.trim() ? { keyword: nextKeyword.trim() } : {}) });
  }
  return <section className="table-panel">
    <div className="wf-filters" style={{ marginBottom: 12 }}>
      <Segmented value={taskType} onChange={value => { setTaskType(value as typeof taskType); query(value as typeof taskType, keyword); }}
        options={[{ value: 'ALL', label: '全部' }, { value: 'ACTION', label: '执行的动作' }, { value: 'APPROVAL', label: '做过的审批' }]} />
      <Input.Search allowClear placeholder="搜索实例标题" style={{ width: 260 }} value={keyword} onChange={event => setKeyword(event.target.value)}
        onSearch={value => query(taskType, value)} />
    </div>
    <Table<HandledTask> rowKey={row => `${row.taskType}:${row.taskId}`} loading={paging.loading} dataSource={paging.error ? [] : paging.data.items} pagination={paging.pagination} columns={[
      { title: '实例', render: (_, row) => <div><Link to={`${workflowRoutes.instances}/${row.instanceId}`}>{row.instanceTitle}</Link>
        <div className="muted">{row.definitionName ?? ''}</div></div> },
      { title: '节点', render: (_, row) => <span>{row.nodeName ?? row.nodeKey}{row.roundNo > 1 ? ` · 第 ${row.roundNo} 轮` : ''}</span> },
      { title: '类型', width: 110, render: (_, row) => row.taskType === 'ACTION' ? `执行 · ${row.actionKey ?? ''}` : '审批' },
      { title: '结果', width: 110, render: (_, row) => <Tag color={row.result === 'VERIFIED' || row.result === 'APPROVED' ? 'success'
        : row.result === 'UNVERIFIED' || row.result === 'REJECTED' ? 'error' : row.result === 'RETURNED' ? 'warning' : 'default'}>{handledResultLabels[row.result] ?? row.result}</Tag> },
      { title: '实例状态', width: 100, render: (_, row) => <Tag color={instanceStatusColors[row.instanceStatus]}>{instanceStatusLabels[row.instanceStatus] ?? row.instanceStatus}</Tag> },
      { title: '处理时间', width: 140, render: (_, row) => shortTime(row.handledTime) },
    ]} /></section>;
}

/** 我发起的：按发起人为自己查询实例。 */
function StartedByMe({ actorId }: { actorId: string }) {
  const filters = useMemo(() => ({ scope: 'MINE', startedBy: actorId }), [actorId]);
  const paging = usePaging<InstanceSummary>(workflowPaths.instances, decodeInstances, filters);
  return <section className="table-panel">
    <Table<InstanceSummary> rowKey="instanceId" loading={paging.loading} dataSource={paging.error ? [] : paging.data.items} pagination={paging.pagination} columns={[
      { title: '实例', render: (_, row) => <Link to={`${workflowRoutes.instances}/${row.instanceId}`}>{row.title}</Link> },
      { title: '业务关联键', dataIndex: 'businessKey', render: value => value ?? '—' },
      { title: '状态', width: 100, render: (_, row) => <Tag color={instanceStatusColors[row.status]}>{instanceStatusLabels[row.status]}</Tag> },
      { title: '发起时间', width: 160, render: (_, row) => shortTime(row.startTime) },
    ]} /></section>;
}
