import { useEffect, useMemo, useState } from 'react';
import { Button, Form, Input, Select, Table, Tabs } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  capabilityStatusLabels, capabilityToggle, channelButtons, channelPaths, channelRoutes, decodeAccountPage, decodeCapability,
  decodeChannel, decodeDefinitions, decodeMid, decodeMidPage, detailValue, editable, entityNoPattern, hasButton, isId, lifecycleActions,
  lifecycleStatuses, lifecycleStatusLabels, listFilters, localTime, primaryLifecycleAction,
} from '../channel/channel';
import type {
  CapabilityDefinition, Channel, ChannelAccountSummary, ChannelMid, ChannelMidSummary, LifecycleAction, MidCapability,
} from '../channel/channel';
import { CapabilityEditor } from '../channel/CapabilityEditor';
import { ChangeList, ChannelNote, ChannelSelect, CommandModal, StatusChangeModal, StatusTag, Summary } from '../channel/ChannelParts';
import { useChannelCommand } from '../channel/useChannelCommand';
import '../configuration/configuration.css';
import '../channel/channel.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Base { onClose: () => void; onAuthorizationChanged: () => void }

/** MID 与能力：/channel/mids 列表与新建，/channel/mids/:midId 详情（能力组与明细）。 */
export function ChannelMids(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && !isId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return <div className="configuration-page channel-page">
    {parts.length === 1 ? <MidDetail key={parts[0]} midId={parts[0]} {...props} /> : <MidList {...props} />}
  </div>;
}

interface FilterValues { channelId?: string; entityNo?: string; status?: string; externalMidCode?: string }
type ListStep = { kind: 'create' } | { kind: 'rebind'; mid: Pick<ChannelMid, 'midId' | 'channelId' | 'channelAccountId' | 'entityNo' | 'externalMidCode'> }
  | { kind: 'status'; mid: Pick<ChannelMid, 'midId' | 'externalMidCode'>; action: LifecycleAction } | null;

/** MID 列表（画板 06）：渠道 / OE / 状态 / 外部 MID 前缀筛选；行内详情、改绑账号与当前状态允许的主动作。 */
function MidList({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const [form] = Form.useForm<FilterValues>();
  const [step, setStep] = useState<ListStep>(null);
  const page = usePaging<ChannelMidSummary>(channelPaths.mids, decodeMidPage, {}, { notifyOnError: false });
  const canManage = hasButton(resources, channelButtons.midManage);
  const done = () => { setStep(null); page.refresh(); };
  return <>
    <div className="page-heading"><h1>MID 与能力</h1></div>
    <div className="channel-toolbar">
      <Form form={form} name="channel-mid-filter" layout="vertical" onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
        <div className="channel-filters">
          <Form.Item name="channelId" label="渠道"><ChannelSelect channelType="PAYMENT" /></Form.Item>
          <Form.Item name="entityNo" label="OE" normalize={(v?: string) => v?.trim().toUpperCase()} rules={[{ pattern: entityNoPattern, message: '如 AE001' }]}>
            <Input allowClear maxLength={5} placeholder="全部" /></Form.Item>
          <Form.Item name="status" label="状态"><Select allowClear placeholder="全部"
            options={lifecycleStatuses.map(value => ({ value, label: `${value} · ${lifecycleStatusLabels[value]}` }))} /></Form.Item>
          <Form.Item name="externalMidCode" label="外部 MID" rules={[{ max: 128, message: '最长 128' }]}>
            <Input allowClear maxLength={128} placeholder="前缀匹配" /></Form.Item>
          <div className="channel-filter-actions"><Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
        </div>
      </Form>
      {canManage && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>新建 MID</Button>}</div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<ChannelMidSummary> rowKey="midId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1100 }} locale={{ emptyText: '没有符合条件的 MID' }} columns={[
          { title: '外部 MID', width: 200, render: (_, row) => <div className="channel-cell"><span>{row.externalMidCode}</span>
            <span className="channel-cell-meta">ID {row.midId}</span></div> },
          { title: '渠道', width: 130, dataIndex: 'channelCode' },
          { title: 'OE', width: 80, dataIndex: 'entityNo' },
          { title: '当前账号', width: 200, render: (_, row) => <div className="channel-cell"><span>{row.accountName}</span>
            <span className="channel-cell-meta">ID {row.channelAccountId}</span></div> },
          { title: '状态', width: 120, render: (_, row) => <StatusTag status={row.status} /> },
          { title: '能力组', width: 120, render: (_, row) => row.capabilityGroupCount ? `${row.capabilityGroupCount} 组 · ${row.enabledCapabilityGroupCount} 启用` : '0 组' },
          { title: '激活时间', width: 150, render: (_, row) => localTime(row.activatedAt) },
          { title: '操作', width: 200, render: (_, row) => {
            const action = primaryLifecycleAction(row.status, canManage);
            return <span className="channel-actions">
              <Button type="link" size="small" onClick={() => void navigate(channelRoutes.mid(row.midId))}>详情</Button>
              {editable(row.status, canManage) && <Button type="link" size="small" onClick={() => setStep({ kind: 'rebind', mid: row })}>改绑账号</Button>}
              {action && <Button type="link" size="small" onClick={() => setStep({ kind: 'status', mid: row, action })}>{action.label}</Button>}
            </span>;
          } },
        ]} />}
    {step?.kind === 'create' && <CreateMidModal onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={mid => void navigate(channelRoutes.mid(mid.midId))} />}
    {step?.kind === 'rebind' && <RebindModal mid={step.mid} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={done} />}
    {step?.kind === 'status' && <MidStatusModal mid={step.mid} action={step.action} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={done} />}
  </>;
}

