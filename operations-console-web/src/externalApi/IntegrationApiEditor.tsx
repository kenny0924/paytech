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
import { Field, HandleMark, Mono, Note, PageHeader, Panel, ReadOnlyValue, StatusTag, counted } from './ui';

/** 所属渠道：编码用于管理接口路径，出站地址来自渠道详情的 endpoints。 */
export interface ProtocolChannel { channelId: string; channelCode: string; endpoints: readonly Endpoint[] }
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; channel: ProtocolChannel; apiCode: string | null }
const allLocations: FieldLocation[] = ['HEADER', 'BODY', 'QUERY', 'PATH'];
const responseLocations: FieldLocation[] = ['HEADER', 'BODY'];
const capabilityPath = `${externalApiBase}/editor-capabilities`;
const scriptPage = (value: unknown) => decodePage(value, decodeScriptSummary, item => item.scriptCode);
const noManage = '只读 · 无“外部 API 管理”权限';

/** 由规范化键（HEADER 小写）还原为字段原有写法的键，例如 HEADER:Content-Type。 */
function rawKey(key: string, fields: ApiField[]): string {
  const [location, ...rest] = key.split(':');
  const path = rest.join(':');
  const field = fields.find(item => item.fieldLocation === location && (location === 'HEADER' ? item.fieldPath.toLowerCase() === path : item.fieldPath === path));
  return field ? `${field.fieldLocation}:${field.fieldPath}` : key;
}

/** 原子 API 编辑与详情（/channel/channels/:channelId/protocol/apis/:apiCode，?mode=edit；新建为 /apis/new）。 */
export function IntegrationApiEditor({ resources, onAuthorizationChanged, channel, apiCode }: Props) {
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
      .catch(error => { if (!abort.signal.aborted) { setScripts([]); notify(error, '公共脚本加载失败'); } });
    return () => abort.abort();
  }, [integrationCode, notify]);
  if (creating && !canManage) return <Alert type="warning" showIcon title="当前没有协议配置维护权限" />;
  if (api.loading || capabilities.loading || scripts === undefined) return <Loading />;
  if (!creating && !api.data) return <LoadFailure title="原子 API 加载失败" failure={api.failure} retry={api.refresh} />;
  return <ApiForm key={`${api.data?.version}:${readOnly}`} channel={channel} detail={api.data} readOnly={readOnly}
    canManage={canManage} capabilities={capabilities.data} scripts={scripts} onAuthorizationChanged={onAuthorizationChanged}
    onReload={api.refresh} />;
}

interface FormProps {
  channel: ProtocolChannel; detail?: IntegrationApiDetail; readOnly: boolean; canManage: boolean;
  capabilities?: Capabilities; scripts: IntegrationScriptSummary[]; onAuthorizationChanged: () => void; onReload: () => void;
}

