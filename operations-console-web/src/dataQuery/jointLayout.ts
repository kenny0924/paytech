import type { JointCell, JointGroup } from './jointModel.ts';
import { subjectValues } from './jointRun.ts';
import type { JointRunState, RunAlreadyLoaded, RunLink, RunNode } from './jointRun.ts';

/**
 * 链路总览的派生数据（B5a，technique 7）：泳道 = 节点行主体键类型的分组，按 laneRank 排序；泳道内按 subjectSortNo、跳数、名称。
 * 颜色按 J1 分组顺序从固定色板依次取，代码中不写分组名或颜色映射；与跳数无关，换入口不改变同一节点的位置。
 */

/** 泳道色板（按分组顺序循环取色）。 */
export const lanePalette = ['#315dca', '#7c3aed', '#64748b', '#0f8a6a', '#b7791f', '#c2410c', '#0e7490', '#be185d', '#4d7c0f', '#6d28d9'] as const;

export interface Lane { groupName: string; rank: number; color: string; nodes: RunNode[] }

/** 分组颜色：按 J1 groups 的顺序取色；J1 未列出的分组排在其后，按 laneRank。 */
export function laneColors(groups: readonly JointGroup[], nodes: readonly Pick<RunNode, 'groupName' | 'laneRank'>[]): Map<string, string> {
  const names = [...groups].sort((a, b) => a.rank - b.rank).map(group => group.groupName);
  const extra = [...new Map(nodes.filter(node => !names.includes(node.groupName)).map(node => [node.groupName, node.laneRank])).entries()]
    .sort((a, b) => a[1] - b[1] || a[0].localeCompare(b[0])).map(([name]) => name);
  return new Map([...names, ...extra].map((name, index) => [name, lanePalette[index % lanePalette.length]]));
}

const nodeOrder = (a: RunNode, b: RunNode) => a.subjectSortNo - b.subjectSortNo || a.depth - b.depth || a.templateName.localeCompare(b.templateName)
  || a.id.localeCompare(b.id);

/** 有节点的泳道，按 laneRank（同 rank 按分组名）；泳道内节点按 subjectSortNo、跳数、名称。 */
export function buildLanes(nodes: readonly RunNode[], colors: ReadonlyMap<string, string>): Lane[] {
  const lanes = new Map<string, Lane>();
  for (const node of nodes) {
    const lane = lanes.get(node.groupName) ?? { groupName: node.groupName, rank: node.laneRank, color: colors.get(node.groupName) ?? lanePalette[0], nodes: [] };
    lane.rank = Math.min(lane.rank, node.laneRank); lane.nodes.push(node);
    lanes.set(node.groupName, lane);
  }
  return [...lanes.values()].map(lane => ({ ...lane, nodes: [...lane.nodes].sort(nodeOrder) }))
    .sort((a, b) => a.rank - b.rank || a.groupName.localeCompare(b.groupName));
}

export interface FlowEdge { id: string; source: string; target: string; values: string[]; keyType: string }

/**
 * 连线：源节点结果行的链路值 → 下一层中 triggerKeyType 相同且 triggerValues 含该值的节点（technique 8.2 links）。
 * 同一对节点的多个值合并为一条线。
 */
export function buildEdges(nodes: readonly RunNode[], links: readonly RunLink[]): FlowEdge[] {
  const byDepth = new Map<number, RunNode[]>();
  for (const node of nodes) byDepth.set(node.depth, [...(byDepth.get(node.depth) ?? []), node]);
  const edges = new Map<string, FlowEdge>();
  for (const link of links) {
    for (const target of byDepth.get(link.depth + 1) ?? []) {
      if (target.triggerKeyType !== link.keyType || !target.triggerValues.includes(link.value)) continue;
      const id = `${link.sourceId}->${target.id}`;
      const edge = edges.get(id) ?? { id, source: link.sourceId, target: target.id, values: [], keyType: link.keyType };
      if (!edge.values.includes(link.value)) edge.values.push(link.value);
      edges.set(id, edge);
    }
  }
  return [...edges.values()];
}

