import { useEffect, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Dropdown, Form, Input, Spin, Table, Tabs } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource, GatewayRouteSummaryResponseBody } from '../api/types';
import { session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { buildMenuTree, menuPages } from '../navigation/menu';
import { scopeCodePattern, scopeMutation, scopeReferenceQuery, validateScopePage } from '../clientApi/clientApi';
import type { Credential, ReferenceKind, Scope, ScopeDetail, ScopePage } from '../clientApi/clientApi';
import { CredentialStatus } from './ApiCredentials';
import { Audit } from './Audit';
import '../configuration/configuration.css';
import '../clientApi/clientApi.css';
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const scopePath = '/api-scope-admin/v1/scopes';
const canManage = (resources: ConsoleResource[]) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_API_SCOPE_MANAGE');
export function ApiScopes(props: Props) {
  const location = useLocation(); const query = new URLSearchParams(location.search);
  return query.has('scope') || query.get('mode') === 'create'
    ? <ScopeEditor key={`${query.get('scope')}:${query.get('mode')}`} {...props} /> : <ScopeList key={location.search} {...props} />;
}
function ScopeList({ resources }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const filters: Record<string, string> = query.get('keyword') ? { keyword: query.get('keyword')! } : {};
  const page = usePaging<Scope>(scopePath, undefined, filters, { initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`, { replace: true }); } });
  function open(code: string | null, mode: string) { const next = new URLSearchParams(location.search); next.set('mode', mode); if (code) next.set('scope', code); void navigate(`?${next}`); }
  return <div className="configuration-page client-api-page"><div className="page-heading"><h1>API Scope</h1>{canManage(resources) && <Button type="primary" onClick={() => open(null, 'create')}>＋ 新增 Scope</Button>}</div>
    <Form form={form} layout="inline" initialValues={filters} onFinish={v => page.applyFilters(v.keyword?.trim() ? { keyword: v.keyword.trim() } : {})}>
      <Form.Item name="keyword" label="编码 / 名称"><Input allowClear maxLength={255} placeholder="搜索 Scope" /></Form.Item><Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item><Form.Item><Button onClick={() => page.applyFilters({})}>重置</Button></Form.Item>
    </Form><section className="table-panel client-api-reference"><div className="table-toolbar"><span>共 {page.data.total} 个 Scope</span><Button loading={page.loading} onClick={page.refresh}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<Scope> rowKey="apiScopeId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 850 }} pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} columns={[
        { title: 'Scope 编码', dataIndex: 'scopeCode', width: 250, ellipsis: true }, { title: '名称', dataIndex: 'displayName', width: 230, ellipsis: true },
        { title: '说明', dataIndex: 'description', ellipsis: true, render: (v: string | null) => v || '—' },
        { title: '操作', width: 100, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
          { key: 'view', label: '详情', onClick: () => open(row.scopeCode, 'view') }, ...(canManage(resources) ? [{ key: 'edit', label: '编辑', onClick: () => open(row.scopeCode, 'edit') }] : []),
        ] }}><Button size="small">操作 ▾</Button></Dropdown> },
      ]} /></section></div>;
}
function ScopeEditor({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const code = query.get('scope') || ''; const creating = query.get('mode') === 'create';
  const readOnly = !canManage(resources) || !creating && query.get('mode') !== 'edit';
  const [form] = Form.useForm(); const editor = useConfigurationForm(onAuthorizationChanged, form); const notify = useErrorNotification();
  const [detail, setDetail] = useState<ScopeDetail>(); const [loading, setLoading] = useState(!creating); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  useEffect(() => {
    if (creating) return;
    const abort = new AbortController(); setLoading(true); setFailed(false);
    session.request<ScopePage>(`${scopePath}/${encodeURIComponent(code)}?referenceKind=NONE`, { signal: abort.signal }, value => validateScopePage(value as ScopePage, code))
      .then(value => { if (!abort.signal.aborted) { setDetail(value.scope); form.setFieldsValue({ ...value.scope, reason: '' }); } })
      .catch(e => { if (!abort.signal.aborted) { setFailed(true); notify(e, 'Scope 详情加载失败'); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [creating, code, reload, form, notify]);
  async function save(values: { scopeCode: string; displayName: string; description?: string; reason: string }) {
    if (readOnly) return; const savedCode = creating ? values.scopeCode : code;
    const result = await editor.save(`${scopePath}${creating ? '' : `/${encodeURIComponent(code)}`}`, creating ? 'POST' : 'PUT', {
      displayName: values.displayName.trim(), description: values.description?.trim() || null, reason: values.reason.trim(),
      ...(creating ? { scopeCode: savedCode } : { expectedVersion: detail!.version }),
    }, value => scopeMutation(value, savedCode));
    if (result) { flushSync(() => editor.setDirty(false)); const next = new URLSearchParams(location.search); next.set('scope', result.scopeCode); next.set('mode', 'view'); void navigate(`?${next}`, { replace: true }); }
  }
  if (loading) return <div className="content-loading"><Spin /></div>;
  if (failed || !creating && !detail) return <Alert type="warning" showIcon title="Scope 详情暂时无法加载" action={<Button onClick={() => setReload(v => v + 1)}>重试</Button>} />;
  if (creating && readOnly) return <Alert type="warning" showIcon title="当前没有 Scope 维护权限" />;
  return <div className="configuration-page client-api-page"><div className="page-heading"><h1>{creating ? '新增 Scope' : readOnly ? 'Scope 详情' : '编辑 Scope'}</h1>
    {detail && readOnly && canManage(resources) && <Button onClick={() => { const next = new URLSearchParams(location.search); next.set('mode', 'edit'); void navigate(`?${next}`); }}>编辑</Button>}</div>
    <section className="configuration-editor"><Form form={form} layout="vertical" requiredMark={false} onFinish={save} onValuesChange={() => { if (!readOnly) editor.setDirty(true); }}>
      <div className="configuration-grid two"><Form.Item name="scopeCode" label="Scope 编码" rules={[{ required: true, pattern: scopeCodePattern, max: 128, message: '请输入小写字母开头的 Scope 编码，如 checkout.read' }]}><Input maxLength={128} disabled={!creating || readOnly || editor.saving} placeholder="例如 checkout.read" /></Form.Item>
        <Form.Item name="displayName" label="名称" rules={[{ required: true, whitespace: true, message: '请填写名称' }]}><Input maxLength={255} disabled={readOnly || editor.saving || editor.conflict} /></Form.Item></div>
      <Form.Item name="description" label="说明"><Input.TextArea rows={3} maxLength={500} disabled={readOnly || editor.saving || editor.conflict} /></Form.Item>
      {detail && readOnly && <div className="configuration-grid two"><div className="muted">创建时间：{new Date(detail.createTime).toLocaleString('zh-CN')}</div><div className="muted">更新时间：{new Date(detail.updateTime).toLocaleString('zh-CN')}</div></div>}
      {editor.conflict && <Alert type="warning" showIcon title="Scope 已有更新，草稿已保留。请重新打开详情核对最新版本。" />}
      {!readOnly && <><Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true, message: '请填写变更原因' }]}><Input.TextArea rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} /></Form.Item>
        <footer className="configuration-save-bar"><span /> <Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict}>保存</Button></footer></>}
    </Form></section>
    {detail && readOnly && <ScopeReferences detail={detail} resources={resources} />}
  </div>;
}
function ScopeReferences({ detail, resources }: { detail: ScopeDetail; resources: ConsoleResource[] }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const menus = menuPages(buildMenuTree(resources));
  const has = (key: string) => menus.some(m => m.componentKey === key);
  const tabs = [{ key: 'NONE', label: '基本资料' }, ...(has('OPERATIONS_GATEWAY_CONFIGURATION') ? [{ key: 'ROUTE', label: '关联路由' }] : []),
    ...(has('OPERATIONS_API_CREDENTIALS') ? [{ key: 'CREDENTIAL', label: '凭证' }] : []), ...(has('OPERATIONS_AUDIT') ? [{ key: 'AUDIT', label: '操作记录' }] : [])];
  const kind = tabs.some(t => t.key === query.get('reference')) ? query.get('reference')! : 'NONE';
  return <section className="client-api-reference"><Tabs activeKey={kind} items={tabs} onChange={value => { const next = new URLSearchParams(location.search); next.set('reference', value); next.delete('referencePage'); next.delete('referenceSize'); void navigate(`?${next}`, { replace: true }); }} />
    {kind === 'AUDIT' ? <Audit key={`scope-${detail.scopeCode}`} objectFilter={{ resourceType: 'API_SCOPE', resourceId: detail.scopeCode }} embedded />
      : kind !== 'NONE' && <ScopeReferenceTable key={`${kind}:${query.get('referencePage')}:${query.get('referenceSize')}`} code={detail.scopeCode} kind={kind as ReferenceKind} />}
  </section>;
}
function ScopeReferenceTable({ code, kind }: { code: string; kind: ReferenceKind }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const page = Number(query.get('referencePage') || 1), size = Number(query.get('referenceSize') || 10);
  const notify = useErrorNotification(); const [result, setResult] = useState<ScopePage>(); const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setFailed(false);
    session.request<ScopePage>(`${scopePath}/${encodeURIComponent(code)}?${scopeReferenceQuery(kind, page, size)}`, { signal: abort.signal }, v => validateScopePage(v as ScopePage, code, kind))
      .then(v => { if (!abort.signal.aborted) setResult(v); }).catch(e => { if (!abort.signal.aborted) { setFailed(true); notify(e, '使用关系加载失败'); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [code, kind, page, size, reload, notify]);
  const references = result?.references; const pagination = { current: page, pageSize: size, total: references?.page.total || 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100],
    onChange: (p: number, s: number) => { const next = new URLSearchParams(location.search); next.set('referencePage', String(size === s ? p : 1)); next.set('referenceSize', String(s)); void navigate(`?${next}`, { replace: true }); } };
  return <section className="table-panel"><div className="table-toolbar"><span>共 {references?.page.total || 0} 条引用</span><Button onClick={() => setReload(v => v + 1)}>{failed ? '重试' : '刷新'}</Button></div>
    {kind === 'ROUTE' ? <Table<GatewayRouteSummaryResponseBody> rowKey="routeCode" tableLayout="fixed" loading={loading} dataSource={!failed && references?.kind === 'ROUTE' ? references.page.items : []} pagination={pagination} scroll={{ x: 950 }} columns={[
      { title: '路由编码', dataIndex: 'routeCode', width: 260, ellipsis: true }, { title: 'Channel', dataIndex: 'channel', width: 140 }, { title: 'Method', dataIndex: 'httpMethod', width: 90 },
      { title: '入口路径', dataIndex: 'externalPathTemplate', ellipsis: true }, { title: '状态', dataIndex: 'status', width: 100 },
      { title: '操作', width: 90, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void navigate(`/gateway-configuration?tab=routes&code=${encodeURIComponent(row.routeCode)}&mode=view`)}>详情</Button> },
    ]} /> : <Table<Credential> rowKey="credentialId" tableLayout="fixed" loading={loading} dataSource={!failed && references?.kind === 'CREDENTIAL' ? references.page.items : []} pagination={pagination} scroll={{ x: 850 }} columns={[
      { title: 'Client', dataIndex: 'clientName', ellipsis: true }, { title: 'Client ID', dataIndex: 'clientId', width: 210, ellipsis: true }, { title: '密钥前缀', dataIndex: 'keyPrefix', width: 260, ellipsis: true },
      { title: '状态', width: 110, render: (_, row) => <CredentialStatus status={row.status} /> }, { title: '操作', width: 90, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void navigate(`/api-credentials?credential=${encodeURIComponent(row.credentialId)}`)}>详情</Button> },
    ]} />}
  </section>;
}
