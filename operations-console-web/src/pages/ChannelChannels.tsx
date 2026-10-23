import { useEffect, useMemo, useState } from 'react';
import { Button, Form, Input, Segmented, Select, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import { requestAllPages, requestPage } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { useT } from '../i18n/react';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { useDetail as useOptionalDetail } from '../externalApi/externalApiHooks';
import { decodeImplementationList, integrationPath, manageProtocol } from '../externalApi/externalApiModel';
import { ExternalApiFrame } from '../externalApi/ui';
import { FlowEditor } from '../externalApi/FlowEditor';
import { IntegrationApiEditor } from '../externalApi/IntegrationApiEditor';
import { ScriptEditor } from '../externalApi/ScriptEditor';
import { VerifyAccountEditor } from '../externalApi/verify/VerifyAccountEditor';
import { VerifyCaseEditor } from '../externalApi/verify/VerifyCaseEditor';
import { VerifyRunDetail } from '../externalApi/verify/VerifyRunDetail';
import { VerifyTab } from '../externalApi/verify/VerifyTab';
import {
  channelButtons, channelCodePattern, channelPaths, channelRoutes, channelTypeLabel, decodeAccount, decodeAccountPage, decodeChannel,
  decodeChannelPage, decodeMidPage, editable, entityNoPattern, hasButton, lifecycleActions, lifecycleStatuses, lifecycleStatusLabel, listFilters, localTime,
  primaryLifecycleAction, tokenSuffixLabel,
} from '../channel/channel';
import type { Channel, ChannelAccountSummary, ChannelMidSummary, ChannelSummary, ChannelType, LifecycleAction } from '../channel/channel';
import {
  activeGrantsByCaller, channelListFilters, channelTypeFilter, detailTab, detailTabLabel, detailTabs, parseChannelPath, typeCountsSequentially,
  typeSegmentSearch, workspaceRoutes,
} from '../channel/channelWorkspace';
import type { ChannelPage, DetailTab, ImplementedApi } from '../channel/channelWorkspace';
import { AccountConfigurationModal } from '../channel/AccountConfigurationModal';
import { ChannelChanges } from '../channel/ChannelChanges';
import { ChannelCredential } from '../channel/ChannelCredential';
import { ChannelGrants, useChannelGrants } from '../channel/ChannelGrants';
import { ChannelOverview, EndpointEditorModal } from '../channel/ChannelOverview';
import { ChannelProtocol } from '../channel/ChannelProtocol';
import { ChannelCodeRedirect } from '../channel/ChannelResolve';
import { ChannelNote, CommandModal, StatusChangeModal, StatusTag, Summary } from '../channel/ChannelParts';
import { useChannelCommand } from '../channel/useChannelCommand';
import '../configuration/configuration.css';
import '../channel/channel.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Base { onClose: () => void; onAuthorizationChanged: () => void }
type StatusTarget = Pick<Channel, 'channelId' | 'channelCode' | 'channelType'>;

/**
 * 渠道：列表（?type= 类型分段）、详情（?tab= 页签按类型不同）、按编码进入（/code/:channelCode），
 * 以及协议配置与沙箱验证子页（/:channelId/protocol|verify/:section/:code，编辑器复用原接入方页）。
 */
export function ChannelChannels(props: Props) {
  const location = useLocation();
  const page = parseChannelPath(location.pathname);
  if (!page) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  if (page.kind === 'byCode') return <ChannelCodeRedirect channelCode={page.channelCode} suffix={page.suffix} />;
  if (page.kind === 'protocol' || page.kind === 'verify') return <ChannelChildPage key={location.pathname} page={page} {...props} />;
  return <div className="configuration-page channel-page">
    {page.kind === 'detail' ? <ChannelDetail key={page.channelId} channelId={page.channelId} {...props} /> : <ChannelList key={location.search} {...props} />}
  </div>;
}

interface FilterValues { keyword?: string; status?: string }
type ChannelStep = { kind: 'status'; channel: StatusTarget; action: LifecycleAction } | { kind: 'create' } | null;

/** 类型分段的计数：与列表同一筛选条件（关键字、状态），三个查询依次取总数（不并发，见 typeCountsSequentially）。 */
function useTypeCounts(filters: Readonly<Record<string, string>>) {
  const [counts, setCounts] = useState<Partial<Record<'ALL' | ChannelType, number>>>({});
  const { keyword, status } = filters;
  useEffect(() => {
    const controller = new AbortController();
    const base = { ...(keyword ? { keyword } : {}), ...(status ? { status } : {}) };
    const total = (channelType: ChannelType | null) => controller.signal.aborted ? Promise.resolve(undefined)
      : requestPage(channelPaths.channels, { page: 1, size: 10 }, controller.signal, undefined, { ...base, ...(channelType ? { channelType } : {}) },
        decodeChannelPage).then(result => result.total).catch(() => undefined);
    void typeCountsSequentially(total).then(next => {
      if (!controller.signal.aborted) setCounts(next);
    });
    return () => controller.abort();
  }, [keyword, status]);
  return counts;
}

/** 渠道列表（画板 01）：类型分段写入 ?type=，编码 / 名称与状态筛选；行内主动作只给当前状态允许的下一步。 */
function ChannelList({ resources, onAuthorizationChanged }: Props) {
  const t = useT('channel'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate();
  const query = new URLSearchParams(location.search);
  const filters = channelListFilters(location.search);
  const type = channelTypeFilter(location.search);
  const [form] = Form.useForm<FilterValues>();
  const [step, setStep] = useState<ChannelStep>(null);
  const page = usePaging<ChannelSummary>(channelPaths.channels, decodeChannelPage, filters, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) }, notifyOnError: false,
    onQueryChange: next => {
      const params = new URLSearchParams();
      if (next.filters.keyword) params.set('keyword', next.filters.keyword);
      if (next.filters.status) params.set('status', next.filters.status);
      if (type) params.set('type', type);
      if (next.paging.page > 1) params.set('page', String(next.paging.page));
      if (next.paging.size !== 10) params.set('size', String(next.paging.size));
      const text = params.toString();
      void navigate(text ? `?${text}` : '');
    },
  });
  const counts = useTypeCounts(filters);
  const canManage = hasButton(resources, channelButtons.channelManage);
  const segment = (label: string, value: 'ALL' | ChannelType) => ({ value, label: <span>{label} <span className="channel-cell-meta">{counts[value] ?? ''}</span></span> });
  return <>
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_CHANNEL_CHANNELS')}</h1>{canManage && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>{t('channels.create')}</Button>}</div>
    <div className="channel-toolbar">
      <Segmented value={type ?? 'ALL'} onChange={value => void navigate(typeSegmentSearch(location.search, value === 'ALL' ? null : value as ChannelType))}
        options={[segment(t('filter.all'), 'ALL'), segment(channelTypeLabel('PAYMENT'), 'PAYMENT'), segment(channelTypeLabel('SERVICE'), 'SERVICE')]} />
      <Form form={form} name="channel-filter" layout="inline" initialValues={{ keyword: filters.keyword, status: filters.status }}
        onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
        <Form.Item name="keyword"><Input allowClear maxLength={64} placeholder={t('channels.keywordPlaceholder')} style={{ width: 200 }} /></Form.Item>
        <Form.Item name="status"><Select allowClear placeholder={t('grants.allStatuses')} style={{ width: 160 }}
          options={lifecycleStatuses.map(value => ({ value, label: `${value} · ${lifecycleStatusLabel(value)}` }))} /></Form.Item>
        <Button htmlType="submit">{t('changes.search')}</Button>
        {(filters.keyword || filters.status) && <Button type="link" onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('channels.clearFilters')}</Button>}
      </Form>
    </div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<ChannelSummary> rowKey="channelId" size="middle" loading={page.loading} dataSource={page.data.items}
        pagination={{ ...page.pagination, showTotal: total => t('grants.total', { total }) }} scroll={{ x: 1180 }} locale={{ emptyText: t('channels.noMatch') }} columns={[
          { title: t('channels.code'), width: 170, render: (_, row) => <Button type="link" size="small" className="mono" onClick={() => void navigate(channelRoutes.channel(row.channelId))}>
            {row.channelCode}</Button> },
          { title: t('form.name'), width: 190, dataIndex: 'channelName', ellipsis: true },
          { title: t('capability.type'), width: 80, render: (_, row) => <ChannelTypeTag type={row.channelType} short /> },
          { title: t('grants.status'), width: 120, render: (_, row) => <StatusTag status={row.status} /> },
          { title: t('overview.endpoints'), width: 100, render: (_, row) => row.endpointCount ? t('channels.endpointCount', { count: row.endpointCount }) : <span className="channel-cell-meta">{t('account.notConfigured')}</span> },
          { title: t('channels.accountsMids'), width: 140, render: (_, row) => row.channelType === 'SERVICE'
            ? row.accountCount ? t('channels.credentialCreated') : <span className="channel-cell-meta">{t('channels.credentialMissing')}</span> : `${row.accountCount} / ${row.midCount}` },
          { title: t('workspace.tab.grants'), width: 140, render: (_, row) => row.channelType === 'SERVICE' ? t('channels.grantCount', { count: row.activeGrantCount ?? 0 }) : '—' },
          { title: t('channels.updatedAt'), width: 150, render: (_, row) => localTime(row.updateTime) },
          { title: t('grants.actions'), width: 130, render: (_, row) => {
            const action = primaryLifecycleAction(row.status, canManage);
            return <span className="channel-actions">
              <Button type="link" size="small" onClick={() => void navigate(channelRoutes.channel(row.channelId))}>{t('protocol.details')}</Button>
              {action && <Button type="link" size="small" onClick={() => setStep({ kind: 'status', channel: row, action })}>{action.label}</Button>}
            </span>;
          } },
        ]} />}
    {step?.kind === 'create' && <CreateChannelModal onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={channel => void navigate(channelRoutes.channel(channel.channelId))} />}
    {step?.kind === 'status' && <ChannelStatusModal channel={step.channel} action={step.action} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setStep(null)} onDone={() => { setStep(null); page.refresh(); }} />}
  </>;
}

