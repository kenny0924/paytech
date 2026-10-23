import { useEffect, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, App, Button, Dropdown, Form, Input, Select, Spin, Table, Tabs, Tag, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { ApiError, session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ConfigurationStatusModal } from '../configuration/ConfigurationStatusModal';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import type { PermissionDetail, PermissionSummary, PermissionReference, PagingResponseBody, ResourceReference, RouteReference, RoleReference } from '../configuration/types';
import { buildMenuTree, menuPages } from '../navigation/menu';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';

/** Risk levels in display order; labels are `access.enum.riskLevel.<value>`. */
const riskLevels = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
const riskOptions = (t: Translate) => riskLevels.map(value => ({ value, label: enumLabel(t, 'enum.riskLevel', value) }));
const riskColor = { LOW: 'green', MEDIUM: 'blue', HIGH: 'orange', CRITICAL: 'red' };
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
export function Permissions(props: Props) {
  const location = useLocation(); const query = new URLSearchParams(location.search);
  return query.get('mode') === 'create' || query.has('permission')
    ? <PermissionEditor key={`${query.get('permission')}:${query.get('mode')}`} {...props} />
    : <PermissionList key={location.search} {...props} />;
}

function PermissionList({ resources, onAuthorizationChanged }: Props) {
  const t = useT('access'), tCommon = useT('common'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification(); const { message } = App.useApp();
  const query = new URLSearchParams(location.search); const filters = Object.fromEntries(['code', 'resourceType', 'status', 'riskLevel'].flatMap(name => query.get(name) ? [[name, query.get(name)!]] : []));
  const [form] = Form.useForm(); const [changing, setChanging] = useState<PermissionDetail | null>(null); const [opening, setOpening] = useState(false);
  const canManage = resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === 'OPERATIONS_PERMISSION_MANAGE');
  const page = usePaging<PermissionSummary>('/permission-admin/v1/permissions', undefined, filters, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(Math.min(next.paging.size, 100)) })}`); },
  });
  function open(code: string, mode: string) { const next = new URLSearchParams(location.search); next.set('permission', code); next.set('mode', mode); void navigate(`?${next}`); }
  async function change(row: PermissionSummary) {
    if (opening) return; setOpening(true);
    try {
      const detail = await session.request<PermissionDetail>(`/permission-admin/v1/permissions/${encodeURIComponent(row.permissionCode)}`);
      if (!detail.managementProtection.statusChangeAllowed) { void message.info(detail.managementProtection.reason || t('permissions.statusChangeNotAllowed')); return; }
      setChanging(detail);
    } catch (error) { notify(error, t('permissions.detailLoadFailed')); if (error instanceof ApiError && error.status === 403) onAuthorizationChanged(); }
    finally { setOpening(false); }
  }
  return <div className="configuration-page">
    <div className="page-header"><h1>{tNav('resource.OPERATIONS_PERMISSIONS')}</h1>{canManage && <Button type="primary" onClick={() => { const next = new URLSearchParams(location.search); next.set('mode', 'create'); void navigate(`?${next}`); }}>{t('permissions.create')}</Button>}</div>
    <Form form={form} layout="vertical" initialValues={filters} onFinish={values => page.applyFilters(Object.fromEntries(Object.entries(values).filter(([, value]) => typeof value === 'string' && value.trim()).map(([key, value]) => [key, String(value).trim()])))}>
      <div className="configuration-grid two"><Form.Item name="code" label={t('permissions.field.code')}><Input allowClear placeholder={t('audit.filter.exactMatch')} maxLength={128} /></Form.Item>
        <Form.Item name="resourceType" label={t('permissions.field.resourceType')}><Input allowClear maxLength={64} placeholder={t('permissions.placeholder.resourceTypeFilter')} /></Form.Item></div>
      <div className="configuration-grid two"><Form.Item name="status" label={t('permissions.field.status')}><Select allowClear options={[{ value: 'ACTIVE', label: enumLabel(t, 'enum.permissionStatus', 'ACTIVE') }, { value: 'INACTIVE', label: enumLabel(t, 'enum.permissionStatus', 'INACTIVE') }]} placeholder={t('tenants.filter.allStatuses')} /></Form.Item>
        <Form.Item name="riskLevel" label={t('permissions.field.riskLevel')}><Select allowClear options={riskOptions(t)} placeholder={t('permissions.allRiskLevels')} /></Form.Item></div>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('tenants.filter.search')}</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('tenants.filter.reset')}</Button></div>
    </Form>
    <section className="table-panel" style={{ marginTop: 24 }}><div className="table-toolbar"><span>{t('permissions.total', { total: page.data.total })}</span><Button loading={page.loading} onClick={page.refresh}>{tCommon('action.refresh')}</Button></div>
      <Table rowKey="permissionId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} scroll={{ x: 1000 }} columns={[
          { title: t('permissions.field.code'), dataIndex: 'permissionCode', width: 310, ellipsis: { showTitle: false }, render: (code: string) =>
            <Button type="link" className="permission-code-link" title={code} onClick={() => open(code, 'view')}><span>{code}</span></Button> },
          { title: t('permissions.field.resourceType'), dataIndex: 'resourceType', width: 220, ellipsis: true },
          { title: t('permissions.column.action'), dataIndex: 'actionCode', width: 170, ellipsis: true },
          { title: t('permissions.field.riskLevel'), width: 110, render: (_, row: PermissionSummary) => <Tag color={riskColor[row.riskLevel]}>{enumLabel(t, 'enum.riskLevel', row.riskLevel)}</Tag> },
          { title: t('permissions.field.status'), width: 90, render: (_, row: PermissionSummary) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.permissionStatus', row.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE')}</Tag> },
          { title: t('tenants.column.actions'), width: 100, fixed: 'right', render: (_, row: PermissionSummary) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
            { key: 'view', label: t('tenants.action.details'), onClick: () => open(row.permissionCode, 'view') },
            ...(canManage ? [{ key: 'edit', label: t('permissions.action.edit'), onClick: () => open(row.permissionCode, 'edit') },
              { key: 'status', label: row.status === 'ACTIVE' ? t('permissions.action.disable') : t('permissions.action.enable'), disabled: opening, onClick: () => void change(row) }] : []),
          ] }}><Button size="small">{t('tenants.action.menu')}</Button></Dropdown> },
        ]} />
    </section>
    {changing && <ConfigurationStatusModal kind="permission" code={changing.permissionCode} version={changing.version} enabled={changing.status === 'ACTIVE'}
      onClose={() => setChanging(null)} onSaved={() => { setChanging(null); page.refresh(); onAuthorizationChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function PermissionEditor({ resources, onAuthorizationChanged }: Props) {
  const t = useT('access'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification(); const [form] = Form.useForm();
  const query = new URLSearchParams(location.search); const code = query.get('permission') || ''; const creating = query.get('mode') === 'create';
  const canManage = resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === 'OPERATIONS_PERMISSION_MANAGE');
  const readOnly = !canManage || !creating && query.get('mode') !== 'edit';
  const [detail, setDetail] = useState<PermissionDetail>(); const [loading, setLoading] = useState(!creating); const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0); const [changing, setChanging] = useState(false);
  const editor = useConfigurationForm(onAuthorizationChanged, form);
  useEffect(() => {
    if (creating) { form.setFieldsValue({ riskLevel: 'MEDIUM' }); return; }
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request<PermissionDetail>(`/permission-admin/v1/permissions/${encodeURIComponent(code)}`, { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) { setDetail(value); form.setFieldsValue({ ...value, reason: '' }); } })
      .catch(error => { if (!controller.signal.aborted) { setFailed(true); notify(error, t('permissions.detailLoadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [creating, code, reload, notify, form]);
  async function save(values: Record<string, unknown>) {
    if (readOnly) return;
    const payload = { riskLevel: values.riskLevel, reason: String(values.reason).trim(), ...(creating ? {
      permissionCode: values.permissionCode, resourceType: values.resourceType, actionCode: values.actionCode,
    } : { expectedVersion: detail!.version }) };
    const result = await editor.save(`/permission-admin/v1/permissions${creating ? '' : `/${encodeURIComponent(code)}`}`, creating ? 'POST' : 'PUT', payload);
    if (result) { flushSync(() => editor.setDirty(false)); onAuthorizationChanged(); const next = new URLSearchParams(location.search);
      next.set('permission', result.objectCode); next.set('mode', 'view'); void navigate(`?${next}`, { replace: true }); }
  }
  if (loading) return <div className="content-loading"><Spin /></div>;
  if (failed || !creating && !detail) return <Alert type="warning" showIcon title={t('permissions.detailUnavailable')} action={<Button onClick={() => setReload(v => v + 1)}>{tCommon('action.retry')}</Button>} />;
  return <div className="configuration-page configuration-wide-editor">
    <div className="page-header"><h1>{creating ? t('permissions.editor.createTitle') : readOnly ? t('permissions.editor.viewTitle') : t('permissions.editor.editTitle')}</h1>
      {detail && <div className="configuration-actions"><Tag color={detail.status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.permissionStatus', detail.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE')}</Tag>
        {canManage && readOnly && <Button onClick={() => { const next = new URLSearchParams(location.search); next.set('mode', 'edit'); void navigate(`?${next}`); }}>{t('permissions.action.edit')}</Button>}
        {canManage && readOnly && detail.managementProtection.statusChangeAllowed && <Button onClick={() => setChanging(true)}>{detail.status === 'ACTIVE' ? t('permissions.action.disable') : t('permissions.action.enable')}</Button>}</div>}</div>
    <section className="configuration-editor"><Form form={form} layout="vertical" onFinish={save} onValuesChange={() => editor.setDirty(true)} requiredMark={false}>
      <h3 className="configuration-section-heading">{t('permissions.editor.identity')}</h3>
      <Form.Item name="permissionCode" label={t('permissions.field.code')} rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,127}$/, message: t('permissions.editor.codeInvalid') }]}>
        <Input disabled={!creating || readOnly || editor.saving} maxLength={128} placeholder={t('permissions.placeholder.code')} /></Form.Item>
      <div className="configuration-grid two"><Form.Item name="resourceType" label={t('permissions.field.resourceType')} rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/ }]}>
        <Input disabled={!creating || readOnly || editor.saving} maxLength={64} placeholder={t('permissions.placeholder.resourceType')} /></Form.Item>
        <Form.Item name="actionCode" label={t('permissions.field.actionCode')} rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/ }]}>
          <Input disabled={!creating || readOnly || editor.saving} maxLength={64} placeholder={t('permissions.placeholder.actionCode')} /></Form.Item></div>
      <Form.Item name="riskLevel" label={t('permissions.field.riskLevel')} rules={[{ required: true }]}><Select options={riskOptions(t)} disabled={readOnly || editor.saving || editor.conflict} /></Form.Item>
      {detail?.managementProtection.reason && <div className="configuration-protection">{detail.managementProtection.reason}</div>}
      {editor.conflict && <Alert type="warning" showIcon title={t('permissions.editor.conflict')} />}
      {!readOnly && <><Form.Item name="reason" label={t('permissions.editor.reason')} rules={[{ required: true, whitespace: true, message: t('permissions.editor.reasonRequired') }]}><Input.TextArea rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} /></Form.Item>
        <footer className="configuration-save-bar"><span className="muted">{creating ? t('permissions.editor.createdDisabled') : t('permissions.editor.currentVersion', { version: detail?.version ?? '' })}</span><Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict || !canManage}>{t('permissions.editor.save')}</Button></footer></>}
    </Form></section>
    {detail && readOnly && <PermissionReferences detail={detail} resources={resources} />}
    {changing && detail && <ConfigurationStatusModal kind="permission" code={detail.permissionCode} version={detail.version} enabled={detail.status === 'ACTIVE'}
      onClose={() => setChanging(false)} onSaved={() => { setChanging(false); setReload(v => v + 1); onAuthorizationChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function PermissionReferences({ detail, resources }: { detail: PermissionDetail; resources: ConsoleResource[] }) {
  const t = useT('access'), tCommon = useT('common');
  const navigate = useNavigate(); const notify = useErrorNotification(); const [kind, setKind] = useState('RESOURCE');
  const [page, setPage] = useState(1); const [result, setResult] = useState<PagingResponseBody<PermissionReference>>({ items: [], page: 1, size: 10, total: 0 });
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const menus = menuPages(buildMenuTree(resources));
  const has = (key: string) => menus.some(menu => menu.componentKey === key);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request<PagingResponseBody<PermissionReference>>(`/permission-admin/v1/permissions/${encodeURIComponent(detail.permissionCode)}/references?${new URLSearchParams({ kind, page: String(page), size: '10' })}`, { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setResult(value); }).catch(error => { if (!controller.signal.aborted) { setFailed(true); notify(error, t('permissions.references.loadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [detail.permissionCode, detail.version, kind, page, reload, notify]);
  const label = (text: string, url?: string) => url ? <Button type="link" onClick={() => void navigate(url)}>{text}</Button> : <Typography.Text>{text}</Typography.Text>;
  const columns = kind === 'RESOURCE' ? [
    { title: t('permissions.references.resource'), render: (_: unknown, row: PermissionReference) => { const r = row as ResourceReference; return label(r.displayName, r.channel === 'OPERATIONS' && has('OPERATIONS_CONSOLE_RESOURCES') ? `/console-resources?resource=${encodeURIComponent(r.resourceCode)}&mode=view` : undefined); } },
    { title: t('permissions.references.code'), dataIndex: 'resourceCode' }, { title: t('permissions.references.channel'), dataIndex: 'channel' }, { title: t('permissions.references.type'), dataIndex: 'resourceType' }, { title: t('permissions.field.status'), dataIndex: 'status' },
  ] : kind === 'ROUTE' ? [
    { title: t('permissions.references.route'), render: (_: unknown, row: PermissionReference) => { const r = row as RouteReference; return label(r.routeCode, has('OPERATIONS_GATEWAY_CONFIGURATION') ? `/gateway-configuration?tab=routes&code=${encodeURIComponent(r.routeCode)}&mode=view` : undefined); } },
    { title: t('permissions.references.channel'), dataIndex: 'channel' }, { title: t('permissions.references.method'), dataIndex: 'httpMethod' }, { title: t('permissions.references.entryPath'), dataIndex: 'externalPathTemplate' }, { title: t('permissions.field.status'), dataIndex: 'status' },
  ] : [
    { title: t('memberAccess.column.role'), dataIndex: 'roleName' }, { title: t('roles.column.roleCode'), dataIndex: 'roleCode' }, { title: t('memberAccess.column.scope'), dataIndex: 'scopeKey' }, { title: t('memberAccess.column.roleStatus'), dataIndex: 'roleStatus' }, { title: t('permissions.references.grantStatus'), dataIndex: 'grantStatus' },
  ];
  return <section className="table-panel configuration-reference-panel"><div className="table-toolbar"><h2>{t('permissions.references.title')}</h2><Button onClick={() => setReload(v => v + 1)}>{tCommon('action.refresh')}</Button></div>
    <Tabs activeKey={kind} onChange={value => { setKind(value); setPage(1); }} items={[
      { key: 'RESOURCE', label: t('permissions.references.tab.resource', { count: detail.referenceCounts.resource }) }, { key: 'ROUTE', label: t('permissions.references.tab.route', { count: detail.referenceCounts.route }) }, { key: 'ROLE', label: t('permissions.references.tab.role', { count: detail.referenceCounts.role }) },
    ]} /><Table rowKey={row => 'resourceId' in row ? row.resourceId : 'routeCode' in row ? row.routeCode : (row as RoleReference).roleId}
      dataSource={failed ? [] : result.items} columns={columns} loading={loading} pagination={{ current: page, pageSize: 10, total: result.total, onChange: setPage }} scroll={{ x: 800 }} />
  </section>;
}
