// 明细账运维 · 阶段 5 浏览器走查（真实 Chromium，Playwright）。
// 入口 http://127.0.0.1:18317（私有 Vite，代码 Code-MoR-plops-e2e/operations-console-web；/api/operations → Nginx 18444 → Gateway → ops-bff）。
// 本机 Platform 语言包 #20 只有 en 且不含本需求文案：在浏览器内改写 manifest（模拟阶段 6 发布）——en 替换 finance / nav，
// zh-CN 全部命名空间取工作区 i18n/zh-CN/*.json。19400 公共桶不对 18317 放行 CORS，在浏览器内补 access-control-allow-origin。
// 凭据只在内存读取（secrets.env、t6-state.json），不打印。
// 用法：GROUPS=base,readonly,rebuild,segments,catchup RUNS='[["zh-CN",1440]]' node t6_browser.mjs
//   rebuild / segments / catchup 是写操作或停服务，只在 WRITE 指定的组合执行（默认 zh-CN-1440,en-390）。
import { createRequire } from 'node:module';
import { execFileSync, spawn } from 'node:child_process';
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = 'http://127.0.0.1:18317';
const SHOTS = join(HERE, 'screenshots');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const readEnv = path => Object.fromEntries(readFileSync(path, 'utf8').split('\n').filter(l => l.includes('=') && !l.startsWith('#'))
  .map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const secrets = readEnv('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env');
const state = JSON.parse(readFileSync(join(homedir(), 'Code-MoR-backups/plops-stage5-t6/state.json'), 'utf8'));
const payout = JSON.parse(readFileSync(join(homedir(), 'Code-MoR-backups/payout-s1-stage5/state.json'), 'utf8'));
const USERS = {
  admin: { email: secrets.E2E_ADMIN_EMAIL, password: secrets.E2E_ADMIN_PASSWORD },
  readonly: { email: state.readonly.email, password: state.readonly.password },
  noperm: { email: payout.operators.noperm.email, password: payout.operators.noperm.password },
};
const OE_BOOK = state.OE.bookId, FE_BOOK = state.FE.bookId, STREAM = `OE.${state.entityNo}.USD.0`;
const WEB = '/Users/kenny/Desktop/Workspace/Code-MoR-plops-e2e/operations-console-web';
const SIM = { en: { finance: `${WEB}/src/i18n/en/finance.json`, nav: `${WEB}/src/i18n/en/nav.json` }, 'zh-CN': {} };
for (const f of readdirSync(`${WEB}/i18n/zh-CN`)) if (f.endsWith('.json')) SIM['zh-CN'][f.replace(/\.json$/, '')] = `${WEB}/i18n/zh-CN/${f}`;
const L = Object.fromEntries(['en', 'zh-CN'].map(code => [code, {
  ...JSON.parse(readFileSync(code === 'en' ? `${WEB}/src/i18n/en/finance.json` : `${WEB}/i18n/zh-CN/finance.json`, 'utf8')),
  ...JSON.parse(readFileSync(code === 'en' ? `${WEB}/src/i18n/en/common.json` : `${WEB}/i18n/zh-CN/common.json`, 'utf8')),
  ...JSON.parse(readFileSync(code === 'en' ? `${WEB}/src/i18n/en/nav.json` : `${WEB}/i18n/zh-CN/nav.json`, 'utf8')),
}]));
const GROUPS = (process.env.GROUPS ?? 'base,readonly').split(',');
const WRITE = (process.env.WRITE ?? 'zh-CN-1440,en-390').split(',');
const PREFIX = process.env.PREFIX ?? '';
const RUN_STACK = join(HERE, 'run-stack.sh');

const log = { startedAt: new Date().toISOString(), groups: GROUPS, runs: [], console: [], httpErrors: [], deliberate: [], posts: [] };
const browser = await chromium.launch({ headless: true });
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];
const sleep = ms => new Promise(r => setTimeout(r, ms));
const py = (code) => execFileSync('python3', ['-c', `import sys; sys.path.insert(0, ${JSON.stringify(HERE)}); import t6lib as L\n${code}`], { cwd: HERE, encoding: 'utf8' }).trim();
const stack = (verb, svc) => execFileSync('bash', [RUN_STACK, verb, svc], { encoding: 'utf8' }).trim().split('\n').slice(-1)[0];