/** 选中节点的来源路径：全部上游节点与连线（反向遍历到起点）。 */
export function ancestry(nodeId: string | null, edges: readonly FlowEdge[]): { nodes: Set<string>; edges: Set<string> } {
  const nodes = new Set<string>(); const used = new Set<string>();
  if (!nodeId) return { nodes, edges: used };
  const queue = [nodeId]; nodes.add(nodeId);
  while (queue.length) {
    const current = queue.shift()!;
    for (const edge of edges) {
      if (edge.target !== current) continue;
      used.add(edge.id);
      if (!nodes.has(edge.source)) { nodes.add(edge.source); queue.push(edge.source); }
    }
  }
  return { nodes, edges: used };
}

/** 路径文字：沿第一条来源连线（按泳道顺序最靠前的上游）回到起点，返回从起点到该节点的节点序列。 */
export function sourcePath(nodeId: string, nodes: readonly RunNode[], edges: readonly FlowEdge[], lanes: readonly Lane[]): RunNode[] {
  const byId = new Map(nodes.map(node => [node.id, node]));
  const laneIndex = new Map(lanes.map((lane, index) => [lane.groupName, index]));
  const path: RunNode[] = []; const seen = new Set<string>();
  let current = byId.get(nodeId);
  while (current && !seen.has(current.id)) {
    path.unshift(current); seen.add(current.id);
    const parents = edges.filter(edge => edge.target === current!.id).map(edge => byId.get(edge.source)).filter((node): node is RunNode => !!node)
      .sort((a, b) => a.depth - b.depth || (laneIndex.get(a.groupName) ?? 0) - (laneIndex.get(b.groupName) ?? 0) || a.subjectSortNo - b.subjectSortNo);
    current = parents[0];
  }
  return path;
}

/** 节点角标：起点（第 0 层）、失败、未查（单步跳过）、截断（n+），其余为条数。 */
export type NodeBadge = { kind: 'entry'; count: number } | { kind: 'failed' } | { kind: 'skipped' } | { kind: 'truncated'; count: number } | { kind: 'count'; count: number };
export function nodeBadge(node: RunNode, rows = node.rows.length): NodeBadge {
  if (node.status === 'FAILED') return { kind: 'failed' };
  if (node.status === 'SKIPPED') return { kind: 'skipped' };
  if (node.depth === 0) return { kind: 'entry', count: rows };
  if (node.truncated) return { kind: 'truncated', count: rows };
  return { kind: 'count', count: rows };
}

// ---------- 未查全 ----------

export type IssueKind = 'failed' | 'partial' | 'skipped';
export interface Issue { kind: IssueKind; nodeIds: string[]; rule: string | null; keyType: string | null; dropped: number | null; depth: number | null }

/**
 * 未查全汇总：失败节点逐个列出；单键值数截断为“部分”；单步跳过的节点与页面护栏停止合并为“未查”。
 * 截断但可加载更多的节点不算问题。
 */
export function collectIssues(state: Pick<JointRunState, 'nodes' | 'truncations' | 'stop'>): Issue[] {
  const issues: Issue[] = state.nodes.filter(node => node.status === 'FAILED')
    .map(node => ({ kind: 'failed', nodeIds: [node.id], rule: node.errorCode, keyType: null, dropped: null, depth: node.depth }));
  for (const item of state.truncations.filter(item => item.rule === 'MAX_VALUES_PER_KEY')) {
    issues.push({ kind: 'partial', nodeIds: [], rule: item.rule, keyType: item.keyType, dropped: item.dropped, depth: item.depth });
  }
  const skippedRules = new Map<string, string[]>();
  for (const node of state.nodes.filter(node => node.status === 'SKIPPED')) {
    const rule = node.errorCode ?? 'STEP_BUDGET_EXHAUSTED';
    skippedRules.set(rule, [...(skippedRules.get(rule) ?? []), node.id]);
  }
  for (const [rule, nodeIds] of skippedRules) issues.push({ kind: 'skipped', nodeIds, rule, keyType: null, dropped: null, depth: null });
  if (state.stop && state.stop.rule !== 'USER_STOPPED') {
    issues.push({ kind: 'skipped', nodeIds: [], rule: state.stop.rule, keyType: null, dropped: null, depth: state.stop.depth });
  }
  return issues;
}

