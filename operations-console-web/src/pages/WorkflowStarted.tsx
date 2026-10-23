import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, Pagination, Segmented, Select, Tag } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { TerminateModal } from '../workflow/InstanceDetailView';
import { LaunchModal } from '../workflow/LaunchModal';
import { latestRuns, traversedEdges, visualState } from '../workflow/runtimeModel';
import { ActivityTimeline, FullFlowModal } from '../workflow/TaskPanels';
import { durationText, waitingForMe, waitingOn } from '../workflow/taskModel';
import { WorkflowCanvas } from '../workflow/WorkflowCanvas';
import { decodeDefinitionSummary, decodeInstanceDetail, decodeInstanceSummary, decodePage, workflowPaths, COUNT_PAGE_SIZE } from '../workflow/workflowApi';
import { instanceStatusColors, instanceStatusLabels } from '../workflow/workflowLabels';
import type { DefinitionSummary, GraphNode, InstanceDetail, InstanceSummary } from '../workflow/workflowTypes';
import { LoadFailed, Spinner, canOpenInstances, hasButton, personName, shortTime, useLoad, workflowButtons } from '../workflow/WorkflowParts';
import '../workflow/workflow.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type Tab = 'RUNNING' | 'ENDED';
type Ended = 'COMPLETED' | 'TERMINATED';
const decodeInstances = decodePage(decodeInstanceSummary);
const decodeDefinitions = decodePage(decodeDefinitionSummary);

/**
 * 我发起的（原型 05）：进行中 / 已结束两个页签；表格列出当前步骤与等待人、已用时长；右侧为小流程图与活动时间线。
 * 数据来自实例列表 scope=MINE&startedBy=本人；撤回 = 终止实例（现有接口，发起人可在运行中终止）。
 */
export function WorkflowStarted({ resources, actorId, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const [tab, setTab] = useState<Tab>('RUNNING');
  const [ended, setEnded] = useState<Ended>('COMPLETED');
  const [definitionId, setDefinitionId] = useState<string>();
  const [keyword, setKeyword] = useState(''); const [searching, setSearching] = useState(false);
  // 实例列表只接受一个状态：已结束页签再分已完成 / 已终止。
  const filters = useMemo(() => ({ scope: 'MINE', startedBy: actorId, status: tab === 'RUNNING' ? 'RUNNING' : ended,
    ...(definitionId ? { definitionId } : {}), ...(keyword.trim() ? { keyword: keyword.trim() } : {}) }), [actorId, tab, ended, definitionId, keyword]);
  const paging = usePaging<InstanceSummary>(workflowPaths.instances, decodeInstances, filters);
  const { applyFilters } = paging;
  const [applied, setApplied] = useState(filters);
  useEffect(() => { if (JSON.stringify(applied) !== JSON.stringify(filters)) { setApplied(filters); applyFilters(filters); } }, [filters, applied, applyFilters]);
  const runningCount = usePaging<InstanceSummary>(workflowPaths.instances, decodeInstances, { scope: 'MINE', startedBy: actorId, status: 'RUNNING' },
    { initialPaging: { page: 1, size: COUNT_PAGE_SIZE }, notifyOnError: false });
  const [definitions, setDefinitions] = useState<DefinitionSummary[]>([]);
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages(workflowPaths.launchable, controller.signal, undefined, {}, decodeDefinitions).then(setDefinitions).catch(() => undefined);
    return () => controller.abort();
  }, []);
  const items = paging.error ? [] : paging.data.items;
  const selectedId = params.get('instance');
  const selected = items.find(item => item.instanceId === selectedId) ?? items[0] ?? null;
  const choose = useCallback((id: string | null) => setParams(current => {
    const next = new URLSearchParams(current); if (id) next.set('instance', id); else next.delete('instance'); return next;
  }, { replace: true }), [setParams]);
  const launching = params.get('launch');
  const canStart = hasButton(resources, workflowButtons.instanceStart);
  function refresh() { paging.refresh(); runningCount.refresh(); }
  return <div className="wf-page">
    <div className="wf-page-head"><h1>我发起的</h1>
      <nav className="wf-tabs">
        <button type="button" className={`wf-tab${tab === 'RUNNING' ? ' is-active' : ''}`} onClick={() => setTab('RUNNING')}>进行中
          {!runningCount.loading && !runningCount.error && <span className="wf-tab-count">{runningCount.data.total}</span>}</button>
        <button type="button" className={`wf-tab${tab === 'ENDED' ? ' is-active' : ''}`} onClick={() => setTab('ENDED')}>已结束</button>
      </nav>
      <span className="wf-grow" />
      {canStart && <Button type="primary" onClick={() => setParams(current => { const next = new URLSearchParams(current); next.set('launch', ''); return next; })} icon={<PlusOutlined />}>发起流程</Button>}
    </div>
    <div className="wf-started">
      <section className="wf-started-list">
        <div className="wf-inbox-tools">
          <Select allowClear value={definitionId} onChange={setDefinitionId} style={{ width: 200 }} placeholder="全部流程"
            options={definitions.map(item => ({ value: item.definitionId, label: item.definitionName }))} />
          {tab === 'ENDED' && <Segmented size="small" value={ended} onChange={value => setEnded(value as Ended)}
            options={[{ value: 'COMPLETED', label: '已完成' }, { value: 'TERMINATED', label: '已终止' }]} />}
          <span className="wf-grow" />
          {searching ? <Input.Search allowClear autoFocus size="small" style={{ width: 220 }} placeholder="搜索实例标题" onSearch={setKeyword} />
            : <Button type="text" size="small" aria-label="搜索" icon={<SearchOutlined />} onClick={() => setSearching(true)} />}
        </div>
        <table className="wf-table wf-started-table"><thead><tr><th>实例</th><th>当前步骤</th><th>已用时长</th><th>状态</th></tr></thead>
          <tbody>{items.map(row => {
            const mine = waitingForMe(row, actorId);
            return <tr key={row.instanceId} className={selected?.instanceId === row.instanceId ? 'is-selected' : undefined} onClick={() => choose(row.instanceId)}>
              <td><strong>{row.title}</strong><div className="muted">{row.definitionName ?? '—'} · {shortTime(row.startTime)} 发起</div></td>
              <td>{waitingOn(row, actorId)}</td>
              <td>{durationText(row.startTime, row.endTime)}</td>
              <td>{mine ? <Tag color="orange">待你处理</Tag> : <Tag color={instanceStatusColors[row.status]}>{instanceStatusLabels[row.status]}</Tag>}</td>
            </tr>;
          })}</tbody></table>
        {paging.loading && !items.length && <Spinner />}
        {!paging.loading && !items.length && <Empty description={tab === 'RUNNING' ? '没有进行中的实例' : '没有已结束的实例'} />}
        {paging.data.total > paging.data.size && <div className="wf-inbox-pager"><Pagination size="small" {...paging.pagination} /></div>}
      </section>
      {selected ? <StartedDetail key={selected.instanceId} instanceId={selected.instanceId} actorId={actorId} resources={resources}
        onAuthorizationChanged={onAuthorizationChanged} onChanged={refresh} />
        : <aside className="wf-started-detail wf-detail-empty"><Empty description="选择左侧实例查看进度" /></aside>}
    </div>
    {launching !== null && <LaunchModal initialDefinitionId={launching || null} onAuthorizationChanged={onAuthorizationChanged}
      onStarted={id => { setTab('RUNNING'); choose(id); refresh(); }}
      onClose={() => setParams(current => { const next = new URLSearchParams(current); next.delete('launch'); return next; })} />}
  </div>;
}

