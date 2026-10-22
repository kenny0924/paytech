import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Empty, Input, Segmented, Space, Tag, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { session } from '../api/session';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { EdgePanel, NodePanel, RulesEditor } from './EditorPanels';
import type { PanelContext } from './EditorPanels';
import { FieldDesigner } from './FieldDesigner';
import {
  addNode, autoLayout, conditionReferences, connect, emptyHistory, localProblems, moveNode, newGraph, nodeOf, normalizeGraph, publishedBaseVersionNo, record, redo,
  removeEdge, removeNode, undo, variableCatalog,
} from './graphModel';
import type { History } from './graphModel';
import { needsLayout, structureProblems } from './fileModel';
import { useWorkflowCommand } from './useWorkflowCommand';
import { WorkflowCanvas, paletteMime } from './WorkflowCanvas';
import type { CanvasSelection } from './WorkflowCanvas';
import { decodeDefinitionDetail, decodeDraft, decodeMutation, decodePage, decodeTemplate, decodeVersion, workflowPaths } from './workflowApi';
import { instanceTriggers, nodeTypeLabels, operatorLabels } from './workflowLabels';
import type { ActionTemplate, DefinitionDetail, FormDefinition, GraphNode, NodeType, Problem, VersionBody, WorkflowGraph } from './workflowTypes';
import { LoadFailed, Spinner, useLoad, workflowRoutes } from './WorkflowParts';

const decodeTemplates = decodePage(decodeTemplate);
const palette: { type: NodeType; hint: string }[] = [
  { type: 'TASK', hint: '审批人 / 动作' }, { type: 'EXCLUSIVE', hint: '按条件走一条' },
  { type: 'PARALLEL_SPLIT', hint: '同时开启多条分支' }, { type: 'PARALLEL_JOIN', hint: '等全部分支完成' }, { type: 'END', hint: '可有多个' },
];

/** 动作模板缓存：列表（不含配置）+ 引用到的模板详情（含配置，用于变量与参数映射）。 */
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
      session.request(workflowPaths.template(id), {}, decodeTemplate)
        .then(template => setDetails(current => new Map(current).set(id, template))).catch(() => requested.current.delete(id));
    }
  }, [ids]);
  const merged = useMemo(() => {
    const map = new Map(list.map(item => [item.templateId, item]));
    for (const [id, item] of details) map.set(id, item);
    return map;
  }, [list, details]);
  return { list, map: merged };
}

/** 流程编辑器路由入口：/workflow/definitions/:id（草稿）与 /:id/versions/:versionNo（已发布版本只读）。 */
export function DefinitionEditor({ definitionId, versionNo, canManage, onAuthorizationChanged }: {
  definitionId: string; versionNo?: number; canManage: boolean; onAuthorizationChanged: () => void;
}) {
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
    viewingVersion={viewing?.versionNo} onReload={detail.refresh} onAuthorizationChanged={onAuthorizationChanged} />;
}

type EditorTab = 'graph' | 'form' | 'settings';

