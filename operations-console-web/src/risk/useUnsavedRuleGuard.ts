import { useCallback, useEffect, useLayoutEffect, useReducer, useRef } from 'react';
import { useBlocker } from 'react-router-dom';
import type { Blocker, BlockerFunction } from 'react-router-dom';

export interface BeforeUnloadTarget {
  addEventListener(type: 'beforeunload', listener: (event: BeforeUnloadEvent) => void): void;
  removeEventListener(type: 'beforeunload', listener: (event: BeforeUnloadEvent) => void): void;
}

export interface BlockedNavigation {
  proceed(): void;
  reset(): void;
}

export interface BlockedNavigationController {
  handle(blocked: BlockedNavigation, dirty: boolean): void;
  dispose(): void;
}

type RouterBlockedNavigation = Extract<Blocker, { state: 'blocked' }>;

export interface RouterBlockedNavigationAdapter extends BlockedNavigation {
  commit(current: Blocker): void;
  revoke(): void;
}

/** Router actions may execute only for the navigation attempt that created this blocked object. */
export function createRouterBlockedNavigationAdapter(blocked: RouterBlockedNavigation,
  requestCommit: () => void, isCurrentAttempt: () => boolean = () => true): RouterBlockedNavigationAdapter {
  let requested: 'PROCEED' | 'RESET' | null = null;
  let consumed = false; let revoked = false;
  function request(action: 'PROCEED' | 'RESET') {
    if (revoked || consumed || requested !== null) return;
    requested = action; requestCommit();
  }
  return {
    proceed: () => request('PROCEED'),
    reset: () => request('RESET'),
    commit(current) {
      if (revoked || consumed || requested === null) return;
      const action = requested; requested = null; consumed = true;
      if (current !== blocked || !isCurrentAttempt()) return;
      if (action === 'PROCEED') blocked.proceed();
      else blocked.reset();
    },
    revoke() { revoked = true; requested = null; },
  };
}

export function installBeforeUnload(target: BeforeUnloadTarget, shouldBlock: () => boolean): () => void {
  const listener = (event: BeforeUnloadEvent) => {
    if (!shouldBlock()) return;
    event.preventDefault();
    event.returnValue = '';
  };
  target.addEventListener('beforeunload', listener);
  let installed = true;
  return () => {
    if (!installed) return;
    installed = false;
    target.removeEventListener('beforeunload', listener);
  };
}

export function nextBlockedNavigation(confirmed: boolean): 'PROCEED' | 'RESET' {
  return confirmed ? 'PROCEED' : 'RESET';
}

interface NavigationEntry {
  blocked: BlockedNavigation;
  settled: boolean;
}

export function createBlockedNavigationController(confirmLeave: () => Promise<boolean>): BlockedNavigationController {
  let disposed = false;
  let active: NavigationEntry | null = null;
  let queued: NavigationEntry | null = null;
  const handled = new WeakSet<BlockedNavigation>();

  function settle(entry: NavigationEntry, result: 'PROCEED' | 'RESET') {
    if (entry.settled) return;
    entry.settled = true;
    if (result === 'PROCEED') entry.blocked.proceed();
    else entry.blocked.reset();
  }

  function finish(entry: NavigationEntry, confirmed: boolean) {
    if (active !== entry) return;
    if (!disposed && !entry.settled) settle(entry, nextBlockedNavigation(confirmed));
    active = null;
    if (!disposed && queued) {
      const next = queued; queued = null; begin(next);
    }
  }

  function begin(entry: NavigationEntry) {
    active = entry;
    let confirmation: Promise<boolean>;
    try { confirmation = confirmLeave(); }
    catch { finish(entry, false); return; }
    void confirmation.then(value => finish(entry, value), () => finish(entry, false));
  }

  return {
    handle(blocked, dirty) {
      if (disposed) return;
      if (!dirty) {
        const alreadyTracked = active?.blocked === blocked || queued?.blocked === blocked || handled.has(blocked);
        if (active && !active.settled) settle(active, 'RESET');
        if (queued && !queued.settled) settle(queued, 'RESET');
        active = null; queued = null;
        if (!alreadyTracked) {
          handled.add(blocked);
          settle({ blocked, settled: false }, 'RESET');
        }
        return;
      }
      if (handled.has(blocked)) return;
      handled.add(blocked);
      const entry: NavigationEntry = { blocked, settled: false };
      if (active) {
        if (!active.settled) settle(active, 'RESET');
        if (queued && !queued.settled) settle(queued, 'RESET');
        queued = entry;
        return;
      }
      begin(entry);
    },
    dispose() {
      if (disposed) return;
      disposed = true;
      if (active && !active.settled) settle(active, 'RESET');
      if (queued && !queued.settled) settle(queued, 'RESET');
      queued = null;
    },
  };
}

export function useUnsavedRuleGuard(dirty: boolean, confirmLeave: () => Promise<boolean>): void {
  const dirtyRef = useRef(dirty);
  const confirmLeaveRef = useRef(confirmLeave);
  const controllerRef = useRef<BlockedNavigationController | null>(null);
  const blockedRef = useRef<BlockedNavigation | null>(null);
  const navigationAttemptsRef = useRef({ latest: 0, byLocation: new WeakMap<object, number>() });
  const shouldBlockNavigation: BlockerFunction = useCallback(({ nextLocation }) => {
    const attempt = ++navigationAttemptsRef.current.latest;
    navigationAttemptsRef.current.byLocation.set(nextLocation, attempt);
    return dirty;
  }, [dirty]);
  const blocker = useBlocker(shouldBlockNavigation);
  const [adapterCommitVersion, requestAdapterCommit] = useReducer(value => value + 1, 0);
  const adaptedRef = useRef<{ source: RouterBlockedNavigation; adapted: RouterBlockedNavigationAdapter } | null>(null);

  useLayoutEffect(() => {
    dirtyRef.current = dirty;
    confirmLeaveRef.current = confirmLeave;
    adaptedRef.current?.adapted.commit(blocker);
  }, [dirty, confirmLeave, blocker, adapterCommitVersion]);

  useEffect(() => {
    const controller = createBlockedNavigationController(() => confirmLeaveRef.current());
    controllerRef.current = controller;
    return () => {
      if (controllerRef.current === controller) controllerRef.current = null;
      adaptedRef.current?.adapted.revoke();
      adaptedRef.current = null;
      blockedRef.current = null;
      controller.dispose();
    };
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    return installBeforeUnload(window, () => dirtyRef.current);
  }, []);

  useEffect(() => {
    const controller = controllerRef.current;
    if (!controller) return;
    if (blocker.state === 'blocked') {
      const source = blocker;
      if (adaptedRef.current?.source !== source) adaptedRef.current?.adapted.revoke();
      const attempt = navigationAttemptsRef.current.byLocation.get(source.location);
      const adapted = adaptedRef.current?.source === source ? adaptedRef.current.adapted
        : createRouterBlockedNavigationAdapter(source, requestAdapterCommit,
          () => attempt !== undefined && navigationAttemptsRef.current.latest === attempt);
      adaptedRef.current = { source, adapted };
      blockedRef.current = adapted;
      controller.handle(adapted, dirty);
    } else if (!dirty && blockedRef.current) {
      controller.handle(blockedRef.current, false);
      blockedRef.current = null;
    }
  }, [blocker, dirty]);
}
