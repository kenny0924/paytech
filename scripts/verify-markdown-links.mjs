// Verifies that every relative link in the repository's Markdown files resolves to an existing path.
//
// Usage: node scripts/verify-markdown-links.mjs [path-prefix ...]
//   Without arguments all tracked and untracked-but-not-ignored *.md files are checked.
//   With arguments only files whose repository-relative path starts with one of the prefixes are checked.
// Output: one `file:line:link` per broken link; exit code 1 when any link is broken, 0 otherwise.
// External URLs, pure anchors, absolute paths and links inside code are not checked.
// A missing target that git ignores (for example test-environment/private/ or *.log evidence) is a
// deliberate pointer to local-only material: it is listed as `local-only` and does not fail the check.

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePath, extractLinks, listMarkdownFiles, parseRelativeTarget } from './markdown-links.mjs';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const prefixes = process.argv.slice(2).map((prefix) => prefix.replace(/^\.\//, ''));

const files = listMarkdownFiles(repositoryRoot).filter(
  (file) => prefixes.length === 0 || prefixes.some((prefix) => file.startsWith(prefix)),
);

const missing = [];
let checked = 0;
for (const file of files) {
  const text = readFileSync(join(repositoryRoot, file), 'utf8');
  const directory = dirname(join(repositoryRoot, file));
  for (const link of extractLinks(text)) {
    const target = parseRelativeTarget(link.raw);
    if (!target) continue;
    checked += 1;
    const absolute = resolve(directory, decodePath(target.path));
    if (!existsSync(absolute)) {
      missing.push({ entry: `${file}:${link.line}:${link.raw}`, path: relative(repositoryRoot, absolute).split(sep).join('/') });
    }
  }
}

/** Returns the subset of repository-relative paths that git ignores. */
function ignoredPaths(paths) {
  const inside = [...new Set(paths.filter((path) => !path.startsWith('..')))];
  if (inside.length === 0) return new Set();
  try {
    const output = execFileSync('git', ['check-ignore', '--no-index', '--stdin', '-z'], {
      cwd: repositoryRoot,
      input: inside.join('\0'),
      encoding: 'utf8',
    });
    return new Set(output.split('\0').filter(Boolean));
  } catch (error) {
    if (error.status === 1) return new Set();
    throw error;
  }
}

const ignored = ignoredPaths(missing.map((item) => item.path));
const localOnly = missing.filter((item) => ignored.has(item.path));
const broken = missing.filter((item) => !ignored.has(item.path));
for (const item of broken) process.stdout.write(`${item.entry}\n`);
for (const item of localOnly) process.stdout.write(`local-only (git-ignored target): ${item.entry}\n`);
process.stdout.write(
  `checked ${checked} relative links in ${files.length} Markdown files; ${broken.length} broken; ${localOnly.length} local-only\n`,
);
process.exit(broken.length === 0 ? 0 : 1);
