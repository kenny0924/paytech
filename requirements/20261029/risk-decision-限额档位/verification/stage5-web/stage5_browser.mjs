// REQ-04 阶段 5 浏览器走查（本机 e2e）：rq04combo worktree 的运营 Web（127.0.0.1:18315 → Nginx 18444）。由 REQ-03 同名脚本改写。
// 组合：zh-CN / en × 1440 / 390；账号 req / appr / elev / ro（~/Code-MoR-backups/rq04-stage5/accounts.json）与管理员（local-environment/e2e/secrets.env），均不打印凭据。
// 按阶段走，每个阶段在全部组合上截图，写操作只在第一个组合里点一次：
//   A 只读：列表、AUTO 验证档（额度用满、清单）、E 标准档（长历史）、880000000901 迁移、B / D 无档位、只读账号、入口链接、变量编辑器 DAILY_SUM_AMOUNT。
//   B 待审批：req 为 AUTO 申请 VERIFICATION → RESTRICTED（LOW → 普通审批）、为 E 申请 STANDARD → RESTRICTED（无 CRR → 高级审批）；各视角截图；
//     AUTO 的申请由 req 撤回；E 的申请由 elev 批准（为原型 05 受限档造状态）。
//   C E 往返：受限（05）→ 申请恢复到验证档（elev 批准）→ 上线复核清单提交（elev 批准）→ 观察档（03）→ 申请升标准档（04 审批人视角）→ elev 批准，E 回到 STANDARD。
// 截图 screens/<语言>-<宽度>-<账号>-<步骤>.png，结果 stage5-browser-result.json。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'http://127.0.0.1:18315';
const SHOTS = join(HERE, 'screens');
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(SHOTS, { recursive: true });

const accounts = JSON.parse(readFileSync(join(homedir(), 'Code-MoR-backups/rq04-stage5/accounts.json'), 'utf8'));
const secrets = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8')
  .split('\n').filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const login = { req: accounts.req, appr: accounts.appr, elev: accounts.elev, ro: accounts.ro, admin: { email: secrets.E2E_ADMIN_EMAIL, password: secrets.E2E_ADMIN_PASSWORD } };
const C = { auto: '241533217348268033', e: '240674482170114049', migrated: '880000000901', b: '241532599334350849', d: '241533481480368129' };
const UW_AUTO = '241829409114308608';
const RAWKEY = /\b(risk|nav|master-data|common)\.[A-Za-z_][\w-]*(\.[\w-]+)+/g;
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];
const phases = (process.env.PHASES ?? 'A,B,C').split(',');
const result = { runs, steps: [], console: [], httpErrors: [], writes: [] };
let current = 'start';

