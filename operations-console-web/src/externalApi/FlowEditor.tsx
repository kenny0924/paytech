import { useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { ReactFlowProvider } from '@xyflow/react';
import { Alert, App, Button, Dropdown, InputNumber, Select, Space } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ReasonDialog, ValidationPanel } from './EditorParts';
import { FlowCanvas, startId } from './FlowCanvas';
import type { FlowSelection } from './FlowCanvas';
import { InitializerList, InitializerPanel, NodePanel, ReturnPanel, StartPanel } from './FlowPanels';
import { decodeCapabilities, decodeValidation } from './editorModel';
import type { Capabilities, ValidationIssue } from './editorModel';
import { Loading, LoadFailure, SaveNotice, hasButton, useDetail } from './externalApiHooks';
import { decodeMutation, externalApiBase, integrationPath, integrationRoute, manageIntegrations, replaceQuery } from './externalApiModel';
import {
  addEdge, decodeFlowContext, decodeImplementation, flowTarget, idPrefix, newFlow, nextId, toFlow, toFlowJson,
} from './flowModel';
import type { ExecuteStatus, Flow, FlowEditorContext, ImplementationDetail } from './flowModel';
import '../risk/decisionFlow.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; integrationCode: string; standardApiCode: string }
const capabilityPath = `${externalApiBase}/editor-capabilities`;

/** 渠道标准 API 实现的流程编辑（/integrations/:code/implementations/:standardApiCode，?mode=create|edit）。 */
export function FlowEditor({ resources, onAuthorizationChanged, integrationCode, standardApiCode }: Props) {
  const location = useLocation(); const mode = new URLSearchParams(location.search).get('mode');
  const canManage = hasButton(resources, manageIntegrations); const creating = mode === 'create';
  const readOnly = !canManage || mode !== 'edit' && !creating;
  const base = `${integrationPath}/${integrationCode}`;
  const decodeDetail = useMemo(() => (value: unknown) => decodeImplementation(value, standardApiCode), [standardApiCode]);
  const context = useDetail<FlowEditorContext>(`${base}/flow-editor-context?standardApiCode=${encodeURIComponent(standardApiCode)}`, decodeFlowContext);
  const capabilities = useDetail<Capabilities>(capabilityPath, decodeCapabilities);
  const detail = useDetail<ImplementationDetail>(creating ? null : `${base}/implementations/${standardApiCode}`, decodeDetail);
  if (creating && !canManage) return <Alert type="warning" showIcon title="当前没有接入方维护权限" />;
  if (context.loading || capabilities.loading || detail.loading) return <Loading />;
  if (!context.data) return <LoadFailure failure={context.failure} retry={context.refresh} />;
  if (!creating && !detail.data) return <LoadFailure failure={detail.failure} retry={detail.refresh} />;
  return <ReactFlowProvider><FlowWorkspace key={`${detail.data?.version}:${readOnly}`} integrationCode={integrationCode}
    standardApiCode={standardApiCode} context={context.data} functions={capabilities.data?.functions ?? []} detail={detail.data}
    readOnly={readOnly} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} onReload={detail.refresh} /></ReactFlowProvider>;
}

