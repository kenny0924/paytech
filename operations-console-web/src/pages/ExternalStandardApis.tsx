import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Dropdown, Empty, Form, Input, Table, Tabs } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { FieldTable } from '../externalApi/FieldTable';
import { ReasonDialog, useReasonFlow } from '../externalApi/EditorParts';
import {
  ConflictBanner, DeleteDialog, Loading, LoadFailure, hasButton, useCopyDraft, useDetail, useEditorSave,
} from '../externalApi/externalApiHooks';
import type { DeleteTarget, Reference } from '../externalApi/externalApiHooks';
import {
  cleanFilters, codePattern, decodeMutation, decodePage, decodeStandardApi, decodeStandardApiSummary, errorRows,
  integrationRoute, listSizes, manageStandardApis, replaceQuery, standardApiPath, standardApiRoute, toFieldBodies,
} from '../externalApi/externalApiModel';
import type { ApiField, FieldLocation, StandardApiDetail, StandardApiSummary } from '../externalApi/externalApiModel';
import { ExternalApiFrame, Field, Mono, PageHeader, Panel, ReadOnlyValue, counted, formatTime } from '../externalApi/ui';
import type { Crumb } from '../externalApi/ui';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const bodyOnly: FieldLocation[] = ['BODY'];
const summaryPage = (value: unknown) => decodePage(value, decodeStandardApiSummary, item => item.standardApiCode);
const rootCrumbs: Crumb[] = [{ label: '渠道' }, { label: '标准 API', to: standardApiRoute }];
const noManage = '只读 · 无“外部 API 管理”权限';

/** 标准 API 当前被哪些接入方实现：删除前读取，有实现时不能删除。 */
export function standardApiReferences(code: string) {
  return () => session.request(`${standardApiPath}/${code}`, {}, value => decodeStandardApi(value, code))
    .then(detail => detail.implementations.map<Reference>(item => ({
      label: <>标准 API 实现 · <Mono>{item.integrationCode}</Mono>（{item.integrationName}）</>,
      to: `${integrationRoute}/${item.integrationCode}/implementations/${code}` })));
}

/** 标准 API：列表、新建（/new）与详情编辑（/:code，?mode=edit 进入编辑）。 */
export function ExternalStandardApis(props: Props) {
  const location = useLocation();
  const code = location.pathname.slice(standardApiRoute.length).replace(/^\/|\/$/g, '');
  let page;
  if (!code) page = <StandardApiList key={location.search} {...props} />;
  else if (code === 'new') page = <StandardApiEditor key="new" {...props} code={null} />;
  else page = codePattern.test(code) ? <StandardApiEditor key={code} {...props} code={code} />
    : <LoadFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return <ExternalApiFrame>{page}</ExternalApiFrame>;
}

function StandardApiList({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const [form] = Form.useForm(); const canManage = hasButton(resources, manageStandardApis);
  const initial = cleanFilters({ keyword: query.get('keyword') ?? undefined });
  const page = usePaging<StandardApiSummary>(standardApiPath, summaryPage, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) }, notifyOnError: false,
    onQueryChange: next => void navigate(replaceQuery('', { ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <PageHeader crumbs={rootCrumbs.slice(0, 1).concat({ label: '标准 API' })} title="标准 API"
      actions={canManage && <Button type="primary" onClick={() => void navigate(`${standardApiRoute}/new`)}>新建标准 API</Button>} />
    <Form form={form} initialValues={initial} onFinish={values => page.applyFilters(cleanFilters(values))} className="xapi-filter">
      <Field label="编码 / 名称"><Form.Item name="keyword" noStyle><Input allowClear maxLength={64} placeholder="输入编码或名称" /></Form.Item></Field>
      <Button htmlType="submit">查询</Button>
      {initial.keyword && <button type="button" className="xapi-link" onClick={() => { form.resetFields(); page.applyFilters({}); }}>清除条件</button>}
    </Form>
    {page.error ? <LoadFailure title="标准 API 加载失败" failure={page.failure} retry={page.refresh} keptFilters /> : <div className="xapi-table-wrap">
      <Table<StandardApiSummary> rowKey="standardApiCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={initial.keyword ? '没有符合条件的标准 API' : '暂无标准 API'} /> }}
        columns={[
          { title: '编码', width: 200, render: (_, row) => <Link className="xapi-link-code" to={`${standardApiRoute}/${row.standardApiCode}`}><Mono>{row.standardApiCode}</Mono></Link> },
          { title: '名称', dataIndex: 'standardApiName', width: 180, ellipsis: true },
          { title: '请求字段', dataIndex: 'requestFieldCount', width: 90 },
          { title: '响应字段', dataIndex: 'responseFieldCount', width: 90 },
          { title: '已实现接入方', render: (_, row) => row.implementedBy.length ? row.implementedBy.map((item, index) => <span key={item.integrationCode}>
            {index > 0 && '、'}<Link to={`${integrationRoute}/${item.integrationCode}?tab=implementations`}>{item.integrationCode}</Link></span>) : '—' },
          { title: '更新时间', width: 150, render: (_, row) => formatTime(row.updateTime) },
          { title: '操作', width: 70, render: (_, row) => <Dropdown trigger={['click']} menu={{ items: [
            { key: 'view', label: '详情', onClick: () => void navigate(`${standardApiRoute}/${row.standardApiCode}`) },
            ...(canManage ? [
              { key: 'edit', label: '编辑', onClick: () => void navigate(`${standardApiRoute}/${row.standardApiCode}?mode=edit`) },
              { key: 'delete', label: '删除', danger: true, onClick: () => setDeleting({ title: '标准 API', objectType: 'STANDARD_API',
                objectCode: row.standardApiCode, version: row.version, path: `${standardApiPath}/${row.standardApiCode}`,
                references: standardApiReferences(row.standardApiCode) }) },
            ] : []),
          ] }}><button type="button" className="xapi-more" aria-label="更多操作">···</button></Dropdown> },
        ]} /></div>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); }} />}
  </>;
}

