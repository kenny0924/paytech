// 工作流带文件浏览器验收：发起（启动表单上传 PDF）→ 待办里 FILE_UPLOAD 与带文件 FORM 上传提交 → user2 审批页内嵌预览与下载。
// 截图 screenshots/file-*.png。只记方法、路径、状态。用法：node file_browser.mjs
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
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
const PDF = name => join(SCRATCH, 'wf-pdf', name);
const log = (...a) => console.log(...a);
const shot = (page, name) => page.screenshot({ path: join(HERE, 'screenshots', `file-${name}.png`) });
const network = [], errors = [];
const browser = await chromium.launch({ headless: true });

async function session(email, password, tag) {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1600, height: 1000 }, acceptDownloads: true });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(`${tag} ${String(e).slice(0, 200)}`));
  page.on('console', m => { if (m.type() === 'error' && !m.text().includes('deprecated')) errors.push(`${tag} console ${m.text().slice(0, 200)}`); });
  page.on('response', r => { if (r.status() >= 400) network.push(`${tag} ${r.request().method()} ${new URL(r.url()).pathname} -> ${r.status()}`); });
  await page.goto(OPS + '/');
  await page.getByLabel('Email / 初始化账号').fill(email);
  await page.getByLabel('密码').fill(password);
  await page.getByRole('button', { name: /^登\s*录$/ }).click();
  await page.waitForLoadState('networkidle');
  return page;
}
const settle = async page => { await page.waitForLoadState('networkidle'); await page.waitForTimeout(1200); };
async function step(name, fn) { try { log('PASS', name, '|', (await fn()) ?? ''); } catch (e) { log('FAIL', name, '|', String(e.message || e).split('\n')[0].slice(0, 300)); } }

const admin = process.env.ONLY_REVIEW ? null : await session(env.E2E_ADMIN_EMAIL, env.E2E_ADMIN_PASSWORD, 'admin');
let instanceId = process.env.ONLY_REVIEW ? JSON.parse(readFileSync(join(SCRATCH, 'workflow-file-browser.json'), 'utf8')).instanceId : undefined;
// 1. 发起：启动表单上传 PDF
if (!process.env.ONLY_REVIEW) await step('发起（上传启动文件）', async () => {
  const p = admin;
  await p.goto(`${OPS}/workflow/instances`); await settle(p);
  await p.getByRole('button', { name: /发起流程/ }).click();
  const modal = p.locator('.ant-modal').last();
  await modal.getByText(`E2E 带文件流程 ${fs.stamp}`).click();
  await modal.getByRole('button', { name: /下一步/ }).click();
  await modal.getByLabel('实例标题').fill(`E2E FILE 浏览器 ${fs.stamp}`);
  await modal.locator('.ant-form-item', { hasText: '渠道编码' }).locator('input').first().fill('E2E-FILE-UI');
  const done = p.waitForResponse(r => r.url().includes('/attachments/uploads/') && r.url().endsWith('/completion'), { timeout: 20000 });
  await modal.locator('input[type=file]').first().setInputFiles(PDF('scan-race.pdf'));
  const completion = await done;
  await p.waitForTimeout(1500);
  await shot(p, '01-launch-uploaded');
  const fileId = (await completion.json()).fileId;
  await modal.getByRole('button', { name: /下一步/ }).click();
  const reply = p.waitForResponse(r => r.url().endsWith('/workflow/v1/instances') && r.request().method() === 'POST', { timeout: 15000 });
  await modal.getByRole('button', { name: /^发\s*起$/ }).click();
  const res = await reply;
  const body = await res.json().catch(() => ({}));
  instanceId = body.objectId;
  await p.waitForTimeout(1500); await shot(p, '02-launch-result');
  return `完成上传 HTTP ${completion.status()} fileId=${fileId}；发起 HTTP ${res.status()} instanceId=${instanceId ?? JSON.stringify(body).slice(0, 200)}`;
});
if (!process.env.ONLY_REVIEW) writeFileSync(join(SCRATCH, 'workflow-file-browser.json'), JSON.stringify({ instanceId }));

