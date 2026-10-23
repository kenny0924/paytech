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
  listSizes, manageStandardApis, replaceQuery, standardApiPath, standardApiRoute, toFieldBodies,
} from '../externalApi/externalApiModel';
import type { ApiField, FieldLocation, StandardApiDetail, StandardApiSummary } from '../externalApi/externalApiModel';
import { ExternalApiFrame, Field, Mono, PageHeader, Panel, ReadOnlyValue, counted, formatTime } from '../externalApi/ui';
import type { Crumb } from '../externalApi/ui';
import { channelByCodeRoute } from '../channel/channelWorkspace';
import { richText } from '../i18n/richText';
import { translator } from '../i18n/index';
import { useT } from '../i18n/react';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const bodyOnly: FieldLocation[] = ['BODY'];
const summaryPage = (value: unknown) => decodePage(value, decodeStandardApiSummary, item => item.standardApiCode);
const tStandard = translator('channel'), tNav = translator('nav');
/** 面包屑根：渠道 / 标准 API（菜单名，调用时翻译）。 */
const rootCrumbs = (): Crumb[] => [{ label: tNav('resource.OPERATIONS_CHANNEL') }, { label: tNav('resource.OPERATIONS_EXTERNAL_STANDARD_APIS'), to: standardApiRoute }];

