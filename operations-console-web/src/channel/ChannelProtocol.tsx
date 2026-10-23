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
import { translator } from '../i18n/index';
import { useT } from '../i18n/react';
import type { Channel } from './channel';
import { richText } from '../i18n/richText';
import { protocolSection, protocolSectionLabel, protocolSections, workspaceRoutes } from './channelWorkspace';
import type { ProtocolSection, WorkspaceRoutes } from './channelWorkspace';

interface Props {
  channel: Channel; canManage: boolean; implementationCount: number | undefined; onAuthorizationChanged: () => void;
  onImplementationsChanged: () => void;
}
interface SectionProps { code: string; routes: WorkspaceRoutes; canManage: boolean; onAuthorizationChanged: () => void; onChanged: () => void }

const tProtocol = translator('channel');

/** 原子 API 被哪些标准 API 实现的哪些节点引用。 */
function apiReferences(code: string, apiCode: string, routes: WorkspaceRoutes) {
  return () => session.request(`${integrationPath}/${code}/apis/${apiCode}`, {}, value => decodeIntegrationApi(value, apiCode))
    .then(detail => detail.referencedBy.map<Reference>(item => ({
      label: <>{richText(tProtocol, 'protocol.reference.implementation', { api: <Mono>{item.standardApiCode}</Mono>,
        nodes: <Mono>{item.nodeIds.join(tProtocol('list.separator'))}</Mono> })}</>,
      to: routes.implementation(item.standardApiCode) })));
}

/** 公共脚本被哪些原子 API 引用。 */
function scriptReferences(code: string, scriptCode: string, routes: WorkspaceRoutes) {
  return () => session.request(`${integrationPath}/${code}/scripts/${scriptCode}`, {}, value => decodeScript(value, scriptCode))
    .then(detail => detail.referencedApiCodes.map<Reference>(apiCode => ({ label: <>{richText(tProtocol, 'protocol.reference.api', { api: <Mono>{apiCode}</Mono> })}</>, to: routes.api(apiCode) })));
}

const noReferences = () => Promise.resolve<Reference[]>([]);

/**
 * 原子 API 与公共脚本的数量，用于子导航计数；读取失败时不显示计数。实现数取自渠道详情已读取的实现列表，
 * 不再重复请求：同一路由的 Gateway 并发上限为每人 2，详情与实现列表已各占 1 个。
 */
function useProtocolCounts(code: string, revision: number) {
  const [counts, setCounts] = useState<Partial<Record<ProtocolSection, number>>>({});
  useEffect(() => {
    const controller = new AbortController();
    const total = (path: string) => session.request<{ total: number }>(`${path}?page=1&size=10`, { signal: controller.signal })
      .then(value => (Number.isSafeInteger(value?.total) ? value.total : undefined)).catch(() => undefined);
    void Promise.all([total(`${integrationPath}/${code}/apis`), total(`${integrationPath}/${code}/scripts`)]).then(([apis, scripts]) => {
      if (!controller.signal.aborted) setCounts({ apis, scripts });
    });
    return () => controller.abort();
  }, [code, revision]);
  return counts;
}

/**
 * 协议配置页签（画板 10）：左侧子导航（标准 API 实现 / 原子 API / 公共脚本），右侧为原「接入方」页的列表；
 * 编辑器（字段表、脚本、流程画布、字段映射）原样复用，位于 /channel/channels/:channelId/protocol/:section/:code。
 */
