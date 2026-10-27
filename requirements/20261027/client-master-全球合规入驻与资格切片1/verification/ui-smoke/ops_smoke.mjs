// UI 冒烟（只读为主）：运营控制台 mor-test 新页面，en + zh-CN，1440px。
// 记录错误横幅、console 错误、失败请求（含 requestId）、原样 i18n key；所有 /api/ JSON 响应体存到 bodies/ 供解码器重放。
// 管理员凭据取 test-environment/private/access.md，不打印。用法：node ops_smoke.mjs [en|zh-CN ...] [--only=config,clients,codes,reviews] [--issue-code]
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = 'http://localhost:28080';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const args = process.argv.slice(2);
const LANGS = args.filter(a => !a.startsWith('--')).length ? args.filter(a => !a.startsWith('--')) : ['en', 'zh-CN'];
const ONLY = (args.find(a => a.startsWith('--only='))?.slice(7) ?? 'config,clients,codes,reviews').split(',');
const ISSUE = args.includes('--issue-code');
const NEW_CLIENT = '240686705999454209', LEGACY_CLIENT = '236087245667409921', CASE = '240686862329565184';
const CASES = process.env.CASES ? process.env.CASES.split(',') : ['240686862329565184', '240687064385966080', '240687127606710272', '239260421964550144'];

const text = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const ADMIN = { email: text.match(/账号[:：]\s*(\S+)/)[1], password: text.match(/密码[:：]\s*(\S+)/)[1] };
mkdirSync(join(HERE, 'screens'), { recursive: true }); mkdirSync(join(HERE, 'bodies'), { recursive: true });

const findings = []; let lang, page, current = 'start', bodyNo = 0;
const ERR = /暂时无法加载|服务暂时不可用|暂时不可用|INVALID_RESPONSE|temporarily unavailable|could not be loaded|Unable to load|Failed to load|加载失败|请求失败|Request failed/i;
const RAWKEY = /\b(onboarding-schema|onboardingSchema|client-review|master-data|nav|common|enum|onboarding|config|tab|detail)\.[A-Za-z][\w-]*(\.[\w-]+)+/g;
const note = (kind, detail) => { const f = { lang, page: current, kind, ...detail }; findings.push(f); console.log('FIND', JSON.stringify(f).slice(0, 500)); };
const settle = async (ms = 700) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
const slug = s => s.replace(/[^\w.\u4e00-\u9fff-]+/g, '_').slice(0, 90);
async function shot(name) { const f = `screens/ops-${lang}-${slug(name)}.png`; await page.screenshot({ path: join(HERE, f), fullPage: true }); return f; }
async function scan(name) {
  const body = await page.locator('body').innerText().catch(() => '');
  const errs = (body.match(new RegExp(`[^\\n]*(${ERR.source})[^\\n]*`, 'gi')) ?? []).map(s => s.trim().slice(0, 200));
  const keys = [...new Set(body.match(RAWKEY) ?? [])];
  const alerts = await page.locator('.ant-alert-error, .ant-result-error, .ant-result-warning, .ant-alert-warning').allInnerTexts().catch(() => []);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  let file;
  if (errs.length || keys.length || alerts.length) {
    file = await shot(name);
    if (errs.length) note('error-banner', { at: name, text: [...new Set(errs)], screenshot: file });
    if (alerts.length) note('alert', { at: name, text: alerts.map(a => a.replace(/\s+/g, ' ').slice(0, 200)), screenshot: file });
    if (keys.length) note('raw-i18n-key', { at: name, keys, screenshot: file });
  }
  if (overflow > 4) note('horizontal-overflow', { at: name, px: overflow });
  return { errs, keys, alerts };
}
async function goto(path, name) {
  current = name;
  for (let i = 0; i < 3; i++) {
    await page.goto(WEB + path); await settle(1200);
    if (await page.locator('.page-heading h1, .obw-page h1, h1').first().isVisible().catch(() => false)) break;
    await page.waitForTimeout(15000);
  }
  await scan(name); return shot(name);
}
async function clickSegments(scopeSel, prefix) {
  const items = page.locator(`${scopeSel} .ant-segmented-item`);
  const n = await items.count(); const labels = [];
  for (let i = 0; i < n; i++) {
    const label = (await items.nth(i).innerText()).trim().replace(/\s+/g, ' ');
    labels.push(label); current = `${prefix} › ${label}`;
    await items.nth(i).click(); await settle(900);
    await scan(current); await shot(current);
  }
  return labels;
}
async function selectAll(selectLocator, prefix, max = 60, each) {
  await selectLocator.click(); await page.waitForTimeout(300);
  const opts = await page.locator('.ant-select-dropdown:visible .ant-select-item-option').allInnerTexts();
  await page.keyboard.press('Escape');
  for (const [i, o] of opts.slice(0, max).entries()) {
    await selectLocator.click(); await page.waitForTimeout(250);
    const opt = page.locator('.ant-select-dropdown:visible .ant-select-item-option').nth(i);
    await opt.scrollIntoViewIfNeeded().catch(() => {}); await opt.click().catch(() => page.keyboard.press('Escape'));
    await settle(500); current = `${prefix} = ${o.trim()}`; await scan(current); if (each) await each(o, i);
  }
  return opts;
}

