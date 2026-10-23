import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createI18n, matchLocale, resolveLocale } from './runtime.ts';
import type { Catalog, I18nOptions, Manifest } from './runtime.ts';

const builtin: Catalog = {
  team: {
    'team.title': 'Team',
    'team.only_builtin': 'Built-in only',
    'team.count': { text: '{count, plural, one {# member} other {# members}}', description: 'Member count' },
    'team.greeting': 'Hello {name}',
  },
};
const locales: I18nOptions['locales'] = [
  { code: 'en', nativeName: 'English', direction: 'LTR' },
  { code: 'zh-CN', nativeName: '简体中文', direction: 'LTR' },
];
const manifest = (locale: string, releaseNo = 7): Manifest => ({
  application: 'client-console', releaseNo, publishedAt: '2026-10-23T08:00:00Z', defaultLocale: 'en',
  locales: [...locales, { code: 'ar', nativeName: 'العربية', direction: 'RTL' }],
  bundles: {
    en: { team: { hash: 'e1', url: 'https://cdn.test/r' + releaseNo + '/en/team.e1.json' } },
    ...(locale === 'en' ? {} : { [locale]: { team: { hash: 'z1', url: 'https://cdn.test/r' + releaseNo + '/' + locale + '/team.z1.json' } } }),
  },
} as Manifest & { publishedAt: string });

type Route = (url: string) => Response | Promise<Response>;
function fakeFetch(route: Route) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const transport = (async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init });
    return route(String(input));
  }) as typeof fetch;
  return { calls, transport };
}
function memoryStore(initial: Record<string, string> = {}) {
  const values = new Map(Object.entries(initial));
  return { values, getItem: (k: string) => values.get(k) ?? null, setItem: (k: string, v: string) => { values.set(k, v); }, removeItem: (k: string) => { values.delete(k); } };
}
const json = (value: unknown) => Response.json(value);
/** Release 7: runtime `en` overrides one key, `zh-CN` translates two keys. */
function healthyRoute(url: string): Response {
  if (url.startsWith('/manifest')) return json(manifest(new URL(url, 'http://x').searchParams.get('locale')!));
  if (url.endsWith('/en/team.e1.json')) return json({ 'team.title': 'Team (runtime en)', 'team.runtime_en_only': 'Runtime en only' });
  if (url.endsWith('/zh-CN/team.z1.json')) return json({ 'team.title': '团队', 'team.count': '{count} 名成员' });
  return new Response('missing', { status: 404 });
}
function setup(overrides: Partial<I18nOptions> = {}, route: Route = healthyRoute) {
  const warnings: string[] = [], http = fakeFetch(route), document = { documentElement: { lang: '', dir: '' } };
  const storage = memoryStore(), session = memoryStore();
  const i18n = createI18n({
    builtin, locales, defaultLocale: 'en', manifestUrl: '/manifest', fetch: http.transport,
    storage, session, document, warn: (message) => warnings.push(message), ...overrides,
  });
  return { i18n, warnings, http, document, storage, session };
}

test('lookup order is runtime target, runtime en, built-in en, then the key with one warning', async () => {
  const { i18n, warnings } = setup();
  i18n.use('team');
  await i18n.start({ search: '?lang=zh-CN' });
  assert.equal(i18n.t('team', 'title'), '团队');
  assert.equal(i18n.t('team', 'runtime_en_only'), 'Runtime en only');
  assert.equal(i18n.t('team', 'only_builtin'), 'Built-in only');
  assert.equal(i18n.t('team', 'missing'), 'team.missing');
  assert.equal(i18n.t('team', 'missing'), 'team.missing');
  assert.deepEqual(warnings, ['i18n: missing key "team.missing"']);
});

test('ICU plural rules follow the locale of the text found, and parameters are substituted', async () => {
  const { i18n } = setup();
  assert.equal(i18n.t('team', 'count', { count: 1 }), '1 member');
  assert.equal(i18n.t('team', 'count', { count: 3 }), '3 members');
  assert.equal(i18n.t('team', 'greeting', { name: 'Dana' }), 'Hello Dana');
  i18n.use('team');
  await i18n.start({ search: '?lang=zh-CN' });
  assert.equal(i18n.t('team', 'count', { count: 3 }), '3 名成员');
});

