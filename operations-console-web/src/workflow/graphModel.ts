import type {
  ActionTemplate, ConditionClause, DefinitionDetail, EdgeCondition, FieldType, FormDefinition, GraphEdge, GraphNode, NodeType, Problem, WorkflowGraph,
} from './workflowTypes.ts';
import { operatorLabels } from './workflowLabels.ts';

/** 节点、连线、动作 key 的格式（与后端 Pattern 一致）。 */
export const keyPattern = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;
export const graphLimits = { nodes: 100, actionsPerNode: 20, formFields: 50, graphBytes: 48 * 1024, startFormBytes: 12 * 1024 };

/** JSON 序列化后的 UTF-8 字节数（与后端 graph_json / start_form_json 上限对应）。 */
export const jsonBytes = (value: unknown) => new TextEncoder().encode(JSON.stringify(value)).length;

const defaultNames: Record<NodeType, string> = {
  START: '开始', END: '结束', TASK: '新任务', EXCLUSIVE: '条件判断', PARALLEL_SPLIT: '并行分叉', PARALLEL_JOIN: '并行汇合',
};
const keyPrefixes: Record<NodeType, string> = {
  START: 'start', END: 'end', TASK: 'task', EXCLUSIVE: 'gateway', PARALLEL_SPLIT: 'fork', PARALLEL_JOIN: 'join',
};

/**
 * 定义详情（接口 7）的版本列表不含流程图。没有草稿时，编辑器底稿与卡片缩略图取当前发布版本（缺省为最新发布版本），
 * 需按接口 12 读取；有草稿时直接用草稿，返回 undefined。
 */
export function publishedBaseVersionNo(detail: DefinitionDetail): number | undefined {
  if (detail.draft) return undefined;
  if (detail.definition.currentVersionNo) return detail.definition.currentVersionNo;
  const published = detail.versions.filter(item => item.status === 'PUBLISHED').map(item => item.versionNo);
  return published.length ? Math.max(...published) : undefined;
}

/** 新流程：一个开始、一个结束（与 API 8 新建草稿一致）。 */
export function newGraph(): WorkflowGraph {
  return { nodes: [
    { key: 'start', type: 'START', name: '开始', position: { x: 0, y: 0 } },
    { key: 'end', type: 'END', name: '结束', position: { x: 440, y: 0 } },
  ], edges: [] };
}

/** 一个版本内节点、连线和动作 key 全局唯一（3.1）。 */
export function usedKeys(graph: WorkflowGraph): Set<string> {
  return new Set([...graph.nodes.map(node => node.key), ...graph.edges.map(edge => edge.key),
    ...graph.nodes.flatMap(node => node.actions?.map(action => action.key) ?? [])]);
}

export function nextKey(graph: WorkflowGraph, prefix: string): string {
  const used = usedKeys(graph);
  for (let index = 1; ; index++) if (!used.has(`${prefix}${index}`)) return `${prefix}${index}`;
}

export const outgoing = (graph: WorkflowGraph, key: string) => graph.edges.filter(edge => edge.from === key);
export const incoming = (graph: WorkflowGraph, key: string) => graph.edges.filter(edge => edge.to === key);
export const nodeOf = (graph: WorkflowGraph, key: string) => graph.nodes.find(node => node.key === key);

export function addNode(graph: WorkflowGraph, type: NodeType, position: { x: number; y: number }): { graph: WorkflowGraph; key: string } {
  const key = nextKey(graph, keyPrefixes[type]);
  const node: GraphNode = { key, type, name: defaultNames[type], position: { x: Math.round(position.x), y: Math.round(position.y) },
    ...(type === 'TASK' ? { actions: [], notifications: [] } : {}), ...(type === 'EXCLUSIVE' ? { conditions: [] } : {}) };
  return { graph: { ...graph, nodes: [...graph.nodes, node] }, key };
}

/** to 是否能沿连线到达 from（新增 from→to 会成环）。 */
export function reaches(graph: WorkflowGraph, from: string, to: string): boolean {
  const seen = new Set<string>(); const queue = [from];
  while (queue.length) {
    const current = queue.shift()!;
    if (current === to) return true;
    if (seen.has(current)) continue;
    seen.add(current);
    queue.push(...outgoing(graph, current).map(edge => edge.to));
  }
  return false;
}

const singleOutgoing: NodeType[] = ['START', 'TASK', 'PARALLEL_JOIN'];

/**
 * 画布连线规则：不连自己、开始没有入边、结束没有出边、不重复、不成环；开始 / 任务 / 汇合只有一条出边。
 * 排他网关的第一条出边自动成为默认出口，之后的出边各带一个空条件组（按添加顺序为优先级）。
 */