// ---------- 命中多个客户 ----------

/** 维度客户键类型（B11 / Q3）：入口命中多个客户时可按客户筛选。 */
export const clientKeyType = 'CLIENT_ID';

const cellValue = (cell: JointCell | undefined) => cell === null || cell === undefined ? null : String(cell);

/** 第 0 层结果中客户维度列的不同取值（少于 2 个时不提供筛选）。 */
export function entryClients(nodes: readonly RunNode[]): string[] {
  const values = new Set<string>();
  for (const node of nodes.filter(node => node.depth === 0)) {
    const index = node.columns.findIndex(column => column.keyType === clientKeyType);
    if (index < 0) continue;
    for (const row of node.rows) { const value = cellValue(row[index]); if (value) values.add(value); }
  }
  return values.size > 1 ? [...values].sort() : [];
}

/**
 * 按客户筛选（页面内，不重新查询）：第 0 层只保留该客户的行；之后的节点只有被保留行的链路值触发时才显示；
 * 节点有客户维度列时再按该列过滤行。返回 节点 id → 保留的行号；不在其中的节点隐藏。
 */
export function filterByClient(nodes: readonly RunNode[], links: readonly RunLink[], client: string | null): Map<string, number[]> | null {
  if (!client) return null;
  const visible = new Map<string, number[]>();
  const rowsMatching = (node: RunNode) => {
    const index = node.columns.findIndex(column => column.keyType === clientKeyType);
    return node.rows.map((row, i) => [row, i] as const).filter(([row]) => index < 0 || cellValue(row[index]) === client || cellValue(row[index]) === null)
      .map(([, i]) => i);
  };
  const maxDepth = Math.max(0, ...nodes.map(node => node.depth));
  for (let depth = 0; depth <= maxDepth; depth++) {
    for (const node of nodes.filter(item => item.depth === depth)) {
      if (depth === 0) { visible.set(node.id, rowsMatching(node)); continue; }
      const reached = links.some(link => link.depth === depth - 1 && link.keyType === node.triggerKeyType && node.triggerValues.includes(link.value)
        && (visible.get(link.sourceId) ?? []).includes(link.rowIndex));
      if (reached) visible.set(node.id, rowsMatching(node));
    }
  }
  return visible;
}

// ---------- 定位链路值 ----------

/**
 * 链路值“定位”：优先找以该键类型为行主体、结果中含该值的节点（这条记录本身），其次找被该值触发的节点。
 */
export function locateValue(nodes: readonly RunNode[], keyType: string, value: string, exceptId: string): RunNode | null {
  const holds = (node: RunNode) => node.columns.some((column, index) => column.keyType === keyType && node.rows.some(row => cellValue(row[index]) === value));
  return nodes.find(node => node.id !== exceptId && node.subjectKeyType === keyType && holds(node))
    ?? nodes.find(node => node.id !== exceptId && node.triggerKeyType === keyType && node.triggerValues.includes(value)) ?? null;
}

/** 涉及的分组数（概要“涉及 n 个系统”）。 */
export const groupCount = (nodes: readonly RunNode[]) => new Set(nodes.map(node => node.groupName)).size;

// ---------- 未执行的视图（已加载对象，technique 8.7） ----------

export interface SkippedView { templateCode: string; templateName: string; keyType: string; values: string[]; depth: number }

/**
 * 节点明细中“另有 N 个视图未执行”：跳过记录的键类型等于本节点行主体键类型、且值与本节点已加载的对象值有交集时，
 * 归到本节点；查看时只用交集的值。同一模板多次跳过合并为一项（取最早的层，值去重）。
 */
