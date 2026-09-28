import { variableContext, editHistoricalRule } from './variableFixtures.ts';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { transformWithOxc } from 'vite';
import { ApiError } from '../api/session.ts';
import type { SuccessDecoder } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import * as navigationModel from './riskNavigation.ts';
import * as pageModel from './riskPageModel.ts';
import type { RiskCommandResult, RiskCommandTarget, RiskPageCommandState } from './riskPageModel.ts';
import * as head from './ruleSetHead.ts';
import * as commands from './riskCommands.ts';
import * as builder from './ruleBuilder.ts';
import * as catalog from './riskAuthoringCatalog.ts';
import * as candidateState from './testCandidateState.ts';
import * as editorPresentation from './ruleEditorPresentation.ts';
import { createRiskDecisionApi } from './riskDecisionApi.ts';
import type { RuleSetDetail } from './riskApiModels.ts';
import { candidateDetail, ruleSet, ruleSetDetail, deleteRuleSetResponse, createRuleSetResponse, freezeRuleSetResponse, createCandidateResponse, validateCandidateResponse } from './riskFixtures.ts';

const app = readFileSync(new URL('../App.tsx', import.meta.url), 'utf8');
test('Risk is a lazy protected route with live resources, actor and refresh callback', () => {
  const registry = readFileSync(new URL('../navigation/ConsolePage.tsx', import.meta.url), 'utf8');
  assert.match(registry, /OPERATIONS_RISK_RULE_CONFIGURATION: lazy/);
  assert.ok(registry.includes("import('../pages/RiskRuleConfiguration')"));
  assert.match(app, /menus\.flatMap\(menu => pageRoutePaths\(menu\)/);
  assert.match(app, /key=\{`\$\{menu.resourceId\}:\$\{path\}`\}/);
  assert.match(app, /pageRoutePaths\(menu\)\.map\(path/);
  assert.match(app, /path=\{path\}/);
  assert.match(app, /<ConsolePage componentKey=\{menu.componentKey!\}/);
  assert.match(app, /resources=\{resources\} actorId=\{context.accountId\}/);
  assert.match(app, /onAuthorizationChanged=\{\(\) => setReload\(value => value \+ 1\)\}/);
  assert.doesNotMatch(registry, /RiskRuleConfiguration[^\n]*\bkey=/);
});
test('background authorization refresh preserves mounted routes and last valid resources', () => {
  const consoleSource = app.slice(app.indexOf('function Console('));
  assert.match(consoleSource, /const \[navigationLoaded, setNavigationLoaded\] = useState\(false\)/);
  assert.match(consoleSource, /setResources\(result.resources\);\s*setNavigationLoaded\(true\)/);
  assert.doesNotMatch(consoleSource, /setResources\(\[\]\)/);
  assert.match(consoleSource, /!navigationLoaded && navigationFailed/);
  assert.match(consoleSource, /!navigationLoaded && loading/);
  assert.match(consoleSource, /navigationLoaded && navigationFailed/);
  assert.match(consoleSource, /权限刷新失败，当前页面已保留/);
  assert.match(consoleSource, /controller\.signal\.aborted/);
});

test('paging accepts initial filters before its first effect and keeps legacy empty defaults', () => {
  const hook = readFileSync(new URL('../hooks/usePaging.ts', import.meta.url), 'utf8');
  assert.match(hook, /initialFilters: Readonly<Record<string, string>> = \{\}/);
  assert.match(hook, /filters: \{ \.\.\.initialFilters \}/);
});
test('page canonicalizes search and mounts only the active list', () => {
  const page = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.match(page, /parseRiskNavigation\(location.search\)/);
  assert.match(page, /riskNavigationSearch\(/);
  assert.match(page, /replace: true/);
  assert.match(page, /key=\{location.key\}/);
  assert.doesNotMatch(page, /<TestCandidateTab|<TestCandidateDetails|<Tabs/);
  assert.match(page, /CHECKOUT_PREPARE/);
});
test('Rule Set list/detail use decoded paging, complete head checks and explicit full-hash copy', () => {
  const list = readFileSync(new URL('RuleSetTab.tsx', import.meta.url), 'utf8');
  const detail = readFileSync(new URL('RuleSetDetails.tsx', import.meta.url), 'utf8');
  const page = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.match(list, /usePaging<RuleSetListItem>\(/);
  assert.match(list, /decodeRuleSetPage, navigation.filters/);
  assert.match(list, /rowKey="ruleSetId"/);
  assert.doesNotMatch(list, /title: '内容摘要'|title: '业务版本'/);
  assert.match(list, /RuleSetFilters/); assert.match(list, /ruleSetCode/);
  assert.match(detail, /detailToEditor\(detail\)/); assert.match(detail, /text: hash/); assert.match(detail, /复制完整 Hash/);
  assert.match(page, /loadObservedRuleSetHead\(/); assert.match(page, /requireSelectedObservedHead\(/); assert.match(page, /requireUnusedRuleSetCode\(/);
  assert.match(page, /loadObservedRuleSetHead\(/); assert.match(page, /controller\.signal\.aborted/);
});
test('structured editor uses pure conversion, string inputs, field direction and accessible reordering', () => {
  const editor = readFileSync(new URL('RuleSetEditor.tsx', import.meta.url), 'utf8');
  const condition = readFileSync(new URL('ConditionBuilder.tsx', import.meta.url), 'utf8');
  assert.match(editor, /editorToCreateRequest\(editor, base, catalog\)/);
  assert.match(editor, /saveSizeState\(payload\)/); assert.match(editor, /utf8ByteLength\(payload\)/);
  assert.match(editor, /onSave\(prepared.payload\)/);
  assert.match(editor, /readOnly=\{base !== null\}/);
  for (const token of ['draggable', 'onDragStart', 'onDragOver', 'onDrop', '上移', '下移']) assert.ok(editor.includes(token), token);
  for (const token of ['字段在左', '字段在右', 'fieldSide', '满足全部', '满足任一', '不满足', 'canonicalFieldValue', 'operatorsForField']) assert.ok(condition.includes(token), token);
  assert.doesNotMatch(condition, /InputNumber|parseFloat|Number\(/);
  for (const source of [editor, condition]) assert.doesNotMatch(source, /JSON\.parse|<pre\b|<code\b|nodeKey|parentNodeKey|valueSequence|sequenceNo|operandRole/);
});
test('command UI consumes the pure dispatcher/reducer and requires explicit same-envelope retry', () => {
  const page = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.match(page, /submitRiskCommand\(/); assert.match(page, /reduceRiskCommandResult\(/);
  assert.match(page, /randomCommand\(/); assert.match(page, /validateKey\(/);
  assert.match(page, /重试同一命令/); assert.match(page, /查看当前配置/);
  assert.match(page, /expectedContentHash: head.contentHash/);
  assert.match(page, /expectedVersion: row.managementVersion/);
  assert.doesNotMatch(page, /session\.request|JSON\.parse|console\./);
});
test('Candidate list/detail rely on decoded paging and the actor-bound pure recovery classification', () => {
  const list = readFileSync(new URL('TestCandidateTab.tsx', import.meta.url), 'utf8');
  const detail = readFileSync(new URL('TestCandidateDetails.tsx', import.meta.url), 'utf8');
  const page = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.match(list, /usePaging<TestCandidateListItem>\('\/decision-releases\/v1\/candidates', decodeCandidatePage, \{ pointCode: 'CHECKOUT_PREPARE' \}\)/);
  assert.match(list, /CandidateFilters/);
  assert.match(detail, /classifyTestCandidate\(detail, actorId\)/);
  for (const kind of ['READY_TO_VALIDATE', 'RECOVERABLE_BY_CURRENT_ACTOR', 'RECOVERY_OWNED_BY_OTHER_ACTOR', 'WAITING_FOR_SHADOW', 'REJECTED']) assert.ok(detail.includes(kind), kind);
  for (const text of ['恢复校验', '静态校验通过，等待未来 Shadow', '联系原操作员', 'staticChecks', 'stateChanges', 'frozenComposition']) assert.ok(detail.includes(text), text);
  assert.doesNotMatch(page, /riskDecisionApi\.candidate\(|创建 TEST Candidate|恢复校验/);
});
test('feature-local styles are imported only at page entry and form errors use presentation labels', () => {
  const page = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  const editor = readFileSync(new URL('RuleSetEditor.tsx', import.meta.url), 'utf8');
  const css = readFileSync(new URL('riskRuleConfiguration.css', import.meta.url), 'utf8');
  assert.match(page, /import '\.\.\/risk\/riskRuleConfiguration.css'/);
  assert.doesNotMatch(editor, /import.*\.css/);
  assert.match(page, /riskFieldErrorLabel\(error.field\)/); assert.match(editor, /riskFieldErrorLabel\(error.field\)/);
  assert.match(css, /\.risk-editor/); assert.match(css, /@media/);
});
test('detail failures remain visible over drawers and pending retries survive detail navigation', () => {
  const page = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.equal((page.match(/\{failureAlert\}/g) ?? []).length, 2);
  assert.match(page, /!state\.confirmation && navigation\.view !== 'detail' && failureAlert/);
  assert.match(page, /刷新列表/);
  assert.match(page, /keyboard=\{!commandChangeDisabled\}/);
  assert.doesNotMatch(page, /onSelect=\{id => \{ setNotice\(''\); setFailure\(null\)/);
  for (const name of ['RuleSetTab.tsx', 'TestCandidateTab.tsx']) {
    const tab = readFileSync(new URL(name, import.meta.url), 'utf8');
    assert.match(tab, /if \(error\) readFailure.current\(\)/);
  }
});
test('revoked retry keeps the previous unknown-result feedback and preflight checks current permission', () => {
  const page = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.match(page, /requireRiskAction\(live.current.resources, target.action\)/);
  assert.match(page, /setFailure\(previousFailure\)/);
  assert.match(page, /open=\{!!observedDetail\}[^>]*zIndex=\{1200\}/);
});
test('navigation clears deterministic field feedback while unknown-result retry feedback survives', () => {
  const page = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.match(page, /function clearNavigationFeedback\(\)[\s\S]*?setFailure\(value => value\?\.kind === 'UNKNOWN_RESULT' \? value : null\)/);
  assert.match(page, /key=\{location.key\}/);
  assert.match(page, /onNavigate\(withRiskView\(navigation, 'detail', row.ruleSetCode\)\)/);
});

// Execute the production component and its handlers in memory. Only React's hook host,
// visual leaf components and the HTTP transport are replaced; command/head/API code stays real.
interface RenderedNode { type: unknown; props: Record<string, unknown> }
interface PageControls {
  runCommand: (target: RiskCommandTarget, payload: string, retry?: boolean) => Promise<void>;
  openNew: () => Promise<void>;
  openLatest: (selected: RuleSetDetail, freeze?: boolean) => Promise<void>;
  openDelete: (row: typeof ruleSet) => void;
  update: (change: (state: RiskPageCommandState) => RiskPageCommandState) => void;
  setCatalog: (value: catalog.AuthoringMatch) => void;
  setBaseDetail: (value: RuleSetDetail | null) => void;
  setNewCode: (value: string) => void;
  newCodeOpen: boolean;
  newCode: string;
  current: { current: RiskPageCommandState };
  pendingTarget: { current: RiskCommandTarget | null };
  failure: Exclude<RiskCommandResult, { kind: 'SUCCESS' }> | null;
}
async function compileComponent(file: string, name: string, capture = '') {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    .replace(/^import .+;\n/gm, '').replace(/^export /gm, '')
    .replace('  return <div className="risk-rule-configuration">', `${capture}\n  return <div className="risk-rule-configuration">`);
  const { code } = await transformWithOxc(source, file, { jsx: { runtime: 'classic', pragma: 'jsx', pragmaFrag: 'Fragment' } });
  return (dependencies: Record<string, unknown>) => new Function(...Object.keys(dependencies), `${code}\nreturn ${name};`)(...Object.values(dependencies));
}
const loadPage = await compileComponent('../pages/RiskRuleConfiguration.tsx', 'RiskRuleConfigurationView',
  '  capture({ runCommand, openNew, openLatest, openDelete, update, setCatalog, setBaseDetail, setNewCode, newCodeOpen, newCode, current, pendingTarget, failure });');
const loadEditor = await compileComponent('RuleSetEditor.tsx', 'RuleSetEditor');
const actionTargets: readonly RiskCommandTarget[] = [{ action: 'SAVE' }, { action: 'FREEZE', ruleSetVersionId: '1' },
  { action: 'DELETE', ruleSetId: '100' }];
const actionResources = ['OPERATIONS_RISK_RULE_SET_DELETE', 'OPERATIONS_RISK_RULE_SET_SAVE', 'OPERATIONS_RISK_RULE_SET_FREEZE',
  'OPERATIONS_RISK_TEST_CANDIDATE_CREATE', 'OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE'].map((resourceCode, index): ConsoleResource => ({
  resourceId: String(index + 1), resourceCode, resourceType: 'BUTTON', parentResourceId: null,
  displayName: resourceCode, frontendRoute: null, componentKey: null, sortOrder: index,
}));
function nodes(value: unknown): RenderedNode[] {
  if (Array.isArray(value)) return value.flatMap(nodes);
  if (!value || typeof value !== 'object' || !('props' in value)) return [];
  const node = value as RenderedNode;
  return [node, ...Object.values(node.props).flatMap(nodes)];
}
function componentHarness(options: { effects?: boolean; editor?: boolean; search?: string } = {}) {
  const hooks: unknown[] = []; let hook = 0; let controls: PageControls;
  const effects: { dependencies: readonly unknown[]; cleanup?: void | (() => void) }[] = [];
  let effectIndex = 0; let scheduled: (() => void)[] = []; let dirty = false;
  const model = { resources: [...actionResources], observed: ruleSetDetail as RuleSetDetail | null,
    postError: new ApiError(503, 'UNAVAILABLE', 'save-unknown') as unknown, readError: null as unknown,
    candidateReadError: null as unknown, candidate: candidateDetail(), search: options.search ?? (options.editor === false ? '?view=detail&selectedRuleSetCode=RULE_SET' : '?view=edit&selectedRuleSetCode=RULE_SET'),
    beforeRead: () => {}, refreshes: 0, reads: 0 };
  const posts: { path: string; body: BodyInit | null | undefined; key: string | null }[] = [];
  const api = createRiskDecisionApi({ async request<T>(path: string, options?: RequestInit, decode?: SuccessDecoder<T>): Promise<T> {
    if (options?.method === 'POST' || options?.method === 'DELETE') {
      posts.push({ path, body: options.body, key: new Headers(options.headers).get('Idempotency-Key') });
      if (model.postError) throw model.postError;
      const response = options?.method === 'DELETE' ? deleteRuleSetResponse : path.endsWith('/freeze') ? freezeRuleSetResponse : path.endsWith('/validate') ? validateCandidateResponse(true)
        : path === '/decision-releases/v1/candidates' ? createCandidateResponse : createRuleSetResponse;
      return decode!(response);
    }
    model.reads++; model.beforeRead();
    if (path.includes('/authoring-context/')) return decode!(variableContext);
    if (path.startsWith('/decision-releases/v1/candidates/')) {
      if (model.candidateReadError) throw model.candidateReadError;
      return decode!(model.candidate);
    }
    if (model.readError) throw model.readError;
    if (path.includes('?')) {
      const query = new URL(path, 'http://in-memory.invalid').searchParams;
      const items = model.observed ? [{ ...Object.fromEntries(Object.entries(model.observed).filter(([key]) => key !== 'rules')), ruleSetId: '100', managementVersion: '1', inUse: false }] : [];
      return decode!({ page: Number(query.get('page')), size: Number(query.get('size')), total: items.length, items });
    }
    return decode!(model.observed);
  } });
  const dependencies = { ...navigationModel, ...pageModel, ...head, ...commands, ...builder, ...catalog, ...candidateState, ...editorPresentation,
    ruleForVariableEditing: editHistoricalRule, ApiError, riskDecisionApi: api, Fragment: 'Fragment', App: { useApp: () => ({ modal: { confirm() {} } }) },
    jsx: (type: unknown, props: Record<string, unknown> | null, ...children: unknown[]): RenderedNode => ({ type, props: { ...props, children } }),
    useState<T>(initial: T | (() => T)) {
      const index = hook++;
      if (!(index in hooks)) hooks[index] = typeof initial === 'function' ? (initial as () => T)() : initial;
      return [hooks[index], (next: T | ((previous: T) => T)) => {
        const value = typeof next === 'function' ? (next as (previous: T) => T)(hooks[index] as T) : next;
        if (!Object.is(value, hooks[index])) dirty = true;
        hooks[index] = value;
      }];
    },
    useRef<T>(initial: T) { const index = hook++; return hooks[index] ??= { current: initial }; },
    useEffect(effect: () => void | (() => void), dependencies: readonly unknown[]) {
      if (!options.effects) return;
      const index = effectIndex++; const previous = effects[index];
      if (previous && dependencies.every((value, position) => Object.is(value, previous.dependencies[position]))) return;
      scheduled.push(() => { previous?.cleanup?.(); effects[index] = { dependencies, cleanup: effect() }; });
    },
    useCallback<T>(value: T) { return value; },
    useLayoutEffect(effect: () => void) { effect(); }, useMemo<T>(value: () => T) { return value(); },
    useLocation: () => ({ search: model.search }), useNavigate: () => navigate,
    capture: (value: PageControls) => { controls = value; },
    ...Object.fromEntries(['Alert', 'Button', 'Drawer', 'Input', 'Modal', 'Space', 'Spin', 'Tabs', 'Checkbox', 'Form', 'Select', 'Dropdown', 'Radio', 'Switch', 'Tag', 'Tooltip', 'ConditionView',
      'RuleSetTab', 'RuleSetDetails', 'ContentHash', 'TestCandidateTab', 'TestCandidateDetails', 'ConditionBuilder', 'AdditionalActionEditor'].map(name => [name, name])),
  };
  const Editor = loadEditor(dependencies) as (props: Record<string, unknown>) => RenderedNode;
  const Page = loadPage({ ...dependencies, RuleSetEditor: Editor }) as (props: Record<string, unknown>) => RenderedNode;
  let tree: RenderedNode;
  function navigate(location: { search: string }) { model.search = location.search; dirty = true; }
  let navigation = navigationModel.parseRiskNavigation(model.search);
  let previousSearch = model.search;
  function render() {
    if (model.search !== previousSearch) { previousSearch = model.search; navigation = navigationModel.parseRiskNavigation(model.search); }
    hook = 0; effectIndex = 0; dirty = false;
    tree = Page({ navigation, resources: model.resources, actorId: '3', onAuthorizationChanged: () => model.refreshes++ });
  }
  async function flush() {
    for (let turn = 0; turn < 20; turn++) {
      if (dirty) render();
      const batch = scheduled; scheduled = []; batch.forEach(effect => effect());
      await new Promise<void>(resolve => setImmediate(resolve));
      if (!dirty && scheduled.length === 0) return;
    }
    assert.fail('page effects did not settle');
  }
  render();
  if (!options.effects) controls!.setCatalog({ editable: true, catalog: catalog.CHECKOUT_PREPARE_AUTHORING_V1 });
  if (options.editor !== false) {
    controls!.setBaseDetail(ruleSetDetail);
    controls!.update(state => ({ ...state, editor: builder.detailToEditor(ruleSetDetail) }));
  }
  if (!options.effects) render();
  return {
    model, posts, render, flush, get controls() { return controls!; },
    node(type: string, title?: string) {
      const node = nodes(tree!).find(node => node.type === type && (title === undefined || node.props.title === title));
      assert.ok(node, `${type} ${title ?? ''} exists`); return node;
    },
    button(label: string, scope: unknown = tree) {
      const node = nodes(scope).find(node => node.type === 'Button' && (node.props.children as unknown[]).includes(label));
      assert.ok(node, `button ${label} exists`); return node;
    },
    async act(handler: unknown, ...args: unknown[]) { await (handler as (...values: unknown[]) => unknown)(...args); await flush(); },
    async run(target: RiskCommandTarget, payload: string, retry = false) { await controls!.runCommand(target, payload, retry); render(); },
    editor() { const node = nodes(tree!).find(node => node.type === Editor); assert.ok(node, 'editor remains mounted'); return node; },
    saveButton() {
      const editor = nodes(Editor(this.editor().props));
      const button = editor.find(node => node.type === 'Button' && (node.props.children as unknown[]).includes('保存新版本'));
      assert.ok(button); return button;
    },
    retryButtons() { return nodes(tree!).filter(node => node.type === 'Button' && (node.props.children as unknown[]).includes('重试同一命令')); },
  };
}
const savePayload = JSON.stringify(builder.editorToCreateRequest(builder.detailToEditor(ruleSetDetail), ruleSetDetail));
test('UNKNOWN Save followed by normal Save keeps failure, identical pending and explicit retry after head advanced', async () => {
  const page = componentHarness();
  await page.run({ action: 'SAVE' }, savePayload);
  const pending = page.controls.current.current.pendingCommand;
  const failure = page.controls.failure;
  assert.equal(failure?.kind, 'UNKNOWN_RESULT'); assert.ok(page.retryButtons().length);
  page.model.observed = { ...ruleSetDetail, ruleSetVersionId: '8', versionNo: 2 };
  await page.run({ action: 'SAVE' }, savePayload);
  assert.equal(page.posts.length, 1);
  assert.equal(page.controls.current.current.pendingCommand, pending);
  assert.equal(page.controls.failure, failure, 'preflight must not erase UNKNOWN_RESULT');
  assert.ok(page.retryButtons().length, 'the explicit retry remains reachable');
  assert.equal(page.saveButton().props.disabled, true, 'ordinary Save is disabled while its command is unresolved');
});
test('editing after UNKNOWN preserves the original envelope and retry sends its body instead of edited content', async () => {
  const page = componentHarness();
  await page.run({ action: 'SAVE' }, savePayload);
  const pending = page.controls.current.current.pendingCommand;
  const failure = page.controls.failure;
  const editor = { ...page.controls.current.current.editor!, ruleSetName: 'Edited after unknown' };
  (page.editor().props.onChange as (value: builder.RuleSetEditorModel) => void)(editor); page.render();
  assert.equal(page.controls.current.current.editor, editor);
  assert.equal(page.controls.current.current.pendingCommand, pending);
  assert.equal(page.controls.failure, failure); assert.ok(page.retryButtons().length);
  const editedPayload = JSON.stringify(builder.editorToCreateRequest(editor, ruleSetDetail));
  await page.run({ action: 'SAVE' }, editedPayload);
  assert.equal(page.posts.length, 1); assert.equal(page.saveButton().props.disabled, true);
  await page.run({ action: 'SAVE' }, pending!.payload, true);
  assert.equal(page.posts.length, 2); assert.deepEqual(page.posts[1], page.posts[0]);
  assert.equal(page.controls.current.current.pendingCommand, pending);
  assert.equal(page.controls.current.current.editor, editor);
});
test('UNKNOWN Save disables only the Save action while editor controls remain available', async () => {
  const page = componentHarness();
  assert.equal(page.saveButton().props.disabled, false);
  await page.run({ action: 'SAVE' }, savePayload);
  assert.equal(page.saveButton().props.disabled, true);
  assert.equal(page.editor().props.disabled, false);
});
for (const target of actionTargets) {
  test(`${target.action} unresolved command rejects ordinary submissions and mismatched retries, then settles exact retry`, async () => {
    const page = componentHarness();
    const payload = target.action === 'SAVE' ? savePayload : target.action === 'FREEZE' ? JSON.stringify({ expectedContentHash: ruleSetDetail.contentHash })
      : target.action === 'CREATE_CANDIDATE' ? JSON.stringify({ ruleSetVersionId: '1', expectedRuleSetContentHash: ruleSetDetail.contentHash }) : '{"expectedVersion":"1"}';
    await page.run(target, payload);
    const pending = page.controls.current.current.pendingCommand;
    const failure = page.controls.failure;
    assert.equal(failure?.kind, 'UNKNOWN_RESULT'); assert.ok(pending);
    for (const other of actionTargets) await page.run(other, payload);
    await page.run(target, `${payload} `, true);
    for (const other of actionTargets.filter(other => other.action !== target.action)) await page.run(other, payload, true);
    if (target.action === 'FREEZE') await page.run({ action: 'FREEZE', ruleSetVersionId: '99' }, payload, true);
    if (target.action === 'VALIDATE_CANDIDATE') await page.run({ action: 'VALIDATE_CANDIDATE', releaseId: '99' }, payload, true);
    assert.equal(page.posts.length, 1);
    assert.equal(page.controls.current.current.pendingCommand, pending); assert.equal(page.controls.failure, failure);
    assert.ok(page.retryButtons().length);
    await page.run(target, payload, true);
    assert.equal(page.posts.length, 2); assert.deepEqual(page.posts[1], page.posts[0]);
    assert.equal(page.controls.current.current.pendingCommand, pending);
    page.model.postError = null;
    await page.run(target, payload, true);
    assert.equal(page.posts.length, 3); assert.deepEqual(page.posts[2], page.posts[0]);
    assert.equal(page.controls.current.current.pendingCommand, null); assert.equal(page.controls.failure, null);
    assert.equal(page.retryButtons().length, 0);
    await page.run(target, payload, true);
    assert.equal(page.posts.length, 3, 'retry without an existing envelope cannot mint a command');
  });
}
test('unknown retry survives catalog and permission preflight failures and a head-read error', async () => {
  const page = componentHarness();
  await page.run({ action: 'SAVE' }, savePayload);
  const pending = page.controls.current.current.pendingCommand;
  const failure = page.controls.failure;
  page.controls.setCatalog({ editable: false, reason: 'Unavailable' }); page.render();
  await page.run({ action: 'SAVE' }, savePayload, true);
  assert.equal(page.controls.failure, failure); assert.equal(page.controls.current.current.pendingCommand, pending);
  assert.ok(page.retryButtons().length); assert.equal(page.posts.length, 1);
  page.controls.setCatalog({ editable: true, catalog: catalog.CHECKOUT_PREPARE_AUTHORING_V1 });
  page.model.resources = []; page.render();
  await page.run({ action: 'SAVE' }, savePayload, true);
  assert.equal(page.model.refreshes, 1); assert.equal(page.posts.length, 1);
  assert.equal(page.controls.failure, failure); assert.equal(page.controls.current.current.pendingCommand, pending);
  page.model.resources = [...actionResources]; page.model.readError = new TypeError('read failed'); page.render();
  await page.run({ action: 'SAVE' }, savePayload);
  assert.equal(page.controls.failure, failure); assert.ok(page.retryButtons().length);
  const reads = page.model.reads;
  page.model.postError = new ApiError(422, 'VALIDATION_ERROR', 'retry-422', undefined, [{ field: 'ruleSetName', message: 'Please correct' }]);
  await page.run({ action: 'SAVE' }, savePayload, true);
  assert.equal(page.model.reads, reads, 'exact retry does not repeat observed-head inference');
  assert.equal(page.posts.length, 2); assert.deepEqual(page.posts[1], page.posts[0]);
  assert.equal(page.controls.current.current.pendingCommand, null);
  assert.equal(page.controls.failure?.kind, 'HTTP_FAILURE');
  assert.equal(page.saveButton().props.disabled, false);
  page.model.readError = null;
  await page.run({ action: 'SAVE' }, savePayload);
  assert.equal(page.posts.length, 3); assert.notEqual(page.posts[2]?.key, page.posts[0]?.key);
});
test('UNKNOWN Save retains edited content and explicit retry without page return controls', async () => {
  const page = componentHarness();
  await page.run({ action: 'SAVE' }, savePayload);
  const pending = page.controls.current.current.pendingCommand;
  const failure = page.controls.failure;
  const editor = page.controls.current.current.editor;
  assert.equal(page.editor().props.onCancel, undefined);
  page.render();
  assert.equal(page.controls.current.current.pendingCommand, pending);
  assert.equal(page.controls.current.current.editor, editor);
  assert.equal(page.controls.failure, failure); assert.ok(page.retryButtons().length);
  assert.equal(page.saveButton().props.disabled, true);
  page.model.postError = new ApiError(422, 'VALIDATION_ERROR');
  await page.run({ action: 'SAVE' }, savePayload, true);
  assert.equal(page.saveButton().props.disabled, false);
  assert.equal(navigationModel.parseRiskNavigation(page.model.search).view, 'edit');
  assert.equal(page.controls.current.current.pendingCommand, null);
});

const modalCases = [
  { action: 'FREEZE', title: '确认冻结版本', entry: '冻结版本' },
  { action: 'DELETE', title: '确认删除规则集', entry: '删除' },
] as const;
async function openCommandModal(action: typeof modalCases[number]['action']) {
  const page = componentHarness({ effects: true, editor: false, search: '?view=list' });
  await page.flush();
  if (action === 'DELETE') await page.act(page.controls.openDelete, ruleSet);
  else await page.act(page.node('RuleSetTab').props.onFreeze, ruleSet);
  return page;
}
function recoverySnapshot(page: ReturnType<typeof componentHarness>) {
  return { pending: page.controls.current.current.pendingCommand, target: page.controls.pendingTarget.current, failure: page.controls.failure };
}
function assertRecovery(page: ReturnType<typeof componentHarness>, expected: ReturnType<typeof recoverySnapshot>) {
  assert.ok(expected.pending); assert.equal(expected.failure?.kind, 'UNKNOWN_RESULT');
  assert.equal(page.controls.current.current.pendingCommand, expected.pending, 'the unresolved envelope must remain identical');
  assert.equal(page.controls.pendingTarget.current, expected.target, 'the recovery target must remain identical');
  assert.equal(page.controls.failure, expected.failure, 'UNKNOWN feedback must remain reachable');
  assert.ok(page.retryButtons().length, 'same-command retry is still rendered');
}
for (const entry of modalCases) {
  test(`Round2 ${entry.action} UNKNOWN ignores exposed Modal cancel and keeps exact retry through settlement`, async () => {
    const page = await openCommandModal(entry.action);
    const modal = page.node('Modal', entry.title);
    assert.equal(modal.props.open, true);
    const confirmation = page.controls.current.current.confirmation;
    await page.act(modal.props.onOk);
    const recovery = recoverySnapshot(page);
    await page.act(modal.props.onCancel);
    assertRecovery(page, recovery);
    assert.equal(page.controls.current.current.confirmation, confirmation, 'Cancel/Escape/mask cannot discard the unresolved confirmation');
    const pendingModal = page.node('Modal', entry.title);
    assert.equal(pendingModal.props.open, true);
    assert.equal(pendingModal.props.keyboard, false);
    assert.deepEqual(pendingModal.props.mask, { closable: false });
    assert.equal(pendingModal.props.closable, false);
    assert.equal((pendingModal.props.cancelButtonProps as { disabled: boolean }).disabled, true);
    assert.equal((pendingModal.props.okButtonProps as { disabled: boolean }).disabled, true);
    await page.act(pendingModal.props.onOk);
    assert.equal(page.posts.length, 1, 'the same body cannot become a new ordinary command');
    assertRecovery(page, recovery);
    await page.act(page.retryButtons()[0]!.props.onClick);
    assert.equal(page.posts.length, 2); assert.deepEqual(page.posts[1], page.posts[0]);
    assert.equal(page.controls.current.current.pendingCommand, recovery.pending, 'another 503 keeps the same envelope');
    page.model.postError = new ApiError(400, 'BUSINESS_REJECTED');
    await page.act(page.retryButtons()[0]!.props.onClick);
    assert.deepEqual(page.posts[2], page.posts[0]);
    assert.equal(page.controls.current.current.pendingCommand, null); assert.equal(page.controls.pendingTarget.current, null);
    const settledModal = page.node('Modal', entry.title);
    assert.equal(settledModal.props.keyboard, true); assert.deepEqual(settledModal.props.mask, { closable: true });
    assert.equal(settledModal.props.closable, true);
    assert.equal((settledModal.props.cancelButtonProps as { disabled: boolean }).disabled, false);
    assert.equal((settledModal.props.okButtonProps as { disabled: boolean }).disabled, false);
    page.model.postError = null;
    await page.act(settledModal.props.onOk);
    assert.equal(page.posts.length, 4); assert.equal(page.posts[3]?.body, page.posts[0]?.body);
    assert.notEqual(page.posts[3]?.key, page.posts[0]?.key, 'a new random key is allowed only after deterministic settlement');
    assert.equal(page.controls.current.current.pendingCommand, null); assert.equal(page.controls.failure, null);
    assert.equal(page.controls.current.current.confirmation, null);
    assert.equal(page.node('Modal', '确认冻结版本').props.open, false);
  });
}
for (const freeze of [false, true]) {
  test(`Round2 UNKNOWN blocks direct openLatest ${freeze ? 'Freeze' : 'Edit'} before it clears recovery`, async () => {
    const page = await openCommandModal('FREEZE');
    const openLatest = page.controls.openLatest;
    await page.act(page.node('Modal', '确认冻结版本').props.onOk);
    const recovery = recoverySnapshot(page); const reads = page.model.reads;
    await page.act(openLatest, ruleSetDetail, freeze);
    assert.equal(page.model.reads, reads); assertRecovery(page, recovery);
    assert.equal(page.controls.current.current.editor, null);
    assert.equal(page.node('RuleSetTab').props.disabled, true);
  });
}
for (const readOutcome of ['failure', 'success'] as const) {
  test(`Round2 UNKNOWN directly guards openNew before a ${readOutcome} head lookup`, async () => {
    const page = await openCommandModal('FREEZE');
    page.controls.setNewCode('UNUSED_RULE_SET'); await page.flush();
    const openNew = page.controls.openNew;
    await page.act(page.node('Modal', '确认冻结版本').props.onOk);
    const recovery = recoverySnapshot(page); const reads = page.model.reads;
    page.model.observed = null;
    page.model.readError = readOutcome === 'failure' ? new TypeError('head lookup unavailable') : null;
    await page.act(openNew);
    assertRecovery(page, recovery);
    assert.equal(page.model.reads, reads, 'neither a failed nor a successful lookup may run while unresolved');
    assert.equal(page.controls.current.current.editor, null);
  });
}
for (const flow of ['New', 'Edit', 'Freeze'] as const) {
  test(`Round2 ${flow} head continuation cannot clear a command started after its lookup began`, async () => {
    const page = await openCommandModal('FREEZE');
    page.controls.setNewCode('UNUSED_RULE_SET'); await page.flush();
    const pendingSubmission = page.controls.runCommand;
    const payload = '{"expectedVersion":"1"}';
    if (flow === 'New') page.model.observed = null;
    let submission: Promise<void> | undefined;
    page.model.beforeRead = () => {
      page.model.beforeRead = () => {};
      submission = pendingSubmission({ action: 'DELETE', ruleSetId: '100' }, payload);
    };
    if (flow === 'New') await page.controls.openNew();
    else await page.controls.openLatest(ruleSetDetail, flow === 'Freeze');
    await submission; await page.flush();
    assert.equal(page.posts.length, 1);
    const recovery = recoverySnapshot(page);
    assert.equal(recovery.failure?.kind, 'UNKNOWN_RESULT');
    assert.ok(recovery.pending, 'a late successful head lookup cannot discard the newly pending command');
    assert.equal(recovery.pending.path, '/decision-rule-sets/v1/rule-sets/100');
    assert.equal(page.controls.current.current.editor, null);
    assert.ok(page.retryButtons().length);
    await page.act(page.retryButtons()[0]!.props.onClick);
    assert.deepEqual(page.posts[1], page.posts[0]);
  });
}