function ChannelTypeTag({ type, short }: { type: ChannelType; short?: boolean }) {
  const t = useT('channel');
  return <Tag color={type === 'SERVICE' ? 'purple' : 'blue'}>{short ? (type === 'SERVICE' ? t('channels.typeShort.service') : t('channels.typeShort.payment')) : channelTypeLabel(type)}</Tag>;
}

/**
 * 渠道状态变更：两类渠道的激活前置都只要求出站地址已配置；关闭要求其下账号与 MID 已全部关闭。
 * 暂停服务渠道（画板 11）须填写原因，并列出受影响的 ACTIVE 授权。
 */
function ChannelStatusModal({ channel, action, onDone, ...base }: Base & { channel: StatusTarget; action: LifecycleAction; onDone: (channel: Channel) => void }) {
  const t = useT('channel');
  const service = channel.channelType === 'SERVICE';
  const stopping = action.targetStatus === 'SUSPENDED' || action.targetStatus === 'CLOSED';
  const grants = useChannelGrants(channel.channelId, service && stopping);
  const affected = activeGrantsByCaller(grants.grants ?? []);
  const description = action.targetStatus === 'ACTIVE' ? t('channels.status.activateNote')
    : action.targetStatus === 'CLOSED' ? t('channels.status.closeNote')
      : service ? t('channels.status.suspendServiceNote')
        : t('channels.status.suspendPaymentNote');
  return <StatusChangeModal title={t('channels.status.title', { action: action.label, code: channel.channelCode })} path={channelPaths.channelStatus(channel.channelId)}
    targetStatus={action.targetStatus} actionLabel={action.targetStatus === 'SUSPENDED' ? t('channels.status.suspendChannel') : action.label} description={description}
    reasonRequired={service && action.targetStatus === 'SUSPENDED'} decode={value => decodeChannel(value, channel.channelId)} onDone={onDone} {...base}>
    {service && stopping && <div className="channel-affected">
      <strong>{t('channels.status.affected', { count: grants.grants ? String(affected.reduce((sum, item) => sum + item.standardApiCodes.length, 0)) : '…' })}</strong>
      {grants.failure ? <span className="channel-cell-meta">{t('channels.status.grantsFailed')}</span>
        : affected.length === 0 && grants.grants ? <span className="channel-cell-meta">{t('accountConfig.wizard.none')}</span>
          : affected.map(item => <div key={item.callerService}><span className="mono">{item.callerService}</span>
            <span className="channel-cell-meta"> {item.standardApiCodes.join(t('list.separator'))}</span></div>)}
      <div className="channel-cell-meta">{t('channels.status.reasonNote')}</div>
    </div>}
  </StatusChangeModal>;
}

