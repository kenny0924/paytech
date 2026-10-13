import { useEffect, useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Dropdown, Empty, Form, Input, Modal, Select, Space, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { DeleteDialog, Loading, LoadFailure, SaveNotice, hasButton, loadAllPages, useDetail } from '../externalApi/externalApiHooks';
import type { DeleteTarget } from '../externalApi/externalApiHooks';
import {
  availableStandardApis, cleanFilters, codePattern, decodeImplementationList, decodeIntegration, decodeIntegrationApiSummary,
  decodeIntegrationSummary, decodeMutation, decodePage, decodeScriptSummary, decodeStandardApiSummary, errorRows,
  integrationPath, integrationRoute, listSizes, manageIntegrations, replaceQuery, stageLabels, standardApiPath, standardApiRoute,
} from '../externalApi/externalApiModel';
import type {
  Endpoint, ImplementationSummary, IntegrationApiSummary, IntegrationDetail, IntegrationScriptSummary, IntegrationSummary,
  StandardApiSummary,
} from '../externalApi/externalApiModel';
import { FlowEditor } from '../externalApi/FlowEditor';
import { IntegrationApiEditor } from '../externalApi/IntegrationApiEditor';
import { ScriptEditor } from '../externalApi/ScriptEditor';
import '../configuration/configuration.css';
import '../externalApi/externalApi.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Tab = 'basic' | 'apis' | 'scripts' | 'implementations';
const tabs: { key: Tab; label: string }[] = [
  { key: 'basic', label: '基本信息' }, { key: 'apis', label: '原子 API' },
  { key: 'scripts', label: '公共脚本' }, { key: 'implementations', label: '标准 API 实现' },
];
const childTitles: Record<string, string> = { apis: '原子 API', scripts: '公共脚本', implementations: '流程' };
const summaryPage = (value: unknown) => decodePage(value, decodeIntegrationSummary, item => item.integrationCode);
const time = (value: string) => new Date(value).toLocaleString('zh-CN');

/**
 * 接入方：列表、新建（/new）、工作区（/:code?tab=）。原子 API、脚本与流程的编辑页位于 /:code/apis|scripts|implementations/:childCode。
 */
export function ExternalIntegrations(props: Props) {
  const location = useLocation();
  const parts = location.pathname.slice(integrationRoute.length).replace(/^\/|\/$/g, '').split('/').filter(Boolean);
  if (parts.length === 0) return <IntegrationList key={location.search} {...props} />;
  if (parts.length === 1 && parts[0] === 'new') return <IntegrationCreate {...props} />;
  if (!codePattern.test(parts[0])) return <LoadFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  if (parts.length === 1) return <IntegrationWorkspace key={parts[0]} {...props} code={parts[0]} />;
  if (parts.length === 3 && childTitles[parts[1]] && (parts[2] === 'new' && parts[1] !== 'implementations' || codePattern.test(parts[2]))) {
    const child = parts[2] === 'new' ? null : parts[2];
    if (parts[1] === 'apis') return <IntegrationApiEditor key={location.pathname} {...props} integrationCode={parts[0]} apiCode={child} />;
    if (parts[1] === 'scripts') return <ScriptEditor key={location.pathname} {...props} integrationCode={parts[0]} scriptCode={child} />;
    return <FlowEditor key={location.pathname + location.search} {...props} integrationCode={parts[0]} standardApiCode={parts[2]} />;
  }
  return <LoadFailure failure={new ApiError(404, 'NOT_FOUND')} />;
}

function IntegrationList({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const [form] = Form.useForm(); const canManage = hasButton(resources, manageIntegrations);
  const initial = cleanFilters({ keyword: query.get('keyword') ?? undefined });
  const page = usePaging<IntegrationSummary>(integrationPath, summaryPage, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => void navigate(replaceQuery('', { ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <div className="configuration-page external-api-page">
    <div className="page-heading"><h1>接入方</h1>
      {canManage && <Button type="primary" onClick={() => void navigate(`${integrationRoute}/new`)}>新建接入方</Button>}</div>
    <Form form={form} layout="inline" initialValues={initial} onFinish={values => page.applyFilters(cleanFilters(values))}>
      <Form.Item name="keyword" label="编码 / 名称"><Input allowClear maxLength={64} placeholder="输入编码或名称" /></Form.Item>
      <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
      <Form.Item><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></Form.Item>
    </Form>
    {page.error ? <LoadFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <div className="table-toolbar"><span>共 {page.data.total} 个接入方</span><Button loading={page.loading} onClick={page.refresh}>刷新</Button></div>
      <Table<IntegrationSummary> rowKey="integrationCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        scroll={{ x: 1050 }} pagination={{ ...page.pagination, pageSizeOptions: listSizes }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={initial.keyword ? '没有符合条件的接入方' : '暂无接入方'}>
          {canManage && !initial.keyword && <Button type="primary" onClick={() => void navigate(`${integrationRoute}/new`)}>新建接入方</Button>}</Empty> }}
        columns={[
          { title: '编码', dataIndex: 'integrationCode', width: 160, ellipsis: true },
          { title: '名称', dataIndex: 'integrationName', width: 160, ellipsis: true },
          { title: '出站地址', dataIndex: 'endpointCount', width: 90 },
          { title: '原子 API', dataIndex: 'apiCount', width: 90 },
          { title: '脚本', dataIndex: 'scriptCount', width: 70 },
          { title: '标准 API 实现', render: (_, row) => row.implementedStandardApis.length ? <Space size={4} wrap>
            {row.implementedStandardApis.map(item => <Tag key={item.standardApiCode}>{item.standardApiCode}</Tag>)}</Space> : '—' },
          { title: '更新时间', width: 170, render: (_, row) => time(row.updateTime) },
          { title: '操作', width: 90, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
            { key: 'view', label: '详情', onClick: () => void navigate(`${integrationRoute}/${row.integrationCode}`) },
            ...(canManage ? [
              { key: 'edit', label: '编辑', onClick: () => void navigate(`${integrationRoute}/${row.integrationCode}?tab=basic&mode=edit`) },
              { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '接入方', objectType: 'INTEGRATION',
                objectCode: row.integrationCode, version: row.version, path: `${integrationPath}/${row.integrationCode}` }) },
            ] : []),
          ] }}><Button size="small">⋯</Button></Dropdown> },
        ]} /></section>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); }} />}
  </div>;
}

