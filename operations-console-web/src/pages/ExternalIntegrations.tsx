import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Dropdown, Empty, Form, Input, Modal, Table, Tabs } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ReasonDialog, useReasonFlow } from '../externalApi/EditorParts';
import {
  ConflictBanner, DeleteDialog, Loading, LoadFailure, hasButton, loadAllPages, useCopyDraft, useDetail, useEditorSave,
} from '../externalApi/externalApiHooks';
import type { DeleteTarget, Reference } from '../externalApi/externalApiHooks';
import {
  availableStandardApis, cleanFilters, codePattern, decodeImplementationList, decodeIntegration, decodeIntegrationApiSummary,
  decodeIntegrationSummary, decodeMutation, decodePage, decodeScriptSummary, decodeStandardApiSummary, errorRows,
  integrationPath, integrationRoute, listSizes, manageIntegrations, replaceQuery, standardApiPath, standardApiRoute,
} from '../externalApi/externalApiModel';
import type {
  Endpoint, ImplementationSummary, IntegrationApiSummary, IntegrationDetail, IntegrationScriptSummary, IntegrationSummary,
  StandardApiSummary,
} from '../externalApi/externalApiModel';
import { decodeScript } from '../externalApi/editorModel';
import { decodeIntegrationApi } from '../externalApi/integrationApiModel';
import { FlowEditor } from '../externalApi/FlowEditor';
import { IntegrationApiEditor } from '../externalApi/IntegrationApiEditor';
import { ScriptEditor } from '../externalApi/ScriptEditor';
import { ExternalApiFrame, Field, Mono, Note, PageHeader, Panel, ReadOnlyValue, counted, formatTime } from '../externalApi/ui';
import type { Crumb } from '../externalApi/ui';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Tab = 'basic' | 'apis' | 'scripts' | 'implementations';
const tabKeys: Tab[] = ['basic', 'apis', 'scripts', 'implementations'];
const childKinds = ['apis', 'scripts', 'implementations'];
const summaryPage = (value: unknown) => decodePage(value, decodeIntegrationSummary, item => item.integrationCode);
const rootCrumbs: Crumb[] = [{ label: '外部 API' }, { label: '接入方', to: integrationRoute }];
const noManage = '只读 · 无“外部 API 管理”权限';

/** 接入方仍有的下级配置：删除前读取，有下级配置时不能删除。 */
function integrationReferences(code: string) {
  return () => session.request(`${integrationPath}/${code}`, {}, value => decodeIntegration(value, code)).then(detail => {
    const result: Reference[] = [];
    const base = `${integrationRoute}/${code}`;
    if (detail.apiCount) result.push({ label: `原子 API · ${detail.apiCount} 个`, to: `${base}?tab=apis` });
    if (detail.scriptCount) result.push({ label: `公共脚本 · ${detail.scriptCount} 个`, to: `${base}?tab=scripts` });
    if (detail.implementationCount) result.push({ label: `标准 API 实现 · ${detail.implementationCount} 个`, to: `${base}?tab=implementations` });
    return result;
  });
}

/** 原子 API 被哪些标准 API 实现的哪些节点引用。 */
function apiReferences(code: string, apiCode: string) {
  return () => session.request(`${integrationPath}/${code}/apis/${apiCode}`, {}, value => decodeIntegrationApi(value, apiCode))
    .then(detail => detail.referencedBy.map<Reference>(item => ({
      label: <>标准 API 实现 · <Mono>{item.standardApiCode}</Mono> / 节点 <Mono>{item.nodeIds.join('、')}</Mono></>,
      to: `${integrationRoute}/${code}/implementations/${item.standardApiCode}` })));
}

/** 公共脚本被哪些原子 API 引用。 */
function scriptReferences(code: string, scriptCode: string) {
  return () => session.request(`${integrationPath}/${code}/scripts/${scriptCode}`, {}, value => decodeScript(value, scriptCode))
    .then(detail => detail.referencedApiCodes.map<Reference>(apiCode => ({
      label: <>原子 API · <Mono>{apiCode}</Mono></>, to: `${integrationRoute}/${code}/apis/${apiCode}` })));
}

