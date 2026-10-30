// tax 阶段 5：tax worktree ops-web（127.0.0.1:18315 → Nginx 18444 → Gateway → ops-bff → tax）税务查询页浏览器走查，对照原型 16—21。
// 组合：zh-CN / en × 1440 / 390（RUNS 可覆盖）。每组：初始态（无最近查询）→ 菜单入口 → 各演示订单 → 未找到 → 回初始态看最近查询 →
// 已登记订单下载税票（真实请求，只确认拿到链接并触发下载，不保存文件）→ 在浏览器侧把下载请求拦截为 503 模拟原型 21（不改环境）。
// 记录控制台 error / warning、HTTP >= 400（含 requestId）、页面上的原始 i18n key、横向溢出。凭据读 local-environment/e2e/secrets.env，不打印。
// 语言包公开桶 CORS 只放行 18444 / 18311，脚本在浏览器上下文为 19400 的响应补 ACAO（内容与状态不变，同 20261028 阶段 5 做法）。
// 用法：ORDERS='{"S1":"...","S2":"...","S4":"..."}' node stage5_ops_browser.mjs
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = 'http://127.0.0.1:18315';
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const ORDERS = JSON.parse(process.env.ORDERS || '{}');
const SIM_ID = 'b5e2c9d4-7a1f-4c3e-9d2b-5a7e00000021';
const MISSING = 'ORD-NOTFOUND-20261029';
const DOWNLOAD_ORDER = process.env.DOWNLOAD_ORDER || ORDERS.S1;
const secrets = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8')
  .split('\n').filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const L = { 'zh-CN': { menu: '税务查询', download: '下载 PDF', failed: '税票下载失败' }, en: { menu: 'Tax Lookup', download: 'Download PDF', failed: 'Receipt download failed' } };
const RAW_KEY = /\b(tax|nav|common|enum)\.[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+\b/g;
const log = { runs: [], console: [], httpErrors: [] };

const browser = await chromium.launch({ headless: true });
for (const [locale, width] of (process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]])) {
  const t = L[locale]; const tag = `${locale}-${width}`;
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width, height: width > 500 ? 1000 : 844 }, locale, acceptDownloads: true });
  await context.route(/^https:\/\/127\.0\.0\.1:19400\//, async route => {
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': ORIGIN } });
  });
  const page = await context.newPage();
  const run = { locale, width, steps: [] };
  let current = 'login';
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('response', async r => {
    if (r.status() < 400) return;
    let requestId = r.headers()['x-request-id'];
    try { requestId = requestId || (await r.json()).requestId; } catch { /* not JSON */ }
    log.httpErrors.push({ run: tag, step: current, method: r.request().method(), path: new URL(r.url()).pathname, status: r.status(), requestId });
  });
  const settle = async () => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(1000); };
  const probe = async () => page.evaluate(re => {
    const text = document.querySelector('main, .ant-layout-content, #root')?.innerText ?? document.body.innerText;
    const el = document.scrollingElement;
    return { rawKeys: [...new Set(text.match(new RegExp(re, 'g')) ?? [])].slice(0, 10), overflowX: el.scrollWidth - el.clientWidth,
      head: (document.querySelector('.tax-head, .tax-initial, .tax-empty')?.innerText ?? '').replace(/\s+/g, ' ').slice(0, 260) };
  }, RAW_KEY.source);
  const shot = async (step, extra) => {
    current = step; await settle();
    const entry = { step, ...(await probe()) };
    try { if (extra) entry.check = await extra(); } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    await page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }));
  };

  await page.goto(`${ORIGIN}/?lang=${locale}`);
  await page.locator('input:not([type=password])').first().fill(secrets.E2E_ADMIN_EMAIL);
  await page.locator('input[type=password]').first().fill(secrets.E2E_ADMIN_PASSWORD);
  await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
  await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
  await settle();

  await page.goto(`${ORIGIN}/finance/tax?lang=${locale}`);
  await shot('19-initial', async () => ({ menuEntries: await page.locator('.ant-menu-item', { hasText: t.menu }).count(),
    recentRows: await page.locator('.tax-recent-row').count() }));

  for (const [label, orderNo] of Object.entries(ORDERS)) {
    await page.goto(`${ORIGIN}/finance/tax?orderNo=${encodeURIComponent(orderNo)}&lang=${locale}`);
    await shot(`order-${label}`, async () => ({ orderNo, pill: await page.locator('.tax-title-row .tax-pill').innerText().catch(() => null),
      callout: (await page.locator('.tax-callout').first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 200),
      lines: await page.locator('.tax-lines tbody tr').count(), downloadButton: await page.getByRole('button', { name: t.download }).count() }));
  }

  await page.goto(`${ORIGIN}/finance/tax?orderNo=${MISSING}&lang=${locale}`);
  await shot('20-not-found');

  await page.goto(`${ORIGIN}/finance/tax?lang=${locale}`);
  await shot('19-initial-recent', async () => ({ recentRows: await page.locator('.tax-recent-row').count(),
    recent: (await page.locator('.tax-recent').innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 400) }));

  if (DOWNLOAD_ORDER) {
    await page.goto(`${ORIGIN}/finance/tax?orderNo=${encodeURIComponent(DOWNLOAD_ORDER)}&lang=${locale}`);
    await settle();
    current = 'download';
    const button = page.getByRole('button', { name: t.download });
    if (await button.count()) {
      const grant = page.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 20000 });
      const download = page.waitForEvent('download', { timeout: 20000 }).catch(e => ({ error: String(e).slice(0, 120) }));
      const popup = page.waitForEvent('popup', { timeout: 5000 }).catch(() => null);
      await button.click();
      const g = await grant; const d = await download; const p = await popup;
      let body = {}; try { body = await g.json(); } catch { /* ignore */ }
      await shot('16-download-ok', async () => ({ grantStatus: g.status(), grantKeys: Object.keys(body).sort(), urlHost: body.url ? new URL(body.url).host : null,
        downloadFile: d?.suggestedFilename?.() ?? d?.error ?? null, popup: p ? new URL(p.url()).host : null }));
      if (p) await p.close();
      current = '21-download-failed';
      await page.route(/\/api\/operations\/tax\/v1\/orders\/.*\/download$/, route => route.fulfill({ status: 503, headers: { 'x-request-id': SIM_ID },
        contentType: 'application/problem+json', body: JSON.stringify({ type: 'about:blank', title: 'Service Unavailable', status: 503,
          code: 'FILE_SERVICE_UNAVAILABLE', detail: 'simulated in browser', requestId: SIM_ID, timestamp: new Date().toISOString() }) }));
      await button.click();
      await page.locator('.tax-download-failure').waitFor({ timeout: 10000 }).catch(() => {});
      current = '21-download-failed';
      await shot('21-download-failed', async () => ({ notice: (await page.locator('.tax-download-failure').innerText().catch(() => '')).replace(/\s+/g, ' ') }));
      await page.unroute(/\/api\/operations\/tax\/v1\/orders\/.*\/download$/);
    } else {
      await shot('16-download-missing');
    }
  }
  log.runs.push(run);
  await context.close();
}
await browser.close();
writeFileSync(join(HERE, `stage5-ops-browser-result${process.env.RUNS ? '-' + log.runs.map(r => r.locale + '-' + r.width).join('_') : ''}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors }, null, 1));
