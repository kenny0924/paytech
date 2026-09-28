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
import '../configuration/configuration.css';

const risks = [{ value: 'LOW', label: '低' }, { value: 'MEDIUM', label: '中' }, { value: 'HIGH', label: '高' }, { value: 'CRITICAL', label: '严重' }];
const riskColor = { LOW: 'green', MEDIUM: 'blue', HIGH: 'orange', CRITICAL: 'red' };
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
export function Permissions(props: Props) {
  const location = useLocation(); const query = new URLSearchParams(location.search);
  return query.get('mode') === 'create' || query.has('permission')
    ? <PermissionEditor key={`${query.get('permission')}:${query.get('mode')}`} {...props} />
    : <PermissionList key={location.search} {...props} />;
}

function PermissionList({ resources, onAuthorizationChanged }: Props) {
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
      if (!detail.managementProtection.statusChangeAllowed) { void message.info(detail.managementProtection.reason || '当前权限不可启停'); return; }
      setChanging(detail);
    } catch (error) { notify(error, '权限详情加载失败'); if (error instanceof ApiError && error.status === 403) onAuthorizationChanged(); }
    finally { setOpening(false); }
  }
  return <div className="configuration-page">
    <div className="page-header"><h1>权限目录</h1>{canManage && <Button type="primary" onClick={() => { const next = new URLSearchParams(location.search); next.set('mode', 'create'); void navigate(`?${next}`); }}>＋ 新增权限</Button>}</div>
    <Form form={form} layout="vertical" initialValues={filters} onFinish={values => page.applyFilters(Object.fromEntries(Object.entries(values).filter(([, value]) => typeof value === 'string' && value.trim()).map(([key, value]) => [key, String(value).trim()])))}>
      <div className="configuration-grid two"><Form.Item name="code" label="权限编码"><Input allowClear placeholder="精确匹配" maxLength={128} /></Form.Item>
        <Form.Item name="resourceType" label="资源类型"><Input allowClear maxLength={64} placeholder="例如 GATEWAY_CONFIGURATION" /></Form.Item></div>
      <div className="configuration-grid two"><Form.Item name="status" label="状态"><Select allowClear options={[{ value: 'ACTIVE', label: '启用' }, { value: 'INACTIVE', label: '停用' }]} placeholder="全部状态" /></Form.Item>
        <Form.Item name="riskLevel" label="风险级别"><Select allowClear options={risks} placeholder="全部级别" /></Form.Item></div>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
    </Form>
    <section className="table-panel" style={{ marginTop: 24 }}><div className="table-toolbar"><span>共 {page.data.total} 个权限</span><Button loading={page.loading} onClick={page.refresh}>刷新</Button></div>
      <Table rowKey="permissionId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} scroll={{ x: 1000 }} columns={[
          { title: '权限编码', dataIndex: 'permissionCode', width: 310, ellipsis: { showTitle: false }, render: (code: string) =>
            <Button type="link" className="permission-code-link" title={code} onClick={() => open(code, 'view')}><span>{code}</span></Button> },
          { title: '资源类型', dataIndex: 'resourceType', width: 220, ellipsis: true },
          { title: '动作', dataIndex: 'actionCode', width: 170, ellipsis: true },
          { title: '风险级别', width: 110, render: (_, row: PermissionSummary) => <Tag color={riskColor[row.riskLevel]}>{risks.find(r => r.value === row.riskLevel)?.label}</Tag> },
          { title: '状态', width: 90, render: (_, row: PermissionSummary) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{row.status === 'ACTIVE' ? '启用' : '停用'}</Tag> },
          { title: '操作', width: 100, fixed: 'right', render: (_, row: PermissionSummary) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
            { key: 'view', label: '详情', onClick: () => open(row.permissionCode, 'view') },
            ...(canManage ? [{ key: 'edit', label: '编辑', onClick: () => open(row.permissionCode, 'edit') },
              { key: 'status', label: row.status === 'ACTIVE' ? '停用' : '启用', disabled: opening, onClick: () => void change(row) }] : []),
          ] }}><Button size="small">操作 ▾</Button></Dropdown> },
        ]} />
    </section>
    {changing && <ConfigurationStatusModal kind="permission" code={changing.permissionCode} version={changing.version} enabled={changing.status === 'ACTIVE'}
      onClose={() => setChanging(null)} onSaved={() => { setChanging(null); page.refresh(); onAuthorizationChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function PermissionEditor({ resources, onAuthorizationChanged }: Props) {
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
      .catch(error => { if (!controller.signal.aborted) { setFailed(true); notify(error, '权限详情加载失败'); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
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
  if (failed || !creating && !detail) return <Alert type="warning" showIcon title="权限详情暂时无法加载" action={<Button onClick={() => setReload(v => v + 1)}>重试</Button>} />;
  return <div className="configuration-page configuration-wide-editor">
    <div className="page-header"><h1>{creating ? '新增权限' : readOnly ? '权限详情' : '编辑权限'}</h1>
      {detail && <div className="configuration-actions"><Tag color={detail.status === 'ACTIVE' ? 'green' : 'default'}>{detail.status === 'ACTIVE' ? '启用' : '停用'}</Tag>
        {canManage && readOnly && <Button onClick={() => { const next = new URLSearchParams(location.search); next.set('mode', 'edit'); void navigate(`?${next}`); }}>编辑</Button>}
        {canManage && readOnly && detail.managementProtection.statusChangeAllowed && <Button onClick={() => setChanging(true)}>{detail.status === 'ACTIVE' ? '停用' : '启用'}</Button>}</div>}</div>
    <section className="configuration-editor"><Form form={form} layout="vertical" onFinish={save} onValuesChange={() => editor.setDirty(true)} requiredMark={false}>
      <h3 className="configuration-section-heading">权限身份</h3>
      <Form.Item name="permissionCode" label="权限编码" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,127}$/, message: '请输入大写字母、数字或下划线编码' }]}>
        <Input disabled={!creating || readOnly || editor.saving} maxLength={128} placeholder="例如 REPORT_EXPORT" /></Form.Item>
      <div className="configuration-grid two"><Form.Item name="resourceType" label="资源类型" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/ }]}>
        <Input disabled={!creating || readOnly || editor.saving} maxLength={64} placeholder="例如 REPORT" /></Form.Item>
        <Form.Item name="actionCode" label="动作编码" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/ }]}>
          <Input disabled={!creating || readOnly || editor.saving} maxLength={64} placeholder="例如 EXPORT" /></Form.Item></div>
      <Form.Item name="riskLevel" label="风险级别" rules={[{ required: true }]}><Select options={risks} disabled={readOnly || editor.saving || editor.conflict} /></Form.Item>
      {detail?.managementProtection.reason && <div className="configuration-protection">{detail.managementProtection.reason}</div>}
      {editor.conflict && <Alert type="warning" showIcon title="配置已有更新，草稿已保留。请重新打开详情核对最新版本。" />}
      {!readOnly && <><Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true, message: '请填写变更原因' }]}><Input.TextArea rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} /></Form.Item>
        <footer className="configuration-save-bar"><span className="muted">{creating ? '新权限保存后为停用状态' : `当前版本 ${detail?.version}`}</span><Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict || !canManage}>保存配置</Button></footer></>}
    </Form></section>
    {detail && readOnly && <PermissionReferences detail={detail} resources={resources} />}
    {changing && detail && <ConfigurationStatusModal kind="permission" code={detail.permissionCode} version={detail.version} enabled={detail.status === 'ACTIVE'}
      onClose={() => setChanging(false)} onSaved={() => { setChanging(false); setReload(v => v + 1); onAuthorizationChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function PermissionReferences({ detail, resources }: { detail: PermissionDetail; resources: ConsoleResource[] }) {
  const navigate = useNavigate(); const notify = useErrorNotification(); const [kind, setKind] = useState('RESOURCE');
  const [page, setPage] = useState(1); const [result, setResult] = useState<PagingResponseBody<PermissionReference>>({ items: [], page: 1, size: 10, total: 0 });
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const menus = menuPages(buildMenuTree(resources));
  const has = (key: string) => menus.some(menu => menu.componentKey === key);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request<PagingResponseBody<PermissionReference>>(`/permission-admin/v1/permissions/${encodeURIComponent(detail.permissionCode)}/references?${new URLSearchParams({ kind, page: String(page), size: '10' })}`, { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setResult(value); }).catch(error => { if (!controller.signal.aborted) { setFailed(true); notify(error, '权限引用加载失败'); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [detail.permissionCode, detail.version, kind, page, reload, notify]);
  const label = (text: string, url?: string) => url ? <Button type="link" onClick={() => void navigate(url)}>{text}</Button> : <Typography.Text>{text}</Typography.Text>;
  const columns = kind === 'RESOURCE' ? [
    { title: '资源', render: (_: unknown, row: PermissionReference) => { const r = row as ResourceReference; return label(r.displayName, r.channel === 'OPERATIONS' && has('OPERATIONS_CONSOLE_RESOURCES') ? `/console-resources?resource=${encodeURIComponent(r.resourceCode)}&mode=view` : undefined); } },
    { title: '编码', dataIndex: 'resourceCode' }, { title: 'Channel', dataIndex: 'channel' }, { title: '类型', dataIndex: 'resourceType' }, { title: '状态', dataIndex: 'status' },
  ] : kind === 'ROUTE' ? [
    { title: 'Route', render: (_: unknown, row: PermissionReference) => { const r = row as RouteReference; return label(r.routeCode, has('OPERATIONS_GATEWAY_CONFIGURATION') ? `/gateway-configuration?tab=routes&code=${encodeURIComponent(r.routeCode)}&mode=view` : undefined); } },
    { title: 'Channel', dataIndex: 'channel' }, { title: 'Method', dataIndex: 'httpMethod' }, { title: '入口路径', dataIndex: 'externalPathTemplate' }, { title: '状态', dataIndex: 'status' },
  ] : [
    { title: '角色', dataIndex: 'roleName' }, { title: '角色编码', dataIndex: 'roleCode' }, { title: '范围', dataIndex: 'scopeKey' }, { title: '角色状态', dataIndex: 'roleStatus' }, { title: '授权状态', dataIndex: 'grantStatus' },
  ];
  return <section className="table-panel configuration-reference-panel"><div className="table-toolbar"><h2>使用关系</h2><Button onClick={() => setReload(v => v + 1)}>刷新</Button></div>
    <Tabs activeKey={kind} onChange={value => { setKind(value); setPage(1); }} items={[
      { key: 'RESOURCE', label: `菜单 / 按钮 (${detail.referenceCounts.resource})` }, { key: 'ROUTE', label: `API 路由 (${detail.referenceCounts.route})` }, { key: 'ROLE', label: `角色 (${detail.referenceCounts.role})` },
    ]} /><Table rowKey={row => 'resourceId' in row ? row.resourceId : 'routeCode' in row ? row.routeCode : (row as RoleReference).roleId}
      dataSource={failed ? [] : result.items} columns={columns} loading={loading} pagination={{ current: page, pageSize: 10, total: result.total, onChange: setPage }} scroll={{ x: 800 }} />
  </section>;
}