const noReferences = () => Promise.resolve<Reference[]>([]);

/**
 * 接入方：列表、新建（/new）、工作区（/:code?tab=）。原子 API、脚本与流程的编辑页位于 /:code/apis|scripts|implementations/:childCode。
 */
export function ExternalIntegrations(props: Props) {
  const location = useLocation();
  const parts = location.pathname.slice(integrationRoute.length).replace(/^\/|\/$/g, '').split('/').filter(Boolean);
  let page;
  if (parts.length === 0) page = <IntegrationList key={location.search} {...props} />;
  else if (parts.length === 1 && parts[0] === 'new') page = <IntegrationCreate {...props} />;
  else if (!codePattern.test(parts[0])) page = <LoadFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  else if (parts.length === 1) page = <IntegrationWorkspace key={parts[0]} {...props} code={parts[0]} />;
  else if (parts.length === 3 && childKinds.includes(parts[1]) && (parts[2] === 'new' && parts[1] !== 'implementations' || codePattern.test(parts[2]))) {
    const child = parts[2] === 'new' ? null : parts[2];
    if (parts[1] === 'apis') page = <IntegrationApiEditor key={location.pathname} {...props} integrationCode={parts[0]} apiCode={child} />;
    else if (parts[1] === 'scripts') page = <ScriptEditor key={location.pathname} {...props} integrationCode={parts[0]} scriptCode={child} />;
    else return <FlowEditor key={location.pathname + location.search} {...props} integrationCode={parts[0]} standardApiCode={parts[2]} />;
  } else page = <LoadFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return <ExternalApiFrame>{page}</ExternalApiFrame>;
}

function IntegrationList({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const [form] = Form.useForm(); const canManage = hasButton(resources, manageIntegrations);
  const initial = cleanFilters({ keyword: query.get('keyword') ?? undefined });
  const page = usePaging<IntegrationSummary>(integrationPath, summaryPage, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) }, notifyOnError: false,
    onQueryChange: next => void navigate(replaceQuery('', { ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <PageHeader crumbs={[rootCrumbs[0], { label: '接入方' }]} title="接入方"
      actions={canManage && <Button type="primary" onClick={() => void navigate(`${integrationRoute}/new`)}>新建接入方</Button>} />
    <Form form={form} initialValues={initial} onFinish={values => page.applyFilters(cleanFilters(values))} className="xapi-filter">
      <Field label="编码 / 名称"><Form.Item name="keyword" noStyle><Input allowClear maxLength={64} placeholder="输入编码或名称" /></Form.Item></Field>
      <Button htmlType="submit">查询</Button>
      {initial.keyword && <button type="button" className="xapi-link" onClick={() => { form.resetFields(); page.applyFilters({}); }}>清除条件</button>}
    </Form>
    {page.error ? <LoadFailure title="接入方加载失败" failure={page.failure} retry={page.refresh} keptFilters /> : <div className="xapi-table-wrap">
      <Table<IntegrationSummary> rowKey="integrationCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={initial.keyword ? '没有符合条件的接入方' : '暂无接入方'} /> }}
        columns={[
          { title: '编码', width: 170, render: (_, row) => <Link className="xapi-link-code" to={`${integrationRoute}/${row.integrationCode}`}><Mono>{row.integrationCode}</Mono></Link> },
          { title: '名称', dataIndex: 'integrationName', width: 160, ellipsis: true },
          { title: '出站地址', dataIndex: 'endpointCount', width: 90 },
          { title: '原子 API', dataIndex: 'apiCount', width: 90 },
          { title: '公共脚本', dataIndex: 'scriptCount', width: 90 },
          { title: '标准 API 实现', render: (_, row) => row.implementedStandardApis.length ? row.implementedStandardApis.map((item, index) =>
            <span key={item.standardApiCode}>{index > 0 && '、'}<Link to={`${integrationRoute}/${row.integrationCode}/implementations/${item.standardApiCode}`}>
              {item.standardApiCode}</Link></span>) : '—' },
          { title: '更新时间', width: 150, render: (_, row) => formatTime(row.updateTime) },
          { title: '操作', width: 70, render: (_, row) => <Dropdown trigger={['click']} menu={{ items: [
            { key: 'view', label: '详情', onClick: () => void navigate(`${integrationRoute}/${row.integrationCode}`) },
            ...(canManage ? [
              { key: 'edit', label: '编辑', onClick: () => void navigate(`${integrationRoute}/${row.integrationCode}?tab=basic&mode=edit`) },
              { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '接入方', objectType: 'INTEGRATION',
                objectCode: row.integrationCode, version: row.version, path: `${integrationPath}/${row.integrationCode}`,
                references: integrationReferences(row.integrationCode) }) },
            ] : []),
          ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown> },
        ]} /></div>}
    <Note items={['出站地址数是本环境维护的地址数量；实际出站仍受部署网络白名单限制。',
      '删除接入方前须先删除其原子 API、公共脚本与标准 API 实现；删除时会列出仍存在的下级配置。']} />
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); }} />}
  </>;
}

