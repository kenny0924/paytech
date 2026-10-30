import type { AlreadyLoaded, JointLimits, JointLink, JointNode, JointStepRequest, JointStepResponse, StepKeys, Truncation } from './jointModel.ts';
import type { KeyValueType } from './keyTypeModel.ts';

/**
 * 联合查询的逐层驱动（technique S-01 / 8.2）：Platform 每次执行一层并返回新发现的链路键；页面维护已访问键
 * `(键类型, 规范值)`、层数、累计执行数与总时长，扣除已访问后逐层调用，直到没有新键或触发页面护栏、用户停止。
 * 本模块不依赖 React 与网络：step 由调用方注入，便于测试。
 */

/** 页面产生的截断（服务端单步截断见响应 truncations）。 */
export const pageStopRules = ['MAX_DEPTH', 'MAX_TEMPLATE_EXECUTIONS', 'MAX_TOTAL_SECONDS', 'USER_STOPPED'] as const;
export type PageStopRule = typeof pageStopRules[number];

/** 页面内的节点：id 在一次查询内唯一（服务端 nodeId 在续查时可能重复，见 mergeStep）。 */
export interface RunNode extends JointNode { id: string; depth: number }
export interface RunLink extends JointLink { sourceId: string; depth: number }
export interface RunTruncation extends Truncation { depth: number }
export interface RunStop { rule: PageStopRule; depth: number; pending: StepKeys[] }
/** 被服务端跳过的“按自身 ID 查”视图，带所在层（按需查看时用同一层调用）。 */
export interface RunAlreadyLoaded extends AlreadyLoaded { depth: number }

export interface JointRunState {
  entry: StepKeys;
  status: 'running' | 'done' | 'stopped' | 'failed';
  nodes: RunNode[];
  links: RunLink[];
  truncations: RunTruncation[];
  /** 已作为触发键发送过的 `(键类型, 规范值)`。 */
  visited: ReadonlySet<string>;
  /**
   * 已加载对象（technique 8.7）：键类型 → 规范值（按加入先后，最新在后）。来自每个 SUCCEEDED 节点结果中
   * 标注为本节点行主体键类型的列的非空值。
   */
  loaded: ReadonlyMap<string, readonly string[]>;
  /** 因对象已加载而未执行的视图，供节点明细按需查看。 */
  alreadyLoaded: RunAlreadyLoaded[];
  /** 已执行的模板次数（不含 SKIPPED）。 */
  executions: number;
  /** 当前或最后一层。 */
  depth: number;
  stop: RunStop | null;
  error: unknown;
  startedAt: number;
  elapsedMillis: number;
}

export const visitKey = (keyType: string, value: string) => `${keyType}\u0000${value}`;

/** 入口值规范化（B4）：INTEGER 去前导零（全零为 0）；STRING 原样。服务端同口径，用于本地去重。 */
export function normalizeKeyValue(valueType: KeyValueType, value: string): string {
  if (valueType !== 'INTEGER') return value;
  const stripped = value.replace(/^0+(?=\d)/, '');
  return stripped;
}

export function initialRun(entry: StepKeys, now: number): JointRunState {
  return { entry, status: 'running', nodes: [], links: [], truncations: [], visited: new Set(entry.values.map(value => visitKey(entry.keyType, value))),
    loaded: new Map(), alreadyLoaded: [], executions: 0, depth: 0, stop: null, error: null, startedAt: now, elapsedMillis: 0 };
}

/**
 * 合入一步的结果。replaceIds 中的节点（重试这一项）被同一服务端 nodeId 的新结果替换，其旧连线一并移除；
 * 其余同 nodeId 的节点（续查时同层同模板再次执行）另起本地 id。
 */
export function mergeStep(state: JointRunState, response: JointStepResponse, replaceIds: readonly string[] = []): JointRunState {
  const replaced = new Map(state.nodes.filter(node => replaceIds.includes(node.id)).map(node => [`${node.depth}|${node.nodeId}`, node.id]));
  const taken = new Set(state.nodes.filter(node => !replaceIds.includes(node.id)).map(node => node.id));
  const idOf = new Map<string, string>();
  const incoming: RunNode[] = response.nodes.map(node => {
    let id = replaced.get(`${response.depth}|${node.nodeId}`) ?? node.nodeId;
    for (let n = 2; taken.has(id); n++) id = `${node.nodeId}#${n}`;
    taken.add(id); idOf.set(node.nodeId, id);
    return { ...node, id, depth: response.depth };
  });
  const incomingIds = new Set(incoming.map(node => node.id));
  const kept = state.nodes.filter(node => !incomingIds.has(node.id));
  const ordered = [...kept, ...incoming];
  // 被替换的节点保持原有位置顺序，避免详情列表跳动。
  const positions = new Map(state.nodes.map((node, index) => [node.id, index]));
  ordered.sort((a, b) => (positions.get(a.id) ?? Number.MAX_SAFE_INTEGER) - (positions.get(b.id) ?? Number.MAX_SAFE_INTEGER));
  const links = [...state.links.filter(link => !incomingIds.has(link.sourceId)),
    ...response.links.filter(link => idOf.has(link.nodeId)).map(link => ({ ...link, sourceId: idOf.get(link.nodeId)!, depth: response.depth }))];
  const executed = response.nodes.filter(node => node.status !== 'SKIPPED').length;
  return { ...state, nodes: ordered, links, executions: state.executions + executed, loaded: withLoaded(state.loaded, incoming),
    alreadyLoaded: [...state.alreadyLoaded, ...(response.alreadyLoaded ?? []).map(item => ({ ...item, depth: response.depth }))],
    truncations: [...state.truncations, ...response.truncations.map(item => ({ ...item, depth: response.depth }))] };
}