interface BasicValues { standardApiCode: string; standardApiName: string; description?: string }

function StandardApiEditor({ resources, onAuthorizationChanged, code }: Props & { code: string | null }) {
  const location = useLocation(); const navigate = useNavigate();
  const creating = code === null; const canManage = hasButton(resources, manageStandardApis);
  const editingMode = creating || new URLSearchParams(location.search).get('mode') === 'edit';
  const readOnly = !canManage || !editingMode;
  const decode = useMemo(() => (value: unknown) => decodeStandardApi(value, code!), [code]);
  const detail = useDetail<StandardApiDetail>(creating ? null : `${standardApiPath}/${code}`, decode);
  if (creating && !canManage) return <Alert type="warning" showIcon title="当前没有标准 API 维护权限" />;
  if (detail.loading) return <Loading />;
  if (!creating && (detail.failure || !detail.data)) return <LoadFailure title="标准 API 加载失败" failure={detail.failure} retry={detail.refresh} />;
  return <StandardApiForm key={`${detail.data?.version}:${readOnly}`} detail={detail.data} readOnly={readOnly} canManage={canManage}
    onAuthorizationChanged={onAuthorizationChanged} onReload={detail.refresh}
    onSaved={savedCode => void navigate(`${standardApiRoute}/${savedCode}`, { replace: true })}
    onEdit={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))} />;
}

