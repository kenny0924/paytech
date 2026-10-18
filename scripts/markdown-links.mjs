// Shared Markdown link parsing for verify-markdown-links.mjs and rewrite-moved-links.mjs.
//
// Only inline links/images `[text](target)` and reference definitions `[id]: target` are
// considered. Fenced code blocks and inline code spans are skipped, so command text in
// examples is never treated as a link.

import { execFileSync } from 'node:child_process';

const SCHEME = /^[a-z][a-z0-9+.-]*:/i;
const FENCE = /^\s{0,3}(`{3,}|~{3,})/;
const REFERENCE_DEFINITION = /^\s{0,3}\[[^\]\n]+\]:\s*(<[^>\n]*>|\S+)/;

/** Lists tracked and untracked-but-not-ignored Markdown files, relative to the repository root. */
export function listMarkdownFiles(repositoryRoot) {
  const output = execFileSync(
    'git',
    ['ls-files', '-z', '--cached', '--others', '--exclude-standard', '--', '*.md'],
    { cwd: repositoryRoot, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 },
  );
  return [...new Set(output.split('\0').filter(Boolean))].sort();
}

/** Replaces inline code spans with spaces of equal length so offsets stay stable. */
function maskInlineCode(line) {
  return line.replace(/(`+)([\s\S]*?[^`])\1(?!`)/g, (match) => ' '.repeat(match.length));
}

/** Reads the destination that starts at `start` (just after `](`); returns the raw destination and end offset. */
function readInlineDestination(line, start) {
  let index = start;
  while (line[index] === ' ') index += 1;
  if (line[index] === '<') {
    const close = line.indexOf('>', index + 1);
    if (close < 0) return null;
    return { raw: line.slice(index, close + 1), from: index, to: close + 1 };
  }
  let depth = 0;
  const from = index;
  for (; index < line.length; index += 1) {
    const ch = line[index];
    if (ch === '\\') {
      index += 1;
      continue;
    }
    if (ch === '(') depth += 1;
    else if (ch === ')') {
      if (depth === 0) break;
      depth -= 1;
    } else if (ch === ' ' || ch === '\t') break;
  }
  if (index === from) return null;
  return { raw: line.slice(from, index), from, to: index };
}

/**
 * Yields every link destination of one Markdown document.
 * Each item: { line (1-based), raw, from, to } where from/to are offsets inside that line.
 */
export function* extractLinks(text) {
  const lines = text.split('\n');
  let fence = null;
  for (let number = 0; number < lines.length; number += 1) {
    const line = lines[number];
    const fenceMatch = FENCE.exec(line);
    if (fence) {
      if (fenceMatch && fenceMatch[1][0] === fence[0] && fenceMatch[1].length >= fence.length) fence = null;
      continue;
    }
    if (fenceMatch) {
      fence = fenceMatch[1];
      continue;
    }
    const masked = maskInlineCode(line);
    const reference = REFERENCE_DEFINITION.exec(masked);
    if (reference) {
      const from = masked.indexOf(reference[1], reference[0].length - reference[1].length);
      yield { line: number + 1, raw: reference[1], from, to: from + reference[1].length };
      continue;
    }
    let search = 0;
    for (;;) {
      const open = masked.indexOf('](', search);
      if (open < 0) break;
      const destination = readInlineDestination(masked, open + 2);
      search = open + 2;
      if (!destination) continue;
      yield { line: number + 1, raw: line.slice(destination.from, destination.to), from: destination.from, to: destination.to };
      search = destination.to;
    }
  }
}

/**
 * Splits a raw destination into { path, suffix, angle } for a relative file link,
 * or returns null for external URLs, pure anchors, absolute paths and empty targets.
 */
export function parseRelativeTarget(raw) {
  const angle = raw.startsWith('<') && raw.endsWith('>');
  const value = angle ? raw.slice(1, -1) : raw;
  if (!value || value.startsWith('#') || value.startsWith('/') || SCHEME.test(value)) return null;
  const cut = value.search(/[?#]/);
  const path = cut < 0 ? value : value.slice(0, cut);
  const suffix = cut < 0 ? '' : value.slice(cut);
  if (!path) return null;
  return { path, suffix, angle };
}

/** Decodes percent-encoding when it is valid; otherwise returns the path unchanged. */
export function decodePath(path) {
  try {
    return decodeURIComponent(path);
  } catch {
    return path;
  }
}
