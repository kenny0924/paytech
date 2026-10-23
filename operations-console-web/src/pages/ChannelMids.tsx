import { useEffect, useMemo, useState } from 'react';
import { Button, Form, Input, Select, Table, Tabs } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { useT } from '../i18n/react';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import {
  capabilityStatusLabel, capabilityToggle, channelButtons, channelPaths, channelRoutes, decodeAccountPage, decodeCapability,
  decodeChannel, decodeDefinitions, decodeMid, decodeMidPage, detailValue, editable, entityNoPattern, hasButton, isId, lifecycleActions,
  lifecycleStatuses, lifecycleStatusLabel, listFilters, localTime, primaryLifecycleAction,
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
  const t = useT('channel'), tNav = useT('nav');
  const navigate = useNavigate();
  const [form] = Form.useForm<FilterValues>();
  const [step, setStep] = useState<ListStep>(null);
  const page = usePaging<ChannelMidSummary>(channelPaths.mids, decodeMidPage, {}, { notifyOnError: false });
  const canManage = hasButton(resources, channelButtons.midManage);
  const done = () => { setStep(null); page.refresh(); };
  return <>
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_CHANNEL_MIDS')}</h1></div>
    <div className="channel-toolbar">
      <Form form={form} name="channel-mid-filter" layout="vertical" onFinish={(values: FilterValues) => page.applyFilters(listFilters({ ...values }))}>
        <div className="channel-filters">
          <Form.Item name="channelId" label={t('filter.channel')}><ChannelSelect channelType="PAYMENT" /></Form.Item>
          <Form.Item name="entityNo" label="OE" normalize={(v?: string) => v?.trim().toUpperCase()} rules={[{ pattern: entityNoPattern, message: t('mids.entityNoExample') }]}>
            <Input allowClear maxLength={5} placeholder={t('filter.all')} /></Form.Item>
          <Form.Item name="status" label={t('grants.status')}><Select allowClear placeholder={t('filter.all')}
            options={lifecycleStatuses.map(value => ({ value, label: `${value} · ${lifecycleStatusLabel(value)}` }))} /></Form.Item>
          <Form.Item name="externalMidCode" label={t('channels.externalMid')} rules={[{ max: 128, message: t('form.maxLength', { max: 128 }) }]}>
            <Input allowClear maxLength={128} placeholder={t('mids.prefixMatch')} /></Form.Item>
          <div className="channel-filter-actions"><Button type="primary" htmlType="submit">{t('changes.search')}</Button>
            <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('filter.reset')}</Button></div>
        </div>
      </Form>
      {canManage && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>{t('mids.create')}</Button>}</div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<ChannelMidSummary> rowKey="midId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1100 }} locale={{ emptyText: t('mids.noMatch') }} columns={[
          { title: t('channels.externalMid'), width: 200, render: (_, row) => <div className="channel-cell"><span>{row.externalMidCode}</span>
            <span className="channel-cell-meta">ID {row.midId}</span></div> },
          { title: t('filter.channel'), width: 130, dataIndex: 'channelCode' },
          { title: 'OE', width: 80, dataIndex: 'entityNo' },
          { title: t('channels.currentAccount'), width: 200, render: (_, row) => <div className="channel-cell"><span>{row.accountName}</span>
            <span className="channel-cell-meta">ID {row.channelAccountId}</span></div> },
          { title: t('grants.status'), width: 120, render: (_, row) => <StatusTag status={row.status} /> },
          { title: t('channels.capabilityGroup'), width: 120, render: (_, row) => row.capabilityGroupCount ? t('channels.capabilityGroups', { total: row.capabilityGroupCount, enabled: row.enabledCapabilityGroupCount }) : t('mids.noGroups') },
          { title: t('channels.activatedAt'), width: 150, render: (_, row) => localTime(row.activatedAt) },
          { title: t('grants.actions'), width: 200, render: (_, row) => {
            const action = primaryLifecycleAction(row.status, canManage);
            return <span className="channel-actions">
              <Button type="link" size="small" onClick={() => void navigate(channelRoutes.mid(row.midId))}>{t('protocol.details')}</Button>
              {editable(row.status, canManage) && <Button type="link" size="small" onClick={() => setStep({ kind: 'rebind', mid: row })}>{t('mids.rebind')}</Button>}
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
  const t = useT('channel');
  return <StatusChangeModal title={t('mids.statusTitle', { action: action.label, code: mid.externalMidCode })} path={channelPaths.midStatus(mid.midId)} targetStatus={action.targetStatus}
    actionLabel={action.label} decode={value => decodeMid(value, mid.midId)} onDone={onDone} {...base}
    description={action.targetStatus === 'ACTIVE' ? t('mids.activateNote')
      : action.targetStatus === 'CLOSED' ? t('mids.closeNote') : t('mids.suspendNote')} />;
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
  const t = useT('channel');
  const [form] = Form.useForm<{ channelId: string; channelAccountId: string; externalMidCode: string; entityNo: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const channelId = Form.useWatch('channelId', form);
  const accounts = useChannelAccounts(channelId);
  const submit = async () => {
    const values = await form.validateFields();
    const mid = await command.run(channelPaths.mids, 'POST', { ...values, externalMidCode: values.externalMidCode.trim() }, value => decodeMid(value), t('mids.createFailed'));
    if (mid) onCreated(mid);
  };
  return <CommandModal title={t('mids.create')} command={command} okText={t('channels.createOk')} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-mid-create" layout="vertical">
      <Form.Item name="channelId" label={t('filter.channel')} rules={[{ required: true, message: t('mids.channelRequired') }]}>
        <ChannelSelect allowClear={false} placeholder={t('mids.selectChannel')} excludeClosed channelType="PAYMENT" onChange={value => form.setFieldsValue({ channelId: value, channelAccountId: undefined })} />
      </Form.Item>
      <Form.Item name="channelAccountId" label={t('workspace.tab.accounts')} rules={[{ required: true, message: t('mids.accountRequired') }]}>
        <Select placeholder={channelId ? t('mids.selectAccount') : t('mids.selectChannelFirst')} options={accounts.map(a => ({ value: a.channelAccountId, label: `${a.accountName} · ${a.entityNo} · ${a.status}` }))}
          onChange={value => form.setFieldsValue({ entityNo: accounts.find(a => a.channelAccountId === value)?.entityNo ?? undefined })} /></Form.Item>
      <div className="channel-form-grid">
        <Form.Item name="externalMidCode" label={t('channels.externalMid')} rules={[{ required: true, whitespace: true, message: t('form.required') }, { max: 128, message: t('form.maxLength', { max: 128 }) }]}>
          <Input maxLength={128} /></Form.Item>
        <Form.Item name="entityNo" label={t('mids.entityNoFromAccount')} rules={[{ required: true, message: t('form.required') }, { pattern: entityNoPattern, message: t('mids.entityNoExample') }]}>
          <Input maxLength={5} disabled /></Form.Item>
      </div>
    </Form>
  </CommandModal>;
}

/** 改绑账号：候选限同渠道、同 OE、非 CLOSED，排除当前账号。 */
function RebindModal({ mid, onDone, ...base }: Base & { mid: Pick<ChannelMid, 'midId' | 'channelId' | 'channelAccountId' | 'entityNo' | 'externalMidCode'>; onDone: () => void }) {
  const t = useT('channel');
  const [form] = Form.useForm<{ channelAccountId: string }>();
  const command = useChannelCommand(base.onAuthorizationChanged, form);
  const accounts = useChannelAccounts(mid.channelId).filter(a => a.entityNo === mid.entityNo && a.channelAccountId !== mid.channelAccountId);
  const submit = async () => {
    const values = await form.validateFields();
    const saved = await command.run(channelPaths.midAccount(mid.midId), 'PUT', values, value => decodeMid(value, mid.midId), t('mids.rebindFailed'));
    if (saved) onDone();
  };
  return <CommandModal title={t('mids.rebindTitle', { code: mid.externalMidCode })} command={command} okText={t('mids.rebindOk')} onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <Form form={form} name="channel-mid-rebind" layout="vertical">
      <Form.Item name="channelAccountId" label={t('mids.targetAccount', { entityNo: mid.entityNo })} rules={[{ required: true, message: t('mids.accountRequired') }]}>
        <Select placeholder={t('mids.selectAccount')} notFoundContent={t('mids.noRebindTarget')} options={accounts.map(a => ({ value: a.channelAccountId, label: `${a.accountName} · ${a.status} · ID ${a.channelAccountId}` }))} />
      </Form.Item>
    </Form>
    <ChannelNote>{t('mids.rebindNote')}</ChannelNote>
  </CommandModal>;
}

type DetailStep = { kind: 'rebind' } | { kind: 'status'; action: LifecycleAction } | { kind: 'capability'; capability?: MidCapability }
  | { kind: 'toggle'; capability: MidCapability } | null;

/** MID 详情（画板 07）：信息条；页签“能力组”（六维 + 状态 + 编辑明细 / 启停，选中组的明细表）与“变更记录”。 */
function MidDetail({ midId, resources, onAuthorizationChanged }: Props & { midId: string }) {
  const t = useT('channel');
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
        {manageable && <Button onClick={() => setStep({ kind: 'rebind' })}>{t('mids.rebind')}</Button>}
        {lifecycleActions(mid.status, canManage).map(action => <Button key={action.targetStatus} danger={action.danger}
          onClick={() => setStep({ kind: 'status', action })}>{action.label}</Button>)}
        <Button onClick={() => void navigate(channelRoutes.mids)}>{t('channels.backToList')}</Button>
      </div>
    </div>
    <Summary items={[
      { label: t('filter.channel'), value: <Button type="link" size="small" onClick={() => void navigate(channelRoutes.channel(mid.channelId))}>
        {channel ? `${channel.channelCode} · ${channel.channelName}` : mid.channelId}</Button> },
      { label: 'OE', value: mid.entityNo },
      { label: t('channels.currentAccount'), value: <span className="mono">ID {mid.channelAccountId}</span> },
      { label: 'MID ID', value: <span className="mono">{mid.midId}</span> },
      { label: t('channels.activatedAt'), value: localTime(mid.activatedAt) },
      ...(mid.closedAt ? [{ label: t('mids.closedAt'), value: localTime(mid.closedAt) }] : []),
      { label: t('protocol.column.updated'), value: localTime(mid.updateTime) },
    ]} />
    <Tabs activeKey={tab} onChange={setTab}
      tabBarExtraContent={tab === 'capabilities' && manageable
        && <Button type="primary" disabled={!definitions.length} onClick={() => setStep({ kind: 'capability' })}>{t('capability.createTitle')}</Button>}
      items={[
      { key: 'capabilities', label: t('channels.capabilityGroup'), children: <>
        {definitionsDetail.failure && <ChannelNote tone="warning">{t('mids.definitionsFailed')}</ChannelNote>}
        <Table<MidCapability> rowKey="midCapabilityId" size="middle" pagination={false} dataSource={mid.capabilities} scroll={{ x: 760 }}
          locale={{ emptyText: t('mids.capabilitiesEmpty') }} rowClassName={row => `clickable-row${row.midCapabilityId === selected?.midCapabilityId ? ' channel-selected-row' : ''}`}
          onRow={row => ({ onClick: () => setSelectedId(row.midCapabilityId) })} columns={[
            { title: t('capability.paymentMethod'), width: 110, dataIndex: 'paymentMethodCode' },
            { title: t('mids.scheme'), width: 120, dataIndex: 'schemeCode' },
            { title: t('costEditor.country'), width: 70, dataIndex: 'countryCode' },
            { title: t('costEditor.currency'), width: 70, dataIndex: 'currency' },
            { title: 'MCC', width: 70, dataIndex: 'mccCode' },
            { title: t('grants.status'), width: 110, render: (_, row) => <StatusTag status={row.status} /> },
            { title: t('grants.actions'), width: 160, render: (_, row) => {
              const toggle = capabilityToggle(row, mid.status, canManage);
              return <span className="channel-actions">
                {manageable && <Button type="link" size="small" disabled={!definitions.length}
                  onClick={event => { event.stopPropagation(); setStep({ kind: 'capability', capability: row }); }}>{t('mids.editDetails')}</Button>}
                {toggle && <Button type="link" size="small" onClick={event => { event.stopPropagation(); setStep({ kind: 'toggle', capability: row }); }}>
                  {capabilityStatusLabel(toggle)}</Button>}
              </span>;
            } },
          ]} />
        {selected && <>
          <div className="channel-section-head"><h2>{t('mids.detailsTitle', { condition: [selected.paymentMethodCode, selected.schemeCode, selected.countryCode, selected.currency, selected.mccCode].join(' / ') })}</h2></div>
          <Table rowKey="capabilityCode" size="middle" pagination={false} dataSource={selected.details} columns={[
            { title: t('capability.code'), dataIndex: 'capabilityCode', width: 220, render: (code: string) => <span className="mono">{code}</span> },
            { title: t('capability.type'), dataIndex: 'valueType', width: 110 },
            { title: t('accountConfig.value'), width: 160, render: (_, row) => detailValue(row) },
            { title: t('mids.note'), render: (_, row) => {
              const allowed = definitions.find(d => d.capabilityCode === row.capabilityCode)?.allowedCodes ?? [];
              return row.valueType === 'CODE' && allowed.length ? t('mids.allowedValues', { values: allowed.join(' / ') }) : '—';
            } },
          ]} />
        </>}
        <ChannelNote>{t('mids.capabilitiesNote')}</ChannelNote>
      </> },
      { key: 'changes', label: t('workspace.tab.changes'), children: <ChangeList key={revision} targetType="CHANNEL_MID" targetId={mid.midId} /> },
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
  const t = useT('channel');
  const command = useChannelCommand(base.onAuthorizationChanged);
  const target = capability.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  const submit = async () => {
    const saved = await command.run(channelPaths.capabilityStatus(capability.midCapabilityId), 'POST', { targetStatus: target }, decodeCapability, t('mids.toggleFailed'));
    if (saved) onDone();
  };
  return <CommandModal title={t('mids.toggleTitle', { action: capabilityStatusLabel(target) })} command={command} okText={capabilityStatusLabel(target)} danger={target === 'DISABLED'}
    onOk={() => void submit()} onClose={base.onClose} onReload={base.onClose}>
    <ChannelNote>{t(target === 'DISABLED' ? 'mids.disableNote' : 'mids.enableNote', {
      condition: [capability.paymentMethodCode, capability.schemeCode, capability.countryCode, capability.currency, capability.mccCode].join(' / ') })}</ChannelNote>
  </CommandModal>;
}
