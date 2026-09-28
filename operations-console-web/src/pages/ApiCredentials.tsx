import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Dropdown, Form, Input, Modal, Select, Space, Spin, Table, Tabs, Tag, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { buildMenuTree, menuPages } from '../navigation/menu';
import { credentialFilters, revokeMutation, statusLabels, validateCredential } from '../clientApi/clientApi';
import type { Credential, CredentialDetail, CredentialGrant, CredentialSearch } from '../clientApi/clientApi';
import { Audit } from './Audit';
import '../configuration/configuration.css';
import '../clientApi/clientApi.css';
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const credentialPath = '/api-credential-admin/v1/credentials';
const date = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
export function CredentialStatus({ status }: { status: string }) { return <Tag color={status === 'ACTIVE' ? 'green' : status === 'EXPIRED' ? 'orange' : 'default'}>{statusLabels[status] || status}</Tag>; }
export function ApiCredentials(props: Props) {
  const location = useLocation(); const query = new URLSearchParams(location.search);
  return query.has('credential') ? <CredentialDetails key={query.get('credential')} {...props} /> : <CredentialList key={location.search} {...props} />;
}
function CredentialList({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const [form] = Form.useForm<CredentialSearch>();
  const query = new URLSearchParams(location.search);
  const filters = Object.fromEntries(['clientId', 'clientName', 'credentialId', 'keyPrefix', 'status', 'scopeCode'].flatMap(k => query.get(k) ? [[k, query.get(k)!]] : []));
  const page = usePaging<Credential>(credentialPath, undefined, filters, { initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`, { replace: true }); } });
  const canRevoke = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_API_CREDENTIAL_REVOKE');
  const [revoking, setRevoking] = useState<Credential | null>(null);
  function open(id: string) { const next = new URLSearchParams(location.search); next.set('credential', id); void navigate(`?${next}`); }
  return <div className="configuration-page client-api-page"><div className="page-heading"><h1>API 凭证</h1></div>
    <Form form={form} layout="vertical" initialValues={{ clientMode: filters.clientId ? 'clientId' : 'clientName', client: filters.clientId || filters.clientName,
      credentialMode: filters.credentialId ? 'credentialId' : 'keyPrefix', credential: filters.credentialId || filters.keyPrefix, status: filters.status }}
      onFinish={values => page.applyFilters(credentialFilters({ ...values, scopeCode: filters.scopeCode }))}>
      <div className="client-api-search"><Form.Item label="Client"><Space.Compact block>
        <Form.Item name="clientMode" noStyle><Select className="client-api-search-mode" options={[{ value: 'clientName', label: 'Client 名称' }, { value: 'clientId', label: 'Client ID' }]} onChange={() => form.setFieldValue('client', '')} /></Form.Item>
        <Form.Item name="client" noStyle><Input aria-label="Client 查询值" allowClear maxLength={255} /></Form.Item></Space.Compact></Form.Item>
        <Form.Item label="凭证"><Space.Compact block><Form.Item name="credentialMode" noStyle><Select className="client-api-search-mode" options={[{ value: 'keyPrefix', label: '密钥前缀' }, { value: 'credentialId', label: '凭证 ID' }]} onChange={() => form.setFieldValue('credential', '')} /></Form.Item>
          <Form.Item name="credential" noStyle><Input aria-label="凭证查询值" allowClear maxLength={32} /></Form.Item></Space.Compact></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部状态" options={['ACTIVE', 'EXPIRED', 'REVOKED'].map(value => ({ value, label: statusLabels[value] }))} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => page.applyFilters({})}>重置</Button></div></div>
    </Form>
    {filters.scopeCode && <Tag closable onClose={() => { const { scopeCode: _, ...rest } = filters; page.applyFilters(rest); }}>Scope：{filters.scopeCode}</Tag>}
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 条凭证</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<Credential> rowKey="credentialId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 980 }} pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} columns={[
        { title: 'Client', width: 260, ellipsis: true, render: (_, row) => <div title={row.clientName}><div className="client-api-ellipsis">{row.clientName}</div><div className="cell-meta">{row.clientId}</div></div> },
        { title: '密钥前缀', dataIndex: 'keyPrefix', width: 260, ellipsis: true },
        { title: '状态', width: 110, render: (_, row) => <CredentialStatus status={row.status} /> },
        { title: '到期时间', dataIndex: 'expiresTime', width: 220, render: date },
        { title: '操作', width: 110, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
          { key: 'detail', label: '详情', onClick: () => open(row.credentialId) },
          ...(canRevoke && row.status !== 'REVOKED' ? [{ key: 'revoke', label: '紧急撤销', danger: true, onClick: () => setRevoking(row) }] : []),
        ] }}><Button size="small">操作 ▾</Button></Dropdown> },
      ]} />
    </section>
    {revoking && <RevokeCredential credential={revoking} onClose={() => setRevoking(null)} onSaved={() => { setRevoking(null); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
function CredentialDetails({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const id = query.get('credential')!;
  const notify = useErrorNotification(); const [detail, setDetail] = useState<CredentialDetail>(); const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0); const [revoking, setRevoking] = useState(false);
  const menus = menuPages(buildMenuTree(resources)); const has = (key: string) => menus.some(m => m.componentKey === key);
  const canRevoke = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_API_CREDENTIAL_REVOKE');
  const tab = query.get('tab') === 'audit' && has('OPERATIONS_AUDIT') ? 'audit' : 'scopes';
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setFailed(false);
    session.request<CredentialDetail>(`${credentialPath}/${encodeURIComponent(id)}`, { signal: abort.signal }, v => validateCredential(v as CredentialDetail, id))
      .then(v => { if (!abort.signal.aborted) setDetail(v); }).catch(e => { if (!abort.signal.aborted) { setFailed(true); notify(e, '凭证详情加载失败'); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [id, reload, notify]);
  if (loading) return <div className="content-loading"><Spin /></div>;
  if (failed || !detail) return <Alert type="warning" showIcon title="凭证详情暂时无法加载" action={<Button onClick={() => setReload(v => v + 1)}>重试</Button>} />;
  return <div className="configuration-page client-api-page"><div className="page-heading"><h1>凭证详情</h1><Space><CredentialStatus status={detail.status} />
    {canRevoke && detail.status !== 'REVOKED' && <Button danger onClick={() => setRevoking(true)}>紧急撤销</Button>}</Space></div>
    <section className="configuration-editor"><Descriptions column={{ xs: 1, sm: 2 }} layout="vertical" items={[
      { key: 'client', label: 'Client', children: <>{detail.clientName}<div className="cell-meta">{detail.clientId}</div></> },
      { key: 'prefix', label: '密钥前缀', children: detail.keyPrefix }, { key: 'id', label: '凭证 ID', children: detail.credentialId },
      { key: 'tenant', label: 'Tenant ID', children: detail.tenantId }, { key: 'owner', label: 'Tenant Owner 状态', children: detail.tenantOwnerStatus },
      { key: 'security', label: 'Tenant 安全状态', children: detail.tenantSecurityStatus }, { key: 'expiry', label: '到期时间', children: date(detail.expiresTime) },
      { key: 'rotated', label: '轮换来源', children: detail.rotatedFromId ? <Button type="link" onClick={() => { const next = new URLSearchParams(location.search); next.set('credential', detail.rotatedFromId!); next.delete('tab'); void navigate(`?${next}`); }}>{detail.rotatedFromId}</Button> : '—' },
      { key: 'create', label: '创建时间', children: date(detail.createTime) }, { key: 'update', label: '更新时间', children: date(detail.updateTime) },
    ]} /></section>
    <Tabs activeKey={tab} onChange={value => { const next = new URLSearchParams(location.search); next.set('tab', value); void navigate(`?${next}`, { replace: true }); }} items={[
      { key: 'scopes', label: 'Scope 授权' }, ...(has('OPERATIONS_AUDIT') ? [{ key: 'audit', label: '操作记录' }] : []),
    ]} />
    {tab === 'audit' ? <Audit key={`credential-${id}`} objectFilter={{ resourceType: 'API_CREDENTIAL', resourceId: id }} embedded />
      : <CredentialGrants key={`${id}:${reload}`} credential={detail} canRoutes={has('OPERATIONS_GATEWAY_CONFIGURATION')} />}
    {revoking && <RevokeCredential credential={detail} onClose={() => setRevoking(false)} onSaved={() => { setRevoking(false); setReload(v => v + 1); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
function CredentialGrants({ credential, canRoutes }: { credential: Credential; canRoutes: boolean }) {
  const navigate = useNavigate(); const page = usePaging<CredentialGrant>(`${credentialPath}/${encodeURIComponent(credential.credentialId)}/scopes`);
  return <section className="table-panel">{credential.status !== 'ACTIVE' && <p className="muted">凭证{statusLabels[credential.status]}。以下保留各条授权自身的状态与有效期。</p>}
    <div className="table-toolbar"><span>共 {page.data.total} 条授权记录</span><Button onClick={page.refresh}>{page.error ? '重试' : '刷新'}</Button></div>
    <Table<CredentialGrant> rowKey="scopeId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1100 }} pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} columns={[
      { title: 'Scope', width: 230, ellipsis: true, render: (_, row) => <div><div className="client-api-ellipsis" title={row.scopeCode}>{row.scopeCode}</div><div className="cell-meta client-api-ellipsis">{row.displayName || '未登记'}</div></div> },
      { title: '资源模式', dataIndex: 'resourcePattern', width: 140, ellipsis: true }, { title: '动作', dataIndex: 'actionCode', width: 110, ellipsis: true },
      { title: '授权状态', width: 100, render: (_, row) => <CredentialStatus status={row.status} /> },
      { title: '生效时间', dataIndex: 'effectiveFrom', width: 200, render: date }, { title: '失效时间', dataIndex: 'effectiveTo', width: 200, render: date },
      ...(canRoutes ? [{ title: '操作', width: 110, fixed: 'right' as const, render: (_: unknown, row: CredentialGrant) => <Button type="link" onClick={() => void navigate(`/gateway-configuration?tab=routes&requiredScopeCode=${encodeURIComponent(row.scopeCode)}`)}>关联 API</Button> }] : []),
    ]} /></section>;
}
function RevokeCredential({ credential, onClose, onSaved, onAuthorizationChanged }: { credential: Credential; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  async function save(values: { reason: string }) {
    const result = await editor.save(`${credentialPath}/${encodeURIComponent(credential.credentialId)}/revoke`, 'POST', { expectedVersion: credential.version, reason: values.reason.trim() },
      value => revokeMutation(value, credential.credentialId, credential.version));
    if (result) onSaved();
  }
  return <Modal title="紧急撤销凭证" open onCancel={() => { if (!editor.saving) onClose(); }} footer={null} closable={!editor.saving} maskClosable={!editor.saving}>
    <Descriptions column={1} items={[{ key: 'client', label: 'Client', children: credential.clientName }, { key: 'prefix', label: '密钥前缀', children: credential.keyPrefix }, { key: 'id', label: '凭证 ID', children: credential.credentialId }]} />
    <Typography.Paragraph>只撤销这一条凭证，撤销后不能恢复。已完成认证的请求可能继续处理。</Typography.Paragraph>
    <Form form={form} layout="vertical" onFinish={save}><Form.Item name="reason" label="撤销原因" rules={[{ required: true, whitespace: true, message: '请填写撤销原因' }]}>
      <Input.TextArea rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} /></Form.Item>
      {editor.conflict && <Alert type="warning" showIcon title="凭证已有更新，原因已保留。请重新打开详情核对最新状态。" />}
      <div className="configuration-actions client-api-modal-actions"><Button disabled={editor.saving} onClick={onClose}>取消</Button><Button danger type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict}>确认撤销</Button></div>
    </Form>
  </Modal>;
}
