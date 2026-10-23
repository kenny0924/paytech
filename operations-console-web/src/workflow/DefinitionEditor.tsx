import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Input, Popover, Segmented, Space, Tooltip, Typography } from 'antd';
import { AimOutlined, ApartmentOutlined, CheckOutlined, DownOutlined, LeftOutlined, QuestionCircleOutlined, RedoOutlined, UndoOutlined, WarningOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { ApiError, session } from '../api/session';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { ActionTemplateDrawer } from './ActionTemplateEditor';
import { editorStatusText, problemTarget } from './definitionModel';
import { EdgePanel, NodePanel, RulesEditor } from './EditorPanels';
import type { PanelContext } from './EditorPanels';
import { FieldDesigner } from './FieldDesigner';
import {
  addNode, autoLayout, conditionReferences, connect, emptyHistory, insertOnEdge, localProblems, moveNode, newGraph, nextKey, nodeOf, normalizeGraph,
  publishedBaseVersionNo, record, redo, removeEdge, removeNode, undo, updateNode, variableCatalog,
} from './graphModel';
import type { History } from './graphModel';
import { needsLayout, structureProblems } from './fileModel';
import { useWorkflowCommand } from './useWorkflowCommand';
import { PublishDialog } from './PublishDialog';
import { DETAIL_PRIORITY, DetailQueue, DetailRequestCancelled, retryAfterMillis } from './prefetchModel';
import { ActionTypePicker } from './TemplateEditors';
import { WorkflowCanvas, paletteMime } from './WorkflowCanvas';
import type { CanvasSelection } from './WorkflowCanvas';
import { decodeDefinitionDetail, decodeDraft, decodeMutation, decodePage, decodeTemplate, decodeVersion, workflowPaths } from './workflowApi';
import { instanceTriggers, nodeTypeLabels, operatorLabels } from './workflowLabels';
import type { ActionTemplate, ActionType, DefinitionDetail, FormDefinition, GraphNode, NodeType, Problem, VersionBody, WorkflowGraph } from './workflowTypes';
import { LoadFailed, Spinner, personName, useLoad, workflowRoutes } from './WorkflowParts';

const decodeTemplates = decodePage(decodeTemplate);
/**
 * 引用模板的详情逐个读取（同时 1 个在途、同一模板去重、429 退避重试一次）：一次并发读多个模板会被 Gateway 管理路由限流（429），
 * 读不到的模板动作卡会一直停在「读取中…」。
 */
const templateQueue = new DetailQueue<ActionTemplate>(id => session.request(workflowPaths.template(id), {}, decodeTemplate),
  { retryDelay: error => error instanceof ApiError && error.status === 429 ? retryAfterMillis(null) : null });
const palette: { type: NodeType; hint: string }[] = [
  { type: 'TASK', hint: '动作 / 审批' }, { type: 'EXCLUSIVE', hint: '按条件走一条' },
  { type: 'PARALLEL_SPLIT', hint: '同时开启多条分支' }, { type: 'PARALLEL_JOIN', hint: '等全部分支完成' }, { type: 'END', hint: '可有多个' },
];

/** 动作模板缓存：列表（不含配置）+ 引用到的模板详情（含配置，用于变量、参数映射与说明首行）；就地新建的模板直接加入。 */
function useTemplates(graph: WorkflowGraph | null) {
  const [list, setList] = useState<ActionTemplate[]>([]);
  const [details, setDetails] = useState<Map<string, ActionTemplate>>(new Map());
  const requested = useRef(new Set<string>());
  useEffect(() => {
    const controller = new AbortController();
    requestAllPages(workflowPaths.templates, controller.signal, undefined, {}, decodeTemplates).then(setList).catch(() => undefined);
    return () => controller.abort();
  }, []);
  const ids = useMemo(() => [...new Set((graph?.nodes ?? []).flatMap(node => node.actions ?? []).map(action => action.templateId).filter(Boolean))], [graph]);
  useEffect(() => {
    for (const id of ids) {
      if (requested.current.has(id)) continue;
      requested.current.add(id);
      templateQueue.request(id, DETAIL_PRIORITY.SELECTED, 'editor-templates')
        .then(template => setDetails(current => new Map(current).set(id, template)))
        .catch(error => { if (!(error instanceof DetailRequestCancelled)) requested.current.delete(id); });
    }
  }, [ids]);
  useEffect(() => () => templateQueue.cancel('editor-templates'), []);
  const merged = useMemo(() => {
    const map = new Map(list.map(item => [item.templateId, item]));
    for (const [id, item] of details) map.set(id, item);
    return map;
  }, [list, details]);
  const add = useCallback((template: ActionTemplate) => {
    requested.current.add(template.templateId);
    setList(current => [...current.filter(item => item.templateId !== template.templateId), template]);
    setDetails(current => new Map(current).set(template.templateId, template));
  }, []);
  return { list, map: merged, add };
}

interface EditorProps {
  definitionId: string; versionNo?: number; canManage: boolean; canManageTemplates: boolean; canReadInstances: boolean; onAuthorizationChanged: () => void;
}

/** 流程编辑器路由入口：/workflow/definitions/:id（草稿）与 /:id/versions/:versionNo（已发布版本只读）。 */
export function DefinitionEditor({ definitionId, versionNo, canManage, canManageTemplates, canReadInstances, onAuthorizationChanged }: EditorProps) {
  const detail = useLoad<DefinitionDetail>(workflowPaths.definition(definitionId), decodeDefinitionDetail);
  // 详情里的版本列表不含流程图：只读查看某版本、或尚无草稿时以发布版本为底稿，都按接口 12 读取该版本全文。
  const baseVersionNo = versionNo ?? (detail.data ? publishedBaseVersionNo(detail.data) : undefined);
  const version = useLoad<VersionBody>(baseVersionNo !== undefined ? workflowPaths.version(definitionId, baseVersionNo) : null, decodeVersion);
  if (!detail.data) return detail.failure ? <LoadFailed failure={detail.failure} retry={detail.refresh} title="流程定义加载失败" /> : <Spinner />;
  if (baseVersionNo !== undefined && version.data?.versionNo !== baseVersionNo) {
    return version.failure ? <LoadFailed failure={version.failure} retry={version.refresh} title={`版本 v${baseVersionNo} 加载失败`} /> : <Spinner />;
  }
  const value = detail.data;
  const viewing = versionNo !== undefined ? version.data : undefined;
  const base = viewing ?? value.draft ?? version.data ?? null;
  return <EditorWorkspace key={`${value.definition.version}:${versionNo ?? 'draft'}`} detail={value} base={base} readOnly={!!viewing || !canManage}
    viewingVersion={viewing?.versionNo} canManageTemplates={canManageTemplates && canManage && !viewing} canReadInstances={canReadInstances}
    onReload={detail.refresh} onAuthorizationChanged={onAuthorizationChanged} />;
}

type EditorTab = 'graph' | 'form' | 'settings';

function EditorWorkspace({ detail, base, readOnly, viewingVersion, canManageTemplates, canReadInstances, onReload, onAuthorizationChanged }: {
  detail: DefinitionDetail; base: VersionBody | null; readOnly: boolean; viewingVersion?: number; canManageTemplates: boolean; canReadInstances: boolean;
  onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const { message, modal } = App.useApp(); const navigate = useNavigate();
  const definition = detail.definition;
  const [graph, setGraph] = useState<WorkflowGraph>(() => base?.graph.nodes.length ? (needsLayout(base.graph) ? autoLayout(base.graph) : base.graph) : newGraph());
  const graphRef = useRef(graph); graphRef.current = graph;
  const [startForm, setStartForm] = useState<FormDefinition>(() => base?.startForm ?? { fields: [] });
  const [name, setName] = useState(definition.definitionName); const [description, setDescription] = useState(definition.description ?? '');
  const [history, setHistory] = useState<History>(emptyHistory);
  const [selection, setSelection] = useState<CanvasSelection>(null); const [focusKey, setFocusKey] = useState<string>();
  const [tab, setTab] = useState<EditorTab>('graph');
  const [dirty, setDirty] = useState(false);
  const [expectedVersion, setExpectedVersion] = useState(definition.version);
  const [draftNo, setDraftNo] = useState<number | null>(definition.draftVersionNo);
  const [serverProblems, setServerProblems] = useState<Problem[]>(viewingVersion ? [] : detail.problems);
  const [issuesOpen, setIssuesOpen] = useState(false); const [helpOpen, setHelpOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [newActionNode, setNewActionNode] = useState<string | null>(null); const [newActionType, setNewActionType] = useState<ActionType | null>(null);
  const command = useWorkflowCommand(onAuthorizationChanged);
  const templates = useTemplates(graph);
  const people = detail.people;
  const local = useMemo(() => readOnly ? [] : localProblems(graph, templates.map, startForm), [graph, templates.map, startForm, readOnly]);
  // 草稿保存 422：图与启动表单的结构错误，按请求体路径定位到节点、连线或字段。
  const structural = useMemo(() => structureProblems(command.fieldErrors, normalizeGraph(graph), startForm),
    [command.fieldErrors, graph, startForm]);
  // 有未保存修改时显示实时本地检查；保存后以服务端返回的问题为准（technique 第 7 节）。
  const problems = useMemo(() => readOnly ? [] : [...structural, ...(dirty ? local : serverProblems)], [readOnly, structural, dirty, local, serverProblems]);
  // 保存被拒（422 结构错误）时自动展开问题清单，便于定位。
  useEffect(() => { if (structural.length) setIssuesOpen(true); }, [structural.length]);
  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    modal.confirm({ title: '离开流程编辑器？', content: '尚未保存的修改将丢失。', okText: '离开', cancelText: '继续编辑', onOk: () => resolve(true), onCancel: () => resolve(false) });
  }), [modal]);
  useUnsavedRuleGuard(dirty && !readOnly, confirmLeave);

  function change(next: WorkflowGraph, remember = true) {
    if (readOnly) return;
    if (remember) setHistory(current => record(current, graphRef.current));
    graphRef.current = next; setGraph(next); setDirty(true);
  }
  function step(direction: 'undo' | 'redo') {
    const result = direction === 'undo' ? undo(history, graph) : redo(history, graph);
    if (result) { setGraph(result.graph); setHistory(result.history); setDirty(true); }
  }
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if ((event.target as HTMLElement | null)?.closest('input, textarea, [contenteditable="true"]')) return;
      if (event.key === 'Escape' && tab === 'graph' && !document.querySelector('.ant-modal-wrap, .ant-drawer-open')) { setSelection(null); return; }
      if (readOnly || !(event.metaKey || event.ctrlKey) || event.key.toLowerCase() !== 'z') return;
      event.preventDefault(); step(event.shiftKey ? 'redo' : 'undo');
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });
  function add(type: NodeType, position?: { x: number; y: number }) {
    const fallback = { x: Math.max(0, ...graph.nodes.map(node => node.position.x)) + 120, y: 160 };
    const result = addNode(graph, type, position ?? fallback);
    change(result.graph); setSelection({ kind: 'node', key: result.key });
  }
  function insert(edgeKey: string, type: NodeType) {
    const result = insertOnEdge(graph, edgeKey, type);
    if ('error' in result) { void message.warning(result.error); return; }
    change(result.graph); setSelection({ kind: 'node', key: result.key });
  }
  function connectNodes(from: string, to: string) {
    const result = connect(graph, from, to);
    if ('error' in result) { void message.warning(result.error); return; }
    change(result.graph); if (nodeOf(graph, from)?.type === 'EXCLUSIVE') setSelection({ kind: 'edge', key: result.edgeKey });
  }
  function deleteSelection(target: NonNullable<CanvasSelection>) {
    if (target.kind === 'node') { if (nodeOf(graph, target.key)?.type === 'START') return; change(removeNode(graph, target.key)); }
    else change(removeEdge(graph, target.key));
    setSelection(null);
  }
  function focus(target: { nodeKey?: string | null; edgeKey?: string | null }) {
    setTab('graph');
    if (target.edgeKey && graph.edges.some(edge => edge.key === target.edgeKey)) { setSelection({ kind: 'edge', key: target.edgeKey }); setFocusKey(target.edgeKey); }
    else if (target.nodeKey && nodeOf(graph, target.nodeKey)) { setSelection({ kind: 'node', key: target.nodeKey }); setFocusKey(target.nodeKey); }
    setTimeout(() => setFocusKey(undefined), 400);
  }
  function locate(problem: Problem) {
    setIssuesOpen(false);
    if (problem.code === 'STRUCTURE_INVALID' && problem.itemKey && !problem.nodeKey && !problem.edgeKey) { setTab('form'); return; }
    focus(problem);
  }
  async function save(quiet = false): Promise<number | null> {
    const result = await command.run(workflowPaths.draft(definition.definitionId), 'PUT', { definitionName: name.trim() || definition.definitionName,
      ...(description.trim() ? { description: description.trim() } : {}), graph: normalizeGraph(graph), startForm, expectedVersion }, decodeDraft, '草稿保存失败');
    if (!result) return null;
    setExpectedVersion(result.version); setDraftNo(result.draftVersionNo || draftNo); setServerProblems(result.problems); setDirty(false);
    if (!quiet) void message.success(result.problems.length ? `草稿已保存，发布前还需处理 ${result.problems.length} 个问题` : '草稿已保存，没有发现问题');
    return result.version;
  }
  /** 发布：先保存草稿取服务端问题，再弹出确认（变更摘要 + 检查结果）。 */
  async function openPublish() {
    if (dirty && await save(true) === null) return;
    setPublishing(true);
  }
  async function publish() {
    const result = await command.run(workflowPaths.publication(definition.definitionId), 'POST', { expectedVersion }, decodeMutation, '发布失败');
    if (result) { setPublishing(false); void message.success('已发布新版本；运行中的实例仍按原版本执行'); onReload(); return; }
    // 校验不通过时后端返回 400；重新读取草稿问题列表，确认弹窗里显示。
    try {
      const fresh = await session.request(workflowPaths.definition(definition.definitionId), {}, decodeDefinitionDetail);
      setServerProblems(fresh.problems);
    } catch { /* 保留原问题 */ }
  }
  function addCreatedAction(template: ActionTemplate) {
    const nodeKey = newActionNode; setNewActionType(null); setNewActionNode(null);
    templates.add(template);
    const current = graphRef.current; const node = nodeKey ? nodeOf(current, nodeKey) : undefined;
    if (!node) return;
    change(updateNode(current, node.key, { actions: [...(node.actions ?? []), { key: nextKey(current, 'action'), templateId: template.templateId, required: true }] }));
    void message.success(`已新建「${template.templateName}」并加入「${node.name || node.key}」，记得保存草稿`);
  }
  const problemByNode = useMemo(() => {
    const map = new Map<string, string>();
    for (const problem of problems) {
      const key = problem.nodeKey ?? (problem.edgeKey ? graph.edges.find(edge => edge.key === problem.edgeKey)?.from : undefined);
      if (key && !map.has(key)) map.set(key, problem.message);
    }
    return map;
  }, [problems, graph.edges]);
  const variablesFor = useCallback((gatewayKey: string) => variableCatalog(graph, startForm, templates.map, gatewayKey), [graph, startForm, templates.map]);
  const decorate = useCallback((node: GraphNode) => ({ error: problemByNode.get(node.key), subtitle: node.type === 'TASK' ? taskSubtitle(node, people) : undefined }),
    [problemByNode, people]);
  const context: PanelContext = { graph, startForm, templates: templates.map, templateList: templates.list, readOnly, problems, people,
    onChange: next => change(next), onDelete: () => selection && deleteSelection(selection), onClose: () => setSelection(null),
    onRequestNewAction: canManageTemplates ? nodeKey => setNewActionNode(nodeKey) : undefined };
  const selectedNode = selection?.kind === 'node' ? nodeOf(graph, selection.key) : undefined;
  const selectedEdge = selection?.kind === 'edge' && graph.edges.some(edge => edge.key === selection.key) ? selection.key : undefined;
  const panelOpen = !!selectedNode || !!selectedEdge;
  const references = (field: { key: string }) => conditionReferences(graph, `start.${field.key}`)
    .map(item => `排他网关「${item.node.name}」的条件：start.${field.key} ${operatorLabels[item.clause.operator]} ${Array.isArray(item.clause.value) ? item.clause.value.join(', ') : item.clause.value ?? ''}`);
  const statusText = editorStatusText({ currentVersionNo: definition.currentVersionNo, hasDraft: draftNo !== null, dirty, viewingVersion });
  const nextVersionNo = draftNo ?? Math.max(definition.currentVersionNo ?? 0, ...detail.versions.map(item => item.versionNo)) + 1;
  return <div className="wf-page wf-editor">
    <div className="wf-editor-head">
      <nav className="wf-editor-crumbs"><Link to={workflowRoutes.definitions}><LeftOutlined /> 流程定义</Link><span className="muted">/</span>
        <strong title={definition.definitionCode}>{name || definition.definitionName}</strong></nav>
      <span className={`wf-status-pill ${viewingVersion ? 'is-ok' : 'is-draft'}`}>{statusText}</span>
      {dirty && <span className="wf-warn-text wf-small">有未保存的修改</span>}
      {!readOnly && <Popover open={issuesOpen} onOpenChange={setIssuesOpen} trigger="click" placement="bottomLeft"
        content={<IssueList problems={problems} graph={graph} onLocate={locate} />}>
        <button type="button" className={`wf-issues-badge ${problems.length ? 'is-warn' : 'is-ok'}`}>
          {problems.length ? <><WarningOutlined /> {problems.length} 个问题</> : <><CheckOutlined /> 0 个问题</>} <DownOutlined aria-hidden /></button>
      </Popover>}
      <span className="wf-grow" />
      <Segmented value={tab} onChange={value => setTab(value as EditorTab)} options={[{ value: 'graph', label: '流程图' }, { value: 'form', label: '启动表单' }, { value: 'settings', label: '流程设置' }]} />
      <span className="wf-grow" />
      {!readOnly && <Space>
        <Button loading={command.saving && !publishing} onClick={() => void save()}>保存草稿</Button>
        <Button type="primary" loading={command.saving && !publishing} onClick={() => void openPublish()}>发布</Button>
      </Space>}
      {viewingVersion && <Button onClick={() => void navigate(`${workflowRoutes.definitions}/${definition.definitionId}`)}>回到草稿</Button>}
    </div>
    {command.conflict && <Alert type="warning" showIcon title="草稿已被他人保存或发布，你的修改未保存"
      description="可以先复制当前草稿 JSON，再重新加载最新内容后合并。" action={<Space>
        <Typography.Text copyable={{ text: JSON.stringify({ graph: normalizeGraph(graph), startForm }, null, 2), tooltips: ['复制草稿 JSON', '已复制'] }}>草稿 JSON</Typography.Text>
        <Button onClick={() => { setDirty(false); command.clearConflict(); onReload(); }}>重新加载</Button></Space>} />}
    {command.formError && !publishing && <Alert type="error" showIcon title={command.formError} closable={{ onClose: command.clearError }} />}
    {readOnly && !viewingVersion && <Alert type="info" showIcon title="只读 · 没有“维护流程定义”权限" />}
    {tab === 'graph' && <div className={`wf-editor-stage${panelOpen ? ' has-panel' : ''}`}>
      <div className="wf-editor-canvas">
        {!readOnly && <NodeToolbar canUndo={history.past.length > 0} canRedo={history.future.length > 0} helpOpen={helpOpen} onHelp={setHelpOpen}
          onAdd={type => add(type)} onUndo={() => step('undo')} onRedo={() => step('redo')} onLayout={() => change(autoLayout(graph))} />}
        <WorkflowCanvas graph={graph} editable={!readOnly} selection={selection} onSelect={setSelection} decorate={decorate} variables={variablesFor}
          focusKey={focusKey} onConnect={connectNodes} onMove={(key, position) => change(moveNode(graph, key, position))} onDelete={deleteSelection}
          onDropNode={(type, position) => add(type, position)} onInsertOnEdge={readOnly ? undefined : insert} height="calc(100vh - 176px)" />
      </div>
      {panelOpen && <aside className="wf-editor-panel">
        {selectedEdge ? <EdgePanel key={selectedEdge} edgeKey={selectedEdge} context={context} />
          : selectedNode && <NodePanel key={selectedNode.key} node={selectedNode} context={context} />}
      </aside>}
    </div>}
    {tab === 'form' && <FieldDesigner form={startForm} readOnly={readOnly} variablePrefix="start." previewTitle="发起人将看到这张表单"
      references={references} onChange={form => { setStartForm(form); setDirty(true); }} />}
    {tab === 'settings' && <div className="wf-settings">
      <section className="wf-card"><div className="wf-card-title">基本信息</div>
        <div className="wf-field"><div className="wf-field-label">流程编码</div><code>{definition.definitionCode}</code></div>
        <div className="wf-field"><div className="wf-field-label">流程名称</div><Input value={name} maxLength={128} disabled={readOnly} onChange={event => { setName(event.target.value); setDirty(true); }} /></div>
        <div className="wf-field"><div className="wf-field-label">说明</div><Input.TextArea rows={3} maxLength={512} value={description} disabled={readOnly}
          onChange={event => { setDescription(event.target.value); setDirty(true); }} /></div>
      </section>
      <section className="wf-card"><div className="wf-card-title">实例级通知与回调</div>
        <RulesEditor rules={graph.settings?.notifications ?? []} triggers={instanceTriggers} readOnly={readOnly}
          people={people} onChange={notifications => change({ ...graph, settings: { notifications } })}
          note="实例发起、完成、终止时执行；规则随版本发布冻结。" /></section>
    </div>}
    {publishing && <PublishDialog definition={definition} nextVersionNo={nextVersionNo} draft={{ graph: normalizeGraph(graph), startForm }} problems={problems}
      templates={templates.map} canReadInstances={canReadInstances} saving={command.saving} error={command.formError}
      onCancel={() => { setPublishing(false); command.clearError(); }} onConfirm={() => void publish()}
      onLocate={({ problem, change: item }) => {
        setPublishing(false);
        if (problem) locate(problem);
        else if (item?.area === 'START_FORM') setTab('form');
        else if (item) focus(item);
      }} />}
    {newActionNode && !newActionType && <ActionTypePicker onCancel={() => setNewActionNode(null)} onNext={type => setNewActionType(type)} />}
    <ActionTemplateDrawer open={!!newActionNode && !!newActionType} actionType={newActionType ?? undefined} canManage={canManageTemplates}
      onAuthorizationChanged={onAuthorizationChanged} onCreated={addCreatedAction} onClose={() => { setNewActionType(null); setNewActionNode(null); }} />
  </div>;
}