/** 标准 API 当前被哪些渠道实现：删除前读取，有实现时不能删除。 */
export function standardApiReferences(code: string) {
  return () => session.request(`${standardApiPath}/${code}`, {}, value => decodeStandardApi(value, code))
    .then(detail => detail.implementations.map<Reference>(item => ({
      label: <>{richText(tStandard, 'standardApi.reference', { code: <Mono>{item.integrationCode}</Mono> }, { name: item.integrationName })}</>,
      to: channelByCodeRoute(item.integrationCode, {}, `/protocol/implementations/${code}`) })));
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
  const t = useT('channel'), tNavPage = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const [form] = Form.useForm(); const canManage = hasButton(resources, manageStandardApis);
  const initial = cleanFilters({ keyword: query.get('keyword') ?? undefined });
  const page = usePaging<StandardApiSummary>(standardApiPath, summaryPage, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) }, notifyOnError: false,
    onQueryChange: next => void navigate(replaceQuery('', { ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  return <>
    <PageHeader crumbs={rootCrumbs().slice(0, 1).concat({ label: tNavPage('resource.OPERATIONS_EXTERNAL_STANDARD_APIS') })} title={tNavPage('resource.OPERATIONS_EXTERNAL_STANDARD_APIS')}
      actions={canManage && <Button type="primary" onClick={() => void navigate(`${standardApiRoute}/new`)}>{t('standardApi.createTitle')}</Button>} />
    <Form form={form} initialValues={initial} onFinish={values => page.applyFilters(cleanFilters(values))} className="xapi-filter">
      <Field label={t('standardApi.keyword')}><Form.Item name="keyword" noStyle><Input allowClear maxLength={64} placeholder={t('standardApi.keywordPlaceholder')} /></Form.Item></Field>
      <Button htmlType="submit">{t('changes.search')}</Button>
      {initial.keyword && <button type="button" className="xapi-link" onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('channels.clearFilters')}</button>}
    </Form>
    {page.error ? <LoadFailure title={t('protocol.standardApisLoadFailed')} failure={page.failure} retry={page.refresh} keptFilters /> : <div className="xapi-table-wrap">
      <Table<StandardApiSummary> rowKey="standardApiCode" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, pageSizeOptions: listSizes, showTotal: total => t('grants.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={initial.keyword ? t('standardApi.noMatch') : t('standardApi.empty')} /> }}
        columns={[
          { title: t('protocol.column.code'), width: 200, render: (_, row) => <Link className="xapi-link-code" to={`${standardApiRoute}/${row.standardApiCode}`}><Mono>{row.standardApiCode}</Mono></Link> },
          { title: t('form.name'), dataIndex: 'standardApiName', width: 180, ellipsis: true },
          { title: t('api.tab.request'), dataIndex: 'requestFieldCount', width: 90 },
          { title: t('mapping.group.RESPONSE'), dataIndex: 'responseFieldCount', width: 90 },
          { title: t('standardApi.implementedBy'), render: (_, row) => row.implementedBy.length ? row.implementedBy.map((item, index) => <span key={item.integrationCode}>
            {index > 0 && t('list.separator')}<Link to={channelByCodeRoute(item.integrationCode, { tab: 'protocol' })}>{item.integrationCode}</Link></span>) : '—' },
          { title: t('channels.updatedAt'), width: 150, render: (_, row) => formatTime(row.updateTime) },
          { title: t('grants.actions'), width: 90, render: (_, row) => <Dropdown trigger={['click']} menu={{ items: [
            { key: 'view', label: t('protocol.details'), onClick: () => void navigate(`${standardApiRoute}/${row.standardApiCode}`) },
            ...(canManage ? [
              { key: 'edit', label: t('protocol.edit'), onClick: () => void navigate(`${standardApiRoute}/${row.standardApiCode}?mode=edit`) },
              { key: 'delete', label: t('endpointEditor.delete'), danger: true, onClick: () => setDeleting({ title: t('standardApi.subject'), objectType: 'STANDARD_API',
                objectCode: row.standardApiCode, version: row.version, path: `${standardApiPath}/${row.standardApiCode}`,
                references: standardApiReferences(row.standardApiCode) }) },
            ] : []),
          ] }}><button type="button" className="xapi-more" aria-label={t('protocol.more')}>···</button></Dropdown> },
        ]} /></div>}
    {deleting && <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDeleted={() => { setDeleting(null); page.refresh(); }} />}
  </>;
}

interface BasicValues { standardApiCode: string; standardApiName: string; description?: string }

function StandardApiEditor({ resources, onAuthorizationChanged, code }: Props & { code: string | null }) {
  const t = useT('channel');
  const location = useLocation(); const navigate = useNavigate();
  const creating = code === null; const canManage = hasButton(resources, manageStandardApis);
  const editingMode = creating || new URLSearchParams(location.search).get('mode') === 'edit';
  const readOnly = !canManage || !editingMode;
  const decode = useMemo(() => (value: unknown) => decodeStandardApi(value, code!), [code]);
  const detail = useDetail<StandardApiDetail>(creating ? null : `${standardApiPath}/${code}`, decode);
  if (creating && !canManage) return <Alert type="warning" showIcon title={t('standardApi.noPermission')} />;
  if (detail.loading) return <Loading />;
  if (!creating && (detail.failure || !detail.data)) return <LoadFailure title={t('protocol.standardApisLoadFailed')} failure={detail.failure} retry={detail.refresh} />;
  return <StandardApiForm key={`${detail.data?.version}:${readOnly}`} detail={detail.data} readOnly={readOnly} canManage={canManage}
    onAuthorizationChanged={onAuthorizationChanged} onReload={detail.refresh}
    onSaved={savedCode => void navigate(`${standardApiRoute}/${savedCode}`, { replace: true })}
    onEdit={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))} />;
}

