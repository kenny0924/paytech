import { useEffect, useMemo, useState } from 'react';
import { Button, Input, Progress, Segmented, Select, Table, Tag } from 'antd';
import { PlusOutlined, RightOutlined } from '@ant-design/icons';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { InstanceDetailView } from '../workflow/InstanceDetailView';
import { LaunchModal } from '../workflow/LaunchModal';
import { decodeDefinitionSummary, decodeInstanceSummary, decodePage, workflowPaths } from '../workflow/workflowApi';
import { instanceStatusColors, instanceStatusLabel, nodeRunStatusLabel } from '../workflow/workflowLabels';
import { useT } from '../i18n/react';
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
  const t = useT('workflow'), tNav = useT('nav');
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
    <div className="wf-page-head"><h1>{tNav('resource.OPERATIONS_WORKFLOW_INSTANCES')}</h1>
      <Segmented value={status} onChange={value => setStatus(value as StatusTab)}
        options={(['RUNNING', 'COMPLETED', 'TERMINATED', 'ALL'] as StatusTab[]).map(value => ({ value, label: value === 'ALL' ? t('catalog.all') : instanceStatusLabel(value) }))} />
      <span className="wf-grow" />
      {canStart && <Button type="primary" icon={<PlusOutlined />} onClick={() => setParams({ launch: '' })}>{t('launch.title')}</Button>}
    </div>
    <div className="wf-filters">
      <Select value={scope} onChange={setScope} style={{ width: 160 }} options={[{ value: 'MINE', label: t('instances.scope.MINE') }, ...(canReadAll ? [{ value: 'ALL', label: t('instances.scope.ALL') }] : [])]} />
      <Select allowClear value={definitionId} onChange={setDefinitionId} style={{ width: 220 }} placeholder={t('instances.filter.definition')}
        options={definitions.map(item => ({ value: item.definitionId, label: item.definitionName }))} />
      <div style={{ width: 200 }}><PersonSelect value={startedBy} onChange={value => setStartedBy(value)} placeholder={t('instances.filter.initiator')} /></div>
      <Input.Search allowClear placeholder={t('instances.filter.keyword')} style={{ width: 220 }} onSearch={setKeyword} />
      <Input.Search allowClear placeholder={t('instances.filter.businessKey')} style={{ width: 220 }} onSearch={setBusinessKey} />
    </div>
    <section className="table-panel">
      <Table<InstanceSummary> rowKey="instanceId" loading={paging.loading} dataSource={paging.error ? [] : paging.data.items} pagination={paging.pagination}
        columns={[
          { title: t('enum.activityType.INSTANCE'), render: (_, row) => <div><Link to={`${workflowRoutes.instances}/${row.instanceId}`}><strong>{row.title}</strong></Link>
            <div className="muted">{row.businessKey ?? ''}</div></div> },
          { title: t('instance.start.process'), width: 180, render: (_, row) => row.definitionName ? `${row.definitionName} · v${row.versionNo ?? '?'}` : '—' },
          { title: t('instances.column.progress'), width: 200, render: (_, row) => { const total = row.totalTaskCount ?? 0; const done = row.completedTaskCount ?? 0;
            return <div><Progress percent={total ? Math.round(done / total * 100) : 0} showInfo={false} size="small" status={row.status === 'TERMINATED' ? 'exception' : undefined} />
              <span className="muted">{t('instances.progress', { done, total })}</span></div>; } },
          { title: t('instances.column.current'), render: (_, row) => {
            const nodes = row.currentNodes ?? [];
            if (!nodes.length) return <Tag color={instanceStatusColors[row.status]}>{instanceStatusLabel(row.status)}</Tag>;
            return <div className="wf-handlers">{nodes.map(item => <span key={item.nodeKey} className="wf-handler">
              <span className="wf-avatar" title={item.handlers.map(person => person.displayName).join(t('list.comma'))}>{(item.handlers[0]?.displayName ?? '?').replace(/^ops\./, '').slice(0, 2).toUpperCase()}</span>
              {item.nodeName}{item.handlers.length > 0 && <span className="muted">{item.handlers.map(person => person.displayName).join(t('list.comma'))}</span>}
              <Tag color={item.status === 'APPROVING' ? 'orange' : item.status === 'WAITING' ? 'default' : 'blue'}>{nodeRunStatusLabel(item.status)}</Tag></span>)}</div>; } },
          { title: t('instances.column.started'), width: 170, render: (_, row) => <span className="muted">{row.startedByName ?? row.startedBy} · {shortTime(row.startTime)}</span> },
          { title: '', width: 40, render: (_, row) => <Link to={`${workflowRoutes.instances}/${row.instanceId}`} aria-label={t('instances.open')}><RightOutlined /></Link> },
        ]} />
    </section>
    {launching !== null && <LaunchModal initialDefinitionId={launching || null} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setParams(current => { const next = new URLSearchParams(current); next.delete('launch'); return next; })} />}
  </div>;
}
