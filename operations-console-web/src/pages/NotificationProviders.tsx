import { useState } from 'react';
import { Button, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { StatusChangeModal } from '../notification/StatusChangeModal';
import { decodeProvider, manageProviders, notificationAdminPath, pageOf, statusLabels } from '../notification/notification';
import type { ProviderSummary } from '../notification/notification';
import '../configuration/configuration.css';
import '../notification/notification.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const providerPage = pageOf(decodeProvider);
const roleLabels: Record<string, string> = { PRIMARY: '主', FALLBACK: '备' };

/** 发送账号：查看本环境配置情况与路由引用，有权限时启停；发件地址与凭据由部署配置维护。 */
export function NotificationProviders({ resources, onAuthorizationChanged }: Props) {
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === manageProviders);
  const page = usePaging<ProviderSummary>(`${notificationAdminPath}/providers`, providerPage);
  const [changing, setChanging] = useState<ProviderSummary | null>(null);
  const activeReferences = changing?.references.filter(r => r.routeStatus === 'ACTIVE') ?? [];
  return <div className="configuration-page notification-page">
    <div className="page-heading"><h1>发送账号</h1></div>
    <div className="notification-note">发件地址、区域与凭据由部署配置和 Vault 维护，此处只能查看和启停。「本环境已配置」为否时，路由到该账号的邮件无法发送。</div>
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 个账号</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<ProviderSummary> rowKey="providerCode" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1000 }} pagination={page.pagination} columns={[
        { title: '编码', width: 220, render: (_, row) => <span className="mono">{row.providerCode}</span> },
        { title: '介质', dataIndex: 'medium', width: 80 },
        { title: '状态', width: 90, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{statusLabels[row.status]}</Tag> },
        { title: '本环境已配置', width: 120, render: (_, row) => row.adapterConfigured ? <Tag color="green">是</Tag> : <Tag color="orange">否</Tag> },
        { title: '被引用', width: 320, render: (_, row) => row.references.length ? <div className="notification-refs">{row.references.map(r =>
          <span key={`${r.templateCode}:${r.role}`} className={r.routeStatus === 'ACTIVE' ? '' : 'muted'}>{r.templateCode}（{roleLabels[r.role]}）</span>)}</div>
          : <span className="muted">未被引用</span> },
        { title: '更新时间', width: 170, render: (_, row) => date(row.updateTime) },
        ...(canManage ? [{ title: '操作', width: 80, fixed: 'right' as const, render: (_: unknown, row: ProviderSummary) =>
          <Button size="small" danger={row.status === 'ACTIVE'} onClick={() => setChanging(row)}>{row.status === 'ACTIVE' ? '停用' : '启用'}</Button> }] : []),
      ]} /></section>
    {changing && <StatusChangeModal title={`${changing.status === 'ACTIVE' ? '停用' : '启用'}发送账号 ${changing.providerCode}？`}
      path={`${notificationAdminPath}/providers/${changing.providerCode}/status`} code={changing.providerCode}
      target={changing.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'} version={changing.version}
      impact={changing.status === 'ACTIVE' && activeReferences.length ? <div>
        <div>以下 {activeReferences.length} 条启用中的路由使用该账号，停用后这些模板将无法经该账号发送：</div>
        <ul className="notification-impact">{activeReferences.map(r => <li key={`${r.templateCode}:${r.role}`}>{r.templateCode}（{roleLabels[r.role]}账号）</li>)}</ul>
        <div>普通通知已受理的投递按重试策略处理；认证邮件不重试。</div></div> : undefined}
      onClose={() => setChanging(null)} onDone={() => { setChanging(null); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
