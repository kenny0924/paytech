// 复测：待办 429、完成上传类型不符提示、PDF 查看降级、批量审批。截图 screenshots/refix-*.png。
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'https://127.0.0.1:18444';
const SCRATCH = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/49da8772-1276-46ac-8174-179e39b15018/scratchpad';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const fs = JSON.parse(readFileSync(join(SCRATCH, 'workflow-file-state.json'), 'utf8'));
const env = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8').split('\n')
  .filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim().replace(/^"|"$/g, '')]));
const user2 = JSON.parse(readFileSync(join(SCRATCH, 'workflow-e2e-user2.json'), 'utf8'));
const log = (...a) => console.log(...a);
const shot = (page, name) => page.screenshot({ path: join(HERE, 'screenshots', `${process.env.SHOT_PREFIX || 'refix'}-${name}.png`) });
const network = [], errors = [], detailCalls = [];
const browser = await chromium.launch({ headless: true });
async function session(email, password, tag) {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1600, height: 1000 }, acceptDownloads: true });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(`${tag} ${String(e).slice(0, 200)}`));
  page.on('response', r => { if (/\/workflow\/v1\/instances\/\d+$/.test(new URL(r.url()).pathname) && r.request().method() === 'GET') detailCalls.push(`${tag} ${Date.now()} ${new URL(r.url()).pathname.split('/').pop()} ${r.status()}`); if (r.status() >= 400) network.push(`${tag} ${r.request().method()} ${new URL(r.url()).pathname} -> ${r.status()}`); });
  await page.goto(OPS + '/');
  await page.getByLabel('Email / 初始化账号').fill(email);
  await page.getByLabel('密码').fill(password);
  await page.getByRole('button', { name: /^登\s*录$/ }).click();
  await page.waitForLoadState('networkidle');
  return page;
}
const settle = async p => { await p.waitForLoadState('networkidle'); await p.waitForTimeout(800); };
async function step(name, fn) { try { log('PASS', name, '|', (await fn()) ?? ''); } catch (e) { log('FAIL', name, '|', String(e.message || e).split('\n')[0].slice(0, 300)); } }

