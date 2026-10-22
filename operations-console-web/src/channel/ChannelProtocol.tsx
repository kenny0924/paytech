import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Button, Dropdown, Empty, Modal, Table } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { DeleteDialog, Loading, LoadFailure, loadAllPages, useDetail } from '../externalApi/externalApiHooks';
import type { DeleteTarget, Reference } from '../externalApi/externalApiHooks';
import {
  availableStandardApis, decodeImplementationList, decodeIntegrationApiSummary, decodePage, decodeScriptSummary, decodeStandardApiSummary,
  integrationPath, listSizes, replaceQuery, standardApiPath, standardApiRoute,
} from '../externalApi/externalApiModel';
import type { ImplementationSummary, IntegrationApiSummary, IntegrationScriptSummary, StandardApiSummary } from '../externalApi/externalApiModel';
import { decodeScript } from '../externalApi/editorModel';
import { decodeIntegrationApi } from '../externalApi/integrationApiModel';
import { ExternalApiFrame, Mono, Note, formatTime } from '../externalApi/ui';
import type { Channel } from './channel';
import { protocolSection, protocolSectionLabels, protocolSections, workspaceRoutes } from './channelWorkspace';
import type { ProtocolSection, WorkspaceRoutes } from './channelWorkspace';

interface Props { channel: Channel; canManage: boolean; onAuthorizationChanged: () => void }
interface SectionProps { code: string; routes: WorkspaceRoutes; canManage: boolean; onAuthorizationChanged: () => void; onChanged: () => void }

/** 原子 API 被哪些标准 API 实现的哪些节点引用。 */
function apiReferences(code: string, apiCode: string, routes: WorkspaceRoutes) {
  return () => session.request(`${integrationPath}/${code}/apis/${apiCode}`, {}, value => decodeIntegrationApi(value, apiCode))
    .then(detail => detail.referencedBy.map<Reference>(item => ({
      label: <>标准 API 实现 · <Mono>{item.standardApiCode}</Mono> / 节点 <Mono>{item.nodeIds.join('、')}</Mono></>,
      to: routes.implementation(item.standardApiCode) })));
}

/** 公共脚本被哪些原子 API 引用。 */
function scriptReferences(code: string, scriptCode: string, routes: WorkspaceRoutes) {
  return () => session.request(`${integrationPath}/${code}/scripts/${scriptCode}`, {}, value => decodeScript(value, scriptCode))
    .then(detail => detail.referencedApiCodes.map<Reference>(apiCode => ({ label: <>原子 API · <Mono>{apiCode}</Mono></>, to: routes.api(apiCode) })));
}

const noReferences = () => Promise.resolve<Reference[]>([]);

/** 本渠道协议对象的数量，用于子导航计数；读取失败时不显示计数。 */
function useProtocolCounts(code: string, revision: number) {
  const [counts, setCounts] = useState<Partial<Record<ProtocolSection, number>>>({});
  useEffect(() => {
    const controller = new AbortController();
    const total = (path: string) => session.request<{ total: number }>(`${path}?page=1&size=10`, { signal: controller.signal })
      .then(value => (Number.isSafeInteger(value?.total) ? value.total : undefined)).catch(() => undefined);
    void Promise.all([
      session.request(`${integrationPath}/${code}/implementations`, { signal: controller.signal }, decodeImplementationList)
        .then(items => items.length).catch(() => undefined),
      total(`${integrationPath}/${code}/apis`), total(`${integrationPath}/${code}/scripts`),
    ]).then(([implementations, apis, scripts]) => {
      if (!controller.signal.aborted) setCounts({ implementations, apis, scripts });
    });
    return () => controller.abort();
  }, [code, revision]);
  return counts;
}

/**
 * 协议配置页签（画板 10）：左侧子导航（标准 API 实现 / 原子 API / 公共脚本），右侧为原「接入方」页的列表；
 * 编辑器（字段表、脚本、流程画布、字段映射）原样复用，位于 /channel/channels/:channelId/protocol/:section/:code。
 */
