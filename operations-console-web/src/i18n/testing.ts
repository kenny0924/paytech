/**
 * Test helper: node tests run against the built-in English base only. Nothing here starts the runtime, so no
 * manifest or bundle is fetched and no storage is read; `i18n` (index.ts) stays on `en` with its built-in text.
 *
 *   import { testT } from '../i18n/testing.ts';
 *   const t = testT('currency');
 *   assert.equal(columnTitle, t('specs.column.code'));       // assert the English base through its key
 *   assert.equal(columnTitle, 'Currency Code');               // or the English literal itself
 */
import { builtin } from './builtin.ts';
import { appLocales, bindTranslate, i18n } from './index.ts';
import type { Translate } from './index.ts';
import { createI18n } from './runtime.ts';

const offline = createI18n({
  builtin, locales: appLocales, defaultLocale: 'en', manifestUrl: 'about:blank',
  fetch: (() => Promise.reject(new Error('i18n tests never fetch'))) as typeof fetch,
  warn: message => { throw new Error(message); },
});

/** Built-in English translator for one namespace; a missing key throws, so assertions cannot pass on a key echo. */
export function testT(namespace: string): Translate {
  return bindTranslate(offline, namespace);
}

/** The application instance code under test uses; it must still be on the built-in English base. */
export function assertBuiltinEnglish(): void {
  if (i18n.locale() !== 'en') throw new Error('i18n tests expect the built-in English base, got ' + i18n.locale());
}