function IntegrationCreate({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  if (!hasButton(resources, manageIntegrations)) return <Alert type="warning" showIcon title="当前没有接入方维护权限" />;
  return <IntegrationBasicForm crumbs={[...rootCrumbs, { label: '新建' }]} onAuthorizationChanged={onAuthorizationChanged}
    onSaved={code => void navigate(`${integrationRoute}/${code}`, { replace: true })} />;
}

function IntegrationWorkspace({ resources, onAuthorizationChanged, code }: Props & { code: string }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const tab: Tab = tabKeys.includes(query.get('tab') as Tab) ? query.get('tab') as Tab : 'basic';
  const canManage = hasButton(resources, manageIntegrations);
  const decode = useMemo(() => (value: unknown) => decodeIntegration(value, code), [code]);
  const detail = useDetail<IntegrationDetail>(`${integrationPath}/${code}`, decode);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure && !detail.data || !detail.data) return <LoadFailure title="接入方加载失败" failure={detail.failure} retry={detail.refresh} />;
  const integration = detail.data;
  const editing = canManage && tab === 'basic' && query.get('mode') === 'edit';
  if (editing) {
    return <IntegrationBasicForm key={integration.version} detail={integration} onAuthorizationChanged={onAuthorizationChanged}
      crumbs={[...rootCrumbs, { label: code, to: `${integrationRoute}/${code}` }, { label: '编辑' }]} onReload={detail.refresh}
      onSaved={() => { detail.refresh(); void navigate(replaceQuery('', { tab: 'basic' }), { replace: true }); }} />;
  }
  const labels: Record<Tab, string> = { basic: '基本信息', apis: counted('原子 API', integration.apiCount),
    scripts: counted('公共脚本', integration.scriptCount), implementations: counted('标准 API 实现', integration.implementationCount) };
  return <>
    <PageHeader crumbs={[...rootCrumbs, { label: code }]} title={integration.integrationName} code={code}
      readOnlyHint={!canManage ? noManage : undefined}
      actions={canManage && <Button type="primary" onClick={() => void navigate(replaceQuery('', { tab: 'basic', mode: 'edit' }))}>编辑</Button>} />
    <Tabs className="xapi-tabs" activeKey={tab} onChange={next => void navigate(replaceQuery('', { tab: next }))}
      items={tabKeys.map(key => ({ key, label: labels[key] }))} />
    {tab === 'basic' && <IntegrationBasicView integration={integration} />}
    {tab === 'apis' && <IntegrationApiTab key={location.search} code={code} canManage={canManage}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />}
    {tab === 'scripts' && <ScriptTab key={location.search} code={code} canManage={canManage}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />}
    {tab === 'implementations' && <ImplementationTab code={code} canManage={canManage}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />}
  </>;
}

