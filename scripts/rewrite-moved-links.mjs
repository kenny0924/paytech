// Rewrites Markdown links that still point at the 12 pre-V2 application roots so they resolve
// under `paytech/` (design/09 §6.2 of requirements/20261017/architecture-V2骨架与模块搬迁).
//
// Usage: node scripts/rewrite-moved-links.mjs --check|--write [--map <mapping.json>]
//   --check  report pending rewrites, unmappable links and remaining `cd <old root>` command text; exit 1 if any
//   --write  rewrite pending links in place, then report what could not be rewritten
// Default mapping table: scripts/moved-links-map.json.
//
// A link is rewritten only when it is broken today and its corrected target exists:
//   1. Inbound: the resolved target lies under an old root; the longest matching `from` prefix of the
//      mapping table (exact file entries first, then directory entries ending in `/`) gives the new path.
//   2. Outbound: the linking file lies under `paytech/` (moved with its root) and the target resolves one
//      directory higher, i.e. one `../` is missing; old-root targets found that way are mapped as in rule 1.
// Only link destinations change. Text, command blocks and inline code are never modified; remaining
// `cd <old root>` command text is listed by --check outside the excluded historical paths.

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePath, extractLinks, listMarkdownFiles, parseRelativeTarget } from './markdown-links.mjs';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const mode = args.includes('--write') ? 'write' : args.includes('--check') ? 'check' : null;
const mapIndex = args.indexOf('--map');
const mapFile = resolve(mapIndex >= 0 ? args[mapIndex + 1] : join(repositoryRoot, 'scripts', 'moved-links-map.json'));

if (!mode || (mapIndex >= 0 && !args[mapIndex + 1])) {
  process.stderr.write('Usage: node scripts/rewrite-moved-links.mjs --check|--write [--map <mapping.json>]\n');
  process.exit(2);
}

const table = JSON.parse(readFileSync(mapFile, 'utf8'));
const oldRoots = new Set(table.oldRoots);
const fileEntries = new Map(table.mappings.filter((entry) => !entry.from.endsWith('/')).map((e) => [e.from, e.to]));
const directoryEntries = table.mappings
  .filter((entry) => entry.from.endsWith('/'))
  .sort((a, b) => b.from.length - a.from.length);
const commandExcludes = (table.commandTextExcludes ?? []).map((pattern) => new RegExp(pattern));
const commandPattern = new RegExp(`\\bcd (${[...oldRoots].map((r) => r.replace(/[-.]/g, '\\$&')).join('|')})\\b`, 'g');

const toRepoPath = (absolute) => relative(repositoryRoot, absolute).split(sep).join('/');
const isOldRootPath = (repoPath) => oldRoots.has(repoPath.split('/')[0]);

/** Maps a repository-relative path under an old root to its new repository-relative path, or null. */
function mapPath(repoPath) {
  const exact = fileEntries.get(repoPath);
  if (exact) return exact;
  for (const entry of directoryEntries) {
    const directory = entry.from.slice(0, -1);
    if (repoPath === directory) return entry.to.slice(0, -1);
    if (repoPath.startsWith(entry.from)) return entry.to + repoPath.slice(entry.from.length);
  }
  return null;
}

/** Returns the corrected repository-relative target for a broken link, or { unmapped } when none exists. */
function correctTarget(file, decoded) {
  const directory = dirname(join(repositoryRoot, file));
  const candidates = [toRepoPath(resolve(directory, decoded))];
  if (file.startsWith('paytech/')) candidates.push(toRepoPath(resolve(dirname(directory), decoded)));
  for (const candidate of candidates) {
    if (candidate.startsWith('..')) continue;
    if (!isOldRootPath(candidate)) {
      if (candidate !== candidates[0] && existsSync(join(repositoryRoot, candidate))) return { target: candidate };
      continue;
    }
    const mapped = mapPath(candidate);
    if (mapped && existsSync(join(repositoryRoot, mapped))) return { target: mapped };
    return { unmapped: candidate, mapped };
  }
  return null;
}

function formatLink(file, target, newRepoPath) {
  const directory = dirname(join(repositoryRoot, file));
  let path = relative(directory, join(repositoryRoot, newRepoPath)).split(sep).join('/') || '.';
  if (target.path.endsWith('/') && !path.endsWith('/')) path += '/';
  if (target.path.startsWith('./') && !path.startsWith('.')) path = `./${path}`;
  if (target.path !== decodePath(target.path)) path = encodeURI(path);
  const value = path + target.suffix;
  return target.angle ? `<${value}>` : value;
}

const pending = [];
const unmapped = [];
const commands = [];
let rewrittenFiles = 0;

for (const file of listMarkdownFiles(repositoryRoot)) {
  const absolute = join(repositoryRoot, file);
  const text = readFileSync(absolute, 'utf8');
  const lines = text.split('\n');
  const edits = [];
  for (const link of extractLinks(text)) {
    const target = parseRelativeTarget(link.raw);
    if (!target) continue;
    const decoded = decodePath(target.path);
    if (existsSync(resolve(dirname(absolute), decoded))) continue;
    const correction = correctTarget(file, decoded);
    if (!correction) continue;
    if (correction.unmapped) {
      unmapped.push(`${file}:${link.line}:${link.raw} (target ${correction.unmapped}${correction.mapped ? ` -> missing ${correction.mapped}` : ', no mapping'})`);
      continue;
    }
    const replacement = formatLink(file, target, correction.target);
    pending.push(`${file}:${link.line}: ${link.raw} -> ${replacement}`);
    edits.push({ ...link, replacement });
  }
  if (!commandExcludes.some((pattern) => pattern.test(file))) {
    lines.forEach((line, index) => {
      for (const match of line.matchAll(commandPattern)) commands.push(`${file}:${index + 1}: ${match[0]}`);
    });
  }
  if (mode === 'write' && edits.length > 0) {
    for (const edit of edits.sort((a, b) => b.line - a.line || b.from - a.from)) {
      const line = lines[edit.line - 1];
      lines[edit.line - 1] = line.slice(0, edit.from) + edit.replacement + line.slice(edit.to);
    }
    writeFileSync(absolute, lines.join('\n'));
    rewrittenFiles += 1;
  }
}

const print = (title, items) => {
  if (items.length === 0) return;
  process.stdout.write(`${title}\n`);
  for (const item of items) process.stdout.write(`  ${item}\n`);
};

if (mode === 'check') print('pending link rewrites:', pending);
print('links that cannot be rewritten automatically:', unmapped);
print('`cd <old root>` command text outside excluded paths:', commands);
process.stdout.write(
  mode === 'write'
    ? `rewrote ${pending.length} links in ${rewrittenFiles} files; ${unmapped.length} unmappable; ${commands.length} command texts\n`
    : `${pending.length} pending; ${unmapped.length} unmappable; ${commands.length} command texts\n`,
);
const remaining = (mode === 'check' ? pending.length : 0) + unmapped.length + commands.length;
process.exit(remaining === 0 ? 0 : 1);
