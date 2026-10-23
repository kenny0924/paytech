import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { IntlMessageFormat } from 'intl-messageformat';
import { builtin } from '../i18n/builtin.ts';
import { parseIcu, validateTranslation } from './icu.ts';
import type { IssueCode } from './icu.ts';

const src = fileURLToPath(new URL('..', import.meta.url));
const seed = JSON.parse(readFileSync(join(src, '../i18n/operations-console.zh-CN.seed.json'), 'utf8')) as {
  format: string; application: string; locale: string; namespaces: Record<string, Record<string, string>>;
};
const cjk = /[぀-ヿ㐀-䶿一-鿿가-힯豈-﫿＀-￯　-〿]/;
const ADMIN = 'i18n-admin';

/** 扫描范围（technique 3.6、S5）：本需求页面 src/localization 与运行时 src/i18n 的 ts / tsx，排除测试。 */
function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.ts$/.test(name) ? [path] : [];
  });
}
const scanned = [...sourceFiles(join(src, 'localization')), ...sourceFiles(join(src, 'i18n'))];

/** 去掉注释，保留代码、字符串与 JSX 文本；中文注释不属于界面文案（与 client-web 相同的约定）。 */
function withoutComments(text: string): string {
  let out = '', i = 0, quote: string | null = null;
  while (i < text.length) {
    const c = text[i], next = text[i + 1];
    if (quote) {
      out += c;
      if (c === '\\') { out += next ?? ''; i += 2; continue; }
      if (c === quote || (c === '\n' && quote !== '`')) quote = null;
      i++;
    } else if (c === '/' && next === '/') {
      while (i < text.length && text[i] !== '\n') i++;
    } else if (c === '/' && next === '*') {
      const end = text.indexOf('*/', i + 2);
      i = end < 0 ? text.length : end + 2;
    } else {
      if (c === "'" || c === '"' || c === '`') quote = c;
      out += c;
      i++;
    }
  }
  return out;
}
const textOf = (entry: unknown) => typeof entry === 'string' ? entry : (entry as { text: string }).text;
const adminKeys = Object.keys(builtin[ADMIN]);
const has = (full: string) => Object.hasOwn(builtin[full.split('.')[0]] ?? {}, full);

test('no CJK characters in the localization pages and the i18n runtime (comments excluded)', () => {
  assert.ok(scanned.length >= 15, 'scan found too few files');
  const offenders = scanned.flatMap(path => withoutComments(readFileSync(path, 'utf8')).split('\n')
    .map((line, index) => ({ line, index })).filter(({ line }) => cjk.test(line))
    .map(({ line, index }) => relative(src, path) + ':' + (index + 1) + ' ' + line.trim()));
  assert.deepEqual(offenders, []);
});

test('the comment stripper keeps strings and JSX text, so the scan cannot be bypassed', () => {
  assert.ok(cjk.test(withoutComments('const a = "中文"; // 注释')));
  assert.ok(cjk.test(withoutComments('<p>中文</p>')));
  assert.ok(!cjk.test(withoutComments("/** 注释 */\nconst url = 'https://x'; // 注释")));
});

test('English base and zh-CN seed have the same namespaces and keys, all namespace-prefixed and non-empty', () => {
  // seed 即翻译导入文件（卡 A 1.7：code-mor-i18n-translation/1），可在翻译页直接导入。
  assert.equal(seed.format, 'code-mor-i18n-translation/1');
  assert.equal(seed.locale, 'zh-CN');
  assert.equal(seed.application, 'operations-console');
  assert.deepEqual(Object.keys(seed.namespaces).sort(), Object.keys(builtin).sort());
  for (const ns of Object.keys(builtin)) {
    assert.deepEqual(Object.keys(seed.namespaces[ns]).sort(), Object.keys(builtin[ns]).sort(), ns);
    for (const key of Object.keys(builtin[ns])) {
      assert.match(key, new RegExp('^' + ns + '(\\.[a-z0-9_]+)+$'), key);
      assert.ok(textOf(builtin[ns][key]).trim(), 'empty English base for ' + key);
      assert.ok(seed.namespaces[ns][key].trim(), 'empty zh-CN seed for ' + key);
    }
  }
});