async function config() {
  await goto('/client-onboarding/config', 'config');
  const items = page.locator('.obc-item'); const n = await items.count();
  for (let j = 0; j < n; j++) {
    const code = (await items.nth(j).locator('.obc-code').innerText()).trim();
    if (!['AE', 'SA', 'EG'].includes(code) && n > 4) continue;
    await items.nth(j).click(); await settle(900);
    const segItems = page.locator('.obw-main .ant-segmented-item'); const sn = await segItems.count();
    for (let s = 0; s < sn; s++) {
      const label = (await segItems.nth(s).innerText()).trim(); current = `config ${code} › ${label}`;
      await segItems.nth(s).click(); await settle(900); await scan(current); await shot(current);
      // 采集 Schema / 目录 tab 的下拉：逐个选项检查
      const sel = page.locator('.obw-main .obw-card-title .ant-select').first();
      if (await sel.isVisible().catch(() => false)) await selectAll(sel, current, s === 0 ? 20 : 40, async (o, i) => { if (i < 3 || s === 0) await shot(`${current} = ${o}`); });
    }
  }
}

async function clients() {
  await goto('/master-data/clients', 'clients list');
  const search = page.locator('form, .ant-form').first();
  const selects = search.locator('.ant-select');
  const sn = await selects.count();
  for (let i = 0; i < sn; i++) {
    const before = await page.url();
    await selectAll(selects.nth(i), `clients filter#${i}`, 12, async () => {
      const btn = page.getByRole('button', { name: /^(Search|查\s*询)$/ }).first();
      if (await btn.isVisible().catch(() => false)) { await btn.click(); await settle(800); await scan(current); }
    });
    const reset = page.getByRole('button', { name: /^(Reset|重\s*置)$/ }).first();
    if (await reset.isVisible().catch(() => false)) { await reset.click(); await settle(600); }
    void before;
  }
  await shot('clients list after filters');
  for (const [id, tag] of [[NEW_CLIENT, 'new'], [LEGACY_CLIENT, 'legacy']]) {
    await goto(`/master-data/clients/${id}`, `client detail ${tag}`);
    await clickSegments('.console-content', `client detail ${tag}`);
    const tabs = page.locator('.console-content .ant-tabs-tab'); const tn = await tabs.count();
    for (let i = 0; i < tn; i++) { const l = (await tabs.nth(i).innerText()).trim(); current = `client detail ${tag} › ${l}`; await tabs.nth(i).click(); await settle(800); await scan(current); await shot(current); }
  }
}

async function codes() {
  await goto('/client-onboarding/codes', 'codes');
  await clickSegments('.console-content', 'codes');
  const selects = page.locator('.console-content .ant-select'); const n = await selects.count();
  for (let i = 0; i < Math.min(n, 2); i++) await selectAll(selects.nth(i), `codes select#${i}`, 10, async () => { await clickSegments('.console-content', current); });
  const issue = page.getByRole('button', { name: /Issue|签发|生成|新建/ }).first();
  if (await issue.isVisible().catch(() => false)) {
    await issue.click(); await page.waitForTimeout(800); current = 'codes issue dialog';
    await scan(current); await shot(current);
    if (ISSUE && lang === 'en') {
      const dlg = page.getByRole('dialog').last();
      await shot('codes issue dialog before submit');
      const ok = dlg.getByRole('button', { name: /^(Issue|Issue code|Confirm|OK|签\s*发|确\s*定)$/i }).last();
      await ok.click().catch(() => {}); await settle(1200); await scan('codes issue result'); await shot('codes issue result');
      await page.keyboard.press('Escape').catch(() => {});
    } else {
      await page.getByRole('dialog').last().getByRole('button', { name: /^(Cancel|取\s*消)$/ }).click().catch(() => page.keyboard.press('Escape'));
    }
  }
}

