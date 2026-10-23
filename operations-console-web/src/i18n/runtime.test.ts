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

/** localStorage stand-in with key enumeration; `failOn` makes setItem throw for matching keys (quota). */
function cacheStore(failOn?: (key: string) => boolean) {
  const values = new Map<string, string>();
  return {
    values,
    getItem: (k: string) => values.get(k) ?? null,
    setItem: (k: string, v: string) => { if (failOn?.(k)) throw new DOMException('full', 'QuotaExceededError'); values.set(k, v); },
    removeItem: (k: string) => { values.delete(k); },
    key: (index: number) => [...values.keys()][index] ?? null,
    get length() { return values.size; },
  };
}
const cacheKeys = (store: ReturnType<typeof cacheStore>) => [...store.values.keys()].filter((key) => key.startsWith('i18n.cache.')).sort();
/** Release N of the published route: bundle URLs carry the release number, the zh-CN title says which release. */
function releaseRoute(releaseNo: number): Route {
  return (url) => {
    if (url.startsWith('/manifest')) {
      const locale = new URL(url, 'http://x').searchParams.get('locale')!;
      const value = manifest(locale, releaseNo);
      if (locale === 'ar') delete value.bundles.ar;
      return json(value);
    }
    if (url.endsWith('/en/team.e1.json')) return json({ 'team.title': 'Team r' + releaseNo });
    if (url.endsWith('/zh-CN/team.z1.json')) return json({ 'team.title': '团队 r' + releaseNo });
    return new Response('missing', { status: 404 });
  };
}
/** Fills `cache` the way a normal (networked) zh-CN start does. */
async function warmCache(cache: ReturnType<typeof cacheStore>, releaseNo = 7) {
  const { i18n } = setup({ publishedOnly: true, cache }, releaseRoute(releaseNo));
  i18n.use('team');
  await i18n.start({ search: '?lang=zh-CN' });
  await i18n.verifyOffered();
  assert.equal(i18n.t('team', 'title'), '团队 r' + releaseNo);
}

test('cache: a cached locale starts and translates without waiting for the network', async () => {
  const cache = cacheStore();
  await warmCache(cache);
  assert.deepEqual(cacheKeys(cache), ['i18n.cache.bundle.https://cdn.test/r7/en/team.e1.json', 'i18n.cache.bundle.https://cdn.test/r7/zh-CN/team.z1.json',
    'i18n.cache.manifest.ar', 'i18n.cache.manifest.zh-CN']);
  const pending: string[] = [];
  const { i18n, storage, document } = setup({ publishedOnly: true, cache }, (url) => { pending.push(url); return new Promise<Response>(() => {}); });
  storage.setItem('i18n.locale', 'zh-CN');
  i18n.use('team');
  await i18n.start({});
  assert.equal(i18n.locale(), 'zh-CN');
  assert.equal(document.documentElement.lang, 'zh-CN');
  assert.equal(i18n.t('team', 'title'), '团队 r7');
  assert.deepEqual(i18n.locales().map((item) => item.code), ['en', 'zh-CN']);
  assert.deepEqual(pending, ['/manifest?locale=zh-CN']);
});

test('cache: a newer release found in the background replaces the bundles, updates the cache and drops old bundles', async () => {
  const cache = cacheStore();
  await warmCache(cache, 7);
  const { i18n, storage } = setup({ publishedOnly: true, cache }, releaseRoute(8));
  storage.setItem('i18n.locale', 'zh-CN');
  i18n.use('team');
  let changes = 0;
  i18n.subscribe(() => { changes++; });
  await i18n.start({});
  assert.equal(i18n.t('team', 'title'), '团队 r7');
  await settle(); await settle();
  assert.equal(i18n.t('team', 'title'), '团队 r8');
  assert.ok(changes > 1);
  assert.equal(JSON.parse(cache.getItem('i18n.cache.manifest.zh-CN')!).releaseNo, 8);
  assert.ok(cache.getItem('i18n.cache.bundle.https://cdn.test/r8/zh-CN/team.z1.json'));
  assert.ok(cacheKeys(cache).every((key) => !key.includes('/r7/')), cacheKeys(cache).join(','));
});

