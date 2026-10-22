// 附件内嵌预览浏览器验收（#28 disposition）：user2 审批页“文件预览”对 PDF / PNG 点“查看”→ INLINE 签发并 iframe / img 内嵌；
// 点“下载”→ ATTACHMENT。签发后立即用 curl 取存储响应头（只打印响应头，不打印签名地址）。截图 screenshots/inline-*.png。
// 前置：python3 inline_preview_seed.py。用法：node inline_preview_browser.mjs（BROWSER_CHANNEL 缺省 chromium，可设 chrome）
// BYPASS_GATEWAY=1：诊断模式，仅把 #28 下载授权请求改由测试直送 ops-bff（按 Gateway 可信 Header 构造），用于在 Gateway 路由未登记
// disposition Query 时验证 BFF → Platform → 存储 → 浏览器内嵌；截图前缀 inline-bypass-。缺省走 Nginx → Gateway 正常链路，前缀 inline-。
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'https://127.0.0.1:18444';
const SCRATCH = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/49da8772-1276-46ac-8174-179e39b15018/scratchpad';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const st = JSON.parse(readFileSync(join(SCRATCH, 'workflow-inline-state.json'), 'utf8'));
const user2 = JSON.parse(readFileSync(join(SCRATCH, 'workflow-e2e-user2.json'), 'utf8'));
const log = (...a) => console.log(...a);
const BYPASS = process.env.BYPASS_GATEWAY === '1';
const shot = (page, name) => page.screenshot({ path: join(HERE, 'screenshots', `inline-${BYPASS ? 'bypass-' : ''}${name}.png`) });
const network = [], errors = [], grants = [], storage = [];
let storageOrigin = null;
const PICK = ['content-type', 'content-disposition', 'x-frame-options', 'content-security-policy', 'cross-origin-resource-policy', 'x-content-type-options'];
const pick = h => Object.fromEntries(PICK.filter(k => h[k] !== undefined).map(k => [k, h[k]]));
/** 用 curl 取签名地址的响应头（GET，丢弃正文）；地址经 stdin 传入 curl 配置，不出现在命令行与输出中。 */
function curlHeaders(url) {
  const out = execFileSync('curl', ['-sS', '-k', '-o', '/dev/null', '-D', '-', '-K', '-'], { input: `url = "${url}"\n`, encoding: 'utf8' });
  return out.split(/\r?\n/).filter(l => /^(HTTP\/|content-type|content-disposition|content-length|x-frame-options|content-security-policy|cross-origin)/i.test(l)).join(' | ');
}

const browser = await chromium.launch({ headless: true, channel: process.env.BROWSER_CHANNEL || 'chromium' });
const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1600, height: 1000 }, acceptDownloads: true });
const page = await ctx.newPage();
ctx.on('page', p => p.on('console', m => { if (m.type() === 'error') errors.push(`popup console ${m.text().slice(0, 200)}`); }));
page.on('pageerror', e => errors.push(`pageerror ${String(e).slice(0, 200)}`));
page.on('console', m => { if (['error', 'warning'].includes(m.type()) && !m.text().includes('deprecated')) errors.push(`console.${m.type()} ${m.text().slice(0, 240)}`); });
ctx.on('requestfailed', r => errors.push(`requestfailed ${r.resourceType()} ${new URL(r.url()).origin}${new URL(r.url()).pathname.replace(/[^/]+$/, '…')} ${r.failure()?.errorText} page=${(() => { try { return r.frame().page() === page ? 'main' : 'popup'; } catch { return 'n/a'; } })()}`));
ctx.on('response', async r => {
  const u = new URL(r.url());
  if (r.status() >= 400) network.push(`${r.request().method()} ${u.pathname} -> ${r.status()}`);
  if (u.pathname.endsWith('/download') && r.request().method() === 'POST' && u.pathname.includes('/workflow/v1/instances/')) {
    const body = await r.json().catch(() => ({}));
    grants.push({ query: u.search, status: r.status(), disposition: body.disposition, url: body.url, at: Date.now() });
    if (body.url) storageOrigin = new URL(body.url).origin;
  } else if (storageOrigin && u.origin === storageOrigin) {
    storage.push({ type: r.request().resourceType(), status: r.status(), path: u.pathname.replace(/[^/]+$/, '…'), headers: pick(await r.allHeaders()) });
  }
});
if (BYPASS) await ctx.route(/\/api\/operations\/workflow\/v1\/instances\/\d+\/files\/\d+\/download(\?.*)?$/, async route => {
  const req = route.request();
  const token = (req.headers().authorization || '').replace(/^Bearer /, '');
  const c = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString());
  const u = new URL(req.url());
  const response = await route.fetch({ url: `http://127.0.0.1:18201${u.pathname.replace('/api/operations', '')}${u.search}`, headers: {
    ...req.headers(), origin: OPS, 'x-gateway-realm': 'OPERATIONS', 'x-gateway-channel': 'OPERATIONS', 'x-gateway-actor-type': 'PLATFORM_USER',
    'x-gateway-actor-subject': c.sub, 'x-gateway-session-id': c.sid, 'x-gateway-route-code': 'operations-workflow-attachment-download',
    'x-gateway-context-type': 'TENANT', 'x-gateway-tenant-type': 'INTERNAL', 'x-gateway-tenant-id': c.tenant_id, 'x-gateway-tenant-member-id': c.tenant_member_id } });
  await route.fulfill({ response });
});
async function step(name, fn) { try { log('RESULT', name, '|', (await fn()) ?? ''); } catch (e) { log('FAIL', name, '|', String(e.message || e).split('\n')[0].slice(0, 300)); } }
const settle = async () => { await page.waitForLoadState('networkidle'); await page.waitForTimeout(1000); };
const lastGrant = () => grants[grants.length - 1];
const grantText = g => g ? `POST …/download${g.query} -> HTTP ${g.status} disposition=${g.disposition}` : '无签发请求';

