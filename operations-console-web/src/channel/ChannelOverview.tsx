import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { App, Button, Input, Select, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { loadAllPages } from '../externalApi/externalApiHooks';
import { decodeIntegrationApiSummary, decodePage, integrationPath } from '../externalApi/externalApiModel';
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
      <div className="channel-section-head"><h2>出站地址</h2><span className="channel-cell-meta">{channel.endpoints.length} / {maxEndpoints}</span>
        <span className="channel-spacer" />
        {canManage && channel.status !== 'CLOSED' && <Button onClick={onEditEndpoints}>编辑出站地址</Button>}</div>
      <Table<ChannelEndpoint> rowKey="endpointCode" size="middle" pagination={false} dataSource={channel.endpoints}
        locale={{ emptyText: '未配置出站地址；配置后渠道才能激活' }} columns={[
          { title: '地址编码', width: 200, render: (_, row) => <span className="mono">{row.endpointCode}</span> },
          { title: 'Base URL', render: (_, row) => <span className="mono">{row.baseUrl}</span> },
          { title: '环境', width: 90, render: (_, row) => row.sandbox ? <Tag color="blue">沙箱</Tag> : <Tag>生产</Tag> },
          { title: '引用原子 API', width: 140, render: (_, row) => {
            if (!references) return '—';
            const apis = references.get(row.endpointCode) ?? [];
            return apis.length ? <span title={apis.join('、')}>{apis.length}</span> : '0';
          } },
        ]} />
      <ChannelNote>出站地址只允许 https；解析结果为回环、私网、链路本地或保留地址的会被拦截（不发出请求，结果 NOT_SENT）。本机与 mor-test 的 Mock 地址经 trusted-networks 放行。</ChannelNote>
      <h2>描述</h2>
      <p className="channel-description">{channel.description || <span className="channel-cell-meta">未填写</span>}</p>
    </section>
    <aside className="channel-panel channel-overview-side">
      <h2>激活条件</h2>
      <ul className="channel-checks">
        <Check ok={channel.endpoints.length > 0} label="出站地址已配置" note="必需" />
        {service
          ? <Check ok={unknown(accounts) ? null : !!credential?.configured} label={credential?.configured ? `凭据已配置（…${credential.vaultTokenSuffix ?? ''}）` : '凭据已配置'} note="执行需要" />
          : <Check ok={unknown(accounts) ? null : activeAccounts.length > 0} label={`ACTIVE 技术账号 ${activeAccounts.length} 个`} note="执行需要" />}
        <Check ok={unknown(implemented) ? null : (implemented?.length ?? 0) > 0} label={`标准 API 实现 ${implemented?.length ?? '—'} 个`} note="执行需要" />
        {service && disabledGrants > 0 && <Check ok={null} warn label={`DISABLED 授权 ${disabledGrants} 条`} note="不影响激活" />}
      </ul>
      <h2>关联</h2>
      <dl className="channel-kv-list">
        {service ? <>
          <Related label="调用授权" value={grants ? <Link to={routes.tab('grants')}>{activeGrants} ACTIVE / {disabledGrants} DISABLED</Link> : '—'} />
          <Related label="调用入口" value="通用入口（需授权）" />
          <Related label="MID / 能力 / 成本" value="不适用" />
        </> : <>
          <Related label="MID" value={midCount === null ? '—' : <Link to={`${channelRoutes.mids}?channelId=${channel.channelId}`}>{midCount} 个</Link>} />
          <Related label="成本方案" value={<Link to={channelRoutes.costPlans}>按渠道查看</Link>} />
          <Related label="调用入口" value="支付入口（仅 Payment）" />
        </>}
        <Related label="沙箱验证" value={<Link to={routes.tab('verify')}>查看用例与最近运行</Link>} />
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
      value => decodeChannel(value, channel.channelId), '出站地址保存失败');
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
  const confirmSandbox = (index: number) => modal.confirm({ title: '标记为沙箱地址？', centered: true, okText: '确认是沙箱', cancelText: '取消',
    content: '标记后，沙箱验证运行会真实调用这个地址。请确认它是渠道的沙箱 / 测试环境；把生产地址标记为沙箱会产生真实交易。',
    onOk: () => update(index, { sandbox: true }) });
  return <CommandModal title={<span>编辑出站地址 <span className="channel-cell-meta">{channel.channelCode} · 配置版本 v{channel.version}</span></span>}
    command={{ ...command, formError: null }} okText="保存" width={860} onOk={() => void save()} onClose={onClose} onReload={onReload}>
    {general.length > 0 && <ChannelNote tone="warning">{general.map(item => <div key={item}>{item}</div>)}</ChannelNote>}
    <div className="channel-endpoint-list">
      {rows.map((row, index) => {
        const refs = locked(row);
        const errors = [...(touched && issues.has(index) ? [issues.get(index)!] : []), ...(rowErrors.get(index) ?? [])];
        return <div key={index} className={`channel-endpoint-row${errors.length ? ' has-error' : ''}`}>
          <Input aria-label="地址编码" className="mono" maxLength={64} placeholder="地址编码，如 NI_API" value={row.endpointCode} disabled={refs.length > 0}
            status={errors.length ? 'error' : undefined} onChange={event => update(index, { endpointCode: event.target.value.trim().toUpperCase() })} />
          <Input aria-label="Base URL" className="mono" maxLength={512} placeholder="https://..." value={row.baseUrl}
            status={errors.length ? 'error' : undefined} onChange={event => update(index, { baseUrl: event.target.value })} />
          <Select aria-label="环境" value={row.sandbox ? 'SANDBOX' : 'PRODUCTION'} style={{ width: 96 }}
            options={[{ value: 'PRODUCTION', label: '生产' }, { value: 'SANDBOX', label: '沙箱' }]}
            onChange={value => value === 'SANDBOX' ? confirmSandbox(index) : update(index, { sandbox: false })} />
          <Button type="link" danger disabled={refs.length > 0 || rows.length === 1} onClick={() => {
            setRows(current => current.filter((_, i) => i !== index)); setRowErrors(new Map());
          }}>删除</Button>
          {refs.length > 0 && <div className="channel-endpoint-hint">被 {refs.length} 个原子 API 引用（{refs.join('、')}），不能删除或改编码；可修改 URL。</div>}
          {errors.map(error => <div key={error} className="channel-endpoint-error">{error}</div>)}
        </div>;
      })}
      <Button type="link" disabled={rows.length >= maxEndpoints} onClick={() => setRows(current => [...current, { endpointCode: '', baseUrl: '', sandbox: false }])}>
        + 增加地址</Button>
    </div>
    <ChannelNote>
      <div>• 只允许 https；保存时校验格式与 IP 字面量，执行时还会在连接前重新解析并固定地址。</div>
      <div>• 本机与 mor-test 的 Mock 地址由 channel-connector 配置 trusted-networks 放行，页面不提供绕过开关。</div>
      <div>• 保存递增配置版本；并发修改时提示「配置已被他人修改」并保留本次输入。</div>
    </ChannelNote>
  </CommandModal>;
}
