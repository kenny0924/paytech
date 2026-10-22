import { maskLabels, pagingModeLabels, typeLabel } from './templateModel.ts';
import type { ParameterDeclaration, QueryColumn, QueryConstraints, TemplateVersion } from './templateModel.ts';

export interface DiffLine { kind: 'same' | 'removed' | 'added'; text: string; leftNo: number | null; rightNo: number | null }

/** LCS 表上限：超过时只对齐公共前后缀，中间整段按删除 + 新增展示。 */
const maxCells = 4_000_000;

/**
 * 正文逐行对比（最长公共子序列）：返回按显示顺序排列的行，删除行只有左行号，新增行只有右行号。
 * 先剥离公共前后缀，降低大正文的计算量。
 */
export function lineDiff(left: string, right: string): DiffLine[] {
  const a = left.split('\n'); const b = right.split('\n');
  let prefix = 0;
  while (prefix < a.length && prefix < b.length && a[prefix] === b[prefix]) prefix++;
  let suffix = 0;
  while (suffix < a.length - prefix && suffix < b.length - prefix && a[a.length - 1 - suffix] === b[b.length - 1 - suffix]) suffix++;
  const midA = a.slice(prefix, a.length - suffix); const midB = b.slice(prefix, b.length - suffix);
  const result: DiffLine[] = a.slice(0, prefix).map((text, i) => ({ kind: 'same', text, leftNo: i + 1, rightNo: i + 1 }));
  for (const line of middleDiff(midA, midB)) {
    result.push({ ...line, leftNo: line.leftNo === null ? null : line.leftNo + prefix, rightNo: line.rightNo === null ? null : line.rightNo + prefix });
  }
  a.slice(a.length - suffix).forEach((text, i) => {
    result.push({ kind: 'same', text, leftNo: a.length - suffix + i + 1, rightNo: b.length - suffix + i + 1 });
  });
  return result;
}

function middleDiff(a: string[], b: string[]): DiffLine[] {
  if ((a.length + 1) * (b.length + 1) > maxCells) {
    return [...a.map((text, i) => ({ kind: 'removed' as const, text, leftNo: i + 1, rightNo: null })),
      ...b.map((text, j) => ({ kind: 'added' as const, text, leftNo: null, rightNo: j + 1 }))];
  }
  const width = b.length + 1;
  const table = new Uint32Array((a.length + 1) * width);
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      table[i * width + j] = a[i] === b[j] ? table[(i + 1) * width + j + 1] + 1 : Math.max(table[(i + 1) * width + j], table[i * width + j + 1]);
    }
  }
  const lines: DiffLine[] = []; let i = 0; let j = 0;
  while (i < a.length || j < b.length) {
    if (i < a.length && j < b.length && a[i] === b[j]) { lines.push({ kind: 'same', text: a[i], leftNo: i + 1, rightNo: j + 1 }); i++; j++; }
    else if (i < a.length && (j === b.length || table[(i + 1) * width + j] >= table[i * width + j + 1])) {
      lines.push({ kind: 'removed', text: a[i], leftNo: i + 1, rightNo: null }); i++;
    } else { lines.push({ kind: 'added', text: b[j], leftNo: null, rightNo: j + 1 }); j++; }
  }
  return lines;
}

/** 左右并排视图：同一行号对齐，删除行右侧留空、新增行左侧留空。 */
export function sideBySide(lines: readonly DiffLine[]): { left: DiffLine | null; right: DiffLine | null }[] {
  const rows: { left: DiffLine | null; right: DiffLine | null }[] = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index];
    if (line.kind === 'same') { rows.push({ left: line, right: line }); index++; continue; }
    const removed: DiffLine[] = []; const added: DiffLine[] = [];
    while (index < lines.length && lines[index].kind !== 'same') {
      (lines[index].kind === 'removed' ? removed : added).push(lines[index]); index++;
    }
    for (let k = 0; k < Math.max(removed.length, added.length); k++) rows.push({ left: removed[k] ?? null, right: added[k] ?? null });
  }
  return rows;
}

export interface DeclarationChange { subject: string; change: string }

const stable = (value: unknown) => JSON.stringify(value, (_, v: unknown) => v && typeof v === 'object' && !Array.isArray(v)
  ? Object.fromEntries(Object.entries(v as Record<string, unknown>).filter(([, item]) => item !== null && item !== undefined).sort(([x], [y]) => x.localeCompare(y)))
  : v);