function MidStatusModal({ mid, action, onDone, ...base }: Base & { mid: Pick<ChannelMid, 'midId' | 'externalMidCode'>; action: LifecycleAction; onDone: () => void }) {
  return <StatusChangeModal title={`${action.label} MID · ${mid.externalMidCode}`} path={channelPaths.midStatus(mid.midId)} targetStatus={action.targetStatus}
    actionLabel={action.label} decode={value => decodeMid(value, mid.midId)} onDone={onDone} {...base}
    description={action.targetStatus === 'ACTIVE' ? '激活要求渠道与当前账号均为 ACTIVE；首次激活记录激活时间。'
      : action.targetStatus === 'CLOSED' ? '关闭为终态，记录关闭时间，之后不能改绑或维护能力组。' : '暂停后该 MID 不再参与路由候选。'} />;
}

/** 渠道下非 CLOSED 的技术账号；可按 OE 过滤。 */
function useChannelAccounts(channelId: string | undefined) {
  const [accounts, setAccounts] = useState<ChannelAccountSummary[]>([]);
  useEffect(() => {
    setAccounts([]);
    if (!channelId) return;
    const controller = new AbortController();
    requestAllPages(channelPaths.accounts, controller.signal, undefined, { channelId }, decodeAccountPage)
      .then(items => setAccounts(items.filter(item => item.status !== 'CLOSED'))).catch(() => setAccounts([]));
    return () => controller.abort();
  }, [channelId]);
  return accounts;
}