test('manifest failure or an unreachable service falls back to built-in English without throwing', async () => {
  for (const route of [() => new Response('down', { status: 503 }), () => { throw new TypeError('network'); }, () => json({ unexpected: true })] as Route[]) {
    const { i18n, http } = setup({}, route);
    i18n.use('team');
    await i18n.start({ search: '?lang=zh-CN' });
    assert.equal(i18n.locale(), 'zh-CN');
    assert.equal(i18n.manifestFailed(), true);
    assert.equal(i18n.t('team', 'title'), 'Team');
    assert.equal(http.calls.length, 1);
  }
});

test('a failed language bundle falls back to runtime en, and a failed en bundle to built-in text', async () => {
  const noTarget = setup({}, (url) => (url.includes('/zh-CN/') ? new Response('', { status: 404 }) : healthyRoute(url)));
  noTarget.i18n.use('team');
  await noTarget.i18n.start({ search: '?lang=zh-CN' });
  assert.equal(noTarget.i18n.t('team', 'title'), 'Team (runtime en)');
  const nothing = setup({}, (url) => (url.startsWith('/manifest') ? healthyRoute(url) : Promise.reject(new TypeError('cors'))));
  nothing.i18n.use('team');
  await nothing.i18n.start({ search: '?lang=zh-CN' });
  assert.equal(nothing.i18n.t('team', 'title'), 'Team');
});

test('bundles load only for namespaces in use, directly from the manifest URL and without credentials', async () => {
  const { i18n, http } = setup();
  await i18n.start({ search: '?lang=zh-CN' });
  assert.deepEqual(http.calls.map((call) => call.url), ['/manifest?locale=zh-CN']);
  assert.equal(http.calls[0].init?.credentials, 'omit');
  i18n.t('team', 'title');
  await i18n.loadNeeded();
  assert.deepEqual(http.calls.slice(1).map((call) => call.url).sort(), ['https://cdn.test/r7/en/team.e1.json', 'https://cdn.test/r7/zh-CN/team.z1.json']);
  assert.ok(http.calls.slice(1).every((call) => call.init?.credentials === 'omit'));
  assert.equal(i18n.t('team', 'title'), '团队');
});

test('locale source order: ?lang (session only) > stored choice > browser language > default', async () => {
  const query = setup();
  query.storage.setItem('i18n.locale', 'en');
  await query.i18n.start({ search: '?lang=zh-CN', navigatorLanguages: ['en-US'] });
  assert.equal(query.i18n.locale(), 'zh-CN');
  assert.equal(query.storage.getItem('i18n.locale'), 'en');
  assert.equal(query.session.getItem('i18n.session-locale'), 'zh-CN');

  const stored = setup();
  stored.storage.setItem('i18n.locale', 'zh-CN');
  await stored.i18n.start({ search: '', navigatorLanguages: ['en-US'] });
  assert.equal(stored.i18n.locale(), 'zh-CN');

  const browser = setup();
  await browser.i18n.start({ search: '?lang=fr', navigatorLanguages: ['fr-FR', 'zh-Hans-CN', 'en'] });
  assert.equal(browser.i18n.locale(), 'zh-CN');

  const fallback = setup();
  await fallback.i18n.start({ navigatorLanguages: ['fr-FR'] });
  assert.equal(fallback.i18n.locale(), 'en');

  const blocked = setup({ storage: { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); }, removeItem: () => {} } });
  await blocked.i18n.start({ navigatorLanguages: ['zh-CN'] });
  assert.equal(blocked.i18n.locale(), 'zh-CN');
  await assert.doesNotReject(blocked.i18n.setLocale('en'));
});

