// tax 阶段 7 mor-test 浏览器走查（runbook 第 6 节 A6—A7）：http://localhost:28080（Nginx → Gateway → ops-bff → tax；语言包同源，不改写响应）。
// 改写自 verification/stage5/stage5_ops_browser.mjs（页面步骤）与 REQ-03 release/acceptance/browser/stage7_browser.mjs（登录、应用内跳转）。
// 组合：zh-CN / en × 1440 / 390（RUNS 可覆盖）。每组：初始态 → 各验收订单（S1 已登记 + 已开票、S2 科威特阻断、发布后 STANDARD 阻断、存量订单 404）
// → 未找到 → 回初始态看最近查询 → S1 下载税票（真实授权请求，只确认拿到链接并触发下载，不保存文件）→ 浏览器侧拦截下载为 503 模拟原型 21
// → 回归：REQ-03 风控 · 承保列表、工作流定义页（wfpanel）。
// 只读：除税票下载授权 POST（只签发 60 秒链接）外不发写请求；记录控制台 error / warning、HTTP >= 400、原始 i18n key、横向溢出。
// 订单号读同目录 tax-api-result.json（tax_api.py scenario / after 的输出）；ORDERS='{"S1":"ord_…"}' 可覆盖。
// 凭据：管理员读 test-environment/private/access.md，不打印。
// 用法：node tax_browser.mortest.mjs；RUNS='[["zh-CN",390]]' 可只跑一组。Operations 路由每人每分钟 30 次 GET，脚本用应用内跳转，组间暂停 PAUSE_MS（缺省 20 秒）。
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'http://localhost:28080';
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const field = name => access.split('\n').find(line => line.startsWith(name + '：')).slice(name.length + 1).trim();
const admin = { email: field('账号'), password: field('密码') };
const api = existsSync(join(HERE, 'tax-api-result.json')) ? JSON.parse(readFileSync(join(HERE, 'tax-api-result.json'), 'utf8')) : {};
const ORDERS = process.env.ORDERS ? JSON.parse(process.env.ORDERS) : Object.fromEntries(Object.entries({
  S1: api.scenario?.S1?.create?.orderNo, S2: api.scenario?.S2?.create?.orderNo,
  STANDARD_US: api.after?.standardUS?.orderNo, STANDARD_AE: api.after?.standardAE?.orderNo,
  EXISTING: api.after?.existingPaidOrder?.[0]?.orderNo,
}).filter(([, v]) => v));
const SIM_ID = 'b5e2c9d4-7a1f-4c3e-9d2b-5a7e00000021';
const MISSING = 'ORD-NOTFOUND-20261029';
const PAUSE = Number(process.env.PAUSE_MS ?? 20000);
const L = { 'zh-CN': { menu: '税务查询', download: '下载 PDF' }, en: { menu: 'Tax Lookup', download: 'Download PDF' } };
const RAW_KEY = /\b(tax|nav|common|enum|risk|workflow)\.[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+\b/g;
const log = { orders: ORDERS, runs: [], console: [], httpErrors: [], writes: [] };
const browser = await chromium.launch({ headless: true });
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];

