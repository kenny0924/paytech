import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { Alert, App, Button, Form, Input, InputNumber, Modal, Radio, Select, Switch, Table, Tabs } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { BindingInput, BindingView, ReasonDialog, ValidationPanel, emptyBinding, useReasonFlow } from './EditorParts';
import { FieldTable } from './FieldTable';
import { decodeCapabilities, decodeValidation } from './editorModel';
import type { Capabilities, ValidationIssue } from './editorModel';
import { ConflictBanner, Loading, LoadFailure, hasButton, loadAllPages, useCopyDraft, useDetail, useEditorSave } from './externalApiHooks';
import {
  codePattern, decodeMutation, decodePage, decodeScriptSummary, errorRows, externalApiBase,
  fieldDataTypes, integrationPath, manageProtocol, replaceQuery, toFieldBodies,
} from './externalApiModel';
import type { ApiField, Endpoint, FieldDataType, FieldLocation, IntegrationScriptSummary } from './externalApiModel';
import { workspaceCrumbs, workspaceRoutes } from '../channel/channelWorkspace';
import {
  decodeIntegrationApi, emptyDraft, fieldKey, issueTab, methods, moveItem, nextScriptUseId, normalizeKey, paramTypes,
  decimalEncodings, pathVariables, requestEncodings, responseDecodings, responseReadPaths, toDefinition, toDraft,
} from './integrationApiModel';
import type {
  Binding, DefinitionDraft, EditorTab, FixedValue, IntegrationApiDetail, OutputDeclaration, ParamDeclaration, ParamType, ScriptUse,
  StatusRule,
} from './integrationApiModel';
import { Field, HandleMark, Mono, Note, PageHeader, Panel, ReadOnlyValue, StatusTag, counted, lockMark } from './ui';
import { useT } from '../i18n/react';
import { richText } from '../i18n/richText';

/** 所属渠道：编码用于管理接口路径，出站地址来自渠道详情的 endpoints。 */
export interface ProtocolChannel { channelId: string; channelCode: string; endpoints: readonly Endpoint[] }
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; channel: ProtocolChannel; apiCode: string | null }
const allLocations: FieldLocation[] = ['HEADER', 'BODY', 'QUERY', 'PATH'];
const responseLocations: FieldLocation[] = ['HEADER', 'BODY'];
const capabilityPath = `${externalApiBase}/editor-capabilities`;
const scriptPage = (value: unknown) => decodePage(value, decodeScriptSummary, item => item.scriptCode);

/** 由规范化键（HEADER 小写）还原为字段原有写法的键，例如 HEADER:Content-Type。 */
function rawKey(key: string, fields: ApiField[]): string {
  const [location, ...rest] = key.split(':');
  const path = rest.join(':');
  const field = fields.find(item => item.fieldLocation === location && (location === 'HEADER' ? item.fieldPath.toLowerCase() === path : item.fieldPath === path));
  return field ? `${field.fieldLocation}:${field.fieldPath}` : key;
}

/** 原子 API 编辑与详情（/channel/channels/:channelId/protocol/apis/:apiCode，?mode=edit；新建为 /apis/new）。 */
export function IntegrationApiEditor({ resources, onAuthorizationChanged, channel, apiCode }: Props) {
  const t = useT('channel');
  const location = useLocation();
  const integrationCode = channel.channelCode;
  const creating = apiCode === null; const canManage = hasButton(resources, manageProtocol);
  const readOnly = !canManage || !creating && new URLSearchParams(location.search).get('mode') !== 'edit';
  const decodeApi = useMemo(() => (value: unknown) => decodeIntegrationApi(value, apiCode!), [apiCode]);
  const api = useDetail<IntegrationApiDetail>(creating ? null : `${integrationPath}/${integrationCode}/apis/${apiCode}`, decodeApi);
  const capabilities = useDetail<Capabilities>(capabilityPath, decodeCapabilities);
  const [scripts, setScripts] = useState<IntegrationScriptSummary[]>();
  const notify = useErrorNotification();
  useEffect(() => {
    const abort = new AbortController();
    loadAllPages(`${integrationPath}/${integrationCode}/scripts`, scriptPage, abort.signal)
      .then(items => { if (!abort.signal.aborted) setScripts(items); })
      .catch(error => { if (!abort.signal.aborted) { setScripts([]); notify(error, t('protocol.scriptsLoadFailed')); } });
    return () => abort.abort();
  // t 只用于失败通知标题，不应触发重新读取
  }, [integrationCode, notify]); // eslint-disable-line react-hooks/exhaustive-deps
  if (creating && !canManage) return <Alert type="warning" showIcon title={t('editor.noProtocolPermission')} />;
  if (api.loading || capabilities.loading || scripts === undefined) return <Loading />;
  if (!creating && !api.data) return <LoadFailure title={t('protocol.apisLoadFailed')} failure={api.failure} retry={api.refresh} />;
  return <ApiForm key={`${api.data?.version}:${readOnly}`} channel={channel} detail={api.data} readOnly={readOnly}
    canManage={canManage} capabilities={capabilities.data} scripts={scripts} onAuthorizationChanged={onAuthorizationChanged}
    onReload={api.refresh} />;
}

