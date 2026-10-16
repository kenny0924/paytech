// 租户与工作区浏览器端到端验收（本机全链路，无头 Chromium，2026-10-16）。
//
// 前置：一个已验证、入驻已批准且工作区已开通的客户账号（ACCOUNT_FILE 指向 JSON：email、password、applicationId、memberId）。
// 流程：登录 → 开始页“我的工作区”（桌面）→ 申请结果页“进入工作区”入口 → 进入工作区 → 首页内容与提示 → 390px 菜单
//   → 切换工作区（回开始页，当前工作区标记）→ 再进入 → 返回账号 → 夹具暂停工作区后开始页显示受限原因 → 恢复 → 进入 → 退出。
// 暂停 / 恢复用本机 mor_platform_e2e 夹具（本机运营租户访问路由未启用）。只记方法、路径、状态，不记 Header/Body、密码或令牌。
//
// 用法：ACCOUNT_FILE=<json> SHOTS=<截图目录> [PLAYWRIGHT_CORE=<playwright-core 目录>] node workspace_browser.mjs
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../../../../..');
const CLIENT = 'https://127.0.0.1:18443';
const SHOTS = process.env.SHOTS || join(HERE, 'browser-shots-workspace');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
const account = JSON.parse(readFileSync(process.env.ACCOUNT_FILE, 'utf8'));
mkdirSync(SHOTS, { recursive: true });

const results = [], network = [], consoleErrors = [];
let shotNo = 0;
class Stop extends Error {}

function env(file) {
  const values = {};
  for (const line of readFileSync(join(ROOT, file), 'utf8').split('\n')) {
    const at = line.indexOf('=');
    if (at > 0 && !line.startsWith('#')) values[line.slice(0, at).trim()] = line.slice(at + 1).trim().replace(/^"|"$/g, '');
  }
  return values;
}

