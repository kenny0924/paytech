// REQ-03 阶段 7 mor-test 浏览器走查（runbook §5 第 5 项）：http://localhost:28080（语言包同源，不改写响应）。
// 组合：zh-CN / en × 1440 / 390。截图 screens/<语言>-<宽度>-<账号>-<步骤>.png，结果 result-<账号>-<组合>.json。
// 只读：不建 / 改承保记录，修订弹窗打开后取消；允许 U11 预览 POST。lead 本人复核禁用视角当前无待复核数据，由 API 验收与阶段 5 覆盖。
// 凭据：uw / lead / ro 读 ~/Code-MoR-backups/rq03-stage7/accounts.json，管理员读 test-environment/private/access.md；均不打印。
// 用法：node stage7_browser.mjs；RUNS / ACCOUNTS / PAUSE_MS 可选。
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

const accounts = JSON.parse(readFileSync(join(homedir(), 'Code-MoR-backups/rq03-stage7/accounts.json'), 'utf8'));
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const field = name => access.split('\n').find(line => line.startsWith(name + '：')).slice(name.length + 1).trim();
const login = { uw: accounts.uw, lead: accounts.lead, ro: accounts.ro, admin: { email: field('账号'), password: field('密码') } };
const C = { L: '240686705999454209', H: '241049681294106625', M: '239260386824658945' };
const U = { L_v1: '241635428858433536', L_v2: '241635435409936384', H_v1: '241635439478411264', H_v2: '241635444230557696', M_v1: '241635463566299136' };
const CASE = { L: '240686862329565184', H: '241049829650845696' };
const RAWKEY = /\b(risk|nav|master-data|client-review|common|workflow|commercial|access)\.[A-Za-z_][\w-]*(\.[\w-]+)+/g;
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];