interface FormProps {
  channel: ProtocolChannel; detail?: IntegrationApiDetail; readOnly: boolean; canManage: boolean;
  capabilities?: Capabilities; scripts: IntegrationScriptSummary[]; onAuthorizationChanged: () => void; onReload: () => void;
}

function ApiForm({ channel, detail, readOnly, canManage, capabilities, scripts, onAuthorizationChanged, onReload }: FormProps) {
  const t = useT('channel'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification(); const copy = useCopyDraft(); const { message } = App.useApp();
  const editor = useEditorSave(onAuthorizationChanged);
  const creating = !detail; const integrationCode = channel.channelCode; const base = `${integrationPath}/${integrationCode}/apis`;
  const [apiCode, setApiCode] = useState(detail?.apiCode ?? '');
  const [apiName, setApiName] = useState(detail?.apiName ?? '');
  const [description, setDescription] = useState(detail?.description ?? '');
  const [requestFields, setRequestFields] = useState<ApiField[]>(detail?.requestFields ?? []);
  const [responseFields, setResponseFields] = useState<ApiField[]>(detail?.responseFields ?? []);
  const [draft, setDraft] = useState<DefinitionDraft>(() => detail ? toDraft(detail.definition)
    : emptyDraft(channel.endpoints[0]?.endpointCode ?? ''));
  const [tab, setTab] = useState<EditorTab>('interface');
  const [issues, setIssues] = useState<ValidationIssue[] | null>(null);
  const [stale, setStale] = useState(false);
  const [validating, setValidating] = useState(false);
  const leaving = useRef(false);
  const disabled = readOnly || editor.saving;
  function touch() { editor.setDirty(true); setStale(issues !== null); }
  function update(patch: Partial<DefinitionDraft>) { setDraft(current => ({ ...current, ...patch })); touch(); }
  function body() {
    return { apiName: apiName.trim(), description: description.trim() || null, requestFields: toFieldBodies(requestFields),
      responseFields: toFieldBodies(responseFields), definition: toDefinition(draft) };
  }
  async function validate() {
    setValidating(true);
    try {
      const result = await session.request(`${base}/validate`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apiCode: creating ? apiCode.trim() : detail.apiCode, ...body(), expectedVersion: detail?.version ?? null }) },
        decodeValidation);
      setIssues(result.issues); setStale(false);
      if (!result.issues.length) void message.success(t('editor.validationPassed'));
      const first = result.issues.find(issue => issue.severity === 'ERROR');
      if (first) setTab(issueTab(first.location));
    } catch (error) { notify(error, t('editor.validationFailed')); } finally { setValidating(false); }
  }
  const reasonFlow = useReasonFlow(async reason => {
    const code = creating ? apiCode.trim() : detail.apiCode;
    const result = await editor.save(creating ? base : `${base}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { apiCode: code, ...body(), reason } : { ...body(), expectedVersion: detail.version, reason },
      value => decodeMutation(value, 'INTEGRATION_API', `${integrationCode}/${code}`));
    if (!result) { if (!editor.conflict) void validate(); return false; }
    flushSync(() => editor.setDirty(false));
    if (!leaving.current) void navigate(workspaceRoutes(channel.channelId).api(code), { replace: true });
    return true;
  }, () => !!apiName.trim() && (!creating || codePattern.test(apiCode.trim())));
  const { setSaveForLeave } = editor;
  useEffect(() => setSaveForLeave(readOnly ? null : () => { leaving.current = true; return reasonFlow.start().finally(() => { leaving.current = false; }); }));
  const latest = useCallback(() => session.request(`${base}/${detail?.apiCode}`, {}, value => decodeIntegrationApi(value, detail!.apiCode)), [base, detail]);
  const fixedValues = new Map(draft.fixedValues.map(item => [normalizeKey(item.key), item.expression]));
  const readPaths = responseReadPaths(responseFields, draft.params);
  const scriptParamPaths = draft.params.filter(param => param.name.trim()).map(param => `params.${param.name.trim()}`);
  const code = detail?.apiCode;
  const crumbs = [...workspaceCrumbs(channel.channelId, integrationCode, 'apis'), ...(creating ? [{ label: t('editor.crumbNew') }]
      : readOnly ? [{ label: code! }, { label: t('protocol.details') }] : [{ label: code!, to: workspaceRoutes(channel.channelId).api(code!) }, { label: t('protocol.edit') }])];
  const title = creating ? t('protocol.newApi') : readOnly ? t('api.viewTitle', { code }) : t('api.editTitle', { code });
  const tabs: { key: EditorTab; label: string; children: ReactNode }[] = [
    { key: 'interface', label: t('api.tab.interface'), children: <InterfaceTab {...{ creating, disabled, apiCode, setApiCode, apiName, setApiName, description,
      setDescription, draft, update, channel, requestFields, touch, detail }} /> },
    { key: 'request', label: counted(t('api.tab.request'), requestFields.length), children: <>
      <FieldTable value={requestFields} locations={allLocations} readOnly={disabled} fixedValues={fixedValues}
        onSourceChange={(previous, key, expression) => {
          const kept = draft.fixedValues.filter(item => normalizeKey(item.key) !== previous && normalizeKey(item.key) !== key);
          update({ fixedValues: key && expression !== null ? [...kept, { key: rawKey(key, requestFields), expression }] : kept });
        }}
        hint={t('api.requestHint')}
        serverErrorRows={errorRows(editor.fieldErrors, 'requestFields')} onChange={fields => { setRequestFields(fields); touch(); }} />
      <Note items={[t('api.requestNote.path'), t('api.requestNote.header'), t('api.requestNote.response')]} /></> },
    { key: 'response', label: counted(t('mapping.group.RESPONSE'), responseFields.length), children: <FieldTable value={responseFields}
      locations={responseLocations} readOnly={disabled} hint={t('api.responseHint')}
      serverErrorRows={errorRows(editor.fieldErrors, 'responseFields')} onChange={fields => { setResponseFields(fields); touch(); }} /> },
    { key: 'params', label: t('api.tab.params'), children: <ParamsTab draft={draft} update={update} requestFields={requestFields} disabled={disabled} /> },
    { key: 'scripts', label: counted(t('protocol.column.scripts'), draft.requestScripts.length + draft.responseScripts.length), children:
      <ScriptsTab draft={draft} update={update} scripts={scripts} paramPaths={scriptParamPaths} disabled={disabled} /> },
    { key: 'rules', label: counted(t('api.statusRules'), draft.statusRules.length), children:
      <RulesTab rules={draft.statusRules} onChange={statusRules => update({ statusRules })} disabled={disabled} /> },
    { key: 'outputs', label: t('api.outputs'), children: <OutputsTab draft={draft} update={update} readPaths={readPaths}
      functions={capabilities?.functions ?? []} disabled={disabled} /> },
  ];
  return <>
    <PageHeader crumbs={crumbs} title={title} readOnlyHint={!canManage ? t('editor.noManage') : undefined}
      actions={readOnly ? canManage && detail && <Button type="primary" onClick={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))}>{t('protocol.edit')}</Button>
        : <><Button loading={validating} onClick={() => void validate()}>{t('editor.validate')}</Button>
          <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>{tCommon('action.save')}</Button></>} />
    {editor.conflict && detail && <ConflictBanner code={detail.apiCode} baseVersion={detail.version} latest={latest} onReload={onReload}
      onCopy={() => copy({ apiName, description, requestFields, responseFields, definition: toDefinition(draft) })} />}
    <Tabs className="xapi-tabs" activeKey={tab} onChange={key => setTab(key as EditorTab)}
      items={tabs.map(item => ({ ...item, children: <div className="xapi-tab-body">{item.children}</div> }))} />
    <ValidationPanel issues={issues} stale={stale} onLocate={issue => setTab(issueTab(issue.location))} />
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}

function InterfaceTab({ creating, disabled, apiCode, setApiCode, apiName, setApiName, description, setDescription, draft, update, channel,
  requestFields, touch, detail }: {
  creating: boolean; disabled: boolean; apiCode: string; setApiCode: (v: string) => void; apiName: string; setApiName: (v: string) => void;
  description: string; setDescription: (v: string) => void; draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void;
  channel: ProtocolChannel; requestFields: ApiField[]; touch: () => void; detail?: IntegrationApiDetail;
}) {
  const t = useT('channel');
  const transport = (patch: Partial<DefinitionDraft['transport']>) => update({ transport: { ...draft.transport, ...patch } });
  const endpoint = channel.endpoints.find(item => item.endpointCode === draft.transport.endpointCode);
  const pathFields = requestFields.filter(field => field.fieldLocation === 'PATH').map(field => field.fieldPath);
  const variables = pathVariables(draft.transport.pathTemplate);
  const extraFields = pathFields.filter(name => !variables.includes(name));
  const value = (editable: ReactNode, shown: ReactNode, mono?: boolean) => disabled ? <ReadOnlyValue value={shown} mono={mono} /> : editable;
  return <>
    <Panel title={t('api.basicInfo')}><div className="xapi-grid xapi-grid-3">
      <Field label={t('editor.codeReadOnlyAfterCreate')} hint={creating && apiCode && !codePattern.test(apiCode) ? t('api.codePattern') : undefined}>
        {creating && !disabled ? <Input value={apiCode} maxLength={64} className="xapi-mono-input" placeholder={t('api.codePlaceholder')}
          onChange={event => { setApiCode(event.target.value); touch(); }} /> : <ReadOnlyValue mono value={apiCode} />}</Field>
      <Field label={t('form.name')}>{value(<Input value={apiName} maxLength={128} onChange={event => { setApiName(event.target.value); touch(); }} />, apiName)}</Field>
      <Field label={t('mids.note')}>{value(<Input value={description} maxLength={512} onChange={event => { setDescription(event.target.value); touch(); }} />, description)}</Field>
    </div></Panel>
    <Panel title={t('api.transport')}>
      <div className="xapi-grid xapi-grid-http">
        <Field label="Method">{value(<Select value={draft.transport.method} className="xapi-mono-input"
          options={methods.map(item => ({ value: item, label: item }))} onChange={method => transport({ method })} />, draft.transport.method, true)}</Field>
        <Field label={t('api.endpointCode')} hint={endpoint ? <Mono tone="muted">{endpoint.baseUrl}</Mono> : channel.endpoints.length ? t('api.endpointRequired') : t('api.noEndpoints')}>
          {value(<Select value={draft.transport.endpointCode || undefined} className="xapi-mono-input"
            options={channel.endpoints.map(item => ({ value: item.endpointCode, label: item.endpointCode }))}
            onChange={endpointCode => transport({ endpointCode })} />, draft.transport.endpointCode, true)}</Field>
        <Field label={t('api.pathTemplate')} hint={<span className="xapi-path-checks">
          {variables.map(name => pathFields.includes(name)
            ? <span key={name} className="ok">{t('api.pathVariableOk', { name })}</span>
            : <span key={name} className="warn">{t('api.pathVariableMissing', { name })}</span>)}
          {extraFields.map(name => <span key={name} className="warn">{t('api.hint.pathFieldUnused', { name })}</span>)}
          {!variables.length && !extraFields.length && t('api.pathTemplateHint')}</span>}>
          {value(<Input value={draft.transport.pathTemplate} maxLength={512} className="xapi-mono-input"
            onChange={event => transport({ pathTemplate: event.target.value })} />, draft.transport.pathTemplate, true)}</Field>
      </div>
      <div className="xapi-grid xapi-grid-4">
        <Field label={t('api.connectTimeout')} hint={t('api.range', { min: '100', max: '10000' })}>{value(<InputNumber min={100} max={10000} value={draft.transport.connectTimeoutMs} style={{ width: '100%' }}
          onChange={next => transport({ connectTimeoutMs: next ?? 2000 })} />, draft.transport.connectTimeoutMs, true)}</Field>
        <Field label={t('api.responseTimeout')} hint={t('api.range', { min: '100', max: '30000' })}>{value(<InputNumber min={100} max={30000} value={draft.transport.responseTimeoutMs} style={{ width: '100%' }}
          onChange={next => transport({ responseTimeoutMs: next ?? 10000 })} />, draft.transport.responseTimeoutMs, true)}</Field>
        <Field label={t('api.requestEncoding')} hint="JSON / FORM_URLENCODED / NONE">{value(<Select value={draft.requestEncoding} className="xapi-mono-input"
          options={requestEncodings.map(item => ({ value: item, label: item }))} onChange={requestEncoding => update({ requestEncoding })} />,
          draft.requestEncoding, true)}</Field>
        <Field label={t('api.responseDecoding')} hint={t('api.responseDecodingHint')}>{value(<Select value={draft.responseDecoding} className="xapi-mono-input"
          options={responseDecodings.map(item => ({ value: item, label: item }))} onChange={responseDecoding => update({ responseDecoding })} />,
          draft.responseDecoding, true)}</Field>
        <Field label={t('api.decimalEncoding')} hint={t('api.decimalEncodingHint')}>{value(<Select value={draft.decimalEncoding} className="xapi-mono-input"
          options={decimalEncodings.map(item => ({ value: item, label: item }))} onChange={decimalEncoding => update({ decimalEncoding })} />,
          draft.decimalEncoding, true)}</Field>
      </div>
      <Field label={t('api.cardFields')} hint={t('api.cardFieldsHint')}>{value(<Select mode="multiple"
        value={draft.cardFields} className="xapi-mono-input" placeholder={t('accountConfig.wizard.none')}
        options={requestFields.filter(field => field.fieldLocation === 'BODY' && field.dataType === 'STRING' && !field.fieldPath.includes('[]'))
          .map(field => ({ value: `BODY:${field.fieldPath}`, label: `BODY:${field.fieldPath}` }))}
        onChange={cardFields => update({ cardFields })} />, draft.cardFields.length ? draft.cardFields.join(t('list.separator')) : t('accountConfig.wizard.none'), true)}</Field>
      <Field label={t('api.credentialFields')} hint={t('api.credentialFieldsHint')}>{value(<Select mode="multiple"
        value={draft.credentialFields} className="xapi-mono-input" placeholder={t('accountConfig.wizard.none')}
        options={requestFields.filter(field => field.fieldLocation === 'BODY' && field.dataType === 'STRING' && !field.fieldPath.includes('[]'))
          .map(field => ({ value: `BODY:${field.fieldPath}`, label: `BODY:${field.fieldPath}` }))}
        onChange={credentialFields => update({ credentialFields })} />, draft.credentialFields.length ? draft.credentialFields.join(t('list.separator')) : t('accountConfig.wizard.none'), true)}</Field>
      <Note items={[t('api.transportNote')]} />
    </Panel>
    {detail && <Panel title={t('protocol.column.referencedBy')}>
      <p className="xapi-desc">{detail.referencedBy.length ? <>{detail.referencedBy.map((item, index) => <span key={item.standardApiCode}>
        {index > 0 && t('error.fieldSeparator')}{richText(t, 'api.referencedItem', {
          api: <Link to={workspaceRoutes(channel.channelId).implementation(item.standardApiCode)}>{item.standardApiCode}</Link>,
          nodes: <Mono>{item.nodeIds.join(t('list.separator'))}</Mono> })}</span>)}{t('api.referencedSuffix')}</>
        : t('api.noReferences')}</p>
    </Panel>}
  </>;
}

/** 通用编辑弹窗：确定时把表单值交给调用方；用于调用参数、固定值、状态规则、输出与脚本引用。 */
function EditModal<T extends object>({ title, initial, onCancel, onSave, children, width = 520 }: {
  title: string; initial: T; onCancel: () => void; onSave: (value: T) => void; children: ReactNode; width?: number;
}) {
  const t = useT('channel');
  const [form] = Form.useForm<T>();
  return <Modal open width={width} title={title} okText={t('field.apply')} onCancel={onCancel} onOk={() => form.submit()} destroyOnHidden>
    <Form form={form} layout="vertical" requiredMark={false} initialValues={initial} onFinish={values => onSave({ ...initial, ...values })}>
      {children}</Form>
  </Modal>;
}

/** 列表行的“编辑 / 删除”。 */
function RowOps({ onEdit, onRemove, extra }: { onEdit: () => void; onRemove: () => void; extra?: ReactNode }) {
  const t = useT('channel');
  return <span className="xapi-ops">{extra}<button type="button" className="xapi-link" onClick={onEdit}>{t('protocol.edit')}</button>
    <button type="button" className="xapi-link" onClick={onRemove}>{t('endpointEditor.delete')}</button></span>;
}

const typeOption = (type: string) => ({ value: type, label: type });

function ParamsTab({ draft, update, requestFields, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; requestFields: ApiField[]; disabled: boolean;
}) {
  const t = useT('channel');
  const [param, setParam] = useState<{ index: number | null; value: ParamDeclaration } | null>(null);
  const [fixed, setFixed] = useState<{ index: number | null; value: FixedValue } | null>(null);
  const usage = (name: string) => {
    const ref = `params.${name}`;
    const scripts = [...draft.requestScripts, ...draft.responseScripts]
      .filter(use => Object.values(use.params).some(binding => binding.kind === 'PATH' ? binding.path === ref : binding.kind === 'EXPRESSION' && binding.expression.includes(ref)))
      .map(use => t('api.usage.script', { id: use.id }));
    const rules = draft.statusRules.some(rule => rule.condition.includes(ref)) ? [t('api.statusRules')] : [];
    const outputs = [...draft.outputs.SUCCESS, ...draft.outputs.FAIL].some(output => JSON.stringify(output.value).includes(ref)) ? [t('api.outputs')] : [];
    return [...scripts, ...rules, ...outputs];
  };
  const fieldKeys = requestFields.map(fieldKey);
  return <>
    <Panel className="xapi-plain" title={t('mapping.group.PARAMS')} extra={!disabled && <Button onClick={() => setParam({ index: null, value: { name: '', dataType: 'STRING', required: true } })}>{t('script.addParameter')}</Button>}>
      <p className="xapi-desc">{t('api.params.hint')}</p>
      <div className="xapi-table-wrap"><Table rowKey={(_, index) => String(index)} pagination={false} dataSource={draft.params}
        locale={{ emptyText: t('api.params.empty') }} columns={[
          { title: t('form.name'), width: 180, render: (_, row) => <Mono>{row.name}</Mono> },
          { title: t('capability.type'), width: 200, render: (_, row) => row.dataType === 'PROTECTED_HANDLE' ? <HandleMark /> : <Mono>{row.dataType}</Mono> },
          { title: t('form.required'), width: 70, render: (_, row) => row.required ? t('field.yes') : t('field.no') },
          { title: t('mids.note'), ellipsis: true, render: (_, row) => row.description || '—' },
          { title: t('api.params.usedIn'), width: 200, render: (_, row) => usage(row.name).join(t('list.separator')) || '—' },
          ...(disabled ? [] : [{ title: t('grants.actions'), width: 110, render: (_: unknown, row: ParamDeclaration, index: number) =>
            <RowOps onEdit={() => setParam({ index, value: row })} onRemove={() => update({ params: draft.params.filter((_, i) => i !== index) })} /> }]),
        ]} /></div>
    </Panel>
    <Panel className="xapi-plain" title={t('api.fixed.title')} extra={!disabled && <Button onClick={() => setFixed({ index: null, value: { key: '', expression: '' } })}>{t('api.fixed.add')}</Button>}>
      <p className="xapi-desc">{t('api.fixed.hint')}</p>
      <div className="xapi-table-wrap"><Table rowKey={(_, index) => String(index)} pagination={false} dataSource={draft.fixedValues}
        locale={{ emptyText: t('api.fixed.empty') }} columns={[
          { title: t('api.tab.request'), width: 320, render: (_, row) => <Mono>{row.key.replace(':', ' ')}</Mono> },
          { title: t('api.fixed.expression'), render: (_, row) => <span className="xapi-code-block">{row.expression}</span> },
          ...(disabled ? [] : [{ title: t('grants.actions'), width: 110, render: (_: unknown, row: FixedValue, index: number) =>
            <RowOps onEdit={() => setFixed({ index, value: row })} onRemove={() => update({ fixedValues: draft.fixedValues.filter((_, i) => i !== index) })} /> }]),
        ]} /></div>
    </Panel>
    {param && <EditModal title={param.index === null ? t('api.param.addTitle') : t('api.param.editTitle')} initial={param.value} onCancel={() => setParam(null)}
      onSave={value => { const next = { ...value, name: value.name.trim(), description: value.description?.trim() || null };
        update({ params: param.index === null ? [...draft.params, next] : draft.params.map((item, i) => i === param.index ? next : item) }); setParam(null); }}>
      <Form.Item name="name" label={t('form.name')} rules={[{ required: true, pattern: /^[a-z][A-Za-z0-9]{0,63}$/, message: t('script.paramNamePattern') }]}>
        <Input className="xapi-mono-input" maxLength={64} /></Form.Item>
      <Form.Item name="dataType" label={t('capability.type')}><Select className="xapi-mono-input" options={paramTypes.map(typeOption)} /></Form.Item>
      <div className="xapi-inline-switch" style={{ marginBottom: 24 }}><Form.Item name="required" valuePropName="checked" noStyle><Switch size="small" /></Form.Item><span>{t('form.required')}</span></div>
      <Form.Item name="description" label={t('mids.note')}><Input maxLength={512} /></Form.Item>
    </EditModal>}
    {fixed && <EditModal title={fixed.index === null ? t('api.fixed.add') : t('api.fixed.editTitle')} initial={fixed.value} onCancel={() => setFixed(null)}
      onSave={value => { update({ fixedValues: fixed.index === null ? [...draft.fixedValues, value] : draft.fixedValues.map((item, i) => i === fixed.index ? value : item) }); setFixed(null); }}>
      <Form.Item name="key" label={t('api.tab.request')} rules={[{ required: true, message: t('api.fixed.fieldRequired') }]}>
        <Select className="xapi-mono-input" options={fieldKeys.map(key => ({ value: key, label: key.replace(':', ' ') }))} /></Form.Item>
      <Form.Item name="expression" label={t('api.fixed.expression')} rules={[{ required: true, whitespace: true, message: t('api.fixed.expressionRequired') }]}
        extra={t('api.fixed.expressionHint')}>
        <Input className="xapi-mono-input" maxLength={4096} /></Form.Item>
    </EditModal>}
  </>;
}

function ScriptsTab({ draft, update, scripts, paramPaths, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; scripts: IntegrationScriptSummary[];
  paramPaths: string[]; disabled: boolean;
}) {
  const t = useT('channel');
  const [editing, setEditing] = useState<{ stage: 'REQUEST' | 'RESPONSE'; index: number | null; value: ScriptUse } | null>(null);
  const handleParams = new Set(draft.params.filter(param => param.dataType === 'PROTECTED_HANDLE').map(param => `params.${param.name}`));
  const group = (stage: 'REQUEST' | 'RESPONSE') => {
    const key = stage === 'REQUEST' ? 'requestScripts' : 'responseScripts';
    const uses = draft[key]; const available = scripts.filter(script => script.executionStage === stage);
    const set = (next: ScriptUse[]) => update({ [key]: next } as Partial<DefinitionDraft>);
    return <div key={stage} className="xapi-group">
      <div className="xapi-group-head"><div><span className="xapi-kind">{stage}</span>
        <span className="xapi-desc">{stage === 'REQUEST' ? t('api.scripts.requestTiming') : t('api.scripts.responseTiming')}</span></div>
        {!disabled && <span className="xapi-ops">{!available.length && <span className="xapi-field-hint">{t('api.scripts.noneForStage', { stage })}</span>}
          <Button disabled={!available.length} onClick={() => setEditing({ stage, index: null,
            value: { id: nextScriptUseId([...draft.requestScripts, ...draft.responseScripts]), scriptCode: available[0]?.scriptCode ?? '', stage, params: {} } })}>
            {t('api.scripts.reference')}</Button></span>}</div>
      {uses.length === 0 && <div className="xapi-field-hint">{stage === 'REQUEST' ? t('api.scripts.noRequest') : t('api.scripts.noResponse')}</div>}
      {uses.map((use, index) => {
        const script = scripts.find(item => item.scriptCode === use.scriptCode);
        const names = script?.parameterNames ?? Object.keys(use.params);
        return <div key={`${use.id}:${index}`} className="xapi-card-row"><span className="xapi-seq">{index + 1}</span>
          <div className="xapi-card-main"><Mono>{use.id}</Mono>
            <span className="xapi-field-hint">{use.scriptCode} · {script?.scriptName ?? t('api.scripts.missing')}</span>
            {names.length ? names.map(name => {
              const binding = use.params[name];
              const handle = binding?.kind === 'PATH' && handleParams.has(binding.path);
              return <div key={name} className="xapi-binding-line">{name}<span className="xapi-arrow">←</span>
                {handle && <span className="xapi-handle">{lockMark} </span>}<span className="xapi-mono-ref">{binding ? (binding.kind === 'PATH' ? binding.path
                  : binding.kind === 'EXPRESSION' ? binding.expression : binding.function) : t('binding.unassigned')}</span></div>;
            }) : <span className="xapi-field-hint">{t('script.noParameters')}</span>}
          </div>
          {!disabled && <span className="xapi-ops">
            <button type="button" className="xapi-link" disabled={index === 0} onClick={() => set(moveItem(uses, index, -1))}>↑</button>
            <button type="button" className="xapi-link" disabled={index === uses.length - 1} onClick={() => set(moveItem(uses, index, 1))}>↓</button>
            <button type="button" className="xapi-link" onClick={() => setEditing({ stage, index, value: use })}>{t('protocol.edit')}</button>
            <button type="button" className="xapi-link" onClick={() => set(uses.filter((_, i) => i !== index))}>{t('api.scripts.remove')}</button></span>}
        </div>;
      })}
    </div>;
  };
  return <>
    <p className="xapi-desc">{t('api.scripts.hint')}</p>
    {group('REQUEST')}{group('RESPONSE')}
    <Note items={[t('api.scripts.note.raw'), t('api.scripts.note.referenced')]} />
    {editing && <ScriptUseModal editing={editing} scripts={scripts.filter(script => script.executionStage === editing.stage)} paramPaths={paramPaths}
      onCancel={() => setEditing(null)} onSave={value => {
        const key = editing.stage === 'REQUEST' ? 'requestScripts' : 'responseScripts';
        const uses = draft[key];
        update({ [key]: editing.index === null ? [...uses, value] : uses.map((item, i) => i === editing.index ? value : item) } as Partial<DefinitionDraft>);
        setEditing(null);
      }} />}
  </>;
}

function ScriptUseModal({ editing, scripts, paramPaths, onCancel, onSave }: {
  editing: { stage: 'REQUEST' | 'RESPONSE'; index: number | null; value: ScriptUse }; scripts: IntegrationScriptSummary[];
  paramPaths: string[]; onCancel: () => void; onSave: (value: ScriptUse) => void;
}) {
  const t = useT('channel');
  const [value, setValue] = useState(editing.value);
  const names = scripts.find(script => script.scriptCode === value.scriptCode)?.parameterNames ?? [];
  return <Modal open width={620} title={editing.index === null ? t('api.scripts.referenceTitle', { stage: editing.stage }) : t('api.scripts.editReference')} okText={t('field.apply')}
    okButtonProps={{ disabled: !value.id.trim() || !value.scriptCode }} onCancel={onCancel}
    onOk={() => onSave({ ...value, id: value.id.trim() })}>
    <div className="xapi-form-stack">
      <Field label={t('api.scripts.referenceId')}><Input value={value.id} maxLength={64} className="xapi-mono-input" onChange={event => setValue({ ...value, id: event.target.value })} /></Field>
      <Field label={t('api.scripts.sharedScript')}><Select value={value.scriptCode || undefined} className="xapi-mono-input"
        options={scripts.map(script => ({ value: script.scriptCode, label: `${script.scriptCode} · ${script.scriptName}` }))}
        onChange={scriptCode => setValue({ ...value, scriptCode, params: {} })} /></Field>
      {names.map(name => <Field key={name} label={<Mono>{name}</Mono>}>
        <BindingInput value={value.params[name]} kinds={['PATH', 'EXPRESSION']} paths={paramPaths}
          onChange={binding => setValue({ ...value, params: { ...value.params, [name]: binding } })} /></Field>)}
      {!names.length && <span className="xapi-field-hint">{t('api.scripts.noParams')}</span>}
    </div>
  </Modal>;
}

function RulesTab({ rules, onChange, disabled }: { rules: StatusRule[]; onChange: (rules: StatusRule[]) => void; disabled: boolean }) {
  const t = useT('channel');
  const [editing, setEditing] = useState<{ index: number | null; value: StatusRule } | null>(null);
  type Row = { rule: StatusRule | null; index: number };
  const rows: Row[] = [...rules.map((rule, index) => ({ rule, index })), { rule: null, index: rules.length }];
  return <>
    <div className="xapi-toolbar"><span className="xapi-desc">{t('api.rules.hint')}</span>
      {!disabled && <Button onClick={() => setEditing({ index: null, value: { condition: '', executeStatus: 'SUCCESS' } })}>{t('api.rules.add')}</Button>}</div>
    <div className="xapi-table-wrap"><Table<Row> rowKey="index" pagination={false} dataSource={rows}
      rowClassName={row => row.rule ? '' : 'xapi-row-fixed'} columns={[
        { title: t('api.rules.order'), width: 70, render: (_, row) => row.rule ? row.index + 1 : '—' },
        { title: t('api.rules.condition'), render: (_, row) => row.rule ? <span className="xapi-code-block">{row.rule.condition || ' '}</span>
          : t('api.rules.otherwise') },
        { title: t('api.rules.executeStatus'), width: 120, render: (_, row) => <StatusTag status={row.rule ? row.rule.executeStatus : 'ERROR'} /> },
        ...(disabled ? [] : [{ title: t('grants.actions'), width: 190, render: (_: unknown, row: Row) => row.rule && <RowOps
          extra={<><button type="button" className="xapi-link" disabled={row.index === 0} onClick={() => onChange(moveItem(rules, row.index, -1))}>{t('api.rules.moveUp')}</button>
            <button type="button" className="xapi-link" disabled={row.index === rules.length - 1} onClick={() => onChange(moveItem(rules, row.index, 1))}>{t('api.rules.moveDown')}</button></>}
          onEdit={() => setEditing({ index: row.index, value: row.rule! })} onRemove={() => onChange(rules.filter((_, i) => i !== row.index))} /> }]),
      ]} /></div>
    <Note items={[<>{richText(t, 'api.rules.note.readable', { status: <Mono>http.responseStatus</Mono>, headers: <Mono>http.responseHeaders</Mono>,
      body: <Mono>http.responseBody</Mono>, params: <Mono>params.*</Mono> })}</>, t('api.rules.note.error')]} />
    {editing && <EditModal width={640} title={editing.index === null ? t('api.rules.addTitle') : t('api.rules.editTitle')} initial={editing.value} onCancel={() => setEditing(null)}
      onSave={value => { onChange(editing.index === null ? [...rules, value] : rules.map((item, i) => i === editing.index ? value : item)); setEditing(null); }}>
      <Form.Item name="condition" label={t('api.rules.condition')} rules={[{ required: true, whitespace: true, message: t('api.rules.conditionRequired') }]}>
        <Input.TextArea autoSize={{ minRows: 3 }} maxLength={4096} className="xapi-mono-input"
          placeholder="http.responseStatus == 201 && http.responseBody.state == 'CAPTURED'" /></Form.Item>
      <Form.Item name="executeStatus" label={t('api.rules.executeStatus')}><Radio.Group options={['SUCCESS', 'FAIL'].map(typeOption)} optionType="button" /></Form.Item>
    </EditModal>}
  </>;
}

function OutputsTab({ draft, update, readPaths, functions, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; readPaths: string[];
  functions: Capabilities['functions']; disabled: boolean;
}) {
  const t = useT('channel');
  const [editing, setEditing] = useState<{ status: 'SUCCESS' | 'FAIL'; index: number | null; value: OutputDeclaration } | null>(null);
  const branch = (status: 'SUCCESS' | 'FAIL') => {
    const rows = draft.outputs[status];
    const set = (next: OutputDeclaration[]) => update({ outputs: { ...draft.outputs, [status]: next } });
    return <div key={status} className="xapi-group">
      <div className="xapi-group-head"><div><StatusTag status={status} /><span className="xapi-desc">{t('api.outputs.count', { count: rows.length })}</span></div>
        {!disabled && <Button onClick={() => setEditing({ status, index: null, value: { name: '', dataType: 'STRING', required: false, value: emptyBinding('PATH') } })}>{t('api.outputs.add')}</Button>}</div>
      <div className="xapi-table-wrap"><Table rowKey={(_, index) => String(index)} pagination={false} dataSource={rows} locale={{ emptyText: t('api.outputs.empty') }} columns={[
        { title: t('form.name'), width: 180, render: (_, row) => <Mono>{row.name}</Mono> },
        { title: t('capability.type'), width: 110, render: (_, row) => <Mono>{row.dataType}</Mono> },
        { title: t('form.required'), width: 70, render: (_, row) => row.required ? t('field.yes') : t('field.no') },
        { title: t('api.outputs.value'), render: (_, row) => <BindingView binding={row.value} /> },
        ...(disabled ? [] : [{ title: t('grants.actions'), width: 110, render: (_: unknown, row: OutputDeclaration, index: number) =>
          <RowOps onEdit={() => setEditing({ status, index, value: row })} onRemove={() => set(rows.filter((_, i) => i !== index))} /> }]),
      ]} /></div>
    </div>;
  };
  return <>
    <p className="xapi-desc">{t('api.outputs.hint')}</p>
    {branch('SUCCESS')}{branch('FAIL')}
    <div className="xapi-group"><div className="xapi-group-head"><div><StatusTag status="ERROR" /><span className="xapi-desc">{t('api.outputs.errorFixed')}</span></div></div></div>
    {editing && <OutputModal editing={editing} readPaths={readPaths} functions={functions} onCancel={() => setEditing(null)}
      onSave={value => {
        const rows = draft.outputs[editing.status];
        update({ outputs: { ...draft.outputs, [editing.status]: editing.index === null ? [...rows, value] : rows.map((item, i) => i === editing.index ? value : item) } });
        setEditing(null);
      }} />}
  </>;
}

function OutputModal({ editing, readPaths, functions, onCancel, onSave }: {
  editing: { status: 'SUCCESS' | 'FAIL'; index: number | null; value: OutputDeclaration }; readPaths: string[];
  functions: Capabilities['functions']; onCancel: () => void; onSave: (value: OutputDeclaration) => void;
}) {
  const t = useT('channel');
  const [value, setValue] = useState(editing.value);
  const nameValid = /^[a-z][A-Za-z0-9]{0,63}$/.test(value.name.trim());
  return <Modal open width={620} title={editing.index === null ? t('api.outputs.addTitle', { status: editing.status }) : t('api.outputs.editTitle', { status: editing.status })} okText={t('field.apply')}
    okButtonProps={{ disabled: !nameValid }} onCancel={onCancel} onOk={() => onSave({ ...value, name: value.name.trim() })}>
    <div className="xapi-form-stack">
      <div className="xapi-form-row">
        <Field label={t('form.name')} hint={value.name && !nameValid ? t('script.paramNamePattern') : undefined}>
          <Input value={value.name} maxLength={64} className="xapi-mono-input" onChange={event => setValue({ ...value, name: event.target.value })} /></Field>
        <Field label={t('capability.type')}><Select value={value.dataType} className="xapi-mono-input" options={fieldDataTypes.map(typeOption)}
          onChange={(dataType: FieldDataType) => setValue({ ...value, dataType })} /></Field>
      </div>
      <div className="xapi-inline-switch"><Switch size="small" checked={value.required} onChange={required => setValue({ ...value, required })} /><span>{t('form.required')}</span></div>
      <Field label={t('api.outputs.value')} hint={t('api.outputs.valueHint')}>
        <BindingInput value={value.value} kinds={['PATH', 'EXPRESSION', 'FUNCTION']} paths={readPaths} functions={functions}
          onChange={(binding: Binding) => setValue({ ...value, value: binding })} /></Field>
    </div>
  </Modal>;
}

export type { ParamType };
