import { ApiError } from '../api/session.ts';
import { decodeFields } from './externalApiModel.ts';
import type { ApiField } from './externalApiModel.ts';
import type { RegisteredFunction } from './editorModel.ts';
import type { Binding } from './integrationApiModel.ts';

export type ExecuteStatus = 'SUCCESS' | 'FAIL' | 'ERROR';
export interface Initializer { id: string; function: string; args: Record<string, Binding>; required: boolean }
export interface FlowNode { id: string; integrationApi: string; request: Record<string, Binding>; params?: Record<string, Binding> }
export interface FlowReturn { id: string; executeStatus: ExecuteStatus; outputs?: Record<string, Binding> }
export interface FlowEdge { from: string; to: string; condition?: string; priority?: number; default?: boolean }
export interface Flow {
  timeoutMs: number; initializers: Initializer[]; entry: string; nodes: FlowNode[]; returns: FlowReturn[]; edges: FlowEdge[];
}

export interface ContractField extends ApiField { fixed: boolean }
export interface ContractParam { name: string; dataType: string; required: boolean }
export interface ApiContract {
  apiCode: string; apiName: string; requestFields: ContractField[]; params: ContractParam[];
  outputs: { SUCCESS?: ContractParam[]; FAIL?: ContractParam[] };
}
export interface FlowEditorContext { standardApi: { requestFields: ApiField[]; responseFields: ApiField[] }; apis: ApiContract[] }
export interface ImplementationDetail { standardApiCode: string; flowDefinition: unknown; version: number; updateTime: string }

function require(valid: unknown): asserts valid {
  if (!valid) throw new ApiError(502, 'INVALID_RESPONSE');
}
const record = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
const array = (value: unknown): unknown[] => Array.isArray(value) ? value : [];

export function decodeFlowContext(value: unknown): FlowEditorContext {
  const v = value as FlowEditorContext;
  require(v && v.standardApi && Array.isArray(v.apis));
  const apis = v.apis.map(api => {
    require(api && typeof api.apiCode === 'string' && typeof api.apiName === 'string' && Array.isArray(api.requestFields)
      && Array.isArray(api.params) && api.outputs && typeof api.outputs === 'object');
    const fields = decodeFields(api.requestFields);
    return { apiCode: api.apiCode, apiName: api.apiName,
      requestFields: fields.map((field, index) => ({ ...field, fixed: (api.requestFields[index] as ContractField).fixed === true })),
      params: api.params.map(param => ({ ...param })),
      outputs: { SUCCESS: array(api.outputs.SUCCESS) as ContractParam[], FAIL: array(api.outputs.FAIL) as ContractParam[] } };
  });
  return { standardApi: { requestFields: decodeFields(v.standardApi.requestFields), responseFields: decodeFields(v.standardApi.responseFields) }, apis };
}

export function decodeImplementation(value: unknown, standardApiCode: string): ImplementationDetail {
  const v = value as ImplementationDetail;
  require(v && v.standardApiCode === standardApiCode && v.flowDefinition && typeof v.flowDefinition === 'object'
    && Number.isInteger(v.version) && v.version >= 1 && typeof v.updateTime === 'string');
  return { standardApiCode: v.standardApiCode, flowDefinition: v.flowDefinition, version: v.version, updateTime: v.updateTime };
}

/** 新建实现的默认流程：没有节点，只有一个待配置的 ERROR 出口。 */
export function newFlow(): Flow {
  return { timeoutMs: 25000, initializers: [], entry: '', nodes: [], returns: [{ id: 'unknown', executeStatus: 'ERROR' }], edges: [] };
}

/** 已保存的流程 JSON 转为编辑态；缺失部分取默认，不修复非法值（交给服务端校验）。 */
export function toFlow(value: unknown): Flow {
  const v = record(value);
  return {
    timeoutMs: typeof v.timeoutMs === 'number' ? v.timeoutMs : 25000,
    initializers: array(v.initializers).map(item => {
      const i = record(item);
      return { id: String(i.id ?? ''), function: String(i.function ?? ''), args: record(i.args) as Record<string, Binding>, required: i.required !== false };
    }),
    entry: typeof v.entry === 'string' ? v.entry : '',
    nodes: array(v.nodes).map(item => {
      const n = record(item);
      return { id: String(n.id ?? ''), integrationApi: String(n.integrationApi ?? ''), request: record(n.request) as Record<string, Binding>,
        ...(n.params !== undefined ? { params: record(n.params) as Record<string, Binding> } : {}) };
    }),
    returns: array(v.returns).map(item => {
      const r = record(item);
      return { id: String(r.id ?? ''), executeStatus: r.executeStatus as ExecuteStatus,
        ...(r.outputs !== undefined ? { outputs: record(r.outputs) as Record<string, Binding> } : {}) };
    }),
    edges: array(v.edges).map(item => item as FlowEdge),
  };
}

