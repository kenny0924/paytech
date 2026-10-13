import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Checkbox, Form, Input, InputNumber, Select, Space, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { BindingInput, ReasonDialog, ValidationPanel, emptyBinding } from './EditorParts';
import { FieldTable } from './FieldTable';
import { decodeCapabilities, decodeValidation } from './editorModel';
import type { Capabilities, ValidationIssue } from './editorModel';
import { Loading, LoadFailure, SaveNotice, hasButton, loadAllPages, useDetail } from './externalApiHooks';
import {
  codePattern, dataTypeLabels, decodeIntegration, decodeMutation, decodePage, decodeScriptSummary, errorRows, externalApiBase,
  fieldDataTypes, integrationPath, integrationRoute, manageIntegrations, replaceQuery, toFieldBodies,
} from './externalApiModel';
import type { ApiField, FieldDataType, FieldLocation, IntegrationDetail, IntegrationScriptSummary } from './externalApiModel';
import {
  decodeIntegrationApi, emptyDraft, fieldKey, fixedKeySet, issueTab, methods, moveItem, nextScriptUseId, paramTypes,
  pathVariableHints, requestEncodings, responseDecodings, responseReadPaths, toDefinition, toDraft,
} from './integrationApiModel';
import type {
  DefinitionDraft, EditorTab, IntegrationApiDetail, OutputDeclaration, ParamDeclaration, ParamType, ScriptUse, StatusRule,
} from './integrationApiModel';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; integrationCode: string; apiCode: string | null }
const allLocations: FieldLocation[] = ['HEADER', 'BODY', 'QUERY', 'PATH'];
const responseLocations: FieldLocation[] = ['HEADER', 'BODY'];
const capabilityPath = `${externalApiBase}/editor-capabilities`;
const scriptPage = (value: unknown) => decodePage(value, decodeScriptSummary, item => item.scriptCode);

/** 原子 API 编辑与详情（/integrations/:code/apis/:apiCode，?mode=edit；新建为 /apis/new）。 */
export function IntegrationApiEditor({ resources, onAuthorizationChanged, integrationCode, apiCode }: Props) {
  const location = useLocation();
  const creating = apiCode === null; const canManage = hasButton(resources, manageIntegrations);
  const readOnly = !canManage || !creating && new URLSearchParams(location.search).get('mode') !== 'edit';
  const decodeIntegrationDetail = useMemo(() => (value: unknown) => decodeIntegration(value, integrationCode), [integrationCode]);
  const decodeApi = useMemo(() => (value: unknown) => decodeIntegrationApi(value, apiCode!), [apiCode]);
  const integration = useDetail<IntegrationDetail>(`${integrationPath}/${integrationCode}`, decodeIntegrationDetail);
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
  if (creating && !canManage) return <Alert type="warning" showIcon title="当前没有接入方维护权限" />;
  if (integration.loading || api.loading || capabilities.loading || scripts === undefined) return <Loading />;
  if (!integration.data) return <LoadFailure failure={integration.failure} retry={integration.refresh} />;
  if (!creating && !api.data) return <LoadFailure failure={api.failure} retry={api.refresh} />;
  return <ApiForm key={`${api.data?.version}:${readOnly}`} integration={integration.data} detail={api.data} readOnly={readOnly}
    canManage={canManage} capabilities={capabilities.data} scripts={scripts} onAuthorizationChanged={onAuthorizationChanged}
    onReload={api.refresh} />;
}

interface FormProps {
  integration: IntegrationDetail; detail?: IntegrationApiDetail; readOnly: boolean; canManage: boolean;
  capabilities?: Capabilities; scripts: IntegrationScriptSummary[]; onAuthorizationChanged: () => void; onReload: () => void;
}