/** 节点结果中行主体键类型列的非空值（字符串原样；结果中的整数已是规范十进制）。 */
export function subjectValues(node: Pick<JointNode, 'status' | 'subjectKeyType' | 'columns' | 'rows'>): string[] {
  if (node.status !== 'SUCCEEDED') return [];
  const indexes = node.columns.map((column, index) => column.keyType === node.subjectKeyType ? index : -1).filter(index => index >= 0);
  const values: string[] = [];
  for (const row of node.rows) for (const index of indexes) {
    const cell = row[index];
    if (cell === null || cell === undefined || cell === '') continue;
    const value = String(cell);
    if (value.length > 128 || !value.trim()) continue;
    if (!values.includes(value)) values.push(value);
  }
  return values;
}

function withLoaded(loaded: ReadonlyMap<string, readonly string[]>, nodes: readonly JointNode[]): ReadonlyMap<string, readonly string[]> {
  const next = new Map(loaded);
  for (const node of nodes) {
    const values = subjectValues(node);
    if (!values.length) continue;
    // 重新出现的值移到末尾：超过上限时保留最近的
    const current = (next.get(node.subjectKeyType) ?? []).filter(value => !values.includes(value));
    next.delete(node.subjectKeyType);
    next.set(node.subjectKeyType, [...current, ...values]);
  }
  return next;
}

/**
 * 请求因 loadedSubjects 被拒：422 且字段错误全部指向 loadedSubjects.*；或 400 INVALID_REQUEST 且没有字段错误
 * （不认识该字段的旧 ops-bff 反序列化失败，见 jq-platform 2026-10-30 说明）。两种情况都不带它重发一次。
 */
function rejectsLoadedSubjects(error: unknown): boolean {
  const value = error as { status?: number; code?: string; fieldErrors?: readonly { field: string }[] } | null;
  const fields = value?.fieldErrors ?? [];
  if (value?.status === 422) return fields.length > 0 && fields.every(item => item.field.startsWith('loadedSubjects'));
  if (value?.status === 400 && value.code === 'INVALID_REQUEST') return fields.length === 0 || fields.every(item => item.field.startsWith('loadedSubjects'));
  return false;
}

/** J3 loadedSubjects 上限（technique 8.7）。 */
export const loadedLimits = { keyTypes: 30, values: 200 } as const;

/** 请求体中的已加载对象：最近更新的 30 个键类型，每类最近的 200 个值；没有时为 undefined（不发送字段）。 */
export function loadedSubjects(loaded: ReadonlyMap<string, readonly string[]>): StepKeys[] | undefined {
  const entries = [...loaded.entries()].filter(([, values]) => values.length).slice(-loadedLimits.keyTypes);
  return entries.length ? entries.map(([keyType, values]) => ({ keyType, values: values.slice(-loadedLimits.values) })) : undefined;
}

/** 本层发现的键扣除已访问值；同一键类型合并去重，每个键类型至多 maxValuesPerKey 个。 */
export function nextKeys(discovered: readonly StepKeys[], visited: ReadonlySet<string>, maxValuesPerKey: number): StepKeys[] {
  const merged = new Map<string, string[]>();
  for (const keys of discovered) {
    const values = merged.get(keys.keyType) ?? [];
    for (const value of keys.values) if (!visited.has(visitKey(keys.keyType, value)) && !values.includes(value)) values.push(value);
    merged.set(keys.keyType, values);
  }
  return [...merged.entries()].filter(([, values]) => values.length).map(([keyType, values]) => ({ keyType, values: values.slice(0, maxValuesPerKey) }));
}

/** 单次请求至多 20 个键类型（J3）；超过时同层拆成多次请求。 */
export const maxKeysPerStep = 20;
export function chunkKeys(keys: readonly StepKeys[]): StepKeys[][] {
  const chunks: StepKeys[][] = [];
  for (let i = 0; i < keys.length; i += maxKeysPerStep) chunks.push(keys.slice(i, i + maxKeysPerStep));
  return chunks;
}