function StandardApiForm({ detail, readOnly, canManage, onAuthorizationChanged, onReload, onSaved, onEdit }: {
  detail?: StandardApiDetail; readOnly: boolean; canManage: boolean; onAuthorizationChanged: () => void;
  onReload: () => void; onSaved: (code: string) => void; onEdit: () => void;
}) {
  const t = useT('channel'), tCommon = useT('common');
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
  const title = creating ? t('standardApi.createTitle') : readOnly ? t('standardApi.viewTitle', { code }) : t('standardApi.editTitle', { code });
  const crumbs: Crumb[] = creating ? [...rootCrumbs(), { label: t('editor.crumbNew') }]
    : readOnly ? [...rootCrumbs(), { label: code! }] : [...rootCrumbs(), { label: code!, to: `${standardApiRoute}/${code}` }, { label: t('protocol.edit') }];
  const fieldHint = t('standardApi.fieldHint');
  return <>
    <PageHeader crumbs={crumbs} title={title} readOnlyHint={!canManage ? t('editor.noManage') : undefined}
      actions={readOnly ? canManage && detail && <Button type="primary" onClick={onEdit}>{t('protocol.edit')}</Button>
        : <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>{tCommon('action.save')}</Button>} />
    {editor.conflict && detail && <ConflictBanner code={detail.standardApiCode} baseVersion={detail.version} latest={latest} onReload={onReload}
      onCopy={() => copy({ ...form.getFieldsValue(true), requestFields, responseFields })} />}
    <Form form={form} layout="vertical" requiredMark={false} disabled={disabled}
      initialValues={detail ? { standardApiCode: detail.standardApiCode, standardApiName: detail.standardApiName, description: detail.description ?? '' } : {}}
      onValuesChange={() => { if (!readOnly) editor.setDirty(true); }}>
      <div className="xapi-grid xapi-grid-3">
        <Form.Item name="standardApiCode" label={t('editor.codeReadOnlyAfterCreate')} style={{ marginBottom: 0 }}
          rules={[{ required: true, pattern: codePattern, message: t('standardApi.codePattern') }]}>
          {creating ? <Input maxLength={64} className="xapi-mono-input" placeholder={t('standardApi.codePlaceholder')} /> : <ReadOnlyValue mono value={detail.standardApiCode} />}</Form.Item>
        <Form.Item name="standardApiName" label={t('form.name')} style={{ marginBottom: 0 }} rules={[{ required: true, whitespace: true, message: t('verify.account.nameRequired') }]}>
          {readOnly ? <ReadOnlyValue value={detail?.standardApiName} /> : <Input maxLength={128} />}</Form.Item>
        <Form.Item name="description" label={t('mids.note')} style={{ marginBottom: 0 }}>
          {readOnly ? <ReadOnlyValue value={detail?.description} /> : <Input maxLength={512} />}</Form.Item>
      </div>
    </Form>
    <Tabs className="xapi-tabs" activeKey={tab} onChange={setTab} items={[
      { key: 'request', label: counted(t('api.tab.request'), requestFields.length), children: <div className="xapi-tab-body">
        <FieldTable value={requestFields} onChange={changeFields(setRequestFields)} locations={bodyOnly} readOnly={disabled} hint={fieldHint}
          serverErrorRows={errorRows(editor.fieldErrors, 'requestFields')} /></div> },
      { key: 'response', label: counted(t('mapping.group.RESPONSE'), responseFields.length), children: <div className="xapi-tab-body">
        <FieldTable value={responseFields} onChange={changeFields(setResponseFields)} locations={bodyOnly} readOnly={disabled} hint={fieldHint}
          serverErrorRows={errorRows(editor.fieldErrors, 'responseFields')} /></div> },
    ]} />
    {!readOnly && <p className="xapi-desc">{t('standardApi.saveHint')}</p>}
    {detail && readOnly && <Panel title={t('standardApi.implementations')}>
      {detail.implementations.length ? <div>{detail.implementations.map((item, index) => <span key={item.integrationCode}>{index > 0 && t('list.separator')}
        <Link to={channelByCodeRoute(item.integrationCode, {}, `/protocol/implementations/${detail.standardApiCode}`)}>{item.integrationCode}</Link>
        <span className="xapi-mono-muted">{t('standardApi.implementationName', { name: item.integrationName })}</span></span>)}
        <span className="xapi-desc">{t('standardApi.modified', { time: formatTime(detail.updateTime), version: detail.version })}</span></div>
        : <span className="xapi-desc">{t('standardApi.noImplementations')}</span>}
    </Panel>}
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}