function ApiForm({ integration, detail, readOnly, canManage, capabilities, scripts, onAuthorizationChanged, onReload }: FormProps) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification();
  const editor = useConfigurationForm(onAuthorizationChanged);
  const creating = !detail; const base = `${integrationPath}/${integration.integrationCode}/apis`;
  const [apiCode, setApiCode] = useState(detail?.apiCode ?? '');
  const [apiName, setApiName] = useState(detail?.apiName ?? '');
  const [description, setDescription] = useState(detail?.description ?? '');
  const [requestFields, setRequestFields] = useState<ApiField[]>(detail?.requestFields ?? []);
  const [responseFields, setResponseFields] = useState<ApiField[]>(detail?.responseFields ?? []);
  const [draft, setDraft] = useState<DefinitionDraft>(() => detail ? toDraft(detail.definition)
    : emptyDraft(integration.endpoints[0]?.endpointCode ?? ''));
  const [tab, setTab] = useState<EditorTab>('interface');
  const [issues, setIssues] = useState<ValidationIssue[] | null>(null);
  const [stale, setStale] = useState(false);
  const [validating, setValidating] = useState(false);
  const [saving, setSaving] = useState(false);
  const disabled = readOnly || editor.saving || editor.conflict;
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
      const first = result.issues.find(issue => issue.severity === 'ERROR');
      if (first) setTab(issueTab(first.location));
    } catch (error) { notify(error, '校验未完成'); } finally { setValidating(false); }
  }
  async function save(reason: string) {
    const code = creating ? apiCode.trim() : detail.apiCode;
    const objectCode = `${integration.integrationCode}/${code}`;
    const result = await editor.save(creating ? base : `${base}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { apiCode: code, ...body(), reason } : { ...body(), expectedVersion: detail.version, reason },
      value => decodeMutation(value, 'INTEGRATION_API', objectCode));
    setSaving(false);
    if (result) {
      flushSync(() => editor.setDirty(false));
      void navigate(`${integrationRoute}/${integration.integrationCode}/apis/${code}`, { replace: true });
    } else if (!editor.conflict) {
      void validate();
    }
  }
  const fixedKeys = fixedKeySet(draft.fixedValues);
  const pathHints = pathVariableHints(draft.transport.pathTemplate, requestFields);
  const readPaths = responseReadPaths(responseFields, draft.params);
  const scriptParamPaths = draft.params.filter(param => param.name.trim()).map(param => `params.${param.name.trim()}`);
  const title = creating ? '新建原子 API' : `接入方 ${integration.integrationCode} / 原子 API / ${detail.apiCode}`;
  const tabs: { key: EditorTab; label: string; children: ReactNode }[] = [
    { key: 'interface', label: '接口', children: <InterfaceTab {...{ creating, disabled, apiCode, setApiCode, apiName, setApiName, description,
      setDescription, draft, update, integration, pathHints, touch, detail }} /> },
    { key: 'request', label: `请求字段（${requestFields.length}）`, children: <FieldTable value={requestFields} locations={allLocations}
      readOnly={disabled} fixedKeys={fixedKeys} serverErrorRows={errorRows(editor.fieldErrors, 'requestFields')}
      onChange={fields => { setRequestFields(fields); touch(); }} /> },
    { key: 'response', label: `响应字段（${responseFields.length}）`, children: <FieldTable value={responseFields} locations={responseLocations}
      readOnly={disabled} serverErrorRows={errorRows(editor.fieldErrors, 'responseFields')}
      onChange={fields => { setResponseFields(fields); touch(); }} /> },
    { key: 'params', label: '调用参数与固定值', children: <ParamsTab draft={draft} update={update} requestFields={requestFields} disabled={disabled} /> },
    { key: 'scripts', label: `脚本（${draft.requestScripts.length + draft.responseScripts.length}）`, children:
      <ScriptsTab draft={draft} update={update} scripts={scripts} paramPaths={scriptParamPaths} disabled={disabled} /> },
    { key: 'rules', label: `状态规则（${draft.statusRules.length}）`, children:
      <RulesTab rules={draft.statusRules} onChange={statusRules => update({ statusRules })} readPaths={readPaths} disabled={disabled} /> },
    { key: 'outputs', label: '输出', children: <OutputsTab draft={draft} update={update} readPaths={readPaths}
      functions={capabilities?.functions ?? []} disabled={disabled} /> },
  ];
  return <div className="configuration-page external-api-page">
    <div className="page-heading"><h1>{title}</h1><Space>
      {readOnly && canManage && detail && <Button onClick={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))}>编辑</Button>}
      {!readOnly && <><Button loading={validating} onClick={() => void validate()}>校验</Button>
        <Button type="primary" disabled={editor.conflict || !apiName.trim() || creating && !codePattern.test(apiCode.trim())}
          onClick={() => setSaving(true)}>保存</Button></>}</Space></div>
    {!readOnly && <div className="external-api-save-row"><SaveNotice /></div>}
    {editor.conflict && <Alert type="warning" showIcon title="该配置已被他人修改，本地修改已保留。" action={<Button onClick={onReload}>重新加载</Button>} />}
    <section className="configuration-editor"><Tabs activeKey={tab} onChange={key => setTab(key as EditorTab)} items={tabs} /></section>
    <ValidationPanel issues={issues} stale={stale} onLocate={issue => setTab(issueTab(issue.location))} />
    {detail && <section className="configuration-editor"><h2>被以下标准 API 实现引用</h2>
      {detail.referencedBy.length ? <Space wrap>{detail.referencedBy.map(item => <Tag key={item.standardApiCode}>
        {item.standardApiCode}（节点 {item.nodeIds.join('、')}）</Tag>)}</Space> : <span className="muted">暂无引用</span>}</section>}
    <ReasonDialog open={saving} saving={editor.saving} onCancel={() => setSaving(false)} onConfirm={reason => void save(reason)} />
  </div>;
}

function InterfaceTab({ creating, disabled, apiCode, setApiCode, apiName, setApiName, description, setDescription, draft, update, integration,
  pathHints, touch, detail }: {
  creating: boolean; disabled: boolean; apiCode: string; setApiCode: (v: string) => void; apiName: string; setApiName: (v: string) => void;
  description: string; setDescription: (v: string) => void; draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void;
  integration: IntegrationDetail; pathHints: string[]; touch: () => void; detail?: IntegrationApiDetail;
}) {
  const transport = (patch: Partial<DefinitionDraft['transport']>) => update({ transport: { ...draft.transport, ...patch } });
  return <Form layout="vertical" disabled={disabled}>
    <div className="configuration-grid two">
      <Form.Item label="编码" extra={creating ? '创建后不可修改' : undefined}
        validateStatus={creating && apiCode && !codePattern.test(apiCode) ? 'error' : undefined}>
        <Input value={apiCode} maxLength={64} disabled={!creating || disabled} placeholder="例如 CREATE_ORDER"
          onChange={event => { setApiCode(event.target.value); touch(); }} /></Form.Item>
      <Form.Item label="名称"><Input value={apiName} maxLength={128} onChange={event => { setApiName(event.target.value); touch(); }} /></Form.Item>
    </div>
    <Form.Item label="说明"><Input.TextArea rows={2} maxLength={512} value={description}
      onChange={event => { setDescription(event.target.value); touch(); }} /></Form.Item>
    <h3>HTTP 传输</h3>
    <div className="configuration-grid two">
      <Form.Item label="Method"><Select value={draft.transport.method} options={methods.map(value => ({ value, label: value }))}
        onChange={method => transport({ method })} /></Form.Item>
      <Form.Item label="地址代码" extra="来自接入方的出站地址"><Select value={draft.transport.endpointCode || undefined}
        options={integration.endpoints.map(item => ({ value: item.endpointCode, label: `${item.endpointCode} · ${item.baseUrl}` }))}
        onChange={endpointCode => transport({ endpointCode })} /></Form.Item>
    </div>
    <Form.Item label="路径模板" extra="以 / 开头的相对路径；{变量} 必须与 PATH 字段一一对应"
      validateStatus={pathHints.length ? 'warning' : undefined} help={pathHints.length ? pathHints.join('；') : undefined}>
      <Input value={draft.transport.pathTemplate} maxLength={512} className="external-api-code-input"
        onChange={event => transport({ pathTemplate: event.target.value })} /></Form.Item>
    <div className="configuration-grid two">
      <Form.Item label="连接超时（ms）" extra="100～10000"><InputNumber min={100} max={10000} value={draft.transport.connectTimeoutMs}
        onChange={value => transport({ connectTimeoutMs: value ?? 2000 })} /></Form.Item>
      <Form.Item label="响应超时（ms）" extra="100～30000"><InputNumber min={100} max={30000} value={draft.transport.responseTimeoutMs}
        onChange={value => transport({ responseTimeoutMs: value ?? 10000 })} /></Form.Item>
      <Form.Item label="请求编码" extra="NONE 表示无请求体，此时不能有 BODY 字段"><Select value={draft.requestEncoding}
        options={requestEncodings.map(value => ({ value, label: value }))} onChange={requestEncoding => update({ requestEncoding })} /></Form.Item>
      <Form.Item label="响应解码" extra="NONE 不解析，交给响应脚本处理"><Select value={draft.responseDecoding}
        options={responseDecodings.map(value => ({ value, label: value }))} onChange={responseDecoding => update({ responseDecoding })} /></Form.Item>
    </div>
    {detail && <div className="muted">当前版本 {detail.version} · 更新于 {new Date(detail.updateTime).toLocaleString('zh-CN')}</div>}
  </Form>;
}

function ParamsTab({ draft, update, requestFields, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; requestFields: ApiField[]; disabled: boolean;
}) {
  const setParam = (index: number, patch: Partial<ParamDeclaration>) =>
    update({ params: draft.params.map((param, i) => i === index ? { ...param, ...patch } : param) });
  const setFixed = (index: number, patch: Partial<{ key: string; expression: string }>) =>
    update({ fixedValues: draft.fixedValues.map((item, i) => i === index ? { ...item, ...patch } : item) });
  const fieldKeys = requestFields.map(fieldKey);
  return <div>
    <h3>调用参数</h3>
    <p className="muted">由流程节点每次提供。受控句柄（PROTECTED_HANDLE）只能交给脚本或注册函数，不能写进报文或输出。</p>
    <Table rowKey={(_, index) => String(index)} size="small" pagination={false} dataSource={draft.params} columns={[
      { title: '名称', render: (_, row, index) => <Input value={row.name} disabled={disabled} maxLength={64} placeholder="小写字母开头"
        onChange={event => setParam(index, { name: event.target.value })} /> },
      { title: '类型', width: 200, render: (_, row, index) => <Select value={row.dataType} disabled={disabled} style={{ width: '100%' }}
        options={paramTypes.map(type => ({ value: type, label: type === 'PROTECTED_HANDLE' ? '🔒 受控句柄' : dataTypeLabels[type as FieldDataType] }))}
        onChange={(dataType: ParamType) => setParam(index, { dataType })} /> },
      { title: '必填', width: 70, render: (_, row, index) => <Checkbox checked={row.required} disabled={disabled}
        onChange={event => setParam(index, { required: event.target.checked })} /> },
      { title: '说明', render: (_, row, index) => <Input value={row.description ?? ''} disabled={disabled} maxLength={512}
        onChange={event => setParam(index, { description: event.target.value })} /> },
      ...(disabled ? [] : [{ title: '', width: 70, render: (_: unknown, __: ParamDeclaration, index: number) => <Button type="link" danger
        onClick={() => update({ params: draft.params.filter((_, i) => i !== index) })}>删除</Button> }]),
    ]} />
    {!disabled && <Button className="external-api-add" onClick={() => update({ params: [...draft.params, { name: '', dataType: 'STRING', required: true }] })}>添加参数</Button>}
    <h3>协议固定值</h3>
    <p className="muted">只能是常量表达式；已设固定值的请求字段不能再由流程节点赋值。</p>
    <Table rowKey={(_, index) => String(index)} size="small" pagination={false} dataSource={draft.fixedValues} columns={[
      { title: '请求字段', width: 320, render: (_, row, index) => <Select value={row.key || undefined} disabled={disabled} style={{ width: '100%' }}
        placeholder="选择请求字段" options={fieldKeys.map(key => ({ value: key, label: key }))} onChange={key => setFixed(index, { key })} /> },
      { title: '常量表达式', render: (_, row, index) => <Input value={row.expression} disabled={disabled} className="external-api-code-input"
        placeholder="'application/json'" onChange={event => setFixed(index, { expression: event.target.value })} /> },
      ...(disabled ? [] : [{ title: '', width: 70, render: (_: unknown, __: unknown, index: number) => <Button type="link" danger
        onClick={() => update({ fixedValues: draft.fixedValues.filter((_, i) => i !== index) })}>删除</Button> }]),
    ]} />
    {!disabled && <Button className="external-api-add" onClick={() => update({ fixedValues: [...draft.fixedValues, { key: '', expression: '' }] })}>添加固定值</Button>}
  </div>;
}

function ScriptsTab({ draft, update, scripts, paramPaths, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; scripts: IntegrationScriptSummary[];
  paramPaths: string[]; disabled: boolean;
}) {
  const group = (stage: 'REQUEST' | 'RESPONSE') => {
    const key = stage === 'REQUEST' ? 'requestScripts' : 'responseScripts';
    const uses = draft[key]; const available = scripts.filter(script => script.executionStage === stage);
    const set = (next: ScriptUse[]) => update({ [key]: next } as Partial<DefinitionDraft>);
    return <div key={stage}>
      <h3>{stage === 'REQUEST' ? '请求阶段（编码前，按顺序执行）' : '响应阶段（解码后，按顺序执行）'}</h3>
      <Table rowKey={(_, index) => String(index)} size="small" pagination={false} dataSource={uses} locale={{ emptyText: '无' }} columns={[
        { title: 'ID', width: 150, render: (_, row, index) => <Input value={row.id} disabled={disabled} maxLength={64}
          onChange={event => set(uses.map((use, i) => i === index ? { ...use, id: event.target.value } : use))} /> },
        { title: '脚本', width: 220, render: (_, row, index) => <Select value={row.scriptCode || undefined} disabled={disabled} style={{ width: '100%' }}
          placeholder="选择本阶段脚本" options={available.map(script => ({ value: script.scriptCode, label: `${script.scriptCode} · ${script.scriptName}` }))}
          onChange={scriptCode => set(uses.map((use, i) => i === index ? { ...use, scriptCode, params: {} } : use))} /> },
        { title: '参数取值（params.* 或常量）', render: (_, row, index) => {
          const names = scripts.find(script => script.scriptCode === row.scriptCode)?.parameterNames ?? [];
          return names.length ? names.map(name => <div key={name} className="external-api-binding-arg"><span className="muted">{name}</span>
            <BindingInput value={row.params[name]} kinds={['PATH', 'EXPRESSION']} paths={paramPaths} disabled={disabled}
              onChange={binding => set(uses.map((use, i) => i === index ? { ...use, params: { ...use.params, [name]: binding } } : use))} /></div>)
            : <span className="muted">无参数</span>;
        } },
        ...(disabled ? [] : [{ title: '', width: 150, render: (_: unknown, __: ScriptUse, index: number) => <Space size={0}>
          <Button type="link" size="small" disabled={index === 0} onClick={() => set(moveItem(uses, index, -1))}>上移</Button>
          <Button type="link" size="small" disabled={index === uses.length - 1} onClick={() => set(moveItem(uses, index, 1))}>下移</Button>
          <Button type="link" size="small" danger onClick={() => set(uses.filter((_, i) => i !== index))}>删除</Button></Space> }]),
      ]} />
      {!disabled && <Button className="external-api-add" disabled={!available.length}
        onClick={() => set([...uses, { id: nextScriptUseId([...draft.requestScripts, ...draft.responseScripts]), scriptCode: '', stage, params: {} }])}>
        添加{stage === 'REQUEST' ? '请求' : '响应'}脚本</Button>}
    </div>;
  };
  return <div>{group('REQUEST')}{group('RESPONSE')}</div>;
}

function RulesTab({ rules, onChange, readPaths, disabled }: {
  rules: StatusRule[]; onChange: (rules: StatusRule[]) => void; readPaths: string[]; disabled: boolean;
}) {
  return <div>
    <p className="muted">按顺序判定，第一条为真即决定结果；全部为假固定为 ERROR。可读：{readPaths.slice(0, 6).join('、')}{readPaths.length > 6 ? ' 等' : ''}</p>
    <Table rowKey={(_, index) => String(index)} size="small" pagination={false} dataSource={[...rules, null]} columns={[
      { title: '#', width: 50, render: (_, __, index) => index + 1 },
      { title: '条件', render: (_, row, index) => row ? <Input.TextArea value={row.condition} disabled={disabled} autoSize maxLength={4096}
        className="external-api-code-input" placeholder="http.responseStatus == 201 && http.responseBody.state == 'CAPTURED'"
        onChange={event => onChange(rules.map((rule, i) => i === index ? { ...rule, condition: event.target.value } : rule))} />
        : <span className="muted">其他情况（固定）</span> },
      { title: '结果', width: 140, render: (_, row, index) => row ? <Select value={row.executeStatus} disabled={disabled} style={{ width: '100%' }}
        options={[{ value: 'SUCCESS', label: 'SUCCESS' }, { value: 'FAIL', label: 'FAIL' }]}
        onChange={(executeStatus: StatusRule['executeStatus']) => onChange(rules.map((rule, i) => i === index ? { ...rule, executeStatus } : rule))} />
        : <Tag>ERROR</Tag> },
      ...(disabled ? [] : [{ title: '', width: 150, render: (_: unknown, row: StatusRule | null, index: number) => row && <Space size={0}>
        <Button type="link" size="small" disabled={index === 0} onClick={() => onChange(moveItem(rules, index, -1))}>上移</Button>
        <Button type="link" size="small" disabled={index === rules.length - 1} onClick={() => onChange(moveItem(rules, index, 1))}>下移</Button>
        <Button type="link" size="small" danger onClick={() => onChange(rules.filter((_, i) => i !== index))}>删除</Button></Space> }]),
    ]} />
    {!disabled && <Button className="external-api-add" onClick={() => onChange([...rules, { condition: '', executeStatus: 'SUCCESS' }])}>添加规则</Button>}
  </div>;
}

function OutputsTab({ draft, update, readPaths, functions, disabled }: {
  draft: DefinitionDraft; update: (patch: Partial<DefinitionDraft>) => void; readPaths: string[];
  functions: Capabilities['functions']; disabled: boolean;
}) {
  const branch = (status: 'SUCCESS' | 'FAIL') => {
    const rows = draft.outputs[status];
    const set = (next: OutputDeclaration[]) => update({ outputs: { ...draft.outputs, [status]: next } });
    const patch = (index: number, value: Partial<OutputDeclaration>) => set(rows.map((row, i) => i === index ? { ...row, ...value } : row));
    return <div key={status}>
      <h3>{status} 分支</h3>
      <Table rowKey={(_, index) => String(index)} size="small" pagination={false} dataSource={rows} locale={{ emptyText: '无输出' }} columns={[
        { title: '名称', width: 170, render: (_, row, index) => <Input value={row.name} disabled={disabled} maxLength={64}
          onChange={event => patch(index, { name: event.target.value })} /> },
        { title: '类型', width: 110, render: (_, row, index) => <Select value={row.dataType} disabled={disabled} style={{ width: '100%' }}
          options={fieldDataTypes.map(type => ({ value: type, label: dataTypeLabels[type] }))} onChange={(dataType: FieldDataType) => patch(index, { dataType })} /> },
        { title: '必填', width: 60, render: (_, row, index) => <Checkbox checked={row.required} disabled={disabled}
          onChange={event => patch(index, { required: event.target.checked })} /> },
        { title: '取值', render: (_, row, index) => <BindingInput value={row.value} kinds={['PATH', 'EXPRESSION', 'FUNCTION']} paths={readPaths}
          functions={functions} disabled={disabled} onChange={value => patch(index, { value })} /> },
        ...(disabled ? [] : [{ title: '', width: 70, render: (_: unknown, __: OutputDeclaration, index: number) => <Button type="link" danger
          onClick={() => set(rows.filter((_, i) => i !== index))}>删除</Button> }]),
      ]} />
      {!disabled && <Button className="external-api-add" onClick={() => set([...rows, { name: '', dataType: 'STRING', required: false, value: emptyBinding('PATH') }])}>
        添加输出</Button>}
    </div>;
  };
  return <div>{branch('SUCCESS')}{branch('FAIL')}<h3>ERROR 分支</h3><p className="muted">固定输出空对象。必填输出缺失或类型不符时整个结果改为 ERROR。</p></div>;
}