const u2 = await session(user2.email, user2.password, 'user2');
await step('1 待办连续切换无 429', async () => {
  const p = u2; const before = network.length;
  await p.goto(`${OPS}/workflow/my-tasks`); await settle(p);
  const items = p.locator('.wf-task-item'); const n = await items.count();
  let failures = 0;
  for (let round = 0; round < 2; round++) for (let i = 0; i < n; i++) {
    await items.nth(i).click(); await p.waitForTimeout(250);
    if (await p.getByText('当前并发请求过多').count()) failures++;
  }
  await settle(p);
  if (await p.getByText('当前并发请求过多').count()) failures++;
  await shot(p, '1-my-tasks-switch');
  const new429 = network.slice(before).filter(x => x.endsWith('429'));
  return `待办 ${n} 条，切换 ${2 * n} 次，出现“并发过多” ${failures} 次，429 响应 ${new429.length} 次`;
});
await step('3 审批页文件查看降级', async () => {
  const p = u2;
  await p.locator('.wf-task-item', { hasText: `E2E FILE API ${fs.stamp}` }).first().click(); await settle(p);
  const iframes = await p.locator('.wf-task-main iframe').count();
  const view = p.getByRole('button', { name: /^查\s*看$/ }).first();
  if (await view.count()) { await view.click(); await p.waitForTimeout(800); }
  const infoText = (await p.locator('.wf-file-info').first().innerText().catch(() => '')).replace(/\s+/g, ' ');
  const iframesAfter = await p.locator('.wf-task-main iframe').count();
  await shot(p, '3-file-view');
  const popupP = p.context().waitForEvent('page', { timeout: 10000 }).catch(() => null);
  const signed = p.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 10000 }).catch(() => null);
  await p.getByRole('button', { name: /下载查看/ }).first().click();
  const [popup, res] = await Promise.all([popupP, signed]);
  const popupUrl = popup ? (popup.url() || '').replace(/\?.*$/, '') : '无新窗口';
  return `文件信息=${infoText}；iframe 数 前=${iframes} 后=${iframesAfter}；签发 HTTP ${res?.status()}；新窗口=${popupUrl || '(about:blank/下载)'}`;
});
await step('4 批量审批', async () => {
  const p = u2;
  await p.goto(`${OPS}/workflow/my-tasks`); await settle(p);
  const boxes = p.locator('.wf-task-item .ant-checkbox-input');
  const n = await boxes.count(); let checked = 0;
  for (let i = 0; i < n; i++) { if (await boxes.nth(i).isEnabled()) { await boxes.nth(i).check(); checked++; } }
  const foot = p.locator('.wf-action-foot').last();
  const first = (await foot.innerText()).replace(/\s+/g, ' ');
  await shot(p, '4a-batch-checking');
  await p.getByRole('button', { name: /批量同意/ }).waitFor({ state: 'visible' });
  for (let i = 0; i < 40 && !(await p.getByRole('button', { name: /批量同意/ }).isEnabled()); i++) await p.waitForTimeout(250);
  const ready = (await foot.innerText()).replace(/\s+/g, ' ');
  await shot(p, '4b-batch-ready');
  await p.getByRole('button', { name: /批量同意/ }).click();
  await p.getByRole('button', { name: /^完\s*成$/ }).waitFor({ timeout: 30000 });
  const done = (await foot.innerText()).replace(/\s+/g, ' ');
  const rows = (await p.locator('.wf-table tbody tr').allInnerTexts()).map(t => t.replace(/\s+/g, ' ').slice(0, 70));
  const unable = await p.getByText('无法检查').count();
  await shot(p, '4c-batch-done');
  return `复选框 ${n} 个，勾选 ${checked}；初始提示「${first.slice(0, 60)}」；就绪「${ready.slice(0, 80)}」；完成「${done.slice(0, 60)}」；行数=${rows.length} 行=${JSON.stringify(rows)}；“无法检查”=${unable}`;
});
// 2：浏览器上传声明 PDF、内容不是 PDF → 服务端 400 原因在上传组件显示
const admin = await session(env.E2E_ADMIN_EMAIL, env.E2E_ADMIN_PASSWORD, 'admin');
await step('2 上传组件显示类型不符原因', async () => {
  const p = admin;
  await p.goto(`${OPS}/workflow/instances`); await settle(p);
  await p.getByRole('button', { name: /发起流程/ }).click();
  const modal = p.locator('.ant-modal').last();
  await modal.getByText(`E2E 带文件流程 ${fs.stamp}`).click();
  await modal.getByRole('button', { name: /下一步/ }).click();
  const done = p.waitForResponse(r => r.url().endsWith('/completion'), { timeout: 20000 });
  await modal.locator('input[type=file]').first().setInputFiles({ name: 'fake.pdf', mimeType: 'application/pdf', buffer: Buffer.from('this is not a pdf at all '.repeat(20)) });
  const res = await done; const body = await res.json().catch(() => ({}));
  await p.waitForTimeout(1200);
  const text = (await modal.locator('.wf-upload').first().innerText()).replace(/\s+/g, ' ');
  await shot(p, '2-upload-rejected');
  return `完成上传 HTTP ${res.status()} code=${body.code} detail=${body.detail}；组件文本「${text.slice(0, 160)}」`;
});
log('DETAIL GET 总数', detailCalls.length, '429 数', detailCalls.filter(x => x.endsWith(' 429')).length, JSON.stringify(detailCalls.filter(x => x.endsWith(' 429'))));
log('NETWORK>=400', JSON.stringify([...new Set(network)]));
log('ERRORS', JSON.stringify([...new Set(errors)].slice(0, 10)));
await browser.close();
