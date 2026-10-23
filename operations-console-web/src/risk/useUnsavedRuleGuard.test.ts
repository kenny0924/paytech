import { variableContext, editHistoricalRule } from './variableFixtures.ts';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { StrictMode, act, createElement, useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import type { Blocker } from 'react-router-dom';
import { transformWithOxc } from 'vite';
import { ApiError } from '../api/session.ts';
import type { SuccessDecoder } from '../api/session.ts';
import * as pageModel from './riskPageModel.ts';
import type { RiskCommandTarget, RiskPageCommandState } from './riskPageModel.ts';
import * as head from './ruleSetHead.ts';
import * as commands from './riskCommands.ts';
import * as builder from './ruleBuilder.ts';
import * as catalog from './riskAuthoringCatalog.ts';
import * as navigationModel from './riskNavigation.ts';
import * as candidateState from './testCandidateState.ts';
import { createRiskDecisionApi } from './riskDecisionApi.ts';
import { ruleSet, ruleSetDetail, createRuleSetResponse } from './riskFixtures.ts';
import * as unsavedGuard from './useUnsavedRuleGuard.ts';
import { testT } from '../i18n/testing.ts';
import { createBlockedNavigationController, installBeforeUnload, nextBlockedNavigation } from './useUnsavedRuleGuard.ts';
import type { BeforeUnloadTarget, BlockedNavigation } from './useUnsavedRuleGuard.ts';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise; reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function navigation() {
  const calls = { proceed: 0, reset: 0 };
  const blocked: BlockedNavigation = {
    proceed: () => { calls.proceed++; },
    reset: () => { calls.reset++; },
  };
  return { blocked, calls };
}

async function checkpoint() {
  await new Promise<void>(resolve => setImmediate(resolve));
}

type RouterBlockedNavigation = Extract<Blocker, { state: 'blocked' }>;
interface CommittedRouterAdapter extends BlockedNavigation {
  commit(current: Blocker): void;
  revoke(): void;
}
type RouterAdapterFactory = (blocked: RouterBlockedNavigation, requestCommit: () => void) => CommittedRouterAdapter;
const routerAdapter = (unsavedGuard as unknown as { createRouterBlockedNavigationAdapter?: RouterAdapterFactory })
  .createRouterBlockedNavigationAdapter ?? ((blocked: RouterBlockedNavigation) => blocked as unknown as CommittedRouterAdapter);

function currentRouterBlocker(router: ReturnType<typeof createMemoryRouter>): RouterBlockedNavigation {
  const blocker = router.state.blockers.get('risk-unsaved');
  assert.equal(blocker?.state, 'blocked');
  return blocker as RouterBlockedNavigation;
}

function currentHookBlocker(router: ReturnType<typeof createMemoryRouter>): RouterBlockedNavigation {
  const blockers = [...router.state.blockers.values()].filter(candidate => candidate.state === 'blocked');
  assert.equal(blockers.length, 1, 'the mounted guard should own one current blocked navigation');
  return blockers[0] as RouterBlockedNavigation;
}

interface ReactRouterGuardHarness {
  router: ReturnType<typeof createMemoryRouter>;
  setDirty(value: boolean): void;
  unmountReact(): Promise<void>;
  dispose(): Promise<void>;
}

async function mountReactRouterGuard(confirmLeave: () => Promise<boolean>): Promise<ReactRouterGuardHarness> {
  const globalObject = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
    window?: Window & typeof globalThis;
    document?: Document;
  };
  const previousActEnvironment = Object.getOwnPropertyDescriptor(globalObject, 'IS_REACT_ACT_ENVIRONMENT');
  const previousWindow = Object.getOwnPropertyDescriptor(globalObject, 'window');
  const previousDocument = Object.getOwnPropertyDescriptor(globalObject, 'document');
  class ElementShim {}
  const windowShim = {
    event: undefined,
    HTMLElement: ElementShim,
    HTMLIFrameElement: ElementShim,
    addEventListener() {},
    removeEventListener() {},
    getSelection() { return null; },
  };
  const documentShim = {
    nodeType: 9,
    defaultView: windowShim,
    documentElement: {},
    activeElement: null,
    addEventListener() {},
    removeEventListener() {},
    createElement() { return {}; },
  };
  Object.defineProperty(globalObject, 'IS_REACT_ACT_ENVIRONMENT', { configurable: true, writable: true, value: true });
  Object.defineProperty(globalObject, 'window', { configurable: true, writable: true, value: windowShim });
  Object.defineProperty(globalObject, 'document', { configurable: true, writable: true, value: documentShim });
  const container = {
    nodeType: 1,
    tagName: 'DIV',
    nodeName: 'DIV',
    ownerDocument: documentShim,
    addEventListener() {},
    removeEventListener() {},
  };
  let updateDirty!: Dispatch<SetStateAction<boolean>>;
  function GuardHost() {
    const [dirty, setDirty] = useState(true);
    updateDirty = setDirty;
    unsavedGuard.useUnsavedRuleGuard(dirty, confirmLeave);
    return null;
  }
  const router = createMemoryRouter([{ path: '*', element: createElement(GuardHost) }], { initialEntries: ['/start'] });
  const root: Root = createRoot(container as unknown as Element);
  let mounted = true;
  const restoreGlobal = (name: 'IS_REACT_ACT_ENVIRONMENT' | 'window' | 'document',
    descriptor: PropertyDescriptor | undefined) => {
    if (descriptor) Object.defineProperty(globalObject, name, descriptor);
    else Reflect.deleteProperty(globalObject, name);
  };
  await act(async () => {
    root.render(createElement(StrictMode, null, createElement(RouterProvider, { router })));
  });
  async function unmountReact() {
    if (!mounted) return;
    mounted = false;
    await act(async () => { root.unmount(); });
    restoreGlobal('document', previousDocument);
    restoreGlobal('window', previousWindow);
    restoreGlobal('IS_REACT_ACT_ENVIRONMENT', previousActEnvironment);
  }
  return {
    router,
    setDirty(value) { updateDirty(value); },
    unmountReact,
    async dispose() { await unmountReact(); router.dispose(); },
  };
}

