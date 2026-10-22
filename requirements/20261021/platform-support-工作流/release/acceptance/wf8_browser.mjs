// mor-test 写数据冒烟浏览器部分：我的待办提交表单、审批同意、实例详情（流程图与活动）。截图 screenshots/mortest-*.png。
// 管理员取 test-environment/private/access.md，口令不打印。用法：node wf8_browser.mjs
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../../../../..');
const OPS = 'http://localhost:28080';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const access = readFileSync(join(ROOT, 'test-environment/private/access.md'), 'utf8');
const email = access.match(/账号：(\S+)/)[1], password = access.match(/密码：(\S+)/)[1];
const state = JSON.parse(readFileSync(join(HERE, 'wf8-state.json'), 'utf8'));
const shot = (p, n) => p.screenshot({ path: join(HERE, 'screenshots', `mortest-${n}.png`) });
const log = (...a) => console.log(...a);
const network = [], errors = [];
const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ locale: 'zh-CN', viewport: { width: 1600, height: 1000 } });
const p = await ctx.newPage();
p.on('pageerror', e => errors.push(String(e).slice(0, 200)));
p.on('response', r => { if (r.status() >= 400) network.push(`${r.request().method()} ${new URL(r.url()).pathname} -> ${r.status()}`); });
const settle = async () => { await p.waitForLoadState('networkidle'); await p.waitForTimeout(1000); };
async function step(name, fn) { try { log('PASS', name, '|', (await fn()) ?? ''); } catch (e) { await shot(p, 'fail-' + name.replace(/\W+/g, '_')); log('FAIL', name, '|', String(e.message || e).split('\n')[0].slice(0, 300)); } }
await p.goto(OPS + '/');
await p.getByLabel('Email / 初始化账号').fill(email);
await p.getByLabel('密码').fill(password);
await p.getByRole('button', { name: /^登\s*录$/ }).click();
await settle();
const title = 'WF-SMOKE-20261022 冒烟实例';
await step('我的待办提交表单', async () => {
  await p.goto(`${OPS}/workflow/my-tasks`); await settle();
  await p.locator('.wf-task-item', { hasText: title }).first().click(); await settle();
  const action = p.locator('.wf-action.is-expanded').first();
  await action.locator('.ant-form-item', { hasText: '说明' }).locator('input').first().fill('冒烟提交');
  await action.locator('.ant-form-item', { hasText: '结论' }).getByText('通过', { exact: true }).first().click().catch(async () => {
    await action.locator('.ant-form-item', { hasText: '结论' }).locator('.ant-select').click(); await p.getByText('OK · 通过').click(); });
  await shot(p, '01-my-tasks-form');
  const reply = p.waitForResponse(r => r.url().includes('/submission'), { timeout: 15000 });
  await action.getByRole('button', { name: /^提\s*交$/ }).click();
  const res = await reply; const body = await res.json();
  await p.waitForTimeout(1500); await shot(p, '02-my-tasks-submitted');
  return `HTTP ${res.status()} status=${body.status}`;
});
await step('审批同意', async () => {
  await p.goto(`${OPS}/workflow/my-tasks`); await settle();
  await p.locator('.wf-task-item', { hasText: title }).first().click(); await settle();
  await p.locator('body').click({ position: { x: 5, y: 5 } });
  await p.keyboard.press('a');
  await shot(p, '03-my-tasks-approval');
  const reply = p.waitForResponse(r => r.url().includes('/decision'), { timeout: 15000 });
  await p.getByRole('button', { name: /提交决定/ }).click();
  const res = await reply;
  await p.waitForTimeout(1500);
  return `HTTP ${res.status()}`;
});
await step('实例详情流程图', async () => {
  await p.goto(`${OPS}/workflow/instances/${state.instanceId}`); await settle();
  await p.locator('.react-flow__node').first().waitFor({ timeout: 15000 });
  await shot(p, '04-instance-graph');
  return (await p.locator('main').first().innerText()).replace(/\s+/g, ' ').slice(0, 160);
});
await step('实例活动记录', async () => {
  await p.getByText('活动记录').first().click();
  await p.locator('.ant-spin-spinning').first().waitFor({ state: 'detached', timeout: 15000 }).catch(() => undefined);
  await p.waitForTimeout(1500);
  await shot(p, '05-instance-activities');
  return (await p.locator('main').first().innerText()).replace(/\s+/g, ' ').slice(150, 420);
});
log('NETWORK>=400', JSON.stringify([...new Set(network)]));
log('ERRORS', JSON.stringify(errors));
await browser.close();
