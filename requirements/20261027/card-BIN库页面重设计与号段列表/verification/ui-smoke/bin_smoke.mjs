// 卡 BIN 库页面冒烟（本机 e2e）：经 nginx https://127.0.0.1:18444 登录与调用 API（Origin 正确），页面静态资源转发到工作树 Vite 18311。
// 用法：node bin_smoke.mjs <en|zh-CN> <1440|390>；管理员凭据取 local-environment/e2e/secrets.env，不打印。
import { createRequire } from 'node:module';
import { appendFileSync, mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = 'https://127.0.0.1:18444', VITE = 'http://127.0.0.1:18311';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const [lang = 'en', widthArg = '1440'] = process.argv.slice(2);
const width = Number(widthArg), zh = lang === 'zh-CN', tag = `${lang}-${width}`;
const env = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8').split('\n')
  .filter(l => l.includes('=') && !l.startsWith('#')).map(l => { const i = l.indexOf('='); return [l.slice(0, i).trim(), l.slice(i + 1).trim().replace(/^"|"$/g, '')]; }));
mkdirSync(join(HERE, 'screens'), { recursive: true });
const LOG = join(HERE, `findings-${tag}.jsonl`);
const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width, height: width < 600 ? 844 : 900 }, ignoreHTTPSErrors: true, locale: lang });
await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, lang);
await ctx.route(WEB + '/**', async route => {
  const u = new URL(route.request().url());
  if (u.pathname.startsWith('/api/')) return route.continue();
  const r = await fetch(VITE + u.pathname + u.search, { headers: { accept: route.request().headers().accept ?? '*/*' } });
  const headers = Object.fromEntries(r.headers); delete headers['content-encoding']; delete headers['content-length'];
  return route.fulfill({ status: r.status, headers, body: Buffer.from(await r.arrayBuffer()) });
});
const page = await ctx.newPage();
let current = 'login', shotNo = 0, fails = 0; const apiCalls = [];
const note = (kind, detail) => { const f = { tag, page: current, kind, ...detail }; appendFileSync(LOG, JSON.stringify(f) + '\n'); console.log('FIND', JSON.stringify(f).slice(0, 300)); };
const ok = (name, pass, ev) => { if (!pass) fails++; console.log(`${pass ? 'PASS' : 'FAIL'} ${tag} ${name}${ev === undefined ? '' : ' | ' + JSON.stringify(ev).slice(0, 300)}`); if (!pass) note('check-failed', { name, ev }); };
page.on('pageerror', e => note('pageerror', { text: String(e).slice(0, 300) }));
page.on('console', m => { if (m.type() === 'error' && !/WebSocket|\[vite\]/.test(m.text())) note('console-error', { text: m.text().slice(0, 300) }); });
page.on('response', r => { const u = new URL(r.url()); if (u.pathname.includes('/bin-library/')) { apiCalls.push(`${r.status()} ${u.pathname}${u.search}`); if (r.status() >= 400 && !page.__expect4xx) note('http-error', { url: u.pathname + u.search, status: r.status(), rid: r.headers()['x-request-id'] }); } });
const settle = async (ms = 800) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); await page.locator('.ant-spin-spinning').first().waitFor({ state: 'detached', timeout: 10000 }).catch(() => {}); await page.waitForTimeout(200); };
const shot = async name => { current = name; const f = `screens/${tag}-${String(++shotNo).padStart(2, '0')}-${name}.png`; await page.screenshot({ path: join(HERE, f), fullPage: true }); return f; };
const RAWKEY = /\b(card|nav|common|enum)\.[a-z][\w]*(\.[\w-]+)+/g;
async function scan(name) {
  const file = await shot(name);
  const body = await page.locator('body').innerText().catch(() => '');
  const keys = [...new Set(body.match(RAWKEY) ?? [])].filter(k => !/^card\.(go|example)/.test(k));
  if (keys.length) note('raw-i18n-key', { keys: keys.slice(0, 10), screenshot: file });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  if (overflow > 1) note('horizontal-overflow', { px: overflow, screenshot: file });
  return body;
}
const rows = () => page.locator(width < 600 ? '.bin-card' : '.bin-table tbody tr.ant-table-row').count();
const search = () => page.getByPlaceholder(/BIN prefix|BIN 前缀/).first();

await page.goto(WEB + '/'); await settle();
await page.getByLabel(/Email \/ initial account|Email \/ 初始化账号|邮箱|账号/).first().fill(env.E2E_ADMIN_EMAIL);
await page.getByLabel(/^(Password|密码)$/).fill(env.E2E_ADMIN_PASSWORD);
await page.getByRole('button', { name: /^(Sign In|登\s*录)$/ }).click();
await page.getByText(/Sign Out|退出/).first().waitFor({ timeout: 20000 });