export function ChannelProtocol({ channel, canManage, implementationCount, onAuthorizationChanged, onImplementationsChanged }: Props) {
  const t = useT('channel');
  const location = useLocation(); const navigate = useNavigate();
  const section = protocolSection(new URLSearchParams(location.search).get('section'));
  const routes = workspaceRoutes(channel.channelId);
  const [revision, setRevision] = useState(0);
  const counts: Partial<Record<ProtocolSection, number>> = { ...useProtocolCounts(channel.channelCode, revision), implementations: implementationCount };
  const onChanged = () => { setRevision(value => value + 1); onImplementationsChanged(); };
  const props: SectionProps = { code: channel.channelCode, routes, canManage, onAuthorizationChanged, onChanged };
  return <ExternalApiFrame className="channel-protocol">
    <nav className="channel-subnav" aria-label={t('workspace.tab.protocol')}>
      {protocolSections.map(item => <button type="button" key={item} className={item === section ? 'is-active' : ''}
        onClick={() => void navigate(routes.protocol(item))}>
        <span>{protocolSectionLabel(item)}</span>{counts[item] !== undefined && <span className="channel-subnav-count">{counts[item]}</span>}
      </button>)}
      <p className="channel-subnav-note">{t('protocol.subnavNote')}</p>
    </nav>
    <div className="channel-protocol-body">
      {section === 'implementations' && <ImplementationSection {...props} />}
      {section === 'apis' && <ApiSection key={location.search} {...props} />}
      {section === 'scripts' && <ScriptSection key={location.search} {...props} />}
      <Note items={[t('protocol.note.endpoint'),
        channel.channelType === 'PAYMENT' ? t('protocol.note.payment') : t('protocol.note.service'),
        t('protocol.note.noReversal')]} />
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
  const t = useT('channel');
  const navigate = useNavigate(); const paging = usePagedQuery();
  const decode = useMemo(() => (value: unknown) => decodePage(value, decodeIntegrationApiSummary, item => item.apiCode), []);
  const page = usePaging<IntegrationApiSummary>(`${integrationPath}/${code}/apis`, decode, {}, paging);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <SectionHead title={t('workspace.section.apis')} description={t('protocol.apisDescription')}
      action={canManage && <Button type="primary" onClick={() => void navigate(routes.api('new'))}>{t('protocol.newApi')}</Button>} />
    {page.error ? <LoadFailure title={t('protocol.apisLoadFailed')} failure={page.failure} retry={page.refresh} /> : <div className="xapi-table-wrap">
      <Table<IntegrationApiSummary> rowKey="apiCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => t('grants.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('protocol.apisEmpty')} /> }} columns={[
          { title: t('protocol.column.code'), width: 190, render: (_, row) => <Link className="xapi-link-code" to={routes.api(row.apiCode)}><Mono>{row.apiCode}</Mono></Link> },
          { title: t('form.name'), dataIndex: 'apiName', width: 150, ellipsis: true },
          { title: 'Method', width: 90, render: (_, row) => <Mono>{row.method}</Mono> },
          { title: t('protocol.column.endpointPath'), ellipsis: true, render: (_, row) => <span>{row.endpointCode} <span className="xapi-mono-muted"><Mono>{row.pathTemplate}</Mono></span></span> },
          { title: t('protocol.column.scripts'), width: 80, render: (_, row) => `${row.requestScriptCount}+${row.responseScriptCount}` },
          { title: t('protocol.column.referencedBy'), width: 180, render: (_, row) => row.referencedBy.length ? row.referencedBy.join(t('list.separator')) : '—' },
          { title: t('grants.actions'), width: 110, render: (_, row) => <span className="xapi-ops">
            {canManage && <Link to={routes.api(row.apiCode, 'edit')}>{t('protocol.edit')}</Link>}
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'view', label: t('protocol.details'), onClick: () => void navigate(routes.api(row.apiCode)) },
              ...(canManage ? [{ key: 'delete', label: t('endpointEditor.delete'), danger: true, onClick: () => setDeleting({ title: t('protocol.subject.api'), objectType: 'INTEGRATION_API',
                objectCode: `${code}/${row.apiCode}`, version: row.version, path: `${integrationPath}/${code}/apis/${row.apiCode}`,
                references: apiReferences(code, row.apiCode, routes) }) }] : []),
            ] }}><button type="button" className="xapi-more" aria-label={t('protocol.more')}>···</button></Dropdown></span> },
        ]} /></div>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); onChanged(); }} />}
  </>;
}