for (let attempt = 1; attempt <= 3; attempt++) {
  await page.goto(OPS + '/'); await page.waitForLoadState('networkidle');
  await page.getByLabel('Email / 初始化账号').fill(user2.email);
  await page.getByLabel('密码').fill(user2.password);
  await page.getByRole('button', { name: /^登\s*录$/ }).click();
  if (await page.getByText('退出').first().waitFor({ timeout: 10000 }).then(() => true, () => false)) break;
  log('login retry', attempt, JSON.stringify(network.slice(-3)));
}
await page.goto(`${OPS}/workflow/my-tasks`); await settle();
await page.locator('.wf-task-item', { hasText: st.title }).first().click({ timeout: 20000 }).catch(async e => {
  await shot(page, '00-task-missing');
  log('PAGE', page.url(), (await page.locator('body').innerText()).replace(/\s+/g, ' ').slice(0, 300), 'NETWORK>=400', JSON.stringify(network));
  throw e;
}); await settle();
const preview = page.locator('.wf-preview');
await preview.waitFor({ timeout: 15000 });
await shot(page, '01-approval-files');

await step('PDF 查看 → INLINE + iframe', async () => {
  const n = grants.length;
  const signed = page.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 15000 });
  await preview.locator('li', { hasText: st.pdfName }).getByRole('button', { name: /^查\s*看$/ }).click();
  await signed; await page.waitForTimeout(3500);
  const g = grants.slice(n)[0];
  const headers = g?.url ? curlHeaders(g.url) : '无地址';
  const iframe = preview.locator('iframe');
  const count = await iframe.count();
  let frameInfo = '无 iframe';
  if (count) {
    const frame = await (await iframe.elementHandle()).contentFrame();
    const fu = frame?.url() ?? '';
    const html = frame ? (await frame.content().catch(e => `ERR ${e.message}`)) : '';
    const box = await iframe.boundingBox();
    frameInfo = `iframe=1 frame.url=${fu.startsWith(storageOrigin ?? '#') ? storageOrigin + new URL(fu).pathname.replace(/[^/]+$/, '…') : JSON.stringify(fu.slice(0, 60))}`
      + ` chrome-error=${fu.startsWith('chrome-error')} 内容含embed/pdf=${/<embed|application\/pdf|pdf-viewer/i.test(html)} 内容片段=${html.replace(/\s+/g, ' ').slice(0, 120)}`
      + ` 尺寸=${Math.round(box?.width ?? 0)}x${Math.round(box?.height ?? 0)}`;
  }
  await shot(page, '02-pdf-inline');
  return `${grantText(g)}；${frameInfo}；curl INLINE 头：${headers}`;
});

await step('PNG 查看 → INLINE + img', async () => {
  const n = grants.length;
  const signed = page.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 15000 });
  await preview.locator('li', { hasText: st.pngName }).getByRole('button', { name: /^查\s*看$/ }).click();
  await signed; await page.waitForTimeout(2500);
  const g = grants.slice(n)[0];
  const headers = g?.url ? curlHeaders(g.url) : '无地址';
  const img = preview.locator('.wf-file-preview img');
  const info = await img.count() ? await img.evaluate(el => `img complete=${el.complete} natural=${el.naturalWidth}x${el.naturalHeight} referrerPolicy=${el.referrerPolicy}`) : '无 img';
  await shot(page, '03-png-inline');
  return `${grantText(g)}；${info}；curl INLINE 头：${headers}`;
});

await step('下载 → ATTACHMENT', async () => {
  const n = grants.length;
  const popupP = ctx.waitForEvent('page', { timeout: 10000 }).catch(() => null);
  const signed = page.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 15000 });
  await preview.locator('li', { hasText: st.pdfName }).getByRole('button', { name: /^下\s*载$/ }).click();
  await signed;
  const popup = await popupP;
  const dl = popup ? await popup.waitForEvent('download', { timeout: 8000 }).catch(() => null) : null;
  await page.waitForTimeout(800);
  const g = grants.slice(n)[0];
  const headers = g?.url ? curlHeaders(g.url) : '无地址';
  await shot(page, '04-download');
  return `${grantText(g)}；新标签=${popup ? 'yes' : 'no'} 浏览器下载文件名=${dl?.suggestedFilename() ?? '无'}；curl ATTACHMENT 头：${headers}`;
});

log('GRANTS', JSON.stringify(grants.map(g => ({ query: g.query, status: g.status, disposition: g.disposition }))));
log('STORAGE_RESPONSES', JSON.stringify(storage));
log('NETWORK>=400', JSON.stringify([...new Set(network)]));
log('CONSOLE/ERRORS', JSON.stringify([...new Set(errors)].slice(0, 20)));
await browser.close();
