import { useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Checkbox, Form, Input, Radio, Select, Space, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ReasonDialog, ValidationPanel } from './EditorParts';
import { decodeCapabilities, decodeScript, decodeValidation, scriptObjects } from './editorModel';
import type { Capabilities, ScriptDetail, ScriptParameter, ValidationIssue } from './editorModel';
import { Loading, LoadFailure, SaveNotice, hasButton, useDetail } from './externalApiHooks';
import {
  codePattern, dataTypeLabels, decodeMutation, externalApiBase, integrationPath, integrationRoute, manageIntegrations, replaceQuery, stageLabels,
} from './externalApiModel';
import type { FieldDataType, ScriptStage } from './externalApiModel';
import { moveItem, paramTypes } from './integrationApiModel';
import type { ParamType } from './integrationApiModel';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; integrationCode: string; scriptCode: string | null }
const capabilityPath = `${externalApiBase}/editor-capabilities`;
const maxSourceBytes = 32 * 1024;

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
  if (!creating && !script.data) return <LoadFailure failure={script.failure} retry={script.refresh} />;
  return <ScriptForm key={`${script.data?.version}:${readOnly}`} integrationCode={integrationCode} detail={script.data} readOnly={readOnly}
    canManage={canManage} capabilities={capabilities.data} onAuthorizationChanged={onAuthorizationChanged} onReload={script.refresh} />;
}

