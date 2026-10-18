// 接入自助（API 凭证与 Webhook）浏览器端到端验收（本机全链路或 mor-test，无头 Chromium，2026-10-16；2026-10-17 增加 E2E_TARGET=test）。
//
// 前置：一个已批准且工作区可访问的客户账号（ACCOUNT_FILE 指向 JSON：email、password、clientId），本机 Scope 目录至少一项
// （developer_flow.py 的夹具会写入 orders.read）。
// 流程：登录 → 进入工作区 → 侧边栏“开发者”分组 → API 凭证：新建 → 一次性密钥弹窗（未勾选不能关闭、Esc 无效）→ 列表
//   → 轮换 → 撤销 → Webhook：新建端点 → 一次性密钥弹窗 → 发送测试事件 → 提交一条 order.paid → 投递列表 / 详情 → 重新发送
//   → 390px 两页无横向溢出。Web 只允许 HTTPS 地址，端点指向本机未监听的 HTTPS 端口，投递结果为连接失败（成功送达由 developer_flow.py 覆盖）。
// E2E_TARGET=test：入口 https://localhost:28443；test 只允许可解析到公网的 HTTPS 端点，且不向外网真实推送（2026-10-16 决定），
//   端点用 https://example.com/code-mor-acceptance 新建后立即停用；测试事件、提交 order.paid、投递详情三步记为 SKIP（本机已覆盖）。
//   前置账号可用 team_browser_accounts.py 的 owner（email、password、clientId）。
// 只记方法、路径、状态，不记 Header/Body、密码、令牌或密钥。
//
// 用法：[E2E_TARGET=test] ACCOUNT_FILE=<json> SHOTS=<截图目录> [PLAYWRIGHT_CORE=<playwright-core 目录>] node developer_browser.mjs
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const TARGET = process.env.E2E_TARGET || 'local';
if (!['local', 'test'].includes(TARGET)) throw new Error('E2E_TARGET 只支持 local 或 test');
const TEST = TARGET === 'test';
const CLIENT = TEST ? 'https://localhost:28443' : 'https://127.0.0.1:18443';
const PLATFORM = 'http://127.0.0.1:18106/platform-support';
const SHOTS = process.env.SHOTS || join(HERE, 'browser-shots-developer');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
const account = JSON.parse(readFileSync(process.env.ACCOUNT_FILE, 'utf8'));
mkdirSync(SHOTS, { recursive: true });

const results = [], network = [], consoleErrors = [];
let shotNo = 0;
class Stop extends Error {}

async function shot(page, name) {
  const file = join(SHOTS, String(++shotNo).padStart(2, '0') + '-' + name + '.png');
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function step(page, name, action, evidence) {
  try {
    await action();
  } catch (failure) {
    const file = await shot(page, name.replace(/[^\w一-龥-]+/g, '_'));
    results.push(['FAIL', name, String(failure.message || failure).split('\n')[0].slice(0, 300), file]);
    console.log(`FAIL ${name} | ${results.at(-1)[2]} | ${file}`);
    throw new Stop(name);
  }
  const file = await shot(page, name.replace(/[^\w一-龥-]+/g, '_'));
  const text = typeof evidence === 'function' ? await evidence() : evidence || '';
  results.push(['PASS', name, text, file]);
  console.log(`PASS ${name} | ${text} | ${file}`);
}

function watch(page) {
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300)); });
  page.on('pageerror', e => consoleErrors.push('pageerror: ' + String(e).slice(0, 300)));
  page.on('response', r => {
    if (r.status() >= 400) { const u = new URL(r.url()); network.push(`${r.request().method()} ${u.pathname} -> ${r.status()}`); }
  });
}

async function noHorizontalScroll(page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  if (overflow > 1) throw new Error(`横向溢出 ${overflow}px`);
  return overflow;
}

