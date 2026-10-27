// Client 门户 UI 冒烟公共部分：浏览器、扫描（错误横幅 / 原样 key / 横向溢出）、响应体落盘。不记录口令或令牌。
import { createRequire } from 'node:module';
import { mkdirSync, writeFileSync, appendFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const HERE = dirname(fileURLToPath(import.meta.url));
export const WEB = 'https://localhost:28443';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(join(HERE, 'screens'), { recursive: true }); mkdirSync(join(HERE, 'bodies'), { recursive: true });

const ERR = /暂时无法加载|服务暂时不可用|暂时不可用|INVALID_RESPONSE|temporarily unavailable|couldn.t load|could not be loaded|Unable to load|Failed to load|加载失败|请求失败|Request failed|Something went wrong|出错了/i;
const RAWKEY = /\b(onboarding-schema|onboardingSchema|onboarding|common|auth|workspace|enum|schema|materials|form|task_intro|start|review|people|declaration|upload|identifier|returned|shell|action)\.[A-Za-z_][\w-]*(\.[\w-]+)*/g;
export const slug = s => s.replace(/[^\w.一-鿿-]+/g, '_').slice(0, 90);

export async function open({ lang, width = 1440, tag }) {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width, height: width < 600 ? 844 : 900 }, ignoreHTTPSErrors: true, isMobile: width < 600, hasTouch: width < 600 });
  await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, lang);
  const page = await ctx.newPage();
  const s = { browser, ctx, page, lang, width, tag, current: 'start', findings: [], n: 0 };
  const log = join(HERE, `client-findings-${tag}.jsonl`);
  s.note = (kind, detail) => { const f = { tag, lang, width, page: s.current, kind, ...detail }; s.findings.push(f); appendFileSync(log, JSON.stringify(f) + '\n'); console.log('FIND', JSON.stringify(f).slice(0, 500)); };
  page.on('pageerror', e => s.note('pageerror', { text: String(e).slice(0, 300) }));
  page.on('console', m => { if (m.type() === 'error') s.note('console-error', { text: m.text().slice(0, 300) }); });
  page.on('response', async r => {
    const u = new URL(r.url()); if (!u.pathname.startsWith('/api/')) { if (r.status() >= 400 && !/favicon/.test(u.pathname)) s.note('http-failure', { url: r.url().slice(0, 200), status: r.status() }); return; }
    const rid = r.headers()['x-request-id'];
    const sensitive = /\/auth\//.test(u.pathname);
    let body = ''; if (!sensitive) { try { body = await r.text(); } catch {} }
    const file = `bodies/client-${tag}-${String(++s.n).padStart(4, '0')}-${slug(r.request().method() + u.pathname)}.json`;
    if (!sensitive) writeFileSync(join(HERE, file), JSON.stringify({ page: s.current, method: r.request().method(), url: u.pathname + u.search, status: r.status(), requestId: rid, request: r.request().method() === 'GET' ? undefined : (r.request().postData() ?? '').slice(0, 4000), body }, null, 1));
    if (r.status() >= 400) s.note('http-failure', { method: r.request().method(), url: u.pathname + u.search, status: r.status(), requestId: rid, body: sensitive ? '(auth, not recorded)' : body.slice(0, 300), file: sensitive ? null : file });
  });
  page.on('requestfailed', r => { if (!/abort/i.test(r.failure()?.errorText ?? '')) s.note('request-failed', { url: r.url().slice(0, 200), error: r.failure()?.errorText }); });
  s.settle = async (ms = 700) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
  s.shot = async name => { const f = `screens/client-${tag}-${slug(name)}.png`; await page.screenshot({ path: join(HERE, f), fullPage: true }); return f; };
  s.scan = async (name, extra = {}) => {
    s.current = name;
    const body = await page.locator('body').innerText().catch(() => '');
    const errs = [...new Set((body.match(new RegExp(`[^\\n]*(${ERR.source})[^\\n]*`, 'gi')) ?? []).map(x => x.trim().slice(0, 200)))];
    const keys = [...new Set(body.match(RAWKEY) ?? [])].filter(k => !/@|example\.test|\.(ae|sa|eg|com|pdf)$/i.test(k));
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    const file = await s.shot(name);
    if (errs.length) s.note('error-banner', { text: errs, screenshot: file, ...extra });
    if (keys.length) s.note('raw-i18n-key', { keys, screenshot: file });
    if (overflow > 2) s.note('horizontal-overflow', { px: overflow, screenshot: file });
    return { errs, keys, body, file };
  };
  s.close = () => browser.close();
  return s;
}
