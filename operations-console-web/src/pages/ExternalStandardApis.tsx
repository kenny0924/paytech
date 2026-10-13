import { useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Dropdown, Empty, Form, Input, Space, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { FieldTable } from '../externalApi/FieldTable';
import { DeleteDialog, Loading, LoadFailure, SaveNotice, hasButton, useDetail } from '../externalApi/externalApiHooks';
import type { DeleteTarget } from '../externalApi/externalApiHooks';
import {
  cleanFilters, codePattern, decodeMutation, decodePage, decodeStandardApi, decodeStandardApiSummary, errorRows,
  integrationRoute, listSizes, manageStandardApis, replaceQuery, standardApiPath, standardApiRoute, toFieldBodies,
} from '../externalApi/externalApiModel';
import type { ApiField, FieldLocation, StandardApiDetail, StandardApiSummary } from '../externalApi/externalApiModel';
import '../configuration/configuration.css';
import '../externalApi/externalApi.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const bodyOnly: FieldLocation[] = ['BODY'];
const summaryPage = (value: unknown) => decodePage(value, decodeStandardApiSummary, item => item.standardApiCode);
const time = (value: string) => new Date(value).toLocaleString('zh-CN');

/** 标准 API：列表、新建（/new）与详情编辑（/:code，?mode=edit 进入编辑）。 */
export function ExternalStandardApis(props: Props) {
  const location = useLocation();
  const code = location.pathname.slice(standardApiRoute.length).replace(/^\/|\/$/g, '');
  if (!code) return <StandardApiList key={location.search} {...props} />;
  if (code === 'new') return <StandardApiEditor key="new" {...props} code={null} />;
  return codePattern.test(code) ? <StandardApiEditor key={code} {...props} code={code} />
    : <LoadFailure failure={new ApiError(404, 'NOT_FOUND')} />;
}

function StandardApiList({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const [form] = Form.useForm(); const canManage = hasButton(resources, manageStandardApis);
  const initial = cleanFilters({ keyword: query.get('keyword') ?? undefined });
  const page = usePaging<StandardApiSummary>(standardApiPath, summaryPage, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => void navigate(replaceQuery('', { ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <div className="configuration-page external-api-page">
    <div className="page-heading"><h1>标准 API</h1>
      {canManage && <Button type="primary" onClick={() => void navigate(`${standardApiRoute}/new`)}>新建标准 API</Button>}</div>
    <Form form={form} layout="inline" initialValues={initial} onFinish={values => page.applyFilters(cleanFilters(values))}>
      <Form.Item name="keyword" label="编码 / 名称"><Input allowClear maxLength={64} placeholder="输入编码或名称" /></Form.Item>
      <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
      <Form.Item><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></Form.Item>
    </Form>
    {page.error ? <LoadFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <div className="table-toolbar"><span>共 {page.data.total} 个标准 API</span><Button loading={page.loading} onClick={page.refresh}>刷新</Button></div>
      <Table<StandardApiSummary> rowKey="standardApiCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        scroll={{ x: 1000 }} pagination={{ ...page.pagination, pageSizeOptions: listSizes }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={initial.keyword ? '没有符合条件的标准 API' : '暂无标准 API'} /> }}
        columns={[
          { title: '编码', dataIndex: 'standardApiCode', width: 200, ellipsis: true },
          { title: '名称', dataIndex: 'standardApiName', width: 180, ellipsis: true },
          { title: '请求字段', dataIndex: 'requestFieldCount', width: 90 },
          { title: '响应字段', dataIndex: 'responseFieldCount', width: 90 },
          { title: '已实现接入方', render: (_, row) => row.implementedBy.length ? <Space size={4} wrap>{row.implementedBy.map(item =>
            <Button key={item.integrationCode} type="link" size="small" className="external-api-inline-link"
              onClick={() => void navigate(`${integrationRoute}/${item.integrationCode}?tab=implementations`)}>{item.integrationCode}</Button>)}</Space> : '—' },
          { title: '更新时间', width: 170, render: (_, row) => time(row.updateTime) },
          { title: '操作', width: 90, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
            { key: 'view', label: '详情', onClick: () => void navigate(`${standardApiRoute}/${row.standardApiCode}`) },
            ...(canManage ? [
              { key: 'edit', label: '编辑', onClick: () => void navigate(`${standardApiRoute}/${row.standardApiCode}?mode=edit`) },
              { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '标准 API', objectType: 'STANDARD_API',
                objectCode: row.standardApiCode, version: row.version, path: `${standardApiPath}/${row.standardApiCode}` }) },
            ] : []),
          ] }}><Button size="small">⋯</Button></Dropdown> },
        ]} /></section>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); }} />}
  </div>;
}

interface BasicValues { standardApiCode: string; standardApiName: string; description?: string; reason: string }