/** 新建渠道（画板 02）：先选类型（创建后不可改），编码 [A-Z][A-Z0-9_]{1,63} 全局唯一，可填描述。 */
function CreateChannelModal({ onCreated, ...base }: Base & { onCreated: (channel: Channel) => void }) {
  const t = useT('channel');
  const [form] = Form.useForm<{ channelType: ChannelType; channelCode: string; channelName: string; description?: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const channelType = Form.useWatch('channelType', form);
  const submit = async () => {
    const values = await form.validateFields();
    const channel = await command.run(channelPaths.channels, 'POST', { channelType: values.channelType, channelCode: values.channelCode,
      channelName: values.channelName.trim(), description: values.description?.trim() || null }, value => decodeChannel(value), t('channels.createFailed'));
    if (channel) onCreated(channel);
  };
  return <CommandModal title={t('channels.create')} command={command} okText={t('channels.createOk')} width={720} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-create" layout="vertical" initialValues={{ channelType: 'PAYMENT' }}>
      <Form.Item name="channelType" label={<span>{t('channels.type')} <span className="channel-cell-meta">{t('channels.immutable')}</span></span>} rules={[{ required: true, message: t('channels.typeRequired') }]}>
        <TypeCards />
      </Form.Item>
      <Form.Item name="channelCode" label={t('channels.code')} normalize={(v?: string) => v?.trim().toUpperCase()}
        extra={t('channels.codeExtra')}
        rules={[{ required: true, message: t('form.required') }, { pattern: channelCodePattern, message: t('channels.codePattern') }]}>
        <Input maxLength={64} className="mono" placeholder={channelType === 'SERVICE' ? 'GOOGLE_MAPS' : 'PAYERMAX'} /></Form.Item>
      <Form.Item name="channelName" label={t('form.name')} rules={[{ required: true, whitespace: true, message: t('form.required') }, { max: 255, message: t('form.maxLength', { max: 255 }) }]}>
        <Input maxLength={255} /></Form.Item>
      <Form.Item name="description" label={t('overview.description')} rules={[{ max: 512, message: t('form.maxLength', { max: 512 }) }]}>
        <Input.TextArea rows={2} maxLength={512} showCount placeholder={t('channels.descriptionPlaceholder')} /></Form.Item>
    </Form>
    <ChannelNote>{channelType === 'SERVICE' ? t('channels.createNoteService') : t('channels.createNotePayment')}</ChannelNote>
  </CommandModal>;
}

/** 类型卡片：文案均为 channel.channels.typeCard.* 的 key，渲染时翻译。 */
const typeCards: { type: ChannelType; summary: string; includes: string[]; excludes: string[] }[] = [
  { type: 'PAYMENT', summary: 'channels.typeCard.payment.summary',
    includes: ['channels.typeCard.payment.accounts', 'channels.typeCard.payment.mids', 'channels.typeCard.protocol'], excludes: ['channels.typeCard.payment.noGrants'] },
  { type: 'SERVICE', summary: 'channels.typeCard.service.summary',
    includes: ['channels.typeCard.service.credential', 'channels.typeCard.protocol', 'workspace.tab.grants'], excludes: ['channels.typeCard.service.noMids'] },
];

/** 类型卡片二选一（受控于 Form.Item）。 */
function TypeCards({ value, onChange }: { value?: ChannelType; onChange?: (value: ChannelType) => void }) {
  const t = useT('channel');
  return <div className="channel-type-cards" role="radiogroup">{typeCards.map(card => <button type="button" key={card.type} role="radio"
    aria-checked={value === card.type} className={`channel-type-card${value === card.type ? ' is-selected' : ''}`} onClick={() => onChange?.(card.type)}>
    <div className="channel-type-card-head"><strong>{channelTypeLabel(card.type)}</strong><span className="mono">{card.type}</span></div>
    <p>{t(card.summary)}</p>
    <ul>{card.includes.map(item => <li key={item}>✓ {t(item)}</li>)}{card.excludes.map(item => <li key={item} className="is-excluded">— {t(item)}</li>)}</ul>
  </button>)}</div>;
}

type DetailStep = { kind: 'edit' } | { kind: 'status'; action: LifecycleAction } | { kind: 'endpoints' } | { kind: 'account'; account?: ChannelAccountSummary }
  | { kind: 'configure'; account: ChannelAccountSummary } | { kind: 'accountStatus'; account: ChannelAccountSummary; action: LifecycleAction } | null;

/** 本渠道的全部账号（概览激活条件、凭据页签、变更记录对象选择）。 */
function useChannelAccounts(channelId: string, revision: number) {
  const [accounts, setAccounts] = useState<ChannelAccountSummary[]>();
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages(channelPaths.accounts, controller.signal, undefined, { channelId }, decodeAccountPage)
      .then(items => { if (!controller.signal.aborted) setAccounts(items); })
      .catch(() => { if (!controller.signal.aborted) setAccounts(undefined); });
    return () => controller.abort();
  }, [channelId, revision]);
  return accounts;
}

