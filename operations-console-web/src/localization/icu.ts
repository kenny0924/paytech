/**
 * ICU MessageFormat 的前端即时解析（technique 3.4，需求 B5）：给编辑器高亮、即时校验、参数改名一键修正与预览示例值用。
 * 规则与 Platform IcuMessageValidator 一致（撇号按 DOUBLE_OPTIONAL：'' 为撇号，'{ '} '# 开始转义）：语法可解析；
 * plural / select / selectordinal 必须有 other；简单参数类型只允许 number、date、time，不支持 choice；参数名集合与英文基准一致
 * （plural 的 # 不算参数）。后端结果为准，这里只做提示。问题只带 code 与参数，界面文案由调用方经 t() 生成。
 */

export type TokenKind = 'text' | 'brace' | 'argument' | 'punct' | 'keyword' | 'selector' | 'pound' | 'style';
export interface Token { kind: TokenKind; start: number; end: number }
export type ArgumentType = 'text' | 'number' | 'date' | 'time' | 'plural' | 'selectordinal' | 'select';
export interface ArgumentInfo { name: string; type: ArgumentType; start: number; end: number; selectors: string[] }

export type IssueCode =
  | 'empty' | 'unclosed_brace' | 'unmatched_close' | 'bad_argument' | 'missing_fragment'
  | 'unsupported_type' | 'choice_unsupported' | 'missing_other'
  | 'missing_parameter' | 'extra_parameter' | 'renamed_parameter';
export interface IcuIssue { code: IssueCode; start: number; end: number; params: Record<string, string> }

export interface ParsedIcu {
  tokens: Token[];
  /** 参数（含嵌套分支内），按首次出现排列；同名只记第一次。 */
  arguments: ArgumentInfo[];
  issues: IcuIssue[];
}

const SELECTABLE = new Set(['plural', 'selectordinal', 'select']);
const SIMPLE = new Set(['number', 'date', 'time']);
const isSpace = (char: string | undefined) => char !== undefined && /\s/.test(char);