for (const [locale, width] of runs) {
  const tag = `${locale}-${width}`; const desktop = width > 500; const write = WRITE.includes(tag);
  const t = key => L[locale][`finance.${key}`] ?? L[locale][key] ?? key;
  const run = { locale, width, steps: [] };

  async function open(role) {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width, height: desktop ? 1000 : 844 }, locale });
    await context.route(/^https:\/\/127\.0\.0\.1:19400\//, async route => {
      const sim = route.request().url().match(/\/plops-sim\/([^/]+)\/([a-z0-9-]+)\.json$/);
      if (sim) return route.fulfill({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': ORIGIN }, body: readFileSync(SIM[sim[1]][sim[2]], 'utf8') });
      const response = await route.fetch();
      await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': ORIGIN } });
    });
    await context.route(/\/api\/operations\/i18n\/v1\/manifest/, async route => {
      const response = await route.fetch(); const manifest = await response.json();
      manifest.bundles = manifest.bundles ?? {};
      for (const code of Object.keys(SIM)) {
        manifest.bundles[code] = manifest.bundles[code] ?? {};
        for (const ns of Object.keys(SIM[code])) manifest.bundles[code][ns] = { hash: `plopssim-${code}-${ns}`, url: `https://127.0.0.1:19400/plops-sim/${code}/${ns}.json` };
      }
      await route.fulfill({ response, body: JSON.stringify(manifest) });
    });
    const page = await context.newPage();
    const ctx = { page, role, current: 'login', deliberate: null };
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, role, step: ctx.current, type: m.type(), text: m.text().slice(0, 300) }); });
    page.on('response', r => {
      const url = new URL(r.url());
      if (r.status() >= 400) (ctx.deliberate ? log.deliberate : log.httpErrors).push({ run: tag, role, step: ctx.current, why: ctx.deliberate ?? undefined, method: r.request().method(), path: url.pathname, status: r.status() });
      if (r.request().method() === 'POST' && url.pathname.includes('/finance/posting')) r.finished().then(() => log.posts.push({ run: tag, role, step: ctx.current, path: url.pathname, status: r.status(), ms: Math.round(r.request().timing().responseEnd) })).catch(() => {});
    });
    await page.goto(`${ORIGIN}/?lang=${locale}`);
    await page.locator('input[type=password]').first().waitFor({ timeout: 30000 });
    await page.locator('input:not([type=password])').first().fill(USERS[role].email);
    await page.locator('input[type=password]').first().fill(USERS[role].password);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
    await settle(page);
    return ctx;
  }
  async function settle(page, ms = 1200) { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); }
  const facts = page => page.evaluate(() => ({
    rawKeys: [...new Set(document.body.innerText.match(/\b(finance|nav|common)\.[A-Za-z]+[A-Za-z0-9_.]*/g) || [])].slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  const text = async (page, selector) => (await page.locator(selector).first().innerText({ timeout: 3000 }).catch(() => '')).replace(/\s+/g, ' ').slice(0, 700);
  async function shot(s, step, check) {
    s.current = step; await settle(s.page);
    const entry = { role: s.role, step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts(s.page);
    await s.page.screenshot({ path: join(SHOTS, `${PREFIX}${tag}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1500));
  }
  // 偶发：登录成功后整页跳转回到登录页（原因未查明，Nginx 无 401 / 429）；遇到时重新登录一次并记录
  const go = async (s, path) => {
    s.current = path;
    for (let attempt = 1; attempt <= 2; attempt++) {
      await s.page.goto(`${ORIGIN}${path}${path.includes('?') ? '&' : '?'}lang=${locale}`); await settle(s.page); await sleep(2200);
      if (!(await s.page.locator('input[type=password]').count())) return;
      log.relogins = (log.relogins ?? 0) + 1; console.log(JSON.stringify({ run: tag, role: s.role, relogin: path }));
      await s.page.locator('input:not([type=password])').first().fill(USERS[s.role].email);
      await s.page.locator('input[type=password]').first().fill(USERS[s.role].password);
      await s.page.locator('button[type=submit], form button.ant-btn-primary').first().click();
      await s.page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {}); await settle(s.page);
    }
  };
  const buttons = s => s.page.locator('.posting-ops-page button:visible, .posting-ops-page a:visible').allInnerTexts().then(a => a.map(x => x.trim()).filter(Boolean));
  const menu = s => s.page.locator('.ant-menu').evaluateAll(els => els.map(el => el.textContent).join(' ').replace(/\s+/g, ' ')).catch(() => '');
  const btn = (s, label) => s.page.locator('button:visible, a:visible, [role=button]:visible', { hasText: label }).first();
  const progressView = s => async () => ({ segments: await s.page.locator('.posting-ops-segment').evaluateAll(els => els.map(e => e.className.replace('posting-ops-segment ', '') + ' | ' + e.innerText.replace(/\s+/g, ' ').slice(0, 160))),
    stuck: await text(s.page, '.posting-ops-stuck'), buttons: await buttons(s) });

  if (GROUPS.includes('base')) {
    const s = await open('admin');
    await go(s, '/finance/posting-ops');
    await s.page.locator('.posting-ops-book .ant-select').first().click().catch(() => {});
    await s.page.waitForTimeout(800);
    await shot(s, 'P7-idle', async () => ({ empty: await text(s.page, '.posting-ops-empty'), menuHasPage: (await menu(s)).includes(t('nav.resource.OPERATIONS_FINANCE_POSTING_OPS')),
      rebuildDisabled: await btn(s, t('postingOps.rebuild.button')).isDisabled().catch(() => null),
      options: await s.page.locator('.posting-ops-book-option').allInnerTexts() }));
    await s.page.keyboard.press('Escape');
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await shot(s, 'P2-oe-behind', progressView(s));
    // P6 批次浏览：从流行进入，打开批次抽屉，再返回
    await btn(s, t('postingOps.action.browse')).click();
    await settle(s.page); await sleep(1500);
    await shot(s, 'P6-browse', async () => ({ url: new URL(s.page.url()).pathname + new URL(s.page.url()).search, summary: await text(s.page, '.posting-ops-browse-summary'),
      rows: await s.page.locator('.posting-ops-page .ant-table-row, .posting-ops-page .table-panel tbody tr').count() }));
    const view = s.page.locator('.posting-ops-page a:visible, .posting-ops-page button:visible', { hasText: t('postingOps.stuck.viewEntries') }).first();
    if (await view.count()) { await view.click(); await settle(s.page); await shot(s, 'P6b-drawer', async () => ({ drawer: await text(s.page, '.ant-drawer-body') })); await s.page.keyboard.press('Escape'); await s.page.waitForTimeout(600); }
    await s.page.locator('.posting-ops-back').first().click().catch(() => {});
    await settle(s.page); await sleep(1500);
    await shot(s, 'P6c-back', async () => ({ url: new URL(s.page.url()).pathname + new URL(s.page.url()).search, keptBook: s.page.url().includes(OE_BOOK) }));
    await go(s, `/finance/posting-ops?ledgerBookId=${FE_BOOK}`);
    await shot(s, 'P9-fe-book', async () => ({ ...(await progressView(s)()), table: await text(s.page, '.posting-ops-table') }));
    await s.page.context().close();
  }

  if (GROUPS.includes('readonly')) {
    const s = await open('readonly');
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await shot(s, 'P10-readonly', async () => ({ ...(await progressView(s)()), locked: await text(s.page, '.posting-ops-locked'),
      hasCatchUp: (await buttons(s)).some(b => b === t('postingOps.action.catchUp')), hasRebuild: (await buttons(s)).some(b => b.includes(t('postingOps.rebuild.button'))) }));
    await s.page.context().close();
    const n = await open('noperm');
    await go(n, '/finance/posting-ops');
    await shot(n, 'R-noperm', async () => ({ menuHasPage: (await menu(n)).includes(t('nav.resource.OPERATIONS_FINANCE_POSTING_OPS')), body: await text(n.page, 'main') }));
    await n.page.context().close();
  }

  if (GROUPS.includes('rebuild') && write) {
    const s = await open('admin');
    const before = py(`print(L.q('mor_account_v2e2e', "SELECT net_balance FROM t_owner_account WHERE entity_id='${state.entityNo}' AND account_code='CLIENT_PAYABLE' ORDER BY id LIMIT 1")[0][0])`);
    py(`L.q('mor_account_v2e2e', "UPDATE t_owner_account SET credit_total=credit_total+5, net_balance=net_balance+5 WHERE entity_id='${state.entityNo}' AND account_code='CLIENT_PAYABLE' ORDER BY id LIMIT 1")`);
    log.posts.push({ run: tag, note: `SQL 篡改 ${state.entityNo} CLIENT_PAYABLE +5（之前 ${before}）` });
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await btn(s, t('postingOps.rebuild.button')).click();
    await s.page.waitForTimeout(800);
    await shot(s, 'P4-rebuild-confirm', async () => ({ modal: await text(s.page, '.ant-modal-content') }));
    await btn(s, t('postingOps.rebuild.confirm')).click();
    await s.page.locator('.posting-ops-result').first().waitFor({ timeout: 20000 }).catch(() => {});
    await settle(s.page); await sleep(1500);
    const after = py(`print(L.q('mor_account_v2e2e', "SELECT net_balance FROM t_owner_account WHERE entity_id='${state.entityNo}' AND account_code='CLIENT_PAYABLE' ORDER BY id LIMIT 1")[0][0])`);
    await shot(s, 'P5-rebuild-corrected', async () => ({ bar: await text(s.page, '.posting-ops-result'), balanceBefore: before, balanceAfter: after }));
    await sleep(6500); // 重建路由每人每分钟 10 次
    await btn(s, t('postingOps.rebuild.button')).click(); await s.page.waitForTimeout(600);
    await btn(s, t('postingOps.rebuild.confirm')).click(); await settle(s.page); await sleep(1500);
    await shot(s, 'P5b-rebuild-unchanged', async () => ({ bar: await text(s.page, '.posting-ops-result') }));
    await s.page.context().close();
  }

  if (GROUPS.includes('segments') && write) {
    const s = await open('admin');
    s.deliberate = 'billing 停止';
    log.posts.push({ run: tag, note: 'stop billing: ' + stack('stop', 'billing') });
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await shot(s, 'P8-billing-down', progressView(s));
    log.posts.push({ run: tag, note: 'start billing: ' + stack('start', 'billing') });
    await btn(s, t('common.retry')).click().catch(() => {}); await settle(s.page); await sleep(1500);
    await shot(s, 'P8b-billing-retry', progressView(s));
    s.deliberate = 'account 停止';
    log.posts.push({ run: tag, note: 'stop account: ' + stack('stop', 'account') });
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await shot(s, 'P8c-account-down', progressView(s));
    log.posts.push({ run: tag, note: 'start account: ' + stack('start', 'account') });
    s.deliberate = 'ledger 停止';
    log.posts.push({ run: tag, note: 'stop ledger: ' + stack('stop', 'ledger') });
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await shot(s, 'P8d-ledger-down', async () => ({ failure: await text(s.page, '.posting-ops-failure'), book: await text(s.page, '.posting-ops-book') }));
    log.posts.push({ run: tag, note: 'start ledger: ' + stack('start', 'ledger') });
    s.deliberate = null;
    await btn(s, t('common.retry')).click().catch(() => {}); await settle(s.page); await sleep(1500);
    await shot(s, 'P8e-ledger-retry', progressView(s));
    await s.page.context().close();
  }

  if (GROUPS.includes('d2') && write) {
    // D-2 残留：已正常打开过账簿的标签页里停 ledger → 刷新；再开新标签页（ledger 从未可用）→ 已知限制
    const s = await open('admin');
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await shot(s, 'D2a-before', async () => ({ book: await text(s.page, '.posting-ops-book') }));
    s.deliberate = 'ledger 停止';
    log.posts.push({ run: tag, note: 'stop ledger: ' + stack('stop', 'ledger') });
    await btn(s, t('common.refresh')).click(); await settle(s.page); await sleep(1500);
    await shot(s, 'D2b-same-tab-ledger-down', async () => ({ book: await text(s.page, '.posting-ops-book'), failure: await text(s.page, '.posting-ops-failure'),
      toasts: await s.page.locator('.ant-notification-notice:visible').count() }));
    const fresh = await s.page.context().newPage(); const f = { ...s, page: fresh };
    await go(f, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await shot(f, 'D2c-new-tab-ledger-down', async () => ({ book: await text(fresh, '.posting-ops-book'), failure: await text(fresh, '.posting-ops-failure'),
      toasts: await fresh.locator('.ant-notification-notice:visible').count() }));
    log.posts.push({ run: tag, note: 'start ledger: ' + stack('start', 'ledger') });
    s.deliberate = null;
    await btn(s, t('common.retry')).click().catch(() => {}); await settle(s.page); await sleep(1500);
    await shot(s, 'D2d-retry', progressView(s));
    await s.page.context().close();
  }

  if (GROUPS.includes('catchup') && write) {
    // 由 t6_lag.py 预先造好落后（可选规则拒绝）；这里只点按钮、截图结果
    const s = await open('admin');
    await go(s, `/finance/posting-ops?ledgerBookId=${OE_BOOK}`);
    await shot(s, 'C1-behind', progressView(s));
    for (let i = 1; i <= Number(process.env.CATCHUP_CLICKS ?? 1); i++) {
      const next = btn(s, t('postingOps.catchUp.continue'));
      const again = btn(s, t('postingOps.catchUp.retry'));
      const trigger = (await next.count()) ? next : (await again.count()) ? again : btn(s, t('postingOps.action.catchUp'));
      if (!(await trigger.count())) break;
      if (process.env.BEFORE_CLICK) { spawn('bash', ['-c', process.env.BEFORE_CLICK], { cwd: HERE, stdio: 'ignore', detached: true }).unref(); await sleep(Number(process.env.BEFORE_CLICK_WAIT ?? 1500)); }
      await trigger.click(); await s.page.waitForTimeout(700);
      const ok = s.page.locator('.ant-modal-confirm-btns button.ant-btn-primary:visible');
      if (await ok.count()) await ok.click();
      await s.page.locator('.posting-ops-result, .posting-ops-rejected').first().waitFor({ timeout: 20000 }).catch(() => {});
      await settle(s.page, 2500); await sleep(1500);
      await shot(s, `C2-after-catchup-${i}`, async () => ({ ...(await progressView(s)()), result: await text(s.page, '.posting-ops-result'), rejected: await text(s.page, '.posting-ops-rejected') }));
      if (i === 1 && process.env.AFTER_FIRST) log.posts.push({ run: tag, note: 'AFTER_FIRST: ' + execFileSync('bash', ['-c', process.env.AFTER_FIRST], { cwd: HERE, encoding: 'utf8' }).trim() });
      await sleep(6500);
    }
    await s.page.context().close();
  }
  log.runs.push(run);
}
await browser.close();
writeFileSync(join(HERE, `t6-browser-result${process.env.OUT ?? ''}.json`), JSON.stringify(log, null, 2));
console.log('httpErrors', JSON.stringify(log.httpErrors), 'console', log.console.length);
