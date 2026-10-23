import { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';
import { bindTranslate, ensureStarted, i18n as defaultI18n } from './index.ts';
import type { Translate } from './index.ts';
import type { I18n } from './runtime.ts';

const Context = createContext<I18n>(defaultI18n);

/** Provides the i18n instance; the caller starts it once (see main.tsx). */
export function I18nProvider({ value = defaultI18n, children }: { value?: I18n; children: ReactNode }) {
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

function useRevision(instance: I18n): number {
  return useSyncExternalStore(instance.subscribe, instance.revision, instance.revision);
}

/**
 * `t(key, params)` for one namespace (plus `t.has(key)`); re-renders when the locale or a bundle changes.
 * Pass the result to `tOr` / `enumLabel` from `i18n/index.ts` when a key may be missing.
 */
export function useT(namespace: string): Translate {
  const instance = useContext(Context), revision = useRevision(instance);
  useEffect(() => { instance.use(namespace); }, [instance, namespace]);
  return useMemo(() => bindTranslate(instance, namespace), [instance, namespace, revision]);
}

/** Current locale and the published locales the switcher may offer. */
export function useLocale() {
  const instance = useContext(Context);
  useRevision(instance);
  return { locale: instance.locale(), locales: instance.locales() };
}

/** Namespaces whose bundles a gate has already waited for; later gates over them render at once. */
const settled = new Set<string>();

/**
 * Holds rendering until the runtime has started and the given namespaces' bundles settled (loaded or failed),
 * so a zh-CN page does not flash the English base first. Never waits longer than `timeoutMs`. The root gate in
 * main.tsx covers every Operations namespace, so nested gates over the same namespaces never wait again.
 */
export function I18nGate({ namespaces, fallback, timeoutMs = 2500, children }: {
  namespaces: readonly string[]; fallback: ReactNode; timeoutMs?: number; children: ReactNode;
}) {
  const key = namespaces.join(',');
  const [ready, setReady] = useState(() => namespaces.every(ns => settled.has(ns)));
  useEffect(() => {
    if (ready) return;
    let active = true;
    const done = () => { if (active) setReady(true); };
    const timer = setTimeout(done, timeoutMs);
    const list = key.split(',');
    for (const ns of list) defaultI18n.use(ns);
    ensureStarted().then(() => defaultI18n.loadNeeded()).catch(() => {}).finally(() => {
      clearTimeout(timer); list.forEach(ns => settled.add(ns)); done();
    });
    return () => { active = false; clearTimeout(timer); };
  }, [key, timeoutMs, ready]);
  return <>{ready ? children : fallback}</>;
}