/** 编辑态转为请求体：键顺序与合同一致；ERROR 出口不带 outputs，兜底连线只带 from/to/default，空调用参数省略。 */
export function toFlowJson(flow: Flow): Record<string, unknown> {
  return {
    timeoutMs: flow.timeoutMs,
    initializers: flow.initializers.map(item => ({ id: item.id, function: item.function, args: item.args, required: item.required })),
    entry: flow.entry,
    nodes: flow.nodes.map(node => ({ id: node.id, integrationApi: node.integrationApi, request: node.request,
      ...(node.params && Object.keys(node.params).length ? { params: node.params } : {}) })),
    returns: flow.returns.map(item => item.executeStatus === 'ERROR' ? { id: item.id, executeStatus: item.executeStatus }
      : { id: item.id, executeStatus: item.executeStatus, outputs: item.outputs ?? {} }),
    edges: flow.edges.map(edge => edge.default ? { from: edge.from, to: edge.to, default: true }
      : { from: edge.from, to: edge.to, condition: edge.condition ?? '', priority: edge.priority }),
  };
}

/** 初始化项、节点与出口共用一个标识空间。 */
export function allIds(flow: Flow): Set<string> {
  return new Set([...flow.initializers.map(item => item.id), ...flow.nodes.map(item => item.id), ...flow.returns.map(item => item.id)]);
}

/** 生成未占用的标识：prefix、prefix2、prefix3……，prefix 需满足 [a-z][A-Za-z0-9]*。 */
export function nextId(flow: Flow, prefix: string): string {
  const used = allIds(flow);
  if (!used.has(prefix)) return prefix;
  for (let index = 2; ; index++) if (!used.has(`${prefix}${index}`)) return `${prefix}${index}`;
}

/** 原子 API 编码转为节点标识前缀：CREATE_ORDER → createOrder。 */
export function idPrefix(apiCode: string): string {
  const words = apiCode.toLowerCase().split('_').filter(Boolean);
  const camel = words.map((word, index) => index === 0 ? word : word[0].toUpperCase() + word.slice(1)).join('');
  return /^[a-z]/.test(camel) ? camel : `node${camel}`;
}

/** 某个节点的出口：条件连线按优先级升序，兜底连线固定最后。 */
export function outgoing(flow: Flow, from: string): FlowEdge[] {
  const edges = flow.edges.filter(edge => edge.from === from);
  return [...edges.filter(edge => !edge.default).toSorted((left, right) => (left.priority ?? 0) - (right.priority ?? 0)),
    ...edges.filter(edge => edge.default)];
}

/** 用新的条件连线顺序重写该节点的优先级（1 起连续），兜底连线不变。 */
export function reorderOutgoing(flow: Flow, from: string, ordered: FlowEdge[]): Flow {
  const priority = new Map(ordered.map((edge, index) => [edge, index + 1]));
  return { ...flow, edges: flow.edges.map(edge => edge.from === from && priority.has(edge) ? { ...edge, priority: priority.get(edge) } : edge) };
}

function successors(flow: Flow): Map<string, string[]> {
  const result = new Map<string, string[]>();
  for (const edge of flow.edges) result.set(edge.from, [...(result.get(edge.from) ?? []), edge.to]);
  return result;
}

/** 从 start 可达的顶点（含 start）。 */
export function reachable(flow: Flow, start: string): Set<string> {
  const next = successors(flow); const seen = new Set<string>(); const stack = start ? [start] : [];
  while (stack.length) {
    const vertex = stack.pop()!;
    if (seen.has(vertex)) continue;
    seen.add(vertex); stack.push(...(next.get(vertex) ?? []));
  }
  return seen;
}

export type EdgeResult = { flow: Flow; edge: FlowEdge } | { error: string };

/**
 * 新建连线：只能从节点出发；不能连回上游形成环。节点尚无出口时第一条为兜底连线，其后为条件连线（优先级递增、条件待填）。
 */
export function addEdge(flow: Flow, from: string, to: string): EdgeResult {
  if (!flow.nodes.some(node => node.id === from)) return { error: '只能从原子 API 节点连出' };
  if (from === to || reachable(flow, to).has(from)) return { error: '流程暂不支持环' };
  if (flow.edges.some(edge => edge.from === from && edge.to === to)) return { error: '已存在相同的连线' };
  const existing = flow.edges.filter(edge => edge.from === from);
  const edge: FlowEdge = existing.length === 0 ? { from, to, default: true }
    : { from, to, condition: '', priority: Math.max(0, ...existing.map(item => item.priority ?? 0)) + 1 };
  return { flow: { ...flow, edges: [...flow.edges, edge] }, edge };
}