/** 列表摘要中的计数（账号、MID、ACTIVE 授权），用于页签计数；读取失败时不显示。 */
function useChannelCounts(channel: Channel | undefined, revision: number) {
  const [summary, setSummary] = useState<ChannelSummary | null>(null);
  const code = channel?.channelCode;
  useEffect(() => {
    if (!code) return undefined;
    const controller = new AbortController();
    requestAllPages(channelPaths.channels, controller.signal, undefined, { keyword: code }, decodeChannelPage)
      .then(items => { if (!controller.signal.aborted) setSummary(items.find(item => item.channelCode === code) ?? null); })
      .catch(() => { if (!controller.signal.aborted) setSummary(null); });
    return () => controller.abort();
  }, [code, revision]);
  return summary;
}

/**
 * 渠道详情（画板 03 / 04 / 05 / 07 / 10）：标题动作按当前状态显示；页签写入 ?tab=，按类型不同——
 * 支付渠道：概览、技术账号、MID、协议配置、沙箱验证、变更记录；服务渠道：概览、凭据、协议配置、沙箱验证、调用授权、变更记录。
 */
function ChannelDetail({ channelId, resources, onAuthorizationChanged }: Props & { channelId: string }) {
  const t = useT('channel');
  const location = useLocation(); const navigate = useNavigate();
  const detail = useDetail(channelPaths.channel(channelId), decodeChannel);
  const [step, setStep] = useState<DetailStep>(null);
  const [revision, setRevision] = useState(0);
  const channel = detail.data;
  const service = channel?.channelType === 'SERVICE';
  const accounts = useChannelAccounts(channelId, revision);
  const counts = useChannelCounts(channel, revision);
  const grants = useChannelGrants(channelId, service);
  const decodeImplementations = useMemo(() => decodeImplementationList, []);
  const implementations = useOptionalDetail(channel ? `${integrationPath}/${channel.channelCode}/implementations` : null, decodeImplementations);
  const implemented: ImplementedApi[] | undefined = implementations.data?.map(item => ({ standardApiCode: item.standardApiCode, standardApiName: item.standardApiName }));
  const canManage = hasButton(resources, channelButtons.channelManage);
  const canConfigure = hasButton(resources, channelButtons.accountCredential);
  const canGrant = hasButton(resources, channelButtons.callGrantManage);
  const canProtocol = hasButton(resources, manageProtocol);
  if (detail.loading && !channel) return <Loading />;
  if (detail.failure || !channel) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const routes = workspaceRoutes(channel.channelId);
  const tab: DetailTab = detailTab(channel.channelType, new URLSearchParams(location.search).get('tab'));
  const reload = () => { setStep(null); detail.refresh(); setRevision(v => v + 1); grants.refresh(); implementations.refresh(); };
  const tabCount = (key: DetailTab) => key === 'accounts' ? counts?.accountCount : key === 'mids' ? counts?.midCount
    : key === 'grants' ? counts?.activeGrantCount ?? undefined : undefined;
  return <>
    <div className="page-heading">
      <div className="channel-title"><h1>{channel.channelCode} · {channel.channelName}</h1><ChannelTypeTag type={channel.channelType} /><StatusTag status={channel.status} /></div>
      <div className="channel-actions">
        {editable(channel.status, canManage) && <Button onClick={() => setStep({ kind: 'edit' })}>{t('protocol.edit')}</Button>}
        {lifecycleActions(channel.status, canManage).map(action => <Button key={action.targetStatus} danger={action.danger}
          onClick={() => setStep({ kind: 'status', action })}>{action.label}</Button>)}
        <Button onClick={() => void navigate(channelRoutes.channels)}>{t('channels.backToList')}</Button>
      </div>
    </div>
    <Summary items={[
      { label: t('channels.code'), value: <span className="mono">{channel.channelCode}</span> },
      { label: t('capability.type'), value: t('channels.typeImmutable', { type: channelTypeLabel(channel.channelType) }) },
      { label: t('channels.id'), value: <span className="mono">{channel.channelId}</span> },
      { label: t('channels.configVersion'), value: `v${channel.version}` },
      { label: t('grants.created'), value: localTime(channel.createTime) },
      { label: t('protocol.column.updated'), value: localTime(channel.updateTime) },
    ]} />
    <Tabs activeKey={tab} onChange={key => void navigate(routes.tab(key as DetailTab))}
      tabBarExtraContent={tab === 'accounts' && editable(channel.status, canManage) && <Button type="primary" onClick={() => setStep({ kind: 'account' })}>{t('channels.createAccount')}</Button>}
      items={detailTabs[channel.channelType].map(key => {
        const count = tabCount(key);
        return { key, label: count === undefined ? detailTabLabel(key) : <span>{detailTabLabel(key)} <span className="channel-tab-count">{count}</span></span> };
      })} />
    {tab === 'overview' && <ChannelOverview channel={channel} canManage={canManage} implemented={implemented} accounts={accounts} grants={grants.grants}
      midCount={counts?.midCount ?? null} onEditEndpoints={() => setStep({ kind: 'endpoints' })} />}
    {tab === 'accounts' && <AccountTab key={revision} channel={channel} canManage={canManage} canConfigure={canConfigure}
      onEdit={account => setStep({ kind: 'account', account })} onConfigure={account => setStep({ kind: 'configure', account })}
      onStatus={(account, action) => setStep({ kind: 'accountStatus', account, action })}
      onChanges={account => void navigate(routes.changes('CHANNEL_ACCOUNT', account.channelAccountId))} />}
    {tab === 'credential' && (accounts ? <ChannelCredential channel={channel} accounts={accounts} canManage={canManage} canConfigure={canConfigure}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={reload} /> : <Loading />)}
    {tab === 'mids' && <MidTab key={revision} channelId={channelId} />}
    {tab === 'protocol' && <ChannelProtocol channel={channel} canManage={canProtocol} implementationCount={implementations.data?.length}
      onAuthorizationChanged={onAuthorizationChanged} onImplementationsChanged={implementations.refresh} />}
    {tab === 'verify' && <ExternalApiFrame><VerifyTab key={location.search} channelId={channel.channelId} code={channel.channelCode} canManage={canProtocol}
      onAuthorizationChanged={onAuthorizationChanged} /></ExternalApiFrame>}
    {tab === 'grants' && <ChannelGrants channel={channel} implemented={implemented} grants={grants} canGrant={canGrant} onAuthorizationChanged={onAuthorizationChanged}
      onChanged={() => { setRevision(v => v + 1); grants.refresh(); }} />}
    {tab === 'changes' && <ChannelChanges channel={channel} accounts={accounts} grants={grants.grants} />}
    {step?.kind === 'edit' && <EditChannelModal channel={channel} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onSaved={reload} />}
    {step?.kind === 'status' && <ChannelStatusModal channel={channel} action={step.action} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={reload} />}
    {step?.kind === 'endpoints' && <EndpointEditorModal channel={channel} onClose={() => setStep(null)} onReload={() => { setStep(null); detail.refresh(); }}
      onAuthorizationChanged={onAuthorizationChanged} onSaved={reload} />}
    {step?.kind === 'account' && <AccountModal channel={channel} account={step.account} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged} onSaved={reload} />}
    {step?.kind === 'configure' && <AccountConfigurationModal account={step.account} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged} onSaved={reload} />}
    {step?.kind === 'accountStatus' && <StatusChangeModal title={t('channels.accountStatusTitle', { action: step.action.label, name: step.account.accountName })}
      path={channelPaths.accountStatus(step.account.channelAccountId)} targetStatus={step.action.targetStatus} actionLabel={step.action.label}
      description={step.action.targetStatus === 'ACTIVE' ? t('channels.accountActivateNote')
        : step.action.targetStatus === 'CLOSED' ? t('channels.accountCloseNote') : undefined}
      decode={value => decodeAccount(value, step.account.channelAccountId)} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={reload} />}
  </>;
}