function StandardApiEditor({ resources, onAuthorizationChanged, code }: Props & { code: string | null }) {
  const location = useLocation(); const navigate = useNavigate();
  const creating = code === null; const canManage = hasButton(resources, manageStandardApis);
  const editingMode = creating || new URLSearchParams(location.search).get('mode') === 'edit';
  const readOnly = !canManage || !editingMode;
  const decode = useMemo(() => (value: unknown) => decodeStandardApi(value, code!), [code]);
  const detail = useDetail<StandardApiDetail>(creating ? null : `${standardApiPath}/${code}`, decode);
  if (creating && !canManage) return <Alert type="warning" showIcon title="当前没有标准 API 维护权限" />;
  if (detail.loading) return <Loading />;
  if (!creating && (detail.failure || !detail.data)) return <LoadFailure failure={detail.failure} retry={detail.refresh} />;
  return <StandardApiForm key={`${detail.data?.version}:${readOnly}`} detail={detail.data} readOnly={readOnly} canManage={canManage}
    onAuthorizationChanged={onAuthorizationChanged} onReload={detail.refresh}
    onSaved={savedCode => void navigate(`${standardApiRoute}/${savedCode}`, { replace: true })}
    onEdit={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))} />;
}

function StandardApiForm({ detail, readOnly, canManage, onAuthorizationChanged, onReload, onSaved, onEdit }: {
  detail?: StandardApiDetail; readOnly: boolean; canManage: boolean; onAuthorizationChanged: () => void;
  onReload: () => void; onSaved: (code: string) => void; onEdit: () => void;
}) {
  const [form] = Form.useForm<BasicValues>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const [requestFields, setRequestFields] = useState<ApiField[]>(detail?.requestFields ?? []);
  const [responseFields, setResponseFields] = useState<ApiField[]>(detail?.responseFields ?? []);
  const [tab, setTab] = useState('request');
  const creating = !detail; const disabled = readOnly || editor.saving || editor.conflict;
  function changeFields(setter: (fields: ApiField[]) => void) {
    return (fields: ApiField[]) => { setter(fields); editor.setDirty(true); };
  }
  async function save(values: BasicValues) {
    const code = creating ? values.standardApiCode : detail.standardApiCode;
    const common = { standardApiName: values.standardApiName.trim(), description: values.description?.trim() || null,
      requestFields: toFieldBodies(requestFields), responseFields: toFieldBodies(responseFields), reason: values.reason.trim() };
    const result = await editor.save(creating ? standardApiPath : `${standardApiPath}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { standardApiCode: code, ...common } : { ...common, expectedVersion: detail.version },
      value => decodeMutation(value, 'STANDARD_API', code));
    if (result) { flushSync(() => editor.setDirty(false)); onSaved(code); }
  }
  const title = creating ? '新建标准 API' : `标准 API / ${detail.standardApiCode}`;
  return <div className="configuration-page external-api-page">
    <div className="page-heading"><h1>{title}</h1>
      {readOnly && canManage && detail && <Button onClick={onEdit}>编辑</Button>}</div>
    <Form form={form} layout="vertical" requiredMark={false} onFinish={save} disabled={disabled}
      initialValues={detail ? { standardApiCode: detail.standardApiCode, standardApiName: detail.standardApiName, description: detail.description ?? '' } : {}}
      onValuesChange={() => { if (!readOnly) editor.setDirty(true); }}>
      <section className="configuration-editor"><h2>基本信息</h2>
        <div className="configuration-grid two">
          <Form.Item name="standardApiCode" label="编码" extra={creating ? '创建后不可修改' : undefined}
            rules={[{ required: true, pattern: codePattern, message: '大写字母开头，包含大写字母、数字或下划线，最多 64 字符' }]}>
            <Input maxLength={64} disabled={!creating || disabled} placeholder="例如 PAY" /></Form.Item>
          <Form.Item name="standardApiName" label="名称" rules={[{ required: true, whitespace: true, message: '请填写名称' }]}>
            <Input maxLength={128} /></Form.Item>
        </div>
        <Form.Item name="description" label="说明"><Input.TextArea rows={2} maxLength={512} /></Form.Item>
        {detail && readOnly && <div className="configuration-grid two muted"><div>当前版本：{detail.version}</div>
          <div>更新时间：{time(detail.updateTime)}</div></div>}
      </section>
      <section className="configuration-editor">
        <Tabs activeKey={tab} onChange={setTab} items={[
          { key: 'request', label: `请求字段（${requestFields.length}）`, children: <FieldTable value={requestFields} onChange={changeFields(setRequestFields)}
            locations={bodyOnly} readOnly={disabled} serverErrorRows={errorRows(editor.fieldErrors, 'requestFields')} /> },
          { key: 'response', label: `响应字段（${responseFields.length}）`, children: <FieldTable value={responseFields} onChange={changeFields(setResponseFields)}
            locations={bodyOnly} readOnly={disabled} serverErrorRows={errorRows(editor.fieldErrors, 'responseFields')} /> },
        ]} />
      </section>
      {detail && readOnly && <section className="configuration-editor"><h2>已实现的接入方</h2>
        {detail.implementations.length ? <Space wrap>{detail.implementations.map(item =>
          <Tag key={item.integrationCode}>{item.integrationCode} · {item.integrationName}</Tag>)}</Space> : <span className="muted">尚无接入方实现</span>}
      </section>}
      {editor.conflict && <Alert type="warning" showIcon title="该配置已被他人修改，本地修改已保留。"
        action={<Button onClick={onReload}>重新加载</Button>} />}
      {editor.fieldErrors.length > 0 && !editor.conflict && <Alert type="error" showIcon title="保存未通过校验，请检查标红的字段" />}
      {!readOnly && <section className="configuration-editor">
        <Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true, message: '请填写变更原因' }]}>
          <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
        <footer className="configuration-save-bar"><SaveNotice />
          <Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict}>保存</Button></footer>
      </section>}
    </Form>
  </div>;
}
