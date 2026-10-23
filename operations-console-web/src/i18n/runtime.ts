import { IntlMessageFormat } from 'intl-messageformat';

/** Base-language entry: plain text, or text plus a note for translators. */
export type MessageEntry = string | { text: string; description?: string };
/** namespace -> full key (`<namespace>.<area>.<name>`) -> entry. */
export type Catalog = Record<string, Record<string, MessageEntry>>;
export type LocaleInfo = { code: string; nativeName: string; direction: 'LTR' | 'RTL' };
export type Manifest = {
  application: string; releaseNo: number | null; locales: LocaleInfo[]; defaultLocale: string;
  bundles: Record<string, Record<string, { hash: string; url: string }>>;
};
export type Params = Record<string, string | number | Date | boolean | null | undefined>;
export type KeyValueStore = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
/** Local cache of published manifests and bundles; needs key enumeration to drop stale bundles. */
export type CacheStore = Pick<Storage, 'getItem' | 'setItem' | 'removeItem' | 'key' | 'length'>;

export type I18nOptions = {
  builtin: Catalog;
  /** Locales this build knows about, used until a manifest is available. */
  locales: LocaleInfo[];
  defaultLocale: string;
  manifestUrl: string;
  fetch?: typeof fetch;
  /** Persistent choice (localStorage) and per-tab session choice (sessionStorage). */
  storage?: KeyValueStore | null;
  session?: KeyValueStore | null;
  /** Development-only warning sink; omit in production. */
  warn?: (message: string) => void;
  document?: { documentElement: { lang: string; dir: string } } | null;
  /**
   * Offer and use only locales the loaded manifest actually publishes for this application. Without a manifest or a
   * release, the base language is used silently (stored / URL preferences are kept but ignored) and `locales()` is empty.
   */
  publishedOnly?: boolean;
  /**
   * Keeps the last published manifest per locale and the bundles it references (immutable URLs) so the next start
   * renders from the cache without waiting for the network, then checks for a newer release in the background.
   * Release previews (`?release=`) never read or write it.
   */
  cache?: CacheStore | null;
};
export type StartInput = { search?: string; navigatorLanguages?: readonly string[] };

const BASE = 'en', LOCALE_KEY = 'i18n.locale', SESSION_LOCALE_KEY = 'i18n.session-locale', RELEASE_KEY = 'i18n.release';
const CACHE_PREFIX = 'i18n.cache.', MANIFEST_CACHE = CACHE_PREFIX + 'manifest.', BUNDLE_CACHE = CACHE_PREFIX + 'bundle.';

function safeGet(store: KeyValueStore | null | undefined, key: string): string | null {
  try { return store?.getItem(key) ?? null; } catch { return null; }
}
function safeSet(store: KeyValueStore | null | undefined, key: string, value: string | null): void {
  try { if (value === null) store?.removeItem(key); else store?.setItem(key, value); } catch { /* storage unavailable */ }
}

/** Matches a requested tag to an enabled locale: exact (case-insensitive), then same primary language. */
export function matchLocale(requested: string | null | undefined, enabled: readonly string[]): string | null {
  if (!requested) return null;
  const wanted = requested.trim().toLowerCase();
  const exact = enabled.find(code => code.toLowerCase() === wanted);
  if (exact) return exact;
  const language = wanted.split(/[-_]/)[0];
  return enabled.find(code => code.toLowerCase().split('-')[0] === language) ?? null;
}

/** Locale source order: `?lang=` (session) > stored choice > browser languages > application default. */
export function resolveLocale(input: {
  query?: string | null; stored?: string | null; navigatorLanguages?: readonly string[]; enabled: readonly string[]; fallback: string;
}): string {
  const exactOnly = (value: string | null | undefined) => input.enabled.find(code => code.toLowerCase() === value?.toLowerCase()) ?? null;
  const fromQuery = exactOnly(input.query) ?? exactOnly(input.stored);
  if (fromQuery) return fromQuery;
  for (const language of input.navigatorLanguages ?? []) {
    const matched = matchLocale(language, input.enabled);
    if (matched) return matched;
  }
  return exactOnly(input.fallback) ?? input.enabled[0] ?? input.fallback;
}

function flatBundle(value: unknown): Record<string, string> | null {
  if (!value || typeof value !== 'object') return null;
  const flat: Record<string, string> = {};
  for (const [key, entry] of Object.entries(value as Record<string, MessageEntry>)) {
    const text = typeof entry === 'string' ? entry : entry?.text;
    if (typeof text === 'string' && text) flat[key] = text;
  }
  return flat;
}

