import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { App, Button, Input, Select, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { loadAllPages } from '../externalApi/externalApiHooks';
import { decodeIntegrationApiSummary, decodePage, integrationPath } from '../externalApi/externalApiModel';
import { useT } from '../i18n/react';
import { channelPaths, channelRoutes, decodeChannel } from './channel';
import type { CallGrant, Channel, ChannelAccountSummary, ChannelEndpoint } from './channel';
import { endpointDraftIssues, endpointReferences, endpointRowErrors, endpointsBody, maxEndpoints, workspaceRoutes } from './channelWorkspace';
import type { ImplementedApi } from './channelWorkspace';
import { ChannelNote, CommandModal } from './ChannelParts';
import { useChannelCommand } from './useChannelCommand';

/** 出站地址被原子 API 引用的情况：读取本渠道全部原子 API 摘要后汇总；失败时为 null（不显示引用、不锁定删除，由 Owner 拒绝）。 */
export function useEndpointReferences(channelCode: string, revision = 0) {
  const [references, setReferences] = useState<Map<string, string[]> | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    loadAllPages(`${integrationPath}/${channelCode}/apis`, value => decodePage(value, decodeIntegrationApiSummary, item => item.apiCode), controller.signal)
      .then(apis => { if (!controller.signal.aborted) setReferences(endpointReferences(apis)); })
      .catch(() => { if (!controller.signal.aborted) setReferences(null); });
    return () => controller.abort();
  }, [channelCode, revision]);
  return references;
}

interface OverviewProps {
  channel: Channel; canManage: boolean; implemented: ImplementedApi[] | undefined; accounts: ChannelAccountSummary[] | undefined;
  grants: CallGrant[] | undefined; midCount: number | null; onEditEndpoints: () => void;
}

/**
 * 概览页签（画板 03 / 04）：出站地址表、描述、激活条件（只有出站地址是必需项，其余为执行需要）与关联信息。
 * 数据都来自 Owner；前端只汇总展示，不重复判断激活规则。
 */
export function ChannelOverview({ channel, canManage, implemented, accounts, grants, midCount, onEditEndpoints }: OverviewProps) {
  const t = useT('channel');
  const references = useEndpointReferences(channel.channelCode);
  const routes = workspaceRoutes(channel.channelId);
  const service = channel.channelType === 'SERVICE';
  const activeAccounts = accounts?.filter(account => account.status === 'ACTIVE') ?? [];
  const credential = activeAccounts[0];
  const activeGrants = grants?.filter(grant => grant.grantStatus === 'ACTIVE').length ?? 0;
  const disabledGrants = (grants?.length ?? 0) - activeGrants;
  const unknown = (value: unknown) => value === undefined;
  return <div className="channel-overview">
    <section className="channel-panel">
      <div className="channel-section-head"><h2>{t('overview.endpoints')}</h2><span className="channel-cell-meta">{channel.endpoints.length} / {maxEndpoints}</span>
        <span className="channel-spacer" />
        {canManage && channel.status !== 'CLOSED' && <Button onClick={onEditEndpoints}>{t('endpointEditor.title')}</Button>}</div>
      <Table<ChannelEndpoint> rowKey="endpointCode" size="middle" pagination={false} dataSource={channel.endpoints}
        locale={{ emptyText: t('overview.endpointsEmpty') }} columns={[
          { title: t('overview.column.endpointCode'), width: 200, render: (_, row) => <span className="mono">{row.endpointCode}</span> },
          { title: 'Base URL', render: (_, row) => <span className="mono">{row.baseUrl}</span> },
          { title: t('overview.column.environment'), width: 90, render: (_, row) => row.sandbox ? <Tag color="blue">{t('overview.sandbox')}</Tag> : <Tag>{t('overview.production')}</Tag> },
          { title: t('overview.column.referencedApis'), width: 140, render: (_, row) => {
            if (!references) return '—';
            const apis = references.get(row.endpointCode) ?? [];
            return apis.length ? <span title={apis.join(t('list.separator'))}>{apis.length}</span> : '0';
          } },
        ]} />
      <ChannelNote>{t('overview.endpointsNote')}</ChannelNote>
      <h2>{t('overview.description')}</h2>
      <p className="channel-description">{channel.description || <span className="channel-cell-meta">{t('overview.notFilled')}</span>}</p>
    </section>
    <aside className="channel-panel channel-overview-side">
      <h2>{t('overview.activationChecks')}</h2>
      <ul className="channel-checks">
        <Check ok={channel.endpoints.length > 0} label={t('overview.endpointsConfigured')} note={t('overview.required')} />
        {service
          ? <Check ok={unknown(accounts) ? null : !!credential?.configured} label={credential?.configured ? t('overview.credentialConfiguredSuffix', { suffix: credential.vaultTokenSuffix ?? '' }) : t('overview.credentialConfigured')} note={t('overview.neededForExecution')} />
          : <Check ok={unknown(accounts) ? null : activeAccounts.length > 0} label={t('overview.activeAccounts', { count: activeAccounts.length })} note={t('overview.neededForExecution')} />}
        <Check ok={unknown(implemented) ? null : (implemented?.length ?? 0) > 0} label={implemented ? t('overview.implementations', { count: implemented.length }) : t('overview.implementationsUnknown')} note={t('overview.neededForExecution')} />
        {service && disabledGrants > 0 && <Check ok={null} warn label={t('overview.disabledGrants', { count: disabledGrants })} note={t('overview.notBlocking')} />}
      </ul>
      <h2>{t('overview.related')}</h2>
      <dl className="channel-kv-list">
        {service ? <>
          <Related label={t('workspace.tab.grants')} value={grants ? <Link to={routes.tab('grants')}>{activeGrants} ACTIVE / {disabledGrants} DISABLED</Link> : '—'} />
          <Related label={t('overview.entry')} value={t('overview.entryGeneric')} />
          <Related label={t('overview.midCapabilityCost')} value={t('overview.notApplicable')} />
        </> : <>
          <Related label="MID" value={midCount === null ? '—' : <Link to={`${channelRoutes.mids}?channelId=${channel.channelId}`}>{t('overview.midCount', { count: midCount })}</Link>} />
          <Related label={t('overview.costPlans')} value={<Link to={channelRoutes.costPlans}>{t('overview.viewByChannel')}</Link>} />
          <Related label={t('overview.entry')} value={t('overview.entryPayment')} />
        </>}
        <Related label={t('workspace.tab.verify')} value={<Link to={routes.tab('verify')}>{t('overview.viewVerify')}</Link>} />
      </dl>
    </aside>
  </div>;
}