/** 修改渠道名称与描述；编码与类型不可改。 */
function EditChannelModal({ channel, onSaved, ...base }: Base & { channel: Channel; onSaved: () => void }) {
  const t = useT('channel');
  const [form] = Form.useForm<{ channelName: string; description?: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const saved = await command.run(channelPaths.channel(channel.channelId), 'PUT', { channelName: values.channelName.trim(),
      description: values.description?.trim() || null }, value => decodeChannel(value, channel.channelId), t('channels.saveFailed'));
    if (saved) onSaved();
  };
  return <CommandModal title={t('channels.editTitle', { code: channel.channelCode })} command={command} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-edit" layout="vertical" initialValues={{ channelName: channel.channelName, description: channel.description ?? '' }}>
      <Form.Item label={t('capability.type')}><Input value={t('channels.typeImmutableAfterCreate', { type: channelTypeLabel(channel.channelType) })} disabled /></Form.Item>
      <Form.Item name="channelName" label={t('form.name')} rules={[{ required: true, whitespace: true, message: t('form.required') }, { max: 255, message: t('form.maxLength', { max: 255 }) }]}>
        <Input maxLength={255} /></Form.Item>
      <Form.Item name="description" label={t('overview.description')} rules={[{ max: 512, message: t('form.maxLength', { max: 512 }) }]}><Input.TextArea rows={3} maxLength={512} showCount /></Form.Item>
    </Form>
  </CommandModal>;
}

