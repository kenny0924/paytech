import { useEffect, useMemo, useState } from 'react';
import { Button, Input, Progress, Segmented, Select, Table, Tag } from 'antd';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { InstanceDetailView } from '../workflow/InstanceDetailView';
import { LaunchModal } from '../workflow/LaunchModal';
import { decodeDefinitionSummary, decodeInstanceSummary, decodePage, workflowPaths } from '../workflow/workflowApi';
import { instanceStatusColors, instanceStatusLabels, nodeRunStatusLabels } from '../workflow/workflowLabels';
import type { DefinitionSummary, InstanceSummary } from '../workflow/workflowTypes';
import { PersonSelect, hasButton, shortTime, workflowButtons, workflowRoutes } from '../workflow/WorkflowParts';
import '../workflow/workflow.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
const decodeInstances = decodePage(decodeInstanceSummary);
const decodeDefinitions = decodePage(decodeDefinitionSummary);

/** 流程实例：列表 + 发起弹窗；/:instanceId 为实例详情（原型 03—06）。 */
export function WorkflowInstances(props: Props) {
  const { instanceId } = useParams();
  if (instanceId) return <InstanceDetailView key={instanceId} instanceId={instanceId} {...props} />;
  return <InstanceList {...props} />;
}

type StatusTab = 'RUNNING' | 'COMPLETED' | 'TERMINATED' | 'ALL';

function InstanceList({ resources, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const canStart = hasButton(resources, workflowButtons.instanceStart);
  const canReadAll = hasButton(resources, workflowButtons.instanceReadAll);
  const [status, setStatus] = useState<StatusTab>('RUNNING');
  const [scope, setScope] = useState<'MINE' | 'ALL'>('MINE');
  const [definitionId, setDefinitionId] = useState<string>(); const [startedBy, setStartedBy] = useState<string>();
  const [businessKey, setBusinessKey] = useState(''); const [keyword, setKeyword] = useState('');
  const filters = useMemo(() => ({ scope, ...(status === 'ALL' ? {} : { status }), ...(definitionId ? { definitionId } : {}),
    ...(startedBy ? { startedBy } : {}), ...(businessKey.trim() ? { businessKey: businessKey.trim() } : {}), ...(keyword.trim() ? { keyword: keyword.trim() } : {}) }),
  [scope, status, definitionId, startedBy, businessKey, keyword]);
  const paging = usePaging<InstanceSummary>(workflowPaths.instances, decodeInstances, filters);
  const { applyFilters } = paging;
  const [applied, setApplied] = useState(filters);
  useEffect(() => { if (JSON.stringify(applied) !== JSON.stringify(filters)) { setApplied(filters); applyFilters(filters); } }, [filters, applied, applyFilters]);
  const [definitions, setDefinitions] = useState<DefinitionSummary[]>([]);
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages(workflowPaths.launchable, controller.signal, undefined, {}, decodeDefinitions).then(setDefinitions).catch(() => undefined);
    return () => controller.abort();
  }, []);
  const launching = params.get('launch');
  return <div className="wf-page">
    <div className="wf-page-head"><h1>流程实例</h1>
      <Segmented value={status} onChange={value => setStatus(value as StatusTab)}
        options={(['RUNNING', 'COMPLETED', 'TERMINATED', 'ALL'] as StatusTab[]).map(value => ({ value, label: value === 'ALL' ? '全部' : instanceStatusLabels[value] }))} />
      <span className="wf-grow" />
      {canStart && <Button type="primary" onClick={() => setParams({ launch: '' })}>＋ 发起流程</Button>}
    </div>
    <div className="wf-filters">
      <Select value={scope} onChange={setScope} style={{ width: 160 }} options={[{ value: 'MINE', label: '范围：我参与的' }, ...(canReadAll ? [{ value: 'ALL', label: '范围：全部' }] : [])]} />
      <Select allowClear value={definitionId} onChange={setDefinitionId} style={{ width: 220 }} placeholder="流程：全部"
        options={definitions.map(item => ({ value: item.definitionId, label: item.definitionName }))} />
      <div style={{ width: 200 }}><PersonSelect value={startedBy} onChange={value => setStartedBy(value)} placeholder="发起人：全部" /></div>
      <Input.Search allowClear placeholder="标题关键字" style={{ width: 220 }} onSearch={setKeyword} />
      <Input.Search allowClear placeholder="业务关联键（精确）" style={{ width: 220 }} onSearch={setBusinessKey} />
    </div>
    <section className="table-panel">
      <Table<InstanceSummary> rowKey="instanceId" loading={paging.loading} dataSource={paging.error ? [] : paging.data.items} pagination={paging.pagination}
        columns={[
          { title: '实例', render: (_, row) => <div><Link to={`${workflowRoutes.instances}/${row.instanceId}`}><strong>{row.title}</strong></Link>
            <div className="muted">{row.businessKey ?? ''}</div></div> },
          { title: '流程', width: 180, render: (_, row) => row.definitionName ? `${row.definitionName} · v${row.versionNo ?? '?'}` : '—' },
          { title: '进度', width: 200, render: (_, row) => { const total = row.totalTaskCount ?? 0; const done = row.completedTaskCount ?? 0;
            return <div><Progress percent={total ? Math.round(done / total * 100) : 0} showInfo={false} size="small" status={row.status === 'TERMINATED' ? 'exception' : undefined} />
              <span className="muted">{done} / {total} 个任务节点已完成</span></div>; } },
          { title: '当前节点 · 处理人', render: (_, row) => {
            const nodes = row.currentNodes ?? [];
            if (!nodes.length) return <Tag color={instanceStatusColors[row.status]}>{instanceStatusLabels[row.status]}</Tag>;
            return <div className="wf-handlers">{nodes.map(item => <span key={item.nodeKey} className="wf-handler">
              <span className="wf-avatar" title={item.handlers.map(person => person.displayName).join('、')}>{(item.handlers[0]?.displayName ?? '?').replace(/^ops\./, '').slice(0, 2).toUpperCase()}</span>
              {item.nodeName}{item.handlers.length > 0 && <span className="muted">{item.handlers.map(person => person.displayName).join('、')}</span>}
              <Tag color={item.status === 'APPROVING' ? 'orange' : item.status === 'WAITING' ? 'default' : 'blue'}>{nodeRunStatusLabels[item.status] ?? item.status}</Tag></span>)}</div>; } },
          { title: '发起', width: 170, render: (_, row) => <span className="muted">{row.startedByName ?? row.startedBy} · {shortTime(row.startTime)}</span> },
          { title: '', width: 40, render: (_, row) => <Link to={`${workflowRoutes.instances}/${row.instanceId}`}>›</Link> },
        ]} />
    </section>
    {launching !== null && <LaunchModal initialDefinitionId={launching || null} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setParams(current => { const next = new URLSearchParams(current); next.delete('launch'); return next; })} />}
  </div>;
}
