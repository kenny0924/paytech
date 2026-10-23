import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { transformWithOxc } from 'vite';
import type { VariableProjection, VariableSummary } from './riskVariable.ts';
import { variableDefinitions } from './variableFixtures.ts';
import { testT } from '../i18n/testing.ts';

type Node = { type: unknown; props: Record<string, unknown>; children: unknown[] };

// Execute the production projection with a small hook host and a controlled HTTP transport.
// Rendering leaves are inert; request cancellation and component state remain real.
async function projectionHost(list: (...args: unknown[]) => Promise<unknown>) {
  const source = readFileSync(new URL('./VariableControls.tsx', import.meta.url), 'utf8')
    .replace(/^import .+;\n/gm, '').replace(/^export /gm, '');
  const { code } = await transformWithOxc(source, 'VariableControls.tsx', {
    jsx: { runtime: 'classic', pragma: 'jsx', pragmaFrag: 'Fragment' },
  });
  const slots: unknown[] = [], effects: { deps?: readonly unknown[]; cleanup?: () => void }[] = [];
  let index = 0;
  const pending: (() => void)[] = [];
  const changed = (before: readonly unknown[] | undefined, after: readonly unknown[]) =>
    !before || before.length !== after.length || before.some((value, i) => value !== after[i]);
  const dependencies = {
    useState(initial: unknown) {
      const at = index++;
      if (!(at in slots)) slots[at] = typeof initial === 'function' ? initial() : initial;
      return [slots[at], (value: unknown) => { slots[at] = typeof value === 'function' ? value(slots[at]) : value; }];
    },
    useRef(initial: unknown) {
      const at = index++;
      return slots[at] ?? (slots[at] = { current: initial });
    },
    useCallback(callback: unknown, deps: readonly unknown[]) {
      const at = index++, previous = slots[at] as { deps: readonly unknown[]; callback: unknown } | undefined;
      if (changed(previous?.deps, deps)) slots[at] = { deps, callback };
      return (slots[at] as { callback: unknown }).callback;
    },
    useEffect(run: () => (() => void) | undefined, deps: readonly unknown[]) {
      const at = index++, previous = effects[at];
      if (changed(previous?.deps, deps)) pending.push(() => {
        previous?.cleanup?.(); effects[at] = { deps, cleanup: run() };
      });
    },
    jsx(type: unknown, props: Record<string, unknown> | null, ...children: unknown[]): Node {
      return { type, props: props ?? {}, children };
    },
    Fragment: 'Fragment', Button: 'Button', Alert: 'Alert', Tooltip: 'Tooltip', Tag: 'Tag', Space: 'Space',
    window: new EventTarget(), variableApi: { list }, useT: testT,
  };
  const component = new Function(...Object.keys(dependencies), `${code}\nreturn VariableProjectionPanel;`)(...Object.values(dependencies));
  return {
    render(projection: VariableProjection): Node {
      index = 0; const tree = component({ projection }); pending.splice(0).forEach(run => run()); return tree;
    },
    close() { effects.forEach(effect => effect.cleanup?.()); },
  };
}

function nodes(value: unknown): Node[] {
  if (Array.isArray(value)) return value.flatMap(nodes);
  if (!value || typeof value !== 'object' || !('children' in value)) return [];
  const node = value as Node; return [node, ...nodes(node.children)];
}

test('late current-value refresh cannot overwrite another rule or flow projection', async () => {
  let resolve!: (value: unknown) => void;
  let signal: AbortSignal | undefined;
  const host = await projectionHost(async (_, abort) => {
    signal = abort as AbortSignal;
    return new Promise(done => { resolve = done; });
  });
  const original = variableDefinitions[0] as VariableSummary;
  const first: VariableProjection = { currentVariables: [original], valuesAsOf: original.updateTime, variableIssues: [] };
  const replacement = { ...original, variableCode: 'ANOTHER_CONFIGURATION', variableName: '另一条配置' };
  const second: VariableProjection = { ...first, currentVariables: [replacement] };
  try {
    const refresh = nodes(host.render(first)).find(node => node.type === 'Button')!.props.onClick as () => void;
    refresh(); assert.equal(signal?.aborted, false);
    host.render(second); assert.equal(signal?.aborted, true);
    resolve({ items: [original], valuesAsOf: original.updateTime, missingCodes: [] });
    await new Promise(done => setImmediate(done));
    const tags = nodes(host.render(second)).filter(node => node.type === 'Tag');
    assert.ok(tags.some(node => node.props.title === replacement.variableCode));
    assert.ok(!tags.some(node => node.props.title === original.variableCode));
  } finally { host.close(); }
});
