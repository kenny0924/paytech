import { useState } from 'react';
import { Button, Form, Input, Select, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  channelButtons, channelCodePattern, channelPaths, channelRoutes, decodeAccount, decodeAccountPage, decodeChannel, decodeChannelPage, decodeMidPage,
  editable, entityNoPattern, hasButton, isId, lifecycleActions, lifecycleStatuses, lifecycleStatusLabels, listFilters, localTime, primaryLifecycleAction,
  tokenSuffixLabel,
} from '../channel/channel';
import type { Channel, ChannelAccountSummary, ChannelMidSummary, ChannelSummary, LifecycleAction } from '../channel/channel';
import { AccountConfigurationModal } from '../channel/AccountConfigurationModal';
import { ChangeList, ChannelNote, CommandModal, StatusChangeModal, StatusTag, Summary } from '../channel/ChannelParts';
import { useChannelCommand } from '../channel/useChannelCommand';
import { integrationRoute } from '../externalApi/externalApiModel';
import '../configuration/configuration.css';
import '../channel/channel.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Base { onClose: () => void; onAuthorizationChanged: () => void }

/** 渠道与账号：/channel/channels 列表与新建，/channel/channels/:channelId 详情（技术账号 / MID / 变更记录）。 */
export function ChannelChannels(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && !isId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return <div className="configuration-page channel-page">
    {parts.length === 1 ? <ChannelDetail key={parts[0]} channelId={parts[0]} {...props} /> : <ChannelList {...props} />}
  </div>;
}

interface FilterValues { keyword?: string; status?: string }
type ChannelStep = { kind: 'status'; channel: Pick<Channel, 'channelId' | 'channelCode'>; action: LifecycleAction } | { kind: 'create' } | null;

/** 渠道列表（画板 01）：编码 / 名称前缀与状态筛选；行内主动作只给当前状态允许的下一步。 */
function ChannelList({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const [form] = Form.useForm<FilterValues>();
  const [step, setStep] = useState<ChannelStep>(null);
  const page = usePaging<ChannelSummary>(channelPaths.channels, decodeChannelPage, {}, { notifyOnError: false });
  const canManage = hasButton(resources, channelButtons.channelManage);
  return <>
    <div className="page-heading"><h1>渠道与账号</h1></div>
    <div className="channel-toolbar">
      <Form form={form} name="channel-filter" layout="vertical" onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
        <div className="channel-filters">
          <Form.Item name="keyword" label="渠道编码 / 名称"><Input allowClear maxLength={64} placeholder="输入前缀" /></Form.Item>
          <Form.Item name="status" label="状态"><Select allowClear placeholder="全部"
            options={lifecycleStatuses.map(value => ({ value, label: `${value} · ${lifecycleStatusLabels[value]}` }))} /></Form.Item>
          <div className="channel-filter-actions"><Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
        </div>
      </Form>
      {canManage && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>新建渠道</Button>}</div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<ChannelSummary> rowKey="channelId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 980 }} locale={{ emptyText: '没有符合条件的渠道' }} columns={[
          { title: '渠道编码', width: 180, render: (_, row) => <Button type="link" size="small" onClick={() => void navigate(channelRoutes.channel(row.channelId))}>
            {row.channelCode}</Button> },
          { title: '名称', width: 220, dataIndex: 'channelName' },
          { title: '状态', width: 120, render: (_, row) => <StatusTag status={row.status} /> },
          { title: '账号', width: 80, dataIndex: 'accountCount' },
          { title: 'MID', width: 80, dataIndex: 'midCount' },
          { title: '更新时间', width: 150, render: (_, row) => localTime(row.updateTime) },
          { title: '操作', width: 140, render: (_, row) => {
            const action = primaryLifecycleAction(row.status, canManage);
            return <span className="channel-actions">
              <Button type="link" size="small" onClick={() => void navigate(channelRoutes.channel(row.channelId))}>详情</Button>
              {action && <Button type="link" size="small" onClick={() => setStep({ kind: 'status', channel: row, action })}>{action.label}</Button>}
            </span>;
          } },
        ]} />}
    <ChannelNote>账号 / MID 数为非 CLOSED 计数；激活时 channel-connector 实时核对 Platform 是否存在同编码接入方。没有「维护」按钮权限时，新建与状态操作不显示。</ChannelNote>
    {step?.kind === 'create' && <CreateChannelModal onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={channel => void navigate(channelRoutes.channel(channel.channelId))} />}
    {step?.kind === 'status' && <ChannelStatusModal channel={step.channel} action={step.action} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setStep(null)} onDone={() => { setStep(null); page.refresh(); }} />}
  </>;
}

