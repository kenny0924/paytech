import { useMemo, useState } from 'react';
import { Button, Space, Table, Tag, Tooltip } from 'antd';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { CredentialModal, ProviderFormModal, TestSendModal } from '../notification/ProviderModals';
import { StatusChangeModal } from '../notification/StatusChangeModal';
import {
  credentialProviders, decodeProvider, editProviders, eventReceiving, manageProviders, missingConfigurationText, notificationAdminPath, pageOf,
} from '../notification/notification';
import type { ProviderSummary } from '../notification/notification';
import '../configuration/configuration.css';
import '../notification/notification.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Dialog = { kind: 'create' } | { kind: 'edit' | 'credential' | 'test' | 'status'; provider: ProviderSummary };
const providerPage = pageOf(decodeProvider);
const missingText = (row: ProviderSummary) => missingConfigurationText(row.missingConfiguration);

/** 投递事件接收状态：未配置、等待首次拉取、拉取失败（附最近成功时间）、接收正常（附最近拉取与最近事件时间）。 */
function EventCell({ row }: { row: ProviderSummary }) {
  const t = useT('notification');
  const state = eventReceiving(row);
  if (state.kind === 'none') return <span className="muted">{t('providers.event.none')}</span>;
  if (state.kind === 'waiting') return <span className="muted">{t('providers.event.waiting')}</span>;
  if (state.kind === 'failed') return <div className="notification-refs"><span className="notification-missing">{t('providers.event.failed', { error: state.error })}</span>
    <span className="muted">{state.lastPoll ? t('providers.event.lastSucceeded', { time: formatDateTime(state.lastPoll) }) : t('providers.event.neverSucceeded')}</span></div>;
  return <div className="notification-refs"><span className="notification-ok">{t('providers.event.ok')}</span><span className="muted">{t('providers.event.lastPoll', { time: formatDateTime(state.lastPoll) })}</span>
    <span className="muted">{state.lastEvent ? t('providers.event.lastEvent', { time: formatDateTime(state.lastEvent) }) : t('providers.event.noEvents')}</span></div>;
}

