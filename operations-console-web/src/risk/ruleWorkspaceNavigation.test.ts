import { testT } from '../i18n/testing.ts';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { transformWithOxc } from 'vite';
import * as builder from './ruleBuilder.ts';
import * as presentation from './ruleEditorPresentation.ts';
import * as commands from './riskCommands.ts';
import { CHECKOUT_PREPARE_AUTHORING_V1 } from './riskAuthoringCatalog.ts';
import { ruleSetDetail } from './riskFixtures.ts';

type Node = { type: unknown; props: Record<string, unknown> };
function nodes(value: unknown): Node[] {
  if (Array.isArray(value)) return value.flatMap(nodes);
  if (!value || typeof value !== 'object' || !('props' in value)) return [];
  const node = value as Node;
  return [node, ...nodes(node.props.children)];
}
function text(value: unknown): string {
  if (Array.isArray(value)) return value.map(text).join('');
  if (value && typeof value === 'object' && 'props' in value) return text((value as Node).props.children);
  return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
}

for (const component of ['RuleSetEditor', 'ReadRuleSetWorkspace']) {
  const source = readFileSync(new URL(`${component}.tsx`, import.meta.url), 'utf8').replace(/^import .+;\n/gm, '').replace(/^export /gm, '');
  const { code } = await transformWithOxc(source, `${component}.tsx`, { jsx: { runtime: 'classic', pragma: 'jsx', pragmaFrag: 'Fragment' } });
  test(`${component} compact navigation retains visible names and summaries, supports selection and has no page back controls`, () => {
    const state: unknown[] = []; let index = 0;
    const dependencies = { Fragment: 'Fragment', ...builder, ...presentation, ...commands, CHECKOUT_PREPARE_AUTHORING_V1,
      catalog: CHECKOUT_PREPARE_AUTHORING_V1, ruleSetStatusLabel: (value: string) => value, formatRiskTime: (value: string) => value, useT: testT,
      App: { useApp: () => ({ modal: { confirm() {} } }) },
      useState(initial: unknown) { const slot = index++; if (!(slot in state)) state[slot] = initial; return [state[slot], (value: unknown) => { state[slot] = value; }]; },
      useRef: (value: unknown) => ({ current: value }), useMemo: (fn: () => unknown) => fn(), useEffect() {},
      jsx: (type: unknown, props: Record<string, unknown> | null, ...children: unknown[]) => ({ type, props: { ...props, children } }),
      ...Object.fromEntries(['Alert', 'Button', 'Descriptions', 'Dropdown', 'Form', 'Input', 'Modal', 'Radio', 'Space', 'Switch', 'Tag', 'Tooltip', 'Typography',
        'ConditionBuilder', 'DictionarySelect', 'AdditionalActionEditor', 'ConditionView', 'ReadRuleSetWorkspace', 'VariableProjectionPanel'].map(name => [name, name])),
    };
    const renderComponent = new Function(...Object.keys(dependencies), `${code}\nreturn ${component};`)(...Object.values(dependencies));
    const editor = builder.detailToEditor(ruleSetDetail);
    editor.rules = [editor.rules[0]!, { ...editor.rules[0]!, id: 'second-rule', ruleName: '第二条规则的完整名称', ruleCode: 'SECOND' }];
    const render = () => { index = 0; return renderComponent({ editor, base: ruleSetDetail, catalog: CHECKOUT_PREPARE_AUTHORING_V1, disabled: false,
      saving: false, commandPending: false, fieldErrors: [], onChange() {}, onSave() {}, onCancel() {} }); };
    let tree = render();
    const collapse = nodes(tree).find(node => node.props['aria-label'] === 'Collapse rule list'); assert.ok(collapse);
    (collapse.props.onClick as () => void)(); tree = render();
    const summaries = nodes(tree).filter(node => node.props.className === 'risk-rule-summary');
    assert.equal(summaries.length, 2);
    for (const [position, summary] of summaries.entries()) {
      assert.ok(text(summary).includes(editor.rules[position]!.ruleName));
      assert.ok(text(summary).includes('condition'));
    }
    const entries = nodes(tree).filter(node => node.props.className === 'risk-rule-select');
    (entries[1]!.props.onClick as () => void)(); tree = render();
    assert.equal(nodes(tree).filter(node => node.props.className === 'risk-rule-select')[1]!.props['aria-current'], 'true');
    assert.equal(nodes(tree).filter(node => node.type === 'Button' && /^(Back|Back to List|Back to Source|Rule Configuration)$/.test(text(node))).length, 0);
  });
}