const browser = await chromium.launch({ headless: true });
const sessions = new Map();
async function session(who, locale, width) {
  const id = `${locale}-${width}-${who}`;
  if (sessions.has(id)) return sessions.get(id);
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale, viewport: { width, height: width < 600 ? 844 : 1000 }, isMobile: width < 600, hasTouch: width < 600 });
  await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
  // MinIO 只放行 18311：本端口的语言包补 ACAO（同 REQ-03 / REQ-12 阶段 5 做法）。
  await ctx.route(/^https:\/\/127\.0\.0\.1:19400\//, async route => {
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': OPS } });
  });
  const page = await ctx.newPage();
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) result.console.push({ step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('pageerror', e => result.console.push({ step: current, type: 'pageerror', text: String(e).slice(0, 300) }));
  page.on('response', r => {
    const url = new URL(r.url());
    if (r.status() >= 400) { result.httpErrors.push({ step: current, method: r.request().method(), path: url.pathname, status: r.status() }); console.log('HTTP', r.status(), r.request().method(), url.pathname, current); }
  });
  page.on('request', r => { const u = new URL(r.url()); if (u.pathname.includes('/risk-limit-tiers/') && r.method() !== 'GET') result.writes.push({ step: current, method: r.method(), path: u.pathname, body: r.postData()?.slice(0, 400) }); });
  current = 'login ' + id;
  await page.goto(`${OPS}/?lang=${locale}`);
  await page.locator('input[type=password]').first().waitFor({ timeout: 30000 });
  for (let attempt = 0; attempt < 2 && await page.locator('input[type=password]').count(); attempt++) {
    await settle(page, 1500);
    await page.locator('input:not([type=password])').first().fill(login[who].email);
    await page.locator('input[type=password]').first().fill(login[who].password);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    await page.waitForFunction(() => !document.querySelector('input[type=password]'), null, { timeout: 30000 }).catch(() => {});
  }
  await settle(page);
  const s = { ctx, page, who, tag: `${locale}-${width}` };
  sessions.set(id, s);
  return s;
}
async function settle(page, ms = 1000) { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); }
const facts = page => page.evaluate(raw => ({
  rawKeys: [...new Set(document.body.innerText.match(new RegExp(raw, 'g')) || [])].filter(k => !/\.(ae|test|com)$/.test(k)).slice(0, 10),
  horizontalScroll: document.documentElement.scrollWidth - document.documentElement.clientWidth,
}), RAWKEY.source);
async function shot(s, step, check) {
  current = `${s.tag}:${s.who}:${step}`;
  const entry = { step: current };
  try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
  entry.page = await facts(s.page);
  await s.page.screenshot({ path: join(SHOTS, `${s.tag}-${s.who}-${step}.png`), fullPage: true });
  result.steps.push(entry); console.log(JSON.stringify(entry).slice(0, 1500));
}
// 应用内跳转（pushState + popstate），避免整页重载触发 Gateway 限流；同一 Client 重读时先经过一个不发请求的无效地址让页面重挂载。
async function go(s, path) {
  current = `${s.tag}:${s.who}:goto ${path}`;
  const push = p => s.page.evaluate(x => { window.history.pushState({}, '', x); window.dispatchEvent(new PopStateEvent('popstate')); }, p);
  if (path.startsWith('/risk/limit-tiers/')) { await push('/risk/limit-tiers/0'); await s.page.waitForTimeout(200); }
  await push(path); await settle(s.page, 1200);
  if (path.startsWith('/risk/limit-tiers')) await s.page.locator('.uw-card, .ant-alert, .ant-table').first().waitFor({ timeout: 15000 }).catch(() => {});
  await s.page.waitForTimeout(400);
}
const clean = t => t.replace(/\s+/g, ' ').trim();
const texts = (s, selector) => s.page.locator(selector).allInnerTexts().then(list => list.map(clean));
const buttons = s => s.page.evaluate(() => [...document.querySelectorAll('.uw-page button')].map(b => ({ text: b.innerText.replace(/\s+/g, ' ').trim(), disabled: b.disabled })).filter(b => b.text));
const detailInfo = s => s.page.evaluate(() => {
  const c = t => (t ?? '').replace(/\s+/g, ' ').trim();
  return { title: c(document.querySelector('.uw-title')?.innerText), meta: c(document.querySelector('.uw-meta')?.innerText),
    cards: [...document.querySelectorAll('.uw-card-title h2')].map(h => c(h.innerText)),
    quotas: [...document.querySelectorAll('.lt-quota tbody tr')].map(tr => c(tr.innerText)), fullBars: document.querySelectorAll('.lt-bar.is-full').length,
    checklist: [...document.querySelectorAll('.lt-checklist li')].map(li => c(li.innerText)), actions: [...document.querySelectorAll('.lt-actions li strong')].map(x => c(x.innerText)),
    alerts: [...document.querySelectorAll('.ant-alert')].map(a => c(a.innerText)), history: [...document.querySelectorAll('.lt-history li')].slice(0, 6).map(li => c(li.innerText)),
    historyCount: document.querySelectorAll('.lt-history li').length, hints: [...document.querySelectorAll('.uw-card .uw-hint')].map(h => c(h.innerText)).slice(-3) };
});
const listInfo = s => async () => ({ stats: await texts(s, '.uw-stat'), tabs: await texts(s, '.uw-toolbar .ant-segmented-item'), rows: await texts(s, '.ant-table-row'),
  total: await s.page.locator('.ant-pagination-total-text').innerText().catch(() => '') });
