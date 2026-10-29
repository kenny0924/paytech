// REQ-05 阶段 7 浏览器验收 A5（mor-test，http://localhost:28080；改写自阶段 5 verification/stage5/stage5_browser.mjs，
// 凭据取 test-environment/private/access.md，不打印）。只读走查，唯一写操作：zh-CN 1440 对一个未满足动作点一次「立即检查」。
// 不发起、不终止任何实例；动作模板编辑页加一个未声明输出后点保存只调用校验接口（浏览器内拦截 PUT），随后放弃修改。
// 组合 zh-CN / en × 1440 / 390；记录控制台 error / warning、/api/ 下 HTTP ≥ 400、原始 workflow.* key、390 横向滚动；截图写 screens/。
// ID 取 ../result-setup-*.json 与 ../result-scenario-*.json。用法：node req05_browser.mortest.mjs
import { createRequire } from 'node:module';
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = 'http://localhost:28080';
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const latest = (prefix) => JSON.parse(readFileSync(join(HERE, '..', readdirSync(join(HERE, '..')).filter(f => f.startsWith(prefix)).sort().at(-1)), 'utf8'));
const setup = latest('result-setup-'), scenario = latest('result-scenario-');
const IDS = {
  drafted: setup.actionTemplates.drafted, definition: setup.definitionId, completed: scenario.instances.complete,
  n8: scenario.instances.stuckAtCollection, n4: scenario.instances.stuckAtActive, n3: scenario.instances.notDrafted,
  agreement: '238411681049415680', // DEMO2-CL-103（REQ-01 阶段 7 验收合同，含开通项）
};
const REGRESSION = ['/card-bin-library', '/reference-data', '/finance/reconciliation/channel-lines'];
const task = (instance, node) => `/workflow/my-tasks?task=${encodeURIComponent(`ACTION:${instance}:${node}:1`)}`;
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const secrets = { E2E_ADMIN_EMAIL: access.match(/账号：(\S+)/)[1], E2E_ADMIN_PASSWORD: access.match(/密码：(\S+)/)[1] };
const L = {
  'zh-CN': { save: '保存', discard: '放弃修改', addOutput: '添加输出', actions: /^动作/, checkNow: '立即检查', activity: '活动记录' },
  en: { save: 'Save', discard: 'Discard Changes', addOutput: 'Add output', actions: /^Actions/, checkNow: 'Check now', activity: 'Activity' },
};
const log = { ids: IDS, runs: [], console: [], httpErrors: [], bundles: 0 };

