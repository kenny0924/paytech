// 任务卡 P2-stage5 G（主会话追加）：渠道列表 429 修复后的复验（无头 Chromium，只读浏览）。
//
// 依次打开渠道列表三个分段（全部 / 支付 / 服务），截图为 *-after-fix.png（不覆盖原缺陷截图）。每页记录：
//   - 分段文本（三个分段计数是否都显示）、表格行；
//   - 对 /api/operations/channel-admin/v1/channels（列表路由）的请求：总数、同一时刻在途的最大数量（request → requestfinished / requestfailed）、
//     状态码分布；HTTP >= 400 的请求与控制台 error / warning。
// 用法：node stage5p2_screens_fix.mjs（PLAYWRIGHT_CORE、SHOTS、PAUSE_MS 同 stage5p2_screens.mjs）。不打印口令或令牌。
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPERATIONS = 'https://127.0.0.1:18444';
const SHOTS = process.env.SHOTS || join(HERE, 'stage5p2-screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const PAUSE = Number(process.env.PAUSE_MS || 9000);
const LIST_PATH = '/api/operations/channel-admin/v1/channels';
const { chromium } = createRequire(import.meta.url)(CORE);

const secrets = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8')
  .split('\n').filter((line) => line.includes('=') && !line.startsWith('#'))
  .map((line) => [line.slice(0, line.indexOf('=')).trim(), line.slice(line.indexOf('=') + 1).trim().replace(/^"|"$/g, '')]));

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 }, locale: 'zh-CN' });
const page = await context.newPage();
const log = { pages: [], console: [], httpErrors: [] };
let current = 'login';
let stats = null;
page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) log.console.push({ page: current, type: m.type(), text: m.text().slice(0, 300) }); });
const isList = (request) => new URL(request.url()).pathname === LIST_PATH;
page.on('request', (request) => {
  if (!stats || !isList(request)) return;
  stats.total += 1; stats.inFlight += 1; stats.maxInFlight = Math.max(stats.maxInFlight, stats.inFlight);
  stats.queries.push(new URL(request.url()).search);
});
const done = (request) => { if (stats && isList(request)) stats.inFlight -= 1; };
page.on('requestfinished', done);
page.on('requestfailed', done);
page.on('response', (response) => {
  const url = new URL(response.url());
  if (stats && url.pathname === LIST_PATH) stats.statuses[response.status()] = (stats.statuses[response.status()] || 0) + 1;
  if (response.status() >= 400) log.httpErrors.push({ page: current, method: response.request().method(), path: url.pathname, status: response.status() });
});

await page.goto(OPERATIONS + '/');
await page.getByLabel('Email / 初始化账号').fill(secrets.E2E_ADMIN_EMAIL);
await page.getByLabel('密码').fill(secrets.E2E_ADMIN_PASSWORD);
await page.getByRole('button', { name: /^登\s*录$/ }).click();
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});

const text = async (selector) => (await page.locator(selector).allInnerTexts()).join(' | ').replace(/\s+/g, ' ').slice(0, 400);
for (const [name, path] of [['01-list-all', '/channel/channels'], ['02-list-payment', '/channel/channels?type=PAYMENT'],
  ['03-list-service', '/channel/channels?type=SERVICE']]) {
  await page.waitForTimeout(PAUSE);
  current = name;
  stats = { total: 0, inFlight: 0, maxInFlight: 0, statuses: {}, queries: [] };
  await page.goto(OPERATIONS + path);
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(1500);
  const entry = { name, path, segments: await text('.ant-segmented'), rows: await text('tbody tr'),
    alert: await text('.ant-alert'), listRoute: { ...stats, inFlight: undefined } };
  await page.screenshot({ path: join(SHOTS, `${name}-after-fix.png`), fullPage: true });
  log.pages.push(entry);
  console.log(JSON.stringify(entry));
  stats = null;
}
await browser.close();
writeFileSync(join(SHOTS, 'browser-log-after-fix.json'), JSON.stringify(log, null, 1) + '\n');
console.log(JSON.stringify({ console: log.console, httpErrors: log.httpErrors }));
