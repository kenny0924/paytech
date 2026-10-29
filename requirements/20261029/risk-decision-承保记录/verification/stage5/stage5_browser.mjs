// REQ-03 阶段 5 浏览器走查（本机 e2e）：rq03 worktree 的运营 Web（127.0.0.1:18314 → Nginx 18444）。
// 组合：zh-CN / en × 1440 / 390。截图 screens/<语言>-<宽度>-<账号>-<步骤>.png，结果 stage5-browser-result-<账号>-<组合>.json。
// 只读：不点一键自动承保、不提交、不复核；修订弹窗打开后取消；草稿页只改 T+N 观察敞口预览，不保存（页面无自动保存）。
// ACCOUNTS=admin,uw,lead,ro 选择账号（uw / lead / ro 需要 Risk 目录权限 RISK_DECISION_CONFIG_READ 才能看到菜单）。
// 凭据：uw / lead / ro 读 ~/Code-MoR-backups/rq03-stage5/accounts.json，管理员读 local-environment/e2e/secrets.env；均不打印。
// 用法：node stage5_browser.mjs；RUNS='[["zh-CN",1440]]' 只跑部分组合。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'http://127.0.0.1:18314';
const SHOTS = join(HERE, 'screens');
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(SHOTS, { recursive: true });

const accounts = JSON.parse(readFileSync(join(homedir(), 'Code-MoR-backups/rq03-stage5/accounts.json'), 'utf8'));
const secrets = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8')
  .split('\n').filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const login = { uw: accounts.uw, lead: accounts.lead, ro: accounts.ro, admin: { email: secrets.E2E_ADMIN_EMAIL, password: secrets.E2E_ADMIN_PASSWORD } };
const C = { auto: '241533217348268033', medium: '241532599334350849', revised: '240674874987655169', high: '241533316505808897', highApproved: '241533481480368129', noCrr: '240674482170114049' };
const U = { revisedV2: '241601362595430400', revisedV1: '241601360582164480', mediumV1: '241601364877131776', highV2: '241601370631716864', highApprovedV1: '241601372552708096', noCrrDraft: '241601375522275328' };
const CASE_AUTO = '241533301817364480';
const RAWKEY = /\b(risk|nav|master-data|client-review|common)\.[A-Za-z_][\w-]*(\.[\w-]+)+/g;
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];

