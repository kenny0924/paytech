import { useEffect, useState } from 'react';
import { Button, Dropdown, Form, Input, Select, Spin, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource, GatewayTargetSummaryResponseBody, GatewayRouteSummaryResponseBody } from '../api/types';
import { ApiError, session } from '../api/session';
import { GatewayStatusModal } from '../components/GatewayStatusModal';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { usePaging } from '../hooks/usePaging';
import { gatewayFilters } from './gatewayConfigurationModel';
import type { GatewayStatusEntity } from './gatewayConfigurationModel';
import type { GatewayCapabilities, ManagementProtection } from '../configuration/types';
import { GatewayEditor } from '../configuration/GatewayEditor';
import { RefreshButton } from '../components/RefreshButton';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
const statuses = ['ACTIVE', 'DISABLED'] as const;
export interface GatewayPageProps { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
export function GatewayConfiguration(props: GatewayPageProps) {
  const t = useT('gateway'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification();
  const [capabilities, setCapabilities] = useState<GatewayCapabilities | null>(null); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const query = new URLSearchParams(location.search); const tab = query.get('tab') === 'targets' ? 'targets' : 'routes';
  useEffect(() => {
    const abort = new AbortController(); setFailed(false);
    session.request<GatewayCapabilities>('/gateway-admin/v1/capabilities', { signal: abort.signal }).then(value => { if (!abort.signal.aborted) setCapabilities(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('routes.capabilitiesLoadFailed')); if (error instanceof ApiError && error.status === 403) props.onAuthorizationChanged(); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [reload, notify]);
  if (!capabilities) return failed ? <Button onClick={() => setReload(v => v + 1)}>{t('routes.reloadCapabilities')}</Button> : <Spin />;
  if (query.get('mode') === 'create' || query.has('code')) return <GatewayEditor key={`${tab}:${query.get('code')}:${query.get('mode')}`} {...props} capabilities={capabilities} kind={tab === 'targets' ? 'target' : 'route'} />;
  return <div className="configuration-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_GATEWAY_CONFIGURATION')}</h1></div>
    <Tabs activeKey={tab} onChange={value => { void navigate(`?tab=${value}`); }} items={[{ key: 'routes', label: t('routes.tab.routes') }, { key: 'targets', label: t('routes.field.targetService') }]} />
    <GatewayList key={`${tab}:${location.search}`} {...props} capabilities={capabilities} kind={tab === 'targets' ? 'target' : 'route'} />
  </div>;
}
function GatewayList({ resources, onAuthorizationChanged, capabilities, kind }: GatewayPageProps & { capabilities: GatewayCapabilities; kind: 'target' | 'route' }) {
  const t = useT('gateway');
  const location = useLocation(); const navigate = useNavigate(); const [form] = Form.useForm();
  const query = new URLSearchParams(location.search); const collection = kind === 'target' ? 'target-services' : 'routes';
  const fields = kind === 'target' ? ['targetServiceCode', 'channel', 'status'] : ['routeCode', 'targetServiceCode', 'channel', 'status', 'requiredScopeCode'];
  const filters = Object.fromEntries(fields.flatMap(key => query.get(key) ? [[key, query.get(key)!]] : []));
  const page = usePaging<GatewayTargetSummaryResponseBody | GatewayRouteSummaryResponseBody>(`/gateway-admin/v1/${collection}`, undefined, filters, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, tab: kind === 'target' ? 'targets' : 'routes', page: String(next.paging.page), size: String(next.paging.size) })}`); },
  });
  const [statusEntity, setStatusEntity] = useState<GatewayStatusEntity | null>(null);
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_GATEWAY_CONFIGURATION_MANAGE');
  function open(code: string | null, mode: string) { const next = new URLSearchParams(location.search); next.set('tab', kind === 'target' ? 'targets' : 'routes'); next.set('mode', mode); if (code) next.set('code', code); else next.delete('code'); void navigate(`?${next}`); }
  const commonColumns = [
    { title: t('routes.field.channel'), dataIndex: 'channel', width: 150 },
    { title: t('routes.column.status'), dataIndex: 'status', width: 95, render: (status: string) => <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.resourceStatus', status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED')}</Tag> },
    { title: t('routes.column.version'), dataIndex: 'version', width: 80 },
    { title: t('routes.column.actions'), fixed: 'right' as const, width: 100, render: (_: unknown, row: GatewayTargetSummaryResponseBody | GatewayRouteSummaryResponseBody) => {
      const code = 'routeCode' in row ? row.routeCode : row.targetServiceCode;
      const protection = (row as typeof row & { managementProtection: ManagementProtection }).managementProtection;
      return <Dropdown trigger={['click', 'hover']} menu={{ items: [
        { key: 'view', label: t('resources.action.details'), onClick: () => open(code, 'view') },
        ...(canManage ? [{ key: 'edit', label: t('resources.action.edit'), disabled: !protection?.editableFields.length, onClick: () => open(code, 'edit') },
          { key: 'status', label: row.status === 'ACTIVE' ? t('resources.action.disable') : t('resources.action.enable'), disabled: !protection?.statusChangeAllowed, onClick: () => setStatusEntity({ kind, code, status: row.status, version: row.version }) }] : []),
      ] }}><Button size="small">{t('routes.actionsMenu')}</Button></Dropdown>;
    } },
  ];
  return <><Form form={form} layout="vertical" initialValues={filters} onFinish={values => page.applyFilters(gatewayFilters(values))}>
    <div className={`gateway-filters ${kind}`}>{kind === 'route' && <Form.Item name="routeCode" label={t('routes.field.routeCode')}><Input allowClear maxLength={128} placeholder={t('routes.exactMatch')} /></Form.Item>}
      <Form.Item name="targetServiceCode" label={t('routes.field.targetServiceCode')}><Input allowClear maxLength={128} placeholder={t('routes.exactMatch')} /></Form.Item>
      {kind === 'route' && <Form.Item name="requiredScopeCode" label={t('routes.field.apiScope')}><Input allowClear maxLength={128} placeholder={t('routes.exactScope')} /></Form.Item>}
      <Form.Item name="channel" label={t('routes.field.channel')}><Select allowClear options={capabilities.channels.map(c => ({ value: c.channel, label: c.channel }))} placeholder={t('routes.all')} /></Form.Item>
      <Form.Item name="status" label={t('routes.column.status')}><Select allowClear options={statuses.map(value => ({ value, label: enumLabel(t, 'enum.resourceStatus', value) }))} placeholder={t('routes.all')} /></Form.Item>
      <div className="configuration-actions gateway-filter-actions"><Button type="primary" htmlType="submit">{t('routes.search')}</Button><Button onClick={() => page.applyFilters({})}>{t('routes.reset')}</Button></div></div>
  </Form><section className="table-panel"><div className="table-toolbar"><span className="table-count"><span>{t('routes.total', { total: page.data.total })}</span>
    <RefreshButton onClick={page.refresh} loading={page.loading} failed={!!page.error} /></span>
    {canManage && <Button type="primary" onClick={() => open(null, 'create')}>{kind === 'target' ? t('routes.createTarget') : t('routes.createRoute')}</Button>}</div>
    <Table rowKey={row => 'routeCode' in row ? row.routeCode : row.targetServiceCode} dataSource={page.error ? [] : page.data.items} loading={page.loading} pagination={page.pagination} scroll={{ x: kind === 'route' ? 1200 : 900 }} columns={[
      ...(kind === 'target' ? [{ title: t('routes.field.targetService'), dataIndex: 'targetServiceCode' }, { title: t('routes.field.discoveryServiceName'), dataIndex: 'discoveryServiceName' }] : [
        { title: t('routes.field.routeCode'), dataIndex: 'routeCode', width: 240 }, { title: t('routes.column.method'), dataIndex: 'httpMethod', width: 90 },
        { title: t('routes.field.entryPath'), dataIndex: 'externalPathTemplate' }, { title: t('routes.field.targetService'), dataIndex: 'targetServiceCode', width: 220 },
      ]), ...commonColumns,
    ]} /></section>
    {statusEntity && <GatewayStatusModal entity={statusEntity} allowed={canManage} onClose={() => setStatusEntity(null)} onChanged={() => { setStatusEntity(null); page.refresh(); onAuthorizationChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}