/** 任务节点副标题：「2 个动作 · 审批 · 赵磊 等 2 人」；什么都没配置时提示。 */
function taskSubtitle(node: GraphNode, people: Record<string, string>): string {
  const actions = node.actions?.length ?? 0; const approvers = node.approval?.approverIds ?? [];
  const parts = [actions ? `${actions} 个动作` : '', approvers.length ? `审批 · ${personName(approvers[0], people)}${approvers.length > 1 ? ` 等 ${approvers.length} 人` : ''}` : ''];
  return parts.filter(Boolean).join(' · ') || '未设置动作与审批人';
}

/** 问题清单浮层：「位置：说明」+ 定位（原型 10）。 */
function IssueList({ problems, graph, onLocate }: { problems: Problem[]; graph: WorkflowGraph; onLocate: (problem: Problem) => void }) {
  if (!problems.length) return <div className="wf-issue-list"><div className="wf-ok-text"><CheckOutlined /> 没有发现问题</div><div className="muted">保存草稿后以服务端检查结果为准</div></div>;
  return <div className="wf-issue-list">
    <div className="wf-issue-title">发布前需要处理 {problems.length} 个问题</div>
    {problems.map((problem, index) => <div key={index} className="wf-issue-row">
      <span className="wf-warn-text" aria-hidden><WarningOutlined /></span>
      <span className="wf-grow"><strong>{problemTarget(problem, graph)}：</strong><span className="muted">{problem.message}</span></span>
      {(problem.nodeKey || problem.edgeKey || problem.code === 'STRUCTURE_INVALID' && problem.itemKey) &&
        <Button type="link" size="small" icon={<AimOutlined />} onClick={() => onLocate(problem)}>定位</Button>}
    </div>)}
  </div>;
}

