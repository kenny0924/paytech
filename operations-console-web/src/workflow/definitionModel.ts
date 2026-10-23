import { clausesText, gatewayExits, nodeOf, publishedBaseVersionNo } from './graphModel.ts';
import type { ActionTemplate, DefinitionDetail, DefinitionSummary, FormDefinition, GraphNode, Problem, WorkflowGraph } from './workflowTypes.ts';
import { translator } from '../i18n/index.ts';

const t = translator('workflow');

// ---------- 流程定义列表（需求 D1，原型 09） ----------
export type CardStatusKind = 'INACTIVE' | 'PUBLISHED_WITH_DRAFT' | 'DRAFT_ONLY' | 'PUBLISHED';
export interface CardStatus { kind: CardStatusKind; label: string; tone: 'muted' | 'warn' | 'draft' | 'ok' }

/** 卡片只显示一个状态，按顺序取第一个成立的：已停用 > 已发布 vN · 有未发布修改 > 草稿 · 未发布 > 已发布 vN。 */
export function definitionCardStatus(summary: Pick<DefinitionSummary, 'status' | 'currentVersionNo' | 'draftVersionNo'>): CardStatus {
  if (summary.status === 'INACTIVE') return { kind: 'INACTIVE', label: t('definitions.card.inactive'), tone: 'muted' };
  if (summary.currentVersionNo && summary.draftVersionNo) {
    return { kind: 'PUBLISHED_WITH_DRAFT', label: t('definitions.card.publishedWithDraft', { version: summary.currentVersionNo }), tone: 'warn' };
  }
  if (!summary.currentVersionNo) return { kind: 'DRAFT_ONLY', label: t('definitions.card.draftOnly'), tone: 'draft' };
  return { kind: 'PUBLISHED', label: t('definitions.card.published', { version: summary.currentVersionNo }), tone: 'ok' };
}

/** 列表状态筛选：已发布含「有未发布修改」，草稿只含未发布过的流程。 */
export type DefinitionFilter = 'ALL' | 'PUBLISHED' | 'DRAFT' | 'INACTIVE';
export function filterBucket(summary: Pick<DefinitionSummary, 'status' | 'currentVersionNo' | 'draftVersionNo'>): Exclude<DefinitionFilter, 'ALL'> {
  const kind = definitionCardStatus(summary).kind;
  return kind === 'INACTIVE' ? 'INACTIVE' : kind === 'DRAFT_ONLY' ? 'DRAFT' : 'PUBLISHED';
}

/** 前端筛选与搜索（名称或编码），按最近修改倒序；同时给出各状态数量。 */
export function filterDefinitions(items: readonly DefinitionSummary[], filter: DefinitionFilter, keyword: string) {
  const text = keyword.trim().toLowerCase();
  const matched = items.filter(item => !text || item.definitionName.toLowerCase().includes(text) || item.definitionCode.toLowerCase().includes(text));
  const counts: Record<DefinitionFilter, number> = { ALL: matched.length, PUBLISHED: 0, DRAFT: 0, INACTIVE: 0 };
  for (const item of matched) counts[filterBucket(item)]++;
  const visible = matched.filter(item => filter === 'ALL' || filterBucket(item) === filter)
    .slice().sort((left, right) => Date.parse(right.updateTime) - Date.parse(left.updateTime));
  return { visible, counts };
}

export type CardAction = 'LAUNCH' | 'CONTINUE' | 'EDIT' | 'VIEW' | 'ENABLE';
export interface CardPermissions { canStart: boolean; canManage: boolean }

/** 每张卡一个主按钮：已发布 → 发起；只有草稿 → 继续编辑；已停用 → 启用。没有对应权限时退为编辑或查看。 */
export function primaryCardAction(summary: Pick<DefinitionSummary, 'status' | 'currentVersionNo' | 'draftVersionNo'>, permissions: CardPermissions): CardAction {
  const kind = definitionCardStatus(summary).kind;
  if (kind === 'INACTIVE') return permissions.canManage ? 'ENABLE' : 'VIEW';
  if (kind === 'DRAFT_ONLY') return permissions.canManage ? 'CONTINUE' : 'VIEW';
  if (permissions.canStart) return 'LAUNCH';
  return permissions.canManage ? 'EDIT' : 'VIEW';
}