const browser = await chromium.launch({ headless: true });
for (const [locale, width] of runs) {
  const tag = `${locale}-${width}`;
  const result = { locale, width, steps: [], console: [], httpErrors: [], writes: [], bundles: 0 };
  let current = 'start';

  async function session(who) {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale, viewport: { width, height: width < 600 ? 844 : 1000 }, isMobile: width < 600, hasTouch: width < 600 });
    await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
    // MinIO 只放行 18311：本端口的语言包补 ACAO（同 REQ-05 / REQ-12 阶段 5 做法）。
    await ctx.route(/^https:\/\/127\.0\.0\.1:19400\//, async route => {
      const response = await route.fetch();
      await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': OPS } });
    });
    const page = await ctx.newPage();
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) result.console.push({ step: current, type: m.type(), text: m.text().slice(0, 300) }); });
    page.on('pageerror', e => result.console.push({ step: current, type: 'pageerror', text: String(e).slice(0, 300) }));
    page.on('response', r => {
      const url = new URL(r.url());
      if (url.port === '19400') result.bundles++;
      if (r.status() >= 400) { result.httpErrors.push({ step: current, method: r.request().method(), path: url.pathname, status: r.status() }); console.log('HTTP', r.status(), r.request().method(), url.pathname, current); }
    });
    page.on('request', r => { const u = new URL(r.url()); if (u.pathname.includes('/risk-underwriting/') && r.method() !== 'GET') result.writes.push({ step: current, method: r.method(), path: u.pathname }); });
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
    if (path.startsWith('/risk/underwriting')) await s.page.locator('.uw-page, .ant-alert, .ant-result').first().waitFor({ timeout: 15000 }).catch(() => {}), await s.page.waitForTimeout(500); };
  const texts = (s, selector) => s.page.locator(selector).allInnerTexts().then(list => list.map(t => t.replace(/\s+/g, ' ').trim()));
  const buttons = s => s.page.evaluate(() => [...document.querySelectorAll('.uw-page button')].map(b => ({ text: b.innerText.trim(), disabled: b.disabled })).filter(b => b.text));
  const pageInfo = s => s.page.evaluate(() => ({ title: document.querySelector('.uw-title')?.innerText.replace(/\s+/g, ' '), meta: document.querySelector('.uw-meta')?.innerText,
    checks: [...document.querySelectorAll('.uw-checks li')].map(li => `${li.className}: ${li.innerText.replace(/\s+/g, ' ')}`),
    verdict: document.querySelector('.uw-verdict')?.innerText.replace(/\s+/g, ' '), exposure: document.querySelector('.uw-exposure')?.innerText.replace(/\s+/g, ' '),
    alerts: [...document.querySelectorAll('.ant-alert')].map(a => a.innerText.replace(/\s+/g, ' ')), history: [...document.querySelectorAll('.uw-history li')].map(li => li.innerText.replace(/\s+/g, ' ')) }));

  const listChecks = s => async () => ({ stats: await texts(s, '.uw-stat'), tabs: await texts(s, '.uw-toolbar .ant-segmented-item'), rows: (await texts(s, '.ant-table-row')).length,
    newButton: await s.page.locator('.uw-head button').count(), pendingSwitch: await s.page.locator('.uw-switch').count(), total: await s.page.locator('.ant-pagination-total-text').innerText().catch(() => '') });
  async function draftTn(s) {
    await go(s, `/risk/underwriting/${U.noCrrDraft}`);
    const before = (await pageInfo(s)).exposure;
    current = `${s.who}:P03 T+N`;
    const field = s.page.locator('.uw-tn input, input.uw-tn').first();
    if (!await field.waitFor({ timeout: 20000 }).then(() => true).catch(() => false)) {
      // 草稿编辑器未出现（如入口限流）：留证后重载一次
      await s.page.screenshot({ path: join(SHOTS, `${tag}-${s.who}-P03-draft-retry.png`), fullPage: true }); await s.page.waitForTimeout(20000); await go(s, `/risk/underwriting/${U.noCrrDraft}`);
    }
    await field.fill('9'); await s.page.waitForTimeout(2500);
    await shot(s, 'P03-draft-no-crr', async () => ({ ...(await pageInfo(s)), exposureBefore: before, crrHint: await s.page.locator('.uw-hint-inline').first().innerText().catch(() => null), buttons: await buttons(s) }));
  }
  async function details(s) {
    await go(s, `/risk/underwriting/${U.mediumV1}`);
    await shot(s, 'D1-medium-approved', async () => ({ ...(await pageInfo(s)), decision: await texts(s, '.uw-column:last-child .uw-row') }));
    await go(s, `/risk/underwriting/${U.highApprovedV1}`);
    await shot(s, 'D2-high-approved', async () => ({ ...(await pageInfo(s)), decision: await texts(s, '.uw-column:last-child .uw-row') }));
  }
  const who = (process.env.ACCOUNTS ?? 'admin,uw,lead,ro').split(',');

  // 管理员（全部权限）：列表、02（只截图，不点一键自动承保）、03 草稿 T+N 预览（不保存）、04 复核人视角（不复核）、05 修订弹窗（打开后取消）、入口链接
  if (who.includes('admin')) {
    const admin = await session('admin');
    await go(admin, '/risk/underwriting');
    await shot(admin, 'L1-list', listChecks(admin));
    await go(admin, '/risk/underwriting?status=DRAFT&page=1&size=20');
    await shot(admin, 'L2-tab-draft', async () => ({ rows: await texts(admin, '.ant-table-row') }));
    await go(admin, '/risk/underwriting?status=SUPERSEDED&page=1&size=20');
    await shot(admin, 'L2b-tab-superseded', async () => ({ rows: await texts(admin, '.ant-table-row') }));
    await go(admin, '/risk/underwriting?crr=HIGH&page=1&size=20');
    await shot(admin, 'L3-filter-high', async () => ({ rows: await texts(admin, '.ant-table-row') }));
    await go(admin, '/risk/underwriting?pendingReviewForMe=true&page=1&size=20');
    await shot(admin, 'L4-pending-for-me', async () => ({ rows: await texts(admin, '.ant-table-row') }));
    await go(admin, `/risk/underwriting/new?clientId=${C.auto}`);
    await shot(admin, 'P02-auto', async () => ({ ...(await pageInfo(admin)), buttons: await buttons(admin) }));
    // 原型 03 的人工表单：同一 Client 点"改为人工承保"，只改 T+N 看敞口实时预览（U11），不保存草稿
    current = 'admin:P03 manual';
    await admin.page.locator('.uw-actions button:not(.ant-btn-primary)').first().click(); await admin.page.waitForTimeout(800);
    const manualBefore = (await pageInfo(admin)).exposure;
    await admin.page.locator('.uw-tn input, input.uw-tn').first().fill('7'); await admin.page.waitForTimeout(2500);
    await shot(admin, 'P03-manual-live-exposure', async () => ({ ...(await pageInfo(admin)), exposureBefore: manualBefore, buttons: await buttons(admin) }));
    await draftTn(admin);
    await go(admin, `/risk/underwriting/${U.highV2}`);
    await shot(admin, 'P04-reviewer', async () => ({ ...(await pageInfo(admin)), buttons: await buttons(admin) }));
    await go(admin, `/risk/underwriting/${U.revisedV2}`);
    await shot(admin, 'P05-history', async () => ({ ...(await pageInfo(admin)), buttons: await buttons(admin) }));
    current = 'admin:P05 revise dialog';
    await admin.page.locator('.uw-actions button.ant-btn-primary').last().click(); await admin.page.waitForTimeout(600);
    // 空原因点确定：只在本地报"必填"，不发请求；随后取消
    await admin.page.locator('.ant-modal .ant-modal-footer .ant-btn-primary').click(); await admin.page.waitForTimeout(400);
    await shot(admin, 'P05-revise-dialog', async () => ({ modal: (await admin.page.locator('.ant-modal').innerText()).replace(/\s+/g, ' '), error: await admin.page.locator('.ant-modal .uw-field-error').innerText().catch(() => null) }));
    await admin.page.locator('.ant-modal-footer .ant-btn:not(.ant-btn-primary)').click(); await admin.page.waitForTimeout(400);
    await details(admin);
    await go(admin, `/master-data/clients/${C.auto}`);
    await shot(admin, 'E1-client-detail', async () => ({ entry: await admin.page.locator('button', { hasText: /^\s*(Underwriting|承\s*保)\s*$/ }).count() }));
    await go(admin, `/client-onboarding/reviews/${CASE_AUTO}`);
    await shot(admin, 'E2-review-case', async () => ({ entry: await admin.page.locator('.obw-head button', { hasText: /^\s*(Underwriting|承\s*保)\s*$/ }).count() }));
    await admin.ctx.close();
  }

  // 承保员 uw（READ + MANAGE）
  if (who.includes('uw')) {
    const uw = await session('uw');
    await go(uw, '/risk/underwriting');
    await shot(uw, 'L1-list', listChecks(uw));
    await go(uw, `/risk/underwriting/new?clientId=${C.auto}`);
    await shot(uw, 'P02-auto', async () => ({ ...(await pageInfo(uw)), buttons: await buttons(uw) }));
    await draftTn(uw);
    await go(uw, `/risk/underwriting/${U.highV2}`);
    await shot(uw, 'P04-uw-no-approve', async () => ({ ...(await pageInfo(uw)), reviewPanel: await uw.page.locator('.uw-card textarea:visible').count() }));
    await uw.ctx.close();
  }

  // 负责人 lead（READ + MANAGE + APPROVE）：C 是本人提交
  if (who.includes('lead')) {
    const lead = await session('lead');
    await go(lead, '/risk/underwriting?pendingReviewForMe=true&page=1&size=20');
    await shot(lead, 'L4-pending-for-me', async () => ({ rows: await texts(lead, '.ant-table-row'), empty: await lead.page.locator('.ant-empty').count() }));
    await go(lead, `/risk/underwriting/${U.highV2}`);
    await shot(lead, 'P04-self', async () => ({ ...(await pageInfo(lead)), buttons: await buttons(lead), hint: await lead.page.locator('.uw-card .uw-hint').allInnerTexts() }));
    await lead.ctx.close();
  }

  // 只读 ro（READ）
  if (who.includes('ro')) {
    const ro = await session('ro');
    await go(ro, '/risk/underwriting');
    await shot(ro, 'L5-readonly-list', listChecks(ro));
    await go(ro, `/risk/underwriting/${U.revisedV2}`);
    await shot(ro, 'P05-history', async () => ({ ...(await pageInfo(ro)), buttons: await buttons(ro) }));
    await go(ro, `/risk/underwriting/new?clientId=${C.auto}`);
    await shot(ro, 'P02-readonly', async () => ({ ...(await pageInfo(ro)), buttons: await buttons(ro) }));
    await ro.ctx.close();
  }

  writeFileSync(join(HERE, `stage5-browser-result-${who.join("+")}-${tag}.json`), JSON.stringify(result, null, 2));
  if (runs.length > 1) await new Promise(r => setTimeout(r, Number(process.env.PAUSE_MS ?? 60000)));
  console.log(JSON.stringify({ run: tag, console: result.console.length, httpErrors: result.httpErrors, writes: result.writes, bundles: result.bundles }));
}
await browser.close();
