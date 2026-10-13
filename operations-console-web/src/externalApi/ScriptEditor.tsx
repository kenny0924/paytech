import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { flushSync } from 'react-dom';
import { Alert, App, Button, Form, Input, Modal, Segmented, Select, Switch } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ReasonDialog, ValidationPanel, useReasonFlow } from './EditorParts';
import { decodeCapabilities, decodeScript, decodeValidation, scriptObjects } from './editorModel';
import type { Capabilities, ScriptDetail, ScriptParameter, ValidationIssue } from './editorModel';
import { ConflictBanner, Loading, LoadFailure, hasButton, useCopyDraft, useDetail, useEditorSave } from './externalApiHooks';
import { codePattern, decodeMutation, externalApiBase, integrationPath, integrationRoute, manageIntegrations, replaceQuery } from './externalApiModel';
import type { ScriptStage } from './externalApiModel';
import { moveItem, paramTypes } from './integrationApiModel';
import { Field, HandleMark, Mono, PageHeader, Panel, ReadOnlyValue } from './ui';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; integrationCode: string; scriptCode: string | null }
const capabilityPath = `${externalApiBase}/editor-capabilities`;
const maxSourceBytes = 32 * 1024;
const noManage = '只读 · 无“外部 API 管理”权限';
const stages: ScriptStage[] = ['REQUEST', 'RESPONSE'];

/** 公共脚本编辑与详情（/integrations/:code/scripts/:scriptCode，?mode=edit；新建为 /scripts/new）。 */
export function ScriptEditor({ resources, onAuthorizationChanged, integrationCode, scriptCode }: Props) {
  const location = useLocation();
  const creating = scriptCode === null; const canManage = hasButton(resources, manageIntegrations);
  const readOnly = !canManage || !creating && new URLSearchParams(location.search).get('mode') !== 'edit';
  const decode = useMemo(() => (value: unknown) => decodeScript(value, scriptCode!), [scriptCode]);
  const script = useDetail<ScriptDetail>(creating ? null : `${integrationPath}/${integrationCode}/scripts/${scriptCode}`, decode);
  const capabilities = useDetail<Capabilities>(capabilityPath, decodeCapabilities);
  if (creating && !canManage) return <Alert type="warning" showIcon title="当前没有接入方维护权限" />;
  if (script.loading || capabilities.loading) return <Loading />;
  if (!creating && !script.data) return <LoadFailure title="公共脚本加载失败" failure={script.failure} retry={script.refresh} />;
  return <ScriptForm key={`${script.data?.version}:${readOnly}`} integrationCode={integrationCode} detail={script.data} readOnly={readOnly}
    canManage={canManage} capabilities={capabilities.data} onAuthorizationChanged={onAuthorizationChanged} onReload={script.refresh} />;
}