function readManifest(value: unknown): Manifest | null {
  const m = value as Manifest | null;
  if (!m || typeof m !== 'object' || !Array.isArray(m.locales) || !m.bundles || typeof m.bundles !== 'object') return null;
  return m;
}

export function createI18n(options: I18nOptions) {
  const transport = options.fetch ?? ((...args: Parameters<typeof fetch>) => fetch(...args));
  const listeners = new Set<() => void>(), compiled = new Map<string, IntlMessageFormat>();
  const bundles = new Map<string, Record<string, string>>(), loading = new Map<string, Promise<void>>();
  const manifests = new Map<string, Manifest>(), needed = new Set<string>(), warned = new Set<string>();
  let locale = options.defaultLocale, releaseNo: string | null = null, query: string | null = null;
  let navigatorLanguages: readonly string[] = [], revision = 0, manifestFailed = false, activation = 0;
  const verified = new Set<string>(), checked = new Set<string>();

  const notify = () => { revision++; listeners.forEach(listener => listener()); };
  const known = (): LocaleInfo[] => manifests.get(locale)?.locales ?? manifests.get(BASE)?.locales ?? options.locales;
  const direction = (code: string) => known().find(item => item.code === code)?.direction
    ?? options.locales.find(item => item.code === code)?.direction ?? 'LTR';
  function applyDocument() {
    const root = options.document?.documentElement;
    if (root) { root.lang = locale; root.dir = direction(locale).toLowerCase(); }
  }
  function pick(enabled: readonly string[], fallback: string) {
    return resolveLocale({ query, stored: safeGet(options.storage, LOCALE_KEY), navigatorLanguages, enabled, fallback });
  }

  // ---- local cache (never used for `?release=` previews) ----
  const caching = () => !!options.cache && !releaseNo;
  function cacheRead<T>(key: string): T | null {
    try { const raw = options.cache?.getItem(key); return raw ? JSON.parse(raw) as T : null; } catch { return null; }
  }
  function cacheKeys(): string[] {
    const keys: string[] = [];
    try {
      const store = options.cache!;
      for (let index = 0; index < store.length; index++) { const key = store.key(index); if (key?.startsWith(CACHE_PREFIX)) keys.push(key); }
    } catch { /* storage unavailable */ }
    return keys;
  }
  function cacheClear(): void {
    for (const key of cacheKeys()) { try { options.cache?.removeItem(key); } catch { /* storage unavailable */ } }
  }
  /** Writes one entry; a quota (or any) failure drops the whole cache and the runtime carries on from memory. */
  function cacheWrite(key: string, value: unknown): void {
    if (!caching()) return;
    try { options.cache!.setItem(key, JSON.stringify(value)); } catch { cacheClear(); }
  }
  /** Drops cached bundles that no cached manifest references any more. */
  function cachePrune(): void {
    const keys = cacheKeys(), referenced = new Set<string>();
    for (const key of keys.filter(item => item.startsWith(MANIFEST_CACHE))) {
      const manifest = readManifest(cacheRead(key));
      for (const byNamespace of Object.values(manifest?.bundles ?? {})) {
        for (const entry of Object.values(byNamespace ?? {})) if (entry?.url) referenced.add(BUNDLE_CACHE + entry.url);
      }
    }
    for (const key of keys) {
      if (key.startsWith(BUNDLE_CACHE) && !referenced.has(key)) { try { options.cache?.removeItem(key); } catch { /* ignore */ } }
    }
  }
  function cacheManifest(code: string, manifest: Manifest): void {
    if (!caching()) return;
    cacheWrite(MANIFEST_CACHE + code, manifest);
    cachePrune();
  }

  async function requestManifest(code: string): Promise<Manifest | null> {
    const params = new URLSearchParams({ locale: code });
    if (releaseNo) params.set('releaseNo', releaseNo);
    try {
      const response = await transport(options.manifestUrl + '?' + params, { credentials: 'omit', headers: { Accept: 'application/json' } });
      return response.ok ? readManifest(await response.json()) : null;
    } catch { return null; }
  }
  async function fetchManifest(code: string): Promise<Manifest | null> {
    if (manifests.has(code)) return manifests.get(code)!;
    const manifest = await requestManifest(code);
    if (manifest) { manifests.set(code, manifest); cacheManifest(code, manifest); } else manifestFailed = true;
    return manifest;
  }
  function bundleUrl(code: string, namespace: string, manifest = manifests.get(locale)): string | undefined {
    return manifest?.bundles?.[code]?.[namespace]?.url;
  }
  /** Bundle URLs (base + `code`) the namespaces in use need under one manifest. */
  function neededUrls(manifest: Manifest | undefined, code: string): string[] {
    return [...needed].flatMap(ns => [bundleUrl(BASE, ns, manifest), bundleUrl(code, ns, manifest)]).filter((url): url is string => !!url);
  }
  function loadBundle(url: string): Promise<void> {
    if (bundles.has(url) || loading.has(url)) return loading.get(url) ?? Promise.resolve();
    const task = transport(url, { credentials: 'omit' })
      .then(response => (response.ok ? response.json() : null))
      .then((value: unknown) => {
        const flat = flatBundle(value);
        if (!flat) return;
        bundles.set(url, flat); cacheWrite(BUNDLE_CACHE + url, flat); notify();
      })
      .catch(() => { /* fall back to base text */ })
      .finally(() => { loading.delete(url); });
    loading.set(url, task);
    return task;
  }
  /** Loads the base and current-locale bundles for every namespace in use; failures never block rendering. */
  function loadNeeded(): Promise<void> {
    return Promise.all(neededUrls(manifests.get(locale), locale).map(loadBundle)).then(() => {});
  }

  /** A locale is usable when the manifest has a release, lists it, and (besides the base) has bundles for it. */
  function published(manifest: Manifest | null, code: string): boolean {
    return !!manifest && manifest.releaseNo !== null && manifest.locales.some(item => item.code === code)
      && (code === BASE || Object.keys(manifest.bundles?.[code] ?? {}).length > 0);
  }
  async function activatePublished(code: string): Promise<void> {
    const seq = ++activation;
    const manifest = await fetchManifest(code);
    if (seq !== activation) return;
    if (published(manifest, code)) { verified.add(BASE); verified.add(code); locale = code; }
    else {
      locale = BASE;
      if (manifest && manifest.releaseNo !== null) { if (!manifests.has(BASE)) manifests.set(BASE, manifest); verified.add(BASE); }
    }
    applyDocument(); notify();
    if (verified.has(BASE)) void verifyOffered();
    await loadNeeded();
  }
  /** Checks each listed locale's own manifest once, so the switcher only offers locales with published bundles. */
  let verifying: Promise<void> = Promise.resolve();
  function verifyOffered(): Promise<void> {
    verifying = verifying.then(checkListed);
    return verifying;
  }
  async function checkListed(): Promise<void> {
    const listed = (manifests.get(locale) ?? manifests.get(BASE))?.locales ?? [];
    await Promise.all(listed.filter(item => !verified.has(item.code) && !checked.has(item.code)).map(async item => {
      checked.add(item.code);
      if (published(await fetchManifest(item.code), item.code)) { verified.add(item.code); notify(); }
    }));
  }

  /**
   * Starts from the cache when the chosen locale's manifest (published, when `publishedOnly`) and every bundle it
   * needs are cached. Cached manifests count as verified, so the switcher works offline.
   */
  function startFromCache(code: string): boolean {
    if (!caching()) return false;
    const manifest = readManifest(cacheRead(MANIFEST_CACHE + code));
    if (!manifest || (options.publishedOnly ? !published(manifest, code) : !manifest.locales.some(item => item.code === code))) return false;
    const cached = new Map<string, Record<string, string>>();
    for (const url of neededUrls(manifest, code)) {
      const flat = flatBundle(cacheRead(BUNDLE_CACHE + url));
      if (!flat) return false;
      cached.set(url, flat);
    }
    cached.forEach((flat, url) => bundles.set(url, flat));
    manifests.set(code, manifest);
    locale = code; verified.add(BASE); verified.add(code);
    for (const item of manifest.locales) {
      const other = item.code === code ? manifest : readManifest(cacheRead(MANIFEST_CACHE + item.code));
      if (other && published(other, item.code)) verified.add(item.code);
    }
    applyDocument(); notify();
    return true;
  }
  /** After a cache start: reads the live manifest and swaps in new bundles only once they have loaded. */
  async function refreshFromNetwork(code: string): Promise<void> {
    const seq = activation;
    const fresh = await requestManifest(code);
    if (seq !== activation || locale !== code) return;
    if (!fresh) { manifestFailed = true; return; }
    if (options.publishedOnly && !published(fresh, code)) { manifests.delete(code); await activate(code); return; }
    const current = manifests.get(code);
    const changed = neededUrls(fresh, code).join('\n') !== neededUrls(current, code).join('\n') || fresh.releaseNo !== current?.releaseNo;
    if (changed) {
      await Promise.all(neededUrls(fresh, code).map(loadBundle));
      if (seq !== activation || locale !== code) return;
      manifests.set(code, fresh); notify();
    }
    cacheManifest(code, fresh);
    void verifyOffered();
  }

  async function activate(code: string): Promise<void> {
    if (options.publishedOnly) return activatePublished(code);
    locale = code; applyDocument(); notify();
    const manifest = await fetchManifest(code);
    if (!manifest || locale !== code) return;
    const enabled = manifest.locales.map(item => item.code);
    if (!enabled.includes(code)) {
      const next = pick(enabled, manifest.defaultLocale);
      if (next !== code) return activate(next);
    }
    applyDocument(); notify();
    await loadNeeded();
  }

  function lookup(namespace: string, key: string): { text: string; locale: string } | null {
    const full = namespace + '.' + key;
    const runtime = (code: string) => { const url = bundleUrl(code, namespace); return url ? bundles.get(url)?.[full] : undefined; };
    const target = locale === BASE ? undefined : runtime(locale);
    if (target) return { text: target, locale };
    const base = runtime(BASE);
    if (base) return { text: base, locale: BASE };
    const entry = options.builtin[namespace]?.[full];
    const text = typeof entry === 'string' ? entry : entry?.text;
    return text ? { text, locale: BASE } : null;
  }
  function format(text: string, code: string, params?: Params): string {
    if (!text.includes('{') && !text.includes("'")) return text;
    const cacheKey = code + '\u0000' + text;
    try {
      let message = compiled.get(cacheKey);
      if (!message) { message = new IntlMessageFormat(text, code, undefined, { ignoreTag: true }); compiled.set(cacheKey, message); }
      return String(message.format(params as Record<string, string | number | Date>));
    } catch (error) {
      options.warn?.('i18n: cannot format "' + text + '": ' + (error as Error).message);
      return text;
    }
  }

  /** Marks namespaces as in use so their runtime bundles load (fetches start asynchronously). */
  function use(...namespaces: string[]): void {
    let added = false;
    for (const ns of namespaces) if (!needed.has(ns)) { needed.add(ns); added = true; }
    if (added && manifests.has(locale)) void loadNeeded();
  }

  return {
    /** Resolves the locale from the URL, storage and browser, then reads the manifest; never rejects. */
    async start(input: StartInput = {}): Promise<void> {
      const params = new URLSearchParams(input.search ?? '');
      query = params.get('lang') ?? safeGet(options.session, SESSION_LOCALE_KEY);
      releaseNo = params.get('release') ?? safeGet(options.session, RELEASE_KEY);
      if (releaseNo && !/^[1-9][0-9]{0,9}$/.test(releaseNo)) releaseNo = null;
      if (params.has('lang')) safeSet(options.session, SESSION_LOCALE_KEY, query);
      if (params.has('release')) safeSet(options.session, RELEASE_KEY, releaseNo);
      navigatorLanguages = input.navigatorLanguages ?? [];
      const wanted = pick(options.locales.map(item => item.code), options.defaultLocale);
      if (startFromCache(wanted)) { void refreshFromNetwork(wanted); return; }
      await activate(wanted);
    },
    /**
     * Fetches `code`'s manifest and every bundle the namespaces in use need into the cache, so a reload after a
     * language switch renders from the cache. Never rejects; does nothing for release previews.
     */
    async prefetch(code: string): Promise<void> {
      if (!caching()) return;
      const manifest = await requestManifest(code);
      if (!manifest) return;
      await Promise.all(neededUrls(manifest, code).map(loadBundle));
      cacheManifest(code, manifest);
    },
    /** Remembers an explicit choice for the next start without switching now (the console reloads instead). */
    rememberLocale(code: string): void {
      query = null; safeSet(options.session, SESSION_LOCALE_KEY, null); safeSet(options.storage, LOCALE_KEY, code);
    },
    /** Explicit user choice: remembered in localStorage and replaces any `?lang=` override. */
    async setLocale(code: string): Promise<void> {
      query = null; safeSet(options.session, SESSION_LOCALE_KEY, null); safeSet(options.storage, LOCALE_KEY, code);
      await activate(code);
    },
    use,
    t(namespace: string, key: string, params?: Params): string {
      use(namespace);
      const found = lookup(namespace, key);
      if (found) return format(found.text, found.locale, params);
      const full = namespace + '.' + key;
      if (!warned.has(full)) { warned.add(full); options.warn?.('i18n: missing key "' + full + '"'); }
      return full;
    },
    /** Whether `key` resolves to a text (runtime or built-in), without warning when it does not. */
    has(namespace: string, key: string): boolean {
      use(namespace);
      return lookup(namespace, key) !== null;
    },
    locale: () => locale,
    locales: (): LocaleInfo[] => (options.publishedOnly ? known().filter(item => verified.has(item.code)) : known()),
    verifyOffered,
    manifestFailed: () => manifestFailed,
    releaseNo: () => releaseNo,
    revision: () => revision,
    loadNeeded,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
  };
}
export type I18n = ReturnType<typeof createI18n>;