test('every base text and seed translation is valid ICU with the same parameters (rules B5)', () => {
  const problems: string[] = [];
  for (const ns of Object.keys(builtin)) {
    for (const [key, entry] of Object.entries(builtin[ns])) {
      const source = textOf(entry), translation = seed.namespaces[ns][key];
      if (parseIcu(source).issues.length) problems.push('base ' + key);
      const issues = validateTranslation(source, translation);
      if (issues.length) problems.push('seed ' + key + ': ' + issues.map(issue => issue.code).join(','));
      try { new IntlMessageFormat(source, 'en', undefined, { ignoreTag: true }); new IntlMessageFormat(translation, 'zh-CN', undefined, { ignoreTag: true }); }
      catch (error) { problems.push('intl ' + key + ': ' + (error as Error).message); }
    }
  }
  assert.deepEqual(problems, []);
});

/** 页面中的静态 key：t('area.name') 形式的字面量（含三元表达式两侧）。 */
function staticKeys(): string[] {
  const areas = new Set(adminKeys.map(key => key.split('.')[1]));
  const found = new Set<string>();
  for (const path of sourceFiles(join(src, 'localization'))) {
    for (const match of readFileSync(path, 'utf8').matchAll(/'([a-z_]+(?:\.[a-z0-9_]+)+)'/g)) {
      if (areas.has(match[1].split('.')[0])) found.add(ADMIN + '.' + match[1]);
    }
  }
  return [...found];
}
const issueCodes: IssueCode[] = ['empty', 'unclosed_brace', 'unmatched_close', 'bad_argument', 'missing_fragment', 'unsupported_type',
  'choice_unsupported', 'missing_other', 'missing_parameter', 'extra_parameter', 'renamed_parameter'];
/** 运行时拼接的 key 家族（静态扫描覆盖不到）。 */
const dynamicKeys = [
  'icu.with_position',
  ...['all', 'untranslated', 'source_changed'].map(tab => 'translations.tab.' + tab),
  ...['translated', 'untranslated', 'source_changed', 'editing', 'error'].map(status => 'list.status.' + status),
  ...['text', 'number', 'select', 'date', 'time'].map(type => 'editor.argument_type.' + type),
  ...issueCodes.map(code => 'icu.' + code),
  ...['added', 'source_changed', 'deprecated', 'restored'].flatMap(kind => ['import.row.' + kind, 'import.desc.' + kind]),
  ...['missing_namespaces', 'problems', 'stale', 'no_changes', 'not_importable'].map(kind => 'import.blocker.' + kind),
  ...['empty', 'too_large', 'not_json', 'bad_format', 'application_mismatch', 'locale_mismatch'].map(kind => 'import.file_problem.' + kind),
  ...['added', 'translation', 'source', 'removed'].flatMap(kind => ['releases.change.' + kind, 'publish.change.' + kind, 'publish.change_desc.' + kind]),
  ...['generate', 'store', 'check', 'activate'].map(step => 'releases.step.' + step),
  ...['required', 'pattern', 'duplicate'].flatMap(problem => ['app_form.problem.' + problem, 'namespace.problem.' + problem]),
].map(key => ADMIN + '.' + key);
const enumKeys = [
  ...['checking', 'live', 'offline', 'failed'].map(value => 'enum.release_display.' + value),
  ...['enabled', 'disabled'].map(value => 'enum.locale_status.' + value),
  ...['ltr', 'rtl'].map(value => 'enum.text_direction.' + value),
];

test('every key used by the pages exists in the English base', () => {
  assert.deepEqual([...staticKeys(), ...dynamicKeys, ...enumKeys].filter(key => !has(key)), []);
});

test('the English base has no unused keys', () => {
  const used = new Set([...staticKeys(), ...dynamicKeys, ...enumKeys]);
  const all = Object.values(builtin).flatMap(entries => Object.keys(entries));
  assert.deepEqual(all.filter(key => !used.has(key)), []);
});
