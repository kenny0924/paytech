// payout 收款账户与余额解释（S1）· mor-test 阶段 7 运营页面浏览器走查（runbook 验收步）：http://localhost:28080（Nginx → Gateway 块 147 →
// operations-console-bff → payout；语言包为 Platform 真实发布，同源，不改写响应）。改写自 verification/stage5/stage5_browser.mjs（页面步骤原样），
// 去掉本机 i18n 模拟与 19400 CORS 补丁；入口、账号与目标账户改为 mor-test：
//   账号读 ~/Code-MoR-backups/payout-s1-stage7/accounts.json（payout_acceptance.py accounts 建的 maker / checker / noperm），不打印；
//   目标账户读 ~/Code-MoR-backups/payout-s1-stage7/run.json 的 accounts（payout_acceptance.py api 的输出）：
//     A_BROWSER_MAKER（写组合 Maker 通过 → Checker 批准）、A_BROWSER_REJECT（只开拒绝弹窗后取消）、B_AE_SHARED（Maker = maker、待复核：
//     只读组合的 O4 四眼禁用与共用提示）。
// 组合 zh-CN / en × 1440 / 390（RUNS 覆盖）；写操作只在 WRITE 组合（缺省 zh-CN-1440）。Operations 路由每人每分钟 30 次 GET、并发 2：
//   组间暂停 PAUSE_MS（缺省 20 秒）。Reveal 后原值在截图中遮盖，结果只记布尔与倒计时数字。
// 记录：控制台 error / warning、HTTP ≥ 400（区分刻意的无权限访问）、原始 i18n key、横向溢出。
// 用法：node payout_browser.mortest.mjs；本阶段（6）只做 node --check 语法检查。
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

const readEnv = path => Object.fromEntries(readFileSync(path, 'utf8').split('\n').filter(l => l.includes('=') && !l.startsWith('#'))
  .map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const STATE = join(homedir(), 'Code-MoR-backups/payout-s1-stage7');
const state = JSON.parse(readFileSync(join(STATE, 'accounts.json'), 'utf8'));
const runState = JSON.parse(readFileSync(join(STATE, 'run.json'), 'utf8'));
const USERS = {
  admin: { email: state.operators.maker.email, password: state.operators.maker.password },  // 页面步骤沿用 admin 角色名 = Maker
  checker: { email: state.operators.checker.email, password: state.operators.checker.password },
  noperm: { email: state.operators.noperm.email, password: state.operators.noperm.password },
};
const CLIENT_A = '237575207592763393';
const MAKER_TARGET = runState.accounts.A_BROWSER_MAKER;
const REJECT_TARGET = runState.accounts.A_BROWSER_REJECT;
const FOUR_EYES_READONLY = runState.accounts.B_AE_SHARED;
const SHARED_READONLY = runState.accounts.B_AE_SHARED;
const ONLY = process.env.ONLY ?? '';             // ONLY=shared 只跑共用提示一步（补充走查，只读）
const REVIEWS = '/finance/payout/beneficiary-reviews';
const OWNERS = '/finance/payout/owners';
const WRITE = process.env.WRITE ?? 'zh-CN-1440';
const PAUSE = Number(process.env.PAUSE_MS ?? 20000);
const MENU = { 'zh-CN': '出款', en: 'Payouts' };
const NOTE = { 'zh-CN': 'S1MT 浏览器走查：户名与法定名一致', en: 'S1MT browser walkthrough: holder name matches' };

const log = { startedAt: new Date().toISOString(), runs: [], console: [], httpErrors: [], deliberate: [], writes: [] };
const browser = await chromium.launch({ headless: true });
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];