function ScriptForm({ integrationCode, detail, readOnly, canManage, capabilities, onAuthorizationChanged, onReload }: {
  integrationCode: string; detail?: ScriptDetail; readOnly: boolean; canManage: boolean; capabilities?: Capabilities;
  onAuthorizationChanged: () => void; onReload: () => void;
}) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification();
  const editor = useConfigurationForm(onAuthorizationChanged);
  const creating = !detail; const base = `${integrationPath}/${integrationCode}/scripts`;
  const [scriptCode, setScriptCode] = useState(detail?.scriptCode ?? '');
  const [scriptName, setScriptName] = useState(detail?.scriptName ?? '');
  const [stage, setStage] = useState<ScriptStage>(detail?.executionStage ?? 'REQUEST');
  const [description, setDescription] = useState(detail?.description ?? '');
  const [parameters, setParameters] = useState<ScriptParameter[]>(detail?.parameterDefinition ?? []);
  const [sourceCode, setSourceCode] = useState(detail?.sourceCode ?? '');
  const [issues, setIssues] = useState<ValidationIssue[] | null>(null);
  const [stale, setStale] = useState(false);
  const [validating, setValidating] = useState(false);
  const [saving, setSaving] = useState(false);
  const disabled = readOnly || editor.saving || editor.conflict;
  const sourceBytes = new TextEncoder().encode(sourceCode).length;
  function touch() { editor.setDirty(true); setStale(issues !== null); }
  function setParameter(index: number, patch: Partial<ScriptParameter>) {
    setParameters(current => current.map((item, i) => i === index ? { ...item, ...patch } : item)); touch();
  }
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
    } catch (error) { notify(error, '校验未完成'); } finally { setValidating(false); }
  }
  async function save(reason: string) {
    const code = creating ? scriptCode.trim() : detail.scriptCode;
    const result = await editor.save(creating ? base : `${base}/${code}`, creating ? 'POST' : 'PUT',
      creating ? { scriptCode: code, ...body(), reason } : { ...body(), expectedVersion: detail.version, reason },
      value => decodeMutation(value, 'INTEGRATION_SCRIPT', `${integrationCode}/${code}`));
    setSaving(false);
    if (result) {
      flushSync(() => editor.setDirty(false));
      void navigate(`${integrationRoute}/${integrationCode}/scripts/${code}`, { replace: true });
    } else {
      void validate();
    }
  }
  const functions = (capabilities?.functions ?? []).filter(item => item.kind !== 'INITIALIZER');
  const title = creating ? '新建公共脚本' : `接入方 ${integrationCode} / 公共脚本 / ${detail.scriptCode}`;
  return <div className="configuration-page external-api-page">
    <div className="page-heading"><h1>{title}</h1><Space>
      {readOnly && canManage && detail && <Button onClick={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))}>编辑</Button>}
      {!readOnly && <><Button loading={validating} onClick={() => void validate()}>校验</Button>
        <Button type="primary" disabled={editor.conflict || !scriptName.trim() || !sourceCode.trim() || creating && !codePattern.test(scriptCode.trim())}
          onClick={() => setSaving(true)}>保存</Button></>}</Space></div>
    {!readOnly && <div className="external-api-save-row"><SaveNotice /></div>}
    {editor.conflict && <Alert type="warning" showIcon title="该配置已被他人修改，本地修改已保留。" action={<Button onClick={onReload}>重新加载</Button>} />}
    <section className="configuration-editor"><Form layout="vertical" disabled={disabled}>
      <div className="configuration-grid two">
        <Form.Item label="编码" extra={creating ? '创建后不可修改' : undefined}>
          <Input value={scriptCode} maxLength={64} disabled={!creating || disabled} placeholder="例如 NI_SIGN"
            onChange={event => { setScriptCode(event.target.value); touch(); }} /></Form.Item>
        <Form.Item label="名称"><Input value={scriptName} maxLength={128} onChange={event => { setScriptName(event.target.value); touch(); }} /></Form.Item>
      </div>
      <div className="configuration-grid two">
        <Form.Item label="执行阶段" extra={detail?.referencedApiCodes.length ? '修改阶段或参数会校验全部引用的原子 API，破坏引用时不能保存' : undefined}>
          <Radio.Group value={stage} onChange={event => { setStage(event.target.value); touch(); }}
            options={Object.entries(stageLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item label="说明"><Input value={description} maxLength={512} onChange={event => { setDescription(event.target.value); touch(); }} /></Form.Item>
      </div>
    </Form></section>
    <div className="external-api-script-layout">
      <section className="configuration-editor"><h2>参数</h2>
        <Table rowKey={(_, index) => String(index)} size="small" pagination={false} dataSource={parameters} locale={{ emptyText: '无参数' }} columns={[
          { title: '名称', render: (_, row, index) => <Input value={row.name} disabled={disabled} maxLength={64}
            onChange={event => setParameter(index, { name: event.target.value })} /> },
          { title: '类型', width: 150, render: (_, row, index) => <Select value={row.dataType} disabled={disabled} style={{ width: '100%' }}
            options={paramTypes.map(type => ({ value: type, label: type === 'PROTECTED_HANDLE' ? '🔒 受控句柄' : dataTypeLabels[type as FieldDataType] }))}
            onChange={(dataType: ParamType) => setParameter(index, { dataType })} /> },
          { title: '必填', width: 60, render: (_, row, index) => <Checkbox checked={row.required} disabled={disabled}
            onChange={event => setParameter(index, { required: event.target.checked })} /> },
          ...(disabled ? [] : [{ title: '', width: 110, render: (_: unknown, __: ScriptParameter, index: number) => <Space size={0}>
            <Button type="link" size="small" disabled={index === 0} onClick={() => { setParameters(moveItem(parameters, index, -1)); touch(); }}>上移</Button>
            <Button type="link" size="small" danger onClick={() => { setParameters(parameters.filter((_, i) => i !== index)); touch(); }}>删除</Button></Space> }]),
        ]} />
        {!disabled && <Button className="external-api-add" disabled={parameters.length >= 32}
          onClick={() => { setParameters([...parameters, { name: '', dataType: 'STRING', required: true }]); touch(); }}>添加参数</Button>}
      </section>
      <section className="configuration-editor"><h2>源码（Aviator）</h2>
        <Input.TextArea value={sourceCode} disabled={disabled} rows={16} className="external-api-code-input external-api-source"
          spellCheck={false} onChange={event => { setSourceCode(event.target.value); touch(); }} />
        <div className={sourceBytes > maxSourceBytes ? 'external-api-field-hint' : 'muted'}>{sourceBytes} / {maxSourceBytes} 字节</div>
      </section>
      <section className="configuration-editor"><h2>可用对象与函数</h2>
        <Table rowKey="path" size="small" pagination={false} dataSource={scriptObjects(stage)} columns={[
          { title: '对象', render: (_, row) => <code>{row.path}</code> }, { title: '权限', dataIndex: 'access', width: 60 },
          { title: '说明', dataIndex: 'note' }]} />
        <h3>注册函数</h3>
        {functions.length ? functions.map(item => <div key={item.functionCode} className="external-api-function">
          <code>{item.functionCode}({item.params.map(param => param.name).join(', ')})</code> → {item.returns}</div>)
          : <span className="muted">暂无可用函数</span>}
      </section>
    </div>
    {detail && <section className="configuration-editor"><h2>引用此脚本的原子 API</h2>
      {detail.referencedApiCodes.length ? <Space wrap>{detail.referencedApiCodes.map(code => <Tag key={code}>{code}</Tag>)}</Space>
        : <span className="muted">暂无引用</span>}</section>}
    <ValidationPanel issues={issues} stale={stale} onLocate={() => undefined} />
    <ReasonDialog open={saving} saving={editor.saving} onCancel={() => setSaving(false)} onConfirm={reason => void save(reason)} />
  </div>;
}