/** 渠道状态变更：激活前置条件为 Platform 存在同编码接入方；关闭要求其下账号与 MID 已全部关闭。 */
function ChannelStatusModal({ channel, action, onDone, ...base }: Base & { channel: Pick<Channel, 'channelId' | 'channelCode'>; action: LifecycleAction; onDone: (channel: Channel) => void }) {
  const description = action.targetStatus === 'ACTIVE' ? '激活前 channel-connector 向 Platform 核对同编码接入方；不存在时拒绝并显示原因。'
    : action.targetStatus === 'CLOSED' ? '关闭为终态，不可恢复；渠道下仍有未关闭的技术账号或 MID 时会被拒绝。' : '暂停后该渠道的 MID 不再参与路由候选。';
  return <StatusChangeModal title={`${action.label}渠道 · ${channel.channelCode}`} path={channelPaths.channelStatus(channel.channelId)}
    targetStatus={action.targetStatus} actionLabel={action.label} description={description} decode={value => decodeChannel(value, channel.channelId)}
    onDone={onDone} {...base} />;
}

/** 新建渠道（画板 03）：编码创建后不可改，须与 Platform 接入方编码一致。 */
function CreateChannelModal({ onCreated, ...base }: Base & { onCreated: (channel: Channel) => void }) {
  const [form] = Form.useForm<{ channelCode: string; channelName: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const channel = await command.run(channelPaths.channels, 'POST', { channelCode: values.channelCode, channelName: values.channelName.trim() },
      value => decodeChannel(value), '渠道创建失败');
    if (channel) onCreated(channel);
  };
  return <CommandModal title="新建渠道" command={command} okText="创建" onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-create" layout="vertical">
      <Form.Item name="channelCode" label="渠道编码" normalize={(v?: string) => v?.trim().toUpperCase()} extra="创建后不可修改；须与 Platform 接入方编码一致"
        rules={[{ required: true, message: '必填' }, { pattern: channelCodePattern, message: '大写字母开头，大写字母、数字或下划线，2–64 位' }]}>
        <Input maxLength={64} placeholder="PAYERMAX" /></Form.Item>
      <Form.Item name="channelName" label="名称" rules={[{ required: true, whitespace: true, message: '必填' }, { max: 255, message: '最长 255' }]}>
        <Input maxLength={255} /></Form.Item>
    </Form>
    <ChannelNote>新建为 DRAFT。激活前需要：Platform 已登记同编码接入方；激活后才能激活其下技术账号与 MID。</ChannelNote>
  </CommandModal>;
}

type DetailStep = { kind: 'edit' } | { kind: 'status'; action: LifecycleAction } | { kind: 'account'; account?: ChannelAccountSummary }
  | { kind: 'configure'; account: ChannelAccountSummary } | { kind: 'accountStatus'; account: ChannelAccountSummary; action: LifecycleAction } | null;