function ScriptSection({ code, routes, canManage, onAuthorizationChanged, onChanged }: SectionProps) {
  const t = useT('channel');
  const navigate = useNavigate(); const paging = usePagedQuery();
  const decode = useMemo(() => (value: unknown) => decodePage(value, decodeScriptSummary, item => item.scriptCode), []);
  const page = usePaging<IntegrationScriptSummary>(`${integrationPath}/${code}/scripts`, decode, {}, paging);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <SectionHead title={t('workspace.section.scripts')} description={t('protocol.scriptsDescription')}
      action={canManage && <Button type="primary" onClick={() => void navigate(routes.script('new'))}>{t('protocol.newScript')}</Button>} />
    {page.error ? <LoadFailure title={t('protocol.scriptsLoadFailed')} failure={page.failure} retry={page.refresh} /> : <div className="xapi-table-wrap">
      <Table<IntegrationScriptSummary> rowKey="scriptCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => t('grants.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('protocol.scriptsEmpty')} /> }} columns={[
          { title: t('protocol.column.code'), width: 200, render: (_, row) => <Link className="xapi-link-code" to={routes.script(row.scriptCode)}><Mono>{row.scriptCode}</Mono></Link> },
          { title: t('form.name'), dataIndex: 'scriptName', width: 160, ellipsis: true },
          { title: t('protocol.column.stage'), width: 110, render: (_, row) => <Mono>{row.executionStage}</Mono> },
          { title: t('credential.parameters'), ellipsis: true, render: (_, row) => row.parameterNames.length ? <Mono>{row.parameterNames.join(t('list.separator'))}</Mono> : '—' },
          { title: t('protocol.column.referencingApis'), width: 200, render: (_, row) => row.referencedApiCodes.length ? row.referencedApiCodes.join(t('list.separator')) : '—' },
          { title: t('grants.actions'), width: 110, render: (_, row) => <span className="xapi-ops">
            {canManage && <Link to={routes.script(row.scriptCode, 'edit')}>{t('protocol.edit')}</Link>}
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'view', label: t('protocol.details'), onClick: () => void navigate(routes.script(row.scriptCode)) },
              ...(canManage ? [{ key: 'delete', label: t('endpointEditor.delete'), danger: true, onClick: () => setDeleting({ title: t('protocol.subject.script'), objectType: 'INTEGRATION_SCRIPT',
                objectCode: `${code}/${row.scriptCode}`, version: row.version, path: `${integrationPath}/${code}/scripts/${row.scriptCode}`,
                references: scriptReferences(code, row.scriptCode, routes) }) }] : []),
            ] }}><button type="button" className="xapi-more" aria-label={t('protocol.more')}>···</button></Dropdown></span> },
        ]} /></div>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); onChanged(); }} />}
  </>;
}