function ApiForm({ channel, detail, readOnly, canManage, capabilities, scripts, onAuthorizationChanged, onReload }: FormProps) {
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
      if (!result.issues.length) void message.success('校验通过，配置可以保存');
      const first = result.issues.find(issue => issue.severity === 'ERROR');
      if (first) setTab(issueTab(first.location));
    } catch (error) { notify(error, '校验未完成'); } finally { setValidating(false); }
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
  const crumbs = [...workspaceCrumbs(channel.channelId, integrationCode, 'apis'), ...(creating ? [{ label: '新建' }]
      : readOnly ? [{ label: code! }, { label: '详情' }] : [{ label: code!, to: workspaceRoutes(channel.channelId).api(code!) }, { label: '编辑' }])];
  const title = creating ? '新建原子 API' : readOnly ? `原子 API · ${code}` : `编辑原子 API · ${code}`;
  const tabs: { key: EditorTab; label: string; children: ReactNode }[] = [
    { key: 'interface', label: '接口', children: <InterfaceTab {...{ creating, disabled, apiCode, setApiCode, apiName, setApiName, description,
      setDescription, draft, update, channel, requestFields, touch, detail }} /> },
    { key: 'request', label: counted('请求字段', requestFields.length), children: <>
      <FieldTable value={requestFields} locations={allLocations} readOnly={disabled} fixedValues={fixedValues}
        onSourceChange={(previous, key, expression) => {
          const kept = draft.fixedValues.filter(item => normalizeKey(item.key) !== previous && normalizeKey(item.key) !== key);
          update({ fixedValues: key && expression !== null ? [...kept, { key: rawKey(key, requestFields), expression }] : kept });
        }}
        hint="请求结构按渠道文档逐行维护；值在流程节点里填写。标“固定”的字段由协议固定值提供，节点不能再赋值。"
        serverErrorRows={errorRows(editor.fieldErrors, 'requestFields')} onChange={fields => { setRequestFields(fields); touch(); }} />
      <Note items={['PATH 字段与路径模板中的 {变量} 一一对应，值按单个路径段编码。', 'HEADER 名称大小写不敏感；同一位置下路径不能重复。',
        '“响应字段”页签结构相同，只允许 HEADER、BODY。']} /></> },
    { key: 'response', label: counted('响应字段', responseFields.length), children: <FieldTable value={responseFields}
      locations={responseLocations} readOnly={disabled} hint="响应结构按渠道文档维护；出现即校验类型，必填只在判定为 SUCCESS 时校验。"
      serverErrorRows={errorRows(editor.fieldErrors, 'responseFields')} onChange={fields => { setResponseFields(fields); touch(); }} /> },
    { key: 'params', label: '调用参数与固定值', children: <ParamsTab draft={draft} update={update} requestFields={requestFields} disabled={disabled} /> },
    { key: 'scripts', label: counted('脚本', draft.requestScripts.length + draft.responseScripts.length), children:
      <ScriptsTab draft={draft} update={update} scripts={scripts} paramPaths={scriptParamPaths} disabled={disabled} /> },
    { key: 'rules', label: counted('状态规则', draft.statusRules.length), children:
      <RulesTab rules={draft.statusRules} onChange={statusRules => update({ statusRules })} disabled={disabled} /> },
    { key: 'outputs', label: '输出', children: <OutputsTab draft={draft} update={update} readPaths={readPaths}
      functions={capabilities?.functions ?? []} disabled={disabled} /> },
  ];
  return <>
    <PageHeader crumbs={crumbs} title={title} readOnlyHint={!canManage ? noManage : undefined}
      actions={readOnly ? canManage && detail && <Button type="primary" onClick={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))}>编辑</Button>
        : <><Button loading={validating} onClick={() => void validate()}>校验</Button>
          <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>保存</Button></>} />
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
  const transport = (patch: Partial<DefinitionDraft['transport']>) => update({ transport: { ...draft.transport, ...patch } });
  const endpoint = channel.endpoints.find(item => item.endpointCode === draft.transport.endpointCode);
  const pathFields = requestFields.filter(field => field.fieldLocation === 'PATH').map(field => field.fieldPath);
  const variables = pathVariables(draft.transport.pathTemplate);
  const extraFields = pathFields.filter(name => !variables.includes(name));
  const value = (editable: ReactNode, shown: ReactNode, mono?: boolean) => disabled ? <ReadOnlyValue value={shown} mono={mono} /> : editable;
  return <>
    <Panel title="基本信息"><div className="xapi-grid xapi-grid-3">
      <Field label="编码（创建后只读）" hint={creating && apiCode && !codePattern.test(apiCode) ? '大写字母开头，包含大写字母、数字或下划线' : undefined}>
        {creating && !disabled ? <Input value={apiCode} maxLength={64} className="xapi-mono-input" placeholder="例如 CREATE_ORDER"
          onChange={event => { setApiCode(event.target.value); touch(); }} /> : <ReadOnlyValue mono value={apiCode} />}</Field>
      <Field label="名称">{value(<Input value={apiName} maxLength={128} onChange={event => { setApiName(event.target.value); touch(); }} />, apiName)}</Field>
      <Field label="说明">{value(<Input value={description} maxLength={512} onChange={event => { setDescription(event.target.value); touch(); }} />, description)}</Field>
    </div></Panel>
    <Panel title="HTTP 传输">
      <div className="xapi-grid xapi-grid-http">
        <Field label="Method">{value(<Select value={draft.transport.method} className="xapi-mono-input"
          options={methods.map(item => ({ value: item, label: item }))} onChange={method => transport({ method })} />, draft.transport.method, true)}</Field>
        <Field label="地址代码" hint={endpoint ? <Mono tone="muted">{endpoint.baseUrl}</Mono> : channel.endpoints.length ? '请选择渠道的出站地址' : '渠道还没有出站地址，请先在概览中配置'}>
          {value(<Select value={draft.transport.endpointCode || undefined} className="xapi-mono-input"
            options={channel.endpoints.map(item => ({ value: item.endpointCode, label: item.endpointCode }))}
            onChange={endpointCode => transport({ endpointCode })} />, draft.transport.endpointCode, true)}</Field>
        <Field label="路径模板" hint={<span className="xapi-path-checks">
          {variables.map(name => pathFields.includes(name)
            ? <span key={name} className="ok">变量 {`{${name}}`} ✓ 已有同名 PATH 字段</span>
            : <span key={name} className="warn">变量 {`{${name}}`} ✗ 缺少同名 PATH 字段</span>)}
          {extraFields.map(name => <span key={name} className="warn">PATH 字段 {name} 未出现在路径模板中</span>)}
          {!variables.length && !extraFields.length && '以 / 开头的相对路径'}</span>}>
          {value(<Input value={draft.transport.pathTemplate} maxLength={512} className="xapi-mono-input"
            onChange={event => transport({ pathTemplate: event.target.value })} />, draft.transport.pathTemplate, true)}</Field>
      </div>
      <div className="xapi-grid xapi-grid-4">
        <Field label="连接超时（ms）" hint="100～10000">{value(<InputNumber min={100} max={10000} value={draft.transport.connectTimeoutMs} style={{ width: '100%' }}
          onChange={next => transport({ connectTimeoutMs: next ?? 2000 })} />, draft.transport.connectTimeoutMs, true)}</Field>
        <Field label="响应超时（ms）" hint="100～30000">{value(<InputNumber min={100} max={30000} value={draft.transport.responseTimeoutMs} style={{ width: '100%' }}
          onChange={next => transport({ responseTimeoutMs: next ?? 10000 })} />, draft.transport.responseTimeoutMs, true)}</Field>
        <Field label="请求编码" hint="JSON / FORM_URLENCODED / NONE">{value(<Select value={draft.requestEncoding} className="xapi-mono-input"
          options={requestEncodings.map(item => ({ value: item, label: item }))} onChange={requestEncoding => update({ requestEncoding })} />,
          draft.requestEncoding, true)}</Field>
        <Field label="响应解码" hint="JSON / NONE（交给响应脚本）">{value(<Select value={draft.responseDecoding} className="xapi-mono-input"
          options={responseDecodings.map(item => ({ value: item, label: item }))} onChange={responseDecoding => update({ responseDecoding })} />,
          draft.responseDecoding, true)}</Field>
        <Field label="小数格式" hint="渠道报文中 DECIMAL 字段：STRING 十进制字符串 / NUMBER JSON 数字">{value(<Select value={draft.decimalEncoding} className="xapi-mono-input"
          options={decimalEncodings.map(item => ({ value: item, label: item }))} onChange={decimalEncoding => update({ decimalEncoding })} />,
          draft.decimalEncoding, true)}</Field>
      </div>
      <Field label="卡字段" hint="BODY 字符串字段；只能由读卡初始化项（PAYMENT_CARD）的卡值赋值，执行轨迹与验证证据中遮蔽">{value(<Select mode="multiple"
        value={draft.cardFields} className="xapi-mono-input" placeholder="无"
        options={requestFields.filter(field => field.fieldLocation === 'BODY' && field.dataType === 'STRING' && !field.fieldPath.includes('[]'))
          .map(field => ({ value: `BODY:${field.fieldPath}`, label: `BODY:${field.fieldPath}` }))}
        onChange={cardFields => update({ cardFields })} />, draft.cardFields.length ? draft.cardFields.join('、') : '无', true)}</Field>
      <Field label="凭据字段" hint="BODY 字符串字段；只能由渠道账号凭据（resources.<初始化项>.credentials.<名称>）赋值，执行轨迹与验证证据中遮蔽，请求体不能再 sealJsonBody">{value(<Select mode="multiple"
        value={draft.credentialFields} className="xapi-mono-input" placeholder="无"
        options={requestFields.filter(field => field.fieldLocation === 'BODY' && field.dataType === 'STRING' && !field.fieldPath.includes('[]'))
          .map(field => ({ value: `BODY:${field.fieldPath}`, label: `BODY:${field.fieldPath}` }))}
        onChange={credentialFields => update({ credentialFields })} />, draft.credentialFields.length ? draft.credentialFields.join('、') : '无', true)}</Field>
      <Note items={['实际超时取配置值与流程剩余预算的较小者。不自动重试、不跟随重定向、不保存 Cookie；Host、Content-Length 等由系统处理，不能配置。']} />
    </Panel>
    {detail && <Panel title="被引用">
      <p className="xapi-desc">{detail.referencedBy.length ? <>{detail.referencedBy.map((item, index) => <span key={item.standardApiCode}>
        {index > 0 && '；'}<Link to={workspaceRoutes(channel.channelId).implementation(item.standardApiCode)}>
          {item.standardApiCode}</Link> 流程的节点 <Mono>{item.nodeIds.join('、')}</Mono></span>)}。修改必填请求字段或删除本 API 时，保存校验会列出这些引用。</>
        : '暂无标准 API 实现引用本 API。'}</p>
    </Panel>}
  </>;
}