function IntegrationCreate({ resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  if (!hasButton(resources, manageIntegrations)) return <Alert type="warning" showIcon title="当前没有接入方维护权限" />;
  return <div className="configuration-page external-api-page"><div className="page-heading"><h1>新建接入方</h1></div>
    <IntegrationBasicForm onAuthorizationChanged={onAuthorizationChanged}
      onSaved={code => void navigate(`${integrationRoute}/${code}`, { replace: true })} /></div>;
}

function IntegrationWorkspace({ resources, onAuthorizationChanged, code }: Props & { code: string }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const tab: Tab = tabs.some(item => item.key === query.get('tab')) ? query.get('tab') as Tab : 'basic';
  const canManage = hasButton(resources, manageIntegrations);
  const decode = useMemo(() => (value: unknown) => decodeIntegration(value, code), [code]);
  const detail = useDetail<IntegrationDetail>(`${integrationPath}/${code}`, decode);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure && !detail.data || !detail.data) return <LoadFailure failure={detail.failure} retry={detail.refresh} />;
  const integration = detail.data;
  const editing = canManage && tab === 'basic' && query.get('mode') === 'edit';
  return <div className="configuration-page external-api-page">
    <div className="page-heading"><h1>接入方 / {integration.integrationCode}</h1>
      <span className="muted">{integration.integrationName}</span></div>
    <Tabs activeKey={tab} onChange={next => void navigate(replaceQuery('', { tab: next }))} items={tabs.map(item => ({
      ...item, label: item.key === 'apis' ? `原子 API（${integration.apiCount}）` : item.key === 'scripts'
        ? `公共脚本（${integration.scriptCount}）` : item.key === 'implementations' ? `标准 API 实现（${integration.implementationCount}）` : item.label,
    }))} />
    {tab === 'basic' && (editing
      ? <IntegrationBasicForm key={integration.version} detail={integration} onAuthorizationChanged={onAuthorizationChanged}
        onReload={detail.refresh} onSaved={() => { detail.refresh(); void navigate(replaceQuery('', { tab: 'basic' }), { replace: true }); }} />
      : <IntegrationBasicView integration={integration} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged}
        onEdit={() => void navigate(replaceQuery('', { tab: 'basic', mode: 'edit' }))}
        onDeleted={() => void navigate(integrationRoute, { replace: true })} />)}
    {tab === 'apis' && <IntegrationApiTab key={location.search} code={code} canManage={canManage}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />}
    {tab === 'scripts' && <ScriptTab key={location.search} code={code} canManage={canManage}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />}
    {tab === 'implementations' && <ImplementationTab code={code} canManage={canManage}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />}
  </div>;
}

