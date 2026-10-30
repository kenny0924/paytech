// 明细账运维 · mor-test 阶段 7 浏览器验收（runbook 第 4 节 V-4 / V-5 / V-6 / V-7 / V-8 的页面部分）。
// 改写自 verification/stage5/t6_browser.mjs（页面步骤沿用）：入口 http://localhost:28080（mor-test Nginx → Gateway → ops-bff，Web 与 API 同源，
// 语言包为 Platform 真实发布 #30，不改写任何响应）；去掉本机 i18n 模拟与 19400 CORS 补丁、去掉所有造数 / 停服务 / 删规则步骤。
// 账号：管理员（test-environment/private/access.md，经 e2e_http 同一来源读取）与合成只读账号（~/Code-MoR-backups/plops-stage7/accounts.json），不打印。
// 写操作（S-03 允许）：REBUILD=en-390 组合做一次"重建余额"（期望无需纠正）。V-4 空追平由 plops_mt.py catchup 经接口执行一次：
// 流已一致时页面不提供"追平"按钮，本脚本在 CATCHUP 组合只核对并截图这一点（只读）。其余组合只读。重建前后的余额逐行对比由 plops_mt.py snapshot / compare 完成。
// 用法：node plops_browser.mortest.mjs；RUNS='[["zh-CN",1440]]' 只跑部分组合；CATCHUP= / REBUILD= 置空可关闭写操作（干跑）。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = 'http://localhost:28080';
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const ro = JSON.parse(readFileSync(join(homedir(), 'Code-MoR-backups/plops-stage7/accounts.json'), 'utf8'));
const USERS = {
  admin: { email: access.match(/账号：(\S+)/)[1], password: access.match(/密码：(\S+)/)[1] },
  readonly: { email: ro.email, password: ro.password },
};
const OE_BOOK = '238137396670066688', FE_BOOK = '238324787049492480', STREAM = 'OE.AE001.USD.0';
const CATCHUP = process.env.CATCHUP ?? 'zh-CN-1440';
const REBUILD = process.env.REBUILD ?? 'en-390';
const PAUSE_MS = Number(process.env.PAUSE_MS ?? 15000);
const REGRESSION = ['/finance/subledger', '/finance/balances', '/finance/period-close', '/finance/payout/beneficiary-reviews', '/finance/payout/owners'];
// 文案断言用语言包 #30 的值（不是内置回退）：从 Platform 发布的 bundle 读取
const BUNDLE = {};

const log = { startedAt: new Date().toISOString(), runs: [], console: [], httpErrors: [], deliberate: [], posts: [], relogins: 0 };
const browser = await chromium.launch({ headless: true });
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function bundle(locale) {
  if (BUNDLE[locale]) return BUNDLE[locale];
  const m = await (await fetch(`${ORIGIN}/api/operations/i18n/v1/manifest?locale=${locale}`)).json();
  const out = { releaseNo: m.releaseNo };
  for (const ns of ['finance', 'nav', 'common']) {
    const url = m.bundles?.[locale]?.[ns]?.url;
    // minio.mor-test.localhost 用测试 CA；Node fetch 不信任，交给浏览器上下文读取
    out[ns] = url;
  }
  BUNDLE[locale] = out;
  return out;
}