interface SavePageControls {
  runCommand(target: RiskCommandTarget, payload: string, retry?: boolean): Promise<void>;
  current: { current: RiskPageCommandState };
}

const savePageSource = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8')
  .replace(/^import .+;\n/gm, '').replace(/^export /gm, '')
  .replace('  return <div className="risk-rule-configuration">',
    '  captureSavePage({ runCommand, current });\n  return <div className="risk-rule-configuration">');
const { code: compiledSavePageSource } = await transformWithOxc(savePageSource, 'RiskRuleConfiguration.tsx', {
  jsx: { runtime: 'classic', pragma: 'jsx', pragmaFrag: 'Fragment' },
});

function saveNavigationHarness(postResult: ReturnType<typeof deferred<unknown>>, initialSearch = navigationModel.riskNavigationSearch(navigationModel.withRiskView(navigationModel.parseRiskNavigation(''), 'edit', ruleSet.ruleSetCode))) {
  const hooks: unknown[] = []; let hook = 0; let effectIndex = 0; let controls!: SavePageControls;
  let scheduled: (() => void)[] = []; let renderPending = false; let renderedDirty = false;
  const effects: { dependencies: readonly unknown[]; cleanup?: void | (() => void) }[] = [];
  const router = createMemoryRouter([{ path: '*', element: null }], { initialEntries: [`/${initialSearch}`] });
  router.getBlocker('save-dirty', () => renderedDirty);
  let navigationAttempts = 0;
  const unsubscribe = router.subscribe(() => { renderPending = true; });
  const api = createRiskDecisionApi({ async request<T>(path: string, options?: RequestInit, decode?: SuccessDecoder<T>): Promise<T> {
    if (options?.method === 'POST') return decode!(await postResult.promise);
    if (path.includes('/authoring-context/')) return decode!(variableContext);
    if (path.includes('/rule-sets?')) return decode!({ items: [ruleSet], page: 1, size: 10, total: 1 });
    if (path.endsWith('/rule-sets/' + ruleSet.ruleSetVersionId)) return decode!(ruleSetDetail);
    throw new Error(`unexpected GET ${path}`);
  } });
  const navigate = (to: { search: string }, options?: { replace?: boolean }) => {
    navigationAttempts++;
    return router.navigate({ pathname: '/', search: to.search }, options);
  };
  const dependencies = {
    ...navigationModel, ...pageModel, ...head, ...commands, ...builder, ...catalog, ...candidateState,
    ruleForVariableEditing: editHistoricalRule, ApiError, riskDecisionApi: api, Fragment: 'Fragment', useT: testT,
    App: { useApp: () => ({ modal: { confirm() {} } }) },
    ...Object.fromEntries(['Alert', 'Button', 'Drawer', 'Input', 'Modal', 'Space', 'Spin', 'Tabs', 'RuleSetTab',
      'RuleSetDetails', 'ContentHash', 'RuleSetEditor', 'TestCandidateTab', 'TestCandidateDetails'].map(name => [name, name])),
    jsx: (type: unknown, props: Record<string, unknown> | null, ...children: unknown[]) => {
      if (typeof type === 'function' && type.name === 'RuleConfigurationUnsavedGuard') renderedDirty = props?.dirty === true;
      return { type, props: { ...props, children } };
    },
    useCallback<T>(value: T) { return value; },
    useRef<T>(initial: T) { const index = hook++; return hooks[index] ??= { current: initial }; },
    useState<T>(initial: T | (() => T)) {
      const index = hook++;
      if (!(index in hooks)) hooks[index] = typeof initial === 'function' ? (initial as () => T)() : initial;
      return [hooks[index], (next: T | ((previous: T) => T)) => {
        const value = typeof next === 'function' ? (next as (previous: T) => T)(hooks[index] as T) : next;
        if (!Object.is(value, hooks[index])) renderPending = true;
        hooks[index] = value;
      }];
    },
    useLayoutEffect(effect: () => void) { effect(); },
    useEffect(effect: () => void | (() => void), dependencies: readonly unknown[]) {
      const index = effectIndex++; const previous = effects[index];
      if (previous && previous.dependencies.length === dependencies.length
        && dependencies.every((value, position) => Object.is(value, previous.dependencies[position]))) return;
      scheduled.push(() => { previous?.cleanup?.(); effects[index] = { dependencies, cleanup: effect() }; });
    },
    useLocation: () => router.state.location, useNavigate: () => navigate,
    useUnsavedRuleGuard() {}, captureSavePage(value: SavePageControls) { controls = value; },
  };
  const Page = new Function(...Object.keys(dependencies), `${compiledSavePageSource}\nreturn RiskRuleConfigurationView;`)
    (...Object.values(dependencies)) as (props: { navigation: navigationModel.RiskNavigation; resources: unknown[]; actorId: string; onAuthorizationChanged(): void }) => unknown;
  const resources = [{ resourceType: 'BUTTON', resourceCode: 'OPERATIONS_RISK_RULE_SET_SAVE' }];
  let previousSearch = router.state.location.search;
  let navigation = navigationModel.parseRiskNavigation(previousSearch);
  function render() {
    if (previousSearch !== router.state.location.search) {
      previousSearch = router.state.location.search; navigation = navigationModel.parseRiskNavigation(previousSearch);
    }
    hook = 0; effectIndex = 0; renderPending = false;
    Page({ navigation, resources, actorId: '3', onAuthorizationChanged() {} });
  }
  async function flush() {
    for (let turn = 0; turn < 30; turn++) {
      if (renderPending) render();
      const batch = scheduled; scheduled = []; batch.forEach(effect => effect());
      await checkpoint();
      if (!renderPending && scheduled.length === 0) return;
    }
    assert.fail('Save page effects did not settle');
  }
  render();
  return {
    router, get controls() { return controls; }, get navigationAttempts() { return navigationAttempts; },
    render, flush,
    dispose() { unsubscribe(); effects.forEach(effect => effect.cleanup?.()); router.dispose(); },
  };
}

