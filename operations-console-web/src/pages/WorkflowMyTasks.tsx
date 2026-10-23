import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Checkbox, Empty, Input, Pagination, Popover, Segmented, Switch, Tag } from 'antd';
import { CheckOutlined, CloseOutlined, EditOutlined, InfoCircleOutlined, QuestionCircleOutlined, SearchOutlined, SettingOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { createPagingLoader } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { batchStatus, batchSubmission, resolveShortcut, runBatch } from '../workflow/runtimeModel';
import type { BatchResult, BatchStatus } from '../workflow/runtimeModel';
import { BatchPanel, HandledDetail, TaskDetail } from '../workflow/TaskPanels';
import type { PanelHandles } from '../workflow/TaskPanels';
import {
  OVERDUE_HOURS, batchCandidates, batchIdle, enterBatch, exitBatch, groupCounts, groupTasks, handledResult, neighborGroup, nextGroupAfterDone, overdueText,
  relativeTime, toggleAll, toggleChecked,
} from '../workflow/taskModel';
import type { BatchMode, TaskGroup } from '../workflow/taskModel';
import { failureText, sendWorkflowCommand, useWorkflowCommand, workflowCommand } from '../workflow/useWorkflowCommand';
import { decodeHandledTask, decodeInstanceDetail, decodeMutation, decodePage, decodeTask, workflowPaths } from '../workflow/workflowApi';
import { instanceStatusLabel } from '../workflow/workflowLabels';
import { enumLabel, translator } from '../i18n/index';
import { useT } from '../i18n/react';
import type { HandledTask, InstanceDetail, Task } from '../workflow/workflowTypes';
import { LoadFailed, Spinner, canOpenInstances, useLoad } from '../workflow/WorkflowParts';
import { DETAIL_PRIORITY, DetailQueue, DetailRequestCancelled, planBatchFill, planPrefetch, retryAfterMillis } from '../workflow/prefetchModel';
import '../workflow/workflow.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
const tw = translator('workflow');
type TaskType = 'ALL' | 'ACTION' | 'APPROVAL';
/** 实例详情 429：会话层不透出 Retry-After，按默认短退避重试一次。 */
function detailRetryDelay(error: unknown): number | null {
  return error instanceof ApiError && error.status === 429 ? retryAfterMillis(null) : null;
}

const decodeTasks = decodePage(decodeTask);
const loadTaskPage = createPagingLoader(workflowPaths.myTasks, decodeTasks);
/** 待办全量读取：Platform 每页最多 100 条；最多读 5 页，超出时列表提示只显示前 500 条。 */
const TASK_PAGE_SIZE = 100;
const TASK_MAX_PAGES = 5;

/**
 * 读取全部待办（按标题关键字过滤走服务端）。合并后的列表项与「全部 / 填写 / 审批」计数都在前端按同一份全量数据计算，
 * 不再用服务端按条（每个动作一条）的 total，避免计数与列表项对不上；待办量小，全量读取代价可接受。
 */
function useAllTasks(keyword: string) {
  const [state, setState] = useState<{ items: Task[]; truncated: boolean; loading: boolean; error: string }>({ items: [], truncated: false, loading: true, error: '' });
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState(current => ({ ...current, loading: true, error: '' }));
    (async () => {
      const items = new Map<string, Task>(); let total = 0;
      for (let page = 1; page <= TASK_MAX_PAGES; page++) {
        const result = await loadTaskPage({ page, size: TASK_PAGE_SIZE }, controller.signal, keyword ? { keyword } : {});
        total = result.total;
        result.items.forEach(task => { if (!items.has(`${task.taskType}:${task.taskId}`)) items.set(`${task.taskType}:${task.taskId}`, task); });
        if (page * TASK_PAGE_SIZE >= result.total || !result.items.length) break;
      }
      return { items: [...items.values()], truncated: total > items.size };
    })().then(result => { if (!controller.signal.aborted) setState({ ...result, loading: false, error: '' }); })
      .catch(error => { if (!controller.signal.aborted) setState(current => ({ ...current, loading: false, error: error instanceof Error ? error.message : tw('myTasks.loadFailed') })); });
    return () => controller.abort();
  }, [keyword, reload]);
  return { ...state, refresh: useCallback(() => setReload(value => value + 1), []) };
}
const autoNextKey = 'workflow.my-tasks.auto-next';
function readAutoNext() { try { return window.localStorage.getItem(autoNextKey) !== '0'; } catch { return true; } }
function saveAutoNext(value: boolean) { try { window.localStorage.setItem(autoNextKey, value ? '1' : '0'); } catch { /* 偏好可丢失 */ } }