function IntegrationBasicView({ integration, canManage, onEdit, onDeleted, onAuthorizationChanged }: {
  integration: IntegrationDetail; canManage: boolean; onEdit: () => void; onDeleted: () => void; onAuthorizationChanged: () => void;
}) {
  const [deleting, setDeleting] = useState(false);
  return <section className="configuration-editor">
    <div className="table-toolbar"><h2>基本信息</h2>{canManage && <Space>
      <Button onClick={onEdit}>编辑</Button><Button danger onClick={() => setDeleting(true)}>删除接入方</Button></Space>}</div>
    <dl className="external-api-facts">
      <dt>编码</dt><dd>{integration.integrationCode}</dd><dt>名称</dt><dd>{integration.integrationName}</dd>
      <dt>说明</dt><dd>{integration.description || '—'}</dd><dt>版本</dt><dd>{integration.version}</dd>
      <dt>更新时间</dt><dd>{time(integration.updateTime)}</dd>
    </dl>
    <h3>出站地址</h3>
    <Alert type="info" showIcon title="实际出站受部署网络白名单限制" />
    <Table rowKey="endpointCode" size="small" pagination={false} dataSource={integration.endpoints} columns={[
      { title: '地址代码', dataIndex: 'endpointCode', width: 200 }, { title: '基础地址', dataIndex: 'baseUrl', ellipsis: true },
      { title: '引用的原子 API', render: (_, row) => row.referencedApiCodes.length ? row.referencedApiCodes.join('、') : '—' },
    ]} />
    {deleting && <DeleteDialog target={{ title: '接入方', objectType: 'INTEGRATION', objectCode: integration.integrationCode,
      version: integration.version, path: `${integrationPath}/${integration.integrationCode}` }} onClose={() => setDeleting(false)}
      onAuthorizationChanged={onAuthorizationChanged} onDeleted={onDeleted} />}
  </section>;
}

interface BasicValues { integrationCode: string; integrationName: string; description?: string; endpoints: Endpoint[]; reason: string }

