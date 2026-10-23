// 把 src/i18n/en/*.json 合并为 Platform key 导入文件（code-mor-i18n-source/1），输出到 i18n-out/。
// i18n/operations-console.zh-CN.seed.json 本身就是翻译导入文件（code-mor-i18n-translation/1）；这里按当前英文基准对齐 key
// （缺的补空字符串 = 不改动、多余的丢弃）后写到 i18n-out/，供翻译页"导入简体中文翻译文件"使用。
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const application = 'operations-console';
const sourceDir = join(root, 'src/i18n/en'), outDir = join(root, 'i18n-out');
const namespaces = {};
for (const file of readdirSync(sourceDir).filter((name) => name.endsWith('.json')).sort()) {
  const ns = file.slice(0, -'.json'.length), entries = JSON.parse(readFileSync(join(sourceDir, file), 'utf8'));
  for (const key of Object.keys(entries)) {
    if (!key.startsWith(ns + '.')) throw new Error(`${file}: key "${key}" must start with "${ns}."`);
  }
  namespaces[ns] = Object.fromEntries(Object.keys(entries).sort().map((key) => [key, entries[key]]));
}
mkdirSync(outDir, { recursive: true });
const target = join(outDir, `${application}.en.json`);
writeFileSync(target, JSON.stringify({ format: 'code-mor-i18n-source/1', application, namespaces }, null, 2) + '\n');
const total = Object.values(namespaces).reduce((sum, entries) => sum + Object.keys(entries).length, 0);
console.log(`Wrote ${target}: ${Object.keys(namespaces).length} namespaces, ${total} keys`);

const seedPath = join(root, `i18n/${application}.zh-CN.seed.json`);
if (existsSync(seedPath)) {
  const seed = JSON.parse(readFileSync(seedPath, 'utf8'));
  if (seed.format !== 'code-mor-i18n-translation/1' || seed.locale !== 'zh-CN') throw new Error(`${seedPath}: expected code-mor-i18n-translation/1 for zh-CN`);
  const translations = Object.fromEntries(Object.keys(namespaces).map((ns) => [ns,
    Object.fromEntries(Object.keys(namespaces[ns]).map((key) => [key, seed.namespaces?.[ns]?.[key] ?? '']))]));
  const seedTarget = join(outDir, `${application}.zh-CN.json`);
  writeFileSync(seedTarget, JSON.stringify({ format: 'code-mor-i18n-translation/1', application, locale: 'zh-CN', namespaces: translations }, null, 2) + '\n');
  console.log(`Wrote ${seedTarget}: zh-CN translation import file`);
}
