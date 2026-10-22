// 阶段 7 mor-test 运营台页面回归（acceptance-plan 第 4 节）：无头 Chromium 逐页打开（只读），记录控制台 error 与 HTTP >= 400，
// 渠道详情抽 I_CYBS_NI / I_MPGS_NI；标准 API 页抽 REFUND / CAPTURE / PAY。截图写 screens/reg-*.png。口令只在内存。
// 用法：node accept_pages.mjs
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'http://localhost:28080';
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const email = access.match(/账号：(\S+)/)[1], password = access.match(/密码：(\S+)/)[1];

const browser = await chromium.launch({ headless: true });
const page = await (await browser.newContext({ viewport: { width: 1600, height: 1000 }, locale: 'zh-CN' })).newPage();
const log = { pages: [], console: [], httpErrors: [] };
let current = 'login';
page.on('console', (m) => { if (m.type() === 'error') log.console.push({ page: current, text: m.text().slice(0, 300) }); });
page.on('response', (r) => { if (r.status() >= 400) { const u = new URL(r.url()); log.httpErrors.push({ page: current, method: r.request().method(), path: u.pathname, status: r.status() }); } });
const settle = async () => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(1200); };
async function visit(name, route, check) {
  current = name;
  if (route) await page.goto(OPS + route);
  await settle();
  const entry = { name, url: page.url().replace(OPS, '') };
  entry.text = (await page.locator('main, .ant-layout-content').first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 300);
  if (check) { try { entry.check = await check(); } catch (e) { entry.check = { error: String(e).slice(0, 200) }; } }
  await page.screenshot({ path: join(HERE, 'screens', `reg-${name}.png`) });
  log.pages.push(entry);
  console.log(JSON.stringify({ name, url: entry.url, text: entry.text.slice(0, 120), check: entry.check }));
}

await page.goto(OPS + '/');
await page.getByLabel('Email / 初始化账号').fill(email);
await page.getByLabel('密码').fill(password);
await page.getByRole('button', { name: /^登\s*录$/ }).click();
await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
await visit('home', null);
for (const [name, route] of [['wf-my-tasks', '/workflow/my-tasks'], ['wf-instances', '/workflow/instances'], ['wf-definitions', '/workflow/definitions'],
  ['wf-action-templates', '/workflow/action-templates'], ['file-storage', '/file-storage'], ['dq-sources', '/data-query/data-sources'],
  ['dq-templates', '/data-query/templates'], ['channels', '/channel/channels'], ['mids', '/channel/mids'], ['standard-apis', '/external-api/standard-apis'],
  ['gateway-configuration', '/gateway-configuration'], ['card-bin-library', '/card-bin-library']]) {
  await visit(name, route);
}
// 渠道详情：在渠道列表点 I_CYBS_NI / I_MPGS_NI
for (const code of ['I_CYBS_NI', 'I_MPGS_NI']) {
  await page.goto(OPS + '/channel/channels'); await settle();
  const row = page.locator('tr', { hasText: code }).first();
  if (await row.count()) {
    await row.getByRole('link').first().click().catch(() => row.click());
    await visit(`channel-${code}`, null, async () => ({ hasStandardApis: (await page.content()).includes('标准 API') }));
  }
}
// 标准 API 详情：REFUND / CAPTURE 仍含 paymentChannelRef
for (const code of ['REFUND', 'CAPTURE', 'PAY']) {
  await page.goto(OPS + '/external-api/standard-apis'); await settle();
  const row = page.locator('tr', { hasText: code }).filter({ hasNotText: code + '_' }).first();
  if (await row.count()) {
    await row.getByRole('link').first().click().catch(() => row.click());
    await visit(`standard-api-${code}`, null, async () => {
      const html = await page.content();
      return { paymentChannelRef: html.includes('paymentChannelRef'), threeDsRequired: html.includes('threeDsRequired') };
    });
  }
}
await browser.close();
writeFileSync(join(HERE, 'screens', 'reg-browser-log.json'), JSON.stringify(log, null, 1) + '\n');
console.log(JSON.stringify({ console: log.console, httpErrors: log.httpErrors }));