test('cache: when the manifest is unreachable the cached language keeps working, offline included', async () => {
  const cache = cacheStore();
  await warmCache(cache);
  for (const route of [() => new Response('down', { status: 503 }), () => { throw new TypeError('offline'); }] as Route[]) {
    const { i18n, storage } = setup({ publishedOnly: true, cache }, route);
    storage.setItem('i18n.locale', 'zh-CN');
    i18n.use('team');
    await i18n.start({});
    await settle();
    assert.equal(i18n.locale(), 'zh-CN');
    assert.equal(i18n.t('team', 'title'), '团队 r7');
    assert.equal(i18n.manifestFailed(), true);
    assert.ok(cache.getItem('i18n.cache.manifest.zh-CN'));
  }
});

test('cache: without every needed bundle cached the runtime falls back to the network start', async () => {
  const cache = cacheStore();
  await warmCache(cache);
  cache.removeItem('i18n.cache.bundle.https://cdn.test/r7/zh-CN/team.z1.json');
  const { i18n, http, storage } = setup({ publishedOnly: true, cache }, releaseRoute(7));
  storage.setItem('i18n.locale', 'zh-CN');
  i18n.use('team');
  await i18n.start({});
  assert.equal(i18n.t('team', 'title'), '团队 r7');
  assert.ok(http.calls.some((call) => call.url.endsWith('/zh-CN/team.z1.json')));
  assert.ok(cache.getItem('i18n.cache.bundle.https://cdn.test/r7/zh-CN/team.z1.json'));
});

test('cache: a quota failure clears every cached entry and the runtime carries on from memory', async () => {
  const cache = cacheStore((key) => key.includes('/zh-CN/'));
  cache.setItem('i18n.cache.manifest.old', '{}');
  cache.setItem('unrelated', 'kept');
  const { i18n } = setup({ publishedOnly: true, cache }, releaseRoute(7));
  i18n.use('team');
  await i18n.start({ search: '?lang=zh-CN' });
  assert.equal(i18n.t('team', 'title'), '团队 r7');
  assert.deepEqual(cacheKeys(cache).filter((key) => key.includes('/zh-CN/')), []);
  assert.equal(cache.getItem('i18n.cache.manifest.old'), null);
  assert.equal(cache.getItem('unrelated'), 'kept');
});

test('cache: a ?release= preview neither reads nor writes the cache', async () => {
  const cache = cacheStore();
  await warmCache(cache, 7);
  const before = new Map(cache.values);
  const { i18n, http } = setup({ publishedOnly: true, cache }, releaseRoute(9));
  i18n.use('team');
  await i18n.start({ search: '?lang=zh-CN&release=9' });
  await i18n.prefetch('en');
  assert.equal(i18n.t('team', 'title'), '团队 r9');
  assert.equal(http.calls[0].url, '/manifest?locale=zh-CN&releaseNo=9');
  assert.deepEqual(cache.values, before);
});

test('prefetch fills the cache for another language; rememberLocale stores the choice and drops ?lang', async () => {
  const cache = cacheStore();
  const { i18n, storage, session } = setup({ publishedOnly: true, cache }, releaseRoute(7));
  i18n.use('team');
  await i18n.start({ search: '?lang=en' });
  assert.equal(cache.getItem('i18n.cache.bundle.https://cdn.test/r7/zh-CN/team.z1.json'), null);
  await i18n.prefetch('zh-CN');
  assert.ok(cache.getItem('i18n.cache.manifest.zh-CN'));
  assert.ok(cache.getItem('i18n.cache.bundle.https://cdn.test/r7/zh-CN/team.z1.json'));
  assert.equal(i18n.locale(), 'en');
  i18n.rememberLocale('zh-CN');
  assert.equal(storage.getItem('i18n.locale'), 'zh-CN');
  assert.equal(session.getItem('i18n.session-locale'), null);
  await assert.doesNotReject(setup({ publishedOnly: true, cache }, () => { throw new TypeError('offline'); }).i18n.prefetch('zh-CN'));
});

test('has() reports whether a key resolves, without a missing-key warning', async () => {
  const { i18n, warnings } = setup();
  assert.equal(i18n.has('team', 'title'), true);
  assert.equal(i18n.has('team', 'absent'), false);
  assert.deepEqual(warnings, []);
});