/** 本机夹具：按成员找到租户并切换安全状态，返回影响行数。 */
function setSecurity(status) {
  const sql = `UPDATE t_access_tenant t JOIN t_tenant_member m ON m.tenant_id=t.tenant_id SET t.security_status='${status}', `
    + `t.version=t.version+1, t.update_time=UTC_TIMESTAMP(6) WHERE m.member_id=${BigInt(account.memberId)} AND t.tenant_type='CLIENT'; SELECT ROW_COUNT();`;
  return execFileSync('mysql', ['-h127.0.0.1', '-P13306', '-uplatform_app', '-N', '-B', 'mor_platform_e2e', '-e', sql],
    { env: { ...process.env, MYSQL_PWD: env('local-environment/.env.local').MOR_PLATFORM_DB_PASSWORD } }).toString().trim();
}

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

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  page.on('dialog', d => d.accept());
  watch(page);
  const workspaceRow = () => page.locator('section[aria-labelledby="workspace-heading"]');
  try {
    await step(page, '登录并看到我的工作区', async () => {
      await page.goto(`${CLIENT}/login`);
      await page.getByLabel('邮箱', { exact: true }).fill(account.email);
      await page.getByLabel('密码', { exact: true }).fill(account.password);
      await page.getByRole('button', { name: '登录' }).click();
      await page.waitForURL(/\/onboarding/, { timeout: 20000 });
      await page.getByRole('heading', { name: '我的工作区' }).waitFor({ timeout: 20000 });
      await workspaceRow().getByRole('button', { name: '进入工作区' }).waitFor({ timeout: 20000 });
    }, async () => `区域文本=${(await workspaceRow().innerText()).replace(/\s+/g, ' ').slice(0, 160)}`);

    await step(page, '申请结果页显示进入工作区', async () => {
      await page.goto(`${CLIENT}/onboarding/applications/${account.applicationId}`);
      await page.getByText('客户工作区已开通').waitFor({ timeout: 20000 });
      await page.getByRole('button', { name: '进入工作区' }).first().waitFor({ timeout: 10000 });
    }, 'banner=客户工作区已开通，可以进入');

    await step(page, '从结果页进入工作区', async () => {
      await page.getByRole('button', { name: '进入工作区' }).first().click();
      await page.waitForURL(/\/workspace/, { timeout: 20000 });
      await page.getByRole('note').waitFor({ timeout: 20000 });
    }, async () => `url=${page.url()} notice=${await page.getByRole('note').innerText()}`);

    await step(page, '工作区首页内容', async () => {
      const main = await page.locator('.workspace-main').innerText();
      for (const text of ['进入工作区不代表已开通收款', '经营模式', '审核通过']) {
        if (!main.includes(text)) throw new Error(`首页缺少：${text}`);
      }
      for (const text of ['余额', '付款', '邀请']) {
        if (main.includes(text)) throw new Error(`首页不应出现：${text}`);
      }
      for (const name of ['切换工作区', '返回账号', '退出']) await page.getByText(name, { exact: true }).first().waitFor();
    }, async () => (await page.locator('.workspace-main').innerText()).replace(/\s+/g, ' ').slice(0, 200));

    await step(page, '390px 宽度菜单', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.getByRole('button', { name: '菜单' }).click();
      await page.getByRole('link', { name: '工作区首页' }).waitFor();
      await noHorizontalScroll(page);
    }, async () => `overflow=${await noHorizontalScroll(page)}`);
    await page.setViewportSize({ width: 1280, height: 900 });

    await step(page, '切换工作区回到开始页', async () => {
      await page.getByRole('link', { name: '切换工作区' }).click();
      await page.waitForURL(/\/onboarding$/, { timeout: 20000 });
      await workspaceRow().getByText('当前工作区').waitFor({ timeout: 20000 });
    }, async () => `区域文本=${(await workspaceRow().innerText()).replace(/\s+/g, ' ').slice(0, 160)}`);

    await step(page, '开始页再次进入', async () => {
      await workspaceRow().getByRole('button', { name: '进入工作区' }).first().click();
      await page.waitForURL(/\/workspace/, { timeout: 20000 });
      await page.getByRole('note').waitFor({ timeout: 20000 });
    }, () => page.url());

    await step(page, '返回账号', async () => {
      await page.getByRole('button', { name: '返回账号' }).click();
      await page.waitForURL(/\/onboarding/, { timeout: 20000 });
      await page.getByRole('heading', { name: '我的工作区' }).waitFor({ timeout: 20000 });
      const current = await workspaceRow().getByText('当前工作区').count();
      if (current !== 0) throw new Error('返回账号后仍标记当前工作区');
    }, () => page.url());

    await step(page, '工作区受限显示原因', async () => {
      const rows = setSecurity('SUSPENDED');
      if (!rows.endsWith('1')) throw new Error('夹具未更新租户');
      await page.reload();
      await workspaceRow().getByText('工作区暂时不可访问').waitFor({ timeout: 20000 });
      if (await workspaceRow().getByRole('button', { name: '进入工作区' }).count() !== 0) throw new Error('受限工作区仍可进入');
    }, async () => `区域文本=${(await workspaceRow().innerText()).replace(/\s+/g, ' ').slice(0, 160)}`);

    await step(page, '恢复后进入并退出', async () => {
      setSecurity('ACTIVE');
      await page.reload();
      await workspaceRow().getByRole('button', { name: '进入工作区' }).first().click();
      await page.waitForURL(/\/workspace/, { timeout: 20000 });
      await page.getByRole('button', { name: '退出' }).click();
      await page.waitForURL(/\/login/, { timeout: 20000 });
    }, () => page.url());
  } catch (failure) {
    if (!(failure instanceof Stop)) {
      results.push(['FAIL', '未预期异常', String(failure.message || failure).split('\n')[0].slice(0, 300), '']);
      console.log('FAIL 未预期异常 | ' + results.at(-1)[2]);
    }
  } finally {
    await browser.close();
    console.log('\n==== 汇总 ====');
    console.log(`${results.filter(r => r[0] === 'PASS').length}/${results.length} PASS`);
    for (const [verdict, name] of results) console.log(`${verdict.padEnd(4)} ${name}`);
    console.log(`HTTP>=400: ${network.length ? network.join(' ; ') : '无'}`);
    console.log(`控制台错误: ${consoleErrors.length ? consoleErrors.join(' ; ') : '无'}`);
  }
}

main();