/** 一次性密钥弹窗：未勾选时关闭按钮禁用且 Esc 无效；勾选后关闭；密钥不进入浏览器存储。返回密钥长度。 */
async function acknowledgeSecret(page) {
  const dialog = page.getByRole('dialog').filter({ has: page.getByText('我已安全保存') });
  await dialog.waitFor({ timeout: 20000 });
  const secret = (await dialog.getByLabel('密钥').innerText()).trim();
  if (!(await dialog.getByRole('button', { name: '关闭' }).isDisabled())) throw new Error('未勾选时关闭按钮可用');
  await page.keyboard.press('Escape');
  if (!(await dialog.isVisible())) throw new Error('未勾选时 Esc 关闭了弹窗');
  await dialog.getByRole('checkbox').check();
  await dialog.getByRole('button', { name: '关闭' }).click();
  await dialog.waitFor({ state: 'detached', timeout: 10000 });
  const stored = await page.evaluate(s => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }), secret);
  if (stored.includes(secret)) throw new Error('密钥进入了浏览器存储');
  if ((await page.content()).includes(secret)) throw new Error('关闭后页面仍包含密钥');
  return secret.length;
}

function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}';
  return JSON.stringify(value);
}

/** 模拟 Commerce 经私网提交 order.paid（本机 webhook.http.callers 已登记 commerce-service），返回 HTTP 状态。 */
async function submitOrderPaid() {
  const eventId = 'evt_browser_' + Date.now();
  const object = { orderNo: 'ord_' + eventId, orderStatus: 'PAID', totalAmount: '42.90', version: 5 };
  const envelope = { eventId, eventType: 'order.paid', schemaVersion: '1', createdAt: '2026-10-16T00:00:00Z', data: { object } };
  const sha = s => createHash('sha256').update(s).digest('hex');
  const response = await fetch(PLATFORM + '/webhooks/v1/events', {
    method: 'POST', headers: { 'X-Caller-Service': 'commerce-service', 'Content-Type': 'application/json' },
    body: JSON.stringify({ clientId: String(account.clientId), sourceVersion: 5, sourcePayloadHash: sha(canonical(object)),
      payloadHash: sha(canonical(envelope)), envelope }),
  });
  return response.status;
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1280, height: 900 } });
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: CLIENT });
  const page = await context.newPage();
  page.on('dialog', d => d.accept());
  watch(page);
  const workspaceRow = () => page.locator('section[aria-labelledby="workspace-heading"]');
  try {
    await step(page, '登录并进入工作区', async () => {
      await page.goto(`${CLIENT}/login`);
      await page.getByLabel('邮箱', { exact: true }).fill(account.email);
      await page.getByLabel('密码', { exact: true }).fill(account.password);
      await page.getByRole('button', { name: '登录' }).click();
      await page.waitForURL(/\/onboarding/, { timeout: 20000 });
      await workspaceRow().getByRole('button', { name: '进入工作区' }).first().click();
      await page.waitForURL(/\/workspace/, { timeout: 20000 });
      await page.getByRole('note').waitFor({ timeout: 20000 });
    }, () => page.url());

    await step(page, '侧边栏开发者分组', async () => {
      await page.getByText('开发者', { exact: true }).waitFor({ timeout: 20000 });
      await page.getByRole('link', { name: 'API 凭证' }).waitFor();
      await page.getByRole('link', { name: 'Webhook' }).waitFor();
    }, 'API 凭证、Webhook 可见');

    let secretLength = 0;
    await step(page, '新建凭证并显示一次性密钥', async () => {
      await page.getByRole('link', { name: 'API 凭证' }).click();
      await page.waitForURL(/api-keys/, { timeout: 20000 });
      await page.getByRole('button', { name: '新建凭证' }).click();
      const form = page.getByRole('dialog', { name: '新建凭证' });
      await form.getByLabel('名称').fill('Browser server');
      const boxes = form.getByRole('checkbox');
      if (await boxes.count() === 0) throw new Error('没有可选的权限范围');
      await boxes.first().check();
      await form.getByRole('button', { name: '创建' }).click();
      secretLength = await acknowledgeSecret(page);
    }, () => `secretLength=${secretLength}，未勾选不能关闭、Esc 无效、关闭后页面与存储均无密钥`);

    await step(page, '凭证列表显示新凭证', async () => {
      await page.locator('tbody tr', { hasText: 'Browser server' }).first().waitFor({ timeout: 20000 });
    }, async () => (await page.locator('tbody tr', { hasText: 'Browser server' }).first().innerText()).replace(/\s+/g, ' '));

    await step(page, '轮换凭证', async () => {
      await page.locator('tbody tr', { hasText: 'Browser server' }).first().getByRole('button', { name: '轮换' }).click();
      const confirm = page.getByRole('dialog', { name: '轮换凭证' });
      await confirm.getByLabel('旧密钥失效时间').selectOption({ index: 0 });
      await confirm.getByRole('button', { name: '生成新密钥' }).click();
      await acknowledgeSecret(page);
      await page.getByText('已撤销').first().waitFor({ timeout: 20000 });
    }, '旧密钥立即失效，列表出现已撤销行');

    await step(page, '撤销凭证', async () => {
      const row = page.locator('tbody tr', { hasText: 'Browser server' }).filter({ has: page.getByRole('button', { name: '撤销' }) }).first();
      await row.getByRole('button', { name: '撤销' }).click();
      await page.getByRole('dialog', { name: '撤销凭证' }).getByRole('button', { name: '撤销' }).click();
      await page.getByRole('dialog', { name: '撤销凭证' }).waitFor({ state: 'detached', timeout: 20000 });
      const active = await page.locator('tbody tr', { hasText: 'Browser server' }).filter({ has: page.getByRole('button', { name: '撤销' }) }).count();
      if (active !== 0) throw new Error(`仍有 ${active} 个可撤销的 Browser server 凭证`);
    }, '同名凭证已全部撤销');

    await step(page, 'API 凭证页 390px', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await noHorizontalScroll(page);
    }, async () => `overflow=${await noHorizontalScroll(page)}`);
    await page.setViewportSize({ width: 1280, height: 900 });

    const nonFatal = async (...args) => { try { await step(...args); } catch (e) { if (!(e instanceof Stop)) throw e;
      await page.keyboard.press('Escape'); const cancel = page.getByRole('dialog').getByRole('button', { name: '取消' });
      if (await cancel.count()) await cancel.first().click(); } };
    // 2026-10-16 已拆分新建请求体，新建端点只发地址和订阅事件。
    await step(page, '新建 Webhook 端点并显示一次性密钥', async () => {
      await page.getByRole('link', { name: 'Webhook' }).click();
      await page.waitForURL(/webhooks/, { timeout: 20000 });
      await page.getByRole('button', { name: '新建端点' }).click();
      const form = page.getByRole('dialog', { name: '新建端点' });
      await form.getByLabel('接收地址（HTTPS）').fill(TEST ? 'https://example.com/code-mor-acceptance' : 'https://127.0.0.1:1/browser-hook');
      await form.getByRole('checkbox', { name: /订单已支付/ }).check();
      await form.getByRole('button', { name: '保存' }).click();
      await acknowledgeSecret(page);
      await page.locator('tbody tr', { hasText: TEST ? 'example.com/code-mor-acceptance' : '127.0.0.1:1/browser-hook' }).first().waitFor({ timeout: 20000 });
    }, '端点出现在列表');
    const HOOK = process.env.PRECREATED_URL || (TEST ? 'example.com/code-mor-acceptance' : '127.0.0.1:1/browser-hook');
    await page.reload();
    await page.locator('tbody tr', { hasText: HOOK.replace('https://', '') }).first().waitFor({ timeout: 20000 });

    if (TEST) {
      await step(page, '停用新建的端点（test 不向外网推送）', async () => {
        await page.locator('tbody tr', { hasText: HOOK.replace('https://', '') }).first().getByRole('button', { name: '详情' }).click();
        const drawer = page.getByRole('dialog', { name: '端点详情' });
        await drawer.getByRole('button', { name: '停用', exact: true }).click();
        await page.getByRole('dialog', { name: '停用端点' }).getByRole('button', { name: '停用', exact: true }).click();
        await drawer.getByRole('button', { name: '启用', exact: true }).waitFor({ timeout: 20000 });
        await drawer.getByRole('button', { name: '关闭' }).click();
      }, '端点已停用');
      for (const name of ['发送测试事件', '投递列表与详情', '等待重试的投递不显示重新发送']) {
        results.push(['SKIP', name, 'test 不向外网推送，本机已覆盖', '']);
        console.log(`SKIP ${name} | test 不向外网推送，本机已覆盖`);
      }
    } else {
      await step(page, '发送测试事件', async () => {
        await page.locator('tbody tr', { hasText: HOOK.replace('https://', '') }).first().getByRole('button', { name: '详情' }).click();
        const drawer = page.getByRole('dialog', { name: '端点详情' });
        await drawer.getByRole('button', { name: '发送测试事件' }).click();
        await page.getByRole('status').filter({ hasText: /测试/ }).first().waitFor({ timeout: 30000 });
      }, async () => (await page.getByRole('status').filter({ hasText: /测试/ }).first().innerText()).replace(/\s+/g, ' '));
      await page.getByRole('dialog', { name: '端点详情' }).getByRole('button', { name: '关闭' }).click();

      let submitted = 0;
      await step(page, '投递列表与详情', async () => {
        submitted = await submitOrderPaid();
        if (submitted !== 204) throw new Error(`提交 order.paid 返回 ${submitted}`);
        await page.waitForTimeout(2000);
        await page.getByRole('button', { name: '查询' }).click();
        const row = page.locator('tbody tr', { hasText: '订单已支付' }).first();
        await row.waitFor({ timeout: 20000 });
        await row.getByRole('button', { name: '详情' }).click();
        await page.getByRole('dialog', { name: '投递详情' }).getByText(/第 1 次/).waitFor({ timeout: 20000 });
      }, async () => (await page.getByRole('dialog', { name: '投递详情' }).innerText()).replace(/\s+/g, ' ').slice(0, 220));

      // 投递指向本机未监听端口，处于“等待重试”：Platform 只允许已结束的投递重发，页面不应提供重发按钮。
      await step(page, '等待重试的投递不显示重新发送', async () => {
        const count = await page.getByRole('dialog', { name: '投递详情' }).getByRole('button', { name: '重新发送' }).count();
        if (count !== 0) throw new Error('等待重试的投递仍显示重新发送');
      }, '未结束的投递没有重发入口');
      await page.getByRole('dialog', { name: '投递详情' }).getByRole('button', { name: '关闭' }).click();
    }

    // 已结束投递的页面重发未在浏览器覆盖：Web 只允许 HTTPS 地址，本机无可信 HTTPS 接收端，投递停在“等待重试”；
    // 本机未运行 XXL-Job，到点重试不会执行，停用端点后投递也不会转为“已取消”。重发本身由 developer_flow.py（HTTP 接收端、已成功投递）覆盖。

    await step(page, 'Webhook 页 390px', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await noHorizontalScroll(page);
    }, async () => `overflow=${await noHorizontalScroll(page)}`);
  } catch (failure) {
    if (!(failure instanceof Stop)) {
      results.push(['FAIL', '未预期异常', String(failure.message || failure).split('\n')[0].slice(0, 300), '']);
      console.log('FAIL 未预期异常 | ' + results.at(-1)[2]);
    }
  } finally {
    await browser.close();
    console.log('\n==== 汇总 ====');
    const counted = results.filter(r => r[0] !== 'SKIP');
    console.log(`${counted.filter(r => r[0] === 'PASS').length}/${counted.length} PASS` + (counted.length < results.length ? `（另有 ${results.length - counted.length} 项 SKIP）` : ''));
    for (const [verdict, name] of results) console.log(`${verdict.padEnd(4)} ${name}`);
    console.log(`HTTP>=400: ${network.length ? network.join(' ; ') : '无'}`);
    console.log(`控制台错误: ${consoleErrors.length ? consoleErrors.join(' ; ') : '无'}`);
  }
}

main();
