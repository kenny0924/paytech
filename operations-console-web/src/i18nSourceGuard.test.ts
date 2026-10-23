import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { cjk, cjkLiterals, sourceFiles, withoutComments } from './i18nSource.ts';

/**
 * Hard-coded Chinese guard for the whole console (requirement R8, technique 3.6): no CJK may appear in
 * production source outside comments. New UI text goes into src/i18n/en/<ns>.json with its zh-CN seed in
 * i18n/zh-CN/<ns>.json. ALLOWED is the only exception: the Platform registration mirror keeps its Chinese
 * display names.
 */
const ALLOWED = ['navigation/pageCapabilities.ts'];

const src = fileURLToPath(new URL('.', import.meta.url));
const files = sourceFiles(src).map(path => relative(src, path)).filter(path => !ALLOWED.includes(path));
const offenders = new Map(files.flatMap(path => {
  const literals = cjkLiterals(withoutComments(readFileSync(join(src, path), 'utf8')));
  return literals.length ? [[path, literals] as const] : [];
}));
test('production source contains no Chinese outside comments', (t) => {
  const literals = [...offenders.values()].reduce((sum, list) => sum + list.length, 0);
  t.diagnostic(`i18n guard: ${offenders.size} of ${files.length} source files contain Chinese, ${literals} literals`);
  const left = [...offenders.entries()].map(([path, list]) => path + ': ' + list.slice(0, 3).join(' | '));
  assert.deepEqual(left, []);
});

test('the allow-list exists, and the scanner sees strings and JSX text but not comments or emoji', () => {
  for (const entry of ALLOWED) assert.ok(sourceFiles(src).some(path => relative(src, path) === entry), 'missing ' + entry);
  assert.deepEqual(cjkLiterals(withoutComments("const a = '中文'; // 注释\n/** 说明 */ const b = <p>标题 {x}</p>;")), ["'中文'", '标题']);
  assert.ok(!cjk.test(withoutComments("/** 注释 */\nconst url = 'https://x'; // 注释")));
  assert.ok(cjk.test(withoutComments('const t = `共 ${count} 条`;')));
  assert.ok(!cjk.test('\u{1F512} \u{1F600}'));
});
