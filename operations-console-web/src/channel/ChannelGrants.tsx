import { useEffect, useMemo, useState } from 'react';
import { AutoComplete, Button, Form, Segmented, Select, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { translator } from '../i18n/index';
import { useT } from '../i18n/react';
import { channelPaths, decodeCallGrant, decodeCallGrantPage, grantStatuses, listFilters, localTime } from './channel';
import type { CallGrant, Channel, GrantStatus } from './channel';
import { callerLabel, callerOptions, grantMatrix, grantView, isCallerService, workspaceRoutes } from './channelWorkspace';
import type { GrantMatrix, ImplementedApi } from './channelWorkspace';
import { ChangeTrail, ChannelNote, CommandModal, StatusChangeModal, StatusTag } from './ChannelParts';
import { richText } from '../i18n/richText';
import { useChannelCommand } from './useChannelCommand';

interface Props {
  channel: Channel; implemented: ImplementedApi[] | undefined; grants: ChannelGrantsState; canGrant: boolean; onAuthorizationChanged: () => void;
  onChanged: () => void;
}
type Step = { kind: 'create'; callerService?: string; standardApiCode?: string } | { kind: 'status'; grant: CallGrant } | null;

/** 本渠道全部授权（矩阵与计数用）；每页 200 条拼接，失败时显示重试。 */
export function useChannelGrants(channelId: string, enabled = true) {
  const [grants, setGrants] = useState<CallGrant[]>();
  const [failure, setFailure] = useState<unknown>();
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!enabled) return undefined;
    const controller = new AbortController(); setFailure(undefined);
    requestAllPages(channelPaths.callGrants, controller.signal, undefined, { channelId }, decodeCallGrantPage)
      .then(items => { if (!controller.signal.aborted) setGrants(items); })
      .catch(error => { if (!controller.signal.aborted) setFailure(error); });
    return () => controller.abort();
  }, [channelId, enabled, revision]);
  return { grants, failure, refresh: () => setRevision(value => value + 1) };
}

export type ChannelGrantsState = ReturnType<typeof useChannelGrants>;

const grantColors: Record<GrantStatus, string> = { ACTIVE: 'green', DISABLED: 'default' };
const tGrants = translator('channel');
const operator = (time: string, id: string | null) => id ? tGrants('changes.trailMeta', { time: localTime(time), operator: id }) : localTime(time);

/**
 * 调用授权页签（画板 07–09）：按「调用方服务 × 标准 API」放行通用入口调用。矩阵 / 列表两种视图写入 ?view=；
 * 写操作需要「调用授权」按钮，没有时只读。授权不删除：停用后再启用沿用同一条记录。
 */