/** 通用编辑弹窗：确定时把表单值交给调用方；用于调用参数、固定值、状态规则、输出与脚本引用。 */
function EditModal<T extends object>({ title, initial, onCancel, onSave, children, width = 520 }: {
  title: string; initial: T; onCancel: () => void; onSave: (value: T) => void; children: ReactNode; width?: number;
}) {
  const [form] = Form.useForm<T>();
  return <Modal open width={width} title={title} okText="应用" onCancel={onCancel} onOk={() => form.submit()} destroyOnHidden>
    <Form form={form} layout="vertical" requiredMark={false} initialValues={initial} onFinish={values => onSave({ ...initial, ...values })}>
      {children}</Form>
  </Modal>;
}

/** 列表行的“编辑 / 删除”。 */
function RowOps({ onEdit, onRemove, extra }: { onEdit: () => void; onRemove: () => void; extra?: ReactNode }) {
  return <span className="xapi-ops">{extra}<button type="button" className="xapi-link" onClick={onEdit}>编辑</button>
    <button type="button" className="xapi-link" onClick={onRemove}>删除</button></span>;
}

const typeOption = (type: string) => ({ value: type, label: type });

function ParamsTab({ draft, update, requestFields, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; requestFields: ApiField[]; disabled: boolean;
}) {
  const [param, setParam] = useState<{ index: number | null; value: ParamDeclaration } | null>(null);
  const [fixed, setFixed] = useState<{ index: number | null; value: FixedValue } | null>(null);
  const usage = (name: string) => {
    const ref = `params.${name}`;
    const scripts = [...draft.requestScripts, ...draft.responseScripts]
      .filter(use => Object.values(use.params).some(binding => binding.kind === 'PATH' ? binding.path === ref : binding.kind === 'EXPRESSION' && binding.expression.includes(ref)))
      .map(use => `脚本 ${use.id}`);
    const rules = draft.statusRules.some(rule => rule.condition.includes(ref)) ? ['状态规则'] : [];
    const outputs = [...draft.outputs.SUCCESS, ...draft.outputs.FAIL].some(output => JSON.stringify(output.value).includes(ref)) ? ['输出'] : [];
    return [...scripts, ...rules, ...outputs];
  };
  const fieldKeys = requestFields.map(fieldKey);
  return <>
    <Panel className="xapi-plain" title="调用参数" extra={!disabled && <Button onClick={() => setParam({ index: null, value: { name: '', dataType: 'STRING', required: true } })}>添加参数</Button>}>
      <p className="xapi-desc">报文字段以外、由流程节点每次提供的值。PROTECTED_HANDLE 只能交给注册函数，不能写进报文字段、输出或日志。</p>
      <div className="xapi-table-wrap"><Table rowKey={(_, index) => String(index)} pagination={false} dataSource={draft.params}
        locale={{ emptyText: '暂无调用参数' }} columns={[
          { title: '名称', width: 180, render: (_, row) => <Mono>{row.name}</Mono> },
          { title: '类型', width: 200, render: (_, row) => row.dataType === 'PROTECTED_HANDLE' ? <HandleMark /> : <Mono>{row.dataType}</Mono> },
          { title: '必填', width: 70, render: (_, row) => row.required ? '是' : '否' },
          { title: '说明', ellipsis: true, render: (_, row) => row.description || '—' },
          { title: '使用位置', width: 200, render: (_, row) => usage(row.name).join('、') || '—' },
          ...(disabled ? [] : [{ title: '操作', width: 110, render: (_: unknown, row: ParamDeclaration, index: number) =>
            <RowOps onEdit={() => setParam({ index, value: row })} onRemove={() => update({ params: draft.params.filter((_, i) => i !== index) })} /> }]),
        ]} /></div>
    </Panel>
    <Panel className="xapi-plain" title="协议固定值" extra={!disabled && <Button onClick={() => setFixed({ index: null, value: { key: '', expression: '' } })}>添加固定值</Button>}>
      <p className="xapi-desc">按渠道协议对请求字段写死的常量；只能是常量表达式，不能引用 input、resources、steps 或 params。设置后该字段在节点中只读。</p>
      <div className="xapi-table-wrap"><Table rowKey={(_, index) => String(index)} pagination={false} dataSource={draft.fixedValues}
        locale={{ emptyText: '暂无固定值' }} columns={[
          { title: '请求字段', width: 320, render: (_, row) => <Mono>{row.key.replace(':', ' ')}</Mono> },
          { title: '常量表达式', render: (_, row) => <span className="xapi-code-block">{row.expression}</span> },
          ...(disabled ? [] : [{ title: '操作', width: 110, render: (_: unknown, row: FixedValue, index: number) =>
            <RowOps onEdit={() => setFixed({ index, value: row })} onRemove={() => update({ fixedValues: draft.fixedValues.filter((_, i) => i !== index) })} /> }]),
        ]} /></div>
    </Panel>
    {param && <EditModal title={param.index === null ? '添加调用参数' : '编辑调用参数'} initial={param.value} onCancel={() => setParam(null)}
      onSave={value => { const next = { ...value, name: value.name.trim(), description: value.description?.trim() || null };
        update({ params: param.index === null ? [...draft.params, next] : draft.params.map((item, i) => i === param.index ? next : item) }); setParam(null); }}>
      <Form.Item name="name" label="名称" rules={[{ required: true, pattern: /^[a-z][A-Za-z0-9]{0,63}$/, message: '小写字母开头，只含字母和数字' }]}>
        <Input className="xapi-mono-input" maxLength={64} /></Form.Item>
      <Form.Item name="dataType" label="类型"><Select className="xapi-mono-input" options={paramTypes.map(typeOption)} /></Form.Item>
      <div className="xapi-inline-switch" style={{ marginBottom: 24 }}><Form.Item name="required" valuePropName="checked" noStyle><Switch size="small" /></Form.Item><span>必填</span></div>
      <Form.Item name="description" label="说明"><Input maxLength={512} /></Form.Item>
    </EditModal>}
    {fixed && <EditModal title={fixed.index === null ? '添加固定值' : '编辑固定值'} initial={fixed.value} onCancel={() => setFixed(null)}
      onSave={value => { update({ fixedValues: fixed.index === null ? [...draft.fixedValues, value] : draft.fixedValues.map((item, i) => i === fixed.index ? value : item) }); setFixed(null); }}>
      <Form.Item name="key" label="请求字段" rules={[{ required: true, message: '请选择请求字段' }]}>
        <Select className="xapi-mono-input" options={fieldKeys.map(key => ({ value: key, label: key.replace(':', ' ') }))} /></Form.Item>
      <Form.Item name="expression" label="常量表达式" rules={[{ required: true, whitespace: true, message: '请填写常量表达式' }]}
        extra="字符串写成 'application/json'，数字直接写 100">
        <Input className="xapi-mono-input" maxLength={4096} /></Form.Item>
    </EditModal>}
  </>;
}

