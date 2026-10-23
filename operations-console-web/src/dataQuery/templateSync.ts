import { emptyColumnRow, emptyParameterRow, nextRowKey, templateLimits } from './templateModel.ts';
import type { ColumnRow, ParameterRow, ParameterType } from './templateModel.ts';

/**
 * 正文 → 参数 / 列的即时同步（编辑体验用，不是校验）：只做轻量识别，最终以服务端校验为准。
 * 自动添加的行带 auto 标记；用户改过的行不再是 auto，之后只提示“未引用”，不会被自动删除。
 */

export interface TemplateReference { name: string; type: Extract<ParameterType, 'STRING' | 'LIST' | 'FRAGMENT'> }

const identifier = '[A-Za-z_][A-Za-z0-9_]*';
const commentPattern = /\{\{!--[\s\S]*?--\}\}/g;
const escapePattern = /\\(?:#\{|@\{|\{\{)/g;
const valuePattern = new RegExp(`#\\{\\s*(${identifier})`, 'g');
const fragmentPattern = new RegExp(`@\\{\\s*(${identifier})\\s*\\}`, 'g');
const eachPattern = new RegExp(`\\{\\{#each\\s+(${identifier})\\s+as\\s+(${identifier})(?:\\s*,\\s*(${identifier}))?`, 'g');
const conditionPattern = /\{\{#(?:else)?if\s+([^}]*)\}\}/g;
const listValuePattern = new RegExp(`\\bIN\\s*\\(\\s*#\\{\\s*(${identifier})\\s*\\}\\s*\\)`, 'gi');
const conditionWords = new Set(['true', 'false', 'null']);

/** 正文引用的调用方参数（按首次出现排序）：#{} 值、@{} 片段、if 条件与 each 集合；循环变量与 __ 系统参数不算。 */
export function templateReferences(body: string): TemplateReference[] {
  const text = body.replace(commentPattern, ' ').replace(escapePattern, ' ');
  const locals = new Set<string>(); const lists = new Set<string>(); const fragments = new Set<string>();
  const found: { name: string; at: number }[] = [];
  for (const match of text.matchAll(eachPattern)) {
    lists.add(match[1]); found.push({ name: match[1], at: match.index });
    locals.add(match[2]); if (match[3]) locals.add(match[3]);
  }
  for (const match of text.matchAll(listValuePattern)) lists.add(match[1]);
  for (const match of text.matchAll(fragmentPattern)) { fragments.add(match[1]); found.push({ name: match[1], at: match.index }); }
  for (const match of text.matchAll(valuePattern)) found.push({ name: match[1], at: match.index });
  for (const match of text.matchAll(conditionPattern)) {
    const expression = match[1].replace(/'[^']*'|"[^"]*"/g, ' ');
    for (const word of expression.matchAll(new RegExp(`(?<![\\w.])(${identifier})`, 'g'))) {
      if (!conditionWords.has(word[1])) found.push({ name: word[1], at: match.index });
    }
  }
  const seen = new Set<string>(); const references: TemplateReference[] = [];
  for (const { name } of found.sort((a, b) => a.at - b.at)) {
    if (seen.has(name) || locals.has(name) || name.startsWith('__')) continue;
    seen.add(name);
    references.push({ name, type: fragments.has(name) ? 'FRAGMENT' : lists.has(name) ? 'LIST' : 'STRING' });
  }
  return references;
}

function autoParameterRow(reference: TemplateReference): ParameterRow {
  return { ...emptyParameterRow(reference.name), type: reference.type, elementType: reference.type === 'LIST' ? 'STRING' : null, auto: true,
    choices: reference.type === 'FRAGMENT' ? [{ key: nextRowKey(), value: '', fragment: '' }] : [] };
}

/** 按正文同步参数：补上新引用的参数；删除不再引用的自动行；自动行随用法调整类型。没有变化时返回原数组。 */
export function syncParameters(body: string, rows: readonly ParameterRow[]): ParameterRow[] {
  const references = templateReferences(body);
  const byName = new Map(references.map(reference => [reference.name, reference]));
  let changed = false;
  const next: ParameterRow[] = [];
  for (const row of rows) {
    const reference = byName.get(row.name.trim());
    if (row.auto && !reference) { changed = true; continue; }
    if (row.auto && reference && reference.type !== row.type) { changed = true; next.push({ ...autoParameterRow(reference), key: row.key }); continue; }
    next.push(row);
  }
  const declared = new Set(next.map(row => row.name.trim()));
  for (const reference of references) {
    if (declared.has(reference.name)) continue;
    changed = true; next.push(autoParameterRow(reference));
  }
  return changed ? next : rows as ParameterRow[];
}

/** 已声明但正文没有引用的参数名（提示用；用户手工声明的不自动删除）。 */
export function unreferencedParameters(body: string, rows: readonly ParameterRow[]): Set<string> {
  const referenced = new Set(templateReferences(body).map(reference => reference.name));
  return new Set(rows.map(row => row.name.trim()).filter(name => name && !referenced.has(name)));
}

// ---------- SELECT 列 ----------

const notAliases = new Set(['END', 'NULL', 'TRUE', 'FALSE', 'DESC', 'ASC', 'THEN', 'ELSE', 'AND', 'OR', 'NOT', 'IS', 'IN', 'FROM', 'DISTINCT']);
const selectModifiers = /^(?:(?:DISTINCT|ALL|DISTINCTROW|HIGH_PRIORITY|STRAIGHT_JOIN|SQL_[A-Z_]+)\s+)*/i;

/** 去掉模板标记与 SQL 注释：标签换成空格，值换成 ?，片段换成占位标识符。 */
function plainSql(body: string): string {
  return body.replace(commentPattern, ' ').replace(escapePattern, ' ').replace(/\{\{[^}]*\}\}/g, ' ')
    .replace(/#\{[^}\n]*\}/g, '?').replace(/@\{[^}\n]*\}/g, ' __fragment ')
    .replace(/--[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');
}

/** 顶层 SELECT 与 FROM 之间的选择项（按顶层逗号切分）；找不到 SELECT 时为 null。 */
function selectItems(sql: string): string[] | null {
  const start = /\bSELECT\b/i.exec(sql);
  if (!start) return null;
  const items: string[] = []; let depth = 0; let quote: string | null = null; let current = '';
  for (let i = start.index + start[0].length; i < sql.length; i++) {
    const char = sql[i];
    if (quote) { current += char; if (char === quote) quote = null; continue; }
    if (char === '\'' || char === '"' || char === '`') { quote = char; current += char; continue; }
    if (char === '(') depth++;
    if (char === ')') depth--;
    if (depth === 0 && /^FROM\b/i.test(sql.slice(i)) && /\W/.test(sql[i - 1] ?? ' ')) break;
    if (depth === 0 && char === ',') { items.push(current); current = ''; continue; }
    current += char;
  }
  items.push(current);
  const trimmed = items.map(item => item.trim());
  trimmed[0] = trimmed[0].replace(selectModifiers, '');
  return trimmed.filter(Boolean);
}

/** 一个选择项的结果列名：AS 别名、隐式别名或列引用的最后一段；* 与无别名表达式返回 null。 */
function itemColumn(item: string): string | null {
  const unquote = (name: string) => name.replace(/^[`"]|[`"]$/g, '');
  const alias = new RegExp(`\\s+AS\\s+([\`"]?${identifier}[\`"]?)$`, 'i').exec(item);
  if (alias) return unquote(alias[1]);
  const reference = new RegExp(`^(?:[\`"]?${identifier}[\`"]?\\.)*([\`"]?${identifier}[\`"]?)$`).exec(item);
  if (reference) return unquote(reference[1]);
  const implicit = new RegExp(`[\\w)\`"'?]\\s+([\`"]?${identifier}[\`"]?)$`).exec(item);
  if (implicit && !notAliases.has(unquote(implicit[1]).toUpperCase())) return unquote(implicit[1]);
  return null;
}

/** 正文顶层 SELECT 的结果列名；complete 为 false 表示有 * 或无法识别的表达式（此时不自动删除列）。 */
export function selectColumns(body: string): { names: string[]; complete: boolean } | null {
  const items = selectItems(plainSql(body));
  if (!items) return null;
  const names: string[] = []; let complete = items.length > 0;
  for (const item of items) {
    const name = item.endsWith('*') ? null : itemColumn(item);
    if (name && name !== '__fragment' && !names.includes(name)) names.push(name); else if (!name || name === '__fragment') complete = false;
  }
  return { names, complete };
}

const autoColumnRow = (name: string): ColumnRow => ({ ...emptyColumnRow(), name, auto: true });

function appendColumns(rows: ColumnRow[], names: readonly string[]): boolean {
  const declared = new Set(rows.map(row => row.name.trim()));
  let changed = false;
  for (const name of names) {
    if (declared.has(name) || rows.length >= templateLimits.maxColumns) continue;
    rows.push(autoColumnRow(name)); declared.add(name); changed = true;
  }
  return changed;
}

/** 按正文同步列：补上新出现的结果列；识别完整时删除不再出现的自动行。没有变化时返回原数组。 */
export function syncColumns(body: string, rows: readonly ColumnRow[]): ColumnRow[] {
  const select = selectColumns(body);
  if (!select) return rows as ColumnRow[];
  const present = new Set(select.names);
  const next = rows.filter(row => !(row.auto && select.complete && !present.has(row.name.trim())));
  const removed = next.length !== rows.length;
  return appendColumns(next, select.names) || removed ? next : rows as ColumnRow[];
}

/** 试运行返回的列补进列声明（服务端结果最准确，例如无别名表达式的列名）。没有变化时返回原数组。 */
export function mergeResultColumns(rows: readonly ColumnRow[], names: readonly string[]): ColumnRow[] {
  const next = [...rows];
  return appendColumns(next, names) ? next : rows as ColumnRow[];
}