/** 新建 MID：账号须属于所选渠道，OE 随账号带出且须一致；同渠道外部 MID 唯一（重复 409）。 */
function CreateMidModal({ onCreated, ...base }: Base & { onCreated: (mid: ChannelMid) => void }) {
  const [form] = Form.useForm<{ channelId: string; channelAccountId: string; externalMidCode: string; entityNo: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const channelId = Form.useWatch('channelId', form);
  const accounts = useChannelAccounts(channelId);
  const submit = async () => {
    const values = await form.validateFields();
    const mid = await command.run(channelPaths.mids, 'POST', { ...values, externalMidCode: values.externalMidCode.trim() }, value => decodeMid(value), 'MID 创建失败');
    if (mid) onCreated(mid);
  };
  return <CommandModal title="新建 MID" command={command} okText="创建" onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-mid-create" layout="vertical">
      <Form.Item name="channelId" label="渠道" rules={[{ required: true, message: '请选择渠道' }]}>
        <ChannelSelect allowClear={false} placeholder="选择渠道" excludeClosed channelType="PAYMENT" onChange={value => form.setFieldsValue({ channelId: value, channelAccountId: undefined })} />
      </Form.Item>
      <Form.Item name="channelAccountId" label="技术账号" rules={[{ required: true, message: '请选择账号' }]}>
        <Select placeholder={channelId ? '选择账号' : '先选择渠道'} options={accounts.map(a => ({ value: a.channelAccountId, label: `${a.accountName} · ${a.entityNo} · ${a.status}` }))}
          onChange={value => form.setFieldsValue({ entityNo: accounts.find(a => a.channelAccountId === value)?.entityNo ?? undefined })} /></Form.Item>
      <div className="channel-form-grid">
        <Form.Item name="externalMidCode" label="外部 MID" rules={[{ required: true, whitespace: true, message: '必填' }, { max: 128, message: '最长 128' }]}>
          <Input maxLength={128} /></Form.Item>
        <Form.Item name="entityNo" label="OE（随账号）" rules={[{ required: true, message: '必填' }, { pattern: entityNoPattern, message: '如 AE001' }]}>
          <Input maxLength={5} disabled /></Form.Item>
      </div>
    </Form>
  </CommandModal>;
}

/** 改绑账号：候选限同渠道、同 OE、非 CLOSED，排除当前账号。 */
function RebindModal({ mid, onDone, ...base }: Base & { mid: Pick<ChannelMid, 'midId' | 'channelId' | 'channelAccountId' | 'entityNo' | 'externalMidCode'>; onDone: () => void }) {
  const [form] = Form.useForm<{ channelAccountId: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const accounts = useChannelAccounts(mid.channelId).filter(a => a.entityNo === mid.entityNo && a.channelAccountId !== mid.channelAccountId);
  const submit = async () => {
    const values = await form.validateFields();
    const saved = await command.run(channelPaths.midAccount(mid.midId), 'PUT', values, value => decodeMid(value, mid.midId), '改绑失败');
    if (saved) onDone();
  };
  return <CommandModal title={`改绑账号 · ${mid.externalMidCode}`} command={command} okText="改绑" onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-mid-rebind" layout="vertical">
      <Form.Item name="channelAccountId" label={`目标账号（同渠道、OE ${mid.entityNo}）`} rules={[{ required: true, message: '请选择账号' }]}>
        <Select placeholder="选择账号" notFoundContent="没有可改绑的账号" options={accounts.map(a => ({ value: a.channelAccountId, label: `${a.accountName} · ${a.status} · ID ${a.channelAccountId}` }))} />
      </Form.Item>
    </Form>
    <ChannelNote>改绑只改变当前绑定；Payment 已冻结到支付尝试的渠道 / 账号 / MID 不受影响。</ChannelNote>
  </CommandModal>;
}

type DetailStep = { kind: 'rebind' } | { kind: 'status'; action: LifecycleAction } | { kind: 'capability'; capability?: MidCapability }
  | { kind: 'toggle'; capability: MidCapability } | null;

/** MID 详情（画板 07）：信息条；页签“能力组”（六维 + 状态 + 编辑明细 / 启停，选中组的明细表）与“变更记录”。 */
function MidDetail({ midId, resources, onAuthorizationChanged }: Props & { midId: string }) {
  const navigate = useNavigate();
  const detail = useDetail(channelPaths.mid(midId), decodeMid);
  const definitionsDetail = useDetail(channelPaths.capabilityDefinitions, decodeDefinitions);
  const [channel, setChannel] = useState<Channel>();
  const [step, setStep] = useState<DetailStep>(null);
  const [selectedId, setSelectedId] = useState<string>();
  const [tab, setTab] = useState('capabilities');
  const [revision, setRevision] = useState(0);
  const canManage = hasButton(resources, channelButtons.midManage);
  const channelId = detail.data?.channelId;
  useEffect(() => {
    if (!channelId) return;
    const controller = new AbortController();
    session.request(channelPaths.channel(channelId), { signal: controller.signal }, value => decodeChannel(value, channelId)).then(setChannel).catch(() => undefined);
    return () => controller.abort();
  }, [channelId]);
  const definitions: CapabilityDefinition[] = useMemo(() => definitionsDetail.data ?? [], [definitionsDetail.data]);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const mid = detail.data;
  const selected = mid.capabilities.find(c => c.midCapabilityId === selectedId) ?? mid.capabilities[0];
  const reload = () => { setStep(null); detail.refresh(); setRevision(v => v + 1); };
  const manageable = editable(mid.status, canManage);
  return <>
    <div className="page-heading">
      <div className="channel-title"><h1>{mid.externalMidCode}</h1><StatusTag status={mid.status} /></div>
      <div className="channel-actions">
        {manageable && <Button onClick={() => setStep({ kind: 'rebind' })}>改绑账号</Button>}
        {lifecycleActions(mid.status, canManage).map(action => <Button key={action.targetStatus} danger={action.danger}
          onClick={() => setStep({ kind: 'status', action })}>{action.label}</Button>)}
        <Button onClick={() => void navigate(channelRoutes.mids)}>返回列表</Button>
      </div>
    </div>
    <Summary items={[
      { label: '渠道', value: <Button type="link" size="small" onClick={() => void navigate(channelRoutes.channel(mid.channelId))}>
        {channel ? `${channel.channelCode} · ${channel.channelName}` : mid.channelId}</Button> },
      { label: 'OE', value: mid.entityNo },
      { label: '当前账号', value: <span className="mono">ID {mid.channelAccountId}</span> },
      { label: 'MID ID', value: <span className="mono">{mid.midId}</span> },
      { label: '激活时间', value: localTime(mid.activatedAt) },
      ...(mid.closedAt ? [{ label: '关闭时间', value: localTime(mid.closedAt) }] : []),
      { label: '更新', value: localTime(mid.updateTime) },
    ]} />
    <Tabs activeKey={tab} onChange={setTab}
      tabBarExtraContent={tab === 'capabilities' && manageable
        && <Button type="primary" disabled={!definitions.length} onClick={() => setStep({ kind: 'capability' })}>新建能力组</Button>}
      items={[
      { key: 'capabilities', label: '能力组', children: <>
        {definitionsDetail.failure && <ChannelNote tone="warning">能力目录加载失败，暂不能新建或编辑明细。</ChannelNote>}
        <Table<MidCapability> rowKey="midCapabilityId" size="middle" pagination={false} dataSource={mid.capabilities} scroll={{ x: 760 }}
          locale={{ emptyText: '还没有能力组' }} rowClassName={row => `clickable-row${row.midCapabilityId === selected?.midCapabilityId ? ' channel-selected-row' : ''}`}
          onRow={row => ({ onClick: () => setSelectedId(row.midCapabilityId) })} columns={[
            { title: '支付方式', width: 110, dataIndex: 'paymentMethodCode' },
            { title: '卡组', width: 120, dataIndex: 'schemeCode' },
            { title: '国家', width: 70, dataIndex: 'countryCode' },
            { title: '币种', width: 70, dataIndex: 'currency' },
            { title: 'MCC', width: 70, dataIndex: 'mccCode' },
            { title: '状态', width: 110, render: (_, row) => <StatusTag status={row.status} /> },
            { title: '操作', width: 160, render: (_, row) => {
              const toggle = capabilityToggle(row, mid.status, canManage);
              return <span className="channel-actions">
                {manageable && <Button type="link" size="small" disabled={!definitions.length}
                  onClick={event => { event.stopPropagation(); setStep({ kind: 'capability', capability: row }); }}>编辑明细</Button>}
                {toggle && <Button type="link" size="small" onClick={event => { event.stopPropagation(); setStep({ kind: 'toggle', capability: row }); }}>
                  {toggle === 'ENABLED' ? '启用' : '停用'}</Button>}
              </span>;
            } },
          ]} />
        {selected && <>
          <div className="channel-section-head"><h2>明细 · {[selected.paymentMethodCode, selected.schemeCode, selected.countryCode, selected.currency, selected.mccCode].join(' / ')}</h2></div>
          <Table rowKey="capabilityCode" size="middle" pagination={false} dataSource={selected.details} columns={[
            { title: '能力代码', dataIndex: 'capabilityCode', width: 220, render: (code: string) => <span className="mono">{code}</span> },
            { title: '类型', dataIndex: 'valueType', width: 110 },
            { title: '值', width: 160, render: (_, row) => detailValue(row) },
            { title: '说明', render: (_, row) => {
              const allowed = definitions.find(d => d.capabilityCode === row.capabilityCode)?.allowedCodes ?? [];
              return row.valueType === 'CODE' && allowed.length ? `允许值 ${allowed.join(' / ')}` : '—';
            } },
          ]} />
        </>}
        <ChannelNote>六维组合在 MID 内唯一；明细按能力目录渲染，类型与目录不一致被拒绝。整组停用后候选查询不再返回该组能力。</ChannelNote>
      </> },
      { key: 'changes', label: '变更记录', children: <ChangeList key={revision} targetType="CHANNEL_MID" targetId={mid.midId} /> },
    ]} />
    {step?.kind === 'rebind' && <RebindModal mid={mid} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={reload} />}
    {step?.kind === 'status' && <MidStatusModal mid={mid} action={step.action} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={reload} />}
    {step?.kind === 'capability' && <CapabilityEditor midId={mid.midId} definitions={definitions} capability={step.capability} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged} onSaved={capability => { setSelectedId(capability.midCapabilityId); reload(); }} />}
    {step?.kind === 'toggle' && <CapabilityToggleModal capability={step.capability} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={reload} />}
  </>;
}

/** 能力组启停确认。 */
function CapabilityToggleModal({ capability, onDone, ...base }: Base & { capability: MidCapability; onDone: () => void }) {
  const command = useChannelCommand(base.onAuthorizationChanged);
  const target = capability.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  const submit = async () => {
    const saved = await command.run(channelPaths.capabilityStatus(capability.midCapabilityId), 'POST', { targetStatus: target }, decodeCapability, '能力组状态变更失败');
    if (saved) onDone();
  };
  return <CommandModal title={`${capabilityStatusLabels[target]}能力组`} command={command} okText={capabilityStatusLabels[target]} danger={target === 'DISABLED'}
    onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <ChannelNote>{[capability.paymentMethodCode, capability.schemeCode, capability.countryCode, capability.currency, capability.mccCode].join(' / ')}
      {target === 'DISABLED' ? '：停用后候选查询不再返回该组能力。' : '：启用后重新参与候选查询。'}</ChannelNote>
  </CommandModal>;
}