export function connect(graph: WorkflowGraph, from: string, to: string): { graph: WorkflowGraph; edgeKey: string } | { error: string } {
  const source = nodeOf(graph, from); const target = nodeOf(graph, to);
  if (!source || !target) return { error: '节点不存在' };
  if (from === to) return { error: '不能连到自身' };
  if (target.type === 'START') return { error: '开始节点不能有入边' };
  if (source.type === 'END') return { error: '结束节点不能有出边' };
  if (graph.edges.some(edge => edge.from === from && edge.to === to)) return { error: '这两个节点之间已有连线' };
  if (singleOutgoing.includes(source.type) && outgoing(graph, from).length) return { error: `${source.name || source.key} 只能有一条出边，请先删除原连线` };
  if (reaches(graph, to, from)) return { error: '流程不能有环；驳回由运行时规则处理，不需要画回边' };
  const edgeKey = nextKey(graph, 'e');
  const edges = [...graph.edges, { key: edgeKey, from, to }];
  const nodes = source.type !== 'EXCLUSIVE' ? graph.nodes : graph.nodes.map(node => node.key !== from ? node
    : node.defaultEdgeKey ? { ...node, conditions: [...(node.conditions ?? []), { edgeKey, all: [] }] }
    : { ...node, defaultEdgeKey: edgeKey });
  return { graph: { ...graph, nodes, edges }, edgeKey };
}

/** 去掉网关里对已删连线的引用。 */
function dropEdgeReferences(nodes: GraphNode[], removed: Set<string>): GraphNode[] {
  return nodes.map(node => node.type !== 'EXCLUSIVE' ? node : {
    ...node, conditions: (node.conditions ?? []).filter(condition => !removed.has(condition.edgeKey)),
    defaultEdgeKey: node.defaultEdgeKey && removed.has(node.defaultEdgeKey) ? undefined : node.defaultEdgeKey,
  });
}

export function removeEdge(graph: WorkflowGraph, edgeKey: string): WorkflowGraph {
  return { ...graph, edges: graph.edges.filter(edge => edge.key !== edgeKey), nodes: dropEdgeReferences(graph.nodes, new Set([edgeKey])) };
}

/** 删除节点及其连线；开始节点固定一个，不能删除。 */
export function removeNode(graph: WorkflowGraph, key: string): WorkflowGraph {
  const node = nodeOf(graph, key);
  if (!node || node.type === 'START') return graph;
  const removed = new Set(graph.edges.filter(edge => edge.from === key || edge.to === key).map(edge => edge.key));
  return { ...graph, nodes: dropEdgeReferences(graph.nodes.filter(item => item.key !== key), removed),
    edges: graph.edges.filter(edge => !removed.has(edge.key)) };
}

/** 可在连线上插入的节点类型（开始唯一、结束没有出边，均不可插入）。 */
export const insertableTypes: NodeType[] = ['TASK', 'EXCLUSIVE', 'PARALLEL_SPLIT', 'PARALLEL_JOIN'];
/**
 * 插入节点的水平间距：前一节点 → 新节点、新节点 → 后一节点（左边缘之间）都至少留这么多，
 * 不够时把后一节点及其右侧节点整体右移，避免新连线出现很短的折弯（stage-5 验收）。
 */
export const insertSpacing = 280;
/** 各类节点连接点相对节点左上角的纵向位置（与 workflow.css 中的 Handle 位置一致），用于让新节点与前一节点的连接点对齐。 */
const handleOffsetY: Record<NodeType, number> = { START: 17, END: 17, TASK: 26, EXCLUSIVE: 25, PARALLEL_SPLIT: 25, PARALLEL_JOIN: 25 };
/** 两端连接点纵向差小于该值视为同一行：新节点与前一节点对齐成直线，否则放在两端中间。 */
const sameRowTolerance = 40;

/**
 * 在连线上插入节点（需求 D3）：A→B 拆成 A→新节点→B。原连线保留 key 并改指向新节点，因此原连线上的条件 / 默认出口留在前一段；
 * 新节点→B 是新连线、不带条件（新节点是排他网关时它成为默认出口）。
 * 位置：B 在 A 右侧时，新节点放在中点，但离 A、B 都不少于 insertSpacing；B 太近则把 B 及其右侧节点右移到够用为止。
 * 同一行时新节点的连接点与 A 对齐，避免细小折弯；B 不在 A 右侧（回绕、竖排）时只取中点、不移动其他节点。
 */
export function insertOnEdge(graph: WorkflowGraph, edgeKey: string, type: NodeType): { graph: WorkflowGraph; key: string } | { error: string } {
  const edge = graph.edges.find(item => item.key === edgeKey);
  if (!edge) return { error: '连线不存在' };
  if (!insertableTypes.includes(type)) return { error: '连线上只能插入任务节点或网关' };
  const source = nodeOf(graph, edge.from); const target = nodeOf(graph, edge.to);
  if (!source || !target) return { error: '连线的端点不存在' };
  const gap = target.position.x - source.position.x;
  const shift = gap > 0 ? Math.max(0, 2 * insertSpacing - gap) : 0;
  const moved = shift ? graph.nodes.map(node => node.position.x >= target.position.x && node.key !== source.key
    ? { ...node, position: { ...node.position, x: node.position.x + shift } } : node) : graph.nodes;
  const x = gap > 0 ? source.position.x + (gap + shift) / 2 : (source.position.x + target.position.x) / 2;
  const sourceHandle = source.position.y + handleOffsetY[source.type];
  const targetHandle = target.position.y + handleOffsetY[target.type];
  const y = Math.abs(sourceHandle - targetHandle) < sameRowTolerance ? sourceHandle - handleOffsetY[type]
    : (source.position.y + target.position.y) / 2;
  const added = addNode({ ...graph, nodes: moved }, type, { x, y });
  const nextEdgeKey = nextKey(added.graph, 'e');
  const nodes = added.graph.nodes.map(node => node.key === added.key && type === 'EXCLUSIVE' ? { ...node, defaultEdgeKey: nextEdgeKey } : node);
  const edges = graph.edges.flatMap(item => item.key !== edgeKey ? [item]
    : [{ ...item, to: added.key }, { key: nextEdgeKey, from: added.key, to: edge.to }]);
  return { graph: { ...graph, nodes, edges }, key: added.key };
}

