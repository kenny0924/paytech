// 团队成员与邀请浏览器端到端验收（本机全链路或 mor-test，无头 Chromium，2026-10-17）。
//
// 前置：team_browser_accounts.py 写出的 ACCOUNT_FILE（owner：已批准且工作区可访问；invitee：已验证邮箱、未加入）。
// 流程：所有者登录进入工作区 → 团队页（成员、邀请成员）→ 邀请弹窗发出邀请 → 邀请区出现待接受邀请 → 390px 无横向溢出
//   → 受邀人未登录打开邀请链接（预览）→ 登录后自动回到接受页（详情含工作区名称）→ 接受 → 我的工作区出现并进入
//   → 开发者看到团队但没有邀请区与操作 → 所有者行内改为只读成员 → 二次确认移除成员。
// 邀请链接由 team_flow.mint_invitation_link 按邀请 ID 在 Platform 库铸造（本机与 test 都收不到认证邮件）。
// 受邀人未登录打开链接时，Web 恢复会话的刷新请求返回 401 属预期（尚无 Refresh Cookie）。
// 只记方法、路径、状态，不记 Header/Body、密码、令牌或链接 Secret。
//
// 用法：[E2E_TARGET=test] ACCOUNT_FILE=<json> SHOTS=<截图目录> [PLAYWRIGHT_CORE=<playwright-core 目录>] node team_browser.mjs
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const TARGET = process.env.E2E_TARGET || 'local';
if (!['local', 'test'].includes(TARGET)) throw new Error('E2E_TARGET 只支持 local 或 test');
const CLIENT = TARGET === 'test' ? 'https://localhost:28443' : 'https://127.0.0.1:18443';
const SHOTS = process.env.SHOTS || join(HERE, 'browser-shots-team');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
const { owner, invitee } = JSON.parse(readFileSync(process.env.ACCOUNT_FILE, 'utf8'));
mkdirSync(SHOTS, { recursive: true });

const results = [], network = [], consoleErrors = [];
let shotNo = 0;
class Stop extends Error {}

function mintLink(invitationId) {
  const code = 'import sys, json, team_flow; print(json.dumps(team_flow.mint_invitation_link(sys.argv[1])))';
  const out = execFileSync('python3', ['-c', code, String(invitationId)], { cwd: HERE, env: process.env }).toString().trim();
  const [challengeNo, secret] = JSON.parse(out.split('\n').at(-1));
  if (!challengeNo) throw new Error('未找到待接受邀请的挑战');
  return { challengeNo, secret };
}

async function shot(page, name) {
  const file = join(SHOTS, String(++shotNo).padStart(2, '0') + '-' + name.replace(/[^\w一-龥-]+/g, '_') + '.png');
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function step(page, name, action, evidence) {
  try {
    await action();
  } catch (failure) {
    const file = await shot(page, name);
    results.push(['FAIL', name, String(failure.message || failure).split('\n')[0].slice(0, 300), file]);
    console.log(`FAIL ${name} | ${results.at(-1)[2]} | ${file}`);
    throw new Stop(name);
  }
  const file = await shot(page, name);
  const text = typeof evidence === 'function' ? await evidence() : evidence || '';
  results.push(['PASS', name, text, file]);
  console.log(`PASS ${name} | ${text} | ${file}`);
}

function watch(page, who) {
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(`${who}: ${m.text().slice(0, 300)}`); });
  page.on('pageerror', e => consoleErrors.push(`${who} pageerror: ` + String(e).slice(0, 300)));
  page.on('response', r => {
    if (r.status() >= 400) { const u = new URL(r.url()); network.push(`${who} ${r.request().method()} ${u.pathname} -> ${r.status()}`); }
  });
}

async function noHorizontalScroll(page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  if (overflow > 1) throw new Error(`横向溢出 ${overflow}px`);
  return overflow;
}

async function login(page, account, expect = /\/onboarding/) {
  await page.getByLabel('邮箱', { exact: true }).fill(account.email);
  await page.getByLabel('密码', { exact: true }).fill(account.password);
  await page.getByRole('button', { name: '登录' }).click();
  await page.waitForURL(expect, { timeout: 20000 });
}

