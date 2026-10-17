import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Empty, Space, Spin, Table, Tabs, Tag, Tooltip } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ClientSelector } from '../webhook/ClientSelector';
import { DeliveriesTab } from '../webhook/DeliveriesTab';
import { EndpointFormModal, EndpointStatusModal, RotateSecretModal, TestEventModal } from '../webhook/WebhookModals';
import {
  clientAccessText, decodeClient, decodeEndpoints, endpointLimit, endpointStatusLabels, manageWebhooks, pageOf, readWebhookQuery,
  webhookAdminPath,
} from '../webhook/webhook';
import type { Endpoint, WebhookClient, WebhookTab } from '../webhook/webhook';
import '../configuration/configuration.css';
import '../webhook/webhook.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: 'create' } | { kind: 'edit' | 'status' | 'rotate' | 'test'; endpoint: Endpoint };
interface EndpointState { loading: boolean; items: Endpoint[]; error: string }
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const clientPage = pageOf(decodeClient);
const errorText = (error: unknown) => error instanceof Error ? error.message : '暂时无法加载，请重试。';

/** Webhook 管理：先选 Client（写入 URL 查询参数），再按"端点 / 投递记录"两个标签页代 Client 配置与排查。 */
export function WebhookManagement({ resources, onAuthorizationChanged }: Props) {
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === manageWebhooks);
  const [params, setParams] = useSearchParams();
  const { clientId, tab } = readWebhookQuery(params);
  const client = useSelectedClient(clientId);
  const endpoints = useEndpoints(clientId, onAuthorizationChanged);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const select = (next: WebhookClient) => { client.set(next); setParams({ clientId: next.clientId, tab }); };
  const switchTab = (next: string) => { if (clientId) setParams({ clientId, tab: next as WebhookTab }, { replace: true }); };
  const done = () => { setDialog(null); endpoints.reload(); };
  const full = endpoints.items.length >= endpointLimit;
  return <div className="configuration-page webhook-page"><div className="page-heading"><h1>Webhook 管理</h1></div>
    <div className="webhook-client-bar"><div className="webhook-client-field"><div className="webhook-field-label">Client</div>
      <ClientSelector value={client.value} onChange={select} /></div>
      {client.value && <span className="webhook-meta">平台访问：{clientAccessText(client.value)} · 端点 {endpoints.loading
        ? client.value.endpointCount : endpoints.items.length} / {endpointLimit}</span>}</div>
    {!clientId ? <div className="webhook-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={<><strong>先选择一个 Client</strong><div className="muted">选择后查看该 Client 的 Webhook 端点和投递记录</div></>} /></div>
      : client.error ? <Alert type="warning" showIcon title={client.error} />
      : !client.value ? <Spin />
      : <Tabs activeKey={tab} onChange={switchTab} tabBarExtraContent={tab === 'endpoints' && canManage
        ? <Tooltip title={full ? `每个 Client 最多 ${endpointLimit} 个端点（含已停用），不支持删除；可修改或停用现有端点` : undefined}>
          <Button type="primary" disabled={full || endpoints.loading || !!endpoints.error} onClick={() => setDialog({ kind: 'create' })}>新建端点</Button></Tooltip>
        : undefined} items={[
        { key: 'endpoints', label: '端点', children: <EndpointsTable state={endpoints} canManage={canManage} onAction={setDialog} /> },
        { key: 'deliveries', label: '投递记录', children: <DeliveriesTab key={clientId} clientId={clientId} endpoints={endpoints.items}
          canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} /> },
      ]} />}
    {client.value && dialog?.kind === 'create' && <EndpointFormModal client={client.value} endpoint={null} onClose={() => setDialog(null)}
      onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {client.value && dialog?.kind === 'edit' && <EndpointFormModal client={client.value} endpoint={dialog.endpoint} onClose={() => setDialog(null)}
      onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'status' && <EndpointStatusModal endpoint={dialog.endpoint} onClose={() => setDialog(null)} onDone={done}
      onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'rotate' && <RotateSecretModal endpoint={dialog.endpoint} onClose={() => setDialog(null)} onDone={done}
      onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'test' && <TestEventModal endpoint={dialog.endpoint} onClose={() => setDialog(null)} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 端点列表；只读账号不显示操作列。 */
function EndpointsTable({ state, canManage, onAction }: { state: EndpointState & { reload: () => void }; canManage: boolean; onAction: (dialog: Dialog) => void }) {
  if (state.error) return <Alert type="warning" showIcon title={state.error} action={<Button size="small" onClick={state.reload}>重试</Button>} />;
  const now = Date.now();
  return <>
    <section className="table-panel"><Table<Endpoint> rowKey="endpointId" tableLayout="fixed" dataSource={state.items} loading={state.loading}
      pagination={false} scroll={{ x: 960 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该 Client 还没有端点" /> }} columns={[
        { title: '地址 / 订阅事件', render: (_, row) => <div><div className="webhook-url">{row.url}</div>
          <div className="cell-meta webhook-events-meta">{row.eventTypes.join(' · ')}</div></div> },
        { title: '状态', width: 90, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{endpointStatusLabels[row.status]}</Tag> },
        { title: '签名密钥', width: 190, render: (_, row) => <div>第 {row.signingSecret.rotationNo} 号
          {row.signingSecret.previousValidUntil && Date.parse(row.signingSecret.previousValidUntil) > now
            && <div className="webhook-grace">旧密钥有效至 {date(row.signingSecret.previousValidUntil)}</div>}</div> },
        { title: '更新时间', width: 170, render: (_, row) => date(row.updateTime) },
        ...(canManage ? [{ title: '操作', width: 260, render: (_: unknown, row: Endpoint) => <Space size={4} wrap>
          <Button size="small" type="link" onClick={() => onAction({ kind: 'edit', endpoint: row })}>修改</Button>
          <Button size="small" type="link" danger={row.status === 'ACTIVE'} onClick={() => onAction({ kind: 'status', endpoint: row })}>
            {row.status === 'ACTIVE' ? '停用' : '启用'}</Button>
          {row.status === 'ACTIVE' && <Button size="small" type="link" onClick={() => onAction({ kind: 'rotate', endpoint: row })}>轮换密钥</Button>}
          <Button size="small" type="link" onClick={() => onAction({ kind: 'test', endpoint: row })}>发送测试</Button>
        </Space> }] : []),
      ]} /></section>
    <div className="webhook-note">每个 Client 最多 {endpointLimit} 个端点（含已停用），不支持删除。地址必须是 https 公网地址，保存和每次发送都会校验。签名密钥只在新建和轮换时显示一次；轮换后旧密钥默认再签名 1 小时。</div>
  </>;
}

/** 当前所选 Client：从下拉选中时直接使用；从 URL 恢复时按 Client ID 精确查询一次。 */
function useSelectedClient(clientId: string | null) {
  const notify = useErrorNotification();
  const [value, set] = useState<WebhookClient | null>(null); const [error, setError] = useState('');
  useEffect(() => {
    setError('');
    if (!clientId) { set(null); return; }
    if (value?.clientId === clientId) return;
    set(null);
    const abort = new AbortController();
    const query = new URLSearchParams({ keyword: clientId, page: '1', size: '10' });
    session.request(`${webhookAdminPath}/clients?${query}`, { signal: abort.signal }, clientPage)
      .then(result => {
        if (abort.signal.aborted) return;
        const found = result.items.find(item => item.clientId === clientId);
        if (found) set(found); else setError(`未找到 Client ${clientId}，或该 Client 未开通平台访问。`);
      })
      .catch(failure => { if (!abort.signal.aborted) { setError(errorText(failure)); notify(failure, 'Client 加载失败'); } });
    return () => abort.abort();
    // 选中的 Client 由下拉直接写入，这里只在 URL 中的 Client ID 变化时补查。
  }, [clientId, notify]);
  return { value: value?.clientId === clientId ? value : null, error, set };
}

/** 所选 Client 的端点（不分页，最多 5 个）；端点数与投递筛选的端点下拉也来自这里。 */
function useEndpoints(clientId: string | null, onAuthorizationChanged: () => void) {
  const [state, setState] = useState<EndpointState & { clientId: string | null }>({ clientId: null, loading: false, items: [], error: '' });
  const [reload, setReload] = useState(0);
  // 上层每次渲染都会传入新的回调，放进 ref 避免触发重新加载。
  const authorizationChanged = useRef(onAuthorizationChanged); authorizationChanged.current = onAuthorizationChanged;
  useEffect(() => {
    if (!clientId) { setState({ clientId: null, loading: false, items: [], error: '' }); return; }
    const abort = new AbortController();
    // 刷新同一 Client 时保留旧列表，切换 Client 时先清空。
    setState(current => ({ clientId, loading: true, error: '', items: current.clientId === clientId ? current.items : [] }));
    session.request(`${webhookAdminPath}/clients/${clientId}/endpoints`, { signal: abort.signal }, decodeEndpoints)
      .then(items => { if (!abort.signal.aborted) setState({ clientId, loading: false, items, error: '' }); })
      .catch(failure => {
        if (abort.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 403) authorizationChanged.current();
        setState({ clientId, loading: false, items: [], error: errorText(failure) });
      });
    return () => abort.abort();
  }, [clientId, reload]);
  return { ...state, reload: () => setReload(value => value + 1) };
}
