import { i18n } from './index.ts';
import type { I18n } from './runtime.ts';

type ReloadTarget = Pick<Location, 'href' | 'replace'>;

/**
 * Switches the console language by reloading the current page (technique 3.3, S1): first caches the target
 * language's manifest and bundles (a failure does not stop the switch), then stores the choice, drops a `?lang=`
 * preview from the URL and reloads the same route, so antd, dayjs and module-level values restart in that language.
 */
export async function switchLanguage(code: string, instance: Pick<I18n, 'prefetch' | 'rememberLocale'> = i18n,
  location: ReloadTarget = window.location): Promise<void> {
  await instance.prefetch(code).catch(() => {});
  instance.rememberLocale(code);
  const url = new URL(location.href);
  url.searchParams.delete('lang');
  location.replace(url.toString());
}
