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
 * 编辑时的本地快速检查（B1 的结构部分），帮助在保存前定位；保存草稿后以 Platform 返回的问题为准，发布时由后端完整校验。
 */
export function localProblems(graph: WorkflowGraph, templates: Map<string, ActionTemplate>, startForm?: FormDefinition): Problem[] {
  const problems: Problem[] = [];
  const add = (code: string, message: string, nodeKey: string | null = null, edgeKey: string | null = null, itemKey: string | null = null) =>
    problems.push({ code: `LOCAL_${code}`, message, nodeKey, edgeKey, itemKey });
  const starts = graph.nodes.filter(node => node.type === 'START');
  const ends = graph.nodes.filter(node => node.type === 'END');
  if (starts.length !== 1) add('START_COUNT', '必须恰好一个开始节点');
  if (!ends.length) add('END_COUNT', '至少需要一个结束节点');
  if (graph.nodes.length > graphLimits.nodes) add('NODE_LIMIT', `节点不能超过 ${graphLimits.nodes} 个`);
  const graphSize = jsonBytes(normalizeGraph(graph));
  if (graphSize > graphLimits.graphBytes) add('GRAPH_SIZE', `流程图配置 ${(graphSize / 1024).toFixed(1)} KiB，超过 48 KiB 上限`);
  if (startForm) {
    const formSize = jsonBytes(startForm);
    if (formSize > graphLimits.startFormBytes) add('FORM_SIZE', `启动表单配置 ${(formSize / 1024).toFixed(1)} KiB，超过 12 KiB 上限`);
  }
  const keys = new Map<string, number>();
  for (const key of [...graph.nodes.map(node => node.key), ...graph.nodes.flatMap(node => node.actions?.map(action => action.key) ?? [])]) {
    keys.set(key, (keys.get(key) ?? 0) + 1);
  }
  for (const [key, count] of keys) if (count > 1) add('DUPLICATE_KEY', `key ${key} 重复`, nodeOf(graph, key) ? key : null);
  const fromStart = starts[0] ? reachable(starts[0].key, edge => edge.to, key => outgoing(graph, key)) : new Set<string>();
  const toEnd = new Set<string>();
  for (const end of ends) for (const key of reachable(end.key, edge => edge.from, key => incoming(graph, key))) toEnd.add(key);
  for (const node of graph.nodes) {
    const label = node.name || node.key;
    const exits = outgoing(graph, node.key);
    if (node.type !== 'START' && !fromStart.has(node.key)) add('UNREACHABLE', `${label} 从开始不可达`, node.key);
    if (node.type !== 'END' && !toEnd.has(node.key)) add('DEAD_END', `${label} 到不了结束节点`, node.key);
    if (node.type === 'TASK') {
      if (exits.length !== 1) add('TASK_EXIT', `${label} 需要恰好一条出边`, node.key);
      const actions = node.actions ?? [];
      if (!actions.length && !(node.approval?.approverIds.length)) add('TASK_EMPTY', `${label} 至少需要一个审批人或一个动作`, node.key);
      if (actions.length > graphLimits.actionsPerNode) add('ACTION_LIMIT', `${label} 的动作不能超过 ${graphLimits.actionsPerNode} 个`, node.key);
      const approvers = node.approval?.approverIds ?? [];
      if (new Set(approvers).size !== approvers.length) add('APPROVER_DUPLICATE', `${label} 的审批人重复`, node.key);
      for (const action of actions) {
        const template = templates.get(action.templateId);
        if (!action.templateId) add('ACTION_TEMPLATE', `${label} 的动作 ${action.key} 未选择动作模板`, node.key, null, action.key);
        else if (template?.actionType === 'DATA_QUERY') add('DATA_QUERY', `动作 ${action.key} 是数据查询，本期不能被发布引用`, node.key, null, action.key);
        else if (template && template.status !== 'ACTIVE') add('TEMPLATE_INACTIVE', `动作 ${action.key} 引用的模板已停用`, node.key, null, action.key);
        if (!action.executorId) add('ACTION_EXECUTOR', `动作 ${action.key} 未指定执行人`, node.key, null, action.key);
      }
    }
    if (node.type === 'EXCLUSIVE') {
      if (exits.length < 2) add('GATEWAY_EXITS', `${label} 至少需要两条出边`, node.key);
      if (!node.defaultEdgeKey || !exits.some(edge => edge.key === node.defaultEdgeKey)) add('GATEWAY_DEFAULT', `${label} 必须有且只有一条默认出口`, node.key);
      for (const exit of gatewayExits(graph, node.key)) {
        if (!exit.isDefault && !exit.clauses.length) add('CONDITION_EMPTY', `${label} 的出口 ${exit.edge.key} 还没有条件`, node.key, exit.edge.key);
      }
    }
    if (node.type === 'START' && incoming(graph, node.key).length) add('START_INCOMING', '开始节点不能有入边', node.key);
    if (node.type === 'END' && exits.length) add('END_OUTGOING', '结束节点不能有出边', node.key);
  }
  return problems;
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
