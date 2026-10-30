/**
 * Every namespace of the Operations Console (technique 3.1). The root I18nGate loads all of them before the first
 * render, so pages never wait for a bundle. A new namespace needs: an entry here, `en/<ns>.json` registered in
 * `builtin.ts`, `i18n/zh-CN/<ns>.json`, and the namespace created in Platform before its keys are imported.
 */
export const OPS_NAMESPACES = [
  'common', 'auth', 'nav', 'enum', 'access', 'gateway', 'channel', 'commercial', 'finance', 'recon', 'risk',
  'workflow', 'data-query', 'file', 'notification', 'webhook', 'vault', 'card', 'client-review', 'currency', 'i18n-admin',
  'qualification', 'master-data', 'onboarding-schema', 'reference-data', 'tax',
] as const;
export type OpsNamespace = typeof OPS_NAMESPACES[number];

/**
 * Runtime-only namespaces: published by Platform, nothing built in, so they are not in OPS_NAMESPACES (no en / zh-CN files).
 * The root I18nGate still preloads them with the rest: loading one lazily on first `useT` bumps the runtime revision mid-page,
 * which re-renders the console shell and remounts the current page (its local state, such as the selected tab, is lost).
 */
export const RUNTIME_DATA_NAMESPACES = ['refdata'] as const;