test('beforeunload blocks only while dirty and cleanup removes the exact listener once', () => {
  const added: ((event: BeforeUnloadEvent) => void)[] = [];
  const removed: ((event: BeforeUnloadEvent) => void)[] = [];
  const target: BeforeUnloadTarget = {
    addEventListener(type, value) { assert.equal(type, 'beforeunload'); added.push(value); },
    removeEventListener(type, value) { assert.equal(type, 'beforeunload'); removed.push(value); },
  };
  let dirty = false;
  const cleanup = installBeforeUnload(target, () => dirty);
  assert.equal(added.length, 1); const listener = added[0]!;
  const cleanEvent = { returnValue: 'unchanged', preventDefaultCalls: 0,
    preventDefault() { this.preventDefaultCalls++; } };
  listener(cleanEvent as unknown as BeforeUnloadEvent);
  assert.equal(cleanEvent.preventDefaultCalls, 0); assert.equal(cleanEvent.returnValue, 'unchanged');
  dirty = true;
  const dirtyEvent = { returnValue: 'unchanged', preventDefaultCalls: 0,
    preventDefault() { this.preventDefaultCalls++; } };
  listener(dirtyEvent as unknown as BeforeUnloadEvent);
  assert.equal(dirtyEvent.preventDefaultCalls, 1); assert.equal(dirtyEvent.returnValue, '');
  cleanup(); cleanup();
  assert.deepEqual(removed, [listener]);
});

