import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { IntlMessageFormat } from 'intl-messageformat';
import { builtin } from './i18n/builtin.ts';
import { OPS_NAMESPACES } from './i18n/namespaces.ts';
import { sourceFiles, withoutComments } from './i18nSource.ts';
import { pageCapabilities } from './navigation/pageCapabilities.ts';

/**
 * Catalog consistency for every Operations namespace: English base (src/i18n/en) against the zh-CN seed
 * (i18n/zh-CN), ICU validity, keys referenced by code, and `nav.resource.*` coverage.
 */
const src = fileURLToPath(new URL('.', import.meta.url));
const zhDir = join(src, '../i18n/zh-CN');
const KEY = /^(?=.{3,191}$)[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)+$/;
const textOf = (entry: unknown) => (typeof entry === 'string' ? entry : (entry as { text: string }).text);
const zh: Record<string, Record<string, string>> = Object.fromEntries(readdirSync(zhDir).filter(name => name.endsWith('.json'))
  .map(name => [name.slice(0, -5), JSON.parse(readFileSync(join(zhDir, name), 'utf8'))]));

test('namespaces: OPS_NAMESPACES, builtin English files and zh-CN seed files are the same set', () => {
  assert.deepEqual(Object.keys(builtin).sort(), [...OPS_NAMESPACES].sort());
  assert.deepEqual(Object.keys(zh).sort(), [...OPS_NAMESPACES].sort());
  assert.deepEqual(readdirSync(join(src, 'i18n/en')).filter(name => name.endsWith('.json')).map(name => name.slice(0, -5)).sort(),
    [...OPS_NAMESPACES].sort());
});

test('en and zh-CN have exactly the same keys; keys follow the Platform rule and start with their namespace; no empty text', () => {
  const problems: string[] = [];
  for (const ns of OPS_NAMESPACES) {
    const en = builtin[ns], seed = zh[ns];
    for (const key of Object.keys(en)) {
      if (!key.startsWith(ns + '.') || !KEY.test(key)) problems.push('bad key ' + key);
      if (!textOf(en[key])?.trim()) problems.push('empty en ' + key);
      if (!(key in seed)) problems.push('no zh-CN ' + key);
      else if (!seed[key]?.trim()) problems.push('empty zh-CN ' + key);
    }
    for (const key of Object.keys(seed)) if (!(key in en)) problems.push('zh-CN only ' + key);
  }
  assert.deepEqual(problems, []);
});

type Element = { type: number; value?: string; options?: Record<string, { value: Element[] }>; children?: Element[] };
/** Argument names used by an ICU message (plural / select / tags included). */
function argumentNames(elements: Element[], into = new Set<string>()): Set<string> {
  for (const element of elements) {
    if (element.type !== 0 && element.type !== 7 && element.value) into.add(element.value);
    for (const option of Object.values(element.options ?? {})) argumentNames(option.value, into);
    if (element.children) argumentNames(element.children, into);
  }
  return into;
}

test('every text is valid ICU and en / zh-CN use the same argument names', () => {
  const problems: string[] = [];
  for (const ns of OPS_NAMESPACES) {
    for (const [key, entry] of Object.entries(builtin[ns])) {
      try {
        const en = argumentNames(new IntlMessageFormat(textOf(entry), 'en', undefined, { ignoreTag: true }).getAst() as Element[]);
        const cn = argumentNames(new IntlMessageFormat(zh[ns][key] ?? '', 'zh-CN', undefined, { ignoreTag: true }).getAst() as Element[]);
        if ([...en].sort().join() !== [...cn].sort().join()) problems.push(`${key}: en {${[...en]}} zh-CN {${[...cn]}}`);
      } catch (error) { problems.push(key + ': ' + (error as Error).message); }
    }
  }
  assert.deepEqual(problems, []);
});

/**
 * Static keys in code: inside a file, `x = useT('ns')` / `translator('ns')` binds the name `x`; each `x('key')`
 * (including both sides of `cond ? 'a' : 'b'`) resolves against the nearest preceding binding of `x`.
 * Dynamic keys (concatenation, template literals, variables) are skipped; cover them in module tests.
 */
function referencedKeys(): { file: string; key: string }[] {
  const found: { file: string; key: string }[] = [];
  for (const path of sourceFiles(src)) {
    const code = withoutComments(readFileSync(path, 'utf8'));
    const bindings = [...code.matchAll(/\b([A-Za-z_$][\w$]*)\s*=\s*(?:useT|translator)\(\s*'([a-z][a-z0-9-]*)'\s*\)/g)]
      .map(match => ({ name: match[1], ns: match[2], at: match.index }));
    if (!bindings.length) continue;
    const names = [...new Set(bindings.map(binding => binding.name))].map(name => name.replace(/\$/g, '\\$')).join('|');
    for (const call of code.matchAll(new RegExp(`(?<![\\w$.])(${names})\\(`, 'g'))) {
      const binding = bindings.filter(item => item.name === call[1] && item.at < call.index).at(-1);
      if (!binding) continue;
      let depth = 0, end = call.index + call[0].length;
      for (; end < code.length; end++) {
        const c = code[end];
        if (c === '(' || c === '[' || c === '{') depth++;
        else if (c === ')' || c === ']' || c === '}') { if (depth === 0) break; depth--; }
        else if (c === ',' && depth === 0) break;
      }
      const argument = code.slice(call.index + call[0].length, end);
      const keys = /[+`]/.test(argument) ? null : /^\s*(?:[\s\S]*\?\s*)?'([^']*)'(?:\s*:\s*'([^']*)')?\s*$/.exec(argument);
      for (const key of keys?.slice(1).filter(Boolean) ?? []) found.push({ file: relative(src, path), key: binding.ns + '.' + key });
    }
  }
  return found;
}

test('every statically referenced key exists in the English base', () => {
  const references = referencedKeys();
  assert.ok(references.length > 100, 'scanner found too few references: ' + references.length);
  const missing = references.filter(({ key }) => !Object.hasOwn(builtin[key.split('.')[0]] ?? {}, key)).map(({ file, key }) => file + ': ' + key);
  assert.deepEqual([...new Set(missing)], []);
});

/** Resources Platform registers that pageCapabilities does not list: menu directories and two Risk buttons (mor-test, 2026-10-23). */
const PLATFORM_ONLY_RESOURCES = [
  'CLIENT_ONBOARDING_REVIEW', 'OPERATIONS_CHANNEL', 'OPERATIONS_COMMERCIAL', 'OPERATIONS_DATA_QUERY', 'OPERATIONS_FINANCE',
  'OPERATIONS_FINANCE_RECON', 'OPERATIONS_I18N', 'OPERATIONS_NOTIFICATION', 'OPERATIONS_PLATFORM_CONFIGURATION',
  'OPERATIONS_RISK_MANAGEMENT', 'OPERATIONS_SYSTEM', 'OPERATIONS_VAULT', 'OPERATIONS_WORKFLOW',
  'OPERATIONS_RISK_TEST_CANDIDATE_CREATE', 'OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE',
];

test('nav.resource covers every page, page action and Platform menu directory', () => {
  const codes = [...pageCapabilities.flatMap(page => [page.componentKey, ...page.actions.map(action => action.resourceCode)]),
    ...PLATFORM_ONLY_RESOURCES];
  assert.deepEqual(codes.filter(code => !Object.hasOwn(builtin.nav, 'nav.resource.' + code)), []);
  const extra = Object.keys(builtin.nav).filter(key => key.startsWith('nav.resource.') && !codes.includes(key.slice('nav.resource.'.length)));
  assert.deepEqual(extra, []);
});
