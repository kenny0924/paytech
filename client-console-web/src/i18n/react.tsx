import { createContext, useContext, useEffect, useMemo, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';
import { i18n as defaultI18n } from './index.ts';
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

/** `find(key)` for one namespace: the text, or null when no bundle has the key (caller supplies the fallback). */
export function useOptionalT(namespace: string): (key: string, params?: Params) => string | null {
  const instance = useContext(Context), revision = useRevision(instance);
  useEffect(() => { instance.use(namespace); }, [instance, namespace]);
  return useMemo(() => (key: string, params?: Params) => instance.find(namespace, key, params),
    [instance, namespace, revision]);
}

/** Current locale, enabled locales and `setLocale`. */
export function useLocale() {
  const instance = useContext(Context);
  useRevision(instance);
  return { locale: instance.locale(), locales: instance.locales(), setLocale: instance.setLocale };
}