async function reviews() {
  await goto('/client-onboarding/reviews', 'reviews list');
  const selects = page.locator('.console-content form .ant-select'); const n = await selects.count();
  for (let i = 0; i < n; i++) {
    await selectAll(selects.nth(i), `reviews filter#${i}`, 12, async () => {
      const btn = page.getByRole('button', { name: /^(Search|查\s*询)$/ }).first();
      if (await btn.isVisible().catch(() => false)) { await btn.click(); await settle(800); await scan(current); }
    });
    const reset = page.getByRole('button', { name: /^(Reset|重\s*置)$/ }).first();
    if (await reset.isVisible().catch(() => false)) { await reset.click(); await settle(600); }
  }
  await shot('reviews list after filters');
  for (const id of CASES) {
    await goto(`/client-onboarding/reviews/${id}`, `case ${id}`);
    const labels = await clickSegments('.obw-main', `case ${id}`);
    // 打开每个分区内的对话框并取消
    if (id === CASE) for (let s = 0; s < labels.length; s++) {
      await page.locator('.obw-main .ant-segmented-item').nth(s).click(); await settle(700);
      const buttons = page.locator('.obw-main button:not([disabled])');
      const bn = await buttons.count();
      for (let b = 0; b < Math.min(bn, 12); b++) {
        const bt = (await buttons.nth(b).innerText().catch(() => '')).trim();
        if (!bt || /refresh|刷新|download|下载|preview|预览/i.test(bt)) continue;
        await buttons.nth(b).click().catch(() => {}); await page.waitForTimeout(700);
        const dlg = page.locator('.ant-modal:visible, .ant-drawer-open');
        if (await dlg.count()) {
          current = `case ${id} › ${labels[s]} › dialog ${bt}`; await scan(current); await shot(current);
          await page.getByRole('button', { name: /^(Cancel|取\s*消|Close|关\s*闭)$/ }).last().click().catch(() => page.keyboard.press('Escape'));
          await page.waitForTimeout(400);
        }
      }
    }
  }
}

const browser = await chromium.launch({ headless: true });
for (lang of LANGS) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true });
  await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, lang);
  page = await ctx.newPage();
  page.on('pageerror', e => note('pageerror', { text: String(e).slice(0, 300) }));
  page.on('console', m => { if (m.type() === 'error') note('console-error', { text: m.text().slice(0, 300) }); });
  page.on('response', async r => {
    const u = new URL(r.url()); if (u.pathname.startsWith('/api/') && /\/auth\//.test(u.pathname) && r.status() >= 400) note('http-failure-auth', { url: u.pathname, status: r.status() });
    if (!u.pathname.startsWith('/api/') || /\/auth\//.test(u.pathname)) return;
    const rid = r.headers()['x-request-id'] ?? r.headers()['request-id'];
    let body = ''; try { body = await r.text(); } catch {}
    const file = `bodies/${lang}-${String(++bodyNo).padStart(4, '0')}-${slug(u.pathname)}.json`;
    writeFileSync(join(HERE, file), JSON.stringify({ page: current, method: r.request().method(), url: u.pathname + u.search, status: r.status(), requestId: rid, body }, null, 1));
    if (r.status() >= 400) note('http-failure', { method: r.request().method(), url: u.pathname + u.search, status: r.status(), requestId: rid, body: body.slice(0, 300), file });
  });
  page.on('requestfailed', r => { if (!/abort/i.test(r.failure()?.errorText ?? '')) note('request-failed', { url: r.url(), error: r.failure()?.errorText }); });
  current = 'login';
  await page.goto(WEB + '/'); await settle();
  await page.getByLabel(/Email \/ initial account|Email \/ 初始化账号|邮箱|账号/).first().fill(ADMIN.email);
  await page.getByLabel(/^(Password|密码)$/).fill(ADMIN.password);
  await page.getByRole('button', { name: /^(Sign In|登\s*录)$/ }).click();
  await page.getByText(/Sign Out|退出/).first().waitFor({ timeout: 20000 });
  for (const [k, fn] of [['config', config], ['clients', clients], ['codes', codes], ['reviews', reviews]]) {
    if (!ONLY.includes(k)) continue;
    try { await fn(); } catch (e) { note('script-error', { section: k, text: String(e.message).split('\n')[0].slice(0, 300) }); await shot(`script-error-${k}`).catch(() => {}); }
  }
  await ctx.close();
}
await browser.close();
writeFileSync(join(HERE, `ops-findings-${LANGS.join('_')}-${ONLY.join('_')}.json`), JSON.stringify(findings, null, 1));
console.log('DONE findings=', findings.length);
