// Shared by the i18n guard and catalog tests (node only; not imported by the application).
import { readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/** CJK ideographs, kana, Hangul and full-width forms: any of them in source is hard-coded UI text. */
export const cjk = /[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uac00-\ud7af\uf900-\ufaff\uff00-\uffef\u3000-\u303f]/u;

/** Production `.ts` / `.tsx` files under `dir` (tests excluded). */
export function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.tsx?$/.test(name) ? [path] : [];
  });
}

/** Removes comments and keeps code, strings and JSX text, so Chinese comments never count as UI text. */
export function withoutComments(text: string): string {
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

/**
 * Hard-coded UI text in comment-free source: string / template literals containing CJK, plus JSX text runs
 * (outside quotes) containing CJK. Approximate by design; it reports progress, and any CJK at all fails STRICT.
 */
export function cjkLiterals(code: string): string[] {
  const found: string[] = [];
  let i = 0, outside = '';
  const flush = () => {
    for (const run of outside.split(/[<>{}()=;,]/)) if (cjk.test(run)) found.push(run.trim());
    outside = '';
  };
  while (i < code.length) {
    const c = code[i];
    if (c === "'" || c === '"' || c === '`') {
      flush();
      let j = i + 1;
      while (j < code.length && code[j] !== c && !(c !== '`' && code[j] === '\n')) j += code[j] === '\\' ? 2 : 1;
      const literal = code.slice(i, j + 1);
      if (cjk.test(literal)) found.push(literal);
      i = j + 1;
    } else { outside += c; i++; }
  }
  flush();
  return found;
}