test('next blocked navigation maps only an explicit confirmation to proceed', () => {
  assert.equal(nextBlockedNavigation(true), 'PROCEED');
  assert.equal(nextBlockedNavigation(false), 'RESET');
});

test('confirmed and cancelled blockers receive exactly one terminal action', async () => {
  for (const confirmed of [true, false]) {
    const current = navigation();
    const controller = createBlockedNavigationController(async () => confirmed);
    controller.handle(current.blocked, true);
    await checkpoint();
    assert.deepEqual(current.calls, confirmed ? { proceed: 1, reset: 0 } : { proceed: 0, reset: 1 });
    controller.handle(current.blocked, true);
    await checkpoint();
    assert.deepEqual(current.calls, confirmed ? { proceed: 1, reset: 0 } : { proceed: 0, reset: 1 });
  }
});

test('repeated callbacks for one blocker share a single confirmation flight', async () => {
  const choice = deferred<boolean>(); let confirmations = 0;
  const current = navigation();
  const controller = createBlockedNavigationController(() => { confirmations++; return choice.promise; });
  controller.handle(current.blocked, true);
  controller.handle(current.blocked, true);
  controller.handle(current.blocked, true);
  assert.equal(confirmations, 1); assert.deepEqual(current.calls, { proceed: 0, reset: 0 });
  choice.resolve(true); await checkpoint();
  assert.equal(confirmations, 1); assert.deepEqual(current.calls, { proceed: 1, reset: 0 });
});

test('dirty becoming false resets immediately and ignores the late confirmation', async () => {
  const choice = deferred<boolean>();
  const current = navigation();
  const controller = createBlockedNavigationController(() => choice.promise);
  controller.handle(current.blocked, true);
  controller.handle(current.blocked, false);
  assert.deepEqual(current.calls, { proceed: 0, reset: 1 });
  choice.resolve(true); await checkpoint();
  assert.deepEqual(current.calls, { proceed: 0, reset: 1 });
});

test('dirty false detaches an unresolved blocker so a later blocker confirms immediately', async () => {
  const never = deferred<boolean>(); const replacementChoice = deferred<boolean>();
  const choices = [never, replacementChoice]; let confirmations = 0;
  const first = navigation(); const replacement = navigation();
  const controller = createBlockedNavigationController(() => choices[confirmations++]!.promise);
  controller.handle(first.blocked, true);
  controller.handle(first.blocked, false);
  assert.deepEqual(first.calls, { proceed: 0, reset: 1 });
  controller.handle(replacement.blocked, true);
  assert.equal(confirmations, 2, 'the replacement must not wait for the detached confirmation Promise');
  replacementChoice.resolve(true); await checkpoint();
  assert.deepEqual(replacement.calls, { proceed: 1, reset: 0 });
});

test('a replacement resets the old blocker and waits for its stale flight before confirming the new one', async () => {
  const firstChoice = deferred<boolean>(); const secondChoice = deferred<boolean>();
  const choices = [firstChoice, secondChoice]; let confirmations = 0;
  const first = navigation(); const replacement = navigation();
  const controller = createBlockedNavigationController(() => choices[confirmations++]!.promise);
  controller.handle(first.blocked, true);
  controller.handle(replacement.blocked, true);
  assert.equal(confirmations, 1, 'only one confirmation promise may be in flight');
  assert.deepEqual(first.calls, { proceed: 0, reset: 1 });
  assert.deepEqual(replacement.calls, { proceed: 0, reset: 0 });
  firstChoice.resolve(true); await checkpoint();
  assert.equal(confirmations, 2, 'the replacement begins after the stale flight settles');
  assert.deepEqual(first.calls, { proceed: 0, reset: 1 }, 'stale completion cannot proceed the old blocker');
  secondChoice.resolve(true); await checkpoint();
  assert.deepEqual(replacement.calls, { proceed: 1, reset: 0 });
});