function ScriptForm({ integrationCode, detail, readOnly, canManage, capabilities, onAuthorizationChanged, onReload }: {
  integrationCode: string; detail?: ScriptDetail; readOnly: boolean; canManage: boolean; capabilities?: Capabilities;
  onAuthorizationChanged: () => void; onReload: () => void;
}) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification(); const { message } = App.useApp(); const copy = useCopyDraft();
  const editor = useEditorSave(onAuthorizationChanged);
  const creating = !detail; const base = `${integrationPath}/${integrationCode}/scripts`;
  const [scriptCode, setScriptCode] = useState(detail?.scriptCode ?? '');
  const [scriptName, setScriptName] = useState(detail?.scriptName ?? '');
  const [stage, setStage] = useState<ScriptStage>(detail?.executionStage ?? 'REQUEST');
  const [description, setDescription] = useState(detail?.description ?? '');
  const [parameters, setParameters] = useState<ScriptParameter[]>(detail?.parameterDefinition ?? []);
  const [sourceCode, setSourceCode] = useState(detail?.sourceCode ?? '');
  const [editingParam, setEditingParam] = useState<{ index: number | null; value: ScriptParameter } | null>(null);
  const [issues, setIssues] = useState<ValidationIssue[] | null>(null);
  const [stale, setStale] = useState(false);
  const [validating, setValidating] = useState(false);
  const leaving = useRef(false);
  const disabled = readOnly || editor.saving;
  const sourceBytes = new TextEncoder().encode(sourceCode).length;
  function touch() { editor.setDirty(true); setStale(issues !== null); }
  function body() {
    return { scriptName: scriptName.trim(), executionStage: stage,
      parameterDefinition: parameters.map(item => ({ ...item, name: item.name.trim() })), sourceCode, description: description.trim() || null };
  }
  async function validate() {
    setValidating(true);
    try {
      const result = await session.request(`${base}/validate`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scriptCode: creating ? scriptCode.trim() : detail.scriptCode, ...body(), expectedVersion: detail?.version ?? null }) },
        decodeValidation);
      setIssues(result.issues); setStale(false);
      if (!result.issues.length) void message.success('校验通过，配置可以保存');
    } catch (error) { notify(error, '校验未完成'); } finally { setValidating(false); }
  }
  const reasonFlow = useReasonFlow(async reason => {
    const code = creating ? scriptCode.trim() : detail.scriptCode;
    const result = await editor.save(creating ? base : `${base}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { scriptCode: code, ...body(), reason } : { ...body(), expectedVersion: detail.version, reason },
      value => decodeMutation(value, 'INTEGRATION_SCRIPT', `${integrationCode}/${code}`));
    if (!result) { if (!editor.conflict) void validate(); return false; }
    flushSync(() => editor.setDirty(false));
    if (!leaving.current) void navigate(`${integrationRoute}/${integrationCode}/scripts/${code}`, { replace: true });
    return true;
  }, () => !!scriptName.trim() && !!sourceCode.trim() && (!creating || codePattern.test(scriptCode.trim())));
  const { setSaveForLeave } = editor;
  useEffect(() => setSaveForLeave(readOnly ? null : () => { leaving.current = true; return reasonFlow.start().finally(() => { leaving.current = false; }); }));
  const latest = useCallback(() => session.request(`${base}/${detail?.scriptCode}`, {}, value => decodeScript(value, detail!.scriptCode)), [base, detail]);
  const functions = (capabilities?.functions ?? []).filter(item => item.kind === 'SCRIPT');
  const code = detail?.scriptCode;
  const workspace = `${integrationRoute}/${integrationCode}`;
  const crumbs = [{ label: '外部 API' }, { label: '接入方', to: integrationRoute }, { label: integrationCode, to: workspace },
    { label: '公共脚本', to: `${workspace}?tab=scripts` }, ...(creating ? [{ label: '新建' }]
      : readOnly ? [{ label: code! }] : [{ label: code!, to: `${workspace}/scripts/${code}` }, { label: '编辑' }])];
  const title = creating ? '新建公共脚本' : readOnly ? `公共脚本 · ${code}` : `编辑公共脚本 · ${code}`;
  const text = (editable: ReactNode, shown: string, mono?: boolean) => disabled ? <ReadOnlyValue value={shown} mono={mono} /> : editable;
  return <>
    <PageHeader crumbs={crumbs} title={title} readOnlyHint={!canManage ? noManage : undefined}
      actions={readOnly ? canManage && detail && <Button type="primary" onClick={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))}>编辑</Button>
        : <><Button loading={validating} onClick={() => void validate()}>校验</Button>
          <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>保存</Button></>} />
    {editor.conflict && detail && <ConflictBanner code={detail.scriptCode} baseVersion={detail.version} latest={latest} onReload={onReload}
      onCopy={() => copy({ scriptCode, ...body() })} />}
    <div className="xapi-script-top">
      <Field label="编码（创建后只读）">{creating && !disabled ? <Input value={scriptCode} maxLength={64} className="xapi-mono-input" placeholder="例如 NI_SIGN"
        onChange={event => { setScriptCode(event.target.value); touch(); }} /> : <ReadOnlyValue mono value={scriptCode} />}</Field>
      <Field label="名称">{text(<Input value={scriptName} maxLength={128} onChange={event => { setScriptName(event.target.value); touch(); }} />, scriptName)}</Field>
      <Field label="执行阶段" hint={detail?.referencedApiCodes.length && !disabled ? '修改阶段会校验全部引用的原子 API' : undefined}>
        <Segmented value={stage} disabled={disabled} options={stages} onChange={value => { setStage(value as ScriptStage); touch(); }} /></Field>
      <Field label="说明">{text(<Input value={description} maxLength={512} onChange={event => { setDescription(event.target.value); touch(); }} />, description)}</Field>
    </div>
    <div className="xapi-script-cols">
      <Panel title="参数">
        {parameters.length === 0 && <span className="xapi-field-hint">无参数</span>}
        {parameters.map((param, index) => <div key={`${param.name}:${index}`} className="xapi-param-card">
          <div className="xapi-param-head"><Mono>{param.name || '（未命名）'}</Mono>
            {!disabled && <span className="xapi-ops">
              <button type="button" className="xapi-link" disabled={index === 0} onClick={() => { setParameters(moveItem(parameters, index, -1)); touch(); }}>↑</button>
              <button type="button" className="xapi-link" onClick={() => setEditingParam({ index, value: param })}>编辑</button>
              <button type="button" className="xapi-link" onClick={() => { setParameters(parameters.filter((_, i) => i !== index)); touch(); }}>删除</button></span>}</div>
          <span className="xapi-field-hint">{param.dataType === 'PROTECTED_HANDLE' ? <HandleMark /> : <Mono tone="muted">{param.dataType}</Mono>}
            {' · '}{param.required ? '必填' : '可选'}</span>
        </div>)}
        {!disabled && <div><button type="button" className="xapi-link xapi-link-add" disabled={parameters.length >= 32}
          onClick={() => setEditingParam({ index: null, value: { name: '', dataType: 'STRING', required: true } })}>添加参数</button></div>}
        <p className="xapi-field-hint" style={{ margin: 0 }}>原子 API 引用脚本时为参数赋值，只能用 params.* 或常量；PROTECTED_HANDLE 只能交给注册函数，脚本读不到明文。</p>
      </Panel>
      <Panel title="源码（Aviator）" extra={<span className={sourceBytes > maxSourceBytes ? 'xapi-inline-hint' : 'xapi-desc'}>
        {sourceBytes} B / 32 KiB</span>}>
        <Input.TextArea value={sourceCode} readOnly={disabled} className="xapi-code-editor" spellCheck={false} autoSize={{ minRows: 16 }}
          onChange={event => { setSourceCode(event.target.value); touch(); }} />
        <p className="xapi-field-hint" style={{ margin: 0 }}>认证函数（bearerAuth、basicAuth）返回受控句柄，赋给请求 Header 后由传输层发送时取出明文；摘要类函数返回普通字符串。</p>
      </Panel>
      <Panel title={`${stage} 阶段可用`}>
        <div className="xapi-avail">{scriptObjects(stage).map(item => <div key={item.path} className="xapi-avail-row">
          <span><Mono>{item.path}</Mono><div className="xapi-field-hint">{item.note}</div></span>
          <span className={item.access === '读写' ? 'xapi-access-rw' : 'xapi-access-ro'}>{item.access}</span></div>)}</div>
        <h3 className="xapi-subtitle">注册函数</h3>
        <div className="xapi-avail">{functions.length ? functions.map(item => <div key={item.functionCode}>
          <Mono tone="ref">{item.functionCode}({item.params.map(param => param.name).join(', ')})</Mono>
          <div className="xapi-field-hint">→ {item.returns}</div></div>) : <span className="xapi-field-hint">暂无脚本函数</span>}</div>
      </Panel>
    </div>
    {detail && <div className="xapi-strip">被引用：{detail.referencedApiCodes.length ? detail.referencedApiCodes.map((apiCode, index) => <span key={apiCode}>
      {index > 0 && '、'}<Link to={`${workspace}/apis/${apiCode}`}>{apiCode}</Link></span>) : '暂无原子 API 引用'}
      {detail.referencedApiCodes.length > 0 && '。修改阶段或参数时，保存会校验这些原子 API。'}</div>}
    <ValidationPanel issues={issues} stale={stale} onLocate={() => undefined} />
    {editingParam && <ParamModal editing={editingParam} onCancel={() => setEditingParam(null)} onSave={value => {
      setParameters(editingParam.index === null ? [...parameters, value] : parameters.map((item, i) => i === editingParam.index ? value : item));
      setEditingParam(null); touch();
    }} />}
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}

function ParamModal({ editing, onCancel, onSave }: {
  editing: { index: number | null; value: ScriptParameter }; onCancel: () => void; onSave: (value: ScriptParameter) => void;
}) {
  const [form] = Form.useForm<ScriptParameter>();
  return <Modal open title={editing.index === null ? '添加参数' : '编辑参数'} okText="应用" onCancel={onCancel} onOk={() => form.submit()} destroyOnHidden>
    <Form form={form} layout="vertical" requiredMark={false} initialValues={editing.value}
      onFinish={values => onSave({ ...values, name: values.name.trim() })}>
      <Form.Item name="name" label="名称" rules={[{ required: true, pattern: /^[a-z][A-Za-z0-9]{0,63}$/, message: '小写字母开头，只含字母和数字' }]}>
        <Input className="xapi-mono-input" maxLength={64} /></Form.Item>
      <Form.Item name="dataType" label="类型"><Select className="xapi-mono-input" options={paramTypes.map(type => ({ value: type, label: type }))} /></Form.Item>
      <div className="xapi-inline-switch"><Form.Item name="required" valuePropName="checked" noStyle><Switch size="small" /></Form.Item><span>必填</span></div>
    </Form>
  </Modal>;
}