export function ChannelGrants({ channel, implemented, grants: all, canGrant, onAuthorizationChanged, onChanged }: Props) {
  const t = useT('channel');
  const location = useLocation(); const navigate = useNavigate();
  const routes = workspaceRoutes(channel.channelId);
  const view = grantView(new URLSearchParams(location.search).get('view'));
  const [step, setStep] = useState<Step>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [listRevision, setListRevision] = useState(0);
  const writable = canGrant && channel.status !== 'CLOSED';
  const matrix = useMemo(() => all.grants && implemented ? grantMatrix(all.grants, implemented) : null, [all.grants, implemented]);
  const selected = all.grants?.find(grant => grant.callGrantId === selectedId) ?? null;
  // 全部授权由渠道详情读取并传入（onChanged 中刷新），本页不重复请求：授权列表路由的 Gateway 并发上限为每人 2
  const reload = () => { setStep(null); setListRevision(value => value + 1); onChanged(); };
  return <div className="channel-grants">
    <div className="channel-section-head">
      <div><h2>{t('workspace.tab.grants')}</h2><div className="channel-cell-meta">{t('grants.subtitle')}</div></div>
      <span className="channel-spacer" />
      <Segmented value={view} onChange={value => void navigate(routes.tab('grants', { view: value === 'list' ? 'list' : null }), { replace: true })}
        options={[{ value: 'matrix', label: t('grants.matrix') }, { value: 'list', label: t('grants.list') }]} />
      {writable && <Button type="primary" disabled={!implemented?.length} title={implemented?.length ? undefined : t('grants.noImplementation')}
        onClick={() => setStep({ kind: 'create' })}>{t('grants.create')}</Button>}
    </div>
    {!canGrant && <ChannelNote>{t('grants.readOnly')}</ChannelNote>}
    {view === 'matrix'
      ? all.failure ? <QueryFailure failure={all.failure} retry={all.refresh} />
        : <MatrixView matrix={matrix} selected={selected} writable={writable} onSelect={grant => setSelectedId(grant.callGrantId)}
          onCreate={(callerService, standardApiCode) => setStep({ kind: 'create', callerService, standardApiCode })}
          onStatus={grant => setStep({ kind: 'status', grant })} onTrail={grant => void navigate(routes.changes('CHANNEL_CALL_GRANT', grant.callGrantId))} />
      : <ListView key={listRevision} channel={channel} implemented={implemented ?? []} grants={all.grants} writable={writable}
        onStatus={grant => setStep({ kind: 'status', grant })} onTrail={grant => void navigate(routes.changes('CHANNEL_CALL_GRANT', grant.callGrantId))} />}
    <ChannelNote>
      <div>{t('grants.note.columns')}</div>
      <div>{t('grants.note.identity')}</div>
      <div>{t('grants.note.scope')}</div>
      <div>{t('grants.note.audit')}</div>
    </ChannelNote>
    {step?.kind === 'create' && implemented && <NewGrantModal channel={channel} implemented={implemented} grants={all.grants ?? []}
      initialCaller={step.callerService} initialApi={step.standardApiCode} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onSaved={grant => { setSelectedId(grant.callGrantId); reload(); }} />}
    {step?.kind === 'status' && <GrantStatusModal grant={step.grant} channel={channel} onClose={() => setStep(null)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={reload} />}
  </div>;
}

function GrantTag({ status }: { status: GrantStatus }) {
  return <Tag color={grantColors[status]}>{status}</Tag>;
}

function CallerCell({ callerService }: { callerService: string }) {
  const label = callerLabel(callerService);
  return <div className="channel-cell"><span className="mono">{callerService}</span>{label && <span className="channel-cell-meta">{label}</span>}</div>;
}

/** 矩阵视图（画板 07）：行 = 调用方，列 = 已实现标准 API；选中单元格后右侧显示详情、启停与留痕。 */
function MatrixView({ matrix, selected, writable, onSelect, onCreate, onStatus, onTrail }: {
  matrix: GrantMatrix | null; selected: CallGrant | null; writable: boolean; onSelect: (grant: CallGrant) => void;
  onCreate: (callerService: string, standardApiCode: string) => void; onStatus: (grant: CallGrant) => void; onTrail: (grant: CallGrant) => void;
}) {
  const t = useT('channel');
  if (!matrix) return <div className="content-loading">{t('grants.loading')}</div>;
  if (matrix.columns.length === 0) return <ChannelNote tone="warning">{t('grants.noImplementationNote')}</ChannelNote>;
  return <div className="channel-grant-layout">
    <div className="channel-grant-matrix-wrap">
      <table className="channel-grant-matrix">
        <thead><tr><th className="channel-grant-corner">{t('grants.corner')}</th>{matrix.columns.map(column => <th key={column.standardApiCode}>
          <span className="mono">{column.standardApiCode}</span><span className="channel-cell-meta">{t('grants.implemented', { name: column.standardApiName })}</span></th>)}</tr></thead>
        <tbody>
          {matrix.rows.length === 0 && <tr><td colSpan={matrix.columns.length + 1} className="channel-grant-empty">{writable ? t('grants.emptyWritable') : t('grants.empty')}</td></tr>}
          {matrix.rows.map(row => <tr key={row.callerService}>
            <th scope="row"><CallerCell callerService={row.callerService} /></th>
            {matrix.columns.map(column => {
              const grant = row.cells[column.standardApiCode];
              if (!grant) {
                return <td key={column.standardApiCode} className="channel-grant-cell is-empty">
                  {writable ? <button type="button" onClick={() => onCreate(row.callerService, column.standardApiCode)}>
                    <span className="channel-grant-dash">—</span><span className="channel-grant-add">{t('grants.add')}</span></button> : '—'}</td>;
              }
              return <td key={column.standardApiCode} className={`channel-grant-cell${selected?.callGrantId === grant.callGrantId ? ' is-selected' : ''}`}>
                <button type="button" onClick={() => onSelect(grant)} aria-label={`${row.callerService} ${column.standardApiCode} ${grant.grantStatus}`}>
                  <GrantTag status={grant.grantStatus} />
                  <span className="channel-cell-meta">{grant.grantStatus === 'ACTIVE' ? localTime(grant.updateTime).slice(5, 10) : writable ? t('grants.enable') : t('enum.grantStatus.DISABLED')}</span>
                </button></td>;
            })}
          </tr>)}
        </tbody>
      </table>
      <div className="channel-legend"><GrantTag status="ACTIVE" /> {t('grants.legend.active')} <GrantTag status="DISABLED" /> {t('grants.legend.disabled')} <span>{t('grants.legend.none')}</span>
        <span className="channel-spacer" /><span className="channel-cell-meta">{matrix.activeCount} ACTIVE / {matrix.disabledCount} DISABLED</span></div>
      {matrix.orphans.length > 0 && <ChannelNote tone="warning">{t('grants.orphans', { count: matrix.orphans.length })}</ChannelNote>}
    </div>
    {selected && <GrantDetail grant={selected} writable={writable} onStatus={onStatus} onTrail={onTrail} />}
  </div>;
}

/** 选中授权的详情：启用 / 停用与留痕（该授权的变更记录）。 */
function GrantDetail({ grant, writable, onStatus, onTrail }: {
  grant: CallGrant; writable: boolean; onStatus: (grant: CallGrant) => void; onTrail: (grant: CallGrant) => void;
}) {
  const t = useT('channel');
  return <aside className="channel-grant-detail">
    <div className="channel-cell"><span className="mono">{grant.callerService}</span>
      <span>→ <span className="mono">{grant.standardApiCode}</span> · {grant.standardApiName}</span></div>
    <div><GrantTag status={grant.grantStatus} /></div>
    <dl className="channel-kv-list">
      <div><dt>{t('grants.id')}</dt><dd className="mono">{grant.callGrantId}</dd></div>
      <div><dt>{t('grants.version')}</dt><dd>v{grant.version}</dd></div>
      <div><dt>{t('grants.created')}</dt><dd>{operator(grant.createTime, grant.createdBy)}</dd></div>
      <div><dt>{t('grants.updated')}</dt><dd>{grant.version > 1 ? operator(grant.updateTime, grant.updatedBy) : '—'}</dd></div>
    </dl>
    <div className="channel-actions">
      {writable && <Button danger={grant.grantStatus === 'ACTIVE'} onClick={() => onStatus(grant)}>{grant.grantStatus === 'ACTIVE' ? t('grants.disableGrant') : t('grants.enableGrant')}</Button>}
      <Button type="link" onClick={() => onTrail(grant)}>{t('grants.allTrail')}</Button>
    </div>
    <h3>{t('grants.trail')}</h3>
    <ChangeTrail key={`${grant.callGrantId}:${grant.version}`} targetType="CHANNEL_CALL_GRANT" targetId={grant.callGrantId} />
    <div className="channel-cell-meta">{t('grants.singleNote')}</div>
  </aside>;
}

interface ListFilters { callerService?: string; standardApiCode?: string; grantStatus?: string }

/** 列表视图（画板 08）：可按调用方、标准 API 与状态筛选（服务端分页）。 */
function ListView({ channel, implemented, grants, writable, onStatus, onTrail }: {
  channel: Channel; implemented: ImplementedApi[]; grants: CallGrant[] | undefined; writable: boolean;
  onStatus: (grant: CallGrant) => void; onTrail: (grant: CallGrant) => void;
}) {
  const t = useT('channel');
  const [form] = Form.useForm<ListFilters>();
  const page = usePaging<CallGrant>(channelPaths.callGrants, decodeCallGrantPage, { channelId: channel.channelId }, { notifyOnError: false });
  const callers = callerOptions(grants ?? []).filter(option => option.grantCount > 0);
  const active = grants?.filter(grant => grant.grantStatus === 'ACTIVE').length;
  return <>
    <Form form={form} layout="inline" className="channel-grant-filters"
      onFinish={(values: ListFilters) => page.applyFilters({ channelId: channel.channelId, ...listFilters({ ...values }) })}>
      <Form.Item name="callerService"><Select allowClear placeholder={t('grants.callerService')} style={{ width: 220 }}
        options={callers.map(option => ({ value: option.callerService, label: option.callerService }))} /></Form.Item>
      <Form.Item name="standardApiCode"><Select allowClear placeholder={t('grants.standardApi')} style={{ width: 220 }}
        options={implemented.map(api => ({ value: api.standardApiCode, label: `${api.standardApiCode} · ${api.standardApiName}` }))} /></Form.Item>
      <Form.Item name="grantStatus"><Select allowClear placeholder={t('grants.allStatuses')} style={{ width: 140 }}
        options={grantStatuses.map(value => ({ value, label: value }))} /></Form.Item>
      <Button type="primary" htmlType="submit">{t('changes.search')}</Button>
      <Button onClick={() => { form.resetFields(); page.applyFilters({ channelId: channel.channelId }); }}>{t('filter.reset')}</Button>
    </Form>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
      : <Table<CallGrant> rowKey="callGrantId" size="middle" loading={page.loading} dataSource={page.data.items}
        pagination={{ ...page.pagination, showTotal: total => grants ? t('grants.totalWithStatus', { total, active: active ?? 0, disabled: grants.length - (active ?? 0) }) : t('grants.total', { total }) }}
        scroll={{ x: 1000 }} locale={{ emptyText: t('grants.noMatch') }} columns={[
          { title: t('grants.callerService'), width: 200, render: (_, row) => <CallerCell callerService={row.callerService} /> },
          { title: t('grants.standardApi'), width: 220, render: (_, row) => <div className="channel-cell"><span className="mono">{row.standardApiCode}</span>
            <span className="channel-cell-meta">{row.standardApiName}</span></div> },
          { title: t('grants.status'), width: 100, render: (_, row) => <GrantTag status={row.grantStatus} /> },
          { title: t('grants.version'), width: 70, render: (_, row) => `v${row.version}` },
          { title: t('grants.created'), width: 190, render: (_, row) => operator(row.createTime, row.createdBy) },
          { title: t('grants.updated'), width: 190, render: (_, row) => row.version > 1 ? operator(row.updateTime, row.updatedBy) : '—' },
          { title: t('grants.actions'), width: 130, render: (_, row) => <span className="channel-actions">
            {writable && <Button type="link" size="small" danger={row.grantStatus === 'ACTIVE'} onClick={() => onStatus(row)}>
              {row.grantStatus === 'ACTIVE' ? t('credential.disable') : t('grants.enable')}</Button>}
            <Button type="link" size="small" onClick={() => onTrail(row)}>{t('grants.trail')}</Button></span> },
        ]} />}
  </>;
}