export function updateNode(graph: WorkflowGraph, key: string, patch: Partial<GraphNode>): WorkflowGraph {
  return { ...graph, nodes: graph.nodes.map(node => node.key === key ? { ...node, ...patch } : node) };
}

/** 修改节点 key：同步连线两端；格式或重复时返回错误。 */
export function renameNode(graph: WorkflowGraph, key: string, next: string): { graph: WorkflowGraph } | { error: string } {
  if (key === next) return { graph };
  if (!keyPattern.test(next)) return { error: 'key 以字母开头，只能包含字母、数字和下划线，最长 64 位' };
  if (usedKeys(graph).has(next)) return { error: `key ${next} 已被使用` };
  return { graph: { ...graph,
    nodes: graph.nodes.map(node => node.key === key ? { ...node, key: next } : node),
    edges: graph.edges.map(edge => ({ ...edge, from: edge.from === key ? next : edge.from, to: edge.to === key ? next : edge.to })) } };
}

export function moveNode(graph: WorkflowGraph, key: string, position: { x: number; y: number }): WorkflowGraph {
  return updateNode(graph, key, { position: { x: Math.round(position.x), y: Math.round(position.y) } });
}

// ---------- 排他网关的出口 ----------
export interface ExitInfo { edge: GraphEdge; priority: number | null; isDefault: boolean; clauses: ConditionClause[] }

/** 网关出口按优先级排列，默认出口在最后。 */
export function gatewayExits(graph: WorkflowGraph, gatewayKey: string): ExitInfo[] {
  const gateway = nodeOf(graph, gatewayKey);
  if (!gateway) return [];
  const edges = outgoing(graph, gatewayKey);
  const conditions = gateway.conditions ?? [];
  const ordered = conditions.flatMap<ExitInfo>((condition, index) => {
    const edge = edges.find(item => item.key === condition.edgeKey);
    return edge ? [{ edge, priority: index + 1, isDefault: false, clauses: condition.all }] : [];
  });
  const loose = edges.filter(edge => edge.key !== gateway.defaultEdgeKey && !conditions.some(item => item.edgeKey === edge.key))
    .map<ExitInfo>(edge => ({ edge, priority: null, isDefault: false, clauses: [] }));
  const fallback = edges.filter(edge => edge.key === gateway.defaultEdgeKey).map<ExitInfo>(edge => ({ edge, priority: null, isDefault: true, clauses: [] }));
  return [...ordered, ...loose, ...fallback];
}

function patchConditions(graph: WorkflowGraph, gatewayKey: string, change: (conditions: EdgeCondition[], node: GraphNode) => Partial<GraphNode>) {
  const gateway = nodeOf(graph, gatewayKey);
  return gateway ? updateNode(graph, gatewayKey, change(gateway.conditions ?? [], gateway)) : graph;
}

export function setEdgeClauses(graph: WorkflowGraph, gatewayKey: string, edgeKey: string, clauses: ConditionClause[]): WorkflowGraph {
  return patchConditions(graph, gatewayKey, conditions => ({
    conditions: conditions.some(item => item.edgeKey === edgeKey)
      ? conditions.map(item => item.edgeKey === edgeKey ? { ...item, all: clauses } : item)
      : [...conditions, { edgeKey, all: clauses }],
  }));
}

/** 设为默认出口：原默认出口转为空条件组排在最后；默认出口不带条件。 */
export function setDefaultExit(graph: WorkflowGraph, gatewayKey: string, edgeKey: string): WorkflowGraph {
  return patchConditions(graph, gatewayKey, (conditions, node) => ({
    defaultEdgeKey: edgeKey,
    conditions: [...conditions.filter(item => item.edgeKey !== edgeKey),
      ...(node.defaultEdgeKey && node.defaultEdgeKey !== edgeKey ? [{ edgeKey: node.defaultEdgeKey, all: [] }] : [])],
  }));
}

/** 调整条件出口的优先级（1 开始）。 */
export function setExitPriority(graph: WorkflowGraph, gatewayKey: string, edgeKey: string, priority: number): WorkflowGraph {
  return patchConditions(graph, gatewayKey, conditions => {
    const current = conditions.find(item => item.edgeKey === edgeKey);
    if (!current) return {};
    const rest = conditions.filter(item => item.edgeKey !== edgeKey);
    const index = Math.max(0, Math.min(rest.length, priority - 1));
    return { conditions: [...rest.slice(0, index), current, ...rest.slice(index)] };
  });
}