/** 发送账号：维护区域、发件地址与密钥（密钥只写不读），测试发送，按配置完整情况启停。 */
export function NotificationProviders({ resources, onAuthorizationChanged }: Props) {
  const t = useT('notification'), tCommon = useT('common'), tNav = useT('nav');
  const reference = useMemo(() => (r: { templateCode: string; role: string }) =>
    t('providers.reference', { templateCode: r.templateCode, role: enumLabel(t, 'enum.routeRole', r.role) }), [t]);
  const has = (code: string) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === code);
  const canManage = has(manageProviders); const canEdit = has(editProviders); const canCredential = has(credentialProviders);
  const page = usePaging<ProviderSummary>(`${notificationAdminPath}/providers`, providerPage);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const done = () => { setDialog(null); page.refresh(); };
  const changing = dialog?.kind === 'status' ? dialog.provider : null;
  const activeReferences = changing?.references.filter(r => r.routeStatus === 'ACTIVE') ?? [];
  const showActions = canManage || canEdit || canCredential;
  return <div className="configuration-page notification-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_NOTIFICATION_PROVIDERS')}</h1>{canEdit && <Button type="primary" onClick={() => setDialog({ kind: 'create' })}>{t('providers.create')}</Button>}</div>
    <div className="notification-note">{t('providers.note')}</div>
    <section className="table-panel"><div className="table-toolbar"><span>{t('providers.count', { count: page.data.total })}</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<ProviderSummary> rowKey="providerCode" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1440 }} pagination={page.pagination} columns={[
        { title: t('providers.column.code'), width: 170, render: (_, row) => <span className="mono">{row.providerCode}</span> },
        { title: t('providers.column.type'), width: 110, render: (_, row) => enumLabel(t, 'enum.providerType', row.providerType) },
        { title: t('providers.column.status'), width: 80, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.status', row.status)}</Tag> },
        { title: t('providers.column.regionAndFrom'), width: 220, render: (_, row) => <div className="notification-refs"><span>{row.region ?? <span className="notification-missing">{t('providers.noRegion')}</span>}</span>
          <span className={row.fromAddress ? 'muted' : 'notification-missing'}>{row.fromAddress ?? t('providers.noFromAddress')}</span></div> },
        { title: t('providers.column.credential'), width: 200, render: (_, row) => <div className="notification-refs">
          {row.credentialConfigured ? <span className="notification-ok">{t('providers.credentialConfigured')}</span> : <span className="notification-missing">{t('providers.credentialMissing')}</span>}
          {row.credentialUpdateTime && <span className="muted">{t('providers.credentialReplacedAt', { time: formatDateTime(row.credentialUpdateTime) })}</span>}
          {row.missingConfiguration.length ? <span className="notification-missing">{t('providers.incomplete', { fields: missingText(row) })}</span> : <span className="notification-ok">{t('providers.complete')}</span>}
          {!row.adapterConfigured && <span className="notification-missing">{t('providers.adapterDisabled')}</span>}</div> },
        { title: t('providers.column.events'), width: 180, render: (_, row) => <EventCell row={row} /> },
        { title: t('providers.column.references'), width: 250, render: (_, row) => row.references.length ? <div className="notification-refs">{row.references.map(r =>
          <span key={`${r.templateCode}:${r.role}`} className={r.routeStatus === 'ACTIVE' ? '' : 'muted'}>{reference(r)}</span>)}</div>
          : <span className="muted">{t('providers.notReferenced')}</span> },
        ...(showActions ? [{ title: t('providers.column.actions'), width: 230, fixed: 'right' as const, render: (_: unknown, row: ProviderSummary) => <Space size={4} wrap>
          {canEdit && <Button size="small" type="link" onClick={() => setDialog({ kind: 'edit', provider: row })}>{t('providers.action.edit')}</Button>}
          {canCredential && <Button size="small" type="link" onClick={() => setDialog({ kind: 'credential', provider: row })}>{row.credentialConfigured ? t('providers.action.replaceCredential') : t('providers.action.enterCredential')}</Button>}
          {canCredential && <Tooltip title={row.missingConfiguration.length ? t('providers.missing', { fields: missingText(row) }) : !row.adapterConfigured ? t('providers.adapterDisabled') : undefined}>
            <Button size="small" type="link" disabled={row.missingConfiguration.length > 0 || !row.adapterConfigured}
              onClick={() => setDialog({ kind: 'test', provider: row })}>{t('providers.action.testSend')}</Button></Tooltip>}
          {canManage && <Tooltip title={row.status === 'DISABLED' && row.missingConfiguration.length ? t('providers.incompleteTooltip', { fields: missingText(row) }) : undefined}>
            <Button size="small" type="link" danger={row.status === 'ACTIVE'} disabled={row.status === 'DISABLED' && row.missingConfiguration.length > 0}
              onClick={() => setDialog({ kind: 'status', provider: row })}>{row.status === 'ACTIVE' ? t('providers.action.disable') : t('providers.action.enable')}</Button></Tooltip>}
        </Space> }] : []),
      ]} /></section>
    {dialog?.kind === 'create' && <ProviderFormModal provider={null} onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'edit' && <ProviderFormModal provider={dialog.provider} onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'credential' && <CredentialModal provider={dialog.provider} onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'test' && <TestSendModal provider={dialog.provider} onClose={() => setDialog(null)} onAuthorizationChanged={onAuthorizationChanged} />}
    {changing && <StatusChangeModal title={changing.status === 'ACTIVE' ? t('providers.disableTitle', { code: changing.providerCode }) : t('providers.enableTitle', { code: changing.providerCode })}
      path={`${notificationAdminPath}/providers/${changing.providerCode}/status`} code={changing.providerCode}
      target={changing.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'} version={changing.version}
      impact={changing.status === 'ACTIVE' && activeReferences.length ? <div>
        <div>{t('providers.disableImpact', { count: activeReferences.length })}</div>
        <ul className="notification-impact">{activeReferences.map(r => <li key={`${r.templateCode}:${r.role}`}>
          {t('providers.referenceAccount', { templateCode: r.templateCode, role: enumLabel(t, 'enum.routeRole', r.role) })}</li>)}</ul>
        <div>{t('providers.disableRetryNote')}</div></div> : undefined}
      onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