function EditorWorkspace({ detail, base, readOnly, viewingVersion, onReload, onAuthorizationChanged }: {
  detail: DefinitionDetail; base: VersionBody | null; readOnly: boolean; viewingVersion?: number; onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const { message, modal } = App.useApp(); const navigate = useNavigate();
  const definition = detail.definition;
  const [graph, setGraph] = useState<WorkflowGraph>(() => base?.graph.nodes.length ? (needsLayout(base.graph) ? autoLayout(base.graph) : base.graph) : newGraph());
  const [startForm, setStartForm] = useState<FormDefinition>(() => base?.startForm ?? { fields: [] });
  const [name, setName] = useState(definition.definitionName); const [description, setDescription] = useState(definition.description ?? '');
  const [history, setHistory] = useState<History>(emptyHistory);
  const [selection, setSelection] = useState<CanvasSelection>(null); const [focusKey, setFocusKey] = useState<string>();
  const [tab, setTab] = useState<EditorTab>('graph');
  const [dirty, setDirty] = useState(false);
  const [expectedVersion, setExpectedVersion] = useState(definition.version);
  const [serverProblems, setServerProblems] = useState<Problem[]>(viewingVersion ? [] : detail.problems);
  const [showProblems, setShowProblems] = useState(true);
  const command = useWorkflowCommand(onAuthorizationChanged);
  const templates = useTemplates(graph);
  const people = detail.people;
  const local = useMemo(() => localProblems(graph, templates.map, startForm), [graph, templates.map, startForm]);
  // 草稿保存 422：图与启动表单的结构错误，按请求体路径定位到节点、连线或字段。
  const structural = useMemo(() => structureProblems(command.fieldErrors, normalizeGraph(graph), startForm),
    [command.fieldErrors, graph, startForm]);
  const problems = useMemo(() => {
    const seen = new Set(serverProblems.map(problem => `${problem.nodeKey}:${problem.edgeKey}:${problem.message}`));
    return [...structural, ...serverProblems, ...(dirty ? local.filter(problem => !seen.has(`${problem.nodeKey}:${problem.edgeKey}:${problem.message}`)) : [])];
  }, [structural, serverProblems, local, dirty]);
  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    modal.confirm({ title: '离开流程编辑器？', content: '尚未保存的修改将丢失。', okText: '离开', cancelText: '继续编辑', onOk: () => resolve(true), onCancel: () => resolve(false) });
  }), [modal]);
  useUnsavedRuleGuard(dirty && !readOnly, confirmLeave);

  function change(next: WorkflowGraph, remember = true) {
    if (readOnly) return;
    if (remember) setHistory(current => record(current, graph));
    setGraph(next); setDirty(true);
  }
  function step(direction: 'undo' | 'redo') {
    const result = direction === 'undo' ? undo(history, graph) : redo(history, graph);
    if (result) { setGraph(result.graph); setHistory(result.history); setDirty(true); }
  }
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (readOnly || !(event.metaKey || event.ctrlKey) || event.key.toLowerCase() !== 'z') return;
      if ((event.target as HTMLElement | null)?.closest('input, textarea')) return;
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
  function locate(problem: Problem) {
    if (problem.code === 'STRUCTURE_INVALID' && problem.itemKey && !problem.nodeKey && !problem.edgeKey) { setTab('form'); return; }
    setTab('graph');
    if (problem.edgeKey && graph.edges.some(edge => edge.key === problem.edgeKey)) { setSelection({ kind: 'edge', key: problem.edgeKey }); setFocusKey(graph.edges.find(edge => edge.key === problem.edgeKey)!.from); }
    else if (problem.nodeKey && nodeOf(graph, problem.nodeKey)) { setSelection({ kind: 'node', key: problem.nodeKey }); setFocusKey(problem.nodeKey); }
    setTimeout(() => setFocusKey(undefined), 400);
  }
  async function save(): Promise<number | null> {
    const result = await command.run(workflowPaths.draft(definition.definitionId), 'PUT', { definitionName: name.trim() || definition.definitionName,
      ...(description.trim() ? { description: description.trim() } : {}), graph: normalizeGraph(graph), startForm, expectedVersion }, decodeDraft, '草稿保存失败');
    if (!result) { setShowProblems(true); return null; }
    setExpectedVersion(result.version); setServerProblems(result.problems); setDirty(false); setShowProblems(true);
    void message.success(result.problems.length ? `草稿已保存，发布前还需解决 ${result.problems.length} 个问题` : '草稿已保存，校验通过');
    return result.version;
  }
  async function publish() {
    const version = dirty ? await save() : expectedVersion;
    if (version === null) return;
    const result = await command.run(workflowPaths.publication(definition.definitionId), 'POST', { expectedVersion: version }, decodeMutation, '发布失败');
    if (result) { void message.success('已发布新版本；运行中的实例仍按原版本执行'); onReload(); return; }
    // 校验不通过时后端返回 400；重新读取草稿问题列表用于标红定位。
    try {
      const fresh = await session.request(workflowPaths.definition(definition.definitionId), {}, decodeDefinitionDetail);
      setServerProblems(fresh.problems); setShowProblems(true);
    } catch { /* 保留原问题 */ }
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
  const decorate = useCallback((node: GraphNode) => ({ error: problemByNode.get(node.key),
    subtitle: node.type === 'TASK' ? [`${node.actions?.length ?? 0} 动作`, node.approval?.approverIds.length ? `审批 ${node.approval.approverIds.length} 人 · ${node.approval.mode}` : '无审批'].join(' · ') : undefined }),
  [problemByNode]);
  const context: PanelContext = { graph, startForm, templates: templates.map, templateList: templates.list, readOnly, problems, people,
    onChange: next => change(next), onDelete: () => selection && deleteSelection(selection) };
  const selectedNode = selection?.kind === 'node' ? nodeOf(graph, selection.key) : undefined;
  const references = (field: { key: string }) => conditionReferences(graph, `start.${field.key}`)
    .map(item => `排他网关「${item.node.name}」的条件：start.${field.key} ${operatorLabels[item.clause.operator]} ${Array.isArray(item.clause.value) ? item.clause.value.join(', ') : item.clause.value ?? ''}`);
  return <div className="wf-page wf-editor">
    <nav className="wf-crumbs"><Link to={workflowRoutes.definitions}>流程定义</Link> / {definition.definitionName}</nav>
    <div className="wf-page-head">
      <h1>{name || definition.definitionName}</h1>
      {viewingVersion ? <Tag color="green">v{viewingVersion} 只读</Tag> : definition.draftVersionNo ? <Tag color="blue">v{definition.draftVersionNo} 草稿</Tag> : <Tag>未建草稿（保存后从最新发布版本复制）</Tag>}
      {definition.currentVersionNo && <Tag color="green">当前发布 v{definition.currentVersionNo}</Tag>}
      {dirty && <span className="wf-warn-text">有未保存的修改</span>}
      <span className="wf-grow" />
      <Segmented value={tab} onChange={value => setTab(value as EditorTab)} options={[{ value: 'graph', label: '流程图' }, { value: 'form', label: '启动表单' }, { value: 'settings', label: '流程设置' }]} />
      {!readOnly && <Space>
        <Button danger={problems.length > 0} onClick={() => setShowProblems(value => !value)}>{problems.length ? `校验 · ${problems.length} 个问题` : '校验通过'}</Button>
        <Button loading={command.saving} onClick={() => void save()}>保存草稿</Button>
        <Button type="primary" loading={command.saving} onClick={() => void publish()}>发布{definition.draftVersionNo ? ` v${definition.draftVersionNo}` : ''}</Button>
      </Space>}
      {viewingVersion && <Link to={`${workflowRoutes.definitions}/${definition.definitionId}`}><Button>回到草稿</Button></Link>}
    </div>
    {command.conflict && <Alert type="warning" showIcon title="草稿已被他人保存或发布，你的修改未保存"
      description="可以先复制当前草稿 JSON，再重新加载最新内容后合并。" action={<Space>
        <Typography.Text copyable={{ text: JSON.stringify({ graph: normalizeGraph(graph), startForm }, null, 2), tooltips: ['复制草稿 JSON', '已复制'] }}>草稿 JSON</Typography.Text>
        <Button onClick={() => { setDirty(false); command.clearConflict(); onReload(); }}>重新加载</Button></Space>} />}
    {command.formError && <Alert type="error" showIcon title={command.formError} closable={{ onClose: command.clearError }} />}
    {tab === 'graph' && <div className="wf-editor-body">
      <aside className="wf-card wf-palette">
        <div className="wf-card-title">节点</div><p className="muted">拖到画布上添加，或点击添加</p>
        {palette.map(item => <button type="button" key={item.type} className="wf-palette-item" draggable={!readOnly} disabled={readOnly}
          onDragStart={event => { event.dataTransfer.setData(paletteMime, item.type); event.dataTransfer.effectAllowed = 'copy'; }}
          onClick={() => add(item.type)}><span className={`wf-palette-shape is-${item.type.toLowerCase()}`} />
          <span><strong>{nodeTypeLabels[item.type]}</strong><div className="muted">{item.hint}</div></span></button>)}
        <ul className="wf-plain-list muted"><li>开始节点固定一个</li><li>从节点右侧连接点拖出连线</li><li>选中连线可配置条件</li><li>Delete 删除 · ⌘Z / Ctrl+Z 撤销</li></ul>
      </aside>
      <div className="wf-editor-center">
        <WorkflowCanvas graph={graph} editable={!readOnly} selection={selection} onSelect={setSelection} decorate={decorate} variables={variablesFor}
          focusKey={focusKey} onConnect={connectNodes} onMove={(key, position) => change(moveNode(graph, key, position))} onDelete={deleteSelection}
          onDropNode={(type, position) => add(type, position)} height="calc(100vh - 330px)"
          toolbar={!readOnly && <Space size={4}><Button size="small" disabled={!history.past.length} onClick={() => step('undo')}>撤销</Button>
            <Button size="small" disabled={!history.future.length} onClick={() => step('redo')}>重做</Button>
            <Button size="small" onClick={() => change(autoLayout(graph))}>自动布局</Button></Space>} />
        {showProblems && problems.length > 0 && <section className="wf-problems">
          <div className="wf-problems-title">发布前需要解决 {problems.length} 个问题<Button type="text" size="small" onClick={() => setShowProblems(false)}>收起</Button></div>
          {problems.map((problem, index) => <div key={index} className="wf-problem">
            <Tag color="red">{problem.nodeKey ? nodeOf(graph, problem.nodeKey)?.name ?? problem.nodeKey : problem.edgeKey ?? '流程'}</Tag>
            <span className="wf-grow">{problem.message}{problem.code.startsWith('LOCAL_') && <span className="muted">（本地检查）</span>}</span>
            {(problem.nodeKey || problem.edgeKey || problem.code === 'STRUCTURE_INVALID' && problem.itemKey) && <Button type="link" size="small" onClick={() => locate(problem)}>定位 →</Button>}</div>)}
        </section>}
      </div>
      <aside className="wf-card wf-editor-side">
        {selection?.kind === 'edge' ? <EdgePanel key={selection.key} edgeKey={selection.key} context={context} />
          : selectedNode ? <NodePanel key={selectedNode.key} node={selectedNode} context={context} />
          : <Empty description="选择节点或连线查看属性" />}
      </aside>
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
    {readOnly && !viewingVersion && <Alert type="info" showIcon title="只读 · 没有“维护流程定义”权限" />}
    {viewingVersion && <Button type="link" onClick={() => void navigate(`${workflowRoutes.definitions}/${definition.definitionId}`)}>编辑草稿</Button>}
  </div>;
}
