// 卡 BIN 库阶段 5（BIN AC-12）：本机运营控制台页面截图（无头 Chromium；只读浏览，弹窗只打开后取消，不在页面上写入）。
//
// 登录 https://127.0.0.1:18444（Nginx → ops-web vite（集成目录）/ Gateway → ops-bff → Card），打开 /card-bin-library：
// 主页面（摘要、版本表、选中版本详情、激活历史）、BIN 查询（8 位命中、未识别、非数字即时提示）、状态筛选、各状态版本详情、
// 导入弹窗（打开后取消）、激活 / 回切确认弹窗（打开后取消）。记录控制台 error / warning 与 HTTP >= 400 的请求（方法、路径、状态）。
// 用法：node stage5_bin_screens.mjs [前缀]；PLAYWRIGHT_CORE 默认 ~/.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPERATIONS = 'https://127.0.0.1:18444';
const PREFIX = process.argv[2] || 'run';
const SHOTS = join(HERE, 'stage5-bin-screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);

function secrets() {
  const values = {};
  for (const line of readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8').split('\n')) {
    const at = line.indexOf('=');
    if (at > 0 && !line.startsWith('#')) values[line.slice(0, at).trim()] = line.slice(at + 1).trim().replace(/^"|"$/g, '');
  }
  return values;
}

mkdirSync(SHOTS, { recursive: true });
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1600, height: 1000 }, locale: 'zh-CN' });
const page = await context.newPage();
const log = { pages: [], console: [], httpErrors: [] };
let current = 'login';
page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) log.console.push({ page: current, type: m.type(), text: m.text().slice(0, 300) }); });
page.on('response', (r) => {
  if (r.status() >= 400) { const u = new URL(r.url()); log.httpErrors.push({ page: current, method: r.request().method(), path: u.pathname, status: r.status() }); }
});
const settle = async () => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(900); };
const text = async (sel) => (await page.locator(sel).allInnerTexts()).join(' | ').replace(/\s+/g, ' ').slice(0, 600);
async function shot(name, check) {
  current = name;
  await settle();
  const entry = { name: `${PREFIX}-${name}`, url: page.url().replace(OPERATIONS, '') };
  try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
  await page.screenshot({ path: join(SHOTS, `${PREFIX}-${name}.png`), fullPage: true });
  log.pages.push(entry);
  console.log(JSON.stringify(entry));
}

const s = secrets();
await page.goto(OPERATIONS + '/');
await page.getByLabel('Email / 初始化账号').fill(s.E2E_ADMIN_EMAIL);
await page.getByLabel('密码').fill(s.E2E_ADMIN_PASSWORD);
await page.getByRole('button', { name: /^登\s*录$/ }).click();
await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
await settle();

await page.goto(OPERATIONS + '/card-bin-library');
await shot('01-main', async () => ({ menu: (await text('.ant-layout-sider, nav')).includes('卡 BIN 库'), summary: await text('.bin-summary, .dq-summary, header'),
  rows: await text('tbody tr') }));

const input = page.locator('.bin-lookup-input');
await input.fill('40128888');
await page.getByRole('button', { name: /^查\s*询$/ }).click();
await shot('02-lookup-8digit', async () => ({ result: await text('.bin-lookup-result, .bin-lookup, .ant-descriptions') }));
await input.fill('60110009');
await page.getByRole('button', { name: /^查\s*询$/ }).click();
await shot('03-lookup-unrecognized', async () => ({ result: await text('.bin-lookup-result, .bin-lookup, .ant-descriptions') }));
await input.fill('41a1');
await shot('04-lookup-invalid', async () => ({ queryDisabled: await page.getByRole('button', { name: /^查\s*询$/ }).isDisabled(),
  hint: await text('.ant-form-item-explain, .bin-lookup-issue, .bin-lookup') }));
await input.fill('');

// 各状态版本：逐行点选后截图详情
const rows = page.locator('.ant-table').first().locator('tr.ant-table-row');
const count = await rows.count();
for (let i = 0; i < Math.min(count, 6); i++) {
  const row = rows.nth(i);
  const label = (await row.innerText()).replace(/\s+/g, ' ').slice(0, 80);
  await row.click({ timeout: 5000 }).catch(() => {});
  await shot(`05-detail-row${i + 1}`, async () => ({ row: label, detail: await text('.dq-detail, .bin-detail') }));
}

// 导入弹窗：打开后取消
const importButton = page.getByRole('button', { name: /导入新版本/ });
if (await importButton.count()) {
  await importButton.first().click();
  await page.locator('.ant-modal').first().waitFor({ timeout: 10000 }).catch(() => {});
  await shot('06-import-dialog', async () => ({ modal: await text('.ant-modal') }));
  await page.locator('.ant-modal').getByRole('button', { name: /取\s*消/ }).first().click().catch(() => page.keyboard.press('Escape'));
}

// 激活 / 回切确认：找第一个“激活”或“回切”按钮，打开后取消
for (const name of ['激活', '回切']) {
  const button = page.locator('tbody').getByRole('button', { name: new RegExp(`^${name[0]}\\s*${name[1]}$`) });
  if (await button.count()) {
    await button.first().click();
    await page.locator('.ant-modal').first().waitFor({ timeout: 10000 }).catch(() => {});
    await shot(`07-confirm-${name === '激活' ? 'activate' : 'rollback'}`, async () => ({ modal: await text('.ant-modal') }));
    await page.locator('.ant-modal').getByRole('button', { name: /取\s*消/ }).first().click().catch(() => page.keyboard.press('Escape'));
    await settle();
  }
}

// 状态筛选
for (const [label, key] of [['校验失败', 'invalid'], ['生效中', 'active']]) {
  const option = page.locator('.ant-segmented').getByText(label, { exact: true });
  if (await option.count()) { await option.first().click(); await shot(`08-filter-${key}`, async () => ({ rows: await text('tbody tr') })); }
}

await browser.close();
writeFileSync(join(SHOTS, `${PREFIX}-browser-log.json`), JSON.stringify(log, null, 1) + '\n');
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors }));