export interface DriveOptions {
  limits: JointLimits;
  step: (request: JointStepRequest, signal: AbortSignal) => Promise<JointStepResponse>;
  signal: AbortSignal;
  now: () => number;
  onUpdate: (state: JointRunState) => void;
}

const withVisited = (visited: ReadonlySet<string>, keys: readonly StepKeys[]) => {
  const next = new Set(visited);
  for (const item of keys) for (const value of item.values) next.add(visitKey(item.keyType, value));
  return next;
};

/** 页面护栏：在发起下一层前检查（层数、累计执行数、总时长）；用户停止由 signal 表示。 */
function pageStop(state: JointRunState, depth: number, options: DriveOptions): PageStopRule | null {
  if (options.signal.aborted) return 'USER_STOPPED';
  if (depth >= options.limits.maxDepth) return 'MAX_DEPTH';
  if (state.executions >= options.limits.maxTemplateExecutions) return 'MAX_TEMPLATE_EXECUTIONS';
  if (options.now() - state.startedAt >= options.limits.maxTotalSeconds * 1000) return 'MAX_TOTAL_SECONDS';
  return null;
}

/**
 * 从 depth 层、frontier 键开始逐层执行，直到没有新键（done）、页面护栏或用户停止（stopped）、请求失败（failed）。
 * 每完成一次请求回调 onUpdate；已得结果始终保留。首层 templateCodes 用于“重试这一项”。
 */
export async function drive(start: JointRunState, frontier: StepKeys[], depth: number, options: DriveOptions,
  first?: { templateCodes: string[]; replaceIds: string[] }): Promise<JointRunState> {
  let state: JointRunState = { ...start, status: 'running', stop: null, error: null };
  let keys = frontier; let level = depth; let retry = first;
  const finish = (patch: Partial<JointRunState>) => {
    state = { ...state, ...patch, elapsedMillis: options.now() - state.startedAt };
    options.onUpdate(state); return state;
  };
  while (keys.length) {
    const rule = retry ? (options.signal.aborted ? 'USER_STOPPED' : null) : pageStop(state, level, options);
    if (rule) return finish({ status: 'stopped', stop: { rule, depth: level, pending: keys } });
    state = { ...state, depth: level, visited: withVisited(state.visited, keys) };
    const discovered: StepKeys[] = [];
    for (const chunk of chunkKeys(keys)) {
      try {
        // 已加载对象只随第 1 跳起的展开请求发送；入口层与“重试这一项”（templateCodes）不做跳过
        const subjects = !retry && level > 0 ? loadedSubjects(state.loaded) : undefined;
        const request = { depth: level, keys: chunk, ...(retry ? { templateCodes: retry.templateCodes } : {}) };
        const response = await options.step(subjects ? { ...request, loadedSubjects: subjects } : request, options.signal).catch(error => {
          // 已加载对象只是优化：服务端因 loadedSubjects 拒绝（422 / 旧 BFF 的 400）时，不带它重发一次，链路照常展开。
          if (subjects && rejectsLoadedSubjects(error) && !options.signal.aborted) return options.step(request, options.signal);
          throw error;
        });
        state = mergeStep(state, response, retry?.replaceIds ?? []);
        discovered.push(...response.discoveredKeys);
        state = { ...state, elapsedMillis: options.now() - state.startedAt };
        options.onUpdate(state);
      } catch (error) {
        if (options.signal.aborted) return finish({ status: 'stopped', stop: { rule: 'USER_STOPPED', depth: level, pending: keys } });
        return finish({ status: 'failed', error });
      }
    }
    retry = undefined;
    keys = nextKeys(discovered, state.visited, options.limits.maxValuesPerKey);
    level += 1;
  }
  return finish({ status: 'done' });
}

/** 新查询：入口键为第 0 层。 */
export function runJointQuery(entry: StepKeys, options: DriveOptions): Promise<JointRunState> {
  const state = initialRun(entry, options.now());
  options.onUpdate(state);
  return drive(state, [entry], 0, options);
}

/**
 * 重试这一项：以节点的触发键与触发值、只执行该模板重新调用同一层；结果替换原节点，
 * 新发现的键（扣除已访问）照常向下一层展开。计时从重试开始，累计执行数延续。
 */
export function retryNode(state: JointRunState, node: RunNode, options: DriveOptions): Promise<JointRunState> {
  const restarted = { ...state, startedAt: options.now() };
  return drive(restarted, [{ keyType: node.triggerKeyType, values: node.triggerValues }], node.depth, options,
    { templateCodes: [node.templateCode], replaceIds: [node.id] });
}