const browser = await chromium.launch({ headless: true });
for (const [locale, width, writes] of (process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440, true], ['zh-CN', 390, false], ['en', 1440, false], ['en', 390, false]])) {
  const t = L[locale];
  const tag = `${locale}-${width}`;
  const context = await browser.newContext({ viewport: { width, height: width > 500 ? 1000 : 844 }, locale });
  await context.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
  // 保险：动作模板编辑页不能真的保存
  await context.route(/\/api\/operations\/workflow-admin\/v1\/action-templates\/\d+$/, route =>
    route.request().method() === 'PUT' ? route.abort() : route.continue());
  const page = await context.newPage();
  const run = { locale, width, steps: [] };
  let current = 'login';
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('response', (r) => {
    const url = new URL(r.url());
    if (/\/i18n|bundle/.test(url.pathname) && url.pathname.endsWith('.json')) log.bundles++;
    if (url.pathname.startsWith('/api/') && r.status() >= 400) log.httpErrors.push({ run: tag, step: current, method: r.request().method(), path: url.pathname, status: r.status() });
  });
  page.on('requestfailed', (r) => { if (new URL(r.url()).pathname.startsWith('/api/')) log.httpErrors.push({ run: tag, step: current, method: r.method(), path: new URL(r.url()).pathname, status: 'failed:' + r.failure()?.errorText }); });
  const settle = async (ms = 1200) => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); };
  const pageFacts = async () => page.evaluate(() => ({
    rawKeys: [...new Set((document.body.innerText.match(/\bworkflow\.[A-Za-z]+\.[A-Za-z0-9_.]+/g) || []))].slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    scrollWidth: document.documentElement.scrollWidth,
    overflow: (() => { const vw = document.documentElement.clientWidth;
      return [...document.querySelectorAll('body *')].filter(el => { const r = el.getBoundingClientRect(); return r.width && r.right > vw + 1; })
        .filter(el => !el.parentElement || el.parentElement.getBoundingClientRect().right <= vw + 1).slice(0, 5)
        .map(el => `${el.tagName.toLowerCase()}.${String(el.className).split(' ').slice(0, 2).join('.')}:${Math.round(el.getBoundingClientRect().right)}`); })(),
  }));
  const shot = async (step, check, { wait = true } = {}) => {
    current = step; if (wait) await settle();
    const entry = { step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await pageFacts();
    await page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }));
  };
  const text = async (selector) => (await page.locator(selector).first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 900);
  const go = async (path) => { current = path; await page.goto(`${ORIGIN}${path}${path.includes('?') ? '&' : '?'}lang=${locale}`); await settle(); };

  // 登录：同一账号连续登录会被 Gateway 限流（429），未进入控制台时等待后重试，最多 4 次；状态写入结果。
  for (let attempt = 1; attempt <= 4; attempt++) {
    await page.goto(`${ORIGIN}/?lang=${locale}`);
    await page.locator('input[type=password]').first().waitFor({ timeout: 20000 }).catch(() => {});
    await page.locator('input:not([type=password])').first().fill(secrets.E2E_ADMIN_EMAIL);
    await page.locator('input[type=password]').first().fill(secrets.E2E_ADMIN_PASSWORD);
    const response = page.waitForResponse(r => r.url().includes('/auth/v1/login'), { timeout: 20000 }).catch(() => null);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    const status = (await response)?.status() ?? null;
    run.login = { attempt, status };
    if (status === 200) { await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {}); break; }
    await page.waitForTimeout(45000);
  }
  await settle();

  // ---------- 原型 17：动作目录 · 数据查询 ----------
  await go(`/workflow/action-templates/${IDS.drafted}`);
  await page.locator('.wf-dq-editor').waitFor({ timeout: 20000 }).catch(() => {});
  await shot('17a-template', async () => ({ meta: await text('.wf-dq-meta'), editor: await text('.wf-dq-editor'), contract: await text('.wf-dq-contract') }));
  await page.locator('.wf-dq-editor .ant-select').first().click();
  await page.waitForTimeout(1200);
  await shot('17b-picker', async () => text('.ant-select-dropdown:visible'), { wait: false });
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: t.addOutput }).click();
  await page.locator('.wf-dq-table').last().locator('tbody tr').last().locator('input').first().fill('client_name');
  await page.getByRole('button', { name: new RegExp('^' + t.save + '$') }).click();
  await page.waitForTimeout(2500);
  await shot('17c-validation', async () => ({ rows: await page.locator('.wf-dq-table tr.is-error').allInnerTexts(), alert: await text('.wf-dq-editor .ant-alert') }));
  await page.getByRole('button', { name: t.discard }).click();
  await page.waitForTimeout(500);

  // ---------- 原型 18：流程编辑器（只看，不发布） ----------
  await go(`/workflow/definitions/${IDS.definition}`);
  await page.locator('.react-flow__node[data-id="n3"]').waitFor({ timeout: 20000 }).catch(() => {});
  await page.locator('.react-flow__node[data-id="n3"]').click().catch(() => {});
  await page.waitForTimeout(600);
  const actionsTab = page.locator('.ant-tabs-tab', { hasText: t.actions }).first();
  if (await actionsTab.isVisible().catch(() => false)) await actionsTab.click();
  await shot('18a-node-actions', async () => text('.wf-node-actions'));
  await page.locator('.react-flow__node[data-id="g1"]').click().catch(() => {});
  await page.waitForTimeout(800);
  await shot('18b-gateway', async () => text('.wf-editor-panel, .wf-panel'));

  // ---------- 原型 19：我的待办 ----------
  await go(task(IDS.n8, 'n8'));
  await shot('19a-unsatisfied-n8', async () => text('.wf-detail'));
  await go(task(IDS.n4, 'n4'));
  await shot('19b-unsatisfied-n4', async () => text('.wf-detail'));
  await go(task(IDS.n3, 'n3'));
  await shot('19c-unsatisfied-n3', async () => text('.wf-detail'));
  if (writes) {
    await page.locator('.wf-dq-row').first().getByRole('button', { name: t.checkNow }).click();
    await page.waitForTimeout(400);
    await shot('19d-checking-now', async () => text('.wf-dq-row'), { wait: false });
    await settle(2500);
    await shot('19e-after-check', async () => text('.wf-dq-row'));
  }
  // ---------- 原型 20：实例详情 ----------
  await go(`/workflow/instances/${IDS.completed}`);
  await page.locator('.ant-segmented-item', { hasText: t.activity }).first().click();
  await shot('20a-activity', async () => ({ timeline: await text('.wf-activity .wf-card'), aside: await text('.wf-activity aside') }));
  await go(`/workflow/instances/${IDS.n8}`);
  await page.locator('.react-flow__node[data-id="n3"]').click().catch(() => {});
  await page.waitForTimeout(1500);
  await shot('20b-node-drawer-satisfied', async () => text('.ant-drawer:visible'));
  await page.keyboard.press('Escape');
  await page.locator('.react-flow__node[data-id="n8"]').click().catch(() => {});
  await page.waitForTimeout(1500);
  await shot('20c-node-drawer-unsatisfied', async () => text('.ant-drawer:visible'));

  // ---------- 回归：REQ-01 合同详情（开通项）、卡 BIN 库、参考数据、对账 ----------
  await go(`/commercial/agreements/${IDS.agreement}`);
  await shot('90-regression-agreement', async () => text('main, .ant-layout-content'));
  for (const path of REGRESSION) {
    await go(path);
    await shot(`90-regression${path.replace(/\//g, '-')}`, async () => text('main, .ant-layout-content'));
  }

  log.runs.push(run);
  await context.close();
}
await browser.close();
writeFileSync(join(HERE, `browser-result${process.env.RUNS ? '-' + log.runs.map(r => r.locale + '-' + r.width).join('_') : ''}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors, bundles: log.bundles }, null, 1));