async function enterWorkspace(page) {
  const section = page.locator('section[aria-labelledby="workspace-heading"]');
  await section.getByRole('button', { name: '进入工作区' }).first().click();
  await page.waitForURL(/\/workspace/, { timeout: 20000 });
  await page.getByRole('note').waitFor({ timeout: 20000 });
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const options = { ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1280, height: 900 } };
  const ownerContext = await browser.newContext(options), inviteeContext = await browser.newContext(options);
  const page = await ownerContext.newPage(), guest = await inviteeContext.newPage();
  page.on('dialog', d => d.accept()); guest.on('dialog', d => d.accept());
  watch(page, 'owner'); watch(guest, 'invitee');
  const members = () => page.getByRole('list', { name: '成员列表' });
  const inviteeMask = invitee.email[0] + '***@' + invitee.email.split('@')[1];
  let invitationId;
  try {
    await step(page, '所有者进入团队页', async () => {
      await page.goto(`${CLIENT}/login`);
      await login(page, owner);
      await enterWorkspace(page);
      await page.getByRole('link', { name: '团队' }).click();
      await page.waitForURL(/\/workspace\/team/, { timeout: 20000 });
      await members().getByText('· 你').waitFor({ timeout: 20000 });
      await page.getByRole('button', { name: '邀请成员' }).waitFor();
    }, async () => `成员=${(await members().innerText()).replace(/\s+/g, ' ').slice(0, 120)}`);

    await step(page, '邀请弹窗发出邀请', async () => {
      await page.getByRole('button', { name: '邀请成员' }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByLabel('邮箱').fill(invitee.email);
      await dialog.getByRole('radio', { name: /开发者/ }).check();
      const reply = page.waitForResponse(r => r.url().endsWith('/api/client/team/v1/invitations') && r.request().method() === 'POST');
      await dialog.getByRole('button', { name: '发送邀请' }).click();
      const response = await reply;
      if (response.status() !== 201) throw new Error(`发起邀请 HTTP ${response.status()}`);
      invitationId = (await response.json()).invitationId;
      await page.getByRole('status').filter({ hasText: /邀请/ }).first().waitFor({ timeout: 10000 });
      await page.getByRole('list', { name: '邀请列表' }).getByText(inviteeMask, { exact: true }).first().waitFor({ timeout: 10000 });
    }, async () => `提示=${(await page.locator('.notice').first().innerText()).slice(0, 80)} invitationId=${invitationId}`);

    await step(page, '390px 团队页', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.getByRole('list', { name: '邀请列表' }).getByRole('button', { name: /更多操作/ }).first().waitFor();
      await noHorizontalScroll(page);
    }, async () => `overflow=${await noHorizontalScroll(page)}`);
    await page.setViewportSize({ width: 1280, height: 900 });

    const proof = mintLink(invitationId);
    await step(guest, '未登录打开邀请链接', async () => {
      await guest.goto(`${CLIENT}/invitations/accept#challengeNo=${proof.challengeNo}&secret=${proof.secret}`);
      await guest.getByRole('heading', { name: '加入 Client 工作区' }).waitFor({ timeout: 20000 });
      await guest.getByText('开发者').waitFor();
      await guest.getByText(inviteeMask, { exact: true }).waitFor();
      if (guest.url().includes('secret=')) throw new Error('地址栏仍保留链接 Secret');
    }, async () => `url=${new URL(guest.url()).pathname}`);

    await step(guest, '登录后回到接受页并显示工作区', async () => {
      await guest.getByRole('link', { name: '登录' }).click();
      await guest.waitForURL(/\/login/, { timeout: 10000 });
      await login(guest, invitee, /\/invitations\/accept/);
      await guest.getByRole('heading', { name: '接受邀请' }).waitFor({ timeout: 20000 });
      const text = await guest.locator('dl.account').innerText();
      if (/名称暂不可用/.test(text)) throw new Error('工作区名称未显示');
    }, async () => (await guest.locator('dl.account').innerText()).replace(/\s+/g, ' ').slice(0, 160));

    await step(guest, '接受后我的工作区出现并进入', async () => {
      await guest.getByRole('button', { name: '接受邀请' }).click();
      await guest.waitForURL(/\/onboarding/, { timeout: 20000 });
      await enterWorkspace(guest);
    }, () => guest.url());

    await step(guest, '开发者只读团队页', async () => {
      await guest.getByRole('link', { name: '团队' }).click();
      await guest.waitForURL(/\/workspace\/team/, { timeout: 20000 });
      await guest.getByRole('list', { name: '成员列表' }).getByText('· 你').waitFor({ timeout: 20000 });
      if (await guest.getByRole('button', { name: '邀请成员' }).count()) throw new Error('开发者看到邀请成员');
      if (await guest.getByRole('list', { name: '邀请列表' }).count()) throw new Error('开发者看到邀请区');
      if (await guest.getByRole('button', { name: /更多操作/ }).count()) throw new Error('开发者看到行操作');
      await guest.getByRole('link', { name: 'API 凭证' }).waitFor();
    }, async () => (await guest.getByRole('list', { name: '成员列表' }).innerText()).replace(/\s+/g, ' ').slice(0, 160));

    await step(page, '所有者行内改为只读成员', async () => {
      await page.reload();
      const select = page.getByRole('combobox', { name: `更改 ${inviteeMask} 的角色` });
      await select.waitFor({ timeout: 20000 });
      await select.selectOption('TENANT_VIEWER');
      await members().locator('li', { hasText: inviteeMask }).locator('.team-main small').getByText('只读成员').waitFor({ timeout: 10000 });
    }, async () => (await page.getByRole('status').first().innerText()).slice(0, 80));

    await step(page, '二次确认移除成员', async () => {
      await members().locator('li', { hasText: inviteeMask }).getByRole('button', { name: '移除' }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByRole('heading', { name: '移除成员' }).waitFor();
      await dialog.getByRole('button', { name: '移除' }).click();
      await members().getByText(inviteeMask).waitFor({ state: 'detached', timeout: 10000 });
    }, async () => `成员=${(await members().innerText()).replace(/\s+/g, ' ').slice(0, 120)}`);
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