function FlowWorkspace({ integrationCode, standardApiCode, context, functions, detail, readOnly, canManage, onAuthorizationChanged, onReload }: {
  integrationCode: string; standardApiCode: string; context: FlowEditorContext; functions: Capabilities['functions'];
  detail?: ImplementationDetail; readOnly: boolean; canManage: boolean; onAuthorizationChanged: () => void; onReload: () => void;
}) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification(); const { message } = App.useApp();
  const editor = useConfigurationForm(onAuthorizationChanged);
  const creating = !detail; const base = `${integrationPath}/${integrationCode}/implementations`;
  const [flow, setFlow] = useState<Flow>(() => detail ? toFlow(detail.flowDefinition) : newFlow());
  const [selection, setSelection] = useState<FlowSelection>(null);
  const [focusId, setFocusId] = useState<string>();
  const [issues, setIssues] = useState<ValidationIssue[] | null>(null);
  const [stale, setStale] = useState(false);
  const [validating, setValidating] = useState(false);
  const [saving, setSaving] = useState(false);
  const disabled = readOnly || editor.saving || editor.conflict;
  function change(next: Flow) { setFlow(next); editor.setDirty(true); setStale(issues !== null); }
  function select(next: FlowSelection) { setSelection(next); setFocusId(undefined); }
  function connect(source: string, target: string) {
    if (source === startId) {
      if (!flow.nodes.some(node => node.id === target)) { void message.warning('入口必须是原子 API 节点'); return; }
      change({ ...flow, entry: target }); return;
    }
    const result = addEdge(flow, source, target);
    if ('error' in result) { void message.warning(result.error); return; }
    change(result.flow); select({ kind: 'node', id: source });
  }
  function addNode(apiCode: string) {
    const id = nextId(flow, idPrefix(apiCode));
    change({ ...flow, entry: flow.entry || id, nodes: [...flow.nodes, { id, integrationApi: apiCode, request: {} }] });
    select({ kind: 'node', id });
  }
  function addReturn(executeStatus: ExecuteStatus) {
    const prefix = executeStatus === 'SUCCESS' ? 'succeeded' : executeStatus === 'FAIL' ? 'failed' : 'unknown';
    const id = nextId(flow, prefix);
    change({ ...flow, returns: [...flow.returns, executeStatus === 'ERROR' ? { id, executeStatus } : { id, executeStatus, outputs: {} }] });
    select({ kind: 'return', id });
  }
  function locate(issue: ValidationIssue) {
    const target = flowTarget(issue.location);
    if (target.kind === 'node' && flow.nodes[target.index]) { setSelection({ kind: 'node', id: flow.nodes[target.index].id }); setFocusId(flow.nodes[target.index].id); }
    else if (target.kind === 'return' && flow.returns[target.index]) { setSelection({ kind: 'return', id: flow.returns[target.index].id }); setFocusId(flow.returns[target.index].id); }
    else if (target.kind === 'edge' && flow.edges[target.index]) { setSelection({ kind: 'node', id: flow.edges[target.index].from }); setFocusId(flow.edges[target.index].from); }
    else if (target.kind === 'initializer' && flow.initializers[target.index]) setSelection({ kind: 'initializer', id: flow.initializers[target.index].id });
    else if (target.kind === 'entry') setSelection({ kind: 'start' });
  }
  async function validate() {
    setValidating(true);
    try {
      const result = await session.request(`${base}/validate`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ standardApiCode, flowDefinition: toFlowJson(flow), expectedVersion: detail?.version ?? null }) }, decodeValidation);
      setIssues(result.issues); setStale(false);
    } catch (error) { notify(error, '校验未完成'); } finally { setValidating(false); }
  }
  async function save(reason: string) {
    const objectCode = `${integrationCode}/${standardApiCode}`;
    const result = await editor.save(creating ? base : `${base}/${standardApiCode}`, creating ? 'POST' : 'PUT',
      creating ? { standardApiCode, flowDefinition: toFlowJson(flow), reason } : { flowDefinition: toFlowJson(flow), expectedVersion: detail.version, reason },
      value => decodeMutation(value, 'INTEGRATION_IMPLEMENTATION', objectCode));
    setSaving(false);
    if (result) {
      flushSync(() => editor.setDirty(false));
      void navigate(`${integrationRoute}/${integrationCode}/implementations/${standardApiCode}`, { replace: true });
    } else {
      void validate();
    }
  }
  const panelProps = { flow, context, functions, readOnly: disabled, onChange: change, onSelect: select };
  return <div className="configuration-page external-api-page">
    <div className="flow-workspace">
      <div className="flow-workspace-heading"><div className="flow-breadcrumb">接入方 {integrationCode} / 标准 API 实现 / {standardApiCode}</div>
        <div className="flow-heading-row"><h1>{creating ? '新建实现' : '流程'}：{standardApiCode}</h1>
          <Space wrap><span>总预算</span><InputNumber min={1} max={60000} value={flow.timeoutMs} disabled={disabled} addonAfter="ms"
            onChange={value => change({ ...flow, timeoutMs: value ?? 1 })} />
            {readOnly && canManage && detail && <Button onClick={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))}>编辑</Button>}
            {!readOnly && <><Button loading={validating} onClick={() => void validate()}>校验</Button>
              <Button type="primary" disabled={editor.conflict} onClick={() => setSaving(true)}>保存</Button></>}</Space></div>
        {!readOnly && <div className="external-api-save-row"><SaveNotice /></div>}
      </div>
      {editor.conflict && <Alert type="warning" showIcon title="该流程已被他人修改，本地修改已保留。" action={<Button onClick={onReload}>重新加载</Button>} />}
      {!readOnly && <div className="flow-workspace-actions"><Space wrap>
        <Select style={{ minWidth: 240 }} placeholder="添加原子 API 节点" value={null} disabled={disabled}
          options={context.apis.map(api => ({ value: api.apiCode, label: `${api.apiCode} · ${api.apiName}` }))} onChange={(code: string) => addNode(code)} />
        <Dropdown disabled={disabled} menu={{ items: (['SUCCESS', 'FAIL', 'ERROR'] as ExecuteStatus[]).map(status => ({ key: status, label: `${status} 出口`,
          onClick: () => addReturn(status) })) }}><Button>添加返回出口</Button></Dropdown></Space>
        <span>{context.apis.length === 0 ? '本接入方还没有原子 API，请先在“原子 API”页签中创建' : '每个节点必须恰好有一条“其他情况”出口'}</span></div>}
      <div className="flow-workspace-body">
        <InitializerList {...panelProps} selection={selection} />
        <FlowCanvas flow={flow} apis={context.apis} readOnly={disabled} selection={selection} focusId={focusId} onSelect={select} onConnect={connect} />
        {selection?.kind === 'start' && <StartPanel flow={flow} />}
        {selection?.kind === 'node' && <NodePanel {...panelProps} id={selection.id} />}
        {selection?.kind === 'return' && <ReturnPanel {...panelProps} id={selection.id} />}
        {selection?.kind === 'initializer' && <InitializerPanel {...panelProps} id={selection.id} />}
      </div>
    </div>
    <ValidationPanel issues={issues} stale={stale} onLocate={locate} />
    <ReasonDialog open={saving} saving={editor.saving} onCancel={() => setSaving(false)} onConfirm={reason => void save(reason)} />
  </div>;
}