/** 解析一段文案；永不抛错，问题进入 issues。 */
export function parseIcu(text: string): ParsedIcu {
  const tokens: Token[] = []; const issues: IcuIssue[] = []; const args: ArgumentInfo[] = [];
  let pos = 0;
  const push = (kind: TokenKind, start: number, end: number) => {
    if (end <= start) return;
    const last = tokens.at(-1);
    if (last && last.kind === kind && last.end === start && (kind === 'text' || kind === 'style')) last.end = end;
    else tokens.push({ kind, start, end });
  };
  const issue = (code: IssueCode, start: number, end: number, params: Record<string, string> = {}) => {
    issues.push({ code, start, end: Math.max(end, start + 1), params });
  };
  const skipSpace = () => { const start = pos; while (isSpace(text[pos])) pos++; push('text', start, pos); };
  const identifier = () => { const start = pos; while (pos < text.length && !isSpace(text[pos]) && !',{}#'.includes(text[pos])) pos++; return text.slice(start, pos); };

  /** 引号转义：返回已消费到的位置。 */
  function quoted(inPlural: boolean): boolean {
    if (text[pos] !== "'") return false;
    const next = text[pos + 1];
    if (next === "'") { push('text', pos, pos + 2); pos += 2; return true; }
    if (next === '{' || next === '}' || (inPlural && next === '#') || next === '|') {
      const start = pos; pos += 2;
      while (pos < text.length) {
        if (text[pos] === "'" && text[pos + 1] === "'") { pos += 2; continue; }
        if (text[pos] === "'") { pos++; break; }
        pos++;
      }
      push('text', start, pos); return true;
    }
    return false;
  }

  /** 读到本层结束：顶层到文末，嵌套层到 '}'（不消费）。 */
  function message(depth: number, inPlural: boolean): void {
    while (pos < text.length) {
      const char = text[pos];
      if (quoted(inPlural)) continue;
      if (char === '{') { argument(depth); continue; }
      if (char === '}') {
        if (depth > 0) return;
        issue('unmatched_close', pos, pos + 1); push('brace', pos, pos + 1); pos++; continue;
      }
      if (char === '#' && inPlural) { push('pound', pos, pos + 1); pos++; continue; }
      push('text', pos, pos + 1); pos++;
    }
  }

  /** 跳到与 open 匹配的 '}' 之后；没有时到文末并报未闭合。 */
  function skipToClose(open: number, kind: TokenKind = 'style'): boolean {
    let level = 0; const start = pos;
    while (pos < text.length) {
      if (text[pos] === '{') level++;
      else if (text[pos] === '}') {
        if (level === 0) { push(kind, start, pos); push('brace', pos, pos + 1); pos++; return true; }
        level--;
      }
      pos++;
    }
    push(kind, start, pos); issue('unclosed_brace', open, open + 1); return false;
  }

  function argument(depth: number): void {
    const open = pos; push('brace', pos, pos + 1); pos++;
    skipSpace();
    const nameStart = pos; const name = identifier(); const nameEnd = pos;
    if (!name) {
      issue('bad_argument', open, Math.max(pos, open + 1));
      skipToClose(open, 'text'); return;
    }
    push('argument', nameStart, nameEnd);
    skipSpace();
    const record = (type: ArgumentType, selectors: string[] = []) => {
      if (!args.some(item => item.name === name)) args.push({ name, type, start: nameStart, end: nameEnd, selectors });
    };
    if (pos >= text.length) { record('text'); issue('unclosed_brace', open, open + 1); return; }
    if (text[pos] === '}') { record('text'); push('brace', pos, pos + 1); pos++; return; }
    if (text[pos] !== ',') { record('text'); issue('bad_argument', nameStart, pos + 1); skipToClose(open, 'text'); return; }
    push('punct', pos, pos + 1); pos++; skipSpace();
    const typeStart = pos; const type = identifier().trim();
    push('keyword', typeStart, pos);
    skipSpace();
    if (SELECTABLE.has(type)) { select(open, type as ArgumentType, record, depth); return; }
    record(SIMPLE.has(type) ? type as ArgumentType : 'text');
    if (type === 'choice') issue('choice_unsupported', open, typeStart + type.length);
    else if (!SIMPLE.has(type)) issue(type ? 'unsupported_type' : 'bad_argument', typeStart, Math.max(pos, typeStart + 1), { type });
    if (text[pos] === ',') { push('punct', pos, pos + 1); pos++; }
    else if (text[pos] !== '}' && pos < text.length) issue('bad_argument', pos, pos + 1);
    skipToClose(open);
  }

  /** plural 分支内 # 为数字占位；嵌套的 select 分支内 # 是普通字符（与 ICU4J 一致）。 */
  function select(open: number, type: ArgumentType, record: (type: ArgumentType, selectors: string[]) => void, depth: number): void {
    const selectors: string[] = [];
    record(type, selectors);
    if (text[pos] !== ',') { issue('bad_argument', pos, pos + 1); skipToClose(open, 'text'); return; }
    push('punct', pos, pos + 1); pos++;
    const nested = type === 'plural' || type === 'selectordinal';
    while (true) {
      skipSpace();
      if (pos >= text.length) { issue('unclosed_brace', open, open + 1); break; }
      if (text[pos] === '}') { push('brace', pos, pos + 1); pos++; break; }
      const selectorStart = pos; const selector = identifier();
      if (!selector) { issue('bad_argument', pos, pos + 1); skipToClose(open, 'text'); return; }
      if (nested && selector.startsWith('offset:') && selectors.length === 0) {
        push('keyword', selectorStart, pos); continue;
      }
      push('selector', selectorStart, pos);
      selectors.push(selector);
      skipSpace();
      if (text[pos] !== '{') { issue('missing_fragment', selectorStart, pos); skipToClose(open, 'text'); return; }
      const fragmentOpen = pos; push('brace', pos, pos + 1); pos++;
      message(depth + 1, nested);
      if (text[pos] !== '}') { issue('unclosed_brace', fragmentOpen, fragmentOpen + 1); issue('unclosed_brace', open, open + 1); return; }
      push('brace', pos, pos + 1); pos++;
    }
    if (!selectors.includes('other')) issue('missing_other', open, open + 1, { type });
  }

  message(0, false);
  return { tokens, arguments: args, issues };
}

/** 文案中的参数名，按首次出现排列；有语法问题时也尽量返回。 */
export const parameterNames = (text: string): string[] => parseIcu(text).arguments.map(item => item.name);

/**
 * 校验翻译：空白、语法与结构问题；没有语法问题且基准可解析时比较参数名集合。
 * 恰好缺一个、多一个参数时合并为一条“参数被改名”，便于一键修正。
 */
