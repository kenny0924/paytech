import type { VariableOptions } from './flowModel.ts';
import type { Binding } from './integrationApiModel.ts';

/** 一个可赋值的目标：原子 API 请求字段、调用参数或标准 API 响应字段。 */
export interface MappingTarget {
  /** 取值的键：请求字段 LOCATION:path，调用参数 PARAM:name，响应字段为字段路径。 */
  key: string;
  /** 分组名：PATH / HEADER / QUERY / BODY / 调用参数 / 响应字段。 */
  group: string;
  /** 组内路径；nested 为真时按“.”分层。 */
  path: string;
  nested: boolean;
  dataType: string;
  required: boolean;
  /** 协议固定值：不能由流程赋值。 */
  fixed: boolean;
  /** 受控句柄参数：只能以路径绑定 credentials。 */
  handle: boolean;
}

/** 目标的取值状态：已赋值、必填未赋值、非必填未赋值、协议固定值、由祖先对象整体赋值。 */
export type MappingState = 'ASSIGNED' | 'MISSING' | 'OPTIONAL' | 'FIXED' | 'INHERITED';

export type MappingRow =
  | { kind: 'group'; group: string; count: number }
  | { kind: 'object'; group: string; path: string; name: string; depth: number }
  | { kind: 'field'; target: MappingTarget; state: MappingState; name: string; depth: number };

export const groupOrder = ['PATH', 'HEADER', 'QUERY', 'BODY', '调用参数', '响应字段'];

const segments = (target: Pick<MappingTarget, 'path' | 'nested'>) => target.nested ? target.path.split('.') : [target.path];
const lastSegment = (path: string) => path.slice(path.lastIndexOf('.') + 1);

/** 每个目标的取值状态；固定值优先，其次本身已赋值、祖先已赋值（只对分层路径），最后按必填区分。 */
export function mappingStates(targets: MappingTarget[], bindings: Map<string, Binding>): Map<string, MappingState> {
  const assignedPaths = new Set(targets.filter(target => bindings.has(target.key)).map(target => `${target.group}:${target.path}`));
  const states = new Map<string, MappingState>();
  for (const target of targets) {
    if (target.fixed) states.set(target.key, 'FIXED');
    else if (bindings.has(target.key)) states.set(target.key, 'ASSIGNED');
    else if (segments(target).slice(0, -1).some((_, index, all) => assignedPaths.has(`${target.group}:${all.slice(0, index + 1).join('.')}`)))
      states.set(target.key, 'INHERITED');
    else states.set(target.key, target.required ? 'MISSING' : 'OPTIONAL');
  }
  return states;
}

/** 未赋值：必填或非必填都算，固定值与祖先整体赋值不算。 */
export const isUnassigned = (state: MappingState) => state === 'MISSING' || state === 'OPTIONAL';

/**
 * 列表行：按分组排列，组内父字段在前、子字段紧随其后（同级保持声明顺序）；固定值不进列表。
 * include 与 query 过滤字段行；被保留字段的上级若未保留，补一行只读的对象行。
 */
export function mappingRows(targets: MappingTarget[], states: Map<string, MappingState>, include: (target: MappingTarget, state: MappingState) => boolean,
  query = ''): MappingRow[] {
  const needle = query.trim().toLowerCase();
  const rank = new Map<string, number>();
  for (const target of targets) segments(target).forEach((_, index, all) => {
    const prefix = `${target.group}:${all.slice(0, index + 1).join('.')}`;
    if (!rank.has(prefix)) rank.set(prefix, rank.size);
  });
  const ranks = (target: MappingTarget) => segments(target).map((_, index, all) => rank.get(`${target.group}:${all.slice(0, index + 1).join('.')}`)!);
  const kept = targets.filter(target => {
    const state = states.get(target.key)!;
    return state !== 'FIXED' && include(target, state) && (!needle || target.path.toLowerCase().includes(needle));
  });
  const rows: MappingRow[] = [];
  const groups = [...new Set(targets.map(target => target.group))]
    .sort((a, b) => (groupOrder.indexOf(a) + 1 || 99) - (groupOrder.indexOf(b) + 1 || 99));
  for (const group of groups) {
    const members = kept.filter(target => target.group === group).sort((a, b) => {
      const left = ranks(a); const right = ranks(b);
      for (let index = 0; index < Math.min(left.length, right.length); index++) if (left[index] !== right[index]) return left[index] - right[index];
      return left.length - right.length;
    });
    if (!members.length) continue;
    rows.push({ kind: 'group', group, count: members.length });
    const shown = new Set<string>();
    for (const target of members) {
      const parts = segments(target);
      for (let depth = 0; depth < parts.length - 1; depth++) {
        const path = parts.slice(0, depth + 1).join('.');
        if (shown.has(path)) continue;
        shown.add(path);
        rows.push({ kind: 'object', group, path, name: parts[depth], depth });
      }
      shown.add(target.path);
      rows.push({ kind: 'field', target, state: states.get(target.key)!, name: parts[parts.length - 1], depth: parts.length - 1 });
    }
  }
  return rows;
}

/** 路径变量的声明类型；初始化结果的键在运行时才知道，按根的声明类型返回。未知时为 null。 */
export function variableType(path: string, variables: VariableOptions): string | null {
  if (variables.types?.[path]) return variables.types[path];
  const root = Object.keys(variables.mapRoots ?? {}).find(prefix => path.startsWith(`${prefix}.`));
  return root ? variables.mapRoots![root] : null;
}