for (const [locale, width] of runs) {
  const tag = `${locale}-${width}`; const desktop = width > 500;
  const run = { locale, width, steps: [] };
  const urls = await bundle(locale);
  let L = null;

  async function open(role) {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width, height: desktop ? 1000 : 844 }, locale });
    const page = await context.newPage();
    const ctx = { page, role, current: 'login', deliberate: null };
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, role, step: ctx.current, type: m.type(), text: m.text().slice(0, 300) }); });
    page.on('response', r => {
      const url = new URL(r.url());
      if (r.status() >= 400) (ctx.deliberate ? log.deliberate : log.httpErrors).push({ run: tag, role, step: ctx.current, why: ctx.deliberate ?? undefined, method: r.request().method(), path: url.pathname, status: r.status() });
      if (r.request().method() === 'POST' && url.pathname.includes('/finance/posting'))
        r.finished().then(() => log.posts.push({ run: tag, role, step: ctx.current, path: url.pathname, status: r.status(), ms: Math.round(r.request().timing().responseEnd) })).catch(() => {});
    });
    await login(ctx);
    if (!L) {
      // 在浏览器内读取语言包 #30（与页面同一来源与 CA 处理）
      L = await page.evaluate(async u => {
        const merged = {};
        for (const ns of ['finance', 'nav', 'common']) if (u[ns]) Object.assign(merged, await (await fetch(u[ns])).json());
        return merged;
      }, urls);
    }
    return ctx;
  }
  async function login(s) {
    await s.page.goto(`${ORIGIN}/?lang=${locale}`);
    await s.page.locator('input[type=password]').first().waitFor({ timeout: 30000 });
    await s.page.locator('input:not([type=password])').first().fill(USERS[s.role].email);
    await s.page.locator('input[type=password]').first().fill(USERS[s.role].password);
    await s.page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    await s.page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
    await settle(s.page);
  }
  const t = key => (L && (L[`finance.${key}`] ?? L[key])) ?? key;
  async function settle(page, ms = 1200) { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); }
  const facts = page => page.evaluate(() => ({
    rawKeys: [...new Set(document.body.innerText.match(/\b(finance|nav|common|payout)\.[A-Za-z]+[A-Za-z0-9_.]*/g) || [])].slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    scrollWidth: document.documentElement.scrollWidth,
    errorAlerts: document.querySelectorAll('.ant-alert-error, .ant-result-error, .ant-result-500, [role=alert].posting-ops-failure').length,
  }));
  const text = async (page, selector) => (await page.locator(selector).first().innerText({ timeout: 3000 }).catch(() => '')).replace(/\s+/g, ' ').slice(0, 700);
  async function shot(s, step, check) {
    s.current = step; await settle(s.page);
    const entry = { role: s.role, step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts(s.page);
    await s.page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1500));
  }
  // 偶发：整页跳转后回到登录页（阶段 5 已记录，原因未查明）；遇到时重新登录一次并记录
  const go = async (s, path) => {
    s.current = path;
    for (let attempt = 1; attempt <= 2; attempt++) {
      await s.page.goto(`${ORIGIN}${path}${path.includes('?') ? '&' : '?'}lang=${locale}`); await settle(s.page); await sleep(2200);
      if (!(await s.page.locator('input[type=password]').count())) return;
      log.relogins++; console.log(JSON.stringify({ run: tag, role: s.role, relogin: path }));
      await login(s);
    }
  };
  const buttons = s => s.page.locator('.posting-ops-page button:visible, .posting-ops-page a:visible').allInnerTexts().then(a => a.map(x => x.trim()).filter(Boolean));
  const menu = s => s.page.locator('.ant-menu').evaluateAll(els => els.map(el => el.textContent).join(' ').replace(/\s+/g, ' ')).catch(() => '');
  const btn = (s, label) => s.page.locator('button:visible, a:visible, [role=button]:visible', { hasText: label }).first();
  const progressView = s => async () => ({ segments: await s.page.locator('.posting-ops-segment').evaluateAll(els => els.map(e => e.className.replace('posting-ops-segment ', '') + ' | ' + e.innerText.replace(/\s+/g, ' ').slice(0, 160))),
    table: await text(s.page, '.posting-ops-table'), stuck: await text(s.page, '.posting-ops-stuck'), buttons: await buttons(s) });

  // ---------- 管理员：只读页面（V-1 / V-2 / V-3 页面 + V-7）
  const s = await open('admin');
  await go(s, '/finance/posting-ops');
  await s.page.locator('.posting-ops-book .ant-select').first().click().catch(() => {});
  await s.page.waitForTimeout(800);
  await shot(s, 'P7-idle', async () => ({ releaseNo: urls.releaseNo, menuHasPage: (await menu(s)).includes(L['nav.resource.OPERATIONS_FINANCE_POSTING_OPS']),
    rebuildLabelFromBundle: t('postingOps.rebuild.button'), rebuildDisabled: await btn(s, t('postingOps.rebuild.button')).isDisabled().catch(() => null),
    options: await s.page.locator('.posting-ops-book-option').allInnerTexts() }));
  await s.page.keyboard.press('Escape');
  await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
  await shot(s, 'P1-oe-consistent', progressView(s));
  await btn(s, t('postingOps.action.browse')).click();
  await settle(s.page); await sleep(1500);
  await shot(s, 'P6-browse', async () => ({ url: new URL(s.page.url()).pathname + new URL(s.page.url()).search, summary: await text(s.page, '.posting-ops-browse-summary'),
    rows: await s.page.locator('.posting-ops-page .table-panel tbody tr').count() }));
  const view = s.page.locator('.posting-ops-page a:visible, .posting-ops-page button:visible', { hasText: t('postingOps.stuck.viewEntries') }).first();
  if (await view.count()) { await view.click(); await settle(s.page); await shot(s, 'P6b-drawer', async () => ({ drawer: await text(s.page, '.ant-drawer-body') })); await s.page.keyboard.press('Escape'); await s.page.waitForTimeout(600); }
  await s.page.locator('.posting-ops-back').first().click().catch(() => {});
  await settle(s.page); await sleep(1500);
  await shot(s, 'P6c-back', async () => ({ url: new URL(s.page.url()).pathname + new URL(s.page.url()).search, keptBook: s.page.url().includes(OE_BOOK) }));
  await go(s, `/finance/posting-ops?ledgerBookId=${FE_BOOK}`);
  await shot(s, 'P9-fe-book', progressView(s));

  // ---------- V-4 页面部分（只读）：流已一致时不提供"追平"按钮；空追平本身由 plops_mt.py catchup 经接口执行一次
  if (CATCHUP === tag) {
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    run.catchUpButtonWhenConsistent = (await btn(s, t('postingOps.action.catchUp')).count()) > 0;
    await shot(s, 'V4-consistent-no-catchup-button', progressView(s));
  }

  // ---------- V-5：一次同值重建（只在 REBUILD 组合）
  if (REBUILD === tag) {
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await btn(s, t('postingOps.rebuild.button')).click();
    await s.page.waitForTimeout(800);
    await shot(s, 'P4-rebuild-confirm', async () => ({ modal: await text(s.page, '.ant-modal-content') }));
    await btn(s, t('postingOps.rebuild.confirm')).click();
    await s.page.locator('.posting-ops-result').first().waitFor({ timeout: 20000 }).catch(() => {});
    await settle(s.page); await sleep(1500);
    await shot(s, 'P5-rebuild-result', async () => ({ bar: await text(s.page, '.posting-ops-result'), ...(await progressView(s)()) }));
  }

  // ---------- V-8：回归页只读打开
  for (const path of REGRESSION) {
    await go(s, path);
    await shot(s, `V8${path.replaceAll('/', '-')}`, async () => ({ heading: await text(s.page, '.page-heading, main h1, main h2'), menuOk: (await menu(s)).length > 0 }));
  }
  await s.page.context().close();

  // ---------- V-6：合成只读账号
  const r = await open('readonly');
  await go(r, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
  await shot(r, 'P10-readonly', async () => ({ ...(await progressView(r)()), menuHasPage: (await menu(r)).includes(L['nav.resource.OPERATIONS_FINANCE_POSTING_OPS']),
    hasCatchUp: (await buttons(r)).some(b => b === t('postingOps.action.catchUp')), hasRebuild: (await buttons(r)).some(b => b.includes(t('postingOps.rebuild.button'))) }));
  await btn(r, t('postingOps.action.browse')).click().catch(() => {});
  await settle(r.page); await sleep(1500);
  await shot(r, 'P10b-readonly-browse', async () => ({ rows: await r.page.locator('.posting-ops-page .table-panel tbody tr').count() }));
  await r.page.context().close();

  log.runs.push(run);
  if (runs.indexOf(runs.find(x => x[0] === locale && x[1] === width)) < runs.length - 1) await sleep(PAUSE_MS);
}
await browser.close();
const stamp = new Date().toISOString().replace(/[:.]/g, '').slice(0, 15) + 'Z';
writeFileSync(join(HERE, `browser-result-${stamp}${process.env.OUT ?? ''}.json`), JSON.stringify(log, null, 2));
console.log('httpErrors', JSON.stringify(log.httpErrors), 'console', log.console.length, 'relogins', log.relogins, 'posts', JSON.stringify(log.posts));
