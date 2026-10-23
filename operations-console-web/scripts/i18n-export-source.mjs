// 生成 Platform 导入文件到 i18n-out/：
// - operations-console.en.json（code-mor-i18n-source/1）：合并 src/i18n/en/<ns>.json 全部命名空间的英文基准；
// - operations-console.zh-CN.json（code-mor-i18n-translation/1）：合并 i18n/zh-CN/<ns>.json 的简体中文种子（= 迁移前原中文）。
// 校验：两边命名空间与 key 集合完全一致；每个 key 以所在文件的命名空间开头且符合 Platform key 规则；英文与中文都不为空。
// 单个导入文件上限 2 MiB（Platform 限制）；超出时按命名空间拆成多个 operations-console.<locale>.<n>.json 依次导入。
import { mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const application = 'operations-console';
const enDir = join(root, 'src/i18n/en'), zhDir = join(root, 'i18n/zh-CN'), outDir = join(root, 'i18n-out');
const LIMIT = 2 * 1024 * 1024;
const KEY = /^(?=.{3,191}$)[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)+$/;
const problems = [];

function readNamespaces(dir, label) {
  const result = {};
  for (const file of readdirSync(dir).filter((name) => name.endsWith('.json')).sort()) {
    const ns = file.slice(0, -'.json'.length), entries = JSON.parse(readFileSync(join(dir, file), 'utf8'));
    for (const [key, value] of Object.entries(entries)) {
      if (!key.startsWith(ns + '.')) problems.push(`${label}/${file}: key "${key}" must start with "${ns}."`);
      if (!KEY.test(key)) problems.push(`${label}/${file}: key "${key}" breaks the Platform key rule`);
      const text = typeof value === 'string' ? value : value?.text;
      if (typeof text !== 'string' || !text.trim()) problems.push(`${label}/${file}: empty text for "${key}"`);
    }
    result[ns] = Object.fromEntries(Object.keys(entries).sort().map((key) => [key, entries[key]]));
  }
  return result;
}
const en = readNamespaces(enDir, 'src/i18n/en'), zh = readNamespaces(zhDir, 'i18n/zh-CN');
for (const ns of new Set([...Object.keys(en), ...Object.keys(zh)])) {
  if (!en[ns] || !zh[ns]) { problems.push(`namespace "${ns}" exists only in ${en[ns] ? 'en' : 'zh-CN'}`); continue; }
  for (const key of Object.keys(en[ns])) if (!(key in zh[ns])) problems.push(`${ns}: "${key}" has no zh-CN seed`);
  for (const key of Object.keys(zh[ns])) if (!(key in en[ns])) problems.push(`${ns}: zh-CN seed "${key}" has no English base`);
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}

/** Splits namespaces into files no larger than LIMIT (one namespace is never split). */
function write(locale, namespaces, header) {
  const files = [[]];
  const size = (list) => Buffer.byteLength(JSON.stringify({ ...header, namespaces: Object.fromEntries(list) }, null, 2) + '\n');
  for (const entry of Object.entries(namespaces)) {
    const current = files.at(-1);
    if (current.length && size([...current, entry]) > LIMIT) files.push([entry]); else current.push(entry);
  }
  const written = files.map((list, index) => {
    const name = files.length === 1 ? `${application}.${locale}.json` : `${application}.${locale}.${index + 1}.json`;
    const body = JSON.stringify({ ...header, namespaces: Object.fromEntries(list) }, null, 2) + '\n';
    if (Buffer.byteLength(body) > LIMIT) throw new Error(`${name}: namespace ${list[0][0]} alone exceeds 2 MiB`);
    writeFileSync(join(outDir, name), body);
    return { name, bytes: Buffer.byteLength(body), namespaces: list.length };
  });
  for (const file of written) console.log(`Wrote i18n-out/${file.name}: ${file.namespaces} namespaces, ${(file.bytes / 1024).toFixed(1)} KiB`);
  if (written.length > 1) console.log(`  ${locale}: over 2 MiB in one file, import the ${written.length} files one after another`);
}

rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });
const flatZh = Object.fromEntries(Object.entries(zh).map(([ns, entries]) => [ns,
  Object.fromEntries(Object.entries(entries).map(([key, value]) => [key, typeof value === 'string' ? value : value.text]))]));
write('en', en, { format: 'code-mor-i18n-source/1', application });
write('zh-CN', flatZh, { format: 'code-mor-i18n-translation/1', application, locale: 'zh-CN' });
const counts = Object.entries(en).map(([ns, entries]) => `${ns} ${Object.keys(entries).length}`);
const total = Object.values(en).reduce((sum, entries) => sum + Object.keys(entries).length, 0);
console.log(`Keys per namespace: ${counts.join(', ')}`);
console.log(`Total: ${Object.keys(en).length} namespaces, ${total} keys`);
