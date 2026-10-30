// REQ-04 阶段 7 mor-test 浏览器走查（⑮ 第 5 步）：http://localhost:28080（mor-test 运营入口，语言包同源，不改写任何响应）。
// 组合：zh-CN / en × 1440 / 390；账号 req / appr / elev / ro（~/Code-MoR-backups/rq04-stage7/accounts.json，不打印凭据）。
// 只读：除登录外不发写请求（脚本统计非 GET 的 /api/ 请求，登录 /auth/v1/ 除外）；不打开申请弹窗、不审批。
// 每个账号每组合登录一次，之后应用内跳转（pushState + popstate）；登录遇限流按退避重试；组合之间间隔 PAUSE_MS（默认 75 秒）。
// 截图 screens/<语言>-<宽度>-<账号>-<步骤>.png，结果 result-<组合>.json。用法：node stage7_browser.mjs；RUNS / PAUSE_MS 可选。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'http://localhost:28080';
const SHOTS = join(HERE, 'screens');
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(SHOTS, { recursive: true });

const login = JSON.parse(readFileSync(join(homedir(), 'Code-MoR-backups/rq04-stage7/accounts.json'), 'utf8'));
const C = { L: '240686705999454209', H: '241049681294106625', migrated: ['237573040244891649', '237573267559391233', '237573476041465857', '237575207592763393'] };
const RAWKEY = /\b(risk|nav|master-data|common|access)\.[A-Za-z_][\w-]*(\.[\w-]+)+/g;
// zh-CN 下界面文案（标题、表头、行标签、按钮、徽标、提示）里的英文单词；业务数据（客户名、邮箱、编号、理由）不在这些位置。
const UI_SELECTORS = ['.uw-head h1', '.uw-crumbs', '.uw-card-title', '.uw-row-label', '.uw-stat-label', '.ant-segmented-item', '.ant-table-thead th', '.uw-page button',
  '.uw-badge', '.lt-checklist .lt-check-text', '.lt-actions strong', '.uw-hint', '.lt-quota th', '.ant-alert-message, .ant-alert-title', '.ant-pagination-total-text', '.uw-guide', '.ant-menu, .console-nav, nav'];
const ALLOWED_LATIN = /^(CRR|LOW|MEDIUM|HIGH|UTC|USD|AED|SAR|EGP|[A-Z]{3}|T|v\d+|CL|E2E|APPROVED|ACTIVE|compliance|risk|commercial|Webhook|PAYTECH|Operations|Console|Hide|Navigation|Development)$/;
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];
const browser = await chromium.launch({ headless: true });

