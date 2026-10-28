// REQ-01 阶段 5：worktree ops-web（127.0.0.1:18311 → Nginx 18444 → Gateway → ops-bff → commercial）浏览器走查，截图对照原型 14—17。
// 组合：zh-CN / en × 1440 / 390。写操作（暂停 → 恢复并检查）只在 zh-CN 1440 与 en 1440 各做一次（对 TOKEN_STORAGE），
// 其余组合只打开弹窗 / 抽屉后取消。记录控制台 error / warning 与 HTTP >= 400 的请求。凭据读 local-environment/e2e/secrets.env，不打印。
// 语言包公开桶 CORS 只放行 18444，脚本在浏览器上下文里为 19400 的响应补 ACAO（内容与状态不变）。
// 用法：node stage5_browser.mjs；PLAYWRIGHT_CORE 默认 ~/.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = 'http://127.0.0.1:18311';
const AGREEMENT = '900000000001390001';
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const secrets = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8')
  .split('\n').filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const name = (text) => new RegExp('^' + [...text].map(c => c.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\\s?') + '$');
const L = {
  'zh-CN': { products: /产品与开通/, check: '检查前置', suspend: '暂停', resume: '恢复', resumeOk: '恢复并检查', edit: '编辑', cancel: '取消', add: '+ 新增开通项' },
  en: { products: /Products & Enablements/, check: 'Check Prerequisites', suspend: 'Suspend', resume: 'Resume', resumeOk: 'Resume and Check', edit: 'Edit',
    cancel: 'Cancel', add: '+ Add Enablement' },
};
const log = { runs: [], console: [], httpErrors: [] };

const browser = await chromium.launch({ headless: true });
for (const [locale, width, writes] of (process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440, true], ['zh-CN', 390, false], ['en', 1440, true], ['en', 390, false]])) {
  const t = L[locale];
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width, height: width > 500 ? 1000 : 844 }, locale });
  // bundleCors：本地语言包公开桶（MinIO 19400）CORS 只放行 https://127.0.0.1:18444；为测试端口补同样的 ACAO（同 relsnap 阶段 5 做法）。
  // NO_BUNDLE_CORS=1：不补 ACAO，验证 MinIO 已放行 18311 后普通浏览器能直接加载语言包（裁决后）。
  if (!process.env.NO_BUNDLE_CORS) await context.route(/^https:\/\/127\.0\.0\.1:19400\//, async route => {
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': ORIGIN } });
  });
  const page = await context.newPage();
  const run = { locale, width, steps: [] };
  let current = 'login';
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: `${locale}-${width}`, step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('response', (r) => { if (new URL(r.url()).port === '19400') log.bundles = (log.bundles ?? 0) + 1; if (r.status() >= 400) log.httpErrors.push({ run: `${locale}-${width}`, step: current, method: r.request().method(), path: new URL(r.url()).pathname, status: r.status() }); });
  const settle = async () => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(1200); };
  const shot = async (step, check) => {
    current = step; await settle();
    const entry = { step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    await page.screenshot({ path: join(SHOTS, `${locale}-${width}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: `${locale}-${width}`, ...entry }));
  };
  const row = (code) => page.locator('tr.ant-table-row', { hasText: code }).first();
  const rowText = async (code) => (await row(code).innerText()).replace(/\s+/g, ' ');
  const modal = () => page.locator('.ant-modal:visible').last();
  // 窄屏下页签可能收进「更多」下拉（antd Tabs 溢出），此时从下拉进入
  const openProducts = async () => {
    await page.locator('.ant-tabs-tab').first().waitFor({ timeout: 30000 });
    const tab = page.locator('.ant-tabs-tab', { hasText: t.products });
    if (await tab.isVisible()) { await tab.click(); return; }
    await page.locator('.ant-tabs-nav-more').first().click();
    await page.locator('.ant-tabs-dropdown-menu-item', { hasText: t.products }).first().click();
  };

  await page.goto(`${ORIGIN}/?lang=${locale}`);
  await page.locator('input:not([type=password])').first().fill(secrets.E2E_ADMIN_EMAIL);
  await page.locator('input[type=password]').first().fill(secrets.E2E_ADMIN_PASSWORD);
  await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
  await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
  await settle();

  await page.goto(`${ORIGIN}/commercial/agreements/${AGREEMENT}?lang=${locale}`);
  await settle();
  await openProducts();
  await shot('01-products', async () => ({
    nextStep: (await page.locator('.cc-next-step').innerText().catch(() => '')).replace(/\s+/g, ' '),
    head: (await page.locator('.cc-product-block .commercial-block-head').first().innerText()).replace(/\s+/g, ' '),
    rows: await Promise.all(['CARD', 'TOKEN_STORAGE', 'APPLE_PAY', 'GOOGLE_PAY'].map(rowText)),
  }));

  await row('APPLE_PAY').locator('.ant-table-row-expand-icon').click();
  await row('CARD').locator('.ant-table-row-expand-icon').click();
  await shot('02-expanded', async () => (await page.locator('.cc-prereq').allInnerTexts()).map(x => x.replace(/\s+/g, ' ')));

  await page.locator('.cc-product-block .commercial-block-head').first().locator('button', { hasText: t.check }).click();
  await shot('03-checked', async () => (await page.locator('.cc-product-block .commercial-block-head').first().innerText()).replace(/\s+/g, ' '));

  await row('TOKEN_STORAGE').getByRole('button', { name: name(t.suspend) }).click();
  await shot('04-suspend-modal', async () => (await modal().innerText()).replace(/\s+/g, ' '));
  if (writes) {
    await modal().locator('textarea').fill(locale === 'en' ? 'Paused for the stage 5 walkthrough' : '阶段 5 走查：临时停用令牌存储');
    await modal().getByRole('button', { name: name(t.suspend) }).click();
    await shot('05-suspended', async () => rowText('TOKEN_STORAGE'));
    await row('TOKEN_STORAGE').getByRole('button', { name: name(t.resume) }).click();
    await page.waitForTimeout(1500);
    await shot('06-resume-modal', async () => (await modal().innerText()).replace(/\s+/g, ' '));
    await modal().getByRole('button', { name: name(t.resumeOk) }).click();
    await page.waitForTimeout(4000);
    await page.reload(); await settle();
    await openProducts();
    await shot('07-resumed', async () => rowText('TOKEN_STORAGE'));
  } else {
    await modal().getByRole('button', { name: name(t.cancel) }).click();
    await row('GOOGLE_PAY').getByRole('button', { name: name(t.resume) }).click();
    await page.waitForTimeout(1500);
    await shot('06-resume-modal', async () => (await modal().innerText()).replace(/\s+/g, ' '));
    await modal().getByRole('button', { name: name(t.cancel) }).click();
  }

  await row('CARD').getByRole('button', { name: name(t.edit) }).click();
  await shot('08-edit-drawer', async () => (await page.locator('.ant-drawer:visible').innerText()).replace(/\s+/g, ' ').slice(0, 800));
  await page.locator('.ant-drawer:visible').getByRole('button', { name: name(t.cancel) }).click();
  await page.waitForTimeout(600);

  await page.locator('.cc-product-block .commercial-block-head').first().locator('button', { hasText: t.add.replace('+ ', '') }).click();
  await shot('09-add-drawer', async () => (await page.locator('.ant-drawer:visible').innerText()).replace(/\s+/g, ' ').slice(0, 600));
  await page.locator('.ant-drawer:visible').getByRole('button', { name: name(t.cancel) }).click();

  log.runs.push(run);
  await context.close();
}
await browser.close();
writeFileSync(join(HERE, `stage5-browser-result${process.env.RUNS ? '-' + log.runs.map(r => r.locale + '-' + r.width).join('_') : ''}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors }, null, 1));