const detail = s => async () => ({ ...(await detailInfo(s)), buttons: await buttons(s) });
const forAll = async (who, fn) => { for (const [locale, width] of runs) await fn(await session(who, locale, width)); };
const first = async who => session(who, runs[0][0], runs[0][1]);
/** 点击后等待写请求返回。 */
async function command(s, label, click) {
  current = `${s.tag}:${s.who}:${label}`;
  const response = s.page.waitForResponse(r => r.url().includes('/risk-limit-tiers/') && r.request().method() === 'POST', { timeout: 20000 });
  await click();
  const r = await response; const status = r.status();
  console.log('WRITE', label, status); result.steps.push({ step: current, write: status });
  if (status >= 300) throw new Error(`${label} failed ${status}: ${(await r.text()).slice(0, 300)}`);
  await settle(s.page, 800);
}
/** 档位页的申请弹窗：点下一步卡片中第 index 个按钮，填理由，可选恢复目标，提交。 */
async function requestVia(s, clientId, index, reason, restoreTo) {
  await go(s, `/risk/limit-tiers/${clientId}`);
  await s.page.locator('.lt-actions li button').nth(index).click();
  const modal = s.page.locator('.ant-modal'); await modal.waitFor();
  if (restoreTo) {
    await modal.locator('.ant-select').click();
    await s.page.locator('.ant-select-item-option', { hasText: restoreTo }).first().click();
  }
  await modal.locator('textarea').fill(reason);
  await command(s, 'request ' + clientId, () => modal.locator('.ant-modal-footer .ant-btn-primary').click());
}
async function decide(s, clientId, approve, comment) {
  await go(s, `/risk/limit-tiers/${clientId}`);
  if (comment) await s.page.locator('.uw-card textarea').first().fill(comment);
  await command(s, (approve ? 'approve ' : 'reject ') + clientId, () => s.page.locator(`.uw-card .uw-actions button${approve ? '.ant-btn-primary' : '.ant-btn-dangerous'}`).click());
}
async function withdraw(s, clientId) {
  await go(s, `/risk/limit-tiers/${clientId}`);
  await command(s, 'withdraw ' + clientId, () => s.page.locator('.uw-card .uw-actions button:not(.ant-btn-primary):not(.ant-btn-dangerous)').first().click());
}

