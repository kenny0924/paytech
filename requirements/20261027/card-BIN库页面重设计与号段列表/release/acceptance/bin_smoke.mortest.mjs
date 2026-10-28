// 卡 BIN 库页面冒烟（mor-test，runbook 第 ⑩ 步）：改写自 verification/ui-smoke/bin_smoke.mjs。
// 与本机版的差别：直接加载 mor-test 已部署页面 http://localhost:28080（不转发 Vite）；管理员凭据取 test-environment/private/access.md，不打印；
// 先核对服务端返回的 index.html 入口 = 候选 dist（EXPECT_INDEX，缺省 binlist-20261027a 的 index-D0P_eShl.js）；
// zh-CN 额外核对页签 / 表头为中文（语言包 #18 已发布）与国旗图片实际加载；操作人显示脱敏邮箱而不是 18 位 ID。
// 只读：激活 / 导入弹窗只打开不提交。输出写 ../../verification/ui-smoke/（文件名前缀 mortest-）。
// 用法：node bin_smoke.mortest.mjs <en|zh-CN> <1440|390>
import { createRequire } from 'node:module';
import { appendFileSync, mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, '../../verification/ui-smoke');
const WEB = process.env.WEB ?? 'http://localhost:28080';
const EXPECT_INDEX = process.env.EXPECT_INDEX ?? 'assets/index-D0P_eShl.js';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const [lang = 'en', widthArg = '1440'] = process.argv.slice(2);
const width = Number(widthArg), zh = lang === 'zh-CN', tag = `mortest-${lang}-${width}`;
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const env = { E2E_ADMIN_EMAIL: access.match(/账号：(\S+)/)[1], E2E_ADMIN_PASSWORD: access.match(/密码：(\S+)/)[1] };
mkdirSync(join(OUT, 'screens'), { recursive: true });
const LOG = join(OUT, `findings-${tag}.jsonl`);
let current = 'login', shotNo = 0, fails = 0; const apiCalls = [];
const note = (kind, detail) => { const f = { tag, page: current, kind, ...detail }; appendFileSync(LOG, JSON.stringify(f) + '\n'); console.log('FIND', JSON.stringify(f).slice(0, 300)); };
const ok = (name, pass, ev) => { if (!pass) fails++; console.log(`${pass ? 'PASS' : 'FAIL'} ${tag} ${name}${ev === undefined ? '' : ' | ' + JSON.stringify(ev).slice(0, 300)}`); if (!pass) note('check-failed', { name, ev }); };

// 0 服务端返回的是新制品
const html = await (await fetch(WEB + '/')).text();
ok('00 index.html 入口为候选 dist', html.includes(EXPECT_INDEX), html.match(/assets\/index-[^"]*\.js/)?.[0]);

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width, height: width < 600 ? 844 : 900 }, ignoreHTTPSErrors: true, locale: lang });
await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, lang);
const page = await ctx.newPage();
page.on('pageerror', e => note('pageerror', { text: String(e).slice(0, 300) }));
page.on('console', m => { if (m.type() === 'error') note('console-error', { text: m.text().slice(0, 300) }); });
page.on('response', r => { const u = new URL(r.url()); if (u.pathname.includes('/bin-library/')) { apiCalls.push(`${r.status()} ${u.pathname}${u.search}`); if (r.status() >= 400) note('http-error', { url: u.pathname + u.search, status: r.status(), rid: r.headers()['x-request-id'] }); } });
const settle = async (ms = 800) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); await page.locator('.ant-spin-spinning').first().waitFor({ state: 'detached', timeout: 10000 }).catch(() => {}); await page.waitForTimeout(200); };
const shot = async name => { current = name; const f = `screens/${tag}-${String(++shotNo).padStart(2, '0')}-${name}.png`; await page.screenshot({ path: join(OUT, f), fullPage: true }); return f; };
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