for (const [locale, width] of runs) {
  const tag = `${locale}-${width}`; const write = WRITE === tag; const desktop = width > 500;
  const run = { locale, width, write, steps: [] };

  /** 一个角色的独立浏览器上下文：登录、记录控制台 / HTTP、截图与事实。 */
  async function open(role) {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width, height: desktop ? 1000 : 844 }, locale });
    const page = await context.newPage();
    const ctx = { page, role, current: 'login', deliberate: null, posts: [] };
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, role, step: ctx.current, type: m.type(), text: m.text().slice(0, 300) }); });
    page.on('request', r => { if (r.method() !== 'GET' && r.url().includes('/payout-admin/')) ctx.posts.push({ step: ctx.current, method: r.method(), path: new URL(r.url()).pathname }); });
    page.on('response', r => {
      const url = new URL(r.url());
      if (r.status() >= 400) (ctx.deliberate ? log.deliberate : log.httpErrors).push({ run: tag, role, step: ctx.current, why: ctx.deliberate ?? undefined, method: r.request().method(), path: url.pathname + url.search, status: r.status() });
      if (r.request().method() === 'POST' && url.pathname.includes('/payout-admin/')) log.writes.push({ run: tag, role, step: ctx.current, path: url.pathname, status: r.status() });
    });
    page.on('requestfailed', r => { const p = new URL(r.url()).pathname; if (!/\.(png|woff2?)$/.test(p)) (ctx.deliberate ? log.deliberate : log.httpErrors).push({ run: tag, role, step: ctx.current, method: r.method(), path: p, status: 'failed:' + r.failure()?.errorText }); });
    await page.goto(`${ORIGIN}/?lang=${locale}`);
    await page.locator('input[type=password]').first().waitFor({ timeout: 30000 });
    await page.locator('input:not([type=password])').first().fill(USERS[role].email);
    await page.locator('input[type=password]').first().fill(USERS[role].password);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
    await settle(page);
    return { ...ctx, context, ctx };
  }
  async function settle(page, ms = 900) { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); }
  const facts = page => page.evaluate(() => ({
    rawKeys: [...new Set(document.body.innerText.match(/\b(payout|nav|common)\.[A-Za-z]+[A-Za-z0-9_.]*/g) || [])].slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  const text = async (page, selector) => (await page.locator(selector).first().innerText({ timeout: 3000 }).catch(() => '')).replace(/\s+/g, ' ').slice(0, 900);
  async function shot(s, step, check, { wait = true, mask = [] } = {}) {
    s.ctx.current = step; if (wait) await settle(s.page);
    const entry = { role: s.role, step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts(s.page);
    await s.page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true, mask, maskColor: '#9aa4b2' });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1400));
  }
  /** 打开工作台详情；队列 / 计数并发超过网关并发上限时详情可能缺失，最多重载 3 次并记录次数。 */
  async function openPanel(s, path, selector = '.payout-panel') {
    for (let attempt = 1; attempt <= 3; attempt++) {
      await go(s, path);
      if (await s.page.locator(selector).first().waitFor({ timeout: 8000 }).then(() => true, () => false)) { await s.page.waitForTimeout(1500); return attempt; }
      await s.page.waitForTimeout(2500);
    }
    return 'failed';
  }
  const go = async (s, path) => { s.ctx.current = path; await s.page.goto(`${ORIGIN}${path}${path.includes('?') ? '&' : '?'}lang=${locale}`); await settle(s.page); };
  const tabs = s => s.page.locator('.payout-tabs button').allInnerTexts();
  const menuText = async s => {
    // textContent 包含折叠子菜单与移动端隐藏导航中的项
    const nav = await s.page.locator('.ant-menu').evaluateAll(els => els.map(el => el.textContent)).catch(() => []);
    return nav.join(' ').replace(/\s+/g, ' ');
  };
  const detailText = s => text(s.page, '.payout-detail');
  const options = s => s.page.locator('.payout-option').evaluateAll(els => els.map(el => ({ text: el.innerText.replace(/\s+/g, ' '), disabled: el.disabled, pressed: el.getAttribute('aria-pressed') })));
  const foot = async s => ({ text: await text(s.page, '.payout-panel-foot'), blocked: await s.page.locator('.payout-panel-foot.is-blocked').count() > 0,
    submitDisabled: await s.page.locator('.payout-panel-foot button').first().isDisabled().catch(() => null) });
  const maskReveal = s => [s.page.locator('.payout-original'), s.page.locator('.payout-cards .payout-card').nth(1).locator('.payout-field-value').nth(1)];

  // ================= 管理员（Maker） =================
  const admin = await open('admin');
  if (ONLY === 'shared') {
    const attempts = await openPanel(admin, `${REVIEWS}?stage=CHECKER&account=${SHARED_READONLY}`);
    await shot(admin, 'O1f-shared-alert', async () => ({ attempts, menuHasPayout: (await menuText(admin)).includes(MENU[locale]), alert: await text(admin.page, '.payout-shared'),
      queueSharedTags: await admin.page.locator('.payout-queue .payout-shared-tag').count() }));
    await admin.context.close(); log.runs.push(run); continue;
  }
  await shot(admin, '00-admin-home', async () => ({ menuHasPayout: (await menuText(admin)).includes(MENU[locale]) }));

  // ---------- O1 待核验：页签计数、详情、Reveal ----------
  const makerTarget = write ? MAKER_TARGET : REJECT_TARGET;
  const o1Attempts = await openPanel(admin, `${REVIEWS}?stage=MAKER&account=${makerTarget}`);
  await shot(admin, 'O1a-maker-detail', async () => ({ attempts: o1Attempts, menuHasPayout: (await menuText(admin)).includes(MENU[locale]), tabs: await tabs(admin), queueItems: await admin.page.locator('.payout-queue-item').count(),
    activeItem: await text(admin.page, '.payout-queue-item.is-active'), detail: await detailText(admin),
    sharedAlert: await admin.page.locator('.payout-shared').count(), options: await options(admin), foot: await foot(admin) }));
  const revealButton = admin.page.locator('.payout-cards .payout-card').first().locator('.payout-field button').first();
  if (await revealButton.count()) {
    await revealButton.click();
    await admin.page.locator('.payout-original').first().waitFor({ timeout: 15000 }).catch(() => {});
    await admin.page.waitForTimeout(400);
    const first = await revealButton.innerText().catch(() => '');
    await shot(admin, 'O1b-revealed', async () => ({ originalShown: await admin.page.locator('.payout-original').count(), hideButton: first.replace(/\s+/g, ' '),
      audit: await text(admin.page, '.payout-audit'), nameMatch: (await admin.page.locator('.payout-match').first().getAttribute('class').catch(() => '')) }), { wait: false, mask: maskReveal(admin) });
    await admin.page.waitForTimeout(2200);
    const later = await revealButton.innerText().catch(() => '');
    await revealButton.click(); await admin.page.waitForTimeout(400);
    await shot(admin, 'O1c-hidden', async () => ({ countdown: [first.match(/\d+/)?.[0], later.match(/\d+/)?.[0]], originalShownAfterHide: await admin.page.locator('.payout-original').count(),
      showButton: (await revealButton.innerText().catch(() => '')).replace(/\s+/g, ' '), nameMatchHint: await text(admin.page, '.payout-match') }), { wait: false });
  } else run.steps.push({ role: 'admin', step: 'O1b-revealed', check: { error: 'no reveal button' } });

  // ---------- O1 Maker 意见"通过"+ 备注 → 提交（只在写组合） ----------
  if (write) {
    await admin.page.locator('.payout-option.is-ok').click();
    await admin.page.locator('.payout-note textarea').fill(NOTE[locale]);
    await shot(admin, 'O1d-maker-filled', async () => ({ options: await options(admin), foot: await foot(admin) }), { wait: false });
    admin.ctx.current = 'O1e-maker-submit';
    await admin.page.locator('.payout-panel-foot button').first().click();
    await admin.page.waitForResponse(r => r.url().includes(`/beneficiary-accounts/${MAKER_TARGET}/maker-reviews`), { timeout: 20000 }).catch(() => {});
    await shot(admin, 'O1e-maker-submitted', async () => ({ tabs: await tabs(admin), toast: await text(admin.page, '.ant-message, .ant-notification'),
      queueStillHasTarget: await admin.page.locator('.payout-queue-item', { hasText: runState.accountCodes.A_BROWSER_MAKER }).count() }));
  }

  // ---------- O4 四眼：同一管理员打开待复核 ----------
  const fourEyesTarget = write ? MAKER_TARGET : FOUR_EYES_READONLY;
  const o4Attempts = await openPanel(admin, `${REVIEWS}?stage=CHECKER&account=${fourEyesTarget}`);
  await shot(admin, 'O4-four-eyes', async () => ({ attempts: o4Attempts, tabs: await tabs(admin), opinion: await text(admin.page, '.payout-opinion'), options: await options(admin), foot: await foot(admin) }));

  // ---------- O5 拒绝弹窗：选拒绝 → 弹窗 → 取消，不提交 ----------
  const o5Attempts = await openPanel(admin, `${REVIEWS}?stage=MAKER&account=${REJECT_TARGET}`, '.payout-option.is-danger');
  run.steps.push({ role: 'admin', step: 'O5-open', check: { attempts: o5Attempts } });
  const postsBefore = admin.ctx.posts.length;
  await admin.page.locator('.payout-option.is-danger').click();
  await admin.page.locator('.ant-modal').waitFor({ timeout: 10000 }).catch(() => {});
  await admin.page.waitForTimeout(500);
  await shot(admin, 'O5a-reject-dialog', async () => ({ title: await text(admin.page, '.ant-modal-title'), subject: await text(admin.page, '.payout-dialog-subject'),
    reasons: await admin.page.locator('.ant-modal .payout-reason-options .ant-radio-wrapper').allInnerTexts(), clientNote: await admin.page.locator('.ant-modal textarea').count(),
    notice: await text(admin.page, '.payout-dialog-notice'), buttons: await admin.page.locator('.ant-modal .payout-dialog-foot button').allInnerTexts() }), { wait: false });
  await admin.page.locator('.ant-modal .payout-dialog-foot button').first().click();
  await admin.page.waitForTimeout(600);
  await shot(admin, 'O5b-cancelled', async () => ({ modalOpen: await admin.page.locator('.ant-modal-wrap:visible').count(), payoutWritesDuringDialog: admin.ctx.posts.length - postsBefore,
    tabs: await tabs(admin) }), { wait: false });

  // ---------- O3 Owner 账户与余额：经筛选框查询 Client A ----------
  await go(admin, OWNERS);
  await shot(admin, 'O3a-owner-empty', async () => ({ empty: await text(admin.page, '.payout-owner-empty') }));
  await admin.page.locator('.payout-owner-filter input').last().fill(CLIENT_A);
  await admin.page.locator('.payout-owner-filter input').last().press('Enter');
  await admin.page.waitForFunction(() => document.querySelector('.payout-balance-card, .payout-balance-grid + *, .payout-page .ant-empty') && !document.querySelector('.payout-page .ant-spin-spinning'), null, { timeout: 40000 }).catch(() => {});
  await shot(admin, 'O3b-owner-client-a', async () => ({ url: new URL(admin.page.url()).search, title: await text(admin.page, '.payout-owner-title'),
    balances: await admin.page.locator('.payout-balance-card').allInnerTexts().then(a => a.map(x => x.replace(/\s+/g, ' '))),
    rows: await admin.page.locator('.payout-table tbody tr.ant-table-row').allInnerTexts().then(a => a.map(x => x.replace(/\s+/g, ' '))),
    submitButton: await admin.page.locator('.payout-section-head button').count() }));
  await admin.context.close();

  // ================= Checker（另一位运营） =================
  const checker = await open('checker');
  const checkerTarget = write ? MAKER_TARGET : FOUR_EYES_READONLY;
  const o2Attempts = await openPanel(checker, `${REVIEWS}?stage=CHECKER&account=${checkerTarget}`);
  await shot(checker, 'O2a-checker-detail', async () => ({ attempts: o2Attempts, tabs: await tabs(checker), opinion: await text(checker.page, '.payout-opinion'),
    noteVisible: write ? (await text(checker.page, '.payout-opinion')).includes(NOTE[locale]) : undefined, options: await options(checker), foot: await foot(checker) }));
  if (write) {
    await checker.page.locator('.payout-option.is-ok').click();
    checker.ctx.current = 'O2b-checker-approve';
    await checker.page.locator('.payout-panel-foot button').first().click();
    await checker.page.waitForResponse(r => r.url().includes(`/beneficiary-accounts/${MAKER_TARGET}/checker-decisions`), { timeout: 20000 }).catch(() => {});
    await settle(checker.page);
    await openPanel(checker, `${REVIEWS}?stage=DONE&account=${MAKER_TARGET}`);
    await shot(checker, 'O2b-after-approve', async () => ({ tabs: await tabs(checker), badge: await text(checker.page, '.payout-badge'), done: await text(checker.page, '.payout-detail .payout-card:last-child'),
      queueItem: await text(checker.page, '.payout-queue-item.is-active') }));
    await go(checker, `${OWNERS}?ownerType=CLIENT&ownerId=${CLIENT_A}`);
    await checker.page.locator('.payout-table').waitFor({ timeout: 20000 }).catch(() => {});
    await shot(checker, 'O2c-owner-after-approve', async () => ({ rows: await checker.page.locator('.payout-table tbody tr.ant-table-row').allInnerTexts().then(a => a.map(x => x.replace(/\s+/g, ' '))) }));
  }
  await checker.context.close();

  // ================= 无出款权限的运营 =================
  const noperm = await open('noperm');
  await shot(noperm, 'R-noperm-home', async () => ({ menuHasPayout: (await menuText(noperm)).includes(MENU[locale]), menu: (await menuText(noperm)).slice(0, 300) }));
  noperm.ctx.deliberate = 'no-permission direct URL';
  await go(noperm, REVIEWS);
  await shot(noperm, 'R-noperm-reviews', async () => ({ main: await text(noperm.page, 'main, .ant-layout-content'), workbench: await noperm.page.locator('.payout-workbench').count() }));
  await go(noperm, `${OWNERS}?ownerType=CLIENT&ownerId=${CLIENT_A}`);
  await shot(noperm, 'R-noperm-owners', async () => ({ main: await text(noperm.page, 'main, .ant-layout-content'), balanceCards: await noperm.page.locator('.payout-balance-card').count() }));
  noperm.ctx.deliberate = null;
  await noperm.context.close();

  log.runs.push(run);
  if (runs.indexOf(runs.find(([l, w]) => l === locale && w === width)) < runs.length - 1) await new Promise(r => setTimeout(r, PAUSE));
}
await browser.close();
const suffix = process.env.OUT ? '-' + process.env.OUT : process.env.RUNS ? '-' + log.runs.map(r => `${r.locale}-${r.width}`).join('_') : '';
writeFileSync(join(HERE, `browser-result-${new Date().toISOString().replace(/[:.]/g, '')}${suffix}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors, deliberate: log.deliberate, writes: log.writes }, null, 1));