/** 新建或编辑技术账号（支付渠道）：OE 创建后不可改；备注 ≤512。 */
function AccountModal({ channel, account, onSaved, ...base }: Base & { channel: Channel; account?: ChannelAccountSummary; onSaved: () => void }) {
  const t = useT('channel'), tCommon = useT('common');
  const [form] = Form.useForm<{ entityNo: string; accountName: string; remark?: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const body = { accountName: values.accountName.trim(), remark: values.remark?.trim() || null };
    const saved = account
      ? await command.run(channelPaths.account(account.channelAccountId), 'PUT', body, value => decodeAccount(value, account.channelAccountId), t('channels.accountSaveFailed'))
      : await command.run(channelPaths.accounts, 'POST', { channelId: channel.channelId, entityNo: values.entityNo, ...body },
        value => decodeAccount(value), t('channels.accountCreateFailed'));
    if (saved) onSaved();
  };
  return <CommandModal title={account ? t('channels.editAccountTitle', { name: account.accountName }) : t('channels.createAccountTitle', { code: channel.channelCode })} command={command}
    okText={account ? tCommon('action.save') : t('channels.createOk')} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-account" layout="vertical" initialValues={account ? { entityNo: account.entityNo, accountName: account.accountName } : undefined}>
      <Form.Item name="entityNo" label="OE" normalize={(v?: string) => v?.trim().toUpperCase()} extra={t('channels.immutable')}
        rules={[{ required: true, message: t('form.required') }, { pattern: entityNoPattern, message: t('channels.entityNoPattern') }]}>
        <Input maxLength={5} disabled={!!account} placeholder="AE001" /></Form.Item>
      <Form.Item name="accountName" label={t('channels.accountName')} rules={[{ required: true, whitespace: true, message: t('form.required') }, { max: 128, message: t('form.maxLength', { max: 128 }) }]}>
        <Input maxLength={128} /></Form.Item>
      <Form.Item name="remark" label={t('form.remarkOptional')} rules={[{ max: 512, message: t('form.maxLength', { max: 512 }) }]}><Input.TextArea rows={2} maxLength={512} /></Form.Item>
    </Form>
    {!account && <ChannelNote>{t('channels.accountCreateNote')}</ChannelNote>}
  </CommandModal>;
}