/** 画布左上角的竖排工具条：节点类型（拖到画布或点击添加，悬停说明）、撤销 / 重做 / 自动布局、操作说明。 */
function NodeToolbar({ canUndo, canRedo, helpOpen, onHelp, onAdd, onUndo, onRedo, onLayout }: {
  canUndo: boolean; canRedo: boolean; helpOpen: boolean; onHelp: (open: boolean) => void; onAdd: (type: NodeType) => void;
  onUndo: () => void; onRedo: () => void; onLayout: () => void;
}) {
  return <div className="wf-node-toolbar" role="toolbar" aria-label="节点工具条">
    {palette.map(item => <Tooltip key={item.type} placement="right" title={`${nodeTypeLabels[item.type]} · ${item.hint}（拖到画布或点击添加）`}>
      <button type="button" className="wf-tool" draggable aria-label={nodeTypeLabels[item.type]}
        onDragStart={event => { event.dataTransfer.setData(paletteMime, item.type); event.dataTransfer.effectAllowed = 'copy'; }}
        onClick={() => onAdd(item.type)}><span className={`wf-tool-shape is-${item.type.toLowerCase()}`} /></button>
    </Tooltip>)}
    <span className="wf-tool-divider" />
    <Tooltip placement="right" title="撤销（⌘Z / Ctrl+Z）"><button type="button" className="wf-tool" aria-label="撤销" disabled={!canUndo} onClick={onUndo}><UndoOutlined /></button></Tooltip>
    <Tooltip placement="right" title="重做（⇧⌘Z）"><button type="button" className="wf-tool" aria-label="重做" disabled={!canRedo} onClick={onRedo}><RedoOutlined /></button></Tooltip>
    <Tooltip placement="right" title="自动布局"><button type="button" className="wf-tool" aria-label="自动布局" onClick={onLayout}><ApartmentOutlined /></button></Tooltip>
    <span className="wf-tool-divider" />
    <Popover open={helpOpen} onOpenChange={onHelp} trigger="click" placement="rightTop" title="操作说明" content={<ul className="wf-plain-list">
      <li>拖动图标到画布，或点击图标添加节点</li><li>从节点右侧连接点拖出连线</li><li>鼠标移到连线上点「+」，在这条线上插入节点</li>
      <li>选中节点或连线在右侧编辑；Esc 关闭面板</li><li>开始节点固定一个；结束节点可有多个</li><li>Delete 删除 · ⌘Z / Ctrl+Z 撤销</li></ul>}>
      <button type="button" className="wf-tool" aria-label="操作说明"><QuestionCircleOutlined /></button>
    </Popover>
  </div>;
}
