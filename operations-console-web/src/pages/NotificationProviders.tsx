import { useState } from 'react';
import { Button, Space, Table, Tag, Tooltip } from 'antd';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { CredentialModal, ProviderFormModal, TestSendModal } from '../notification/ProviderModals';
import { StatusChangeModal } from '../notification/StatusChangeModal';
import {
  credentialProviders, decodeProvider, editProviders, eventReceiving, manageProviders, missingConfigurationLabels, notificationAdminPath, pageOf,
  providerTypeLabels, statusLabels,
} from '../notification/notification';
import type { ProviderSummary } from '../notification/notification';
import '../configuration/configuration.css';
import '../notification/notification.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: 'create' } | { kind: 'edit' | 'credential' | 'test' | 'status'; provider: ProviderSummary };
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const providerPage = pageOf(decodeProvider);
const roleLabels: Record<string, string> = { PRIMARY: '主', FALLBACK: '备' };
const missingText = (row: ProviderSummary) => row.missingConfiguration.map(f => missingConfigurationLabels[f] ?? f).join('、');

/** 投递事件接收状态：未配置、等待首次拉取、拉取失败（附最近成功时间）、接收正常（附最近拉取与最近事件时间）。 */
function EventCell({ row }: { row: ProviderSummary }) {
  const state = eventReceiving(row);
  if (state.kind === 'none') return <span className="muted">未配置</span>;
  if (state.kind === 'waiting') return <span className="muted">等待首次拉取</span>;
  if (state.kind === 'failed') return <div className="notification-refs"><span className="notification-missing">拉取失败：{state.error}</span>
    <span className="muted">{state.lastPoll ? `最近成功 ${date(state.lastPoll)}` : '从未成功'}</span></div>;
  return <div className="notification-refs"><span className="notification-ok">接收正常</span><span className="muted">最近拉取 {date(state.lastPoll)}</span>
    <span className="muted">{state.lastEvent ? `最近事件 ${date(state.lastEvent)}` : '暂无事件'}</span></div>;
}

/** 发送账号：维护区域、发件地址与密钥（密钥只写不读），测试发送，按配置完整情况启停。 */
export function NotificationProviders({ resources, onAuthorizationChanged }: Props) {
  const has = (code: string) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === code);
  const canManage = has(manageProviders); const canEdit = has(editProviders); const canCredential = has(credentialProviders);
  const page = usePaging<ProviderSummary>(`${notificationAdminPath}/providers`, providerPage);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const done = () => { setDialog(null); page.refresh(); };
  const changing = dialog?.kind === 'status' ? dialog.provider : null;
  const activeReferences = changing?.references.filter(r => r.routeStatus === 'ACTIVE') ?? [];
  const showActions = canManage || canEdit || canCredential;
  return <div className="configuration-page notification-page">
    <div className="page-heading"><h1>发送账号</h1>{canEdit && <Button type="primary" onClick={() => setDialog({ kind: 'create' })}>+ 新建账号</Button>}</div>
    <div className="notification-note">区域、发件地址与密钥在此维护；密钥保存在 Vault，录入后无法查看，只能更换。配置不完整的账号不能启用，路由到它的邮件无法发送。</div>
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 个账号</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<ProviderSummary> rowKey="providerCode" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1440 }} pagination={page.pagination} columns={[
        { title: '编码', width: 170, render: (_, row) => <span className="mono">{row.providerCode}</span> },
        { title: '类型', width: 110, render: (_, row) => providerTypeLabels[row.providerType] ?? row.providerType },
        { title: '状态', width: 80, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{statusLabels[row.status]}</Tag> },
        { title: '区域 / 发件地址', width: 220, render: (_, row) => <div className="notification-refs"><span>{row.region ?? <span className="notification-missing">未填写区域</span>}</span>
          <span className={row.fromAddress ? 'muted' : 'notification-missing'}>{row.fromAddress ?? '未填写发件地址'}</span></div> },
        { title: '密钥 / 配置完整', width: 200, render: (_, row) => <div className="notification-refs">
          {row.credentialConfigured ? <span className="notification-ok">已录入</span> : <span className="notification-missing">未录入</span>}
          {row.credentialUpdateTime && <span className="muted">更换于 {date(row.credentialUpdateTime)}</span>}
          {row.missingConfiguration.length ? <span className="notification-missing">配置不完整：缺少{missingText(row)}</span> : <span className="notification-ok">配置完整</span>}
          {!row.adapterConfigured && <span className="notification-missing">本环境未启用该服务商</span>}</div> },
        { title: '投递事件', width: 180, render: (_, row) => <EventCell row={row} /> },
        { title: '被引用', width: 250, render: (_, row) => row.references.length ? <div className="notification-refs">{row.references.map(r =>
          <span key={`${r.templateCode}:${r.role}`} className={r.routeStatus === 'ACTIVE' ? '' : 'muted'}>{r.templateCode}（{roleLabels[r.role]}）</span>)}</div>
          : <span className="muted">未被引用</span> },
        ...(showActions ? [{ title: '操作', width: 230, fixed: 'right' as const, render: (_: unknown, row: ProviderSummary) => <Space size={4} wrap>
          {canEdit && <Button size="small" type="link" onClick={() => setDialog({ kind: 'edit', provider: row })}>编辑</Button>}
          {canCredential && <Button size="small" type="link" onClick={() => setDialog({ kind: 'credential', provider: row })}>{row.credentialConfigured ? '更换密钥' : '录入密钥'}</Button>}
          {canCredential && <Tooltip title={row.missingConfiguration.length ? `缺少${missingText(row)}` : !row.adapterConfigured ? '本环境未启用该服务商' : undefined}>
            <Button size="small" type="link" disabled={row.missingConfiguration.length > 0 || !row.adapterConfigured}
              onClick={() => setDialog({ kind: 'test', provider: row })}>测试发送</Button></Tooltip>}
          {canManage && <Tooltip title={row.status === 'DISABLED' && row.missingConfiguration.length ? `配置不完整，缺少${missingText(row)}` : undefined}>
            <Button size="small" type="link" danger={row.status === 'ACTIVE'} disabled={row.status === 'DISABLED' && row.missingConfiguration.length > 0}
              onClick={() => setDialog({ kind: 'status', provider: row })}>{row.status === 'ACTIVE' ? '停用' : '启用'}</Button></Tooltip>}
        </Space> }] : []),
      ]} /></section>
    {dialog?.kind === 'create' && <ProviderFormModal provider={null} onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'edit' && <ProviderFormModal provider={dialog.provider} onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'credential' && <CredentialModal provider={dialog.provider} onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'test' && <TestSendModal provider={dialog.provider} onClose={() => setDialog(null)} onAuthorizationChanged={onAuthorizationChanged} />}
    {changing && <StatusChangeModal title={`${changing.status === 'ACTIVE' ? '停用' : '启用'}发送账号 ${changing.providerCode}？`}
      path={`${notificationAdminPath}/providers/${changing.providerCode}/status`} code={changing.providerCode}
      target={changing.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'} version={changing.version}
      impact={changing.status === 'ACTIVE' && activeReferences.length ? <div>
        <div>以下 {activeReferences.length} 条启用中的路由使用该账号，停用后这些模板将无法经该账号发送：</div>
        <ul className="notification-impact">{activeReferences.map(r => <li key={`${r.templateCode}:${r.role}`}>{r.templateCode}（{roleLabels[r.role]}账号）</li>)}</ul>
        <div>普通通知已受理的投递按重试策略处理；认证邮件不重试。</div></div> : undefined}
      onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