/** 技术账号标签页（支付渠道）：配置列显示 Token 末 4 位；编辑 / 状态需「维护」，配置需「账号凭据」。 */
function AccountTab({ channel, canManage, canConfigure, onEdit, onConfigure, onStatus, onChanges }: {
  channel: Channel; canManage: boolean; canConfigure: boolean; onEdit: (account: ChannelAccountSummary) => void;
  onConfigure: (account: ChannelAccountSummary) => void; onStatus: (account: ChannelAccountSummary, action: LifecycleAction) => void;
  onChanges: (account: ChannelAccountSummary) => void;
}) {
  const t = useT('channel');
  const page = usePaging<ChannelAccountSummary>(channelPaths.accounts, decodeAccountPage, { channelId: channel.channelId }, { notifyOnError: false });
  return <>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<ChannelAccountSummary> rowKey="channelAccountId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1000 }} locale={{ emptyText: t('channels.accountsEmpty') }} columns={[
          { title: t('channels.account'), width: 220, render: (_, row) => <div className="channel-cell"><span>{row.accountName}</span>
            <span className="channel-cell-meta">ID {row.channelAccountId}</span></div> },
          { title: 'OE', width: 80, render: (_, row) => row.entityNo ?? '—' },
          { title: t('grants.status'), width: 120, render: (_, row) => <StatusTag status={row.status} /> },
          { title: t('channels.configuration'), width: 150, render: (_, row) => row.configured ? <div className="channel-cell"><span>{t('accountConfig.configured')}</span>
            <span className="channel-cell-meta">{tokenSuffixLabel(row)}</span></div> : <Tag color="red">{t('account.notConfigured')}</Tag> },
          { title: t('channels.updatedAt'), width: 150, render: (_, row) => localTime(row.updateTime) },
          { title: t('grants.actions'), width: 240, render: (_, row) => {
            const action = primaryLifecycleAction(row.status, canManage);
            const close = lifecycleActions(row.status, canManage).find(a => a.targetStatus === 'CLOSED');
            return <span className="channel-actions">
              {editable(row.status, canManage) && <Button type="link" size="small" onClick={() => onEdit(row)}>{t('protocol.edit')}</Button>}
              {editable(row.status, canConfigure) && <Button type="link" size="small" onClick={() => onConfigure(row)}>{t('channels.configuration')}</Button>}
              {action && <Button type="link" size="small" onClick={() => onStatus(row, action)}>{action.label}</Button>}
              {close && <Button type="link" size="small" danger onClick={() => onStatus(row, close)}>{close.label}</Button>}
              <Button type="link" size="small" onClick={() => onChanges(row)}>{t('channels.history')}</Button>
            </span>;
          } },
        ]} />}
    <ChannelNote>{t('channels.accountsNote')}</ChannelNote>
  </>;
}