function ScriptsTab({ draft, update, scripts, paramPaths, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; scripts: IntegrationScriptSummary[];
  paramPaths: string[]; disabled: boolean;
}) {
  const [editing, setEditing] = useState<{ stage: 'REQUEST' | 'RESPONSE'; index: number | null; value: ScriptUse } | null>(null);
  const handleParams = new Set(draft.params.filter(param => param.dataType === 'PROTECTED_HANDLE').map(param => `params.${param.name}`));
  const group = (stage: 'REQUEST' | 'RESPONSE') => {
    const key = stage === 'REQUEST' ? 'requestScripts' : 'responseScripts';
    const uses = draft[key]; const available = scripts.filter(script => script.executionStage === stage);
    const set = (next: ScriptUse[]) => update({ [key]: next } as Partial<DefinitionDraft>);
    return <div key={stage} className="xapi-group">
      <div className="xapi-group-head"><div><span className="xapi-kind">{stage}</span>
        <span className="xapi-desc">{stage === 'REQUEST' ? '请求组装后、编码发送前' : '解码响应后、状态判定前'}</span></div>
        {!disabled && <span className="xapi-ops">{!available.length && <span className="xapi-field-hint">本渠道还没有 {stage} 阶段的公共脚本</span>}
          <Button disabled={!available.length} onClick={() => setEditing({ stage, index: null,
            value: { id: nextScriptUseId([...draft.requestScripts, ...draft.responseScripts]), scriptCode: available[0]?.scriptCode ?? '', stage, params: {} } })}>
            引用脚本</Button></span>}</div>
      {uses.length === 0 && <div className="xapi-field-hint">暂无{stage === 'REQUEST' ? '请求' : '响应'}脚本</div>}
      {uses.map((use, index) => {
        const script = scripts.find(item => item.scriptCode === use.scriptCode);
        const names = script?.parameterNames ?? Object.keys(use.params);
        return <div key={`${use.id}:${index}`} className="xapi-card-row"><span className="xapi-seq">{index + 1}</span>
          <div className="xapi-card-main"><Mono>{use.id}</Mono>
            <span className="xapi-field-hint">{use.scriptCode} · {script?.scriptName ?? '脚本不存在'}</span>
            {names.length ? names.map(name => {
              const binding = use.params[name];
              const handle = binding?.kind === 'PATH' && handleParams.has(binding.path);
              return <div key={name} className="xapi-binding-line">{name}<span className="xapi-arrow">←</span>
                {handle && <span className="xapi-handle">🔒 </span>}<span className="xapi-mono-ref">{binding ? (binding.kind === 'PATH' ? binding.path
                  : binding.kind === 'EXPRESSION' ? binding.expression : binding.function) : '未赋值'}</span></div>;
            }) : <span className="xapi-field-hint">无参数</span>}
          </div>
          {!disabled && <span className="xapi-ops">
            <button type="button" className="xapi-link" disabled={index === 0} onClick={() => set(moveItem(uses, index, -1))}>↑</button>
            <button type="button" className="xapi-link" disabled={index === uses.length - 1} onClick={() => set(moveItem(uses, index, 1))}>↓</button>
            <button type="button" className="xapi-link" onClick={() => setEditing({ stage, index, value: use })}>编辑</button>
            <button type="button" className="xapi-link" onClick={() => set(uses.filter((_, i) => i !== index))}>移除</button></span>}
        </div>;
      })}
    </div>;
  };
  return <>
    <p className="xapi-desc">按顺序执行；脚本参数只能取 params.* 或常量，受控句柄只能交给注册函数，脚本读不到明文。</p>
    {group('REQUEST')}{group('RESPONSE')}
    <Note items={['响应验签读取原始响应字节（http.responseRaw），不受响应脚本对工作副本的修改影响。', '被引用脚本修改阶段或参数时，保存会校验并列出本 API。']} />
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
  const [value, setValue] = useState(editing.value);
  const names = scripts.find(script => script.scriptCode === value.scriptCode)?.parameterNames ?? [];
  return <Modal open width={620} title={editing.index === null ? `引用 ${editing.stage} 脚本` : '编辑脚本引用'} okText="应用"
    okButtonProps={{ disabled: !value.id.trim() || !value.scriptCode }} onCancel={onCancel}
    onOk={() => onSave({ ...value, id: value.id.trim() })}>
    <div className="xapi-form-stack">
      <Field label="引用 ID"><Input value={value.id} maxLength={64} className="xapi-mono-input" onChange={event => setValue({ ...value, id: event.target.value })} /></Field>
      <Field label="公共脚本"><Select value={value.scriptCode || undefined} className="xapi-mono-input"
        options={scripts.map(script => ({ value: script.scriptCode, label: `${script.scriptCode} · ${script.scriptName}` }))}
        onChange={scriptCode => setValue({ ...value, scriptCode, params: {} })} /></Field>
      {names.map(name => <Field key={name} label={<Mono>{name}</Mono>}>
        <BindingInput value={value.params[name]} kinds={['PATH', 'EXPRESSION']} paths={paramPaths}
          onChange={binding => setValue({ ...value, params: { ...value.params, [name]: binding } })} /></Field>)}
      {!names.length && <span className="xapi-field-hint">该脚本没有参数</span>}
    </div>
  </Modal>;
}