export function ChannelProtocol({ channel, canManage, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate();
  const section = protocolSection(new URLSearchParams(location.search).get('section'));
  const routes = workspaceRoutes(channel.channelId);
  const [revision, setRevision] = useState(0);
  const counts = useProtocolCounts(channel.channelCode, revision);
  const props: SectionProps = { code: channel.channelCode, routes, canManage, onAuthorizationChanged, onChanged: () => setRevision(value => value + 1) };
  return <ExternalApiFrame className="channel-protocol">
    <nav className="channel-subnav" aria-label="协议配置">
      {protocolSections.map(item => <button type="button" key={item} className={item === section ? 'is-active' : ''}
        onClick={() => void navigate(routes.protocol(item))}>
        <span>{protocolSectionLabels[item]}</span>{counts[item] !== undefined && <span className="channel-subnav-count">{counts[item]}</span>}
      </button>)}
      <p className="channel-subnav-note">原「接入方」页的三个页签迁入这里；编辑器原样复用。</p>
    </nav>
    <div className="channel-protocol-body">
      {section === 'implementations' && <ImplementationSection {...props} />}
      {section === 'apis' && <ApiSection key={location.search} {...props} />}
      {section === 'scripts' && <ScriptSection key={location.search} {...props} />}
      <Note items={['原子 API 的出站地址从「概览 › 出站地址」中选择，不再单独维护接入方。',
        channel.channelType === 'PAYMENT' ? '支付渠道的标准 API 实现由支付入口（仅 Payment）执行。' : '服务渠道的标准 API 实现由通用入口执行，调用方须有 ACTIVE 授权。',
        '流程不编排冲正、撤销、退款等资金反向操作（人工评审）。']} />
    </div>
  </ExternalApiFrame>;
}

function SectionHead({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="xapi-toolbar"><div><h2 className="xapi-section-title">{title}</h2><span className="xapi-desc">{description}</span></div>{action}</div>;
}

/** 列表分页写入地址栏，保留 tab / section。 */
function usePagedQuery() {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  return {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) }, notifyOnError: false,
    onQueryChange: (next: { paging: { page: number; size: number } }) =>
      void navigate(replaceQuery(location.search, { page: String(next.paging.page), size: String(next.paging.size) })),
  };
}

function ApiSection({ code, routes, canManage, onAuthorizationChanged, onChanged }: SectionProps) {
  const navigate = useNavigate(); const paging = usePagedQuery();
  const decode = useMemo(() => (value: unknown) => decodePage(value, decodeIntegrationApiSummary, item => item.apiCode), []);
  const page = usePaging<IntegrationApiSummary>(`${integrationPath}/${code}/apis`, decode, {}, paging);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <SectionHead title="原子 API" description="每个原子 API 是一次外部协议操作；被流程节点引用时不能删除。"
      action={canManage && <Button type="primary" onClick={() => void navigate(routes.api('new'))}>新建原子 API</Button>} />
    {page.error ? <LoadFailure title="原子 API 加载失败" failure={page.failure} retry={page.refresh} /> : <div className="xapi-table-wrap">
      <Table<IntegrationApiSummary> rowKey="apiCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无原子 API" /> }} columns={[
          { title: '编码', width: 190, render: (_, row) => <Link className="xapi-link-code" to={routes.api(row.apiCode)}><Mono>{row.apiCode}</Mono></Link> },
          { title: '名称', dataIndex: 'apiName', width: 150, ellipsis: true },
          { title: 'Method', width: 90, render: (_, row) => <Mono>{row.method}</Mono> },
          { title: '地址 + 路径', ellipsis: true, render: (_, row) => <span>{row.endpointCode} <span className="xapi-mono-muted"><Mono>{row.pathTemplate}</Mono></span></span> },
          { title: '脚本', width: 80, render: (_, row) => `${row.requestScriptCount}+${row.responseScriptCount}` },
          { title: '被引用', width: 180, render: (_, row) => row.referencedBy.length ? row.referencedBy.join('、') : '—' },
          { title: '操作', width: 110, render: (_, row) => <span className="xapi-ops">
            {canManage && <Link to={routes.api(row.apiCode, 'edit')}>编辑</Link>}
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'view', label: '详情', onClick: () => void navigate(routes.api(row.apiCode)) },
              ...(canManage ? [{ key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '原子 API', objectType: 'INTEGRATION_API',
                objectCode: `${code}/${row.apiCode}`, version: row.version, path: `${integrationPath}/${code}/apis/${row.apiCode}`,
                references: apiReferences(code, row.apiCode, routes) }) }] : []),
            ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown></span> },
        ]} /></div>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); onChanged(); }} />}
  </>;
}