for (const [index, [locale, width]] of runs.entries()) {
  const tag = `${locale}-${width}`;
  const result = { locale, width, steps: [], console: [], httpErrors: [], writes: [], loginRetries: 0 };
  let current = 'start';
  async function settle(page, ms = 1000) { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); }
  async function session(who) {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale, viewport: { width, height: width < 600 ? 844 : 1000 }, isMobile: width < 600, hasTouch: width < 600 });
    await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
    const page = await ctx.newPage();
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) result.console.push({ step: current, type: m.type(), text: m.text().slice(0, 300) }); });
    page.on('pageerror', e => result.console.push({ step: current, type: 'pageerror', text: String(e).slice(0, 300) }));
    page.on('response', r => { const u = new URL(r.url()); if (r.status() >= 400) { result.httpErrors.push({ step: current, method: r.request().method(), path: u.pathname, status: r.status() }); console.log('HTTP', r.status(), r.request().method(), u.pathname, current); } });
    page.on('request', r => { const u = new URL(r.url()); if (r.method() !== 'GET' && u.pathname.startsWith('/api/') && !u.pathname.includes('/auth/v1/')) result.writes.push({ step: current, method: r.method(), path: u.pathname }); });
    current = 'login ' + who;
    // 同账号登录限流：失败时按 30 / 60 / 90 秒退避，最多 4 次
    for (let attempt = 0; attempt < 4; attempt++) {
      await page.goto(`${OPS}/?lang=${locale}`);
      await page.locator('input[type=password]').first().waitFor({ timeout: 30000 });
      await settle(page, 1500);
      await page.locator('input:not([type=password])').first().fill(login[who].email);
      await page.locator('input[type=password]').first().fill(login[who].password);
      await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
      const ok = await page.waitForFunction(() => !document.querySelector('input[type=password]'), null, { timeout: 30000 }).then(() => true).catch(() => false);
      if (ok) break;
      result.loginRetries++; console.log('login retry', who, attempt + 1); await page.waitForTimeout(30000 * (attempt + 1));
    }
    await settle(page);
    return { ctx, page, who };
  }
  const facts = page => page.evaluate(({ raw, selectors, zh, allowed }) => {
    const words = new Set();
    if (zh) for (const el of document.querySelectorAll(selectors.join(','))) for (const w of (el.innerText.match(/[A-Za-z][A-Za-z+'-]{1,}/g) || [])) if (!new RegExp(allowed).test(w)) words.add(w);
    return { rawKeys: [...new Set(document.body.innerText.match(new RegExp(raw, 'g')) || [])].filter(k => !/\.(ae|test|com)$/.test(k)).slice(0, 10),
      horizontalScroll: document.documentElement.scrollWidth - document.documentElement.clientWidth, englishInZh: [...words].slice(0, 20) };
  }, { raw: RAWKEY.source, selectors: UI_SELECTORS, zh: locale.startsWith('zh'), allowed: ALLOWED_LATIN.source });
  async function shot(s, step, check) {
    current = `${s.who}:${step}`;
    const entry = { step: current };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts(s.page);
    await s.page.screenshot({ path: join(SHOTS, `${tag}-${s.who}-${step}.png`), fullPage: true });
    result.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1500));
  }
  const go = async (s, path) => { current = `${s.who}:goto ${path}`;
    const push = p => s.page.evaluate(x => { window.history.pushState({}, '', x); window.dispatchEvent(new PopStateEvent('popstate')); }, p);
    if (path.startsWith('/risk/limit-tiers/')) { await push('/risk/limit-tiers/0'); await s.page.waitForTimeout(200); }
    await push(path); await settle(s.page, 1500);
    if (path.startsWith('/risk/limit-tiers')) await s.page.locator('.uw-card, .ant-alert, .ant-table').first().waitFor({ timeout: 20000 }).catch(() => {});
    await s.page.waitForTimeout(500); };
  const clean = t => t.replace(/\s+/g, ' ').trim();
  const texts = (s, selector) => s.page.locator(selector).allInnerTexts().then(list => list.map(clean));
  const buttons = s => s.page.evaluate(() => [...document.querySelectorAll('.uw-page button')].map(b => ({ text: b.innerText.replace(/\s+/g, ' ').trim(), disabled: b.disabled })).filter(b => b.text));
  // 侧栏菜单项：取"限额档位 / Limit tiers"所在菜单项及其所属目录的文字
  const menu = s => s.page.evaluate(() => [...document.querySelectorAll('.ant-menu-item, [role=menuitem]')].map(el => ({ item: el.innerText.trim(),
    parent: el.closest('.ant-menu-submenu')?.querySelector('.ant-menu-submenu-title')?.innerText.trim() ?? null })).filter(x => /限额档位|Limit tiers/.test(x.item)));
  const detail = s => async () => s.page.evaluate(() => {
    const c = t => (t ?? '').replace(/\s+/g, ' ').trim();
    return { title: c(document.querySelector('.uw-title')?.innerText), meta: c(document.querySelector('.uw-meta')?.innerText),
      cards: [...document.querySelectorAll('.uw-card-title h2')].map(h => c(h.innerText)),
      quotas: [...document.querySelectorAll('.lt-quota tbody tr')].map(tr => c(tr.innerText)), fullBars: document.querySelectorAll('.lt-bar.is-full').length,
      checklist: [...document.querySelectorAll('.lt-checklist li')].map(li => c(li.innerText)), actions: [...document.querySelectorAll('.lt-actions li strong')].map(x => c(x.innerText)),
      alerts: [...document.querySelectorAll('.ant-alert')].map(a => c(a.innerText)), history: [...document.querySelectorAll('.lt-history li')].map(li => c(li.innerText)),
      buttons: [...document.querySelectorAll('.uw-page button')].map(b => ({ text: c(b.innerText), disabled: b.disabled })).filter(b => b.text) };
  });
  const list = s => async () => ({ menu: await menu(s), stats: await texts(s, '.uw-stat'), tabs: await texts(s, '.uw-toolbar .ant-segmented-item'), rows: await texts(s, '.ant-table-row'),
    actions: await texts(s, '.ant-table-row .uw-link-button'), total: await s.page.locator('.ant-pagination-total-text').innerText().catch(() => '') });

  try {
    for (const who of ['req', 'appr', 'elev', 'ro']) {
      const s = await session(who);
      await go(s, '/risk/limit-tiers');
      await shot(s, 'L1-list', list(s));
      if (who === 'req') {
        await go(s, '/risk/limit-tiers?tier=STANDARD&page=1&size=20'); await shot(s, 'L2-tab-standard', async () => ({ rows: await texts(s, '.ant-table-row') }));
        await go(s, '/risk/limit-tiers?tier=VERIFICATION&page=1&size=20'); await shot(s, 'L3-tab-verification', async () => ({ rows: await texts(s, '.ant-table-row'), actions: await texts(s, '.ant-table-row .uw-link-button') }));
        await go(s, '/risk/limit-tiers?observationDue=true&page=1&size=20'); await shot(s, 'L4-observation-due', async () => ({ rows: (await texts(s, '.ant-table-row')).length, empty: await s.page.locator('.ant-empty').count() }));
        await go(s, '/risk/limit-tiers?pendingApproval=true&page=1&size=20'); await shot(s, 'L5-pending', async () => ({ rows: (await texts(s, '.ant-table-row')).length, empty: await s.page.locator('.ant-empty').count() }));
        await go(s, `/risk/limit-tiers?keyword=${C.migrated[0]}&page=1&size=20`); await shot(s, 'L6-search-id', async () => ({ rows: await texts(s, '.ant-table-row') }));
        await go(s, `/risk/limit-tiers/${C.migrated[0]}`); await shot(s, 'P06-migrated', detail(s));
      }
      await go(s, `/risk/limit-tiers/${C.L}`); await shot(s, 'P02-L-verification', detail(s));
      await go(s, `/risk/limit-tiers/${C.H}`); await shot(s, 'P04-H', detail(s));
      await s.ctx.close();
    }
  } catch (error) { result.failure = String(error).slice(0, 400); console.log('FAILED', result.failure, current); }
  writeFileSync(join(HERE, `result-${tag}.json`), JSON.stringify(result, null, 2));
  console.log(JSON.stringify({ run: tag, steps: result.steps.length, console: result.console.length, httpErrors: result.httpErrors, writes: result.writes, loginRetries: result.loginRetries }));
  if (index < runs.length - 1) await new Promise(r => setTimeout(r, Number(process.env.PAUSE_MS ?? 75000)));
}
await browser.close();