/** 类型提示：路径变量的声明类型与目标类型不同时给出说明（只提示，不拦截保存）。 */
export function typeHint(target: MappingTarget, binding: Binding | undefined, variables: VariableOptions): string | null {
  if (!binding || binding.kind !== 'PATH' || target.handle) return null;
  const type = variableType(binding.path.trim(), variables);
  if (!type || type === target.dataType) return null;
  return `类型不一致：变量是 ${type}，字段是 ${target.dataType}`;
}

/**
 * 按同名自动匹配：只给未赋值（非固定、非受控句柄）的目标找末段同名（不区分大小写）的变量；
 * 同名多个时只保留类型一致的那个，仍不唯一则不建议。返回目标键 → 建议路径，不写入任何取值。
 */
export function suggestMatches(targets: MappingTarget[], states: Map<string, MappingState>, variables: VariableOptions): Map<string, string> {
  const suggestions = new Map<string, string>();
  for (const target of targets) {
    if (target.handle || !isUnassigned(states.get(target.key)!)) continue;
    const name = lastSegment(target.path).toLowerCase();
    const named = variables.paths.filter(path => lastSegment(path).toLowerCase() === name);
    const typed = named.length > 1 ? named.filter(path => variableType(path, variables) === target.dataType) : named;
    if (typed.length === 1) suggestions.set(target.key, typed[0]);
  }
  return suggestions;
}

/** 当前行之后（循环到开头）的下一个未赋值目标键；没有时返回 null。 */
export function nextUnassigned(rows: MappingRow[], current: string | null): string | null {
  const keys = rows.flatMap(row => row.kind === 'field' && isUnassigned(row.state) ? [row.target.key] : []);
  if (!keys.length) return null;
  const order = rows.flatMap(row => row.kind === 'field' ? [row.target.key] : []);
  const from = current === null ? -1 : order.indexOf(current);
  return keys.find(key => order.indexOf(key) > from) ?? (keys[0] === current ? null : keys[0]);
}

/** 变量树的一行：文件夹（可折叠，本身是变量时也可点击）或可点击的变量；runtime 表示键名要在选中后补全。 */
export interface VariableNode {
  key: string; label: string; depth: number; folder: boolean; group: 'input' | 'resources' | 'steps' | 'handle';
  /** 点击写入的路径；不是变量的文件夹为空。 */
  insert: string | null; type: string | null; runtime?: boolean;
}

/**
 * 变量树：input 按字段层级展开，resources 按初始化项列出可补全的根（键名运行时才知道），steps 按必经节点列出状态与输出；
 * 受控句柄单独一组。query 非空时只列出路径包含它的变量（平铺显示完整路径）。
 */
export function variableTree(variables: VariableOptions, query = ''): VariableNode[] {
  const needle = query.trim().toLowerCase();
  const nodes: VariableNode[] = [];
  const leaf = (group: VariableNode['group'], path: string, label: string, depth: number, extra: Partial<VariableNode> = {}) =>
    nodes.push({ key: `${group}:${path}`, label, depth, folder: false, group, insert: path, type: variableType(path, variables), ...extra });
  if (needle) {
    for (const path of variables.paths.filter(item => item.toLowerCase().includes(needle))) {
      const group = path.startsWith('input.') ? 'input' : path.startsWith('steps.') ? 'steps' : 'resources';
      if (group === 'resources') leaf(group, `${path}.`, `${path}.<名称>`, 0, { runtime: true, type: variables.mapRoots?.[path] ?? null });
      else leaf(group, path, path, 0);
    }
    for (const path of (variables.handlePaths ?? []).filter(item => item.toLowerCase().includes(needle))) leaf('handle', path, `${path}*`, 0, { type: 'PROTECTED_HANDLE' });
    return nodes;
  }
  const folders = new Set<string>();
  const folder = (group: VariableNode['group'], path: string, label: string, depth: number) => {
    if (folders.has(`${group}:${path}`)) return;
    folders.add(`${group}:${path}`);
    const own = variables.paths.includes(path);
    nodes.push({ key: `${group}:${path}/`, label, depth, folder: true, group, insert: own ? path : null, type: own ? variableType(path, variables) : null });
  };
  const inputs = variables.paths.filter(path => path.startsWith('input.'));
  for (const path of inputs) {
    const parts = path.split('.');
    for (let depth = 0; depth < parts.length - 1; depth++) folder('input', parts.slice(0, depth + 1).join('.'), parts[depth], depth);
    if (inputs.some(other => other.startsWith(`${path}.`))) folder('input', path, parts[parts.length - 1], parts.length - 1);
    else leaf('input', path, parts[parts.length - 1], parts.length - 1);
  }
  for (const path of variables.paths.filter(item => item.startsWith('resources.'))) {
    const parts = path.split('.');
    folder('resources', parts.slice(0, 2).join('.'), parts.slice(0, 2).join('.'), 0);
    leaf('resources', `${path}.`, `${parts.slice(2).join('.')}.<名称>`, 1, { runtime: true, type: variables.mapRoots?.[path] ?? null });
  }
  for (const path of variables.paths.filter(item => item.startsWith('steps.'))) {
    const parts = path.split('.');
    folder('steps', parts.slice(0, 2).join('.'), parts.slice(0, 2).join('.'), 0);
    leaf('steps', path, parts.slice(2).join('.'), 1);
  }
  for (const path of variables.handlePaths ?? []) leaf('handle', path, `${path}*`, 0, { type: 'PROTECTED_HANDLE' });
  return nodes;
}