// 2. 待办：FILE_UPLOAD 与带文件 FORM
if (instanceId && !process.env.ONLY_REVIEW) {
  await step('待办 FILE_UPLOAD 上传并提交', async () => {
    const p = admin;
    await p.goto(`${OPS}/workflow/my-tasks`); await settle(p);
    await p.locator('.wf-task-item', { hasText: `E2E FILE 浏览器 ${fs.stamp}` }).filter({ hasText: 'contractUpload' }).first().click(); await settle(p);
    const action = p.locator('.wf-action.is-expanded').first();
    const done = p.waitForResponse(r => r.url().endsWith('/completion'), { timeout: 20000 });
    await action.locator('input[type=file]').first().setInputFiles(PDF('contract-scan.pdf'));
    await done; await p.waitForTimeout(1000);
    const reply = p.waitForResponse(r => r.url().includes('/submission'), { timeout: 15000 });
    await action.getByRole('button', { name: /^提\s*交$/ }).click();
    const res = await reply; const body = await res.json();
    await p.waitForTimeout(1200); await shot(p, '03-file-upload-submitted');
    return `提交 HTTP ${res.status()} status=${body.status} reason=${body.failureReason ?? ''}`;
  });
  await step('待办 带文件 FORM 上传并提交', async () => {
    const p = admin;
    await p.goto(`${OPS}/workflow/my-tasks`); await settle(p);
    await p.locator('.wf-task-item', { hasText: `E2E FILE 浏览器 ${fs.stamp}` }).filter({ hasText: 'evidenceForm' }).first().click(); await settle(p);
    const action = p.locator('.wf-action.is-expanded').first();
    await action.locator('.ant-form-item', { hasText: '说明' }).locator('input').first().fill('浏览器提交的证据');
    const done = p.waitForResponse(r => r.url().endsWith('/completion'), { timeout: 20000 });
    await action.locator('input[type=file]').first().setInputFiles(PDF('evidence.pdf'));
    await done; await p.waitForTimeout(1000);
    const reply = p.waitForResponse(r => r.url().includes('/submission'), { timeout: 15000 });
    await action.getByRole('button', { name: /^提\s*交$/ }).click();
    const res = await reply; const body = await res.json();
    await p.waitForTimeout(1200); await shot(p, '04-file-form-submitted');
    return `提交 HTTP ${res.status()} status=${body.status} reason=${body.failureReason ?? ''}`;
  });
  await step('实例详情文件列表', async () => {
    const p = admin;
    await p.goto(`${OPS}/workflow/instances/${instanceId}`); await settle(p);
    await p.getByText('启动信息').click().catch(() => undefined); await p.waitForTimeout(800);
    await shot(p, '05-instance-start-info');
    return (await p.locator('main').first().innerText()).replace(/\s+/g, ' ').slice(0, 260);
  });
}

// 3. user2 审批页预览与下载（浏览器实例优先，否则 API 实例）
const u2 = await session(user2.email, user2.password, 'user2');
await step('审批页内嵌预览 PDF', async () => {
  const p = u2;
  const title = instanceId ? `E2E FILE 浏览器 ${fs.stamp}` : `E2E FILE API ${fs.stamp}`;
  await p.goto(`${OPS}/workflow/my-tasks`); await settle(p);
  await p.locator('.wf-task-item', { hasText: title }).first().click(); await settle(p);
  for (let i = 0; i < 5 && await p.getByText('加载失败').count(); i++) {
    await shot(p, '06a-approval-load-failed-429');
    await p.waitForTimeout(3000); await p.getByRole('button', { name: /重\s*试/ }).first().click(); await settle(p);
  }
  const downloads = [];
  p.on('download', d => downloads.push(d.suggestedFilename()));
  const preview = p.getByRole('button', { name: /^预\s*览$/ }).first();
  const signed = p.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 15000 });
  if (await preview.count()) await preview.click();
  const res = await signed.catch(() => null);
  await p.waitForTimeout(3000);
  const iframe = p.locator('.wf-preview iframe').first();
  const hasIframe = await iframe.count();
  let frameInfo = '无 iframe';
  if (hasIframe) {
    const frame = await iframe.elementHandle().then(h => h.contentFrame());
    const fu = frame ? frame.url() : ''; frameInfo = `iframe src.host=${(await iframe.getAttribute('src') || '').replace(/^(https?:\/\/[^/]+).*$/, '$1')} frame.url=${fu.startsWith('http') ? fu.replace(/\?.*$/, '').slice(0, 80) : JSON.stringify(fu)} 内容=${frame ? (await frame.content().catch(() => '')).slice(0, 80).replace(/\s+/g, ' ') : ''}`;
  }
  await shot(p, '06-approval-preview');
  return `签发 HTTP ${res?.status()} ${frameInfo} 触发下载=${JSON.stringify(downloads)}`;
});
await step('审批页下载按钮', async () => {
  const p = u2;
  const popupP = p.context().waitForEvent('page', { timeout: 10000 }).catch(() => null);
  const dlP = p.waitForEvent('download', { timeout: 10000 }).catch(() => null);
  await p.getByRole('button', { name: /^下\s*载$/ }).first().click();
  const [popup, dl] = await Promise.all([popupP, dlP]);
  let info = '';
  if (popup) { const d = await popup.waitForEvent('download', { timeout: 8000 }).catch(() => null); info = `新标签 ${(popup.url() || '').replace(/\?.*$/, '').slice(0, 80)} 下载=${d?.suggestedFilename() ?? '无'}`; }
  if (dl) info += ` 本页下载=${dl.suggestedFilename()}`;
  await shot(p, '07-approval-download');
  return info || '无新标签也无下载事件';
});
log('NETWORK>=400', JSON.stringify([...new Set(network)]));
log('ERRORS', JSON.stringify([...new Set(errors)].slice(0, 10)));
await browser.close();