for (const [index, [locale, width]] of runs.entries()) {
  if (index > 0) await new Promise(resolve => setTimeout(resolve, PAUSE));
  const t = L[locale]; const tag = `${locale}-${width}`;
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale, viewport: { width, height: width < 600 ? 844 : 1000 },
    isMobile: width < 600, hasTouch: width < 600, acceptDownloads: true });
  await context.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
  const page = await context.newPage();
  const run = { locale, width, steps: [] };
  let current = 'login';
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('pageerror', e => log.console.push({ run: tag, step: current, type: 'pageerror', text: String(e).slice(0, 300) }));
  page.on('response', async r => {
    if (r.status() < 400) return;
    let requestId = r.headers()['x-request-id'];
    try { requestId = requestId || (await r.json()).requestId; } catch { /* not JSON */ }
    log.httpErrors.push({ run: tag, step: current, method: r.request().method(), path: new URL(r.url()).pathname, status: r.status(), requestId });
  });
  page.on('request', r => { const u = new URL(r.url()); if (r.method() !== 'GET' && u.pathname.startsWith('/api/') && !u.pathname.includes('/auth/v1/'))
    log.writes.push({ run: tag, step: current, method: r.method(), path: u.pathname }); });
  const settle = async (ms = 1000) => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); };
  const go = async path => { current = `goto ${path}`;
    await page.evaluate(p => { window.history.pushState({}, '', p); window.dispatchEvent(new PopStateEvent('popstate')); }, path); await settle(1500); };
  const probe = async () => page.evaluate(re => {
    const text = document.querySelector('main, .ant-layout-content, #root')?.innerText ?? document.body.innerText;
    const el = document.scrollingElement;
    return { rawKeys: [...new Set(text.match(new RegExp(re, 'g')) ?? [])].filter(k => !/\.(ae|test|com|invalid)$/.test(k)).slice(0, 10),
      overflowX: el.scrollWidth - el.clientWidth,
      head: (document.querySelector('.tax-head, .tax-initial, .tax-empty, .uw-head')?.innerText ?? '').replace(/\s+/g, ' ').slice(0, 260) };
  }, RAW_KEY.source);
  const shot = async (step, extra) => {
    current = step; await settle();
    const entry = { step, ...(await probe()) };
    try { if (extra) entry.check = await extra(); } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    await page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1200));
  };

  await page.goto(`${OPS}/?lang=${locale}`);
  await page.locator('input[type=password]').first().waitFor({ timeout: 30000 });
  for (let attempt = 0; attempt < 2 && await page.locator('input[type=password]').count(); attempt++) {
    await settle(1500);
    await page.locator('input:not([type=password])').first().fill(admin.email);
    await page.locator('input[type=password]').first().fill(admin.password);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    await page.waitForFunction(() => !document.querySelector('input[type=password]'), null, { timeout: 30000 }).catch(() => {});
  }
  await settle();
  await go('/finance/tax');
  await shot('19-initial', async () => ({ menuEntries: await page.locator('.ant-menu-item', { hasText: t.menu }).count(),
    recentRows: await page.locator('.tax-recent-row').count() }));
  for (const [label, orderNo] of Object.entries(ORDERS)) {
    await go(`/finance/tax?orderNo=${encodeURIComponent(orderNo)}`);
    await shot(`order-${label}`, async () => ({ orderNo, pill: await page.locator('.tax-title-row .tax-pill').innerText().catch(() => null),
      callout: (await page.locator('.tax-callout').first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 200),
      lines: await page.locator('.tax-lines tbody tr').count(), downloadButton: await page.getByRole('button', { name: t.download }).count() }));
  }
  await go(`/finance/tax?orderNo=${MISSING}`);
  await shot('20-not-found');
  await go('/finance/tax');
  await shot('19-initial-recent', async () => ({ recentRows: await page.locator('.tax-recent-row').count() }));
  if (ORDERS.S1) {
    await go(`/finance/tax?orderNo=${encodeURIComponent(ORDERS.S1)}`);
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
      await page.route(/\/api\/operations\/tax\/v1\/orders\/.*\/download$/, route => route.fulfill({ status: 503, headers: { 'x-request-id': SIM_ID },
        contentType: 'application/problem+json', body: JSON.stringify({ type: 'about:blank', title: 'Service Unavailable', status: 503,
          code: 'FILE_SERVICE_UNAVAILABLE', detail: 'simulated in browser', requestId: SIM_ID, timestamp: new Date().toISOString() }) }));
      await button.click();
      await page.locator('.tax-download-failure').waitFor({ timeout: 10000 }).catch(() => {});
      await shot('21-download-failed', async () => ({ notice: (await page.locator('.tax-download-failure').innerText().catch(() => '')).replace(/\s+/g, ' ') }));
      await page.unroute(/\/api\/operations\/tax\/v1\/orders\/.*\/download$/);
    } else {
      await shot('16-download-missing');
    }
  }
  // 回归：REQ-03 承保列表、工作流定义（wfpanel 现网改动所在页面组）
  await go('/risk/underwriting');
  await page.locator('.uw-page, .ant-alert, .ant-result').first().waitFor({ timeout: 15000 }).catch(() => {});
  await shot('90-regression-risk-underwriting', async () => ({ rows: await page.locator('.ant-table-row').count(), alerts: await page.locator('.ant-alert').count() }));
  await go('/workflow/definitions');
  await shot('91-regression-workflow-definitions', async () => ({ rows: await page.locator('.ant-table-row').count(), alerts: await page.locator('.ant-alert').count() }));
  log.runs.push(run);
  await context.close();
}
await browser.close();
const out = join(HERE, `browser-result-${new Date().toISOString().replace(/[:.]/g, '')}.json`);
writeFileSync(out, JSON.stringify(log, null, 2));
console.log(JSON.stringify({ result: out, console: log.console.length, httpErrors: log.httpErrors, writes: log.writes }, null, 1));