export function validateTranslation(source: string, translation: string): IcuIssue[] {
  if (!translation.trim()) return [{ code: 'empty', start: 0, end: 0, params: {} }];
  const target = parseIcu(translation);
  if (target.issues.length) return target.issues;
  const base = parseIcu(source);
  if (base.issues.length) return [];
  const expected = new Set(base.arguments.map(item => item.name));
  const actual = new Map(target.arguments.map(item => [item.name, item]));
  const missing = [...expected].filter(name => !actual.has(name));
  const extra = target.arguments.filter(item => !expected.has(item.name));
  if (missing.length === 1 && extra.length === 1) {
    return [{ code: 'renamed_parameter', start: extra[0].start, end: extra[0].end, params: { expected: missing[0], actual: extra[0].name } }];
  }
  return [
    ...missing.map(name => ({ code: 'missing_parameter' as const, start: 0, end: 0, params: { name } })),
    ...extra.map(item => ({ code: 'extra_parameter' as const, start: item.start, end: item.end, params: { name: item.name } })),
  ];
}

/** 参数改名的一键修正：把翻译中所有该名称的参数引用改回基准名；不适用时返回 null。 */
export function renameFix(translation: string, issues: IcuIssue[]): { expected: string; actual: string; text: string } | null {
  const rename = issues.find(item => item.code === 'renamed_parameter');
  if (!rename) return null;
  const { expected, actual } = rename.params;
  const targets = parseIcu(translation).tokens.filter(token => token.kind === 'argument' && translation.slice(token.start, token.end) === actual);
  let text = translation;
  for (const token of targets.toReversed()) text = text.slice(0, token.start) + expected + text.slice(token.end);
  return { expected, actual, text };
}

export interface Segment { kind: TokenKind; text: string; error: boolean }
/** 高亮片段：按 token 与问题范围切分，问题范围内标 error（编辑器下划线）。 */
export function highlight(text: string, issues: IcuIssue[] = []): Segment[] {
  const { tokens } = parseIcu(text);
  const marks = issues.filter(item => item.end > item.start);
  const cuts = new Set<number>([0, text.length]);
  for (const token of tokens) { cuts.add(token.start); cuts.add(token.end); }
  for (const mark of marks) { cuts.add(Math.min(mark.start, text.length)); cuts.add(Math.min(mark.end, text.length)); }
  const points = [...cuts].sort((left, right) => left - right);
  const segments: Segment[] = [];
  for (let index = 0; index < points.length - 1; index++) {
    const start = points[index], end = points[index + 1];
    if (end <= start) continue;
    const kind = tokens.find(token => token.start <= start && token.end >= end)?.kind ?? 'text';
    const error = marks.some(mark => mark.start <= start && mark.end >= end);
    const last = segments.at(-1);
    const piece = text.slice(start, end);
    if (last && last.kind === kind && last.error === error) last.text += piece;
    else segments.push({ kind, text: piece, error });
  }
  return segments;
}

export type SampleValues = Record<string, string>;
/**
 * 预览示例：第一个 plural / selectordinal 参数取 1、5、0；否则第一个 select 参数取各分支（other 用一个不在分支内的值）；
 * 其他参数取默认示例（文本为参数名，数字为 3，日期与时间为当前时间）。没有参数时一行空示例。
 */
export function sampleRows(source: string): SampleValues[] {
  const args = parseIcu(source).arguments;
  const defaults: SampleValues = Object.fromEntries(args.map(item => [item.name, defaultSample(item)]));
  const driver = args.find(item => item.type === 'plural' || item.type === 'selectordinal') ?? args.find(item => item.type === 'select');
  if (!driver) return [defaults];
  const values = driver.type === 'select'
    ? [...driver.selectors.filter(selector => selector !== 'other'), 'other']
    : ['1', '5', '0'];
  return values.map(value => ({ ...defaults, [driver.name]: value }));
}
function defaultSample(item: ArgumentInfo): string {
  if (item.type === 'plural' || item.type === 'selectordinal' || item.type === 'number') return '3';
  if (item.type === 'date' || item.type === 'time') return new Date().toISOString();
  if (item.type === 'select') return item.selectors.find(selector => selector !== 'other') ?? 'other';
  return item.name;
}

/** 示例值转换为格式化参数：数字类转数字，日期时间转 Date，其余原样。 */
export function formatValues(source: string, values: SampleValues): Record<string, string | number | Date> {
  const types = new Map(parseIcu(source).arguments.map(item => [item.name, item.type]));
  return Object.fromEntries(Object.entries(values).map(([name, value]) => {
    const type = types.get(name);
    if (type === 'plural' || type === 'selectordinal' || type === 'number') {
      const number = Number(value);
      return [name, Number.isFinite(number) ? number : 0];
    }
    if (type === 'date' || type === 'time') {
      const date = new Date(value);
      return [name, Number.isFinite(date.getTime()) ? date : new Date()];
    }
    return [name, value];
  }));
}