/** 快捷键与说明 key（workflow.myTasks.shortcut.<key>）。 */
const shortcutRows: [string[], string][] = [[['J', 'K'], 'NAVIGATE'], [['A'], 'APPROVE'], [['R'], 'RETURN'], [['T'], 'TRANSFER'], [['Ctrl', 'Enter'], 'SUBMIT']];

/** 我的待办（原型 01—04）：一组页签「待办 N / 已办」，右上 ⚙ 设置与 ? 快捷键浮层。 */
export function WorkflowMyTasks({ resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('workflow'), tNav = useT('nav');
  const canOpenInstance = canOpenInstances(resources);
  const [tab, setTab] = useState<'TODO' | 'HANDLED'>('TODO');
  const [todoTotal, setTodoTotal] = useState<number | null>(null);
  const [autoNext, setAutoNext] = useState(readAutoNext);
  const settings = <div className="wf-popover">
    <label className="wf-popover-row"><span><strong>{t('myTasks.autoNext')}</strong><div className="muted">{t('myTasks.autoNextHint')}</div></span>
      <Switch checked={autoNext} onChange={value => { setAutoNext(value); saveAutoNext(value); }} /></label>
    <div className="wf-popover-row"><span><strong>{t('myTasks.overdueThreshold')}</strong><div className="muted">{t('myTasks.overdueHint')}</div></span><span>{t('duration.hours', { count: OVERDUE_HOURS })}</span></div>
  </div>;
  const shortcuts = <div className="wf-popover">{shortcutRows.map(([keys, label]) => <div key={label} className="wf-popover-row">
    <span>{keys.map(key => <kbd key={key}>{key}</kbd>)}</span><span className="muted">{enumLabel(t, 'myTasks.shortcut', label)}</span></div>)}
    <div className="muted">{t('myTasks.shortcutInputHint')}</div></div>;
  return <div className="wf-page">
    <div className="wf-page-head"><h1>{tNav('resource.OPERATIONS_WORKFLOW_MY_TASKS')}</h1>
      <nav className="wf-tabs">
        <button type="button" className={`wf-tab${tab === 'TODO' ? ' is-active' : ''}`} onClick={() => setTab('TODO')}>{t('myTasks.tab.todo')}{todoTotal !== null && <span className="wf-tab-count">{todoTotal}</span>}</button>
        <button type="button" className={`wf-tab${tab === 'HANDLED' ? ' is-active' : ''}`} onClick={() => setTab('HANDLED')}>{t('myTasks.tab.handled')}</button>
      </nav>
      <span className="wf-grow" />
      <Popover trigger="click" placement="bottomRight" title={t('myTasks.settings')} content={settings}><Button className="wf-icon-btn" aria-label={t('myTasks.settings')} icon={<SettingOutlined />} /></Popover>
      <Popover trigger="click" placement="bottomRight" title={t('myTasks.shortcuts')} content={shortcuts}><Button className="wf-icon-btn" aria-label={t('myTasks.shortcuts')} icon={<QuestionCircleOutlined />} /></Popover>
    </div>
    {tab === 'TODO' ? <TodoWorkspace actorId={actorId} canOpenInstance={canOpenInstance} autoNext={autoNext} onTotal={setTodoTotal} onAuthorizationChanged={onAuthorizationChanged} />
      : <HandledWorkspace canOpenInstance={canOpenInstance} />}
  </div>;
}

function TodoWorkspace({ actorId, canOpenInstance, autoNext, onTotal, onAuthorizationChanged }: {
  actorId: string; canOpenInstance: boolean; autoNext: boolean; onTotal: (total: number | null) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('workflow');
  const { message } = App.useApp();
  const [params, setParams] = useSearchParams();
  const [taskType, setTaskType] = useState<TaskType>('ALL');
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [searching, setSearching] = useState(false);
  const all = useAllTasks(appliedKeyword);
  const tasks = all.items;
  // 列表项：同一实例同一节点运行上我的动作合为一项；类型筛选与计数都在前端按合并后的列表项计算。
  const groups = useMemo(() => groupTasks(tasks), [tasks]);
  const counts = useMemo(() => groupCounts(groups), [groups]);
  const visible = useMemo(() => taskType === 'ALL' ? groups : groups.filter(group => group.taskType === taskType), [groups, taskType]);
  const selectedKey = params.get('task');
  const selectedGroup = visible.find(group => group.key === selectedKey) ?? null;
  const selected = selectedGroup?.tasks[0] ?? null;
  const select = useCallback((group: TaskGroup | null) => {
    setParams(current => { const next = new URLSearchParams(current); if (group) next.set('task', group.key); else next.delete('task'); return next; }, { replace: true });
  }, [setParams]);
  const [batch, setBatch] = useState<BatchMode>(batchIdle);
  useEffect(() => { if (!batch.active && !all.loading && !selectedGroup && visible.length) select(visible[0]); }, [batch.active, all.loading, selectedGroup, visible, select]);
  useEffect(() => { onTotal(appliedKeyword || all.loading && !tasks.length ? null : counts.ALL); }, [counts.ALL, appliedKeyword, all.loading, tasks.length, onTotal]);
  // 实例详情缓存：处理区与批量资格判断共用。
  const [details, setDetails] = useState<Map<string, InstanceDetail>>(new Map());
  const [detailFailure, setDetailFailure] = useState<unknown>();
  const cache = useRef(details); cache.current = details;
  // 所有实例详情走同一串行队列：选中项 > 批量 > 预取，同一请求合并，最多 1 个在途，429 退避重试一次。
  const queue = useMemo(() => new DetailQueue<InstanceDetail>(async id => {
    const value = await session.request(workflowPaths.instance(id), {}, decodeInstanceDetail);
    setDetails(current => new Map(current).set(id, value));
    return value;
  }, { retryDelay: detailRetryDelay }), []);
  useEffect(() => () => { for (const tag of ['selected', 'batch', 'prefetch']) queue.cancel(tag); }, [queue]);
  const loadDetail = useCallback(async (instanceId: string, force = false) => {
    if (!force && cache.current.has(instanceId)) return cache.current.get(instanceId)!;
    return queue.request(instanceId, DETAIL_PRIORITY.SELECTED, 'refresh');
  }, [queue]);
  useEffect(() => {
    if (!selected || batch.active) return;
    setDetailFailure(undefined);
    if (cache.current.has(selected.instanceId)) return;
    queue.request(selected.instanceId, DETAIL_PRIORITY.SELECTED, 'selected')
      .catch(error => { if (!(error instanceof DetailRequestCancelled)) setDetailFailure(error); });
    return () => queue.cancel('selected');
  }, [selected?.instanceId, batch.active, queue]); // 只随所选实例变化，避免缓存更新时重复请求
  // 审批待办预取少量详情，用于判断能否批量同意；排在选中项之后。
  const approvalIds = tasks.filter(task => task.taskType === 'APPROVAL').map(task => task.instanceId).join(',');
  useEffect(() => {
    if (batch.active) return; // 批量模式由按需补齐接管
    const ids = planPrefetch(approvalIds ? approvalIds.split(',') : [], selected?.instanceId ?? null, new Set(cache.current.keys()));
    ids.forEach(id => { queue.request(id, DETAIL_PRIORITY.PREFETCH, 'prefetch').catch(() => undefined); });
    return () => queue.cancel('prefetch');
  }, [approvalIds, selected?.instanceId, batch.active, queue]);
  // 批量模式：按需补齐勾选项（优先）与其他审批待办的详情；重试后仍失败的实例标为无法检查。
  const [failed, setFailed] = useState<ReadonlySet<string>>(new Set());
  const failedRef = useRef(failed); failedRef.current = failed;
  const checkedInstanceIds = tasks.filter(task => batch.checked.includes(task.taskId)).map(task => task.instanceId).join(',');
  useEffect(() => {
    if (!batch.active) { if (failedRef.current.size) setFailed(new Set()); return; }
    const ids = planBatchFill(checkedInstanceIds ? checkedInstanceIds.split(',') : [], approvalIds ? approvalIds.split(',') : [],
      new Set(cache.current.keys()), failedRef.current);
    ids.forEach(id => {
      queue.request(id, DETAIL_PRIORITY.BATCH, 'batch').catch(error => {
        if (!(error instanceof DetailRequestCancelled)) setFailed(current => new Set(current).add(id));
      });
    });
    return () => queue.cancel('batch');
  }, [batch.active, checkedInstanceIds, approvalIds, queue]);
  const statuses = useMemo(() => new Map<string, BatchStatus>(tasks.map(task => [task.taskId,
    batchStatus(task, details.get(task.instanceId), actorId, failed.has(task.instanceId))])), [tasks, details, actorId, failed]);
  const [batchResults, setBatchResults] = useState<Map<string, BatchResult>>(new Map());
  const [batchRunning, setBatchRunning] = useState(false); const [batchComment, setBatchComment] = useState('');
  const command = useWorkflowCommand(onAuthorizationChanged);
  const handles = useRef<PanelHandles>({});
  const detail = selected ? details.get(selected.instanceId) : undefined;
  const candidates = batchCandidates(tasks, statuses);
  const eligibleIds = candidates.eligible.map(task => task.taskId);
  const batchTasks = tasks.filter(task => batch.checked.includes(task.taskId));
  // 开始提交时冻结勾选项与资格状态：提交后列表刷新会移除已同意的项，结果仍按冻结列表展示，直到完成或退出批量模式。
  const [frozenBatch, setFrozenBatch] = useState<{ tasks: Task[]; statuses: ReadonlyMap<string, BatchStatus> } | null>(null);

  function startBatch() { setBatch(enterBatch(selectedGroup?.key ?? null)); setBatchResults(new Map()); setFrozenBatch(null); setBatchComment(''); }
  function leaveBatch() {
    const { mode, restore } = exitBatch(batch);
    setBatch(mode); setBatchResults(new Map()); setFrozenBatch(null); setBatchComment('');
    const group = visible.find(item => item.key === restore) ?? null;
    if (group) select(group); else if (!visible.find(item => item.key === selectedKey)) select(visible[0] ?? null);
  }
  function changed(finished: boolean) {
    if (!selected || !selectedGroup) return;
    const done = selectedGroup;
    void loadDetail(done.instanceId, true).catch(() => undefined);
    all.refresh();
    if (finished && autoNext) select(nextGroupAfterDone(visible, done.key));
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
    setBatchRunning(false); all.refresh();
    for (const id of new Set(batchTasks.map(task => task.instanceId))) void loadDetail(id, true).catch(() => undefined);
  }
  // 快捷键：J/K 在列表项之间切换，A 选中同意，R 选中退回修改，T 转交，Ctrl+Enter 完成或提交选中的决定；批量模式下不响应处理类快捷键。
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      const editable = !!target?.closest('input, textarea, select, [contenteditable="true"]');
      const action = resolveShortcut({ key: event.key, ctrlKey: event.ctrlKey, metaKey: event.metaKey, altKey: event.altKey, editable,
        taskType: batch.active ? null : selected?.taskType ?? null });
      if (!action || (batch.active && (action === 'NEXT' || action === 'PREVIOUS'))) return;
      event.preventDefault();
      if (action === 'NEXT' || action === 'PREVIOUS') {
        const next = neighborGroup(visible, selectedGroup?.key ?? null, action === 'NEXT' ? 1 : -1);
        if (next) select(next); else void message.info(action === 'NEXT' ? t('myTasks.last') : t('myTasks.first'));
      }
      if (action === 'SUBMIT') handles.current.submit?.();
      if (action === 'APPROVE') handles.current.approve?.();
      if (action === 'RETURN') handles.current.giveBack?.();
      if (action === 'TRANSFER') handles.current.transfer?.();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [visible, selected, selectedGroup, select, batch.active, message, t]);

  let panel;
  if (batch.active) panel = <BatchPanel tasks={frozenBatch?.tasks ?? batchTasks} details={details} statuses={frozenBatch?.statuses ?? statuses}
    results={batchResults} running={batchRunning} comment={batchComment} untouched={Math.max(0, groups.length - batchTasks.length)}
    onComment={setBatchComment} onRemove={taskId => setBatch(current => ({ ...current, checked: current.checked.filter(id => id !== taskId) }))}
    onRun={() => void runBatchApprove()} onCancel={leaveBatch} onFinish={leaveBatch} />;
  else if (!selected || !selectedGroup) panel = <div className="wf-detail wf-detail-empty"><Empty description={all.loading ? t('batch.loading') : t('myTasks.emptyTodo')} /></div>;
  else if (detailFailure) panel = <div className="wf-detail wf-detail-empty"><LoadFailed failure={detailFailure}
    retry={() => { setDetailFailure(undefined); loadDetail(selected.instanceId, true).catch(setDetailFailure); }} /></div>;
  else if (!detail) panel = <div className="wf-detail wf-detail-empty"><Spinner /></div>;
  else panel = <TaskDetail key={selectedGroup.key} detail={detail} task={selected} actorId={actorId} command={command} handles={handles} onChanged={changed}
    canOpenInstance={canOpenInstance} />;

  const count = (value: number) => all.loading && !tasks.length ? '' : ` ${value}`;
  const itemOf = (task: Task) => <div key={task.taskId} role="button" tabIndex={0}
    className={`wf-inbox-item${batch.checked.includes(task.taskId) ? ' is-selected' : ''}`}
    onClick={() => { if (!batchRunning) setBatch(current => ({ ...current, checked: toggleChecked(current.checked, task.taskId, eligibleIds) })); }}
    onKeyDown={event => { if (event.key === 'Enter' && !batchRunning) setBatch(current => ({ ...current, checked: toggleChecked(current.checked, task.taskId, eligibleIds) })); }}>
    <Checkbox checked={batch.checked.includes(task.taskId)} disabled={batchRunning} onClick={event => event.stopPropagation()}
      onChange={() => setBatch(current => ({ ...current, checked: toggleChecked(current.checked, task.taskId, eligibleIds) }))} />
    <span className="wf-inbox-icon is-approval" title={t('panel.tab.approval')}><CheckOutlined /></span>
    <div className="wf-inbox-body">
      <strong className="wf-ellipsis">{task.instanceTitle}</strong>
      <span className="muted wf-ellipsis">{[task.definitionName, task.nodeName || task.nodeKey].filter(Boolean).join(' · ')}{task.roundNo > 1 ? ` · ${t('instance.round', { round: task.roundNo })}` : ''}</span>
    </div>
    <TaskTime since={task.waitingSince} />
  </div>;
  return <div className="wf-inbox">
    <aside className="wf-inbox-list">
      {batch.active ? <>
        <div className="wf-batch-strip"><span><UnorderedListOutlined /> {t('myTasks.batchStrip', { checked: batch.checked.length, eligible: eligibleIds.length })}</span><span className="wf-grow" />
          <Button size="small" type="text" className="wf-on-primary" disabled={batchRunning || !eligibleIds.length}
            onClick={() => setBatch(current => ({ ...current, checked: toggleAll(current.checked, eligibleIds) }))}>
            {eligibleIds.length && eligibleIds.every(id => batch.checked.includes(id)) ? t('myTasks.selectNone') : t('myTasks.selectAll')}</Button>
          <Button size="small" ghost disabled={batchRunning} icon={<CloseOutlined />} onClick={leaveBatch}>{t('myTasks.exitBatch')}</Button></div>
        <div className="wf-batch-note"><InfoCircleOutlined /> {candidates.checking ? t('myTasks.batchNoteChecking', { hidden: Math.max(0, groups.length - candidates.eligible.length), checking: candidates.checking })
          : t('myTasks.batchNote', { hidden: Math.max(0, groups.length - candidates.eligible.length) })}</div>
      </> : <div className="wf-inbox-tools">
        <Segmented size="small" value={taskType} onChange={value => setTaskType(value as TaskType)}
          options={[{ value: 'ALL', label: `${t('catalog.all')}${count(counts.ALL)}` }, { value: 'ACTION', label: `${t('myTasks.type.ACTION')}${count(counts.ACTION)}` },
            { value: 'APPROVAL', label: `${t('panel.tab.approval')}${count(counts.APPROVAL)}` }]} />
        <span className="wf-grow" />
        <Button type="text" size="small" aria-label={t('started.searchLabel')} icon={<SearchOutlined />} onClick={() => setSearching(!searching)} />
        <Button type="link" size="small" icon={<UnorderedListOutlined />} onClick={startBatch}>{t('myTasks.batch')}</Button>
      </div>}
      {searching && !batch.active && <div className="wf-inbox-search"><Input.Search allowClear autoFocus placeholder={t('started.search')} value={keyword}
        onChange={event => setKeyword(event.target.value)} onSearch={value => setAppliedKeyword(value.trim())} /></div>}
      {all.error && <Alert type="warning" showIcon title={t('myTasks.loadFailed')} action={<Button size="small" onClick={all.refresh}>{t('action.retry')}</Button>} />}
      <div className="wf-inbox-items">
        {batch.active ? candidates.eligible.map(itemOf) : visible.map(group => {
          const isSelected = group.key === selectedGroup?.key;
          const many = group.tasks.length > 1;
          return <div key={group.key} role="button" tabIndex={0} className={`wf-inbox-item${isSelected ? ' is-selected' : ''}`}
            onClick={() => select(group)} onKeyDown={event => { if (event.key === 'Enter') select(group); }}>
            <span className={`wf-inbox-icon is-${group.taskType.toLowerCase()}`} title={group.taskType === 'ACTION' ? t('myTasks.type.ACTION') : t('panel.tab.approval')}>
              {group.taskType === 'ACTION' ? <EditOutlined /> : <CheckOutlined />}</span>
            <div className="wf-inbox-body">
              <strong className="wf-ellipsis">{group.instanceTitle}</strong>
              <span className="muted wf-ellipsis">{[group.definitionName, group.nodeName || group.nodeKey].filter(Boolean).join(' · ')}
                {group.roundNo > 1 ? ` · ${t('instance.round', { round: group.roundNo })}` : ''}{many ? ` · ${t('launch.preview.actions', { count: group.tasks.length })}` : ''}</span>
            </div>
            <TaskTime since={group.waitingSince} />
          </div>;
        })}
        {!all.loading && !(batch.active ? candidates.eligible : visible).length && <Empty description={batch.active ? (candidates.checking ? t('myTasks.batchChecking') : t('myTasks.batchEmpty')) : t('myTasks.noMatch')} />}
        {all.truncated && <div className="muted wf-inbox-note">{t('myTasks.truncated', { count: TASK_PAGE_SIZE * TASK_MAX_PAGES })}</div>}
      </div>
    </aside>
    {panel}
  </div>;
}

/** 列表右侧：超时显示红色时长，否则相对时间。 */
function TaskTime({ since }: { since: string }) {
  const overdue = overdueText(since);
  return overdue ? <span className="wf-overdue">{overdue}</span> : <span className="wf-inbox-time">{relativeTime(since)}</span>;
}

const decodeHandled = decodePage(decodeHandledTask);

/** 已办（原型 04）：列表同待办风格，带我的处理结果；点击在右侧只读打开。 */
function HandledWorkspace({ canOpenInstance }: { canOpenInstance: boolean }) {
  const t = useT('workflow');
  const [taskType, setTaskType] = useState<TaskType>('ALL');
  const [keyword, setKeyword] = useState(''); const [searching, setSearching] = useState(false);
  const paging = usePaging<HandledTask>(workflowPaths.handledTasks, decodeHandled);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const keyOf = (row: HandledTask) => `${row.taskType}:${row.taskId}`;
  const items = paging.error ? [] : paging.data.items;
  const selected = items.find(row => keyOf(row) === selectedKey) ?? items[0] ?? null;
  function query(nextType: TaskType, nextKeyword: string) {
    paging.applyFilters({ ...(nextType === 'ALL' ? {} : { taskType: nextType }), ...(nextKeyword.trim() ? { keyword: nextKeyword.trim() } : {}) });
  }
  const detail = useLoad<InstanceDetail>(selected ? workflowPaths.instance(selected.instanceId) : null, decodeInstanceDetail);
  const loaded = detail.data && selected && detail.data.instance.instanceId === selected.instanceId ? detail.data : undefined;
  return <div className="wf-inbox">
    <aside className="wf-inbox-list">
      <div className="wf-inbox-tools">
        <Segmented size="small" value={taskType} onChange={value => { setTaskType(value as TaskType); query(value as TaskType, keyword); }}
          options={[{ value: 'ALL', label: t('catalog.all') }, { value: 'ACTION', label: t('myTasks.type.ACTION') }, { value: 'APPROVAL', label: t('panel.tab.approval') }]} />
        <span className="wf-grow" /><Button type="text" size="small" aria-label={t('started.searchLabel')} icon={<SearchOutlined />} onClick={() => setSearching(!searching)} />
      </div>
      {searching && <div className="wf-inbox-search"><Input.Search allowClear autoFocus placeholder={t('started.search')} value={keyword}
        onChange={event => setKeyword(event.target.value)} onSearch={value => query(taskType, value)} /></div>}
      <div className="wf-inbox-items">
        {items.map(row => {
          const result = handledResult(row.result);
          return <div key={keyOf(row)} role="button" tabIndex={0} className={`wf-inbox-item${selected && keyOf(selected) === keyOf(row) ? ' is-selected' : ''}`}
            onClick={() => setSelectedKey(keyOf(row))} onKeyDown={event => { if (event.key === 'Enter') setSelectedKey(keyOf(row)); }}>
            <span className={`wf-inbox-icon is-${row.taskType.toLowerCase()}`}>{row.taskType === 'ACTION' ? <EditOutlined /> : <CheckOutlined />}</span>
            <div className="wf-inbox-body"><strong className="wf-ellipsis">{row.instanceTitle}</strong>
              <span className="muted wf-ellipsis">{row.nodeName ?? row.nodeKey} · {relativeTime(row.handledTime)}</span></div>
            <div className="wf-inbox-result"><Tag color={result.color}>{result.label}</Tag>
              <span className="muted">{t('myTasks.instanceStatus', { status: instanceStatusLabel(row.instanceStatus) })}</span></div>
          </div>;
        })}
        {!paging.loading && !items.length && <Empty description={t('myTasks.emptyHandled')} />}
      </div>
      {paging.data.total > paging.data.size && <div className="wf-inbox-pager"><Pagination size="small" simple {...paging.pagination} /></div>}
    </aside>
    {!selected ? <div className="wf-detail wf-detail-empty"><Empty description={paging.loading ? t('batch.loading') : t('myTasks.selectHint')} /></div>
      : detail.failure ? <div className="wf-detail wf-detail-empty"><LoadFailed failure={detail.failure} retry={detail.refresh} /></div>
      : !loaded ? <div className="wf-detail wf-detail-empty"><Spinner /></div>
      : <HandledDetail key={keyOf(selected)} detail={loaded} handled={selected} canOpenInstance={canOpenInstance} />}
  </div>;
}
