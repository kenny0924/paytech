import { createElement } from 'react';
import type { ReactNode } from 'react';
import type { Params, Translate } from './index.ts';

const mark = '\u0001';

/**
 * Renders one translated sentence that embeds React nodes (monospace codes, links, inputs) without splitting it
 * into fragments: each node is passed as a named ICU argument, so translators still see and reorder the whole
 * sentence, and the nodes are placed back where the translation puts their arguments.
 *
 *   richText(t, 'trial.pageInput', { input: <InputNumber … /> })   // en "Page {input}", zh "第 {input} 页"
 */
export function richText(t: Translate, key: string, nodes: Record<string, ReactNode>, params: Params = {}): ReactNode[] {
  const marks = Object.fromEntries(Object.keys(nodes).map(name => [name, mark + name + mark]));
  return t(key, { ...params, ...marks }).split(mark).map((part, index) => index % 2 === 1 && part in nodes
    ? createElement('span', { key: index }, nodes[part]) : part);
}

/** A monospace code fragment for `richText` arguments. */
export function code(text: string): ReactNode {
  return createElement('span', { className: 'mono' }, text);
}
