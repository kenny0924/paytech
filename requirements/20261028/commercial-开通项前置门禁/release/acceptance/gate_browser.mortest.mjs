// REQ-01 阶段 7 浏览器验收（mor-test，http://localhost:28080；改写自阶段 5 stage5_browser.mjs，登录方式同法务修订 ops_legal.mortest.mjs，
// 凭据取 test-environment/private/access.md，不打印）。只读走查：不点「检查前置」「暂停」「恢复」的确认按钮（写操作由 gate_api.py scenario 完成），
// 弹窗 / 抽屉打开后取消。前提：gate_api.py scenario 已在 --agreement-no（缺省 DEMO2-CL-103）上新增 TOKEN_STORAGE / APPLE_PAY。
// 组合缺省 en / zh-CN × 1440（RUNS='[["en",390]]' 可加窄屏）。记录控制台 error、HTTP ≥ 400、原始 i18n key；截图写 screens/。
// 回归：法务修订的入驻配置页、卡 BIN 库、参考数据页、对账页可打开且无 5xx（确认未回退其他会话的页面）。
// 用法：node gate_browser.mortest.mjs [合同 ID]；PLAYWRIGHT_CORE 默认 ~/.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = 'http://localhost:28080';
const AGREEMENT = process.argv[2] || '238411681049415680'; // DEMO2-CL-103
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const cred = { email: access.match(/账号：(\S+)/)[1], password: access.match(/密码：(\S+)/)[1] };
const name = (text) => new RegExp('^' + [...text].map(c => c.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\\s?') + '$');
const L = {
  'zh-CN': { products: /产品与开通/, check: '检查前置', suspend: '暂停', edit: '编辑', cancel: '取消', add: '新增开通项',
    badges: [/已开通/, /等前置/], pending: /价格未覆盖|等前置/ },
  en: { products: /Products & Enablements/, check: 'Check Prerequisites', suspend: 'Suspend', edit: 'Edit', cancel: 'Cancel', add: 'Add Enablement',
    badges: [/Enabled/, /Pending/i], pending: /Price not covered|Pending/i },
};
const RAWKEY = /\bcommercial\.[A-Za-z][\w-]*(\.[\w-]+)+/g;
const REGRESSION = ['/client-onboarding/config', '/card-bin-library', '/reference-data', '/finance/reconciliation'];
const log = { agreement: AGREEMENT, runs: [], console: [], httpErrors: [], rawKeys: [] };

const browser = await chromium.launch({ headless: true });
for (const [locale, width] of (process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['en', 1440]])) {
  const t = L[locale];
  const context = await browser.newContext({ viewport: { width, height: width > 500 ? 1000 : 844 }, locale });
  await context.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
  const page = await context.newPage();
  const run = { locale, width, steps: [] };
  let current = 'login';
  page.on('console', (m) => { if (m.type() === 'error') log.console.push({ run: `${locale}-${width}`, step: current, text: m.text().slice(0, 300) }); });
  page.on('response', (r) => { const u = new URL(r.url()); if (u.pathname.startsWith('/api/') && r.status() >= 400) log.httpErrors.push({ run: `${locale}-${width}`, step: current, method: r.request().method(), path: u.pathname, status: r.status(), requestId: r.headers()['x-request-id'] }); });
  const settle = async () => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(1200); };
  const shot = async (step, check) => {
    current = step; await settle();
    const entry = { step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    const body = await page.locator('body').innerText().catch(() => '');
    const keys = [...new Set(body.match(RAWKEY) ?? [])];
    if (keys.length) log.rawKeys.push({ run: `${locale}-${width}`, step, keys: keys.slice(0, 10) });
    await page.screenshot({ path: join(SHOTS, `${locale}-${width}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: `${locale}-${width}`, ...entry }).slice(0, 600));
  };
  const row = (code) => page.locator('tr.ant-table-row', { hasText: code }).first();
  const rowText = async (code) => (await row(code).innerText().catch(() => '')).replace(/\s+/g, ' ');
  const modal = () => page.locator('.ant-modal:visible').last();
  const openProducts = async () => {
    await page.locator('.ant-tabs-tab').first().waitFor({ timeout: 30000 });
    const tab = page.locator('.ant-tabs-tab', { hasText: t.products });
    if (await tab.isVisible()) { await tab.click(); return; }
    await page.locator('.ant-tabs-nav-more').first().click();
    await page.locator('.ant-tabs-dropdown-menu-item', { hasText: t.products }).first().click();
  };

  await page.goto(`${WEB}/`); await settle();
  await page.locator('input:not([type=password])').first().fill(cred.email);
  await page.locator('input[type=password]').first().fill(cred.password);
  await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
  await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
  await settle();

  await page.goto(`${WEB}/commercial/agreements/${AGREEMENT}`); await settle();
  await openProducts();
  await shot('01-products', async () => ({
    head: (await page.locator('.cc-product-block .commercial-block-head').first().innerText()).replace(/\s+/g, ' '),
    rows: await Promise.all(['CARD', 'GOOGLE_PAY', 'STC_PAY', 'TOKEN_STORAGE', 'APPLE_PAY'].map(rowText)),
    nextStep: (await page.locator('.cc-next-step').innerText().catch(() => '')).replace(/\s+/g, ' '),
  }));
  const expand = row('APPLE_PAY').locator('.ant-table-row-expand-icon');
  if (await expand.count()) await expand.click();
  await shot('02-expanded', async () => (await page.locator('.cc-prereq').allInnerTexts()).map(x => x.replace(/\s+/g, ' ')));

  await row('TOKEN_STORAGE').getByRole('button', { name: name(t.suspend) }).click();
  await shot('03-suspend-modal', async () => (await modal().innerText()).replace(/\s+/g, ' '));
  await modal().getByRole('button', { name: name(t.cancel) }).click();

  await row('CARD').getByRole('button', { name: name(t.edit) }).click();
  await shot('04-edit-drawer', async () => (await page.locator('.ant-drawer:visible').innerText()).replace(/\s+/g, ' ').slice(0, 800));
  await page.locator('.ant-drawer:visible').getByRole('button', { name: name(t.cancel) }).click();
  await page.waitForTimeout(600);

  await page.locator('.cc-product-block .commercial-block-head').first().locator('button', { hasText: t.add }).click();
  await shot('05-add-drawer', async () => (await page.locator('.ant-drawer:visible').innerText()).replace(/\s+/g, ' ').slice(0, 600));
  await page.locator('.ant-drawer:visible').getByRole('button', { name: name(t.cancel) }).click();

  for (const path of REGRESSION) {
    await page.goto(`${WEB}${path}`); await settle();
    await shot('90-regression' + path.replace(/\//g, '-'), async () => ({ title: await page.title(), url: page.url() }));
  }
  log.runs.push(run);
  await context.close();
}
await browser.close();
writeFileSync(join(HERE, `browser-result-${new Date().toISOString().replace(/[:.]/g, '')}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors, rawKeys: log.rawKeys }, null, 1));
process.exit(log.httpErrors.some(e => e.status >= 500) || log.rawKeys.length ? 1 : 0);