// 01 默认
await page.goto(WEB + '/card-bin-library'); await settle(2500);
let body = await scan('01-default');
ok('01 默认显示生效版本 v1 的 17 个号段', await rows() === 17, await rows());
ok('01 页头生效徽标与元信息', /v1/.test(await page.locator('.bin-header').innerText()), (await page.locator('.bin-header').innerText()).slice(0, 200));
ok('01 无旧摘要卡与长说明', !(zh ? /当前生效版本|生效号段数/ : /Current active version|Active ranges/i).test(body));
ok('01 只请求了 ranges 与版本列表（无报错）', apiCalls.some(c => /^200 .*\/versions\/1\/ranges/.test(c)), apiCalls);

// 02 前缀 + 匹配条
await search().fill('401288'); await settle(1500);
await scan('02-prefix-401288');
ok('02 前缀 401288 → 2 个号段（含 8 位 40128888）', await rows() === 2, await rows());
ok('02 最长前缀匹配条显示', await page.locator('.bin-match:not(.is-miss)').count() === 1, await page.locator('.bin-match').innerText().catch(() => ''));

// 卡组织 chip
if (width >= 600) {
  await page.locator('.bin-chip').first().click(); await page.waitForTimeout(400);
  await page.locator('.bin-chip-option', { hasText: 'Visa' }).first().click(); await settle(1200);
  await scan('02b-brand-visa');
  ok('02b 卡组织 Visa chip 生效', /Visa/.test(await page.locator('.bin-chip.is-active').first().innerText().catch(() => '')) && await rows() === 2, apiCalls.at(-1));
}

// 03 无结果
await search().fill('999999'); await settle(1500);
body = await scan('03-no-result');
ok('03 无结果空状态 + 未识别匹配条', await rows() === 0 && await page.locator('.bin-match.is-miss').count() === 1);

// 完整卡号拦截
const before = apiCalls.length;
await search().fill('4111111111111111'); await settle(1200);
await scan('03c-full-pan-blocked');
ok('03c 超过 8 位提示且不发请求', await page.locator('.bin-input-hint').count() > 0 && apiCalls.length === before, apiCalls.slice(before));

// 清空并切换版本 v2
await search().fill(''); await settle(800);
if (width >= 600) {
  const clear = page.getByText(/清除筛选|Clear filters/i).first();
  if (await clear.isVisible().catch(() => false)) { await clear.click(); await settle(800); }
  await page.locator('.bin-toolbar .ant-select').first().click(); await page.waitForTimeout(400);
  await scan('03d-version-select-open');
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option', { hasText: 'v2' }).first().click(); await settle(1500);
  await scan('03e-version-v2');
  ok('03e 切换到 v2 → 8 个号段', await rows() === 8, await rows());
}

// 04 版本页签
await page.locator('.bin-tab').nth(1).click(); await settle(2000);
body = await scan('04-versions');
ok('04 版本列表含 v1–v5', ['v1', 'v2', 'v3', 'v4', 'v5'].every(v => body.includes(v)));
ok('04 操作人显示名（不是 18 位 ID）', !/\b\d{18}\b/.test(body), body.match(/\d{12,}/g));
await page.getByText('v3', { exact: true }).locator('visible=true').first().click(); await settle(1500);
body = await scan('05-invalid-v3');
ok('05 v3 校验失败显示错误行', /bin_prefix|card_brand|issuer_country|funding_type/.test(body));
await page.getByText('v2', { exact: true }).locator('visible=true').first().click(); await settle(1500);
body = await scan('04b-valid-v2');
ok('04b v2 号段预览', await page.locator('.bin-detail-body table tbody tr').count() >= 5, await page.locator('.bin-detail-body table tbody tr').count());
const act = page.locator('.bin-detail-footer button').last();
if (await act.isVisible().catch(() => false)) { await act.click(); await page.waitForTimeout(800); await scan('06-activate-modal'); ok('06 激活弹窗打开', await page.locator('.ant-modal:visible').count() === 1); await page.keyboard.press('Escape'); await page.waitForTimeout(400); }
if (width >= 600) {
  await page.locator('.bin-header-actions button').last().click(); await page.waitForTimeout(800);
  await scan('07-import-modal'); ok('07 导入弹窗打开', await page.locator('.ant-modal:visible').count() === 1);
  await page.keyboard.press('Escape');
}
console.log(`DONE ${tag} fails=${fails}`);
await browser.close();