/** MID 标签页：本渠道 MID 摘要，点击进入 MID 详情维护。 */
function MidTab({ channelId }: { channelId: string }) {
  const t = useT('channel');
  const navigate = useNavigate();
  const page = usePaging<ChannelMidSummary>(channelPaths.mids, decodeMidPage, { channelId }, { notifyOnError: false });
  return page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
    : <Table<ChannelMidSummary> rowKey="midId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
      scroll={{ x: 900 }} locale={{ emptyText: t('channels.midsEmpty') }}
      onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(channelRoutes.mid(row.midId)) })} columns={[
        { title: t('channels.externalMid'), width: 200, render: (_, row) => <div className="channel-cell"><span>{row.externalMidCode}</span>
          <span className="channel-cell-meta">ID {row.midId}</span></div> },
        { title: 'OE', width: 80, dataIndex: 'entityNo' },
        { title: t('channels.currentAccount'), width: 200, dataIndex: 'accountName' },
        { title: t('grants.status'), width: 120, render: (_, row) => <StatusTag status={row.status} /> },
        { title: t('channels.capabilityGroup'), width: 120, render: (_, row) => t('channels.capabilityGroups', { total: row.capabilityGroupCount, enabled: row.enabledCapabilityGroupCount }) },
        { title: t('channels.activatedAt'), width: 150, render: (_, row) => localTime(row.activatedAt) },
      ]} />;
}

/**
 * 协议配置与沙箱验证子页：先读取渠道详情（编码与出站地址），再复用原接入方页的编辑器。
 * 编辑器内的管理接口仍为 /external-api-admin/v1/integrations/{渠道编码}/…（路径与字段名保持不变）。
 */
function ChannelChildPage({ page, resources, onAuthorizationChanged }: Props & { page: Extract<ChannelPage, { kind: 'protocol' | 'verify' }> }) {
  const detail = useDetail(channelPaths.channel(page.channelId), decodeChannel);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const channel = detail.data;
  const common = { resources, onAuthorizationChanged, channelId: channel.channelId, integrationCode: channel.channelCode };
  if (page.kind === 'protocol' && page.section === 'implementations') return <FlowEditor {...common} standardApiCode={page.code!} />;
  let child;
  if (page.kind === 'protocol') {
    child = page.section === 'apis'
      ? <IntegrationApiEditor resources={resources} onAuthorizationChanged={onAuthorizationChanged} apiCode={page.code}
        channel={{ channelId: channel.channelId, channelCode: channel.channelCode, endpoints: channel.endpoints }} />
      : <ScriptEditor {...common} scriptCode={page.code} />;
  } else if (page.section === 'accounts') child = <VerifyAccountEditor {...common} accountCode={page.code} />;
  else if (page.section === 'cases') child = <VerifyCaseEditor {...common} caseCode={page.code} />;
  else child = <VerifyRunDetail channelId={channel.channelId} integrationCode={channel.channelCode} runCode={page.code!} />;
  return <ExternalApiFrame>{child}</ExternalApiFrame>;
}