test('setLocale remembers the choice, replaces the ?lang override and updates <html lang dir>', async () => {
  const { i18n, storage, session, document } = setup();
  await i18n.start({ search: '?lang=zh-CN' });
  assert.deepEqual(document.documentElement, { lang: 'zh-CN', dir: 'ltr' });
  await i18n.setLocale('ar');
  assert.equal(storage.getItem('i18n.locale'), 'ar');
  assert.equal(session.getItem('i18n.session-locale'), null);
  assert.deepEqual(document.documentElement, { lang: 'ar', dir: 'rtl' });
});

test('?release=N is passed through as releaseNo; an invalid value is ignored', async () => {
  const preview = setup();
  await preview.i18n.start({ search: '?lang=zh-CN&release=7' });
  assert.equal(preview.http.calls[0].url, '/manifest?locale=zh-CN&releaseNo=7');
  assert.equal(preview.session.getItem('i18n.release'), '7');
  const invalid = setup();
  await invalid.i18n.start({ search: '?release=7;drop' });
  assert.equal(invalid.http.calls[0].url, '/manifest?locale=en');
});

test('locale matching accepts exact tags first, then the same primary language', () => {
  assert.equal(matchLocale('ZH-cn', ['en', 'zh-CN']), 'zh-CN');
  assert.equal(matchLocale('zh-Hans-CN', ['en', 'zh-CN']), 'zh-CN');
  assert.equal(matchLocale('en-GB', ['en', 'zh-CN']), 'en');
  assert.equal(matchLocale('fr', ['en', 'zh-CN']), null);
  assert.equal(resolveLocale({ query: 'zh', enabled: ['en', 'zh-CN'], fallback: 'en' }), 'en');
});

/** Release 7 publishes en and zh-CN bundles; `ar` is enabled but has no translations, so it ships no bundle. */
function publishedRoute(releaseNo: number | null): Route {
  return (url) => {
    if (!url.startsWith('/manifest')) return healthyRoute(url);
    const locale = new URL(url, 'http://x').searchParams.get('locale')!;
    const value = manifest(locale);
    if (releaseNo === null) return json({ ...value, releaseNo: null, bundles: {} });
    if (locale === 'ar') delete value.bundles.ar;
    return json(value);
  };
}
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

test('published-only: without a manifest or a release, zh-CN preferences are ignored and nothing is offered', async () => {
  for (const route of [() => new Response('down', { status: 503 }), publishedRoute(null)] as Route[]) {
    const { i18n, storage, document } = setup({ publishedOnly: true }, route);
    storage.setItem('i18n.locale', 'zh-CN');
    i18n.use('team');
    await i18n.start({ search: '?lang=zh-CN', navigatorLanguages: ['zh-CN'] });
    await settle();
    assert.equal(i18n.locale(), 'en');
    assert.equal(document.documentElement.lang, 'en');
    assert.deepEqual(i18n.locales(), []);
    assert.equal(i18n.t('team', 'title'), 'Team');
    assert.equal(storage.getItem('i18n.locale'), 'zh-CN');
  }
});

test('published-only: offers only locales whose bundles are published and uses the requested one', async () => {
  const { i18n } = setup({ publishedOnly: true }, publishedRoute(7));
  i18n.use('team');
  await i18n.start({ search: '?lang=zh-CN' });
  await i18n.verifyOffered();
  assert.equal(i18n.locale(), 'zh-CN');
  assert.equal(i18n.t('team', 'title'), '团队');
  assert.deepEqual(i18n.locales().map((item) => item.code), ['en', 'zh-CN']);
  await i18n.setLocale('ar');
  assert.equal(i18n.locale(), 'en');
  assert.equal(i18n.t('team', 'title'), 'Team (runtime en)');
});

test('published-only: an English start still discovers zh-CN for the switcher', async () => {
  const { i18n, http } = setup({ publishedOnly: true }, publishedRoute(7));
  await i18n.start({ navigatorLanguages: ['en-US'] });
  await i18n.verifyOffered();
  assert.equal(i18n.locale(), 'en');
  assert.deepEqual(i18n.locales().map((item) => item.code), ['en', 'zh-CN']);
  assert.ok(http.calls.some((call) => call.url === '/manifest?locale=zh-CN'));
});