// ---------- 变量 ----------
export interface VariableInfo { name: string; label: string; type: FieldType; options?: { value: string; label: string }[]; source: string }

/** 节点的全部上游节点（沿入边反向）。 */
export function upstream(graph: WorkflowGraph, key: string): Set<string> {
  const result = new Set<string>(); const queue = incoming(graph, key).map(edge => edge.from);
  while (queue.length) {
    const current = queue.shift()!;
    if (result.has(current)) continue;
    result.add(current);
    queue.push(...incoming(graph, current).map(edge => edge.from));
  }
  return result;
}

/** 一个动作模板的输出变量（3.3）。 */
export function actionOutputs(actionKey: string, template: Pick<ActionTemplate, 'actionType' | 'config' | 'templateName'> | undefined): VariableInfo[] {
  if (!template) return [];
  const source = template.templateName;
  switch (template.actionType) {
    case 'FORM': return ((template.config as FormDefinition | null)?.fields ?? []).map(field => ({
      name: `${actionKey}.${field.key}`, label: field.label, type: field.type, options: field.rules?.options, source }));
    case 'FILE_UPLOAD': return [{ name: `${actionKey}.files`, label: '上传的文件', type: 'FILE', source }];
    case 'PAGE_TASK': case 'MANUAL_CONFIRM': return [{ name: `${actionKey}.note`, label: '确认说明', type: 'TEXT', source }];
    default: return [];
  }
}

/**
 * 某节点可引用的变量：启动表单字段 + 上游任务节点的动作输出。
 * 上游按连线反向计算，并行分支里的另一分支不在上游中，汇合之后才可见（3.4 第 7 项）。
 */
export function variableCatalog(graph: WorkflowGraph, startForm: FormDefinition, templates: Map<string, ActionTemplate>, nodeKey: string): VariableInfo[] {
  const start = startForm.fields.map(field => ({ name: `start.${field.key}`, label: field.label, type: field.type,
    options: field.rules?.options, source: '启动表单' }));
  const above = upstream(graph, nodeKey);
  const outputs = graph.nodes.filter(node => node.type === 'TASK' && above.has(node.key))
    .flatMap(node => (node.actions ?? []).flatMap(action => actionOutputs(action.key, templates.get(action.templateId))));
  return [...start, ...outputs];
}

