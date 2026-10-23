import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Dropdown, Form, Input, Modal, Select, Space, Spin, Table, Tabs, Tag, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { buildMenuTree, menuPages } from '../navigation/menu';
import { credentialFilters, credentialStatuses, revokeMutation, validateCredential } from '../clientApi/clientApi';
import type { Credential, CredentialDetail, CredentialGrant, CredentialSearch } from '../clientApi/clientApi';
import { Audit } from './Audit';
import { RefreshButton } from '../components/RefreshButton';
import '../configuration/configuration.css';
import '../clientApi/clientApi.css';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const credentialPath = '/api-credential-admin/v1/credentials';
const date = (value: string | null) => formatDateTime(value);
export function CredentialStatus({ status }: { status: string }) {
  const t = useT('gateway');
  return <Tag color={status === 'ACTIVE' ? 'green' : status === 'EXPIRED' ? 'orange' : 'default'}>{enumLabel(t, 'enum.credentialStatus', status)}</Tag>;
}
export function ApiCredentials(props: Props) {
  const location = useLocation(); const query = new URLSearchParams(location.search);
  return query.has('credential') ? <CredentialDetails key={query.get('credential')} {...props} /> : <CredentialList key={location.search} {...props} />;
}
function CredentialList({ resources, onAuthorizationChanged }: Props) {
  const t = useT('gateway'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const [form] = Form.useForm<CredentialSearch>();
  const query = new URLSearchParams(location.search);
  const filters = Object.fromEntries(['clientId', 'clientName', 'credentialId', 'keyPrefix', 'status', 'scopeCode'].flatMap(k => query.get(k) ? [[k, query.get(k)!]] : []));
  const page = usePaging<Credential>(credentialPath, undefined, filters, { initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`, { replace: true }); } });
  const canRevoke = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_API_CREDENTIAL_REVOKE');
  const [revoking, setRevoking] = useState<Credential | null>(null);
  function open(id: string) { const next = new URLSearchParams(location.search); next.set('credential', id); void navigate(`?${next}`); }
  return <div className="configuration-page client-api-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_API_CREDENTIALS')}</h1></div>
    <Form form={form} layout="vertical" initialValues={{ clientMode: filters.clientId ? 'clientId' : 'clientName', client: filters.clientId || filters.clientName,
      credentialMode: filters.credentialId ? 'credentialId' : 'keyPrefix', credential: filters.credentialId || filters.keyPrefix, status: filters.status }}
      onFinish={values => page.applyFilters(credentialFilters({ ...values, scopeCode: filters.scopeCode }))}>
      <div className="client-api-search"><Form.Item label={t('credentials.field.client')}><Space.Compact block>
        <Form.Item name="clientMode" noStyle><Select className="client-api-search-mode" options={[{ value: 'clientName', label: t('credentials.field.clientName') }, { value: 'clientId', label: t('credentials.field.clientId') }]} onChange={() => form.setFieldValue('client', '')} /></Form.Item>
        <Form.Item name="client" noStyle><Input aria-label={t('credentials.field.clientQuery')} allowClear maxLength={255} /></Form.Item></Space.Compact></Form.Item>
        <Form.Item label={t('credentials.field.credential')}><Space.Compact block><Form.Item name="credentialMode" noStyle><Select className="client-api-search-mode" options={[{ value: 'keyPrefix', label: t('credentials.field.keyPrefix') }, { value: 'credentialId', label: t('credentials.field.credentialId') }]} onChange={() => form.setFieldValue('credential', '')} /></Form.Item>
          <Form.Item name="credential" noStyle><Input aria-label={t('credentials.field.credentialQuery')} allowClear maxLength={32} /></Form.Item></Space.Compact></Form.Item>
        <Form.Item name="status" label={t('routes.column.status')}><Select allowClear placeholder={t('resources.allStatuses')} options={credentialStatuses.map(value => ({ value, label: enumLabel(t, 'enum.credentialStatus', value) }))} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('routes.search')}</Button><Button onClick={() => page.applyFilters({})}>{t('routes.reset')}</Button></div></div>
    </Form>
    {filters.scopeCode && <Tag closable onClose={() => { const { scopeCode: _, ...rest } = filters; page.applyFilters(rest); }}>{t('credentials.scopeFilter', { scopeCode: filters.scopeCode })}</Tag>}
    <section className="table-panel"><div className="table-toolbar"><span className="table-count"><span>{t('credentials.total', { total: page.data.total })}</span><RefreshButton onClick={page.refresh} loading={page.loading} failed={!!page.error} /></span></div>
      <Table<Credential> rowKey="credentialId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 980 }} pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} columns={[
        { title: t('credentials.field.client'), width: 260, ellipsis: true, render: (_, row) => <div title={row.clientName}><div className="client-api-ellipsis">{row.clientName}</div><div className="cell-meta">{row.clientId}</div></div> },
        { title: t('credentials.field.keyPrefix'), dataIndex: 'keyPrefix', width: 260, ellipsis: true },
        { title: t('routes.column.status'), width: 110, render: (_, row) => <CredentialStatus status={row.status} /> },
        { title: t('credentials.field.expiresAt'), dataIndex: 'expiresTime', width: 220, render: date },
        { title: t('routes.column.actions'), width: 110, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
          { key: 'detail', label: t('resources.action.details'), onClick: () => open(row.credentialId) },
          ...(canRevoke && row.status !== 'REVOKED' ? [{ key: 'revoke', label: t('credentials.action.revoke'), danger: true, onClick: () => setRevoking(row) }] : []),
        ] }}><Button size="small">{t('routes.actionsMenu')}</Button></Dropdown> },
      ]} />
    </section>
    {revoking && <RevokeCredential credential={revoking} onClose={() => setRevoking(null)} onSaved={() => { setRevoking(null); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
function CredentialDetails({ resources, onAuthorizationChanged }: Props) {
  const t = useT('gateway'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const id = query.get('credential')!;
  const notify = useErrorNotification(); const [detail, setDetail] = useState<CredentialDetail>(); const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0); const [revoking, setRevoking] = useState(false);
  const menus = menuPages(buildMenuTree(resources)); const has = (key: string) => menus.some(m => m.componentKey === key);
  const canRevoke = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_API_CREDENTIAL_REVOKE');
  const tab = query.get('tab') === 'audit' && has('OPERATIONS_AUDIT') ? 'audit' : 'scopes';
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setFailed(false);
    session.request<CredentialDetail>(`${credentialPath}/${encodeURIComponent(id)}`, { signal: abort.signal }, v => validateCredential(v as CredentialDetail, id))
      .then(v => { if (!abort.signal.aborted) setDetail(v); }).catch(e => { if (!abort.signal.aborted) { setFailed(true); notify(e, t('credentials.detailLoadFailed')); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [id, reload, notify]);
  if (loading) return <div className="content-loading"><Spin /></div>;
  if (failed || !detail) return <Alert type="warning" showIcon title={t('credentials.detailUnavailable')} action={<Button onClick={() => setReload(v => v + 1)}>{tCommon('action.retry')}</Button>} />;
  return <div className="configuration-page client-api-page"><div className="page-heading"><h1>{t('credentials.detailTitle')}</h1><Space><CredentialStatus status={detail.status} />
    {canRevoke && detail.status !== 'REVOKED' && <Button danger onClick={() => setRevoking(true)}>{t('credentials.action.revoke')}</Button>}</Space></div>
    <section className="configuration-editor"><Descriptions column={{ xs: 1, sm: 2 }} layout="vertical" items={[
      { key: 'client', label: t('credentials.field.client'), children: <>{detail.clientName}<div className="cell-meta">{detail.clientId}</div></> },
      { key: 'prefix', label: t('credentials.field.keyPrefix'), children: detail.keyPrefix }, { key: 'id', label: t('credentials.field.credentialId'), children: detail.credentialId },
      { key: 'tenant', label: t('credentials.field.tenantId'), children: detail.tenantId }, { key: 'owner', label: t('credentials.field.tenantOwnerStatus'), children: detail.tenantOwnerStatus },
      { key: 'security', label: t('credentials.field.tenantSecurityStatus'), children: detail.tenantSecurityStatus }, { key: 'expiry', label: t('credentials.field.expiresAt'), children: date(detail.expiresTime) },
      { key: 'rotated', label: t('credentials.field.rotatedFrom'), children: detail.rotatedFromId ? <Button type="link" onClick={() => { const next = new URLSearchParams(location.search); next.set('credential', detail.rotatedFromId!); next.delete('tab'); void navigate(`?${next}`); }}>{detail.rotatedFromId}</Button> : '—' },
      { key: 'create', label: t('credentials.field.createdAt'), children: date(detail.createTime) }, { key: 'update', label: t('credentials.field.updatedAt'), children: date(detail.updateTime) },
    ]} /></section>
    <Tabs activeKey={tab} onChange={value => { const next = new URLSearchParams(location.search); next.set('tab', value); void navigate(`?${next}`, { replace: true }); }} items={[
      { key: 'scopes', label: t('credentials.tab.scopes') }, ...(has('OPERATIONS_AUDIT') ? [{ key: 'audit', label: t('credentials.tab.audit') }] : []),
    ]} />
    {tab === 'audit' ? <Audit key={`credential-${id}`} objectFilter={{ resourceType: 'API_CREDENTIAL', resourceId: id }} embedded />
      : <CredentialGrants key={`${id}:${reload}`} credential={detail} canRoutes={has('OPERATIONS_GATEWAY_CONFIGURATION')} />}
    {revoking && <RevokeCredential credential={detail} onClose={() => setRevoking(false)} onSaved={() => { setRevoking(false); setReload(v => v + 1); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
function CredentialGrants({ credential, canRoutes }: { credential: Credential; canRoutes: boolean }) {
  const t = useT('gateway'), tCommon = useT('common');
  const navigate = useNavigate(); const page = usePaging<CredentialGrant>(`${credentialPath}/${encodeURIComponent(credential.credentialId)}/scopes`);
  return <section className="table-panel">{credential.status !== 'ACTIVE' && <p className="muted">{t('credentials.grants.inactiveNote', { status: enumLabel(t, 'enum.credentialStatus', credential.status) })}</p>}
    <div className="table-toolbar"><span>{t('credentials.grants.total', { total: page.data.total })}</span><Button onClick={page.refresh}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
    <Table<CredentialGrant> rowKey="scopeId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1100 }} pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} columns={[
      { title: t('credentials.grants.scope'), width: 230, ellipsis: true, render: (_, row) => <div><div className="client-api-ellipsis" title={row.scopeCode}>{row.scopeCode}</div><div className="cell-meta client-api-ellipsis">{row.displayName || t('scopes.unregistered')}</div></div> },
      { title: t('credentials.grants.resourcePattern'), dataIndex: 'resourcePattern', width: 140, ellipsis: true }, { title: t('credentials.grants.action'), dataIndex: 'actionCode', width: 110, ellipsis: true },
      { title: t('credentials.grants.status'), width: 100, render: (_, row) => <CredentialStatus status={row.status} /> },
      { title: t('credentials.grants.effectiveFrom'), dataIndex: 'effectiveFrom', width: 200, render: date }, { title: t('credentials.grants.effectiveTo'), dataIndex: 'effectiveTo', width: 200, render: date },
      ...(canRoutes ? [{ title: t('routes.column.actions'), width: 110, fixed: 'right' as const, render: (_: unknown, row: CredentialGrant) => <Button type="link" onClick={() => void navigate(`/gateway-configuration?tab=routes&requiredScopeCode=${encodeURIComponent(row.scopeCode)}`)}>{t('credentials.grants.relatedApis')}</Button> }] : []),
    ]} /></section>;
}
function RevokeCredential({ credential, onClose, onSaved, onAuthorizationChanged }: { credential: Credential; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const t = useT('gateway'), tCommon = useT('common');
  const [form] = Form.useForm(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  async function save(values: { reason: string }) {
    const result = await editor.save(`${credentialPath}/${encodeURIComponent(credential.credentialId)}/revoke`, 'POST', { expectedVersion: credential.version, reason: values.reason.trim() },
      value => revokeMutation(value, credential.credentialId, credential.version));
    if (result) onSaved();
  }
  return <Modal title={t('credentials.revoke.title')} open onCancel={() => { if (!editor.saving) onClose(); }} footer={null} closable={!editor.saving} maskClosable={!editor.saving}>
    <Descriptions column={1} items={[{ key: 'client', label: t('credentials.field.client'), children: credential.clientName }, { key: 'prefix', label: t('credentials.field.keyPrefix'), children: credential.keyPrefix }, { key: 'id', label: t('credentials.field.credentialId'), children: credential.credentialId }]} />
    <Typography.Paragraph>{t('credentials.revoke.hint')}</Typography.Paragraph>
    <Form form={form} layout="vertical" onFinish={save}><Form.Item name="reason" label={t('credentials.revoke.reason')} rules={[{ required: true, whitespace: true, message: t('credentials.revoke.reasonRequired') }]}>
      <Input.TextArea rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} /></Form.Item>
      {editor.conflict && <Alert type="warning" showIcon title={t('credentials.revoke.conflict')} />}
      <div className="configuration-actions client-api-modal-actions"><Button disabled={editor.saving} onClick={onClose}>{tCommon('action.cancel')}</Button><Button danger type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict}>{t('credentials.revoke.confirm')}</Button></div>
    </Form>
  </Modal>;
}