function StandardApiForm({ detail, readOnly, canManage, onAuthorizationChanged, onReload, onSaved, onEdit }: {
  detail?: StandardApiDetail; readOnly: boolean; canManage: boolean; onAuthorizationChanged: () => void;
  onReload: () => void; onSaved: (code: string) => void; onEdit: () => void;
}) {
  const [form] = Form.useForm<BasicValues>(); const editor = useEditorSave(onAuthorizationChanged); const copy = useCopyDraft();
  const [requestFields, setRequestFields] = useState<ApiField[]>(detail?.requestFields ?? []);
  const [responseFields, setResponseFields] = useState<ApiField[]>(detail?.responseFields ?? []);
  const [tab, setTab] = useState('request');
  const leaving = useRef(false);
  const creating = !detail; const disabled = readOnly || editor.saving;
  function changeFields(setter: (fields: ApiField[]) => void) {
    return (fields: ApiField[]) => { setter(fields); editor.setDirty(true); };
  }
  function payload(reason: string) {
    const values = form.getFieldsValue(true);
    return { standardApiName: values.standardApiName.trim(), description: values.description?.trim() || null,
      requestFields: toFieldBodies(requestFields), responseFields: toFieldBodies(responseFields), reason };
  }
  const reasonFlow = useReasonFlow(async reason => {
    const code = creating ? form.getFieldValue('standardApiCode') as string : detail.standardApiCode;
    const body = payload(reason);
    const result = await editor.save(creating ? standardApiPath : `${standardApiPath}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { standardApiCode: code, ...body } : { ...body, expectedVersion: detail.version },
      value => decodeMutation(value, 'STANDARD_API', code));
    if (!result) return false;
    flushSync(() => editor.setDirty(false));
    if (!leaving.current) onSaved(code);
    return true;
  }, () => form.validateFields().then(() => true, () => false));
  const { setSaveForLeave } = editor;
  useEffect(() => {
    setSaveForLeave(readOnly ? null : () => { leaving.current = true; return reasonFlow.start().finally(() => { leaving.current = false; }); });
  });
  const latest = useCallback(() => session.request(`${standardApiPath}/${detail?.standardApiCode}`, {},
    value => decodeStandardApi(value, detail!.standardApiCode)), [detail]);
  const code = detail?.standardApiCode;
  const title = creating ? '新建标准 API' : readOnly ? `标准 API · ${code}` : `编辑标准 API · ${code}`;
  const crumbs: Crumb[] = creating ? [...rootCrumbs, { label: '新建' }]
    : readOnly ? [...rootCrumbs, { label: code! }] : [...rootCrumbs, { label: code!, to: `${standardApiRoute}/${code}` }, { label: '编辑' }];
  const fieldHint = '按路径缩进显示层级；[] 表示数组元素，子字段必填只在父对象存在时校验。';
  return <>
    <PageHeader crumbs={crumbs} title={title} readOnlyHint={!canManage ? noManage : undefined}
      actions={readOnly ? canManage && detail && <Button type="primary" onClick={onEdit}>编辑</Button>
        : <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>保存</Button>} />
    {editor.conflict && detail && <ConflictBanner code={detail.standardApiCode} baseVersion={detail.version} latest={latest} onReload={onReload}
      onCopy={() => copy({ ...form.getFieldsValue(true), requestFields, responseFields })} />}
    <Form form={form} layout="vertical" requiredMark={false} disabled={disabled}
      initialValues={detail ? { standardApiCode: detail.standardApiCode, standardApiName: detail.standardApiName, description: detail.description ?? '' } : {}}
      onValuesChange={() => { if (!readOnly) editor.setDirty(true); }}>
      <div className="xapi-grid xapi-grid-3">
        <Form.Item name="standardApiCode" label="编码（创建后只读）" style={{ marginBottom: 0 }}
          rules={[{ required: true, pattern: codePattern, message: '大写字母开头，包含大写字母、数字或下划线，最多 64 字符' }]}>
          {creating ? <Input maxLength={64} className="xapi-mono-input" placeholder="例如 PAY" /> : <ReadOnlyValue mono value={detail.standardApiCode} />}</Form.Item>
        <Form.Item name="standardApiName" label="名称" style={{ marginBottom: 0 }} rules={[{ required: true, whitespace: true, message: '请填写名称' }]}>
          {readOnly ? <ReadOnlyValue value={detail?.standardApiName} /> : <Input maxLength={128} />}</Form.Item>
        <Form.Item name="description" label="说明" style={{ marginBottom: 0 }}>
          {readOnly ? <ReadOnlyValue value={detail?.description} /> : <Input maxLength={512} />}</Form.Item>
      </div>
    </Form>
    <Tabs className="xapi-tabs" activeKey={tab} onChange={setTab} items={[
      { key: 'request', label: counted('请求字段', requestFields.length), children: <div className="xapi-tab-body">
        <FieldTable value={requestFields} onChange={changeFields(setRequestFields)} locations={bodyOnly} readOnly={disabled} hint={fieldHint}
          serverErrorRows={errorRows(editor.fieldErrors, 'requestFields')} /></div> },
      { key: 'response', label: counted('响应字段', responseFields.length), children: <div className="xapi-tab-body">
        <FieldTable value={responseFields} onChange={changeFields(setResponseFields)} locations={bodyOnly} readOnly={disabled} hint={fieldHint}
          serverErrorRows={errorRows(editor.fieldErrors, 'responseFields')} /></div> },
    ]} />
    {!readOnly && <p className="xapi-desc">保存时服务端校验：若新增或修改的必填字段使已有渠道实现不完整（如出口未赋值新必填输出），保存失败并列出受影响的接入方与位置。</p>}
    {detail && readOnly && <Panel title="已实现的接入方">
      {detail.implementations.length ? <div>{detail.implementations.map((item, index) => <span key={item.integrationCode}>{index > 0 && '、'}
        <Link to={`${integrationRoute}/${item.integrationCode}/implementations/${detail.standardApiCode}`}>{item.integrationCode}</Link>
        <span className="xapi-mono-muted">（{item.integrationName}）</span></span>)}
        <span className="xapi-desc">　· 最近修改 {formatTime(detail.updateTime)} · 版本 {detail.version}</span></div>
        : <span className="xapi-desc">尚无接入方实现</span>}
    </Panel>}
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}