function ImplementationSection({ code, routes, canManage, onAuthorizationChanged, onChanged }: SectionProps) {
  const t = useT('channel');
  const navigate = useNavigate();
  const list = useDetail<ImplementationSummary[]>(`${integrationPath}/${code}/implementations`, decodeImplementationList);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  if (list.loading && !list.data) return <Loading />;
  if (!list.data) return <LoadFailure title={t('protocol.implementationsLoadFailed')} failure={list.failure} retry={list.refresh} />;
  const items = list.data;
  const create = canManage && <Button type="primary" onClick={() => setCreating(true)}>{t('protocol.newImplementation')}</Button>;
  return <>
    <SectionHead title={t('workspace.section.implementations')} description={t('protocol.implementationsDescription')} action={create} />
    {items.length === 0 ? <div className="xapi-panel xapi-state"><span className="xapi-state-icon">⛓</span><h3>{t('protocol.implementationsEmpty')}</h3>
      <p>{t('protocol.implementationsEmptyHint')}</p></div>
      : <div className="xapi-table-wrap"><Table<ImplementationSummary> rowKey="standardApiCode" tableLayout="fixed" dataSource={items} pagination={false} columns={[
        { title: t('grants.standardApi'), width: 240, render: (_, row) => <Link className="xapi-link-code" to={routes.implementation(row.standardApiCode)}><Mono>{row.standardApiCode}</Mono>
          <span className="xapi-mono-muted"> · {row.standardApiName}</span></Link> },
        { title: t('protocol.column.flow'), render: (_, row) => <span>{t('protocol.flowSummary', { nodes: row.nodeCount, returns: row.returnCount })}{row.initializerIds.length
          ? richText(t, 'protocol.initializers', { ids: <Mono>{row.initializerIds.join(t('list.separator'))}</Mono> }) : null}</span> },
        { title: t('protocol.column.budget'), width: 110, render: (_, row) => <Mono>{row.timeoutMs} ms</Mono> },
        { title: t('grants.version'), width: 70, render: (_, row) => `v${row.version}` },
        { title: t('protocol.column.updated'), width: 150, render: (_, row) => formatTime(row.updateTime) },
        { title: t('grants.actions'), width: 170, render: (_, row) => <span className="xapi-ops">
          <Link to={routes.implementation(row.standardApiCode, canManage ? 'edit' : undefined)}>{canManage ? t('protocol.editFlow') : t('protocol.viewFlow')}</Link>
          <Link to={routes.tab('verify')}>{t('protocol.verify')}</Link>
          {canManage && <Dropdown trigger={['click']} menu={{ items: [
            { key: 'delete', label: t('endpointEditor.delete'), danger: true, onClick: () => setDeleting({ title: t('protocol.subject.implementation'), objectType: 'INTEGRATION_IMPLEMENTATION',
              objectCode: `${code}/${row.standardApiCode}`, version: row.version, path: `${integrationPath}/${code}/implementations/${row.standardApiCode}`,
              references: noReferences }) },
          ] }}><button type="button" className="xapi-more" aria-label={t('protocol.more')}>···</button></Dropdown>}</span> },
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
  const t = useT('channel');
  const notify = useErrorNotification();
  const [all, setAll] = useState<StandardApiSummary[]>();
  const [selected, setSelected] = useState<string>();
  useEffect(() => {
    const abort = new AbortController();
    loadAllPages(standardApiPath, value => decodePage(value, decodeStandardApiSummary, item => item.standardApiCode), abort.signal)
      .then(items => { if (!abort.signal.aborted) { setAll(items); setSelected(availableStandardApis(items, implemented)[0]?.standardApiCode); } })
      .catch(error => { if (!abort.signal.aborted) { setAll([]); notify(error, t('protocol.standardApisLoadFailed')); } });
    return () => abort.abort();
    // t 只用于失败通知标题，不应触发重新读取
  }, [notify, implemented]); // eslint-disable-line react-hooks/exhaustive-deps
  const available = new Set((all ? availableStandardApis(all, implemented) : []).map(item => item.standardApiCode));
  return <Modal open centered width={560} title={t('protocol.newImplementationTitle')} okText={t('protocol.createAndEdit')} okButtonProps={{ disabled: !selected }} onCancel={onClose}
    onOk={() => selected && onPick(selected)}>
    <p className="xapi-desc">{t('protocol.newImplementationHint')}</p>
    {all === undefined ? <Loading /> : all.length === 0
      ? <p className="xapi-desc">{richText(t, 'protocol.noStandardApi', { link: <Link to={standardApiRoute}>{t('protocol.goToStandardApis')}</Link> })}</p>
      : <div className="xapi-choice-list" role="radiogroup">{all.map(item => {
        const disabled = !available.has(item.standardApiCode);
        return <button type="button" key={item.standardApiCode} role="radio" aria-checked={selected === item.standardApiCode} disabled={disabled}
          className={`xapi-choice${selected === item.standardApiCode ? ' is-selected' : ''}`} onClick={() => setSelected(item.standardApiCode)}>
          <Mono>{item.standardApiCode}</Mono><span>{item.standardApiName}{disabled && t('protocol.alreadyImplemented')}</span></button>;
      })}</div>}
  </Modal>;
}