function Check({ ok, label, note, warn }: { ok: boolean | null; label: string; note: string; warn?: boolean }) {
  const mark = warn ? '!' : ok === null ? '·' : ok ? '✓' : '✗';
  return <li className={warn ? 'is-warn' : ok === null ? '' : ok ? 'is-ok' : 'is-missing'}><span className="channel-check-mark">{mark}</span>
    <span>{label}</span><span className="channel-cell-meta">{note}</span></li>;
}

function Related({ label, value }: { label: string; value: ReactNode }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>;
}

/**
 * 编辑出站地址（画板 12）：增删改地址；被原子 API 引用的地址不可删除、编码不可改；私网与 localhost 等 Owner 拒绝原因显示在对应行。
 * 版本冲突（409）时保留本次输入并提示重新加载；保存后渠道配置版本递增。
 */
export function EndpointEditorModal({ channel, onSaved, onClose, onReload, onAuthorizationChanged }: {
  channel: Channel; onSaved: (channel: Channel) => void; onClose: () => void; onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('channel'), tCommon = useT('common');
  const { modal } = App.useApp();
  const command = useChannelCommand(onAuthorizationChanged);
  const references = useEndpointReferences(channel.channelCode);
  const [rows, setRows] = useState<ChannelEndpoint[]>(() => channel.endpoints.length ? channel.endpoints.map(item => ({ ...item }))
    : [{ endpointCode: '', baseUrl: '', sandbox: false }]);
  const [rowErrors, setRowErrors] = useState<Map<number, string[]>>(new Map());
  const [general, setGeneral] = useState<string[]>([]);
  const [touched, setTouched] = useState(false);
  const issues = endpointDraftIssues(rows);
  const originalCodes = new Set(channel.endpoints.map(item => item.endpointCode));
  const locked = (row: ChannelEndpoint) => originalCodes.has(row.endpointCode) ? references?.get(row.endpointCode) ?? [] : [];
  const update = (index: number, patch: Partial<ChannelEndpoint>) => {
    setRows(current => current.map((row, i) => i === index ? { ...row, ...patch } : row));
    setRowErrors(current => { const next = new Map(current); next.delete(index); return next; });
  };
  const save = async () => {
    setTouched(true);
    if (issues.size) return;
    const saved = await command.run(channelPaths.channelEndpoints(channel.channelId), 'PUT', endpointsBody(channel.version, rows),
      value => decodeChannel(value, channel.channelId), t('endpointEditor.saveFailed'));
    if (saved) onSaved(saved);
  };
  // 422 字段错误与 400 / 404 的 Owner 原因按行定位；409 由冲突横幅显示（保留输入）
  const { lastError, formError } = command;
  useEffect(() => {
    if (!lastError || lastError.status === 409) { setGeneral([]); return; }
    const detail = (lastError.status === 400 || lastError.status === 404) && lastError.detail ? lastError.detail : null;
    if (!lastError.fieldErrors.length && !detail) { setGeneral(formError ? [formError] : []); return; }
    const located = endpointRowErrors(rows, lastError.fieldErrors, detail);
    setRowErrors(located.rows); setGeneral(located.general);
  }, [lastError]); // eslint-disable-line react-hooks/exhaustive-deps
  const confirmSandbox = (index: number) => modal.confirm({ title: t('endpointEditor.sandboxConfirmTitle'), centered: true, okText: t('endpointEditor.sandboxConfirmOk'), cancelText: tCommon('action.cancel'),
    content: t('endpointEditor.sandboxConfirmContent'),
    onOk: () => update(index, { sandbox: true }) });
  return <CommandModal title={<span>{t('endpointEditor.title')} <span className="channel-cell-meta">{t('endpointEditor.titleMeta', { code: channel.channelCode, version: channel.version })}</span></span>}
    command={{ ...command, formError: null }} okText={tCommon('action.save')} width={860} onOk={() => void save()} onClose={onClose} onReload={onReload}>
    {general.length > 0 && <ChannelNote tone="warning">{general.map(item => <div key={item}>{item}</div>)}</ChannelNote>}
    <div className="channel-endpoint-list">
      {rows.map((row, index) => {
        const refs = locked(row);
        const errors = [...(touched && issues.has(index) ? [issues.get(index)!] : []), ...(rowErrors.get(index) ?? [])];
        return <div key={index} className={`channel-endpoint-row${errors.length ? ' has-error' : ''}`}>
          <Input aria-label={t('overview.column.endpointCode')} className="mono" maxLength={64} placeholder={t('endpointEditor.codePlaceholder')} value={row.endpointCode} disabled={refs.length > 0}
            status={errors.length ? 'error' : undefined} onChange={event => update(index, { endpointCode: event.target.value.trim().toUpperCase() })} />
          <Input aria-label="Base URL" className="mono" maxLength={512} placeholder="https://..." value={row.baseUrl}
            status={errors.length ? 'error' : undefined} onChange={event => update(index, { baseUrl: event.target.value })} />
          <Select aria-label={t('overview.column.environment')} value={row.sandbox ? 'SANDBOX' : 'PRODUCTION'} style={{ width: 96 }}
            options={[{ value: 'PRODUCTION', label: t('overview.production') }, { value: 'SANDBOX', label: t('overview.sandbox') }]}
            onChange={value => value === 'SANDBOX' ? confirmSandbox(index) : update(index, { sandbox: false })} />
          <Button type="link" danger disabled={refs.length > 0 || rows.length === 1} onClick={() => {
            setRows(current => current.filter((_, i) => i !== index)); setRowErrors(new Map());
          }}>{t('endpointEditor.delete')}</Button>
          {refs.length > 0 && <div className="channel-endpoint-hint">{t('endpointEditor.referencedHint', { count: refs.length, apis: refs.join(t('list.separator')) })}</div>}
          {errors.map(error => <div key={error} className="channel-endpoint-error">{error}</div>)}
        </div>;
      })}
      <Button type="link" disabled={rows.length >= maxEndpoints} onClick={() => setRows(current => [...current, { endpointCode: '', baseUrl: '', sandbox: false }])}>
        {t('endpointEditor.add')}</Button>
    </div>
    <ChannelNote>
      <div>{t('endpointEditor.note.https')}</div>
      <div>{t('endpointEditor.note.trusted')}</div>
      <div>{t('endpointEditor.note.version')}</div>
    </ChannelNote>
  </CommandModal>;
}