function RulesTab({ rules, onChange, disabled }: { rules: StatusRule[]; onChange: (rules: StatusRule[]) => void; disabled: boolean }) {
  const [editing, setEditing] = useState<{ index: number | null; value: StatusRule } | null>(null);
  type Row = { rule: StatusRule | null; index: number };
  const rows: Row[] = [...rules.map((rule, index) => ({ rule, index })), { rule: null, index: rules.length }];
  return <>
    <div className="xapi-toolbar"><span className="xapi-desc">按顺序判定，第一条为真即决定结果；全部为假固定为 ERROR。不存在“HTTP 200 即成功”的默认规则。</span>
      {!disabled && <Button onClick={() => setEditing({ index: null, value: { condition: '', executeStatus: 'SUCCESS' } })}>添加规则</Button>}</div>
    <div className="xapi-table-wrap"><Table<Row> rowKey="index" pagination={false} dataSource={rows}
      rowClassName={row => row.rule ? '' : 'xapi-row-fixed'} columns={[
        { title: '顺序', width: 70, render: (_, row) => row.rule ? row.index + 1 : '—' },
        { title: '条件（Aviator 布尔表达式）', render: (_, row) => row.rule ? <span className="xapi-code-block">{row.rule.condition || ' '}</span>
          : '其他情况（固定，不可编辑）' },
        { title: '执行状态', width: 120, render: (_, row) => <StatusTag status={row.rule ? row.rule.executeStatus : 'ERROR'} /> },
        ...(disabled ? [] : [{ title: '操作', width: 190, render: (_: unknown, row: Row) => row.rule && <RowOps
          extra={<><button type="button" className="xapi-link" disabled={row.index === 0} onClick={() => onChange(moveItem(rules, row.index, -1))}>上移</button>
            <button type="button" className="xapi-link" disabled={row.index === rules.length - 1} onClick={() => onChange(moveItem(rules, row.index, 1))}>下移</button></>}
          onEdit={() => setEditing({ index: row.index, value: row.rule! })} onRemove={() => onChange(rules.filter((_, i) => i !== row.index))} /> }]),
      ]} /></div>
    <Note items={[<>条件可读取：<Mono>http.responseStatus</Mono>、<Mono>http.responseHeaders</Mono>、<Mono>http.responseBody</Mono>、<Mono>params.*</Mono>（受控句柄除外）。</>,
      '表达式异常或结果不是布尔值时为 ERROR；判定为 SUCCESS 后再校验响应必填字段，必填输出缺失时结果改为 ERROR。']} />
    {editing && <EditModal width={640} title={editing.index === null ? '添加状态规则' : '编辑状态规则'} initial={editing.value} onCancel={() => setEditing(null)}
      onSave={value => { onChange(editing.index === null ? [...rules, value] : rules.map((item, i) => i === editing.index ? value : item)); setEditing(null); }}>
      <Form.Item name="condition" label="条件（Aviator 布尔表达式）" rules={[{ required: true, whitespace: true, message: '请填写条件' }]}>
        <Input.TextArea autoSize={{ minRows: 3 }} maxLength={4096} className="xapi-mono-input"
          placeholder="http.responseStatus == 201 && http.responseBody.state == 'CAPTURED'" /></Form.Item>
      <Form.Item name="executeStatus" label="执行状态"><Radio.Group options={['SUCCESS', 'FAIL'].map(typeOption)} optionType="button" /></Form.Item>
    </EditModal>}
  </>;
}