/** 删除节点或出口及其关联连线；删除入口节点时清空 entry。 */
export function removeVertex(flow: Flow, id: string): Flow {
  return { ...flow, entry: flow.entry === id ? '' : flow.entry, nodes: flow.nodes.filter(node => node.id !== id),
    returns: flow.returns.filter(item => item.id !== id), edges: flow.edges.filter(edge => edge.from !== id && edge.to !== id) };
}

/** 重命名节点或出口，同步 entry 与连线；取值中的 steps 引用不自动改写，由校验提示。 */
export function renameVertex(flow: Flow, from: string, to: string): Flow {
  const rename = (id: string) => id === from ? to : id;
  return { ...flow, entry: rename(flow.entry), nodes: flow.nodes.map(node => node.id === from ? { ...node, id: to } : node),
    returns: flow.returns.map(item => item.id === from ? { ...item, id: to } : item),
    edges: flow.edges.map(edge => ({ ...edge, from: rename(edge.from), to: rename(edge.to) })) };
}

/**
 * 支配节点：从 entry 到该顶点的每条路径都经过的顶点集合（含自身）；不可达顶点为空集。只在无环图上计算，有环时返回空映射。
 */
export function dominators(flow: Flow): Map<string, Set<string>> {
  const result = new Map<string, Set<string>>();
  if (!flow.entry) return result;
  const live = reachable(flow, flow.entry);
  const predecessors = new Map<string, string[]>();
  for (const edge of flow.edges) {
    if (live.has(edge.from) && live.has(edge.to)) predecessors.set(edge.to, [...(predecessors.get(edge.to) ?? []), edge.from]);
  }
  const order = topological(flow, live);
  if (!order) return result;
  for (const vertex of order) {
    const preds = predecessors.get(vertex) ?? [];
    if (vertex === flow.entry || preds.length === 0) { result.set(vertex, new Set([vertex])); continue; }
    let common: string[] | null = null;
    for (const pred of preds) {
      const set = result.get(pred) ?? new Set<string>();
      common = common === null ? [...set] : common.filter(item => set.has(item));
    }
    result.set(vertex, new Set<string>([...(common ?? []), vertex]));
  }
  return result;
}

function topological(flow: Flow, vertices: Set<string>): string[] | null {
  const indegree = new Map([...vertices].map(vertex => [vertex, 0]));
  const edges = flow.edges.filter(edge => vertices.has(edge.from) && vertices.has(edge.to));
  for (const edge of edges) indegree.set(edge.to, (indegree.get(edge.to) ?? 0) + 1);
  const queue = [...vertices].filter(vertex => indegree.get(vertex) === 0); const order: string[] = [];
  while (queue.length) {
    const vertex = queue.shift()!; order.push(vertex);
    for (const edge of edges.filter(item => item.from === vertex)) {
      indegree.set(edge.to, indegree.get(edge.to)! - 1);
      if (indegree.get(edge.to) === 0) queue.push(edge.to);
    }
  }
  return order.length === vertices.size ? order : null;
}

/** 某位置可以读取 steps 的节点：其必经节点（inclusive 为真时含自身，用于连线条件读起点）。 */
export function readableSteps(flow: Flow, vertex: string, inclusive: boolean): string[] {
  const dominating = dominators(flow).get(vertex) ?? new Set<string>();
  return flow.nodes.map(node => node.id).filter(id => dominating.has(id) && (inclusive || id !== vertex));
}

export interface VariableOptions { paths: string[]; unavailableSteps: string[]; handlePaths: string[] }

/**
 * 取值编辑器的变量树：input（标准 API 请求字段）、resources（初始化项非凭据返回属性）、steps（必经节点的状态与输出），
 * 以及只能用于受控句柄参数的 credentials 路径。不在所有路径上的节点单独列出，供页面灰显说明。
 */
export function variableOptions(flow: Flow, context: FlowEditorContext, functions: RegisteredFunction[], vertex: string | null,
  inclusive = false): VariableOptions {
  const input = context.standardApi.requestFields.filter(field => field.fieldLocation === 'BODY' && !field.fieldPath.includes('[]'))
    .map(field => `input.${field.fieldPath}`);
  const resources: string[] = []; const handlePaths: string[] = [];
  for (const initializer of flow.initializers) {
    const declared = functions.find(item => item.functionCode === initializer.function);
    for (const [root, type] of Object.entries(declared?.returnRoots ?? {})) {
      if (type === 'PROTECTED_HANDLE') handlePaths.push(`resources.${initializer.id}.${root}.`);
      else resources.push(`resources.${initializer.id}.${root}`);
    }
  }
  const readable = vertex === null ? [] : readableSteps(flow, vertex, inclusive);
  const steps = readable.flatMap(id => {
    const api = context.apis.find(item => item.apiCode === flow.nodes.find(node => node.id === id)?.integrationApi);
    const outputs = [...new Set([...(api?.outputs.SUCCESS ?? []), ...(api?.outputs.FAIL ?? [])].map(item => item.name))];
    return [`steps.${id}.executeStatus`, ...outputs.map(name => `steps.${id}.outputs.${name}`)];
  });
  const unavailableSteps = flow.nodes.map(node => node.id).filter(id => !readable.includes(id) && id !== vertex);
  return { paths: [...input, ...resources, ...steps], unavailableSteps, handlePaths };
}