test('a newer queued blocker replaces and resets the prior queued blocker', async () => {
  const firstChoice = deferred<boolean>(); const latestChoice = deferred<boolean>();
  const choices = [firstChoice, latestChoice]; let confirmations = 0;
  const first = navigation(); const middle = navigation(); const latest = navigation();
  const controller = createBlockedNavigationController(() => choices[confirmations++]!.promise);
  controller.handle(first.blocked, true);
  controller.handle(middle.blocked, true);
  controller.handle(latest.blocked, true);
  assert.deepEqual(first.calls, { proceed: 0, reset: 1 });
  assert.deepEqual(middle.calls, { proceed: 0, reset: 1 });
  assert.deepEqual(latest.calls, { proceed: 0, reset: 0 });
  firstChoice.resolve(false); await checkpoint();
  assert.equal(confirmations, 2);
  latestChoice.resolve(false); await checkpoint();
  assert.deepEqual(latest.calls, { proceed: 0, reset: 1 });
});

test('a clean blocker resets without opening confirmation', () => {
  const current = navigation(); let confirmations = 0;
  const controller = createBlockedNavigationController(async () => { confirmations++; return true; });
  controller.handle(current.blocked, false);
  assert.equal(confirmations, 0); assert.deepEqual(current.calls, { proceed: 0, reset: 1 });
});

test('dispose resets the active blocker and ignores later promise completion', async () => {
  const choice = deferred<boolean>();
  const current = navigation();
  const controller = createBlockedNavigationController(() => choice.promise);
  controller.handle(current.blocked, true);
  controller.dispose(); controller.dispose();
  assert.deepEqual(current.calls, { proceed: 0, reset: 1 });
  choice.resolve(true); await checkpoint();
  assert.deepEqual(current.calls, { proceed: 0, reset: 1 });
  const afterDispose = navigation();
  controller.handle(afterDispose.blocked, true);
  assert.deepEqual(afterDispose.calls, { proceed: 0, reset: 0 });
});

test('a rejected confirmation fails closed by resetting the blocker', async () => {
  const current = navigation();
  const controller = createBlockedNavigationController(async () => { throw new Error('dialog unavailable'); });
  controller.handle(current.blocked, true);
  await checkpoint();
  assert.deepEqual(current.calls, { proceed: 0, reset: 1 });
});

for (const oldDecision of [true, false]) {
  for (const replacementPath of ['/first-target', '/second-target']) {
    test(`the real React Router host ignores an old ${oldDecision ? 'proceed' : 'reset'} before the ${replacementPath === '/first-target' ? 'same' : 'different'}-target transition commits`, async () => {
      const firstChoice = deferred<boolean>(); const replacementChoice = deferred<boolean>();
      const choices = [firstChoice, replacementChoice]; let confirmations = 0;
      const host = await mountReactRouterGuard(() => choices[confirmations++]!.promise);
      try {
        await act(async () => { await host.router.navigate('/first-target'); });
        const first = currentHookBlocker(host.router);
        assert.equal(confirmations, 1, 'blocker A is committed and owns the first confirmation');

        await act(async () => {
          void host.router.navigate(replacementPath);
          firstChoice.resolve(oldDecision);
          await firstChoice.promise;
        });

        assert.equal(host.router.state.location.pathname, '/start', 'A cannot change the URL after navigation attempt B begins');
        const replacement = currentHookBlocker(host.router);
        assert.notEqual(replacement, first, 'the router replaces A with an independently owned blocker B');
        assert.equal(confirmations, 2, 'B opens its own confirmation without inheriting A\'s decision');

        await act(async () => {
          replacementChoice.resolve(true);
          await replacementChoice.promise;
        });
        assert.equal(host.router.state.location.pathname, replacementPath);
        assert.notEqual([...host.router.state.blockers.values()][0]?.state, 'blocked');
      } finally { await host.dispose(); }
    });
  }
}

for (const confirmed of [true, false]) {
  test(`the real React Router host gives a current blocker one normal ${confirmed ? 'proceed' : 'reset'} terminal action`, async () => {
    const choice = deferred<boolean>(); let confirmations = 0;
    const host = await mountReactRouterGuard(() => { confirmations++; return choice.promise; });
    try {
      await act(async () => { await host.router.navigate('/ordinary-target'); });
      currentHookBlocker(host.router);
      assert.equal(confirmations, 1);
      await act(async () => { choice.resolve(confirmed); await choice.promise; });
      assert.equal(host.router.state.location.pathname, confirmed ? '/ordinary-target' : '/start');
      assert.notEqual([...host.router.state.blockers.values()][0]?.state, 'blocked');
      assert.equal(confirmations, 1);
    } finally { await host.dispose(); }
  });
}