/** 渠道详情（画板 02 / 05 / 13）：标题动作按当前状态显示；三个标签页：技术账号、MID、变更记录。 */
function ChannelDetail({ channelId, resources, onAuthorizationChanged }: Props & { channelId: string }) {
  const navigate = useNavigate();
  const detail = useDetail(channelPaths.channel(channelId), decodeChannel);
  const [step, setStep] = useState<DetailStep>(null);
  const [tab, setTab] = useState('accounts');
  const [revision, setRevision] = useState(0);
  const [changeTarget, setChangeTarget] = useState<{ type: 'CHANNEL' | 'CHANNEL_ACCOUNT'; id: string; label: string }>({ type: 'CHANNEL', id: channelId, label: '渠道' });
  const canManage = hasButton(resources, channelButtons.channelManage);
  const canConfigure = hasButton(resources, channelButtons.accountCredential);
  const canViewIntegration = resources.some(resource => resource.componentKey === 'OPERATIONS_EXTERNAL_INTEGRATIONS');
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const channel = detail.data;
  const reload = () => { setStep(null); detail.refresh(); setRevision(v => v + 1); };
  return <>
    <div className="page-heading">
      <div className="channel-title"><h1>{channel.channelCode} · {channel.channelName}</h1><StatusTag status={channel.status} /></div>
      <div className="channel-actions">
        {editable(channel.status, canManage) && <Button onClick={() => setStep({ kind: 'edit' })}>编辑</Button>}
        {lifecycleActions(channel.status, canManage).map(action => <Button key={action.targetStatus} danger={action.danger}
          onClick={() => setStep({ kind: 'status', action })}>{action.label}</Button>)}
        <Button onClick={() => void navigate(channelRoutes.channels)}>返回列表</Button>
      </div>
    </div>
    <Summary items={[
      { label: '渠道编码', value: <span className="mono">{channel.channelCode}</span> },
      { label: '名称', value: channel.channelName },
      { label: '渠道 ID', value: <span className="mono">{channel.channelId}</span> },
      { label: 'Platform 接入方', value: canViewIntegration
        ? <Button type="link" size="small" className="mono" onClick={() => void navigate(`${integrationRoute}/${encodeURIComponent(channel.channelCode)}`)}>
          {channel.channelCode}（激活时核对）</Button>
        : <span className="mono">{channel.channelCode}（激活时核对）</span> },
      { label: '创建', value: localTime(channel.createTime) },
      { label: '更新', value: localTime(channel.updateTime) },
    ]} />
    <Tabs activeKey={tab} onChange={setTab}
      tabBarExtraContent={tab === 'accounts' && editable(channel.status, canManage) && <Button type="primary" onClick={() => setStep({ kind: 'account' })}>新建账号</Button>}
      items={[
      { key: 'accounts', label: '技术账号', children: <AccountTab key={revision} channel={channel} canManage={canManage} canConfigure={canConfigure}
        onEdit={account => setStep({ kind: 'account', account })} onConfigure={account => setStep({ kind: 'configure', account })}
        onStatus={(account, action) => setStep({ kind: 'accountStatus', account, action })}
        onChanges={account => { setChangeTarget({ type: 'CHANNEL_ACCOUNT', id: account.channelAccountId, label: `账号 ${account.accountName}` }); setTab('changes'); }} /> },
      { key: 'mids', label: 'MID', children: <MidTab key={revision} channelId={channelId} /> },
      { key: 'changes', label: '变更记录', children: <>
        <div className="channel-section-head"><h2>变更记录 · {changeTarget.label}</h2><span className="channel-spacer" />
          {changeTarget.type !== 'CHANNEL' && <Button size="small" onClick={() => setChangeTarget({ type: 'CHANNEL', id: channelId, label: '渠道' })}>查看渠道记录</Button>}</div>
        <ChangeList key={`${changeTarget.type}:${changeTarget.id}:${revision}`} targetType={changeTarget.type} targetId={changeTarget.id} /></> },
    ]} />
    {step?.kind === 'edit' && <EditChannelModal channel={channel} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onSaved={reload} />}
    {step?.kind === 'status' && <ChannelStatusModal channel={channel} action={step.action} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={reload} />}
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

/** 修改渠道名称；编码不可改。 */
function EditChannelModal({ channel, onSaved, ...base }: Base & { channel: Channel; onSaved: () => void }) {
  const [form] = Form.useForm<{ channelName: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const saved = await command.run(channelPaths.channel(channel.channelId), 'PUT', { channelName: values.channelName.trim() },
      value => decodeChannel(value, channel.channelId), '渠道保存失败');
    if (saved) onSaved();
  };
  return <CommandModal title={`编辑渠道 · ${channel.channelCode}`} command={command} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-edit" layout="vertical" initialValues={{ channelName: channel.channelName }}>
      <Form.Item name="channelName" label="名称" rules={[{ required: true, whitespace: true, message: '必填' }, { max: 255, message: '最长 255' }]}>
        <Input maxLength={255} /></Form.Item>
    </Form>
  </CommandModal>;
}

/** 新建或编辑技术账号：OE 创建后不可改；备注 ≤512。 */
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

/** 技术账号标签页（画板 02）：配置列显示 Token 末 4 位；编辑 / 状态需「维护」，配置需「账号凭据」。 */
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
          { title: 'OE', width: 80, dataIndex: 'entityNo' },
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