/**
 * 新增授权（画板 09）：调用方为可输入下拉（已有调用方 + 预置名单），格式 [a-z][a-z0-9-]{1,63}；标准 API 只列已实现的。
 * 已存在同一组合时 Owner 返回 409，弹窗显示原因（含现有状态与授权 ID），引导回矩阵启用原记录。
 */
function NewGrantModal({ channel, implemented, grants, initialCaller, initialApi, onSaved, onClose, onAuthorizationChanged }: {
  channel: Channel; implemented: ImplementedApi[]; grants: CallGrant[]; initialCaller?: string; initialApi?: string;
  onSaved: (grant: CallGrant) => void; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('channel');
  const [form] = Form.useForm<{ callerService: string; standardApiCode: string }>();
  const command = useChannelCommand(onAuthorizationChanged, form);
  const callerService = (Form.useWatch('callerService', form) ?? '').trim();
  const standardApiCode = Form.useWatch('standardApiCode', form);
  const existing = grants.find(grant => grant.callerService === callerService && grant.standardApiCode === standardApiCode);
  const submit = async () => {
    const values = await form.validateFields();
    const grant = await command.run(channelPaths.callGrants, 'POST', { channelId: channel.channelId, callerService: values.callerService.trim(),
      standardApiCode: values.standardApiCode }, value => decodeCallGrant(value), t('grants.createFailed'));
    if (grant) onSaved(grant);
  };
  const api = implemented.find(item => item.standardApiCode === standardApiCode);
  return <CommandModal title={t('grants.createTitle', { code: channel.channelCode })} command={command} okText={t('grants.grant')} width={640} okDisabled={!!existing} onOk={() => void submit()}
    onClose={onClose} onReload={onClose}>
    <Form form={form} name="channel-grant-create" layout="vertical" initialValues={{ callerService: initialCaller, standardApiCode: initialApi }}>
      <Form.Item name="callerService" label={t('grants.callerService')} extra={t('grants.callerExtra')} normalize={(value?: string) => value?.trim()}
        rules={[{ required: true, message: t('form.required') }, { validator: async (_, value?: string) => {
          if (value && !isCallerService(value)) throw new Error(t('grants.callerPattern'));
        } }]}>
        <AutoComplete placeholder={t('grants.callerPlaceholder')} options={callerOptions(grants).map(option => ({ value: option.callerService,
          label: <span className="channel-option"><span className="mono">{option.callerService}</span>
            <span className="channel-cell-meta">{option.label ?? ''}{option.grantCount ? t('grants.existingCount', { count: option.grantCount }) : ''}</span></span> }))}
          filterOption={(input, option) => String(option?.value ?? '').includes(input.trim())} />
      </Form.Item>
      <Form.Item name="standardApiCode" label={t('grants.standardApi')} extra={t('grants.apiExtra')} rules={[{ required: true, message: t('grants.apiRequired') }]}>
        <Select options={implemented.map(item => ({ value: item.standardApiCode, label: `${item.standardApiCode} · ${item.standardApiName}` }))} />
      </Form.Item>
    </Form>
    {existing ? <ChannelNote tone="warning">{existing.grantStatus === 'ACTIVE'
      ? t('grants.existsView', { status: existing.grantStatus, id: existing.callGrantId })
      : t('grants.existsEnable', { status: existing.grantStatus, id: existing.callGrantId })}</ChannelNote>
      : callerService && api && isCallerService(callerService) && <ChannelNote>{richText(t, 'grants.createEffect', {
        caller: <span className="mono">{callerService}</span>, target: <span className="mono">{channel.channelCode} · {api.standardApiCode}</span> })}</ChannelNote>}
  </CommandModal>;
}

/** 启用 / 停用授权：带期望版本，原因写入变更记录。 */
function GrantStatusModal({ grant, channel, onDone, onClose, onAuthorizationChanged }: {
  grant: CallGrant; channel: Channel; onDone: () => void; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('channel');
  const enable = grant.grantStatus !== 'ACTIVE';
  const pair = { caller: grant.callerService, api: grant.standardApiCode };
  return <StatusChangeModal title={enable ? t('grants.enableTitle', pair) : t('grants.disableTitle', pair)}
    path={channelPaths.callGrantStatus(grant.callGrantId)} targetStatus={enable ? 'ACTIVE' : 'DISABLED'} actionLabel={enable ? t('grants.enable') : t('credential.disable')}
    extraBody={{ expectedVersion: grant.version }} decode={value => decodeCallGrant(value, grant.callGrantId)}
    description={enable ? t('grants.enableNote', { ...pair, channel: channel.channelCode })
      : t('grants.disableNote', pair)}
    onDone={onDone} onClose={onClose} onAuthorizationChanged={onAuthorizationChanged}>
    <StatusTag status={grant.grantStatus} /> <span className="channel-cell-meta">{t('grants.currentVersion', { version: grant.version })}</span>
  </StatusChangeModal>;
}