export function skippedViews(node: RunNode, alreadyLoaded: readonly RunAlreadyLoaded[]): SkippedView[] {
  const own = subjectValues(node);
  if (!own.length) return [];
  const views = new Map<string, SkippedView>();
  for (const item of alreadyLoaded) {
    if (item.keyType !== node.subjectKeyType || item.templateCode === node.templateCode) continue;
    const values = item.values.filter(value => own.includes(value));
    if (!values.length) continue;
    const view = views.get(item.templateCode) ?? { templateCode: item.templateCode, templateName: item.templateName, keyType: item.keyType, values: [], depth: item.depth };
    view.depth = Math.min(view.depth, item.depth);
    for (const value of values) if (!view.values.includes(value)) view.values.push(value);
    views.set(item.templateCode, view);
  }
  return [...views.values()];
}

// ---------- 按对象合并显示（修订 c，technique 8.7 补充 / B5c） ----------

/** 被合并掉的路径：该节点的行已在更早的节点显示过（同一行主体值）。 */
export interface AlsoReached { nodeId: string; templateName: string; depth: number }
export interface MergedRun {
  /** 去重后用于显示的节点：行只保留首次出现的对象；全部重复的节点不在其中。 */
  nodes: RunNode[];
  /** 链接改指向显示该对象的节点与行号（源行被合并时指向首次显示该对象的节点）。 */
  links: RunLink[];
  /** 首次显示对象的节点 → 也能到达这些对象的其他路径（按节点去重）。 */
  also: ReadonlyMap<string, AlsoReached[]>;
}

/**
 * 按到达顺序遍历节点，以（行主体键类型, 行主体值）为对象身份：已在更早节点显示过的行去掉，去重后没有行的节点不绘制，
 * 改记在首次显示该对象的节点上（“也可经 … 到达”）。没有行主体列的节点、失败 / 未查节点、0 行节点保持原样。
 * 只影响显示；链路展开（LINK 提取、loadedSubjects、下一跳键）仍按原始结果。
 */
export function mergeByObject(nodes: readonly RunNode[], links: readonly RunLink[]): MergedRun {
  const owner = new Map<string, { nodeId: string; row: number }>();
  const rowMap = new Map<string, { nodeId: string; row: number }>();
  const also = new Map<string, AlsoReached[]>();
  const kept: RunNode[] = [];
  const note = (firstId: string, node: RunNode) => {
    const list = also.get(firstId) ?? [];
    if (!list.some(item => item.nodeId === node.id)) list.push({ nodeId: node.id, templateName: node.templateName, depth: node.depth });
    also.set(firstId, list);
  };
  for (const node of nodes) {
    const subject = node.columns.findIndex(column => column.keyType === node.subjectKeyType);
    if (node.status !== 'SUCCEEDED' || subject < 0 || node.rows.length === 0) {
      node.rows.forEach((_, row) => rowMap.set(`${node.id}|${row}`, { nodeId: node.id, row }));
      kept.push(node); continue;
    }
    const rows: RunNode['rows'] = [];
    node.rows.forEach((row, index) => {
      const cell = row[subject];
      const key = cell === null || cell === undefined || cell === '' ? null : `${node.subjectKeyType}\u0000${String(cell)}`;
      const first = key ? owner.get(key) : undefined;
      if (first) { rowMap.set(`${node.id}|${index}`, first); note(first.nodeId, node); return; }
      const position = { nodeId: node.id, row: rows.length };
      if (key) owner.set(key, position);
      rowMap.set(`${node.id}|${index}`, position);
      rows.push(row);
    });
    if (rows.length) kept.push(rows.length === node.rows.length ? node : { ...node, rows });
  }
  const mapped = links.map(link => {
    const target = rowMap.get(`${link.sourceId}|${link.rowIndex}`);
    return target ? { ...link, sourceId: target.nodeId, rowIndex: target.row } : link;
  });
  return { nodes: kept, links: mapped, also };
}