/** 请求键：LOCATION:path，HEADER 名称按小写比较。 */
export function normalizeRequestKey(key: string): string {
  return key.startsWith('HEADER:') ? `HEADER:${key.slice(7).toLowerCase()}` : key;
}

/** 节点必填请求字段的覆盖情况（固定值计入，祖先已赋值即视为覆盖；数组元素字段不计）。 */
export function requestCoverage(node: FlowNode, api: ApiContract | undefined): { covered: number; required: number; missing: string[] } {
  if (!api) return { covered: 0, required: 0, missing: [] };
  const assigned = new Set([...Object.keys(node.request).map(normalizeRequestKey),
    ...api.requestFields.filter(field => field.fixed).map(field => normalizeRequestKey(`${field.fieldLocation}:${field.fieldPath}`))]);
  const required = api.requestFields.filter(field => field.required && !field.fieldPath.includes('[]'));
  const missing = required.filter(field => {
    let path = field.fieldPath;
    for (;;) {
      if (assigned.has(normalizeRequestKey(`${field.fieldLocation}:${path}`))) return false;
      const dot = path.lastIndexOf('.');
      if (dot < 0) return true;
      path = path.slice(0, dot);
    }
  }).map(field => `${field.fieldLocation}:${field.fieldPath}`);
  return { covered: required.length - missing.length, required: required.length, missing };
}

/** 条件快捷项：执行状态为 SUCCESS / FAIL / ERROR。 */
export function statusCondition(nodeId: string, status: ExecuteStatus): string {
  return `steps.${nodeId}.executeStatus == '${status}'`;
}

export type FlowTarget =
  | { kind: 'initializer' | 'node' | 'return' | 'edge'; index: number }
  | { kind: 'timeout' | 'entry' | 'edges' | 'flow' };

/** 校验问题位置对应的编辑对象，用于定位与高亮。 */
export function flowTarget(location: string): FlowTarget {
  const match = /^flowDefinition\.(initializers|nodes|returns|edges)\[(\d+)\]/.exec(location);
  if (match) {
    const kinds = { initializers: 'initializer', nodes: 'node', returns: 'return', edges: 'edge' } as const;
    return { kind: kinds[match[1] as keyof typeof kinds], index: Number(match[2]) };
  }
  if (location.startsWith('flowDefinition.timeoutMs')) return { kind: 'timeout' };
  if (location.startsWith('flowDefinition.entry')) return { kind: 'entry' };
  if (location.startsWith('flowDefinition.edges')) return { kind: 'edges' };
  return { kind: 'flow' };
}

export interface Position { x: number; y: number }

/**
 * 自动布局：按从 entry 出发的最长路径分层（开始节点在第 0 层），同层按声明顺序横向排列；不可达的顶点放在最后一层。
 * 位置只用于显示，不写入流程定义。
 */
export function layout(flow: Flow, startId: string): Map<string, Position> {
  const vertices = [...flow.nodes.map(node => node.id), ...flow.returns.map(item => item.id)];
  const depth = new Map<string, number>();
  if (flow.entry && vertices.includes(flow.entry)) {
    const live = reachable(flow, flow.entry);
    const order = topological(flow, live) ?? [...live];
    depth.set(flow.entry, 1);
    for (const vertex of order) {
      const current = depth.get(vertex);
      if (current === undefined) continue;
      for (const edge of flow.edges.filter(item => item.from === vertex && live.has(item.to))) {
        depth.set(edge.to, Math.max(depth.get(edge.to) ?? 0, current + 1));
      }
    }
  }
  const last = Math.max(1, ...depth.values()) + 1;
  for (const vertex of vertices) if (!depth.has(vertex)) depth.set(vertex, last);
  const positions = new Map<string, Position>([[startId, { x: 0, y: 0 }]]);
  const columns = new Map<number, number>();
  for (const vertex of vertices) {
    const layer = depth.get(vertex)!; const column = columns.get(layer) ?? 0;
    columns.set(layer, column + 1);
    positions.set(vertex, { x: column * 300, y: layer * 170 });
  }
  return positions;
}
