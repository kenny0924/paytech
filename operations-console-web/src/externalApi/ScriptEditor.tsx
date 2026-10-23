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
import { codePattern, decodeMutation, externalApiBase, integrationPath, manageProtocol, replaceQuery } from './externalApiModel';
import { workspaceCrumbs, workspaceRoutes } from '../channel/channelWorkspace';
import type { ScriptStage } from './externalApiModel';
import { moveItem, paramTypes } from './integrationApiModel';
import { Field, HandleMark, Mono, PageHeader, Panel, ReadOnlyValue } from './ui';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';

/** integrationCode 为渠道编码（管理接口路径沿用 integrations/{code}），channelId 用于页面路由。 */
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; channelId: string; integrationCode: string; scriptCode: string | null }
const capabilityPath = `${externalApiBase}/editor-capabilities`;
const maxSourceBytes = 32 * 1024;
const stages: ScriptStage[] = ['REQUEST', 'RESPONSE'];

/** 公共脚本编辑与详情（/channel/channels/:channelId/protocol/scripts/:scriptCode，?mode=edit；新建为 /scripts/new）。 */
export function ScriptEditor({ resources, onAuthorizationChanged, channelId, integrationCode, scriptCode }: Props) {
  const t = useT('channel');
  const location = useLocation();
  const creating = scriptCode === null; const canManage = hasButton(resources, manageProtocol);
  const readOnly = !canManage || !creating && new URLSearchParams(location.search).get('mode') !== 'edit';
  const decode = useMemo(() => (value: unknown) => decodeScript(value, scriptCode!), [scriptCode]);
  const script = useDetail<ScriptDetail>(creating ? null : `${integrationPath}/${integrationCode}/scripts/${scriptCode}`, decode);
  const capabilities = useDetail<Capabilities>(capabilityPath, decodeCapabilities);
  if (creating && !canManage) return <Alert type="warning" showIcon title={t('editor.noProtocolPermission')} />;
  if (script.loading || capabilities.loading) return <Loading />;
  if (!creating && !script.data) return <LoadFailure title={t('protocol.scriptsLoadFailed')} failure={script.failure} retry={script.refresh} />;
  return <ScriptForm key={`${script.data?.version}:${readOnly}`} channelId={channelId} integrationCode={integrationCode} detail={script.data} readOnly={readOnly}
    canManage={canManage} capabilities={capabilities.data} onAuthorizationChanged={onAuthorizationChanged} onReload={script.refresh} />;
}