function ScriptSection({ code, routes, canManage, onAuthorizationChanged, onChanged }: SectionProps) {
  const navigate = useNavigate(); const paging = usePagedQuery();
  const decode = useMemo(() => (value: unknown) => decodePage(value, decodeScriptSummary, item => item.scriptCode), []);
  const page = usePaging<IntegrationScriptSummary>(`${integrationPath}/${code}/scripts`, decode, {}, paging);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <SectionHead title="公共脚本" description="同一渠道内可复用的请求或响应阶段脚本；被原子 API 引用时不能删除。"
      action={canManage && <Button type="primary" onClick={() => void navigate(routes.script('new'))}>新建脚本</Button>} />
    {page.error ? <LoadFailure title="公共脚本加载失败" failure={page.failure} retry={page.refresh} /> : <div className="xapi-table-wrap">
      <Table<IntegrationScriptSummary> rowKey="scriptCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公共脚本" /> }} columns={[
          { title: '编码', width: 200, render: (_, row) => <Link className="xapi-link-code" to={routes.script(row.scriptCode)}><Mono>{row.scriptCode}</Mono></Link> },
          { title: '名称', dataIndex: 'scriptName', width: 160, ellipsis: true },
          { title: '阶段', width: 110, render: (_, row) => <Mono>{row.executionStage}</Mono> },
          { title: '参数', ellipsis: true, render: (_, row) => row.parameterNames.length ? <Mono>{row.parameterNames.join('、')}</Mono> : '—' },
          { title: '引用 API', width: 200, render: (_, row) => row.referencedApiCodes.length ? row.referencedApiCodes.join('、') : '—' },
          { title: '操作', width: 110, render: (_, row) => <span className="xapi-ops">
            {canManage && <Link to={routes.script(row.scriptCode, 'edit')}>编辑</Link>}
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'view', label: '详情', onClick: () => void navigate(routes.script(row.scriptCode)) },
              ...(canManage ? [{ key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '公共脚本', objectType: 'INTEGRATION_SCRIPT',
                objectCode: `${code}/${row.scriptCode}`, version: row.version, path: `${integrationPath}/${code}/scripts/${row.scriptCode}`,
                references: scriptReferences(code, row.scriptCode, routes) }) }] : []),
            ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown></span> },
        ]} /></div>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); onChanged(); }} />}
  </>;
}