function IntegrationBasicForm({ detail, onAuthorizationChanged, onSaved, onReload }: {
  detail?: IntegrationDetail; onAuthorizationChanged: () => void; onSaved: (code: string) => void; onReload?: () => void;
}) {
  const [form] = Form.useForm<BasicValues>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const creating = !detail; const disabled = editor.saving || editor.conflict;
  const referenced = new Set(detail?.endpoints.filter(item => item.referencedApiCodes.length).map(item => item.endpointCode));
  const badRows = new Set(errorRows(editor.fieldErrors, 'endpoints'));
  async function save(values: BasicValues) {
    const code = creating ? values.integrationCode : detail.integrationCode;
    const common = { integrationName: values.integrationName.trim(), description: values.description?.trim() || null,
      endpoints: values.endpoints.map(item => ({ endpointCode: item.endpointCode.trim(), baseUrl: item.baseUrl.trim() })),
      reason: values.reason.trim() };
    const result = await editor.save(creating ? integrationPath : `${integrationPath}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { integrationCode: code, ...common } : { ...common, expectedVersion: detail.version },
      value => decodeMutation(value, 'INTEGRATION', code));
    if (result) { flushSync(() => editor.setDirty(false)); onSaved(code); }
  }
  return <Form form={form} layout="vertical" requiredMark={false} onFinish={save} disabled={disabled}
    onValuesChange={() => editor.setDirty(true)}
    initialValues={detail ? { integrationCode: detail.integrationCode, integrationName: detail.integrationName,
      description: detail.description ?? '', endpoints: detail.endpoints.map(({ endpointCode, baseUrl }) => ({ endpointCode, baseUrl })) }
      : { endpoints: [{ endpointCode: '', baseUrl: '' }] }}>
    <section className="configuration-editor"><h2>基本信息</h2>
      <div className="configuration-grid two">
        <Form.Item name="integrationCode" label="编码" extra={creating ? '创建后不可修改' : undefined}
          rules={[{ required: true, pattern: codePattern, message: '大写字母开头，包含大写字母、数字或下划线，最多 64 字符' }]}>
          <Input maxLength={64} disabled={!creating || disabled} placeholder="例如 NI" /></Form.Item>
        <Form.Item name="integrationName" label="名称" rules={[{ required: true, whitespace: true, message: '请填写名称' }]}>
          <Input maxLength={128} /></Form.Item>
      </div>
      <Form.Item name="description" label="说明"><Input.TextArea rows={2} maxLength={512} /></Form.Item>
      <h3>出站地址</h3>
      <Alert type="info" showIcon title="只允许 https 地址，可带路径前缀，不能带查询串；实际出站受部署网络白名单限制" />
      <Form.List name="endpoints">{(fields, { add, remove }) => <>
        {fields.map(field => {
          const endpointCode = form.getFieldValue(['endpoints', field.name, 'endpointCode']) as string;
          const locked = !creating && referenced.has(endpointCode);
          return <div key={field.key} className={`external-api-endpoint-row${badRows.has(field.name) ? ' external-api-row-error' : ''}`}>
            <Form.Item name={[field.name, 'endpointCode']} label="地址代码" rules={[{ required: true, whitespace: true, message: '请填写地址代码' }]}>
              <Input maxLength={64} disabled={locked || disabled} placeholder="例如 NI_GATEWAY" /></Form.Item>
            <Form.Item name={[field.name, 'baseUrl']} label="基础地址" rules={[{ required: true, whitespace: true, message: '请填写基础地址' }]}>
              <Input maxLength={512} placeholder="https://..." /></Form.Item>
            <Button danger disabled={locked || fields.length === 1 || disabled} title={locked ? '仍被原子 API 引用' : undefined}
              onClick={() => remove(field.name)}>删除</Button>
          </div>;
        })}
        <Button onClick={() => add({ endpointCode: '', baseUrl: '' })} disabled={fields.length >= 16 || disabled}>添加出站地址</Button>
      </>}</Form.List>
    </section>
    {editor.conflict && <Alert type="warning" showIcon title="该配置已被他人修改，本地修改已保留。"
      action={onReload && <Button onClick={onReload}>重新加载</Button>} />}
    <section className="configuration-editor">
      <Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true, message: '请填写变更原因' }]}>
        <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
      <footer className="configuration-save-bar"><SaveNotice />
        <Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict}>保存</Button></footer>
    </section>
  </Form>;
}

interface ChildTabProps { code: string; canManage: boolean; onAuthorizationChanged: () => void; onChanged: () => void }

function IntegrationApiTab({ code, canManage, onAuthorizationChanged, onChanged }: ChildTabProps) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const base = `${integrationRoute}/${code}/apis`;
  const decode = useMemo(() => (value: unknown) => decodePage(value, decodeIntegrationApiSummary, item => item.apiCode), []);
  const initial = cleanFilters({ keyword: query.get('keyword') ?? undefined });
  const page = usePaging<IntegrationApiSummary>(`${integrationPath}/${code}/apis`, decode, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => void navigate(replaceQuery('', { tab: 'apis', ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <section className="table-panel">
    <div className="table-toolbar"><Form form={form} layout="inline" initialValues={initial} onFinish={values => page.applyFilters(cleanFilters(values))}>
      <Form.Item name="keyword"><Input allowClear maxLength={64} placeholder="编码或名称" /></Form.Item>
      <Form.Item><Button htmlType="submit">查询</Button></Form.Item></Form>
      {canManage && <Button type="primary" onClick={() => void navigate(`${base}/new`)}>新建原子 API</Button>}</div>
    {page.error ? <LoadFailure failure={page.failure} retry={page.refresh} /> :
      <Table<IntegrationApiSummary> rowKey="apiCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1050 }}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无原子 API" /> }} columns={[
          { title: '编码', dataIndex: 'apiCode', width: 180, ellipsis: true }, { title: '名称', dataIndex: 'apiName', width: 150, ellipsis: true },
          { title: 'Method', dataIndex: 'method', width: 80 },
          { title: '地址代码 + 路径', ellipsis: true, render: (_, row) => <code>{row.endpointCode} {row.pathTemplate}</code> },
          { title: '脚本', width: 90, render: (_, row) => `${row.requestScriptCount} / ${row.responseScriptCount}` },
          { title: '被实现引用', width: 160, render: (_, row) => row.referencedBy.length ? row.referencedBy.join('、') : '—' },
          { title: '操作', width: 90, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
            { key: 'view', label: '详情', onClick: () => void navigate(`${base}/${row.apiCode}`) },
            ...(canManage ? [{ key: 'edit', label: '编辑', onClick: () => void navigate(`${base}/${row.apiCode}?mode=edit`) },
              { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '原子 API', objectType: 'INTEGRATION_API',
                objectCode: `${code}/${row.apiCode}`, version: row.version, path: `${integrationPath}/${code}/apis/${row.apiCode}` }) }] : []),
          ] }}><Button size="small">⋯</Button></Dropdown> },
        ]} />}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); onChanged(); }} />}
  </section>;
}

function ScriptTab({ code, canManage, onAuthorizationChanged, onChanged }: ChildTabProps) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const base = `${integrationRoute}/${code}/scripts`;
  const decode = useMemo(() => (value: unknown) => decodePage(value, decodeScriptSummary, item => item.scriptCode), []);
  const initial = cleanFilters({ keyword: query.get('keyword') ?? undefined, executionStage: query.get('executionStage') ?? undefined });
  const page = usePaging<IntegrationScriptSummary>(`${integrationPath}/${code}/scripts`, decode, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => void navigate(replaceQuery('', { tab: 'scripts', ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <section className="table-panel">
    <div className="table-toolbar"><Form form={form} layout="inline" initialValues={initial} onFinish={values => page.applyFilters(cleanFilters(values))}>
      <Form.Item name="keyword"><Input allowClear maxLength={64} placeholder="编码或名称" /></Form.Item>
      <Form.Item name="executionStage"><Select allowClear placeholder="全部阶段" style={{ width: 130 }}
        options={Object.entries(stageLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item><Button htmlType="submit">查询</Button></Form.Item></Form>
      {canManage && <Button type="primary" onClick={() => void navigate(`${base}/new`)}>新建脚本</Button>}</div>
    {page.error ? <LoadFailure failure={page.failure} retry={page.refresh} /> :
      <Table<IntegrationScriptSummary> rowKey="scriptCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 900 }}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无公共脚本" /> }} columns={[
          { title: '编码', dataIndex: 'scriptCode', width: 180, ellipsis: true }, { title: '名称', dataIndex: 'scriptName', width: 160, ellipsis: true },
          { title: '阶段', width: 100, render: (_, row) => stageLabels[row.executionStage] },
          { title: '参数', ellipsis: true, render: (_, row) => row.parameterNames.length ? row.parameterNames.join('、') : '—' },
          { title: '引用的原子 API', width: 200, render: (_, row) => row.referencedApiCodes.length ? row.referencedApiCodes.join('、') : '—' },
          { title: '操作', width: 90, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
            { key: 'view', label: '详情', onClick: () => void navigate(`${base}/${row.scriptCode}`) },
            ...(canManage ? [{ key: 'edit', label: '编辑', onClick: () => void navigate(`${base}/${row.scriptCode}?mode=edit`) },
              { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '公共脚本', objectType: 'INTEGRATION_SCRIPT',
                objectCode: `${code}/${row.scriptCode}`, version: row.version, path: `${integrationPath}/${code}/scripts/${row.scriptCode}` }) }] : []),
          ] }}><Button size="small">⋯</Button></Dropdown> },
        ]} />}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); onChanged(); }} />}
  </section>;
}

function ImplementationTab({ code, canManage, onAuthorizationChanged, onChanged }: ChildTabProps) {
  const navigate = useNavigate(); const base = `${integrationRoute}/${code}/implementations`;
  const list = useDetail<ImplementationSummary[]>(`${integrationPath}/${code}/implementations`, decodeImplementationList);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  if (list.loading && !list.data) return <Loading />;
  if (!list.data) return <LoadFailure failure={list.failure} retry={list.refresh} />;
  const items = list.data;
  return <section className="table-panel">
    <div className="table-toolbar"><span>共 {items.length} 个实现</span>
      {canManage && <Button type="primary" onClick={() => setCreating(true)}>新建实现</Button>}</div>
    <Table<ImplementationSummary> rowKey="standardApiCode" tableLayout="fixed" dataSource={items} pagination={false} scroll={{ x: 900 }}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该接入方尚无标准 API 实现">
        {canManage && <Button type="primary" onClick={() => setCreating(true)}>新建实现</Button>}</Empty> }} columns={[
        { title: '标准 API', width: 220, render: (_, row) => <div><div>{row.standardApiCode}</div><div className="cell-meta">{row.standardApiName}</div></div> },
        { title: '节点数', dataIndex: 'nodeCount', width: 80 }, { title: '出口数', dataIndex: 'returnCount', width: 80 },
        { title: '初始化项', render: (_, row) => row.initializerIds.length ? row.initializerIds.join('、') : '—' },
        { title: '总预算', width: 100, render: (_, row) => `${row.timeoutMs} ms` },
        { title: '更新时间', width: 170, render: (_, row) => time(row.updateTime) },
        { title: '操作', width: 90, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
          { key: 'view', label: '详情', onClick: () => void navigate(`${base}/${row.standardApiCode}`) },
          ...(canManage ? [{ key: 'edit', label: '编辑', onClick: () => void navigate(`${base}/${row.standardApiCode}?mode=edit`) },
            { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '标准 API 实现', objectType: 'INTEGRATION_IMPLEMENTATION',
              objectCode: `${code}/${row.standardApiCode}`, version: row.version, path: `${integrationPath}/${code}/implementations/${row.standardApiCode}` }) }] : []),
        ] }}><Button size="small">⋯</Button></Dropdown> },
      ]} />
    {creating && <NewImplementationDialog implemented={items} onClose={() => setCreating(false)}
      onPick={standardApiCode => void navigate(`${base}/${standardApiCode}?mode=create`)} />}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); list.refresh(); onChanged(); }} />}
  </section>;
}

/** 新建实现：先选择尚未实现的标准 API，再进入流程编辑。 */
function NewImplementationDialog({ implemented, onClose, onPick }: {
  implemented: ImplementationSummary[]; onClose: () => void; onPick: (standardApiCode: string) => void;
}) {
  const notify = useErrorNotification();
  const [options, setOptions] = useState<StandardApiSummary[]>();
  const [selected, setSelected] = useState<string>();
  useEffect(() => {
    const abort = new AbortController();
    loadAllPages(standardApiPath, value => decodePage(value, decodeStandardApiSummary, item => item.standardApiCode), abort.signal)
      .then(all => { if (!abort.signal.aborted) setOptions(availableStandardApis(all, implemented)); })
      .catch(error => { if (!abort.signal.aborted) { setOptions([]); notify(error, '标准 API 加载失败'); } });
    return () => abort.abort();
  }, [implemented, notify]);
  return <Modal open title="新建实现" okText="下一步" okButtonProps={{ disabled: !selected }} onCancel={onClose}
    onOk={() => selected && onPick(selected)}>
    {options === undefined ? <Loading /> : options.length === 0
      ? <Alert type="info" showIcon title="所有标准 API 已实现" description={<a href={standardApiRoute}>前往标准 API</a>} />
      : <Select style={{ width: '100%' }} showSearch placeholder="选择标准 API" value={selected} onChange={setSelected}
        options={options.map(item => ({ value: item.standardApiCode, label: `${item.standardApiCode} · ${item.standardApiName}` }))} />}
  </Modal>;
}