function ScriptForm({ channelId, integrationCode, detail, readOnly, canManage, capabilities, onAuthorizationChanged, onReload }: {
  channelId: string; integrationCode: string; detail?: ScriptDetail; readOnly: boolean; canManage: boolean; capabilities?: Capabilities;
  onAuthorizationChanged: () => void; onReload: () => void;
}) {
  const t = useT('channel'), tCommon = useT('common');
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
      if (!result.issues.length) void message.success(t('editor.validationPassed'));
    } catch (error) { notify(error, t('editor.validationFailed')); } finally { setValidating(false); }
  }
  const reasonFlow = useReasonFlow(async reason => {
    const code = creating ? scriptCode.trim() : detail.scriptCode;
    const result = await editor.save(creating ? base : `${base}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { scriptCode: code, ...body(), reason } : { ...body(), expectedVersion: detail.version, reason },
      value => decodeMutation(value, 'INTEGRATION_SCRIPT', `${integrationCode}/${code}`));
    if (!result) { if (!editor.conflict) void validate(); return false; }
    flushSync(() => editor.setDirty(false));
    if (!leaving.current) void navigate(workspaceRoutes(channelId).script(code), { replace: true });
    return true;
  }, () => !!scriptName.trim() && !!sourceCode.trim() && (!creating || codePattern.test(scriptCode.trim())));
  const { setSaveForLeave } = editor;
  useEffect(() => setSaveForLeave(readOnly ? null : () => { leaving.current = true; return reasonFlow.start().finally(() => { leaving.current = false; }); }));
  const latest = useCallback(() => session.request(`${base}/${detail?.scriptCode}`, {}, value => decodeScript(value, detail!.scriptCode)), [base, detail]);
  const functions = (capabilities?.functions ?? []).filter(item => item.kind === 'SCRIPT');
  const code = detail?.scriptCode;
  const routes = workspaceRoutes(channelId);
  const crumbs = [...workspaceCrumbs(channelId, integrationCode, 'scripts'), ...(creating ? [{ label: t('editor.crumbNew') }]
      : readOnly ? [{ label: code! }] : [{ label: code!, to: routes.script(code!) }, { label: t('protocol.edit') }])];
  const title = creating ? t('script.createTitle') : readOnly ? t('script.viewTitle', { code }) : t('script.editTitle', { code });
  const text = (editable: ReactNode, shown: string, mono?: boolean) => disabled ? <ReadOnlyValue value={shown} mono={mono} /> : editable;
  return <>
    <PageHeader crumbs={crumbs} title={title} readOnlyHint={!canManage ? t('editor.noManage') : undefined}
      actions={readOnly ? canManage && detail && <Button type="primary" onClick={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))}>{t('protocol.edit')}</Button>
        : <><Button loading={validating} onClick={() => void validate()}>{t('editor.validate')}</Button>
          <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>{tCommon('action.save')}</Button></>} />
    {editor.conflict && detail && <ConflictBanner code={detail.scriptCode} baseVersion={detail.version} latest={latest} onReload={onReload}
      onCopy={() => copy({ scriptCode, ...body() })} />}
    <div className="xapi-script-top">
      <Field label={t('editor.codeReadOnlyAfterCreate')}>{creating && !disabled ? <Input value={scriptCode} maxLength={64} className="xapi-mono-input" placeholder={t('script.codePlaceholder')}
        onChange={event => { setScriptCode(event.target.value); touch(); }} /> : <ReadOnlyValue mono value={scriptCode} />}</Field>
      <Field label={t('form.name')}>{text(<Input value={scriptName} maxLength={128} onChange={event => { setScriptName(event.target.value); touch(); }} />, scriptName)}</Field>
      <Field label={t('script.stage')} hint={detail?.referencedApiCodes.length && !disabled ? t('script.stageHint') : undefined}>
        <Segmented value={stage} disabled={disabled} options={stages} onChange={value => { setStage(value as ScriptStage); touch(); }} /></Field>
      <Field label={t('mids.note')}>{text(<Input value={description} maxLength={512} onChange={event => { setDescription(event.target.value); touch(); }} />, description)}</Field>
    </div>
    <div className="xapi-script-cols">
      <Panel title={t('credential.parameters')}>
        {parameters.length === 0 && <span className="xapi-field-hint">{t('script.noParameters')}</span>}
        {parameters.map((param, index) => <div key={`${param.name}:${index}`} className="xapi-param-card">
          <div className="xapi-param-head"><Mono>{param.name || t('script.unnamed')}</Mono>
            {!disabled && <span className="xapi-ops">
              <button type="button" className="xapi-link" disabled={index === 0} onClick={() => { setParameters(moveItem(parameters, index, -1)); touch(); }}>↑</button>
              <button type="button" className="xapi-link" onClick={() => setEditingParam({ index, value: param })}>{t('protocol.edit')}</button>
              <button type="button" className="xapi-link" onClick={() => { setParameters(parameters.filter((_, i) => i !== index)); touch(); }}>{t('endpointEditor.delete')}</button></span>}</div>
          <span className="xapi-field-hint">{param.dataType === 'PROTECTED_HANDLE' ? <HandleMark /> : <Mono tone="muted">{param.dataType}</Mono>}
            {' · '}{param.required ? t('form.required') : t('script.optional')}</span>
        </div>)}
        {!disabled && <div><button type="button" className="xapi-link xapi-link-add" disabled={parameters.length >= 32}
          onClick={() => setEditingParam({ index: null, value: { name: '', dataType: 'STRING', required: true } })}>{t('script.addParameter')}</button></div>}
        <p className="xapi-field-hint" style={{ margin: 0 }}>{t('script.parametersHint')}</p>
      </Panel>
      <Panel title={t('script.source')} extra={<span className={sourceBytes > maxSourceBytes ? 'xapi-inline-hint' : 'xapi-desc'}>
        {sourceBytes} B / 32 KiB</span>}>
        <Input.TextArea value={sourceCode} readOnly={disabled} className="xapi-code-editor" spellCheck={false} autoSize={{ minRows: 16 }}
          onChange={event => { setSourceCode(event.target.value); touch(); }} />
        <p className="xapi-field-hint" style={{ margin: 0 }}>{t('script.sourceHint')}</p>
      </Panel>
      <Panel title={t('script.availableIn', { stage })}>
        <div className="xapi-avail">{scriptObjects(stage).map(item => <div key={item.path} className="xapi-avail-row">
          <span><Mono>{item.path}</Mono><div className="xapi-field-hint">{item.note}</div></span>
          <span className={item.access === 'READ_WRITE' ? 'xapi-access-rw' : 'xapi-access-ro'}>{enumLabel(t, 'enum.access', item.access)}</span></div>)}</div>
        <h3 className="xapi-subtitle">{t('script.registeredFunctions')}</h3>
        <div className="xapi-avail">{functions.length ? functions.map(item => <div key={item.functionCode}>
          <Mono tone="ref">{item.functionCode}({item.params.map(param => param.name).join(', ')})</Mono>
          <div className="xapi-field-hint">→ {item.returns}</div></div>) : <span className="xapi-field-hint">{t('script.noFunctions')}</span>}</div>
      </Panel>
    </div>
    {detail && <div className="xapi-strip">{t('script.referencedBy')}{detail.referencedApiCodes.length ? detail.referencedApiCodes.map((apiCode, index) => <span key={apiCode}>
      {index > 0 && t('list.separator')}<Link to={routes.api(apiCode)}>{apiCode}</Link></span>) : t('script.noReferences')}
      {detail.referencedApiCodes.length > 0 && t('script.referencedNote')}</div>}
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
  const t = useT('channel');
  const [form] = Form.useForm<ScriptParameter>();
  return <Modal open title={editing.index === null ? t('script.addParameter') : t('script.editParameter')} okText={t('field.apply')} onCancel={onCancel} onOk={() => form.submit()} destroyOnHidden>
    <Form form={form} layout="vertical" requiredMark={false} initialValues={editing.value}
      onFinish={values => onSave({ ...values, name: values.name.trim() })}>
      <Form.Item name="name" label={t('form.name')} rules={[{ required: true, pattern: /^[a-z][A-Za-z0-9]{0,63}$/, message: t('script.paramNamePattern') }]}>
        <Input className="xapi-mono-input" maxLength={64} /></Form.Item>
      <Form.Item name="dataType" label={t('capability.type')}><Select className="xapi-mono-input" options={paramTypes.map(type => ({ value: type, label: type }))} /></Form.Item>
      <div className="xapi-inline-switch"><Form.Item name="required" valuePropName="checked" noStyle><Switch size="small" /></Form.Item><span>{t('form.required')}</span></div>
    </Form>
  </Modal>;
}
