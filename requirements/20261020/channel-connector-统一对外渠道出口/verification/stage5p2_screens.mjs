// 任务卡 P2-stage5 F：第二段渠道页本机截图（无头 Chromium，只读浏览，不在页面上做写操作）。
//
// 登录 https://127.0.0.1:18444（Nginx → ops-web vite 开发服务器 / Gateway → ops-bff），依次打开：渠道列表三个分段、支付渠道详情
// （概览、协议配置、沙箱验证）、服务渠道详情（概览、凭据、调用授权矩阵与列表）、出站地址编辑弹窗（只打开后取消）、
// 旧地址 /external-api/integrations/PAYERMAX_MOCK 的跳转。每页截图到 SHOTS，并记录浏览器控制台 error / warning 与 HTTP >= 400 的请求
// （只记方法、路径、状态）。不打印口令或令牌。
//
// 用法：PAYMENT_ID=<渠道 ID> SERVICE_ID=<渠道 ID> node stage5p2_screens.mjs
//   PLAYWRIGHT_CORE 默认 ~/.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core（1.58.1）；SHOTS 默认 ./stage5p2-screens。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPERATIONS = 'https://127.0.0.1:18444';
const SHOTS = process.env.SHOTS || join(HERE, 'stage5p2-screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
const PAYMENT_ID = process.env.PAYMENT_ID;
const SERVICE_ID = process.env.SERVICE_ID;

function secrets() {
  const text = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8');
  const values = {};
  for (const line of text.split('\n')) {
    const at = line.indexOf('=');
    if (at > 0 && !line.startsWith('#')) values[line.slice(0, at).trim()] = line.slice(at + 1).trim().replace(/^"|"$/g, '');
  }
  return values;
}

mkdirSync(SHOTS, { recursive: true });
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 }, locale: 'zh-CN' });
const page = await context.newPage();
const log = { pages: [], console: [], httpErrors: [] };
let current = 'login';
page.on('console', (message) => {
  if (['error', 'warning'].includes(message.type())) {
    log.console.push({ page: current, type: message.type(), text: message.text().slice(0, 300) });
  }
});
page.on('response', (response) => {
  if (response.status() >= 400) {
    const url = new URL(response.url());
    log.httpErrors.push({ page: current, method: response.request().method(), path: url.pathname, status: response.status() });
  }
});

async function settle() {
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(800);
}

const PAUSE = Number(process.env.PAUSE_MS || 9000);  // Gateway 管理路由按账号 / IP 限流（30 次 / 分钟），页面间留间隔避免 429

async function shot(name, path, check) {
  current = name;
  if (path) {
    await page.waitForTimeout(PAUSE);
    await page.goto(OPERATIONS + path);
  }
  await settle();
  const entry = { name, requested: path, finalUrl: page.url().replace(OPERATIONS, '') };
  if (check) {
    try { entry.check = await check(); } catch (error) { entry.check = { error: String(error).slice(0, 200) }; }
  }
  await page.screenshot({ path: join(SHOTS, `${name}.png`), fullPage: true });
  log.pages.push(entry);
  console.log(JSON.stringify(entry));
}

const text = async (selector) => (await page.locator(selector).allInnerTexts()).join(' | ').replace(/\s+/g, ' ').slice(0, 400);

// 登录
const s = secrets();
await page.goto(OPERATIONS + '/');
await page.getByLabel('Email / 初始化账号').fill(s.E2E_ADMIN_EMAIL);
await page.getByLabel('密码').fill(s.E2E_ADMIN_PASSWORD);
await page.getByRole('button', { name: /^登\s*录$/ }).click();
await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
await settle();

const menuText = async () => text('.ant-layout-sider, nav');
await shot('01-list-all', '/channel/channels', async () => ({ menu: await menuText(), tabs: await text('.ant-segmented, .ant-tabs-nav'),
  rows: await page.locator('tbody tr').count() }));
await shot('02-list-payment', '/channel/channels?type=PAYMENT', async () => ({ rows: await text('tbody tr') }));
await shot('03-list-service', '/channel/channels?type=SERVICE', async () => ({ rows: await text('tbody tr') }));
await shot('04-payment-overview', `/channel/channels/${PAYMENT_ID}?tab=overview`, async () => ({ tabs: await text('.ant-tabs-nav') }));
await shot('05-payment-protocol', `/channel/channels/${PAYMENT_ID}?tab=protocol`, async () => ({ rows: await page.locator('tbody tr').count() }));
await shot('06-payment-verify', `/channel/channels/${PAYMENT_ID}?tab=verify`, async () => ({ rows: await page.locator('tbody tr').count() }));
await shot('07-service-overview', `/channel/channels/${SERVICE_ID}?tab=overview`, async () => ({ tabs: await text('.ant-tabs-nav') }));
await shot('08-service-credential', `/channel/channels/${SERVICE_ID}?tab=credential`, async () => ({ body: await text('main') }));
await shot('09-service-grants-matrix', `/channel/channels/${SERVICE_ID}?tab=grants`, async () => ({ body: await text('main') }));
await shot('10-service-grants-list', `/channel/channels/${SERVICE_ID}?tab=grants&view=list`, async () => ({ rows: await text('tbody tr') }));

// 出站地址编辑：只打开弹窗截图，然后取消（不提交）
await page.goto(`${OPERATIONS}/channel/channels/${SERVICE_ID}?tab=overview`);
await settle();
current = '11-endpoints-edit';
const button = page.getByRole('button', { name: /编辑出站地址/ });
if (await button.count()) {
  await button.first().click();
  await page.locator('.ant-modal').first().waitFor({ timeout: 10000 }).catch(() => {});
  await shot('11-endpoints-edit', null, async () => ({ modal: await text('.ant-modal') }));
  const cancel = page.locator('.ant-modal').getByRole('button', { name: /取\s*消/ });
  if (await cancel.count()) await cancel.first().click();
} else {
  await shot('11-endpoints-edit', null, async () => ({ error: '未找到“编辑出站地址”按钮' }));
}

await shot('12-legacy-redirect', '/external-api/integrations/PAYERMAX_MOCK', async () => ({ heading: await text('h1, h2, h3, .ant-page-header-heading-title') }));
await page.waitForTimeout(1500);
log.legacyFinalUrl = page.url().replace(OPERATIONS, '');

await browser.close();
writeFileSync(join(SHOTS, 'browser-log.json'), JSON.stringify(log, null, 1) + '\n');
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors, legacyFinalUrl: log.legacyFinalUrl }));