function OutputsTab({ draft, update, readPaths, functions, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; readPaths: string[];
  functions: Capabilities['functions']; disabled: boolean;
}) {
  const [editing, setEditing] = useState<{ status: 'SUCCESS' | 'FAIL'; index: number | null; value: OutputDeclaration } | null>(null);
  const branch = (status: 'SUCCESS' | 'FAIL') => {
    const rows = draft.outputs[status];
    const set = (next: OutputDeclaration[]) => update({ outputs: { ...draft.outputs, [status]: next } });
    return <div key={status} className="xapi-group">
      <div className="xapi-group-head"><div><StatusTag status={status} /><span className="xapi-desc">{rows.length} 个输出</span></div>
        {!disabled && <Button onClick={() => setEditing({ status, index: null, value: { name: '', dataType: 'STRING', required: false, value: emptyBinding('PATH') } })}>添加输出</Button>}</div>
      <div className="xapi-table-wrap"><Table rowKey={(_, index) => String(index)} pagination={false} dataSource={rows} locale={{ emptyText: '无输出' }} columns={[
        { title: '名称', width: 180, render: (_, row) => <Mono>{row.name}</Mono> },
        { title: '类型', width: 110, render: (_, row) => <Mono>{row.dataType}</Mono> },
        { title: '必填', width: 70, render: (_, row) => row.required ? '是' : '否' },
        { title: '取值', render: (_, row) => <BindingView binding={row.value} /> },
        ...(disabled ? [] : [{ title: '操作', width: 110, render: (_: unknown, row: OutputDeclaration, index: number) =>
          <RowOps onEdit={() => setEditing({ status, index, value: row })} onRemove={() => set(rows.filter((_, i) => i !== index))} /> }]),
      ]} /></div>
    </div>;
  };
  return <>
    <p className="xapi-desc">状态判定后按分支映射输出，写入 steps.&lt;节点&gt;.outputs 供流程使用；必填输出缺失或类型不符时整个结果改为 ERROR。</p>
    {branch('SUCCESS')}{branch('FAIL')}
    <div className="xapi-group"><div className="xapi-group-head"><div><StatusTag status="ERROR" /><span className="xapi-desc">固定为空对象 {'{}'}，不可配置。</span></div></div></div>
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
  const [value, setValue] = useState(editing.value);
  const nameValid = /^[a-z][A-Za-z0-9]{0,63}$/.test(value.name.trim());
  return <Modal open width={620} title={`${editing.index === null ? '添加' : '编辑'} ${editing.status} 输出`} okText="应用"
    okButtonProps={{ disabled: !nameValid }} onCancel={onCancel} onOk={() => onSave({ ...value, name: value.name.trim() })}>
    <div className="xapi-form-stack">
      <div className="xapi-form-row">
        <Field label="名称" hint={value.name && !nameValid ? '小写字母开头，只含字母和数字' : undefined}>
          <Input value={value.name} maxLength={64} className="xapi-mono-input" onChange={event => setValue({ ...value, name: event.target.value })} /></Field>
        <Field label="类型"><Select value={value.dataType} className="xapi-mono-input" options={fieldDataTypes.map(typeOption)}
          onChange={(dataType: FieldDataType) => setValue({ ...value, dataType })} /></Field>
      </div>
      <div className="xapi-inline-switch"><Switch size="small" checked={value.required} onChange={required => setValue({ ...value, required })} /><span>必填</span></div>
      <Field label="取值" hint="可读 http.* 与不含受控句柄的 params.*；按数组取值时用表达式明确选择元素，如 #http.responseBody.errors[0].code。">
        <BindingInput value={value.value} kinds={['PATH', 'EXPRESSION', 'FUNCTION']} paths={readPaths} functions={functions}
          onChange={(binding: Binding) => setValue({ ...value, value: binding })} /></Field>
    </div>
  </Modal>;
}

export type { ParamType };