function parameterChanges(before: ParameterDeclaration, after: ParameterDeclaration): string[] {
  const changes: string[] = [];
  const typeBefore = typeLabel(before.type, before.elementType); const typeAfter = typeLabel(after.type, after.elementType);
  if (typeBefore !== typeAfter) changes.push(`类型 ${typeBefore} → ${typeAfter}`);
  if ((before.required === true) !== (after.required === true)) changes.push(after.required ? '改为必填' : '改为可选');
  if (stable(before.default) !== stable(after.default)) changes.push(`默认值 ${stable(before.default) ?? '无'} → ${stable(after.default) ?? '无'}`);
  const choicesBefore = before.choices ?? {}; const choicesAfter = after.choices ?? {};
  for (const [key, fragment] of Object.entries(choicesAfter)) {
    if (!(key in choicesBefore)) changes.push(`片段取值新增 ${key} → ${fragment}`);
    else if (choicesBefore[key] !== fragment) changes.push(`片段 ${key}：${choicesBefore[key]} → ${fragment}`);
  }
  for (const key of Object.keys(choicesBefore)) if (!(key in choicesAfter)) changes.push(`片段取值删除 ${key}`);
  for (const key of ['maxLength', 'min', 'max', 'maxSize', 'allowedValues', 'fields'] as const) {
    if (stable(before[key]) !== stable(after[key])) changes.push(`${key} ${stable(before[key]) ?? '无'} → ${stable(after[key]) ?? '无'}`);
  }
  return changes;
}

const constraintLabels: Readonly<Record<keyof QueryConstraints, string>> = {
  maxRows: '最大行数', timeoutSeconds: '超时', pagingMode: '分页方式', maxPageSize: '页大小上限', countAllowed: '允许总数', cursorColumns: '游标列',
};
function constraintText(key: keyof QueryConstraints, value: unknown): string {
  if (value === null || value === undefined) return '无';
  if (key === 'timeoutSeconds') return `${String(value)} s`;
  if (key === 'pagingMode') return pagingModeLabels[value as QueryConstraints['pagingMode']] ?? String(value);
  if (key === 'countAllowed') return value ? '是' : '否';
  if (Array.isArray(value)) return value.join(', ') || '无';
  return String(value);
}
const columnText = (column: QueryColumn) => `${column.label || column.name} · ${maskLabels[column.mask ?? 'NONE']}`
  + (column.mask === 'KEEP_EDGES' ? `（首 ${column.keepStart ?? 0} 尾 ${column.keepEnd ?? 0}）` : '');

/** 参数 / 约束 / 列 / 数据源的差异（before → after），按参数、约束、列、数据源顺序。 */
export function declarationChanges(before: TemplateVersion, after: TemplateVersion): DeclarationChange[] {
  const result: DeclarationChange[] = [];
  const beforeParams = new Map(before.parameters.map(p => [p.name, p])); const afterParams = new Map(after.parameters.map(p => [p.name, p]));
  for (const [name, declaration] of afterParams) {
    const previous = beforeParams.get(name);
    if (!previous) result.push({ subject: `参数 ${name}`, change: `新增 ${typeLabel(declaration.type, declaration.elementType)}${declaration.required ? '（必填）' : ''}` });
    else parameterChanges(previous, declaration).forEach(change => result.push({ subject: `参数 ${name}`, change }));
  }
  for (const name of beforeParams.keys()) if (!afterParams.has(name)) result.push({ subject: `参数 ${name}`, change: '删除' });
  const beforeOrder = before.parameters.map(p => p.name).filter(name => afterParams.has(name));
  const afterOrder = after.parameters.map(p => p.name).filter(name => beforeParams.has(name));
  if (beforeOrder.join('\n') !== afterOrder.join('\n')) result.push({ subject: '参数顺序', change: `${beforeOrder.join(', ')} → ${afterOrder.join(', ')}` });
  for (const key of Object.keys(constraintLabels) as (keyof QueryConstraints)[]) {
    const x = constraintText(key, before.constraints[key]); const y = constraintText(key, after.constraints[key]);
    if (x !== y) result.push({ subject: `约束 ${constraintLabels[key]}`, change: `${x} → ${y}` });
  }
  const beforeColumns = new Map(before.columns.map(c => [c.name, c])); const afterColumns = new Map(after.columns.map(c => [c.name, c]));
  for (const [name, column] of afterColumns) {
    const previous = beforeColumns.get(name);
    if (!previous) result.push({ subject: `列 ${name}`, change: `新增 ${columnText(column)}` });
    else if (columnText(previous) !== columnText(column)) result.push({ subject: `列 ${name}`, change: `${columnText(previous)} → ${columnText(column)}` });
  }
  for (const name of beforeColumns.keys()) if (!afterColumns.has(name)) result.push({ subject: `列 ${name}`, change: '删除' });
  if (before.dataSourceId !== after.dataSourceId) result.push({ subject: '数据源', change: `${before.dataSourceCode} → ${after.dataSourceCode}` });
  return result;
}