/** 引用列表较长时只列前两个：“A、B 等 7 个”。 */
const briefList = (items: string[]) => items.length === 0 ? '—' : items.length <= 2 ? items.join('、')
  : `${items.slice(0, 2).join('、')} 等 ${items.length} 个`;

function IntegrationBasicView({ integration }: { integration: IntegrationDetail }) {
  return <>
    <div className="xapi-facts">
      <Field label="编码"><Mono>{integration.integrationCode}</Mono></Field>
      <Field label="名称">{integration.integrationName}</Field>
      <Field label="说明">{integration.description || '—'}</Field>
      <Field label="最近修改">{formatTime(integration.updateTime)} · 账号 {integration.updatedBy} · 版本 {integration.version}</Field>
    </div>
    <div className="xapi-toolbar"><h2 className="xapi-section-title">出站地址</h2>
      <span className="xapi-desc">{integration.endpoints.length} / 16</span></div>
    <div className="xapi-table-wrap"><Table rowKey="endpointCode" pagination={false} dataSource={integration.endpoints} columns={[
      { title: '地址代码 endpointCode', width: 240, render: (_, row) => <Mono>{row.endpointCode}</Mono> },
      { title: '基础地址 baseUrl', ellipsis: true, render: (_, row) => <Mono>{row.baseUrl}</Mono> },
      { title: '引用的原子 API', width: 320, render: (_, row) => briefList(row.referencedApiCodes) },
    ]} /></div>
    <Note items={['基础地址只允许 https（本机测试环境另允许 http），可带路径前缀，不能包含用户信息、查询串或片段；每个环境的数据库各自维护。',
      '实际出站仍受部署网络白名单限制；地址不在白名单内时请求失败，不能用这里的配置绕过。', '被原子 API 引用的地址代码不能删除或改名。']} />
  </>;
}

interface BasicValues { integrationCode: string; integrationName: string; description?: string; endpoints: Endpoint[] }