test('the real React Router host resets immediately when dirty becomes false', async () => {
  const staleChoice = deferred<boolean>(); let confirmations = 0;
  const host = await mountReactRouterGuard(() => { confirmations++; return staleChoice.promise; });
  try {
    await act(async () => { await host.router.navigate('/dirty-target'); });
    currentHookBlocker(host.router);
    assert.equal(confirmations, 1);
    await act(async () => { host.setDirty(false); });
    assert.equal(host.router.state.location.pathname, '/start');
    assert.notEqual([...host.router.state.blockers.values()][0]?.state, 'blocked');
    await act(async () => { staleChoice.resolve(true); await staleChoice.promise; });
    assert.equal(host.router.state.location.pathname, '/start', 'the detached confirmation cannot navigate later');
  } finally { await host.dispose(); }
});

test('the real StrictMode host revokes its blocker action before unmount cleanup', async () => {
  const staleChoice = deferred<boolean>();
  const host = await mountReactRouterGuard(() => staleChoice.promise);
  try {
    await act(async () => { await host.router.navigate('/unmount-target'); });
    currentHookBlocker(host.router);
    await host.unmountReact();
    staleChoice.resolve(true); await staleChoice.promise; await checkpoint();
    assert.equal(host.router.state.location.pathname, '/start',
      'unmount cleanup and the late Promise cannot navigate');
  } finally { await host.dispose(); }
});

for (const oldDecision of [true, false]) {
  for (const replacementPath of ['/first-target', '/second-target']) {
    test(`a pre-commit ${oldDecision ? 'proceed' : 'reset'} cannot mutate the ${replacementPath === '/first-target' ? 'same' : 'different'}-target replacement`, async () => {
      const router = createMemoryRouter([{ path: '*', element: null }], { initialEntries: ['/start'] });
      router.getBlocker('risk-unsaved', () => true);
      const firstChoice = deferred<boolean>(); const replacementChoice = deferred<boolean>();
      const choices = [firstChoice, replacementChoice]; let confirmations = 0; let commitRequests = 0;
      const controller = createBlockedNavigationController(() => choices[confirmations++]!.promise);
      try {
        await router.navigate('/first-target');
        const first = currentRouterBlocker(router);
        const firstAdapter = routerAdapter(first, () => { commitRequests++; return true; });
        controller.handle(firstAdapter, true);

        await router.navigate(replacementPath);
        const replacement = currentRouterBlocker(router);
        assert.notEqual(replacement, first);
        firstChoice.resolve(oldDecision); await checkpoint();

        assert.equal(router.state.location.pathname, '/start', 'a decision cannot touch the router before a React commit');
        assert.equal(currentRouterBlocker(router), replacement, 'the uncommitted replacement remains the router authority');
        assert.equal(commitRequests, 1);
        firstAdapter.commit(replacement);
        assert.equal(currentRouterBlocker(router), replacement, 'the next commit discards the stale decision by blocker identity');

        const replacementAdapter = routerAdapter(replacement, () => { commitRequests++; return true; });
        controller.handle(replacementAdapter, true);
        replacementChoice.resolve(true); await checkpoint();
        assert.equal(router.state.location.pathname, '/start');
        assert.equal(currentRouterBlocker(router), replacement);
        assert.equal(commitRequests, 2);
        replacementAdapter.commit(replacement); await checkpoint();
        assert.equal(router.state.location.pathname, replacementPath);
        assert.notEqual(router.state.blockers.get('risk-unsaved')?.state, 'blocked');
      } finally { controller.dispose(); router.dispose(); }
    });
  }
}