export type CardMenuItem = 'EDIT' | 'VERSIONS' | 'DISABLE';
/** 「⋯」菜单：编辑 / 版本历史 / 停用（红色、最后、需确认）；主按钮已经是进入编辑器时不重复「编辑」。 */
export function cardMenuItems(summary: Pick<DefinitionSummary, 'status' | 'currentVersionNo' | 'draftVersionNo'>, permissions: CardPermissions): CardMenuItem[] {
  const primary = primaryCardAction(summary, permissions);
  const items: CardMenuItem[] = [];
  if (primary !== 'CONTINUE' && primary !== 'EDIT' && primary !== 'VIEW') items.push('EDIT');
  items.push('VERSIONS');
  if (permissions.canManage && summary.status === 'ACTIVE') items.push('DISABLE');
  return items;
}

/** 卡片日期「MM-DD」（非本年加年份）。 */
export function monthDay(value: string | null | undefined, now = new Date()): string {
  if (!value) return '—';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  const pad = (number: number) => String(number).padStart(2, '0');
  const day = `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return date.getFullYear() === now.getFullYear() ? day : `${date.getFullYear()}-${day}`;
}

// ---------- 缩略图 ----------
/**
 * 缩略图读哪一份：有发布版本时读当前发布版本（接口 12，发起的就是它）；只有草稿时读定义详情里的草稿图。每张卡一次请求。
 */
export type ThumbnailSource = { kind: 'VERSION'; versionNo: number } | { kind: 'DRAFT' };
export function thumbnailSource(summary: Pick<DefinitionSummary, 'currentVersionNo'>): ThumbnailSource {
  return summary.currentVersionNo ? { kind: 'VERSION', versionNo: summary.currentVersionNo } : { kind: 'DRAFT' };
}

/** 缩略图队列中的请求 ID：定义版本号变化（保存、发布）后重新读取。 */
export const thumbnailRequestId = (summary: Pick<DefinitionSummary, 'definitionId' | 'version' | 'currentVersionNo'>) =>
  `${summary.definitionId}:${summary.version}:${summary.currentVersionNo ?? 'draft'}`;

export interface ThumbnailFetchers {
  detail: (definitionId: string) => Promise<DefinitionDetail>;
  version: (definitionId: string, versionNo: number) => Promise<{ graph: WorkflowGraph }>;
}

/** 读取一张卡的缩略图；只有草稿但详情里没有草稿时退回最新发布版本；都没有时为 null。 */
export async function loadThumbnail(summary: Pick<DefinitionSummary, 'definitionId' | 'currentVersionNo'>, fetchers: ThumbnailFetchers): Promise<WorkflowGraph | null> {
  const source = thumbnailSource(summary);
  if (source.kind === 'VERSION') return (await fetchers.version(summary.definitionId, source.versionNo)).graph;
  const detail = await fetchers.detail(summary.definitionId);
  if (detail.draft) return detail.draft.graph;
  const base = publishedBaseVersionNo(detail);
  return base === undefined ? null : (await fetchers.version(summary.definitionId, base)).graph;
}

// ---------- 编辑器页头（需求 D2） ----------
/** 页头状态药丸。 */
export function editorStatusText(input: { currentVersionNo: number | null; hasDraft: boolean; dirty: boolean; viewingVersion?: number }): string {
  if (input.viewingVersion) return t('editor.status.readOnly', { version: input.viewingVersion });
  if (!input.currentVersionNo) return t('editor.status.new');
  if (input.hasDraft || input.dirty) return t('editor.status.draft', { version: input.currentVersionNo });
  return t('editor.status.same', { version: input.currentVersionNo });
}

/** 问题清单里的位置名：节点名、连线「A → B」、启动表单或「流程」。 */
export function problemTarget(problem: Problem, graph: WorkflowGraph): string {
  if (problem.edgeKey) {
    const edge = graph.edges.find(item => item.key === problem.edgeKey);
    if (edge) return `${nodeLabel(graph, edge.from)} → ${nodeLabel(graph, edge.to)}`;
  }
  if (problem.nodeKey) return nodeLabel(graph, problem.nodeKey);
  if (problem.code === 'STRUCTURE_INVALID' && problem.itemKey) return t('graph.startForm');
  return t('problems.process');
}
const nodeLabel = (graph: WorkflowGraph, key: string) => { const node = nodeOf(graph, key); return node?.name || node?.key || key; };

// ---------- 发布确认（原型 12） ----------
export interface VersionContent { graph: WorkflowGraph; startForm: FormDefinition }
export type ChangeKind = 'ADDED' | 'REMOVED' | 'CHANGED';
export interface VersionChange {
  kind: ChangeKind; area: 'GRAPH' | 'START_FORM'; action: string; subject: string;
  /** 「查看」定位：节点或连线（发布版本里已删除的没有）。 */
  nodeKey?: string; edgeKey?: string;
}

/**
 * 草稿与当前发布版本的变更摘要：节点增删 / 改名、连线增删与条件变化、启动表单字段增删、各节点动作增删、审批人变化。
 * templateName 把模板 ID 转为名称（草稿用动作目录，发布版本可用冻结快照）。
 */
export function versionChanges(base: VersionContent, draft: VersionContent, templateName: (templateId: string) => string): VersionChange[] {
  const changes: VersionChange[] = [];
  const baseNodes = new Map(base.graph.nodes.map(node => [node.key, node]));
  const draftNodes = new Map(draft.graph.nodes.map(node => [node.key, node]));
  for (const node of draft.graph.nodes) {
    const previous = baseNodes.get(node.key);
    if (!previous) changes.push({ kind: 'ADDED', area: 'GRAPH', action: t('publish.change.nodeAdded'), subject: node.name || node.key, nodeKey: node.key });
    else if ((previous.name || '') !== (node.name || '')) {
      changes.push({ kind: 'CHANGED', area: 'GRAPH', action: t('publish.change.nodeRenamed'), subject: `${previous.name || previous.key} → ${node.name || node.key}`, nodeKey: node.key });
    }
  }
  for (const node of base.graph.nodes) {
    if (!draftNodes.has(node.key)) changes.push({ kind: 'REMOVED', area: 'GRAPH', action: t('publish.change.nodeRemoved'), subject: node.name || node.key });
  }
  changes.push(...edgeChanges(base.graph, draft.graph, baseNodes, draftNodes));
  for (const node of draft.graph.nodes) {
    const previous = baseNodes.get(node.key);
    if (previous && node.type === 'TASK') changes.push(...taskChanges(previous, node, templateName));
  }
  const baseFields = new Map(base.startForm.fields.map(field => [field.key, field]));
  const draftFields = new Set(draft.startForm.fields.map(field => field.key));
  for (const field of draft.startForm.fields) {
    if (!baseFields.has(field.key)) changes.push({ kind: 'ADDED', area: 'START_FORM', action: t('publish.change.fieldAdded'), subject: field.label || field.key });
  }
  for (const field of base.startForm.fields) {
    if (!draftFields.has(field.key)) changes.push({ kind: 'REMOVED', area: 'START_FORM', action: t('publish.change.fieldRemoved'), subject: field.label || field.key });
  }
  return changes;
}

const pairOf = (edge: { from: string; to: string }) => `${edge.from}\u0000${edge.to}`;

/** 连线按「起点 → 终点」比较（插入节点会改 key）；只比较两端在两个版本都存在的连线，其余由节点增删表达。 */
function edgeChanges(base: WorkflowGraph, draft: WorkflowGraph, baseNodes: Map<string, GraphNode>, draftNodes: Map<string, GraphNode>): VersionChange[] {
  const changes: VersionChange[] = [];
  const kept = (edge: { from: string; to: string }) => baseNodes.has(edge.from) && baseNodes.has(edge.to) && draftNodes.has(edge.from) && draftNodes.has(edge.to);
  const baseEdges = new Map(base.edges.map(edge => [pairOf(edge), edge]));
  const draftPairs = new Set(draft.edges.map(pairOf));
  const name = (key: string) => { const node = draftNodes.get(key) ?? baseNodes.get(key); return node?.name || key; };
  for (const edge of draft.edges) {
    if (!kept(edge)) continue;
    const previous = baseEdges.get(pairOf(edge));
    if (!previous) { changes.push({ kind: 'ADDED', area: 'GRAPH', action: t('publish.change.edgeAdded'), subject: `${name(edge.from)} → ${name(edge.to)}`, edgeKey: edge.key }); continue; }
    if (draftNodes.get(edge.from)?.type !== 'EXCLUSIVE') continue;
    const before = exitText(base, previous.from, previous.key); const after = exitText(draft, edge.from, edge.key);
    if (before !== after) changes.push({ kind: 'CHANGED', area: 'GRAPH', action: t('publish.change.edgeCondition'),
      subject: t('publish.change.edgeConditionSubject', { from: name(edge.from), to: name(edge.to), condition: exitText(draft, edge.from, edge.key, false) }), edgeKey: edge.key });
  }
  for (const edge of base.edges) {
    if (kept(edge) && !draftPairs.has(pairOf(edge))) changes.push({ kind: 'REMOVED', area: 'GRAPH', action: t('publish.change.edgeRemoved'), subject: `${name(edge.from)} → ${name(edge.to)}` });
  }
  return changes;
}

/** 网关出口的文字：默认出口 / 条件；比较时带上优先级（调整优先级也算条件变化），展示时不带。 */
function exitText(graph: WorkflowGraph, gatewayKey: string, edgeKey: string, withPriority = true): string {
  const exit = gatewayExits(graph, gatewayKey).find(item => item.edge.key === edgeKey);
  if (!exit) return '—';
  if (exit.isDefault) return t('graph.defaultEdge');
  return `${withPriority && exit.priority ? t('publish.change.priority', { priority: exit.priority }) : ''}${clausesText(exit.clauses)}`;
}

function taskChanges(previous: GraphNode, node: GraphNode, templateName: (templateId: string) => string): VersionChange[] {
  const changes: VersionChange[] = [];
  const label = node.name || node.key;
  const before = (previous.actions ?? []).map(action => action.templateId);
  const after = (node.actions ?? []).map(action => action.templateId);
  for (const id of multisetMinus(after, before)) changes.push({ kind: 'ADDED', area: 'GRAPH', action: t('publish.change.actionAdded', { node: label }), subject: templateName(id), nodeKey: node.key });
  for (const id of multisetMinus(before, after)) changes.push({ kind: 'REMOVED', area: 'GRAPH', action: t('publish.change.actionRemoved', { node: label }), subject: templateName(id), nodeKey: node.key });
  const approversBefore = [...(previous.approval?.approverIds ?? [])].sort().join(',');
  const approversAfter = [...(node.approval?.approverIds ?? [])].sort().join(',');
  const modeChanged = (previous.approval?.approverIds.length ? previous.approval.mode : '') !== (node.approval?.approverIds.length ? node.approval.mode : '');
  if (approversBefore !== approversAfter || modeChanged) changes.push({ kind: 'CHANGED', area: 'GRAPH', action: t('publish.change.approvers'), subject: label, nodeKey: node.key });
  return changes;
}

function multisetMinus(left: string[], right: string[]): string[] {
  const rest = [...right];
  return left.filter(item => { const index = rest.indexOf(item); if (index < 0) return true; rest.splice(index, 1); return false; });
}

/**
 * 发布确认里的运行中实例提示：读取失败（含 403 没有实例查看权限）或还没有发布版本时不显示；没有运行中的实例时也不显示。
 * 计数是该流程全部运行中实例（不分版本）：只有 v1 时都按 v1 运行，否则各按发起时的版本运行。
 */
export function runningInstanceNote(count: number | null, currentVersionNo: number | null, nextVersionNo: number): string | null {
  if (count === null || !currentVersionNo || count <= 0) return null;
  return currentVersionNo === 1 ? t('publish.runningNoteV1', { count, next: nextVersionNo })
    : t('publish.runningNote', { count, next: nextVersionNo });
}

/** 节点动作卡上的说明首行（原型 16）：上传 / 页面任务 / 人工确认取执行说明，表单取动作描述；只取第一行。 */
export function instructionLine(template: Pick<ActionTemplate, 'actionType' | 'config' | 'description'>): string {
  const config = template.config as { instruction?: string } | null;
  const text = template.actionType !== 'FORM' && config?.instruction?.trim() ? config.instruction : template.description ?? '';
  return text.trim().split(/\r?\n/)[0]?.trim() ?? '';
}