function IntegrationBasicForm({ detail, crumbs, onAuthorizationChanged, onSaved, onReload }: {
  detail?: IntegrationDetail; crumbs: Crumb[]; onAuthorizationChanged: () => void; onSaved: (code: string) => void; onReload?: () => void;
}) {
  const [form] = Form.useForm<BasicValues>(); const editor = useEditorSave(onAuthorizationChanged); const copy = useCopyDraft();
  const creating = !detail; const disabled = editor.saving; const leaving = useRef(false);
  const referenced = new Set(detail?.endpoints.filter(item => item.referencedApiCodes.length).map(item => item.endpointCode));
  const badRows = new Set(errorRows(editor.fieldErrors, 'endpoints'));
  const reasonFlow = useReasonFlow(async reason => {
    const values = form.getFieldsValue(true) as BasicValues;
    const code = creating ? values.integrationCode : detail.integrationCode;
    const common = { integrationName: values.integrationName.trim(), description: values.description?.trim() || null,
      endpoints: values.endpoints.map(item => ({ endpointCode: item.endpointCode.trim(), baseUrl: item.baseUrl.trim() })), reason };
    const result = await editor.save(creating ? integrationPath : `${integrationPath}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { integrationCode: code, ...common } : { ...common, expectedVersion: detail.version },
      value => decodeMutation(value, 'INTEGRATION', code));
    if (!result) return false;
    flushSync(() => editor.setDirty(false));
    if (!leaving.current) onSaved(code);
    return true;
  }, () => form.validateFields().then(() => true, () => false));
  const { setSaveForLeave } = editor;
  useEffect(() => setSaveForLeave(() => { leaving.current = true; return reasonFlow.start().finally(() => { leaving.current = false; }); }));
  const latest = useCallback(() => session.request(`${integrationPath}/${detail?.integrationCode}`, {},
    value => decodeIntegration(value, detail!.integrationCode)), [detail]);
  return <>
    <PageHeader crumbs={crumbs} title={creating ? '新建接入方' : `编辑接入方 · ${detail.integrationCode}`}
      actions={<Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>保存</Button>} />
    {editor.conflict && detail && onReload && <ConflictBanner code={detail.integrationCode} baseVersion={detail.version} latest={latest}
      onReload={onReload} onCopy={() => copy(form.getFieldsValue(true))} />}
    <Form form={form} layout="vertical" requiredMark={false} disabled={disabled} onValuesChange={() => editor.setDirty(true)}
      initialValues={detail ? { integrationCode: detail.integrationCode, integrationName: detail.integrationName,
        description: detail.description ?? '', endpoints: detail.endpoints.map(({ endpointCode, baseUrl }) => ({ endpointCode, baseUrl })) }
        : { endpoints: [{ endpointCode: '', baseUrl: '' }] }}>
      <Panel title="基本信息"><div className="xapi-grid xapi-grid-3">
        <Form.Item name="integrationCode" label="编码（创建后只读）" style={{ marginBottom: 0 }}
          rules={[{ required: true, pattern: codePattern, message: '大写字母开头，包含大写字母、数字或下划线，最多 64 字符' }]}>
          {creating ? <Input maxLength={64} className="xapi-mono-input" placeholder="例如 NI" /> : <ReadOnlyValue mono value={detail.integrationCode} />}</Form.Item>
        <Form.Item name="integrationName" label="名称" style={{ marginBottom: 0 }} rules={[{ required: true, whitespace: true, message: '请填写名称' }]}>
          <Input maxLength={128} /></Form.Item>
        <Form.Item name="description" label="说明" style={{ marginBottom: 0 }}><Input maxLength={512} /></Form.Item>
      </div></Panel>
      <Panel title="出站地址" className="xapi-gap-top" extra={<span className="xapi-desc">最多 16 个</span>}>
        <Form.List name="endpoints">{(fields, { add, remove }) => <>
          {fields.map(field => {
            const endpointCode = form.getFieldValue(['endpoints', field.name, 'endpointCode']) as string;
            const locked = !creating && referenced.has(endpointCode);
            return <div key={field.key} className={`xapi-endpoint-row${badRows.has(field.name) ? ' xapi-row-error' : ''}`}>
              <Form.Item name={[field.name, 'endpointCode']} label="地址代码" style={{ marginBottom: 0 }}
                rules={[{ required: true, whitespace: true, message: '请填写地址代码' }]}>
                <Input maxLength={64} disabled={locked || disabled} className="xapi-mono-input" placeholder="例如 NI_GATEWAY" /></Form.Item>
              <Form.Item name={[field.name, 'baseUrl']} label="基础地址" style={{ marginBottom: 0 }}
                rules={[{ required: true, whitespace: true, message: '请填写基础地址' }]}>
                <Input maxLength={512} className="xapi-mono-input" placeholder="https://..." /></Form.Item>
              <button type="button" className="xapi-link" disabled={locked || fields.length === 1 || disabled}
                title={locked ? '仍被原子 API 引用' : undefined} onClick={() => remove(field.name)}>删除</button>
            </div>;
          })}
          <div><button type="button" className="xapi-link xapi-link-add" disabled={fields.length >= 16 || disabled}
            onClick={() => add({ endpointCode: '', baseUrl: '' })}>添加出站地址</button></div>
        </>}</Form.List>
        <Note items={['基础地址只允许 https（本机测试环境另允许 http），可带路径前缀，不能带查询串。', '被原子 API 引用的地址代码不能删除或改名。']} />
      </Panel>
    </Form>
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}

interface ChildTabProps { code: string; canManage: boolean; onAuthorizationChanged: () => void; onChanged: () => void }

/** 子页签公共骨架：说明 + 新建按钮、带外框的表格、删除弹窗。 */
function ChildToolbar({ description, action }: { description: string; action?: ReactNode }) {
  return <div className="xapi-toolbar"><span className="xapi-desc">{description}</span>{action}</div>;
}

function IntegrationApiTab({ code, canManage, onAuthorizationChanged, onChanged }: ChildTabProps) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const base = `${integrationRoute}/${code}/apis`;
  const decode = useMemo(() => (value: unknown) => decodePage(value, decodeIntegrationApiSummary, item => item.apiCode), []);
  const page = usePaging<IntegrationApiSummary>(`${integrationPath}/${code}/apis`, decode, {}, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) }, notifyOnError: false,
    onQueryChange: next => void navigate(replaceQuery('', { tab: 'apis', page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <ChildToolbar description="每个原子 API 是一次外部协议操作；被流程节点引用时不能删除。"
      action={canManage && <Button type="primary" onClick={() => void navigate(`${base}/new`)}>新建原子 API</Button>} />
    {page.error ? <LoadFailure title="原子 API 加载失败" failure={page.failure} retry={page.refresh} /> : <div className="xapi-table-wrap">
      <Table<IntegrationApiSummary> rowKey="apiCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无原子 API" /> }} columns={[
          { title: '编码', width: 190, render: (_, row) => <Link className="xapi-link-code" to={`${base}/${row.apiCode}`}><Mono>{row.apiCode}</Mono></Link> },
          { title: '名称', dataIndex: 'apiName', width: 150, ellipsis: true },
          { title: 'Method', width: 90, render: (_, row) => <Mono>{row.method}</Mono> },
          { title: '地址 + 路径', ellipsis: true, render: (_, row) => <span>{row.endpointCode} <span className="xapi-mono-muted"><Mono>{row.pathTemplate}</Mono></span></span> },
          { title: '脚本', width: 80, render: (_, row) => `${row.requestScriptCount}+${row.responseScriptCount}` },
          { title: '被引用', width: 180, render: (_, row) => row.referencedBy.length ? row.referencedBy.join('、') : '—' },
          { title: '操作', width: 110, render: (_, row) => <span className="xapi-ops">
            {canManage && <Link to={`${base}/${row.apiCode}?mode=edit`}>编辑</Link>}
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'view', label: '详情', onClick: () => void navigate(`${base}/${row.apiCode}`) },
              ...(canManage ? [{ key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '原子 API', objectType: 'INTEGRATION_API',
                objectCode: `${code}/${row.apiCode}`, version: row.version, path: `${integrationPath}/${code}/apis/${row.apiCode}`,
                references: apiReferences(code, row.apiCode) }) }] : []),
            ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown></span> },
        ]} /></div>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); onChanged(); }} />}
  </>;
}

function ScriptTab({ code, canManage, onAuthorizationChanged, onChanged }: ChildTabProps) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const base = `${integrationRoute}/${code}/scripts`;
  const decode = useMemo(() => (value: unknown) => decodePage(value, decodeScriptSummary, item => item.scriptCode), []);
  const page = usePaging<IntegrationScriptSummary>(`${integrationPath}/${code}/scripts`, decode, {}, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) }, notifyOnError: false,
    onQueryChange: next => void navigate(replaceQuery('', { tab: 'scripts', page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <ChildToolbar description="同一接入方内可复用的请求或响应阶段脚本；被原子 API 引用时不能删除。"
      action={canManage && <Button type="primary" onClick={() => void navigate(`${base}/new`)}>新建脚本</Button>} />
    {page.error ? <LoadFailure title="公共脚本加载失败" failure={page.failure} retry={page.refresh} /> : <div className="xapi-table-wrap">
      <Table<IntegrationScriptSummary> rowKey="scriptCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公共脚本" /> }} columns={[
          { title: '编码', width: 200, render: (_, row) => <Link className="xapi-link-code" to={`${base}/${row.scriptCode}`}><Mono>{row.scriptCode}</Mono></Link> },
          { title: '名称', dataIndex: 'scriptName', width: 160, ellipsis: true },
          { title: '阶段', width: 110, render: (_, row) => <Mono>{row.executionStage}</Mono> },
          { title: '参数', ellipsis: true, render: (_, row) => row.parameterNames.length ? <Mono>{row.parameterNames.join('、')}</Mono> : '—' },
          { title: '引用 API', width: 200, render: (_, row) => row.referencedApiCodes.length ? row.referencedApiCodes.join('、') : '—' },
          { title: '操作', width: 110, render: (_, row) => <span className="xapi-ops">
            {canManage && <Link to={`${base}/${row.scriptCode}?mode=edit`}>编辑</Link>}
            <Dropdown trigger={['click']} menu={{ items: [
              { key: 'view', label: '详情', onClick: () => void navigate(`${base}/${row.scriptCode}`) },
              ...(canManage ? [{ key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '公共脚本', objectType: 'INTEGRATION_SCRIPT',
                objectCode: `${code}/${row.scriptCode}`, version: row.version, path: `${integrationPath}/${code}/scripts/${row.scriptCode}`,
                references: scriptReferences(code, row.scriptCode) }) }] : []),
            ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown></span> },
        ]} /></div>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); onChanged(); }} />}
  </>;
}

function ImplementationTab({ code, canManage, onAuthorizationChanged, onChanged }: ChildTabProps) {
  const navigate = useNavigate(); const base = `${integrationRoute}/${code}/implementations`;
  const list = useDetail<ImplementationSummary[]>(`${integrationPath}/${code}/implementations`, decodeImplementationList);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  if (list.loading && !list.data) return <Loading />;
  if (!list.data) return <LoadFailure title="标准 API 实现加载失败" failure={list.failure} retry={list.refresh} />;
  const items = list.data;
  const create = canManage && <Button type="primary" onClick={() => setCreating(true)}>新建实现</Button>;
  return <>
    {items.length === 0 ? <div className="xapi-panel xapi-state"><span className="xapi-state-icon">⛓</span><h3>还没有标准 API 实现</h3>
      <p>选择一个标准 API，用本接入方的原子 API 编排流程。</p>{create}</div> : <>
      <ChildToolbar description="每行表示本接入方如何实现一个标准 API；同一标准 API 只能有一份实现。删除后 Payment 调用会得到保证未发送的 404。"
        action={create} />
      <div className="xapi-table-wrap"><Table<ImplementationSummary> rowKey="standardApiCode" tableLayout="fixed" dataSource={items} pagination={false} columns={[
        { title: '标准 API', width: 240, render: (_, row) => <Link className="xapi-link-code" to={`${base}/${row.standardApiCode}`}><Mono>{row.standardApiCode}</Mono>
          <span className="xapi-mono-muted"> · {row.standardApiName}</span></Link> },
        { title: '节点', dataIndex: 'nodeCount', width: 70 }, { title: '出口', dataIndex: 'returnCount', width: 70 },
        { title: '初始化项', render: (_, row) => row.initializerIds.length ? <Mono>{row.initializerIds.join('、')}</Mono> : '—' },
        { title: '总预算', width: 110, render: (_, row) => <Mono>{row.timeoutMs} ms</Mono> },
        { title: '更新时间', width: 150, render: (_, row) => formatTime(row.updateTime) },
        { title: '操作', width: 130, render: (_, row) => <span className="xapi-ops">
          <Link to={`${base}/${row.standardApiCode}${canManage ? '?mode=edit' : ''}`}>{canManage ? '编辑流程' : '查看流程'}</Link>
          {canManage && <Dropdown trigger={['click']} menu={{ items: [
            { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '标准 API 实现', objectType: 'INTEGRATION_IMPLEMENTATION',
              objectCode: `${code}/${row.standardApiCode}`, version: row.version, path: `${integrationPath}/${code}/implementations/${row.standardApiCode}`,
              references: noReferences }) },
          ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown>}</span> },
      ]} /></div></>}
    {creating && <NewImplementationDialog implemented={items} onClose={() => setCreating(false)}
      onPick={standardApiCode => void navigate(`${base}/${standardApiCode}?mode=create`)} />}
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
  return <Modal open centered width={560} title="新建标准 API 实现" okText="创建并编辑流程" okButtonProps={{ disabled: !selected }} onCancel={onClose}
    onOk={() => selected && onPick(selected)}>
    <p className="xapi-desc">选择本接入方尚未实现的标准 API，创建后进入流程编辑，默认“开始 → 待配置出口（ERROR）”。</p>
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
