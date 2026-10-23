import { maskLabel, pagingModeLabel, typeLabel } from './templateModel.ts';
import type { ParameterDeclaration, QueryColumn, QueryConstraints, TemplateVersion } from './templateModel.ts';
import { translator } from '../i18n/index.ts';

const t = translator('data-query');

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
  const none = t('diff.none');
  if (typeBefore !== typeAfter) changes.push(t('diff.type', { before: typeBefore, after: typeAfter }));
  if ((before.required === true) !== (after.required === true)) changes.push(after.required ? t('diff.nowRequired') : t('diff.nowOptional'));
  if (stable(before.default) !== stable(after.default)) changes.push(t('diff.default', { before: stable(before.default) ?? none, after: stable(after.default) ?? none }));
  const choicesBefore = before.choices ?? {}; const choicesAfter = after.choices ?? {};
  for (const [key, fragment] of Object.entries(choicesAfter)) {
    if (!(key in choicesBefore)) changes.push(t('diff.choiceAdded', { key, fragment }));
    else if (choicesBefore[key] !== fragment) changes.push(t('diff.choiceChanged', { key, before: choicesBefore[key], after: fragment }));
  }
  for (const key of Object.keys(choicesBefore)) if (!(key in choicesAfter)) changes.push(t('diff.choiceRemoved', { key }));
  for (const key of ['maxLength', 'min', 'max', 'maxSize', 'allowedValues', 'fields'] as const) {
    if (stable(before[key]) !== stable(after[key])) changes.push(`${key} ${stable(before[key]) ?? none} → ${stable(after[key]) ?? none}`);
  }
  return changes;
}

/** 参与比较的约束，按展示顺序；名称为 `diff.constraint.<键>`。 */
const constraintKeys: readonly (keyof QueryConstraints)[] = ['maxRows', 'timeoutSeconds', 'pagingMode', 'maxPageSize', 'countAllowed', 'cursorColumns'];
function constraintText(key: keyof QueryConstraints, value: unknown): string {
  if (value === null || value === undefined) return t('diff.none');
  if (key === 'timeoutSeconds') return `${String(value)} s`;
  if (key === 'pagingMode') return pagingModeLabel(String(value));
  if (key === 'countAllowed') return value ? t('diff.yes') : t('diff.no');
  if (Array.isArray(value)) return value.join(', ') || t('diff.none');
  return String(value);
}
const columnText = (column: QueryColumn) => column.mask === 'KEEP_EDGES'
  ? t('diff.columnKeepEdges', { label: column.label || column.name, mask: maskLabel(column.mask), start: column.keepStart ?? 0, end: column.keepEnd ?? 0 })
  : `${column.label || column.name} · ${maskLabel(column.mask ?? 'NONE')}`;

/** 参数 / 约束 / 列 / 数据源的差异（before → after），按参数、约束、列、数据源顺序。 */
export function declarationChanges(before: TemplateVersion, after: TemplateVersion): DeclarationChange[] {
  const result: DeclarationChange[] = [];
  const beforeParams = new Map(before.parameters.map(p => [p.name, p])); const afterParams = new Map(after.parameters.map(p => [p.name, p]));
  for (const [name, declaration] of afterParams) {
    const previous = beforeParams.get(name);
    const subject = t('diff.parameter', { name });
    if (!previous) result.push({ subject, change: declaration.required ? t('diff.addedRequired', { type: typeLabel(declaration.type, declaration.elementType) })
      : t('diff.added', { item: typeLabel(declaration.type, declaration.elementType) }) });
    else parameterChanges(previous, declaration).forEach(change => result.push({ subject, change }));
  }
  for (const name of beforeParams.keys()) if (!afterParams.has(name)) result.push({ subject: t('diff.parameter', { name }), change: t('diff.removed') });
  const beforeOrder = before.parameters.map(p => p.name).filter(name => afterParams.has(name));
  const afterOrder = after.parameters.map(p => p.name).filter(name => beforeParams.has(name));
  if (beforeOrder.join('\n') !== afterOrder.join('\n')) result.push({ subject: t('diff.parameterOrder'), change: `${beforeOrder.join(', ')} → ${afterOrder.join(', ')}` });
  for (const key of constraintKeys) {
    const x = constraintText(key, before.constraints[key]); const y = constraintText(key, after.constraints[key]);
    if (x !== y) result.push({ subject: t('diff.constraint', { name: t('diff.constraintName.' + key) }), change: `${x} → ${y}` });
  }
  const beforeColumns = new Map(before.columns.map(c => [c.name, c])); const afterColumns = new Map(after.columns.map(c => [c.name, c]));
  for (const [name, column] of afterColumns) {
    const previous = beforeColumns.get(name);
    if (!previous) result.push({ subject: t('diff.column', { name }), change: t('diff.added', { item: columnText(column) }) });
    else if (columnText(previous) !== columnText(column)) result.push({ subject: t('diff.column', { name }), change: `${columnText(previous)} → ${columnText(column)}` });
  }
  for (const name of beforeColumns.keys()) if (!afterColumns.has(name)) result.push({ subject: t('diff.column', { name }), change: t('diff.removed') });
  if (before.dataSourceId !== after.dataSourceId) result.push({ subject: t('diff.dataSource'), change: `${before.dataSourceCode} → ${after.dataSourceCode}` });
  return result;
}
