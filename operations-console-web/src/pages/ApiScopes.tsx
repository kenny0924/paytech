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
import { formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const scopePath = '/api-scope-admin/v1/scopes';
const canManage = (resources: ConsoleResource[]) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_API_SCOPE_MANAGE');
export function ApiScopes(props: Props) {
  const location = useLocation(); const query = new URLSearchParams(location.search);
  return query.has('scope') || query.get('mode') === 'create'
    ? <ScopeEditor key={`${query.get('scope')}:${query.get('mode')}`} {...props} /> : <ScopeList key={location.search} {...props} />;
}
function ScopeList({ resources }: Props) {
  const t = useT('gateway'), tCommon = useT('common'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const filters: Record<string, string> = query.get('keyword') ? { keyword: query.get('keyword')! } : {};
  const page = usePaging<Scope>(scopePath, undefined, filters, { initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`, { replace: true }); } });
  function open(code: string | null, mode: string) { const next = new URLSearchParams(location.search); next.set('mode', mode); if (code) next.set('scope', code); void navigate(`?${next}`); }
  return <div className="configuration-page client-api-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_API_SCOPES')}</h1>{canManage(resources) && <Button type="primary" onClick={() => open(null, 'create')}>{t('scopes.create')}</Button>}</div>
    <Form form={form} layout="inline" initialValues={filters} onFinish={v => page.applyFilters(v.keyword?.trim() ? { keyword: v.keyword.trim() } : {})}>
      <Form.Item name="keyword" label={t('scopes.filter.keyword')}><Input allowClear maxLength={255} placeholder={t('scopes.filter.placeholder')} /></Form.Item><Form.Item><Button type="primary" htmlType="submit">{t('routes.search')}</Button></Form.Item><Form.Item><Button onClick={() => page.applyFilters({})}>{t('routes.reset')}</Button></Form.Item>
    </Form><section className="table-panel client-api-reference"><div className="table-toolbar"><span>{t('scopes.total', { total: page.data.total })}</span><Button loading={page.loading} onClick={page.refresh}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<Scope> rowKey="apiScopeId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 850 }} pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} columns={[
        { title: t('scopes.field.scopeCode'), dataIndex: 'scopeCode', width: 250, ellipsis: true }, { title: t('scopes.field.displayName'), dataIndex: 'displayName', width: 230, ellipsis: true },
        { title: t('scopes.field.description'), dataIndex: 'description', ellipsis: true, render: (v: string | null) => v || '—' },
        { title: t('routes.column.actions'), width: 100, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
          { key: 'view', label: t('resources.action.details'), onClick: () => open(row.scopeCode, 'view') }, ...(canManage(resources) ? [{ key: 'edit', label: t('resources.action.edit'), onClick: () => open(row.scopeCode, 'edit') }] : []),
        ] }}><Button size="small">{t('routes.actionsMenu')}</Button></Dropdown> },
      ]} /></section></div>;
}
function ScopeEditor({ resources, onAuthorizationChanged }: Props) {
  const t = useT('gateway'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const code = query.get('scope') || ''; const creating = query.get('mode') === 'create';
  const readOnly = !canManage(resources) || !creating && query.get('mode') !== 'edit';
  const [form] = Form.useForm(); const editor = useConfigurationForm(onAuthorizationChanged, form); const notify = useErrorNotification();
  const [detail, setDetail] = useState<ScopeDetail>(); const [loading, setLoading] = useState(!creating); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  useEffect(() => {
    if (creating) return;
    const abort = new AbortController(); setLoading(true); setFailed(false);
    session.request<ScopePage>(`${scopePath}/${encodeURIComponent(code)}?referenceKind=NONE`, { signal: abort.signal }, value => validateScopePage(value as ScopePage, code))
      .then(value => { if (!abort.signal.aborted) { setDetail(value.scope); form.setFieldsValue({ ...value.scope, reason: '' }); } })
      .catch(e => { if (!abort.signal.aborted) { setFailed(true); notify(e, t('scopes.detailLoadFailed')); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
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
  if (failed || !creating && !detail) return <Alert type="warning" showIcon title={t('scopes.detailUnavailable')} action={<Button onClick={() => setReload(v => v + 1)}>{tCommon('action.retry')}</Button>} />;
  if (creating && readOnly) return <Alert type="warning" showIcon title={t('scopes.noManagePermission')} />;
  return <div className="configuration-page client-api-page"><div className="page-heading"><h1>{creating ? t('scopes.editor.createTitle') : readOnly ? t('scopes.editor.viewTitle') : t('scopes.editor.editTitle')}</h1>
    {detail && readOnly && canManage(resources) && <Button onClick={() => { const next = new URLSearchParams(location.search); next.set('mode', 'edit'); void navigate(`?${next}`); }}>{t('resources.action.edit')}</Button>}</div>
    <section className="configuration-editor"><Form form={form} layout="vertical" requiredMark={false} onFinish={save} onValuesChange={() => { if (!readOnly) editor.setDirty(true); }}>
      <div className="configuration-grid two"><Form.Item name="scopeCode" label={t('scopes.field.scopeCode')} rules={[{ required: true, pattern: scopeCodePattern, max: 128, message: t('scopes.editor.codeInvalid') }]}><Input maxLength={128} disabled={!creating || readOnly || editor.saving} placeholder={t('scopes.editor.codePlaceholder')} /></Form.Item>
        <Form.Item name="displayName" label={t('scopes.field.displayName')} rules={[{ required: true, whitespace: true, message: t('scopes.editor.displayNameRequired') }]}><Input maxLength={255} disabled={readOnly || editor.saving || editor.conflict} /></Form.Item></div>
      <Form.Item name="description" label={t('scopes.field.description')}><Input.TextArea rows={3} maxLength={500} disabled={readOnly || editor.saving || editor.conflict} /></Form.Item>
      {detail && readOnly && <div className="configuration-grid two"><div className="muted">{t('scopes.editor.createdAt', { time: formatDateTime(detail.createTime) })}</div><div className="muted">{t('scopes.editor.updatedAt', { time: formatDateTime(detail.updateTime) })}</div></div>}
      {editor.conflict && <Alert type="warning" showIcon title={t('scopes.editor.conflict')} />}
      {!readOnly && <><Form.Item name="reason" label={t('config.reason')} rules={[{ required: true, whitespace: true, message: t('config.reasonRequired') }]}><Input.TextArea rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} /></Form.Item>
        <footer className="configuration-save-bar"><span /> <Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict}>{tCommon('action.save')}</Button></footer></>}
    </Form></section>
    {detail && readOnly && <ScopeReferences detail={detail} resources={resources} />}
  </div>;
}
function ScopeReferences({ detail, resources }: { detail: ScopeDetail; resources: ConsoleResource[] }) {
  const t = useT('gateway');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const menus = menuPages(buildMenuTree(resources));
  const has = (key: string) => menus.some(m => m.componentKey === key);
  const tabs = [{ key: 'NONE', label: t('scopes.tab.basics') }, ...(has('OPERATIONS_GATEWAY_CONFIGURATION') ? [{ key: 'ROUTE', label: t('scopes.tab.routes') }] : []),
    ...(has('OPERATIONS_API_CREDENTIALS') ? [{ key: 'CREDENTIAL', label: t('scopes.tab.credentials') }] : []), ...(has('OPERATIONS_AUDIT') ? [{ key: 'AUDIT', label: t('credentials.tab.audit') }] : [])];
  const kind = tabs.some(tab => tab.key === query.get('reference')) ? query.get('reference')! : 'NONE';
  return <section className="client-api-reference"><Tabs activeKey={kind} items={tabs} onChange={value => { const next = new URLSearchParams(location.search); next.set('reference', value); next.delete('referencePage'); next.delete('referenceSize'); void navigate(`?${next}`, { replace: true }); }} />
    {kind === 'AUDIT' ? <Audit key={`scope-${detail.scopeCode}`} objectFilter={{ resourceType: 'API_SCOPE', resourceId: detail.scopeCode }} embedded />
      : kind !== 'NONE' && <ScopeReferenceTable key={`${kind}:${query.get('referencePage')}:${query.get('referenceSize')}`} code={detail.scopeCode} kind={kind as ReferenceKind} />}
  </section>;
}
function ScopeReferenceTable({ code, kind }: { code: string; kind: ReferenceKind }) {
  const t = useT('gateway'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const page = Number(query.get('referencePage') || 1), size = Number(query.get('referenceSize') || 10);
  const notify = useErrorNotification(); const [result, setResult] = useState<ScopePage>(); const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setFailed(false);
    session.request<ScopePage>(`${scopePath}/${encodeURIComponent(code)}?${scopeReferenceQuery(kind, page, size)}`, { signal: abort.signal }, v => validateScopePage(v as ScopePage, code, kind))
      .then(v => { if (!abort.signal.aborted) setResult(v); }).catch(e => { if (!abort.signal.aborted) { setFailed(true); notify(e, t('scopes.references.loadFailed')); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [code, kind, page, size, reload, notify]);
  const references = result?.references; const pagination = { current: page, pageSize: size, total: references?.page.total || 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100],
    onChange: (p: number, s: number) => { const next = new URLSearchParams(location.search); next.set('referencePage', String(size === s ? p : 1)); next.set('referenceSize', String(s)); void navigate(`?${next}`, { replace: true }); } };
  return <section className="table-panel"><div className="table-toolbar"><span>{t('scopes.references.total', { total: references?.page.total || 0 })}</span><Button onClick={() => setReload(v => v + 1)}>{failed ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
    {kind === 'ROUTE' ? <Table<GatewayRouteSummaryResponseBody> rowKey="routeCode" tableLayout="fixed" loading={loading} dataSource={!failed && references?.kind === 'ROUTE' ? references.page.items : []} pagination={pagination} scroll={{ x: 950 }} columns={[
      { title: t('routes.field.routeCode'), dataIndex: 'routeCode', width: 260, ellipsis: true }, { title: t('routes.field.channel'), dataIndex: 'channel', width: 140 }, { title: t('routes.column.method'), dataIndex: 'httpMethod', width: 90 },
      { title: t('routes.field.entryPath'), dataIndex: 'externalPathTemplate', ellipsis: true }, { title: t('routes.column.status'), dataIndex: 'status', width: 100 },
      { title: t('routes.column.actions'), width: 90, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void navigate(`/gateway-configuration?tab=routes&code=${encodeURIComponent(row.routeCode)}&mode=view`)}>{t('resources.action.details')}</Button> },
    ]} /> : <Table<Credential> rowKey="credentialId" tableLayout="fixed" loading={loading} dataSource={!failed && references?.kind === 'CREDENTIAL' ? references.page.items : []} pagination={pagination} scroll={{ x: 850 }} columns={[
      { title: t('credentials.field.client'), dataIndex: 'clientName', ellipsis: true }, { title: t('credentials.field.clientId'), dataIndex: 'clientId', width: 210, ellipsis: true }, { title: t('credentials.field.keyPrefix'), dataIndex: 'keyPrefix', width: 260, ellipsis: true },
      { title: t('routes.column.status'), width: 110, render: (_, row) => <CredentialStatus status={row.status} /> }, { title: t('routes.column.actions'), width: 90, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void navigate(`/api-credentials?credential=${encodeURIComponent(row.credentialId)}`)}>{t('resources.action.details')}</Button> },
    ]} />}
  </section>;
}