for (const replacementPath of ['/first-target', '/second-target']) {
  test(`the production router adapter isolates a replacement for ${replacementPath === '/first-target' ? 'the same' : 'a different'} location`, async () => {
    const router = createMemoryRouter([{ path: '*', element: null }], { initialEntries: ['/start'] });
    router.getBlocker('risk-unsaved', () => true);
    const firstChoice = deferred<boolean>(); const replacementChoice = deferred<boolean>();
    const choices = [firstChoice, replacementChoice]; let confirmations = 0;
    let commitRequests = 0;
    const controller = createBlockedNavigationController(() => choices[confirmations++]!.promise);
    try {
      await router.navigate('/first-target');
      const first = currentRouterBlocker(router);
      const firstAdapter = routerAdapter(first, () => { commitRequests++; });
      controller.handle(firstAdapter, true);

      await router.navigate(replacementPath);
      const replacement = currentRouterBlocker(router);
      assert.notEqual(replacement, first, 'React Router replaces the blocker object while retaining its internal blocker key');
      firstAdapter.commit(replacement); firstAdapter.revoke();
      const replacementAdapter = routerAdapter(replacement, () => { commitRequests++; });
      controller.handle(replacementAdapter, true);
      assert.equal(currentRouterBlocker(router), replacement,
        'resetting the superseded controller entry must not unblock the current router navigation');
      assert.equal(confirmations, 1, 'only the stale confirmation is in flight');

      firstChoice.resolve(true); await checkpoint();
      assert.equal(confirmations, 2, 'the replacement receives an independent confirmation after the stale flight settles');
      assert.equal(currentRouterBlocker(router), replacement, 'the late stale confirmation cannot proceed the current navigation');
      replacementChoice.resolve(true); await checkpoint(); await checkpoint();
      assert.equal(commitRequests, 1);
      assert.equal(currentRouterBlocker(router), replacement, 'the router action still waits for the post-decision commit');
      replacementAdapter.commit(replacement); await checkpoint();
      assert.equal(router.state.location.pathname, replacementPath);
      assert.equal(router.state.blockers.get('risk-unsaved')?.state, 'unblocked');
    } finally { controller.dispose(); router.dispose(); }
  });
}

test('effect cleanup revokes its adapter before dispose and a StrictMode-style replacement can proceed', async () => {
  const router = createMemoryRouter([{ path: '*', element: null }], { initialEntries: ['/start'] });
  router.getBlocker('risk-unsaved', () => true);
  const staleChoice = deferred<boolean>(); let commitRequests = 0;
  const staleController = createBlockedNavigationController(() => staleChoice.promise);
  try {
    await router.navigate('/strict-target');
    const blocked = currentRouterBlocker(router);
    const staleAdapter = routerAdapter(blocked, () => { commitRequests++; });
    staleController.handle(staleAdapter, true);
    staleAdapter.revoke();
    staleController.dispose();
    assert.equal(currentRouterBlocker(router), blocked, 'effect cleanup cannot reset a router blocker after revoking its adapter');
    staleChoice.resolve(true); await checkpoint();
    assert.equal(currentRouterBlocker(router), blocked, 'late completion from the disposed effect remains inert');

    const replacementChoice = deferred<boolean>();
    const replacementController = createBlockedNavigationController(() => replacementChoice.promise);
    const replacementAdapter = routerAdapter(blocked, () => { commitRequests++; });
    replacementController.handle(replacementAdapter, true);
    replacementChoice.resolve(true); await checkpoint(); await checkpoint();
    assert.equal(commitRequests, 1);
    assert.equal(currentRouterBlocker(router), blocked);
    replacementAdapter.commit(blocked); await checkpoint();
    assert.equal(router.state.location.pathname, '/strict-target');
    assert.equal(router.state.blockers.get('risk-unsaved')?.state, 'unblocked');
    replacementController.dispose();
  } finally { staleController.dispose(); router.dispose(); }
});

