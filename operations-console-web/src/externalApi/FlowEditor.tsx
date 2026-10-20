import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { ReactFlowProvider } from '@xyflow/react';
import { Alert, App, Button, InputNumber } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ReasonDialog, ValidationPanel, useReasonFlow } from './EditorParts';
import { FlowCanvas, startId } from './FlowCanvas';
import type { FlowSelection } from './FlowCanvas';
import { EmptySide, ExitsSide, FlowSidebar, InitializerSide, NodeSide, ReturnSide, StartSide, ValuePopover } from './FlowPanels';
import type { ValueTarget } from './FlowPanels';
import { decodeCapabilities, decodeValidation } from './editorModel';
import type { Capabilities, ValidationIssue } from './editorModel';
import { ConflictBanner, Loading, LoadFailure, hasButton, useCopyDraft, useDetail, useEditorSave } from './externalApiHooks';
import { decodeMutation, externalApiBase, integrationPath, integrationRoute, manageIntegrations, replaceQuery } from './externalApiModel';
import {
  addEdge, decodeFlowContext, decodeImplementation, flowTarget, idPrefix, newFlow, nextId, toFlow, toFlowJson,
} from './flowModel';
import type { ExecuteStatus, Flow, FlowEditorContext, ImplementationDetail } from './flowModel';
import { ExternalApiFrame, Note, PageHeader } from './ui';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; integrationCode: string; standardApiCode: string }
const capabilityPath = `${externalApiBase}/editor-capabilities`;
const noManage = '只读 · 无“外部 API 管理”权限';

/** 校验问题所属对象的前缀，例如“出口 paid：”。 */
function issueOwner(flow: Flow, location: string): string {
  const target = flowTarget(location);
  if (target.kind === 'node') return flow.nodes[target.index] ? `节点 ${flow.nodes[target.index].id}：` : '';
  if (target.kind === 'return') return flow.returns[target.index] ? `出口 ${flow.returns[target.index].id}：` : '';
  if (target.kind === 'edge') return flow.edges[target.index] ? `${flow.edges[target.index].from} 的出口：` : '';
  if (target.kind === 'initializer') return flow.initializers[target.index] ? `初始化项 ${flow.initializers[target.index].id}：` : '';
  return '';
}

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
  let page;
  if (creating && !canManage) page = <Alert type="warning" showIcon title="当前没有接入方维护权限" />;
  else if (context.loading || capabilities.loading || detail.loading) page = <Loading />;
  else if (!context.data) page = <LoadFailure title="流程编辑数据加载失败" failure={context.failure} retry={context.refresh} />;
  else if (!creating && !detail.data) page = <LoadFailure title="标准 API 实现加载失败" failure={detail.failure} retry={detail.refresh} />;
  else page = <ReactFlowProvider><FlowWorkspace key={`${detail.data?.version}:${readOnly}`} integrationCode={integrationCode}
    standardApiCode={standardApiCode} context={context.data} functions={capabilities.data?.functions ?? []} detail={detail.data}
    readOnly={readOnly} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} onReload={detail.refresh} /></ReactFlowProvider>;
  return <ExternalApiFrame className="xflow-page">{page}</ExternalApiFrame>;
}

