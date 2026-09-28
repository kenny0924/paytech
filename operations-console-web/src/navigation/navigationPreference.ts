const navigationCollapsedStorageKey = 'paytech.operations.navigation.collapsed.v1';

export type NavigationPreferenceStorage = Pick<Storage, 'getItem' | 'setItem'>;

export function loadNavigationCollapsed(storage?: NavigationPreferenceStorage): boolean {
  if (!storage) return false;
  try { return storage.getItem(navigationCollapsedStorageKey) === 'true'; }
  catch { return false; }
}

export function saveNavigationCollapsed(
  storage: NavigationPreferenceStorage | undefined,
  collapsed: boolean,
): void {
  if (!storage) return;
  try { storage.setItem(navigationCollapsedStorageKey, String(collapsed)); }
  catch { /* 当前浏览器存储不可访问。 */ }
}

export function getBrowserLocalStorage(): NavigationPreferenceStorage | undefined {
  try { return typeof window === 'undefined' ? undefined : window.localStorage; }
  catch { return undefined; }
}
