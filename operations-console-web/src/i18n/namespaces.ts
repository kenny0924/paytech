/**
 * Every namespace of the Operations Console (technique 3.1). The root I18nGate loads all of them before the first
 * render, so pages never wait for a bundle. A new namespace needs: an entry here, `en/<ns>.json` registered in
 * `builtin.ts`, `i18n/zh-CN/<ns>.json`, and the namespace created in Platform before its keys are imported.
 */
export const OPS_NAMESPACES = [
  'common', 'auth', 'nav', 'enum', 'access', 'gateway', 'channel', 'commercial', 'finance', 'recon', 'risk',
  'workflow', 'data-query', 'file', 'notification', 'webhook', 'vault', 'card', 'client-review', 'currency', 'i18n-admin',
] as const;
export type OpsNamespace = typeof OPS_NAMESPACES[number];