function FlowWorkspace({ integrationCode, standardApiCode, context, functions, detail, readOnly, canManage, onAuthorizationChanged, onReload }: {
  integrationCode: string; standardApiCode: string; context: FlowEditorContext; functions: Capabilities['functions'];
  detail?: ImplementationDetail; readOnly: boolean; canManage: boolean; onAuthorizationChanged: () => void; onReload: () => void;
}) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification(); const { message } = App.useApp();
  const copy = useCopyDraft();
  const [issues, setIssues] = useState<ValidationIssue[] | null>(null);
  const errorCount = issues?.filter(issue => issue.severity === 'ERROR').length ?? 0;
  const editor = useEditorSave(onAuthorizationChanged, errorCount ? `当前流程还有 ${errorCount} 个校验错误，保存会失败并停留在本页。` : undefined);
  const creating = !detail; const base = `${integrationPath}/${integrationCode}/implementations`;
  const [flow, setFlow] = useState<Flow>(() => detail ? toFlow(detail.flowDefinition) : newFlow());
  const [selection, setSelection] = useState<FlowSelection>(null);
  const [focusId, setFocusId] = useState<string>();
  const [stale, setStale] = useState(false);
  const [validating, setValidating] = useState(false);
  const [valueTarget, setValueTarget] = useState<ValueTarget | null>(null);
  const leaving = useRef(false);
  const disabled = readOnly || editor.saving;
  function change(next: Flow) { setFlow(next); editor.setDirty(true); setStale(issues !== null); }
  function select(next: FlowSelection) { setSelection(next); setFocusId(undefined); setValueTarget(null); }
  function connect(source: string, target: string) {
    if (source === startId) {
      if (!flow.nodes.some(node => node.id === target)) { void message.warning('入口必须是原子 API 节点'); return; }
      change({ ...flow, entry: target }); return;
    }
    const result = addEdge(flow, source, target);
    if ('error' in result) { void message.warning(result.error); return; }
    change(result.flow); select({ kind: 'exits', id: source });
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
    const pick = (next: FlowSelection, focus?: string) => { select(next); setFocusId(focus); };
    if (target.kind === 'node' && flow.nodes[target.index]) pick({ kind: 'node', id: flow.nodes[target.index].id }, flow.nodes[target.index].id);
    else if (target.kind === 'return' && flow.returns[target.index]) pick({ kind: 'return', id: flow.returns[target.index].id }, flow.returns[target.index].id);
    else if (target.kind === 'edge' && flow.edges[target.index]) pick({ kind: 'exits', id: flow.edges[target.index].from }, flow.edges[target.index].from);
    else if (target.kind === 'initializer' && flow.initializers[target.index]) pick({ kind: 'initializer', id: flow.initializers[target.index].id });
    else if (target.kind === 'entry') pick({ kind: 'start' });
  }
  async function validate() {
    setValidating(true);
    try {
      const result = await session.request(`${base}/validate`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ standardApiCode, flowDefinition: toFlowJson(flow), expectedVersion: detail?.version ?? null }) }, decodeValidation);
      setIssues(result.issues); setStale(false);
      if (!result.issues.length) void message.success('校验通过，配置可以保存');
    } catch (error) { notify(error, '校验未完成'); } finally { setValidating(false); }
  }
  const reasonFlow = useReasonFlow(async reason => {
    const objectCode = `${integrationCode}/${standardApiCode}`;
    const result = await editor.save(creating ? base : `${base}/${standardApiCode}`, creating ? 'POST' : 'PUT',
      creating ? { standardApiCode, flowDefinition: toFlowJson(flow), reason } : { flowDefinition: toFlowJson(flow), expectedVersion: detail.version, reason },
      value => decodeMutation(value, 'INTEGRATION_IMPLEMENTATION', objectCode));
    if (!result) { if (!editor.conflict) void validate(); return false; }
    flushSync(() => editor.setDirty(false));
    if (!leaving.current) void navigate(`${integrationRoute}/${integrationCode}/implementations/${standardApiCode}`, { replace: true });
    return true;
  });
  const { setSaveForLeave } = editor;
  useEffect(() => setSaveForLeave(readOnly ? null : () => { leaving.current = true; return reasonFlow.start().finally(() => { leaving.current = false; }); }));
  const latest = useCallback(() => session.request(`${base}/${standardApiCode}`, {}, value => decodeImplementation(value, standardApiCode)), [base, standardApiCode]);
  /** 校验问题按节点或出口归集，画布上标红并写出原因（每个对象取第一条）。 */
  const vertexErrors = useMemo(() => {
    const result = new Map<string, string>();
    for (const issue of issues ?? []) {
      if (issue.severity !== 'ERROR') continue;
      const target = flowTarget(issue.location);
      const id = target.kind === 'node' ? flow.nodes[target.index]?.id : target.kind === 'return' ? flow.returns[target.index]?.id
        : target.kind === 'edge' ? flow.edges[target.index]?.from : undefined;
      if (id && !result.has(id)) result.set(id, issue.message);
    }
    return result;
  }, [issues, flow]);
  const panelProps = { flow, context, functions, readOnly: disabled, standardApiCode, onChange: change, onSelect: select };
  const workspace = `${integrationRoute}/${integrationCode}`;
  return <>
    <PageHeader crumbs={[{ label: '渠道' }, { label: `接入方 ${integrationCode}`, to: workspace },
      { label: '标准 API 实现', to: `${workspace}?tab=implementations` }, { label: standardApiCode }]}
      title={`${integrationCode} 实现 ${standardApiCode}`} readOnlyHint={!canManage ? noManage : undefined}
      actions={<><span className="xapi-field-label">流程总预算</span>
        <InputNumber min={1} max={60000} value={flow.timeoutMs} disabled={disabled} className="xapi-mono-input xflow-budget" suffix="ms"
          onChange={value => change({ ...flow, timeoutMs: value ?? 1 })} />
        {readOnly ? canManage && detail && <Button type="primary" onClick={() => void navigate(replaceQuery(location.search, { mode: 'edit' }))}>编辑</Button>
          : <>{editor.dirty && <span className="xflow-dirty">有未保存的修改</span>}<Button loading={validating} onClick={() => void validate()}>校验</Button>
            <Button type="primary" loading={editor.saving} onClick={() => void reasonFlow.start()}>保存</Button></>}</>} />
    {editor.conflict && detail && <ConflictBanner code={`${integrationCode}/${standardApiCode}`} baseVersion={detail.version} latest={latest}
      onReload={onReload} onCopy={() => copy(toFlowJson(flow))} />}
    <div className="xflow-workbench">
      <FlowSidebar {...panelProps} selection={selection} onAddNode={addNode} onAddReturn={addReturn} />
      <div className="xflow-center">
        <FlowCanvas flow={flow} apis={context.apis} standardApiCode={standardApiCode} readOnly={disabled} selection={selection} focusId={focusId}
          errors={vertexErrors} onSelect={select} onConnect={connect} />
        <Note items={['保存后对新调用立即生效，无发布流程；进行中的调用使用已加载的配置。', '流程暂不支持环：拖回上游节点的连线会被拒绝；每个节点在一次调用中最多执行一次。',
          '节点只能引用本接入方的原子 API；流程不编排冲正、撤销、退款等资金反向操作（人工评审）。']} />
      </div>
      {selection === null && <EmptySide />}
      {selection?.kind === 'start' && <StartSide flow={flow} standardApiCode={standardApiCode} />}
      {selection?.kind === 'node' && <NodeSide {...panelProps} id={selection.id} onEdit={setValueTarget} />}
      {selection?.kind === 'exits' && <ExitsSide key={selection.id} {...panelProps} id={selection.id} />}
      {selection?.kind === 'return' && <ReturnSide {...panelProps} id={selection.id} onEdit={setValueTarget} />}
      {selection?.kind === 'initializer' && <InitializerSide {...panelProps} id={selection.id} onEdit={setValueTarget} />}
      {valueTarget && <ValuePopover key={valueTarget.title} target={valueTarget} functions={functions} onClose={() => setValueTarget(null)} />}
    </div>
    <ValidationPanel issues={issues?.map(issue => ({ ...issue, message: `${issueOwner(flow, issue.location)}${issue.message}` })) ?? null}
      stale={stale} onLocate={locate} />
    <ReasonDialog open={reasonFlow.open} saving={editor.saving} onCancel={reasonFlow.cancel} onConfirm={reason => void reasonFlow.confirm(reason)} />
  </>;
}
