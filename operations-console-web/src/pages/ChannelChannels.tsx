import { useEffect, useMemo, useState } from 'react';
import { Button, Form, Input, Segmented, Select, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import { requestAllPages, requestPage } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
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
  channelButtons, channelCodePattern, channelPaths, channelRoutes, channelTypeLabels, decodeAccount, decodeAccountPage, decodeChannel,
  decodeChannelPage, decodeMidPage, editable, entityNoPattern, hasButton, lifecycleActions, lifecycleStatuses, lifecycleStatusLabels, listFilters, localTime,
  primaryLifecycleAction, tokenSuffixLabel,
} from '../channel/channel';
import type { Channel, ChannelAccountSummary, ChannelMidSummary, ChannelSummary, ChannelType, LifecycleAction } from '../channel/channel';
import {
  activeGrantsByCaller, channelListFilters, channelTypeFilter, detailTab, detailTabLabels, detailTabs, parseChannelPath, typeCountsSequentially,
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
    <div className="page-heading"><h1>渠道</h1>{canManage && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>新建渠道</Button>}</div>
    <div className="channel-toolbar">
      <Segmented value={type ?? 'ALL'} onChange={value => void navigate(typeSegmentSearch(location.search, value === 'ALL' ? null : value as ChannelType))}
        options={[segment('全部', 'ALL'), segment('支付渠道', 'PAYMENT'), segment('服务渠道', 'SERVICE')]} />
      <Form form={form} name="channel-filter" layout="inline" initialValues={{ keyword: filters.keyword, status: filters.status }}
        onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
        <Form.Item name="keyword"><Input allowClear maxLength={64} placeholder="编码或名称" style={{ width: 200 }} /></Form.Item>
        <Form.Item name="status"><Select allowClear placeholder="全部状态" style={{ width: 160 }}
          options={lifecycleStatuses.map(value => ({ value, label: `${value} · ${lifecycleStatusLabels[value]}` }))} /></Form.Item>
        <Button htmlType="submit">查询</Button>
        {(filters.keyword || filters.status) && <Button type="link" onClick={() => { form.resetFields(); page.applyFilters({}); }}>清除条件</Button>}
      </Form>
    </div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<ChannelSummary> rowKey="channelId" size="middle" loading={page.loading} dataSource={page.data.items}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} scroll={{ x: 1180 }} locale={{ emptyText: '没有符合条件的渠道' }} columns={[
          { title: '渠道编码', width: 170, render: (_, row) => <Button type="link" size="small" className="mono" onClick={() => void navigate(channelRoutes.channel(row.channelId))}>
            {row.channelCode}</Button> },
          { title: '名称', width: 190, dataIndex: 'channelName', ellipsis: true },
          { title: '类型', width: 80, render: (_, row) => <ChannelTypeTag type={row.channelType} short /> },
          { title: '状态', width: 120, render: (_, row) => <StatusTag status={row.status} /> },
          { title: '出站地址', width: 100, render: (_, row) => row.endpointCount ? `${row.endpointCount} 个` : <span className="channel-cell-meta">未配置</span> },
          { title: '账号 / MID', width: 140, render: (_, row) => row.channelType === 'SERVICE'
            ? row.accountCount ? '已建凭据' : <span className="channel-cell-meta">凭据未配置</span> : `${row.accountCount} / ${row.midCount}` },
          { title: '调用授权', width: 100, render: (_, row) => row.channelType === 'SERVICE' ? `${row.activeGrantCount ?? 0} 条` : '—' },
          { title: '更新时间', width: 150, render: (_, row) => localTime(row.updateTime) },
          { title: '操作', width: 130, render: (_, row) => {
            const action = primaryLifecycleAction(row.status, canManage);
            return <span className="channel-actions">
              <Button type="link" size="small" onClick={() => void navigate(channelRoutes.channel(row.channelId))}>详情</Button>
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
  return <Tag color={type === 'SERVICE' ? 'purple' : 'blue'}>{short ? (type === 'SERVICE' ? '服务' : '支付') : channelTypeLabels[type]}</Tag>;
}

/**
 * 渠道状态变更：两类渠道的激活前置都只要求出站地址已配置；关闭要求其下账号与 MID 已全部关闭。
 * 暂停服务渠道（画板 11）须填写原因，并列出受影响的 ACTIVE 授权。
 */
function ChannelStatusModal({ channel, action, onDone, ...base }: Base & { channel: StatusTarget; action: LifecycleAction; onDone: (channel: Channel) => void }) {
  const service = channel.channelType === 'SERVICE';
  const stopping = action.targetStatus === 'SUSPENDED' || action.targetStatus === 'CLOSED';
  const grants = useChannelGrants(channel.channelId, service && stopping);
  const affected = activeGrantsByCaller(grants.grants ?? []);
  const description = action.targetStatus === 'ACTIVE' ? '激活前置：出站地址已配置（支付渠道与服务渠道相同）；未配置时 Owner 拒绝并显示原因。凭据与标准 API 实现不阻止激活。'
    : action.targetStatus === 'CLOSED' ? '关闭为终态，不可恢复；渠道下仍有未关闭的技术账号、凭据或 MID 时会被拒绝。'
      : service ? '暂停后通用入口对该渠道的所有调用立即被拒绝（渠道非 ACTIVE），不发出外部请求。授权与凭据保留，恢复后无需重新配置。'
        : '暂停后该渠道的 MID 不再参与路由候选。';
  return <StatusChangeModal title={`${action.label}渠道 · ${channel.channelCode}`} path={channelPaths.channelStatus(channel.channelId)}
    targetStatus={action.targetStatus} actionLabel={action.targetStatus === 'SUSPENDED' ? '暂停渠道' : action.label} description={description}
    reasonRequired={service && action.targetStatus === 'SUSPENDED'} decode={value => decodeChannel(value, channel.channelId)} onDone={onDone} {...base}>
    {service && stopping && <div className="channel-affected">
      <strong>受影响的 ACTIVE 授权（{grants.grants ? affected.reduce((sum, item) => sum + item.standardApiCodes.length, 0) : '…'}）</strong>
      {grants.failure ? <span className="channel-cell-meta">授权读取失败，不影响本次操作</span>
        : affected.length === 0 && grants.grants ? <span className="channel-cell-meta">无</span>
          : affected.map(item => <div key={item.callerService}><span className="mono">{item.callerService}</span>
            <span className="channel-cell-meta"> {item.standardApiCodes.join('、')}</span></div>)}
      <div className="channel-cell-meta">原因写入变更记录；调用方看到的拒绝信息不包含原因。</div>
    </div>}
  </StatusChangeModal>;
}

/** 新建渠道（画板 02）：先选类型（创建后不可改），编码 [A-Z][A-Z0-9_]{1,63} 全局唯一，可填描述。 */
function CreateChannelModal({ onCreated, ...base }: Base & { onCreated: (channel: Channel) => void }) {
  const [form] = Form.useForm<{ channelType: ChannelType; channelCode: string; channelName: string; description?: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const channelType = Form.useWatch('channelType', form);
  const submit = async () => {
    const values = await form.validateFields();
    const channel = await command.run(channelPaths.channels, 'POST', { channelType: values.channelType, channelCode: values.channelCode,
      channelName: values.channelName.trim(), description: values.description?.trim() || null }, value => decodeChannel(value), '渠道创建失败');
    if (channel) onCreated(channel);
  };
  return <CommandModal title="新建渠道" command={command} okText="创建" width={720} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-create" layout="vertical" initialValues={{ channelType: 'PAYMENT' }}>
      <Form.Item name="channelType" label={<span>渠道类型 <span className="channel-cell-meta">创建后不可修改</span></span>} rules={[{ required: true, message: '请选择类型' }]}>
        <TypeCards />
      </Form.Item>
      <Form.Item name="channelCode" label="渠道编码" normalize={(v?: string) => v?.trim().toUpperCase()}
        extra="大写字母开头，仅 A–Z、0–9、_，2–64 位；全局唯一，创建后不可修改。"
        rules={[{ required: true, message: '必填' }, { pattern: channelCodePattern, message: '大写字母开头，大写字母、数字或下划线，2–64 位' }]}>
        <Input maxLength={64} className="mono" placeholder={channelType === 'SERVICE' ? 'GOOGLE_MAPS' : 'PAYERMAX'} /></Form.Item>
      <Form.Item name="channelName" label="名称" rules={[{ required: true, whitespace: true, message: '必填' }, { max: 255, message: '最长 255' }]}>
        <Input maxLength={255} /></Form.Item>
      <Form.Item name="description" label="描述" rules={[{ max: 512, message: '最长 512' }]}>
        <Input.TextArea rows={2} maxLength={512} showCount placeholder="用途、负责人、对接文档链接（选填）" /></Form.Item>
    </Form>
    <ChannelNote>创建后状态为 DRAFT，可先配置协议并跑沙箱验证；配置出站地址后才能激活。{channelType === 'SERVICE'
      ? '服务渠道激活且有授权后，内部服务才能经通用入口调用。' : '支付渠道由 Payment 经支付入口调用。'}</ChannelNote>
  </CommandModal>;
}

const typeCards: { type: ChannelType; summary: string; includes: string[]; excludes: string[] }[] = [
  { type: 'PAYMENT', summary: '收单、退款等支付执行。由 Payment 经支付入口调用，路由按 OE 与 MID 冻结。',
    includes: ['技术账号（绑定 OE，可多个）', 'MID、能力、成本方案', '协议配置与沙箱验证'], excludes: ['调用授权（不适用）'] },
  { type: 'SERVICE', summary: '地图、KYC、短信、汇率等第三方服务。内部服务经通用入口调用，按「调用方 × 标准 API」授权。',
    includes: ['一份凭据（不绑定 OE）', '协议配置与沙箱验证', '调用授权'], excludes: ['MID、能力、成本（不适用）'] },
];

/** 类型卡片二选一（受控于 Form.Item）。 */
function TypeCards({ value, onChange }: { value?: ChannelType; onChange?: (value: ChannelType) => void }) {
  return <div className="channel-type-cards" role="radiogroup">{typeCards.map(card => <button type="button" key={card.type} role="radio"
    aria-checked={value === card.type} className={`channel-type-card${value === card.type ? ' is-selected' : ''}`} onClick={() => onChange?.(card.type)}>
    <div className="channel-type-card-head"><strong>{channelTypeLabels[card.type]}</strong><span className="mono">{card.type}</span></div>
    <p>{card.summary}</p>
    <ul>{card.includes.map(item => <li key={item}>✓ {item}</li>)}{card.excludes.map(item => <li key={item} className="is-excluded">— {item}</li>)}</ul>
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
        {editable(channel.status, canManage) && <Button onClick={() => setStep({ kind: 'edit' })}>编辑</Button>}
        {lifecycleActions(channel.status, canManage).map(action => <Button key={action.targetStatus} danger={action.danger}
          onClick={() => setStep({ kind: 'status', action })}>{action.label}</Button>)}
        <Button onClick={() => void navigate(channelRoutes.channels)}>返回列表</Button>
      </div>
    </div>
    <Summary items={[
      { label: '渠道编码', value: <span className="mono">{channel.channelCode}</span> },
      { label: '类型', value: `${channelTypeLabels[channel.channelType]}（不可修改）` },
      { label: '渠道 ID', value: <span className="mono">{channel.channelId}</span> },
      { label: '配置版本', value: `v${channel.version}` },
      { label: '创建', value: localTime(channel.createTime) },
      { label: '更新', value: localTime(channel.updateTime) },
    ]} />
    <Tabs activeKey={tab} onChange={key => void navigate(routes.tab(key as DetailTab))}
      tabBarExtraContent={tab === 'accounts' && editable(channel.status, canManage) && <Button type="primary" onClick={() => setStep({ kind: 'account' })}>新建账号</Button>}
      items={detailTabs[channel.channelType].map(key => {
        const count = tabCount(key);
        return { key, label: count === undefined ? detailTabLabels[key] : <span>{detailTabLabels[key]} <span className="channel-tab-count">{count}</span></span> };
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
    {step?.kind === 'accountStatus' && <StatusChangeModal title={`${step.action.label}账号 · ${step.account.accountName}`}
      path={channelPaths.accountStatus(step.account.channelAccountId)} targetStatus={step.action.targetStatus} actionLabel={step.action.label}
      description={step.action.targetStatus === 'ACTIVE' ? '激活要求渠道为 ACTIVE 且账号已写入参数与凭据。'
        : step.action.targetStatus === 'CLOSED' ? '关闭为终态；仍有未关闭的 MID 绑定本账号时会被拒绝。' : undefined}
      decode={value => decodeAccount(value, step.account.channelAccountId)} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={reload} />}
  </>;
}

/** 修改渠道名称与描述；编码与类型不可改。 */
function EditChannelModal({ channel, onSaved, ...base }: Base & { channel: Channel; onSaved: () => void }) {
  const [form] = Form.useForm<{ channelName: string; description?: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const saved = await command.run(channelPaths.channel(channel.channelId), 'PUT', { channelName: values.channelName.trim(),
      description: values.description?.trim() || null }, value => decodeChannel(value, channel.channelId), '渠道保存失败');
    if (saved) onSaved();
  };
  return <CommandModal title={`编辑渠道 · ${channel.channelCode}`} command={command} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-edit" layout="vertical" initialValues={{ channelName: channel.channelName, description: channel.description ?? '' }}>
      <Form.Item label="类型"><Input value={`${channelTypeLabels[channel.channelType]}（创建后不可修改）`} disabled /></Form.Item>
      <Form.Item name="channelName" label="名称" rules={[{ required: true, whitespace: true, message: '必填' }, { max: 255, message: '最长 255' }]}>
        <Input maxLength={255} /></Form.Item>
      <Form.Item name="description" label="描述" rules={[{ max: 512, message: '最长 512' }]}><Input.TextArea rows={3} maxLength={512} showCount /></Form.Item>
    </Form>
  </CommandModal>;
}

/** 新建或编辑技术账号（支付渠道）：OE 创建后不可改；备注 ≤512。 */
function AccountModal({ channel, account, onSaved, ...base }: Base & { channel: Channel; account?: ChannelAccountSummary; onSaved: () => void }) {
  const [form] = Form.useForm<{ entityNo: string; accountName: string; remark?: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const body = { accountName: values.accountName.trim(), remark: values.remark?.trim() || null };
    const saved = account
      ? await command.run(channelPaths.account(account.channelAccountId), 'PUT', body, value => decodeAccount(value, account.channelAccountId), '账号保存失败')
      : await command.run(channelPaths.accounts, 'POST', { channelId: channel.channelId, entityNo: values.entityNo, ...body },
        value => decodeAccount(value), '账号创建失败');
    if (saved) onSaved();
  };
  return <CommandModal title={account ? `编辑账号 · ${account.accountName}` : `新建账号 · ${channel.channelCode}`} command={command}
    okText={account ? '保存' : '创建'} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-account" layout="vertical" initialValues={account ? { entityNo: account.entityNo, accountName: account.accountName } : undefined}>
      <Form.Item name="entityNo" label="OE" normalize={(v?: string) => v?.trim().toUpperCase()} extra="创建后不可修改"
        rules={[{ required: true, message: '必填' }, { pattern: entityNoPattern, message: '两位大写字母加三位数字，如 AE001' }]}>
        <Input maxLength={5} disabled={!!account} placeholder="AE001" /></Form.Item>
      <Form.Item name="accountName" label="账号名称" rules={[{ required: true, whitespace: true, message: '必填' }, { max: 128, message: '最长 128' }]}>
        <Input maxLength={128} /></Form.Item>
      <Form.Item name="remark" label="备注（可选）" rules={[{ max: 512, message: '最长 512' }]}><Input.TextArea rows={2} maxLength={512} /></Form.Item>
    </Form>
    {!account && <ChannelNote>新建为 DRAFT 且未配置；写入参数与凭据并在渠道激活后才能激活。</ChannelNote>}
  </CommandModal>;
}

/** 技术账号标签页（支付渠道）：配置列显示 Token 末 4 位；编辑 / 状态需「维护」，配置需「账号凭据」。 */
function AccountTab({ channel, canManage, canConfigure, onEdit, onConfigure, onStatus, onChanges }: {
  channel: Channel; canManage: boolean; canConfigure: boolean; onEdit: (account: ChannelAccountSummary) => void;
  onConfigure: (account: ChannelAccountSummary) => void; onStatus: (account: ChannelAccountSummary, action: LifecycleAction) => void;
  onChanges: (account: ChannelAccountSummary) => void;
}) {
  const page = usePaging<ChannelAccountSummary>(channelPaths.accounts, decodeAccountPage, { channelId: channel.channelId }, { notifyOnError: false });
  return <>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<ChannelAccountSummary> rowKey="channelAccountId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1000 }} locale={{ emptyText: '该渠道还没有技术账号' }} columns={[
          { title: '账号', width: 220, render: (_, row) => <div className="channel-cell"><span>{row.accountName}</span>
            <span className="channel-cell-meta">ID {row.channelAccountId}</span></div> },
          { title: 'OE', width: 80, render: (_, row) => row.entityNo ?? '—' },
          { title: '状态', width: 120, render: (_, row) => <StatusTag status={row.status} /> },
          { title: '配置', width: 150, render: (_, row) => row.configured ? <div className="channel-cell"><span>已配置</span>
            <span className="channel-cell-meta">{tokenSuffixLabel(row)}</span></div> : <Tag color="red">未配置</Tag> },
          { title: '更新时间', width: 150, render: (_, row) => localTime(row.updateTime) },
          { title: '操作', width: 240, render: (_, row) => {
            const action = primaryLifecycleAction(row.status, canManage);
            const close = lifecycleActions(row.status, canManage).find(a => a.targetStatus === 'CLOSED');
            return <span className="channel-actions">
              {editable(row.status, canManage) && <Button type="link" size="small" onClick={() => onEdit(row)}>编辑</Button>}
              {editable(row.status, canConfigure) && <Button type="link" size="small" onClick={() => onConfigure(row)}>配置</Button>}
              {action && <Button type="link" size="small" onClick={() => onStatus(row, action)}>{action.label}</Button>}
              {close && <Button type="link" size="small" danger onClick={() => onStatus(row, close)}>关闭</Button>}
              <Button type="link" size="small" onClick={() => onChanges(row)}>记录</Button>
            </span>;
          } },
        ]} />}
    <ChannelNote>账号 OE 创建后不可改；未配置参数与凭据时不能激活。凭据永不回显，只显示 Token 末 4 位。「激活」被拒绝时在弹窗内显示 Owner 原因。</ChannelNote>
  </>;
}

/** MID 标签页：本渠道 MID 摘要，点击进入 MID 详情维护。 */
function MidTab({ channelId }: { channelId: string }) {
  const navigate = useNavigate();
  const page = usePaging<ChannelMidSummary>(channelPaths.mids, decodeMidPage, { channelId }, { notifyOnError: false });
  return page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
    : <Table<ChannelMidSummary> rowKey="midId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
      scroll={{ x: 900 }} locale={{ emptyText: '该渠道还没有 MID' }}
      onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(channelRoutes.mid(row.midId)) })} columns={[
        { title: '外部 MID', width: 200, render: (_, row) => <div className="channel-cell"><span>{row.externalMidCode}</span>
          <span className="channel-cell-meta">ID {row.midId}</span></div> },
        { title: 'OE', width: 80, dataIndex: 'entityNo' },
        { title: '当前账号', width: 200, dataIndex: 'accountName' },
        { title: '状态', width: 120, render: (_, row) => <StatusTag status={row.status} /> },
        { title: '能力组', width: 120, render: (_, row) => `${row.capabilityGroupCount} 组 · ${row.enabledCapabilityGroupCount} 启用` },
        { title: '激活时间', width: 150, render: (_, row) => localTime(row.activatedAt) },
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