try {
  if (phases.includes('A')) {
    if (!process.env.ONLY_ADMIN && process.env.ONLY_AUTO) await forAll('req', async s => { await go(s, `/risk/limit-tiers/${C.auto}`); await shot(s, 'A02-auto-verification', detail(s)); });
    if (!process.env.ONLY_ADMIN && !process.env.ONLY_AUTO) await forAll('req', async s => {
      await go(s, '/risk/limit-tiers');
      await shot(s, 'A01-list', listInfo(s));
      await go(s, '/risk/limit-tiers?keyword=RELSNAP&page=1&size=20');
      await shot(s, 'A01b-search-relsnap', async () => ({ rows: await texts(s, '.ant-table-row') }));
      await go(s, '/risk/limit-tiers?tier=VERIFICATION&page=1&size=20');
      await shot(s, 'A01c-tab-verification', async () => ({ rows: await texts(s, '.ant-table-row') }));
      await go(s, '/risk/limit-tiers?observationDue=true&page=1&size=20');
      await shot(s, 'A01d-observation-due', async () => ({ rows: (await texts(s, '.ant-table-row')).length, empty: await s.page.locator('.ant-empty').count() }));
      await go(s, `/risk/limit-tiers/${C.auto}`);
      await shot(s, 'A02-auto-verification', detail(s));
      await go(s, `/risk/limit-tiers/${C.e}`);
      await shot(s, 'A05-e-standard-history', detail(s));
      await go(s, `/risk/limit-tiers/${C.migrated}`);
      await shot(s, 'A06-migrated-standard', detail(s));
      await go(s, `/risk/limit-tiers/${C.b}`);
      await shot(s, 'A07-b-no-tier', detail(s));
      await go(s, `/risk/limit-tiers/${C.d}`);
      await shot(s, 'A07b-d-no-tier', detail(s));
    });
    if (!process.env.ONLY_ADMIN && !process.env.ONLY_AUTO) await forAll('ro', async s => {
      await go(s, '/risk/limit-tiers');
      await shot(s, 'A01-list-readonly', async () => ({ ...(await listInfo(s)()), rowActions: await texts(s, '.ant-table-row .uw-link-button') }));
      await go(s, `/risk/limit-tiers/${C.auto}`);
      await shot(s, 'A02-auto-readonly', detail(s));
      await go(s, `/risk/limit-tiers/${C.e}`);
      await shot(s, 'A05-e-readonly', detail(s));
    });
    if (!process.env.ONLY_AUTO) await forAll('admin', async s => {
      await go(s, `/master-data/clients/${C.auto}`);
      await shot(s, 'E1-client-detail', async () => ({ buttons: await s.page.locator('.page-heading button').allInnerTexts() }));
      await go(s, `/risk/underwriting/${UW_AUTO}`);
      await s.page.locator('.uw-card').first().waitFor({ timeout: 15000 }).catch(() => {});
      await shot(s, 'E2-underwriting-detail', async () => ({ alerts: await texts(s, '.ant-alert'), link: await s.page.locator('.ant-alert a[href*="/risk/limit-tiers/"]').count() }));
      // 变量编辑器：新建实时累计变量，选支付尝试决策点、放行事件与"按日金额合计"，看窗口选项与预览；不保存（离开页面即丢弃）。
      await go(s, '/risk/variables?view=create');
      const item = label => s.page.locator('.ant-form-item').filter({ has: s.page.locator('label', { hasText: label }) }).first();
      const pick = async (label, pattern) => {
        await item(label).locator('.ant-select').click(); await s.page.waitForTimeout(300);
        const option = s.page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: pattern }).first();
        await option.click(); await s.page.waitForTimeout(400);
      };
      const zh = s.tag.startsWith('zh');
      await pick(zh ? '来源类型' : 'Source Type', zh ? '实时累计' : 'Real-Time Aggregate');
      await pick(zh ? '适用决策点' : 'Decision Point', /PAYMENT_ATTEMPT|支付|Payment/);
      await pick(zh ? '统计事件' : 'Event', /ALLOWED|放行|allowed/i);
      await item(zh ? '统计主体' : 'Subject').locator('.ant-select').click(); await s.page.waitForTimeout(300);
      await s.page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: /Client|客户/ }).first().click(); await s.page.waitForTimeout(300);
      await s.page.locator('.ant-radio-button-wrapper', { hasText: zh ? '按日金额合计' : 'Daily Total Amount' }).click(); await s.page.waitForTimeout(400);
      const windowItem = item(zh ? '时间窗口' : 'Time Window');
      const windows = await windowItem.locator('.ant-radio-button-wrapper').allInnerTexts();
      await windowItem.locator('.ant-radio-button-wrapper').last().click(); await s.page.waitForTimeout(300);
      await shot(s, 'V1-variable-daily-windows', async () => ({ windows, metrics: await texts(s, '.ant-radio-group:first-of-type .ant-radio-button-wrapper'),
        checked: await texts(s, '.ant-radio-button-wrapper-checked'), preview: await texts(s, '.ant-alert') }));
    });
  }

  if (phases.includes('B')) {
    const req = await first('req');
    await requestVia(req, C.auto, 0, 'Stage 5 browser pass: pending-approval views (will be withdrawn)');
    await requestVia(req, C.e, 0, 'Stage 5 browser pass: restrict to capture the Restricted page');
    await forAll('req', async s => {
      await go(s, `/risk/limit-tiers/${C.auto}`); await shot(s, 'B04-auto-self', detail(s));
      await go(s, '/risk/limit-tiers?pendingApproval=true&page=1&size=20'); await shot(s, 'B01-list-pending', async () => ({ rows: await texts(s, '.ant-table-row') }));
    });
    await forAll('appr', async s => {
      await go(s, `/risk/limit-tiers/${C.auto}`); await shot(s, 'B04-auto-standard-approver', detail(s));
      await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'B04-e-elevated-insufficient', detail(s));
      await go(s, '/risk/limit-tiers?pendingApproval=true&page=1&size=20'); await shot(s, 'B01-list-pending', async () => ({ rows: await texts(s, '.ant-table-row'), actions: await texts(s, '.ant-table-row .uw-link-button') }));
    });
    await forAll('elev', async s => {
      await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'B04-e-elevated-approver', detail(s));
    });
    await forAll('ro', async s => { await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'B04-e-readonly-waiting', detail(s)); });
    // 驳回不填意见：只在本地报必填，不发请求
    const elev = await first('elev');
    await go(elev, `/risk/limit-tiers/${C.e}`);
    current = 'elev:reject-empty'; await elev.page.locator('.uw-card .uw-actions button.ant-btn-dangerous').click(); await elev.page.waitForTimeout(400);
    await shot(elev, 'B04-reject-comment-required', async () => ({ error: await elev.page.locator('.uw-field-error').allInnerTexts() }));
    await withdraw(req, C.auto);
    await decide(elev, C.e, true, 'Stage 5 browser pass');
  }

  if (phases.includes('C')) {
    const req = await first('req'), elev = await first('elev');
    await forAll('req', async s => { await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'C05-e-restricted', detail(s)); });
    await forAll('ro', async s => { await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'C05-e-restricted-readonly', detail(s)); });
    // 恢复弹窗：先截弹窗（可选目标不高于受限前档位），再选验证档提交
    await go(req, `/risk/limit-tiers/${C.e}`);
    await req.page.locator('.lt-actions li button').first().click(); await req.page.locator('.ant-modal').waitFor();
    await req.page.locator('.ant-modal .ant-select').click(); await req.page.waitForTimeout(400);
    await shot(req, 'C05-restore-dialog', async () => ({ options: await texts(req, '.ant-select-item-option'), modal: clean(await req.page.locator('.ant-modal').innerText()) }));
    await req.page.keyboard.press('Escape'); await req.page.locator('.ant-modal-footer .ant-btn:not(.ant-btn-primary)').click(); await req.page.waitForTimeout(400);
    await requestVia(req, C.e, 0, 'Stage 5 browser pass: restore to Verification', /^(Verification|验证档)$/);
    await decide(elev, C.e, true, null);
    // 上线复核：系统项全部满足，勾四个人工项、填说明后提交（提交前截图：按钮可用）
    await go(req, `/risk/limit-tiers/${C.e}`);
    for (const box of await req.page.locator('.lt-checklist .ant-checkbox-input').all()) await box.check();
    await req.page.locator('.uw-card textarea').first().fill('Stage 5 browser pass: production payments verified locally');
    await shot(req, 'C02-e-go-live-ready', detail(req));
    await command(req, 'go-live ' + C.e, () => req.page.locator('.lt-submit button').click());
    await forAll('elev', async s => { await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'C04-e-go-live-approver', detail(s)); });
    await decide(elev, C.e, true, 'Go-live approved (stage 5)');
    await forAll('req', async s => { await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'C03-e-observation', detail(s)); });
    await forAll('req', async s => { await go(s, '/risk/limit-tiers?tier=OBSERVATION&page=1&size=20'); await shot(s, 'C01-list-observation', async () => ({ rows: await texts(s, '.ant-table-row') })); });
    // 延长弹窗只截图后取消；升标准档申请 → 原型 04 审批人视角 → 批准，E 回到 STANDARD
    await go(req, `/risk/limit-tiers/${C.e}`);
    await req.page.locator('.lt-actions li button').nth(1).click(); await req.page.locator('.ant-modal').waitFor();
    await shot(req, 'C03-extend-dialog', async () => ({ modal: clean(await req.page.locator('.ant-modal').innerText()) }));
    await req.page.locator('.ant-modal-footer .ant-btn:not(.ant-btn-primary)').click(); await req.page.waitForTimeout(400);
    await requestVia(req, C.e, 0, 'Stage 5 browser pass: back to Standard');
    await forAll('elev', async s => { await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'C04-e-promote-approver', detail(s)); });
    await forAll('req', async s => { await go(s, `/risk/limit-tiers/${C.e}`); await shot(s, 'C04-e-promote-requester', detail(s)); });
    await decide(elev, C.e, true, 'Back to Standard (stage 5)');
    await go(req, `/risk/limit-tiers/${C.e}`); await shot(req, 'C06-e-standard-again', detail(req));
  }
} catch (error) {
  result.failure = String(error).slice(0, 500); console.log('FAILED', result.failure, current);
  for (const s of sessions.values()) await s.page.screenshot({ path: join(SHOTS, `failure-${s.tag}-${s.who}.png`), fullPage: true }).catch(() => {});
} finally {
  writeFileSync(join(HERE, `stage5-browser-result-${phases.join('')}.json`), JSON.stringify(result, null, 2));
  console.log(JSON.stringify({ steps: result.steps.length, console: result.console.length, httpErrors: result.httpErrors, writes: result.writes.map(w => `${w.method} ${w.path}`) }));
  await browser.close();
}