/** 右侧只读：标题与已用时长、撤回（终止实例）、流程位置小图（当前节点高亮）、活动时间线。 */
function StartedDetail({ instanceId, actorId, resources, onAuthorizationChanged, onChanged }: {
  instanceId: string; actorId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const detail = useLoad<InstanceDetail>(workflowPaths.instance(instanceId), decodeInstanceDetail);
  const [terminating, setTerminating] = useState(false);
  const [flow, setFlow] = useState(false);
  const value = detail.data;
  const runs = useMemo(() => latestRuns(value?.nodeRuns ?? []), [value]);
  const edges = useMemo(() => value ? traversedEdges(value.graph, value.nodeRuns) : new Set<string>(), [value]);
  const decorate = useCallback((node: GraphNode) => ({
    state: node.type === 'START' && value?.nodeRuns.length ? 'done' as const : visualState(runs.get(node.key)) }), [runs, value]);
  const edgeState = useCallback((key: string) => edges.has(key) ? 'done' as const : 'pending' as const, [edges]);
  if (detail.loading && !value) return <aside className="wf-started-detail"><Spinner /></aside>;
  if (!value) return <aside className="wf-started-detail"><LoadFailed failure={detail.failure} retry={detail.refresh} title="实例加载失败" /></aside>;
  // 现有终止规则：运行中，发起人本人或有管理实例权限的人。
  const canWithdraw = value.instance.status === 'RUNNING' && (value.instance.startedBy === actorId || hasButton(resources, workflowButtons.instanceManage));
  return <aside className="wf-started-detail">
    <div className="wf-detail-title"><h2>{value.instance.title}</h2><span className="wf-grow" />
      {canWithdraw && <Button onClick={() => setTerminating(true)}>撤回（终止实例）</Button>}</div>
    <div className="wf-meta">{value.definitionName} v{value.versionNo} · {shortTime(value.instance.startTime)} 发起 · {value.instance.endTime ? '历时' : '已用'} {durationText(value.instance.startTime, value.instance.endTime)}</div>
    {value.instance.status === 'TERMINATED' && <div className="muted">已由 {personName(value.terminatedBy, value.people)} 终止{value.terminationReason ? `：${value.terminationReason}` : ''}</div>}
    <section className="wf-section"><div className="wf-section-title">流程位置<span className="wf-grow" />
      <Button type="link" size="small" className="wf-small-link" onClick={() => setFlow(true)}>完整详情</Button></div>
      <WorkflowCanvas graph={value.graph} editable={false} selection={null} onSelect={() => {}} decorate={decorate} edgeState={edgeState} height={220} /></section>
    <section className="wf-section"><div className="wf-section-title">活动</div><ActivityTimeline detail={value} actorId={actorId} /></section>
    {flow && <FullFlowModal detail={value} canOpenInstance={canOpenInstances(resources)} onClose={() => setFlow(false)} />}
    {terminating && <TerminateModal title="撤回（终止实例）" instanceId={instanceId} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setTerminating(false)} onDone={() => { setTerminating(false); detail.refresh(); onChanged(); }} />}
  </aside>;
}