/** 每个比较符适用的字段类型（与后端 ConditionOperator 一致；文件只允许判空）。 */
export const operatorsByType: Record<FieldType, ConditionClause['operator'][]> = {
  TEXT: ['EQ', 'NE', 'IN', 'CONTAINS', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  LONG_TEXT: ['EQ', 'NE', 'CONTAINS', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  NUMBER: ['EQ', 'NE', 'IN', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  BOOLEAN: ['EQ', 'NE', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  ENUM: ['EQ', 'NE', 'IN', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  DATE: ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  FILE: ['IS_EMPTY', 'IS_NOT_EMPTY'],
};
export const valueShape = (operator: ConditionClause['operator']): 'NONE' | 'SCALAR' | 'LIST' =>
  operator === 'IS_EMPTY' || operator === 'IS_NOT_EMPTY' ? 'NONE' : operator === 'IN' ? 'LIST' : 'SCALAR';

/** 换比较符时按值形态转换已填的值。 */
export function changeOperator(clause: ConditionClause, operator: ConditionClause['operator']): ConditionClause {
  const shape = valueShape(operator);
  if (shape === 'NONE') return { variable: clause.variable, operator };
  const values = Array.isArray(clause.value) ? clause.value : clause.value ? [clause.value] : [];
  return { variable: clause.variable, operator, value: shape === 'LIST' ? values : values[0] ?? '' };
}

/** 换变量时保留仍适用的比较符，否则取该类型的第一个。 */
export function changeVariable(clause: ConditionClause, variable: VariableInfo): ConditionClause {
  const allowed = operatorsByType[variable.type];
  const operator = allowed.includes(clause.operator) ? clause.operator : allowed[0];
  return changeOperator({ variable: variable.name, operator, value: clause.value }, operator);
}

/** 条件文字：“支付方式 包含 APPLE_PAY 且 国家 属于 SA, AE”。 */
export function clausesText(clauses: ConditionClause[], variables: VariableInfo[] = []): string {
  if (!clauses.length) return '条件待填';
  return clauses.map(clause => {
    const label = variables.find(item => item.name === clause.variable)?.label ?? clause.variable;
    const value = Array.isArray(clause.value) ? clause.value.join(', ') : clause.value ?? '';
    return `${label} ${operatorLabels[clause.operator]}${value ? ` ${value}` : ''}`;
  }).join(' 且 ');
}

/** 被条件引用的启动表单字段与枚举值（启动表单设计器提示用）。 */
export function conditionReferences(graph: WorkflowGraph, variable: string): { node: GraphNode; clause: ConditionClause }[] {
  return graph.nodes.flatMap(node => (node.conditions ?? []).flatMap(condition => condition.all
    .filter(clause => clause.variable === variable).map(clause => ({ node, clause }))));
}

// ---------- 布局 ----------
/** 自动布局：按从开始的最长路径分层，从左到右排列，同层上下排开。 */
export function autoLayout(graph: WorkflowGraph): WorkflowGraph {
  const layer = new Map<string, number>();
  const start = graph.nodes.find(node => node.type === 'START');
  const order = topologicalOrder(graph);
  for (const key of order) {
    const parents = incoming(graph, key).map(edge => layer.get(edge.from) ?? 0);
    layer.set(key, key === start?.key || !parents.length ? 0 : Math.max(...parents) + 1);
  }
  const rows = new Map<number, number>();
  const positions = new Map<string, { x: number; y: number }>();
  for (const key of order) {
    const column = layer.get(key) ?? 0; const row = rows.get(column) ?? 0; rows.set(column, row + 1);
    positions.set(key, { x: column * 240, y: row * 130 });
  }
  for (const [column, count] of rows) {
    for (const [key, position] of positions) if ((layer.get(key) ?? 0) === column) position.y -= (count - 1) * 65;
  }
  return { ...graph, nodes: graph.nodes.map(node => ({ ...node, position: positions.get(node.key) ?? node.position })) };
}

/** 拓扑序；成环的节点追加在最后（不会死循环）。 */
export function topologicalOrder(graph: WorkflowGraph): string[] {
  const degree = new Map(graph.nodes.map(node => [node.key, incoming(graph, node.key).length]));
  const queue = graph.nodes.filter(node => degree.get(node.key) === 0).map(node => node.key);
  const result: string[] = [];
  while (queue.length) {
    const key = queue.shift()!; result.push(key);
    for (const edge of outgoing(graph, key)) {
      const next = (degree.get(edge.to) ?? 0) - 1; degree.set(edge.to, next);
      if (next === 0) queue.push(edge.to);
    }
  }
  return [...result, ...graph.nodes.map(node => node.key).filter(key => !result.includes(key))];
}

// ---------- 本地检查 ----------
/**
 * 编辑时的实时检查（需求 D2）：问题码与说明尽量与 Platform 草稿校验（WorkflowGraphValidator）一致，便于保存后无缝替换；
 * 只做不依赖服务端数据的部分（账号是否 ACTIVE、模板是否存在等仍以保存后的服务端结果为准）。
 */
export function localProblems(graph: WorkflowGraph, templates: Map<string, ActionTemplate>, startForm?: FormDefinition): Problem[] {
  const problems: Problem[] = [];
  const add = (code: string, message: string, nodeKey: string | null = null, edgeKey: string | null = null, itemKey: string | null = null) =>
    problems.push({ code, message, nodeKey, edgeKey, itemKey });
  checkShape(graph, startForm, add);
  checkReachability(graph, add);
  for (const node of graph.nodes) checkNode(graph, node, templates, add);
  checkParallelBlocks(graph, add);
  if (startForm) checkVariables(graph, startForm, templates, add);
  return problems;
}

type AddProblem = (code: string, message: string, nodeKey?: string | null, edgeKey?: string | null, itemKey?: string | null) => void;

function checkShape(graph: WorkflowGraph, startForm: FormDefinition | undefined, add: AddProblem) {
  const starts = graph.nodes.filter(node => node.type === 'START');
  if (starts.length !== 1) add('START_COUNT', `开始节点必须恰好一个，当前为 ${starts.length} 个`);
  if (!graph.nodes.some(node => node.type === 'END')) add('END_MISSING', '至少需要一个结束节点');
  if (graph.nodes.length > graphLimits.nodes) add('TOO_MANY_NODES', `节点有 ${graph.nodes.length} 个，超过上限 ${graphLimits.nodes} 个`);
  const graphSize = jsonBytes(normalizeGraph(graph));
  if (graphSize > graphLimits.graphBytes) add('GRAPH_TOO_LARGE', `流程图配置 ${(graphSize / 1024).toFixed(1)} KiB，超过 48 KiB 上限`);
  if (startForm) {
    const formSize = jsonBytes(startForm);
    if (formSize > graphLimits.startFormBytes) add('START_FORM_TOO_LARGE', `启动表单配置 ${(formSize / 1024).toFixed(1)} KiB，超过 12 KiB 上限`);
  }
  const seen = new Set<string>();
  for (const node of graph.nodes) {
    if (seen.has(node.key)) add('NODE_KEY_DUPLICATE', `节点 key 重复：${node.key}`, node.key);
    seen.add(node.key);
  }
  const actionKeys = new Set<string>();
  for (const node of graph.nodes) for (const action of node.actions ?? []) {
    if (actionKeys.has(action.key) || seen.has(action.key)) add('ACTION_KEY_DUPLICATE', `动作 key 在流程内重复：${action.key}`, node.key, null, action.key);
    actionKeys.add(action.key);
  }
}

/** 与服务端同口径：每个节点从开始可达、且能到达某个结束节点。 */
function checkReachability(graph: WorkflowGraph, add: AddProblem) {
  const starts = graph.nodes.filter(node => node.type === 'START');
  const ends = graph.nodes.filter(node => node.type === 'END');
  if (starts.length === 1) {
    const fromStart = reachable(starts[0].key, edge => edge.to, key => outgoing(graph, key));
    graph.nodes.filter(node => !fromStart.has(node.key)).forEach(node => add('NODE_UNREACHABLE', '节点从开始节点不可达', node.key));
  }
  if (ends.length) {
    const toEnd = new Set<string>();
    for (const end of ends) for (const key of reachable(end.key, edge => edge.from, key => incoming(graph, key))) toEnd.add(key);
    graph.nodes.filter(node => !toEnd.has(node.key)).forEach(node => add('NODE_CANNOT_REACH_END', '节点不能到达任何结束节点', node.key));
  }
}

function checkNode(graph: WorkflowGraph, node: GraphNode, templates: Map<string, ActionTemplate>, add: AddProblem) {
  const out = outgoing(graph, node.key).length; const into = incoming(graph, node.key).length;
  if ((node.type === 'TASK' || node.type === 'EXCLUSIVE') && !node.name.trim()) add('NODE_NAME_MISSING', '任务节点与排他网关必须填写名称', node.key);
  switch (node.type) {
    case 'START':
      if (into) add('START_HAS_INCOMING', '开始节点不能有入边', node.key);
      if (out !== 1) add('START_OUTGOING', '开始节点必须恰好有一条出边', node.key);
      break;
    case 'END':
      if (out) add('END_HAS_OUTGOING', '结束节点不能有出边', node.key);
      break;
    case 'TASK':
      if (out !== 1) add('TASK_OUTGOING', '任务节点必须恰好有一条出边', node.key);
      checkTask(node, templates, add);
      break;
    case 'EXCLUSIVE':
      if (out < 2) add('EXCLUSIVE_OUTGOING', '排他网关至少需要两条出边', node.key);
      checkExclusive(graph, node, add);
      break;
    case 'PARALLEL_SPLIT':
      if (out < 2) add('PARALLEL_SPLIT_OUTGOING', '并行分叉至少需要两条出边', node.key);
      break;
    case 'PARALLEL_JOIN':
      if (into < 2) add('PARALLEL_JOIN_INCOMING', '并行汇合至少需要两条入边', node.key);
      if (out !== 1) add('PARALLEL_JOIN_OUTGOING', '并行汇合必须恰好有一条出边', node.key);
      break;
  }
}

function checkTask(node: GraphNode, templates: Map<string, ActionTemplate>, add: AddProblem) {
  const actions = node.actions ?? [];
  const approvers = node.approval?.approverIds ?? [];
  if (!actions.length && !approvers.length) add('TASK_EMPTY', '任务节点至少需要一个审批人或一个动作', node.key);
  if (actions.length > graphLimits.actionsPerNode) add('TOO_MANY_ACTIONS', `节点有 ${actions.length} 个动作，超过上限 ${graphLimits.actionsPerNode} 个`, node.key);
  const seen = new Set<string>();
  for (const id of approvers) {
    if (seen.has(id)) add('APPROVER_DUPLICATE', `审批人重复：${id}`, node.key, null, id);
    seen.add(id);
  }
  for (const action of actions) {
    const template = templates.get(action.templateId);
    if (!action.templateId) add('TEMPLATE_NOT_FOUND', '动作未选择动作模板', node.key, null, action.key);
    else if (template?.actionType === 'DATA_QUERY') add('TEMPLATE_RESERVED_TYPE', '数据查询动作本期未开放，不能被发布的流程引用', node.key, null, action.key);
    else if (template && template.status !== 'ACTIVE') add('TEMPLATE_INACTIVE', `动作引用的模板已停用：${template.templateName}`, node.key, null, action.key);
    if (!action.executorId) add('EXECUTOR_MISSING', '动作必须指定默认执行人', node.key, null, action.key);
  }
  (node.notifications ?? []).forEach((rule, index) => {
    if (rule.trigger === 'VERIFICATION_FAILED') {
      add('NOTIFICATION_TRIGGER_INVALID', '「动作验证未通过」已停用（动作不再验证），请删除该通知规则', node.key, null, `notifications[${index}]`);
    }
  });
}

function checkExclusive(graph: WorkflowGraph, node: GraphNode, add: AddProblem) {
  const exits = outgoing(graph, node.key).map(edge => edge.key);
  if (!node.defaultEdgeKey) add('EXCLUSIVE_DEFAULT_MISSING', '排他网关必须指定一条默认出边', node.key);
  else if (!exits.includes(node.defaultEdgeKey)) add('EXCLUSIVE_DEFAULT_INVALID', `默认出边 ${node.defaultEdgeKey} 不是本网关的出边`, node.key, node.defaultEdgeKey);
  const conditioned = new Set<string>();
  for (const condition of node.conditions ?? []) {
    if (!exits.includes(condition.edgeKey)) continue;
    conditioned.add(condition.edgeKey);
    if (!condition.all.length) add('CONDITION_EMPTY', `出边 ${condition.edgeKey} 的条件组没有任何子句`, node.key, condition.edgeKey);
  }
  for (const edgeKey of exits) {
    if (!conditioned.has(edgeKey) && edgeKey !== node.defaultEdgeKey) {
      add('EXCLUSIVE_EDGE_UNUSED', `出边 ${edgeKey} 既没有条件也不是默认边，永远不会被选中`, node.key, edgeKey);
    }
  }
}

/**
 * 并行块配对（服务端 ParallelBlockRules 的简化版）：每个分叉的全部分支须汇入同一个汇合；分支内不能再分叉、不能有结束节点；
 * 每个汇合恰好与一个分叉成对。
 */
function checkParallelBlocks(graph: WorkflowGraph, add: AddProblem) {
  const pairings = new Map<string, number>();
  for (const split of graph.nodes.filter(node => node.type === 'PARALLEL_SPLIT')) {
    const joins = new Set<string>(); let everyBranchJoins = true; let blocked = false;
    for (const edge of outgoing(graph, split.key)) {
      const branch = exploreBranch(graph, edge.to);
      if (nodeOf(graph, edge.to)?.type === 'PARALLEL_JOIN') add('PARALLEL_BRANCH_EMPTY', `并行分支 ${edge.key} 中没有任何节点`, split.key, edge.key);
      branch.nested.forEach(inner => add('PARALLEL_NESTED', `不允许嵌套并行：该分叉位于并行分叉 ${split.key} 的分支内，可改为串联多个并行块`, inner));
      branch.ends.forEach(end => add('PARALLEL_END_INSIDE', `结束节点不能位于并行分叉 ${split.key} 的分支内`, end));
      if (branch.nested.length || branch.ends.length) blocked = true;
      if (!branch.joins.size) everyBranchJoins = false;
      branch.joins.forEach(join => joins.add(join));
    }
    if (blocked) continue;
    if (joins.size !== 1 || !everyBranchJoins) {
      add('PARALLEL_UNPAIRED', `并行分叉的全部分支必须汇入同一个并行汇合，当前汇入 [${[...joins].join(', ')}]`, split.key);
      continue;
    }
    const join = [...joins][0];
    pairings.set(join, (pairings.get(join) ?? 0) + 1);
  }
  for (const join of graph.nodes.filter(node => node.type === 'PARALLEL_JOIN')) {
    const count = pairings.get(join.key) ?? 0;
    if (count !== 1) add('PARALLEL_UNPAIRED', `并行汇合必须恰好与一个并行分叉成对，当前与 ${count} 个分叉成对`, join.key);
  }
}

/** 从分支入口沿出边前进，遇到汇合停止；记录到达的汇合、结束节点与嵌套分叉。 */
function exploreBranch(graph: WorkflowGraph, entry: string): { joins: Set<string>; ends: string[]; nested: string[] } {
  const joins = new Set<string>(); const ends: string[] = []; const nested: string[] = [];
  const seen = new Set<string>(); const queue = [entry];
  while (queue.length) {
    const key = queue.shift()!;
    if (seen.has(key)) continue;
    seen.add(key);
    const type = nodeOf(graph, key)?.type;
    if (type === 'PARALLEL_JOIN') { joins.add(key); continue; }
    if (type === 'END') { ends.push(key); continue; }
    if (type === 'PARALLEL_SPLIT') { nested.push(key); continue; }
    queue.push(...outgoing(graph, key).map(edge => edge.to));
  }
  return { joins, ends, nested };
}

/**
 * 条件与输入映射引用的变量须在该节点可用（启动表单或上游动作输出）。模板详情尚未读到的动作输出跳过（与服务端对缺失模板不重复报告一致）。
 */
function checkVariables(graph: WorkflowGraph, startForm: FormDefinition, templates: Map<string, ActionTemplate>, add: AddProblem) {
  const unresolved = new Set(graph.nodes.flatMap(node => node.actions ?? []).filter(action => {
    const template = templates.get(action.templateId);
    return !template || (template.actionType === 'FORM' && !template.config);
  }).map(action => action.key));
  const skip = (name: string) => name.includes('.') && unresolved.has(name.slice(0, name.indexOf('.')));
  const message = (name: string, nodeKey: string) =>
    `变量 ${name} 在节点 ${nodeKey} 不可用：须来自启动表单，或来自每条到达路径上必经的上游动作输出（并行分支之间不可互相引用）`;
  for (const node of graph.nodes) {
    if (node.type !== 'EXCLUSIVE' && node.type !== 'TASK') continue;
    const available = new Set(variableCatalog(graph, startForm, templates, node.key).map(item => item.name));
    for (const condition of node.type === 'EXCLUSIVE' ? node.conditions ?? [] : []) {
      for (const clause of condition.all) {
        if (!clause.variable || available.has(clause.variable) || skip(clause.variable)) continue;
        add('VARIABLE_UNAVAILABLE', message(clause.variable, node.key), node.key, condition.edgeKey);
      }
    }
    for (const action of node.actions ?? []) {
      for (const mapping of Object.values(action.inputs ?? {})) {
        if (mapping.source !== 'VARIABLE' || !mapping.value || available.has(mapping.value) || skip(mapping.value)) continue;
        add('VARIABLE_UNAVAILABLE', message(mapping.value, node.key), node.key, null, action.key);
      }
    }
  }
}

function reachable(from: string, next: (edge: GraphEdge) => string, edges: (key: string) => GraphEdge[]): Set<string> {
  const seen = new Set<string>(); const queue = [from];
  while (queue.length) {
    const key = queue.shift()!;
    if (seen.has(key)) continue;
    seen.add(key); queue.push(...edges(key).map(next));
  }
  return seen;
}

// ---------- 保存 ----------
/** 保存前整理：只保留各类型允许的属性，去掉空审批、判空条件的值；不改变 key 与顺序。 */
export function normalizeGraph(graph: WorkflowGraph): WorkflowGraph {
  const nodes = graph.nodes.map(node => {
    const base: GraphNode = { key: node.key, type: node.type, name: node.name, position: node.position };
    if (node.type === 'TASK') {
      const approverIds = node.approval?.approverIds.filter(Boolean) ?? [];
      return { ...base,
        ...(approverIds.length ? { approval: { mode: node.approval!.mode, approverIds } } : {}),
        actions: (node.actions ?? []).map(action => ({ key: action.key, templateId: action.templateId, required: action.required,
          ...(action.executorId ? { executorId: action.executorId } : {}),
          ...(action.inputs && Object.keys(action.inputs).length ? { inputs: action.inputs } : {}) })),
        notifications: node.notifications ?? [] };
    }
    if (node.type === 'EXCLUSIVE') {
      return { ...base, conditions: (node.conditions ?? []).map(condition => ({ edgeKey: condition.edgeKey,
        all: condition.all.map(clause => valueShape(clause.operator) === 'NONE' ? { variable: clause.variable, operator: clause.operator } : clause) })),
      ...(node.defaultEdgeKey ? { defaultEdgeKey: node.defaultEdgeKey } : {}) };
    }
    return base;
  });
  return { nodes, edges: graph.edges.map(edge => ({ key: edge.key, from: edge.from, to: edge.to })),
    settings: { notifications: graph.settings?.notifications ?? [] } };
}

// ---------- 画布 ----------
export interface CanvasNodeData { node: GraphNode; [key: string]: unknown }
export interface CanvasElement { id: string; type: string; position: { x: number; y: number }; data: CanvasNodeData; selected?: boolean }
export interface CanvasEdgeElement { id: string; source: string; target: string; label?: string; data: { isDefault: boolean; conditional: boolean } }
const canvasTypes: Record<NodeType, string> = {
  START: 'circle', END: 'circle', TASK: 'task', EXCLUSIVE: 'gateway', PARALLEL_SPLIT: 'gateway', PARALLEL_JOIN: 'gateway',
};

/** graph_json → 画布节点与连线；排他网关出边带条件标签（默认出口标“默认”）。 */
export function toCanvas(graph: WorkflowGraph, variables: (gatewayKey: string) => VariableInfo[] = () => [], selectedKey?: string | null) {
  const nodes: CanvasElement[] = graph.nodes.map(node => ({ id: node.key, type: canvasTypes[node.type], position: node.position,
    data: { node }, selected: node.key === selectedKey }));
  const edges: CanvasEdgeElement[] = graph.edges.map(edge => {
    const source = nodeOf(graph, edge.from);
    if (source?.type !== 'EXCLUSIVE') return { id: edge.key, source: edge.from, target: edge.to, data: { isDefault: false, conditional: false } };
    const exit = gatewayExits(graph, edge.from).find(item => item.edge.key === edge.key);
    return { id: edge.key, source: edge.from, target: edge.to,
      label: exit?.isDefault ? '默认' : clausesText(exit?.clauses ?? [], variables(edge.from)),
      data: { isDefault: !!exit?.isDefault, conditional: true } };
  });
  return { nodes, edges };
}

/** 画布拖动的位置变化写回 graph_json。 */
export function applyPositions(graph: WorkflowGraph, changes: { id: string; position?: { x: number; y: number } }[]): WorkflowGraph {
  return changes.reduce((current, change) => change.position ? moveNode(current, change.id, change.position) : current, graph);
}

// ---------- 撤销 ----------
export interface History { past: WorkflowGraph[]; future: WorkflowGraph[] }
export const emptyHistory: History = { past: [], future: [] };
export function record(history: History, previous: WorkflowGraph, limit = 50): History {
  return { past: [...history.past, previous].slice(-limit), future: [] };
}
export function undo(history: History, current: WorkflowGraph): { history: History; graph: WorkflowGraph } | null {
  const previous = history.past.at(-1);
  return previous ? { graph: previous, history: { past: history.past.slice(0, -1), future: [current, ...history.future] } } : null;
}
export function redo(history: History, current: WorkflowGraph): { history: History; graph: WorkflowGraph } | null {
  const [next, ...future] = history.future;
  return next ? { graph: next, history: { past: [...history.past, current], future } } : null;
}