test('the application root owns one data router inside the existing Ant Design providers', () => {
  const source = readFileSync(new URL('../main.tsx', import.meta.url), 'utf8');
  assert.equal(source.match(/createBrowserRouter\(/g)?.length, 1);
  assert.equal(source.match(/<RouterProvider\b/g)?.length, 1);
  assert.doesNotMatch(source, /\bBrowserRouter\b/);
  assert.match(source, /const router = createBrowserRouter\(\[\{ path: '\*', element: <App \/> \}\]\)/);
  assert.match(source, /<ConfigProvider[\s\S]*?<AntApp[\s\S]*?<RouterProvider router=\{router\} \/>[\s\S]*?<\/AntApp>[\s\S]*?<\/ConfigProvider>/);
});

test('the React hook composes the tested controller with useBlocker and exact lifecycle cleanup', () => {
  const source = readFileSync(new URL('useUnsavedRuleGuard.ts', import.meta.url), 'utf8');
  assert.match(source, /\+\+navigationAttemptsRef\.current\.latest;[\s\S]*?byLocation\.set\(nextLocation, attempt\)[\s\S]*?useBlocker\(shouldBlockNavigation\)/);
  assert.match(source, /createBlockedNavigationController\(/);
  assert.match(source, /createRouterBlockedNavigationAdapter\(/);
  assert.match(source, /byLocation\.get\(source\.location\)[\s\S]*?navigationAttemptsRef\.current\.latest === attempt/);
  assert.match(source, /blocker\.state === 'blocked'[\s\S]*?controller\.handle\(adapted, dirty\)/);
  assert.match(source, /installBeforeUnload\(window,/);
  assert.match(source, /adaptedRef\.current\?\.adapted\.revoke\(\)[\s\S]*?controller\.dispose\(\)/);
  assert.match(source, /useLayoutEffect\([\s\S]*?adaptedRef\.current\?\.adapted\.commit\(blocker\)/);
  assert.match(source, /controller\.dispose\(\)/);
});

test('the Risk page guards editor differences and unresolved commands with an asynchronous Ant modal', () => {
  const source = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.match(source, /App\.useApp\(\)/);
  assert.match(source, /modal\.confirm\(\{/);
  assert.match(source, /useUnsavedRuleGuard\(dirty, confirmLeave\)/);
  assert.match(source, /const editorDirty = state\.editor !== null[\s\S]*?JSON\.stringify\(state\.editor\) !== editorBaseline/);
  assert.match(source, /const unsavedDirty = editorDirty \|\| state\.pendingCommand !== null/);
  assert.match(source, /<RuleConfigurationUnsavedGuard dirty=\{unsavedDirty\} \/>/);
  assert.match(source, /setEditorBaseline\(JSON\.stringify\(editor\)\)/);
});

test('SAVE navigates after clearing dirty state and a refresh opens the canonical detail view', async () => {
  const response = deferred<unknown>(); const page = saveNavigationHarness(response);
  try {
    await page.flush();
    const running = page.controls.runCommand({ action: 'SAVE' }, '{}');
    await checkpoint(); page.render(); await page.flush();
    assert.notEqual(page.controls.current.current.pendingCommand, null);
    response.resolve(createRuleSetResponse);
    await running; await page.flush();
    assert.notEqual(page.router.state.blockers.get('save-dirty')?.state, 'blocked');
    const navigation = navigationModel.parseRiskNavigation(page.router.state.location.search);
    assert.equal(navigation.view, 'detail');
    assert.equal(navigation.selectedRuleSetCode, ruleSet.ruleSetCode);
    assert.equal(page.navigationAttempts, 1);
    const refreshed = saveNavigationHarness(deferred<unknown>(), page.router.state.location.search);
    try {
      await refreshed.flush();
      assert.equal(refreshed.controls.current.current.selectedRuleSetVersionId, ruleSet.ruleSetVersionId);
      assert.equal(refreshed.navigationAttempts, 0);
    } finally { refreshed.dispose(); }
  } finally { page.dispose(); }
});

for (const failure of [new ApiError(503, 'UNAVAILABLE', 'save-unknown'),
  new ApiError(422, 'VALIDATION_ERROR', 'save-invalid')] as const) {
  test(`SAVE ${failure.status} retains the edit view without navigating`, async () => {
    const response = deferred<unknown>(); const page = saveNavigationHarness(response);
    try {
      await page.flush();
      const running = page.controls.runCommand({ action: 'SAVE' }, '{}');
      await checkpoint(); page.render(); await page.flush();
      response.reject(failure);
      await running; await page.flush();
      assert.equal(page.navigationAttempts, 0);
      assert.equal(navigationModel.parseRiskNavigation(page.router.state.location.search).view, 'edit');
      assert.equal(page.controls.current.current.pendingCommand !== null, failure.status === 503);
    } finally { page.dispose(); }
  });
}

test('SAVE success queues its URL transition for an effect after dirty state is cleared', () => {
  const source = readFileSync(new URL('../pages/RiskRuleConfiguration.tsx', import.meta.url), 'utf8');
  assert.match(source, /result\.action === 'SAVE'[\s\S]*?setCompletedNavigation/);
  assert.match(source, /completedNavigation && !unsavedDirty/);
});