const browser = await chromium.launch({ headless: true });
for (const [locale, width] of runs) {
  const tag = `${locale}-${width}`;
  const result = { locale, width, steps: [], console: [], httpErrors: [], writes: [], bundles: 0 };
  let current = 'start';

  async function session(who) {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale, viewport: { width, height: width < 600 ? 844 : 1000 }, isMobile: width < 600, hasTouch: width < 600 });
    await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
    const page = await ctx.newPage();
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) result.console.push({ step: current, type: m.type(), text: m.text().slice(0, 300) }); });
    page.on('pageerror', e => result.console.push({ step: current, type: 'pageerror', text: String(e).slice(0, 300) }));
    page.on('response', r => {
      const url = new URL(r.url());
      if (/i18n|bundle/.test(url.pathname) && r.request().method() === 'GET') result.bundles++;
      if (r.status() >= 400) { result.httpErrors.push({ step: current, method: r.request().method(), path: url.pathname, status: r.status() }); console.log('HTTP', r.status(), r.request().method(), url.pathname, current); }
    });
    page.on('request', r => { const u = new URL(r.url()); if (r.method() !== 'GET' && u.pathname.startsWith('/api/') && !u.pathname.includes('/auth/v1/')) result.writes.push({ step: current, method: r.method(), path: u.pathname }); });
    current = 'login ' + who;
    await page.goto(`${OPS}/?lang=${locale}`);
    // 语言包到达会重新渲染登录表单：等表单稳定后再填，仍停在登录页时重试一次
    await page.locator('input[type=password]').first().waitFor({ timeout: 30000 });
    for (let attempt = 0; attempt < 2 && await page.locator('input[type=password]').count(); attempt++) {
      await settle(page, 1500);
      await page.locator('input:not([type=password])').first().fill(login[who].email);
      await page.locator('input[type=password]').first().fill(login[who].password);
      await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
      await page.waitForFunction(() => !document.querySelector('input[type=password]'), null, { timeout: 30000 }).catch(() => {});
    }
    await settle(page);
    return { ctx, page, who };
  }
  async function settle(page, ms = 1000) { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); }
  const facts = page => page.evaluate((raw) => ({
    rawKeys: [...new Set(document.body.innerText.match(new RegExp(raw, 'g')) || [])].filter(k => !/\.(ae|test|com)$/.test(k)).slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth - document.documentElement.clientWidth,
  }), RAWKEY.source);
  async function shot(s, step, check) {
    current = `${s.who}:${step}`;
    const entry = { step: current };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts(s.page);
    await s.page.screenshot({ path: join(SHOTS, `${tag}-${s.who}-${step}.png`), fullPage: true });
    result.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1200));
  }
  // 应用内跳转（pushState + popstate）：整页重载会重复恢复会话与导航，连续走查会触发 Gateway 限流（GATEWAY_RATE_LIMITED）。
  const go = async (s, path) => { current = `${s.who}:goto ${path}`;
    await s.page.evaluate(p => { window.history.pushState({}, '', p); window.dispatchEvent(new PopStateEvent('popstate')); }, path); await settle(s.page, 1500);
    // 承保页依赖 ops-bff 聚合（最长约 3 秒）：等到页面内容或失败提示出现再截图
    if (path.startsWith('/client-onboarding/reviews/')) await s.page.locator('.obw-head, .ant-alert').first().waitFor({ timeout: 20000 }).catch(() => {});
    if (path.startsWith('/master-data/clients/')) await s.page.locator('.master-data-page, .ant-alert').first().waitFor({ timeout: 20000 }).catch(() => {});
    if (path.startsWith('/risk/underwriting')) await s.page.locator('.uw-page, .ant-alert, .ant-result').first().waitFor({ timeout: 15000 }).catch(() => {}), await s.page.waitForTimeout(500); };
  const texts = (s, selector) => s.page.locator(selector).allInnerTexts().then(list => list.map(t => t.replace(/\s+/g, ' ').trim()));
  const buttons = s => s.page.evaluate(() => [...document.querySelectorAll('.uw-page button')].map(b => ({ text: b.innerText.trim(), disabled: b.disabled })).filter(b => b.text));
  const pageInfo = s => s.page.evaluate(() => ({ title: document.querySelector('.uw-title')?.innerText.replace(/\s+/g, ' '), meta: document.querySelector('.uw-meta')?.innerText,
    checks: [...document.querySelectorAll('.uw-checks li')].map(li => `${li.className}: ${li.innerText.replace(/\s+/g, ' ')}`),
    verdict: document.querySelector('.uw-verdict')?.innerText.replace(/\s+/g, ' '), exposure: document.querySelector('.uw-exposure')?.innerText.replace(/\s+/g, ' '),
    alerts: [...document.querySelectorAll('.ant-alert')].map(a => a.innerText.replace(/\s+/g, ' ')), history: [...document.querySelectorAll('.uw-history li')].map(li => li.innerText.replace(/\s+/g, ' ')) }));

  const who = (process.env.ACCOUNTS ?? 'admin,uw,lead,ro').split(',');
  const listChecks = s => async () => ({ stats: await texts(s, '.uw-stat'), tabs: await texts(s, '.uw-toolbar .ant-segmented-item'), rows: await texts(s, '.ant-table-row'),
    newButton: await s.page.locator('.uw-head button').count(), pendingSwitch: await s.page.locator('.uw-switch').count(), total: await s.page.locator('.ant-pagination-total-text').innerText().catch(() => '') });
  const rows = s => async () => ({ rows: await texts(s, '.ant-table-row'), empty: await s.page.locator('.ant-empty').count() });
  const detail = s => async () => ({ ...(await pageInfo(s)), buttons: await buttons(s), decision: await texts(s, '.uw-column:last-child .uw-row'), sources: await texts(s, '.uw-column:first-child .uw-card:last-child .uw-row') });
  const entry = (s, scope) => s.page.locator(`${scope} button`, { hasText: /^\s*(Underwriting|承\s*保)\s*$/ }).count();

  // 管理员（全部权限）
  if (who.includes('admin')) {
    const admin = await session('admin');
    await go(admin, '/risk/underwriting');
    await shot(admin, 'L1-list', listChecks(admin));
    await go(admin, '/risk/underwriting?status=SUPERSEDED&page=1&size=20');
    await shot(admin, 'L2-tab-superseded', rows(admin));
    await go(admin, '/risk/underwriting?status=DECLINED&page=1&size=20');
    await shot(admin, 'L3-tab-declined', rows(admin));
    await go(admin, '/risk/underwriting?crr=HIGH&page=1&size=20');
    await shot(admin, 'L4-filter-crr-high', rows(admin));
    await go(admin, '/risk/underwriting?status=SUPERSEDED&decisionSource=AUTO_RULE&page=1&size=20');
    await shot(admin, 'L5-filter-auto-superseded', rows(admin));
    await go(admin, '/risk/underwriting?keyword=' + encodeURIComponent(C.H) + '&page=1&size=20');
    await shot(admin, 'L6-search-client-id', rows(admin));
    await go(admin, '/risk/underwriting?pendingReviewForMe=true&page=1&size=20');
    await shot(admin, 'L7-pending-for-me', rows(admin));
    await go(admin, `/risk/underwriting/${U.L_v2}`);
    await shot(admin, 'P05-L-v2-history', detail(admin));
    current = 'admin:P05 revise dialog';
    await admin.page.locator('.uw-actions button.ant-btn-primary').last().click(); await admin.page.waitForTimeout(600);
    await admin.page.locator('.ant-modal .ant-modal-footer .ant-btn-primary').click(); await admin.page.waitForTimeout(400);
    await shot(admin, 'P05-revise-dialog', async () => ({ modal: (await admin.page.locator('.ant-modal').innerText()).replace(/\s+/g, ' '), fieldError: await admin.page.locator('.ant-modal .uw-field-error').innerText().catch(() => null) }));
    await admin.page.locator('.ant-modal-footer .ant-btn:not(.ant-btn-primary)').click(); await admin.page.waitForTimeout(400);
    await go(admin, `/risk/underwriting/${U.L_v1}`);
    await shot(admin, 'D1-L-v1-superseded-auto', detail(admin));
    await go(admin, `/risk/underwriting/${U.H_v2}`);
    await shot(admin, 'D2-H-v2-high-approved', detail(admin));
    await go(admin, `/risk/underwriting/${U.H_v1}`);
    await shot(admin, 'D3-H-v1-declined', detail(admin));
    await go(admin, `/risk/underwriting/${U.M_v1}`);
    await shot(admin, 'D4-M-v1-no-crr-declined', detail(admin));
    await go(admin, `/risk/underwriting/new?clientId=${C.H}`);
    await shot(admin, 'N1-new-H-effective-exists', detail(admin));
    await go(admin, `/risk/underwriting/new?clientId=${C.M}`);
    await shot(admin, 'N2-new-M-manual', detail(admin));
    if (await admin.page.locator('.uw-tn input').count()) {
      current = 'admin:N2 T+N';
      await admin.page.locator('.uw-tn input').first().fill('7'); await admin.page.waitForTimeout(2500);
      await shot(admin, 'N3-new-M-tn-preview', detail(admin));
    }
    await go(admin, `/master-data/clients/${C.L}`);
    await shot(admin, 'E1-client-detail-L', async () => ({ entry: await entry(admin, '') }));
    await go(admin, `/client-onboarding/reviews/${CASE.L}`);
    await shot(admin, 'E2-review-case-L', async () => ({ entry: await entry(admin, '.obw-head') }));
    await go(admin, `/client-onboarding/reviews/${CASE.H}`);
    await shot(admin, 'E3-review-case-H', async () => ({ entry: await entry(admin, '.obw-head') }));
    for (const code of ['OPERATIONS_RISK_UNDERWRITING', 'OPERATIONS_RISK_UNDERWRITING_MANAGE', 'OPERATIONS_RISK_UNDERWRITING_APPROVE']) {
      await go(admin, `/console-resources?resource=${code}&mode=view`);
      await admin.page.locator('.resource-workspace .configuration-editor').first().waitFor({ timeout: 15000 }).catch(() => {});
      await shot(admin, `R-${code}`, async () => ({ editor: (await admin.page.locator('.resource-workspace .configuration-editor').first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 700),
        warnings: await admin.page.locator('.resource-workspace .ant-alert').allInnerTexts() }));
    }
    await go(admin, '/risk/rule-configuration');
    await shot(admin, 'G1-risk-rules', async () => ({ heading: await admin.page.locator('h1, .page-heading').first().innerText().catch(() => '') }));
    await go(admin, '/workflow/instances');
    await shot(admin, 'G2-workflow-instances', rows(admin));
    await go(admin, '/commercial/agreements');
    current = 'admin:G3 open agreement';
    const first = admin.page.locator('.ant-table-row').first();
    if (await first.count()) { await first.click(); await settle(admin.page, 1500); }
    await shot(admin, 'G3-agreement-detail', async () => ({ path: new URL(admin.page.url()).pathname, heading: await admin.page.locator('h1').first().innerText().catch(() => '') }));
    await admin.ctx.close();
  }

  // 承保员 uw（RISK_DECISION_CONFIG_READ + READ + MANAGE）
  if (who.includes('uw')) {
    const uw = await session('uw');
    await go(uw, '/risk/underwriting');
    await shot(uw, 'L1-list', listChecks(uw));
    await go(uw, `/risk/underwriting/${U.H_v2}`);
    await shot(uw, 'D2-H-v2', detail(uw));
    await go(uw, `/risk/underwriting/new?clientId=${C.M}`);
    await shot(uw, 'N2-new-M-manual', detail(uw));
    await uw.ctx.close();
  }

  // 负责人 lead（再加 APPROVE）：当前无待复核记录
  if (who.includes('lead')) {
    const lead = await session('lead');
    await go(lead, '/risk/underwriting?pendingReviewForMe=true&page=1&size=20');
    await shot(lead, 'L7-pending-for-me', rows(lead));
    await go(lead, `/risk/underwriting/${U.H_v2}`);
    await shot(lead, 'D2-H-v2', detail(lead));
    await lead.ctx.close();
  }

  // 只读 ro（RISK_DECISION_CONFIG_READ + READ）
  if (who.includes('ro')) {
    const ro = await session('ro');
    await go(ro, '/risk/underwriting');
    await shot(ro, 'L1-list', listChecks(ro));
    await go(ro, `/risk/underwriting/${U.L_v2}`);
    await shot(ro, 'P05-L-v2', detail(ro));
    await go(ro, `/risk/underwriting/new?clientId=${C.M}`);
    await shot(ro, 'N2-new-M-readonly', detail(ro));
    await ro.ctx.close();
  }

  writeFileSync(join(HERE, `result-${who.join("+")}-${tag}.json`), JSON.stringify(result, null, 2));
  if (runs.length > 1) await new Promise(r => setTimeout(r, Number(process.env.PAUSE_MS ?? 60000)));
  console.log(JSON.stringify({ run: tag, console: result.console.length, httpErrors: result.httpErrors, writes: result.writes, bundles: result.bundles }));
}
await browser.close();