// 01 默认（mor-test：v1 ACTIVE 17 个号段）
await page.goto(WEB + '/card-bin-library'); await settle(2500);
let body = await scan('01-default');
ok('01 默认显示生效版本 v1 的 17 个号段', await rows() === 17, await rows());
ok('01 页头生效徽标与元信息', /v1/.test(await page.locator('.bin-header').innerText()), (await page.locator('.bin-header').innerText()).slice(0, 200));
ok('01 无旧摘要卡与长说明', !(zh ? /当前生效版本|生效号段数/ : /Current active version|Active ranges/i).test(body));
ok('01 ranges 请求 200', apiCalls.some(c => /^200 .*\/versions\/1\/ranges/.test(c)), apiCalls);
const tabs = (await page.locator('.bin-tab').allInnerTexts()).join('|');
ok(`01 页签文案为${zh ? '中文（语言包已发布）' : '英文'}`, zh ? /号段数据/.test(tabs) && /版本/.test(tabs) : /BIN Ranges/.test(tabs) && /Versions/.test(tabs), tabs);
await page.waitForTimeout(1500);
const flags = await page.locator('img.bin-flag').evaluateAll(imgs => imgs.map(i => ({ ok: i.complete && i.naturalWidth > 0, src: i.src.slice(0, 80) })));
ok('01 国旗渲染（至少 1 个且全部加载成功）', flags.length > 0 && flags.every(f => f.ok), { count: flags.length, bad: flags.filter(f => !f.ok).slice(0, 3) });

// 02 前缀 + 匹配条
await search().fill('401288'); await settle(1500);
await scan('02-prefix-401288');
ok('02 前缀 401288 → 2 个号段（含 8 位 40128888）', await rows() === 2, await rows());
ok('02 最长前缀匹配条显示', await page.locator('.bin-match:not(.is-miss)').count() === 1, await page.locator('.bin-match').innerText().catch(() => ''));
ok('02 请求带 binPrefix 经 Gateway 白名单 200', apiCalls.some(c => /^200 .*\/ranges\?.*binPrefix=401288/.test(c)), apiCalls.slice(-3));

if (width >= 600) {
  await page.locator('.bin-chip').first().click(); await page.waitForTimeout(400);
  await page.locator('.bin-chip-option', { hasText: 'Visa' }).first().click(); await settle(1200);
  await scan('02b-brand-visa');
  ok('02b 卡组织 Visa chip 生效', /Visa/.test(await page.locator('.bin-chip.is-active').first().innerText().catch(() => '')) && await rows() === 2, apiCalls.at(-1));
}

await search().fill('999999'); await settle(1500);
await scan('03-no-result');
ok('03 无结果空状态 + 未识别匹配条', await rows() === 0 && await page.locator('.bin-match.is-miss').count() === 1);

const before = apiCalls.length;
await search().fill('4111111111111111'); await settle(1200);
await scan('03c-full-pan-blocked');
ok('03c 超过 8 位提示且不发请求', await page.locator('.bin-input-hint').count() > 0 && apiCalls.length === before, apiCalls.slice(before));

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

// 04 版本页签（mor-test：v1—v6；操作人 221546852372267008 无登录名 → 脱敏邮箱）
await page.locator('.bin-tab').nth(1).click(); await settle(2000);
body = await scan('04-versions');
ok('04 版本列表含 v1–v6', ['v1', 'v2', 'v3', 'v4', 'v5', 'v6'].every(v => body.includes(v)));
ok('04 操作人显示名（不是 18 位 ID）', !/\b\d{18}\b/.test(body), body.match(/\d{12,}/g));
ok('04 操作人为脱敏邮箱', /\*\*\*@/.test(body), body.match(/\S+\*\*\*\S+/g));
await page.getByText('v3', { exact: true }).locator('visible=true').first().click(); await settle(1500);
body = await scan('05-invalid-v3');
ok('05 v3 校验失败显示错误行', /bin_prefix|card_brand|issuer_country|funding_type/.test(body));
await page.getByText('v2', { exact: true }).locator('visible=true').first().click(); await settle(1500);
body = await scan('04b-valid-v2');
ok('04b v2 号段预览', await page.locator('.bin-detail-body table tbody tr').count() >= 5, await page.locator('.bin-detail-body table tbody tr').count());
ok('04b 激活记录操作人非 18 位 ID', !/\b\d{18}\b/.test(body));
const act = page.locator('.bin-detail-footer button').last();
if (await act.isVisible().catch(() => false)) { await act.click(); await page.waitForTimeout(800); await scan('06-activate-modal'); ok('06 激活弹窗打开（不提交）', await page.locator('.ant-modal:visible').count() === 1); await page.keyboard.press('Escape'); await page.waitForTimeout(400); }
if (width >= 600) {
  await page.locator('.bin-header-actions button').last().click(); await page.waitForTimeout(800);
  await scan('07-import-modal'); ok('07 导入弹窗打开（不上传）', await page.locator('.ant-modal:visible').count() === 1);
  await page.keyboard.press('Escape');
}
console.log(`DONE ${tag} fails=${fails}`);
await browser.close();
process.exit(fails ? 1 : 0);
