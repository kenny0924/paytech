import { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';
import { ensureStarted, i18n as defaultI18n } from './index.ts';
import type { Params } from './runtime.ts';
import type { I18n } from './runtime.ts';

const Context = createContext<I18n>(defaultI18n);

/** Provides the i18n instance; the caller starts it once (see main.tsx). */
export function I18nProvider({ value = defaultI18n, children }: { value?: I18n; children: ReactNode }) {
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

function useRevision(instance: I18n): number {
  return useSyncExternalStore(instance.subscribe, instance.revision, instance.revision);
}

/** `t(key, params)` for one namespace; re-renders when the locale or a bundle changes. */
export function useT(namespace: string): (key: string, params?: Params) => string {
  const instance = useContext(Context), revision = useRevision(instance);
  useEffect(() => { instance.use(namespace); }, [instance, namespace]);
  return useMemo(() => (key: string, params?: Params) => instance.t(namespace, key, params),
    [instance, namespace, revision]);
}

/** Current locale, enabled locales and `setLocale`. */
export function useLocale() {
  const instance = useContext(Context);
  useRevision(instance);
  return { locale: instance.locale(), locales: instance.locales(), setLocale: instance.setLocale };
}

/**
 * Holds rendering until the runtime has started and the given namespaces' bundles settled (loaded or failed),
 * so a zh-CN page does not flash the English base first. Never waits longer than `timeoutMs`.
 */
export function I18nGate({ namespaces, fallback, timeoutMs = 2500, children }: {
  namespaces: string[]; fallback: ReactNode; timeoutMs?: number; children: ReactNode;
}) {
  const [ready, setReady] = useState(false);
  const key = namespaces.join(',');
  useEffect(() => {
    let active = true;
    const done = () => { if (active) setReady(true); };
    const timer = setTimeout(done, timeoutMs);
    for (const ns of key.split(',')) defaultI18n.use(ns);
    ensureStarted().then(() => defaultI18n.loadNeeded()).catch(() => {}).finally(() => { clearTimeout(timer); done(); });
    return () => { active = false; clearTimeout(timer); };
  }, [key, timeoutMs]);
  return <>{ready ? children : fallback}</>;
}