function ImplementationSection({ code, routes, canManage, onAuthorizationChanged, onChanged }: SectionProps) {
  const navigate = useNavigate();
  const list = useDetail<ImplementationSummary[]>(`${integrationPath}/${code}/implementations`, decodeImplementationList);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  if (list.loading && !list.data) return <Loading />;
  if (!list.data) return <LoadFailure title="标准 API 实现加载失败" failure={list.failure} retry={list.refresh} />;
  const items = list.data;
  const create = canManage && <Button type="primary" onClick={() => setCreating(true)}>新增实现</Button>;
  return <>
    <SectionHead title="标准 API 实现" description="每个标准 API 在本渠道有一个实现流程；删除前需先停用对应调用授权。" action={create} />
    {items.length === 0 ? <div className="xapi-panel xapi-state"><span className="xapi-state-icon">⛓</span><h3>还没有标准 API 实现</h3>
      <p>选择一个标准 API，用本渠道的原子 API 编排流程。</p></div>
      : <div className="xapi-table-wrap"><Table<ImplementationSummary> rowKey="standardApiCode" tableLayout="fixed" dataSource={items} pagination={false} columns={[
        { title: '标准 API', width: 240, render: (_, row) => <Link className="xapi-link-code" to={routes.implementation(row.standardApiCode)}><Mono>{row.standardApiCode}</Mono>
          <span className="xapi-mono-muted"> · {row.standardApiName}</span></Link> },
        { title: '流程', render: (_, row) => <span>{row.nodeCount} 个节点 · {row.returnCount} 个出口{row.initializerIds.length ? <> · 初始化 <Mono>{row.initializerIds.join('、')}</Mono></> : null}</span> },
        { title: '总预算', width: 110, render: (_, row) => <Mono>{row.timeoutMs} ms</Mono> },
        { title: '版本', width: 70, render: (_, row) => `v${row.version}` },
        { title: '更新', width: 150, render: (_, row) => formatTime(row.updateTime) },
        { title: '操作', width: 170, render: (_, row) => <span className="xapi-ops">
          <Link to={routes.implementation(row.standardApiCode, canManage ? 'edit' : undefined)}>{canManage ? '编辑流程' : '查看流程'}</Link>
          <Link to={routes.tab('verify')}>验证</Link>
          {canManage && <Dropdown trigger={['click']} menu={{ items: [
            { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '标准 API 实现', objectType: 'INTEGRATION_IMPLEMENTATION',
              objectCode: `${code}/${row.standardApiCode}`, version: row.version, path: `${integrationPath}/${code}/implementations/${row.standardApiCode}`,
              references: noReferences }) },
          ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown>}</span> },
      ]} /></div>}
    {creating && <NewImplementationDialog implemented={items} onClose={() => setCreating(false)}
      onPick={standardApiCode => void navigate(routes.implementation(standardApiCode, 'create'))} />}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); list.refresh(); onChanged(); }} />}
  </>;
}

/** 新建标准 API 实现：一步选择尚未实现的标准 API（已实现的置灰），创建后进入流程编辑。 */
function NewImplementationDialog({ implemented, onClose, onPick }: {
  implemented: ImplementationSummary[]; onClose: () => void; onPick: (standardApiCode: string) => void;
}) {
  const notify = useErrorNotification();
  const [all, setAll] = useState<StandardApiSummary[]>();
  const [selected, setSelected] = useState<string>();
  useEffect(() => {
    const abort = new AbortController();
    loadAllPages(standardApiPath, value => decodePage(value, decodeStandardApiSummary, item => item.standardApiCode), abort.signal)
      .then(items => { if (!abort.signal.aborted) { setAll(items); setSelected(availableStandardApis(items, implemented)[0]?.standardApiCode); } })
      .catch(error => { if (!abort.signal.aborted) { setAll([]); notify(error, '标准 API 加载失败'); } });
    return () => abort.abort();
  }, [notify, implemented]);
  const available = new Set((all ? availableStandardApis(all, implemented) : []).map(item => item.standardApiCode));
  return <Modal open centered width={560} title="新增标准 API 实现" okText="创建并编辑流程" okButtonProps={{ disabled: !selected }} onCancel={onClose}
    onOk={() => selected && onPick(selected)}>
    <p className="xapi-desc">选择本渠道尚未实现的标准 API，创建后进入流程编辑，默认“开始 → 待配置出口（ERROR）”。</p>
    {all === undefined ? <Loading /> : all.length === 0
      ? <p className="xapi-desc">还没有标准 API，<Link to={standardApiRoute}>前往标准 API</Link> 新建。</p>
      : <div className="xapi-choice-list" role="radiogroup">{all.map(item => {
        const disabled = !available.has(item.standardApiCode);
        return <button type="button" key={item.standardApiCode} role="radio" aria-checked={selected === item.standardApiCode} disabled={disabled}
          className={`xapi-choice${selected === item.standardApiCode ? ' is-selected' : ''}`} onClick={() => setSelected(item.standardApiCode)}>
          <Mono>{item.standardApiCode}</Mono><span>{item.standardApiName}{disabled && '（已实现）'}</span></button>;
      })}</div>}
  </Modal>;
}
